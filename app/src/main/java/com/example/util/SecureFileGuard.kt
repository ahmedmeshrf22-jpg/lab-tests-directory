package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.InputStream
import kotlin.math.max

/**
 * Defense-in-depth gate for every external image/PDF consumed by the app.
 * Accepts bounded content:// streams, verifies the real file signature,
 * and decodes images with memory limits. V60 OCR fix adds HEIC/HEIF/AVIF
 * handling and treats unknown provider sizes correctly instead of rejecting them.
 */
object SecureFileGuard {

    enum class FileKind { IMAGE, PDF }

    data class ValidatedInput(
        val kind: FileKind,
        val detectedMime: String,
        val sizeBytes: Long
    )

    private const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
    private const val MAX_PDF_BYTES = 30L * 1024L * 1024L
    private const val MAX_IMAGE_EDGE = 4096
    private const val MAX_IMAGE_PIXELS = 12_000_000L
    private const val MAX_DECLARED_NAME_LENGTH = 180

    private val imageMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
        "image/avif"
    )

    fun validate(context: Context, uri: Uri): Result<ValidatedInput> = runCatching {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "لأمان التطبيق، مسموح فقط بالملفات المختارة من الجهاز أو التطبيقات الموثوقة"
        }

        val resolver = context.contentResolver
        val declaredMime = resolver.getType(uri)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        val displayName = queryDisplayName(resolver, uri)
        require(displayName.length <= MAX_DECLARED_NAME_LENGTH) { "اسم الملف غير صالح" }

        val header = resolver.openInputStream(uri)?.use { input -> readHeader(input, 64) }
            ?: error("تعذر فتح الملف")

        val detected = detectKind(header) ?: error("نوع الصورة أو الملف غير مدعوم")
        val detectedMime = when (detected) {
            FileKind.PDF -> "application/pdf"
            FileKind.IMAGE -> detectImageMime(header)
                ?: error("صيغة الصورة غير مدعومة")
        }

        if (declaredMime.isNotBlank() && declaredMime != "application/octet-stream") {
            val declaredKind = when {
                declaredMime == "application/pdf" -> FileKind.PDF
                declaredMime in imageMimeTypes || declaredMime.startsWith("image/") -> FileKind.IMAGE
                else -> null
            }
            require(declaredKind != null && declaredKind == detected) {
                "نوع الملف المعلن لا يطابق محتواه الحقيقي"
            }
        }

        val maxBytes = if (detected == FileKind.PDF) MAX_PDF_BYTES else MAX_IMAGE_BYTES
        val size = resolveSize(resolver, uri, maxBytes)
        require(size > 0L) { "الملف فارغ أو تعذر تحديد محتواه" }
        require(size <= maxBytes) {
            if (detected == FileKind.PDF) {
                "ملف PDF أكبر من الحد الآمن (30 MB)"
            } else {
                "الصورة أكبر من الحد الآمن (20 MB)"
            }
        }

        ValidatedInput(detected, detectedMime, size)
    }

    fun requireKind(context: Context, uri: Uri, expected: FileKind): ValidatedInput {
        val validated = validate(context, uri).getOrThrow()
        require(validated.kind == expected) {
            if (expected == FileKind.PDF) "الملف المختار ليس PDF صالحًا" else "الملف المختار ليس صورة صالحة"
        }
        return validated
    }

    fun decodeImageSafely(context: Context, uri: Uri): Bitmap {
        val validated = requireKind(context, uri, FileKind.IMAGE)

        // ImageDecoder handles modern phone formats such as HEIC/HEIF and AVIF.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeWithImageDecoder(context, uri)
        }

        // API 24-27 fallback for JPEG/PNG/WEBP and any codec supported by the device.
        return decodeWithBitmapFactory(context, uri, validated.detectedMime)
    }

    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0) { "الصورة غير صالحة" }
            require(width <= 100_000 && height <= 100_000) { "أبعاد الصورة غير آمنة" }

            var sample = 1
            while (
                max(1, width / sample) > MAX_IMAGE_EDGE ||
                max(1, height / sample) > MAX_IMAGE_EDGE ||
                (width.toLong() / sample) * (height.toLong() / sample) > MAX_IMAGE_PIXELS
            ) {
                sample *= 2
                require(sample <= 128) { "أبعاد الصورة كبيرة جدًا" }
            }

            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sample)
        }.also { bitmap ->
            require(bitmap.width.toLong() * bitmap.height.toLong() <= MAX_IMAGE_PIXELS * 2L) {
                bitmap.recycle()
                "الصورة تحتاج ذاكرة أكبر من الحد الآمن"
            }
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri, detectedMime: String): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("تعذر فتح الصورة")

        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0) {
            if (detectedMime == "image/heic" || detectedMime == "image/heif" || detectedMime == "image/avif") {
                "هذا النوع من الصور يحتاج إصدار أندرويد أحدث. احفظ الصورة JPG أو PNG ثم حاول مرة أخرى"
            } else {
                "الصورة غير صالحة"
            }
        }
        require(width <= 100_000 && height <= 100_000) { "أبعاد الصورة غير آمنة" }

        var sample = 1
        while (
            max(1, width / sample) > MAX_IMAGE_EDGE ||
            max(1, height / sample) > MAX_IMAGE_EDGE ||
            (width.toLong() / sample) * (height.toLong() / sample) > MAX_IMAGE_PIXELS
        ) {
            sample *= 2
            require(sample <= 128) { "أبعاد الصورة كبيرة جدًا" }
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("تعذر قراءة الصورة")

        require(bitmap.width.toLong() * bitmap.height.toLong() <= MAX_IMAGE_PIXELS * 2L) {
            bitmap.recycle()
            "الصورة تحتاج ذاكرة أكبر من الحد الآمن"
        }

        return bitmap
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun resolveSize(resolver: ContentResolver, uri: Uri, limit: Long): Long {
        val fromCursor = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        // Some Android/cloud providers report 0 while the stream is valid. Treat it as unknown.
        if (fromCursor > 0L) return fromCursor

        val fromDescriptor = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        if (fromDescriptor > 0L) return fromDescriptor

        return resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return@use total
            }
            total
        } ?: -1L
    }

    private fun readHeader(input: InputStream, count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(bytes, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return bytes.copyOf(offset)
    }

    private fun detectKind(header: ByteArray): FileKind? {
        if (header.size >= 5 && header.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") {
            return FileKind.PDF
        }
        return if (detectImageMime(header) != null) FileKind.IMAGE else null
    }

    private fun detectImageMime(header: ByteArray): String? {
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) return "image/jpeg"

        if (header.size >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()
        ) return "image/png"

        if (header.size >= 12 &&
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        ) return "image/webp"

        // HEIC/HEIF/AVIF use ISO Base Media File Format with an ftyp box.
        if (header.size >= 12 && header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp") {
            val ascii = header.toString(Charsets.US_ASCII)
            val heifBrands = listOf("heic", "heix", "hevc", "hevx", "heim", "heis", "heif", "mif1", "msf1")
            if (heifBrands.any { ascii.contains(it) }) return "image/heic"
            if (ascii.contains("avif") || ascii.contains("avis")) return "image/avif"
        }

        return null
    }
}
