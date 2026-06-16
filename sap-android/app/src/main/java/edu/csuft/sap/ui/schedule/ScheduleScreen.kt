package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.schedule.CustomCourse
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Remark
import edu.csuft.sap.data.schedule.WeekUtil
import androidx.compose.ui.platform.LocalContext
import edu.csuft.sap.di.Graph
import edu.csuft.sap.share.ScheduleShareRenderer
import edu.csuft.sap.ui.common.ErrorRetry
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.theme.colorIndexOf
import edu.csuft.sap.ui.theme.paletteColor
import edu.csuft.sap.webview.WebImportScreen
import kotlinx.coroutines.flow.drop
import java.time.LocalDate

private sealed interface Route {
    data object None : Route
    data class Edit(
        val initial: CustomCourse?,
        val day: Int? = null,
        val startNode: Int? = null,
        val prefillName: String? = null,
        val prefillTeacher: String? = null,
        val prefillWeeks: Set<Int>? = null,
        val prefillLocation: String? = null,
        val prefillEndNode: Int? = null,
        val prefillColorIndex: Int? = null,
    ) : Route
    data object Settings : Route
    data object WebImport : Route
}

private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    vm: ScheduleViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val accounts by Graph.accountManager.accounts.collectAsState()
    var route by remember { mutableStateOf<Route>(Route.None) }
    var detail by remember { mutableStateOf<List<DisplayCourse>?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showSaveAs by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val s = state.settings
    val days = remember(s.showWeekend, s.weekStartSunday) {
        when {
            !s.showWeekend -> (1..5).toList()
            s.weekStartSunday -> listOf(7, 1, 2, 3, 4, 5, 6)
            else -> (1..7).toList()
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            val shareCtx = LocalContext.current
            TopBar(
                weekTitle = "第 ${state.selectedWeek} 周",
                onSettings = { route = Route.Settings },
                onWeekTitle = { showWeekPicker = true },
                onShare = {
                    val wk = state.selectedWeek
                    val weekCourses = state.display.filter { it.weeks.isEmpty() || it.weeks.contains(wk) }
                        .map { it.copy(isThisWeek = true) }
                    ScheduleShareRenderer.renderAndShare(
                        shareCtx,
                        title = state.activeProfileName.ifBlank { "课程表" },
                        week = wk,
                        days = days,
                        periodCount = s.dailyPeriods,
                        courses = weekCourses,
                    )
                },
            )
            WeekHeader(days, WeekUtil.datesOfWeek(s.semesterStartDate, state.selectedWeek))
            Box(Modifier.weight(1f)) {
                when {
                    // 扫描期间（含「重新扫描」）始终盖住旧数据显示动画，拿到新数据后才覆盖呈现
                    state.scanning -> RescanLoading()
                    state.loading && state.display.isEmpty() -> LoadingBox()
                    state.isLocalSource && state.display.isEmpty() ->
                        LocalImportHint { route = Route.WebImport }
                    state.error != null && state.display.isEmpty() -> ErrorRetry(state.error!!, vm::retry)
                    else -> {
                        val totalWeeks = s.totalWeeks.coerceAtLeast(1)
                        val pagerState = rememberPagerState(
                            initialPage = (state.selectedWeek - 1).coerceIn(0, totalWeeks - 1),
                            pageCount = { totalWeeks },
                        )
                        // 用户滑动翻页 → 更新选中周（drop 首个=初始页，避免误标“手动选周”）
                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.settledPage }
                                .drop(1)
                                .collect { vm.selectWeek(it + 1) }
                        }
                        // 选周/回到本周等外部改周 → 平滑翻到对应页
                        LaunchedEffect(state.selectedWeek) {
                            val target = (state.selectedWeek - 1).coerceIn(0, totalWeeks - 1)
                            if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val week = page + 1
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                if (s.semesterStartDate == null) {
                                    SetupHint { route = Route.Settings }
                                }
                                // 派生集合用 remember 缓存：仅在源数据/周/设置变化时重算，
                                // 开弹窗、翻页吸附、分钟刷新等无关重组不再重复 map/copy/filter。
                                val weekCourses = remember(state.display, week, s.showNonWeek) {
                                    state.display.mapNotNull { c ->
                                        val thisWeek = c.weeks.isEmpty() || c.weeks.contains(week)
                                        when {
                                            thisWeek -> c.copy(isThisWeek = true)
                                            s.showNonWeek -> c.copy(isThisWeek = false)
                                            else -> null
                                        }
                                    }
                                }
                                ScheduleGrid(
                                    days = days,
                                    courses = weekCourses,
                                    periodCount = s.dailyPeriods,
                                    showNowLine = s.showNowLine,
                                    rowHeight = s.rowHeightDp.dp,
                                    cardScale = s.cardScale,
                                    onCourseClick = { c -> detail = slotCourses(state.display, c, week, s.showOtherWeekInDetail) },
                                    onEmptyClick = { day, node -> route = Route.Edit(null, day, node) },
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                                )
                                // 备注里的“无固定时间”课：按本周过滤；已被排进网格（同名自建课）的不再重复显示
                                val weekRemarks = remember(state.remarks, state.display, week) {
                                    val placedNames = state.display.filter { it.isCustom }.map { it.name }.toSet()
                                    state.remarks.filter { r ->
                                        if (r.name in placedNames) return@filter false
                                        val w = WeekUtil.parseWeeks(r.weeks)
                                        w.isEmpty() || w.contains(week)
                                    }
                                }
                                if (weekRemarks.isNotEmpty()) WeekRemarks(weekRemarks) { r ->
                                    route = Route.Edit(
                                        initial = null,
                                        prefillName = r.name,
                                        prefillTeacher = r.teacher,
                                        prefillWeeks = WeekUtil.parseWeeks(r.weeks).toSet().ifEmpty { null },
                                    )
                                }
                                Spacer(Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { route = Route.Edit(null, null, null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Icon(AppIcons.Add, "添加课程") }
    }

    detail?.let { list ->
        CourseDetailSheet(
            courses = list,
            onEdit = { c ->
                // 自建课→编辑原课；教务课→把数据反填进"添加课程"表单(保存即生成自建课覆盖该时段)
                route = if (c.isCustom && c.customId != null) {
                    Route.Edit(initial = c.toCustom())
                } else {
                    Route.Edit(
                        initial = null,
                        day = c.day,
                        startNode = c.startNode,
                        prefillName = c.name,
                        prefillTeacher = c.teacher,
                        prefillWeeks = c.weeks.toSet().ifEmpty { null },
                        prefillLocation = c.location,
                        prefillEndNode = c.endNode,
                        prefillColorIndex = c.colorIndex,
                    )
                }
                detail = null
            },
            onAdd = { val c = list.first(); route = Route.Edit(null, c.day, c.startNode); detail = null },
            onDismiss = { detail = null },
        )
    }

    when (val r = route) {
        is Route.Edit -> FullScreen(onDismiss = { route = Route.None }) {
            EditCourseScreen(
                initial = r.initial,
                prefillDay = r.day,
                prefillStartNode = r.startNode,
                prefillName = r.prefillName,
                prefillTeacher = r.prefillTeacher,
                prefillWeeks = r.prefillWeeks,
                prefillLocation = r.prefillLocation,
                prefillEndNode = r.prefillEndNode,
                prefillColorIndex = r.prefillColorIndex,
                maxWeeks = s.totalWeeks,
                maxNodes = s.dailyPeriods,
                onSave = { vm.upsertCourse(it); route = Route.None },
                onDelete = { vm.deleteCourse(it); route = Route.None },
                onBack = { route = Route.None },
            )
        }
        Route.Settings -> FullScreen(onDismiss = { route = Route.None }) {
            ScheduleSettingsScreen(
                settings = s,
                accounts = accounts,
                activeAccount = state.account,
                onSwitchAccount = { Graph.accountManager.setActive(it) },
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                onSelectProfile = vm::selectProfile,
                onSaveAs = { route = Route.None; showSaveAs = true },
                onRename = { route = Route.None; showRename = true },
                onDelete = { route = Route.None; showDelete = true },
                onRescan = { route = Route.None; vm.rescan() },
                onWebImport = { route = Route.WebImport },
                onSave = vm::saveSettings,
                onBack = { route = Route.None },
            )
        }
        Route.WebImport -> FullScreen(onDismiss = { route = Route.None }) {
            WebImportScreen(onClose = { route = Route.None }, onImported = { route = Route.None })
        }
        Route.None -> Unit
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            total = s.totalWeeks,
            selected = state.selectedWeek,
            current = state.currentWeek,
            onPick = { vm.selectWeek(it); showWeekPicker = false },
            onToday = { vm.gotoCurrentWeek(); showWeekPicker = false },
            onDismiss = { showWeekPicker = false },
        )
    }
    if (showSaveAs) NameDialog("另存为新课表", "保存当前课表（教务课 + 自建课）为独立课表，重新拉取教务不会覆盖它。",
        state.activeProfileName + " 副本", "保存", { vm.saveAsNew(it); showSaveAs = false }, { showSaveAs = false })
    if (showRename) NameDialog("重命名课表", null, state.activeProfileName, "保存",
        { name -> state.activeProfileId?.let { vm.renameProfile(it, name) }; showRename = false }, { showRename = false })
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("删除课表") },
        text = { Text("确定删除「${state.activeProfileName}」？教务课表可在「重新扫描」时恢复；自定义课表删除后无法找回。") },
        confirmButton = {
            TextButton(onClick = { state.activeProfileId?.let(vm::deleteProfile); showDelete = false }) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
    )
}

/**
 * 该格全部课程：与点中课同一天、节次相交。从**全量**课程取（不受"是否显示非本周"影响），
 * 含本周与「非本周」(灰显)，并按选中周重新标记 isThisWeek。
 * 这样点一节课能看到同一时间段、不同周的所有课（如上/下半学期同时段不同课），方便对照修改。
 */
private fun slotCourses(
    all: List<DisplayCourse>,
    clicked: DisplayCourse,
    week: Int,
    showOtherWeeks: Boolean,
): List<DisplayCourse> =
    all.filter { it.day == clicked.day && it.startNode <= clicked.endNode && clicked.startNode <= it.endNode }
        .map { it.copy(isThisWeek = it.weeks.isEmpty() || it.weeks.contains(week)) }
        .filter { showOtherWeeks || it.isThisWeek } // 关掉"点课显示其他周课程"时只看本周
        .sortedByDescending { it.isThisWeek }

private fun DisplayCourse.toCustom(): CustomCourse? =
    if (isCustom && customId != null) CustomCourse(
        id = customId, name = name, teacher = teacher, location = location,
        day = day, startNode = startNode, endNode = endNode, weeks = weeks, colorIndex = colorIndex,
        customColor = customColor,
    ) else null

@Composable
private fun TopBar(
    weekTitle: String,
    onSettings: () -> Unit,
    onWeekTitle: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左：分享课表
        Icon(
            AppIcons.Share, "分享课表",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).clickable { onShare() },
        )
        // 中：周次（点开选周），居中
        Row(
            Modifier.weight(1f).clickable { onWeekTitle() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(weekTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Icon(AppIcons.DropDown, "选择周", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 右：设置（切换课表 / 切换账号 / 显示设置 都在里面）
        Icon(
            AppIcons.Settings, "课表设置",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).clickable { onSettings() },
        )
    }
}

@Composable
private fun WeekHeader(days: List<Int>, dates: List<LocalDate>?) {
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(bottom = 8.dp),
    ) {
        Spacer(Modifier.width(34.dp))
        for (day in days) {
            val date = dates?.getOrNull(day - 1)
            val isToday = date != null && date == today
            Column(
                Modifier.weight(1f).padding(horizontal = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dayNames[day - 1],
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (date != null) {
                    Text(
                        "${date.monthValue}/${date.dayOfMonth.toString().padStart(2, '0')}",
                        fontSize = 10.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable
private fun WeekPickerDialog(
    total: Int,
    selected: Int,
    current: Int?,
    onPick: (Int) -> Unit,
    onToday: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            Column {
                val rows = (1..total).chunked(5)
                rows.forEach { week ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { w ->
                            val sel = w == selected
                            val isCur = current != null && w == current
                            val past = current != null && w < current
                            val future = current != null && w > current
                            // 选中=蓝；今日所在周=过渡色(浅蓝)；已过去的周=灰；未到的周=白(描边)
                            val bg = when {
                                sel -> MaterialTheme.colorScheme.primary
                                isCur -> MaterialTheme.colorScheme.primaryContainer
                                past -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val fg = when {
                                sel -> MaterialTheme.colorScheme.onPrimary
                                isCur -> MaterialTheme.colorScheme.onPrimaryContainer
                                past -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                Modifier.weight(1f).size(44.dp)
                                    .background(bg, RoundedCornerShape(10.dp))
                                    .then(
                                        if (future) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                        else Modifier,
                                    )
                                    .clickable { onPick(w) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$w",
                                    fontSize = 13.sp,
                                    fontWeight = if (sel || isCur) FontWeight.SemiBold else FontWeight.Normal,
                                    color = fg,
                                )
                            }
                        }
                        repeat(5 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (current != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendItem(MaterialTheme.colorScheme.primary, "选中", false)
                        LegendItem(MaterialTheme.colorScheme.primaryContainer, "本周", false)
                        LegendItem(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), "已过", false)
                        LegendItem(MaterialTheme.colorScheme.surface, "未到", true)
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) TextButton(onClick = onToday) { Text("回到本周（第 $current 周）") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String, border: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
                .then(
                    if (border) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    else Modifier,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 重新扫描/初次扫描时的加载动画：旋转的刷新图标 + 文案，盖住旧数据直到新课表就绪。 */
@Composable
private fun RescanLoading() {
    val transition = rememberInfiniteTransition(label = "rescan")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "spin",
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            AppIcons.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(46.dp).rotate(angle),
        )
        Text(
            "正在重新获取各学期课表…",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            "完成后将自动覆盖更新，请稍候",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 本地「WebVPN 课表」源还没数据时的引导：去 WebView 导入。 */
@Composable
private fun LocalImportHint(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有课表数据", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "通过 WebVPN 登录学校教务，端上直接导入课表（不经服务器）",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onClick,
            modifier = Modifier.padding(top = 20.dp),
        ) { Text("导入课表", modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
    }
}

@Composable
private fun SetupHint(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("设置开始上课时间，自动定位当前周并显示日期", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        Text("去设置", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * 本周的“无固定时间”课：教务因没有固定时间格而放进备注的课（实验/实习/集中实践/选修），
 * 这里按当前周过滤后作为课卡融入课表，跟着翻周显示。点击任意一条可去“新增课程”给它设时间。
 */
@Composable
private fun WeekRemarks(remarks: List<Remark>, onClick: (Remark) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
        remarks.forEach { r ->
            val color = paletteColor(colorIndexOf(r.name))
            val teacher = r.teacher.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("、")
            val sub = listOf(r.weeks, teacher, r.clazz).filter { it.isNotBlank() }.joinToString("  ·  ")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .background(color.container, RoundedCornerShape(8.dp))
                    .clickable { onClick(r) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color.onContainer,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotBlank()) {
                        Text(sub, fontSize = 10.sp, color = color.onContainer.copy(alpha = 0.8f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Text("点击设时间", fontSize = 9.sp, color = color.onContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    hint: String?,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                hint?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(name, { name = it }, label = { Text("课表名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = if (hint != null) 10.dp else 0.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
