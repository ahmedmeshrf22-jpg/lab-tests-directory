package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.settings.tr

private val LoginNavy = Color(0xFF082D42)
private val LoginBlue = Color(0xFF087FA4)
private val LoginCyan = Color(0xFF20B9D2)
private val LoginText = Color(0xFF102A43)
private val LoginMuted = Color(0xFF718096)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    onLoginClick: (email: String, pass: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var recoveryMessage by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val autofill = LocalAutofill.current
    val emailAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = { email = it }
        )
    }
    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { password = it }
        )
    }
    val autofillTree = LocalAutofillTree.current
    remember {
        autofillTree += emailAutofillNode
        autofillTree += passwordAutofillNode
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(modifier = Modifier.fillMaxSize(), color = LoginNavy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF062B40),
                                Color(0xFF0C5870),
                                Color(0xFFEAF9FC)
                            )
                        )
                    )
            ) {
                // Soft glow only: no home-screen cards or service shortcuts on login.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 80.dp, y = (-85).dp)
                        .size(245.dp)
                        .shadow(
                            elevation = 32.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x4420B9D2),
                            spotColor = Color(0x5520B9D2)
                        )
                        .background(Color(0x2220B9D2), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-90).dp, y = 95.dp)
                        .size(260.dp)
                        .shadow(
                            elevation = 34.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x33168FB0),
                            spotColor = Color(0x44168FB0)
                        )
                        .background(Color(0x18168FB0), CircleShape)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Brand header + one medical 3D accent, all still part of the login screen.
                    Surface(
                        modifier = Modifier
                            .size(92.dp)
                            .shadow(
                                elevation = 22.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = Color(0x4620B9D2),
                                spotColor = Color(0x5520B9D2)
                            ),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xF7FFFFFF),
                        border = BorderStroke(1.dp, Color(0xCFFFFFFF))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.ic_clinic_logo),
                                contentDescription = tr("شعار عيادات العقاد", "Alakkad Clinics logo"),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "تحاليل العقاد",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = tr("نظام المعمل الذكي", "Smart Laboratory System"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD9F4F8)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = Color(0x2EFFFFFF),
                        border = BorderStroke(1.dp, Color(0x55FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF7EEBFA),
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = tr("دخول آمن للموظفين", "Secure staff access"),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 22.dp,
                                shape = RoundedCornerShape(32.dp),
                                ambientColor = Color(0x26002030),
                                spotColor = Color(0x3320B9D2)
                            ),
                        shape = RoundedCornerShape(32.dp),
                        border = BorderStroke(1.dp, Color(0xD9FFFFFF)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 21.dp, vertical = 23.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // This is the only 3D medical accent inside the login form.
                            Surface(
                                modifier = Modifier
                                    .size(68.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        ambientColor = Color(0x2620B9D2),
                                        spotColor = Color(0x3320B9D2)
                                    ),
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFD7EEF3))
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.staff_tests_3d),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = tr("تسجيل الدخول", "Sign in"),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                color = LoginText
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tr("أدخل بيانات حسابك للمتابعة", "Enter your account details to continue"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = LoginMuted,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 66.dp)
                                    .testTag("email_input")
                                    .onGloballyPositioned { emailAutofillNode.boundingBox = it.boundsInWindow() }
                                    .onFocusChanged { focusState ->
                                        autofill?.run {
                                            if (focusState.isFocused) requestAutofillForNode(emailAutofillNode)
                                            else cancelAutofillForNode(emailAutofillNode)
                                        }
                                    },
                                label = { Text(tr("البريد الإلكتروني", "Email")) },
                                leadingIcon = {
                                    Surface(
                                        modifier = Modifier.shadow(5.dp, CircleShape),
                                        shape = CircleShape,
                                        color = Color(0xFFE4F8FB)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = LoginBlue,
                                            modifier = Modifier.padding(8.dp).size(19.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                shape = RoundedCornerShape(19.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LoginCyan,
                                    unfocusedBorderColor = Color(0xFFD6E5EA),
                                    focusedLabelColor = LoginBlue,
                                    unfocusedLabelColor = LoginMuted,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFFBFDFE)
                                )
                            )

                            Spacer(modifier = Modifier.height(13.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 66.dp)
                                    .testTag("password_input")
                                    .onGloballyPositioned { passwordAutofillNode.boundingBox = it.boundsInWindow() }
                                    .onFocusChanged { focusState ->
                                        autofill?.run {
                                            if (focusState.isFocused) requestAutofillForNode(passwordAutofillNode)
                                            else cancelAutofillForNode(passwordAutofillNode)
                                        }
                                    },
                                label = { Text(tr("كلمة المرور", "Password")) },
                                leadingIcon = {
                                    Surface(
                                        modifier = Modifier.shadow(5.dp, CircleShape),
                                        shape = CircleShape,
                                        color = Color(0xFFE4F8FB)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = LoginBlue,
                                            modifier = Modifier.padding(8.dp).size(19.dp)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) tr("إخفاء كلمة المرور", "Hide password") else tr("إظهار كلمة المرور", "Show password"),
                                            tint = LoginMuted
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                            onLoginClick(email.trim(), password)
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(19.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LoginCyan,
                                    unfocusedBorderColor = Color(0xFFD6E5EA),
                                    focusedLabelColor = LoginBlue,
                                    unfocusedLabelColor = LoginMuted,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFFBFDFE)
                                )
                            )

                            if (!errorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(11.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFFFF1F2),
                                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                                ) {
                                    Text(
                                        text = errorMessage,
                                        color = Color(0xFFBE123C),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(17.dp))

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    onLoginClick(email.trim(), password)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(57.dp)
                                    .shadow(
                                        elevation = 13.dp,
                                        shape = RoundedCornerShape(19.dp),
                                        ambientColor = Color(0x3520B9D2),
                                        spotColor = Color(0x5020B9D2)
                                    )
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color(0xFF087FA4), Color(0xFF20B9D2))
                                        ),
                                        shape = RoundedCornerShape(19.dp)
                                    )
                                    .testTag("login_button"),
                                enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                                shape = RoundedCornerShape(19.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFBCD8DF),
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(21.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = if (isLoading) tr("جارٍ تسجيل الدخول...", "Signing in...") else tr("تسجيل الدخول", "Sign in"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(15.dp))

                            RecoveryAction3D(
                                iconRes = R.drawable.mgr_staff_3d,
                                title = tr("نسيت اسم المستخدم؟", "Forgot username?"),
                                subtitle = tr("اضغط لمعرفة طريقة الاسترجاع", "Tap for recovery instructions"),
                                onClick = {
                                    recoveryMessage = tr(
                                        "راجع الإدارة لمعرفة اسم الدخول / البريد المسجل لحسابك.",
                                        "Contact administration to recover the login email / username registered for your account."
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.height(9.dp))

                            RecoveryAction3D(
                                iconRes = R.drawable.settings_3d,
                                title = tr("نسيت كلمة المرور؟", "Forgot password?"),
                                subtitle = tr("اضغط لطلب إعادة تعيين بيانات الدخول", "Tap to request a credential reset"),
                                onClick = {
                                    recoveryMessage = tr(
                                        "راجع الإدارة لإعادة تعيين بيانات الدخول لحسابك.",
                                        "Contact administration to reset your account login credentials."
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(13.dp))
                    Text(
                        text = tr("عيادات العقاد التخصصية • نظام داخلي آمن", "Alakkad Specialized Clinics • Secure internal system"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF315B68),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    recoveryMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { recoveryMessage = null },
            title = { Text(tr("استرجاع الحساب", "Account recovery"), fontWeight = FontWeight.Bold) },
            text = { Text(msg, textAlign = TextAlign.Center) },
            confirmButton = {
                LabeledIconAction(label = tr("حسناً", "OK"), onClick = { recoveryMessage = null }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LoginBlue)
                }
            }
        )
    }
}

@Composable
private fun RecoveryAction3D(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF1FAFC),
            contentColor = LoginText
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(
                        elevation = 7.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = Color(0x1D20B9D2),
                        spotColor = Color(0x2920B9D2)
                    ),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFD9EEF2))
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = LoginText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoginMuted
                )
            }
        }
    }
}
