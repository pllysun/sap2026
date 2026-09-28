package edu.csuft.sap.widget

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetSummaryTest {
    private val course = WidgetCourse(4, 3, 4, "英语", "教室", "老师", listOf(1), 0)
    @Test fun parallelGroupsCountOnceWithoutOvercountingPeriods() {
        val grouped = groupedWidgetCourses((1..11).map { course.copy(teacher = "教师$it", location = "教室$it") })
        assertEquals(1, grouped.size)
        assertEquals("11 处教室", grouped.single().location)
        assertEquals(2, widgetPeriodCount(grouped))
    }
    @Test fun otherDaysAndPeriodsRemainSeparateAndSemesterEndIsRespected() {
        assertEquals(3, groupedWidgetCourses(listOf(course, course.copy(day = 5), course.copy(startNode = 7, endNode = 8))).size)
        val data = WidgetData(true, "课表", 1, listOf(course.copy(weeks = emptyList())), "2026-09-07", 1)
        assertEquals(1, WidgetRepository.coursesOn(data, LocalDate.parse("2026-09-10")).size)
        assertTrue(WidgetRepository.coursesOn(data, LocalDate.parse("2026-09-17")).isEmpty())
    }
}
