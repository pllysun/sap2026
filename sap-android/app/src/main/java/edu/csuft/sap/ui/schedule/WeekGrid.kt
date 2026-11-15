package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WeekGrid(maxWeeks: Int, selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val currentSelection by rememberUpdatedState(selected)
    val changeSelection by rememberUpdatedState(onChange)
    val weekCoordinates = remember(maxWeeks) { mutableMapOf<Int, LayoutCoordinates>() }
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { gridCoordinates = it }
            .pointerInput(maxWeeks) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val grid = gridCoordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    val cells = weekCoordinates.mapNotNull { (week, coordinates) ->
                        if (coordinates.isAttached) week to grid.localBoundingBoxOf(coordinates, clipBounds = false)
                        else null
                    }
                    val startWeek = cells.firstOrNull { (_, bounds) -> bounds.contains(down.position) }
                        ?.first ?: return@awaitEachGesture
                    // 横向起手选择周次，竖向起手交给编辑页滚动。
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture

                    val selection = currentSelection.toMutableSet()
                    val select = startWeek !in selection
                    var previous = down.position
                    fun applyStroke(position: Offset) {
                        var changed = false
                        for ((week, bounds) in cells) {
                            if (!bounds.crosses(previous, position)) continue
                            val updated = if (select) selection.add(week) else selection.remove(week)
                            if (updated) changed = true
                        }
                        if (changed) changeSelection(selection.toSet())
                        previous = position
                    }

                    applyStroke(start.position)
                    drag(start.id) { change ->
                        applyStroke(change.position)
                        change.consume()
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..maxWeeks).chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { week ->
                    val checked = week in selected
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .onGloballyPositioned { weekCoordinates[week] = it }
                            .background(
                                if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(9.dp),
                            )
                            .toggleable(value = checked, role = Role.Checkbox) { select ->
                                onChange(if (select) selected + week else selected - week)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$week", fontSize = 13.sp,
                            color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 手指移动较快时，命中两次触摸事件之间经过的格子。 */
private fun Rect.crosses(from: Offset, to: Offset): Boolean {
    if (contains(from) || contains(to)) return true
    var enter = 0f
    var leave = 1f
    fun crossesAxis(start: Float, end: Float, min: Float, max: Float): Boolean {
        val distance = end - start
        if (distance == 0f) return start >= min && start < max
        val a = (min - start) / distance
        val b = (max - start) / distance
        enter = maxOf(enter, minOf(a, b))
        leave = minOf(leave, maxOf(a, b))
        return enter < leave
    }
    return crossesAxis(from.x, to.x, left, right) && crossesAxis(from.y, to.y, top, bottom)
}
