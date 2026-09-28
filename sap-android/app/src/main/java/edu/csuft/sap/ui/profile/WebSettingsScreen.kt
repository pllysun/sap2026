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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.ModeOptionRow
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.ScreenHeader
import edu.csuft.sap.ui.feedback.FeedbackScreen
import edu.csuft.sap.ui.icons.ChevronIcon

private enum class WebRoute { HOME, PROFILE_EDIT, FEEDBACK, THEME, ANNOUNCEMENTS, PRIVACY, CHANGELOG }

/**
 * Web 模式底栏「设置」页：个人信息(登录账号) + 修改主题色 + 隐私协议 + 退出登录。
 * 完整 App 能力下还可在此切回教务模式。复用 ProfileViewModel / ThemeScreen / PrivacyScreen。
 */
@Composable
fun WebSettingsScreen(
    modifier: Modifier = Modifier,
    onLoggedOut: () -> Unit,
    vm: ProfileViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val avatarBitmap by vm.avatar.collectAsState()
    LaunchedEffect(vm) { vm.refresh() }
    val ctx = LocalContext.current
    var route by remember { mutableStateOf(WebRoute.HOME) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showModeInfo by remember { mutableStateOf(false) }

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
                    // 让退出入口与内容处于同一滚动容器：内容不足一屏时由最小高度推到页面底部，
                    // 内容超出一屏时随设置内容自然滚动，不会固定遮挡或脱离上下文。
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        // 个人信息：在线时可编辑；离线兜底时显示「离线模式」且不可点。
                        SapCard(onClick = if (ConnectivityState.online) ({ route = WebRoute.PROFILE_EDIT }) else null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (!ConnectivityState.online) {
                                    // 离线：当前账号位直接展示「离线模式」，不显示账号/学号
                                    Box(
                                        Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("离", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    Column(Modifier.padding(start = 14.dp)) {
                                        Text("离线模式", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                        Text("服务器暂时连不上，仅本地课表可用，联网后自动恢复", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    }
                                } else {
                                    ProfileAvatar(avatarBitmap, state.user?.name ?: state.user?.nickname)
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text(state.user?.name ?: state.user?.nickname ?: "当前账号", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                        state.user?.studentId?.let {
                                            Text("学号 $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        // 平台身份（游客 / 2025正式成员 / 2025宣传部部长 / 2026会长…）
                                        IdentityTags(identityLabels(state.identities, MemberState.roleCodes))
                                    }
                                    ChevronIcon()
                                }
                            }
                        }
                        // 主题色
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard(onClick = { route = WebRoute.THEME }) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("主题色", fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    ChevronIcon(Modifier.padding(start = 10.dp))
                                }
                            }
                        }
                        // 课表公告：联网刷新，断网时仍可查看最近一次缓存。
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard(onClick = { route = WebRoute.ANNOUNCEMENTS }) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("课表公告", fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    ChevronIcon()
                                }
                            }
                        }
                        // 隐私协议
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard(onClick = { route = WebRoute.PRIVACY }) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("隐私协议", fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    ChevronIcon()
                                }
                            }
                        }
                        // 更新日志（本地内置，离线也可查看）
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard(onClick = { route = WebRoute.CHANGELOG }) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("更新日志", fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    ChevronIcon()
                                }
                            }
                        }
                        // 所有已登录账号均可反馈；具体防滥用规则由接口在真正触发时返回提示。
                        Box(Modifier.padding(top = 12.dp)) {
                            SapCard(onClick = if (ConnectivityState.online) ({ route = WebRoute.FEEDBACK }) else null) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("意见反馈", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            if (ConnectivityState.online) "提交建议并查看维护者处理进度"
                                            else "当前离线，联网后可提交和查看反馈",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp))
                                    }
                                    ChevronIcon()
                                }
                            }
                        }
                        // 可用模式由云控决定；此处也支持游客在 Web 与班级课表间切换。
                        if (MemberState.availableModes.size > 1) {
                            Box(Modifier.padding(top = 12.dp)) {
                                SapCard {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text("课表模式", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                                Text("切换个人、网页或班级课表", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                            }
                                            TextButton(onClick = { showModeInfo = true }) { Text("模式说明") }
                                        }
                                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup()) {
                                            MemberState.availableModes.forEach { item ->
                                                ModeOptionRow(item, MemberState.effectiveMode == item, false, horizontalPadding = 0.dp) {
                                                    MemberState.requestMode(ctx, item)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                            // 离线模式下退出无意义；在线时作为滚动内容的最后一项。
                            if (ConnectivityState.online) {
                                Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp)) {
                                    SapCard(onClick = { showLogoutConfirm = true }) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                            ChevronIcon(Modifier.padding(start = 8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            WebRoute.PROFILE_EDIT -> ProfileEditScreen(modifier = modifier, vm = vm, onBack = { route = WebRoute.HOME })
            WebRoute.FEEDBACK -> FeedbackScreen(modifier = modifier, onBack = { route = WebRoute.HOME })
            WebRoute.THEME -> ThemeScreen(modifier = modifier, onBack = { route = WebRoute.HOME })
            WebRoute.ANNOUNCEMENTS -> AnnouncementScreen(modifier = modifier, onBack = { route = WebRoute.HOME })
            WebRoute.PRIVACY -> PrivacyScreen(modifier = modifier, onBack = { route = WebRoute.HOME }, offline = !ConnectivityState.online)
            WebRoute.CHANGELOG -> ChangelogScreen(modifier = modifier, onBack = { route = WebRoute.HOME })
        }
    }
    if (showModeInfo) {
        AlertDialog(
            onDismissRequest = { showModeInfo = false },
            title = { Text("课表模式说明") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text("教务课表\n自动采集个人各学期课表、成绩和考试安排；需要绑定教务账号。")
                    Text("Web课表\n在学校网页中自行登录并导入个人课表；账号密码不经过服务器，但不会自动获取成绩。")
                    Text("班级课表\n无需账号密码，按学期、学院、专业和班级选择公共课表；与个人课表可能因重修、选课而略有差异。")
                }
            },
            confirmButton = { TextButton(onClick = { showModeInfo = false }) { Text("知道了") } },
        )
    }
}
