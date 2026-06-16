package edu.csuft.sap.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

/** 为课表列表提供 RemoteViewsFactory（每个小组件实例一个，按 widgetId 区分今日/本周）。 */
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return ScheduleWidgetFactory(applicationContext, id)
    }
}
