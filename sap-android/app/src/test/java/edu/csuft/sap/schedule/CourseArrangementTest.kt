package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CourseArrangementTest {
    private val english = DisplayCourse("大学英语拓展课程", "教师甲", "树人楼南402", 4, 3, 4,
        listOf(1), 0, false, sourceId = "source:a")

    @Test fun elevenParallelClassesBecomeOneCardWithoutLosingTeacherRoomPairs() {
        val classes = (1..11).map { english.copy(teacher = "教师$it", location = "教室$it") }
        val groups = groupCourseArrangements(classes + english.copy(name = "形势与政策III", startNode = 7, endNode = 8))
        assertEquals(2, groups.size)
        assertEquals(11, groups.first().arrangements.size)
        assertEquals(classes, groups.first().courses)
        assertEquals(CourseArrangement("教师5", "教室5"), groups.first().arrangements[4])
        assertEquals(4, calendarPeriodCount(groups.flatMap { it.courses }))
    }

    @Test fun exactDuplicatesAppearOnceButOriginalsRemainAvailableForEditing() {
        val group = groupCourseArrangements(listOf(english, english.copy(sourceId = "source:b"))).single()
        assertEquals(1, group.arrangements.size)
        assertEquals(2, group.courses.size)
        assertEquals("教师甲", group.teacherSummary)
        assertEquals("树人楼南402", group.locationSummary)
    }

    @Test fun sameTeacherDifferentRoomAndDifferentTeacherSameRoomPreserveCorrespondence() {
        val group = groupCourseArrangements(listOf(english, english.copy(location = "教室乙"),
            english.copy(teacher = "教师乙"))).single()
        assertEquals(3, group.arrangements.size)
        assertEquals("2 位教师", group.teacherSummary)
        assertEquals("2 处教室", group.locationSummary)
    }

    @Test fun differentNamesDaysExactTimesAndWeekVisibilityMustNotMerge() {
        val courses = listOf(english, english.copy(name = "数学"), english.copy(day = 5),
            english.copy(startNode = 4), english.copy(endNode = 5), english.copy(isThisWeek = false))
        assertEquals(courses.size, groupCourseArrangements(courses).size)
    }

    @Test fun calendarFiltersTeachingWeeksBeforeGroupingAndRetainsCustomCourses() {
        val courses = listOf(english, english.copy(teacher = "下周教师", weeks = listOf(2)),
            english.copy(teacher = "自建教师", isCustom = true, customId = "custom"))
        val day = coursesOnDate(courses, ScheduleSettings(semesterStartDate = "2026-09-07"),
            LocalDate.parse("2026-09-10"))!!
        val group = groupCourseArrangements(day).single()
        assertEquals(2, group.arrangements.size)
        assertFalse(group.teachers.contains("下周教师"))
        assertTrue(group.courses.any { it.customId == "custom" })
    }

    @Test fun blankNamesAreNotCollapsedAndWhitespaceInDetailsIsIgnored() {
        assertEquals(2, groupCourseArrangements(listOf(english.copy(name = ""), english.copy(name = ""))).size)
        val group = groupCourseArrangements(listOf(english, english.copy(name = " 大学英语拓展课程 ",
            teacher = " 教师甲 ", location = "树人楼南402 "))).single()
        assertEquals(1, group.arrangements.size)
    }
}
