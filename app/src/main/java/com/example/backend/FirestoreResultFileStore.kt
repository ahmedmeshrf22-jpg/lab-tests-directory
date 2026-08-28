package com.example.backend

import com.example.resilience.ResultFailoverClient

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.resilience.ShadowBackupReplicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Firebase-only result storage. Result files are chunked into Firestore documents,
 * reconstructed locally, integrity-checked with SHA-256, and can be opened/shared/downloaded.
 */
object FirestoreResultFileStore {
    private const val RAW_CHUNK_BYTES = 420 * 1024
    private const val MAX_FILE_BYTES = 6 * 1024 * 1024
    private const val META_PREFIX = "resultmeta_"
    private const val CHUNK_PREFIX = "resultchunk_"

    private data class LoadedResult(
        val fileId: String,
        val name: String,
        val mime: String,
        val bytes: ByteArray
    )

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun uploadResult(
        context: Context,
        uri: Uri,
        customerId: String,
        orderId: String,
        displayName: String,
        onResult: (Boolean, String?, String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(false, null, "سجل الدخول أولاً")
            return
        }
        if (customerId.isBlank() || orderId.isBlank()) {
            onResult(false, null, "بيانات الطلب غير مكتملة")
            return
        }

        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().lowercase(Locale.US)
        if (!(mime == "application/pdf" || mime.startsWith("image/"))) {
            onResult(false, null, "الملفات المسموحة صور أو PDF فقط")
            return
        }

        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) throw IOException("حجم الملف أكبر من 6 MB")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: throw IOException("تعذر قراءة الملف")
        } catch (e: Exception) {
            onResult(false, null, e.localizedMessage ?: "تعذر قراءة الملف")
            return
        }

        if (bytes.isEmpty()) {
            onResult(false, null, "الملف فارغ")
            return
        }

        val fileId = UUID.randomUUID().toString().replace("-", "")
        val safeName = sanitizeName(displayName, mime)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val end = (offset + RAW_CHUNK_BYTES).coerceAtMost(bytes.size)
            chunks += bytes.copyOfRange(offset, end)
            offset = end
        }
        val activity = firestore.collection("customers").document(customerId).collection("activity")

        fun writeChunk(index: Int) {
            if (index >= chunks.size) {
                val digest = sha256Hex(bytes)
                val meta = mapOf(
                    "type" to "result_file_meta",
                    "file_id" to fileId,
                    "order_id" to orderId,
                    "file_name" to safeName,
                    "mime_type" to mime,
                    "size_bytes" to bytes.size.toLong(),
                    "chunk_count" to chunks.size,
                    "sha256" to digest,
                    "sha256_hex" to digest,
                    "upload_complete" to true,
                    "actor_uid" to user.uid,
                    "actor_email" to user.email.orEmpty(),
                    "file_created_ms" to System.currentTimeMillis()
                )
                val metaRef = activity.document("$META_PREFIX$fileId")
                metaRef.set(meta)
                    .addOnSuccessListener {
                        ShadowBackupReplicator.mirrorPath(metaRef.path, System.currentTimeMillis())
                        val ref = Uri.Builder().scheme("fsr").appendPath(customerId).appendPath(fileId).build().toString()
                        onResult(true, ref, "تم رفع الملف")
                    }
                    .addOnFailureListener { error ->
                        onResult(false, null, error.localizedMessage ?: "تعذر حفظ بيانات الملف")
                    }
                return
            }

            val encoded = Base64.encodeToString(chunks[index], Base64.NO_WRAP)
            val chunkData = mapOf(
                "type" to "result_file_chunk",
                "file_id" to fileId,
                "order_id" to orderId,
                "chunk_index" to index,
                "chunk_count" to chunks.size,
                "data_b64" to encoded,
                "actor_uid" to user.uid,
                "actor_email" to user.email.orEmpty()
            )
            val chunkId = "$CHUNK_PREFIX${fileId}_${index.toString().padStart(3, '0')}"
            activity.document(chunkId).set(chunkData)
                .addOnSuccessListener { writeChunk(index + 1) }
                .addOnFailureListener { error ->
                    onResult(false, null, error.localizedMessage ?: "تعذر رفع جزء من الملف")
                }
        }

        writeChunk(0)
    }

    fun openResult(context: Context, storedRef: String, onResult: (Boolean, String) -> Unit) {
        openResultPrimaryV139(
            context,
            storedRef,
            { ok, message ->
            if (ok || !storedRef.startsWith("fsr:")) {
                onResult(ok, message)
            } else {
                ResultFailoverClient.open(context, storedRef) { fallbackOk, fallbackMessage ->
                    if (fallbackOk) onResult(true, fallbackMessage) else onResult(false, message)
                }
            }
        }
        )
    }

    fun openResultPrimaryV139(context: Context, storedRef: String, onResult: (Boolean, String) -> Unit) {
        if (!storedRef.startsWith("fsr:")) {
            openExternal(context, storedRef, null, onResult)
            return
        }
        loadStoredResult(storedRef) { loaded, error ->
            if (loaded == null) {
                onResult(false, error)
                return@loadStoredResult
            }
            try {
                val contentUri = cacheResultFile(context, loaded)
                openExternal(context, contentUri.toString(), loaded.mime, onResult)
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "تعذر فتح ملف النتيجة")
            }
        }
    }

    /** V121: share the actual PDF/image through Android Sharesheet. */
    fun shareResult(context: Context, storedRef: String, onResult: (Boolean, String) -> Unit) {
        if (!storedRef.startsWith("fsr:")) {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, storedRef)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(send, "مشاركة نتيجة التحاليل").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onResult(true, "")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "تعذر مشاركة النتيجة")
            }
            return
        }

        loadStoredResult(storedRef) { loaded, error ->
            if (loaded == null) {
                onResult(false, error)
                return@loadStoredResult
            }
            try {
                val contentUri = cacheResultFile(context, loaded)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = loaded.mime
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    clipData = ClipData.newRawUri("result", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "مشاركة نتيجة التحاليل").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onResult(true, "")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "تعذر مشاركة النتيجة")
            }
        }
    }

    /** V121: save a local copy into Downloads/Tahalil Alakkad on modern Android. */
    fun saveResultToDownloads(
        context: Context,
        storedRef: String,
        displayName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!storedRef.startsWith("fsr:")) {
            onResult(false, "النتائج القديمة بالرابط تُفتح مباشرة؛ التحميل المباشر متاح للنتائج المرفوعة من داخل النظام")
            return
        }

        loadStoredResult(storedRef) { loaded, error ->
            if (loaded == null) {
                onResult(false, error)
                return@loadStoredResult
            }
            val safeName = sanitizeName(displayName.ifBlank { loaded.name }, loaded.mime)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                        put(MediaStore.MediaColumns.MIME_TYPE, loaded.mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Tahalil Alakkad")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("تعذر إنشاء ملف في التنزيلات")
                    try {
                        resolver.openOutputStream(uri, "w")?.use { it.write(loaded.bytes) }
                            ?: throw IOException("تعذر كتابة ملف النتيجة")
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } catch (e: Exception) {
                        runCatching { resolver.delete(uri, null, null) }
                        throw e
                    }
                } else {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: File(context.filesDir, "downloads")
                    dir.mkdirs()
                    File(dir, safeName).writeBytes(loaded.bytes)
                }
                onResult(true, "تم تحميل النتيجة في مجلد Downloads")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "تعذر تحميل النتيجة")
            }
        }
    }

    private fun loadStoredResult(storedRef: String, onLoaded: (LoadedResult?, String) -> Unit) {
        val parsed = runCatching { Uri.parse(storedRef) }.getOrNull()
        val segments = parsed?.pathSegments.orEmpty()
        if (segments.size < 2) {
            onLoaded(null, "مرجع ملف النتيجة غير صالح")
            return
        }
        val customerId = segments[0]
        val fileId = segments[1]
        val activity = firestore.collection("customers").document(customerId).collection("activity")

        activity.document("$META_PREFIX$fileId").get()
            .addOnSuccessListener metaSuccess@{ meta ->
                if (!meta.exists()) {
                    onLoaded(null, "ملف النتيجة غير موجود")
                    return@metaSuccess
                }
                val name = meta.getString("file_name").orEmpty().ifBlank { "result.pdf" }
                val mime = meta.getString("mime_type").orEmpty().ifBlank { "application/pdf" }
                val chunkCount = (meta.getLong("chunk_count") ?: 0L).toInt()
                val expectedSize = meta.getLong("size_bytes") ?: 0L
                val expectedSha256 = meta.getString("sha256").orEmpty()
                    .ifBlank { meta.getString("sha256_hex").orEmpty() }
                    .lowercase(Locale.US)
                if (chunkCount <= 0 || chunkCount > 64) {
                    onLoaded(null, "بيانات ملف النتيجة غير مكتملة")
                    return@metaSuccess
                }

                val output = ByteArrayOutputStream(expectedSize.coerceAtMost(MAX_FILE_BYTES.toLong()).toInt())

                fun readChunk(index: Int) {
                    if (index >= chunkCount) {
                        val bytes = output.toByteArray()
                        if (bytes.isEmpty() || (expectedSize > 0L && bytes.size.toLong() != expectedSize)) {
                            onLoaded(null, "ملف النتيجة غير مكتمل، حاول مرة أخرى")
                            return
                        }
                        if (expectedSha256.isNotBlank() && !MessageDigest.isEqual(
                                expectedSha256.toByteArray(Charsets.US_ASCII),
                                sha256Hex(bytes).toByteArray(Charsets.US_ASCII)
                            )) {
                            onLoaded(null, "فشل فحص سلامة ملف النتيجة")
                            return
                        }
                        onLoaded(LoadedResult(fileId, name, mime, bytes), "")
                        return
                    }

                    val chunkId = "$CHUNK_PREFIX${fileId}_${index.toString().padStart(3, '0')}"
                    activity.document(chunkId).get()
                        .addOnSuccessListener chunkSuccess@{ chunk ->
                            val encoded = chunk.getString("data_b64").orEmpty()
                            if (encoded.isBlank()) {
                                onLoaded(null, "جزء من ملف النتيجة غير موجود")
                                return@chunkSuccess
                            }
                            val decoded = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                            if (decoded == null) {
                                onLoaded(null, "تعذر قراءة ملف النتيجة")
                                return@chunkSuccess
                            }
                            output.write(decoded)
                            readChunk(index + 1)
                        }
                        .addOnFailureListener { error ->
                            onLoaded(null, error.localizedMessage ?: "تعذر تحميل ملف النتيجة")
                        }
                }

                readChunk(0)
            }
            .addOnFailureListener { error ->
                onLoaded(null, error.localizedMessage ?: "تعذر تحميل بيانات النتيجة")
            }
    }

    private fun cacheResultFile(context: Context, loaded: LoadedResult): Uri {
        cleanupStaleResultCache(context)
        val dir = File(context.cacheDir, "results").apply { mkdirs() }
        val file = File(dir, "${loaded.fileId}_${sanitizeName(loaded.name, loaded.mime)}")
        file.writeBytes(loaded.bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun openExternal(context: Context, uriText: String, mime: String?, onResult: (Boolean, String) -> Unit) {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull()
        if (uri == null) {
            onResult(false, "رابط النتيجة غير صالح")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (mime.isNullOrBlank()) data = uri else setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (uri.scheme == "content") clipData = ClipData.newRawUri("result", uri)
            }
            context.startActivity(intent)
            onResult(true, "")
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (uri.scheme == "content") clipData = ClipData.newRawUri("result", uri)
                }
                context.startActivity(fallback)
                onResult(true, "")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "لا يوجد تطبيق مناسب لفتح النتيجة")
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun cleanupStaleResultCache(context: Context, maxAgeMs: Long = 15L * 60L * 1000L) {
        runCatching {
            val dir = File(context.cacheDir, "results")
            if (!dir.exists()) return@runCatching
            val cutoff = System.currentTimeMillis() - maxAgeMs
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private fun sanitizeName(name: String, mime: String): String {
        val fallback = when {
            mime == "application/pdf" -> "result.pdf"
            mime.contains("png") -> "result.png"
            mime.contains("webp") -> "result.webp"
            else -> "result.jpg"
        }
        val clean = name.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .take(80)
        return clean.ifBlank { fallback }
    }
}
