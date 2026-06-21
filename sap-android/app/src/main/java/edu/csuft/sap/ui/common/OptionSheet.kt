package edu.csuft.sap.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全 App 统一的「选项选择」底部弹出（圆角白、简约、滑入动画、选中高亮 + 对勾）。
 * 替代各处朴素的 AlertDialog 列表选择（提前提醒、总周数、字号、周次、学年学期…）。
 *
 * 选项可附 [副标题][subLabel] 与 [角标][badge]（如「本学期」）。点选即回调并关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    subLabel: (T) -> String? = { null },
    badge: (T) -> String? = { null },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            itemsIndexed(options) { _, opt ->
                OptionRow(
                    label = label(opt),
                    sub = subLabel(opt),
                    badge = badge(opt),
                    selected = opt == selected,
                    onClick = { onPick(opt); onDismiss() },
                )
            }
        }
        Spacer(Modifier.padding(bottom = 14.dp))
    }
}

@Composable
private fun OptionRow(label: String, sub: String?, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                label, fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (!sub.isNullOrBlank()) {
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (!badge.isNullOrBlank()) {
            Text(
                badge, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, "已选", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
