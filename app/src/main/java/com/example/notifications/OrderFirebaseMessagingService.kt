package com.example.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class OrderFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val user = FirebaseAuth.getInstance().currentUser ?: return
        OrderNotificationManager.saveTokenIfApproved(this, user.uid, user.email.orEmpty(), token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val user = FirebaseAuth.getInstance().currentUser ?: return
        DeviceApprovalGuard.checkServerApproved(this, user) { approved ->
            if (!approved || FirebaseAuth.getInstance().currentUser?.uid != user.uid) return@checkServerApproved
            showApprovedMessage(message)
        }
    }

    private fun showApprovedMessage(message: RemoteMessage) {
        OrderNotificationManager.ensureChannel(this)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "تحديث حالة طلب"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "تم تحديث أحد طلباتك"
        val orderId = message.data["order_id"].orEmpty()
        val customerId = message.data["customer_id"].orEmpty()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_orders", true)
            putExtra("order_id", orderId)
            putExtra("customer_id", customerId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (message.data["workflow_status"] == "sent_to_lab" || title.contains("للمعمل")) {
            OrderNotificationManager.LAB_CHANNEL_ID
        } else {
            OrderNotificationManager.CHANNEL_ID
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(orderId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission may still be denied by the user.
        }
    }
}
