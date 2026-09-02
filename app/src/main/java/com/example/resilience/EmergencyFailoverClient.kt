package com.example.resilience

import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object EmergencyFailoverClient {
    private const val ENDPOINT =
        "https://ogidyelgmigcvasizzuv.supabase.co/functions/v1/tahalil-failover-read"
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 12_000

    data class ShadowDocument(
        val path: String,
        val id: String,
        val fields: Map<String, Any?>,
        val updatedAtMillis: Long
    )

    data class FetchResult(
        val ok: Boolean,
        val status: String,
        val documents: List<ShadowDocument> = emptyList()
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tahalil-failover-read-v138").apply { isDaemon = true }
    }

    fun fetch(
        context: Context,
        entityType: String,
        prefix: String = "",
        onResult: (FetchResult) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(FetchResult(false, "not_signed_in"))
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            val firstToken = runCatching {
                Tasks.await(user.getIdToken(false), 8, TimeUnit.SECONDS).token
            }.getOrNull().orEmpty()
            if (firstToken.isBlank()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(FetchResult(false, "token_unavailable"))
                }
                return@execute
            }

            val first = request(appContext, firstToken, entityType, prefix)
            val final = if (first.first == HttpURLConnection.HTTP_UNAUTHORIZED &&
                FirebaseAuth.getInstance().currentUser?.uid == user.uid
            ) {
                val refreshed = runCatching {
                    Tasks.await(user.getIdToken(true), 8, TimeUnit.SECONDS).token
                }.getOrNull().orEmpty()
                if (refreshed.isNotBlank()) request(appContext, refreshed, entityType, prefix) else first
            } else first

            val parsed = parse(final.first, final.second)
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(parsed) }
        }
    }

    private fun request(
        context: Context,
        token: String,
        entityType: String,
        prefix: String
    ): Pair<Int, String> {
        val query = buildString {
            append("?entityType=")
            append(URLEncoder.encode(entityType, "UTF-8"))
            if (prefix.isNotBlank()) {
                append("&prefix=")
                append(URLEncoder.encode(prefix, "UTF-8"))
            }
        }
        val conn = (URL(ENDPOINT + query).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Tahalil-Device", currentDeviceId(context))
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = runCatching { stream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            code to text
        } catch (_: Throwable) {
            -1 to ""
        } finally {
            conn.disconnect()
        }
    }

    private fun currentDeviceId(context: Context): String {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty().ifBlank { "unknown-android-id" }
        val material = "$raw|${context.packageName}"
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    private fun parse(code: Int, body: String): FetchResult {
        if (code != HttpURLConnection.HTTP_OK || body.isBlank()) {
            val status = when (code) {
                HttpURLConnection.HTTP_UNAUTHORIZED -> "unauthorized"
                HttpURLConnection.HTTP_FORBIDDEN -> "device_not_approved"
                else -> "unavailable"
            }
            return FetchResult(false, status)
        }
        return runCatching {
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) return@runCatching FetchResult(false, "rejected")
            val array = root.optJSONArray("documents") ?: JSONArray()
            val docs = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val path = item.optString("path").trim()
                    val payload = item.optJSONObject("payload") ?: continue
                    val fieldsJson = payload.optJSONObject("fields") ?: JSONObject()
                    if (path.isBlank()) continue
                    add(
                        ShadowDocument(
                            path = path,
                            id = path.substringAfterLast('/'),
                            fields = parseFields(fieldsJson),
                            updatedAtMillis = item.optLong("updatedAtMs", 0L)
                        )
                    )
                }
            }
            FetchResult(true, "shadow_read_only", docs)
        }.getOrElse { FetchResult(false, "invalid_response") }
    }

    private fun parseFields(fields: JSONObject): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        val keys = fields.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = parseValue(fields.optJSONObject(key))
        }
        return out
    }

    private fun parseValue(value: JSONObject?): Any? {
        if (value == null) return null
        if (value.has("nullValue")) return null
        if (value.has("stringValue")) return value.optString("stringValue")
        if (value.has("integerValue")) return value.optString("integerValue").toLongOrNull() ?: 0L
        if (value.has("doubleValue")) return value.optDouble("doubleValue")
        if (value.has("booleanValue")) return value.optBoolean("booleanValue")
        if (value.has("timestampValue")) return value.optString("timestampValue")
        val array = value.optJSONObject("arrayValue")?.optJSONArray("values")
        if (array != null) {
            return buildList {
                for (i in 0 until array.length()) add(parseValue(array.optJSONObject(i)))
            }
        }
        val map = value.optJSONObject("mapValue")?.optJSONObject("fields")
        if (map != null) return parseFields(map)
        return null
    }
}
