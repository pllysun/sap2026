package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.WeekUtil
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekUtilTest {

    @Test
    fun dateBeforeSemesterHasNoActiveWeek() {
        val start = "2026-09-07"

        assertNull(WeekUtil.currentWeek(start, LocalDate.of(2026, 9, 5)))
        assertEquals(1, WeekUtil.currentWeek(start, LocalDate.of(2026, 9, 7)))
        assertEquals(1, WeekUtil.currentWeek(start, LocalDate.of(2026, 9, 11)))
        assertEquals(2, WeekUtil.currentWeek(start, LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun weekDatesRemainAnchoredToSemesterMonday() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 13),
            ),
            WeekUtil.datesOfWeek("2026-09-07", 1),
        )
    }
}
