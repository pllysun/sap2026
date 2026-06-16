package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import edu.csuft.sap.ui.icons.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.schedule.Period
import edu.csuft.sap.data.schedule.Periods

/** 节次时间编辑：逐节改起止时间。保存即写入 [Periods]，课表/小组件/提醒随之生效。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTimesScreen(
    initial: List<Period>,
    onSave: (List<Period>) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var list by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (index, isStart)

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSave(list); onBack() }) { Icon(AppIcons.Back, "返回") }
            Text("课表时间设置", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { list = Periods.DEFAULT; onReset() }) { Text("恢复默认") }
        }
        Text(
            "点时间可修改；返回即保存。课表、小组件、上课提醒都会按新时间计算。",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        list.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("第 ${i + 1} 节", fontSize = 15.sp, modifier = Modifier.width(72.dp))
                TimeChip(p.start) { editing = i to true }
                Text("  —  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimeChip(p.end) { editing = i to false }
            }
        }
    }

    editing?.let { (i, isStart) ->
        val initialTime = if (isStart) list[i].start else list[i].end
        TimePickerDialog(
            initial = initialTime,
            onConfirm = { hhmm ->
                list = list.toMutableList().also {
                    it[i] = if (isStart) it[i].copy(start = hhmm) else it[i].copy(end = hhmm)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimeChip(time: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(time, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val parts = initial.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(String.format("%02d:%02d", state.hour, state.minute))
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}
