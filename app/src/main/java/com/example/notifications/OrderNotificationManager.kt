package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest

object OrderNotificationManager {
    const val CHANNEL_ID = "order_updates"
    const val LAB_CHANNEL_ID = "lab_new_orders"
    private const val PREFS = "lab_order_notifications_v106"
    private const val MAX_SEEN = 120

    fun ensureChannel(context: Context) = ensureChannels(context)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تحديثات الطلبات",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات تحديث حالة الطلبات والنتائج"
                    enableVibration(true)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    LAB_CHANNEL_ID,
                    "طلبات المعمل الجديدة",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات مخصصة لحساب المعمل عند وصول طلب تحاليل جديد"
                    enableVibration(true)
                }
            )
        }
    }

    /** Register the FCM token for the currently signed-in account.
     * Call after every successful sign-in so switching to the lab account cannot leave
     * the token attached only to the previous clinic account. */
    fun registerCurrentToken() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (token.isBlank()) return@addOnSuccessListener
            saveToken(user.uid, user.email.orEmpty(), token)
        }
    }

    fun saveToken(uid: String, email: String, token: String) {
        val tokenId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(40)
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("fcm_tokens").document(tokenId)
            .set(
                mapOf(
                    "token" to token,
                    "email" to email.trim().lowercase(),
                    "updated_at" to FieldValue.serverTimestamp(),
                    "updated_at_ms" to System.currentTimeMillis(),
                    "platform" to "android"
                )
            )
    }


    fun notifyOrderUpdateIfNeeded(
        context: Context,
        eventKey: String,
        title: String,
        body: String,
        labOnly: Boolean = false,
        orderId: String = ""
    ) {
        if (eventKey.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = if (labOnly) "seen_lab_events" else "seen_clinic_events"
        val seen = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (eventKey in seen) return

        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_orders", true)
            putExtra("order_id", orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            context,
            if (labOnly) LAB_CHANNEL_ID else CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventKey.hashCode(), notification)
            seen += eventKey
            prefs.edit().putStringSet(key, seen.toList().takeLast(MAX_SEEN).toSet()).apply()
        } catch (_: SecurityException) {
        }
    }

    /** Local fallback for lab-only new-order alerts. Used by the live Firestore listener
     * and by the periodic background job. Dedupe is persisted so the same order does not
     * keep notifying on refresh/restart. */
    fun notifyNewLabOrderIfNeeded(
        context: Context,
        orderId: String,
        orderNumber: String,
        customerName: String
    ) {
        if (orderId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen_ids", emptySet()).orEmpty().toMutableSet()
        if (seen.contains(orderId)) return

        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_orders", true)
            putExtra("order_id", orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = listOf(orderNumber.ifBlank { "طلب جديد" }, customerName.ifBlank { "عميل" })
            .joinToString(" • ")
        val notification = NotificationCompat.Builder(context, LAB_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("طلب تحاليل جديد للمعمل")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(orderId.hashCode(), notification)
            seen.add(orderId)
            val trimmed = seen.toList().takeLast(MAX_SEEN).toSet()
            prefs.edit().putStringSet("seen_ids", trimmed).apply()
        } catch (_: SecurityException) {
            // Android 13+ notification permission may be denied by the user.
        }
    }
}
