package app.rosa.weather.widget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import androidx.core.os.BundleCompat

/** Every exact size (dp) a launcher may show this widget at — usually portrait and landscape. */
internal object WidgetSizes {
    fun from(options: Bundle, info: AppWidgetProviderInfo?): List<SizeF> {
        val exact = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.filter { it.width > 0 && it.height > 0 }
            ?.distinct()
        if (!exact.isNullOrEmpty()) return exact.take(MAX_SIZES)

        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (minW > 0 && maxH > 0) {
            // Launchers report min width with max height for portrait, and the reverse for landscape.
            return listOf(SizeF(minW.toFloat(), maxH.toFloat()), SizeF(maxW.toFloat(), minH.toFloat())).distinct()
        }
        val fallbackW = info?.minWidth?.takeIf { it > 0 }?.toFloat() ?: 250f
        val fallbackH = info?.minHeight?.takeIf { it > 0 }?.toFloat() ?: 110f
        return listOf(SizeF(fallbackW, fallbackH))
    }

    /** RemoteViews accepts a bounded map of sizes; foldables rarely report more than four. */
    private const val MAX_SIZES = 4
}
