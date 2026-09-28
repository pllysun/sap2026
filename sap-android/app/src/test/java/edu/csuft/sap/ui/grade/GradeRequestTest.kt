package edu.csuft.sap.ui.grade

import edu.csuft.sap.data.account.AccountManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeRequestTest {
    @Test fun classAndWebSlotsNeverRequestGradesEvenDuringModeTransitions() {
        for (account in listOf(null, "", AccountManager.DEFAULT_CLASS_ACCOUNT,
            AccountManager.CLASS_ACCOUNT_PREFIX + "saved-class", AccountManager.WEBVIEW_ACCOUNT)) {
            assertFalse(canSyncGrades(account, academicMode = true, online = true))
        }
    }

    @Test fun leavingAcademicModeOrGoingOfflineStopsAcademicRequests() {
        assertFalse(canSyncGrades("20260001", academicMode = false, online = true))
        assertFalse(canSyncGrades("20260001", academicMode = true, online = false))
        assertTrue(canSyncGrades("20260001", academicMode = true, online = true))
    }
}
