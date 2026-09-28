package edu.csuft.sap.design

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.data.schedule.CustomCourse
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.schedule.SchedulePersonalizationScreen
import edu.csuft.sap.ui.schedule.ScheduleScreen
import edu.csuft.sap.ui.schedule.ScheduleViewModel

internal fun prepareScheduleColorsPreview(context: Context, reset: Boolean) {
    check(BuildConfig.APPLICATION_ID.endsWith(".classqa"))
    CurrentAccount.set("course-colors-qa")
    Graph.accountManager.onUserChanged()
    MemberState.setAccess(listOf(4), 2)
    MemberState.setMode(context, AppMode.CLASS)
    if (!reset) return
    Graph.accountManager.useClass("qa-colors")
    val account = Graph.accountManager.activeAccount!!
    Graph.scheduleStore.clearAccount(account)
    for (term in listOf("2025-2026-2", "2026-2027-1")) {
        Graph.scheduleStore.importClass(account, term, "颜色验收",
            listOf(CachedCourse("自动课程", day = 1, sectionIndex = 1, colorIndex = 2)), "2026-09-07")
        Graph.scheduleStore.upsertCourse(account, "class:$term", CustomCourse("preset", "自建预设", day = 2,
            startNode = 1, endNode = 2, colorIndex = 2))
        Graph.scheduleStore.upsertCourse(account, "class:$term", CustomCourse("picked", "自建指定", day = 3,
            startNode = 1, endNode = 2, customColor = 0xFFCCD1F0))
    }
    Graph.scheduleStore.updateSettings(account, "class:2026-2027-1", ScheduleSettings(semesterStartDate = "2026-09-07"))
}

@Composable
internal fun ScheduleColorsPreview(vm: ScheduleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (editing) {
            SchedulePersonalizationScreen(state.settings, state.display, state.selectedWeek, vm::saveSettings) { editing = false }
        } else {
            TextButton(onClick = { editing = true }) { Text("调整颜色") }
            ScheduleScreen(Modifier.weight(1f), vm)
        }
    }
}
