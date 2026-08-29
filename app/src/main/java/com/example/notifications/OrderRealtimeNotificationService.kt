package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.normalizeUserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * V120 no-server realtime notification bridge.
 * Keeps a lightweight Firestore listener alive while the signed-in device is allowed to run
 * background work, so clinic/lab notifications can appear outside the app UI.
 * Android Force Stop still disables all app-side background execution until the app is opened again.
 */
class OrderRealtimeNotificationService : Service() {
    companion object {
        private const val SERVICE_CHANNEL_ID = "order_watch_service"
        private const val SERVICE_NOTIFICATION_ID = 12001
        private const val PREFS = "order_realtime_watch_v120"

        fun start(context: Context) {
            val intent = Intent(context, OrderRealtimeNotificationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrderRealtimeNotificationService::class.java))
        }
    }

    private var registration: ListenerRegistration? = null
    private var activeUid: String = ""

    override fun onCreate() {
        super.onCreate()
        OrderNotificationManager.ensureChannels(this)
        ensureServiceChannel()
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("إشعارات تحاليل العقاد مفعلة")
            .setContentText("متابعة الطلبات والنتائج في الخلفية")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(SERVICE_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachForCurrentUser()
        return START_STICKY
    }

    private fun attachForCurrentUser() {
        registration?.remove()
        registration = null

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            stopSelf()
            return
        }
        activeUid = user.uid

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { profile ->
                if (FirebaseAuth.getInstance().currentUser?.uid != activeUid) return@addOnSuccessListener
                if (profile.getBoolean("enabled") != true) {
                    stopSelf()
                    return@addOnSuccessListener
                }
                val role = normalizeUserRole(profile.getString("role").orEmpty())
                attachOrderListener(db, user.uid, role)
            }
            .addOnFailureListener {
                // Existing periodic JobScheduler remains the fallback if this profile read fails.
            }
    }

    private fun attachOrderListener(db: FirebaseFirestore, uid: String, role: String) {
        registration?.remove()
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "checkpoint_${uid}_${role}"
        if (!prefs.contains(key)) prefs.edit().putLong(key, System.currentTimeMillis()).apply()

        val query = if (role == "lab_operator") {
            db.collection("lab_orders")
                .orderBy("updated_at_ms", Query.Direction.DESCENDING)
                .limit(200)
        } else {
            db.collectionGroup("orders")
                .orderBy("updated_at_ms", Query.Direction.DESCENDING)
                .limit(200)
        }

        registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            var checkpoint = prefs.getLong(key, 0L)
            var newest = checkpoint

            snapshot.documentChanges.forEach { change ->
                if (change.type == DocumentChange.Type.REMOVED) return@forEach
                val doc = change.document
                val updated = doc.getLong("updated_at_ms") ?: doc.getLong("created_at_ms") ?: 0L
                if (updated <= checkpoint) return@forEach
                if (updated > newest) newest = updated

                val id = doc.id
                val number = doc.getString("order_number").orEmpty()
                val name = doc.getString("customer_name").orEmpty()
                val status = doc.getString("workflow_status").orEmpty()
                val updatedBy = doc.getString("updated_by_uid").orEmpty()
                val voided = doc.getBoolean("is_voided") == true
                if (updatedBy == uid) return@forEach

                if (role == "lab_operator") {
                    when {
                        voided -> OrderNotificationManager.notifyOrderUpdateIfNeeded(
                            this, "lab_cancel_${id}_$updated", "تم إلغاء طلب",
                            "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}",
                            labOnly = true, orderId = id
                        )
                        status == "sent_to_lab" -> OrderNotificationManager.notifyNewLabOrderIfNeeded(
                            this, id, number, name
                        )
                        status == "processing" -> {
                            val notes = doc.getString("notes").orEmpty()
                            val revision = notes.contains("طلب تعديل النتيجة من العيادة")
                            OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                this, "lab_update_${id}_$updated",
                                if (revision) "طلب تعديل نتيجة" else "تم تحديث طلب تحاليل",
                                if (revision) {
                                    "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }} • راجع ملاحظة العيادة"
                                } else {
                                    "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}"
                                },
                                labOnly = true, orderId = id
                            )
                        }
                    }
                } else {
                    when {
                        voided -> OrderNotificationManager.notifyOrderUpdateIfNeeded(
                            this, "clinic_cancel_${id}_$updated", "تم إلغاء الطلب",
                            "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}", orderId = id
                        )
                        status == "ready" -> OrderNotificationManager.notifyOrderUpdateIfNeeded(
                            this, "clinic_ready_${id}_$updated", "وصلت نتيجة التحاليل",
                            "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }} • اضغط لفتح الطلب", orderId = id
                        )
                        status == "processing" -> OrderNotificationManager.notifyOrderUpdateIfNeeded(
                            this, "clinic_processing_${id}_$updated", "المعمل استلم الطلب",
                            "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}", orderId = id
                        )
                    }
                }
            }

            if (newest > checkpoint) prefs.edit().putLong(key, newest).apply()
        }
    }

    private fun ensureServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "متابعة الطلبات في الخلفية",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "تشغيل متابعة فورية للطلبات والنتائج"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

        override fun onTimeout(startId: Int, fgsType: Int) {
        LabOrderBackgroundScheduler.schedule(this)
        stopSelf(startId)
    }

override fun onDestroy() {
        registration?.remove()
        registration = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
