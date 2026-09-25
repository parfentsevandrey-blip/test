package app.rosa.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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
import app.rosa.weather.core.model.SavedPlaces
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTapAction
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.core.model.nextSceneChange
import app.rosa.weather.widget.calendar.CalendarAlarm
import app.rosa.weather.widget.calendar.CalendarChanges
import app.rosa.weather.widget.calendar.CalendarEvents
import app.rosa.weather.widget.calendar.CalendarNavigation
import app.rosa.weather.widget.calendar.CalendarPages
import app.rosa.weather.widget.calendar.views
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.motion.setLiveWeather
import app.rosa.weather.widget.provider.RosaWidgetProvider
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.provider.WidgetSizes
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import app.rosa.weather.widget.render.calendar.CalendarRenderer
import app.rosa.weather.widget.render.calendar.CalendarView
import app.rosa.weather.widget.render.calendar.SeasonClock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val navigation by lazy { CalendarNavigation(context) }

    /**
     * Draws the months either side of each calendar as whole pages ([CalendarPages]) once an
     * update is out, on a renderer of its own. Callers that keep the process alive for a while — a
     * broadcast, a job — wait for it with [settle], so it is never frozen half-way.
     */
    private val aheadRenderer by lazy { WidgetRenderer(context) }
    private val aheadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var aheadJob: Job? = null
    private val ahead = mutableListOf<Ahead>()
    /** What is still to be drawn ahead, newest first, one entry per widget; survives a cancelled run. */
    private val pending = LinkedHashMap<Int, Ahead>()
    private val manuallyRefreshing = mutableSetOf<Int>()

    override suspend fun onWeatherChanged(reason: SyncReason) {
        if (reason != SyncReason.Render) synchronized(manuallyRefreshing) { manuallyRefreshing.clear() }
        update()
        settle()
    }

    /** Waits for the pages being drawn ahead, so the caller keeps the process alive until they're kept. */
    suspend fun settle() {
        while (true) {
            val job = aheadJob ?: return
            job.join()
            if (aheadJob === job) return
        }
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
        // Whatever was being drawn ahead is for the picture about to be replaced.
        aheadJob?.cancel()
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
                val content = contentFor(id, config, units, saved, cache, inFlight, now)
                val views = runCatching {
                    if (config.face == WidgetFace.Calendar) calendarViews(id, manager, info, config, content)
                    else remoteViews(id, manager, info.provider, config, content)
                }.getOrNull() ?: continue
                runCatching { manager.updateAppWidget(id, views) }
            }
            startAhead()
            // The next tick serves *every* placed widget, not just the ones redrawn now — otherwise
            // resizing one widget could postpone another's "rain in 5 min" update.
            val stored = configs.snapshot()
            val nextTick = allWidgetIds(context).asList().mapNotNull { id ->
                resolvePlace(stored[id], saved)?.let { cache[it.id] }?.let { nextMeaningfulChange(it, now) }
            }.minOrNull() ?: (now + 30 * 60)
            scheduler.scheduleRenderTick(nextTick * 1000)
        }
    }

    private fun resolvePlace(config: WidgetConfig, saved: SavedPlaces): Place? = saved.forWidget(config.placeId)

    private fun contentFor(id: Int, config: WidgetConfig, units: Units, saved: SavedPlaces, cache: Map<String, Forecast>, inFlight: Set<String>, now: Long): WidgetContent {
        val place = resolvePlace(config, saved)
        val forecast = place?.let { cache[it.id] }
        val refreshing = (place != null && place.id in inFlight) ||
            synchronized(manuallyRefreshing) { id in manuallyRefreshing }
        return WidgetContent(
            placeName = place?.name.orEmpty(),
            isCurrentLocation = place?.isCurrentLocation == true,
            placeId = place?.id,
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
    }

    /**
     * Widgets that follow the app switch city together with it, and every widget notices a city
     * being added or removed — without waiting for the next sync. Call once per process.
     */
    fun followPlaceChanges(scope: CoroutineScope) {
        scope.launch {
            places.saved
                .map { saved -> saved.selected?.id to saved.all.map { it.id } }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    delay(250) // let a swipe through several cities settle first
                    if (hasWidgets(context)) update()
                }
        }
    }

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
        // Rain, snow or a storm right now: the launcher animates it over the picture.
        val moment = content.forecast?.takeIf { content.status == WidgetContent.Status.Ready }?.momentAt(content.nowEpochSeconds)
        val live = LiveWeather.of(config, moment)

        val bySize = sizes.associateWith { size ->
            val density = densityFor(size, budget)
            fun render(night: Boolean): Bitmap = renderer.render(
                WidgetRenderRequest(size.width, size.height, config, content, radius, night, dynamic, seed = widgetId, live = live != null),
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
                setLiveWeather(context.packageName, live, size.width, size.height, radius)
            }
        }
        return if (bySize.size == 1) bySize.values.first() else RemoteViews(bySize)
    }

    /**
     * A calendar: the month on display (the arrows may have moved it) with its days' events, when
     * they are wanted and allowed, drawn as a page at every size with tap targets laid over it.
     * The months around it are drawn next ([startAhead]), so the arrows find them ready.
     */
    private suspend fun calendarViews(
        widgetId: Int,
        manager: AppWidgetManager,
        info: AppWidgetProviderInfo,
        config: WidgetConfig,
        content: WidgetContent,
    ): RemoteViews {
        val frame = frame(widgetId, manager, info, config, content.nowEpochSeconds)
        if (frame.events) {
            // Watching the phone's calendars for edits, off the path of what's being drawn now.
            aheadScope.launch { runCatching { CalendarChanges.watch(context) } }
        }
        val view = CalendarView(frame.month, frame.today, events(frame, config, frame.month), frame.locale)
        val page = renderPage(renderer, frame, config, content, view)
        CalendarAlarm.scheduleMidnight(context, info.provider)
        ahead += Ahead(frame, info.provider, config, content, shown = page)
        return page.views(context, info.provider, widgetId, frame.locale)
    }

    /**
     * Shows the month the arrows just moved [widgetId] to from its page drawn ahead, when there is
     * one for today and the widget's settings and sizes: nothing is drawn, nothing waits for the
     * weather or the phone's calendars — the picture only goes to the launcher. False when the
     * month has to be drawn after all ([update]). Follow with [drawAhead].
     */
    suspend fun flip(widgetId: Int): Boolean = withContext(Dispatchers.Default) {
        aheadJob?.cancel()
        mutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            val info = manager.getAppWidgetInfo(widgetId) ?: return@withLock false
            val config = configs.get(widgetId)
            if (config.face != WidgetFace.Calendar) return@withLock false
            val frame = frame(widgetId, manager, info, config, clock.nowEpochSeconds())
            val page = CalendarPages.load(context, widgetId, frame.month, frame.today, frame.stamp) ?: return@withLock false
            runCatching { manager.updateAppWidget(widgetId, page.views(context, info.provider, widgetId, frame.locale)) }.isSuccess
        }
    }

    /**
     * After a [flip]: draws the months around the one [ids] show now, and redraws that one if the
     * weather or the events moved on since its page was drawn. See [settle].
     */
    suspend fun drawAhead(ids: IntArray) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            val units = settings.current().resolvedUnits()
            val saved = places.snapshot()
            val cache = weather.forecasts.first()
            val inFlight = weather.refreshing.value
            val now = clock.nowEpochSeconds()
            for (id in ids) {
                val info = manager.getAppWidgetInfo(id) ?: continue
                val config = configs.get(id)
                if (config.face != WidgetFace.Calendar) continue
                val content = contentFor(id, config, units, saved, cache, inFlight, now)
                ahead += Ahead(frame(id, manager, info, config, now), info.provider, config, content, shown = null)
            }
            startAhead()
        }
    }

    /** What a calendar's pages are drawn into: its sizes and their densities, its look, its day and month. */
    private class Frame(
        val widgetId: Int,
        val sizes: List<SizeF>,
        val densities: List<Float>,
        val radius: Float,
        val systemNight: Boolean,
        val dynamic: DynamicTones,
        val locale: Locale,
        /** It shows the phone's events, and may read them. */
        val events: Boolean,
        val today: LocalDate,
        val month: YearMonth,
        config: WidgetConfig,
        version: String,
    ) {
        /**
         * Everything a page depends on but its month, its day and what it shows of the weather and
         * the events. Made from text, not objects' hashes, so that it is the same in every process:
         * pages outlive the process that drew them.
         */
        val stamp: Int = listOf(version, config, sizes, densities, radius, systemNight, dynamic, locale, events).joinToString("|").hashCode()
    }

    /** This build of the app: pages an older one drew are not used. */
    private val appVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.longVersionCode}/${it.lastUpdateTime}" } }.getOrDefault("")
    }

    private fun frame(widgetId: Int, manager: AppWidgetManager, info: AppWidgetProviderInfo, config: WidgetConfig, now: Long): Frame {
        val sizes = WidgetSizes.from(manager.getAppWidgetOptions(widgetId), info)
        val budget = bitmapPixelBudget() / sizes.size
        val systemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dynamic = runCatching { DynamicTones.from(context) }.getOrDefault(DynamicTones.Fallback)
        val today = Instant.ofEpochSecond(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val month = YearMonth.from(today).plusMonths(navigation.offset(widgetId, today).toLong())
        val events = config.calendar.events && CalendarEvents.granted(context)
        return Frame(
            widgetId, sizes, sizes.map { densityFor(it, budget) }, cornerRadius(config), systemNight, dynamic,
            Locale.getDefault(), events, today, month, config, appVersion,
        )
    }

    /** The phone's events on [month]'s grid, when the widget shows them and may read them. */
    private fun events(frame: Frame, config: WidgetConfig, month: YearMonth): Map<LocalDate, List<Int>> {
        if (!frame.events) return emptyMap()
        val grid = CalendarMonth.of(month, frame.today, CalendarMonth.firstDayFor(config.calendar.weekStart, frame.locale))
        return CalendarEvents.read(context, grid.first, grid.last)
    }

    /** What a page of [view] shows of the weather and the events: when it is unchanged, so is the page. */
    private fun shows(frame: Frame, config: WidgetConfig, content: WidgetContent, view: CalendarView): Int =
        (CalendarRenderer.weatherShown(view, content, config, frame.sizes) + "|" + view.events.hashCode()).hashCode()

    /** [view]'s page as the launcher gets it, at every size of [frame]. */
    private suspend fun renderPage(renderer: WidgetRenderer, frame: Frame, config: WidgetConfig, content: WidgetContent, view: CalendarView): CalendarPages.Page {
        // The week's own motion over its picture: snow, sparks, petals, birds, leaves, lights.
        val live = LiveWeather.ofSeason(config, SeasonClock.weekFor(view.month, view.today))
        val sizes = frame.sizes.mapIndexed { i, size ->
            currentCoroutineContext().ensureActive()
            val request = WidgetRenderRequest(
                size.width, size.height, config, content, frame.radius, frame.systemNight, frame.dynamic,
                seed = frame.widgetId, live = live != null, calendar = view,
            )
            val (bitmap, targets) = renderer.renderCalendar(request, frame.densities[i])
            CalendarPages.Size(size, bitmap, targets)
        }
        return CalendarPages.Page(view.month, view.today, frame.stamp, shows(frame, config, content, view), live, frame.radius, sizes)
    }

    /** A calendar to draw ahead for; [shown] is the page it shows now when it was just drawn. */
    private class Ahead(
        val frame: Frame,
        val provider: ComponentName,
        val config: WidgetConfig,
        val content: WidgetContent,
        val shown: CalendarPages.Page?,
    )

    /**
     * Draws what [calendarViews] and [drawAhead] queued, outside the lock: keeps the page on show,
     * draws the months around it where their pages are missing or out of date, and forgets pages
     * further off. A newer update or flip cancels it (what it left undone is taken up again next
     * time); [settle] waits for it.
     */
    private fun startAhead() {
        val empty = synchronized(pending) {
            // The newest first; what a cancelled run left undone after it.
            val older = pending.values.filter { old -> ahead.none { it.frame.widgetId == old.frame.widgetId } }
            pending.clear()
            (ahead + older).forEach { pending[it.frame.widgetId] = it }
            pending.isEmpty()
        }
        ahead.clear()
        if (empty) return
        val previous = aheadJob
        previous?.cancel()
        aheadJob = aheadScope.launch {
            // One page at a time on this renderer: a cancelled run lets go of it first.
            previous?.join()
            while (true) {
                val request = synchronized(pending) { pending.values.firstOrNull() } ?: break
                runCatching { drawAround(request) }.onFailure { if (it is CancellationException) throw it }
                synchronized(pending) { if (pending[request.frame.widgetId] === request) pending.remove(request.frame.widgetId) }
            }
        }
    }

    private suspend fun drawAround(request: Ahead) {
        val frame = request.frame
        val id = frame.widgetId
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id) == null) {
            // Removed from the home screen meanwhile: nothing to keep for it.
            CalendarPages.forget(context, intArrayOf(id))
            return
        }
        val shown = request.shown
        if (shown != null) {
            if (!CalendarPages.has(context, id, shown.month, shown.today, shown.stamp, shown.shows)) CalendarPages.save(context, id, shown)
        } else {
            // Flipped to from a page drawn earlier: redrawn and shown again if the weather or the
            // events have moved on since.
            drawIfStale(request, frame.month)?.let { page ->
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    val manager = AppWidgetManager.getInstance(context)
                    val onShow = YearMonth.from(frame.today).plusMonths(navigation.offset(id, frame.today).toLong())
                    if (onShow == page.month) runCatching { manager.updateAppWidget(id, page.views(context, request.provider, id, frame.locale)) }
                }
            }
        }
        // In the arrows' likely order: on, back, the title's way home to this month, then on again
        // — so that two quick taps forward find both months ready.
        val around = listOf(frame.month.plusMonths(1), frame.month.minusMonths(1), YearMonth.from(frame.today), frame.month.plusMonths(2)).distinct() - frame.month
        for (month in around) drawIfStale(request, month)
        CalendarPages.forget(context, intArrayOf(id), keep = around + frame.month)
    }

    /** Draws and keeps [month]'s page unless the one kept already shows what it would; the page drawn, if any. */
    private suspend fun drawIfStale(request: Ahead, month: YearMonth): CalendarPages.Page? {
        currentCoroutineContext().ensureActive()
        val frame = request.frame
        val view = CalendarView(month, frame.today, events(frame, request.config, month), frame.locale)
        val shows = shows(frame, request.config, request.content, view)
        if (CalendarPages.has(context, frame.widgetId, month, frame.today, frame.stamp, shows)) return null
        val page = renderPage(aheadRenderer, frame, request.config, request.content, view)
        CalendarPages.save(context, frame.widgetId, page)
        return page
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
            .putExtra(EXTRA_PLACE_ID, content.placeId)
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
        // Rain starting or stopping, snow, a storm: the widget turns with the app's sky, not up to
        // half an hour later.
        forecast.nextSceneChange(now)?.let { candidates += it + 30 }
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
