package app.rosa.calendar.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.calendar.data.Agenda
import app.rosa.calendar.data.CalendarSettings
import app.rosa.calendar.data.CalendarSettingsRepository
import app.rosa.calendar.data.Occurrence
import app.rosa.calendar.weather.SharedWeather
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.DailyPoint
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WeatherSnapshot
import app.rosa.weather.core.model.momentAt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The weather Rosa Weather lends for the city it has open, as the calendar shows it. */
data class CalendarWeather(
    val placeName: String,
    val isCurrentLocation: Boolean,
    val units: Units,
    val zone: ZoneId,
    /** The forecast's days, by their date where the forecast is. */
    val days: Map<LocalDate, DailyPoint>,
    /** The weather right now. */
    val now: ForecastMoment,
    val fetchedAt: Long,
) {
    companion object {
        fun of(snapshot: WeatherSnapshot, nowEpochSeconds: Long): CalendarWeather? {
            val place = snapshot.places.forWidget(Place.FOLLOW_APP_ID) ?: return null
            val forecast = snapshot.forecasts[place.id] ?: return null
            val zone = WeatherFormat.zoneOf(forecast.timezone, forecast.utcOffsetSeconds)
            return CalendarWeather(
                placeName = place.name,
                isCurrentLocation = place.isCurrentLocation,
                units = snapshot.units,
                zone = zone,
                days = forecast.daily.associateBy { Instant.ofEpochSecond(it.time + 3600).atZone(zone).toLocalDate() },
                now = forecast.momentAt(nowEpochSeconds),
                fetchedAt = forecast.fetchedAt,
            )
        }
    }
}

/** Where the weather stands: lent, not there yet, turned off, or no weather app to lend it. */
sealed interface WeatherState {
    data class Lent(val weather: CalendarWeather) : WeatherState
    data object Waiting : WeatherState
    data object Off : WeatherState
    data object NoWeatherApp : WeatherState
}

/**
 * Everything the calendar's screens show: today, the settings, the weather Rosa Weather lends and
 * the phone's events — the months around the one on screen, and the month ahead for the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: CalendarSettingsRepository,
    private val weather: SharedWeather,
) : ViewModel() {
    val settings: StateFlow<CalendarSettings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Bumped whenever the app comes back to the front: the day, the permission or the weather may have moved on. */
    private val resumes = MutableStateFlow(0)
    private val allowed = MutableStateFlow(Agenda.granted(context))

    /** Whether the phone's events may be read. */
    val eventsAllowed: StateFlow<Boolean> = allowed

    /** Today, moving on at midnight while the app is open. */
    val today: StateFlow<LocalDate> = resumes
        .flatMapLatest { midnights() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val weatherState: StateFlow<WeatherState> = combine(
        weather.changes,
        resumes,
        settingsStore.settings.map { it.showWeather }.distinctUntilChanged(),
    ) { _, _, show -> show }
        .mapLatest { show ->
            when {
                !show -> WeatherState.Off
                !weather.installed() -> WeatherState.NoWeatherApp
                else -> {
                    val snapshot = weather.snapshot(listOf(Place.FOLLOW_APP_ID))
                    val now = System.currentTimeMillis() / 1000
                    val lent = snapshot?.let { CalendarWeather.of(it, now) }
                    if (snapshot != null && (lent == null || now - lent.fetchedAt > snapshot.refreshMinutes * 60L + 20 * 60)) {
                        // Old or missing: the weather app fetches it and says so when it has it.
                        weather.refresh()
                    }
                    if (lent != null) WeatherState.Lent(lent) else WeatherState.Waiting
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherState.Waiting)

    /** The month on screen: its events and those of the months either side are read. */
    private val shown = MutableStateFlow(YearMonth.now())

    /** The phone's events on the months around the one on screen, by day. */
    val monthEvents: StateFlow<Map<LocalDate, List<Occurrence>>> = combine(
        shown,
        today,
        eventsWanted(),
        calendarEdits(),
    ) { month, today, wanted, _ -> Triple(month, today, wanted) }
        .mapLatest { (month, today, wanted) ->
            if (!wanted) return@mapLatest emptyMap()
            val firstDay = CalendarMonth.firstDayFor(settings.value?.weekStart ?: app.rosa.weather.core.model.WeekStart.Auto, Locale.getDefault())
            val from = CalendarMonth.of(month.minusMonths(1), today, firstDay).first
            val to = CalendarMonth.of(month.plusMonths(1), today, firstDay).last
            withContext(Dispatchers.IO) { Agenda.read(context, from, to) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The phone's events of the month ahead, by day, for the list. */
    val upcoming: StateFlow<Map<LocalDate, List<Occurrence>>?> = combine(today, eventsWanted(), calendarEdits()) { today, wanted, _ -> today to wanted }
        .mapLatest { (today, wanted) ->
            if (!wanted) emptyMap() else withContext(Dispatchers.IO) { Agenda.read(context, today, today.plusDays(UPCOMING_DAYS - 1L)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun show(month: YearMonth) {
        shown.value = month
    }

    /** The app is in front again; also after the permission dialog closes. */
    fun resumed() {
        allowed.value = Agenda.granted(context)
        resumes.update { it + 1 }
    }

    fun update(transform: (CalendarSettings) -> CalendarSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    /** Whether events are shown and may be read, again after each resume. */
    private fun eventsWanted(): Flow<Boolean> =
        combine(allowed, settingsStore.settings.map { it.showEvents }.distinctUntilChanged(), resumes) { allowed, show, _ -> allowed && show }
            .distinctUntilChanged()

    /** Ticks when the phone's calendars change, while they may be read. */
    private fun calendarEdits(): Flow<Int> = allowed.flatMapLatest { granted ->
        if (!granted) {
            flow { emit(0) }
        } else {
            var count = 0
            callbackFlow {
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(++count)
                    }
                }
                val registered = runCatching { context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }.isSuccess
                awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
            }.onStart { emit(0) }
        }
    }.mapLatest { value ->
        // An edit writes several rows: read once they have settled.
        if (value != 0) delay(600)
        value
    }

    private fun midnights(): Flow<LocalDate> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone)
            emit(now.toLocalDate())
            val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            delay(java.time.Duration.between(now, next).toMillis() + 1_000)
        }
    }

    companion object {
        const val UPCOMING_DAYS = 31
    }
}
