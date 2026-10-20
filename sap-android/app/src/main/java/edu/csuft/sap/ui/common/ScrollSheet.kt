package edu.csuft.sap.ui.common

import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import edu.csuft.sap.R

/**
 * 可滚动内容使用独立的底部 Dialog，不让面板拖动和列表滚动争抢同一手势。
 * 复用底部面板的形状、颜色与顶部短线；短线可点击关闭，外部点击与返回键正常关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * 0.92f
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(R.style.ScrollSheetAnimation)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClickLabel = "关闭弹窗", onClick = onDismissRequest,
                    ).semantics { contentDescription = "关闭" },
            )
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(250)) { it },
                exit = slideOutVertically(tween(200)) { it },
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = BottomSheetDefaults.SheetMaxWidth).fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .pointerInput(Unit) { detectTapGestures { /* 面板内部留白不触发遮罩关闭。 */ } },
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.navigationBarsPadding().imePadding()) {
                        Box(
                            Modifier.fillMaxWidth().height(48.dp)
                                .clickable(role = Role.Button, onClick = onDismissRequest)
                                .clearAndSetSemantics {
                                    contentDescription = "关闭弹窗"
                                    role = Role.Button
                                    onClick { onDismissRequest(); true }
                                },
                            contentAlignment = Alignment.Center,
                        ) { BottomSheetDefaults.DragHandle() }
                        content()
                    }
                }
            }
        }
    }
}
