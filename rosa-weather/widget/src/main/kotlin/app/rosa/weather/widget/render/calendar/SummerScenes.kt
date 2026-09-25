package app.rosa.weather.widget.render.calendar

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Summer's weeks: poplar fluff in a town courtyard, the white nights and the scarlet sails,
 * haymaking, Ivan Kupala's wreaths on the river, the sea, tea on a dacha veranda, a campfire by a
 * tent, the apple harvest, mist on a lake at dawn, a field of sunflowers.
 */

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

private const val PIF = PI.toFloat()

// region Pieces

/** A brig under scarlet sails: a dark hull, two masts, the sails swelling red, lit on their sunny side. */
private fun Painting.brig(x: Float, y: Float, size: Float) {
    val sail = c(0xE0302A)
    // Its red mirror in the water.
    glow(x, y + size * 0.25f, size * 0.5f, c(0xE0402A), 0.3f, squash = 1.4f, mode = android.graphics.BlendMode.SRC_OVER)
    path.reset()
    path.moveTo(x - size * 0.5f, y - size * 0.08f)
    path.lineTo(x + size * 0.56f, y - size * 0.1f)
    path.lineTo(x + size * 0.4f, y)
    path.lineTo(x - size * 0.42f, y)
    path.close()
    canvas.drawPath(path, pen(c(0x2A2226)))
    canvas.drawLine(x - size * 0.48f, y - size * 0.07f, x + size * 0.54f, y - size * 0.09f, stroke(c(0xC8A060), max(0.15f, size * 0.01f)))
    val mast = stroke(c(0x3A2A22), max(0.2f, size * 0.014f))
    for ((k, mx) in listOf(-0.16f, 0.16f).withIndex()) {
        val px = x + mx * size
        val top = y - size * (0.92f - k * 0.06f)
        canvas.drawLine(px, y - size * 0.08f, px, top, mast)
        for (tier in 0..2) {
            val t0 = y - size * (0.14f + tier * 0.25f)
            val t1 = t0 - size * 0.22f
            val half = size * (0.2f - tier * 0.045f)
            val p = pen()
            p.shader = LinearGradient(px - half, 0f, px + half, 0f, Tone.shade(sail, -0.25f), Tone.shade(sail, 0.15f), Shader.TileMode.CLAMP)
            path.reset()
            path.moveTo(px - half, t1)
            path.lineTo(px + half, t1)
            path.quadTo(px + half * 1.15f, (t0 + t1) / 2f, px + half * 0.95f, t0)
            path.lineTo(px - half * 0.95f, t0)
            path.quadTo(px - half * 0.85f, (t0 + t1) / 2f, px - half, t1)
            path.close()
            canvas.drawPath(path, p)
        }
    }
    path.reset()
    path.moveTo(x + size * 0.56f, y - size * 0.1f)
    path.lineTo(x + size * 0.2f, y - size * 0.8f)
    path.lineTo(x + size * 0.22f, y - size * 0.14f)
    path.close()
    canvas.drawPath(path, pen(Tone.shade(sail, 0.05f)))
}

/** A drawbridge opened for the night: its two leaves raised, its stone piers, lamps along it. */
private fun Painting.raisedBridge(x: Float, deck: Float, span: Float, leaf: Float) {
    val iron = c(0x3A3448)
    val pier = pen()
    pier.shader = LinearGradient(0f, deck - span * 0.1f, 0f, deck + span * 0.12f, c(0xA89098), c(0x6A5A66), Shader.TileMode.CLAMP)
    for (side in listOf(-1f, 1f)) {
        val px = x + side * span * 0.5f
        canvas.drawRect(px - span * 0.12f, deck - span * 0.05f, px + span * 0.12f, deck + span * 0.1f, pier)
        // The deck running off to the bank.
        canvas.drawRect(if (side < 0) x - span * 1.4f else px, deck - span * 0.03f, if (side < 0) px else x + span * 1.4f, deck, pen(iron))
    }
    for (side in listOf(-1f, 1f)) {
        val hinge = x + side * span * 0.38f
        canvas.withRotation(side * 62f, hinge, deck - span * 0.02f) {
            val lx0 = hinge
            val lx1 = hinge - side * leaf
            val p = pen(iron)
            canvas.drawRect(minOf(lx0, lx1), deck - span * 0.05f, maxOf(lx0, lx1), deck - span * 0.005f, p)
            val truss = stroke(Tone.shade(iron, 0.15f), max(0.15f, span * 0.006f))
            var t = 0f
            while (t < 1f) {
                val tx = lx0 + (lx1 - lx0) * t
                canvas.drawLine(tx, deck - span * 0.04f, tx + (lx1 - lx0) * 0.08f, deck - span * 0.1f, truss)
                t += 0.12f
            }
            canvas.drawLine(lx0, deck - span * 0.1f, lx1, deck - span * 0.1f, truss)
        }
    }
    for (k in -3..3) {
        if (k == 0) continue
        val lx = x + k * span * 0.3f
        glow(lx, deck - span * 0.14f, span * 0.12f, c(0xFFD890), 0.5f)
        canvas.drawCircle(lx, deck - span * 0.14f, span * 0.012f, pen(c(0xFFF0C8)))
    }
}

/** A golden spire on its tower, catching the last light. */
private fun Painting.spire(x: Float, base: Float, height: Float) {
    canvas.drawRect(x - height * 0.05f, base - height * 0.3f, x + height * 0.05f, base, pen(c(0xC8A8A0)))
    val gold = pen()
    gold.shader = LinearGradient(x - height * 0.03f, 0f, x + height * 0.03f, 0f, c(0xFFF0B0), c(0xB8862E), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - height * 0.035f, base - height * 0.3f)
    path.lineTo(x, base - height)
    path.lineTo(x + height * 0.035f, base - height * 0.3f)
    path.close()
    canvas.drawPath(path, gold)
    glow(x, base - height * 0.6f, height * 0.25f, c(0xFFE0A0), 0.3f)
}

/** A great gilded dome on its colonnaded drum. */
private fun Painting.dome(x: Float, base: Float, size: Float) {
    canvas.drawRect(x - size * 0.5f, base - size * 0.35f, x + size * 0.5f, base, pen(c(0xB8A0A0)))
    val cols = stroke(Tone.alpha(c(0x6A5A60), 0.6f), max(0.15f, size * 0.02f))
    for (k in 0..8) {
        val cx = x - size * 0.45f + size * 0.9f * k / 8f
        canvas.drawLine(cx, base - size * 0.33f, cx, base - size * 0.02f, cols)
    }
    val gold = pen()
    gold.shader = RadialGradient(x - size * 0.15f, base - size * 0.6f, size * 0.6f, intArrayOf(c(0xFFF4C0), c(0xE0B048), c(0x7A5A20)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    canvas.drawArc(RectF(x - size * 0.4f, base - size * 0.78f, x + size * 0.4f, base - size * 0.02f), 180f, 180f, true, gold)
    canvas.drawRect(x - size * 0.04f, base - size * 0.9f, x + size * 0.04f, base - size * 0.74f, gold)
}

/** A hay rake leaning on a stack: a long handle, its head of wooden teeth. */
private fun Painting.rake(x: Float, base: Float, length: Float) {
    val wood = stroke(c(0xC8A06A), max(0.2f, length * 0.02f))
    canvas.drawLine(x, base, x + length * 0.3f, base - length, wood)
    val hx = x + length * 0.3f
    val hy = base - length
    canvas.drawLine(hx - length * 0.12f, hy + length * 0.02f, hx + length * 0.12f, hy - length * 0.02f, wood)
    for (k in 0..6) {
        val tx = hx - length * 0.11f + length * 0.22f * k / 6f
        val ty = hy + length * 0.02f - length * 0.04f * k / 6f
        canvas.drawLine(tx, ty, tx + length * 0.01f, ty + length * 0.05f, stroke(c(0xB8905A), max(0.15f, length * 0.01f)))
    }
}

/** A wooden rowing boat, seen from the side and a little above, oars shipped. */
private fun Painting.rowboat(x: Float, y: Float, size: Float, color: Int) {
    glow(x, y + size * 0.05f, size * 0.7f, c(0x1A2030), 0.3f, squash = 0.2f, mode = android.graphics.BlendMode.SRC_OVER)
    path.reset()
    path.moveTo(x - size * 0.55f, y - size * 0.14f)
    path.quadTo(x, y - size * 0.1f, x + size * 0.6f, y - size * 0.2f)
    path.quadTo(x + size * 0.45f, y + size * 0.02f, x + size * 0.1f, y + size * 0.04f)
    path.lineTo(x - size * 0.4f, y + size * 0.03f)
    path.close()
    val hull = pen()
    hull.shader = LinearGradient(0f, y - size * 0.2f, 0f, y + size * 0.04f, Tone.shade(color, 0.15f), Tone.shade(color, -0.4f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, hull)
    canvas.drawLine(x - size * 0.55f, y - size * 0.14f, x + size * 0.6f, y - size * 0.2f, stroke(c(0xE8E0D0), max(0.2f, size * 0.02f)))
    canvas.drawOval(x - size * 0.4f, y - size * 0.18f, x + size * 0.42f, y - size * 0.1f, pen(Tone.shade(color, -0.55f)))
    canvas.drawLine(x - size * 0.25f, y - size * 0.15f, x + size * 0.5f, y - size * 0.3f, stroke(c(0xC8A06A), max(0.2f, size * 0.018f)))
}

/** A plank pier running out from the shore on posts. */
private fun Painting.pier(x0: Float, y0: Float, x1: Float, y1: Float, width0: Float, width1: Float) {
    path.reset()
    path.moveTo(x0 - width0 / 2f, y0)
    path.lineTo(x1 - width1 / 2f, y1)
    path.lineTo(x1 + width1 / 2f, y1)
    path.lineTo(x0 + width0 / 2f, y0)
    path.close()
    val deck = pen()
    deck.shader = LinearGradient(0f, y1, 0f, y0, c(0x9A8A7A), c(0x6A5242), Shader.TileMode.CLAMP)
    canvas.drawPath(path, deck)
    val planks = stroke(Tone.alpha(c(0x3A2A20), 0.6f), max(0.12f, width0 * 0.01f))
    for (k in 1..14) {
        val t = (k / 15f).pow(1.6f)
        val px = x1 + (x0 - x1) * t
        val py = y1 + (y0 - y1) * t
        val half = (width1 + (width0 - width1) * t) / 2f
        canvas.drawLine(px - half, py, px + half, py, planks)
    }
    val post = pen(c(0x3A2A20))
    for (k in 0..4) {
        val t = (k / 4f).pow(1.4f)
        val px = x1 + (x0 - x1) * t
        val py = y1 + (y0 - y1) * t
        val half = (width1 + (width0 - width1) * t) / 2f
        for (side in listOf(-1f, 1f)) canvas.drawRect(px + side * half - half * 0.06f, py, px + side * half + half * 0.06f, py + half * 0.5f, post)
    }
}

/** A sunflower: a heavy head of seeds in a ring of gold petals, nodding on its stem, leaves below. */
private fun Painting.sunflower(x: Float, y: Float, size: Float, stemBase: Float, seed: Int) {
    val r = Random(seed)
    val stem = stroke(c(0x4A6A2A), max(0.25f, size * 0.08f))
    canvas.drawLine(x, y + size * 0.4f, x + r.range(-0.1f, 0.1f) * size, stemBase, stem)
    for (k in 0..2) {
        val ly = y + size * (0.9f + k * 0.8f)
        if (ly > stemBase) break
        val side = if (k % 2 == 0) -1f else 1f
        canvas.withRotation(side * 35f, x, ly) {
            val leaf = pen()
            leaf.shader = LinearGradient(x, ly, x + side * size * 0.9f, ly, c(0x5A8A2E), c(0x2E4E1A), Shader.TileMode.CLAMP)
            canvas.drawOval(minOf(x, x + side * size * 0.9f), ly - size * 0.22f, maxOf(x, x + side * size * 0.9f), ly + size * 0.22f, leaf)
        }
    }
    val petals = 22
    for (k in 0 until petals) {
        val a = k * 2f * PIF / petals + r.range(-0.05f, 0.05f)
        val px = x + cos(a) * size * 0.62f
        val py = y + sin(a) * size * 0.56f
        canvas.withRotation(a * 57.3f, px, py) {
            val p = pen()
            p.shader = LinearGradient(px - size * 0.24f, 0f, px + size * 0.24f, 0f, c(0xE89A10), c(0xFFD84A), Shader.TileMode.CLAMP)
            canvas.drawOval(px - size * 0.26f, py - size * 0.09f, px + size * 0.26f, py + size * 0.09f, p)
        }
    }
    val disc = pen()
    disc.shader = RadialGradient(x - size * 0.1f, y - size * 0.1f, size * 0.5f, intArrayOf(c(0x7A4A1A), c(0x4A2A10), c(0x2A1808)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    canvas.drawOval(x - size * 0.42f, y - size * 0.38f, x + size * 0.42f, y + size * 0.38f, disc)
    // The seeds' spiral.
    val dot = pen(Tone.alpha(c(0xA87A3A), 0.5f))
    for (k in 0 until 90) {
        val a = k * 2.39996f
        val d = sqrtOf(k / 90f) * size * 0.38f
        canvas.drawCircle(x + cos(a) * d, y + sin(a) * d * 0.9f, max(0.15f, size * 0.018f), dot)
    }
}

private fun sqrtOf(v: Float) = kotlin.math.sqrt(v)

/** A bowl of strawberries, red and glossy, their seeds and green caps. */
private fun Painting.strawberries(x: Float, base: Float, size: Float, seed: Int) {
    floorShadow(x, base, size * 0.8f, size * 0.12f, 0.35f)
    val r = Random(seed)
    repeat(14) {
        val bx = x + r.range(-0.5f, 0.5f) * size
        val by = base - size * r.range(0.42f, 0.6f)
        val br = size * r.range(0.1f, 0.14f)
        val p = pen()
        p.shader = RadialGradient(bx - br * 0.3f, by - br * 0.3f, br * 1.5f, intArrayOf(c(0xFF8A7A), c(0xD8201E), c(0x7A0A10)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(bx - br, by - br * 0.3f)
        path.quadTo(bx - br, by + br * 0.9f, bx, by + br * 1.2f)
        path.quadTo(bx + br, by + br * 0.9f, bx + br, by - br * 0.3f)
        path.quadTo(bx, by - br * 0.8f, bx - br, by - br * 0.3f)
        path.close()
        canvas.drawPath(path, p)
        canvas.drawOval(bx - br * 0.6f, by - br * 0.6f, bx + br * 0.6f, by - br * 0.2f, pen(c(0x3A8A2A)))
    }
    val bowl = pen()
    bowl.shader = LinearGradient(0f, base - size * 0.45f, 0f, base, c(0xF4F2EE), c(0xB0B6C0), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.66f, base - size * 0.46f)
    path.quadTo(x - size * 0.6f, base, x, base)
    path.quadTo(x + size * 0.6f, base, x + size * 0.66f, base - size * 0.46f)
    path.close()
    canvas.drawPath(path, bowl)
    for (k in 0..6) {
        val px = x - size * 0.5f + size * k / 6f
        canvas.drawCircle(px, base - size * 0.34f, size * 0.035f, pen(c(0x3A6AB8)))
    }
}

/** Apples hanging in a crown: red-cheeked, each lit on its side toward the sun. */
private fun Painting.apples(x: Float, y: Float, w0: Float, h0: Float, count: Int, seed: Int) {
    val r = Random(seed)
    repeat(count) {
        val a = r.nextFloat() * 2f * PIF
        val d = kotlin.math.sqrt(r.nextFloat()) * 0.9f
        val ax = x + cos(a) * d * w0 / 2f
        val ay = y + sin(a) * d * h0 / 2f
        val ar = w0 * r.range(0.025f, 0.038f)
        val cheek = if (r.nextFloat() < 0.7f) c(0xD8281E) else c(0xB8C83A)
        val p = pen()
        p.shader = RadialGradient(ax - ar * 0.35f, ay - ar * 0.35f, ar * 1.5f, intArrayOf(Tone.mix(cheek, 0xFFFFFFFF.toInt(), 0.4f), cheek, Tone.shade(cheek, -0.5f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(ax, ay, ar, p)
    }
}

/** A wooden ladder leant into a tree. */
private fun Painting.ladder(x0: Float, base: Float, x1: Float, top: Float, width: Float) {
    val rail = stroke(c(0x9A7A52), max(0.25f, width * 0.12f))
    canvas.drawLine(x0 - width / 2f, base, x1 - width * 0.35f, top, rail)
    canvas.drawLine(x0 + width / 2f, base, x1 + width * 0.35f, top, rail)
    val rung = stroke(c(0x8A6A44), max(0.2f, width * 0.09f))
    for (k in 1..9) {
        val t = k / 10f
        val cx = x0 + (x1 - x0) * t
        val cy = base + (top - base) * t
        val half = width / 2f * (1f - t * 0.3f)
        canvas.drawLine(cx - half, cy, cx + half, cy, rung)
    }
}

// endregion

// region Scenes

/**
 * Тополиный пух: a town courtyard in June — old houses round it, tall poplars, a green bench, the
 * fluff drifting in the sun and lying in drifts along the kerb.
 */
internal fun Painting.poplarFluff(art: WeekArt, live: Boolean) {
    sky(0f to 0x4A8ADA, 0.45f to 0x8ABCEC, 0.85f to 0xCCE2F2, 1f to 0xF0EEE6, to = h * 0.6f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF4DA), power = 0.95f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.36f, 0.3f, h * 0.12f, 501, c(0xFFFFFF), c(0xB8C8DC), opacity = 0.88f)
    facades(-w * 0.05f, w * 1.05f, h * 0.72f, h * 0.13f, tones(0xE8C89A, 0xF0D8B8, 0xE0B8A8, 0xD8C8A0, 0xC8D0B8), 0.04f, 502)
    val crowns = tones(0x1E3A1E, 0x2E5228, 0x4A7438, 0x6E9A4E, 0xA8C87E)
    poplar(w * 0.1f, h * 0.8f, h * 0.84f, crowns, 503)
    poplar(w * 0.84f, h * 0.78f, h * 0.76f, crowns, 504)
    poplar(w * 0.95f, h * 0.82f, h * 0.92f, crowns, 505)
    // The yard: packed earth, dappled with the poplars' shade, the fluff gathered along the edges.
    val top = h * 0.72f
    field(RectF(0f, top, w, h), kx * 0.6f) { x, y ->
        val depth = (y - top) / (h - top)
        val dapple = Noise.fbm(x / (h * 0.08f), y / (h * 0.03f), 506, 3)
        var color = Tone.mix(c(0xA8A298), c(0x8A847A), depth)
        color = Tone.shade(color, (Tone.smooth(0.45f, 0.6f, dapple) - 0.5f) * 0.3f)
        val drift = Noise.fbm(x / (h * 0.05f), y / (h * 0.02f), 507, 3)
        val edge = Tone.smooth(0.25f, 0f, depth)
        Tone.mix(color, c(0xFBFAF6), Tone.smooth(0.6f, 0.72f, drift) * edge * 0.85f)
    }
    // A swing, and the bench.
    val sx = w * 0.3f
    val frame = stroke(c(0x3A5A8A), max(0.3f, h * 0.008f))
    canvas.drawLine(sx - h * 0.1f, h * 0.9f, sx, h * 0.62f, frame)
    canvas.drawLine(sx + h * 0.1f, h * 0.9f, sx, h * 0.62f, frame)
    canvas.drawLine(sx, h * 0.62f, sx + h * 0.3f, h * 0.62f, frame)
    canvas.drawLine(sx + h * 0.2f, h * 0.9f, sx + h * 0.3f, h * 0.62f, frame)
    canvas.drawLine(sx + h * 0.4f, h * 0.9f, sx + h * 0.3f, h * 0.62f, frame)
    val chain = stroke(c(0x5A5A60), max(0.15f, h * 0.003f))
    canvas.drawLine(sx + h * 0.1f, h * 0.62f, sx + h * 0.1f, h * 0.8f, chain)
    canvas.drawLine(sx + h * 0.2f, h * 0.62f, sx + h * 0.2f, h * 0.8f, chain)
    canvas.drawRect(sx + h * 0.08f, h * 0.8f, sx + h * 0.22f, h * 0.815f, pen(c(0xD8402E)))
    bench(w * 0.64f, h * 0.94f, h * 0.22f, c(0x3A6A4A))
    // The fluff on the air.
    if (!live) {
        val r = Random(508)
        repeat((w * h / 180f).roundToInt()) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val s = h * r.range(0.003f, 0.009f)
            val p = pen(Tone.alpha(c(0xFFFFFF), r.range(0.5f, 0.9f)))
            p.maskFilter = BlurMaskFilter(s * 0.6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x, y, s, p)
        }
    }
    done(bloom = 0.35f, vignette = 0.12f)
}

/**
 * Белые ночи: the river in the white night — a pale pink sky that never darkens, the golden spire
 * and the dome over the embankment, a bridge raised for the ships, and a brig under scarlet
 * sails going by.
 */
internal fun Painting.whiteNights(art: WeekArt, live: Boolean) {
    sky(0f to 0x6E7CB0, 0.35f to 0xC4A6C2, 0.7f to 0xF4C8B8, 1f to 0xFBE4CC, to = h * 0.58f)
    glow(lightX, lightY, w * 0.8f, c(0xFFD8B0), 0.45f, squash = 0.3f)
    clouds(h * 0.04f, h * 0.4f, 0.32f, h * 0.08f, 521, c(0xFFD8D0), c(0x8A7AA8), opacity = 0.8f, stretch = 4f, silver = 0.9f)
    facades(-w * 0.05f, w * 1.05f, h * 0.56f, h * 0.045f, tones(0xC8A8A8, 0xD8B8A0, 0xB8A8B8, 0xE0C8B0), 0.18f, 522)
    spire(w * 0.2f, h * 0.53f, h * 0.3f)
    dome(w * 0.5f, h * 0.53f, h * 0.14f)
    haze(h * 0.57f, h * 0.44f, c(0xF8D8C8), 0.35f)
    raisedBridge(w * 0.76f, h * 0.585f, h * 0.42f, h * 0.3f)
    water(h * 0.585f, h, c(0x4A5078), 0.3f, ripple = 0.35f, seed = 523, glint = c(0xFFE0C0), glintStrength = 0.45f)
    brig(w * 0.36f, h * 0.7f, h * 0.34f)
    // The near embankment: granite, a lamp on it.
    val quay = pen()
    quay.shader = LinearGradient(0f, h * 0.9f, 0f, h, c(0xB89A98), c(0x7A6068), Shader.TileMode.CLAMP)
    canvas.drawRect(-1f, h * 0.9f, w + 1f, h + 1f, quay)
    canvas.drawRect(-1f, h * 0.9f, w + 1f, h * 0.915f, pen(c(0xD8C0B8)))
    streetLamp(w * 0.86f, h * 0.9f, h * 0.36f, 0.8f)
    done(bloom = 0.45f, vignette = 0.16f)
}

/**
 * Сенокос: haymaking on an evening meadow — the grass mown in long rows, the stacks going up, a
 * rake leant on the nearest, swallows skimming low, the uncut edge still full of flowers.
 */
internal fun Painting.haymaking(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x4A7AC8, 0.4f to 0x9AB8E0, 0.75f to 0xF0D8B0, 1f to 0xFFE2AA, to = h * 0.6f)
    sun(lightX, lightY, h * 0.04f, c(0xFFD89A), power = 0.9f, streak = 0.4f)
    clouds(h * 0.04f, h * 0.44f, 0.34f, h * 0.12f, 541, c(0xFFE8C8), c(0x8A94B8), opacity = 0.88f, stretch = 2.6f, silver = 0.8f)
    val far = treeLine(flat(h * 0.56f, h * 0.008f, seed = 542), h * 0.04f..h * 0.08f, h * 0.024f, conifers = 0.4f, seed = 543)
    forest(far, h * 0.6f, tones(0x2E3A2A, 0x44583A, 0x66784A, 0x9AA468, 0xF0D8A0), 544, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.5f)
    haze(h * 0.6f, h * 0.5f, c(0xFFE0B8), 0.4f)
    val meadowLine = flat(h * 0.6f, h * 0.006f, seed = 545)
    val cut = tones(0x7A7A3A, 0x9A9448, 0xBAAE5A, 0xD6C878, 0xEEE0A0)
    meadow(meadowLine, h * 0.58f, cut, c(0xE8D8B0), 546)
    // The windrows: the cut hay lying in long rows running away.
    for (k in 0 until 9) {
        val t = k / 8f
        val y = h * 0.62f + (h * 0.38f) * t.pow(1.6f)
        val width = h * (0.006f + t * 0.03f)
        val p = stroke(Tone.alpha(c(0xD8B860), 0.85f), width)
        p.maskFilter = BlurMaskFilter(width * 0.3f, BlurMaskFilter.Blur.NORMAL)
        path.reset()
        path.moveTo(-w * 0.05f, y + h * 0.02f * t)
        path.quadTo(w * 0.5f, y - h * 0.02f, w * 1.05f, y + h * 0.01f)
        canvas.drawPath(path, p)
        canvas.drawPath(path, stroke(Tone.alpha(c(0xFFE8A8), 0.5f), width * 0.3f))
    }
    for ((i, t) in listOf(Triple(0.2f, 0.66f, 0.07f), Triple(0.48f, 0.7f, 0.1f), Triple(0.76f, 0.8f, 0.2f)).withIndex()) {
        val (fx, fy, size) = t
        castShadow(w * fx, h * fy, h * size, h * size * 0.5f, c(0x6A5A2A), 0.4f)
        haystack(w * fx, h * fy, h * size * 0.8f, h * size, c(0xE8C878), c(0x7A6030), 547 + i)
    }
    rake(w * 0.8f + h * 0.08f, h * 0.8f, h * 0.24f)
    birds(w * 0.4f, h * 0.36f, 5, w * 0.2f, h * 0.018f, c(0x1E2030), 550)
    flowers(h * 0.9f, h * 1.02f, (w * 0.3f).roundToInt(), listOf(Bloom.Chamomile, Bloom.Cornflower, Bloom.Clover), 551, size = h * 0.013f)
    grass(h * 0.9f, h * 1.02f, (w * 0.5f).roundToInt(), h * 0.06f, s.grassTones().copyOfRange(1, 5), 552, lean = 0.3f)
    done(bloom = 0.5f, vignette = 0.16f, threshold = 0.72f)
}

/**
 * Иван Купала: the shortest night on the river — a bonfire on the bank, wreaths of flowers with
 * candles set floating down the dark water, their lights doubled in it, ferns in the dark.
 */
internal fun Painting.kupala(art: WeekArt, live: Boolean) {
    sky(0f to 0x080C28, 0.4f to 0x18204E, 0.75f to 0x2C386C, 1f to 0x4A5288, to = h * 0.6f)
    stars(h * 0.5f, (w * h / 150f).roundToInt(), 561, brightness = 0.75f)
    glow(w * 0.7f, h * 0.6f, w * 0.7f, c(0x6A6AA8), 0.3f, squash = 0.25f)
    val far = treeLine(flat(h * 0.56f, h * 0.008f, seed = 562), h * 0.05f..h * 0.1f, h * 0.026f, conifers = 0.5f, seed = 563)
    forest(far, h * 0.6f, tones(0x05070F, 0x080B18, 0x0C1022, 0x161C36, 0x3A4270), 564, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.25f)
    water(h * 0.6f, h, c(0x0A1030), 0.4f, ripple = 0.4f, seed = 565)
    // The bank on the left and the fire on it, its light running out across the water.
    val bank = Path().apply {
        moveTo(-1f, h * 0.62f)
        cubicTo(w * 0.2f, h * 0.6f, w * 0.34f, h * 0.7f, w * 0.3f, h * 0.84f)
        cubicTo(w * 0.28f, h * 0.92f, w * 0.2f, h * 0.98f, w * 0.1f, h * 1.01f)
        lineTo(-1f, h * 1.01f)
        close()
    }
    val fx = w * 0.16f
    val fy = h * 0.72f
    val streak = pen()
    streak.shader = LinearGradient(0f, h * 0.62f, 0f, h, Tone.alpha(c(0xFF9A40), 0.35f), 0, Shader.TileMode.CLAMP)
    canvas.drawRect(fx - h * 0.04f, h * 0.62f, fx + h * 0.5f, h, streak)
    canvas.drawPath(bank, pen(c(0x0A0C16)))
    glow(fx, fy, h * 0.34f, c(0xFF8A3A), 0.35f)
    campfire(fx, fy, h * 0.16f, 566)
    // The wreaths drifting, the nearest largest.
    val wreaths = listOf(floatArrayOf(0.44f, 0.66f, 0.05f), floatArrayOf(0.62f, 0.7f, 0.07f), floatArrayOf(0.8f, 0.64f, 0.045f), floatArrayOf(0.54f, 0.8f, 0.1f), floatArrayOf(0.78f, 0.9f, 0.14f))
    for ((k, wr) in wreaths.withIndex()) {
        val x = w * wr[0]
        val y = h * wr[1]
        val s = h * wr[2]
        val gleam = pen()
        gleam.shader = LinearGradient(0f, y, 0f, y + s * 2.4f, Tone.alpha(c(0xFFC060), 0.45f), 0, Shader.TileMode.CLAMP)
        canvas.drawRect(x - s * 0.08f, y, x + s * 0.08f, y + s * 2.4f, gleam)
        wreath(x, y, s * 2f, 570 + k)
    }
    fern(w * 0.94f, h * 1.02f, h * 0.3f, c(0x1A3A22), 580)
    fern(w * 0.02f, h * 1.04f, h * 0.24f, c(0x16301C), 581)
    if (!live) fireflies(h * 0.5f, h * 0.98f, (w * h / 800f).roundToInt(), c(0xD9F59A), 582, size = 0.45f)
    done(bloom = 0.8f, vignette = 0.3f, threshold = 0.5f, grain = 0.02f)
}

/**
 * Море: a beach at the height of summer — the sea turquoise over the sand and deep blue to the
 * horizon, a sail far out, a lighthouse on the point, a striped umbrella and a deck chair, gulls.
 */
internal fun Painting.seaside(art: WeekArt, live: Boolean) {
    val horizon = h * 0.5f
    sky(0f to 0x1E6AC8, 0.45f to 0x5AA2E8, 0.8f to 0xA8D4F4, 1f to 0xE0F0F8, to = horizon)
    sun(lightX, lightY, h * 0.035f, c(0xFFF8E6), power = 1f, streak = 0.25f)
    clouds(h * 0.1f, h * 0.46f, 0.3f, h * 0.1f, 601, c(0xFFFFFF), c(0xA8C0DC), opacity = 0.9f, stretch = 2.2f)
    val shore = flat(h * 0.72f, h * 0.012f, feature = w * 0.2f, seed = 602)
    sea(horizon, shore.bottom + h * 0.02f, c(0x0A4E9A), c(0x3AC8C8), 603)
    // The point with its lighthouse.
    val point = ridge(h * 0.5f, h * 0.08f, w * 0.3f, 604, lift = h * 0.06f, liftX = w * 0.94f, liftWidth = w * 0.14f)
    val clip = Path().apply {
        moveTo(w * 0.7f, h * 0.52f)
        for (i in point.ys.indices) {
            val x = i * point.step
            if (x >= w * 0.7f) lineTo(x, point.ys[i] + (h * 0.5f - point.ys[i]) * Tone.smooth(w * 0.82f, w * 0.7f, x))
        }
        lineTo(w + 1f, h * 0.52f)
        close()
    }
    canvas.drawPath(clip, pen(c(0x5A7A4A)))
    texture(clip, 0.3f, 1f)
    lighthouse(w * 0.92f, point.at(w * 0.92f) + h * 0.01f, h * 0.2f, lit = 0.3f)
    sailboat(w * 0.56f, h * 0.52f, h * 0.08f)
    sand(shore, horizon, tones(0xC8A878, 0xDCC094, 0xEAD2A8, 0xF4E2C0, 0xFBEFD8), 605)
    surf(shore, 606)
    umbrella(w * 0.3f, h * 0.95f, h * 0.32f, c(0xE8402E), c(0xFFFFFF))
    deckChair(w * 0.5f, h * 0.98f, h * 0.2f, c(0x2A7AC8))
    birds(w * 0.42f, h * 0.28f, 4, w * 0.14f, h * 0.022f, c(0x3A4A5A), 607)
    val r = Random(608)
    repeat(8) {
        val x = r.nextFloat() * w
        val y = h * r.range(0.8f, 1f)
        canvas.drawOval(x - h * 0.008f, y - h * 0.005f, x + h * 0.008f, y + h * 0.005f, pen(c(0xF8F0E8)))
    }
    done(bloom = 0.4f, vignette = 0.1f)
}

/**
 * На даче: tea on the veranda — the samovar on a white cloth, cups, a bowl of strawberries and a
 * jar of jam; beyond the carved rail the garden in July, the hammock slung between the apple trees.
 */
internal fun Painting.veranda(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x5A94D8, 0.45f to 0x9CC4EC, 0.85f to 0xDCEAF0, 1f to 0xF6EAD8, to = h * 0.6f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF0D0), power = 0.9f, streak = 0.2f)
    clouds(h * 0.1f, h * 0.44f, 0.3f, h * 0.12f, 621, c(0xFFFFFF), c(0xB8C8DC), opacity = 0.88f)
    val far = treeLine(flat(h * 0.5f, h * 0.008f, seed = 622), h * 0.06f..h * 0.12f, h * 0.03f, conifers = 0.3f, seed = 623)
    forest(far, h * 0.56f, tones(0x1E3A22, 0x2E5230, 0x4A7042, 0x7A9A5E, 0xC8DAB0), 624, leaf = h * 0.011f, depth = h * 0.05f, rim = 0.35f)
    val lawn = flat(h * 0.55f, h * 0.006f, seed = 625)
    meadow(lawn, h * 0.52f, s.grassTones(), c(0xC8DAB8), 626, speckle = tones(0xF4A8C8, 0xFFFFFF, 0xE870A0), speckleAmount = 0.16f)
    val crown = s.crown()
    broadleaf(w * 0.22f, h * 0.62f, h * 0.36f, h * 0.36f, h * 0.24f, c(0x4A3A2E), crown, 627, leaf = h * 0.01f, lumps = 14)
    broadleaf(w * 0.74f, h * 0.6f, h * 0.34f, h * 0.34f, h * 0.22f, c(0x4A3A2E), crown, 628, leaf = h * 0.01f, lumps = 14)
    apples(w * 0.22f, h * 0.38f, h * 0.36f, h * 0.24f, 10, 629)
    hammock(w * 0.3f, w * 0.66f, h * 0.5f, h * 0.05f, c(0xE8B84A))
    // The veranda: its carved valance across the top, the posts, the rail with turned balusters.
    val wood = c(0xE8DCC8)
    val valance = pen()
    valance.shader = LinearGradient(0f, 0f, 0f, h * 0.12f, c(0x6A4A30), c(0x8A6A48), Shader.TileMode.CLAMP)
    canvas.drawRect(-1f, -1f, w + 1f, h * 0.08f, valance)
    var vx = 0f
    while (vx < w + h * 0.05f) {
        canvas.drawCircle(vx, h * 0.08f, h * 0.025f, pen(c(0xF2EADA)))
        canvas.drawCircle(vx, h * 0.078f, h * 0.008f, pen(c(0x6A4A30)))
        vx += h * 0.05f
    }
    for (px in listOf(w * 0.03f, w * 0.97f)) {
        val post = pen()
        post.shader = LinearGradient(px - h * 0.025f, 0f, px + h * 0.025f, 0f, c(0xFFF8EC), c(0xB8AC98), Shader.TileMode.CLAMP)
        canvas.drawRect(px - h * 0.025f, 0f, px + h * 0.025f, h, post)
    }
    val railY = h * 0.64f
    canvas.drawRect(-1f, railY, w + 1f, railY + h * 0.02f, pen(wood))
    var bx = h * 0.03f
    while (bx < w) {
        val b = pen()
        b.shader = LinearGradient(bx - h * 0.008f, 0f, bx + h * 0.008f, 0f, c(0xFFF8EC), c(0xA89C88), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bx - h * 0.007f, railY + h * 0.02f, bx + h * 0.007f, h * 0.8f, h * 0.006f, h * 0.006f, b)
        canvas.drawOval(bx - h * 0.012f, railY + h * 0.07f, bx + h * 0.012f, railY + h * 0.1f, b)
        bx += h * 0.04f
    }
    tabletop(h * 0.8f, c(0x8A6A4A), 630, cloth = c(0xFBF8F2))
    samovar(w * 0.3f, h * 0.97f, h * 0.3f)
    teaCup(w * 0.52f, h * 0.95f, h * 0.1f, c(0xFBF8F4), c(0x2A5AA8))
    teaCup(w * 0.46f, h * 0.99f, h * 0.11f, c(0xFBF8F4), c(0x2A5AA8))
    strawberries(w * 0.7f, h * 0.97f, h * 0.18f, 631)
    jar(w * 0.86f, h * 0.96f, h * 0.14f, c(0xA82A2A), c(0xE8D8B8))
    done(bloom = 0.35f, vignette = 0.12f)
}

/**
 * Костёр в лесу: a camp by a lake at dusk — the fire going, a pot over it on its tripod, the tent
 * glowing, the pine trunks lit orange, the last light on the water and the first stars.
 */
internal fun Painting.camp(art: WeekArt, live: Boolean) {
    sky(0f to 0x16244E, 0.35f to 0x34427A, 0.7f to 0x8A7A98, 1f to 0xE0A080, to = h * 0.56f)
    stars(h * 0.36f, (w * h / 90f).roundToInt(), 641, brightness = 0.7f)
    val far = treeLine(flat(h * 0.5f, h * 0.006f, seed = 642), h * 0.04f..h * 0.1f, h * 0.024f, conifers = 0.85f, seed = 643)
    forest(far, h * 0.54f, tones(0x0A0C18, 0x121626, 0x1C2236, 0x2E3450, 0x6A5A7A), 644, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.3f)
    water(h * 0.53f, h * 0.64f, c(0x1A2040), 0.3f, ripple = 0.3f, seed = 645)
    val ground = ridge(h * 0.64f, h * 0.01f, w * 0.5f, 646)
    land(ground, h, c(0x2A2420), c(0x14100E), texture = 0.3f)
    val fx = w * 0.38f
    val fy = h * 0.88f
    glow(fx, fy, h * 0.9f, c(0xFF8A3A), 0.4f, squash = 0.5f)
    // Pines round the camp, their trunks lit by the fire.
    val pines = tones(0x0A0E0C, 0x121A14, 0x1E2A1E, 0x3A3A26, 0x8A5A30)
    for ((i, t) in listOf(0.06f to 0.96f, 0.16f to 0.8f, 0.84f to 0.86f, 0.95f to 1f).withIndex()) {
        val (px, ph) = t
        pine(w * px, h * (0.8f + i * 0.04f), h * ph, 647 + i, pines, c(0x2A1810), c(0xE0784A))
    }
    tent(w * 0.68f, h * 0.84f, h * 0.36f, c(0x3A6A4A), 1f)
    // The tripod and the pot.
    val tripod = stroke(c(0x3A2A1E), max(0.2f, h * 0.006f))
    canvas.drawLine(fx - h * 0.1f, fy + h * 0.02f, fx, fy - h * 0.26f, tripod)
    canvas.drawLine(fx + h * 0.1f, fy + h * 0.02f, fx, fy - h * 0.26f, tripod)
    canvas.drawLine(fx + h * 0.02f, fy + h * 0.03f, fx, fy - h * 0.26f, tripod)
    canvas.drawLine(fx, fy - h * 0.26f, fx, fy - h * 0.18f, stroke(c(0x2A2A2E), max(0.15f, h * 0.003f)))
    val pot = pen()
    pot.shader = LinearGradient(fx - h * 0.04f, 0f, fx + h * 0.04f, 0f, c(0x5A5A60), c(0x1A1A1E), Shader.TileMode.CLAMP)
    canvas.drawArc(RectF(fx - h * 0.045f, fy - h * 0.22f, fx + h * 0.045f, fy - h * 0.12f), 0f, 180f, true, pot)
    campfire(fx, fy, h * 0.14f, 651)
    // A log to sit on.
    val log = pen()
    log.shader = LinearGradient(0f, fy - h * 0.02f, 0f, fy + h * 0.05f, c(0x8A5A3A), c(0x2A1A10), Shader.TileMode.CLAMP)
    canvas.drawRoundRect(w * 0.08f, fy - h * 0.01f, w * 0.26f, fy + h * 0.05f, h * 0.03f, h * 0.03f, log)
    if (!live) fireflies(h * 0.5f, h * 0.95f, (w * h / 700f).roundToInt(), c(0xD9F59A), 652, size = 0.45f)
    done(bloom = 0.8f, vignette = 0.32f, threshold = 0.5f, grain = 0.02f)
}

/**
 * Яблочный Спас: the apple harvest — trees heavy with red apples, a ladder in one, baskets full
 * in the grass and windfalls round them, the church's domes over the orchard.
 */
internal fun Painting.appleSaviour(art: WeekArt, live: Boolean) {
    val s = art.season
    sky(0f to 0x4A86D0, 0.45f to 0x8AB8E8, 0.85f to 0xD0E2F0, 1f to 0xF2EEDC, to = h * 0.58f)
    sun(lightX, lightY, h * 0.03f, c(0xFFF0D0), power = 0.9f, streak = 0.2f)
    clouds(h * 0.02f, h * 0.4f, 0.34f, h * 0.12f, 661, c(0xFFFFFF), c(0xB0C0D8), opacity = 0.9f)
    val hill = ridge(h * 0.52f, h * 0.05f, w * 0.7f, 662, lift = h * 0.04f, liftX = w * 0.7f, liftWidth = w * 0.2f)
    land(hill, h, c(0x8AA46A), c(0x6A8A52), texture = 0.12f, shading = 0.2f)
    church(w * 0.7f, hill.at(w * 0.7f) + h * 0.015f, h * 0.1f, c(0xFBF8F0), c(0xE8B84A))
    haze(h * 0.56f, h * 0.46f, c(0xF0EEE0), 0.4f)
    val orchard = flat(h * 0.58f, h * 0.006f, seed = 663)
    meadow(orchard, h * 0.56f, s.grassTones(), c(0xD0DCB8), 664)
    val crown = tones(0x1E3A1A, 0x2E5224, 0x4A7034, 0x6E9448, 0xA8C070)
    val trees = listOf(Triple(0.12f, 0.66f, 0.18f), Triple(0.5f, 0.62f, 0.14f), Triple(0.88f, 0.7f, 0.22f), Triple(0.3f, 0.78f, 0.34f))
    for ((i, t) in trees.withIndex()) {
        val (fx, gy, size) = t
        castShadow(w * fx, h * gy, h * size * 0.5f, h * size * 0.6f, c(0x2E4A1E), 0.3f)
        broadleaf(w * fx, h * gy, h * size * 1.6f, h * size * 1.7f, h * size * 1.1f, c(0x5A4636), crown, 665 + i, leaf = h * size * 0.04f, lumps = 16)
        apples(w * fx, h * gy - h * size * 1.6f + h * size * 0.55f, h * size * 1.6f, h * size * 1f, (size * 90f).roundToInt(), 670 + i)
    }
    ladder(w * 0.4f, h * 0.9f, w * 0.34f, h * 0.36f, h * 0.06f)
    basket(w * 0.62f, h * 0.95f, h * 0.2f, tones(0xD8281E, 0xC8302A, 0xB8C83A, 0xE84A2A), 675)
    basket(w * 0.8f, h * 1f, h * 0.24f, tones(0xD8281E, 0xE84A2A, 0xC8302A), 676)
    val r = Random(677)
    repeat(10) {
        val x = r.nextFloat() * w
        val y = h * r.range(0.82f, 1f)
        val ar = h * r.range(0.012f, 0.02f)
        val p = pen()
        p.shader = RadialGradient(x - ar * 0.3f, y - ar * 0.3f, ar * 1.4f, intArrayOf(c(0xFF8A7A), c(0xC8201A), c(0x6A0A08)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, ar, p)
    }
    grass(h * 0.9f, h * 1.02f, (w * 0.5f).roundToInt(), h * 0.04f, s.grassTones().copyOfRange(1, 5), 678)
    done(bloom = 0.35f, vignette = 0.14f)
}

/**
 * Туман над озером: dawn on a still lake — the mist lying on the water, the far shore's spruces
 * standing out of it, a boat tied at a little pier, reeds, the sun just risen, pink in the air.
 */
internal fun Painting.lakeMist(art: WeekArt, live: Boolean) {
    sky(0f to 0x8A9AC0, 0.4f to 0xC8B8C8, 0.75f to 0xF2D2C4, 1f to 0xFCE8D8, to = h * 0.56f)
    sun(lightX, lightY, h * 0.04f, c(0xFFE0C0), power = 0.65f, streak = 0.35f)
    val far = treeLine(flat(h * 0.52f, h * 0.008f, seed = 681), h * 0.06f..h * 0.14f, h * 0.028f, conifers = 0.85f, seed = 682)
    forest(far, h * 0.56f, veiled(tones(0x3A4050, 0x4E5466, 0x6A6E80, 0x9A96A4, 0xE8D8D0), c(0xE8D4CC), 0.45f), 683, leaf = h * 0.011f, depth = h * 0.05f, rim = 0.4f)
    water(h * 0.555f, h, c(0x6A7890), 0.2f, ripple = 0.15f, seed = 684, glint = c(0xFFE8D0), glintStrength = 0.4f)
    fog(h * 0.42f, h * 0.66f, c(0xFBEAE2), 0.9f, h * 0.05f, 685, peak = 0.7f)
    fog(h * 0.5f, h * 0.82f, c(0xF6E4DC), 0.6f, h * 0.07f, 686, peak = 0.3f)
    reeds(w * 0.72f, w * 1.02f, h * 0.84f, (w / 3f).roundToInt(), h * 0.16f, c(0x5A6040), c(0x3A2E22), 687)
    pier(w * 0.16f, h * 1.02f, w * 0.36f, h * 0.7f, h * 0.36f, h * 0.1f)
    rowboat(w * 0.5f, h * 0.84f, h * 0.3f, c(0x3A6A8A))
    canvas.drawLine(w * 0.34f, h * 0.76f, w * 0.44f, h * 0.8f, stroke(c(0x5A4A3A), max(0.15f, h * 0.003f)))
    reeds(-w * 0.02f, w * 0.12f, h * 0.9f, (w / 8f).roundToInt(), h * 0.12f, c(0x5A6040), c(0x3A2E22), 688)
    done(bloom = 0.45f, vignette = 0.14f)
}

/** Подсолнухи: a field of sunflowers at sunset, their heads in rows to the horizon, the nearest as big as plates. */
internal fun Painting.sunflowers(art: WeekArt, live: Boolean) {
    sky(0f to 0x3A56A4, 0.35f to 0x8A78AE, 0.7f to 0xF0A070, 1f to 0xFFD890, to = h * 0.56f)
    sun(lightX, lightY, h * 0.05f, c(0xFFC880), power = 0.95f, streak = 0.5f)
    clouds(h * 0.02f, h * 0.4f, 0.32f, h * 0.1f, 701, c(0xFFC8A0), c(0x6E5A8A), opacity = 0.85f, stretch = 3.2f, silver = 0.9f)
    val far = treeLine(flat(h * 0.54f, h * 0.004f, seed = 702), h * 0.02f..h * 0.05f, h * 0.02f, conifers = 0.2f, seed = 703)
    forest(far, h * 0.56f, tones(0x3A2A40, 0x4E3A52, 0x6A506A, 0x9A7488, 0xFFC090), 704, leaf = h * 0.008f, depth = h * 0.02f, rim = 0.5f)
    church(w * 0.3f, h * 0.545f, h * 0.05f, c(0xE8C8C0), c(0xE8A84A))
    haze(h * 0.56f, h * 0.44f, c(0xFFC895), 0.5f)
    // The field: rows of heads running to the horizon, leaves dark between them.
    val top = h * 0.56f
    val base = pen()
    base.shader = LinearGradient(0f, top, 0f, h, c(0x5A5A2A), c(0x2A3A18), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, top, w, h, base)
    val r = Random(705)
    val rows = 26
    for (k in 0 until rows) {
        val t = (k / (rows - 1f)).pow(1.8f)
        val y = top + (h * 0.95f - top) * t
        val size = h * (0.004f + t * 0.05f)
        var x = r.range(0f, size * 2f)
        while (x < w + size) {
            val p = pen()
            p.color = Tone.mix(c(0xE8A020), c(0xFFD050), r.nextFloat())
            canvas.drawOval(x - size, y - size * 0.8f, x + size, y + size * 0.8f, p)
            if (size > 0.8f) canvas.drawOval(x - size * 0.45f, y - size * 0.35f, x + size * 0.45f, y + size * 0.35f, pen(c(0x4A2A10)))
            x += size * r.range(2.2f, 3.2f)
        }
    }
    val glowing = pen()
    glowing.shader = LinearGradient(0f, top, 0f, top + h * 0.2f, Tone.alpha(c(0xFFC080), 0.35f), 0, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, top, w, top + h * 0.2f, glowing)
    sunflower(w * 0.2f, h * 0.72f, h * 0.14f, h * 1.05f, 706)
    sunflower(w * 0.8f, h * 0.66f, h * 0.17f, h * 1.05f, 707)
    sunflower(w * 0.52f, h * 0.86f, h * 0.2f, h * 1.1f, 708)
    done(bloom = 0.6f, vignette = 0.22f, threshold = 0.68f)
}

// endregion
