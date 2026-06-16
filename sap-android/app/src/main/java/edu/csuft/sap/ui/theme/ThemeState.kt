package edu.csuft.sap.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 全局「辅色 / 强调色」：简约白为主，辅色亮一点，默认天蓝。
 * 改后整个 App 的 `colorScheme.primary`（底栏选中/FAB/今日高亮/开关/选周/选中态…）随之变。
 * 用 Compose 快照状态 + SharedPreferences 持久化。
 */
object ThemeState {
    private const val PREFS = "sap_theme"
    private const val KEY = "accent"

    /** 默认辅色：天蓝。 */
    val DEFAULT = Color(0xFF2E9BEF)

    /** 预设辅色（亮色系，配简约白）。 */
    val PRESETS: List<Pair<String, Color>> = listOf(
        "天蓝" to Color(0xFF2E9BEF),
        "宝蓝" to Color(0xFF3B5BDB),
        "青绿" to Color(0xFF12B886),
        "薄荷" to Color(0xFF20C997),
        "活力橙" to Color(0xFFFF922B),
        "珊瑚红" to Color(0xFFFF6B6B),
        "玫红" to Color(0xFFE64980),
        "葡萄紫" to Color(0xFF7C5CFC),
        "薰衣草" to Color(0xFF9775FA),
        "墨黑" to Color(0xFF2B2F38),
    )

    var accent by mutableStateOf(DEFAULT)
        private set

    fun load(context: Context) {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT.toArgb())
        accent = Color(v)
    }

    fun setAccent(context: Context, color: Color) {
        accent = color
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, color.toArgb()).apply()
    }
}
