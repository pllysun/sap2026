package edu.csuft.sap.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.ui.auth.LoginScreen
import edu.csuft.sap.ui.common.JwMfaGate
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.home.HomeScreen

@Composable
fun AppRoot(appViewModel: AppViewModel = viewModel()) {
    val gate by appViewModel.gate.collectAsState()

    // 每次切回前台都重探后端可达性：服务器恢复→自动上线，挂了→自动离线（丝滑切换）。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) appViewModel.onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    AnimatedContent(
        targetState = gate,
        label = "gate",
        transitionSpec = {
            // 前进/退出时用水平滑入 + 淡入，柔和过渡
            val forward = targetState.ordinal >= initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { full -> dir * full / 4 } + fadeIn(tween(280)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { full -> -dir * full / 4 } + fadeOut(tween(280))
                )
        },
    ) { state ->
        when (state) {
            AppViewModel.Gate.LOADING -> LoadingBox()
            AppViewModel.Gate.LOGIN -> LoginScreen(
                onLoggedIn = appViewModel::onLoggedIn,
                onOffline = appViewModel::enterOffline,
            )
            AppViewModel.Gate.APP -> HomeScreen(onLoggedOut = appViewModel::onLoggedOut)
        }
    }

    // 全局短信二次验证弹框：拉教务数据触发 MFA 时（后端 428）任意页面之上弹出
    JwMfaGate()
}
