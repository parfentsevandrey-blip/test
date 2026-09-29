package app.rosa.weather.widget.render

import android.content.Context
import android.content.res.Configuration
import android.util.SizeF
import app.rosa.weather.core.model.WidgetConfig
import kotlin.math.min
import kotlin.math.sqrt

/** How the pictures of a widget are drawn on this phone: their pixels, their corners, the theme. */
object WidgetPixels {
    /**
     * RemoteViews may carry at most ~1.5 screens of bitmap memory. We keep 20 % headroom; callers
     * spread the rest across every size variant, lowering pixel density only for huge widgets.
     */
    fun budget(context: Context): Long {
        val dm = context.resources.displayMetrics
        return (dm.widthPixels.toLong() * dm.heightPixels * 1.5 * 0.8).toLong()
    }

    /** Pixels per dp for a picture of [size] that has [pixelBudget] pixels to spend. */
    fun density(context: Context, size: SizeF, pixelBudget: Long): Float {
        val native = context.resources.displayMetrics.density
        val areaDp = size.width * size.height
        val maxDensity = sqrt(pixelBudget / areaDp.toDouble()).toFloat()
        return min(native, maxDensity).coerceAtLeast(1f)
    }

    /** The widget's corner radius in dp: its own, or the launcher's. */
    fun cornerRadius(context: Context, config: WidgetConfig): Float {
        if (config.cornerRadiusDp >= 0f) return config.cornerRadiusDp
        val res = context.resources
        val px = runCatching { res.getDimension(android.R.dimen.system_app_widget_background_radius) }.getOrDefault(0f)
        return if (px > 0f) px / res.displayMetrics.density else 24f
    }

    /** Whether the phone is in its dark theme. */
    fun systemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
