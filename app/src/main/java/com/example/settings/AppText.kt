package com.example.settings

import androidx.compose.runtime.Composable

object AppLanguageRuntime {
    @Volatile var code: String = "ar"
    fun set(value: String?) { code = if (value == "en") "en" else "ar" }
}

@Composable
fun appText(ar: String, en: String): String =
    if (LocalAppSettings.current.language == "en") en else ar

fun appText(settings: AppSettings, ar: String, en: String): String =
    if (settings.language == "en") en else ar

fun tr(ar: String, en: String): String = if (AppLanguageRuntime.code == "en") en else ar
