package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.Arrangement
import com.example.settings.tr
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountDisabledScreen(onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(tr("الحساب غير مفعّل", "Account Not Activated"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            tr("الحساب غير مضاف للنظام أو تم إيقافه. اطلب من المدير تفعيله من حسابات الاستاف.", "This account is not provisioned or has been disabled. Ask the manager to activate it from Staff Accounts."),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        LabeledIconAction(label = tr("تسجيل الخروج", "Log out"), onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = null) }
    }
}
