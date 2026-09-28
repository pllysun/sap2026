package edu.csuft.sap.data.account

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 按 App 账号保存协议版本，不保存登录密码。 */
object PrivacyConsents {
    private const val VERSION = 1
    private var prefs: SharedPreferences? = null
    private var revision by mutableIntStateOf(0)
    fun load(context: Context) { prefs = context.getSharedPreferences("privacy_consents", Context.MODE_PRIVATE) }
    fun hasAcademic(account: String = CurrentAccount.key): Boolean {
        revision // Compose 订阅同意状态变化。
        return account != "_" && prefs?.getInt("academic_$account", 0) == VERSION
    }
    fun accept(account: String, academic: Boolean) {
        require(account.isNotBlank() && account != "_")
        val editor = checkNotNull(prefs).edit().putInt("basic_$account", VERSION)
        if (academic) editor.putInt("academic_$account", VERSION)
        check(editor.commit()) { "无法保存协议确认，请重试" }
        revision++
    }
}
