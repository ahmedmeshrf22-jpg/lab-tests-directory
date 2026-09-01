package com.example.notifications

import android.app.AlarmManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.settings.AppSettingsStore
import com.example.ui.LabTestsViewModel
import com.example.util.AutoBackupCredentialStore
import com.example.util.AutoBackupStorage
import com.example.util.CommercialBackupManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class AutoBackupJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        BackupNotificationManager.ensureChannel(this)
        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !LabTestsViewModel.isManagerAccount(user.email, user.uid)) {
            BackupNotificationManager.notifyBackupFailed(this, "لازم حساب المدير يكون مسجل على الجهاز لعمل النسخة التلقائية.")
            return false
        }

        val password = AutoBackupCredentialStore.loadPassword(this)
        if (password == null) {
            BackupNotificationManager.notifyAutoBackupNeedsSetup(this)
            return false
        }

        BackupNotificationManager.notifyBackupStarted(this)
        val settingsStore = AppSettingsStore(applicationContext).apply { setActiveProfile(user.uid) }
        CommercialBackupManager.createEncryptedBackupV134(
            db = FirebaseFirestore.getInstance(),
            settings = settingsStore.settings.value,
            password = password
        ) { result ->
            result.fold(
                onSuccess = { backup ->
                    runCatching { AutoBackupStorage.saveToPhone(applicationContext, backup.encrypted) }
                        .onSuccess { path -> BackupNotificationManager.notifyBackupCompleted(applicationContext, path) }
                        .onFailure { error -> BackupNotificationManager.notifyBackupFailed(applicationContext, error.message.orEmpty()) }
                    finish(params, false)
                },
                onFailure = { error ->
                    BackupNotificationManager.notifyBackupFailed(applicationContext, error.message.orEmpty())
                    finish(params, false)
                }
            )
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private fun finish(params: JobParameters, retry: Boolean) {
        jobFinished(params, retry)
    }
}

class AutoBackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_RUN) {
            AutoBackupScheduler.enqueueNow(context)
        }
        AutoBackupScheduler.schedule(context)
    }

    companion object {
        const val ACTION_RUN = "com.aistudio.labtestsdirectory.egypt.AUTO_BACKUP_4AM"
    }
}

object AutoBackupScheduler {
    private const val JOB_ID = 13740
    private const val REQUEST_CODE = 13740
    private const val FOUR_AM_HOUR = 4

    fun schedule(context: Context) {
        val triggerAt = nextFourAmMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AutoBackupAlarmReceiver::class.java).setAction(AutoBackupAlarmReceiver.ACTION_RUN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            // Exact 04:00 when Android grants Alarms & reminders access.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent)
        } else {
            // Without special access Android can shift this slightly for battery optimization.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent)
        }
    }

    fun requestExactAlarmAccessOnce(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return
        val prefs = context.getSharedPreferences("auto_backup_schedule_v137", Context.MODE_PRIVATE)
        if (prefs.getBoolean("exact_access_prompted", false)) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onSuccess {
            prefs.edit().putBoolean("exact_access_prompted", true).apply()
        }
    }

    /** V142: recover a missed 04:00 backup when the app is next opened. */
    fun catchUpIfMissed(context: Context) {
        if (!AutoBackupCredentialStore.isConfigured(context)) return
        val now = System.currentTimeMillis()
        val todayFour = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, FOUR_AM_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (now < todayFour + 15 * 60 * 1000L) return
        val latest = AutoBackupStorage.latestBackupEpochMillis(context) ?: 0L
        if (latest < todayFour) enqueueNow(context)
    }

    fun enqueueNow(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, AutoBackupJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(1_000L)
            .build()
        scheduler.schedule(info)
    }

    private fun nextFourAmMillis(): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, FOUR_AM_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
