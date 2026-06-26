package com.example.calendarwidget.data

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color

/** Colour helpers ported from `WidgetMonth.dc.html` (contrast + hex parsing). */
object ColorUtils {

    /**
     * Relative-luminance contrast pick from the handoff:
     * `L = 0.2126·R + 0.7152·G + 0.0722·B; L > 0.62 -> #0B0B0F else #FFFFFF`.
     */
    @ColorInt
    fun contrastOn(@ColorInt color: Int): Int {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
        return if (l > 0.62f) 0xFF0B0B0F.toInt() else 0xFFFFFFFF.toInt()
    }

    /** Parses `#RGB` / `#RRGGBB`, returning an opaque ARGB int (fallback on error). */
    @ColorInt
    fun parse(hex: String, @ColorInt fallback: Int = 0xFF7C9CFF.toInt()): Int {
        return runCatching {
            var h = hex.trim().removePrefix("#")
            if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            (0xFF000000.toInt()) or (h.toLong(16).toInt() and 0x00FFFFFF)
        }.getOrDefault(fallback)
    }

    fun composeColor(hex: String): Color = Color(parse(hex))
}
