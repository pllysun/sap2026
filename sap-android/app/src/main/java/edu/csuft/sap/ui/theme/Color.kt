package edu.csuft.sap.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// 强调色（辅色）已迁至 ThemeState：默认天蓝、可在设置里换。此处仅保留简约白底色与中性灰。

// 简约白 + 中性灰
val PageBg = Color(0xFFF6F7F9)
val Surface = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFF1F3F5)
val TextPrimary = Color(0xFF1F2329)
val TextSecondary = Color(0xFF646A73)
val TextTertiary = Color(0xFF9098A1)
val Hairline = Color(0xFFEDEFF2)
val Danger = Color(0xFFE2504A)

/** 课程色卡：每门课按名称稳定取一组（底色, 文字色）。 */
data class CourseColor(val container: Color, val onContainer: Color)

// WakeUp 风格 12 色柔和卡片（底色, 同色系深字）——无绿/青绿系；底色比纯淡再加深一档（约 84% 亮度）
val CoursePalette: List<CourseColor> = listOf(
    CourseColor(Color(0xFFC5DDF7), Color(0xFF2A6BB0)), // 0 blue
    CourseColor(Color(0xFFF8CEBB), Color(0xFFC0552C)), // 1 coral
    CourseColor(Color(0xFFCCD1F0), Color(0xFF45499C)), // 2 indigo
    CourseColor(Color(0xFFD8D0F6), Color(0xFF5A4FBE)), // 3 purple
    CourseColor(Color(0xFFF8DDA6), Color(0xFFB07A12)), // 4 amber
    CourseColor(Color(0xFFF8CADC), Color(0xFFB53E6E)), // 5 pink
    CourseColor(Color(0xFFC7D8EC), Color(0xFF3A6491)), // 6 steel blue
    CourseColor(Color(0xFFF8D6AD), Color(0xFFB16A18)), // 7 apricot
    CourseColor(Color(0xFFF8CCCC), Color(0xFFBD4B4B)), // 8 rose
    CourseColor(Color(0xFFCFD5DF), Color(0xFF4C566B)), // 9 slate
    CourseColor(Color(0xFFDDD3F4), Color(0xFF6A5BC0)), // 10 lavender
    CourseColor(Color(0xFFEFE3A4), Color(0xFF9A7B10)), // 11 gold
)

/** 名称 → 稳定色卡下标（同名课永远同色）。 */
fun colorIndexOf(name: String?): Int {
    val key = name.orEmpty()
    if (key.isEmpty()) return 0
    var h = 0
    for (c in key) h = (h * 31 + c.code) and 0x7fffffff
    return h % CoursePalette.size
}

/** 取色卡：override 优先（自建课指定色），否则按名称稳定取色。 */
fun courseColorOf(name: String?, override: Int? = null): CourseColor {
    val idx = if (override != null && override in CoursePalette.indices) override else colorIndexOf(name)
    return CoursePalette[idx]
}

/** 安全取调色板色卡。 */
fun paletteColor(index: Int): CourseColor =
    CoursePalette[((index % CoursePalette.size) + CoursePalette.size) % CoursePalette.size]

/** 由单个自定义颜色(ARGB)派生课卡色卡：选中色作底，按亮度自动配可读文字色。 */
fun customCourseColor(argb: Long): CourseColor {
    val container = Color(argb)
    val onC = if (container.luminance() > 0.5f)
        Color(container.red * 0.32f, container.green * 0.32f, container.blue * 0.32f, 1f)
    else Color.White
    return CourseColor(container, onC)
}
