package app.rosa.calendar.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.rosa.calendar.data.CalendarSettings
import app.rosa.calendar.data.Occurrence
import app.rosa.calendar.ui.events.EventsScreen
import app.rosa.calendar.ui.month.MonthScreen
import app.rosa.calendar.ui.settings.SettingsScreen
import app.rosa.calendar.ui.widget.WidgetScreen
import app.rosa.calendar.ui.widget.WidgetTabState
import app.rosa.calendar.widget.CalendarWidgetUpdater
import app.rosa.calendar.widget.calendarContent
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.SavedPlaces
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WeatherSnapshot
import app.rosa.weather.widget.render.calendar.CalendarArt
import app.rosa.weather.widget.render.calendar.SeasonClock
import app.rosa.weather.widget.render.calendar.WeekArt
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the calendar's screens over their paintings to `build/screens/` for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class CalendarScreensTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 29)
    private val now = today.atTime(11, 0).atZone(zone).toEpochSecond()

    private val weather: WeatherState by lazy {
        val place = Place("geo:1", "Москва", 55.75, 37.62)
        val forecast = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now, placeId = place.id)
        val snapshot = WeatherSnapshot(
            units = Units(),
            places = SavedPlaces(places = listOf(place), followDeviceLocation = false, selectedId = place.id),
            forecasts = mapOf(place.id to forecast),
        )
        WeatherState.Lent(CalendarWeather.of(snapshot, now)!!)
    }

    private var nextId = 1L

    private fun event(date: LocalDate, hour: Int, minute: Int, minutes: Int, title: String, color: Long, location: String = ""): Occurrence {
        val begin = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        return Occurrence(nextId++, title, begin, begin + minutes * 60_000L, allDay = false, color = color.toInt(), location = location, calendar = "Личный")
    }

    private fun allDay(date: LocalDate, title: String, color: Long): Occurrence {
        val begin = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        return Occurrence(nextId++, title, begin, begin + 86_400_000L, allDay = true, color = color.toInt(), location = "", calendar = "Семья")
    }

    private val events: Map<LocalDate, List<Occurrence>> by lazy {
        mapOf(
            today to listOf(
                event(today, 10, 0, 30, "Стендап команды", 0xFF4F7DF3, "Zoom"),
                event(today, 13, 0, 60, "Обед с Аней", 0xFF3DAA6E, "Кафе «Пушкинъ»"),
                event(today, 19, 30, 60, "Йога", 0xFF9C6ADE),
            ),
            today.plusDays(1) to listOf(event(today.plusDays(1), 9, 0, 45, "Врач", 0xFFE0564F, "Клиника на Арбате")),
            today.plusDays(3) to listOf(allDay(today.plusDays(3), "День рождения мамы", 0xFFE8A13A), event(today.plusDays(3), 18, 0, 120, "Ужин у мамы", 0xFF3DAA6E)),
            today.plusDays(8) to listOf(event(today.plusDays(8), 11, 0, 90, "Презентация квартала", 0xFF4F7DF3, "Переговорная 4")),
            today.minusDays(6) to listOf(event(today.minusDays(6), 12, 0, 60, "Стрижка", 0xFF9C6ADE)),
            today.minusDays(12) to listOf(event(today.minusDays(12), 8, 0, 60, "Бассейн", 0xFF3AB6C8), event(today.minusDays(12), 20, 0, 60, "Кино", 0xFFE0564F)),
        )
    }

    @Before
    fun setUp() {
        // A Russian phone: weeks begin on Monday.
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
    }

    /** Waits [seconds] of real time too: pictures drawn off the main thread (the widget's) land meanwhile. */
    private fun capture(name: String, seconds: Int = 1): Bitmap {
        repeat(6 + seconds * 4) {
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
            Thread.sleep(if (it < 6) 40 else 250)
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    /** The app's frame over [art]: the painting (painted first, so the render never waits), the glass, the tabs. */
    private fun frame(tab: CalendarTab, art: WeekArt, night: Boolean, settings: CalendarSettings = CalendarSettings(), screen: @Composable () -> Unit) {
        val density = context.resources.displayMetrics.density
        val metrics = context.resources.displayMetrics
        val pixels = paintingPixels(metrics.widthPixels / density, metrics.heightPixels / density, density, sways = true)
        CalendarArt.painting(context, art, pixels.width, pixels.height, pixels.pxPerDp, live = art.motion != null, night = night)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RosaEnvironment(settings.asAppSettings(), PaintingPalette.of(art, night)) {
                PaintingBackdrop(art, night, live = true) {
                    TabScaffold(tab, {}) { screen() }
                }
            }
        }
    }

    private fun month(name: String, month: YearMonth, selected: LocalDate, night: Boolean = false, allowed: Boolean = true) {
        val art = WeekArt.of(SeasonClock.weekFor(month, today))
        frame(CalendarTab.Month, art, night) {
            MonthScreen(
                month = month,
                today = today,
                selected = selected,
                settings = CalendarSettings(),
                events = if (allowed) events else emptyMap(),
                weather = weather,
                eventsAllowed = allowed,
                onMonth = {},
                onSelect = {},
                onAllowEvents = {},
            )
        }
        capture(name)
    }

    @Test
    fun monthByDay() = month("calendar-month", YearMonth.from(today), today)

    @Test
    fun monthByMoonlight() = month("calendar-month-night", YearMonth.from(today), today.plusDays(3), night = true)

    @Test
    fun winterMonth() = month("calendar-month-winter", YearMonth.of(2027, 1), LocalDate.of(2027, 1, 14), allowed = false)

    @Test
    fun springMonth() = month("calendar-month-spring", YearMonth.of(2027, 5), LocalDate.of(2027, 5, 9))

    @Test
    fun eventsList() {
        frame(CalendarTab.Events, WeekArt.of(SeasonClock.weekOf(today)), night = false) {
            EventsScreen(
                showEvents = true,
                upcoming = events.filterKeys { !it.isBefore(today) },
                allowed = true,
                weather = weather,
                today = today,
                onOpenDay = {},
                onAllowEvents = {},
            )
        }
        capture("calendar-events")
    }

    @Test
    fun settings() {
        frame(CalendarTab.Settings, WeekArt.of(SeasonClock.weekOf(today)), night = false) {
            SettingsScreen(CalendarSettings(), allowed = true, weather = weather, update = {}, onAllowEvents = {})
        }
        capture("calendar-settings")
    }

    @Test
    fun widgetTab() {
        val lent = (weather as WeatherState.Lent)
        val snapshot = WeatherSnapshot(
            places = SavedPlaces(places = listOf(Place("geo:1", "Москва", 55.75, 37.62)), followDeviceLocation = false, selectedId = "geo:1"),
            forecasts = mapOf("geo:1" to SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now, placeId = "geo:1")),
        )
        check(lent.weather.placeName == "Москва")
        val content = calendarContent(CalendarWidgetUpdater.DEFAULT_CONFIG, snapshot, now)
        frame(CalendarTab.Widget, WeekArt.of(SeasonClock.weekOf(today)), night = false) {
            WidgetScreen(WidgetTabState(placed = emptyList(), fresh = content))
        }
        capture("calendar-widget", seconds = 12)
    }
}
