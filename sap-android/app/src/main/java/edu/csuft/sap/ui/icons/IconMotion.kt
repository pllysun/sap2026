package edu.csuft.sap.ui.icons

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.csuft.sap.R
import kotlin.math.sin

/** Selection feedback is finite. Compose honors Android's animation duration scale. */
@Composable
fun AnimatedAppIcon(
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
    description: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(240), label = "iconSelection")
    Box(
        modifier.size(24.dp)
            .semantics { description?.let { contentDescription = it } }
            .graphicsLayer {
                val lift = sin(progress * Math.PI).toFloat()
                scaleX = 1f + lift * 0.08f
                scaleY = scaleX
                translationY = -lift * 2.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon == selectedIcon) {
            Icon(icon, null, Modifier.matchParentSize(), tint = tint)
        } else {
            Icon(icon, null, Modifier.matchParentSize().graphicsLayer { alpha = 1f - progress }, tint = tint)
            Icon(selectedIcon, null, Modifier.matchParentSize().graphicsLayer { alpha = progress }, tint = tint)
        }
    }
}

/** An infinite animation exists only while a request is actually running. */
@Composable
fun SyncIcon(
    running: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
) {
    val angle = if (running && ValueAnimator.areAnimatorsEnabled()) {
        val transition = rememberInfiniteTransition(label = "sync")
        val rotation by transition.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(1200, easing = LinearEasing)),
            label = "syncRotation",
        )
        rotation
    } else 0f
    Icon(
        AppIcons.Refresh, description, tint = tint,
        modifier = modifier.size(24.dp)
            .semantics {
                if (running) {
                    stateDescription = "正在加载"
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            }
            .graphicsLayer { rotationZ = angle },
    )
}

@Composable
fun AppLogo(modifier: Modifier = Modifier, description: String? = "软协课表") {
    Image(painterResource(R.drawable.app_logo), description, modifier.size(48.dp))
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.07f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun ChevronIcon(modifier: Modifier = Modifier) {
    Icon(AppIcons.ChevronRight, null, modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
}
