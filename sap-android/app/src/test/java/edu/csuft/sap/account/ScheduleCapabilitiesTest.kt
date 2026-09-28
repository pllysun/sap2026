package edu.csuft.sap.data.account

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ScheduleCapabilitiesTest {
    @After fun reset() { MemberState.clearAccess(); ConnectivityState.online = true }
    @Test fun allOnlineIdentitiesAndCloudLevelsKeepWebAndClass() {
        ConnectivityState.online = true
        listOf(emptyList(), listOf(4), listOf(3), listOf(0)).forEach { roles ->
            (0..2).forEach { level ->
                MemberState.setAccess(roles, level)
                assertTrue(MemberState.availableModes.containsAll(listOf(AppMode.WEB, AppMode.CLASS)))
                assertEquals(roles.any { it <= 3 } || level == 2, MemberState.availableModes.contains(AppMode.JW))
                assertEquals(AppMode.CLASS, resolveScheduleMode(true, AppMode.CLASS, MemberState.isMember, level))
            }
        }
    }
    @Test fun academicResolutionRequiresMembershipOrFullCloud() {
        assertEquals(AppMode.JW, resolveScheduleMode(true, AppMode.JW, false, 2))
        (0..1).forEach { assertEquals(AppMode.WEB, resolveScheduleMode(true, AppMode.JW, false, it)) }
        (0..2).forEach { assertEquals(AppMode.JW, resolveScheduleMode(true, AppMode.JW, true, it)) }
    }
}
