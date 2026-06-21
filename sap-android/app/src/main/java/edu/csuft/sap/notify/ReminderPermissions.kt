package edu.csuft.sap.notify

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 「上课提醒」可靠性所需的系统权限/设置检查与跳转。
 *
 * 杀后台/系统清后台后仍要准时收到提醒，依赖三件事（缺一就可能延迟或收不到）：
 * 1. **通知权限**（Android 13+）：否则通知被静默拦截。
 * 2. **精确闹钟**（Android 12+）：否则只能用不精确闹钟、可能晚到。
 * 3. **忽略电池优化 / 自启动**：否则进入 Doze 或被国产 ROM 冻结/清理后，到点不会被唤醒。
 *
 * 闹钟本身用 `setExactAndAllowWhileIdle` + manifest 注册的 [ReminderReceiver]（开机/换包重排），
 * 原生 Android 划掉后台后系统仍会拉起进程投递；以上权限是为覆盖 Doze 与国产 ROM 的省电策略。
 */
object ReminderPermissions {

    fun notificationsGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun exactAlarmGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
        return am.canScheduleExactAlarms()
    }

    fun batteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 精确闹钟设置页（API31+）；低版本无需返回 null。 */
    fun exactAlarmIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    }

    /** 请求忽略电池优化（系统弹窗，一键允许）。 */
    fun ignoreBatteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** 应用详情页（兜底：通知/自启动找不到专门入口时）。 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * 打开「自启动 / 后台启动」管理页。国产 ROM 无统一 API，按厂商逐个尝试，全失败回退应用详情页。
     * 在 UI 线程调用。
     */
    fun openAutoStart(context: Context) {
        val candidates = listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Intent().setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            Intent().setClassName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        )
        for (i in candidates) {
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (runCatching { context.startActivity(i); true }.getOrDefault(false)) return
        }
        runCatching { context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
