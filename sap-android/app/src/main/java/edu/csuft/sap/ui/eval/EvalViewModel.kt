package edu.csuft.sap.ui.eval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.EvalAnswerRequest
import edu.csuft.sap.data.remote.dto.EvalFormDto
import edu.csuft.sap.data.remote.dto.EvalOverviewDto
import edu.csuft.sap.data.remote.dto.EvalResultDto
import edu.csuft.sap.data.remote.dto.EvalRoundDto
import edu.csuft.sap.data.remote.dto.EvalTaskDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 学生评教：进页面只读本地缓存，不打教务；点「同步」才拉评教列表（同成绩/考试，防风控）。
 * 支持读取新平台量表后逐题手动填写，也支持批量提交平台允许的最高评分。
 */
class EvalViewModel : ViewModel() {

    data class UiState(
        val syncing: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        val noAccount: Boolean = false,
        val rounds: List<EvalRoundDto> = emptyList(),
        val selectedRoundId: Long? = null,
        val taskId: Long? = null,
        val restrictHighest: Boolean = false,
        val restrictLowest: Boolean = false,
        val pending: List<EvalTaskDto> = emptyList(),
        val done: List<EvalTaskDto> = emptyList(),
        val syncedAt: Long? = null,
        val results: List<EvalResultDto>? = null, // 一键评教结果（结果弹窗）
        val formLoading: Boolean = false,
        val manualSubmitting: Boolean = false,
        val form: EvalFormDto? = null,
        val manualError: String? = null,
    )

    private val acc = Graph.accountManager
    private val cache = Graph.jwCacheStore
    private val jw = Graph.jwRepository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // active 学号相同但会员登录账号切换时，也必须重新取该会员账号自己的缓存。
        viewModelScope.launch {
            combine(acc.active, acc.contextVersion) { _, _ -> Unit }.collect { loadCache() }
        }
    }

    /** 仅读取当前会员账号 + 教务账号下的评教缓存，绝不因进入页面请求学校平台。 */
    private fun loadCache() {
        val account = acc.activeAccount
        if (account == null) {
            _state.value = UiState(noAccount = true, error = "请先在「我的」里绑定教务账号")
            return
        }
        val catalog = cache.evalCatalog(account)
        val selectedRoundId = catalog?.selectedRoundId
            ?.takeIf { selected -> catalog.rounds.any { it.id == selected } }
            ?: catalog?.rounds?.firstOrNull()?.id
        val c = cache.eval(account, selectedRoundId)
        if (c?.overview != null) {
            applyOverview(c.overview, c.syncedAt.takeIf { it > 0 }, selectedRoundId, catalog?.rounds.orEmpty())
        } else {
            _state.value = UiState(rounds = catalog?.rounds.orEmpty(), selectedRoundId = selectedRoundId)
        }
    }

    /** 切换评教学年/轮次时只读对应缓存，用户点击同步后才会拉取该轮次。 */
    fun selectRound(roundId: Long) {
        if (roundId == _state.value.selectedRoundId) return
        val account = acc.activeAccount ?: return
        cache.selectEvalRound(account, roundId)
        val c = cache.eval(account, roundId)
        if (c?.overview != null) {
            applyOverview(c.overview, c.syncedAt.takeIf { it > 0 }, roundId, _state.value.rounds)
        } else {
            _state.value = UiState(rounds = _state.value.rounds, selectedRoundId = roundId)
        }
    }

    /** 手动同步评教列表。 */
    fun sync() {
        val account = acc.activeAccount ?: return
        val requestedRoundId = _state.value.selectedRoundId
        if (_state.value.syncing) return
        _state.value = _state.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            when (val r = jw.evalList(account, requestedRoundId?.toString())) {
                is Outcome.Success -> {
                    // 成功但没有任务同样写入缓存，下一次进入仍显示「暂无评教」。
                    val selectedRoundId = r.data.taskId ?: requestedRoundId
                    val at = cache.saveEval(account, selectedRoundId, r.data)
                    applyOverview(r.data, at, selectedRoundId)
                    _state.value = _state.value.copy(syncing = false)
                }
                is Outcome.Error -> _state.value = _state.value.copy(syncing = false, error = r.message)
            }
        }
    }

    /** 打开一门课程的新平台原始评价量表。 */
    fun openForm(task: EvalTaskDto) {
        val account = acc.activeAccount ?: return
        val taskId = task.taskId ?: _state.value.taskId ?: return
        val courseId = task.courseId ?: return
        if (_state.value.formLoading || _state.value.manualSubmitting) return
        _state.value = _state.value.copy(formLoading = true, manualError = null, error = null)
        viewModelScope.launch {
            when (val r = jw.evalForm(account, taskId, courseId)) {
                is Outcome.Success -> _state.value = _state.value.copy(formLoading = false, form = r.data)
                is Outcome.Error -> _state.value = _state.value.copy(formLoading = false, error = r.message)
            }
        }
    }

    fun closeForm() {
        if (_state.value.manualSubmitting) return
        _state.value = _state.value.copy(form = null, manualError = null)
    }

    /** 提交单门课程的自定义答案。 */
    fun submitManual(answers: List<EvalAnswerRequest>) {
        val account = acc.activeAccount ?: return
        val form = _state.value.form ?: return
        val taskId = form.taskId ?: return
        val courseId = form.courseId ?: return
        if (_state.value.manualSubmitting) return
        _state.value = _state.value.copy(manualSubmitting = true, manualError = null)
        viewModelScope.launch {
            when (val r = jw.evalSubmit(account, taskId, courseId, answers)) {
                is Outcome.Success -> {
                    if (r.data.success || r.data.pending) {
                        // 已成功或源站已进入“评价中”时都关闭表单，避免用户重复提交同一课程。
                        _state.value = _state.value.copy(
                            manualSubmitting = false,
                            form = null,
                            manualError = null,
                            results = listOf(r.data),
                        )
                        sync()
                    } else {
                        // 源站明确未接收/无法确认时保留 form，Compose 中逐题填写的草稿也会原样保留。
                        val suffix = if (r.data.retryable) "\n当前填写内容已保留。" else ""
                        _state.value = _state.value.copy(
                            manualSubmitting = false,
                            manualError = friendlyEvalSubmitError(r.data.message ?: "评价提交失败") + suffix,
                        )
                    }
                }
                is Outcome.Error -> _state.value = _state.value.copy(
                    manualSubmitting = false,
                    manualError = friendlyEvalSubmitError(r.message) + "\n当前填写内容已保留。",
                )
            }
        }
    }

    /** 一键最高合法分（提交后不可撤销），完成后刷新列表并弹结果。 */
    fun autoEvaluate() {
        val account = acc.activeAccount ?: return
        if (_state.value.submitting || _state.value.pending.none { it.status == 0 }) return
        _state.value = _state.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val r = jw.evalAuto(account, _state.value.taskId, null)) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(submitting = false, results = r.data)
                    sync() // 刷新：已评列表更新
                }
                is Outcome.Error -> _state.value = _state.value.copy(
                    submitting = false,
                    error = friendlyEvalSubmitError(r.message),
                )
            }
        }
    }

    fun dismissResults() {
        _state.value = _state.value.copy(results = null)
    }

    private fun applyOverview(
        o: EvalOverviewDto,
        at: Long?,
        selectedRoundId: Long? = o.taskId ?: _state.value.selectedRoundId,
        fallbackRounds: List<EvalRoundDto> = _state.value.rounds,
    ) {
        _state.value = _state.value.copy(
            rounds = o.rounds.ifEmpty { fallbackRounds },
            selectedRoundId = selectedRoundId,
            taskId = o.taskId,
            restrictHighest = o.restrictHighest,
            restrictLowest = o.restrictLowest,
            pending = o.tasks.filter { !it.evaluated },
            done = o.tasks.filter { it.evaluated },
            syncedAt = at,
            noAccount = false,
            error = null,
        )
    }
}

private fun friendlyEvalSubmitError(message: String): String {
    val lower = message.lowercase()
    return when {
        "mq" in lower || "rabbit" in lower || "kafka" in lower || "消息队列" in message ->
            "源站消息队列暂时异常，本次评价尚未确认提交；请稍后重试"
        else -> message
    }
}
