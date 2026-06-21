package edu.csuft.sap.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 由「辅色」[accent] 派生整套配色：简约白为底，辅色用于强调（primary）。
 * 容器色＝辅色淡化到接近白；容器上文字＝辅色压暗，保证浅底深字可读。
 */
private fun buildScheme(accent: Color): ColorScheme {
    // 辅色偏亮时（如黄/薄荷）用深字，否则用白字，保证按钮文字对比度。
    val onAccent = if (accent.luminance() > 0.55f) TextPrimary else Color.White
    val container = lerp(accent, Color.White, 0.86f)        // 辅色超淡底（选中态/头像底）
    val onContainer = lerp(accent, Color.Black, 0.28f)      // 容器上的深字
    return lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = lerp(accent, Color.Black, 0.22f),
        background = PageBg,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceMuted,
        onSurfaceVariant = TextSecondary,
        // 弹窗/底部弹出/菜单纯白：M3 默认用 surfaceContainer* + surfaceTint（带淡紫灰高程染色），
        // 全部压成纯白，配灰底页面=纯净「灰底白窗」。AlertDialog/DatePicker 用 High、ModalBottomSheet 用 Low。
        surfaceTint = Color.White,              // 取消高程染色
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,      // ModalBottomSheet（OptionSheet 等）
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,     // AlertDialog / DatePickerDialog
        surfaceContainerHighest = SurfaceMuted, // 文本框/筹码浅灰底，白窗内仍有对比
        outline = TextTertiary,
        outlineVariant = Hairline,
        error = Danger,
        onError = Surface,
    )
}

/**
 * 简约白 + 可换辅色（默认天蓝）。辅色来自 [ThemeState.accent]，在「设置 → 主题色」中
 * 可选预设或取色盘自定义；改后此处随快照状态重组，整 App 强调色实时更新。
 */
@Composable
fun SapTheme(content: @Composable () -> Unit) {
    val accent = ThemeState.accent
    val scheme = remember(accent) { buildScheme(accent) }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}
