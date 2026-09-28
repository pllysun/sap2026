package edu.csuft.sap.ui.auth

import org.junit.Assert.*
import org.junit.Test

class LoginApprovalTest {
    @Test fun validationAloneDoesNotPersistAndConfirmationRunsOnce() {
        val approval = LoginApproval<String>()
        var writes = 0
        approval.stage("verified-session")
        assertEquals(0, writes)
        assertTrue(approval.approve { assertEquals("verified-session", it); writes++ })
        assertFalse(approval.approve { writes++ })
        assertEquals(1, writes)
    }
    @Test fun decliningOrRecreatingDropsUnconfirmedSession() {
        val approval = LoginApproval<String>()
        approval.stage("session")
        approval.discard()
        assertFalse(approval.approve { fail("拒绝后不可持久化") })
        assertFalse(LoginApproval<String>().approve { fail("新进程不可恢复未确认会话") })
    }
    @Test fun failedPersistenceCanBeRetriedWithoutLosingPendingSession() {
        val approval = LoginApproval<String>()
        approval.stage("session")
        assertTrue(runCatching { approval.approve { error("disk") } }.isFailure)
        assertTrue(approval.approve { assertEquals("session", it) })
    }
    @Test fun cloudFullCapabilityNeedsAcademicAgreementWithoutMembership() {
        assertTrue(requiresMemberPrivacy(listOf(4), 2))
        assertFalse(requiresMemberPrivacy(listOf(4), 0))
        assertFalse(requiresMemberPrivacy(listOf(4), 1))
    }
}
