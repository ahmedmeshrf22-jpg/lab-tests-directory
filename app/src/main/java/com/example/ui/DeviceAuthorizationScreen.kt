package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.Arrangement
import com.example.settings.tr
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun DeviceAuthorizationScreen(
    status: String,
    onRefresh: () -> Unit,
    onApproveWithAdminPin: (String, (Boolean, String) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    val rejected = status == "rejected" || status == "revoked"
    val error = status == "error"
    var adminPin by remember { mutableStateOf("") }
    var approving by remember { mutableStateOf(false) }
    var approvalMessage by remember { mutableStateOf<String?>(null) }
    var approvalSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (rejected) Icons.Default.Block else Icons.Default.AdminPanelSettings,
            contentDescription = null,
            tint = if (rejected || error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = when {
                rejected -> "الجهاز غير مصرح به"
                error -> "تعذر التحقق من الجهاز"
                else -> "في انتظار موافقة المدير"
            },
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                status == "revoked" -> "تم إلغاء اعتماد هذا الجهاز. اطلب من المدير إعادة اعتماده لو هتستخدمه مرة تانية."
                status == "rejected" -> "تم رفض طلب هذا الجهاز. تواصل مع المدير لو محتاج السماح له بالدخول."
                error -> "تأكد من الإنترنت ومن نشر Firestore Rules الخاصة بتصريح الأجهزة، ثم حاول مرة أخرى."
                else -> "تم تسجيل الجهاز. المدير يقدر يعتمد الجهاز من: الإدارة ← الأجهزة المصرح بها."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = adminPin,
            onValueChange = { adminPin = it.filter(Char::isDigit).take(6); approvalMessage = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("PIN الإدارة لاعتماد الجهاز", "Admin PIN to approve device")) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Spacer(Modifier.height(8.dp))
        LabeledIconAction(label = if (approving) tr("جاري الاعتماد...", "Approving...") else tr("اعتماد الجهاز بـ PIN الإدارة", "Approve with admin PIN"), onClick = {
                if (!approving) {
                    approving = true
                    onApproveWithAdminPin(adminPin) { ok, message ->
                        approving = false
                        approvalSuccess = ok
                        approvalMessage = message
                        if (ok) adminPin = ""
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !approving && adminPin.length == 6) { Icon(Icons.Default.CheckCircle, contentDescription = null) }
        approvalMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                color = if (approvalSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        LabeledIconAction(label = tr("إعادة التحقق", "Check again"), onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, contentDescription = null) }
        Spacer(Modifier.height(8.dp))
        LabeledIconAction(label = tr("تسجيل الخروج", "Log out"), onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, contentDescription = null) }
    }
}
