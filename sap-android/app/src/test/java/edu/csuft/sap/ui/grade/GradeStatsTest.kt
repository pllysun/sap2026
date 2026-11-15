package edu.csuft.sap.ui.grade

import edu.csuft.sap.data.remote.dto.GradeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeStatsTest {
    @Test
    fun termAverageDistinguishesZeroFromAnUnavailableScore() {
        val stats = computeGradeStats(
            listOf(
                GradeDto(term = "2025-2026-1", score = "0", credit = "3", gradePoint = "0"),
                GradeDto(term = "2025-2026-2", score = "合格", credit = "2", gradePoint = "2"),
                GradeDto(term = "2025-2026-2", score = "NaN", credit = "1"),
            ),
        )

        assertEquals(0.0, stats.terms[0].avgScore!!, 0.001)
        assertNull(stats.terms[1].avgScore)
        assertEquals(1, stats.scoredCourses)
    }

    @Test
    fun termAverageUsesOnlyNumericalScoresAndKeepsCreditWeighting() {
        val stats = computeGradeStats(
            listOf(
                GradeDto(term = "2025-2026-1", score = "80", credit = "3"),
                GradeDto(term = "2025-2026-1", score = "100", credit = "1"),
                GradeDto(term = "2025-2026-1", score = "优秀", credit = "4"),
                GradeDto(term = "2025-2026-2", score = "70"),
                GradeDto(term = "2025-2026-2", score = "90"),
            ),
        )

        assertEquals(85.0, stats.terms[0].avgScore!!, 0.001)
        assertEquals(80.0, stats.terms[1].avgScore!!, 0.001)
    }
}
