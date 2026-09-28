package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CourseCalendarTest {
    private val settings = ScheduleSettings(semesterStartDate = "2026-09-07", totalWeeks = 20)
    private fun course(day: Int = 1, weeks: List<Int> = emptyList(), from: Int = 1, to: Int = 2) =
        DisplayCourse("数学", "老师", "教室", day, from, to, weeks, 0, false)
    @Test fun matchesWeekdayAndTeachingWeeksNotCurrentlySelectedWeek() {
        val courses = listOf(course(weeks = listOf(1, 3)), course(day = 2))
        assertEquals(1, coursesOnDate(courses, settings, LocalDate.parse("2026-09-07"))!!.size)
        assertTrue(coursesOnDate(courses, settings, LocalDate.parse("2026-09-14"))!!.isEmpty())
        assertEquals(1, coursesOnDate(courses, settings, LocalDate.parse("2026-09-21"))!!.size)
    }
    @Test fun unknownDateIsNotRestAndOutsideSemesterIsRest() {
        assertNull(coursesOnDate(listOf(course()), settings.copy(semesterStartDate = null), LocalDate.now()))
        assertTrue(coursesOnDate(listOf(course()), settings, LocalDate.parse("2026-08-31"))!!.isEmpty())
        assertTrue(coursesOnDate(listOf(course()), settings, LocalDate.parse("2027-09-06"))!!.isEmpty())
    }
    @Test fun countsPeriodsAndPreservesOverlappingCoursesInDetails() {
        val courses = listOf(course(), course(from = 2, to = 4))
        assertEquals(4, calendarPeriodCount(courses))
        assertEquals(2, coursesOnDate(courses, settings, LocalDate.parse("2026-09-07"))!!.size)
    }
    @Test fun monthHandlesLeapYearAndWeekPadding() {
        val cells = calendarMonthCells(YearMonth.of(2028, 2))
        assertEquals(29, cells.filterNotNull().size)
        assertNull(cells.first()) // 2028-02-01 is Tuesday
        assertEquals(0, cells.size % 7)
        assertEquals(LocalDate.of(2028, 2, 29), cells.filterNotNull().last())
    }
    @Test fun matchesAcrossCalendarYearAndIncludesCustomCourses() {
        val custom = course().copy(isCustom = true, customId = "custom")
        assertEquals(listOf(custom), coursesOnDate(listOf(custom), settings, LocalDate.parse("2027-01-04")))
    }
}
