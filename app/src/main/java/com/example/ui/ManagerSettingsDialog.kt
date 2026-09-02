package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.settings.AppSettings
import com.example.settings.appText
import com.example.settings.tr
import com.example.resilience.RecoveryReadOnlyProbe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManagerSettingsDialog(
    viewModel: LabTestsViewModel,
    settings: AppSettings,
    currentUserEmail: String?,
    currentUserUid: String?,
    onDismiss: () -> Unit,
    onOpenManagerDashboard: () -> Unit,
    onLogout: (() -> Unit)?
) {
    val context = LocalContext.current
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showAdminPinEditor by remember { mutableStateOf(false) }
    var currentAdminPin by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }
    var confirmAdminPin by remember { mutableStateOf("") }
    var changingAdminPin by remember { mutableStateOf(false) }
    var adminPinMessage by remember { mutableStateOf<String?>(null) }
    var adminPinMessageSuccess by remember { mutableStateOf(false) }
    var brandMessage by remember { mutableStateOf<String?>(null) }
    var brandMessageSuccess by remember { mutableStateOf(false) }
    var recoveryChecking by remember { mutableStateOf(false) }
    var recoveryMessage by remember { mutableStateOf<String?>(null) }
    var recoverySuccess by remember { mutableStateOf(false) }
    val tr: (String, String) -> String = { ar, en -> appText(settings, ar, en) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            brandMessage = null
            viewModel.importBrandLogo(uri) { ok, message ->
                brandMessageSuccess = ok
                brandMessage = message
            }
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (settings.darkMode) Color(0xFF0F172A) else Color(0xFFF5F7FB),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("إعدادات التطبيق", "App Settings"),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (settings.darkMode) Color.White else Color(0xFF102A43)
                        )
                        Text(
                            text = tr("إعدادات الإدارة المحمية", "Protected administration settings"),
                            fontSize = 12.sp,
                            color = if (settings.darkMode) Color(0xFFCBD5E1) else Color(0xFF64748B)
                        )
                    }
                    LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("إغلاق", "Close"),
                            tint = if (settings.darkMode) Color.White else Color(0xFF334155)
                        )
                    }
                }

                HorizontalDivider(color = if (settings.darkMode) Color(0xFF334155) else Color(0xFFE2E8F0))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SettingsSectionCard(title = tr("اللغة", "Language"), icon = Icons.Default.Language) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabeledIconAction(label = tr("العربية", "Arabic"), onClick = { viewModel.updateAppSettings(settings.copy(language = "ar")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Language, contentDescription = null) }
                                LabeledIconAction(label = "English", onClick = { viewModel.updateAppSettings(settings.copy(language = "en")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Language, contentDescription = null) }
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("الحساب", "Account"), icon = Icons.Default.ManageAccounts) {
                            SettingsInfoRow(tr("البريد الإلكتروني", "Email"), currentUserEmail ?: "—")
                            SettingsInfoRow(tr("نوع الحساب", "Account type"), tr("مدير النظام", "General Manager"))
                            SettingsInfoRow("User UID", currentUserUid ?: "—")
                            if (onLogout != null) {
                                Spacer(Modifier.height(8.dp))
                                LabeledIconAction(label = tr("تسجيل الخروج", "Log out"), onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, contentDescription = null) }
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("الأمان", "Security"), icon = Icons.Default.Lock) {
                            SettingsSwitchRow(
                                title = tr("قفل التطبيق", "App lock"),
                                subtitle = tr("استخدام البصمة أو رمز قفل الهاتف عند فتح التطبيق", "Use biometrics or device lock when opening the app"),
                                checked = settings.securityEnabled,
                                onCheckedChange = {
                                    viewModel.updateAppSettings(settings.copy(securityEnabled = it))
                                }
                            )

                            if (settings.securityEnabled) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = tr("القفل التلقائي", "Auto lock"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(Modifier.height(8.dp))
                                ChoiceButtons(
                                    choices = listOf(
                                        0 to tr("فورًا", "Immediately"),
                                        60 to tr("دقيقة", "1 min"),
                                        300 to tr("5 دقائق", "5 min"),
                                        900 to tr("15 دقيقة", "15 min")
                                    ),
                                    selected = settings.autoLockSeconds,
                                    onSelected = {
                                        viewModel.updateAppSettings(settings.copy(autoLockSeconds = it))
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                LabeledIconAction(label = tr("فتح إعدادات البصمة / PIN في الهاتف", "Open phone biometrics / PIN settings"), onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Visibility, contentDescription = null) }
                            }

                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(Modifier.height(10.dp))

                            LabeledIconAction(label = tr("تغيير PIN الإدارة", "Change admin PIN"), onClick = {
                                    showAdminPinEditor = !showAdminPinEditor
                                    adminPinMessage = null
                                    if (!showAdminPinEditor) {
                                        currentAdminPin = ""
                                        newAdminPin = ""
                                        confirmAdminPin = ""
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, contentDescription = null) }

                            if (showAdminPinEditor) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    tr(
                                        "الـ PIN مكون من 6 أرقام ويؤمن الإدارة والأسعار و Lab 2 Lab على كل الأجهزة.",
                                        "The 6-digit PIN protects administration, prices and Lab 2 Lab on all devices."
                                    ),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = currentAdminPin,
                                    onValueChange = { currentAdminPin = it.filter(Char::isDigit).take(6); adminPinMessage = null },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                                    label = { Text(tr("PIN الحالي", "Current PIN")) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(7.dp))
                                OutlinedTextField(
                                    value = newAdminPin,
                                    onValueChange = { newAdminPin = it.filter(Char::isDigit).take(6); adminPinMessage = null },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                                    label = { Text(tr("PIN الجديد", "New PIN")) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(7.dp))
                                OutlinedTextField(
                                    value = confirmAdminPin,
                                    onValueChange = { confirmAdminPin = it.filter(Char::isDigit).take(6); adminPinMessage = null },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                                    label = { Text(tr("تأكيد PIN الجديد", "Confirm new PIN")) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    shape = RoundedCornerShape(12.dp),
                                    isError = confirmAdminPin.length == 6 && confirmAdminPin != newAdminPin
                                )
                                adminPinMessage?.let { message ->
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        message,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (adminPinMessageSuccess) Color(0xFF15803D) else Color(0xFFB91C1C)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LabeledIconAction(label = if (changingAdminPin) tr("جاري التغيير...", "Changing...") else tr("حفظ PIN الجديد", "Save new PIN"), onClick = {
                                        if (newAdminPin != confirmAdminPin) {
                                            adminPinMessageSuccess = false
                                            adminPinMessage = tr("تأكيد PIN الجديد غير مطابق", "New PIN confirmation does not match")
                                        } else {
                                            changingAdminPin = true
                                            adminPinMessage = null
                                            viewModel.changeAdminPin(currentAdminPin, newAdminPin) { ok, message ->
                                                changingAdminPin = false
                                                adminPinMessageSuccess = ok
                                                adminPinMessage = message
                                                if (ok) {
                                                    currentAdminPin = ""
                                                    newAdminPin = ""
                                                    confirmAdminPin = ""
                                                }
                                            }
                                        }
                                    }, modifier = Modifier.fillMaxWidth(), enabled = !changingAdminPin && currentAdminPin.length == 6 && newAdminPin.length == 6 && confirmAdminPin.length == 6) { Icon(Icons.Default.AddCircle, contentDescription = null) }
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("المظهر", "Appearance"), icon = Icons.Default.Palette) {
                            SettingsSwitchRow(
                                title = tr("الوضع الداكن", "Dark mode"),
                                subtitle = tr("تغيير خلفية التطبيق ووضع العرض", "Change app appearance"),
                                checked = settings.darkMode,
                                onCheckedChange = {
                                    viewModel.updateAppSettings(settings.copy(darkMode = it))
                                },
                                icon = Icons.Default.DarkMode
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = tr("حجم الخط", "Font size"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Spacer(Modifier.height(8.dp))
                            ChoiceButtons(
                                choices = listOf(0.90f to tr("صغير", "Small"), 1.0f to tr("متوسط", "Medium"), 1.15f to tr("كبير", "Large")),
                                selected = settings.fontScale,
                                onSelected = {
                                    viewModel.updateAppSettings(settings.copy(fontScale = it))
                                }
                            )
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("إعدادات البحث", "Search Settings"), icon = Icons.Default.Search) {
                            SettingsSwitchRow(
                                tr("إظهار الاسم الإنجليزي", "Show English name"),
                                tr("يظهر الاسم الإنجليزي في نتائج البحث", "Show English name in search results"),
                                settings.showEnglishName,
                                onCheckedChange = { enabled ->
                                    if (enabled || settings.showArabicName || settings.showMarketName) {
                                        viewModel.updateAppSettings(settings.copy(showEnglishName = enabled))
                                    }
                                }
                            )
                            SettingsSwitchRow(
                                tr("إظهار الاسم العربي", "Show Arabic name"),
                                tr("يظهر الاسم العربي في نتائج البحث", "Show Arabic name in search results"),
                                settings.showArabicName,
                                onCheckedChange = { enabled ->
                                    if (enabled || settings.showEnglishName || settings.showMarketName) {
                                        viewModel.updateAppSettings(settings.copy(showArabicName = enabled))
                                    }
                                }
                            )
                            SettingsSwitchRow(
                                tr("إظهار الاسم الدارج", "Show market name"),
                                tr("إظهار الاسم المستخدم في السوق المصري عند توفره", "Show common market name when available"),
                                settings.showMarketName,
                                onCheckedChange = { enabled ->
                                    if (enabled || settings.showEnglishName || settings.showArabicName) {
                                        viewModel.updateAppSettings(settings.copy(showMarketName = enabled))
                                    }
                                }
                            )
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("عرض الأسعار", "Price Display"), icon = Icons.Default.Settings) {
                            SettingsSwitchRow(
                                tr("سعر العميل", "Customer price"),
                                tr("إظهار سعر العميل في نتائج البحث والسلة", "Show customer price in search results and cart"),
                                settings.showCustomerPrice,
                                onCheckedChange = {
                                    viewModel.updateAppSettings(settings.copy(showCustomerPrice = it))
                                }
                            )
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("هوية المعمل والصور", "Lab Identity & Images"), icon = Icons.Default.Description) {
                            Text(
                                tr(
                                    "دي البيانات اللي بتظهر في واجهة التطبيق وصور العميل/المعمل. تقدر تغيرها لأي معمل من غير تعديل الكود.",
                                    "These details appear in the app and generated customer/lab images. They can be white-labeled without code changes."
                                ),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = settings.pdfLabName,
                                onValueChange = { viewModel.updateAppSettings(settings.copy(pdfLabName = it.take(60))) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                singleLine = true,
                                label = { Text(tr("اسم المعمل", "Lab name")) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.height(7.dp))
                            OutlinedTextField(
                                value = settings.brandTagline,
                                onValueChange = { viewModel.updateAppSettings(settings.copy(brandTagline = it.take(80))) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                                singleLine = true,
                                label = { Text(tr("السطر التعريفي", "Tagline")) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.height(7.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = settings.brandWhatsApp,
                                    onValueChange = { viewModel.updateAppSettings(settings.copy(brandWhatsApp = it.take(24))) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(tr("واتساب", "WhatsApp")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = settings.brandPhone,
                                    onValueChange = { viewModel.updateAppSettings(settings.copy(brandPhone = it.take(24))) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(tr("هاتف", "Phone")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            OutlinedTextField(
                                value = settings.brandAddress,
                                onValueChange = { viewModel.updateAppSettings(settings.copy(brandAddress = it.take(220))) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                                minLines = 2,
                                maxLines = 3,
                                label = { Text(tr("العنوان", "Address")) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabeledIconAction(label = if (settings.brandLogoPath.isBlank()) tr("اختيار لوجو", "Choose logo") else tr("تغيير اللوجو", "Change logo"), onClick = { logoPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Settings, contentDescription = null) }
                                if (settings.brandLogoPath.isNotBlank()) {
                                    LabeledIconAction(label = tr("اللوجو الافتراضي", "Default logo"), onClick = {
                                            viewModel.clearBrandLogo { ok, message ->
                                                brandMessageSuccess = ok
                                                brandMessage = message
                                            }
                                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                                }
                            }
                            brandMessage?.let { message ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    message,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (brandMessageSuccess) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            SettingsSwitchRow(
                                tr("سعر العميل في الصورة", "Customer price in image"),
                                tr("إظهار سعر العميل في الصور السريعة وإيصال العميل", "Show customer prices in generated images"),
                                settings.pdfIncludeCustomerPrice,
                                onCheckedChange = { viewModel.updateAppSettings(settings.copy(pdfIncludeCustomerPrice = it)) }
                            )
                            SettingsSwitchRow(
                                tr("إظهار الإجمالي", "Show totals"),
                                tr("إضافة إجمالي الأسعار في نهاية الصورة", "Add totals at the end of the image"),
                                settings.pdfShowTotals,
                                onCheckedChange = { viewModel.updateAppSettings(settings.copy(pdfShowTotals = it)) }
                            )
                            SettingsSwitchRow(
                                tr("سطر تواصل إضافي", "Extra contact line"),
                                tr("إضافة ملاحظة أو بيانات تواصل إضافية أسفل الصورة", "Add an extra contact/footer line"),
                                settings.pdfShowContactInfo,
                                onCheckedChange = { viewModel.updateAppSettings(settings.copy(pdfShowContactInfo = it)) }
                            )
                            if (settings.pdfShowContactInfo) {
                                OutlinedTextField(
                                    value = settings.pdfContactInfo,
                                    onValueChange = { viewModel.updateAppSettings(settings.copy(pdfContactInfo = it.take(180))) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                                    minLines = 2,
                                    maxLines = 3,
                                    label = { Text(tr("السطر الإضافي", "Extra footer")) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("المزامنة والبيانات", "Sync & Data"), icon = Icons.Default.Sync) {
                            SettingsInfoRow(tr("آخر تحديث", "Last sync"), formatLastSync(settings.lastSyncMillis, settings.language))
                            if (!syncMessage.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = syncMessage.orEmpty(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (syncMessage?.startsWith("تم") == true) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            SettingsInfoRow(
                                tr("تخزين النتائج", "Result storage"),
                                tr("Firebase المجاني • بدون خادم خارجي", "Firebase free • no external server")
                            )
                            Text(
                                tr(
                                    "صور وملفات PDF للنتائج تُحفظ داخل Firebase تلقائيًا ولا تحتاج أي إعداد إضافي.",
                                    "Result images and PDFs are stored automatically in Firebase with no extra setup."
                                ),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(Modifier.height(10.dp))
                            SettingsInfoRow(
                                tr("اختبار الاسترجاع", "Recovery test"),
                                tr("قراءة ومقارنة فقط • لا يغيّر أي بيانات", "Read/compare only • changes no data")
                            )
                            recoveryMessage?.let { message ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = message,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (recoverySuccess) Color(0xFF15803D) else Color(0xFFB45309)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LabeledIconAction(
                                label = if (recoveryChecking) tr("جاري الاختبار...", "Testing...") else tr("اختبار النسخة الاحتياطية", "Test backup recovery"),
                                onClick = {
                                    recoveryChecking = true
                                    recoveryMessage = null
                                    RecoveryReadOnlyProbe.run { result ->
                                        recoveryChecking = false
                                        recoverySuccess = result.ok && result.status == "healthy"
                                        recoveryMessage = when {
                                            !result.ok -> tr(
                                                "تعذر الوصول للنسخة الاحتياطية الآن؛ Firebase والتطبيق غير متأثرين.",
                                                "Backup is unavailable right now; Firebase and the app are unaffected."
                                            )
                                            result.status == "no_backup_yet" -> tr(
                                                "لا توجد نسخة احتياطية كافية لهذا الحساب حتى الآن.",
                                                "There is not enough backup history for this account yet."
                                            )
                                            result.status == "healthy" -> tr(
                                                "الاختبار ناجح: ${result.exact}/${result.examined} سجلات مطابقة، بدون كتابة أو استرجاع فعلي.",
                                                "Test passed: ${result.exact}/${result.examined} records match, with no write or live recovery."
                                            )
                                            else -> tr(
                                                "الاختبار اكتمل: مطابق ${result.exact}، أحدث من النسخة ${result.stale}، مفقود ${result.missing}، غير متاح للصلاحية ${result.denied}. لم يتم تغيير أي بيانات.",
                                                "Test completed: exact ${result.exact}, newer than backup ${result.stale}, missing ${result.missing}, permission unavailable ${result.denied}. No data was changed."
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !recoveryChecking
                            ) { Icon(Icons.Default.Sync, contentDescription = null) }

                            Spacer(Modifier.height(10.dp))
                            LabeledIconAction(label = if (syncing) tr("جاري التحديث...", "Refreshing...") else tr("تحديث الأسعار الآن", "Refresh prices now"), onClick = {
                                    syncing = true
                                    syncMessage = null
                                    viewModel.refreshPrices { _, message ->
                                        syncing = false
                                        syncMessage = message
                                    }
                                }, modifier = Modifier.fillMaxWidth(), enabled = !syncing) { Icon(Icons.Default.Refresh, contentDescription = null) }
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("الإدارة", "Administration"), icon = Icons.Default.AdminPanelSettings) {
                            LabeledIconAction(label = tr("فتح لوحة تحكم المدير", "Open Manager Dashboard"), onClick = onOpenManagerDashboard, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Visibility, contentDescription = null) }
                        }
                    }

                    item {
                        SettingsSectionCard(title = tr("عن التطبيق", "About App"), icon = Icons.Default.Info) {
                            SettingsInfoRow(tr("الإصدار", "Version"), BuildConfig.VERSION_NAME)
                            SettingsInfoRow(tr("رقم البناء", "Build number"), BuildConfig.VERSION_CODE.toString())
                            SettingsInfoRow(tr("هوية المعمل", "Lab identity"), settings.pdfLabName)
                            Spacer(Modifier.height(8.dp))
                            LabeledIconAction(label = tr("إرجاع الإعدادات للوضع الافتراضي", "Reset settings to default"), onClick = { viewModel.resetAppSettings() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings, contentDescription = null) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color(0xFF006D86), modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF102A43)
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        LabeledIconAction(label = if (checked) "مفعّل" else "غير مفعّل", onClick = { onCheckedChange(!(checked)) }) { Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.Block, contentDescription = null) }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1E293B),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun <T> ChoiceButtons(
    choices: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        choices.forEach { (value, label) ->
            val selectedNow = value == selected
            LabeledIconAction(label = label, onClick = { onSelected(value) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
        }
    }
}

private fun formatLastSync(timestamp: Long, language: String): String {
    val en = language == "en"
    if (timestamp <= 0L) return if (en) "No manual sync yet" else "لم يتم التحديث يدويًا بعد"
    return runCatching {
        SimpleDateFormat("dd/MM/yyyy - hh:mm a", if (en) Locale.ENGLISH else Locale("ar", "EG")).format(Date(timestamp))
    }.getOrDefault(if (en) "Synced" else "تم التحديث")
}
