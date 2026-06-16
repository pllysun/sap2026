package edu.csuft.sap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 应用级鉴权门：决定显示启动校验 / 登录 / 主界面。 */
class AppViewModel : ViewModel() {

    enum class AuthState { LOADING, AUTHED, UNAUTHED }

    private val _auth = MutableStateFlow(AuthState.LOADING)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    init {
        verify()
    }

    /** 启动免密校验：本地无 token → 去登录；有 token → 调 /me 验证。 */
    private fun verify() {
        viewModelScope.launch {
            if (!Graph.authRepository.hasLocalToken()) {
                _auth.value = AuthState.UNAUTHED
                return@launch
            }
            when (Graph.authRepository.me()) {
                is Outcome.Success -> {
                    Graph.accountManager.refresh()
                    _auth.value = AuthState.AUTHED
                }
                is Outcome.Error -> {
                    Graph.tokenStore.clear()
                    _auth.value = AuthState.UNAUTHED
                }
            }
        }
    }

    fun onLoggedIn() {
        viewModelScope.launch {
            Graph.authRepository.me() // 刷新会员态(roles)，确保登录当下即按会员/非会员正确门控
            Graph.accountManager.refresh()
        }
        _auth.value = AuthState.AUTHED
    }

    fun onLoggedOut() {
        _auth.value = AuthState.UNAUTHED
    }
}
