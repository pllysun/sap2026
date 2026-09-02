package edu.csuft.sap.share

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleShareRendererTest {

    @Test
    fun createsANewWeekSpecificPathForEveryShare() {
        val directory = File("share")

        val first = newShareImageFile(directory, week = 14)
        val sameWeekAgain = newShareImageFile(directory, week = 14)
        val nextWeek = newShareImageFile(directory, week = 15)

        assertTrue(first.name.startsWith("schedule-week-14-"))
        assertTrue(sameWeekAgain.name.startsWith("schedule-week-14-"))
        assertTrue(nextWeek.name.startsWith("schedule-week-15-"))
        assertTrue(first.absolutePath != sameWeekAgain.absolutePath)
        assertTrue(first.absolutePath != nextWeek.absolutePath)
    }

    @Test
    fun removesOnlyExpiredGeneratedShareImages() {
        val directory = Files.createTempDirectory("schedule-share-test").toFile()
        try {
            val now = 2L * 24L * 60L * 60L * 1000L
            val expired = File(directory, "schedule-week-14-old.png").apply {
                writeText("old")
                setLastModified(1L)
            }
            val recent = File(directory, "schedule-week-15-new.png").apply {
                writeText("new")
                setLastModified(now - 1_000L)
            }
            val unrelated = File(directory, "avatar.png").apply {
                writeText("avatar")
                setLastModified(1L)
            }

            removeExpiredShareImages(directory, now)

            assertFalse(expired.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
