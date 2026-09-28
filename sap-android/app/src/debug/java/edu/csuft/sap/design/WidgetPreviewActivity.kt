package edu.csuft.sap.design

import android.app.Activity
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import edu.csuft.sap.R
import edu.csuft.sap.widget.*
import java.io.File
import java.time.LocalDate

/** 仅 debug：实际 Android XML/RemoteViews 渲染并导出预览，无网络、无用户数据。 */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        intent.getStringExtra("accent")?.let { color ->
            getSharedPreferences("sap_theme", MODE_PRIVATE).edit().putInt("accent", android.graphics.Color.parseColor(color)).apply()
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24); setBackgroundColor(0xFFE8EBF2.toInt()) }
        setContentView(ScrollView(this).apply { addView(root) })
        val entries = listOf(
            Triple("next", R.layout.widget_preview_next, 320 to 112),
            Triple("today", R.layout.widget_preview_today, 240 to 220),
            Triple("schedule", R.layout.widget_preview_schedule, 320 to 256),
            Triple("focus", R.layout.widget_preview_focus, 240 to 220),
            Triple("week", R.layout.widget_preview_week, 320 to 190),
            Triple("month", R.layout.widget_preview_month, 320 to 360),
        )
        val target = File(filesDir, "widget-previews").apply { mkdirs() }
        entries.forEach { (name, layout, size) ->
            val view = LayoutInflater.from(this).inflate(layout, root, false)
            val w = (size.first * resources.displayMetrics.density).toInt()
            val h = (size.second * resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(target, "widget_preview_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            root.addView(TextView(this).apply { text = name; setPadding(0, 24, 0, 12) })
            root.addView(view, LinearLayout.LayoutParams(w, h))
        }
        // 同时验证运行时 RemoteViews 能正常 inflate/reapply（包括动态月历行）。
        val courses = (1..5).map { WidgetCourse(it, 3, 4, "大学英语拓展课程", "树人楼301", "教师", emptyList(), 0) }
        val sample = WidgetData(true, "示例班级", 1, courses, "2026-09-07")
        SummaryWidgetKind.values().forEach { kind ->
            val size = when(kind) { SummaryWidgetKind.FOCUS -> 160 to 170; SummaryWidgetKind.WEEK -> 320 to 150; SummaryWidgetKind.MONTH -> 320 to 300 }
            val rv = summaryWidgetViews(this, kind, sample, LocalDate.parse("2026-09-11"), heightDp=size.second)
            val view = rv.apply(this, root)
            rv.reapply(this, view)
            val w=(size.first*resources.displayMetrics.density).toInt()
            val h=(size.second*resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            view.layout(0,0,w,h)
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(target,"runtime_${kind.name.lowercase()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
            root.addView(view, LinearLayout.LayoutParams(w,h))
        }
    }
}
