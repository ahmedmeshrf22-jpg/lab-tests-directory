package com.example.resilience

/**
 * V128 safe secondary-backend contract.
 *
 * This layer is intentionally NOT connected to production reads/writes yet.
 * Firebase remains the only authoritative remote backend in V128. The goal is
 * to give later releases a stable adapter surface for a shadow backup provider
 * (for example Supabase) without touching the existing Firebase code paths.
 */
enum class BackendFailoverMode {
    FIREBASE_ONLY,
    SHADOW_BACKUP,
    MANUAL_SECONDARY,
    AUTO_FAILOVER
}

data class SecondaryBackendHealth(
    val provider: String,
    val configured: Boolean,
    val reachable: Boolean,
    val checkedAtMillis: Long,
    val detail: String = ""
)

data class BackupEnvelope(
    val entityType: String,
    val entityId: String,
    val operationId: String,
    val updatedAtMillis: Long,
    val payloadJson: String
)

interface SecondaryBackendAdapter {
    val providerName: String
    val configured: Boolean

    /** Lightweight provider health probe. Must never mutate primary data. */
    suspend fun healthCheck(): SecondaryBackendHealth

    /**
     * Future shadow-backup hook. V128 production flows do not call this method.
     * Implementations must be idempotent by operationId before activation.
     */
    suspend fun mirror(envelope: BackupEnvelope): Result<Unit>

    /** Future disaster-recovery read hook. Not used by V128 production flows. */
    suspend fun fetch(entityType: String, entityId: String): Result<BackupEnvelope?>
}

object DisabledSecondaryBackendAdapter : SecondaryBackendAdapter {
    override val providerName: String = "disabled"
    override val configured: Boolean = false

    override suspend fun healthCheck(): SecondaryBackendHealth = SecondaryBackendHealth(
        provider = providerName,
        configured = false,
        reachable = false,
        checkedAtMillis = System.currentTimeMillis(),
        detail = "Secondary backend is not configured"
    )

    override suspend fun mirror(envelope: BackupEnvelope): Result<Unit> =
        Result.failure(IllegalStateException("Secondary backend is disabled"))

    override suspend fun fetch(entityType: String, entityId: String): Result<BackupEnvelope?> =
        Result.success(null)
}
