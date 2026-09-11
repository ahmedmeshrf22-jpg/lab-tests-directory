package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V145 R7 shared action control: every tappable action now lives inside a
 * consistent square icon frame, with its visible name directly underneath.
 */
@Composable
fun LabeledIconAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconTint: Color = LocalContentColor.current,
    actionSize: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(actionSize),
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = BorderStroke(
                1.dp,
                if (enabled) Color(0xFF9CCFD5).copy(alpha = 0.72f)
                else Color(0xFFCBD5E1).copy(alpha = 0.48f)
            ),
            shadowElevation = if (enabled) 3.dp else 0.dp,
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxSize(),
            ) { content() }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (enabled) iconTint else iconTint.copy(alpha = 0.45f),
        )
    }
}
