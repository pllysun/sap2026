package edu.csuft.sap.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSnapshotTest {

    @Test
    fun emptyWebSpaceInheritsCompleteJwSnapshot() {
        val current = ScheduleProfile(
            id = "term:2025-2026-1",
            name = "2025-2026 第1学期",
            kind = ProfileKind.TERM,
            termValue = "2025-2026-1",
            settings = ScheduleSettings(totalWeeks = 19),
        )
        val source = AccountData(
            activeProfileId = current.id,
            profiles = listOf(current),
            termCourses = mapOf("2025-2026-1" to listOf(course("软件工程"))),
            termRemarks = mapOf("2025-2026-1" to listOf(Remark("专业实习"))),
            scanned = true,
        )

        assertEquals(source, inheritScheduleSnapshot(source, AccountData()))
    }

    @Test
    fun existingWebSchedulesArePreservedAndMissingJwTermsAreAdded() {
        val webProfile = termProfile("webview:2024-2025-2", "2024-2025-2")
        val jwOld = termProfile("term:2024-2025-2", "2024-2025-2")
        val jwCurrent = termProfile("term:2025-2026-1", "2025-2026-1")
        val target = AccountData(
            activeProfileId = webProfile.id,
            profiles = listOf(webProfile),
            termCourses = mapOf("2024-2025-2" to listOf(course("Web 本地课程"))),
            termRemarks = mapOf("2024-2025-2" to listOf(Remark("Web 备注"))),
            scanned = true,
        )
        val source = AccountData(
            activeProfileId = jwCurrent.id,
            profiles = listOf(jwOld, jwCurrent),
            termCourses = mapOf(
                "2024-2025-2" to listOf(course("教务旧课程")),
                "2025-2026-1" to listOf(course("教务当前课程")),
            ),
            termRemarks = mapOf(
                "2024-2025-2" to listOf(Remark("教务旧备注")),
                "2025-2026-1" to listOf(Remark("教务当前备注")),
            ),
            scanned = true,
        )

        val result = inheritScheduleSnapshot(source, target)

        assertEquals(jwCurrent.id, result.activeProfileId)
        assertEquals(listOf(webProfile, jwOld, jwCurrent), result.profiles)
        assertEquals("Web 本地课程", result.termCourses.getValue("2024-2025-2").single().name)
        assertEquals("教务当前课程", result.termCourses.getValue("2025-2026-1").single().name)
        assertEquals("Web 备注", result.termRemarks.orEmpty().getValue("2024-2025-2").single().name)
        assertEquals("教务当前备注", result.termRemarks.orEmpty().getValue("2025-2026-1").single().name)
    }

    @Test
    fun sourceWithoutProfilesDoesNotAlterWebSpace() {
        val target = AccountData(
            profiles = listOf(termProfile("webview:2025-2026-1", "2025-2026-1")),
            scanned = true,
        )

        assertEquals(target, inheritScheduleSnapshot(AccountData(), target))
    }

    private fun termProfile(id: String, term: String) = ScheduleProfile(
        id = id,
        name = term,
        kind = ProfileKind.TERM,
        termValue = term,
    )

    private fun course(name: String) = CachedCourse(
        name = name,
        day = 1,
        sectionIndex = 1,
    )
}
