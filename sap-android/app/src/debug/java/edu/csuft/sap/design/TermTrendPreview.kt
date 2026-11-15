package edu.csuft.sap.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.ui.grade.GradeAnalysis

@Composable
internal fun TermTrendPreview(manyTerms: Boolean, missingAverage: Boolean) {
    val grades = remember(manyTerms, missingAverage) {
        val terms = if (manyTerms) {
            (2022..2025).flatMap { year -> listOf("$year-${year + 1}-1", "$year-${year + 1}-2") }
        } else {
            listOf("2025-2026-1", "2025-2026-2", "2026-2027-1")
        }
        val scores = listOf("65.6", "75.4", "93.5")
        val gradePoints = listOf("1.57", "2.39", "4.50")
        terms.mapIndexed { index, term ->
            GradeDto(
                term = term,
                courseName = "示例课程 ${index + 1}",
                score = if (missingAverage && index == 1) "合格" else scores[index % scores.size],
                credit = "3",
                gradePoint = gradePoints[index % gradePoints.size],
            )
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        GradeAnalysis(grades)
    }
}
