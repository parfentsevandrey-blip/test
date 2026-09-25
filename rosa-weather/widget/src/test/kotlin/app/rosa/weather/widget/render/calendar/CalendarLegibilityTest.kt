package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The type reads on every week's picture: each of the 52 rendered as a 4×3 calendar in the
 * picture's own styles — the season's glass and plain glass — by day and in the dark theme, and
 * the contrast of every day's number measured from the pixels themselves: the cores of the
 * digits' strokes against what lies round them in their cell, held to WCAG's 4.5:1 for text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarLegibilityTest {
    private val renderer = WidgetRenderer(ApplicationProvider.getApplicationContext())
    private val ru = Locale.forLanguageTag("ru-RU")

    @Test
    fun everyWeekReadsInEveryThemeAndStyle() {
        val (w, h) = 314f to 252f
        // A phone's own density: at less, the strokes of the digits never reach their full colour.
        val density = 3f
        val base = WidgetConfig(face = WidgetFace.Calendar, style = WidgetStyle.Sky, opacity = 1f)
        val looks = listOf(
            "сезон" to base.copy(theme = WidgetTheme.Light),
            "сезон, тёмная" to base.copy(theme = WidgetTheme.Dark),
            "стекло" to base.copy(style = WidgetStyle.Glass, opacity = 0.72f, theme = WidgetTheme.Light),
            "стекло, тёмная" to base.copy(style = WidgetStyle.Glass, opacity = 0.72f, theme = WidgetTheme.Dark),
        )
        val report = StringBuilder()
        var worst = Float.MAX_VALUE
        val failures = mutableListOf<String>()
        val only = System.getProperty("rosa.legibility.weeks")?.split(',')?.map { it.trim().toInt() }
        for (week in only ?: (1..SeasonClock.WEEKS).toList()) {
            // A Wednesday in the week, so the whole week's picture is the one shown.
            val today = LocalDate.ofYearDay(2026, ((week - 1) * 7 + 3).coerceAtMost(364))
            val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
            val content = WidgetContent("Москва", true, SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now), now, Units())
            val view = CalendarView(YearMonth.from(today), today, locale = ru)
            for ((name, config) in looks) {
                val request = WidgetRenderRequest(w, h, config, content, 22f, systemNight = config.theme == WidgetTheme.Dark, calendar = view)
                val (bitmap, targets) = renderer.renderCalendar(request, density)
                val days = targets.days.filter { (date, _) -> YearMonth.from(date) == view.month && date != today }
                val contrasts = days.map { (_, cell) -> digitContrast(bitmap, cell, density) }
                val least = contrasts.min()
                val weakest = days[contrasts.indexOf(least)].first
                if (System.getProperty("rosa.legibility.debug") != null) {
                    CalendarArt.clear()
                    val (fresh, _) = renderer.renderCalendar(request, density)
                    val again = days.map { (_, cell) -> digitContrast(fresh, cell, density) }.min()
                    if (kotlin.math.abs(again - least) > 0.05f) report.append("  !! week $week $name: %.2f from the caches, %.2f fresh%n".format(Locale.ROOT, least, again))
                }
                if (System.getProperty("rosa.legibility.debug") != null && least < TEXT) debugImage(bitmap, days, contrasts, "w$week-$name", density)
                val title = targets.title?.let { digitContrast(bitmap, it, density, whole = true) } ?: 99f
                worst = min(worst, least)
                report.append("week %2d %-15s days ≥ %.2f (%s)  title %.2f%n".format(Locale.ROOT, week, name, least, weakest, title))
                if (least < TEXT) failures += "week $week, $name: a day at %.2f".format(least)
                if (title < TEXT) failures += "week $week, $name: the month's name at %.2f".format(title)
            }
        }
        println(report)
        println("The least contrast of any day of any week: %.2f".format(worst))
        assertWithMessage("type too faint:\n" + failures.joinToString("\n")).that(failures).isEmpty()
    }

    /**
     * The contrast of the type in [area] (dp at [density]) as the eye takes it: the ground is the
     * area's middle tone; the type is its strongest few pixels on the far side of the ground — the
     * cores of the strokes, past their soft edges.
     */
    private fun digitContrast(bitmap: Bitmap, area: RectF, density: Float, whole: Boolean = false): Float {
        // The number sits in the top part of its cell; the forecast's figures under it are left out.
        val box = if (whole) area else RectF(area.left + area.width() * 0.2f, area.top + area.height() * 0.05f, area.right - area.width() * 0.2f, area.top + area.height() * 0.62f)
        val x0 = (box.left * density).roundToInt().coerceIn(0, bitmap.width - 1)
        val x1 = (box.right * density).roundToInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (box.top * density).roundToInt().coerceIn(0, bitmap.height - 1)
        val y1 = (box.bottom * density).roundToInt().coerceIn(y0 + 1, bitmap.height)
        val lums = FloatArray((x1 - x0) * (y1 - y0))
        var i = 0
        for (y in y0 until y1) for (x in x0 until x1) lums[i++] = Contrast.luminance(bitmap.getPixel(x, y))
        lums.sort()
        val ground = lums[lums.size / 2]
        // The cores of the strokes: a thin "1" covers only a few percent of its box.
        val dark = lums[(lums.size * 0.004f).toInt()]
        val light = lums[(lums.size * 0.996f).toInt().coerceAtMost(lums.size - 1)]
        // The type is whichever extreme stands further from the ground.
        return max(Contrast.ratio(ground, dark), Contrast.ratio(ground, light))
    }

    private fun debugImage(bitmap: Bitmap, days: List<Pair<LocalDate, RectF>>, contrasts: List<Float>, name: String, density: Float) {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(copy)
        canvas.scale(density, density)
        val p = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 0.5f }
        val t = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 6f }
        for ((k, pair) in days.withIndex()) {
            val cell = pair.second
            val box = RectF(cell.left + cell.width() * 0.2f, cell.top + cell.height() * 0.05f, cell.right - cell.width() * 0.2f, cell.top + cell.height() * 0.62f)
            p.color = if (contrasts[k] < TEXT) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
            canvas.drawRect(box, p)
            t.color = p.color
            canvas.drawText("%.1f".format(Locale.ROOT, contrasts[k]), cell.left, cell.bottom, t)
        }
        java.io.File("build/widget-gallery/legibility").apply { mkdirs() }.let { dir ->
            java.io.File(dir, "$name.png").outputStream().use { copy.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /**
     * A render never depends on what was drawn before it: the same page drawn with empty caches,
     * again after the same week at another density and after other weeks and looks, and after the
     * caches were emptied, comes out the same to the pixel.
     */
    @Test
    fun rendersDoNotLeakIntoEachOther() {
        val base = WidgetConfig(face = WidgetFace.Calendar, style = WidgetStyle.Sky, opacity = 1f, theme = WidgetTheme.Light)
        fun render(week: Int, config: WidgetConfig, density: Float = 2f): IntArray {
            val today = LocalDate.ofYearDay(2026, (week - 1) * 7 + 3)
            val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
            val content = WidgetContent("Москва", true, SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now), now, Units())
            val view = CalendarView(YearMonth.from(today), today, locale = ru)
            val (bitmap, _) = renderer.renderCalendar(WidgetRenderRequest(314f, 252f, config, content, 22f, systemNight = config.theme == WidgetTheme.Dark, calendar = view), density)
            return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        }
        fun differing(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }
        CalendarArt.clear()
        val first = render(39, base)
        CalendarArt.clear()
        // The same week measured and kept at a phone's other density first: it must not stand in.
        render(39, base, density = 3f)
        render(12, base.copy(theme = WidgetTheme.Dark))
        render(12, base.copy(style = WidgetStyle.Glass, opacity = 0.72f))
        val after = render(39, base)
        assertWithMessage("pixels changed by what was drawn before").that(differing(after, first)).isEqualTo(0)
        CalendarArt.clear()
        val again = render(39, base)
        assertWithMessage("pixels changed once the caches were emptied").that(differing(again, first)).isEqualTo(0)
    }

    companion object {
        /** WCAG AA for text. */
        private const val TEXT = Contrast.TEXT
    }
}
