package edu.csuft.sap.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.R
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.ui.common.ModeOptionRow
import edu.csuft.sap.ui.common.ModePickerHeader
import edu.csuft.sap.ui.common.pressFade
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.ChevronIcon
import edu.csuft.sap.ui.icons.IconBadge
import edu.csuft.sap.ui.icons.ModeIcon
import edu.csuft.sap.ui.icons.title
import edu.csuft.sap.update.Changelog
import edu.csuft.sap.update.UpdateDialog
import edu.csuft.sap.update.UpdateViewModel

/** App 设置入口：隐私协议 / 关于 / 退出登录。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppSettingsScreen(
    modifier: Modifier = Modifier,
    mode: AppMode,
    onSelectMode: (AppMode) -> Unit,
    onTheme: () -> Unit,
    onAnnouncements: () -> Unit,
    onPrivacy: () -> Unit,
    onChangelog: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    openModesInitially: Boolean = false,
    onModePickerOpened: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var showModeInfo by remember { mutableStateOf(false) }
    var showModes by remember { mutableStateOf(false) }
    LaunchedEffect(openModesInitially) {
        if (openModesInitially) {
            showModes = true
            onModePickerOpened()
        }
    }
    val modeSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectingMode by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("设置", onBack)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight).padding(20.dp)) {
            Text("课表与显示", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            // 三种课表模式统一入口；可见模式由云控与当前账号能力决定。
            Card {
                    Row(
                        Modifier.fillMaxWidth().clickable { showModes = true }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModeIcon(mode, Modifier.padding(end = 14.dp), selected = true, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(mode.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("当前课表模式 · 点击切换", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        ChevronIcon()
                    }
                    RowDivider()
                    ThemeRow(onTheme)
            }
            // 显示成绩：教务模式专属；关闭后教务模式底栏仅「课表 / 我的」
            if (mode == AppMode.JW) {
                Spacer(Modifier.height(12.dp))
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示成绩", fontSize = 16.sp)
                            Text(
                                "底栏显示「成绩」菜单；关闭后仅保留课表与我的",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Switch(checked = MemberState.showGrade, onCheckedChange = { MemberState.setShowGrade(ctx, it) })
                    }
                }
            }
            Text("帮助与关于", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp))
            Card {
                NavRow("课表公告", onAnnouncements, AppIcons.Bell)
                RowDivider()
                NavRow("隐私协议", onPrivacy, AppIcons.Shield)
                RowDivider()
                NavRow("更新日志", onChangelog, AppIcons.Update)
                RowDivider()
                NavRow("关于", onAbout, AppIcons.Info)
            }
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
        }
        }
    }
    if (showModes) {
        ModalBottomSheet(onDismissRequest = { showModes = false }, sheetState = modeSheet) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(bottom = 24.dp)) {
                ModePickerHeader(showModeInfo) { showModeInfo = !showModeInfo }
                MemberState.availableModes.forEach { item ->
                    ModeOptionRow(item, item == mode, showModeInfo) {
                        if (!selectingMode) {
                            selectingMode = true
                            val owner = edu.csuft.sap.data.account.CurrentAccount.key
                            scope.launch {
                                try {
                                    try { modeSheet.hide() }
                                    catch (e: kotlinx.coroutines.CancellationException) {
                                        // 拖动手势可取消动画，但不应吞掉已点击的模式；离开页面则仍取消。
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    }
                                    showModes = false
                                    androidx.compose.runtime.withFrameNanos { }
                                    if (owner == edu.csuft.sap.data.account.CurrentAccount.key && item != mode) onSelectMode(item)
                                } finally { selectingMode = false }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 隐私协议。
 * [offline]=true 时展示离线版本（完全不联网、不收集任何信息），优先级最高。
 * 按会员身份选择协议；登录前显式使用游客协议。
 */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit, member: Boolean = MemberState.hasFullAppFeatures, offline: Boolean = false) {
    if (offline) { OfflinePrivacyScreen(modifier, onBack); return }
    if (!member) { GuestPrivacyScreen(modifier, onBack); return }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("教务功能隐私协议", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Para("软协课表（以下简称“本应用”）非常重视你的隐私。本协议说明我们收集哪些信息、如何使用与存储，以及你拥有的权利。")
            Para("软件协会会员及以上身份，或云控开放完整能力的账号，可以使用教务、Web 和班级课表。登录不等于绑定教务账号；只有主动绑定后，服务器才会加密保存教务账号密码，用于同步个人教务数据。Web 与班级模式不托管教务密码。")
            Section("一、我们收集的信息")
            Para("1. 平台账号：用于登录本应用及校验账号身份。\n" +
                "2. 学校教务账号与密码：用于代你登录学校教务系统，抓取你的课表、成绩、考试安排、评教等数据。\n" +
                "3. 意见反馈：用户主动提交反馈时，会保存反馈内容、所选图片附件及 App 版本。只有点击上传图片后才会打开系统图片选择器，并仅获得你所选图片的读取权限；不会读取其他照片或收集设备唯一标识。\n" +
                "4. 课表背景：只有你主动点击选择图片时才会打开系统照片选择器，仅处理选中的图片并在裁剪后保存到本机 App 私有目录，不会上传服务器。")
            Section("二、信息如何使用与存储")
            Para("· 你的教务密码经 AES 加密后存储在本应用后端服务器，仅用于自动登录学校教务系统抓取你本人的教务数据，不会明文保存、不会用于其他用途、不会提供给任何第三方。\n" +
                "· 登录凭证及课表/成绩/考试/评教等数据会缓存在你的设备本地（加密存储），以便离线查看并减少对教务系统的请求。\n" +
                "· 反馈标题、正文、所选图片附件与讨论会在 Issue 中心共享展示。\n" +
                "· 课表背景图仅保存于你的设备本地，替换、移除背景或删除课表时会删除对应的私有副本。")
            Section("三、数据来源")
            Para("本应用所有教务数据均来自学校教务系统（统一身份认证 / 强智教务），本应用仅做代理抓取、整理与展示，不修改你的教务数据（评教功能除外，且评教仅在你主动点击后提交）。")
            Section("四、你的权利")
            Para("· 你可随时在「我的」页解绑教务账号，对应的加密凭证将从服务器删除。\n" +
                "· 你可随时退出登录，本地保存的登录凭证将被清除。")
            Section("五、第三方共享")
            Para("除你的学校教务系统外，本应用不会向任何第三方共享你的个人信息。")
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 游客仅使用 Web 与班级课表，不包含服务器托管教务密码的授权。 */
@Composable
fun GuestPrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("游客隐私协议", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Para("App 仅供内部使用，禁止分享。本协议适用于使用 Web 与班级课表的基础能力账号。如后续获得教务能力，会在首次使用前请你确认教务功能隐私协议。")
            Section("一、App 账号")
            Para("学号和 App 密码用于账号验证；同意协议后才在本机保存登录凭证，以便下次自动进入。App 不保存你输入的明文密码。Web 与班级课表为默认开放的基础功能。")
            Section("二、两种课表模式")
            Para("· Web 课表：你在学校认证网页自行登录，App 在本机解析个人课表。教务账号密码不由 App 服务器托管，网页登录状态（Cookie）可能保留在本机。\n" +
                "· 班级课表：无需教务账号密码，选择、下载并在本机缓存公共班级课表，可离线查看。\n" +
                "· 两种模式均不授权服务器保存教务密码，不自动采集个人成绩。")
            Section("三、App 功能与数据")
            Para("App 会处理账号资料及功能所需的访问、下载和操作日志。你主动提交的反馈及附件会保存并在 Issue 中心展示；课表背景仅在本机处理。仅在你主动选择图片时读取所选图片。找回 App 密码时，验证码发送到账号登记的 QQ 邮箱。")
            Section("四、管理自己的数据")
            Para("你可以退出登录清除本机登录凭证，在 Web 导入中通过「换账号」清除网页登录状态，并删除本地课表。账号资料有误或需要协助处理数据时，请联系软件协会管理员。")
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Web 模式隐私协议：端上抓取、不存教务密码、仅课表，数据权限与教务模式不同。 */
@Composable
fun WebPrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("隐私协议", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Para("软协课表（以下简称「本应用」）非常重视你的隐私。Web 模式下本应用仅在你的设备本地处理数据。本协议说明 Web 模式收集哪些信息、如何使用与存储，以及你拥有的权利。")
            Section("一、我们收集的信息")
            Para("1. 平台账号（如已登录）：用于校验身份。\n" +
                "2. Web 模式不收集、不存储你的学校教务账号与密码。你在应用内置的统一身份认证网页中自行登录，本应用不读取、不上传你的账号密码。\n" +
                "3. 用户主动提交意见反馈时，会保存反馈内容、所选图片附件及 App 版本。图片只在你点击上传并通过系统选择器选中后读取，不包含设备唯一标识。\n" +
                "4. 你主动选择的课表背景只会在本机裁剪并保存，不会上传服务器。")
            Section("二、信息如何使用与存储")
            Para("· 你的网页登录态（Cookie）仅保存在你的设备本地，用于免重复登录，本应用不会上传到服务器。\n" +
                "· 仅在你主动点击「导入课表」后，本应用在你的设备上（端侧）解析当前网页中的课表数据并保存在本地，用于离线展示；课表数据不上传服务器。\n" +
                "· 课表背景的裁剪副本仅保存在 App 私有目录。")
            Section("三、数据范围")
            Para("Web 模式仅抓取并展示你的课表，不抓取成绩、考试安排等其它教务数据（这与教务模式不同）。")
            Section("四、你的权利")
            Para("· 你可随时在导入页点「换账号」清除网页登录态，或退出登录以清除本地登录态与网页缓存。\n" +
                "· 本地保存的课表可随时删除。")
            Section("五、第三方共享")
            Para("除你在网页中自行访问的学校教务系统外，本应用不会向任何第三方共享你的个人信息。")
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 离线模式隐私协议：完全离线、不联网，不收集/不上传/不存储任何信息，仅本地展示已缓存课表。 */
@Composable
fun OfflinePrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("隐私协议", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Para("软协课表（以下简称「本应用」）非常重视你的隐私。当前为离线模式：本应用未连接任何服务器，不收集、不上传、不存储你的任何个人信息。")
            Section("一、我们不收集任何信息")
            Para("离线模式下，本应用不收集你的平台账号、学校教务账号与密码、设备标识、位置等任何个人信息，也不会发起任何网络请求或数据上传。")
            Section("二、数据如何处理")
            Para("· 本应用仅在你的设备本地读取此前已缓存的课表数据用于离线展示。\n" +
                "· 你主动选择并裁剪的课表背景仅保存在 App 私有目录。\n" +
                "· 所有数据均保存在你的设备本地，不会离开你的设备。")
            Section("三、数据范围")
            Para("离线模式支持切换本地 Web 课表和已下载的班级课表，包括已缓存学期及开学日期，不抓取或下载新数据。本机会记住你选择的班级；恢复联网后按云控和账号权限提供在线功能。")
            Section("四、你的权利")
            Para("· 本地保存的课表可随时删除。\n" +
                "· 离线模式下无需登录或退出，本应用不持有任何可在离线状态下收集的信息。")
            Section("五、第三方共享")
            Para("离线模式下本应用不联网，不会向任何第三方共享任何信息。")
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 更新日志：展示全部历史版本的更新内容（本地内置，离线可见）。 */
@Composable
fun ChangelogScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("更新日志", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Changelog.entries.forEachIndexed { i, e ->
                if (i > 0) Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("v${e.versionName}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("  (build ${e.versionCode})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(e.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(6.dp))
                e.changes.forEach { c ->
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("·  ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 关于：软件版本与相关信息。 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val updateVm: UpdateViewModel = viewModel()
    val context = LocalContext.current
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("关于", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "软协课表",
                modifier = Modifier.padding(top = 16.dp).size(96.dp)
                    .clip(RoundedCornerShape(22.dp)),
            )
            Text("软协课表", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
            Text(
                "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Box(Modifier.padding(top = 24.dp).fillMaxWidth()) {
                Card {
                    NavRow("检查更新", onClick = { updateVm.check(manual = true) }, icon = AppIcons.Refresh)
                    RowDivider()
                    InfoRow("应用名称", "软协课表")
                    RowDivider()
                    InfoRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    RowDivider()
                    InfoRow("包名", BuildConfig.APPLICATION_ID)
                    RowDivider()
                    InfoRow("开发", "中南林业科技大学软件协会")
                }
            }
            Para("面向校内用户的校园教务助手，提供课表、成绩、考试安排与教学评价等功能。" +
                "教务数据均来自学校教务系统，仅供本人查看，请勿用于非法用途。")
            Text(
                "© 2026 中南林业科技大学软件协会",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    UpdateDialog(
        state = updateVm.state.collectAsState().value,
        onDownload = { updateVm.download(context) },
        onInstall = { updateVm.install(context) },
        onDismiss = { updateVm.dismiss() },
    )
}

// ---------- 共用小组件 ----------

@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        // 先 clip 再 background，保证整行按下高亮被裁到圆角内（首尾行不溢出圆角）
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp)
        .background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun ThemeRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressFade(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(AppIcons.Palette, Modifier.padding(end = 12.dp), size = 34.dp)
        Text("主题色", fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        ChevronIcon(Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Row(
        Modifier.fillMaxWidth().pressFade(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { IconBadge(it, Modifier.padding(end = 12.dp), size = 34.dp) }
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        ChevronIcon()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Para(text: String) {
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
}
