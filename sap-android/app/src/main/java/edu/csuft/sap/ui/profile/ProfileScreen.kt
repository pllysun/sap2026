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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.BoundAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.ScreenHeader

private enum class ProfileRoute { NONE, SETTINGS, THEME, PRIVACY, ABOUT }

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLoggedOut: () -> Unit,
    vm: ProfileViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var showBind by remember { mutableStateOf(false) }
    var nicknameTarget by remember { mutableStateOf<BoundAccount?>(null) }
    var route by remember { mutableStateOf(ProfileRoute.NONE) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 拦截系统返回键：子页逐级回退（主题/隐私/关于→设置，设置→我的），避免一按返回就退到桌面
    BackHandler(enabled = route != ProfileRoute.NONE) {
        route = when (route) {
            ProfileRoute.THEME, ProfileRoute.PRIVACY, ProfileRoute.ABOUT -> ProfileRoute.SETTINGS
            else -> ProfileRoute.NONE
        }
    }

    LaunchedEffect(state.bindSuccess) {
        if (state.bindSuccess) { showBind = false; vm.consumeBindResult() }
    }

    // 退出确认弹窗：放在路由判断之前，使「我的」页与「设置」页两个入口都能弹出
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("退出后需重新输入会员账号密码登录。确定退出？") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; vm.logout(onLoggedOut) }) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") } },
        )
    }

    if (route != ProfileRoute.NONE) {
        AnimatedContent(
            targetState = route,
            label = "profileRoute",
            transitionSpec = {
                // 进入子页右滑入，返回时左滑出，配合淡入淡出
                val forward = targetState.ordinal >= initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { full -> dir * full / 4 } + fadeIn(tween(280)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { full -> -dir * full / 4 } + fadeOut(tween(280))
                    )
            },
        ) { r ->
            when (r) {
                ProfileRoute.SETTINGS -> AppSettingsScreen(
                    modifier = modifier,
                    mode = MemberState.mode,
                    onToggleMode = { on -> MemberState.setMode(ctx, if (on) AppMode.WEB else AppMode.JW) },
                    onTheme = { route = ProfileRoute.THEME },
                    onPrivacy = { route = ProfileRoute.PRIVACY },
                    onAbout = { route = ProfileRoute.ABOUT },
                    onLogout = { showLogoutConfirm = true },
                    onBack = { route = ProfileRoute.NONE },
                )
                ProfileRoute.THEME -> ThemeScreen(modifier = modifier, onBack = { route = ProfileRoute.SETTINGS })
                ProfileRoute.PRIVACY -> PrivacyScreen(modifier = modifier, onBack = { route = ProfileRoute.SETTINGS })
                ProfileRoute.ABOUT -> AboutScreen(modifier = modifier, onBack = { route = ProfileRoute.SETTINGS })
                ProfileRoute.NONE -> Unit
            }
        }
        return
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader("我的")
        if (state.loading && state.user == null) {
            LoadingBox()
            return@Column
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SapCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val initial = (state.user?.name ?: "会").take(1)
                    Box(
                        Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initial, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(state.user?.name ?: state.user?.nickname ?: "会员", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        state.user?.studentId?.let {
                            Text("学号 $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            val bound = state.accounts.filter { !it.isLocal } // 本地「WebVPN 课表」源不算教务绑定，不在此列出
            Box(Modifier.padding(top = 12.dp)) {
                SapCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("教务账号", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (bound.isNotEmpty()) {
                            TextButton(onClick = { showBind = true }) { Text("添加") }
                        }
                    }
                    if (bound.isEmpty()) {
                        Text("绑定后即可查看课表、成绩与考试安排；可绑定多个学号并随时切换。", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { showBind = true }, modifier = Modifier.padding(top = 12.dp)) { Text("绑定教务账号") }
                    } else {
                        bound.forEach { a ->
                            AccountRow(
                                account = a,
                                active = a.account == state.activeAccount,
                                onSwitch = { vm.switchAccount(a.account) },
                                onNickname = { nicknameTarget = a },
                                onUnbind = { vm.unbind(a.account) },
                            )
                        }
                    }
                }
            }

        }

        // 设置 + 退出登录 固定在底部
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            SapCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { route = ProfileRoute.SETTINGS },
                ) {
                    Text("设置", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            Box(Modifier.padding(top = 12.dp)) {
                SapCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showLogoutConfirm = true },
                    ) {
                        Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (showBind) {
        BindDialog(
            loading = state.bindLoading,
            error = state.bindError,
            captchaImage = state.captchaImage,
            onConfirm = vm::bind,
            onSubmitCaptcha = vm::submitCaptcha,
            onDismiss = { if (!state.bindLoading) { showBind = false; vm.consumeBindResult() } },
        )
    }
    nicknameTarget?.let { target ->
        NicknameDialog(
            account = target,
            onConfirm = { nick -> vm.setNickname(target.account, nick); nicknameTarget = null },
            onDismiss = { nicknameTarget = null },
        )
    }
}

@Composable
private fun AccountRow(
    account: BoundAccount,
    active: Boolean,
    onSwitch: () -> Unit,
    onNickname: () -> Unit,
    onUnbind: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(enabled = !active, onClick = onSwitch)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(account.nickname ?: account.account, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (account.nickname != null) account.account else "学号",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (active) {
                Box(
                    Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) { Text("当前", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary) }
            } else {
                Text("切换", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onNickname, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("备注", fontSize = 12.sp)
            }
            TextButton(onClick = onUnbind, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("解绑", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BindDialog(
    loading: Boolean,
    error: String?,
    captchaImage: String?,
    onConfirm: (String, String) -> Unit,
    onSubmitCaptcha: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    val needCaptcha = captchaImage != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (needCaptcha) "输入验证码" else "绑定教务账号") },
        text = {
            Column {
                if (needCaptcha) {
                    Text("教务要求验证码，自动识别未通过，请照图输入：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CaptchaImage(captchaImage)
                    OutlinedTextField(captcha, { captcha = it }, label = { Text("验证码") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    Text("使用你的学校统一身份认证（教务）账号密码", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(account, { account = it }, label = { Text("学校账号") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    OutlinedTextField(password, { password = it }, label = { Text("学校密码") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                if (needCaptcha) {
                    TextButton(onClick = { onSubmitCaptcha(captcha); captcha = "" }, enabled = !loading) { Text("确定") }
                } else {
                    TextButton(onClick = { onConfirm(account, password) }, enabled = !loading) { Text("绑定") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}

@Composable
private fun CaptchaImage(base64: String) {
    val bitmap = remember(base64) {
        runCatching {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = "验证码",
            modifier = Modifier.padding(top = 10.dp).size(width = 150.dp, height = 56.dp),
        )
    }
}

@Composable
private fun NicknameDialog(
    account: BoundAccount,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var nickname by remember { mutableStateOf(account.nickname ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注名") },
        text = {
            Column {
                Text("给学号 ${account.account} 起个好认的名字（留空则不显示）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(nickname, { nickname = it }, label = { Text("备注名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(nickname.ifBlank { null }) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
