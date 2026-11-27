package edu.csuft.sap.ui.profile

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.McpBindingDto
import edu.csuft.sap.data.remote.dto.McpCreateKeyRequest
import edu.csuft.sap.data.remote.dto.McpCreatedKeyDto
import edu.csuft.sap.data.remote.dto.McpKeyDto
import edu.csuft.sap.data.remote.dto.McpKeySettingsDto
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.common.SapCard
import kotlinx.coroutines.launch

private val scopeLabels = mapOf(
    "schedule:read" to "课表",
    "grades:read" to "成绩",
    "evaluations:read" to "评教状态",
    "exams:read" to "考试安排",
)

@Composable
internal fun McpKeysScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val owner by CurrentAccount.uid.collectAsState()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("MCP 密钥管理", onBack)
        val account = owner
        if (account == null) {
            Text("请先登录 SAP 账号。", modifier = Modifier.padding(20.dp))
        } else {
            key(account) { McpKeysContent(account) }
        }
    }
}

@Composable
private fun McpKeysContent(owner: String) {
    val coroutineScope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val repository = Graph.mcpKeyRepository
    var settings by remember { mutableStateOf<McpKeySettingsDto?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var confirmDestroy by remember { mutableStateOf<McpKeyDto?>(null) }
    var created by remember { mutableStateOf<McpCreatedKeyDto?>(null) }

    fun run(action: suspend () -> Unit) {
        if (working || CurrentAccount.uid.value != owner) return
        working = true
        error = null
        coroutineScope.launch {
            try {
                action()
            } finally {
                working = false
            }
        }
    }

    fun refresh() = run {
        val result = repository.settings()
        if (CurrentAccount.uid.value == owner) {
            when (result) {
                is Outcome.Success -> settings = result.data
                is Outcome.Error -> error = result.message
            }
        }
    }

    LaunchedEffect(lifecycle, owner) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh() }
    }

    val current = settings
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SapCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的密钥", style = MaterialTheme.typography.titleMedium)
                    Text(
                        current?.let { "已同步至 SAP · ${it.keys.size} / ${it.limit} 把" } ?: "从 SAP 获取密钥",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = ::refresh, enabled = !working) { Text("刷新") }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (!showCreate && confirmDestroy == null) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (current != null) {
            if (current.keys.isEmpty()) Text("还没有密钥。")
            current.keys.forEach { item ->
                SapCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(item.prefix + "…", fontFamily = FontFamily.Monospace)
                            val binding = current.bindings.firstOrNull { it.id == item.credentialId }
                            Text(
                                binding?.label ?: "教务绑定不可用",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                item.scopes.mapNotNull { scopeLabels[it] }.joinToString("、"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(item.createdAt.substringBefore('.').replace('T', ' '),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(
                            onClick = { error = null; confirmDestroy = item },
                            enabled = !working,
                        ) { Text("销毁", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            when {
                !current.canCreate -> Text("当前账号没有个人教务功能权限。")
                current.bindings.isEmpty() -> Text("请先到「我的」绑定教务账号，再刷新。")
                current.keys.size >= current.limit -> Text("已达到密钥上限。销毁一把后可继续创建。")
                else -> Button(
                    onClick = { error = null; showCreate = true },
                    enabled = !working,
                ) { Text("创建密钥") }
            }
        }
    }

    if (showCreate && current != null) {
        CreateMcpKeyDialog(current.bindings, working, error, onClose = { showCreate = false; error = null }) { request ->
            run {
                val result = repository.create(request)
                if (CurrentAccount.uid.value == owner) {
                    when (result) {
                        is Outcome.Success -> {
                            settings = settings?.let { it.copy(keys = listOf(result.data.key) + it.keys) }
                            showCreate = false
                            created = result.data
                        }
                        is Outcome.Error -> error = result.message
                    }
                }
            }
        }
    }

    confirmDestroy?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!working) { confirmDestroy = null; error = null } },
            title = { Text("销毁密钥") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("销毁「${item.name}」后，使用这把密钥的 Agent 会失去访问权限。")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(enabled = !working, onClick = { confirmDestroy = null; error = null }) { Text("取消") }
            },
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    run {
                        val result = repository.destroy(item.id)
                        if (CurrentAccount.uid.value == owner) {
                            if (result is Outcome.Error && result.code != 404) {
                                error = result.message
                            } else {
                                settings = settings?.let { it.copy(keys = it.keys.filterNot { key -> key.id == item.id }) }
                                confirmDestroy = null
                            }
                        }
                    }
                }) { Text("销毁", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    created?.let { key -> McpSecretDialog(key.secret, onClose = { created = null }) }
}

@Composable
private fun CreateMcpKeyDialog(
    bindings: List<McpBindingDto>,
    saving: Boolean,
    error: String?,
    onClose: () -> Unit,
    onCreate: (McpCreateKeyRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var bindingId by remember(bindings) { mutableStateOf(bindings.singleOrNull()?.id) }
    var scopes by remember { mutableStateOf(emptySet<String>()) }
    val id = bindingId
    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text("创建 MCP 密钥") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密钥名称") },
                    enabled = !saving,
                    singleLine = true,
                )
                Text("授权的教务账号", style = MaterialTheme.typography.labelLarge)
                bindings.forEach { binding ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(binding.id == id, onClick = { bindingId = binding.id }, enabled = !saving)
                        Text(binding.label)
                    }
                }
                Text("允许查询", style = MaterialTheme.typography.labelLarge)
                scopeLabels.forEach { (scope, label) ->
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = scope in scopes,
                            enabled = !saving,
                            role = Role.Checkbox,
                            onValueChange = { checked -> scopes = if (checked) scopes + scope else scopes - scope },
                        ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(checked = scope in scopes, onCheckedChange = null, enabled = !saving)
                        Text(label)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !saving) { Text("取消") } },
        confirmButton = {
            TextButton(
                onClick = { if (id != null) onCreate(McpCreateKeyRequest(name.trim(), id, scopes.toList())) },
                enabled = !saving && name.isNotBlank() && id != null && scopes.isNotEmpty(),
            ) { Text(if (saving) "创建中…" else "创建") }
        },
    )
}

@Composable
private fun McpSecretDialog(secret: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
        title = { Text("密钥已创建") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("完整密钥只在此弹窗显示一次。复制到 Agent 客户端的 Bearer 密钥设置后，再关闭弹窗。")
                SelectionContainer {
                    Text(secret, fontFamily = FontFamily.Monospace)
                }
                if (copied) Text("密钥已复制")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("已保存") } },
        confirmButton = {
            TextButton(onClick = {
                val clip = ClipData.newPlainText("SAP MCP", secret)
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
                context.getSystemService(ClipboardManager::class.java)?.let {
                    it.setPrimaryClip(clip)
                    copied = true
                }
            }) { Text("复制密钥") }
        },
    )
}
