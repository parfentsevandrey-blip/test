package app.papersky.weather.widget.ui

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import app.papersky.weather.R
import app.papersky.weather.scene.ColorMath
import app.papersky.weather.scene.PaperSceneRenderer
import app.papersky.weather.scene.SceneState
import app.papersky.weather.scene.ScenePalette
import app.papersky.weather.widget.layout.RectDp
import app.papersky.weather.widget.layout.WidgetPlan
import kotlin.math.abs
import kotlin.math.max

/**
 * One animated weather layer on the home screen: an indeterminate ProgressBar looping an
 * AnimatedVectorDrawable (see tools/widget_fx.py). The launcher plays it on its render thread, so
 * rain keeps falling without the app waking up.
 *
 * [region] is where the layer may draw (dp, widget coordinates); [cx], [cy] (dp, relative to the
 * region) is the centre of its square of side [side].
 */
data class FxLayer(
    val layout: Int,
    val tint: Int,
    val mirrored: Boolean,
    val region: RectDp,
    val cx: Float,
    val cy: Float,
    val side: Float,
)

/** Which moving layers a scene gets, and which static parts the background bitmap must leave out. */
class FxPlan(val layers: List<FxLayer>) {
    val hasPrecipitation: Boolean get() = layers.any { it.layout in PRECIP }
    val hasRays: Boolean get() = layers.any { it.layout == R.layout.rv_wfx_rays }

    companion object {
        val None = FxPlan(emptyList())
        private val PRECIP = setOf(
            R.layout.rv_wfx_rain1_calm_s, R.layout.rv_wfx_rain1_calm_l, R.layout.rv_wfx_rain1_windy_s, R.layout.rv_wfx_rain1_windy_l,
            R.layout.rv_wfx_rain2_calm_s, R.layout.rv_wfx_rain2_calm_l, R.layout.rv_wfx_rain2_windy_s, R.layout.rv_wfx_rain2_windy_l,
            R.layout.rv_wfx_rain3_calm_s, R.layout.rv_wfx_rain3_calm_l, R.layout.rv_wfx_rain3_windy_s, R.layout.rv_wfx_rain3_windy_l,
            R.layout.rv_wfx_snow1_s, R.layout.rv_wfx_snow1_l, R.layout.rv_wfx_snow2_s, R.layout.rv_wfx_snow2_l,
            R.layout.rv_wfx_snow3_s, R.layout.rv_wfx_snow3_l, R.layout.rv_wfx_drizzle_s, R.layout.rv_wfx_drizzle_l,
            R.layout.rv_wfx_hail_s, R.layout.rv_wfx_hail_l, R.layout.rv_wfx_sleet_s, R.layout.rv_wfx_sleet_l,
            R.layout.rv_wfx_stars_s, R.layout.rv_wfx_stars_l, R.layout.rv_wfx_motes_s, R.layout.rv_wfx_motes_l,
        )
    }
}

object WidgetFx {
    private val RAIN = arrayOf(
        intArrayOf(R.layout.rv_wfx_rain1_calm_s, R.layout.rv_wfx_rain1_calm_l, R.layout.rv_wfx_rain1_windy_s, R.layout.rv_wfx_rain1_windy_l),
        intArrayOf(R.layout.rv_wfx_rain2_calm_s, R.layout.rv_wfx_rain2_calm_l, R.layout.rv_wfx_rain2_windy_s, R.layout.rv_wfx_rain2_windy_l),
        intArrayOf(R.layout.rv_wfx_rain3_calm_s, R.layout.rv_wfx_rain3_calm_l, R.layout.rv_wfx_rain3_windy_s, R.layout.rv_wfx_rain3_windy_l),
    )
    private val SNOW = arrayOf(
        intArrayOf(R.layout.rv_wfx_snow1_s, R.layout.rv_wfx_snow1_l),
        intArrayOf(R.layout.rv_wfx_snow2_s, R.layout.rv_wfx_snow2_l),
        intArrayOf(R.layout.rv_wfx_snow3_s, R.layout.rv_wfx_snow3_l),
    )

    /** The open sky of a widget: everything not covered by vellum panels. */
    fun skyRegion(plan: WidgetPlan): RectDp {
        val panels = plan.panels
        if (panels.isEmpty()) return RectDp(0f, 0f, plan.width, plan.height)
        // Either the band above the panels, or the column left of them — whichever is bigger.
        val above = RectDp(0f, 0f, plan.width, panels.minOf { it.top } - 2f)
        val left = RectDp(0f, 0f, panels.minOf { it.left } - 2f, plan.height)
        return if (above.width * above.height >= left.width * left.height) above else left
    }

    fun plan(scene: SceneState, p: ScenePalette, plan: WidgetPlan, sun: FloatArray?): FxPlan {
        val sky = skyRegion(plan)
        if (sky.width < 24f || sky.height < 24f) return FxPlan.None
        val big = max(sky.width, sky.height)
        val large = big >= 240f
        val sz = if (large) 1 else 0
        val layers = mutableListOf<FxLayer>()
        fun inSky(layout: Int, tint: Int, mirrored: Boolean = false) {
            layers += FxLayer(layout, tint, mirrored, sky, sky.width / 2, sky.height / 2, big)
        }

        val wet = scene.rain + scene.drizzle
        val windy = abs(scene.windX) > 5f
        when {
            scene.rain > 0.08f && scene.snow > 0.1f -> inSky(if (large) R.layout.rv_wfx_sleet_l else R.layout.rv_wfx_sleet_s, p.precip)
            scene.rain > 0.05f -> {
                val level = when {
                    scene.rain < 0.35f -> 0
                    scene.rain < 0.7f -> 1
                    else -> 2
                }
                inSky(RAIN[level][(if (windy) 2 else 0) + sz], p.precip, mirrored = scene.windX < 0)
            }
            scene.snow > 0.05f -> {
                val level = when {
                    scene.snow < 0.35f -> 0
                    scene.snow < 0.75f -> 1
                    else -> 2
                }
                inSky(SNOW[level][sz], p.precip)
            }
            scene.drizzle > 0.1f -> inSky(if (large) R.layout.rv_wfx_drizzle_l else R.layout.rv_wfx_drizzle_s, p.precip)
        }
        if (scene.hail > 0.1f) inSky(if (large) R.layout.rv_wfx_hail_l else R.layout.rv_wfx_hail_s, 0xFFF4F8FF.toInt())

        val stars = (1f - scene.daylight * 1.6f).coerceIn(0f, 1f) * (1f - scene.cloudCover * 0.9f) * (1f - scene.fog)
        if (stars > 0.3f && wet < 0.05f && scene.snow < 0.05f) inSky(if (large) R.layout.rv_wfx_stars_l else R.layout.rv_wfx_stars_s, p.star)

        val clearDay = scene.daylight > 0.6f && scene.cloudCover < 0.55f && wet + scene.snow < 0.05f
        if (clearDay && scene.fog < 0.3f) inSky(if (large) R.layout.rv_wfx_motes_l else R.layout.rv_wfx_motes_s, p.sunRay)

        if (scene.fog > 0.3f) inSky(R.layout.rv_wfx_fog, ColorMath.lerp(p.cloud, p.skyBottom, 0.3f))

        val sunUnderPanel = sun != null && plan.panels.any { r ->
            sun[0] + sun[2] * 1.6f > r.left && sun[0] - sun[2] * 1.6f < r.right && sun[1] + sun[2] * 1.6f > r.top && sun[1] - sun[2] * 1.6f < r.bottom
        }
        if (sun != null && clearDay && !sunUnderPanel) {
            // The halo breathes around the painted sun: a square island centred on it.
            val side = sun[2] * 6.3f
            layers += FxLayer(R.layout.rv_wfx_rays, p.sunRay, false, RectDp(0f, 0f, plan.width, plan.height), sun[0], sun[1], side)
        }
        if (scene.thunder > 0.5f) {
            layers += FxLayer(R.layout.rv_wfx_flash, 0xFFF4F2FF.toInt(), false, RectDp(0f, 0f, plan.width, plan.height), plan.width / 2, plan.height / 2, max(plan.width, plan.height))
        }
        return FxPlan(layers.take(4))
    }

    /** Sun position [x, y, radius] in dp for the static frame, or null when it's hidden. */
    fun sunDp(scene: SceneState, plan: WidgetPlan, options: PaperSceneRenderer.Options): FloatArray? =
        PaperSceneRenderer(1f).celestialAt(plan.width, plan.height, scene, options)?.takeIf { scene.daylight > 0.5f }
}

@Composable
fun FxIsland(layer: FxLayer) {
    val context = LocalContext.current
    val half = layer.side / 2f
    val rv = RemoteViews(context.packageName, layer.layout).apply {
        setViewLayoutWidth(R.id.fx, half, TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutHeight(R.id.fx, half, TypedValue.COMPLEX_UNIT_DIP)
        // The bar is scaled 2× about its centre, so offset it by half its own size.
        setViewLayoutMargin(R.id.fx, RemoteViews.MARGIN_LEFT, layer.cx - half / 2f, TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutMargin(R.id.fx, RemoteViews.MARGIN_TOP, layer.cy - half / 2f, TypedValue.COMPLEX_UNIT_DIP)
        setColorStateList(R.id.fx, "setIndeterminateTintList", ColorStateList.valueOf(layer.tint))
        if (layer.mirrored) setInt(R.id.fx, "setLayoutDirection", View.LAYOUT_DIRECTION_RTL)
    }
    val r = layer.region
    Box(GlanceModifier.fillMaxSize().padding(start = r.left.dp, top = r.top.dp)) {
        AndroidRemoteViews(rv, GlanceModifier.size(r.width.coerceAtLeast(1f).dp, r.height.coerceAtLeast(1f).dp))
    }
}
