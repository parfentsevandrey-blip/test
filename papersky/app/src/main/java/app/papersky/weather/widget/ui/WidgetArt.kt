package app.papersky.weather.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import app.papersky.weather.scene.ColorMath
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import app.papersky.weather.scene.MaterialTextures
import app.papersky.weather.scene.PaperSceneRenderer
import app.papersky.weather.scene.ScenePalette
import app.papersky.weather.scene.SceneState
import app.papersky.weather.widget.WidgetBackground
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.layout.Mode
import app.papersky.weather.widget.layout.RectDp
import app.papersky.weather.widget.layout.WidgetPlan
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Temperature curve for an hourly panel, in dp relative to the widget. */
class ChartSpec(val left: Float, val top: Float, val width: Float, val height: Float, val pointsY: FloatArray, val bars: FloatArray?, val barsTop: Float)

/**
 * Bitmaps behind widget text. Size-aware: rendered for the exact widget size but capped so the
 * RemoteViews bitmap budget (≈1.5 × screen) is never approached even with both orientations.
 */
object WidgetArt {
    private const val MAX_SCENE_PIXELS = 480_000

    private val glyphs = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun glyph(context: Context, glyph: Glyph, sizeDp: Float, colors: GlyphColors, rotation: Float = 0f): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * min(density, 2.75f)).roundToInt().coerceIn(8, 220)
        val key = "${glyph.name}|$px|${colors.hashCode()}|${rotation.roundToInt()}"
        return glyphs.get(key) ?: GlyphRenderer.bitmap(glyph, px, colors, rotation).also { glyphs.put(key, it) }
    }

    /** Options for the static frame, in dp (scale with [scaled]). */
    fun sceneOptions(plan: WidgetPlan, scene: SceneState, clockSeconds: Long, village: Boolean = true): PaperSceneRenderer.Options {
        val textOnLeft = plan.mode == Mode.Card || plan.mode == Mode.Panorama || plan.mode == Mode.Strip
        return PaperSceneRenderer.Options(
            // A slowly advancing "time" so clouds sit somewhere new after each refresh.
            time = ((clockSeconds / 60) % 997).toFloat() * 3.1f,
            detail = if (plan.mode == Mode.Micro) 0.6f else 1f,
            vignette = 0.5f,
            landscape = plan.height >= 40,
            grain = 0.85f,
            laneStart = if (textOnLeft) 0.56f else 0.12f,
            laneEnd = if (textOnLeft) 0.93f else 0.88f,
            staticBolt = scene.thunder > 0.5f,
            village = village,
        )
    }

    fun background(
        context: Context,
        plan: WidgetPlan,
        config: WidgetConfig,
        scene: SceneState,
        palette: ScenePalette,
        charts: List<ChartSpec>,
        clockSeconds: Long,
        fx: FxPlan = FxPlan.None,
        village: Boolean = true,
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        val scale = PaperSceneRenderer.scaleFor(plan.width, plan.height, density, MAX_SCENE_PIXELS)
        return when (config.background) {
            WidgetBackground.Clear -> null
            WidgetBackground.Paper -> paper(plan, palette, scale, scene.seed)
            WidgetBackground.Scene -> PaperSceneRenderer.renderBitmap(plan.width, plan.height, scale, scene, palette) { k ->
                fun RectDp.px() = RectF(left * k, top * k, right * k, bottom * k)
                val base = sceneOptions(plan, scene, clockSeconds, village)
                base.copy(
                    panels = plan.panels.map { r -> PaperSceneRenderer.Panel(r.px()) },
                    charts = charts.map { c ->
                        PaperSceneRenderer.Chart(
                            RectF(c.left * k, c.top * k, (c.left + c.width) * k, (c.top + c.height) * k),
                            FloatArray(c.pointsY.size) { c.pointsY[it] * k },
                            c.bars,
                            c.barsTop * k,
                        )
                    },
                    // What the launcher animates must not also be painted still.
                    particles = fx.layers.isEmpty(),
                    rays = !fx.hasRays,
                    staticBolt = base.staticBolt && fx.layers.none { it.layout == app.papersky.weather.R.layout.rv_wfx_flash },
                    keepClear = listOfNotNull(plan.heroRect()?.px()),
                    scrim = plan.textRect()?.px(),
                    scrimDark = palette.isDarkSky,
                )
            }
        }
    }

    /** A plain sheet of cotton paper with the faintest printed horizon (DESIGN_DOCTRINE §13). */
    private fun paper(plan: WidgetPlan, p: ScenePalette, scale: Float, seed: Int): Bitmap {
        val w = (plan.width * scale).roundToInt().coerceAtLeast(1)
        val h = (plan.height * scale).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), p.paper, ColorMath.darken(p.paper, 0.025f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Faint horizon of hills along the bottom edge.
        val path = Path()
        val base = h * 0.86f
        path.moveTo(0f, h.toFloat())
        var x = 0f
        while (x <= w) {
            val y = base - (sin(x / (60 * scale) + seed % 7) * 0.5f + sin(x / (23 * scale) + seed % 3) * 0.25f) * 8 * scale
            path.lineTo(x, y)
            x += 4 * scale
        }
        path.lineTo(w.toFloat(), h.toFloat())
        path.close()
        paint.color = ColorMath.withAlpha(p.hillMid, 0.12f)
        canvas.drawPath(path, paint)

        // Cotton fibres, and a hairline cut edge around the sheet.
        paint.shader = android.graphics.BitmapShader(MaterialTextures.paper, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.alpha = if (p.isDarkPaper) 56 else 102
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        paint.alpha = 255

        return bmp
    }
}
