package app.rosa.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.edit
import app.rosa.calendar.R
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.calendar.CalendarPageRenderer
import java.util.Locale

/**
 * Android 15+ generated preview: the widget picker shows this week's calendar drawn by the real
 * renderer rather than a static picture. The platform rate-limits these calls, so it is published
 * once per app version, trying again at most once an hour when the platform turned it away.
 */
object CalendarPreviews {
    private const val PREFS = "calendar_previews"
    private const val KEY_VERSION = "published_version"
    private const val KEY_ATTEMPT = "last_attempt"
    private const val RETRY_MILLIS = 60 * 60 * 1000L

    fun publish(context: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(0L)
        if (!force && prefs.getLong(KEY_VERSION, -1) == version) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_ATTEMPT, 0L) in 0 until RETRY_MILLIS) return
        prefs.edit { putLong(KEY_ATTEMPT, now) }

        val manager = AppWidgetManager.getInstance(context) ?: return
        val seconds = now / 1000
        val content = WidgetContent(
            placeName = "",
            isCurrentLocation = true,
            forecast = SampleForecast.create(SampleForecast.Scenario.RainyAfternoon, nowEpochSeconds = seconds),
            nowEpochSeconds = seconds,
            units = Units.forCountry(Locale.getDefault().country),
        )
        val density = context.resources.displayMetrics.density.coerceAtMost(2.5f)
        val bitmap = CalendarPageRenderer(context).render(
            WidgetRenderRequest(300f, 290f, CalendarWidgetUpdater.DEFAULT_CONFIG, content, cornerRadiusDp = 24f, systemNight = false, dynamic = DynamicTones.Fallback),
            density,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_calendar).apply {
            setImageViewBitmap(R.id.widget_image, bitmap)
        }
        val ok = runCatching {
            manager.setWidgetPreview(ComponentName(context, CalendarWidgetProvider::class.java), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
        }.getOrDefault(false)
        if (ok) prefs.edit { putLong(KEY_VERSION, version) }
    }
}
