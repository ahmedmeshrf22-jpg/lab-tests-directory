package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream

/**
 * V40: OCR helper for extracting printed Latin lab-test names/abbreviations from
 * an image or a PDF selected on the same device. PDF pages are rendered locally
 * then passed through the bundled ML Kit Latin text recognizer.
 */
object TestDocumentImport {

    private const val MAX_PDF_PAGES = 20
    private const val MAX_RENDER_EDGE = 2000
    private const val MAX_VISION_PDF_PAGES = 4
    private const val MAX_VISION_EDGE = 1600

    enum class ReadMode { SMART_VISION, LOCAL_OCR }
    enum class SmartFallbackReason { OFFLINE, VISION_UNAVAILABLE }

    data class DocumentReadResult(
        val text: String,
        val mode: ReadMode,
        val fallbackReason: SmartFallbackReason? = null
    )

    /**
     * V62: smart vision first when an internet connection is available, with the proven
     * bundled OCR pipeline retained as a transparent fallback. The final text is the union
     * of the vision transcription and local OCR so catalogue matching never loses a locally
     * recognized test merely because the cloud response omitted it.
     */
    suspend fun readTests(context: Context, uri: Uri): Result<DocumentReadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validated = SecureFileGuard.validate(context, uri).getOrThrow()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val localText = try {
                runCatching {
                    when (validated.kind) {
                        SecureFileGuard.FileKind.PDF -> readPdf(context, uri, recognizer)
                        SecureFileGuard.FileKind.IMAGE -> readImage(context, uri, recognizer)
                    }
                }.getOrDefault("").trim()
            } finally {
                recognizer.close()
            }

            if (!SmartMedicalVision.isOnline(context)) {
                val fallback = localText.takeIf { it.isNotBlank() }
                    ?: error("لم يتم التعرف على نص واضح. شغّل الإنترنت للقراءة الذكية أو جرّب صورة أوضح")
                return@runCatching DocumentReadResult(
                    text = fallback,
                    mode = ReadMode.LOCAL_OCR,
                    fallbackReason = SmartFallbackReason.OFFLINE
                )
            }

            val visionBitmaps = prepareVisionBitmaps(context, uri, validated.kind)
            try {
                val visionText = SmartMedicalVision
                    .extractRequestedLabTests(visionBitmaps, localText)
                    .getOrNull()
                    .orEmpty()
                    .trim()

                if (visionText.isNotBlank()) {
                    val combined = buildString {
                        append(visionText)
                        if (localText.isNotBlank()) {
                            append('\n')
                            append(localText)
                        }
                    }.trim()
                    return@runCatching DocumentReadResult(
                        text = combined,
                        mode = ReadMode.SMART_VISION
                    )
                }

                val fallback = localText.takeIf { it.isNotBlank() }
                    ?: error("تعذر التعرف على التحاليل من الصورة أو الملف")
                DocumentReadResult(
                    text = fallback,
                    mode = ReadMode.LOCAL_OCR,
                    fallbackReason = SmartFallbackReason.VISION_UNAVAILABLE
                )
            } finally {
                visionBitmaps.forEach { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
    }

    // Kept for compatibility with any older call sites.
    suspend fun readText(context: Context, uri: Uri): Result<String> =
        readTests(context, uri).map { it.text }

    private fun readImage(context: Context, uri: Uri, recognizer: TextRecognizer): String {
        val bitmap = SecureFileGuard.decodeImageSafely(context, uri)

        return try {
            recognizeImageRobust(recognizer, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun readPdf(context: Context, uri: Uri, recognizer: TextRecognizer): String {
        val validated = SecureFileGuard.requireKind(context, uri, SecureFileGuard.FileKind.PDF)

        // PdfRenderer needs a seekable file descriptor. Documents coming from Google Drive,
        // WhatsApp, Files providers, etc. may expose a pipe/non-seekable descriptor even though
        // the PDF opens normally elsewhere. Copy the already-validated PDF to our private cache
        // first so PdfRenderer always receives a local seekable descriptor.
        val tempPdf = File.createTempFile("lab_tests_import_", ".pdf", context.cacheDir)
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
            val collected = StringBuilder()
            try {
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        require(renderer.pageCount > 0) { "ملف PDF بدون صفحات" }
                        val count = minOf(renderer.pageCount, MAX_PDF_PAGES)
                        for (pageIndex in 0 until count) {
                            renderer.openPage(pageIndex).use { page ->
                                val scale = minOf(
                                    MAX_RENDER_EDGE.toFloat() / max(1, page.width),
                                    MAX_RENDER_EDGE.toFloat() / max(1, page.height),
                                    2.5f
                                ).coerceIn(0.5f, 2.5f)

                                val width = max(1, (page.width * scale).roundToInt())
                                val height = max(1, (page.height * scale).roundToInt())
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val pageText = recognizeImageRobust(recognizer, bitmap).trim()
                                    if (pageText.isNotBlank()) {
                                        if (collected.isNotEmpty()) collected.append('\n')
                                        collected.append(pageText)
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                error("ملف PDF محمي بكلمة مرور أو لا يسمح بقراءته")
            } catch (e: IllegalArgumentException) {
                error("ملف PDF غير صالح أو مشفر بطريقة غير مدعومة")
            }
            return collected.toString()
        } finally {
            runCatching { tempPdf.delete() }
        }
    }

    private fun prepareVisionBitmaps(
        context: Context,
        uri: Uri,
        kind: SecureFileGuard.FileKind
    ): List<Bitmap> {
        return when (kind) {
            SecureFileGuard.FileKind.IMAGE -> listOf(prepareVisionImage(context, uri))
            SecureFileGuard.FileKind.PDF -> renderPdfForVision(context, uri)
        }
    }

    private fun prepareVisionImage(context: Context, uri: Uri): Bitmap {
        val decoded = SecureFileGuard.decodeImageSafely(context, uri)
        val longest = max(decoded.width, decoded.height).coerceAtLeast(1)
        if (longest <= MAX_VISION_EDGE) return decoded

        val scale = MAX_VISION_EDGE.toFloat() / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            max(1, (decoded.width * scale).roundToInt()),
            max(1, (decoded.height * scale).roundToInt()),
            true
        )
        decoded.recycle()
        return scaled
    }

    private fun renderPdfForVision(context: Context, uri: Uri): List<Bitmap> {
        val validated = SecureFileGuard.requireKind(context, uri, SecureFileGuard.FileKind.PDF)
        val tempPdf = File.createTempFile("lab_tests_vision_", ".pdf", context.cacheDir)
        val pages = mutableListOf<Bitmap>()
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

            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "ملف PDF بدون صفحات" }
                    val count = minOf(renderer.pageCount, MAX_VISION_PDF_PAGES)
                    for (pageIndex in 0 until count) {
                        renderer.openPage(pageIndex).use { page ->
                            val longest = max(page.width, page.height).coerceAtLeast(1)
                            val scale = (MAX_VISION_EDGE.toFloat() / longest.toFloat())
                                .coerceAtMost(2.0f)
                                .coerceAtLeast(0.5f)
                            val width = max(1, (page.width * scale).roundToInt())
                            val height = max(1, (page.height * scale).roundToInt())
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pages += bitmap
                        }
                    }
                }
            }
            return pages
        } catch (t: Throwable) {
            pages.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            throw t
        } finally {
            runCatching { tempPdf.delete() }
        }
    }

    private fun recognizeBitmap(recognizer: TextRecognizer, bitmap: Bitmap, rotationDegrees: Int = 0): String {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        return Tasks.await(recognizer.process(image)).text.orEmpty()
    }

    /**
     * V60 handwriting boost: a single full-page OCR pass is often dominated by the
     * printed prescription header while small handwritten lab abbreviations are missed.
     * Keep the original pass, add a high-contrast grayscale pass, then OCR overlapping
     * enlarged horizontal bands. The parser receives the union of all non-empty lines.
     */
    private fun recognizeImageRobust(recognizer: TextRecognizer, bitmap: Bitmap): String {
        val collected = LinkedHashSet<String>()

        fun addText(text: String) {
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { collected.add(it) }
        }

        val upright = recognizeBitmap(recognizer, bitmap, 0)
        addText(upright)

        // If almost nothing was read, orientation metadata may not have been applied.
        if (upright.count { it.isLetterOrDigit() } < 24) {
            var best = upright
            var bestScore = upright.count { it.isLetterOrDigit() }
            for (rotation in intArrayOf(90, 270, 180)) {
                val text = recognizeBitmap(recognizer, bitmap, rotation)
                val score = text.count { it.isLetterOrDigit() }
                if (score > bestScore) {
                    best = text
                    bestScore = score
                }
            }
            addText(best)
        }

        val enhanced = createHighContrastBitmap(bitmap)
        try {
            addText(recognizeBitmap(recognizer, enhanced, 0))
        } finally {
            enhanced.recycle()
        }

        // Handwriting is commonly much smaller than the pre-printed header. OCR overlapping
        // bands independently and enlarge them so abbreviations such as CBC / ESR / SGPT
        // have more pixels available to the recognizer.
        if (bitmap.height >= 600 && bitmap.width >= 400) {
            val bandHeight = (bitmap.height * 0.48f).roundToInt().coerceAtLeast(1)
            val starts = intArrayOf(
                0,
                (bitmap.height * 0.26f).roundToInt(),
                (bitmap.height - bandHeight).coerceAtLeast(0)
            ).distinct()

            for (start in starts) {
                val safeStart = start.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                val safeHeight = minOf(bandHeight, bitmap.height - safeStart)
                if (safeHeight <= 0) continue

                val crop = Bitmap.createBitmap(bitmap, 0, safeStart, bitmap.width, safeHeight)
                try {
                    val targetWidth = max(bitmap.width, 1800)
                    val scale = (targetWidth.toFloat() / crop.width.toFloat()).coerceAtMost(2.0f)
                    val scaled = if (scale > 1.05f) {
                        Bitmap.createScaledBitmap(
                            crop,
                            (crop.width * scale).roundToInt().coerceAtLeast(1),
                            (crop.height * scale).roundToInt().coerceAtLeast(1),
                            true
                        )
                    } else crop

                    try {
                        val bandEnhanced = createHighContrastBitmap(scaled)
                        try {
                            addText(recognizeBitmap(recognizer, bandEnhanced, 0))
                        } finally {
                            bandEnhanced.recycle()
                        }
                    } finally {
                        if (scaled !== crop) scaled.recycle()
                    }
                } finally {
                    crop.recycle()
                }
            }
        }

        return collected.joinToString("\n")
    }

    private fun createHighContrastBitmap(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.65f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayscale.postConcat(contrastMatrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(grayscale)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Phone photos may be stored with orientation metadata instead of physically rotated pixels.
     * ML Kit receives the decoded bitmap, so try the common rotations and keep the strongest OCR result.
     */
    private fun recognizeBitmapBestRotation(recognizer: TextRecognizer, bitmap: Bitmap): String {
        val rotations = intArrayOf(0, 90, 270, 180)
        var best = ""
        var bestScore = -1
        for (rotation in rotations) {
            val text = recognizeBitmap(recognizer, bitmap, rotation)
            val score = text.count { it.isLetterOrDigit() }
            if (score > bestScore) {
                best = text
                bestScore = score
            }
            // A strong first-pass result avoids four OCR passes for normal upright images.
            if (rotation == 0 && score >= 24) break
        }
        return best
    }
}
