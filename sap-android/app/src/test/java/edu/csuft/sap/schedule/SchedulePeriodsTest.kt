package edu.csuft.sap.schedule

import edu.csuft.sap.data.schedule.*
import org.junit.Assert.*
import org.junit.Test

class SchedulePeriodsTest {
    private fun course(start: Int, end: Int, day: Int = 1) = DisplayCourse(
        name = "测试课程", teacher = "", location = "", day = day,
        startNode = start, endNode = end, weeks = listOf(1), colorIndex = 0, isCustom = false,
    )

    @Test fun ordinaryAndEmptySchedulesKeepTheCurrentLayout() {
        val settings = ScheduleSettings()
        assertEquals(10, settings.displayPeriodCount(emptyList()))
        assertEquals(10, settings.displayPeriodCount(listOf(course(1, 8), course(9, 10))))
        assertEquals(12, settings.copy(periodsPerDay = 12).displayPeriodCount(listOf(course(1, 2))))
    }

    @Test fun eleventhOrTwelfthPeriodAutomaticallyShowsTheFullLateBlock() {
        val settings = ScheduleSettings()
        for (late in listOf(course(9, 11), course(9, 12), course(11, 11), course(11, 12))) {
            assertEquals(12, settings.displayPeriodCount(listOf(course(1, 2), late)))
        }
    }

    @Test fun lateCourseInAnotherWeekStillExtendsTheWholeTermLayout() {
        val future = course(11, 12).copy(weeks = listOf(17), isThisWeek = false)
        assertEquals(12, ScheduleSettings().displayPeriodCount(listOf(course(1, 2), future)))
    }

    @Test fun changingOrClearingTheScheduleRestoresThePreferenceWithoutOverwritingIt() {
        val settings = ScheduleSettings(rowHeight = 72, cardFontSize = 14)
        assertEquals(12, settings.displayPeriodCount(listOf(course(9, 12))))
        assertEquals(10, settings.displayPeriodCount(listOf(course(5, 6))))
        assertEquals(10, settings.displayPeriodCount(emptyList()))
        assertEquals(ScheduleSettings(rowHeight = 72, cardFontSize = 14), settings)
        assertEquals(16, settings.copy(periodsPerDay = 16).displayPeriodCount(listOf(course(9, 12))))
    }

    @Test fun parsedAndLegacyCachedLateSectionsBothTriggerExpansion() {
        val parsed = CachedCourse("实验课", day = 1, sectionIndex = 5, nodes = SchoolSections.parse("9-12"))
        val legacy = CachedCourse("旧课表", day = 2, sectionIndex = 6)
        val disjoint = parsed.copy(nodes = listOf(1, 2, 11, 12))
        for (cached in listOf(parsed, legacy, disjoint)) {
            val display = cached.nodeRanges().map { course(it.first, it.last, cached.day) }
            assertEquals(12, ScheduleSettings().displayPeriodCount(display))
        }
    }

    @Test fun invalidTimeBlocksCannotInflateTheLayout() {
        val invalid = listOf(course(11, 12, day = 0), course(11, 12, day = 8),
            course(12, 11), course(0, 12), course(9, 99))
        assertEquals(10, ScheduleSettings().displayPeriodCount(invalid))
        assertEquals(12, ScheduleSettings().displayPeriodCount(invalid + course(11, 12)))
    }

    @Test fun supportedLaterPeriodsAndOldMissingPreferencesRemainBounded() {
        assertEquals(10, ScheduleSettings(periodsPerDay = 0).displayPeriodCount(emptyList()))
        assertEquals(12, ScheduleSettings(periodsPerDay = 0).displayPeriodCount(listOf(course(9, 12))))
        assertEquals(14, ScheduleSettings().displayPeriodCount(listOf(course(13, 13))))
        assertEquals(16, ScheduleSettings().displayPeriodCount(listOf(course(15, 16))))
    }
}
