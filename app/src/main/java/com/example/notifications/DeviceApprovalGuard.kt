package com.example.notifications

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.security.MessageDigest

/** V141: background reads/notifications are allowed only for a server-approved device. */
object DeviceApprovalGuard {
    fun currentDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ).orEmpty().ifBlank { "unknown-android-id" }
        val seed = "$androidId|${context.packageName}"
        return MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    fun checkServerApproved(context: Context, user: FirebaseUser, onResult: (Boolean) -> Unit) {
        val deviceId = currentDeviceId(context)
        FirebaseFirestore.getInstance()
            .collection("users").document(user.uid)
            .collection("devices").document(deviceId)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val approved = doc.exists() &&
                    doc.getString("status") == "approved" &&
                    doc.getString("uid") == user.uid &&
                    doc.getString("device_id") == deviceId
                onResult(approved)
            }
            .addOnFailureListener { onResult(false) }
    }
}
