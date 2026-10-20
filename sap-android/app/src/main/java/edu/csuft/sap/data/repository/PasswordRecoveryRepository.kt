package edu.csuft.sap.data.repository

import android.content.Context
import edu.csuft.sap.data.remote.*
import edu.csuft.sap.data.remote.dto.*

interface PasswordRecoveryGateway {
    val cooldownUntil: Long
    fun rememberCooldown(deadline: Long)
    suspend fun captcha(): Outcome<RegistrationCaptcha>
    suspend fun send(request: RecoverySendRequest): Outcome<RecoverySendData>
    suspend fun reset(request: RecoveryResetRequest): Outcome<Unit>
}

/** 与网页共用服务端限流和邮件验证；不创建会话、不自动登录、不保存密码和邮箱验证码。 */
class PasswordRecoveryRepository(private val api: ApiService, context: Context) : PasswordRecoveryGateway {
    private val prefs = context.getSharedPreferences("sap_password_recovery", Context.MODE_PRIVATE)
    override val cooldownUntil: Long get() = prefs.getLong("cooldown_until", 0)
    override fun rememberCooldown(deadline: Long) { prefs.edit().putLong("cooldown_until", deadline).apply() }
    override suspend fun captcha() = RegistrationRepository(api).captcha()
    override suspend fun send(request: RecoverySendRequest): Outcome<RecoverySendData> =
        when (val result = apiData { api.recoverySend(request) }) {
            is Outcome.Success -> if (result.data.requestId?.matches(Regex("[0-9a-f-]{36}")) == true &&
                result.data.cooldownSeconds in 1..3600 && result.data.expiresInSeconds in 1..3600) result
                else Outcome.Error("验证码申请返回异常，请稍后重试")
            is Outcome.Error -> result
        }
    override suspend fun reset(request: RecoveryResetRequest): Outcome<Unit> = apiUnit { api.recoveryReset(request) }
}
