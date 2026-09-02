package edu.csuft.sap.data.account

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App 使用模式。 */
enum class AppMode { JW, WEB }

/**
 * 全局会员态 + 使用模式：决定功能门控。
 * - [isMember] 由后端角色判定(角色码 ≤ 3 = 会员/成员及以上；仅游客 = 非会员)，每次启动经 /api/auth/info 重拉刷新。
 * - [mode] 完整 App 能力账号手选的模式(本地持久化)，默认 [AppMode.JW]。
 * - [effectiveMode] 实际生效模式：真实会员或云控 2 级按其选择，其余使用 WEB。
 *   - JW(教务模式)：绑教务学号查课表/成绩，含 课表/成绩/我的。
 *   - WEB(Web模式)：WebView 抓课表，仅 课表/设置。
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

    var mode by mutableStateOf(AppMode.JW)
        private set

    /** 实际生效模式：离线强制 WEB（只看本地课表）；非会员强制 WEB；会员在线按其手选 [mode]。 */
    val effectiveMode: AppMode
        get() = when {
            !ConnectivityState.online -> AppMode.WEB
            hasFullAppFeatures -> mode
            else -> AppMode.WEB
        }
    val isWeb: Boolean get() = effectiveMode == AppMode.WEB
    val isJw: Boolean get() = effectiveMode == AppMode.JW

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString(KEY_MODE, AppMode.JW.name) ?: AppMode.JW.name
        mode = runCatching { AppMode.valueOf(name) }.getOrDefault(AppMode.JW)
        showGrade = p.getBoolean(KEY_SHOW_GRADE, true)
    }

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
        roleCodes = emptyList()
        isMember = false
        appAccessLevel = 0
        accessResolved = false
    }

    /** 会员切换 教务/Web 模式（持久化）。 */
    fun setMode(context: Context, m: AppMode) {
        mode = m
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, m.name).apply()
    }

    /** 设置教务模式底栏是否显示「成绩」（持久化）。 */
    fun setShowGrade(context: Context, v: Boolean) {
        showGrade = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOW_GRADE, v).apply()
    }
}
