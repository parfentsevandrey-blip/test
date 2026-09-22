package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
 * Paints the Papersky diorama — layered cut-paper hills, a village, drifting clouds, sun or moon
 * and weather — onto a plain [android.graphics.Canvas].
 *
 * The same renderer draws the living scene in the app (hardware canvas, every frame, with
 * [Options.time] advancing) and the frozen frame behind every widget (software bitmap canvas).
 * It therefore sticks to primitives that behave identically in both pipelines.
 */
class PaperSceneRenderer(private val density: Float) {

    data class Panel(val rect: RectF, val tape: Boolean = false, val alpha: Float = 0.9f, val radius: Float = 12f)

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
        val skyShader: Shader? = null,
        val panels: List<Panel> = emptyList(),
        val charts: List<Chart> = emptyList(),
        /** Fewer, bolder details for tiny scenes, 0..1. */
        val detail: Float = 1f,
        val vignette: Float = 1f,
        /** Draw the landscape (hills, village, trees). Tiny widgets may drop it. */
        val landscape: Boolean = true,
    )

    private val dp = density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val shadowed = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rainBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rainFront = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val gradient = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint().apply { shader = PaperGrain.shader(); blendMode = BlendMode.OVERLAY }
    private val tmpPath = Path()
    private val tmpRect = RectF()

    private var rainPoints = FloatArray(0)

    // --- Layout cache, rebuilt when size or seed changes -------------------------------------
    private var cacheW = -1f
    private var cacheH = -1f
    private var cacheSeed = Int.MIN_VALUE
    private var cacheHorizon = -1f
    private var horizonY = 0f
    private val hills = Array(3) { Path() }
    private val caps = Array(3) { Path() }
    private var clouds: List<CloudShape> = emptyList()
    private var houses: List<House> = emptyList()
    private var trees: List<Tree> = emptyList()
    private val blanket = Path()
    private var blanketPeriod = 1f

    private class CloudShape(val path: Path, val width: Float, val height: Float, val x0: Float, val y: Float, val depth: Float)
    private class House(val x: Float, val w: Float, val h: Float, val roofH: Float, val twoWindows: Boolean, val chimney: Boolean)
    private class Tree(val x: Float, val h: Float, val pine: Boolean, val tone: Float)

    private fun wDp(w: Float) = w / dp
    private fun sizeScale(w: Float, h: Float) = (min(w, h) / (dp * 220f)).coerceIn(0.62f, 1.25f)

    fun horizonFor(w: Float, h: Float, o: Options): Float = when {
        !o.horizon.isNaN() -> o.horizon
        h > w * 1.5f -> 0.7f
        w > h * 2.4f -> 0.6f
        else -> 0.64f
    }

    private fun ensureLayout(w: Float, h: Float, seed: Int, horizonFrac: Float, detail: Float) {
        if (w == cacheW && h == cacheH && seed == cacheSeed && horizonFrac == cacheHorizon) return
        cacheW = w; cacheH = h; cacheSeed = seed; cacheHorizon = horizonFrac
        horizonY = h * horizonFrac
        val margin = 28 * dp
        for (layer in 0..2) {
            val path = hills[layer]
            path.reset()
            path.moveTo(-margin, h + 20 * dp)
            var x = -margin
            val step = 3 * dp
            while (x <= w + margin) {
                path.lineTo(x, ridge(layer, x, w, h, seed))
                x += step
            }
            path.lineTo(w + margin, ridge(layer, w + margin, w, h, seed))
            path.lineTo(w + margin, h + 20 * dp)
            path.close()

            // "Icing" snow cap following the ridge with little drips.
            val cap = caps[layer]
            cap.reset()
            val capH = (5f + layer * 1.5f) * dp * sizeScale(w, h)
            x = -margin
            cap.moveTo(x, ridge(layer, x, w, h, seed) - 0.5f * dp)
            while (x <= w + margin) {
                cap.lineTo(x, ridge(layer, x, w, h, seed) - 0.5f * dp)
                x += step
            }
            x = w + margin
            while (x >= -margin) {
                val drip = max(0f, sin(x / (6.5f * dp) + seed % 7)).let { it * it * it }
                cap.lineTo(x, ridge(layer, x, w, h, seed) + capH * (0.45f + 0.9f * drip))
                x -= step
            }
            cap.close()
        }

        val s = sizeScale(w, h)
        val widthDp = wDp(w)

        val maxClouds = (widthDp / 64f).roundToInt().coerceIn(3, 12)
        clouds = List(maxClouds) { i ->
            val depth = 0.3f + rand(i, seed + 101) * 0.7f
            val cw = (48f + rand(i, seed + 103) * 62f) * dp * s * (0.7f + 0.45f * depth)
            val ch = cw * (0.36f + rand(i, seed + 107) * 0.12f)
            val y = (0.1f + rand(i, seed + 109) * 0.42f) * horizonY
            CloudShape(cloudPath(cw, ch, i + seed), cw, ch, rand(i, seed + 113) * (w + cw), y, depth)
        }.sortedBy { it.depth }

        val nHouses = (widthDp / 92f * detail).roundToInt().coerceIn(1, 7)
        houses = List(nHouses) { i ->
            val slot = (i + 0.5f + (rand(i, seed + 201) - 0.5f) * 0.55f) / nHouses
            val hw = (14f + rand(i, seed + 203) * 9f) * dp * s
            House(
                x = slot * w,
                w = hw,
                h = hw * (0.72f + rand(i, seed + 205) * 0.25f),
                roofH = hw * (0.48f + rand(i, seed + 207) * 0.2f),
                twoWindows = hw > 18 * dp && rand(i, seed + 209) > 0.4f,
                chimney = rand(i, seed + 211) > 0.25f,
            )
        }

        val nTrees = (widthDp / 34f * detail).roundToInt().coerceIn(2, 26)
        trees = List(nTrees) { i ->
            val slot = (i + 0.5f + (rand(i, seed + 301) - 0.5f) * 0.8f) / nTrees
            Tree(
                x = slot * w,
                h = (15f + rand(i, seed + 303) * 16f) * dp * s,
                pine = rand(i, seed + 305) < 0.62f,
                tone = rand(i, seed + 307),
            )
        }

        // Overcast blanket: a scalloped band across the top of the sky.
        blanket.reset()
        val scallop = 30 * dp * s
        blanketPeriod = scallop * 2
        val bottom = horizonY * 0.2f
        blanket.moveTo(-blanketPeriod, -10 * dp)
        var bx = -blanketPeriod
        var k = 0
        blanket.lineTo(bx, bottom)
        while (bx < w + blanketPeriod * 2) {
            val r = scallop * (0.75f + 0.5f * rand(k++, seed + 401))
            blanket.quadTo(bx + r * 0.5f, bottom + r * 0.9f, bx + r, bottom)
            bx += r
        }
        blanket.lineTo(bx, -10 * dp)
        blanket.close()
    }

    /** Ridge height of hill [layer] at x; smooth sum of seeded sines, wavelength in dp. */
    private fun ridge(layer: Int, x: Float, w: Float, h: Float, seed: Int): Float {
        val below = h - horizonY
        val base = when (layer) {
            0 -> horizonY - min(h * 0.075f, 46 * dp)
            1 -> horizonY
            else -> horizonY + below * 0.42f
        }
        val amp = when (layer) {
            0 -> min(h * 0.06f, 34 * dp)
            1 -> min(h * 0.045f, 26 * dp)
            else -> min(h * 0.038f, 20 * dp)
        }
        val xd = x / (dp * 150f)
        val f = 1f + layer * 0.35f
        val p1 = rand(seed, layer * 3 + 1) * 6.283f
        val p2 = rand(seed, layer * 3 + 2) * 6.283f
        val p3 = rand(seed, layer * 3 + 3) * 6.283f
        val n = sin(xd * 0.8f * f + p1) * 0.55f + sin(xd * 1.9f * f + p2) * 0.3f + sin(xd * 4.3f * f + p3) * 0.15f
        return base - n * amp
    }

    private fun cloudPath(cw: Float, ch: Float, salt: Int): Path {
        val bumps = Path()
        val n = 3 + (rand(salt, 17) * 2).toInt()
        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            val r = ch * (0.42f + 0.5f * sin(t * PI.toFloat()) * (0.75f + 0.5f * rand(salt + i, 19)))
            bumps.addCircle(cw * (0.12f + t * 0.76f), -r * 0.62f, r, Path.Direction.CW)
        }
        val base = Path().apply { addRoundRect(RectF(0f, -ch * 0.5f, cw, 0f), ch * 0.25f, ch * 0.25f, Path.Direction.CW) }
        base.op(bumps, Path.Op.UNION)
        // Flat paper-cut bottom.
        val clip = Path().apply { addRect(-cw, -ch * 3, cw * 2, 0f, Path.Direction.CW) }
        base.op(clip, Path.Op.INTERSECT)
        return base
    }

    // ------------------------------------------------------------------------------------------

    fun draw(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, o: Options = Options()) {
        if (w <= 1f || h <= 1f) return
        val horizonFrac = horizonFor(w, h, o)
        ensureLayout(w, h, s.seed, horizonFrac, o.detail)
        val t = o.time
        val wind = s.windX + o.gust

        drawSky(canvas, w, h, s, p, o)
        drawStars(canvas, w, s, p, t)
        if (s.rainbow > 0.01f) drawRainbow(canvas, w, h, s.rainbow)
        drawSunAndMoon(canvas, w, h, s, p, o)
        drawClouds(canvas, w, h, s, p, t, wind)
        if (o.flash > 0f || o.staticBolt) drawBolt(canvas, w, h, o)

        if (o.landscape) {
            val px = o.parallaxX * 10 * dp
            val py = o.parallaxY * 6 * dp
            drawHill(canvas, 0, p.hillFar, p, s, px * 0.3f, py * 0.3f, h)
            canvas.save()
            canvas.translate(px * 0.6f, py * 0.6f)
            drawHill(canvas, 1, p.hillMid, p, s, 0f, 0f, h)
            drawVillage(canvas, w, h, s, p, t, wind)
            canvas.restore()
            drawPrecipitation(canvas, w, h, s, p, t, wind, front = false)
            canvas.save()
            canvas.translate(px, py)
            drawHill(canvas, 2, p.hillNear, p, s, 0f, 0f, h)
            drawTrees(canvas, w, h, s, p, t, wind)
            if (s.rain > 0.25f && t > 0f) drawSplashes(canvas, w, h, s, p, t)
            canvas.restore()
        } else {
            drawPrecipitation(canvas, w, h, s, p, t, wind, front = false)
        }
        drawPrecipitation(canvas, w, h, s, p, t, wind, front = true)
        if (s.fog > 0.02f) drawFog(canvas, w, h, s, p, t)
        if (o.flash > 0f) canvas.drawColor(ColorMath.withAlpha(0xFFFFFBEA.toInt(), o.flash * 0.33f))

        for (panel in o.panels) drawPanel(canvas, panel, p)
        for (chart in o.charts) drawChart(canvas, chart, p)

        if (o.vignette > 0f) drawVignette(canvas, w, h, p, o.vignette)
        if (o.grain > 0f) {
            grainPaint.alpha = (o.grain * 70).roundToInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, grainPaint)
        }
    }

    private fun drawSky(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, o: Options) {
        gradient.shader = o.skyShader ?: LinearGradient(
            0f, 0f, 0f, horizonY * 1.08f,
            intArrayOf(p.skyTop, ColorMath.lerp(p.skyTop, p.skyBottom, 0.55f), p.skyBottom),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, gradient)
        gradient.shader = null
    }

    private fun celestialRadius(w: Float, h: Float) = (min(w, h) * 0.085f).coerceIn(9 * dp, 30 * dp)

    private fun sunPosition(w: Float, h: Float, progress: Float, out: FloatArray) {
        val pr = progress.coerceIn(-0.08f, 1.08f)
        val arcH = min(horizonY * 0.72f, w * 0.55f)
        out[0] = w * (0.1f + 0.8f * pr)
        out[1] = horizonY - arcH * sin(PI.toFloat() * pr.coerceIn(0f, 1f)) + (if (pr < 0f || pr > 1f) abs(pr - pr.coerceIn(0f, 1f)) * arcH else 0f)
    }

    private val pos = FloatArray(2)

    /**
     * Where the visible sun (or, at night, the moon) is: [x, y, radius], or null. Lets the app
     * hit-test taps on it.
     */
    fun celestialAt(w: Float, h: Float, s: SceneState, o: Options = Options()): FloatArray? {
        ensureLayout(w, h, s.seed, horizonFor(w, h, o), o.detail)
        val r = celestialRadius(w, h)
        val out = FloatArray(2)
        return when {
            s.daylight > 0.35f && s.sunProgress in -0.05f..1.05f -> { sunPosition(w, h, s.sunProgress, out); floatArrayOf(out[0], out[1], r) }
            s.daylight < 0.6f -> { sunPosition(w, h, s.nightProgress.coerceIn(0.05f, 0.95f), out); floatArrayOf(out[0], out[1], r * 0.86f) }
            else -> null
        }
    }

    private fun drawSunAndMoon(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, o: Options) {
        val r = celestialRadius(w, h)
        val visibility = 1f - s.cloudCover * 0.55f - s.fog * 0.3f

        // Moon rides across the night.
        val moonAlpha = (1f - s.daylight * 1.7f).coerceIn(0f, 1f) * visibility.coerceIn(0.18f, 1f)
        if (moonAlpha > 0.01f) {
            sunPosition(w, h, s.nightProgress.coerceIn(0.05f, 0.95f), pos)
            drawGlow(canvas, pos[0], pos[1], r * 3.4f, ColorMath.scaleAlpha(p.glow, moonAlpha * 1.6f))
            drawMoon(canvas, pos[0], pos[1], r * 0.86f, s.moonPhase, p, moonAlpha)
        }

        val sunAlpha = smoothstep(0.05f, 0.45f, s.daylight) * visibility.coerceIn(0.3f, 1f)
        if (sunAlpha > 0.01f && s.sunProgress > -0.1f && s.sunProgress < 1.1f) {
            sunPosition(w, h, s.sunProgress, pos)
            val (x, y) = pos[0] to pos[1]
            drawGlow(canvas, x, y, r * 4.2f, ColorMath.scaleAlpha(p.glow, sunAlpha))
            // Rotating paper petals.
            val rot = o.time * 6f + o.sunSpin
            fill.color = ColorMath.scaleAlpha(p.sunRay, sunAlpha)
            val petals = 12
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rot)
            for (i in 0 until petals) {
                val long = i % 2 == 0
                val inner = r * 1.22f
                val outer = r * if (long) 1.78f else 1.52f
                val half = r * if (long) 0.17f else 0.13f
                tmpRect.set(-half, -outer, half, -inner)
                canvas.drawRoundRect(tmpRect, half, half, fill)
                canvas.rotate(360f / petals)
            }
            canvas.restore()
            shadowed.color = ColorMath.scaleAlpha(p.sun, sunAlpha)
            shadowed.setShadowLayer(5 * dp, 0f, 2 * dp, ColorMath.scaleAlpha(p.shadow, sunAlpha * 0.8f))
            canvas.drawCircle(x, y, r, shadowed)
            shadowed.clearShadowLayer()
            fill.color = ColorMath.withAlpha(ColorMath.lighten(p.sun, 0.35f), 0.55f * sunAlpha)
            canvas.drawCircle(x - r * 0.12f, y - r * 0.12f, r * 0.66f, fill)
        }
    }

    private fun drawMoon(canvas: Canvas, x: Float, y: Float, r: Float, phase: Float, p: ScenePalette, alpha: Float) {
        shadowed.color = ColorMath.scaleAlpha(p.moon, alpha)
        shadowed.setShadowLayer(6 * dp, 0f, 2 * dp, ColorMath.scaleAlpha(p.shadow, alpha))
        canvas.drawCircle(x, y, r, shadowed)
        shadowed.clearShadowLayer()

        // Craters.
        fill.color = ColorMath.withAlpha(ColorMath.darken(p.moon, 0.25f), 0.18f * alpha)
        canvas.drawCircle(x - r * 0.3f, y - r * 0.2f, r * 0.2f, fill)
        canvas.drawCircle(x + r * 0.25f, y + r * 0.3f, r * 0.14f, fill)
        canvas.drawCircle(x + r * 0.1f, y - r * 0.45f, r * 0.09f, fill)

        // Terminator: dark side as half-disc plus a half-ellipse.
        val k = cos(2 * PI * phase).toFloat() // 1 new, 0 quarter, -1 full
        if (k > -0.97f) {
            val waxing = phase < 0.5f
            tmpPath.reset()
            tmpRect.set(x - r, y - r, x + r, y + r)
            // Dark half: left when waxing, right when waning.
            tmpPath.arcTo(tmpRect, -90f, if (waxing) -180f else 180f, true)
            val ex = r * abs(k)
            tmpRect.set(x - ex, y - r, x + ex, y + r)
            // Crescent (k > 0): the shadow ellipse bulges into the lit side.
            val sweep = if (waxing) (if (k > 0) -180f else 180f) else (if (k > 0) 180f else -180f)
            tmpPath.arcTo(tmpRect, 90f, sweep, false)
            tmpPath.close()
            fill.color = ColorMath.withAlpha(ColorMath.lerp(p.skyTop, p.moon, 0.22f), 0.86f * alpha)
            canvas.drawPath(tmpPath, fill)
        }
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        if (ColorMath.a(color) == 0) return
        gradient.shader = RadialGradient(x, y, radius, intArrayOf(color, ColorMath.withAlpha(color, 0f)), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, gradient)
        gradient.shader = null
    }

    private fun drawStars(canvas: Canvas, w: Float, s: SceneState, p: ScenePalette, t: Float) {
        val alpha = (1f - s.daylight * 1.6f).coerceIn(0f, 1f) * (1f - s.cloudCover * 0.85f) * (1f - s.fog)
        if (alpha < 0.02f) return
        val n = ((w * horizonY) / (dp * dp * 950f)).roundToInt().coerceIn(6, 90)
        for (i in 0 until n) {
            val x = rand(i, 501) * w
            val y = rand(i, 503) * horizonY * 0.86f
            val tw = 0.55f + 0.45f * sin(t * (1.2f + rand(i, 505) * 2.4f) + i)
            val bright = rand(i, 507) > 0.84f
            fill.color = ColorMath.withAlpha(p.star, alpha * tw * if (bright) 1f else 0.7f)
            if (bright) {
                val r = 2.6f * dp
                tmpPath.reset()
                tmpPath.moveTo(x, y - r * 1.6f)
                tmpPath.quadTo(x, y, x + r * 1.6f, y)
                tmpPath.quadTo(x, y, x, y + r * 1.6f)
                tmpPath.quadTo(x, y, x - r * 1.6f, y)
                tmpPath.quadTo(x, y, x, y - r * 1.6f)
                canvas.drawPath(tmpPath, fill)
            } else {
                canvas.drawCircle(x, y, (0.7f + rand(i, 509) * 0.9f) * dp, fill)
            }
        }
    }

    private fun drawRainbow(canvas: Canvas, w: Float, h: Float, amount: Float) {
        val colors = intArrayOf(0xFFE8766B.toInt(), 0xFFF2A65E.toInt(), 0xFFF2D56B.toInt(), 0xFF8FC38B.toInt(), 0xFF7BA7D6.toInt(), 0xFF9C88C9.toInt())
        val cx = w * 0.32f
        val radius = min(w * 0.42f, horizonY * 0.95f)
        val band = radius * 0.045f
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = band * 0.92f
        colors.forEachIndexed { i, c ->
            stroke.color = ColorMath.withAlpha(c, 0.62f * amount)
            val r = radius - i * band
            tmpRect.set(cx - r, horizonY - r, cx + r, horizonY + r)
            canvas.drawArc(tmpRect, 180f, 180f, false, stroke)
        }
        stroke.strokeCap = Paint.Cap.ROUND
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, t: Float, wind: Float) {
        val cover = s.cloudCover
        val count = cover * clouds.size * 1.1f + if (s.rain + s.snow + s.drizzle > 0.05f) 1.5f else 0f
        val dir = if (wind < 0) -1f else 1f
        val speedBase = (3f + abs(wind) * 1.3f) * dp

        if (cover > 0.72f) {
            val a = smoothstep(0.72f, 1f, cover)
            val offset = (t * speedBase * 0.4f * dir) % blanketPeriod
            canvas.save()
            canvas.translate(offset, 0f)
            fill.color = ColorMath.withAlpha(p.cloudShade, a)
            canvas.save(); canvas.translate(blanketPeriod * 0.5f, horizonY * 0.08f); canvas.drawPath(blanket, fill); canvas.restore()
            shadowed.color = ColorMath.withAlpha(ColorMath.lerp(p.cloudShade, p.cloud, 0.6f), a)
            shadowed.setShadowLayer(6 * dp, 0f, 3 * dp, ColorMath.scaleAlpha(p.shadow, a * 0.8f))
            canvas.drawPath(blanket, shadowed)
            shadowed.clearShadowLayer()
            canvas.restore()
        }

        for ((i, c) in clouds.withIndex()) {
            val presence = (count - i).coerceIn(0f, 1f)
            if (presence <= 0.01f) continue
            val span = w + c.width * 2
            val x = ((c.x0 + t * speedBase * (0.35f + c.depth) * dir) % span + span) % span - c.width
            canvas.save()
            canvas.translate(x, c.y)
            fill.color = ColorMath.withAlpha(p.cloudShade, presence)
            canvas.save(); canvas.translate(c.width * 0.04f, c.height * 0.1f); canvas.drawPath(c.path, fill); canvas.restore()
            shadowed.color = ColorMath.withAlpha(ColorMath.lerp(p.cloudShade, p.cloud, 0.45f + 0.55f * c.depth), presence)
            shadowed.setShadowLayer(5 * dp, 0f, 2.5f * dp, ColorMath.scaleAlpha(p.shadow, presence * 0.7f))
            canvas.drawPath(c.path, shadowed)
            shadowed.clearShadowLayer()
            canvas.restore()
        }
    }

    private fun drawHill(canvas: Canvas, layer: Int, color: Int, p: ScenePalette, s: SceneState, dx: Float, dy: Float, h: Float) {
        canvas.save()
        canvas.translate(dx, dy)
        shadowed.color = color
        shadowed.setShadowLayer((7 + layer * 2) * dp, 0f, -1.2f * dp, p.shadow)
        canvas.drawPath(hills[layer], shadowed)
        shadowed.clearShadowLayer()
        // Soft light along the ridge, like a paper edge catching light.
        stroke.strokeWidth = 1.2f * dp
        stroke.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.12f)
        canvas.drawPath(hills[layer], stroke)
        if (s.snowGround > 0.02f && layer >= 0) {
            fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround * (0.8f + 0.2f * layer / 2f))
            canvas.drawPath(caps[layer], fill)
        }
        canvas.restore()
    }

    private fun drawVillage(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, t: Float, wind: Float) {
        val lit = (1f - s.daylight * 1.25f + s.cloudCover * 0.35f + (s.rain + s.snow) * 0.4f).coerceIn(0f, 1f)
        for ((i, house) in houses.withIndex()) {
            val gy = ridge(1, house.x, w, h, cacheSeed) + 3 * dp
            val left = house.x - house.w / 2
            val right = house.x + house.w / 2
            val top = gy - house.h
            // Walls.
            shadowed.color = p.house
            shadowed.setShadowLayer(4 * dp, 0f, 1.5f * dp, p.shadow)
            canvas.drawRect(left, top, right, gy + 6 * dp, shadowed)
            // Chimney behind the roof.
            val chX = left + house.w * 0.68f
            val chTop = top - house.roofH * 0.95f
            if (house.chimney) {
                fill.color = ColorMath.darken(p.roof, 0.18f)
                canvas.drawRect(chX, chTop, chX + house.w * 0.14f, top - house.roofH * 0.3f, fill)
            }
            // Roof.
            tmpPath.reset()
            tmpPath.moveTo(left - 2.5f * dp, top + 0.5f * dp)
            tmpPath.lineTo(house.x, top - house.roofH)
            tmpPath.lineTo(right + 2.5f * dp, top + 0.5f * dp)
            tmpPath.close()
            shadowed.color = p.roof
            canvas.drawPath(tmpPath, shadowed)
            shadowed.clearShadowLayer()
            if (s.snowGround > 0.05f) {
                tmpPath.reset()
                val sx = house.w * 0.36f
                tmpPath.moveTo(house.x - sx - 2 * dp, top - house.roofH * 0.42f)
                tmpPath.lineTo(house.x, top - house.roofH - 1 * dp)
                tmpPath.lineTo(house.x + sx + 2 * dp, top - house.roofH * 0.42f)
                tmpPath.quadTo(house.x, top - house.roofH * 0.55f, house.x - sx - 2 * dp, top - house.roofH * 0.42f)
                fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround)
                canvas.drawPath(tmpPath, fill)
            }
            // Windows, glowing when it's dark or dreary.
            val ws = house.w * 0.22f
            val wy = top + house.h * 0.3f
            val windowsX = if (house.twoWindows) floatArrayOf(house.x - house.w * 0.22f, house.x + house.w * 0.22f) else floatArrayOf(house.x)
            val flicker = 0.88f + 0.12f * sin(t * 2.7f + i * 1.3f)
            for (wx in windowsX) {
                if (lit > 0.05f) drawGlow(canvas, wx, wy + ws / 2, ws * 2.6f, ColorMath.withAlpha(p.window, 0.35f * lit * flicker))
                fill.color = ColorMath.lerp(ColorMath.darken(p.house, 0.45f), p.window, lit * flicker)
                canvas.drawRect(wx - ws / 2, wy, wx + ws / 2, wy + ws, fill)
            }
            // Smoke puffs on cold days.
            if (house.chimney && s.temperature < 14f) {
                val puffs = 5
                for (k in 0 until puffs) {
                    val ph = fract(t * 0.22f + k / puffs.toFloat() + i * 0.37f)
                    val px = chX + house.w * 0.07f + wind.coerceIn(-12f, 12f) * ph * 2.4f * dp + sin(ph * 7f + i) * 2 * dp
                    val py = chTop - ph * 34 * dp
                    fill.color = ColorMath.withAlpha(ColorMath.lerp(p.cloud, p.cloudShade, 0.3f), (1f - ph) * 0.55f)
                    canvas.drawCircle(px, py, (2.2f + ph * 6f) * dp * sizeScale(w, h), fill)
                }
            }
        }
    }

    private fun drawTrees(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, t: Float, wind: Float) {
        val swayBase = (1.2f + abs(wind) * 0.8f).coerceAtMost(11f)
        for ((i, tree) in trees.withIndex()) {
            val gy = ridge(2, tree.x, w, h, cacheSeed) + 4 * dp
            val sway = sin(t * (1.3f + (i % 5) * 0.12f) + i * 0.9f) * swayBase * 0.5f + wind.coerceIn(-14f, 14f) * 0.35f
            val color = ColorMath.lerp(p.tree, p.hillNear, 0.12f + tree.tone * 0.3f)
            canvas.save()
            canvas.rotate(sway, tree.x, gy)
            shadowed.color = color
            shadowed.setShadowLayer(3 * dp, 0f, 1.2f * dp, p.shadow)
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
                    canvas.drawPath(tmpPath, shadowed)
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
                canvas.drawCircle(tree.x, gy - tree.h * 0.62f, cr, shadowed)
                canvas.drawCircle(tree.x - cr * 0.55f, gy - tree.h * 0.5f, cr * 0.72f, shadowed)
                if (s.snowGround > 0.3f) {
                    fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround * 0.85f)
                    tmpRect.set(tree.x - cr * 0.8f, gy - tree.h * 0.62f - cr, tree.x + cr * 0.8f, gy - tree.h * 0.62f - cr * 0.2f)
                    canvas.drawArc(tmpRect, 180f, 180f, true, fill)
                }
            }
            shadowed.clearShadowLayer()
            canvas.restore()
        }
    }

    private fun drawPrecipitation(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, t: Float, wind: Float, front: Boolean) {
        val areaDp = (w / dp) * (h / dp)
        val slant = (wind / 22f).coerceIn(-0.65f, 0.65f)

        // Rain and drizzle as paper strokes, batched into one drawLines call per layer.
        val rainN = ((areaDp / 520f).coerceIn(24f, 460f) * s.rain).toInt()
        val drizzleN = ((areaDp / 380f).coerceIn(24f, 560f) * s.drizzle).toInt()
        val hailN = ((areaDp / 1500f).coerceIn(8f, 120f) * s.hail).toInt()
        val total = rainN + drizzleN
        if (total > 0) {
            if (rainPoints.size < total * 4) rainPoints = FloatArray(total * 4 + 64)
            var n = 0
            for (i in 0 until total) {
                val drizzle = i >= rainN
                val depth = rand(i, 601)
                if ((depth >= 0.5f) != front) continue
                val len = (if (drizzle) 6f else 13f + 9f * depth) * dp
                val speed = (if (drizzle) 260f else 480f + 260f * depth) * dp
                val span = h + len
                val y = ((rand(i, 603) * span + t * speed) % span + span) % span - len
                val x0 = rand(i, 605) * (w + abs(slant) * h) - if (slant > 0) slant * h else 0f
                val x = x0 + y * slant
                rainPoints[n++] = x; rainPoints[n++] = y
                rainPoints[n++] = x + len * slant; rainPoints[n++] = y + len
            }
            val paint = if (front) rainFront else rainBack
            paint.color = ColorMath.withAlpha(p.precip, if (front) 0.8f else 0.5f)
            paint.strokeWidth = (if (front) 2.1f else 1.4f) * dp
            if (n > 0) canvas.drawLines(rainPoints, 0, n, paint)
        }

        for (i in 0 until hailN) {
            val depth = rand(i, 701)
            if ((depth >= 0.5f) != front) continue
            val speed = (380f + 200f * depth) * dp
            val y = ((rand(i, 703) * h + t * speed) % (h + 8 * dp)) - 4 * dp
            val x = rand(i, 705) * w + y * slant
            fill.color = ColorMath.withAlpha(0xFFF4F8FF.toInt(), 0.9f)
            canvas.drawCircle(x, y, (1.4f + depth * 1.4f) * dp, fill)
        }

        val flakes = ((areaDp / 820f).coerceIn(16f, 280f) * s.snow).toInt()
        for (i in 0 until flakes) {
            val depth = rand(i, 801)
            if ((depth >= 0.5f) != front) continue
            val r = (1.2f + depth * 2.3f) * dp
            val speed = (26f + depth * 42f) * dp
            val span = h + r * 4
            val y = ((rand(i, 803) * span + t * speed) % span + span) % span - r * 2
            val amp = (5f + rand(i, 805) * 10f) * dp
            val drift = wind * 3f * dp * t * (0.4f + depth)
            val xw = w + 20 * dp
            val x = ((rand(i, 807) * xw + drift + sin(t * (0.6f + rand(i, 809) * 0.8f) + i) * amp) % xw + xw) % xw - 10 * dp
            if (i % 6 == 0 && r > 2.4f * dp) {
                drawSnowStar(canvas, x, y, r * 1.9f, t * (20f + depth * 40f) + i * 17f, p)
            } else {
                fill.color = ColorMath.withAlpha(ColorMath.darken(p.cloudShade, 0.15f), 0.35f)
                canvas.drawCircle(x, y + 0.8f * dp, r, fill)
                fill.color = ColorMath.withAlpha(p.precip, 0.95f)
                canvas.drawCircle(x, y, r, fill)
            }
        }
    }

    private fun drawSnowStar(canvas: Canvas, x: Float, y: Float, r: Float, rotation: Float, p: ScenePalette) {
        stroke.strokeWidth = r * 0.22f
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        for (pass in 0..1) {
            stroke.color = if (pass == 0) ColorMath.withAlpha(ColorMath.darken(p.cloudShade, 0.2f), 0.35f) else p.precip
            val off = if (pass == 0) 0.8f * dp else 0f
            for (k in 0 until 3) {
                canvas.drawLine(-r, off, r, off, stroke)
                canvas.rotate(60f)
            }
        }
        canvas.restore()
    }

    private fun drawSplashes(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, t: Float) {
        val n = (18 * s.rain).toInt()
        stroke.strokeWidth = 1.1f * dp
        for (i in 0 until n) {
            val ph = fract(t * 1.8f + rand(i, 901))
            val cycle = floor(t * 1.8f + rand(i, 901)).toInt()
            val x = rand(i * 31 + cycle, 903) * w
            val y = ridge(2, x, w, h, cacheSeed) + 1.5f * dp
            stroke.color = ColorMath.withAlpha(p.precip, (1f - ph) * 0.6f)
            val r = (1f + ph * 5f) * dp
            tmpRect.set(x - r, y - r * 0.45f, x + r, y + r * 0.45f)
            canvas.drawArc(tmpRect, 180f, 180f, false, stroke)
        }
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, t: Float) {
        val color = ColorMath.lerp(p.cloud, p.skyBottom, 0.35f)
        for (i in 0 until 4) {
            val cy = horizonY + (i - 1.3f) * h * 0.085f
            val bandH = h * (0.09f + 0.03f * rand(i, 1001))
            val cx = w * 0.5f + sin(t * 0.05f + i * 1.7f) * w * 0.12f
            val rw = w * (0.75f + 0.2f * rand(i, 1003))
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(rw / bandH, 1f)
            gradient.shader = RadialGradient(0f, 0f, bandH, intArrayOf(ColorMath.withAlpha(color, 0.75f * s.fog), ColorMath.withAlpha(color, 0f)), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, bandH, gradient)
            canvas.restore()
        }
        gradient.shader = LinearGradient(0f, horizonY * 0.3f, 0f, h, ColorMath.withAlpha(color, 0f), ColorMath.withAlpha(color, 0.5f * s.fog), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, gradient)
        gradient.shader = null
    }

    private fun drawBolt(canvas: Canvas, w: Float, h: Float, o: Options) {
        val strength = if (o.staticBolt) 1f else o.flash
        if (strength < 0.15f) return
        val seed = o.boltSeed
        val startX = w * (0.25f + rand(seed, 1101) * 0.5f)
        var x = startX
        var y = horizonY * 0.18f
        val endY = horizonY * 0.95f
        val seg = 6
        val xs = FloatArray(seg + 1)
        val ys = FloatArray(seg + 1)
        xs[0] = x; ys[0] = y
        for (i in 1..seg) {
            y += (endY - horizonY * 0.18f) / seg
            x += (rand(seed + i, 1103) - 0.5f) * 26 * dp
            xs[i] = x; ys[i] = y
        }
        val half = 3.2f * dp * sizeScale(w, h)
        tmpPath.reset()
        tmpPath.moveTo(xs[0] - half, ys[0])
        for (i in 1..seg) tmpPath.lineTo(xs[i] - half * (1f - i / (seg + 1f)), ys[i])
        for (i in seg downTo 0) tmpPath.lineTo(xs[i] + half * (1f - i / (seg + 1f)) + (if (i % 2 == 0) half else 0f), ys[i])
        tmpPath.close()
        shadowed.color = ColorMath.withAlpha(0xFFFFF0A0.toInt(), strength)
        shadowed.setShadowLayer(9 * dp, 0f, 0f, ColorMath.withAlpha(0xFFFFE27A.toInt(), strength))
        canvas.drawPath(tmpPath, shadowed)
        shadowed.clearShadowLayer()
    }

    private fun drawVignette(canvas: Canvas, w: Float, h: Float, p: ScenePalette, strength: Float) {
        val r = hypot(w, h) * 0.62f
        gradient.shader = RadialGradient(
            w / 2, h * 0.45f, r,
            intArrayOf(0, 0, ColorMath.scaleAlpha(p.shadow, 0.55f * strength)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, gradient)
        gradient.shader = null
    }

    // --- Widget overlays ---------------------------------------------------------------------

    private fun deckledRect(rect: RectF, radius: Float, salt: Int, out: Path) {
        out.reset()
        val r = radius.coerceAtMost(min(rect.width(), rect.height()) / 2)
        val jitter = 0.7f * dp
        val step = 7 * dp
        // Walk the perimeter clockwise with a tiny seeded wobble — torn/deckled paper.
        fun wob(i: Int) = (rand(i, salt) - 0.5f) * jitter * 2
        var i = 0
        out.moveTo(rect.left + r, rect.top + wob(i++))
        var x = rect.left + r
        while (x < rect.right - r) { x += step; out.lineTo(min(x, rect.right - r), rect.top + wob(i++)) }
        out.quadTo(rect.right, rect.top, rect.right, rect.top + r)
        var y = rect.top + r
        while (y < rect.bottom - r) { y += step; out.lineTo(rect.right + wob(i++), min(y, rect.bottom - r)) }
        out.quadTo(rect.right, rect.bottom, rect.right - r, rect.bottom)
        x = rect.right - r
        while (x > rect.left + r) { x -= step; out.lineTo(max(x, rect.left + r), rect.bottom + wob(i++)) }
        out.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - r)
        y = rect.bottom - r
        while (y > rect.top + r) { y -= step; out.lineTo(rect.left + wob(i++), max(y, rect.top + r)) }
        out.quadTo(rect.left, rect.top, rect.left + r, rect.top)
        out.close()
    }

    private val panelPath = Path()

    fun drawPanel(canvas: Canvas, panel: Panel, p: ScenePalette) {
        val rect = panel.rect
        deckledRect(rect, panel.radius * dp, (rect.left + rect.top * 3).toInt(), panelPath)
        shadowed.color = ColorMath.withAlpha(p.paper, panel.alpha)
        shadowed.setShadowLayer(7 * dp, 0f, 2.5f * dp, ColorMath.scaleAlpha(p.shadow, 0.9f))
        canvas.drawPath(panelPath, shadowed)
        shadowed.clearShadowLayer()
        canvas.save()
        canvas.clipPath(panelPath)
        grainPaint.alpha = 90
        canvas.drawRect(rect, grainPaint)
        canvas.restore()
        if (panel.tape) drawTape(canvas, rect.left + min(rect.width() * 0.12f, 30 * dp), rect.top, -7f, p)
    }

    /** A strip of washi tape holding a paper note; dotted pattern, torn ends. */
    fun drawTape(canvas: Canvas, cx: Float, cy: Float, angle: Float, p: ScenePalette, lengthDp: Float = 38f) {
        val len = lengthDp * dp
        val th = 11 * dp
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)
        tmpPath.reset()
        tmpPath.moveTo(-len / 2, -th / 2)
        tmpPath.lineTo(len / 2, -th / 2)
        var yy = -th / 2
        var k = 0
        while (yy < th / 2) { yy += th / 5; tmpPath.lineTo(len / 2 + if (k++ % 2 == 0) 1.6f * dp else 0f, min(yy, th / 2)) }
        tmpPath.lineTo(-len / 2, th / 2)
        while (yy > -th / 2) { yy -= th / 5; tmpPath.lineTo(-len / 2 - if (k++ % 2 == 0) 1.6f * dp else 0f, max(yy, -th / 2)) }
        tmpPath.close()
        fill.color = ColorMath.withAlpha(p.tape, 0.82f)
        canvas.drawPath(tmpPath, fill)
        fill.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.35f)
        var dx = -len / 2 + 4 * dp
        while (dx < len / 2 - 2 * dp) {
            canvas.drawCircle(dx, -th * 0.18f, 1.1f * dp, fill)
            canvas.drawCircle(dx + 3 * dp, th * 0.2f, 1.1f * dp, fill)
            dx += 6 * dp
        }
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
                val bw = min(colW * 0.34f, 9 * dp)
                val bh = max(3 * dp, maxH * v)
                tmpRect.set(cx - bw / 2, rect.bottom - bh, cx + bw / 2, rect.bottom)
                fill.color = ColorMath.withAlpha(p.precip, 0.22f + 0.4f * v)
                canvas.drawRoundRect(tmpRect, bw / 2, bw / 2, fill)
            }
        }
        if (n < 2) return
        tmpPath.reset()
        val xs = FloatArray(n) { rect.left + colW * (it + 0.5f) }
        val ys = chart.pointsY
        tmpPath.moveTo(xs[0], ys[0])
        for (i in 0 until n - 1) {
            // Catmull-Rom → cubic Bézier for a hand-drawn smooth line.
            val x0 = xs[max(i - 1, 0)]; val y0 = ys[max(i - 1, 0)]
            val x3 = xs[min(i + 2, n - 1)]; val y3 = ys[min(i + 2, n - 1)]
            val c1x = xs[i] + (xs[i + 1] - x0) / 6f; val c1y = ys[i] + (ys[i + 1] - y0) / 6f
            val c2x = xs[i + 1] - (x3 - xs[i]) / 6f; val c2y = ys[i + 1] - (y3 - ys[i]) / 6f
            tmpPath.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1])
        }
        stroke.strokeWidth = 2.2f * dp
        stroke.color = ColorMath.withAlpha(p.accent, 0.85f)
        canvas.drawPath(tmpPath, stroke)
        for (i in 0 until n) {
            fill.color = p.paper
            canvas.drawCircle(xs[i], ys[i], 2.8f * dp, fill)
            stroke.strokeWidth = 1.6f * dp
            canvas.drawCircle(xs[i], ys[i], 2.8f * dp, stroke)
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
