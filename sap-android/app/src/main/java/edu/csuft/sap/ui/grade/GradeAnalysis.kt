package edu.csuft.sap.ui.grade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val gpaColor = MaterialTheme.colorScheme.onPrimaryContainer
    val avgColor = Color(0xFF995100)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gpa = remember(s.terms) { s.terms.map { it.gpa } }
    val averages = remember(s.terms) { s.terms.map { it.avgScore } }
    val termWidth = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val scroll = rememberScrollState()

    SapCard {
        Text("学期趋势", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val chartWidth = maxOf(maxWidth, termWidth * s.terms.size)
            Column {
                TermTrendPlot("绩点", gpa, s.terms, 5, gpaColor, gridColor, labelColor, chartWidth, scroll, ::d2)
                if (averages.any { it != null }) {
                    Spacer(Modifier.height(12.dp))
                    TermTrendPlot("加权均分", averages, s.terms, 100, avgColor, gridColor, labelColor, chartWidth, scroll, ::d1)
                } else {
                    Text(
                        "暂无可计算均分的数字成绩",
                        fontSize = 12.sp,
                        color = labelColor,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TermTrendPlot(
    label: String,
    values: List<Double?>,
    terms: List<TermPoint>,
    maximum: Int,
    color: Color,
    gridColor: Color,
    labelColor: Color,
    width: Dp,
    scroll: ScrollState,
    format: (Double) -> String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color, label)
        Spacer(Modifier.width(8.dp))
        Text("满分 $maximum", fontSize = 11.sp, color = labelColor)
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.horizontalScroll(scroll).width(width)) {
        Row(Modifier.fillMaxWidth()) {
            values.forEachIndexed { index, value ->
                val number = value?.let(format) ?: "—"
                Text(
                    number,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (value != null) color else labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "${terms[index].term} $label ${value?.let(format) ?: "暂无"}"
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(62.dp)) {
            val inset = 4.dp.toPx()
            val height = size.height - 2 * inset
            val slot = size.width / values.size
            val points = values.mapIndexed { index, value ->
                value?.let {
                    val ratio = (it / maximum).toFloat().coerceIn(0f, 1f)
                    Offset(slot * (index + 0.5f), inset + height * (1f - ratio))
                }
            }

            for (i in 0..2) {
                val y = inset + height * i / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
            }
            points.zipWithNext().forEach { (from, to) ->
                if (from != null && to != null) drawLine(color, from, to, strokeWidth = 2.dp.toPx())
            }
            points.filterNotNull().forEach { drawCircle(color, 3.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth()) {
            terms.forEach { term ->
                Text(
                    shortTerm(term.term),
                    fontSize = 10.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
