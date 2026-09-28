package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test

class WeekendSyncTest {
    private fun root(day: Int) = ScheduleRoot(accounts = mapOf("a" to AccountData(activeProfileId = "p",
        profiles = listOf(ScheduleProfile("p", "课表", ProfileKind.TERM, "term")),
        termCourses = mapOf("term" to listOf(CachedCourse("课程", day = day, sectionIndex = 1))))),
        displaySettings = ScheduleDisplaySettings(rowHeight = 80, showWeekend = false))
    @Test fun weekendIsBasedOnlyOnSuccessfullySyncedSelectedTerm() {
        assertTrue(root(6).withSyncedWeekend("a", setOf("term")).displaySettings!!.showWeekend)
        assertTrue(root(7).withSyncedWeekend("a", setOf("term")).displaySettings!!.showWeekend)
        assertFalse(root(5).withSyncedWeekend("a", setOf("term")).displaySettings!!.showWeekend)
        assertEquals(root(6), root(6).withSyncedWeekend("a", setOf("other")))
        assertEquals(root(6), root(6).withSyncedWeekend("deleted", setOf("term")))
    }
    @Test fun manualOverridePersistsUntilNextSyncAndOtherAppearanceIsRetained() {
        val synced = root(6).withSyncedWeekend("a", setOf("term"))
        val manual = synced.withDisplaySettings(synced.displaySettings!!.copy(showWeekend = false))
        assertFalse(manual.displaySettings!!.showWeekend)
        assertEquals(manual, manual.withAcademicCalendar(emptyMap()))
        assertTrue(manual.withSyncedWeekend("a", setOf("term")).displaySettings!!.showWeekend)
        assertEquals(80, manual.withSyncedWeekend("a", setOf("term")).displaySettings!!.rowHeight)
    }
}
