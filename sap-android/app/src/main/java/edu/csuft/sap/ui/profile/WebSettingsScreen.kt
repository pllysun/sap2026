package edu.csuft.sap.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.ScreenHeader

private enum class WebRoute { HOME, THEME, PRIVACY }

/**
 * Web 模式底栏「设置」页：个人信息(登录账号) + 修改主题色 + 隐私协议 + 退出登录。
 * 会员还可在此切回教务模式(非会员强制 WEB，不显示该开关)。复用 ProfileViewModel / ThemeScreen / PrivacyScreen。
 */
@Composable
fun WebSettingsScreen(
    modifier: Modifier = Modifier,
    onLoggedOut: () -> Unit,
    vm: ProfileViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var route by remember { mutableStateOf(WebRoute.HOME) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 拦截系统返回键：子页回退到设置首页，避免退到桌面
    BackHandler(enabled = route != WebRoute.HOME) { route = WebRoute.HOME }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("退出后需重新登录。确定退出？") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; vm.logout(onLoggedOut) }) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } },
        )
    }

    AnimatedContent(
        targetState = route,
        label = "webSettingsRoute",
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { full -> dir * full / 4 } + fadeIn(tween(280)))
                .togetherWith(slideOutHorizontally(tween(280)) { full -> -dir * full / 4 } + fadeOut(tween(280)))
        },
    ) { r ->
        when (r) {
            WebRoute.HOME -> Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ScreenHeader("设置")
                if (state.loading && state.user == null) {
                    LoadingBox()
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        // 个人信息（当前登录账号）
                        SapCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val initial = (state.user?.name ?: state.user?.nickname ?: "用").take(1)
                                Box(
                                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(initial, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(state.user?.name ?: state.user?.nickname ?: "当前账号", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    state.user?.studentId?.let {
                                        Text("学号 $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                        // 主题色
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard {
                                Row(
                                    Modifier.fillMaxWidth().clickable { route = WebRoute.THEME },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("主题色", fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 10.dp))
                                }
                            }
                        }
                        // 隐私协议
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard {
                                Row(
                                    Modifier.fillMaxWidth().clickable { route = WebRoute.PRIVACY },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("隐私协议", fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        // 模式切换：仅会员可见（非会员强制 Web 模式）
                        if (MemberState.isMember) {
                            Box(Modifier.padding(top = 12.dp)) {
                                SapCard {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Web 模式", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            Text("关闭切回教务模式（课表/成绩/我的）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Switch(
                                            checked = MemberState.mode == AppMode.WEB,
                                            onCheckedChange = { on -> MemberState.setMode(ctx, if (on) AppMode.WEB else AppMode.JW) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 退出登录固定底部
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        SapCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { showLogoutConfirm = true },
                            ) {
                                Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            WebRoute.THEME -> ThemeScreen(modifier = modifier, onBack = { route = WebRoute.HOME })
            WebRoute.PRIVACY -> PrivacyScreen(modifier = modifier, onBack = { route = WebRoute.HOME })
        }
    }
}
