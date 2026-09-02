package com.example.ui

import androidx.compose.material.icons.filled.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.AppUserProfile
import com.example.data.model.Customer
import com.example.data.model.CustomerOrder
import com.example.data.model.LabTest
import com.example.settings.LocalAppSettings
import com.example.settings.appText
import com.example.settings.tr
import com.example.util.PdfGenerator
import com.example.util.TestDocumentImport
import com.example.util.NonLabMedicalLookup
import com.example.util.NonLabMedicalInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LabTestsApp(
    viewModel: LabTestsViewModel,
    currentUserEmail: String? = null,
    currentUserUid: String? = null,
    onLogout: (() -> Unit)? = null
) {
    val appSettings by viewModel.appSettings.collectAsState()
    val baseDensity = LocalDensity.current
    val layoutDirection = if (appSettings.language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalDensity provides Density(baseDensity.density, appSettings.fontScale),
        LocalAppSettings provides appSettings
    ) {
        val searchQuery by viewModel.searchQuery.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        val selectedTests by viewModel.selectedTests.collectAsState()
        val recognizedTests by viewModel.recognizedTests.collectAsState()
        val adminUnlocked by viewModel.adminUnlocked.collectAsState()
        val actualManager by viewModel.actualManager.collectAsState()
        val actingAsUser by viewModel.actingAsUser.collectAsState()
        val isOnline by viewModel.isOnline.collectAsState()
        val offlineGraceAccess by viewModel.offlineGraceAccess.collectAsState()
        val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
        val lastSuccessfulSyncMillis by viewModel.lastSuccessfulSyncMillis.collectAsState()
        val pendingSharedQrUri by viewModel.pendingSharedQrUri.collectAsState()
        val pendingOpenOrderId by viewModel.pendingOpenOrderId.collectAsState()
        val lab2LabPrices by viewModel.lab2LabPrices.collectAsState()
        val customerPriceOverrides by viewModel.customerPriceOverrides.collectAsState()
        val dailyOrders by viewModel.dailyOrders.collectAsState()
        val dailyOrdersLoading by viewModel.dailyOrdersLoading.collectAsState()
        // V116: while Abdelrahman is impersonating another user, the UI follows that
        // user's daily permissions. Normal users still cannot start user switching.
        val isManager = (actualManager && actingAsUser == null) || adminUnlocked
        var priceEditorTest by remember { mutableStateOf<LabTest?>(null) }
        var showAddCatalogEditor by remember { mutableStateOf(false) }
        var showBulkCatalogEditor by remember { mutableStateOf(false) }
        var deleteCatalogTest by remember { mutableStateOf<LabTest?>(null) }
        var showManagerDashboard by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showAdminSettingsDialog by remember { mutableStateOf(false) }
        var showAdminPasswordDialog by remember { mutableStateOf(false) }
        var showUserSwitcher by remember { mutableStateOf(false) }
        var adminPin by remember { mutableStateOf("") }
        var adminUnlocking by remember { mutableStateOf(false) }
        var adminUnlockError by remember { mutableStateOf<String?>(null) }
        var pendingAdminAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var showCustomerSystem by remember { mutableStateOf(false) }
        var customerSystemInitialCustomer by remember { mutableStateOf<Customer?>(null) }
        var customerSystemAutoScan by remember { mutableStateOf(false) }
        var showScanHub by remember { mutableStateOf(false) }
        var showOperationsV14 by remember { mutableStateOf(false) }
        var operationsInitialPage by remember { mutableStateOf("home") }
        var operationsInitialDebtsOnly by remember { mutableStateOf(false) }
        var operationsInitialRange by remember { mutableStateOf("today") }
        // V58: every account lands on the same unified home.
        var managerMainMode by remember(currentUserUid, currentUserEmail) { mutableStateOf("home") } // home | orders | worklist | order | catalog | quick_image
        var showOrderCustomerPicker by remember { mutableStateOf(false) }
        var showOrderCheckout by remember { mutableStateOf(false) }
        var autoCheckoutAfterCustomerPick by remember { mutableStateOf(false) }
        var activeOrderCustomer by remember { mutableStateOf<Customer?>(null) }
        val context = LocalContext.current
        var orderStatusMessage by remember { mutableStateOf<String?>(null) }
        var orderStatusSuccess by remember { mutableStateOf(true) }
        var importingTestsDocument by remember { mutableStateOf(false) }
        val documentImportScope = rememberCoroutineScope()
        val testsDocumentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            // V93: image import is a normal daily function for every approved user.
            viewModel.setExternalPickerActive(false)
            if (uri != null) {
                importingTestsDocument = true
                documentImportScope.launch {
                    val result = TestDocumentImport.readTests(context, uri)
                    importingTestsDocument = false
                    result.onSuccess { readResult ->
                        val count = viewModel.applyRecognizedTestsToSearch(readResult.text)
                        orderStatusSuccess = count > 0
                        orderStatusMessage = if (count > 0) {
                            when {
                                readResult.mode == TestDocumentImport.ReadMode.SMART_VISION -> tr(
                                    "تم التعرف الذكي على $count بند طبي. التحاليل هتظهر بأسعارها وأي فحص غير معملي هيتوضح نوعه منفصل.",
                                    "$count medical items detected with smart vision. Lab tests will show prices and non-lab investigations will be identified separately."
                                )
                                readResult.fallbackReason == TestDocumentImport.SmartFallbackReason.OFFLINE -> tr(
                                    "تم التعرف محليا على $count بند طبي. شغّل الإنترنت لتحسين قراءة الخط اليدوي.",
                                    "$count medical items detected locally. Connect to the internet to improve handwriting recognition."
                                )
                                else -> tr(
                                    "تم التعرف محليا على $count بند طبي لأن القراءة الذكية غير متاحة حاليا. راجع النتائج.",
                                    "$count medical items detected locally because smart vision is currently unavailable. Review the results."
                                )
                            }
                        } else {
                            when {
                                readResult.fallbackReason == TestDocumentImport.SmartFallbackReason.OFFLINE -> tr(
                                    "تمت قراءة الملف محليا لكن لم أجد بنودا طبية مطابقة أو معروفة. شغّل الإنترنت وجرب القراءة الذكية.",
                                    "The file was read locally but no matching or recognized medical items were found. Connect to the internet and try smart vision."
                                )
                                else -> tr(
                                    "تمت قراءة الملف لكن لم أجد أسماء تحاليل أو فحوصات معروفة يمكن عرضها بأمان.",
                                    "The file was read, but no lab tests or safely recognized non-lab investigations were found."
                                )
                            }
                        }
                    }.onFailure { error ->
                        orderStatusSuccess = false
                        orderStatusMessage = tr(
                            "تعذر قراءة التحاليل من الملف: ${error.message ?: "الملف غير واضح"}",
                            "Unable to read tests from file: ${error.message ?: "The file is unclear"}"
                        )
                    }
                }
            }
        }
        val launchTestsImagePicker: () -> Unit = {
            // No manager/admin gate: every approved account can search from a prescription image.
            viewModel.setExternalPickerActive(true)
            testsDocumentLauncher.launch(arrayOf("image/*"))
        }

        var pendingOutputOrder by remember { mutableStateOf<CustomerOrder?>(null) }
        var pendingOutputCustomer by remember { mutableStateOf<Customer?>(null) }

        // Android system Back follows the in-app navigation stack instead of
        // dropping the user out of the current workflow/root screen.
        BackHandler(enabled = true) {
            when {
                pendingOutputOrder != null -> { pendingOutputOrder = null; pendingOutputCustomer = null }
                orderStatusMessage != null -> orderStatusMessage = null
                showOrderCheckout -> showOrderCheckout = false
                showOrderCustomerPicker -> showOrderCustomerPicker = false
                showCustomerSystem -> showCustomerSystem = false
                showScanHub -> showScanHub = false
                showOperationsV14 -> showOperationsV14 = false
                showManagerDashboard -> showManagerDashboard = false
                showAdminPasswordDialog -> { showAdminPasswordDialog = false; pendingAdminAction = null; adminUnlockError = null }
                showAdminSettingsDialog -> showAdminSettingsDialog = false
                showSettingsDialog -> showSettingsDialog = false
                managerMainMode != "home" -> {
                    managerMainMode = "home"
                    viewModel.clearSearch()
                }
                else -> Unit
            }
        }

        LaunchedEffect(currentUserEmail, currentUserUid) {
            viewModel.onAuthenticatedUserChanged(currentUserEmail, currentUserUid)
        }

        // Staff privileged surfaces close when the PIN session locks. The real manager
        // remains authorized by the signed-in manager account and never needs the PIN.
        LaunchedEffect(isManager) {
            if (!isManager) {
                showOperationsV14 = false
                showAdminSettingsDialog = false
                showManagerDashboard = false
                priceEditorTest = null
            }
        }

        // V39: when Android shares an image receipt to the app, open Customer Files.
        // The dialog consumes the URI and resolves the QR after login/unlock is complete.
        LaunchedEffect(pendingSharedQrUri) {
            if (!pendingSharedQrUri.isNullOrBlank()) {
                customerSystemAutoScan = false
                showCustomerSystem = true
            }
        }

        // V113: tapping a system order notification lands directly in Orders Hub.
        LaunchedEffect(pendingOpenOrderId) {
            if (!pendingOpenOrderId.isNullOrBlank()) {
                managerMainMode = "orders"
            }
        }

        // V116: task-first navigation with no collapsed menus or duplicate launchers.
        val openQuickImageWorkspace: () -> Unit = {
            // V115: price inquiry is a temporary multi-select basket, independent from customer orders.
            viewModel.clearSearch()
            viewModel.clearSelectedTests()
            managerMainMode = "quick_image"
        }
        val openOrdersWorkspace: () -> Unit = {
            managerMainMode = "orders"
        }
        val openOrderWorkspace: () -> Unit = {
            // V89: a new order always starts with the customer, then tests, then payment.
            viewModel.clearSelectedTests()
            activeOrderCustomer = null
            managerMainMode = "order"
            autoCheckoutAfterCustomerPick = false
            showOrderCustomerPicker = true
        }
        val openCustomersWorkspace: () -> Unit = {
            customerSystemInitialCustomer = null
            customerSystemAutoScan = false
            showCustomerSystem = true
        }
        val openCustomerQrScanner: () -> Unit = {
            customerSystemInitialCustomer = null
            customerSystemAutoScan = true
            showCustomerSystem = true
        }
        val openCatalogWorkspace: () -> Unit = {
            viewModel.clearSearch()
            managerMainMode = "catalog"
        }
        // V67: every administration action uses the same PIN gate, including the
        // signed-in manager account. Daily services stay identical for everyone.
        val requireAdmin: (() -> Unit) -> Unit = { action ->
            if (actualManager && actingAsUser != null) {
                orderStatusSuccess = false
                orderStatusMessage = tr(
                    "ارجع لحساب عبد الرحمن أولًا لفتح الإدارة",
                    "Return to Abdelrahman first to open administration"
                )
            } else if (adminUnlocked) {
                action()
            } else {
                pendingAdminAction = action
                adminPin = ""
                adminUnlockError = null
                showAdminPasswordDialog = true
            }
        }
        val openAdminPage: (String) -> Unit = { page ->
            requireAdmin {
                operationsInitialPage = page
                operationsInitialDebtsOnly = false
                operationsInitialRange = "today"
                showOperationsV14 = true
            }
        }
        val openAdminWorkspace: () -> Unit = { openAdminPage("home") }
        val openSettingsWorkspace: () -> Unit = { showSettingsDialog = true }
        val openAdminSettings: () -> Unit = { requireAdmin { showAdminSettingsDialog = true } }
        // V44: a staff-first visual shortcut. It opens the same order workspace,
        // then starts OCR immediately so the employee does not hunt for the import action.
        val openImportWorkspace: () -> Unit = {
            viewModel.clearSearch()
            managerMainMode = "order"
            launchTestsImagePicker()
        }

        // V80: keep the home screen alive as an operational dashboard, not just an icon launcher.
        LaunchedEffect(managerMainMode) {
            if (managerMainMode == "home") viewModel.loadDailyOrders()
        }

        // V65: task-first navigation. The home dashboard is only the launcher.
        // Once a task is opened, that task owns the whole screen until Back is pressed.
        if (managerMainMode == "home") {
            Scaffold(
                topBar = {
                    Column {
                        TopHeader(
                            onLogout = null,
                            showSettings = false,
                            onSettings = null,
                            showHome = false,
                            onHome = null
                        )
                        SyncStatusBar(
                            isOnline = isOnline,
                            offlineGraceAccess = offlineGraceAccess,
                            pendingCount = pendingSyncCount,
                            lastSyncMillis = lastSuccessfulSyncMillis
                        )
                    }
                },
                containerColor = if (appSettings.darkMode) Color(0xFF0B1220) else Color(0xFFF4F7FB)
            ) { paddingValues ->
                ManagerHomeDashboard(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    dailyOrders = dailyOrders,
                    dailyOrdersLoading = dailyOrdersLoading,
                    onRefreshToday = { viewModel.loadDailyOrders() },
                    onOrders = openOrdersWorkspace,
                    onCatalog = openCatalogWorkspace,
                    onQuickImage = openQuickImageWorkspace,
                    onNewOrder = openOrderWorkspace,
                    onCustomers = openCustomersWorkspace,
                    onScan = { showScanHub = true },
                    onAdmin = openAdminWorkspace,
                    onPrices = { requireAdmin { showManagerDashboard = true } },
                    onSettings = openSettingsWorkspace,
                    onLogout = onLogout,
                    onLockAdmin = { viewModel.lockAdmin() },
                    adminUnlocked = adminUnlocked,
                    actualManager = actualManager,
                    actingAsUser = actingAsUser,
                    onSwitchUser = { if (actualManager && actingAsUser == null) showUserSwitcher = true },
                    onReturnToManager = {
                        viewModel.returnToManagerMode { ok, msg ->
                            orderStatusSuccess = ok
                            orderStatusMessage = msg
                        }
                    }
                )
            }
        } else {
            val temporaryWorkspaceTitle = when (managerMainMode) {
                "orders" -> appText("سجل الطلبات", "Orders Hub")
                "worklist" -> appText("تشغيل المعمل اليوم", "Today's lab worklist")
                "catalog" -> appText("دليل التحاليل", "Test guide")
                "quick_image" -> appText("استعلام عن تحليل", "Test inquiry")
                else -> appText("تسجيل طلب جديد", "New order")
            }
            val temporaryWorkspaceIcon = when (managerMainMode) {
                "orders" -> Icons.Default.History
                "worklist" -> Icons.Default.ReceiptLong
                "catalog" -> Icons.Outlined.Biotech
                "quick_image" -> Icons.Default.Image
                else -> Icons.Default.AddCircle
            }

            Scaffold(
                topBar = {
                    Column {
                        TemporaryWorkspaceTopBar(
                            title = temporaryWorkspaceTitle,
                            icon = temporaryWorkspaceIcon,
                            onBack = {
                                managerMainMode = "home"
                                viewModel.clearSearch()
                            }
                        )
                        // Keep status out of the working area unless attention is required.
                        if (!isOnline || pendingSyncCount > 0) {
                            SyncStatusBar(
                                isOnline = isOnline,
                                offlineGraceAccess = offlineGraceAccess,
                                pendingCount = pendingSyncCount,
                                lastSyncMillis = lastSuccessfulSyncMillis
                            )
                        }
                    }
                },
                containerColor = if (appSettings.darkMode) Color(0xFF0B1220) else Color(0xFFF4F7FB)
            ) { paddingValues ->
                when (managerMainMode) {
                    "orders" -> {
                        OrderHubScreen(
                            viewModel = viewModel,
                            isManager = isManager,
                            initialOrderId = pendingOpenOrderId,
                            onInitialOrderConsumed = { viewModel.consumePendingOpenOrder() }
                        )
                    }

                    "worklist" -> {
                        DailyWorklistScreen(
                            viewModel = viewModel,
                            onOpenCustomer = { customer ->
                                customerSystemInitialCustomer = customer
                                customerSystemAutoScan = false
                                showCustomerSystem = true
                            }
                        )
                    }

                    "quick_image" -> {
                        MainContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 14.dp),
                            topContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = appText(
                                            "استعلم عن أكتر من تحليل في نفس القائمة — اختار من البحث أو النتائج وشوف الإجمالي فورًا",
                                            "Add multiple tests to one inquiry list — choose from the dropdown or results and see the total instantly"
                                        ),
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    SearchBox(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onManualQueryChanged(it) },
                                        onClear = { viewModel.clearSearch() },
                                        suggestions = viewModel.smartSearchCandidates(searchQuery),
                                        selectedIds = selectedTests.map { it.id }.toSet(),
                                        onSuggestionSelected = { test ->
                                            viewModel.addSelectedTest(test)
                                            // Keep the field focused and ready for the next test.
                                            viewModel.clearSearch()
                                        },
                                        onImportDocument = { launchTestsImagePicker() },
                                        importingDocument = importingTestsDocument
                                    )
                                }
                            },
                            uiState = uiState,
                            selectedTests = selectedTests,
                            recognizedTests = recognizedTests,
                            isManager = false,
                            lab2LabPrices = emptyMap(),
                            customerPriceOverrides = customerPriceOverrides,
                            activeOrderCustomer = null,
                            onChooseCustomer = {},
                            onClearCustomer = {},
                            onSaveCustomerOrder = {},
                            onSelectTest = { test ->
                                viewModel.addSelectedTest(test)
                            },
                            onRemoveTest = { testId -> viewModel.removeSelectedTest(testId) },
                            onAddAllRecognized = {
                                viewModel.addAllRecognizedTests()
                            },
                            onEditPrices = {},
                            onAddPriceResultsToOrder = { tests ->
                                tests.forEach { viewModel.addSelectedTest(it) }
                            },
                            allowOrderSelection = true,
                            showResolvedBatchAction = true,
                            selectedTestsContent = { tests ->
                                PriceInquiryBasketSection(
                                    selectedTests = tests,
                                    customerPriceOverrides = customerPriceOverrides,
                                    onRemoveTest = { testId -> viewModel.removeSelectedTest(testId) },
                                    onClearAll = { viewModel.clearSelectedTests() }
                                )
                            },
                            bottomContent = null
                        )
                    }

                    "catalog" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            AvailableTestsScreen(
                                viewModel = viewModel,
                                isManager = false,
                                customerPriceOverrides = customerPriceOverrides,
                                lab2LabPrices = emptyMap(),
                                onEditPrices = {},
                                bottomContent = null
                            )
                        }
                    }

                    else -> {
                        MainContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 14.dp),
                            topContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OrderWorkspaceCustomerBar(
                                        customer = activeOrderCustomer,
                                        onChooseCustomer = { showOrderCustomerPicker = true },
                                        onOpenCustomerFiles = openCustomersWorkspace,
                                        onClearCustomer = { activeOrderCustomer = null }
                                    )
                                    SearchBox(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onManualQueryChanged(it) },
                                        onClear = { viewModel.clearSearch() },
                                        suggestions = viewModel.smartSearchCandidates(searchQuery),
                                        selectedIds = selectedTests.map { it.id }.toSet(),
                                        onSuggestionSelected = { test -> viewModel.addSelectedTest(test) },
                                        onImportDocument = { launchTestsImagePicker() },
                                        importingDocument = importingTestsDocument
                                    )
                                }
                            },
                            uiState = uiState,
                            selectedTests = selectedTests,
                            recognizedTests = recognizedTests,
                            isManager = false,
                            lab2LabPrices = emptyMap(),
                            customerPriceOverrides = customerPriceOverrides,
                            activeOrderCustomer = activeOrderCustomer,
                            onChooseCustomer = { showOrderCustomerPicker = true },
                            onClearCustomer = { activeOrderCustomer = null },
                            onSaveCustomerOrder = {
                                if (activeOrderCustomer != null && selectedTests.isNotEmpty()) {
                                    showOrderCheckout = true
                                }
                            },
                            onSelectTest = { test -> viewModel.addSelectedTest(test) },
                            onRemoveTest = { testId -> viewModel.removeSelectedTest(testId) },
                            onAddAllRecognized = {
                                val added = viewModel.addAllRecognizedTests()
                                if (added > 0) {
                                    orderStatusSuccess = true
                                    orderStatusMessage = tr(
                                        "تمت إضافة $added تحليل إلى القائمة.",
                                        "$added tests were added to the list."
                                    )
                                }
                            },
                            onEditPrices = {},
                            allowOrderSelection = true,
                            bottomContent = null
                        )
                    }
                }
            }
        }

        if (showScanHub) {
            HomeScanHubDialog(
                onDismiss = { showScanHub = false },
                onCustomerQr = {
                    showScanHub = false
                    openCustomerQrScanner()
                },
                onTestsDocument = {
                    showScanHub = false
                    openImportWorkspace()
                },
                onNavigateSearch = { showScanHub = false; openCatalogWorkspace() },
                onNavigateQuickImage = { showScanHub = false; openQuickImageWorkspace() },
                onNavigateOrder = { showScanHub = false; openOrderWorkspace() },
                onNavigateCustomers = { showScanHub = false; openCustomersWorkspace() },
                onNavigateCatalog = { showScanHub = false; openCatalogWorkspace() }
            )
        }

        if (showOperationsV14 && isManager) {
            OperationsV14Dialog(
                viewModel = viewModel,
                isManager = isManager,
                initialPage = operationsInitialPage,
                initialDebtsOnly = operationsInitialDebtsOnly,
                initialRange = operationsInitialRange,
                onOpenAdminSettings = {
                    showOperationsV14 = false
                    showAdminSettingsDialog = true
                },
                onDismiss = { showOperationsV14 = false }
            )
        }

        if (showCustomerSystem) {
            CustomerSystemDialog(
                viewModel = viewModel,
                selectedTests = selectedTests,
                onDismiss = {
                    showCustomerSystem = false
                    customerSystemInitialCustomer = null
                    customerSystemAutoScan = false
                },
                onStartOrderForCustomer = { customer ->
                    activeOrderCustomer = customer
                    showCustomerSystem = false
                    managerMainMode = "order"
                            orderStatusMessage = "تم اختيار ${customer.name}. اختار التحاليل ثم اضغط متابعة الدفع."
                    orderStatusSuccess = true
                },
                initialCustomer = customerSystemInitialCustomer,
                initialAutoScan = customerSystemAutoScan,
                initialQrUri = pendingSharedQrUri,
                onInitialQrConsumed = { uri -> viewModel.consumeSharedQrUri(uri) },
                onNavigateSearch = { showCustomerSystem = false; openCatalogWorkspace() },
                onNavigateQuickImage = { showCustomerSystem = false; openQuickImageWorkspace() },
                onNavigateOrder = { showCustomerSystem = false; openOrderWorkspace() },
                onNavigateCustomers = {},
                onNavigateScan = { showCustomerSystem = false; showScanHub = true },
                onNavigateCatalog = { showCustomerSystem = false; openCatalogWorkspace() }
            )
        }

        if (showOrderCustomerPicker) {
            CustomerOrderFlowDialog(
                viewModel = viewModel,
                selectedTests = selectedTests,
                onDismiss = {
                    showOrderCustomerPicker = false
                    autoCheckoutAfterCustomerPick = false
                },
                onCustomerSelected = { customer ->
                    val shouldOpenCheckout = autoCheckoutAfterCustomerPick
                    activeOrderCustomer = customer
                    showOrderCustomerPicker = false
                    autoCheckoutAfterCustomerPick = false
                    orderStatusSuccess = true
                    orderStatusMessage = if (shouldOpenCheckout) {
                        null
                    } else {
                        "تم ربط الطلب بالعميل ${customer.name}"
                    }
                    managerMainMode = "order"
                    if (shouldOpenCheckout && selectedTests.isNotEmpty()) {
                        showOrderCheckout = true
                    }
                }
            )
        }

        val checkoutCustomer = activeOrderCustomer
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
                        orderStatusSuccess = order != null
                        orderStatusMessage = msg
                        if (order != null) {
                            // V38: the order is saved first. Document generation/sharing is optional.
                            pendingOutputOrder = order
                            pendingOutputCustomer = checkoutCustomer
                            orderStatusSuccess = true
                            orderStatusMessage = null
                            showOrderCheckout = false
                            viewModel.clearSelectedTests()
                            viewModel.loadDailyOrders()
                            // V64: keep the customer linked so the user can immediately open the file
                            // or start another action without having to find the customer again.
                        }
                    }
                }
            )
        }

        val outputOrder = pendingOutputOrder
        val outputCustomer = pendingOutputCustomer
        if (outputOrder != null && outputCustomer != null) {
            OrderImageShareDialog(
                order = outputOrder,
                customer = outputCustomer,
                onDismiss = {
                    orderStatusSuccess = true
                    orderStatusMessage = tr(
                        "تم إرسال الطلب ${outputOrder.orderNumber} للمعمل بنجاح",
                        "Order ${outputOrder.orderNumber} was sent to the lab successfully"
                    )
                    pendingOutputOrder = null
                    pendingOutputCustomer = null
                }
            )
        }

        orderStatusMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { orderStatusMessage = null },
                title = {
                    Text(
                        if (orderStatusSuccess) "تم" else "تعذر التنفيذ",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = { Text(message) },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (orderStatusSuccess && activeOrderCustomer != null) {
                            LabeledIconAction(label = appText("فتح ملف العميل", "Open customer file"), onClick = {
                                    orderStatusMessage = null
                                    customerSystemInitialCustomer = activeOrderCustomer
                                    showCustomerSystem = true
                                }) { Icon(Icons.Default.Description, contentDescription = null) }
                        }
                        LabeledIconAction(label = appText("حسنا", "OK"), onClick = { orderStatusMessage = null }) { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                    }
                }
            )
        }

        if (showAdminPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!adminUnlocking) {
                        showAdminPasswordDialog = false
                        pendingAdminAction = null
                        adminUnlockError = null
                    }
                },
                title = {
                    Text(
                        appText("دخول الإدارة", "Administration access"),
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = {
                    Column {
                        Text(
                            appText(
                                "اكتب PIN الإدارة المكون من 6 أرقام لفتح الخدمات الحساسة.",
                                "Enter the 6-digit admin PIN to unlock sensitive services."
                            ),
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = adminPin,
                            onValueChange = { adminPin = it.filter(Char::isDigit).take(6); adminUnlockError = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(appText("PIN الإدارة", "Admin PIN")) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (!adminUnlocking) {
                                    adminUnlocking = true
                                    viewModel.unlockAdmin(adminPin) { success, message ->
                                        adminUnlocking = false
                                        if (success) {
                                            showAdminPasswordDialog = false
                                            adminUnlockError = null
                                            val action = pendingAdminAction
                                            pendingAdminAction = null
                                            action?.invoke()
                                        } else {
                                            adminUnlockError = message
                                        }
                                    }
                                }
                            })
                        )
                        adminUnlockError?.let {
                            Spacer(Modifier.height(7.dp))
                            Text(it, color = Color(0xFFB42318), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    LabeledIconAction(label = appText("فتح", "Unlock"), onClick = {
                            if (!adminUnlocking) {
                                adminUnlocking = true
                                viewModel.unlockAdmin(adminPin) { success, message ->
                                    adminUnlocking = false
                                    if (success) {
                                        showAdminPasswordDialog = false
                                        adminUnlockError = null
                                        val action = pendingAdminAction
                                        pendingAdminAction = null
                                        action?.invoke()
                                    } else {
                                        adminUnlockError = message
                                    }
                                }
                            }
                        }, enabled = !adminUnlocking && adminPin.length == 6) { Icon(Icons.Default.Visibility, contentDescription = null) }
                },
                dismissButton = {
                    LabeledIconAction(label = appText("إلغاء", "Cancel"), onClick = {
                        if (!adminUnlocking) {
                            showAdminPasswordDialog = false
                            pendingAdminAction = null
                            adminUnlockError = null
                        }
                    }) { Icon(Icons.Default.Close, contentDescription = null) }
                }
            )
        }

        if (showUserSwitcher && actualManager && actingAsUser == null) {
            UserSwitcherDialog(
                viewModel = viewModel,
                onDismiss = { showUserSwitcher = false },
                onUserSelected = { profile ->
                    viewModel.switchToUser(profile) { ok, msg ->
                        orderStatusSuccess = ok
                        orderStatusMessage = msg
                        if (ok) showUserSwitcher = false
                    }
                }
            )
        }

        if (showSettingsDialog) {
            UserSettingsDialog(
                viewModel = viewModel,
                settings = appSettings,
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showAdminSettingsDialog && isManager) {
            ManagerSettingsDialog(
                viewModel = viewModel,
                settings = appSettings,
                currentUserEmail = currentUserEmail,
                currentUserUid = currentUserUid,
                onDismiss = { showAdminSettingsDialog = false },
                onOpenManagerDashboard = {
                    showAdminSettingsDialog = false
                    showManagerDashboard = true
                },
                onLogout = onLogout
            )
        }

        if (isManager && showManagerDashboard) {
            ManagerDashboardDialog(
                viewModel = viewModel,
                customerPriceOverrides = customerPriceOverrides,
                lab2LabPrices = lab2LabPrices,
                onDismiss = { showManagerDashboard = false },
                onAdd = { showAddCatalogEditor = true },
                onBulk = { showBulkCatalogEditor = true },
                onEdit = { test -> priceEditorTest = test },
                onDelete = { test -> deleteCatalogTest = test }
            )
        }

        if (isManager) {
            if (showBulkCatalogEditor) {
                BulkCatalogUpdateDialog(
                    viewModel = viewModel,
                    onDismiss = { showBulkCatalogEditor = false }
                )
            }
            priceEditorTest?.let { test ->
                CatalogTestEditorDialog(
                    test = test,
                    customerPrice = customerPriceOverrides[test.id] ?: test.customerPrice.orEmpty(),
                    lab2LabPrice = lab2LabPrices[test.id].orEmpty(),
                    onDismiss = { priceEditorTest = null },
                    onSave = { en, ar, market, search, customer, lab, done ->
                        viewModel.updateCatalogTest(test, en, ar, market, search, customer, lab) { success, message ->
                            if (success) priceEditorTest = null
                            done(success, message)
                        }
                    }
                )
            }

            if (showAddCatalogEditor) {
                CatalogTestEditorDialog(
                    test = null,
                    customerPrice = "",
                    lab2LabPrice = "",
                    onDismiss = { showAddCatalogEditor = false },
                    onSave = { en, ar, market, search, customer, lab, done ->
                        viewModel.addCatalogTest(en, ar, market, search, customer, lab) { success, message ->
                            if (success) showAddCatalogEditor = false
                            done(success, message)
                        }
                    }
                )
            }

            deleteCatalogTest?.let { test ->
                AlertDialog(
                    onDismissRequest = { deleteCatalogTest = null },
                    title = { Text(tr("حذف التحليل؟", "Delete test?"), fontWeight = FontWeight.ExtraBold) },
                    text = { Text(tr("سيتم حذف ${test.englishName.ifBlank { test.arabicName }} من دليل التحاليل والبحث.", "${test.englishName.ifBlank { test.arabicName }} will be removed from the catalogue and search.")) },
                    confirmButton = {
                        LabeledIconAction(label = tr("حذف", "Delete"), onClick = {
                                viewModel.deleteCatalogTest(test) { success, _ ->
                                    if (success) deleteCatalogTest = null
                                }
                            }) { Icon(Icons.Default.Delete, contentDescription = null) }
                    },
                    dismissButton = { LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = { deleteCatalogTest = null }) { Icon(Icons.Default.Close, contentDescription = null) } }
                )
            }
        }
    }
}

@Composable
private fun SyncStatusBar(
    isOnline: Boolean,
    offlineGraceAccess: Boolean,
    pendingCount: Int,
    lastSyncMillis: Long
) {
    val bg = when {
        !isOnline -> Color(0xFFFFF3CD)
        pendingCount > 0 -> Color(0xFFE0F2FE)
        else -> Color(0xFFE8F5E9)
    }
    val fg = when {
        !isOnline -> Color(0xFF92400E)
        pendingCount > 0 -> Color(0xFF075985)
        else -> Color(0xFF166534)
    }
    val text = when {
        !isOnline && offlineGraceAccess && pendingCount > 0 -> appText(
            "وضع الطوارئ الآمن • $pendingCount عملية محفوظة للمزامنة",
            "Protected outage mode • $pendingCount operation(s) queued"
        )
        !isOnline && offlineGraceAccess -> appText(
            "وضع الطوارئ الآمن • آخر اعتماد محفوظ على هذا الجهاز",
            "Protected outage mode • last verified access is cached on this device"
        )
        !isOnline && pendingCount > 0 -> appText(
            "Offline • $pendingCount عملية في انتظار المزامنة",
            "Offline • $pendingCount operation(s) waiting to sync"
        )
        !isOnline -> appText("Offline • سيتم الحفظ والمزامنة عند رجوع الإنترنت", "Offline • changes will sync when internet returns")
        pendingCount > 0 -> appText("جاري مزامنة $pendingCount عملية...", "Syncing $pendingCount operation(s)...")
        else -> appText("Online • تمت المزامنة", "Online • Synced")
    }
    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = fg,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TopHeader(
    onLogout: (() -> Unit)? = null,
    showSettings: Boolean = false,
    onSettings: (() -> Unit)? = null,
    showHome: Boolean = false,
    onHome: (() -> Unit)? = null
) {
    val settings = LocalAppSettings.current
    val customLogo = remember(settings.brandLogoPath) {
        settings.brandLogoPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF003A5D),
                        Color(0xFF006D86),
                        Color(0xFF008FA0)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customLogo != null) {
                            Image(
                                bitmap = customLogo,
                                contentDescription = appText("شعار المعمل", "Lab logo"),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_clinic_logo),
                                contentDescription = appText("شعار المعمل", "Lab logo"),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = settings.pdfLabName.ifBlank { appText("تحاليل العقاد", "Tahalil Alakkad") },
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = settings.brandTagline.ifBlank { appText("دليل التحاليل والأسعار", "Lab Tests & Prices") },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD9F4F4)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (showHome && onHome != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        LabeledIconAction(label = appText("الرئيسية", "Home"), onClick = onHome) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = appText("الرئيسية", "Home"),
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                if (showSettings && onSettings != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        LabeledIconAction(label = appText("إعدادات التطبيق", "App settings"), onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = appText("إعدادات التطبيق", "App settings"),
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                if (onLogout != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        LabeledIconAction(label = appText("تسجيل الخروج", "Logout"), onClick = onLogout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = appText("تسجيل الخروج", "Logout"),
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffHomeDashboard(
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onNewOrder: () -> Unit,
    onImportTests: () -> Unit,
    onCustomers: () -> Unit,
    onQr: () -> Unit,
    onAvailableTests: () -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Text(
                text = appText("كل الخدمات", "All services"),
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = appText("اختار الخدمة من الأيقونة مباشرة", "Choose a service directly from its icon"),
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlossyServiceCard(Modifier.weight(1f), R.drawable.staff_search_3d, appText("بحث عن تحليل", "Find test"), onClick = onSearch)
                GlossyServiceCard(Modifier.weight(1f), R.drawable.staff_new_order_3d, appText("طلب جديد", "New order"), onClick = onNewOrder)
                GlossyServiceCard(Modifier.weight(1f), R.drawable.staff_quick_image_3d, appText("صورة سريعة", "Quick image"), onClick = onImportTests)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlossyServiceCard(Modifier.weight(1f), R.drawable.staff_qr_3d, appText("QR العميل", "Customer QR"), onClick = onQr)
                GlossyServiceCard(Modifier.weight(1f), R.drawable.staff_customers_3d, appText("سجلات العملاء", "Customers"), onClick = onCustomers)
                GlossyServiceCard(Modifier.weight(1f), R.drawable.staff_tests_3d, appText("التحاليل المتاحة", "Available tests"), onClick = onAvailableTests)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GlossyServiceCard(Modifier.fillMaxWidth(0.32f), R.drawable.settings_3d, appText("الإعدادات", "Settings"), onClick = onSettings)
            }
        }
    }
}

@Composable
private fun GlossyServiceCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    locked: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = modifier, actionSize = if (compact) 56.dp else 62.dp) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 46.dp else 52.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun StaffFeatureIllustration(
    mainIcon: ImageVector,
    badgeIcon: ImageVector,
    accent: Color
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.08f), accent.copy(alpha = 0.18f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = mainIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(31.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(27.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun OrdersHubIconShortcut(
    title: String,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = Modifier.fillMaxWidth(), actionSize = 58.dp) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF167A8B), modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun StaffSecondaryActionCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = Modifier.fillMaxWidth(), actionSize = 62.dp) {
        Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun HomeServiceIconShortcut(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    locked: Boolean = false,
    notificationBadge: Boolean = false,
    onClick: () -> Unit
) {
LabeledIconAction(
        label = if (locked) "$title 🔒" else title,
        onClick = onClick,
        modifier = modifier,
        actionSize = 64.dp
    ) {
        Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(54.dp), contentScale = ContentScale.Fit)
    }
}

private data class ManagerHomeAction(
    val key: String,
    val title: String,
    val onClick: () -> Unit,
    val iconRes: Int? = null,
    val vectorIcon: ImageVector? = null,
    val locked: Boolean = false,
    val tint: Color = Color(0xFF167A8B),
    val actionSize: Dp = 64.dp,
    val iconSize: Dp = 54.dp,
)

private val V126_MANAGER_HOME_DEFAULT_ORDER = listOf(
    "quick_image",
    "new_order",
    "customers",
    "catalog",
    "orders",
    "scan",
    "admin",
    "prices",
    "switch_user",
    "settings",
    "logout",
)

private fun normalizeV126HomeOrder(raw: String?): List<String> {
    val known = V126_MANAGER_HOME_DEFAULT_ORDER.toSet()
    val saved = raw.orEmpty().split('|').map { it.trim() }.filter { it in known }
    return (saved + V126_MANAGER_HOME_DEFAULT_ORDER).distinct()
}

@Composable
private fun DraggableManagerHomeIcon(
    action: ManagerHomeAction,
    isRtl: Boolean,
    onDrop: (String, Offset, Boolean) -> Unit,
) {
    var dragOffset by remember(action.key) { mutableStateOf(Offset.Zero) }
    var dragging by remember(action.key) { mutableStateOf(false) }
    var suppressClick by remember(action.key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnDrop by rememberUpdatedState(onDrop)

    LabeledIconAction(
        label = if (action.locked) "${action.title} 🔒" else action.title,
        onClick = { if (!suppressClick) action.onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 10f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = if (dragging) 1.08f else 1f
                scaleY = if (dragging) 1.08f else 1f
                alpha = if (dragging) 0.92f else 1f
            }
            .pointerInput(action.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        suppressClick = true
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = Offset.Zero
                        scope.launch {
                            delay(180)
                            suppressClick = false
                        }
                    },
                    onDragEnd = {
                        val finalOffset = dragOffset
                        dragging = false
                        dragOffset = Offset.Zero
                        currentOnDrop(action.key, finalOffset, isRtl)
                        scope.launch {
                            delay(180)
                            suppressClick = false
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                )
            },
        actionSize = action.actionSize,
    ) {
        when {
            action.iconRes != null -> Image(
                painter = painterResource(action.iconRes),
                contentDescription = null,
                modifier = Modifier.size(action.iconSize),
                contentScale = ContentScale.Fit
            )
            action.vectorIcon != null -> Icon(
                imageVector = action.vectorIcon,
                contentDescription = null,
                tint = action.tint,
                modifier = Modifier.size(action.iconSize)
            )
        }
    }
}

@Composable
private fun DraggableManagerHomeGrid(
    actions: List<ManagerHomeAction>,
    onDrop: (String, Offset, Boolean) -> Unit,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val spacing = 10.dp

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            actions.forEach { action ->
                key(action.key) {
                    DraggableManagerHomeIcon(
                        action = action,
                        isRtl = isRtl,
                        onDrop = onDrop,
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val columns = 3
        val spacingPx = spacing.roundToPx()
        val availableWidth = (constraints.maxWidth - spacingPx * (columns - 1)).coerceAtLeast(columns)
        val cellWidth = (availableWidth / columns).coerceAtLeast(1)
        val placeables = measurables.map { measurable ->
            measurable.measure(
                constraints.copy(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                    minHeight = 0,
                )
            )
        }
        val rows = (placeables.size + columns - 1) / columns
        val rowHeights = IntArray(rows)
        placeables.forEachIndexed { index, placeable ->
            val row = index / columns
            if (placeable.height > rowHeights[row]) rowHeights[row] = placeable.height
        }
        val totalHeight = rowHeights.sum() + spacingPx * (rows - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            rowHeights.forEachIndexed { row, rowHeight ->
                repeat(columns) { column ->
                    val index = row * columns + column
                    if (index < placeables.size) {
                        placeables[index].placeRelative(
                            x = column * (cellWidth + spacingPx),
                            y = y,
                        )
                    }
                }
                y += rowHeight + spacingPx
            }
        }
    }
}

@Composable
private fun ManagerHomeDashboard(
    modifier: Modifier = Modifier,
    dailyOrders: List<CustomerOrder>,
    dailyOrdersLoading: Boolean,
    onRefreshToday: () -> Unit,
    onOrders: () -> Unit,
    onCatalog: () -> Unit,
    onQuickImage: () -> Unit,
    onNewOrder: () -> Unit,
    onCustomers: () -> Unit,
    onScan: () -> Unit,
    onAdmin: () -> Unit,
    onPrices: () -> Unit,
    onSettings: () -> Unit,
    onLogout: (() -> Unit)?,
    onLockAdmin: () -> Unit,
    adminUnlocked: Boolean,
    actualManager: Boolean,
    actingAsUser: AppUserProfile?,
    onSwitchUser: () -> Unit,
    onReturnToManager: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("v126_manager_home_icon_order", Context.MODE_PRIVATE)
    }
    var homeOrder by remember {
        mutableStateOf(normalizeV126HomeOrder(prefs.getString("order", null)))
    }
    val density = LocalDensity.current
    val horizontalStepPx = with(density) { 104.dp.toPx() }
    val verticalStepPx = with(density) { 86.dp.toPx() }

    val availableActions = mutableListOf<ManagerHomeAction>().apply {
        add(ManagerHomeAction("quick_image", appText("استعلام تحاليل", "Test inquiry"), onQuickImage, iconRes = R.drawable.staff_search_3d))
        add(ManagerHomeAction("new_order", appText("طلب جديد", "New order"), onNewOrder, iconRes = R.drawable.staff_new_order_3d))
        add(ManagerHomeAction("customers", appText("العملاء", "Customers"), onCustomers, iconRes = R.drawable.staff_customers_3d))
        add(ManagerHomeAction("catalog", appText("دليل التحاليل", "Test guide"), onCatalog, iconRes = R.drawable.staff_tests_3d))
        add(ManagerHomeAction("orders", appText("الطلبات", "Orders"), onOrders, iconRes = R.drawable.mgr_reports_3d))
        add(ManagerHomeAction("scan", appText("قراءة روشتة / طلب عيادة", "Prescription / clinic request"), onScan, iconRes = R.drawable.staff_qr_3d))
        if (actingAsUser == null) {
            add(ManagerHomeAction("admin", appText("الإدارة", "Admin"), onAdmin, iconRes = R.drawable.mgr_health_3d, locked = !adminUnlocked))
            add(ManagerHomeAction("prices", appText("الأسعار Lab 2 Lab", "Lab 2 Lab prices"), onPrices, iconRes = R.drawable.mgr_reports_3d, locked = !adminUnlocked))
        }
        if (actualManager && actingAsUser == null) {
            add(ManagerHomeAction("switch_user", appText("تبديل المستخدم", "Switch user"), onSwitchUser, iconRes = R.drawable.mgr_switch_3d))
        }
        add(
            ManagerHomeAction(
                key = "settings",
                title = appText("الإعدادات", "Settings"),
                onClick = onSettings,
                vectorIcon = Icons.Default.Settings,
                tint = Color(0xFF006D86),
                actionSize = 58.dp,
                iconSize = 40.dp,
            )
        )
        if (onLogout != null) {
            add(
                ManagerHomeAction(
                    key = "logout",
                    title = appText("تسجيل الخروج", "Logout"),
                    onClick = onLogout,
                    vectorIcon = Icons.AutoMirrored.Filled.Logout,
                    tint = Color(0xFFB91C1C),
                    actionSize = 58.dp,
                    iconSize = 40.dp,
                )
            )
        }
    }
    val byKey = availableActions.associateBy { it.key }
    val orderedActions = (homeOrder.mapNotNull { byKey[it] } + availableActions.filter { it.key !in homeOrder })
        .distinctBy { it.key }

    fun persistOrder(next: List<String>) {
        homeOrder = next
        prefs.edit().putString("order", next.joinToString("|")).apply()
    }

    fun moveDroppedIcon(key: String, offset: Offset, isRtl: Boolean) {
        if (abs(offset.x) < horizontalStepPx * 0.42f && abs(offset.y) < verticalStepPx * 0.42f) return
        val visibleKeys = orderedActions.map { it.key }
        val from = visibleKeys.indexOf(key)
        if (from < 0) return
        var horizontal = (offset.x / horizontalStepPx).roundToInt()
        val vertical = (offset.y / verticalStepPx).roundToInt()
        if (isRtl) horizontal = -horizontal
        val target = (from + vertical * 3 + horizontal).coerceIn(0, visibleKeys.lastIndex)
        if (target == from) return
        val targetKey = visibleKeys[target]
        val next = homeOrder.toMutableList()
        val fromGlobal = next.indexOf(key)
        val targetGlobal = next.indexOf(targetKey)
        if (fromGlobal < 0 || targetGlobal < 0) return
        next.removeAt(fromGlobal)
        val adjustedTarget = next.indexOf(targetKey).let { if (target > from) it + 1 else it }.coerceIn(0, next.size)
        next.add(adjustedTarget, key)
        persistOrder(next)
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 30.dp)
    ) {
        if (actingAsUser != null) {
            item {
                ActingAsUserBanner(
                    profile = actingAsUser,
                    onReturnToManager = onReturnToManager
                )
            }
        }

        item {
            Text(
                text = appText("الخدمات", "Services"),
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = appText("اضغط مطولاً على أي أيقونة واسحبها لتغيير ترتيبها", "Long-press any icon and drag it to change its position"),
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }

        item {
            DraggableManagerHomeGrid(
                actions = orderedActions,
                onDrop = ::moveDroppedIcon,
            )
        }

        if (adminUnlocked) {
            item {
                LabeledIconAction(label = appText("قفل صلاحيات الإدارة", "Lock administration"), onClick = onLockAdmin, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun LabHomeStat(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = accent.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        }
    }
}

@Composable
private fun BottomUtilityCard(
    modifier: Modifier = Modifier,
    imageRes: Int,
    title: String,
    locked: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
LabeledIconAction(
        label = if (locked) "$title 🔒" else title,
        onClick = onClick,
        modifier = modifier,
        actionSize = 62.dp
    ) {
        Image(painter = painterResource(imageRes), contentDescription = null, modifier = Modifier.size(52.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun HomeScanHubDialog(
    onDismiss: () -> Unit,
    onCustomerQr: () -> Unit,
    onTestsDocument: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateQuickImage: () -> Unit,
    onNavigateOrder: () -> Unit,
    onNavigateCustomers: () -> Unit,
    onNavigateCatalog: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                appText("QR / صورة", "QR / Image"),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DailyServicesNavBar(
                    active = "scan",
                    onCatalog = onNavigateCatalog,
                    onQuickImage = onNavigateQuickImage,
                    onOrder = onNavigateOrder,
                    onCustomers = onNavigateCustomers,
                    onScan = {}
                )
                Text(
                    appText("اختار نوع القراءة المطلوبة", "Choose what you want to read"),
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
                LabeledIconAction(label = appText("مسح QR العميل", "Scan customer QR"), onClick = onCustomerQr, modifier = Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                LabeledIconAction(label = appText("قراءة روشتة / طلب عيادة", "Read prescription / clinic request"), onClick = onTestsDocument, modifier = Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
            }
        },
        confirmButton = {},
        dismissButton = {
            LabeledIconAction(label = appText("إلغاء", "Cancel"), onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
        }
    )
}


@Composable
private fun TemporaryWorkspaceTopBar(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabeledIconAction(label = appText("رجوع", "Back"), onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = appText("رجوع", "Back"),
                    tint = Color(0xFF17324D),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE7F5F7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF007E89),
                    modifier = Modifier.size(21.dp)
                )
            }

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

        }
    }
}

@Composable
private fun FocusedWorkspaceHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF17324D)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun OrderWorkspaceCustomerBar(
    customer: Customer?,
    onChooseCustomer: () -> Unit,
    onOpenCustomerFiles: () -> Unit,
    onClearCustomer: () -> Unit
) {
    val today = android.text.format.DateFormat.format("dd/MM/yyyy", System.currentTimeMillis()).toString()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3EAF2))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = appText("بيانات العميل", "Customer details"),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color(0xFF17324D)
            )

            if (customer == null) {
                Text(
                    text = appText("ابدأ باسم العميل والسن والنوع، ثم أضف التحاليل المطلوبة.", "Start with name, age and gender, then add the requested tests."),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF4FAFB)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(customer.name, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), fontSize = 15.sp)
                        Text(
                            text = appText("السن: ${customer.age.ifBlank { "-" }} • النوع: ${customer.gender.ifBlank { "-" }}", "Age: ${customer.age.ifBlank { "-" }} • Gender: ${customer.gender.ifBlank { "-" }}"),
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = appText("التاريخ: $today", "Date: $today"),
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                        if (customer.phone.isNotBlank()) {
                            Text(
                                text = appText("الموبايل: ${customer.phone}", "Mobile: ${customer.phone}"),
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            LabeledIconAction(label = if (customer == null) appText("إدخال / اختيار بيانات العميل", "Enter / choose customer") else appText("تغيير بيانات العميل", "Change customer"), onClick = onChooseCustomer, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.People, contentDescription = null) }
        }
    }
}

@Composable
private fun WorkspaceMoreSection(
    currentMode: String,
    showManagerActions: Boolean,
    onOrder: () -> Unit,
    onPrice: () -> Unit,
    onCustomers: () -> Unit,
    onCatalog: () -> Unit,
    onSettings: () -> Unit,
    onAdmin: () -> Unit
) {
    var expanded by remember(currentMode) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            LabeledIconAction(label = appText("المزيد", "More"), onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Tune, contentDescription = null) }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (currentMode != "order") {
                        WorkspaceMoreButton(Icons.Default.AddCircle, appText("طلب جديد", "New order"), onOrder)
                    }
                    if (currentMode != "price_search") {
                        WorkspaceMoreButton(Icons.Default.Search, appText("استعلام أسعار التحاليل", "Test price inquiry"), onPrice)
                    }
                    WorkspaceMoreButton(Icons.Default.People, appText("سجلات العملاء والـ QR", "Customer records & QR"), onClick = onCustomers)
                    if (currentMode != "catalog") {
                        WorkspaceMoreButton(Icons.Outlined.Biotech, appText("التحاليل المتاحة", "Available tests"), onCatalog)
                    }
                    WorkspaceMoreButton(Icons.Default.Settings, appText("الإعدادات", "Settings"), onClick = onSettings)

                    if (showManagerActions) {
                        WorkspaceMoreButton(Icons.Default.AdminPanelSettings, appText("خدمات الإدارة 🔒", "Administration 🔒"), onAdmin)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceMoreButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = Color(0xFF167A8B))
    }
}

@Composable
private fun AvailableTestsScreen(
    viewModel: LabTestsViewModel,
    isManager: Boolean,
    customerPriceOverrides: Map<Int, String>,
    lab2LabPrices: Map<Int, String>,
    onEditPrices: (LabTest) -> Unit,
    bottomContent: (@Composable () -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val tests = remember(query) { viewModel.searchManagerTests(query) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = appText("دليل التحاليل", "Test guide"),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF17324D)
        )
        Text(
            text = appText(
                "السعر • معلومات التحليل • نوع العينة • لون التيوب • التحضير",
                "Price • test information • specimen • tube color • preparation"
            ),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = Color(0xFF64748B)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(appText("ابحث باسم التحليل", "Search test name")) },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = appText("عدد التحاليل: ${tests.size}", "Tests: ${tests.size}"),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569)
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(tests, key = { it.id }) { test ->
                AvailableTestPriceCard(
                    test = test,
                    customerPrice = customerPriceOverrides[test.id] ?: test.customerPrice,
                    lab2LabPrice = lab2LabPrices[test.id],
                    isManager = isManager,
                    onEditPrices = { onEditPrices(test) }
                )
            }

            if (bottomContent != null) {
                item(key = "catalog_workspace_more") {
                    Spacer(Modifier.height(8.dp))
                    bottomContent()
                }
            }
        }
    }
}

@Composable
private fun AvailableTestPriceCard(
    test: LabTest,
    customerPrice: String?,
    lab2LabPrice: String?,
    isManager: Boolean,
    onEditPrices: () -> Unit
) {
    var expanded by remember(test.id) { mutableStateOf(false) }
    val guide = remember(test.id) { labTestGuideFor(test) }
    val tubeColor = remember(guide.tubeVisualKey) {
        when (guide.tubeVisualKey) {
            "lavender" -> Color(0xFF9B7BEA)
            "light_blue" -> Color(0xFF77D4F6)
            "black" -> Color(0xFF202733)
            "gray" -> Color(0xFF9AA4AE)
            "green" -> Color(0xFF45C879)
            "gold" -> Color(0xFFE7B83D)
            "culture" -> Color(0xFF33B794)
            "urine" -> Color(0xFFFFD85A)
            "stool" -> Color(0xFFA9825E)
            else -> Color(0xFFADB7C0)
        }
    }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (expanded) 8.dp else 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0x16000000)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (expanded) Color(0xFF65BCC7) else Color(0xFFE3EAF0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // V132: collapsed row is the analysis itself. One tap opens the full data list;
            // a second tap on the same card closes it again.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (expanded) Color(0xFFDDF5F7) else Color(0xFFEAF4F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Biotech,
                        contentDescription = null,
                        tint = Color(0xFF007E89),
                        modifier = Modifier.size(25.dp)
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        test.englishName,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF17324D),
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (test.arabicName.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            test.arabicName,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            maxLines = if (expanded) 4 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (test.marketName.isNotBlank() && test.marketName != test.arabicName && test.marketName != test.englishName) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            appText("الاسم الدارج: ${test.marketName}", "Market name: ${test.marketName}"),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = Color(0xFF64748B),
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (expanded) "▲" else "▼",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF007E89)
                    )
                    Text(
                        if (expanded) appText("إخفاء", "Hide") else appText("التفاصيل", "Details"),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF64748B)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFE3EAF0))

                    // Price must be the first, largest and clearest value after opening the analysis.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        color = Color(0xFFEDF7FF),
                        border = BorderStroke(1.dp, Color(0xFFC7E4F5))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).background(Color(0xFFD9EEFB), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF006D86), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(appText("سعر العميل", "Customer price"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF28607E))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        customerPrice?.takeIf { it.isNotBlank() } ?: "—",
                                        fontSize = 34.sp,
                                        lineHeight = 38.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0B4F6C)
                                    )
                                    if (!customerPrice.isNullOrBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr("جنيه", "EGP"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF28607E), modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Directly under the price: tube colour and result duration.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        V132GuideMetric(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Biotech,
                            title = appText("لون الأنبوبة", "Tube color"),
                            value = guide.tubeColorName,
                            accent = tubeColor,
                            secondary = guide.tubeName,
                            showColorDot = true
                        )
                        V132GuideMetric(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Schedule,
                            title = appText("مدة النتيجة", "Result time"),
                            value = guide.turnaround,
                            accent = Color(0xFF007E89)
                        )
                    }

                    // Remaining information mirrors the detailed analysis guide in a compact list.
                    V132GuideDetail(Icons.Outlined.Biotech, appText("نوع العينة", "Specimen"), guide.specimen)
                    V132GuideDetail(Icons.Default.ReceiptLong, appText("اسم الأنبوبة", "Tube"), guide.tubeName)
                    V132GuideDetail(Icons.Default.CheckCircle, appText("درجة الاعتماد", "Confidence"), guide.tubeConfidence)
                    V132GuideDetail(Icons.Default.Assessment, appText("عن التحليل", "About the test"), guide.overview)
                    V132GuideDetail(Icons.Default.Search, appText("متى ولماذا؟", "When & why"), guide.whyWhen)
                    V132GuideDetail(Icons.Default.Tune, appText("التحضير", "Preparation"), guide.preparation)
                    if (guide.specialNote.isNotBlank()) {
                        V132GuideDetail(Icons.Default.NotificationsActive, appText("ملاحظة مهمة", "Important note"), guide.specialNote, Color(0xFFB26A00))
                    }
                    V132GuideDetail(Icons.Default.ReceiptLong, appText("المصدر الطبي", "Medical source"), guide.medicalSourceName)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TestGuideMoreButton(
                            test = test,
                            modifier = Modifier.weight(1f).testTag("catalog_test_guide_${test.id}")
                        )
                        if (isManager) {
                            LabeledIconAction(
                                label = appText("تعديل السعر", "Edit price"),
                                onClick = onEditPrices,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF006D86))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V132GuideMetric(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color,
    secondary: String? = null,
    showColorDot: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = accent.copy(alpha = .08f),
        border = BorderStroke(1.dp, accent.copy(alpha = .24f))
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                if (showColorDot) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(12.dp).background(accent, CircleShape))
                }
            }
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF64748B))
            Text(value.ifBlank { "—" }, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (!secondary.isNullOrBlank()) {
                Text(secondary, fontSize = 9.sp, lineHeight = 13.sp, color = Color(0xFF64748B), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun V132GuideDetail(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color = Color(0xFF007E89)
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE7ECF2))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                Spacer(Modifier.height(2.dp))
                Text(value.ifBlank { "—" }, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF334155))
            }
        }
    }
}

@Composable
private fun ActingAsUserBanner(
    profile: AppUserProfile,
    onReturnToManager: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFFEDD5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "تعمل الآن بصلاحيات ${profile.displayName.ifBlank { profile.email }}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color(0xFF9A3412),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "الحساب الفعلي: Abdelrahman",
                    fontSize = 9.sp,
                    color = Color(0xFF7C2D12)
                )
            }
            LabeledIconAction(label = appText("رجوع لـ Abdelrahman", "Back to Abdelrahman"), onClick = onReturnToManager) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
        }
    }
}

@Composable
private fun UserSwitcherDialog(
    viewModel: LabTestsViewModel,
    onDismiss: () -> Unit,
    onUserSelected: (AppUserProfile) -> Unit
) {
    val users by viewModel.users.collectAsState()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadUsers { success, result ->
            loading = false
            if (!success) message = result
        }
    }

    val filtered = remember(users, query) {
        val q = query.trim().lowercase()
        users.filter {
            it.enabled &&
                !LabTestsViewModel.isManagerAccount(it.email, it.uid) &&
                (q.isBlank() || it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).height(560.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF8FAFC),
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(appText("تبديل المستخدم", "Switch user"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                        Text(appText("اختر مستخدمًا لتجربة واستخدام صلاحياته", "Choose a user to operate with their permissions"), fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = tr("إغلاق", "Close"))
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(appText("ابحث بالاسم أو البريد", "Search by name or email")) },
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(10.dp))

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    message != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(message.orEmpty(), color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                    }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(appText("لا يوجد مستخدمون نشطون", "No active users"), color = Color(0xFF64748B))
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filtered, key = { it.uid }) { profile ->
                            Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5EAF0))
    ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE7F5F7)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF006D86))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(profile.displayName.ifBlank { profile.email }, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                                        Text(profile.email, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    
            LabeledIconAction(label = "فتح", onClick = { onUserSelected(profile) }) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
                                    Text(appText(tr("دخول", "Sign In"), "Open"), color = Color(0xFF006D86), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerStatChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE5EAF0))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color(0xFF17324D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ManagerHomeActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = accent)
    }
}

@Composable
private fun ManagerActionIcon(
    icon: ImageVector,
    accent: Color
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
private fun ManagerDashboardCard(onClick: () -> Unit) {
LabeledIconAction(
        label = tr("لوحة تحكم المدير", "Manager Dashboard"),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) { Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF6D28D9)) }
}

@Composable
private fun ManagerDashboardDialog(
    viewModel: LabTestsViewModel,
    customerPriceOverrides: Map<Int, String>,
    lab2LabPrices: Map<Int, String>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onBulk: () -> Unit,
    onEdit: (LabTest) -> Unit,
    onDelete: (LabTest) -> Unit
) {
    var managerQuery by remember { mutableStateOf("") }
    val catalogRevision by viewModel.catalogRevision.collectAsState()
    val tests = remember(managerQuery, catalogRevision) { viewModel.searchManagerTests(managerQuery) }

    BackHandler(enabled = true) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF7F8FC),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("لوحة تحكم المدير", "Manager Dashboard"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF3B176B)
                        )
                        Text(
                            text = tr("إدارة سعر العميل و Lab 2 Lab", "Manage Customer & Lab2Lab Prices"),
                            fontSize = 12.sp,
                            color = Color(0xFF6B5A8E)
                        )
                    }
                    LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("إغلاق", "Close"),
                            tint = Color(0xFF4C1D95)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LabeledIconAction(label = tr("إضافة تحليل جديد", "Add new test"), onClick = onAdd, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.AddCircle, contentDescription = null) }

                Spacer(modifier = Modifier.height(8.dp))

                LabeledIconAction(label = tr("تعديل جماعي للأسعار", "Bulk price update"), onClick = onBulk, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.Edit, contentDescription = null) }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = managerQuery,
                    onValueChange = { managerQuery = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    placeholder = { Text(appText("ابحث باسم التحليل عربي أو إنجليزي", "Search test by Arabic or English name")) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6D28D9),
                        cursorColor = Color(0xFF6D28D9),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("التحاليل: ${tests.size}", "Tests: ${tests.size}"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = tr("الأسعار المعدلة: ${customerPriceOverrides.size}", "Modified prices: ${customerPriceOverrides.size}"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D28D9)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(tests, key = { it.id }) { test ->
                        val customer = customerPriceOverrides[test.id] ?: test.customerPrice ?: "—"
                        val lab = lab2LabPrices[test.id] ?: "—"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE7E8EF))
                        ) {
                            Column(modifier = Modifier.padding(13.dp)) {
                                Text(
                                    text = test.englishName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF17324D),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (test.arabicName.isNotBlank()) {
                                    Text(
                                        text = test.arabicName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF64748B),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(9.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "سعر العميل: $customer ج",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF007C88)
                                        )
                                        Text(
                                            text = "Lab 2 Lab: $lab ج",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6D28D9)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        LabeledIconAction(label = tr("حذف", "Delete"), onClick = { onDelete(test) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = tr("حذف", "Delete"),
                                                tint = Color(0xFFB42318)
                                            )
                                        }
                                        LabeledIconAction(label = appText(tr("تعديل", "Edit"), "Edit"), onClick = { onEdit(test) }) { Icon(Icons.Default.Edit, contentDescription = null) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkCatalogUpdateDialog(
    viewModel: LabTestsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val preview = remember(text) { viewModel.previewBulkCatalogUpdate(text) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importing = true
            message = tr("جاري قراءة الصورة أو PDF...", "Reading image or PDF...")
            scope.launch {
                val result = TestDocumentImport.readTests(context, uri)
                importing = false
                result.onSuccess { readResult ->
                    text = readResult.text
                    val count = viewModel.previewBulkCatalogUpdate(readResult.text).matched.size
                    message = if (count > 0) {
                        tr(
                            "تم استخراج النص من الملف • تم التعرف على $count تحليل بأسعار صالحة. راجع النتائج ثم اضغط تطبيق الكل.",
                            "Text extracted • $count tests with valid prices matched. Review then tap Apply all."
                        )
                    } else {
                        tr(
                            "تمت قراءة الملف لكن لم أجد سطورًا بصيغة اسم تحليل + سعر. يمكنك تعديل النص المستخرج يدويًا قبل التطبيق.",
                            "The file was read, but no test-name + price rows were found. You can edit the extracted text before applying."
                        )
                    }
                }.onFailure { error ->
                    message = tr(
                        "تعذر قراءة الملف: ${error.message ?: "الصورة أو PDF غير واضح"}",
                        "Unable to read file: ${error.message ?: "Image/PDF is unclear"}"
                    )
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!importing) onDismiss() }) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(tr("تعديل جماعي للأسعار", "Bulk price update"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
                        "الصق القائمة أو ارفع صورة / PDF فيه أسماء التحاليل والأسعار. مثال: CBC | 250",
                        "Paste the list or upload an image/PDF containing test names and prices. Example: CBC | 250"
                    ),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(Modifier.height(12.dp))

                LabeledIconAction(label = if (importing) tr("جاري القراءة...", "Reading...")
                        else tr("رفع صورة أو PDF للأسعار", "Upload image or PDF prices"), onClick = {
                        message = ""
                        documentLauncher.launch(arrayOf("image/*", "application/pdf"))
                    }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !importing) { Icon(Icons.Default.CloudUpload, contentDescription = null) }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; message = "" },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    placeholder = { Text("CBC | 250\nTSH | 400\nVitamin D | 850") },
                    label = { Text(tr("النص المستخرج / القائمة", "Extracted text / list")) }
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    tr(
                        "مطابق: ${preview.matched.size} • غير مطابق: ${preview.notFound.size}",
                        "Matched: ${preview.matched.size} • unmatched: ${preview.notFound.size}"
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4C1D95)
                )

                if (preview.matched.isNotEmpty()) {
                    val sample = preview.matched.take(4).joinToString(" • ") { (test, price) ->
                        "${test.englishName.ifBlank { test.arabicName }} → $price"
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sample,
                        fontSize = 11.sp,
                        color = Color(0xFF087F5B),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !importing) { Icon(Icons.Default.Close, contentDescription = null) }

                    LabeledIconAction(label = tr("تطبيق الكل", "Apply all"), onClick = {
                            viewModel.applyBulkCatalogUpdate(text) { success, msg ->
                                message = msg
                                if (success) text = ""
                            }
                        }, modifier = Modifier.weight(1f), enabled = preview.matched.isNotEmpty() && !importing) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
private fun CatalogTestEditorDialog(
    test: LabTest?,
    customerPrice: String,
    lab2LabPrice: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, (Boolean, String) -> Unit) -> Unit
) {
    val key = test?.id ?: -1
    var englishText by remember(key) { mutableStateOf(test?.englishName.orEmpty()) }
    var arabicText by remember(key) { mutableStateOf(test?.arabicName.orEmpty()) }
    var marketText by remember(key) { mutableStateOf(test?.marketName.orEmpty()) }
    var searchText by remember(key) { mutableStateOf(test?.searchText.orEmpty()) }
    var customerText by remember(key, customerPrice) { mutableStateOf(customerPrice) }
    var labText by remember(key, lab2LabPrice) { mutableStateOf(lab2LabPrice) }
    var saving by remember(key) { mutableStateOf(false) }
    var message by remember(key) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = if (test == null) tr("إضافة تحليل جديد", "Add New Test") else tr("تعديل التحليل", "Edit Test"),
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF102A43)
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = englishText, onValueChange = { englishText = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("الاسم الإنجليزي", "English name")) }, singleLine = true, enabled = !saving
                    )
                }
                item {
                    OutlinedTextField(
                        value = arabicText, onValueChange = { arabicText = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("الاسم العربي", "Arabic name")) }, singleLine = true, enabled = !saving
                    )
                }
                item {
                    OutlinedTextField(
                        value = marketText, onValueChange = { marketText = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("اسم السوق / الاسم الشائع", "Market / common name")) }, singleLine = true, enabled = !saving
                    )
                }
                item {
                    OutlinedTextField(
                        value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("كلمات إضافية للبحث", "Extra search keywords")) }, enabled = !saving,
                        minLines = 2, maxLines = 3
                    )
                }
                item {
                    OutlinedTextField(
                        value = customerText,
                        onValueChange = { customerText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.fillMaxWidth(), label = { Text(tr("سعر العميل", "Customer price")) },
                        suffix = { Text(tr("جنيه", "EGP")) }, singleLine = true, enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                item {
                    OutlinedTextField(
                        value = labText,
                        onValueChange = { labText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Lab 2 Lab") },
                        suffix = { Text(tr("جنيه", "EGP")) }, singleLine = true, enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                message?.let { msg ->
                    item { Text(msg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB42318)) }
                }
            }
        },
        confirmButton = {
            LabeledIconAction(label = if (test == null) tr("إضافة وحفظ", "Add & Save") else tr("حفظ التعديلات", "Save Changes"), onClick = {
                    saving = true
                    message = null
                    onSave(englishText, arabicText, marketText, searchText, customerText, labText) { success, resultMessage ->
                        saving = false
                        if (!success) message = resultMessage
                    }
                }, enabled = !saving && (englishText.isNotBlank() || arabicText.isNotBlank() || marketText.isNotBlank()) && customerText.isNotBlank() && labText.isNotBlank()) { Icon(Icons.Default.AddCircle, contentDescription = null) }
        },
        dismissButton = { LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, contentDescription = null) } },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    suggestions: List<LabTest>,
    selectedIds: Set<Int>,
    onSuggestionSelected: (LabTest) -> Unit,
    onImportDocument: (() -> Unit)? = null,
    importingDocument: Boolean = false
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val trimmed = query.trim()
    val naturalPhrase = trimmed.split(Regex("\\s+")).size >= 3
    val dropdownTitle = when {
        trimmed.length < 2 -> tr("كل التحاليل — اكتب حرفين للفلترة", "All tests — type two characters to filter")
        naturalPhrase -> tr("اقتراحات مرتبطة بما كتبت", "Suggestions related to what you typed")
        else -> tr("التحاليل المطابقة", "Matching tests")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = true,
            readOnly = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .onFocusChanged { focused = it.isFocused }
                .testTag("search_text_field"),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF007B87))
            },
            placeholder = {
                Text(
                    text = appText(
                        "ابحث عن تحليل أو اكتب ما تريد الاطمئنان عليه",
                        "Search a test or type what you want to check"
                    ),
                    color = Color(0xFF7C8A99),
                    fontSize = 13.sp
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onImportDocument != null) {
                        LabeledIconAction(label = tr("قراءة التحاليل من صورة", "Read tests from image"), onClick = onImportDocument, enabled = !importingDocument) {
                            if (importingDocument) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF007B87))
                            } else {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = tr("قراءة التحاليل من صورة", "Read tests from image"), tint = Color(0xFF007B87))
                            }
                        }
                    }
                    if (query.isNotEmpty()) {
                        LabeledIconAction(label = appText("مسح البحث", "Clear search"), onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = appText("مسح البحث", "Clear search"), tint = Color(0xFF64748B))
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF008B95),
                unfocusedBorderColor = Color(0xFFDCE5EE),
                focusedTextColor = Color(0xFF102A43),
                unfocusedTextColor = Color(0xFF102A43)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        if (focused && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE6EF))
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dropdownTitle, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF47657A))
                        Text("${suggestions.size}", fontSize = 10.sp, color = Color(0xFF718096))
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(suggestions, key = { it.id }) { test ->
                            val selected = test.id in selectedIds
                            Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
                                    containerColor = if (selected) Color(0xFFEAF7F2) else Color(0xFFF8FAFC)
                                ),
        border = BorderStroke(1.dp, if (selected) Color(0xFF9ED9C2) else Color(0xFFE5EBF0))
    ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(test.englishName, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (test.arabicName.isNotBlank()) {
                                            Text(test.arabicName, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (test.marketName.isNotBlank() && test.marketName != test.englishName) {
                                            Text(test.marketName, fontSize = 9.sp, color = Color(0xFF8A97A5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    
            LabeledIconAction(label = "فتح", onClick = { if (!selected) onSuggestionSelected(test) }, enabled = !selected) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        test.customerPrice?.takeIf { it.isNotBlank() }?.let { price ->
                                            Text("$price ${appText("ج", "EGP")}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF087F5B))
                                        }
                                        if (selected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF087F5B), modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCountChip(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFFE1F1FF))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$count نتائج",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0061A4)
            )
        }
    }
}

private fun calculatePriceTotal(tests: List<LabTest>, priceSelector: (LabTest) -> String?): Double {
    var total = 0.0
    val regex = Regex("""\d+(\.\d+)?""")
    for (test in tests) {
        val priceStr = priceSelector(test)
        if (!priceStr.isNullOrBlank()) {
            val match = regex.find(priceStr)
            val valDouble = match?.value?.toDoubleOrNull()
            if (valDouble != null) {
                total += valDouble
            }
        }
    }
    return total
}

private fun formatTotalDisplay(total: Double): String {
    return if (total % 1.0 == 0.0) {
        total.toLong().toString()
    } else {
        String.format("%.2f", total)
    }
}


@Composable
private fun DetectedBatchTotalCard(
    tests: List<LabTest>,
    customerPriceOverrides: Map<Int, String>,
    requestedCount: Int,
    unmatchedCount: Int
) {
    val total = calculatePriceTotal(tests) { test ->
        customerPriceOverrides[test.id] ?: test.customerPrice
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFE9F8F7),
        border = BorderStroke(1.dp, Color(0xFFB9E2DF))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("إجمالي التحاليل المطابقة", "Matched tests total"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF356B68)
                    )
                    Text(
                        text = tr(
                            "${tests.size} من $requestedCount بند",
                            "${tests.size} of $requestedCount items"
                        ),
                        fontSize = 12.sp,
                        color = Color(0xFF527A77)
                    )
                }
                Text(
                    text = "${formatTotalDisplay(total)} ${tr("جنيه", "EGP")}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF006B67)
                )
            }
            if (unmatchedCount > 0) {
                Text(
                    text = tr(
                        "الإجمالي لا يشمل $unmatchedCount بند غير مطابق أو غير معملي.",
                        "Total excludes $unmatchedCount unmatched or non-lab item(s)."
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFF9A3412)
                )
            }
        }
    }
}

@Composable
private fun PriceInquirySummaryCard(
    tests: List<LabTest>,
    customerPriceOverrides: Map<Int, String>,
    requestedCount: Int,
    unmatchedCount: Int,
    onAddAllToOrder: (List<LabTest>) -> Unit,
    onStartCustomer: (List<LabTest>) -> Unit,
    onStartOutput: (List<LabTest>) -> Unit
) {
    val total = calculatePriceTotal(tests) { test ->
        customerPriceOverrides[test.id] ?: test.customerPrice
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFE9F8F7),
        border = BorderStroke(1.dp, Color(0xFFB9E2DF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = tr("إجمالي التحاليل", "Tests total"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF356B68)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tr(
                            "${tests.size} من $requestedCount تحليل",
                            "${tests.size} of $requestedCount tests"
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (unmatchedCount == 0) Color(0xFF527A77) else Color(0xFF9A3412)
                    )
                }
                Text(
                    text = "${formatTotalDisplay(total)} ${tr("جنيه", "EGP")}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF006B67)
                )
            }

            HorizontalDivider(color = Color(0xFFB9E2DF))

            LabeledIconAction(label = tr("إضافة الكل للقائمة (${tests.size})", "Add all to list (${tests.size})"), onClick = { onAddAllToOrder(tests) }, modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("price_results_add_all"), enabled = tests.isNotEmpty()) { Icon(Icons.Default.AddCircle, contentDescription = null) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabeledIconAction(label = tr("ربط بعميل", "Customer"), onClick = { onStartCustomer(tests) }, modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("price_results_customer"), enabled = tests.isNotEmpty()) { Icon(Icons.Default.People, contentDescription = null) }

                LabeledIconAction(label = tr("استخراج صورة", "Create image"), onClick = { onStartOutput(tests) }, modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("price_results_output"), enabled = tests.isNotEmpty()) { Icon(Icons.Default.Image, contentDescription = null) }
            }

            Text(
                text = tr(
                    "اختار العميل ثم احفظ الطلب، وبعدها استخرج صورة العميل أو صورة طلب المعمل مباشرة.",
                    "Choose the customer, save the order, then create the customer or lab image directly."
                ),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Color(0xFF5D7472)
            )
        }
    }
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    topContent: (@Composable () -> Unit)? = null,
    uiState: SearchUiState,
    selectedTests: List<LabTest>,
    recognizedTests: List<LabTest>,
    isManager: Boolean,
    lab2LabPrices: Map<Int, String>,
    customerPriceOverrides: Map<Int, String>,
    activeOrderCustomer: Customer?,
    onChooseCustomer: () -> Unit,
    onClearCustomer: () -> Unit,
    onSaveCustomerOrder: () -> Unit,
    onSelectTest: (LabTest) -> Unit,
    onRemoveTest: (Int) -> Unit,
    onAddAllRecognized: () -> Unit,
    onEditPrices: (LabTest) -> Unit,
    onAddPriceResultsToOrder: (List<LabTest>) -> Unit = {},
    onStartPriceResultsCustomer: (List<LabTest>) -> Unit = {},
    onStartPriceResultsOutput: (List<LabTest>) -> Unit = {},
    allowOrderSelection: Boolean = true,
    showResolvedBatchAction: Boolean = false,
    selectedTestsContent: (@Composable (List<LabTest>) -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        if (topContent != null) {
            item(key = "workspace_top_content") {
                topContent()
            }
        }

        // 1. Selected Tests Section (tr("التحاليل المختارة", "Selected Tests"))
        if (allowOrderSelection && selectedTests.isNotEmpty()) {
            item(key = "selected_tests_section") {
                if (selectedTestsContent != null) {
                    selectedTestsContent(selectedTests)
                } else {
                    SelectedTestsSection(
                        selectedTests = selectedTests,
                        isManager = isManager,
                        lab2LabPrices = lab2LabPrices,
                        customerPriceOverrides = customerPriceOverrides,
                        activeOrderCustomer = activeOrderCustomer,
                        onChooseCustomer = onChooseCustomer,
                        onClearCustomer = onClearCustomer,
                        onSaveCustomerOrder = onSaveCustomerOrder,
                        onRemoveTest = onRemoveTest
                    )
                }
            }
        }

        // V90: image/PDF OCR already resolves recognizedTests before the normal grouped search UI.
        // Show its customer-price total directly, without requiring selection/add-to-order and
        // without depending on each search group having exactly one candidate.
        if (!allowOrderSelection && recognizedTests.size > 1) {
            item(key = "recognized_image_direct_total") {
                DetectedBatchTotalCard(
                    tests = recognizedTests.distinctBy { it.id },
                    customerPriceOverrides = customerPriceOverrides,
                    requestedCount = recognizedTests.distinctBy { it.id }.size,
                    unmatchedCount = 0
                )
            }
        }

        // 2. Imported image batch actions.
        val selectedIds = selectedTests.map { it.id }.toSet()
        val selectedRecognizedCount = recognizedTests.count { it.id in selectedIds }
        val remainingRecognizedCount = recognizedTests.size - selectedRecognizedCount
        if (allowOrderSelection && remainingRecognizedCount > 0) {
            item(key = "recognized_tests_actions") {
                RecognizedTestsActionsCard(
                    totalDetected = recognizedTests.size,
                    selectedDetected = selectedRecognizedCount,
                    remainingDetected = remainingRecognizedCount,
                    detectedTotalPrice = calculatePriceTotal(recognizedTests) { test ->
                        customerPriceOverrides[test.id] ?: test.customerPrice
                    },
                    onAddAll = onAddAllRecognized
                )
            }
        }

        // 3. Search States
        when (uiState) {
            is SearchUiState.EmptyQuery -> {
                if (!allowOrderSelection || selectedTests.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyStateView()
                    }
                }
            }
            is SearchUiState.NoResults -> {
                item(key = "no_results_section") {
                    NoResultsSection(unmatchedQueries = uiState.unmatchedQueries)
                }
            }
            is SearchUiState.Success -> {
                item(key = "search_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("نتائج البحث", "Search Results"),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF17324D)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE7F5F7))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${uiState.queryGroups.sumOf { it.candidates.size }} نتيجة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF007B87)
                            )
                        }
                    }
                }

                val resolvedSelectionTests = uiState.queryGroups
                    .mapNotNull { it.candidates.singleOrNull() }
                    .distinctBy { it.id }
                val requestedBatchCount = uiState.queryGroups.size + uiState.unmatchedQueries.size

                // V89: always show the total for any resolved multi-item input, including image/PDF imports.
                // This does not require adding tests to an order or selection list.
                if (requestedBatchCount > 1 && resolvedSelectionTests.isNotEmpty()) {
                    item(key = "detected_batch_price_total") {
                        DetectedBatchTotalCard(
                            tests = resolvedSelectionTests,
                            customerPriceOverrides = customerPriceOverrides,
                            requestedCount = requestedBatchCount,
                            unmatchedCount = uiState.unmatchedQueries.size
                        )
                    }
                }

                if (allowOrderSelection && showResolvedBatchAction) {
                    if (resolvedSelectionTests.size > 1) {
                        item(key = "selection_batch_add_all") {
                            LabeledIconAction(label = appText("إضافة كل النتائج (${resolvedSelectionTests.size})", "Add all results (${resolvedSelectionTests.size})"), onClick = { onAddPriceResultsToOrder(resolvedSelectionTests) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.AddCircle, contentDescription = null) }
                        }
                    }
                }

                items(uiState.queryGroups, key = { it.query }) { group ->
                    QueryGroupSection(
                        group = group,
                        isManager = isManager,
                        lab2LabPrices = lab2LabPrices,
                        customerPriceOverrides = customerPriceOverrides,
                        onSelectTest = onSelectTest,
                        onEditPrices = onEditPrices,
                        allowSelection = allowOrderSelection
                    )
                }

                if (uiState.unmatchedQueries.isNotEmpty()) {
                    item(key = "unmatched_queries") {
                        UnmatchedQueriesCard(unmatchedQueries = uiState.unmatchedQueries)
                    }
                }
            }
        }

        if (bottomContent != null) {
            item(key = "workspace_more") {
                bottomContent()
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PriceInquiryBasketSection(
    selectedTests: List<LabTest>,
    customerPriceOverrides: Map<Int, String>,
    onRemoveTest: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    val totalPrice = calculatePriceTotal(selectedTests) { customerPriceOverrides[it.id] ?: it.customerPrice }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Color(0xFF9DDDE3))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(appText("قائمة الاستعلام", "Inquiry list"), color = Color(0xFF17324D), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(appText("${selectedTests.size} تحليل مختار", "${selectedTests.size} selected tests"), color = Color(0xFF64748B), fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(appText("الإجمالي", "Total"), color = Color(0xFF64748B), fontSize = 10.sp)
                    Text(
                        "${formatTotalDisplay(totalPrice)} ${appText("ج", "EGP")}",
                        color = Color(0xFF007E89),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE5EEF2))

            selectedTests.forEach { test ->
                val price = customerPriceOverrides[test.id] ?: test.customerPrice
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE5EBF0))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(test.englishName.ifBlank { test.marketName.ifBlank { test.arabicName } }, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D), fontSize = 12.sp)
                            if (test.arabicName.isNotBlank() && test.arabicName != test.englishName) {
                                Text(test.arabicName, color = Color(0xFF64748B), fontSize = 10.sp)
                            }
                        }
                        Text(
                            "${price.orEmpty()} ${appText("ج", "EGP")}",
                            color = Color(0xFF087F5B),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                        LabeledIconAction(label = appText("إزالة", "Remove"), onClick = { onRemoveTest(test.id) }) {
                            Icon(Icons.Default.Close, contentDescription = appText("إزالة", "Remove"), tint = Color(0xFFB42318))
                        }
                    }
                }
            }

            LabeledIconAction(label = appText("مسح قائمة الاستعلام", "Clear inquiry list"), onClick = onClearAll, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Delete, contentDescription = null) }
        }
    }
}

@Composable
private fun QuickImageSelectedSection(
    selectedTests: List<LabTest>,
    customerPriceOverrides: Map<Int, String>,
    onRemoveTest: (Int) -> Unit
) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val branding = PdfGenerator.LabBranding(
        name = settings.pdfLabName,
        tagline = settings.brandTagline,
        whatsapp = settings.brandWhatsApp,
        phone = settings.brandPhone,
        address = settings.brandAddress,
        extraContact = if (settings.pdfShowContactInfo) settings.pdfContactInfo else "",
        logoPath = settings.brandLogoPath
    )
    val totalPrice = calculatePriceTotal(selectedTests) { customerPriceOverrides[it.id] ?: it.customerPrice }
    var showOptionalCustomer by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var busyAction by remember { mutableStateOf<String?>(null) }
    var pendingLegacySave by remember { mutableStateOf(false) }

    fun buildImage() = PdfGenerator.generateQuickTestsImage(
        context = context,
        selectedTests = selectedTests,
        customerPriceOverrides = customerPriceOverrides,
        customerName = customerName.trim(),
        customerPhone = customerPhone.trim(),
        branding = branding
    )

    fun saveNow() {
        if (busyAction != null) return
        busyAction = "save"
        try {
            val file = buildImage()
            if (file != null) {
                PdfGenerator.saveGeneratedImageToGallery(
                    context = context,
                    file = file,
                    displayName = "Tahalil_Alakkad_Quick_${System.currentTimeMillis()}.png"
                )
            }
        } finally {
            busyAction = null
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldSave = pendingLegacySave
        pendingLegacySave = false
        if (granted && shouldSave) saveNow()
    }

    fun requestSave() {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingLegacySave = true
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveNow()
        }
    }

    fun shareNow() {
        if (busyAction != null) return
        busyAction = "share"
        try {
            val file = buildImage()
            if (file != null) {
                PdfGenerator.shareGeneratedImage(
                    context = context,
                    file = file,
                    subject = "${settings.pdfLabName} - قائمة تحاليل وأسعار",
                    chooserTitle = "مشاركة الصورة السريعة"
                )
            }
        } finally {
            busyAction = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = Color(0x6600E7F2)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Color(0xFF32D7E4))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF062638), Color(0xFF0A5162), Color(0xFF007F91))
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            appText("الصورة السريعة", "Quick image"),
                            color = Color(0xFF8CF7FF),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            appText("${selectedTests.size} تحليل مختار", "${selectedTests.size} selected tests"),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(appText("الإجمالي", "Total"), color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                        Text(
                            "${formatTotalDisplay(totalPrice)} ${appText("ج", "EGP")}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 21.sp
                        )
                    }
                }
            }

            selectedTests.forEach { test ->
                SelectedTestRow(
                    test = test,
                    customerPrice = customerPriceOverrides[test.id] ?: test.customerPrice,
                    isManager = false,
                    lab2LabPrice = null,
                    onRemove = { onRemoveTest(test.id) }
                )
            }

            LabeledIconAction(label = if (showOptionalCustomer) appText("إخفاء بيانات العميل الاختيارية", "Hide optional customer data")
                    else appText("إضافة بيانات العميل - اختياري", "Add customer details - optional"), onClick = { showOptionalCustomer = !showOptionalCustomer }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddCircle, contentDescription = null) }

            AnimatedVisibility(visible = showOptionalCustomer) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appText("اسم العميل - اختياري", "Customer name - optional")) },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp)
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it.filter { ch -> ch.isDigit() }.take(15) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appText("واتساب - اختياري", "WhatsApp - optional")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp)
                    )
                    Text(
                        appText("البيانات دي بتظهر في الصورة فقط ومش بتتسجل في سجلات العملاء.", "These details appear in the image only and are not saved to customer records."),
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledIconAction(label = appText("حفظ الصورة", "Save image"), onClick = { requestSave() }, modifier = Modifier
                        .weight(1f)
                        .shadow(13.dp, RoundedCornerShape(13.dp), ambientColor = Color(0xFF25F0A7), spotColor = Color(0xFF25F0A7)), enabled = busyAction == null) { Icon(Icons.Default.Save, contentDescription = null) }
                LabeledIconAction(label = appText("مشاركة", "Share"), onClick = { shareNow() }, modifier = Modifier
                        .weight(1f)
                        .shadow(11.dp, RoundedCornerShape(13.dp), ambientColor = Color(0xFFAE72FF), spotColor = Color(0xFFAE72FF)), enabled = busyAction == null) { Icon(Icons.Default.Share, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun RecognizedTestsActionsCard(
    totalDetected: Int,
    selectedDetected: Int,
    remainingDetected: Int,
    detectedTotalPrice: Double,
    onAddAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F7)),
        border = BorderStroke(1.dp, Color(0xFFB7E4E1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("التحاليل المقروءة من الملف", "Tests detected from file"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF075E63)
                )
                Text(
                    text = "$totalDetected",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF007E89)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(
                        "تمت إضافة $selectedDetected • متبقي $remainingDetected",
                        "$selectedDetected added • $remainingDetected remaining"
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF365A5C)
                )
                Text(
                    text = "${formatTotalDisplay(detectedTotalPrice)} ${tr("جنيه", "EGP")}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF006B67)
                )
            }

            Text(
                text = tr(
                    "الإجمالي محسوب مباشرة من التحاليل المقروءة بدون إضافتها للقائمة.",
                    "Total is calculated directly from detected tests without adding them to the list."
                ),
                fontSize = 11.sp,
                color = Color(0xFF527A77)
            )

            LabeledIconAction(label = tr(
                        "إضافة كل التحاليل المقروءة ($remainingDetected)",
                        "Add all detected tests ($remainingDetected)"
                    ), onClick = onAddAll, modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFF00D9FF), spotColor = Color(0xFF00D9FF))
                    .testTag("add_all_recognized_tests")) { Icon(Icons.Default.AddCircle, contentDescription = null) }
        }
    }
}

@Composable
private fun SelectedTestsSection(
    selectedTests: List<LabTest>,
    isManager: Boolean,
    lab2LabPrices: Map<Int, String>,
    customerPriceOverrides: Map<Int, String>,
    activeOrderCustomer: Customer?,
    onChooseCustomer: () -> Unit,
    onClearCustomer: () -> Unit,
    onSaveCustomerOrder: () -> Unit,
    onRemoveTest: (Int) -> Unit
) {
    val settings = LocalAppSettings.current
    val totalPrice = calculatePriceTotal(selectedTests) { customerPriceOverrides[it.id] ?: it.customerPrice }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0x1A000000)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Color(0xFF0061A4))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF0061A4),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("التحاليل المختارة", "Selected Tests"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001D35)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFE1F1FF))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${selectedTests.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0061A4)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // V64: keep the order total visible BEFORE the long selected-tests list.
            // The transaction total is always shown while creating an order; catalog price
            // visibility settings still apply elsewhere in the app.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0061A4))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tr("إجمالي الطلب الحالي", "Current Order Total"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = tr("${selectedTests.size} تحليل مختار", "${selectedTests.size} selected tests"),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatTotalDisplay(totalPrice),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tr("جنيه", "EGP"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected items list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedTests.forEach { test ->
                    SelectedTestRow(
                        test = test,
                        customerPrice = customerPriceOverrides[test.id] ?: test.customerPrice,
                        isManager = isManager,
                        lab2LabPrice = lab2LabPrices[test.id],
                        onRemove = { onRemoveTest(test.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Customer order flow
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (activeOrderCustomer == null) Color(0xFFF8FAFC) else Color(0xFFE7F5F7),
                border = BorderStroke(1.dp, if (activeOrderCustomer == null) Color(0xFFE2E8F0) else Color(0xFF7DD3FC))
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (activeOrderCustomer == null) {
                        Text(
                            tr("اربط التحاليل بعميل قبل حفظ الطلب", "Link tests to a customer before saving"),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF17324D)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("اختار عميل سابق أو أضف عميل جديد", "Choose an existing customer or add a new one"),
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(Modifier.height(10.dp))
                        LabeledIconAction(label = appText("اختيار العميل", "Choose customer"), onClick = onChooseCustomer, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appText("العميل المرتبط بالطلب", "Order customer"), fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(activeOrderCustomer.name, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                                Text(
                                    tr("واتساب: ${activeOrderCustomer.phone}", "WhatsApp: ${activeOrderCustomer.phone}"),
                                    fontSize = 11.sp,
                                    color = Color(0xFF006D86)
                                )
                            }
                            LabeledIconAction(label = appText("تغيير", "Change"), onClick = onClearCustomer) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                        }
                        Spacer(Modifier.height(10.dp))
                        LabeledIconAction(label = appText("متابعة الدفع", "Continue to payment"), onClick = onSaveCustomerOrder, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payments, contentDescription = null) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // V73: one clear continuation button above is enough; no duplicate document action.
        }
    }
}

@Composable
fun ProfessionalOrderCheckoutDialog(
    customer: Customer,
    selectedTests: List<LabTest>,
    customerPriceOverrides: Map<Int, String>,
    onDismiss: () -> Unit,
    onConfirm: (discount: Double, discountPercent: Double, paymentStatus: String, paidAmount: Double, notes: String, createImage: Boolean, done: () -> Unit) -> Unit
) {
    val total = calculatePriceTotal(selectedTests) { customerPriceOverrides[it.id] ?: it.customerPrice }
    var paymentStatus by remember { mutableStateOf("unpaid") }
    var paidText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var savingOrder by remember { mutableStateOf(false) }

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

    BackHandler(enabled = true) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(12.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF8FAFC)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr("تأكيد الطلب", "Confirm order"),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                    color = Color(0xFF17324D)
                                )
                                Text(
                                    "${customer.name} • ${customer.phone}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF006D86)
                                )
                            }
                            LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = tr("إغلاق", "Close"))
                            }
                        }
                    }
                }

                item {
                    FinanceDashboardCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = tr("إجمالي الطلب", "Order total"),
                        value = "${formatTotalDisplay(total)} ج",
                        subtitle = tr("${selectedTests.size} تحليل", "${selectedTests.size} tests"),
                        icon = Icons.Default.ReceiptLong,
                        accent = Color(0xFF0369A1),
                        background = Color(0xFFEFF6FF)
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(tr("حالة الدفع", "Payment status"), fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                            Spacer(Modifier.height(9.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                            if (paidEntryRequired) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = paidText,
                                    onValueChange = { value -> paidText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                    label = { Text(tr("المبلغ المسدد (إجباري)", "Paid amount (required)")) },
                                    suffix = { Text(tr("جنيه", "EGP")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    isError = !paidEntryValid
                                )
                                if (!paidEntryValid) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        if (paymentStatus == "unpaid") {
                                            tr("اكتب 0 لو لم يتم سداد أي مبلغ.", "Enter 0 if nothing was paid.")
                                        } else {
                                            tr("اكتب مبلغ أكبر من 0 وأقل من إجمالي الطلب.", "Enter an amount greater than 0 and less than the order total.")
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFFB91C1C)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinanceDashboardCard(
                            modifier = Modifier.weight(1f),
                            title = tr("المدفوع", "Paid"),
                            value = "${formatTotalDisplay(paidAmount)} ج",
                            subtitle = paymentStatusArabic(paymentStatus),
                            icon = Icons.Default.Payments,
                            accent = Color(0xFF15803D),
                            background = Color(0xFFECFDF3)
                        )
                        FinanceDashboardCard(
                            modifier = Modifier.weight(1f),
                            title = tr("المتبقي", "Remaining"),
                            value = "${formatTotalDisplay(remaining)} ج",
                            subtitle = if (remaining > 0.0) tr("مستحق", "Due") else tr("تم السداد", "Paid in full"),
                            icon = Icons.Default.AccountBalanceWallet,
                            accent = if (remaining > 0.0) Color(0xFFB91C1C) else Color(0xFF15803D),
                            background = if (remaining > 0.0) Color(0xFFFEF2F2) else Color(0xFFECFDF3)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text(tr("ملاحظات - اختياري", "Notes - Optional")) },
                        minLines = 2,
                        maxLines = 4
                    )
                }

                item {
                    Text(
                        tr("بالضغط على الزر سيتم حفظ الطلب وإرساله للمعمل مباشرة.", "Pressing the button saves the order and sends it to the lab immediately."),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(Modifier.height(4.dp))
                    LabeledIconAction(label = if (savingOrder) tr("جاري الإرسال...", "Sending...") else tr("حفظ وإرسال الطلب للمعمل", "Save & send to lab"), onClick = {
                            if (!savingOrder) {
                                savingOrder = true
                                onConfirm(0.0, 0.0, paymentStatus, paidAmount, notes, true) { savingOrder = false }
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = paidEntryValid && !savingOrder) { Icon(Icons.Default.Send, contentDescription = null) }
                    LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = !savingOrder) { Icon(Icons.Default.Close, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
private fun FinanceDashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    background: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        color = background,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                maxLines = 1
            )
        }
    }
}

private fun formatDashboardPercent(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.1f", value)
}

@Composable
private fun SelectedTestRow(
    test: LabTest,
    customerPrice: String?,
    isManager: Boolean,
    lab2LabPrice: String?,
    onRemove: () -> Unit
) {
    val settings = LocalAppSettings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (settings.showEnglishName) {
                    Text(
                        text = test.englishName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001D35),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (settings.showArabicName && test.arabicName.isNotBlank()) {
                    Text(
                        text = test.arabicName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (settings.showMarketName && test.marketName.isNotBlank() && test.marketName != test.englishName) {
                    Text(
                        text = test.marketName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (settings.showCustomerPrice) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (settings.showCustomerPrice) {
                        Text(
                            text = "عميل: ${customerPrice ?: "0"} جنيه",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF004A77)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            LabeledIconAction(label = tr("إزالة", "Remove"), onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = tr("إزالة", "Remove"),
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun UnmatchedQueriesCard(unmatchedQueries: List<String>) {
    if (unmatchedQueries.isEmpty()) return

    val context = LocalContext.current
    var pendingExternalQuery by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color(0x10000000)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8ED)),
        border = BorderStroke(1.dp, Color(0xFFF5D9A8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = tr("بنود غير موجودة ضمن التحاليل", "Items not found in the lab catalogue"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = tr(
                            "لن يتم احتساب سعر تحليل لأي بند غير موجود. هنوضح نوعه لو أمكن.",
                            "No lab-test price will be assigned to unmatched items. The type is shown when it can be identified safely."
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8A5A22),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                unmatchedQueries.forEach { query ->
                    val info = remember(query) { NonLabMedicalLookup.classify(query) }
                    val confidenceLabel = when (info.confidence) {
                        NonLabMedicalInfo.Confidence.CONFIRMED -> tr("مؤكد", "Confirmed")
                        NonLabMedicalInfo.Confidence.LIKELY -> tr("مرجح", "Likely")
                        NonLabMedicalInfo.Confidence.UNKNOWN -> tr("غير محدد", "Unclassified")
                    }
                    val category = appText(info.categoryAr, info.categoryEn)
                    val title = appText(info.titleAr, info.titleEn)
                    val description = appText(info.descriptionAr, info.descriptionEn)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF0E2C8))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                text = query,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF3F2D17)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF1D6))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = category,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9A5B00)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = confidenceLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5C3B12)
                            )

                            Text(
                                text = description,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B),
                                lineHeight = 18.sp
                            )

                            LabeledIconAction(label = tr("البحث عن المصطلح على الإنترنت", "Search this term on the web"), onClick = { pendingExternalQuery = query }, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)) { Icon(Icons.Default.Search, contentDescription = null) }
                        }
                    }
                }
            }
        }
    }

    val externalQuery = pendingExternalQuery
    if (!externalQuery.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { pendingExternalQuery = null },
            title = {
                Text(
                    text = tr("فتح موقع خارجي", "Open external website"),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = tr(
                        "هتغادر تطبيق تحاليل العقاد وتفتح المتصفح للبحث عن: $externalQuery\n\nنتائج البحث من مواقع خارجية وليست جزءا من التطبيق. هل تريد المتابعة؟",
                        "You are about to leave Tahalil Alakkad and open your browser to search for: $externalQuery\n\nSearch results come from external websites and are not part of the app. Continue?"
                    ),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                LabeledIconAction(label = tr("متابعة", "Continue"), onClick = {
                        val term = externalQuery
                        pendingExternalQuery = null
                        val url = "https://www.google.com/search?q=" + android.net.Uri.encode("$term medical")
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        runCatching { context.startActivity(intent) }
                    }) { Icon(Icons.Default.TouchApp, contentDescription = null) }
            },
            dismissButton = {
                LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = { pendingExternalQuery = null }) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        )
    }
}

@Composable
private fun QueryGroupSection(
    group: QueryGroup,
    isManager: Boolean,
    lab2LabPrices: Map<Int, String>,
    customerPriceOverrides: Map<Int, String>,
    onSelectTest: (LabTest) -> Unit,
    onEditPrices: (LabTest) -> Unit,
    allowSelection: Boolean = true
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (group.candidates.size > 1) {
            AmbiguousHeaderCard(query = group.query, count = group.candidates.size)
        }
        group.candidates.forEach { test ->
            SearchCandidateCard(
                test = test,
                customerPrice = customerPriceOverrides[test.id] ?: test.customerPrice,
                isManager = isManager,
                lab2LabPrice = lab2LabPrices[test.id],
                onSelect = { onSelectTest(test) },
                onEditPrices = { onEditPrices(test) },
                allowSelection = allowSelection
            )
        }
    }
}

@Composable
private fun AmbiguousHeaderCard(query: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, Color(0xFFFFD8A8))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = Color(0xFFC2410C),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "نتائج عدة لـ \"$query\" ($count تحاليل):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9A3412)
            )
        }
    }
}

@Composable
private fun SearchCandidateCard(
    test: LabTest,
    customerPrice: String?,
    isManager: Boolean,
    lab2LabPrice: String?,
    onSelect: () -> Unit,
    onEditPrices: () -> Unit,
    allowSelection: Boolean = true
) {
    val settings = LocalAppSettings.current
    // V67: price inquiry must always show the identifying names and customer price,
    // even if the user hid some optional fields in general display settings.
    val forcePriceInquiry = !allowSelection
    val showEnglishName = settings.showEnglishName || forcePriceInquiry
    val showArabicName = settings.showArabicName || forcePriceInquiry
    val showMarketName = settings.showMarketName || forcePriceInquiry
    val showCustomerPrice = settings.showCustomerPrice || forcePriceInquiry
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = Color(0x14000000)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5ECF2))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showEnglishName) {
                        Text(
                            text = test.englishName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF102A43),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (showArabicName && test.arabicName.isNotBlank()) {
                        if (showEnglishName) Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = test.arabicName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF52667A),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!showEnglishName && !showArabicName && test.marketName.isNotBlank()) {
                        Text(
                            text = test.marketName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF102A43),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF0F4F8))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "#${test.id}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF718096)
                    )
                }
            }

            if (showMarketName && test.marketName.isNotBlank() && test.marketName != test.englishName) {
                Spacer(modifier = Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("الاسم الدارج", "Market Name"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7B8794)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFF2F7FA))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = test.marketName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF36536B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (showCustomerPrice) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showCustomerPrice) {
                        PriceBox(
                            modifier = Modifier.weight(1f),
                            title = tr("سعر العميل", "Customer Price"),
                            price = customerPrice,
                            backgroundColor = Color(0xFFEDF7FF),
                            borderColor = Color(0xFFCFE8F7),
                            titleColor = Color(0xFF28607E),
                            priceColor = Color(0xFF114D6B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (allowSelection) {
                LabeledIconAction(label = tr("إضافة إلى القائمة", "Add to List"), onClick = onSelect, modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("select_test_${test.id}")) { Icon(Icons.Default.AddCircle, contentDescription = null) }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TestGuideMoreButton(
                test = test,
                modifier = Modifier.testTag("search_test_guide_${test.id}")
            )
        }
    }
}

@Composable
private fun NoResultsSection(unmatchedQueries: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (unmatchedQueries.isNotEmpty()) {
            UnmatchedQueriesCard(unmatchedQueries = unmatchedQueries)
        }
        NoResultsView()
    }
}

@Composable
private fun LabTestCard(
    test: LabTest,
    isSelected: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 4.dp else 3.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0x12000000)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF8FAFC) else Color.White
        ),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF0061A4)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // English Name
            Text(
                text = test.englishName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF001D35),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Arabic Name
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tr("الاسم العربي: ", "Arabic name: "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = if (test.arabicName.isNotBlank()) test.arabicName else "غير مسجل",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Market Name
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tr("الاسم الدارج: ", "Market name: "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = if (test.marketName.isNotBlank()) test.marketName else "غير مسجل",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price Section
            PriceBox(
                modifier = Modifier.fillMaxWidth(),
                title = tr("سعر العميل", "Customer Price"),
                price = test.customerPrice,
                backgroundColor = Color(0xFFF0F7FF),
                borderColor = Color(0xFFD0E4FF),
                titleColor = Color(0xFF004A77),
                priceColor = Color(0xFF004A77)
            )

            Spacer(modifier = Modifier.height(10.dp))
            TestGuideMoreButton(
                test = test,
                modifier = Modifier.testTag("basic_test_guide_${test.id}")
            )
        }
    }
}

@Composable
private fun PriceBox(
    modifier: Modifier = Modifier,
    title: String,
    price: String?,
    backgroundColor: Color,
    borderColor: Color,
    titleColor: Color,
    priceColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (!price.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = price,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = priceColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tr("جنيه", "EGP"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = priceColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            } else {
                Text(
                    text = "غير مسجل",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6EDF3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFE7F5F7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Biotech,
                    contentDescription = null,
                    tint = Color(0xFF007E89),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = tr("ابدأ بكتابة اسم التحليل", "Start typing a test name"),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF17324D),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = tr("البحث يدعم العربي والإنجليزي والاسم المتداول في المعامل", "Search supports Arabic, English and common lab names"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF718096),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun NoResultsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEE2E2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = tr("لم يتم العثور على التحليل", "Test Not Found"),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF001D35),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = tr("جرّب البحث باسم مختلف أو بالاسم الإنجليزي", "Try another name or the English name"),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
    }
}


private fun paymentStatusArabic(status: String): String = when (status) {
    "paid" -> "مدفوع"
    "partial" -> "جزئي"
    else -> tr("غير مدفوع", "Unpaid")
}
