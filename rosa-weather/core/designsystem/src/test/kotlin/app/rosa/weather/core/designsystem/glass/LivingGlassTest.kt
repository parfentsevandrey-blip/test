package app.rosa.weather.core.designsystem.glass

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.LocalBackdrop
import app.rosa.weather.core.designsystem.motion.AmbientClock
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.theme.RosaColors
import app.rosa.weather.core.designsystem.theme.RosaTheme
import java.io.File
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The living glass: the same panes in every weather, alive — sunlight rippling through the lit
 * bevel, clouds drifting across, stars glinting in the rims, rain landing and running, snow
 * settling and melting, frost glittering, a finger's light running round the rim — to
 * `build/glass-lab/living.png`. (In motion, on the real screen: ScreenGalleryTest.homeAlive*.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1500dp-h780dp-hdpi")
class LivingGlassTest {
    @get:Rule val compose = createComposeRule()

    private class Weather(
        val name: String,
        val light: Boolean,
        val sky: List<Color>,
        val sun: Offset?,
        val sunColor: Color = Color.White,
        val power: Float = 0f,
        val stars: Float = 0f,
        val clouds: Float = 0f,
        val rain: Float = 0f,
        val snow: Float = 0f,
        val frost: Float = 0f,
        val wind: Float = 0.2f,
    )

    private val weathers = listOf(
        Weather("Солнце", false, listOf(Color(0xFF2F7FEA), Color(0xFF8CC8FF)), Offset(0.82f, 0.08f), Color(0xFFFFFBF0), 1f, clouds = 0.1f),
        Weather("Закат", false, listOf(Color(0xFF3A3F7A), Color(0xFFE0786A), Color(0xFFFFB36B)), Offset(0.06f, 0.62f), Color(0xFFFFB35C), 0.9f, clouds = 0.3f),
        Weather("Ночь", false, listOf(Color(0xFF070B1E), Color(0xFF22305A)), Offset(0.8f, 0.1f), Color(0xFFD3DCF0), 0.45f, stars = 1f),
        Weather("Облачно", true, listOf(Color(0xFF9AA6B8), Color(0xFFC7CED9)), null, clouds = 1f, wind = 0.5f),
        Weather("Дождь", false, listOf(Color(0xFF3B4658), Color(0xFF6B7788)), null, clouds = 1f, rain = 0.75f, wind = 0.3f),
        Weather("Снег", true, listOf(Color(0xFFAFBBCB), Color(0xFFDDE3EC)), null, clouds = 1f, snow = 0.8f),
        Weather("Мороз", true, listOf(Color(0xFFB9D3F0), Color(0xFFE6F0FA)), Offset(0.75f, 0.12f), Color(0xFFFFFFFF), 0.6f, frost = 0.85f),
        Weather("Касание", false, listOf(Color(0xFF2F7FEA), Color(0xFF8CC8FF)), Offset(0.82f, 0.08f), Color(0xFFFFFBF0), 1f, clouds = 0.1f),
    )

    /** Every weather at one moment, alive. */
    @Test
    fun livingSheet() {
        val clock = AmbientClock()
        clock.time.floatValue = 4.3f
        val touch = GlassState().apply {
            touch = Offset(300f, 60f)
            touchStrength = 0.9f
            lens = 0.9f
            spread = 0.55f
        }
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weathers.chunked(4).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { w ->
                                LivingPanel(w, clock, if (w.name == "Касание") touch else null, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "living.png")
    }

    /** Four of them, larger, quicker to render: for tuning. */
    @Test
    @Config(qualifiers = "w1240dp-h470dp-xhdpi")
    fun livingPreview() {
        val clock = AmbientClock()
        clock.time.floatValue = 4.3f
        val touch = GlassState().apply {
            touch = Offset(300f, 60f)
            touchStrength = 0.9f
            lens = 0.9f
            spread = 0.55f
        }
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Солнце", "Ночь", "Дождь", "Касание").forEach { name ->
                        LivingPanel(weathers.first { it.name == name }, clock, if (name == "Касание") touch else null, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "living-preview.png")
    }

    @Composable
    private fun LivingPanel(w: Weather, clock: AmbientClock, state: GlassState?, modifier: Modifier) {
        val backdrop = rememberBackdrop()
        val colors = colorsFor(w.light)
        val environment = remember { GlassEnvironment() }
        environment.tint = colors.glassTint
        environment.lightColor = w.sunColor
        environment.lightPower = if (w.sun != null) w.power else 0f
        environment.skyColor = w.sky.first()
        environment.stars = w.stars
        environment.clouds = w.clouds
        environment.rain = w.rain
        environment.snow = w.snow
        environment.frost = w.frost
        environment.wind = w.wind
        environment.alive = 1f
        environment.clock = clock
        RosaTheme(colors) {
            Box(
                modifier.onGloballyPositioned { c ->
                    val sun = w.sun ?: return@onGloballyPositioned
                    val at = c.positionInRoot() + Offset(sun.x * c.size.width, sun.y * c.size.height)
                    if (environment.lightPosition != at) environment.lightPosition = at
                },
            ) {
                Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
                    drawRect(Brush.verticalGradient(w.sky))
                    w.sun?.let { sun ->
                        val c = Offset(sun.x * size.width, sun.y * size.height)
                        drawCircle(Brush.radialGradient(listOf(w.sunColor, w.sunColor.copy(alpha = 0f)), c, size.width * 0.22f), size.width * 0.22f, c)
                        drawCircle(w.sunColor, size.width * 0.035f, c)
                    }
                    val rnd = Random(5)
                    repeat(if (w.stars > 0f) 0 else 7) {
                        val cx = rnd.nextFloat() * size.width
                        val cy = size.height * (0.1f + rnd.nextFloat() * 0.8f)
                        val r = size.width * (0.12f + rnd.nextFloat() * 0.18f)
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
                    }
                    if (w.stars > 0f) {
                        repeat(120) { drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.6f), 0.6f + rnd.nextFloat() * 1.4f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)) }
                    }
                }
                CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassEnvironment provides environment) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(w.name, color = colors.ink, fontSize = 13.sp)
                        GlassSurface(Modifier.width(128.dp).height(44.dp), cornerRadius = 22.dp) {
                            Text("Москва", color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
                        }
                        GlassSurface(Modifier.fillMaxWidth().height(178.dp), style = GlassStyle.Frosted, cornerRadius = 28.dp, state = state ?: remember { GlassState() }) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Ближайшие 48 часов", color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Потяните ленту времени", color = colors.inkSoft, fontSize = 11.sp)
                                Spacer(Modifier.height(14.dp))
                                Row {
                                    listOf("14" to "24°", "15" to "24°", "16" to "23°", "17" to "22°").forEach { (hour, temp) ->
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(hour, color = colors.inkSoft, fontSize = 12.sp)
                                            Spacer(Modifier.height(26.dp))
                                            Text(temp, color = colors.ink, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassSurface(Modifier.width(62.dp).height(90.dp), style = GlassStyle.Lens, cornerRadius = 22.dp, shadow = false) {}
                            GlassSurface(style = GlassStyle.Clear, cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                                Text("Сейчас", color = colors.ink, fontSize = 14.sp)
                            }
                        }
                        GlassSurface(Modifier.fillMaxWidth().height(120.dp), style = GlassStyle.Frosted, cornerRadius = 28.dp) {
                            Text("Прогноз на 10 дней", color = colors.ink, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                        }
                        Box(Modifier.size(1.dp))
                    }
                }
            }
        }
    }

    private fun colorsFor(light: Boolean): RosaColors {
        val ink = if (light) Color(0xFF14233B) else Color.White
        return RosaColors(
            ink = ink,
            inkSoft = ink.copy(alpha = 0.72f),
            inkFaint = ink.copy(alpha = 0.45f),
            accent = if (light) Color(0xFF2F7BE0) else Color(0xFFFFD37A),
            warm = Color(0xFFFFB26B),
            cool = Color(0xFF7FB6FF),
            rain = if (light) Color(0xFF2F7BE0) else Color(0xFF8CCBFF),
            glassTint = if (light) Color.White else Color(0xFF0B1020),
            fill = ink.copy(alpha = if (light) 0.07f else 0.1f),
            isLightSky = light,
            zenith = Color(0xFF3E8EF7),
            horizon = Color(0xFF9CD2FF),
        )
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/glass-lab", name)
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
