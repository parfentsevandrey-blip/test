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
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.widget.WidgetRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth
import java.util.Locale

/**
 * Everything the app puts on a screen that is not a composable in isolation: the home-screen
 * widget, the launcher icon, and the two Activities assembling and painting end to end.
 *
 * The widget is the reason this runs on real graphics. `RemoteViews.apply` puts the tree through
 * the platform's own inflater with its `@RemoteView` filter, so a disallowed view class fails
 * here exactly as it would on a home screen — and nothing in a compiler will tell you first.
 *
 * The app's own screens are drawn one at a time by `AppRenderTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    /**
     * Measures and lays out at a fixed size, without drawing.
     *
     * Laying a Compose tree out is what starts the animations its content asks for on the way in —
     * a lazy list's items fade in as they are placed — so a screenshot taken in the same breath
     * catches them at nothing. Anything animated has to be laid out first, given frames, and only
     * then drawn.
     */
    private fun layOut(view: View, widthDp: Int, heightDp: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return width to height
    }

    private fun render(view: View, widthDp: Int, heightDp: Int, name: String): Bitmap {
        val (width, height) = layOut(view, widthDp, heightDp)
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

    private fun collectText(view: View, out: MutableList<String>) {
        if (view is android.widget.TextView) out += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
    }

    @Test
    fun `the launcher icon draws inside its mask`() {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt()
        val icon = androidx.core.content.ContextCompat.getDrawable(context, app.quire.R.mipmap.ic_launcher)!!
        icon.setBounds(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.draw(Canvas(bitmap))
        File(outputDir, "launcher-icon.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertPainted(bitmap, "launcher-icon")
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

    /**
     * The chip is the part of the reference style that only a wide card can carry, so it is the
     * part most likely to ship unlooked-at. Real events are pushed through the fake provider so
     * the widget queries them the way it will on a phone.
     */
    @Test
    fun `a wide filled card names the day's first event`() {
        org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        FakeCalendarProvider.reset()
        org.robolectric.Robolectric.setupContentProvider(
            FakeCalendarProvider::class.java,
            android.provider.CalendarContract.AUTHORITY,
        )
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).grantPermissions(android.Manifest.permission.READ_CALENDAR)

        val zone = java.time.ZoneId.systemDefault()
        val month = YearMonth.now()
        fun at(day: Int, hour: Int) = month.atDay(day)
            .atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        fun julian(day: Int) = MonthModel.julianDay(month.atDay(day))

        FakeCalendarProvider.instances = listOf(
            // Two on the same day, out of order, so "first" has to mean earliest rather than
            // whichever row the provider happened to hand back first.
            FakeCalendarProvider.instance(
                eventId = 1, beginMillis = at(12, 15), endMillis = at(12, 16),
                startDay = julian(12), endDay = julian(12),
                title = "Afternoon", colour = 0xFF4C5D3C.toInt(),
            ),
            FakeCalendarProvider.instance(
                eventId = 2, beginMillis = at(12, 9), endMillis = at(12, 10),
                startDay = julian(12), endDay = julian(12),
                title = "Standup", colour = 0xFF2E4A7D.toInt(),
            ),
            FakeCalendarProvider.instance(
                eventId = 3, beginMillis = at(20, 11), endMillis = at(20, 12),
                startDay = julian(20), endDay = julian(20),
                title = "Рабочая встреча", colour = 0xFF9A6F21.toInt(),
            ),
        )

        val widgetId = 44
        Prefs.get(context).widget(widgetId).apply {
            skin = Skin.COLOUR
            accent = Accent.PLUM
            opacity = 100
            showEvents = true
            weekNumbers = false
            monthOffset = 0
        }
        val views = WidgetRenderer.build(context, widgetId, 350, 300)
        val host = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
        host.addView(
            views.apply(context, host),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        assertPainted(render(host, 350, 300, "widget-colour-chips"), "widget-colour-chips")

        val texts = ArrayList<String>()
        collectText(host, texts)
        assertTrue(
            "the earliest event of the day was not named: $texts",
            texts.any { it == "Standup" },
        )
        assertTrue(
            "a later event won the cell instead of the earliest",
            texts.none { it == "Afternoon" },
        )
        assertTrue("a Cyrillic title did not survive", texts.any { it == "Рабочая встреча" })

        // The same month on a card too short for a dot, which is the bottom of the same ladder:
        // a chip names the day, dots count it, and below the height where a dot fits the ground
        // says it in no height at all. This size used to say nothing whatever.
        val narrow = 45
        Prefs.get(context).widget(narrow).apply {
            skin = Skin.COLOUR
            accent = Accent.PLUM
            opacity = 100
            showEvents = true
            density = true
            weekNumbers = false
            monthOffset = 0
        }
        val small = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
        small.addView(
            WidgetRenderer.build(context, narrow, 160, 150).apply(context, small),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val ground = render(small, 160, 150, "widget-colour-busy")
        assertPainted(ground, "widget-colour-busy")

        // The busy day carries a colour the empty ones do not. Measured rather than trusted: a
        // tint that fails to apply is invisible, and an invisible tint is exactly what a colour
        // filter silently dropped through RemoteViews looks like.
        assertTrue(
            "a busy day is not tinted at all on a card too small for dots",
            grounds(ground) > 0,
        )

        FakeCalendarProvider.reset()
        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")
    }

    /**
     * How many distinct card colours the grid area holds.
     *
     * The card is one flat colour behind the numbers; a ground tint puts a second one on it. So
     * "more than one" is exactly the claim that a tint was painted, and it needs no coordinates.
     */
    private fun grounds(bitmap: android.graphics.Bitmap): Int {
        val counts = HashMap<Int, Int>()
        var y = bitmap.height / 3
        while (y < bitmap.height - 4) {
            var x = 6
            while (x < bitmap.width - 6) {
                counts[bitmap.getPixel(x, y)] = (counts[bitmap.getPixel(x, y)] ?: 0) + 1
                x += 2
            }
            y += 2
        }
        // Colours that cover a real area rather than antialiasing on the edge of a glyph.
        val broad = counts.filterValues { it > 60 }.keys
        return broad.size - 1
    }

    /**
     * The filled skin at the two placements that matter: half a home-screen row, which is what it
     * was asked for, and a full-width card where a column is wide enough to name the day's first
     * entry instead of dotting it.
     */
    @Test
    fun `the filled skin reads at half width and at full width`() {
        // The filled card follows the system now, so a picture of it is a picture of one of its
        // two faces and has to say which. These are the night one; the daylight face is rendered
        // beside it in the weather app's own tests.
        org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        val cases = listOf(
            Triple("widget-colour-half", 175 to 230, 40),
            Triple("widget-colour-wide", 350 to 300, 41),
            Triple("widget-colour-indigo", 175 to 230, 42),
        )
        cases.forEachIndexed { index, (name, size, widgetId) ->
            Prefs.get(context).widget(widgetId).apply {
                skin = Skin.COLOUR
                accent = if (index == 2) Accent.INDIGO else Accent.PLUM
                opacity = 100
                showEvents = true
                weekNumbers = false
                monthOffset = 0
            }
            val views = WidgetRenderer.build(context, widgetId, size.first, size.second)
            val host = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
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
    }

    /**
     * The card on a phone with the type turned up.
     *
     * A widget is a fixed rectangle that cannot scroll or grow, and its type is asked for in sp
     * against a budget kept in dp — so above a font scale of one the arithmetic is simply wrong,
     * and the first thing to go is the bottom of whatever is last in a row. The weather card lost
     * its chance of rain that way on a real phone; this is the same fault's other half.
     *
     * Read from the layout: a line of text taller than the view holding it is what clipping is.
     */
    @Test
    fun `the calendar card fits its rows with the type turned up`() {
        val configuration = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val wasScale = configuration.fontScale
        @Suppress("DEPRECATION")
        val wasScaled = metrics.scaledDensity
        try {
            configuration.fontScale = 1.4f
            @Suppress("DEPRECATION")
            metrics.scaledDensity = metrics.density * 1.4f
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, metrics)

            listOf(175 to 230, 350 to 300, 160 to 150).forEach { (widthDp, heightDp) ->
                val widgetId = 80 + widthDp
                Prefs.get(context).widget(widgetId).apply {
                    skin = Skin.COLOUR
                    accent = Accent.PLUM
                    opacity = 100
                    showEvents = true
                    weekNumbers = false
                    monthOffset = 0
                }
                val host = FrameLayout(context)
                host.addView(
                    WidgetRenderer.build(context, widgetId, widthDp, heightDp).apply(context, host),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                render(host, widthDp, heightDp, "widget-large-type-$widthDp")

                val over = overflowing(host)
                assertTrue("${widthDp}x$heightDp clips: $over", over == null)

                val texts = ArrayList<String>()
                collectText(host, texts)
                assertEquals(
                    "${widthDp}x$heightDp lost a square",
                    MonthModel.CELLS,
                    texts.mapNotNull { it.toIntOrNull() }.count { it in 1..31 },
                )
            }
        } finally {
            configuration.fontScale = wasScale
            @Suppress("DEPRECATION")
            metrics.scaledDensity = wasScaled
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, metrics)
        }
    }

    /**
     * The first thing laid out too small for what it holds, or null if nothing is.
     *
     * Two shapes of the same fault. A view placed past the edge of the one holding it is the
     * obvious one. The other is what a LinearLayout with a fixed height actually does — it hands
     * the last child whatever is left — so the view fits and its text does not. From outside,
     * both are a number with its bottom sliced off.
     */
    private fun overflowing(view: View, path: String = "root"): String? {
        if (view is android.widget.TextView) {
            val room = view.height - view.paddingTop - view.paddingBottom
            val needs = view.layout?.height ?: 0
            if (needs > room + 1) return "$path '${view.text}' needs ${needs}px in $room"
        }
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (child.visibility == View.GONE) continue
            val name = "$path > ${child.javaClass.simpleName}"
            if (child.bottom > view.height + 1 || child.top < -1) {
                val text = (child as? android.widget.TextView)?.text?.toString().orEmpty()
                return "$name '$text' spans ${child.top}..${child.bottom} in ${view.height}"
            }
            overflowing(child, name)?.let { return it }
        }
        return null
    }

    /**
     * The widget's configuration screen is now Compose like everything else, but it is still the
     * launcher's screen rather than the app's, and it is still the one that has to hand a result
     * back. Assembling it through the real `onCreate` is the only way to catch a theme or a
     * missing extra that a composable preview would sail past.
     */
    @Test
    fun `the widget configuration screen assembles and paints`() {
        val intent = android.content.Intent(
            context,
            app.quire.calendar.widget.WidgetConfigActivity::class.java,
        ).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 31)
        val controller = org.robolectric.Robolectric
            .buildActivity(app.quire.calendar.widget.WidgetConfigActivity::class.java, intent)
            .setup()
        try {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val bitmap = render(controller.get().window.decorView, 411, 891, "widget-config")
            assertPainted(bitmap, "widget-config")
        } finally {
            controller.close()
        }
    }

    /**
     * The app as it actually opens: the real `onCreate`, the real window, and with it everything
     * the screen tests leave out — the flexible top app bar, the navigation bar, the button.
     *
     * Assembling it here is also the check that nothing in the theme fights Compose: the window
     * is a platform DeviceDefault one and the content is Material 3 Expressive, and a mismatch
     * between them shows up as a black or blank page rather than as a compiler error.
     */
    @Test
    fun `the app assembles and paints end to end`() {
        FakeCalendarProvider.reset()
        org.robolectric.Robolectric.setupContentProvider(
            FakeCalendarProvider::class.java,
            android.provider.CalendarContract.AUTHORITY,
        )
        val zone = java.time.ZoneId.systemDefault()
        val month = YearMonth.now()
        FakeCalendarProvider.instances = listOf(3, 9, 17, 23).mapIndexed { index, day ->
            FakeCalendarProvider.instance(
                eventId = index + 1L,
                beginMillis = month.atDay(day).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
                endMillis = month.atDay(day).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                startDay = MonthModel.julianDay(month.atDay(day)),
                endDay = MonthModel.julianDay(month.atDay(day)),
                title = listOf("Dentist", "Standup", "Flight to Porto", "Books due")[index],
            )
        }
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).grantPermissions(android.Manifest.permission.READ_CALENDAR)

        // Preferences outlive a test class, so the mode is pinned rather than inherited: a
        // screenshot that comes out light or dark depending on what ran before it is no evidence.
        val prefs = Prefs.get(context)
        val beforeSkin = prefs.skin
        prefs.skin = Skin.PAPER

        val controller = org.robolectric.Robolectric
            .buildActivity(app.quire.calendar.m3.MainActivity::class.java)
            .setup()
        try {
            // Laid out at the size it will be shot at before any of the waiting, so the frames
            // below are the ones the arriving content animates on.
            layOut(controller.get().window.decorView, 411, 891)

            // idleFor rather than idle: the clock has to move for the Choreographer to hand
            // Compose a frame, and without frames every animation the first composition started
            // stays at the value it began on.
            repeat(30) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofMillis(32))
                Thread.sleep(10)
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(2))
            val bitmap = render(controller.get().window.decorView, 411, 891, "app-main")
            assertPainted(bitmap, "app-main")

            // Compose draws rather than inflating TextViews, so there is no view tree to read the
            // labels out of; what can be checked without a semantics tree is that the bottom of
            // the window is a navigation bar rather than more page — four labelled destinations
            // put far more than a page's worth of colour into that band.
            val band = HashSet<Int>()
            var y = bitmap.height - 160
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    band += bitmap.getPixel(x, y)
                    x += 2
                }
                y += 2
            }
            assertTrue("nothing painted a navigation bar (${band.size} colours)", band.size > 24)
        } finally {
            controller.close()
            prefs.skin = beforeSkin
            FakeCalendarProvider.reset()
        }
    }
}
