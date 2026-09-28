package edu.csuft.sap.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginPrivacyTest {
    @Test fun guestAndUnknownIdentityUseGuestAgreement() {
        assertFalse(requiresMemberPrivacy(emptyList()))
        assertFalse(requiresMemberPrivacy(listOf(4)))
    }

    @Test fun membersAndAdministratorsRequireAdditionalConsent() {
        (0..3).forEach { assertTrue(requiresMemberPrivacy(listOf(it))) }
        assertTrue(requiresMemberPrivacy(listOf(4, 3)))
    }
}
