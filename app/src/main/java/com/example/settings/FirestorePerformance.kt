package com.example.settings

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/** V82 production Firestore continuity policy: larger persistent cache for lab work during outages. */
object FirestorePerformance {
    private const val CACHE_BYTES = 300L * 1024L * 1024L

    @Volatile
    private var configured = false

    fun get(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        val persistentCache = PersistentCacheSettings.newBuilder()
                            .setSizeBytes(CACHE_BYTES)
                            .build()
                        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(persistentCache)
                            .build()
                    } catch (_: IllegalStateException) {
                        // Firestore may already be started by another component. Keep its active settings.
                    }
                    configured = true
                }
            }
        }
        return db
    }
}
