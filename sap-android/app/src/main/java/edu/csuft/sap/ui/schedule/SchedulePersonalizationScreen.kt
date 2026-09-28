package edu.csuft.sap.ui.schedule

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.ScheduleAppearanceLimits
import edu.csuft.sap.data.schedule.ScheduleBackgroundStore
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.data.schedule.WeekUtil
import edu.csuft.sap.data.schedule.withDefaultPersonalization
import edu.csuft.sap.ui.icons.AppIcons
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val scheduleDayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 上半屏实时课表预览，下半屏分类调整个性化参数。 */
@Composable
fun SchedulePersonalizationScreen(
    settings: ScheduleSettings,
    courses: List<DisplayCourse>,
    selectedWeek: Int,
    onSave: (ScheduleSettings) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodingImage by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                decodingImage = true
                runCatching { ScheduleBackgroundStore.decodeSelected(context, uri) }
                    .onSuccess { cropBitmap = it }
                    .onFailure { Toast.makeText(context, "图片读取失败，请换一张重试", Toast.LENGTH_SHORT).show() }
                decodingImage = false
            }
        }
    }

    fun commit(next: ScheduleSettings) {
        draft = next
        onSave(next)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
            Text("个性化", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showResetConfirm = true }) { Text("恢复默认") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            SchedulePreview(
                settings = draft,
                sourceCourses = courses,
                initialWeek = selectedWeek,
            )
            if (decodingImage) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }

        HorizontalDivider()
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppearanceGroup("课程颜色") {
                    AppearanceSlider(
                        title = "默认课表颜色浓淡",
                        value = draft.colorIntensityPercent.toFloat(),
                        range = ScheduleAppearanceLimits.COLOR_INTENSITY,
                        valueText = "${draft.colorIntensityPercent}%" + if (draft.colorIntensityPercent == 100) " · 默认" else "",
                        onValueChange = { draft = draft.copy(cardColorIntensityPercent = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0% · 淡", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("200% · 浓", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(
                            value = draft.colorIntensityAffectsCustom,
                            role = Role.Checkbox,
                            onValueChange = { commit(draft.copy(colorIntensityAffectsCustom = it)) },
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("同时调整自建课程", fontSize = 14.sp)
                            Text("自己创建的课程也跟随调整", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = draft.colorIntensityAffectsCustom, onCheckedChange = null)
                    }
                }
            }
            item {
                AppearanceGroup("布局与显示") {
                    AppearanceSlider(
                        title = "课表高度",
                        value = draft.rowHeightDp.toFloat(),
                        range = ScheduleAppearanceLimits.ROW_HEIGHT,
                        valueText = "${draft.rowHeightDp} dp",
                        onValueChange = { draft = draft.copy(rowHeight = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSlider(
                        title = "侧边栏宽度",
                        value = draft.sidebarWidthDp.toFloat(),
                        range = ScheduleAppearanceLimits.SIDEBAR_WIDTH,
                        valueText = "${draft.sidebarWidthDp} dp",
                        onValueChange = { draft = draft.copy(sidebarWidth = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSlider(
                        title = "顶部表头高度",
                        value = draft.headerHeightDp.toFloat(),
                        range = ScheduleAppearanceLimits.HEADER_HEIGHT,
                        valueText = "${draft.headerHeightDp} dp",
                        onValueChange = { draft = draft.copy(headerHeight = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSwitch("显示周末", draft.showWeekend) {
                        commit(draft.copy(showWeekend = it))
                    }
                    AppearanceSwitch("显示非本周课程", draft.showNonWeek) {
                        commit(draft.copy(showNonWeek = it))
                    }
                }
            }

            item {
                AppearanceGroup("文字") {
                    AppearanceSlider(
                        title = "课程卡字号",
                        value = draft.cardFontSizeSp.toFloat(),
                        range = ScheduleAppearanceLimits.CARD_FONT_SIZE,
                        valueText = "${draft.cardFontSizeSp} sp",
                        onValueChange = { draft = draft.copy(cardFontSize = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSlider(
                        title = "文字缩放比例",
                        value = (draft.cardScale * 100f),
                        range = ScheduleAppearanceLimits.TEXT_SCALE,
                        valueText = "${(draft.cardScale * 100).roundToInt()}%",
                        onValueChange = { draft = draft.copy(cardTextScale = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSwitch("隐藏上课地点", draft.hideLocation) {
                        commit(draft.copy(hideLocation = it))
                    }
                    AppearanceSwitch("隐藏授课老师", draft.hideTeacher) {
                        commit(draft.copy(hideTeacher = it))
                    }
                    AppearanceSwitch("文字水平居中", draft.centerTextHorizontally) {
                        commit(draft.copy(centerTextHorizontally = it))
                    }
                    AppearanceSwitch("文字垂直居中", draft.centerTextVertically) {
                        commit(draft.copy(centerTextVertically = it))
                    }
                }
            }

            item {
                AppearanceGroup("课程卡") {
                    Text("边框样式", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to "无", 1 to "实线", 2 to "虚线").forEach { (value, label) ->
                            FilterChip(
                                selected = draft.borderStyle == value,
                                onClick = { commit(draft.copy(cardBorderStyle = value)) },
                                label = { Text(label) },
                            )
                        }
                    }
                    AppearanceSlider(
                        title = "圆角半径",
                        value = draft.cornerRadiusDp.toFloat(),
                        range = ScheduleAppearanceLimits.CORNER_RADIUS,
                        valueText = "${draft.cornerRadiusDp} dp",
                        onValueChange = { draft = draft.copy(cardCornerRadius = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSlider(
                        title = "内部填充",
                        value = draft.innerPaddingDp.toFloat(),
                        range = ScheduleAppearanceLimits.INNER_PADDING,
                        valueText = "${draft.innerPaddingDp} dp",
                        onValueChange = { draft = draft.copy(cardInnerPadding = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSlider(
                        title = "外部间距",
                        value = draft.outerSpacingDp.toFloat(),
                        range = ScheduleAppearanceLimits.OUTER_SPACING,
                        valueText = "${draft.outerSpacingDp} dp",
                        onValueChange = { draft = draft.copy(cardOuterSpacing = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                    AppearanceSlider(
                        title = "不透明度",
                        value = draft.opacityFraction * 100f,
                        range = ScheduleAppearanceLimits.OPACITY,
                        valueText = "${(draft.opacityFraction * 100).roundToInt()}%",
                        onValueChange = { draft = draft.copy(cardOpacity = it.roundToInt()) },
                        onValueChangeFinished = { onSave(draft) },
                    )
                }
            }

            item {
                AppearanceGroup("课表背景") {
                    Text(
                        "从相册选择后可拖动、缩放并裁剪；只会读取你选中的图片。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    draft.backgroundImagePath?.takeIf { File(it).isFile }?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "当前课表背景",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 10.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !decodingImage,
                        ) { Text(if (draft.backgroundImagePath.isNullOrBlank()) "选择图片" else "更换图片") }
                        if (!draft.backgroundImagePath.isNullOrBlank()) {
                            TextButton(onClick = {
                                ScheduleBackgroundStore.deleteOwned(context, draft.backgroundImagePath)
                                commit(draft.copy(backgroundImagePath = null))
                            }) { Text("移除背景", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    cropBitmap?.let { bitmap ->
        BackgroundCropDialog(
            bitmap = bitmap,
            onCancel = { cropBitmap = null },
            onSaved = { path ->
                ScheduleBackgroundStore.deleteOwned(context, draft.backgroundImagePath)
                commit(draft.copy(backgroundImagePath = path))
                cropBitmap = null
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复默认设置") },
            text = { Text("将恢复课表外观、布局和背景设置，不影响开学日期与学期周数。") },
            confirmButton = {
                TextButton(onClick = {
                    ScheduleBackgroundStore.deleteOwned(context, draft.backgroundImagePath)
                    commit(draft.withDefaultPersonalization())
                    showResetConfirm = false
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SchedulePreview(
    settings: ScheduleSettings,
    sourceCourses: List<DisplayCourse>,
    initialWeek: Int,
) {
    val totalWeeks = settings.totalWeeks.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = (initialWeek - 1).coerceIn(0, totalWeeks - 1),
        pageCount = { totalWeeks },
    )
    val allCourses = sourceCourses.ifEmpty { previewCourses }
    val days = remember(settings.showWeekend, settings.weekStartSunday) {
        when {
            !settings.showWeekend -> (1..5).toList()
            settings.weekStartSunday -> listOf(7, 1, 2, 3, 4, 5, 6)
            else -> (1..7).toList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("效果预览", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                "第 ${pagerState.currentPage + 1} 周 · 左右滑动切换",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ScheduleBackgroundLayer(settings, Modifier.fillMaxSize())
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val week = page + 1
                val weekCourses = remember(allCourses, week, settings.showNonWeek) {
                    allCourses.mapNotNull { course ->
                        val thisWeek = course.weeks.isEmpty() || week in course.weeks
                        when {
                            thisWeek -> course.copy(isThisWeek = true)
                            settings.showNonWeek -> course.copy(isThisWeek = false)
                            else -> null
                        }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    ScheduleWeekHeader(
                        days = days,
                        dates = WeekUtil.datesOfWeek(settings.semesterStartDate, week),
                        settings = settings,
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ScheduleGrid(
                            days = days,
                            courses = weekCourses,
                            periodCount = settings.dailyPeriods,
                            showNowLine = false,
                            settings = settings,
                            weekDates = WeekUtil.datesOfWeek(settings.semesterStartDate, week),
                            onCourseClick = {},
                            onEmptyClick = { _, _ -> },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScheduleBackgroundLayer(settings: ScheduleSettings, modifier: Modifier = Modifier) {
    val path = settings.backgroundImagePath
    if (!path.isNullOrBlank() && File(path).isFile) {
        Box(modifier) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 轻微白蒙层保证网格、时间与低透明课程卡仍然易读。
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)))
        }
    }
}

@Composable
internal fun ScheduleWeekHeader(
    days: List<Int>,
    dates: List<LocalDate>?,
    settings: ScheduleSettings,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Row(
        modifier.fillMaxWidth().height(settings.headerHeightDp.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(settings.sidebarWidthDp.dp))
        for (day in days) {
            val date = dates?.getOrNull(day - 1)
            val isToday = date != null && date == today
            Column(
                Modifier.weight(1f).padding(horizontal = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    scheduleDayNames[day - 1],
                    fontSize = (12f * settings.cardScale).sp,
                    fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (date != null) {
                    Text(
                        "${date.monthValue}/${date.dayOfMonth.toString().padStart(2, '0')}",
                        fontSize = (10f * settings.cardScale).sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 1.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).padding(14.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
    range: IntRange,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(valueText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = value.coerceIn(range.first.toFloat(), range.last.toFloat()),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
        ),
        modifier = Modifier.fillMaxWidth().height(34.dp).semantics { contentDescription = title },
    )
}

@Composable
private fun AppearanceSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackgroundCropDialog(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }

    DisposableEffect(bitmap) {
        onDispose { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    fun clampedOffset(candidate: Offset, nextZoom: Float): Offset {
        if (viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
        val baseScale = max(
            viewport.width.toFloat() / bitmap.width,
            viewport.height.toFloat() / bitmap.height,
        )
        val maxX = ((bitmap.width * baseScale * nextZoom - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((bitmap.height * baseScale * nextZoom - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !saving),
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel, enabled = !saving) { Icon(AppIcons.Back, "取消裁剪") }
                Text("裁剪课表背景", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                "拖动调整位置，双指或下方滑条缩放",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxHeight().aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black)
                        .onSizeChanged {
                            viewport = it
                            offset = clampedOffset(offset, zoom)
                        }
                        .then(
                            Modifier.pointerInput(bitmap, viewport) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    val nextZoom = (zoom * zoomChange).coerceIn(1f, ScheduleBackgroundStore.MAX_ZOOM)
                                    zoom = nextZoom
                                    offset = clampedOffset(offset + pan, nextZoom)
                                }
                            },
                        ),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCropImage(image, bitmap.width, bitmap.height, zoom, offset)
                        val guide = Color.White.copy(alpha = 0.58f)
                        drawLine(guide, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.dp.toPx())
                        drawLine(guide, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.dp.toPx())
                        drawLine(guide, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.dp.toPx())
                        drawLine(guide, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.dp.toPx())
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("缩放", fontSize = 13.sp)
                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        offset = clampedOffset(offset, it)
                    },
                    valueRange = 1f..ScheduleBackgroundStore.MAX_ZOOM,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text(String.format("%.1fx", zoom), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel, enabled = !saving) { Text("取消") }
                TextButton(
                    onClick = {
                        if (viewport == IntSize.Zero) return@TextButton
                        saving = true
                        scope.launch {
                            runCatching {
                                ScheduleBackgroundStore.cropAndSave(
                                    context = context,
                                    bitmap = bitmap,
                                    viewportWidth = viewport.width,
                                    viewportHeight = viewport.height,
                                    zoom = zoom,
                                    offsetX = offset.x,
                                    offsetY = offset.y,
                                )
                            }.onSuccess(onSaved).onFailure {
                                saving = false
                                Toast.makeText(context, "背景图保存失败，请重试", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !saving && viewport != IntSize.Zero,
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("使用背景")
                }
            }
        }
    }
}

private fun DrawScope.drawCropImage(
    image: androidx.compose.ui.graphics.ImageBitmap,
    bitmapWidth: Int,
    bitmapHeight: Int,
    zoom: Float,
    offset: Offset,
) {
    val baseScale = max(size.width / bitmapWidth, size.height / bitmapHeight)
    val displayScale = baseScale * zoom
    val width = (bitmapWidth * displayScale).roundToInt().coerceAtLeast(1)
    val height = (bitmapHeight * displayScale).roundToInt().coerceAtLeast(1)
    drawImage(
        image = image,
        dstOffset = IntOffset(
            ((size.width - width) / 2f + offset.x).roundToInt(),
            ((size.height - height) / 2f + offset.y).roundToInt(),
        ),
        dstSize = IntSize(width, height),
        filterQuality = FilterQuality.High,
    )
}

private val previewCourses = listOf(
    // weeks 为空=每周展示，保证没有真实课表时无论当前第几周都有可调节的效果样例。
    DisplayCourse("高等数学", "张老师", "理科楼 A201", 1, 1, 2, emptyList(), 0, false),
    DisplayCourse("自建课程", "李老师", "外语楼 302", 2, 3, 4, emptyList(), 2, true, customId = "preview-custom"),
    DisplayCourse("数据结构", "王老师", "软件楼 401", 3, 5, 6, emptyList(), 4, false),
    DisplayCourse("体育", "陈老师", "东操场", 4, 7, 8, (1..18 step 2).toList(), 5, false),
    DisplayCourse("软件工程", "周老师", "计算机楼 210", 5, 3, 4, (2..18 step 2).toList(), 1, false),
    DisplayCourse("创新实践", "刘老师", "创客空间", 6, 5, 7, (1..12).toList(), 3, false),
)
