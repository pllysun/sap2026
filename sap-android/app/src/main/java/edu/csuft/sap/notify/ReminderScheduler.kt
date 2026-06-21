package edu.csuft.sap.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import edu.csuft.sap.widget.WidgetRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 上课提醒的本地设置（全局，与账号/课表无关）。通知提醒与弹窗提醒可独立开关、并存。 */
object ReminderPrefs {
    private const val PREFS = "sap_reminder"
    private const val KEY_ENABLED = "enabled"        // 通知提醒（状态栏通知）
    private const val KEY_POPUP = "popup_enabled"    // 弹窗提醒（悬浮窗，盖在其它应用上）
    private const val KEY_LEAD = "lead_minutes"

    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = p(context).getBoolean(KEY_ENABLED, false)
    fun popupEnabled(context: Context): Boolean = p(context).getBoolean(KEY_POPUP, false)
    /** 通知或弹窗任一开启即需要排闹钟。 */
    fun anyEnabled(context: Context): Boolean = enabled(context) || popupEnabled(context)
    fun leadMinutes(context: Context): Int = p(context).getInt(KEY_LEAD, 15)

    fun setEnabled(context: Context, v: Boolean) = p(context).edit().putBoolean(KEY_ENABLED, v).apply()
    fun setPopupEnabled(context: Context, v: Boolean) = p(context).edit().putBoolean(KEY_POPUP, v).apply()
    fun setLead(context: Context, m: Int) = p(context).edit().putInt(KEY_LEAD, m).apply()
}

/**
 * 上课提醒调度：用 [AlarmManager] 为「今日剩余的课」各排一个课前 N 分钟的精确闹钟，
 * 并排一个次日 00:05 的「重排」闹钟滚动续期。App 进后台、开机、数据变化时调用 [reschedule]。
 * 注意：国产 ROM 的省电策略可能延迟/拦截后台闹钟，必要时引导用户关闭对本 App 的电池优化。
 */
object ReminderScheduler {

    const val ACTION_SHOW = "edu.csuft.sap.notify.SHOW"
    const val ACTION_RESCHEDULE = "edu.csuft.sap.notify.RESCHEDULE"
    private const val BASE_CODE = 19000
    private const val MAX_SLOTS = 32
    private const val RESCHEDULE_CODE = 18999

    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // 先取消旧的（固定 requestCode 区间）
        for (i in 0 until MAX_SLOTS) am.cancel(showIntent(context, BASE_CODE + i, null))

        // 始终排一个次日凌晨的“重排”闹钟，滚动续期
        scheduleRearm(context, am)

        if (!ReminderPrefs.anyEnabled(context)) return

        val data = WidgetRepository.load(context)
        if (!data.bound) return
        val lead = ReminderPrefs.leadMinutes(context).toLong()
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        var slot = 0
        // 排「今天 + 明天」两天：晚上设置的“次日早课”当天已过、只靠次日 00:05 重排易漏
        // （rearm 没准时触发就收不到）；提前把明天的也排上，更稳。
        dayLoop@ for (dayOffset in 0L..1L) {
            val date = today.plusDays(dayOffset)
            for (c in WidgetRepository.coursesOn(data, date)) {
                if (slot >= MAX_SLOTS) break@dayLoop
                val start = WidgetRepository.startTime(c) ?: continue
                val end = WidgetRepository.endTime(c)
                val startDateTime = LocalDateTime.of(date, start)
                val triggerAt = startDateTime.minusMinutes(lead)
                if (triggerAt.isBefore(now)) continue // 已过，跳过
                val info = ReminderInfo(
                    name = c.name,
                    location = c.location,
                    startText = hhmm(start),
                    endText = end?.let { hhmm(it) }.orEmpty(),
                    startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    lead = lead.toInt(),
                )
                val pi = showIntent(context, BASE_CODE + slot, info)
                val atMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                } catch (_: SecurityException) {
                    am.set(AlarmManager.RTC_WAKEUP, atMillis, pi) // 无精确闹钟权限时退化
                }
                slot++
            }
        }
    }

    private fun hhmm(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    private fun scheduleRearm(context: Context, am: AlarmManager) {
        val next = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(0, 5))
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_RESCHEDULE }
        val pi = PendingIntent.getBroadcast(context, RESCHEDULE_CODE, intent, flags())
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun showIntent(context: Context, code: Int, info: ReminderInfo?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SHOW
            if (info != null) {
                putExtra("name", info.name)
                putExtra("location", info.location)
                putExtra("start", info.startText)
                putExtra("end", info.endText)
                putExtra("startMillis", info.startMillis)
                putExtra("lead", info.lead)
            }
        }
        return PendingIntent.getBroadcast(context, code, intent, flags())
    }

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}

private data class ReminderInfo(
    val name: String,
    val location: String,
    val startText: String,   // HH:mm
    val endText: String,     // HH:mm（可能为空）
    val startMillis: Long,   // 上课开始时刻（用于算“还有几分钟”）
    val lead: Int,           // 配置的提前分钟（startMillis 不可用时兜底）
)
