package edu.csuft.sap.ui.auth

import androidx.lifecycle.ViewModel
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.repository.RegistrationCaptcha
import edu.csuft.sap.data.repository.RegistrationGateway
import edu.csuft.sap.data.repository.RegistrationResult
import edu.csuft.sap.di.Graph
import edu.csuft.sap.data.remote.dto.RegisterEmailRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val repository: RegistrationGateway = Graph.registrationRepository,
    private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {
    data class UiState(
        val form: RegistrationForm = RegistrationForm(),
        val fieldErrors: Map<RegistrationField, String> = emptyMap(),
        val validationAttempt: Int = 0,
        val loading: Boolean = false,
        val emailLoading: Boolean = false,
        val emailRequestId: String? = null,
        val emailNotice: String? = null,
        val cooldownSeconds: Int = 0,
        val error: String? = null,
        val captchaRequired: Boolean = false,
        val captchaLoading: Boolean = false,
        val captcha: RegistrationCaptcha? = null,
        val captchaError: String? = null,
        val registeredStudentId: String? = null,
    ) {
        val busy get() = loading || emailLoading || captchaLoading
        val canSubmit get() = !busy && registeredStudentId == null
        val canSendEmail get() = !busy && registeredStudentId == null && cooldownSeconds == 0 && (!captchaRequired || captcha != null)
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var cooldownJob: Job? = null

    fun edit(field: RegistrationField, value: String) {
        val current = _state.value
        if (current.busy || current.registeredStudentId != null) return
        val form = when (field) {
            RegistrationField.STUDENT_ID -> current.form.copy(studentId = value)
            RegistrationField.PASSWORD -> current.form.copy(password = value)
            RegistrationField.NAME -> current.form.copy(name = value)
            RegistrationField.GENDER -> current.form.copy(gender = value.toIntOrNull() ?: 1)
            RegistrationField.QQ -> current.form.copy(qq = value)
            RegistrationField.CAPTCHA -> current.form.copy(captchaCode = value)
            RegistrationField.EMAIL_CODE -> current.form.copy(emailCode = value)
        }
        val recipientChanged = form.studentId.trim() != current.form.studentId.trim() || form.qq.trim() != current.form.qq.trim()
        _state.value = current.copy(form = if (recipientChanged) form.copy(emailCode = "") else form,
            fieldErrors = current.fieldErrors - field, error = null,
            emailRequestId = current.emailRequestId.takeUnless { recipientChanged },
            emailNotice = if (recipientChanged && current.emailRequestId != null) "学号或 QQ 已更改，请重新获取邮箱验证码" else current.emailNotice)
    }

    fun sendEmailCode() {
        val current = _state.value
        if (!current.canSendEmail) return
        val errors = current.form.validate(current.captchaRequired)
        if (errors.isNotEmpty()) {
            _state.value = current.copy(fieldErrors = errors, validationAttempt = current.validationAttempt + 1,
                error = "请先检查填写项，再获取邮箱验证码")
            return
        }
        val form = current.form
        _state.value = current.copy(emailLoading = true, error = null, fieldErrors = emptyMap())
        viewModelScope.launch {
            when (val result = repository.requestEmailCode(RegisterEmailRequest(form.studentId.trim(), form.name.trim(),
                form.qq.trim(), current.captcha?.id, form.captchaCode.trim().takeIf { current.captcha != null }))) {
                is Outcome.Success -> {
                    if (result.data.captchaRequired) {
                        _state.value = _state.value.copy(emailLoading = false, captchaRequired = true)
                        refreshCaptcha()
                    } else {
                        _state.value = _state.value.copy(emailLoading = false, captchaRequired = false, captcha = null, captchaError = null,
                            emailRequestId = result.data.requestId, emailNotice = "验证码已申请发送至 ${result.data.email}，${result.data.expiresInSeconds / 60} 分钟内有效，请查收邮箱及垃圾箱",
                            form = _state.value.form.copy(captchaCode = "", emailCode = ""))
                        startCooldown(result.data.cooldownSeconds)
                    }
                }
                is Outcome.Error -> {
                    _state.value = _state.value.copy(emailLoading = false, error = result.message)
                    if (_state.value.captchaRequired) refreshCaptcha()
                }
            }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        val deadline = nowMillis() + seconds * 1000L
        _state.value = _state.value.copy(cooldownSeconds = seconds)
        cooldownJob = viewModelScope.launch {
            while (_state.value.cooldownSeconds > 0) {
                delay(1000)
                _state.value = _state.value.copy(cooldownSeconds = ((deadline - nowMillis() + 999) / 1000).coerceAtLeast(0).toInt())
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val errors = current.form.validate(captchaRequired = false, emailRequired = true).toMutableMap()
        if (current.emailRequestId == null) errors[RegistrationField.EMAIL_CODE] = "请先获取 QQ 邮箱验证码"
        if (errors.isNotEmpty()) {
            _state.value = current.copy(fieldErrors = errors, validationAttempt = current.validationAttempt + 1,
                error = "请检查标红的填写项")
            return
        }
        val request = current.form.toRequest(null, current.emailRequestId)
        // 同步锁住提交，连点按钮或键盘完成均只发起一次请求。
        _state.value = current.copy(loading = true, fieldErrors = emptyMap(), error = null)
        viewModelScope.launch {
            when (val result = repository.register(request)) {
                is Outcome.Success -> when (result.data) {
                    RegistrationResult.REGISTERED -> {
                        // 清除密码与验证码，只把学号交回登录页。
                        cooldownJob?.cancel()
                        _state.value = UiState(registeredStudentId = request.studentId)
                    }
                    RegistrationResult.CAPTCHA_REQUIRED -> {
                        _state.value = _state.value.copy(loading = false, error = "请重新获取 QQ 邮箱验证码")
                    }
                }
                is Outcome.Error -> {
                    _state.value = _state.value.copy(loading = false, error = result.message)
                    _state.value = _state.value.copy(form = _state.value.form.copy(emailCode = ""))
                }
            }
        }
    }

    fun refreshCaptcha() {
        val current = _state.value
        if (current.busy || !current.captchaRequired || current.registeredStudentId != null) return
        _state.value = current.copy(
            captchaLoading = true, captcha = null, captchaError = null,
            form = current.form.copy(captchaCode = ""), fieldErrors = current.fieldErrors - RegistrationField.CAPTCHA,
        )
        viewModelScope.launch {
            _state.value = when (val result = repository.captcha()) {
                is Outcome.Success -> _state.value.copy(captchaLoading = false, captcha = result.data)
                is Outcome.Error -> _state.value.copy(captchaLoading = false, captchaError = result.message)
            }
        }
    }

    fun captchaImageFailed(id: String) {
        if (_state.value.captcha?.id == id) {
            _state.value = _state.value.copy(captcha = null, captchaError = "验证码图片无法显示，请点击重试")
        }
    }
}
