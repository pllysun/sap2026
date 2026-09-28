package edu.csuft.sap.widget

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils

internal class WidgetPalette(context: Context) {
    private val accent = context.getSharedPreferences("sap_theme", Context.MODE_PRIVATE)
        .getInt("accent", 0xFF3564DC.toInt())
    val tint = ColorUtils.blendARGB(accent, Color.WHITE, .88f)
    val ink: Int = run {
        var value = accent
        repeat(12) { if (ColorUtils.calculateContrast(value, Color.WHITE) < 4.5) value = ColorUtils.blendARGB(value, Color.BLACK, .12f) }
        value
    }
}
