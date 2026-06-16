package edu.csuft.sap.notify

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

/** 接收上课提醒闹钟（弹通知）与「重排/开机」事件（重新排课前提醒）。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_SHOW -> showNotification(context, intent)
            ReminderScheduler.ACTION_RESCHEDULE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReminderScheduler.reschedule(context)
        }
    }

    private fun showNotification(context: Context, intent: Intent) {
        // 运行时通知权限（Android 13+）未授予则不弹
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val name = intent.getStringExtra("name") ?: "上课提醒"
        val location = intent.getStringExtra("location").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val lead = intent.getIntExtra("lead", 0)
        val text = buildString {
            append("${lead} 分钟后上课")
            if (time.isNotBlank()) append("  ·  $time")
            if (location.isNotBlank()) append("  ·  $location")
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(name.hashCode(), n) }
    }

    companion object {
        const val CHANNEL_ID = "class_reminder"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课前提醒通知"
            }
            nm.createNotificationChannel(ch)
        }
    }
}
