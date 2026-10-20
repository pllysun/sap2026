package edu.csuft.sap.data.remote.dto

data class RecoverySendRequest(val account: String, val captchaId: String, val captcha: String)
data class RecoverySendData(val requestId: String?, val cooldownSeconds: Int, val expiresInSeconds: Int, val notice: String?)
data class RecoveryResetRequest(val requestId: String, val code: String, val newPassword: String)
