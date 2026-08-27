package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Biotech
import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Customer
import com.example.data.model.CustomerOrder
import com.example.settings.appText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HubBlue = Color(0xFF006D86)
private val HubGreen = Color(0xFF15803D)
private val HubRed = Color(0xFFB91C1C)
private val HubAmber = Color(0xFFD97706)
private val HubText = Color(0xFF17324D)
private val HubMuted = Color(0xFF64748B)
private val HubBg = Color(0xFFF4F7FB)

private const val HUB_PREFS = "order_hub_v113"
private const val HUB_SEEN_RESULTS = "seen_results"

private data class HubRange(val start: Long, val end: Long)

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun defaultHubRange(): HubRange {
    val end = endOfDay(System.currentTimeMillis())
    val start = Calendar.getInstance().apply {
        timeInMillis = end
        add(Calendar.DAY_OF_YEAR, -6)
    }.timeInMillis
    return HubRange(startOfDay(start), end)
}

private fun shiftRange(range: HubRange, days: Int): HubRange {
    fun shift(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = value
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis
    return HubRange(shift(range.start), shift(range.end))
}

private fun shortDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("ar", "EG")).format(Date(millis))
private fun fullDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale("ar", "EG")).format(Date(millis))

private fun CustomerOrder.hubStatus(): String = when {
    isVoided -> "cancelled"
    workflowStatus in setOf("ready", "delivered") || resultSentAtMillis > 0L || resultUrls.isNotEmpty() -> "completed"
    else -> "active"
}

private fun CustomerOrder.resultSeenKey(): String {
    val stamp = resultSentAtMillis.takeIf { it > 0L } ?: updatedAtMillis.takeIf { it > 0L } ?: createdAtMillis
    return "$id:$stamp"
}

@Composable
fun OrderHubScreen(
    viewModel: LabTestsViewModel,
    isManager: Boolean,
    initialOrderId: String? = null,
    onInitialOrderConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val orders by viewModel.orderArchive.collectAsState()
    val loading by viewModel.orderArchiveLoading.collectAsState()
    var range by remember { mutableStateOf(defaultHubRange()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var showNotifications by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var openingOrderId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val prefs = remember(context) { context.getSharedPreferences(HUB_PREFS, Context.MODE_PRIVATE) }
    var seenResults by remember {
        mutableStateOf(prefs.getStringSet(HUB_SEEN_RESULTS, emptySet()).orEmpty().toSet())
    }

    fun persistSeen(next: Set<String>) {
        seenResults = next.toList().takeLast(400).toSet()
        prefs.edit().putStringSet(HUB_SEEN_RESULTS, seenResults).apply()
    }

    fun reload() {
        viewModel.loadOrderArchive(range.start, range.end) { ok, msg ->
            if (!ok) statusMessage = msg
        }
    }

    LaunchedEffect(range.start, range.end) { reload() }

    val normalized = search.trim().lowercase()
    val filtered = orders.filter { order ->
        val matchesSearch = normalized.isBlank() || listOf(
            order.orderNumber,
            order.customerName,
            order.customerPhone,
            order.customerFileNumber
        ).any { it.lowercase().contains(normalized) }
        val matchesStatus = statusFilter == "all" || order.hubStatus() == statusFilter
        matchesSearch && matchesStatus
    }

    val activeCount = orders.count { it.hubStatus() == "active" }
    val completedCount = orders.count { it.hubStatus() == "completed" }
    val cancelledCount = orders.count { it.hubStatus() == "cancelled" }
    val unreadResults = orders.filter {
        it.hubStatus() == "completed" && it.resultSeenKey() !in seenResults
    }

    fun openOrder(order: CustomerOrder) {
        openingOrderId = order.id
        if (order.hubStatus() == "completed") persistSeen(seenResults + order.resultSeenKey())
        viewModel.findCustomerById(order.customerId) { customer, _ ->
            selectedCustomer = customer
            selectedOrder = order
            openingOrderId = null
        }
    }

    LaunchedEffect(initialOrderId, orders) {
        val id = initialOrderId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        orders.firstOrNull { it.id == id }?.let {
            openOrder(it)
            onInitialOrderConsumed()
        }
    }

    BackHandler(enabled = selectedOrder != null || showNotifications) {
        when {
            selectedOrder != null -> selectedOrder = null
            showNotifications -> showNotifications = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(HubBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(appText("سجل الطلبات", "Orders Hub"), fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = HubText)
                Text(
                    appText("كل طلبات المعمل في مكان واحد", "All lab orders in one place"),
                    fontSize = 11.sp,
                    color = HubMuted
                )
            }
            Box {
                LabeledIconAction(label = "الإشعارات", onClick = { showNotifications = !showNotifications }) {
                    Icon(
                        if (unreadResults.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (unreadResults.isNotEmpty()) HubRed else HubBlue
                    )
                }
                if (unreadResults.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).size(19.dp),
                        shape = CircleShape,
                        color = HubRed
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(unreadResults.size.coerceAtMost(99).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            LabeledIconAction(label = "تحديث", onClick = { reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = HubBlue)
            }
        }

        if (showNotifications) {
            NotificationCenter(
                orders = orders,
                seenResults = seenResults,
                onOpen = { openOrder(it) },
                onMarkAllRead = {
                    persistSeen(seenResults + orders.filter { it.hubStatus() == "completed" }.map { it.resultSeenKey() })
                }
            )
            return@Column
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(appText("رقم الطلب / اسم العميل / الهاتف / رقم الملف", "Order / customer / phone / file")) },
                shape = RoundedCornerShape(16.dp)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LabeledIconAction(label = "التالي", onClick = { range = shiftRange(range, -7) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
                LabeledIconAction(label = shortDate(range.start), onClick = { pickDate(context, range.start) { range = HubRange(startOfDay(it), range.end.coerceAtLeast(endOfDay(it))) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                Text("—", color = HubMuted)
                LabeledIconAction(label = shortDate(range.end), onClick = { pickDate(context, range.end) { range = HubRange(range.start.coerceAtMost(startOfDay(it)), endOfDay(it)) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                LabeledIconAction(label = "السابق", onClick = { range = shiftRange(range, 7) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HubFilterButton("all", appText("الكل", "All"), orders.size, statusFilter) { statusFilter = it }
                HubFilterButton("active", appText("قيد التنفيذ", "In progress"), activeCount, statusFilter) { statusFilter = it }
                HubFilterButton("completed", appText("مكتمل", "Completed"), completedCount, statusFilter) { statusFilter = it }
                HubFilterButton("cancelled", appText("ملغي", "Cancelled"), cancelledCount, statusFilter) { statusFilter = it }
            }
        }

        Spacer(Modifier.height(8.dp))
        statusMessage?.let {
            Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = HubRed, fontSize = 11.sp)
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(appText("لا توجد طلبات مطابقة", "No matching orders"), color = HubMuted, fontWeight = FontWeight.Bold)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(filtered, key = { it.id }) { order ->
                    OrderHubCard(
                        order = order,
                        unreadResult = order.hubStatus() == "completed" && order.resultSeenKey() !in seenResults,
                        busy = openingOrderId == order.id,
                        onClick = { openOrder(order) }
                    )
                }
            }
        }
    }

    selectedOrder?.let { order ->
        CustomerOrderDetailsDialog(
            viewModel = viewModel,
            customer = selectedCustomer,
            order = order,
            isManager = isManager,
            onDismiss = {
                selectedOrder = null
                selectedCustomer = null
                reload()
            }
        )
    }
}

private fun pickDate(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val picked = Calendar.getInstance().apply {
                set(year, month, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onPicked(picked)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
private fun RowScope.HubFilterButton(
    value: String,
    label: String,
    count: Int,
    selected: String,
    onSelect: (String) -> Unit
) {
    val active = value == selected
    val icon = when (value) {
        "active" -> Icons.Default.Schedule
        "completed" -> Icons.Default.CheckCircle
        "cancelled" -> Icons.Default.Cancel
        else -> Icons.Default.WorkHistory
    }
    LabeledIconAction(label = "$label\n$count", onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) {
        Icon(icon, contentDescription = null, tint = if (active) HubBlue else HubMuted)
    }
}

@Composable
private fun OrderHubCard(order: CustomerOrder, unreadResult: Boolean, busy: Boolean, onClick: () -> Unit) {
    val status = order.hubStatus()
    val accent = when (status) {
        "completed" -> HubGreen
        "cancelled" -> HubRed
        else -> HubBlue
    }
    val statusIcon = when (status) {
        "completed" -> Icons.Default.CheckCircle
        "cancelled" -> Icons.Default.Cancel
        else -> Icons.Default.Schedule
    }
    val statusLabel = when (status) {
        "completed" -> appText("مكتمل", "Completed")
        "cancelled" -> appText("تم الإلغاء", "Cancelled")
        else -> appText("قيد التنفيذ", "In progress")
    }
    val oldActive = status == "active" && System.currentTimeMillis() - order.createdAtMillis > 24L * 60L * 60L * 1000L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (unreadResult) HubGreen.copy(alpha = .6f) else accent.copy(alpha = .18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (unreadResult) 4.dp else 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(43.dp).background(accent.copy(alpha=.11f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(statusIcon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(order.customerName.ifBlank { appText("عميل", "Customer") }, fontWeight = FontWeight.ExtraBold, color = HubText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkHistory, contentDescription = null, tint = HubMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(order.orderNumber, fontSize = 10.sp, color = HubMuted, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = .12f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubMiniInfo(Modifier.weight(1f), Icons.Default.Badge, appText("رقم الملف", "File no."), order.customerFileNumber.ifBlank { "—" })
                HubMiniInfo(Modifier.weight(1f), Icons.Default.Phone, appText("الموبايل", "Phone"), order.customerPhone.ifBlank { "—" })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubMiniInfo(Modifier.weight(1f), Icons.Default.CalendarMonth, appText("التاريخ", "Date"), fullDate(order.createdAtMillis))
                HubMiniInfo(Modifier.weight(1f), Icons.Outlined.Biotech, appText("التحاليل", "Tests"), order.items.size.toString())
                HubMiniInfo(Modifier.weight(1f), Icons.Default.Payments, appText("الإجمالي", "Total"), "${order.totalCustomerPrice.toInt()} ج")
            }

            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp); Spacer(Modifier.width(7.dp)); Text(appText("جاري الفتح...", "Opening..."), fontSize = 10.sp, color = HubMuted) }
                unreadResult -> Text(appText("● نتيجة جديدة وصلت للطلب", "● New result received"), color = HubGreen, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                oldActive -> Text(appText("⚠ الطلب متأخر أكثر من 24 ساعة", "⚠ Delayed more than 24 hours"), color = HubAmber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }

            LabeledIconAction(label = appText("فتح تفاصيل الطلب", "Open order details"), onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun HubMiniInfo(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = HubBg, border = BorderStroke(1.dp, HubBlue.copy(alpha = .10f))) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(28.dp).background(HubBlue.copy(alpha = .10f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = HubBlue, modifier = Modifier.size(17.dp))
            }
            Text(label, fontSize = 9.sp, color = HubMuted, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 10.sp, lineHeight = 14.sp, color = HubText, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NotificationCenter(
    orders: List<CustomerOrder>,
    seenResults: Set<String>,
    onOpen: (CustomerOrder) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val events = orders.sortedByDescending { it.updatedAtMillis.takeIf { v -> v > 0L } ?: it.createdAtMillis }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(appText("مركز الإشعارات", "Notification Center"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = HubText)
                Text(appText("اضغط على أي إشعار لفتح الطلب", "Tap any notification to open the order"), fontSize = 10.sp, color = HubMuted)
            }
            LabeledIconAction(label = appText("قراءة الكل", "Mark all read"), onClick = onMarkAllRead) { Icon(Icons.Default.TouchApp, contentDescription = null) }
        }
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(appText("لا توجد إشعارات", "No notifications"), color = HubMuted) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(events, key = { "event_${it.id}" }) { order ->
                    val status = order.hubStatus()
                    val unread = status == "completed" && order.resultSeenKey() !in seenResults
                    val title = when (status) {
                        "completed" -> if (unread) appText("نتيجة جديدة وصلت ✅", "New result received ✅") else appText("الطلب مكتمل", "Order completed")
                        "cancelled" -> appText("تم إلغاء الطلب", "Order cancelled")
                        else -> if (order.workflowStatus == "processing") appText("المعمل استلم الطلب", "Lab accepted the order") else appText("الطلب قيد التنفيذ", "Order in progress")
                    }
                    val accent = when (status) { "completed" -> HubGreen; "cancelled" -> HubRed; else -> HubBlue }
                    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = if (unread) Color(0xFFF0FDF4) else Color.White),
        border = BorderStroke(1.dp, if (unread) HubGreen.copy(alpha=.5f) else Color(0xFFE3EAF0))
    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).background(accent.copy(alpha=.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    when (status) { "completed" -> Icons.Default.CheckCircle; "cancelled" -> Icons.Default.Cancel; else -> Icons.Default.Schedule },
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.ExtraBold, color = HubText, fontSize = 12.sp)
                                Text("${order.orderNumber} • ${order.customerName}", fontSize = 10.sp, color = HubMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fullDate(order.updatedAtMillis.takeIf { it > 0L } ?: order.createdAtMillis), fontSize = 9.sp, color = HubMuted)
                            
            LabeledIconAction(label = "فتح", onClick = { onOpen(order) }) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
                            if (unread) Surface(modifier = Modifier.size(9.dp), shape = CircleShape, color = HubRed) {}
                        }
                    }
                }
            }
        }
    }
}
