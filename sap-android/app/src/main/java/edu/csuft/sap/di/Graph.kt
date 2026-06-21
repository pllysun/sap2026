package edu.csuft.sap.di

import android.content.Context
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.MemberState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import edu.csuft.sap.data.local.JwCacheStore
import edu.csuft.sap.data.local.TokenStore
import edu.csuft.sap.data.local.UserStore
import edu.csuft.sap.data.remote.ApiClient
import edu.csuft.sap.data.repository.AuthRepository
import edu.csuft.sap.data.repository.JwRepository
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.ScheduleStore
import edu.csuft.sap.ui.theme.ThemeState
import edu.csuft.sap.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 极简手写依赖容器（无 DI 框架），由 SapApp 初始化。 */
object Graph {

    /** 应用级协程作用域：用于不应随某个 ViewModel 销毁而取消的后台任务（如退出登录的服务端通知）。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 应用级 Context：供 ViewModel 等无 Context 处触发上课提醒重排等。 */
    lateinit var appContext: Context
        private set

    lateinit var tokenStore: TokenStore
        private set
    lateinit var userStore: UserStore
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var jwRepository: JwRepository
        private set
    lateinit var accountManager: AccountManager
        private set
    lateinit var scheduleStore: ScheduleStore
        private set
    lateinit var jwCacheStore: JwCacheStore
        private set
    lateinit var updateRepository: UpdateRepository
        private set

    /** 短超时(3s)探针客户端：仅用于进场/前台的「在线/离线」连通性探测，避免用 30s 超时卡住进场。 */
    lateinit var probeApi: edu.csuft.sap.data.remote.ApiService
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        // 先恢复当前会员账号命名空间，再建按账号隔离的本地存储（UserStore/AccountManager）。
        CurrentAccount.load(context.applicationContext)
        tokenStore = TokenStore(context.applicationContext)
        userStore = UserStore(context.applicationContext)
        val api = ApiClient.create(BuildConfig.BASE_URL, { tokenStore.token })
        probeApi = ApiClient.create(BuildConfig.BASE_URL, { tokenStore.token },
            connectTimeoutSec = 3, readTimeoutSec = 3)
        authRepository = AuthRepository(api, tokenStore, userStore)
        jwRepository = JwRepository(api)
        accountManager = AccountManager(context.applicationContext, jwRepository)
        // 会员账号切换（登录/登出）时，让 AccountManager 切到对应账号的教务激活号命名空间。
        // drop(1) 跳过订阅即发的当前值（构造时已读取，无需重复重载）。
        appScope.launch { CurrentAccount.uid.drop(1).collect { accountManager.onUserChanged() } }
        scheduleStore = ScheduleStore(context.applicationContext)
        jwCacheStore = JwCacheStore(context.applicationContext)
        updateRepository = UpdateRepository(api, BuildConfig.BASE_URL) { tokenStore.token }
        Periods.load(context.applicationContext) // 载入自定义节次时间
        ThemeState.load(context.applicationContext) // 载入自定义辅色（主题色）
        MemberState.load(context.applicationContext) // 载入教务/Web 模式选择
    }
}
