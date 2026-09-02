package com.example.ui

import androidx.compose.material.icons.filled.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.Customer
import com.example.data.model.CustomerOrder
import com.example.settings.LocalAppSettings
import com.example.settings.appText
import com.example.util.PdfGenerator

/**
 * V74 production output flow: image-only, with direct Save and Share actions.
 * No PDF choice is exposed anywhere in the order workflow.
 */
@Composable
fun OrderImageShareDialog(
    order: CustomerOrder,
    customer: Customer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val branding = PdfGenerator.LabBranding(
        name = if (settings.pdfLabName.isBlank() || settings.pdfLabName == "تحاليل العقاد") "عيادات العقاد التخصصية" else settings.pdfLabName,
        tagline = settings.brandTagline,
        whatsapp = settings.brandWhatsApp,
        phone = settings.brandPhone,
        address = settings.brandAddress,
        extraContact = if (settings.pdfShowContactInfo) settings.pdfContactInfo else "",
        logoPath = settings.brandLogoPath
    )
    var busyAction by remember { mutableStateOf<String?>(null) }
    var pendingLegacySave by remember { mutableStateOf<String?>(null) }

    fun customerImage() = PdfGenerator.generateCustomerOrderImage(context, order, customer, branding)
    fun labImage() = PdfGenerator.generateLabRequestImage(context, order, customer, branding)

    fun saveImage(kind: String) {
        if (busyAction != null) return
        busyAction = "save_$kind"
        try {
            val file = if (kind == "customer") customerImage() else labImage()
            if (file != null) {
                val prefix = if (kind == "customer") "Customer" else "Lab_Request"
                PdfGenerator.saveGeneratedImageToGallery(
                    context = context,
                    file = file,
                    displayName = "Tahalil_Alakkad_${prefix}_${order.orderNumber}.png"
                )
            }
        } finally {
            busyAction = null
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingLegacySave
        pendingLegacySave = null
        if (granted && kind != null) saveImage(kind)
    }

    fun requestSave(kind: String) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingLegacySave = kind
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImage(kind)
        }
    }

    fun shareImage(kind: String) {
        if (busyAction != null) return
        busyAction = "share_$kind"
        try {
            val file = if (kind == "customer") customerImage() else labImage()
            if (file != null) {
                PdfGenerator.shareGeneratedImage(
                    context = context,
                    file = file,
                    subject = if (kind == "customer") {
                        "تحاليل ${customer.name} - ${settings.pdfLabName}"
                    } else {
                        "طلب تحاليل - ${customer.name}"
                    },
                    chooserTitle = if (kind == "customer") "مشاركة صورة العميل" else "مشاركة صورة طلب المعمل"
                )
            }
        } finally {
            busyAction = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D))
                    Text(
                        text = appText("تم حفظ الطلب — إيصال العميل", "Order saved — customer receipt"),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF17324D)
                    )
                }

                Text(
                    text = appText(
                        "إيصال دفع العميل جاهز ببياناته والتحاليل والدفع وQR الخاص به. احفظه أو شاركه مباشرة.",
                        "The customer payment receipt is ready with details, tests, payment and a unique QR. Save or share it directly."
                    ),
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )

                ImageActionSection(
                    title = appText("إيصال دفع للعميل", "Customer payment receipt"),
                    icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    busySave = busyAction == "save_customer",
                    busyShare = busyAction == "share_customer",
                    enabled = busyAction == null,
                    onSave = { requestSave("customer") },
                    onShare = { shareImage("customer") }
                )


                LabeledIconAction(label = appText("إغلاق", "Close"), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = busyAction == null) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun ImageActionSection(
    title: String,
    icon: @Composable () -> Unit,
    busySave: Boolean,
    busyShare: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                icon()
                Text(title, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledIconAction(label = appText("حفظ", "Save"), onClick = onSave, modifier = Modifier.weight(1f), enabled = enabled) { Icon(Icons.Default.Save, contentDescription = null) }
                LabeledIconAction(label = appText("مشاركة", "Share"), onClick = onShare, modifier = Modifier.weight(1f), enabled = enabled) { Icon(Icons.Default.Share, contentDescription = null) }
            }
        }
    }
}
