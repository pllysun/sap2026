package edu.csuft.sap.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.ModeGuideKind
import edu.csuft.sap.ui.icons.ModeIcon
import edu.csuft.sap.ui.icons.title
import kotlinx.coroutines.delay

private data class GuidePage(val title: String, val detail: String, val mode: AppMode?)

/** 只有可用的模式会被介绍；对后来解锁教务能力的游客单独补一页。 */
@Composable
fun ModeGuideOverlay(kind: ModeGuideKind, availableModes: List<AppMode>,
                     onDismiss: () -> Unit, onOpenModePicker: () -> Unit) {
    val pages = remember(kind, availableModes) {
        if (kind == ModeGuideKind.ACADEMIC_UNLOCKED) listOf(
            GuidePage("教务课表已开放", "现在可以使用教务课表。它同步你本人的课表和成绩，需要先同意教务模式协议并绑定教务账号。", AppMode.JW),
        ) else buildList {
            add(GuidePage("选择你的课表方式", "在「我的 → 设置 → 课表模式」切换。下面只介绍当前账号可用的方式。", null))
            for (mode in availableModes) add(when (mode) {
                AppMode.JW -> GuidePage(mode.title, "自动同步个人课表与成绩，与你的教务课表一致。需绑定教务账号；密码加密托管，解绑可清除。", mode)
                AppMode.WEB -> GuidePage(mode.title, "自行登录学校网页导入个人课表，与你的教务课表一致；不托管密码，每次导入需重新登录。", mode)
                AppMode.CLASS -> GuidePage(mode.title, "无需教务账号，按班级查看公共课表。实验课、重修与选课等可能缺失，请务必和自己的课表核对。", mode)
            })
        }
    }
    var page by remember(kind) { mutableIntStateOf(0) }
    var demoMode by remember(kind) { mutableIntStateOf(0) }
    LaunchedEffect(kind, page, availableModes) {
        if (kind == ModeGuideKind.INTRO && page == 0 && availableModes.isNotEmpty()) {
            while (true) {
                delay(1400)
                demoMode = (demoMode + 1) % availableModes.size
            }
        }
    }
    val motion = rememberInfiniteTransition(label = "guidePulse")
    val scale by motion.animateFloat(0.96f, 1.05f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "guideIconScale")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 22.dp), shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(if (kind == ModeGuideKind.INTRO) "快速上手" else "新能力", color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                AnimatedContent(targetState = page, label = "guidePage", transitionSpec = {
                    (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(180)))
                }) { index ->
                    val item = pages[index.coerceIn(0, pages.lastIndex)]
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(78.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center) {
                            if (item.mode != null) ModeIcon(item.mode, selected = true, size = 56.dp)
                            else if (availableModes.isNotEmpty()) ModeIcon(availableModes[demoMode % availableModes.size], selected = true, size = 56.dp)
                        }
                        Spacer(Modifier.height(23.dp))
                        Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp,
                            textAlign = TextAlign.Center)
                        if (item.mode == null) {
                            Spacer(Modifier.height(17.dp))
                            Text("我的  →  设置  →  课表模式", color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    pages.indices.forEach { index ->
                        Box(Modifier.padding(horizontal = 3.dp).size(if (index == page) 8.dp else 6.dp)
                            .background(if (index == page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("跳过") }
                    Button(onClick = {
                        if (page < pages.lastIndex) page++ else onOpenModePicker()
                    }, shape = RoundedCornerShape(14.dp)) {
                        Text(if (page == pages.lastIndex) "前往切换" else "下一步")
                    }
                }
            }
        }
    }
}
