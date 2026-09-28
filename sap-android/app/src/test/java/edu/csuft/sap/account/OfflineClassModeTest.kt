package edu.csuft.sap.data.account

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class OfflineClassModeTest {
    @After fun reset() {
        ConnectivityState.online = true
        MemberState.clearAccess()
    }

    @Test fun coldOfflineLaunchOffersClassWithoutResolvedIdentity() {
        MemberState.clearAccess()
        ConnectivityState.online = false
        assertFalse(MemberState.accessResolved)
        assertEquals(listOf(AppMode.WEB, AppMode.CLASS), MemberState.availableModes)
    }

    @Test fun offlineMemberDoesNotOfferUnavailableAcademicSyncMode() {
        MemberState.setAccess(listOf(3), 2)
        ConnectivityState.online = false
        assertEquals(listOf(AppMode.WEB, AppMode.CLASS), MemberState.availableModes)
    }

    @Test fun savedClassSurvivesOfflineRestartWithoutRoleInformation() {
        assertEquals(AppMode.CLASS, resolveScheduleMode(false, AppMode.CLASS, false, 0))
        assertEquals(AppMode.CLASS, resolveScheduleMode(false, AppMode.CLASS, true, 2))
    }

    @Test fun offlineWebAndAcademicFallbackRemainUnchanged() {
        assertEquals(AppMode.WEB, resolveScheduleMode(false, AppMode.WEB, false, 0))
        assertEquals(AppMode.WEB, resolveScheduleMode(false, AppMode.JW, true, 2))
    }

    @Test fun reconnectRestoresPermissionChecks() {
        assertEquals(AppMode.CLASS, resolveScheduleMode(true, AppMode.CLASS, false, 0))
        assertEquals(AppMode.CLASS, resolveScheduleMode(true, AppMode.CLASS, false, 1))
        assertEquals(AppMode.JW, resolveScheduleMode(true, AppMode.JW, true, 2))
    }
}
