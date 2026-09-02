package edu.csuft.sap.data.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前登录的「会员账号」标识（= 登录用的学号 studentId），用于把本地状态**按账号隔离**：
 * - 教务激活学号选择（[AccountManager]）——每个会员账号各自记住上次选的教务号，互不覆盖。
 * - 用户信息缓存（[edu.csuft.sap.data.local.UserStore]）——每个会员账号一份，切号不串。
 *
 * 一个 App 可先后登录多个会员账号（登出再登录切换），故这些本地状态必须跟着会员账号走，
 * 而不是全局一份。登录时 [set] 写入并持久化，登出时 [set]`(null)`。冷启动 [load] 恢复。
 */
object CurrentAccount {
    private const val PREFS = "sap_session"
    private const val KEY_UID = "current_uid"

    private var prefs: android.content.SharedPreferences? = null

    private val _uid = MutableStateFlow<String?>(null)
    /** 当前会员账号标识（studentId）；null=未登录/未知。变更时通知监听者重载按账号隔离的状态。 */
    val uid: StateFlow<String?> = _uid.asStateFlow()

    /** 用于拼本地存储 key 的命名空间片段（未登录回退 "_"，保证 key 合法且不与真实账号冲突）。 */
    val key: String get() = _uid.value?.takeIf { it.isNotBlank() } ?: "_"

    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _uid.value = p.getString(KEY_UID, null)
    }

    /** 登录成功设当前账号；登出设 null。持久化以便冷启动恢复。 */
    fun set(uid: String?) {
        val v = uid?.trim()?.takeIf { it.isNotBlank() }
        if (v == _uid.value) return
        prefs?.edit()?.apply { if (v == null) remove(KEY_UID) else putString(KEY_UID, v) }?.apply()
        _uid.value = v
    }
}
