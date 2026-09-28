package edu.csuft.sap.data.account

import org.junit.Assert.*
import org.junit.Test

class ModeRequestTest {
    @Test fun academicSelectionDoesNotCommitBeforeConsent() {
        repeat(3) { assertEquals(ModeRequestDecision.CONSENT, modeRequestDecision(AppMode.JW, AppMode.entries, false)) }
        assertEquals(ModeRequestDecision.APPLY, modeRequestDecision(AppMode.JW, AppMode.entries, true))
    }
    @Test fun webAndClassNeverRequireAcademicConsent() {
        for (mode in listOf(AppMode.WEB, AppMode.CLASS)) {
            assertEquals(ModeRequestDecision.APPLY, modeRequestDecision(mode, AppMode.entries, false))
        }
    }
    @Test fun consentDoesNotBypassCloudOrOfflineRestriction() {
        assertEquals(ModeRequestDecision.DENIED, modeRequestDecision(AppMode.JW, listOf(AppMode.WEB, AppMode.CLASS), true))
    }
}
