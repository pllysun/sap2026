package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.ui.theme.customCourseColor
import edu.csuft.sap.ui.theme.paletteColor
import java.time.LocalDate
import java.time.LocalTime

private val dividerHeight = 18.dp
private val timeColWidth = 34.dp
private val hairline = Color(0x12000000)
private val nonWeekBg = Color(0xFFF0F1F3)
private val nonWeekFg = Color(0xFF9AA0A6)

/** 节顶端 y 偏移（含午休/晚分隔的累加）。返回 size=count 的列表，下标 node-1。 */
private fun nodeTops(count: Int, rowHeight: Dp): List<Dp> {
    val list = ArrayList<Dp>(count)
    var y = 0.dp
    for (node in 1..count) {
        list.add(y)
        y += rowHeight
        if (node < count && (node == Periods.LUNCH_AFTER_NODE || node == Periods.EVENING_AFTER_NODE)) y += dividerHeight
    }
    return list
}

/**
 * WakeUp 风格按节课表网格。
 * - 左侧时间列：大节号 + 起止时间；午休/晚分隔行整宽。
 * - 课卡按 startNode..endNode 跨行；非本周课灰显；同格多课时主卡画右下折角。
 */
@Composable
fun ScheduleGrid(
    days: List<Int>,
    courses: List<DisplayCourse>,
    periodCount: Int,
    showNowLine: Boolean,
    rowHeight: Dp,
    cardScale: Float,
    onCourseClick: (DisplayCourse) -> Unit,
    onEmptyClick: (day: Int, startNode: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = periodCount.coerceIn(8, 16)
    val tops = remember(count, rowHeight) { nodeTops(count, rowHeight) }
    val totalH = tops[count - 1] + rowHeight

    Box(modifier.fillMaxWidth().height(totalH)) {
        // 背景层：时间列 + 空格 + 午休/晚分隔。
        // 整层一个 detectTapGestures 按坐标命中“空格”，取代每格一个 clickable（省去上百个交互节点）。
        Column(
            Modifier.fillMaxSize().pointerInput(days, count, rowHeight) {
                detectTapGestures { off ->
                    val timeW = timeColWidth.toPx()
                    if (off.x < timeW || days.isEmpty()) return@detectTapGestures
                    val colW = (size.width - timeW) / days.size
                    val col = (((off.x - timeW) / colW).toInt()).coerceIn(0, days.size - 1)
                    val rh = rowHeight.toPx()
                    for (node in 1..count) {
                        val topPx = tops[node - 1].toPx()
                        if (off.y >= topPx && off.y < topPx + rh) {
                            onEmptyClick(days[col], node)
                            break
                        }
                    }
                }
            },
        ) {
            for (node in 1..count) {
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    TimeCell(node, rowHeight)
                    for (day in days) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .drawBehind {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(hairline, Offset(0f, h), Offset(w, h), 1f)
                                    drawLine(hairline, Offset(w, 0f), Offset(w, h), 1f)
                                },
                        )
                    }
                }
                if (node < count && node == Periods.LUNCH_AFTER_NODE) DividerRow("午休")
                if (node < count && node == Periods.EVENING_AFTER_NODE) DividerRow("晚")
            }
        }
        // 每分钟刷新一次，让“此刻”线与正在上课高亮自动更新
        var nowTick by remember { mutableStateOf(LocalTime.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowTick = LocalTime.now()
                kotlinx.coroutines.delay(60_000L)
            }
        }
        val now = nowTick
        val todayDay = LocalDate.now().dayOfWeek.value

        // 课卡层：与背景同样的列结构，卡片在各天列内按偏移绝对定位
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.width(timeColWidth))
            for (day in days) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    DayCards(courses.filter { it.day == day }, day, todayDay, now, tops, rowHeight, cardScale, onCourseClick)
                }
            }
        }

        // 当前时间线（“此刻”）：设置开启 + now 落在上课时段内时才显示
        val nowY = if (showNowLine) nowLineY(now, count, tops, rowHeight) else null
        if (nowY != null) {
            val lineColor = MaterialTheme.colorScheme.error
            Box(
                Modifier.fillMaxWidth().padding(start = timeColWidth)
                    .offset(y = nowY).height(1.5.dp).background(lineColor),
            )
            Box(
                Modifier.offset(x = timeColWidth - 3.dp, y = nowY - 2.5.dp)
                    .size(6.dp).background(lineColor, CircleShape),
            )
        }
    }
}

/** 当前时刻在网格中的 y 偏移；不在任何上课时段内（早于第1节/晚于末节）返回 null。 */
private fun nowLineY(now: LocalTime, count: Int, tops: List<Dp>, rowHeight: Dp): Dp? {
    val first = parseTime(Periods.period(1)?.start) ?: return null
    val last = parseTime(Periods.period(count)?.end) ?: return null
    if (now.isBefore(first) || now.isAfter(last)) return null
    for (node in 1..count) {
        val s = parseTime(Periods.period(node)?.start) ?: continue
        val e = parseTime(Periods.period(node)?.end) ?: continue
        if (!now.isAfter(e)) {
            return if (now.isBefore(s)) {
                tops[node - 1] // 处在两节之间的休息时段 → 落在下一节顶端
            } else {
                val span = (e.toSecondOfDay() - s.toSecondOfDay()).coerceAtLeast(1)
                val frac = (now.toSecondOfDay() - s.toSecondOfDay()).toFloat() / span
                tops[node - 1] + rowHeight * frac.coerceIn(0f, 1f)
            }
        }
    }
    return null
}

private fun parseTime(s: String?): LocalTime? =
    s?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } }

/** 课程此刻是否正在上（今日 + 本周 + 当前时间落在其节次时段内）。 */
private fun isOngoing(course: DisplayCourse, day: Int, todayDay: Int, now: LocalTime): Boolean {
    if (day != todayDay || !course.isThisWeek) return false
    val s = parseTime(Periods.period(course.startNode)?.start) ?: return false
    val e = parseTime(Periods.period(course.endNode)?.end) ?: return false
    return !now.isBefore(s) && now.isBefore(e)
}

@Composable
private fun TimeCell(node: Int, rowHeight: Dp) {
    val p = Periods.period(node)
    Column(
        Modifier.width(timeColWidth).height(rowHeight),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$node", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        if (p != null) {
            Text(p.start, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 10.sp)
            Text(p.end, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 10.sp)
        }
    }
}

@Composable
private fun DividerRow(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(dividerHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCards(
    dayCourses: List<DisplayCourse>,
    day: Int,
    todayDay: Int,
    now: LocalTime,
    tops: List<Dp>,
    rowHeight: Dp,
    cardScale: Float,
    onCourseClick: (DisplayCourse) -> Unit,
) {
    // 本周课优先占格；与已占课重叠的课不再单独画，但给主卡记一个折角
    val ordered = dayCourses.sortedWith(
        compareByDescending<DisplayCourse> { it.isThisWeek }.thenBy { it.startNode },
    )
    val placed = ArrayList<Pair<DisplayCourse, Int>>()
    for (c in ordered) {
        val idx = placed.indexOfFirst { overlaps(it.first, c) }
        if (idx < 0) placed.add(c to 0) else placed[idx] = placed[idx].first to (placed[idx].second + 1)
    }
    for ((c, conflicts) in placed) {
        val top = tops[(c.startNode - 1).coerceIn(0, tops.size - 1)]
        val bottom = tops[(c.endNode - 1).coerceIn(0, tops.size - 1)] + rowHeight
        CourseCard(c, top, bottom - top, conflicts > 0, isOngoing(c, day, todayDay, now), cardScale) { onCourseClick(c) }
    }
}

private fun overlaps(a: DisplayCourse, b: DisplayCourse): Boolean =
    a.startNode <= b.endNode && b.startNode <= a.endNode

@Composable
private fun CourseCard(course: DisplayCourse, top: Dp, height: Dp, conflict: Boolean, ongoing: Boolean, cardScale: Float, onClick: () -> Unit) {
    val color = course.customColor?.let { customCourseColor(it) } ?: paletteColor(course.colorIndex)
    val bg = if (course.isThisWeek) color.container else nonWeekBg
    val fg = if (course.isThisWeek) color.onContainer else nonWeekFg
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = top)
            .height(height)
            .padding(1.5.dp)
            .background(bg, shape)
            .then(if (ongoing) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp)) {
            Text(
                course.name,
                fontSize = (11f * cardScale).sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                lineHeight = (13f * cardScale).sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    "@" + course.location,
                    fontSize = (9f * cardScale).sp,
                    color = fg.copy(alpha = 0.85f),
                    lineHeight = (11f * cardScale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (course.teacher.isNotBlank()) {
                Text(
                    course.teacher,
                    fontSize = (9f * cardScale).sp,
                    color = fg.copy(alpha = 0.85f),
                    lineHeight = (11f * cardScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (conflict) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .height(14.dp)
                    .width(14.dp)
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(w, h)
                            lineTo(0f, h)
                            lineTo(w, 0f)
                            close()
                        }
                        drawPath(path, fg.copy(alpha = 0.9f))
                    },
            )
        }
    }
}
