package com.example.resilience

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object ResultFailoverClient {
    private const val ENDPOINT = "https://ogidyelgmigcvasizzuv.supabase.co/functions/v1/tahalil-result-failover"
    private const val MAX_FILE_BYTES = 6 * 1024 * 1024
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "TahalilResultFailover").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun open(context: Context, storedRef: String, onResult: (Boolean, String) -> Unit) {
        val parsed = parseRef(storedRef)
        if (parsed == null) {
            onResult(false, "مرجع ملف النتيجة غير صالح")
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(false, "جلسة المستخدم غير متاحة")
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val token = tokenResult.token.orEmpty()
                if (token.isBlank()) {
                    onResult(false, "تعذر التحقق من جلسة المستخدم")
                    return@addOnSuccessListener
                }
                executor.execute {
                    val first = download(context.applicationContext, parsed.first, parsed.second, token)
                    if (first.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        user.getIdToken(true)
                            .addOnSuccessListener { refreshed ->
                                val fresh = refreshed.token.orEmpty()
                                if (fresh.isBlank()) {
                                    main.post { onResult(false, "تعذر تحديث جلسة المستخدم") }
                                } else {
                                    executor.execute {
                                        finish(context, parsed.second, download(context.applicationContext, parsed.first, parsed.second, fresh), onResult)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                main.post { onResult(false, "تعذر تحديث جلسة المستخدم") }
                            }
                    } else {
                        finish(context, parsed.second, first, onResult)
                    }
                }
            }
            .addOnFailureListener {
                onResult(false, "تعذر التحقق من جلسة المستخدم")
            }
    }

    private data class Download(
        val code: Int,
        val bytes: ByteArray? = null,
        val mime: String = "",
        val sha: String = ""
    )

    private fun finish(
        context: Context,
        fileId: String,
        result: Download,
        onResult: (Boolean, String) -> Unit
    ) {
        if (result.code !in 200..299 || result.bytes == null) {
            main.post { onResult(false, "النسخة الاحتياطية لملف النتيجة غير متاحة") }
            return
        }
        try {
            val detected = detectMime(result.bytes)
            if (detected.isBlank()) throw IllegalStateException("unsupported")
            if (result.mime.isNotBlank() &&
                ((result.mime == "application/pdf") != (detected == "application/pdf"))) {
                throw IllegalStateException("mime_mismatch")
            }
            val actualSha = sha256(result.bytes)
            if (result.sha.isNotBlank() && !actualSha.equals(result.sha, ignoreCase = true)) {
                throw IllegalStateException("sha_mismatch")
            }
            val ext = when (detected) {
                "application/pdf" -> "pdf"
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                else -> "bin"
            }
            val dir = File(context.cacheDir, "result_failover").apply { mkdirs() }
            dir.listFiles()
                ?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 24L * 60L * 60L * 1000L }
                ?.forEach { it.delete() }
            val safeId = fileId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
            val file = File(dir, "result_${safeId}.$ext")
            file.writeBytes(result.bytes)
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, detected)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            main.post { onResult(true, "تم فتح النتيجة من النسخة الاحتياطية الآمنة") }
        } catch (_: Throwable) {
            main.post { onResult(false, "تعذر فتح ملف النتيجة من النسخة الاحتياطية") }
        }
    }

    private fun download(
        context: Context,
        customerId: String,
        fileId: String,
        token: String
    ): Download {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$ENDPOINT?customerId=${Uri.encode(customerId)}&fileId=${Uri.encode(fileId)}")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Tahalil-Device", deviceId(context))
                setRequestProperty("Accept", "application/pdf,image/*")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                try { connection.errorStream?.close() } catch (_: Throwable) { }
                Download(code)
            } else {
                val declaredLength = connection.contentLengthLong
                if (declaredLength > MAX_FILE_BYTES) return Download(413)
                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        if (out.size() + n > MAX_FILE_BYTES) return Download(413)
                        out.write(buffer, 0, n)
                    }
                }
                Download(
                    code = code,
                    bytes = out.toByteArray(),
                    mime = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(),
                    sha = connection.getHeaderField("X-Tahalil-SHA256").orEmpty().trim().lowercase()
                )
            }
        } catch (_: Throwable) {
            Download(-1)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseRef(storedRef: String): Pair<String, String>? {
        if (!storedRef.startsWith("fsr:")) return null
        return try {
            val path = storedRef.removePrefix("fsr:").trim()
            val segments = path.trim('/').split('/').filter { it.isNotBlank() }
            if (segments.size < 2) null else {
                val customerId = segments[segments.size - 2]
                val fileId = segments.last()
                if (!customerId.matches(Regex("[A-Za-z0-9_-]{1,240}")) ||
                    !fileId.matches(Regex("[A-Za-z0-9_-]{12,80}"))) null
                else customerId to fileId
            }
        } catch (_: Throwable) {
            null
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun detectMime(b: ByteArray): String {
        if (b.size >= 5 &&
            b[0] == '%'.code.toByte() && b[1] == 'P'.code.toByte() &&
            b[2] == 'D'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[4] == '-'.code.toByte()) return "application/pdf"
        if (b.size >= 3 &&
            (b[0].toInt() and 0xff) == 0xff &&
            (b[1].toInt() and 0xff) == 0xd8 &&
            (b[2].toInt() and 0xff) == 0xff) return "image/jpeg"
        val png = intArrayOf(0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
        if (b.size >= 8 && png.indices.all { (b[it].toInt() and 0xff) == png[it] }) return "image/png"
        if (b.size >= 12 &&
            String(b.copyOfRange(0,4), Charsets.US_ASCII) == "RIFF" &&
            String(b.copyOfRange(8,12), Charsets.US_ASCII) == "WEBP") return "image/webp"
        if (b.size >= 12 &&
            String(b.copyOfRange(4,8), Charsets.US_ASCII) == "ftyp") return "image/heic"
        return ""
    }
}
