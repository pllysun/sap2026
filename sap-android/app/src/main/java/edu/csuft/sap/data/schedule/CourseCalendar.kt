package edu.csuft.sap.data.schedule

import java.time.LocalDate
import java.time.YearMonth

/** 日历只读投影，不修改课表页正在查看的周数。null 表示日期未知，不能误标为休息。 */
fun coursesOnDate(courses: List<DisplayCourse>, settings: ScheduleSettings, date: LocalDate): List<DisplayCourse>? {
    if (WeekUtil.parseDate(settings.semesterStartDate) == null) return null
    val week = WeekUtil.currentWeek(settings.semesterStartDate, date) ?: return emptyList()
    if (week > settings.totalWeeks.coerceAtLeast(1)) return emptyList()
    return courses.filter { it.day == date.dayOfWeek.value && (it.weeks.isEmpty() || week in it.weeks) }
        .sortedWith(compareBy({ it.startNode }, { it.endNode }, { it.name }))
}

/** 重叠课程不重复计算课时，详情仍保留全部课程。 */
fun calendarPeriodCount(courses: List<DisplayCourse>): Int = courses.flatMap {
    (it.startNode.coerceAtLeast(1)..it.endNode.coerceAtMost(Periods.MAX_NODES)).toList()
}.toSet().size

fun calendarMonthCells(month: YearMonth): List<LocalDate?> {
    val padding = month.atDay(1).dayOfWeek.value - 1
    val cells = List<LocalDate?>(padding) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return cells + List((7 - cells.size % 7) % 7) { null }
}

data class CalendarDay(val date: LocalDate, val courseCount: Int, val periodCount: Int)

/** 月历与今日共用同名/同一时段合并规则，null 与“已知无课”严格区分。 */
fun calendarMonthDays(courses: List<DisplayCourse>, settings: ScheduleSettings, month: YearMonth): List<CalendarDay>? {
    if (WeekUtil.parseDate(settings.semesterStartDate) == null) return null
    return (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        val active = coursesOnDate(courses, settings, date).orEmpty().map { it.copy(isThisWeek = true) }
        CalendarDay(date, groupCourseArrangements(active).size, calendarPeriodCount(active))
    }
}

data class RestPeriod(val start: LocalDate, val end: LocalDate) {
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
}

/** 仅表示课表上连续无课，不推断法定假期。跨周也保留一个完整区间。 */
fun calendarRestPeriods(days: List<CalendarDay>): List<RestPeriod> {
    val result = mutableListOf<RestPeriod>()
    days.filter { it.courseCount == 0 }.sortedBy { it.date }.forEach { day ->
        val last = result.lastOrNull()
        if (last != null && last.end.plusDays(1) == day.date) result[result.lastIndex] = last.copy(end = day.date)
        else result += RestPeriod(day.date, day.date)
    }
    return result
}
