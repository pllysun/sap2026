package edu.csuft.sap.data.repository

import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.local.TokenStore
import edu.csuft.sap.data.local.UserStore
import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.apiUnit
import edu.csuft.sap.data.remote.dto.LoginRequest
import edu.csuft.sap.data.remote.dto.LoginData
import edu.csuft.sap.data.remote.dto.MeData
import edu.csuft.sap.data.remote.dto.UpdateProfileRequest
import edu.csuft.sap.data.remote.dto.UserDto
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val userStore: UserStore,
) {

    fun hasLocalToken(): Boolean = tokenStore.hasToken()

    /** 验证身份，协议确认前不保存登录凭证。 */
    suspend fun appLogin(studentId: String, password: String): Outcome<LoginData> {
        return when (val r = apiData { api.appLogin(LoginRequest(studentId.trim(), password)) }) {
            is Outcome.Success -> {
                val token = r.data.token
                if (token.isNullOrBlank()) {
                    Outcome.Error("登录返回异常：缺少凭证")
                } else {
                    Outcome.Success(r.data)
                }
            }
            is Outcome.Error -> r
        }
    }

    fun completeAppLogin(data: LoginData, studentId: String) {
        require(!data.token.isNullOrBlank())
        edu.csuft.sap.data.account.PrivacyConsents.accept(
            data.user?.studentId?.takeIf { it.isNotBlank() } ?: studentId.trim(),
            data.roles.any { it <= 3 } || data.appAccessLevel >= 2,
        )
        CurrentAccount.set(data.user?.studentId ?: studentId.trim())
        tokenStore.saveConfirmed(data.token!!)
        MemberState.setAccess(data.roles, data.appAccessLevel)
    }

    /** 用本地 token 拉当前用户（启动免密校验）；顺带刷新会员态。 */
    suspend fun me(): Outcome<UserDto> {
        val owner = CurrentAccount.key
        val token = tokenStore.token
        val result = apiData { api.me() }
        if (CurrentAccount.key != owner || tokenStore.token != token) return Outcome.Error("登录状态已变化")
        return when (val r = result) {
        is Outcome.Success -> {
            MemberState.setAccess(r.data.roles, r.data.appAccessLevel)
            Outcome.Success(r.data.user ?: UserDto())
        }
        is Outcome.Error -> r
        }
    }

    /** 本地缓存的用户信息（含头像/身份/角色），用于即时展示与离线兜底。 */
    fun cachedMe(): MeData? = userStore.cached()

    /**
     * 同步当前用户信息（省流量）：先调轻量接口 /info/light 拿最新文本 + 服务端 updatedAt（不含头像），
     * 仅当服务端 updatedAt 比本地缓存更新（头像/资料变过）才去 /info 拉新头像 URL，否则沿用缓存头像
     * （Coil 命中磁盘缓存不重复下载）。任一步成功都刷新会员态并更新本地缓存；网络失败回退缓存（离线）。
     */
    suspend fun syncUser(): Outcome<MeData> {
        val account = CurrentAccount.key
        val cached = userStore.cached()
        return when (val r = apiData { api.meLight() }) {
            is Outcome.Success -> {
                if (CurrentAccount.key != account) return Outcome.Error("账号已切换")
                val light = r.data
                MemberState.setAccess(light.roles, light.appAccessLevel)
                if (needsFullProfile(cached, light)) {
                    when (val full = apiData { api.me() }) {
                        is Outcome.Success -> {
                            if (CurrentAccount.key != account) return Outcome.Error("账号已切换")
                            MemberState.setAccess(full.data.roles, full.data.appAccessLevel)
                            userStore.save(full.data)
                            Outcome.Success(full.data)
                        }
                        // 完整接口失败：用轻量文本 + 旧缓存头像兜底（不丢头像）
                        is Outcome.Error -> {
                            if (CurrentAccount.key != account) return Outcome.Error("账号已切换")
                            mergeProfileWithoutAvatar(cached, light)
                                .also { userStore.save(it) }.let { Outcome.Success(it) }
                        }
                    }
                } else {
                    // 头像没变：只更新文本/身份/角色，沿用缓存头像（不请求头像 → 省流量）
                    mergeProfileWithoutAvatar(cached, light)
                        .also { userStore.save(it) }.let { Outcome.Success(it) }
                }
            }
            // 网络失败：有缓存则离线展示，否则透传错误
            is Outcome.Error -> if (cached != null) Outcome.Success(cached) else r
        }
    }

    /** 立即清除本地登录凭证 + 用户信息缓存（同步，不走网络）。 */
    fun clearLocalToken() {
        userStore.clear()        // 先清当前账号缓存（依赖 CurrentAccount.key），再切空账号
        tokenStore.clear()
        CurrentAccount.set(null)
        MemberState.clearAccess()
    }

    /** 通知服务端登出，尽力而为（可能耗时/失败，调用方不应阻塞 UI 等它）。 */
    suspend fun logoutRemote() {
        runCatching { api.logout() }
    }

    /** 修改本人资料（网名/性别/头像）。复用用户端 /api/auth/profile。 */
    suspend fun updateProfile(nickname: String?, gender: Int?, avatar: String?): Outcome<Unit> =
        apiUnit { api.updateProfile(UpdateProfileRequest(nickname, gender, avatar)) }

    /** 上传头像图片字节 → 返回 COS url。复用用户端 /api/file/upload。 */
    suspend fun uploadAvatar(bytes: ByteArray, filename: String): Outcome<String> {
        val body = bytes.toRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", filename, body)
        return when (val r = apiData { api.uploadFile(part) }) {
            is Outcome.Success -> r.data.url?.takeIf { it.isNotBlank() }
                ?.let { Outcome.Success(it) } ?: Outcome.Error("上传失败：未返回地址")
            is Outcome.Error -> r
        }
    }
}
