package app.rosa.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.edit
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer

/**
 * Android 15+ generated previews: the widget picker shows each style rendered by the real
 * engine rather than a static screenshot. The platform rate-limits these calls, so we publish
 * once per app version (and on package replacement), trying again at most once an hour when the
 * platform turned some away.
 */
object WidgetPreviews {
    private const val PREFS = "widget_previews"
    private const val KEY_VERSION = "published_version"
    private const val KEY_ATTEMPT = "last_attempt"
    private const val RETRY_MILLIS = 60 * 60 * 1000L

    fun publish(context: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(0L)
        if (!force && prefs.getLong(KEY_VERSION, -1) == version) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_ATTEMPT, 0L) in 0 until RETRY_MILLIS) return
        prefs.edit { putLong(KEY_ATTEMPT, now) }

        val manager = AppWidgetManager.getInstance(context) ?: return
        val renderer = WidgetRenderer(context)
        val seconds = now / 1000
        val forecast = SampleForecast.create(SampleForecast.Scenario.RainyAfternoon, nowEpochSeconds = seconds)
        val content = WidgetContent(
            placeName = context.getString(R.string.widget_preview_place),
            isCurrentLocation = true,
            forecast = forecast,
            nowEpochSeconds = seconds,
            units = Units.forCountry(java.util.Locale.getDefault().country),
        )
        val density = context.resources.displayMetrics.density
        var allOk = true
        for (kind in WidgetKind.entries) {
            val (w, h) = when (kind) {
                WidgetKind.Glass -> 320f to 170f
                WidgetKind.Sky -> 170f to 170f
                WidgetKind.Almanac -> 250f to 260f
                WidgetKind.Calendar -> 300f to 270f
            }
            val bitmap = renderer.render(
                WidgetRenderRequest(w, h, kind.defaultConfig, content, cornerRadiusDp = 24f, systemNight = false, dynamic = DynamicTones.Fallback),
                density.coerceAtMost(2.5f),
            )
            val views = RemoteViews(context.packageName, R.layout.widget_canvas).apply {
                setImageViewBitmap(R.id.widget_image, bitmap)
            }
            val ok = runCatching {
                manager.setWidgetPreview(ComponentName(context, kind.providerClass), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
            }.getOrDefault(false)
            allOk = allOk && ok
        }
        if (allOk) prefs.edit { putLong(KEY_VERSION, version) }
    }
}
