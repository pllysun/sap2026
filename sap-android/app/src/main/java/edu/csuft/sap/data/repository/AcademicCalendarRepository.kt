package edu.csuft.sap.data.repository

import android.content.Context
import edu.csuft.sap.data.account.ConnectivityState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.AcademicCalendarDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** 公共校历独立于账号及课表来源缓存；离线也能补齐日期。 */
class AcademicCalendarRepository(context: Context, private val api: ApiService) {
    private val prefs = context.getSharedPreferences("academic_calendar", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Mutex()
    private var lastCheck = 0L

    fun cached(): Map<String, String> = runCatching {
        gson.fromJson<Map<String, String>>(prefs.getString("dates", "{}"), object : TypeToken<Map<String, String>>() {}.type)
    }.getOrNull().orEmpty()

    suspend fun dates(): Map<String, String> = lock.withLock {
        if (!ConnectivityState.online) return@withLock cached()
        if (System.currentTimeMillis() - lastCheck < 60_000L) return@withLock cached()
        when (val result = apiData { api.academicCalendar() }) {
            is Outcome.Success -> {
                val dates = result.data.filter { row ->
                    row.term.matches(Regex("\\d{4}-\\d{4}-[12]")) && runCatching { LocalDate.parse(row.semesterStartDate) }.isSuccess
                }.associate { it.term to it.semesterStartDate }
                prefs.edit().putString("dates", gson.toJson(dates)).apply()
            }
            is Outcome.Error -> Unit // 不因联网失败清掉已缓存校历。
        }
        lastCheck = System.currentTimeMillis()
        cached()
    }
}
