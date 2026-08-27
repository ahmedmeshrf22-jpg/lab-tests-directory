package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * V39: Reads a Tahalil Alakkad customer QR from an image or PDF selected/shared on the same phone.
 * Uses the ZXing dependency already bundled for QR creation, so no camera permission is required.
 */
object CustomerQrImport {

    private const val MAX_PDF_PAGES_TO_SCAN = 25
    private const val MAX_RENDER_EDGE = 2200

    fun readQrPayload(context: Context, uri: Uri): Result<String> = runCatching {
        val validated = SecureFileGuard.validate(context, uri).getOrThrow()

        val payload = when (validated.kind) {
            SecureFileGuard.FileKind.PDF -> readFromPdf(context, uri)
            SecureFileGuard.FileKind.IMAGE -> readFromImage(context, uri)
        }

        payload ?: error("لم يتم العثور على QR عميل واضح داخل الملف")
    }

    private fun readFromImage(context: Context, uri: Uri): String? {
        val bitmap = SecureFileGuard.decodeImageSafely(context, uri)

        return try {
            decodeBitmapWithRotations(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun readFromPdf(context: Context, uri: Uri): String? {
        val validated = SecureFileGuard.requireKind(context, uri, SecureFileGuard.FileKind.PDF)

        // Cloud/WhatsApp providers can expose a non-seekable descriptor. PdfRenderer requires
        // a seekable file, so use the same private-cache strategy as the lab-test PDF reader.
        val tempPdf = File.createTempFile("customer_qr_import_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdf).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= validated.sizeBytes + 64 * 1024L) {
                            "حجم ملف PDF تغير أثناء القراءة. أعد اختيار الملف"
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    require(total > 0L) { "ملف PDF فارغ" }
                }
            } ?: error("تعذر فتح ملف PDF")

            val descriptor = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        require(renderer.pageCount > 0) { "ملف PDF بدون صفحات" }
                        val pagesToScan = minOf(renderer.pageCount, MAX_PDF_PAGES_TO_SCAN)
                        for (pageIndex in 0 until pagesToScan) {
                            renderer.openPage(pageIndex).use { page ->
                                val scale = minOf(
                                    MAX_RENDER_EDGE.toFloat() / max(1, page.width),
                                    MAX_RENDER_EDGE.toFloat() / max(1, page.height),
                                    2.5f
                                ).coerceIn(0.25f, 2.5f)

                                val width = max(1, (page.width * scale).roundToInt())
                                val height = max(1, (page.height * scale).roundToInt())
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    decodeBitmapWithRotations(bitmap)?.let { return it }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
                error("ملف PDF محمي بكلمة مرور أو لا يسمح بقراءته")
            } catch (_: IllegalArgumentException) {
                error("ملف PDF غير صالح أو مشفر بطريقة غير مدعومة")
            }
            return null
        } finally {
            runCatching { tempPdf.delete() }
        }
    }

    private fun decodeBitmapWithRotations(source: Bitmap): String? {
        decodeBitmap(source)?.let { return it }

        // Some gallery apps store JPEG orientation separately. Try physical rotations as a fallback.
        for (angle in intArrayOf(90, 180, 270)) {
            val matrix = Matrix().apply { postRotate(angle.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            try {
                decodeBitmap(rotated)?.let { return it }
            } finally {
                if (rotated !== source) rotated.recycle()
            }
        }
        return null
    }

    private fun decodeBitmap(bitmap: Bitmap): String? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))

        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }

        return try {
            MultiFormatReader().decode(binary, hints).text?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
