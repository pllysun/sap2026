package edu.csuft.sap.ui.home

import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.schedule.AccountData
import edu.csuft.sap.data.schedule.ProfileKind
import edu.csuft.sap.data.schedule.ScheduleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DowngradeSnapshotSourceTest {

    @Test
    fun activeJwAccountIsAlwaysUsedForCurrentDowngrade() {
        assertEquals("20225313", downgradeSnapshotSource("20225313", "20220001", AccountData(scanned = true)))
    }

    @Test
    fun virginWebSpaceRecoversLastJwCacheFromOlderBuggyVersion() {
        assertEquals(
            "20225313",
            downgradeSnapshotSource(AccountManager.WEBVIEW_ACCOUNT, "20225313", AccountData()),
        )
    }

    @Test
    fun intentionallyDeletedWebSpaceIsNotRepopulated() {
        assertNull(
            downgradeSnapshotSource(
                AccountManager.WEBVIEW_ACCOUNT,
                "20225313",
                AccountData(scanned = true),
            ),
        )
    }

    @Test
    fun existingWebScheduleIsNotOverwrittenByCompatibilityRecovery() {
        val web = AccountData(
            profiles = listOf(
                ScheduleProfile("webview:term", "本地课表", ProfileKind.TERM, "2025-2026-1"),
            ),
            scanned = true,
        )
        assertNull(downgradeSnapshotSource(AccountManager.WEBVIEW_ACCOUNT, "20225313", web))
    }
}
