package edu.csuft.sap.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRepositoryTest {

    @Test
    fun coursesBeforeSemesterDoNotMatchTheFirstWeek() {
        val data = WidgetData(
            bound = true,
            profileName = "2026-2027 第1学期",
            currentWeek = null,
            semesterStart = "2026-09-07",
            courses = listOf(
                WidgetCourse(5, 1, 2, "周五课程", "", "", emptyList(), 1),
                WidgetCourse(5, 3, 4, "第一周课程", "", "", listOf(1), 2),
            ),
        )

        assertEquals(emptyList<WidgetCourse>(), WidgetRepository.coursesOn(data, LocalDate.of(2026, 9, 4)))
        assertEquals(
            listOf("周五课程", "第一周课程"),
            WidgetRepository.coursesOn(data, LocalDate.of(2026, 9, 11)).map { it.name },
        )
    }
}
