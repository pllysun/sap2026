package edu.csuft.sap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import edu.csuft.sap.MainActivity
import edu.csuft.sap.R
import java.time.LocalDate

/**
 * 桌面课表小组件（经典 AppWidgetProvider + RemoteViews，最大兼容国产 ROM）。
 * 顶栏：标题（今日/明日/周一/本周 · 第N周）、另外两种视图的入口、刷新。
 * 列表走 RemoteViewsService，每个组件分别保存所选视图。
 * 数据来自 App 本地缓存（[WidgetRepository]），无需网络。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        updateWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // 兼容升级前桌面中尚未替换的切换 PendingIntent。
                    setMode(context, id, if (mode(context, id) == ScheduleWidgetMode.TODAY) ScheduleWidgetMode.WEEK else ScheduleWidgetMode.TODAY)
                    val mgr = AppWidgetManager.getInstance(context)
                    updateWidget(context, mgr, id)
                }
            }
            ACTION_SELECT_MODE -> {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val selected = ScheduleWidgetMode.fromValue(intent.getIntExtra(EXTRA_MODE, MODE_TODAY))
                    setMode(context, id, selected)
                    val mgr = AppWidgetManager.getInstance(context)
                    updateWidget(context, mgr, id)
                }
            }
            ACTION_REFRESH -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf(id) else allIds(context, mgr)
                for (w in ids) {
                    updateWidget(context, mgr, w)
                    mgr.notifyAppWidgetViewDataChanged(w, R.id.widget_list)
                }
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { prefs.remove("mode_$it") }
        prefs.apply()
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
        val data = WidgetRepository.load(context)
        val state = scheduleWidgetState(data, mode(context, id), LocalDate.now())
        val width = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
        val layout = if (width < 240) R.layout.widget_schedule_compact else R.layout.widget_schedule
        val views = RemoteViews(context.packageName, layout)

        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_empty, state.emptyText)
        listOf(R.id.widget_day_toggle, R.id.widget_toggle).zip(state.tabs).forEach { (viewId, tab) ->
            views.setTextViewText(viewId, tab.label)
            views.setOnClickPendingIntent(viewId, broadcast(context, id, ACTION_SELECT_MODE, tab.mode))
        }

        // 列表数据源
        val svc = Intent(context, ScheduleWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            this.data = Uri.parse("sapwidget://list/$id") // 显式指向 Intent.data，避免被外层局部 val data 截获
        }
        views.setRemoteAdapter(R.id.widget_list, svc)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // 点击：标题区/列表项 → 打开 App；切换/刷新 → 广播
        views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context, id))
        views.setPendingIntentTemplate(R.id.widget_list, openAppIntent(context, id))
        views.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, id, ACTION_REFRESH))

        mgr.updateAppWidget(id, views)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
    }

    private fun openAppIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("sapwidget://open/$id")
        }
        return PendingIntent.getActivity(context, id, intent, immutableFlags())
    }

    private fun broadcast(context: Context, id: Int, action: String, mode: ScheduleWidgetMode? = null): PendingIntent {
        val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            mode?.let { putExtra(EXTRA_MODE, it.value) }
            data = Uri.parse("sapwidget://$action/$id/${mode?.value ?: "refresh"}")
        }
        return PendingIntent.getBroadcast(context, id, intent, immutableFlags())
    }

    companion object {
        const val ACTION_TOGGLE = "edu.csuft.sap.widget.TOGGLE"
        const val ACTION_SELECT_MODE = "edu.csuft.sap.widget.SELECT_MODE"
        const val ACTION_REFRESH = "edu.csuft.sap.widget.REFRESH"
        const val MODE_TODAY = 0
        const val EXTRA_MODE = "schedule_mode"
        private const val PREFS = "sap_widget"

        fun mode(context: Context, id: Int): ScheduleWidgetMode = ScheduleWidgetMode.fromValue(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("mode_$id", MODE_TODAY),
        )

        private fun setMode(context: Context, id: Int, mode: ScheduleWidgetMode) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("mode_$id", mode.value).apply()

        private fun allIds(context: Context, mgr: AppWidgetManager): IntArray =
            mgr.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))

        private fun immutableFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** 供 App 在课表数据变化后调用：刷新全部桌面小组件。 */
        fun notifyChanged(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            refreshProvider(context, mgr, ScheduleWidgetProvider::class.java, hasList = true)
            refreshProvider(context, mgr, TodayWidgetProvider::class.java, hasList = true)
            refreshProvider(context, mgr, NextClassWidgetProvider::class.java, hasList = false)
            refreshProvider(context, mgr, FocusWidgetProvider::class.java, hasList = false)
            refreshProvider(context, mgr, WeekOverviewWidgetProvider::class.java, hasList = false)
            refreshProvider(context, mgr, MonthWidgetProvider::class.java, hasList = false)
        }

        private fun refreshProvider(
            context: Context,
            mgr: AppWidgetManager,
            cls: Class<*>,
            hasList: Boolean,
        ) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
            if (hasList) mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }
}
