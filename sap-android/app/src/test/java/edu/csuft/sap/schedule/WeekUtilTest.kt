package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.WeekUtil
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WeekUtilTest {
    @Test fun preservesParityAcrossSchoolWeekFormatsAndFullWidthPunctuation() {
        for (raw in listOf("1-16单周","1-16(单)","1-16周（单周）","单周1～16周","第１—１６周(单)[1-2节]"))
            assertEquals(raw,(1..16 step 2).toList(),WeekUtil.parseWeeks(raw))
        for (raw in listOf("1-16双周","2-16(双)","1-16（双周）","双周1至16周","1-16周[双]"))
            assertEquals(raw,(2..16 step 2).toList(),WeekUtil.parseWeeks(raw))
        assertEquals(listOf(1,3,5,7,11,13,15),WeekUtil.parseWeeks("1-8、10-16（单）"))
        assertEquals(listOf(1,2,3,4,5,6,7,8),WeekUtil.parseWeeks("1-8单周,2-8双周"))
        assertEquals(listOf(1,3,5,7,10,11,12),WeekUtil.parseWeeks("1-8(单),10-12"))
        assertEquals(listOf(1,2,3,4,11,13,15),WeekUtil.parseWeeks("1-4周,10-16周(单)"))
        assertTrue(WeekUtil.parseWeeks("2周(单)").isEmpty())
        assertEquals(listOf(1,3,5,10,11,12),WeekUtil.parseWeeks("1,3,5,10-12(周)"))
    }

    @Test fun invalidWeekStringsCannotBecomeEveryWeekAndEmptyStillMeansUnrestricted() {
        assertTrue(WeekUtil.isUnrestricted(null));assertTrue(WeekUtil.isUnrestricted("每周"))
        for(raw in listOf("周次待确认","1-999999周","2周（单）")) {
            assertFalse(WeekUtil.isUnrestricted(raw));assertTrue(WeekUtil.parseWeeks(raw).isEmpty())
        }
    }

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
