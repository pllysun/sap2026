package edu.csuft.sap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用级门控：进场/切回前台都探一次后端可达性，决定 在线 / 离线 / 需登录。
 *
 * 铁律（永久免密 + 离线兜底）：
 * - 除非服务端**明确 401**（被踢/凭证失效），否则**绝不清本地 token**。
 * - 没网/服务器宕机（连接级失败）→ 不踢、不清、不弹登录，**丝滑切入离线(WEB)模式**，只看本地课表。
 * - 下次进场重新探到可达 → 自动恢复在线（已登录回主界面 / 否则去登录）。
 */
class AppViewModel : ViewModel() {

    /** LOADING=探测中；APP=进主界面(在线或离线)；LOGIN=在线但需登录。 */
    enum class Gate { LOADING, APP, LOGIN }

    private val _gate = MutableStateFlow(Gate.LOADING)
    val gate: StateFlow<Gate> = _gate.asStateFlow()

    @Volatile
    private var probing = false

    init { probe() }

    /** 切回前台重探：服务器恢复→自动上线；服务器挂了→自动离线。 */
    fun onForeground() = probe()

    private fun probe() {
        if (probing) return
        probing = true
        val hasToken = Graph.authRepository.hasLocalToken()
        // 冷启动且有凭证 → 先乐观进主界面（丝滑、不等网络）；探测结果再调整 online / 是否需登录。
        if (_gate.value == Gate.LOADING && hasToken) _gate.value = Gate.APP
        viewModelScope.launch {
            try {
                if (hasToken) probeWithToken() else probeNoToken()
            } finally {
                probing = false
            }
        }
    }

    private suspend fun probeWithToken() {
        when (val r = apiData { Graph.probeApi.me() }) {
            is Outcome.Success -> {
                ConnectivityState.online = true
                MemberState.setRoles(r.data.roles)
                Graph.accountManager.refresh()
                _gate.value = Gate.APP
            }
            is Outcome.Error -> when {
                r.offline -> { ConnectivityState.online = false; _gate.value = Gate.APP }                 // 连不上 → 离线兜底，保留登录
                r.code == 401 -> { ConnectivityState.online = true; Graph.authRepository.clearLocalToken(); _gate.value = Gate.LOGIN } // 被踢/失效 → 清凭证+缓存去登录
                else -> { ConnectivityState.online = true; _gate.value = Gate.APP }                       // 5xx 等：可达，乐观保持登录
            }
        }
    }

    private suspend fun probeNoToken() {
        when (val r = apiData { Graph.probeApi.ping() }) {
            is Outcome.Success -> { ConnectivityState.online = true; _gate.value = Gate.LOGIN }
            is Outcome.Error ->
                if (r.offline) { ConnectivityState.online = false; _gate.value = Gate.APP }               // 连不上 → 直接进离线 web
                else { ConnectivityState.online = true; _gate.value = Gate.LOGIN }                        // 可达 → 去登录
        }
    }

    fun onLoggedIn() {
        ConnectivityState.online = true
        viewModelScope.launch {
            Graph.authRepository.me()                 // 刷新会员角色
            Graph.accountManager.onUserChanged()      // 切到该会员账号命名空间（重载其上次激活的教务号）后再拉取
            Graph.accountManager.refresh()
        }
        _gate.value = Gate.APP
    }

    fun onLoggedOut() {
        ConnectivityState.online = true
        _gate.value = Gate.LOGIN
    }

    /** 登录页网络不通时「直接进入离线模式」。 */
    fun enterOffline() {
        ConnectivityState.online = false
        _gate.value = Gate.APP
    }
}
