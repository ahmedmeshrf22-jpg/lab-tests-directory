package com.example.ui

import androidx.compose.material.icons.filled.*
import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppUserProfile
import com.example.data.model.AuditLogEntry
import com.example.data.model.AuthorizedDevice
import com.example.data.model.CustomerOrder
import com.example.data.model.ReportSummary
import com.example.data.model.permissionsForRole
import com.example.settings.LocalAppSettings
import com.example.settings.appText
import com.example.settings.tr
import com.example.notifications.AutoBackupScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.util.AutoBackupCredentialStore
import com.example.util.AutoBackupStorage
import com.example.notifications.BackupNotificationManager

private val OpsPrimary = Color(0xFF006D86)
private val OpsDark = Color(0xFF17324D)
private val OpsGreen = Color(0xFF15803D)
private val OpsBg = Color(0xFFF4F7FB)

@Composable
fun OperationsSystemCard(isManager: Boolean, onClick: () -> Unit) {
LabeledIconAction(
        label = appText("التقارير والحسابات", "Reports & Accounts"),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        actionSize = 68.dp) { Icon(Icons.Default.Assessment, contentDescription = null, tint = OpsPrimary, modifier = Modifier.size(48.dp)) }
}

@Composable
fun OperationsV14Dialog(
    viewModel: LabTestsViewModel,
    isManager: Boolean,
    initialPage: String = "home",
    initialDebtsOnly: Boolean = false,
    initialRange: String = "today",
    onOpenAdminSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (!isManager) return
    var page by remember(initialPage) { mutableStateOf(initialPage) }

    val handleSystemBack: () -> Unit = {
        if (page != "home") page = "home" else onDismiss()
    }

    BackHandler(enabled = true) { handleSystemBack() }

    Dialog(
        onDismissRequest = handleSystemBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = OpsBg) {
            Column(Modifier.fillMaxSize()) {
                OperationsHeader(
                    title = when (page) {
                        "reports" -> appText("التقارير والحسابات", "Reports & Accounts")
                        "audit" -> appText("سجل المراجعة", "Audit Log")
                        "users" -> appText("حسابات الاستاف", "Staff Accounts")
                        "devices" -> appText("الأجهزة المصرح بها", "Authorized Devices")
                        "health" -> appText("حالة النظام", "System Health")
                        "alerts" -> appText("مركز التنبيهات", "Alert Center")
                        "integrity" -> appText("سلامة البيانات", "Data Integrity")
                        "backup" -> appText("النسخ الاحتياطي والاسترجاع", "Backup & Restore")
                        "demo" -> appText("وضع العرض التجاري", "Commercial Demo")
                        else -> appText("نظام الإدارة", "Administration")
                    },
                    subtitle = when (page) {
                        "reports" -> appText("مبيعات • مدفوع • مديونيات • إحصائيات", "Sales • Paid • Debts • Statistics")
                        "audit" -> appText("سجل كامل للعمليات والمزامنة", "Full operations and sync history")
                        "users" -> appText("إضافة حساب • بحث • تفعيل وإيقاف • صلاحيات", "Add • Search • Enable/Disable • Permissions")
                        "devices" -> appText("اعتماد أو رفض أجهزة المستخدمين", "Approve or reject user devices")
                        "health" -> appText("الاتصال • Firebase • المزامنة • الأداء", "Connectivity • Firebase • Sync • Performance")
                        "alerts" -> appText("جديد • مهم • مزامنة • أجهزة • نشاط", "New • Important • Sync • Devices • Activity")
                        "integrity" -> appText("تكرار • تعارض • حسابات • إصلاح آمن", "Duplicates • Conflicts • Totals • Safe repair")
                        "backup" -> appText("نسخة مشفرة • حفظ على الهاتف • استرجاع آمن", "Encrypted backup • Local save • Safe restore")
                        "demo" -> appText("عرض احترافي بدون لمس بيانات التشغيل", "Professional showcase without touching production data")
                        else -> appText("لوحة تشغيل وإدارة المدير", "Manager administration panel")
                    },
                    onBack = if (page != "home") ({ page = "home" }) else null,
                    onClose = onDismiss
                )

                when (page) {
                    "reports" -> if (isManager) ReportsScreen(
                        viewModel = viewModel,
                        isManager = true,
                        initialDebtsOnly = initialDebtsOnly,
                        initialRange = initialRange
                    ) else AccessDeniedCard()
                    "audit" -> if (isManager) AuditScreen(viewModel) else AccessDeniedCard()
                    "users" -> if (isManager) UsersScreen(viewModel) else AccessDeniedCard()
                    "devices" -> if (isManager) DevicesScreen(viewModel) else AccessDeniedCard()
                    "health" -> if (isManager) SystemHealthScreen(viewModel) else AccessDeniedCard()
                    "alerts" -> if (isManager) SmartAlertsScreen(viewModel) else AccessDeniedCard()
                    "integrity" -> if (isManager) DataIntegrityScreen(viewModel) else AccessDeniedCard()
                    "backup" -> if (isManager) BackupRestoreScreen(viewModel) else AccessDeniedCard()
                    "demo" -> if (isManager) CommercialDemoScreen() else AccessDeniedCard()
                    else -> OperationsHome(
                        viewModel = viewModel,
                        isManager = isManager,
                        onOpen = { page = it },
                        onOpenAdminSettings = onOpenAdminSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationsHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(OpsDark).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            LabeledIconAction(label = appText(tr("رجوع", "Back"), "Back"), onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = appText(tr("رجوع", "Back"), "Back"), tint = Color.White) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
        }
        LabeledIconAction(label = appText(tr("إغلاق", "Close"), "Close"), onClick = onClose) { Icon(Icons.Default.Close, contentDescription = appText(tr("إغلاق", "Close"), "Close"), tint = Color.White) }
    }
}

@Composable
private fun OperationsHome(
    viewModel: LabTestsViewModel,
    isManager: Boolean,
    onOpen: (String) -> Unit,
    onOpenAdminSettings: () -> Unit
) {
    val unreadAlerts by viewModel.unreadAdminAlertCount.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshAdminAlerts() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        if (isManager) {
            item {
                Text(
                    appText("كل خدمات الإدارة", "All administration services"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = OpsDark
                )
                Text(
                    appText("كل خدمة بأيقونة مستقلة — بدون قوائم نصية طويلة", "Every service has its own icon"),
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_reports_3d, appText("التقارير", "Reports"), appText("مبيعات • مديونيات • أرباح", "Sales • Debts • Profit")) { onOpen("reports") }
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_staff_3d, appText("حسابات الاستاف", "Staff Accounts"), appText("إضافة • صلاحيات • PIN", "Add • Permissions • PIN")) { onOpen("users") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_devices_3d, appText("الأجهزة", "Devices"), appText("اعتماد • رفض • إلغاء", "Approve • Reject • Revoke")) { onOpen("devices") }
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_alerts_3d, if (unreadAlerts > 0) appText("التنبيهات ($unreadAlerts)", "Alerts ($unreadAlerts)") else appText("التنبيهات", "Alerts"), appText("مزامنة • أجهزة • نشاط", "Sync • Devices • Activity")) { onOpen("alerts") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_integrity_3d, appText("سلامة البيانات", "Data Integrity"), appText("تكرار • تعارض • إصلاح", "Duplicates • Conflicts • Repair")) { onOpen("integrity") }
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_health_3d, appText("حالة النظام", "System Health"), appText("Firebase • مزامنة • أداء", "Firebase • Sync • Performance")) { onOpen("health") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_integrity_3d, appText("نسخة احتياطية", "Backup"), appText("تصدير مشفر • استرجاع", "Encrypted export • Restore")) { onOpen("backup") }
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_reports_3d, appText("وضع العرض", "Demo Mode"), appText("عرض تجاري آمن", "Safe commercial showcase")) { onOpen("demo") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpsMenuCard(Modifier.weight(1f), R.drawable.mgr_audit_3d, appText("سجل العمليات", "Audit Log"), appText("كل العمليات الحساسة", "Sensitive activity history")) { onOpen("audit") }
                    OpsMenuCard(
                        Modifier.weight(1f),
                        R.drawable.settings_3d,
                        appText("إعدادات الإدارة", "Admin settings"),
                        appText("أسعار • صلاحيات • إعدادات المدير", "Prices • permissions • manager settings")
                    ) { onOpenAdminSettings() }
                }
            }
        }
    }
}

@Composable
private fun OpsMenuCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
LabeledIconAction(label = title, onClick = onClick, modifier = modifier,
        actionSize = 72.dp) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SmartAlertsScreen(viewModel: LabTestsViewModel) {
    val alerts by viewModel.adminAlerts.collectAsState()
    val unreadCount by viewModel.unreadAdminAlertCount.collectAsState()
    var filter by remember { mutableStateOf("new") }

    LaunchedEffect(Unit) { viewModel.refreshAdminAlerts() }

    val filtered = remember(alerts, filter) {
        when (filter) {
            "new" -> alerts.filter { !it.isRead }
            "important" -> alerts.filter { it.severity == "critical" || it.severity == "warning" }
            else -> alerts
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Color(0xFFFEE2E2), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFB91C1C))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(appText("مركز تنبيهات الإدارة", "Admin Alert Center"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                        Text(
                            appText("$unreadCount تنبيه جديد", "$unreadCount new alert(s)"),
                            fontSize = 11.sp,
                            color = if (unreadCount > 0) Color(0xFFB91C1C) else OpsGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LabeledIconAction(label = appText("تحديث", "Refresh"), onClick = { viewModel.refreshAdminAlerts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = appText("تحديث", "Refresh"), tint = OpsPrimary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertFilterButton(appText("جديد", "New"), filter == "new", Modifier.weight(1f)) { filter = "new" }
                    AlertFilterButton(appText("مهم", "Important"), filter == "important", Modifier.weight(1f)) { filter = "important" }
                    AlertFilterButton(appText("الكل", "All"), filter == "all", Modifier.weight(1f)) { filter = "all" }
                }
                if (unreadCount > 0) {
                    LabeledIconAction(label = appText("تحديد الكل كمقروء", "Mark all as read"), onClick = { viewModel.markAllAdminAlertsRead() }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.DoneAll, contentDescription = null) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (filtered.isEmpty()) {
                item { OpsMessage(appText("لا توجد تنبيهات في هذا القسم", "No alerts in this section"), true) }
            } else {
                items(filtered, key = { it.id }) { alert ->
                    AdminAlertRow(alert) { viewModel.markAdminAlertRead(alert.id) }
                }
            }
        }
    }
}

@Composable
private fun AlertFilterButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        LabeledIconAction(label = text, onClick = onClick, modifier = modifier) { Icon(Icons.Default.TouchApp, contentDescription = null) }
    } else {
        LabeledIconAction(label = text, onClick = onClick, modifier = modifier) { Icon(Icons.Default.TouchApp, contentDescription = null) }
    }
}

@Composable
private fun AdminAlertRow(alert: AdminAlert, onMarkRead: () -> Unit) {
    val accent = when (alert.severity) {
        "critical" -> Color(0xFFB91C1C)
        "warning" -> Color(0xFFB45309)
        else -> Color(0xFF2563EB)
    }
    val bg = if (alert.isRead) Color.White else accent.copy(alpha = 0.07f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        if (alert.severity == "critical") Icons.Default.Warning else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = accent
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(appText(alert.titleAr, alert.titleEn), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    Text(appText(alert.detailsAr, alert.detailsEn), fontSize = 11.sp, color = Color(0xFF475569), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (alert.actorEmail.isNotBlank()) {
                        Text(alert.actorEmail, fontSize = 9.sp, color = OpsPrimary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(opsDate(alert.createdAtMillis), fontSize = 9.sp, color = Color(0xFF64748B))
                    if (!alert.isRead) {
                        LabeledIconAction(label = appText("مقروء", "Read"), onClick = onMarkRead) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (alert.severity) {
                    "critical" -> appText("مهم", "Important")
                    "warning" -> appText("تنبيه", "Warning")
                    else -> appText("معلومة", "Info")
                },
                fontSize = 9.sp,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DataIntegrityScreen(viewModel: LabTestsViewModel) {
    val state by viewModel.dataIntegrityState.collectAsState()
    val issues by viewModel.dataIntegrityIssues.collectAsState()
    var resultMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshDataIntegrity() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.criticalCount > 0) Color(0xFFFFF1F2) else Color(0xFFECFDF5)
                )
            ) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.criticalCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.criticalCount > 0) Color(0xFFB91C1C) else OpsGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.checking) appText("جار فحص البيانات...", "Scanning data...")
                                else if (state.issueCount == 0 && state.checkedAtMillis > 0L) appText("البيانات المفحوصة سليمة", "Scanned data looks healthy")
                                else appText("مراجعة سلامة البيانات", "Data integrity review"),
                                fontWeight = FontWeight.ExtraBold,
                                color = OpsDark,
                                fontSize = 17.sp
                            )
                            Text(state.message, fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        LabeledIconAction(label = appText("تحديث", "Refresh"), onClick = { viewModel.refreshDataIntegrity() }, enabled = !state.checking) {
                            if (state.checking) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = appText("تحديث", "Refresh"), tint = OpsPrimary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntegrityStat(appText("العملاء", "Customers"), state.customersScanned.toString(), Modifier.weight(1f))
                        IntegrityStat(appText("الطلبات", "Orders"), state.ordersScanned.toString(), Modifier.weight(1f))
                        IntegrityStat(appText("الملاحظات", "Findings"), state.issueCount.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        if (resultMessage.isNotBlank()) {
            item { OpsMessage(resultMessage, !resultMessage.contains("تعذر") && !resultMessage.contains("Could not")) }
        }

        if (!state.checking && issues.isEmpty() && state.checkedAtMillis > 0L) {
            item { OpsMessage(appText("لم يتم اكتشاف تكرار أو خلل حسابي أو تعارض واضح في البيانات المفحوصة.", "No duplicate, arithmetic, or obvious conflict issue was found in the scanned data."), true) }
        }

        items(issues, key = { it.id }) { issue ->
            val critical = issue.severity == "critical"
            val accent = if (critical) Color(0xFFB91C1C) else Color(0xFFB45309)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.07f))
            ) {
                Column(Modifier.padding(13.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(if (critical) Icons.Default.Warning else Icons.Default.Assessment, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(appText(issue.titleAr, issue.titleEn), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                            Text(appText(issue.detailsAr, issue.detailsEn), fontSize = 11.sp, color = Color(0xFF475569))
                        }
                    }
                    if (issue.type == "financial_mismatch" || issue.type == "payment_status") {
                        Spacer(Modifier.height(8.dp))
                        LabeledIconAction(label = appText("إصلاح آمن بدون حذف بيانات", "Safe repair without deleting data"), onClick = {
                                viewModel.repairIntegrityIssue(issue) { _, message -> resultMessage = message }
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, contentDescription = null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrityStat(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = OpsPrimary, fontSize = 16.sp)
            Text(title, fontSize = 9.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun SystemHealthScreen(viewModel: LabTestsViewModel) {
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingCount by viewModel.pendingSyncCount.collectAsState()
    val lastSync by viewModel.lastSuccessfulSyncMillis.collectAsState()
    val health by viewModel.systemHealth.collectAsState()

    LaunchedEffect(isOnline) { viewModel.refreshSystemHealth() }

    val overallGood = isOnline && health.firebaseReachable == true && pendingCount == 0
    val overallColor = when {
        overallGood -> OpsGreen
        !isOnline || health.firebaseReachable == false -> Color(0xFFB91C1C)
        else -> Color(0xFFB45309)
    }
    val overallText = when {
        overallGood -> appText("النظام يعمل بشكل جيد", "System is healthy")
        !isOnline -> appText("الجهاز غير متصل بالإنترنت", "Device is offline")
        health.checking -> appText("جار فحص النظام...", "Checking system...")
        health.firebaseReachable == false -> appText("يوجد مشكلة في الوصول إلى Firebase", "Firebase is currently unreachable")
        pendingCount > 0 -> appText("توجد عمليات في انتظار المزامنة", "Some operations are waiting to sync")
        else -> appText("حالة النظام غير مكتملة", "System status is incomplete")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = overallColor.copy(alpha = 0.10f))
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (overallGood) Icons.Default.CheckCircle else Icons.Default.Assessment,
                        contentDescription = null,
                        tint = overallColor,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(overallText, fontWeight = FontWeight.ExtraBold, color = overallColor, fontSize = 17.sp)
                        if (health.message.isNotBlank()) {
                            Text(health.message, fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                    LabeledIconAction(label = appText("تحديث", "Refresh"), onClick = { viewModel.refreshSystemHealth() }, enabled = !health.checking) {
                        if (health.checking) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = appText("تحديث", "Refresh"), tint = OpsPrimary)
                        }
                    }
                }
            }
        }

        item {
            SystemHealthMetricCard(
                icon = Icons.Default.Wifi,
                title = appText("اتصال الإنترنت", "Internet Connection"),
                value = if (isOnline) appText("متصل", "Online") else appText("غير متصل", "Offline"),
                good = isOnline
            )
        }
        item {
            SystemHealthMetricCard(
                icon = if (health.firebaseReachable == false) Icons.Default.CloudOff else Icons.Default.CloudDone,
                title = "Firebase / Firestore",
                value = when {
                    health.checking -> appText("جار الفحص", "Checking")
                    health.firebaseReachable == true -> appText("متصل", "Reachable")
                    health.firebaseReachable == false -> appText("غير متاح حاليا", "Unreachable")
                    else -> appText("لم يتم الفحص بعد", "Not checked yet")
                },
                good = health.firebaseReachable == true
            )
        }
        item {
            val latency = health.latencyMs
            SystemHealthMetricCard(
                icon = Icons.Default.Speed,
                title = appText("زمن استجابة Firebase", "Firebase Latency"),
                value = if (latency == null) "—" else "$latency ms",
                good = latency != null && latency < 1000L
            )
        }
        item {
            SystemHealthMetricCard(
                icon = Icons.Default.Sync,
                title = appText("عمليات في انتظار المزامنة", "Pending Sync Operations"),
                value = pendingCount.toString(),
                good = pendingCount == 0
            )
        }
        item {
            val lastSyncText = if (lastSync <= 0L) {
                appText("لا يوجد تسجيل بعد", "No sync recorded yet")
            } else {
                SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault()).format(Date(lastSync))
            }
            SystemHealthMetricCard(
                icon = Icons.Default.History,
                title = appText("آخر مزامنة مكتملة", "Last Completed Sync"),
                value = lastSyncText,
                good = lastSync > 0L
            )
        }
        item {
            SystemHealthMetricCard(
                icon = Icons.Default.Storage,
                title = appText("الـ Cache المحلي", "Local Cache"),
                value = "300 MB",
                good = true
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(appText("جاهزية التشغيل التجاري", "Commercial Readiness"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    Text(
                        appText(
                            "V${BuildConfig.VERSION_CODE} • Backup مشفر • Crash Monitoring • صلاحيات وأجهزة • Audit Log • White-label • Offline/Sync",
                            "V${BuildConfig.VERSION_CODE} • Encrypted backup • Crash monitoring • Roles/devices • Audit log • White-label • Offline/Sync"
                        ),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
        item {
            Text(
                appText("ملاحظة: الفحص يقيس اتصال هذا الجهاز ووصوله إلى Firebase في اللحظة الحالية، وليس لوحة مراقبة عالمية للبنية التحتية لـ Firebase.", "Note: this checks the current device connection and Firebase reachability; it is not a global Firebase infrastructure monitoring console."),
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun SystemHealthMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    good: Boolean
) {
    val accent = if (good) OpsGreen else Color(0xFFB45309)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, color = Color(0xFF64748B))
                Text(value, fontWeight = FontWeight.ExtraBold, color = OpsDark, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ReportsScreen(
    viewModel: LabTestsViewModel,
    isManager: Boolean,
    initialDebtsOnly: Boolean = false,
    initialRange: String = "today"
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    val orders by viewModel.reportOrders.collectAsState()
    val loading by viewModel.reportsLoading.collectAsState()
    var range by remember(initialRange) { mutableStateOf(initialRange) }
    var query by remember { mutableStateOf("") }
    var debtsOnly by remember(initialDebtsOnly) { mutableStateOf(initialDebtsOnly) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var selectedFromMillis by remember { mutableStateOf(startOfToday()) }
    var selectedToMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun setDisplayedPeriod(from: Long, to: Long) {
        selectedFromMillis = from
        selectedToMillis = to
        fromText = if (from > 0L) formatDateField(from) else ""
        toText = if (to > 0L) formatDateField(to) else ""
    }

    fun loadPreset(value: String) {
        range = value
        val now = System.currentTimeMillis()
        val from = when (value) {
            "week" -> startOfDaysAgo(6)
            "month" -> startOfDaysAgo(29)
            "all" -> 0L
            else -> startOfToday()
        }
        setDisplayedPeriod(from, now)
        viewModel.loadReports(from, now) { ok, msg -> if (!ok) message = msg }
    }

    fun showDatePicker(currentText: String, onSelected: (String) -> Unit) {
        val initialMillis = parseDateStart(currentText) ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSelected(formatDateField(picked.timeInMillis))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LaunchedEffect(initialRange) { loadPreset(initialRange) }

    val filtered = remember(orders, query, debtsOnly) {
        val normalized = query.trim().lowercase()
        orders.filter { order ->
            val matchesDebt = !debtsOnly || order.remainingAmount > 0.0
            val matchesQuery = normalized.isBlank() ||
                order.customerName.lowercase().contains(normalized) ||
                order.customerFileNumber.lowercase().contains(normalized) ||
                order.orderNumber.lowercase().contains(normalized) ||
                order.createdByEmail.lowercase().contains(normalized) ||
                paymentStatusLabel(order.paymentStatus).contains(query.trim())
            matchesDebt && matchesQuery
        }
    }
    val summary = viewModel.calculateReportSummary(filtered)
    val topTests = remember(filtered) {
        filtered.flatMap { it.items }
            .groupingBy { it.englishName.ifBlank { it.arabicName }.ifBlank { it.marketName } }
            .eachCount()
            .entries.sortedByDescending { it.value }.take(5)
    }

    val periodLabel = when {
        selectedFromMillis <= 0L -> "كل الفترات حتى ${formatDateDisplay(selectedToMillis)}"
        else -> "من ${formatDateDisplay(selectedFromMillis)} إلى ${formatDateDisplay(selectedToMillis)}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        message?.let { item { OpsMessage(it, false) } }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("today" to tr("اليوم", "Today"), "week" to tr("7 أيام", "7 Days"), "month" to tr("30 يوم", "30 Days"), "all" to tr("الكل", "All")).forEach { (value, label) ->
                    LabeledIconAction(label = label, onClick = { loadPreset(value) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(tr("فترة مخصصة", "Custom Range"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ReportDateField(
                            label = tr("من", "From"),
                            value = fromText,
                            modifier = Modifier.weight(1f),
                            onClick = { showDatePicker(fromText) { fromText = it } }
                        )
                        ReportDateField(
                            label = tr("إلى", "To"),
                            value = toText,
                            modifier = Modifier.weight(1f),
                            onClick = { showDatePicker(toText) { toText = it } }
                        )
                    }
                    LabeledIconAction(label = tr("عرض التقرير للفترة المحددة", "Show Report for Selected Range"), onClick = {
                            val from = parseDateStart(fromText)
                            val to = parseDateEnd(toText)
                            if (from == null || to == null || from > to) {
                                message = tr("حدد تاريخ البداية والنهاية من التقويم", "Select start and end dates from the calendar")
                            } else {
                                range = "custom"
                                message = null
                                setDisplayedPeriod(from, to)
                                viewModel.loadReports(from, to) { ok, msg -> if (!ok) message = msg }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Visibility, contentDescription = null) }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F5F7))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(appText("فترة التقرير", "Report period"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    Text(periodLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OpsPrimary)
                    Text(
                        appText("التقرير يظهر داخل التطبيق فقط. إخراج الطلبات أصبح صور فقط.", "The report stays inside the app. Order output is image-only."),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                label = { Text(tr("بحث: عميل / طلب / مستخدم / حالة دفع", "Search: customer / order / user / payment status")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricCard(tr("الطلبات", "Orders"), summary.ordersCount.toString(), Icons.Default.Assessment, Color(0xFF0369A1), Modifier.weight(1f))
                ReportMetricCard(tr("العملاء", "Customers"), summary.customersCount.toString(), Icons.Default.People, Color(0xFF7C3AED), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricCard(tr("المبيعات", "Sales"), "${opsMoney(summary.sales)} ج", Icons.Default.TrendingUp, OpsGreen, Modifier.weight(1f))
                ReportMetricCard(tr("الخصومات", "Discounts"), "${opsMoney(summary.discounts)} ج", Icons.Default.Assessment, Color(0xFFB45309), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricCard(tr("المدفوع", "Paid"), "${opsMoney(summary.paid)} ج", Icons.Default.Payments, OpsGreen, Modifier.weight(1f))
                ReportMetricCard(tr("المتبقي", "Remaining"), "${opsMoney(summary.remaining)} ج", Icons.Default.AccountBalanceWallet, Color(0xFFB91C1C), Modifier.weight(1f))
            }
        }
        if (isManager) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    ReportMetricCard(tr("ربح تقديري*", "Estimated Profit*"), "${opsMoney(summary.estimatedProfit)} ج", Icons.Default.TrendingUp, Color(0xFF0F766E), Modifier.weight(1f))
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("المديونيات", "Debts"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                LabeledIconAction(label = if (debtsOnly) tr("عرض الكل", "Show All") else tr("المتبقي فقط", "Outstanding Only"), onClick = { debtsOnly = !debtsOnly }) { Icon(Icons.Default.Visibility, contentDescription = null) }
            }
        }

        if (topTests.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(13.dp)) {
                        Text(tr("أكثر التحاليل طلبًا", "Most Requested Tests"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                        Spacer(Modifier.height(6.dp))
                        topTests.forEachIndexed { index, entry ->
                            Text("${index + 1}. ${entry.key} — ${entry.value}", fontSize = 12.sp, color = Color(0xFF475569))
                        }
                    }
                }
            }
        }

        when {
            loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            filtered.isEmpty() -> item { OpsMessage(tr("لا توجد طلبات في الفترة/الفلاتر الحالية", "No orders for the current period/filters"), true) }
            else -> items(filtered, key = { "${it.customerId}_${it.id}" }) { order -> ReportOrderRow(order) }
        }
    }
}

@Composable
private fun ReportDateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LabeledIconAction(label = label, onClick = onClick, modifier = modifier.height(58.dp)) { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
}

@Composable
private fun ReportMetricCard(title: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(5.dp))
            Text(title, fontSize = 10.sp, color = Color(0xFF64748B))
            Text(value, fontWeight = FontWeight.ExtraBold, color = accent, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ReportOrderRow(order: CustomerOrder) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(order.customerName.ifBlank { order.customerFileNumber }, fontWeight = FontWeight.ExtraBold, color = OpsDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(order.orderNumber, fontSize = 10.sp, color = OpsPrimary)
                    Text(opsDate(order.createdAtMillis), fontSize = 10.sp, color = Color(0xFF64748B))
                    if (order.createdByEmail.isNotBlank()) Text(order.createdByEmail, fontSize = 10.sp, color = Color(0xFF64748B))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${opsMoney(order.totalCustomerPrice)} ${tr("ج", "EGP")}", fontWeight = FontWeight.ExtraBold, color = OpsGreen)
                    Text(paymentStatusLabel(order.paymentStatus), fontSize = 10.sp, color = if (order.remainingAmount > 0) Color(0xFFB91C1C) else OpsGreen)
                }
            }
            if (order.remainingAmount > 0.0) {
                Spacer(Modifier.height(5.dp))
                Text(tr("متبقي ${opsMoney(order.remainingAmount)} ج", "Outstanding ${opsMoney(order.remainingAmount)} EGP"), color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AuditScreen(viewModel: LabTestsViewModel) {
    val logs by viewModel.auditLogs.collectAsState()
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.loadAuditLogs { ok, msg -> if (!ok) message = msg } }

    val filtered = remember(logs, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) logs else logs.filter {
            listOf(it.title, it.details, it.actorEmail, it.action, it.entityType).joinToString(" ").lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                label = { Text(appText("بحث في السجل", "Search audit log")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            LabeledIconAction(label = appText("تحديث", "Refresh"), onClick = { viewModel.loadAuditLogs() }) { Icon(Icons.Default.Refresh, contentDescription = appText("تحديث", "Refresh")) }
        }
        message?.let { OpsMessage(it, false) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            if (filtered.isEmpty()) item { OpsMessage(appText("لا توجد سجلات", "No audit records"), true) }
            else items(filtered, key = { it.id }) { AuditRow(it) }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditLogEntry) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.title, fontWeight = FontWeight.ExtraBold, color = OpsDark, modifier = Modifier.weight(1f))
                Text(opsDate(entry.createdAtMillis), fontSize = 9.sp, color = Color(0xFF64748B))
            }
            if (entry.details.isNotBlank()) Text(entry.details, fontSize = 11.sp, color = Color(0xFF475569), maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (entry.actorEmail.isNotBlank()) Text(appText("بواسطة: ${entry.actorEmail}", "By: ${entry.actorEmail}"), fontSize = 10.sp, color = OpsPrimary)
            if (entry.wasOffline) {
                Text(
                    appText(
                        "تمت العملية بدون إنترنت ثم وصلت للسيرفر عند رجوع الشبكة",
                        "Performed offline and delivered to the server after reconnect"
                    ),
                    fontSize = 10.sp,
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.Bold
                )
                if (entry.syncedAtMillis > 0L) {
                    Text(
                        appText("وقت المزامنة: ${opsDate(entry.syncedAtMillis)}", "Synced: ${opsDate(entry.syncedAtMillis)}"),
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            Text("${entry.entityType} • ${entry.action}", fontSize = 9.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun UsersScreen(viewModel: LabTestsViewModel) {
    val users by viewModel.users.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppUserProfile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") } // all | active | disabled

    LaunchedEffect(Unit) { viewModel.loadUsers { ok, msg -> success = ok; if (!ok) message = msg } }

    val normalizedQuery = query.trim().lowercase()
    val filteredUsers = users.filter { profile ->
        val matchesStatus = when (statusFilter) {
            "active" -> profile.enabled
            "disabled" -> !profile.enabled
            else -> true
        }
        val searchable = listOf(
            profile.displayName,
            profile.email,
            profile.role,
            roleArabic(profile.role)
        ).joinToString(" ").lowercase()
        matchesStatus && (normalizedQuery.isBlank() || searchable.contains(normalizedQuery))
    }.sortedWith(
        compareByDescending<AppUserProfile> { it.role == "super_admin" }
            .thenByDescending { it.enabled }
            .thenBy { it.displayName.ifBlank { it.email }.lowercase() }
    )

    val activeCount = users.count { it.enabled }
    val disabledCount = users.size - activeCount

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("حسابات الاستاف", "Staff Accounts"), fontWeight = FontWeight.ExtraBold, color = OpsDark, fontSize = 20.sp)
                Text(
                    tr("كل الحسابات والصلاحيات في مكان واحد", "Accounts and permissions in one simple place"),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
            LabeledIconAction(label = tr("تحديث", "Refresh"), onClick = {
                viewModel.loadUsers { ok, msg ->
                    success = ok
                    message = if (ok) null else msg
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = tr("تحديث", "Refresh"), tint = OpsPrimary)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StaffSummaryCard(
                modifier = Modifier.weight(1f),
                value = users.size.toString(),
                label = tr("كل الحسابات", "All"),
                selected = statusFilter == "all",
                onClick = { statusFilter = "all" }
            )
            StaffSummaryCard(
                modifier = Modifier.weight(1f),
                value = activeCount.toString(),
                label = tr("نشط", "Active"),
                selected = statusFilter == "active",
                onClick = { statusFilter = "active" }
            )
            StaffSummaryCard(
                modifier = Modifier.weight(1f),
                value = disabledCount.toString(),
                label = tr("موقوف", "Disabled"),
                selected = statusFilter == "disabled",
                onClick = { statusFilter = "disabled" }
            )
        }

        Spacer(Modifier.height(9.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            label = { Text(tr("ابحث بالاسم أو الإيميل أو الدور", "Search name, email or role")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        LabeledIconAction(label = tr("إضافة حساب استاف جديد", "Add Staff Account"), onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd, contentDescription = null) }

        message?.let {
            Spacer(Modifier.height(8.dp))
            OpsMessage(it, success)
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (filteredUsers.isEmpty()) {
                item {
                    OpsMessage(
                        if (query.isBlank()) tr("لا توجد حسابات في هذا القسم", "No accounts in this section")
                        else tr("لا توجد نتيجة مطابقة للبحث", "No matching account found"),
                        true
                    )
                }
            } else {
                items(filteredUsers, key = { it.uid }) { profile ->
                    StaffUserCard(profile = profile, onOpen = { editing = profile })
                }
            }
        }
    }

    if (showAdd) {
        AddUserDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, email, password, role, done ->
                viewModel.createUserAccount(name, email, password, role) { ok, msg ->
                    done()
                    success = ok
                    message = msg
                    if (ok) showAdd = false
                }
            }
        )
    }

    editing?.let { profile ->
        UserAccessDialog(
            profile = profile,
            onDismiss = { editing = null },
            onSave = { enabled, role, editCustomers, discount, collect, reports, done ->
                viewModel.updateUserAccess(profile, enabled, role, editCustomers, discount, collect, reports) { ok, msg ->
                    done()
                    success = ok
                    message = msg
                    if (ok) editing = null
                }
            },
            onResetPin = {
                viewModel.requestUserPinReset(profile) { ok, msg ->
                    success = ok
                    message = msg
                }
            },
            onResetPassword = { newPassword ->
                viewModel.resetUserLoginCredentials(profile, newPassword) { ok, msg ->
                    success = ok
                    message = msg
                    if (ok) editing = null
                }
            }
        )
    }
}

@Composable
private fun StaffSummaryCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
LabeledIconAction(
        label = "$label • $value",
        onClick = onClick,
        modifier = modifier,
        actionSize = 62.dp) { Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Tune, contentDescription = null, tint = OpsPrimary, modifier = Modifier.size(42.dp)) }
}

@Composable
private fun StaffUserCard(profile: AppUserProfile, onOpen: () -> Unit) {
    val isSuper = profile.role == "super_admin"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (profile.enabled) Color.White else Color(0xFFF1F5F9))
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(if (profile.enabled) Color(0xFFE7F5F7) else Color(0xFFE2E8F0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (profile.enabled) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        tint = if (profile.enabled) OpsGreen else Color(0xFF64748B)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName.ifBlank { profile.email }, fontWeight = FontWeight.ExtraBold, color = OpsDark, fontSize = 15.sp)
                    Text(profile.email, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (profile.enabled) Color(0xFFE8F7ED) else Color(0xFFFFEEEE)
                ) {
                    Text(
                        if (profile.enabled) tr("نشط", "Active") else tr("موقوف", "Disabled"),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (profile.enabled) OpsGreen else Color(0xFFB91C1C)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isSuper) tr("الحساب الرئيسي", "Main Account") else roleArabic(profile.role),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OpsPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (!isSuper) {
                    Text(
                        tr("نفس خدمات التشغيل اليومية", "Same daily services"),
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LabeledIconAction(label = if (isSuper) tr("عرض الحساب", "View Account") else tr("إدارة الحساب", "Manage Account"), onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Visibility, contentDescription = null) }
        
            LabeledIconAction(label = "فتح", onClick = onOpen) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
    }
}

@Composable
private fun DevicesScreen(viewModel: LabTestsViewModel) {
    val devices by viewModel.authorizedDevices.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadAuthorizedDevices { ok, msg ->
            success = ok
            if (!ok) message = msg
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("تصريح الأجهزة", "Device Authorization"), fontWeight = FontWeight.ExtraBold, color = OpsDark, fontSize = 18.sp)
                Text(tr("كل مستخدم له جهاز واحد معتمد في نفس الوقت", "Each user can have one approved device at a time"), fontSize = 11.sp, color = Color(0xFF64748B))
            }
            LabeledIconAction(label = appText("تحديث", "Refresh"), onClick = {
                viewModel.loadAuthorizedDevices { ok, msg ->
                    success = ok
                    message = if (ok) null else msg
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = appText("تحديث", "Refresh"), tint = OpsPrimary)
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            OpsMessage(it, success)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (devices.isEmpty()) {
                item { OpsMessage(tr("لا توجد طلبات أجهزة حتى الآن", "No device requests yet"), true) }
            } else {
                items(devices, key = { "${it.uid}_${it.id}" }) { device ->
                    DeviceAccessCard(
                        device = device,
                        onApprove = {
                            viewModel.approveDevice(device) { ok, msg ->
                                success = ok
                                message = msg
                            }
                        },
                        onReject = {
                            viewModel.setDeviceStatus(device, "rejected") { ok, msg ->
                                success = ok
                                message = msg
                            }
                        },
                        onRevoke = {
                            viewModel.setDeviceStatus(device, "revoked") { ok, msg ->
                                success = ok
                                message = msg
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceAccessCard(
    device: AuthorizedDevice,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRevoke: () -> Unit
) {
    val statusText = when (device.status) {
        "approved" -> tr("معتمد", "Approved")
        "rejected" -> tr("مرفوض", "Rejected")
        "revoked" -> tr("تم إلغاء الاعتماد", "Authorization Revoked")
        else -> tr("بانتظار الموافقة", "Pending Approval")
    }
    val statusColor = when (device.status) {
        "approved" -> OpsGreen
        "pending" -> Color(0xFFB45309)
        else -> Color(0xFFB91C1C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(Color(0xFFE7F5F7), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = OpsPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.email.ifBlank { device.uid }, fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    Text(
                        "${device.manufacturer} ${device.model}".trim().ifBlank { tr("جهاز Android", "Android Device") },
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                }
            }

            when (device.status) {
                "approved" -> {
                    LabeledIconAction(label = tr("إلغاء اعتماد الجهاز", "Revoke Device"), onClick = onRevoke, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Close, contentDescription = null) }
                }
                "pending" -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledIconAction(label = tr("اعتماد", "Approve"), onClick = onApprove, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                        LabeledIconAction(label = tr("رفض", "Reject"), onClick = onReject, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                    }
                }
                else -> {
                    LabeledIconAction(label = tr("اعتماد هذا الجهاز", "Approve This Device"), onClick = onApprove, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, email: String, password: String, role: String, done: () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf("staff") }
    var saving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("إضافة حساب استاف", "Add Staff Account"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = OpsDark)
                        Text(tr("اكتب البيانات الأساسية وحدد الدور", "Enter basic details and choose a role"), fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, contentDescription = tr("إغلاق", "Close")) }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(tr("اسم الموظف", "Staff Name")) },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text(tr("البريد الإلكتروني", "Email")) },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    value = password,
                    onValueChange = {
                        password = it.take(50)
                        passwordError = null
                    },
                    label = { Text(tr("كلمة المرور", "Password")) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        LabeledIconAction(label = if (showPassword) tr("إخفاء", "Hide") else tr("إظهار", "Show"), onClick = { showPassword = !showPassword }) { Icon(Icons.Default.Lock, contentDescription = null) }
                    },
                    isError = passwordError != null,
                    supportingText = {
                        Text(passwordError ?: tr("أي حروف أو أرقام أو رموز — 6 خانات على الأقل", "Any characters — minimum 6 characters"))
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it.take(50)
                        passwordError = null
                    },
                    label = { Text(tr("تأكيد كلمة المرور", "Confirm password")) },
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        LabeledIconAction(label = if (showConfirmPassword) tr("إخفاء", "Hide") else tr("إظهار", "Show"), onClick = { showConfirmPassword = !showConfirmPassword }) { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                    },
                    isError = confirmPassword.isNotBlank() && confirmPassword != password,
                    singleLine = true
                )

                Text(tr("الدور داخل النظام", "System Role"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                RoleButtons(role = role, onRole = { role = it })

                LabeledIconAction(label = if (saving) tr("جاري إنشاء الحساب...", "Creating Account...") else tr("إنشاء الحساب", "Create Account"), onClick = {
                        if (!saving) {
                            when {
                                password.length < 6 -> passwordError = tr("كلمة المرور لازم تكون 6 خانات على الأقل", "Password must be at least 6 characters")
                                password != confirmPassword -> passwordError = tr("تأكيد كلمة المرور غير مطابق", "Password confirmation does not match")
                                else -> {
                                    saving = true
                                    onCreate(name, email, password, role) { saving = false }
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !saving) { Icon(Icons.Default.TouchApp, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun UserAccessDialog(
    profile: AppUserProfile,
    onDismiss: () -> Unit,
    onSave: (
        enabled: Boolean,
        role: String,
        canEditCustomers: Boolean,
        canDiscount: Boolean,
        canCollectPayments: Boolean,
        canViewReports: Boolean,
        done: () -> Unit
    ) -> Unit,
    onResetPin: () -> Unit,
    onResetPassword: (String) -> Unit
) {
    var enabled by remember(profile.uid) { mutableStateOf(profile.enabled) }
    var role by remember(profile.uid) { mutableStateOf(profile.role) }
    var canEditCustomers by remember(profile.uid) { mutableStateOf(profile.canEditCustomers) }
    var canDiscount by remember(profile.uid) { mutableStateOf(profile.canDiscount) }
    var canCollectPayments by remember(profile.uid) { mutableStateOf(profile.canCollectPayments) }
    var canViewReports by remember(profile.uid) { mutableStateOf(profile.canViewSalesReports) }
    var saving by remember { mutableStateOf(false) }
    var showPasswordReset by remember(profile.uid) { mutableStateOf(false) }
    var newLoginPassword by remember(profile.uid) { mutableStateOf("") }
    var confirmLoginPassword by remember(profile.uid) { mutableStateOf("") }
    val isSuper = profile.role == "super_admin"

    fun applyRolePreset(nextRole: String) {
        role = nextRole
        val preset = permissionsForRole(nextRole)
        canEditCustomers = preset.canEditCustomers
        canDiscount = preset.canDiscount
        canCollectPayments = preset.canCollectPayments
        canViewReports = preset.canViewSalesReports
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).background(if (enabled) Color(0xFFE7F5F7) else Color(0xFFE2E8F0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.Block, contentDescription = null, tint = if (enabled) OpsGreen else Color(0xFF64748B), modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName.ifBlank { profile.email }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = OpsDark)
                        Text(profile.email, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    LabeledIconAction(label = tr("إغلاق", "Close"), onClick = onDismiss, enabled = !saving, actionSize = 60.dp) { Icon(Icons.Default.Close, contentDescription = tr("إغلاق", "Close"), modifier = Modifier.size(38.dp)) }
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            Text(tr("البريد الإلكتروني / اسم الدخول", "Email / login username"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                            Spacer(Modifier.height(4.dp))
                            Text(profile.email, fontSize = 13.sp, color = OpsPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFFF2FBF5) else Color(0xFFFFF4F4))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("حالة الحساب", "Account Status"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                                    Text(
                                        if (enabled) tr("الحساب نشط ويقدر يدخل النظام", "Active and can access the system")
                                        else tr("الحساب موقوف وممنوع من الدخول", "Disabled and cannot access the system"),
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                LabeledIconAction(label = if (enabled) "مفعّل" else "غير مفعّل", onClick = { if (!isSuper) enabled = !(enabled) }, enabled = !isSuper, actionSize = 60.dp) { Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.Block, contentDescription = null, modifier = Modifier.size(38.dp)) }
                            }
                        }
                    }

                    item {
                        Text(tr("المسمى الوظيفي", "Job title"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                        Spacer(Modifier.height(5.dp))
                        RoleButtons(role = role, onRole = { if (!isSuper) applyRolePreset(it) })
                    }

                    if (!isSuper) {
                        item {
                            Text(tr("صلاحيات التشغيل", "Operational Permissions"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                        }
                        item {
                            PermissionSwitch(
                                tr("إضافة وتعديل العملاء", "Add / edit customers"),
                                canEditCustomers
                            ) { canEditCustomers = it }
                        }
                        item {
                            PermissionSwitch(
                                tr("تطبيق وتعديل الخصومات", "Apply / edit discounts"),
                                canDiscount
                            ) { canDiscount = it }
                        }
                        item {
                            PermissionSwitch(
                                tr("التحصيل وتغيير حالة الدفع", "Collect payments"),
                                canCollectPayments
                            ) { canCollectPayments = it }
                        }
                        item {
                            PermissionSwitch(
                                tr("عرض تقارير المبيعات", "View sales reports"),
                                canViewReports
                            ) { canViewReports = it }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))
                        ) {
                            Text(
                                tr(
                                    "كل مستخدم له صلاحيات محددة، وكل جهاز جديد لازم يتم اعتماده قبل فتح بيانات المعمل.",
                                    "Each user has explicit permissions, and every new device must be approved before lab data can open."
                                ),
                                modifier = Modifier.padding(10.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpsPrimary
                            )
                        }
                    }
                    if (!isSuper) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                                LabeledIconAction(label = tr("إعادة ضبط PIN التطبيق", "Reset app PIN"), onClick = onResetPin, modifier = Modifier.weight(1f), actionSize = 60.dp) { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(38.dp)) }
                                LabeledIconAction(label = tr("إعادة تعيين كلمة المرور", "Reset password"), onClick = { showPasswordReset = true }, modifier = Modifier.weight(1f), actionSize = 60.dp) { Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(38.dp)) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (isSuper) {
                    Text(
                        tr("الحساب الرئيسي محمي ولا يمكن تغيير صلاحياته من هنا", "The main account is protected and cannot be changed here"),
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    LabeledIconAction(label = if (saving) tr("جاري الحفظ...", "Saving...") else tr("حفظ التعديلات", "Save Changes"), onClick = {
                            if (!saving) {
                                saving = true
                                onSave(enabled, role, canEditCustomers, canDiscount, canCollectPayments, canViewReports) { saving = false }
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !saving, actionSize = 60.dp) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(38.dp)) }
                }
            }
        }
    }

    if (showPasswordReset) {
        AlertDialog(
            onDismissRequest = {
                showPasswordReset = false
                newLoginPassword = ""
                confirmLoginPassword = ""
            },
            title = { Text(tr("إعادة تعيين كلمة المرور", "Reset password"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "الإدارة مفتوحة بالفعل بالـ PIN. اكتب كلمة المرور الجديدة للمستخدم.",
                            "Administration is already unlocked. Enter the user's new password."
                        ),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newLoginPassword,
                        onValueChange = { newLoginPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("كلمة المرور الجديدة", "New password")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmLoginPassword,
                        onValueChange = { confirmLoginPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("تأكيد كلمة المرور", "Confirm password")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (confirmLoginPassword.isNotBlank() && newLoginPassword != confirmLoginPassword) {
                        Text(tr("كلمتا المرور غير متطابقتين", "Passwords do not match"), color = Color(0xFFB91C1C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                LabeledIconAction(
                    label = tr("تنفيذ الريسيت", "Reset now"),
                    onClick = {
                        if (newLoginPassword.length >= 6 && newLoginPassword == confirmLoginPassword) {
                            val next = newLoginPassword
                            showPasswordReset = false
                            newLoginPassword = ""
                            confirmLoginPassword = ""
                            onResetPassword(next)
                        }
                    },
                    enabled = newLoginPassword.length >= 6 && newLoginPassword == confirmLoginPassword, actionSize = 60.dp) { Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(38.dp)) }
            },
            dismissButton = {
                LabeledIconAction(label = tr("إلغاء", "Cancel"), onClick = {
                    showPasswordReset = false
                    newLoginPassword = ""
                    confirmLoginPassword = ""
                }, actionSize = 60.dp) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(38.dp)) }
            }
        )
    }
}

@Composable
private fun PermissionSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (checked) Color(0xFFF3FAFB) else Color(0xFFF8FAFC))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OpsDark)
            LabeledIconAction(label = if (checked) "مفعّل" else "غير مفعّل", onClick = { onChange(!(checked)) }) { Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.Block, contentDescription = null) }
        }
    }
}

@Composable
private fun RoleButtons(role: String, onRole: (String) -> Unit) {
    val rows = listOf(
        listOf(
            "staff" to tr("موظف", "Staff"),
            "reception" to tr("استقبال", "Reception"),
            "cashier" to tr("كاشير", "Cashier")
        ),
        listOf(
            "lab_operator" to tr("المعمل", "Lab"),
            "technician" to tr("فني", "Technician"),
            "supervisor" to tr("مشرف", "Supervisor"),
            "manager" to tr("مدير", "Manager")
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { (value, label) ->
                    LabeledIconAction(label = if (role == value) "✓ $label" else label, onClick = { onRole(value) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TouchApp, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreScreen(viewModel: LabTestsViewModel) {
    // V142_QUICK_BACKUP: always-visible manual backup control and status.
    val quickBackupContext = LocalContext.current
    var quickBackupBusy by remember { mutableStateOf(false) }
    var quickBackupMessage by remember { mutableStateOf("") }
    var showQuickBackupPassword by remember { mutableStateOf(false) }
    var quickBackupPassword by remember { mutableStateOf("") }
    var quickBackupLastName by remember { mutableStateOf(AutoBackupStorage.latestBackupName(quickBackupContext)) }

    // Resolve localized Compose text while we are still inside the @Composable scope.
    // The local backup function itself must stay non-Composable because it is invoked
    // from click/callback lambdas.
    val quickBackupCreatingText = appText("جاري إنشاء النسخة الاحتياطية…", "Creating backup…")
    val quickBackupSuccessText = appText(
        "تم حفظ نسخة جديدة ✓ والنسخ التلقائي 4:00 ص مفعّل",
        "New backup saved ✓ and automatic 4:00 AM backup is enabled"
    )

    fun runQuickBackup(password: String) {
        if (quickBackupBusy) return
        quickBackupBusy = true
        quickBackupMessage = quickBackupCreatingText
        BackupNotificationManager.notifyBackupStarted(quickBackupContext)
        viewModel.createCommercialBackup(password) { ok, msg, bytes ->
            if (!ok || bytes == null) {
                quickBackupBusy = false
                quickBackupMessage = msg
                BackupNotificationManager.notifyBackupFailed(quickBackupContext, msg)
            } else {
                runCatching { AutoBackupStorage.saveToPhone(quickBackupContext, bytes) }
                    .onSuccess { savedPath ->
                        quickBackupBusy = false
                        quickBackupLastName = AutoBackupStorage.latestBackupName(quickBackupContext)
                        quickBackupMessage = quickBackupSuccessText
                        AutoBackupScheduler.requestExactAlarmAccessOnce(quickBackupContext)
                        AutoBackupScheduler.schedule(quickBackupContext)
                        BackupNotificationManager.notifyBackupCompleted(quickBackupContext, savedPath)
                    }
                    .onFailure { error ->
                        quickBackupBusy = false
                        quickBackupMessage = error.message.orEmpty()
                        BackupNotificationManager.notifyBackupFailed(quickBackupContext, error.message.orEmpty())
                    }
            }
        }
    }

    val context = LocalContext.current
    val isOnline by viewModel.isOnline.collectAsState()
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedRestoreName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showRestoreDone by remember { mutableStateOf(false) }
    var restoreDoneMessage by remember { mutableStateOf("") }

    val createBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            val writeResult = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("تعذر فتح الملف للحفظ")
            }
            success = writeResult.isSuccess
            if (writeResult.isSuccess) AutoBackupScheduler.requestExactAlarmAccessOnce(context)
            message = if (writeResult.isSuccess) {
                tr("تم حفظ النسخة الاحتياطية المشفرة على الموبايل", "Encrypted backup saved to the phone")
            } else {
                tr("تعذر حفظ ملف النسخة الاحتياطية", "Could not save backup file")
            }
        }
    }

    val openBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val readResult = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    require(bytes.size <= 40 * 1024 * 1024) { "ملف النسخة أكبر من الحد المسموح" }
                    bytes
                } ?: error("تعذر قراءة الملف")
            }
            readResult.fold(
                onSuccess = { bytes ->
                    selectedRestoreBytes = bytes
                    selectedRestoreName = uri.lastPathSegment?.substringAfterLast('/') ?: "backup.tahbak"
                    success = true
                    message = tr("تم اختيار النسخة. أدخل كلمة المرور ثم اضغط استرجاع.", "Backup selected. Enter its password, then restore.")
                },
                onFailure = {
                    selectedRestoreBytes = null
                    selectedRestoreName = ""
                    success = false
                    message = it.message ?: tr("تعذر قراءة النسخة", "Could not read backup")
                }
            )
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(appText("تأكيد الاسترجاع", "Confirm restore"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    appText(
                        "الاسترجاع الآمن يفحص النسخة ويرجع البيانات المفقودة فقط، ولا يكتب فوق البيانات الموجودة ولا يحذفها. استمر فقط لو الملف موثوق.",
                        "Safe restore checks the backup and restores missing data only. It never overwrites or deletes existing data. Continue only with a trusted backup."
                    )
                )
            },
            confirmButton = {
                LabeledIconAction(label = appText("استرجاع الآن", "Restore now"), onClick = {
                    showRestoreConfirm = false
                    val bytes = selectedRestoreBytes ?: return@LabeledIconAction
                    busy = true
                    message = ""
                    viewModel.restoreCommercialBackup(bytes, restorePassword) { ok, msg ->
                        busy = false
                        success = ok
                        message = msg
                        if (ok) {
                            selectedRestoreBytes = null
                            selectedRestoreName = ""
                            restorePassword = ""
                            restoreDoneMessage = msg
                            showRestoreDone = true
                        }
                    }
                }) { Icon(Icons.Default.Restore, contentDescription = null) }
            },
            dismissButton = {
                LabeledIconAction(label = appText("إلغاء", "Cancel"), onClick = { showRestoreConfirm = false }) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        )
    }

    if (showRestoreDone) {
        AlertDialog(
            onDismissRequest = { showRestoreDone = false },
            title = { Text(appText("تم الاسترجاع ✓", "Restore Complete ✓"), fontWeight = FontWeight.ExtraBold) },
            text = { Text(restoreDoneMessage) },
            confirmButton = {
                LabeledIconAction(
                    label = appText("تم", "Done"),
                    onClick = { showRestoreDone = false }
                ) { Icon(Icons.Default.CheckCircle, contentDescription = null) }
            }
        )
    }

    
    if (showQuickBackupPassword) {
        AlertDialog(
            onDismissRequest = { showQuickBackupPassword = false; quickBackupPassword = "" },
            title = { Text(appText("تأمين النسخة الاحتياطية", "Secure backup"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(appText(
                        "اكتب كلمة مرور لا تقل عن 10 أحرف. هتتحفظ مشفرة داخل Android Keystore علشان النسخ التلقائي.",
                        "Enter at least 10 characters. It is encrypted by Android Keystore for automatic backups."
                    ))
                    OutlinedTextField(
                        value = quickBackupPassword,
                        onValueChange = { quickBackupPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appText("كلمة مرور النسخة", "Backup password")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                LabeledIconAction(
                    label = appText("إنشاء النسخة", "Create backup"),
                    onClick = {
                        val password = quickBackupPassword
                        showQuickBackupPassword = false
                        quickBackupPassword = ""
                        runQuickBackup(password)
                    },
                    enabled = quickBackupPassword.length >= 10,
                    actionSize = 62.dp
                ) { Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(42.dp)) }
            },
            dismissButton = {
                LabeledIconAction(
                    label = appText("إلغاء", "Cancel"),
                    onClick = { showQuickBackupPassword = false; quickBackupPassword = "" },
                    actionSize = 58.dp
                ) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(38.dp)) }
            }
        )
    }

LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8FB)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(appText("النسخ الاحتياطي", "Backup"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    LabeledIconAction(
                        label = if (quickBackupBusy) appText("جاري النسخ…", "Backing up…") else appText("نسخة احتياطية الآن", "Backup now"),
                        onClick = {
                            val savedPassword = AutoBackupCredentialStore.loadPassword(quickBackupContext)
                            if (savedPassword != null) runQuickBackup(savedPassword) else showQuickBackupPassword = true
                        },
                        enabled = !quickBackupBusy,
                        actionSize = 72.dp
                    ) {
                        if (quickBackupBusy) CircularProgressIndicator(modifier = Modifier.size(42.dp), strokeWidth = 3.dp)
                        else Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(50.dp))
                    }
                    Text(
                        if (AutoBackupCredentialStore.isConfigured(quickBackupContext))
                            appText("النسخ التلقائي: 4:00 ص • مفعّل", "Automatic backup: 4:00 AM • enabled")
                        else appText("النسخ التلقائي: يحتاج إعداد أول نسخة", "Automatic backup: first backup setup required"),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    quickBackupLastName?.let { Text(appText("آخر نسخة: $it", "Last backup: $it"), fontSize = 11.sp) }
                    if (quickBackupMessage.isNotBlank()) Text(quickBackupMessage, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }


        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF8FA))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = OpsPrimary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(appText("نسخة احتياطية تجارية مشفرة", "Encrypted Production Backup"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    }
                    Text(
                        appText(
                            "تشمل العملاء والطلبات والمدفوعات والأسعار وهوية المعمل. الملف مشفر AES-256-GCM بكلمة مرور تختارها أنت.",
                            "Includes customers, orders, payments, prices and lab identity. The file is protected with AES-256-GCM using your password."
                        ),
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                    Text(
                        if (isOnline) appText("● السيرفر متصل", "● Server online") else appText("● Offline — التصدير/الاسترجاع الكامل متوقف", "● Offline — full backup/restore disabled"),
                        color = if (isOnline) OpsGreen else Color(0xFFB91C1C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, null, tint = OpsPrimary)
                        Spacer(Modifier.width(7.dp))
                        Text(appText("إنشاء نسخة احتياطية", "Create Backup"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    }
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appText("كلمة مرور النسخة — 10 أحرف على الأقل", "Backup password — 10+ characters")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = backupPasswordConfirm,
                        onValueChange = { backupPasswordConfirm = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appText("تأكيد كلمة المرور", "Confirm password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = backupPasswordConfirm.isNotBlank() && backupPasswordConfirm != backupPassword
                    )
                    LabeledIconAction(label = appText("تشفير وحفظ النسخة", "Encrypt & Save Backup"), onClick = {
                            if (backupPassword.length < 10) {
                                success = false
                                message = tr("كلمة المرور لازم تكون 10 أحرف على الأقل", "Password must be at least 10 characters")
                            } else if (backupPassword != backupPasswordConfirm) {
                                success = false
                                message = tr("تأكيد كلمة المرور غير مطابق", "Password confirmation does not match")
                            } else {
                                busy = true
                                message = ""
                                viewModel.createCommercialBackup(backupPassword) { ok, msg, bytes ->
                                    busy = false
                                    success = ok
                                    message = msg
                                    if (ok && bytes != null) {
                                        pendingExportBytes = bytes
                                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                        createBackupFile.launch("Tahalil_Backup_$stamp.tahbak")
                                    }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = isOnline && !busy) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Restore, null, tint = Color(0xFF7C3AED))
                        Spacer(Modifier.width(7.dp))
                        Text(appText("استرجاع نسخة", "Restore Backup"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    }
                    LabeledIconAction(label = if (selectedRestoreBytes == null) appText("اختيار ملف .tahbak", "Choose .tahbak file") else selectedRestoreName, onClick = { openBackupFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Default.Restore, contentDescription = null) }
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(appText("كلمة مرور النسخة", "Backup password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    LabeledIconAction(label = appText("استرجاع آمن", "Safe Restore"), onClick = { showRestoreConfirm = true }, modifier = Modifier.fillMaxWidth(), enabled = isOnline && !busy && selectedRestoreBytes != null && restorePassword.length >= 10) { Icon(Icons.Default.Restore, contentDescription = null) }
                }
            }
        }

        if (message.isNotBlank()) item { OpsMessage(message, success) }

        item {
            Text(
                appText(
                    "مهم: احتفظ بكلمة مرور النسخة في مكان آمن. بدونها لا يمكن فك تشفير الملف.",
                    "Important: keep the backup password safe. The encrypted file cannot be recovered without it."
                ),
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun CommercialDemoScreen() {
    val settings = LocalAppSettings.current
    val demoOrders = listOf(
        Triple("DEM-1042", appText("عميل تجريبي 1", "Demo Patient 1"), appText("جاهز", "Ready")),
        Triple("DEM-1043", appText("عميل تجريبي 2", "Demo Patient 2"), appText("جاري التنفيذ", "Processing")),
        Triple("DEM-1044", appText("عميل تجريبي 3", "Demo Patient 3"), appText("جديد", "New"))
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = OpsDark)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF67E8F9), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(settings.pdfLabName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(settings.brandTagline, color = Color(0xFFBAE6FD), fontSize = 12.sp)
                    Text(
                        appText("وضع عرض فقط — لا يقرأ ولا يكتب بيانات العملاء الحقيقية", "Showcase only — does not read or write real patient data"),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoMetricCard(appText("طلبات اليوم", "Today's Orders"), "18", Modifier.weight(1f))
                DemoMetricCard(appText("جاهز", "Ready"), "7", Modifier.weight(1f))
                DemoMetricCard(appText("تحصيل", "Collected"), "8,420", Modifier.weight(1f))
            }
        }
        item {
            Text(appText("مثال تشغيل حي", "Live Workflow Example"), fontWeight = FontWeight.ExtraBold, color = OpsDark, fontSize = 17.sp)
        }
        items(demoOrders) { (number, name, status) ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.ExtraBold, color = OpsDark)
                        Text("$number • CBC • TSH • Ferritin", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OpsPrimary)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(appText("نقاط العرض التجاري", "Commercial Highlights"), fontWeight = FontWeight.ExtraBold, color = OpsDark)
                    listOf(
                        appText("بحث عربي/إنجليزي ومجموعة تحاليل دفعة واحدة", "Arabic/English batch test search"),
                        appText("طلبات وعملاء ودفع جزئي ومتبقي", "Orders, customers, partial payments and balances"),
                        appText("صور للعميل وللمعمل + حفظ ومشاركة", "Customer/lab images with save and share"),
                        appText("صلاحيات موظفين واعتماد أجهزة وAdmin PIN", "Staff roles, device approval and Admin PIN"),
                        appText("Audit + System Health + سلامة البيانات", "Audit + System Health + data integrity"),
                        appText("Backup مشفر + Offline/Sync + Crash monitoring", "Encrypted backup + Offline/Sync + crash monitoring"),
                        appText("White-label: اسم ولوجو وبيانات المعمل", "White-label lab name, logo and contact identity")
                    ).forEach { text ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("✓", color = OpsGreen, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(7.dp))
                            Text(text, fontSize = 12.sp, color = Color(0xFF334155), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = OpsPrimary)
            Text(title, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 2)
        }
    }
}

@Composable
private fun AccessDeniedCard() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(tr("القسم متاح للمدير فقط", "Manager Only"), fontWeight = FontWeight.ExtraBold, color = Color(0xFFB91C1C))
    }
}

@Composable
private fun OpsMessage(message: String, success: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (success) Color(0xFFE8F7ED) else Color(0xFFFFEEEE))
    ) {
        Text(
            message,
            modifier = Modifier.padding(10.dp),
            color = if (success) Color(0xFF166534) else Color(0xFFB91C1C),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

private fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun startOfDaysAgo(days: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -days.coerceAtLeast(0))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatDateField(value: Long): String =
    if (value <= 0L) "" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))

private fun formatDateDisplay(value: Long): String =
    if (value <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy", Locale("ar", "EG")).format(Date(value))

private fun parseDateStart(value: String): Long? {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        format.parse(value.trim())?.time
    } catch (_: Exception) { null }
}

private fun parseDateEnd(value: String): Long? {
    val start = parseDateStart(value) ?: return null
    val cal = Calendar.getInstance().apply { timeInMillis = start }
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

private fun opsMoney(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

private fun opsDate(value: Long): String =
    if (value <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar", "EG")).format(Date(value))

private fun paymentStatusLabel(status: String): String = when (status) {
    "paid" -> "مدفوع"
    "partial" -> tr("مدفوع جزئي", "Partially Paid")
    else -> tr("غير مدفوع", "Unpaid")
}

private fun roleArabic(role: String): String = when (role) {
    "super_admin" -> "Super Admin"
    "manager" -> tr("مدير", "Manager")
    "supervisor" -> tr("مشرف", "Supervisor")
    "cashier" -> tr("كاشير", "Cashier")
    "technician" -> tr("فني", "Technician")
    "reception" -> tr("استقبال", "Reception")
    "lab_operator" -> tr("المعمل", "Lab")
    else -> tr("موظف", "Staff")
}
