package app.papersky.weather.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.TestApp
import app.papersky.weather.scene.PaperSceneRenderer
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApp::class)
class SceneScreenshots {

    private val states = mapOf(
        "clear_day" to SceneState(daylight = 1f, sunProgress = 0.42f, cloudCover = 0.15f, windX = 2f, temperature = 22f, seed = 11),
        "golden_hour" to SceneState(daylight = 1f, sunProgress = 0.93f, cloudCover = 0.35f, windX = -1f, temperature = 17f, seed = 12),
        "dusk" to SceneState(daylight = 0.5f, sunProgress = 1.02f, nightProgress = 0.05f, cloudCover = 0.3f, temperature = 12f, seed = 13),
        "night_clear" to SceneState(daylight = 0f, sunProgress = -0.4f, nightProgress = 0.45f, cloudCover = 0.1f, temperature = 6f, moonPhase = 0.3f, seed = 14),
        "rain_day" to SceneState(daylight = 1f, sunProgress = 0.5f, cloudCover = 0.95f, rain = 0.7f, windX = 4f, temperature = 13f, seed = 15),
        "storm_night" to SceneState(daylight = 0.05f, sunProgress = -0.2f, nightProgress = 0.3f, cloudCover = 1f, rain = 0.9f, thunder = 1f, windX = -7f, windSpeed = 9f, temperature = 16f, seed = 16),
        "snow_day" to SceneState(daylight = 1f, sunProgress = 0.35f, cloudCover = 0.8f, snow = 0.8f, snowGround = 1f, windX = 1.5f, temperature = -4f, seed = 17),
        "fog_morning" to SceneState(daylight = 0.9f, sunProgress = 0.12f, cloudCover = 0.6f, fog = 0.85f, temperature = 4f, seed = 18),
        "rainbow" to SceneState(daylight = 1f, sunProgress = 0.8f, cloudCover = 0.45f, rainbow = 1f, temperature = 19f, seed = 19),
        "snow_night" to SceneState(daylight = 0f, sunProgress = -0.3f, nightProgress = 0.6f, cloudCover = 0.5f, snow = 0.5f, snowGround = 1f, temperature = -9f, moonPhase = 0.5f, seed = 20),
    )

    @Test
    fun renderScenes() {
        Shots.assumeEnabled()
        val density = 2.5f
        for ((name, s) in states) {
            val w = (412 * density).toInt()
            val h = (560 * density).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            PaperSceneRenderer(density).draw(Canvas(bmp), w.toFloat(), h.toFloat(), s, Palettes.forState(s), PaperSceneRenderer.Options(time = 12.5f, staticBolt = s.thunder > 0.5f))
            Shots.save("scene_$name", bmp)
        }
    }
}
