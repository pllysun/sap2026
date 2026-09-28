package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.schedule.*
import edu.csuft.sap.ui.icons.AppIcons
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun CalendarScreen(modifier: Modifier = Modifier, vm: ScheduleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    CalendarContent(state, modifier)
}

@Composable
internal fun CalendarContent(state: ScheduleViewModel.UiState, modifier: Modifier = Modifier, initialMonthView: Boolean = false) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(30_000) } }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var monthView by rememberSaveable { mutableStateOf(initialMonthView) }
    var previousToday by remember { mutableStateOf(today) }
    LaunchedEffect(today) {
        if (previousToday != today && selected == previousToday.toString()) {
            selected = today.toString()
            monthValue = YearMonth.from(today).toString()
        }
        previousToday = today
    }
    val date = LocalDate.parse(selected)
    val month = YearMonth.parse(monthValue)
    val colors = MaterialTheme.colorScheme
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val dayCourses = remember(state.display, state.settings, date) { coursesOnDate(state.display, state.settings, date) }
    val dayGroups = remember(dayCourses) {
        // 日历按选中日期过滤，不沿用课表页正在查看周的灰显状态。
        groupCourseArrangements(dayCourses.orEmpty().map { it.copy(isThisWeek = true) })
    }
    var detail by remember(date, state.activeProfileId, state.display) { mutableStateOf<CourseGroup?>(null) }
    val ready = state.activeProfileId != null
    val monthDays = remember(state.display, state.settings, month) { calendarMonthDays(state.display, state.settings, month) }
    val monthIndex = remember(monthDays) { monthDays.orEmpty().associateBy { it.date } }
    fun goToday() { selected = today.toString(); monthValue = YearMonth.from(today).toString() }

    Column(modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("日历", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(state.activeProfileName.ifBlank { "当前课表" }, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (if (monthView) month != YearMonth.from(today) else date != today) {
                TextButton(onClick = { goToday(); monthView = false }) { Text("回到今天") }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.45f)).padding(4.dp)) {
            listOf(false to "今日", true to "月历").forEach { (isMonth, title) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (monthView == isMonth) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { monthView = isMonth; if (!isMonth) goToday() }
                    .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(title, color = if (monthView == isMonth) colors.primary else colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (monthView) monthValue = month.minusMonths(1).toString() else selected = date.minusDays(1).toString()
                    }) { Icon(AppIcons.ChevronRight, "上一${if (monthView) "月" else "天"}", Modifier.rotate(180f)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (monthView) "${month.year}年${month.monthValue}月" else date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (!monthView && ready && dayCourses != null) Text(
                            if (dayCourses.isEmpty()) "休 · 当日无课" else "${calendarPeriodCount(dayCourses)} 节课 · ${dayGroups.size} 项课程",
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 5.dp))
                    }
                    IconButton(onClick = {
                        if (monthView) monthValue = month.plusMonths(1).toString() else selected = date.plusDays(1).toString()
                    }) { Icon(AppIcons.ChevronRight, "下一${if (monthView) "月" else "天"}") }
                }
            }
            when {
                !ready -> item {
                    CalendarMessage(if (state.loading || state.scanning) "正在读取课表…" else "还没有课表，请先在课表页选择或导入")
                }
                dayCourses == null -> item { CalendarMessage("请先在课表设置中补充开学日期") }
                monthView -> item {
                    Surface(modifier = Modifier.pointerInput(month, swipeThreshold) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onDragCancel = { distance = 0f },
                            onDragEnd = {
                                monthValue = monthAfterSwipe(month, distance, swipeThreshold).toString()
                                distance = 0f
                            },
                            onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                        )
                    }, shape = RoundedCornerShape(20.dp), color = colors.surface) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                    Box(Modifier.weight(1f).padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                            calendarMonthCells(month).chunked(7).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEachIndexed { index, cell ->
                                        val count = monthIndex[cell]?.courseCount ?: 0
                                        val isToday = cell == today
                                        val rest = cell != null && monthIndex[cell]?.courseCount == 0
                                        val joinsLeft = rest && index > 0 && week[index - 1]?.let { monthIndex[it]?.courseCount == 0 } == true
                                        val joinsRight = rest && index < 6 && week[index + 1]?.let { monthIndex[it]?.courseCount == 0 } == true
                                        val shape = RoundedCornerShape(
                                            topStart = if (joinsLeft) 0.dp else 14.dp, bottomStart = if (joinsLeft) 0.dp else 14.dp,
                                            topEnd = if (joinsRight) 0.dp else 14.dp, bottomEnd = if (joinsRight) 0.dp else 14.dp)
                                        Column(Modifier.weight(1f).padding(vertical = 4.dp).clip(shape)
                                            .background(if (rest) colors.tertiaryContainer.copy(alpha = 0.32f) else androidx.compose.ui.graphics.Color.Transparent)
                                            .clickable(enabled = cell != null) { selected = cell.toString(); monthView = false }
                                            .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp))
                                                .background(if (isToday) colors.primary else androidx.compose.ui.graphics.Color.Transparent),
                                                contentAlignment = Alignment.Center) {
                                            Text(cell?.dayOfMonth?.toString() ?: "", fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isToday) colors.onPrimary else colors.onSurface)
                                            }
                                            Text(if (cell == null) "" else if (count == 0) "休" else "${count}课",
                                                fontSize = 11.sp, color = if (count > 0) colors.primary else colors.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                dayCourses.isEmpty() -> item { CalendarMessage("休", "这一天没有课程安排") }
                else -> items(dayGroups) { group ->
                    val course = group.first
                    Surface(onClick = { detail = group }, shape = RoundedCornerShape(20.dp), color = colors.surface) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.widthIn(min = 60.dp)) {
                                Text(Periods.period(course.startNode)?.start ?: "—", color = colors.primary,
                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(Periods.period(course.endNode)?.end ?: "—", color = colors.onSurfaceVariant,
                                    fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(course.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
                                Text("第 ${course.startNode}–${course.endNode} 节", style = MaterialTheme.typography.bodySmall,
                                    color = colors.primary, modifier = Modifier.padding(top = 6.dp))
                                Text("教室 · ${group.locationSummary}", style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                                Text("教师 · ${group.teacherSummary}", style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                if (group.arrangements.size > 1) Text("${group.arrangements.size} 组授课安排 · 点击查看",
                                    style = MaterialTheme.typography.labelMedium, color = colors.primary,
                                    modifier = Modifier.padding(top = 10.dp))
                            }
                            Icon(AppIcons.ChevronRight, "查看课程详情", tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp).align(Alignment.CenterVertically))
                        }
                    }
                }
            }
        }
    }
    detail?.let { group -> CalendarCourseDetails(group, date) { detail = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarCourseDetails(group: CourseGroup, date: LocalDate, onDismiss: () -> Unit) {
    val course = group.first
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)) +
                    " · 第 ${course.startNode}–${course.endNode} 节", color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                Text("${Periods.period(course.startNode)?.start ?: "—"}–${Periods.period(course.endNode)?.end ?: "—"}",
                    color = colors.primary, modifier = Modifier.padding(top = 4.dp))
                if (group.arrangements.size > 1) Text("${group.arrangements.size} 组授课安排，请按实际选课确认",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp))
            }
            items(group.arrangements) { arrangement ->
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceVariant.copy(alpha = 0.4f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("教师 · ${arrangement.teacher.ifBlank { "待定" }}", fontWeight = FontWeight.Medium)
                        Text("教室 · ${arrangement.location.ifBlank { "待定" }}", color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMessage(title: String, detail: String? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
