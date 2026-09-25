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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.GlassSegmented
import app.rosa.weather.core.designsystem.component.GlassSlider
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.component.LocalBackdrop
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.theme.RosaColors
import app.rosa.weather.core.designsystem.theme.RosaTheme
import java.io.File
import kotlin.random.Random
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The glass laboratory: every material and control over the backdrops the app really shows —
 * a bright day, a night, a sunset, busy detail — side by side, to `build/glass-lab/`. Optics are
 * tuned against this sheet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1500dp-h780dp-xhdpi")
class GlassLabTest {
    @get:Rule val compose = createComposeRule()

    private enum class Scene(val light: Boolean) { Day(true), Night(false), Sunset(false), Detail(true) }

    @Test
    fun lab() {
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Scene.entries.forEach { scene -> Panel(scene, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/glass-lab").apply { mkdirs() }
        File(dir, "lab.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** One moment of the scene's light, as the sky would publish it. */
    private class Light(
        val name: String,
        val light: Boolean,
        val sky: List<Color>,
        val sun: Offset?,
        val sunColor: Color = Color.White,
        val power: Float = 0f,
        val flash: Float = 0f,
        val frost: Float = 0f,
        val state: (GlassState) -> Unit = {},
    )

    /**
     * Glass 2.0 is lit by the scene: the same panes under a noon sun, a low gold one, the moon,
     * cloud, a lightning flash, frost — and pressed, rippling and materialising.
     */
    @Test
    fun dynamic() {
        val lights = listOf(
            Light("noon", false, listOf(Color(0xFF2F7FEA), Color(0xFF8CC8FF)), Offset(0.82f, 0.08f), Color(0xFFFFFBF0), 1f),
            Light("golden", false, listOf(Color(0xFF3A3F7A), Color(0xFFE0786A), Color(0xFFFFB36B)), Offset(0.06f, 0.55f), Color(0xFFFFB35C), 0.9f),
            Light("moon", false, listOf(Color(0xFF070B1E), Color(0xFF22305A)), Offset(0.8f, 0.1f), Color(0xFFD3DCF0), 0.45f),
            Light("overcast", true, listOf(Color(0xFF9AA6B8), Color(0xFFC7CED9)), null),
            Light("lightning", false, listOf(Color(0xFF1A1F33), Color(0xFF3B4260)), null, flash = 0.85f),
            Light("frost", true, listOf(Color(0xFFB9D3F0), Color(0xFFE6F0FA)), Offset(0.75f, 0.12f), Color(0xFFFFFFFF), 0.6f, frost = 0.85f),
            Light("touch", false, listOf(Color(0xFF2F7FEA), Color(0xFF8CC8FF)), Offset(0.82f, 0.08f), Color(0xFFFFFBF0), 1f, state = {
                it.touch = Offset(260f, 150f)
                it.touchStrength = 1f
            }),
            Light("materialise", false, listOf(Color(0xFF2F7FEA), Color(0xFF8CC8FF)), Offset(0.82f, 0.08f), Color(0xFFFFFBF0), 1f, state = { it.materialize = 0.5f }),
        )
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lights.chunked(4).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { l -> LitPanel(l, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(File("build/glass-lab").apply { mkdirs() }, "dynamic.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun LitPanel(l: Light, modifier: Modifier) {
        val backdrop = rememberBackdrop()
        val colors = colorsFor(l.light)
        val environment = remember { GlassEnvironment() }
        environment.tint = colors.glassTint
        environment.flash = l.flash
        environment.frost = l.frost
        environment.lightColor = l.sunColor
        environment.lightPower = if (l.sun != null) l.power else 0f
        environment.skyColor = l.sky.first()
        val state = remember { GlassState().also(l.state) }
        RosaTheme(colors) {
            Box(modifier.onGloballyPositioned { c ->
                val sun = l.sun ?: return@onGloballyPositioned
                val at = c.positionInRoot() + Offset(sun.x * c.size.width, sun.y * c.size.height)
                if (environment.lightPosition != at) environment.lightPosition = at
            }) {
                Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
                    drawRect(Brush.verticalGradient(l.sky))
                    if (l.flash > 0f) drawRect(Color.White.copy(alpha = l.flash * 0.25f))
                    l.sun?.let { sun ->
                        val c = Offset(sun.x * size.width, sun.y * size.height)
                        drawCircle(Brush.radialGradient(listOf(l.sunColor, l.sunColor.copy(alpha = 0f)), c, size.width * 0.22f), size.width * 0.22f, c)
                        drawCircle(l.sunColor, size.width * 0.035f, c)
                    }
                }
                CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassEnvironment provides environment) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(l.name, color = colors.ink, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassSurface(Modifier.width(118.dp).height(44.dp), cornerRadius = 22.dp, state = state) {
                                Text("Москва", color = colors.ink, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
                            }
                            GlassSurface(Modifier.size(48.dp), cornerRadius = 24.dp, state = state) {}
                        }
                        GlassSurface(Modifier.fillMaxWidth().height(150.dp), style = GlassStyle.Frosted, cornerRadius = 28.dp, state = state) {
                            Text("Ближайшие 48 часов", color = colors.ink, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassSurface(Modifier.width(60.dp).height(90.dp), style = GlassStyle.Lens, cornerRadius = 22.dp, shadow = false, state = state) {}
                            GlassSurface(Modifier.width(150.dp).height(90.dp), style = GlassStyle.Clear, cornerRadius = 30.dp, state = state) {}
                        }
                    }
                }
            }
        }
    }

    /** Big plain shapes on a flat backdrop: the optics alone, nothing to hide artefacts behind. */
    @Test
    fun shapes() {
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(true, false).forEach { light ->
                        val backdrop = rememberBackdrop()
                        val colors = colorsFor(light)
                        val environment = remember { GlassEnvironment() }
                        environment.tint = colors.glassTint
                        RosaTheme(colors) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
                                    drawRect(if (light) Color(0xFF5E93D8) else Color(0xFF1A2440))
                                    var x = 0f
                                    while (x < size.width) {
                                        drawRect(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Size(2f, size.height))
                                        x += 40f
                                    }
                                }
                                CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassEnvironment provides environment) {
                                    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                            GlassSurface(Modifier.size(width = 300.dp, height = 200.dp), style = GlassStyle.Lens, cornerRadius = 40.dp, shadow = false) {}
                                            GlassSurface(Modifier.size(width = 300.dp, height = 200.dp), style = GlassStyle.Frosted, cornerRadius = 30.dp) {}
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                            GlassSurface(Modifier.size(width = 260.dp, height = 60.dp), style = GlassStyle.Regular, cornerRadius = 30.dp) {}
                                            GlassSurface(Modifier.size(width = 120.dp, height = 180.dp), style = GlassStyle.Lens, cornerRadius = 44.dp, shadow = false) {}
                                            GlassSurface(Modifier.size(120.dp), style = GlassStyle.Regular, cornerRadius = 60.dp) {}
                                        }
                                        GlassSurface(Modifier.size(width = 640.dp, height = 200.dp), style = GlassStyle.Sheet, cornerRadius = 38.dp) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/glass-lab").apply { mkdirs() }
        File(dir, "shapes.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The frosted body is smooth: no facets, no seams along the shape's medial axis. Across
        // the middle of the big frosted card, neighbouring pixels differ only by grain.
        val y = ((20 + 10 + 100) * 2)
        var worst = 0
        var previous = bitmap.getPixel((20 + 10 + 320 + 30) * 2, y)
        for (x in (20 + 10 + 320 + 30) * 2 until (20 + 10 + 320 + 270) * 2) {
            val c = bitmap.getPixel(x, y)
            worst = maxOf(worst, kotlin.math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(previous)), kotlin.math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(previous)))
            previous = c
        }
        assertThat(worst).isAtMost(8)
    }

    /**
     * The optics alone, over a ruled grid and lit by a low sun: straight lines must bend and crowd
     * into the rim while the flat middle stays true; the rim mirrors what lies beside it, the lit
     * bevel carries a crisp highlight (a weaker one opposite), and a caustic falls on the far side.
     */
    @Test
    fun optics() {
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(true, false).forEach { light ->
                        val backdrop = rememberBackdrop()
                        val colors = colorsFor(light)
                        val environment = remember { GlassEnvironment() }
                        val sunColor = if (light) Color(0xFFFFF4DC) else Color(0xFFD3DCF0)
                        environment.tint = colors.glassTint
                        environment.lightColor = sunColor
                        environment.lightPower = if (light) 1f else 0.5f
                        environment.skyColor = if (light) Color(0xFF3E8EF7) else Color(0xFF0B1330)
                        RosaTheme(colors) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { c ->
                                    val at = c.positionInRoot() + Offset(c.size.width * 0.04f, c.size.height * 0.05f)
                                    if (environment.lightPosition != at) environment.lightPosition = at
                                },
                            ) {
                                Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) { grid(light, sunColor) }
                                CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassEnvironment provides environment) {
                                    Column(Modifier.fillMaxSize().padding(start = 60.dp, top = 50.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                                            GlassSurface(Modifier.size(width = 330.dp, height = 210.dp), style = GlassStyle.Lens, cornerRadius = 44.dp, shadow = false) {}
                                            GlassSurface(Modifier.size(150.dp), style = GlassStyle.Regular, cornerRadius = 75.dp) {}
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                            GlassSurface(Modifier.size(width = 240.dp, height = 56.dp), style = GlassStyle.Regular, cornerRadius = 28.dp) {
                                                Text("Москва", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
                                            }
                                            GlassSurface(Modifier.size(width = 240.dp, height = 56.dp), style = GlassStyle.Clear, cornerRadius = 28.dp) {}
                                        }
                                        GlassSurface(Modifier.size(width = 516.dp, height = 170.dp), style = GlassStyle.Frosted, cornerRadius = 30.dp) {
                                            Column(Modifier.padding(18.dp)) {
                                                Text("Ближайшие 48 часов", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                                Text("Потяните ленту времени", color = colors.inkSoft, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(File("build/glass-lab").apply { mkdirs() }, "optics.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The flat middle stays true: across the day lens, away from its bevel, every line of the
        // grid shows exactly where it lies behind the glass (3 px wide, every 48 px from x = 20).
        fun luma(x: Int, y: Int) = bitmap.getPixel(x, y).let { android.graphics.Color.red(it) + android.graphics.Color.green(it) + android.graphics.Color.blue(it) } / 3
        val y = (10 + 50 + 105) * 2
        val lines = (0..20).map { 21 + 48 * it }.filter { it in (10 + 60 + 12) * 2 + 8..(10 + 60 + 330 - 12) * 2 - 8 }
        assertThat(lines.size).isAtLeast(10)
        lines.forEach { x ->
            assertThat(luma(x, y) - maxOf(luma(x - 3, y), luma(x + 3, y))).isAtLeast(15)
        }
    }

    /**
     * The segmented control's selection moving like a drop: frames of one change of option, top to
     * bottom — it stretches toward the option, its tail catches up and overshoots, it settles.
     */
    @Test
    fun segmentedFlows() {
        var selected by mutableStateOf("Авто")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                val backdrop = rememberBackdrop()
                val colors = colorsFor(true)
                RosaTheme(colors) {
                    Box(Modifier.size(width = 460.dp, height = 84.dp).testTag("drop")) {
                        Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
                            drawRect(Brush.horizontalGradient(listOf(Color(0xFF2F7FEA), Color(0xFF8CC8FF))))
                        }
                        CompositionLocalProvider(LocalBackdrop provides backdrop) {
                            GlassSegmented(listOf("Авто", "Светлый", "Тёмный"), selected, { selected = it }, { it }, Modifier.padding(20.dp).fillMaxWidth())
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { selected = "Тёмный" }
        val frames = listOf(16L, 64, 64, 64, 80, 96, 112, 144, 192, 800).map { step ->
            compose.mainClock.advanceTimeBy(step)
            compose.onNodeWithTag("drop").captureToImage().asAndroidBitmap()
        }
        val sheet = Bitmap.createBitmap(frames[0].width, frames[0].height * frames.size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        frames.forEachIndexed { i, frame -> canvas.drawBitmap(frame, 0f, (frame.height * i).toFloat(), null) }
        File(File("build/glass-lab").apply { mkdirs() }, "drop.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A ruled sheet: a fine grid, bold bars and blocks of colour, a sun in the corner. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.grid(light: Boolean, sun: Color) {
        drawRect(if (light) Color(0xFF6FA4E6) else Color(0xFF141C38))
        val line = if (light) Color.White.copy(alpha = 0.75f) else Color(0xFFB8C6EE).copy(alpha = 0.45f)
        val step = 24.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(line, Offset(x, 0f), Size(1.5.dp.toPx(), size.height))
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawRect(line, Offset(0f, y), Size(size.width, 1.5.dp.toPx()))
            y += step
        }
        val hues = listOf(Color(0xFFE4572E), Color(0xFFFFC914), Color(0xFF17BEBB), Color(0xFF2E282A))
        hues.forEachIndexed { i, c ->
            drawRect(c, Offset(size.width * (0.08f + 0.22f * i), size.height * 0.36f), Size(10.dp.toPx(), size.height * 0.3f))
        }
        drawRect(hues[0], Offset(0f, size.height * 0.83f), Size(size.width, 8.dp.toPx()))
        drawRect(hues[2], Offset(size.width * 0.66f, size.height * 0.02f), Size(size.width * 0.12f, size.height * 0.1f))
        val at = Offset(size.width * 0.04f, size.height * 0.05f)
        drawCircle(Brush.radialGradient(listOf(sun, sun.copy(alpha = 0f)), at, size.width * 0.2f), size.width * 0.2f, at)
        drawCircle(sun, 16.dp.toPx(), at)
    }

    /** Glass never refracts glass: set into a card, a surface is a platter, not a hole to the sky. */
    @Test
    fun nestedGlassIsAPlatter() {
        compose.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                val backdrop = rememberBackdrop()
                val colors = colorsFor(false)
                RosaTheme(colors) {
                    Box(Modifier.fillMaxSize()) {
                        // A harsh checkerboard: anything that shows it unblurred is plain to see.
                        Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
                            drawRect(Color.White)
                            val cell = 12f
                            var y = 0f
                            var row = 0
                            while (y < size.height) {
                                var x = if (row % 2 == 0) 0f else cell
                                while (x < size.width) {
                                    drawRect(Color.Black, Offset(x, y), Size(cell, cell))
                                    x += cell * 2
                                }
                                y += cell
                                row++
                            }
                        }
                        CompositionLocalProvider(LocalBackdrop provides backdrop) {
                            GlassSurface(Modifier.padding(40.dp).size(400.dp), style = GlassStyle.Frosted, cornerRadius = 30.dp) {
                                GlassSurface(Modifier.padding(100.dp).size(200.dp), style = GlassStyle.Regular, cornerRadius = 30.dp) {}
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(File("build/glass-lab").apply { mkdirs() }, "nested.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Inside the nested surface (away from its rim), the frosted card shows through, evenly.
        val luma = mutableListOf<Int>()
        for (y in (40 + 100 + 40) * 2 until (40 + 100 + 160) * 2 step 3) {
            for (x in (40 + 100 + 40) * 2 until (40 + 100 + 160) * 2 step 3) {
                val c = bitmap.getPixel(x, y)
                luma += (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / 3
            }
        }
        assertThat(luma.max() - luma.min()).isAtMost(24)
    }

    @Composable
    private fun Panel(scene: Scene, modifier: Modifier) {
        val backdrop = rememberBackdrop()
        val colors = colorsFor(scene.light)
        val environment = remember { GlassEnvironment() }
        environment.tint = colors.glassTint
        RosaTheme(colors) {
            Box(modifier) {
                Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) { paint(scene) }
                CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassEnvironment provides environment) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(onClick = {}) { Text("Москва", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
                            Spacer(Modifier.weight(1f))
                            GlassSurface(cornerRadius = 24.dp) {
                                Row(Modifier.padding(horizontal = 6.dp)) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { RosaIconView(RosaIcon.Widgets, colors.ink) }
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { RosaIconView(RosaIcon.Settings, colors.ink) }
                                }
                            }
                        }
                        Text("23°", color = colors.ink, fontSize = 64.sp, fontWeight = FontWeight.Light)
                        GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 30.dp) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Ближайшие 48 часов", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("Потяните ленту времени", color = colors.inkSoft, fontSize = 12.sp)
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassSurface(Modifier.size(width = 58.dp, height = 86.dp), style = GlassStyle.Lens, cornerRadius = 22.dp, shadow = false) {
                                        Text("Сейчас", color = colors.ink, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                                        Text("23°", color = colors.ink, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                                    }
                                    listOf("14", "15", "16", "17").forEach { hour ->
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(hour, color = colors.inkSoft, fontSize = 12.sp)
                                            Spacer(Modifier.height(30.dp))
                                            Text("24°", color = colors.ink, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassSurface(style = GlassStyle.Clear, cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                                Text("Дождь ещё надолго", color = colors.ink, fontSize = 14.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            GlassIconButton(RosaIcon.Refresh, "refresh", onClick = {})
                        }
                        GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 28.dp, contentPadding = PaddingValues(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassSegmented(listOf("Авто", "Светлый", "Тёмный"), "Светлый", {}, { it })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Живая погода", color = colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    GlassToggle(true, {})
                                }
                                GlassSlider(0.62f, {})
                            }
                        }
                        GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Sheet, cornerRadius = 34.dp, contentPadding = PaddingValues(18.dp)) {
                            Text("Разрешить геолокацию", color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassSurface(Modifier.width(92.dp).height(40.dp), style = GlassStyle.Regular, cornerRadius = 20.dp) {}
                            GlassSurface(Modifier.width(92.dp).height(40.dp), style = GlassStyle.Clear, cornerRadius = 20.dp) {}
                            GlassSurface(Modifier.width(92.dp).height(40.dp), style = GlassStyle.Lens, cornerRadius = 20.dp) {}
                        }
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

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.paint(scene: Scene) {
        val w = size.width
        val h = size.height
        when (scene) {
            Scene.Day -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF2F7FEA), Color(0xFF5FA6F5), Color(0xFFA9D7FF))))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0x88FFFFFF), Color(0x00FFFFFF)), Offset(w * 0.8f, h * 0.2f), w * 0.3f), w * 0.3f, Offset(w * 0.8f, h * 0.2f))
                clouds(Color.White, 0.55f)
            }
            Scene.Night -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF070B1E), Color(0xFF121C40), Color(0xFF2A3A68))))
                val rnd = Random(3)
                repeat(160) { drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.6f), 0.6f + rnd.nextFloat() * 1.6f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)) }
                drawCircle(Color(0xFFE9ECF7), w * 0.07f, Offset(w * 0.76f, h * 0.17f))
            }
            Scene.Sunset -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF232A63), Color(0xFF6B3F8C), Color(0xFFD9587A), Color(0xFFFFA65C))))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A1), Color(0x00FFB35C)), Offset(w * 0.3f, h * 0.86f), w * 0.45f), w * 0.45f, Offset(w * 0.3f, h * 0.86f))
                clouds(Color(0xFFFFC7A8), 0.35f)
            }
            Scene.Detail -> {
                drawRect(Color(0xFFF4F1EA))
                val rnd = Random(9)
                val hues = listOf(Color(0xFFE4572E), Color(0xFF17BEBB), Color(0xFFFFC914), Color(0xFF2E282A), Color(0xFF76B041), Color(0xFF3F6CDF))
                repeat(70) {
                    val c = hues[rnd.nextInt(hues.size)]
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    if (rnd.nextBoolean()) {
                        drawCircle(c, 6f + rnd.nextFloat() * 40f, Offset(x, y))
                    } else {
                        drawRect(c, Offset(x, y), Size(8f + rnd.nextFloat() * 90f, 4f + rnd.nextFloat() * 14f))
                    }
                }
                var y = 12f
                while (y < h) {
                    drawRect(Color(0x552E282A), Offset(0f, y), Size(w, 1.5f))
                    y += 26f
                }
            }
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.clouds(color: Color, alpha: Float) {
        val rnd = Random(5)
        repeat(9) {
            val cx = rnd.nextFloat() * size.width
            val cy = size.height * (0.1f + rnd.nextFloat() * 0.8f)
            val r = size.width * (0.12f + rnd.nextFloat() * 0.18f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        }
    }
}
