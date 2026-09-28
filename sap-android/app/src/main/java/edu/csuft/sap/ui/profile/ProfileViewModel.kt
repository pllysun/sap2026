package edu.csuft.sap.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.account.BoundAccount
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.IdentityDto
import edu.csuft.sap.data.remote.dto.UserDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 我的页 VM：会员信息 + 多教务学号的绑定/切换/备注/解绑。 */
class ProfileViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val user: UserDto? = null,
        val identities: List<IdentityDto> = emptyList(), // 平台身份(届+职务)，空=游客
        val avatarVersion: Long? = null, // = 服务端 updatedAt，用于头像 URL 缓存破坏(固定URL换图也能刷新)
        val accounts: List<BoundAccount> = emptyList(),
        val activeAccount: String? = null,
        val bindLoading: Boolean = false,
        val bindError: String? = null,
        val bindSuccess: Boolean = false,
        val captchaImage: String? = null,   // base64，非空=需手动输入验证码
        val captchaChallenge: String? = null,
        val mfaPhone: String? = null,        // 非空=需输入短信二次验证码（掩码手机号）
        val mfaChallenge: String? = null,
        val profileSaving: Boolean = false,  // 个人信息保存中
        val profileError: String? = null,
        val nicknameSaving: Boolean = false,
        val nicknameError: String? = null,
    )

    private var pendingAccount: String? = null

    private val acc = Graph.accountManager
    private val cachedUser = Graph.authRepository.cachedMe()
    private val _state = MutableStateFlow(UiState(
        user = cachedUser?.user,
        identities = cachedUser?.identities.orEmpty(),
        avatarVersion = cachedUser?.updatedAt,
    ))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val avatarLoader = AvatarLoader(Graph.appContext)
    val avatar = avatarLoader.image
    private var refreshJob: Job? = null
    private var accountsRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            acc.accounts.collect { list -> _state.value = _state.value.copy(accounts = list) }
        }
        viewModelScope.launch {
            acc.active.collect { a -> _state.value = _state.value.copy(activeAccount = a) }
        }
        // 会员账号切换（登出再登录另一个账号）：本 VM 是 Activity 级、不会重建，
        // 故监听当前账号变化，主动清掉上一个账号的内存信息并重新加载，避免“切游客号还显示会员信息”。
        viewModelScope.launch {
            CurrentAccount.uid.drop(1).collect {
                refreshJob?.cancel()
                refreshJob = null
                accountsRefreshJob?.cancel()
                accountsRefreshJob = null
                avatarLoader.activate(CurrentAccount.key)
                _state.value = _state.value.copy(user = null, identities = emptyList(), avatarVersion = null)
                refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val account = CurrentAccount.key
        refreshJob = viewModelScope.launch {
            // 先用本地缓存即时展示（含头像/身份），避免白屏；离线也有内容
            Graph.authRepository.cachedMe()?.let { c ->
                _state.value = _state.value.copy(user = c.user, identities = c.identities, avatarVersion = c.updatedAt)
            }
            loadAvatar(account)
            _state.value = _state.value.copy(loading = _state.value.user == null, error = null)
            // 省流量同步：先轻量接口，仅资料/头像变过才重新拉头像
            when (val r = Graph.authRepository.syncUser()) {
                is Outcome.Success -> if (CurrentAccount.key == account) {
                    _state.value = _state.value.copy(
                        loading = false, user = r.data.user, identities = r.data.identities, avatarVersion = r.data.updatedAt)
                    loadAvatar(account)
                }
                is Outcome.Error -> if (CurrentAccount.key == account) _state.value = _state.value.copy(loading = false)
            }
            if (CurrentAccount.key == account && accountsRefreshJob?.isActive != true) {
                accountsRefreshJob = viewModelScope.launch { acc.refresh() }
            }
        }
    }

    private fun loadAvatar(account: String) {
        val url = avatarUrlOf(_state.value.user?.avatar, _state.value.avatarVersion)
        viewModelScope.launch {
            if (CurrentAccount.key == account) avatarLoader.load(account, url)
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
                captchaImage = null, captchaChallenge = null, mfaPhone = null, mfaChallenge = null,
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

    /** 提交短信二次验证码续登。 */
    fun submitMfa(code: String) {
        val cid = _state.value.mfaChallenge ?: return
        if (code.isBlank()) {
            _state.value = _state.value.copy(bindError = "请输入短信验证码")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(bindLoading = true, bindError = null)
            when (val r = Graph.jwRepository.bindMfa(cid, code)) {
                is Outcome.Success -> handleBindResult(r.data, pendingAccount ?: "")
                is Outcome.Error -> _state.value = _state.value.copy(bindLoading = false, bindError = r.message)
            }
        }
    }

    /** 重新发送短信验证码。 */
    fun resendMfa() {
        val cid = _state.value.mfaChallenge ?: return
        viewModelScope.launch {
            when (val r = Graph.jwRepository.bindMfaResend(cid)) {
                is Outcome.Success -> _state.value = _state.value.copy(bindError = "验证码已重新发送")
                is Outcome.Error -> _state.value = _state.value.copy(bindError = r.message)
            }
        }
    }

    private suspend fun handleBindResult(data: edu.csuft.sap.data.remote.dto.BindResult, account: String) {
        when {
            data.needCaptcha -> {
                pendingAccount = account
                _state.value = _state.value.copy(
                    bindLoading = false, bindError = null,
                    captchaImage = data.captchaImage, captchaChallenge = data.challengeId,
                    mfaPhone = null, mfaChallenge = null,
                )
            }
            data.needMfa -> {
                pendingAccount = account
                _state.value = _state.value.copy(
                    bindLoading = false, bindError = null,
                    captchaImage = null, captchaChallenge = null,
                    mfaPhone = data.phone ?: "", mfaChallenge = data.challengeId,
                )
            }
            else -> {
                acc.refresh()
                if (account.isNotBlank()) acc.setActive(account)
                _state.value = _state.value.copy(
                    bindLoading = false, bindSuccess = true,
                    captchaImage = null, captchaChallenge = null, mfaPhone = null, mfaChallenge = null,
                )
            }
        }
    }

    fun unbind(account: String) {
        viewModelScope.launch {
            Graph.jwRepository.unbind(account)
            acc.refresh()
        }
    }

    /** 保存个人信息（网名/性别/头像 url）；成功后刷新用户信息并回调。 */
    fun saveProfile(nickname: String?, gender: Int?, avatar: String?, onDone: () -> Unit) {
        val account = CurrentAccount.key
        viewModelScope.launch {
            _state.value = _state.value.copy(profileSaving = true, profileError = null)
            when (val r = Graph.authRepository.updateProfile(nickname, gender, avatar)) {
                is Outcome.Success -> {
                    if (CurrentAccount.key != account) return@launch
                    // 保存后 updatedAt 已变，syncUser 会按规则重新拉到新头像并刷新缓存
                    val synced = (Graph.authRepository.syncUser() as? Outcome.Success)?.data
                    if (CurrentAccount.key != account) return@launch
                    _state.value = _state.value.copy(
                        profileSaving = false,
                        user = synced?.user ?: _state.value.user,
                        identities = synced?.identities ?: _state.value.identities,
                        avatarVersion = synced?.updatedAt ?: _state.value.avatarVersion,
                    )
                    loadAvatar(account)
                    onDone()
                }
                is Outcome.Error -> _state.value = _state.value.copy(profileSaving = false, profileError = r.message)
            }
        }
    }

    /** 上传头像图片字节，返回 COS url；失败返回 null 并把后端真实原因写入 profileError（不再静默吞掉）。 */
    suspend fun uploadAvatar(bytes: ByteArray, name: String): String? =
        when (val r = Graph.authRepository.uploadAvatar(bytes, name)) {
            is Outcome.Success -> r.data
            is Outcome.Error -> {
                _state.value = _state.value.copy(profileError = r.message ?: "头像上传失败")
                null
            }
        }

    fun clearProfileError() { _state.value = _state.value.copy(profileError = null) }

    fun switchAccount(account: String) = acc.setActive(account)

    fun setNickname(account: String, nickname: String?, onDone: () -> Unit) {
        val clean = nickname?.trim()?.takeIf(String::isNotBlank)
        if (clean != null && clean.length > 40) {
            _state.value = _state.value.copy(nicknameError = "备注不能超过 40 个字符")
            return
        }
        if (_state.value.nicknameSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(nicknameSaving = true, nicknameError = null)
            when (val result = acc.setNickname(account, clean)) {
                is Outcome.Success -> {
                    _state.value = _state.value.copy(nicknameSaving = false, nicknameError = null)
                    onDone()
                }
                is Outcome.Error -> _state.value = _state.value.copy(
                    nicknameSaving = false,
                    nicknameError = result.message ?: "备注保存失败",
                )
            }
        }
    }

    fun clearNicknameError() {
        _state.value = _state.value.copy(nicknameError = null)
    }

    fun consumeBindResult() {
        pendingAccount = null
        _state.value = _state.value.copy(
            bindSuccess = false, bindError = null, captchaImage = null, captchaChallenge = null,
            mfaPhone = null, mfaChallenge = null,
        )
    }

    fun logout(onDone: () -> Unit) {
        // 离线模式下退出登录无效：不清凭证、不跳转（符合"除非服务端主动踢出否则不登出"的设计）。
        if (!edu.csuft.sap.data.account.ConnectivityState.online) return
        // 清本地凭证 + 立即跳转登录。
        Graph.authRepository.clearLocalToken()
        onDone()
        // 服务端登出后台尽力而为。
        Graph.appScope.launch { runCatching { Graph.authRepository.logoutRemote() } }
        // 注意：绝不在退出登录里调 CookieManager/WebStorage.getInstance()——那是 App 首次触碰 WebView 时
        // 在主线程同步初始化 Chromium 的重操作，会卡住「切回登录页」的重组导致“退不出去”。
        // 网页 CAS 登录态由 WebImportScreen 离开时自行清理（且每次进入需重新登录），此处无需处理。
    }
}
