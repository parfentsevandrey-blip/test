package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A landscape painted into a bitmap the way a matte painter builds one: the sky and its light,
 * clouds lit from the sun, ridges one behind another paling into the air, fog lying in between,
 * water that mirrors it all, then the near things drawn crisp — and at the end the light
 * blooming, a vignette and a fine grain. Coordinates are dp over [w] × [h]; the pixels are
 * [pxPerDp] per dp. Soft things (clouds, fog, the Milky Way, the moon's face) are computed per
 * pixel at a lower resolution and drawn up; hard edges are paths.
 */
internal class Painting(val w: Float, val h: Float, pxPerDp: Float, seed: Int) {
    val widthPx: Int = max(2, (w * pxPerDp).roundToInt())
    val heightPx: Int = max(2, (h * pxPerDp).roundToInt())
    val kx = widthPx / w
    val ky = heightPx / h
    val bitmap: Bitmap = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bitmap).apply { scale(kx, ky) }
    val rnd = Random(seed)
    val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Where the light comes from, in dp: the painted sun or moon. Near things are lit from here. */
    var lightX = w / 2f
    var lightY = h * 0.2f

    /** The shared paint, reset to an anti-aliased, dithered fill. */
    fun pen(color: Int = 0xFF000000.toInt()): Paint {
        paint.reset()
        paint.flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        paint.color = color
        return paint
    }

    fun stroke(color: Int, width: Float, round: Boolean = true): Paint = pen(color).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        if (round) {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    /** Which way the light falls across a thing standing at [x]: -1 from the left … +1 from the right. */
    fun lightFrom(x: Float): Float = ((lightX - x) / (w * 0.5f)).coerceIn(-1f, 1f)

    // region Sky and lights

    fun sky(colors: IntArray, stops: FloatArray, to: Float = h) {
        val p = pen()
        p.shader = LinearGradient(0f, 0f, 0f, to, colors, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }

    /**
     * A soft light: [color] at [strength] falling off smoothly to nothing at [radius] (a Gaussian,
     * not a cone), squashed vertically by [squash]. Screened on, so it brightens and never greys.
     */
    fun glow(x: Float, y: Float, radius: Float, color: Int, strength: Float, squash: Float = 1f, mode: BlendMode = BlendMode.SCREEN) {
        if (radius <= 0f || strength <= 0f) return
        val n = 12
        val colors = IntArray(n) { i ->
            val t = i / (n - 1f)
            Tone.alpha(color, strength * exp(-4.6f * t * t) * (1f - t))
        }
        val stops = FloatArray(n) { it / (n - 1f) }
        val p = pen()
        p.shader = RadialGradient(0f, 0f, radius, colors, stops, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(1f, squash); postTranslate(x, y) })
        }
        p.blendMode = mode
        canvas.drawRect(x - radius, y - radius * squash, x + radius, y + radius * squash, p)
    }

    /**
     * The sun: its disc, the bloom round it, the wide glow it spreads through the air and a faint
     * anamorphic streak, as a lens sees it. [power] 0..1 dims it behind haze.
     */
    fun sun(x: Float, y: Float, r: Float, color: Int, power: Float, streak: Float = 0.35f) {
        lightX = x
        lightY = y
        glow(x, y, r * 30f, color, 0.42f * power, squash = 0.8f)
        glow(x, y, r * 11f, color, 0.55f * power)
        glow(x, y, r * 4f, Tone.mix(color, 0xFFFFFFFF.toInt(), 0.5f), 0.9f * power)
        val p = pen()
        p.shader = RadialGradient(x, y, r, intArrayOf(0xFFFFFFFF.toInt(), Tone.mix(color, 0xFFFFFFFF.toInt(), 0.75f), Tone.alpha(color, 0.85f)), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, p)
        if (streak > 0f) glow(x, y, w * 0.55f, Tone.mix(color, 0xFFFFFFFF.toInt(), 0.4f), streak * power, squash = r * 0.9f / (w * 0.55f))
    }

    /**
     * The moon's face per pixel: seas and highlands, darkened towards the limb, lit from the side
     * by [phase] (1 full, 0.25 a thin crescent lit on the right), with its halo in the air.
     */
    fun moon(x: Float, y: Float, r: Float, phase: Float, tone: Int, halo: Int, haloStrength: Float) {
        lightX = x
        lightY = y
        glow(x, y, r * 16f, halo, 0.22f * haloStrength)
        glow(x, y, r * 5f, halo, 0.38f * haloStrength)
        // The sun's direction for this phase: from behind at new moon, from the viewer at full.
        val angle = (1f - phase.coerceIn(0f, 1f)) * PI.toFloat()
        val lx = sin(angle)
        val lz = cos(angle)
        val seas = Tone.shade(tone, -0.28f)
        field(RectF(x - r - 1f, y - r - 1f, x + r + 1f, y + r + 1f), max(kx, ky)) { px, py ->
            val nx = (px - x) / r
            val ny = (py - y) / r
            val d2 = nx * nx + ny * ny
            if (d2 > 1.08f) return@field 0
            val edge = ((1f - sqrt(d2)) * r * kx + 0.5f).coerceIn(0f, 1f)
            val nz = sqrt(max(0f, 1f - d2))
            val lit = Tone.smooth(-0.08f, 0.12f, nx * lx + nz * lz)
            val maria = Tone.smooth(0.52f, 0.66f, Noise.fbm(nx * 1.6f + 3f, ny * 1.6f + 1f, 71, 4))
            val craters = Noise.fbm(nx * 7f, ny * 7f, 72, 3)
            var c = Tone.mix(tone, seas, maria * 0.75f)
            c = Tone.shade(c, (craters - 0.5f) * 0.25f)
            c = Tone.shade(c, -(1f - nz) * 0.35f)
            val earthshine = 0.1f
            val light = earthshine + (1f - earthshine) * lit
            Tone.alpha(c, edge * light)
        }
    }

    /**
     * Stars down to [bottom], fewer and dimmer towards it: a dust of faint ones, a few bright ones
     * with a glow and the four short spikes of a lens, some warm, some blue.
     */
    fun stars(bottom: Float, count: Int, seed: Int, brightness: Float = 1f) {
        val r = Random(seed)
        val tints = intArrayOf(0xFFFFFFFF.toInt(), 0xFFDDE6FF.toInt(), 0xFFFFF0D8.toInt(), 0xFFCFDBFF.toInt())
        val p = pen()
        repeat(count) {
            val x = r.nextFloat() * w
            val y = r.nextFloat().pow(1.35f) * bottom
            val fade = 1f - Tone.smooth(bottom * 0.55f, bottom, y)
            val mag = r.nextFloat().pow(3.2f)
            val size = 0.22f + mag * 0.95f
            val a = (0.25f + 0.75f * r.nextFloat()) * fade * brightness
            val tint = tints[r.nextInt(tints.size)]
            p.shader = null
            p.color = Tone.alpha(tint, a)
            canvas.drawCircle(x, y, size, p)
            if (mag > 0.55f) {
                glow(x, y, size * 7f, tint, 0.35f * a)
                val s = stroke(Tone.alpha(tint, 0.5f * a), 0.18f)
                val l = size * (3f + mag * 5f)
                canvas.drawLine(x - l, y, x + l, y, s)
                canvas.drawLine(x, y - l, x, y + l, s)
                pen()
            }
        }
    }

    /**
     * The Milky Way across the sky from ([x0], [y0]) to ([x1], [y1]): a glowing band [width] wide,
     * clotted with star clouds and split by dark lanes of dust, thick with faint stars.
     */
    fun milkyWay(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, bottom: Float, strength: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val core = 0xFFF2E6FF.toInt()
        val outer = 0xFFB8C4FF.toInt()
        field(RectF(0f, 0f, w, bottom), max(kx, ky) * 0.32f) { px, py ->
            val along = (px - x0) * ux + (py - y0) * uy
            val across = -(px - x0) * uy + (py - y0) * ux
            val warp = (Noise.fbm(along / (width * 1.3f), 3.1f, 81, 3) - 0.5f) * width * 0.9f
            val d = (across - warp) / width
            val band = exp(-d * d * 2.2f)
            if (band < 0.01f) return@field 0
            val clouds = Noise.fbm(along / (width * 0.35f), across / (width * 0.35f), 82, 5)
            val lanes = Noise.ridged(along / (width * 0.8f), across / (width * 0.5f) + 5f, 83, 4)
            val dust = Tone.smooth(0.55f, 0.9f, lanes) * exp(-d * d * 6f)
            val fade = 1f - Tone.smooth(bottom * 0.6f, bottom, py)
            val a = (band * (0.35f + 0.9f * clouds) * (1f - 0.85f * dust) * strength * fade).coerceIn(0f, 0.85f)
            Tone.alpha(Tone.mix(outer, core, exp(-d * d * 5f) * clouds), a)
        }
        // Faint stars crowd the band.
        val r = Random(84)
        val p = pen()
        repeat((len * width / 9f).roundToInt().coerceIn(40, 900)) {
            val along = r.nextFloat() * len
            val across = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) * width * 0.9f
            val x = x0 + ux * along - uy * across
            val y = y0 + uy * along + ux * across
            if (y > bottom) return@repeat
            p.color = Tone.alpha(0xFFFFFFFF.toInt(), (0.2f + r.nextFloat() * 0.55f) * strength)
            canvas.drawCircle(x, y, 0.16f + r.nextFloat() * 0.3f, p)
        }
    }

    /** A meteor: a thin streak from ([x], [y]) along [angle], brightest at its head, fading behind. */
    fun meteor(x: Float, y: Float, length: Float, angle: Float, strength: Float) {
        val ex = x - cos(angle) * length
        val ey = y - sin(angle) * length
        val p = stroke(0xFFFFFFFF.toInt(), 0.55f)
        p.shader = LinearGradient(x, y, ex, ey, intArrayOf(Tone.alpha(0xFFFFFFFF.toInt(), strength), Tone.alpha(0xFFCFE0FF.toInt(), strength * 0.4f), 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawLine(x, y, ex, ey, p)
        glow(x, y, 3.5f, 0xFFFFFFFF.toInt(), 0.6f * strength)
    }

    /**
     * Clouds between [top] and [bottom] computed per pixel: a fractal field cut at [cover],
     * [scale] dp per feature, [stretch] times wider than tall. Each point is lit by how much cloud
     * lies between it and the light — bright rims facing the sun, grey bellies away from it.
     */
    fun clouds(
        top: Float,
        bottom: Float,
        cover: Float,
        scale: Float,
        seed: Int,
        lit: Int,
        shade: Int,
        opacity: Float = 0.95f,
        stretch: Float = 2.2f,
        softness: Float = 0.16f,
        silver: Float = 0.5f,
    ) {
        val sx = lightX
        val sy = lightY
        val threshold = 1f - cover
        field(RectF(0f, top, w, bottom), max(kx, ky) * 0.42f) { px, py ->
            val v = (py - top) / (bottom - top)
            val band = Tone.smooth(0f, 0.28f, v) * (1f - Tone.smooth(0.55f, 1f, v))
            if (band <= 0f) return@field 0
            val nx = px / (scale * stretch)
            val ny = py / scale
            val n = Noise.fbm(nx, ny, seed, 6) - (1f - band) * 0.3f
            val d = ((n - threshold) / softness).coerceIn(0f, 1f)
            if (d <= 0f) return@field 0
            // Towards the light by a fraction of a feature: less cloud there means a lit face.
            val lx = sx - px
            val ly = sy - py
            val ll = max(1f, hypot(lx, ly))
            val step = scale * 0.22f
            val n2 = Noise.fbm((px + lx / ll * step) / (scale * stretch), (py + ly / ll * step) / scale, seed, 5) - (1f - band) * 0.3f
            val d2 = ((n2 - threshold) / softness).coerceIn(0f, 1f)
            var light = (0.55f + (d - d2) * 1.4f - v * 0.25f).coerceIn(0f, 1f)
            // A thin cloud near the sun glows through: the silver lining.
            val near = exp(-(ll / (max(w, h) * 0.35f)).let { it * it })
            light = (light + silver * near * (1f - d) * 1.5f).coerceIn(0f, 1f)
            Tone.alpha(Tone.mix(shade, lit, light), (d * opacity).coerceIn(0f, 1f))
        }
    }

    /** Fog in long wisps between [top] and [bottom], thickest at [peak] (0..1 down the band). */
    fun fog(top: Float, bottom: Float, color: Int, strength: Float, scale: Float, seed: Int, peak: Float = 0.6f) {
        field(RectF(0f, top, w, bottom), max(kx, ky) * 0.35f) { px, py ->
            val v = (py - top) / (bottom - top)
            val band = if (v < peak) Tone.smooth(0f, peak, v) else 1f - Tone.smooth(peak, 1f, v)
            val n = Noise.fbm(px / (scale * 4f), py / scale, seed, 5)
            val a = band * Tone.smooth(0.28f, 0.75f, n) * strength
            if (a <= 0.003f) 0 else Tone.alpha(color, a.coerceIn(0f, 1f))
        }
    }

    /**
     * Light shafts from ([x], [y]) fanning down across [spread] radians around [angle]: the sun
     * through mist or branches. Screened, each shaft fading along its length.
     */
    fun rays(x: Float, y: Float, length: Float, angle: Float, spread: Float, count: Int, color: Int, strength: Float, seed: Int) {
        val r = Random(seed)
        val p = pen()
        p.blendMode = BlendMode.SCREEN
        p.maskFilter = BlurMaskFilter(max(1f, length * 0.012f), BlurMaskFilter.Blur.NORMAL)
        p.shader = RadialGradient(x, y, length, intArrayOf(Tone.alpha(color, strength), Tone.alpha(color, strength * 0.45f), 0), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        repeat(count) {
            val a = angle + (r.nextFloat() - 0.5f) * spread
            val half = 0.008f + r.nextFloat() * 0.03f
            path.reset()
            path.moveTo(x, y)
            path.lineTo(x + cos(a - half) * length, y + sin(a - half) * length)
            path.lineTo(x + cos(a + half) * length, y + sin(a + half) * length)
            path.close()
            canvas.drawPath(path, p)
        }
    }

    /** A rainbow round ([x], [y]) of [radius], [width] wide, fading at its feet into [fade]. */
    fun rainbow(x: Float, y: Float, radius: Float, width: Float, strength: Float, fadeFrom: Float) {
        val bands = intArrayOf(0xFF7A3FD0.toInt(), 0xFF3F6FE0.toInt(), 0xFF33B0E0.toInt(), 0xFF4CC46A.toInt(), 0xFFF2DD4A.toInt(), 0xFFF29A3A.toInt(), 0xFFE0493A.toInt())
        val n = bands.size
        val colors = IntArray(n + 2)
        val stops = FloatArray(n + 2)
        val inner = (radius - width / 2f) / (radius + width / 2f)
        colors[0] = 0
        stops[0] = inner - 0.004f
        for (i in 0 until n) {
            colors[i + 1] = Tone.alpha(bands[i], strength)
            stops[i + 1] = inner + (1f - inner) * (i + 0.5f) / n
        }
        colors[n + 1] = 0
        stops[n + 1] = 1f
        // Drawn in a layer of its own, so its feet can fade out without cutting into the sky.
        val layer = Paint()
        layer.blendMode = BlendMode.SCREEN
        val saved = canvas.saveLayer(0f, 0f, w, fadeFrom, layer)
        val p = pen()
        p.shader = RadialGradient(x, y, radius + width / 2f, colors, stops, Shader.TileMode.CLAMP)
        p.maskFilter = BlurMaskFilter(max(0.8f, width * 0.12f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(x, y, radius + width / 2f, p)
        val q = pen()
        q.shader = LinearGradient(0f, fadeFrom - radius * 0.4f, 0f, fadeFrom, 0, 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
        q.blendMode = BlendMode.DST_OUT
        canvas.drawRect(x - radius - width, fadeFrom - radius * 0.4f, x + radius + width, fadeFrom, q)
        canvas.restoreToCount(saved)
    }

    // endregion

    // region Land

    /** A line across the painting: its height at every [step] dp from the left. */
    class Line(val step: Float, val ys: FloatArray) {
        fun at(x: Float): Float {
            val f = (x / step).coerceIn(0f, (ys.size - 1).toFloat())
            val i = floor(f).toInt().coerceAtMost(ys.size - 2)
            val t = f - i
            return ys[i] + (ys[i + 1] - ys[i]) * t
        }

        val top: Float get() = ys.min()
        val bottom: Float get() = ys.max()

        fun shifted(dy: Float) = Line(step, FloatArray(ys.size) { ys[it] + dy })
    }

    /**
     * A ridge line near [base], rising up to [amplitude] with features [feature] dp wide. [sharp]
     * 0..1 turns rolling hills into crested mountains; [lift] raises it towards [liftX] (a peak).
     */
    fun ridge(base: Float, amplitude: Float, feature: Float, seed: Int, sharp: Float = 0f, step: Float = 0.5f, octaves: Int = 6, lift: Float = 0f, liftX: Float = w / 2f, liftWidth: Float = w * 0.3f): Line {
        val n = (w / step).toInt() + 2
        return Line(step, FloatArray(n) { i ->
            val x = i * step
            val soft = Noise.fbm(x / feature, 0.37f, seed, octaves)
            val hard = if (sharp > 0f) Noise.ridged(x / (feature * 1.2f), 1.9f, seed + 7, octaves) else 0f
            val v = soft * (1f - sharp) + hard * sharp
            val peak = if (lift != 0f) lift * exp(-((x - liftX) / liftWidth).let { it * it }) else 0f
            base - amplitude * v - peak
        })
    }

    /** A line at [y] everywhere, with a gentle [wave] of [feature] dp. */
    fun flat(y: Float, wave: Float = 0f, feature: Float = w * 0.3f, seed: Int = 1, step: Float = 1f): Line {
        val n = (w / step).toInt() + 2
        return Line(step, FloatArray(n) { i -> y - wave * (Noise.fbm(i * step / feature, 0.5f, seed, 3) - 0.5f) * 2f })
    }

    private fun areaUnder(line: Line, bottom: Float): Path {
        val p = Path()
        p.moveTo(-1f, bottom)
        p.lineTo(-1f, line.ys[0])
        for (i in line.ys.indices) p.lineTo(i * line.step, line.ys[i])
        p.lineTo(w + 1f, line.ys.last())
        p.lineTo(w + 1f, bottom)
        p.close()
        return p
    }

    private fun edgeOf(line: Line): Path {
        val p = Path()
        p.moveTo(-1f, line.ys[0])
        for (i in line.ys.indices) p.lineTo(i * line.step, line.ys[i])
        p.lineTo(w + 1f, line.ys.last())
        return p
    }

    /**
     * Land under [line] down to [bottom]: [top] at its crest shading to [low], a grain of [texture],
     * and a rim of [rim] light along the crest where it faces the light, strongest near it.
     */
    fun land(
        line: Line,
        bottom: Float,
        top: Int,
        low: Int,
        texture: Float = 0.2f,
        textureScale: Float = 1f,
        rim: Int = 0,
        rimWidth: Float = 1.1f,
        rimStrength: Float = 0.6f,
        shading: Float = 0f,
    ): Path {
        val area = areaUnder(line, bottom)
        val p = pen()
        p.shader = LinearGradient(0f, line.top, 0f, bottom, top, low, Shader.TileMode.CLAMP)
        canvas.drawPath(area, p)
        if (shading > 0f) slopes(line, area, bottom, shading)
        if (texture > 0f) texture(area, texture, textureScale)
        if (rim != 0 && rimStrength > 0f) {
            canvas.withClip(area) {
                val s = stroke(rim, rimWidth * 2f)
                val fall = w * 0.6f
                s.shader = LinearGradient(lightX - fall, 0f, lightX + fall, 0f, intArrayOf(Tone.alpha(rim, rimStrength * 0.25f), Tone.alpha(rim, rimStrength), Tone.alpha(rim, rimStrength * 0.25f)), null, Shader.TileMode.CLAMP)
                s.maskFilter = BlurMaskFilter(rimWidth * 0.6f, BlurMaskFilter.Blur.NORMAL)
                drawPath(edgeOf(line), s)
            }
        }
        return area
    }

    /** Faces turned to the light lighter, faces turned away darker: slopes read as relief. */
    private fun slopes(line: Line, area: Path, bottom: Float, strength: Float) {
        val n = 96
        val colors = IntArray(n)
        val stops = FloatArray(n)
        val dir = if (lightX >= w / 2f) 1f else -1f
        for (i in 0 until n) {
            val x = i / (n - 1f) * w
            val dx = w / n
            val slope = (line.at(x - dx) - line.at(x + dx)) / (2f * dx) // > 0 rising to the right
            val facing = (slope * dir * 2.2f).coerceIn(-1f, 1f)
            colors[i] = if (facing >= 0f) Tone.alpha(0xFFFFFFFF.toInt(), facing * strength) else Tone.alpha(0xFF000000.toInt(), -facing * strength * 0.8f)
            stops[i] = i / (n - 1f)
        }
        canvas.withClip(area) {
            val p = pen()
            p.shader = LinearGradient(0f, 0f, w, 0f, colors, stops, Shader.TileMode.CLAMP)
            p.blendMode = BlendMode.SOFT_LIGHT
            drawRect(0f, line.top, w, bottom, p)
        }
    }

    /** A grain of light and dark over [area], [amount] strong, features [scale] dp. */
    fun texture(area: Path, amount: Float, scale: Float = 1f, mode: BlendMode = BlendMode.OVERLAY) {
        val p = pen()
        p.shader = BitmapShader(grainTile, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
            setLocalMatrix(Matrix().apply { setScale(scale * 0.35f, scale * 0.35f) })
        }
        p.alpha = (amount * 255f).roundToInt().coerceIn(0, 255)
        p.blendMode = mode
        canvas.drawPath(area, p)
    }

    /** Air between the viewer and a layer: [color] rising from [from] up to [to], [strength] at its foot. */
    fun haze(from: Float, to: Float, color: Int, strength: Float) {
        val p = pen()
        p.shader = LinearGradient(0f, from, 0f, to, Tone.alpha(color, strength), Tone.alpha(color, 0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, min(from, to), w, max(from, to), p)
    }

    /**
     * A tree line on [ground]: crowns of [heights] every ~[spacing] dp, [conifers] of them
     * pointed spruces, the rest round-crowned. A silhouette, for layers too far for leaves.
     */
    fun treeLine(ground: Line, heights: ClosedFloatingPointRange<Float>, spacing: Float, conifers: Float, seed: Int, step: Float = 0.3f, roundness: Float = 1f): Line {
        val n = (w / step).toInt() + 2
        val ys = FloatArray(n) { ground.at(it * step) }
        val r = Random(seed)
        var x = -spacing * r.nextFloat()
        while (x < w + spacing * 2) {
            val th = heights.start + r.nextFloat() * (heights.endInclusive - heights.start)
            val conifer = r.nextFloat() < conifers
            val half = if (conifer) th * (0.2f + r.nextFloat() * 0.08f) else th * (0.42f + r.nextFloat() * 0.2f) * roundness
            val base = ground.at(x)
            val i0 = ((x - half) / step).toInt().coerceAtLeast(0)
            val i1 = ((x + half) / step).toInt().coerceAtMost(n - 1)
            for (i in i0..i1) {
                val d = abs(i * step - x) / half
                if (d > 1f) continue
                val y = if (conifer) {
                    // A spire: straight flanks roughened by the tiers of branches.
                    val jag = (Noise.value(i * step * 1.7f, x, seed) - 0.5f) * th * 0.08f
                    base - th * (1f - d).pow(1.05f) + jag * d
                } else {
                    val lump = (Noise.fbm(i * step / (half * 0.45f), x * 0.1f, seed + 3, 3) - 0.5f) * th * 0.22f
                    base - th * 0.25f - th * 0.75f * sqrt(max(0f, 1f - d * d)) + lump * sqrt(max(0f, 1f - d * d))
                }
                if (y < ys[i]) ys[i] = y
            }
            x += spacing * (0.55f + r.nextFloat() * 0.8f)
        }
        return Line(step, ys)
    }

    // endregion

    // region Water

    /**
     * Still water from [top] to [bottom]: everything above mirrored in it, broken by ripples that
     * grow towards the viewer, darkened and tinted by [tint], with the light's path of glints.
     */
    fun water(top: Float, bottom: Float, tint: Int, tintStrength: Float, ripple: Float, seed: Int, glint: Int = 0, glintStrength: Float = 0f, clip: Path? = null) {
        val w0 = widthPx
        val y0 = (top * ky).roundToInt().coerceIn(1, heightPx - 1)
        val y1 = (bottom * ky).roundToInt().coerceIn(y0, heightPx)
        if (y1 <= y0) return
        val src = IntArray(w0 * y0)
        bitmap.getPixels(src, 0, w0, 0, 0, w0, y0)
        val out = IntArray(w0 * (y1 - y0))
        val tr = tint shr 16 and 0xFF
        val tg = tint shr 8 and 0xFF
        val tb = tint and 0xFF
        for (y in y0 until y1) {
            val depth = (y - y0).toFloat() / max(1, y1 - y0)
            // Perspective: rows near the shore mirror what's just above it, farther ones stretch.
            val sy = (y0 - 1 - (y - y0) * (1f + depth * 0.3f)).roundToInt().coerceIn(0, y0 - 1)
            val amp = ripple * kx * (0.25f + depth * 1.4f)
            val wave = (Noise.fbm(y * 0.9f / ky, 0.3f, seed, 3) - 0.5f) * 2f * amp
            val k = (tintStrength * (0.55f + depth * 0.45f)).coerceIn(0f, 1f)
            for (x in 0 until w0) {
                val ripples = (Noise.value(x * 0.08f / kx, y * 0.6f / ky, seed + 1) - 0.5f) * amp * 0.8f
                val sx = (x + wave + ripples).roundToInt().coerceIn(0, w0 - 1)
                val c = src[sy * w0 + sx]
                val r = (c shr 16 and 0xFF) * (1f - k) + tr * k
                val g = (c shr 8 and 0xFF) * (1f - k) + tg * k
                val b = (c and 0xFF) * (1f - k) + tb * k
                out[(y - y0) * w0 + x] = (0xFF shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
            }
        }
        if (clip == null) {
            bitmap.setPixels(out, 0, w0, 0, y0, w0, y1 - y0)
            // The shore line: a thin darker seam where land meets its reflection.
            val seam = pen()
            seam.shader = LinearGradient(0f, top, 0f, top + 1.5f, Tone.alpha(0xFF000000.toInt(), 0.18f), 0, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, top, w, top + 1.5f, seam)
        } else {
            val layer = createBitmap(w0, y1 - y0)
            layer.setPixels(out, 0, w0, 0, 0, w0, y1 - y0)
            drawExact(layer, 0, y0, clip)
            layer.recycle()
        }
        if (glint != 0 && glintStrength > 0f) {
            if (clip != null) canvas.withClip(clip) { glints(top, bottom, glint, glintStrength, seed) } else glints(top, bottom, glint, glintStrength, seed)
        }
    }

    /** The light's path on water: short bright dashes, dense under the light and thinning away. */
    private fun glints(top: Float, bottom: Float, color: Int, strength: Float, seed: Int) {
        val r = Random(seed + 5)
        val p = pen()
        p.blendMode = BlendMode.SCREEN
        val count = ((bottom - top) * w / 14f).roundToInt().coerceIn(20, 700)
        repeat(count) {
            val v = r.nextFloat()
            val y = top + v.pow(0.8f) * (bottom - top)
            val spread = w * (0.04f + v * 0.18f)
            val x = lightX + (r.nextFloat() + r.nextFloat() - 1f) * spread
            val len = 0.8f + v * 4f * r.nextFloat()
            val a = strength * (0.3f + 0.7f * r.nextFloat()) * (1f - abs(x - lightX) / (spread * 1.2f)).coerceIn(0f, 1f)
            if (a <= 0.01f) return@repeat
            p.color = Tone.alpha(color, a)
            canvas.drawRoundRect(x - len, y - 0.18f - v * 0.2f, x + len, y + 0.18f + v * 0.2f, 0.3f, 0.3f, p)
        }
    }

    // endregion

    // region Fields and finish

    /**
     * Computes a soft layer per pixel over [area] at [res] pixels per dp and draws it up with
     * bilinear filtering. [shade] returns a non-premultiplied ARGB colour for a point in dp.
     */
    inline fun field(area: RectF, res: Float, clip: Path? = null, shade: (x: Float, y: Float) -> Int) {
        val fw = max(2, (area.width() * res).roundToInt())
        val fh = max(2, (area.height() * res).roundToInt())
        val pixels = IntArray(fw * fh)
        val sx = area.width() / fw
        val sy = area.height() / fh
        for (j in 0 until fh) {
            val y = area.top + (j + 0.5f) * sy
            for (i in 0 until fw) {
                pixels[j * fw + i] = shade(area.left + (i + 0.5f) * sx, y)
            }
        }
        val layer = createBitmap(fw, fh)
        layer.setPixels(pixels, 0, fw, 0, 0, fw, fh)
        drawLayer(layer, area, clip)
        layer.recycle()
    }

    fun drawLayer(layer: Bitmap, area: RectF, clip: Path? = null) {
        if (clip != null) canvas.withClip(clip) { drawBitmap(layer, null, area, filter) } else canvas.drawBitmap(layer, null, area, filter)
    }

    /**
     * Computes pixels one for one over [area] (snapped to the pixel grid) and lays them on,
     * inside [clip] when given: for things that must stay crisp — crowns, spruces, the ground.
     */
    inline fun pixels(area: RectF, clip: Path? = null, shade: (x: Float, y: Float) -> Int) {
        val x0 = floor(area.left * kx).toInt().coerceIn(0, widthPx)
        val x1 = ceil(area.right * kx).toInt().coerceIn(0, widthPx)
        val y0 = floor(area.top * ky).toInt().coerceIn(0, heightPx)
        val y1 = ceil(area.bottom * ky).toInt().coerceIn(0, heightPx)
        val fw = x1 - x0
        val fh = y1 - y0
        if (fw <= 0 || fh <= 0) return
        val buffer = IntArray(fw * fh)
        for (j in 0 until fh) {
            val y = (y0 + j + 0.5f) / ky
            for (i in 0 until fw) {
                buffer[j * fw + i] = shade((x0 + i + 0.5f) / kx, y)
            }
        }
        val layer = createBitmap(fw, fh)
        layer.setPixels(buffer, 0, fw, 0, 0, fw, fh)
        drawExact(layer, x0, y0, clip)
        layer.recycle()
    }

    fun drawExact(layer: Bitmap, x0: Int, y0: Int, clip: Path?) {
        val dst = RectF(x0 / kx, y0 / ky, (x0 + layer.width) / kx, (y0 + layer.height) / ky)
        if (clip != null) {
            canvas.withClip(clip) { drawBitmap(layer, null, dst, exact) }
        } else {
            canvas.drawBitmap(layer, null, dst, exact)
        }
    }

    private val exact = Paint()

    /**
     * Where the painting is brightest the light spills over: a bloom from everything above
     * [threshold] luminance, blurred [radius] dp wide and screened back on.
     */
    fun bloom(threshold: Float, strength: Float, radius: Float) {
        val scale = 0.25f
        val bw = max(4, (widthPx * scale).roundToInt())
        val bh = max(4, (heightPx * scale).roundToInt())
        val small = bitmap.scale(bw, bh)
        val pixels = IntArray(bw * bh)
        small.getPixels(pixels, 0, bw, 0, 0, bw, bh)
        for (i in pixels.indices) {
            val c = pixels[i]
            val l = Tone.luminance(c)
            val k = Tone.smooth(threshold, 1f, l)
            pixels[i] = Tone.alpha(c, k)
        }
        Blur.box(pixels, bw, bh, radius = max(1, (radius * kx * scale / 2f).roundToInt()))
        small.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        p.blendMode = BlendMode.SCREEN
        p.alpha = (strength * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(small, null, RectF(0f, 0f, w, h), p)
        if (small !== bitmap) small.recycle()
    }

    /** Darker corners, as a lens gives them: [strength] at the far corners. */
    fun vignette(strength: Float, color: Int = 0xFF05070F.toInt()) {
        val p = pen()
        val r = hypot(w, h) * 0.62f
        p.shader = RadialGradient(w / 2f, h * 0.45f, r, intArrayOf(0, 0, Tone.alpha(color, strength)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }

    /**
     * The last pass over every pixel: a fine film grain (which also dithers away banding in the
     * gradients), and an optional lift of the shadows towards [lift].
     */
    fun finish(grain: Float, seed: Int, lift: Int = 0, liftAmount: Float = 0f) {
        val n = widthPx * heightPx
        val pixels = IntArray(n)
        bitmap.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
        // Fixed point: the grain's amplitude in 1/256ths of a level.
        val g = (grain * 255f * 256f).roundToInt()
        val lr = lift shr 16 and 0xFF
        val lg = lift shr 8 and 0xFF
        val lb = lift and 0xFF
        var state = seed * 747796405 + 2891336453L.toInt()
        for (i in 0 until n) {
            state = state * 1664525 + 1013904223
            val noise = (((state ushr 16) and 0xFF) - 128) * g shr 15
            val c = pixels[i]
            var r = c shr 16 and 0xFF
            var gg = c shr 8 and 0xFF
            var b = c and 0xFF
            if (liftAmount > 0f) {
                val dark = 1f - (r * 0.2126f + gg * 0.7152f + b * 0.0722f) / 255f
                val k = liftAmount * dark * dark
                r += ((lr - r) * k).roundToInt()
                gg += ((lg - gg) * k).roundToInt()
                b += ((lb - b) * k).roundToInt()
            }
            r += noise
            gg += noise
            b += noise
            pixels[i] = (0xFF shl 24) or
                ((if (r < 0) 0 else if (r > 255) 255 else r) shl 16) or
                ((if (gg < 0) 0 else if (gg > 255) 255 else gg) shl 8) or
                (if (b < 0) 0 else if (b > 255) 255 else b)
        }
        bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
    }

    // endregion

    companion object {
        /** A tile of soft fractal grain, 50 % grey on average, for textures laid over areas. */
        private val grainTile: Bitmap by lazy {
            val size = 128
            val pixels = IntArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                val n = Noise.fbm(x / 9f, y / 9f, 5, 4) * 0.7f + Noise.value(x / 2.2f, y / 2.2f, 6) * 0.3f
                val v = (n * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * size + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            createBitmap(size, size).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
        }

    }
}

/** Box blur on ARGB pixels: three passes each way approach a Gaussian. */
internal object Blur {
    fun box(pixels: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3) {
        if (radius < 1) return
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, scratch, w, h, radius, horizontal = true)
            pass(scratch, pixels, w, h, radius, horizontal = false)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val k = i.coerceIn(0, length - 1)
                val p = if (horizontal) src[line * w + k] else src[k * w + line]
                a += p ushr 24
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                val out = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val gi = (i - radius).coerceIn(0, length - 1)
                val ci = (i + radius + 1).coerceIn(0, length - 1)
                val gone = if (horizontal) src[line * w + gi] else src[gi * w + line]
                val come = if (horizontal) src[line * w + ci] else src[ci * w + line]
                a += (come ushr 24) - (gone ushr 24)
                r += (come shr 16 and 0xFF) - (gone shr 16 and 0xFF)
                g += (come shr 8 and 0xFF) - (gone shr 8 and 0xFF)
                b += (come and 0xFF) - (gone and 0xFF)
            }
        }
    }
}
