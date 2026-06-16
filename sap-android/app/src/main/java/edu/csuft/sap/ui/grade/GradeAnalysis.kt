package edu.csuft.sap.ui.grade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.ui.common.SapCard
import kotlin.math.max

/** 一位小数（分数） / 两位小数（绩点）格式化。 */
private fun d1(v: Double): String = String.format("%.1f", v)
private fun d2(v: Double): String = String.format("%.2f", v)
private fun creditText(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

/**
 * 成绩分析区：概览数据卡 + 分数段分布柱状图 + 学期 GPA/均分趋势折线 + 课程属性学分占比环形。
 * 图表全部用 Compose [Canvas] 手绘（无第三方图表库）。
 */
@Composable
fun GradeAnalysis(grades: List<GradeDto>, modifier: Modifier = Modifier) {
    val stats = remember(grades) { computeGradeStats(grades) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewCard(stats)
        if (stats.hasScored) ScoreBandCard(stats)
        if (stats.terms.size >= 2) TermTrendCard(stats)
        if (stats.attrSlices.isNotEmpty()) AttrDonutCard(stats)
    }
}

// ---------- 概览数据卡 ----------

@Composable
private fun OverviewCard(s: GradeStats) {
    SapCard {
        Text("成绩概览", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        // 第一行：核心三项
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            BigStat("平均绩点", d2(s.gpa))
            BigStat("加权均分", if (s.hasScored) d1(s.avgScore) else "—")
            BigStat("课程门数", s.totalCourses.toString())
        }
        Spacer(Modifier.height(14.dp))
        FlowStats(
            listOfNotNull(
                "总学分" to creditText(s.totalCredit),
                "已修学分" to creditText(s.earnedCredit),
                if (s.hasScored) "最高分" to d1(s.maxScore ?: 0.0) else null,
                if (s.hasScored) "最低分" to d1(s.minScore ?: 0.0) else null,
                if (s.hasScored) "优秀率" to "${d1(s.excellentRate)}%" else null,
                if (s.hasScored) "及格率" to "${d1(s.passRate)}%" else null,
                "挂科门数" to s.failCount.toString(),
            ),
        )
        if (s.hasScored && s.maxCourse != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "最高：${s.maxCourse}（${d1(s.maxScore ?: 0.0)}）",
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.minCourse != null) {
                Text(
                    "最低：${s.minCourse}（${d1(s.minScore ?: 0.0)}）",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 把若干 小标签/值 以两列网格排布。 */
@Composable
private fun FlowStats(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (label, value) ->
                    Row(
                        Modifier.weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------- 分数段分布柱状图 ----------

@Composable
private fun ScoreBandCard(s: GradeStats) {
    val barColor = MaterialTheme.colorScheme.primary
    val failColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val maxCount = max(1, s.bands.maxOf { it.second })
    val density = LocalDensity.current
    val labelPx = with(density) { 11.sp.toPx() }
    val valuePx = with(density) { 12.sp.toPx() }

    SapCard {
        Text("分数段分布", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "共 ${s.scoredCourses} 门有数字成绩",
            fontSize = 11.5.sp, color = labelColor, modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = s.bands.size
            val bottomPad = labelPx + 14f      // 给 x 轴文字留空
            val topPad = valuePx + 8f          // 给柱顶数字留空
            val chartH = size.height - bottomPad - topPad
            val slot = size.width / n
            val barW = slot * 0.5f

            // 基线
            drawLine(
                color = gridColor,
                start = Offset(0f, topPad + chartH),
                end = Offset(size.width, topPad + chartH),
                strokeWidth = 1f,
            )
            s.bands.forEachIndexed { i, (band, count) ->
                val cx = slot * i + slot / 2f
                val h = chartH * (count.toFloat() / maxCount)
                val top = topPad + (chartH - h)
                val color = if (band == ScoreBand.FAIL && count > 0) failColor else barColor
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - barW / 2f, top),
                    size = Size(barW, h.coerceAtLeast(if (count > 0) 3f else 0f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
                drawTextCentered(count.toString(), cx, top - 6f, valuePx, onSurface)
                drawTextCentered(band.label, cx, topPad + chartH + labelPx + 6f, labelPx, labelColor)
            }
        }
    }
}

// ---------- 学期 GPA / 均分趋势折线 ----------

@Composable
private fun TermTrendCard(s: GradeStats) {
    val gpaColor = MaterialTheme.colorScheme.primary
    val avgColor = Color(0xFFFFA726) // 橙色：与主色（绩点）拉开对比
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hasAvg = s.terms.any { it.avgScore > 0 }
    val density = LocalDensity.current
    val labelPx = with(density) { 10.sp.toPx() }
    val valuePx = with(density) { 10.sp.toPx() }
    val dotR = with(density) { 3.5.dp.toPx() }
    val lineW = with(density) { 2.dp.toPx() }

    SapCard {
        Text("学期趋势", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(gpaColor, "绩点")
            if (hasAvg) {
                Spacer(Modifier.width(14.dp))
                LegendDot(avgColor, "均分")
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val n = s.terms.size
            val bottomPad = labelPx + 12f
            val topPad = valuePx + 8f
            val chartH = size.height - bottomPad - topPad
            val slot = if (n > 1) size.width / (n - 1) else size.width
            fun x(i: Int) = if (n > 1) slot * i else size.width / 2f

            // 横向网格线（4 等分）
            for (g in 0..3) {
                val y = topPad + chartH * g / 3f
                drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            // 绩点折线（0..5 量程）
            drawTrendLine(
                values = s.terms.map { it.gpa },
                yMin = 0f, yMax = 5f, color = gpaColor,
                xOf = ::x, topPad = topPad, chartH = chartH, dotR = dotR, lineW = lineW,
                valuePx = valuePx, valueFmt = { d2(it.toDouble()) }, drawValue = true,
            )
            // 均分折线（0..100 量程）
            if (hasAvg) {
                drawTrendLine(
                    values = s.terms.map { it.avgScore },
                    yMin = 0f, yMax = 100f, color = avgColor,
                    xOf = ::x, topPad = topPad, chartH = chartH, dotR = dotR, lineW = lineW,
                    valuePx = valuePx, valueFmt = { "" }, drawValue = false,
                )
            }
            // x 轴学期标签（取末段，如 2023-2024-1 → "23-1"）
            s.terms.forEachIndexed { i, t ->
                drawTextCentered(shortTerm(t.term), x(i), topPad + chartH + labelPx + 4f, labelPx, labelColor)
            }
        }
    }
}

/** 在 DrawScope 内画一条带数据点的折线（值会被量程归一化）。 */
private fun DrawScope.drawTrendLine(
    values: List<Double>,
    yMin: Float,
    yMax: Float,
    color: Color,
    xOf: (Int) -> Float,
    topPad: Float,
    chartH: Float,
    dotR: Float,
    lineW: Float,
    valuePx: Float,
    valueFmt: (Float) -> String,
    drawValue: Boolean,
) {
    if (values.isEmpty()) return
    fun y(v: Double): Float {
        val ratio = ((v - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
        return topPad + chartH * (1f - ratio)
    }
    val path = Path()
    values.forEachIndexed { i, v ->
        val px = xOf(i); val py = y(v)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, color = color, style = Stroke(width = lineW))
    values.forEachIndexed { i, v ->
        val px = xOf(i); val py = y(v)
        drawCircle(color, radius = dotR, center = Offset(px, py))
        if (drawValue) drawTextCentered(valueFmt(v.toFloat()), px, py - dotR - 5f, valuePx, color)
    }
}

// ---------- 课程属性学分占比环形 ----------

@Composable
private fun AttrDonutCard(s: GradeStats) {
    val palette = chartPalette()
    val total = s.attrSlices.sumOf { it.credit }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val centerBig = with(density) { 18.sp.toPx() }
    val centerSmall = with(density) { 10.sp.toPx() }
    val ringW = with(density) { 18.dp.toPx() }

    SapCard {
        Text("学分构成", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("按课程属性 · 学分占比", fontSize = 11.5.sp, color = labelColor, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp)) {
                    val stroke = Stroke(width = ringW)
                    val inset = ringW / 2f
                    val arc = Rect(inset, inset, size.width - inset, size.height - inset)
                    // 底环
                    drawArc(
                        color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = arc.topLeft, size = arc.size, style = stroke,
                    )
                    var start = -90f
                    s.attrSlices.forEachIndexed { i, slice ->
                        if (total <= 0) return@forEachIndexed
                        val sweep = (slice.credit / total * 360.0).toFloat()
                        drawArc(
                            color = palette[i % palette.size], startAngle = start, sweepAngle = sweep - 1.5f,
                            useCenter = false, topLeft = arc.topLeft, size = arc.size, style = stroke,
                        )
                        start += sweep
                    }
                    val cx = size.width / 2f
                    drawTextCentered(creditText(total), cx, size.height / 2f - 2f, centerBig, onSurface)
                    drawTextCentered("总学分", cx, size.height / 2f + centerSmall + 6f, centerSmall, labelColor)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.attrSlices.forEachIndexed { i, slice ->
                    val pct = if (total > 0) slice.credit / total * 100.0 else 0.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(palette[i % palette.size], CircleShape))
                        Text(slice.label, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text(
                            "${creditText(slice.credit)}分 · ${d1(pct)}%",
                            fontSize = 11.5.sp, color = labelColor,
                        )
                    }
                }
            }
        }
    }
}

// ---------- 共用小组件 / 绘制 ----------

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
    }
}

/** 图表配色：以主色为首，辅以稳定的几个区分色，主题切换时主色随动。 */
@Composable
private fun chartPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    Color(0xFFFFA726),
    Color(0xFF66BB6A),
    Color(0xFFAB47BC),
    Color(0xFF26C6DA),
    Color(0xFFEC407A),
)

/** 在 (x,y) 处水平居中绘制一行文字（baseline 在 y）。 */
private fun DrawScope.drawTextCentered(text: String, x: Float, y: Float, sizePx: Float, color: Color) {
    if (text.isEmpty()) return
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        this.textSize = sizePx
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** "2023-2024-1" → "23-1"；其它原样截断。 */
private fun shortTerm(term: String): String {
    val parts = term.split("-")
    return if (parts.size >= 3 && parts[0].length == 4) "${parts[0].takeLast(2)}-${parts[2]}"
    else term.takeLast(6)
}
