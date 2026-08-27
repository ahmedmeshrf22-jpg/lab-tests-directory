package com.example.data.model

data class LabTest(
    val id: Int,
    val englishName: String,
    val arabicName: String,
    val marketName: String,
    val customerPrice: String?,
    val searchText: String,
    val normEnglish: String = normalizeText(englishName),
    val normArabic: String = normalizeText(arabicName),
    val normMarket: String = normalizeText(marketName),
    val normSearch: String = normalizeText(searchText)
)

