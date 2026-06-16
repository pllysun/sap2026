package edu.csuft.sap.ui.eval

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.remote.dto.EvalResultDto
import edu.csuft.sap.data.remote.dto.EvalTaskDto
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.ui.common.EmptyHint
import edu.csuft.sap.ui.common.ErrorRetry
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.SyncBar
import edu.csuft.sap.ui.common.TermSelector

/** 评教内容（作为「成绩」页第三个二级 tab 内嵌）。 */
@Composable
fun EvalContent(modifier: Modifier = Modifier, vm: EvalViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncBar(state.syncedAt, state.syncing, state.error, vm::sync)
        TermSelector(
            terms = state.terms.map { TermDto(value = it, label = it) },
            selected = state.term,
            onSelect = vm::selectTerm,
        )
        Box(Modifier.weight(1f)) {
            when {
                state.noAccount -> EmptyHint("请先在「我的」里绑定教务账号")
                state.syncing && state.pending.isEmpty() && state.done.isEmpty() -> LoadingBox()
                state.error != null && state.pending.isEmpty() && state.done.isEmpty() ->
                    ErrorRetry(state.error!!, vm::sync)
                state.pending.isEmpty() && state.done.isEmpty() -> EmptyHint("本学期暂无评教任务")
                else -> EvalList(state.pending, state.done, state.submitting) { confirm = true }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("一键评教") },
            text = {
                Text(
                    "将对 ${state.pending.size} 项未评教自动提交：每项指标给满分（仅留 1 项次高分以满足系统限制），" +
                        "并附固定好评。\n\n提交后无法修改，确定继续？",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.autoEvaluate() }) {
                    Text("确认提交", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }

    state.results?.let { ResultsDialog(it, vm::dismissResults) }
}

@Composable
private fun EvalList(
    pending: List<EvalTaskDto>,
    done: List<EvalTaskDto>,
    submitting: Boolean,
    onAuto: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pending.isNotEmpty()) {
            item {
                SapCard {
                    Text("待评教 ${pending.size} 门", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "一键自动提交：每位老师满分（留 1 项次高）+ 固定好评，提交后不可修改",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Button(
                        onClick = onAuto,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Text("提交中…", modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text("一键评教")
                        }
                    }
                }
            }
            item { SectionLabel("待评教") }
            items(pending) { EvalRow(it, pendingRow = true) }
        }
        if (done.isNotEmpty()) {
            item { SectionLabel("已评教") }
            items(done) { EvalRow(it, pendingRow = false) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun EvalRow(t: EvalTaskDto, pendingRow: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.teacher ?: "未知教师", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(t.typeName, t.college).joinToString("  ·  "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (pendingRow) {
            Text("待评", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        } else {
            Text(t.score ?: "已评", fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ResultsDialog(results: List<EvalResultDto>, onDismiss: () -> Unit) {
    val ok = results.count { it.success }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评教结果（成功 $ok/${results.size}）") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                results.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (r.success) "✓" else "✗", color = if (r.success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(r.teacher ?: "", fontSize = 14.sp)
                            Text(
                                if (r.success) "评教成功${r.score?.let { "  ·  $it 分" } ?: ""}" else (r.message ?: "失败"),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
