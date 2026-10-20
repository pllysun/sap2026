package edu.csuft.sap.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.*
import edu.csuft.sap.data.repository.*
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecoveryField { ACCOUNT, CAPTCHA, CODE, PASSWORD, CONFIRMATION }

data class RecoveryForm(val account: String = "", val captcha: String = "", val code: String = "",
                        val password: String = "", val confirmation: String = "") {
    fun validateRequest(): Map<RecoveryField, String> = buildMap {
        if (!account.trim().matches(Regex("[A-Za-z0-9_-]{1,20}"))) put(RecoveryField.ACCOUNT, "请输入正确的注册账号（学号）")
        if (captcha.isBlank()) put(RecoveryField.CAPTCHA, "请输入图形验证码")
    }
    fun validateReset(): Map<RecoveryField, String> = buildMap {
        if (!code.matches(Regex("[0-9]{6}"))) put(RecoveryField.CODE, "请输入 6 位邮箱验证码")
        if (password.length !in 6..64 || password.toByteArray(Charsets.UTF_8).size > 72)
            put(RecoveryField.PASSWORD, "密码需为 6–64 位，中文等字符合计不超过 72 字节")
        if (confirmation != password) put(RecoveryField.CONFIRMATION, "两次输入的密码不一致")
    }
}

class PasswordRecoveryViewModel(
    private val gateway: PasswordRecoveryGateway = Graph.passwordRecoveryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    data class UiState(val form: RecoveryForm = RecoveryForm(), val captcha: RegistrationCaptcha? = null,
        val captchaLoading: Boolean = false, val loading: Boolean = false, val requestId: String? = null,
        val error: String? = null, val fieldErrors: Map<RecoveryField, String> = emptyMap(),
        val remaining: Int = 0, val expired: Boolean = false, val done: Boolean = false)
    private val mutable = MutableStateFlow(UiState())
    val state = mutable.asStateFlow()
    private var cooldownJob: Job? = null
    private var expiresAt = 0L
    init { startTicker(); refreshCaptcha() }

    fun prefill(account: String) {
        if (mutable.value.form.account.isBlank() && mutable.value.requestId == null)
            mutable.value = mutable.value.copy(form = mutable.value.form.copy(account = account.trim().take(20)))
    }
    fun edit(field: RecoveryField, value: String) {
        val current = mutable.value
        if (current.loading || current.done) return
        val form = when (field) {
            RecoveryField.ACCOUNT -> current.form.copy(account = value.take(20))
            RecoveryField.CAPTCHA -> current.form.copy(captcha = value.take(8))
            RecoveryField.CODE -> current.form.copy(code = value.filter(Char::isDigit).take(6))
            RecoveryField.PASSWORD -> current.form.copy(password = value.take(64))
            RecoveryField.CONFIRMATION -> current.form.copy(confirmation = value.take(64))
        }
        mutable.value = current.copy(form = form, error = null, fieldErrors = current.fieldErrors - field)
    }
    fun refreshCaptcha() {
        if (mutable.value.loading || mutable.value.captchaLoading) return
        mutable.value = mutable.value.copy(captchaLoading = true, captcha = null,
            form = mutable.value.form.copy(captcha = ""))
        viewModelScope.launch {
            mutable.value = when (val result = gateway.captcha()) {
                is Outcome.Success -> mutable.value.copy(captchaLoading = false, captcha = result.data)
                is Outcome.Error -> mutable.value.copy(captchaLoading = false, error = result.message)
            }
        }
    }
    fun send() {
        val current = mutable.value
        if (current.loading || current.captchaLoading || current.remaining > 0 || current.done) return
        val errors = current.form.validateRequest()
        if (errors.isNotEmpty() || current.captcha == null) {
            mutable.value = current.copy(fieldErrors = errors, error = if (current.captcha == null) "请先加载图形验证码" else null)
            return
        }
        mutable.value = current.copy(loading = true, error = null, fieldErrors = emptyMap())
        viewModelScope.launch {
            when (val result = gateway.send(RecoverySendRequest(current.form.account.trim(), current.captcha.id, current.form.captcha.trim()))) {
                is Outcome.Success -> {
                    gateway.rememberCooldown(now() + result.data.cooldownSeconds * 1000L)
                    expiresAt = now() + result.data.expiresInSeconds * 1000L
                    mutable.value = mutable.value.copy(loading = false, requestId = result.data.requestId,
                        captcha = null, expired = false, form = RecoveryForm(account = current.form.account.trim()))
                    startTicker()
                }
                is Outcome.Error -> {
                    mutable.value = mutable.value.copy(loading = false, error = result.message)
                    refreshCaptcha() // 图片验证码为一次性；失败后刷新，不自动重发邮件。
                }
            }
        }
    }
    fun reset() {
        val current = mutable.value
        if (current.loading || current.requestId == null || current.done) return
        if (expiresAt <= now()) { mutable.value = current.copy(expired = true, error = "邮箱验证码已过期，请重新获取"); return }
        val errors = current.form.validateReset()
        if (errors.isNotEmpty()) { mutable.value = current.copy(fieldErrors = errors, error = null); return }
        mutable.value = current.copy(loading = true, error = null, fieldErrors = emptyMap())
        viewModelScope.launch {
            mutable.value = when (val result = gateway.reset(RecoveryResetRequest(current.requestId, current.form.code, current.form.password))) {
                is Outcome.Success -> mutable.value.copy(loading = false, done = true, requestId = null,
                    form = RecoveryForm(account = current.form.account))
                is Outcome.Error -> mutable.value.copy(loading = false, error = result.message)
            }
        }
    }
    fun requestAgain() {
        if (mutable.value.loading || mutable.value.remaining > 0) return
        expiresAt = 0
        mutable.value = UiState(form = RecoveryForm(account = mutable.value.form.account))
        refreshCaptcha()
    }
    private fun startTicker() {
        cooldownJob?.cancel()
        fun update() {
            mutable.value = mutable.value.copy(remaining = ((gateway.cooldownUntil - now() + 999) / 1000).coerceIn(0, 3600).toInt(),
                expired = expiresAt > 0 && expiresAt <= now())
        }
        update()
        cooldownJob = viewModelScope.launch {
            while (gateway.cooldownUntil > now() || expiresAt > now()) { delay(1000); update() }
        }
    }
}
