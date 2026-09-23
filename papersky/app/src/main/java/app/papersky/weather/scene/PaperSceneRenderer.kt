package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Paints the Papersky diorama: a watercolour sky, sun or moon, paper clouds, five layers of cut
 * paper landscape (three soft hills, a hamlet, trees) and the weather moving through it.
 *
 * The scene is split into layers so the app can cache the expensive, static ones on the GPU and
 * only redraw what moves ([drawParticles], [drawSkyFx]) each frame. Widgets and thumbnails use
 * [draw], which paints everything in one pass onto a software canvas.
 *
 * Nothing on a per-frame path allocates, blurs or builds geometry.
 */
class PaperSceneRenderer(private val density: Float) {

    data class Panel(val rect: RectF, val alpha: Float = 0.96f, val radius: Float = 16f, val tape: Boolean = false)

    /** A soft temperature line (and optional precipitation bars) printed on a widget panel. */
    class Chart(
        val rect: RectF,
        /** Y coordinate for each point, already laid out by the caller. */
        val pointsY: FloatArray,
        val bars: FloatArray? = null,
        val barsTop: Float = 0f,
    )

    data class Options(
        val time: Float = 0f,
        /** Horizon as a fraction of height; NaN picks one from the aspect ratio. */
        val horizon: Float = Float.NaN,
        /** Vertical extent of the landscape in px; NaN fits it to the space below the horizon. */
        val depth: Float = Float.NaN,
        val parallaxX: Float = 0f,
        val parallaxY: Float = 0f,
        val grain: Float = 1f,
        /** Lightning flash strength 0..1, driven by the app. */
        val flash: Float = 0f,
        val boltSeed: Int = 0,
        /** Draw a lightning bolt without a flash (static widget frames). */
        val staticBolt: Boolean = false,
        val sunSpin: Float = 0f,
        /** Extra wind from a user swipe, m/s. */
        val gust: Float = 0f,
        val panels: List<Panel> = emptyList(),
        val charts: List<Chart> = emptyList(),
        /** Fewer, bolder details for tiny scenes, 0..1. */
        val detail: Float = 1f,
        val vignette: Float = 1f,
        /** Draw the landscape. Tiny widgets may drop it. */
        val landscape: Boolean = true,
        /** Precipitation, stars and other moving bits. Animated widgets leave them to the launcher. */
        val particles: Boolean = true,
        /** Horizontal lane (fractions of width) the sun and moon travel along. */
        val laneStart: Float = 0.12f,
        val laneEnd: Float = 0.88f,
        /** Areas that clouds keep out of in static frames (widget text). */
        val keepClear: List<RectF> = emptyList(),
        /** A soft wash behind this area so text stays legible. */
        val scrim: RectF? = null,
        val scrimDark: Boolean = true,
        /** Raindrops resting on the glass in front of the scene. */
        val glass: Boolean = false,
        /** Slowly turning paper petals around the sun (animated widgets draw their own). */
        val rays: Boolean = true,
        /** The hamlet on the meadow (cottages, lantern, chimney smoke). */
        val village: Boolean = true,
        /** Offset of the nearest ridge relative to the particle layer (app parallax). */
        val groundDx: Float = 0f,
        val groundDy: Float = 0f,
    )

    private val dp = density
    internal val layout = SceneLayout(dp)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = PaperGrain.shader() }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val sprite = SpritePaint()
    private val tmpPath = Path()
    private val tmpRect = RectF()
    private val srcRect = Rect()
    private val pos = FloatArray(2)
    private val buf = Array(4) { FloatArray(256) }

    fun horizonFor(w: Float, h: Float, o: Options): Float = when {
        !o.horizon.isNaN() -> o.horizon
        h > w * 1.5f -> 0.66f
        w > h * 2.4f -> 0.58f
        else -> 0.62f
    }

    /** Lays the scene out for this size; cheap when nothing changed. */
    fun prepare(w: Float, h: Float, s: SceneState, o: Options) {
        layout.ensure(w, h, s.seed, horizonFor(w, h, o), o.detail, o.depth, o.village)
    }

    // ============================================================================================
    // One pass (widgets, thumbnails, tests)
    // ============================================================================================

    fun draw(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, o: Options = Options()) {
        if (w <= 1f || h <= 1f) return
        prepare(w, h, s, o)
        drawSky(canvas, s, p, o)
        drawSkyFx(canvas, s, p, o)
        val festoon = blanketAlpha(s)
        if (festoon > 0.01f) {
            canvas.save()
            canvas.translate(blanketOffset(s, o.time, o.gust), 0f)
            drawBlanket(canvas, s, p, festoon)
            canvas.restore()
        }
        for (i in layout.clouds.indices) {
            val a = cloudAlpha(i, s)
            if (a <= 0.01f) continue
            val c = layout.clouds[i]
            val x = cloudX(i, s, o)
            if (!c.ceiling && o.keepClear.isNotEmpty()) {
                tmpRect.set(x, c.y, x + c.width, c.y + c.height)
                if (o.keepClear.any { RectF.intersects(it, tmpRect) }) continue
            }
            canvas.save()
            canvas.translate(x, c.y)
            drawCloud(canvas, i, s, p, a)
            canvas.restore()
        }
        if (o.landscape) {
            for (b in layout.bands.indices) {
                val band = layout.bands[b]
                canvas.save()
                canvas.translate(o.parallaxX * 10 * dp * band.depth, o.parallaxY * 6 * dp * band.depth)
                drawBand(canvas, b, s, p)
                if (b == layout.bands.lastIndex) {
                    fill.color = bandBodyColor(p)
                    canvas.drawRect(-layout.margin, band.bottom - 1, w + layout.margin, h + 20 * dp, fill)
                    if (o.grain > 0f) {
                        grainPaint.alpha = (o.grain * 70).roundToInt().coerceIn(0, 255)
                        canvas.drawRect(-layout.margin, band.bottom - 1, w + layout.margin, h + 20 * dp, grainPaint)
                    }
                }
                canvas.restore()
            }
            canvas.save()
            val near = layout.bands.last()
            canvas.translate(o.parallaxX * 10 * dp * near.depth, o.parallaxY * 6 * dp * near.depth)
            drawProps(canvas, s, p, o)
            canvas.restore()
        }
        drawParticles(canvas, s, p, o.copy(vignette = 0f))
        o.scrim?.let { drawScrim(canvas, it, o.scrimDark) }
        for (panel in o.panels) drawPanel(canvas, panel, p)
        for (chart in o.charts) drawChart(canvas, chart, p)
        if (o.vignette > 0f) drawVignette(canvas, p, o.vignette)
    }

    // ============================================================================================
    // Sky: gradient, watercolour wash, glows, rainbow, sun and moon discs. Static per state.
    // ============================================================================================

    fun drawSky(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val w = layout.w
        val h = layout.h
        val hy = layout.horizonY
        val bottom = h + 20 * dp
        shaded.shader = LinearGradient(
            0f, -20 * dp, 0f, hy + layout.depthD * 0.2f,
            intArrayOf(p.skyTop, ColorMath.lerp(p.skyTop, p.skyBottom, 0.5f), p.skyBottom),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(-layout.margin, -20 * dp, w + layout.margin, bottom, shaded)

        // Warm band along the horizon at dawn and dusk.
        val golden = Palettes.timeWeights(s.daylight, s.sunProgress).let { it[1] + it[2] * 0.8f }.coerceIn(0f, 1f) * (1f - s.cloudCover * 0.6f)
        if (golden > 0.02f) {
            shaded.shader = LinearGradient(
                0f, hy - layout.depthD * 1.4f, 0f, hy + layout.depthD * 0.3f,
                intArrayOf(0, ColorMath.scaleAlpha(p.glow, golden * 1.2f)), null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(-layout.margin, hy - layout.depthD * 1.4f, w + layout.margin, hy + layout.depthD * 0.3f, shaded)
        }

        // Watercolour blooms, stretched from a tiny texture.
        srcRect.set(0, 0, PaperGrain.wash.width, PaperGrain.wash.height)
        tmpRect.set(-layout.margin, -20 * dp, w + layout.margin, hy + layout.depthD * 0.4f)
        bitmapPaint.alpha = 255
        canvas.drawBitmap(PaperGrain.wash, srcRect, tmpRect, bitmapPaint)

        if (s.rainbow > 0.01f) drawRainbow(canvas, s.rainbow)
        drawGodRays(canvas, s, p, o)
        drawCelestial(canvas, s, p, o)

        if (o.grain > 0f) {
            grainPaint.alpha = (o.grain * 55).roundToInt().coerceIn(0, 255)
            canvas.drawRect(-layout.margin, -20 * dp, w + layout.margin, bottom, grainPaint)
        }
        shaded.shader = null
    }

    private fun drawRainbow(canvas: Canvas, amount: Float) {
        val colors = intArrayOf(0xFFE8766B.toInt(), 0xFFF2A65E.toInt(), 0xFFF2D56B.toInt(), 0xFF8FC38B.toInt(), 0xFF7BA7D6.toInt(), 0xFF9C88C9.toInt())
        val hy = layout.horizonY
        val cx = layout.w * 0.3f
        val radius = min(layout.w * 0.44f, hy * 0.9f)
        val band = radius * 0.04f
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = band * 0.94f
        colors.forEachIndexed { i, c ->
            stroke.color = ColorMath.withAlpha(c, 0.45f * amount)
            val r = radius - i * band
            tmpRect.set(cx - r, hy - r, cx + r, hy + r)
            canvas.drawArc(tmpRect, 180f, 180f, false, stroke)
        }
        stroke.strokeCap = Paint.Cap.ROUND
    }

    private fun celestialRadius(): Float = (min(layout.w, layout.h) * 0.075f).coerceIn(9 * dp, 30 * dp)

    private fun arcPosition(progress: Float, o: Options, out: FloatArray) {
        val pr = progress.coerceIn(-0.1f, 1.1f)
        val w = layout.w
        val hy = layout.horizonY
        val arcH = min(hy * 0.72f, w * 0.6f)
        out[0] = w * (o.laneStart + (o.laneEnd - o.laneStart) * pr)
        val clamped = pr.coerceIn(0f, 1f)
        out[1] = hy - arcH * sin(PI.toFloat() * clamped) + abs(pr - clamped) * arcH * 1.2f
    }

    /** How much of the sky is open: thick cloud, rain or fog hide the sun and moon. */
    private fun openSky(s: SceneState) =
        (1f - smoothstep(0.55f, 1f, s.cloudCover) * 0.75f - s.fog * 0.45f - (s.rain + s.snow + s.drizzle).coerceIn(0f, 1f) * 0.7f).coerceIn(0f, 1f)

    private fun sunAlpha(s: SceneState) = smoothstep(0.05f, 0.45f, s.daylight) * openSky(s) *
        (if (s.sunProgress > -0.12f && s.sunProgress < 1.12f) 1f else 0f)

    private fun moonAlpha(s: SceneState) = (1f - s.daylight * 1.7f).coerceIn(0f, 1f) * openSky(s)

    /** True when a body at (x, y, r) would sit on text the scene must keep clear. */
    private fun blocked(o: Options, x: Float, y: Float, r: Float): Boolean {
        if (o.keepClear.isEmpty()) return false
        tmpRect.set(x - r * 1.3f, y - r * 1.3f, x + r * 1.3f, y + r * 1.3f)
        return o.keepClear.any { RectF.intersects(it, tmpRect) }
    }

    /** Where the visible sun (or moon) is: [x, y, radius], or null. For hit-testing taps. */
    fun celestialAt(w: Float, h: Float, s: SceneState, o: Options = Options()): FloatArray? {
        prepare(w, h, s, o)
        val r = celestialRadius()
        val out = FloatArray(2)
        val body = when {
            sunAlpha(s) > 0.2f -> { arcPosition(s.sunProgress, o, out); floatArrayOf(out[0], out[1], r) }
            moonAlpha(s) > 0.2f -> { arcPosition(s.nightProgress.coerceIn(0.05f, 0.95f), o, out); floatArrayOf(out[0], out[1], r * 0.86f) }
            else -> null
        }
        return body?.takeUnless { blocked(o, it[0], it[1], it[2]) }
    }

    private fun drawCelestial(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val r = celestialRadius()
        val big = min(layout.w, layout.h)

        val moonA = moonAlpha(s)
        if (moonA > 0.01f && !arcPosition(s.nightProgress.coerceIn(0.05f, 0.95f), o, pos).let { blocked(o, pos[0], pos[1], r) }) {
            glow(canvas, pos[0], pos[1], big * 0.42f, ColorMath.scaleAlpha(p.glow, moonA * 1.4f))
            drawMoon(canvas, pos[0], pos[1], r * 0.86f, s.moonPhase, p, moonA)
        }

        val sunA = sunAlpha(s)
        if (sunA > 0.01f && !arcPosition(s.sunProgress, o, pos).let { blocked(o, pos[0], pos[1], r) }) {
            val x = pos[0]
            val y = pos[1]
            glow(canvas, x, y, big * 0.62f, ColorMath.scaleAlpha(p.glow, sunA * 1.1f))
            glow(canvas, x, y, r * 3.2f, ColorMath.withAlpha(p.sunRay, 0.35f * sunA))
            // Halo rings, like circles of tracing paper.
            stroke.strokeWidth = 1.1f * dp
            stroke.color = ColorMath.withAlpha(p.sunRay, 0.2f * sunA)
            canvas.drawCircle(x, y, r * 1.75f, stroke)
            stroke.color = ColorMath.withAlpha(p.sunRay, 0.11f * sunA)
            canvas.drawCircle(x, y, r * 2.5f, stroke)
            // Layered paper discs.
            fill.color = ColorMath.withAlpha(p.sunRay, 0.55f * sunA)
            canvas.drawCircle(x, y, r * 1.16f, fill)
            shaded.shader = RadialGradient(
                x - r * 0.3f, y - r * 0.35f, r * 1.4f,
                intArrayOf(ColorMath.lighten(p.sun, 0.35f), p.sun, ColorMath.darken(p.sun, 0.06f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            shaded.alpha = (sunA * 255).roundToInt()
            canvas.drawCircle(x, y, r, shaded)
            shaded.shader = null
            shaded.alpha = 255
        }
    }

    private fun drawMoon(canvas: Canvas, x: Float, y: Float, r: Float, phase: Float, p: ScenePalette, alpha: Float) {
        // Soft paper shadow below the disc.
        for (k in 3 downTo 1) {
            fill.color = ColorMath.scaleAlpha(p.shadow, 0.12f * alpha)
            canvas.drawCircle(x, y + k * 0.9f * dp, r + k * 0.6f * dp, fill)
        }
        shaded.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.3f, r * 1.3f,
            intArrayOf(ColorMath.lighten(p.moon, 0.3f), p.moon, ColorMath.darken(p.moon, 0.08f)),
            null, Shader.TileMode.CLAMP,
        )
        shaded.alpha = (alpha * 255).roundToInt()
        canvas.drawCircle(x, y, r, shaded)
        shaded.shader = null
        shaded.alpha = 255

        fill.color = ColorMath.withAlpha(ColorMath.darken(p.moon, 0.3f), 0.14f * alpha)
        canvas.drawCircle(x - r * 0.3f, y - r * 0.18f, r * 0.2f, fill)
        canvas.drawCircle(x + r * 0.28f, y + r * 0.3f, r * 0.14f, fill)
        canvas.drawCircle(x + r * 0.05f, y - r * 0.48f, r * 0.09f, fill)
        canvas.drawCircle(x - r * 0.05f, y + r * 0.55f, r * 0.07f, fill)

        // Terminator: dark side as half-disc plus a half-ellipse.
        val k = cos(2 * PI * phase).toFloat() // 1 new, 0 quarter, -1 full
        if (k > -0.97f) {
            val waxing = phase < 0.5f
            tmpPath.reset()
            tmpRect.set(x - r, y - r, x + r, y + r)
            tmpPath.arcTo(tmpRect, -90f, if (waxing) -180f else 180f, true)
            val ex = r * abs(k)
            tmpRect.set(x - ex, y - r, x + ex, y + r)
            val sweep = if (waxing) (if (k > 0) -180f else 180f) else (if (k > 0) 180f else -180f)
            tmpPath.arcTo(tmpRect, 90f, sweep, false)
            tmpPath.close()
            fill.color = ColorMath.withAlpha(ColorMath.lerp(p.skyTop, p.moon, 0.18f), 0.84f * alpha)
            canvas.drawPath(tmpPath, fill)
        }
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        if (ColorMath.a(color) == 0) return
        shaded.shader = RadialGradient(
            x, y, radius,
            intArrayOf(color, ColorMath.scaleAlpha(color, 0.35f), ColorMath.withAlpha(color, 0f)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, shaded)
        shaded.shader = null
    }

    // ============================================================================================
    // Sky effects: twinkling stars, shooting stars, turning sun rays. Cheap; redrawn per frame.
    // ============================================================================================

    fun drawSkyFx(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        if (o.particles) drawStars(canvas, s, p, o)
        if (o.rays) drawSunRays(canvas, s, p, o)
    }

    private fun starVisibility(s: SceneState) = (1f - s.daylight * 1.6f).coerceIn(0f, 1f) * (1f - s.cloudCover * 0.9f) * (1f - s.fog)

    private fun drawStars(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val vis = starVisibility(s)
        if (vis < 0.02f) return
        val t = o.time
        // Keep stars off the moon.
        val moon = if (moonAlpha(s) > 0.05f) {
            arcPosition(s.nightProgress.coerceIn(0.05f, 0.95f), o, pos); celestialRadius() * 1.5f
        } else 0f
        for (g in 0..2) {
            val src = layout.stars[g]
            val out = ensureBuf(3, src.size)
            var n = 0
            var i = 0
            while (i < src.size) {
                val x = src[i]; val y = src[i + 1]
                if (moon == 0f || hypot(x - pos[0], y - pos[1]) > moon) { out[n++] = x; out[n++] = y }
                i += 2
            }
            val tw = 0.5f + 0.5f * sin(t * (0.9f + g * 0.55f) + g * 2.1f)
            lines.color = ColorMath.withAlpha(p.star, vis * (0.35f + 0.55f * tw))
            lines.strokeWidth = (1.1f + g * 0.45f) * dp
            if (n > 0) canvas.drawPoints(out, 0, n, lines)
        }
        val b = layout.bright
        var k = 0
        while (k < b.size) {
            val i = k / 3
            val tw = 0.55f + 0.45f * sin(t * (1.3f + rand(i, 77) * 1.6f) + i * 1.7f)
            sprite.tint(p.star, vis * tw)
            sprite.draw(canvas, SceneSprites.glint, b[k], b[k + 1], b[k + 2] * (0.8f + 0.3f * tw))
            k += 3
        }
        // A shooting star every so often on clear nights.
        if (s.cloudCover < 0.4f && o.particles) {
            val period = 13f
            val cycle = floor(t / period).toInt()
            val ph = (t - cycle * period) / 0.9f
            if (ph in 0f..1f) {
                val sx = layout.w * (0.15f + 0.7f * rand(cycle, 41))
                val sy = layout.horizonY * (0.08f + 0.3f * rand(cycle, 43))
                val ang = (if (rand(cycle, 45) > 0.5f) 0.42f else PI.toFloat() - 0.42f)
                val len = min(layout.w, layout.h) * 0.3f
                val hx = sx + cos(ang) * len * ph
                val hy = sy + sin(ang) * len * ph
                val tail = len * 0.35f * sin(ph * PI.toFloat())
                shaded.shader = LinearGradient(hx, hy, hx - cos(ang) * tail, hy - sin(ang) * tail, ColorMath.withAlpha(p.star, vis), 0, Shader.TileMode.CLAMP)
                stroke.strokeWidth = 1.6f * dp
                stroke.shader = shaded.shader
                canvas.drawLine(hx, hy, hx - cos(ang) * tail, hy - sin(ang) * tail, stroke)
                stroke.shader = null
                shaded.shader = null
            }
        }
    }

    /**
     * The sun's paper petals: twelve rounded slips, long and short, turning once a minute
     * (DESIGN_DOCTRINE §10). Drawn without shadow so they cost nothing per frame.
     */
    private fun drawSunRays(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val a = sunAlpha(s)
        if (a < 0.03f) return
        arcPosition(s.sunProgress, o, pos)
        val r = celestialRadius()
        if (blocked(o, pos[0], pos[1], r)) return
        fill.color = ColorMath.scaleAlpha(p.sunRay, a)
        canvas.save()
        canvas.translate(pos[0], pos[1])
        canvas.rotate(o.time * 6f + o.sunSpin)
        for (i in 0 until 12) {
            val long = i % 2 == 0
            val inner = r * 1.24f
            val outer = r * if (long) 1.8f else 1.52f
            val half = r * if (long) 0.17f else 0.13f
            tmpRect.set(-half, -outer, half, -inner)
            canvas.drawRoundRect(tmpRect, half, half, fill)
            canvas.rotate(30f)
        }
        canvas.restore()
    }

    /** Soft shafts of light falling from a sun half-hidden by clouds (§4.4). */
    private fun drawGodRays(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val cover = s.cloudCover
        val a = sunAlpha(s) * smoothstep(0.25f, 0.45f, cover) * (1f - smoothstep(0.8f, 0.95f, cover)) * (1f - (s.rain + s.snow).coerceIn(0f, 1f))
        if (a < 0.03f) return
        arcPosition(s.sunProgress, o, pos)
        val len = layout.h * 0.9f
        shaded.shader = RadialGradient(pos[0], pos[1], len, intArrayOf(ColorMath.withAlpha(p.sunRay, 0.22f * a), ColorMath.withAlpha(p.sunRay, 0f)), null, Shader.TileMode.CLAMP)
        for (k in 0 until 5) {
            val ang = (PI / 2 + (k - 2) * 0.28f + (rand(k, s.seed + 1701) - 0.5f) * 0.12f).toFloat()
            val half = 0.035f + 0.03f * rand(k, s.seed + 1703)
            tmpPath.reset()
            tmpPath.moveTo(pos[0], pos[1])
            tmpPath.lineTo(pos[0] + cos(ang - half) * len, pos[1] + sin(ang - half) * len)
            tmpPath.lineTo(pos[0] + cos(ang + half) * len, pos[1] + sin(ang + half) * len)
            tmpPath.close()
            canvas.drawPath(tmpPath, shaded)
        }
        shaded.shader = null
    }

    // ============================================================================================
    // Clouds: each is a small, separately cached sprite that the app slides across the sky.
    // ============================================================================================

    val cloudCount: Int get() = layout.clouds.size

    fun cloudIsCeiling(i: Int) = layout.clouds[i].ceiling

    /** Local box a cloud's drawing covers, relative to its top-left, shadows included. */
    fun cloudBounds(i: Int, out: RectF) {
        val c = layout.clouds[i]
        out.set(-6 * dp, -c.height * 0.34f - 4 * dp, c.width + 6 * dp, c.height * 1.12f + 8 * dp)
    }

    fun cloudTop(i: Int) = layout.clouds[i].y

    /** Left edge of cloud [i] at [Options.time]: drifting with the wind and wrapping around. */
    fun cloudX(i: Int, s: SceneState, o: Options): Float = cloudX(i, s, o.time, o.gust)

    fun cloudX(i: Int, s: SceneState, time: Float, gust: Float): Float {
        val c = layout.clouds[i]
        val wind = s.windX + gust
        val dir = if (wind < 0) -1f else 1f
        val speed = (3.2f + abs(wind) * 1.1f) * dp * (if (c.ceiling) 0.3f else 0.4f + 0.8f * c.depth)
        val span = layout.w + c.width * 1.4f
        val x = ((c.x0 + time * speed * dir) % span + span) % span
        return x - c.width * 1.2f
    }

    fun cloudAlpha(i: Int, s: SceneState): Float {
        val c = layout.clouds[i]
        val wet = (s.rain + s.drizzle + s.snow + s.thunder).coerceIn(0f, 1f)
        val count = s.cloudCover * layout.clouds.size * 1.1f + wet * 1.5f + 0.3f
        return (count - c.rank).coerceIn(0f, 1f)
    }

    /** Draws cloud [i] with its top-left at the canvas origin. */
    fun drawCloud(canvas: Canvas, i: Int, s: SceneState, p: ScenePalette, alpha: Float = 1f) {
        val c = layout.clouds[i]
        val air = (1f - c.depth) * 0.25f
        val heavy = (s.rain + s.thunder + s.drizzle * 0.5f).coerceIn(0f, 1f) * 0.35f
        val body = if (c.ceiling) ColorMath.lerp(p.cloudShade, p.cloud, 0.45f - heavy) else ColorMath.lerp(ColorMath.lerp(p.cloud, p.skyBottom, air), p.cloudShade, heavy)
        val back = ColorMath.lerp(ColorMath.lerp(p.cloudShade, p.skyTop, air * 0.8f), p.cloudShade, heavy)

        // Back sheet peeking out above, with its own faint shadow.
        fill.color = ColorMath.withAlpha(back, alpha)
        canvas.drawPath(c.back, fill)

        // Soft shadow cast downward by the front sheet.
        for (k in 4 downTo 1) {
            fill.color = ColorMath.scaleAlpha(p.shadow, 0.09f * alpha)
            canvas.save()
            canvas.translate(0f, k * 1.3f * dp)
            canvas.drawPath(c.front, fill)
            canvas.restore()
        }
        shaded.shader = LinearGradient(
            0f, 0f, 0f, c.height,
            ColorMath.lighten(body, 0.07f), ColorMath.lerp(body, p.cloudShade, 0.3f), Shader.TileMode.CLAMP,
        )
        shaded.alpha = (alpha * 255).roundToInt()
        canvas.drawPath(c.front, shaded)
        shaded.shader = null
        shaded.alpha = 255
        // Light catching the paper edge.
        stroke.strokeWidth = 1f * dp
        stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.16f * alpha)
        canvas.drawPath(c.front, stroke)
    }

    // ============================================================================================
    // Overcast festoon: a scalloped paper band along the top of the sky.
    // ============================================================================================

    val blanketPeriod: Float get() = layout.blanketPeriod

    fun blanketAlpha(s: SceneState): Float =
        (smoothstep(0.72f, 1f, s.cloudCover) + (s.rain + s.drizzle + s.thunder).coerceIn(0f, 1f) * 0.35f * smoothstep(0.5f, 0.8f, s.cloudCover)).coerceIn(0f, 1f)

    /** Horizontal drift of the festoon, wrapped to one period. */
    fun blanketOffset(s: SceneState, time: Float, gust: Float): Float {
        val wind = s.windX + gust
        val dir = if (wind < 0) -1f else 1f
        val speed = (3f + abs(wind) * 1.3f) * dp * 0.4f
        return (time * speed * dir) % layout.blanketPeriod
    }

    /** Draws the festoon in scene coordinates (x from −2 periods to w + 2 periods). */
    fun drawBlanket(canvas: Canvas, s: SceneState, p: ScenePalette, alpha: Float = blanketAlpha(s)) {
        if (alpha <= 0.01f) return
        val heavy = (s.rain + s.thunder).coerceIn(0f, 1f) * 0.3f
        val period = layout.blanketPeriod
        // Back row, offset half a scallop and a little lower: two sheets of paper.
        canvas.save()
        canvas.translate(period * 0.5f, layout.horizonY * 0.06f)
        fill.color = ColorMath.withAlpha(ColorMath.lerp(p.cloudShade, p.skyTop, 0.1f), alpha)
        canvas.drawPath(layout.blanket, fill)
        canvas.restore()
        for (k in 4 downTo 1) {
            fill.color = ColorMath.scaleAlpha(p.shadow, 0.1f * alpha)
            canvas.save()
            canvas.translate(0f, k * 1.4f * dp)
            canvas.drawPath(layout.blanket, fill)
            canvas.restore()
        }
        val body = ColorMath.lerp(ColorMath.lerp(p.cloudShade, p.cloud, 0.6f), p.cloudShade, heavy)
        shaded.shader = LinearGradient(0f, 0f, 0f, layout.horizonY * 0.4f, ColorMath.lighten(body, 0.05f), ColorMath.lerp(body, p.cloudShade, 0.25f), Shader.TileMode.CLAMP)
        shaded.alpha = (alpha * 255).roundToInt()
        canvas.drawPath(layout.blanket, shaded)
        shaded.shader = null
        shaded.alpha = 255
        stroke.strokeWidth = 1f * dp
        stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.14f * alpha)
        canvas.drawPath(layout.blanket, stroke)
    }

    // ============================================================================================
    // Village: cottages baked into the meadow's band; trees, smoke and lit windows per frame.
    // ============================================================================================

    /** How lit the windows are: at dusk, at night, and on dreary days. */
    private fun windowsLit(s: SceneState) = (1f - s.daylight * 1.25f + s.cloudCover * 0.3f + (s.rain + s.snow) * 0.35f).coerceIn(0f, 1f)

    private fun drawVillage(canvas: Canvas, s: SceneState, p: ScenePalette) {
        val meadow = layout.ridges.last()
        val sc = layout.propScale
        val lit = windowsLit(s)
        // Soft ground shadows under the trees (the trees themselves sway per frame).
        for (t in layout.trees) {
            val gy = layout.edge(meadow, t.x) + 3 * dp
            fill.color = ColorMath.scaleAlpha(p.shadow, 0.35f)
            tmpRect.set(t.x - t.h * 0.2f, gy - 1.5f * dp, t.x + t.h * 0.35f, gy + 2.5f * dp)
            canvas.drawOval(tmpRect, fill)
        }
        for ((i, house) in layout.houses.withIndex()) {
            val gy = layout.edge(meadow, house.x) + 4 * dp
            val left = house.x - house.w / 2
            val right = house.x + house.w / 2
            val top = gy - house.h
            val wall = ColorMath.lerp(p.house, ColorMath.lighten(p.house, 0.2f), house.tone * 0.5f)
            val roofTop = top - house.roofH
            // Paper shadow cast down-right onto the meadow.
            for (k in 3 downTo 1) {
                fill.color = ColorMath.scaleAlpha(p.shadow, 0.12f)
                canvas.drawRect(left + k * 0.8f * dp, top + k * 1.2f * dp, right + k * 0.8f * dp, gy + 2 * dp, fill)
            }
            // Warm light spilling onto the grass from the windows.
            if (lit > 0.05f) glow(canvas, house.x, gy + 1.5f * dp, house.w * 1.3f, ColorMath.withAlpha(p.window, 0.28f * lit))
            // Walls: lit front, a darker side face on the right (a folded paper box).
            shaded.shader = LinearGradient(left, top, right, gy, ColorMath.lighten(wall, 0.06f), ColorMath.darken(wall, 0.05f), Shader.TileMode.CLAMP)
            canvas.drawRect(left, top, right, gy + 4 * dp, shaded)
            shaded.shader = null
            fill.color = ColorMath.withAlpha(ColorMath.darken(wall, 0.25f), 0.55f)
            canvas.drawRect(right - house.w * 0.18f, top, right, gy + 4 * dp, fill)
            // Chimney behind the roof.
            val chX = left + house.w * 0.66f
            if (house.chimney) {
                fill.color = ColorMath.darken(p.roof, 0.2f)
                canvas.drawRect(chX, roofTop + house.roofH * 0.1f, chX + house.w * 0.14f, top - house.roofH * 0.3f, fill)
                fill.color = ColorMath.darken(p.roof, 0.35f)
                canvas.drawRect(chX - 0.8f * dp, roofTop + house.roofH * 0.06f, chX + house.w * 0.14f + 0.8f * dp, roofTop + house.roofH * 0.16f, fill)
            }
            // Roof with a little overhang, shingle lines and a lit ridge.
            tmpPath.reset()
            tmpPath.moveTo(left - 2.5f * dp, top + 0.6f * dp)
            tmpPath.lineTo(house.x, roofTop)
            tmpPath.lineTo(right + 2.5f * dp, top + 0.6f * dp)
            tmpPath.close()
            for (k in 3 downTo 1) {
                fill.color = ColorMath.scaleAlpha(p.shadow, 0.1f)
                canvas.save(); canvas.translate(0f, k * 0.9f * dp); canvas.drawPath(tmpPath, fill); canvas.restore()
            }
            shaded.shader = LinearGradient(left, roofTop, right, top, ColorMath.lighten(p.roof, 0.1f), ColorMath.darken(p.roof, 0.12f), Shader.TileMode.CLAMP)
            canvas.drawPath(tmpPath, shaded)
            shaded.shader = null
            canvas.save()
            canvas.clipPath(tmpPath)
            stroke.strokeWidth = 0.7f * dp
            stroke.color = ColorMath.withAlpha(ColorMath.darken(p.roof, 0.35f), 0.4f)
            var yy = roofTop + house.roofH * 0.35f
            while (yy < top) { canvas.drawLine(left - 3 * dp, yy, right + 3 * dp, yy, stroke); yy += house.roofH * 0.26f }
            canvas.restore()
            stroke.strokeWidth = 1f * dp
            stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.22f)
            canvas.drawLine(left - 2.5f * dp, top + 0.6f * dp, house.x, roofTop, stroke)
            if (s.snowGround > 0.05f) {
                tmpPath.reset()
                val sx = house.w * 0.4f
                tmpPath.moveTo(house.x - sx - 2 * dp, roofTop + house.roofH * 0.55f)
                tmpPath.lineTo(house.x, roofTop - 1 * dp)
                tmpPath.lineTo(house.x + sx + 2 * dp, roofTop + house.roofH * 0.55f)
                tmpPath.quadTo(house.x, roofTop + house.roofH * 0.7f, house.x - sx - 2 * dp, roofTop + house.roofH * 0.55f)
                fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround)
                canvas.drawPath(tmpPath, fill)
            }
            // Windows with a cross of mullions; warm when lit.
            val ws = house.w * 0.24f
            val wy = top + house.h * 0.28f
            val glass = ColorMath.lerp(ColorMath.darken(wall, 0.5f), p.window, lit)
            val frame = ColorMath.darken(wall, 0.3f)
            for (wx in windowsOf(house)) {
                fill.color = frame
                canvas.drawRect(wx - ws / 2 - 0.9f * dp, wy - 0.9f * dp, wx + ws / 2 + 0.9f * dp, wy + ws + 0.9f * dp, fill)
                fill.color = glass
                canvas.drawRect(wx - ws / 2, wy, wx + ws / 2, wy + ws, fill)
                stroke.strokeWidth = 0.7f * dp
                stroke.color = frame
                canvas.drawLine(wx, wy, wx, wy + ws, stroke)
                canvas.drawLine(wx - ws / 2, wy + ws / 2, wx + ws / 2, wy + ws / 2, stroke)
            }
            if (house.door) {
                val dw = house.w * 0.2f
                val dx = if (house.twoWindows) house.x else house.x + house.w * 0.24f
                fill.color = ColorMath.darken(p.roof, 0.3f)
                tmpRect.set(dx - dw / 2, gy - house.h * 0.42f, dx + dw / 2, gy + 1 * dp)
                canvas.drawRoundRect(tmpRect, dw / 2, dw / 2, fill)
            }
        }
        if (!layout.lanternX.isNaN()) {
            val lx = layout.lanternX
            val gy = layout.edge(meadow, lx) + 3 * dp
            val postH = 22 * dp * sc
            stroke.strokeWidth = 1.6f * dp
            stroke.color = ColorMath.darken(p.hillNear, 0.55f)
            canvas.drawLine(lx, gy, lx, gy - postH, stroke)
            fill.color = ColorMath.darken(p.hillNear, 0.55f)
            canvas.drawRect(lx - 3.2f * dp * sc, gy - postH - 1.5f * dp, lx + 3.2f * dp * sc, gy - postH + 0.5f * dp, fill)
            fill.color = ColorMath.lerp(ColorMath.darken(p.house, 0.4f), p.window, lit)
            canvas.drawRect(lx - 2.4f * dp * sc, gy - postH - 7 * dp * sc, lx + 2.4f * dp * sc, gy - postH - 1.5f * dp, fill)
            fill.color = ColorMath.darken(p.hillNear, 0.55f)
            tmpPath.reset()
            tmpPath.moveTo(lx - 3.6f * dp * sc, gy - postH - 7 * dp * sc)
            tmpPath.lineTo(lx, gy - postH - 10.5f * dp * sc)
            tmpPath.lineTo(lx + 3.6f * dp * sc, gy - postH - 7 * dp * sc)
            tmpPath.close()
            canvas.drawPath(tmpPath, fill)
            if (lit > 0.05f) glow(canvas, lx, gy, 16 * dp * sc, ColorMath.withAlpha(p.window, 0.25f * lit))
        }
    }

    private val windowXs = FloatArray(2)

    private fun windowsOf(house: SceneLayout.House): FloatArray {
        if (house.twoWindows) { windowXs[0] = house.x - house.w * 0.22f; windowXs[1] = house.x + house.w * 0.22f; return windowXs }
        return floatArrayOf(house.x - house.w * 0.12f)
    }

    /**
     * Everything alive on the meadow, redrawn each frame: trees bending in the wind, smoke from
     * chimneys on cold days, windows and the lantern flickering like candles.
     */
    fun drawProps(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        if (!o.landscape || layout.ridges.isEmpty()) return
        val meadow = layout.ridges.last()
        val t = o.time
        val wind = s.windX + o.gust
        val sc = layout.propScale
        val lit = windowsLit(s)
        if (lit > 0.05f) {
            for ((i, house) in layout.houses.withIndex()) {
                val gy = layout.edge(meadow, house.x) + 4 * dp
                val ws = house.w * 0.24f
                val wy = gy - house.h + house.h * 0.28f
                val flicker = 0.88f + 0.12f * sin(t * 2.7f * 6.283f / 6f + i * 1.3f)
                for (wx in windowsOf(house)) {
                    sprite.tint(p.window, 0.4f * lit * flicker)
                    sprite.draw(canvas, SceneSprites.glow, wx, wy + ws / 2, ws * 2.6f)
                }
            }
            if (!layout.lanternX.isNaN()) {
                val lx = layout.lanternX
                val gy = layout.edge(meadow, lx) + 3 * dp
                val f = 0.9f + 0.1f * sin(t * 3.1f)
                sprite.tint(p.window, 0.6f * lit * f)
                sprite.draw(canvas, SceneSprites.glow, lx, gy - 22 * dp * sc - 4 * dp * sc, 26 * dp * sc)
            }
        }
        // Smoke puffs drift with the wind when it's cold enough to heat the houses.
        if (s.temperature < 14f && o.village) {
            for ((i, house) in layout.houses.withIndex()) {
                if (!house.chimney) continue
                val gy = layout.edge(meadow, house.x) + 4 * dp
                val chX = house.x - house.w / 2 + house.w * 0.73f
                val chTop = gy - house.h - house.roofH * 0.9f
                for (k in 0 until 5) {
                    val ph = fract(t * 0.22f + k / 5f + i * 0.37f)
                    val px = chX + wind.coerceIn(-12f, 12f) * ph * 2.6f * dp + sin(ph * 7f + i) * 2 * dp
                    val py = chTop - ph * 34 * dp * sc
                    fill.color = ColorMath.withAlpha(ColorMath.lerp(p.cloud, p.cloudShade, 0.3f), (1f - ph) * 0.55f)
                    canvas.drawCircle(px, py, (2.2f + ph * 6f) * dp * sc, fill)
                }
            }
        }
        // Trees sway on their roots, more in the wind and when a gust is flicked across.
        val swayBase = (1.2f + abs(wind) * 0.8f).coerceAtMost(11f)
        for ((i, tree) in layout.trees.withIndex()) {
            val gy = layout.edge(meadow, tree.x) + 4 * dp
            val sway = if (t == 0f) wind.coerceIn(-14f, 14f) * 0.35f else sin(t * (1.3f + (i % 5) * 0.12f) + i * 0.9f) * swayBase * 0.5f + wind.coerceIn(-14f, 14f) * 0.35f
            val color = ColorMath.lerp(p.tree, p.hillNear, 0.12f + tree.tone * 0.3f)
            canvas.save()
            canvas.rotate(sway, tree.x, gy)
            if (tree.pine) {
                val tw = tree.h * 0.52f
                for (tier in 0..2) {
                    val tierTop = gy - tree.h + tier * tree.h * 0.24f
                    val tierW = tw * (0.55f + tier * 0.22f)
                    tmpPath.reset()
                    tmpPath.moveTo(tree.x, tierTop)
                    tmpPath.lineTo(tree.x + tierW / 2, tierTop + tree.h * 0.42f)
                    tmpPath.lineTo(tree.x - tierW / 2, tierTop + tree.h * 0.42f)
                    tmpPath.close()
                    // Each tier is its own slip of paper, shaded at the hem.
                    fill.color = ColorMath.darken(color, 0.12f)
                    canvas.save(); canvas.translate(0.6f * dp, 1.2f * dp); canvas.drawPath(tmpPath, fill); canvas.restore()
                    fill.color = ColorMath.lighten(color, 0.04f * (2 - tier))
                    canvas.drawPath(tmpPath, fill)
                    if (s.snowGround > 0.3f) {
                        fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround * 0.9f)
                        tmpPath.reset()
                        tmpPath.moveTo(tree.x, tierTop)
                        tmpPath.lineTo(tree.x + tierW * 0.22f, tierTop + tree.h * 0.16f)
                        tmpPath.lineTo(tree.x - tierW * 0.22f, tierTop + tree.h * 0.16f)
                        tmpPath.close()
                        canvas.drawPath(tmpPath, fill)
                    }
                }
            } else {
                fill.color = ColorMath.darken(p.tree, 0.35f)
                canvas.drawRect(tree.x - tree.h * 0.05f, gy - tree.h * 0.45f, tree.x + tree.h * 0.05f, gy, fill)
                val cr = tree.h * 0.3f
                fill.color = ColorMath.darken(color, 0.12f)
                canvas.drawCircle(tree.x + 0.8f * dp, gy - tree.h * 0.6f + 1.2f * dp, cr, fill)
                fill.color = color
                canvas.drawCircle(tree.x, gy - tree.h * 0.62f, cr, fill)
                fill.color = ColorMath.lighten(color, 0.08f)
                canvas.drawCircle(tree.x - cr * 0.55f, gy - tree.h * 0.5f, cr * 0.72f, fill)
                if (s.snowGround > 0.3f) {
                    fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround * 0.85f)
                    tmpRect.set(tree.x - cr * 0.8f, gy - tree.h * 0.62f - cr, tree.x + cr * 0.8f, gy - tree.h * 0.62f - cr * 0.2f)
                    canvas.drawArc(tmpRect, 180f, 180f, true, fill)
                }
            }
            canvas.restore()
        }
    }

    // ============================================================================================
    // Landscape bands: cached layers of paper ridges.
    // ============================================================================================

    val bandCount: Int get() = layout.bands.size
    fun bandTop(b: Int) = layout.bands[b].top
    fun bandBottom(b: Int) = layout.bands[b].bottom
    fun bandDepth(b: Int) = layout.bands[b].depth
    val sceneMargin: Float get() = layout.margin

    /** Colour of the nearest ridge below its band; the app fills the rest of the screen with it. */
    fun bandBodyColor(p: ScenePalette): Int = ColorMath.darken(ridgeColor(layout.ridges.last(), p), 0.07f)

    private fun ridgeColor(r: SceneLayout.Ridge, p: ScenePalette): Int {
        val base = when (r.index) {
            0 -> p.hillFar
            1 -> p.hillMid
            else -> p.hillNear
        }
        // A breath of aerial perspective: the far hill leans toward the sky.
        val air = ColorMath.lerp(p.skyTop, p.skyBottom, 0.6f)
        return ColorMath.lerp(base, air, (1f - r.depth) * (1f - r.depth) * 0.22f)
    }

    private fun hazeColor(p: ScenePalette) = ColorMath.lerp(p.skyBottom, p.cloud, 0.25f)

    /** Draws band [b] in scene coordinates. */
    fun drawBand(canvas: Canvas, b: Int, s: SceneState, p: ScenePalette) {
        val band = layout.bands[b]
        for (i in band.first..band.last) {
            val r = layout.ridges[i]
            val next = layout.ridges.getOrNull(i + 1)
            drawRidge(canvas, r, next, s, p, clipBottom = band.bottom)
        }
        if (b == layout.bands.lastIndex) drawVillage(canvas, s, p)
    }

    private fun drawRidge(canvas: Canvas, r: SceneLayout.Ridge, next: SceneLayout.Ridge?, s: SceneState, p: ScenePalette, clipBottom: Float) {
        val left = -layout.margin
        val right = layout.w + layout.margin
        val color = ridgeColor(r, p)
        canvas.save()
        canvas.clipRect(left, r.top - 24 * dp, right, clipBottom)

        // The sheet casts a soft shadow onto the one behind it, just above its edge.
        val step = (1.3f + 1.2f * r.depth) * dp
        for (k in 7 downTo 1) {
            fill.color = ColorMath.withAlpha(p.shadow, 0.05f)
            canvas.save()
            canvas.translate(0f, -k * step)
            canvas.drawPath(r.path, fill)
            canvas.restore()
        }

        // The sheet: a touch lighter along the top where light grazes it.
        shaded.shader = LinearGradient(
            0f, r.top, 0f, r.bottom + r.amp * 0.8f,
            intArrayOf(ColorMath.lerp(color, p.skyBottom, 0.08f), color, ColorMath.darken(color, 0.06f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(r.path, shaded)
        shaded.shader = null

        // Paper edge catching the light.
        stroke.strokeWidth = 1.2f * dp
        stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.12f)
        canvas.drawPath(r.path, stroke)

        if (s.snowGround > 0.02f) drawSnow(canvas, r, p, s.snowGround)

        // Paper texture within the sheet.
        canvas.save()
        canvas.clipPath(r.path)
        grainPaint.alpha = 70
        canvas.drawRect(left, r.top - 4 * dp, right, clipBottom, grainPaint)
        canvas.restore()

        // Fog pools at the foot of the sheet, so the next one stands out.
        if (next != null && s.fog > 0.02f) {
            val hazeA = (s.fog * 0.6f).coerceAtMost(0.8f)
            val y0 = r.base
            val y1 = next.bottom
            if (y1 > y0) {
                val haze = hazeColor(p)
                shaded.shader = LinearGradient(0f, y0, 0f, y1, ColorMath.withAlpha(haze, 0f), ColorMath.withAlpha(haze, hazeA), Shader.TileMode.CLAMP)
                canvas.drawRect(left, y0, right, clipBottom, shaded)
                shaded.shader = null
            }
        }
        canvas.restore()
    }

    /** Snow like icing on a cake: thick along the crest, with little drips down the slope. */
    private fun drawSnow(canvas: Canvas, r: SceneLayout.Ridge, p: ScenePalette, amount: Float) {
        val capH = (5f + 2.5f * r.index) * dp
        val step = 3 * dp
        val a = amount.coerceIn(0f, 1f)
        tmpPath.reset()
        var x = -layout.margin
        tmpPath.moveTo(x, layout.edge(r, x) - 0.6f * dp)
        while (x <= layout.w + layout.margin) {
            tmpPath.lineTo(x, layout.edge(r, x) - 0.6f * dp)
            x += step
        }
        x = layout.w + layout.margin
        while (x >= -layout.margin) {
            val drip = max(0f, sin(x / (6.5f * dp) + r.index * 2.1f)).let { it * it * it }
            tmpPath.lineTo(x, layout.edge(r, x) + capH * (0.45f + 0.9f * drip))
            x -= step
        }
        tmpPath.close()
        fill.color = ColorMath.withAlpha(p.snowCap, a * (0.82f + 0.08f * r.index))
        canvas.drawPath(tmpPath, fill)
    }

    // ============================================================================================
    // Particles: everything that moves in front of the landscape. Redrawn every frame.
    // ============================================================================================

    fun drawParticles(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val wind = s.windX + o.gust
        if (s.fog > 0.03f) drawFog(canvas, s, p, o)
        if (o.particles) {
            drawBirds(canvas, s, p, o, wind)
            drawMotes(canvas, s, p, o)
            if (s.drizzle > 0.01f) drawDrizzle(canvas, s, p, o, wind)
            if (s.rain > 0.01f) drawRain(canvas, s, p, o, wind)
            if (s.hail > 0.01f) drawHail(canvas, s, p, o, wind)
            if (s.snow > 0.01f) drawSnowfall(canvas, s, p, o, wind)
            if (s.rain > 0.15f && o.landscape) drawSplashes(canvas, s, p, o)
            drawFireflies(canvas, s, p, o)
            if (abs(wind) > 6.5f && s.snow < 0.2f) drawWind(canvas, s, p, o, wind)
        }
        if (o.flash > 0.12f || o.staticBolt) drawBolt(canvas, o)
        if (o.flash > 0f) canvas.drawColor(ColorMath.withAlpha(0xFFF4F2FF.toInt(), o.flash * 0.32f))
        if (o.glass && (s.rain + s.drizzle) > 0.2f) drawGlass(canvas, s, o)
        if (o.vignette > 0f) drawVignette(canvas, p, o.vignette)
    }

    private fun area() = (layout.w / dp) * (layout.h / dp)

    private fun ensureBuf(i: Int, size: Int): FloatArray {
        if (buf[i].size < size) buf[i] = FloatArray(size + 64)
        return buf[i]
    }

    private fun wrap(v: Float, span: Float): Float = ((v % span) + span) % span

    private val rainLen = floatArrayOf(9f, 15f, 24f)
    private val rainSpeed = floatArrayOf(430f, 660f, 950f)
    private val rainWidth = floatArrayOf(0.8f, 1.15f, 1.6f)
    private val rainAlpha = floatArrayOf(0.26f, 0.4f, 0.6f)
    private val layerShare = floatArrayOf(0.45f, 0.33f, 0.22f)

    private fun drawRain(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options, wind: Float) {
        val n = ((area() / 900f).coerceIn(40f, 440f) * s.rain.coerceIn(0f, 1.2f)).toInt()
        if (n <= 0) return
        val w = layout.w
        val h = layout.h
        val slant = (wind / 20f).coerceIn(-0.7f, 0.7f)
        for (layer in 0..2) {
            val count = (n * layerShare[layer]).toInt()
            val out = ensureBuf(layer, count * 4)
            val len = rainLen[layer] * dp * (0.8f + 0.4f * s.rain.coerceAtMost(1f))
            val speed = rainSpeed[layer] * dp
            val span = h + len * 2
            val spread = w + abs(slant) * h
            var k = 0
            for (j in 0 until count) {
                val i = layer * 1000 + j
                val y = wrap(rand(i, 603) * span + o.time * speed, span) - len
                val x0 = rand(i, 605) * spread - (if (slant > 0) slant * h else 0f)
                val x = x0 + y * slant
                out[k++] = x; out[k++] = y
                out[k++] = x + len * slant; out[k++] = y + len
            }
            lines.strokeWidth = rainWidth[layer] * dp
            lines.color = ColorMath.withAlpha(p.precip, rainAlpha[layer] * (0.75f + 0.25f * s.rain.coerceAtMost(1f)))
            if (k > 0) canvas.drawLines(out, 0, k, lines)
        }
    }

    private fun drawDrizzle(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options, wind: Float) {
        val n = ((area() / 650f).coerceIn(30f, 400f) * s.drizzle).toInt()
        if (n <= 0) return
        val out = ensureBuf(3, n * 4)
        val h = layout.h
        val slant = (wind / 24f).coerceIn(-0.5f, 0.5f)
        val len = 5 * dp
        val span = h + len * 2
        var k = 0
        for (j in 0 until n) {
            val depth = rand(j, 611)
            val y = wrap(rand(j, 613) * span + o.time * (220f + 140f * depth) * dp, span) - len
            val x = rand(j, 615) * (layout.w + abs(slant) * h) - (if (slant > 0) slant * h else 0f) + y * slant
            out[k++] = x; out[k++] = y; out[k++] = x + len * slant; out[k++] = y + len
        }
        lines.strokeWidth = 0.9f * dp
        lines.color = ColorMath.withAlpha(p.precip, 0.4f)
        canvas.drawLines(out, 0, k, lines)
    }

    private fun drawHail(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options, wind: Float) {
        val n = ((area() / 2400f).coerceIn(10f, 120f) * s.hail).toInt()
        if (n <= 0) return
        val out = ensureBuf(3, n * 2)
        val span = layout.h + 10 * dp
        val slant = (wind / 26f).coerceIn(-0.5f, 0.5f)
        var k = 0
        for (j in 0 until n) {
            val y = wrap(rand(j, 703) * span + o.time * (560f + 240f * rand(j, 701)) * dp, span) - 5 * dp
            out[k++] = rand(j, 705) * layout.w + y * slant
            out[k++] = y
        }
        lines.strokeWidth = 2.6f * dp
        lines.color = ColorMath.withAlpha(0xFFF4F8FF.toInt(), 0.92f)
        canvas.drawPoints(out, 0, k, lines)
    }

    private val snowSize = floatArrayOf(1.4f, 2.4f, 0f)
    private val snowSpeed = floatArrayOf(20f, 34f, 56f)
    private val snowAlpha = floatArrayOf(0.55f, 0.8f, 0.92f)

    private var flakeLines = FloatArray(0)
    private var flakeN = 0

    private fun drawSnowfall(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options, wind: Float) {
        val n = ((area() / 1300f).coerceIn(30f, 280f) * s.snow.coerceIn(0f, 1.2f)).toInt()
        if (n <= 0) return
        if (flakeLines.size < n * 12) flakeLines = FloatArray(n * 12 + 24)
        flakeN = 0
        val t = o.time
        val xw = layout.w + 24 * dp
        for (layer in 0..2) {
            val count = (n * layerShare[layer]).toInt()
            val speed = snowSpeed[layer] * dp
            val span = layout.h + 16 * dp
            val drift = wind * (1.2f + layer) * dp
            val out = ensureBuf(layer, count * 2)
            var k = 0
            for (j in 0 until count) {
                val i = layer * 1000 + j
                val y = wrap(rand(i, 803) * span + t * speed * (0.8f + 0.4f * rand(i, 801)), span) - 8 * dp
                val amp = (5f + rand(i, 805) * 10f) * dp
                val x = wrap(rand(i, 807) * xw + drift * t + sin(t * (0.5f + rand(i, 809) * 0.8f) + i) * amp, xw) - 12 * dp
                if (layer < 2) {
                    out[k++] = x; out[k++] = y
                } else if (j % 3 == 0) {
                    // A six-armed paper star, turning as it falls.
                    val r = (3.4f + rand(i, 811) * 2.4f) * dp * layout.propScale
                    val rot = Math.toRadians((t * (30f + rand(i, 813) * 40f) + i * 17f).toDouble()).toFloat()
                    for (arm in 0 until 3) {
                        val a = rot + arm * PI.toFloat() / 3f
                        val dx = cos(a) * r
                        val dy = sin(a) * r
                        flakeLines[flakeN++] = x - dx; flakeLines[flakeN++] = y - dy
                        flakeLines[flakeN++] = x + dx; flakeLines[flakeN++] = y + dy
                    }
                } else {
                    sprite.tint(p.precip, snowAlpha[2])
                    sprite.draw(canvas, SceneSprites.soft, x, y, (3.2f + rand(i, 811) * 3f) * dp)
                }
            }
            if (layer < 2 && k > 0) {
                lines.strokeWidth = snowSize[layer] * dp
                lines.color = ColorMath.withAlpha(p.precip, snowAlpha[layer])
                canvas.drawPoints(out, 0, k, lines)
            }
        }
        if (flakeN > 0) {
            lines.strokeWidth = 1.3f * dp * layout.propScale
            lines.color = ColorMath.withAlpha(p.precip, 0.95f)
            canvas.drawLines(flakeLines, 0, flakeN, lines)
        }
    }

    private fun drawSplashes(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val ground = layout.ridges.last()
        val n = ((layout.w / dp / 20f) * s.rain.coerceAtMost(1f)).toInt().coerceIn(0, 30)
        if (n == 0) return
        val out = ensureBuf(3, n * 8)
        val period = 0.5f
        var k = 0
        for (i in 0 until n) {
            val phase = o.time / period + rand(i, 901)
            val cycle = floor(phase).toInt()
            val ph = phase - cycle
            val x = rand(i * 31 + cycle, 903) * layout.w
            val y = layout.edge(ground, x - o.groundDx) + o.groundDy + 1.5f * dp
            val sz = (1.5f + ph * 4.5f) * dp
            out[k++] = x; out[k++] = y; out[k++] = x - sz * 0.9f; out[k++] = y - sz * (1.1f - ph * 0.6f)
            out[k++] = x; out[k++] = y; out[k++] = x + sz * 0.9f; out[k++] = y - sz * (1.1f - ph * 0.6f)
        }
        lines.strokeWidth = 1.1f * dp
        lines.color = ColorMath.withAlpha(p.precip, 0.5f)
        canvas.drawLines(out, 0, k, lines)
    }

    private fun drawFog(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val color = ColorMath.lerp(p.cloud, p.skyBottom, 0.35f)
        val hy = layout.horizonY
        for (i in 0 until 4) {
            val cy = hy + (i - 1.6f) * layout.depthD * 0.32f
            val bandH = layout.depthD * (0.34f + 0.12f * rand(i, 1001))
            val cx = layout.w * (0.5f + 0.35f * sin(o.time * 0.03f * (1 + i * 0.4f) + i * 1.7f))
            val rw = layout.w * (0.8f + 0.25f * rand(i, 1003))
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(rw / bandH, 1f)
            shaded.shader = fogShader(color)
            shaded.alpha = (s.fog * 0.8f * 255).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, bandH, shaded)
            canvas.restore()
        }
        shaded.shader = null
        shaded.alpha = 255
    }

    private var fogColor = 0
    private var fogUnit = 0f
    private var fogShaderCache: Shader? = null

    /** Radial fog puff of unit radius scaled at draw time; rebuilt only when the colour changes. */
    private fun fogShader(color: Int): Shader {
        val unit = layout.depthD * 0.46f
        if (fogShaderCache == null || fogColor != color || fogUnit != unit) {
            fogColor = color
            fogUnit = unit
            fogShaderCache = RadialGradient(0f, 0f, unit, intArrayOf(color, ColorMath.withAlpha(color, 0.5f), ColorMath.withAlpha(color, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        return fogShaderCache!!
    }

    private fun drawBirds(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options, wind: Float) {
        if (s.daylight < 0.55f || s.rain + s.snow + s.drizzle + s.thunder > 0.1f || abs(wind) > 10f || s.fog > 0.4f) return
        val period = 36f
        val cycle = floor(o.time / period).toInt()
        val ph = (o.time - cycle * period) / 17f
        if (ph !in 0f..1f) return
        val flock = 3 + (rand(cycle, 61) * 4).toInt()
        val dir = if (rand(cycle, 63) > 0.5f) 1f else -1f
        val span = layout.w + 160 * dp
        val headX = if (dir > 0) -80 * dp + span * ph else layout.w + 80 * dp - span * ph
        val headY = layout.horizonY * (0.18f + 0.32f * rand(cycle, 65)) + sin(o.time * 0.7f) * 6 * dp
        val size = (5f + 2.5f * rand(cycle, 67)) * dp
        val out = ensureBuf(3, flock * 8)
        var k = 0
        for (b in 0 until flock) {
            val side = if (b % 2 == 0) 1f else -1f
            val rank = (b + 1) / 2
            val bx = headX - dir * rank * 15 * dp + (rand(b, cycle + 69) - 0.5f) * 6 * dp
            val by = headY + side * rank * 8 * dp + sin(o.time * 1.3f + b) * 2 * dp
            val flap = sin(o.time * (7.5f + b * 0.4f) + b * 1.1f)
            val sz = size * (1f - rank * 0.07f)
            val tipY = by - sz * (0.2f + 0.5f * flap)
            out[k++] = bx; out[k++] = by; out[k++] = bx - sz; out[k++] = tipY
            out[k++] = bx; out[k++] = by; out[k++] = bx + sz; out[k++] = tipY
        }
        lines.strokeWidth = 1.4f * dp
        lines.color = ColorMath.withAlpha(ColorMath.darken(p.hillNear, 0.35f), 0.55f)
        canvas.drawLines(out, 0, k, lines)
    }

    private fun drawMotes(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val a = smoothstep(0.5f, 0.9f, s.daylight) * (1f - s.cloudCover).coerceIn(0f, 1f) * (1f - (s.rain + s.snow + s.drizzle).coerceIn(0f, 1f))
        if (a < 0.05f) return
        val n = (area() / 9000f).toInt().coerceIn(6, 26)
        val t = o.time
        val top = layout.horizonY + layout.depthD * 0.3f
        for (i in 0 until n) {
            val x = wrap(rand(i, 1201) * layout.w + t * (4f + 6f * rand(i, 1203)) * dp + sin(t * 0.3f + i) * 14 * dp, layout.w)
            val y = wrap(rand(i, 1205) * top - t * (5f + 5f * rand(i, 1207)) * dp, top)
            val tw = sin(t * (0.6f + rand(i, 1209)) + i * 2.3f)
            sprite.tint(p.sunRay, a * (0.18f + 0.3f * tw * tw))
            sprite.draw(canvas, SceneSprites.soft, x, y, (2.5f + 3.5f * rand(i, 1211)) * dp)
        }
    }

    private fun drawFireflies(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options) {
        val a = (1f - s.daylight * 2f).coerceIn(0f, 1f) * smoothstep(12f, 17f, s.temperature) *
            (1f - (s.rain + s.snow + s.drizzle + s.fog).coerceIn(0f, 1f)) * (1f - smoothstep(4f, 8f, abs(s.windX)))
        if (a < 0.05f || !o.landscape) return
        val n = (layout.w / dp / 28f).toInt().coerceIn(5, 16)
        val t = o.time
        // Over the middle hill and the meadow.
        val mid = layout.ridges[layout.ridges.size - 2]
        val near = layout.ridges.last()
        val y0 = mid.base - mid.amp
        val y1 = near.bottom + 10 * dp
        for (i in 0 until n) {
            val fx = rand(i, 1301) * layout.w + sin(t * (0.17f + 0.1f * rand(i, 1303)) + i) * 30 * dp
            val fy = y0 + rand(i, 1305) * (y1 - y0) + sin(t * (0.23f + 0.1f * rand(i, 1307)) + i * 2f) * 12 * dp
            val pulse = max(0f, sin(t * (1.1f + rand(i, 1309) * 0.8f) + i * 1.9f))
            sprite.tint(p.window, a * pulse * pulse)
            sprite.draw(canvas, SceneSprites.glow, fx + o.groundDx, fy + o.groundDy, 7 * dp)
        }
    }

    private val leafPath = Path().apply {
        moveTo(0f, -1f)
        quadTo(0.75f, -0.2f, 0f, 1f)
        quadTo(-0.75f, -0.2f, 0f, -1f)
        close()
    }

    private fun drawWind(canvas: Canvas, s: SceneState, p: ScenePalette, o: Options, wind: Float) {
        val t = o.time
        val dir = if (wind < 0) -1f else 1f
        val strength = smoothstep(6.5f, 14f, abs(wind))
        // Streaks of moving air.
        val period = 1.7f
        stroke.strokeWidth = 1.2f * dp
        for (i in 0 until 4) {
            val phase = t / period + rand(i, 1401)
            val cycle = floor(phase).toInt()
            val ph = phase - cycle
            val y = layout.horizonY * (0.2f + 0.7f * rand(i * 13 + cycle, 1403))
            val len = (60f + 70f * rand(i, 1405)) * dp
            val x = if (dir > 0) -len + ph * (layout.w + len * 2) else layout.w + len - ph * (layout.w + len * 2)
            stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.28f * strength * sin(ph * PI.toFloat()))
            tmpPath.reset()
            tmpPath.moveTo(x, y)
            tmpPath.cubicTo(x + dir * len * 0.33f, y - 5 * dp, x + dir * len * 0.66f, y + 5 * dp, x + dir * len, y)
            canvas.drawPath(tmpPath, stroke)
        }
        // Tumbling leaves when it isn't freezing.
        if (s.temperature < 2f) return
        val n = (layout.w / dp / 45f).toInt().coerceIn(4, 10)
        val autumn = ColorMath.lerp(p.accent, p.sunRay, 0.3f)
        for (i in 0 until n) {
            val speed = (40f + abs(wind) * 9f) * dp * (0.7f + 0.6f * rand(i, 1411))
            val xw = layout.w + 60 * dp
            val x = wrap(rand(i, 1413) * xw + dir * t * speed, xw) - 30 * dp
            val y = layout.horizonY + layout.depthD * (-0.4f + rand(i, 1415) * 0.9f) + sin(t * (1.1f + rand(i, 1417)) + i) * 18 * dp
            val size = (3.2f + 2f * rand(i, 1419)) * dp
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(t * (160f + 200f * rand(i, 1421)) * dir + i * 40f)
            canvas.scale(size, size * (0.55f + 0.45f * abs(sin(t * 3f + i))))
            fill.color = ColorMath.withAlpha(if (i % 3 == 0) p.tree else autumn, 0.85f * strength)
            canvas.drawPath(leafPath, fill)
            canvas.restore()
        }
    }

    private var boltSeedCached = Int.MIN_VALUE
    private var boltW = -1f
    private val boltPath = Path()

    private fun buildBolt(seed: Int) {
        boltSeedCached = seed
        boltW = layout.w
        boltPath.reset()
        val x0 = layout.w * (0.2f + rand(seed, 1101) * 0.6f)
        val y0 = layout.horizonY * 0.1f
        val y1 = layout.ridges.getOrNull(1)?.let { layout.edge(it, x0) } ?: layout.horizonY
        val seg = 10
        var x = x0
        var y = y0
        boltPath.moveTo(x, y)
        val xs = FloatArray(seg + 1)
        val ys = FloatArray(seg + 1)
        xs[0] = x; ys[0] = y
        for (i in 1..seg) {
            y += (y1 - y0) / seg
            x += (rand(seed + i, 1103) - 0.5f) * 30 * dp
            boltPath.lineTo(x, y)
            xs[i] = x; ys[i] = y
        }
        repeat(2) { b ->
            val from = 2 + (rand(seed + b, 1105) * (seg - 5)).toInt()
            var bx = xs[from]
            var by = ys[from]
            val dir = if (rand(seed + b, 1107) > 0.5f) 1f else -1f
            boltPath.moveTo(bx, by)
            for (i in 0 until 4) {
                bx += dir * (8f + rand(seed + b * 5 + i, 1109) * 14f) * dp
                by += (y1 - y0) / seg * 0.8f
                boltPath.lineTo(bx, by)
            }
        }
    }

    private fun drawBolt(canvas: Canvas, o: Options) {
        val strength = if (o.staticBolt) 1f else o.flash
        if (strength < 0.12f) return
        if (boltSeedCached != o.boltSeed || boltW != layout.w) buildBolt(o.boltSeed)
        stroke.color = ColorMath.withAlpha(0xFFB9C4FF.toInt(), 0.16f * strength)
        stroke.strokeWidth = 11 * dp
        canvas.drawPath(boltPath, stroke)
        stroke.color = ColorMath.withAlpha(0xFFE4E8FF.toInt(), 0.4f * strength)
        stroke.strokeWidth = 4.5f * dp
        canvas.drawPath(boltPath, stroke)
        stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), strength)
        stroke.strokeWidth = 1.8f * dp
        canvas.drawPath(boltPath, stroke)
    }

    /** Raindrops on the glass: they bead, sit a while, then run down and re-form elsewhere. */
    private fun drawGlass(canvas: Canvas, s: SceneState, o: Options) {
        val wet = (s.rain + s.drizzle * 0.6f).coerceIn(0f, 1f)
        val n = (4 + wet * 8).toInt()
        val t = o.time
        for (i in 0 until n) {
            val life = 6f + 5f * rand(i, 1501)
            val phase = t / life + rand(i, 1503)
            val cycle = floor(phase).toInt()
            val ph = phase - cycle
            val x = rand(i * 17 + cycle, 1505) * layout.w
            val y0 = rand(i * 17 + cycle, 1507) * layout.horizonY * 0.95f
            val r = (3.5f + 4.5f * rand(i * 17 + cycle, 1509)) * dp
            val grow = smoothstep(0f, 0.08f, ph)
            val slide = smoothstep(0.72f, 1f, ph)
            val y = y0 + slide * slide * layout.h * 0.35f
            val a = grow * (1f - smoothstep(0.9f, 1f, ph))
            if (slide > 0.01f) {
                lines.strokeWidth = r * 0.5f
                lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.1f * a)
                canvas.drawLine(x, y0, x, y - r, lines)
            }
            bitmapPaint.alpha = (a * 255).roundToInt()
            srcRect.set(0, 0, SceneSprites.drop.width, SceneSprites.drop.height)
            tmpRect.set(x - r, y - r * 1.05f, x + r, y + r * (1.05f + slide * 0.3f))
            canvas.drawBitmap(SceneSprites.drop, srcRect, tmpRect, bitmapPaint)
        }
        bitmapPaint.alpha = 255
    }

    /** A faint diagonal reflection on the diorama's glass (§2). */
    fun drawGlare(canvas: Canvas) {
        val w = layout.w
        val h = layout.h
        canvas.save()
        canvas.rotate(-30f, w * 0.3f, h * 0.2f)
        shaded.shader = LinearGradient(w * 0.05f, 0f, w * 0.45f, 0f, intArrayOf(0x00FFFFFF, 0x10FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-w, -h, w * 2, h * 2, shaded)
        shaded.shader = null
        canvas.restore()
    }

    private var vignetteKey = 0L
    private var vignetteShader: Shader? = null

    fun drawVignette(canvas: Canvas, p: ScenePalette, strength: Float) {
        val w = layout.w
        val h = layout.h
        val key = (w.toLong() shl 32) xor h.toLong() xor (p.shadow.toLong() shl 7)
        if (vignetteShader == null || key != vignetteKey) {
            vignetteKey = key
            vignetteShader = RadialGradient(
                w / 2, h * 0.42f, hypot(w, h) * 0.62f,
                intArrayOf(0, 0, ColorMath.scaleAlpha(p.shadow, 0.5f)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
        }
        shaded.shader = vignetteShader
        shaded.alpha = (strength.coerceIn(0f, 1f) * 255).roundToInt()
        canvas.drawRect(0f, 0f, w, h, shaded)
        shaded.shader = null
        shaded.alpha = 255
    }

    // ============================================================================================
    // Widget overlays
    // ============================================================================================

    /** A soft oval wash behind text: darker for light ink, paler for dark ink. */
    fun drawScrim(canvas: Canvas, rect: RectF, dark: Boolean) {
        val color = if (dark) 0x66101420 else 0x80FFFFFF.toInt()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rx = rect.width() * 0.75f + 24 * dp
        val ry = rect.height() * 0.8f + 18 * dp
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(rx / ry, 1f)
        shaded.shader = RadialGradient(0f, 0f, ry, intArrayOf(color, ColorMath.scaleAlpha(color, 0.55f), ColorMath.withAlpha(color, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, ry, shaded)
        shaded.shader = null
        canvas.restore()
    }

    private val panelPath = Path()

    private val paperPaint by lazy {
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            shader = android.graphics.BitmapShader(MaterialTextures.paper, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    /**
     * A sheet of cotton paper laid over the diorama (DESIGN_DOCTRINE §13): deckled edge, the
     * fibres of the paper, a level-2 shadow falling down-right, light along the top edge and,
     * on the first sheet, a strip of washi tape holding it.
     */
    fun drawPanel(canvas: Canvas, panel: Panel, p: ScenePalette) {
        val rect = panel.rect
        val r = (panel.radius * dp).coerceAtMost(min(rect.width(), rect.height()) / 2)
        deckle(panelPath, rect, r, (rect.left * 7 + rect.top * 13).toInt())

        // Shadow: a tight contact shadow and a soft wide one, both down and a little right.
        for (k in 5 downTo 1) {
            fill.color = ColorMath.scaleAlpha(p.shadow, if (k <= 2) 0.16f else 0.07f)
            canvas.save()
            canvas.translate(k * 0.3f * dp, k * 0.9f * dp)
            canvas.drawPath(panelPath, fill)
            canvas.restore()
        }
        canvas.save()
        canvas.clipPath(panelPath)
        fill.color = ColorMath.withAlpha(p.paper, panel.alpha)
        canvas.drawRect(rect, fill)
        // Fibres read strongly on dark paper; keep them a whisper there.
        paperPaint.alpha = (255 * panel.alpha * if (p.isDarkPaper) 0.45f else 0.9f).roundToInt()
        canvas.drawRect(rect, paperPaint)
        // The sheet catches the light at the top and turns away from it at the bottom.
        shaded.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0x14FFFFFF, 0x0F000000, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, shaded)
        shaded.shader = null
        canvas.restore()

        canvas.save()
        canvas.clipRect(rect.left - dp, rect.top - 2 * dp, rect.right + dp, rect.top + 1.5f * dp)
        stroke.strokeWidth = 1.2f * dp
        stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.55f)
        canvas.drawPath(panelPath, stroke)
        canvas.restore()

        if (panel.tape) tape(canvas, rect.left + min(28 * dp, rect.width() * 0.2f), rect.top, p.tape, -7f)
    }

    /** Rounded rectangle with edges that wobble every 9 dp, like torn cotton paper. */
    private fun deckle(path: Path, rect: RectF, r: Float, seed: Int) {
        path.reset()
        val j = 0.8f * dp
        val step = 9 * dp
        var i = 0
        fun wob() = (hash(i++, seed) - 0.5f) * 2f * j
        path.moveTo(rect.left + r, rect.top + wob())
        var x = rect.left + r
        while (x < rect.right - r) { x = min(x + step, rect.right - r); path.lineTo(x, rect.top + wob()) }
        path.quadTo(rect.right, rect.top, rect.right, rect.top + r)
        var y = rect.top + r
        while (y < rect.bottom - r) { y = min(y + step, rect.bottom - r); path.lineTo(rect.right + wob(), y) }
        path.quadTo(rect.right, rect.bottom, rect.right - r, rect.bottom)
        x = rect.right - r
        while (x > rect.left + r) { x = max(x - step, rect.left + r); path.lineTo(x, rect.bottom + wob()) }
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - r)
        y = rect.bottom - r
        while (y > rect.top + r) { y = max(y - step, rect.top + r); path.lineTo(rect.left + wob(), y) }
        path.quadTo(rect.left, rect.top, rect.left + r, rect.top)
        path.close()
    }

    private fun hash(i: Int, s: Int): Float {
        var v = i * 374_761_393 + s * 668_265_263
        v = (v xor (v ushr 13)) * 1_274_126_177
        v = v xor (v ushr 16)
        return (v and 0xFFFFFF) / 16_777_216f
    }

    private val tapePath = Path()

    /** A strip of washi tape centred on (x, y): translucent, torn at both ends, dotted. */
    fun tape(canvas: Canvas, x: Float, y: Float, color: Int, angle: Float) {
        val w = 44 * dp
        val h = 13 * dp
        val tooth = 1.5f * dp
        tapePath.reset()
        tapePath.moveTo(-w / 2, -h / 2)
        tapePath.lineTo(w / 2, -h / 2)
        for (k in 1..5) tapePath.lineTo(w / 2 + if (k % 2 == 1) tooth else 0f, -h / 2 + h * k / 5)
        tapePath.lineTo(-w / 2, h / 2)
        for (k in 4 downTo 0) tapePath.lineTo(-w / 2 + if (k % 2 == 1) -tooth else 0f, -h / 2 + h * k / 5)
        tapePath.close()
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        fill.color = 0x1F2A1C10
        canvas.save(); canvas.translate(0.5f * dp, 1f * dp); canvas.drawPath(tapePath, fill); canvas.restore()
        fill.color = ColorMath.withAlpha(color, 0.82f)
        canvas.drawPath(tapePath, fill)
        canvas.clipPath(tapePath)
        fill.color = 0x66FFFFFF
        var dx = -w / 2 + 4 * dp
        while (dx < w / 2) {
            canvas.drawCircle(dx, -h * 0.2f, 1.2f * dp, fill)
            canvas.drawCircle(dx + 3 * dp, h * 0.22f, 1.2f * dp, fill)
            dx += 6 * dp
        }
        paperPaint.alpha = 80
        canvas.drawRect(-w, -h, w, h, paperPaint)
        canvas.restore()
    }

    fun drawChart(canvas: Canvas, chart: Chart, p: ScenePalette) {
        val n = chart.pointsY.size
        if (n == 0) return
        val rect = chart.rect
        val colW = rect.width() / n
        chart.bars?.let { bars ->
            val maxH = rect.bottom - chart.barsTop
            for (i in bars.indices) {
                val v = bars[i].coerceIn(0f, 1f)
                if (v < 0.3f) continue
                val cx = rect.left + colW * (i + 0.5f)
                val bw = min(colW * 0.3f, 7 * dp)
                val bh = max(3 * dp, maxH * v)
                tmpRect.set(cx - bw / 2, rect.bottom - bh, cx + bw / 2, rect.bottom)
                fill.color = ColorMath.withAlpha(p.precip, 0.25f + 0.4f * v)
                canvas.drawRoundRect(tmpRect, bw / 2, bw / 2, fill)
            }
        }
        if (n < 2) return
        tmpPath.reset()
        val xs = FloatArray(n) { rect.left + colW * (it + 0.5f) }
        val ys = chart.pointsY
        tmpPath.moveTo(xs[0], ys[0])
        for (i in 0 until n - 1) {
            // Catmull-Rom → cubic Bézier for a smooth line through every point.
            val x0 = xs[max(i - 1, 0)]; val y0 = ys[max(i - 1, 0)]
            val x3 = xs[min(i + 2, n - 1)]; val y3 = ys[min(i + 2, n - 1)]
            val c1x = xs[i] + (xs[i + 1] - x0) / 6f; val c1y = ys[i] + (ys[i + 1] - y0) / 6f
            val c2x = xs[i + 1] - (x3 - xs[i]) / 6f; val c2y = ys[i + 1] - (y3 - ys[i]) / 6f
            tmpPath.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1])
        }
        val lowest = ys.max()
        val washPath = Path(tmpPath).apply {
            lineTo(xs.last(), lowest + 14 * dp)
            lineTo(xs.first(), lowest + 14 * dp)
            close()
        }
        shaded.shader = LinearGradient(0f, ys.min(), 0f, lowest + 14 * dp, ColorMath.withAlpha(p.accent, 0.2f), ColorMath.withAlpha(p.accent, 0f), Shader.TileMode.CLAMP)
        canvas.drawPath(washPath, shaded)
        shaded.shader = null
        stroke.strokeWidth = 2f * dp
        stroke.color = ColorMath.withAlpha(p.accent, 0.9f)
        canvas.drawPath(tmpPath, stroke)
        for (i in 0 until n) {
            fill.color = p.paper
            canvas.drawCircle(xs[i], ys[i], 2.6f * dp, fill)
            stroke.strokeWidth = 1.5f * dp
            canvas.drawCircle(xs[i], ys[i], 2.6f * dp, stroke)
        }
    }

    companion object {
        /** Renders a static frame into a new bitmap at [scale] px per dp. */
        fun renderBitmap(
            widthDp: Float,
            heightDp: Float,
            scale: Float,
            state: SceneState,
            palette: ScenePalette,
            options: (Float) -> Options,
        ): Bitmap {
            val w = (widthDp * scale).roundToInt().coerceAtLeast(1)
            val h = (heightDp * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val renderer = PaperSceneRenderer(scale)
            renderer.draw(Canvas(bmp), w.toFloat(), h.toFloat(), state, palette, options(scale))
            return bmp
        }

        /** Largest px-per-dp scale that keeps a bitmap under [maxPixels]. */
        fun scaleFor(widthDp: Float, heightDp: Float, density: Float, maxPixels: Int): Float {
            val area = max(1f, widthDp * heightDp)
            return min(density, sqrt(maxPixels / area)).coerceAtLeast(0.75f)
        }
    }
}
