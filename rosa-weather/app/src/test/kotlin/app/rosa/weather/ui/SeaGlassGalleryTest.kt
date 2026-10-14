package app.rosa.weather.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.GlassSegmented
import app.rosa.weather.core.designsystem.component.GlassSlider
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.seaglass.SeaTone
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.SkyController
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sea glass design's pieces on its canvas, to compare with the design's references. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SeaGlassGalleryTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String): Bitmap {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screens").apply { mkdirs() }.let { File(it, "$name.png").outputStream().use { o -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, o) } }
        return bitmap
    }

    /**
     * A press, frame by frame: the cyan button sinks (its shadow draws in, its glass deepens), the
     * milky round button turns into a cyan squircle, the toggle's pebble swells; let go, all of it
     * comes back. 40 ms apart, to `build/screens/sea-press/`.
     */
    @Test
    fun pressFrames() {
        val settings = AppSettings(appearance = Appearance.SeaGlass, effects = EffectsQuality.Balanced)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember {
                SkyController(SampleForecast.create(SampleForecast.Scenario.SunnyMild).momentAt(1_758_628_800L)).apply { applyAppearance(Appearance.SeaGlass) }
            }
            RosaEnvironment(settings, sky.palette, richGlass = true) {
                SkyBackdrop(sky.params, settings.effects, transitionMillis = 0) {
                    Row(Modifier.padding(start = 24.dp, top = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        GlassButton({}, tone = SeaTone.Cyan, cornerRadius = 26.dp, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)) {
                            Text("Обновить", style = Rosa.type.headline, color = Rosa.colors.ink)
                        }
                        GlassIconButton(RosaIcon.Settings, "Настройки", {})
                        GlassIconButton(RosaIcon.Search, "Поиск", {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        val dir = File("build/screens/sea-press").apply { deleteRecursively(); mkdirs() }
        var i = 0
        fun frame() {
            compose.waitForIdle()
            val b = compose.onRoot().captureToImage().asAndroidBitmap()
            val crop = Bitmap.createBitmap(b, 0, 0, b.width, 120 * 3)
            File(dir, "%03d.png".format(i++)).outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val button = androidx.compose.ui.geometry.Offset(90f * 3, 58f * 3)
        val icon = androidx.compose.ui.geometry.Offset(222f * 3, 58f * 3)
        repeat(3) { compose.mainClock.advanceTimeBy(40); frame() }
        compose.onRoot().performTouchInput { down(0, button); down(1, icon) }
        repeat(12) { compose.mainClock.advanceTimeBy(40); frame() }
        compose.onRoot().performTouchInput { up(0); up(1) }
        repeat(18) { compose.mainClock.advanceTimeBy(40); frame() }
    }

    @Test
    fun kit() {
        val settings = AppSettings(appearance = Appearance.SeaGlass, effects = EffectsQuality.Balanced)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember {
                SkyController(SampleForecast.create(SampleForecast.Scenario.SunnyMild).momentAt(1_758_628_800L)).apply { applyAppearance(Appearance.SeaGlass) }
            }
            RosaEnvironment(settings, sky.palette, richGlass = true) {
                SkyBackdrop(sky.params, settings.effects, transitionMillis = 0) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            GlassButton({}, tone = SeaTone.Rose, cornerRadius = 30.dp, contentPadding = PaddingValues(18.dp)) {
                                RosaIconView(RosaIcon.Plus, Rosa.colors.ink, size = 24.dp)
                            }
                            GlassButton({}, tone = SeaTone.Cyan, cornerRadius = 26.dp, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RosaIconView(RosaIcon.Search, Rosa.colors.ink, size = 22.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Compose", style = Rosa.type.headline, color = Rosa.colors.ink)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            GlassToggle(true, {})
                            GlassToggle(false, {})
                            GlassIconButton(RosaIcon.Settings, "Настройки", {})
                            GlassIconButton(RosaIcon.Search, "Поиск", {})
                        }
                        GlassSlider(0.35f, {})
                        GlassSegmented(listOf("Фото", "Коллекции", "Создать"), "Фото", {}, { it })
                        GlassSurface(Modifier.fillMaxWidth().height(120.dp), style = GlassStyle.Frosted, cornerRadius = 32.dp, tone = SeaTone.Mint, contentPadding = PaddingValues(22.dp)) {
                            Text("18°  Ясно", style = Rosa.type.display, color = Rosa.colors.ink)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf(SeaTone.Lilac, SeaTone.Peach, SeaTone.Periwinkle, SeaTone.Milk).forEach { tone ->
                                GlassSurface(Modifier.size(78.dp), style = GlassStyle.Frosted, cornerRadius = 39.dp, tone = tone) {}
                            }
                        }
                    }
                }
            }
        }
        capture("sea-kit")
    }
}
