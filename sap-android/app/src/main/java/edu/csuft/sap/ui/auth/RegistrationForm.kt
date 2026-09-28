package edu.csuft.sap.ui.auth

import edu.csuft.sap.data.remote.dto.RegisterRequest

enum class RegistrationField { STUDENT_ID, PASSWORD, NAME, GENDER, QQ, CAPTCHA, EMAIL_CODE }

/** 仅保留在当前注册页面的内存中，不向 savedState 或磁盘写入密码。 */
data class RegistrationForm(
    val studentId: String = "",
    val password: String = "",
    val name: String = "",
    val gender: Int = 1,
    val qq: String = "",
    val captchaCode: String = "",
    val emailCode: String = "",
) {
    fun validate(captchaRequired: Boolean, emailRequired: Boolean = false): Map<RegistrationField, String> = buildMap {
        if (!Regex("[A-Za-z0-9_-]{1,20}").matches(studentId.trim())) {
            put(RegistrationField.STUDENT_ID, "学号需为 1–20 位字母、数字、下划线或短横线")
        }
        if (password.isBlank() || password.length !in 6..64) {
            put(RegistrationField.PASSWORD, "请设置 6–64 位密码")
        }
        if (name.isBlank() || name.trim().length > 50) {
            put(RegistrationField.NAME, "请填写真实姓名，不超过 50 个字")
        }
        if (gender !in 0..1) put(RegistrationField.GENDER, "请选择性别")
        if (!Regex("[1-9][0-9]{4,14}").matches(qq.trim())) {
            put(RegistrationField.QQ, "请输入 5–15 位有效 QQ 号")
        }
        if (captchaRequired && (captchaCode.isBlank() || captchaCode.trim().length > 6)) {
            put(RegistrationField.CAPTCHA, "请输入图中验证码")
        }
        if (emailRequired && !Regex("[0-9]{6}").matches(emailCode)) {
            put(RegistrationField.EMAIL_CODE, "请输入 QQ 邮箱中的六位验证码")
        }
    }

    fun toRequest(captchaId: String?, emailRequestId: String? = null) = RegisterRequest(
        studentId = studentId.trim(), password = password, name = name.trim(), gender = gender,
        qq = qq.trim(), captchaId = captchaId,
        captchaCode = captchaCode.trim().takeIf { captchaId != null },
        emailRequestId = emailRequestId, emailCode = emailCode.takeIf { emailRequestId != null },
    )
}
