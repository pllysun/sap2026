package edu.csuft.sap.data.account

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleModeDefaultsTest {
    @Test fun newAccountsAlwaysStartWithClassRegardlessOfAccessAndLegacyMode() {
        for (oldMode in AppMode.entries.map { it.name } + null) {
            val selected = restoredScheduleMode(null, null, oldMode)
            assertEquals(AppMode.CLASS, selected)
            for (online in listOf(false, true)) for (member in listOf(false, true)) for (level in 0..2) {
                assertEquals(AppMode.CLASS, resolveScheduleMode(online, selected, member, level))
            }
        }
    }

    @Test fun explicitAccountChoiceSurvivesRestartEvenBeforeImportingAnyCourses() {
        for (mode in AppMode.entries) {
            assertEquals(mode, restoredScheduleMode(mode.name, null, AppMode.JW.name))
            assertEquals(mode, restoredScheduleMode(mode.name, AccountManager.DEFAULT_CLASS_ACCOUNT))
        }
    }

    @Test fun historicalSourcesRestoreEachAccountsOwnMode() {
        assertEquals(AppMode.JW, restoredScheduleMode(null, "20260001"))
        assertEquals(AppMode.WEB, restoredScheduleMode(null, AccountManager.WEBVIEW_ACCOUNT))
        assertEquals(AppMode.CLASS, restoredScheduleMode(null, AccountManager.CLASS_ACCOUNT_PREFIX + "class-a"))
        assertEquals(AppMode.CLASS, restoredScheduleMode(null, null))
    }

    @Test fun upgradePreservesCurrentAccountsLegacyChoiceIncludingOfflineFallback() {
        assertEquals(AppMode.JW, restoredScheduleMode(null, AccountManager.WEBVIEW_ACCOUNT, AppMode.JW.name))
        assertEquals(AppMode.CLASS, restoredScheduleMode(null, AccountManager.DEFAULT_CLASS_ACCOUNT, AppMode.CLASS.name))
    }

    @Test fun invalidOrMissingValuesFallBackToClassWithoutHistory() {
        for (saved in listOf(null, "", "INVALID")) {
            assertEquals(AppMode.CLASS, restoredScheduleMode(saved, null))
            assertEquals(AppMode.CLASS, restoredScheduleMode(saved, ""))
        }
        assertEquals(AppMode.WEB, restoredScheduleMode("INVALID", AccountManager.WEBVIEW_ACCOUNT, "INVALID"))
    }
}
