package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.WeekUtil
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.theme.paletteColor

/**
 * 课程详情底部弹窗（WakeUp 风格）：列出点中格子的全部课程（含灰显的「非本周」），
 * 自建课带「编辑」，教务课带「教务」标签。右上「+」在该格新增自建课。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    courses: List<DisplayCourse>,
    onEdit: (DisplayCourse) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp).navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("课程详情", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(
                    AppIcons.Add, "在该格新增课程",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .padding(2.dp)
                        .clickable { onAdd() },
                )
            }
            courses.forEach { c -> CourseDetailItem(c, onEdit) }
        }
    }
}

@Composable
private fun CourseDetailItem(course: DisplayCourse, onEdit: (DisplayCourse) -> Unit) {
    val color = paletteColor(course.colorIndex)
    Row(
        Modifier.fillMaxWidth().clickable { onEdit(course) }.padding(top = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(color.onContainer, CircleShape))
                Text(
                    course.name,
                    fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (!course.isThisWeek) {
                    Text("（非本周）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                secondLine(course),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            val third = listOf(course.location, course.teacher).filter { it.isNotBlank() }.joinToString(" ｜ ")
            if (third.isNotBlank()) {
                Text(third, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (!course.isCustom) {
            Box(
                Modifier.padding(top = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("教务", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // 任意课都可编辑：教务课会反填进"添加课程"表单(保存即生成自建课覆盖该时段)
        TextButton(onClick = { onEdit(course) }) { Text("编辑") }
    }
}

private fun secondLine(c: DisplayCourse): String {
    val weeks = c.weeksLabel?.takeIf { it.isNotBlank() } ?: WeekUtil.formatWeeks(c.weeks)
    val sec = "第${c.startNode}-${c.endNode}节"
    val start = Periods.period(c.startNode)?.start
    val end = Periods.period(c.endNode)?.end
    val time = if (start != null && end != null) "（$start-$end）" else ""
    return listOf(weeks, "$sec$time").filter { it.isNotBlank() }.joinToString(" ｜ ")
}
