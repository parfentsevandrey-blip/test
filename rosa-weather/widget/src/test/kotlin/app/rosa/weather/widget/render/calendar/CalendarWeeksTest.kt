package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.widget.R
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The year week by week: all 52 pictures, a quarter to a sheet, into
 * `build/widget-gallery/calendar-weeks-*.png` — and a few by night for the dark theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarWeeksTest {
    private val out = File("build/widget-gallery").apply { mkdirs() }

    @Test
    fun everyWeekHasItsOwnPicture() {
        val weeks = (1..SeasonClock.WEEKS).map { WeekArt.of(it) }
        assertThat(weeks.map { it.week }).containsExactlyElementsIn(1..SeasonClock.WEEKS).inOrder()
        // Every month's weeks fall on pictures of their own.
        val months = (1..12).flatMap { m -> SeasonClock.weeksOf(m).let { (a, b) -> (a..b).toList() } }.toSet()
        assertThat(months).containsExactlyElementsIn(1..SeasonClock.WEEKS)
        // The season still moves on under them, every week.
        for (w in 1 until SeasonClock.WEEKS) {
            assertThat(SeasonClock.of(w)).isNotEqualTo(SeasonClock.of(w + 1).copy(week = w))
        }
        // Named, in both languages.
        val titles = ApplicationProvider.getApplicationContext<android.content.Context>().resources.getStringArray(R.array.calendar_weeks)
        assertThat(titles).hasLength(SeasonClock.WEEKS)
        assertThat(titles.toSet()).hasSize(SeasonClock.WEEKS)
    }

    @Test
    fun paintedDifferently() {
        // No two weeks paint the same picture: compare small thumbnails pixel by pixel.
        val scene = SeasonScene()
        val thumbs = (1..SeasonClock.WEEKS).map { week ->
            val bitmap = scene.paint(WeekArt.of(week), 48f, 40f, 1f)
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); bitmap.recycle() }
        }
        for (a in thumbs.indices) for (b in a + 1 until thumbs.size) {
            val diff = thumbs[a].indices.sumOf { i -> distance(thumbs[a][i], thumbs[b][i]) } / thumbs[a].size
            assertThat(diff).isGreaterThan(8)
        }
    }

    @Test fun winter() = sheet("calendar-weeks-1-winter", 1..13)

    @Test fun spring() = sheet("calendar-weeks-2-spring", 14..26)

    @Test fun summer() = sheet("calendar-weeks-3-summer", 27..39)

    @Test fun autumn() = sheet("calendar-weeks-4-autumn", 40..52)

    @Test fun night() = sheet("calendar-weeks-night", listOf(1, 4, 8, 16, 25, 27, 31, 35, 39, 42, 46, 52), night = true)

    /** The whole year on one sheet, for the README: every week's picture with its name. */
    @Test
    fun year() {
        val titles = ApplicationProvider.getApplicationContext<android.content.Context>().resources.getStringArray(R.array.calendar_weeks)
        val (w, h) = 200f to 150f
        val columns = 8
        val gap = 8f
        val density = 1.5f
        val rows = (SeasonClock.WEEKS + columns - 1) / columns
        val sheetW = gap + columns * (w + gap)
        val sheetH = gap + rows * (h + gap + 15f)
        val bmp = Bitmap.createBitmap((sheetW * density).toInt(), (sheetH * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(density, density)
        canvas.drawColor(0xFF15171F.toInt())
        val scene = SeasonScene()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); textSize = 10f }
        for (week in 1..SeasonClock.WEEKS) {
            val x = gap + ((week - 1) % columns) * (w + gap)
            val y = gap + ((week - 1) / columns) * (h + gap + 15f)
            val painting = scene.paint(WeekArt.of(week), w, h, density)
            canvas.drawBitmap(painting, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            painting.recycle()
            canvas.drawText("$week · ${titles[week - 1]}", x + 1f, y + h + 11f, label)
        }
        File(out, "calendar-year.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.rosa.weather.widget.render.exportDocImage(bmp, "calendar-year", 1400)
    }

    /** The scenes at a phone widget's other proportions: a wide 4 × 2, a tall 2 × 3. */
    @Test fun proportions() = sheet("calendar-weeks-proportions", listOf(4, 10, 39, 42, 46, 51, 52, 29), w = 300f, h = 150f, columns = 4)

    private fun distance(a: Int, b: Int): Int =
        kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + kotlin.math.abs((a and 0xFF) - (b and 0xFF))

    private fun sheet(name: String, weeks: Iterable<Int>, night: Boolean = false, w: Float = 220f, h: Float = 190f, columns: Int = 5) {
        val titles = ApplicationProvider.getApplicationContext<android.content.Context>().resources.getStringArray(R.array.calendar_weeks)
        val list = weeks.toList()
        val density = 1.5f
        val gap = 10f
        val rows = (list.size + columns - 1) / columns
        val sheetW = gap + columns * (w + gap)
        val sheetH = gap + rows * (h + gap + 14f)
        val bmp = Bitmap.createBitmap((sheetW * density).toInt(), (sheetH * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(density, density)
        canvas.drawColor(0xFF1E1E24.toInt())
        val scene = SeasonScene()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); textSize = 10f }
        for ((i, week) in list.withIndex()) {
            val x = gap + (i % columns) * (w + gap)
            val y = gap + (i / columns) * (h + gap + 14f)
            val painting = scene.paint(WeekArt.of(week), w, h, density, live = false, night = night)
            canvas.drawBitmap(painting, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            painting.recycle()
            canvas.drawText("$week · ${titles[week - 1]}", x, y + h + 11f, label)
        }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
