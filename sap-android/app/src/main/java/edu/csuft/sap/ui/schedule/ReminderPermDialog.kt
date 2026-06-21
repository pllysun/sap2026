package edu.csuft.sap.ui.schedule

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.notify.ReminderPermissions

/**
 * 「确保准时收到提醒」权限引导弹框：逐项显示状态 + 一键跳转开通，
 * 覆盖杀后台/锁屏后仍能弹通知所需的权限（通知 / 精确闹钟 / 电池优化 / 自启动）。
 */
@Composable
fun ReminderPermDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val openSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val notifOk = remember(tick) { ReminderPermissions.notificationsGranted(context) }
    val exactOk = remember(tick) { ReminderPermissions.exactAlarmGranted(context) }
    val batteryOk = remember(tick) { ReminderPermissions.batteryUnrestricted(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确保准时收到提醒") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "为保证杀后台、锁屏后也能准时弹出提醒，请逐项开启（建议全开）：",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PermItem("通知权限", "允许后通知才会显示", notifOk) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else openSettings.launch(ReminderPermissions.appDetailsIntent(context))
                }
                PermItem("精确闹钟", "保证课前准点、不延迟", exactOk) {
                    ReminderPermissions.exactAlarmIntent(context)?.let { openSettings.launch(it) }
                }
                PermItem("忽略电池优化", "防止系统省电把后台闹钟延迟或拦截（杀后台关键）", batteryOk) {
                    openSettings.launch(ReminderPermissions.ignoreBatteryIntent(context))
                }
                PermItem(
                    "自启动 / 后台启动", "小米 / 华为 / OPPO / vivo 等需手动允许本应用自启动，否则清后台后无法被唤醒", null,
                ) { ReminderPermissions.openAutoStart(context); tick++ }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

/** 单项权限行：[ok]=true 显示「已开启」；=false 显示「去开启」；=null（无法检测，如自启动）显示「去设置」。 */
@Composable
private fun PermItem(title: String, desc: String, ok: Boolean?, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        when (ok) {
            true -> Text("已开启", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            else -> TextButton(onClick = onFix) { Text(if (ok == null) "去设置" else "去开启") }
        }
    }
}
