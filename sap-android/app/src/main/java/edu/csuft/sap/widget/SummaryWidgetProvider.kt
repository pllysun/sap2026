package edu.csuft.sap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import edu.csuft.sap.MainActivity
import edu.csuft.sap.R
import edu.csuft.sap.data.schedule.calendarMonthCells
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SummaryWidgetKind { FOCUS, WEEK, MONTH }

abstract class SummaryWidgetProvider(private val kind: SummaryWidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val data = WidgetRepository.load(context)
        ids.forEach { id ->
            val views = summaryWidgetViews(context, kind, data, heightDp = manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                this.data = Uri.parse("sapwidget://summary/${kind.name}/$id")
            }
            views.setOnClickPendingIntent(R.id.summary_root, PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }
}

class FocusWidgetProvider : SummaryWidgetProvider(SummaryWidgetKind.FOCUS)
class WeekOverviewWidgetProvider : SummaryWidgetProvider(SummaryWidgetKind.WEEK)
class MonthWidgetProvider : SummaryWidgetProvider(SummaryWidgetKind.MONTH)

internal fun summaryWidgetViews(context: Context, kind: SummaryWidgetKind, data: WidgetData,
                                today: LocalDate = LocalDate.now(), heightDp: Int = 0): RemoteViews {
    val views = RemoteViews(context.packageName, when (kind) {
        SummaryWidgetKind.FOCUS -> R.layout.widget_focus
        SummaryWidgetKind.WEEK -> R.layout.widget_week_overview
        SummaryWidgetKind.MONTH -> R.layout.widget_month
    })
    val palette = WidgetPalette(context)
    views.setInt(R.id.summary_root, "setBackgroundResource", R.drawable.widget_bg)
    if (kind == SummaryWidgetKind.MONTH) views.setViewVisibility(R.id.summary_footer, android.view.View.GONE)
    if (kind != SummaryWidgetKind.MONTH) {
        views.setTextColor(R.id.summary_number, palette.ink)
        views.setInt(R.id.summary_root, "setGravity", android.view.Gravity.CENTER_VERTICAL)
        if (heightDp in 1..200) {
            views.setTextViewTextSize(R.id.summary_number, android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
            views.setViewVisibility(R.id.summary_footer, android.view.View.GONE)
            if (kind == SummaryWidgetKind.FOCUS) {
                views.setInt(R.id.summary_name, "setMaxLines", 1)
                views.setInt(R.id.summary_detail, "setMaxLines", 1)
            }
        }
    }
    val known = data.bound && edu.csuft.sap.data.schedule.WeekUtil.parseDate(data.semesterStart) != null
    fun courses(date: LocalDate) = groupedWidgetCourses(WidgetRepository.coursesOn(data, date))
    val day = courses(today)
    views.setTextViewText(R.id.summary_title, when(kind) {
        SummaryWidgetKind.FOCUS -> today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
        SummaryWidgetKind.WEEK -> "本周课量 · ${data.currentWeek?.let { "第${it}周" } ?: "课表"}"
        SummaryWidgetKind.MONTH -> "${today.year}年${today.monthValue}月"
    })
    views.setTextViewText(R.id.summary_footer, if (known) "软协课表 · ${data.profileName}" else
        if (!data.bound) "请打开 App 选择课表" else "请先设置开学日期")
    when (kind) {
        SummaryWidgetKind.FOCUS -> {
            val next = WidgetRepository.nextClass(data)?.let { raw ->
                day.firstOrNull { it.name == raw.name && it.startNode == raw.startNode && it.endNode == raw.endNode }
            }
            views.setTextViewText(R.id.summary_number, if (!known) "—" else "${day.size}")
            views.setTextViewText(R.id.summary_caption, if (known) "项课程 · ${widgetPeriodCount(day)} 节" else "今日课程")
            views.setTextViewText(R.id.summary_name, next?.name ?: if (known) {
                if (day.isEmpty()) "今日无课" else "今日课程已结束"
            } else "等待课表")
            views.setTextViewText(R.id.summary_detail, next?.let {
                "${WidgetRepository.startTime(it) ?: "—"}–${WidgetRepository.endTime(it) ?: "—"} · ${it.location.ifBlank { "教室待定" }}"
            } ?: if (known) "打开 App 查看日历" else "支持三种课表模式")
        }
        SummaryWidgetKind.WEEK -> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val days = (0..6).map { monday.plusDays(it.toLong()) }
            val lists = days.map(::courses)
            views.setTextViewText(R.id.summary_number, if (known) "${lists.sumOf { it.size }}" else "—")
            views.setTextViewText(R.id.summary_caption, "项课程 · 本周")
            views.removeAllViews(R.id.summary_rows)
            views.addView(R.id.summary_rows, summaryRow(context, days.mapIndexed { i, date ->
                Triple(listOf("一", "二", "三", "四", "五", "六", "日")[i],
                    if (!known) "—" else lists[i].size.takeIf { it > 0 }?.let { "${it}课" } ?: "休", date == today)
            }))
        }
        SummaryWidgetKind.MONTH -> {
            views.removeAllViews(R.id.summary_rows)
            views.addView(R.id.summary_rows, summaryRow(context, listOf("一", "二", "三", "四", "五", "六", "日").map { Triple(it, "", false) }))
            val weeks = calendarMonthCells(YearMonth.from(today)).chunked(7)
            val rowHeight = ((heightDp.takeIf { it > 0 } ?: 320) - 96).div(weeks.size).coerceAtLeast(30)
            weeks.forEach { week ->
                views.addView(R.id.summary_rows, summaryRow(context, week.map { date ->
                    val count = date?.let { courses(it).size } ?: 0
                    Triple(date?.dayOfMonth?.toString() ?: "", if (date == null) "" else if (!known) "—"
                        else if (count == 0) "休" else "${count}课", date == today)
                }, rowHeight))
            }
        }
    }
    return views
}

private fun summaryRow(context: Context, cells: List<Triple<String, String, Boolean>>, heightDp: Int = 40): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_summary_row)
    val palette = WidgetPalette(context)
    cells.forEach { (label, count, today) ->
        val cell = RemoteViews(context.packageName, R.layout.widget_summary_cell)
        cell.setTextViewText(R.id.summary_day, label)
        cell.setTextViewText(R.id.summary_count, count)
        cell.setTextColor(R.id.summary_day, if (today) palette.ink else 0xFF293345.toInt())
        cell.setTextColor(R.id.summary_count, palette.ink)
        if (today) {
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawRoundRect(0f, 0f, 64f, 64f, 14f, 14f,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = palette.tint })
            cell.setImageViewBitmap(R.id.summary_highlight, bitmap)
        }
        val padding = ((heightDp - 30).coerceAtLeast(0) / 2f * context.resources.displayMetrics.density).toInt()
        cell.setViewPadding(R.id.summary_cell, 0, padding, 0, padding)
        row.addView(R.id.summary_row, cell)
    }
    return row
}
