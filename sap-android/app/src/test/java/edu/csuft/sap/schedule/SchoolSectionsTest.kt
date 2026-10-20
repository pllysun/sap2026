package edu.csuft.sap.schedule

import com.google.gson.Gson
import edu.csuft.sap.data.remote.dto.CourseDto
import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test

class SchoolSectionsTest {
    @Test fun oldClientsCannotSuppressMetadataRefreshBySavingANewerServerRevision() {
        val revision = "a".repeat(64)
        val old = CachedCourse("实验课", day=6, sectionIndex=1, weeksRaw="6-7周")
        assertNull(cachedClassRevision(listOf(old),revision))
        assertEquals(revision,cachedClassRevision(listOf(old.copy(nodes=(1..8).toList())),revision))
        assertEquals(revision,cachedClassRevision(emptyList(),revision))
        assertNull(cachedClassRevision(listOf(old.copy(nodes=listOf(1,2))),null))
    }

    @Test fun actualNodesSurviveSerializationAndExtendBeyondTheFirstBigSection() {
        val source = CachedCourse("连续实验课", day = 6, sectionIndex = 1, weeksRaw = "6-7周", nodes = (1..8).toList())
        val cached = Gson().fromJson(Gson().toJson(source), CachedCourse::class.java)
        assertEquals(listOf(1..8), cached.nodeRanges())
        val dto = Gson().fromJson("""{"name":"实验课","day":6,"sectionIndex":1,"nodes":[1,2,3,4,5,6,7,8]}""", CourseDto::class.java)
        assertEquals((1..8).toList(), dto.nodes)
        val legacy = Gson().fromJson("""{"name":"旧课","day":1,"sectionIndex":3}""", CachedCourse::class.java)
        assertEquals(listOf(5..6), legacy.nodeRanges())
        assertEquals(listOf(1..2,5..5,7..8), source.copy(nodes = listOf(8,7,5,2,1)).nodeRanges())
        assertEquals(listOf(1..2),source.copy(nodes = listOf(0,99)).nodeRanges())
    }

    @Test fun supportsSchoolSectionFormatsAndRejectsBrokenRanges() {
        for (raw in listOf("1-8", "第１～８节", "[1至8节]")) assertEquals((1..8).toList(), SchoolSections.parse(raw))
        assertEquals(listOf(1,2,5,7,8),SchoolSections.parse("1-2、5、7-8"))
        assertEquals(listOf(5,6),SchoolSections.fromRow("第3节",3))
        for (raw in listOf("", "待定", "0-8", "1-999", "8-1", "1,", "1-3foo")) assertTrue(raw,SchoolSections.parse(raw).isEmpty())
    }

    @Test fun upgradedLongCoursesKeepOldLocalEditsAndNewArrangementsHaveDistinctIds() {
        val old = CachedCourse("实验课", day=6, sectionIndex=1, weeksRaw="6-7周")
        val upgraded = old.copy(nodes=(1..8).toList())
        assertNotEquals(old.sourceId(),upgraded.sourceId())
        val edited=ScheduleProfile("p","课表",ProfileKind.TERM,hiddenSourceIds=setOf(old.sourceId()))
        assertTrue(edited.visibleBase(listOf(upgraded)).isEmpty())
        assertNotEquals(upgraded.sourceId(),upgraded.copy(nodes=(1..4).toList()).sourceId())
        assertEquals(old.sourceId(),old.copy(nodes=listOf(1,2)).sourceId())
    }
}
