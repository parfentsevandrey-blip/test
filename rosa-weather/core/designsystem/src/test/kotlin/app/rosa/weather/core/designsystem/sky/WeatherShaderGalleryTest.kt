package app.rosa.weather.core.designsystem.sky

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.momentAt
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the weather shaders — sky, precipitation and the window pane, layered as [SkyScene]
 * layers them — for moments the app can't be caught in on demand (a lightning strike), to
 * `build/weather/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w810dp-h1755dp-mdpi")
class WeatherShaderGalleryTest {
    @get:Rule val compose = createComposeRule()

    // A 1080 × 2340 phone: sky at half resolution, pane at three quarters (Balanced).
    private val skyW = 540
    private val skyH = 1170
    private val paneW = 810
    private val paneH = 1755

    private class Scene(
        val name: String,
        val scenario: SampleForecast.Scenario,
        val epoch: Long,
        val change: (SkyParams) -> SkyParams = { it },
        val bolt: Float = 0f,
        val time: Float = 12.3f,
    )

    private fun render(scene: Scene): Bitmap {
        val forecast = SampleForecast.create(scene.scenario, nowEpochSeconds = scene.epoch)
        val moment = forecast.momentAt(scene.epoch)
        val palette = SkyPalette.of(moment.sun.elevation, moment.visual, moment.moonPhase.illumination)
        val p = scene.change(SkyParams.from(moment, palette))
        println("${scene.name}: ${moment.condition} rain=${p.rain} snow=${p.snow} fog=${p.fog} frost=${p.frost} mist=${p.condensation} wind=${p.wind} cover=${p.cloudCover}")
        val t = scene.time
        val sky = RuntimeShader(SKY_SHADER).apply {
            setFloatUniform("resolution", skyW.toFloat(), skyH.toFloat())
            setFloatUniform("time", t)
            setColorUniform("zenith", p.zenith.toArgb())
            setColorUniform("horizon", p.horizon.toArgb())
            setColorUniform("glow", p.glow.toArgb())
            setColorUniform("sunColor", p.sun.toArgb())
            setColorUniform("cloudLight", p.cloudLight.toArgb())
            setColorUniform("cloudShade", p.cloudShade.toArgb())
            val body = SkyStage.Default.at(p.bodyPath, p.bodyLift)
            setFloatUniform("sunPos", body.x, body.y)
            setFloatUniform("isSun", if (p.isSun) 1f else 0f)
            setFloatUniform("bodySize", (if (p.isSun) 0.022f else SkyStage.BODY_RADIUS) * p.bodyVisible)
            setFloatUniform("moonPhase", p.moonPhase)
            setFloatUniform("cloudCover", p.cloudCover)
            setFloatUniform("cloudDark", p.cloudDark)
            setFloatUniform("fog", p.fog)
            setFloatUniform("wind", p.wind)
            setFloatUniform("stars", p.stars)
            setFloatUniform("flash", scene.bolt * 0.7f)
            setFloatUniform("bolt", scene.bolt)
            setFloatUniform("boltSeed", 42.7f)
            setFloatUniform("boltX", 0.62f)
            setFloatUniform("tilt", 0f, 0f)
        }
        val precipitating = p.rain > 0.02f || p.snow > 0.02f
        val precip = RuntimeShader(PRECIPITATION_SHADER).apply {
            setFloatUniform("resolution", paneW.toFloat(), paneH.toFloat())
            setFloatUniform("time", t)
            setFloatUniform("rain", p.rain)
            setFloatUniform("snow", p.snow)
            setFloatUniform("wind", p.wind)
            setFloatUniform("tilt", 0f, 0f)
            setColorUniform("tint", lerp(p.horizon, p.cloudLight, 0.5f).toArgb())
        }
        val window = RuntimeShader(WINDOW_SHADER).apply {
            setInputShader("wipe", LinearGradient(0f, 0f, 1f, 1f, 0, 0, Shader.TileMode.CLAMP))
            setFloatUniform("resolution", paneW.toFloat(), paneH.toFloat())
            setFloatUniform("time", t)
            setFloatUniform("drops", (p.rain * 1.1f).coerceAtMost(1f))
            setFloatUniform("frost", p.frost)
            setFloatUniform("fogged", p.condensation)
            setFloatUniform("ripple", 0f, 0f, 0f, 0f)
        }
        compose.setContent {
            val skyLayer = rememberGraphicsLayer()
            val pane = rememberGraphicsLayer()
            Canvas(Modifier.size(paneW.dp, paneH.dp)) {
                skyLayer.record(IntSize(skyW, skyH)) { drawRect(ShaderBrush(sky)) }
                pane.renderEffect = RenderEffect.createRuntimeShaderEffect(window, "content").asComposeRenderEffect()
                pane.record(IntSize(paneW, paneH)) {
                    scale(paneW.toFloat() / skyW, paneW.toFloat() / skyW, pivot = Offset.Zero) { drawLayer(skyLayer) }
                    if (precipitating) drawRect(ShaderBrush(precip))
                }
                drawLayer(pane)
            }
        }
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/weather").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val scenes = listOf(
        // 17:30 in Moscow, light rain: the moment of the screenshot this was tuned against.
        Scene(
            "rain", SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L,
            { it.copy(rain = 0.45f, cloudCover = 0.9f, cloudDark = 0.45f, fog = 0.15f, condensation = 0.25f) },
        ),
        Scene(
            "downpour", SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L,
            { it.copy(rain = 1f, wind = 0.6f, cloudCover = 1f, cloudDark = 0.65f, fog = 0.2f, condensation = 0.25f) },
        ),
        Scene("storm", SampleForecast.Scenario.StormyWarm, 1_758_637_800L, bolt = 0.9f),
        Scene("snow", SampleForecast.Scenario.SnowyCold, 1_758_610_800L),
        Scene("fog", SampleForecast.Scenario.FoggyMorning, 1_758_600_000L),
        Scene("frost", SampleForecast.Scenario.SnowyCold, 1_758_610_800L, { it.copy(frost = 0.85f, snow = 0.3f) }),
    )

    @Test fun rain() = scenes[0].let { save("weather-${it.name}", render(it)) }

    @Test fun downpour() = scenes[1].let { save("weather-${it.name}", render(it)) }

    @Test fun storm() = scenes[2].let { save("weather-${it.name}", render(it)) }

    @Test fun snow() = scenes[3].let { save("weather-${it.name}", render(it)) }

    @Test fun fog() = scenes[4].let { save("weather-${it.name}", render(it)) }

    @Test fun frost() = scenes[5].let { save("weather-${it.name}", render(it)) }
}
