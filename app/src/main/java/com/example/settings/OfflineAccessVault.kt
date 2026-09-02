package com.example.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.AppUserProfile
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * V127 outage-resilience access vault.
 *
 * Stores only the last *server-verified* enabled profile + approved device pair.
 * The record is encrypted with an Android Keystore AES key and may be used only
 * for a short grace window when Firebase/the network is unavailable. Server
 * disable/revoke events clear the record immediately, so this never replaces
 * normal Firebase authorization while the service is reachable.
 */
object OfflineAccessVault {
    private const val PREFS = "tahalil_offline_access_v127"
    private const val KEY_ALIAS = "tahalil_offline_access_v127_aes"
    private const val RECORD_VERSION = 1
    const val MAX_GRACE_MS = 72L * 60L * 60L * 1000L // 72 hours

    private fun recordKey(uid: String, deviceId: String): String = "$uid::$deviceId"

    fun saveApprovedSession(
        context: Context,
        profile: AppUserProfile,
        deviceId: String,
        verifiedAtMillis: Long = System.currentTimeMillis()
    ) {
        if (!profile.enabled || profile.uid.isBlank() || deviceId.isBlank()) return
        runCatching {
            val payload = JSONObject().apply {
                put("version", RECORD_VERSION)
                put("package", context.packageName)
                put("uid", profile.uid)
                put("device_id", deviceId)
                put("email", profile.email)
                put("display_name", profile.displayName)
                put("role", profile.role)
                put("enabled", profile.enabled)
                put("can_edit_customers", profile.canEditCustomers)
                put("can_discount", profile.canDiscount)
                put("can_collect_payments", profile.canCollectPayments)
                put("can_view_sales_reports", profile.canViewSalesReports)
                put("created_at_ms", profile.createdAtMillis)
                put("updated_at_ms", profile.updatedAtMillis)
                put("pin_reset_requested_at_ms", profile.pinResetRequestedAtMillis)
                put("verified_at_ms", verifiedAtMillis)
            }.toString().toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(payload)
            val packed = ByteArray(1 + cipher.iv.size + encrypted.size)
            packed[0] = cipher.iv.size.toByte()
            System.arraycopy(cipher.iv, 0, packed, 1, cipher.iv.size)
            System.arraycopy(encrypted, 0, packed, 1 + cipher.iv.size, encrypted.size)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(recordKey(profile.uid, deviceId), Base64.encodeToString(packed, Base64.NO_WRAP))
                .apply()
        }
    }

    fun loadApprovedSession(
        context: Context,
        uid: String,
        deviceId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AppUserProfile? {
        if (uid.isBlank() || deviceId.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = recordKey(uid, deviceId)
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 18)
            val ivSize = packed[0].toInt() and 0xFF
            require(ivSize in 12..16 && packed.size > 1 + ivSize + 16)
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val encrypted = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val obj = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            require(obj.optInt("version") == RECORD_VERSION)
            require(obj.optString("package") == context.packageName)
            require(obj.optString("uid") == uid)
            require(obj.optString("device_id") == deviceId)
            require(obj.optBoolean("enabled", false))
            val verifiedAt = obj.optLong("verified_at_ms", 0L)
            require(verifiedAt > 0L && nowMillis >= verifiedAt && nowMillis - verifiedAt <= MAX_GRACE_MS)
            AppUserProfile(
                uid = uid,
                email = obj.optString("email"),
                displayName = obj.optString("display_name"),
                role = obj.optString("role", "staff"),
                enabled = true,
                canEditCustomers = obj.optBoolean("can_edit_customers", false),
                canDiscount = obj.optBoolean("can_discount", false),
                canCollectPayments = obj.optBoolean("can_collect_payments", false),
                canViewSalesReports = obj.optBoolean("can_view_sales_reports", false),
                createdAtMillis = obj.optLong("created_at_ms", 0L),
                updatedAtMillis = obj.optLong("updated_at_ms", 0L),
                pinResetRequestedAtMillis = obj.optLong("pin_reset_requested_at_ms", 0L)
            )
        }.getOrElse {
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun clear(context: Context, uid: String, deviceId: String) {
        if (uid.isBlank() || deviceId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(recordKey(uid, deviceId)).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
