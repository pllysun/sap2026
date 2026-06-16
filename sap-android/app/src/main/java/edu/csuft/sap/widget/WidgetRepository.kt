package edu.csuft.sap.widget

import android.content.Context
import com.google.gson.Gson
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.ProfileKind
import edu.csuft.sap.data.schedule.ScheduleRoot
import edu.csuft.sap.data.schedule.WeekUtil
import edu.csuft.sap.ui.theme.colorIndexOf
import java.time.LocalDate
import java.time.LocalTime

/** 小组件用的精简课程（无 Compose）。node 为节 1-12。 */
data class WidgetCourse(
    val day: Int,
    val startNode: Int,
    val endNode: Int,
    val name: String,
    val location: String,
    val teacher: String,
    val weeks: List<Int>,
    val colorIndex: Int,
)

/** 当前激活学号·激活课表的全量课程 + 当前周。 */
data class WidgetData(
    val bound: Boolean,
    val profileName: String,
    val currentWeek: Int?,
    val courses: List<WidgetCourse>,
)

/**
 * 桌面小组件的数据来源：直接读 App 写在 SharedPreferences 里的本地缓存（无需登录/网络）。
 * 复用 [ScheduleRoot] 等领域模型与 [WeekUtil]/[Periods]。
 * 注意：prefs 名与 key 必须与 ScheduleStore / AccountManager 保持一致。
 */
object WidgetRepository {

    private const val ACCOUNT_PREFS = "sap_account"
    private const val ACCOUNT_KEY = "active_account"
    private const val SCHEDULE_PREFS = "sap_schedule"
    private const val SCHEDULE_KEY = "root_v3"

    private val gson = Gson()

    fun load(context: Context): WidgetData {
        Periods.load(context) // 小组件进程也载入自定义节次时间
        val account = context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)
            .getString(ACCOUNT_KEY, null)
            ?: return WidgetData(false, "", null, emptyList())

        val json = context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE)
            .getString(SCHEDULE_KEY, null)
            ?: return WidgetData(true, "", null, emptyList())

        val root = try {
            gson.fromJson(json, ScheduleRoot::class.java)
        } catch (_: Exception) {
            null
        } ?: return WidgetData(true, "", null, emptyList())

        val data = root.accounts[account] ?: return WidgetData(true, "", null, emptyList())
        val profile = data.profiles.firstOrNull { it.id == data.activeProfileId }
            ?: data.profiles.firstOrNull()
            ?: return WidgetData(true, "", null, emptyList())

        val currentWeek = WeekUtil.currentWeek(profile.settings.semesterStartDate)
        val out = ArrayList<WidgetCourse>()

        val base = when (profile.kind) {
            ProfileKind.TERM -> data.termCourses[profile.termValue].orEmpty()
            ProfileKind.CUSTOM -> profile.frozenCourses
        }
        for (c in base) {
            val nodes = Periods.nodesOfSection(c.sectionIndex)
            out.add(
                WidgetCourse(
                    day = c.day,
                    startNode = nodes.first,
                    endNode = nodes.last,
                    name = c.name,
                    location = c.location,
                    teacher = c.teacher,
                    weeks = WeekUtil.parseWeeks(c.weeksRaw),
                    colorIndex = if (c.colorIndex > 0) c.colorIndex else colorIndexOf(c.name),
                ),
            )
        }
        for (c in profile.customCourses) {
            out.add(
                WidgetCourse(
                    day = c.day,
                    startNode = c.startNode,
                    endNode = c.endNode,
                    name = c.name,
                    location = c.location,
                    teacher = c.teacher,
                    weeks = c.weeks,
                    colorIndex = c.colorIndex,
                ),
            )
        }
        return WidgetData(true, profile.name, currentWeek, out)
    }

    /** 今日（本周生效）的课，按节次升序。 */
    fun todayCourses(data: WidgetData): List<WidgetCourse> {
        val today = LocalDate.now().dayOfWeek.value // 1=周一 … 7=周日
        val week = data.currentWeek
        return data.courses
            .filter { it.day == today && (it.weeks.isEmpty() || week == null || it.weeks.contains(week)) }
            .sortedBy { it.startNode }
    }

    /** 今日正在上的课，否则下一节；都没有返回 null。 */
    fun nextClass(data: WidgetData, now: LocalTime = LocalTime.now()): WidgetCourse? {
        val list = todayCourses(data)
        list.firstOrNull { c ->
            val s = startTime(c); val e = endTime(c)
            s != null && e != null && !now.isBefore(s) && now.isBefore(e)
        }?.let { return it }
        return list.mapNotNull { c -> startTime(c)?.let { it to c } }
            .filter { it.first.isAfter(now) }
            .minByOrNull { it.first }?.second
    }

    fun startTime(c: WidgetCourse): LocalTime? = parseTime(Periods.period(c.startNode)?.start)
    fun endTime(c: WidgetCourse): LocalTime? = parseTime(Periods.period(c.endNode)?.end)

    private fun parseTime(s: String?): LocalTime? =
        s?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } }
}
