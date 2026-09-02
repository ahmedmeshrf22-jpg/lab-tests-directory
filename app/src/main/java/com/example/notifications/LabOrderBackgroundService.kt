package com.example.notifications

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.model.normalizeUserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source

/**
 * Backend-free safety net for lab notifications.
 * Real-time alerts are delivered while the app is alive. This periodic job checks for
 * newly sent lab orders while the app is backgrounded. Android controls the exact run time
 * (minimum periodic interval is 15 minutes), so FCM remains the instant path when available.
 */
class LabOrderBackgroundService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid).get(Source.SERVER)
            .addOnSuccessListener { profile ->
                val role = normalizeUserRole(profile.getString("role").orEmpty())
                val enabled = profile.getBoolean("enabled") == true
                if (!enabled) {
                    jobFinished(params, false)
                    return@addOnSuccessListener
                }

                DeviceApprovalGuard.checkServerApproved(this, user) { approved ->
                    if (!approved) {
                        jobFinished(params, false)
                        return@checkServerApproved
                    }

                val prefs = getSharedPreferences("order_background_v107", Context.MODE_PRIVATE)
                val checkpointKey = "last_scan_ms_${user.uid}"
                val lastScan = prefs.getLong(checkpointKey, 0L)
                val now = System.currentTimeMillis()

                // V116 FIX3: query only documents changed since the previous successful scan.
                // The old fallback re-read up to 200 recent orders every 15 minutes per device,
                // which could waste tens of thousands of Firestore reads per day.
                val operationalQuery = if (role == "lab_operator") {
                    db.collection("lab_orders")
                } else {
                    db.collectionGroup("orders")
                }
                operationalQuery
                    .whereGreaterThan("updated_at_ms", lastScan)
                    .orderBy("updated_at_ms", Query.Direction.ASCENDING)
                    .limit(200)
                    .get(Source.SERVER)
                    .addOnSuccessListener { snapshot ->
                        var newestProcessed = lastScan
                        snapshot.documents.forEach { doc ->
                            val id = doc.id
                            val number = doc.getString("order_number").orEmpty()
                            val name = doc.getString("customer_name").orEmpty()
                            val status = doc.getString("workflow_status").orEmpty()
                            val updated = doc.getLong("updated_at_ms") ?: doc.getLong("created_at_ms") ?: 0L
                            val created = doc.getLong("created_at_ms") ?: 0L
                            if (updated > newestProcessed) newestProcessed = updated
                            val editCount = (doc.getLong("edit_count") ?: 0L).toInt()
                            val voided = doc.getBoolean("is_voided") == true

                            if (role == "lab_operator") {
                                if (status == "sent_to_lab" && created > lastScan) {
                                    OrderNotificationManager.notifyNewLabOrderIfNeeded(
                                        applicationContext, id, number, name
                                    )
                                }
                                if (updated > lastScan && editCount > 0 && !voided &&
                                    status in setOf("sent_to_lab", "processing")) {
                                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                        applicationContext,
                                        "lab_edit_${id}_$updated",
                                        "تم تعديل طلب تحاليل",
                                        "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}",
                                        labOnly = true,
                                        orderId = id
                                    )
                                }
                                if (updated > lastScan && voided) {
                                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                        applicationContext,
                                        "lab_cancel_${id}_$updated",
                                        "تم إلغاء طلب",
                                        "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}",
                                        labOnly = true,
                                        orderId = id
                                    )
                                }
                            } else {
                                if (updated > lastScan && status == "processing" && !voided) {
                                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                        applicationContext,
                                        "clinic_processing_${id}_$updated",
                                        "المعمل استلم الطلب",
                                        "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}",
                                        orderId = id
                                    )
                                }
                                if (updated > lastScan && status == "ready" && !voided) {
                                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                        applicationContext,
                                        "clinic_ready_${id}_$updated",
                                        "نتيجة التحاليل جاهزة",
                                        "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}",
                                        orderId = id
                                    )
                                }
                                if (updated > lastScan && voided) {
                                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                        applicationContext,
                                        "clinic_cancel_${id}_$updated",
                                        "المعمل ألغى طلب",
                                        "${number.ifBlank { "طلب" }} • ${name.ifBlank { "عميل" }}",
                                        orderId = id
                                    )
                                }
                            }
                        }
                        // Advance only to the newest document actually processed. If more than
                        // 200 events arrived, the next pass continues from this checkpoint instead
                        // of skipping the remainder. With no changes, advance to now to avoid
                        // repeatedly querying the same empty range.
                        val nextCheckpoint = if (snapshot.isEmpty) now else newestProcessed
                        prefs.edit().putLong(checkpointKey, nextCheckpoint).apply()
                        jobFinished(params, false)
                    }
                    .addOnFailureListener { jobFinished(params, true) }
                }
            }
            .addOnFailureListener { jobFinished(params, true) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

object LabOrderBackgroundScheduler {
    private const val JOB_ID = 10701
    private const val INTERVAL_MS = 15L * 60L * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return

        // The first background pass should only alert for events arriving after scheduling.
        val prefs = context.getSharedPreferences("order_background_v107", Context.MODE_PRIVATE)
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val checkpointKey = "last_scan_ms_$uid"
        if (uid.isNotBlank() && !prefs.contains(checkpointKey)) {
            prefs.edit().putLong(checkpointKey, System.currentTimeMillis()).apply()
        }

        val info = JobInfo.Builder(JOB_ID, ComponentName(context, LabOrderBackgroundService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(INTERVAL_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(info)
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(JOB_ID)
    }
}
