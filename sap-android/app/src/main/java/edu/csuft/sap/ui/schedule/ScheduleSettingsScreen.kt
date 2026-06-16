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
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.data.schedule.WeekUtil
import edu.csuft.sap.notify.ReminderPrefs
import edu.csuft.sap.notify.ReminderScheduler
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
    onSave: (ScheduleSettings) -> Unit,
    onBack: () -> Unit,
) {
    var showSwitcher by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showWeeks by remember { mutableStateOf(false) }
    var showPeriods by remember { mutableStateOf(false) }
    var showPeriodTimes by remember { mutableStateOf(false) }
    var showLead by remember { mutableStateOf(false) }
    var showRowHeight by remember { mutableStateOf(false) }
    var showCardScale by remember { mutableStateOf(false) }
    var showRescanConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var reminderOn by remember { mutableStateOf(ReminderPrefs.enabled(context)) }
    var lead by remember { mutableStateOf(ReminderPrefs.leadMinutes(context)) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
            initial = Periods.current,
            onSave = { Periods.save(context, it); ScheduleWidgetProvider.notifyChanged(context) },
            onReset = { Periods.resetDefault(context); ScheduleWidgetProvider.notifyChanged(context) },
            onBack = { showPeriodTimes = false },
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

        // 教务账号多账号切换：仅教务模式。Web 模式无教务绑定、单一课表，不展示。
        if (MemberState.isJw && accounts.isNotEmpty()) {
            SectionHeader("教务账号")
            Card {
                accounts.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    SelectableRow(
                        title = if (a.isLocal) "WebVPN 课表" else (a.nickname?.takeIf { it.isNotBlank() } ?: a.account),
                        subtitle = if (a.isLocal) "网页端上导入，不绑定教务" else (if (a.nickname.isNullOrBlank()) null else a.account),
                        selected = a.account == activeAccount,
                        onClick = { onSwitchAccount(a.account) },
                    )
                }
            }
        }

        SectionHeader("课表")
        Card {
            // 课表管理(切换/另存为/多学期/重新扫描)仅教务模式；Web 模式课表唯一，只能重新导入覆盖
            if (MemberState.isJw) {
                NavRow("切换课表", currentName) { showSwitcher = true }
                RowDivider()
                ActionRow("另存为新课表", "把当前课表（含自建课）冻结成独立课表", onSaveAs)
                RowDivider()
                ActionRow("重命名当前课表", null, onRename)
                RowDivider()
                ActionRow("删除当前课表", null, onDelete)
                RowDivider()
                ActionRow("重新扫描学期", "重新拉取各学期教务课表", { showRescanConfirm = true })
                RowDivider()
            }
            ActionRow(
                "WebVPN 导入课表",
                if (MemberState.isWeb) "在网页里登录教务、抓取并覆盖当前课表" else "在网页里登录教务、端上抓取（不经服务器）",
                onWebImport,
            )
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
            SettingRow("课表时间设置", "自定义每节的起止时间", value = "", onClick = { showPeriodTimes = true })
            RowDivider()
            SettingRow("课格高度", "调整每节课格子的高度", value = "${settings.rowHeightDp} dp", onClick = { showRowHeight = true })
            RowDivider()
            SettingRow("课程卡字号", "调整课表里课程文字大小", value = "${(settings.cardScale * 100).toInt()}%", onClick = { showCardScale = true })
            RowDivider()
            SwitchRow("是否显示周末", "如果周末有课程，可打开该设置",
                checked = settings.showWeekend, onChange = { onSave(settings.copy(showWeekend = it)) })
            RowDivider()
            SwitchRow("是否显示非本周课程", "开启后单双周课程都能看见（灰显）",
                checked = settings.showNonWeek, onChange = { onSave(settings.copy(showNonWeek = it)) })
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
                "上课提醒", "课前提醒；国产手机请关闭对本应用的电池优化以保证准时",
                checked = reminderOn,
                onChange = { on ->
                    reminderOn = on
                    ReminderPrefs.setEnabled(context, on)
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    ReminderScheduler.reschedule(context)
                },
            )
            if (reminderOn) {
                RowDivider()
                SettingRow("提前提醒", "课程开始前多少分钟提醒",
                    value = "$lead 分钟", onClick = { showLead = true })
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

    if (showWeeks) PickerDialog("本学期总周数", (10..30).toList(), settings.totalWeeks, { "$it 周" },
        onPick = { onSave(settings.copy(totalWeeks = it)) }, onDismiss = { showWeeks = false })

    if (showPeriods) PickerDialog("一天的总课时数", (8..16).toList(), settings.dailyPeriods, { "$it 节" },
        onPick = { onSave(settings.copy(periodsPerDay = it)) }, onDismiss = { showPeriods = false })

    if (showRowHeight) PickerDialog("课格高度", (40..88 step 4).toList(), settings.rowHeightDp, { "$it dp" },
        onPick = { onSave(settings.copy(rowHeight = it)) }, onDismiss = { showRowHeight = false })

    if (showCardScale) PickerDialog("课程卡字号", listOf(80, 90, 100, 110, 125, 140),
        (settings.cardScale * 100).toInt(), { "$it%" },
        onPick = { onSave(settings.copy(cardTextScale = it)) }, onDismiss = { showCardScale = false })

    if (showLead) PickerDialog("提前提醒", listOf(5, 10, 15, 20, 30, 45, 60), lead, { "$it 分钟" },
        onPick = { lead = it; ReminderPrefs.setLead(context, it); ReminderScheduler.reschedule(context) },
        onDismiss = { showLead = false })
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

/** 通用单列选择对话框（总周数 / 每日节数共用）。 */
@Composable
private fun PickerDialog(
    title: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                options.forEach { v ->
                    val sel = v == selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(v); onDismiss() }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                    ) {
                        Text(label(v), fontSize = 15.sp,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
