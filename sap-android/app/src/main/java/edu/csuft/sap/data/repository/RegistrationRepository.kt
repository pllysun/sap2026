package edu.csuft.sap.data.repository

import com.google.gson.JsonElement
import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.RegisterRequest
import edu.csuft.sap.data.remote.dto.RegisterEmailRequest
import edu.csuft.sap.data.remote.dto.RegistrationEmailData
import java.util.Base64

enum class RegistrationResult { REGISTERED, CAPTCHA_REQUIRED }

data class RegistrationCaptcha(val id: String, val image: ByteArray)

interface RegistrationGateway {
    suspend fun register(request: RegisterRequest): Outcome<RegistrationResult>
    suspend fun captcha(): Outcome<RegistrationCaptcha>
    suspend fun requestEmailCode(request: RegisterEmailRequest): Outcome<RegistrationEmailData>
}

/** 注册不会创建 App 会话；完成后仍需走原有登录与协议确认流程。 */
class RegistrationRepository(private val api: ApiService) : RegistrationGateway {
    override suspend fun requestEmailCode(request: RegisterEmailRequest): Outcome<RegistrationEmailData> =
        when (val result = apiData { api.registrationEmailCode(request) }) {
            is Outcome.Success -> if (result.data.captchaRequired ||
                (!result.data.requestId.isNullOrBlank() && result.data.email == request.qq + "@qq.com" &&
                    result.data.cooldownSeconds in 1..3600 && result.data.expiresInSeconds in 1..3600)) result
                else Outcome.Error("邮箱验证码返回异常，请重新获取")
            is Outcome.Error -> result
        }
    override suspend fun register(request: RegisterRequest): Outcome<RegistrationResult> =
        when (val result = apiData { api.register(request) }) {
            is Outcome.Success -> parseRegistrationResult(result.data)
            is Outcome.Error -> result
        }

    override suspend fun captcha(): Outcome<RegistrationCaptcha> =
        when (val result = apiData { api.registrationCaptcha() }) {
            is Outcome.Success -> {
                val id = result.data.captchaId
                val image = result.data.image
                val bytes = runCatching {
                    Base64.getDecoder().decode(image?.substringAfter(',').orEmpty())
                }.getOrNull()
                if (id.isNullOrBlank() || bytes == null || bytes.isEmpty()) {
                    Outcome.Error("验证码加载失败，请点击重试")
                } else {
                    Outcome.Success(RegistrationCaptcha(id, bytes))
                }
            }
            is Outcome.Error -> result
        }
}

internal fun parseRegistrationResult(data: JsonElement): Outcome<RegistrationResult> {
    if (data.isJsonObject) {
        val required = data.asJsonObject.get("captchaRequired")
        if (required?.isJsonPrimitive == true && required.asJsonPrimitive.isBoolean && required.asBoolean) {
            return Outcome.Success(RegistrationResult.CAPTCHA_REQUIRED)
        }
    }
    if (data.isJsonPrimitive && data.asJsonPrimitive.isString && data.asString == "注册成功") {
        return Outcome.Success(RegistrationResult.REGISTERED)
    }
    return Outcome.Error("注册返回异常，请稍后重试")
}
