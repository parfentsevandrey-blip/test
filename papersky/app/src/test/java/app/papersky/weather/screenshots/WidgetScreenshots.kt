package app.papersky.weather.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.Fixtures
import app.papersky.weather.TestApp
import app.papersky.weather.container
import app.papersky.weather.core.data.ForecastStore
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.PrecipUnit
import app.papersky.weather.core.model.PressureUnit
import app.papersky.weather.core.model.TempUnit
import app.papersky.weather.core.model.Units
import app.papersky.weather.core.model.WindUnit
import app.papersky.weather.core.model.SampleForecast
import app.papersky.weather.widget.PaperskyWidget
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.WidgetPreset
import androidx.glance.appwidget.compose
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real widget RemoteViews at many grid sizes onto a fake wallpaper, one sheet per
 * preset — a quick way to eyeball adaptive layouts.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApp::class, qualifiers = "w411dp-h914dp-xxhdpi")
class WidgetScreenshots {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    // (cols, rows) → dp, roughly a 5-column Pixel launcher grid.
    private val sizes = listOf(
        1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1,
        1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2,
        1 to 3, 2 to 3, 3 to 3, 4 to 3,
        1 to 4, 2 to 4, 4 to 4, 5 to 5,
    )

    private fun dp(cols: Int, rows: Int) = DpSize((cols * 76 - 12).dp, (rows * 104 - 16).dp)

    private fun seed(forecast: Forecast, units: Units = Units()) = runBlocking {
        val c = app.container
        c.settings.update { it.copy(units = units) }
        c.places.updateDevice(Fixtures.moscow)
        c.weather.ensureLoaded()
        forecastStorePut(forecast)
    }

    private suspend fun forecastStorePut(f: Forecast) {
        val field = app.container.weather.javaClass.getDeclaredField("store").apply { isAccessible = true }
        val store = field.get(app.container.weather) as ForecastStore
        store.put(f)
    }

    private fun render(config: WidgetConfig, size: DpSize): Bitmap {
        val density = app.resources.displayMetrics.density
        val rv = runBlocking { PaperskyWidget().compose(app, size = size, state = config.toPreferences()) }
        val wPx = (size.width.value * density).toInt()
        val hPx = (size.height.value * density).toInt()
        val host = FrameLayout(app)
        val view = rv.apply(app, host)
        host.addView(view, FrameLayout.LayoutParams(wPx, hPx))
        host.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, wPx, hPx)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bmp))
        return bmp
    }

    private fun sheet(name: String, config: WidgetConfig) {
        val density = app.resources.displayMetrics.density
        val tiles = sizes.map { (c, r) -> Triple(c, r, render(config, dp(c, r))) }
        // Lay tiles out in rows of up to ~900dp.
        val gap = (18 * density).toInt()
        val maxW = (980 * density).toInt()
        var x = gap
        var y = gap
        var rowH = 0
        val placed = mutableListOf<Pair<Bitmap, Pair<Int, Int>>>()
        for ((_, _, b) in tiles) {
            if (x + b.width + gap > maxW) { x = gap; y += rowH + gap; rowH = 0 }
            placed += b to (x to y)
            x += b.width + gap
            rowH = maxOf(rowH, b.height)
        }
        val out = Bitmap.createBitmap(maxW, y + rowH + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val wall = Paint().apply {
            shader = LinearGradient(0f, 0f, out.width.toFloat(), out.height.toFloat(),
                intArrayOf(0xFF2F4858.toInt(), 0xFF5B6C8F.toInt(), 0xFFB38B91.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), wall)
        for ((b, pos) in placed) canvas.drawBitmap(b, pos.first.toFloat(), pos.second.toFloat(), null)
        Shots.save("widgets_$name", out)
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun rainyMoscow() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        sheet("rain_living", WidgetPreset.LivingWindow.config)
        sheet("rain_paper", WidgetPreset.PaperNote.config)
        sheet("rain_glass", WidgetPreset.Glass.config)
    }

    @Test
    fun sunnySample() {
        Shots.assumeEnabled()
        val sample = SampleForecast.build(System.currentTimeMillis())
        seed(sample.copy(placeId = Place.HERE), Units(TempUnit.Fahrenheit, WindUnit.MilesPerHour, PressureUnit.InchesOfMercury, PrecipUnit.Inches))
        sheet("sun_living", WidgetPreset.LivingWindow.config)
        sheet("sun_riso", WidgetPreset.Riso.config)
        sheet("sun_ink", WidgetPreset.NightLight.config.copy(showClock = true))
    }
}
