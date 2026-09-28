package edu.csuft.sap.ui.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.JwMfaState
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            combine(acc.active, acc.contextVersion, CurrentAccount.uid,
                snapshotFlow { MemberState.isJw to ConnectivityState.online }) { _, _, _, _ -> Unit }
                .collect { loadCache() }
        }
        // 教务短信验证通过后自动重试同步
        viewModelScope.launch { JwMfaState.passedTick.drop(1).collect { sync() } }
    }

    /** 切账号/进页面：只读缓存。无缓存（首次）才自动同步一次。 */
    private fun loadCache() {
        syncJob?.cancel()
        syncJob = null
        val account = acc.activeAccount
        if (account == null || AccountManager.isLocalOrClass(account) || !MemberState.isJw) {
            _state.value = UiState(noAccount = true,
                error = if (MemberState.isJw) "请先在「我的」里绑定教务账号" else null)
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
        if (!canSyncGrades(account, MemberState.isJw, ConnectivityState.online)) return
        if (_state.value.syncing) return
        val owner = CurrentAccount.key
        _state.value = _state.value.copy(syncing = true, error = null)
        syncJob = viewModelScope.launch {
            val r = jw.grades(account)
            if (owner != CurrentAccount.key || account != acc.activeAccount ||
                !canSyncGrades(account, MemberState.isJw, ConnectivityState.online)) return@launch
            _state.value = when (r) {
                is Outcome.Success -> {
                    val at = cache.saveGrades(account, r.data)
                    _state.value.copy(syncing = false, grades = r.data, syncedAt = at, error = null)
                }
                is Outcome.Error -> _state.value.copy(syncing = false, error = r.message)
            }
        }
    }
}

/** Activity 保留的成绩页不能把班级/Web 缓存槽当成教务学号发起同步。 */
internal fun canSyncGrades(account: String?, academicMode: Boolean, online: Boolean): Boolean =
    online && academicMode && !account.isNullOrBlank() && !AccountManager.isLocalOrClass(account)
