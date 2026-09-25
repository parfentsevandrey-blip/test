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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Autumn's weeks: the cranes going over in the Indian summer, mushrooms in the moss, coffee at a
 * café window on a wet street, a park alley under orange maples, a fire in a castle hall, a town
 * street in the rain, the first hoarfrost on the fallen leaves, an evening with a book.
 */

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

private const val PIF = PI.toFloat()

// region Pieces

/** A cup of coffee on its saucer: the crema, a heart poured in the milk, steam rising. */
private fun Painting.latte(x: Float, base: Float, size: Float) {
    floorShadow(x + size * 0.1f, base, size * 0.95f, size * 0.15f, 0.4f)
    canvas.drawOval(x - size * 0.78f, base - size * 0.17f, x + size * 0.78f, base + size * 0.07f, pen(c(0xC8C2B8)))
    canvas.drawOval(x - size * 0.7f, base - size * 0.2f, x + size * 0.7f, base + size * 0.02f, pen(c(0xF4F0EA)))
    canvas.drawOval(x - size * 0.34f, base - size * 0.13f, x + size * 0.34f, base - size * 0.03f, pen(c(0xDDD8D0)))
    canvas.drawArc(RectF(x + size * 0.42f, base - size * 0.66f, x + size * 0.86f, base - size * 0.28f), -80f, 170f, false, stroke(c(0xECE8E0), max(0.25f, size * 0.08f)))
    val cup = pen()
    cup.shader = LinearGradient(x - size * 0.56f, 0f, x + size * 0.56f, 0f, intArrayOf(c(0xFFFFFF), c(0xF2EEE8), c(0xB8B2AA)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.56f, base - size * 0.72f)
    path.cubicTo(x - size * 0.56f, base - size * 0.22f, x - size * 0.3f, base - size * 0.1f, x, base - size * 0.1f)
    path.cubicTo(x + size * 0.3f, base - size * 0.1f, x + size * 0.56f, base - size * 0.22f, x + size * 0.56f, base - size * 0.72f)
    path.close()
    canvas.drawPath(path, cup)
    canvas.drawOval(x - size * 0.56f, base - size * 0.8f, x + size * 0.56f, base - size * 0.64f, pen(c(0xFBFAF6)))
    val crema = pen()
    crema.shader = RadialGradient(x, base - size * 0.72f, size * 0.5f, intArrayOf(c(0xC89A6A), c(0xA06A3A), c(0x6A3E1E)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    canvas.drawOval(x - size * 0.5f, base - size * 0.785f, x + size * 0.5f, base - size * 0.655f, crema)
    // The heart, poured in the milk.
    val milk = pen(c(0xF6EEE2))
    val hy = base - size * 0.725f
    canvas.drawOval(x - size * 0.2f, hy - size * 0.04f, x, hy + size * 0.015f, milk)
    canvas.drawOval(x, hy - size * 0.04f, x + size * 0.2f, hy + size * 0.015f, milk)
    path.reset()
    path.moveTo(x - size * 0.19f, hy)
    path.lineTo(x + size * 0.19f, hy)
    path.lineTo(x, hy + size * 0.05f)
    path.close()
    canvas.drawPath(path, milk)
    steam(x, base - size * 0.8f, size * 1.4f, 0.9f, 11)
}

/** A croissant on a small plate: a golden crescent rolled in layers, glossy along its top. */
private fun Painting.croissant(x: Float, base: Float, size: Float) {
    floorShadow(x, base, size * 0.95f, size * 0.14f, 0.35f)
    canvas.drawOval(x - size * 0.92f, base - size * 0.2f, x + size * 0.92f, base + size * 0.08f, pen(c(0xC8C2B8)))
    canvas.drawOval(x - size * 0.84f, base - size * 0.22f, x + size * 0.84f, base + size * 0.03f, pen(c(0xF6F2EC)))
    // The crescent: a band bent on an arc, thick in the middle, drawn to points at its horns.
    val cy = base + size * 0.28f
    val radius = size * 0.58f
    val n = 24
    val upper = ArrayList<Pair<Float, Float>>()
    val lower = ArrayList<Pair<Float, Float>>()
    for (i in 0..n) {
        val t = i / n.toFloat()
        val a = (200f + 140f * t) * PIF / 180f
        val thick = size * 0.3f * sin(PIF * t).coerceAtLeast(0f).pow(0.7f) + size * 0.02f
        upper += Pair(x + cos(a) * (radius + thick * 0.5f), cy + sin(a) * (radius + thick * 0.5f) * 0.72f)
        lower += Pair(x + cos(a) * (radius - thick * 0.5f), cy + sin(a) * (radius - thick * 0.5f) * 0.72f)
    }
    path.reset()
    path.moveTo(upper[0].first, upper[0].second)
    for (p in upper) path.lineTo(p.first, p.second)
    for (p in lower.reversed()) path.lineTo(p.first, p.second)
    path.close()
    val body = pen()
    body.shader = LinearGradient(0f, cy - radius * 0.72f - size * 0.2f, 0f, cy - radius * 0.3f, intArrayOf(c(0xF8D08A), c(0xD89040), c(0x8A4A18)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, body)
    // The rolled layers: ridges across the band.
    val ridge = stroke(Tone.alpha(c(0x6A3610), 0.55f), max(0.15f, size * 0.025f))
    for (k in 1..5) {
        val i = n * k / 6
        canvas.drawLine(upper[i].first, upper[i].second, lower[i].first, lower[i].second, ridge)
    }
    val gloss = stroke(Tone.alpha(c(0xFFF0C8), 0.6f), max(0.15f, size * 0.03f))
    gloss.maskFilter = BlurMaskFilter(max(0.2f, size * 0.02f), BlurMaskFilter.Blur.NORMAL)
    path.reset()
    for (i in 3..n - 3) {
        val (ux, uy) = upper[i]
        val (lx, ly) = lower[i]
        val gx = ux + (lx - ux) * 0.25f
        val gy = uy + (ly - uy) * 0.25f
        if (i == 3) path.moveTo(gx, gy) else path.lineTo(gx, gy)
    }
    canvas.drawPath(path, gloss)
}

/** A tealight in a small amber glass, burning. */
private fun Painting.votive(x: Float, base: Float, size: Float) {
    glow(x, base - size * 0.5f, size * 3.5f, c(0xFFB050), 0.45f)
    val glass = pen()
    glass.shader = LinearGradient(x - size * 0.4f, 0f, x + size * 0.4f, 0f, 0xAAE08A3A.toInt(), 0x88FFC070.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(x - size * 0.38f, base - size * 0.7f, x + size * 0.38f, base, size * 0.08f, size * 0.08f, glass)
    canvas.drawOval(x - size * 0.38f, base - size * 0.76f, x + size * 0.38f, base - size * 0.64f, stroke(0x88FFE0B0.toInt(), max(0.15f, size * 0.04f)))
    flame(x, base - size * 0.42f, size * 0.16f, size * 0.42f, size * 2.2f)
}

/** A book lying open, its pages fanning up, lines of print on them. */
private fun Painting.openBook(x: Float, base: Float, size: Float) {
    floorShadow(x, base, size * 1.1f, size * 0.14f, 0.35f)
    for (side in listOf(-1f, 1f)) {
        path.reset()
        path.moveTo(x, base)
        path.quadTo(x + side * size * 0.5f, base - size * 0.1f, x + side * size, base - size * 0.02f)
        path.lineTo(x + side * size * 0.92f, base - size * 0.5f)
        path.quadTo(x + side * size * 0.45f, base - size * 0.6f, x, base - size * 0.5f)
        path.close()
        val page = pen()
        page.shader = LinearGradient(x, 0f, x + side * size, 0f, c(0xD8CCB4), c(0xF6EEDC), Shader.TileMode.CLAMP)
        canvas.drawPath(path, page)
        val print = stroke(Tone.alpha(c(0x6A5E50), 0.5f), max(0.1f, size * 0.012f))
        for (k in 0..6) {
            val t = 0.14f + k * 0.045f
            val y0 = base - size * (0.46f - t * 0.9f)
            canvas.drawLine(x + side * size * 0.1f, y0 - size * 0.02f, x + side * size * 0.82f, y0 + size * 0.02f, print)
        }
    }
}

/** A tram going by on the wet street, its windows lit, the pole up to the wire. */
private fun Painting.tram(x: Float, base: Float, size: Float) {
    val l = x - size * 0.85f
    val r = x + size * 0.85f
    val top = base - size * 0.52f
    canvas.drawLine(-1f, top - size * 0.3f, w + 1f, top - size * 0.3f, stroke(c(0x1A1A20), max(0.12f, size * 0.008f)))
    canvas.drawLine(x - size * 0.2f, top, x + size * 0.1f, top - size * 0.3f, stroke(c(0x2A2A30), max(0.15f, size * 0.015f)))
    glow(x, base, size * 1.2f, c(0xFFC878), 0.25f, squash = 0.25f)
    canvas.drawRoundRect(l, top, r, base - size * 0.06f, size * 0.08f, size * 0.08f, pen(c(0xF0E2C0)))
    canvas.drawRect(l, base - size * 0.26f, r, base - size * 0.06f, pen(c(0xC8302A)))
    canvas.drawRoundRect(l, top - size * 0.04f, r, top + size * 0.04f, size * 0.04f, size * 0.04f, pen(c(0x6A6A70)))
    val windows = 6
    for (k in 0 until windows) {
        val wx = l + size * 0.1f + (r - l - size * 0.2f) * k / (windows - 1f)
        canvas.drawRect(wx - size * 0.08f, top + size * 0.08f, wx + size * 0.08f, base - size * 0.3f, pen(c(0xFFD890)))
    }
    glow(x, (top + base) / 2f, size * 1.1f, c(0xFFC878), 0.3f)
    canvas.drawCircle(l + size * 0.03f, base - size * 0.16f, size * 0.03f, pen(c(0xFFF4D8)))
    glow(l, base - size * 0.16f, size * 0.25f, c(0xFFF4D8), 0.5f)
}

/**
 * A maple leaf lying on the ground: five lobes, each drawn to a point and toothed along its
 * edges, the veins running out to the points — and, with [rime], hoarfrost furring its edges in
 * tiny needles of ice and whitening the veins.
 */
private fun Painting.mapleLeaf(x: Float, y: Float, size: Float, angle: Float, color: Int, rime: Float, seed: Int) {
    canvas.withRotation(angle, x, y) {
        canvas.scale(1f, 0.62f, x, y)
        val lobes = floatArrayOf(-90f, -38f, -142f, 12f, -192f)
        val reach = floatArrayOf(1f, 0.86f, 0.86f, 0.56f, 0.56f)
        val n = 180
        val outline = ArrayList<Pair<Float, Float>>(n)
        for (i in 0 until n) {
            val deg = -270f + 360f * i / n
            var r = 0.3f
            for (k in lobes.indices) {
                var d = abs(deg - lobes[k])
                if (d > 180f) d = 360f - d
                val lobe = (1f - d / 26f).coerceAtLeast(0f)
                r = max(r, 0.3f + (reach[k] - 0.3f) * lobe.pow(0.8f))
            }
            // Teeth along the edges.
            r *= 1f + 0.06f * abs(sin(deg * PIF / 180f * 18f))
            // The stem's notch at the bottom.
            var s = abs(deg - 90f)
            if (s > 180f) s = 360f - s
            if (s < 22f) r *= 0.55f + 0.45f * (s / 22f)
            val a = deg * PIF / 180f
            outline += Pair(x + cos(a) * size * r, y + sin(a) * size * r)
        }
        path.reset()
        path.moveTo(outline[0].first, outline[0].second)
        for (p in outline) path.lineTo(p.first, p.second)
        path.close()
        val leaf = Path(path)
        val p = pen()
        p.shader = RadialGradient(x, y - size * 0.2f, size, intArrayOf(Tone.shade(color, 0.15f), color, Tone.shade(color, -0.35f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(leaf, p)
        val veins = stroke(Tone.alpha(Tone.shade(color, -0.45f), 0.55f), max(0.12f, size * 0.018f))
        for (k in lobes.indices) {
            val a = lobes[k] * PIF / 180f
            canvas.drawLine(x, y, x + cos(a) * size * reach[k] * 0.9f, y + sin(a) * size * reach[k] * 0.9f, veins)
        }
        canvas.drawLine(x, y, x, y + size * 0.45f, stroke(Tone.shade(color, -0.3f), max(0.15f, size * 0.03f)))
        if (rime > 0f) {
            canvas.withClip(leaf) {
                val bloom = stroke(Tone.alpha(c(0xF4F8FF), 0.55f * rime), size * 0.12f)
                bloom.maskFilter = BlurMaskFilter(size * 0.05f, BlurMaskFilter.Blur.NORMAL)
                drawPath(leaf, bloom)
                for (k in lobes.indices) {
                    val a = lobes[k] * PIF / 180f
                    drawLine(x, y, x + cos(a) * size * reach[k] * 0.9f, y + sin(a) * size * reach[k] * 0.9f, stroke(Tone.alpha(c(0xFFFFFF), 0.45f * rime), max(0.12f, size * 0.02f)))
                }
            }
            // Needles of ice standing out from the edge.
            val r = Random(seed)
            val needle = stroke(Tone.alpha(c(0xFFFFFF), 0.85f * rime), max(0.1f, size * 0.008f))
            for (i in outline.indices step 2) {
                val (ex, ey) = outline[i]
                val dx = ex - x
                val dy = ey - y
                val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.01f)
                val l = size * r.range(0.02f, 0.05f)
                canvas.drawLine(ex, ey, ex + dx / len * l, ey + dy / len * l, needle)
            }
        }
    }
}

/** A cat asleep in a ring, its tail round its nose, the stripes of a tabby on its back. */
private fun Painting.curledCat(x: Float, base: Float, size: Float, fur: Int) {
    floorShadow(x, base, size * 1.2f, size * 0.2f, 0.45f)
    val body = pen()
    body.shader = RadialGradient(x - size * 0.2f, base - size * 0.6f, size * 1.2f, intArrayOf(Tone.shade(fur, 0.25f), fur, Tone.shade(fur, -0.45f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    canvas.drawOval(x - size, base - size * 0.72f, x + size, base, body)
    val stripes = stroke(Tone.alpha(Tone.shade(fur, -0.4f), 0.6f), max(0.2f, size * 0.07f))
    for (k in 0..4) {
        val sx = x - size * 0.6f + size * 0.28f * k
        canvas.drawArc(RectF(sx - size * 0.1f, base - size * 0.72f, sx + size * 0.14f, base - size * 0.3f), 200f, 100f, false, stripes)
    }
    val hx = x + size * 0.72f
    val hy = base - size * 0.36f
    canvas.drawCircle(hx, hy, size * 0.34f, body)
    for (side in listOf(-1f, 1f)) {
        path.reset()
        path.moveTo(hx + side * size * 0.28f, hy - size * 0.1f)
        path.lineTo(hx + side * size * 0.2f, hy - size * 0.46f)
        path.lineTo(hx + side * size * 0.02f, hy - size * 0.26f)
        path.close()
        canvas.drawPath(path, pen(Tone.shade(fur, -0.1f)))
    }
    canvas.drawLine(hx - size * 0.16f, hy + size * 0.02f, hx - size * 0.04f, hy + size * 0.04f, stroke(Tone.shade(fur, -0.6f), max(0.15f, size * 0.03f)))
    canvas.drawLine(hx + size * 0.06f, hy + size * 0.04f, hx + size * 0.18f, hy + size * 0.02f, stroke(Tone.shade(fur, -0.6f), max(0.15f, size * 0.03f)))
    val tail = stroke(Tone.shade(fur, -0.05f), max(0.3f, size * 0.2f))
    path.reset()
    path.moveTo(x - size * 0.9f, base - size * 0.2f)
    path.quadTo(x - size * 0.2f, base + size * 0.08f, x + size * 0.5f, base - size * 0.08f)
    canvas.drawPath(path, tail)
}

/** A draped plaid: a woollen rug in red and green checks hanging over the arm of a chair. */
private fun Painting.plaid(l: Float, t: Float, r: Float, b: Float) {
    val area = RectF(l, t, r, b)
    val base = pen()
    base.shader = LinearGradient(l, t, l, b, c(0xA83A2A), c(0x6A1E16), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(area, (r - l) * 0.1f, (r - l) * 0.1f, base)
    canvas.withClip(area) {
        val band = (b - t) * 0.16f
        var x = l
        while (x < r) {
            drawRect(x, t, x + band * 0.5f, b, pen(Tone.alpha(c(0x1E4A2E), 0.55f)))
            drawRect(x + band * 0.7f, t, x + band * 0.78f, b, pen(Tone.alpha(c(0xF2D25A), 0.6f)))
            x += band * 1.5f
        }
        var y = t
        while (y < b) {
            drawRect(l, y, r, y + band * 0.5f, pen(Tone.alpha(c(0x1E4A2E), 0.45f)))
            y += band * 1.5f
        }
    }
    val fringe = stroke(c(0x8A2A20), max(0.15f, (r - l) * 0.02f))
    var fx = l
    while (fx < r) {
        canvas.drawLine(fx, b, fx, b + (b - t) * 0.08f, fringe)
        fx += (r - l) * 0.05f
    }
}

// endregion

// region Scenes

/**
 * Журавли: the Indian summer — a wedge of cranes going south, crying high over the stubble, the
 * stacks standing in the field, a birch with its first yellow strands, gossamer on the still air.
 */
internal fun Painting.indianSummer(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x3A7ACC, 0.45f to 0x7AB0E6, 0.85f to 0xC8DCEC, 1f to 0xF0ECDC, to = h * 0.6f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF0D0), power = 0.9f, streak = 0.2f)
    clouds(h * 0.3f, h * 0.56f, 0.3f, h * 0.1f, 801, c(0xFFFFFF), c(0xB0C0D8), opacity = 0.85f, stretch = 3f)
    cranes(w * 0.36f, h * 0.16f, 19, w * 0.9f, h * 0.02f, c(0x2E2E38), 802)
    cranes(w * 0.7f, h * 0.3f, 9, w * 0.4f, h * 0.013f, c(0x3E3E48), 803)
    val far = treeLine(flat(h * 0.56f, h * 0.008f, seed = 804), h * 0.04f..h * 0.08f, h * 0.024f, conifers = 0.35f, seed = 805)
    forest(far, h * 0.6f, tones(0x3A4A2E, 0x566A3A, 0x7E8A4A, 0xB8B060, 0xECE4B0), 806, leaf = h * 0.01f, depth = h * 0.04f, variety = tones(0xE8B43A, 0x6A7A3A, 0xD8963A), varietyScale = h * 0.02f, rim = 0.3f)
    haze(h * 0.6f, h * 0.5f, c(0xEEE8DA), 0.4f)
    val field = flat(h * 0.6f, h * 0.006f, seed = 807)
    rye(field, h * 0.58f, tones(0x8A7A4A, 0xA8945A, 0xC8B070, 0xE0CC8A, 0xF2E4B0), c(0xE8E0C8), c(0xFFF0C8), 808)
    for ((i, t) in listOf(Triple(0.4f, 0.66f, 0.07f), Triple(0.62f, 0.72f, 0.11f)).withIndex()) {
        val (fx, fy, size) = t
        castShadow(w * fx, h * fy, h * size, h * size * 0.5f, c(0x6A5A3A), 0.35f)
        haystack(w * fx, h * fy, h * size * 0.8f, h * size, c(0xD8B870), c(0x7A6030), 809 + i)
    }
    birch(w * 0.14f, h * 0.94f, h * 0.9f, h * 0.03f, 812, crown = s.birchLeaves(), leaves = 0.95f, leafSize = h * 0.009f, twig = c(0x5B463A), weeping = 0.55f)
    gossamer(12, 813, c(0xFFF6DC))
    grass(h * 0.9f, h * 1.02f, (w * 0.4f).roundToInt(), h * 0.05f, tones(0xA89A5A, 0xC8B870, 0x8A8A4A), 814, lean = 0.2f)
    done(bloom = 0.4f, vignette = 0.12f)
}

/**
 * Грибная пора: in the birch wood after rain — a birch bolete and its little one in the moss, a
 * fly agaric shining red, ferns, the first yellow leaves down, a basket half full.
 */
internal fun Painting.mushrooms(art: WeekArt, live: Boolean) {
    sky(0f to 0x8AA070, 0.4f to 0xB8B880, 0.8f to 0x8A8A5A, 1f to 0x5A5A38, to = h * 0.6f)
    glow(lightX, lightY, h * 0.9f, c(0xFFF0C0), 0.5f)
    // The wood behind, out of focus: white birch trunks, gold and green light between.
    val r = Random(821)
    val far = treeLine(flat(h * 0.52f, h * 0.01f, seed = 831), h * 0.1f..h * 0.24f, h * 0.05f, conifers = 0.25f, seed = 832)
    forest(far, h * 0.58f, veiled(tones(0x3A4A26, 0x5A6A32, 0x8A8A40, 0xC8B45A, 0xF0E0A0), c(0xB8B880), 0.4f), 833, leaf = h * 0.025f, depth = h * 0.1f, rim = 0.4f)
    repeat(5) {
        val x = r.nextFloat() * w
        val half = h * r.range(0.018f, 0.032f)
        val p = pen()
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.58f, Tone.alpha(c(0xF2EEE4), 0.15f), Tone.alpha(c(0xF2EEE4), r.range(0.35f, 0.55f)), Shader.TileMode.CLAMP)
        p.maskFilter = BlurMaskFilter(h * 0.018f, BlurMaskFilter.Blur.NORMAL)
        path.reset()
        path.moveTo(x - half * 0.7f, -h * 0.02f)
        path.lineTo(x + half * 0.7f, -h * 0.02f)
        path.lineTo(x + half, h * 0.58f)
        path.lineTo(x - half, h * 0.58f)
        path.close()
        canvas.drawPath(path, p)
    }
    repeat(18) {
        glow(r.nextFloat() * w, r.nextFloat() * h * 0.55f, h * r.range(0.04f, 0.1f), if (r.nextBoolean()) c(0xE8C860) else c(0x9AB860), r.range(0.25f, 0.5f))
    }
    fog(h * 0.3f, h * 0.6f, c(0xE8E4C0), 0.4f, h * 0.08f, 834)
    rays(lightX, lightY, h * 1.2f, PIF * 0.58f, 1.2f, 8, c(0xFFF4D0), 0.14f, 822)
    // The floor of the wood: moss, fallen leaves, needles.
    val top = h * 0.56f
    val g = Ground(w, h, h * 0.5f)
    pixels(RectF(0f, top, w, h)) { x, y ->
        g.at(x, y)
        val depth = ((y - top) / (h - top)).coerceIn(0f, 1f)
        val moss = Noise.fbm(g.across * 1.4f, g.away * 0.6f, 823, 4)
        val tuft = Noise.value(x / (0.4f + depth * 1.4f), y / (0.3f + depth * 1.2f), 824)
        var color = ramp(tones(0x1E2E12, 0x2E4A1A, 0x4A6A26, 0x6E8A34, 0x9AAA4A), (moss * 0.8f + tuft * 0.35f).coerceIn(0f, 1f))
        val leaf = Noise.value(x / (0.8f + depth * 2.4f), y / (0.5f + depth * 1.6f), 825)
        if (leaf > 0.82f) color = Tone.mix(color, ramp(tones(0xB8741E, 0xE0A232, 0xF6C84E), leaf), Tone.smooth(0.82f, 0.9f, leaf))
        Tone.mix(c(0x8A8A5A), color, Tone.smooth(0f, 0.3f, depth))
    }
    fern(w * 0.06f, h * 1.02f, h * 0.32f, c(0x3A6A2A), 826)
    fern(w * 0.97f, h * 0.96f, h * 0.26f, c(0x4A7A2E), 827)
    mushroom(w * 0.32f, h * 0.9f, h * 0.34f, agaric = false)
    mushroom(w * 0.42f, h * 0.93f, h * 0.2f, agaric = false)
    mushroom(w * 0.68f, h * 0.8f, h * 0.24f, agaric = true)
    basket(w * 0.86f, h * 1f, h * 0.3f, tones(0x7A4A26, 0x8A5A30, 0x6A3E1E, 0xB87A3A), 828)
    fallingLeaves(6, SeasonClock.of(37).birchLeaves(), 829, size = h * 0.012f, top = h * 0.8f, bottom = h)
    if (!live) fallingLeaves((w * h / 900f).roundToInt(), SeasonClock.of(38).birchLeaves(), 830, size = h * 0.008f, bottom = h * 0.7f)
    done(bloom = 0.4f, vignette = 0.24f)
}

/** Where the café's window stands in a [w] × [h] picture. */
private fun cafeWindow(w: Float, h: Float): RectF = RectF(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.64f)

/**
 * Кафе у окна: a table at a café window on an autumn evening — coffee with a heart in the milk,
 * a croissant, a candle, an open book; outside, the lamps come on along a wet street of yellow
 * trees, a tram goes by lit, people hurry under umbrellas, rain beads the glass.
 */
internal fun Painting.cafe(art: WeekArt, live: Boolean) {
    wall(RectF(0f, 0f, w, h), c(0x4A3228), c(0x2A1A14), grain = 0.2f)
    val win = cafeWindow(w, h)
    val frame = h * 0.022f
    window(win, c(0x2A1C14), frame, 3, 841, sill = false) {
        sky(0f to 0x2A3656, 0.5f to 0x5E5E7E, 0.85f to 0xB88A7A, 1f to 0xE8A880, to = h * 0.5f)
        clouds(0f, h * 0.36f, 0.6f, h * 0.14f, 842, c(0xC89A8A), c(0x3A3A56), opacity = 0.8f, stretch = 3f)
        facades(-w * 0.05f, w * 1.05f, h * 0.66f, h * 0.15f, tones(0x8A6A5A, 0x9A7A62, 0x7A6A6A, 0xA88A6A), 0.6f, 843)
        // The shop fronts along the street, lit.
        val r = Random(844)
        var sx = 0f
        while (sx < w) {
            val sw = h * r.range(0.14f, 0.24f)
            if (r.nextFloat() < 0.7f) {
                canvas.drawRect(sx + h * 0.01f, h * 0.58f, sx + sw - h * 0.01f, h * 0.66f, pen(c(0xFFD090)))
                glow(sx + sw / 2f, h * 0.62f, sw, c(0xFFC070), 0.3f)
                canvas.drawRect(sx, h * 0.55f, sx + sw, h * 0.575f, pen(if (r.nextBoolean()) c(0x2E5A3A) else c(0x7A2A26)))
            }
            sx += sw
        }
        val gold = SeasonClock.of(38).crown()
        for ((i, fx) in listOf(0.12f, 0.5f, 0.88f).withIndex()) {
            broadleaf(w * fx, h * 0.7f, h * 0.5f, h * 0.36f, h * 0.3f, c(0x2A201A), gold, 845 + i, leaf = h * 0.012f, lumps = 14, leaves = 0.75f, back = 0.3f)
        }
        streetLamp(w * 0.32f, h * 0.7f, h * 0.36f, 1f)
        streetLamp(w * 0.72f, h * 0.7f, h * 0.36f, 1f)
        water(h * 0.7f, h, c(0x2A2A36), 0.35f, ripple = 0.5f, seed = 848, glint = c(0xFFD090), glintStrength = 0.55f)
        tram(w * 0.58f, h * 0.84f, h * 0.26f)
        umbrellaPerson(w * 0.2f, h * 0.76f, h * 0.2f, c(0xC8302A), c(0x2A2A34))
        umbrellaPerson(w * 0.86f, h * 0.78f, h * 0.22f, c(0x1E1E24), c(0x6A5A4A))
        fallingLeaves((w * h / 220f).roundToInt(), gold, 849, size = h * 0.012f)
        if (!live) {
            val rain = stroke(0, 0.25f)
            repeat((w * h / 60f).roundToInt()) {
                val x = r.nextFloat() * w
                val y = r.nextFloat() * h
                rain.color = Tone.alpha(c(0xE0E4F0), r.range(0.15f, 0.4f))
                canvas.drawLine(x, y, x - h * 0.01f, y + h * 0.05f, rain)
            }
        }
    }
    raindrops(win, (win.width() * win.height() / 55f).roundToInt(), h * 0.009f, 850)
    // The warm room: a lamp hanging over the table.
    val lx = w * 0.5f
    val ly = h * 0.12f
    canvas.drawLine(lx, -1f, lx, ly - h * 0.05f, stroke(c(0x1A1410), max(0.2f, h * 0.004f)))
    glow(lx, h * 0.5f, h * 0.9f, c(0xFFB860), 0.28f)
    val shade = pen()
    shade.shader = LinearGradient(lx - h * 0.1f, 0f, lx + h * 0.1f, 0f, c(0x2E4A3A), c(0x142018), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(lx - h * 0.03f, ly - h * 0.06f)
    path.lineTo(lx + h * 0.03f, ly - h * 0.06f)
    path.quadTo(lx + h * 0.1f, ly - h * 0.04f, lx + h * 0.11f, ly)
    path.lineTo(lx - h * 0.11f, ly)
    path.quadTo(lx - h * 0.1f, ly - h * 0.04f, lx - h * 0.03f, ly - h * 0.06f)
    path.close()
    canvas.drawPath(path, shade)
    canvas.drawOval(lx - h * 0.1f, ly - h * 0.012f, lx + h * 0.1f, ly + h * 0.012f, pen(c(0xFFE8B8)))
    glow(lx, ly, h * 0.2f, c(0xFFE0A0), 0.6f)
    tabletop(h * 0.72f, c(0x4A2E1E), 851)
    glow(w * 0.5f, h * 0.86f, h * 0.6f, c(0xFFB860), 0.22f, squash = 0.4f)
    openBook(w * 0.14f, h * 0.95f, h * 0.18f)
    latte(w * 0.38f, h * 0.95f, h * 0.17f)
    croissant(w * 0.63f, h * 0.94f, h * 0.15f)
    votive(w * 0.84f, h * 0.9f, h * 0.1f)
    done(bloom = 0.65f, vignette = 0.32f, threshold = 0.6f)
}

/**
 * Листопад в парке: an alley running away under orange maples, the leaves coming down thick, a
 * carpet of them on the lawns and the path, lamps along it, a bench with leaves on it.
 */
internal fun Painting.parkAlley(art: WeekArt, live: Boolean) {
    sky(0f to 0x8A9AB8, 0.5f to 0xD8C0A8, 1f to 0xF0C890, to = h * 0.5f)
    glow(lightX, lightY, h * 0.9f, c(0xFFE0B0), 0.55f)
    val orange = tones(0x6A2A12, 0xA84A1A, 0xDC7A2A, 0xF4A640, 0xFFD27A)
    val far = treeLine(flat(h * 0.44f, h * 0.008f, seed = 861), h * 0.06f..h * 0.12f, h * 0.03f, conifers = 0.08f, seed = 862)
    forest(far, h * 0.5f, orange, 863, leaf = h * 0.012f, depth = h * 0.05f, variety = tones(0xD8483A, 0xE88A3A, 0xF2C04A), varietyScale = h * 0.03f, rim = 0.5f)
    fog(h * 0.3f, h * 0.52f, c(0xFFE8C8), 0.5f, h * 0.05f, 864)
    val lawn = flat(h * 0.48f, 0f)
    litter(lawn, h * 0.46f, tones(0xC0402E, 0xE06A2E, 0xF0A03A, 0xD8C04A, 0x9A3A22), c(0x4A2A1A), c(0xF0C8A0), 865)
    // The path, paler, running to the vanishing point.
    val vx = w * 0.5f
    val alley = Path().apply {
        moveTo(vx - w * 0.02f, h * 0.48f)
        lineTo(vx + w * 0.02f, h * 0.48f)
        lineTo(w * 0.86f, h * 1.02f)
        lineTo(w * 0.14f, h * 1.02f)
        close()
    }
    val paving = pen()
    paving.shader = LinearGradient(0f, h * 0.48f, 0f, h, c(0xE0C8A8), c(0xA89478), Shader.TileMode.CLAMP)
    canvas.drawPath(alley, paving)
    canvas.withClip(alley) {
        val r = Random(866)
        repeat((w * h / 60f).roundToInt()) {
            val v = r.nextFloat().pow(0.7f)
            val y = h * 0.48f + (h * 0.54f) * v
            val x = vx + (r.nextFloat() - 0.5f) * w * (0.04f + 0.8f * v)
            val sz = h * (0.002f + 0.012f * v)
            drawOval(x - sz, y - sz * 0.5f, x + sz, y + sz * 0.5f, pen(orange[1 + r.nextInt(4)]))
        }
    }
    // The maples in two rows, the nearest arching over.
    val n = 6
    for (i in 0 until n) {
        val e = ((i + 1) / n.toFloat()).pow(1.7f)
        for (side in listOf(-1f, 1f)) {
            val gx = vx + side * (w * 0.05f + w * 0.56f * e)
            val gy = h * 0.49f + h * 0.54f * e
            val size = h * (0.07f + 0.95f * e)
            castShadow(gx, gy, size * 0.3f, size * 0.4f, c(0x5A2A12), 0.25f)
            broadleaf(gx, gy, size, size * 0.95f, size * 0.72f, c(0x2E221C), orange, 867 + i * 2 + (if (side > 0) 1 else 0), leaf = size * 0.028f, back = 0.6f, lumps = 16, leaves = 0.8f)
            if (i % 2 == 1) streetLamp(vx + side * (w * 0.03f + w * 0.4f * e), gy + h * 0.01f, h * (0.05f + 0.45f * e), 0.6f)
        }
    }
    bench(w * 0.78f, h * 0.94f, h * 0.24f, c(0x6A4A30))
    fallingLeaves(8, orange, 880, size = h * 0.012f, top = h * 0.84f, bottom = h * 0.9f)
    if (!live) fallingLeaves((w * h / 260f).roundToInt(), orange, 881, size = h * 0.01f)
    done(bloom = 0.5f, vignette = 0.22f, threshold = 0.72f)
}

/** Where the castle hall's window stands in a [w] × [h] picture. */
private fun castleWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.18f, h * 0.26f)
    val cx = w * 0.82f
    return RectF(cx - ww / 2f, h * 0.14f, cx + ww / 2f, h * 0.64f)
}

/**
 * Камин в замке: a fire roaring in a castle hall — the great stone hearth, candles on its mantel,
 * the stone warm with its light, a tapestry on the wall, an armchair drawn up; at the tall pointed
 * window, rain and a stormy night.
 */
internal fun Painting.castleFire(art: WeekArt, live: Boolean) {
    val fireX = w * 0.42f
    val stone = tones(0x14100E, 0x2A2420, 0x423832, 0x5E524A, 0x7A6C62)
    stoneWall(RectF(0f, 0f, w, h * 0.86f), stone, h * 0.14f, 891, warmX = fireX, warmY = h * 0.72f, warm = c(0xFF9A4A), warmth = 0.95f)
    haze(0f, h * 0.6f, c(0x060404), 0.75f)
    stoneWall(RectF(0f, h * 0.86f, w, h), tones(0x2A2420, 0x4A3E36, 0x6A5A4E, 0x8A7868, 0xA89484), h * 0.1f, 892, warmX = fireX, warmY = h * 0.86f, warm = c(0xFFA050), warmth = 0.9f)
    // The tall window: a pointed arch of leaded panes, the storm outside.
    val win = castleWindow(w, h)
    val ww = win.width()
    val arch = Path().apply {
        moveTo(win.left, win.bottom)
        lineTo(win.left, win.top + ww * 0.4f)
        cubicTo(win.left, win.top - ww * 0.1f, win.centerX() - ww * 0.15f, win.top - ww * 0.35f, win.centerX(), win.top - ww * 0.5f)
        cubicTo(win.centerX() + ww * 0.15f, win.top - ww * 0.35f, win.right, win.top - ww * 0.1f, win.right, win.top + ww * 0.4f)
        lineTo(win.right, win.bottom)
        close()
    }
    val outer = RectF(win.left, win.top - ww * 0.5f, win.right, win.bottom)
    canvas.withClip(arch) {
        view(outer, 893) {
            sky(0f to 0x0A0C1A, 0.5f to 0x1A1E34, 1f to 0x2A3048, to = h)
            clouds(0f, h * 0.8f, 0.7f, h * 0.2f, 894, c(0x4A5068), c(0x0E1020), opacity = 0.9f, stretch = 1.4f)
            lightning(w * 0.6f, h * 0.05f, h * 0.5f, 895, strength = 0.8f)
            val rain = stroke(0, 0.25f)
            val r = Random(896)
            repeat((w * h / 12f).roundToInt()) {
                val x = r.nextFloat() * w
                val y = r.nextFloat() * h
                rain.color = Tone.alpha(c(0xC8D0E8), r.range(0.15f, 0.45f))
                canvas.drawLine(x, y, x - h * 0.01f, y + h * 0.05f, rain)
            }
        }
        val lead = stroke(Tone.alpha(c(0x14120E), 0.85f), max(0.2f, ww * 0.02f))
        val step = ww / 4f
        var d = -outer.height()
        while (d < outer.width() + outer.height()) {
            drawLine(outer.left + d, outer.top, outer.left + d + outer.height() * 0.6f, outer.bottom, lead)
            drawLine(outer.left + d, outer.bottom, outer.left + d + outer.height() * 0.6f, outer.top, lead)
            d += step
        }
    }
    raindrops(win, (ww * win.height() / 30f).roundToInt(), h * 0.006f, 897)
    val surround = stroke(c(0x6A5E54), ww * 0.14f)
    canvas.drawPath(arch, surround)
    canvas.drawPath(arch, stroke(Tone.alpha(c(0xFFB060), 0.25f), ww * 0.04f))
    // A tapestry on the left wall.
    val tl = w * 0.03f
    val tr = tl + min(w * 0.1f, h * 0.16f)
    if (tr < fireX - min(w * 0.44f, h * 0.62f) / 2f - h * 0.02f) {
        val weave = pen()
        weave.shader = LinearGradient(0f, h * 0.12f, 0f, h * 0.66f, c(0x7A1E1E), c(0x4A1414), Shader.TileMode.CLAMP)
        canvas.drawRect(tl, h * 0.12f, tr, h * 0.66f, weave)
        canvas.drawRect(tl, h * 0.12f, tr, h * 0.66f, stroke(c(0xC8A050), max(0.3f, h * 0.008f), round = false))
        val cx = (tl + tr) / 2f
        for (k in 0..3) {
            val cy = h * (0.2f + k * 0.12f)
            path.reset()
            path.moveTo(cx, cy - h * 0.04f)
            path.lineTo(cx + (tr - tl) * 0.3f, cy)
            path.lineTo(cx, cy + h * 0.04f)
            path.lineTo(cx - (tr - tl) * 0.3f, cy)
            path.close()
            canvas.drawPath(path, pen(Tone.alpha(c(0xD8B060), 0.7f)))
        }
    }
    // The hearth.
    val fw = min(w * 0.44f, h * 0.62f)
    val hearth = RectF(fireX - fw / 2f, h * 0.36f, fireX + fw / 2f, h * 0.9f)
    fireplace(hearth, tones(0x3A322E, 0x5A4E46, 0x7A6C60, 0x9A8A7C, 0xB8A898), 898)
    for (k in -1..1) candle(fireX + k * fw * 0.36f, hearth.top, h * 0.1f)
    armchair(w * 0.66f, h * 0.99f, h * 0.4f, c(0x6A1E1E))
    done(bloom = 0.8f, vignette = 0.5f, threshold = 0.55f)
}

/**
 * Осенний дождь: a town street in the rain at dusk — the lamps lit and doubled in the wet road,
 * the last yellow leaves on the trees and stuck to the paving, people hurrying under umbrellas.
 */
internal fun Painting.autumnRain(art: WeekArt, live: Boolean) {
    sky(0f to 0x343C50, 0.45f to 0x545C70, 0.85f to 0x8A8A94, 1f to 0xA8A0A0, to = h * 0.52f)
    clouds(0f, h * 0.42f, 0.8f, h * 0.16f, 901, c(0x9A9EA8), c(0x3A404E), opacity = 0.9f, stretch = 3f)
    facades(-w * 0.05f, w * 1.05f, h * 0.6f, h * 0.1f, tones(0x6A5A5A, 0x7A6A62, 0x5A5A66, 0x8A7A6A), 0.55f, 902)
    val gold = SeasonClock.of(39).crown()
    for ((i, fx) in listOf(0.22f, 0.64f).withIndex()) {
        broadleaf(w * fx, h * 0.62f, h * 0.36f, h * 0.3f, h * 0.24f, c(0x2A201A), gold, 903 + i, leaf = h * 0.01f, lumps = 12, leaves = 0.55f, back = 0.2f)
    }
    for (fx in listOf(0.08f, 0.44f, 0.84f)) streetLamp(w * fx, h * 0.62f, h * 0.3f, 1f)
    water(h * 0.62f, h, c(0x2A3040), 0.3f, ripple = 0.8f, seed = 905, glint = c(0xFFD090), glintStrength = 0.5f)
    val r = Random(906)
    repeat((w * h / 160f).roundToInt()) {
        val v = r.nextFloat()
        val y = h * 0.64f + h * 0.36f * v
        val x = r.nextFloat() * w
        val rx = h * (0.004f + v * 0.02f)
        canvas.drawOval(x - rx, y - rx * 0.3f, x + rx, y + rx * 0.3f, stroke(Tone.alpha(c(0xE0E4EC), 0.3f), 0.2f))
    }
    repeat((w * h / 300f).roundToInt()) {
        val v = r.nextFloat()
        val y = h * 0.66f + h * 0.34f * v
        val x = r.nextFloat() * w
        val sz = h * (0.003f + v * 0.012f)
        canvas.drawOval(x - sz, y - sz * 0.5f, x + sz, y + sz * 0.5f, pen(gold[2 + r.nextInt(3)]))
    }
    umbrellaPerson(w * 0.3f, h * 0.74f, h * 0.2f, c(0xC8302A), c(0x2A2A34))
    umbrellaPerson(w * 0.56f, h * 0.7f, h * 0.15f, c(0xF2C230), c(0x3A4A5A))
    umbrellaPerson(w * 0.76f, h * 0.86f, h * 0.3f, c(0x1E1E24), c(0x5A4A3A))
    umbrellaPerson(w * 0.12f, h * 0.92f, h * 0.34f, c(0x2A5A9A), c(0x2A2A30))
    if (!live) {
        val rain = stroke(0, 0.25f)
        repeat((w * h / 50f).roundToInt()) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            rain.color = Tone.alpha(c(0xD8DEEA), r.range(0.15f, 0.4f))
            canvas.drawLine(x, y, x - h * 0.008f, y + h * 0.05f, rain)
        }
    }
    done(bloom = 0.6f, vignette = 0.3f, threshold = 0.6f)
}

/**
 * Первый иней: the first hard frost, seen close at sunrise — fallen maple leaves and the grass
 * white-rimmed with hoarfrost, glittering where the low sun catches it.
 */
internal fun Painting.firstFrost(art: WeekArt, live: Boolean) {
    sky(0f to 0xE8C8B0, 0.4f to 0xD8C8C8, 0.8f to 0xB8C0D0, 1f to 0xA8B0C0, to = h * 0.52f)
    sun(lightX, lightY, h * 0.045f, c(0xFFD8B0), power = 0.85f, streak = 0.45f)
    val r = Random(921)
    repeat(14) { glow(r.nextFloat() * w, r.nextFloat() * h * 0.45f, h * r.range(0.03f, 0.08f), c(0xFFF0E0), r.range(0.2f, 0.5f)) }
    val far = treeLine(flat(h * 0.46f, h * 0.01f, seed = 922), h * 0.1f..h * 0.2f, h * 0.04f, conifers = 0.3f, seed = 923)
    forest(far, h * 0.52f, veiled(tones(0x5A5058, 0x6E6470, 0x8A8090, 0xB4AEBA, 0xE8E0E8), c(0xD8CCD0), 0.5f), 924, leaf = h * 0.015f, depth = h * 0.06f, rim = 0.4f)
    fog(h * 0.3f, h * 0.55f, c(0xF6E8E0), 0.7f, h * 0.06f, 925)
    val ground = flat(h * 0.5f, h * 0.008f, seed = 926)
    litter(ground, h * 0.48f, tones(0x8A3A22, 0xB8542A, 0xC8843A, 0x9A6A3A, 0x6A3A22), c(0x3A2A1E), c(0xE8D8D0), 927, rime = 0.85f)
    grass(h * 0.78f, h * 1.02f, (w * 0.9f).roundToInt(), h * 0.07f, tones(0xC8D0C8, 0xE0E6EA, 0xA8B4A8, 0xF4F6F8), 928, lean = 0.15f, width = 0.5f)
    val leaves = listOf(
        floatArrayOf(0.12f, 0.7f, 0.08f, -30f), floatArrayOf(0.42f, 0.66f, 0.07f, 40f), floatArrayOf(0.8f, 0.68f, 0.08f, 10f),
        floatArrayOf(0.62f, 0.78f, 0.12f, -60f), floatArrayOf(0.26f, 0.86f, 0.16f, -20f), floatArrayOf(0.88f, 0.88f, 0.15f, 70f),
        floatArrayOf(0.56f, 0.95f, 0.2f, 25f),
    )
    val colors = intArrayOf(c(0xC83A22), c(0xE08A2A), c(0xB8302A), c(0xD8A038), c(0xA83A1E), c(0xE06A2A), c(0xC84A26))
    for ((k, l) in leaves.withIndex()) mapleLeaf(w * l[0], h * l[1], h * l[2], l[3], colors[k], 1f, 930 + k)
    sparkles(h * 0.5f, h, (w * h / 30f).roundToInt(), c(0xFFFFFF), 929, 0.3f)
    done(bloom = 0.55f, vignette = 0.16f, threshold = 0.72f)
}

/** Where the window of the reading room stands in a [w] × [h] picture. */
private fun readingWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.26f, h * 0.38f)
    val left = w * 0.68f
    return RectF(left, h * 0.1f, left + ww, h * 0.58f)
}

/**
 * Вечер с книгой: a November evening in — the armchair with a plaid over its arm, the lamp on the
 * side table, a cup of tea, books on the shelves and in a pile on the floor, the cat asleep on the
 * rug; at the window the first snow falling in the dark.
 */
internal fun Painting.reading(art: WeekArt, live: Boolean) {
    wall(RectF(0f, 0f, w, h * 0.84f), c(0x3A4030), c(0x262A20), grain = 0.18f)
    tabletop(h * 0.84f, c(0x4A3222), 941)
    val rug = pen()
    rug.shader = RadialGradient(w * 0.46f, h * 0.96f, w * 0.44f, intArrayOf(c(0x8A2A22), c(0x6A1E1A), c(0x3A1210)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    canvas.drawOval(w * 0.04f, h * 0.87f, w * 0.88f, h * 1.1f, rug)
    canvas.drawOval(w * 0.08f, h * 0.89f, w * 0.84f, h * 1.08f, stroke(Tone.alpha(c(0xD8A860), 0.6f), max(0.3f, h * 0.008f)))
    // The bookshelf.
    val sl = w * 0.02f
    val sr = sl + min(w * 0.24f, h * 0.36f)
    canvas.drawRect(sl, h * 0.06f, sr, h * 0.84f, pen(c(0x2A1C14)))
    val bookColors = tones(0x7A2A22, 0x2A4A6A, 0x3A5A3A, 0x8A6A2A, 0x5A3A5A, 0x9A8A6A, 0x2A2A3A)
    for (k in 0 until 4) {
        val shelf = h * (0.24f + k * 0.18f)
        books(sl + h * 0.01f, shelf, ((sr - sl) / (h * 0.03f)).roundToInt(), h * 0.14f, bookColors, 942 + k)
        canvas.drawRect(sl, shelf, sr, shelf + h * 0.015f, pen(c(0x4A3222)))
    }
    val win = readingWindow(w, h)
    val frame = h * 0.02f
    window(win, c(0xE8E0D2), frame, 2, 947) {
        sky(0f to 0x0E1630, 0.6f to 0x1E2A50, 1f to 0x34426E, to = h * 0.8f)
        val ground = flat(h * 0.8f, 0f)
        snowfield(ground, h * 0.72f, c(0xB8C4E0), c(0x4C5A88), c(0x6A78A8), 948, relief = 0.5f)
        house(w * 0.6f, h * 0.82f, h * 0.4f, c(0x3A2A26), c(0x2E2426), 949, snowRoof = c(0xDCE4FA), window = c(0xFFB85C), smoke = c(0x9AA8CE))
        birch(w * 0.2f, h * 1.02f, h * 0.9f, h * 0.04f, 950, bark = c(0xE0E4EC), barkShade = c(0x7A869C), twig = c(0x2A2A34), weeping = 0.5f)
        streetLamp(w * 0.86f, h * 0.9f, h * 0.5f, 1f)
        if (!live) snowfall((w * h / 20f).roundToInt(), 951, size = 0.7f)
    }
    curtain(win.left - h * 0.1f, win.left + h * 0.04f, h * 0.04f, h * 0.84f, c(0x6A5A3A), 952, gatherRight = false)
    // The lamp's light, then the chair, the table, the cat.
    val lampX = w * 0.7f
    glow(lampX, h * 0.62f, h * 0.9f, c(0xFFB860), 0.35f)
    armchair(w * 0.44f, h * 0.97f, h * 0.46f, c(0x7A3A2A))
    plaid(w * 0.44f + h * 0.46f * 0.28f, h * 0.97f - h * 0.46f * 0.62f, w * 0.44f + h * 0.46f * 0.58f, h * 0.97f - h * 0.46f * 0.2f)
    val tableTop = h * 0.74f
    canvas.drawRect(lampX - h * 0.012f, tableTop, lampX + h * 0.012f, h * 0.9f, pen(c(0x2A1C14)))
    canvas.drawOval(lampX - h * 0.06f, h * 0.89f, lampX + h * 0.06f, h * 0.91f, pen(c(0x2A1C14)))
    val top = pen()
    top.shader = LinearGradient(0f, tableTop - h * 0.02f, 0f, tableTop + h * 0.02f, c(0x7A5A3A), c(0x3A2818), Shader.TileMode.CLAMP)
    canvas.drawOval(lampX - h * 0.12f, tableTop - h * 0.02f, lampX + h * 0.12f, tableTop + h * 0.02f, top)
    tableLamp(lampX - h * 0.04f, tableTop, h * 0.24f, c(0xE8C890))
    teaCup(lampX + h * 0.06f, tableTop + h * 0.005f, h * 0.05f, c(0xF4F0EA), c(0x8A2A2A))
    // A pile of books by the chair.
    val px = w * 0.2f
    var py = h * 0.93f
    for ((k, color) in bookColors.take(4).withIndex()) {
        val bw = h * (0.14f - k * 0.015f)
        canvas.drawRect(px - bw / 2f + k * h * 0.006f, py - h * 0.025f, px + bw / 2f + k * h * 0.006f, py, pen(color))
        canvas.drawRect(px - bw / 2f + k * h * 0.006f + h * 0.004f, py - h * 0.021f, px + bw / 2f + k * h * 0.006f - h * 0.002f, py - h * 0.004f, pen(Tone.alpha(c(0xF0E8D8), 0.8f)))
        py -= h * 0.025f
    }
    curledCat(w * 0.66f, h * 0.99f, h * 0.1f, c(0xC88A4A))
    done(bloom = 0.65f, vignette = 0.4f, threshold = 0.6f)
}

// endregion
