package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paints a week's picture at any proportions ([Painting]). Deterministic: the same week at the
 * same size is always the same picture. With [live] the things that fall — snow, leaves, petals,
 * fireflies — are left to the tiles that animate them; with [night] it is the same place by
 * moonlight (or by lamplight, deeper into the evening), for the dark theme.
 */
internal class SeasonScene {
    fun paint(art: WeekArt, widthDp: Float, heightDp: Float, pxPerDp: Float, live: Boolean = false, night: Boolean = false): Bitmap {
        val p = Painting(widthDp, heightDp, pxPerDp, art.week * 7919)
        p.lightX = art.bodyX * widthDp
        p.lightY = art.bodyY * heightDp
        when (art.week) {
            1 -> p.christmas(art, live)
            2 -> p.january(art, live)
            3 -> p.epiphany(art, live)
            4 -> p.izba(art, live)
            5 -> p.skiTrack(art, live)
            6 -> p.blizzard(art, live)
            7 -> p.february(art, live)
            8 -> p.maslenitsa(art, live)
            9 -> p.drops(art, live)
            10 -> p.mimosa(art, live)
            11 -> p.march(art, live)
            12 -> p.streams(art, live)
            13 -> p.snowdropGlade(art, live)
            14 -> p.iceDrift(art, live)
            15 -> p.april(art, live)
            16 -> p.easter(art, live)
            17 -> p.dachaSeason(art, live)
            18 -> p.mayStorm(art, live)
            19 -> p.birdCherry(art, live)
            20 -> p.may(art, live)
            21 -> p.lilac(art, live)
            22 -> p.kiteDay(art, live)
            23 -> p.june(art, live)
            24 -> p.poplarFluff(art, live)
            25 -> p.whiteNights(art, live)
            26 -> p.haymaking(art, live)
            27 -> p.kupala(art, live)
            28 -> p.july(art, live)
            29 -> p.seaside(art, live)
            30 -> p.veranda(art, live)
            31 -> p.camp(art, live)
            32 -> p.august(art, live)
            33 -> p.appleSaviour(art, live)
            34 -> p.lakeMist(art, live)
            35 -> p.sunflowers(art, live)
            36 -> p.indianSummer(art, live)
            37 -> p.mushrooms(art, live)
            38 -> p.september(art, live)
            39 -> p.cafe(art, live)
            40 -> p.october(art, live)
            41 -> p.parkAlley(art, live)
            42 -> p.castleFire(art, live)
            43 -> p.autumnRain(art, live)
            44 -> p.firstFrost(art, live)
            45 -> p.november(art, live)
            46 -> p.reading(art, live)
            47 -> p.firstSnow(art, live)
            48 -> p.bullfinches(art, live)
            49 -> p.rink(art, live)
            50 -> p.december(art, live)
            51 -> p.frostyWindow(art, live)
            else -> p.newYear(art, live)
        }
        // The dark theme: the same place by moonlight.
        if (night) p.nightfall(art.nightfall)
        return p.bitmap
    }

    /** Paints into [rect] of [canvas] at the canvas's own resolution. */
    fun draw(canvas: Canvas, rect: RectF, art: WeekArt, live: Boolean = false) {
        @Suppress("DEPRECATION")
        val scale = canvas.matrix.mapRadius(1f).coerceIn(0.25f, 4f)
        val bitmap = paint(art, rect.width(), rect.height(), scale, live)
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
    }
}

internal fun c(rgb: Long) = Tone.of(rgb)

internal fun tones(vararg rgb: Long) = IntArray(rgb.size) { Tone.of(rgb[it]) }

internal fun Painting.sky(vararg stops: Pair<Float, Int>, to: Float = h) =
    sky(IntArray(stops.size) { c(stops[it].second.toLong()) }, FloatArray(stops.size) { stops[it].first }, to)

/** The area under [line] down to [bottom], for clipping the ground's pixels. */
internal fun Painting.under(line: Painting.Line, bottom: Float = h + 1f): Path = Path().apply {
    moveTo(-1f, bottom)
    lineTo(-1f, line.ys[0])
    for (i in line.ys.indices) lineTo(i * line.step, line.ys[i])
    lineTo(w + 1f, line.ys.last())
    lineTo(w + 1f, bottom)
    close()
}

/** A tree's shadow thrown on snow or grass, away from the light and towards the viewer. */
internal fun Painting.castShadow(x: Float, ground: Float, length: Float, width: Float, color: Int, strength: Float) {
    val away = if (lightX > x) -1f else 1f
    val p = pen()
    p.color = Tone.alpha(color, strength)
    p.maskFilter = BlurMaskFilter(max(0.6f, width * 0.3f), BlurMaskFilter.Blur.NORMAL)
    path.reset()
    path.moveTo(x - width * 0.5f, ground)
    path.quadTo(x + away * length * 0.5f, ground + length * 0.18f, x + away * length, ground + length * 0.26f)
    path.quadTo(x + away * length * 0.55f, ground + length * 0.06f, x + width * 0.5f, ground)
    path.close()
    canvas.drawPath(path, p)
}

/**
 * Snow lying in soft drifts, per pixel: bright where the drifts face the light, blue in their
 * troughs, the pattern shrinking and flattening into the distance, where it takes the air's [far].
 */
internal fun Painting.snowfield(line: Painting.Line, horizon: Float, lit: Int, shade: Int, far: Int, seed: Int, relief: Float = 1f) {
    val g = Ground(w, h, horizon)
    val dir = if (lightX >= w / 2f) 1f else -1f
    // Snow is soft: half resolution, drawn up; the clip keeps its edge crisp.
    field(RectF(0f, line.top, w, h), kx * 0.5f, under(line)) { x, y ->
        g.at(x, y)
        val u = g.across * 0.5f
        val v = g.away * 0.2f
        val n = Noise.fbm(u, v, seed, 3)
        val nd = Noise.fbm(u + 0.06f * dir, v, seed, 2)
        val facing = (nd - n) * 16f * relief
        val depth = ((y - horizon) / (h - horizon)).coerceIn(0f, 1f)
        val k = (0.66f + facing + (n - 0.5f) * 0.3f - depth * 0.12f).coerceIn(0f, 1f)
        Tone.mix(far, Tone.mix(shade, lit, k), Tone.smooth(0f, 0.3f, depth))
    }
}

/**
 * A meadow, per pixel: blades streaking upright, finer with the distance, clumps and the shadows
 * of passing clouds lying on the ground plane, lit towards the sun; [speckle] colours dot it with
 * flowers in drifts, sized by the distance.
 */
internal fun Painting.meadow(line: Painting.Line, horizon: Float, ramp: IntArray, far: Int, seed: Int, speckle: IntArray? = null, speckleAmount: Float = 0f) {
    val g = Ground(w, h, horizon)
    pixels(RectF(0f, line.top, w, h), under(line)) { x, y ->
        g.at(x, y)
        val depth = ((y - horizon) / (h - horizon)).coerceIn(0f, 1f)
        val near = Tone.smooth(0.02f, 0.45f, depth)
        val blade = 0.18f + depth * 1.3f
        val blades = Noise.value(x / blade, y / (blade * 3f), seed)
        val fine = Noise.value(x / (blade * 0.45f), y / (blade * 2f), seed + 4)
        val clumps = Noise.fbm(g.across * 0.7f, g.away * 0.7f, seed + 1, 2)
        val shadow = Noise.fbm(x / (w * 0.5f), y / (h * 0.2f), seed + 2, 2)
        val sun = 1f - kotlin.math.abs(x - lightX) / (w * 1.2f)
        val v = 0.5f + (blades - 0.5f) * 0.55f * near + (fine - 0.5f) * 0.3f * near + (clumps - 0.5f) * 0.6f * near +
            (Tone.smooth(0.45f, 0.65f, shadow) - 0.5f) * 0.22f + sun * 0.12f
        var color = ramp(ramp, v.coerceIn(0f, 1f))
        if (speckle != null && speckleAmount > 0f && depth > 0.08f) {
            val cell = 0.5f + depth * 2.6f
            val cu = x / cell
            val cv = y / (cell * 0.7f)
            val ci = kotlin.math.floor(cu).toInt()
            val cj = kotlin.math.floor(cv).toInt()
            val pick = Noise.rand(ci, cj, seed + 7)
            if (pick > 1f - speckleAmount && pick > 1f - speckleAmount * Tone.smooth(0.42f, 0.68f, Noise.fbm(g.across * 0.5f + 3f, g.away * 0.5f, seed + 8, 2))) {
                val du = cu - ci - 0.3f - Noise.rand(ci, cj, seed + 10) * 0.4f
                val dv = cv - cj - 0.3f - Noise.rand(ci, cj, seed + 11) * 0.4f
                val d2 = du * du + dv * dv
                if (d2 < 0.09f) {
                    val hue = speckle[(Noise.rand(ci, cj, seed + 9) * speckle.size).toInt().coerceIn(0, speckle.size - 1)]
                    color = Tone.mix(color, Tone.shade(hue, -d2 * 3f), Tone.smooth(0.09f, 0.05f, d2))
                }
            }
        }
        Tone.mix(far, color, Tone.smooth(0f, 0.25f, depth))
    }
}

/** Ripe rye, per pixel: stalks standing upright, the wind's waves running over it, glowing towards the sun. */
internal fun Painting.rye(line: Painting.Line, horizon: Float, ramp: IntArray, far: Int, glow: Int, seed: Int) {
    val g = Ground(w, h, horizon)
    pixels(RectF(0f, line.top, w, h), under(line)) { x, y ->
        g.at(x, y)
        val depth = ((y - horizon) / (h - horizon)).coerceIn(0f, 1f)
        val near = Tone.smooth(0.02f, 0.4f, depth)
        val stalk = 0.2f + depth * 1.2f
        val stalks = Noise.value(x / stalk, y / (stalk * 6f), seed)
        val ears = Noise.value(x / (stalk * 0.8f), y / (stalk * 1.2f), seed + 3)
        val waves = Noise.fbm(g.across * 0.35f + g.away * 0.2f, g.away * 0.35f, seed + 1, 3)
        val v = 0.52f + (stalks - 0.5f) * 0.6f * near + (ears - 0.5f) * 0.3f * near + (Tone.smooth(0.38f, 0.68f, waves) - 0.5f) * 0.4f * (0.4f + near)
        var color = ramp(ramp, v.coerceIn(0f, 1f))
        val sun = kotlin.math.exp(-((x - lightX) / (w * 0.25f)).let { it * it }) * (1f - depth * 0.6f)
        color = Tone.mix(color, glow, (sun * 0.45f * (0.5f + stalks * 0.5f)).coerceIn(0f, 1f))
        Tone.mix(far, color, Tone.smooth(0f, 0.2f, depth))
    }
}

/**
 * Ground in the thaw, per pixel: snow going in patches — [cover] of it still lying — wet earth
 * between, puddles holding the sky and darkened rims round them, as many as [pools] says (by
 * default more of them as the snow goes).
 */
internal fun Painting.thaw(line: Painting.Line, horizon: Float, snow: Int, snowShade: Int, earth: Int, wet: Int, skyNear: Int, skyFar: Int, far: Int, seed: Int, cover: Float = 0.7f, pools: Float = 1f - cover) {
    val rimAt = 0.59f - pools * 0.035f
    val waterAt = rimAt + 0.04f
    val g = Ground(w, h, horizon)
    field(RectF(0f, line.top, w, h), kx * 0.7f, under(line)) { x, y ->
        g.at(x, y)
        val depth = ((y - horizon) / (h - horizon)).coerceIn(0f, 1f)
        val patch = Noise.fbm(g.across * 1.3f, g.away * 0.5f, seed, 4)
        val pool = Noise.fbm(g.across * 0.7f + 11f, g.away * 0.3f, seed + 5, 3)
        val grain = Noise.value(g.across * 12f, g.away * 2f, seed + 2)
        val snowLine = if (cover <= 0.01f) 2f else 0.5f + (0.5f - cover) * 0.32f - depth * 0.12f
        var color = if (patch > snowLine) {
            val k = Tone.smooth(snowLine, snowLine + 0.06f, patch)
            Tone.mix(Tone.mix(earth, wet, grain * 0.5f), Tone.mix(snowShade, snow, 0.5f + (patch - 0.55f) * 2f + (grain - 0.5f) * 0.2f), k)
        } else {
            Tone.shade(Tone.mix(earth, wet, 0.3f + grain * 0.5f), (grain - 0.5f) * 0.25f)
        }
        if (pool > rimAt) {
            val rim = Tone.smooth(rimAt, waterAt, pool)
            color = Tone.mix(color, wet, rim * 0.7f)
            if (pool > waterAt) {
                val sky = Tone.mix(skyFar, skyNear, depth)
                val ripple = (Noise.value(g.across * 3f, g.away * 8f, seed + 6) - 0.5f) * 0.1f
                color = Tone.mix(color, Tone.shade(sky, ripple), Tone.smooth(waterAt, waterAt + 0.03f, pool))
            }
        }
        Tone.mix(far, color, Tone.smooth(0f, 0.25f, depth))
    }
}

/**
 * A forest floor under fallen leaves, per pixel: leaves in [colors] lying in drifts, darker
 * between; after a frosty night [rime] whitens their edges.
 */
internal fun Painting.litter(line: Painting.Line, horizon: Float, colors: IntArray, earth: Int, far: Int, seed: Int, rime: Float = 0f) {
    val g = Ground(w, h, horizon)
    pixels(RectF(0f, line.top, w, h), under(line)) { x, y ->
        g.at(x, y)
        val depth = ((y - horizon) / (h - horizon)).coerceIn(0f, 1f)
        val near = Tone.smooth(0.02f, 0.4f, depth)
        val leaf = 0.3f + depth * 1.8f
        val pick = Noise.value(x / leaf, y / (leaf * 0.6f), seed)
        val hue = ramp(colors, pick)
        val gaps = Noise.fbm(x / (leaf * 0.7f), y / (leaf * 0.45f), seed + 2, 2)
        val light = Noise.fbm(g.across * 0.6f, g.away * 0.6f, seed + 1, 3)
        var color = Tone.shade(hue, (light - 0.5f) * 0.5f + (gaps - 0.5f) * 0.4f * near)
        color = Tone.mix(color, earth, Tone.smooth(0.3f, 0.12f, gaps) * 0.8f * near)
        if (rime > 0f) {
            val crystals = Noise.value(x / (leaf * 0.35f), y / (leaf * 0.3f), seed + 5)
            color = Tone.mix(color, Tone.of(0xE6EAF0), rime * (0.25f + 0.55f * crystals) * (0.5f + 0.5f * near))
        }
        Tone.mix(far, color, Tone.smooth(0f, 0.3f, depth))
    }
}

internal fun Painting.done(bloom: Float, vignette: Float, grain: Float = 0.016f, threshold: Float = 0.78f) {
    if (bloom > 0f) bloom(threshold, bloom, h * 0.05f)
    if (vignette > 0f) vignette(vignette)
    finish(grain, 17)
}

// region Seasonal motifs

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

/** [tones] whitened by hoarfrost: rime on every needle and twig, most on the lit tips. */
internal fun rimed(tones: IntArray, amount: Float): IntArray =
    IntArray(tones.size) { Tone.mix(tones[it], c(0xE8EEF8), amount * (0.35f + 0.65f * it / (tones.size - 1f))) }

/** Every colour of [tones] pulled toward [to] by [t]. */
internal fun veiled(tones: IntArray, to: Int, t: Float): IntArray = IntArray(tones.size) { Tone.mix(tones[it], to, t) }

/** [a] and [b] mixed: a colour between two of a painting's, as the weeks go from one to the other. */
internal fun between(a: Long, b: Long, t: Float): Int = Tone.mix(c(a), c(b), t)

/**
 * A string of lights for the New Year: bulbs in turn red, gold, green and blue along the line
 * through [xs], [ys], sagging between the points, each glowing.
 */
internal fun Painting.garland(xs: FloatArray, ys: FloatArray, sag: Float, bulbs: Int, size: Float, strength: Float, seed: Int) {
    if (strength <= 0.02f || xs.size < 2) return
    val colors = intArrayOf(0xFFFF5A4E.toInt(), 0xFFFFC94A.toInt(), 0xFF6CE08A.toInt(), 0xFF6AB8FF.toInt())
    val r = Random(seed)
    val per = max(2, bulbs / (xs.size - 1))
    var k = r.nextInt(colors.size)
    for (i in 0 until xs.size - 1) {
        val x0 = xs[i]
        val y0 = ys[i]
        val x1 = xs[i + 1]
        val y1 = ys[i + 1]
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo((x0 + x1) / 2f, (y0 + y1) / 2f + sag * 2f, x1, y1)
        canvas.drawPath(path, stroke(Tone.alpha(0xFF14141C.toInt(), 0.45f * strength), max(0.1f, size * 0.12f)))
        for (j in 0 until per) {
            val t = (j + 0.5f) / per
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t + sag * 4f * t * (1f - t)
            val color = colors[k++ % colors.size]
            glow(x, y, size * 5f, color, 0.55f * strength)
            canvas.drawCircle(x, y, size * 0.55f, pen(Tone.alpha(Tone.mix(color, 0xFFFFFFFF.toInt(), 0.45f), strength)))
        }
    }
}

/**
 * A firework over the village: sparks flying out from one point, each a streak brightening to
 * its tip and bending down as it falls, an inner ring of crackling stars, the air lit round it.
 */
internal fun Painting.firework(x: Float, y: Float, radius: Float, color: Int, strength: Float, seed: Int) {
    val r = Random(seed)
    glow(x, y, radius * 2f, color, 0.38f * strength)
    val bright = Tone.mix(color, 0xFFFFFFFF.toInt(), 0.45f)
    val sparks = 32
    repeat(sparks) { k ->
        val a = k * 2f * PI.toFloat() / sparks + r.range(-0.07f, 0.07f)
        val len = radius * r.range(0.75f, 1.05f)
        val x0 = x + cos(a) * len * 0.25f
        val y0 = y + sin(a) * len * 0.25f
        val x1 = x + cos(a) * len
        val y1 = y + sin(a) * len + radius * 0.16f
        val p = stroke(0, max(0.3f, radius * 0.03f))
        p.shader = LinearGradient(x0, y0, x1, y1, Tone.alpha(color, 0f), Tone.alpha(bright, strength), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo((x0 + x1) / 2f + cos(a) * len * 0.05f, (y0 + y1) / 2f - radius * 0.05f, x1, y1)
        canvas.drawPath(path, p)
        glow(x1, y1, max(0.8f, radius * 0.1f), bright, 0.6f * strength)
        canvas.drawCircle(x1, y1, max(0.35f, radius * 0.035f), pen(Tone.alpha(0xFFFFFFFF.toInt(), strength)))
    }
    repeat(14) { k ->
        val a = k * 2f * PI.toFloat() / 14 + 0.2f
        val px = x + cos(a) * radius * 0.5f
        val py = y + sin(a) * radius * 0.5f + radius * 0.05f
        canvas.drawCircle(px, py, max(0.25f, radius * 0.025f), pen(Tone.alpha(0xFFFFF4D8.toInt(), 0.9f * strength)))
    }
}

/**
 * Sun dogs in a hard frost: ice crystals in the air ring the sun with a faint halo, and two mock
 * suns burn on it level with the real one, reddish on their inner side.
 */
internal fun Painting.sunDogs(x: Float, y: Float, radius: Float, strength: Float) {
    if (strength <= 0.02f) return
    val ring = stroke(Tone.alpha(0xFFFFFFFF.toInt(), 0.3f * strength), max(0.3f, radius * 0.045f))
    ring.maskFilter = BlurMaskFilter(max(0.4f, radius * 0.035f), BlurMaskFilter.Blur.NORMAL)
    canvas.drawCircle(x, y, radius, ring)
    val inner = stroke(Tone.alpha(0xFFFFB08A.toInt(), 0.2f * strength), max(0.25f, radius * 0.03f))
    inner.maskFilter = BlurMaskFilter(max(0.3f, radius * 0.03f), BlurMaskFilter.Blur.NORMAL)
    canvas.drawCircle(x, y, radius * 0.955f, inner)
    for (side in listOf(-1f, 1f)) {
        val dx = x + side * radius
        glow(dx, y, radius * 0.5f, 0xFFFFE6C8.toInt(), 0.3f * strength, squash = 0.35f)
        glow(dx, y, radius * 0.2f, 0xFFFFF6E6.toInt(), 0.85f * strength)
        glow(dx - side * radius * 0.05f, y, radius * 0.1f, 0xFFFFB890.toInt(), 0.35f * strength)
    }
}

/** A star on the top of a New Year's tree: a gold point with its rays. */
internal fun Painting.treeStar(x: Float, y: Float, size: Float, strength: Float) {
    if (strength <= 0.02f) return
    glow(x, y, size * 5f, 0xFFFFD66A.toInt(), 0.6f * strength)
    val p = stroke(Tone.alpha(0xFFFFF0B8.toInt(), strength), max(0.18f, size * 0.25f))
    for (k in 0 until 4) {
        val a = k * PI.toFloat() / 4
        canvas.drawLine(x - cos(a) * size * 1.6f, y - sin(a) * size * 1.6f, x + cos(a) * size * 1.6f, y + sin(a) * size * 1.6f, p)
    }
    canvas.drawCircle(x, y, size * 0.6f, pen(Tone.alpha(0xFFFFFFFF.toInt(), strength)))
}

/** Cranes flying south: a wedge of small dark birds, the leader first and the two arms trailing back. */
internal fun Painting.cranes(x: Float, y: Float, count: Int, span: Float, size: Float, color: Int, seed: Int) {
    val r = Random(seed)
    val s = stroke(color, max(0.16f, size * 0.12f))
    for (i in 0 until count) {
        val n = (i + 1) / 2
        val arm = if (i % 2 == 0) 1f else -1f
        val bx = x + n * span * 0.085f
        val by = y + arm * n * span * 0.05f + r.range(-0.3f, 0.3f) * size
        val sz = size * r.range(0.8f, 1.1f)
        val flap = r.range(-0.2f, 0.45f)
        path.reset()
        path.moveTo(bx - sz, by - sz * (0.15f + flap))
        path.quadTo(bx - sz * 0.4f, by - sz * 0.3f, bx, by)
        path.quadTo(bx + sz * 0.4f, by - sz * 0.3f, bx + sz, by - sz * (0.15f + flap))
        canvas.drawPath(path, s)
    }
}

/** Ice going out on the flood: flat slabs, white on top over a blue-grey edge, smaller far off. */
internal fun Painting.floes(top: Float, bottom: Float, count: Int, seed: Int) {
    val r = Random(seed)
    val slab = Path()
    repeat(count) {
        val v = r.nextFloat().pow(1.5f)
        val y = top + (bottom - top) * v
        val x = r.nextFloat() * w
        val size = h * (0.008f + v * 0.05f) * r.range(0.6f, 1.3f)
        val flat = 0.2f + v * 0.12f
        slab.reset()
        for (k in 0 until 7) {
            val a = k * 2f * PI.toFloat() / 7 + r.range(-0.25f, 0.25f)
            val rr = size * r.range(0.55f, 1f)
            val px = x + cos(a) * rr
            val py = y + sin(a) * rr * flat
            if (k == 0) slab.moveTo(px, py) else slab.lineTo(px, py)
        }
        slab.close()
        val thick = max(0.2f, size * flat * 0.3f)
        canvas.withTranslation(0f, thick) {
            canvas.drawPath(slab, pen(c(0x7E90A6)))
        }
        val face = pen()
        face.shader = LinearGradient(x - size, y, x + size, y, c(0xDCE4EE), c(0xF8FAFC), Shader.TileMode.CLAMP)
        canvas.drawPath(slab, face)
    }
}

/** Бабье лето: threads of gossamer drifting on the still air, catching the light along their length. */
internal fun Painting.gossamer(count: Int, seed: Int, color: Int) {
    val r = Random(seed)
    repeat(count) {
        val x0 = r.nextFloat() * w
        val y0 = r.range(h * 0.08f, h * 0.62f)
        val len = w * r.range(0.14f, 0.32f)
        val a = r.range(-0.35f, 0.15f)
        val x1 = x0 + cos(a) * len
        val y1 = y0 + sin(a) * len
        val p = stroke(0, 0.14f)
        p.shader = LinearGradient(x0, y0, x1, y1, intArrayOf(Tone.alpha(color, 0f), Tone.alpha(color, 0.75f), Tone.alpha(color, 0f)), null, Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo((x0 + x1) / 2f + r.range(-0.1f, 0.1f) * len, (y0 + y1) / 2f + len * 0.07f, x1, y1)
        canvas.drawPath(path, p)
    }
}

/** Snow driven by a blizzard: faint streaks slanting across, thicker low over the ground. */
internal fun Painting.driven(count: Int, seed: Int, slant: Float, strength: Float) {
    val r = Random(seed)
    repeat(count) {
        val y = h * r.nextFloat().pow(0.7f)
        val x = r.range(-0.1f, 1.2f) * w
        val len = h * r.range(0.04f, 0.13f)
        canvas.drawLine(x, y, x - len, y + len * slant, stroke(Tone.alpha(0xFFFFFFFF.toInt(), strength * r.range(0.22f, 0.6f)), r.range(0.14f, 0.45f)))
    }
}

/** Ice setting along a river: grey-white shelves reaching out from both banks, their edges ragged. */
internal fun Painting.shoreIce(top: Float, bottom: Float, amount: Float, seed: Int) {
    if (amount <= 0.01f) return
    val far = (bottom - top) * 0.22f * amount
    val near = (bottom - top) * 0.55f * amount
    val step = w / 60f
    for ((edge, reach) in listOf(top to far, bottom to -near)) {
        path.reset()
        path.moveTo(-1f, edge)
        var x = -1f
        while (x <= w + step) {
            val n = Noise.fbm(x / (w * 0.08f), edge, seed, 3)
            path.lineTo(x, edge + reach * (0.35f + 0.9f * n))
            x += step
        }
        path.lineTo(w + 1f, edge)
        path.close()
        val p = pen()
        p.shader = LinearGradient(0f, edge, 0f, edge + reach, c(0xC9D2DC), c(0xE8EDF2), Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
    }
}

// endregion

// region Winter

/**
 * «Мороз и солнце»: the snowfield glittering, spruces bowed under snow, smoke rising straight —
 * the week after the New Year, its lights still on the house and a spruce dressed by it.
 */
private fun Painting.january(art: WeekArt, live: Boolean) {
    val s = art.season
    val late = s.within(1)
    val rime = s.frost
    sky(0f to 0x1F4E96, 0.32f to 0x4B82C6, 0.62f to 0x9CBEE4, 0.86f to between(0xE8D9CB, 0xDCE6F2, late), 1f to between(0xF6D5B2, 0xF2E4D2, late), to = h * 0.6f)
    sun(lightX, lightY, h * 0.032f, between(0xFFE6BC, 0xFFF4E2, late), power = 1f, streak = 0.28f)
    val far = ridge(h * 0.5f, h * 0.07f, h * 0.8f, 11, sharp = 0.25f)
    land(far, h, c(0xB0BADB), c(0xD4DAEC), texture = 0.05f, rim = c(0xFFF1DC), rimStrength = 0.45f, shading = 0.25f)
    haze(h * 0.53f, h * 0.42f, c(0xF1DFD0), 0.55f + rime * 0.15f)
    val forestLine = treeLine(ridge(h * 0.57f, h * 0.02f, h * 0.5f, 12), h * 0.045f..h * 0.1f, h * 0.026f, conifers = 0.92f, seed = 13)
    forest(forestLine, h * 0.6f, rimed(tones(0x223656, 0x3A5278, 0x5E769C, 0x9AAACA, 0xE8ECF6), rime * 0.4f), 14, leaf = h * 0.012f, depth = h * 0.05f, rim = 0.45f)
    haze(h * 0.59f, h * 0.5f, c(0xEDE3E6), 0.35f + rime * 0.2f)
    house(w * 0.2f, h * 0.578f, h * 0.055f, c(0x5E4234), c(0x6F5242), 30, snowRoof = c(0xF4F2F8), window = c(0xFFC46B), smoke = c(0xEEE8EE))
    val field = flat(h * 0.578f, wave = h * 0.006f, seed = 15)
    snowfield(field, h * 0.56f, c(0xFFF8EE), c(0xA6BAE4), c(0xE6E2EE), 16, relief = 0.6f)
    // Spruces on the right, the nearest cut by the frame; their shadows reach across the snow.
    val spruceTones = rimed(tones(0x0C1D1C, 0x17332E, 0x28503F, 0x40705A, 0x6A9274), rime * 0.32f)
    val trees = listOf(Triple(0.99f, 1.02f, 0.92f), Triple(0.86f, 0.94f, 0.62f), Triple(0.76f, 0.8f, 0.36f), Triple(0.69f, 0.72f, 0.2f))
    for ((i, t) in trees.withIndex().reversed()) {
        val (fx, fy, fh) = t
        castShadow(w * fx, h * fy, h * fh * 0.9f, h * fh * 0.26f, c(0x7F9BD4), 0.4f)
        conifer(w * fx, h * fy, h * fh, h * fh * 0.42f, spruceTones, 20 + i, snow = c(0xFFFFFF), snowShade = c(0x9DB2DE), snowLoad = 0.55f + rime * 0.12f)
    }
    conifer(w * 0.06f, h * 0.7f, h * 0.2f, h * 0.085f, spruceTones, 27, snow = c(0xFFFFFF), snowShade = c(0x9DB2DE))
    conifer(w * 0.13f, h * 0.66f, h * 0.12f, h * 0.05f, spruceTones, 28, snow = c(0xFFFFFF), snowShade = c(0x9DB2DE))
    // The New Year's lights along the eaves, and a dressed tree by the house.
    if (s.festive > 0.02f) {
        val hx = w * 0.2f
        val hw = h * 0.055f
        garland(floatArrayOf(hx - hw * 0.62f, hx, hx + hw * 0.62f), floatArrayOf(h * 0.578f - hw * 0.5f, h * 0.578f - hw * 0.92f, h * 0.578f - hw * 0.5f), hw * 0.04f, 10, h * 0.006f, s.festive, 33)
        // The New Year's tree by the house.
        val tx = w * 0.3f
        val tg = h * 0.64f
        val th = h * 0.15f
        conifer(tx, tg, th, th * 0.42f, spruceTones, 36, snow = c(0xFFFFFF), snowShade = c(0x9DB2DE), snowLoad = 0.45f)
        for (k in 0 until 5) {
            val y0 = tg - th * (0.8f - k * 0.16f)
            val half = th * (0.07f + k * 0.045f)
            garland(floatArrayOf(tx - half, tx + half), floatArrayOf(y0, y0 + th * 0.06f), th * 0.012f, 4, h * 0.0065f, s.festive, 34 + k)
        }
        treeStar(tx, tg - th * 0.98f, h * 0.008f, s.festive)
    }
    sparkles(h * 0.6f, h, (w * h / 60f * (0.8f + rime * 0.5f)).roundToInt(), c(0xFFFFFF), 31, 0.26f)
    if (!live) sparkles(h * 0.15f, h * 0.56f, (w * h / 380f * (0.6f + rime)).roundToInt(), c(0xFFF6E0), 32, 0.2f)
    done(bloom = 0.55f, vignette = 0.2f)
}

/** After Grabar's «Февральская лазурь»: birches rising white into a deep azure sky. */
private fun Painting.february(art: WeekArt, live: Boolean) {
    sky(0f to 0x1A4DA6, 0.4f to 0x3B7ED6, 0.75f to 0x99C2EC, 1f to 0xE4EEF8, to = h * 0.85f)
    glow(lightX, lightY, h * 1.3f, c(0xFFF4DC), 0.45f)
    val forestLine = treeLine(flat(h * 0.8f, h * 0.006f, seed = 2), h * 0.03f..h * 0.07f, h * 0.022f, conifers = 0.75f, seed = 3)
    forest(forestLine, h * 0.83f, tones(0x4A5C8C, 0x6478A6, 0x8496C0, 0xB4C2E0, 0xE4EAF6), 4, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.35f)
    haze(h * 0.82f, h * 0.74f, c(0xDCE7F6), 0.45f)
    val field = flat(h * 0.815f, h * 0.008f, seed = 5)
    snowfield(field, h * 0.8f, c(0xFFFFFF), c(0x86A6DE), c(0xD8E4F4), 6, relief = 0.8f)
    // The birches: a clump rising out of frame, leaning apart, their crowns a lace of reddish twigs.
    val xs = listOf(0.47f, 0.56f, 0.64f, 0.7f, 0.8f, 0.9f, 0.99f)
    val heights = listOf(0.98f, 1.25f, 1.15f, 1.3f, 1.12f, 1.2f, 1.18f)
    for ((i, fx) in xs.withIndex()) castShadow(w * fx, h * (0.9f + (i % 3) * 0.03f), h * 0.55f, h * 0.02f, c(0x6F92D6), 0.35f)
    for ((i, fx) in xs.withIndex()) {
        val ground = h * (0.9f + (i % 3) * 0.03f)
        birch(
            w * fx, ground, h * heights[i], h * (0.028f + (i % 2) * 0.012f), 40 + i,
            lean = (fx - 0.72f) * 0.22f,
            bark = c(0xFFFCF4), barkShade = c(0x93A7CF), twig = c(0x7E4E4A), weeping = 0.35f,
        )
    }
    if (!live) snowfall((w * h / 900f).roundToInt(), 7, size = 0.5f)
    sparkles(h * 0.83f, h, (w * h / 110f).roundToInt(), c(0xFFFFFF), 8, 0.24f)
    done(bloom = 0.35f, vignette = 0.18f)
}

/**
 * A village asleep in deep snow under the full moon, smoke from the chimneys, the moon in its
 * halo, snow coming down — the middle of December, the New Year's lights not up yet.
 */
private fun Painting.december(art: WeekArt, live: Boolean) {
    val s = art.season
    val deep = s.within(12)
    val phase = 1f
    moon(lightX, lightY, h * 0.055f, phase, c(0xF4F1E6), c(0xBFD0FF), 0.6f + phase * 0.4f)
    clouds(h * 0.28f, h * 0.5f, 0.32f, h * 0.12f, 5, c(0x7D8DC0), c(0x2A3868), opacity = 0.55f, silver = 0.8f)
    val far = ridge(h * 0.56f, h * 0.06f, h * 0.7f, 6)
    land(far, h, c(0x4A5E96), c(0x6A7FB6), texture = 0.08f, rim = c(0xC9D8FF), rimStrength = 0.5f, shading = 0.25f)
    val forestLine = treeLine(ridge(h * 0.6f, h * 0.02f, h * 0.4f, 7), h * 0.04f..h * 0.09f, h * 0.024f, conifers = 0.95f, seed = 8)
    forest(forestLine, h * 0.66f, tones(0x0C1430, 0x16224A, 0x24345E, 0x5A6C9C, 0xC8D4F4), 9, leaf = h * 0.011f, depth = h * 0.05f, rim = 0.4f)
    haze(h * 0.63f, h * 0.55f, c(0x6E82B8), 0.4f)
    val hill = ridge(h * 0.68f, h * 0.05f, h * 0.9f, 10)
    snowfield(hill, h * 0.62f, c(0xD4DEF8), c(0x5E72AC), c(0x7F92C4), 11, relief = 0.8f + deep * 0.5f)
    for ((i, fx) in listOf(0.14f, 0.3f, 0.42f, 0.88f).withIndex()) {
        val gx = w * fx
        val size = h * (0.07f - i * 0.006f)
        val ground = hill.at(gx) + h * 0.012f
        house(gx, ground, size, c(0x3A2A26), c(0x2E2426), 50 + i, snowRoof = c(0xDCE4FA), window = c(0xFFB85C), smoke = c(0x9AA8CE))
    }
    val spruceTones = rimed(tones(0x060C1A, 0x0E1A30, 0x1C2E4C, 0x33496C, 0x566C90), s.frost * 0.18f)
    for ((i, fx) in listOf(0.55f, 0.7f, 0.78f, 0.05f).withIndex()) {
        val gx = w * fx
        conifer(gx, hill.at(gx) + h * 0.02f, h * (0.16f + i * 0.02f), h * 0.07f, spruceTones, 60 + i, snow = c(0xE6EEFF), snowShade = c(0x7F93C8), snowLoad = 0.5f + deep * 0.15f)
    }
    val field = flat(h * 0.8f, h * 0.02f, seed = 12)
    snowfield(field, h * 0.62f, c(0xC4D2F2), c(0x4C5E98), c(0x6E82B8), 13, relief = 1f + deep * 0.5f)
    fence(w * 0.02f, w * 0.5f, h * 0.84f, h * 0.05f * (1f - deep * 0.3f), c(0x2E2A34), 14, snow = c(0xE0E8FF))
    conifer(w * 0.93f, h * 1.02f, h * 0.58f, h * 0.24f, spruceTones, 15, snow = c(0xF0F4FF), snowShade = c(0x8295CC), snowLoad = 0.55f + deep * 0.15f)
    sparkles(h * 0.7f, h, (w * h / 80f).roundToInt(), c(0xDDE6FF), 16, 0.24f)
    if (!live) snowfall((w * h / 110f * (0.7f + deep * 0.6f)).roundToInt(), 17, size = 0.7f)
    done(bloom = 0.6f, vignette = 0.3f, threshold = 0.6f)
}

// endregion

// region Spring

/**
 * After Savrasov's «Грачи прилетели»: the thaw — rooks back at their nests in the birches, the
 * snow going in patches, puddles full of sky, the twigs reddening with the rising sap.
 */
private fun Painting.march(art: WeekArt, live: Boolean) {
    val s = art.season
    val t = s.within(3)
    sky(0f to between(0x3E72BE, 0x3478CE, t), 0.4f to between(0x7AA6DC, 0x74AEE8, t), 0.8f to 0xC4DCF2, 1f to 0xEDF1F2, to = h * 0.62f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF4DA), power = 0.85f + t * 0.1f, streak = 0.2f)
    clouds(h * 0.05f, h * 0.45f, 0.4f, h * 0.13f, 21, c(0xFFFFFF), c(0xAFC0D8), opacity = 0.9f)
    val forestLine = treeLine(ridge(h * 0.57f, h * 0.02f, h * 0.6f, 22), h * 0.03f..h * 0.07f, h * 0.02f, conifers = 0.5f, seed = 23)
    forest(forestLine, h * 0.62f, tones(0x4A4458, 0x645C70, 0x8A8296, 0xB4AEBC, 0xE4E0E4), 24, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.3f)
    church(w * 0.3f, forestLine.at(w * 0.3f) + h * 0.03f, h * 0.06f, c(0xF4F0E8), c(0xE0B24A))
    haze(h * 0.6f, h * 0.52f, c(0xE3EAF0), 0.45f)
    val ground = flat(h * 0.6f, h * 0.008f, seed = 25)
    thaw(ground, h * 0.58f, c(0xF6F8FA), c(0xB4C4DC), c(0x7A6A5A), c(0x4A3E36), c(0x5E96DA), c(0xC8DCF0), c(0xE0E4EA), 26, cover = s.snow)
    // Birches with rooks' nests, and the rooks circling.
    val twig = between(0x6E4E4A, 0x8A4638, t)
    for ((i, fx) in listOf(0.08f, 0.16f, 0.83f, 0.92f).withIndex()) {
        val gy = h * (0.92f - (i % 2) * 0.04f)
        castShadow(w * fx, gy, h * 0.3f, h * 0.02f, c(0x8FA3C8), 0.3f)
        birch(w * fx, gy, h * (0.95f - (i % 2) * 0.12f), h * 0.028f, 70 + i, lean = if (fx < 0.5f) 0.04f else -0.04f, bark = c(0xFBF8F0), barkShade = c(0x9EA9BC), twig = twig, weeping = 0.3f)
    }
    val nest = pen(c(0x2A2220))
    for ((nx, ny) in listOf(0.1f to 0.22f, 0.15f to 0.3f, 0.86f to 0.18f, 0.9f to 0.27f)) {
        canvas.drawOval(w * nx - h * 0.016f, h * ny - h * 0.009f, w * nx + h * 0.016f, h * ny + h * 0.011f, nest)
    }
    val rooks = 7
    birds(w * 0.5f, h * 0.2f, rooks, w * (0.08f + rooks * 0.012f), h * 0.018f, c(0x22201E), 27)
    grass(h * 0.9f, h * 1.02f, (w / 3f).roundToInt(), h * 0.05f, veiled(tones(0x9C8E5E, 0x7F7550, 0xB4A06A), c(0x7E9A4A), Tone.smooth(0.55f, 1f, t) * 0.6f), 28, lean = 0.2f)
    sparkles(h * 0.62f, h, (w * h / 160f * (0.4f + s.snow)).roundToInt(), c(0xFFFFFF), 29, 0.24f)
    done(bloom = 0.4f, vignette = 0.16f)
}

/**
 * After Levitan's «Весна. Большая вода»: flood water to the horizon, birches standing in it and
 * doubled in it, a rainbow after the shower.
 */
private fun Painting.april(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x6690C4, 0.4f to 0x9FBFE2, 0.8f to 0xD6E4EE, 1f to 0xEEF1E6, to = h * 0.56f)
    sun(lightX, lightY, h * 0.028f, c(0xFFF3D8), power = 0.75f, streak = 0.15f)
    clouds(0f, h * 0.42f, 0.5f, h * 0.14f, 31, c(0xFFFFFF), c(0x8D9DB6), opacity = 0.92f, stretch = 2.6f)
    rainbow(w * 0.28f, h * 0.62f, h * 0.42f, h * 0.035f, 0.32f, h * 0.55f)
    val forestLine = treeLine(flat(h * 0.53f, h * 0.006f, seed = 32), h * 0.03f..h * 0.06f, h * 0.022f, conifers = 0.35f, seed = 33)
    forest(forestLine, h * 0.61f, veiled(tones(0x4C5A52, 0x66746A, 0x869484, 0xB2BCA6, 0xE0E6D4), c(0x9AB070), s.leaves * 0.6f), 34, leaf = h * 0.01f, depth = h * 0.035f, rim = 0.3f)
    haze(h * 0.555f, h * 0.47f, c(0xE3EAEA), 0.45f + s.mist * 0.2f)
    val crown = s.birchLeaves()
    val trees = listOf(0.12f to 0.62f, 0.2f to 0.72f, 0.27f to 0.58f, 0.62f to 0.6f, 0.7f to 0.66f, 0.78f to 0.56f, 0.88f to 0.7f)
    for ((i, tr) in trees.withIndex()) {
        val (fx, fh) = tr
        birch(w * fx, h * 0.6f, h * fh * 0.9f, h * 0.018f, 80 + i, lean = (fx - 0.5f) * 0.06f, bark = c(0xF4F1E8), barkShade = c(0xA3ABB6), twig = c(0x6E5A50), crown = if (s.leaves > 0.01f) crown else null, leaves = s.leaves * 1.5f, leafSize = h * 0.0035f, weeping = 0.4f)
    }
    val shore = flat(h * 0.6f, 0f)
    land(shore, h * 0.605f, veiled(intArrayOf(c(0x6C745A)), c(0x5E8A3A), s.grass * 0.6f)[0], c(0x5A624C), texture = 0.2f)
    water(h * 0.603f, h, c(0x55708E), 0.26f, ripple = 0.6f, seed = 35, glint = c(0xFFF8E0), glintStrength = 0.5f)
    if (!live) {
        val p = stroke(Tone.alpha(c(0xE8F0F8), 0.3f), 0.2f)
        val r = Random(36)
        repeat((w * h / 200f).roundToInt()) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h * 0.9f
            canvas.drawLine(x, y, x - h * 0.008f, y + h * 0.04f, p)
        }
    }
    done(bloom = 0.35f, vignette = 0.14f)
}

/** An apple orchard in full blossom, white and pink, the new grass full of dandelions. */
private fun Painting.may(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x5896DC, 0.4f to 0x98C4EC, 0.8f to 0xDCE8F2, 1f to 0xF6E4E0, to = h * 0.58f)
    sun(lightX, lightY, h * 0.03f, c(0xFFEFD2), power = 0.95f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.4f, 0.36f, h * 0.12f, 41, c(0xFFFFFF), c(0xB8C4DA), opacity = 0.9f)
    val far = ridge(h * 0.52f, h * 0.05f, h * 0.8f, 42)
    land(far, h, c(0x9CB8A4), c(0xB0C6B0), texture = 0.08f, rim = c(0xFFF6DA), rimStrength = 0.35f, shading = 0.15f)
    haze(h * 0.54f, h * 0.45f, c(0xEDE8EA), 0.45f)
    val forestLine = treeLine(ridge(h * 0.575f, h * 0.02f, h * 0.5f, 43), h * 0.04f..h * 0.08f, h * 0.028f, conifers = 0.1f, seed = 44)
    forest(forestLine, h * 0.62f, tones(0x3E6A38, 0x55834A, 0x77A060, 0xA8C688, 0xE8F0D8), 45, leaf = h * 0.01f, depth = h * 0.04f, variety = tones(0xF2F0F0, 0xF4D8E2, 0x8CB870), varietyScale = h * 0.025f, rim = 0.3f)
    haze(h * 0.6f, h * 0.53f, c(0xF2E6EC), 0.3f)
    val meadowLine = flat(h * 0.6f, h * 0.008f, seed = 46)
    val speckle = tones(0xFFD42E, 0xFFE35A, 0xFFFFFF)
    meadow(meadowLine, h * 0.58f, s.grassTones(), c(0xB9CCA4), 47, speckle = speckle, speckleAmount = 0.28f)
    // The orchard, receding: smaller trees further off.
    val crowns = s.crown()
    val blossom = tones(0xFFFFFF, 0xFBE3EA, 0xF5C6D6, 0xFFF4F6)
    val rows = listOf(
        Triple(0.64f, 0.14f, listOf(0.06f, 0.28f, 0.5f, 0.72f, 0.94f)),
        Triple(0.72f, 0.24f, listOf(0.18f, 0.62f)),
        Triple(0.9f, 0.46f, listOf(0.0f, 0.92f)),
    )
    var seed = 400
    for ((ground, size, xs) in rows) {
        for (fx in xs) {
            val gx = w * fx
            castShadow(gx, h * ground, h * size * 0.5f, h * size * 0.6f, c(0x2E5A22), 0.3f)
            broadleaf(gx, h * ground, h * size, h * size * 1.05f, h * size * 0.72f, c(0x5A4636), crowns, seed++, leaf = h * size * 0.035f, blossom = blossom, blossomAmount = 0.2f + s.blossom * 0.7f, lumps = 12, leaves = (0.45f + s.leaves * 0.55f))
        }
    }
    flowers(h * 0.84f, h, (w * 0.35f).roundToInt(), listOf(Bloom.Dandelion, Bloom.Dandelion, Bloom.Chamomile), 48, size = h * 0.012f)
    if (!live) fallingPetals((w * h / 300f * (0.3f + s.blossom)).roundToInt(), tones(0xFFFFFF, 0xFBDDE6, 0xF7C3D2), 49, size = h * 0.006f)
    done(bloom = 0.35f, vignette = 0.12f)
}

// endregion

// region Summer

/** An old oak on a meadow in flower — buttercups and daisies — clouds towering in a deep blue sky. */
private fun Painting.june(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x2766C8, 0.45f to 0x64A0E6, 0.8f to 0xB6D6F2, 1f to 0xE4EFF4, to = h * 0.6f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF8E6), power = 1f, streak = 0.2f)
    clouds(h * 0.04f, h * 0.55f, 0.46f, h * 0.17f, 51, c(0xFFFFFF), c(0x8FA3C4), opacity = 0.97f, stretch = 1.6f, softness = 0.12f)
    val far = ridge(h * 0.55f, h * 0.045f, h * 0.9f, 52)
    land(far, h, c(0x86A6C0), c(0x9AB8C8), texture = 0.08f, shading = 0.2f)
    haze(h * 0.56f, h * 0.47f, c(0xDDEBF2), 0.5f)
    val forestLine = treeLine(ridge(h * 0.6f, h * 0.02f, h * 0.5f, 53), h * 0.05f..h * 0.1f, h * 0.03f, conifers = 0.3f, seed = 54)
    forest(forestLine, h * 0.66f, tones(0x1E3A24, 0x2E5232, 0x4A7042, 0x7A9A5E, 0xC8DAB0), 55, leaf = h * 0.011f, depth = h * 0.05f, rim = 0.35f)
    haze(h * 0.625f, h * 0.56f, c(0xCFE2EA), 0.3f)
    val meadowLine = ridge(h * 0.64f, h * 0.03f, w * 0.9f, 56, lift = h * 0.03f, liftX = w * 0.32f, liftWidth = w * 0.3f)
    val bloom = tones(0xFFE14A, 0xFFD42E, 0xFFFFFF, 0xFFE14A)
    meadow(meadowLine, h * 0.6f, s.grassTones(), c(0xA6C8B0), 57, speckle = bloom, speckleAmount = 0.35f)
    // The oak, and its pool of shade.
    val ox = w * 0.3f
    val oy = meadowLine.at(ox) + h * 0.03f
    castShadow(ox, oy, h * 0.22f, h * 0.4f, c(0x1E4A16), 0.5f)
    val oak = s.crown().let { five -> intArrayOf(Tone.shade(five[0], -0.2f)) + five }
    broadleaf(ox, oy, h * 0.54f, h * 0.64f, h * 0.42f, c(0x4A3A2E), oak, 58, leaf = h * 0.014f, lumps = 22)
    grass(h * 0.8f, h * 1.02f, (w * 0.9f).roundToInt(), h * 0.046f, tones(0x5E9A3E, 0x78B44C, 0x96C95C, 0x4A8534), 59)
    val kinds = listOf(Bloom.Buttercup, Bloom.Buttercup, Bloom.Dandelion, Bloom.Chamomile)
    flowers(h * 0.82f, h, (w * 0.7f).roundToInt(), kinds, 60, size = h * 0.012f)
    done(bloom = 0.35f, vignette = 0.12f)
}

/** After Shishkin's «Рожь»: ripe rye at sunset, a road through it, pines standing tall. */
private fun Painting.july(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x42559E, 0.3f to 0x9E7A9E, 0.6f to 0xF5A273, 0.85f to 0xFFC98C, 1f to 0xFFE0A6, to = h * 0.56f)
    sun(lightX, lightY, h * 0.045f, c(0xFFD08A), power = 1f, streak = 0.45f)
    clouds(h * 0.02f, h * 0.44f, 0.38f, h * 0.12f, 61, c(0xFFC89A), c(0x7E5E86), opacity = 0.9f, stretch = 3f, silver = 0.9f)
    val forestLine = treeLine(ridge(h * 0.535f, h * 0.012f, h * 0.6f, 62), h * 0.025f..h * 0.055f, h * 0.018f, conifers = 0.6f, seed = 63)
    forest(forestLine, h * 0.58f, tones(0x3A2A4A, 0x5A4062, 0x7E5E7E, 0xB88A8A, 0xFFC090), 64, leaf = h * 0.009f, depth = h * 0.03f, rim = 0.5f)
    haze(h * 0.55f, h * 0.44f, c(0xFFC895), 0.5f)
    val field = flat(h * 0.55f, h * 0.004f, seed = 65)
    val green = tones(0x4A5A22, 0x74862E, 0xA2AC44, 0xC8C668, 0xE6DC96)
    val gold = tones(0x6E3E1A, 0xA0602A, 0xD08E3E, 0xF0B45A, 0xFFD88A)
    // Shishkin's rye stands ripe: gold with the last of the green in it.
    val ripe = 0.82f
    rye(field, h * 0.54f, IntArray(5) { Tone.mix(green[it], gold[it], Tone.smooth(0.2f, 1f, ripe)) }, c(0xF2B888), c(0xFFE0A0), 66)
    // The road: a paler track winding into the distance.
    val road = pen()
    road.shader = LinearGradient(0f, h * 0.55f, 0f, h, Tone.alpha(c(0xE8C08A), 0.55f), Tone.alpha(c(0xB88A5A), 0.85f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(w * 0.47f, h * 0.552f)
    path.quadTo(w * 0.42f, h * 0.7f, w * 0.18f, h * 1.01f)
    path.lineTo(w * 0.5f, h * 1.01f)
    path.quadTo(w * 0.5f, h * 0.7f, w * 0.49f, h * 0.552f)
    path.close()
    canvas.drawPath(path, road)
    rays(lightX, lightY, h * 0.9f, PI.toFloat() * 0.5f, 1.8f, 9, c(0xFFD9A0), 0.14f, 67)
    for ((i, t) in listOf(0.2f to 0.75f, 0.28f to 0.62f, 0.8f to 0.82f, 0.88f to 0.66f).withIndex()) {
        val (fx, fh) = t
        pine(w * fx, h * (0.6f + fh * 0.06f), h * fh, 70 + i, tones(0x141E14, 0x22301C, 0x3A4024, 0x6A5A30, 0xC08A4A), c(0x3A2418), c(0xE08A4A))
    }
    wheat(h * 0.78f, h * 1.02f, (w * 1.6f).roundToInt(), 68, between(0x8A8A36, 0xB07A36, ripe), between(0xB8B050, 0xD89A4A, ripe), between(0xE8E0A0, 0xFFD890, ripe), h * 0.09f)
    flowers(h * 0.82f, h, (w * 0.25f).roundToInt(), listOf(Bloom.Cornflower), 69, size = h * 0.011f)
    if (!live) fireflies(h * 0.5f, h * 0.95f, (w * h / 900f).roundToInt(), c(0xFFE9A0), 71, size = 0.45f)
    done(bloom = 0.6f, vignette = 0.24f, threshold = 0.7f)
}

/**
 * Falling stars over a still lake: the Perseids at their height, all from one corner of the sky,
 * the Milky Way, a young moon, haystacks on the shore.
 */
private fun Painting.august(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x050920, 0.4f to 0x0F1842, 0.8f to 0x222C63, 1f to 0x3C4478, to = h * 0.62f)
    glow(w * 0.5f, h * 0.64f, w * 0.8f, c(0x6A5A9A), 0.35f, squash = 0.3f)
    milkyWay(w * 0.02f, h * 0.02f, w * 0.95f, h * 0.62f, h * 0.18f, h * 0.6f, 0.9f)
    stars(h * 0.6f, (w * h / 40f).roundToInt(), 81)
    val shower = 6
    val streaks = listOf(
        floatArrayOf(0.34f, 0.2f, 0.2f, 2.6f, 0.9f), floatArrayOf(0.62f, 0.1f, 0.14f, 2.5f, 0.7f),
        floatArrayOf(0.16f, 0.36f, 0.1f, 2.7f, 0.5f), floatArrayOf(0.48f, 0.3f, 0.12f, 2.55f, 0.6f),
        floatArrayOf(0.74f, 0.24f, 0.09f, 2.65f, 0.55f), floatArrayOf(0.24f, 0.08f, 0.16f, 2.5f, 0.75f),
    )
    // At the peak the stars fall bright and long.
    for (m in streaks.take(shower)) meteor(w * m[0], h * m[1], h * m[2] * 1.35f, m[3], (m[4] * 1.25f).coerceAtMost(1f), width = 0.85f)
    val phase = 0.18f
    moon(lightX, lightY, h * 0.035f, phase, c(0xFFF1D8), c(0xFFD9A8), 0.5f + phase * 0.5f)
    val forestLine = treeLine(ridge(h * 0.6f, h * 0.02f, h * 0.6f, 82), h * 0.035f..h * 0.08f, h * 0.024f, conifers = 0.7f, seed = 83)
    forest(forestLine, h * 0.63f, tones(0x05070F, 0x080B18, 0x0C1022, 0x161C36, 0x3A4270), 84, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.25f)
    water(h * 0.625f, h * 0.86f, c(0x0A1030), 0.35f, ripple = 0.35f, seed = 85)
    if (s.mist > 0.2f) fog(h * 0.58f, h * 0.84f, c(0x8A94C4), 1.3f * s.mist, h * 0.05f, 90)
    val near = ridge(h * 0.86f, h * 0.03f, w * 0.7f, 86)
    land(near, h, c(0x14192E), c(0x0A0D1C), texture = 0.12f, rim = c(0x4A5288), rimStrength = 0.3f)
    for ((i, fx) in listOf(0.18f, 0.3f, 0.78f).withIndex()) {
        val gx = w * fx
        haystack(gx, near.at(gx) + h * 0.02f, h * (0.12f - i * 0.02f), h * (0.12f - i * 0.02f), c(0x3A3656), c(0x14142A), 87 + i)
    }
    grass(h * 0.9f, h * 1.02f, (w * 0.8f).roundToInt(), h * 0.06f, tones(0x10142A, 0x1A2036, 0x232A44), 88)
    if (!live) fireflies(h * 0.72f, h * 0.98f, (w * h / 700f * (1f - s.mist)).roundToInt(), c(0xD9F59A), 89, size = 0.45f)
    done(bloom = 0.5f, vignette = 0.3f, threshold = 0.55f, grain = 0.02f)
}

// endregion

// region Autumn

/**
 * After Levitan's «Золотая осень»: golden birches along a blue river, the meadow fading to rust,
 * the first leaves down and floating on the water.
 */
private fun Painting.september(art: WeekArt, live: Boolean) {
    val s = art.season
    val gold = Tone.smooth(0.09f, 0.5f, s.autumn)
    sky(0f to 0x3A78CC, 0.45f to 0x7BAEE6, 0.85f to 0xC8DCEC, 1f to 0xEEEDE2, to = h * 0.56f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF0D0), power = 0.95f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.42f, 0.4f, h * 0.14f, 91, c(0xFFFFFF), c(0xA8B6CC), opacity = 0.92f)
    val forestLine = treeLine(ridge(h * 0.5f, h * 0.02f, h * 0.7f, 92), h * 0.03f..h * 0.06f, h * 0.02f, conifers = 0.4f, seed = 93)
    val farGreen = tones(0x2E4A30, 0x44633E, 0x5E7E4A, 0x8EA468, 0xD0DCB0)
    val farGold = tones(0x3E4A30, 0x5E6A3E, 0x8A8A4A, 0xC4B060, 0xF0E4B0)
    forest(forestLine, h * 0.58f, IntArray(5) { Tone.mix(farGreen[it], farGold[it], gold) }, 94, leaf = h * 0.01f, depth = h * 0.04f, variety = veiled(tones(0xE8B43A, 0x6A7A3A, 0xD8963A), c(0x6E8A40), 1f - gold), varietyScale = h * 0.02f, rim = 0.3f)
    haze(h * 0.52f, h * 0.44f, c(0xE6E8E2), 0.45f)
    val bankLine = ridge(h * 0.56f, h * 0.015f, w * 0.6f, 95)
    meadow(bankLine, h * 0.52f, s.grassTones(), c(0xD8D4B8), 96)
    // The river: from far left sweeping to the near right, mirroring the sky and the birches.
    val river = Path().apply {
        moveTo(-1f, h * 0.575f)
        cubicTo(w * 0.3f, h * 0.57f, w * 0.55f, h * 0.6f, w * 0.52f, h * 0.7f)
        cubicTo(w * 0.5f, h * 0.8f, w * 0.7f, h * 0.9f, w * 1.01f, h * 0.92f)
        lineTo(w * 1.01f, h * 1.01f)
        lineTo(w * 0.62f, h * 1.01f)
        cubicTo(w * 0.4f, h * 0.9f, w * 0.3f, h * 0.76f, w * 0.34f, h * 0.68f)
        cubicTo(w * 0.36f, h * 0.62f, w * 0.2f, h * 0.595f, -1f, h * 0.595f)
        close()
    }
    val crowns = s.birchLeaves()
    val trees = listOf(0.6f to 0.56f, 0.66f to 0.66f, 0.72f to 0.6f, 0.8f to 0.72f, 0.88f to 0.64f, 0.95f to 0.7f, 0.1f to 0.5f, 0.04f to 0.46f)
    for ((i, t) in trees.withIndex()) {
        val (fx, fh) = t
        val ground = h * (if (fx > 0.5f) 0.58f + (fx - 0.6f) * 0.12f else 0.6f)
        birch(w * fx, ground, h * fh, h * 0.02f, 100 + i, lean = (i % 3 - 1) * 0.03f, crown = crowns, leaves = 0.9f * s.leaves, leafSize = h * 0.007f, twig = c(0x5B463A), weeping = 0.45f)
    }
    water(h * 0.57f, h, c(0x2F5E98), 0.35f, ripple = 0.5f, seed = 97, glint = c(0xFFFFFF), glintStrength = 0.4f, clip = river)
    // Leaves come down onto the water and float there.
    if (s.autumn > 0.3f) {
        val r = Random(98)
        canvas.withClip(river) {
            repeat((w * h / 400f * Tone.smooth(0.3f, 0.5f, s.autumn)).roundToInt()) {
                val y = h * r.range(0.6f, 1f)
                val x = r.nextFloat() * w
                val sz = h * 0.004f * (0.4f + (y - h * 0.6f) / h * 3f)
                drawOval(x - sz, y - sz * 0.4f, x + sz, y + sz * 0.4f, pen(crowns[2 + r.nextInt(3)]))
            }
        }
    }
    grass(h * 0.86f, h * 1.02f, (w * 0.5f).roundToInt(), h * 0.04f, s.grassTones().copyOfRange(1, 5), 98)
    if (!live) fallingLeaves((w * h / 500f * (0.3f + gold)).roundToInt(), crowns, 99, size = h * 0.008f)
    done(bloom = 0.4f, vignette = 0.14f)
}

/**
 * «Багрянец»: a misty morning in a red and gold forest at the height of its colour, the low sun's
 * rays through the trees, the first leaves coming down onto the carpet of them.
 */
private fun Painting.october(art: WeekArt, live: Boolean) {
    val s = art.season
    val bare = 1f - (s.leaves / 0.66f).coerceIn(0f, 1f)
    val greyish = c(0xA8A6AE)
    sky(0f to Tone.mix(c(0x6A77A2), c(0x5E6A86), bare), 0.35f to Tone.mix(c(0xB39896), greyish, bare * 0.8f), 0.7f to Tone.mix(c(0xEBBF9E), c(0xC8C0BA), bare * 0.8f), 1f to Tone.mix(c(0xF7DAB8), c(0xDCD4CC), bare * 0.7f), to = h * 0.6f)
    sun(lightX, lightY, h * 0.04f, c(0xFFDAA8), power = 0.9f - bare * 0.35f, streak = 0.3f)
    val farLine = treeLine(ridge(h * 0.48f, h * 0.03f, h * 0.6f, 101), h * 0.05f..h * 0.1f, h * 0.028f, conifers = 0.45f, seed = 102)
    forest(farLine, h * 0.62f, veiled(tones(0x8A6A6A, 0xA88480, 0xC4A094, 0xDEBCA8, 0xF6DCC4), c(0xA4A0A8), bare * 0.6f), 103, leaf = h * 0.012f, depth = h * 0.05f, rim = 0.3f)
    fog(h * 0.36f, h * 0.62f, c(0xFBE3CA), 0.5f + s.mist * 0.5f, h * 0.08f, 104)
    val midLine = treeLine(ridge(h * 0.58f, h * 0.03f, h * 0.5f, 105), h * 0.08f..h * 0.16f, h * 0.032f, conifers = 0.25f, seed = 106)
    val mid = tones(0x3A1A14, 0x6A2A1C, 0xA8442A, 0xDC7A3A, 0xFFC27A)
    val midBare = tones(0x2E2622, 0x463A34, 0x5E504A, 0x7E6E66, 0xA8988E)
    forest(midLine, h * 0.78f, IntArray(5) { Tone.mix(mid[it], midBare[it], Tone.smooth(0.3f, 1f, bare)) }, 107, leaf = h * 0.013f, depth = h * 0.1f, variety = veiled(tones(0xD9573A, 0xE88A3A, 0xF2B64A, 0x4A4A30, 0xC0402E), c(0x5E504A), Tone.smooth(0.4f, 1f, bare)), varietyScale = h * 0.03f, rim = 0.4f)
    fog(h * 0.5f, h * 0.78f, c(0xF6D8BE), 0.4f + s.mist * 0.45f, h * 0.07f, 108)
    rays(lightX, lightY, h * 1.1f, PI.toFloat() * 0.6f, 1.6f, 11, c(0xFFE3B8), 0.2f * (1f - bare * 0.7f), 109)
    val groundLine = flat(h * 0.74f, h * 0.015f, seed = 110)
    val litterBright = tones(0xC0402E, 0xE06A2E, 0xF0A03A, 0xD8C04A, 0x9A3A22)
    val litterBrown = tones(0x6E3A22, 0x8A5230, 0xA06A3E, 0x7E5A3A, 0x5A3A26)
    litter(groundLine, h * 0.66f, IntArray(5) { Tone.mix(litterBright[it], litterBrown[it], Tone.smooth(0.2f, 1f, bare)) }, c(0x4A2A1A), c(0xE8C0A0), 111, rime = Tone.smooth(0.3f, 0.65f, s.frost) * 0.55f)
    castShadow(w * 0.15f, h * 0.9f, h * 0.3f, h * 0.3f, c(0x3A1E14), 0.4f * (1f - bare * 0.5f))
    val leaves = (s.leaves / 0.66f).coerceIn(0.03f, 1f)
    broadleaf(w * 0.15f, h * 0.9f, h * 0.72f, h * 0.58f, h * 0.46f, c(0x3A2A22), s.crown(), 112, leaf = h * 0.013f, back = 0.8f, lumps = 18, leaves = leaves)
    broadleaf(w * 0.86f, h * 0.84f, h * 0.56f, h * 0.46f, h * 0.36f, c(0x3A2A22), along(s.autumn - 0.25f), 113, leaf = h * 0.012f, back = 0.6f, lumps = 16, leaves = (leaves * 1.2f).coerceAtMost(1f))
    conifer(w * 0.72f, h * 0.8f, h * 0.42f, h * 0.15f, tones(0x0E1612, 0x1A261E, 0x2A3A2A, 0x405236, 0x6A7A4A), 114)
    if (!live) fallingLeaves((w * h / 450f * (0.3f + leaves)).roundToInt(), s.crown(), 115, size = h * 0.009f)
    done(bloom = 0.55f - bare * 0.2f, vignette = 0.22f, threshold = 0.72f)
}

/** A crown's colours [t] along autumn's way: the second tree turns a little behind the first. */
private fun along(t: Float): IntArray = SeasonClock.of(40).copy(autumn = t.coerceIn(0f, 1f), fresh = 0f).crown()

/**
 * «Предзимье»: a dark river at dusk before the snow, bare birches, the last light low under the
 * clouds, crows going over to roost.
 */
private fun Painting.november(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x3E4A62, 0.4f to 0x6E7C94, 0.75f to 0xA9ADB2, 0.92f to 0xDCCFB2, 1f to 0xE8D4AE, to = h * 0.56f)
    glow(lightX, lightY, w * 0.6f, c(0xFFE0B0), 0.35f, squash = 0.25f)
    clouds(0f, h * 0.45f, 0.72f, h * 0.16f, 121, c(0x9BA3B4), c(0x3E4658), opacity = 0.9f, stretch = 3.4f, silver = 0.6f)
    val forestLine = treeLine(flat(h * 0.53f, h * 0.006f, seed = 122), h * 0.03f..h * 0.06f, h * 0.018f, conifers = 0.5f, seed = 123)
    forest(forestLine, h * 0.57f, veiled(tones(0x22262E, 0x323842, 0x4A505C, 0x6E7480, 0xB0B4BC), c(0xC8CCD4), s.snow * 0.25f), 124, leaf = h * 0.009f, depth = h * 0.03f, rim = 0.25f)
    church(w * 0.78f, h * 0.535f, h * 0.05f, c(0xBFC2C8), c(0x6A7080))
    haze(h * 0.54f, h * 0.47f, c(0xB9B8B4), 0.4f)
    val bank = flat(h * 0.555f, h * 0.004f, seed = 125)
    thaw(bank, h * 0.54f, c(0xE0E4EA), c(0x8A92A4), c(0x5A5046), c(0x3A342E), c(0x4E5666), c(0x6E7686), c(0xB8BCC4), 126, cover = (s.snow * 1.15f).coerceAtMost(1f), pools = 0.15f)
    water(h * 0.565f, h * 0.8f, c(0x262C3A), 0.45f, ripple = 0.3f, seed = 127, glint = c(0xFFE8C0), glintStrength = 0.35f)
    shoreIce(h * 0.565f, h * 0.8f, s.ice, 136)
    val near = ridge(h * 0.8f, h * 0.025f, w * 0.6f, 128)
    thaw(near, h * 0.7f, c(0xE8EBF0), c(0x9AA2B4), c(0x3A342E), c(0x2A2622), c(0x4A5262), c(0x6A7282), c(0xC0C4CA), 129, cover = s.snow, pools = 0.3f)
    for ((i, t) in listOf(0.08f to 0.6f, 0.16f to 0.5f, 0.9f to 0.62f).withIndex()) {
        val (fx, fh) = t
        birch(w * fx, h * (0.86f + i * 0.03f), h * fh, h * 0.022f, 130 + i, lean = (0.5f - fx) * 0.05f, bark = c(0xE4E4E2), barkShade = c(0x7C8290), twig = c(0x3A3230), weeping = 0.5f)
    }
    birds(w * 0.42f, h * 0.3f, 5, w * 0.12f, h * 0.02f, c(0x1A1A20), 133)
    reeds(w * 0.3f, w * 0.7f, h * 0.81f, (w / 4f).roundToInt(), h * 0.06f, c(0x8A7A5A), c(0x5A4A32), 134)
    if (!live && s.snow > 0.15f) snowfall((w * h / 260f * s.snow).roundToInt(), 135, size = 0.55f)
    done(bloom = 0.3f, vignette = 0.26f)
}

// endregion
