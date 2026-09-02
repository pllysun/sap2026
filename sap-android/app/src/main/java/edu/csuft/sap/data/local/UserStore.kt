package edu.csuft.sap.data.local

import android.content.Context
import com.google.gson.Gson
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.remote.dto.MeData

/**
 * 当前用户信息本地缓存（含头像 URL + identities + roles + updatedAt）。
 *
 * 省流量策略（见 [edu.csuft.sap.data.repository.AuthRepository.syncUser]）：
 * - 每次进资料页调轻量接口 /api/auth/info/light 拿最新文本信息 + 服务端 updatedAt（不含头像）。
 * - 仅当 服务端 updatedAt > 本地缓存 updatedAt（即头像/资料变过）才去调 /api/auth/info 拿新头像 URL；
 *   否则直接用本地缓存的头像 URL（Coil 再命中磁盘缓存 → 不重复下载，省 CDN 流量）。
 * - 这里始终缓存"带头像 URL 的完整 MeData"，离线也能即时展示。
 */
class UserStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sap_user", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 缓存按当前会员账号隔离：key=me_<studentId>，切号互不串（解决“切游客号还显示会员信息”）。
    private fun key() = "$KEY_ME${CurrentAccount.key}"

    /** 读取当前账号缓存的用户信息（含头像 URL / identities / roles / updatedAt）；无缓存返回 null。 */
    fun cached(): MeData? {
        val json = prefs.getString(key(), null) ?: return null
        return runCatching { gson.fromJson(json, MeData::class.java) }.getOrNull()
    }

    /** 覆盖写入当前账号的完整用户信息。 */
    fun save(me: MeData) {
        prefs.edit().putString(key(), gson.toJson(me)).apply()
    }

    /** 清除当前账号的用户缓存（登出/被踢时）。 */
    fun clear() {
        prefs.edit().remove(key()).apply()
    }

    private companion object {
        const val KEY_ME = "me_"
    }
}
