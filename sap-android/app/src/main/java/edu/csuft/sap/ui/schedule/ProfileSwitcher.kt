package edu.csuft.sap.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import edu.csuft.sap.ui.icons.AppIcons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.schedule.ProfileKind

private const val CUSTOM_YEAR = "__custom__"

/** termValue "2024-2025-2" → ("2024-2025","2")；非学期格式（含自定义旧数据）返回 null。 */
private fun parseTerm(termValue: String?): Pair<String, String>? {
    if (termValue == null) return null
    val parts = termValue.split("-")
    if (parts.size >= 3 && parts[0].length == 4 && parts[1].length == 4) {
        return ("${parts[0]}-${parts[1]}") to parts.last()
    }
    return null
}

private fun semLabel(sem: String): String = when (sem) {
    "1" -> "第一学期"
    "2" -> "第二学期"
    "3" -> "第三学期"
    else -> "第 $sem 学期"
}

private sealed interface Level {
    data object Years : Level
    data class Terms(val year: String) : Level
    data class Schedules(val year: String, val sem: String) : Level
}

/**
 * 切换课表 · 三级逐级下钻：学年 → 学期 → 课表（教务课表 + 自定义副本）。
 * 顶部返回逐级回退，最顶层返回退出面板（[onBack]）。选中课表即 [onSelect] 并退出。
 */
@Composable
fun ProfileSwitcher(
    profiles: List<ScheduleViewModel.ProfileMeta>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var level by remember { mutableStateOf<Level>(Level.Years) }

    // 系统返回键逐级回退，最顶层退出面板（与顶部返回箭头一致）
    BackHandler {
        level = when (val l = level) {
            Level.Years -> { onBack(); Level.Years }
            is Level.Terms -> Level.Years
            is Level.Schedules -> Level.Terms(l.year)
        }
    }

    val parsed = remember(profiles) { profiles.map { it to parseTerm(it.termValue) } }
    val years = remember(profiles) { parsed.mapNotNull { it.second?.first }.distinct().sortedDescending() }
    val orphans = remember(profiles) { parsed.filter { it.second == null }.map { it.first } }
    // 当前激活课表落在哪个学年/学期，给上层菜单打“当前”提示
    val activePair = parsed.firstOrNull { it.first.id == activeProfileId }?.second
    val activeYear = activePair?.first ?: orphans.firstOrNull { it.id == activeProfileId }?.let { CUSTOM_YEAR }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val title = when (val l = level) {
            Level.Years -> "切换课表"
            is Level.Terms -> if (l.year == CUSTOM_YEAR) "自定义课表" else "${l.year} 学年"
            is Level.Schedules -> "${l.year} · ${semLabel(l.sem)}"
        }
        TopBar(title) {
            level = when (val l = level) {
                Level.Years -> { onBack(); Level.Years }
                is Level.Terms -> Level.Years
                is Level.Schedules -> Level.Terms(l.year)
            }
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Card {
                when (val l = level) {
                    Level.Years -> {
                        years.forEachIndexed { i, y ->
                            if (i > 0) RowDivider()
                            DrillRow("$y 学年", isCurrent = y == activeYear) { level = Level.Terms(y) }
                        }
                        if (orphans.isNotEmpty()) {
                            if (years.isNotEmpty()) RowDivider()
                            DrillRow("自定义课表", isCurrent = activeYear == CUSTOM_YEAR) { level = Level.Terms(CUSTOM_YEAR) }
                        }
                    }
                    is Level.Terms -> {
                        if (l.year == CUSTOM_YEAR) {
                            orphans.forEachIndexed { i, p ->
                                if (i > 0) RowDivider()
                                LeafRow(p, p.id == activeProfileId, onSelect)
                            }
                        } else {
                            val sems = parsed.filter { it.second?.first == l.year }
                                .mapNotNull { it.second?.second }.distinct().sortedDescending()
                            sems.forEachIndexed { i, s ->
                                if (i > 0) RowDivider()
                                val cur = activePair?.first == l.year && activePair.second == s
                                DrillRow(semLabel(s), isCurrent = cur) { level = Level.Schedules(l.year, s) }
                            }
                        }
                    }
                    is Level.Schedules -> {
                        val list = parsed.filter { it.second?.first == l.year && it.second?.second == l.sem }.map { it.first }
                        list.forEachIndexed { i, p ->
                            if (i > 0) RowDivider()
                            LeafRow(p, p.id == activeProfileId, onSelect)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp)
        .background(MaterialTheme.colorScheme.outlineVariant))
}

/** 可下钻一级的行：右侧带 ›，含当前激活则标“当前”。 */
@Composable
private fun DrillRow(title: String, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (isCurrent) Text("当前", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp))
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 叶子行：具体课表，点选即切换；激活打勾、自定义带标签。 */
@Composable
private fun LeafRow(p: ScheduleViewModel.ProfileMeta, selected: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(p.id) }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.name, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (p.kind == ProfileKind.CUSTOM) Text(
            "自定义", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        if (selected) Icon(AppIcons.Check, "当前", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
    }
}
