package edu.csuft.sap.ui.auth

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** 注册 ViewModel 绑定返回栈；离开注册页面即清理输入和未完成的验证码请求。 */
@Composable
fun AuthScreen(onLoggedIn: () -> Unit, onOffline: () -> Unit) {
    val nav = rememberNavController()
    NavHost(
        navController = nav, startDestination = "login",
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 6 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 6 } },
    ) {
        composable("login") { entry ->
            val registered by entry.savedStateHandle.getStateFlow<String?>("registeredStudentId", null).collectAsState()
            LoginScreen(
                onLoggedIn = onLoggedIn, onOffline = onOffline,
                onRegister = { nav.navigate("register") { launchSingleTop = true } },
                registeredStudentId = registered,
                onRegistrationConsumed = { entry.savedStateHandle["registeredStudentId"] = null },
            )
        }
        composable("register") {
            RegisterScreen(
                onBack = { if (nav.currentBackStackEntry?.destination?.route == "register") nav.popBackStack() },
                onRegistered = { studentId ->
                    nav.previousBackStackEntry?.savedStateHandle?.set("registeredStudentId", studentId)
                    if (nav.currentBackStackEntry?.destination?.route == "register") nav.popBackStack()
                },
            )
        }
    }
}
