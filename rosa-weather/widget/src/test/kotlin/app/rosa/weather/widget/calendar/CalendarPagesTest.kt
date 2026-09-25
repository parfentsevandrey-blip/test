package app.rosa.weather.widget.calendar

import android.content.ComponentName
import android.content.Context
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.R
import app.rosa.weather.widget.motion.LiveWeather
import app.rosa.weather.widget.provider.CalendarWidgetProvider
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.render.DynamicTones
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import app.rosa.weather.widget.render.calendar.CalendarRenderer
import app.rosa.weather.widget.render.calendar.CalendarView
import app.rosa.weather.widget.render.calendar.SeasonClock
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Months drawn ahead: a page kept on disk comes back exactly as it was drawn, only for the day
 * and settings it was drawn for, and what it shows of the weather changes only with what can be
 * seen on it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarPagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = WidgetRenderer(context)
    private val provider = ComponentName(context, CalendarWidgetProvider::class.java)
    private val locale = Locale.forLanguageTag("ru-RU")
    private val config = WidgetKind.Calendar.defaultConfig
    private val today = LocalDate.of(2026, 9, 25)
    private val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
    private val forecast = SampleForecast.create(nowEpochSeconds = now)
    private val content = WidgetContent("Москва", true, forecast, now, Units())
    private val wall = listOf(SizeF(314f, 252f))
    private val id = 42

    @After
    fun tearDown() {
        CalendarPages.forget(context, intArrayOf(id))
    }

    private fun page(month: YearMonth, stamp: Int = 7, shows: Int = 1, sizes: List<SizeF> = listOf(SizeF(314f, 252f), SizeF(380f, 200f))): CalendarPages.Page {
        val view = CalendarView(month, today, locale = locale)
        val drawn = sizes.map { size ->
            val (bitmap, targets) = renderer.renderCalendar(
                WidgetRenderRequest(size.width, size.height, config, content, 22f, systemNight = false, live = true, calendar = view),
                2f,
            )
            CalendarPages.Size(size, bitmap, targets)
        }
        return CalendarPages.Page(month, today, stamp, shows, LiveWeather.ofSeason(config, SeasonClock.weekFor(month, today)), 22f, drawn)
    }

    @Test
    fun `a page kept on disk comes back exactly as it was drawn`() {
        val october = YearMonth.of(2026, 10)
        val drawn = page(october)
        CalendarPages.save(context, id, drawn)
        CalendarPages.clearMemory()

        val started = System.nanoTime()
        val back = CalendarPages.load(context, id, october, today, stamp = 7)
        val loadMs = (System.nanoTime() - started) / 1e6
        assertThat(back).isNotNull()
        back!!
        assertThat(back.month).isEqualTo(october)
        assertThat(back.shows).isEqualTo(1)
        assertThat(back.live).isEqualTo(drawn.live)
        assertThat(back.radius).isEqualTo(22f)
        assertThat(back.sizes.map { it.size }).isEqualTo(drawn.sizes.map { it.size })
        back.sizes.zip(drawn.sizes).forEach { (b, d) ->
            assertThat(b.bitmap.sameAs(d.bitmap)).isTrue()
            assertThat(b.targets).isEqualTo(d.targets)
        }

        // What a tap does with it: the launcher's views, nothing drawn.
        val flipStarted = System.nanoTime()
        val views = back.views(context, provider, id, locale)
        val flipMs = (System.nanoTime() - flipStarted) / 1e6
        println("CalendarPages: load from disk %.1f ms, views %.1f ms (%d KB)".format(loadMs, flipMs, back.bytes / 1024))
        val host = FrameLayout(context)
        val root = views.apply(context, host)
        host.addView(root)
        val density = context.resources.displayMetrics.density
        val pw = (314f * density).roundToInt()
        val ph = (252f * density).roundToInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, pw, ph)
        assertThat(root.findViewById<ViewGroup>(R.id.widget_targets).childCount).isEqualTo(46)
        assertThat(root.findViewById<View>(R.id.widget_image).contentDescription.toString()).isEqualTo("Октябрь 2026. Пятница, 25. Неделя 42: Камин в замке")
    }

    @Test
    fun `a page is good only for its day and its settings`() {
        val october = YearMonth.of(2026, 10)
        CalendarPages.save(context, id, page(october, stamp = 7, shows = 1, sizes = wall))
        for (fromDisk in listOf(false, true)) {
            if (fromDisk) CalendarPages.clearMemory()
            assertThat(CalendarPages.load(context, id, october, today, stamp = 8)).isNull()
            assertThat(CalendarPages.load(context, id, october, today.plusDays(1), stamp = 7)).isNull()
            assertThat(CalendarPages.load(context, id, october.plusMonths(1), today, stamp = 7)).isNull()
            assertThat(CalendarPages.has(context, id, october, today, stamp = 7, shows = 1)).isTrue()
            assertThat(CalendarPages.has(context, id, october, today, stamp = 7, shows = 2)).isFalse()
            assertThat(CalendarPages.has(context, id, october, today, stamp = 8, shows = 1)).isFalse()
        }
        assertThat(CalendarPages.load(context, id, october, today, stamp = 7)).isNotNull()
    }

    @Test
    fun `pages further off are forgotten`() {
        val months = (8..11).map { YearMonth.of(2026, it) }
        months.forEach { CalendarPages.save(context, id, page(it, sizes = wall)) }
        CalendarPages.forget(context, intArrayOf(id), keep = months.take(3))
        CalendarPages.clearMemory()
        months.take(3).forEach { assertThat(CalendarPages.load(context, id, it, today, stamp = 7)).isNotNull() }
        assertThat(CalendarPages.load(context, id, months.last(), today, stamp = 7)).isNull()
        CalendarPages.forget(context, intArrayOf(id))
        assertThat(CalendarPages.load(context, id, months.first(), today, stamp = 7)).isNull()
    }

    @Test
    fun `a page's stamp is made of text, the same in every process`() {
        // Enums and plain objects hash by identity, which changes from process to process: pages
        // kept on disk would never match again. Their text is what goes into the stamp.
        val parts = WidgetKind.entries.map { it.defaultConfig } + listOf(DynamicTones.Fallback, SizeF(314f, 252f), locale)
        parts.forEach { assertThat(it.toString()).doesNotContainMatch("@[0-9a-f]{4,}") }
    }

    private fun shown(month: YearMonth, content: WidgetContent = this.content, sizes: List<SizeF> = wall) =
        CalendarRenderer.weatherShown(CalendarView(month, today, locale = locale), content, config, sizes)

    private fun withDay(date: LocalDate, code: Int): WidgetContent {
        val zone = ZoneOffset.ofHours(3)
        val daily = forecast.daily.map { day ->
            if (java.time.Instant.ofEpochSecond(day.time + 3600).atZone(zone).toLocalDate() == date) day.copy(weatherCode = code) else day
        }
        return content.copy(forecast = forecast.copy(daily = daily))
    }

    @Test
    fun `a page's weather changes only with what can be seen on it`() {
        val august = YearMonth.of(2026, 8)
        val september = YearMonth.of(2026, 9)
        val october = YearMonth.of(2026, 10)
        // October's grid opens on Monday 28 September: the forecast's days from then on are on it.
        assertThat(shown(october, withDay(LocalDate.of(2026, 10, 2), 95))).isNotEqualTo(shown(october))
        assertThat(shown(october, withDay(LocalDate.of(2026, 9, 26), 95))).isEqualTo(shown(october))
        assertThat(shown(september, withDay(LocalDate.of(2026, 9, 26), 95))).isNotEqualTo(shown(september))
        // August is all past: no forecast on it.
        assertThat(shown(august, withDay(LocalDate.of(2026, 10, 2), 95))).isEqualTo(shown(august))

        // Today's weather is under this month's name only; beside a wide month, on every one.
        val warmer = content.copy(forecast = forecast.copy(current = forecast.current.copy(temperature = forecast.current.temperature + 4)))
        assertThat(shown(september, warmer)).isNotEqualTo(shown(september))
        assertThat(shown(october, warmer)).isEqualTo(shown(october))
        val wide = listOf(SizeF(380f, 200f))
        assertThat(shown(october, warmer, wide)).isNotEqualTo(shown(october, sizes = wide))

        // Without a forecast there is no weather to show.
        assertThat(shown(october, content.copy(forecast = null))).isEqualTo(shown(august, content.copy(forecast = null)))
    }
}
