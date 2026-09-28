package edu.csuft.sap.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import edu.csuft.sap.data.account.*
import edu.csuft.sap.ui.profile.PrivacyScreen

/** 已登录账号后续获得完整能力时，进入教务模式前补齐协议。 */
@Composable
fun AcademicPrivacyGate() {
    val currentOwner by CurrentAccount.uid.collectAsState()
    val owner = currentOwner?.takeIf(String::isNotBlank) ?: "_"
    if (!ConnectivityState.online || !MemberState.accessResolved ||
        !MemberState.hasFullAppFeatures ||
        (MemberState.mode != AppMode.JW && MemberState.pendingAcademicOwner != owner) ||
        owner == "_" || PrivacyConsents.hasAcademic(owner)) return
    val context = LocalContext.current
    var error by remember(owner) { mutableStateOf<String?>(null) }
    val dismiss = { MemberState.cancelAcademicRequest(context, owner) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 20.dp).widthIn(max = 460.dp).fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp)) {
            Column {
                PrivacyScreen(Modifier.weight(1f), onBack = dismiss, member = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = dismiss) { Text("暂不使用") }
                    Button(onClick = {
                        runCatching {
                            if (CurrentAccount.key == owner && MemberState.hasFullAppFeatures) {
                                PrivacyConsents.accept(owner, academic = true)
                                MemberState.setMode(context, AppMode.JW)
                            }
                        }
                            .onFailure { error = "无法保存协议确认，请重试" }
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("同意并使用") }
                }
            }
        }
    }
}
