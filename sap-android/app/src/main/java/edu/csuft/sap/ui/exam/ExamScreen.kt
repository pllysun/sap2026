package edu.csuft.sap.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.data.remote.dto.ExamDto
import edu.csuft.sap.ui.common.EmptyHint
import edu.csuft.sap.ui.common.ErrorRetry
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SyncBar
import edu.csuft.sap.ui.common.TermSelector
import edu.csuft.sap.ui.theme.courseColorOf

/**
 * 考试安排内容（无独立标题栏，作为「成绩」页的二级 tab 内嵌）。
 * 顶部学期选择器，下方考试卡片列表。
 */
@Composable
fun ExamContent(modifier: Modifier = Modifier, vm: ExamViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SyncBar(state.syncedAt, state.syncing, state.error, vm::sync)
        TermSelector(
            terms = state.terms,
            selected = state.selectedTerm,
            onSelect = vm::selectTerm,
        )
        Box(Modifier.weight(1f)) {
            when {
                state.noAccount -> EmptyHint("请先在「我的」里绑定教务账号")
                state.syncing && state.exams.isEmpty() -> LoadingBox()
                state.error != null && state.exams.isEmpty() && state.terms.isEmpty() -> ErrorRetry(state.error!!, vm::sync)
                state.exams.isEmpty() -> EmptyHint("该学期暂无考试安排")
                else -> ExamList(state.exams)
            }
        }
    }
}

@Composable
private fun ExamList(exams: List<ExamDto>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(exams) { ExamCard(it) }
    }
}

@Composable
private fun ExamCard(e: ExamDto) {
    val color = courseColorOf(e.courseName)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color.container, CircleShape))
            Text(
                e.courseName ?: "",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        e.time?.takeIf { it.isNotBlank() }?.let { InfoLine("时间", it) }
        e.room?.takeIf { it.isNotBlank() }?.let { InfoLine("考场", it) }
        e.seat?.takeIf { it.isNotBlank() }?.let { InfoLine("座位", it) }
        e.admissionTicket?.takeIf { it.isNotBlank() }?.let { InfoLine("准考证", it) }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
