package edu.csuft.sap.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import edu.csuft.sap.R
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.ui.theme.paletteColor
import java.time.LocalDate

private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 课表列表工厂：按 widgetId 的模式（今日/本周）从本地缓存取课，构建每行 RemoteViews。
 * 今日：左列「节-节 / 起始时间」；本周：左列「周X / 节-节」。左侧色条按课名取色。
 */
class ScheduleWidgetFactory(
    private val context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetCourse> = emptyList()
    private var mode: Int = ScheduleWidgetProvider.MODE_TODAY

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val data = WidgetRepository.load(context)
        mode = ScheduleWidgetProvider.mode(context, widgetId)
        val week = (data.currentWeek ?: 1).coerceAtLeast(1)
        val active = data.courses.filter { it.weeks.isEmpty() || it.weeks.contains(week) }
        items = if (mode == ScheduleWidgetProvider.MODE_TODAY) {
            val today = LocalDate.now().dayOfWeek.value
            active.filter { it.day == today }.sortedBy { it.startNode }
        } else {
            active.sortedWith(compareBy({ it.day }, { it.startNode }))
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val c = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_item)
        if (mode == ScheduleWidgetProvider.MODE_TODAY) {
            rv.setTextViewText(R.id.item_t1, "${c.startNode}-${c.endNode}")
            rv.setTextViewText(R.id.item_t2, Periods.period(c.startNode)?.start ?: "")
        } else {
            rv.setTextViewText(R.id.item_t1, dayNames.getOrElse(c.day - 1) { "" })
            rv.setTextViewText(R.id.item_t2, "${c.startNode}-${c.endNode}")
        }
        rv.setTextViewText(R.id.item_name, c.name)
        val sub = listOfNotNull(
            c.location.takeIf { it.isNotBlank() }?.let { "@$it" },
            c.teacher.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        rv.setTextViewText(R.id.item_sub, sub)
        rv.setInt(R.id.item_bar, "setBackgroundColor", paletteColor(c.colorIndex).onContainer.toArgb())
        rv.setOnClickFillInIntent(R.id.item_root, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
