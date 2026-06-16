package edu.csuft.sap.di

import android.content.Context
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.local.JwCacheStore
import edu.csuft.sap.data.local.TokenStore
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

    lateinit var tokenStore: TokenStore
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

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        val api = ApiClient.create(BuildConfig.BASE_URL) { tokenStore.token }
        authRepository = AuthRepository(api, tokenStore)
        jwRepository = JwRepository(api)
        accountManager = AccountManager(context.applicationContext, jwRepository)
        scheduleStore = ScheduleStore(context.applicationContext)
        jwCacheStore = JwCacheStore(context.applicationContext)
        updateRepository = UpdateRepository(api, BuildConfig.BASE_URL) { tokenStore.token }
        Periods.load(context.applicationContext) // 载入自定义节次时间
        ThemeState.load(context.applicationContext) // 载入自定义辅色（主题色）
        MemberState.load(context.applicationContext) // 载入教务/Web 模式选择
    }
}
