package com.example.update

import com.example.ui.LabeledIconAction
import androidx.compose.material.icons.filled.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val UPDATE_MANIFEST_URL =
    "https://github.com/ahmedmeshrf22-jpg/tahalil-alakkad-updates/releases/latest/download/latest.json"

private data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val mandatory: Boolean
)

private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val versionName: String) : UpdateState
    data class Ready(val versionName: String, val apk: File) : UpdateState
    data class Retry(val message: String, val allowBypass: Boolean) : UpdateState
}

@Composable
fun ForcedUpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var state: UpdateState by remember { mutableStateOf(UpdateState.Checking) }
    var refreshKey by remember { mutableStateOf(0) }
    var permissionRefresh by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshKey) {
        state = UpdateState.Checking
        state = checkAndPrepareUpdate(context) { versionName ->
            state = UpdateState.Downloading(versionName)
        }
    }

    when (val current = state) {
        UpdateState.UpToDate -> content()
        UpdateState.Checking -> UpdateBlockingScreen(
            title = "جاري التحقق من التحديثات",
            body = "لحظات للتأكد أن الجهاز يعمل على أحدث إصدار من تحاليل العقاد.",
            loading = true
        )
        is UpdateState.Downloading -> UpdateBlockingScreen(
            title = "يوجد تحديث جديد",
            body = "جاري تنزيل ${current.versionName} تلقائيًا. لا تغلق التطبيق.",
            loading = true
        )
        is UpdateState.Ready -> {
            permissionRefresh // force re-check after returning from Android install-source settings
            val canInstall = canRequestInstall(context)
            UpdateBlockingScreen(
                title = "التحديث جاهز للتثبيت",
                body = if (canInstall) {
                    "تم تنزيل ${current.versionName}. يجب تثبيت التحديث قبل متابعة استخدام التطبيق."
                } else {
                    "تم تنزيل ${current.versionName}. اسمح لتطبيق تحاليل العقاد بتثبيت التحديثات مرة واحدة، ثم ارجع للتطبيق."
                },
                loading = false,
                buttonText = if (canInstall) "تثبيت التحديث" else "السماح بالتثبيت",
                onButton = {
                    if (canInstall) installApk(context, current.apk)
                    else openInstallPermission(context)
                }
            )
        }
        is UpdateState.Retry -> UpdateBlockingScreen(
            title = "تعذر التحقق من التحديث",
            body = current.message,
            loading = false,
            buttonText = "إعادة المحاولة",
            onButton = { refreshKey++ },
            secondaryText = if (current.allowBypass) "فتح التطبيق بدون فحص التحديث" else null,
            onSecondary = if (current.allowBypass) ({ state = UpdateState.UpToDate }) else null
        )
    }
}

@Composable
private fun UpdateBlockingScreen(
    title: String,
    body: String,
    loading: Boolean,
    buttonText: String? = null,
    onButton: (() -> Unit)? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (loading) {
            CircularProgressIndicator()
        } else if (buttonText != null && onButton != null) {
            LabeledIconAction(label = buttonText, onClick = onButton, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.TouchApp, contentDescription = null) }
        }
        if (secondaryText != null && onSecondary != null) {
            Spacer(Modifier.height(12.dp))
            LabeledIconAction(label = secondaryText, onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.TouchApp, contentDescription = null) }
        }
    }
}

private suspend fun checkAndPrepareUpdate(
    context: Context,
    onDownloadStarted: (String) -> Unit
): UpdateState = withContext(Dispatchers.IO) {
    var updateRequired = false
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val manifestRequest = Request.Builder()
            .url(UPDATE_MANIFEST_URL)
            .header("User-Agent", "Tahalil-Alakkad/${BuildConfig.VERSION_CODE}")
            .build()

        val manifestText = client.newCall(manifestRequest).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("تعذر الوصول لملف التحديث (${response.code})")
            response.body?.string().orEmpty()
        }
        val json = JSONObject(manifestText)
        val manifest = UpdateManifest(
            versionCode = json.getInt("versionCode"),
            versionName = json.optString("versionName", "V${json.getInt("versionCode")}"),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.getString("sha256").lowercase(),
            mandatory = json.optBoolean("mandatory", true)
        )

        if (manifest.versionCode <= BuildConfig.VERSION_CODE || !manifest.mandatory) {
            return@withContext UpdateState.UpToDate
        }

        updateRequired = true
        withContext(Dispatchers.Main) { onDownloadStarted(manifest.versionName) }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "Tahalil-Alakkad-update.apk")
        if (apkFile.exists()) apkFile.delete()

        val apkRequest = Request.Builder()
            .url(manifest.apkUrl)
            .header("User-Agent", "Tahalil-Alakkad/${BuildConfig.VERSION_CODE}")
            .build()

        client.newCall(apkRequest).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("فشل تنزيل التحديث (${response.code})")
            val body = response.body ?: throw IllegalStateException("ملف التحديث فارغ")
            FileOutputStream(apkFile).use { out -> body.byteStream().copyTo(out) }
        }

        if (sha256(apkFile) != manifest.sha256) {
            apkFile.delete()
            throw SecurityException("فشل التحقق من سلامة ملف التحديث")
        }
        if (!isTrustedUpdateApk(context, apkFile)) {
            apkFile.delete()
            throw SecurityException("ملف التحديث لا يطابق تطبيق تحاليل العقاد أو توقيعه الأصلي")
        }

        UpdateState.Ready(manifest.versionName, apkFile)
    } catch (e: Exception) {
        UpdateState.Retry(
            message = e.message ?: "تحقق من اتصال الإنترنت ثم حاول مرة أخرى.",
            allowBypass = !updateRequired
        )
    }
}

private fun canRequestInstall(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun openInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun installApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Suppress("DEPRECATION")
private fun isTrustedUpdateApk(context: Context, apk: File): Boolean {
    val pm = context.packageManager
    val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
    } else {
        pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
    } ?: return false

    if (archive.packageName != context.packageName) return false

    val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
    } else {
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    }

    fun fingerprints(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty().toList()
        } else {
            info.signatures.orEmpty().toList()
        }
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    return fingerprints(current).isNotEmpty() && fingerprints(current) == fingerprints(archive)
}
