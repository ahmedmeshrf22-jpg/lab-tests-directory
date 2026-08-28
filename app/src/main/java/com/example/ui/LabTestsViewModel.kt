package com.example.ui

import android.os.SystemClock

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppUserProfile
import com.example.data.model.AuditLogEntry
import com.example.data.model.AuthorizedDevice
import com.example.data.model.Customer
import com.example.data.model.CustomerActivityEntry
import com.example.data.model.CustomerOrder
import com.example.data.model.CustomerOrderItem
import com.example.data.model.LabTest
import com.example.data.model.PaymentEntry
import com.example.data.model.ReportSummary
import com.example.data.model.normalizeText
import com.example.data.model.normalizeUserRole
import com.example.data.model.permissionsForRole
import com.example.data.repository.LabTestRepository
import com.example.notifications.OrderNotificationManager
import com.example.backend.FirestoreResultFileStore
import com.example.settings.AppSettings
import com.example.settings.AppSettingsStore
import com.example.settings.ConnectivityMonitor
import com.example.settings.FirestorePerformance
import com.example.settings.PendingSyncStore
import com.example.settings.OfflineAccessVault
import com.example.resilience.ShadowBackupReplicator
import com.example.settings.tr
import com.example.util.CommercialBackupManager
import com.example.util.AutoBackupCredentialStore
import com.example.notifications.AutoBackupScheduler
import com.example.notifications.BackupNotificationManager
import com.example.util.NonLabMedicalLookup
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


data class QueryGroup(
    val query: String,
    val candidates: List<LabTest>
)

sealed interface SearchUiState {
    object EmptyQuery : SearchUiState
    data class Success(
        val query: String,
        val queryGroups: List<QueryGroup>,
        val unmatchedQueries: List<String> = emptyList()
    ) : SearchUiState
    data class NoResults(
        val unmatchedQueries: List<String> = emptyList()
    ) : SearchUiState
}

data class SystemHealthState(
    val checking: Boolean = false,
    val firebaseReachable: Boolean? = null,
    val latencyMs: Long? = null,
    val checkedAtMillis: Long = 0L,
    val message: String = ""
)

data class AdminAlert(
    val id: String,
    val category: String,
    val titleAr: String,
    val titleEn: String,
    val detailsAr: String,
    val detailsEn: String,
    val severity: String = "info", // info | warning | critical
    val createdAtMillis: Long = 0L,
    val actorEmail: String = "",
    val isRead: Boolean = false
)

/** V32 manager-facing integrity finding. No data is deleted automatically. */
data class DataIntegrityIssue(
    val id: String,
    val type: String,
    val titleAr: String,
    val titleEn: String,
    val detailsAr: String,
    val detailsEn: String,
    val severity: String = "warning",
    val entityId: String = "",
    val documentPath: String = ""
)

data class DataIntegrityState(
    val checking: Boolean = false,
    val checkedAtMillis: Long = 0L,
    val customersScanned: Int = 0,
    val ordersScanned: Int = 0,
    val issueCount: Int = 0,
    val criticalCount: Int = 0,
    val message: String = ""
)

class LabTestsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MANAGER_EMAIL = "abdelrahman@tahali.com"
        private const val MANAGER_UID = "Wps5Y19pZSbeGooEzFfW5UFsshq2"
        private const val LAB2LAB_COLLECTION = "lab2lab_prices"
        private const val CUSTOMER_OVERRIDES_COLLECTION = "customer_price_overrides"
        private const val CUSTOMERS_COLLECTION = "customers"
        private const val LAB_ORDERS_COLLECTION = "lab_orders"
        private const val USERS_COLLECTION = "users"
        private const val DEVICES_SUBCOLLECTION = "devices"
        private const val AUDIT_COLLECTION = "audit_logs"
        private const val PHONE_REGISTRY_COLLECTION = "phone_registry"
        private const val SECONDARY_AUTH_APP = "TahalilUserAdmin"
        private const val ADMIN_UNLOCK_APP = "TahalilAdminUnlock"
        private const val ADMIN_GATE_EMAIL = "tahalil.admin.gate.v60@tahali.com"
        private const val ADMIN_PIN_SALT = "TahalilAlakkad.AdminGate.v60.2026"
        private const val ADMIN_PIN_ROUNDS = 40_000
        private const val SERVER_PAGE_SIZE = 750L
        private const val DAILY_ORDER_LIMIT = 1_500L
        private const val ORDER_ARCHIVE_LIMIT = 3_000L
        private const val REALTIME_ORDER_LIMIT = 500L
        private const val CUSTOMER_ACTIVITY_LIMIT = 150L
        private const val CUSTOMER_ORDERS_LIMIT = 500L
        private const val PAYMENT_HISTORY_LIMIT = 250L
        private const val AUDIT_LOG_LIMIT = 400L
        private const val HIGH_ACTIVITY_ALERT_COUNT = 20
        private const val LARGE_DEBT_ALERT = 5_000.0
        private const val CRITICAL_DEBT_ALERT = 20_000.0
        private const val INTEGRITY_ORDER_SCAN_LIMIT = 1_500L
        private const val CONFLICT_WINDOW_MS = 120_000L

        fun isManagerAccount(email: String?, uid: String? = null): Boolean {
            val normalizedEmail = email?.trim()?.lowercase()
            return normalizedEmail == MANAGER_EMAIL || uid == MANAGER_UID
        }
    }

    private val repository = LabTestRepository(application)
    private val _catalogRevision = MutableStateFlow(0)
    val catalogRevision: StateFlow<Int> = _catalogRevision.asStateFlow()
    private val settingsStore = AppSettingsStore(application)
    private val connectivityMonitor = ConnectivityMonitor(application)
    private val pendingSyncStore = PendingSyncStore(application)

    val appSettings: StateFlow<AppSettings> = settingsStore.settings

    // V29 centralizes Firestore tuning: persistent local cache, larger cache budget,
    // and one shared instance. This keeps reads responsive while preserving V28 offline writes.
    private val firestore: FirebaseFirestore = FirestorePerformance.get()

    private val _isOnline = MutableStateFlow(connectivityMonitor.isOnline.value)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _offlineGraceAccess = MutableStateFlow(false)
    val offlineGraceAccess: StateFlow<Boolean> = _offlineGraceAccess.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = pendingSyncStore.count

    private val _lastSuccessfulSyncMillis = MutableStateFlow(settingsStore.settings.value.lastSyncMillis)
    val lastSuccessfulSyncMillis: StateFlow<Long> = _lastSuccessfulSyncMillis.asStateFlow()

    private val _systemHealth = MutableStateFlow(SystemHealthState())
    val systemHealth: StateFlow<SystemHealthState> = _systemHealth.asStateFlow()

    private val adminAlertPrefs = application.getSharedPreferences("admin_alerts_v31", Context.MODE_PRIVATE)
    // V33: keep Abdulrahman's temporary user-mode selection across normal app closes/reopens.
    // This is local-only UI session state; Firebase Auth remains signed in as the manager.
    private val switchSessionPrefs = application.getSharedPreferences("manager_switch_session_v33", Context.MODE_PRIVATE)
    private val _adminAlerts = MutableStateFlow<List<AdminAlert>>(emptyList())
    val adminAlerts: StateFlow<List<AdminAlert>> = _adminAlerts.asStateFlow()
    private val _unreadAdminAlertCount = MutableStateFlow(0)
    val unreadAdminAlertCount: StateFlow<Int> = _unreadAdminAlertCount.asStateFlow()

    private val _dataIntegrityState = MutableStateFlow(DataIntegrityState())
    val dataIntegrityState: StateFlow<DataIntegrityState> = _dataIntegrityState.asStateFlow()
    private val _dataIntegrityIssues = MutableStateFlow<List<DataIntegrityIssue>>(emptyList())
    val dataIntegrityIssues: StateFlow<List<DataIntegrityIssue>> = _dataIntegrityIssues.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // V39: holds one image/PDF shared to the app until the authenticated UI consumes it.
    private val _pendingSharedQrUri = MutableStateFlow<String?>(null)
    val pendingSharedQrUri: StateFlow<String?> = _pendingSharedQrUri.asStateFlow()

    // V93: system image/document pickers temporarily background the activity.
    // Keep the operational UI unlocked while the picker is open so staff users
    // receive the ActivityResult callback instead of being bounced to the app lock screen.
    private val _externalPickerActive = MutableStateFlow(false)
    val externalPickerActive: StateFlow<Boolean> = _externalPickerActive.asStateFlow()

    fun setExternalPickerActive(active: Boolean) {
        _externalPickerActive.value = active
    }

    fun queueSharedQrUri(uri: String) {
        if (uri.isNotBlank()) _pendingSharedQrUri.value = uri
    }

    fun consumeSharedQrUri(uri: String) {
        if (_pendingSharedQrUri.value == uri) _pendingSharedQrUri.value = null
    }

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.EmptyQuery)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _selectedTests = MutableStateFlow<List<LabTest>>(emptyList())
    val selectedTests: StateFlow<List<LabTest>> = _selectedTests.asStateFlow()

    // V63: keeps the exact catalogue tests detected from the latest image/PDF import.
    // This lets the user add all detected tests at once without losing the remaining results.
    private val _recognizedTests = MutableStateFlow<List<LabTest>>(emptyList())
    val recognizedTests: StateFlow<List<LabTest>> = _recognizedTests.asStateFlow()

    private val _isManager = MutableStateFlow(false)
    val isManager: StateFlow<Boolean> = _isManager.asStateFlow()

    // V60: every account uses the same UI. Sensitive administration is unlocked
    // with a six-digit admin PIN. The PIN is never stored as plaintext: the app
    // validates a slow SHA-256 verifier, then signs a secondary Firebase admin-gate
    // account in with a strong password deterministically derived from that PIN.
    private val _adminUnlocked = MutableStateFlow(false)
    val adminUnlocked: StateFlow<Boolean> = _adminUnlocked.asStateFlow()
    private var adminFirebaseApp: FirebaseApp? = null
    private var adminFirestore: FirebaseFirestore? = null
    private var adminPinFailedAttempts = 0
    private var adminPinLockedUntilElapsed = 0L

    /**
     * V116 one-way manager impersonation policy:
     * - Abdelrahman can temporarily operate as any enabled non-manager user.
     * - While impersonating, daily permissions come from the selected user profile.
     * - Normal users can never start impersonation and nobody can impersonate Abdelrahman.
     */
    private fun managerIsActingAsUser(): Boolean = _actualManager.value && _actingAsUser.value != null

    private fun hasAdminAccess(): Boolean =
        (_actualManager.value && !managerIsActingAsUser()) || _adminUnlocked.value

    /** Operational permissions follow the effective profile, not merely Firebase Auth. */
    private fun operationalProfile(): AppUserProfile? = when {
        _actualManager.value && _actingAsUser.value != null -> _actingAsUser.value
        _actualManager.value -> managerProfile()
        else -> _currentUserProfile.value
    }

    private fun hasOperationalAccess(): Boolean =
        _accountEnabled.value == true && _deviceAccessStatus.value == "approved"

    private fun canEditCustomersOperationally(): Boolean =
        (_actualManager.value && !managerIsActingAsUser()) || operationalProfile()?.canEditCustomers == true

    private fun canDiscountOperationally(): Boolean =
        (_actualManager.value && !managerIsActingAsUser()) || operationalProfile()?.canDiscount == true

    private fun canCollectPaymentsOperationally(): Boolean =
        (_actualManager.value && !managerIsActingAsUser()) || operationalProfile()?.canCollectPayments == true

    private fun canViewReportsOperationally(): Boolean =
        (_actualManager.value && !managerIsActingAsUser()) || operationalProfile()?.canViewSalesReports == true

    private fun clearSensitiveRuntimeData() {
        _customers.value = emptyList()
        _customerOrders.value = emptyList()
        _dailyOrders.value = emptyList()
        _orderArchive.value = emptyList()
        _orderArchiveLoading.value = false
        _customerActivity.value = emptyList()
        _paymentHistory.value = emptyList()
        _reportOrders.value = emptyList()
        _managerHomeSummary.value = ReportSummary()
        _auditLogs.value = emptyList()
        _users.value = emptyList()
        _authorizedDevices.value = emptyList()
        _customerPriceOverrides.value = emptyMap()
        customerOverridesLoaded = false
        _lab2LabPrices.value = emptyMap()
        managerPricesLoaded = false
    }

    private fun privilegedFirestore(): FirebaseFirestore =
        if (_actualManager.value && !managerIsActingAsUser()) firestore
        else adminFirestore ?: error("Admin access is not unlocked")

    private fun adminPinDigest(pin: String, purpose: String): String {
        var bytes = "$purpose|$ADMIN_PIN_SALT|$pin".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(ADMIN_PIN_ROUNDS) { bytes = digest.digest(bytes) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun derivedAdminFirebasePassword(pin: String): String =
        "Aa1!" + adminPinDigest(pin, "firebase").take(48)

    private fun completeAdminUnlock(
        app: FirebaseApp,
        auth: FirebaseAuth,
        onResult: (Boolean, String) -> Unit
    ) {
        val email = auth.currentUser?.email?.trim()?.lowercase()
        if (email != ADMIN_GATE_EMAIL) {
            auth.signOut()
            _adminUnlocked.value = false
            onResult(false, tr("تعذر التحقق من حساب الإدارة", "Unable to verify the admin account"))
            return
        }
        adminFirebaseApp = app
        val candidateFirestore = FirebaseFirestore.getInstance(app)
        // Do not show an unlocked admin UI until Firestore confirms that the
        // secondary admin identity is actually authorized by the deployed rules.
        candidateFirestore.collection(LAB2LAB_COLLECTION)
            .limit(1)
            .get(Source.SERVER)
            .addOnSuccessListener {
                adminFirestore = candidateFirestore
                _adminUnlocked.value = true
                loadManagerPriceState()
                onResult(true, tr("تم فتح خدمات الإدارة", "Administration unlocked"))
            }
            .addOnFailureListener {
                auth.signOut()
                adminFirestore = null
                _adminUnlocked.value = false
                onResult(
                    false,
                    tr(
                        "PIN صحيح لكن صلاحيات Firebase للإدارة لم يتم تفعيلها بعد",
                        "PIN is correct, but Firebase admin permissions are not active yet"
                    )
                )
            }
    }

    private fun registerAdminPinFailure(onResult: (Boolean, String) -> Unit) {
        _adminUnlocked.value = false
        adminPinFailedAttempts += 1
        if (adminPinFailedAttempts >= 5) {
            adminPinFailedAttempts = 0
            adminPinLockedUntilElapsed = SystemClock.elapsedRealtime() + 60_000L
            onResult(false, tr("محاولات كثيرة. تم إيقاف PIN لمدة دقيقة", "Too many attempts. PIN is locked for one minute"))
        } else {
            val left = 5 - adminPinFailedAttempts
            onResult(false, tr("PIN الإدارة غير صحيح. متبقي $left محاولات", "Incorrect admin PIN. $left attempts remaining"))
        }
    }

    /**
     * V74 production gate: Firebase Auth is the source of truth for the admin PIN.
     * No fixed verifier or plaintext PIN is shipped inside the APK, and the app never
     * auto-creates the privileged admin account during a normal unlock attempt.
     */
    fun unlockAdmin(pin: String, onResult: (Boolean, String) -> Unit) {
        val cleanPin = pin.trim()
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed < adminPinLockedUntilElapsed) {
            val seconds = ((adminPinLockedUntilElapsed - nowElapsed + 999L) / 1000L).coerceAtLeast(1L)
            onResult(false, tr("محاولات كثيرة. حاول بعد $seconds ثانية", "Too many attempts. Try again in $seconds seconds"))
            return
        }
        if (cleanPin.length != 6 || cleanPin.any { !it.isDigit() }) {
            onResult(false, tr("اكتب PIN الإدارة المكون من 6 أرقام", "Enter the 6-digit admin PIN"))
            return
        }

        viewModelScope.launch {
            val firebasePassword = kotlinx.coroutines.withContext(Dispatchers.Default) {
                derivedAdminFirebasePassword(cleanPin)
            }

            val app = try {
                FirebaseApp.getInstance(ADMIN_UNLOCK_APP)
            } catch (_: IllegalStateException) {
                FirebaseApp.initializeApp(
                    getApplication<Application>(),
                    FirebaseApp.getInstance().options,
                    ADMIN_UNLOCK_APP
                ) ?: run {
                    onResult(false, tr("تعذر تجهيز دخول الإدارة", "Unable to initialize admin access"))
                    return@launch
                }
            }
            adminFirebaseApp = app
            try {
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            } catch (_: Exception) {
                // Already configured for this secondary app.
            }

            val auth = FirebaseAuth.getInstance(app)
            auth.signInWithEmailAndPassword(ADMIN_GATE_EMAIL, firebasePassword)
                .addOnSuccessListener {
                    adminPinFailedAttempts = 0
                    adminPinLockedUntilElapsed = 0L
                    completeAdminUnlock(app, auth, onResult)
                }
                .addOnFailureListener { error ->
                    val message = error.message.orEmpty().lowercase()
                    val looksLikeNetwork = "network" in message || "timeout" in message || "unreachable" in message
                    if (looksLikeNetwork) {
                        _adminUnlocked.value = false
                        onResult(false, tr("تعذر الاتصال للتحقق من PIN. تأكد من الإنترنت وحاول مرة أخرى.", "Unable to verify the PIN. Check the internet and try again."))
                    } else {
                        registerAdminPinFailure(onResult)
                    }
                }
        }
    }

    /**
     * Change the shared six-digit administration PIN after re-authenticating the
     * current PIN. Updating the Firebase admin-gate password makes the new PIN apply
     * to every device on its next admin unlock without storing the PIN in Firestore.
     */
    fun changeAdminPin(
        currentPin: String,
        newPin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val current = currentPin.trim()
        val next = newPin.trim()
        if (!_adminUnlocked.value || adminFirebaseApp == null) {
            onResult(false, tr("افتح الإدارة بالـ PIN أولا", "Unlock administration first"))
            return
        }
        if (current.length != 6 || current.any { !it.isDigit() }) {
            onResult(false, tr("PIN الحالي يجب أن يكون 6 أرقام", "Current PIN must be 6 digits"))
            return
        }
        if (next.length != 6 || next.any { !it.isDigit() }) {
            onResult(false, tr("PIN الجديد يجب أن يكون 6 أرقام", "New PIN must be 6 digits"))
            return
        }
        if (current == next) {
            onResult(false, tr("اختار PIN جديد مختلف عن الحالي", "Choose a new PIN different from the current PIN"))
            return
        }

        viewModelScope.launch {
            val app = adminFirebaseApp ?: run {
                onResult(false, tr("جلسة الإدارة غير متاحة", "Admin session is unavailable"))
                return@launch
            }
            val auth = FirebaseAuth.getInstance(app)
            val currentPassword = kotlinx.coroutines.withContext(Dispatchers.Default) { derivedAdminFirebasePassword(current) }
            val nextPassword = kotlinx.coroutines.withContext(Dispatchers.Default) { derivedAdminFirebasePassword(next) }

            auth.signInWithEmailAndPassword(ADMIN_GATE_EMAIL, currentPassword)
                .addOnSuccessListener {
                    val user = auth.currentUser
                    if (user == null || user.email?.trim()?.lowercase() != ADMIN_GATE_EMAIL) {
                        onResult(false, tr("تعذر التحقق من حساب الإدارة", "Unable to verify the admin account"))
                        return@addOnSuccessListener
                    }
                    user.updatePassword(nextPassword)
                        .addOnSuccessListener {
                            adminPinFailedAttempts = 0
                            adminPinLockedUntilElapsed = 0L
                            onResult(true, tr("تم تغيير PIN الإدارة بنجاح. استخدم الرقم الجديد من الآن.", "Admin PIN changed successfully. Use the new PIN from now on."))
                        }
                        .addOnFailureListener {
                            onResult(false, tr("تعذر تغيير PIN حاليا. تأكد من الإنترنت وأعد المحاولة.", "Unable to change the PIN right now. Check the internet and try again."))
                        }
                }
                .addOnFailureListener {
                    onResult(false, tr("PIN الحالي غير صحيح", "Current PIN is incorrect"))
                }
        }
    }

    fun lockAdmin() {
        try { adminFirebaseApp?.let { FirebaseAuth.getInstance(it).signOut() } } catch (_: Exception) {}
        _adminUnlocked.value = false
        adminFirestore = null
        // PIN sessions belong to staff. The real manager remains authorized by the
        // primary Firebase account, so backgrounding must not wipe manager-only data.
        if (!_actualManager.value) {
            _lab2LabPrices.value = emptyMap()
            managerPricesLoaded = false
        }
    }

    // The authenticated account can remain Abdulrahman while the UI temporarily
    // operates with another user's permissions. This never changes Firebase Auth.
    private val _actualManager = MutableStateFlow(false)
    val actualManager: StateFlow<Boolean> = _actualManager.asStateFlow()

    private val _actingAsUser = MutableStateFlow<AppUserProfile?>(null)
    val actingAsUser: StateFlow<AppUserProfile?> = _actingAsUser.asStateFlow()

    /** null = checking, true = enabled, false = disabled by manager. */
    private val _accountEnabled = MutableStateFlow<Boolean?>(null)
    val accountEnabled: StateFlow<Boolean?> = _accountEnabled.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<AppUserProfile?>(null)
    val currentUserProfile: StateFlow<AppUserProfile?> = _currentUserProfile.asStateFlow()

    /** checking / pending / approved / rejected / revoked / error */
    private val _deviceAccessStatus = MutableStateFlow("checking")
    val deviceAccessStatus: StateFlow<String> = _deviceAccessStatus.asStateFlow()

    private val _authorizedDevices = MutableStateFlow<List<AuthorizedDevice>>(emptyList())
    val authorizedDevices: StateFlow<List<AuthorizedDevice>> = _authorizedDevices.asStateFlow()

    private val _lab2LabPrices = MutableStateFlow<Map<Int, String>>(emptyMap())
    val lab2LabPrices: StateFlow<Map<Int, String>> = _lab2LabPrices.asStateFlow()

    private val _customerPriceOverrides = MutableStateFlow<Map<Int, String>>(emptyMap())
    val customerPriceOverrides: StateFlow<Map<Int, String>> = _customerPriceOverrides.asStateFlow()

    // V113 deep-link target from Android order notifications.
    private val _pendingOpenOrderId = MutableStateFlow<String?>(null)
    val pendingOpenOrderId: StateFlow<String?> = _pendingOpenOrderId.asStateFlow()

    fun requestOpenOrderFromNotification(orderId: String?) {
        _pendingOpenOrderId.value = orderId?.trim()?.takeIf { it.isNotBlank() }
    }

    fun consumePendingOpenOrder() {
        _pendingOpenOrderId.value = null
    }

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _customersLoading = MutableStateFlow(false)
    val customersLoading: StateFlow<Boolean> = _customersLoading.asStateFlow()

    private val _customerOrders = MutableStateFlow<List<CustomerOrder>>(emptyList())
    val customerOrders: StateFlow<List<CustomerOrder>> = _customerOrders.asStateFlow()

    private val _customerOrdersLoading = MutableStateFlow(false)
    val customerOrdersLoading: StateFlow<Boolean> = _customerOrdersLoading.asStateFlow()
    private var customerOrdersListener: ListenerRegistration? = null
    private var customerOrdersCustomerId: String? = null

    // V80: operational worklist for today's lab desk. This is intentionally available
    // to normal active staff without exposing Lab2Lab/admin-only data.
    private val _dailyOrders = MutableStateFlow<List<CustomerOrder>>(emptyList())
    val dailyOrders: StateFlow<List<CustomerOrder>> = _dailyOrders.asStateFlow()

    // V113: clinic-wide order archive used by the dedicated Orders Hub.
    // Unlike the daily desk this list intentionally includes cancelled orders.
    private val _orderArchive = MutableStateFlow<List<CustomerOrder>>(emptyList())
    val orderArchive: StateFlow<List<CustomerOrder>> = _orderArchive.asStateFlow()

    private val _orderArchiveLoading = MutableStateFlow(false)
    val orderArchiveLoading: StateFlow<Boolean> = _orderArchiveLoading.asStateFlow()

    private val _labOrders = MutableStateFlow<List<CustomerOrder>>(emptyList())
    val labOrders: StateFlow<List<CustomerOrder>> = _labOrders.asStateFlow()

    private val _labOrdersLoading = MutableStateFlow(false)
    val labOrdersLoading: StateFlow<Boolean> = _labOrdersLoading.asStateFlow()
    private var labOrdersListener: ListenerRegistration? = null
    private var labRealtimeInitialized = false
    private var clinicOrdersListener: ListenerRegistration? = null
    private var clinicRealtimeInitialized = false
    private var clinicRealtimeOrders: Map<String, CustomerOrder> = emptyMap()

    private val _dailyOrdersLoading = MutableStateFlow(false)
    val dailyOrdersLoading: StateFlow<Boolean> = _dailyOrdersLoading.asStateFlow()

    private val _customerActivity = MutableStateFlow<List<CustomerActivityEntry>>(emptyList())
    val customerActivity: StateFlow<List<CustomerActivityEntry>> = _customerActivity.asStateFlow()

    private val _paymentHistory = MutableStateFlow<List<PaymentEntry>>(emptyList())
    val paymentHistory: StateFlow<List<PaymentEntry>> = _paymentHistory.asStateFlow()

    private val _reportOrders = MutableStateFlow<List<CustomerOrder>>(emptyList())
    val reportOrders: StateFlow<List<CustomerOrder>> = _reportOrders.asStateFlow()

    private val _reportsLoading = MutableStateFlow(false)
    val reportsLoading: StateFlow<Boolean> = _reportsLoading.asStateFlow()

    private val _managerHomeSummary = MutableStateFlow(ReportSummary())
    val managerHomeSummary: StateFlow<ReportSummary> = _managerHomeSummary.asStateFlow()

    private val _managerHomeLoading = MutableStateFlow(false)
    val managerHomeLoading: StateFlow<Boolean> = _managerHomeLoading.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    private val _users = MutableStateFlow<List<AppUserProfile>>(emptyList())
    val users: StateFlow<List<AppUserProfile>> = _users.asStateFlow()

    private val _systemMessage = MutableStateFlow<String?>(null)
    val systemMessage: StateFlow<String?> = _systemMessage.asStateFlow()

    private var managerPricesLoaded = false
    private var customerOverridesLoaded = false
    private var profileListener: ListenerRegistration? = null
    private var deviceAccessListener: ListenerRegistration? = null
    private var deviceRequestInFlight = false
    private var lastAuthUid: String? = null
    private var authenticatedEmail: String = ""
    private var authenticatedUid: String = ""
    private var serverVerifiedProfileUid: String? = null
    private val pendingOfflineAuditKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) { repository.getLabTests() }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collectLatest { online ->
                _isOnline.value = online
                if (online && pendingSyncStore.count.value > 0) {
                    firestore.waitForPendingWrites()
                        .addOnSuccessListener {
                            pendingSyncStore.clearAll()
                            val syncedAt = System.currentTimeMillis()
                            _lastSuccessfulSyncMillis.value = syncedAt
                            settingsStore.markSynced(syncedAt)
                            rebuildAdminAlerts()
                        }
                }
                rebuildAdminAlerts()
            }
        }
    }

    /** V30: manager-facing client health probe for connectivity, Firestore reachability and latency. */
    fun refreshSystemHealth() {
        if (!hasAdminAccess()) return

        val checkedAt = System.currentTimeMillis()
        if (!_isOnline.value) {
            _systemHealth.value = SystemHealthState(
                checking = false,
                firebaseReachable = false,
                latencyMs = null,
                checkedAtMillis = checkedAt,
                message = tr("الجهاز غير متصل بالإنترنت", "Device is offline")
            )
            rebuildAdminAlerts()
            return
        }

        _systemHealth.value = SystemHealthState(
            checking = true,
            firebaseReachable = null,
            latencyMs = null,
            checkedAtMillis = checkedAt,
            message = tr("جار فحص Firebase...", "Checking Firebase...")
        )

        val startedAt = SystemClock.elapsedRealtime()
        privilegedFirestore().collection("users")
            .document(MANAGER_UID)
            .get(Source.SERVER)
            .addOnSuccessListener {
                val latency = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                _systemHealth.value = SystemHealthState(
                    checking = false,
                    firebaseReachable = true,
                    latencyMs = latency,
                    checkedAtMillis = System.currentTimeMillis(),
                    message = tr("Firebase متصل ويستجيب", "Firebase is reachable")
                )
                rebuildAdminAlerts()
            }
            .addOnFailureListener { error ->
                _systemHealth.value = SystemHealthState(
                    checking = false,
                    firebaseReachable = false,
                    latencyMs = null,
                    checkedAtMillis = System.currentTimeMillis(),
                    message = error.localizedMessage?.take(120)
                        ?: tr("تعذر الوصول إلى Firebase", "Could not reach Firebase")
                )
                rebuildAdminAlerts()
            }
    }

    /** V32: scans server/local data for duplicates, arithmetic drift and conflict candidates. */
    fun refreshDataIntegrity() {
        if (!hasAdminAccess()) return
        if (_dataIntegrityState.value.checking) return

        _dataIntegrityState.value = DataIntegrityState(
            checking = true,
            message = tr("جار فحص سلامة البيانات...", "Checking data integrity...")
        )

        fun scanOrders(customersSnapshot: List<Customer>) {
            privilegedFirestore().collectionGroup("orders")
                .limit(INTEGRITY_ORDER_SCAN_LIMIT)
                .get(if (_isOnline.value) Source.SERVER else Source.CACHE)
                .addOnSuccessListener { snapshot ->
                    val issues = mutableListOf<DataIntegrityIssue>()

                    val phoneOwners = mutableMapOf<String, MutableList<Customer>>()
                    customersSnapshot.filterNot { it.isArchived }.forEach { customer ->
                        listOf(normalizePhone(customer.phone), normalizePhone(customer.alternatePhone))
                            .filter { it.isNotBlank() }
                            .distinct()
                            .forEach { phone -> phoneOwners.getOrPut(phone) { mutableListOf() } += customer }
                    }
                    phoneOwners.filterValues { it.map(Customer::id).distinct().size > 1 }.forEach { (phone, owners) ->
                        val names = owners.distinctBy { it.id }.joinToString(" • ") { "${it.name} (${it.fileNumber})" }
                        issues += DataIntegrityIssue(
                            id = "duplicate_phone_$phone",
                            type = "duplicate_customer",
                            titleAr = "رقم موبايل مكرر",
                            titleEn = "Duplicate customer phone",
                            detailsAr = "$phone مسجل لأكثر من عميل: $names",
                            detailsEn = "$phone is linked to more than one customer: $names",
                            severity = "critical"
                        )
                    }

                    val orderNumbers = mutableMapOf<String, MutableList<String>>()
                    val operationIds = mutableMapOf<String, MutableList<String>>()
                    snapshot.documents.forEach { doc ->
                        val orderNumber = doc.getString("order_number").orEmpty()
                        val customerId = doc.getString("customer_id").orEmpty()
                        val operationId = doc.getString("operation_id").orEmpty()
                        if (orderNumber.isNotBlank()) orderNumbers.getOrPut(orderNumber) { mutableListOf() } += doc.id
                        if (operationId.isNotBlank()) operationIds.getOrPut(operationId) { mutableListOf() } += doc.id

                        if (orderNumber.isBlank() || customerId.isBlank()) {
                            issues += DataIntegrityIssue(
                                id = "missing_identity_${doc.id}",
                                type = "missing_identity",
                                titleAr = "طلب ناقص البيانات الأساسية",
                                titleEn = "Order missing core identifiers",
                                detailsAr = "الطلب ${orderNumber.ifBlank { doc.id }} ناقص رقم الطلب أو معرف العميل.",
                                detailsEn = "Order ${orderNumber.ifBlank { doc.id }} is missing an order number or customer id.",
                                severity = "critical",
                                entityId = doc.id,
                                documentPath = doc.reference.path
                            )
                        }

                        val subtotal = numberAsDouble(doc.get("subtotal_customer_price"))
                        val discount = numberAsDouble(doc.get("discount_amount"))
                        val total = numberAsDouble(doc.get("total_customer_price"))
                        val paid = numberAsDouble(doc.get("paid_amount"))
                        val remaining = numberAsDouble(doc.get("remaining_amount"))
                        val expectedTotal = (subtotal - discount).coerceAtLeast(0.0)
                        val moneyMismatch = kotlin.math.abs(expectedTotal - total) > 0.02 ||
                            kotlin.math.abs((paid + remaining) - total) > 0.02 || paid < -0.001 || remaining < -0.001
                        if (moneyMismatch) {
                            issues += DataIntegrityIssue(
                                id = "money_${doc.id}",
                                type = "financial_mismatch",
                                titleAr = "عدم تطابق حسابات طلب",
                                titleEn = "Order totals mismatch",
                                detailsAr = "${orderNumber.ifBlank { doc.id }} • قبل الخصم ${formatNumber(subtotal)} • الخصم ${formatNumber(discount)} • الإجمالي ${formatNumber(total)} • المدفوع ${formatNumber(paid)} • المتبقي ${formatNumber(remaining)}",
                                detailsEn = "${orderNumber.ifBlank { doc.id }} • subtotal ${formatNumber(subtotal)} • discount ${formatNumber(discount)} • total ${formatNumber(total)} • paid ${formatNumber(paid)} • remaining ${formatNumber(remaining)}",
                                severity = "critical",
                                entityId = doc.id,
                                documentPath = doc.reference.path
                            )
                        }

                        val status = doc.getString("payment_status").orEmpty()
                        val expectedStatus = when {
                            remaining <= 0.01 && total > 0.0 -> "paid"
                            paid > 0.01 -> "partial"
                            else -> "unpaid"
                        }
                        if (status.isNotBlank() && status != expectedStatus) {
                            issues += DataIntegrityIssue(
                                id = "payment_status_${doc.id}",
                                type = "payment_status",
                                titleAr = "حالة دفع غير متطابقة",
                                titleEn = "Payment status mismatch",
                                detailsAr = "${orderNumber.ifBlank { doc.id }} حالته $status بينما الأرقام تشير إلى $expectedStatus.",
                                detailsEn = "${orderNumber.ifBlank { doc.id }} is marked $status while its amounts indicate $expectedStatus.",
                                severity = "warning",
                                entityId = doc.id,
                                documentPath = doc.reference.path
                            )
                        }
                    }

                    orderNumbers.filterValues { it.distinct().size > 1 }.forEach { (number, ids) ->
                        issues += DataIntegrityIssue(
                            id = "duplicate_order_$number",
                            type = "duplicate_order",
                            titleAr = "رقم طلب مكرر",
                            titleEn = "Duplicate order number",
                            detailsAr = "رقم الطلب $number موجود في ${ids.distinct().size} سجلات.",
                            detailsEn = "Order number $number exists in ${ids.distinct().size} records.",
                            severity = "critical"
                        )
                    }
                    operationIds.filterValues { it.distinct().size > 1 }.forEach { (operationId, ids) ->
                        issues += DataIntegrityIssue(
                            id = "duplicate_operation_$operationId",
                            type = "duplicate_operation",
                            titleAr = "معرف عملية مكرر",
                            titleEn = "Duplicate operation id",
                            detailsAr = "Operation ID $operationId ظهر في ${ids.distinct().size} طلبات.",
                            detailsEn = "Operation ID $operationId appears in ${ids.distinct().size} orders.",
                            severity = "critical"
                        )
                    }

                    // Detect two different users updating the same customer in a short time window.
                    val updateLogs = _auditLogs.value
                        .filter { it.action.startsWith("customer_update") && it.customerId.isNotBlank() }
                        .groupBy { it.customerId }
                    updateLogs.forEach { (customerId, logs) ->
                        logs.sortedBy { it.createdAtMillis }.zipWithNext().forEach { (a, b) ->
                            if (a.actorUid != b.actorUid && b.createdAtMillis - a.createdAtMillis in 0..CONFLICT_WINDOW_MS) {
                                issues += DataIntegrityIssue(
                                    id = "conflict_${customerId}_${b.createdAtMillis}",
                                    type = "edit_conflict",
                                    titleAr = "تعارض تعديل محتمل",
                                    titleEn = "Possible edit conflict",
                                    detailsAr = "تم تعديل نفس العميل بواسطة ${a.actorEmail} ثم ${b.actorEmail} خلال أقل من دقيقتين. راجع سجل المراجعة.",
                                    detailsEn = "The same customer was edited by ${a.actorEmail} then ${b.actorEmail} within two minutes. Review the audit log.",
                                    severity = "warning",
                                    entityId = customerId
                                )
                            }
                        }
                    }

                    val sortedIssues = issues.distinctBy { it.id }.sortedWith(
                        compareBy<DataIntegrityIssue> { if (it.severity == "critical") 0 else 1 }.thenBy { it.type }
                    )
                    _dataIntegrityIssues.value = sortedIssues
                    _dataIntegrityState.value = DataIntegrityState(
                        checking = false,
                        checkedAtMillis = System.currentTimeMillis(),
                        customersScanned = customersSnapshot.size,
                        ordersScanned = snapshot.documents.size,
                        issueCount = sortedIssues.size,
                        criticalCount = sortedIssues.count { it.severity == "critical" },
                        message = if (sortedIssues.isEmpty())
                            tr("لم يتم اكتشاف مشاكل في البيانات المفحوصة", "No issues were found in the scanned data")
                        else tr("تم اكتشاف ${sortedIssues.size} ملاحظة تحتاج مراجعة", "${sortedIssues.size} finding(s) need review")
                    )
                    rebuildAdminAlerts()
                }
                .addOnFailureListener { error ->
                    _dataIntegrityState.value = DataIntegrityState(
                        checking = false,
                        checkedAtMillis = System.currentTimeMillis(),
                        customersScanned = customersSnapshot.size,
                        message = error.localizedMessage?.take(140)
                            ?: tr("تعذر فحص الطلبات", "Unable to scan orders")
                    )
                }
        }

        // Ensure the conflict detector has recent audit data, then scan customers/orders.
        loadAuditLogs { _, _ ->
            if (_customers.value.isEmpty() && _isOnline.value) {
                loadCustomers { _, _ -> scanOrders(_customers.value) }
            } else {
                scanOrders(_customers.value)
            }
        }
    }

    /** V32 safe repair: recalculates order totals/status only; never deletes customer/order data. */
    fun repairIntegrityIssue(issue: DataIntegrityIssue, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإصلاح متاح للمدير فقط", "Repair is manager only"))
            return
        }
        if (issue.documentPath.isBlank() || issue.type !in setOf("financial_mismatch", "payment_status")) {
            onResult(false, tr("هذه الملاحظة تحتاج مراجعة يدوية", "This finding requires manual review"))
            return
        }
        val ref = privilegedFirestore().document(issue.documentPath)
        ref.get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(false, tr("السجل لم يعد موجودا", "Record no longer exists"))
                    return@addOnSuccessListener
                }
                val subtotal = numberAsDouble(doc.get("subtotal_customer_price")).coerceAtLeast(0.0)
                val discount = numberAsDouble(doc.get("discount_amount")).coerceIn(0.0, subtotal)
                val total = (subtotal - discount).coerceAtLeast(0.0)
                val paid = numberAsDouble(doc.get("paid_amount")).coerceIn(0.0, total)
                val remaining = (total - paid).coerceAtLeast(0.0)
                val status = when {
                    remaining <= 0.01 && total > 0.0 -> "paid"
                    paid > 0.01 -> "partial"
                    else -> "unpaid"
                }
                ref.set(
                    mapOf(
                        "discount_amount" to discount,
                        "discount_percent" to if (subtotal > 0.0) discount / subtotal * 100.0 else 0.0,
                        "total_customer_price" to total,
                        "paid_amount" to paid,
                        "remaining_amount" to remaining,
                        "payment_status" to status,
                        "updated_at_ms" to System.currentTimeMillis(),
                        "updated_at" to FieldValue.serverTimestamp(),
                        "updated_by_uid" to currentUid(),
                        "updated_by_email" to currentEmail(),
                        "integrity_repaired" to true
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    logAudit(
                        "integrity_repair",
                        "order",
                        issue.entityId,
                        "Data Integrity Repair",
                        "Recalculated totals and payment status without deleting data.",
                        orderId = issue.entityId
                    )
                    onResult(true, tr("تم الإصلاح الآمن وإعادة حساب الأرقام", "Safe repair completed and totals recalculated"))
                    refreshDataIntegrity()
                }.addOnFailureListener {
                    onResult(false, tr("تعذر حفظ الإصلاح", "Could not save repair"))
                }
            }
            .addOnFailureListener {
                onResult(false, tr("تعذر تحميل السجل من السيرفر", "Could not load the record from server"))
            }
    }

    /** V31: manager alert center derived from existing audit/device/health/debt data.
     *  Read state stays local to the manager device, so no new Firestore collection or rules are required. */
    fun refreshAdminAlerts() {
        if (!hasAdminAccess()) return
        rebuildAdminAlerts()
        if (_isOnline.value) {
            refreshSystemHealth()
            loadAuthorizedDevices { _, _ -> rebuildAdminAlerts() }
            loadAuditLogs { _, _ -> rebuildAdminAlerts() }
            loadManagerHomeSummary { _, _ -> rebuildAdminAlerts() }
        }
    }

    fun markAdminAlertRead(alertId: String) {
        if (alertId.isBlank()) return
        val readIds = adminAlertPrefs.getStringSet("read_ids", emptySet()).orEmpty().toMutableSet()
        readIds += alertId
        adminAlertPrefs.edit().putStringSet("read_ids", readIds).apply()
        rebuildAdminAlerts()
    }

    fun markAllAdminAlertsRead() {
        val readIds = adminAlertPrefs.getStringSet("read_ids", emptySet()).orEmpty().toMutableSet()
        readIds += _adminAlerts.value.map { it.id }
        adminAlertPrefs.edit().putStringSet("read_ids", readIds).apply()
        rebuildAdminAlerts()
    }

    private fun rebuildAdminAlerts() {
        if (!hasAdminAccess()) {
            _adminAlerts.value = emptyList()
            _unreadAdminAlertCount.value = 0
            return
        }

        val now = System.currentTimeMillis()
        val readIds = adminAlertPrefs.getStringSet("read_ids", emptySet()).orEmpty()
        val alerts = mutableListOf<AdminAlert>()

        if (!_isOnline.value) {
            alerts += AdminAlert(
                id = "system_offline_${now / 3_600_000L}",
                category = "system",
                titleAr = "التطبيق يعمل بدون إنترنت",
                titleEn = "App is working offline",
                detailsAr = "العمليات الجديدة ستظل معلقة حتى عودة الاتصال ثم تتم مزامنتها تلقائيا.",
                detailsEn = "New operations will remain pending until connectivity returns, then sync automatically.",
                severity = "critical",
                createdAtMillis = now
            )
        }

        val pending = pendingSyncStore.count.value
        if (pending > 0) {
            alerts += AdminAlert(
                id = "pending_sync_${now / 3_600_000L}",
                category = "sync",
                titleAr = "عمليات في انتظار المزامنة",
                titleEn = "Operations waiting to sync",
                detailsAr = "يوجد $pending عملية لم تصل للسيرفر بعد.",
                detailsEn = "$pending operation(s) have not reached the server yet.",
                severity = if (pending >= 10) "critical" else "warning",
                createdAtMillis = now
            )
        }

        val integrity = _dataIntegrityState.value
        if (!integrity.checking && integrity.criticalCount > 0) {
            alerts += AdminAlert(
                id = "integrity_${integrity.checkedAtMillis}",
                category = "integrity",
                titleAr = "مشاكل في سلامة البيانات",
                titleEn = "Data integrity issues detected",
                detailsAr = "تم اكتشاف ${integrity.criticalCount} مشكلة مهمة من إجمالي ${integrity.issueCount} ملاحظة. افتح Data Integrity للمراجعة.",
                detailsEn = "${integrity.criticalCount} critical issue(s) were found out of ${integrity.issueCount} finding(s). Open Data Integrity for review.",
                severity = "critical",
                createdAtMillis = integrity.checkedAtMillis
            )
        }

        val health = _systemHealth.value
        if (health.firebaseReachable == false && _isOnline.value) {
            alerts += AdminAlert(
                id = "firebase_unreachable_${health.checkedAtMillis / 3_600_000L}",
                category = "system",
                titleAr = "تعذر الوصول إلى Firebase",
                titleEn = "Firebase is unreachable",
                detailsAr = health.message.ifBlank { "اتصال الإنترنت موجود لكن Firebase لم يستجب للفحص." },
                detailsEn = health.message.ifBlank { "Internet is available but Firebase did not respond to the health check." },
                severity = "critical",
                createdAtMillis = health.checkedAtMillis.takeIf { it > 0L } ?: now
            )
        } else if ((health.latencyMs ?: 0L) >= 1_200L) {
            alerts += AdminAlert(
                id = "firebase_slow_${health.checkedAtMillis / 3_600_000L}",
                category = "performance",
                titleAr = "استجابة Firebase بطيئة",
                titleEn = "Firebase response is slow",
                detailsAr = "زمن الاستجابة الحالي ${health.latencyMs} ms.",
                detailsEn = "Current response latency is ${health.latencyMs} ms.",
                severity = "warning",
                createdAtMillis = health.checkedAtMillis.takeIf { it > 0L } ?: now
            )
        }

        _authorizedDevices.value.filter { it.status == "pending" }.forEach { device ->
            alerts += AdminAlert(
                id = "device_pending_${device.uid}_${device.id}",
                category = "device",
                titleAr = "جهاز جديد ينتظر الموافقة",
                titleEn = "New device waiting for approval",
                detailsAr = "${device.email.ifBlank { device.uid }} • ${device.manufacturer} ${device.model}",
                detailsEn = "${device.email.ifBlank { device.uid }} • ${device.manufacturer} ${device.model}",
                severity = "critical",
                createdAtMillis = device.requestedAtMillis.takeIf { it > 0L } ?: now,
                actorEmail = device.email
            )
        }

        val recentCutoff = now - 7L * 24L * 60L * 60L * 1000L
        _auditLogs.value.filter { it.createdAtMillis >= recentCutoff }.forEach { log ->
            if (log.wasOffline) {
                alerts += AdminAlert(
                    id = "offline_audit_${log.id}",
                    category = "sync",
                    titleAr = "عملية تمت بدون إنترنت وتمت مزامنتها",
                    titleEn = "Offline operation synchronized",
                    detailsAr = "${log.title} • ${log.actorEmail}",
                    detailsEn = "${log.title} • ${log.actorEmail}",
                    severity = "info",
                    createdAtMillis = log.syncedAtMillis.takeIf { it > 0L } ?: log.createdAtMillis,
                    actorEmail = log.actorEmail
                )
            }

            val importantAction = log.action.contains("blacklist", true) ||
                log.action.contains("archive", true) ||
                log.action.contains("void", true) ||
                log.action.contains("revoked", true) ||
                log.action.contains("rejected", true) ||
                log.action.contains("disabled", true)
            if (importantAction) {
                alerts += AdminAlert(
                    id = "important_audit_${log.id}",
                    category = "audit",
                    titleAr = "عملية إدارية مهمة: ${log.title}",
                    titleEn = "Important admin action: ${log.title}",
                    detailsAr = log.details.ifBlank { log.actorEmail },
                    detailsEn = log.details.ifBlank { log.actorEmail },
                    severity = "critical",
                    createdAtMillis = log.createdAtMillis,
                    actorEmail = log.actorEmail
                )
            }
        }

        val oneHourAgo = now - 60L * 60L * 1000L
        _auditLogs.value
            .filter { it.createdAtMillis >= oneHourAgo && it.actorEmail.isNotBlank() }
            .groupBy { it.actorEmail.lowercase() }
            .filterValues { it.size >= HIGH_ACTIVITY_ALERT_COUNT }
            .forEach { (actor, logs) ->
                alerts += AdminAlert(
                    id = "high_activity_${actor.hashCode()}_${now / 3_600_000L}",
                    category = "activity",
                    titleAr = "نشاط مرتفع لمستخدم",
                    titleEn = "High user activity",
                    detailsAr = "$actor نفذ ${logs.size} عملية خلال آخر ساعة.",
                    detailsEn = "$actor performed ${logs.size} operations in the last hour.",
                    severity = "warning",
                    createdAtMillis = logs.maxOfOrNull { it.createdAtMillis } ?: now,
                    actorEmail = actor
                )
            }

        val debt = _managerHomeSummary.value.remaining
        if (debt >= LARGE_DEBT_ALERT) {
            alerts += AdminAlert(
                id = "debt_${(debt / 1000.0).toInt()}_${now / 86_400_000L}",
                category = "debt",
                titleAr = "إجمالي مديونيات يحتاج متابعة",
                titleEn = "Outstanding debt needs attention",
                detailsAr = "إجمالي المبالغ المتبقية حاليا ${"%.2f".format(Locale.US, debt)} EGP.",
                detailsEn = "Current outstanding balance is ${"%.2f".format(Locale.US, debt)} EGP.",
                severity = if (debt >= CRITICAL_DEBT_ALERT) "critical" else "warning",
                createdAtMillis = now
            )
        }

        val finalAlerts = alerts
            .distinctBy { it.id }
            .map { it.copy(isRead = readIds.contains(it.id)) }
            .sortedWith(
                compareBy<AdminAlert> { it.isRead }
                    .thenBy { if (it.severity == "critical") 0 else if (it.severity == "warning") 1 else 2 }
                    .thenByDescending { it.createdAtMillis }
            )

        _adminAlerts.value = finalAlerts
        _unreadAdminAlertCount.value = finalAlerts.count { !it.isRead }
    }

    override fun onCleared() {
        lockAdmin()
        profileListener?.remove()
        deviceAccessListener?.remove()
        labOrdersListener?.remove()
        clinicOrdersListener?.remove()
        customerOrdersListener?.remove()
        connectivityMonitor.close()
        super.onCleared()
    }

    /** Called whenever Firebase Auth user changes. */
    fun onAuthenticatedUserChanged(email: String?, uid: String? = null) {
        settingsStore.setActiveProfile(uid ?: email?.trim()?.lowercase())

        if (email.isNullOrBlank() && uid.isNullOrBlank()) {
            // Explicit Firebase logout clears the remembered acting-user session.
            clearPersistedActingUser()
            lastAuthUid = null
            authenticatedEmail = ""
            authenticatedUid = ""
            serverVerifiedProfileUid = null
            profileListener?.remove()
            profileListener = null
            deviceAccessListener?.remove()
            deviceAccessListener = null
            labOrdersListener?.remove()
            labOrdersListener = null
            labRealtimeInitialized = false
            clinicOrdersListener?.remove()
            clinicOrdersListener = null
            clinicRealtimeInitialized = false
            clinicRealtimeOrders = emptyMap()
            customerOrdersListener?.remove()
            customerOrdersListener = null
            customerOrdersCustomerId = null
            _customerOrders.value = emptyList()
            _labOrders.value = emptyList()
            deviceRequestInFlight = false
            _deviceAccessStatus.value = "checking"
            _authorizedDevices.value = emptyList()
            _adminAlerts.value = emptyList()
            _unreadAdminAlertCount.value = 0
            lockAdmin()
            _actualManager.value = false
            _actingAsUser.value = null
            _isManager.value = false
            _accountEnabled.value = null
            _currentUserProfile.value = null
            _offlineGraceAccess.value = false
            clearSensitiveRuntimeData()
            return
        }

        val cleanEmail = email.orEmpty().trim().lowercase()
        val cleanUid = uid.orEmpty()
        val identityChanged = authenticatedUid != cleanUid || authenticatedEmail != cleanEmail
        authenticatedEmail = cleanEmail
        authenticatedUid = cleanUid
        OrderNotificationManager.registerCurrentToken()

        val manager = isManagerAccount(email, uid)
        _actualManager.value = manager

        if (identityChanged) {
            lockAdmin()
            _actingAsUser.value = null
            _offlineGraceAccess.value = false
            serverVerifiedProfileUid = null
        }

        if (manager) {
            // V85 stability: the fixed primary manager account must always be able to open
            // the lab after successful Firebase authentication. Staff devices remain subject
            // to explicit device approval. This avoids a Firestore/App Check delay trapping
            // the owner on the authorization gate during real lab work.
            clearPersistedActingUser()
            _actingAsUser.value = null
            _isManager.value = false
            _currentUserProfile.value = managerProfile()
            _accountEnabled.value = true
            clearManagerPriceCache()
            ensureManagerProfile(email, uid)
            if (uid.isNullOrBlank()) {
                _deviceAccessStatus.value = "error"
                return
            }
            lastAuthUid = uid
            deviceAccessListener?.remove()
            deviceAccessListener = null
            _deviceAccessStatus.value = "approved"
            loadAfterAccessGranted()
            loadManagerPriceState()
            return
        }

        _actingAsUser.value = null
        clearPersistedActingUser()
        _isManager.value = false
        clearManagerPriceCache()
        if (uid.isNullOrBlank()) {
            _deviceAccessStatus.value = "error"
            _accountEnabled.value = false
            _currentUserProfile.value = null
            clearSensitiveRuntimeData()
            return
        }

        if (lastAuthUid != uid || profileListener == null) {
            _accountEnabled.value = null
            _currentUserProfile.value = null
            _deviceAccessStatus.value = "checking"
            deviceAccessListener?.remove()
            deviceAccessListener = null
            clearSensitiveRuntimeData()
            lastAuthUid = uid
            attachUserProfileListener(email.orEmpty(), uid)
        }
    }

    private fun currentDeviceId(): String {
        val raw = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty().ifBlank { "unknown-android-id" }
        val material = "$raw|${getApplication<Application>().packageName}"
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    /**
     * V127: short, device-bound outage grace. This is used only while the device is
     * offline and only if the same account + device were previously verified by
     * live Firestore as enabled/approved. The encrypted vault expires after 72h.
     */
    private fun restoreOfflineApprovedAccess(email: String, uid: String): Boolean {
        if (_isOnline.value || uid.isBlank()) return false
        val profile = OfflineAccessVault.loadApprovedSession(
            getApplication<Application>(),
            uid,
            currentDeviceId()
        ) ?: return false
        _currentUserProfile.value = profile
        _accountEnabled.value = true
        _deviceAccessStatus.value = "approved"
        _offlineGraceAccess.value = true
        settingsStore.applyRemotePinReset(profile.pinResetRequestedAtMillis)
        loadAfterAccessGranted()
        return true
    }

    private fun attachDeviceAuthorization(email: String, uid: String) {
        deviceAccessListener?.remove()
        deviceRequestInFlight = false
        _deviceAccessStatus.value = "checking"

        val deviceId = currentDeviceId()
        val ref = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(DEVICES_SUBCOLLECTION)
            .document(deviceId)

        deviceAccessListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (restoreOfflineApprovedAccess(email, uid)) return@addSnapshotListener
                _deviceAccessStatus.value = "error"
                _offlineGraceAccess.value = false
                clearSensitiveRuntimeData()
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                val cacheMissDuringOutage = !_isOnline.value && snapshot?.metadata?.isFromCache == true
                if (cacheMissDuringOutage && restoreOfflineApprovedAccess(email, uid)) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.isFromCache) {
                    OfflineAccessVault.clear(getApplication<Application>(), uid, deviceId)
                }
                _deviceAccessStatus.value = "pending"
                _offlineGraceAccess.value = false
                clearSensitiveRuntimeData()
                if (!deviceRequestInFlight && _accountEnabled.value == true) {
                    deviceRequestInFlight = true
                    val now = System.currentTimeMillis()
                    val data = mapOf(
                        "uid" to uid,
                        "email" to email.trim().lowercase(),
                        "device_id" to deviceId,
                        "manufacturer" to Build.MANUFACTURER.orEmpty(),
                        "model" to Build.MODEL.orEmpty(),
                        "android_version" to Build.VERSION.RELEASE.orEmpty(),
                        "status" to "pending",
                        "requested_at_ms" to now,
                        "requested_at" to FieldValue.serverTimestamp(),
                        "updated_at_ms" to now,
                        "updated_at" to FieldValue.serverTimestamp()
                    )
                    ref.set(data)
                        .addOnSuccessListener { ShadowBackupReplicator.mirrorPath(ref.path, now) }
                        .addOnFailureListener {
                            deviceRequestInFlight = false
                            if (!restoreOfflineApprovedAccess(email, uid)) {
                                _deviceAccessStatus.value = "error"
                                clearSensitiveRuntimeData()
                            }
                        }
                }
                return@addSnapshotListener
            }

            deviceRequestInFlight = false
            ShadowBackupReplicator.mirrorSnapshot(snapshot)
            val status = snapshot.getString("status") ?: "pending"
            _deviceAccessStatus.value = status
            if (status == "approved" && _accountEnabled.value == true) {
                val profile = _currentUserProfile.value
                if (!snapshot.metadata.isFromCache && profile != null && profile.enabled && serverVerifiedProfileUid == uid) {
                    OfflineAccessVault.saveApprovedSession(
                        getApplication<Application>(),
                        profile,
                        deviceId
                    )
                    _offlineGraceAccess.value = false
                }
                loadAfterAccessGranted()
                if (_actualManager.value) loadManagerPriceState()
            } else {
                if (!snapshot.metadata.isFromCache) {
                    OfflineAccessVault.clear(getApplication<Application>(), uid, deviceId)
                    _offlineGraceAccess.value = false
                }
                clearSensitiveRuntimeData()
            }
        }
    }

    fun recheckDeviceAuthorization() {
        if (authenticatedUid.isBlank()) {
            _deviceAccessStatus.value = "error"
            return
        }
        attachDeviceAuthorization(authenticatedEmail, authenticatedUid)
    }

    fun approveCurrentDeviceWithAdminPin(
        pin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (authenticatedUid.isBlank()) {
            onResult(false, tr("تعذر تحديد المستخدم الحالي", "Unable to identify the current user"))
            return
        }
        unlockAdmin(pin) { unlocked, message ->
            if (!unlocked) {
                onResult(false, message)
                return@unlockAdmin
            }
            val now = System.currentTimeMillis()
            val deviceId = currentDeviceId()
            val ref = privilegedFirestore().collection(USERS_COLLECTION)
                .document(authenticatedUid)
                .collection(DEVICES_SUBCOLLECTION)
                .document(deviceId)

            ref.get(Source.SERVER)
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        lockAdmin()
                        onResult(false, tr("اضغط إعادة التحقق أولا ثم حاول اعتماد الجهاز", "Check again first, then approve the device"))
                        return@addOnSuccessListener
                    }
                    ref.set(
                        mapOf(
                            "status" to "approved",
                            "approved_at_ms" to now,
                            "approved_at" to FieldValue.serverTimestamp(),
                            "approved_by_uid" to MANAGER_UID,
                            "updated_at_ms" to now,
                            "updated_at" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).addOnSuccessListener {
                        ShadowBackupReplicator.mirrorPath(ref.path, now)
                        lockAdmin()
                        attachDeviceAuthorization(authenticatedEmail, authenticatedUid)
                        onResult(true, tr("تم اعتماد الجهاز", "Device approved"))
                    }.addOnFailureListener {
                        lockAdmin()
                        onResult(false, tr("تعذر اعتماد الجهاز", "Unable to approve device"))
                    }
                }
                .addOnFailureListener {
                    lockAdmin()
                    onResult(false, tr("تعذر قراءة طلب الجهاز", "Unable to read the device request"))
                }
        }
    }

    fun loadAuthorizedDevices(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasAdminAccess()) {
            onResult?.invoke(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        privilegedFirestore().collectionGroup(DEVICES_SUBCOLLECTION)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    AuthorizedDevice(
                        id = doc.id,
                        uid = uid,
                        email = doc.getString("email").orEmpty(),
                        manufacturer = doc.getString("manufacturer").orEmpty(),
                        model = doc.getString("model").orEmpty(),
                        status = doc.getString("status") ?: "pending",
                        requestedAtMillis = doc.getLong("requested_at_ms") ?: 0L,
                        approvedAtMillis = doc.getLong("approved_at_ms") ?: 0L,
                        approvedByUid = doc.getString("approved_by_uid").orEmpty()
                    )
                }.sortedWith(
                    compareBy<AuthorizedDevice> {
                        when (it.status) {
                            "pending" -> 0
                            "approved" -> 1
                            else -> 2
                        }
                    }.thenByDescending { it.requestedAtMillis }
                )
                _authorizedDevices.value = list
                rebuildAdminAlerts()
                onResult?.invoke(true, tr("تم تحديث الأجهزة", "Devices refreshed"))
            }
            .addOnFailureListener {
                onResult?.invoke(false, tr("تعذر تحميل الأجهزة. تأكد من Firestore Rules والإنترنت", "Unable to load devices"))
            }
    }

    fun approveDevice(device: AuthorizedDevice, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val devicesRef = privilegedFirestore().collection(USERS_COLLECTION)
            .document(device.uid)
            .collection(DEVICES_SUBCOLLECTION)

        devicesRef.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                privilegedFirestore().runBatch { batch ->
                    snapshot.documents.forEach { doc ->
                        if (doc.id != device.id && doc.getString("status") == "approved") {
                            batch.update(
                                doc.reference,
                                mapOf(
                                    "status" to "revoked",
                                    "updated_at_ms" to now,
                                    "updated_at" to FieldValue.serverTimestamp(),
                                    "revoked_by_uid" to authenticatedUid
                                )
                            )
                        }
                    }
                    batch.set(
                        devicesRef.document(device.id),
                        mapOf(
                            "status" to "approved",
                            "approved_at_ms" to now,
                            "approved_at" to FieldValue.serverTimestamp(),
                            "approved_by_uid" to authenticatedUid,
                            "updated_at_ms" to now,
                            "updated_at" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }.addOnSuccessListener {
                    snapshot.documents.forEach { ShadowBackupReplicator.mirrorPath(it.reference.path, now) }
                    ShadowBackupReplicator.mirrorPath(devicesRef.document(device.id).path, now)
                    logAudit(
                        action = "device_approved",
                        entityType = "device",
                        entityId = device.id,
                        title = "اعتماد جهاز مستخدم",
                        details = "${device.email} • ${device.manufacturer} ${device.model}"
                    )
                    loadAuthorizedDevices()
                    onResult(true, "تم اعتماد الجهاز وإلغاء أي جهاز قديم لنفس المستخدم")
                }.addOnFailureListener {
                    onResult(false, tr("تعذر اعتماد الجهاز", "Unable to approve device"))
                }
            }
            .addOnFailureListener {
                onResult(false, "تعذر قراءة أجهزة المستخدم")
            }
    }

    fun setDeviceStatus(device: AuthorizedDevice, status: String, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val safeStatus = if (status in setOf("rejected", "revoked")) status else "revoked"
        val now = System.currentTimeMillis()
        privilegedFirestore().collection(USERS_COLLECTION)
            .document(device.uid)
            .collection(DEVICES_SUBCOLLECTION)
            .document(device.id)
            .set(
                mapOf(
                    "status" to safeStatus,
                    "updated_at_ms" to now,
                    "updated_at" to FieldValue.serverTimestamp(),
                    "updated_by_uid" to authenticatedUid
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                ShadowBackupReplicator.mirrorPath("users/${device.uid}/devices/${device.id}", now)
                logAudit(
                    action = "device_$safeStatus",
                    entityType = "device",
                    entityId = device.id,
                    title = if (safeStatus == "rejected") "رفض جهاز" else "إلغاء اعتماد جهاز",
                    details = "${device.email} • ${device.manufacturer} ${device.model}"
                )
                loadAuthorizedDevices()
                onResult(
                    true,
                    if (safeStatus == "rejected") "تم رفض الجهاز" else "تم إلغاء اعتماد الجهاز"
                )
            }
            .addOnFailureListener {
                onResult(false, tr("تعذر تحديث حالة الجهاز", "Unable to update device status"))
            }
    }

    private fun persistActingUser(profile: AppUserProfile) {
        switchSessionPrefs.edit()
            .putString("owner_uid", authenticatedUid)
            .putString("uid", profile.uid)
            .putString("email", profile.email)
            .putString("display_name", profile.displayName)
            .putString("role", profile.role)
            .putBoolean("enabled", profile.enabled)
            .putBoolean("can_edit_customers", profile.canEditCustomers)
            .putBoolean("can_discount", profile.canDiscount)
            .putBoolean("can_collect_payments", profile.canCollectPayments)
            .putBoolean("can_view_sales_reports", profile.canViewSalesReports)
            .putLong("created_at_ms", profile.createdAtMillis)
            .putLong("updated_at_ms", profile.updatedAtMillis)
            .putLong("pin_reset_requested_at_ms", profile.pinResetRequestedAtMillis)
            .apply()
    }

    private fun restorePersistedActingUser(): AppUserProfile? {
        val ownerUid = switchSessionPrefs.getString("owner_uid", "").orEmpty()
        if (authenticatedUid.isBlank() || ownerUid != authenticatedUid) return null
        val uid = switchSessionPrefs.getString("uid", "").orEmpty()
        if (uid.isBlank()) return null
        val profile = AppUserProfile(
            uid = uid,
            email = switchSessionPrefs.getString("email", "").orEmpty(),
            displayName = switchSessionPrefs.getString("display_name", "").orEmpty(),
            role = switchSessionPrefs.getString("role", "staff") ?: "staff",
            enabled = switchSessionPrefs.getBoolean("enabled", false),
            canEditCustomers = switchSessionPrefs.getBoolean("can_edit_customers", false),
            canDiscount = switchSessionPrefs.getBoolean("can_discount", false),
            canCollectPayments = switchSessionPrefs.getBoolean("can_collect_payments", false),
            canViewSalesReports = switchSessionPrefs.getBoolean("can_view_sales_reports", false),
            createdAtMillis = switchSessionPrefs.getLong("created_at_ms", 0L),
            updatedAtMillis = switchSessionPrefs.getLong("updated_at_ms", 0L),
            pinResetRequestedAtMillis = switchSessionPrefs.getLong("pin_reset_requested_at_ms", 0L)
        )
        return profile.takeIf { it.enabled && !isManagerAccount(it.email, it.uid) }
    }

    private fun clearPersistedActingUser() {
        switchSessionPrefs.edit().clear().apply()
    }

    private fun managerProfile(): AppUserProfile = AppUserProfile(
        uid = authenticatedUid.ifBlank { MANAGER_UID },
        email = authenticatedEmail.ifBlank { MANAGER_EMAIL },
        displayName = "Abdelrahman",
        role = "super_admin",
        enabled = true,
        canEditCustomers = true,
        canDiscount = true,
        canCollectPayments = true,
        canViewSalesReports = true
    )

    private fun clearManagerPriceCache() {
        _lab2LabPrices.value = emptyMap()
        managerPricesLoaded = false
    }

    /** V116: only the real Abdelrahman account may switch into another enabled user. */
    fun switchToUser(profile: AppUserProfile, onResult: (Boolean, String) -> Unit) {
        if (!_actualManager.value || !isManagerAccount(authenticatedEmail, authenticatedUid)) {
            onResult(false, tr("تبديل المستخدم متاح لعبد الرحمن فقط", "User switching is available to Abdelrahman only"))
            return
        }
        if (!profile.enabled) {
            onResult(false, tr("لا يمكن الدخول بحساب موقوف", "Disabled users cannot be impersonated"))
            return
        }
        if (isManagerAccount(profile.email, profile.uid) || profile.uid == authenticatedUid) {
            onResult(false, tr("لا يمكن التبديل إلى حساب عبد الرحمن", "Cannot switch into the Abdelrahman account"))
            return
        }

        // Never carry an unlocked administration session into impersonation mode.
        lockAdmin()
        stopClinicOrderNotificationsRealtime()
        stopLabOrdersRealtime()
        _selectedTests.value = emptyList()
        _recognizedTests.value = emptyList()
        _searchQuery.value = ""
        _uiState.value = SearchUiState.EmptyQuery
        _actingAsUser.value = profile
        _isManager.value = false
        clearManagerPriceCache()

        logAudit(
            action = "manager_switch_user",
            entityType = "user",
            entityId = profile.uid,
            title = "تبديل مستخدم",
            details = "Abdelrahman -> ${profile.displayName.ifBlank { profile.email }} • ${normalizeUserRole(profile.role)}"
        )
        onResult(true, tr("تم الدخول بصلاحيات ${profile.displayName.ifBlank { profile.email }}", "Now operating with ${profile.displayName.ifBlank { profile.email }} permissions"))
    }

    /** V116: the return path exists only for the real manager after he started impersonation. */
    fun returnToManagerMode(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!_actualManager.value) {
            onResult?.invoke(false, tr("هذا الإجراء متاح لعبد الرحمن فقط", "This action is available to Abdelrahman only"))
            return
        }
        val previous = _actingAsUser.value
        if (previous == null) {
            onResult?.invoke(true, tr("أنت بالفعل على حساب عبد الرحمن", "You are already using the Abdelrahman account"))
            return
        }

        logAudit(
            action = "manager_return_self",
            entityType = "user",
            entityId = previous.uid,
            title = "العودة لحساب عبد الرحمن",
            details = "رجوع من ${previous.displayName.ifBlank { previous.email }}"
        )
        _actingAsUser.value = null
        clearPersistedActingUser()
        _isManager.value = false
        _selectedTests.value = emptyList()
        _recognizedTests.value = emptyList()
        _searchQuery.value = ""
        _uiState.value = SearchUiState.EmptyQuery
        clearManagerPriceCache()
        loadManagerPriceState()
        onResult?.invoke(true, tr("تم الرجوع لحساب عبد الرحمن", "Returned to Abdelrahman"))
    }

    private fun loadAfterAccessGranted() {
        if (!hasOperationalAccess()) return
        val labOnly = normalizeUserRole(operationalProfile()?.role.orEmpty()) == "lab_operator"
        if (labOnly) {
            _customerPriceOverrides.value = emptyMap()
            _customers.value = emptyList()
            customerOverridesLoaded = false
            return
        }
        if (!customerOverridesLoaded) loadCustomerPriceOverrides()
        if (_customers.value.isEmpty()) loadCustomers()
    }

    private fun ensureManagerProfile(email: String?, uid: String?) {
        if (uid.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        val data = mapOf(
            "email" to email.orEmpty().trim().lowercase(),
            "display_name" to "Abdelrahman",
            "role" to "super_admin",
            "enabled" to true,
            "can_edit_customers" to true,
            "can_discount" to true,
            "can_collect_payments" to true,
            "can_view_sales_reports" to true,
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp()
        )
        firestore.collection(USERS_COLLECTION).document(uid).set(data, SetOptions.merge())
    }

    private fun attachUserProfileListener(email: String, uid: String) {
        profileListener?.remove()
        val ref = firestore.collection(USERS_COLLECTION).document(uid)

        // V82 production policy: access is fail-closed.
        // A Firebase Auth account is not enough; the manager must provision an enabled /users/{uid} profile.
        profileListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (restoreOfflineApprovedAccess(email, uid)) return@addSnapshotListener
                _accountEnabled.value = false
                _currentUserProfile.value = null
                deviceAccessListener?.remove()
                deviceAccessListener = null
                _deviceAccessStatus.value = "error"
                _offlineGraceAccess.value = false
                clearSensitiveRuntimeData()
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                val cacheMissDuringOutage = !_isOnline.value && snapshot?.metadata?.isFromCache == true
                if (cacheMissDuringOutage && restoreOfflineApprovedAccess(email, uid)) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.isFromCache) {
                    serverVerifiedProfileUid = null
                    OfflineAccessVault.clear(getApplication<Application>(), uid, currentDeviceId())
                }
                _accountEnabled.value = false
                _currentUserProfile.value = null
                deviceAccessListener?.remove()
                deviceAccessListener = null
                _deviceAccessStatus.value = "profile_missing"
                _offlineGraceAccess.value = false
                clearSensitiveRuntimeData()
                return@addSnapshotListener
            }

            val profile = parseUserProfile(snapshot)
            ShadowBackupReplicator.mirrorSnapshot(snapshot)
            _currentUserProfile.value = profile
            _accountEnabled.value = profile.enabled
            settingsStore.applyRemotePinReset(profile.pinResetRequestedAtMillis)
            if (!snapshot.metadata.isFromCache) {
                _offlineGraceAccess.value = false
                serverVerifiedProfileUid = if (profile.enabled) uid else null
            }

            if (profile.enabled) {
                attachDeviceAuthorization(email, uid)
            } else {
                if (!snapshot.metadata.isFromCache) {
                    OfflineAccessVault.clear(getApplication<Application>(), uid, currentDeviceId())
                }
                deviceAccessListener?.remove()
                deviceAccessListener = null
                _deviceAccessStatus.value = "checking"
                clearSensitiveRuntimeData()
            }
        }
    }

    private fun defaultStaffProfile(uid: String, email: String): AppUserProfile {
        val permissions = permissionsForRole("staff")
        return AppUserProfile(
            uid = uid,
            email = email,
            displayName = email.substringBefore('@').ifBlank { "مستخدم" },
            role = "staff",
            enabled = false,
            canEditCustomers = permissions.canEditCustomers,
            canDiscount = permissions.canDiscount,
            canCollectPayments = permissions.canCollectPayments,
            canViewSalesReports = permissions.canViewSalesReports
        )
    }

    private fun parseUserProfile(doc: DocumentSnapshot): AppUserProfile {
        val role = normalizeUserRole(doc.getString("role") ?: "staff")
        val defaults = permissionsForRole(role)
        return AppUserProfile(
            uid = doc.id,
            email = doc.getString("email").orEmpty(),
            displayName = doc.getString("display_name").orEmpty().ifBlank { doc.getString("email").orEmpty().substringBefore('@') },
            role = role,
            enabled = doc.getBoolean("enabled") ?: false,
            canEditCustomers = doc.getBoolean("can_edit_customers") ?: defaults.canEditCustomers,
            canDiscount = doc.getBoolean("can_discount") ?: defaults.canDiscount,
            canCollectPayments = doc.getBoolean("can_collect_payments") ?: defaults.canCollectPayments,
            canViewSalesReports = doc.getBoolean("can_view_sales_reports") ?: defaults.canViewSalesReports,
            createdAtMillis = doc.getLong("created_at_ms") ?: 0L,
            updatedAtMillis = doc.getLong("updated_at_ms") ?: 0L,
            pinResetRequestedAtMillis = doc.getLong("pin_reset_requested_at_ms") ?: 0L
        )
    }

    private fun loadManagerPriceState() {
        if (!managerPricesLoaded || _lab2LabPrices.value.isEmpty()) {
            val localPrices = loadManagerPricesFromAsset()
            _lab2LabPrices.value = localPrices
            managerPricesLoaded = localPrices.isNotEmpty()
        }

        privilegedFirestore().collection(LAB2LAB_COLLECTION)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (!hasAdminAccess()) return@addOnSuccessListener
                val serverPrices = mutableMapOf<Int, String>()
                snapshot.documents.forEach { doc ->
                    val id = documentTestId(doc.id, doc.getLong("id")?.toInt()) ?: return@forEach
                    val value = doc.get("lab_to_lab_price")
                        ?: doc.get("lab2lab_price")
                        ?: doc.get("labToLabPrice")
                        ?: doc.get("price")
                        ?: return@forEach
                    formatFirestorePrice(value)?.takeIf { it.isNotBlank() }?.let { serverPrices[id] = it }
                }
                if (serverPrices.isNotEmpty()) {
                    _lab2LabPrices.value = _lab2LabPrices.value + serverPrices
                    managerPricesLoaded = true
                }
            }
    }

    private fun loadCustomerPriceOverrides() {
        if (!hasOperationalAccess()) return
        if (normalizeUserRole(operationalProfile()?.role.orEmpty()) == "lab_operator") {
            _customerPriceOverrides.value = emptyMap()
            customerOverridesLoaded = false
            return
        }

        fun applySnapshot(snapshot: QuerySnapshot) {
            snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
            val prices = mutableMapOf<Int, String>()
            snapshot.documents.forEach { doc ->
                val id = documentTestId(doc.id, doc.getLong("id")?.toInt()) ?: return@forEach
                val value = doc.get("customer_price")
                    ?: doc.get("customerPrice")
                    ?: doc.get("price")
                    ?: return@forEach
                formatFirestorePrice(value)?.takeIf { it.isNotBlank() }?.let { prices[id] = it }
            }
            _customerPriceOverrides.value = prices
            customerOverridesLoaded = true
        }

        val ref = firestore.collection(CUSTOMER_OVERRIDES_COLLECTION)
        if (_isOnline.value) {
            ref.get(Source.SERVER)
                .addOnSuccessListener(::applySnapshot)
                .addOnFailureListener {
                    // Server-first, then persistent cache so counter pricing remains usable during outages.
                    ref.get(Source.CACHE).addOnSuccessListener(::applySnapshot)
                }
        } else {
            ref.get(Source.CACHE).addOnSuccessListener(::applySnapshot)
        }
    }

    fun saveManagerPrices(
        test: LabTest,
        customerPrice: String,
        lab2LabPrice: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("غير مصرح بتعديل الأسعار", "Not authorized to edit prices"))
            return
        }

        val customerNumber = parsePriceInput(customerPrice)
        val labNumber = parsePriceInput(lab2LabPrice)
        if (customerNumber == null || customerNumber < 0) {
            onResult(false, tr("اكتب سعر عميل صحيح", "Enter a valid customer price"))
            return
        }
        if (labNumber == null || labNumber < 0) {
            onResult(false, tr("اكتب سعر Lab 2 Lab صحيح", "Enter a valid Lab2Lab price"))
            return
        }

        val docId = "test_${test.id}"
        val customerRef = privilegedFirestore().collection(CUSTOMER_OVERRIDES_COLLECTION).document(docId)
        val labRef = privilegedFirestore().collection(LAB2LAB_COLLECTION).document(docId)
        val customerData = mapOf(
            "id" to test.id,
            "customer_price" to customerNumber,
            "updated_at" to FieldValue.serverTimestamp()
        )
        val labData = mapOf(
            "id" to test.id,
            "lab_to_lab_price" to labNumber,
            "updated_at" to FieldValue.serverTimestamp()
        )

        privilegedFirestore().runBatch { batch ->
            batch.set(customerRef, customerData, SetOptions.merge())
            batch.set(labRef, labData, SetOptions.merge())
        }.addOnSuccessListener {
            ShadowBackupReplicator.mirrorPath(customerRef.path, System.currentTimeMillis())
            ShadowBackupReplicator.mirrorPath(labRef.path, System.currentTimeMillis())
            _customerPriceOverrides.value = _customerPriceOverrides.value + (test.id to formatNumber(customerNumber))
            _lab2LabPrices.value = _lab2LabPrices.value + (test.id to formatNumber(labNumber))
            customerOverridesLoaded = true
            managerPricesLoaded = true
            logAudit(
                action = "price_update",
                entityType = "lab_test",
                entityId = test.id.toString(),
                title = "تعديل سعر تحليل",
                details = "${test.englishName}: عميل ${formatNumber(customerNumber)} / Lab2Lab ${formatNumber(labNumber)}"
            )
            onResult(true, "تم حفظ أسعار ${test.englishName}")
        }.addOnFailureListener {
            onResult(false, "تعذر حفظ الأسعار. تأكد من الإنترنت وصلاحيات Firebase")
        }
    }

    fun updateAppSettings(settings: AppSettings) = settingsStore.save(settings)

    /** V83: import a white-label logo into app-private storage. */
    fun importBrandLogo(uri: Uri, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val context = getApplication<Application>()
                val dir = File(context.filesDir, "branding").apply { mkdirs() }
                val target = File(dir, "lab_logo.png")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "تعذر قراءة الصورة" }
                    target.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
                }
                require(target.length() in 1..3_000_000) { "حجم اللوجو لازم يكون أقل من 3 MB" }
                require(BitmapFactory.decodeFile(target.absolutePath) != null) { "الملف المختار مش صورة صالحة" }
                target.absolutePath
            }
            viewModelScope.launch(Dispatchers.Main) {
                result.fold(
                    onSuccess = { path ->
                        settingsStore.update { it.copy(brandLogoPath = path) }
                        logAudit("brand_logo_update", "settings", "brand_logo", "تحديث لوجو المعمل", "تم تغيير اللوجو المستخدم في هوية التطبيق والصور")
                        onResult(true, tr("تم حفظ لوجو المعمل", "Lab logo saved"))
                    },
                    onFailure = { error ->
                        FirebaseCrashlytics.getInstance().recordException(error)
                        onResult(false, error.message ?: tr("تعذر حفظ اللوجو", "Unable to save logo"))
                    }
                )
            }
        }
    }

    fun clearBrandLogo(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val path = settingsStore.settings.value.brandLogoPath
        runCatching { if (path.isNotBlank()) File(path).delete() }
        settingsStore.update { it.copy(brandLogoPath = "") }
        logAudit("brand_logo_reset", "settings", "brand_logo", "إرجاع اللوجو الافتراضي", "تم حذف اللوجو المخصص")
        onResult(true, tr("تم إرجاع اللوجو الافتراضي", "Default logo restored"))
    }

    /**
     * V83: creates an encrypted, portable backup of core operational data.
     * A complete production backup is only allowed while online.
     */
    fun createCommercialBackup(
        password: String,
        onResult: (Boolean, String, ByteArray?) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"), null)
            return
        }
        if (!_isOnline.value) {
            onResult(false, tr("لازم الإنترنت يكون متصل لعمل نسخة احتياطية كاملة", "Internet is required for a complete backup"), null)
            return
        }
        CommercialBackupManager.createEncryptedBackupV134(
            db = privilegedFirestore(),
            settings = settingsStore.settings.value,
            password = password
        ) { result ->
            result.fold(
                onSuccess = { backup ->
                    val appContext = getApplication<Application>()
                    AutoBackupCredentialStore.savePassword(appContext, password)
                    AutoBackupScheduler.schedule(appContext)
                    logAudit(
                        "backup_export", "system", "backup", "إنشاء نسخة احتياطية",
                        "${backup.customers} عميل • ${backup.orders} طلب • ${backup.payments} دفعة"
                    )
                    onResult(
                        true,
                        tr(
                            "تم تجهيز النسخة: ${backup.customers} عميل • ${backup.orders} طلب • النسخ التلقائي الساعة 4:00 ص مفعّل",
                            "Backup ready: ${backup.customers} customers • ${backup.orders} orders • automatic 4:00 AM backup enabled"
                        ),
                        backup.encrypted
                    )
                },
                onFailure = { error ->
                    FirebaseCrashlytics.getInstance().recordException(error)
                    onResult(false, error.message ?: tr("تعذر إنشاء النسخة الاحتياطية", "Unable to create backup"), null)
                }
            )
        }
    }

    /** Safe restore: merge/upsert only, never mass-deletes current data. */
    fun restoreCommercialBackup(
        encryptedBytes: ByteArray,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        if (!_isOnline.value) {
            onResult(false, tr("لازم الإنترنت يكون متصل قبل الاسترجاع", "Internet is required before restore"))
            return
        }
        val restoreContext = getApplication<Application>()
        BackupNotificationManager.notifyRestoreStarted(restoreContext)
        CommercialBackupManager.restoreEncryptedBackup(
            db = privilegedFirestore(),
            encryptedBytes = encryptedBytes,
            password = password
        ) { result ->
            result.fold(
                onSuccess = { restored ->
                    restored.settings?.let { imported ->
                        settingsStore.update { current ->
                            current.copy(
                                pdfLabName = imported.pdfLabName,
                                brandTagline = imported.brandTagline,
                                brandWhatsApp = imported.brandWhatsApp,
                                brandPhone = imported.brandPhone,
                                brandAddress = imported.brandAddress,
                                pdfShowContactInfo = imported.pdfShowContactInfo,
                                pdfContactInfo = imported.pdfContactInfo,
                                showCustomerPrice = imported.showCustomerPrice,
                                pdfIncludeCustomerPrice = imported.pdfIncludeCustomerPrice,
                                pdfShowTotals = imported.pdfShowTotals
                            )
                        }
                    }
                    restored.brandLogoBytes?.let { bytes ->
                        runCatching {
                            val context = getApplication<Application>()
                            val dir = File(context.filesDir, "branding").apply { mkdirs() }
                            val target = File(dir, "lab_logo.png")
                            target.writeBytes(bytes)
                            if (BitmapFactory.decodeFile(target.absolutePath) != null) {
                                settingsStore.update { it.copy(brandLogoPath = target.absolutePath) }
                            } else {
                                target.delete()
                            }
                        }
                    }
                    logAudit(
                        "backup_restore", "system", "backup", "استرجاع نسخة احتياطية",
                        "${restored.documentsWritten} مستند • ${restored.customers} عميل • ${restored.orders} طلب"
                    )
                    loadCustomers()
                    refreshPrices { _, _ -> }
                    val restoreMessage = if (restored.documentsWritten == 0) {
                        tr(
                            "تم فحص النسخة والاسترجاع بنجاح — لا توجد بيانات مفقودة للاسترجاع.",
                            "Backup checked and restore completed successfully — no missing data needed restoring."
                        )
                    } else {
                        tr(
                            "تم الاسترجاع بنجاح: ${restored.documentsWritten} مستند • ${restored.customers} عميل • ${restored.orders} طلب",
                            "Restore completed: ${restored.documentsWritten} documents • ${restored.customers} customers • ${restored.orders} orders"
                        )
                    }
                    BackupNotificationManager.notifyRestoreCompleted(restoreContext, restoreMessage)
                    onResult(true, restoreMessage)
                },
                onFailure = { error ->
                    FirebaseCrashlytics.getInstance().recordException(error)
                    val restoreError = error.message ?: tr("تعذر استرجاع النسخة", "Unable to restore backup")
                    BackupNotificationManager.notifyRestoreFailed(restoreContext, restoreError)
                    onResult(false, restoreError)
                }
            )
        }
    }

    fun setAppPin(pin: String): Boolean = settingsStore.setAppPin(pin)
    fun verifyAppPin(pin: String): Boolean = settingsStore.verifyAppPin(pin)
    fun clearAppPin() = settingsStore.clearAppPin()
    fun resetAppSettings() = settingsStore.reset()
    fun toggleFavoriteTest(testId: Int) = settingsStore.toggleFavoriteTest(testId)
    fun useRecentSearch(query: String) {
        _recognizedTests.value = emptyList()
        onQueryChanged(query)
    }

    fun refreshPrices(onResult: (Boolean, String) -> Unit) {
        firestore.collection(CUSTOMER_OVERRIDES_COLLECTION)
            .get(Source.SERVER)
            .addOnSuccessListener { customerSnapshot ->
                customerSnapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                val customerPrices = mutableMapOf<Int, String>()
                customerSnapshot.documents.forEach { doc ->
                    val id = documentTestId(doc.id, doc.getLong("id")?.toInt()) ?: return@forEach
                    val value = doc.get("customer_price") ?: doc.get("customerPrice") ?: doc.get("price") ?: return@forEach
                    formatFirestorePrice(value)?.takeIf { it.isNotBlank() }?.let { customerPrices[id] = it }
                }
                _customerPriceOverrides.value = customerPrices
                customerOverridesLoaded = true

                if (!hasAdminAccess()) {
                    settingsStore.markSynced()
                    onResult(true, tr("تم تحديث الأسعار", "Prices refreshed"))
                    return@addOnSuccessListener
                }

                privilegedFirestore().collection(LAB2LAB_COLLECTION).get(Source.SERVER)
                    .addOnSuccessListener { labSnapshot ->
                        labSnapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                        val labPrices = mutableMapOf<Int, String>()
                        labSnapshot.documents.forEach { doc ->
                            val id = documentTestId(doc.id, doc.getLong("id")?.toInt()) ?: return@forEach
                            val value = doc.get("lab_to_lab_price")
                                ?: doc.get("lab2lab_price")
                                ?: doc.get("labToLabPrice")
                                ?: doc.get("price")
                                ?: return@forEach
                            formatFirestorePrice(value)?.takeIf { it.isNotBlank() }?.let { labPrices[id] = it }
                        }
                        if (labPrices.isNotEmpty()) {
                            _lab2LabPrices.value = _lab2LabPrices.value + labPrices
                            managerPricesLoaded = true
                        }
                        settingsStore.markSynced()
                        onResult(true, tr("تم تحديث الأسعار بنجاح", "Prices refreshed successfully"))
                    }
                    .addOnFailureListener { onResult(false, "تعذر تحديث أسعار Lab 2 Lab") }
            }
            .addOnFailureListener { onResult(false, tr("تعذر تحديث الأسعار. تأكد من الإنترنت", "Unable to refresh prices. Check internet connection")) }
    }

    fun searchManagerTests(query: String): List<LabTest> =
        if (query.isBlank()) repository.getLabTests() else repository.searchTests(query)

    fun addCatalogTest(
        englishName: String,
        arabicName: String,
        marketName: String,
        searchText: String,
        customerPrice: String,
        lab2LabPrice: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        if (englishName.isBlank() && arabicName.isBlank() && marketName.isBlank()) {
            onResult(false, tr("اكتب اسم التحليل على الأقل", "Enter at least one test name"))
            return
        }
        val customerNumber = parsePriceInput(customerPrice)
        val labNumber = parsePriceInput(lab2LabPrice)
        if (customerNumber == null || customerNumber < 0 || labNumber == null || labNumber < 0) {
            onResult(false, tr("اكتب أسعار صحيحة", "Enter valid prices"))
            return
        }
        val test = repository.addLabTest(englishName, arabicName, marketName, searchText, formatNumber(customerNumber))
        _catalogRevision.value += 1
        saveManagerPrices(test, customerPrice, lab2LabPrice) { success, message ->
            if (success) {
                logAudit(
                    action = "catalog_add", entityType = "lab_test", entityId = test.id.toString(),
                    title = "إضافة تحليل", details = "${test.englishName} / ${test.arabicName}"
                )
            }
            onResult(success, message)
        }
    }

    fun updateCatalogTest(
        original: LabTest,
        englishName: String,
        arabicName: String,
        marketName: String,
        searchText: String,
        customerPrice: String,
        lab2LabPrice: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        if (englishName.isBlank() && arabicName.isBlank() && marketName.isBlank()) {
            onResult(false, tr("اكتب اسم التحليل على الأقل", "Enter at least one test name"))
            return
        }
        val edited = repository.updateLabTest(
            original.copy(
                englishName = englishName.trim(),
                arabicName = arabicName.trim(),
                marketName = marketName.trim(),
                searchText = searchText.trim(),
                customerPrice = customerPrice.trim()
            )
        )
        _selectedTests.value = _selectedTests.value.map { if (it.id == edited.id) edited else it }
        _recognizedTests.value = _recognizedTests.value.map { if (it.id == edited.id) edited else it }
        _catalogRevision.value += 1
        saveManagerPrices(edited, customerPrice, lab2LabPrice) { success, message ->
            if (success) {
                logAudit(
                    action = "catalog_update", entityType = "lab_test", entityId = edited.id.toString(),
                    title = "تعديل تحليل", details = "${edited.englishName} / ${edited.arabicName}"
                )
            }
            onResult(success, message)
        }
    }

    data class BulkCatalogPreview(
        val matched: List<Pair<LabTest, String>>,
        val notFound: List<String>
    )

    fun previewBulkCatalogUpdate(text: String): BulkCatalogPreview {
        val all = repository.getLabTests()
        val matched = mutableListOf<Pair<LabTest, String>>()
        val missing = mutableListOf<String>()
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(Regex("[\\t|;,=]+"), limit = 2).map { it.trim() }
            val fallback = Regex("^(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)$").find(line)
            val name = when {
                parts.size == 2 -> parts[0]
                fallback != null -> fallback.groupValues[1].trim()
                else -> line
            }
            val price = when {
                parts.size == 2 -> parts[1].replace("جنيه", "").trim()
                fallback != null -> fallback.groupValues[2]
                else -> ""
            }
            val found = repository.searchTests(name).firstOrNull { candidate ->
                listOf(candidate.englishName, candidate.arabicName, candidate.marketName)
                    .filter { it.isNotBlank() }
                    .any { normalizeText(it) == normalizeText(name) }
            } ?: repository.searchTests(name).firstOrNull()
            if (found != null && parsePriceInput(price) != null) matched += found to price
            else missing += line
        }
        return BulkCatalogPreview(matched.distinctBy { it.first.id }, missing)
    }

    fun applyBulkCatalogUpdate(text: String, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action")); return
        }
        val preview = previewBulkCatalogUpdate(text)
        if (preview.matched.isEmpty()) {
            onResult(false, tr("لم يتم العثور على تعديلات صالحة", "No valid updates found")); return
        }
        val updates = preview.matched.associate { (test, price) ->
            test.id to test.copy(customerPrice = formatNumber(parsePriceInput(price)!!))
        }
        repository.bulkUpdateLabTests(updates)
        _catalogRevision.value += 1
        _selectedTests.value = _selectedTests.value.map { updates[it.id] ?: it }
        _recognizedTests.value = _recognizedTests.value.map { updates[it.id] ?: it }
        _customerPriceOverrides.value = _customerPriceOverrides.value + updates.mapValues { it.value.customerPrice.orEmpty() }
        onResult(true, tr(
            "تم تعديل ${updates.size} تحليل${if (preview.notFound.isNotEmpty()) " • غير مطابق: ${preview.notFound.size}" else ""}",
            "Updated ${updates.size} tests${if (preview.notFound.isNotEmpty()) " • unmatched: ${preview.notFound.size}" else ""}"
        ))
    }

    fun deleteCatalogTest(test: LabTest, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        repository.deleteLabTest(test.id)
        _selectedTests.value = _selectedTests.value.filterNot { it.id == test.id }
        _recognizedTests.value = _recognizedTests.value.filterNot { it.id == test.id }
        _catalogRevision.value += 1
        logAudit(
            action = "catalog_delete", entityType = "lab_test", entityId = test.id.toString(),
            title = "حذف تحليل", details = "${test.englishName} / ${test.arabicName}"
        )
        onResult(true, tr("تم حذف التحليل من الدليل", "Test removed from catalogue"))
    }

    fun clearLab2LabState() {
        lockAdmin()
        _isManager.value = false
        _actualManager.value = false
        _actingAsUser.value = null
        clearManagerPriceCache()
    }

    // ------------------------- USERS / ROLES -------------------------

    fun loadUsers(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasAdminAccess()) {
            onResult?.invoke(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        privilegedFirestore().collection(USERS_COLLECTION).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                val parsed = snapshot.documents
                    .filter { it.getString("credential_replaced_by_uid").isNullOrBlank() }
                    .map { parseUserProfile(it) }
                    .toMutableList()
                if (parsed.none { it.uid == MANAGER_UID }) {
                    parsed += AppUserProfile(
                        uid = MANAGER_UID,
                        email = MANAGER_EMAIL,
                        displayName = "Abdelrahman",
                        role = "super_admin",
                        enabled = true
                    )
                }
                _users.value = parsed.sortedWith(compareByDescending<AppUserProfile> { it.uid == MANAGER_UID }.thenBy { it.displayName.lowercase() })
                onResult?.invoke(true, tr("تم تحديث المستخدمين", "Users refreshed"))
            }
            .addOnFailureListener { onResult?.invoke(false, tr("تعذر تحميل المستخدمين", "Unable to load users")) }
    }

    fun createUserAccount(
        displayName: String,
        email: String,
        password: String,
        role: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim()
        if (cleanName.length < 2) {
            onResult(false, tr("اكتب اسم المستخدم", "Enter user name"))
            return
        }
        if (!cleanEmail.contains('@')) {
            onResult(false, tr("اكتب بريد إلكتروني صحيح", "Enter a valid email"))
            return
        }
        // Firebase Email/Password accepts any character mix; the only platform rule is 6+ characters.
        if (password.length < 6) {
            onResult(false, tr("كلمة المرور لازم تكون 6 خانات على الأقل", "Password must be at least 6 characters"))
            return
        }

        val secondaryApp = try {
            FirebaseApp.getInstance(SECONDARY_AUTH_APP)
        } catch (_: IllegalStateException) {
            FirebaseApp.initializeApp(
                getApplication<Application>(),
                FirebaseApp.getInstance().options,
                SECONDARY_AUTH_APP
            ) ?: run {
                onResult(false, "تعذر تجهيز إنشاء المستخدم")
                return
            }
        }
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
        secondaryAuth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val newUid = authResult.user?.uid
                if (newUid.isNullOrBlank()) {
                    secondaryAuth.signOut()
                    onResult(false, "تعذر الحصول على UID للمستخدم")
                    return@addOnSuccessListener
                }
                val now = System.currentTimeMillis()
                val normalizedRole = normalizeRole(role)
                val rolePermissions = permissionsForRole(normalizedRole)
                val data = mapOf(
                    "email" to cleanEmail,
                    "display_name" to cleanName,
                    "role" to normalizedRole,
                    "enabled" to true,
                    "can_edit_customers" to rolePermissions.canEditCustomers,
                    "can_discount" to rolePermissions.canDiscount,
                    "can_collect_payments" to rolePermissions.canCollectPayments,
                    "can_view_sales_reports" to rolePermissions.canViewSalesReports,
                    "created_at_ms" to now,
                    "created_at" to FieldValue.serverTimestamp(),
                    "updated_at_ms" to now,
                    "updated_at" to FieldValue.serverTimestamp(),
                    "created_by_uid" to currentUid()
                )
                privilegedFirestore().collection(USERS_COLLECTION).document(newUid).set(data)
                    .addOnSuccessListener {
                        ShadowBackupReplicator.mirrorPath("users/$newUid", now)
                        secondaryAuth.signOut()
                        logAudit(
                            action = "user_create",
                            entityType = "user",
                            entityId = newUid,
                            title = tr("إضافة مستخدم", "Add User"),
                            details = "$cleanName • $cleanEmail • $normalizedRole"
                        )
                        loadUsers()
                        onResult(true, "تم إنشاء حساب $cleanName")
                    }
                    .addOnFailureListener {
                        // Avoid leaving an orphan Firebase Auth account without an application profile.
                        authResult.user?.delete()?.addOnCompleteListener {
                            secondaryAuth.signOut()
                            onResult(false, "تعذر حفظ صلاحيات المستخدم وتم التراجع عن إنشاء الحساب")
                        } ?: run {
                            secondaryAuth.signOut()
                            onResult(false, "تعذر حفظ صلاحيات المستخدم. راجع Firebase")
                        }
                    }
            }
            .addOnFailureListener { error ->
                secondaryAuth.signOut()
                onResult(false, error.localizedMessage ?: tr("تعذر إنشاء المستخدم", "Unable to create user"))
            }
    }

    fun updateUserAccess(
        profile: AppUserProfile,
        enabled: Boolean,
        role: String,
        canEditCustomers: Boolean,
        canDiscount: Boolean,
        canCollectPayments: Boolean,
        canViewSalesReports: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        if (profile.uid == MANAGER_UID) {
            onResult(false, "لا يمكن إيقاف أو خفض صلاحيات حساب المدير الأساسي")
            return
        }
        val now = System.currentTimeMillis()
        val normalizedRole = normalizeRole(role)
        val data = mapOf(
            "enabled" to enabled,
            "role" to normalizedRole,
            "can_edit_customers" to canEditCustomers,
            "can_discount" to canDiscount,
            "can_collect_payments" to canCollectPayments,
            "can_view_sales_reports" to canViewSalesReports,
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid()
        )
        privilegedFirestore().collection(USERS_COLLECTION).document(profile.uid).set(data, SetOptions.merge())
            .addOnSuccessListener {
                ShadowBackupReplicator.mirrorPath("users/${profile.uid}", now)
                logAudit(
                    action = "user_access_update",
                    entityType = "user",
                    entityId = profile.uid,
                    title = "تعديل صلاحيات مستخدم",
                    details = "${profile.displayName}: enabled=$enabled role=${normalizeRole(role)}"
                )
                loadUsers()
                onResult(true, "تم تحديث صلاحيات ${profile.displayName}")
            }
            .addOnFailureListener { onResult(false, tr("تعذر تحديث صلاحيات المستخدم", "Unable to update user permissions")) }
    }

    /**
     * V123 backend-free password recovery.
     * Firebase client SDK cannot change another user's Auth password directly.
     * To keep the reset inside the already PIN-locked administration area and avoid
     * email reset links / paid backend services, rotate the user's login credentials:
     * create a fresh Firebase Auth identity, clone the application profile + approved
     * devices to it, then disable the old application profile. The old Auth identity
     * may still authenticate at Firebase, but Firestore activeUser() denies it because
     * its profile is disabled.
     */
    fun resetUserLoginCredentials(
        profile: AppUserProfile,
        newPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        if (profile.uid == MANAGER_UID || profile.role == "super_admin") {
            onResult(false, tr("الحساب الرئيسي لا يتم تغيير بيانات دخوله من هنا", "The primary account cannot be rotated here"))
            return
        }
        if (newPassword.length < 6) {
            onResult(false, tr("كلمة المرور الجديدة لازم تكون 6 خانات على الأقل", "New password must be at least 6 characters"))
            return
        }

        val originalEmail = profile.email.trim().lowercase()
        val at = originalEmail.lastIndexOf('@')
        if (at <= 0 || at >= originalEmail.length - 1) {
            onResult(false, tr("اسم الدخول الحالي غير صالح", "Current login email is invalid"))
            return
        }
        val localBase = originalEmail.substring(0, at).substringBefore("+ak")
        val domain = originalEmail.substring(at + 1)
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val newLoginEmail = "$localBase+ak$suffix@$domain"

        val userRef = privilegedFirestore().collection(USERS_COLLECTION).document(profile.uid)
        userRef.get(Source.SERVER)
            .addOnSuccessListener { oldProfileDoc ->
                if (!oldProfileDoc.exists()) {
                    onResult(false, tr("ملف المستخدم غير موجود", "User profile was not found"))
                    return@addOnSuccessListener
                }

                val secondaryApp = try {
                    FirebaseApp.getInstance(SECONDARY_AUTH_APP)
                } catch (_: IllegalStateException) {
                    FirebaseApp.initializeApp(
                        getApplication<Application>(),
                        FirebaseApp.getInstance().options,
                        SECONDARY_AUTH_APP
                    ) ?: run {
                        onResult(false, tr("تعذر تجهيز إعادة تعيين بيانات الدخول", "Unable to initialize credential reset"))
                        return@addOnSuccessListener
                    }
                }
                val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
                secondaryAuth.createUserWithEmailAndPassword(newLoginEmail, newPassword)
                    .addOnSuccessListener { authResult ->
                        val newUid = authResult.user?.uid
                        if (newUid.isNullOrBlank()) {
                            secondaryAuth.signOut()
                            onResult(false, tr("تعذر إنشاء بيانات الدخول الجديدة", "Unable to create the new login"))
                            return@addOnSuccessListener
                        }

                        userRef.collection(DEVICES_SUBCOLLECTION).get(Source.SERVER)
                            .addOnSuccessListener { deviceSnapshot ->
                                val now = System.currentTimeMillis()
                                val newUserRef = privilegedFirestore().collection(USERS_COLLECTION).document(newUid)
                                val cloned = oldProfileDoc.data.orEmpty().toMutableMap()
                                cloned["email"] = newLoginEmail
                                cloned["enabled"] = profile.enabled
                                cloned["updated_at_ms"] = now
                                cloned["updated_at"] = FieldValue.serverTimestamp()
                                cloned["updated_by_uid"] = currentUid()
                                cloned["credential_rotated_from_uid"] = profile.uid
                                cloned["credential_rotated_at_ms"] = now
                                cloned.remove("credential_replaced_by_uid")
                                cloned.remove("credential_replaced_at_ms")
                                cloned.remove("pin_reset_requested_at_ms")

                                privilegedFirestore().runBatch { batch ->
                                    batch.set(newUserRef, cloned)
                                    batch.set(
                                        userRef,
                                        mapOf(
                                            "enabled" to false,
                                            "credential_replaced_by_uid" to newUid,
                                            "credential_replaced_at_ms" to now,
                                            "updated_at_ms" to now,
                                            "updated_at" to FieldValue.serverTimestamp(),
                                            "updated_by_uid" to currentUid()
                                        ),
                                        SetOptions.merge()
                                    )
                                    deviceSnapshot.documents.forEach { deviceDoc ->
                                        val copied = deviceDoc.data.orEmpty().toMutableMap()
                                        copied["uid"] = newUid
                                        copied["email"] = newLoginEmail
                                        copied["updated_at_ms"] = now
                                        copied["updated_at"] = FieldValue.serverTimestamp()
                                        batch.set(newUserRef.collection(DEVICES_SUBCOLLECTION).document(deviceDoc.id), copied)
                                    }
                                }.addOnSuccessListener {
                                    ShadowBackupReplicator.mirrorPath(newUserRef.path, now)
                                    ShadowBackupReplicator.mirrorPath(userRef.path, now)
                                    deviceSnapshot.documents.forEach { deviceDoc ->
                                        ShadowBackupReplicator.mirrorPath(newUserRef.collection(DEVICES_SUBCOLLECTION).document(deviceDoc.id).path, now)
                                    }
                                    secondaryAuth.signOut()
                                    logAudit(
                                        action = "user_credentials_rotate",
                                        entityType = "user",
                                        entityId = newUid,
                                        title = tr("إعادة تعيين كلمة المرور", "Reset password"),
                                        details = "${profile.displayName} • $originalEmail -> $newLoginEmail"
                                    )
                                    loadUsers()
                                    onResult(
                                        true,
                                        tr(
                                            "تمت إعادة تعيين بيانات الدخول. اسم الدخول الجديد: $newLoginEmail",
                                            "Login credentials reset. New login email: $newLoginEmail"
                                        )
                                    )
                                }.addOnFailureListener {
                                    authResult.user?.delete()?.addOnCompleteListener {
                                        secondaryAuth.signOut()
                                        onResult(false, tr("تعذر حفظ بيانات الدخول الجديدة وتم التراجع عن العملية", "Unable to save the new login; the operation was rolled back"))
                                    } ?: run {
                                        secondaryAuth.signOut()
                                        onResult(false, tr("تعذر حفظ بيانات الدخول الجديدة", "Unable to save the new login"))
                                    }
                                }
                            }
                            .addOnFailureListener {
                                authResult.user?.delete()?.addOnCompleteListener {
                                    secondaryAuth.signOut()
                                    onResult(false, tr("تعذر قراءة أجهزة المستخدم وتم التراجع عن العملية", "Unable to read user devices; the operation was rolled back"))
                                } ?: run {
                                    secondaryAuth.signOut()
                                    onResult(false, tr("تعذر قراءة أجهزة المستخدم", "Unable to read user devices"))
                                }
                            }
                    }
                    .addOnFailureListener { error ->
                        secondaryAuth.signOut()
                        onResult(false, error.localizedMessage ?: tr("تعذر إنشاء بيانات الدخول الجديدة", "Unable to create new login credentials"))
                    }
            }
            .addOnFailureListener {
                onResult(false, tr("تعذر قراءة ملف المستخدم", "Unable to read user profile"))
            }
    }

    fun requestUserPinReset(profile: AppUserProfile, onResult: (Boolean, String) -> Unit) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val now = System.currentTimeMillis()
        privilegedFirestore().collection(USERS_COLLECTION).document(profile.uid)
            .set(
                mapOf(
                    "pin_reset_requested_at_ms" to now,
                    "updated_at_ms" to now,
                    "updated_at" to FieldValue.serverTimestamp(),
                    "updated_by_uid" to currentUid()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                ShadowBackupReplicator.mirrorPath("users/${profile.uid}", now)
                logAudit(
                    action = "pin_reset_request",
                    entityType = "user",
                    entityId = profile.uid,
                    title = "طلب Reset PIN",
                    details = profile.displayName
                )
                onResult(true, "هيتم إلغاء PIN الخاص بالمستخدم عند فتح التطبيق على جهازه")
            }
            .addOnFailureListener { onResult(false, "تعذر إرسال طلب Reset PIN") }
    }

    private fun normalizeRole(role: String): String = normalizeUserRole(role)

    // ------------------------- V80 DAILY LAB DESK -------------------------

    /**
     * Loads today's orders for the operational desk using the already-permitted
     * customer/{id}/orders path. This avoids requiring a privileged collectionGroup
     * query for staff accounts and keeps the feature compatible with the existing rules.
     */
    /**
     * V116 server-load cleanup: one collection-group query replaces one request per customer.
     * This keeps the home fast as the customer count grows and uses the deployed orders index.
     */
    fun loadDailyOrders(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasOperationalAccess()) {
            onResult?.invoke(false, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (_dailyOrdersLoading.value) return
        _dailyOrdersLoading.value = true

        val startTrackingWindow = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -30)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val query = firestore.collectionGroup("orders")
            .whereGreaterThanOrEqualTo("created_at_ms", startTrackingWindow)
            .orderBy("created_at_ms", Query.Direction.DESCENDING)
            .limit(DAILY_ORDER_LIMIT)

        fun apply(snapshot: QuerySnapshot, fromCache: Boolean) {
            _dailyOrders.value = snapshot.documents
                .mapNotNull { parseOrder(it) }
                .filterNot { it.isVoided }
                .distinctBy { it.id }
                .sortedByDescending { it.createdAtMillis }
            _dailyOrdersLoading.value = false
            val suffix = if (fromCache) tr(" • من النسخة المحلية", " • from local cache") else ""
            onResult?.invoke(true, tr("تم تحديث متابعة الطلبات", "Order tracking refreshed") + suffix)
        }

        query.get(if (_isOnline.value) Source.SERVER else Source.CACHE)
            .addOnSuccessListener { apply(it, !_isOnline.value) }
            .addOnFailureListener {
                query.get(Source.CACHE)
                    .addOnSuccessListener { cached -> apply(cached, true) }
                    .addOnFailureListener {
                        _dailyOrdersLoading.value = false
                        onResult?.invoke(false, tr("تعذر تحميل متابعة الطلبات", "Unable to load order tracking"))
                    }
            }
    }

    /**
     * V116 orders hub: one indexed range query for the selected dates.
     * No N+1 customer reads, so week-by-week archive browsing stays predictable.
     */
    fun loadOrderArchive(
        startMillis: Long,
        endMillis: Long,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (!hasOperationalAccess()) {
            onResult?.invoke(false, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (_orderArchiveLoading.value) return
        _orderArchiveLoading.value = true

        val safeStart = minOf(startMillis, endMillis).coerceAtLeast(0L)
        val safeEnd = maxOf(startMillis, endMillis).coerceAtLeast(safeStart)
        val query = firestore.collectionGroup("orders")
            .whereGreaterThanOrEqualTo("created_at_ms", safeStart)
            .whereLessThanOrEqualTo("created_at_ms", safeEnd)
            .orderBy("created_at_ms", Query.Direction.DESCENDING)
            .limit(ORDER_ARCHIVE_LIMIT)

        fun apply(snapshot: QuerySnapshot, fromCache: Boolean) {
            _orderArchive.value = snapshot.documents
                .mapNotNull { parseOrder(it) }
                .distinctBy { it.id }
                .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.createdAtMillis }
            _orderArchiveLoading.value = false
            val suffix = if (fromCache) tr(" • من النسخة المحلية", " • from local cache") else ""
            onResult?.invoke(
                true,
                if (_orderArchive.value.isEmpty()) tr("لا توجد طلبات في الفترة المحددة", "No orders in the selected period")
                else tr("تم تحديث سجل الطلبات", "Order history refreshed") + suffix
            )
        }

        query.get(if (_isOnline.value) Source.SERVER else Source.CACHE)
            .addOnSuccessListener { apply(it, !_isOnline.value) }
            .addOnFailureListener {
                query.get(Source.CACHE)
                    .addOnSuccessListener { cached -> apply(cached, true) }
                    .addOnFailureListener { error ->
                        _orderArchiveLoading.value = false
                        onResult?.invoke(false, error.localizedMessage ?: tr("تعذر تحميل سجل الطلبات", "Unable to load order history"))
                    }
            }
    }

    fun findCustomerById(customerId: String, onResult: (Customer?, String) -> Unit) {
        if (customerId.isBlank()) {
            onResult(null, tr("ملف العميل غير موجود", "Customer file not found"))
            return
        }
        _customers.value.firstOrNull { it.id == customerId }?.let {
            onResult(it, tr("تم فتح ملف العميل", "Customer file opened"))
            return
        }
        firestore.collection(CUSTOMERS_COLLECTION).document(customerId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) ShadowBackupReplicator.mirrorSnapshot(doc)
                val customer = if (doc.exists()) parseCustomer(doc) else null
                if (customer != null) {
                    _customers.value = listOf(customer) + _customers.value.filterNot { it.id == customer.id }
                    onResult(customer, tr("تم فتح ملف العميل", "Customer file opened"))
                } else {
                    onResult(null, tr("ملف العميل غير موجود", "Customer file not found"))
                }
            }
            .addOnFailureListener {
                onResult(null, tr("تعذر تحميل بيانات العميل", "Unable to load customer data"))
            }
    }

    // ------------------------- CUSTOMERS -------------------------

    fun loadCustomers(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasOperationalAccess()) {
            onResult?.invoke(false, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (_customersLoading.value) return
        _customersLoading.value = true

        val query = firestore.collection(CUSTOMERS_COLLECTION)

        fun applySnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot, fromCache: Boolean) {
            snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
            _customers.value = snapshot.documents.mapNotNull { parseCustomer(it) }
                .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0 } ?: it.createdAtMillis }
            _customersLoading.value = false
            onResult?.invoke(
                true,
                if (fromCache) tr("تم تحميل العملاء من النسخة المحلية • سيتم التحديث عند رجوع الإنترنت", "Customers loaded from local cache • will refresh when online")
                else tr("تم تحديث بيانات العملاء من السيرفر", "Customers refreshed from server")
            )
        }

        if (_isOnline.value) {
            query.get(Source.SERVER)
                .addOnSuccessListener { applySnapshot(it, false) }
                .addOnFailureListener {
                    query.get(Source.CACHE)
                        .addOnSuccessListener { cached -> applySnapshot(cached, true) }
                        .addOnFailureListener {
                            _customersLoading.value = false
                            onResult?.invoke(false, tr("تعذر تحميل العملاء من السيرفر أو النسخة المحلية", "Unable to load customers from server or local cache"))
                        }
                }
        } else {
            query.get(Source.CACHE)
                .addOnSuccessListener { applySnapshot(it, true) }
                .addOnFailureListener {
                    _customersLoading.value = false
                    onResult?.invoke(false, tr("لا توجد نسخة محلية متاحة للعملاء", "No local customer cache is available"))
                }
        }
    }

    /**
     * V37: Resolve a customer QR back to the same customer record.
     * The QR payload generated by PdfGenerator is stable per customer because it contains
     * the Firestore document id plus the clinic file number.
     */
    fun findCustomerByQrPayload(
        payload: String,
        onResult: (Customer?, String) -> Unit
    ) {
        val raw = payload.trim()
        if (raw.length > 512 || raw.any { it.code < 0x20 || it.code == 0x7F }) {
            onResult(null, tr("QR العميل غير صالح أو حجمه غير طبيعي", "Invalid or oversized customer QR"))
            return
        }
        if (!raw.startsWith("TAHALIL_ALAKKAD_CUSTOMER|")) {
            onResult(null, tr("الكود ده مش QR عميل صادر من تحاليل العقاد", "This is not a Tahalil Alakkad customer QR"))
            return
        }

        fun valueFor(key: String): String {
            return raw.split('|')
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter('=')
                ?.trim()
                .orEmpty()
        }

        val customerId = valueFor("ID")
        val fileNumber = valueFor("FILE")

        if (customerId.isBlank() && fileNumber.isBlank()) {
            onResult(null, tr("QR العميل غير صالح", "Invalid customer QR"))
            return
        }

        val localMatch = _customers.value.firstOrNull { customer ->
            (customerId.isNotBlank() && customer.id == customerId) ||
                (fileNumber.isNotBlank() && customer.fileNumber == fileNumber)
        }
        if (localMatch != null) {
            onResult(localMatch, tr("تم فتح ملف العميل", "Customer file opened"))
            return
        }

        if (!_isOnline.value) {
            onResult(null, tr("العميل مش موجود في البيانات المحفوظة على الجهاز. اتصل بالإنترنت وحاول تاني", "Customer is not available in the local cache. Connect to the internet and try again"))
            return
        }

        if (customerId.isNotBlank()) {
            firestore.collection(CUSTOMERS_COLLECTION)
                .document(customerId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) ShadowBackupReplicator.mirrorSnapshot(doc)
                    val customer = if (doc.exists()) parseCustomer(doc) else null
                    if (customer != null) {
                        _customers.value = listOf(customer) + _customers.value.filterNot { it.id == customer.id }
                        onResult(customer, tr("تم فتح ملف العميل", "Customer file opened"))
                    } else if (fileNumber.isNotBlank()) {
                        findCustomerByFileNumberFromServer(fileNumber, onResult)
                    } else {
                        onResult(null, tr("ملف العميل غير موجود", "Customer file not found"))
                    }
                }
                .addOnFailureListener {
                    if (fileNumber.isNotBlank()) {
                        findCustomerByFileNumberFromServer(fileNumber, onResult)
                    } else {
                        onResult(null, tr("تعذر تحميل بيانات العميل", "Unable to load customer data"))
                    }
                }
        } else {
            findCustomerByFileNumberFromServer(fileNumber, onResult)
        }
    }

    private fun findCustomerByFileNumberFromServer(
        fileNumber: String,
        onResult: (Customer?, String) -> Unit
    ) {
        firestore.collection(CUSTOMERS_COLLECTION)
            .whereEqualTo("file_number", fileNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                val customer = snapshot.documents.firstOrNull()?.let(::parseCustomer)
                if (customer != null) {
                    _customers.value = listOf(customer) + _customers.value.filterNot { it.id == customer.id }
                    onResult(customer, tr("تم فتح ملف العميل", "Customer file opened"))
                } else {
                    onResult(null, tr("ملف العميل غير موجود", "Customer file not found"))
                }
            }
            .addOnFailureListener {
                onResult(null, tr("تعذر تحميل بيانات العميل", "Unable to load customer data"))
            }
    }

    private fun parseCustomer(doc: DocumentSnapshot): Customer? {
        val name = doc.getString("name")?.trim().orEmpty()
        if (name.isBlank()) return null
        val rawTags = doc.get("tags") as? List<*>
        return Customer(
            id = doc.id,
            fileNumber = doc.getString("file_number").orEmpty(),
            name = name,
            phone = doc.getString("phone").orEmpty(),
            alternatePhone = doc.getString("alternate_phone").orEmpty(),
            age = doc.getString("age").orEmpty(),
            birthDate = doc.getString("birth_date").orEmpty(),
            gender = doc.getString("gender").orEmpty(),
            address = doc.getString("address").orEmpty(),
            notes = doc.getString("notes").orEmpty(),
            importantAlert = doc.getString("important_alert").orEmpty(),
            tags = rawTags?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) } ?: emptyList(),
            defaultDiscountPercent = numberAsDouble(doc.get("default_discount_percent")).coerceIn(0.0, 100.0),
            createdAtMillis = doc.getLong("created_at_ms") ?: 0L,
            updatedAtMillis = doc.getLong("updated_at_ms") ?: 0L,
            createdByUid = doc.getString("created_by_uid").orEmpty(),
            updatedByUid = doc.getString("updated_by_uid").orEmpty(),
            isBlacklisted = doc.getBoolean("is_blacklisted") ?: false,
            blacklistReason = doc.getString("blacklist_reason").orEmpty(),
            blacklistedAtMillis = doc.getLong("blacklisted_at_ms") ?: 0L,
            isArchived = doc.getBoolean("is_archived") ?: false,
            archivedAtMillis = doc.getLong("archived_at_ms") ?: 0L
        )
    }

    fun saveCustomer(
        existing: Customer?,
        name: String,
        phone: String,
        alternatePhone: String = "",
        age: String,
        birthDate: String = "",
        gender: String,
        address: String = "",
        notes: String,
        importantAlert: String = "",
        tags: List<String> = emptyList(),
        defaultDiscountPercent: Double = 0.0,
        onResult: (Boolean, String) -> Unit
    ) {
        saveCustomerInternal(
            existing, name, phone, alternatePhone, age, birthDate, gender, address,
            notes, importantAlert, tags, defaultDiscountPercent
        ) { customer, message -> onResult(customer != null, message) }
    }

    fun saveCustomerAndReturn(
        name: String,
        phone: String,
        alternatePhone: String = "",
        age: String,
        birthDate: String = "",
        gender: String,
        address: String = "",
        notes: String,
        importantAlert: String = "",
        tags: List<String> = emptyList(),
        defaultDiscountPercent: Double = 0.0,
        onResult: (Customer?, String) -> Unit
    ) {
        saveCustomerInternal(
            null, name, phone, alternatePhone, age, birthDate, gender, address,
            notes, importantAlert, tags, defaultDiscountPercent, onResult
        )
    }

    private fun saveCustomerInternal(
        existing: Customer?,
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
        onResult: (Customer?, String) -> Unit
    ) {
        if (!hasOperationalAccess()) {
            onResult(null, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (!canEditCustomersOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية إضافة أو تعديل العملاء", "Your account cannot add or edit customers"))
            return
        }
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        val cleanAltPhone = normalizePhone(alternatePhone)
        val cleanAge = age.trim()
        val cleanBirthDate = birthDate.trim().take(20)
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
        val safeDefaultDiscount = defaultDiscountPercent.coerceIn(0.0, 100.0)
        if (safeDefaultDiscount > 0.0 && !canDiscountOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية الخصومات", "Your account cannot apply discounts"))
            return
        }

        if (cleanName.length < 2) {
            onResult(null, tr("اكتب اسم العميل", "Enter customer name"))
            return
        }
        if (cleanPhone.length < 8) {
            onResult(null, tr("اكتب رقم واتساب صحيح", "Enter a valid WhatsApp number"))
            return
        }
        if (cleanAltPhone.isNotBlank() && cleanAltPhone.length < 8) {
            onResult(null, tr("رقم الموبايل البديل غير صحيح", "Alternate phone is invalid"))
            return
        }
        if (cleanPhone == cleanAltPhone && cleanAltPhone.isNotBlank()) {
            onResult(null, tr("رقم الموبايل البديل مطابق للرقم الأساسي", "Alternate phone matches primary phone"))
            return
        }
        if (cleanAge.isNotBlank() && (cleanAge.toIntOrNull() == null || cleanAge.toInt() !in 0..120)) {
            onResult(null, tr("اكتب سن صحيح", "Enter a valid age"))
            return
        }
        val normalizedGender = when (gender) {
            "Male" -> "ذكر"
            "Female" -> "أنثى"
            else -> gender
        }
        // V73: gender is a legacy optional field and is no longer required in the simplified customer form.

        val numbersToCheck = listOf(cleanPhone, cleanAltPhone).filter { it.isNotBlank() }.distinct()
        findPotentialDuplicateCustomer(
            name = cleanName,
            age = cleanAge,
            birthDate = cleanBirthDate,
            gender = normalizedGender,
            address = address.trim(),
            existingId = existing?.id
        ) { potentialDuplicate ->
            if (potentialDuplicate != null) {
                onResult(null, duplicateCustomerMessage(potentialDuplicate))
            } else {
                findDuplicatePhone(numbersToCheck, existing?.id) { duplicate ->
                    if (duplicate != null) {
                        onResult(null, duplicatePhoneMessage(duplicate))
                    } else {
                        val now = System.currentTimeMillis()
            val customerRef = existing?.let { firestore.collection(CUSTOMERS_COLLECTION).document(it.id) }
                ?: firestore.collection(CUSTOMERS_COLLECTION).document()
            val fileNumber = existing?.fileNumber?.takeIf { it.isNotBlank() } ?: generateFileNumber(now)
            val currentUid = currentUid()
            val currentEmail = currentEmail()

            val customer = Customer(
                id = customerRef.id,
                fileNumber = fileNumber,
                name = cleanName,
                phone = phone.trim(),
                alternatePhone = alternatePhone.trim(),
                age = cleanAge,
                birthDate = cleanBirthDate,
                gender = normalizedGender,
                address = address.trim(),
                notes = notes.trim(),
                importantAlert = importantAlert.trim(),
                tags = cleanTags,
                defaultDiscountPercent = safeDefaultDiscount,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
                createdByUid = existing?.createdByUid ?: currentUid,
                updatedByUid = currentUid,
                isBlacklisted = existing?.isBlacklisted ?: false,
                blacklistReason = existing?.blacklistReason.orEmpty(),
                blacklistedAtMillis = existing?.blacklistedAtMillis ?: 0L,
                isArchived = existing?.isArchived ?: false,
                archivedAtMillis = existing?.archivedAtMillis ?: 0L
            )

            val data = mutableMapOf<String, Any>(
                "file_number" to fileNumber,
                "name" to cleanName,
                "name_search" to normalizeText(cleanName),
                "phone" to phone.trim(),
                "phone_normalized" to cleanPhone,
                "alternate_phone" to alternatePhone.trim(),
                "alternate_phone_normalized" to cleanAltPhone,
                "age" to cleanAge,
                "birth_date" to cleanBirthDate,
                "gender" to normalizedGender,
                "address" to address.trim(),
                "address_search" to normalizeText(address),
                "notes" to notes.trim(),
                "important_alert" to importantAlert.trim(),
                "tags" to cleanTags,
                "tags_search" to normalizeText(cleanTags.joinToString(" ")),
                "default_discount_percent" to safeDefaultDiscount,
                "updated_at_ms" to now,
                "updated_at" to FieldValue.serverTimestamp(),
                "updated_by_uid" to currentUid,
                "updated_by_email" to currentEmail
            )
            if (existing == null) {
                data["created_at_ms"] = now
                data["created_at"] = FieldValue.serverTimestamp()
                data["created_by_uid"] = currentUid
                data["created_by_email"] = currentEmail
                data["is_blacklisted"] = false
                data["blacklist_reason"] = ""
                data["blacklisted_at_ms"] = 0L
                data["is_archived"] = false
                data["archived_at_ms"] = 0L
            }

            val newNumbers = numbersToCheck.toSet()
            val oldNumbers = existing?.let {
                setOf(normalizePhone(it.phone), normalizePhone(it.alternatePhone)).filter { n -> n.isNotBlank() }.toSet()
            } ?: emptySet()

            val action = if (existing == null) "customer_create" else "customer_update"
            val title = if (existing == null) "إنشاء عميل" else tr("تعديل بيانات العميل", "Edit Customer")
            val changes = describeCustomerChanges(existing, customer)
            val detail = if (changes.isBlank()) "$cleanName • $fileNumber" else changes
            queueOfflineAuditIfNeeded(action, "customer", customer.id, title, detail, customerId = customer.id)

            val offlineAtStart = !_isOnline.value

            fun commitCustomerWrite() {
                val batchTask = firestore.runBatch { batch ->
                    batch.set(customerRef, data, SetOptions.merge())
                    newNumbers.forEach { number ->
                        val registryRef = firestore.collection(PHONE_REGISTRY_COLLECTION).document(number)
                        batch.set(
                            registryRef,
                            mapOf(
                                "customer_id" to customerRef.id,
                                "phone" to number,
                                "updated_at_ms" to now,
                                "updated_by_uid" to currentUid
                            ),
                            SetOptions.merge()
                        )
                    }
                    (oldNumbers - newNumbers).forEach { number ->
                        batch.delete(firestore.collection(PHONE_REGISTRY_COLLECTION).document(number))
                    }
                }

                if (offlineAtStart) {
                    _customers.value = listOf(customer) + _customers.value.filterNot { it.id == customer.id }
                    addCustomerActivity(customer.id, action, title, detail)
                    onResult(customer, (if (existing == null) "تم إنشاء ملف العميل $fileNumber" else "تم تحديث بيانات العميل") + " • في انتظار المزامنة")
                }

                batchTask.addOnSuccessListener {
                    ShadowBackupReplicator.mirrorPath(customerRef.path, now)
                    newNumbers.forEach { number ->
                        ShadowBackupReplicator.mirrorPath(firestore.collection(PHONE_REGISTRY_COLLECTION).document(number).path, now)
                    }
                    (oldNumbers - newNumbers).forEach { number ->
                        ShadowBackupReplicator.mirrorPath(
                            firestore.collection(PHONE_REGISTRY_COLLECTION).document(number).path,
                            now,
                            tombstone = true
                        )
                    }
                    _customers.value = listOf(customer) + _customers.value.filterNot { it.id == customer.id }
                    logAudit(action, "customer", customer.id, title, detail, customerId = customer.id)
                    if (!offlineAtStart) {
                        addCustomerActivity(customer.id, action, title, detail)
                        onResult(customer, if (existing == null) "تم إنشاء ملف العميل $fileNumber" else "تم تحديث بيانات العميل")
                    }
                }.addOnFailureListener {
                    if (!offlineAtStart) onResult(null, "تعذر حفظ بيانات العميل. تأكد من صلاحيات Firebase")
                    else _systemMessage.value = "تعذر مزامنة بيانات العميل ${customer.name}"
                }
            }

            // When online, reject a stale edit before it overwrites another user's newer customer edit.
            if (existing != null && !offlineAtStart) {
                customerRef.get(Source.SERVER)
                    .addOnSuccessListener { serverDoc ->
                        val serverUpdated = serverDoc.getLong("updated_at_ms") ?: existing.updatedAtMillis
                        if (serverUpdated > existing.updatedAtMillis + 1L) {
                            onResult(
                                null,
                                tr(
                                    "تم تعديل العميل بواسطة مستخدم آخر. افتح الملف من جديد قبل الحفظ.",
                                    "This customer was changed by another user. Reopen the record before saving."
                                )
                            )
                            refreshDataIntegrity()
                        } else {
                            commitCustomerWrite()
                        }
                    }
                    .addOnFailureListener {
                        onResult(null, tr("تعذر التحقق من أحدث نسخة للعميل", "Could not verify the latest customer version"))
                    }
            } else {
                commitCustomerWrite()
            }
                    }
                }
            }
        }
    }

    /** V42: strong duplicate guard beyond phone number.
     * Matches exact normalized name plus DOB, or exact normalized name + age + gender + address.
     * This intentionally avoids blocking common names on name alone.
     */
    private fun findPotentialDuplicateCustomer(
        name: String,
        age: String,
        birthDate: String,
        gender: String,
        address: String,
        existingId: String?,
        callback: (Customer?) -> Unit
    ) {
        val normName = normalizeText(name)
        val normAddress = normalizeText(address)
        fun strongMatch(customer: Customer): Boolean {
            if (customer.id == existingId || customer.isArchived) return false
            if (normalizeText(customer.name) != normName) return false
            val dobMatch = birthDate.isNotBlank() && customer.birthDate.isNotBlank() &&
                customer.birthDate.trim() == birthDate.trim()
            val profileMatch = age.isNotBlank() && address.isNotBlank() &&
                customer.age.trim() == age.trim() &&
                customer.gender.trim() == gender.trim() &&
                normalizeText(customer.address) == normAddress
            return dobMatch || profileMatch
        }
        val localDuplicate = _customers.value.firstOrNull(::strongMatch)
        if (localDuplicate != null) {
            callback(localDuplicate)
            return
        }
        if (!_isOnline.value || normName.isBlank()) {
            callback(null)
            return
        }
        firestore.collection(CUSTOMERS_COLLECTION)
            .whereEqualTo("name_search", normName)
            .limit(10)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                callback(snapshot.documents.mapNotNull(::parseCustomer).firstOrNull(::strongMatch))
            }
            .addOnFailureListener { callback(null) }
    }

    private fun duplicateCustomerMessage(customer: Customer): String {
        val file = customer.fileNumber.takeIf { it.isNotBlank() }?.let { " • ملف $it" }.orEmpty()
        return "ممكن يكون العميل موجود بالفعل: ${customer.name}$file • افتح الملف الحالي بدل إنشاء ملف مكرر"
    }

    private fun findDuplicatePhone(numbers: List<String>, existingId: String?, callback: (Customer?) -> Unit) {
        // First inspect already-loaded customers. This catches legacy V6-V13 records that do not
        // yet have phone_normalized / phone_registry fields and prevents the old "number exists but
        // search cannot find it" case from returning.
        val localDuplicate = _customers.value.firstOrNull { customer ->
            customer.id != existingId && numbers.any { number ->
                normalizePhone(customer.phone) == number || normalizePhone(customer.alternatePhone) == number
            }
        }
        if (localDuplicate != null) {
            callback(localDuplicate)
            return
        }

        fun check(index: Int, fieldIndex: Int) {
            if (index >= numbers.size) {
                callback(null)
                return
            }
            val number = numbers[index]
            val field = if (fieldIndex == 0) "phone_normalized" else "alternate_phone_normalized"
            firestore.collection(CUSTOMERS_COLLECTION)
                .whereEqualTo(field, number)
                .limit(3)
                .get(Source.SERVER)
                .addOnSuccessListener { snapshot ->
                    val duplicateDoc = snapshot.documents.firstOrNull { it.id != existingId }
                    if (duplicateDoc != null) {
                        callback(parseCustomer(duplicateDoc))
                    } else if (fieldIndex == 0) {
                        check(index, 1)
                    } else {
                        check(index + 1, 0)
                    }
                }
                .addOnFailureListener {
                    // Registry transaction is the final uniqueness guard. Continue if a legacy query
                    // temporarily fails instead of blocking all customer updates.
                    if (fieldIndex == 0) check(index, 1) else check(index + 1, 0)
                }
        }
        check(0, 0)
    }

    private fun duplicatePhoneMessage(customer: Customer): String {
        val status = when {
            customer.isArchived -> "محذوف/مؤرشف - راجع المدير"
            customer.isBlacklisted -> tr("بلاك ليست", "Blacklist")
            else -> "نشط"
        }
        val file = customer.fileNumber.takeIf { it.isNotBlank() }?.let { " • ملف $it" }.orEmpty()
        return "رقم الموبايل مسجل للعميل ${customer.name}$file • الحالة: $status"
    }

    private fun describeCustomerChanges(old: Customer?, new: Customer): String {
        if (old == null) return "${new.name} • ملف ${new.fileNumber}"
        val changes = mutableListOf<String>()
        fun changed(label: String, before: String, after: String) {
            if (before.trim() != after.trim()) changes += "$label: ${before.ifBlank { "—" }} ← ${after.ifBlank { "—" }}"
        }
        changed(tr("الاسم", "Name"), old.name, new.name)
        changed(tr("الموبايل", "Phone"), old.phone, new.phone)
        changed("الموبايل البديل", old.alternatePhone, new.alternatePhone)
        changed(tr("السن", "Age"), old.age, new.age)
        changed(tr("تاريخ الميلاد", "Date of Birth"), old.birthDate, new.birthDate)
        changed(tr("النوع", "Gender"), old.gender, new.gender)
        changed(tr("العنوان", "Address"), old.address, new.address)
        changed("الملاحظات", old.notes, new.notes)
        changed("التنبيه", old.importantAlert, new.importantAlert)
        changed("Tags", old.tags.joinToString(", "), new.tags.joinToString(", "))
        if (kotlin.math.abs(old.defaultDiscountPercent - new.defaultDiscountPercent) > 0.001) {
            changes += "خصم افتراضي: ${formatNumber(old.defaultDiscountPercent)}% ← ${formatNumber(new.defaultDiscountPercent)}%"
        }
        return changes.joinToString(" | ").ifBlank { "تم حفظ الملف بدون تغييرات جوهرية" }
    }

    fun setCustomerBlacklist(
        customer: Customer,
        blacklisted: Boolean,
        reason: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val now = System.currentTimeMillis()
        val data = mapOf(
            "is_blacklisted" to blacklisted,
            "blacklist_reason" to if (blacklisted) reason.trim() else "",
            "blacklisted_at_ms" to if (blacklisted) now else 0L,
            "blacklisted_by_uid" to if (blacklisted) currentUid() else "",
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid()
        )
        queueOfflineAuditIfNeeded(
            action = "customer_blacklist",
            entityType = "customer",
            entityId = customer.id,
            title = if (blacklisted) tr("إضافة للبلاك ليست", "Blacklist") else tr("فك الحظر", "Remove Blacklist"),
            details = if (blacklisted) reason.trim().ifBlank { "بدون سبب مسجل" } else "إعادة العميل للحالة النشطة",
            customerId = customer.id
        )
        privilegedFirestore().collection(CUSTOMERS_COLLECTION).document(customer.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                loadCustomers()
                val title = if (blacklisted) tr("إضافة للبلاك ليست", "Blacklist") else tr("فك الحظر", "Remove Blacklist")
                val detail = if (blacklisted) reason.trim().ifBlank { "بدون سبب مسجل" } else "تمت إعادة العميل للحالة النشطة"
                logAudit("customer_blacklist", "customer", customer.id, title, detail, customerId = customer.id)
                addCustomerActivity(customer.id, "blacklist", title, detail)
                onResult(true, if (blacklisted) "تمت إضافة العميل للبلاك ليست" else "تمت إزالة العميل من البلاك ليست")
            }
            .addOnFailureListener { onResult(false, "تعذر تحديث حالة العميل") }
    }

    fun setCustomerArchived(
        customer: Customer,
        archived: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الإجراء متاح للمدير فقط", "Manager only action"))
            return
        }
        val now = System.currentTimeMillis()
        val data = mutableMapOf<String, Any>(
            "is_archived" to archived,
            "archived_at_ms" to if (archived) now else 0L,
            "archived_by_uid" to if (archived) currentUid() else "",
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid()
        )
        if (archived) {
            data["is_blacklisted"] = false
            data["blacklist_reason"] = ""
            data["blacklisted_at_ms"] = 0L
        }
        queueOfflineAuditIfNeeded(
            action = "customer_archive",
            entityType = "customer",
            entityId = customer.id,
            title = if (archived) "أرشفة العميل" else tr("استرجاع العميل", "Restore Customer"),
            details = if (archived) "حذف آمن مع الاحتفاظ بالسجل" else "إعادة العميل للقائمة النشطة",
            customerId = customer.id
        )
        privilegedFirestore().collection(CUSTOMERS_COLLECTION).document(customer.id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                loadCustomers()
                val title = if (archived) "أرشفة العميل" else tr("استرجاع العميل", "Restore Customer")
                val detail = if (archived) "حذف آمن مع الاحتفاظ بالسجل" else "إعادة العميل للقائمة النشطة"
                logAudit("customer_archive", "customer", customer.id, title, detail, customerId = customer.id)
                addCustomerActivity(customer.id, "archive", title, detail)
                onResult(true, if (archived) "تم حذف العميل من القائمة مع الاحتفاظ بسجله" else "تم استرجاع العميل")
            }
            .addOnFailureListener { onResult(false, "تعذر تحديث حالة العميل") }
    }

    fun loadCustomerActivity(customerId: String) {
        _customerActivity.value = emptyList()
        if (customerId.isBlank()) return
        firestore.collection(CUSTOMERS_COLLECTION).document(customerId).collection("activity")
            .orderBy("created_at_ms", Query.Direction.DESCENDING)
            .limit(CUSTOMER_ACTIVITY_LIMIT)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                _customerActivity.value = snapshot.documents.map { doc ->
                    CustomerActivityEntry(
                        id = doc.id,
                        type = doc.getString("type").orEmpty(),
                        title = doc.getString("title").orEmpty(),
                        details = doc.getString("details").orEmpty(),
                        actorUid = doc.getString("actor_uid").orEmpty(),
                        actorEmail = doc.getString("actor_email").orEmpty(),
                        createdAtMillis = doc.getLong("created_at_ms") ?: 0L
                    )
                }.sortedByDescending { it.createdAtMillis }.take(CUSTOMER_ACTIVITY_LIMIT.toInt())
            }
    }

    private fun addCustomerActivity(customerId: String, type: String, title: String, details: String) {
        if (customerId.isBlank()) return
        val ref = firestore.collection(CUSTOMERS_COLLECTION).document(customerId).collection("activity").document()
        val now = System.currentTimeMillis()
        ref.set(
            mapOf(
                "type" to type,
                "title" to title,
                "details" to details.take(1500),
                "actor_uid" to currentUid(),
                "actor_email" to currentEmail(),
                "created_at_ms" to now,
                "created_at" to FieldValue.serverTimestamp()
            )
        )
            .addOnSuccessListener { ShadowBackupReplicator.mirrorPath(ref.path, now) }
    }

    // ------------------------- ORDERS / PAYMENTS -------------------------

    fun loadCustomerOrders(customerId: String, preserveCurrent: Boolean = false) {
        if (!hasOperationalAccess()) {
            customerOrdersListener?.remove()
            customerOrdersListener = null
            customerOrdersCustomerId = null
            if (!preserveCurrent) _customerOrders.value = emptyList()
            _customerOrdersLoading.value = false
            return
        }
        if (customerId.isBlank()) {
            customerOrdersListener?.remove()
            customerOrdersListener = null
            customerOrdersCustomerId = null
            if (!preserveCurrent) _customerOrders.value = emptyList()
            _customerOrdersLoading.value = false
            return
        }

        if (customerOrdersCustomerId == customerId && customerOrdersListener != null) {
            return
        }

        customerOrdersListener?.remove()
        customerOrdersListener = null
        customerOrdersCustomerId = customerId
        if (!preserveCurrent) _customerOrders.value = emptyList()
        _customerOrdersLoading.value = true

        val query = firestore.collection(CUSTOMERS_COLLECTION)
            .document(customerId)
            .collection("orders")
            .orderBy("created_at_ms", Query.Direction.DESCENDING)
            .limit(CUSTOMER_ORDERS_LIMIT)

        customerOrdersListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (!_isOnline.value) {
                    query.get(Source.CACHE)
                        .addOnSuccessListener { cached ->
                            _customerOrders.value = cached.documents.mapNotNull { parseOrder(it, customerId) }
                                .sortedByDescending { it.createdAtMillis }
                            _customerOrdersLoading.value = false
                        }
                        .addOnFailureListener { _customerOrdersLoading.value = false }
                } else {
                    _customerOrdersLoading.value = false
                }
                return@addSnapshotListener
            }
            if (snapshot != null) {
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                _customerOrders.value = snapshot.documents.mapNotNull { parseOrder(it, customerId) }
                    .sortedByDescending { it.createdAtMillis }
                _customerOrdersLoading.value = false
            }
        }
    }

    /** V119 privacy boundary: the lab reads only /lab_orders mirrors. Financial fields never enter this document. */
    private fun labMirrorData(input: Map<String, Any?>): Map<String, Any?> {
        val allowed = setOf(
            "order_number", "operation_id", "customer_id", "customer_file_number",
            "customer_name", "customer_phone", "customer_age", "customer_gender",
            "items", "tests_count", "workflow_status", "status_history", "notes",
            "created_at_ms", "created_at", "created_by_uid", "created_by_email",
            "updated_at_ms", "updated_at", "updated_by_uid", "updated_by_email",
            "edit_count", "is_voided", "void_reason", "voided_at_ms", "voided_by_uid", "voided_by_email",
            "result_urls", "result_names", "result_sent_at_ms", "result_sent_at",
            "result_uploaded_by_uid", "result_uploaded_by_email"
        )
        val out = mutableMapOf<String, Any?>()
        input.forEach { (key, value) -> if (key in allowed) out[key] = value }
        val rawItems = input["items"] as? List<*>
        if (rawItems != null) {
            out["items"] = rawItems.mapNotNull { raw ->
                val item = raw as? Map<*, *> ?: return@mapNotNull null
                val testId = (item["test_id"] as? Number)?.toInt() ?: return@mapNotNull null
                mapOf(
                    "test_id" to testId,
                    "english_name" to item["english_name"]?.toString().orEmpty(),
                    "arabic_name" to item["arabic_name"]?.toString().orEmpty(),
                    "market_name" to item["market_name"]?.toString().orEmpty()
                )
            }
        }
        out["privacy_schema"] = 1
        return out
    }

    private fun labMirrorRef(orderId: String) = firestore.collection(LAB_ORDERS_COLLECTION).document(orderId)

    private fun parseOrder(doc: DocumentSnapshot, fallbackCustomerId: String = ""): CustomerOrder? {
        val rawItems = doc.get("items") as? List<*> ?: emptyList<Any>()
        val items = rawItems.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val testId = (map["test_id"] as? Number)?.toInt() ?: return@mapNotNull null
            CustomerOrderItem(
                testId = testId,
                englishName = map["english_name"]?.toString().orEmpty(),
                arabicName = map["arabic_name"]?.toString().orEmpty(),
                marketName = map["market_name"]?.toString().orEmpty(),
                customerPrice = numberAsDouble(map["customer_price"])
            )
        }
        val finalTotal = numberAsDouble(doc.get("total_customer_price"))
        val subtotal = numberAsDouble(doc.get("subtotal_customer_price")).takeIf { it > 0.0 } ?: finalTotal
        val discount = numberAsDouble(doc.get("discount_amount")).takeIf { it > 0.0 }
            ?: (subtotal - finalTotal).coerceAtLeast(0.0)
        val paid = numberAsDouble(doc.get("paid_amount")).takeIf { it > 0.0 }
            ?: if (doc.getString("payment_status") == "paid") finalTotal else 0.0
        return CustomerOrder(
            id = doc.id,
            orderNumber = doc.getString("order_number").orEmpty(),
            customerId = doc.getString("customer_id").orEmpty().ifBlank { fallbackCustomerId },
            customerFileNumber = doc.getString("customer_file_number").orEmpty(),
            customerName = doc.getString("customer_name").orEmpty(),
            customerPhone = doc.getString("customer_phone").orEmpty(),
            customerAge = doc.getString("customer_age").orEmpty(),
            customerGender = doc.getString("customer_gender").orEmpty(),
            items = items,
            subtotalCustomerPrice = subtotal,
            discountAmount = discount,
            discountPercent = numberAsDouble(doc.get("discount_percent")).takeIf { it > 0.0 }
                ?: if (subtotal > 0.0) (discount / subtotal) * 100.0 else 0.0,
            totalCustomerPrice = finalTotal,
            paymentStatus = doc.getString("payment_status") ?: "unpaid",
            workflowStatus = doc.getString("workflow_status") ?: "new",
            paidAmount = paid.coerceAtMost(finalTotal),
            remainingAmount = if (doc.get("remaining_amount") != null) {
                numberAsDouble(doc.get("remaining_amount")).coerceAtLeast(0.0)
            } else {
                (finalTotal - paid).coerceAtLeast(0.0)
            },
            notes = doc.getString("notes").orEmpty(),
            createdAtMillis = doc.getLong("created_at_ms") ?: 0L,
            createdByUid = doc.getString("created_by_uid").orEmpty(),
            createdByEmail = doc.getString("created_by_email").orEmpty(),
            updatedAtMillis = doc.getLong("updated_at_ms") ?: 0L,
            updatedByUid = doc.getString("updated_by_uid").orEmpty(),
            editCount = (doc.getLong("edit_count") ?: 0L).toInt(),
            isVoided = doc.getBoolean("is_voided") ?: false,
            voidReason = doc.getString("void_reason").orEmpty(),
            resultUrls = (doc.get("result_urls") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            resultNames = (doc.get("result_names") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            resultSentAtMillis = doc.getLong("result_sent_at_ms") ?: 0L
        )
    }

    fun saveCustomerOrder(
        customer: Customer,
        tests: List<LabTest>,
        onResult: (Boolean, String) -> Unit
    ) {
        saveCustomerOrderAdvanced(
            customer = customer,
            tests = tests,
            discountAmount = 0.0,
            discountPercent = customer.defaultDiscountPercent,
            paymentStatus = "unpaid",
            paidAmount = 0.0,
            notes = "",
            onResult = { order, message -> onResult(order != null, message) }
        )
    }

    fun saveCustomerOrderAdvanced(
        customer: Customer,
        tests: List<LabTest>,
        discountAmount: Double,
        discountPercent: Double,
        paymentStatus: String,
        paidAmount: Double,
        notes: String,
        onResult: (CustomerOrder?, String) -> Unit
    ) {
        if (!hasOperationalAccess()) {
            onResult(null, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (customer.isBlacklisted) {
            onResult(null, "العميل موجود في البلاك ليست. لازم المدير يفك الحظر قبل إنشاء طلب جديد")
            return
        }
        if (customer.isArchived) {
            onResult(null, tr("العميل مؤرشف. استرجعه الأول", "Customer is archived. Restore it first."))
            return
        }
        if (tests.isEmpty()) {
            onResult(null, tr("اختار تحليل واحد على الأقل", "Select at least one test"))
            return
        }
        val now = System.currentTimeMillis()
        val orderRef = firestore.collection(CUSTOMERS_COLLECTION)
            .document(customer.id).collection("orders").document()
        val items = tests.map { test ->
            val price = parsePriceInput(_customerPriceOverrides.value[test.id] ?: test.customerPrice.orEmpty()) ?: 0.0
            mapOf(
                "test_id" to test.id,
                "english_name" to test.englishName,
                "arabic_name" to test.arabicName,
                "market_name" to test.marketName,
                "customer_price" to price
            )
        }
        val subtotal = items.sumOf { numberAsDouble(it["customer_price"]) }
        val safeDiscountPercent = if (discountPercent > 0.0) discountPercent.coerceIn(0.0, 100.0) else 0.0
        val discountFromPercent = subtotal * safeDiscountPercent / 100.0
        val safeDiscount = if (safeDiscountPercent > 0.0) discountFromPercent else discountAmount.coerceIn(0.0, subtotal)
        val actualPercent = if (subtotal > 0.0) (safeDiscount / subtotal * 100.0).coerceIn(0.0, 100.0) else 0.0
        val finalTotal = (subtotal - safeDiscount).coerceAtLeast(0.0)
        val normalizedStatus = when (paymentStatus) { "paid", "partial", "unpaid" -> paymentStatus; else -> "unpaid" }
        val safePaid = when (normalizedStatus) {
            "paid" -> finalTotal
            "partial" -> paidAmount.coerceIn(0.0, finalTotal)
            else -> 0.0
        }
        if (safeDiscount > 0.001 && !canDiscountOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية الخصومات", "Your account cannot apply discounts"))
            return
        }
        if ((safePaid > 0.001 || normalizedStatus != "unpaid") && !canCollectPaymentsOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية التحصيل أو تغيير حالة الدفع", "Your account cannot collect payments or change payment status"))
            return
        }
        val remaining = (finalTotal - safePaid).coerceAtLeast(0.0)
        val orderNumber = generateOrderNumber(now, orderRef.id)
        val uid = currentUid()
        val email = currentEmail()
        val order = CustomerOrder(
            id = orderRef.id,
            orderNumber = orderNumber,
            customerId = customer.id,
            customerFileNumber = customer.fileNumber,
            customerName = customer.name,
            customerPhone = customer.phone,
            customerAge = customer.age,
            customerGender = customer.gender,
            items = items.map { raw ->
                CustomerOrderItem(
                    testId = (raw["test_id"] as Number).toInt(),
                    englishName = raw["english_name"].toString(),
                    arabicName = raw["arabic_name"].toString(),
                    marketName = raw["market_name"].toString(),
                    customerPrice = numberAsDouble(raw["customer_price"])
                )
            },
            subtotalCustomerPrice = subtotal,
            discountAmount = safeDiscount,
            discountPercent = actualPercent,
            totalCustomerPrice = finalTotal,
            paymentStatus = normalizedStatus,
            workflowStatus = "sent_to_lab",
            paidAmount = safePaid,
            remainingAmount = remaining,
            notes = notes.trim(),
            createdAtMillis = now,
            createdByUid = uid,
            createdByEmail = email,
            updatedAtMillis = now,
            updatedByUid = uid
        )
        val data = mapOf(
            "order_number" to orderNumber,
            "operation_id" to orderRef.id,
            "customer_id" to customer.id,
            "customer_file_number" to customer.fileNumber,
            "customer_name" to customer.name,
            "customer_phone" to customer.phone,
            "customer_age" to customer.age,
            "customer_gender" to customer.gender,
            "items" to items,
            "tests_count" to items.size,
            "subtotal_customer_price" to subtotal,
            "discount_amount" to safeDiscount,
            "discount_percent" to actualPercent,
            "total_customer_price" to finalTotal,
            "payment_status" to normalizedStatus,
            "workflow_status" to "sent_to_lab",
            "status_history" to listOf(
                mapOf(
                    "status" to "sent_to_lab",
                    "at_ms" to now,
                    "by_uid" to uid,
                    "by_email" to email
                )
            ),
            "paid_amount" to safePaid,
            "remaining_amount" to remaining,
            "notes" to notes.trim(),
            "created_at_ms" to now,
            "created_at" to FieldValue.serverTimestamp(),
            "created_by_uid" to uid,
            "created_by_email" to email,
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to uid,
            "edit_count" to 0,
            "is_voided" to false,
            "void_reason" to ""
        )

        // V119: the clinic keeps the financial order private; the lab receives only a sanitized mirror.

        queueOfflineAuditIfNeeded(
            action = "order_create",
            entityType = "order",
            entityId = order.id,
            title = "إنشاء طلب $orderNumber",
            details = "$orderNumber • ${items.size} تحليل • إجمالي ${formatNumber(finalTotal)}",
            customerId = customer.id,
            orderId = order.id
        )

        val offlineAtStart = !_isOnline.value
        val batchTask = firestore.runBatch { batch ->
            batch.set(orderRef, data)
            batch.set(labMirrorRef(orderRef.id), labMirrorData(data))
            if (safePaid > 0.0) {
                val paymentRef = orderRef.collection("payments").document()
                batch.set(
                    paymentRef,
                    mapOf(
                        "operation_id" to paymentRef.id,
                        "customer_id" to customer.id,
                        "order_id" to orderRef.id,
                        "amount" to safePaid,
                        "note" to "دفعة عند إنشاء الطلب",
                        "created_at_ms" to now,
                        "created_at" to FieldValue.serverTimestamp(),
                        "created_by_uid" to uid,
                        "created_by_email" to email
                    )
                )
            }
        }

        if (offlineAtStart) {
            _customerOrders.value = listOf(order) + _customerOrders.value.filterNot { it.id == order.id }
            onResult(order, "تم حفظ الطلب على الجهاز • في انتظار المزامنة عند رجوع الإنترنت")
        }

        batchTask.addOnSuccessListener {
            ShadowBackupReplicator.mirrorPath(orderRef.path, now)
            ShadowBackupReplicator.mirrorPath(labMirrorRef(order.id).path, now)
            // V64: show the saved order in the customer file immediately,
            // then refresh from Firestore for the authoritative snapshot.
            _customerOrders.value = listOf(order) + _customerOrders.value.filterNot { it.id == order.id }
            loadCustomerOrders(customer.id, preserveCurrent = true)
            val detail = "$orderNumber • ${items.size} تحليل • إجمالي ${formatNumber(finalTotal)} • خصم ${formatNumber(safeDiscount)}"
            logAudit("order_create", "order", order.id, "إنشاء طلب", detail, customerId = customer.id, orderId = order.id)
            addCustomerActivity(customer.id, "order", "طلب جديد $orderNumber", detail)
            if (!offlineAtStart) {
                sendExternalOrderPush("new_order", customer.id, order.id)
                onResult(order, "تم حفظ وإرسال الطلب $orderNumber للمعمل")
            }
        }.addOnFailureListener {
            if (!offlineAtStart) onResult(null, "تعذر حفظ الطلب. تأكد من صلاحيات Firebase")
            else _systemMessage.value = "تعذر مزامنة الطلب $orderNumber بعد عودة الإنترنت"
        }
    }

    /** V42 operational order status: independent from payment status. */
    fun updateOrderWorkflowStatus(
        customer: Customer,
        order: CustomerOrder,
        workflowStatus: String,
        onResult: (CustomerOrder?, String) -> Unit
    ) {
        if (!hasOperationalAccess()) {
            onResult(null, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (order.isVoided) {
            onResult(null, tr("الطلب ملغي", "Order is voided"))
            return
        }
        val allowed = setOf("new", "sent_to_lab", "processing", "ready", "delivered")
        if (workflowStatus !in allowed) {
            onResult(null, tr("حالة الطلب غير صحيحة", "Invalid order status"))
            return
        }
        if (workflowStatus == order.workflowStatus) {
            onResult(order, tr("حالة الطلب بدون تغيير", "Order status unchanged"))
            return
        }
        val now = System.currentTimeMillis()
        val orderRef = firestore.collection(CUSTOMERS_COLLECTION)
            .document(customer.id).collection("orders").document(order.id)
        val updated = order.copy(
            workflowStatus = workflowStatus,
            updatedAtMillis = now,
            updatedByUid = currentUid(),
            editCount = order.editCount + 1
        )
        val statusAr = when (workflowStatus) {
            "sent_to_lab" -> "قيد التنفيذ"
            "processing" -> "جاري التنفيذ"
            "ready" -> "النتائج جاهزة"
            "delivered" -> "تم التسليم"
            else -> "جديد"
        }
        val detail = "${order.orderNumber} • حالة التشغيل: $statusAr"
        queueOfflineAuditIfNeeded(
            action = "order_workflow_status",
            entityType = "order",
            entityId = order.id,
            title = "تحديث حالة الطلب",
            details = detail,
            customerId = customer.id,
            orderId = order.id
        )
        val offlineAtStart = !_isOnline.value
        val workflowPatch = mapOf<String, Any?>(
            "workflow_status" to workflowStatus,
            "status_history" to FieldValue.arrayUnion(
                mapOf(
                    "status" to workflowStatus,
                    "at_ms" to now,
                    "by_uid" to currentUid(),
                    "by_email" to currentEmail()
                )
            ),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail(),
            "edit_count" to FieldValue.increment(1)
        )
        val task = firestore.runBatch { batch ->
            batch.update(orderRef, workflowPatch)
            batch.update(labMirrorRef(order.id), labMirrorData(workflowPatch))
        }
        if (offlineAtStart) {
            _customerOrders.value = _customerOrders.value.map { if (it.id == order.id) updated else it }
            addCustomerActivity(customer.id, "order_status", "تحديث حالة الطلب", detail)
            onResult(updated, "تم تحديث الحالة على الجهاز • في انتظار المزامنة")
        }
        task.addOnSuccessListener {
            ShadowBackupReplicator.mirrorPath(orderRef.path, now)
            ShadowBackupReplicator.mirrorPath(labMirrorRef(order.id).path, now)
            _customerOrders.value = _customerOrders.value.map { if (it.id == order.id) updated else it }
            logAudit("order_workflow_status", "order", order.id, "تحديث حالة الطلب", detail, customerId = customer.id, orderId = order.id)
            if (!offlineAtStart) {
                addCustomerActivity(customer.id, "order_status", "تحديث حالة الطلب", detail)
                onResult(updated, "تم تحديث حالة الطلب")
            }
        }.addOnFailureListener {
            if (!offlineAtStart) onResult(null, tr("تعذر تحديث حالة الطلب", "Unable to update order status"))
            else _systemMessage.value = "تعذر مزامنة حالة ${order.orderNumber}"
        }
    }



    /** V107: live clinic-side notifications for lab actions without requiring a new backend.
     * While the app is alive this listener is immediate; the periodic background job is the fallback
     * when Android has suspended the app. */
    fun startClinicOrderNotificationsRealtime() {
        val profile = operationalProfile()
        if (normalizeUserRole(profile?.role.orEmpty()) == "lab_operator" || profile == null) {
            stopClinicOrderNotificationsRealtime()
            return
        }
        stopClinicOrderNotificationsRealtime()
        clinicRealtimeInitialized = false
        clinicRealtimeOrders = emptyMap()

        clinicOrdersListener = firestore.collectionGroup("orders")
            .orderBy("updated_at_ms", Query.Direction.DESCENDING)
            .limit(REALTIME_ORDER_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                val next = snapshot.documents.mapNotNull { parseOrder(it) }.associateBy { it.id }

                if (clinicRealtimeInitialized) {
                    next.values.forEach { order ->
                        val previous = clinicRealtimeOrders[order.id]
                        val changedByAnotherUser = order.updatedByUid.isBlank() || order.updatedByUid != currentUid()
                        if (!changedByAnotherUser) return@forEach

                        if (order.isVoided && previous?.isVoided != true) {
                            OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                getApplication<Application>(),
                                "clinic_cancel_${order.id}_${order.updatedAtMillis}",
                                "تم إلغاء الطلب",
                                "${order.orderNumber} • ${order.customerName}",
                                orderId = order.id
                            )
                        } else if (!order.isVoided && order.workflowStatus == "processing" &&
                            previous?.workflowStatus != "processing") {
                            OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                getApplication<Application>(),
                                "clinic_processing_${order.id}_${order.updatedAtMillis}",
                                "المعمل استلم الطلب",
                                "${order.orderNumber} • ${order.customerName}",
                                orderId = order.id
                            )
                        } else if (!order.isVoided && order.workflowStatus == "ready" &&
                            previous?.workflowStatus != "ready") {
                            OrderNotificationManager.notifyOrderUpdateIfNeeded(
                                getApplication<Application>(),
                                "clinic_ready_${order.id}_${order.updatedAtMillis}",
                                "نتيجة التحاليل جاهزة",
                                "${order.orderNumber} • ${order.customerName}",
                                orderId = order.id
                            )
                        }
                    }
                }

                clinicRealtimeOrders = next
                clinicRealtimeInitialized = true
            }
    }

    fun stopClinicOrderNotificationsRealtime() {
        clinicOrdersListener?.remove()
        clinicOrdersListener = null
        clinicRealtimeInitialized = false
        clinicRealtimeOrders = emptyMap()
    }

    /** V116: read the latest updated shared orders using the collection-group index.
     * Updated-at ordering keeps edited/returned orders visible even when they are older. */
    fun loadLabOrders(onResult: ((Boolean, String) -> Unit)? = null) {
        val profile = operationalProfile()
        if (normalizeUserRole(profile?.role.orEmpty()) != "lab_operator") {
            _labOrders.value = emptyList()
            onResult?.invoke(false, "هذه الشاشة مخصصة لحساب المعمل")
            return
        }
        _labOrdersLoading.value = true

        fun apply(snapshot: QuerySnapshot) {
            _labOrders.value = parseLabOrders(snapshot)
            _labOrdersLoading.value = false
            onResult?.invoke(true, if (_labOrders.value.isEmpty()) "لا توجد طلبات للمعمل" else "تم تحديث الطلبات")
        }

        firestore.collection(LAB_ORDERS_COLLECTION)
            .orderBy("updated_at_ms", Query.Direction.DESCENDING)
            .limit(REALTIME_ORDER_LIMIT)
            .get(Source.SERVER)
            .addOnSuccessListener(::apply)
            .addOnFailureListener {
                firestore.collection(LAB_ORDERS_COLLECTION)
                    .limit(REALTIME_ORDER_LIMIT)
                    .get(Source.SERVER)
                    .addOnSuccessListener(::apply)
                    .addOnFailureListener { error ->
                        _labOrdersLoading.value = false
                        onResult?.invoke(false, error.localizedMessage ?: "تعذر تحميل طلبات المعمل")
                    }
            }
    }

    /** Live listener while the lab screen is open. New sent_to_lab orders trigger a
     * lab-only local notification immediately without any additional backend. */
    fun startLabOrdersRealtime(onResult: ((Boolean, String) -> Unit)? = null) {
        val profile = operationalProfile()
        if (normalizeUserRole(profile?.role.orEmpty()) != "lab_operator") {
            onResult?.invoke(false, "هذه الشاشة مخصصة لحساب المعمل")
            return
        }

        stopLabOrdersRealtime()
        _labOrdersLoading.value = true
        labRealtimeInitialized = false

        fun attachFallback() {
            labOrdersListener?.remove()
            // Never attach an unbounded collection-group listener as a fallback.
            labOrdersListener = firestore.collection(LAB_ORDERS_COLLECTION)
                .limit(REALTIME_ORDER_LIMIT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        _labOrdersLoading.value = false
                        onResult?.invoke(false, error?.localizedMessage ?: "تعذر متابعة طلبات المعمل")
                        return@addSnapshotListener
                    }
                    applyLabRealtimeSnapshot(snapshot)
                    onResult?.invoke(true, if (_labOrders.value.isEmpty()) "لا توجد طلبات للمعمل" else "الطلبات متصلة مباشرة")
                }
        }

        labOrdersListener = firestore.collection(LAB_ORDERS_COLLECTION)
            .orderBy("updated_at_ms", Query.Direction.DESCENDING)
            .limit(REALTIME_ORDER_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    attachFallback()
                    return@addSnapshotListener
                }
                applyLabRealtimeSnapshot(snapshot)
                onResult?.invoke(true, if (_labOrders.value.isEmpty()) "لا توجد طلبات للمعمل" else "الطلبات متصلة مباشرة")
            }
    }

    fun stopLabOrdersRealtime() {
        labOrdersListener?.remove()
        labOrdersListener = null
        labRealtimeInitialized = false
    }

    private fun parseLabOrders(snapshot: QuerySnapshot): List<CustomerOrder> = snapshot.documents
        .mapNotNull { parseOrder(it) }
        .filter { it.workflowStatus in setOf("sent_to_lab", "processing", "ready") }
        .filterNot { it.isVoided }
        .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.createdAtMillis }

    private fun applyLabRealtimeSnapshot(snapshot: QuerySnapshot) {
        snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
        val previousById = _labOrders.value.associateBy { it.id }
        val rawNext = snapshot.documents.mapNotNull { parseOrder(it) }
        val next = rawNext
            .filter { it.workflowStatus in setOf("sent_to_lab", "processing", "ready") }
            .filterNot { it.isVoided }
            .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.createdAtMillis }

        if (labRealtimeInitialized) {
            rawNext.asSequence().take(500).forEach { order ->
                val previous = previousById[order.id]
                val changedByAnotherUser = order.updatedByUid.isBlank() || order.updatedByUid != currentUid()
                if (!changedByAnotherUser) return@forEach

                if (order.isVoided && previous?.isVoided != true) {
                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                        getApplication<Application>(),
                        "lab_cancel_${order.id}_${order.updatedAtMillis}",
                        "تم إلغاء طلب",
                        "${order.orderNumber} • ${order.customerName}",
                        labOnly = true,
                        orderId = order.id
                    )
                } else if (order.workflowStatus == "sent_to_lab" && previous == null) {
                    OrderNotificationManager.notifyNewLabOrderIfNeeded(
                        getApplication<Application>(),
                        order.id,
                        order.orderNumber,
                        order.customerName
                    )
                } else if (!order.isVoided && order.editCount > (previous?.editCount ?: 0) &&
                    order.workflowStatus in setOf("sent_to_lab", "processing")) {
                    OrderNotificationManager.notifyOrderUpdateIfNeeded(
                        getApplication<Application>(),
                        "lab_edit_${order.id}_${order.updatedAtMillis}",
                        "تم تعديل طلب تحاليل",
                        "${order.orderNumber} • ${order.customerName}",
                        labOnly = true,
                        orderId = order.id
                    )
                }
            }
        }

        _labOrders.value = next
        _labOrdersLoading.value = false
        labRealtimeInitialized = true
    }

    fun labAcceptOrder(order: CustomerOrder, onResult: (Boolean, String) -> Unit) {
        updateLabOrderStatus(order, "processing", "تم استلام الطلب وبدء التنفيذ", onResult)
    }

    private fun updateLabOrderStatus(
        order: CustomerOrder,
        status: String,
        successMessage: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val profile = operationalProfile()
        if (normalizeUserRole(profile?.role.orEmpty()) != "lab_operator") {
            onResult(false, "الحساب غير مصرح له بتنفيذ طلبات المعمل")
            return
        }
        if (status !in setOf("processing", "ready")) {
            onResult(false, "حالة غير مسموحة لحساب المعمل")
            return
        }
        val now = System.currentTimeMillis()
        val originalRef = firestore.collection(CUSTOMERS_COLLECTION).document(order.customerId)
            .collection("orders").document(order.id)
        val statusUpdate = mapOf(
            "workflow_status" to status,
            "status_history" to FieldValue.arrayUnion(
                mapOf(
                    "status" to status,
                    "at_ms" to now,
                    "by_uid" to currentUid(),
                    "by_email" to currentEmail()
                )
            ),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail()
        )
        firestore.runBatch { batch ->
            batch.update(originalRef, statusUpdate)
            batch.update(labMirrorRef(order.id), labMirrorData(statusUpdate))
        }
            .addOnSuccessListener {
                _labOrders.value = _labOrders.value.map {
                    if (it.id == order.id) it.copy(workflowStatus = status, updatedAtMillis = now, updatedByUid = currentUid()) else it
                }
                if (status == "processing") sendExternalOrderPush("processing", order.customerId, order.id)
                onResult(true, successMessage)
            }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "تعذر تحديث الطلب") }
    }


    /** Lab may correct the operational request details. Every change is audited and notifies clinic listeners. */
    fun labEditOrderDetails(
        order: CustomerOrder,
        customerName: String,
        customerPhone: String,
        customerAge: String,
        customerGender: String,
        notes: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val profile = operationalProfile()
        if (normalizeUserRole(profile?.role.orEmpty()) != "lab_operator") {
            onResult(false, "الحساب غير مصرح له بتعديل الطلب")
            return
        }
        if (order.isVoided || order.workflowStatus == "delivered") {
            onResult(false, "لا يمكن تعديل طلب ملغي أو مكتمل")
            return
        }
        val now = System.currentTimeMillis()
        val ref = firestore.collection(CUSTOMERS_COLLECTION).document(order.customerId)
            .collection("orders").document(order.id)
        val labEditPatch = mapOf<String, Any?>(
            "customer_name" to customerName.trim(),
            "customer_phone" to customerPhone.trim(),
            "customer_age" to customerAge.trim(),
            "customer_gender" to customerGender.trim(),
            "notes" to notes.trim(),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail(),
            "edit_count" to FieldValue.increment(1),
            "status_history" to FieldValue.arrayUnion(mapOf(
                "status" to "edited_by_lab", "at_ms" to now,
                "by_uid" to currentUid(), "by_email" to currentEmail()
            ))
        )
        firestore.runBatch { batch ->
            batch.update(ref, labEditPatch)
            batch.update(labMirrorRef(order.id), labMirrorData(labEditPatch))
        }.addOnSuccessListener {
            _labOrders.value = _labOrders.value.map {
                if (it.id == order.id) it.copy(
                    customerName = customerName.trim(), customerPhone = customerPhone.trim(),
                    customerAge = customerAge.trim(), customerGender = customerGender.trim(),
                    notes = notes.trim(), updatedAtMillis = now, updatedByUid = currentUid(),
                    editCount = it.editCount + 1
                ) else it
            }
            sendExternalOrderPush("lab_edit", order.customerId, order.id)
            onResult(true, "تم تعديل الطلب وإبلاغ العيادة بالتحديث")
        }.addOnFailureListener { onResult(false, it.localizedMessage ?: "تعذر تعديل الطلب") }
    }

    /** Lab-side delete is a soft delete for safety: it disappears from the lab queue immediately,
     * while the clinic/audit history keeps the record and can see who removed it. */
    fun labCancelOrder(order: CustomerOrder, onResult: (Boolean, String) -> Unit) {
        val profile = operationalProfile()
        if (normalizeUserRole(profile?.role.orEmpty()) != "lab_operator") {
            onResult(false, "الحساب غير مصرح له بإلغاء طلبات المعمل")
            return
        }
        val now = System.currentTimeMillis()
        val ref = firestore.collection(CUSTOMERS_COLLECTION).document(order.customerId)
            .collection("orders").document(order.id)
        val labCancelPatch = mapOf<String, Any?>(
            "is_voided" to true,
            "void_reason" to "إلغاء بواسطة المعمل",
            "voided_at_ms" to now,
            "voided_by_uid" to currentUid(),
            "voided_by_email" to currentEmail(),
            "workflow_status" to "cancelled",
            "status_history" to FieldValue.arrayUnion(
                mapOf(
                    "status" to "cancelled",
                    "at_ms" to now,
                    "by_uid" to currentUid(),
                    "by_email" to currentEmail()
                )
            ),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail()
        )
        firestore.runBatch { batch ->
            batch.update(ref, labCancelPatch)
            batch.update(labMirrorRef(order.id), labMirrorData(labCancelPatch))
        }.addOnSuccessListener {
            _labOrders.value = _labOrders.value.filterNot { it.id == order.id }
            sendExternalOrderPush("cancelled", order.customerId, order.id)
            onResult(true, "تم إلغاء الطلب وإبلاغ العيادة بالحالة")
        }.addOnFailureListener {
            onResult(false, it.localizedMessage ?: "تعذر إلغاء الطلب")
        }
    }

    /** V117 GitHub/Firebase-only: no external result server is required. */
    fun testResultBackend(onResult: (Boolean, String) -> Unit) {
        onResult(true, "تخزين النتائج يعمل على Firebase المجاني بدون خادم خارجي")
    }

    /** Opens both new Firestore-chunk result refs and legacy http(s) result links. */
    fun openResultFile(storedRef: String, onResult: (Boolean, String) -> Unit) {
        FirestoreResultFileStore.openResult(getApplication<Application>(), storedRef, onResult)
    }

    /** V121: save a received result into Android Downloads. */
    fun downloadResultFile(storedRef: String, displayName: String, onResult: (Boolean, String) -> Unit) {
        FirestoreResultFileStore.saveResultToDownloads(getApplication<Application>(), storedRef, displayName, onResult)
    }

    /** V121: share the real result PDF/image via Android Sharesheet. */
    fun shareResultFile(storedRef: String, onResult: (Boolean, String) -> Unit) {
        FirestoreResultFileStore.shareResult(getApplication<Application>(), storedRef, onResult)
    }

    /** V121: clinic can return a result to the lab for correction, repeatedly if needed.
     * Uses only already-allowed order fields so no Firebase Rules change is required. */
    fun requestResultRevision(
        customer: Customer,
        order: CustomerOrder,
        revisionNote: String,
        onResult: (CustomerOrder?, String) -> Unit
    ) {
        if (!hasOperationalAccess()) {
            onResult(null, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (order.isVoided) {
            onResult(null, "الطلب ملغي ولا يمكن إعادته للمعمل")
            return
        }
        if (order.resultUrls.isEmpty()) {
            onResult(null, "لا توجد نتيجة مرسلة لإعادتها للمعمل")
            return
        }

        val cleanNote = revisionNote.trim().take(500)
        val revisionLine = if (cleanNote.isBlank()) {
            "طلب تعديل النتيجة من العيادة"
        } else {
            "طلب تعديل النتيجة من العيادة: $cleanNote"
        }
        val mergedNotes = when {
            order.notes.isBlank() -> revisionLine
            order.notes.endsWith(revisionLine) -> order.notes
            else -> (order.notes.trimEnd() + "\n" + revisionLine).takeLast(1500)
        }
        val now = System.currentTimeMillis()
        val patch = mapOf<String, Any?>(
            "workflow_status" to "processing",
            "notes" to mergedNotes,
            "status_history" to FieldValue.arrayUnion(
                mapOf(
                    "status" to "processing",
                    "reason" to "result_revision_requested",
                    "note" to cleanNote,
                    "at_ms" to now,
                    "by_uid" to currentUid(),
                    "by_email" to currentEmail()
                )
            ),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail(),
            "edit_count" to FieldValue.increment(1)
        )
        val originalRef = firestore.collection(CUSTOMERS_COLLECTION).document(customer.id)
            .collection("orders").document(order.id)
        firestore.runBatch { batch ->
            batch.update(originalRef, patch)
            batch.update(labMirrorRef(order.id), labMirrorData(patch))
        }.addOnSuccessListener {
            val updated = order.copy(
                workflowStatus = "processing",
                notes = mergedNotes,
                updatedAtMillis = now,
                updatedByUid = currentUid(),
                editCount = order.editCount + 1
            )
            _customerOrders.value = _customerOrders.value.map { if (it.id == order.id) updated else it }
            addCustomerActivity(customer.id, "result_revision", "طلب تعديل نتيجة", revisionLine)
            onResult(updated, "تمت إعادة النتيجة للمعمل للتعديل")
        }.addOnFailureListener { error ->
            onResult(null, error.localizedMessage ?: "تعذر إعادة النتيجة للمعمل")
        }
    }

    /** No paid/external push backend in V117. Realtime + the 15-minute Android safety job remain active. */
    private fun sendExternalOrderPush(event: String, customerId: String, orderId: String) {
        // Intentionally no-op. Firestore realtime listeners notify instantly while the app is alive,
        // and LabOrderBackgroundService provides the backend-free background safety net.
    }

    /** V117: upload images/PDFs into Firestore chunks and send the result back to the clinic. */
    fun labUploadAndSendResults(
        order: CustomerOrder,
        uris: List<Uri>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onResult: (Boolean, String) -> Unit
    ) {
        val profile = operationalProfile()
        val operationalRole = normalizeUserRole(profile?.role.orEmpty())
        if (operationalRole != "lab_operator" && !hasAdminAccess()) {
            onResult(false, "الحساب غير مصرح له برفع النتائج")
            return
        }
        if (uris.isEmpty()) {
            onResult(false, "اختر صورة أو PDF للنتيجة")
            return
        }

        val resolver = getApplication<Application>().contentResolver
        val urls = mutableListOf<String>()
        val names = mutableListOf<String>()

        fun uploadAt(index: Int) {
            if (index >= uris.size) {
                val now = System.currentTimeMillis()
                val originalRef = firestore.collection(CUSTOMERS_COLLECTION).document(order.customerId)
                    .collection("orders").document(order.id)
                val resultUpdate = mapOf(
                    "result_urls" to urls,
                    "result_names" to names,
                    "result_sent_at_ms" to now,
                    "result_sent_at" to FieldValue.serverTimestamp(),
                    "result_uploaded_by_uid" to currentUid(),
                    "result_uploaded_by_email" to currentEmail(),
                    "workflow_status" to "ready",
                    "updated_at_ms" to now,
                    "updated_at" to FieldValue.serverTimestamp(),
                    "updated_by_uid" to currentUid(),
                    "updated_by_email" to currentEmail()
                )
                val readyPatch = resultUpdate + mapOf(
                    "status_history" to FieldValue.arrayUnion(
                        mapOf(
                            "status" to "ready",
                            "at_ms" to now,
                            "by_uid" to currentUid(),
                            "by_email" to currentEmail()
                        )
                    )
                )
                firestore.runBatch { batch ->
                    batch.update(originalRef, readyPatch)
                    batch.update(labMirrorRef(order.id), labMirrorData(readyPatch))
                }
                    .addOnSuccessListener {
                        _labOrders.value = _labOrders.value.map {
                            if (it.id == order.id) it.copy(
                                workflowStatus = "ready",
                                resultUrls = urls.toList(),
                                resultNames = names.toList(),
                                resultSentAtMillis = now,
                                updatedAtMillis = now,
                                updatedByUid = currentUid()
                            ) else it
                        }
                        sendExternalOrderPush("ready", order.customerId, order.id)
                        onResult(true, "تم إرسال النتيجة للعيادة")
                    }
                    .addOnFailureListener { onResult(false, it.localizedMessage ?: "تم رفع الملف لكن تعذر إرسال النتيجة") }
                return
            }

            val uri = uris[index]
            val mime = resolver.getType(uri).orEmpty().lowercase()
            if (!(mime == "application/pdf" || mime.startsWith("image/"))) {
                onResult(false, "الملفات المسموحة صور أو PDF فقط")
                return
            }
            val ext = when {
                mime == "application/pdf" -> "pdf"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val fileName = "result_${index + 1}.$ext"
            FirestoreResultFileStore.uploadResult(
                context = getApplication<Application>(),
                uri = uri,
                customerId = order.customerId,
                orderId = order.id,
                displayName = fileName
            ) { ok, storageRef, message ->
                if (!ok || storageRef.isNullOrBlank()) {
                    onResult(false, message.ifBlank { "تعذر رفع ملف النتيجة" })
                    return@uploadResult
                }
                urls += storageRef
                names += fileName
                onProgress(index + 1, uris.size)
                uploadAt(index + 1)
            }
        }

        uploadAt(0)
    }


    /** V107: edit the actual request (tests + clinic notes) after it was sent.
     * The lab reads the same Firestore document, so the updated receipt appears automatically. */
    fun updateOrderContents(
        customer: Customer,
        order: CustomerOrder,
        testsText: String,
        notes: String,
        onResult: (CustomerOrder?, String) -> Unit
    ) {
        if (!hasOperationalAccess() || normalizeUserRole(operationalProfile()?.role.orEmpty()) == "lab_operator") {
            onResult(null, tr("الحساب غير مصرح له بتعديل الطلب", "Account cannot edit this order"))
            return
        }
        if (order.isVoided) {
            onResult(null, tr("الطلب ملغي ولا يمكن تعديله", "Voided order cannot be edited"))
            return
        }

        val names = testsText
            .split(Regex("[\\n,;]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (names.isEmpty()) {
            onResult(null, tr("اكتب تحليل واحد على الأقل", "Enter at least one test"))
            return
        }

        val missing = mutableListOf<String>()
        val resolved = names.mapNotNull { name ->
            val exact = repository.searchTests(name).firstOrNull { candidate ->
                listOf(candidate.englishName, candidate.arabicName, candidate.marketName)
                    .filter { it.isNotBlank() }
                    .any { normalizeText(it) == normalizeText(name) }
            }
            val found = exact ?: repository.searchTests(name).firstOrNull()
            if (found == null) missing += name
            found
        }.distinctBy { it.id }

        if (missing.isNotEmpty()) {
            onResult(null, "تحاليل غير موجودة: ${missing.take(6).joinToString("، ")}")
            return
        }
        if (resolved.isEmpty()) {
            onResult(null, tr("لم يتم العثور على تحاليل صالحة", "No valid tests found"))
            return
        }

        val oldPriceById = order.items.associate { it.testId to it.customerPrice }
        val newItems = resolved.map { test ->
            val price = oldPriceById[test.id]
                ?: parsePriceInput(_customerPriceOverrides.value[test.id] ?: test.customerPrice.orEmpty())
                ?: 0.0
            CustomerOrderItem(
                testId = test.id,
                englishName = test.englishName,
                arabicName = test.arabicName,
                marketName = test.marketName,
                customerPrice = price
            )
        }
        val subtotal = newItems.sumOf { it.customerPrice }
        val discount = when {
            order.discountPercent > 0.0 -> subtotal * order.discountPercent.coerceIn(0.0, 100.0) / 100.0
            else -> order.discountAmount.coerceIn(0.0, subtotal)
        }
        val total = (subtotal - discount).coerceAtLeast(0.0)
        val paid = order.paidAmount.coerceIn(0.0, total)
        val remaining = (total - paid).coerceAtLeast(0.0)
        val paymentStatus = when {
            remaining <= 0.001 -> "paid"
            paid > 0.001 -> "partial"
            else -> "unpaid"
        }
        val now = System.currentTimeMillis()
        val itemMaps = newItems.map {
            mapOf(
                "test_id" to it.testId,
                "english_name" to it.englishName,
                "arabic_name" to it.arabicName,
                "market_name" to it.marketName,
                "customer_price" to it.customerPrice
            )
        }
        val ref = firestore.collection(CUSTOMERS_COLLECTION).document(customer.id)
            .collection("orders").document(order.id)

        val updated = order.copy(
            items = newItems,
            subtotalCustomerPrice = subtotal,
            discountAmount = discount,
            totalCustomerPrice = total,
            paymentStatus = paymentStatus,
            paidAmount = paid,
            remainingAmount = remaining,
            notes = notes.trim(),
            updatedAtMillis = now,
            updatedByUid = currentUid(),
            editCount = order.editCount + 1
        )

        val contentPatch = mapOf<String, Any?>(
            "items" to itemMaps,
            "tests_count" to itemMaps.size,
            "subtotal_customer_price" to subtotal,
            "discount_amount" to discount,
            "total_customer_price" to total,
            "payment_status" to paymentStatus,
            "paid_amount" to paid,
            "remaining_amount" to remaining,
            "notes" to notes.trim(),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail(),
            "edit_count" to FieldValue.increment(1),
            "status_history" to FieldValue.arrayUnion(
                mapOf(
                    "status" to "edited",
                    "at_ms" to now,
                    "by_uid" to currentUid(),
                    "by_email" to currentEmail()
                )
            )
        )
        firestore.runBatch { batch ->
            batch.update(ref, contentPatch)
            batch.update(labMirrorRef(order.id), labMirrorData(contentPatch))
        }.addOnSuccessListener {
            _customerOrders.value = _customerOrders.value.map { if (it.id == order.id) updated else it }
            logAudit(
                "order_update", "order", order.id, "تعديل محتوى الطلب ${order.orderNumber}",
                "${newItems.size} تحليل • تعديل الإيصال المرسل للمعمل",
                customerId = customer.id, orderId = order.id
            )
            addCustomerActivity(customer.id, "order_update", "تعديل ${order.orderNumber}", "تم تعديل التحاليل/الملاحظات")
            sendExternalOrderPush("clinic_edit", customer.id, order.id)
            onResult(updated, "تم تعديل الطلب • التعديل يظهر للمعمل تلقائيًا")
        }.addOnFailureListener {
            onResult(null, it.localizedMessage ?: "تعذر تعديل الطلب")
        }
    }

    fun updateOrderFinancials(
        customer: Customer,
        order: CustomerOrder,
        discountAmount: Double,
        discountPercent: Double,
        paymentStatus: String,
        paidAmount: Double,
        notes: String,
        onResult: (CustomerOrder?, String) -> Unit
    ) {
        if (!hasOperationalAccess()) {
            onResult(null, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (order.isVoided) {
            onResult(null, tr("الطلب ملغي ولا يمكن تعديله", "Voided order cannot be edited"))
            return
        }
        val subtotal = order.subtotalCustomerPrice
        val safePercent = discountPercent.coerceIn(0.0, 100.0)
        val safeDiscount = if (safePercent > 0.0) subtotal * safePercent / 100.0 else discountAmount.coerceIn(0.0, subtotal)
        val finalTotal = (subtotal - safeDiscount).coerceAtLeast(0.0)
        val normalizedStatus = when (paymentStatus) { "paid", "partial", "unpaid" -> paymentStatus; else -> "unpaid" }
        val safePaid = when (normalizedStatus) {
            "paid" -> finalTotal
            "partial" -> paidAmount.coerceIn(0.0, finalTotal)
            else -> 0.0
        }
        val discountChanged = kotlin.math.abs(order.discountAmount - safeDiscount) > 0.001
        val paymentChanged = kotlin.math.abs(order.paidAmount - safePaid) > 0.001 || order.paymentStatus != normalizedStatus
        if (discountChanged && !canDiscountOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية تعديل الخصومات", "Your account cannot change discounts"))
            return
        }
        if (paymentChanged && !canCollectPaymentsOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية تعديل التحصيل", "Your account cannot change payments"))
            return
        }
        val remaining = (finalTotal - safePaid).coerceAtLeast(0.0)
        val now = System.currentTimeMillis()
        val orderRef = firestore.collection(CUSTOMERS_COLLECTION).document(customer.id).collection("orders").document(order.id)
        val changes = buildList {
            if (kotlin.math.abs(order.discountAmount - safeDiscount) > 0.001) add("الخصم ${formatNumber(order.discountAmount)} ← ${formatNumber(safeDiscount)}")
            if (kotlin.math.abs(order.paidAmount - safePaid) > 0.001) add("المدفوع ${formatNumber(order.paidAmount)} ← ${formatNumber(safePaid)}")
            if (order.paymentStatus != normalizedStatus) add("حالة الدفع ${order.paymentStatus} ← $normalizedStatus")
            if (order.notes.trim() != notes.trim()) add("تعديل الملاحظات")
        }.joinToString(" | ").ifBlank { tr("لا يوجد تغيير", "No changes") }

        queueOfflineAuditIfNeeded(
            action = "order_update",
            entityType = "order",
            entityId = order.id,
            title = "تعديل طلب ${order.orderNumber}",
            details = changes,
            customerId = customer.id,
            orderId = order.id
        )

        val offlineAtStart = !_isOnline.value
        val updatedOrder = order.copy(
            discountAmount = safeDiscount,
            discountPercent = if (subtotal > 0.0) safeDiscount / subtotal * 100.0 else 0.0,
            totalCustomerPrice = finalTotal,
            paymentStatus = normalizedStatus,
            paidAmount = safePaid,
            remainingAmount = remaining,
            notes = notes.trim(),
            updatedAtMillis = now,
            updatedByUid = currentUid(),
            editCount = order.editCount + 1
        )
        fun commitOrderUpdate() {
            val financialPatch = mapOf<String, Any?>(
                "discount_amount" to safeDiscount,
                "discount_percent" to if (subtotal > 0.0) (safeDiscount / subtotal * 100.0) else 0.0,
                "total_customer_price" to finalTotal,
                "payment_status" to normalizedStatus,
                "paid_amount" to safePaid,
                "remaining_amount" to remaining,
                "notes" to notes.trim(),
                "updated_at_ms" to now,
                "updated_at" to FieldValue.serverTimestamp(),
                "updated_by_uid" to currentUid(),
                "updated_by_email" to currentEmail(),
                "edit_count" to FieldValue.increment(1)
            )
            val notesChangedForLab = order.notes.trim() != notes.trim()
            val updateTask = if (notesChangedForLab) {
                val mirrorPatch = mapOf<String, Any?>(
                    "notes" to notes.trim(),
                    "updated_at_ms" to now,
                    "updated_at" to FieldValue.serverTimestamp(),
                    "updated_by_uid" to currentUid(),
                    "updated_by_email" to currentEmail(),
                    "edit_count" to FieldValue.increment(1)
                )
                firestore.runBatch { batch ->
                    batch.update(orderRef, financialPatch)
                    batch.update(labMirrorRef(order.id), labMirrorData(mirrorPatch))
                }
            } else {
                orderRef.update(financialPatch)
            }

            if (offlineAtStart) {
                _customerOrders.value = _customerOrders.value.map { if (it.id == order.id) updatedOrder else it }
                onResult(updatedOrder, "تم حفظ التعديل على الجهاز • في انتظار المزامنة")
            }

            updateTask.addOnSuccessListener {
                // V119: financial-only changes stay private; operational note changes are mirrored separately.
                loadCustomerOrders(customer.id)
                logAudit("order_update", "order", order.id, "تعديل طلب ${order.orderNumber}", changes, customerId = customer.id, orderId = order.id)
                addCustomerActivity(customer.id, "order_update", "تعديل ${order.orderNumber}", changes)
                if (!offlineAtStart) onResult(updatedOrder, "تم تعديل الطلب مع تسجيل التغيير")
            }.addOnFailureListener {
                if (!offlineAtStart) onResult(null, tr("تعذر تعديل الطلب", "Unable to update order"))
                else _systemMessage.value = "تعذر مزامنة تعديل ${order.orderNumber}"
            }
        }

        if (!offlineAtStart) {
            orderRef.get(Source.SERVER)
                .addOnSuccessListener { serverDoc ->
                    val serverUpdated = serverDoc.getLong("updated_at_ms") ?: order.updatedAtMillis
                    if (serverUpdated > order.updatedAtMillis + 1L) {
                        onResult(null, tr("تم تعديل الطلب بواسطة مستخدم آخر. افتحه من جديد قبل الحفظ.", "This order was changed by another user. Reopen it before saving."))
                    } else {
                        commitOrderUpdate()
                    }
                }
                .addOnFailureListener {
                    onResult(null, tr("تعذر التحقق من أحدث نسخة للطلب", "Could not verify the latest order version"))
                }
        } else {
            commitOrderUpdate()
        }
    }

    fun collectOrderPayment(
        customer: Customer,
        order: CustomerOrder,
        amount: Double,
        note: String,
        onResult: (CustomerOrder?, String) -> Unit
    ) {
        if (!hasOperationalAccess()) {
            onResult(null, tr("الحساب أو الجهاز غير معتمد للتشغيل", "Account or device is not approved for operations"))
            return
        }
        if (!canCollectPaymentsOperationally()) {
            onResult(null, tr("حسابك لا يملك صلاحية التحصيل", "Your account cannot collect payments"))
            return
        }
        if (order.isVoided) {
            onResult(null, tr("الطلب ملغي", "Order is voided"))
            return
        }
        val cleanAmount = amount.coerceAtLeast(0.0)
        if (cleanAmount <= 0.0) {
            onResult(null, tr("اكتب مبلغ التحصيل", "Enter payment amount"))
            return
        }
        val orderRef = firestore.collection(CUSTOMERS_COLLECTION).document(customer.id).collection("orders").document(order.id)
        val paymentRef = orderRef.collection("payments").document()
        val now = System.currentTimeMillis()
        val uid = currentUid()
        val email = currentEmail()

        val total = order.totalCustomerPrice
        val currentPaid = order.paidAmount
        val currentRemaining = order.remainingAmount.coerceAtLeast(0.0)
        if (currentRemaining <= 0.001) {
            onResult(null, tr("الطلب مدفوع بالكامل", "Order is fully paid"))
            return
        }
        if (cleanAmount > currentRemaining + 0.001) {
            onResult(null, tr("المبلغ أكبر من المتبقي", "Amount exceeds remaining balance"))
            return
        }
        val newPaid = (currentPaid + cleanAmount).coerceAtMost(total)
        val newRemaining = (total - newPaid).coerceAtLeast(0.0)
        val newStatus = when {
            newRemaining <= 0.001 -> "paid"
            newPaid > 0.0 -> "partial"
            else -> "unpaid"
        }
        val updated = order.copy(
            paidAmount = newPaid,
            remainingAmount = newRemaining,
            paymentStatus = newStatus,
            updatedAtMillis = now,
            updatedByUid = uid,
            editCount = order.editCount + 1
        )
        val detail = "${order.orderNumber} • تحصيل ${formatNumber(cleanAmount)} • المتبقي ${formatNumber(newRemaining)}"
        queueOfflineAuditIfNeeded("payment_collect", "payment", paymentRef.id, "تحصيل دفعة", detail, customerId = customer.id, orderId = order.id)
        val offlineAtStart = !_isOnline.value

        fun commitPaymentWrite() {
            val batchTask = firestore.runBatch { batch ->
                batch.update(
                    orderRef,
                    mapOf(
                        "paid_amount" to newPaid,
                        "remaining_amount" to newRemaining,
                        "payment_status" to newStatus,
                        "updated_at_ms" to now,
                        "updated_at" to FieldValue.serverTimestamp(),
                        "updated_by_uid" to uid,
                        "updated_by_email" to email,
                        "edit_count" to FieldValue.increment(1)
                    )
                )
                batch.set(
                    paymentRef,
                    mapOf(
                        "operation_id" to paymentRef.id,
                        "customer_id" to customer.id,
                        "order_id" to order.id,
                        "amount" to cleanAmount,
                        "note" to note.trim(),
                        "created_at_ms" to now,
                        "created_at" to FieldValue.serverTimestamp(),
                        "created_by_uid" to uid,
                        "created_by_email" to email
                    )
                )
            }

            if (offlineAtStart) {
                _customerOrders.value = _customerOrders.value.map { if (it.id == order.id) updated else it }
                addCustomerActivity(customer.id, "payment", "تحصيل دفعة", detail)
                onResult(updated, "تم تسجيل الدفعة على الجهاز • في انتظار المزامنة")
            }

            batchTask.addOnSuccessListener {
                ShadowBackupReplicator.mirrorPath(paymentRef.path, now)
                ShadowBackupReplicator.mirrorPath(orderRef.path, now)
                loadCustomerOrders(customer.id)
                loadPaymentHistory(customer.id, order.id)
                logAudit("payment_collect", "payment", paymentRef.id, "تحصيل دفعة", detail, customerId = customer.id, orderId = order.id)
                if (!offlineAtStart) {
                    addCustomerActivity(customer.id, "payment", "تحصيل دفعة", detail)
                    onResult(updated, "تم تسجيل الدفعة")
                }
            }.addOnFailureListener {
                if (!offlineAtStart) onResult(null, tr("تعذر تسجيل الدفعة", "Unable to record payment"))
                else _systemMessage.value = "تعذر مزامنة دفعة ${order.orderNumber}"
            }
        }

        if (!offlineAtStart) {
            orderRef.get(Source.SERVER)
                .addOnSuccessListener { serverDoc ->
                    val serverUpdated = serverDoc.getLong("updated_at_ms") ?: order.updatedAtMillis
                    if (serverUpdated > order.updatedAtMillis + 1L) {
                        onResult(null, tr("بيانات الطلب اتغيرت عند مستخدم آخر. افتحه من جديد قبل التحصيل.", "The order changed on another device. Reopen it before collecting payment."))
                    } else {
                        commitPaymentWrite()
                    }
                }
                .addOnFailureListener {
                    onResult(null, tr("تعذر التحقق من أحدث نسخة للطلب", "Could not verify the latest order version"))
                }
        } else {
            commitPaymentWrite()
        }
    }

    fun loadPaymentHistory(customerId: String, orderId: String) {
        _paymentHistory.value = emptyList()
        if (customerId.isBlank() || orderId.isBlank()) return
        firestore.collection(CUSTOMERS_COLLECTION).document(customerId)
            .collection("orders").document(orderId)
            .collection("payments")
            .orderBy("created_at_ms", Query.Direction.DESCENDING)
            .limit(PAYMENT_HISTORY_LIMIT)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                _paymentHistory.value = snapshot.documents.map { doc ->
                    PaymentEntry(
                        id = doc.id,
                        customerId = doc.getString("customer_id").orEmpty().ifBlank { customerId },
                        orderId = doc.getString("order_id").orEmpty().ifBlank { orderId },
                        amount = numberAsDouble(doc.get("amount")),
                        note = doc.getString("note").orEmpty(),
                        createdAtMillis = doc.getLong("created_at_ms") ?: 0L,
                        createdByUid = doc.getString("created_by_uid").orEmpty(),
                        createdByEmail = doc.getString("created_by_email").orEmpty()
                    )
                }.sortedByDescending { it.createdAtMillis }
            }
    }

    fun voidOrder(customer: Customer, order: CustomerOrder, reason: String, onResult: (Boolean, String) -> Unit) {
        if (!hasOperationalAccess() || normalizeUserRole(operationalProfile()?.role.orEmpty()) == "lab_operator") {
            onResult(false, tr("الحساب غير مصرح له بإلغاء الطلب", "Account cannot void this order"))
            return
        }
        if (reason.trim().length < 3) {
            onResult(false, tr("اكتب سبب الإلغاء", "Enter void reason"))
            return
        }
        val now = System.currentTimeMillis()
        queueOfflineAuditIfNeeded(
            action = "order_void",
            entityType = "order",
            entityId = order.id,
            title = "إلغاء طلب ${order.orderNumber}",
            details = reason.trim(),
            customerId = customer.id,
            orderId = order.id
        )
        val voidRef = firestore.collection(CUSTOMERS_COLLECTION).document(customer.id).collection("orders").document(order.id)
        val voidPatch = mapOf<String, Any?>(
            "is_voided" to true,
            "void_reason" to reason.trim(),
            "voided_at_ms" to now,
            "voided_by_uid" to currentUid(),
            "voided_by_email" to currentEmail(),
            "workflow_status" to "cancelled",
            "status_history" to FieldValue.arrayUnion(
                mapOf(
                    "status" to "cancelled",
                    "at_ms" to now,
                    "by_uid" to currentUid(),
                    "by_email" to currentEmail()
                )
            ),
            "updated_at_ms" to now,
            "updated_at" to FieldValue.serverTimestamp(),
            "updated_by_uid" to currentUid(),
            "updated_by_email" to currentEmail(),
            "edit_count" to FieldValue.increment(1)
        )
        firestore.runBatch { batch ->
            batch.update(voidRef, voidPatch)
            batch.update(labMirrorRef(order.id), labMirrorData(voidPatch))
        }
            .addOnSuccessListener {
                loadCustomerOrders(customer.id)
                logAudit("order_void", "order", order.id, "إلغاء طلب ${order.orderNumber}", reason.trim(), customerId = customer.id, orderId = order.id)
                addCustomerActivity(customer.id, "order_void", "إلغاء ${order.orderNumber}", reason.trim())
                sendExternalOrderPush("cancelled", customer.id, order.id)
                onResult(true, "تم إلغاء الطلب مع الاحتفاظ به في سجل المراجعة")
            }
            .addOnFailureListener { onResult(false, tr("تعذر إلغاء الطلب", "Unable to void order")) }
    }

    /** V113: permanent delete is deliberately admin-only. Cancellation remains the
     * normal staff action because it keeps the audit trail. */
    fun deleteOrderPermanently(
        customer: Customer,
        order: CustomerOrder,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasAdminAccess()) {
            onResult(false, tr("الحذف النهائي متاح للإدارة فقط", "Permanent delete is admin only"))
            return
        }
        val orderRef = privilegedFirestore().collection(CUSTOMERS_COLLECTION)
            .document(customer.id).collection("orders").document(order.id)

        // Preserve a top-level audit entry before removing the operational document.
        logAudit(
            "order_hard_delete", "order", order.id,
            "حذف نهائي ${order.orderNumber}",
            "${order.customerName} • ${order.items.size} تحليل",
            customerId = customer.id, orderId = order.id
        )

        orderRef.collection("payments").get()
            .addOnSuccessListener { paymentsSnapshot ->
                val batch = privilegedFirestore().batch()
                paymentsSnapshot.documents.forEach { batch.delete(it.reference) }
                batch.delete(orderRef)
                batch.delete(privilegedFirestore().collection(LAB_ORDERS_COLLECTION).document(order.id))
                batch.commit()
                    .addOnSuccessListener {
                        paymentsSnapshot.documents.forEach { deleted ->
                            ShadowBackupReplicator.mirrorPath(deleted.reference.path, System.currentTimeMillis(), tombstone = true)
                        }
                        ShadowBackupReplicator.mirrorPath(orderRef.path, System.currentTimeMillis(), tombstone = true)
                        ShadowBackupReplicator.mirrorPath("lab_orders/${order.id}", System.currentTimeMillis(), tombstone = true)
                        _customerOrders.value = _customerOrders.value.filterNot { it.id == order.id }
                        _dailyOrders.value = _dailyOrders.value.filterNot { it.id == order.id }
                        _orderArchive.value = _orderArchive.value.filterNot { it.id == order.id }
                        addCustomerActivity(customer.id, "order_hard_delete", "حذف نهائي ${order.orderNumber}", "تم الحذف بواسطة الإدارة")
                        onResult(true, tr("تم حذف الطلب نهائيًا", "Order permanently deleted"))
                    }
                    .addOnFailureListener { error ->
                        onResult(false, error.localizedMessage ?: tr("تعذر حذف الطلب", "Unable to delete order"))
                    }
            }
            .addOnFailureListener { error ->
                onResult(false, error.localizedMessage ?: tr("تعذر التحقق من سجل الدفعات", "Unable to verify payment history"))
            }
    }

    // ------------------------- REPORTS -------------------------

    fun loadManagerHomeSummary(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasAdminAccess()) {
            onResult?.invoke(false, tr("لوحة المدير متاحة للمدير فقط", "Manager dashboard is manager only"))
            return
        }
        if (_managerHomeLoading.value) return
        _managerHomeLoading.value = true

        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -30)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startTrackingWindow = calendar.timeInMillis

        // Fast path: read only today's orders and orders that still carry debt instead of downloading
        // the entire historical order collection every time the manager opens the dashboard.
        val todayQuery = privilegedFirestore().collectionGroup("orders")
            .whereGreaterThanOrEqualTo("created_at_ms", startTrackingWindow)
            .orderBy("created_at_ms", Query.Direction.DESCENDING)

        todayQuery.get(Source.SERVER)
            .addOnSuccessListener { todaySnapshot ->
                val todayOrders = todaySnapshot.documents.mapNotNull { doc ->
                    val customerId = doc.reference.parent.parent?.id.orEmpty()
                    parseOrder(doc, customerId)
                }.filterNot { it.isVoided }

                val allOrdersQuery = privilegedFirestore().collectionGroup("orders")
                    .orderBy("created_at_ms", Query.Direction.DESCENDING)

                loadManagerRemainingPages(
                    baseQuery = allOrdersQuery,
                    cursor = null,
                    runningRemaining = 0.0,
                    onSuccess = { totalRemaining ->
                        _managerHomeSummary.value = ReportSummary(
                            ordersCount = todayOrders.size,
                            customersCount = todayOrders.map { it.customerId }.filter { it.isNotBlank() }.distinct().size,
                            paid = todayOrders.sumOf { it.paidAmount },
                            remaining = totalRemaining
                        )
                        _managerHomeLoading.value = false
                        rebuildAdminAlerts()
                        onResult?.invoke(true, tr("تم تحديث لوحة المدير بسرعة", "Manager dashboard refreshed"))
                    },
                    onFailure = { loadManagerHomeSummaryLegacy(onResult) }
                )
            }
            .addOnFailureListener {
                loadManagerHomeSummaryLegacy(onResult)
            }
    }


    private fun loadManagerRemainingPages(
        baseQuery: Query,
        cursor: DocumentSnapshot?,
        runningRemaining: Double,
        onSuccess: (Double) -> Unit,
        onFailure: () -> Unit
    ) {
        val pageQuery = (cursor?.let { baseQuery.startAfter(it) } ?: baseQuery).limit(SERVER_PAGE_SIZE)
        pageQuery.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val pageRemaining = snapshot.documents.mapNotNull { doc ->
                    val customerId = doc.reference.parent.parent?.id.orEmpty()
                    parseOrder(doc, customerId)
                }.filterNot { it.isVoided }.sumOf { it.remainingAmount }
                val total = runningRemaining + pageRemaining
                if (snapshot.size().toLong() < SERVER_PAGE_SIZE || snapshot.documents.isEmpty()) {
                    onSuccess(total)
                } else {
                    loadManagerRemainingPages(baseQuery, snapshot.documents.last(), total, onSuccess, onFailure)
                }
            }
            .addOnFailureListener { onFailure() }
    }

    private fun loadManagerHomeSummaryLegacy(onResult: ((Boolean, String) -> Unit)? = null) {
        // Safe fallback for projects where the new collection-group indexes have not been deployed yet.
        privilegedFirestore().collectionGroup("orders").get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val allOrders = snapshot.documents.mapNotNull { doc ->
                    val customerId = doc.reference.parent.parent?.id.orEmpty()
                    parseOrder(doc, customerId)
                }.filterNot { it.isVoided }

                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startToday = calendar.timeInMillis
                val todayOrders = allOrders.filter { it.createdAtMillis >= startToday }

                _managerHomeSummary.value = ReportSummary(
                    ordersCount = todayOrders.size,
                    customersCount = todayOrders.map { it.customerId }.filter { it.isNotBlank() }.distinct().size,
                    paid = todayOrders.sumOf { it.paidAmount },
                    remaining = allOrders.sumOf { it.remainingAmount }
                )
                _managerHomeLoading.value = false
                rebuildAdminAlerts()
                onResult?.invoke(true, tr("تم تحديث لوحة المدير", "Manager dashboard refreshed"))
            }
            .addOnFailureListener {
                _managerHomeLoading.value = false
                onResult?.invoke(false, tr("تعذر تحديث ملخص لوحة المدير", "Unable to refresh manager dashboard"))
            }
    }

    fun loadReports(fromMillis: Long, toMillis: Long, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasAdminAccess()) {
            onResult?.invoke(false, tr("التقارير والحسابات العامة متاحة للمدير فقط", "Global reports and accounts are manager only"))
            return
        }
        if (_reportsLoading.value) return
        _reportsLoading.value = true

        val baseQuery = privilegedFirestore().collectionGroup("orders")
            .whereGreaterThanOrEqualTo("created_at_ms", fromMillis)
            .whereLessThanOrEqualTo("created_at_ms", toMillis)
            .orderBy("created_at_ms", Query.Direction.DESCENDING)

        loadReportPages(
            baseQuery = baseQuery,
            cursor = null,
            collected = mutableListOf(),
            onSuccess = { documents ->
                _reportOrders.value = documents.mapNotNull { doc ->
                    val customerId = doc.reference.parent.parent?.id.orEmpty()
                    parseOrder(doc, customerId)
                }.filterNot { it.isVoided }.sortedByDescending { it.createdAtMillis }
                _reportsLoading.value = false
                onResult?.invoke(true, tr("تم تحميل التقرير", "Report loaded"))
            },
            onFailure = {
                loadReportsLegacy(fromMillis, toMillis, onResult)
            }
        )
    }

    private fun loadReportPages(
        baseQuery: Query,
        cursor: DocumentSnapshot?,
        collected: MutableList<DocumentSnapshot>,
        onSuccess: (List<DocumentSnapshot>) -> Unit,
        onFailure: () -> Unit
    ) {
        val pageQuery = (cursor?.let { baseQuery.startAfter(it) } ?: baseQuery).limit(SERVER_PAGE_SIZE)
        pageQuery.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                collected.addAll(snapshot.documents)
                if (snapshot.size().toLong() < SERVER_PAGE_SIZE || snapshot.documents.isEmpty()) {
                    onSuccess(collected)
                } else {
                    loadReportPages(baseQuery, snapshot.documents.last(), collected, onSuccess, onFailure)
                }
            }
            .addOnFailureListener { onFailure() }
    }

    private fun loadReportsLegacy(
        fromMillis: Long,
        toMillis: Long,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        privilegedFirestore().collectionGroup("orders").get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                _reportOrders.value = snapshot.documents.mapNotNull { doc ->
                    val created = doc.getLong("created_at_ms") ?: 0L
                    if (created !in fromMillis..toMillis) return@mapNotNull null
                    val customerId = doc.reference.parent.parent?.id.orEmpty()
                    parseOrder(doc, customerId)
                }.filterNot { it.isVoided }.sortedByDescending { it.createdAtMillis }
                _reportsLoading.value = false
                onResult?.invoke(true, tr("تم تحميل التقرير", "Report loaded"))
            }
            .addOnFailureListener {
                _reportsLoading.value = false
                onResult?.invoke(false, tr("تعذر تحميل التقارير. تأكد من Firestore Rules", "Unable to load reports. Check Firestore Rules"))
            }
    }

    fun calculateReportSummary(orders: List<CustomerOrder> = _reportOrders.value): ReportSummary {
        val valid = orders.filterNot { it.isVoided }
        val subtotal = valid.sumOf { it.subtotalCustomerPrice }
        val discounts = valid.sumOf { it.discountAmount }
        val sales = valid.sumOf { it.totalCustomerPrice }
        val paid = valid.sumOf { it.paidAmount }
        val remaining = valid.sumOf { it.remainingAmount }
        val estimatedCost = if (hasAdminAccess()) {
            valid.sumOf { order ->
                order.items.sumOf { item -> parsePriceInput(_lab2LabPrices.value[item.testId].orEmpty()) ?: 0.0 }
            }
        } else 0.0
        return ReportSummary(
            ordersCount = valid.size,
            customersCount = valid.map { it.customerId }.filter { it.isNotBlank() }.distinct().size,
            subtotal = subtotal,
            discounts = discounts,
            sales = sales,
            paid = paid,
            remaining = remaining,
            estimatedLabCost = estimatedCost,
            estimatedProfit = if (hasAdminAccess()) sales - estimatedCost else 0.0
        )
    }

    // ------------------------- AUDIT -------------------------

    fun loadAuditLogs(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasAdminAccess()) {
            onResult?.invoke(false, tr("سجل المراجعة متاح للمدير فقط", "Audit log is manager only"))
            return
        }
        privilegedFirestore().collection(AUDIT_COLLECTION)
            .orderBy("created_at_ms", Query.Direction.DESCENDING)
            .limit(AUDIT_LOG_LIMIT)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { ShadowBackupReplicator.mirrorSnapshot(it) }
                _auditLogs.value = snapshot.documents.map { doc ->
                    AuditLogEntry(
                        id = doc.id,
                        action = doc.getString("action").orEmpty(),
                        entityType = doc.getString("entity_type").orEmpty(),
                        entityId = doc.getString("entity_id").orEmpty(),
                        customerId = doc.getString("customer_id").orEmpty(),
                        orderId = doc.getString("order_id").orEmpty(),
                        title = doc.getString("title").orEmpty(),
                        details = doc.getString("details").orEmpty(),
                        actorUid = doc.getString("actor_uid").orEmpty(),
                        actorEmail = doc.getString("actor_email").orEmpty(),
                        createdAtMillis = doc.getLong("performed_at_ms") ?: doc.getLong("created_at_ms") ?: 0L,
                        syncedAtMillis = doc.getTimestamp("synced_at")?.toDate()?.time ?: 0L,
                        wasOffline = doc.getBoolean("was_offline") == true
                    )
                }.sortedByDescending { it.createdAtMillis }.take(AUDIT_LOG_LIMIT.toInt())
                rebuildAdminAlerts()
                onResult?.invoke(true, "تم تحديث سجل المراجعة")
            }
            .addOnFailureListener { onResult?.invoke(false, tr("تعذر تحميل سجل المراجعة", "Unable to load audit log")) }
    }

    private fun auditKey(action: String, entityId: String, orderId: String): String =
        "$action|$entityId|$orderId"

    private fun queueOfflineAuditIfNeeded(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        details: String,
        customerId: String = "",
        orderId: String = ""
    ) {
        if (_isOnline.value) return
        val now = System.currentTimeMillis()
        pendingOfflineAuditKeys += auditKey(action, entityId, orderId)
        pendingSyncStore.increment()
        val auditRef = firestore.collection(AUDIT_COLLECTION).document()
        auditRef.set(
            mapOf(
                "operation_id" to auditRef.id,
                "action" to "${action}_offline",
                "entity_type" to entityType,
                "entity_id" to entityId,
                "customer_id" to customerId,
                "order_id" to orderId,
                "title" to "Offline • $title",
                "details" to details.take(2000),
                "actor_uid" to currentUid(),
                "actor_email" to currentEmail(),
                "performed_at_ms" to now,
                "created_at_ms" to now,
                "was_offline" to true,
                "synced_at" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            ShadowBackupReplicator.mirrorPath(auditRef.path, now)
        }
    }

    private fun logAudit(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        details: String,
        customerId: String = "",
        orderId: String = ""
    ) {
        val key = auditKey(action, entityId, orderId)
        if (pendingOfflineAuditKeys.remove(key)) return
        val now = System.currentTimeMillis()
        val auditRef = firestore.collection(AUDIT_COLLECTION).document()
        auditRef.set(
            mapOf(
                "operation_id" to auditRef.id,
                "action" to action,
                "entity_type" to entityType,
                "entity_id" to entityId,
                "customer_id" to customerId,
                "order_id" to orderId,
                "title" to title,
                "details" to details.take(2000),
                "actor_uid" to currentUid(),
                "actor_email" to currentEmail(),
                "performed_at_ms" to now,
                "created_at_ms" to now,
                "created_at" to FieldValue.serverTimestamp(),
                "was_offline" to false,
                "synced_at" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            ShadowBackupReplicator.mirrorPath(auditRef.path, now)
        }
    }

    // ------------------------- SEARCH / SELECTION -------------------------

    fun clearSelectedTests() {
        _selectedTests.value = emptyList()
        clearSearch()
    }

    private fun parseQueries(rawInput: String): List<String> {
        if (rawInput.isBlank()) return emptyList()
        val delimiterRegex = Regex("[\\n,،;؛]+")
        val rawTokens = rawInput.split(delimiterRegex)
        val seenNorm = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (token in rawTokens) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) continue
            val norm = normalizeText(trimmed)
            if (norm.isNotEmpty() && seenNorm.add(norm)) result.add(trimmed)
        }
        return result
    }


    /** V69 exact medical-key comparison used to collapse explicit queries to one result. */
    private fun compactLatinSearchKey(value: String): String? {
        val normalized = normalizeText(value)
        if (normalized.isBlank()) return null
        val isLatinMedical = normalized.all { ch ->
            ch == ' ' || ch in 'a'..'z' || ch.isDigit()
        }
        if (!isLatinMedical) return null
        return normalized.filter { it in 'a'..'z' || it.isDigit() }
            .takeIf { it.length >= 3 }
    }

    private fun isExactRequestedTest(query: String, candidate: LabTest): Boolean {
        val normalizedQuery = normalizeText(query)
        if (normalizedQuery.isBlank()) return false
        if (candidate.normEnglish == normalizedQuery ||
            candidate.normMarket == normalizedQuery ||
            candidate.normArabic == normalizedQuery
        ) return true

        val compactQuery = compactLatinSearchKey(query) ?: return false
        return compactLatinSearchKey(candidate.normEnglish) == compactQuery ||
            compactLatinSearchKey(candidate.normMarket) == compactQuery
    }


    /** V114: unified dropdown catalogue/natural-language suggestions used by every test search field. */
    fun smartSearchCandidates(query: String): List<LabTest> = repository.smartSearchTests(query)

    fun onManualQueryChanged(newQuery: String) {
        _recognizedTests.value = emptyList()
        _searchQuery.value = newQuery
        val trimmed = newQuery.trim()
        if (trimmed.length < 2) {
            _uiState.value = SearchUiState.EmptyQuery
            return
        }

        // Preserve the established batch behaviour for pasted multi-test lists.
        if (newQuery.contains('\n') || newQuery.contains(',') || newQuery.contains('،') || newQuery.contains(';') || newQuery.contains('؛')) {
            onQueryChanged(newQuery)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val selectedIds = _selectedTests.value.map { it.id }.toSet()
            val candidates = repository.smartSearchTests(trimmed).filter { it.id !in selectedIds }
            _uiState.value = if (candidates.isNotEmpty()) {
                SearchUiState.Success(
                    query = newQuery,
                    queryGroups = listOf(QueryGroup(query = newQuery, candidates = candidates)),
                    unmatchedQueries = emptyList()
                )
            } else {
                SearchUiState.NoResults(unmatchedQueries = listOf(trimmed))
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        val trimmedInput = newQuery.trim()
        if (trimmedInput.isEmpty()) {
            _uiState.value = SearchUiState.EmptyQuery
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val queries = parseQueries(newQuery)
            if (queries.isEmpty()) {
                _uiState.value = SearchUiState.EmptyQuery
                return@launch
            }
            val selectedIds = _selectedTests.value.map { it.id }.toSet()
            val queryGroups = mutableListOf<QueryGroup>()
            val unmatchedQueries = mutableListOf<String>()
            for (q in queries) {
                val allMatches = repository.searchTests(q)
                // V69: when the user typed a full catalogue name/abbreviation, show only
                // that exact test instead of related substring results. This is especially
                // important for pasted multi-line price inquiries.
                val exactMatches = allMatches.filter { candidate -> isExactRequestedTest(q, candidate) }
                val preferredMatches = if (exactMatches.isNotEmpty()) exactMatches else allMatches
                val selectedExactMatch = preferredMatches.any { candidate ->
                    candidate.id in selectedIds && isExactRequestedTest(q, candidate)
                }
                val matches = preferredMatches.filter { it.id !in selectedIds }
                when {
                    selectedExactMatch -> Unit // this requested test was already added
                    matches.isNotEmpty() -> queryGroups.add(QueryGroup(query = q, candidates = matches))
                    preferredMatches.any { it.id in selectedIds } -> Unit // already added; do not show as unmatched
                    else -> unmatchedQueries.add(q)
                }
            }
            _uiState.value = when {
                queryGroups.isNotEmpty() -> SearchUiState.Success(
                    query = newQuery,
                    queryGroups = queryGroups,
                    unmatchedQueries = unmatchedQueries
                )
                unmatchedQueries.isNotEmpty() -> SearchUiState.NoResults(unmatchedQueries = unmatchedQueries)
                else -> SearchUiState.EmptyQuery // every requested test is already selected
            }
        }
    }

    /**
     * V40: Match OCR text from a lab request image/PDF against the local catalogue.
     * We deliberately return catalogue objects (not raw OCR strings) so the normal
     * search UI can present exact English test names for human review before selection.
     */
    fun detectTestsFromRecognizedText(rawText: String): List<LabTest> {
        if (rawText.isBlank()) return emptyList()

        val normalizedDocument = normalizeText(rawText.replace('\n', ' '))
        if (normalizedDocument.isBlank()) return emptyList()
        val paddedDocument = " $normalizedDocument "
        val documentTokens = normalizedDocument.split(' ').filter { it.isNotBlank() }.toSet()

        // OCR may insert punctuation/spaces inside handwritten abbreviations. Keep a compact
        // Latin/digit representation as a second signal (e.g. "Hb A1 c" -> "hba1c").
        val latinDocument = rawText.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val latinTokens = latinDocument.split(' ').filter { it.isNotBlank() }.toSet()
        val compactLatin = latinDocument.replace(" ", "")

        data class PositionedTest(val test: LabTest, val position: Int)
        val positioned = mutableListOf<PositionedTest>()
        val already = mutableSetOf<Int>()
        val allTests = repository.getLabTests()

        fun add(test: LabTest?, position: Int) {
            if (test != null && already.add(test.id)) {
                positioned += PositionedTest(test, position)
            }
        }

        fun exactCatalogueTest(vararg preferredNames: String): LabTest? {
            val norms = preferredNames.map(::normalizeText).filter { it.isNotBlank() }.toSet()
            return allTests.firstOrNull { test ->
                test.normEnglish in norms || test.normMarket in norms
            } ?: preferredNames.asSequence()
                .flatMap { repository.searchTests(it).asSequence() }
                .firstOrNull()
        }

        fun signalPosition(vararg signals: String): Int {
            return signals.asSequence()
                .map { normalizeText(it) }
                .filter { it.isNotBlank() }
                .map { normalizedDocument.indexOf(it) }
                .filter { it >= 0 }
                .minOrNull() ?: Int.MAX_VALUE
        }

        fun hasToken(vararg variants: String): Boolean {
            return variants.any { variant ->
                val v = variant.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (v.isBlank()) false
                else v in latinTokens || (v.length >= 4 && compactLatin.contains(v))
            }
        }

        // First pass: exact catalogue names/market names anywhere in the OCR document.
        for (test in allTests) {
            val aliases = linkedSetOf(test.normEnglish, test.normMarket)
                .filter { it.isNotBlank() }

            var bestPosition = Int.MAX_VALUE
            for (alias in aliases) {
                val compact = alias.trim()
                if (compact.isBlank()) continue

                val isSingleToken = !compact.contains(' ')
                val acceptableShortToken = compact.length >= 3 || compact.any { it.isDigit() }
                val found = if (isSingleToken) {
                    acceptableShortToken && compact in documentTokens
                } else {
                    paddedDocument.contains(" $compact ")
                }

                if (found) {
                    val pos = normalizedDocument.indexOf(compact).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    if (pos < bestPosition) bestPosition = pos
                }
            }

            if (bestPosition != Int.MAX_VALUE) add(test, bestPosition)
        }

        // Extra pass: OCR often places one abbreviation/name on its own line. Exact
        // catalogue search catches punctuation variants such as ALT(SGPT), TSH, HbA1c.
        val segments = rawText
            .split(Regex("[\\n,،;؛•|]+"))
            .map { it.trim() }
            .filter { it.length in 2..120 }

        for ((index, segment) in segments.withIndex()) {
            val norm = normalizeText(segment)
            if (norm.isBlank()) continue
            val searchMatches = repository.searchTests(segment)
            val exact = searchMatches.firstOrNull { candidate ->
                candidate.normEnglish == norm || candidate.normMarket == norm || candidate.normArabic == norm
            } ?: searchMatches.singleOrNull { candidate ->
                norm.length >= 3 && candidate.normSearch.split(' ').any { it == norm }
            }
            if (exact != null) {
                add(exact, normalizedDocument.indexOf(norm).takeIf { it >= 0 } ?: (100000 + index))
            }
        }

        // Abbreviations are often printed/handwritten beside each other. Accept a single
        // OCR token only when it uniquely resolves in the catalogue.
        for ((index, token) in normalizedDocument.split(' ').withIndex()) {
            if (token.length < 3 || token.all { it.isDigit() }) continue
            val matches = repository.searchTests(token)
            val unique = matches.singleOrNull { candidate ->
                candidate.normEnglish == token ||
                    candidate.normMarket == token ||
                    candidate.normSearch.split(' ').any { it == token }
            }
            if (unique != null) add(unique, index)
        }

        // V60 medical handwriting dictionary. These are conservative aliases for common
        // lab abbreviations that doctors routinely write but that are not always literal
        // catalogue names (e.g. INR, RBS, HBV, Blood Group). Long compact matches also
        // tolerate OCR inserting spaces between handwritten letters.
        if (hasToken("cbc", "c8c")) add(exactCatalogueTest("CBC"), signalPosition("cbc"))
        if (hasToken("crp")) add(exactCatalogueTest("CRP"), signalPosition("crp"))
        if (hasToken("esr")) add(exactCatalogueTest("ESR"), signalPosition("esr"))
        if (hasToken("rbs") || compactLatin.contains("randomglucose")) {
            add(exactCatalogueTest("Glucose Random", "سكر عشوائي"), signalPosition("rbs", "glucose random"))
        }
        if (hasToken("hba1c", "hbalc", "hbaic", "hb1ac") || compactLatin.contains("glycatedhaemoglobin")) {
            add(exactCatalogueTest("Glycated Haemoglobin (HbA1c)", "HbA1c السكر التراكمي"), signalPosition("hba1c"))
        }
        if (hasToken("inr")) {
            add(exactCatalogueTest("Prothrombin Time & Conc", "PT"), signalPosition("inr"))
        }
        if (hasToken("sgpt", "alt")) add(exactCatalogueTest("ALT (SGPT)", "ALT"), signalPosition("sgpt", "alt"))
        if (hasToken("sgot", "ast")) add(exactCatalogueTest("AST (SGOT)", "AST"), signalPosition("sgot", "ast"))
        if (hasToken("albumin", "albmin", "albunin")) add(exactCatalogueTest("Albumin"), signalPosition("albumin"))
        if (hasToken("creatinine", "creatinin", "creatin", "creat")) add(exactCatalogueTest("Creatinine"), signalPosition("creatinine", "creat"))
        if (hasToken("urea")) add(exactCatalogueTest("Blood Urea", "Urea"), signalPosition("urea"))

        val bilirubinSeen = hasToken("bilirubin", "bilrubin", "bilirbin", "bili")
        if (bilirubinSeen) {
            val directSeen = hasToken("direct")
            val indirectSeen = hasToken("indirect")
            when {
                directSeen || indirectSeen -> {
                    if (directSeen) add(exactCatalogueTest("Bilirubin (Direct)", "Bilirubin Direct"), signalPosition("direct"))
                    if (indirectSeen) add(exactCatalogueTest("Bilirubin (Indirect)", "Bilirubin Indirect"), signalPosition("indirect"))
                }
                else -> add(exactCatalogueTest("Bilirubin (Total)", "Bilirubin Total"), signalPosition("bilirubin"))
            }
        }

        if (hasToken("hbv", "hbsag", "hbs")) add(exactCatalogueTest("HBs Ag"), signalPosition("hbv", "hbs ag", "hbsag"))
        if (hasToken("hcv")) add(exactCatalogueTest("HCV Ab"), signalPosition("hcv"))
        if (hasToken("hiv")) add(exactCatalogueTest("HIV Ab"), signalPosition("hiv"))

        if (compactLatin.contains("bloodgroup") || hasToken("abo")) {
            add(exactCatalogueTest("ABO"), signalPosition("blood group", "abo"))
            // Blood grouping requests normally need Rh alongside ABO in this catalogue.
            add(exactCatalogueTest("Rh", "Rh Factor"), signalPosition("blood group", "rh"))
        }

        return positioned
            .distinctBy { it.test.id }
            .sortedWith(compareBy<PositionedTest> { it.position }.thenBy { it.test.id })
            .take(100)
            .map { it.test }
    }

    fun applyRecognizedTestsToSearch(rawText: String): Int {
        val detected = detectTestsFromRecognizedText(rawText)
        val nonLabItems = NonLabMedicalLookup.extractKnownNonLabQueries(rawText)
        _recognizedTests.value = detected

        val searchItems = buildList {
            addAll(detected.map { it.englishName })
            addAll(nonLabItems)
        }.distinctBy { normalizeText(it) }

        if (searchItems.isEmpty()) return 0
        onQueryChanged(searchItems.joinToString("\n"))
        return searchItems.size
    }

    fun addSelectedTest(test: LabTest) {
        if (_selectedTests.value.none { it.id == test.id }) {
            _selectedTests.value = _selectedTests.value + test
        }
        val current = _searchQuery.value.trim()
        if (current.isNotBlank()) {
            settingsStore.addRecentSearch(current)
            // V63: keep the imported/manual search visible and only remove the test
            // that was just selected. The remaining detected tests stay on screen.
            onManualQueryChanged(current)
        }
    }

    /** V70: move a resolved multi-test price inquiry straight into the active order list. */
    fun addTestsToSelection(tests: List<LabTest>): Int {
        if (tests.isEmpty()) return 0
        val alreadySelected = _selectedTests.value.map { it.id }.toSet()
        val toAdd = tests
            .distinctBy { it.id }
            .filter { it.id !in alreadySelected }

        if (toAdd.isNotEmpty()) {
            _selectedTests.value = _selectedTests.value + toAdd
        }

        val current = _searchQuery.value.trim()
        if (current.isNotBlank()) settingsStore.addRecentSearch(current)
        clearSearch()
        return toAdd.size
    }

    fun addAllRecognizedTests(): Int {
        val alreadySelected = _selectedTests.value.map { it.id }.toSet()
        val toAdd = _recognizedTests.value.filter { it.id !in alreadySelected }
        if (toAdd.isEmpty()) return 0

        _selectedTests.value = _selectedTests.value + toAdd
        val current = _searchQuery.value.trim()
        if (current.isNotBlank()) settingsStore.addRecentSearch(current)

        // The batch is now fully represented in the selected list, so return the
        // workspace to the selected-tests section instead of leaving stale results.
        clearSearch()
        return toAdd.size
    }

    /** V42: Load the tests from a previous order back into the active order workspace. */
    fun prepareRepeatOrder(order: CustomerOrder): Int {
        val ids = order.items.map { it.testId }.toSet()
        val matched = repository.getLabTests().filter { it.id in ids }
        _selectedTests.value = matched
        clearSearch()
        return matched.size
    }

    fun removeSelectedTest(testId: Int) {
        _selectedTests.value = _selectedTests.value.filter { it.id != testId }
        if (_searchQuery.value.isNotEmpty()) onManualQueryChanged(_searchQuery.value)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _recognizedTests.value = emptyList()
        _uiState.value = SearchUiState.EmptyQuery
    }

    // ------------------------- HELPERS -------------------------

    private fun loadManagerPricesFromAsset(): Map<Int, String> {
        return try {
            val json = getApplication<Application>().assets
                .open("manager_lab2lab_prices.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val array = JSONArray(json)
            buildMap {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optInt("id", -1)
                    if (id <= 0 || item.isNull("lab_to_lab_price")) continue
                    val formatted = formatFirestorePrice(item.get("lab_to_lab_price"))
                    if (!formatted.isNullOrBlank()) put(id, formatted)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun documentTestId(documentId: String, fallbackId: Int?): Int? =
        documentId.removePrefix("test_").toIntOrNull() ?: fallbackId

    /**
     * Accept a normal numeric price or a legacy catalogue range such as 1250/1500.
     * The UI and PDF generator have historically used the first listed amount for ranges,
     * so order persistence now follows the same rule instead of silently storing 0.
     */
    private fun parsePriceInput(value: String): Double? {
        val normalized = value
            .trim()
            .replace(",", "")
            .replace(tr("جنيه", "EGP"), "", ignoreCase = true)
            .trim()
        val match = Regex("""^(\d+(?:\.\d+)?)(?:\s*/\s*\d+(?:\.\d+)?)?$""").matchEntire(normalized)
            ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun formatFirestorePrice(value: Any?): String? = when (value) {
        null -> null
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> formatNumber(value.toDouble())
        is Double -> formatNumber(value)
        is Number -> value.toString()
        else -> value.toString().trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }

    private fun numberAsDouble(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        else -> value?.toString()?.toDoubleOrNull() ?: 0.0
    }

    private fun normalizePhone(value: String): String = value.filter { it.isDigit() }

    private fun generateFileNumber(now: Long): String {
        val date = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date(now))
        val suffix = (now % 1000).toString().padStart(3, '0')
        return "AK-$date-$suffix"
    }

    private fun generateOrderNumber(now: Long, documentId: String): String {
        val date = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date(now))
        val suffix = documentId.takeLast(4).uppercase(Locale.US)
        return "ORD-$date-$suffix"
    }

    private fun currentUid(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private fun currentEmail(): String = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
}
