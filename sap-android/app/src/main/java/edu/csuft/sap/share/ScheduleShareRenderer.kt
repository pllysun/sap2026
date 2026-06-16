package edu.csuft.sap.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.ui.theme.paletteColor
import java.io.File
import java.io.FileOutputStream

private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 把当前周课表渲染成一张图片并分享（用 Android Canvas 直接画整周网格，不依赖屏幕捕获）。
 */
object ScheduleShareRenderer {

    fun renderAndShare(
        context: Context,
        title: String,
        week: Int,
        days: List<Int>,
        periodCount: Int,
        courses: List<DisplayCourse>,
    ) {
        val file = render(context, title, week, days, periodCount, courses) ?: return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // 设置 ClipData，系统分享面板才会显示图片预览缩略图（仅 putExtra 不会预览）
            clipData = android.content.ClipData.newUri(context.contentResolver, "课表", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享课表").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun render(
        context: Context,
        title: String,
        week: Int,
        days: List<Int>,
        periodCount: Int,
        courses: List<DisplayCourse>,
    ): File? {
        val count = periodCount.coerceIn(8, 16)
        val pad = 32f
        val timeCol = 96f
        val headerH = 150f
        val dayHeadH = 84f
        val rowH = 132f
        val w = 1120f
        val gridW = w - pad * 2 - timeCol
        val colW = gridW / days.size
        val gridTop = pad + headerH + dayHeadH
        val h = gridTop + rowH * count + pad

        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#FFF6F7F9"))

        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        // 标题
        tp.color = Color.parseColor("#FF1F2329"); tp.textSize = 52f; tp.typeface = Typeface.DEFAULT_BOLD
        c.drawText(title, pad, pad + 56f, tp)
        tp.color = Color.parseColor("#FF888F99"); tp.textSize = 34f; tp.typeface = Typeface.DEFAULT
        c.drawText("第 $week 周", pad, pad + 104f, tp)

        // 星期表头
        tp.textSize = 30f
        for ((i, day) in days.withIndex()) {
            val cx = pad + timeCol + colW * i + colW / 2
            tp.color = Color.parseColor("#FF1F2329"); tp.textAlign = Paint.Align.CENTER
            c.drawText(dayNames.getOrElse(day - 1) { "" }, cx, pad + headerH + 44f, tp)
        }
        tp.textAlign = Paint.Align.LEFT

        // 时间列
        for (node in 1..count) {
            val top = gridTop + rowH * (node - 1)
            tp.color = Color.parseColor("#FF646A73"); tp.textSize = 30f; tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.DEFAULT_BOLD
            c.drawText("$node", pad + timeCol / 2, top + 44f, tp)
            tp.typeface = Typeface.DEFAULT; tp.textSize = 22f; tp.color = Color.parseColor("#FFA0A6AE")
            Periods.period(node)?.let {
                c.drawText(it.start, pad + timeCol / 2, top + 76f, tp)
                c.drawText(it.end, pad + timeCol / 2, top + 102f, tp)
            }
        }
        tp.textAlign = Paint.Align.LEFT

        // 网格淡线
        val line = Paint().apply { color = Color.parseColor("#11000000"); strokeWidth = 1f }
        for (node in 0..count) {
            val y = gridTop + rowH * node
            c.drawLine(pad + timeCol, y, w - pad, y, line)
        }
        for (i in 0..days.size) {
            val x = pad + timeCol + colW * i
            c.drawLine(x, gridTop, x, gridTop + rowH * count, line)
        }

        // 课卡
        val rect = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((idx, day) in days.withIndex()) {
            for (course in courses.filter { it.day == day }) {
                val s = course.startNode.coerceIn(1, count)
                val e = course.endNode.coerceIn(s, count)
                val left = pad + timeCol + colW * idx + 4f
                val top = gridTop + rowH * (s - 1) + 4f
                val right = pad + timeCol + colW * (idx + 1) - 4f
                val bottom = gridTop + rowH * e - 4f
                val col = paletteColor(course.colorIndex)
                rect.color = col.container.toArgb()
                c.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, rect)
                drawCourseText(c, course, col.onContainer.toArgb(), left + 12f, top + 12f, right - left - 20f)
            }
        }

        // 页脚
        tp.color = Color.parseColor("#FFB0B4BC"); tp.textSize = 26f; tp.textAlign = Paint.Align.RIGHT
        c.drawText("软协课表", w - pad, h - 12f, tp)

        return try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "schedule.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f
        } catch (_: Exception) {
            null
        }
    }

    private fun drawCourseText(c: Canvas, course: DisplayCourse, color: Int, x: Float, y: Float, maxW: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
        var cy = y + 26f
        for (l in wrap(course.name, p, maxW, 2)) { c.drawText(l, x, cy, p); cy += 30f }
        p.typeface = Typeface.DEFAULT; p.textSize = 22f; p.alpha = 220
        course.location.takeIf { it.isNotBlank() }?.let {
            c.drawText(ellipsize("@$it", p, maxW), x, cy + 4f, p); cy += 28f
        }
        course.teacher.takeIf { it.isNotBlank() }?.let {
            c.drawText(ellipsize(it, p, maxW), x, cy + 4f, p)
        }
    }

    private fun wrap(text: String, p: Paint, maxW: Float, maxLines: Int): List<String> {
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (ch in text) {
            if (p.measureText(cur.toString() + ch) > maxW) {
                if (out.size == maxLines - 1) { // 末行截断
                    while (cur.isNotEmpty() && p.measureText("$cur…") > maxW) cur.deleteCharAt(cur.length - 1)
                    out.add("$cur…"); return out
                }
                out.add(cur.toString()); cur = StringBuilder()
            }
            cur.append(ch)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun ellipsize(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        val sb = StringBuilder(text)
        while (sb.isNotEmpty() && p.measureText("$sb…") > maxW) sb.deleteCharAt(sb.length - 1)
        return "$sb…"
    }
}
