package edu.csuft.sap.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetStateTest {
    @Test fun importedWeekTextUsesSameParityForWidgetAndReminderData() {
        val raw="1-16周（双周）"
        val even=course(1,"双周课程",edu.csuft.sap.data.schedule.WeekUtil.parseWeeks(raw)).copy(weeksRaw=raw)
        val unknown=course(1,"未知周次").copy(weeksRaw="周次待确认")
        val data=semester.copy(courses=listOf(even,unknown))
        assertTrue(WidgetRepository.coursesOn(data,LocalDate.parse("2026-09-07")).isEmpty())
        assertEquals(listOf("双周课程"),WidgetRepository.coursesOn(data,LocalDate.parse("2026-09-14")).map { it.name })
    }
    private val semester = WidgetData(
        bound = true,
        profileName = "2026-2027 第1学期",
        currentWeek = 5,
        semesterStart = "2026-09-07",
        totalWeeks = 20,
        courses = emptyList(),
    )

    private fun course(day: Int, name: String, weeks: List<Int> = emptyList(), start: Int = 1) =
        WidgetCourse(day, start, start + 1, name, "树人楼301", "老师", weeks, 1)

    private fun state(data: WidgetData, date: String, mode: ScheduleWidgetMode = ScheduleWidgetMode.TOMORROW) =
        scheduleWidgetState(data, mode, LocalDate.parse(date))

    @Test
    fun weekdayTomorrowUsesItsOwnDateAndSortsLessons() {
        val data = semester.copy(courses = listOf(
            course(3, "下午课", start = 7), course(2, "今日课程"), course(3, "上午课", start = 3),
        ))
        val result = state(data, "2026-10-06")
        assertEquals(LocalDate.parse("2026-10-07"), result.date)
        assertEquals("明日 · 第5周", result.title)
        assertEquals(listOf("上午课", "下午课"), result.courses.map { it.name })
        assertEquals(listOf("今日", "本周"), result.tabs.map { it.label })
    }

    @Test
    fun weekendPreviewMovesToNextMondayAndUsesNewWeek() {
        val data = semester.copy(courses = listOf(
            course(1, "第五周周一", listOf(5)), course(1, "第六周周一", listOf(6)),
            course(6, "周六课程"), course(7, "周日课程"),
        ))
        listOf("2026-10-09", "2026-10-10", "2026-10-11").forEach { date ->
            val result = state(data, date)
            assertEquals(LocalDate.parse("2026-10-12"), result.date)
            assertEquals("周一 · 第6周", result.title)
            assertEquals(listOf("第六周周一"), result.courses.map { it.name })
            assertEquals("周一", state(data, date, ScheduleWidgetMode.TODAY).tabs.first().label)
        }
    }

    @Test
    fun weekendTodayAndWeekStillIncludeWeekendLessons() {
        val data = semester.copy(courses = listOf(course(1, "周一课"), course(6, "周六课"), course(7, "周日课")))
        assertEquals(listOf("周六课"), state(data, "2026-10-10", ScheduleWidgetMode.TODAY).courses.map { it.name })
        assertEquals(listOf("周日课"), state(data, "2026-10-11", ScheduleWidgetMode.TODAY).courses.map { it.name })
        val week = state(data, "2026-10-11", ScheduleWidgetMode.WEEK)
        assertEquals(listOf("周一课", "周六课", "周日课"), week.courses.map { it.name })
        assertEquals(listOf("今日", "周一"), week.tabs.map { it.label })
    }

    @Test
    fun nextWeekDoesNotReuseCachedCurrentWeekOrWrongParity() {
        val data = semester.copy(currentWeek = 1, courses = listOf(
            course(1, "单周课程", listOf(1, 3, 5)), course(1, "双周课程", listOf(2, 4, 6)),
        ))
        val result = state(data, "2026-10-11")
        assertEquals("周一 · 第6周", result.title)
        assertEquals(listOf("双周课程"), result.courses.map { it.name })
    }

    @Test
    fun emptyDayWithLaterLessonsDoesNotClaimSemesterHasEnded() {
        val data = semester.copy(courses = listOf(course(4, "周四课", listOf(5)), course(1, "以后还有课", listOf(8))))
        assertTrue(state(data, "2026-10-06").courses.isEmpty())
        assertEquals("明日无课", state(data, "2026-10-06").emptyText)
        assertEquals("周一无课", state(data, "2026-10-11").emptyText)
        assertEquals("本周无课", state(data, "2026-10-19", ScheduleWidgetMode.WEEK).emptyText)
    }

    @Test
    fun afterLastScheduledLessonSemesterIsEndedEvenBeforeTotalWeeks() {
        val data = semester.copy(courses = listOf(course(4, "最后一堂课", listOf(5))))
        val result = state(data, "2026-10-08")
        assertTrue(result.courses.isEmpty())
        assertEquals("当前学期课程已结束", result.emptyText)
        assertEquals("当前学期课程已结束", state(data, "2026-10-11", ScheduleWidgetMode.TODAY).emptyText)
    }

    @Test
    fun everyWeekLessonsStopAtConfiguredSemesterEnd() {
        val data = semester.copy(totalWeeks = 6, courses = listOf(course(1, "每周课")))
        assertEquals(listOf("每周课"), state(data, "2026-10-11").courses.map { it.name })
        assertTrue(state(data, "2026-10-18").courses.isEmpty())
        assertEquals("当前学期课程已结束", state(data, "2026-10-18").emptyText)
    }

    @Test
    fun outOfSemesterWeeksDoNotPreventEndedMessage() {
        val data = semester.copy(totalWeeks = 6, courses = listOf(
            course(1, "最后一周", listOf(6)), course(1, "无效周次", listOf(0, 21)),
        ))
        assertEquals("当前学期课程已结束", state(data, "2026-10-18").emptyText)
    }

    @Test
    fun emptyImportedSemesterShowsEndedMessage() {
        assertEquals("当前学期课程已结束", state(semester, "2026-10-06").emptyText)
    }

    @Test
    fun unconfiguredAndPreSemesterAreNotMistakenForEndedSemester() {
        val data = semester.copy(courses = listOf(course(1, "每周课")))
        assertEquals("请在 App 选择或导入课表", state(data.copy(bound = false), "2026-10-06").emptyText)
        assertTrue(state(data.copy(bound = false), "2026-10-06").courses.isEmpty())
        assertEquals("请在 App 选择或导入课表", state(data.copy(profileName = ""), "2026-10-06").emptyText)
        assertEquals("请在 App 设置开学日期", state(data.copy(semesterStart = null), "2026-10-06").emptyText)
        assertEquals("请在 App 设置开学日期", state(data.copy(semesterStart = "wrong-date"), "2026-10-06").emptyText)
        assertEquals("当前学期尚未开始", state(data, "2026-08-31").emptyText)
        assertEquals("周一 · 第1周", state(data, "2026-09-06").title)
        assertEquals(listOf("每周课"), state(data, "2026-09-06").courses.map { it.name })
    }

    @Test
    fun futureCourseDatesRespectNonMondaySemesterStart() {
        val data = semester.copy(semesterStart = "2026-09-09", totalWeeks = 1, courses = listOf(course(1, "周一课")))
        assertEquals(listOf("周一课"), state(data, "2026-09-13").courses.map { it.name })
        assertEquals("当前学期课程已结束", state(data, "2026-09-14").emptyText)
    }

    @Test
    fun tomorrowAlsoWorksAcrossYearBoundary() {
        val data = semester.copy(semesterStart = "2026-12-28", currentWeek = 1, courses = listOf(course(5, "元旦课")))
        val result = state(data, "2026-12-31")
        assertEquals(LocalDate.parse("2027-01-01"), result.date)
        assertEquals("明日 · 第1周", result.title)
        assertEquals(listOf("元旦课"), result.courses.map { it.name })
    }

    @Test
    fun parallelClassesRemainGroupedInTomorrowView() {
        val first = course(3, "大学英语")
        val result = state(semester.copy(courses = listOf(first, first.copy(location = "树人楼302", teacher = "另一位老师"))), "2026-10-06")
        assertEquals(1, result.courses.size)
        assertEquals("2 处教室", result.courses.single().location)
        assertEquals("2 位教师", result.courses.single().teacher)
    }

    @Test
    fun oldPreferencesRetainTheirMeaningAfterUpgrade() {
        assertEquals(ScheduleWidgetMode.TODAY, ScheduleWidgetMode.fromValue(0))
        assertEquals(ScheduleWidgetMode.WEEK, ScheduleWidgetMode.fromValue(1))
        assertEquals(ScheduleWidgetMode.TOMORROW, ScheduleWidgetMode.fromValue(2))
        assertEquals(ScheduleWidgetMode.TODAY, ScheduleWidgetMode.fromValue(999))
    }
}
