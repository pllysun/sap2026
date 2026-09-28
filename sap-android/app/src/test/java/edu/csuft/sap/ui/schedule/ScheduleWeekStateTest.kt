package edu.csuft.sap.ui.schedule

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleWeekStateTest {
    private val screenshotDate = LocalDate.of(2026, 9, 5)

    @Test
    fun coldStartAndSourceChangesImmediatelyResolveClampedWeek() {
        val today=LocalDate.of(2026,9,11)
        val old=ScheduleWeekState.from("2025-09-08",20,today)
        assertEquals(20,old.resolveSelection(1,manuallySelected=false))
        val current=ScheduleWeekState.from("2026-08-31",20,today)
        assertEquals(2,current.resolveSelection(20,manuallySelected=false))
        assertEquals(9,current.resolveSelection(9,manuallySelected=true))
    }

    @Test
    fun latestSemesterBeforeSeptemberSeventhShowsUpcomingWeeksAndFirstWeekShortcut() {
        val state = ScheduleWeekState.from("2026-09-07", 20, screenshotDate)

        assertNull(state.currentWeek)
        assertEquals(1, state.defaultWeek)
        assertEquals(setOf(WeekStatus.UPCOMING), (1..20).map(state::statusOf).toSet())
        assertEquals("学期尚未开始，9 月 7 日开学", state.statusText)
        assertEquals("查看第 1 周", state.shortcutLabel)
    }

    @Test
    fun switchingFromOldSemesterWeekTwentyToUpcomingSemesterStartsAtWeekOne() {
        val previous = ScheduleWeekState.from("2026-03-09", 20, screenshotDate)
        val latest = ScheduleWeekState.from("2026-09-07", 20, screenshotDate)

        assertEquals(20, previous.defaultWeek)
        assertEquals(1, latest.resolveSelection(previous.defaultWeek, manuallySelected = false))
    }

    @Test
    fun manualSelectionWithinTheUpcomingSemesterSurvivesRerender() {
        val state = ScheduleWeekState.from("2026-09-07", 20, screenshotDate)

        assertEquals(7, state.resolveSelection(7, manuallySelected = true))
        assertEquals(1, state.resolveSelection(7, manuallySelected = false))
    }

    @Test
    fun openingDayIsTheFirstCurrentWeek() {
        val state = ScheduleWeekState.from("2026-09-07", 20, LocalDate.of(2026, 9, 7))

        assertEquals(1, state.currentWeek)
        assertEquals(WeekStatus.CURRENT, state.statusOf(1))
        assertEquals(WeekStatus.UPCOMING, state.statusOf(2))
        assertEquals("回到本周（第 1 周）", state.shortcutLabel)
        assertNull(state.statusText)
    }

    @Test
    fun duringSemesterDistinguishesPastCurrentAndFutureWeeks() {
        val state = ScheduleWeekState.from("2026-08-24", 20, screenshotDate)

        assertEquals(2, state.currentWeek)
        assertEquals(WeekStatus.PAST, state.statusOf(1))
        assertEquals(WeekStatus.CURRENT, state.statusOf(2))
        assertEquals(WeekStatus.UPCOMING, state.statusOf(3))
        assertEquals(2, state.resolveSelection(20, manuallySelected = false))
    }

    @Test
    fun endedSemesterDoesNotOfferAnOutOfRangeCurrentWeek() {
        val state = ScheduleWeekState.from("2026-03-09", 20, screenshotDate)

        assertEquals(26, state.currentWeek)
        assertEquals(20, state.defaultWeek)
        assertEquals(setOf(WeekStatus.PAST), (1..20).map(state::statusOf).toSet())
        assertEquals("本学期已结束，共 20 周", state.statusText)
        assertEquals("查看最后一周", state.shortcutLabel)
    }

    @Test
    fun semesterEndsAfterTheSundayOfItsFinalWeek() {
        val sunday = ScheduleWeekState.from("2026-03-09", 20, LocalDate.of(2026, 7, 26))
        val monday = ScheduleWeekState.from("2026-03-09", 20, LocalDate.of(2026, 7, 27))

        assertEquals(WeekStatus.CURRENT, sunday.statusOf(20))
        assertNull(sunday.statusText)
        assertEquals(WeekStatus.PAST, monday.statusOf(20))
        assertEquals("本学期已结束，共 20 周", monday.statusText)
    }

    @Test
    fun absentAndInvalidDatesAreUnknownInsteadOfUpcoming() {
        for (start in listOf(null, "", "2026-13-01")) {
            val state = ScheduleWeekState.from(start, 20, screenshotDate)

            assertNull(state.currentWeek)
            assertEquals(WeekStatus.UNKNOWN, state.statusOf(1))
            assertEquals("未设置开学日期，可手动选择周次", state.statusText)
            assertEquals(1, state.defaultWeek)
            assertEquals(6, state.resolveSelection(6, manuallySelected = true))
        }
    }

    @Test
    fun selectionStaysWithinTheConfiguredRange() {
        val state = ScheduleWeekState.from("2026-09-07", 0, screenshotDate)

        assertEquals(1, state.totalWeeks)
        assertEquals(1, state.resolveSelection(20, manuallySelected = true))
    }
}
