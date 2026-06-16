package edu.csuft.sap.ui.grade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.ui.common.EmptyHint
import edu.csuft.sap.ui.common.ErrorRetry
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.common.ScreenHeader
import edu.csuft.sap.ui.common.SyncBar
import edu.csuft.sap.ui.eval.EvalContent
import edu.csuft.sap.ui.exam.ExamContent

/** 成绩入口：顶部二级 tab 在「成绩」与「考试安排」间切换。 */
@Composable
fun GradeScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader("成绩")
        Segmented(tab, listOf("成绩", "考试", "评教")) { tab = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> GradesContent()
                1 -> ExamContent()
                else -> EvalContent()
            }
        }
    }
}

@Composable
private fun Segmented(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f)
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GradesContent(vm: GradeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SyncBar(state.syncedAt, state.syncing, state.error, vm::sync)
        Box(Modifier.weight(1f)) {
            when {
                state.noAccount -> EmptyHint("请先在「我的」里绑定教务账号")
                state.syncing && state.grades.isEmpty() -> LoadingBox()
                state.error != null && state.grades.isEmpty() -> ErrorRetry(state.error!!, vm::sync)
                state.grades.isEmpty() -> EmptyHint("暂无成绩，点右上「同步」获取")
                else -> Column(Modifier.fillMaxSize()) {
                    Segmented(sub, listOf("列表", "分析")) { sub = it }
                    Box(Modifier.weight(1f)) {
                        when (sub) {
                            0 -> GradeList(state.grades)
                            else -> GradeAnalysisPage(state.grades)
                        }
                    }
                }
            }
        }
    }
}

/** 分析页：可滚动容器包住成绩分析区。 */
@Composable
private fun GradeAnalysisPage(grades: List<GradeDto>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { GradeAnalysis(grades) }
    }
}

@Composable
private fun GradeList(grades: List<GradeDto>) {
    val totalCredit = grades.sumOf { it.credit?.toDoubleOrNull() ?: 0.0 }
    val gpaPairs = grades.mapNotNull {
        val c = it.credit?.toDoubleOrNull(); val g = it.gradePoint?.toDoubleOrNull()
        if (c != null && g != null && c > 0) c to g else null
    }
    val gpa = if (gpaPairs.isNotEmpty())
        gpaPairs.sumOf { it.first * it.second } / gpaPairs.sumOf { it.first } else 0.0
    val grouped = grades.groupBy { it.term ?: "" }.toList().sortedByDescending { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SapCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Stat("课程", grades.size.toString())
                    Stat("总学分", trim(totalCredit))
                    Stat("平均绩点", String.format("%.2f", gpa))
                }
            }
        }
        grouped.forEach { (term, list) ->
            item {
                Text(
                    text = term.ifBlank { "其它" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, bottom = 2.dp),
                )
            }
            items(list) { GradeRow(it) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun GradeRow(g: GradeDto) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(g.courseName ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                text = listOfNotNull(
                    g.credit?.let { "学分 $it" },
                    g.gradePoint?.takeIf { it.isNotBlank() }?.let { "绩点 $it" },
                    g.courseAttr,
                ).joinToString("  ·  "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            text = g.score ?: "",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (isFail(g.score)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun isFail(score: String?): Boolean {
    if (score == null) return false
    score.toDoubleOrNull()?.let { return it < 60 }
    return score.contains("不及格") || score.contains("不合格")
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
