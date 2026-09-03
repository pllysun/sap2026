package edu.csuft.sap.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
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
import edu.csuft.sap.BuildConfig
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import edu.csuft.sap.R
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.ui.common.pressFade
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.update.Changelog
import edu.csuft.sap.update.UpdateDialog
import edu.csuft.sap.update.UpdateViewModel

/** App 设置入口：隐私协议 / 关于 / 退出登录。 */
@Composable
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
) {
    val ctx = LocalContext.current
    var showModeInfo by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("设置", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card {
                ThemeRow(onTheme)
            }
            Spacer(Modifier.height(12.dp))
            // 三种课表模式统一入口；可见模式由云控与当前账号能力决定。
            Card {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("课表模式", fontSize = 16.sp)
                            Text("选择你要使用的课表来源", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        TextButton(onClick = { showModeInfo = true }) { Text("模式说明") }
                    }
                    MemberState.availableModes.forEachIndexed { index, item ->
                        if (index > 0) RowDivider()
                        ModeOptionRow(
                            mode = item,
                            selected = item == mode,
                            onClick = { onSelectMode(item) },
                        )
                    }
                }
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
            Spacer(Modifier.height(12.dp))
            Card {
                NavRow("课表公告", onAnnouncements)
                RowDivider()
                NavRow("隐私协议", onPrivacy)
                RowDivider()
                NavRow("更新日志", onChangelog)
                RowDivider()
                NavRow("关于", onAbout)
            }
            Spacer(Modifier.height(20.dp))
        }
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
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

/**
 * 隐私协议。
 * [offline]=true 时展示离线版本（完全不联网、不收集任何信息），优先级最高。
 * [web]=true 时展示 Web 模式版本（端上抓取、不存教务密码、仅课表），数据权限与教务模式不同。
 */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit, web: Boolean = false, offline: Boolean = false) {
    if (offline) { OfflinePrivacyScreen(modifier, onBack); return }
    if (web) { WebPrivacyScreen(modifier, onBack); return }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("隐私协议", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Para("软协课表（以下简称“本应用”）非常重视你的隐私。本协议说明我们收集哪些信息、如何使用与存储，以及你拥有的权利。")
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
            Para("离线模式仅展示你设备本地已有的课表，不抓取、不更新任何教务数据；恢复联网后才会按你所选模式重新提供在线功能。")
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
                    NavRow("检查更新", onClick = { updateVm.check(manual = true) })
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),
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
        Text("主题色", fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 10.dp))
    }
}

/** 课表模式切换行：在窄屏上纵向展示标题和说明，避免三个横向标签挤压或截断。 */
@Composable
private fun ModeOptionRow(mode: AppMode, selected: Boolean, onClick: () -> Unit) {
    val title = when (mode) {
        AppMode.JW -> "教务课表"
        AppMode.WEB -> "Web课表"
        AppMode.CLASS -> "班级课表"
    }
    val subtitle = when (mode) {
        AppMode.JW -> "自动采集个人课表、成绩和考试安排"
        AppMode.WEB -> "自行登录学校网页，导入个人课表"
        AppMode.CLASS -> "无需账号，按班级查看公共课表"
    }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(AppIcons.Check, "当前模式", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressFade(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
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
