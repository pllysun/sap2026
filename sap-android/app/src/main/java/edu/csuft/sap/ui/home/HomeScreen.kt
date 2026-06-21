package edu.csuft.sap.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.grade.GradeScreen
import edu.csuft.sap.ui.profile.ProfileScreen
import edu.csuft.sap.ui.profile.WebSettingsScreen
import edu.csuft.sap.ui.schedule.ScheduleScreen
import edu.csuft.sap.update.UpdateDialog
import edu.csuft.sap.update.UpdateViewModel

private enum class Tab(val label: String, val icon: ImageVector, val iconFilled: ImageVector) {
    Schedule("课表", AppIcons.Schedule, AppIcons.ScheduleFilled),
    Grade("成绩", AppIcons.Grades, AppIcons.GradesFilled),
    Profile("我的", AppIcons.Profile, AppIcons.ProfileFilled),
    WebSettings("设置", AppIcons.Settings, AppIcons.SettingsFilled),
}

@Composable
fun HomeScreen(onLoggedOut: () -> Unit) {
    var current by rememberSaveable { mutableIntStateOf(0) }

    // 模式门控：JW(教务)=课表/(开启→成绩)/我的；WEB=课表/设置。非会员强制 WEB。
    // 成绩属教务模式，是否显示由「我的→设置→显示成绩」控制（默认显示，关闭则只剩课表/我的）。
    val mode = MemberState.effectiveMode
    val visibleTabs = if (mode == AppMode.JW)
        buildList {
            add(Tab.Schedule)
            if (MemberState.showGrade) add(Tab.Grade)
            add(Tab.Profile)
        }
    else
        listOf(Tab.Schedule, Tab.WebSettings)
    val idx = current.coerceIn(0, visibleTabs.size - 1)
    // 切换模式后回到首个 Tab(课表)，避免停留在已消失的 Tab
    LaunchedEffect(mode) { current = 0 }

    // 仅会员才检查更新（非会员不能更新软件）
    val updateVm: UpdateViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(MemberState.isMember) {
        if (MemberState.isMember) updateVm.check(manual = false)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                visibleTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = idx == index,
                        onClick = { current = index },
                        icon = {
                            // 选中=实心剪影，未选中=线性，提升 Tab 切换的层次与质感
                            Icon(if (idx == index) tab.iconFilled else tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.outline,
                            unselectedTextColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = visibleTabs[idx],
                label = "tab",
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    // 底部 Tab 切换用淡入淡出，自然不突兀
                    fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                },
            ) { tab ->
                when (tab) {
                    Tab.Schedule -> ScheduleScreen(Modifier.fillMaxSize())
                    Tab.Grade -> GradeScreen(Modifier.fillMaxSize())
                    Tab.Profile -> ProfileScreen(Modifier.fillMaxSize(), onLoggedOut = onLoggedOut)
                    Tab.WebSettings -> WebSettingsScreen(Modifier.fillMaxSize(), onLoggedOut = onLoggedOut)
                }
            }
        }
    }

    UpdateDialog(
        state = updateVm.state.collectAsState().value,
        onDownload = { updateVm.download(context) },
        onInstall = { updateVm.install(context) },
        onDismiss = { updateVm.dismiss() },
    )
}
