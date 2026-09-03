package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import edu.csuft.sap.ui.icons.AppIcons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.account.BoundAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.data.schedule.WeekUtil
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import edu.csuft.sap.notify.ReminderPermissions
import edu.csuft.sap.notify.ReminderPrefs
import edu.csuft.sap.notify.ReminderScheduler
import edu.csuft.sap.ui.common.OptionSheet
import edu.csuft.sap.widget.ScheduleWidgetProvider
import java.time.Instant
import java.time.ZoneOffset

/**
 * 「课表设置」整页：分卡片收纳 教务账号 / 课表（切换=三级下钻 + 管理） / 显示设置。
 * 显示设置每次改动立即回调 onSave；账号、课表的切换/管理走各自回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(
    settings: ScheduleSettings,
    accounts: List<BoundAccount>,
    activeAccount: String?,
    onSwitchAccount: (String) -> Unit,
    profiles: List<ScheduleViewModel.ProfileMeta>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onSaveAs: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRescan: () -> Unit,
    onWebImport: () -> Unit,
    onClassPicker: () -> Unit,
    previewCourses: List<DisplayCourse>,
    previewWeek: Int,
    onSave: (ScheduleSettings) -> Unit,
    onBack: () -> Unit,
) {
    var showSwitcher by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showWeeks by remember { mutableStateOf(false) }
    var showPeriods by remember { mutableStateOf(false) }
    var showPeriodTimes by remember { mutableStateOf(false) }
    var showPersonalization by remember { mutableStateOf(false) }
    var showLead by remember { mutableStateOf(false) }
    var showRescanConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var reminderOn by remember { mutableStateOf(ReminderPrefs.enabled(context)) }
    var popupOn by remember { mutableStateOf(ReminderPrefs.popupEnabled(context)) }
    var lead by remember { mutableStateOf(ReminderPrefs.leadMinutes(context)) }
    var showPermDialog by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // 申请「显示在其它应用上层」(悬浮窗)权限；返回后若已授予则正式开启弹窗提醒
    val overlayPerm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) {
            popupOn = true
            ReminderPrefs.setPopupEnabled(context, true)
            ReminderScheduler.reschedule(context)
        }
    }

    if (showRescanConfirm) {
        AlertDialog(
            onDismissRequest = { showRescanConfirm = false },
            title = { Text("重新扫描学期") },
            text = { Text("将从教务重新拉取各学期课表，并覆盖当前所有学期课表（自定义课表不受影响）。期间请保持网络畅通。确定继续？") },
            confirmButton = {
                TextButton(onClick = { showRescanConfirm = false; onRescan() }) { Text("确定扫描") }
            },
            dismissButton = { TextButton(onClick = { showRescanConfirm = false }) { Text("取消") } },
        )
    }

    if (showSwitcher) {
        ProfileSwitcher(
            profiles = profiles,
            activeProfileId = activeProfileId,
            onSelect = { onSelectProfile(it); showSwitcher = false },
            onBack = { showSwitcher = false },
        )
        return
    }
    if (showPeriodTimes) {
        PeriodTimesScreen(
            initial = Periods.tableFor(settings.dailyPeriods), // 有多少节就编辑多少节的时间
            onSave = { Periods.save(context, it); ScheduleWidgetProvider.notifyChanged(context); ReminderScheduler.reschedule(context) },
            onReset = { Periods.resetDefault(context); ScheduleWidgetProvider.notifyChanged(context); ReminderScheduler.reschedule(context) },
            onBack = { showPeriodTimes = false },
        )
        return
    }
    if (showPersonalization) {
        SchedulePersonalizationScreen(
            settings = settings,
            courses = previewCourses,
            selectedWeek = previewWeek,
            onSave = onSave,
            onBack = { showPersonalization = false },
        )
        return
    }

    val currentName = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "未选择"

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
            Text("课表设置", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        // 教务账号多账号切换：仅教务模式，且只列真实教务账号（不含本地网页/班级缓存源）
        val jwAccounts = accounts.filter { !it.isLocal }
        if (MemberState.isJw && jwAccounts.isNotEmpty()) {
            SectionHeader("教务账号")
            Card {
                jwAccounts.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    SelectableRow(
                        title = a.nickname?.takeIf { it.isNotBlank() } ?: a.account,
                        subtitle = if (a.nickname.isNullOrBlank()) null else a.account,
                        selected = a.account == activeAccount,
                        onClick = { onSwitchAccount(a.account) },
                    )
                }
            }
        }

        SectionHeader("课表")
        Card {
            if (MemberState.isClass) {
                ClassScheduleRow(currentName, onClassPicker)
                RowDivider()
                ActionRow("清除当前班级缓存", "仅删除本机缓存，之后仍可重新选择", onDelete)
            } else {
            // 已有课表始终可切换与管理；新增副本只在完整能力下开放。
            if (profiles.isNotEmpty()) {
                NavRow("切换课表", currentName) { showSwitcher = true }
                RowDivider()
                if (MemberState.hasFullAppFeatures) {
                    ActionRow("另存为新课表", "把当前课表（含自建课）冻结成独立课表", onSaveAs)
                    RowDivider()
                }
                ActionRow("重命名当前课表", null, onRename)
                RowDivider()
            }
            // 重新扫描：仅教务模式（走后端代抓）
            if (MemberState.isJw) {
                ActionRow("重新扫描学期", "重新拉取各学期教务课表", { showRescanConfirm = true })
                RowDivider()
            }
            ActionRow(
                "删除当前课表",
                null,
                onDelete,
            )
            // 网页导入：仅 Web 模式（教务模式不显示）
            if (MemberState.isWeb) {
                RowDivider()
                ActionRow(
                    "网页登录导入课表",
                    "在网页登录教务并更新课表内容",
                    onWebImport,
                )
            }
            }
        }

        SectionHeader("显示设置")
        Card {
            SettingRow("开始上课时间", "课表开始的第一天，不是开学时间",
                value = settings.semesterStartDate ?: "未设置", onClick = { showDate = true })
            RowDivider()
            val curWeek = WeekUtil.currentWeek(settings.semesterStartDate)
            SettingRow("当前的周数", "开学到现在几周，便于确定单双周",
                value = curWeek?.let { "第 $it 周" } ?: "假期中", onClick = null)
            RowDivider()
            SettingRow("本学期总周数", "请选择本学期总共多少周",
                value = "${settings.totalWeeks}", onClick = { showWeeks = true })
            RowDivider()
            SettingRow("一天的总课时数", "每天显示多少节课，可设 8-16 节",
                value = "${settings.dailyPeriods} 节", onClick = { showPeriods = true })
            RowDivider()
            SettingRow("课表时间设置", "自定义每节的起止时间，提醒/课表按新时间生效",
                value = Periods.tableFor(settings.dailyPeriods).let { "${it.first().start}–${it.last().end}" },
                onClick = { showPeriodTimes = true })
            RowDivider()
            NavRow("个性化", "课表外观与布局") { showPersonalization = true }
            RowDivider()
            SwitchRow("点课显示其他周课程", "点某节课时，一并列出该时段在其他周的不同课程（方便对照修改）；关闭则只看本周",
                checked = settings.showOtherWeekInDetail, onChange = { onSave(settings.copy(showOtherWeekInDetail = it)) })
            RowDivider()
            SwitchRow("设置每周起始日", "周日也可以作为一周的起始啦",
                checked = settings.weekStartSunday, onChange = { onSave(settings.copy(weekStartSunday = it)) })
            RowDivider()
            SwitchRow("显示当前时间线", "在课表上画一条标示当前时刻的红线",
                checked = settings.showNowLine, onChange = { onSave(settings.copy(showNowLine = it)) })
        }

        SectionHeader("提醒")
        Card {
            SwitchRow(
                "上课提醒（通知）", "课前在通知栏提醒；国产手机请关闭对本应用的电池优化以保证准时",
                checked = reminderOn,
                onChange = { on ->
                    reminderOn = on
                    ReminderPrefs.setEnabled(context, on)
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // 杀后台仍准时的关键：未关电池优化/无精确闹钟权限时引导开启
                    if (on && (!ReminderPermissions.batteryUnrestricted(context) || !ReminderPermissions.exactAlarmGranted(context))) {
                        showPermDialog = true
                    }
                    ReminderScheduler.reschedule(context)
                },
            )
            RowDivider()
            SwitchRow(
                "上课弹窗提醒", "课前弹出悬浮窗，盖在其它应用上层（类似微信来消息的弹窗）；需授予「显示在其它应用上层」权限",
                checked = popupOn,
                onChange = { on ->
                    if (on) {
                        if (Settings.canDrawOverlays(context)) {
                            popupOn = true
                            ReminderPrefs.setPopupEnabled(context, true)
                            ReminderScheduler.reschedule(context)
                            if (!ReminderPermissions.batteryUnrestricted(context) || !ReminderPermissions.exactAlarmGranted(context)) {
                                showPermDialog = true
                            }
                        } else {
                            // 去系统设置授予悬浮窗权限，返回后在 overlayPerm 回调里正式开启
                            overlayPerm.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    } else {
                        popupOn = false
                        ReminderPrefs.setPopupEnabled(context, false)
                        ReminderScheduler.reschedule(context)
                    }
                },
            )
            if (reminderOn || popupOn) {
                RowDivider()
                SettingRow("提前提醒", "课程开始前多少分钟提醒",
                    value = "$lead 分钟", onClick = { showLead = true })
                RowDivider()
                ActionRow("确保准时收到（权限设置）", "杀后台 / 锁屏也能准时提醒：开启通知、精确闹钟、忽略电池优化、自启动") {
                    showPermDialog = true
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showDate) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        // 用户手动设置 → 打标记，后续自动同步不再覆盖
                        onSave(settings.copy(semesterStartDate = d.toString(), semesterStartDateManual = true))
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }

    if (showWeeks) OptionSheet("本学期总周数", (10..30).toList(), settings.totalWeeks, { "$it 周" },
        onPick = { onSave(settings.copy(totalWeeks = it)) }, onDismiss = { showWeeks = false })

    if (showPeriods) OptionSheet("一天的总课时数", (8..16).toList(), settings.dailyPeriods, { "$it 节" },
        onPick = { onSave(settings.copy(periodsPerDay = it)) }, onDismiss = { showPeriods = false })

    if (showLead) OptionSheet("提前提醒", listOf(5, 10, 15, 20, 30, 45, 60), lead, { "$it 分钟" },
        onPick = { lead = it; ReminderPrefs.setLead(context, it); ReminderScheduler.reschedule(context) },
        onDismiss = { showLead = false })

    if (showPermDialog) ReminderPermDialog(onDismiss = { showPermDialog = false })
}

// ---------- 通用卡片/行 ----------

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp)
        .background(MaterialTheme.colorScheme.outlineVariant))
}

/** 可选中行：左标题/副标题，右侧选中打勾。用于切换账号。 */
@Composable
private fun SelectableRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (selected) Icon(AppIcons.Check, "当前", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
    }
}

/** 导航行：右侧显示当前值 + ›。用于「切换课表」。 */
@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp))
    }
}

/** 可点击操作行（无右值）。 */
@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 班级模式的当前选择行：完整显示班级路径，长文本自然换行，不再挤在右侧被省略。 */
@Composable
private fun ClassScheduleRow(currentName: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("当前班级课表", fontSize = 16.sp)
            Text(
                currentName.takeUnless { it == "未选择" } ?: "尚未选择班级课表",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text("更换", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
