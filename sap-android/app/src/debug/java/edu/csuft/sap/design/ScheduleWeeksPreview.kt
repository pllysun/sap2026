package edu.csuft.sap.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.data.schedule.ProfileKind
import edu.csuft.sap.data.schedule.ScheduleProfile
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.schedule.ScheduleScreen
import edu.csuft.sap.ui.schedule.ScheduleViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 独立班级缓存样本，用真实 ViewModel 和课表页验证切学期不会沿用旧周次。 */
internal fun seedWeekSwitchPreview() {
    Graph.accountManager.useClass("week-picker-qa")
    val account = Graph.accountManager.activeAccount ?: return
    val nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    val fixtures = listOf(
        Triple("qa-past", "旧学期", nextMonday.minusWeeks(26)),
        Triple("qa-upcoming", "新学期", nextMonday),
    )
    for ((id, name, start) in fixtures) {
        Graph.scheduleStore.setTermCourses(account, id, listOf(
            CachedCourse(
                name = if (id == "qa-upcoming") "新学期第一周示例课" else "旧学期第二十周示例课",
                day = 1, sectionIndex = 1, weeksRaw = if (id == "qa-upcoming") "1" else "20",
            ),
        ))
        Graph.scheduleStore.addProfile(account, ScheduleProfile(
            id = id, name = name, kind = ProfileKind.TERM, termValue = id,
            settings = ScheduleSettings(semesterStartDate = start.toString(), semesterStartDateManual = true),
        ), makeActive = false)
    }
    Graph.scheduleStore.setActiveProfile(account, "qa-past")
}

@Composable
internal fun ScheduleWeeksPreview(vm: ScheduleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row {
            TextButton(onClick = { vm.selectProfile("qa-past") }) { Text("旧学期") }
            TextButton(onClick = { vm.selectProfile("qa-upcoming") }) { Text("新学期") }
        }
        Text("${state.activeProfileName} · 第 ${state.selectedWeek} 周")
        ScheduleScreen(modifier = Modifier.weight(1f), vm = vm)
    }
}
