package com.example.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.settings.appText

private data class DailyServiceNavItem(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val imageRes: Int,
    val glow: Color,
    val action: () -> Unit
)

/**
 * V81 — simple realistic 3D medical swipe launcher used inside every daily workspace.
 *
 * The old text-button strip is intentionally gone. Each service is now a bright
 * 3D tile in a horizontal carousel, so the user can swipe right/left between
 * daily tasks without leaving the visual language of the home dashboard.
 */
@Composable
fun DailyServicesNavBar(
    active: String? = null,
    onWorklist: (() -> Unit)? = null,
    onCatalog: () -> Unit,
    onQuickImage: () -> Unit,
    onOrder: () -> Unit,
    onCustomers: () -> Unit,
    onScan: () -> Unit
) {
    val items = buildList {
        if (onWorklist != null) add(
            DailyServiceNavItem(
                id = "worklist",
                titleAr = "متابعة الطلبات",
                titleEn = "Order tracking",
                imageRes = R.drawable.staff_search_3d,
                glow = Color(0xFF38BDF8),
                action = onWorklist
            )
        )
        add(DailyServiceNavItem(
            id = "catalog",
            titleAr = "كتالوج التحاليل",
            titleEn = "Test catalog",
            imageRes = R.drawable.staff_tests_3d,
            glow = Color(0xFF00D9FF),
            action = onCatalog
        ))
        add(DailyServiceNavItem(
            id = "quick_image",
            titleAr = "صورة سريعة",
            titleEn = "Quick image",
            imageRes = R.drawable.staff_quick_image_3d,
            glow = Color(0xFF9B6CFF),
            action = onQuickImage
        ))
        add(DailyServiceNavItem(
            id = "order",
            titleAr = "طلب جديد",
            titleEn = "New order",
            imageRes = R.drawable.staff_new_order_3d,
            glow = Color(0xFF2DE3A7),
            action = onOrder
        ))
        add(DailyServiceNavItem(
            id = "customers",
            titleAr = "سجلات العملاء",
            titleEn = "Customer records",
            imageRes = R.drawable.staff_customers_3d,
            glow = Color(0xFF42A5FF),
            action = onCustomers
        ))
        add(DailyServiceNavItem(
            id = "scan",
            titleAr = "QR / صورة",
            titleEn = "QR / Image",
            imageRes = R.drawable.staff_qr_3d,
            glow = Color(0xFFFFB23E),
            action = onScan
        ))
    }

    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        val index = items.indexOfFirst { it.id == active }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F9FC))
            .border(
                BorderStroke(1.dp, Color(0xFFDCE6EF)),
                RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
            )
            .padding(vertical = 9.dp)
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                Mini3DSwipeServiceCard(
                    item = item,
                    selected = active == item.id
                )
            }
        }
    }
}

@Composable
private fun Mini3DSwipeServiceCard(
    item: DailyServiceNavItem,
    selected: Boolean
) {
LabeledIconAction(
        label = appText(item.titleAr, item.titleEn),
        onClick = item.action,
        modifier = Modifier.width(108.dp)
    ) {
        Image(
            painter = painterResource(item.imageRes),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            contentScale = ContentScale.Fit
        )
    }
}
