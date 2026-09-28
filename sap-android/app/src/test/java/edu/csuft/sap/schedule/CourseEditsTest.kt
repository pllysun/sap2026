package edu.csuft.sap.schedule

import com.google.gson.Gson
import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test

class CourseEditsTest {
    private val monday = CachedCourse("数学", "王老师", "A101", 1, 1, "1-16")
    private val other = monday.copy(day = 3)
    private val profile = ScheduleProfile("p", "课表", ProfileKind.TERM, "2026-2027-1")
    private fun moved(day: Int) = CustomCourse(monday.sourceId(), monday.name, monday.teacher,
        monday.location, day, 1, 2, (1..16).toList())

    @Test fun movingImportedCourseReplacesOriginalAndRepeatedEditsDoNotDuplicate() {
        val result = profile.withCourseEdit(moved(2)).withCourseEdit(moved(5))
        assertEquals(listOf(other), result.visibleBase(listOf(monday, other)))
        assertEquals(listOf(moved(5)), result.customCourses)
        assertEquals(listOf(monday, other), profile.visibleBase(listOf(monday, other)))
    }
    @Test fun deletingOriginalOrEditedCourseDoesNotRestoreOriginal() {
        for (p in listOf(profile, profile.withCourseEdit(moved(2)))) {
            val deleted = p.withCourseDeleted(monday.sourceId())
            assertTrue(deleted.customCourses.isEmpty())
            assertEquals(listOf(other), deleted.visibleBase(listOf(monday, other)))
        }
    }
    @Test fun customCourseDeleteAndLegacyCacheAreSafe() {
        val custom = moved(2).copy(id = "local-uuid")
        val result = profile.withCourseEdit(custom).withCourseDeleted(custom.id)
        assertTrue(result.hiddenSourceIds.orEmpty().isEmpty())
        assertEquals(listOf(monday), result.visibleBase(listOf(monday)))
        val legacy = Gson().fromJson("""{"id":"p","name":"旧课表","kind":"TERM","customCourses":[],"frozenCourses":[]}""", ScheduleProfile::class.java)
        assertEquals(listOf(monday), legacy.visibleBase(listOf(monday)))
    }
    @Test fun persistedEditSurvivesRefreshAndDoesNotHideOtherWeekOrLocation() {
        val p = Gson().fromJson(Gson().toJson(profile.withCourseEdit(moved(2))), ScheduleProfile::class.java)
        val identity = ClassIdentity("院", "年", "专业", "班")
        val key = ScheduleStore.accountStorageKey("owner", identity.account())
        val root = ScheduleRoot(mapOf(key to AccountData(profiles = listOf(p),
            termCourses = mapOf(p.termValue!! to listOf(monday)))))
        val update = ClassRefresh(identity.account(), p.id, p.termValue!!, identity,
            listOf(monday), listOf(other, monday.copy()), null, "revision")
        val data = root.withClassRefresh("owner", listOf(update)).accounts.getValue(key)
        assertEquals(listOf(other), data.profiles.single().visibleBase(data.termCourses[p.termValue]!!))
        assertEquals(listOf(moved(2)), data.profiles.single().customCourses)
        assertNotEquals(monday.sourceId(), monday.copy(weeksRaw = "17").sourceId())
        assertNotEquals(monday.sourceId(), monday.copy(location = "B102").sourceId())
        assertEquals(monday.sourceId(), monday.copy(colorIndex = 4, weeksRaw = "1-8,9-16").sourceId())
    }
}
