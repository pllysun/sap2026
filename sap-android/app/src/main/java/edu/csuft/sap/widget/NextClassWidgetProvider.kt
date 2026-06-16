package edu.csuft.sap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import edu.csuft.sap.MainActivity
import edu.csuft.sap.R
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.ui.theme.paletteColor
import java.time.LocalTime

/**
 * 「下一节课」小组件（1×1 ↔ 2×1，可缩放）：显示今日正在上 / 下一节课。
 * 静态 RemoteViews（单条内容，无列表），数据来自本地缓存。
 */
class NextClassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, mgr, id)
    }

    private fun update(context: Context, mgr: AppWidgetManager, id: Int) {
        val data = WidgetRepository.load(context)
        val views = RemoteViews(context.packageName, R.layout.widget_next)

        if (!data.bound) {
            views.setTextViewText(R.id.next_label, "软协课表")
            views.setTextViewText(R.id.next_name, "未登录")
            views.setTextViewText(R.id.next_info, "请在 App 登录并绑定教务")
            views.setInt(R.id.next_bar, "setBackgroundColor", GRAY)
        } else {
            val c = WidgetRepository.nextClass(data)
            if (c == null) {
                views.setTextViewText(R.id.next_label, "今日")
                views.setTextViewText(
                    R.id.next_name,
                    if (WidgetRepository.todayCourses(data).isEmpty()) "今日无课" else "今日课程已结束",
                )
                views.setTextViewText(R.id.next_info, data.profileName)
                views.setInt(R.id.next_bar, "setBackgroundColor", GRAY)
            } else {
                val now = LocalTime.now()
                val s = WidgetRepository.startTime(c)
                val e = WidgetRepository.endTime(c)
                val ongoing = s != null && e != null && !now.isBefore(s) && now.isBefore(e)
                views.setTextViewText(R.id.next_label, if (ongoing) "正在上课" else "下一节")
                views.setTextViewText(R.id.next_name, c.name)
                val time = (Periods.period(c.startNode)?.start ?: "") + "-" + (Periods.period(c.endNode)?.end ?: "")
                val info = listOfNotNull(
                    time.takeIf { it.length > 1 },
                    c.location.takeIf { it.isNotBlank() }?.let { "@$it" },
                ).joinToString("  ")
                views.setTextViewText(R.id.next_info, info)
                views.setInt(R.id.next_bar, "setBackgroundColor", paletteColor(c.colorIndex).onContainer.toArgb())
            }
        }

        views.setOnClickPendingIntent(R.id.next_root, openApp(context, id))
        mgr.updateAppWidget(id, views)
    }

    private fun openApp(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("sapwidget://next/$id")
        }
        return PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        val GRAY = 0xFFB0B4BC.toInt()
    }
}
