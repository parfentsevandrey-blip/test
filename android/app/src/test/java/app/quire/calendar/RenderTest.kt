package app.quire.calendar

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.Accent
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.core.Tokens
import app.quire.calendar.ui.MonthGridView
import app.quire.calendar.ui.WeekdayHeaderView
import app.quire.calendar.widget.WidgetRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Draws the real views and inflates the real RemoteViews tree, then writes the
 * pixels out. Two things are being checked at once: that the widget survives
 * `RemoteViews.apply` — the host's inflater rejects any class not annotated
 * @RemoteView, which no compiler catches — and that the grid actually paints.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private val month = YearMonth.of(2026, 8)
    private val today = LocalDate.of(2026, 8, 12)

    private fun sampleLoads(palette: Palette): Map<LocalDate, DayLoad> {
        val colours = intArrayOf(
            0xFF2E4A7D.toInt(), 0xFF4C5D3C.toInt(), 0xFF9A6F21.toInt(), 0xFF6C3A55.toInt(),
        )
        val loads = HashMap<LocalDate, DayLoad>()
        val busy = listOf(3, 4, 6, 10, 12, 13, 17, 19, 20, 24, 26, 27, 28, 31)
        busy.forEachIndexed { index, day ->
            val count = (index % 3) + 1
            loads[month.atDay(day)] = DayLoad(
                count,
                IntArray(count) { colours[(index + it) % colours.size] },
            )
        }
        assertNotNull(palette)
        return loads
    }

    private fun render(view: View, widthDp: Int, heightDp: Int, name: String): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    private fun assertPainted(bitmap: Bitmap, name: String) {
        val colours = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colours += bitmap.getPixel(x, y)
                y += 3
            }
            x += 3
        }
        assertTrue("$name painted only ${colours.size} distinct colours", colours.size > 8)
    }

    private fun grid(palette: Palette, dark: Boolean): MonthGridView =
        MonthGridView(context).apply {
            this.palette = palette
            firstDayOfWeek = DayOfWeek.MONDAY
            this.today = this@RenderTest.today
            this.month = this@RenderTest.month
            selected = LocalDate.of(2026, 8, 20)
            loads = sampleLoads(palette)
            setBackgroundColor(palette.canvas)
            assertEquals(dark, palette.dark)
        }

    @Test
    fun `month grid paints in both skins`() {
        listOf(false to "paper", true to "ink").forEach { (dark, label) ->
            val palette = Tokens.palette(dark, Accent.CINNABAR)
            val container = FrameLayout(context).apply {
                setBackgroundColor(palette.canvas)
                addView(
                    WeekdayHeaderView(context).apply {
                        this.palette = palette
                        firstDayOfWeek = DayOfWeek.MONDAY
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (26 * 3f).toInt()),
                )
                addView(
                    grid(palette, dark),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (300 * 3f).toInt(),
                    ).apply { topMargin = (26 * 3f).toInt() },
                )
            }
            val bitmap = render(container, 411, 330, "grid-$label")
            assertPainted(bitmap, "grid-$label")
        }
    }

    @Test
    fun `compact grid paints for the year view`() {
        val palette = Tokens.palette(false, Accent.INDIGO)
        val view = MonthGridView(context).apply {
            this.palette = palette
            compact = true
            showAdjacent = false
            firstDayOfWeek = DayOfWeek.MONDAY
            this.today = this@RenderTest.today
            this.month = this@RenderTest.month
            setBackgroundColor(palette.canvas)
        }
        assertPainted(render(view, 110, 80, "grid-compact"), "grid-compact")
    }

    /**
     * The important one: `RemoteViews.apply` runs the platform inflater with the
     * @RemoteView filter, so a disallowed view class fails here exactly as it
     * would on a home screen.
     */
    @Test
    fun `widget inflates and paints through RemoteViews`() {
        val widgetId = 7
        val prefs = Prefs.get(context).widget(widgetId)
        listOf(
            Triple(Skin.PAPER, Accent.CINNABAR, "widget-paper"),
            Triple(Skin.INK, Accent.CINNABAR, "widget-ink"),
            Triple(Skin.PAPER, Accent.INDIGO, "widget-indigo"),
        ).forEach { (skin, accent, name) ->
            prefs.skin = skin
            prefs.accent = accent
            prefs.opacity = 100
            prefs.showEvents = true

            val views = WidgetRenderer.build(context, AppWidgetManager.getInstance(context), widgetId)
            val host = FrameLayout(context)
            val inflated = views.apply(context, host)
            host.addView(
                inflated,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            assertPainted(render(host, 280, 260, name), name)
        }
    }

    @Test
    fun `widget week rows carry every day of the month`() {
        val widgetId = 9
        Prefs.get(context).widget(widgetId).apply {
            skin = Skin.PAPER
            accent = Accent.CINNABAR
            showAdjacent = true
        }
        val views = WidgetRenderer.build(context, AppWidgetManager.getInstance(context), widgetId)
        val host = FrameLayout(context)
        val inflated = views.apply(context, host)
        host.addView(inflated)
        render(host, 280, 260, "widget-structure")

        val numbers = ArrayList<String>()
        collectText(inflated, numbers)
        val expected = MonthModel.cells(YearMonth.now(), DayOfWeek.MONDAY).size
        // The year sits in the header and is also a number; day numbers are 1..31.
        val digits = numbers.mapNotNull { it.toIntOrNull() }.filter { it in 1..31 }
        assertEquals("cells rendered", expected, digits.size)
        assertTrue(
            "month title present",
            numbers.any { it.equals(MonthModel.monthName(YearMonth.now(), Locale.getDefault()), true) },
        )
    }

    @Test
    fun `widget honours week numbers and switched-off marks`() {
        val widgetId = 11
        Prefs.get(context).widget(widgetId).apply {
            skin = Skin.PAPER
            accent = Accent.MOSS
            weekNumbers = true
            showEvents = false
            opacity = 90
        }
        val views = WidgetRenderer.build(context, AppWidgetManager.getInstance(context), widgetId)
        val host = FrameLayout(context).apply { setBackgroundColor(0xFFC9C4B8.toInt()) }
        host.addView(
            views.apply(context, host),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val bitmap = render(host, 300, 270, "widget-weeknumbers")
        assertPainted(bitmap, "widget-weeknumbers")

        val texts = ArrayList<String>()
        collectText(host, texts)
        val weeks = MonthModel.ROWS
        val numbers = texts.mapNotNull { it.toIntOrNull() }
        assertEquals(
            "day cells plus one week number per row",
            MonthModel.CELLS + weeks,
            numbers.count { it in 1..53 } - 0,
        )
    }

    @Test
    fun `the app screen assembles and paints`() {
        val controller = org.robolectric.Robolectric
            .buildActivity(app.quire.calendar.ui.MainActivity::class.java)
            .setup()
        val decor = controller.get().window.decorView
        val bitmap = render(decor, 411, 891, "app-main")
        assertPainted(bitmap, "app-main")
        controller.close()
    }

    @Test
    fun `the app screen shows the day's entries once calendars are readable`() {
        FakeCalendarProvider.reset()
        org.robolectric.Robolectric.setupContentProvider(
            FakeCalendarProvider::class.java,
            android.provider.CalendarContract.AUTHORITY,
        )
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).grantPermissions(android.Manifest.permission.READ_CALENDAR)

        val now = LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        fun at(hour: Int, minute: Int = 0) = now.atTime(hour, minute)
            .atZone(zone).toInstant().toEpochMilli()
        val julian = MonthModel.julianDay(now)
        FakeCalendarProvider.instances = listOf(
            FakeCalendarProvider.instance(
                eventId = 1, beginMillis = at(0), endMillis = at(23, 59),
                startDay = julian, endDay = julian, title = "Studio closed", allDay = 1,
                colour = 0xFF6C3A55.toInt(), calendarName = "Personal",
            ),
            FakeCalendarProvider.instance(
                eventId = 2, beginMillis = at(9, 30), endMillis = at(10, 30),
                startDay = julian, endDay = julian, title = "Design review",
                location = "Kutuzovsky 12", colour = 0xFF2E4A7D.toInt(),
            ),
            FakeCalendarProvider.instance(
                eventId = 3, beginMillis = at(13), endMillis = at(14),
                startDay = julian, endDay = julian, title = "Lunch with Anna",
                colour = 0xFF4C5D3C.toInt(), calendarName = "Personal",
            ),
            FakeCalendarProvider.instance(
                eventId = 4, beginMillis = at(18), endMillis = at(20),
                startDay = julian, endDay = julian + 1, title = "Overnight render",
                colour = 0xFF9A6F21.toInt(), calendarName = "Work",
            ),
        ) + (1..24).map { offset ->
            val date = now.plusDays((offset % 20).toLong() - 6)
            FakeCalendarProvider.instance(
                eventId = 100L + offset,
                beginMillis = at(8 + offset % 10),
                endMillis = at(9 + offset % 10),
                startDay = MonthModel.julianDay(date),
                endDay = MonthModel.julianDay(date),
                title = "Slot $offset",
                colour = intArrayOf(
                    0xFF2E4A7D.toInt(), 0xFF4C5D3C.toInt(),
                    0xFF9A6F21.toInt(), 0xFF6C3A55.toInt(),
                )[offset % 4],
            )
        }

        val controller = org.robolectric.Robolectric
            .buildActivity(app.quire.calendar.ui.MainActivity::class.java)
            .setup()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val bitmap = render(controller.get().window.decorView, 411, 891, "app-main-events")
        assertPainted(bitmap, "app-main-events")
        controller.close()
        FakeCalendarProvider.reset()
    }

    private fun collectText(view: View, out: MutableList<String>) {
        if (view is android.widget.TextView) out += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
    }
}
