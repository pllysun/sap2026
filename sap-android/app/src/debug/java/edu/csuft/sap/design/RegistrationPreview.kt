package edu.csuft.sap.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import edu.csuft.sap.data.repository.RegistrationCaptcha
import edu.csuft.sap.ui.auth.RegistrationContent
import edu.csuft.sap.ui.auth.RegistrationField
import edu.csuft.sap.ui.auth.RegistrationForm
import edu.csuft.sap.ui.auth.RegistrationViewModel
import java.io.ByteArrayOutputStream

/** 仅本地图形验收，不请求注册接口、不创建账号。 */
@Composable
internal fun RegistrationPreview(screen: String) {
    val sample = RegistrationForm("20260001", "sample123", "示例同学", 0, "12345678")
    var state by remember {
        mutableStateOf(RegistrationViewModel.UiState(
            form = if (screen == "register") RegistrationForm() else sample,
            loading = screen == "register-loading",
            emailRequestId = if (screen == "register-email" || screen == "register-loading") "preview-email" else null,
            emailNotice = if (screen == "register-email") "验证码已申请发送至 12345678@qq.com，15 分钟内有效，请查收邮箱及垃圾箱" else null,
            cooldownSeconds = if (screen == "register-email") 180 else 0,
            captchaRequired = screen == "register-captcha" || screen == "register-error",
            captcha = if (screen == "register-captcha" || screen == "register-error") sampleCaptcha() else null,
            error = if (screen == "register-error") "验证码错误或已过期，请刷新后重试。若仍无法完成注册，请稍后重试。" else null,
        ))
    }
    RegistrationContent(state,
        onEdit = { field, value ->
            val form = state.form
            state = state.copy(form = when (field) {
                RegistrationField.STUDENT_ID -> form.copy(studentId = value)
                RegistrationField.PASSWORD -> form.copy(password = value)
                RegistrationField.NAME -> form.copy(name = value)
                RegistrationField.GENDER -> form.copy(gender = value.toInt())
                RegistrationField.QQ -> form.copy(qq = value)
                RegistrationField.CAPTCHA -> form.copy(captchaCode = value)
                RegistrationField.EMAIL_CODE -> form.copy(emailCode = value)
            }, fieldErrors = state.fieldErrors - field)
        },
        onSubmit = {
            state = state.copy(fieldErrors = state.form.validate(false, true),
                validationAttempt = state.validationAttempt + 1)
        },
        onRefreshCaptcha = { state = state.copy(captcha = sampleCaptcha(), form = state.form.copy(captchaCode = "")) },
        onSendEmailCode = {
            val errors = state.form.validate(state.captchaRequired)
            state = if (errors.isNotEmpty()) state.copy(fieldErrors = errors, validationAttempt = state.validationAttempt + 1)
            else if (!state.captchaRequired) state.copy(captchaRequired = true, captcha = sampleCaptcha())
            else state.copy(captchaRequired = false, captcha = null, emailRequestId = "preview-email",
                emailNotice = "验证码已申请发送至 ${state.form.qq}@qq.com，15 分钟内有效", cooldownSeconds = 180)
        },
        onBack = {},
    )
}

private fun sampleCaptcha(): RegistrationCaptcha {
    val bitmap = Bitmap.createBitmap(160, 50, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        drawText("A7K2", 25f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(53, 100, 220); textSize = 32f })
    }
    val bytes = ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
    bitmap.recycle()
    return RegistrationCaptcha("preview", bytes)
}
