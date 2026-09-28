package edu.csuft.sap.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.ScheduleAppearanceLimits
import edu.csuft.sap.data.schedule.ScheduleSettings
import kotlin.math.abs

/** 正式网格、个性化预览和分享图片共用；自建课程按来源判断，而非是否指定了 ARGB。 */
fun scheduleCourseColor(course: DisplayCourse, settings: ScheduleSettings): CourseColor {
    val base = course.customColor?.let(::customCourseColor) ?: paletteColor(course.colorIndex)
    if (course.isCustom && !settings.colorIntensityAffectsCustom) return base
    return adjustedCourseColor(base, settings)
}

/** 色卡和课表使用相同的浓淡曲线，不把调整后的颜色再次写回原始色值。 */
fun adjustedCourseColor(base: CourseColor, settings: ScheduleSettings): CourseColor {
    val intensity = settings.colorIntensityPercent
    val container = base.container.withCourseIntensity(intensity)
    // 浓色底需要同步保证文字可读；透明度仍由原有设置独立控制。
    val visibleBackground = container.copy(alpha = settings.opacityFraction).compositeOver(PageBg)
    val foreground = if (contrastRatio(base.onContainer, visibleBackground) >= 4.5f) base.onContainer
        else if (contrastRatio(Color.Black, visibleBackground) >= contrastRatio(Color.White, visibleBackground)) Color.Black
        else Color.White
    return CourseColor(container, foreground)
}

/** 新 100% 精确对应旧 165%；0% 为白底，200% 延续加深曲线，保留色相和不透明度。 */
internal fun Color.withCourseIntensity(percent: Int): Color {
    val high = maxOf(red, green, blue)
    val low = minOf(red, green, blue)
    val delta = high - low
    val lightness = (high + low) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
    val hue = if (delta == 0f) 0f else when (high) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.let { (it + 360f) % 360f }
    val amount = percent.coerceIn(ScheduleAppearanceLimits.COLOR_INTENSITY) / 100f *
        (ScheduleAppearanceLimits.LEGACY_COLOR_BASELINE / 100f)
    val adjustedLightness = if (amount < 1f) 1f - (1f - lightness) * amount
        else lightness * (1f - 0.28f * (amount - 1f))
    val adjustedSaturation = if (amount <= 1f) saturation
        else saturation + saturation * (1f - saturation) * (amount - 1f)
    return Color.hsl(hue, adjustedSaturation.coerceIn(0f, 1f), adjustedLightness.coerceIn(0f, 1f), alpha)
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
}
