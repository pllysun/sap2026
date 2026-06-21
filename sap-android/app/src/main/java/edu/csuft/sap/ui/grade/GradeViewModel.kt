package edu.csuft.sap.ui.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.account.JwMfaState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 成绩：进页面只读本地缓存，不打教务；用户点「同步」才请求并回写缓存。
 * 首次（无缓存）自动同步一次，之后全走缓存——避免大量人员频繁请求触发风控。
 */
class GradeViewModel : ViewModel() {

    data class UiState(
        val syncing: Boolean = false,
        val error: String? = null,
        val noAccount: Boolean = false,
        val grades: List<GradeDto> = emptyList(),
        val syncedAt: Long? = null, // 缓存时间；null = 从未同步
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

    /** 切账号/进页面：只读缓存。无缓存（首次）才自动同步一次。 */
    private fun loadCache() {
        val account = acc.activeAccount
        if (account == null) {
            _state.value = UiState(noAccount = true, error = "请先在「我的」里绑定教务账号")
            return
        }
        val c = cache.grades(account)
        _state.value = UiState(
            grades = c?.items ?: emptyList(),
            syncedAt = c?.syncedAt?.takeIf { it > 0 },
        )
        if (c == null) sync()
    }

    /** 手动同步：拉教务成绩并回写缓存；失败保留旧缓存只提示错误。 */
    fun sync() {
        val account = acc.activeAccount ?: return
        if (_state.value.syncing) return
        _state.value = _state.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            _state.value = when (val r = jw.grades(account)) {
                is Outcome.Success -> {
                    val at = cache.saveGrades(account, r.data)
                    _state.value.copy(syncing = false, grades = r.data, syncedAt = at, error = null)
                }
                is Outcome.Error -> _state.value.copy(syncing = false, error = r.message)
            }
        }
    }
}
