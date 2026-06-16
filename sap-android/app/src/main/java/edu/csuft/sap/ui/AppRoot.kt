package edu.csuft.sap.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.ui.auth.LoginScreen
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.home.HomeScreen

@Composable
fun AppRoot(appViewModel: AppViewModel = viewModel()) {
    val auth by appViewModel.auth.collectAsState()
    AnimatedContent(
        targetState = auth,
        label = "auth",
        transitionSpec = {
            // 登录态前进/退出时用水平滑入 + 淡入，柔和过渡
            val forward = targetState.ordinal >= initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { full -> dir * full / 4 } + fadeIn(tween(280)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { full -> -dir * full / 4 } + fadeOut(tween(280))
                )
        },
    ) { state ->
        when (state) {
            AppViewModel.AuthState.LOADING -> LoadingBox()
            AppViewModel.AuthState.UNAUTHED -> LoginScreen(onLoggedIn = appViewModel::onLoggedIn)
            AppViewModel.AuthState.AUTHED -> HomeScreen(onLoggedOut = appViewModel::onLoggedOut)
        }
    }
}
