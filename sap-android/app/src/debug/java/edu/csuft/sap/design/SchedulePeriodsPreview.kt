package edu.csuft.sap.design

import android.content.Context
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
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.data.schedule.displayPeriodCount
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.schedule.ScheduleScreen
import edu.csuft.sap.ui.schedule.ScheduleViewModel

/** Local cached samples in an isolated debug application; no school login. */
internal fun prepareSchedulePeriodsPreview(context: Context) {
    check(BuildConfig.APPLICATION_ID.endsWith(".periodqa"))
    CurrentAccount.set("periods-qa")
    Graph.accountManager.onUserChanged()
    MemberState.setAccess(listOf(4), 2)
    MemberState.setMode(context, AppMode.CLASS)
    Graph.accountManager.useClass("periods-qa")
    val account = Graph.accountManager.activeAccount ?: return
    val fixtures = listOf(
        Triple("normal", "普通课表", listOf(9, 10)),
        Triple("late", "晚课课表", listOf(9, 10, 11, 12)),
        Triple("eleven", "第11节课表", listOf(11)),
    )
    for ((term, name, nodes) in fixtures) {
        Graph.scheduleStore.importClass(account, term, name, listOf(
            CachedCourse("程序设计", day = 1, sectionIndex = 1, weeksRaw = "1-20", nodes = listOf(1, 2)),
            CachedCourse("物理化学实验", day = 4, sectionIndex = 5, weeksRaw = "1-20", nodes = nodes),
        ), "2026-09-07")
        val profile = Graph.scheduleStore.accountData(account).profiles.first { it.termValue == term }
        Graph.scheduleStore.updateSettings(account, profile.id, ScheduleSettings(semesterStartDate = "2026-09-07"))
    }
    Graph.scheduleStore.setActiveProfile(account, "class:late")
}

@Composable
internal fun SchedulePeriodsPreview(vm: ScheduleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row {
            TextButton(onClick = { vm.selectProfile("class:normal") }) { Text("普通10节") }
            TextButton(onClick = { vm.selectProfile("class:late") }) { Text("晚课12节") }
            TextButton(onClick = { vm.selectProfile("class:eleven") }) { Text("仅第11节") }
        }
        Text("${state.activeProfileName} · 显示${state.settings.displayPeriodCount(state.display)}节 · 默认${state.settings.dailyPeriods}节")
        ScheduleScreen(Modifier.weight(1f), vm)
    }
}
