package edu.csuft.sap.data.account

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberStateTest {

    @Test
    fun cloudLevelOffersAcademicModeWithoutChangingGuestIdentity() {
        MemberState.setAccess(listOf(4), 2)
        assertTrue(MemberState.availableModes.contains(AppMode.JW))
        assertTrue(MemberState.availableModes.contains(AppMode.WEB))
        assertTrue(MemberState.availableModes.contains(AppMode.CLASS))
        assertFalse(MemberState.isJw)
        assertFalse(MemberState.isMember)
    }

    @After
    fun reset() {
        MemberState.clearAccess()
    }

    @Test
    fun accessIsUnresolvedAfterCredentialsAreCleared() {
        MemberState.setAccess(listOf(4), 2)
        assertTrue(MemberState.accessResolved)
        assertTrue(MemberState.hasFullAppFeatures)

        MemberState.clearAccess()

        assertFalse(MemberState.accessResolved)
        assertFalse(MemberState.hasFullAppFeatures)
    }

    @Test
    fun realMemberKeepsFullFeaturesRegardlessOfCloudLevel() {
        MemberState.setAccess(listOf(3), 0)

        assertTrue(MemberState.accessResolved)
        assertTrue(MemberState.isMember)
        assertTrue(MemberState.hasFullAppFeatures)
    }
}
