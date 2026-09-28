package edu.csuft.sap.account

import edu.csuft.sap.data.account.ModeGuideKind
import edu.csuft.sap.data.account.nextModeGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeGuideStateTest {
    @Test fun newGuestSeesIntro() {
        assertEquals(ModeGuideKind.INTRO, nextModeGuide(false, false, false))
    }

    @Test fun cloudUnlockShowsAcademicOnlyAfterIntro() {
        assertNull(nextModeGuide(true, false, false))
        assertEquals(ModeGuideKind.ACADEMIC_UNLOCKED, nextModeGuide(true, false, true))
        assertNull(nextModeGuide(true, true, true))
    }

    @Test fun fullAccessOnFirstLoginDoesNotShowDuplicateGuide() {
        assertEquals(ModeGuideKind.INTRO, nextModeGuide(false, false, true))
        assertNull(nextModeGuide(true, true, true))
    }
}
