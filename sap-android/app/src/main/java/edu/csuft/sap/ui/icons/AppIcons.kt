package edu.csuft.sap.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
        // 偶奇填充：单条路径里「外形 + 镂空子路径」共存，实心图标用它抠出内部细节
        // （选中态被单色 tint 后，同色内描边不可见，必须靠镂空/负空间保留语义）
        evenOddFills: List<String> = emptyList(),
        strokeWidth: Float = 2f,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        fills.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
        evenOddFills.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
        }
        strokes.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    // ─── 底部 Tab 图标（线性=未选中 / 实心=选中，一套精准线性语言，24dp）───
    // 设计经多方案评审收敛：课表/成绩/我的用 Crisp Linear，设置用 Soft Rounded 双滑杆。
    // 选中态用实心剪影 + 偶奇镂空保留语义（日历表头/课卡格、柱状图柱、头肩、滑块旋钮）。

    /** 课表（未选中）：日历线框 + 表头线 + 装订环 + 5 个课卡点 */
    val Schedule: ImageVector by lazy {
        build(
            "schedule",
            strokes = listOf(
                "M5.5 5.5 L18.5 5.5 A2 2 0 0 1 20.5 7.5 L20.5 19 A2 2 0 0 1 18.5 21 L5.5 21 A2 2 0 0 1 3.5 19 L3.5 7.5 A2 2 0 0 1 5.5 5.5 Z",
                "M3.5 9.5 L20.5 9.5",
                "M8 3.2 L8 6.3", "M16 3.2 L16 6.3",
                "M7 13 L9 13", "M11 13 L13 13", "M15 13 L17 13",
                "M7 17 L9 17", "M11 17 L13 17",
            ),
        )
    }

    /** 课表（选中）：实心日历，偶奇镂空出表头条与课卡格，装订环描边在上 */
    val ScheduleFilled: ImageVector by lazy {
        build(
            "schedule_filled",
            strokes = listOf("M8 3.2 L8 6", "M16 3.2 L16 6"),
            evenOddFills = listOf(
                "M5.5 5.5 L18.5 5.5 A2 2 0 0 1 20.5 7.5 L20.5 19 A2 2 0 0 1 18.5 21 L5.5 21 A2 2 0 0 1 3.5 19 L3.5 7.5 A2 2 0 0 1 5.5 5.5 Z " +
                    "M5 8.8 L19 8.8 L19 9.6 L5 9.6 Z " +
                    "M6.7 12.2 L8.7 12.2 L8.7 14 L6.7 14 Z M11 12.2 L13 12.2 L13 14 L11 14 Z M15.3 12.2 L17.3 12.2 L17.3 14 L15.3 14 Z " +
                    "M6.7 16 L8.7 16 L8.7 17.8 L6.7 17.8 Z M11 16 L13 16 L13 17.8 L11 17.8 Z",
            ),
        )
    }

    /** 成绩（未选中）：基线 + 三根升序柱（圆头） */
    val Grades: ImageVector by lazy {
        build(
            "grades",
            strokes = listOf(
                "M3.8 20 L20.2 20",
                "M7 20 L7 14.5", "M12 20 L12 10.5", "M17 20 L17 6.5",
            ),
        )
    }

    /** 成绩（选中）：基线 + 三根实心圆角升序柱 */
    val GradesFilled: ImageVector by lazy {
        build(
            "grades_filled",
            strokes = listOf("M3.8 20.2 L20.2 20.2"),
            fills = listOf(
                "M5.6 20 L5.6 16 A1.4 1.4 0 0 1 8.4 16 L8.4 20 Z",
                "M10.6 20 L10.6 11.5 A1.4 1.4 0 0 1 13.4 11.5 L13.4 20 Z",
                "M15.6 20 L15.6 7 A1.4 1.4 0 0 1 18.4 7 L18.4 20 Z",
            ),
        )
    }

    /** 我的（未选中）：圆头 + 肩弧 */
    val Profile: ImageVector by lazy {
        build(
            "profile",
            strokes = listOf(
                "M8.6 8.5 A3.4 3.4 0 1 0 15.4 8.5 A3.4 3.4 0 1 0 8.6 8.5 Z",
                "M5.2 19.6 A6.8 6.8 0 0 1 18.8 19.6",
            ),
        )
    }

    /** 我的（选中）：实心头 + 实心肩穹 */
    val ProfileFilled: ImageVector by lazy {
        build(
            "profile_filled",
            fills = listOf(
                "M8.3 8 A3.7 3.7 0 1 0 15.7 8 A3.7 3.7 0 1 0 8.3 8 Z",
                "M4.8 20 A7.2 6.2 0 0 1 19.2 20 Z",
            ),
        )
    }

    // 8 齿齿轮（齿尖 R9 / 齿谷 R6.5，余弦定点，bbox≈[3,21]）
    private const val GEAR_COG =
        "M 18.3 10.5 L 20.9 10.6 L 20.9 13.4 L 18.3 13.5 L 17.5 15.4 L 19.3 17.3 L 17.3 19.3 L 15.4 17.5 " +
            "L 13.5 18.3 L 13.4 20.9 L 10.6 20.9 L 10.5 18.3 L 8.6 17.5 L 6.7 19.3 L 4.7 17.3 L 6.5 15.4 " +
            "L 5.7 13.5 L 3.1 13.4 L 3.1 10.6 L 5.7 10.5 L 6.5 8.6 L 4.7 6.7 L 6.7 4.7 L 8.6 6.5 " +
            "L 10.5 5.7 L 10.6 3.1 L 13.4 3.1 L 13.5 5.7 L 15.4 6.5 L 17.3 4.7 L 19.3 6.7 L 17.5 8.6 Z"

    /** 设置（未选中）：齿轮线框 + 中心圆 */
    val Settings: ImageVector by lazy {
        build(
            "settings",
            strokes = listOf(
                GEAR_COG,
                "M 9.4 12 A 2.6 2.6 0 1 0 14.6 12 A 2.6 2.6 0 1 0 9.4 12 Z",
            ),
        )
    }

    /** 设置（选中）：实心齿轮，偶奇镂空出中心孔 */
    val SettingsFilled: ImageVector by lazy {
        build(
            "settings_filled",
            evenOddFills = listOf(
                "$GEAR_COG M 9.1 12 A 2.9 2.9 0 1 0 14.9 12 A 2.9 2.9 0 1 0 9.1 12 Z",
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
