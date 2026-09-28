package edu.csuft.sap.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.ClassOptionDto
import edu.csuft.sap.data.remote.dto.ClassScheduleTermDto
import edu.csuft.sap.data.remote.dto.ScheduleData

/** 班级课表只读接口；缓存由 ScheduleStore 按班级选择键隔离。 */
class ClassScheduleRepository(private val api: ApiService, context: Context) {
    suspend fun sync(request: edu.csuft.sap.data.remote.dto.ClassSyncRequest) =
        apiData { api.syncClassSchedules(request) }
    private val prefs = context.getSharedPreferences("class_catalog_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private inline fun <reified T> read(key: String): T? = runCatching {
        gson.fromJson<T>(prefs.getString(key, null), object : TypeToken<T>() {}.type)
    }.getOrNull()

    fun cachedTerms(): List<ClassScheduleTermDto> = read<List<ClassScheduleTermDto>>("terms").orEmpty()
    fun cachedClasses(term: String): List<ClassOptionDto> = read<List<ClassOptionDto>>("classes_$term").orEmpty()
    private fun fresh(key: String) = System.currentTimeMillis() - prefs.getLong("time_$key", 0) < 24 * 60 * 60 * 1000L
    private fun save(key: String, data: Any) {
        prefs.edit().putString(key, gson.toJson(data)).putLong("time_$key", System.currentTimeMillis()).apply()
    }
    suspend fun terms(force: Boolean = false): Outcome<List<ClassScheduleTermDto>> {
        val cached = cachedTerms()
        if (!ConnectivityState.online) return Outcome.Success(cached)
        if (!force && cached.isNotEmpty() && fresh("terms")) return Outcome.Success(cached)
        return when (val result = apiData { api.classScheduleTerms() }) {
            is Outcome.Success -> result.also { save("terms", it.data) }
            is Outcome.Error -> if (!force && cached.isNotEmpty()) Outcome.Success(cached) else result
        }
    }

    suspend fun classes(term: String, college: String?, major: String?, grade: String? = null, force: Boolean = false): Outcome<List<ClassOptionDto>> {
        if (!ConnectivityState.online) return Outcome.Success(cachedClasses(term).filter {
            (college == null || it.college == college) && (major == null || it.major == major) &&
                (grade == null || it.grade == grade)
        })
        // 全量班级目录按学期缓存；各级选择在端内过滤，无需逐级访问服务器。
        if (college != null || major != null || grade != null) return apiData { api.classScheduleClasses(term, college, major, grade) }
        val key = "classes_$term"
        val cached = cachedClasses(term)
        if (!force && cached.isNotEmpty() && fresh(key)) return Outcome.Success(cached)
        return when (val result = apiData { api.classScheduleClasses(term, null, null, null) }) {
            is Outcome.Success -> result.also { save(key, it.data) }
            is Outcome.Error -> if (!force && cached.isNotEmpty()) Outcome.Success(cached) else result
        }
    }

    suspend fun schedule(term: String, college: String, major: String, className: String, grade: String? = null): Outcome<ScheduleData> =
        if (!ConnectivityState.online) Outcome.Error("当前离线，请从已下载课表中选择；下载新课表需联网")
        else apiData { api.classSchedule(term, college, major, grade, className) }
}
