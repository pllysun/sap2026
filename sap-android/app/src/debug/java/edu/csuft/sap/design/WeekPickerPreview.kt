package edu.csuft.sap.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.csuft.sap.ui.schedule.ScheduleWeekState
import edu.csuft.sap.ui.schedule.WeekPickerDialog
import java.time.LocalDate

/** 固定到用户反馈当天，复现开学前、学期中和学期结束后的真实周次弹窗。 */
@Composable
internal fun WeekPickerPreview(screen: String) {
    val start = when (screen) {
        "week-picker-active" -> "2026-08-24"
        "week-picker-past" -> "2026-03-09"
        "week-picker-missing" -> null
        else -> "2026-09-07"
    }
    val state = ScheduleWeekState.from(start, 20, LocalDate.of(2026, 9, 5))
    var selected by rememberSaveable(screen) { mutableIntStateOf(state.defaultWeek) }
    var open by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text("已选：第 $selected 周")
        TextButton(onClick = { open = true }) { Text("选择周次") }
    }
    if (open) WeekPickerDialog(
        state = state,
        selected = selected,
        onPick = { selected = it; open = false },
        onDismiss = { open = false },
    )
}
