package edu.csuft.sap.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import edu.csuft.sap.MainActivity
import edu.csuft.sap.R

/** 接收上课提醒闹钟（弹通知/弹窗）与「重排/开机」事件（重新排课前提醒）。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_SHOW -> {
                val info = ReminderContent(
                    name = intent.getStringExtra("name") ?: "上课提醒",
                    location = intent.getStringExtra("location").orEmpty(),
                    start = intent.getStringExtra("start").orEmpty(),
                    end = intent.getStringExtra("end").orEmpty(),
                    startMillis = intent.getLongExtra("startMillis", 0L),
                    lead = intent.getIntExtra("lead", 0),
                )
                if (ReminderPrefs.enabled(context)) ReminderNotifier.notify(context, info)
                if (ReminderPrefs.popupEnabled(context)) ReminderNotifier.popup(context, info)
            }
            ReminderScheduler.ACTION_RESCHEDULE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReminderScheduler.reschedule(context)
        }
    }
}

/** 一条上课提醒的展示内容：课程名、起止时间、地点、上课时刻（算倒计时）。 */
data class ReminderContent(
    val name: String,
    val location: String,
    val start: String,
    val end: String,
    val startMillis: Long,
    val lead: Int,
)

/**
 * 上课提醒的两种呈现（抽出来便于「闹钟触发」与「设置页测试按钮」复用）：
 * - [notify] 状态栏 + 锁屏通知（需 POST_NOTIFICATIONS）。
 * - [popup] 悬浮窗弹窗（需「显示在其它应用上层」权限）。
 */
object ReminderNotifier {

    // 升级渠道 id：开启锁屏可见 + 高优先级（旧渠道用户改不动，换 id 保证新设置生效）
    const val CHANNEL_ID = "class_reminder_v2"

    /** 起止时间段，如 "07:00 - 07:45"；无结束时间则只显示开始。 */
    fun timeRange(start: String, end: String): String =
        if (end.isNotBlank()) "$start - $end" else start

    /** 时间 + 地点行。 */
    fun timeLine(c: ReminderContent): String = buildString {
        append(timeRange(c.start, c.end))
        if (c.location.isNotBlank()) append("  ·  ").append(c.location)
    }

    /** 「还有几分钟开始」：优先按真实上课时刻算（闹钟若被 Doze 延后也准），否则用配置的提前分钟。 */
    fun remainText(c: ReminderContent): String {
        val mins = if (c.startMillis > 0L)
            ((c.startMillis - System.currentTimeMillis()) / 60000L).toInt()
        else c.lead
        return if (mins <= 1) "即将开始" else "还有 $mins 分钟开始"
    }

    fun notify(context: Context, c: ReminderContent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val timeLine = timeLine(c)
        val remain = remainText(c)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setSubText("上课提醒")
            .setContentTitle(c.name)
            .setContentText("$remain · $timeLine")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$timeLine\n$remain"))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏完整显示内容
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(c.name.hashCode(), n) }
    }

    /** 启动悬浮窗弹窗服务（盖在其它应用上层）。无悬浮窗权限则忽略。 */
    fun popup(context: Context, c: ReminderContent) {
        if (!ClassPopupService.canShow(context)) return
        val i = Intent(context, ClassPopupService::class.java).apply {
            putExtra("name", c.name)
            putExtra("location", c.location)
            putExtra("start", c.start)
            putExtra("end", c.end)
            putExtra("startMillis", c.startMillis)
            putExtra("lead", c.lead)
        }
        runCatching { ContextCompat.startForegroundService(context, i) }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "课前提醒（通知栏 / 锁屏）"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }
}
