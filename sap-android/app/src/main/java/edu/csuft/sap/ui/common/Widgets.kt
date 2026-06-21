package edu.csuft.sap.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import edu.csuft.sap.ui.icons.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
    }
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("重试") }
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
    }
}

/** 顶部标题栏（简约白）。 */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 同步条：左侧显示「上次同步时间 / 未同步 / 同步中 / 错误」，右侧「同步」按钮。
 * 进页面只读缓存，刷新靠用户点这里，避免频繁打教务触发风控。
 */
@Composable
fun SyncBar(syncedAt: Long?, syncing: Boolean, error: String?, onSync: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isErr = error != null && !syncing
        val text = when {
            syncing -> "同步中…"
            error != null -> error
            syncedAt == null -> "未同步，点右侧获取最新数据"
            else -> "上次同步 ${formatSyncTime(syncedAt)}"
        }
        Text(
            text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !syncing) { onSync() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(AppIcons.Refresh, "同步", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Text(
                "同步", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun formatSyncTime(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
}

/**
 * 扁平白卡：白底、圆角、无投影。
 * 传 [onClick] 即把**整张卡**变为按钮：按下时整卡背景渐变上浅色、松手淡出（无 ripple 灰条）。
 */
@Composable
fun SapCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val surface = MaterialTheme.colorScheme.surface
    val target = if (onClick != null && pressed)
        lerp(surface, MaterialTheme.colorScheme.onSurface, 0.06f) else surface
    val bg by animateColorAsState(target, label = "cardPress")
    var m = modifier.fillMaxWidth().clip(shape).background(bg)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    Column(m.padding(16.dp), content = content)
}

/**
 * 列表行/按钮的「整块按下高亮」Modifier：按下时整行背景渐变上一层浅色、松手淡出，
 * 替代默认 ripple（避免只在文字那一行出现一条灰条）。
 * 用法：放在 `fillMaxWidth()` 之后、`padding()` 之前，使整行（含内边距）成为受效区。
 */
@Composable
fun Modifier.pressFade(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = if (pressed && enabled)
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, 0.08f)
    else Color.Transparent
    val bg by animateColorAsState(target, label = "pressFade")
    return this
        .background(bg)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}
