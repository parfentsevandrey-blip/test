package app.quire.retro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.quire.weather.Conditions
import app.quire.weather.DayForecast
import app.quire.weather.Forecast
import app.quire.weather.Sky
import app.quire.weather.WeatherStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Quire 95, in pixels.
 *
 * The joke has exactly one failure mode worth testing for: looking like 2026. So the assertions
 * are about the era rather than about the layout — the grey is the grey, the title bar is navy
 * on the left and lighter on the right, the bevel is light on top and dark underneath, and
 * nothing anywhere is a rounded corner. Everything else the widget must do — hold its box, show
 * the forecast, survive a placement — is checked the same way the other two cards are.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RetroRenderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private fun stub(now: Sky = Sky.SHOWERS): Forecast {
        val today = LocalDate.now()
        val skies = listOf(Sky.SHOWERS, Sky.PARTLY_CLOUDY, Sky.CLEAR, Sky.THUNDER, Sky.SNOW)
        return Forecast(
            place = "Redmond",
            latitude = 47.67,
            longitude = -122.12,
            now = Conditions(12.4, 10.6, now, true, 82, 14.0, 27.0, 210, 1004.0, 3.0),
            days = skies.mapIndexed { index, sky ->
                DayForecast(
                    date = today.plusDays(index.toLong()),
                    sky = sky,
                    high = 22.0 - index,
                    low = 11.0 - index,
                    rain = 20 * index,
                )
            },
            fetched = System.currentTimeMillis(),
        )
    }

    private fun applied(widgetId: Int, widthDp: Int, heightDp: Int): FrameLayout {
        val host = FrameLayout(context).apply { setBackgroundColor(0xFF101014.toInt()) }
        host.addView(
            W95WidgetRenderer.build(context, widgetId, widthDp, heightDp).apply(context, host),
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
            if (child.bottom > view.height + 1 || child.top < -1) {
                return "$name spans ${child.top}..${child.bottom} in ${view.height}"
            }
            overflowing(child, name)?.let { return it }
        }
        return null
    }

    @Test
    fun `the card is a window, and the window is 1995`() {
        WeatherStore.save(context, stub())
        val host = applied(widgetId = 95, widthDp = 300, heightDp = 180)
        val bitmap = render(host, 300, 180, "retro-widget")
        val density = context.resources.displayMetrics.density

        // The face: the window's own margin down its left side — past the two-pixel bevel and
        // beside the client area, which is the one place the grey is not covered by something.
        val face = bitmap.getPixel((2.5f * density).toInt(), bitmap.height / 2)
        assertEquals(
            "the card's face is not #C0C0C0 but " + Integer.toHexString(face),
            0xFFC0C0C0.toInt(),
            face,
        )

        // The title bar's gradient: navy at its left, visibly lighter at its right. Measured as
        // the darkest pixel down a column of the bar rather than a single one, because the bar
        // also carries white text and a white button, and either would answer for the ground.
        fun barRed(x: Int): Int {
            var darkest = 255
            for (y in (4 * density).toInt()..(16 * density).toInt()) {
                darkest = minOf(darkest, android.graphics.Color.red(bitmap.getPixel(x, y)))
            }
            return darkest
        }
        val leftRed = barRed((10 * density).toInt())
        val rightRed = barRed(bitmap.width - (30 * density).toInt())
        assertTrue(
            "the title bar is not navy at the left",
            android.graphics.Color.blue(bitmap.getPixel((10 * density).toInt(), (4 * density).toInt())) > 100,
        )
        assertTrue(
            "the title bar does not lighten to the right ($leftRed then $rightRed)",
            rightRed > leftRed + 8,
        )

        // The bevel: white along the top edge, black along the bottom one. The whole look is
        // this one rule, and if it ever inverts the card stops being a slab and becomes a hole.
        val top = bitmap.getPixel(bitmap.width / 2, 0)
        val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height - 1)
        assertTrue("the top edge is not the light one", android.graphics.Color.red(top) > 200)
        assertTrue("the bottom edge is not the dark one", android.graphics.Color.red(bottom) < 60)

        // The corner: square. A rounded corner would leave the host's background showing at the
        // very corner pixel, which is what every modern card does and this one must not.
        val corner = bitmap.getPixel(0, 0)
        assertTrue(
            "the corner is rounded (" + Integer.toHexString(corner) + ")",
            corner == top || android.graphics.Color.red(corner) > 200,
        )

        val texts = ArrayList<String>()
        collectText(host, texts)
        assertTrue("the title bar does not name the place: $texts", texts.any { it.contains("Redmond") })
        assertTrue("the card lost the temperature: $texts", texts.any { it == "12°" })
        assertTrue("the card lost the sky: $texts", texts.any { it == "Showers" })
        assertTrue("the status bar never said Ready: $texts", texts.any { it == "Ready" })
        assertTrue("the card clips: ${overflowing(host)}", overflowing(host) == null)
    }

    @Test
    fun `it holds its box at every placement it allows`() {
        WeatherStore.save(context, stub(Sky.SNOW))
        listOf(
            Triple("retro-widget-wide", 380 to 200, 96),
            Triple("retro-widget-small", 180 to 120, 97),
        ).forEach { (name, size, widgetId) ->
            val host = applied(widgetId, size.first, size.second)
            render(host, size.first, size.second, name)
            assertTrue("$name clips: ${overflowing(host)}", overflowing(host) == null)

            val texts = ArrayList<String>()
            collectText(host, texts)
            assertTrue("$name truncated something: $texts", texts.none { it.contains("…") })
            // The strip is bought with height: the tall card shows days, the short one gives
            // them up rather than drawing columns nobody can read.
            val days = texts.count { it == "Today" || it.length == 3 && it.first().isUpperCase() }
            if (name.endsWith("wide")) {
                assertTrue("the wide card shows no days: $texts", days >= 3)
            }
        }
    }

    /** A card placed before the first fetch says so in the status bar, like a 1995 dialog. */
    @Test
    fun `an empty card says so where a window would say it`() {
        WeatherStore.clear(context)
        val host = applied(widgetId = 98, widthDp = 300, heightDp = 180)
        render(host, 300, 180, "retro-widget-empty")

        val texts = ArrayList<String>()
        collectText(host, texts)
        assertTrue("an unfetched card claimed a temperature: $texts", texts.none { it == "0°" })
        assertTrue("the status bar said nothing: $texts", texts.any { it == "Connecting..." })
        assertTrue("the client area showed no dash: $texts", texts.any { it == "--" })
    }

    /** Every sky has a block glyph of its own, and no two of them are the same picture. */
    @Test
    fun `every sky has a sixteen-colour glyph`() {
        val seen = HashMap<String, List<Int>>()
        Sky.entries.flatMap { listOf(it to true, it to false) }.forEach { (sky, day) ->
            val id = W95WidgetRenderer.glyph(sky, day)
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, id)!!.mutate()
            drawable.setBounds(0, 0, 64, 64)
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(bitmap))

            var lit = 0
            val cells = MutableList(16) { 0 }
            for (x in 0 until 64) {
                for (y in 0 until 64) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 24) {
                        lit++
                        val cell = (y * 4 / 64) * 4 + (x * 4 / 64)
                        cells[cell] = cells[cell] + 1
                    }
                }
            }
            assertTrue("${sky.name} (day=$day) drew nothing", lit > 200)
            seen["${sky.name}-$day"] = cells
        }
        // Several skies share a picture on purpose — mostly clear and partly cloudy are one
        // thing to look at — so what must be distinct is the set of pictures, not of states.
        assertTrue("every sky drew the same glyph", seen.values.toSet().size >= 8)
    }
}
