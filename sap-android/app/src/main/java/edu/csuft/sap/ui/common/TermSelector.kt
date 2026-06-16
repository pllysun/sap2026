package edu.csuft.sap.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.ui.icons.AppIcons

/** 学期选择器（课表/考试/评教/成绩共用）：点 Chip 弹底部面板选学期，含「本学期」标记与选中高亮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermSelector(
    terms: List<TermDto>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (terms.isEmpty()) return
    var showSheet by remember { mutableStateOf(false) }
    val label = terms.firstOrNull { it.value == selected }?.label ?: selected ?: terms.first().label

    Row(modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        AssistChip(
            onClick = { showSheet = true },
            label = { Text(label) },
            trailingIcon = { Icon(AppIcons.DropDown, contentDescription = "选择学期") },
        )
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Text(
                "选择学期",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(terms, key = { it.value }) { term ->
                    TermRow(
                        label = term.label,
                        isSelected = term.value == selected,
                        isCurrent = term.current,
                        onClick = {
                            showSheet = false
                            onSelect(term.value)
                        },
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun TermRow(
    label: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (isCurrent) {
            Spacer(Modifier.width(8.dp))
            Text(
                "本学期",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
