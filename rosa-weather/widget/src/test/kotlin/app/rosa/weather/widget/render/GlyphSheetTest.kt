package app.rosa.weather.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.model.WeatherCondition
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every pictogram, day and night, at three sizes on a dark and a pale sky: `build/widget-gallery/glyphs.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GlyphSheetTest {
    @Test
    fun glyphSheet() {
        val painter = WeatherGlyphPainter()
        val conditions = WeatherCondition.entries
        val sizes = floatArrayOf(40f, 84f, 160f)
        val cell = 180f
        val rows = conditions.size
        val width = (cell * 4 + 20).toInt()
        val height = (cell * rows + 20).toInt()
        val bitmap = Bitmap.createBitmap(width * 2, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint()
        bg.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF1E3A70.toInt(), 0xFF6F86B8.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        bg.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFFBBD3F3.toInt(), 0xFFF2F6FC.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(width.toFloat(), 0f, width * 2f, height.toFloat(), bg)
        for ((half, light) in listOf(0 to false, 1 to true)) {
            val ox = half * width + 10f
            conditions.forEachIndexed { row, condition ->
                val cy = 10f + row * cell + cell / 2
                sizes.forEachIndexed { i, size ->
                    val cx = ox + cell * i + cell / 2
                    painter.draw(canvas, condition, isDay = true, bounds = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), onLightBackground = light)
                }
                val cx = ox + cell * 3 + cell / 2
                painter.draw(canvas, condition, isDay = false, bounds = RectF(cx - 80f, cy - 80f, cx + 80f, cy + 80f), moonPhase = 0.3, onLightBackground = light)
            }
        }
        val out = File("build/widget-gallery").apply { mkdirs() }
        File(out, "glyphs.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The README strip: the main pictograms on an evening and a daytime sky. */
    @Test
    fun glyphStrip() {
        val painter = WeatherGlyphPainter()
        val shown = listOf(
            WeatherCondition.Clear to true,
            WeatherCondition.PartlyCloudy to true,
            WeatherCondition.Overcast to true,
            WeatherCondition.Fog to true,
            WeatherCondition.Rain to true,
            WeatherCondition.Snow to true,
            WeatherCondition.Thunderstorm to true,
            WeatherCondition.PartlyCloudy to false,
            WeatherCondition.Clear to false,
        )
        val cell = 150f
        val width = (cell * shown.size + 40).toInt()
        val height = (cell * 2 + 40).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint()
        val half = height / 2f
        bg.shader = LinearGradient(0f, 0f, 0f, half, 0xFF1F2A5C.toInt(), 0xFF6A4C8E.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), half, bg)
        bg.shader = LinearGradient(0f, half, 0f, height.toFloat(), 0xFF9CC3F2.toInt(), 0xFFE6EFFB.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, half, width.toFloat(), height.toFloat(), bg)
        for ((row, light) in listOf(0 to false, 1 to true)) {
            val cy = 20f + row * cell + cell / 2 + if (row == 1) 20f else 0f
            shown.forEachIndexed { i, (condition, day) ->
                val cx = 20f + cell * i + cell / 2
                val r = cell * 0.4f
                painter.draw(canvas, condition, isDay = day, bounds = RectF(cx - r, cy - r, cx + r, cy + r), moonPhase = 0.3, onLightBackground = light)
            }
        }
        val out = File("build/widget-gallery").apply { mkdirs() }
        File(out, "glyph-strip.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        exportDocImage(bitmap, "glyphs", 1080)
    }
}
