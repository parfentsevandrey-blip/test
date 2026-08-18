package app.quire.calendar

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
import app.quire.calendar.widget.AgendaWidgetRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The agenda card, rendered and read back.
 *
 * The same discipline as the month card: the RemoteViews goes through the platform's own
 * inflater, the pixels are written out to be looked at, and the claims worth making — order,
 * honesty about what did not fit, the two faces — are made against what was actually drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AgendaRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private val zone = java.time.ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun julian(date: LocalDate): Long = MonthModel.julianDay(date)

    private fun grant() {
        FakeCalendarProvider.reset()
        org.robolectric.Robolectric.setupContentProvider(
            FakeCalendarProvider::class.java,
            android.provider.CalendarContract.AUTHORITY,
        )
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).grantPermissions(android.Manifest.permission.READ_CALENDAR)
    }

    private fun seed() {
        grant()
        val tomorrow = today.plusDays(1)
        val marathonOn = today.plusDays(3)
        FakeCalendarProvider.instances = listOf(
            // Two today, out of order, so the card has to sort rather than trust the cursor.
            FakeCalendarProvider.instance(
                eventId = 1, beginMillis = at(today, 14, 30), endMillis = at(today, 15, 30),
                startDay = julian(today), endDay = julian(today),
                title = "Dentist", colour = 0xFF2E4A7D.toInt(),
            ),
            FakeCalendarProvider.instance(
                eventId = 2, beginMillis = at(today, 9), endMillis = at(today, 10),
                startDay = julian(today), endDay = julian(today),
                title = "Standup", colour = 0xFF4C5D3C.toInt(),
            ),
            FakeCalendarProvider.instance(
                eventId = 3, beginMillis = at(tomorrow, 10), endMillis = at(tomorrow, 11),
                startDay = julian(tomorrow), endDay = julian(tomorrow),
                title = "Планёрка", colour = 0xFF9A6F21.toInt(),
            ),
            // A two-day all-day entry: it must appear under both of its days, exactly as the
            // app's own day list would show it on either.
            FakeCalendarProvider.instance(
                eventId = 4, beginMillis = at(marathonOn, 0), endMillis = at(marathonOn.plusDays(2), 0),
                startDay = julian(marathonOn), endDay = julian(marathonOn.plusDays(1)),
                title = "Marathon", allDay = 1, colour = 0xFF6B3FA0.toInt(),
            ),
            FakeCalendarProvider.instance(
                eventId = 5, beginMillis = at(today.plusDays(8), 19), endMillis = at(today.plusDays(8), 21),
                startDay = julian(today.plusDays(8)), endDay = julian(today.plusDays(8)),
                title = "Dinner", colour = 0xFF2E4A7D.toInt(),
            ),
        )
    }

    private fun applied(widgetId: Int, widthDp: Int, heightDp: Int, dynamic: Boolean = false): FrameLayout {
        Prefs.get(context).widget(widgetId).apply {
            skin = Skin.COLOUR
            accent = Accent.PLUM
            opacity = 100
            this.dynamic = dynamic
        }
        val host = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
        host.addView(
            AgendaWidgetRenderer.build(context, widgetId, widthDp, heightDp).apply(context, host),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return host
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

    private fun collectText(view: View, out: MutableList<String>) {
        if (view.visibility != View.VISIBLE) return
        if (view is android.widget.TextView) out += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
    }

    /** The first view laid out past the edge of the one holding it, or null. */
    private fun overflowing(view: View, path: String = "root"): String? {
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (child.visibility == View.GONE) continue
            val name = "$path > ${child.javaClass.simpleName}"
            val text = (child as? android.widget.TextView)?.text?.toString().orEmpty()
            if (child.bottom > view.height + 1 || child.top < -1) {
                return "$name '$text' spans ${child.top}..${child.bottom} in ${view.height}"
            }
            overflowing(child, name)?.let { return it }
        }
        return null
    }

    @Test
    fun `the agenda lists the fortnight in order`() {
        seed()
        val host = applied(widgetId = 90, widthDp = 175, heightDp = 250)
        val bitmap = render(host, 175, 250, "agenda-half")

        val colours = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) { colours += bitmap.getPixel(x, y); y += 3 }
            x += 3
        }
        assertTrue("the agenda painted only ${colours.size} colours", colours.size > 8)

        val texts = ArrayList<String>()
        collectText(host, texts)
        val standup = texts.indexOfFirst { it == "Standup" }
        val dentist = texts.indexOfFirst { it == "Dentist" }
        assertTrue("today's entries are missing: $texts", standup >= 0 && dentist >= 0)
        assertTrue("9:00 came after 14:30: $texts", standup < dentist)
        assertTrue("tomorrow is not labelled by name: $texts", texts.any { it == "Tomorrow" })
        assertTrue("tomorrow's entry is missing: $texts", texts.any { it == "Планёрка" })
        assertTrue(
            "a two-day entry did not appear under both days: $texts",
            texts.count { it == "Marathon" } == 2,
        )
        assertTrue("agenda clips: ${overflowing(host)}", overflowing(host) == null)
    }

    /**
     * The tail is the honesty rule: a card too short for the fortnight counts what it dropped
     * rather than clipping it, and the count line itself never overflows because its room is
     * reserved before any row is placed.
     */
    @Test
    fun `what does not fit is counted, not clipped`() {
        grant()
        FakeCalendarProvider.instances = (0 until 12).map { index ->
            FakeCalendarProvider.instance(
                eventId = 10L + index,
                beginMillis = at(today, 8 + index), endMillis = at(today, 9 + index),
                startDay = julian(today), endDay = julian(today),
                title = "Meeting " + (index + 1), colour = 0xFF2E4A7D.toInt(),
            )
        }
        val host = applied(widgetId = 91, widthDp = 175, heightDp = 110)
        render(host, 175, 110, "agenda-tight")

        val texts = ArrayList<String>()
        collectText(host, texts)
        val shown = texts.count { it.startsWith("Meeting") }
        assertTrue("a 110dp card claims to hold twelve rows: $texts", shown < 12)
        val tail = texts.firstOrNull { it.matches(Regex("\\+\\d+ more")) }
        assertTrue("nothing says what was dropped: $texts", tail != null)
        assertTrue(
            "the tail miscounts: $tail for $shown shown of 12",
            tail == "+" + (12 - shown) + " more",
        )
        assertTrue("agenda clips: ${overflowing(host)}", overflowing(host) == null)
    }

    /** The launcher-side theme flip, on the agenda: built once light, applied dark, dark. */
    @Test
    fun `one agenda picture wears both faces`() {
        seed()
        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")
        Prefs.get(context).widget(92).apply {
            skin = Skin.COLOUR
            accent = Accent.PLUM
            opacity = 100
            dynamic = true
        }
        val views = AgendaWidgetRenderer.build(context, 92, 250, 250)

        fun shoot(name: String): Bitmap {
            val host = FrameLayout(context).apply { setBackgroundColor(0xFF7A7A80.toInt()) }
            host.addView(
                views.apply(context, host),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            return render(host, 250, 250, name)
        }

        fun luma(bitmap: Bitmap): Int {
            val p = bitmap.getPixel(bitmap.width / 2, 6)
            return (
                android.graphics.Color.red(p) * 299 +
                    android.graphics.Color.green(p) * 587 +
                    android.graphics.Color.blue(p) * 114
                ) / 1000
        }

        val light = luma(shoot("agenda-faces-light"))
        org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        val dark = luma(shoot("agenda-faces-dark"))
        org.robolectric.RuntimeEnvironment.setQualifiers("+notnight")

        assertTrue("the light application came out dark (luma XX)".replace("XX", "" + light), light > 170)
        assertTrue(
            "the picture painted in the light did not go dark when applied dark (luma XX)"
                .replace("XX", "" + dark),
            dark < 90,
        )
    }

    @Test
    fun `an empty fortnight says so, and a missing permission says why`() {
        grant()
        FakeCalendarProvider.instances = emptyList()
        val clear = ArrayList<String>()
        collectText(applied(widgetId = 93, widthDp = 250, heightDp = 180), clear)
        assertTrue(
            "an empty card said nothing: $clear",
            clear.any { it == "The next two weeks are clear" },
        )

        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).denyPermissions(android.Manifest.permission.READ_CALENDAR)
        val denied = ArrayList<String>()
        collectText(applied(widgetId = 94, widthDp = 250, heightDp = 180), denied)
        assertTrue(
            "a card with no permission pretended the calendar was empty: $denied",
            denied.any { it == "Open the app to allow calendar access" },
        )
        org.robolectric.Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.app.Application>(),
        ).grantPermissions(android.Manifest.permission.READ_CALENDAR)
    }
}
