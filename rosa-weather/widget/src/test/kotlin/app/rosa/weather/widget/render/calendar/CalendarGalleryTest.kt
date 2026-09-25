package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetRenderer
import app.rosa.weather.widget.render.exportDocImage
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The calendar widget's seasons and layouts, rendered into contact sheets under
 * `build/widget-gallery/` — a visual regression aid, and how the scenes were painted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-rXX-w411dp-h891dp-xxhdpi")
class CalendarGalleryTest {
    private val out = File("build/widget-gallery").apply { mkdirs() }

    /** The twelve paintings, one per month, at a 4×3 widget's size. */
    @Test
    fun scenes() {
        val density = 2f
        val (w, h) = 314f to 252f
        val gap = 14f
        val columns = 4
        val sheetW = gap + columns * (w + gap)
        val sheetH = gap + 3 * (h + gap + 12f)
        val bmp = Bitmap.createBitmap((sheetW * density).toInt(), (sheetH * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(density, density)
        canvas.drawColor(0xFF1E1E24.toInt())
        val scene = SeasonScene()
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 10f }
        for (m in 1..12) {
            val x = gap + ((m - 1) % columns) * (w + gap)
            val y = gap + ((m - 1) / columns) * (h + gap + 12f)
            canvas.save()
            canvas.clipRect(x, y, x + w, y + h)
            scene.draw(canvas, RectF(x, y, x + w, y + h), MonthArt.of(m))
            canvas.restore()
            canvas.drawText("$m", x, y + h + 10f, label)
        }
        File(out, "calendar-scenes.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val renderer = WidgetRenderer(ApplicationProvider.getApplicationContext())
    private val calendar = WidgetConfig(face = WidgetFace.Calendar, style = WidgetStyle.Sky, opacity = 1f)
    private val ru = Locale.forLanguageTag("ru-RU")

    /** A day of [month] in 2026 with a forecast from it and a few events around it. */
    private fun scene(month: Int, day: Int = 16): Pair<WidgetContent, CalendarView> {
        val today = LocalDate.of(2026, month, day)
        val now = today.atTime(12, 0).toEpochSecond(ZoneOffset.ofHours(3))
        val forecast = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now)
        val content = WidgetContent("Москва", true, forecast, now, Units())
        val events = mapOf(
            today to listOf(0xFF4285F4.toInt(), 0xFF33B679.toInt()),
            today.plusDays(2) to listOf(0xFFF4511E.toInt()),
            today.plusDays(5) to listOf(0xFF8E24AA.toInt(), 0xFF4285F4.toInt(), 0xFFF6BF26.toInt()),
            today.minusDays(4) to listOf(0xFF33B679.toInt()),
        )
        return content to CalendarView(YearMonth.from(today), today, events, ru)
    }

    private fun wallpaper(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF3A2A20.toInt(), 0xFF5B4636.toInt(), 0xFF2A2F3E.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }

    private fun sheet(name: String, cells: List<Triple<String, Pair<Float, Float>, WidgetRenderRequest>>, columns: Int, density: Float = 2f) {
        val gap = 14f
        val rows = cells.chunked(columns)
        val sheetW = gap + rows.maxOf { row -> row.sumOf { (it.second.first + gap).toDouble() } }.toFloat()
        val sheetH = gap + rows.sumOf { row -> (row.maxOf { it.second.second } + gap + 12f).toDouble() }.toFloat()
        val bmp = Bitmap.createBitmap((sheetW * density).toInt(), (sheetH * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(density, density)
        wallpaper(canvas, sheetW, sheetH)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 9f }
        var y = gap
        for (row in rows) {
            var x = gap
            for ((text, size, request) in row) {
                canvas.save()
                canvas.translate(x, y)
                renderer.draw(canvas, request)
                canvas.restore()
                canvas.drawText(text, x, y + size.second + 10f, label)
                x += size.first + gap
            }
            y += row.maxOf { it.second.second } + gap + 12f
        }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        exportDocImage(bmp, "widgets-$name", 1400)
    }

    /** The year: every month as a 4×3 widget in its own season. */
    @Test
    fun months() {
        val size = 314f to 252f
        sheet("calendar-months", (1..12).map { m ->
            val (content, view) = scene(m)
            Triple("$m", size, WidgetRenderRequest(size.first, size.second, calendar, content, 22f, systemNight = false, calendar = view))
        }, columns = 4)
    }

    /** One month at every size: a date tile, a week strip, today beside the month, the wall calendar. */
    @Test
    fun sizes() {
        val (content, view) = scene(9, 25)
        val sizes = listOf(
            "1×1" to (68f to 72f), "2×1" to (150f to 72f), "4×1" to (314f to 72f), "1×2" to (68f to 162f),
            "2×2" to (150f to 162f), "3×2" to (232f to 162f), "4×2" to (314f to 162f), "2×3" to (150f to 252f),
            "4×3" to (314f to 252f), "4×4" to (314f to 342f), "5×5" to (396f to 432f),
        )
        sheet("calendar-sizes", sizes.map { (label, size) ->
            Triple(label, size, WidgetRenderRequest(size.first, size.second, calendar, content, 22f, systemNight = false, calendar = view))
        }, columns = 4)
    }

    /** One month in every style, and the glass in both themes. */
    @Test
    fun styles() {
        val (content, view) = scene(10, 12)
        val size = 314f to 252f
        val configs = listOf(
            "Сезон" to calendar,
            "Стекло" to calendar.copy(style = WidgetStyle.Glass, opacity = 0.72f),
            "Стекло, светлая" to calendar.copy(style = WidgetStyle.Glass, opacity = 0.72f, theme = WidgetTheme.Light),
            "Прозрачный" to calendar.copy(style = WidgetStyle.Clear),
            "Тональный" to calendar.copy(style = WidgetStyle.Tonal, opacity = 0.85f),
            "Бумага" to calendar.copy(style = WidgetStyle.Paper),
            "Сезон, номера недель" to calendar.copy(calendar = calendar.calendar.copy(weekNumbers = true)),
            "Сезон, светлая" to calendar.copy(theme = WidgetTheme.Light),
        )
        sheet("calendar-styles", configs.map { (label, config) ->
            Triple(label, size, WidgetRenderRequest(size.first, size.second, config, content, 22f, systemNight = false, calendar = view))
        }, columns = 4)
    }
}
