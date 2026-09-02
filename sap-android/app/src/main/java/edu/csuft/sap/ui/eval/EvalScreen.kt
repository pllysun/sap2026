package edu.csuft.sap.ui.eval

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.remote.dto.EvalAnswerRequest
import edu.csuft.sap.data.remote.dto.EvalFormDto
import edu.csuft.sap.data.remote.dto.EvalQuestionDto
import edu.csuft.sap.data.remote.dto.EvalResultDto
import edu.csuft.sap.data.remote.dto.EvalTaskDto
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.ui.common.EmptyHint
import edu.csuft.sap.ui.common.ErrorRetry
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.SyncBar
import edu.csuft.sap.ui.common.TermSelector
import java.util.Locale
import kotlin.math.abs

/**
 * 新教学质量保障系统评教。
 *
 * 页面只展示本地缓存；用户点击「同步」后才会访问学校平台并替换缓存。
 */
@Composable
fun EvalContent(modifier: Modifier = Modifier, vm: EvalViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var confirmAuto by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncBar(state.syncedAt, state.syncing, state.error, vm::sync)
        TermSelector(
            terms = state.rounds.mapNotNull { round ->
                round.id?.let { id ->
                    TermDto(
                        value = id.toString(),
                        label = round.name?.takeIf { it.isNotBlank() } ?: "评教任务 $id",
                        current = round.status == "进行中",
                    )
                }
            },
            selected = state.selectedRoundId?.toString(),
            onSelect = { selected -> selected.toLongOrNull()?.let(vm::selectRound) },
            sheetTitle = "选择评教年份",
        )
        Box(Modifier.weight(1f)) {
            when {
                state.noAccount -> EmptyHint("请先在「我的」里绑定教务账号")
                state.syncing && state.pending.isEmpty() && state.done.isEmpty() -> LoadingBox()
                state.error != null && state.pending.isEmpty() && state.done.isEmpty() ->
                    ErrorRetry(state.error!!, vm::sync)
                state.pending.isEmpty() && state.done.isEmpty() && state.syncedAt == null ->
                    EmptyHint("暂无内容，请点击同步获取评教信息")
                state.pending.isEmpty() && state.done.isEmpty() -> EmptyHint("暂无评教")
                else -> EvalList(
                    pending = state.pending,
                    done = state.done,
                    submitting = state.submitting,
                    restrictHighest = state.restrictHighest,
                    onAuto = { confirmAuto = true },
                    onManual = vm::openForm,
                )
            }
        }
    }

    if (confirmAuto) {
        val count = state.pending.count { it.status == 0 }
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("一键满评") },
            text = {
                Text(
                    if (state.restrictHighest) {
                        "将对 $count 门待评课程提交平台允许的最高分和默认好评。该任务禁止所有题均为最高分，" +
                            "当前量表通常会得到 99.99 分。\n\n提交后通常无法修改，确定继续？"
                    } else {
                        "将对 $count 门待评课程提交满分和默认好评。\n\n提交后通常无法修改，确定继续？"
                    },
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmAuto = false; vm.autoEvaluate() }) {
                    Text("确认提交", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text("取消") } },
        )
    }

    if (state.formLoading) LoadingFormDialog()
    state.form?.let { form ->
        ManualEvalDialog(
            form = form,
            submitting = state.manualSubmitting,
            serverError = state.manualError,
            onDismiss = vm::closeForm,
            onSubmit = vm::submitManual,
        )
    }
    state.results?.let { ResultsDialog(it, vm::dismissResults) }
}

@Composable
private fun EvalList(
    pending: List<EvalTaskDto>,
    done: List<EvalTaskDto>,
    submitting: Boolean,
    restrictHighest: Boolean,
    onAuto: () -> Unit,
    onManual: (EvalTaskDto) -> Unit,
) {
    val actionable = pending.count { it.status == 0 }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (actionable > 0) {
            item {
                SapCard {
                    Text("待评价 $actionable 门", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (restrictHighest) {
                            "一键提交平台最高合法分（当前量表通常为 99.99）和默认好评；也可逐门手动填写。"
                        } else {
                            "一键提交满分和默认好评；也可逐门手动填写。"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Button(onClick = onAuto, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                        if (submitting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("提交中…", modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text("一键满评全部待评课程")
                        }
                    }
                }
            }
        }
        if (pending.isNotEmpty()) {
            item { SectionLabel("待评价") }
            items(pending, key = { it.courseId ?: "${it.courseCode}-${it.teacherNo}" }) {
                EvalRow(it, pendingRow = true, onManual = onManual)
            }
        }
        if (done.isNotEmpty()) {
            item { SectionLabel("已评价") }
            items(done, key = { it.courseId ?: "${it.courseCode}-${it.teacherNo}" }) {
                EvalRow(it, pendingRow = false, onManual = onManual)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

@Composable
private fun EvalRow(t: EvalTaskDto, pendingRow: Boolean, onManual: (EvalTaskDto) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.courseName ?: "未命名课程", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(t.teacher, t.typeName, t.college).joinToString("  ·  "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (pendingRow && t.status == 0) {
            TextButton(onClick = { onManual(t) }) { Text("手动填写") }
        } else if (pendingRow) {
            Text(t.statusText ?: "评价中", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                t.score ?: t.statusText ?: "已评价",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LoadingFormDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在加载评价表") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("正在读取平台原始量表…", modifier = Modifier.padding(start = 12.dp))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ManualEvalDialog(
    form: EvalFormDto,
    submitting: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (List<EvalAnswerRequest>) -> Unit,
) {
    val values = remember(form.courseId) {
        mutableStateMapOf<Long, String>().apply {
            form.questions.forEach { q ->
                if (q.type == "问答题" && q.indexId != null) put(q.indexId, form.defaultComment.orEmpty())
            }
        }
    }
    val multiples = remember(form.courseId) { mutableStateMapOf<Long, Set<Long>>() }
    var validationError by remember(form.courseId) { mutableStateOf<String?>(null) }
    var pendingAnswers by remember(form.courseId) { mutableStateOf<List<EvalAnswerRequest>?>(null) }
    val total = calculateTotal(form, values)
    val allHighest = form.restrictHighest && allScoredAtHighest(form, values)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Column {
                Text(form.courseName ?: "课程评价")
                Text(
                    listOfNotNull(form.teacher, form.typeName).joinToString(" · "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前总分", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatScore(total)} / ${formatScore(form.maxTotal)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (allHighest) {
                    Text(
                        "当前所有评分题都是最高分，源站不允许这样提交，请至少调整一项。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                (validationError ?: serverError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                form.questions.forEach { question ->
                    QuestionEditor(
                        question = question,
                        value = question.indexId?.let { values[it] }.orEmpty(),
                        selected = question.indexId?.let { multiples[it] }.orEmpty(),
                        onValueChanged = { newValue -> question.indexId?.let { values[it] = newValue } },
                        onMultipleChanged = { newValue -> question.indexId?.let { multiples[it] = newValue } },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val built = buildAnswers(form, values, multiples)
                    validationError = built.error
                    if (built.error == null) pendingAnswers = built.answers
                },
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("提交中…", modifier = Modifier.padding(start = 6.dp))
                } else Text(if (serverError == null) "提交评价" else "重新提交")
            }
        },
        dismissButton = { TextButton(enabled = !submitting, onClick = onDismiss) { Text("取消") } },
    )

    pendingAnswers?.let { answers ->
        AlertDialog(
            onDismissRequest = { pendingAnswers = null },
            title = { Text("确认提交评价") },
            text = { Text("本次总分 ${formatScore(total)}，提交后通常无法修改，确定继续？") },
            confirmButton = {
                TextButton(onClick = { pendingAnswers = null; onSubmit(answers) }) { Text("确认提交") }
            },
            dismissButton = { TextButton(onClick = { pendingAnswers = null }) { Text("返回检查") } },
        )
    }
}

@Composable
private fun QuestionEditor(
    question: EvalQuestionDto,
    value: String,
    selected: Set<Long>,
    onValueChanged: (String) -> Unit,
    onMultipleChanged: (Set<Long>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        question.section?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "${question.order}. ${question.title.orEmpty()}${if (question.required) " *" else ""}",
            fontSize = 14.sp,
            color = if (question.required) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 3.dp),
        )
        question.remark?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (question.type) {
            "打分题" -> OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = { Text("0～${formatScore(question.maxScore)} 分") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            )
            "单选题" -> question.options.forEach { option ->
                val optionId = option.id ?: return@forEach
                Row(
                    Modifier.fillMaxWidth().clickable { onValueChanged(optionId.toString()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == optionId.toString(), onClick = { onValueChanged(optionId.toString()) })
                    Text(option.title.orEmpty() + if (question.scored) "（${formatScore(option.score)} 分）" else "")
                }
            }
            "多选题" -> question.options.forEach { option ->
                val optionId = option.id ?: return@forEach
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onMultipleChanged(if (optionId in selected) selected - optionId else selected + optionId)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = optionId in selected,
                        onCheckedChange = {
                            onMultipleChanged(if (optionId in selected) selected - optionId else selected + optionId)
                        },
                    )
                    Text(option.title.orEmpty())
                }
            }
            "填空题" -> OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = { Text("多个空请用 | 分隔") },
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            )
            else -> OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = { Text("自定义评价") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

private data class BuiltAnswers(val answers: List<EvalAnswerRequest>, val error: String? = null)
private data class ScorePoint(val value: Double, val minimum: Double, val maximum: Double)

private fun buildAnswers(
    form: EvalFormDto,
    values: Map<Long, String>,
    multiples: Map<Long, Set<Long>>,
): BuiltAnswers {
    val answers = mutableListOf<EvalAnswerRequest>()
    val scored = mutableListOf<ScorePoint>()
    form.questions.forEach { question ->
        val id = question.indexId ?: return BuiltAnswers(emptyList(), "评价表包含无效题目")
        val raw = values[id].orEmpty().trim()
        val label = "第${question.order}题"
        when (question.type) {
            "打分题" -> {
                if (question.required && raw.isBlank()) return BuiltAnswers(emptyList(), "$label 为必填项")
                val number = raw.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                if (raw.isNotBlank() && number == null) return BuiltAnswers(emptyList(), "$label 分数格式不正确")
                if (number != null && (number < 0 || number > question.maxScore)) {
                    return BuiltAnswers(emptyList(), "$label 应在 0～${formatScore(question.maxScore)} 分之间")
                }
                if (number != null && question.scoringType != 0 && number % 1.0 != 0.0) {
                    return BuiltAnswers(emptyList(), "$label 只能填写整数")
                }
                answers += EvalAnswerRequest(indexId = id, score = number)
                if (question.scored) scored += ScorePoint(
                    number ?: 0.0,
                    if (question.scoringType == 1) 1.0 else 0.0,
                    question.maxScore,
                )
            }
            "单选题" -> {
                val optionId = raw.toLongOrNull()
                if (question.required && optionId == null) return BuiltAnswers(emptyList(), "请选择 $label")
                val option = question.options.firstOrNull { it.id == optionId }
                if (optionId != null && option == null) return BuiltAnswers(emptyList(), "$label 选项无效")
                answers += EvalAnswerRequest(indexId = id, optionId = optionId)
                if (question.scored) scored += ScorePoint(
                    option?.score ?: 0.0,
                    question.options.minOfOrNull { it.score } ?: 0.0,
                    question.options.maxOfOrNull { it.score } ?: 0.0,
                )
            }
            "多选题" -> {
                val selected = multiples[id].orEmpty().toList()
                if (question.required && selected.isEmpty()) return BuiltAnswers(emptyList(), "请选择 $label")
                answers += EvalAnswerRequest(indexId = id, optionIds = selected)
            }
            "填空题" -> {
                val parts = raw.split('|').map { it.trim() }
                if (question.required && (raw.isBlank() || parts.any { it.isBlank() })) {
                    return BuiltAnswers(emptyList(), "请完整填写 $label")
                }
                answers += EvalAnswerRequest(indexId = id, values = parts)
            }
            else -> {
                if (question.required && raw.isBlank()) return BuiltAnswers(emptyList(), "$label 为必填项")
                answers += EvalAnswerRequest(indexId = id, text = raw)
            }
        }
    }
    if (scored.isNotEmpty() && form.restrictHighest && scored.all { same(it.value, it.maximum) }) {
        return BuiltAnswers(emptyList(), "平台不允许所有评分题均为最高分，请至少调整一项")
    }
    if (scored.isNotEmpty() && form.restrictLowest && scored.all { same(it.value, it.minimum) }) {
        return BuiltAnswers(emptyList(), "平台不允许所有评分题均为最低分，请至少调整一项")
    }
    return BuiltAnswers(answers)
}

private fun calculateTotal(
    form: EvalFormDto,
    values: Map<Long, String>,
): Double = form.questions.sumOf { question ->
    if (!question.scored || question.indexId == null) 0.0
    else when (question.type) {
        "打分题" -> values[question.indexId].orEmpty().toDoubleOrNull() ?: 0.0
        "单选题" -> {
            val id = values[question.indexId].orEmpty().toLongOrNull()
            question.options.firstOrNull { it.id == id }?.score ?: 0.0
        }
        else -> 0.0
    }
}

private fun allScoredAtHighest(
    form: EvalFormDto,
    values: Map<Long, String>,
): Boolean {
    val scored = form.questions.filter { it.scored && (it.type == "打分题" || it.type == "单选题") }
    if (scored.isEmpty()) return false
    return scored.all { question ->
        val id = question.indexId ?: return@all false
        when (question.type) {
            "打分题" -> values[id].orEmpty().toDoubleOrNull()?.let { same(it, question.maxScore) } == true
            "单选题" -> {
                val optionId = values[id].orEmpty().toLongOrNull()
                val selected = question.options.firstOrNull { it.id == optionId } ?: return@all false
                val maximum = question.options.maxOfOrNull { it.score } ?: return@all false
                same(selected.score, maximum)
            }
            else -> false
        }
    }
}

private fun same(a: Double, b: Double): Boolean = abs(a - b) < 0.000001

private fun formatScore(value: Double): String = String.format(Locale.US, "%.2f", value)
    .trimEnd('0').trimEnd('.')

@Composable
private fun ResultsDialog(results: List<EvalResultDto>, onDismiss: () -> Unit) {
    val ok = results.count { it.success }
    val processing = results.count { it.pending }
    val skipped = results.count { it.skipped }
    val summary = buildList {
        add("成功 $ok")
        if (processing > 0) add("处理中 $processing")
        if (skipped > 0) add("未尝试 $skipped")
        add("共 ${results.size}")
    }.joinToString(" · ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评价结果（$summary）") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                results.forEach { result ->
                    val marker = when {
                        result.success -> "✓"
                        result.pending -> "…"
                        result.skipped -> "—"
                        else -> "✗"
                    }
                    val markerColor = when {
                        result.success -> MaterialTheme.colorScheme.primary
                        result.pending -> MaterialTheme.colorScheme.tertiary
                        result.skipped -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            marker,
                            color = markerColor,
                            fontWeight = FontWeight.Medium,
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(result.courseName ?: result.teacher.orEmpty(), fontSize = 14.sp)
                            result.teacher?.takeIf { result.courseName != null }?.let {
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when {
                                    result.success -> "评价成功${result.score?.let { "  ·  $it 分" } ?: ""}"
                                    result.pending -> result.message ?: "源站正在处理，请稍后同步确认"
                                    else -> result.message ?: "失败"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
