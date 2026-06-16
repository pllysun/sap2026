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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.update.UpdateDialog
import edu.csuft.sap.update.UpdateViewModel

/** App 设置入口：隐私协议 / 关于 / 退出登录。 */
@Composable
fun AppSettingsScreen(
    modifier: Modifier = Modifier,
    mode: AppMode,
    onToggleMode: (Boolean) -> Unit,
    onTheme: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("设置", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card {
                ThemeRow(onTheme)
            }
            Spacer(Modifier.height(12.dp))
            // 模式切换：开 = Web 模式(网页课表，仅课表+设置)；关 = 教务模式(课表/成绩/我的)
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Web 模式", fontSize = 16.sp)
                        Text(
                            "开启后用网页(WebVPN)课表，仅保留课表与设置；关闭则为教务模式",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(checked = mode == AppMode.WEB, onCheckedChange = onToggleMode)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card {
                NavRow("隐私协议", onPrivacy)
                RowDivider()
                NavRow("关于", onAbout)
            }
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            ) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** 隐私协议。 */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("隐私协议", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Para("软协课表（以下简称“本应用”）非常重视你的隐私。本协议说明我们收集哪些信息、如何使用与存储，以及你拥有的权利。")
            Section("一、我们收集的信息")
            Para("1. 协会会员账号：用于登录本应用、校验会员身份。\n" +
                "2. 学校教务账号与密码：用于代你登录学校教务系统，抓取你的课表、成绩、考试安排、评教等数据。")
            Section("二、信息如何使用与存储")
            Para("· 你的教务密码经 AES 加密后存储在本应用后端服务器，仅用于自动登录学校教务系统抓取你本人的教务数据，不会明文保存、不会用于其他用途、不会提供给任何第三方。\n" +
                "· 登录凭证及课表/成绩/考试/评教等数据会缓存在你的设备本地（加密存储），以便离线查看并减少对教务系统的请求。")
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
            Para("面向协会会员的校园教务助手，提供课表、成绩、考试安排与一键评教等功能。" +
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
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
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
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("主题色", fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
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
