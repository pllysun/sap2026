package edu.csuft.sap.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局「教务短信二次验证(MFA)」状态。
 * 拉课表/成绩/考试时，后端重新登录教务触发 MFA（短信已发），会返回 code=428 + 挑战信息；
 * 网络层拦截器据此调用 [require]，由顶层 [edu.csuft.sap.ui.AppRoot] 弹出全局短信输入框。
 * 用户输码续验成功后调 [passed]：清弹框 + 自增 [passedTick]，让各数据页自动重试拉取。
 */
object JwMfaState {
    data class Challenge(val challengeId: String, val phone: String)

    private val _challenge = MutableStateFlow<Challenge?>(null)
    val challenge: StateFlow<Challenge?> = _challenge.asStateFlow()

    /** 每次 MFA 验证通过自增；数据页 collect 后自动重试。 */
    private val _passedTick = MutableStateFlow(0L)
    val passedTick: StateFlow<Long> = _passedTick.asStateFlow()

    fun require(challengeId: String, phone: String) {
        if (challengeId.isBlank()) return
        if (_challenge.value?.challengeId == challengeId) return // 同一挑战不重复弹
        _challenge.value = Challenge(challengeId, phone)
    }

    fun dismiss() { _challenge.value = null }

    fun passed() {
        _challenge.value = null
        _passedTick.value = _passedTick.value + 1
    }
}
