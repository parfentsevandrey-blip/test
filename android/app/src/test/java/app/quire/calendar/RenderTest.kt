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
import app.quire.engine.anim.MotionProfile
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
 * The half of the app that is not the world: the widget, the launcher icon, and the one Activity
 * assembling and painting end to end.
 *
 * The widget is the reason this runs on real graphics. `RemoteViews.apply` puts the tree through
 * the platform's own inflater with its `@RemoteView` filter, so a disallowed view class fails
 * here exactly as it would on a home screen — and nothing in a compiler will tell you first.
 *
 * The world's own surfaces are covered by `WorldRenderTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

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

    private fun collectText(view: View, out: MutableList<String>) {
        if (view is android.widget.TextView) out += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
    }

    @Test
    fun `the launcher icon draws inside its mask`() {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt()
        val icon = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
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

        FakeCalendarProvider.reset()
    }

    /**
     * The filled skin at the two placements that matter: half a home-screen row, which is what it
     * was asked for, and a full-width card where a column is wide enough to name the day's first
     * entry instead of dotting it.
     */
    @Test
    fun `the filled skin reads at half width and at full width`() {
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
     * The regression that once made every animation in the app stand still: the profile came from
     * the system animator scale on every launch, so a phone with animations turned down could
     * never be talked out of it. The app's own setting is the authority now.
     */
    @Test
    fun `the stored motion profile is what the app uses`() {
        val prefs = Prefs.get(context)
        val before = prefs.motion
        try {
            prefs.motion = MotionProfile.PLAYFUL.key
            assertEquals(MotionProfile.PLAYFUL, MotionProfile.from(prefs.motion))
            assertTrue(prefs.hasMotionPreference)
            assertTrue("a live profile must not be instant", !MotionProfile.PLAYFUL.instant)
            assertTrue(
                "standard must be soft enough to see",
                MotionProfile.STANDARD.stiffness < 260f,
            )
        } finally {
            prefs.motion = before
        }
    }

    /**
     * The widget's configuration screen is the one place still built from Views rather than
     * drawn, because it is the launcher's screen rather than the app's. It now runs on the same
     * spring engine as everything else, so it is worth proving it still assembles and paints.
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
     * The world, its overlay and the Activity holding them, assembled by the real `onCreate` and
     * drawn through the real window — the one check that the whole thing starts.
     */
    @Test
    fun `the app assembles and paints end to end`() {
        val prefs = Prefs.get(context)
        val before = prefs.motion
        prefs.motion = MotionProfile.OFF.key
        val controller = org.robolectric.Robolectric
            .buildActivity(app.quire.calendar.world.WorldActivity::class.java)
            .setup()
        try {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val bitmap = render(controller.get().window.decorView, 411, 891, "app-world")
            assertPainted(bitmap, "app-world")
        } finally {
            controller.close()
            prefs.motion = before
        }
    }
}
