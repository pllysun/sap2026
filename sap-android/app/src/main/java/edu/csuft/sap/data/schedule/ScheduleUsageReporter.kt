package edu.csuft.sap.data.schedule

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.AppMode
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.local.TokenStore
import edu.csuft.sap.data.remote.ApiClient
import edu.csuft.sap.data.remote.dto.ScheduleUsageEvent
import edu.csuft.sap.data.remote.dto.ScheduleUsageReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** 仅上报日期和模式，不上传课程、教务账号、位置或设备标识；失败不影响课表。 */
class ScheduleUsageReporter(context: Context, private val tokens: TokenStore, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("sap_schedule_usage", Context.MODE_PRIVATE)
    private val json = Gson()
    private val lock = Any()
    private val sending = Mutex()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val buffer = ScheduleUsageBuffer(runCatching {
        json.fromJson<List<UsageStamp>>(prefs.getString("events", "[]"), object : TypeToken<List<UsageStamp>>() {}.type)
    }.getOrNull().orEmpty())

    fun viewed(mode: AppMode) = enqueue(mode.name, "VIEW")
    fun webImported(owner: String) {
        // 导入开始后切号，不能把前一个账号的结果计给新账号。
        if (CurrentAccount.key == owner) enqueue("WEB", "SYNC")
    }
    private fun enqueue(mode: String, event: String) {
        val owner = CurrentAccount.key
        if (owner == "_") return
        synchronized(lock) {
            val today = LocalDate.now(zone)
            buffer.prune(today)
            buffer.add(UsageStamp(owner, today.toString(), mode, event))
            persist()
        }
        flush()
    }
    private fun persist() { prefs.edit().putString("events", json.toJson(buffer.records())).apply() }

    fun flush() {
        scope.launch {
            sending.withLock {
                val owner = CurrentAccount.key
                val token = tokens.token?.takeIf { it.isNotBlank() } ?: return@withLock
                if (owner == "_" || !ConnectivityState.online) return@withLock
                // 冻结本次身份：异步请求不能在切号后读到另一个账号的新 token。
                val api = ApiClient.create(BuildConfig.BASE_URL, { token }, connectTimeoutSec = 5, readTimeoutSec = 8)
                var identityVerified = false
                while (CurrentAccount.key == owner && tokens.token == token && ConnectivityState.online) {
                    val batch = synchronized(lock) {
                        buffer.prune(LocalDate.now(zone)); persist(); buffer.pending(owner)
                    }
                    if (batch.isEmpty()) break
                    val accepted = try {
                        // 登录时账号命名空间和 token 分两步写入；用同一个冻结 token 核验归属，
                        // 防止恰好切号时把新账号的待发记录提交到旧账号。每个有待发数据的会话只查一次。
                        if (!identityVerified) {
                            val identity = api.meLight()
                            if (identity.code != 200 || identity.data?.user?.studentId?.trim() != owner) break
                            identityVerified = true
                        }
                        api.scheduleUsage(ScheduleUsageReport(batch.map { ScheduleUsageEvent(it.date, it.mode, it.event) })).code == 200
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { false }
                    if (!accepted) break // 保留待发记录，下次进前台或恢复网络时重试。
                    synchronized(lock) { buffer.acknowledge(batch); persist() }
                }
            }
        }
    }
}
