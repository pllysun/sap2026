package edu.csuft.sap.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.ModeIcon
import edu.csuft.sap.ui.icons.title

@Composable
internal fun ModePickerHeader(showDetails: Boolean, onToggleDetails: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        val largeText = LocalDensity.current.fontScale > 1.25f
        val title = @Composable {
            Text("选择课表模式", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
        val toggle = @Composable {
            TextButton(onClick = onToggleDetails) { Text(if (showDetails) "收起说明" else "了解区别") }
        }
        if (largeText) {
            title()
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { title() }
                toggle()
            }
        }
        Text("按你的使用习惯选择课表来源", fontSize = 13.sp, lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        if (largeText) toggle()
    }
}

/** One accessible, wrapping mode selector shared by both settings entrances. */
@Composable
internal fun ModeOptionRow(
    mode: AppMode,
    selected: Boolean,
    showDetails: Boolean,
    horizontalPadding: Dp = 20.dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.055f) else colors.surface, tween(240), label = "modeRowBackground",
    )
    val border by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.38f) else colors.outlineVariant, tween(240), label = "modeRowBorder",
    )
    val subtitle = when (mode) {
        AppMode.JW -> "自动同步课表、成绩与考试"
        AppMode.WEB -> "从学校网页导入个人课表"
        AppMode.CLASS -> "选择班级，快速查看公共课表"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background).border(1.dp, border, RoundedCornerShape(20.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIcon(mode, Modifier.padding(end = 14.dp), selected = selected, size = 46.dp)
        Column(Modifier.weight(1f)) {
            Text(mode.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(subtitle, fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp))
            if (showDetails) {
                Text(when (mode) {
                    AppMode.JW -> "需绑定教务账号，密码加密保存；解绑可清除托管数据。"
                    AppMode.WEB -> "由你在学校网页登录，密码不经本软件服务器；每次导入需登录，不自动同步成绩。"
                    AppMode.CLASS -> "不需要教务账号。公共课表可能与个人的重修、英语、体育等选课安排不同。"
                }, fontSize = 12.sp, lineHeight = 19.sp, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }
        Box(
            Modifier.padding(start = 10.dp).size(20.dp)
                .background(if (selected) colors.primary else Color.Transparent, CircleShape)
                .border(1.5.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(AppIcons.Check, null, Modifier.size(13.dp), tint = colors.onPrimary)
        }
    }
}
