package edu.csuft.sap.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.BoundAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.ScreenHeader
import edu.csuft.sap.ui.icons.AppIcons
import kotlinx.coroutines.launch

private enum class ProfileRoute { NONE, PROFILE_EDIT, JW_ACCOUNTS, SETTINGS, THEME, PRIVACY, CHANGELOG, ABOUT }

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLoggedOut: () -> Unit,
    vm: ProfileViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var route by remember { mutableStateOf(ProfileRoute.NONE) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 拦截系统返回键：子页逐级回退（主题/隐私/关于→设置，设置→我的），避免一按返回就退到桌面
    BackHandler(enabled = route != ProfileRoute.NONE) {
        route = when (route) {
            ProfileRoute.THEME, ProfileRoute.PRIVACY, ProfileRoute.CHANGELOG, ProfileRoute.ABOUT -> ProfileRoute.SETTINGS
            else -> ProfileRoute.NONE
        }
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
                ProfileRoute.PROFILE_EDIT -> ProfileEditScreen(
                    modifier = modifier, vm = vm, onBack = { route = ProfileRoute.NONE },
                )
                ProfileRoute.JW_ACCOUNTS -> JwAccountsScreen(
                    modifier = modifier, vm = vm, onBack = { route = ProfileRoute.NONE },
                )
                ProfileRoute.SETTINGS -> AppSettingsScreen(
                    modifier = modifier,
                    mode = MemberState.mode,
                    onToggleMode = { on ->
                        MemberState.setMode(ctx, if (on) AppMode.WEB else AppMode.JW)
                        // 切 Web→本地源；切回教务→自动激活上次的教务账号（无则提示绑定）
                        if (on) Graph.accountManager.useWebview() else Graph.accountManager.activateJwAccount()
                    },
                    onTheme = { route = ProfileRoute.THEME },
                    onPrivacy = { route = ProfileRoute.PRIVACY },
                    onChangelog = { route = ProfileRoute.CHANGELOG },
                    onAbout = { route = ProfileRoute.ABOUT },
                    onLogout = { showLogoutConfirm = true },
                    onBack = { route = ProfileRoute.NONE },
                )
                ProfileRoute.THEME -> ThemeScreen(modifier = modifier, onBack = { route = ProfileRoute.SETTINGS })
                ProfileRoute.PRIVACY -> PrivacyScreen(modifier = modifier, onBack = { route = ProfileRoute.SETTINGS })
                ProfileRoute.CHANGELOG -> ChangelogScreen(modifier = modifier, onBack = { route = ProfileRoute.SETTINGS })
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
            val avatarUrl = avatarUrlOf(state.user?.avatar, state.avatarVersion)
            // 用户信息卡：点击进入「个人信息」二级菜单（查看/编辑软协平台资料）
            SapCard(onClick = { route = ProfileRoute.PROFILE_EDIT }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(model = avatarUrl, contentDescription = "头像", contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
                        } else {
                            Text((state.user?.name ?: "会").take(1), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(state.user?.name ?: state.user?.nickname ?: "会员", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        state.user?.studentId?.let {
                            Text("学号 $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                        // 平台身份（游客 / 2025正式成员 / 2025宣传部部长 / 2026会长…）
                        IdentityTags(identityLabels(state.identities, MemberState.roleCodes))
                    }
                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // 教务账号：二级菜单（绑定 / 切换 / 备注 / 解绑 都在子页里管理）
            val boundCount = state.accounts.count { !it.isLocal }
            Box(Modifier.padding(top = 12.dp)) {
                SapCard(onClick = { route = ProfileRoute.JW_ACCOUNTS }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("教务账号", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (boundCount > 0) "已绑定 $boundCount 个学号，点击管理" else "未绑定，点击绑定",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // 设置 固定在底部（退出登录已移至「设置」页内）
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            SapCard(onClick = { route = ProfileRoute.SETTINGS }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设置", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

}

/** 顶部返回栏（子页共用）。 */
@Composable
private fun SubBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/** 教务账号二级菜单：绑定 / 切换 / 备注 / 解绑。 */
@Composable
private fun JwAccountsScreen(modifier: Modifier, vm: ProfileViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var showBind by remember { mutableStateOf(false) }
    var nicknameTarget by remember { mutableStateOf<BoundAccount?>(null) }
    LaunchedEffect(state.bindSuccess) { if (state.bindSuccess) { showBind = false; vm.consumeBindResult() } }

    val bound = state.accounts.filter { !it.isLocal }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubBar("教务账号", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Box {
                SapCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("已绑定学号", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (bound.isNotEmpty()) TextButton(onClick = { showBind = true }) { Text("添加") }
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
    }

    if (showBind) {
        BindDialog(
            loading = state.bindLoading,
            error = state.bindError,
            captchaImage = state.captchaImage,
            mfaPhone = state.mfaPhone,
            onConfirm = vm::bind,
            onSubmitCaptcha = vm::submitCaptcha,
            onSubmitMfa = vm::submitMfa,
            onResendMfa = vm::resendMfa,
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

/** 个人信息二级菜单：显示软协平台资料 + 修改网名/性别/头像（复用用户端 /api/auth/profile）。 */
@Composable
fun ProfileEditScreen(modifier: Modifier, vm: ProfileViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val user = state.user

    var nickname by remember(user?.id) { mutableStateOf(user?.nickname ?: "") }
    var gender by remember(user?.id) { mutableStateOf(user?.gender ?: 0) }
    var avatar by remember(user?.id) { mutableStateOf(user?.avatar ?: "") }
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                vm.clearProfileError()
                val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                val url = if (bytes != null) vm.uploadAvatar(bytes, "avatar.jpg") else null
                uploading = false
                if (url != null) avatar = url
            }
        }
    }
    val avatarShown = avatarUrlOf(avatar, state.avatarVersion)

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubBar("个人信息", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 头像（点击更换）
            Box(
                Modifier.padding(top = 8.dp).size(96.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = !uploading) { picker.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uploading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    avatarShown != null -> AsyncImage(model = avatarShown, contentDescription = "头像", contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                    else -> Text((user?.name ?: "会").take(1), fontSize = 34.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text("点击更换头像", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

            Spacer(Modifier.height(20.dp))
            SapCard {
                InfoLine("姓名", user?.name ?: "—")
                ThinDivider()
                InfoLine("学号", user?.studentId ?: "—")
                if (!user?.grade.isNullOrBlank()) { ThinDivider(); InfoLine("年级", user?.grade!!) }
                ThinDivider()
                InfoLine("身份", identityLabels(state.identities, MemberState.roleCodes).joinToString(" / "))
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = nickname, onValueChange = { nickname = it },
                label = { Text("网名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 性别选择
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("性别", fontSize = 15.sp, modifier = Modifier.weight(1f))
                GenderChip("男", gender == 1) { gender = 1 }
                Spacer(Modifier.width(8.dp))
                GenderChip("女", gender == 2) { gender = 2 }
            }

            state.profileError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }

            Button(
                onClick = { vm.saveProfile(nickname.trim().ifBlank { null }, gender, avatar.ifBlank { null }, onBack) },
                enabled = !state.profileSaving && !uploading,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                if (state.profileSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("保存")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThinDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun GenderChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) { Text(text, fontSize = 14.sp, color = fg) }
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
    mfaPhone: String?,
    onConfirm: (String, String) -> Unit,
    onSubmitCaptcha: (String) -> Unit,
    onSubmitMfa: (String) -> Unit,
    onResendMfa: () -> Unit,
    onDismiss: () -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }
    val needCaptcha = captchaImage != null
    val needMfa = mfaPhone != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { needCaptcha -> "输入验证码"; needMfa -> "短信二次验证"; else -> "绑定教务账号" }) },
        text = {
            Column {
                when {
                    needCaptcha -> {
                        Text("教务要求验证码，自动识别未通过，请照图输入：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CaptchaImage(captchaImage)
                        OutlinedTextField(captcha, { captcha = it }, label = { Text("验证码") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    needMfa -> {
                        Text(
                            if (mfaPhone.isNullOrBlank()) "该账号开启了短信二次验证，验证码已发送至你的安全手机，请输入："
                            else "该账号开启了短信二次验证，验证码已发送至 $mfaPhone，请输入：",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(mfaCode, { mfaCode = it }, label = { Text("短信验证码") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        TextButton(onClick = onResendMfa, enabled = !loading, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("没收到？重新发送", fontSize = 12.sp)
                        }
                    }
                    else -> {
                        Text("使用你的学校统一身份认证（教务）账号密码", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(account, { account = it }, label = { Text("学校账号") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        OutlinedTextField(password, { password = it }, label = { Text("学校密码") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                when {
                    needCaptcha -> TextButton(onClick = { onSubmitCaptcha(captcha); captcha = "" }, enabled = !loading) { Text("确定") }
                    needMfa -> TextButton(onClick = { onSubmitMfa(mfaCode); mfaCode = "" }, enabled = !loading) { Text("确定") }
                    else -> TextButton(onClick = { onConfirm(account, password) }, enabled = !loading) { Text("绑定") }
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
