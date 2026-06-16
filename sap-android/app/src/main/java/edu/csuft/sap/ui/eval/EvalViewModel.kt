package edu.csuft.sap.ui.eval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.EvalOverviewDto
import edu.csuft.sap.data.remote.dto.EvalResultDto
import edu.csuft.sap.data.remote.dto.EvalTaskDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 学生评教：进页面只读本地缓存，不打教务；点「同步」才拉评教列表（同成绩/考试，防风控）。
 * 「一键评教」对未评任务自动满分(留1项)+固定评语提交，提交后刷新列表。
 */
class EvalViewModel : ViewModel() {

    data class UiState(
        val syncing: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        val noAccount: Boolean = false,
        val term: String? = null,
        val terms: List<String> = emptyList(),
        val pending: List<EvalTaskDto> = emptyList(),
        val done: List<EvalTaskDto> = emptyList(),
        val syncedAt: Long? = null,
        val results: List<EvalResultDto>? = null, // 一键评教结果（结果弹窗）
    )

    private val acc = Graph.accountManager
    private val cache = Graph.jwCacheStore
    private val jw = Graph.jwRepository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { acc.active.collect { loadCache(null) } }
    }

    private fun loadCache(term: String?) {
        val account = acc.activeAccount
        if (account == null) {
            _state.value = UiState(noAccount = true, error = "请先在「我的」里绑定教务账号")
            return
        }
        val c = cache.eval(account, term)
        if (c?.overview != null) {
            applyOverview(c.overview, c.syncedAt.takeIf { it > 0 })
        } else {
            _state.value = _state.value.copy(term = term, pending = emptyList(), done = emptyList(), syncedAt = null)
            sync(term)
        }
    }

    fun selectTerm(term: String) {
        if (term == _state.value.term) return
        val account = acc.activeAccount ?: return
        val c = cache.eval(account, term)
        if (c?.overview != null) applyOverview(c.overview, c.syncedAt.takeIf { it > 0 })
        else { _state.value = _state.value.copy(term = term); sync(term) }
    }

    /** 手动同步评教列表。 */
    fun sync(term: String? = _state.value.term) {
        val account = acc.activeAccount ?: return
        if (_state.value.syncing) return
        _state.value = _state.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            when (val r = jw.evalList(account, term)) {
                is Outcome.Success -> {
                    val at = cache.saveEval(account, term, r.data)
                    applyOverview(r.data, at)
                    _state.value = _state.value.copy(syncing = false)
                }
                is Outcome.Error -> _state.value = _state.value.copy(syncing = false, error = r.message)
            }
        }
    }

    /** 一键自动评教（提交后不可撤销），完成后刷新列表并弹结果。 */
    fun autoEvaluate() {
        val account = acc.activeAccount ?: return
        if (_state.value.submitting || _state.value.pending.isEmpty()) return
        val term = _state.value.term
        _state.value = _state.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val r = jw.evalAuto(account, term, null)) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(submitting = false, results = r.data)
                    sync(term) // 刷新：已评列表更新
                }
                is Outcome.Error -> _state.value = _state.value.copy(submitting = false, error = r.message)
            }
        }
    }

    fun dismissResults() {
        _state.value = _state.value.copy(results = null)
    }

    private fun applyOverview(o: EvalOverviewDto, at: Long?) {
        _state.value = _state.value.copy(
            term = o.term ?: _state.value.term,
            terms = o.terms,
            pending = o.tasks.filter { !it.evaluated },
            done = o.tasks.filter { it.evaluated },
            syncedAt = at,
            noAccount = false,
            error = null,
        )
    }
}
