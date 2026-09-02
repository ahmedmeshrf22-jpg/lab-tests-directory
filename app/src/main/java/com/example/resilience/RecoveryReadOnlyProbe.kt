package com.example.resilience

import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * V130 read-only disaster-recovery probe.
 *
 * This NEVER returns production data from Supabase and NEVER writes to Firebase or Supabase.
 * It only asks the recovery Edge Function to compare recent shadow metadata with the current
 * Firestore documents visible to the signed-in Firebase user.
 */
object RecoveryReadOnlyProbe {
    private const val ENDPOINT =
        "https://ogidyelgmigcvasizzuv.supabase.co/functions/v1/tahalil-recovery-probe"
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 14_000

    data class Result(
        val ok: Boolean,
        val status: String,
        val examined: Int = 0,
        val exact: Int = 0,
        val stale: Int = 0,
        val missing: Int = 0,
        val denied: Int = 0
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tahalil-recovery-probe-v130").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun run(onResult: (Result) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            deliver(onResult, Result(false, "not_signed_in"))
            return
        }
        executor.execute {
            val firstToken = runCatching {
                Tasks.await(user.getIdToken(false), 8, TimeUnit.SECONDS).token
            }.getOrNull().orEmpty()
            if (firstToken.isBlank()) {
                deliver(onResult, Result(false, "token_unavailable"))
                return@execute
            }

            val first = request(firstToken)
            if (first.first == HttpURLConnection.HTTP_UNAUTHORIZED &&
                FirebaseAuth.getInstance().currentUser?.uid == user.uid
            ) {
                val refreshed = runCatching {
                    Tasks.await(user.getIdToken(true), 8, TimeUnit.SECONDS).token
                }.getOrNull().orEmpty()
                if (refreshed.isNotBlank()) {
                    val second = request(refreshed)
                    deliver(onResult, parse(second.first, second.second))
                    return@execute
                }
            }
            deliver(onResult, parse(first.first, first.second))
        }
    }

    private fun request(token: String): Pair<Int, String> {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
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

    private fun parse(code: Int, body: String): Result {
        if (code != HttpURLConnection.HTTP_OK || body.isBlank()) {
            return Result(false, if (code == HttpURLConnection.HTTP_UNAUTHORIZED) "unauthorized" else "unavailable")
        }
        return runCatching {
            val json = JSONObject(body)
            Result(
                ok = json.optBoolean("ok", false),
                status = json.optString("status", "unknown"),
                examined = json.optInt("examined", 0),
                exact = json.optInt("exact", 0),
                stale = json.optInt("stale", 0),
                missing = json.optInt("missing", 0),
                denied = json.optInt("denied", 0)
            )
        }.getOrElse { Result(false, "invalid_response") }
    }

    private fun deliver(onResult: (Result) -> Unit, result: Result) {
        main.post { onResult(result) }
    }
}
