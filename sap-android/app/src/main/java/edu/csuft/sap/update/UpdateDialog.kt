package edu.csuft.sap.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.remote.dto.AppVersionDto

/**
 * 升级弹窗（无状态）：按 [state] 渲染，Idle 时不渲染。
 * 强制更新时不可点外部关闭、不提供“以后再说”。
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Idle -> Unit

        is UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp)
                    Text("正在检查最新版本…", fontSize = 14.sp)
                }
            },
            confirmButton = {},
        )

        is UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已是最新版本") },
            text = { Text("当前已是最新版本，无需更新。", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好的") } },
        )

        is UpdateUiState.CheckFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新失败") },
            text = { Text(state.message, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好的") } },
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { if (!state.forced) onDismiss() },
            title = { Text("发现新版本 ${state.info.versionName ?: ""}") },
            text = { ChangelogBody(state.info) },
            confirmButton = { TextButton(onClick = onDownload) { Text("立即更新") } },
            dismissButton = if (state.forced) null else {
                { TextButton(onClick = onDismiss) { Text("以后再说") } }
            },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column {
                    Text("${(state.progress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateUiState.Downloaded -> AlertDialog(
            onDismissRequest = { if (!state.forced) onDismiss() },
            title = { Text("下载完成") },
            text = { Text("新版本已下载，点击安装。若提示“未知来源”，请授权后再点一次安装。", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = onInstall) { Text("安装") } },
            dismissButton = if (state.forced) null else {
                { TextButton(onClick = onDismiss) { Text("稍后") } }
            },
        )

        is UpdateUiState.DownloadFailed -> AlertDialog(
            onDismissRequest = { if (!state.forced) onDismiss() },
            title = { Text("下载失败") },
            text = { Text(state.message, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = onDownload) { Text("重试") } },
            dismissButton = if (state.forced) null else {
                { TextButton(onClick = onDismiss) { Text("取消") } }
            },
        )
    }
}

@Composable
private fun ChangelogBody(info: AppVersionDto) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        info.size.takeIf { it > 0 }?.let {
            Text(
                "更新包 ${"%.1f".format(it / 1024.0 / 1024.0)}MB",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            changelogItems(info.changelog).forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        "•",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        item,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private val CHANGELOG_ITEM_PREFIX = Regex("^(?:[-*•·●▪◦]+|\\d+[.)、])\\s*")

/**
 * 服务端约定每行是一条更新内容，项目符号属于客户端展示样式。
 * 同时容忍管理端手工输入的项目符号，避免出现重复符号。
 */
internal fun changelogItems(changelog: String?): List<String> {
    val items = changelog.orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.replaceFirst(CHANGELOG_ITEM_PREFIX, "").trim() }
        .filter { it.isNotEmpty() }
        .toList()
    return items.ifEmpty { listOf("优化与修复。") }
}
