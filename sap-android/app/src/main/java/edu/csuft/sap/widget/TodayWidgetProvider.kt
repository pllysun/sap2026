package edu.csuft.sap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import edu.csuft.sap.MainActivity
import edu.csuft.sap.R

/**
 * 「今日课程」小组件（2×2）：今日课程紧凑列表。
 * 列表复用 [ScheduleWidgetService]（工厂按 widgetId 取模式，本 Provider 的 id 默认今日模式）。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            for (w in ids) {
                update(context, mgr, w)
                mgr.notifyAppWidgetViewDataChanged(w, R.id.widget_list)
            }
        }
    }

    private fun update(context: Context, mgr: AppWidgetManager, id: Int) {
        val data = WidgetRepository.load(context)
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        val title = if (!data.bound) "软协课表 · 未登录"
        else "今日 · " + (data.currentWeek?.let { "第${it}周" } ?: "未设周次")
        views.setTextViewText(R.id.today_title, title)
        views.setTextViewText(R.id.widget_empty, if (!data.bound) "请在 App 登录" else "今日无课")

        val svc = Intent(context, ScheduleWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            this.data = Uri.parse("sapwidget://today/$id")
        }
        views.setRemoteAdapter(R.id.widget_list, svc)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_list, openApp(context, id))
        views.setOnClickPendingIntent(R.id.today_title, openApp(context, id))
        views.setOnClickPendingIntent(R.id.today_refresh, refreshIntent(context, id))

        mgr.updateAppWidget(id, views)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
    }

    private fun openApp(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("sapwidget://todayopen/$id")
        }
        return PendingIntent.getActivity(context, id, intent, flags())
    }

    private fun refreshIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, TodayWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("sapwidget://todayrefresh/$id")
        }
        return PendingIntent.getBroadcast(context, id, intent, flags())
    }

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        const val ACTION_REFRESH = "edu.csuft.sap.widget.TODAY_REFRESH"
    }
}
