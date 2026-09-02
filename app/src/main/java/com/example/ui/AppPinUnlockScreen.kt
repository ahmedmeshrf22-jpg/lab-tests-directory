package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.Arrangement
import com.example.settings.tr
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppPinUnlockScreen(
    onUnlock: (String) -> Boolean,
    onLogoutClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockedUntilMillis by remember { mutableStateOf(0L) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF006D86))
                    Spacer(Modifier.height(14.dp))
                    Text(tr("رمز دخول التطبيق", "App PIN"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17324D))
                    Spacer(Modifier.height(6.dp))
                    Text(tr("اكتب الرقم السري الخاص بالتطبيق", "Enter the app PIN"), fontSize = 13.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            pin = value.filter(Char::isDigit).take(6)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        singleLine = true,
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error.orEmpty(), color = Color(0xFFB91C1C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                    LabeledIconAction(label = tr("فتح التطبيق", "Unlock App"), onClick = {
                            val now = System.currentTimeMillis()
                            if (lockedUntilMillis > now) {
                                val remaining = ((lockedUntilMillis - now + 999L) / 1000L).coerceAtLeast(1L)
                                error = "محاولات كثيرة. حاول مرة أخرى بعد $remaining ثانية"
                            } else if (pin.length < 4) {
                                // Legacy 4/5-digit PINs remain unlockable; every new PIN is six digits.
                                error = "الرقم السري غير صحيح"
                            } else if (!onUnlock(pin)) {
                                val nextFailures = failedAttempts + 1
                                failedAttempts = nextFailures
                                pin = ""
                                if (nextFailures >= 5) {
                                    failedAttempts = 0
                                    lockedUntilMillis = now + 60_000L
                                    error = "تم إيقاف المحاولات لمدة 60 ثانية للحماية"
                                } else {
                                    error = "الرقم السري غير صحيح • متبقي ${5 - nextFailures} محاولات"
                                }
                            } else {
                                failedAttempts = 0
                                lockedUntilMillis = 0L
                                error = null
                            }
                        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Visibility, contentDescription = null) }

                    Spacer(Modifier.height(10.dp))
                    LabeledIconAction(label = tr("  تسجيل الخروج", "  Log out"), onClick = onLogoutClick) { Icon(Icons.Default.Logout, contentDescription = null) }
                }
            }
        }
    }
}
