package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Biotech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.settings.AppSettings
import com.example.settings.appText
import com.example.settings.tr

@Composable
fun UserSettingsDialog(
    viewModel: LabTestsViewModel,
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    var showPinEditor by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val tr: (String, String) -> String = { ar, en -> appText(settings, ar, en) }

    val handleSystemBack: () -> Unit = {
        if (showPinEditor) {
            showPinEditor = false
            pinError = null
        } else {
            onDismiss()
        }
    }

    BackHandler(enabled = true) { handleSystemBack() }

    Dialog(
        onDismissRequest = handleSystemBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (settings.darkMode) Color(0xFF0F172A) else Color(0xFFF5F7FB)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("إعدادات التطبيق", "App Settings"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.darkMode) Color.White else Color(0xFF102A43))
                        Text(tr("إعداداتك الشخصية على الجهاز", "Your personal settings on this device"), fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = tr("إغلاق", "Close")) }
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SimpleSettingsCard(tr("اللغة", "Language"), Icons.Default.Translate) {
                            Text(tr("لغة التطبيق", "App language"), fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabeledIconAction(label = tr("العربية", "Arabic"), onClick = { viewModel.updateAppSettings(settings.copy(language = "ar")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Language, contentDescription = null) }
                                LabeledIconAction(label = "English", onClick = { viewModel.updateAppSettings(settings.copy(language = "en")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Language, contentDescription = null) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (settings.language == "en") "English interface is active" else "الواجهة العربية مفعلة",
                                fontSize = 11.sp,
                                color = Color(0xFF006D86),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        SimpleSettingsCard(tr("الأمان", "Security"), Icons.Default.Lock) {
                            SettingSwitch(
                                title = tr("قفل التطبيق • مفعل دائما", "App lock • always on"),
                                subtitle = tr("حماية إلزامية لبيانات العملاء والأسعار", "Mandatory protection for customer and pricing data"),
                                checked = true,
                                icon = Icons.Default.Lock,
                                onChecked = { }
                            )
                            if (settings.securityEnabled) {
                                Text(tr("طريقة فتح التطبيق", "Unlock method"), fontWeight = FontWeight.Bold, color = Color(0xFF334155), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LabeledIconAction(label = tr("  بصمة / قفل الهاتف", "  Biometrics / device lock"), onClick = {
                                            viewModel.updateAppSettings(settings.copy(unlockMode = "device"))
                                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Fingerprint, contentDescription = null) }
                                    LabeledIconAction(label = tr("  PIN للتطبيق", "  App PIN"), onClick = {
                                            if (settings.appPinConfigured) {
                                                viewModel.updateAppSettings(settings.copy(unlockMode = "pin", securityEnabled = true))
                                            } else {
                                                pinError = null
                                                showPinEditor = true
                                            }
                                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Lock, contentDescription = null) }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (settings.unlockMode == "pin") tr("المستخدم حاليًا: PIN خاص بالتطبيق", "Current: App PIN") else tr("المستخدم حاليًا: بصمة أو قفل الهاتف", "Current: Biometrics / device lock"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF006D86)
                                )
                                if (settings.unlockMode == "pin") {
                                    Spacer(Modifier.height(8.dp))
                                    LabeledIconAction(label = tr("تغيير الرقم السري", "Change PIN"), onClick = { pinError = null; showPinEditor = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, contentDescription = null) }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(tr("القفل التلقائي", "Auto lock"), fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0 to tr("فورًا", "Now"), 60 to tr("دقيقة", "1 min"), 300 to tr("5 دقائق", "5 min"), 900 to tr("15 دقيقة", "15 min")).forEach { (seconds, label) ->
                                        LabeledIconAction(label = label, onClick = { viewModel.updateAppSettings(settings.copy(autoLockSeconds = seconds)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SimpleSettingsCard(tr("المظهر", "Appearance"), Icons.Default.Palette) {
                            SettingSwitch(tr("الوضع الداكن", "Dark mode"), tr("تغيير شكل التطبيق", "Change app appearance"), settings.darkMode, Icons.Default.DarkMode) {
                                viewModel.updateAppSettings(settings.copy(darkMode = it))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(tr("حجم الخط", "Font size"), fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.9f to tr("صغير", "Small"), 1.0f to tr("متوسط", "Medium"), 1.15f to tr("كبير", "Large")).forEach { (scale, label) ->
                                    LabeledIconAction(label = label, onClick = { viewModel.updateAppSettings(settings.copy(fontScale = scale)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                                }
                            }
                        }
                    }

                    item {
                        SimpleSettingsCard(tr("عرض أسماء التحاليل", "Test name display"), Icons.Outlined.Biotech) {
                            SettingSwitch(tr("الاسم الإنجليزي", "English name"), tr("إظهاره في نتائج البحث", "Show in search results"), settings.showEnglishName, Icons.Default.Language) { enabled ->
                                if (enabled || settings.showArabicName || settings.showMarketName) viewModel.updateAppSettings(settings.copy(showEnglishName = enabled))
                            }
                            SettingSwitch(tr("الاسم العربي", "Arabic name"), tr("إظهاره في نتائج البحث", "Show in search results"), settings.showArabicName, Icons.Default.Translate) { enabled ->
                                if (enabled || settings.showEnglishName || settings.showMarketName) viewModel.updateAppSettings(settings.copy(showArabicName = enabled))
                            }
                            SettingSwitch(tr("الاسم الدارج", "Market name"), tr("إظهار الاسم المتداول عند توفره", "Show common market name when available"), settings.showMarketName, Icons.Default.Label) { enabled ->
                                if (enabled || settings.showEnglishName || settings.showArabicName) viewModel.updateAppSettings(settings.copy(showMarketName = enabled))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinEditor) {
        SetPinDialog(
            onDismiss = { showPinEditor = false; pinError = null },
            error = pinError,
            onSave = { pin, confirm ->
                when {
                    pin.length != 6 -> pinError = "اكتب PIN مكون من 6 أرقام"
                    pin != confirm -> pinError = "الرقمين غير متطابقين"
                    !viewModel.setAppPin(pin) -> pinError = "تعذر حفظ الرقم السري"
                    else -> { showPinEditor = false; pinError = null }
                }
            }
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, error: String?, onSave: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appText("تعيين PIN للتطبيق", "Set App PIN")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text(appText("PIN مكون من 6 أرقام", "6-digit PIN")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(6) },
                    label = { Text(appText("تأكيد PIN", "Confirm PIN")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB91C1C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { LabeledIconAction(label = appText("حفظ", "Save"), onClick = { onSave(pin, confirm) }) { Icon(Icons.Default.Save, contentDescription = null) } },
        dismissButton = { LabeledIconAction(label = appText("إلغاء", "Cancel"), onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) } }
    )
}

@Composable
private fun SimpleSettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color(0xFFE7F5F7), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF006D86), modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Tune,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Color(0xFFE7F5F7), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF006D86), modifier = Modifier.size(21.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        LabeledIconAction(label = if (checked) tr("مفعّل", "On") else tr("غير مفعّل", "Off"), onClick = { onChecked(!checked) }) {
            Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.Block, contentDescription = null, tint = if (checked) Color(0xFF15803D) else Color(0xFF64748B))
        }
    }
}
