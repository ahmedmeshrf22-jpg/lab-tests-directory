package com.example.data.model

private val TASHKEEL_REGEX = Regex("[\u064B-\u0652\u0640]")
private val ALEF_REGEX = Regex("[أإآا]")
private val PUNCTUATION_REGEX = Regex("[-_.,/\\\\()]")
private val MULTI_SPACE_REGEX = Regex("\\s+")

fun normalizeText(input: String?): String {
    if (input.isNullOrBlank()) return ""
    
    var text = input.lowercase()
    
    // Remove Arabic diacritics (tashkeel) and tatweel
    text = text.replace(TASHKEEL_REGEX, "")
    
    // Normalize Alef variants
    text = text.replace(ALEF_REGEX, "ا")
    
    // Normalize Alef Maqsura to Ya
    text = text.replace("ى", "ي")
    
    // Normalize Teh Marbuta to Heh for search flexibility
    text = text.replace("ة", "ه")
    
    // Replace hyphens, underscores, punctuation with space
    text = text.replace(PUNCTUATION_REGEX, " ")
    
    // Collapse multiple whitespace characters into a single space
    text = text.replace(MULTI_SPACE_REGEX, " ").trim()
    
    return text
}

