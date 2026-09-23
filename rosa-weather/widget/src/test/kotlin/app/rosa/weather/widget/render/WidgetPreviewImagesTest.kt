package app.rosa.weather.widget.render

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.provider.WidgetKind
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regenerates the static widget-picker previews (used on Android 13–14, before generated
 * previews) from the real renderer: `./gradlew :widget:testDebugUnitTest -Prosa.previews`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetPreviewImagesTest {
    @Test
    fun writePickerPreviews() {
        assumeTrue(System.getProperty("rosa.previews") != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val renderer = WidgetRenderer(context)
        val now = 1_758_628_800L
        val content = WidgetContent(
            "Moscow", isCurrentLocation = true,
            forecast = SampleForecast.create(SampleForecast.Scenario.RainyAfternoon, nowEpochSeconds = now),
            nowEpochSeconds = now, units = Units(),
        )
        val out = File("src/main/res/drawable-nodpi")
        for (kind in WidgetKind.entries) {
            val (w, h) = when (kind) {
                WidgetKind.Glass -> 320f to 170f
                WidgetKind.Sky -> 170f to 170f
                WidgetKind.Almanac -> 250f to 260f
            }
            val bitmap = renderer.render(WidgetRenderRequest(w, h, kind.defaultConfig, content, 24f, systemNight = false), 2f)
            File(out, "widget_preview_${kind.name.lowercase()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
