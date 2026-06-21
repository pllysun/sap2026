package edu.csuft.sap.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.account.JwMfaState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.launch

/**
 * 全局教务短信二次验证(MFA)弹框：任何拉教务数据触发 MFA（后端 428）时由顶层弹出。
 * 用户输短信码 → 调 /api/jw/bind/mfa 续登缓存会话 → [JwMfaState.passed] 触发各数据页自动重试。
 */
@Composable
fun JwMfaGate() {
    val challenge by JwMfaState.challenge.collectAsState()
    val ch = challenge ?: return
    val scope = rememberCoroutineScope()
    var code by remember(ch.challengeId) { mutableStateOf("") }
    var loading by remember(ch.challengeId) { mutableStateOf(false) }
    var error by remember(ch.challengeId) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) JwMfaState.dismiss() },
        title = { Text("短信二次验证") },
        text = {
            Column {
                Text(
                    if (ch.phone.isBlank()) "教务登录需短信验证，验证码已发送至你的安全手机，请输入："
                    else "教务登录需短信验证，验证码已发送至 ${ch.phone}，请输入：",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter { c -> c.isDigit() } },
                    label = { Text("短信验证码") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                TextButton(
                    onClick = { scope.launch { error = (Graph.jwRepository.bindMfaResend(ch.challengeId) as? Outcome.Error)?.message ?: "验证码已重新发送" } },
                    enabled = !loading,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text("没收到？重新发送", fontSize = 12.sp) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && code.isNotBlank(),
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        when (val r = Graph.jwRepository.bindMfa(ch.challengeId, code.trim())) {
                            is Outcome.Success -> JwMfaState.passed()   // 清弹框 + 触发各页自动重试
                            is Outcome.Error -> { error = r.message; loading = false }
                        }
                    }
                },
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = { if (!loading) JwMfaState.dismiss() }) { Text("取消") } },
    )
}
