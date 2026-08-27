package com.example.data.model

data class AppUserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: String = "staff",
    val enabled: Boolean = false,
    val canEditCustomers: Boolean = false,
    val canDiscount: Boolean = false,
    val canCollectPayments: Boolean = false,
    val canViewSalesReports: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val pinResetRequestedAtMillis: Long = 0L
)


data class RolePermissions(
    val canEditCustomers: Boolean,
    val canDiscount: Boolean,
    val canCollectPayments: Boolean,
    val canViewSalesReports: Boolean
)

fun normalizeUserRole(role: String): String = when (role.trim().lowercase()) {
    "reception", "استقبال" -> "reception"
    "cashier", "كاشير" -> "cashier"
    "technician", "فني", "فنى" -> "technician"
    "lab_operator", "lab", "معمل", "المعمل" -> "lab_operator"
    "supervisor", "مشرف" -> "supervisor"
    "manager", "مدير" -> "manager"
    "super_admin", "super admin" -> "super_admin"
    else -> "staff"
}

fun permissionsForRole(role: String): RolePermissions = when (normalizeUserRole(role)) {
    "reception" -> RolePermissions(true, false, false, false)
    "cashier" -> RolePermissions(true, false, true, false)
    "technician" -> RolePermissions(false, false, false, false)
    "lab_operator" -> RolePermissions(false, false, false, false)
    "supervisor" -> RolePermissions(true, true, true, true)
    "manager", "super_admin" -> RolePermissions(true, true, true, true)
    else -> RolePermissions(true, false, true, false)
}

data class AuditLogEntry(
    val id: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val customerId: String = "",
    val orderId: String = "",
    val title: String,
    val details: String,
    val actorUid: String,
    val actorEmail: String,
    val createdAtMillis: Long,
    val syncedAtMillis: Long = 0L,
    val wasOffline: Boolean = false
)

data class CustomerActivityEntry(
    val id: String,
    val type: String,
    val title: String,
    val details: String,
    val actorUid: String,
    val actorEmail: String,
    val createdAtMillis: Long,
    val syncedAtMillis: Long = 0L,
    val wasOffline: Boolean = false
)

data class PaymentEntry(
    val id: String,
    val customerId: String,
    val orderId: String,
    val amount: Double,
    val note: String,
    val createdAtMillis: Long,
    val createdByUid: String,
    val createdByEmail: String
)

data class ReportSummary(
    val ordersCount: Int = 0,
    val customersCount: Int = 0,
    val subtotal: Double = 0.0,
    val discounts: Double = 0.0,
    val sales: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    val estimatedLabCost: Double = 0.0,
    val estimatedProfit: Double = 0.0
)


data class AuthorizedDevice(
    val id: String,
    val uid: String,
    val email: String,
    val manufacturer: String,
    val model: String,
    val status: String = "pending",
    val requestedAtMillis: Long = 0L,
    val approvedAtMillis: Long = 0L,
    val approvedByUid: String = ""
)
