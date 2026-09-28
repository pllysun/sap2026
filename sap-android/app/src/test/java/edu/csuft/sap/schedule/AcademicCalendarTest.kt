package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*

import org.junit.Assert.*
import org.junit.Test

class AcademicCalendarTest {
    private fun profile(id: String, manual: Boolean = false, kind: ProfileKind = ProfileKind.TERM) =
        ScheduleProfile(id, id, kind, "2026-2027-1", ScheduleSettings(semesterStartDate = "2026-09-01", semesterStartDateManual = manual))

    @Test fun sharedCalendarUpdatesAllSourcesButPreservesManualAndCustom() {
        val root = ScheduleRoot(accounts = mapOf(
            "jw" to AccountData(profiles = listOf(profile("jw"), profile("manual", true), profile("custom", kind = ProfileKind.CUSTOM))),
            "web" to AccountData(profiles = listOf(profile("web"))),
            "class" to AccountData(profiles = listOf(profile("class"))),
        ))
        val updated = root.withAcademicCalendar(mapOf("2026-2027-1" to "2026-09-07"))
        for (source in listOf("jw", "web", "class")) assertEquals("2026-09-07", updated.accounts.getValue(source).profiles.first().settings.semesterStartDate)
        assertEquals("2026-09-01", updated.accounts.getValue("jw").profiles[1].settings.semesterStartDate)
        assertEquals("2026-09-01", updated.accounts.getValue("jw").profiles[2].settings.semesterStartDate)
        assertEquals(root, root.withAcademicCalendar(emptyMap()))
        assertEquals(updated, updated.withAcademicCalendar(mapOf("2026-2027-1" to "2026-09-07")))
    }
}
