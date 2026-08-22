package app.quire.weather

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import app.quire.calendar.m3.QuireTheme
import app.quire.weather.ui.Dial
import app.quire.weather.ui.Reading
import app.quire.weather.ui.ReadingGrid
import app.quire.weather.ui.markTag
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * The readings, drawn.
 *
 * Six identical rings needed no test beyond "the arc sweeps": one picture, one behaviour. Six
 * different marks need two things proved instead, and neither is provable by reading the code.
 *
 * The first is that they are actually different — that a clover of drops, a filled puff, a
 * compass, a gust profile, a UV scale and a barometer come out as six distinguishable pictures
 * rather than six variations on a blob at the size a phone draws them.
 *
 * The second is that each one still *answers its own number*. A mark that looks handsome and
 * ignores its reading is a decoration, and this project does not ship decorations over data:
 * every assertion below moves one value and insists the picture moves with it, in the direction
 * and on the axis that particular quantity is encoded on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ReadingsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun stopTheClock() {
        compose.mainClock.autoAdvance = false
    }

    /**
     * Draws a grid of readings once, and hands back each mark photographed on its own.
     *
     * A test rule gets one `setContent`, so every reading a test wants to compare goes up in the
     * same pass and is captured by its tag afterwards. Capturing the tagged node rather than
     * cropping the root by arithmetic is what keeps these measurements honest when the tile size
     * or the type underneath it changes.
     */
    private fun marks(readings: List<Reading>, name: String? = null): List<Bitmap> {
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.width(360.dp)) {
                        ReadingGrid(readings)
                    }
                }
            }
        }
        compose.waitForIdle()
        val shots = readings.mapIndexed { index, reading ->
            compose.onAllNodesWithTag(markTag(reading.dial))[
                readings.take(index).count { it.dial == reading.dial },
            ].captureToImage().asAndroidBitmap()
        }
        if (name != null) {
            shots.forEachIndexed { index, bitmap ->
                File(outputDir, "$name-$index.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        return shots
    }

    /** Two of the same dial at two values, for asking whether the mark answers its number. */
    private fun pair(dial: Dial, low: Reading, high: Reading): Pair<Bitmap, Bitmap> {
        val shots = marks(listOf(low, high))
        return shots[0] to shots[1]
    }

    /**
     * The mark, separated from the tile it is drawn on.
     *
     * Three traps here, all found the hard way, and each one makes every tile score the same —
     * which looks exactly like six marks that ignore their numbers.
     *
     * The node is clipped to its expressive shape, so a corner pixel is not the tile at all and
     * the ground cannot be sampled from one. There are two grounds inside these bounds, not
     * one: the container inside the silhouette and the page showing through outside it, and the
     * page is opaque, so left in it swamps the mark twenty to one. And picking the ground as
     * "the commonest colour" fails on the one mark that is a fill — at ninety per cent humidity
     * the water *is* the commonest colour, so the vessel reads as empty and the reading inverts.
     *
     * So ink is not defined against a ground at all. Every mark is drawn in the accent (or, for
     * UV, in its own published scale), and this scheme's accent is warm while every surface in
     * it is cool — so ink is simply the warm pixels. Tracks and tick marks are drawn in the cool
     * outline colour and fall out of the count on purpose: they are the scale, not the reading.
     */
    private class Mask(val w: Int, val h: Int, val on: BooleanArray) {
        val lit: Int get() = on.count { it }
    }

    private fun maskOf(b: Bitmap): Mask {
        val w = b.width / 2
        val h = b.height / 2
        val on = BooleanArray(w * h) { idx ->
            val p = b.getPixel((idx % w) * 2, (idx / w) * 2)
            val warmth = ((p shr 16) and 0xFF) - (p and 0xFF)
            ((p ushr 24) and 0xFF) > 200 && warmth > 20
        }
        return Mask(w, h, on)
    }

    private fun litCount(b: Bitmap): Int = maskOf(b).lit

    private fun litCentroidX(b: Bitmap): Float {
        val m = maskOf(b)
        var sum = 0f
        var n = 0
        for (j in 0 until m.h) for (i in 0 until m.w) if (m.on[j * m.w + i]) { sum += i; n++ }
        return if (n == 0) -1f else sum * 2f / n
    }

    /** The topmost row the mark reaches, as a fraction of the tile. */
    private fun litTop(b: Bitmap): Float {
        val m = maskOf(b)
        for (j in 0 until m.h) for (i in 0 until m.w) if (m.on[j * m.w + i]) return j.toFloat() / m.h
        return 1f
    }

    /** Where the near-white pip sits, for the one mark whose marker is not the accent. */
    private fun pipCentroidX(b: Bitmap): Float {
        var sum = 0f
        var n = 0
        for (x in 0 until b.width) for (y in 0 until b.height) {
            val p = b.getPixel(x, y)
            if (((p ushr 24) and 0xFF) > 200 &&
                ((p shr 16) and 0xFF) > 235 && ((p shr 8) and 0xFF) > 235 && (p and 0xFF) > 235
            ) { sum += x; n++ }
        }
        return if (n == 0) -1f else sum / n
    }

    /** A coarse map of where the ink sits, for telling two marks apart. */
    private fun signature(b: Bitmap): List<Int> {
        val m = maskOf(b)
        val cells = MutableList(16) { 0 }
        for (j in 0 until m.h) for (i in 0 until m.w) {
            if (m.on[j * m.w + i]) {
                val cell = (j * 4 / m.h) * 4 + (i * 4 / m.w)
                cells[cell] = cells[cell] + 1
            }
        }
        // Quantised, so two marks count as different only when they differ visibly.
        val total = cells.sum().coerceAtLeast(1)
        return cells.map { it * 20 / total }
    }

    private fun reading(dial: Dial, fraction: Float, bearing: Float? = null, second: Float? = null) =
        Reading(dial = dial, label = dial.name, value = "—", fraction = fraction, bearing = bearing, second = second)

    @Test
    fun `each reading draws its own mark, and no two are alike`() {
        val all = listOf(
            reading(Dial.RAIN, 0.7f),
            reading(Dial.HUMIDITY, 0.81f),
            reading(Dial.WIND, 0.28f, bearing = 45f),
            reading(Dial.GUST, 0.39f, second = 0.2f),
            reading(Dial.UV, 0.55f),
            reading(Dial.PRESSURE, 0.40f),
        )
        val shots = marks(all, "reading")

        val signatures = all.map { it.dial }.zip(shots.map { signature(it) })
        shots.forEachIndexed { index, tile ->
            val dial = all[index].dial
            val lit = litCount(tile)
            val samples = (tile.width / 2) * (tile.height / 2)
            assertTrue("$dial drew nothing at all", lit > 30)
            // Generous on purpose: a vessel at eighty per cent humidity is *supposed* to be
            // most of its tile. What this catches is a mark that has flooded the whole one.
            assertTrue("$dial flooded its tile ($lit of $samples)", lit < samples * 85 / 100)
        }
        for (i in signatures.indices) {
            for (j in i + 1 until signatures.size) {
                assertTrue(
                    "${signatures[i].first} and ${signatures[j].first} draw the same picture",
                    signatures[i].second != signatures[j].second,
                )
            }
        }
    }

    /** A chance of rain is drawn in fifths, and more chance is more drops. */
    @Test
    fun `the drops count the chance of rain`() {
        val (little, lots) = pair(Dial.RAIN, reading(Dial.RAIN, 0.1f), reading(Dial.RAIN, 0.95f))
        assertTrue(
            "a near-certain soaking drew no more drops than a one-in-ten " +
                "(${litCount(lots)} vs ${litCount(little)})",
            litCount(lots) > litCount(little) * 2,
        )
    }

    /** The puff is a vessel, and humidity is how full it is. */
    @Test
    fun `the vessel fills with the humidity`() {
        val (dry, damp) = pair(
            Dial.HUMIDITY,
            reading(Dial.HUMIDITY, 0.15f),
            reading(Dial.HUMIDITY, 0.9f),
        )
        assertTrue(
            "the vessel did not fill (${litCount(damp)} vs ${litCount(dry)})",
            litCount(damp) > litCount(dry) * 2,
        )
        assertTrue("the waterline did not rise (${litTop(damp)})", litTop(damp) < 0.35f)
    }

    /** The needle points where the wind is going, which is a different picture at each bearing. */
    @Test
    fun `the needle turns with the wind`() {
        val shots = marks(
            listOf(
                reading(Dial.WIND, 0.5f, bearing = 90f),
                reading(Dial.WIND, 0.5f, bearing = 270f),
                reading(Dial.WIND, 0.5f, bearing = null),
            ),
        )
        val east = litCentroidX(shots[0])
        val west = litCentroidX(shots[1])
        assertTrue("the needle did not turn (east $east, west $west)", east > west + 6f)
        assertTrue(
            "a bearingless wind still drew a needle",
            litCount(shots[2]) < litCount(shots[0]),
        )
    }

    /** A gust is a spike above the steady wind, and a harder gust spikes higher. */
    @Test
    fun `the gust spikes above the steady wind`() {
        val (gentle, fierce) = pair(
            Dial.GUST,
            reading(Dial.GUST, 0.3f, second = 0.25f),
            reading(Dial.GUST, 0.95f, second = 0.25f),
        )
        assertTrue(
            "a fierce gust did not reach higher than a gentle one " +
                "(${litTop(fierce)} vs ${litTop(gentle)})",
            litTop(fierce) < litTop(gentle) - 0.08f,
        )
    }

    /** The marker slides along the published scale as the index climbs. */
    @Test
    fun `the UV marker walks its scale`() {
        val (low, high) = pair(Dial.UV, reading(Dial.UV, 0.05f), reading(Dial.UV, 0.95f))
        val a = pipCentroidX(low)
        val b = pipCentroidX(high)
        assertTrue("no marker was drawn on the UV scale", a > 0 && b > 0)
        assertTrue("the UV marker did not move ($a -> $b)", b > a + 20f)
    }

    /** The barometer's lit arc grows with the pressure. */
    @Test
    fun `the barometer sweeps with the pressure`() {
        val (low, high) = pair(
            Dial.PRESSURE,
            reading(Dial.PRESSURE, 0.05f),
            reading(Dial.PRESSURE, 0.95f),
        )
        assertTrue(
            "the barometer did not sweep (${litCount(low)} -> ${litCount(high)})",
            litCount(high) > litCount(low) + 40,
        )
    }
}
