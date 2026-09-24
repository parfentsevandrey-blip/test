package app.rosa.weather.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import app.rosa.weather.core.designsystem.glyph.GlyphRaster
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter.Part
import app.rosa.weather.core.model.WeatherCondition
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The split the app uses to animate glyphs cheaply must look exactly like the whole glyph. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GlyphLayersTest {
    private val painter = WeatherGlyphPainter()
    private val size = 120
    private val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())

    private fun render(block: (Canvas) -> Unit): Bitmap = createBitmap(size, size).also { block(Canvas(it)) }

    /**
     * Largest channel difference, compared premultiplied as the pixels are blended: un-premultiplying
     * a nearly transparent pixel would blow a rounding step of 1 up into dozens.
     */
    private fun maxDifference(a: Bitmap, b: Bitmap): Int {
        fun premultiplied(c: Int, channel: Int) = channel * Color.alpha(c) / 255
        var worst = 0
        for (y in 0 until size) for (x in 0 until size) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            worst = max(worst, abs(Color.alpha(p) - Color.alpha(q)))
            worst = max(worst, abs(premultiplied(p, Color.red(p)) - premultiplied(q, Color.red(q))))
            worst = max(worst, abs(premultiplied(p, Color.green(p)) - premultiplied(q, Color.green(q))))
            worst = max(worst, abs(premultiplied(p, Color.blue(p)) - premultiplied(q, Color.blue(q))))
        }
        return worst
    }

    @Test
    fun `live part beneath the cached clouds adds up to the whole glyph`() {
        for (condition in WeatherCondition.entries) for (isDay in listOf(true, false)) for (light in listOf(false, true)) {
            val whole = render { painter.draw(it, condition, isDay, bounds, onLightBackground = light) }
            val clouds = render { painter.draw(it, condition, isDay, bounds, onLightBackground = light, part = Part.Clouds) }
            val layered = render {
                painter.draw(it, condition, isDay, bounds, onLightBackground = light, part = Part.Beneath)
                it.drawBitmap(clouds, 0f, 0f, null)
            }
            // Stacked translucent layers round a little differently when blended as one bitmap
            // (a few 1/255 steps); a missing or misordered part would differ by far more.
            assertWithMessage("$condition day=$isDay light=$light").that(maxDifference(whole, layered)).isAtMost(6)
        }
    }

    @Test
    fun `still glyphs are rendered once and shared`() {
        val tone = WeatherGlyphPainter.Tone.Color
        val a = GlyphRaster.get(WeatherCondition.Rain, true, 0.3, tone, 0, false, Part.Whole, 80, 80)
        // Moon phase and tint change nothing in a coloured daytime glyph.
        val b = GlyphRaster.get(WeatherCondition.Rain, true, 0.7, tone, 0xFF00FF00.toInt(), false, Part.Whole, 80, 80)
        assertThat(b).isSameInstanceAs(a)
        assertThat(GlyphRaster.get(WeatherCondition.Rain, true, 0.3, tone, 0, false, Part.Whole, 96, 96)).isNotSameInstanceAs(a)
        assertThat(GlyphRaster.get(WeatherCondition.Rain, true, 0.3, tone, 0, true, Part.Whole, 80, 80)).isNotSameInstanceAs(a)
    }

    @Test
    fun `only rays and precipitation move`() {
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.Overcast, true)).isFalse()
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.Fog, true)).isFalse()
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.Clear, false)).isFalse()
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.PartlyCloudy, false)).isFalse()
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.Clear, true)).isTrue()
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.Snow, false)).isTrue()
        assertThat(WeatherGlyphPainter.moves(WeatherCondition.Thunderstorm, true)).isTrue()
    }
}
