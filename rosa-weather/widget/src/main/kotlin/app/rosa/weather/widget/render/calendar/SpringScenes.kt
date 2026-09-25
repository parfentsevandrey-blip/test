package app.rosa.weather.widget.render.calendar

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Spring's weeks: mimosa on the sill for the 8th of March, a paper boat in a stream, snowdrops,
 * the ice going out on the river, the Easter table, the dacha opened for the year, the first
 * thunderstorm, the bird cherry in flower, lilac by an old house, a kite over the dandelions.
 */

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

private const val PIF = PI.toFloat()

// region Pieces

/** A greeting card standing open: a big "8" on its front with a sprig of mimosa. */
private fun Painting.card(x: Float, base: Float, size: Float) {
    floorShadow(x, base, size * 0.7f, size * 0.1f, 0.3f)
    path.reset()
    path.moveTo(x, base + size * 0.02f)
    path.lineTo(x + size * 0.04f, base - size * 0.96f)
    path.lineTo(x + size * 0.48f, base - size * 0.88f)
    path.lineTo(x + size * 0.44f, base + size * 0.01f)
    path.close()
    canvas.drawPath(path, pen(c(0xE8B4C0)))
    path.reset()
    path.moveTo(x - size * 0.52f, base)
    path.lineTo(x - size * 0.44f, base - size)
    path.lineTo(x + size * 0.04f, base - size * 0.96f)
    path.lineTo(x, base + size * 0.02f)
    path.close()
    val front = pen()
    front.shader = LinearGradient(x - size * 0.5f, 0f, x, 0f, c(0xFFFCF8), c(0xF0E6E0), Shader.TileMode.CLAMP)
    canvas.drawPath(path, front)
    val cx = x - size * 0.22f
    val eight = stroke(c(0xC8284A), max(0.3f, size * 0.07f))
    canvas.drawOval(cx - size * 0.1f, base - size * 0.78f, cx + size * 0.1f, base - size * 0.56f, eight)
    canvas.drawOval(cx - size * 0.13f, base - size * 0.57f, cx + size * 0.13f, base - size * 0.28f, eight)
    val r = Random(7)
    repeat(12) {
        canvas.drawCircle(cx + size * r.range(0.06f, 0.2f), base - size * r.range(0.2f, 0.42f), size * 0.022f, pen(c(0xF6C82A)))
    }
    canvas.drawLine(cx + size * 0.04f, base - size * 0.16f, cx + size * 0.2f, base - size * 0.4f, stroke(c(0x7AA06A), max(0.15f, size * 0.015f)))
}

/** A paper boat afloat: its sail folded up out of the hull, lit on one face, its shadow on the water. */
private fun Painting.paperBoat(x: Float, y: Float, size: Float) {
    glow(x + size * 0.1f, y + size * 0.04f, size * 0.8f, c(0x0A1830), 0.35f, squash = 0.18f, mode = android.graphics.BlendMode.SRC_OVER)
    // Its mirror, faint and broken.
    val mirror = pen(Tone.alpha(c(0xF4F4F0), 0.25f))
    path.reset()
    path.moveTo(x - size * 0.36f, y + size * 0.02f)
    path.lineTo(x + size * 0.36f, y + size * 0.02f)
    path.lineTo(x, y + size * 0.42f)
    path.close()
    canvas.drawPath(path, mirror)
    val paper = c(0xFBF8F2)
    val shade = c(0xC8CCD4)
    path.reset()
    path.moveTo(x - size * 0.56f, y - size * 0.2f)
    path.lineTo(x + size * 0.56f, y - size * 0.2f)
    path.lineTo(x + size * 0.36f, y)
    path.lineTo(x - size * 0.36f, y)
    path.close()
    val hull = pen()
    hull.shader = LinearGradient(0f, y - size * 0.2f, 0f, y, paper, shade, Shader.TileMode.CLAMP)
    canvas.drawPath(path, hull)
    path.reset()
    path.moveTo(x - size * 0.3f, y - size * 0.2f)
    path.lineTo(x, y - size * 0.74f)
    path.lineTo(x, y - size * 0.2f)
    path.close()
    canvas.drawPath(path, pen(if (lightX < x) paper else shade))
    path.reset()
    path.moveTo(x, y - size * 0.74f)
    path.lineTo(x + size * 0.3f, y - size * 0.2f)
    path.lineTo(x, y - size * 0.2f)
    path.close()
    canvas.drawPath(path, pen(if (lightX < x) shade else paper))
    canvas.drawLine(x, y - size * 0.74f, x, y - size * 0.2f, stroke(Tone.alpha(c(0x8A90A0), 0.6f), max(0.1f, size * 0.01f)))
    // The ripples it leaves.
    val ripple = stroke(Tone.alpha(c(0xFFFFFF), 0.4f), max(0.15f, size * 0.012f))
    for (k in 1..3) canvas.drawArc(RectF(x - size * (0.5f + k * 0.2f), y - size * 0.05f * k, x + size * (0.5f + k * 0.2f), y + size * 0.08f * k), 20f, 140f, false, ripple)
}

/** A kulich: the tall Easter loaf, its dome under white glaze running down its sides, sprinkles on it. */
private fun Painting.kulich(x: Float, base: Float, size: Float) {
    floorShadow(x + size * 0.05f, base, size * 0.5f, size * 0.07f, 0.4f)
    val hw = size * 0.27f
    val top = base - size * 0.62f
    val crust = pen()
    crust.shader = LinearGradient(x - hw, 0f, x + hw, 0f, intArrayOf(c(0x8A4A1E), c(0xD8964A), c(0xB8702E), c(0x6A3814)), floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(x - hw, top, x + hw, base, crust)
    canvas.drawOval(x - hw, base - hw * 0.25f, x + hw, base + hw * 0.25f, crust)
    // The glaze: a dome over the top, tongues running down the sides.
    val glaze = pen()
    glaze.shader = RadialGradient(x - hw * 0.3f, top - size * 0.2f, size * 0.5f, intArrayOf(c(0xFFFFFF), c(0xF2F0EA), c(0xD4D0C8)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - hw * 1.04f, top + size * 0.02f)
    path.cubicTo(x - hw * 1.1f, top - size * 0.3f, x + hw * 1.1f, top - size * 0.3f, x + hw * 1.04f, top + size * 0.02f)
    val drips = floatArrayOf(0.16f, 0.07f, 0.24f, 0.1f, 0.2f, 0.06f, 0.14f)
    for (k in drips.indices.reversed()) {
        val t = (k + 0.5f) / drips.size
        val dx = x - hw + hw * 2f * t
        val len = size * drips[k]
        path.lineTo(dx + hw * 0.12f, top + size * 0.02f)
        path.quadTo(dx + hw * 0.12f, top + len, dx, top + len + hw * 0.06f)
        path.quadTo(dx - hw * 0.12f, top + len, dx - hw * 0.12f, top + size * 0.02f)
    }
    path.close()
    canvas.drawPath(path, glaze)
    val r = Random(31)
    val sprinkles = intArrayOf(c(0xE0302A), c(0xF2C230), c(0x3A9AE0), c(0x4AB84A), c(0xE85AA0))
    repeat(26) {
        val a = r.range(-0.9f, 0.9f)
        val sx = x + a * hw
        val sy = top - size * 0.2f * (1f - a * a) + size * r.range(0f, 0.06f)
        canvas.drawCircle(sx, sy, max(0.2f, size * 0.008f), pen(sprinkles[r.nextInt(sprinkles.size)]))
    }
}

/** Painted eggs in a shallow bowl: red, gold, blue, green, a pattern round some. */
private fun Painting.eggs(x: Float, base: Float, size: Float, seed: Int) {
    floorShadow(x, base, size * 0.8f, size * 0.12f, 0.35f)
    val colors = intArrayOf(c(0xC8282A), c(0xE8A82A), c(0x2A6AC8), c(0x3A9A4A), c(0xB82A6A), c(0xD8402E))
    val r = Random(seed)
    val spots = listOf(-0.34f to 0.52f, 0.02f to 0.58f, 0.36f to 0.5f, -0.16f to 0.74f, 0.2f to 0.76f)
    for ((k, s) in spots.withIndex()) {
        val (dx, dy) = s
        val ex = x + dx * size
        val ey = base - dy * size
        val ew = size * 0.17f
        val color = colors[(k + r.nextInt(2)) % colors.size]
        val p = pen()
        p.shader = RadialGradient(ex - ew * 0.3f, ey - ew * 0.5f, ew * 1.8f, intArrayOf(Tone.mix(color, 0xFFFFFFFF.toInt(), 0.45f), color, Tone.shade(color, -0.5f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.withRotation(dx * 30f, ex, ey) {
            canvas.drawOval(ex - ew, ey - ew * 1.3f, ex + ew, ey + ew * 1.2f, p)
            if (k % 2 == 0) {
                val band = stroke(Tone.alpha(c(0xFFF4D8), 0.85f), max(0.15f, ew * 0.12f))
                canvas.drawLine(ex - ew * 0.95f, ey, ex + ew * 0.95f, ey, band)
                for (d in -2..2) canvas.drawCircle(ex + d * ew * 0.35f, ey - ew * 0.45f, ew * 0.08f, pen(c(0xFFF4D8)))
            }
        }
    }
    val bowl = pen()
    bowl.shader = LinearGradient(0f, base - size * 0.45f, 0f, base, c(0xF4F2EE), c(0xA8B0BC), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.62f, base - size * 0.42f)
    path.quadTo(x - size * 0.56f, base, x, base)
    path.quadTo(x + size * 0.56f, base, x + size * 0.62f, base - size * 0.42f)
    path.close()
    canvas.drawPath(path, bowl)
    canvas.drawLine(x - size * 0.6f, base - size * 0.36f, x + size * 0.6f, base - size * 0.36f, stroke(c(0x2A5AA8), max(0.2f, size * 0.03f)))
}

/** A birdhouse on a pole, a starling singing on its roof. */
private fun Painting.birdhouse(x: Float, ground: Float, height: Float, singer: Boolean) {
    canvas.drawLine(x, ground, x, ground - height, stroke(c(0x6A5242), max(0.3f, height * 0.025f)))
    val s = height * 0.2f
    val top = ground - height - s * 0.4f
    val box = pen()
    box.shader = LinearGradient(x - s * 0.4f, 0f, x + s * 0.4f, 0f, c(0xA07A52), c(0x6A4A2E), Shader.TileMode.CLAMP)
    canvas.drawRect(x - s * 0.4f, top, x + s * 0.4f, top + s, box)
    path.reset()
    path.moveTo(x - s * 0.55f, top + s * 0.05f)
    path.lineTo(x, top - s * 0.35f)
    path.lineTo(x + s * 0.55f, top + s * 0.05f)
    path.close()
    canvas.drawPath(path, pen(c(0x4A3A2E)))
    canvas.drawCircle(x, top + s * 0.4f, s * 0.12f, pen(c(0x1A1410)))
    if (singer) {
        val bx = x + s * 0.12f
        val by = top - s * 0.32f
        canvas.drawOval(bx - s * 0.22f, by - s * 0.14f, bx + s * 0.2f, by + s * 0.1f, pen(c(0x1E1E2A)))
        canvas.drawCircle(bx + s * 0.18f, by - s * 0.16f, s * 0.1f, pen(c(0x1E1E2A)))
        canvas.drawLine(bx + s * 0.26f, by - s * 0.18f, bx + s * 0.38f, by - s * 0.24f, stroke(c(0xE8C84A), max(0.15f, s * 0.05f)))
        canvas.drawLine(bx - s * 0.2f, by, bx - s * 0.36f, by + s * 0.1f, stroke(c(0x1E1E2A), max(0.15f, s * 0.08f)))
    }
}

/** A footbridge of planks over the water, its handrail on posts. */
private fun Painting.footbridge(x0: Float, x1: Float, y: Float, rise: Float, wood: Int) {
    val deck = stroke(wood, max(0.3f, rise * 0.35f), round = false)
    path.reset()
    path.moveTo(x0, y)
    path.quadTo((x0 + x1) / 2f, y - rise * 2f, x1, y)
    canvas.drawPath(path, deck)
    val rail = stroke(Tone.shade(wood, -0.2f), max(0.2f, rise * 0.12f))
    val posts = 9
    val railH = rise * 1.6f
    path.reset()
    for (k in 0..posts) {
        val t = k / posts.toFloat()
        val px = x0 + (x1 - x0) * t
        val py = y - rise * 4f * t * (1f - t)
        canvas.drawLine(px, py, px, py - railH, rail)
        if (k == 0) path.moveTo(px, py - railH) else path.lineTo(px, py - railH)
    }
    canvas.drawPath(path, rail)
}

/**
 * A lilac bush in flower: a mound of dark heart-shaped leaves, and the flowers standing out of
 * it in cones — hundreds of little florets, lit on the side of the evening sun.
 */
private fun Painting.lilacBush(x: Float, ground: Float, size: Float, leaves: IntArray, flowers: IntArray, seed: Int) {
    broadleaf(x, ground, size, size * 1.15f, size * 0.85f, c(0x3A302A), leaves, seed, leaf = size * 0.022f, lumps = 18, holes = 0.35f)
    val r = Random(seed + 1)
    val cy = ground - size + size * 0.85f * 0.5f
    val panicles = 16
    repeat(panicles) {
        val a = r.nextFloat() * 2f * PIF
        val d = kotlin.math.sqrt(r.nextFloat()) * 0.85f
        val px = x + kotlin.math.cos(a) * d * size * 0.55f
        val py = cy + kotlin.math.sin(a) * d * size * 0.4f - size * 0.05f
        val ph = size * r.range(0.12f, 0.2f)
        val pw = ph * 0.42f
        val tilt = r.range(-0.4f, 0.4f)
        val lit = if (lightX < px) -1f else 1f
        repeat(34) {
            val t = r.nextFloat()
            val half = pw * (1f - t) * 0.9f
            val fx = px + r.range(-1f, 1f) * half + tilt * ph * t
            val fy = py - ph * t
            val side = ((fx - px) / (pw + 0.01f) * -lit).coerceIn(-1f, 1f)
            val tone = ramp(flowers, (0.55f + side * 0.3f + r.range(-0.15f, 0.15f) + t * 0.1f).coerceIn(0f, 1f))
            canvas.drawCircle(fx, fy, size * r.range(0.008f, 0.013f), pen(tone))
        }
    }
}

// endregion

// region Scenes

/** Where the window of the sunny room stands in a [w] × [h] picture. */
private fun mimosaWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.54f, h * 0.84f)
    val cx = w * 0.6f
    return RectF(cx - ww / 2f, h * 0.06f, cx + ww / 2f, h * 0.64f)
}

/**
 * Мимоза: the 8th of March on a sunny sill — mimosa in a glass vase, a card with a big "8",
 * the light pouring in across the room, and outside the town's roofs thawing under a blue sky.
 */
internal fun Painting.mimosa(art: WeekArt, live: Boolean) {
    wall(RectF(0f, 0f, w, h), c(0xF2E8DA), c(0xE0CDB6), grain = 0.04f)
    val win = mimosaWindow(w, h)
    val frame = h * 0.02f
    window(win, c(0xF6F2EC), frame, 2, 301) {
        sky(0f to 0x3C7ED6, 0.5f to 0x86B8EC, 1f to 0xD4E6F6, to = h * 0.8f)
        sun(w * 0.78f, h * 0.18f, h * 0.05f, c(0xFFF4DA), power = 1f, streak = 0.2f)
        clouds(h * 0.05f, h * 0.55f, 0.32f, h * 0.2f, 302, c(0xFFFFFF), c(0xB8C8DE), opacity = 0.9f)
        birch(w * 0.14f, h * 1.05f, h * 0.95f, h * 0.045f, 304, bark = c(0xFBF8F0), barkShade = c(0x9EA9BC), twig = c(0x8A4638), weeping = 0.3f)
        facades(w * 0.2f, w * 1.05f, h * 1.02f, h * 0.2f, tones(0xE8C8A0, 0xF0DCC0, 0xD8B8A8, 0xE8D0B0), 0f, 303)
        val roofs = pen()
        roofs.shader = LinearGradient(0f, h * 0.5f, 0f, h * 0.6f, c(0xF6F8FA), c(0xC8D4E4), Shader.TileMode.CLAMP)
        birds(w * 0.52f, h * 0.3f, 4, w * 0.2f, h * 0.03f, c(0x22201E), 305)
    }
    // The light coming in: shafts across the room and a warm patch on the sill.
    rays(win.right - win.width() * 0.1f, win.top, h * 1.5f, PIF * 0.64f, 0.5f, 6, c(0xFFF0C8), 0.2f, 306)
    curtain(win.left - h * 0.1f, win.left + win.width() * 0.28f, 0f, win.bottom + frame * 4f, Tone.alpha(c(0xFFFFFF), 0.55f), 307, gatherRight = true)
    val sill = win.bottom + frame * 2.6f
    tabletop(sill, c(0xF6F2EC), 308)
    glow(win.centerX() + h * 0.1f, sill + (h - sill) * 0.45f, h * 0.45f, c(0xFFE8B0), 0.4f, squash = 0.3f)
    vase(w * 0.42f, h * 0.95f, h * 0.36f, c(0xB8D4E0), tones(0xFFE14A, 0xF6C82A, 0xFFD83A, 0xE8B01E), fluffy = true, seed = 309)
    card(w * 0.64f, h * 0.96f, h * 0.22f)
    done(bloom = 0.45f, vignette = 0.12f)
}

/**
 * Ручьи: the snow going and the water running — a stream winding out of a birch grove through
 * the melting banks, the sky in it, and a paper boat sailing down.
 */
internal fun Painting.streams(art: WeekArt, live: Boolean) {
    sky(0f to 0x4A86D6, 0.45f to 0x8EBCEC, 0.85f to 0xD4E6F4, 1f to 0xF0F0EC, to = h * 0.46f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF2D8), power = 0.9f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.36f, 0.35f, h * 0.1f, 321, c(0xFFFFFF), c(0xB8C8DC), opacity = 0.85f)
    val far = treeLine(flat(h * 0.44f, h * 0.006f, seed = 322), h * 0.03f..h * 0.07f, h * 0.02f, conifers = 0.4f, seed = 323)
    forest(far, h * 0.48f, tones(0x4A4458, 0x645C70, 0x8A8296, 0xB4AEBC, 0xE4E0E4), 324, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.3f)
    haze(h * 0.48f, h * 0.4f, c(0xE3EAF0), 0.4f)
    val ground = flat(h * 0.47f, h * 0.004f, seed = 325)
    thaw(ground, h * 0.45f, c(0xF6F8FA), c(0xB4C4DC), c(0x7A6A5A), c(0x4A3E36), c(0x5E96DA), c(0xC8DCF0), c(0xE0E4EA), 326, cover = 0.5f, pools = 0.2f)
    for ((i, t) in listOf(0.18f to 0.36f, 0.3f to 0.3f, 0.7f to 0.34f, 0.84f to 0.42f, 0.94f to 0.5f).withIndex()) {
        val (fx, fh) = t
        birch(w * fx, h * (0.5f + fh * 0.2f), h * fh * 1.2f, h * 0.012f + fh * h * 0.02f, 327 + i, lean = (fx - 0.5f) * 0.05f, bark = c(0xFBF8F0), barkShade = c(0x9EA9BC), twig = c(0x8A4638), weeping = 0.3f)
    }
    // The stream: out of the grove, winding, widening as it comes, the melt running brown and clear.
    val left = floatArrayOf(0.47f, 0.43f, 0.5f, 0.56f, 0.48f, 0.36f, 0.28f)
    val right = floatArrayOf(0.5f, 0.47f, 0.56f, 0.66f, 0.62f, 0.56f, 0.62f)
    val ys = floatArrayOf(0.5f, 0.56f, 0.63f, 0.72f, 0.82f, 0.92f, 1.02f)
    val stream = Path().apply {
        moveTo(w * left[0], h * ys[0])
        for (i in 1 until ys.size) quadTo(w * (left[i - 1] + left[i]) / 2f + w * 0.02f * (if (i % 2 == 0) 1f else -1f), h * (ys[i - 1] + ys[i]) / 2f, w * left[i], h * ys[i])
        lineTo(w * right.last(), h * ys.last())
        for (i in ys.size - 2 downTo 0) quadTo(w * (right[i + 1] + right[i]) / 2f + w * 0.02f * (if (i % 2 == 0) 1f else -1f), h * (ys[i + 1] + ys[i]) / 2f, w * right[i], h * ys[i])
        close()
    }
    water(h * 0.5f, h, c(0x2A6AB0), 0.22f, ripple = 1.3f, seed = 332, glint = c(0xFFFFFF), glintStrength = 0.85f, clip = stream)
    canvas.withClip(stream) {
        val edge = stroke(Tone.alpha(c(0x1E3A58), 0.4f), h * 0.016f)
        edge.maskFilter = BlurMaskFilter(h * 0.01f, BlurMaskFilter.Blur.NORMAL)
        drawPath(stream, edge)
    }
    // The old snow along its banks, broken and wet.
    val r = Random(333)
    val bankSnow = stroke(Tone.alpha(c(0xF4F8FC), 0.9f), h * 0.01f)
    val measure = android.graphics.PathMeasure(stream, true)
    val pos = FloatArray(2)
    var d = 0f
    while (d < measure.length) {
        val len = measure.length * r.range(0.01f, 0.04f)
        if (r.nextFloat() < 0.6f) {
            val seg = Path()
            measure.getSegment(d, d + len, seg, true)
            measure.getPosTan(d, pos, null)
            bankSnow.strokeWidth = h * r.range(0.006f, 0.014f) * (0.4f + pos[1] / h)
            canvas.drawPath(seg, bankSnow)
        }
        d += len * r.range(1.1f, 1.8f)
    }
    paperBoat(w * 0.56f, h * 0.84f, h * 0.17f)
    grass(h * 0.86f, h * 1.02f, (w * 0.3f).roundToInt(), h * 0.05f, tones(0x9C8E5E, 0x7F7550, 0xB4A06A), 333, lean = 0.2f)
    sparkles(h * 0.5f, h, (w * h / 150f).roundToInt(), c(0xFFFFFF), 334, 0.26f)
    done(bloom = 0.45f, vignette = 0.14f)
}

/**
 * Подснежники: the first flowers of the year, seen close — snowdrops nodding through the last of
 * the snow and last year's brown leaves, the sun coming through the bare wood behind.
 */
internal fun Painting.snowdropGlade(art: WeekArt, live: Boolean) {
    sky(0f to 0xA8C0DA, 0.45f to 0xC8D6E2, 0.8f to 0xD8D8CE, 1f to 0xC8C0A8, to = h * 0.62f)
    glow(lightX, lightY, h * 1.1f, c(0xFFF4DC), 0.55f)
    // The wood behind, far out of focus: a few soft trunks, the sun's light round them.
    val far = treeLine(flat(h * 0.52f, h * 0.01f, seed = 342), h * 0.08f..h * 0.2f, h * 0.04f, conifers = 0.3f, seed = 343)
    forest(far, h * 0.58f, veiled(tones(0x5A5048, 0x7A6E62, 0x9A8E80, 0xC0B6A6, 0xE8E0D4), c(0xC8C4BA), 0.45f), 344, leaf = h * 0.02f, depth = h * 0.08f, rim = 0.4f)
    val r = Random(341)
    repeat(12) { glow(r.nextFloat() * w, r.nextFloat() * h * 0.5f, h * r.range(0.04f, 0.09f), if (r.nextBoolean()) c(0xFFF6E0) else c(0xD8E8C8), r.range(0.2f, 0.4f)) }
    fog(h * 0.3f, h * 0.6f, c(0xEAE6E0), 0.55f, h * 0.08f, 345)
    val ground = flat(h * 0.55f, h * 0.01f, seed = 346)
    thaw(ground, h * 0.52f, c(0xF6F8FA), c(0xAAB8CE), c(0x7A5E42), c(0x4A3826), c(0x8AA8C8), c(0xC8D4E0), c(0xD8D4CC), 347, cover = 0.38f, pools = 0.05f)
    rays(lightX, lightY, h * 1.2f, PIF * 0.62f, 1.4f, 8, c(0xFFF4DC), 0.14f, 348)
    // Snowdrops scattered back into the wood, and clumps of them close.
    snowdrops(h * 0.56f, h * 0.84f, (w * 0.4f).roundToInt(), h * 0.04f, 349)
    for ((k, x) in listOf(0.2f, 0.52f, 0.82f).withIndex()) {
        snowdrops(h * 0.9f, h * 1.03f, 7, h * 0.09f, 350 + k, x0 = w * x - h * 0.12f, x1 = w * x + h * 0.12f)
    }
    done(bloom = 0.4f, vignette = 0.2f)
}

/**
 * Ледоход: the river breaking up — the ice going down it in floes under a town on its high bank,
 * a church over the roofs, gulls following the water.
 */
internal fun Painting.iceDrift(art: WeekArt, live: Boolean) {
    sky(0f to 0x6A84A8, 0.45f to 0xA0B4CC, 0.8f to 0xD0D8E0, 1f to 0xE8E8E2, to = h * 0.58f)
    sun(lightX, lightY, h * 0.028f, c(0xFFF4DC), power = 0.6f, streak = 0.15f)
    clouds(0f, h * 0.45f, 0.55f, h * 0.15f, 361, c(0xFFFFFF), c(0x8898B0), opacity = 0.92f, stretch = 2.6f)
    val bank = ridge(h * 0.52f, h * 0.06f, w * 0.5f, 362, lift = h * 0.16f, liftX = w * 0.84f, liftWidth = w * 0.36f)
    val area = land(bank, h * 0.6f, c(0x9A9280), c(0x7A7060), texture = 0.25f, rim = c(0xF0F2F4), rimStrength = 0.35f, shading = 0.25f)
    // Snow lingering in streaks down the slope.
    canvas.withClip(area) {
        val r = Random(363)
        repeat((w / 5f).roundToInt()) {
            val x = r.nextFloat() * w
            val y = bank.at(x) + h * r.range(0.01f, 0.06f)
            drawLine(x, y, x + h * r.range(-0.02f, 0.02f), y + h * r.range(0.01f, 0.05f), stroke(Tone.alpha(c(0xF0F4F8), 0.7f), h * 0.005f))
        }
    }
    val cx = w * 0.82f
    church(cx, bank.at(cx) + h * 0.02f, h * 0.12f, c(0xF4F0E8), c(0x3A7A5A))
    for ((i, fx) in listOf(0.56f, 0.64f, 0.72f, 0.94f).withIndex()) {
        house(w * fx, bank.at(w * fx) + h * 0.025f, h * 0.06f, c(0x6A5244), c(0x8A3A2A), 364 + i, snowRoof = c(0xE8ECF0), window = 0, smoke = c(0xD0D4DC))
    }
    val trees = treeLine(bank.shifted(h * 0.012f), h * 0.02f..h * 0.05f, h * 0.02f, conifers = 0.3f, seed = 368)
    forest(trees, bank.bottom + h * 0.03f, tones(0x5A5058, 0x6E6470, 0x8A8090, 0xB4AEBA, 0xE8E4E8), 369, leaf = h * 0.008f, depth = h * 0.02f, rim = 0.3f)
    haze(h * 0.6f, h * 0.5f, c(0xDCE2E8), 0.35f)
    water(h * 0.6f, h, c(0x4A5A70), 0.42f, ripple = 0.5f, seed = 370, glint = c(0xFFFFFF), glintStrength = 0.35f)
    floes(h * 0.61f, h * 0.99f, (w * h / 450f).roundToInt().coerceIn(30, 90), 371)
    val near = ridge(h * 0.97f, h * 0.025f, w * 0.4f, 372)
    val nearArea = land(near, h + 1f, c(0x8A7A5E), c(0x5E5040), texture = 0.3f, rim = c(0xF0F2F4), rimStrength = 0.3f)
    canvas.withClip(nearArea) {
        val r = Random(373)
        repeat((w / 6f).roundToInt()) {
            val x = r.nextFloat() * w
            val y = near.at(x) + h * r.range(0f, 0.04f)
            drawOval(x - h * r.range(0.01f, 0.04f), y, x + h * r.range(0.01f, 0.04f), y + h * 0.01f, pen(Tone.alpha(c(0xF0F4F8), 0.85f)))
        }
    }
    grass(near.top, h * 1.02f, (w * 0.4f).roundToInt(), h * 0.04f, tones(0x9C8E5E, 0x7F7550, 0xB4A06A), 375, lean = 0.3f)
    birds(w * 0.36f, h * 0.3f, 6, w * 0.16f, h * 0.022f, c(0x4A5060), 374)
    done(bloom = 0.3f, vignette = 0.16f)
}

/** Where the window of the Easter room stands in a [w] × [h] picture. */
private fun easterWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.34f, h * 0.48f)
    return RectF(w * 0.07f, h * 0.08f, w * 0.07f + ww, h * 0.6f)
}

/**
 * Пасха: the Easter table on a bright morning — a kulich under white glaze, painted eggs in a
 * bowl, pussy willow in a jar, an embroidered towel on the white cloth; the church's domes in the
 * window.
 */
internal fun Painting.easter(art: WeekArt, live: Boolean) {
    wall(RectF(0f, 0f, w, h), c(0xDCE6E0), c(0xC4D2CC), grain = 0.03f)
    val win = easterWindow(w, h)
    val frame = h * 0.02f
    window(win, c(0xF4F0EA), frame, 2, 381) {
        sky(0f to 0x5A96DC, 0.6f to 0xA8CCEE, 1f to 0xE0ECF4, to = h)
        sun(w * 0.3f, h * 0.18f, h * 0.05f, c(0xFFF4DA), power = 0.9f, streak = 0.15f)
        val hill = ridge(h * 0.8f, h * 0.06f, w * 0.8f, 382)
        land(hill, h, c(0x8AAE5A), c(0x6A8A48), texture = 0.2f, shading = 0.2f)
        church(w * 0.55f, hill.at(w * 0.55f) + h * 0.02f, h * 0.36f, c(0xFBF8F0), c(0xE8B84A))
        for ((i, fx) in listOf(0.1f, 0.9f).withIndex()) {
            birch(w * fx, h * 1.02f, h * 0.8f, h * 0.035f, 383 + i, crown = SeasonClock.of(17).birchLeaves(), leaves = 0.25f, leafSize = h * 0.012f, twig = c(0x6E4E44), weeping = 0.4f)
        }
    }
    rays(win.left + win.width() * 0.3f, win.top, h * 1.6f, PIF * 0.3f, 0.5f, 6, c(0xFFF4D8), 0.16f, 385)
    val table = h * 0.7f
    tabletop(table, c(0xF6F2EA), 386, cloth = c(0xFBF8F2))
    // The embroidered towel across the table, its red cross-stitch border.
    val towelTop = table + (h - table) * 0.25f
    val towelBottom = table + (h - table) * 0.62f
    canvas.drawRect(-1f, towelTop, w + 1f, towelBottom, pen(c(0xFFFDF8)))
    val stitch = stroke(c(0xC8282A), max(0.2f, h * 0.004f), round = false)
    for (edge in listOf(towelTop + h * 0.012f, towelBottom - h * 0.012f)) {
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, edge - h * 0.006f, x + h * 0.012f, edge + h * 0.006f, stitch)
            canvas.drawLine(x, edge + h * 0.006f, x + h * 0.012f, edge - h * 0.006f, stitch)
            x += h * 0.02f
        }
    }
    // Pussy willow in a jar of water.
    val jx = w * 0.26f
    catkins(jx, h * 0.84f, h * 0.55f, 6, 387)
    val jarGlass = pen()
    jarGlass.shader = LinearGradient(jx - h * 0.06f, 0f, jx + h * 0.06f, 0f, 0x66D8E8F0, 0x88A8C0D0.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(jx - h * 0.06f, h * 0.8f, jx + h * 0.06f, h * 0.95f, h * 0.02f, h * 0.02f, jarGlass)
    kulich(w * 0.52f, h * 0.93f, h * 0.38f)
    eggs(w * 0.77f, h * 0.95f, h * 0.2f, 388)
    done(bloom = 0.4f, vignette = 0.12f)
}

/**
 * Дачный сезон: the dacha opened for the year on the May holidays — beds dug and the seedlings
 * in, the greenhouse up, a starling singing on its house, the birches in their first green haze.
 */
internal fun Painting.dachaSeason(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x4A8ADA, 0.45f to 0x92C2EE, 0.85f to 0xD6E8F4, 1f to 0xF4F0E4, to = h * 0.6f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF2D6), power = 0.95f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.4f, 0.34f, h * 0.12f, 401, c(0xFFFFFF), c(0xB8C4DA), opacity = 0.9f)
    val far = treeLine(flat(h * 0.56f, h * 0.008f, seed = 402), h * 0.05f..h * 0.1f, h * 0.026f, conifers = 0.35f, seed = 403)
    forest(far, h * 0.62f, veiled(tones(0x3E4E3A, 0x566A4A, 0x76905E, 0xA8C088, 0xE0ECC8), c(0x9AC070), 0.3f), 404, leaf = h * 0.01f, depth = h * 0.05f, rim = 0.3f)
    haze(h * 0.6f, h * 0.5f, c(0xE8ECE4), 0.35f)
    val yard = flat(h * 0.62f, h * 0.004f, seed = 405)
    meadow(yard, h * 0.58f, s.grassTones(), c(0xC8D4B0), 406)
    birch(w * 0.06f, h * 0.9f, h * 0.9f, h * 0.026f, 407, crown = s.birchLeaves(), leaves = 0.35f, leafSize = h * 0.008f, twig = c(0x6E4E44), weeping = 0.5f)
    birch(w * 0.94f, h * 0.86f, h * 0.8f, h * 0.024f, 408, crown = s.birchLeaves(), leaves = 0.35f, leafSize = h * 0.008f, twig = c(0x6E4E44), weeping = 0.5f)
    dacha(w * 0.32f, h * 0.68f, h * 0.46f, c(0xA8C8B8), c(0x8A3A2A), c(0xF4F0E8), 0f)
    greenhouse(w * 0.72f, h * 0.7f, h * 0.36f, h * 0.2f)
    birdhouse(w * 0.86f, h * 0.72f, h * 0.36f, singer = true)
    gardenBeds(h * 0.74f, h * 1.02f, 4, c(0x6AAA3A), c(0x4A3424), 409, grown = 0.4f)
    done(bloom = 0.3f, vignette = 0.12f)
}

/**
 * «Люблю грозу в начале мая»: the first thunderstorm — lightning out of a dark sky, rain hanging
 * from it in the distance, while the sun behind breaks through onto the young green fields, a
 * row of birches and a rainbow.
 */
internal fun Painting.mayStorm(art: WeekArt, live: Boolean) {
    sky(0f to 0x2A3046, 0.4f to 0x485068, 0.75f to 0x7A8296, 1f to 0xC8D0B8, to = h * 0.62f)
    clouds(0f, h * 0.52f, 0.85f, h * 0.18f, 421, c(0xA8AEC0), c(0x22283A), opacity = 0.95f, stretch = 2.4f, silver = 0.3f)
    // Rain hanging from the clouds far off.
    val r = Random(422)
    val rain = stroke(0, 0.3f)
    repeat((w * 1.5f).roundToInt()) {
        val x = w * r.range(0.45f, 1.05f)
        val y = h * r.range(0.3f, 0.58f)
        rain.color = Tone.alpha(c(0xB8C0D0), r.range(0.1f, 0.3f))
        canvas.drawLine(x, y, x - h * 0.01f, y + h * r.range(0.04f, 0.1f), rain)
    }
    lightning(w * 0.68f, h * 0.08f, h * 0.46f, 423)
    rainbow(w * 0.14f, h * 0.66f, h * 0.36f, h * 0.03f, 0.35f, h * 0.62f)
    val far = ridge(h * 0.6f, h * 0.03f, w * 0.6f, 424)
    land(far, h, c(0x86B24A), c(0x5E8A36), texture = 0.12f, rim = c(0xF0FFD0), rimStrength = 0.4f, shading = 0.3f)
    val trees = treeLine(far.shifted(h * 0.01f), h * 0.04f..h * 0.08f, h * 0.03f, conifers = 0.2f, seed = 425)
    forest(trees, h * 0.66f, tones(0x2E4A22, 0x4A6E2E, 0x6E963E, 0xA2C85E, 0xE0F4A8), 426, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.5f)
    val field = flat(h * 0.66f, h * 0.01f, seed = 427)
    meadow(field, h * 0.62f, SeasonClock.of(18).grassTones(), c(0xB8D090), 428, speckle = tones(0xFFD42E, 0xFFE35A), speckleAmount = 0.12f)
    for ((i, fx) in listOf(0.52f, 0.6f, 0.68f, 0.76f, 0.84f).withIndex()) {
        val gy = h * (0.72f + i * 0.02f)
        birch(w * fx, gy, h * (0.3f + i * 0.04f), h * 0.012f, 429 + i, crown = SeasonClock.of(18).birchLeaves(), leaves = 0.55f, leafSize = h * 0.006f, twig = c(0x5A463A), weeping = 0.4f)
    }
    grass(h * 0.86f, h * 1.02f, (w * 0.8f).roundToInt(), h * 0.05f, tones(0x5E9A3E, 0x78B44C, 0x96C95C, 0x4A8534), 434, lean = 0.4f)
    if (!live) {
        val drops = stroke(0, 0.25f)
        repeat((w * h / 120f).roundToInt()) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            drops.color = Tone.alpha(c(0xE0E8F0), r.range(0.15f, 0.4f))
            canvas.drawLine(x, y, x - h * 0.008f, y + h * 0.05f, drops)
        }
    }
    done(bloom = 0.45f, vignette = 0.3f, threshold = 0.7f)
}

/**
 * Черёмуха: bird cherry in full white flower along a river, doubled in the water, a footbridge
 * across, petals on the stream — and the cold snap that always comes with it in the blue air.
 */
internal fun Painting.birdCherry(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x5A92D8, 0.45f to 0x9CC4EC, 0.85f to 0xD8E8F4, 1f to 0xF0F2F2, to = h * 0.56f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF6E2), power = 0.9f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.36f, 0.3f, h * 0.12f, 441, c(0xFFFFFF), c(0xB8C8DC), opacity = 0.88f)
    val far = treeLine(flat(h * 0.5f, h * 0.008f, seed = 442), h * 0.05f..h * 0.1f, h * 0.026f, conifers = 0.3f, seed = 443)
    forest(far, h * 0.56f, tones(0x2E5A2A, 0x467A36, 0x6A9C48, 0x9CC46A, 0xDCEEB8), 444, leaf = h * 0.01f, depth = h * 0.05f, rim = 0.35f)
    haze(h * 0.56f, h * 0.46f, c(0xE6EEF0), 0.4f)
    val bank = flat(h * 0.575f, h * 0.004f, seed = 445)
    meadow(bank, h * 0.55f, s.grassTones(), c(0xC8D8B8), 446)
    val blossom = tones(0xFFFFFF, 0xF6F8F2, 0xEEF0E8, 0xFFFFFF)
    val leaves = tones(0x1E3A1E, 0x2E5228, 0x4A7438, 0x6E9A4E, 0xA8C87E)
    for ((i, t) in listOf(Triple(0.1f, 0.44f, 0.5f), Triple(0.28f, 0.3f, 0.34f), Triple(0.78f, 0.38f, 0.44f), Triple(0.95f, 0.46f, 0.5f)).withIndex()) {
        val (fx, fh, fw) = t
        broadleaf(w * fx, h * 0.585f, h * fh, h * fw, h * fh * 0.8f, c(0x3A2E28), leaves, 447 + i, leaf = h * 0.008f, blossom = blossom, blossomAmount = 0.95f, lumps = 18, holes = 0.5f)
    }
    water(h * 0.585f, h, c(0x3A6A9A), 0.3f, ripple = 0.4f, seed = 452, glint = c(0xFFFFFF), glintStrength = 0.45f)
    footbridge(w * 0.3f, w * 0.72f, h * 0.7f, h * 0.02f, c(0x5A4632))
    // Petals come down onto the water and drift.
    val r = Random(453)
    repeat((w * h / 250f).roundToInt()) {
        val y = h * r.range(0.6f, 1f)
        val x = r.nextFloat() * w
        val sz = h * 0.0035f * (0.4f + (y - h * 0.6f) / h * 3f)
        canvas.drawOval(x - sz, y - sz * 0.45f, x + sz, y + sz * 0.45f, pen(c(0xFBFBF6)))
    }
    val near = ridge(h * 0.94f, h * 0.03f, w * 0.4f, 454)
    meadow(near, h * 0.7f, s.grassTones(), c(0xC8D8B8), 455, speckle = tones(0xFFD42E, 0xFFFFFF), speckleAmount = 0.2f)
    if (!live) fallingPetals((w * h / 280f).roundToInt(), tones(0xFFFFFF, 0xF4F6EE), 456, size = h * 0.005f)
    done(bloom = 0.4f, vignette = 0.12f)
}

/**
 * Сирень: lilac in flower — purple and white — by an old wooden house at evening, its windows lit,
 * a bench under the bushes.
 */
internal fun Painting.lilac(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x5A74B8, 0.4f to 0xA496C8, 0.75f to 0xF0C6B8, 1f to 0xF8E0C6, to = h * 0.62f)
    sun(lightX, lightY, h * 0.036f, c(0xFFD8A8), power = 0.8f, streak = 0.35f)
    clouds(h * 0.04f, h * 0.4f, 0.3f, h * 0.1f, 461, c(0xFFD8C8), c(0x8A7AA8), opacity = 0.85f, stretch = 3f, silver = 0.8f)
    val far = treeLine(flat(h * 0.58f, h * 0.008f, seed = 462), h * 0.05f..h * 0.1f, h * 0.026f, conifers = 0.3f, seed = 463)
    forest(far, h * 0.64f, tones(0x3A3A4A, 0x4E5A4E, 0x6E7A5E, 0xA8A882, 0xF0D8B0), 464, leaf = h * 0.01f, depth = h * 0.05f, rim = 0.45f)
    haze(h * 0.62f, h * 0.52f, c(0xF4D8C4), 0.35f)
    val yard = flat(h * 0.66f, h * 0.004f, seed = 465)
    meadow(yard, h * 0.62f, s.grassTones(), c(0xD8C8B0), 466)
    // The old house: boards painted grey-blue, white carved frames, the windows lit for the evening.
    val hx = w * 0.76f
    val hg = h * 0.74f
    val hw = h * 0.5f
    house(hx, hg, hw, c(0x7A90A8), c(0x4A5A4A), 466, window = c(0xFFD08A), smoke = 0)
    for (k in listOf(-1, 1)) {
        val wx = hx + k * hw * 0.22f
        val wy = hg - hw * 0.55f + hw * 0.55f * 0.3f
        val ww = hw * 0.14f
        canvas.drawRect(wx - ww * 0.62f, wy - ww * 0.1f, wx + ww * 0.62f, wy + ww * 1.3f, stroke(c(0xF4F0E8), max(0.3f, hw * 0.02f), round = false))
        path.reset()
        path.moveTo(wx - ww * 0.8f, wy - ww * 0.15f)
        path.lineTo(wx, wy - ww * 0.55f)
        path.lineTo(wx + ww * 0.8f, wy - ww * 0.15f)
        path.close()
        canvas.drawPath(path, pen(c(0xF4F0E8)))
    }
    val purple = tones(0x7A4AA8, 0x9A6AC8, 0xB88ADA, 0xD2AEEA, 0xE8D0F4)
    val white = tones(0xC8C0D8, 0xE0D8EA, 0xF2EEF6, 0xFFFFFF, 0xFFFFFF)
    val leaves = tones(0x1E321E, 0x2E4A28, 0x466A38, 0x62884A, 0x98B474)
    lilacBush(w * 0.24f, h * 0.88f, h * 0.52f, leaves, purple, 467)
    lilacBush(w * 0.5f, h * 0.92f, h * 0.38f, leaves, white, 468)
    bench(w * 0.36f, h * 0.95f, h * 0.2f, c(0x7A5A3A))
    grass(h * 0.9f, h * 1.02f, (w * 0.5f).roundToInt(), h * 0.04f, s.grassTones().copyOfRange(1, 4), 469)
    if (!live) fallingPetals((w * h / 600f).roundToInt(), tones(0xC8A0E0, 0xFFFFFF), 470, size = h * 0.004f)
    done(bloom = 0.45f, vignette = 0.2f)
}

/**
 * Воздушный змей: the last days of May on a hill gone to seed — dandelion clocks everywhere, a
 * kite riding high on the wind, its string running down out of the picture.
 */
internal fun Painting.kiteDay(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x2A70D0, 0.45f to 0x6AA8EC, 0.85f to 0xB8D8F4, 1f to 0xE8F0F4, to = h * 0.7f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF6E0), power = 1f, streak = 0.2f)
    clouds(h * 0.05f, h * 0.6f, 0.4f, h * 0.16f, 481, c(0xFFFFFF), c(0x98AECC), opacity = 0.96f, stretch = 1.7f, softness = 0.12f)
    val far = ridge(h * 0.66f, h * 0.04f, w * 0.7f, 482)
    land(far, h, c(0x8AB0B8), c(0x9ABCC0), texture = 0.06f, shading = 0.15f)
    haze(h * 0.68f, h * 0.6f, c(0xE0EEF4), 0.45f)
    val hill = ridge(h * 0.74f, h * 0.08f, w * 0.9f, 483, lift = h * 0.1f, liftX = w * 0.2f, liftWidth = w * 0.4f)
    meadow(hill, h * 0.64f, s.grassTones(), c(0xC8DCC0), 484, speckle = tones(0xF4F4EE, 0xFFFFFF, 0xFFD42E, 0xF4F4EE), speckleAmount = 0.4f)
    val kx0 = w * 0.64f
    val ky0 = h * 0.2f
    val string = stroke(Tone.alpha(c(0xFFFFFF), 0.7f), max(0.15f, h * 0.003f))
    path.reset()
    path.moveTo(kx0, ky0 + h * 0.08f)
    path.quadTo(w * 0.4f, h * 0.4f, w * 0.12f, h * 1.02f)
    canvas.drawPath(path, string)
    kite(kx0, ky0, h * 0.09f, c(0xE84A3A), c(0xF2C230))
    flowers(h * 0.8f, h * 1.02f, (w * 0.5f).roundToInt(), listOf(Bloom.Puff, Bloom.Puff, Bloom.Puff, Bloom.Dandelion), 485, size = h * 0.016f)
    grass(h * 0.86f, h * 1.02f, (w * 0.7f).roundToInt(), h * 0.05f, s.grassTones().copyOfRange(1, 5), 486, lean = 0.35f)
    if (!live) fallingPetals((w * h / 700f).roundToInt(), tones(0xFFFFFF, 0xF4F4EE), 487, size = h * 0.004f)
    done(bloom = 0.35f, vignette = 0.1f)
}

// endregion
