package edu.csuft.sap.ui.schedule

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarNavigationTest {
    @Test fun swipesCrossYearAndOnlyMoveOneMonth() {
        val month = YearMonth.of(2026, 12)
        assertEquals(YearMonth.of(2027, 1), monthAfterSwipe(month, -900f, 48f))
        assertEquals(YearMonth.of(2026, 11), monthAfterSwipe(month, 90f, 48f))
        assertEquals(YearMonth.of(2025, 12), monthAfterSwipe(YearMonth.of(2026, 1), 48f, 48f))
    }
    @Test fun smallDragsDoNotChangeMonth() {
        val month = YearMonth.of(2026, 9)
        for (distance in listOf(0f, 20f, -20f, 47f, -47f)) {
            assertEquals(month, monthAfterSwipe(month, distance, 48f))
        }
    }
}
