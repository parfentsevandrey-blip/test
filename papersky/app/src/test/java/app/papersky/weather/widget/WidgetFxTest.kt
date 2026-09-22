package app.papersky.weather.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.R
import app.papersky.weather.TestApp
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import app.papersky.weather.widget.layout.PlanInput
import app.papersky.weather.widget.layout.WidgetLayoutPlanner
import app.papersky.weather.widget.ui.WidgetFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], application = TestApp::class)
class WidgetFxTest {

    private fun plan(w: Float, h: Float) = WidgetLayoutPlanner.plan(PlanInput(width = w, height = h, config = WidgetConfig()))

    @Test
    fun rainFallsOnlyOnOpenSky() {
        val p = plan(292f, 192f)
        assertTrue("4×2 should carry a panel", p.panels.isNotEmpty())
        val scene = SceneState(rain = 0.6f, cloudCover = 1f, windX = 2f)
        val fx = WidgetFx.plan(scene, Palettes.forState(scene), p, sun = null)
        val rain = fx.layers.single { it.layout == R.layout.rv_wfx_rain2_calm_l }
        assertTrue("rain must stop above the panels", rain.region.bottom <= p.panels.minOf { it.top })
        assertEquals(Palettes.forState(scene).precip, rain.tint)
        assertTrue(fx.hasPrecipitation)
    }

    @Test
    fun windFromTheEastMirrorsTheSlant() {
        val scene = SceneState(rain = 0.9f, cloudCover = 1f, windX = -8f)
        val fx = WidgetFx.plan(scene, Palettes.forState(scene), plan(292f, 192f), sun = null)
        val rain = fx.layers.first()
        assertEquals(R.layout.rv_wfx_rain3_windy_l, rain.layout)
        assertTrue(rain.mirrored)
    }

    @Test
    fun smallWidgetsUseTheSmallSizeClass() {
        val scene = SceneState(snow = 0.5f, cloudCover = 0.9f)
        val fx = WidgetFx.plan(scene, Palettes.forState(scene), plan(140f, 180f), sun = null)
        assertEquals(R.layout.rv_wfx_snow2_s, fx.layers.first().layout)
    }

    @Test
    fun clearNightTwinklesAndStormsFlash() {
        val night = SceneState(daylight = 0f, cloudCover = 0.05f)
        assertTrue(WidgetFx.plan(night, Palettes.forState(night), plan(292f, 192f), null).layers.any { it.layout == R.layout.rv_wfx_stars_l })

        val storm = SceneState(daylight = 0.2f, rain = 0.8f, thunder = 1f, cloudCover = 1f)
        val fx = WidgetFx.plan(storm, Palettes.forState(storm), plan(292f, 192f), null)
        assertTrue(fx.layers.any { it.layout == R.layout.rv_wfx_flash })
        assertTrue("no stars behind storm clouds", fx.layers.none { it.layout == R.layout.rv_wfx_stars_l })
    }

    @Test
    fun sunnyDayGetsRaysOnTheSun() {
        val day = SceneState(daylight = 1f, sunProgress = 0.4f, cloudCover = 0.1f)
        val fx = WidgetFx.plan(day, Palettes.forState(day), plan(292f, 192f), sun = floatArrayOf(200f, 40f, 14f))
        val rays = fx.layers.single { it.layout == R.layout.rv_wfx_rays }
        assertEquals(200f, rays.cx)
        assertEquals(40f, rays.cy)
        assertTrue(fx.hasRays)
    }

    @Test
    fun skyRegionPicksTheLargerOpenArea() {
        // Panels down the right-hand side (panorama): the sky is the left column.
        val p = plan(500f, 200f)
        val sky = WidgetFx.skyRegion(p)
        if (p.panels.isNotEmpty()) {
            assertTrue(sky.width * sky.height > 0f)
            p.panels.forEach { panel ->
                val overlaps = sky.left < panel.right && sky.right > panel.left && sky.top < panel.bottom && sky.bottom > panel.top
                assertTrue("sky region overlaps a panel", !overlaps)
            }
        }
    }
}
