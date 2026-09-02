package com.example.ui

import androidx.compose.material.icons.filled.*
import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomerOrder
import com.example.data.model.Customer
import com.example.data.model.AppUserProfile
import com.example.util.PdfGenerator
import com.example.settings.LocalAppSettings
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LabBlue = Color(0xFF075985)
private val LabCyan = Color(0xFF0EA5E9)
private val LabGreen = Color(0xFF16A34A)
private val LabAmber = Color(0xFFD97706)
private val LabSlate = Color(0xFF475569)

@Composable
fun LabOperatorScreen(
    viewModel: LabTestsViewModel,
    userEmail: String,
    managerActingAs: AppUserProfile? = null,
    onReturnToManager: (() -> Unit)? = null,
    onLogout: () -> Unit
) {
    val orders by viewModel.labOrders.collectAsState()
    val loading by viewModel.labOrdersLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingOpenOrderId by viewModel.pendingOpenOrderId.collectAsState()
    var selectedTab by remember { mutableStateOf("sent_to_lab") }
    var message by remember { mutableStateOf<String?>(null) }
    var uploadOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var deleteOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var uploadProgress by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showResultSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var shareOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appSettings = LocalAppSettings.current

    fun submitResultUris(order: CustomerOrder, uris: List<Uri>) {
        if (uris.isEmpty()) return
        uploadProgress = "جاري رفع النتائج..."
        viewModel.labUploadAndSendResults(
            order = order,
            uris = uris,
            onProgress = { done, total -> uploadProgress = "رفع $done من $total" },
            onResult = { ok, msg ->
                message = msg
                uploadProgress = ""
                if (ok) selectedTab = "ready"
            }
        )
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val order = uploadOrder
        if (order != null && uris.isNotEmpty()) {
            submitResultUris(order, uris)
        }
        uploadOrder = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val order = uploadOrder
        val uri = pendingCameraUri
        if (ok && order != null && uri != null) {
            submitResultUris(order, listOf(uri))
        }
        pendingCameraUri = null
        uploadOrder = null
    }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val documentScanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val order = uploadOrder
        if (activityResult.resultCode == Activity.RESULT_OK && order != null) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val uris = buildList {
                result?.pages?.forEach { add(it.imageUri) }
                result?.pdf?.uri?.let { add(it) }
            }.distinct()
            if (uris.isNotEmpty()) submitResultUris(order, uris)
        }
        uploadOrder = null
    }

    DisposableEffect(Unit) {
        viewModel.startLabOrdersRealtime { _, msg -> message = msg }
        onDispose { viewModel.stopLabOrdersRealtime() }
    }

    // V115: tapping a lab notification opens the exact order inside the lab queue.
    LaunchedEffect(pendingOpenOrderId, orders) {
        val targetId = pendingOpenOrderId.orEmpty()
        if (targetId.isNotBlank()) {
            val target = orders.firstOrNull { it.id == targetId }
            if (target != null) {
                selectedTab = target.workflowStatus
                searchQuery = target.orderNumber.ifBlank { target.customerName }
                viewModel.consumePendingOpenOrder()
            }
        }
    }

    val filtered = remember(orders, selectedTab, searchQuery) {
        val q = searchQuery.trim().lowercase()
        orders.filter { order ->
            order.workflowStatus == selectedTab && (
                q.isBlank() ||
                order.orderNumber.lowercase().contains(q) ||
                order.customerName.lowercase().contains(q) ||
                order.customerPhone.lowercase().contains(q) ||
                order.customerFileNumber.lowercase().contains(q)
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F8FB)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = LabBlue, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Biotech, contentDescription = null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("المعمل", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            if (isOnline) "متصل • استقبال الطلبات مباشر" else "غير متصل • سيظهر آخر ما تم تحميله",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    LabeledIconAction(label = "تحديث", onClick = { viewModel.loadLabOrders { _, msg -> message = msg } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
                    }
                    LabeledIconAction(label = "إعدادات التطبيق", onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "إعدادات التطبيق", tint = Color.White)
                    }
                    LabeledIconAction(label = "خروج", onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "خروج", tint = Color.White)
                    }
                }
            }

            if (managerActingAs != null && onReturnToManager != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF7ED),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF9A3412), modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "عبد الرحمن يعمل الآن بصلاحيات ${managerActingAs.displayName.ifBlank { managerActingAs.email }}",
                                color = Color(0xFF9A3412), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("الحساب الفعلي: Abdelrahman", color = Color(0xFF7C2D12), fontSize = 9.sp)
                        }
                        LabeledIconAction(label = "رجوع لعبد الرحمن", onClick = onReturnToManager) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                    }
                }
            }

            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Surface(
                    color = Color(0xFFEFF8FF),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFCFE8F5))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = LabBlue, modifier = Modifier.size(18.dp))
                        Text("الطلبات والتعديلات والإلغاءات بتتحدث لحظيًا لهذا الحساب", color = LabBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("بحث برقم الطلب أو اسم/هاتف العميل") }
                )

                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabStatusIcon(
                        modifier = Modifier.weight(1f),
                        key = "sent_to_lab",
                        label = "طلبات جديدة",
                        count = orders.count { it.workflowStatus == "sent_to_lab" },
                        selected = selectedTab,
                        icon = Icons.Default.Inbox,
                        accent = LabCyan,
                        onClick = { selectedTab = it }
                    )
                    LabStatusIcon(
                        modifier = Modifier.weight(1f),
                        key = "processing",
                        label = "جاري التنفيذ",
                        count = orders.count { it.workflowStatus == "processing" },
                        selected = selectedTab,
                        icon = Icons.Default.PlayArrow,
                        accent = LabAmber,
                        onClick = { selectedTab = it }
                    )
                    LabStatusIcon(
                        modifier = Modifier.weight(1f),
                        key = "ready",
                        label = "النتائج المرسلة",
                        count = orders.count { it.workflowStatus == "ready" },
                        selected = selectedTab,
                        icon = Icons.Default.DoneAll,
                        accent = LabGreen,
                        onClick = { selectedTab = it }
                    )
                }

                if (!message.isNullOrBlank()) {
                    Text(message.orEmpty(), modifier = Modifier.padding(vertical = 8.dp), color = LabSlate, fontSize = 11.sp)
                }
                if (uploadProgress.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(uploadProgress, color = LabBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                when {
                    loading && orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("لا توجد طلبات هنا", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { order ->
                            LabOrderCard(
                                order = order,
                                onAccept = {
                                    viewModel.labAcceptOrder(order) { ok, msg ->
                                        message = msg
                                        if (ok) selectedTab = "processing"
                                    }
                                },
                                onSendResults = {
                                    uploadOrder = order
                                    showResultSourceDialog = true
                                },
                                onCancel = { deleteOrder = order },
                                onEdit = { editOrder = order },
                                onShare = { shareOrder = order }
                            )
                        }
                    }
                }
            }
        }
    }


    if (showSettings) {
        UserSettingsDialog(
            viewModel = viewModel,
            settings = appSettings,
            onDismiss = { showSettings = false }
        )
    }

    if (showResultSourceDialog) {
        AlertDialog(
            onDismissRequest = {
                showResultSourceDialog = false
                uploadOrder = null
            },
            title = { Text("إضافة نتيجة التحاليل", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledIconAction(label = "تصوير النتيجة بالكاميرا", onClick = {
                            val order = uploadOrder ?: return@LabeledIconAction
                            val uri = createLabCameraUri(context)
                            pendingCameraUri = uri
                            showResultSourceDialog = false
                            cameraLauncher.launch(uri)
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CameraAlt, contentDescription = null) }
                    LabeledIconAction(label = "رفع صور أو PDF من الجهاز", onClick = {
                            showResultSourceDialog = false
                            picker.launch(arrayOf("image/*", "application/pdf"))
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudUpload, contentDescription = null) }
                    LabeledIconAction(label = "سكان ورقة النتائج", onClick = {
                            val activity = context as? Activity
                            if (activity == null) {
                                message = "تعذر فتح الماسح الضوئي على هذا الجهاز"
                                showResultSourceDialog = false
                                uploadOrder = null
                            } else {
                                documentScanner.getStartScanIntent(activity)
                                    .addOnSuccessListener { intentSender ->
                                        showResultSourceDialog = false
                                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                    }
                                    .addOnFailureListener { error ->
                                        message = "تعذر فتح السكانر: ${error.message.orEmpty()}"
                                        showResultSourceDialog = false
                                        uploadOrder = null
                                    }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DocumentScanner, contentDescription = null) }
                }
            },
            confirmButton = {},
            dismissButton = {
                LabeledIconAction(label = "إلغاء", onClick = {
                    showResultSourceDialog = false
                    uploadOrder = null
                }) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        )
    }

    val pendingEdit = editOrder
    if (pendingEdit != null) {
        var editName by remember(pendingEdit.id) { mutableStateOf(pendingEdit.customerName) }
        var editPhone by remember(pendingEdit.id) { mutableStateOf(pendingEdit.customerPhone) }
        var editAge by remember(pendingEdit.id) { mutableStateOf(pendingEdit.customerAge) }
        var editGender by remember(pendingEdit.id) { mutableStateOf(pendingEdit.customerGender) }
        var editNotes by remember(pendingEdit.id) { mutableStateOf(pendingEdit.notes) }
        AlertDialog(
            onDismissRequest = { editOrder = null },
            title = { Text("تعديل الطلب ${pendingEdit.orderNumber}", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("عدّل بيانات الطلب ثم احفظ.", fontSize = 11.sp)
                    OutlinedTextField(editName, { editName = it }, label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editPhone, { editPhone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(editAge, { editAge = it }, label = { Text("السن") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(editGender, { editGender = it }, label = { Text("النوع") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(editNotes, { editNotes = it }, label = { Text("ملاحظات الطلب") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text("قائمة التحاليل يحددها مستخدم العيادة.", color = Color(0xFF9A3412), fontSize = 10.sp)
                }
            },
            confirmButton = {
                LabeledIconAction(label = "حفظ وإبلاغ العيادة", onClick = {
                    viewModel.labEditOrderDetails(pendingEdit, editName, editPhone, editAge, editGender, editNotes) { _, msg -> message = msg }
                    editOrder = null
                }) { Icon(Icons.Default.Save, contentDescription = null) }
            },
            dismissButton = { LabeledIconAction(label = "إلغاء", onClick = { editOrder = null }) { Icon(Icons.Default.Close, contentDescription = null) } }
        )
    }

    val pendingShare = shareOrder
    if (pendingShare != null) {
        val shareCustomer = Customer(
            id = pendingShare.customerId, fileNumber = pendingShare.customerFileNumber,
            name = pendingShare.customerName, phone = pendingShare.customerPhone,
            age = pendingShare.customerAge, gender = pendingShare.customerGender,
            notes = pendingShare.notes, createdAtMillis = pendingShare.createdAtMillis,
            updatedAtMillis = pendingShare.updatedAtMillis
        )
        AlertDialog(
            onDismissRequest = { shareOrder = null },
            title = { Text("مشاركة الطلب", fontWeight = FontWeight.ExtraBold) },
            text = { Text("شارك نسخة المعمل الحالية بدون أسعار. الحالة الحالية محفوظة في بيانات الطلب.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledIconAction(label = "صورة", onClick = {
                        PdfGenerator.generateLabRequestImage(context, pendingShare, shareCustomer)?.let {
                            PdfGenerator.shareGeneratedImage(context, it, "طلب تحاليل ${pendingShare.orderNumber}", "مشاركة الطلب كصورة")
                        }
                        shareOrder = null
                    }) { Icon(Icons.Default.Image, contentDescription = null) }
                    LabeledIconAction(label = "PDF", onClick = {
                        PdfGenerator.generateLabRequestPdf(context, pendingShare, shareCustomer)?.let {
                            PdfGenerator.shareGeneratedPdf(context, it, "طلب تحاليل ${pendingShare.orderNumber}", "مشاركة الطلب PDF")
                        }
                        shareOrder = null
                    }) { Icon(Icons.Default.Description, contentDescription = null) }
                }
            },
            dismissButton = { LabeledIconAction(label = "إلغاء", onClick = { shareOrder = null }) { Icon(Icons.Default.Close, contentDescription = null) } }
        )
    }

    val pendingDelete = deleteOrder
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteOrder = null },
            title = { Text("إلغاء الطلب", fontWeight = FontWeight.ExtraBold) },
            text = { Text("سيتم إلغاء الطلب مع الاحتفاظ به في سجل العيادة والتدقيق. لا يوجد حذف نهائي لحساب المعمل.") },
            confirmButton = {
                LabeledIconAction(label = "تأكيد الإلغاء", onClick = {
                        viewModel.labCancelOrder(pendingDelete) { _, msg ->
                            message = msg
                            deleteOrder = null
                        }
                    }) { Icon(Icons.Default.Close, contentDescription = null) }
            },
            dismissButton = {
                LabeledIconAction(label = "رجوع", onClick = { deleteOrder = null }) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            }
        )
    }

}

@Composable
private fun LabStatusIcon(
    modifier: Modifier,
    key: String,
    label: String,
    count: Int,
    selected: String,
    icon: ImageVector,
    accent: Color,
    onClick: (String) -> Unit
) {
val active = selected == key
    LabeledIconAction(
        label = "$label • $count",
        onClick = { onClick(key) },
        modifier = modifier
    ) { Icon(if (active) Icons.Default.CheckCircle else icon, contentDescription = null, tint = accent) }
}

@Composable
private fun LabOrderCard(order: CustomerOrder, onAccept: () -> Unit, onSendResults: () -> Unit, onCancel: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit) {
    val accent = when (order.workflowStatus) {
        "sent_to_lab" -> LabCyan
        "processing" -> LabAmber
        else -> LabGreen
    }
    val time = remember(order.createdAtMillis) {
        SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale("ar", "EG")).format(Date(order.createdAtMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDCE7EC))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("طلب ${order.orderNumber}", color = Color(0xFF17324D), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                        Text(time, color = Color(0xFF64748B), fontSize = 9.sp)
                    }
                }
            }
            if (order.editCount > 0 && order.workflowStatus in setOf("sent_to_lab", "processing")) {
                val revisionRequested = order.notes.contains("طلب تعديل النتيجة من العيادة")
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF7ED),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(16.dp))
                        Text(
                            if (revisionRequested) "العيادة طلبت تعديل النتيجة — راجع الملاحظة وأعد الإرسال" else "الطلب اتحدّث — راجع البيانات قبل التنفيذ",
                            color = Color(0xFF9A3412), fontWeight = FontWeight.Bold, fontSize = 10.sp
                        )
                    }
                }
            }

            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF2FBFC), border = BorderStroke(1.dp, Color(0xFFCFE8EC))) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = LabBlue, modifier = Modifier.size(18.dp))
                        Text(order.customerName.ifBlank { "—" }, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A), fontSize = 14.sp)
                    }
                    if (order.customerPhone.isNotBlank()) Text("الهاتف: ${order.customerPhone}", color = Color(0xFF475569), fontSize = 11.sp)
                    if (order.customerAge.isNotBlank() || order.customerGender.isNotBlank()) {
                        Text(
                            listOf(
                                order.customerAge.takeIf { it.isNotBlank() }?.let { "السن: $it" },
                                order.customerGender.takeIf { it.isNotBlank() }?.let { "النوع: $it" }
                            ).filterNotNull().joinToString(" • "),
                            color = Color(0xFF475569),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.Biotech, contentDescription = null, tint = LabBlue, modifier = Modifier.size(18.dp))
                Text("التحاليل المطلوبة (${order.items.size})", color = LabBlue, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            order.items.forEachIndexed { index, item ->
                Column {
                    Text("${index + 1}. ${item.englishName.ifBlank { item.marketName.ifBlank { item.arabicName } }}", color = Color(0xFF17324D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    if (item.arabicName.isNotBlank() && item.arabicName != item.englishName) {
                        Text(item.arabicName, color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }
            }

            if (order.notes.isNotBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF7ED)) {
                    Text("ملاحظات العيادة: ${order.notes}", modifier = Modifier.fillMaxWidth().padding(9.dp), color = Color(0xFF9A3412), fontSize = 10.sp)
                }
            }

            // حساب المعمل لا يعرض أي سعر أو إجمالي أو خصم أو حالة دفع.
            when (order.workflowStatus) {
                "sent_to_lab" -> LabeledIconAction(label = "استلام الطلب", onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                "processing" -> LabeledIconAction(label = "إرسال النتيجة", onClick = onSendResults, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Send, contentDescription = null) }
                "ready" -> {
                    Surface(shape = RoundedCornerShape(12.dp), color = LabGreen.copy(alpha = 0.10f)) {
                        Text(
                            "تم إرسال النتيجة${if (order.resultUrls.isNotEmpty()) " • ${order.resultUrls.size} ملف" else ""}",
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            color = LabGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                    LabeledIconAction(label = "إعادة إرسال النتيجة", onClick = onSendResults, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Send, contentDescription = null) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledIconAction(label = "تعديل", onClick = onEdit, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, contentDescription = null) }
                LabeledIconAction(label = "مشاركة", onClick = onShare, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, contentDescription = null) }
            }

            LabeledIconAction(label = "إلغاء الطلب", onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Block, contentDescription = null) }
        }
    }
}


private fun createLabCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "lab_result_camera").apply { mkdirs() }
    val file = File.createTempFile("lab_result_", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}
