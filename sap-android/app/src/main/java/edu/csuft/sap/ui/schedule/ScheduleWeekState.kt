package edu.csuft.sap.ui.schedule

import edu.csuft.sap.data.schedule.WeekUtil
import java.time.LocalDate

internal enum class WeekStatus { UNKNOWN, UPCOMING, CURRENT, PAST }

/** 浏览周次与实际上课周次分开：未开学可以浏览第 1 周，但没有“本周”课程。 */
internal class ScheduleWeekState private constructor(
    val totalWeeks: Int,
    val startDate: LocalDate?,
    val currentWeek: Int?,
) {
    val defaultWeek: Int get() = (currentWeek ?: 1).coerceIn(1, totalWeeks)

    val statusText: String? get() = when {
        startDate == null -> "未设置开学日期，可手动选择周次"
        currentWeek == null -> "学期尚未开始，${startDate.monthValue} 月 ${startDate.dayOfMonth} 日开学"
        currentWeek > totalWeeks -> "本学期已结束，共 $totalWeeks 周"
        else -> null
    }

    val shortcutLabel: String get() = when {
        startDate == null -> "回到第 1 周"
        currentWeek == null -> "查看第 1 周"
        currentWeek > totalWeeks -> "查看最后一周"
        else -> "回到本周（第 $currentWeek 周）"
    }

    fun statusOf(week: Int): WeekStatus = when {
        startDate == null -> WeekStatus.UNKNOWN
        currentWeek == null -> WeekStatus.UPCOMING
        week < currentWeek -> WeekStatus.PAST
        week == currentWeek -> WeekStatus.CURRENT
        else -> WeekStatus.UPCOMING
    }

    fun resolveSelection(previousWeek: Int, manuallySelected: Boolean): Int =
        if (manuallySelected) previousWeek.coerceIn(1, totalWeeks) else defaultWeek

    companion object {
        fun from(
            semesterStartDate: String?,
            totalWeeks: Int,
            today: LocalDate = LocalDate.now(),
        ) = ScheduleWeekState(
            totalWeeks = totalWeeks.coerceAtLeast(1),
            startDate = WeekUtil.parseDate(semesterStartDate),
            currentWeek = WeekUtil.currentWeek(semesterStartDate, today),
        )
    }
}
