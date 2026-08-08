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
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.core.Tokens
import app.quire.calendar.ui.GridStyle
import app.quire.calendar.ui.MotionProfile
import app.quire.calendar.ui.RadialMenu
import app.quire.calendar.ui.StageView
import app.quire.calendar.widget.WidgetRenderer
import org.junit.Assert.assertEquals
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
 * pixels out. Two things are checked at once: that the widget survives
 * `RemoteViews.apply` — the host's inflater rejects any class not annotated
 * @RemoteView, which no compiler catches — and that the stage paints at every
 * point along its zoom, including halfway between two levels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private val today = LocalDate.now()

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

    /** A month with enough going on that marks, colours and density all show. */
    private fun sampleData(): StageView.Data = object : StageView.Data {
        private val palette = intArrayOf(
            0xFF2E4A7D.toInt(), 0xFF4C5D3C.toInt(), 0xFF9A6F21.toInt(), 0xFF6C3A55.toInt(),
        )

        override fun loads(month: YearMonth): Map<LocalDate, DayLoad> {
            val out = HashMap<LocalDate, DayLoad>()
            for (day in 1..month.lengthOfMonth()) {
                val seed = (day * 7 + month.monthValue * 3) % 11
                if (seed >= 5) continue
                val count = seed % 3 + 1
                out[month.atDay(day)] = DayLoad(
                    count,
                    IntArray(count) { palette[(day + it) % palette.size] },
                )
            }
            return out
        }

        override fun agenda(date: LocalDate): List<AgendaEntry> {
            val zone = java.time.ZoneId.systemDefault()
            fun at(hour: Int, minute: Int = 0) =
                date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            return listOf(
                AgendaEntry(1, at(0), at(23, 59), true, "Studio closed", null, palette[3], "Personal"),
                AgendaEntry(2, at(9, 30), at(10, 30), false, "Design review", "Kutuzovsky 12", palette[0], "Work"),
                AgendaEntry(3, at(13), at(14), false, "Lunch with Anna", null, palette[1], "Personal"),
                AgendaEntry(4, at(16), at(17, 30), false, "Structural walkthrough", "Site", palette[2], "Work"),
                AgendaEntry(5, at(19), at(21), false, "Rehearsal", "Conservatory", palette[0], "Personal"),
            )
        }
    }

    private fun stage(dark: Boolean, heat: Boolean = false): StageView =
        StageView(context).apply {
            palette = Tokens.palette(dark, Accent.CINNABAR)
            motion = MotionProfile.OFF
            haptics = false
            firstDayOfWeek = DayOfWeek.MONDAY
            this.today = this@RenderTest.today
            style = GridStyle(heat = heat)
            data = sampleData()
            setSafeInsets(52f * context.resources.displayMetrics.density / 3f, 24f)
        }

    /**
     * The zoom is one continuous number, so it is worth looking at the frames
     * between the levels — those are what the user actually sees while moving.
     */
    @Test
    fun `the stage paints at every point along the zoom`() {
        val cases = listOf(
            "stage-year" to 0f,
            "stage-zooming" to 0.55f,
            "stage-month" to 1f,
            "stage-opening" to 1.45f,
            "stage-day" to 2f,
        )
        cases.forEach { (name, z) ->
            val view = stage(dark = false)
            view.goTo(today, level = 1, animate = false)
            when {
                z <= 1f -> {
                    if (z < 0.5f) view.goToLevel(0)
                    view.zoom.snapTo(z)
                }
                else -> {
                    view.goTo(today, level = 2, animate = false)
                    view.zoom.snapTo(z)
                }
            }
            assertPainted(render(view, 411, 891, name), name)
        }
    }

    @Test
    fun `the stage paints in ink and with density on`() {
        val dark = stage(dark = true).apply { goTo(today, level = 1, animate = false) }
        assertPainted(render(dark, 411, 891, "stage-ink"), "stage-ink")

        val heat = stage(dark = false, heat = true).apply { goTo(today, level = 1, animate = false) }
        assertPainted(render(heat, 411, 891, "stage-density"), "stage-density")

        val darkDay = stage(dark = true).apply { goTo(today, level = 2, animate = false) }
        assertPainted(render(darkDay, 411, 891, "stage-day-ink"), "stage-day-ink")
    }

    @Test
    fun `the radial menu blooms without running off the screen`() {
        val menu = RadialMenu(context).apply {
            palette = Tokens.palette(false, Accent.CINNABAR)
            motion = MotionProfile.OFF
            haptics = false
            safeTop = 60f
            safeBottom = 48f
        }
        val host = FrameLayout(context).apply {
            setBackgroundColor(Tokens.palette(false, Accent.CINNABAR).canvas)
            addView(
                menu,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val density = context.resources.displayMetrics.density
        host.measure(
            View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((891 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, (411 * density).toInt(), (891 * density).toInt())

        // Opened in the bottom-right corner: the arc has to fold back inwards.
        menu.open(
            380f * density,
            820f * density,
            listOf(
                RadialMenu.Item(1, "Today", R.drawable.ic_ring),
                RadialMenu.Item(2, "Year", R.drawable.ic_grid),
                RadialMenu.Item(3, "Search", R.drawable.ic_search),
                RadialMenu.Item(4, "Add", R.drawable.ic_plus),
                RadialMenu.Item(5, "Settings", R.drawable.ic_settings),
            ),
        )
        assertPainted(render(host, 411, 891, "radial-menu"), "radial-menu")
    }

    /**
     * Two cells on a four-column launcher — exactly half the screen — is the
     * tightest placement the provider allows. Rendered with the longest month
     * names in both languages, since the header is what runs out of room first.
     */
    @Test
    fun `widget reads at a half-width placement`() {
        val cases = listOf(
            Triple("widget-half-en", Locale.ENGLISH, 175 to 230),
            Triple("widget-half-ru", Locale("ru", "RU"), 175 to 230),
            Triple("widget-half-short", Locale.ENGLISH, 165 to 175),
        )
        val original = Locale.getDefault()
        try {
            cases.forEachIndexed { index, (name, locale, size) ->
                Locale.setDefault(locale)
                val widgetId = 20 + index
                Prefs.get(context).widget(widgetId).apply {
                    skin = Skin.PAPER
                    accent = Accent.CINNABAR
                    opacity = 100
                    showEvents = true
                    monthOffset = 9 - YearMonth.now().monthValue
                }
                val views = WidgetRenderer.build(context, widgetId, size.first, size.second)
                val host = FrameLayout(context).apply { setBackgroundColor(0xFFC9C4B8.toInt()) }
                host.addView(
                    views.apply(context, host),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                assertPainted(render(host, size.first, size.second, name), name)

                val texts = ArrayList<String>()
                collectText(host, texts)
                assertEquals(
                    "$name renders every square",
                    MonthModel.CELLS,
                    texts.mapNotNull { it.toIntOrNull() }.count { it in 1..31 },
                )
            }
        } finally {
            Locale.setDefault(original)
        }
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
            monthOffset = 0
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
        assertPainted(render(host, 300, 270, "widget-weeknumbers"), "widget-weeknumbers")

        val texts = ArrayList<String>()
        collectText(host, texts)
        assertEquals(
            "day cells plus one week number per row",
            MonthModel.CELLS + MonthModel.ROWS,
            texts.mapNotNull { it.toIntOrNull() }.count { it in 1..53 },
        )
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
            prefs.weekNumbers = false
            prefs.monthOffset = 0

            val views = WidgetRenderer.build(context, AppWidgetManager.getInstance(context), widgetId)
            val host = FrameLayout(context)
            host.addView(
                views.apply(context, host),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            assertPainted(render(host, 280, 260, name), name)
        }
    }

    @Test
    fun `floating panels stack and stay readable over the world`() {
        val skin = Tokens.palette(false, Accent.CINNABAR)
        val behind = stage(dark = false).apply { goTo(today, level = 1, animate = false) }
        val sheet = app.quire.calendar.ui.SheetOverlay(context).apply {
            palette = skin
            motion = MotionProfile.OFF
        }
        val host = FrameLayout(context)
        host.addView(
            behind,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        host.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val builder = sheet.begin()
        builder.title(context.getString(R.string.settings))
        builder.slab { panel ->
            panel.section(R.string.section_motion)
            panel.segmented(
                R.string.motion,
                listOf(
                    context.getString(R.string.motion_off),
                    context.getString(R.string.motion_calm),
                    context.getString(R.string.motion_standard),
                    context.getString(R.string.motion_playful),
                ),
                2,
            ) {}
            panel.rule()
            panel.toggle(R.string.haptics, R.string.haptics_hint, true) {}
        }
        builder.slab { panel ->
            panel.section(R.string.section_appearance)
            panel.segmented(
                R.string.skin,
                listOf(
                    context.getString(R.string.skin_auto),
                    context.getString(R.string.skin_paper),
                    context.getString(R.string.skin_ink),
                ),
                0,
            ) {}
            panel.accents(Accent.CINNABAR) {}
        }
        builder.slab { panel ->
            panel.section(R.string.section_grid)
            panel.toggle(R.string.heat, R.string.heat_hint, true) {}
            panel.rule()
            panel.toggle(R.string.week_numbers, R.string.week_numbers_hint, false) {}
        }
        sheet.present()

        assertPainted(render(host, 411, 891, "sheet-settings"), "sheet-settings")
    }

    @Test
    fun `the app assembles and paints end to end`() {
        Prefs.get(context).motion = MotionProfile.OFF.key
        val controller = org.robolectric.Robolectric
            .buildActivity(app.quire.calendar.ui.MainActivity::class.java)
            .setup()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val bitmap = render(controller.get().window.decorView, 411, 891, "app-main")
        assertPainted(bitmap, "app-main")
        controller.close()
        Prefs.get(context).motion = MotionProfile.STANDARD.key
    }

    private fun collectText(view: View, out: MutableList<String>) {
        if (view is android.widget.TextView) out += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
    }
}
