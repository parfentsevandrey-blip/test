package app.rosa.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.util.SizeF
import android.widget.RemoteViews
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.core.data.sync.SyncScheduler
import app.rosa.weather.core.data.sync.WeatherSyncListener
import app.rosa.weather.core.data.util.WallClock
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Headline
import app.rosa.weather.core.model.Headlines
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTapAction
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.widget.provider.RosaWidgetProvider
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.provider.WidgetSizes
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Renders and pushes every widget. Called after each sync (as a [WeatherSyncListener]), on
 * resize, on clock/locale changes and on scheduled render ticks, so the picture always matches
 * *now* — not the moment the data was downloaded.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weather: WeatherRepository,
    private val places: PlacesRepository,
    private val settings: SettingsRepository,
    private val configs: WidgetConfigRepository,
    private val scheduler: SyncScheduler,
    private val clock: WallClock,
) : WeatherSyncListener {
    private val mutex = Mutex()
    private val renderer by lazy { WidgetRenderer(context) }
    private val manuallyRefreshing = mutableSetOf<Int>()

    override suspend fun onWeatherChanged(reason: SyncReason) {
        if (reason != SyncReason.Render) synchronized(manuallyRefreshing) { manuallyRefreshing.clear() }
        update()
    }

    suspend fun markRefreshing(widgetId: Int) {
        synchronized(manuallyRefreshing) { manuallyRefreshing += widgetId }
        update(intArrayOf(widgetId))
    }

    suspend fun anyStale(ids: IntArray): Boolean {
        val saved = places.snapshot()
        val now = clock.nowEpochSeconds()
        val cache = weather.forecasts.first()
        val maxAge = settings.current().refreshIntervalMinutes * 60L
        return ids.any { id ->
            val place = resolvePlace(configs.get(id), saved) ?: return@any true
            val forecast = cache[place.id] ?: return@any true
            forecast.ageSeconds(now) > maxAge
        }
    }

    /** Re-renders [ids], or every placed widget when null. */
    suspend fun update(ids: IntArray? = null) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            val targets = ids ?: allWidgetIds(context)
            if (targets.isEmpty()) return@withLock
            val appSettings = settings.current()
            val units = appSettings.resolvedUnits()
            val saved = places.snapshot()
            val cache = weather.forecasts.first()
            val inFlight = weather.refreshing.value
            val now = clock.nowEpochSeconds()

            for (id in targets) {
                val info = manager.getAppWidgetInfo(id) ?: continue
                // Atomic put-if-absent: never clobber a config the studio saved meanwhile.
                val config = configs.getOrPut(id, WidgetKind.forProvider(info.provider.className).defaultConfig)
                val place = resolvePlace(config, saved)
                val forecast = place?.let { cache[it.id] }
                val refreshing = (place != null && place.id in inFlight) ||
                    synchronized(manuallyRefreshing) { id in manuallyRefreshing }
                val content = WidgetContent(
                    placeName = place?.name.orEmpty(),
                    isCurrentLocation = place?.isCurrentLocation ?: (config.placeId == Place.CURRENT_ID),
                    forecast = forecast,
                    nowEpochSeconds = now,
                    units = units,
                    refreshing = refreshing,
                    status = when {
                        place == null -> WidgetContent.Status.NeedsLocation
                        forecast == null -> if (refreshing) WidgetContent.Status.Loading else WidgetContent.Status.NoData
                        else -> WidgetContent.Status.Ready
                    },
                )
                val views = runCatching { remoteViews(id, manager, info.provider, config, content) }.getOrNull() ?: continue
                runCatching { manager.updateAppWidget(id, views) }
            }
            // The next tick serves *every* placed widget, not just the ones redrawn now — otherwise
            // resizing one widget could postpone another's "rain in 5 min" update.
            val stored = configs.snapshot()
            val nextTick = allWidgetIds(context).asList().mapNotNull { id ->
                resolvePlace(stored[id], saved)?.let { cache[it.id] }?.let { nextMeaningfulChange(it, now) }
            }.minOrNull() ?: (now + 30 * 60)
            scheduler.scheduleRenderTick(nextTick * 1000)
        }
    }

    private fun resolvePlace(config: WidgetConfig, saved: app.rosa.weather.core.model.SavedPlaces): Place? =
        saved.find(config.placeId) ?: if (config.placeId == Place.CURRENT_ID) null else saved.all.firstOrNull()

    private fun remoteViews(
        widgetId: Int,
        manager: AppWidgetManager,
        provider: ComponentName,
        config: WidgetConfig,
        content: WidgetContent,
    ): RemoteViews {
        val info = manager.getAppWidgetInfo(widgetId)
        val sizes = WidgetSizes.from(manager.getAppWidgetOptions(widgetId), info)
        val dualTheme = config.style == WidgetStyle.Tonal && config.theme == WidgetTheme.Auto
        val bitmapsPerSize = if (dualTheme) 2 else 1
        val budget = bitmapPixelBudget() / (sizes.size * bitmapsPerSize)
        val systemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dynamic = runCatching { DynamicTones.from(context) }.getOrDefault(DynamicTones.Fallback)
        val radius = cornerRadius(config)
        val description = renderer.describe(content)
        val click = clickIntent(widgetId, provider, config, content)

        val bySize = sizes.associateWith { size ->
            val density = densityFor(size, budget)
            fun render(night: Boolean): Bitmap = renderer.render(
                WidgetRenderRequest(size.width, size.height, config, content, radius, night, dynamic, seed = widgetId),
                density,
            )
            RemoteViews(context.packageName, R.layout.widget_canvas).apply {
                if (dualTheme) {
                    setIcon(R.id.widget_image, "setImageIcon", Icon.createWithBitmap(render(false)), Icon.createWithBitmap(render(true)))
                } else {
                    setImageViewBitmap(R.id.widget_image, render(systemNight))
                }
                setContentDescription(R.id.widget_image, description)
                setOnClickPendingIntent(R.id.widget_root, click)
            }
        }
        return if (bySize.size == 1) bySize.values.first() else RemoteViews(bySize)
    }

    /**
     * RemoteViews may carry at most ~1.5 screens of bitmap memory. We keep 20 % headroom and
     * spread the rest across every size variant, lowering pixel density only for huge widgets.
     */
    private fun bitmapPixelBudget(): Long {
        val dm = context.resources.displayMetrics
        return (dm.widthPixels.toLong() * dm.heightPixels * 1.5 * 0.8).toLong()
    }

    private fun densityFor(size: SizeF, pixelBudget: Long): Float {
        val native = context.resources.displayMetrics.density
        val areaDp = size.width * size.height
        val maxDensity = sqrt(pixelBudget / areaDp.toDouble()).toFloat()
        return min(native, maxDensity).coerceAtLeast(1f)
    }

    private fun cornerRadius(config: WidgetConfig): Float {
        if (config.cornerRadiusDp >= 0f) return config.cornerRadiusDp
        val res = context.resources
        val px = runCatching { res.getDimension(android.R.dimen.system_app_widget_background_radius) }.getOrDefault(0f)
        return if (px > 0f) px / res.displayMetrics.density else 24f
    }

    private fun clickIntent(widgetId: Int, provider: ComponentName, config: WidgetConfig, content: WidgetContent): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val wantsRefresh = content.status == WidgetContent.Status.NoData ||
            (config.tapAction == WidgetTapAction.Refresh && content.status == WidgetContent.Status.Ready)
        if (wantsRefresh) {
            val intent = Intent(RosaWidgetProvider.ACTION_REFRESH).setComponent(provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            return PendingIntent.getBroadcast(context, widgetId, intent, flags)
        }
        val launch = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_PLACE_ID, config.placeId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getActivity(context, widgetId, launch, flags)
    }

    /**
     * When should this widget next look different even without new data? Minute-precise
     * nowcast sentences need ~5-minute ticks; otherwise the next hour boundary or sun event.
     */
    private fun nextMeaningfulChange(forecast: Forecast, now: Long): Long {
        // Hour boundaries come from the data: in UTC+5:30 or +9:30 zones local hours start at :30.
        val nextHour = forecast.hourly.firstOrNull { it.time > now }?.time ?: ((now / 3600 + 1) * 3600)
        val candidates = mutableListOf(now + 30 * 60, nextHour + 60)
        val headline = Headlines.pick(forecast, forecast.momentAt(now))
        if (headline is Headline.PrecipitationStarts || headline is Headline.PrecipitationEnds) candidates += now + 5 * 60
        forecast.dayAt(now)?.let { day ->
            listOfNotNull(day.sunrise, day.sunset).filter { it > now }.forEach { candidates += it + 30 }
        }
        return candidates.min().coerceAtLeast(now + 60)
    }

    companion object {
        const val EXTRA_PLACE_ID = "app.rosa.weather.extra.PLACE_ID"

        fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
            return WidgetKind.entries.flatMap { kind ->
                manager.getAppWidgetIds(ComponentName(context, kind.providerClass)).asList()
            }.toIntArray()
        }

        fun hasWidgets(context: Context) = allWidgetIds(context).isNotEmpty()
    }
}
