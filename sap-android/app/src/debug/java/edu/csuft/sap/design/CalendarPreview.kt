package edu.csuft.sap.design

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.*
import edu.csuft.sap.data.schedule.*
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.common.AcademicPrivacyGate
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.profile.AppSettingsScreen
import edu.csuft.sap.ui.schedule.CalendarContent
import edu.csuft.sap.ui.schedule.ScheduleViewModel
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

internal fun prepareModePreview(context: Context) {
    check(BuildConfig.APPLICATION_ID.endsWith(".classqa"))
    CurrentAccount.set("mode-qa")
    ConnectivityState.online = true
    MemberState.setAccess(listOf(3), 2)
    MemberState.setMode(context, AppMode.CLASS)
    context.getSharedPreferences("privacy_consents", Context.MODE_PRIVATE).edit().remove("academic_mode-qa").commit()
    PrivacyConsents.load(context)
}

@Composable
internal fun ModeSwitchPreview() {
    val context = LocalContext.current
    AppSettingsScreen(Modifier.safeDrawingPadding(), MemberState.effectiveMode,
        onSelectMode = { MemberState.requestMode(context, it) }, onTheme = {}, onAnnouncements = {},
        onPrivacy = {}, onChangelog = {}, onAbout = {}, onLogout = {}, onBack = {})
    AcademicPrivacyGate()
}

@Composable
internal fun CalendarPreview(month: Boolean) {
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
    val courses = listOf(
        DisplayCourse("软件工程与项目实践", "张老师", "树人楼北 301", today.dayOfWeek.value, 1, 2, listOf(1, 2, 3, 4), 0, false),
        DisplayCourse("数据结构", "李老师", "信息楼 502", today.dayOfWeek.value, 5, 6, listOf(2, 4), 1, false),
        DisplayCourse("体育选项课Ⅱ", "王老师", "田径场", 1, 7, 8, emptyList(), 2, false),
    )
    Scaffold(Modifier.safeDrawingPadding(), bottomBar = {
        NavigationBar {
            listOf("日历" to AppIcons.CalendarFilled, "课表" to AppIcons.Schedule, "我的" to AppIcons.Profile).forEach { (title, icon) ->
                NavigationBarItem(selected = title == "日历", onClick = {}, icon = { Icon(icon, null) }, label = { Text(title) })
            }
        }
    }) { padding ->
        CalendarContent(ScheduleViewModel.UiState(loading = false, activeProfileId = "qa", activeProfileName = "2025软件工程4班 · 2026-2027-1",
            settings = ScheduleSettings(semesterStartDate = start.toString()), display = courses), Modifier.padding(padding), month)
    }
}
