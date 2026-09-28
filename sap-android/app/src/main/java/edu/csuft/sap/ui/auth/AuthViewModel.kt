package edu.csuft.sap.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.di.Graph
import edu.csuft.sap.data.remote.dto.LoginData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val memberConsentRequired: Boolean = false,
        val consentRequired: Boolean = false,
        val offline: Boolean = false, // 登录时连不上服务器 → 直接进离线模式
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val pendingLogin = LoginApproval<Pair<LoginData, String>>()

    fun login(studentId: String, password: String) {
        if (_state.value.loading || _state.value.consentRequired) return
        if (studentId.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "请输入学号和密码")
            return
        }
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            _state.value = when (val r = Graph.authRepository.appLogin(studentId, password)) {
                is Outcome.Success -> {
                    pendingLogin.stage(r.data to studentId)
                    UiState(consentRequired = true,
                        memberConsentRequired = requiresMemberPrivacy(r.data.roles, r.data.appAccessLevel))
                }
                is Outcome.Error ->
                    if (r.offline) UiState(offline = true)   // 连不上服务器 → 不报错，直接进离线
                    else UiState(error = r.message)
            }
        }
    }

    fun acceptMemberPrivacy() {
        if (!_state.value.consentRequired) return
        try {
            if (pendingLogin.approve { (data, account) -> Graph.authRepository.completeAppLogin(data, account) }) {
                _state.value = UiState(success = true)
            }
        } catch (_: Exception) {
            _state.value = _state.value.copy(error = "无法保存登录状态，请重试")
        }
    }

    fun declineMemberPrivacy() {
        pendingLogin.discard()
        _state.value = UiState()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** 已消费"离线"信号（已切入离线模式）后复位，避免重显登录页时残留。 */
    fun consumeOffline() {
        _state.value = _state.value.copy(offline = false)
    }

    /** 登录成功已被消费（已跳转）后复位 success。
     *  否则该 VM 是 Activity 级、跨登录/退出复用：退出登录后 LoginScreen 重显，
     *  残留的 success=true 会让 LaunchedEffect 立刻又触发 onLoggedIn → 弹回主界面（“退不出去”）。 */
    fun consumeSuccess() {
        _state.value = _state.value.copy(success = false)
    }
}

internal fun requiresMemberPrivacy(roles: List<Int>, level: Int = 0): Boolean = roles.any { it <= 3 } || level >= 2
