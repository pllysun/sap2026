package edu.csuft.sap.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.account.BoundAccount
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.UserDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 我的页 VM：会员信息 + 多教务学号的绑定/切换/备注/解绑。 */
class ProfileViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val user: UserDto? = null,
        val accounts: List<BoundAccount> = emptyList(),
        val activeAccount: String? = null,
        val bindLoading: Boolean = false,
        val bindError: String? = null,
        val bindSuccess: Boolean = false,
        val captchaImage: String? = null,   // base64，非空=需手动输入验证码
        val captchaChallenge: String? = null,
    )

    private var pendingAccount: String? = null

    private val acc = Graph.accountManager
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            acc.accounts.collect { list -> _state.value = _state.value.copy(accounts = list) }
        }
        viewModelScope.launch {
            acc.active.collect { a -> _state.value = _state.value.copy(activeAccount = a) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val user = (Graph.authRepository.me() as? Outcome.Success)?.data
            acc.refresh()
            _state.value = _state.value.copy(loading = false, user = user)
        }
    }

    fun bind(account: String, password: String) {
        if (account.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(bindError = "请输入学校账号和密码")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                bindLoading = true, bindError = null, bindSuccess = false,
                captchaImage = null, captchaChallenge = null,
            )
            when (val r = Graph.jwRepository.bind(account, password)) {
                is Outcome.Success -> handleBindResult(r.data, account.trim())
                is Outcome.Error -> _state.value = _state.value.copy(bindLoading = false, bindError = r.message)
            }
        }
    }

    /** 提交手动输入的验证码续登。 */
    fun submitCaptcha(code: String) {
        val cid = _state.value.captchaChallenge ?: return
        if (code.isBlank()) {
            _state.value = _state.value.copy(bindError = "请输入验证码")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(bindLoading = true, bindError = null)
            when (val r = Graph.jwRepository.bindCaptcha(cid, code)) {
                is Outcome.Success -> handleBindResult(r.data, pendingAccount ?: "")
                is Outcome.Error -> _state.value = _state.value.copy(bindLoading = false, bindError = r.message)
            }
        }
    }

    private suspend fun handleBindResult(data: edu.csuft.sap.data.remote.dto.BindResult, account: String) {
        if (data.needCaptcha) {
            pendingAccount = account
            _state.value = _state.value.copy(
                bindLoading = false, bindError = null,
                captchaImage = data.captchaImage, captchaChallenge = data.challengeId,
            )
        } else {
            acc.refresh()
            if (account.isNotBlank()) acc.setActive(account)
            _state.value = _state.value.copy(
                bindLoading = false, bindSuccess = true,
                captchaImage = null, captchaChallenge = null,
            )
        }
    }

    fun unbind(account: String) {
        viewModelScope.launch {
            Graph.jwRepository.unbind(account)
            acc.refresh()
        }
    }

    fun switchAccount(account: String) = acc.setActive(account)

    fun setNickname(account: String, nickname: String?) = acc.setNickname(account, nickname)

    fun consumeBindResult() {
        pendingAccount = null
        _state.value = _state.value.copy(
            bindSuccess = false, bindError = null, captchaImage = null, captchaChallenge = null,
        )
    }

    fun logout(onDone: () -> Unit) {
        // 先清本地凭证并立即跳转，避免等服务端 logout 网络超时导致“点了没反应”。
        Graph.authRepository.clearLocalToken()
        onDone()
        // 服务端登出后台尽力而为：用应用级 scope，不随本 VM 销毁而取消。
        Graph.appScope.launch { Graph.authRepository.logoutRemote() }
    }
}
