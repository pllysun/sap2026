package edu.csuft.sap.design

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.schedule.ClassSchedulePickerScreen
import edu.csuft.sap.ui.schedule.ScheduleScreen

internal fun prepareClassSchedulesPreview(context: Context, reset: Boolean) {
    check(BuildConfig.APPLICATION_ID.endsWith(".classqa")) { "Only use the isolated class QA application" }
    CurrentAccount.set("class-qa")
    Graph.accountManager.onUserChanged()
    MemberState.setAccess(listOf(4), 2)
    MemberState.setMode(context, AppMode.CLASS)
    if (!reset) return
    Graph.classScheduleCache.delete(Graph.scheduleStore.cachedClassAccounts().map { it.first }.toSet())
    context.getSharedPreferences("class_catalog_v1", Context.MODE_PRIVATE).edit().clear().apply()
    for ((key, name) in listOf("qa-a" to "2023人工智能2班", "qa-b" to "2024城乡规划1班", "qa-empty" to "空课表样本")) {
        for (term in listOf("2025-2026-2", "2026-2027-1")) {
            Graph.scheduleStore.importClass(AccountManager.CLASS_ACCOUNT_PREFIX + key, term, name,
                if (key == "qa-empty") emptyList() else listOf(CachedCourse("智慧农林", teacher = "示例教师", location = "树人楼南501", day = 3, sectionIndex = 1, weeksRaw = "1-6")),
                "2026-09-07", displayName = "测试学院 · 2023 · 示例专业 · $name")
        }
    }
    Graph.accountManager.useClass("qa-a")
}

@Composable
internal fun ClassSchedulesPreview() {
    var picker by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (picker) {
            ClassSchedulePickerScreen(onBack = { picker = false }, onSelected = { picker = false })
        } else {
            TextButton(onClick = { picker = true }) { Text("打开班级目录") }
            ScheduleScreen(Modifier.weight(1f))
        }
    }
}
