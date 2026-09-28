package edu.csuft.sap.ui.schedule

import org.junit.Assert.*
import org.junit.Test

class ScheduleRequestContextTest {
    @Test fun onlyCurrentOnlineAcademicSourceMayWriteResponse() {
        assertTrue(scheduleRequestStillCurrent("user", "jw", "user", "jw", true, true))
        assertFalse(scheduleRequestStillCurrent("user", "jw", "other", "jw", true, true))
        assertFalse(scheduleRequestStillCurrent("user", "jw", "user", "class", true, true))
        assertFalse(scheduleRequestStillCurrent("user", "jw", "user", "jw", false, true))
        assertFalse(scheduleRequestStillCurrent("user", "jw", "user", "jw", true, false))
    }
}
