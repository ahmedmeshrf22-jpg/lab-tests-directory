package com.example.settings

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Personal settings are stored per signed-in account on this device.
 * Manager-only display/PDF settings remain device-wide for the manager workflow.
 */
data class AppSettings(
    val language: String = "ar", // ar | en
    val securityEnabled: Boolean = true,
    val unlockMode: String = "device", // device | pin
    val appPinConfigured: Boolean = false,
    val autoLockSeconds: Int = 0,
    val darkMode: Boolean = false,
    val fontScale: Float = 1.0f,
    val showEnglishName: Boolean = true,
    val showArabicName: Boolean = true,
    val showMarketName: Boolean = true,
    val showCustomerPrice: Boolean = true,
    val showLab2LabPrice: Boolean = false,
    val pdfIncludeCustomerPrice: Boolean = true,
    val pdfIncludeLab2LabPrice: Boolean = false,
    val pdfShowTotals: Boolean = true,
    val pdfLabName: String = "تحاليل العقاد",
    val brandTagline: String = "دليل التحاليل والأسعار",
    val brandWhatsApp: String = "",
    val brandPhone: String = "",
    val brandAddress: String = "",
    val brandLogoPath: String = "",
    val pdfShowContactInfo: Boolean = false,
    val pdfContactInfo: String = "",
    val lastSyncMillis: Long = 0L,
    val recentSearches: List<String> = emptyList(),
    val favoriteTestIds: Set<Int> = emptySet()
)

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var activeProfile: String = "default"

    private val _settings = MutableStateFlow(load()).also { AppLanguageRuntime.set(it.value.language) }
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setActiveProfile(profileId: String?) {
        val normalized = profileId?.trim()?.takeIf { it.isNotBlank() } ?: "default"
        if (normalized == activeProfile) return
        activeProfile = normalized
        _settings.value = load()
        AppLanguageRuntime.set(_settings.value.language)
    }

    fun save(settings: AppSettings) {
        val safeSettings = settings.copy(
            securityEnabled = true,
            fontScale = settings.fontScale.coerceIn(0.85f, 1.25f),
            autoLockSeconds = settings.autoLockSeconds.coerceAtLeast(0),
            unlockMode = if (settings.unlockMode == "pin") "pin" else "device",
            appPinConfigured = hasAppPin(),
            // V74: Lab 2 Lab never leaks into daily/customer-facing surfaces.
            showLab2LabPrice = false,
            pdfIncludeLab2LabPrice = false
        )

        prefs.edit()
            // Personal settings: scoped to current signed-in account.
            .putString(personal(KEY_LANGUAGE), if (safeSettings.language == "en") "en" else "ar")
            .putBoolean(personal(KEY_SECURITY_ENABLED), safeSettings.securityEnabled)
            .putString(personal(KEY_UNLOCK_MODE), safeSettings.unlockMode)
            .putInt(personal(KEY_AUTO_LOCK_SECONDS), safeSettings.autoLockSeconds)
            .putBoolean(personal(KEY_DARK_MODE), safeSettings.darkMode)
            .putFloat(personal(KEY_FONT_SCALE), safeSettings.fontScale)
            .putBoolean(personal(KEY_SHOW_ENGLISH), safeSettings.showEnglishName)
            .putBoolean(personal(KEY_SHOW_ARABIC), safeSettings.showArabicName)
            .putBoolean(personal(KEY_SHOW_MARKET), safeSettings.showMarketName)
            .putString(personal(KEY_RECENT_SEARCHES), safeSettings.recentSearches.take(8).joinToString("\u001F"))
            .putStringSet(personal(KEY_FAVORITE_TEST_IDS), safeSettings.favoriteTestIds.map { it.toString() }.toSet())
            // Manager/application controls remain device-wide.
            .putBoolean(KEY_SHOW_CUSTOMER_PRICE, safeSettings.showCustomerPrice)
            .putBoolean(KEY_SHOW_LAB_PRICE, safeSettings.showLab2LabPrice)
            .putBoolean(KEY_PDF_CUSTOMER_PRICE, safeSettings.pdfIncludeCustomerPrice)
            .putBoolean(KEY_PDF_LAB_PRICE, safeSettings.pdfIncludeLab2LabPrice)
            .putBoolean(KEY_PDF_TOTALS, safeSettings.pdfShowTotals)
            .putString(KEY_PDF_LAB_NAME, safeSettings.pdfLabName)
            .putString(KEY_BRAND_TAGLINE, safeSettings.brandTagline)
            .putString(KEY_BRAND_WHATSAPP, safeSettings.brandWhatsApp)
            .putString(KEY_BRAND_PHONE, safeSettings.brandPhone)
            .putString(KEY_BRAND_ADDRESS, safeSettings.brandAddress)
            .putString(KEY_BRAND_LOGO_PATH, safeSettings.brandLogoPath)
            .putBoolean(KEY_PDF_CONTACT_ENABLED, safeSettings.pdfShowContactInfo)
            .putString(KEY_PDF_CONTACT_INFO, safeSettings.pdfContactInfo)
            .putLong(KEY_LAST_SYNC, safeSettings.lastSyncMillis)
            .apply()

        _settings.value = safeSettings.copy(appPinConfigured = hasAppPin())
        AppLanguageRuntime.set(_settings.value.language)
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        save(transform(_settings.value))
    }

    fun setAppPin(pin: String): Boolean {
        // V82: all newly-created local app PINs are exactly six digits.
        if (!pin.matches(Regex("\\d{6}"))) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPinV3(pin, salt)
        prefs.edit()
            .putString(personal(KEY_PIN_SALT), Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(personal(KEY_PIN_HASH), Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(personal(KEY_PIN_VERSION), PIN_VERSION_PBKDF2_STRONG)
            .putString(personal(KEY_UNLOCK_MODE), "pin")
            .putBoolean(personal(KEY_SECURITY_ENABLED), true)
            .apply()
        _settings.value = load()
        return true
    }

    fun verifyAppPin(pin: String): Boolean {
        val saltText = prefs.getString(personal(KEY_PIN_SALT), null) ?: return false
        val expectedText = prefs.getString(personal(KEY_PIN_HASH), null) ?: return false
        return try {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(expectedText, Base64.NO_WRAP)
            val version = prefs.getInt(personal(KEY_PIN_VERSION), PIN_VERSION_LEGACY)
            val calculated = when {
                version >= PIN_VERSION_PBKDF2_STRONG -> hashPinV3(pin, salt)
                version >= PIN_VERSION_PBKDF2 -> hashPinV2(pin, salt)
                else -> hashPinLegacy(pin, salt)
            }
            val valid = MessageDigest.isEqual(expected, calculated)
            // V118: transparently upgrade old local PIN hashes to stronger PBKDF2 after a valid unlock.
            if (valid && version < PIN_VERSION_PBKDF2_STRONG && pin.matches(Regex("\\d{6}"))) {
                setAppPin(pin)
            }
            valid
        } catch (_: Exception) {
            false
        }
    }

    fun clearAppPin() {
        prefs.edit()
            .remove(personal(KEY_PIN_SALT))
            .remove(personal(KEY_PIN_HASH))
            .remove(personal(KEY_PIN_VERSION))
            .putString(personal(KEY_UNLOCK_MODE), "device")
            .apply()
        _settings.value = load()
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        update { current ->
            current.copy(recentSearches = (listOf(clean) + current.recentSearches.filterNot { it.equals(clean, true) }).take(8))
        }
    }

    fun toggleFavoriteTest(testId: Int) {
        update { current ->
            val next = current.favoriteTestIds.toMutableSet()
            if (!next.add(testId)) next.remove(testId)
            current.copy(favoriteTestIds = next)
        }
    }

    /**
     * A manager can request a remote PIN reset through the user profile. The PIN itself is
     * never readable or synced. Each device handles a reset token once and returns to the
     * device-credential unlock mode.
     */
    fun applyRemotePinReset(resetToken: Long): Boolean {
        if (resetToken <= 0L) return false
        val handledKey = personal(KEY_HANDLED_PIN_RESET_TOKEN)
        val handled = prefs.getLong(handledKey, 0L)
        if (resetToken <= handled) return false
        prefs.edit()
            .remove(personal(KEY_PIN_SALT))
            .remove(personal(KEY_PIN_HASH))
            .remove(personal(KEY_PIN_VERSION))
            .putString(personal(KEY_UNLOCK_MODE), "device")
            .putBoolean(personal(KEY_SECURITY_ENABLED), true)
            .putLong(handledKey, resetToken)
            .apply()
        _settings.value = load()
        return true
    }

    fun markSynced(timeMillis: Long = System.currentTimeMillis()) {
        update { it.copy(lastSyncMillis = timeMillis) }
    }

    fun reset() {
        val preservedSync = _settings.value.lastSyncMillis
        val preservedPin = hasAppPin()
        val defaults = AppSettings(lastSyncMillis = preservedSync, appPinConfigured = preservedPin)
        save(defaults)
    }

    private fun hasAppPin(): Boolean {
        return !prefs.getString(personal(KEY_PIN_HASH), null).isNullOrBlank() &&
            !prefs.getString(personal(KEY_PIN_SALT), null).isNullOrBlank()
    }

    private fun load(): AppSettings {
        val hasPin = hasAppPin()
        val storedUnlockMode = prefs.getString(personal(KEY_UNLOCK_MODE), "device") ?: "device"
        val safeUnlockMode = if (storedUnlockMode == "pin" && hasPin) "pin" else "device"

        return AppSettings(
            language = prefs.getString(personal(KEY_LANGUAGE), "ar").let { if (it == "en") "en" else "ar" },
            securityEnabled = true,
            unlockMode = safeUnlockMode,
            appPinConfigured = hasPin,
            autoLockSeconds = prefs.getInt(personal(KEY_AUTO_LOCK_SECONDS), prefs.getInt(KEY_AUTO_LOCK_SECONDS, 0)),
            darkMode = prefs.getBoolean(personal(KEY_DARK_MODE), prefs.getBoolean(KEY_DARK_MODE, false)),
            fontScale = prefs.getFloat(personal(KEY_FONT_SCALE), prefs.getFloat(KEY_FONT_SCALE, 1.0f)),
            showEnglishName = prefs.getBoolean(personal(KEY_SHOW_ENGLISH), prefs.getBoolean(KEY_SHOW_ENGLISH, true)),
            showArabicName = prefs.getBoolean(personal(KEY_SHOW_ARABIC), prefs.getBoolean(KEY_SHOW_ARABIC, true)),
            showMarketName = prefs.getBoolean(personal(KEY_SHOW_MARKET), prefs.getBoolean(KEY_SHOW_MARKET, true)),
            showCustomerPrice = prefs.getBoolean(KEY_SHOW_CUSTOMER_PRICE, true),
            showLab2LabPrice = false,
            pdfIncludeCustomerPrice = prefs.getBoolean(KEY_PDF_CUSTOMER_PRICE, true),
            pdfIncludeLab2LabPrice = false,
            pdfShowTotals = prefs.getBoolean(KEY_PDF_TOTALS, true),
            pdfLabName = prefs.getString(KEY_PDF_LAB_NAME, "تحاليل العقاد").orEmpty().ifBlank { "تحاليل العقاد" },
            brandTagline = prefs.getString(KEY_BRAND_TAGLINE, "دليل التحاليل والأسعار").orEmpty().ifBlank { "دليل التحاليل والأسعار" },
            brandWhatsApp = prefs.getString(KEY_BRAND_WHATSAPP, "").orEmpty(),
            brandPhone = prefs.getString(KEY_BRAND_PHONE, "").orEmpty(),
            brandAddress = prefs.getString(KEY_BRAND_ADDRESS, "").orEmpty(),
            brandLogoPath = prefs.getString(KEY_BRAND_LOGO_PATH, "").orEmpty(),
            pdfShowContactInfo = prefs.getBoolean(KEY_PDF_CONTACT_ENABLED, false),
            pdfContactInfo = prefs.getString(KEY_PDF_CONTACT_INFO, "").orEmpty(),
            lastSyncMillis = prefs.getLong(KEY_LAST_SYNC, 0L),
            recentSearches = prefs.getString(personal(KEY_RECENT_SEARCHES), "").orEmpty()
                .split("\u001F").filter { it.isNotBlank() }.take(8),
            favoriteTestIds = prefs.getStringSet(personal(KEY_FAVORITE_TEST_IDS), emptySet())
                .orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        )
    }

    private fun personal(key: String): String = "profile_${activeProfile}_$key"

    private fun hashPinLegacy(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    private fun hashPinV2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hashPinV3(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_PBKDF2_STRONG_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val PREFS_NAME = "tahalil_manager_settings_v5"
        const val KEY_LANGUAGE = "language"
        const val KEY_SECURITY_ENABLED = "security_enabled"
        const val KEY_UNLOCK_MODE = "unlock_mode"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_VERSION = "pin_version"
        const val PIN_VERSION_LEGACY = 1
        const val PIN_VERSION_PBKDF2 = 2
        const val PIN_VERSION_PBKDF2_STRONG = 3
        const val PIN_PBKDF2_ITERATIONS = 120_000
        const val PIN_PBKDF2_STRONG_ITERATIONS = 310_000
        const val KEY_AUTO_LOCK_SECONDS = "auto_lock_seconds"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_SHOW_ENGLISH = "show_english"
        const val KEY_SHOW_ARABIC = "show_arabic"
        const val KEY_SHOW_MARKET = "show_market"
        const val KEY_SHOW_CUSTOMER_PRICE = "show_customer_price"
        const val KEY_SHOW_LAB_PRICE = "show_lab_price"
        const val KEY_PDF_CUSTOMER_PRICE = "pdf_customer_price"
        const val KEY_PDF_LAB_PRICE = "pdf_lab_price"
        const val KEY_PDF_TOTALS = "pdf_totals"
        const val KEY_PDF_LAB_NAME = "pdf_lab_name"
        const val KEY_BRAND_TAGLINE = "brand_tagline"
        const val KEY_BRAND_WHATSAPP = "brand_whatsapp"
        const val KEY_BRAND_PHONE = "brand_phone"
        const val KEY_BRAND_ADDRESS = "brand_address"
        const val KEY_BRAND_LOGO_PATH = "brand_logo_path"
        const val KEY_PDF_CONTACT_ENABLED = "pdf_contact_enabled"
        const val KEY_PDF_CONTACT_INFO = "pdf_contact_info"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_FAVORITE_TEST_IDS = "favorite_test_ids"
        const val KEY_HANDLED_PIN_RESET_TOKEN = "handled_pin_reset_token"
    }
}
