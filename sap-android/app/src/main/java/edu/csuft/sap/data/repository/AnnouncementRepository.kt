package edu.csuft.sap.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.AppAnnouncementDto

/**
 * 课表公告仓库。公告属于全 App 公共内容，因此缓存不按登录账号隔离；
 * 联网时刷新，连接失败时继续展示最近一次成功获取的内容。
 */
class AnnouncementRepository(context: Context, private val api: ApiService) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sap_announcements", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun cached(): List<AppAnnouncementDto> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<AppAnnouncementDto>>(
                json,
                object : TypeToken<List<AppAnnouncementDto>>() {}.type,
            ) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun announcements(): Outcome<List<AppAnnouncementDto>> =
        when (val result = apiData { api.appAnnouncements() }) {
            is Outcome.Success -> {
                prefs.edit().putString(KEY_ITEMS, gson.toJson(result.data)).apply()
                result
            }
            is Outcome.Error -> if (prefs.contains(KEY_ITEMS)) Outcome.Success(cached()) else result
        }

    private companion object {
        const val KEY_ITEMS = "published_items_v1"
    }
}
