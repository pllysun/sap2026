package edu.csuft.sap.ui.auth

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.AppLogo
import edu.csuft.sap.ui.icons.SyncIcon

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegistered: (String) -> Unit,
    vm: RegistrationViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.registeredStudentId) { state.registeredStudentId?.let(onRegistered) }
    // 显式处理注册页返回，避免导航组件在输入法收起/返回栈切换时重复处理预测返回。
    // 提交期间等待结果，防止服务端已注册而客户端丢失成功提示。
    BackHandler { if (!state.loading) onBack() }
    RegistrationContent(state, vm::edit, vm::submit, vm::refreshCaptcha, onBack, vm::captchaImageFailed, vm::sendEmailCode)
}

@Composable
internal fun RegistrationContent(
    state: RegistrationViewModel.UiState,
    onEdit: (RegistrationField, String) -> Unit,
    onSubmit: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    onBack: () -> Unit,
    onCaptchaImageFailed: (String) -> Unit = {},
    onSendEmailCode: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fields = remember { RegistrationField.entries.associateWith { FocusRequester() } }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (state.canSubmit) {
            focus.clearFocus()
            keyboard?.hide()
            onSubmit()
        }
    }
    val sendEmail = {
        if (state.canSendEmail) {
            focus.clearFocus()
            keyboard?.hide()
            onSendEmailCode()
        }
    }
    val back = {
        if (!state.loading) {
            focus.clearFocus()
            keyboard?.hide()
            onBack()
        }
    }
    LaunchedEffect(state.validationAttempt) {
        state.fieldErrors.keys.firstOrNull { it != RegistrationField.GENDER }?.let { fields.getValue(it).requestFocus() }
    }
    LaunchedEffect(state.captcha?.id) {
        if (state.captchaRequired && state.captcha != null) fields.getValue(RegistrationField.CAPTCHA).requestFocus()
    }
    LaunchedEffect(state.emailRequestId) {
        if (state.emailRequestId != null) fields.getValue(RegistrationField.EMAIL_CODE).requestFocus()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding().clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = maxHeight < 620.dp
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 16.dp),
        ) {
            TextButton(onClick = back, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(AppIcons.Back, null, Modifier.size(20.dp))
                Text("返回登录", fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLogo(Modifier.size(48.dp), description = null)
                Column(Modifier.weight(1f)) {
                    Text("创建账号", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text("加入中南林业科技大学软件协会", fontSize = 12.sp, lineHeight = 18.sp,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(24.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RegistrationTextField(
                        "学号", state.form.studentId, { onEdit(RegistrationField.STUDENT_ID, it.take(20)) }, "请输入学号",
                        state.fieldErrors[RegistrationField.STUDENT_ID], !state.busy, AppIcons.Profile,
                        keyboardType = KeyboardType.Ascii,
                        onNext = { fields.getValue(RegistrationField.PASSWORD).requestFocus() },
                        modifier = Modifier.focusRequester(fields.getValue(RegistrationField.STUDENT_ID)),
                    )
                    RegistrationTextField(
                        "密码", state.form.password, { onEdit(RegistrationField.PASSWORD, it.take(64)) }, "设置 6–64 位密码",
                        state.fieldErrors[RegistrationField.PASSWORD], !state.busy, AppIcons.Lock,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.busy) {
                                Icon(if (passwordVisible) AppIcons.Visibility else AppIcons.VisibilityOff,
                                    if (passwordVisible) "隐藏密码" else "显示密码", Modifier.size(20.dp))
                            }
                        },
                        onNext = { fields.getValue(RegistrationField.NAME).requestFocus() },
                        modifier = Modifier.focusRequester(fields.getValue(RegistrationField.PASSWORD)),
                    )
                    RegistrationTextField(
                        "姓名", state.form.name, { onEdit(RegistrationField.NAME, it.take(50)) }, "请输入真实姓名",
                        state.fieldErrors[RegistrationField.NAME], !state.busy, AppIcons.Profile,
                        onNext = { fields.getValue(RegistrationField.QQ).requestFocus() },
                        modifier = Modifier.focusRequester(fields.getValue(RegistrationField.NAME)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("性别", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(1 to "男", 0 to "女").forEach { (value, label) ->
                                val selected = state.form.gender == value
                                Surface(
                                    shape = RoundedCornerShape(13.dp),
                                    color = if (selected) colors.primary.copy(alpha = 0.07f) else colors.surfaceVariant.copy(alpha = 0.65f),
                                    border = if (selected) BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)) else null,
                                    modifier = Modifier.weight(1f).selectable(selected = selected, enabled = !state.busy,
                                        role = Role.RadioButton, onClick = { onEdit(RegistrationField.GENDER, value.toString()) }),
                                ) {
                                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected, onClick = null, enabled = !state.busy, modifier = Modifier.size(24.dp))
                                        Text(label, fontSize = 14.sp, color = if (selected) colors.primary else colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    RegistrationTextField(
                        "QQ 号", state.form.qq, { onEdit(RegistrationField.QQ, it.take(15)) }, "请输入 QQ 号",
                        state.fieldErrors[RegistrationField.QQ], !state.busy, AppIcons.Feedback,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        onNext = { fields.getValue(if (state.captchaRequired) RegistrationField.CAPTCHA else RegistrationField.EMAIL_CODE).requestFocus() },
                        modifier = Modifier.focusRequester(fields.getValue(RegistrationField.QQ)),
                    )
                    Text(if (state.form.qq.trim().matches(Regex("[1-9][0-9]{4,14}")))
                        "验证邮箱：${state.form.qq.trim()}@qq.com" else "手机端注册需要验证 QQ 邮箱，请准确填写 QQ 号",
                        fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
                    if (state.captchaRequired) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("发送邮箱验证码前，请完成图片验证", fontSize = 12.sp, lineHeight = 18.sp, color = colors.primary,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            CaptchaImage(state, onRefreshCaptcha, onCaptchaImageFailed)
                            state.error?.let {
                                Text(it, color = colors.error, fontSize = 12.sp, lineHeight = 18.sp,
                                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            RegistrationTextField(
                                "图形验证码", state.form.captchaCode, { onEdit(RegistrationField.CAPTCHA, it.take(6)) }, "请输入图中字符",
                                state.fieldErrors[RegistrationField.CAPTCHA], !state.busy && state.captcha != null, AppIcons.Shield,
                                keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, onDone = sendEmail,
                                modifier = Modifier.focusRequester(fields.getValue(RegistrationField.CAPTCHA)),
                            )
                        }
                    }
                    OutlinedButton(onClick = sendEmail, enabled = state.canSendEmail,
                        shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(when {
                            state.emailLoading -> "正在申请验证码"
                            state.captchaLoading -> "加载图形验证码"
                            state.cooldownSeconds > 0 -> "${state.cooldownSeconds} 秒后可重新获取"
                            state.captchaRequired -> "发送邮箱验证码"
                            state.emailRequestId != null -> "重新获取邮箱验证码"
                            else -> "获取邮箱验证码"
                        }, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    state.emailNotice?.let {
                        Text(it, color = colors.primary, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    state.error?.takeIf { !state.captchaRequired }?.let {
                        Text(it, color = colors.error, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    RegistrationTextField(
                        "邮箱验证码", state.form.emailCode, { onEdit(RegistrationField.EMAIL_CODE, it.take(6)) }, "QQ 邮箱中的六位验证码",
                        state.fieldErrors[RegistrationField.EMAIL_CODE], !state.busy, AppIcons.Shield,
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, onDone = submit,
                        modifier = Modifier.focusRequester(fields.getValue(RegistrationField.EMAIL_CODE)),
                    )
                    Button(
                        onClick = submit, enabled = state.canSubmit,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = colors.primary.copy(alpha = 0.72f),
                            disabledContentColor = colors.onPrimary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (state.busy) SyncIcon(true, Modifier.size(20.dp), tint = colors.onPrimary)
                            Text(if (state.loading) "正在注册" else "验证邮箱并注册",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            TextButton(onClick = back, enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text("已有账号？去登录", fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RegistrationTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String?,
    enabled: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value, onValueChange, enabled = enabled, singleLine = true, isError = error != null,
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(13.dp), colors = authFieldColors(),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction,
                capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
            modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                // 硬件 Enter 的按下/抬起必须完整消费；提交清焦点后，抬起不能误点返回按钮。
                if (imeAction == ImeAction.Done && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                    if (event.type == KeyEventType.KeyUp) onDone()
                    true
                } else false
            }.semantics { contentDescription = label },
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun CaptchaImage(
    state: RegistrationViewModel.UiState,
    onRefresh: () -> Unit,
    onImageFailed: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val captcha = state.captcha
    val bitmap = remember(captcha) {
        captcha?.image?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
    }
    LaunchedEffect(captcha, bitmap) { if (captcha != null && bitmap == null) onImageFailed(captcha.id) }
    Surface(
        onClick = onRefresh, enabled = !state.busy,
        color = colors.surfaceVariant.copy(alpha = 0.65f), shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "刷新验证码" },
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bitmap != null) {
                Image(bitmap, "图形验证码", contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 180.dp).fillMaxWidth().height(56.dp))
            } else if (state.captchaLoading) {
                SyncIcon(true, Modifier.size(24.dp), tint = colors.primary)
            }
            Text(if (state.captchaLoading) "正在加载验证码…" else if (bitmap != null) "看不清？点击换一张" else "点击获取验证码",
                fontSize = 12.sp, lineHeight = 18.sp, color = colors.primary, textAlign = TextAlign.Center)
            state.captchaError?.let {
                Text(it, color = colors.error, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}
