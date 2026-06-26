package com.example.calendarwidget.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.example.calendarwidget.data.ColorUtils
import com.example.calendarwidget.data.EventCategory
import com.example.calendarwidget.data.WidgetSettings
import kotlin.math.min

/**
 * Resolved colour tokens for one render pass of the widget — mirrors the token
 * table in `WidgetMonth.dc.html -> renderVals()` (раздел 7). The widget follows
 * the *device* theme ([dark]); the settings-preview theme is app-only.
 */
class WidgetPalette(settings: WidgetSettings, val dark: Boolean) {
    val accentInt = ColorUtils.parse(settings.accent)
    val accent = Color(accentInt)
    val contrast = Color(ColorUtils.contrastOn(accentInt))

    val text = if (dark) Color(0xFFF1F1F6) else Color(0xFF1B1B23)
    val muted = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.42f) else Color(0xFF16141C).copy(alpha = 0.45f)
    val weekendMuted = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.30f) else Color(0xFF16141C).copy(alpha = 0.32f)
    val weekendNum = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.60f) else Color(0xFF16141C).copy(alpha = 0.52f)
    val outMonth = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.18f) else Color(0xFF14141C).copy(alpha = 0.20f)
    val hairline = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.10f) else Color(0xFF141428).copy(alpha = 0.10f)
    val navBg = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.06f) else Color(0xFF141428).copy(alpha = 0.05f)

    // Glassmorphism is emulated with a translucent fill over the wallpaper (Glance
    // has no backdrop blur — раздел 5). Dark: rgba(20,20,28,opacity); light lifted +0.32.
    val glass = if (dark) {
        Color(0xFF14141C).copy(alpha = settings.bgOpacity)
    } else {
        Color(0xFFFFFFFF).copy(alpha = min(0.95f, settings.bgOpacity + 0.32f))
    }

    fun categoryColor(category: EventCategory): Color =
        if (category == EventCategory.WORK) accent else Color(category.fallbackColor)

    fun provider(color: Color) = ColorProvider(color)
}
