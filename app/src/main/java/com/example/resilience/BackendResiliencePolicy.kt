package com.example.resilience

import android.content.Context

/**
 * V128 failover policy guard.
 *
 * Safety rule: the shipped default is FIREBASE_ONLY and no V128 code promotes
 * itself to another mode. Future releases may expose an admin-only control after
 * shadow synchronization has been verified. This prevents an accidental backend
 * switch from changing any behavior that already works in V127.
 */
class BackendResiliencePolicy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentMode(): BackendFailoverMode {
        val stored = prefs.getString(KEY_MODE, null)
        return runCatching { BackendFailoverMode.valueOf(stored.orEmpty()) }
            .getOrDefault(BackendFailoverMode.FIREBASE_ONLY)
            .let { mode ->
                // V128 hard safety gate: standby architecture only.
                if (mode == BackendFailoverMode.FIREBASE_ONLY) mode else BackendFailoverMode.FIREBASE_ONLY
            }
    }

    fun isPrimaryAuthoritative(): Boolean = currentMode() == BackendFailoverMode.FIREBASE_ONLY

    companion object {
        private const val PREFS = "tahalil_backend_resilience_v128"
        private const val KEY_MODE = "failover_mode"
    }
}
