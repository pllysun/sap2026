package edu.csuft.sap.ui.schedule

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.delay

/** 只在前台的周课表、今日或月历实际展示已有课表时记录；后台刷新和空引导页不计。 */
@Composable
internal fun ScheduleUsageEffect(state: ScheduleViewModel.UiState, enabled: Boolean = true) {
    val owner by CurrentAccount.uid.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mode = MemberState.effectiveMode
    val online = ConnectivityState.online
    val ready = enabled && state.activeProfileId != null && !state.scanning &&
        !(state.loading && state.display.isEmpty())
    LaunchedEffect(owner, state.account, state.activeProfileId, ready, mode, online, lifecycle) {
        if (owner.isNullOrBlank() || !ready || state.account != Graph.accountManager.activeAccount) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                Graph.scheduleUsage.viewed(mode)
                // 长时间留在课表页跨过午夜，也能记到下一天；已确认记录不会重复请求。
                delay(60_000)
            }
        }
    }
}
