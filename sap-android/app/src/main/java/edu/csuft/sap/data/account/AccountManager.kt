package edu.csuft.sap.data.account

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.repository.JwRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 已绑定教务学号 + 服务端备注名（本地镜像）+ 上次同步时间。isLocal=本地网页课表。 */
data class BoundAccount(
    val account: String,
    val nickname: String? = null,
    val lastSyncAt: String? = null,
    val isLocal: Boolean = false,
) {
    val display: String get() = when {
        isLocal -> nickname ?: "网页课表"
        nickname.isNullOrBlank() -> account
        else -> "$nickname（$account）"
    }
}

/**
 * 教务账号上下文：维护已绑定学号列表（后端为准）+ 当前激活学号 + 备注名。
 * [active] 用 StateFlow 暴露，切号即全 App 数据切换；学号选择保存在本地，备注名保存到服务端。
 */
class AccountManager(context: Context, private val jw: JwRepository) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sap_account", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 激活学号选择 + 上次教务号按「当前会员账号」隔离：key 带 CurrentAccount.key 后缀，
    // 多会员账号互不覆盖（解决“切回上一个会员账号时教务选择丢失”）。
    private var ns = CurrentAccount.key // 上次已加载的命名空间，仅用于检测账号切换
    // key 直接按当前账号算（不依赖 ns 缓存），避免登录瞬间 ns 未及时切换导致写错命名空间
    private fun keyActive() = "$KEY_ACTIVE${CurrentAccount.key}"
    private fun keyLastJw() = "$KEY_LAST_JW${CurrentAccount.key}"
    private fun keyLastClass() = "$KEY_LAST_CLASS${CurrentAccount.key}"

    private val _accounts = MutableStateFlow(emptyList<BoundAccount>())
    val accounts: StateFlow<List<BoundAccount>> = _accounts.asStateFlow()

    private val _active = MutableStateFlow(prefs.getString(keyActive(), null))
    val active: StateFlow<String?> = _active.asStateFlow()

    // 会员账号切换时，激活教务号可能恰好仍是同一个学号；单靠 [active] 无法通知缓存页重载。
    private val _contextVersion = MutableStateFlow(0L)
    val contextVersion: StateFlow<Long> = _contextVersion.asStateFlow()

    val activeAccount: String? get() = _active.value

    init { mirrorActive(_active.value) } // 冷启动即把当前激活账号写入无后缀镜像 key

    /**
     * 把「当前激活教务号」镜像到无后缀的稳定 key [KEY_ACTIVE_MIRROR]，供 [edu.csuft.sap.widget.WidgetRepository]
     * （桌面小组件进程 / 上课提醒闹钟）读取。真正的 active 按会员账号隔离存于 active_account_<会员号>，
     * 跨进程无法拼出该后缀，故由本类（唯一写入方）始终维护一份与当前 active 同步的镜像。
     */
    private fun mirrorActive(account: String?) {
        prefs.edit().apply {
            if (account == null) remove(KEY_ACTIVE_MIRROR) else putString(KEY_ACTIVE_MIRROR, account)
        }.apply()
    }

    /**
     * 会员账号切换时调用（登录/登出/冷启动恢复）：切到新账号的命名空间，
     * 重载其上次激活的教务号并清空账号列表（触发重新拉取）。同账号则忽略。
     */
    fun onUserChanged() {
        val next = CurrentAccount.key
        if (next == ns) return
        ns = next
        _accounts.value = emptyList()
        _active.value = prefs.getString(keyActive(), null)
        mirrorActive(_active.value)
        _contextVersion.value += 1
    }

    /**
     * 拉后端已绑定学号，合并备注的本地兼容镜像 + 末尾追加「网页课表」源，校正 active。
     * 返回是否存在任何教务绑定（不含本地源）。非会员无绑定时 active 落到网页源。
     */
    suspend fun refresh(): Boolean {
        val nick = loadNicknames()
        return when (val r = jw.accounts()) {
            is Outcome.Success -> {
                // 服务端备注为长期真源；本地值仅兼容升级前已保存的备注。
                val bound = r.data.map {
                    BoundAccount(
                        it.account,
                        it.remark?.trim()?.takeIf(String::isNotBlank) ?: nick[it.account],
                        it.lastSyncAt,
                    )
                }
                val full = bound + webviewEntry
                _accounts.value = full
                val cur = _active.value
                // 班级课表账号键由班级选择动态生成，不在教务绑定列表中；保留它才能离线切回上次班级缓存。
                if (full.none { it.account == cur } && !isClass(cur)) {
                    setActive((bound.firstOrNull()?.account) ?: WEBVIEW_ACCOUNT)
                }
                bound.isNotEmpty()
            }
            is Outcome.Error -> {
                // 拉取失败：至少保证本地网页源可用
                if (_accounts.value.none { it.isLocal }) _accounts.value = _accounts.value + webviewEntry
                if (_active.value == null) setActive(WEBVIEW_ACCOUNT)
                _accounts.value.any { !it.isLocal }
            }
        }
    }

    /** 切到本地网页课表源。 */
    fun useWebview() = setActive(WEBVIEW_ACCOUNT)

    /** 切到指定班级的本地缓存槽；首次使用时槽为空，选择器成功下载后写入。 */
    fun useClass(selectionKey: String? = null) {
        val key = selectionKey?.takeIf { it.isNotBlank() }
            ?: prefs.getString(keyLastClass(), null)?.removePrefix(CLASS_ACCOUNT_PREFIX)
            ?: "default"
        setActive(CLASS_ACCOUNT_PREFIX + key)
    }

    /** 切换回班级模式时恢复最近一次选择，缓存存在时可直接离线显示。 */
    fun activateClassAccount(): Boolean {
        val saved = prefs.getString(keyLastClass(), null)
        if (saved.isNullOrBlank()) {
            useClass("default")
            return false
        }
        setActive(saved)
        return true
    }

    /** 上次使用过的教务账号，仅用于云控降级时从对应的本地缓存恢复课表，不触发网络请求。 */
    fun lastJwAccount(): String? = prefs.getString(keyLastJw(), null)

    /**
     * 切回教务模式时调用：激活上次用过的教务账号（否则第一个教务账号）；
     * 无任何教务账号时置空，由界面提示去「我的」绑定。返回是否激活了教务账号。
     */
    fun activateJwAccount(): Boolean {
        val jwAccts = _accounts.value.filter { !it.isLocal }.map { it.account }
        val last = prefs.getString(keyLastJw(), null)
        val target = when {
            last != null && jwAccts.contains(last) -> last
            jwAccts.isNotEmpty() -> jwAccts.first()
            last != null -> last // accounts 列表可能尚未拉取，回退到上次用过的教务账号
            else -> null
        }
        setActive(target)
        return target != null
    }

    fun setActive(account: String?) {
        if (account == _active.value) return
        _active.value = account
        prefs.edit().apply {
            if (account == null) remove(keyActive()) else putString(keyActive(), account)
            // 记住最近使用的教务账号（非本地），供 Web→教务 切回时自动激活
            if (account != null && !isLocal(account) && !isClass(account)) putString(keyLastJw(), account)
            if (account != null && isClass(account)) putString(keyLastClass(), account)
        }.apply()
        mirrorActive(account) // 同步无后缀镜像，供小组件 / 上课提醒读取当前激活账号
    }

    /** 先落服务端，成功后再更新本地镜像，避免 UI 显示“已保存”但重启/换设备后丢失。 */
    suspend fun setNickname(account: String, nickname: String?): Outcome<Unit> {
        val clean = nickname?.trim()?.takeIf(String::isNotBlank)
        val result = jw.updateRemark(account, clean)
        if (result !is Outcome.Success) return result
        val map = loadNicknames().toMutableMap()
        if (clean == null) map.remove(account) else map[account] = clean
        prefs.edit().putString(keyNick(), gson.toJson(map)).apply()
        _accounts.value = _accounts.value.map {
            if (it.account == account) it.copy(nickname = map[account]) else it
        }
        return result
    }

    private fun loadNicknames(): Map<String, String> {
        val json = prefs.getString(keyNick(), null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun keyNick() = "$KEY_NICK${CurrentAccount.key}"

    companion object {
        /** 本地「网页课表」源的账号键（非教务绑定，数据来自 WebView 端上导入）。 */
        const val WEBVIEW_ACCOUNT = "__webview__"
        fun isLocal(account: String?) = account == WEBVIEW_ACCOUNT
        /** 本地班级课表缓存前缀；后缀是学院/年级/专业/班级的稳定摘要（跨学期复用）。 */
        const val CLASS_ACCOUNT_PREFIX = "__class__:"
        fun isClass(account: String?) = account?.startsWith(CLASS_ACCOUNT_PREFIX) == true
        fun isLocalOrClass(account: String?) = isLocal(account) || isClass(account)
        private const val KEY_ACTIVE = "active_account_" // 实际 key 拼 CurrentAccount.key 后缀（按会员账号隔离）
        // 无后缀镜像 key：始终 = 当前激活教务号，供 WidgetRepository（小组件 / 上课提醒，跨进程取不到 CurrentAccount 后缀）读取
        private const val KEY_ACTIVE_MIRROR = "active_account"
        private const val KEY_NICK = "nicknames_"         // 备注按会员账号 + 教务号隔离
        private const val KEY_LAST_JW = "last_jw_"        // 同上，按会员账号隔离
        private const val KEY_LAST_CLASS = "last_class_"  // 最近班级缓存槽，按会员账号隔离
    }

    private val webviewEntry = BoundAccount(WEBVIEW_ACCOUNT, "网页课表", isLocal = true)
}
