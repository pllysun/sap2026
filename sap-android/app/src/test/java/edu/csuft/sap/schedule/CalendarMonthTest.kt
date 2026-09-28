package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthTest {
    private val settings = ScheduleSettings(semesterStartDate = "2026-09-07")
    private val course = DisplayCourse("英语", "老师", "教室", 4, 3, 4, listOf(1), 0, false)
    @Test fun monthCountsParallelGroupsOnceAndFiltersWeeks() {
        val courses = (1..11).map { course.copy(teacher = "老师$it", location = "教室$it") } + course.copy(weeks = listOf(2))
        val month = calendarMonthDays(courses, settings, YearMonth.of(2026, 9))!!
        assertEquals(1, month[9].courseCount)
        assertEquals(2, month[9].periodCount)
        assertEquals(1, month[16].courseCount)
        assertEquals(0, month[23].courseCount)
        assertNull(calendarMonthDays(courses, settings.copy(semesterStartDate = null), YearMonth.of(2026, 9)))
    }
    @Test fun restIntervalsJoinAcrossWeekBoundaryButStopAtClasses() {
        val days = (1..14).map { CalendarDay(LocalDate.of(2026, 9, it), if (it == 9) 1 else 0, 0) }
        val ranges = calendarRestPeriods(days)
        assertEquals(listOf(8L, 5L), ranges.map { it.days })
        assertEquals(LocalDate.of(2026, 9, 8), ranges.first().end)
        assertTrue(calendarRestPeriods(emptyList()).isEmpty())
    }
}
