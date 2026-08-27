package com.example.resilience

import android.content.Context

/**
 * Single registry point for future secondary providers.
 *
 * V128 deliberately returns a disabled adapter so that adding this architecture
 * has zero effect on authentication, Firestore, notifications, result files,
 * pricing, customer orders, or existing offline behavior.
 */
object BackendResilienceRegistry {
    @Volatile
    private var adapter: SecondaryBackendAdapter = DisabledSecondaryBackendAdapter

    fun secondary(): SecondaryBackendAdapter = adapter

    fun policy(context: Context): BackendResiliencePolicy = BackendResiliencePolicy(context)

    internal fun installForTesting(candidate: SecondaryBackendAdapter) {
        adapter = candidate
    }

    internal fun resetForTesting() {
        adapter = DisabledSecondaryBackendAdapter
    }
}
