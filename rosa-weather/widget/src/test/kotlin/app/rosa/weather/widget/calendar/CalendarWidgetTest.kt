package app.rosa.weather.widget.calendar

import android.content.ComponentName
import android.content.Context
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.R
import app.rosa.weather.widget.provider.CalendarWidgetProvider
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import app.rosa.weather.widget.render.calendar.CalendarView
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The calendar widget as the launcher gets it: its picture, its tap targets, its month. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = WidgetRenderer(context)
    private val provider = ComponentName(context, CalendarWidgetProvider::class.java)
    private val today = LocalDate.of(2026, 9, 25)
    private val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
    private val view = CalendarView(YearMonth.of(2026, 9), today, locale = Locale.forLanguageTag("ru-RU"))

    private fun render(w: Float, h: Float) = renderer.renderCalendar(
        WidgetRenderRequest(
            w, h, WidgetKind.Calendar.defaultConfig,
            WidgetContent("Москва", true, SampleForecast.create(nowEpochSeconds = now), now, Units()),
            22f, systemNight = false, calendar = view,
        ),
        2f,
    )

    @Test
    fun `a wall calendar answers taps on its arrows, its name, plus and every day`() {
        val (_, targets) = render(314f, 252f)
        val bounds = RectF(0f, 0f, 314f, 252f)
        val header = listOfNotNull(targets.previous, targets.next, targets.title, targets.add)
        assertThat(header).hasSize(4)
        assertThat(targets.days).hasSize(42)
        (header + targets.days.map { it.second }).forEach { assertThat(bounds.contains(it)).isTrue() }
        // 1 September 2026 is a Tuesday: Monday 31 August opens the grid, the 25th is the 26th square.
        assertThat(targets.days.indexOfFirst { it.first == today }).isEqualTo(25)
        assertThat(targets.previous!!.right).isAtMost(targets.next!!.left + 1f)
        assertThat(targets.title!!.right).isAtMost(targets.previous!!.left + 1f)
    }

    @Test
    fun `the wide calendar keeps today on the left and the month on the right`() {
        val (_, targets) = render(314f, 162f)
        assertThat(targets.days).hasSize(42)
        assertThat(targets.days.minOf { it.second.left }).isAtLeast(314f * 0.36f - 1f)
    }

    @Test
    fun `a week strip answers each of its seven days, a tile opens today`() {
        assertThat(render(314f, 72f).second.days.map { it.first }).containsExactlyElementsIn((21..27).map { LocalDate.of(2026, 9, it) })
        assertThat(render(68f, 72f).second.days.map { it.first }).containsExactly(today)
    }

    @Test
    fun `remote views lay a target over every tap area`() {
        val (bitmap, targets) = render(314f, 252f)
        val views = RemoteViews(context.packageName, R.layout.widget_calendar).apply {
            setImageViewBitmap(R.id.widget_image, bitmap)
            setCalendarTargets(context, CalendarClicks(context, provider, 7, view), targets)
        }
        val host = FrameLayout(context)
        val root = views.apply(context, host)
        host.addView(root)
        val density = context.resources.displayMetrics.density
        val pw = (314f * density).roundToInt()
        val ph = (252f * density).roundToInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, pw, ph)
        val layer = root.findViewById<ViewGroup>(R.id.widget_targets)
        assertThat(layer.childCount).isEqualTo(46)
        val first = layer.getChildAt(0)
        val rect = targets.days.first().second
        // Where the renderer put the day, to the pixel (the launcher truncates dp, we round).
        assertThat(abs(first.left - (rect.left * density).roundToInt())).isAtMost(1)
        assertThat(abs(first.top - (rect.top * density).roundToInt())).isAtMost(1)
        assertThat(abs(first.width - (rect.width() * density).roundToInt())).isAtMost(1)
        assertThat(first.hasOnClickListeners()).isTrue()
        assertThat(first.contentDescription.toString()).isEqualTo("31 августа")
    }

    @Test
    fun `arrows move the month, and the next day brings it back`() {
        val navigation = CalendarNavigation(context)
        navigation.move(3, 1, today)
        navigation.move(3, 1, today)
        assertThat(navigation.offset(3, today)).isEqualTo(2)
        assertThat(navigation.offset(3, today.plusDays(1))).isEqualTo(0)
        navigation.move(3, -1, today)
        navigation.move(3, 0, today)
        assertThat(navigation.offset(3, today)).isEqualTo(0)
    }
}
