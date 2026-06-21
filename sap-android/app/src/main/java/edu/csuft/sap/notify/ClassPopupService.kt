package edu.csuft.sap.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import edu.csuft.sap.MainActivity
import edu.csuft.sap.R
import kotlin.math.abs

/**
 * 上课「弹窗提醒」：悬浮窗（[WindowManager] + TYPE_APPLICATION_OVERLAY）在屏幕顶部弹现代卡片，
 * 盖在其它应用之上。展示课程名 / 起止时间 / 地点 / 还有几分钟开始，右侧课表图标。
 *
 * 交互：[AUTO_DISMISS_MS]（10s）后自动消失；**可上/下/左/右任一方向滑动关闭**；点「进入课表」按钮才跳课表
 * （点卡片本身不跳，避免误触）。
 */
class ClassPopupService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var wm: WindowManager? = null
    private val autoDismiss = Runnable { dismissAnimated() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (intent == null || !canShow(this)) { stopSelf(); return START_NOT_STICKY }

        val c = ReminderContent(
            name = intent.getStringExtra("name") ?: "上课提醒",
            location = intent.getStringExtra("location").orEmpty(),
            start = intent.getStringExtra("start").orEmpty(),
            end = intent.getStringExtra("end").orEmpty(),
            startMillis = intent.getLongExtra("startMillis", 0L),
            lead = intent.getIntExtra("lead", 0),
        )
        showOverlay(c)
        vibrate()
        handler.removeCallbacks(autoDismiss)
        handler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
        return START_NOT_STICKY
    }

    private fun showOverlay(c: ReminderContent) {
        removeView()
        val ctx = this
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val accent = Color.parseColor("#0EA5E9")

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = px(20).toFloat(); setColor(Color.WHITE) }
            elevation = px(12).toFloat()
            setPadding(px(20), px(18), px(20), px(18))
        }

        // 上半部分：左文字列 + 右课表图标
        val topRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(ctx).apply {
            text = "🔔  上课提醒"; setTextColor(accent); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(ctx).apply {
            text = c.name; setTextColor(Color.parseColor("#111111")); textSize = 21f
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, px(10), 0, 0)
        })
        col.addView(TextView(ctx).apply {
            text = ReminderNotifier.timeLine(c); setTextColor(Color.parseColor("#5F6368")); textSize = 14f
            setPadding(0, px(6), 0, 0)
        })
        val remainWrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, px(12), 0, 0)
            addView(TextView(ctx).apply {
                text = ReminderNotifier.remainText(c); setTextColor(accent); textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(px(12), px(7), px(12), px(7))
                background = GradientDrawable().apply { cornerRadius = px(10).toFloat(); setColor(Color.parseColor("#E8F5FE")) }
            })
        }
        col.addView(remainWrap)
        val icon = ImageView(ctx).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(px(52), px(52)).apply { marginStart = px(14) }
            background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(Color.parseColor("#F1F5F9")) }
            val p = px(6); setPadding(p, p, p, p)
        }
        topRow.addView(col)
        topRow.addView(icon)

        val enterBtn = TextView(ctx).apply {
            text = "进入课表"; setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, px(12), 0, px(12))
            background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(accent) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(16) }
            setOnClickListener {
                runCatching {
                    startActivity(Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                }
                stopSelf()
            }
        }

        card.addView(topRow)
        card.addView(enterBtn)
        attachSwipeToDismiss(card, px(96))

        val wmgr = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = px(48)
            width = resources.displayMetrics.widthPixels - px(24)
        }
        runCatching {
            wmgr.addView(card, params)
            card.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 220 })
            wm = wmgr
            view = card
        }.onFailure { stopSelf() }
    }

    /** 上/下/左/右任一方向滑动关闭（点击「进入课表」按钮不受影响，它自行消费点击）。 */
    private fun attachSwipeToDismiss(card: View, dismissPx: Int) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var dragging = false
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        v.translationX = dx
                        v.translationY = dy // 上下左右都跟手
                        val prog = (maxOf(abs(dx), abs(dy)) / dismissPx).coerceIn(0f, 1f)
                        v.alpha = 1f - prog * 0.6f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 用 DOWN→UP 实际位移判断（对快速滑动也生效，不依赖 MOVE 累计）：上/下/左/右任一方向超阈值即关闭
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dismissPx || abs(dy) > dismissPx) {
                        handler.removeCallbacks(autoDismiss)
                        // 朝主导轴方向滑出屏幕
                        val anim = v.animate().alpha(0f).setDuration(150).withEndAction { stopSelf() }
                        if (abs(dx) >= abs(dy)) anim.translationX(if (dx >= 0) v.width.toFloat() else -v.width.toFloat())
                        else anim.translationY(if (dy >= 0) v.height.toFloat() * 1.5f else -v.height.toFloat() * 1.5f)
                        anim.start()
                    } else {
                        v.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(160).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dismissAnimated() {
        val v = view
        if (v != null) v.animate().alpha(0f).setDuration(180).withEndAction { stopSelf() }.start()
        else stopSelf()
    }

    private fun vibrate() {
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            vib.vibrate(android.os.VibrationEffect.createOneShot(220, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun removeView() {
        val v = view; val w = wm
        if (v != null && w != null) runCatching { w.removeView(v) }
        view = null; wm = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismiss)
        removeView()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureFgsChannel(this)
        val n = NotificationCompat.Builder(this, FGS_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("上课提醒")
            .setContentText("正在弹出上课提醒")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FGS_NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FGS_NOTIF_ID, n)
        }
    }

    companion object {
        private const val AUTO_DISMISS_MS = 10_000L // 10 秒自动消失
        private const val FGS_CHANNEL = "class_popup_fgs"
        private const val FGS_NOTIF_ID = 19100

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

        private fun ensureFgsChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(FGS_CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(FGS_CHANNEL, "上课弹窗", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "弹出上课提醒时的后台运行提示"
                },
            )
        }
    }
}
