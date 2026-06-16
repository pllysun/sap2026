package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import edu.csuft.sap.ui.icons.AppIcons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.schedule.CustomCourse
import edu.csuft.sap.ui.theme.CoursePalette
import edu.csuft.sap.ui.theme.customCourseColor
import java.util.UUID

private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * WakeUp 风格「编辑课程」整页。initial 非空=编辑已有自建课；空=新增。
 * prefillName/Teacher/Weeks 用于“无固定时间”备注课转课表时反填已知数据（仍属新增）。
 */
@Composable
fun EditCourseScreen(
    initial: CustomCourse?,
    prefillDay: Int?,
    prefillStartNode: Int?,
    maxWeeks: Int,
    maxNodes: Int,
    onSave: (CustomCourse) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    prefillName: String? = null,
    prefillTeacher: String? = null,
    prefillWeeks: Set<Int>? = null,
    prefillLocation: String? = null,
    prefillEndNode: Int? = null,
    prefillColorIndex: Int? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: prefillName ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: prefillTeacher ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: prefillLocation ?: "") }
    var day by remember { mutableStateOf(initial?.day ?: prefillDay ?: 1) }
    var startNode by remember { mutableStateOf(initial?.startNode ?: prefillStartNode ?: 1) }
    var endNode by remember { mutableStateOf(initial?.endNode ?: prefillEndNode ?: prefillStartNode ?: 1) }
    var weeks by remember { mutableStateOf(initial?.weeks?.toSet() ?: prefillWeeks ?: (1..maxWeeks).toSet()) }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: prefillColorIndex ?: 0) }
    var customColor by remember { mutableStateOf(initial?.customColor) }
    var showColorPicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
            Text(
                if (initial == null) "添加课程" else "编辑课程",
                fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else {
                    onSave(
                        CustomCourse(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(), teacher = teacher.trim(), location = location.trim(),
                            day = day, startNode = startNode, endNode = endNode,
                            weeks = weeks.sorted(), colorIndex = colorIndex, customColor = customColor,
                        ),
                    )
                }
            }) { Icon(AppIcons.Check, "保存", tint = MaterialTheme.colorScheme.primary) }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            FieldRow("课程名", name, { name = it; nameError = false },
                placeholder = if (nameError) "必填" else "必填",
                placeholderColor = if (nameError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
            FieldRow("教室", location, { location = it }, placeholder = "非必填",
                placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FieldRow("老师", teacher, { teacher = it }, placeholder = "非必填",
                placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionLabel("上课时间", "周${dayLabels[day - 1].drop(1)} 第$startNode-$endNode 节")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLabels.forEachIndexed { i, d -> Chip(d, day == i + 1) { day = i + 1 } }
            }
            // 编辑旧课时若其节号超出当前每日节数设置，仍允许显示到原节号
            val nodeMax = maxOf(maxNodes.coerceIn(8, 16), startNode, endNode)
            SubLabel("开始节")
            NodeRow(startNode, nodeMax = nodeMax) { n -> startNode = n; if (endNode < n) endNode = n }
            SubLabel("结束节")
            NodeRow(endNode, nodeMax = nodeMax, minNode = startNode) { n -> if (n >= startNode) endNode = n }

            SectionLabel("上课周数", "快速选择（上半 1-8 周 / 下半 10-17 周）")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("上半学期", weeks == firstHalf(maxWeeks)) { weeks = firstHalf(maxWeeks) }
                Chip("下半学期", weeks == secondHalf(maxWeeks)) { weeks = secondHalf(maxWeeks) }
                Chip("单周", weeks == oddWeeks(maxWeeks)) { weeks = oddWeeks(maxWeeks) }
                Chip("双周", weeks == evenWeeks(maxWeeks)) { weeks = evenWeeks(maxWeeks) }
                Chip("全选", weeks == (1..maxWeeks).toSet()) { weeks = (1..maxWeeks).toSet() }
            }
            Spacer(Modifier.height(8.dp))
            WeekGrid(maxWeeks, weeks) { w -> weeks = if (w in weeks) weeks - w else weeks + w }

            SectionLabel("颜色", null)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoursePalette.forEachIndexed { i, c ->
                    val sel = customColor == null && colorIndex == i
                    Box(
                        Modifier.size(30.dp).background(c.container, CircleShape)
                            .clickable { colorIndex = i; customColor = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sel) Box(Modifier.size(12.dp).background(c.onContainer, CircleShape))
                    }
                }
                // 自定义取色（彩虹圈→打开取色器；选了自定义色则显示该色）
                val cc = customColor
                val swatch: Brush = if (cc != null) SolidColor(Color(cc)) else Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE),
                        Color(0xFF40C4FF), Color(0xFF7C4DFF), Color(0xFFFF4081), Color(0xFFFF5252),
                    ),
                )
                Box(
                    Modifier.size(30.dp).background(swatch, CircleShape).clickable { showColorPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cc != null) Box(Modifier.size(12.dp).background(customCourseColor(cc).onContainer, CircleShape))
                }
            }
            if (showColorPicker) ColorPickerDialog(
                initial = customColor,
                onPick = { customColor = it; showColorPicker = false },
                onDismiss = { showColorPicker = false },
            )

            if (initial != null) {
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onDelete(initial.id) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("删除课程", color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, onValue: (String) -> Unit, placeholder: String, placeholderColor: Color) {
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = placeholderColor)
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        value?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun NodeRow(selected: Int, nodeMax: Int, minNode: Int = 1, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (n in 1..nodeMax) Chip("$n", selected == n, enabled = n >= minNode) { onPick(n) }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.outline
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.background(bg, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(text, fontSize = 13.sp, color = fg, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal) }
}

@Composable
private fun WeekGrid(maxWeeks: Int, selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..maxWeeks).chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { w ->
                    val sel = w in selected
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(
                                if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(9.dp),
                            )
                            .clickable { onToggle(w) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$w", fontSize = 13.sp,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun oddWeeks(max: Int): Set<Int> = (1..max).filter { it % 2 == 1 }.toSet()
private fun evenWeeks(max: Int): Set<Int> = (1..max).filter { it % 2 == 0 }.toSet()

/** 上半学期：前八周（本校以第9周为分界）。 */
private fun firstHalf(max: Int): Set<Int> = (1..minOf(8, max)).toSet()

/** 下半学期：第 10 周到第 17 周（按总周数封顶）。 */
private fun secondHalf(max: Int): Set<Int> = if (max < 10) emptySet() else (10..minOf(17, max)).toSet()

/** 课程自定义取色器：色相 + 浓淡两滑块，实时预览课卡，输出柔和可读底色(ARGB)。 */
@Composable
private fun ColorPickerDialog(initial: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var hue by remember { mutableStateOf(initial?.let { hueOf(Color(it)) } ?: 210f) }
    var sat by remember { mutableStateOf(0.5f) }
    val container = pastel(hue, sat)
    val argb = colorToArgbLong(container)
    val cc = customCourseColor(argb)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(cc.container)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("课程预览", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cc.onContainer)
                        Text("@教室 · 老师", fontSize = 10.sp, color = cc.onContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("色相", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 0.7f, 1f) })),
                )
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("浓淡", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 由色相/浓淡生成柔和底色（保持高亮度，深字可读）。 */
private fun pastel(hue: Float, sat: Float): Color =
    Color.hsv(hue.coerceIn(0f, 360f), 0.12f + sat.coerceIn(0f, 1f) * 0.45f, 0.98f)

private fun colorToArgbLong(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

/** 从颜色反推色相(0-360)，用于编辑已有自定义色时初始化滑块。 */
private fun hueOf(c: Color): Float {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
    if (d <= 0f) return 0f
    val h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return (h + 360f) % 360f
}
