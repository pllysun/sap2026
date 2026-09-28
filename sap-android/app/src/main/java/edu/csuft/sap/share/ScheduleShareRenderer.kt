package edu.csuft.sap.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.ui.theme.scheduleCourseColor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 把当前周课表渲染成一张图片并分享（用 Android Canvas 直接画整周网格，不依赖屏幕捕获）。
 */
object ScheduleShareRenderer {

    private data class ShareTextLine(val text: String, val size: Float, val bold: Boolean)

    fun renderAndShare(
        context: Context,
        title: String,
        week: Int,
        days: List<Int>,
        periodCount: Int,
        courses: List<DisplayCourse>,
        settings: ScheduleSettings,
    ) {
        val file = render(context, title, week, days, periodCount, courses, settings) ?: return
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
        settings: ScheduleSettings,
    ): File? {
        val count = periodCount.coerceIn(8, 16)
        val pad = 32f
        val timeCol = settings.sidebarWidthDp * 2.3f
        val headerH = 150f
        val dayHeadH = settings.headerHeightDp * 1.75f
        val rowH = settings.rowHeightDp * 2.35f
        val w = 1120f
        val gridW = w - pad * 2 - timeCol
        val colW = gridW / days.size
        val gridTop = pad + headerH + dayHeadH
        val h = gridTop + rowH * count + pad

        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawBackground(c, settings.backgroundImagePath, w.toInt(), h.toInt())

        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        // 标题
        tp.color = Color.parseColor("#FF1F2329"); tp.textSize = 52f * settings.cardScale; tp.typeface = Typeface.DEFAULT_BOLD
        c.drawText(title, pad, pad + 56f, tp)
        tp.color = Color.parseColor("#FF888F99"); tp.textSize = 34f * settings.cardScale; tp.typeface = Typeface.DEFAULT
        c.drawText("第 $week 周", pad, pad + 104f, tp)

        // 星期表头
        tp.textSize = 30f * settings.cardScale
        for ((i, day) in days.withIndex()) {
            val cx = pad + timeCol + colW * i + colW / 2
            tp.color = Color.parseColor("#FF1F2329"); tp.textAlign = Paint.Align.CENTER
            c.drawText(dayNames.getOrElse(day - 1) { "" }, cx, pad + headerH + 44f, tp)
        }
        tp.textAlign = Paint.Align.LEFT

        // 时间列
        for (node in 1..count) {
            val top = gridTop + rowH * (node - 1)
            tp.color = Color.parseColor("#FF646A73"); tp.textSize = 30f * settings.cardScale; tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.DEFAULT_BOLD
            c.drawText("$node", pad + timeCol / 2, top + 44f, tp)
            tp.typeface = Typeface.DEFAULT; tp.textSize = 22f * settings.cardScale; tp.color = Color.parseColor("#FFA0A6AE")
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
                val spacing = settings.outerSpacingDp * 2f
                val left = pad + timeCol + colW * idx + spacing
                val top = gridTop + rowH * (s - 1) + spacing
                val right = pad + timeCol + colW * (idx + 1) - spacing
                val bottom = gridTop + rowH * e - spacing
                val col = scheduleCourseColor(course, settings)
                val radius = settings.cornerRadiusDp * 2f
                rect.style = Paint.Style.FILL
                rect.color = col.container.toArgb()
                rect.alpha = (settings.opacityFraction * 255).roundToInt().coerceIn(0, 255)
                c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, rect)
                drawCardBorder(c, RectF(left, top, right, bottom), radius, col.onContainer.toArgb(), settings.borderStyle)
                drawCourseText(c, course, col.onContainer.toArgb(), RectF(left, top, right, bottom), settings)
            }
        }

        // 页脚
        tp.color = Color.parseColor("#FFB0B4BC"); tp.textSize = 26f; tp.textAlign = Paint.Align.RIGHT
        c.drawText("软协课表", w - pad, h - 12f, tp)

        return try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            removeExpiredShareImages(dir)
            // 分享面板会按 content URI 缓存缩略图。复用固定的 schedule.png 时，即使文件内容
            // 已覆盖，部分系统仍会展示上一次分享的周次；每次使用唯一文件名可强制刷新预览。
            val f = newShareImageFile(dir, week)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f
        } catch (_: Exception) {
            null
        }
    }

    private fun drawCourseText(
        c: Canvas,
        course: DisplayCourse,
        color: Int,
        bounds: RectF,
        settings: ScheduleSettings,
    ) {
        val padding = settings.innerPaddingDp * 3f
        val maxW = (bounds.width() - padding * 2).coerceAtLeast(1f)
        val nameSize = settings.cardFontSizeSp * settings.cardScale * 2.35f
        val detailSize = (settings.cardFontSizeSp - 2f).coerceAtLeast(7f) * settings.cardScale * 2.35f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = nameSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val lines = mutableListOf<ShareTextLine>()
        wrap(course.name, p, maxW, 3).forEach { lines += ShareTextLine(it, nameSize, true) }
        if (!settings.hideLocation && course.location.isNotBlank()) {
            p.textSize = detailSize
            p.typeface = Typeface.DEFAULT
            lines += ShareTextLine(ellipsize("@${course.location}", p, maxW), detailSize, false)
        }
        if (!settings.hideTeacher && course.teacher.isNotBlank()) {
            p.textSize = detailSize
            p.typeface = Typeface.DEFAULT
            lines += ShareTextLine(ellipsize(course.teacher, p, maxW), detailSize, false)
        }
        if (lines.isEmpty()) return

        val totalHeight = lines.sumOf { (it.size * 1.18f).toDouble() }.toFloat()
        var baseline = if (settings.centerTextVertically) {
            bounds.top + (bounds.height() - totalHeight) / 2f + lines.first().size
        } else {
            bounds.top + padding + lines.first().size
        }
        p.textAlign = if (settings.centerTextHorizontally) Paint.Align.CENTER else Paint.Align.LEFT
        val x = if (settings.centerTextHorizontally) bounds.centerX() else bounds.left + padding
        lines.forEach { line ->
            p.textSize = line.size
            p.typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            p.alpha = if (line.bold) 255 else 220
            c.drawText(line.text, x, baseline, p)
            baseline += line.size * 1.18f
        }
    }

    private fun drawBackground(canvas: Canvas, path: String?, width: Int, height: Int) {
        val bitmap = path?.takeIf { it.isNotBlank() }?.let { filePath ->
            runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
        }
        if (bitmap == null) {
            canvas.drawColor(Color.parseColor("#FFF6F7F9"))
            return
        }
        val targetRatio = width.toFloat() / height
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val source = if (sourceRatio > targetRatio) {
            val cropWidth = (bitmap.height * targetRatio).roundToInt().coerceAtMost(bitmap.width)
            val left = (bitmap.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bitmap.height)
        } else {
            val cropHeight = (bitmap.width / targetRatio).roundToInt().coerceAtMost(bitmap.height)
            val top = (bitmap.height - cropHeight) / 2
            Rect(0, top, bitmap.width, top + cropHeight)
        }
        canvas.drawBitmap(bitmap, source, Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.drawColor(Color.argb(46, 255, 255, 255))
        bitmap.recycle()
    }

    private fun drawCardBorder(c: Canvas, bounds: RectF, radius: Float, color: Int, style: Int) {
        if (style == 0) return
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 150
            this.style = Paint.Style.STROKE
            strokeWidth = 2f
            if (style == 2) pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
        }
        val inset = border.strokeWidth / 2f
        c.drawRoundRect(
            RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset),
            radius,
            radius,
            border,
        )
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

private const val SHARE_IMAGE_RETENTION_MS = 24L * 60L * 60L * 1000L

internal fun newShareImageFile(
    directory: File,
    week: Int,
    nonce: String = UUID.randomUUID().toString(),
): File = File(directory, "schedule-week-$week-$nonce.png")

/** 保留近期文件，避免用户连续分享时过早撤销仍在使用的 URI，同时清理长期缓存。 */
internal fun removeExpiredShareImages(
    directory: File,
    nowMillis: Long = System.currentTimeMillis(),
) {
    directory.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.name.startsWith("schedule-week-") && it.extension == "png" }
        ?.filter { nowMillis - it.lastModified() > SHARE_IMAGE_RETENTION_MS }
        ?.forEach { runCatching { it.delete() } }
}
