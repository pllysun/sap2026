package edu.csuft.sap.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.account.JwMfaState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.ExamDto
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 考试安排：进页面只读本地缓存（学期列表 + 各学期考试都缓存），不打教务。
 * 首次（无缓存）或切到未缓存学期才自动拉一次；用户点「同步」刷新学期 + 当前学期考试。
 */
class ExamViewModel : ViewModel() {

    data class UiState(
        val syncing: Boolean = false,
        val error: String? = null,
        val noAccount: Boolean = false,
        val terms: List<TermDto> = emptyList(),
        val selectedTerm: String? = null,
        val exams: List<ExamDto> = emptyList(),
        val syncedAt: Long? = null, // 当前学期考试的缓存时间
    )

    private val acc = Graph.accountManager
    private val cache = Graph.jwCacheStore
    private val jw = Graph.jwRepository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { acc.active.collect { loadCache() } }
        // 教务短信验证通过后自动重试同步
        viewModelScope.launch { JwMfaState.passedTick.drop(1).collect { sync() } }
    }

    private fun loadCache() {
        val account = acc.activeAccount
        if (account == null) {
            _state.value = UiState(noAccount = true, error = "请先在「我的」里绑定教务账号")
            return
        }
        val tc = cache.terms(account)
        val terms = tc?.items ?: emptyList()
        val sel = pickTerm(terms, _state.value.selectedTerm)
        val ec = sel?.let { cache.exams(account, it) }
        _state.value = UiState(
            terms = terms,
            selectedTerm = sel,
            exams = ec?.items ?: emptyList(),
            syncedAt = ec?.syncedAt?.takeIf { it > 0 },
        )
        if (tc == null) sync()
    }

    fun selectTerm(term: String) {
        if (term == _state.value.selectedTerm) return
        val account = acc.activeAccount ?: return
        val ec = cache.exams(account, term)
        _state.value = _state.value.copy(
            selectedTerm = term,
            exams = ec?.items ?: emptyList(),
            syncedAt = ec?.syncedAt?.takeIf { it > 0 },
            error = null,
        )
        if (ec == null) syncExams(term) // 该学期从未缓存 → 首次自动拉一次
    }

    /** 手动同步：刷新学期列表 + 当前选中学期的考试。 */
    fun sync() {
        val account = acc.activeAccount ?: return
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true, error = null)
            val terms = when (val t = jw.terms(account)) {
                is Outcome.Success -> {
                    cache.saveTerms(account, t.data)
                    t.data
                }
                is Outcome.Error -> {
                    _state.value = _state.value.copy(syncing = false, error = t.message)
                    return@launch
                }
            }
            val sel = pickTerm(terms, _state.value.selectedTerm)
            _state.value = _state.value.copy(terms = terms, selectedTerm = sel)
            when (val r = jw.exams(account, sel)) {
                is Outcome.Success -> {
                    val at = cache.saveExams(account, sel, r.data)
                    _state.value = _state.value.copy(syncing = false, exams = r.data, syncedAt = at, error = null)
                }
                is Outcome.Error -> _state.value = _state.value.copy(syncing = false, error = r.message)
            }
        }
    }

    private fun syncExams(term: String) {
        val account = acc.activeAccount ?: return
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true, error = null)
            _state.value = when (val r = jw.exams(account, term)) {
                is Outcome.Success -> {
                    val at = cache.saveExams(account, term, r.data)
                    _state.value.copy(syncing = false, exams = r.data, syncedAt = at, error = null)
                }
                is Outcome.Error -> _state.value.copy(syncing = false, error = r.message)
            }
        }
    }

    private fun pickTerm(terms: List<TermDto>, prev: String?): String? =
        prev?.takeIf { p -> terms.any { it.value == p } }
            ?: terms.firstOrNull { it.current }?.value
            ?: terms.firstOrNull()?.value
}
