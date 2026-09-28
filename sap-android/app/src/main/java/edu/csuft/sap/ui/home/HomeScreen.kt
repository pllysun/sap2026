package edu.csuft.sap.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.account.ModeGuideKind
import edu.csuft.sap.data.account.ModeGuideState
import edu.csuft.sap.data.schedule.AccountData
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.grade.GradeScreen
import edu.csuft.sap.ui.icons.AnimatedAppIcon
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.profile.ProfileScreen
import edu.csuft.sap.ui.profile.ProfileViewModel
import edu.csuft.sap.ui.schedule.ScheduleScreen
import edu.csuft.sap.update.UpdateDialog
import edu.csuft.sap.update.UpdateViewModel

private enum class Tab(val label: String, val icon: ImageVector, val iconFilled: ImageVector) {
    Calendar("日历", AppIcons.Calendar, AppIcons.CalendarFilled),
    Schedule("课表", AppIcons.Schedule, AppIcons.ScheduleFilled),
    Grade("成绩", AppIcons.Grades, AppIcons.GradesFilled),
    Profile("我的", AppIcons.Profile, AppIcons.ProfileFilled),
}

/** 选择降级快照来源；仅“从未导入过”的空 Web 空间允许用上次教务缓存做旧版本兼容恢复。 */
internal fun downgradeSnapshotSource(active: String?, lastJw: String?, web: AccountData): String? =
    active?.takeUnless(AccountManager::isLocalOrClass)
        ?: lastJw?.takeIf { web.profiles.isEmpty() && !web.scanned }

@Composable
fun HomeScreen(onLoggedOut: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Tab.Schedule.name) }
    var openModePicker by rememberSaveable { mutableStateOf(false) }
    var guide by remember { mutableStateOf<ModeGuideKind?>(null) }
    // 进首页即恢复已缓存的头像，首次点“我的”时也无需等待图片解码。
    val profileVm: ProfileViewModel = viewModel()

    // 模式门控：三种模式都使用统一的「课表 / 成绩(仅教务) / 我的」布局。
    // Web 与班级模式的设置从「我的→设置」进入，避免为不同模式维护两套入口。
    // 成绩属教务模式，是否显示由「我的→设置→显示成绩」控制（默认显示，关闭则只剩课表/我的）。
    val mode = MemberState.effectiveMode
    val visibleTabs = if (mode == AppMode.JW)
        buildList {
            add(Tab.Calendar)
            add(Tab.Schedule)
            if (MemberState.showGrade) add(Tab.Grade)
            add(Tab.Profile)
        }
    else
        listOf(Tab.Calendar, Tab.Schedule, Tab.Profile)
    val idx = visibleTabs.indexOfFirst { it.name == current }.takeIf { it >= 0 } ?: visibleTabs.indexOf(Tab.Schedule)
    // 切换模式后回到首个 Tab(课表)，避免停留在已消失的 Tab
    LaunchedEffect(
        mode,
        MemberState.appAccessLevel,
        MemberState.isMember,
        MemberState.accessResolved,
        ConnectivityState.online,
    ) {
        current = Tab.Schedule.name
        // 云控 2→1 时先把当前教务缓存无损继承到 Web 本地空间，再切换数据源。
        // 源教务缓存与服务端绑定均保留；再次升到 2 仍可原样恢复并继续同步。
        // 冷启动会先乐观进入首页；身份尚未从服务端解析前不能执行继承，否则真实会员会被误当成游客。
        when (mode) {
            AppMode.WEB -> {
                if (!ConnectivityState.online || MemberState.accessResolved) {
                val active = Graph.accountManager.activeAccount
                if (!ConnectivityState.online || !MemberState.hasFullAppFeatures) {
                    // 兼容已被旧版本直接切到空 Web 源的用户。scanned=true 但无 profile 代表用户主动删空，
                    // 此时不能擅自恢复；只有从未导入过的全新空空间才尝试上次教务缓存。
                    val source = downgradeSnapshotSource(
                        active,
                        Graph.accountManager.lastJwAccount(),
                        Graph.scheduleStore.accountData(AccountManager.WEBVIEW_ACCOUNT),
                    )
                    source?.let(Graph.scheduleStore::inheritIntoWebview)
                }
                }
                // 掉线时也切到本地 Web 槽，避免仍拿着教务账号而触发网络扫描。
                Graph.accountManager.useWebview()
            }
            AppMode.CLASS -> {
                Graph.accountManager.activateClassAccount()
                Graph.classScheduleSync.request()
            }
            AppMode.JW -> if (ConnectivityState.online) {
                Graph.accountManager.activateJwAccount()
                Graph.accountManager.refresh()
                if (MemberState.isJw) Graph.accountManager.activateJwAccount()
            }
                else Graph.accountManager.useWebview()
        }
    }

    // 完整 App 能力账号检查更新；真实角色不参与界面文案。
    val updateVm: UpdateViewModel = viewModel()
    val context = LocalContext.current
    val owner by CurrentAccount.uid.collectAsState()
    LaunchedEffect(owner, MemberState.accessResolved, MemberState.isMember,
        MemberState.appAccessLevel, ConnectivityState.online) {
        guide = ModeGuideState.pending(context, owner ?: "_", MemberState.accessResolved,
            ConnectivityState.online, MemberState.isMember, MemberState.appAccessLevel)
    }
    fun finishGuide(openPicker: Boolean) {
        val active = guide ?: return
        ModeGuideState.complete(context, owner ?: "_", active, MemberState.hasFullAppFeatures)
        guide = null
        if (openPicker) {
            openModePicker = true
            current = Tab.Profile.name
        }
    }
    LaunchedEffect(MemberState.hasFullAppFeatures) {
        if (MemberState.hasFullAppFeatures) updateVm.check(manual = false)
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
                        onClick = { current = tab.name },
                        icon = {
                            AnimatedAppIcon(
                                icon = tab.icon, selectedIcon = tab.iconFilled,
                                selected = idx == index, tint = LocalContentColor.current,
                            )
                        },
                        label = { Text(tab.label, fontSize = 11.sp, fontWeight = if (idx == index) FontWeight.SemiBold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.065f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Tab.Calendar -> edu.csuft.sap.ui.schedule.CalendarScreen(Modifier.fillMaxSize())
                    Tab.Schedule -> ScheduleScreen(Modifier.fillMaxSize())
                    Tab.Grade -> GradeScreen(Modifier.fillMaxSize())
                    Tab.Profile -> ProfileScreen(Modifier.fillMaxSize(), onLoggedOut = onLoggedOut, vm = profileVm,
                        openModePicker = openModePicker, onModePickerOpened = { openModePicker = false })
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
    guide?.let { kind ->
        ModeGuideOverlay(kind, MemberState.availableModes,
            onDismiss = { finishGuide(false) }, onOpenModePicker = { finishGuide(true) })
    }
}
