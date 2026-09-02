package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object BackupNotificationManager {
    private const val CHANNEL_ID = "backup_restore_v137"
    private const val BACKUP_ID = 13701
    private const val RESTORE_ID = 13702

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "النسخ الاحتياطي والاسترجاع", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "إشعارات إنشاء واستعادة النسخ الاحتياطية"
                    enableVibration(true)
                }
            )
        }
    }

    fun notifyBackupStarted(context: Context) = show(
        context, BACKUP_ID, "جاري إنشاء النسخة الاحتياطية", "يتم الآن إنشاء نسخة تلقائية وحفظها على الهاتف.", ongoing = true
    )

    fun notifyBackupCompleted(context: Context, savedPath: String) = show(
        context, BACKUP_ID, "تم حفظ النسخة الاحتياطية ✓", "تم حفظ النسخة التلقائية في $savedPath"
    )

    fun notifyBackupFailed(context: Context, reason: String) = show(
        context, BACKUP_ID, "تعذر إنشاء النسخة الاحتياطية", reason.ifBlank { "سيتم المحاولة في الموعد التالي." }
    )

    fun notifyAutoBackupNeedsSetup(context: Context) = show(
        context, BACKUP_ID, "النسخ التلقائي يحتاج إعدادًا مرة واحدة", "اعمل نسخة يدوية واحدة بكلمة مرور، وبعدها النسخ اليومي الساعة 4:00 ص هيشتغل تلقائيًا."
    )

    fun notifyRestoreStarted(context: Context) = show(
        context, RESTORE_ID, "جاري استعادة نسخة احتياطية", "بدأ فحص واستعادة النسخة الاحتياطية.", ongoing = true
    )

    fun notifyRestoreCompleted(context: Context, details: String) = show(
        context, RESTORE_ID, "تم استعادة النسخة الاحتياطية ✓", details.ifBlank { "اكتملت عملية الاسترجاع بنجاح." }
    )

    fun notifyRestoreFailed(context: Context, reason: String) = show(
        context, RESTORE_ID, "فشل استعادة النسخة الاحتياطية", reason.ifBlank { "تعذر إكمال عملية الاسترجاع." }
    )

    private fun show(context: Context, id: Int, title: String, body: String, ongoing: Boolean = false) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
