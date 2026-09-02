package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Customer
import com.example.data.model.CustomerOrder
import com.example.data.model.CustomerActivityEntry
import com.example.data.model.PaymentEntry
import com.example.data.model.LabTest
import com.example.data.model.normalizeText
import com.example.settings.LocalAppSettings
import com.example.settings.appText
import com.example.settings.tr
import com.example.util.CustomerQrImport
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CustomerPrimary = Color(0xFF006D86)
private val CustomerDark = Color(0xFF003A5D)
private val CustomerGreen = Color(0xFF15803D)
private val CustomerBg = Color(0xFFF4F7FB)

@Composable
fun CustomerSystemCard(onClick: () -> Unit) {
LabeledIconAction(
        label = appText("ملفات العملاء", "Customer Files"),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) { Icon(Icons.Default.People, contentDescription = null, tint = CustomerPrimary) }
}

@Composable
fun CustomerSystemDialog(
    viewModel: LabTestsViewModel,
    selectedTests: List<LabTest>,
    onDismiss: () -> Unit,
    onStartOrderForCustomer: (Customer) -> Unit = {},
    initialCustomer: Customer? = null,
    initialAutoScan: Boolean = false,
    initialQrUri: String? = null,
    onInitialQrConsumed: (String) -> Unit = {},
    onNavigateSearch: () -> Unit = {},
    onNavigateQuickImage: () -> Unit = {},
    onNavigateOrder: () -> Unit = {},
    onNavigateCustomers: () -> Unit = {},
    onNavigateScan: () -> Unit = {},
    onNavigateCatalog: () -> Unit = {}
) {
    val customers by viewModel.customers.collectAsState()
    val loading by viewModel.customersLoading.collectAsState()
    val orders by viewModel.customerOrders.collectAsState()
    val ordersLoading by viewModel.customerOrdersLoading.collectAsState()
    val activity by viewModel.customerActivity.collectAsState()
    val customerPriceOverrides by viewModel.customerPriceOverrides.collectAsState()
    val adminUnlocked by viewModel.adminUnlocked.collectAsState()
    val actualManager by viewModel.actualManager.collectAsState()
    val isManager = actualManager || adminUnlocked
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var selectedCustomer by remember(initialCustomer?.id) { mutableStateOf(initialCustomer) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var showCustomerEditor by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var showOrderCheckout by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    var blacklistReason by remember { mutableStateOf("") }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccess by remember { mutableStateOf(true) }
    var pendingOutputOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var pendingOutputCustomer by remember { mutableStateOf<Customer?>(null) }
    var qrScanBusy by remember { mutableStateOf(false) }
    var initialAutoScanConsumed by remember(initialAutoScan) { mutableStateOf(false) }

    val customerQrScannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
    }
    val customerQrScanner = remember(context) {
        GmsBarcodeScanning.getClient(context, customerQrScannerOptions)
    }

    // One resolver is shared by camera scan, local file import, and Android Share.
    val resolveCustomerQrPayload: (String) -> Unit = { rawValue ->
        viewModel.findCustomerByQrPayload(rawValue) { customer, message ->
            qrScanBusy = false
            if (customer != null) {
                if (customer.isArchived && !isManager) {
                    statusSuccess = false
                    statusMessage = tr("ملف العميل مؤرشف. لازم المدير يفتحه", "This customer is archived. A manager must open it")
                } else {
                    statusSuccess = true
                    statusMessage = null
                    selectedCustomer = customer
                    viewModel.loadCustomerOrders(customer.id)
                    viewModel.loadCustomerActivity(customer.id)
                }
            } else {
                statusSuccess = false
                statusMessage = message
            }
        }
    }

    val importCustomerQrFromUri: (Uri) -> Unit = { uri ->
        if (!qrScanBusy) {
            qrScanBusy = true
            statusMessage = tr("جاري قراءة QR من الملف...", "Reading QR from file...")
            statusSuccess = true
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    CustomerQrImport.readQrPayload(context, uri)
                }
                result.onSuccess { payload ->
                    resolveCustomerQrPayload(payload)
                }.onFailure { error ->
                    qrScanBusy = false
                    statusSuccess = false
                    statusMessage = tr(
                        "تعذر قراءة QR من الصورة: ${error.message ?: "الملف غير واضح"}",
                        "Unable to read QR from image: ${error.message ?: "The file is unclear"}"
                    )
                }
            }
        }
    }

    val qrFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importCustomerQrFromUri(uri)
    }

    val openScannedCustomer: () -> Unit = {
        if (!qrScanBusy) {
            qrScanBusy = true
            customerQrScanner.startScan()
                .addOnSuccessListener { barcode ->
                    resolveCustomerQrPayload(barcode.rawValue.orEmpty())
                }
                .addOnCanceledListener {
                    qrScanBusy = false
                }
                .addOnFailureListener { error ->
                    qrScanBusy = false
                    statusSuccess = false
                    statusMessage = tr(
                        "تعذر فتح ماسح QR: ${error.message ?: "خطأ غير معروف"}",
                        "Unable to open QR scanner: ${error.message ?: "Unknown error"}"
                    )
                }
        }
    }

    val openQrFromFile: () -> Unit = {
        if (!qrScanBusy) {
            qrFilePicker.launch(arrayOf("image/*"))
        }
    }

    // V66: the QR shortcut on the home screen opens the camera scanner immediately
    // instead of making the user enter Customer Records and press another button.
    LaunchedEffect(initialAutoScan) {
        if (initialAutoScan && !initialAutoScanConsumed) {
            initialAutoScanConsumed = true
            openScannedCustomer()
        }
    }

    // V39: a receipt shared from WhatsApp/gallery/files opens the same customer flow.
    LaunchedEffect(initialQrUri) {
        val shared = initialQrUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        onInitialQrConsumed(shared)
        importCustomerQrFromUri(Uri.parse(shared))
    }

    LaunchedEffect(initialCustomer?.id) {
        val customer = initialCustomer ?: return@LaunchedEffect
        selectedCustomer = customer
        viewModel.loadCustomerOrders(customer.id)
        viewModel.loadCustomerActivity(customer.id)
    }

    LaunchedEffect(Unit) {
        viewModel.loadCustomers()
    }

    val handleSystemBack: () -> Unit = {
        when {
            pendingOutputOrder != null -> { pendingOutputOrder = null; pendingOutputCustomer = null }
            showBlacklistDialog -> showBlacklistDialog = false
            showArchiveConfirm -> showArchiveConfirm = false
            showOrderCheckout -> showOrderCheckout = false
            selectedOrder != null -> selectedOrder = null
            showCustomerEditor -> showCustomerEditor = false
            selectedCustomer != null -> {
                selectedCustomer = null
                statusMessage = null
            }
            else -> onDismiss()
        }
    }

    BackHandler(enabled = true) { handleSystemBack() }

    Dialog(
        onDismissRequest = handleSystemBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CustomerBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CustomerHeader(
                    title = if (selectedCustomer == null) appText("ملفات العملاء", "Customer Files") else appText("ملف العميل", "Customer File"),
                    subtitle = if (selectedCustomer == null) {
                        appText("بحث وإضافة ومتابعة العملاء", "Search, add and manage customers")
                    } else {
                        selectedCustomer?.phone.orEmpty()
                    },
                    onBack = if (selectedCustomer != null) {
                        {
                            selectedCustomer = null
                            statusMessage = null
                        }
                    } else null,
                    onClose = onDismiss
                )
                DailyServicesNavBar(
                    active = "customers",
                    onCatalog = onNavigateCatalog,
                    onQuickImage = onNavigateQuickImage,
                    onOrder = onNavigateOrder,
                    onCustomers = onNavigateCustomers,
                    onScan = onNavigateScan
                )

                statusMessage?.let { message ->
                    StatusBanner(
                        message = message,
                        success = statusSuccess,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                if (selectedCustomer == null) {
                    CustomerListScreen(
                        customers = customers,
                        loading = loading,
                        query = query,
                        onQueryChange = { query = it },
                        onRefresh = {
                            viewModel.loadCustomers { ok, msg ->
                                statusSuccess = ok
                                statusMessage = msg
                            }
                        },
                        onScanQr = openScannedCustomer,
                        onImportQr = openQrFromFile,
                        qrScanBusy = qrScanBusy,
                        onAdd = {
                            editingCustomer = null
                            showCustomerEditor = true
                        },
                        isManager = isManager,
                        onSelect = { customer ->
                            selectedCustomer = customer
                            viewModel.loadCustomerOrders(customer.id)
                            viewModel.loadCustomerActivity(customer.id)
                            statusMessage = null
                        }
                    )
                } else {
                    val customer = selectedCustomer
                    if (customer != null) CustomerDetailsScreen(
                        customer = customer,
                        orders = orders,
                        ordersLoading = ordersLoading,
                        activity = activity,
                        selectedTests = selectedTests,
                        onEdit = {
                            editingCustomer = customer
                            showCustomerEditor = true
                        },
                        onSaveOrder = {
                            if (selectedTests.isNotEmpty()) showOrderCheckout = true
                        },
                        onStartNewOrder = { onStartOrderForCustomer(customer) },
                        onRepeatLastOrder = { previousOrder ->
                            val matched = viewModel.prepareRepeatOrder(previousOrder)
                            if (matched > 0) {
                                onStartOrderForCustomer(customer)
                            } else {
                                statusSuccess = false
                                statusMessage = tr("تحاليل الطلب القديم مش موجودة في الكتالوج الحالي", "The previous order tests are not available in the current catalogue")
                            }
                        },
                        onRefreshOrders = {
                            viewModel.loadCustomerOrders(customer.id)
                            viewModel.loadCustomerActivity(customer.id)
                        },
                        onOpenOrder = { order ->
                            selectedOrder = order
                        },
                        isManager = isManager,
                        onBlacklist = {
                            blacklistReason = customer.blacklistReason
                            showBlacklistDialog = true
                        },
                        onUnblacklist = {
                            viewModel.setCustomerBlacklist(customer, false) { ok, msg ->
                                statusSuccess = ok
                                statusMessage = msg
                                if (ok) selectedCustomer = customer.copy(
                                    isBlacklisted = false,
                                    blacklistReason = "",
                                    blacklistedAtMillis = 0L,
                                    updatedAtMillis = System.currentTimeMillis()
                                )
                            }
                        },
                        onArchive = { showArchiveConfirm = true },
                        onRestore = {
                            viewModel.setCustomerArchived(customer, false) { ok, msg ->
                                statusSuccess = ok
                                statusMessage = msg
                                if (ok) selectedCustomer = customer.copy(
                                    isArchived = false,
                                    archivedAtMillis = 0L,
                                    updatedAtMillis = System.currentTimeMillis()
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showBlacklistDialog && selectedCustomer != null) {
        val customer = selectedCustomer!!
        AlertDialog(
            onDismissRequest = { showBlacklistDialog = false },
            title = { Text(tr("إضافة للبلاك ليست", "Blacklist"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("العميل: ${customer.name}", "Customer: ${customer.name}"))
                    OutlinedTextField(
                        value = blacklistReason,
                        onValueChange = { blacklistReason = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                        label = { Text(tr("سبب الحظر - اختياري", "Blacklist reason - optional")) },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text(
                        tr("العميل المحظور يفضل محفوظ بس مش هيظهر لاختيار طلب جديد.", "The customer remains saved but cannot be selected for a new order."),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                LabeledIconAction(label = tr("حظر العميل", "Blacklist Customer"), onClick = {
                        viewModel.setCustomerBlacklist(customer, true, blacklistReason) { ok, msg ->
                            statusSuccess = ok
                            statusMessage = msg
                            if (ok) selectedCustomer = customer.copy(
                                isBlacklisted = true,
                                blacklistReason = blacklistReason.trim(),
                                blacklistedAtMillis = System.currentTimeMillis(),
                                updatedAtMillis = System.currentTimeMillis()
                            )
                            showBlacklistDialog = false
                        }
                    }) { Icon(Icons.Default.Block, contentDescription = null) }
            },
            dismissButton = { LabeledIconAction(label = appText(tr("إلغاء", "Cancel"), "Cancel"), onClick = { showBlacklistDialog = false }) { Icon(Icons.Default.Close, contentDescription = null) } }
        )
    }

    if (showArchiveConfirm && selectedCustomer != null) {
        val customer = selectedCustomer!!
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text(tr("حذف العميل من القائمة", "Remove Customer from List"), fontWeight = FontWeight.ExtraBold) },
            text = { Text(tr("هنشيل ${customer.name} من قائمة العملاء النشطين، لكن هنحتفظ بملفه وطلباته القديمة عشان التاريخ مايضيعش.", "${customer.name} will be removed from active customers while keeping the profile and history.")) },
            confirmButton = {
                LabeledIconAction(label = tr("حذف من القائمة", "Remove from List"), onClick = {
                        viewModel.setCustomerArchived(customer, true) { ok, msg ->
                            statusSuccess = ok
                            statusMessage = msg
                            if (ok) {
                                selectedCustomer = null
                                viewModel.loadCustomers()
                            }
                            showArchiveConfirm = false
                        }
                    }) { Icon(Icons.Default.Delete, contentDescription = null) }
            },
            dismissButton = { LabeledIconAction(label = appText(tr("إلغاء", "Cancel"), "Cancel"), onClick = { showArchiveConfirm = false }) { Icon(Icons.Default.Close, contentDescription = null) } }
        )
    }

    val checkoutCustomer = selectedCustomer
    if (showOrderCheckout && checkoutCustomer != null) {
        ProfessionalOrderCheckoutDialog(
            customer = checkoutCustomer,
            selectedTests = selectedTests,
            customerPriceOverrides = customerPriceOverrides,
            onDismiss = { showOrderCheckout = false },
            onConfirm = { discount, discountPercent, paymentStatus, paidAmount, notes, _, done ->
                viewModel.saveCustomerOrderAdvanced(
                    customer = checkoutCustomer,
                    tests = selectedTests,
                    discountAmount = discount,
                    discountPercent = discountPercent,
                    paymentStatus = paymentStatus,
                    paidAmount = paidAmount,
                    notes = notes
                ) { order, msg ->
                    done()
                    statusSuccess = order != null
                    statusMessage = msg
                    if (order != null) {
                        // V38: save first; image output is optional and generated only on request.
                        pendingOutputOrder = order
                        pendingOutputCustomer = checkoutCustomer
                        statusMessage = null
                        showOrderCheckout = false
                        viewModel.clearSelectedTests()
                    }
                }
            }
        )
    }

    val savedOutputOrder = pendingOutputOrder
    val savedOutputCustomer = pendingOutputCustomer
    if (savedOutputOrder != null && savedOutputCustomer != null) {
        OrderImageShareDialog(
            order = savedOutputOrder,
            customer = savedOutputCustomer,
            onDismiss = {
                pendingOutputOrder = null
                pendingOutputCustomer = null
            }
        )
    }

    selectedOrder?.let { order ->
        CustomerOrderDetailsDialog(
            viewModel = viewModel,
            customer = selectedCustomer,
            order = order,
            isManager = isManager,
            onDismiss = { selectedOrder = null }
        )
    }

    if (showCustomerEditor) {
        CustomerEditorDialog(
            existing = editingCustomer,
            onDismiss = { showCustomerEditor = false },
            onSave = { name, phone, alternatePhone, age, birthDate, gender, address, notes, importantAlert, tags, defaultDiscountPercent, done ->
                viewModel.saveCustomer(
                    existing = editingCustomer,
                    name = name,
                    phone = phone,
                    alternatePhone = alternatePhone,
                    age = age,
                    birthDate = birthDate,
                    gender = gender,
                    address = address,
                    notes = notes,
                    importantAlert = importantAlert,
                    tags = tags,
                    defaultDiscountPercent = defaultDiscountPercent
                ) { ok, msg ->
                    done()
                    statusSuccess = ok
                    statusMessage = msg
                    if (ok) {
                        showCustomerEditor = false
                        val updated = editingCustomer?.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            alternatePhone = alternatePhone.trim(),
                            age = age.trim(),
                            birthDate = birthDate.trim(),
                            gender = gender,
                            address = address.trim(),
                            notes = notes.trim(),
                            importantAlert = importantAlert.trim(),
                            tags = tags,
                            defaultDiscountPercent = defaultDiscountPercent.coerceIn(0.0, 100.0),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        if (updated != null) {
                            selectedCustomer = updated
                            editingCustomer = updated
                            viewModel.loadCustomerActivity(updated.id)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun CustomerOrderFlowDialog(
    viewModel: LabTestsViewModel,
    selectedTests: List<LabTest>,
    onDismiss: () -> Unit,
    onCustomerSelected: (Customer) -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    val loading by viewModel.customersLoading.collectAsState()
    var mode by remember { mutableStateOf("choose") }
    var query by remember { mutableStateOf("") }
    var showNewCustomer by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadCustomers() }

    val handleSystemBack: () -> Unit = {
        when {
            showNewCustomer -> showNewCustomer = false
            mode != "choose" -> mode = "choose"
            else -> onDismiss()
        }
    }

    BackHandler(enabled = true) { handleSystemBack() }

    Dialog(
        onDismissRequest = handleSystemBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = CustomerBg) {
            Column(Modifier.fillMaxSize()) {
                CustomerHeader(
                    title = tr("بيانات العميل", "Customer details"),
                    subtitle = tr("${selectedTests.size} تحليل مختار", "${selectedTests.size} selected tests"),
                    onBack = if (mode != "choose") ({ mode = "choose" }) else null,
                    onClose = onDismiss
                )

                errorMessage?.let {
                    StatusBanner(
                        message = it,
                        success = false,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                when (mode) {
                    "choose" -> {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                tr("ابدأ ببيانات العميل", "Start with customer details"),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF17324D)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CustomerChoiceTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.People,
                                    title = tr("عميل سابق", "Existing Customer"),
                                    subtitle = tr("اختيار من ملفات العملاء", "Choose from customer files"),
                                    accent = CustomerPrimary,
                                    onClick = { mode = "existing" }
                                )
                                CustomerChoiceTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.PersonAdd,
                                    title = tr("عميل جديد", "New Customer"),
                                    subtitle = tr("إنشاء ملف عميل جديد", "Create a new customer file"),
                                    accent = CustomerGreen,
                                    onClick = { showNewCustomer = true }
                                )
                            }
                        }
                    }
                    "existing" -> {
                        val normalizedQuery = remember(query) { normalizeText(query) }
                        val phoneQuery = remember(query) { query.filter { it.isDigit() } }
                        val filtered = remember(customers, normalizedQuery, phoneQuery) {
                            val availableCustomers = customers.filter { !it.isArchived }
                            if (query.isBlank()) availableCustomers else availableCustomers.filter { customer ->
                                val blob = normalizeText(
                                    listOf(
                                        customer.name, customer.fileNumber, customer.age, customer.birthDate, customer.gender,
                                        customer.address, customer.notes, customer.importantAlert, customer.tags.joinToString(" ")
                                    ).joinToString(" ")
                                )
                                blob.contains(normalizedQuery) ||
                                    (phoneQuery.isNotBlank() && (
                                        customer.phone.filter { it.isDigit() }.contains(phoneQuery) ||
                                            customer.alternatePhone.filter { it.isDigit() }.contains(phoneQuery)
                                        ))
                            }
                        }
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                singleLine = true,
                                label = { Text(tr("ابحث بالاسم أو رقم واتساب", "Search by name or WhatsApp")) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            if (loading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = CustomerPrimary)
                                }
                            } else if (filtered.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(tr("لا يوجد عملاء مطابقون", "No matching customers"), color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                CustomerDataTable(
                                    customers = filtered,
                                    onOpen = { customer ->
                                        if (customer.isBlacklisted) {
                                            errorMessage = tr(
                                                "العميل ${customer.name} على البلاك ليست. لازم المدير يفك الحظر قبل طلب جديد.",
                                                "${customer.name} is blacklisted. A manager must remove the blacklist before creating a new order."
                                            )
                                        } else {
                                            onCustomerSelected(customer)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewCustomer) {
        CustomerEditorDialog(
            existing = null,
            onDismiss = { showNewCustomer = false },
            onSave = { name, phone, alternatePhone, age, birthDate, gender, address, notes, importantAlert, tags, defaultDiscountPercent, done ->
                viewModel.saveCustomerAndReturn(
                    name = name,
                    phone = phone,
                    alternatePhone = alternatePhone,
                    age = age,
                    birthDate = birthDate,
                    gender = gender,
                    address = address,
                    notes = notes,
                    importantAlert = importantAlert,
                    tags = tags,
                    defaultDiscountPercent = defaultDiscountPercent
                ) { customer, msg ->
                    done()
                    if (customer != null) {
                        showNewCustomer = false
                        onCustomerSelected(customer)
                    } else {
                        errorMessage = msg
                    }
                }
            }
        )
    }
}

@Composable
private fun CustomerHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(CustomerDark, CustomerPrimary, Color(0xFF008FA0))
                )
            )
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                LabeledIconAction(label = appText(tr("رجوع", "Back"), "Back"), onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = appText(tr("رجوع", "Back"), "Back"), tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
            }
            LabeledIconAction(label = appText(tr("إغلاق", "Close"), "Close"), onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = appText(tr("إغلاق", "Close"), "Close"), tint = Color.White)
            }
        }
    }
}

@Composable
private fun CustomerListScreen(
    customers: List<Customer>,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onScanQr: () -> Unit,
    onImportQr: () -> Unit,
    qrScanBusy: Boolean,
    onAdd: () -> Unit,
    isManager: Boolean,
    onSelect: (Customer) -> Unit
) {
    var statusFilter by remember { mutableStateOf("active") }
    val normalizedQuery = remember(query) { normalizeText(query) }
    val phoneQuery = remember(query) { query.filter { it.isDigit() } }
    val filtered = remember(customers, query, normalizedQuery, phoneQuery, statusFilter, isManager) {
        customers.filter { customer ->
            // Manager searches across ALL customer states so an existing phone can always be found.
            // Without a query, the selected status tab still controls the list.
            val matchesStatus = if (!isManager) {
                // Staff can search/open every non-archived customer, including blacklist records.
                !customer.isArchived
            } else if (query.isNotBlank()) {
                // Manager search spans active + blacklist + archived.
                true
            } else {
                when (statusFilter) {
                    "blacklist" -> customer.isBlacklisted && !customer.isArchived
                    "archived" -> customer.isArchived
                    else -> !customer.isBlacklisted && !customer.isArchived
                }
            }
            val normalizedCustomerBlob = normalizeText(
                listOf(
                    customer.name,
                    customer.fileNumber,
                    customer.age,
                    customer.birthDate,
                    customer.gender,
                    customer.address,
                    customer.notes,
                    customer.importantAlert,
                    customer.tags.joinToString(" ")
                ).joinToString(" ")
            )
            val matchesPhone = phoneQuery.isNotBlank() && (
                customer.phone.filter { it.isDigit() }.contains(phoneQuery) ||
                    customer.alternatePhone.filter { it.isDigit() }.contains(phoneQuery)
                )
            val matchesQuery = query.isBlank() ||
                normalizedCustomerBlob.contains(normalizedQuery) ||
                matchesPhone
            matchesStatus && matchesQuery
        }
    }

    val activeCount = customers.count { !it.isBlacklisted && !it.isArchived }
    val blacklistCount = customers.count { it.isBlacklisted && !it.isArchived }
    val archivedCount = customers.count { it.isArchived }
    val recentCustomers = remember(customers) {
        customers
            .filterNot { it.isArchived }
            .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.createdAtMillis }
            .take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                singleLine = true,
                label = { Text(tr("ابحث بالاسم أو رقم واتساب", "Search by name or WhatsApp")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            LabeledIconAction(label = tr("تحديث", "Refresh"), onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = tr("تحديث", "Refresh"), tint = CustomerPrimary)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomerVisualToolCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.QrCodeScanner,
                title = tr("QR بالكاميرا", "Camera QR"),
                subtitle = tr("للورقة أو شاشة جهاز تاني", "Paper or another screen"),
                enabled = !qrScanBusy,
                busy = qrScanBusy,
                onClick = onScanQr
            )
            CustomerVisualToolCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.QrCodeScanner,
                title = tr("QR من ملف", "QR from file"),
                subtitle = tr("صورة على الموبايل", "Image on phone"),
                enabled = !qrScanBusy,
                busy = false,
                onClick = onImportQr
            )
        }

        Spacer(Modifier.height(10.dp))

        LabeledIconAction(label = appText("إضافة عميل جديد", "Add New Customer"), onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddCircle, contentDescription = null) }

        Spacer(Modifier.height(12.dp))

        if (query.isBlank() && recentCustomers.isNotEmpty()) {
            Text(
                tr("آخر العملاء", "Recent Customers"),
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D)
            )
            Spacer(Modifier.height(7.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 6.dp)
            ) {
                items(recentCustomers, key = { "recent_${it.id}" }) { customer ->
                    RecentCustomerCard(customer = customer, onClick = { onSelect(customer) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (isManager) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomerStatusCard(
                    title = tr("النشطين", "Active"),
                    count = activeCount,
                    selected = statusFilter == "active",
                    icon = Icons.Default.People,
                    modifier = Modifier.weight(1f),
                    onClick = { statusFilter = "active" }
                )
                CustomerStatusCard(
                    title = tr("بلاك ليست", "Blacklist"),
                    count = blacklistCount,
                    selected = statusFilter == "blacklist",
                    icon = Icons.Default.Block,
                    modifier = Modifier.weight(1f),
                    onClick = { statusFilter = "blacklist" }
                )
                CustomerStatusCard(
                    title = tr("المحذوفين", "Archived"),
                    count = archivedCount,
                    selected = statusFilter == "archived",
                    icon = Icons.Default.Delete,
                    modifier = Modifier.weight(1f),
                    onClick = { statusFilter = "archived" }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (!isManager) {
                    if (query.isNotBlank()) tr("نتائج البحث في العملاء", "Customer Search Results") else tr("كل العملاء المتاحين", "All Available Customers")
                } else if (query.isNotBlank()) {
                    tr("نتائج البحث في كل العملاء", "Search Results")
                } else when (statusFilter) {
                    "blacklist" -> tr("العملاء المحظورين", "Blacklisted Customers")
                    "archived" -> tr("العملاء المحذوفين", "Archived Customers")
                    else -> tr("العملاء النشطين", "Active Customers")
                },
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D)
            )
            Text(tr("${filtered.size} ملف", "${filtered.size} customers"), color = CustomerPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CustomerPrimary)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when (statusFilter) {
                            "blacklist" -> Icons.Default.Block
                            "archived" -> Icons.Default.Delete
                            else -> Icons.Default.People
                        },
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(if (query.isBlank()) tr("مفيش عملاء في القسم ده", "No customers in this section") else tr("مفيش نتائج للبحث", "No search results"), color = Color(0xFF64748B))
                }
            }
            else -> CustomerDataTable(customers = filtered, onOpen = onSelect)
        }
    }
}

@Composable
private fun CustomerVisualToolCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = modifier, enabled = enabled && !busy) {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = CustomerPrimary)
        else Icon(icon, contentDescription = null, tint = CustomerPrimary)
    }
}

@Composable
private fun RecentCustomerCard(customer: Customer, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(190.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                customer.name,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (customer.phone.isNotBlank()) {
                Text(
                    text = tr("واتساب: ${customer.phone}", "WhatsApp: ${customer.phone}"),
                    fontSize = 11.sp,
                    color = CustomerPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        
            LabeledIconAction(label = "فتح", onClick = onClick) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
    }
}

@Composable
private fun CustomerStatusCard(
    title: String,
    count: Int,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
LabeledIconAction(
        label = "$title • $count",
        onClick = onClick,
        modifier = modifier
    ) { Icon(if (selected) Icons.Default.CheckCircle else icon, contentDescription = null, tint = CustomerPrimary) }
}

@Composable
private fun CustomerChoiceTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 132.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(58.dp).background(accent.copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(9.dp))
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF17324D))
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private val CustomerTableWidth = 980.dp

@Composable
private fun CustomerDataTable(
    customers: List<Customer>,
    onOpen: (Customer) -> Unit
) {
    val horizontalState = rememberScrollState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCE6EC))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().horizontalScroll(horizontalState)
        ) {
            Column(
                modifier = Modifier.width(CustomerTableWidth).fillMaxHeight()
            ) {
                CustomerTableHeader()
                HorizontalDivider(color = Color(0xFFD9E4EA))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
                        CustomerTableRow(customer = customer, onOpen = { onOpen(customer) })
                        HorizontalDivider(color = Color(0xFFEDF2F5))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerTableHeader() {
    Row(
        modifier = Modifier.width(CustomerTableWidth).background(Color(0xFFE8F1F5)).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomerHeaderCell(tr("اسم العميل", "Customer name"), 230.dp)
        CustomerHeaderCell(tr("رقم الملف", "File no."), 110.dp)
        CustomerHeaderCell(tr("واتساب", "WhatsApp"), 150.dp)
        CustomerHeaderCell(tr("السن", "Age"), 80.dp)
        CustomerHeaderCell(tr("النوع", "Gender"), 100.dp)
        CustomerHeaderCell(tr("العنوان", "Address"), 190.dp)
        CustomerHeaderCell(tr("الحالة", "Status"), 90.dp)
        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
            Text(tr("فتح", "Open"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = CustomerDark)
        }
    }
}

@Composable
private fun CustomerHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        modifier = Modifier.width(width),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = CustomerDark,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CustomerTableRow(customer: Customer, onOpen: () -> Unit) {
    val accent = when {
        customer.isArchived -> Color(0xFF64748B)
        customer.isBlacklisted -> Color(0xFFB91C1C)
        else -> CustomerPrimary
    }
    val rowColor = when {
        customer.isArchived -> Color(0xFFF8FAFC)
        customer.isBlacklisted -> Color(0xFFFFF7F7)
        else -> Color.White
    }
    Row(
        modifier = Modifier.width(CustomerTableWidth).background(rowColor).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.width(230.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(accent.copy(alpha = .11f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(180.dp)) {
                Text(customer.name.ifBlank { "—" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (customer.alternatePhone.isNotBlank()) {
                    Text(tr("بديل: ${customer.alternatePhone}", "Alt: ${customer.alternatePhone}"), fontSize = 9.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        CustomerValueCell(customer.fileNumber.ifBlank { "—" }, 110.dp, bold = true)
        CustomerValueCell(customer.phone.ifBlank { "—" }, 150.dp)
        CustomerValueCell(customer.age.ifBlank { "—" }, 80.dp)
        CustomerValueCell(customer.gender.ifBlank { "—" }, 100.dp)
        CustomerValueCell(customer.address.ifBlank { "—" }, 190.dp)
        Box(Modifier.width(90.dp), contentAlignment = Alignment.CenterStart) {
            Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .10f)) {
                Text(
                    when {
                        customer.isArchived -> tr("مؤرشف", "Archived")
                        customer.isBlacklisted -> tr("محظور", "Blocked")
                        else -> tr("نشط", "Active")
                    },
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
            LabeledIconAction(label = tr("فتح", "Open"), onClick = onOpen) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
private fun CustomerValueCell(value: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false) {
    Text(
        value,
        modifier = Modifier.width(width),
        fontSize = 11.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = Color(0xFF475569),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CustomerDetailsScreen(
    customer: Customer,
    orders: List<CustomerOrder>,
    ordersLoading: Boolean,
    activity: List<CustomerActivityEntry>,
    selectedTests: List<LabTest>,
    onEdit: () -> Unit,
    onSaveOrder: () -> Unit,
    onStartNewOrder: () -> Unit,
    onRepeatLastOrder: (CustomerOrder) -> Unit,
    onRefreshOrders: () -> Unit,
    onOpenOrder: (CustomerOrder) -> Unit,
    isManager: Boolean,
    onBlacklist: () -> Unit,
    onUnblacklist: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current
    val validOrders = orders.filterNot { it.isVoided }
    val totalSpent = validOrders.sumOf { it.totalCustomerPrice }
    val totalDebt = validOrders.sumOf { it.remainingAmount }
    val lastVisit = validOrders.maxOfOrNull { it.createdAtMillis } ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).background(Color(0xFFE7F5F7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(customer.name.take(1), color = CustomerPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(customer.name, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color(0xFF17324D))
                            Text(tr("ملف العميل", "Customer file"), color = CustomerPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        LabeledIconAction(label = tr("تعديل", "Edit"), onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = tr("تعديل", "Edit"), tint = CustomerPrimary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("بيانات العميل", "Customer data"), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = CustomerDark)
                    Spacer(Modifier.height(7.dp))
                    CustomerProfileDataRow(tr("رقم الملف", "File number"), customer.fileNumber.ifBlank { "—" }, Icons.Default.Badge)
                    CustomerProfileDataRow(tr("واتساب", "WhatsApp"), customer.phone.ifBlank { "—" }, Icons.Default.Chat)
                    CustomerProfileDataRow(tr("رقم بديل", "Alternate phone"), customer.alternatePhone.ifBlank { "—" }, Icons.Default.Phone)
                    CustomerProfileDataRow(tr("السن", "Age"), customer.age.ifBlank { "—" }, Icons.Default.Person)
                    CustomerProfileDataRow(tr("تاريخ الميلاد", "Birth date"), customer.birthDate.ifBlank { "—" }, Icons.Default.CalendarMonth)
                    CustomerProfileDataRow(tr("النوع", "Gender"), customer.gender.ifBlank { "—" }, Icons.Default.Wc)
                    CustomerProfileDataRow(tr("العنوان", "Address"), customer.address.ifBlank { "—" }, Icons.Default.LocationOn)
                    CustomerProfileDataRow(tr("الخصم الافتراضي", "Default discount"), "${formatMoney(customer.defaultDiscountPercent)}%", Icons.Default.Percent)
                    if (customer.tags.isNotEmpty()) CustomerProfileDataRow(tr("التصنيفات", "Tags"), customer.tags.joinToString(" • "), Icons.Default.Label)
                    if (customer.importantAlert.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3E0)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(tr("تنبيه مهم", "Important alert"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E))
                                    Text(customer.importantAlert, fontSize = 12.sp, color = Color(0xFF78350F))
                                }
                            }
                        }
                    }
                    if (customer.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF7FAFC)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(tr("ملاحظات", "Notes"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
                                Spacer(Modifier.height(3.dp))
                                Text(customer.notes, color = Color(0xFF334155), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledIconAction(label = tr("اتصال", "Call"), onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}")))
                                }
                            }, modifier = Modifier.weight(1f), enabled = customer.phone.isNotBlank()) { Icon(Icons.Default.Call, contentDescription = null) }
                        LabeledIconAction(label = tr("واتساب", "WhatsApp"), onClick = {
                                val wa = normalizeWhatsAppNumber(customer.phone)
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa")))
                                }
                            }, modifier = Modifier.weight(1f), enabled = customer.phone.isNotBlank()) { Icon(Icons.Default.Chat, contentDescription = null) }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledIconAction(label = tr("طلب جديد", "New Order"), onClick = onStartNewOrder, modifier = Modifier.weight(1f), enabled = !customer.isBlacklisted && !customer.isArchived) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = CustomerGreen, modifier = Modifier.size(30.dp))
                }
                LabeledIconAction(label = tr("تعديل العميل", "Edit Customer"), onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = CustomerPrimary, modifier = Modifier.size(30.dp))
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F5F7))
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = CustomerPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr(
                            "كل طلب محفوظ يظهر هنا. اضغط على أي طلب لعرض التحاليل والإجمالي واستخراج صورة العميل أو صورة طلب المعمل.",
                            "Every saved order appears here. Open any order to view tests, totals, and create the customer or lab image."
                        ),
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = Color(0xFF365A5C),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = CustomerPrimary)
                    Spacer(Modifier.width(7.dp))
                    Text(appText(tr("سجل الطلبات", "Order History"), "Order history"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("${orders.size} طلب محفوظ", "${orders.size} saved orders"), color = CustomerPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LabeledIconAction(label = tr("تحديث الطلبات", "Refresh Orders"), onClick = onRefreshOrders) {
                        Icon(Icons.Default.Refresh, contentDescription = tr("تحديث الطلبات", "Refresh Orders"), tint = CustomerPrimary)
                    }
                }
            }
        }

        if (ordersLoading && orders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CustomerPrimary)
                        Spacer(Modifier.width(9.dp))
                        Text(tr("جاري تحميل الطلبات المحفوظة...", "Loading saved orders..."), color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (orders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(tr("لا توجد طلبات محفوظة لهذا العميل", "No saved orders for this customer"), modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
                }
            }
        } else {
            items(orders, key = { it.id }) { order ->
                CustomerOrderCard(order = order, onClick = { onOpenOrder(order) })
            }
        }

        if (customer.isBlacklisted || customer.isArchived) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (customer.isArchived) Color(0xFFF1F5F9) else Color(0xFFFFE4E6)
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (customer.isArchived) Icons.Default.Delete else Icons.Default.Block,
                            contentDescription = null,
                            tint = if (customer.isArchived) Color(0xFF475569) else Color(0xFFB91C1C)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (customer.isArchived) tr("العميل محذوف/مؤرشف", "Customer is Archived") else tr("العميل على البلاك ليست", "Customer is Blacklisted"),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (customer.isArchived) Color(0xFF334155) else Color(0xFF991B1B)
                            )
                            if (customer.isBlacklisted && customer.blacklistReason.isNotBlank()) {
                                Text(tr("السبب: ${customer.blacklistReason}", "Reason: ${customer.blacklistReason}"), fontSize = 12.sp, color = Color(0xFF7F1D1D))
                            }
                            if (customer.isBlacklisted) {
                                Text(tr("يظهر في البحث للجميع لكن ممنوع إنشاء طلب جديد له.", "Visible in search, but new orders are blocked."), fontSize = 11.sp, color = Color(0xFF7F1D1D))
                            }
                        }
                    }
                }
            }
        }

        if (isManager) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("إدارة حالة العميل", "Customer Status"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                        when {
                            customer.isArchived -> {
                                LabeledIconAction(label = tr("استرجاع العميل", "Restore Customer"), onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, contentDescription = null) }
                            }
                            customer.isBlacklisted -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LabeledIconAction(label = tr("فك الحظر", "Remove Blacklist"), onClick = onUnblacklist, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, contentDescription = null) }
                                    LabeledIconAction(label = tr("حذف آمن", "Safe Archive"), onClick = onArchive, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, contentDescription = null) }
                                }
                            }
                            else -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LabeledIconAction(label = tr("بلاك ليست", "Blacklist"), onClick = onBlacklist, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Block, contentDescription = null) }
                                    LabeledIconAction(label = tr("حذف آمن", "Safe Archive"), onClick = onArchive, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, contentDescription = null) }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("ملخص العميل", "Customer Summary"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomerStatCard(tr("الزيارات", "Visits"), validOrders.size.toString(), Modifier.weight(1f))
                    CustomerStatCard(tr("إجمالي التعاملات", "Total Business"), "${formatMoney(totalSpent)} ج", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomerStatCard(tr("المديونية", "Debt"), "${formatMoney(totalDebt)} ج", Modifier.weight(1f))
                    CustomerStatCard(tr("آخر زيارة", "Last Visit"), if (lastVisit > 0L) formatShortDate(lastVisit) else "—", Modifier.weight(1f))
                }
            }
        }

        if (totalDebt > 0.0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFFB91C1C))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(tr("على العميل مديونية", "Outstanding Debt"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                            Text(tr("${formatMoney(totalDebt)} جنيه متبقي", "${formatMoney(totalDebt)} EGP outstanding"), color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = CustomerGreen)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("طلب تحاليل جديد", "New Lab Order"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                            Text(
                                when {
                                    customer.isArchived -> tr("العميل محذوف من القائمة", "Customer is Archived")
                                    customer.isBlacklisted -> "العميل محظور - فك الحظر قبل إنشاء طلب جديد"
                                    selectedTests.isEmpty() -> tr("اختار التحاليل من شاشة البحث الأول", "Select tests from the search screen first")
                                    else -> "${selectedTests.size} تحليل مختار جاهز للحفظ"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val lastRepeatableOrder = validOrders.maxByOrNull { it.createdAtMillis }
                    if (lastRepeatableOrder != null) {
                        LabeledIconAction(label = tr("إعادة آخر طلب (${lastRepeatableOrder.items.size} تحليل)", "Repeat Last Order (${lastRepeatableOrder.items.size} tests)"), onClick = { onRepeatLastOrder(lastRepeatableOrder) }, modifier = Modifier.fillMaxWidth(), enabled = !customer.isBlacklisted && !customer.isArchived) { Icon(Icons.Default.History, contentDescription = null) }
                        Spacer(Modifier.height(8.dp))
                    }
                    LabeledIconAction(label = tr("اختيار تحاليل جديدة لهذا العميل", "Select New Tests for This Customer"), onClick = onStartNewOrder, modifier = Modifier.fillMaxWidth(), enabled = !customer.isBlacklisted && !customer.isArchived) { Icon(Icons.Default.AddCircle, contentDescription = null) }
                    Spacer(Modifier.height(8.dp))
                    LabeledIconAction(label = tr("حفظ التحاليل المختارة كطلب", "Save Selected Tests as Order"), onClick = onSaveOrder, modifier = Modifier.fillMaxWidth(), enabled = selectedTests.isNotEmpty() && !customer.isBlacklisted && !customer.isArchived) { Icon(Icons.Default.Save, contentDescription = null) }
                }
            }
        }

        item {
            Text("Timeline", fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), fontSize = 16.sp)
        }
        if (activity.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(tr("لا توجد عمليات مسجلة حتى الآن", "No activity recorded yet"), modifier = Modifier.padding(14.dp), fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
        } else {
            items(activity.take(20), key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.title, fontWeight = FontWeight.Bold, color = Color(0xFF17324D), modifier = Modifier.weight(1f))
                            Text(formatShortDate(entry.createdAtMillis), fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                        if (entry.details.isNotBlank()) Text(entry.details, fontSize = 11.sp, color = Color(0xFF475569))
                        if (entry.actorEmail.isNotBlank()) Text(entry.actorEmail, fontSize = 10.sp, color = CustomerPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerProfileDataRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(CustomerPrimary.copy(alpha = .09f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = CustomerPrimary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(label, modifier = Modifier.width(112.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Text(value, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF17324D), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CustomerPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF475569))
        Text(value, fontSize = 13.sp, color = Color(0xFF17324D))
    }
}

@Composable
private fun CustomerStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color(0xFF64748B), fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = CustomerPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
    }
}

@Composable
internal fun CustomerOrderDetailsDialog(
    viewModel: LabTestsViewModel,
    customer: Customer?,
    order: CustomerOrder,
    isManager: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val payments by viewModel.paymentHistory.collectAsState()
    val liveOrders by viewModel.customerOrders.collectAsState()
    var currentOrder by remember(order) { mutableStateOf(order) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showOrderEditDialog by remember { mutableStateOf(false) }
    var showVoidDialog by remember { mutableStateOf(false) }
    var showHardDeleteDialog by remember { mutableStateOf(false) }
    var deletingPermanently by remember { mutableStateOf(false) }
    var showResultRevisionDialog by remember { mutableStateOf(false) }
    var resultRevisionNote by remember { mutableStateOf("") }
    var resultRevisionSending by remember { mutableStateOf(false) }
    var voidReason by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccess by remember { mutableStateOf(true) }
    var pendingOutputOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var pendingOutputCustomer by remember { mutableStateOf<Customer?>(null) }

    LaunchedEffect(currentOrder.id, customer?.id) {
        val customerId = customer?.id ?: currentOrder.customerId
        viewModel.loadCustomerOrders(customerId, preserveCurrent = true)
        viewModel.loadPaymentHistory(customerId, currentOrder.id)
    }

    LaunchedEffect(liveOrders, currentOrder.id) {
        liveOrders.firstOrNull { it.id == currentOrder.id }?.let { latest ->
            if (latest != currentOrder) currentOrder = latest
        }
    }

    val handleSystemBack: () -> Unit = {
        when {
            pendingOutputOrder != null -> { pendingOutputOrder = null; pendingOutputCustomer = null }
            showPaymentDialog -> showPaymentDialog = false
            showEditDialog -> showEditDialog = false
            showOrderEditDialog -> showOrderEditDialog = false
            showResultRevisionDialog -> showResultRevisionDialog = false
            showVoidDialog -> showVoidDialog = false
            showHardDeleteDialog -> showHardDeleteDialog = false
            else -> onDismiss()
        }
    }

    BackHandler(enabled = true) { handleSystemBack() }

    Dialog(
        onDismissRequest = handleSystemBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = CustomerBg) {
            Column(Modifier.fillMaxSize()) {
                CustomerHeader(
                    title = tr("تفاصيل الطلب", "Order Details"),
                    subtitle = currentOrder.orderNumber,
                    onBack = onDismiss,
                    onClose = onDismiss
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    statusMessage?.let { message ->
                        item { StatusBanner(message, statusSuccess, Modifier.fillMaxWidth()) }
                    }

                    if (currentOrder.isVoided) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E6))
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFB91C1C))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(tr("طلب ملغي", "Voided Order"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                                        if (currentOrder.voidReason.isNotBlank()) Text(tr("السبب: ${currentOrder.voidReason}", "Reason: ${currentOrder.voidReason}"), fontSize = 12.sp, color = Color(0xFF7F1D1D))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    customer?.name ?: currentOrder.customerName,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF17324D)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    customer?.fileNumber ?: currentOrder.customerFileNumber,
                                    color = CustomerPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                InfoLine(Icons.Default.History, tr("التاريخ", "Date"), formatDate(currentOrder.createdAtMillis))
                                InfoLine(Icons.Default.ShoppingCart, tr("عدد التحاليل", "Tests Count"), currentOrder.items.size.toString())
                                val creator = currentOrder.createdByEmail.ifBlank { currentOrder.createdByUid }
                                if (creator.isNotBlank()) InfoLine(Icons.Default.Person, tr("أنشأ الطلب", "Created By"), creator)
                                if (currentOrder.editCount > 0) InfoLine(Icons.Default.Edit, tr("عدد التعديلات", "Edit Count"), currentOrder.editCount.toString())
                            }
                        }
                    }

                    item {
                        Text(tr("التحاليل", "Tests"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), fontSize = 16.sp)
                    }

                    items(currentOrder.items) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.englishName.ifBlank { item.arabicName },
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF17324D),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val secondName = when {
                                        item.arabicName.isNotBlank() && item.arabicName != item.englishName -> item.arabicName
                                        item.marketName.isNotBlank() -> item.marketName
                                        else -> ""
                                    }
                                    if (secondName.isNotBlank()) Text(secondName, fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("${formatMoney(item.customerPrice)} ${tr("ج", "EGP")}", color = CustomerPrimary, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F7ED))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tr("الإجمالي", "Total"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF166534))
                                    Text("${formatMoney(currentOrder.totalCustomerPrice)} ${tr("جنيه", "EGP")}", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = CustomerGreen)
                                }
                                FinancialRow(tr("المدفوع", "Paid"), currentOrder.paidAmount)
                                FinancialRow(tr("المتبقي", "Remaining"), currentOrder.remainingAmount, if (currentOrder.remainingAmount > 0.0) Color(0xFFB91C1C) else CustomerGreen)
                                Text(tr("حالة الدفع: ${paymentStatusArabic(currentOrder.paymentStatus)}", "Payment status: ${paymentStatusEnglish(currentOrder.paymentStatus)}"), fontWeight = FontWeight.Bold, color = Color(0xFF17324D))
                                if (currentOrder.notes.isNotBlank()) Text(tr("ملاحظات: ${currentOrder.notes}", "Notes: ${currentOrder.notes}"), fontSize = 12.sp, color = Color(0xFF475569))
                            }
                        }
                    }

                    if (!currentOrder.isVoided) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        tr("حالة الطلب", "Order Status"),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF17324D)
                                    )
                                    Text(
                                        tr(workflowStatusArabic(currentOrder.workflowStatus), workflowStatusEnglish(currentOrder.workflowStatus)),
                                        color = CustomerPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        tr("الحالة تتحدث تلقائيًا من المعمل.", "Status updates automatically from the lab."),
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                        item {
                            LabeledIconAction(label = tr("تعديل الطلب نفسه", "Edit Order"), onClick = { showOrderEditDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, contentDescription = null) }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabeledIconAction(label = tr("تحصيل دفعة", "Collect payment"), onClick = { showPaymentDialog = true }, modifier = Modifier.weight(1f), enabled = currentOrder.remainingAmount > 0.0) { Icon(Icons.Default.Payments, contentDescription = null) }
                                LabeledIconAction(label = tr("تعديل الدفع", "Edit Payment"), onClick = { showEditDialog = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, contentDescription = null) }
                            }
                        }
                    }

                    if (currentOrder.resultUrls.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFFAF3))
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("نتائج المعمل", fontWeight = FontWeight.ExtraBold, color = Color(0xFF166534), fontSize = 15.sp)
                                    currentOrder.resultUrls.forEachIndexed { index, url ->
                                        val label = currentOrder.resultNames.getOrNull(index) ?: "نتيجة ${index + 1}"
                                        Text(label, fontWeight = FontWeight.Bold, color = Color(0xFF17324D), fontSize = 12.sp)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            LabeledIconAction(label = "فتح", onClick = {
                                                    viewModel.openResultFile(url) { ok, message ->
                                                        if (!ok) android.widget.Toast.makeText(context, message.ifBlank { "تعذر فتح النتيجة" }, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Visibility, contentDescription = null) }
                                            LabeledIconAction(label = "تحميل", onClick = {
                                                    viewModel.downloadResultFile(url, label) { ok, message ->
                                                        statusSuccess = ok
                                                        statusMessage = message
                                                        android.widget.Toast.makeText(context, message.ifBlank { if (ok) "تم التحميل" else "تعذر التحميل" }, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Download, contentDescription = null) }
                                            LabeledIconAction(label = "مشاركة", onClick = {
                                                    viewModel.shareResultFile(url) { ok, message ->
                                                        if (!ok) android.widget.Toast.makeText(context, message.ifBlank { "تعذر المشاركة" }, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, contentDescription = null) }
                                        }
                                    }
                                    if (!currentOrder.isVoided && customer != null) {
                                        LabeledIconAction(label = "إعادة للمعمل للتعديل", onClick = {
                                                resultRevisionNote = ""
                                                showResultRevisionDialog = true
                                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, contentDescription = null) }
                                    }
                                }
                            }
                        }
                    }

                    if (payments.isNotEmpty()) {
                        item { Text(tr("سجل الدفعات", "Payment History"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D)) }
                        items(payments, key = { it.id }) { payment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${formatMoney(payment.amount)} ${tr("جنيه", "EGP")}", fontWeight = FontWeight.ExtraBold, color = CustomerGreen)
                                        Text(formatDate(payment.createdAtMillis), fontSize = 10.sp, color = Color(0xFF64748B))
                                        if (payment.createdByEmail.isNotBlank()) Text(payment.createdByEmail, fontSize = 10.sp, color = CustomerPrimary)
                                        if (payment.note.isNotBlank()) Text(payment.note, fontSize = 11.sp, color = Color(0xFF475569))
                                    }
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = CustomerGreen)
                                }
                            }
                        }
                    }

                    item {
                        LabeledIconAction(label = appText(tr("استخراج صورة للطلب", "Create order image"), "Create order image"), onClick = {
                                customer?.let { receiptCustomer ->
                                    pendingOutputOrder = currentOrder
                                    pendingOutputCustomer = receiptCustomer
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, contentDescription = null) }
                    }

                    if (!currentOrder.isVoided) {
                        item {
                            LabeledIconAction(label = tr("إلغاء الطلب", "Cancel Order"), onClick = { showVoidDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Block, contentDescription = null) }
                        }
                    }

                    if (isManager) {
                        item {
                            LabeledIconAction(label = tr("حذف نهائي — الإدارة فقط", "Permanent Delete — Admin Only"), onClick = { showHardDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, contentDescription = null) }
                        }
                    }
                }
            }
        }
    }

    val detailsOutputOrder = pendingOutputOrder
    val detailsOutputCustomer = pendingOutputCustomer
    if (detailsOutputOrder != null && detailsOutputCustomer != null) {
        OrderImageShareDialog(
            order = detailsOutputOrder,
            customer = detailsOutputCustomer,
            onDismiss = {
                pendingOutputOrder = null
                pendingOutputCustomer = null
            }
        )
    }

    val activeCustomer = customer
    if (showPaymentDialog && activeCustomer != null) {
        PaymentCollectionDialog(
            remaining = currentOrder.remainingAmount,
            onDismiss = { showPaymentDialog = false },
            onSave = { amount, note, done ->
                viewModel.collectOrderPayment(activeCustomer, currentOrder, amount, note) { updated, message ->
                    done()
                    statusSuccess = updated != null
                    statusMessage = message
                    if (updated != null) {
                        currentOrder = updated
                        showPaymentDialog = false
                    }
                }
            }
        )
    }


    if (showOrderEditDialog && activeCustomer != null) {
        var testsText by remember(currentOrder.id, currentOrder.editCount) {
            mutableStateOf(
                currentOrder.items.joinToString("\n") {
                    it.englishName.ifBlank { it.marketName.ifBlank { it.arabicName } }
                }
            )
        }
        var orderNotes by remember(currentOrder.id, currentOrder.editCount) { mutableStateOf(currentOrder.notes) }
        var savingOrderEdit by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!savingOrderEdit) showOrderEditDialog = false },
            title = { Text(tr("تعديل الطلب نفسه", "Edit Order"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr(
                            "اكتب التحاليل المطلوبة، كل تحليل في سطر. التعديل يظهر للمعمل تلقائيًا.",
                            "Enter requested tests, one per line. Changes appear automatically for the lab."
                        ),
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = testsText,
                        onValueChange = { testsText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
                        label = { Text(tr("التحاليل", "Tests")) },
                        minLines = 6
                    )
                    OutlinedTextField(
                        value = orderNotes,
                        onValueChange = { orderNotes = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("ملاحظات العيادة", "Clinic Notes")) },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                LabeledIconAction(label = if (savingOrderEdit) tr("جاري الحفظ...", "Saving...") else tr("حفظ التعديل", "Save Changes"), onClick = {
                        savingOrderEdit = true
                        viewModel.updateOrderContents(
                            customer = activeCustomer,
                            order = currentOrder,
                            testsText = testsText,
                            notes = orderNotes
                        ) { updated, message ->
                            savingOrderEdit = false
                            statusSuccess = updated != null
                            statusMessage = message
                            if (updated != null) {
                                currentOrder = updated
                                showOrderEditDialog = false
                            }
                        }
                    }, enabled = !savingOrderEdit) { Icon(Icons.Default.Edit, contentDescription = null) }
            },
            dismissButton = {
                LabeledIconAction(label = tr("رجوع", "Back"), onClick = { showOrderEditDialog = false }, enabled = !savingOrderEdit) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            }
        )
    }

    if (showEditDialog && activeCustomer != null) {
        OrderFinancialEditDialog(
            order = currentOrder,
            onDismiss = { showEditDialog = false },
            onSave = { discountAmount, discountPercent, paymentStatus, paidAmount, notes, done ->
                viewModel.updateOrderFinancials(
                    customer = activeCustomer,
                    order = currentOrder,
                    discountAmount = discountAmount,
                    discountPercent = discountPercent,
                    paymentStatus = paymentStatus,
                    paidAmount = paidAmount,
                    notes = notes
                ) { updated, message ->
                    done()
                    statusSuccess = updated != null
                    statusMessage = message
                    if (updated != null) {
                        currentOrder = updated
                        showEditDialog = false
                    }
                }
            }
        )
    }

    if (showResultRevisionDialog && activeCustomer != null) {
        AlertDialog(
            onDismissRequest = { if (!resultRevisionSending) showResultRevisionDialog = false },
            title = { Text("إعادة النتيجة للمعمل", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اكتب المطلوب تعديله، وهيظهر للمعمل ويرجع الطلب إلى جاري التنفيذ.")
                    OutlinedTextField(
                        value = resultRevisionNote,
                        onValueChange = { resultRevisionNote = it.take(500) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 105.dp),
                        label = { Text("ملاحظة التعديل (اختياري)") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                LabeledIconAction(label = if (resultRevisionSending) "جاري الإرسال..." else "إرسال للمعمل", onClick = {
                        resultRevisionSending = true
                        viewModel.requestResultRevision(activeCustomer, currentOrder, resultRevisionNote) { updated, message ->
                            resultRevisionSending = false
                            statusSuccess = updated != null
                            statusMessage = message
                            if (updated != null) {
                                currentOrder = updated
                                showResultRevisionDialog = false
                            }
                        }
                    }, enabled = !resultRevisionSending) { Icon(Icons.Default.Edit, contentDescription = null) }
            },
            dismissButton = {
                LabeledIconAction(label = "رجوع", onClick = { showResultRevisionDialog = false }, enabled = !resultRevisionSending) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            }
        )
    }

    if (showVoidDialog && activeCustomer != null) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text(tr("إلغاء الطلب", "Void Order"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("الطلب هيختفي من قوائم التشغيل ويظل محفوظًا في سجل المراجعة. المعمل سيعرف أنه اتلغى.", "The order will leave active worklists but remain in the audit trail. The lab will see it as cancelled."))
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it.take(300) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                        label = { Text(tr("سبب الإلغاء", "Void Reason")) },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                LabeledIconAction(label = tr("تأكيد الإلغاء", "Confirm Void"), onClick = {
                        viewModel.voidOrder(activeCustomer, currentOrder, voidReason) { ok, message ->
                            statusSuccess = ok
                            statusMessage = message
                            if (ok) {
                                currentOrder = currentOrder.copy(isVoided = true, voidReason = voidReason.trim())
                                showVoidDialog = false
                            }
                        }
                    }) { Icon(Icons.Default.Close, contentDescription = null) }
            },
            dismissButton = { LabeledIconAction(label = tr("رجوع", "Back"), onClick = { showVoidDialog = false }) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
        )
    }

    if (showHardDeleteDialog && activeCustomer != null) {
        AlertDialog(
            onDismissRequest = { if (!deletingPermanently) showHardDeleteDialog = false },
            title = { Text(tr("حذف نهائي للطلب", "Permanently Delete Order"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B)) },
            text = {
                Text(
                    tr(
                        "هذا الإجراء للإدارة فقط وسيحذف الطلب من سجل التشغيل نهائيًا. لو هدفك إيقاف الطلب فقط استخدم إلغاء الطلب بدلًا منه.",
                        "Admin only. This removes the order from operational history permanently. Use Cancel Order if you only want to stop it."
                    )
                )
            },
            confirmButton = {
                LabeledIconAction(label = if (deletingPermanently) tr("جاري الحذف...", "Deleting...") else tr("حذف نهائي", "Delete Permanently"), onClick = {
                        deletingPermanently = true
                        viewModel.deleteOrderPermanently(activeCustomer, currentOrder) { ok, message ->
                            deletingPermanently = false
                            statusSuccess = ok
                            statusMessage = message
                            showHardDeleteDialog = false
                            if (ok) onDismiss()
                        }
                    }, enabled = !deletingPermanently) { Icon(Icons.Default.Delete, contentDescription = null) }
            },
            dismissButton = {
                LabeledIconAction(label = tr("رجوع", "Back"), onClick = { showHardDeleteDialog = false }, enabled = !deletingPermanently) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            }
        )
    }
}

private fun workflowStatusArabic(status: String): String = when (status) {
    "ready", "delivered" -> "مكتمل"
    "sent_to_lab", "processing", "new" -> "قيد التنفيذ"
    else -> "قيد التنفيذ"
}

private fun workflowStatusEnglish(status: String): String = when (status) {
    "ready", "delivered" -> "Completed"
    else -> "In Progress"
}

@Composable
private fun PaymentCollectionDialog(
    remaining: Double,
    onDismiss: () -> Unit,
    onSave: (amount: Double, note: String, done: () -> Unit) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(tr("تحصيل دفعة", "Collect Payment"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("المتبقي: ${formatMoney(remaining)} جنيه", "Outstanding: ${formatMoney(remaining)} EGP"), fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    label = { Text(tr("المبلغ المحصل", "Collected Amount")) },
                    suffix = { Text(tr("ج", "EGP")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    label = { Text(tr("ملاحظة - اختياري", "Note - Optional")) },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            LabeledIconAction(label = if (saving) tr("جاري الحفظ...", "Saving...") else tr("تسجيل الدفعة", "Record Payment"), onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (!saving) {
                        saving = true
                        onSave(amount, note) { saving = false }
                    }
                }, enabled = !saving) { Icon(Icons.Default.Save, contentDescription = null) }
        },
        dismissButton = { LabeledIconAction(label = appText(tr("إلغاء", "Cancel"), "Cancel"), onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, contentDescription = null) } }
    )
}

@Composable
private fun OrderFinancialEditDialog(
    order: CustomerOrder,
    onDismiss: () -> Unit,
    onSave: (
        discountAmount: Double,
        discountPercent: Double,
        paymentStatus: String,
        paidAmount: Double,
        notes: String,
        done: () -> Unit
    ) -> Unit
) {
    var paymentStatus by remember { mutableStateOf(order.paymentStatus) }
    var paidText by remember { mutableStateOf(formatMoney(order.paidAmount).takeIf { order.paidAmount > 0.0 }.orEmpty()) }
    var notes by remember { mutableStateOf(order.notes) }
    var saving by remember { mutableStateOf(false) }

    val total = order.totalCustomerPrice
    val typedPaid = paidText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val paidEntryRequired = paymentStatus == "partial" || paymentStatus == "unpaid"
    val paidEntryValid = when (paymentStatus) {
        "partial" -> paidText.isNotBlank() && typedPaid > 0.0 && typedPaid < total
        "unpaid" -> paidText.isNotBlank() && typedPaid == 0.0
        else -> true
    }
    val paidAmount = when (paymentStatus) {
        "paid" -> total
        "partial" -> typedPaid.coerceIn(0.0, total)
        else -> 0.0
    }
    val remaining = (total - paidAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(tr("تعديل الدفع", "Edit Payment"), fontWeight = FontWeight.ExtraBold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(tr("الإجمالي: ${formatMoney(total)} ج", "Total: ${formatMoney(total)} EGP"), fontWeight = FontWeight.ExtraBold, color = CustomerGreen)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "unpaid" to tr("غير مدفوع", "Unpaid"),
                            "partial" to tr("جزئي", "Partial"),
                            "paid" to tr("مدفوع", "Paid")
                        ).forEach { (value, label) ->
                            LabeledIconAction(label = label, onClick = {
                                    paymentStatus = value
                                    paidText = when (value) {
                                        "unpaid" -> "0"
                                        "partial" -> paidText.takeIf { it != "0" }.orEmpty()
                                        else -> ""
                                    }
                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                        }
                    }
                }
                if (paidEntryRequired) {
                    item {
                        OutlinedTextField(
                            value = paidText,
                            onValueChange = { paidText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(12) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            label = { Text(tr("المبلغ المسدد (إجباري)", "Paid amount (required)")) },
                            suffix = { Text(tr("ج", "EGP")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = !paidEntryValid
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("المدفوع: ${formatMoney(paidAmount)} ج", "Paid: ${formatMoney(paidAmount)} EGP"), fontWeight = FontWeight.Bold, color = CustomerGreen)
                        Text(tr("المتبقي: ${formatMoney(remaining)} ج", "Remaining: ${formatMoney(remaining)} EGP"), fontWeight = FontWeight.Bold, color = if (remaining > 0.0) Color(0xFFB91C1C) else CustomerGreen)
                    }
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text(tr("ملاحظات - اختياري", "Notes - Optional")) },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            LabeledIconAction(label = if (saving) tr("جاري الحفظ...", "Saving...") else tr("حفظ", "Save"), onClick = {
                    if (!saving) {
                        saving = true
                        onSave(order.discountAmount, order.discountPercent, paymentStatus, paidAmount, notes) { saving = false }
                    }
                }, enabled = !saving && paidEntryValid) { Icon(Icons.Default.Save, contentDescription = null) }
        },
        dismissButton = { LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, contentDescription = null) } }
    )
}

@Composable
private fun CustomerOrderCard(order: CustomerOrder, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.orderNumber, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                    Text(formatDate(order.createdAtMillis), fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        tr("حالة الطلب: ${workflowStatusArabic(order.workflowStatus)}", "Order status: ${workflowStatusEnglish(order.workflowStatus)}"),
                        fontSize = 11.sp,
                        color = CustomerPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${paymentStatusArabic(order.paymentStatus)} • المتبقي ${formatMoney(order.remainingAmount)} ج",
                        fontSize = 11.sp,
                        color = if (order.remainingAmount > 0.0) Color(0xFFB91C1C) else CustomerGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(tr("${order.items.size} تحليل • فتح / صورة", "${order.items.size} tests • open / image"), fontSize = 11.sp, color = CustomerPrimary)
                }
                Text(
                    "${formatMoney(order.totalCustomerPrice)} جنيه",
                    color = CustomerGreen,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(Modifier.height(8.dp))
            order.items.take(4).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.englishName.ifBlank { item.arabicName },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${formatMoney(item.customerPrice)} ${tr("ج", "EGP")}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CustomerPrimary)
                }
            }
            if (order.items.size > 4) {
                Text(tr("+ ${order.items.size - 4} تحاليل أخرى", "+ ${order.items.size - 4} more tests"), fontSize = 11.sp, color = Color(0xFF64748B))
            }
        
            LabeledIconAction(label = "فتح", onClick = onClick) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
    }
}

@Composable
private fun CustomerEditorDialog(
    existing: Customer?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        alternatePhone: String,
        age: String,
        birthDate: String,
        gender: String,
        address: String,
        notes: String,
        importantAlert: String,
        tags: List<String>,
        defaultDiscountPercent: Double,
        done: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val today = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()) }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.phone.orEmpty()) }
    var age by remember(existing) { mutableStateOf(existing?.age.orEmpty()) }
    var selectedDate by remember(existing) { mutableStateOf(existing?.birthDate?.ifBlank { today } ?: today) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    fun openDatePicker() {
        val calendar = Calendar.getInstance()
        runCatching {
            val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(selectedDate)
            if (parsed != null) calendar.time = parsed
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = String.format(Locale.US, "%02d/%02d/%04d", day, month + 1, year)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    BackHandler(enabled = true) {
        if (!saving) onDismiss()
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = CustomerBg
        ) {
            Column(Modifier.fillMaxSize()) {
                CustomerHeader(
                    title = if (existing == null) tr("إضافة عميل", "Add Customer") else tr("تعديل بيانات العميل", "Edit Customer"),
                    subtitle = tr("بيانات أساسية فقط", "Essential data only"),
                    onBack = onDismiss,
                    onClose = onDismiss
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 30.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(120) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            label = { Text(tr("اسم العميل", "Customer Name")) },
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(20) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            label = { Text(tr("رقم واتساب", "WhatsApp Number")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it.filter(Char::isDigit).take(3) },
                                modifier = Modifier.weight(0.8f).heightIn(min = 72.dp),
                                label = { Text(tr("السن", "Age")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            LabeledIconAction(label = tr("التاريخ", "Date"), onClick = { openDatePicker() }, modifier = Modifier.weight(1.2f).heightIn(min = 72.dp)) { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(500) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                            label = { Text(tr("ملاحظات - اختياري", "Notes - Optional")) },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    item {
                        Text(
                            tr("المدفوع والمتبقي يتسجلوا داخل كل طلب، مش في بيانات العميل الأساسية.", "Paid and remaining amounts are recorded per order, not in the basic customer profile."),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    item {
                        LabeledIconAction(label = tr("حفظ", "Save"), onClick = {
                                if (!saving && name.isNotBlank() && phone.isNotBlank()) {
                                    saving = true
                                    onSave(
                                        name.trim(),
                                        phone.trim(),
                                        existing?.alternatePhone.orEmpty(),
                                        age.trim(),
                                        selectedDate,
                                        existing?.gender.orEmpty(),
                                        existing?.address.orEmpty(),
                                        notes.trim(),
                                        existing?.importantAlert.orEmpty(),
                                        existing?.tags.orEmpty(),
                                        existing?.defaultDiscountPercent ?: 0.0
                                    ) { saving = false }
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = !saving && name.isNotBlank() && phone.isNotBlank()) { Icon(Icons.Default.Save, contentDescription = null) }
                        LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = !saving) { Icon(Icons.Default.Close, contentDescription = null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String, success: Boolean, modifier: Modifier = Modifier) {
    val bg = if (success) Color(0xFFE8F7ED) else Color(0xFFFFEEEE)
    val fg = if (success) Color(0xFF166534) else Color(0xFFB91C1C)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(message, color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun FinancialRow(label: String, value: Double, valueColor: Color = Color(0xFF17324D)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF64748B), fontSize = 12.sp)
        val prefix = if (value < 0.0) "- " else ""
        Text("$prefix${formatMoney(kotlin.math.abs(value))} ${tr("جنيه", "EGP")}", fontWeight = FontWeight.Bold, color = valueColor, fontSize = 12.sp)
    }
}

private fun paymentStatusArabic(status: String): String = when (status) {
    "paid" -> tr("مدفوع بالكامل", "Fully Paid")
    "partial" -> tr("مدفوع جزئيا", "Partially Paid")
    else -> tr("غير مدفوع", "Unpaid")
}

private fun paymentStatusEnglish(status: String): String = when (status) {
    "paid" -> "Paid"
    "partial" -> "Partially paid"
    else -> "Unpaid"
}


private fun formatDate(value: Long): String {
    if (value <= 0L) return ""
    return SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale("ar", "EG")).format(Date(value))
}

private fun formatMoney(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
}

private fun formatPercent(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
}

private fun formatShortDate(value: Long): String {
    if (value <= 0L) return "—"
    return SimpleDateFormat("dd/MM/yy", Locale("ar", "EG")).format(Date(value))
}

private fun normalizeWhatsAppNumber(value: String): String {
    val digits = value.filter { it.isDigit() }
    return when {
        digits.startsWith("20") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "20${digits.drop(1)}"
        else -> digits
    }
}
