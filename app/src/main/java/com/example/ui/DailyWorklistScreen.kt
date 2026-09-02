package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Customer
import com.example.data.model.CustomerOrder
import com.example.settings.appText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class WorkStatus(
    val key: String,
    val ar: String,
    val en: String,
    val accent: Color
)

private val labDeskStatuses = listOf(
    WorkStatus("all", "الكل", "All", Color(0xFF38BDF8)),
    WorkStatus("sent", "تم الإرسال للمعمل", "Sent to Lab", Color(0xFF60A5FA)),
    WorkStatus("processing", "جاري التنفيذ", "Processing", Color(0xFFF59E0B)),
    WorkStatus("ready", "النتيجة جاهزة", "Result Ready", Color(0xFF22C55E))
)

private fun clinicStatusKey(raw: String): String = when (raw) {
    "processing" -> "processing"
    "ready", "delivered" -> "ready"
    else -> "sent"
}

private fun statusFor(key: String): WorkStatus {
    val normalized = if (key == "all") "all" else clinicStatusKey(key)
    return labDeskStatuses.firstOrNull { it.key == normalized } ?: labDeskStatuses[1]
}

@Composable
fun DailyWorklistScreen(
    viewModel: LabTestsViewModel,
    onOpenCustomer: (Customer) -> Unit
) {
    val orders by viewModel.dailyOrders.collectAsState()
    val loading by viewModel.dailyOrdersLoading.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("all") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadDailyOrders { _, msg -> message = msg }
    }

    val normalized = query.trim().lowercase()
    val filtered = orders.filter { order ->
        val statusMatch = selectedStatus == "all" || clinicStatusKey(order.workflowStatus) == selectedStatus
        val queryMatch = normalized.isBlank() ||
            order.customerName.lowercase().contains(normalized) ||
            order.orderNumber.lowercase().contains(normalized) ||
            order.items.any { item ->
                item.englishName.lowercase().contains(normalized) ||
                    item.arabicName.lowercase().contains(normalized) ||
                    item.marketName.lowercase().contains(normalized)
            }
        statusMatch && queryMatch
    }

    val sentCount = orders.count { clinicStatusKey(it.workflowStatus) == "sent" }
    val processingCount = orders.count { clinicStatusKey(it.workflowStatus) == "processing" }
    val readyCount = orders.count { clinicStatusKey(it.workflowStatus) == "ready" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF071827),
                border = BorderStroke(1.dp, Color(0xFF22D3EE))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(appText("تشغيل اليوم", "Today's worklist"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(appText("متابعة بسيطة لحالة طلبات المعمل — آخر 30 يوم", "Simple lab order tracking — last 30 days"), color = Color(0xFF9FB8CA), fontSize = 10.sp)
                        }
                        LabeledIconAction(label = "تحديث", onClick = { viewModel.loadDailyOrders { _, msg -> message = msg } }) {
                            if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF22D3EE))
                            else Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF67E8F9))
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        DeskStat(Modifier.weight(1f), orders.size.toString(), appText("الكل", "All"), Color(0xFF38BDF8))
                        DeskStat(Modifier.weight(1f), sentCount.toString(), appText("تم الإرسال", "Sent"), Color(0xFF60A5FA))
                        DeskStat(Modifier.weight(1f), processingCount.toString(), appText("تنفيذ", "Processing"), Color(0xFFF59E0B))
                        DeskStat(Modifier.weight(1f), readyCount.toString(), appText("جاهز", "Ready"), Color(0xFF22C55E))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(appText("اسم العميل، رقم الطلب أو التحليل", "Customer, order no. or test")) },
                shape = RoundedCornerShape(16.dp)
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labDeskStatuses.forEach { status ->
                    LabeledIconAction(label = appText(status.ar, status.en), onClick = { selectedStatus = status.key }, modifier = Modifier.weight(1f)) { Icon(if (selectedStatus == status.key) Icons.Default.CheckCircle else Icons.Default.Tune, contentDescription = null) }
                }
            }
        }

        if (message != null) {
            item {
                Text(message.orEmpty(), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 4.dp))
            }
        }

        if (!loading && filtered.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(appText("مفيش طلبات مطابقة", "No matching orders"), fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { order ->
            WorkOrderCard(
                order = order,
                onOpenCustomer = {
                    viewModel.findCustomerById(order.customerId) { customer, msg ->
                        message = msg
                        if (customer != null) onOpenCustomer(customer)
                    }
                }
            )
        }
    }
}

@Composable
private fun DeskStat(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WorkOrderCard(
    order: CustomerOrder,
    onOpenCustomer: () -> Unit
) {
    val status = statusFor(order.workflowStatus)
    val time = remember(order.createdAtMillis) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(order.createdAtMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, status.accent.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(status.accent.copy(alpha = 0.28f), status.accent.copy(alpha = 0.08f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = status.accent)
                }
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(order.customerName.ifBlank { appText("عميل", "Customer") }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${order.orderNumber}  •  $time", fontSize = 9.sp, color = Color(0xFF64748B))
                }
                Surface(shape = RoundedCornerShape(99.dp), color = status.accent.copy(alpha = 0.12f), border = BorderStroke(1.dp, status.accent.copy(alpha = 0.45f))) {
                    Text(appText(status.ar, status.en), modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = status.accent)
                }
            }

            Text(
                order.items.joinToString(" • ") { it.englishName.ifBlank { it.arabicName } },
                fontSize = 10.sp,
                color = Color(0xFF334155),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(appText("الإجمالي: ${formatMoney(order.totalCustomerPrice)}", "Total: ${formatMoney(order.totalCustomerPrice)}"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(
                    if (order.remainingAmount > 0.001) appText("متبقي: ${formatMoney(order.remainingAmount)}", "Due: ${formatMoney(order.remainingAmount)}") else appText("مدفوع", "Paid"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (order.remainingAmount > 0.001) Color(0xFFE11D48) else Color(0xFF16A34A)
                )
            }


            val updatedTime = remember(order.updatedAtMillis, order.createdAtMillis) {
                SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()).format(Date(order.updatedAtMillis.takeIf { it > 0 } ?: order.createdAtMillis))
            }
            Text(
                appText("آخر تحديث: $updatedTime", "Last update: $updatedTime"),
                fontSize = 9.sp,
                color = Color(0xFF64748B)
            )

            LabeledIconAction(label = appText("فتح الطلب", "Open order"), onClick = onOpenCustomer, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Visibility, contentDescription = null) }
        }
    }
}


private fun formatMoney(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format(Locale.US, "%.2f", rounded)
}
