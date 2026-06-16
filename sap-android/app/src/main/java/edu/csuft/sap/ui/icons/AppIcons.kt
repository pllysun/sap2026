package edu.csuft.sap.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自定义图标集（线性描边圆角风，24dp）。
 * 路径与设计稿同一套 SVG `d` 字符串，经 [PathParser] 转成矢量节点。
 * 颜色不写死，统一由 `Icon(tint=...)` 着色（描边/填充都会被 tint 覆盖）。
 */
object AppIcons {

    private fun build(
        name: String,
        strokes: List<String> = emptyList(),
        fills: List<String> = emptyList(),
        strokeWidth: Float = 2f,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        strokes.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        fills.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()

    /** 课表：日历 + 高亮课卡 */
    val Schedule: ImageVector by lazy {
        build(
            "schedule",
            strokes = listOf(
                "M8 2.5 V6", "M16 2.5 V6",
                "M5 4.5 H19 A2 2 0 0 1 21 6.5 V19 A2 2 0 0 1 19 21 H5 A2 2 0 0 1 3 19 V6.5 A2 2 0 0 1 5 4.5 Z",
                "M3 9.5 H21",
            ),
            fills = listOf("M7.3 12.3 H11 A0.9 0.9 0 0 1 11.9 13.2 V16 A0.9 0.9 0 0 1 11 16.9 H7.3 A0.9 0.9 0 0 1 6.4 16 V13.2 A0.9 0.9 0 0 1 7.3 12.3 Z"),
        )
    }

    /** 成绩：奖章 + 对勾 */
    val Grades: ImageVector by lazy {
        build(
            "grades",
            strokes = listOf(
                "M9 3 L11.2 9", "M15 3 L12.8 9",
                "M7 14.5 A5 5 0 1 0 17 14.5 A5 5 0 1 0 7 14.5 Z",
                "M9.9 14.7 L11.3 16.1 L14.1 13.2",
            ),
        )
    }

    /** 我的：圆头 + 肩 */
    val Profile: ImageVector by lazy {
        build(
            "profile",
            strokes = listOf(
                "M8.8 7.8 A3.2 3.2 0 1 0 15.2 7.8 A3.2 3.2 0 1 0 8.8 7.8 Z",
                "M5.5 20 A6.5 6.5 0 0 1 18.5 20",
            ),
        )
    }

    /** 设置：滑杆 */
    val Settings: ImageVector by lazy {
        build(
            "settings",
            strokes = listOf("M4 7 H20", "M4 12 H20", "M4 17 H20"),
            fills = listOf(
                "M8 7 A2 2 0 1 0 12 7 A2 2 0 1 0 8 7 Z",
                "M13 12 A2 2 0 1 0 17 12 A2 2 0 1 0 13 12 Z",
                "M5 17 A2 2 0 1 0 9 17 A2 2 0 1 0 5 17 Z",
            ),
        )
    }

    /** 加号 */
    val Add: ImageVector by lazy {
        build("add", strokes = listOf("M12 5 V19", "M5 12 H19"))
    }

    /** 分享 */
    val Share: ImageVector by lazy {
        build(
            "share",
            strokes = listOf(
                "M3.7 12 A2.3 2.3 0 1 0 8.3 12 A2.3 2.3 0 1 0 3.7 12 Z",
                "M15.2 6 A2.3 2.3 0 1 0 19.8 6 A2.3 2.3 0 1 0 15.2 6 Z",
                "M15.2 18 A2.3 2.3 0 1 0 19.8 18 A2.3 2.3 0 1 0 15.2 18 Z",
                "M7.9 10.9 L15.6 7.1", "M7.9 13.1 L15.6 16.9",
            ),
        )
    }

    /** 刷新/同步：环形箭头 */
    val Refresh: ImageVector by lazy {
        build(
            "refresh",
            strokes = listOf(
                "M19 8 A7 7 0 1 0 20 12.8",
                "M19 3.6 L19 8 L14.6 8",
            ),
        )
    }

    /** 对勾 */
    val Check: ImageVector by lazy {
        build("check", strokes = listOf("M5 12.5 L10 17.5 L19 7"))
    }

    /** 下拉箭头 */
    val DropDown: ImageVector by lazy {
        build("dropdown", strokes = listOf("M7 10 L12 15 L17 10"))
    }

    /** 返回 */
    val Back: ImageVector by lazy {
        build("back", strokes = listOf("M19 12 H5", "M11 6 L5 12 L11 18"))
    }
}
