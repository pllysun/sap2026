package edu.csuft.sap.design

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.ui.auth.AuthViewModel
import edu.csuft.sap.ui.auth.AuthScreen
import edu.csuft.sap.ui.auth.LoginContent
import edu.csuft.sap.ui.common.ModeOptionRow
import edu.csuft.sap.ui.common.ModePickerHeader
import edu.csuft.sap.ui.icons.AnimatedAppIcon
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.AppLogo
import edu.csuft.sap.ui.icons.SyncIcon
import edu.csuft.sap.ui.home.HomeScreen
import edu.csuft.sap.ui.theme.SapTheme
import kotlinx.coroutines.delay

/** Real production composables with local sample state. Never authenticates a user. */
class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "login"
        if (screen == "mode-switch") prepareModePreview(this)
        if (screen == "registration-flow") check(packageName.endsWith(".registrationqa"))
        if (screen == "schedule-weeks") seedWeekSwitchPreview()
        if (screen == "profile-avatar") seedProfileAvatarPreview(this)
        if (screen == "class-schedules") prepareClassSchedulesPreview(this, intent.getBooleanExtra("reset", false))
        if (screen == "course-colors") prepareScheduleColorsPreview(this, intent.getBooleanExtra("reset", false))
        setContent {
            SapTheme {
                when {
                    screen == "mode-switch" -> ModeSwitchPreview()
                    screen.startsWith("calendar") -> CalendarPreview(screen == "calendar-month")
                    screen == "registration-flow" -> AuthScreen(onLoggedIn = {}, onOffline = {})
                    screen.startsWith("register") -> RegistrationPreview(screen)
                    screen == "course-colors" -> ScheduleColorsPreview()
                    screen == "class-schedules" -> ClassSchedulesPreview()
                    screen == "profile-avatar" -> HomeScreen(onLoggedOut = {})
                    screen == "schedule-weeks" -> ScheduleWeeksPreview()
                    screen.startsWith("week-picker") -> WeekPickerPreview(screen)
                    screen.startsWith("login") -> LoginPreview(screen)
                    else -> IconGallery(screen == "icons")
                }
            }
        }
    }
}

@Composable
private fun LoginPreview(screen: String) {
    var studentId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(when (screen) {
        "login-error" -> "请输入学号和密码"
        "login-long-error" -> "暂时无法验证你的登录信息，请检查学号和密码后重试。如仍无法登录，请联系软件协会管理员。"
        else -> null
    }) }
    var loading by rememberSaveable { mutableStateOf(screen == "login-loading") }
    LaunchedEffect(loading) {
        if (loading && screen != "login-loading") {
            delay(1500)
            loading = false
            error = "学号或密码有误，请重新输入"
        }
    }
    LoginContent(AuthViewModel.UiState(loading = loading, error = error), studentId, password,
        onStudentIdChange = { studentId = it; error = null },
        onPasswordChange = { password = it; error = null },
        onLogin = {
            if (studentId.isBlank() || password.isBlank()) error = "请输入学号和密码" else loading = true
        })
}

@Composable
private fun IconGallery(showCatalog: Boolean) {
    val colors = MaterialTheme.colorScheme
    var mode by rememberSaveable { mutableStateOf(AppMode.JW) }
    var details by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppLogo(Modifier.size(64.dp))
                Column {
                    Text("软协课表", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("日程清晰，生活从容", fontSize = 12.sp, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp))
                }
            }
            if (showCatalog) {
                Text("统一图标系统", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                AppIcons.catalog.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        row.forEach { (label, icon) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, Modifier.size(28.dp), tint = colors.onSurface)
                                Text(label.replace(" · 选中", " / 实心"), fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 10.dp), color = colors.onSurfaceVariant)
                            }
                        }
                        repeat(4 - row.size) { Column(Modifier.weight(1f)) {} }
                    }
                }
            } else {
                ModePickerHeader(details) { details = !details }
                Column(Modifier.selectableGroup()) {
                    AppMode.entries.forEach { item -> ModeOptionRow(item, mode == item, details) { mode = item } }
                }
                Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SyncIcon(true)
                    Text("同步时旋转，完成后静止", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        val icons = listOf(AppIcons.Schedule to AppIcons.ScheduleFilled, AppIcons.Grades to AppIcons.GradesFilled, AppIcons.Profile to AppIcons.ProfileFilled)
        NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
            listOf("课表", "成绩", "我的").forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index },
                    icon = { AnimatedAppIcon(icons[index].first, tab == index, selectedIcon = icons[index].second, tint = LocalContentColor.current) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.primary, selectedTextColor = colors.primary,
                        indicatorColor = colors.primary.copy(alpha = 0.065f), unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant))
            }
        }
    }
}
