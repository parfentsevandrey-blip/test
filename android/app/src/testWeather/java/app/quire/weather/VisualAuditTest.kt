package app.quire.weather

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.quire.weather.ui.WeatherApp
import app.quire.weather.ui.dialSweep
import app.quire.weather.ui.WeatherModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The audit: one invariant, applied to every state the screen can actually be in.
 *
 * The header bug — "Ближайшие 24 часа" printed across "Москва" — was not found by a test. It was
 * found by somebody scrolling a real phone, because every test this app had photographed the
 * screen at rest, and at rest it was perfect. The class of fault is general and so is its
 * signature: **two pieces of text occupying the same pixels.** Nothing legitimate on any of these
 * screens does that. A heading over a title, a temperature over a label, a card sliding under a
 * transparent bar — all of them are this one measurement coming back positive.
 *
 * So this file does not photograph one screen carefully. It walks the states nobody scrolls to in
 * a test — scrolled, in the other theme, at the font scale an older phone is actually set to,
 * with a place name longer than the bar, with nothing fetched at all — and asks the same question
 * of each: does any text overlap any other text? The pictures are saved beside the answer,
 * because "no two texts overlap" is necessary and not sufficient, and the rest is a judgement
 * only a person looking can make.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class VisualAuditTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun stopTheClock() {
        compose.mainClock.autoAdvance = false
    }

    private fun settle(rounds: Int = 8) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(16L)
            Thread.sleep(5)
        }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
    }

    private fun shoot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    /** One piece of text and the box it was laid out in. */
    private data class Printed(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun overlaps(other: Printed): Boolean =
            left < other.right && other.left < right && top < other.bottom && other.top < bottom

        /** How much of the smaller box the shared area covers, 0..1. */
        fun share(other: Printed): Float {
            val w = minOf(right, other.right) - maxOf(left, other.left)
            val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
            if (w <= 0f || h <= 0f) return 0f
            val mine = (right - left) * (bottom - top)
            val theirs = (other.right - other.left) * (other.bottom - other.top)
            return (w * h) / minOf(mine, theirs).coerceAtLeast(1f)
        }
    }

    private fun printed(): List<Printed> =
        compose.onRoot().fetchSemanticsNode().let { root ->
            val out = ArrayList<Printed>()
            fun walk(node: SemanticsNode) {
                val text = node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString(" ") { it.text }
                    ?.takeIf { it.isNotBlank() }
                if (text != null) {
                    val b = node.boundsInRoot
                    // Zero-area and off-screen nodes are not printed on anything.
                    if (b.width > 1f && b.height > 1f) {
                        out += Printed(text, b.left, b.top, b.right, b.bottom)
                    }
                }
                node.children.forEach { walk(it) }
            }
            walk(root)
            out
        }

    /** Colour distance, for finding the one horizontal edge the bar's ground makes. */
    private fun distance(a: Int, b: Int): Int {
        var sum = 0
        for (shift in intArrayOf(16, 8, 0)) {
            sum += kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        }
        return sum
    }

    /**
     * Where the top bar's own ground ends, in pixels, or zero if it has none.
     *
     * Walked down the left margin, which is inside the bar but clear of its title, subtitle and
     * icons — so the only thing that can put an edge in that column is the bar's own bottom.
     */
    private fun barBottom(shot: Bitmap): Float {
        val ceiling = (300 * compose.density.density).toInt().coerceAtMost(shot.height - 1)
        for (y in 1 until ceiling) {
            if (distance(shot.getPixel(4, y), shot.getPixel(4, y - 1)) > 12) return y.toFloat()
        }
        return 0f
    }

    /**
     * The audit itself.
     *
     * Two exemptions, and only two. A node whose string *contains* the other's is its own parent
     * in the semantics tree — that is how a merged description sits over its parts, not two
     * things printed on each other. And an overlap that happens entirely above the top bar's
     * ground is the page sliding under an opaque bar, which is what a bar is for.
     *
     * That second exemption is the whole point of measuring the ground first rather than
     * hardcoding a bar height: it is granted by the pixels, not assumed. A bar with no ground
     * covers nothing, [barBottom] returns zero, no overlap is forgiven, and the fault this file
     * was written for comes straight back out.
     */
    private fun assertNothingPrintsOverAnything(state: String, shot: Bitmap) {
        val all = printed()
        val covered = barBottom(shot)
        assertTrue("$state: nothing was laid out at all", all.isNotEmpty())
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val a = all[i]
                val b = all[j]
                if (!a.overlaps(b)) continue
                if (a.text.contains(b.text) || b.text.contains(a.text)) continue
                if (minOf(a.bottom, b.bottom) <= covered) continue
                assertTrue(
                    "$state: \"${a.text}\" is printed over \"${b.text}\" " +
                        "(${(a.share(b) * 100).toInt()}% of the smaller box, " +
                        "bar ground ends at ${covered.toInt()}px)",
                    a.share(b) < 0.12f,
                )
            }
        }
    }

    private fun store(place: String = "Moscow", hours: Int = 26, rain: Int = 40) {
        WeatherStore.clear(app)
        WeatherStore.pin(app, Place(place, null, "Russia", 55.75, 37.62))
        WeatherStore.save(
            app,
            Forecast(
                place = place,
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(22.4, 21.0, Sky.SHOWERS, true, 68, 9.0, 23.0, 135, 1009.0, 5.0),
                hours = (0 until hours).map { hour ->
                    HourForecast(
                        time = LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 22.0 - hour * 0.3,
                        sky = Sky.SHOWERS,
                        day = true,
                        rain = rain,
                    )
                },
                days = (0 until 5).map { index ->
                    DayForecast(
                        date = LocalDate.now().plusDays(index.toLong()),
                        sky = Sky.SHOWERS,
                        high = 24.0 - index,
                        low = 13.0 - index,
                        rain = rain,
                        sunrise = LocalDateTime.now().minusHours(8).plusDays(index.toLong()),
                        sunset = LocalDateTime.now().plusHours(8).plusDays(index.toLong()),
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Scrolls the page, on a clock that stays under this test's control.
     *
     * Letting the clock run free and waiting for idle is the obvious way to do this and it hangs
     * for ever on any wet sky: [app.quire.weather.ui.SkyPulse] keeps a frame loop alive for as
     * long as it is raining, so Compose is never idle and never will be. The paused clock is
     * stepped by hand instead, which is what every other render test here does anyway.
     */
    /** The place the bar is showing has to be the place this test stored. */
    private fun assertShowing(place: String) {
        val titles = compose.onAllNodes(
            androidx.compose.ui.test.hasText(place, substring = true),
        ).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("the bar is not showing \"$place\" — a stale place survived", titles.isNotEmpty())
    }

    private fun scroll() {
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.85f, endY = height * 0.15f)
        }
        settle(rounds = 24)
    }

    @Test
    fun `nothing prints over anything, scrolled`() {
        store()
        compose.setContent { WeatherApp(WeatherModel(app)) }
        settle()
        assertShowing("Moscow")
        assertNothingPrintsOverAnything("at rest", shoot("audit-rest"))

        scroll()
        assertNothingPrintsOverAnything("scrolled", shoot("audit-scrolled"))

        scroll()
        assertNothingPrintsOverAnything("scrolled far", shoot("audit-scrolled-far"))
    }

    @Test
    fun `nothing prints over anything at the font scale an older phone is set to`() {
        store()
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = compose.density.density,
                    fontScale = 1.5f,
                ),
            ) {
                WeatherApp(WeatherModel(app))
            }
        }
        settle()
        assertShowing("Moscow")
        assertNothingPrintsOverAnything("at font scale 1.5", shoot("audit-large-type"))

        scroll()
        assertNothingPrintsOverAnything("at font scale 1.5, scrolled", shoot("audit-large-type-scrolled"))
    }

    @Test
    fun `nothing prints over anything with a place name longer than the bar`() {
        store(place = "Западный административный округ города Москвы")
        compose.setContent { WeatherApp(WeatherModel(app)) }
        settle()
        assertNothingPrintsOverAnything("with a long place name", shoot("audit-long-place"))

        scroll()
        assertNothingPrintsOverAnything("with a long place name, scrolled", shoot("audit-long-place-scrolled"))
    }

    @Test
    fun `nothing prints over anything before the first fetch`() {
        WeatherStore.clear(app)
        compose.setContent { WeatherApp(WeatherModel(app)) }
        settle()
        assertNothingPrintsOverAnything("before the first fetch", shoot("audit-empty"))
    }

    /**
     * A dry day draws no rain on the rain dial.
     *
     * The dial's sweep has a floor so a one-per-cent chance stays visible as a tick instead of
     * vanishing. Zero was going through that same floor, so nought per cent drew the tick too —
     * a mark that says "a little" on a day with none. Measured as ink: the whole point is that
     * there is less of it at zero than at one per cent.
     */
    /**
     * A dry day draws no rain on the rain dial.
     *
     * Counted in pixels this is unmeasurable: the droplet glyph inside the ring is drawn in the
     * very colour the arc is, so the dial is never free of accent and a seven-degree stub hides
     * inside the glyph's own count. The claim is arithmetic, so it is proved as arithmetic.
     */
    @Test
    fun `nought draws no arc, and a trace still draws one`() {
        assertTrue("nought per cent drew an arc", dialSweep(0f) == 0f)
        assertTrue("a reading the provider never sent drew an arc", dialSweep(null) == 0f)
        assertTrue("one per cent vanished instead of ticking", dialSweep(0.01f) > 0f)
        assertTrue("a full dial did not close", dialSweep(1f) == 360f)
        assertTrue("half a dial is not half a turn", kotlin.math.abs(dialSweep(0.5f) - 180f) < 0.1f)
    }

    /** Accent-coloured pixels inside a fractional window of the shot. */
    private fun accent(shot: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Int {
        var n = 0
        val x0 = (shot.width * left).toInt()
        val x1 = (shot.width * right).toInt()
        val y0 = (shot.height * top).toInt()
        val y1 = (shot.height * bottom).toInt()
        for (x in x0 until x1 step 2) {
            for (y in y0 until y1 step 2) {
                val p = shot.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // The dial's lit arc is the scheme's primary; the track and the page are not.
                if (kotlin.math.abs(r - b) > 24 || kotlin.math.abs(g - b) > 24) n++
            }
        }
        return n
    }

    /**
     * A reading never loses its unit.
     *
     * Two dead ends before this one, both worth recording. Semantics cannot answer it: the node
     * still *says* "1009 hPa" whether or not the phone drew the "hPa", which is exactly why one
     * clipped line could throw it away in silence. And `hasVisualOverflow` on the text layout
     * answers true for almost every string on the screen, so it separates nothing.
     *
     * What is measurable is the consequence. At a raised font scale the pressure no longer fits
     * one line of its cell. Fitted, it takes a second line and its box is taller than a short
     * reading's beside it; clipped, the box stays exactly as short and the unit is simply gone.
     * The comparison is inside one picture, so nothing has to be calibrated.
     */
    @Test
    fun `a reading too long for its cell wraps rather than losing its unit`() {
        store()
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(compose.density.density, fontScale = 1.5f),
            ) {
                WeatherApp(WeatherModel(app))
            }
        }
        settle()
        shoot("audit-units-large-type")

        val all = printed()
        val pressure = all.firstOrNull { it.text.contains("hPa") }
        val short = all.firstOrNull { it.text == "40%" }
        assertTrue("the pressure reading is missing entirely: ${all.map { it.text }}", pressure != null)
        assertTrue("the rain reading is missing entirely", short != null)
        val tall = pressure!!.bottom - pressure.top
        val one = short!!.bottom - short.top
        assertTrue(
            "the pressure was cut to one line instead of wrapping — its unit is gone " +
                "(pressure box ${tall}px, a one-line reading ${one}px)",
            tall > one * 1.5f,
        )
    }

    /**
     * The screen does not claim to be looking up weather it has no way to look up.
     *
     * With no location and no place named, the card asking for one is the whole message. The
     * line under it fired on exactly the condition that means nothing is being fetched.
     */
    @Test
    fun `an unlocated screen does not also claim to be fetching`() {
        WeatherStore.clear(app)
        compose.setContent { WeatherApp(WeatherModel(app)) }
        settle()
        shoot("audit-empty")
        val said = printed().map { it.text }
        assertTrue("the card asking for a place is missing: $said", said.any { it.contains("where you are") })
        assertTrue(
            "the screen says it is looking up the weather while asking where you are: $said",
            said.none { it.contains("Looking up") },
        )
    }

    /**
     * The pair of buttons on the first card anybody sees are the same height.
     *
     * "Share approximate location" wraps to two lines where "Choose a place" does not, and a row
     * laid out at wrap height gives each button its own — so the first card in the app opened
     * with one button visibly taller than the one beside it.
     */
    @Test
    fun `the two buttons on the location card are level`() {
        WeatherStore.clear(app)
        compose.setContent { WeatherApp(WeatherModel(app)) }
        settle()
        val boxes = printed().filter {
            it.text.contains("Choose") || it.text.contains("Share")
        }
        assertTrue("the location card's buttons are missing", boxes.size >= 2)
        val heights = boxes.map { it.bottom - it.top }
        val ragged = heights.max() - heights.min()
        assertTrue(
            "the buttons are ragged: heights $heights",
            ragged < 4f * compose.density.density,
        )
    }
}
