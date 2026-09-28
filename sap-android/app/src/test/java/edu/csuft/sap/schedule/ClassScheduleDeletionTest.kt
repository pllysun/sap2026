package edu.csuft.sap.schedule

import com.google.gson.Gson
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.classSelectionAfterDeletion
import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test

class ClassScheduleDeletionTest {
    private val a = "${AccountManager.CLASS_ACCOUNT_PREFIX}a"
    private val b = "${AccountManager.CLASS_ACCOUNT_PREFIX}b"
    private val c = "${AccountManager.CLASS_ACCOUNT_PREFIX}c"
    private fun key(member: String, account: String) = ScheduleStore.accountStorageKey(member, account)
    private val data = AccountData(
        activeProfileId = "new",
        profiles = listOf("old", "new").map {
            ScheduleProfile(it, it, ProfileKind.TERM, termValue = it,
                customCourses = listOf(CustomCourse("custom-$it", "本地课", day = 2, startNode = 3, endNode = 4)))
        } + ScheduleProfile("copy", "副本", ProfileKind.CUSTOM,
            frozenCourses = listOf(CachedCourse("冻结课程", day = 1, sectionIndex = 1))),
        termCourses = mapOf("old" to listOf(CachedCourse("旧课", day = 1, sectionIndex = 1)), "new" to emptyList()),
        termRemarks = mapOf("old" to listOf(Remark("实习"))), scanned = true,
    )

    @Test fun batchDeletionRemovesWholeSlotsAndSurvivesPersistence() {
        val source = ScheduleRoot(mapOf(key("user", a) to data, key("user", b) to data, key("user", c) to data))
        val deleted = source.withoutClassAccounts("user", setOf(a, b))
        val restored = Gson().fromJson(Gson().toJson(deleted), ScheduleRoot::class.java)
        assertEquals(setOf(key("user", c)), restored.accounts.keys)
        assertEquals(data, restored.accounts[key("user", c)])
    }

    @Test fun deletionIsIsolatedFromOtherMembersAndModes() {
        val display = ScheduleDisplaySettings(showWeekend = true)
        val source = ScheduleRoot(mapOf(key("user", a) to data, key("other", a) to data,
            key("user", AccountManager.WEBVIEW_ACCOUNT) to data, key("user", "student") to data), display)
        val deleted = source.withoutClassAccounts("user", setOf(a, AccountManager.WEBVIEW_ACCOUNT, "student"))
        assertFalse(deleted.accounts.containsKey(key("user", a)))
        assertEquals(source.accounts - key("user", a), deleted.accounts)
        assertEquals(display, deleted.displaySettings)
    }

    @Test fun missingAndEmptySelectionsDoNotChangeData() {
        val source = ScheduleRoot(mapOf(key("user", a) to data))
        assertEquals(source, source.withoutClassAccounts("user", emptySet()))
        assertEquals(source, source.withoutClassAccounts("user", setOf(b)))
    }

    @Test fun deletingActiveClassSelectsRemainingClass() {
        val next = classSelectionAfterDeletion(a, a, setOf(a, b), listOf(c))
        assertEquals(c, next.active)
        assertEquals(c, next.last)
    }

    @Test fun deletingAllClassesClearsLastSelection() {
        val next = classSelectionAfterDeletion(a, a, setOf(a, b), emptyList())
        assertEquals(AccountManager.DEFAULT_CLASS_ACCOUNT, next.active)
        assertNull(next.last)
    }

    @Test fun deletingInactiveClassKeepsCurrentSelection() {
        val next = classSelectionAfterDeletion(a, a, setOf(b), listOf(a, c))
        assertEquals(a, next.active)
        assertEquals(a, next.last)
    }

    @Test fun deletingRememberedClassDoesNotSwitchOtherMode() {
        val next = classSelectionAfterDeletion(AccountManager.WEBVIEW_ACCOUNT, a, setOf(a), listOf(b))
        assertEquals(AccountManager.WEBVIEW_ACCOUNT, next.active)
        assertEquals(b, next.last)
        assertNull(classSelectionAfterDeletion("student", a, setOf(a), emptyList()).last)
    }

    @Test fun staleDefaultIsNotRememberedAsARealClass() {
        val next = classSelectionAfterDeletion(AccountManager.DEFAULT_CLASS_ACCOUNT,
            AccountManager.DEFAULT_CLASS_ACCOUNT, setOf(b), emptyList())
        assertEquals(AccountManager.DEFAULT_CLASS_ACCOUNT, next.active)
        assertNull(next.last)
    }
}
