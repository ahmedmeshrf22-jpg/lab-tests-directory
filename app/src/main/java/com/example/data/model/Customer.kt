package com.example.data.model

data class Customer(
    val id: String,
    val fileNumber: String,
    val name: String,
    val phone: String,
    val alternatePhone: String = "",
    val age: String,
    val birthDate: String = "",
    val gender: String,
    val address: String = "",
    val notes: String,
    val importantAlert: String = "",
    val tags: List<String> = emptyList(),
    val defaultDiscountPercent: Double = 0.0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val createdByUid: String = "",
    val updatedByUid: String = "",
    val isBlacklisted: Boolean = false,
    val blacklistReason: String = "",
    val blacklistedAtMillis: Long = 0L,
    val isArchived: Boolean = false,
    val archivedAtMillis: Long = 0L
)

data class CustomerOrderItem(
    val testId: Int,
    val englishName: String,
    val arabicName: String,
    val marketName: String,
    val customerPrice: Double
)

data class CustomerOrder(
    val id: String,
    val orderNumber: String,
    val customerId: String,
    val customerFileNumber: String,
    val customerName: String,
    val customerPhone: String = "",
    val customerAge: String = "",
    val customerGender: String = "",
    val items: List<CustomerOrderItem>,
    val subtotalCustomerPrice: Double = 0.0,
    val discountAmount: Double = 0.0,
    val discountPercent: Double = 0.0,
    val totalCustomerPrice: Double,
    val paymentStatus: String = "unpaid",
    val workflowStatus: String = "new",
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val notes: String = "",
    val createdAtMillis: Long,
    val createdByUid: String,
    val createdByEmail: String = "",
    val updatedAtMillis: Long = 0L,
    val updatedByUid: String = "",
    val editCount: Int = 0,
    val isVoided: Boolean = false,
    val voidReason: String = "",
    val resultUrls: List<String> = emptyList(),
    val resultNames: List<String> = emptyList(),
    val resultSentAtMillis: Long = 0L
)
