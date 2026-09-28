package edu.csuft.sap.data.remote.dto

/** 与 Web 注册共用字段；性别遵循注册接口：1=男，0=女。 */
data class RegisterRequest(
    val studentId: String,
    val password: String,
    val name: String,
    val gender: Int,
    val qq: String,
    val captchaId: String? = null,
    val captchaCode: String? = null,
    val emailRequestId: String? = null,
    val emailCode: String? = null,
)

data class RegisterEmailRequest(
    val studentId: String,
    val name: String,
    val qq: String,
    val captchaId: String? = null,
    val captchaCode: String? = null,
)

data class RegistrationEmailData(
    val captchaRequired: Boolean = false,
    val requestId: String? = null,
    val email: String? = null,
    val cooldownSeconds: Int = 180,
    val expiresInSeconds: Int = 900,
    val notice: String? = null,
)

data class RegistrationCaptchaDto(
    val captchaId: String? = null,
    val image: String? = null,
)
