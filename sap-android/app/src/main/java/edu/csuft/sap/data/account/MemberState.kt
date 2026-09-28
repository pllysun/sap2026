package edu.csuft.sap.data.account

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App 使用模式：教务课表、网页登录课表、班级公共课表。 */
enum class AppMode { JW, WEB, CLASS }

/**
 * 全局会员态 + 使用模式：决定功能门控。
 * - [isMember] 由后端角色判定(角色码 ≤ 3 = 会员/成员及以上；仅游客 = 非会员)，每次启动经 /api/auth/info 重拉刷新。
 * - [mode] 按登录账号保存手选模式；没有历史选择的新账号统一默认 [AppMode.CLASS]。
 * - [effectiveMode] 实际生效模式：在线按角色及云控；离线允许切换 Web 与班级缓存。
 *   - JW(教务模式)：绑教务学号查课表/成绩，含 课表/成绩/我的。
 *   - WEB(Web模式)：WebView 抓课表，仅 课表/设置。
 *   - CLASS(班级模式)：按学期/学院/专业/班级读取公共课表，仅 课表/设置。
 */
object MemberState {
    private const val PREFS = "sap_member"
    private const val KEY_MODE = "app_mode"
    private const val KEY_SHOW_GRADE = "show_grade"

    var isMember by mutableStateOf(false)
        private set

    /** 后端下发的有效 App 能力等级：真实会员恒为 2；游客由课表云控决定 0/1/2。 */
    var appAccessLevel by mutableStateOf(0)
        private set

    /** 本进程是否已从登录/用户接口取得真实角色与云控等级，避免冷启动乐观进首页时误判身份。 */
    var accessResolved by mutableStateOf(false)
        private set

    val hasFullAppFeatures: Boolean get() = isMember || appAccessLevel >= 2

    /**
     * 教务模式下是否在底栏显示「成绩」菜单（在「我的 → 设置」里开关，默认显示）。
     * 关闭后教务模式底栏仅「课表 / 我的」两项。成绩属教务模式，故仅教务模式生效。
     */
    var showGrade by mutableStateOf(true)
        private set

    /** 当前用户角色码列表（0超管/1会长/2管理/3成员/4游客）；用于身份展示兜底。 */
    var roleCodes by mutableStateOf<List<Int>>(emptyList())
        private set

    var mode by mutableStateOf(AppMode.CLASS)
        private set

    var pendingAcademicOwner by mutableStateOf<String?>(null)
        private set

    /** 先取得授权再提交模式；取消协议不改变之前正在使用的课表。 */
    fun requestMode(context: Context, selected: AppMode) {
        when (modeRequestDecision(selected, availableModes, PrivacyConsents.hasAcademic())) {
            ModeRequestDecision.DENIED -> {
                // 权限/网络状态可能在面板打开后变化，不能悄悄吞掉用户点击。
                val message = if (!ConnectivityState.online) "当前离线，请联网后切换教务课表"
                    else "当前账号未获得教务课表权限，请刷新登录状态或联系管理员"
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
            ModeRequestDecision.CONSENT -> pendingAcademicOwner = CurrentAccount.key
            ModeRequestDecision.APPLY -> setMode(context, selected)
        }
    }

    fun cancelAcademicRequest(context: Context, owner: String) {
        if (CurrentAccount.key != owner) return
        pendingAcademicOwner = null
        if (mode == AppMode.JW && !PrivacyConsents.hasAcademic()) setMode(context, AppMode.WEB)
    }

    /**
     * 实际生效模式：离线仍展示本地 Web/班级缓存；会员按选择生效；
     * 会员或云控完整能力可用教务模式；Web/班级始终开放。
     */
    val effectiveMode: AppMode
        get() {
            val resolved = resolveScheduleMode(ConnectivityState.online, mode, isMember, appAccessLevel)
            return if (resolved == AppMode.JW && !PrivacyConsents.hasAcademic()) AppMode.WEB else resolved
        }
    val isWeb: Boolean get() = effectiveMode == AppMode.WEB
    val isJw: Boolean get() = effectiveMode == AppMode.JW
    val isClass: Boolean get() = effectiveMode == AppMode.CLASS

    /** 当前账号在模式选择器可见的模式；角色文案不在客户端展示。 */
    val availableModes: List<AppMode>
        get() = when {
            !ConnectivityState.online -> listOf(AppMode.WEB, AppMode.CLASS)
            hasFullAppFeatures -> listOf(AppMode.JW, AppMode.WEB, AppMode.CLASS)
            else -> listOf(AppMode.WEB, AppMode.CLASS)
        }

    fun load(context: Context) {
        pendingAcademicOwner = null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val accounts = context.getSharedPreferences("sap_account", Context.MODE_PRIVATE)
        // 旧版只存一份全局模式。迁移现有账号各自的来源，避免首次登录的新账号继承他人的模式。
        if (p.contains(KEY_MODE)) {
            val legacy = p.getString(KEY_MODE, null)
            val edit = p.edit()
            accounts.all.forEach { (key, value) ->
                if (key.startsWith(AccountManager.KEY_ACTIVE) && value is String) {
                    val member = key.removePrefix(AccountManager.KEY_ACTIVE)
                    val modeKey = modeKey(member)
                    if (!p.contains(modeKey)) {
                        val restored = restoredScheduleMode(null, value,
                            legacy.takeIf { member == CurrentAccount.key })
                        edit.putString(modeKey, restored.name)
                    }
                }
            }
            edit.remove(KEY_MODE).apply()
        }
        mode = restoredScheduleMode(
            p.getString(modeKey(), null),
            accounts.getString("${AccountManager.KEY_ACTIVE}${CurrentAccount.key}", null),
        )
        p.edit().putString(modeKey(), mode.name).apply()
        showGrade = p.getBoolean(KEY_SHOW_GRADE, true)
    }

    /**
     * 兼容旧安装：令牌仍有效，但旧版本没有写入 current_uid。只在服务端已核实学号后恢复命名空间。
     * 匿名命名空间中的手选模式可继承给该账号；教务隐私授权不能继承，仍需本人确认。
     */
    fun restoreVerifiedOwner(context: Context, studentId: String?): Boolean {
        val verified = studentId?.trim()?.takeIf(String::isNotEmpty) ?: return false
        if (CurrentAccount.key == verified) return false
        val previous = CurrentAccount.key
        if (previous == "_") {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val anonymousMode = prefs.getString(modeKey("_"), null)
            if (!prefs.contains(modeKey(verified)) && anonymousMode != null) {
                prefs.edit().putString(modeKey(verified), anonymousMode).apply()
            }
        }
        CurrentAccount.set(verified)
        load(context)
        return true
    }

    private fun modeKey(member: String = CurrentAccount.key) = "${KEY_MODE}_$member"

    /** 由角色列表判定会员（含成员/管理/会长/超管，即任一角色码 ≤ 3）。 */
    fun setAccess(roles: List<Int>, level: Int) {
        this.roleCodes = roles
        isMember = roles.any { it <= 3 }
        appAccessLevel = if (isMember) 2 else level.coerceIn(0, 2)
        accessResolved = true
    }

    /** 兼容只刷新角色的旧调用；真实会员仍自动拥有完整能力。 */
    fun setRoles(roles: List<Int>) = setAccess(roles, appAccessLevel)

    /** 清除登录凭证时同步清掉上一个账号的能力快照，等待下次登录重新解析。 */
    fun clearAccess() {
        pendingAcademicOwner = null
        roleCodes = emptyList()
        isMember = false
        appAccessLevel = 0
        accessResolved = false
    }

    /** 切换课表模式，按当前登录账号持久化。 */
    fun setMode(context: Context, m: AppMode) {
        pendingAcademicOwner = null
        mode = m
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(modeKey(), m.name).apply()
    }

    /** 设置教务模式底栏是否显示「成绩」（持久化）。 */
    fun setShowGrade(context: Context, v: Boolean) {
        showGrade = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOW_GRADE, v).apply()
    }
}

internal enum class ModeRequestDecision { DENIED, CONSENT, APPLY }
internal fun modeRequestDecision(selected: AppMode, available: List<AppMode>, academicConsent: Boolean) = when {
    selected !in available -> ModeRequestDecision.DENIED
    selected == AppMode.JW && !academicConsent -> ModeRequestDecision.CONSENT
    else -> ModeRequestDecision.APPLY
}

/** 有个人选择时恢复；没有历史来源的新账号统一使用班级课表，不受旧全局模式影响。 */
internal fun restoredScheduleMode(saved: String?, source: String?, legacy: String? = null): AppMode {
    AppMode.entries.firstOrNull { it.name == saved }?.let { return it }
    if (source.isNullOrBlank()) return AppMode.CLASS
    AppMode.entries.firstOrNull { it.name == legacy }?.let { return it }
    return when {
        AccountManager.isClass(source) -> AppMode.CLASS
        AccountManager.isLocal(source) -> AppMode.WEB
        else -> AppMode.JW
    }
}

/** 冷启动身份未解析也不阻断离线班级缓存；恢复在线后重新遵守角色和云控。 */
internal fun resolveScheduleMode(online: Boolean, selected: AppMode, member: Boolean, level: Int): AppMode = when {
    !online && selected == AppMode.CLASS -> AppMode.CLASS
    !online -> AppMode.WEB
    member || level >= 2 -> selected
    selected == AppMode.CLASS -> AppMode.CLASS
    else -> AppMode.WEB
}
