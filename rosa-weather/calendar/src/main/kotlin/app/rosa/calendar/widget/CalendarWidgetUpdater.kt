package app.rosa.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews
import app.rosa.calendar.weather.SharedWeather
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.util.WallClock
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WeatherSnapshot
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.provider.WidgetSizes
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetPixels
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.calendar.CalendarPageRenderer
import app.rosa.weather.widget.render.calendar.CalendarRenderer
import app.rosa.weather.widget.render.calendar.CalendarView
import app.rosa.weather.widget.render.calendar.SeasonClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Draws and pushes every calendar widget: the month on display (the arrows may have moved it)
 * over its week's painting, with the phone's events and the weather Rosa Weather lends
 * ([SharedWeather]). Called when a widget is placed or resized, when the weather app says its
 * weather changed, at midnight, on calendar edits and on clock or locale changes.
 */
@Singleton
class CalendarWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configs: WidgetConfigRepository,
    private val weather: SharedWeather,
    private val clock: WallClock,
) {
    private val mutex = Mutex()
    private val renderer by lazy { CalendarPageRenderer(context) }
    private val navigation by lazy { CalendarNavigation(context) }

    /**
     * Draws the months either side of each calendar as whole pages ([CalendarPages]) once an
     * update is out, on a renderer of its own. Callers that keep the process alive for a while — a
     * broadcast, a job — wait for it with [settle], so it is never frozen half-way.
     */
    private val aheadRenderer by lazy { CalendarPageRenderer(context) }
    private val aheadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var aheadJob: Job? = null
    private val ahead = mutableListOf<Ahead>()

    /** What is still to be drawn ahead, newest first, one entry per widget; survives a cancelled run. */
    private val pending = LinkedHashMap<Int, Ahead>()

    /** Waits for the pages being drawn ahead, so the caller keeps the process alive until they're kept. */
    suspend fun settle() {
        while (true) {
            val job = aheadJob ?: return
            job.join()
            if (aheadJob === job) return
        }
    }

    /** Redraws [ids], or every placed calendar when null. */
    suspend fun update(ids: IntArray? = null) = withContext(Dispatchers.Default) {
        // Whatever was being drawn ahead is for the picture about to be replaced.
        aheadJob?.cancel()
        mutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            val targets = ids ?: allWidgetIds(context)
            if (targets.isEmpty()) return@withLock
            val now = clock.nowEpochSeconds()
            val placed = targets.asList().mapNotNull { id ->
                val info = manager.getAppWidgetInfo(id) ?: return@mapNotNull null
                // Atomic put-if-absent: never clobber a config the studio saved meanwhile.
                Triple(id, info, configs.getOrPut(id, DEFAULT_CONFIG))
            }
            if (placed.isEmpty()) return@withLock
            // One call to the weather app for all of them.
            val snapshot = weather.snapshot(placed.map { it.third.placeId })
            for ((id, info, config) in placed) {
                val content = calendarContent(config, snapshot, now)
                val views = runCatching { calendarViews(id, manager, info, config, content) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: continue
                runCatching { manager.updateAppWidget(id, views) }
            }
            startAhead()
            if (snapshot != null && placed.any { (_, _, config) -> stale(config, snapshot, now) }) {
                // Old or missing weather: the weather app fetches it and says so when it has it.
                aheadScope.launch { weather.refresh() }
            }
        }
    }

    /** Whether [config]'s place has weather older than the weather app lets it grow, or none. */
    private fun stale(config: WidgetConfig, snapshot: WeatherSnapshot, now: Long): Boolean {
        val place = snapshot.places.forWidget(config.placeId) ?: return false
        if (place.id in snapshot.refreshing) return false
        val forecast = snapshot.forecasts[place.id] ?: return true
        return forecast.ageSeconds(now) > snapshot.refreshMinutes * 60L + STALE_SLACK_SECONDS
    }

    /**
     * A calendar: the month on display with its days' events, when they are wanted and allowed,
     * drawn as a page at every size with tap targets laid over it. The months around it are drawn
     * next ([startAhead]), so the arrows find them ready.
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
            val now = clock.nowEpochSeconds()
            val placed = ids.asList().mapNotNull { id -> manager.getAppWidgetInfo(id)?.let { Triple(id, it, configs.get(id)) } }
                .filter { it.third.face == WidgetFace.Calendar }
            if (placed.isEmpty()) return@withLock
            val snapshot = weather.snapshot(placed.map { it.third.placeId })
            for ((id, info, config) in placed) {
                val content = calendarContent(config, snapshot, now)
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
        val budget = WidgetPixels.budget(context) / sizes.size
        val dynamic = runCatching { DynamicTones.from(context) }.getOrDefault(DynamicTones.Fallback)
        val today = Instant.ofEpochSecond(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val month = YearMonth.from(today).plusMonths(navigation.offset(widgetId, today).toLong())
        val events = config.calendar.events && CalendarEvents.granted(context)
        return Frame(
            widgetId, sizes, sizes.map { WidgetPixels.density(context, it, budget) }, WidgetPixels.cornerRadius(context, config),
            WidgetPixels.systemNight(context), dynamic, Locale.getDefault(), events, today, month, config, appVersion,
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
    private suspend fun renderPage(renderer: CalendarPageRenderer, frame: Frame, config: WidgetConfig, content: WidgetContent, view: CalendarView): CalendarPages.Page {
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

    companion object {
        /** A new calendar: the month over its season's painting, edge to edge. */
        val DEFAULT_CONFIG = WidgetConfig(face = WidgetFace.Calendar, style = WidgetStyle.Sky, opacity = 1f)

        /** Weather this much older than the weather app's own refresh interval is asked for again. */
        private const val STALE_SLACK_SECONDS = 20 * 60L

        fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
            return manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
        }

        fun hasWidgets(context: Context) = allWidgetIds(context).isNotEmpty()
    }
}

/**
 * What a calendar with [config] shows of the weather Rosa Weather lent in [snapshot] (null when it
 * lent none): its place's forecast, resolved as the weather app's own widgets resolve it.
 */
internal fun calendarContent(config: WidgetConfig, snapshot: WeatherSnapshot?, now: Long): WidgetContent {
    val units = snapshot?.units ?: Units.forCountry(Locale.getDefault().country)
    val place = snapshot?.places?.forWidget(config.placeId)
    val forecast = place?.let { snapshot.forecasts[it.id] }
    val refreshing = place != null && place.id in snapshot.refreshing
    return WidgetContent(
        placeName = place?.name.orEmpty(),
        isCurrentLocation = place?.isCurrentLocation == true,
        forecast = forecast,
        nowEpochSeconds = now,
        units = units,
        refreshing = refreshing,
        status = when {
            snapshot == null -> WidgetContent.Status.NoData
            place == null -> WidgetContent.Status.NeedsLocation
            forecast == null -> if (refreshing) WidgetContent.Status.Loading else WidgetContent.Status.NoData
            else -> WidgetContent.Status.Ready
        },
        placeId = place?.id,
    )
}
