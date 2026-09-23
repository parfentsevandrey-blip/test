package app.rosa.weather.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the widget across launcher grid sizes and styles into contact sheets under
 * `build/widget-gallery/` — a visual regression aid (and how these widgets were designed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class WidgetGalleryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val renderer = WidgetRenderer(context)
    private val out = File("build/widget-gallery").apply { mkdirs() }

    private val cells = listOf(
        "1×1" to (68f to 72f), "2×1" to (150f to 72f), "4×1" to (314f to 72f),
        "1×2" to (68f to 162f), "2×2" to (150f to 162f), "3×2" to (232f to 162f), "4×2" to (314f to 162f),
        "1×4" to (68f to 342f), "2×3" to (150f to 252f), "4×3" to (314f to 252f), "4×4" to (314f to 342f),
        "5×5" to (396f to 432f),
    )

    @Test
    fun sizes() {
        for (style in WidgetStyle.entries) {
            sheet(
                "sizes-${style.name.lowercase()}",
                SampleForecast.Scenario.RainyAfternoon,
                WidgetConfig(style = style, opacity = if (style == WidgetStyle.Sky) 1f else 0.72f),
                now = 1_758_628_800L,
            )
        }
    }

    @Test
    fun scenarios() {
        val times = listOf(
            SampleForecast.Scenario.SunnyMild to 1_758_621_600L,
            SampleForecast.Scenario.RainyAfternoon to 1_758_628_800L,
            SampleForecast.Scenario.SnowyCold to 1_758_610_800L,
            SampleForecast.Scenario.StormyWarm to 1_758_643_200L,
            SampleForecast.Scenario.FoggyMorning to 1_758_598_200L,
            SampleForecast.Scenario.ClearNight to 1_758_664_800L,
        )
        for (style in listOf(WidgetStyle.Sky, WidgetStyle.Glass)) {
            val density = 2f
            val sizes = listOf(150f to 162f, 314f to 162f, 314f to 252f)
            val gap = 16f
            val rowH = 252f + gap
            val sheetW = gap + sizes.sumOf { (it.first + gap).toDouble() }.toFloat()
            val sheetH = gap + times.size * rowH
            val bmp = Bitmap.createBitmap((sheetW * density).toInt(), (sheetH * density).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(density, density)
            wallpaper(canvas, sheetW, sheetH)
            times.forEachIndexed { row, (scenario, now) ->
                val forecast = SampleForecast.create(scenario, nowEpochSeconds = now)
                val content = WidgetContent("Москва", true, forecast, now, Units())
                var x = gap
                sizes.forEach { (w, h) ->
                    canvas.save()
                    canvas.translate(x, gap + row * rowH)
                    val config = WidgetConfig(style = style, opacity = if (style == WidgetStyle.Sky) 1f else 0.72f)
                    renderer.draw(canvas, WidgetRenderRequest(w, h, config, content, 22f, systemNight = false, seed = row))
                    canvas.restore()
                    x += w + gap
                }
            }
            File(out, "scenarios-${style.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun sheet(name: String, scenario: SampleForecast.Scenario, config: WidgetConfig, now: Long, subset: Boolean = false) {
        val density = 2.625f
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now)
        val content = WidgetContent("Москва", isCurrentLocation = true, forecast = forecast, nowEpochSeconds = now, units = Units())
        val list = if (subset) cells.filter { it.first in setOf("1×1", "2×2", "4×2", "4×4") } else cells
        val gap = 18f
        val sheetW = 860f
        var x = gap
        var y = gap
        var rowH = 0f
        val placed = list.map { (label, size) ->
            val (w, h) = size
            if (x + w > sheetW - gap) {
                x = gap; y += rowH + gap + 14f; rowH = 0f
            }
            val pos = Triple(label, x to y, w to h)
            x += w + gap
            rowH = maxOf(rowH, h)
            pos
        }
        val sheetH = y + rowH + gap + 14f
        val bmp = Bitmap.createBitmap((sheetW * density).toInt(), (sheetH * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(density, density)
        wallpaper(canvas, sheetW, sheetH)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 9f }
        placed.forEach { (text, pos, size) ->
            canvas.save()
            canvas.translate(pos.first, pos.second)
            renderer.draw(
                canvas,
                WidgetRenderRequest(size.first, size.second, config, content, cornerRadiusDp = 22f, systemNight = false, seed = 7),
            )
            canvas.restore()
            canvas.drawText(text, pos.first, pos.second + size.second + 11f, label)
        }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun wallpaper(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF2B3A67.toInt(), 0xFF7A5C8E.toInt(), 0xFFE0A07A.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.75f, h * 0.3f, w * 0.5f, 0x88FFE0B0.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.2f, h * 0.8f, w * 0.4f, 0x6633CCAA, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }
}
