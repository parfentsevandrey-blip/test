package app.rosa.weather.core.designsystem.sky

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the weather shaders — sky, precipitation and the window pane, layered as [SkyScene]
 * layers them — for moments the app can't be caught in on demand (a lightning strike), to
 * `build/weather/`.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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
        val bolt = BoltState().apply {
            visible = scene.bolt > 0f
            seed = 42.7f
            x = 0.62f
            channel = if (visible) LightningChannel(7, x, 0.72f) else null
            flashAt = Offset(0.62f, 0.12f)
        }
        val flash = scene.bolt * 0.7f
        val sky = RuntimeShader(SKY_SHADER).apply {
            setSkyUniforms(p, SkyStage.Default.at(p.bodyPath, p.bodyLift), skyW, skyH, t, flash, bolt, Offset.Zero)
        }
        val precipitating = p.rain > 0.02f || p.snow > 0.02f
        val precip = RuntimeShader(PRECIPITATION_SHADER).apply {
            setPrecipitationUniforms(p, paneW, paneH, t, flash, Offset.Zero)
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
                bolt.channel?.let { drawLightning(it, flash) }
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
        // 18:10 in Moscow in late September: the sun a few degrees up, in broken cloud.
        Scene("golden", SampleForecast.Scenario.SunnyMild, 1_758_640_200L, { it.copy(cloudCover = 0.4f, cloudDark = 0.1f) }),
        // A shower passing at 17:00, the sun low behind you.
        Scene("rainbow", SampleForecast.Scenario.RainyAfternoon, 1_758_636_000L, { it.copy(rain = 0.3f, cloudCover = 0.55f, cloudDark = 0.25f, rainbow = 1f, condensation = 0f, fog = 0f) }),
        // A clear moonless night: the Milky Way, and a meteor mid-flight.
        Scene("night", SampleForecast.Scenario.ClearNight, 1_758_664_800L, { it.copy(stars = 1f, bodyVisible = 0f, cloudCover = 0.05f) }, time = 7.0f * 17 + 0.45f),
        Scene("moonlit", SampleForecast.Scenario.ClearNight, 1_758_664_800L, { it.copy(cloudCover = 0.45f, cloudDark = 0.1f, moonPhase = 0.5f, isSun = false, bodyVisible = 1f, bodyPath = 0.75f, bodyLift = 0.6f, stars = 0.6f) }),
    )

    @Test fun rain() = scenes[0].let { save("weather-${it.name}", render(it)) }

    @Test fun downpour() = scenes[1].let { save("weather-${it.name}", render(it)) }

    @Test fun storm() = scenes[2].let { save("weather-${it.name}", render(it)) }

    @Test fun snow() = scenes[3].let { save("weather-${it.name}", render(it)) }

    @Test fun fog() = scenes[4].let { save("weather-${it.name}", render(it)) }

    @Test fun frost() = scenes[5].let { save("weather-${it.name}", render(it)) }

    @Test fun golden() = scenes[6].let { save("weather-${it.name}", render(it)) }

    @Test fun rainbow() = scenes[7].let { save("weather-${it.name}", render(it)) }

    @Test fun night() = scenes[8].let { save("weather-${it.name}", render(it)) }

    @Test fun moonlit() = scenes[9].let { save("weather-${it.name}", render(it)) }

    /**
     * The README's strip of the sky's new light — golden hour, a rainbow after a shower, the Milky
     * Way with a meteor, a storm's channel, a downpour — from this run's renders (the tests run in
     * name order, this one last). Written only with `-Prosa.docs`.
     */
    @Test
    fun zCinematicSheet() {
        val dir = System.getProperty("rosa.docs") ?: return
        val names = listOf("golden", "rainbow", "night", "storm", "downpour")
        val shots = names.mapNotNull { n -> File("build/weather/weather-$n.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
        if (shots.size != names.size) return
        val tileW = 324
        val tileH = 702
        val gap = 10
        val sheet = createBitmap(tileW * shots.size + gap * (shots.size - 1), tileH)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(android.graphics.Color.rgb(20, 26, 58))
        shots.forEachIndexed { i, shot -> canvas.drawBitmap(shot.scale(tileW, tileH), (i * (tileW + gap)).toFloat(), 0f, null) }
        File(dir).mkdirs()
        File(dir, "weather-cinematic.jpg").outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    }
}
