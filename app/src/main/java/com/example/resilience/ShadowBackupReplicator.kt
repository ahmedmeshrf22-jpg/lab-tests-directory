package com.example.resilience

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * V129 best-effort shadow backup bridge.
 *
 * Safety invariants:
 *  - Firebase remains the only authoritative backend for every production read/write.
 *  - Supabase is never consulted to decide whether a Firebase operation succeeds.
 *  - No Supabase secret or publishable key is shipped in the APK.
 *  - V129 creates NO additional Firestore listeners or broad backup queries. It only mirrors
 *    snapshots already read by existing application flows, plus explicit post-success write hooks.
 *  - The Edge Function receives the current Firebase ID token, validates it server-side,
 *    then fetches the authoritative Firestore document itself before storing a shadow copy.
 *  - Failures are silent and retry naturally on a later normal app read/write.
 */
object ShadowBackupReplicator {
    private const val ENDPOINT =
        "https://ogidyelgmigcvasizzuv.supabase.co/functions/v1/tahalil-shadow-mirror"
    private const val PREFS = "tahalil_shadow_backup_v129"
    private const val MAX_QUEUE = 300
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 9_000

    private val lock = Any()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val executor = ThreadPoolExecutor(
        1, 1,
        30L, TimeUnit.SECONDS,
        ArrayBlockingQueue(MAX_QUEUE),
        { runnable -> Thread(runnable, "tahalil-shadow-v129").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    @Volatile private var activeUid: String = ""
    @Volatile private var activeRole: String = ""
    @Volatile private var appContext: Context? = null

    /**
     * Enables only the lightweight mirror hooks. No Firestore read/listener is started here.
     * Role is retained for diagnostics/future policy but does not grant additional data access.
     */
    fun start(context: Context, normalizedRole: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        synchronized(lock) {
            appContext = context.applicationContext
            if (activeUid != user.uid) inFlight.clear()
            activeUid = user.uid
            activeRole = normalizedRole
        }
    }

    fun stop() {
        synchronized(lock) {
            inFlight.clear()
            activeUid = ""
            activeRole = ""
        }
    }

    /** Explicit post-success hook for a Firestore document already touched by normal app flow. */
    fun mirrorPath(
        firestorePath: String,
        updatedAtMillis: Long = System.currentTimeMillis(),
        tombstone: Boolean = false
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid != activeUid || firestorePath.isBlank()) return
        enqueue(firestorePath, updatedAtMillis.coerceAtLeast(0L), tombstone, fingerprint = null)
    }

    /** Mirrors an already-loaded Firestore snapshot without trusting its payload on Supabase. */
    fun mirrorSnapshot(snapshot: DocumentSnapshot) {
        if (!snapshot.exists() || snapshot.metadata.hasPendingWrites()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid != activeUid) return
        mirrorSnapshotInternal(snapshot)
    }

    private fun mirrorSnapshotInternal(snapshot: DocumentSnapshot) {
        val path = snapshot.reference.path
        val dataHash = snapshot.data?.hashCode() ?: 0
        val logicalMs = firstLong(snapshot, "updated_at_ms", "result_sent_at_ms", "file_created_ms", "created_at_ms")
        val fingerprint = "v:${logicalMs}:${dataHash}"
        val prefs = prefs() ?: return
        val key = fingerprintKey(path)
        if (prefs.getString(key, null) == fingerprint) return
        enqueue(path, logicalMs.coerceAtLeast(0L), tombstone = false, fingerprint = fingerprint)
    }

    private fun firstLong(snapshot: DocumentSnapshot, vararg fields: String): Long {
        fields.forEach { field ->
            val n = snapshot.get(field) as? Number
            if (n != null) return n.toLong()
            val ts = runCatching { snapshot.getTimestamp(field) }.getOrNull()
            if (ts != null) return ts.toDate().time
        }
        return 0L
    }

    private fun enqueue(path: String, updatedAtMillis: Long, tombstone: Boolean, fingerprint: String?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (uid != activeUid) return
        val flightKey = "$uid|$path|$tombstone|${fingerprint.orEmpty()}"
        if (!inFlight.add(flightKey)) return
        try {
            executor.execute {
                try {
                    val ok = postShadow(uid, path, updatedAtMillis, tombstone)
                    if (ok && fingerprint != null && !tombstone) {
                        prefs()?.edit()?.putString(fingerprintKey(path), fingerprint)?.apply()
                    } else if (ok && tombstone) {
                        prefs()?.edit()?.remove(fingerprintKey(path))?.apply()
                    }
                    if (ok) {
                        prefs()?.edit()?.putLong("last_success_ms", System.currentTimeMillis())?.apply()
                    }
                } catch (_: Throwable) {
                    prefs()?.edit()?.putLong("last_failure_ms", System.currentTimeMillis())?.apply()
                    // Shadow backup is deliberately non-blocking: Firebase operation/UI is untouched.
                } finally {
                    inFlight.remove(flightKey)
                }
            }
        } catch (_: Throwable) {
            inFlight.remove(flightKey)
        }
    }

    private fun postShadow(expectedUid: String, path: String, updatedAtMillis: Long, tombstone: Boolean): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        if (user.uid != expectedUid || activeUid != expectedUid) return false
        val token1 = runCatching {
            Tasks.await(user.getIdToken(false), 8, TimeUnit.SECONDS).token
        }.getOrNull().orEmpty()
        if (token1.isBlank()) return false

        val first = sendRequest(token1, path, updatedAtMillis, tombstone)
        if (first == HttpURLConnection.HTTP_OK) return true
        if (first != HttpURLConnection.HTTP_UNAUTHORIZED) return false

        // One forced token refresh only. Never loop/retry against the primary workflow.
        if (!sessionMatches(expectedUid)) return false
        val token2 = runCatching {
            Tasks.await(user.getIdToken(true), 8, TimeUnit.SECONDS).token
        }.getOrNull().orEmpty()
        if (token2.isBlank()) return false
        return sendRequest(token2, path, updatedAtMillis, tombstone) == HttpURLConnection.HTTP_OK
    }

    private fun sendRequest(token: String, path: String, updatedAtMillis: Long, tombstone: Boolean): Int {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val body = JSONObject()
                .put("firestorePath", path)
                .put("updatedAtMillis", updatedAtMillis)
                .put("tombstone", tombstone)
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("appVersionName", BuildConfig.VERSION_NAME)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            runCatching { (if (code >= 400) conn.errorStream else conn.inputStream)?.close() }
            code
        } finally {
            conn.disconnect()
        }
    }

    private fun sessionMatches(expectedUid: String): Boolean =
        expectedUid.isNotBlank() && activeUid == expectedUid &&
            FirebaseAuth.getInstance().currentUser?.uid == expectedUid

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fingerprintKey(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
        return "doc_" + digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
