package edu.csuft.sap.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.AppAnnouncementDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.launch

/** 最新公告完整展示在最前，历史公告在下方列表中按需展开。 */
@Composable
fun AnnouncementScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(Graph.announcementRepository.cached()) }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    fun refresh() {
        loading = items.isEmpty()
        error = null
        scope.launch {
            when (val result = Graph.announcementRepository.announcements()) {
                is Outcome.Success -> items = result.data
                is Outcome.Error -> error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsTopBar("课表公告", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "暂无公告", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error != null) {
                        Button(onClick = ::refresh, modifier = Modifier.padding(top = 16.dp)) { Text("重新加载") }
                    }
                }
            }
            else -> Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                Text("最新公告", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                AnnouncementCard(items.first(), expanded = true, onClick = null)

                if (items.size > 1) {
                    Text(
                        "公告列表",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    items.drop(1).forEach { item ->
                        val expanded = expandedId == item.id
                        AnnouncementCard(
                            item = item,
                            expanded = expanded,
                            onClick = { expandedId = if (expanded) null else item.id },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AnnouncementCard(item: AppAnnouncementDto, expanded: Boolean, onClick: (() -> Unit)?) {
    val modifier = Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(16.dp)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Text(if (expanded) "收起" else "查看", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        announcementTime(item)?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 5.dp))
        }
        if (expanded) {
            Text(
                item.content,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

private fun announcementTime(item: AppAnnouncementDto): String? =
    (item.updatedAt ?: item.createdAt)?.takeIf { it.isNotBlank() }
        ?.replace('T', ' ')
        ?.take(16)
