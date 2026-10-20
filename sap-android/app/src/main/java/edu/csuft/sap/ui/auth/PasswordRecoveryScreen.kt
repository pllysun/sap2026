package edu.csuft.sap.ui.auth

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.AppLogo
import edu.csuft.sap.ui.icons.SyncIcon

@Composable
fun PasswordRecoveryScreen(initialAccount: String, onBack: () -> Unit, onRecovered: (String) -> Unit,
                           vm: PasswordRecoveryViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(initialAccount) { vm.prefill(initialAccount) }
    val leave = { if (state.done) onRecovered(state.form.account) else onBack() }
    BackHandler { if (!state.loading) leave() }
    PasswordRecoveryContent(state, vm::edit, vm::send, vm::reset, vm::refreshCaptcha, vm::requestAgain, leave)
}

@Composable
internal fun PasswordRecoveryContent(state: PasswordRecoveryViewModel.UiState,
    onEdit: (RecoveryField, String) -> Unit, onSend: () -> Unit, onReset: () -> Unit,
    onCaptcha: () -> Unit, onRequestAgain: () -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var visiblePassword by remember { mutableStateOf(false) }
    val verifying = state.requestId != null
    val submit = { focus.clearFocus(); keyboard?.hide(); if (verifying) onReset() else onSend() }
    val back = { focus.clearFocus(); keyboard?.hide(); onBack() }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 620.dp
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 16.dp)) {
            TextButton(onClick = back, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(AppIcons.Back, null, Modifier.size(20.dp))
                Text("返回登录", Modifier.padding(start = 6.dp))
            }
            Row(Modifier.padding(top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppLogo(Modifier.size(48.dp), description = null)
                Column {
                    Text("找回密码", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("找回软件协会账号，不修改学校教务密码", fontSize = 12.sp,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .6f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.done) {
                        Surface(color = colors.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Icon(AppIcons.Key, null, Modifier.padding(16.dp).size(28.dp), colors.onPrimaryContainer)
                        }
                        Text("密码已重置", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text("请使用新密码重新登录。这个账号在其他设备上的旧登录也已失效。",
                            fontSize = 14.sp, lineHeight = 22.sp, color = colors.onSurfaceVariant)
                        Button(onClick = back, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(13.dp)) { Text("使用新密码登录") }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf("1  验证账号", "2  重设密码").forEachIndexed { index, label ->
                                val selected = if (verifying) index == 1 else index == 0
                                Surface(color = if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = .45f),
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                    Text(label, Modifier.padding(vertical = 10.dp, horizontal = 12.dp), fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                                }
                            }
                        }
                        if (!verifying) {
                            Text("验证码发送到账号资料中的 QQ 邮箱，无需填写或更换收件人。",
                                color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 21.sp)
                            RecoveryInput("注册账号（学号）", state.form.account, { onEdit(RecoveryField.ACCOUNT, it) },
                                state.fieldErrors[RecoveryField.ACCOUNT], !state.loading, keyboard = KeyboardType.Ascii)
                            val bitmap = remember(state.captcha) { state.captcha?.let { runCatching {
                                BitmapFactory.decodeByteArray(it.image, 0, it.image.size)?.asImageBitmap()
                            }.getOrNull() } }
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RecoveryInput("图形验证码", state.form.captcha, { onEdit(RecoveryField.CAPTCHA, it) },
                                    state.fieldErrors[RecoveryField.CAPTCHA], !state.loading && !state.captchaLoading,
                                    modifier = Modifier.weight(1f), keyboard = KeyboardType.Ascii)
                                Surface(shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, colors.outlineVariant),
                                    modifier = Modifier.width(116.dp).height(56.dp).clickable(
                                        enabled = !state.loading && !state.captchaLoading, onClickLabel = "刷新图形验证码", onClick = onCaptcha)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when {
                                            state.captchaLoading -> SyncIcon(true, Modifier.size(22.dp))
                                            bitmap != null -> Image(bitmap, "图形验证码，点击刷新", Modifier.fillMaxWidth().padding(5.dp))
                                            else -> Text("点击加载", fontSize = 12.sp, color = colors.primary)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("账号 ${state.form.account}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("如账号存在且注册 QQ 有效，验证码将发送到该邮箱。请查收收件箱及垃圾箱，15 分钟内有效。",
                                color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 21.sp)
                            RecoveryInput("邮箱验证码", state.form.code, { onEdit(RecoveryField.CODE, it) },
                                state.fieldErrors[RecoveryField.CODE], !state.loading, keyboard = KeyboardType.NumberPassword)
                            RecoveryInput("新密码", state.form.password, { onEdit(RecoveryField.PASSWORD, it) },
                                state.fieldErrors[RecoveryField.PASSWORD], !state.loading, keyboard = KeyboardType.Password,
                                hint = "6–64 位，请勿与其他平台密码相同", passwordVisible = visiblePassword,
                                togglePassword = { visiblePassword = !visiblePassword })
                            RecoveryInput("再次输入新密码", state.form.confirmation, { onEdit(RecoveryField.CONFIRMATION, it) },
                                state.fieldErrors[RecoveryField.CONFIRMATION], !state.loading, keyboard = KeyboardType.Password,
                                passwordVisible = visiblePassword, done = true, onDone = submit)
                        }
                        state.error?.let { Text(it, fontSize = 12.sp, lineHeight = 19.sp, color = colors.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        if (state.expired) Text("验证码已过期，请重新获取。", color = colors.error, fontSize = 12.sp)
                        Button(onClick = submit, enabled = !state.loading && if (verifying) !state.expired else
                            !state.captchaLoading && state.captcha != null && state.remaining == 0,
                            shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (state.loading) SyncIcon(true, Modifier.size(18.dp))
                                Text(if (state.loading) { if (verifying) "正在重置" else "正在申请" }
                                    else if (verifying) "验证并重置密码" else if (state.remaining > 0) "${state.remaining} 秒后可再次发送" else "发送邮箱验证码")
                            }
                        }
                        if (verifying) TextButton(onClick = onRequestAgain, enabled = !state.loading && state.remaining == 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                            Text(if (state.remaining > 0) "${state.remaining} 秒后可重新获取" else "重新获取验证码", fontSize = 13.sp)
                        }
                    }
                }
            }
            if (!state.done) Text("每 3 分钟可申请一次，同一账号或 QQ 邮箱 24 小时最多 3 次。QQ 填错或收不到邮件，请联系软件协会管理员。",
                Modifier.padding(horizontal = 8.dp, vertical = 18.dp), fontSize = 12.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecoveryInput(label: String, value: String, change: (String) -> Unit, error: String?, enabled: Boolean,
    modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, hint: String = "",
    passwordVisible: Boolean = false, togglePassword: (() -> Unit)? = null, done: Boolean = false, onDone: () -> Unit = {}) {
    val focus = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(value, change, enabled = enabled, singleLine = true, isError = error != null,
            placeholder = if (hint.isNotEmpty()) ({ Text(hint, fontSize = 12.sp) }) else null,
            shape = RoundedCornerShape(13.dp), colors = authFieldColors(),
            visualTransformation = if (keyboard == KeyboardType.Password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = togglePassword?.let { toggle -> { IconButton(onClick = toggle, enabled = enabled) {
                Icon(if (passwordVisible) AppIcons.Visibility else AppIcons.VisibilityOff,
                    if (passwordVisible) "隐藏密码" else "显示密码", Modifier.size(20.dp))
            } } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (done) ImeAction.Done else ImeAction.Next,
                autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { if (enabled) onDone() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
        error?.let { Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}
