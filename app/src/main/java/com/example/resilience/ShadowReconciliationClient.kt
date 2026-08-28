package com.example.resilience

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object ShadowReconciliationClient {
    private const val ENDPOINT = "https://ogidyelgmigcvasizzuv.supabase.co/functions/v1/tahalil-shadow-reconcile"
    private const val PREFS = "v139_shadow_reconcile"
    private const val LAST_SUCCESS = "last_success_ms"
    private const val LAST_ATTEMPT = "last_attempt_ms"
    private const val SUCCESS_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val RETRY_INTERVAL_MS = 60L * 60L * 1000L
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TahalilShadowReconcile").apply { isDaemon = true }
    }

    fun runIfDue(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_SUCCESS, 0L) < SUCCESS_INTERVAL_MS) return
        if (now - prefs.getLong(LAST_ATTEMPT, 0L) < RETRY_INTERVAL_MS) return
        prefs.edit().putLong(LAST_ATTEMPT, now).apply()

        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val token = tokenResult.token.orEmpty()
                if (token.isBlank()) return@addOnSuccessListener
                executor.execute {
                    val code = request(app, token)
                    if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        user.getIdToken(true).addOnSuccessListener { refreshed ->
                            val fresh = refreshed.token.orEmpty()
                            if (fresh.isNotBlank()) executor.execute {
                                if (request(app, fresh) in 200..299) {
                                    prefs.edit().putLong(LAST_SUCCESS, System.currentTimeMillis()).apply()
                                }
                            }
                        }
                    } else if (code in 200..299) {
                        prefs.edit().putLong(LAST_SUCCESS, System.currentTimeMillis()).apply()
                    }
                }
            }
    }

    private fun request(context: Context, token: String): Int {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Tahalil-Device", deviceId(context))
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            try {
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
            } catch (_: Throwable) { }
            code
        } catch (_: Throwable) {
            -1
        } finally {
            connection?.disconnect()
        }
    }

    private fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val seed = "$androidId|${context.packageName}"
        return MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }
}
