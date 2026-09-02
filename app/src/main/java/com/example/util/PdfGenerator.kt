package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Build
import android.os.Environment
import android.text.Layout
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.CustomerOrder
import com.example.data.model.LabTest
import com.example.data.model.ReportSummary
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    data class LabBranding(
        val name: String = "تحاليل العقاد",
        val tagline: String = "دليل التحاليل والأسعار",
        val whatsapp: String = "",
        val phone: String = "",
        val address: String = "",
        val extraContact: String = "",
        val logoPath: String = ""
    )

    private const val PAGE_WIDTH = 420
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 24f

    fun generateAndSharePdf(
        context: Context,
        selectedTests: List<LabTest>,
        includeCustomerPrice: Boolean = true,
        includeLab2Lab: Boolean = false,
        showTotals: Boolean = true,
        labName: String = "تحاليل العقاد",
        showContactInfo: Boolean = false,
        contactInfo: String = "",
        lab2LabPrices: Map<Int, String> = emptyMap(),
        customerPriceOverrides: Map<Int, String> = emptyMap(),
        customerName: String? = null,
        customerFileNumber: String? = null,
        customerPhone: String? = null,
        orderNumber: String? = null,
        orderDateMillis: Long? = null,
        discountAmount: Double = 0.0,
        finalCustomerTotal: Double? = null,
        paymentStatus: String? = null,
        paidAmount: Double = 0.0,
        remainingAmount: Double = 0.0,
        orderNotes: String = "",
        language: String = "ar"
    ) {
        val en = language == "en"
        fun tr(ar: String, english: String): String = if (en) english else ar
        if (selectedTests.isEmpty()) {
            Toast.makeText(context, tr("لم يتم اختيار أي تحاليل", "No tests selected"), Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val primaryColor = Color.parseColor("#0061A4")
        val darkTextColor = Color.parseColor("#001D35")
        val grayTextColor = Color.parseColor("#475569")
        val lightBgColor = Color.parseColor("#F8FAFC")
        val dividerColor = Color.parseColor("#E2E8F0")
        val managerColor = Color.parseColor("#6D28D9")

        val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val labNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkTextColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (includeCustomerPrice && includeLab2Lab) 9f else 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkTextColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val arabicNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = grayTextColor
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val lab2LabPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = managerColor
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val totalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val contactPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = grayTextColor
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dividerColor
            strokeWidth = 0.8f
        }

        var currentY = MARGIN

        fun startCleanPage() {
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)
            currentY = MARGIN
        }

        fun drawPageHeader() {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_clinic_logo)
            if (logoBitmap != null) {
                val logoSize = 62f
                val logoX = (PAGE_WIDTH - logoSize) / 2f
                val srcRect = Rect(0, 0, logoBitmap.width, logoBitmap.height)
                val dstRect = RectF(logoX, currentY, logoX + logoSize, currentY + logoSize)
                canvas.drawBitmap(logoBitmap, srcRect, dstRect, null)
                currentY += logoSize + 6f
            }

            canvas.drawText(labName.ifBlank { tr("تحاليل العقاد", "Tahalil Alakkad") }, PAGE_WIDTH / 2f, currentY + 13f, labNamePaint)
            currentY += 19f
            canvas.drawText(tr("قائمة التحاليل والأسعار", "Lab Tests & Prices"), PAGE_WIDTH / 2f, currentY + 10f, titlePaint)
            currentY += 20f

            if (!customerName.isNullOrBlank()) {
                val customerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E7F5F7")
                    style = Paint.Style.FILL
                }
                val customerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = darkTextColor
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                val infoRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 34f)
                canvas.drawRoundRect(infoRect, 5f, 5f, customerBg)
                canvas.drawText(tr("العميل: $customerName", "Customer: $customerName"), PAGE_WIDTH - MARGIN - 8f, currentY + 13f, customerPaint)
                val secondLine = listOfNotNull(
                    customerFileNumber?.takeIf { it.isNotBlank() },
                    customerPhone?.takeIf { it.isNotBlank() }
                ).joinToString("  •  ")
                if (secondLine.isNotBlank()) {
                    customerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    customerPaint.textSize = 7.8f
                    canvas.drawText(secondLine, PAGE_WIDTH - MARGIN - 8f, currentY + 26f, customerPaint)
                }
                currentY += 42f
            }

            if (!orderNumber.isNullOrBlank()) {
                val orderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#F1F5F9")
                    style = Paint.Style.FILL
                }
                val orderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = darkTextColor
                    textSize = 7.8f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                val orderRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 25f)
                canvas.drawRoundRect(orderRect, 5f, 5f, orderBg)
                canvas.drawText(tr("رقم الطلب: $orderNumber", "Order No.: $orderNumber"), PAGE_WIDTH - MARGIN - 8f, currentY + 11f, orderPaint)
                val orderDate = orderDateMillis?.takeIf { it > 0L }?.let {
                    SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("ar", "EG")).format(Date(it))
                }.orEmpty()
                if (orderDate.isNotBlank()) {
                    orderPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    orderPaint.textSize = 7.2f
                    canvas.drawText(tr("التاريخ: $orderDate", "Date: $orderDate"), PAGE_WIDTH - MARGIN - 8f, currentY + 21f, orderPaint)
                }
                currentY += 33f
            }

            val headerHeight = 24f
            val headerRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + headerHeight)
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(headerRect, 4f, 4f, headerBgPaint)

            when {
                includeCustomerPrice && includeLab2Lab -> {
                    headerTextPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText("Lab 2 Lab", MARGIN + 7f, currentY + 16f, headerTextPaint)
                    canvas.drawText(tr("سعر العميل", "Customer Price"), MARGIN + 82f, currentY + 16f, headerTextPaint)
                    headerTextPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tr("اسم التحليل", "Test Name"), PAGE_WIDTH - MARGIN - 8f, currentY + 16f, headerTextPaint)
                }
                includeCustomerPrice -> {
                    headerTextPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(tr("سعر العميل", "Customer Price"), MARGIN + 10f, currentY + 16f, headerTextPaint)
                    headerTextPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tr("اسم التحليل", "Test Name"), PAGE_WIDTH - MARGIN - 10f, currentY + 16f, headerTextPaint)
                }
                includeLab2Lab -> {
                    headerTextPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText("Lab 2 Lab", MARGIN + 10f, currentY + 16f, headerTextPaint)
                    headerTextPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tr("اسم التحليل", "Test Name"), PAGE_WIDTH - MARGIN - 10f, currentY + 16f, headerTextPaint)
                }
                else -> {
                    headerTextPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tr("اسم التحليل", "Test Name"), PAGE_WIDTH - MARGIN - 10f, currentY + 16f, headerTextPaint)
                }
            }

            currentY += headerHeight + 10f
        }

        fun nextPage(withHeader: Boolean = true) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            startCleanPage()
            if (withHeader) drawPageHeader()
        }

        startCleanPage()
        drawPageHeader()

        val reservedBottom = if (showTotals || (showContactInfo && contactInfo.isNotBlank())) 80f else 36f
        val maxUsableY = PAGE_HEIGHT - reservedBottom
        val priceColumnsWidth = when {
            includeCustomerPrice && includeLab2Lab -> 155
            includeCustomerPrice || includeLab2Lab -> 90
            else -> 0
        }
        val nameColumnWidth = (PAGE_WIDTH - (MARGIN * 2) - priceColumnsWidth - 18).toInt().coerceAtLeast(170)

        selectedTests.forEachIndexed { index, test ->
            val nameLayout = createStaticLayout(test.englishName, namePaint, nameColumnWidth)
            val arabicLayout = test.arabicName.takeIf { it.isNotBlank() }?.let {
                createStaticLayout(it, arabicNamePaint, nameColumnWidth)
            }
            val textHeight = nameLayout.height + (arabicLayout?.height ?: 0)
            val rowHeight = textHeight.coerceAtLeast(20) + 12f

            if (currentY + rowHeight > maxUsableY) nextPage(withHeader = true)

            if (index % 2 == 1) {
                val rowBgPaint = Paint().apply { color = lightBgColor; style = Paint.Style.FILL }
                canvas.drawRoundRect(
                    RectF(MARGIN, currentY - 2f, PAGE_WIDTH - MARGIN, currentY + rowHeight - 2f),
                    4f,
                    4f,
                    rowBgPaint
                )
            }

            val customerPrice = customerPriceOverrides[test.id] ?: test.customerPrice ?: "0"
            val labPrice = lab2LabPrices[test.id]

            when {
                includeCustomerPrice && includeLab2Lab -> {
                    canvas.drawText(if (labPrice.isNullOrBlank()) "--" else "$labPrice ${tr("جنيه", "EGP")}", MARGIN + 7f, currentY + 14f, lab2LabPaint)
                    canvas.drawText("$customerPrice ${tr("جنيه", "EGP")}", MARGIN + 82f, currentY + 14f, pricePaint)
                }
                includeCustomerPrice -> {
                    canvas.drawText("$customerPrice ${tr("جنيه", "EGP")}", MARGIN + 10f, currentY + 14f, pricePaint)
                }
                includeLab2Lab -> {
                    canvas.drawText(if (labPrice.isNullOrBlank()) "--" else "$labPrice ${tr("جنيه", "EGP")}", MARGIN + 10f, currentY + 14f, lab2LabPaint)
                }
            }

            canvas.save()
            canvas.translate(PAGE_WIDTH - MARGIN - 10f - nameLayout.width, currentY)
            nameLayout.draw(canvas)
            if (arabicLayout != null) {
                canvas.translate(0f, nameLayout.height.toFloat())
                arabicLayout.draw(canvas)
            }
            canvas.restore()

            currentY += rowHeight
            canvas.drawLine(MARGIN, currentY - 2f, PAGE_WIDTH - MARGIN, currentY - 2f, linePaint)
        }

        val totalCustomer = selectedTests.sumOf { extractNumericPrice(customerPriceOverrides[it.id] ?: it.customerPrice) }
        val totalLab = selectedTests.sumOf { extractNumericPrice(lab2LabPrices[it.id]) }

        if (showTotals && (includeCustomerPrice || includeLab2Lab)) {
            val totalRows = (if (includeCustomerPrice) 1 else 0) + (if (includeLab2Lab) 1 else 0)
            val totalBoxHeight = if (totalRows == 2) 50f else 32f
            if (currentY + totalBoxHeight + 12f > PAGE_HEIGHT - 30f) nextPage(withHeader = false)
            else currentY += 8f

            val totalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor; style = Paint.Style.FILL }
            canvas.drawRoundRect(
                RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + totalBoxHeight),
                6f,
                6f,
                totalBgPaint
            )

            var rowY = currentY + 20f
            if (includeCustomerPrice) {
                totalPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(tr("إجمالي سعر العميل:", "Customer total:"), PAGE_WIDTH - MARGIN - 16f, rowY, totalPaint)
                totalPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("${formatTotal(finalCustomerTotal ?: totalCustomer)} ${tr("جنيه", "EGP")}", MARGIN + 16f, rowY, totalPaint)
                rowY += 21f
            }
            if (includeLab2Lab) {
                totalPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(tr("إجمالي Lab 2 Lab:", "Lab 2 Lab total:"), PAGE_WIDTH - MARGIN - 16f, rowY, totalPaint)
                totalPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("${formatTotal(totalLab)} ${tr("جنيه", "EGP")}", MARGIN + 16f, rowY, totalPaint)
            }
            currentY += totalBoxHeight + 10f
        }

        if (!orderNumber.isNullOrBlank()) {
            val statusAr = when (paymentStatus) {
                "paid" -> tr("مدفوع بالكامل", "Paid")
                "partial" -> tr("مدفوع جزئيا", "Partially paid")
                else -> tr("غير مدفوع", "Unpaid")
            }
            val boxHeight = if (orderNotes.isBlank()) 47f else 70f
            if (currentY + boxHeight + 8f > PAGE_HEIGHT - MARGIN) nextPage(withHeader = false)
            val orderSummaryBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            val summaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkTextColor
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawRoundRect(RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + boxHeight), 6f, 6f, orderSummaryBg)
            canvas.drawText(tr("حالة الدفع: $statusAr", "Payment status: $statusAr"), PAGE_WIDTH - MARGIN - 10f, currentY + 13f, summaryPaint)
            summaryPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(tr("الخصم: ${formatTotal(discountAmount)} جنيه", "Discount: ${formatTotal(discountAmount)} EGP"), PAGE_WIDTH - MARGIN - 10f, currentY + 27f, summaryPaint)
            canvas.drawText(tr("المدفوع: ${formatTotal(paidAmount)} جنيه    •    المتبقي: ${formatTotal(remainingAmount)} جنيه", "Paid: ${formatTotal(paidAmount)} EGP    •    Outstanding: ${formatTotal(remainingAmount)} EGP"), PAGE_WIDTH - MARGIN - 10f, currentY + 41f, summaryPaint)
            if (orderNotes.isNotBlank()) {
                val notesPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = darkTextColor
                    textSize = 7.6f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val notesLayout = createStaticLayout(tr("ملاحظات: $orderNotes", "Notes: $orderNotes"), notesPaint, (PAGE_WIDTH - MARGIN * 2 - 20).toInt())
                canvas.save()
                canvas.translate(MARGIN + 10f, currentY + 48f)
                notesLayout.draw(canvas)
                canvas.restore()
            }
            currentY += boxHeight + 8f
        }

        if (showContactInfo && contactInfo.isNotBlank()) {
            val label = tr("بيانات التواصل: $contactInfo", "Contact: $contactInfo")
            val contactLayout = createStaticLayout(label, contactPaint, (PAGE_WIDTH - MARGIN * 2).toInt())
            if (currentY + contactLayout.height + 12f > PAGE_HEIGHT - MARGIN) nextPage(withHeader = false)
            canvas.save()
            canvas.translate(MARGIN, currentY + 2f)
            contactLayout.draw(canvas)
            canvas.restore()
            currentY += contactLayout.height + 8f
        }

        pdfDocument.finishPage(page)

        val pdfDir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(pdfDir, if (en) "Tahalil_Alakkad.pdf" else "تحاليل_العقاد.pdf")
        try {
            FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, tr("قائمة التحاليل والأسعار - ${labName.ifBlank { "تحاليل العقاد" }}", "Lab Tests & Prices - ${labName.ifBlank { "Tahalil Alakkad" }}"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, tr("مشاركة قائمة التحاليل PDF", "Share lab tests PDF"))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            runCatching { pdfDocument.close() }
            Toast.makeText(context, tr("حدث خطأ أثناء إنشاء ملف PDF", "Error creating PDF"), Toast.LENGTH_SHORT).show()
        }
    }

    fun generateAndShareReportPdf(
        context: Context,
        orders: List<CustomerOrder>,
        summary: ReportSummary,
        fromMillis: Long,
        toMillis: Long,
        labName: String = "تحاليل العقاد",
        showContactInfo: Boolean = false,
        contactInfo: String = "",
        debtsOnly: Boolean = false,
        query: String = "",
        language: String = "ar"
    ) {
        val en = language == "en"
        fun tr(ar: String, english: String): String = if (en) english else ar
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas

        val primary = Color.parseColor("#006D86")
        val dark = Color.parseColor("#17324D")
        val gray = Color.parseColor("#64748B")
        val light = Color.parseColor("#F4F7FB")
        val pale = Color.parseColor("#E7F5F7")
        val divider = Color.parseColor("#E2E8F0")
        val green = Color.parseColor("#15803D")
        val red = Color.parseColor("#B91C1C")
        val purple = Color.parseColor("#6D28D9")

        val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val rtlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = if (en) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val rtlBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 8.7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = if (en) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val metricLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = if (en) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val metricValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 10.3f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = if (en) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = divider; strokeWidth = 0.8f }

        var y = MARGIN

        fun paintPageBackground() {
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)
            y = MARGIN
        }

        fun periodText(): String {
            val df = SimpleDateFormat("dd/MM/yyyy", if (en) Locale.US else Locale("ar", "EG"))
            return if (fromMillis <= 0L) {
                tr("الفترة: كل الفترات حتى ${df.format(Date(toMillis))}", "Period: all records through ${df.format(Date(toMillis))}")
            } else {
                tr("الفترة: من ${df.format(Date(fromMillis))} إلى ${df.format(Date(toMillis))}", "Period: ${df.format(Date(fromMillis))} to ${df.format(Date(toMillis))}")
            }
        }

        fun drawMainHeader(compact: Boolean = false) {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_clinic_logo)
            if (!compact && logoBitmap != null) {
                val logoSize = 48f
                val logoX = (PAGE_WIDTH - logoSize) / 2f
                canvas.drawBitmap(
                    logoBitmap,
                    Rect(0, 0, logoBitmap.width, logoBitmap.height),
                    RectF(logoX, y, logoX + logoSize, y + logoSize),
                    null
                )
                y += logoSize + 4f
            }
            canvas.drawText(labName.ifBlank { tr("تحاليل العقاد", "Tahalil Alakkad") }, PAGE_WIDTH / 2f, y + 12f, titlePaint)
            y += 17f
            canvas.drawText(tr("تقرير الحسابات والمبيعات", "Accounts & Sales Report"), PAGE_WIDTH / 2f, y + 10f, subtitlePaint)
            y += 17f
            canvas.drawText(periodText(), PAGE_WIDTH / 2f, y + 9f, subtitlePaint)
            y += 18f

            if (!compact && (debtsOnly || query.isNotBlank())) {
                val filters = buildList {
                    if (debtsOnly) add(tr("المتبقي فقط", "Outstanding only"))
                    if (query.isNotBlank()) add(tr("بحث: ${query.trim()}", "Search: ${query.trim()}"))
                }.joinToString(" • ")
                val filterLayout = createStaticLayout(tr("الفلاتر: $filters", "Filters: $filters"), rtlPaint, (PAGE_WIDTH - MARGIN * 2 - 16).toInt())
                val h = filterLayout.height + 12f
                canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + h), 5f, 5f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pale; style = Paint.Style.FILL })
                canvas.save()
                canvas.translate(MARGIN + 8f, y + 6f)
                filterLayout.draw(canvas)
                canvas.restore()
                y += h + 8f
            }
        }

        fun nextPage() {
            pdfDocument.finishPage(page)
            pageNumber += 1
            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            paintPageBackground()
            drawMainHeader(compact = true)
        }

        fun metricBox(x1: Float, x2: Float, top: Float, label: String, value: String, valueColor: Int = dark) {
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light; style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(x1, top, x2, top + 37f), 6f, 6f, boxPaint)
            metricLabelPaint.color = gray
            metricValuePaint.color = valueColor
            val textX = if (en) x1 + 8f else x2 - 8f
            canvas.drawText(label, textX, top + 13f, metricLabelPaint)
            canvas.drawText(value, textX, top + 29f, metricValuePaint)
        }

        paintPageBackground()
        drawMainHeader()

        val gap = 7f
        val boxWidth = (PAGE_WIDTH - (MARGIN * 2) - gap) / 2f
        val leftX1 = MARGIN
        val leftX2 = MARGIN + boxWidth
        val rightX1 = leftX2 + gap
        val rightX2 = PAGE_WIDTH - MARGIN

        metricBox(rightX1, rightX2, y, tr("عدد الطلبات", "Orders"), summary.ordersCount.toString(), primary)
        metricBox(leftX1, leftX2, y, tr("عدد العملاء", "Customers"), summary.customersCount.toString(), purple)
        y += 44f
        metricBox(rightX1, rightX2, y, tr("المبيعات", "Sales"), "${formatTotal(summary.sales)} ${tr("جنيه", "EGP")}", green)
        metricBox(leftX1, leftX2, y, tr("الخصومات", "Discounts"), "${formatTotal(summary.discounts)} ${tr("جنيه", "EGP")}")
        y += 44f
        metricBox(rightX1, rightX2, y, tr("المدفوع", "Paid"), "${formatTotal(summary.paid)} ${tr("جنيه", "EGP")}", green)
        metricBox(leftX1, leftX2, y, tr("المتبقي", "Outstanding"), "${formatTotal(summary.remaining)} ${tr("جنيه", "EGP")}", red)
        y += 44f
        metricBox(rightX1, rightX2, y, tr("تكلفة Lab2Lab التقديرية", "Estimated Lab2Lab cost"), "${formatTotal(summary.estimatedLabCost)} ${tr("جنيه", "EGP")}")
        metricBox(leftX1, leftX2, y, tr("الربح التقديري", "Estimated profit"), "${formatTotal(summary.estimatedProfit)} ${tr("جنيه", "EGP")}", primary)
        y += 48f

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary; style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f), 5f, 5f, sectionPaint)
        val sectionText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = if (en) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        canvas.drawText(tr("تفاصيل الطلبات", "Order details"), if (en) MARGIN + 9f else PAGE_WIDTH - MARGIN - 9f, y + 16f, sectionText)
        y += 31f

        if (orders.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gray
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawRoundRect(
                RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 42f),
                6f,
                6f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light; style = Paint.Style.FILL }
            )
            canvas.drawText(tr("لا توجد طلبات في الفترة المحددة", "No orders in the selected period"), PAGE_WIDTH / 2f, y + 25f, emptyPaint)
            y += 50f
        }

        orders.sortedByDescending { it.createdAtMillis }.forEachIndexed { index, order ->
            val rowHeight = 45f
            if (y + rowHeight > PAGE_HEIGHT - 42f) nextPage()

            if (index % 2 == 0) {
                canvas.drawRoundRect(
                    RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight - 3f),
                    5f, 5f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light; style = Paint.Style.FILL }
                )
            }

            val date = if (order.createdAtMillis > 0L) {
                SimpleDateFormat("dd/MM/yyyy HH:mm", if (en) Locale.US else Locale("ar", "EG")).format(Date(order.createdAtMillis))
            } else "—"
            val status = when (order.paymentStatus) {
                "paid" -> tr("مدفوع", "Paid")
                "partial" -> tr("مدفوع جزئي", "Partially paid")
                else -> tr("غير مدفوع", "Unpaid")
            }
            val customerLine = tr(
                "العميل: ${order.customerName}    •    الطلب: ${order.orderNumber}",
                "Customer: ${order.customerName}    •    Order: ${order.orderNumber}"
            )
            val moneyLine = tr("التاريخ: $date    •    الحالة: $status", "Date: $date    •    Status: $status")
            val totalsLine = tr(
                "الإجمالي: ${formatTotal(order.totalCustomerPrice)}    •    المدفوع: ${formatTotal(order.paidAmount)}    •    المتبقي: ${formatTotal(order.remainingAmount)} جنيه",
                "Total: ${formatTotal(order.totalCustomerPrice)}    •    Paid: ${formatTotal(order.paidAmount)}    •    Outstanding: ${formatTotal(order.remainingAmount)} EGP"
            )
            val rowX = if (en) MARGIN + 8f else PAGE_WIDTH - MARGIN - 8f
            canvas.drawText(customerLine, rowX, y + 12f, rtlBoldPaint)
            canvas.drawText(moneyLine, rowX, y + 25f, rtlPaint)
            rtlPaint.color = if (order.remainingAmount > 0.0) red else green
            canvas.drawText(totalsLine, rowX, y + 38f, rtlPaint)
            rtlPaint.color = dark
            y += rowHeight
            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, linePaint)
        }

        if (showContactInfo && contactInfo.isNotBlank()) {
            val contactLayout = createStaticLayout(tr("بيانات التواصل: $contactInfo", "Contact: $contactInfo"), rtlPaint, (PAGE_WIDTH - MARGIN * 2).toInt())
            if (y + contactLayout.height + 18f > PAGE_HEIGHT - MARGIN) nextPage()
            y += 7f
            canvas.save()
            canvas.translate(MARGIN, y)
            contactLayout.draw(canvas)
            canvas.restore()
            y += contactLayout.height + 6f
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            textSize = 7f
            textAlign = Paint.Align.CENTER
        }
        val createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", if (en) Locale.US else Locale("ar", "EG")).format(Date())
        if (y + 18f > PAGE_HEIGHT - MARGIN) nextPage()
        canvas.drawText(tr("تم إنشاء التقرير: $createdAt", "Report created: $createdAt"), PAGE_WIDTH / 2f, PAGE_HEIGHT - 18f, footerPaint)

        pdfDocument.finishPage(page)

        val pdfDir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val fileDate = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(pdfDir, if (en) "Tahalil_Alakkad_Report_$fileDate.pdf" else "تقرير_تحاليل_العقاد_$fileDate.pdf")
        try {
            FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, tr("تقرير الحسابات والمبيعات - ${labName.ifBlank { "تحاليل العقاد" }}", "Accounts & Sales Report - ${labName.ifBlank { "Tahalil Alakkad" }}"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, tr("مشاركة التقرير PDF", "Share report PDF"))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            runCatching { pdfDocument.close() }
            Toast.makeText(context, tr("حدث خطأ أثناء إنشاء تقرير PDF", "Error creating report PDF"), Toast.LENGTH_SHORT).show()
        }
    }

    fun generateAndShareSavedOrderPdf(
        context: Context,
        order: CustomerOrder,
        customerPhone: String? = null,
        labName: String = "تحاليل العقاد",
        showContactInfo: Boolean = false,
        contactInfo: String = "",
        language: String = "ar"
    ) {
        val tests = order.items.map { item ->
            LabTest(
                id = item.testId,
                englishName = item.englishName,
                arabicName = item.arabicName,
                marketName = item.marketName,
                customerPrice = item.customerPrice.toString(),
                searchText = ""
            )
        }
        val priceMap = order.items.associate { it.testId to it.customerPrice.toString() }
        generateAndSharePdf(
            context = context,
            selectedTests = tests,
            includeCustomerPrice = true,
            includeLab2Lab = false,
            showTotals = true,
            labName = labName,
            showContactInfo = showContactInfo,
            contactInfo = contactInfo,
            customerPriceOverrides = priceMap,
            customerName = order.customerName,
            customerFileNumber = order.customerFileNumber,
            customerPhone = customerPhone,
            orderNumber = order.orderNumber,
            orderDateMillis = order.createdAtMillis,
            discountAmount = order.discountAmount,
            finalCustomerTotal = order.totalCustomerPrice,
            paymentStatus = order.paymentStatus,
            paidAmount = order.paidAmount,
            remainingAmount = order.remainingAmount,
            orderNotes = order.notes,
            language = language
        )
    }



    data class OrderPdfBundle(
        val customerPdf: File,
        val labRequestPdf: File
    )

    private const val CLINIC_NAME = "عيادات العقاد التخصصية"
    private const val CLINIC_TAGLINE = "رعاية تليق بك"
    private const val CLINIC_WHATSAPP = "01102233167"
    private const val CLINIC_PHONE = "01107072134"
    private const val CLINIC_ADDRESS = "59 شارع فيصل الرئيسي - ناصية شارع الوفاء والامل - امام اسماك عروس البحر وعنتر الكبابجي - فيصل - الجيزة"

    /**
     * Legacy dual generator kept for compatibility. V38 no longer calls this automatically
     * when saving an order: document generation/sharing is optional and lazy.
     */
    fun generateDualOrderPdfs(
        context: Context,
        order: CustomerOrder,
        customer: Customer
    ): OrderPdfBundle? {
        return try {
            val customerPdf = createCustomerOrderPdf(context, order, customer)
            val labRequestPdf = createLabRequestPdf(context, order, customer)
            OrderPdfBundle(customerPdf = customerPdf, labRequestPdf = labRequestPdf)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "تعذر إنشاء ملفي PDF: ${e.message ?: "خطأ غير معروف"}",
                Toast.LENGTH_LONG
            ).show()
            null
        }
    }

    fun generateCustomerOrderPdf(
        context: Context,
        order: CustomerOrder,
        customer: Customer
    ): File? = try {
        createCustomerOrderPdf(context, order, customer)
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر إنشاء إيصال العميل: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
        null
    }

    fun generateLabRequestPdf(
        context: Context,
        order: CustomerOrder,
        customer: Customer
    ): File? = try {
        createLabRequestPdf(context, order, customer)
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر إنشاء طلب المعمل: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
        null
    }

    fun generateCustomerOrderImage(
        context: Context,
        order: CustomerOrder,
        customer: Customer,
        branding: LabBranding = LabBranding()
    ): File? = try {
        val pdf = createCustomerOrderPdf(context, order, customer, branding)
        renderPdfToLongPng(context, pdf, "customer_receipt_${safeFilePart(order.orderNumber)}.png")
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر إنشاء صورة إيصال العميل: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
        null
    }

    fun generateLabRequestImage(
        context: Context,
        order: CustomerOrder,
        customer: Customer,
        branding: LabBranding = LabBranding()
    ): File? = try {
        val pdf = createLabRequestPdf(context, order, customer, branding)
        renderPdfToLongPng(context, pdf, "lab_request_${safeFilePart(order.orderNumber)}.png")
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر إنشاء صورة طلب المعمل: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
        null
    }


    /**
     * V75 quick quote image. Creates a clean customer-price image directly from selected tests.
     * Customer details are optional and nothing is written to customer records.
     */
    fun generateQuickTestsImage(
        context: Context,
        selectedTests: List<LabTest>,
        customerPriceOverrides: Map<Int, String> = emptyMap(),
        customerName: String = "",
        customerPhone: String = "",
        branding: LabBranding = LabBranding()
    ): File? {
        if (selectedTests.isEmpty()) {
            Toast.makeText(context, "اختار تحليل واحد على الأقل", Toast.LENGTH_SHORT).show()
            return null
        }
        return try {

        val width = 1080
        val side = 64f
        val headerHeight = if (customerName.isBlank() && customerPhone.isBlank()) 300f else 390f
        val rowHeight = 118f
        val footerHeight = 190f
        val height = (headerHeight + selectedTests.size * rowHeight + footerHeight).toInt().coerceAtLeast(720)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val white = Color.WHITE
        val navy = Color.parseColor("#0A2535")
        val cyan = Color.parseColor("#00AFC0")
        val softCyan = Color.parseColor("#E8FBFD")
        val slate = Color.parseColor("#5B6B78")
        val divider = Color.parseColor("#DCE8EC")

        canvas.drawColor(white)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(32f, 28f, width - 32f, headerHeight - 20f), 42f, 42f, headerPaint)

        val logo = loadBrandLogo(context, branding.logoPath)
        if (logo != null) {
            val logoSize = 126f
            val x = (width - logoSize) / 2f
            canvas.drawBitmap(
                logo,
                Rect(0, 0, logo.width, logo.height),
                RectF(x, 54f, x + logoSize, 54f + logoSize),
                null
            )
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 43f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(branding.name.ifBlank { "تحاليل العقاد" }, width / 2f, 212f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8DF5FF")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(branding.tagline.ifBlank { "قائمة التحاليل والأسعار" }, width / 2f, 254f, subtitlePaint)

        if (customerName.isNotBlank() || customerPhone.isNotBlank()) {
            val customerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#163B4B") }
            canvas.drawRoundRect(RectF(72f, 278f, width - 72f, 355f), 24f, 24f, customerBg)
            val customerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val first = if (customerName.isNotBlank()) "العميل: $customerName" else "بيانات العميل"
            canvas.drawText(first, width - 96f, 309f, customerPaint)
            if (customerPhone.isNotBlank()) {
                customerPaint.color = Color.parseColor("#AEEFF4")
                customerPaint.textSize = 22f
                customerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("واتساب: $customerPhone", width - 96f, 340f, customerPaint)
            }
        }

        var y = headerHeight
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val arabicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slate
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyan
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = divider; strokeWidth = 2f }

        selectedTests.forEachIndexed { index, test ->
            if (index % 2 == 0) {
                val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FCFD") }
                canvas.drawRoundRect(RectF(46f, y + 7f, width - 46f, y + rowHeight - 7f), 24f, 24f, rowBg)
            }

            canvas.drawCircle(width - 84f, y + 43f, 27f, badgePaint)
            canvas.drawText((index + 1).toString(), width - 84f, y + 51f, indexPaint)

            val textRight = width - 128f
            canvas.drawText(test.englishName.take(45), textRight, y + 39f, namePaint)
            if (test.arabicName.isNotBlank()) {
                canvas.drawText(test.arabicName.take(48), textRight, y + 75f, arabicPaint)
            }

            val price = customerPriceOverrides[test.id] ?: test.customerPrice ?: "0"
            canvas.drawText("$price جنيه", 72f, y + 61f, pricePaint)
            canvas.drawLine(64f, y + rowHeight, width - 64f, y + rowHeight, dividerPaint)
            y += rowHeight
        }

        val total = selectedTests.sumOf { extractNumericPrice(customerPriceOverrides[it.id] ?: it.customerPrice) }
        val totalBox = RectF(62f, y + 30f, width - 62f, y + 125f)
        val totalBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan }
        canvas.drawRoundRect(totalBox, 30f, 30f, totalBg)
        val totalLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val totalValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("الإجمالي", width - 92f, y + 86f, totalLabel)
        canvas.drawText("${formatTotal(total)} جنيه", 92f, y + 88f, totalValue)

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slate
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val quickFooter = listOf(branding.whatsapp, branding.phone, branding.address, branding.extraContact)
            .map(String::trim).filter(String::isNotBlank).joinToString(" • ").take(110)
        canvas.drawText(
            if (quickFooter.isBlank()) "الأسعار الظاهرة هي سعر العميل الحالي داخل التطبيق" else quickFooter,
            width / 2f, y + 162f, notePaint
        )

        val imageDir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(imageDir, "quick_tests_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        bitmap.recycle()
        file
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر إنشاء الصورة السريعة: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
            null
        }
    }

    /** V74: save a generated PNG directly to the phone Gallery/Pictures. */
    fun saveGeneratedImageToGallery(
        context: Context,
        file: File,
        displayName: String
    ): Uri? {
        if (!file.isFile) {
            Toast.makeText(context, "الصورة غير موجودة", Toast.LENGTH_SHORT).show()
            return null
        }
        val safeName = displayName.trim()
            .replace(Regex("[^A-Za-z0-9._\\-\\u0600-\\u06FF]+"), "_")
            .let { if (it.endsWith(".png", true)) it else "$it.png" }
            .take(120)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tahalil Alakkad")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("تعذر إنشاء ملف الصورة")
                try {
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        FileInputStream(file).use { input -> input.copyTo(output) }
                    } ?: error("تعذر فتح مكان الحفظ")
                    val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                    Toast.makeText(context, "تم حفظ الصورة في Pictures / Tahalil Alakkad", Toast.LENGTH_LONG).show()
                    uri
                } catch (e: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dir = File(pictures, "Tahalil Alakkad").apply { mkdirs() }
                var target = File(dir, safeName)
                if (target.exists()) {
                    val base = safeName.removeSuffix(".png")
                    target = File(dir, "${base}_${System.currentTimeMillis()}.png")
                }
                file.copyTo(target, overwrite = false)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/png"), null)
                Toast.makeText(context, "تم حفظ الصورة في Pictures / Tahalil Alakkad", Toast.LENGTH_LONG).show()
                Uri.fromFile(target)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر حفظ الصورة: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
            null
        }
    }

    fun shareGeneratedImage(
        context: Context,
        file: File,
        subject: String,
        chooserTitle: String
    ) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر مشاركة الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareGeneratedPdf(
        context: Context,
        file: File,
        subject: String,
        chooserTitle: String
    ) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر مشاركة ملف PDF", Toast.LENGTH_SHORT).show()
        }
    }

    /** V64: direct Android print flow for saved customer/lab PDFs. */
    fun printGeneratedPdf(
        context: Context,
        file: File,
        jobName: String
    ) {
        try {
            if (!file.isFile) {
                Toast.makeText(context, "ملف الطباعة غير موجود", Toast.LENGTH_SHORT).show()
                return
            }
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "خدمة الطباعة غير متاحة على الجهاز", Toast.LENGTH_SHORT).show()
                return
            }
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback.onLayoutFinished(info, oldAttributes != newAttributes)
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    try {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        FileInputStream(file).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message ?: "تعذر تجهيز الطباعة")
                    }
                }
            }
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .build()
            printManager.print(jobName, adapter, attributes)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر بدء الطباعة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCustomerOrderPdf(
        context: Context,
        order: CustomerOrder,
        customer: Customer,
        branding: LabBranding = LabBranding()
    ): File {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas

        val teal = Color.parseColor("#006D86")
        val dark = Color.parseColor("#17324D")
        val gray = Color.parseColor("#64748B")
        val pale = Color.parseColor("#E7F5F7")
        val border = Color.parseColor("#DCE7EC")
        val rowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; strokeWidth = 0.8f }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val sectionBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal; style = Paint.Style.FILL }

        fun drawTop(firstPage: Boolean): Float {
            canvas.drawColor(Color.WHITE)
            var y = 17f
            y = drawClinicHeader(context, canvas, y, teal, dark, branding)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = teal
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("إيصال دفع وتحاليل", PAGE_WIDTH / 2f, y + 14f, titlePaint)
            y += 28f
            if (firstPage) {
                y = drawCustomerReceiptData(canvas, y, customer, order.createdAtMillis, dark, gray, pale, border)
                y += 8f
            }
            canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f), 5f, 5f, sectionBg)
            canvas.drawText("التحاليل المطلوبة", PAGE_WIDTH - MARGIN - 10f, y + 16f, sectionPaint)
            return y + 31f
        }

        var y = drawTop(true)
        val maxY = PAGE_HEIGHT - 102f

        order.items.forEachIndexed { index, item ->
            if (y + 24f > maxY) {
                drawClinicFooter(context, canvas, dark, gray, branding)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = drawTop(false)
            }
            val testName = item.englishName.ifBlank { item.marketName }.ifBlank { item.arabicName }
            canvas.drawText("${index + 1}. $testName", MARGIN + 6f, y + 14f, namePaint)
            canvas.drawText("${formatTotal(item.customerPrice)} جنيه", PAGE_WIDTH - MARGIN - 6f, y + 14f, pricePaint)
            y += 22f
            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, rowLine)
        }

        val summaryHeight = 59f
        if (y + summaryHeight + 10f > maxY) {
            drawClinicFooter(context, canvas, dark, gray, branding)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = drawTop(false)
        } else {
            y += 8f
        }

        val summaryBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
        val summaryBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val summaryLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val summaryValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            textSize = 8.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val summaryRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + summaryHeight)
        canvas.drawRoundRect(summaryRect, 6f, 6f, summaryBg)
        canvas.drawRoundRect(summaryRect, 6f, 6f, summaryBorder)
        canvas.drawText("الإجمالي", PAGE_WIDTH - MARGIN - 10f, y + 16f, summaryLabel)
        canvas.drawText("${formatTotal(order.totalCustomerPrice)} جنيه", MARGIN + 10f, y + 16f, summaryValue)
        canvas.drawText("الخصم", PAGE_WIDTH - MARGIN - 10f, y + 32f, summaryLabel)
        canvas.drawText("${formatTotal(order.discountAmount)} جنيه", MARGIN + 10f, y + 32f, summaryValue)
        canvas.drawText("المدفوع / المتبقي", PAGE_WIDTH - MARGIN - 10f, y + 48f, summaryLabel)
        canvas.drawText("${formatTotal(order.paidAmount)} / ${formatTotal(order.remainingAmount)} جنيه", MARGIN + 10f, y + 48f, summaryValue)

        drawClinicFooter(context, canvas, dark, gray, branding)
        pdfDocument.finishPage(page)

        val pdfDir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val safeOrder = order.orderNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(pdfDir, "Customer_Receipt_${safeOrder}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }

    private fun createLabRequestPdf(
        context: Context,
        order: CustomerOrder,
        customer: Customer,
        branding: LabBranding = LabBranding()
    ): File {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas

        val teal = Color.parseColor("#006D86")
        val dark = Color.parseColor("#17324D")
        val gray = Color.parseColor("#64748B")
        val pale = Color.parseColor("#E7F5F7")
        val border = Color.parseColor("#DCE7EC")
        val rowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; strokeWidth = 0.8f }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            textSize = 9.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val sectionBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal; style = Paint.Style.FILL }

        fun drawTop(firstPage: Boolean): Float {
            canvas.drawColor(Color.WHITE)
            var y = 17f
            y = drawClinicHeader(context, canvas, y, teal, dark, branding)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = teal
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("طلب تحاليل للمعمل", PAGE_WIDTH / 2f, y + 14f, titlePaint)
            y += 29f
            if (firstPage) {
                y = drawLabPatientData(canvas, y, customer, order.createdAtMillis, dark, pale, border)
                y += 9f
            }
            canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f), 5f, 5f, sectionBg)
            canvas.drawText("التحاليل المطلوبة", PAGE_WIDTH - MARGIN - 10f, y + 16f, sectionPaint)
            return y + 31f
        }

        var y = drawTop(true)
        val maxY = PAGE_HEIGHT - 102f
        order.items.forEachIndexed { index, item ->
            if (y + 24f > maxY) {
                drawClinicFooter(context, canvas, dark, gray, branding)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = drawTop(false)
            }
            val englishOnly = item.englishName.trim().ifBlank { item.marketName.trim() }.ifBlank { "Test ${item.testId}" }
            canvas.drawText("${index + 1}", MARGIN + 22f, y + 15f, numberPaint)
            canvas.drawText(englishOnly, MARGIN + 38f, y + 15f, namePaint)
            y += 23f
            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, rowLine)
        }

        drawClinicFooter(context, canvas, dark, gray, branding)
        pdfDocument.finishPage(page)

        val pdfDir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val safeOrder = order.orderNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(pdfDir, "Lab_Request_${safeOrder}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }

    /**
     * V37: full-width clinic identity header for both customer and lab PDFs.
     * The previous centered logo block left too much empty space and looked detached
     * from the A5 page. This card uses nearly the full printable width.
     */
    private fun drawClinicHeader(
        context: Context,
        canvas: Canvas,
        startY: Float,
        teal: Int,
        dark: Int,
        branding: LabBranding = LabBranding()
    ): Float {
        val left = 14f
        val right = PAGE_WIDTH - 14f
        val top = startY
        val bottom = top + 72f

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F2FBFC")
            style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CFE8EC")
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            style = Paint.Style.FILL
        }

        val card = RectF(left, top, right, bottom)
        canvas.drawRoundRect(card, 10f, 10f, bg)
        canvas.drawRoundRect(card, 10f, 10f, border)
        canvas.drawRoundRect(RectF(left, top, right, top + 4f), 10f, 10f, accent)

        val logo = loadBrandLogo(context, branding.logoPath)
        val logoSize = 48f
        val logoRight = right - 10f
        val logoLeft = logoRight - logoSize
        if (logo != null) {
            val dst = RectF(logoLeft, top + 12f, logoRight, top + 12f + logoSize)
            canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), dst, null)
        }

        val clinicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val identityPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }

        val textRight = logoLeft - 12f
        canvas.drawText(branding.name.ifBlank { CLINIC_NAME }, textRight, top + 29f, clinicPaint)
        canvas.drawText(branding.tagline.ifBlank { CLINIC_TAGLINE }, textRight, top + 45f, subPaint)
        canvas.drawText(branding.address.ifBlank { "فيصل - الجيزة" }.take(70), textRight, top + 59f, identityPaint)

        return bottom + 9f
    }

    private fun drawCustomerReceiptData(
        canvas: Canvas,
        startY: Float,
        customer: Customer,
        dateMillis: Long,
        dark: Int,
        gray: Int,
        pale: Int,
        border: Int
    ): Float {
        val height = 148f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pale; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 10.3f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + height)
        canvas.drawRoundRect(rect, 7f, 7f, bg)
        canvas.drawRoundRect(rect, 7f, 7f, stroke)

        val right = PAGE_WIDTH - MARGIN - 10f
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(dateMillis))

        canvas.drawText("بيانات العميل", right, startY + 17f, headingPaint)
        canvas.drawText("الاسم: ${customer.name}", right, startY + 35f, valuePaint)
        canvas.drawText("السن: ${customer.age.ifBlank { "-" }}", right, startY + 53f, valuePaint)
        canvas.drawText("النوع: ${normalizedGender(customer.gender)}", right, startY + 71f, valuePaint)
        canvas.drawText("التاريخ: $date", right, startY + 89f, valuePaint)

        canvas.drawText("التواصل", right, startY + 109f, headingPaint)
        canvas.drawText("واتساب: ${customer.phone.ifBlank { "-" }}", right, startY + 127f, valuePaint)

        val qr = createCustomerQrBitmap(customer, 260)
        val qrSize = 70f
        val qrX = MARGIN + 12f
        val qrY = startY + 30f
        canvas.drawBitmap(qr, Rect(0, 0, qr.width, qr.height), RectF(qrX, qrY, qrX + qrSize, qrY + qrSize), null)
        canvas.drawText("QR العميل", qrX + qrSize / 2f, qrY + qrSize + 10f, smallPaint)
        canvas.drawText(customer.fileNumber.ifBlank { customer.id.take(10) }, qrX + qrSize / 2f, qrY + qrSize + 21f, smallPaint)

        return startY + height
    }

    private fun drawLabPatientData(
        canvas: Canvas,
        startY: Float,
        customer: Customer,
        dateMillis: Long,
        dark: Int,
        pale: Int,
        border: Int
    ): Float {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pale; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + 76f)
        canvas.drawRoundRect(rect, 7f, 7f, bg)
        canvas.drawRoundRect(rect, 7f, 7f, stroke)
        val right = PAGE_WIDTH - MARGIN - 10f
        canvas.drawText("بيانات الحالة", right, startY + 17f, headingPaint)

        val date = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(dateMillis))
        canvas.drawText("الاسم: ${customer.name}", right, startY + 34f, valuePaint)
        canvas.drawText("السن: ${customer.age.ifBlank { "-" }}", right, startY + 51f, valuePaint)
        canvas.drawText("واتساب: ${customer.phone.ifBlank { "-" }}", right, startY + 68f, valuePaint)
        canvas.drawText("التاريخ: $date", right - 210f, startY + 68f, valuePaint)
        return startY + 76f
    }

    /**
     * V37: organized full-width clinic footer.
     * Contact numbers sit on one balanced row and the complete address spans the
     * page below them, so the footer reads as one identity block rather than loose text.
     */
    private fun drawClinicFooter(
        context: Context,
        canvas: Canvas,
        dark: Int,
        gray: Int,
        branding: LabBranding = LabBranding()
    ) {
        val teal = Color.parseColor("#006D86")
        val top = PAGE_HEIGHT - 78f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4FAFB")
            style = Paint.Style.FILL
        }
        val topRule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            style = Paint.Style.FILL
        }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7E7EA")
            strokeWidth = 0.8f
        }

        canvas.drawRect(0f, top, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bg)
        canvas.drawRect(0f, top, PAGE_WIDTH.toFloat(), top + 3f, topRule)

        val contactPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val half = PAGE_WIDTH / 2f
        // WhatsApp block - left half
        val waIconX = 34f
        drawPdfDrawable(context, canvas, R.drawable.ic_whatsapp_pdf, waIconX, top + 12f, 18f)
        canvas.drawText(branding.whatsapp.ifBlank { CLINIC_WHATSAPP }, waIconX + 25f, top + 25f, contactPaint)

        // Phone block - right half
        val phoneIconX = half + 28f
        drawPdfDrawable(context, canvas, R.drawable.ic_phone_pdf, phoneIconX, top + 12f, 18f)
        canvas.drawText(branding.phone.ifBlank { CLINIC_PHONE }, phoneIconX + 25f, top + 25f, contactPaint)

        canvas.drawLine(half, top + 10f, half, top + 31f, divider)
        canvas.drawLine(18f, top + 37f, PAGE_WIDTH - 18f, top + 37f, divider)

        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val addressLayout = createRtlStaticLayout(
            listOf(branding.address.ifBlank { CLINIC_ADDRESS }, branding.extraContact)
                .map(String::trim).filter(String::isNotBlank).joinToString(" • "),
            addressPaint,
            (PAGE_WIDTH - 40f).toInt()
        )
        canvas.save()
        canvas.translate(20f, top + 43f)
        addressLayout.draw(canvas)
        canvas.restore()
    }

    private fun loadBrandLogo(context: Context, customPath: String): Bitmap? {
        val custom = customPath.trim().takeIf { it.isNotBlank() }?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
        return custom ?: BitmapFactory.decodeResource(context.resources, R.drawable.ic_clinic_logo)
    }

    private fun drawPdfDrawable(
        context: Context,
        canvas: Canvas,
        resId: Int,
        x: Float,
        y: Float,
        size: Float
    ) {
        ContextCompat.getDrawable(context, resId)?.let { drawable ->
            drawable.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
            drawable.draw(canvas)
        }
    }

    private fun normalizedGender(value: String): String {
        return when (value.trim().lowercase(Locale.US)) {
            "male", "m", "ذكر" -> "ذكر"
            "female", "f", "أنثى", "انثى" -> "أنثى"
            else -> value.ifBlank { "-" }
        }
    }

    private fun createCustomerQrBitmap(customer: Customer, size: Int): Bitmap {
        val payload = "TAHALIL_ALAKKAD_CUSTOMER|V=2|ID=${customer.id}|FILE=${customer.fileNumber}"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun renderPdfToLongPng(context: Context, pdfFile: File, outputName: String): File {
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val scale = 2
        val renderedPages = mutableListOf<Bitmap>()
        try {
            var maxWidth = 0
            var totalHeight = 0
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try {
                    val bitmap = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    renderedPages += bitmap
                    maxWidth = maxOf(maxWidth, bitmap.width)
                    totalHeight += bitmap.height
                } finally {
                    page.close()
                }
            }

            if (renderedPages.isEmpty()) error("PDF بدون صفحات")

            val combined = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combined)
            canvas.drawColor(Color.WHITE)
            var top = 0f
            renderedPages.forEach { bitmap ->
                canvas.drawBitmap(bitmap, 0f, top, null)
                top += bitmap.height
            }

            val outputDir = File(context.cacheDir, "pdf").apply { mkdirs() }
            val output = File(outputDir, outputName)
            FileOutputStream(output).use { stream ->
                if (!combined.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    error("تعذر ضغط الصورة")
                }
            }
            combined.recycle()
            return output
        } finally {
            renderedPages.forEach { if (!it.isRecycled) it.recycle() }
            renderer.close()
            descriptor.close()
        }
    }

    private fun safeFilePart(value: String): String =
        value.ifBlank { System.currentTimeMillis().toString() }.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun createRtlStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_OPPOSITE, 1.0f, 0.0f, false)
        }
    }

    private fun extractNumericPrice(price: String?): Double {
        if (price.isNullOrBlank()) return 0.0
        val normalized = price.replace(",", "").trim()
        // Legacy catalogue ranges (for example 1250/1500) use the first listed amount,
        // matching the on-screen total and order persistence logic.
        val match = Regex("""\d+(?:\.\d+)?""").find(normalized)
        return match?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun formatTotal(total: Double): String {
        return if (total % 1.0 == 0.0) total.toLong().toString() else String.format("%.2f", total)
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        }
    }
}
