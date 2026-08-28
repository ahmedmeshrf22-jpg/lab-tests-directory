package com.example

import android.Manifest

import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.AccountDisabledScreen
import com.example.backend.FirestoreResultFileStore
import com.example.ui.AppPinUnlockScreen
import com.example.ui.DeviceAuthorizationScreen
import com.example.ui.DeviceUnlockScreen
import com.example.ui.LabTestsApp
import com.example.ui.LabTestsViewModel
import com.example.ui.LabOperatorScreen
import com.example.data.model.normalizeUserRole
import com.example.ui.LoginScreen
import com.example.ui.SplashScreen
import com.example.ui.theme.LabTestsTheme
import com.example.notifications.OrderNotificationManager
import com.example.notifications.LabOrderBackgroundScheduler
import com.example.notifications.OrderRealtimeNotificationService
import com.example.notifications.BackupNotificationManager
import com.example.notifications.AutoBackupScheduler
import com.example.update.ForcedUpdateGate
import com.example.resilience.ShadowBackupReplicator
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val viewModel: LabTestsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirestoreResultFileStore.cleanupStaleResultCache(this)
        OrderNotificationManager.ensureChannel(this)
        BackupNotificationManager.ensureChannel(this)
        AutoBackupScheduler.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9901)
        }
        OrderNotificationManager.registerCurrentToken()

        // Sensitive pricing and account data must never appear in screenshots,
        // screen recordings, casting, or the recent-apps thumbnail.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // V45: block third-party overlay windows on Android 12+ to reduce tapjacking risk.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        // Install App Check before any other Firebase SDK is used. Enforcement is
        // enabled later from Firebase Console after the signing SHA-256 is registered.
        // V85 stability: Firebase/App Check diagnostics must never block app startup.
        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        // Crash reporting is best-effort and must not prevent the counter from opening.
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(true)
                setCustomKey("app_version_code", BuildConfig.VERSION_CODE)
                setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
                setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            }
        }

        // Process a shared image only after Firebase/ViewModel dependencies are ready.
        handleIncomingQrShare(intent)
        handleOrderNotificationIntent(intent)

        enableEdgeToEdge()

        val auth = FirebaseAuth.getInstance()

        setContent {
            val appSettings by viewModel.appSettings.collectAsState()
            val accountEnabled by viewModel.accountEnabled.collectAsState()
            val currentUserProfile by viewModel.currentUserProfile.collectAsState()
            val actualManager by viewModel.actualManager.collectAsState()
            val actingAsUser by viewModel.actingAsUser.collectAsState()
            val effectiveUserProfile = if (actualManager && actingAsUser != null) actingAsUser else currentUserProfile
            val deviceAccessStatus by viewModel.deviceAccessStatus.collectAsState()
            val externalPickerActive by viewModel.externalPickerActive.collectAsState()
            LabTestsTheme(darkTheme = appSettings.darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ForcedUpdateGate {
                    var currentUser by remember { mutableStateOf(auth.currentUser) }
                    var isSplashActive by remember { mutableStateOf(true) }
                    var isLocallyUnlocked by remember { mutableStateOf(false) }
                    var isLoading by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var failedLoginAttempts by remember { mutableStateOf(0) }
                    var loginLockedUntilMillis by remember { mutableStateOf(0L) }
                    var unlockError by remember { mutableStateOf<String?>(null) }
                    var backgroundedAt by remember { mutableStateOf(0L) }

                    LaunchedEffect(Unit) {
                        delay(900L)
                        isSplashActive = false
                    }

                    LaunchedEffect(appSettings.securityEnabled, currentUser) {
                        if (currentUser != null && !appSettings.securityEnabled) {
                            isLocallyUnlocked = true
                            unlockError = null
                        }
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current

                    // V129: best-effort Supabase shadow backup. Firebase stays authoritative.
                    // The replicator is started only for an enabled account on an approved device.
                    // Any Supabase/network failure is swallowed inside the replicator and can never
                    // block login, Firestore writes, results, notifications, or the UI.
                    LaunchedEffect(currentUser?.uid, accountEnabled, deviceAccessStatus, effectiveUserProfile?.role) {
                        if (currentUser != null && accountEnabled == true && deviceAccessStatus == "approved") {
                            ShadowBackupReplicator.start(
                                context = this@MainActivity,
                                normalizedRole = normalizeUserRole(effectiveUserProfile?.role.orEmpty())
                            )
                        } else {
                            ShadowBackupReplicator.stop()
                        }
                    }

                    // V107: one lightweight background checker serves both sides.
                    // Lab gets new/edit/cancel alerts; clinic gets accepted/result/cancel alerts.
                    LaunchedEffect(effectiveUserProfile?.role, currentUser?.uid, actingAsUser?.uid) {
                        if (currentUser != null) {
                            OrderNotificationManager.ensureChannels(this@MainActivity)
                            OrderNotificationManager.registerCurrentToken()
                            LabOrderBackgroundScheduler.schedule(this@MainActivity)
                            OrderRealtimeNotificationService.start(this@MainActivity)
                            val isLab = normalizeUserRole(effectiveUserProfile?.role.orEmpty()) == "lab_operator"
                            if (isLab) {
                                viewModel.stopClinicOrderNotificationsRealtime()
                            } else {
                                viewModel.startClinicOrderNotificationsRealtime()
                            }
                        } else {
                            viewModel.stopClinicOrderNotificationsRealtime()
                            LabOrderBackgroundScheduler.cancel(this@MainActivity)
                            OrderRealtimeNotificationService.stop(this@MainActivity)
                        }
                    }

                    DisposableEffect(auth) {
                        val listener = FirebaseAuth.AuthStateListener { fAuth ->
                            currentUser = fAuth.currentUser
                            FirebaseCrashlytics.getInstance().apply {
                                setUserId(fAuth.currentUser?.uid.orEmpty())
                                setCustomKey("signed_in", fAuth.currentUser != null)
                            }
                            viewModel.onAuthenticatedUserChanged(fAuth.currentUser?.email, fAuth.currentUser?.uid)
                            if (fAuth.currentUser != null) {
                                OrderNotificationManager.registerCurrentToken()
                            }
                            if (fAuth.currentUser == null) {
                                isLocallyUnlocked = false
                                viewModel.clearLab2LabState()
                            }
                        }
                        auth.addAuthStateListener(listener)
                        onDispose {
                            auth.removeAuthStateListener(listener)
                        }
                    }

                    DisposableEffect(lifecycleOwner, appSettings.securityEnabled, appSettings.autoLockSeconds, appSettings.unlockMode, externalPickerActive) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) {
                                if (auth.currentUser != null) {
                                    viewModel.lockAdmin()
                                }
                                // V93: choosing a prescription image/document is a trusted system flow,
                                // not the user leaving the app. Do not lock staff out mid-picker.
                                if (!externalPickerActive && auth.currentUser != null && appSettings.securityEnabled) {
                                    backgroundedAt = System.currentTimeMillis()
                                    if (appSettings.autoLockSeconds == 0) {
                                        isLocallyUnlocked = false
                                    }
                                }
                            } else if (event == Lifecycle.Event.ON_START) {
                                if (!externalPickerActive && auth.currentUser != null && appSettings.securityEnabled) {
                                    val elapsedSeconds = if (backgroundedAt > 0L) {
                                        (System.currentTimeMillis() - backgroundedAt) / 1000L
                                    } else {
                                        Long.MAX_VALUE
                                    }
                                    val timeoutReached = appSettings.autoLockSeconds == 0 ||
                                        elapsedSeconds >= appSettings.autoLockSeconds
                                    if (!isLocallyUnlocked || timeoutReached) {
                                        isLocallyUnlocked = false
                                        if (appSettings.unlockMode != "pin" && isSecurityConfiguredOnDevice()) {
                                            launchBiometricPrompt(
                                                onSuccess = {
                                                    isLocallyUnlocked = true
                                                    backgroundedAt = 0L
                                                    unlockError = null
                                                },
                                                onError = { unlockError = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    when {
                        isSplashActive -> {
                            SplashScreen()
                        }

                        currentUser == null -> {
                            LoginScreen(
                                onLoginClick = { email, password ->
                                    val now = System.currentTimeMillis()
                                    if (loginLockedUntilMillis > now) {
                                        val remaining = ((loginLockedUntilMillis - now + 999L) / 1000L).coerceAtLeast(1L)
                                        errorMessage = "محاولات دخول كثيرة. حاول بعد $remaining ثانية"
                                        return@LoginScreen
                                    }
                                    if (email.isBlank() || password.isBlank()) {
                                        errorMessage = "يرجى إدخال البريد الإلكتروني وكلمة المرور"
                                        return@LoginScreen
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    auth.signInWithEmailAndPassword(email, password)
                                        .addOnCompleteListener { task ->
                                            isLoading = false
                                            if (task.isSuccessful) {
                                                failedLoginAttempts = 0
                                                loginLockedUntilMillis = 0L
                                                viewModel.onAuthenticatedUserChanged(
                                                    auth.currentUser?.email ?: email,
                                                    auth.currentUser?.uid
                                                )
                                                isLocallyUnlocked = true
                                            } else {
                                                val exception = task.exception
                                                if (exception !is FirebaseNetworkException) {
                                                    val nextFailures = failedLoginAttempts + 1
                                                    failedLoginAttempts = nextFailures
                                                    if (nextFailures >= 5) {
                                                        failedLoginAttempts = 0
                                                        loginLockedUntilMillis = System.currentTimeMillis() + 60_000L
                                                        errorMessage = "تم إيقاف محاولات تسجيل الدخول لمدة 60 ثانية للحماية"
                                                        return@addOnCompleteListener
                                                    }
                                                }
                                                errorMessage = when (exception) {
                                                    is FirebaseAuthInvalidUserException,
                                                    is FirebaseAuthInvalidCredentialsException -> "البريد الإلكتروني أو كلمة المرور غير صحيحة"
                                                    is FirebaseNetworkException -> "خطأ في الاتصال بالإنترنت. يرجى التحقق من الشبكة."
                                                    else -> exception?.localizedMessage ?: "فشل تسجيل الدخول. يرجى التأكد من البيانات."
                                                }
                                            }
                                        }
                                },
                                isLoading = isLoading,
                                errorMessage = errorMessage
                            )
                        }

                        currentUser != null && accountEnabled == false -> {
                            AccountDisabledScreen(
                                onLogout = {
                                    viewModel.clearLab2LabState()
                                    auth.signOut()
                                    isLocallyUnlocked = false
                                }
                            )
                        }

                        currentUser != null && accountEnabled == null -> {
                            SplashScreen()
                        }

                        // V82 production gate: no operational data opens until this exact
                        // device is explicitly approved. Pending/checking/error/revoked/rejected
                        // states all remain inside the authorization screen.
                        currentUser != null && deviceAccessStatus != "approved" -> {
                            DeviceAuthorizationScreen(
                                status = deviceAccessStatus,
                                onRefresh = { viewModel.recheckDeviceAuthorization() },
                                onApproveWithAdminPin = { pin, result ->
                                    viewModel.approveCurrentDeviceWithAdminPin(pin, result)
                                },
                                onLogout = {
                                    viewModel.clearLab2LabState()
                                    auth.signOut()
                                    isLocallyUnlocked = false
                                }
                            )
                        }

                        appSettings.securityEnabled && !isLocallyUnlocked -> {
                            if (appSettings.unlockMode == "pin" && appSettings.appPinConfigured) {
                                AppPinUnlockScreen(
                                    onUnlock = { pin ->
                                        val ok = viewModel.verifyAppPin(pin)
                                        if (ok) {
                                            isLocallyUnlocked = true
                                            backgroundedAt = 0L
                                            unlockError = null
                                        }
                                        ok
                                    },
                                    onLogoutClick = {
                                        viewModel.clearLab2LabState()
                                        auth.signOut()
                                        isLocallyUnlocked = false
                                    }
                                )
                            } else {
                                val isSecured = isSecurityConfiguredOnDevice()

                                LaunchedEffect(currentUser) {
                                    if (isSecured) {
                                        launchBiometricPrompt(
                                            onSuccess = {
                                                isLocallyUnlocked = true
                                                unlockError = null
                                            },
                                            onError = { unlockError = it }
                                        )
                                    }
                                }

                                DeviceUnlockScreen(
                                    onUnlockClick = {
                                        if (isSecured || isSecurityConfiguredOnDevice()) {
                                            launchBiometricPrompt(
                                                onSuccess = {
                                                    isLocallyUnlocked = true
                                                    unlockError = null
                                                },
                                                onError = { unlockError = it }
                                            )
                                        }
                                    },
                                    onLogoutClick = {
                                        viewModel.clearLab2LabState()
                                        auth.signOut()
                                        isLocallyUnlocked = false
                                    },
                                    isSecurityConfigured = isSecured,
                                    errorMessage = unlockError
                                )
                            }
                        }

                        effectiveUserProfile != null && normalizeUserRole(effectiveUserProfile?.role.orEmpty()) == "lab_operator" -> {
                            LabOperatorScreen(
                                viewModel = viewModel,
                                userEmail = effectiveUserProfile?.email.orEmpty().ifBlank { currentUser?.email.orEmpty() },
                                managerActingAs = if (actualManager) actingAsUser else null,
                                onReturnToManager = if (actualManager && actingAsUser != null) {
                                    { viewModel.returnToManagerMode() }
                                } else null,
                                onLogout = {
                                    viewModel.clearLab2LabState()
                                    auth.signOut()
                                    isLocallyUnlocked = false
                                }
                            )
                        }

                        else -> {
                            LabTestsApp(
                                viewModel = viewModel,
                                currentUserEmail = currentUser?.email,
                                currentUserUid = currentUser?.uid,
                                onLogout = {
                                    viewModel.clearLab2LabState()
                                    auth.signOut()
                                    isLocallyUnlocked = false
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingQrShare(intent)
        handleOrderNotificationIntent(intent)
    }

    private fun handleOrderNotificationIntent(incoming: Intent?) {
        if (incoming?.getBooleanExtra("open_orders", false) != true) return
        viewModel.requestOpenOrderFromNotification(incoming.getStringExtra("order_id"))
        incoming.removeExtra("open_orders")
    }

    private fun handleIncomingQrShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return

        val mime = incoming.type.orEmpty().substringBefore(';').trim().lowercase()
        val allowedMime = mime in setOf(
            "image/jpeg", "image/jpg", "image/png", "image/webp",
            "image/heic", "image/heif", "image/avif"
        )
        if (!allowedMime) return

        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            incoming.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            viewModel.queueSharedQrUri(uri.toString())
            // Do not queue the same share again if Android recreates the activity.
            incoming.action = null
        }
    }

    private fun isSecurityConfiguredOnDevice(): Boolean {
        val biometricManager = BiometricManager.from(this)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is not supported on Android 10 and lower.
            // Use the compatible weak-biometric/device-credential combination and also check
            // the device lock directly so PIN/pattern/password users are not blocked.
            val compatibleAuthenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.isDeviceSecure == true ||
                    biometricManager.canAuthenticate(compatibleAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    private fun launchBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }
        }

        try {
            val biometricPrompt = BiometricPrompt(this, executor, callback)
            val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح دليل التحاليل")
                .setSubtitle("استخدم البصمة أو رمز قفل الهاتف للمتابعة")
                .setAllowedAuthenticators(authenticators)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "فشل في فتح قفل الهاتف")
        }
    }
}
