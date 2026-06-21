package edu.csuft.sap.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val offline: Boolean = false, // 登录时连不上服务器 → 直接进离线模式
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun login(studentId: String, password: String) {
        if (studentId.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "请输入学号和密码")
            return
        }
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            _state.value = when (val r = Graph.authRepository.appLogin(studentId, password)) {
                is Outcome.Success -> UiState(success = true)
                is Outcome.Error ->
                    if (r.offline) UiState(offline = true)   // 连不上服务器 → 不报错，直接进离线
                    else UiState(error = r.message)
            }
        }
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
