package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test

class ClassRefreshTest {
    private val identity = ClassIdentity("学院", "2025", "专业", "一班")
    private val oldCourse = CachedCourse("数学", day = 1, sectionIndex = 1)
    private val newCourse = oldCourse.copy(teacher = "新教师", location = "新教室")
    private val custom = CustomCourse("custom", "自建", day = 2, startNode = 3, endNode = 4)
    private val p = ScheduleProfile("p", "我的班级", ProfileKind.TERM, "2026-2027-1",
        ScheduleSettings(semesterStartDate = "2026-09-07", semesterStartDateManual = true), listOf(custom))
    private val data = AccountData(activeProfileId = "frozen", profiles = listOf(p,
        p.copy(id = "frozen", kind = ProfileKind.CUSTOM, frozenCourses = listOf(oldCourse))),
        termCourses = mapOf(p.termValue!! to listOf(oldCourse)))
    private fun update() = ClassRefresh(identity.account(), p.id, p.termValue!!, identity,
        listOf(oldCourse), listOf(newCourse), "2026-09-14", "v2")
    private fun key(owner: String = "1") = ScheduleStore.accountStorageKey(owner, identity.account())

    @Test fun batchPreservesSelectionCustomCoursesManualDatesAndOtherUsers() {
        val root = ScheduleRoot(mapOf(key() to data, key("2") to data))
        val next = root.withClassRefresh("1", listOf(update()))
        val changed = next.accounts.getValue(key())
        assertEquals("frozen", changed.activeProfileId)
        assertEquals(p, changed.profiles.first())
        assertEquals(data.profiles.last(), changed.profiles.last())
        assertEquals(listOf(newCourse), changed.termCourses[p.termValue])
        assertEquals(data, next.accounts[key("2")])
        assertEquals(identity, changed.classIdentity)
        assertEquals("v2", changed.classRevisions?.get(p.termValue))
    }
    @Test fun updatesAutomaticDateAndEmptyCourseList() {
        val auto = data.copy(profiles = listOf(p.copy(settings = p.settings.copy(semesterStartDateManual = false))))
        val changed = ScheduleRoot(mapOf(key() to auto)).withClassRefresh("1", listOf(update().copy(courses = emptyList())))
            .accounts.getValue(key())
        assertEquals("2026-09-14", changed.profiles.first().settings.semesterStartDate)
        assertTrue(changed.termCourses[p.termValue]!!.isEmpty())
        assertEquals(listOf(custom), changed.profiles.first().customCourses)
    }
    @Test fun deletedOrManuallyReDownloadedCacheIsNotOverwritten() {
        assertEquals(ScheduleRoot(), ScheduleRoot().withClassRefresh("1", listOf(update())))
        val root = ScheduleRoot(mapOf(key() to data.copy(termCourses = mapOf(p.termValue!! to listOf(newCourse)))))
        assertEquals(root, root.withClassRefresh("1", listOf(update())))
        val deleted = ScheduleRoot(mapOf(key() to data.copy(profiles = emptyList())))
        assertEquals(deleted, deleted.withClassRefresh("1", listOf(update())))
    }
    @Test fun classIdentityIsStableAndSeparatesGradeAndCollege() {
        assertEquals(identity.key(), identity.copy().key())
        assertNotEquals(identity.key(), identity.copy(grade = "2024").key())
        assertNotEquals(identity.key(), identity.copy(college = "另一学院").key())
    }
}
