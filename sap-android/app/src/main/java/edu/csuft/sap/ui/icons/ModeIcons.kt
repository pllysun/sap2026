package edu.csuft.sap.ui.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.csuft.sap.data.account.AppMode

val AppMode.icon: ImageVector get() = when (this) {
    AppMode.JW -> AppIcons.Academic
    AppMode.WEB -> AppIcons.Web
    AppMode.CLASS -> AppIcons.ClassGroup
}

val AppMode.title: String get() = when (this) {
    AppMode.JW -> "教务课表"
    AppMode.WEB -> "Web 课表"
    AppMode.CLASS -> "班级课表"
}

@Composable
fun ModeIcon(mode: AppMode, modifier: Modifier = Modifier, selected: Boolean = false, size: Dp = 48.dp) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.primary else colors.primary.copy(alpha = 0.065f), tween(240), label = "modeBackground",
    )
    val foreground by animateColorAsState(
        if (selected) colors.onPrimary else colors.primary, tween(240), label = "modeForeground",
    )
    Box(modifier.size(size).background(background, RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        AnimatedAppIcon(mode.icon, selected, Modifier.size(size * 0.52f), tint = foreground)
    }
}
