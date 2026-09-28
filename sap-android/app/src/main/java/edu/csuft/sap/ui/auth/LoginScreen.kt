package edu.csuft.sap.ui.auth

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.AppLogo
import edu.csuft.sap.ui.icons.SyncIcon
import edu.csuft.sap.ui.profile.PrivacyScreen

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onOffline: () -> Unit = {},
    vm: AuthViewModel = viewModel(),
    onRegister: () -> Unit = {},
    registeredStudentId: String? = null,
    onRegistrationConsumed: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var studentId by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registrationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(registeredStudentId) {
        registeredStudentId?.let {
            studentId = it
            password = ""
            registrationMessage = "注册成功，请使用新账号登录"
            vm.clearError()
            onRegistrationConsumed()
        }
    }

    // Consume terminal signals: the activity-scoped VM is reused after logout.
    LaunchedEffect(state.success) {
        if (state.success) { onLoggedIn(); vm.consumeSuccess() }
    }
    LaunchedEffect(state.offline) {
        if (state.offline) {
            Toast.makeText(context, "网络不可用，已进入离线模式", Toast.LENGTH_SHORT).show()
            onOffline(); vm.consumeOffline()
        }
    }
    LoginContent(
        state, studentId, password,
        onStudentIdChange = { studentId = it; if (state.error != null) vm.clearError() },
        onPasswordChange = { password = it; if (state.error != null) vm.clearError() },
        onLogin = {
            registrationMessage = null
            if (!state.loading) vm.login(studentId.trim(), password)
        },
        onAcceptMemberPrivacy = vm::acceptMemberPrivacy,
        onDeclineMemberPrivacy = vm::declineMemberPrivacy,
        onRegister = { registrationMessage = null; vm.clearError(); onRegister() },
        registrationMessage = registrationMessage,
    )
}

/** Stateless UI is also rendered by the debug gallery, without auth or network calls. */
@Composable
internal fun LoginContent(
    state: AuthViewModel.UiState,
    studentId: String,
    password: String,
    onStudentIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onAcceptMemberPrivacy: () -> Unit = {},
    onDeclineMemberPrivacy: () -> Unit = {},
    onRegister: () -> Unit = {},
    registrationMessage: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val recoveryUri = LocalUriHandler.current
    val recoveryContext = LocalContext.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(360), label = "loginEntrance")
    val submit = {
        if (!state.loading && !state.consentRequired) {
            focus.clearFocus()
            keyboard?.hide()
            onLogin()
        }
    }

    if (showPrivacy || state.consentRequired) {
        val memberAgreement = state.memberConsentRequired
        val needsConsent = state.consentRequired
        val dismiss = {
            showPrivacy = false
            if (needsConsent) onDeclineMemberPrivacy()
        }
        Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 460.dp)
                    .fillMaxWidth().fillMaxHeight(0.88f),
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
            ) {
                Column {
                    PrivacyScreen(modifier = Modifier.weight(1f), onBack = dismiss, member = memberAgreement)
                    if (needsConsent && state.error != null) {
                        Text(state.error, color = colors.error, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (needsConsent) "不同意" else "关闭")
                        }
                        if (needsConsent) {
                            Button(
                                onClick = {
                                    // 验证身份不保存会话，仅在用户确认协议后持久化并进入主页。
                                    if (needsConsent && !state.loading) {
                                        onAcceptMemberPrivacy()
                                    }
                                },
                                enabled = !state.loading,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text("同意并进入") }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding().clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val viewport = maxHeight
        val compact = viewport < 620.dp
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(horizontal = 24.dp, vertical = if (compact) 16.dp else 28.dp)
                .graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 12.dp.toPx()
                },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.padding(top = if (compact) 8.dp else 40.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogo(Modifier.size(if (compact) 48.dp else 60.dp), description = null)
                    Text("软协课表", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface, modifier = Modifier.padding(top = 14.dp))
                    Text("使用软件协会账号登录", fontSize = 13.sp,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }

                Spacer(Modifier.height(if (compact) 24.dp else 32.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(22.dp)) {
                        val fieldColors = authFieldColors()
                        registrationMessage?.let { message ->
                            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite }) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = colors.onPrimaryContainer)
                                    Text(message, fontSize = 13.sp, lineHeight = 19.sp, color = colors.onPrimaryContainer,
                                        modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Text("学号", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 7.dp))
                        OutlinedTextField(
                            value = studentId, onValueChange = onStudentIdChange,
                            placeholder = { Text("请输入学号", fontSize = 14.sp) },
                            leadingIcon = { Icon(AppIcons.Profile, null, Modifier.size(20.dp)) },
                            enabled = !state.loading, singleLine = true,
                            isError = state.error != null,
                            shape = RoundedCornerShape(13.dp), colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "学号" },
                        )
                        Text("密码", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 7.dp))
                        OutlinedTextField(
                            value = password, onValueChange = onPasswordChange,
                            placeholder = { Text("请输入密码", fontSize = 14.sp) },
                            leadingIcon = { Icon(AppIcons.Lock, null, Modifier.size(20.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.loading) {
                                    Icon(if (passwordVisible) AppIcons.Visibility else AppIcons.VisibilityOff,
                                        if (passwordVisible) "隐藏密码" else "显示密码",
                                        Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                                }
                            },
                            enabled = !state.loading, singleLine = true,
                            isError = state.error != null,
                            shape = RoundedCornerShape(13.dp), colors = fieldColors,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "密码" },
                        )
                        Box(Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(vertical = 7.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }) {
                            state.error?.let { Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = colors.error) }
                        }
                        Button(
                            onClick = submit, enabled = !state.loading,
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = colors.primary.copy(alpha = 0.72f),
                                disabledContentColor = colors.onPrimary,
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (state.loading) SyncIcon(true, Modifier.size(20.dp), tint = colors.onPrimary)
                                Text(if (state.loading) "正在登录" else "登录", fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        TextButton(
                            onClick = {
                                focus.clearFocus()
                                keyboard?.hide()
                                onRegister()
                            },
                            enabled = !state.loading && !state.consentRequired,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                        ) { Text("还没有账号？注册账号", fontSize = 13.sp, textAlign = TextAlign.Center) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    showPrivacy = true
                }, enabled = !state.loading) {
                    Text("隐私协议", fontSize = 13.sp)
                }
                TextButton(onClick = {
                    try { recoveryUri.openUri("https://csuftsap.top/forgot-password?from=app") }
                    catch (_: Exception) { Toast.makeText(recoveryContext, "无法打开找回页面，请联系管理员", Toast.LENGTH_LONG).show() }
                }, enabled = !state.loading) {
                    Text("忘记密码？", fontSize = 13.sp)
                }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    color = colors.primary.copy(alpha = 0.055f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Shield, null, Modifier.size(18.dp), tint = colors.primary)
                        Text("App 仅供内部使用，禁止分享", fontSize = 12.sp,
                            lineHeight = 18.sp, color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                    }
                }
                if (state.error != null) {
                    Text("QQ 填写有误或密码仍不正确，请联系管理员。", fontSize = 11.sp,
                        lineHeight = 18.sp, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Text("中南林业科技大学软件协会", fontSize = 11.sp, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 28.dp))
        }
    }
}
