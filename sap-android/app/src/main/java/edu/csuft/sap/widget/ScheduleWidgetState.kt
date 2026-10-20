package edu.csuft.sap.widget

import edu.csuft.sap.data.schedule.WeekUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 保持已有今日 / 本周的存储值，升级后各组件仍保留原来的选择。 */
enum class ScheduleWidgetMode(val value: Int) {
    TODAY(0), WEEK(1), TOMORROW(2);

    companion object {
        fun fromValue(value: Int): ScheduleWidgetMode = entries.firstOrNull { it.value == value } ?: TODAY
    }
}

data class ScheduleWidgetTab(val mode: ScheduleWidgetMode, val label: String)

/** 标题与列表共用目标日期，跨周时不会把明日课程套用到今日周次。 */
data class ScheduleWidgetState(
    val mode: ScheduleWidgetMode,
    val date: LocalDate,
    val title: String,
    val emptyText: String,
    val courses: List<WidgetCourse>,
    val tabs: List<ScheduleWidgetTab>,
)

fun scheduleWidgetState(
    data: WidgetData,
    mode: ScheduleWidgetMode,
    today: LocalDate = LocalDate.now(),
): ScheduleWidgetState {
    // 明日落在周末时直接预告周一；周五、周六、周日均指向下周一。
    val tomorrow = today.plusDays(1)
    val nextDate = if (tomorrow.dayOfWeek.value >= DayOfWeek.SATURDAY.value) {
        tomorrow.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    } else {
        tomorrow
    }
    val nextLabel = if (nextDate == tomorrow && today.dayOfWeek != DayOfWeek.SUNDAY) "明日" else "周一"
    val date = if (mode == ScheduleWidgetMode.TOMORROW) nextDate else today
    val tabs = listOf(
        ScheduleWidgetTab(ScheduleWidgetMode.TODAY, "今日"),
        ScheduleWidgetTab(ScheduleWidgetMode.TOMORROW, nextLabel),
        ScheduleWidgetTab(ScheduleWidgetMode.WEEK, "本周"),
    )
    val label = tabs.first { it.mode == mode }.label
    val week = WeekUtil.currentWeek(data.semesterStart, date)
    val weekLabel = week?.let { "第${it}周" } ?: "未设周次"
    val courses = if (!data.bound) {
        emptyList()
    } else if (mode == ScheduleWidgetMode.WEEK) {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0..6).flatMap { WidgetRepository.coursesOn(data, monday.plusDays(it.toLong())) }
            .sortedWith(compareBy({ it.day }, { it.startNode }))
    } else {
        WidgetRepository.coursesOn(data, date)
    }
    val start = WeekUtil.parseDate(data.semesterStart)
    val emptyText = when {
        !data.bound || data.profileName.isBlank() -> "请在 App 选择或导入课表"
        start == null -> "请在 App 设置开学日期"
        date.isBefore(start) -> "当前学期尚未开始"
        (lastSemesterCourseDate(data) ?: start.minusDays(1)).isBefore(date) -> "当前学期课程已结束"
        else -> "${label}无课"
    }
    return ScheduleWidgetState(
        mode = mode,
        date = date,
        title = if (data.bound) "$label · $weekLabel" else "软协课表 · 未登录",
        emptyText = emptyText,
        courses = groupedWidgetCourses(courses),
        tabs = tabs.filter { it.mode != mode },
    )
}

/** 按实际开课周次找最后一次课程；空周次表示每周，超出学期范围的周次不生效。 */
private fun lastSemesterCourseDate(data: WidgetData): LocalDate? {
    val start = WeekUtil.parseDate(data.semesterStart) ?: return null
    return data.courses.mapNotNull { course ->
        if (course.day !in 1..7) return@mapNotNull null
        val lastWeek = (1..data.totalWeeks).lastOrNull { course.occursInWeek(it) } ?: return@mapNotNull null
        val weekStart = start.plusWeeks((lastWeek - 1).toLong())
        weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(course.day)))
    }.maxOrNull()
}
