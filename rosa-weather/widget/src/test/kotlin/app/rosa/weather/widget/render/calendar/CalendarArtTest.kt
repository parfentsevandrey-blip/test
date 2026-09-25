package app.rosa.weather.widget.render.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The paintings are made once and kept: in memory, and on disk for after the process has gone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarArtTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun fresh() {
        CalendarArt.clear()
        File(context.cacheDir, "calendar-art").deleteRecursively()
    }

    @Test
    fun `a month is painted the same way every time, and once`() {
        val first = CalendarArt.painting(null, WeekArt.ofMonth(9), 220, 176, 2f, live = false)
        assertThat(CalendarArt.painting(null, WeekArt.ofMonth(9), 220, 176, 2f, live = false)).isSameInstanceAs(first)
        CalendarArt.clear()
        val again = CalendarArt.painting(null, WeekArt.ofMonth(9), 220, 176, 2f, live = false)
        assertThat(again).isNotSameInstanceAs(first)
        assertThat(again.sameAs(first)).isTrue()
        // Without the falling leaves when the live tiles bring them.
        assertThat(CalendarArt.painting(null, WeekArt.ofMonth(9), 220, 176, 2f, live = true).sameAs(first)).isFalse()
    }

    @Test
    fun `the disk keeps a painting for after the process is gone`() {
        val painted = CalendarArt.painting(context, WeekArt.ofMonth(12), 200, 160, 2f, live = false)
        val file = File(context.cacheDir, "calendar-art/w${WeekArt.ofMonth(12).week}-200x160@200-still-v${CalendarArt.VERSION}.png")
        val deadline = System.currentTimeMillis() + 10_000
        while (!file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(file.exists()).isTrue()
        CalendarArt.clear()
        val restored = CalendarArt.painting(context, WeekArt.ofMonth(12), 200, 160, 2f, live = false)
        assertThat(restored).isNotSameInstanceAs(painted)
        assertThat(restored.sameAs(painted)).isTrue()
    }

    @Test
    fun `a month already painted redraws in a moment`() {
        val renderer = WidgetRenderer(context)
        val today = LocalDate.of(2026, 10, 12)
        val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
        val content = WidgetContent("Москва", true, SampleForecast.create(nowEpochSeconds = now), now, Units())
        val view = CalendarView(YearMonth.from(today), today, locale = Locale.forLanguageTag("ru-RU"))
        val request = WidgetRenderRequest(314f, 252f, WidgetKind.Calendar.defaultConfig, content, 22f, systemNight = false, calendar = view)
        val cold = System.nanoTime().also { renderer.renderCalendar(request, 2.75f) }.let { System.nanoTime() - it }
        val warm = System.nanoTime().also { renderer.renderCalendar(request, 2.75f) }.let { System.nanoTime() - it }
        println("calendar render: cold ${cold / 1_000_000} ms, warm ${warm / 1_000_000} ms")
        // The warm one only draws the numbers over a kept pane.
        assertThat(warm).isLessThan(cold / 4)
    }
}
