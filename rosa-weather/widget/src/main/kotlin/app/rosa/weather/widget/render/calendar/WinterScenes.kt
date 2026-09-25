package app.rosa.weather.widget.render.calendar

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Winter's weeks beyond the classic landscapes: Christmas night by a wooden church, the Epiphany
 * frosts on the river, an evening in a log house, a ski track, a blizzard, Maslenitsa, the March
 * drops from the eaves — and at the year's other end the first snow, the bullfinches, the skating
 * rink, the frosted window and the New Year's night.
 */

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

private const val PIF = PI.toFloat()

// region Pieces

/** The Christmas star: a burning point with long rays, the longest pointing down to the church. */
private fun Painting.christmasStar(x: Float, y: Float, reach: Float) {
    glow(x, y, reach * 0.9f, c(0xA8BCFF), 0.3f)
    glow(x, y, reach * 0.28f, c(0xFFF0D0), 0.75f)
    val rays = listOf(PIF / 2f to 1f, -PIF / 2f to 0.42f, 0f to 0.5f, PIF to 0.5f, PIF / 4f to 0.2f, 3f * PIF / 4f to 0.2f, -PIF / 4f to 0.2f, -3f * PIF / 4f to 0.2f)
    for ((a, len) in rays) {
        val ex = x + cos(a) * reach * len
        val ey = y + sin(a) * reach * len
        val p = stroke(0, max(0.3f, reach * 0.014f))
        p.shader = LinearGradient(x, y, ex, ey, 0xFFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        p.maskFilter = BlurMaskFilter(max(0.3f, reach * 0.008f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawLine(x, y, ex, ey, p)
    }
    canvas.drawCircle(x, y, max(0.5f, reach * 0.02f), pen(0xFFFFFFFF.toInt()))
}

/** A block of logs from [l] to [r], [t] to [b]: lit on the side of the light, the seams dark. */
private fun Painting.logBlock(l: Float, t: Float, r: Float, b: Float, wall: Int, log: Float) {
    val p = pen()
    val lit = lightX < (l + r) / 2f
    p.shader = LinearGradient(l, 0f, r, 0f, if (lit) Tone.shade(wall, 0.2f) else Tone.shade(wall, -0.3f), if (lit) Tone.shade(wall, -0.3f) else Tone.shade(wall, 0.2f), Shader.TileMode.CLAMP)
    canvas.drawRect(l, t, r, b, p)
    val seam = stroke(Tone.alpha(Tone.shade(wall, -0.6f), 0.6f), max(0.12f, log * 0.12f))
    var y = t + log
    while (y < b) {
        canvas.drawLine(l, y, r, y, seam)
        y += log
    }
    // The log ends sticking out at the corners.
    val ends = pen(Tone.shade(wall, 0.12f))
    y = t + log * 0.5f
    while (y < b) {
        canvas.drawCircle(l - log * 0.15f, y, log * 0.42f, ends)
        canvas.drawCircle(r + log * 0.15f, y, log * 0.42f, ends)
        y += log
    }
}

/** A gable roof under snow from [l] to [r] on [eave], rising to [peak]. */
private fun Painting.snowRoof(l: Float, r: Float, eave: Float, peak: Float, snow: Int, shade: Int) {
    val x = (l + r) / 2f
    path.reset()
    path.moveTo(l, eave)
    path.lineTo(x, peak)
    path.lineTo(r, eave)
    path.quadTo(x, eave + (eave - peak) * 0.12f, l, eave)
    path.close()
    val p = pen()
    val lit = lightX < x
    p.shader = LinearGradient(l, 0f, r, 0f, if (lit) snow else shade, if (lit) shade else snow, Shader.TileMode.CLAMP)
    canvas.drawPath(path, p)
}

/** A tall tented roof — a шатёр — under snow, from [base] up [height], [width] wide at the foot. */
private fun Painting.tent(x: Float, base: Float, width: Float, height: Float, wood: Int, snow: Int) {
    path.reset()
    path.moveTo(x - width / 2f, base)
    path.lineTo(x, base - height)
    path.lineTo(x + width / 2f, base)
    path.close()
    val p = pen()
    p.shader = LinearGradient(x - width / 2f, 0f, x + width / 2f, 0f, if (lightX < x) Tone.shade(wood, 0.15f) else Tone.shade(wood, -0.35f), if (lightX < x) Tone.shade(wood, -0.35f) else Tone.shade(wood, 0.15f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, p)
    // Snow caught in the shingles, streaking down the tent.
    val r = Random((x * 7f).toInt())
    val s = stroke(Tone.alpha(snow, 0.8f), max(0.2f, width * 0.05f))
    repeat(7) {
        val t = r.range(0.15f, 0.95f)
        val y = base - height * t
        val half = width / 2f * (1f - t)
        val sx = x + r.range(-1f, 1f) * half * 0.8f
        canvas.drawLine(sx, y, sx + (sx - x) * 0.08f, y + height * r.range(0.04f, 0.1f), s)
    }
}

/** An onion dome with its cross, gilded, catching the light. */
private fun Painting.onionDome(x: Float, base: Float, r: Float, gold: Int) {
    path.reset()
    path.moveTo(x - r, base)
    path.cubicTo(x - r * 1.35f, base - r * 1.15f, x - r * 0.1f, base - r * 1.4f, x, base - r * 2.2f)
    path.cubicTo(x + r * 0.1f, base - r * 1.4f, x + r * 1.35f, base - r * 1.15f, x + r, base)
    path.close()
    val p = pen()
    p.shader = RadialGradient(x - r * 0.35f, base - r * 0.9f, r * 1.6f, intArrayOf(Tone.mix(gold, 0xFFFFFFFF.toInt(), 0.55f), gold, Tone.shade(gold, -0.5f)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, p)
    val cross = stroke(gold, max(0.15f, r * 0.12f), round = false)
    canvas.drawLine(x, base - r * 2.2f, x, base - r * 3.3f, cross)
    canvas.drawLine(x - r * 0.35f, base - r * 2.95f, x + r * 0.35f, base - r * 2.95f, cross)
    canvas.drawLine(x - r * 0.22f, base - r * 2.5f, x + r * 0.22f, base - r * 2.62f, cross)
}

/** A small arched window, lit warm from within, its light glowing out into the night. */
private fun Painting.litWindow(x: Float, top: Float, width: Float, height: Float, light: Float) {
    if (light > 0f) glow(x, top + height * 0.5f, width * 4f, c(0xFFB860), 0.4f * light)
    val p = pen(Tone.mix(c(0x2A2030), c(0xFFD58A), light))
    canvas.drawRect(x - width / 2f, top + width / 2f, x + width / 2f, top + height, p)
    canvas.drawCircle(x, top + width / 2f, width / 2f, p)
}

/**
 * A wooden church of the north: a log nave, a lower apse, a porch, and a tall tented tower crowned
 * with a small golden onion; snow on every roof, the windows lit.
 */
private fun Painting.woodenChurch(x: Float, ground: Float, size: Float, lit: Float) {
    val wall = c(0x6A4834)
    val snow = c(0xE8EEFC)
    val shade = c(0x8A9ACC)
    val log = size * 0.035f
    // The apse to the left, the porch to the right, the nave between; the tower rising over the nave.
    val nw = size * 0.44f
    val nh = size * 0.26f
    val nl = x - nw / 2f
    val nt = ground - nh
    logBlock(nl - size * 0.2f, ground - size * 0.18f, nl + 1f, ground, wall, log)
    snowRoof(nl - size * 0.23f, nl + size * 0.02f, ground - size * 0.18f, ground - size * 0.3f, snow, shade)
    logBlock(nl + nw - 1f, ground - size * 0.2f, nl + nw + size * 0.16f, ground, wall, log)
    snowRoof(nl + nw - size * 0.02f, nl + nw + size * 0.19f, ground - size * 0.2f, ground - size * 0.32f, snow, shade)
    logBlock(nl, nt, nl + nw, ground, wall, log)
    snowRoof(nl - size * 0.04f, nl + nw + size * 0.04f, nt, nt - size * 0.12f, snow, shade)
    // The tower: an octagon of logs, then the tent, then the dome.
    val tw = size * 0.2f
    val tb = nt - size * 0.06f
    logBlock(x - tw / 2f, tb - size * 0.16f, x + tw / 2f, tb, wall, log)
    val tentBase = tb - size * 0.16f
    tent(x, tentBase, tw * 1.25f, size * 0.42f, c(0x5A4A40), snow)
    onionDome(x, tentBase - size * 0.4f, size * 0.035f, c(0xE8B84A))
    // A little dome over the apse.
    onionDome(nl - size * 0.1f, ground - size * 0.29f, size * 0.022f, c(0xE8B84A))
    // Windows, and the open door with its light on the snow.
    for (k in 0..2) litWindow(nl + nw * (0.2f + k * 0.3f), nt + nh * 0.25f, size * 0.035f, size * 0.07f, lit)
    litWindow(x, tb - size * 0.12f, size * 0.03f, size * 0.07f, lit)
    litWindow(nl - size * 0.1f, ground - size * 0.14f, size * 0.03f, size * 0.06f, lit)
    val door = nl + nw + size * 0.08f
    glow(door, ground, size * 0.3f, c(0xFFB860), 0.5f * lit, squash = 0.35f)
    canvas.drawRect(door - size * 0.025f, ground - size * 0.1f, door + size * 0.025f, ground, pen(Tone.mix(c(0x2A2030), c(0xFFD08A), lit)))
}

/**
 * The Jordan cut for Epiphany: a cross-shaped hole in the river's ice, seen lying flat — black
 * water in it, a rim of broken ice and trodden snow round it, steam rising — and a cross carved
 * of ice standing at its head, the sun in it.
 */
private fun Painting.jordan(x: Float, y: Float, size: Float) {
    val flat = 0.4f
    fun cross(scale: Float): Path {
        val arm = size * 0.16f * scale
        val long = size * 0.62f * scale
        val bar = size * 0.42f * scale
        val barY = -size * 0.2f * scale
        return Path().apply {
            moveTo(x - arm, y + (-long) * flat)
            lineTo(x + arm, y + (-long) * flat)
            lineTo(x + arm, y + (barY - arm) * flat)
            lineTo(x + bar, y + (barY - arm) * flat)
            lineTo(x + bar, y + (barY + arm) * flat)
            lineTo(x + arm, y + (barY + arm) * flat)
            lineTo(x + arm, y + long * 0.55f * flat)
            lineTo(x - arm, y + long * 0.55f * flat)
            lineTo(x - arm, y + (barY + arm) * flat)
            lineTo(x - bar, y + (barY + arm) * flat)
            lineTo(x - bar, y + (barY - arm) * flat)
            lineTo(x - arm, y + (barY - arm) * flat)
            close()
        }
    }
    canvas.drawOval(x - size * 0.9f, y - size * 0.34f, x + size * 0.9f, y + size * 0.3f, pen(Tone.alpha(c(0x9AAACC), 0.5f)))
    val rim = cross(1.22f)
    val rimPaint = pen()
    rimPaint.shader = LinearGradient(0f, y - size * 0.3f, 0f, y + size * 0.3f, c(0xF6FAFF), c(0xC8D6EC), Shader.TileMode.CLAMP)
    canvas.drawPath(rim, rimPaint)
    val water = cross(1f)
    val dark = pen()
    dark.shader = LinearGradient(0f, y - size * 0.25f, 0f, y + size * 0.2f, c(0x0E2238), c(0x2A4E6E), Shader.TileMode.CLAMP)
    canvas.drawPath(water, dark)
    canvas.withClip(water) {
        glow(lightX, y, size * 0.5f, c(0xFFE8C8), 0.35f, squash = 0.3f)
        drawPath(water, stroke(Tone.alpha(c(0x6A8AB0), 0.6f), size * 0.03f))
    }
    fog(y - size * 0.9f, y + size * 0.05f, c(0xF6F8FC), 0.55f, size * 0.12f, 37, peak = 0.75f)
    // The ice cross at the head of the Jordan: blocks of clear ice, blue in their depth.
    val cx = x
    val cb = y - size * 0.62f * flat - size * 0.04f
    val ch = size * 0.9f
    val ice = pen()
    ice.shader = LinearGradient(cx - size * 0.1f, 0f, cx + size * 0.1f, 0f, intArrayOf(c(0xB8D4EE), c(0xF4FAFF), c(0x8AB0D8)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(cx - size * 0.06f, cb - ch, cx + size * 0.06f, cb, ice)
    canvas.drawRect(cx - size * 0.26f, cb - ch * 0.78f, cx + size * 0.26f, cb - ch * 0.66f, ice)
    canvas.drawRect(cx - size * 0.14f, cb - ch * 0.93f, cx + size * 0.14f, cb - ch * 0.86f, ice)
    glow(cx - size * 0.02f, cb - ch * 0.72f, size * 0.3f, c(0xFFFFFF), 0.4f)
    canvas.drawOval(cx - size * 0.16f, cb - size * 0.03f, cx + size * 0.16f, cb + size * 0.04f, pen(c(0xF2F6FC)))
}

/** Skis stuck upright in a drift, and the poles beside them. */
private fun Painting.skis(x: Float, base: Float, length: Float, color: Int) {
    for ((k, tilt) in listOf(-0.07f, 0.05f).withIndex()) {
        canvas.withRotation(tilt * 57.3f, x + k * length * 0.07f, base) {
            val sx = x + k * length * 0.07f
            val sw = length * 0.045f
            val p = pen()
            p.shader = LinearGradient(sx - sw, 0f, sx + sw, 0f, Tone.shade(color, 0.2f), Tone.shade(color, -0.35f), Shader.TileMode.CLAMP)
            path.reset()
            path.moveTo(sx - sw, base)
            path.lineTo(sx - sw, base - length * 0.9f)
            path.quadTo(sx - sw, base - length, sx + sw * 0.4f, base - length * 1.02f)
            path.lineTo(sx + sw, base - length * 0.93f)
            path.lineTo(sx + sw, base)
            path.close()
            canvas.drawPath(path, p)
            canvas.drawLine(sx, base, sx, base - length * 0.9f, stroke(Tone.alpha(0xFFFFFFFF.toInt(), 0.55f), sw * 0.3f))
        }
    }
    val pole = stroke(c(0x2A2A32), max(0.2f, length * 0.014f))
    for ((k, a) in listOf(0.12f, 0.2f).withIndex()) {
        val px = x + length * (0.2f + k * 0.08f)
        val top = base - length * 0.95f
        canvas.drawLine(px, base + length * 0.02f, px + length * a * 0.3f, top, pole)
        canvas.drawOval(px - length * 0.04f, base - length * 0.1f, px + length * 0.05f, base - length * 0.07f, stroke(c(0x2A2A32), max(0.15f, length * 0.008f)))
        canvas.drawLine(px + length * a * 0.3f, top, px + length * a * 0.3f + length * 0.02f, top + length * 0.1f, stroke(c(0xC83A30), max(0.2f, length * 0.012f)))
    }
    // The drift they stand in.
    val d = pen()
    d.shader = LinearGradient(0f, base - length * 0.06f, 0f, base + length * 0.05f, c(0xFFFFFF), c(0xB8C8E8), Shader.TileMode.CLAMP)
    canvas.drawOval(x - length * 0.2f, base - length * 0.06f, x + length * 0.42f, base + length * 0.06f, d)
}

/** A glass of tea in its metal holder — a подстаканник — the tea amber, a spoon in it. */
private fun Painting.teaGlass(x: Float, base: Float, size: Float) {
    floorShadow(x + size * 0.1f, base, size * 0.55f, size * 0.1f, 0.35f)
    val hw = size * 0.26f
    val top = base - size
    val tea = pen()
    tea.shader = LinearGradient(x - hw, 0f, x + hw, 0f, intArrayOf(c(0x8A3A10), c(0xE08A30), c(0x9A4A14)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(x - hw, top + size * 0.12f, x + hw, base - size * 0.1f, tea)
    canvas.drawRect(x - hw, top, x + hw, top + size * 0.12f, pen(0x55E8F0F8))
    canvas.drawLine(x - hw * 0.6f, top + size * 0.05f, x - hw * 0.6f, base - size * 0.4f, stroke(0x88FFFFFF.toInt(), hw * 0.14f))
    // The spoon.
    canvas.drawLine(x + hw * 0.3f, top - size * 0.18f, x + hw * 0.1f, base - size * 0.3f, stroke(c(0xC8CCD4), max(0.15f, size * 0.03f)))
    // The holder: a lattice cup of silver round the lower glass, its handle.
    val silver = pen()
    silver.shader = LinearGradient(x - hw * 1.1f, 0f, x + hw * 1.1f, 0f, intArrayOf(c(0x6A6E78), c(0xF4F6FA), c(0x8A8E98), c(0x4A4E58)), floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - hw * 1.08f, base - size * 0.5f)
    path.lineTo(x + hw * 1.08f, base - size * 0.5f)
    path.lineTo(x + hw * 0.95f, base - size * 0.04f)
    path.lineTo(x + hw * 1.2f, base)
    path.lineTo(x - hw * 1.2f, base)
    path.lineTo(x - hw * 0.95f, base - size * 0.04f)
    path.close()
    canvas.drawPath(path, silver)
    val lace = stroke(Tone.alpha(c(0x3A3E48), 0.6f), max(0.12f, size * 0.012f))
    for (k in 0..5) {
        val lx = x - hw + hw * 2f * k / 5f
        canvas.drawLine(lx, base - size * 0.46f, lx + hw * 0.2f, base - size * 0.08f, lace)
    }
    canvas.drawArc(RectF(x + hw * 0.9f, base - size * 0.46f, x + hw * 1.7f, base - size * 0.12f), -90f, 180f, false, stroke(c(0xB8BCC4), max(0.2f, size * 0.045f)))
    steam(x, top - size * 0.02f, size * 1.2f, 0.9f, 3)
}

/** A tangerine: dimpled orange peel lit on one side, a green leaf at its stalk. */
private fun Painting.tangerine(x: Float, base: Float, size: Float, leaf: Boolean) {
    floorShadow(x + size * 0.1f, base, size * 0.6f, size * 0.12f, 0.35f)
    val r = size * 0.5f
    val cy = base - r * 0.9f
    val p = pen()
    p.shader = RadialGradient(x - r * 0.35f, cy - r * 0.35f, r * 1.5f, intArrayOf(c(0xFFD08A), c(0xFF8A1E), c(0xB8480A)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    canvas.drawOval(x - r, cy - r * 0.86f, x + r, cy + r * 0.86f, p)
    canvas.drawCircle(x - r * 0.35f, cy - r * 0.35f, r * 0.16f, pen(0x66FFFFFF))
    if (leaf) {
        canvas.withRotation(-30f, x, cy - r * 0.85f) {
            canvas.drawOval(x, cy - r * 1.05f, x + r * 0.9f, cy - r * 0.7f, pen(c(0x3E7A2E)))
        }
    }
    canvas.drawCircle(x, cy - r * 0.84f, r * 0.08f, pen(c(0x4A5A22)))
}

/** A skater gliding: a coat, a scarf flying, a hat with a bobble, one leg pushing off. */
private fun Painting.skater(x: Float, base: Float, size: Float, coat: Int, scarf: Int, facing: Float) {
    val f = if (facing >= 0f) 1f else -1f
    floorShadow(x, base, size * 0.25f, size * 0.04f, 0.3f)
    val legs = stroke(c(0x1E1E26), max(0.2f, size * 0.07f))
    canvas.drawLine(x, base - size * 0.42f, x - f * size * 0.04f, base, legs)
    canvas.drawLine(x, base - size * 0.42f, x - f * size * 0.3f, base - size * 0.12f, legs)
    canvas.drawLine(x - f * size * 0.1f, base, x + f * size * 0.08f, base, stroke(c(0xDDE4F0), max(0.15f, size * 0.025f)))
    val p = pen()
    p.shader = LinearGradient(x - size * 0.15f, 0f, x + size * 0.15f, 0f, Tone.shade(coat, 0.15f), Tone.shade(coat, -0.3f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.1f, base - size * 0.4f)
    path.lineTo(x + f * size * 0.06f, base - size * 0.82f)
    path.lineTo(x + f * size * 0.2f, base - size * 0.78f)
    path.lineTo(x + size * 0.12f * f, base - size * 0.38f)
    path.close()
    canvas.drawPath(path, p)
    canvas.drawLine(x + f * size * 0.1f, base - size * 0.72f, x - f * size * 0.14f, base - size * 0.52f, stroke(coat, max(0.2f, size * 0.06f)))
    val hx = x + f * size * 0.15f
    val hy = base - size * 0.9f
    canvas.drawCircle(hx, hy, size * 0.08f, pen(c(0xF0C8A8)))
    canvas.drawArc(RectF(hx - size * 0.085f, hy - size * 0.1f, hx + size * 0.085f, hy + size * 0.04f), 180f, 180f, true, pen(scarf))
    canvas.drawCircle(hx, hy - size * 0.1f, size * 0.035f, pen(Tone.mix(scarf, 0xFFFFFFFF.toInt(), 0.6f)))
    canvas.drawLine(hx - f * size * 0.02f, hy + size * 0.1f, hx - f * size * 0.22f, hy + size * 0.06f, stroke(scarf, max(0.2f, size * 0.045f)))
}

/** A round wall clock, its hands at a few minutes to midnight. */
private fun Painting.wallClock(x: Float, y: Float, r: Float) {
    floorShadow(x + r * 0.1f, y + r * 0.1f, r * 1.1f, r * 1.1f, 0.3f)
    val rim = pen()
    rim.shader = LinearGradient(x - r, y - r, x + r, y + r, c(0xE8C87A), c(0x8A6A2A), Shader.TileMode.CLAMP)
    canvas.drawCircle(x, y, r, rim)
    canvas.drawCircle(x, y, r * 0.86f, pen(c(0xF4EEE0)))
    val tick = stroke(c(0x2A2420), max(0.12f, r * 0.05f), round = false)
    for (k in 0 until 12) {
        val a = k * PIF / 6f
        canvas.drawLine(x + cos(a) * r * 0.7f, y + sin(a) * r * 0.7f, x + cos(a) * r * 0.8f, y + sin(a) * r * 0.8f, tick)
    }
    val hand = stroke(c(0x1E1A18), max(0.2f, r * 0.08f))
    canvas.drawLine(x, y, x + cos(-PIF / 2f - 0.05f) * r * 0.45f, y + sin(-PIF / 2f - 0.05f) * r * 0.45f, hand)
    canvas.drawLine(x, y, x + cos(-PIF / 2f - 0.26f) * r * 0.68f, y + sin(-PIF / 2f - 0.26f) * r * 0.68f, stroke(c(0x1E1A18), max(0.15f, r * 0.05f)))
    canvas.drawCircle(x, y, r * 0.06f, pen(c(0x8A2A20)))
}

// endregion

// region Scenes

/**
 * Рождество: a wooden church on a snowy rise under the Christmas star, its windows lit, lanterns
 * along the trodden path to its door, the village asleep round it, snow coming down.
 */
internal fun Painting.christmas(art: WeekArt, live: Boolean) {
    sky(0f to 0x07102E, 0.4f to 0x16275E, 0.75f to 0x34498A, 1f to 0x6378B4, to = h * 0.64f)
    stars(h * 0.6f, (w * h / 42f).roundToInt(), 1, brightness = 0.9f)
    val far = ridge(h * 0.6f, h * 0.05f, h * 0.8f, 2)
    land(far, h, c(0x3A4E86), c(0x5A6EA6), texture = 0.06f, rim = c(0xB8C8F4), rimStrength = 0.35f, shading = 0.2f)
    val forestLine = treeLine(ridge(h * 0.64f, h * 0.015f, h * 0.5f, 3), h * 0.035f..h * 0.08f, h * 0.022f, conifers = 0.95f, seed = 4)
    forest(forestLine, h * 0.7f, tones(0x0A1230, 0x142046, 0x22325E, 0x4A5C8E, 0xB8C6EC), 5, leaf = h * 0.011f, depth = h * 0.05f, rim = 0.4f)
    haze(h * 0.68f, h * 0.6f, c(0x5E72AA), 0.35f)
    // The rise the church stands on, and the star over it.
    val cx = w * 0.62f
    val rise = ridge(h * 0.76f, h * 0.05f, w * 0.5f, 6, lift = h * 0.06f, liftX = cx, liftWidth = w * 0.25f)
    christmasStar(lightX, lightY, h * 0.55f)
    snowfield(rise, h * 0.66f, c(0xD4DEF8), c(0x56689E), c(0x7A8CC0), 7, relief = 0.9f)
    val cg = rise.at(cx) + h * 0.012f
    woodenChurch(cx, cg, h * 0.42f, lit = 1f)
    // The village: log houses along the foot of the rise, windows warm, smoke going straight up.
    for ((i, fx) in listOf(0.1f, 0.24f, 0.93f).withIndex()) {
        val gx = w * fx
        house(gx, rise.at(gx) + h * 0.02f, h * (0.075f - i * 0.008f), c(0x3A2A26), c(0x2E2426), 10 + i, snowRoof = c(0xDCE4FA), window = c(0xFFB85C), smoke = c(0x9AA8CE))
    }
    // The path trodden to the church door, lanterns standing along it.
    val door = cx + h * 0.42f * 0.3f
    val trodden = pen()
    trodden.shader = LinearGradient(0f, cg, 0f, h, Tone.alpha(c(0x6A7CB4), 0.55f), Tone.alpha(c(0x7A8CC0), 0.8f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(door - h * 0.01f, cg)
    path.cubicTo(door - h * 0.04f, h * 0.84f, w * 0.44f, h * 0.9f, w * 0.34f, h * 1.01f)
    path.lineTo(w * 0.5f, h * 1.01f)
    path.cubicTo(w * 0.56f, h * 0.9f, door + h * 0.03f, h * 0.84f, door + h * 0.01f, cg)
    path.close()
    canvas.drawPath(path, trodden)
    val lamps = listOf(0.12f to 0.018f, 0.3f to 0.024f, 0.55f to 0.032f, 0.85f to 0.042f)
    for ((t, s) in lamps) {
        val ly = cg + (h - cg) * t
        val lx = door + (w * 0.42f - door) * t + h * 0.05f * (1f - t) + (if (t > 0.5f) h * 0.08f else h * 0.03f)
        canvas.drawLine(lx, ly, lx, ly - h * s * 2.4f, stroke(c(0x1A1A24), max(0.2f, h * s * 0.12f)))
        glow(lx, ly - h * s * 2.4f, h * s * 5f, c(0xFFB860), 0.55f)
        canvas.drawRoundRect(lx - h * s * 0.3f, ly - h * s * 2.9f, lx + h * s * 0.3f, ly - h * s * 2.2f, h * s * 0.1f, h * s * 0.1f, pen(c(0xFFE0A0)))
        glow(lx, ly, h * s * 4f, c(0xFFB860), 0.3f, squash = 0.35f)
    }
    // Spruces in the foreground, deep in snow.
    val spruces = tones(0x060C1A, 0x0E1A30, 0x1C2E4C, 0x33496C, 0x566C90)
    conifer(w * 0.05f, h * 1.03f, h * 0.55f, h * 0.22f, spruces, 20, snow = c(0xE8EEFF), snowShade = c(0x7F93C8), snowLoad = 0.6f)
    conifer(w * 0.16f, h * 0.98f, h * 0.3f, h * 0.12f, spruces, 21, snow = c(0xE8EEFF), snowShade = c(0x7F93C8), snowLoad = 0.6f)
    conifer(w * 0.97f, h * 1.02f, h * 0.42f, h * 0.17f, spruces, 22, snow = c(0xE8EEFF), snowShade = c(0x7F93C8), snowLoad = 0.6f)
    sparkles(h * 0.72f, h, (w * h / 90f).roundToInt(), c(0xDDE6FF), 23, 0.24f)
    if (!live) snowfall((w * h / 130f).roundToInt(), 24, size = 0.75f)
    done(bloom = 0.75f, vignette = 0.3f, threshold = 0.55f)
}

/**
 * Крещенские морозы: the Epiphany cross cut in the river's ice, steam rising off the black water
 * in it; birches white with rime on the near bank, a church on the far one, the low sun ringed by
 * a halo and two mock suns.
 */
internal fun Painting.epiphany(art: WeekArt, live: Boolean) {
    sky(0f to 0x3A68AE, 0.4f to 0x7FA8DA, 0.78f to 0xD2E0F0, 1f to 0xF4DCC6, to = h * 0.58f)
    sun(lightX, lightY, h * 0.03f, c(0xFFE8C8), power = 0.95f, streak = 0.3f)
    sunDogs(lightX, lightY, h * 0.17f, 1f)
    val far = ridge(h * 0.56f, h * 0.06f, h * 0.7f, 31, lift = h * 0.04f, liftX = w * 0.3f, liftWidth = w * 0.2f)
    land(far, h, c(0xC0C8E0), c(0xD8DCEC), texture = 0.05f, rim = c(0xFFF1DC), rimStrength = 0.45f, shading = 0.25f)
    church(w * 0.3f, far.at(w * 0.3f) + h * 0.012f, h * 0.075f, c(0xF6F4F0), c(0x3A6AC8))
    val forestLine = treeLine(ridge(h * 0.6f, h * 0.012f, h * 0.5f, 32), h * 0.03f..h * 0.06f, h * 0.02f, conifers = 0.7f, seed = 33)
    forest(forestLine, h * 0.62f, rimed(tones(0x2A3E60, 0x42587E, 0x6478A0, 0xA0B0CE, 0xE8ECF6), 0.45f), 34, leaf = h * 0.01f, depth = h * 0.03f, rim = 0.45f)
    haze(h * 0.62f, h * 0.52f, c(0xF0E4E4), 0.55f)
    // The river under ice and snow: swept grey-blue in streaks, drifted white between.
    val iceTop = h * 0.62f
    val iceBottom = h * 0.8f
    val g = Ground(w, h, h * 0.6f)
    field(RectF(0f, iceTop, w, iceBottom), kx * 0.6f) { x, y ->
        g.at(x, y)
        val depth = ((y - iceTop) / (iceBottom - iceTop)).coerceIn(0f, 1f)
        val swept = Noise.fbm(g.across * 0.25f, g.away * 0.9f, 35, 4)
        val glaze = Tone.smooth(0.5f, 0.62f, swept)
        val snowy = Tone.mix(c(0xE8EEF8), c(0xFFFFFF), Noise.value(g.across * 2f, g.away * 3f, 36))
        Tone.mix(Tone.mix(c(0xD8E0EE), snowy, depth), Tone.mix(c(0x8EA4C4), c(0xB4C4DC), depth), glaze * 0.85f)
    }
    // The Jordan: a cross cut in the ice, black water in it breathing steam, an ice cross at its head.
    jordan(w * 0.56f, h * 0.72f, h * 0.2f)
    // The near bank.
    val bank = ridge(h * 0.8f, h * 0.025f, w * 0.6f, 38)
    snowfield(bank, h * 0.7f, c(0xFFF8F0), c(0xA2B6E0), c(0xE0E4F0), 39, relief = 0.8f)
    // A path trodden down to the cross.
    val path1 = pen(Tone.alpha(c(0x8EA2CC), 0.5f))
    path.reset()
    path.moveTo(w * 0.3f, h * 1.01f)
    path.cubicTo(w * 0.36f, h * 0.9f, w * 0.48f, h * 0.84f, w * 0.52f, h * 0.76f)
    path.lineTo(w * 0.58f, h * 0.76f)
    path.cubicTo(w * 0.52f, h * 0.86f, w * 0.46f, h * 0.92f, w * 0.44f, h * 1.01f)
    path.close()
    canvas.drawPath(path, path1)
    // Birches white with rime, every twig furred with it.
    for ((i, t) in listOf(0.06f to 0.9f, 0.15f to 0.75f, 0.9f to 0.85f).withIndex()) {
        val (fx, fh) = t
        val gy = bank.at(w * fx) + h * (0.08f + i * 0.03f)
        castShadow(w * fx, gy, h * 0.3f, h * 0.02f, c(0x8FA3D0), 0.3f)
        birch(w * fx, gy, h * fh, h * 0.024f, 40 + i, lean = if (fx < 0.5f) 0.03f else -0.03f, bark = c(0xFBF8F0), barkShade = c(0xA4B0C8), twig = c(0xD6DEEC), weeping = 0.65f)
    }
    sparkles(h * 0.62f, h, (w * h / 45f).roundToInt(), c(0xFFFFFF), 44, 0.26f)
    if (!live) sparkles(h * 0.1f, h * 0.6f, (w * h / 260f).roundToInt(), c(0xFFF6E0), 45, 0.2f)
    done(bloom = 0.55f, vignette = 0.16f)
}

/** Where the window of the log house stands in a [w] × [h] picture. */
private fun izbaWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.4f, h * 0.5f)
    val cx = w * 0.64f
    return RectF(cx - ww / 2f, h * 0.1f, cx + ww / 2f, h * 0.56f)
}

/**
 * Вечер в избе: a winter evening in a log house by candlelight — a glass of tea in its holder, jam,
 * a red cloth on the table; the cat sits in the frosted window, looking out at the moonlit village.
 */
internal fun Painting.izba(art: WeekArt, live: Boolean) {
    logWall(RectF(0f, 0f, w, h), c(0x7A4E2C), h * 0.068f, 51)
    val win = izbaWindow(w, h)
    val frame = h * 0.02f
    // The carved surround: a board frame with a pointed pediment, painted white.
    val carved = pen()
    carved.shader = LinearGradient(0f, win.top - frame * 5f, 0f, win.bottom, c(0xF2ECE0), c(0xB8AE9E), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(win.left - frame * 2.6f, win.top - frame * 1.4f)
    path.lineTo(win.centerX(), win.top - frame * 5.5f)
    path.lineTo(win.right + frame * 2.6f, win.top - frame * 1.4f)
    path.close()
    canvas.drawPath(path, carved)
    canvas.drawRect(win.left - frame * 2.2f, win.top - frame * 1.6f, win.right + frame * 2.2f, win.bottom + frame * 3.4f, carved)
    val cut = pen(Tone.alpha(c(0x6A4A30), 0.7f))
    for (k in 0..6) {
        val x = win.left - frame * 1.2f + (win.width() + frame * 2.4f) * k / 6f
        canvas.drawCircle(x, win.bottom + frame * 2.6f, frame * 0.45f, cut)
    }
    canvas.drawCircle(win.centerX(), win.top - frame * 3.4f, frame * 0.9f, cut)
    window(win, c(0xE8E0D2), frame, 2, 52) {
        sky(0f to 0x0A1436, 0.55f to 0x1C2E64, 1f to 0x3A4E8A, to = h * 0.72f)
        stars(h * 0.6f, (w * h / 30f).roundToInt(), 53, brightness = 0.8f)
        moon(w * 0.72f, h * 0.2f, h * 0.07f, 0.95f, c(0xF4F1E6), c(0xBFD0FF), 0.9f)
        val hill = ridge(h * 0.66f, h * 0.06f, w * 0.8f, 54)
        snowfield(hill, h * 0.6f, c(0xC4D2F2), c(0x4C5E98), c(0x6E82B8), 55, relief = 0.8f)
        for ((i, fx) in listOf(0.2f, 0.52f).withIndex()) {
            house(w * fx, hill.at(w * fx) + h * 0.03f, h * (0.2f - i * 0.04f), c(0x3A2A26), c(0x2E2426), 56 + i, snowRoof = c(0xDCE4FA), window = c(0xFFB85C), smoke = c(0x9AA8CE))
        }
        conifer(w * 0.9f, h * 1.02f, h * 0.55f, h * 0.24f, tones(0x060C1A, 0x0E1A30, 0x1C2E4C, 0x33496C, 0x566C90), 58, snow = c(0xE0E8FF), snowShade = c(0x7F93C8))
        if (!live) snowfall((w * h / 25f).roundToInt(), 59, size = 0.6f)
    }
    frostOnGlass(win, 0.75f, 60)
    // The cat on the sill, dark against the moonlight.
    cat(win.left + win.width() * 0.3f, win.bottom + frame * 1.1f, h * 0.17f, c(0x2E2A2C))
    // The candle's warmth on the logs.
    val cx = w * 0.36f
    glow(cx, h * 0.72f, h * 0.9f, c(0xFFA24A), 0.35f)
    tabletop(h * 0.8f, c(0x5A3A22), 61, cloth = c(0xC8342C))
    candle(cx, h * 0.93f, h * 0.2f)
    teaGlass(w * 0.52f, h * 0.97f, h * 0.2f)
    jar(w * 0.2f, h * 0.96f, h * 0.15f, c(0x8A1428), c(0xF0E4C8))
    done(bloom = 0.6f, vignette = 0.38f, threshold = 0.6f)
}

/**
 * Лыжня: a ski track running through a sunny winter forest, the long blue shadows of the trees
 * lying across it; a pair of skis and their poles stuck in a drift in front.
 */
internal fun Painting.skiTrack(art: WeekArt, live: Boolean) {
    sky(0f to 0x2F66B8, 0.4f to 0x6CA2DE, 0.8f to 0xC0D8F0, 1f to 0xF4E6D4, to = h * 0.6f)
    sun(lightX, lightY, h * 0.032f, c(0xFFEAC8), power = 0.95f, streak = 0.3f)
    val forestLine = treeLine(ridge(h * 0.56f, h * 0.02f, h * 0.5f, 61), h * 0.05f..h * 0.12f, h * 0.026f, conifers = 0.85f, seed = 62)
    forest(forestLine, h * 0.6f, tones(0x223656, 0x3A5278, 0x5E769C, 0x9AAACA, 0xE8ECF6), 63, leaf = h * 0.012f, depth = h * 0.05f, rim = 0.45f)
    haze(h * 0.6f, h * 0.48f, c(0xEDE3E6), 0.5f)
    val field = flat(h * 0.585f, h * 0.008f, seed = 64)
    snowfield(field, h * 0.56f, c(0xFFF8EE), c(0xA6BAE4), c(0xE6E2EE), 65, relief = 0.9f)
    rays(lightX, lightY, h * 1.1f, PIF * 0.35f, 1.2f, 9, c(0xFFF2D8), 0.16f, 66)
    // The track: two grooves side by side, curving out of the forest to the viewer's feet.
    val ax = w * 0.58f
    val ay = h * 0.6f
    val bx = w * 0.3f
    val by = h * 1.04f
    val steps = 60
    val left = Path()
    val right = Path()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val u = 1f - t
        // A cubic from the forest (a) to the foreground (b), swinging right on the way.
        val x = u * u * u * ax + 3f * u * u * t * (w * 0.72f) + 3f * u * t * t * (w * 0.24f) + t * t * t * bx
        val y = u * u * u * ay + 3f * u * u * t * (h * 0.68f) + 3f * u * t * t * (h * 0.82f) + t * t * t * by
        val gap = h * (0.004f + 0.045f * ((y - ay) / (by - ay)).coerceIn(0f, 1f))
        if (i == 0) {
            left.moveTo(x - gap, y)
            right.moveTo(x + gap, y)
        } else {
            left.lineTo(x - gap, y)
            right.lineTo(x + gap, y)
        }
    }
    for (groove in listOf(left, right)) {
        canvas.drawPath(groove, stroke(Tone.alpha(c(0x7F96CC), 0.75f), h * 0.008f))
        canvas.withTranslation(h * 0.002f, -h * 0.002f) {
            canvas.drawPath(groove, stroke(Tone.alpha(c(0xFFFFFF), 0.6f), h * 0.003f))
        }
    }
    // Spruces and a birch, and their shadows across the track.
    val spruces = tones(0x0C1D1C, 0x17332E, 0x28503F, 0x40705A, 0x6A9274)
    val trees = listOf(Triple(0.08f, 0.98f, 0.6f), Triple(0.2f, 0.8f, 0.36f), Triple(0.78f, 0.86f, 0.42f), Triple(0.92f, 1.02f, 0.66f))
    for ((i, t) in trees.withIndex()) {
        val (fx, fy, fh) = t
        castShadow(w * fx, h * fy, h * fh * 1.4f, h * fh * 0.22f, c(0x7F9BD4), 0.45f)
        conifer(w * fx, h * fy, h * fh, h * fh * 0.4f, spruces, 70 + i, snow = c(0xFFFFFF), snowShade = c(0x9DB2DE), snowLoad = 0.6f)
    }
    birch(w * 0.68f, h * 0.74f, h * 0.5f, h * 0.018f, 75, lean = -0.02f, bark = c(0xFBF8F0), barkShade = c(0x9EA9BC), twig = c(0x6E4E4A), weeping = 0.4f)
    skis(w * 0.8f, h * 0.97f, h * 0.36f, c(0xD83A30))
    sparkles(h * 0.6f, h, (w * h / 55f).roundToInt(), c(0xFFFFFF), 76, 0.26f)
    if (!live) sparkles(h * 0.15f, h * 0.56f, (w * h / 420f).roundToInt(), c(0xFFF6E0), 77, 0.2f)
    done(bloom = 0.5f, vignette = 0.16f)
}

/**
 * Метель: a blizzard over a village street at dusk — the houses half lost in the driven snow,
 * their windows the only warmth, a lamp burning with the snow streaming through its light.
 */
internal fun Painting.blizzard(art: WeekArt, live: Boolean) {
    sky(0f to 0x4A5270, 0.5f to 0x7A8098, 0.85f to 0xA8ACBA, 1f to 0xC4C4CA, to = h * 0.7f)
    clouds(0f, h * 0.55f, 0.8f, h * 0.2f, 81, c(0xB8BCCA), c(0x5A6078), opacity = 0.85f, stretch = 3f)
    val street = flat(h * 0.66f, h * 0.008f, seed = 82)
    val far = treeLine(flat(h * 0.63f, h * 0.004f, seed = 83), h * 0.04f..h * 0.08f, h * 0.025f, conifers = 0.8f, seed = 84)
    forest(far, h * 0.66f, tones(0x5E667E, 0x6E7690, 0x8088A0, 0x9AA0B4, 0xC8CCD8), 85, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.2f)
    haze(h * 0.66f, h * 0.5f, c(0xBCC0CA), 0.7f)
    for ((i, fx) in listOf(0.34f, 0.5f, 0.14f).withIndex()) {
        val size = h * (0.1f + i * 0.035f)
        house(w * fx, street.at(w * fx) + h * (0.02f + i * 0.04f), size, c(0x4A3A34), c(0x3A3236), 86 + i, snowRoof = c(0xE4E8F0), window = c(0xFFB85C), smoke = c(0xB8BCC8))
        haze(h * 0.8f, h * 0.55f, c(0xC4C8D2), 0.25f)
    }
    val field = flat(h * 0.74f, h * 0.02f, seed = 89)
    snowfield(field, h * 0.62f, c(0xE8ECF4), c(0x8A94B0), c(0xB4BACA), 90, relief = 1.3f)
    fence(w * 0.02f, w * 0.6f, h * 0.84f, h * 0.06f, c(0x3A3438), 91, snow = c(0xECF0F8))
    // The lamp, and the cone of its light full of snow.
    val lx = w * 0.74f
    val lb = h * 0.95f
    val lh = h * 0.6f
    val headX = lx + lh * 0.12f
    val headY = lb - lh + lh * 0.02f
    val cone = Path().apply {
        moveTo(headX, headY)
        lineTo(headX - h * 0.26f, lb)
        lineTo(headX + h * 0.26f, lb)
        close()
    }
    val light = pen()
    light.shader = LinearGradient(0f, headY, 0f, lb, Tone.alpha(c(0xFFD890), 0.45f), Tone.alpha(c(0xFFD890), 0.08f), Shader.TileMode.CLAMP)
    light.maskFilter = BlurMaskFilter(h * 0.03f, BlurMaskFilter.Blur.NORMAL)
    canvas.drawPath(cone, light)
    glow(headX, lb, h * 0.3f, c(0xFFD08A), 0.4f, squash = 0.25f)
    streetLamp(lx, lb, lh, 1f)
    val r = Random(92)
    canvas.withClip(cone) {
        repeat((h * 1.2f).roundToInt()) {
            val y = headY + (lb - headY) * r.nextFloat()
            val x = headX + r.range(-0.3f, 0.3f) * h
            val len = h * r.range(0.02f, 0.06f)
            drawLine(x, y, x - len, y + len * 0.35f, stroke(Tone.alpha(c(0xFFF4DC), r.range(0.3f, 0.8f)), r.range(0.2f, 0.5f)))
        }
    }
    fog(h * 0.3f, h * 0.95f, c(0xDCE0E8), 0.55f, h * 0.06f, 93, peak = 0.7f)
    if (!live) {
        driven((w * h / 20f).roundToInt(), 94, slant = 0.32f, strength = 0.9f)
        snowfall((w * h / 60f).roundToInt(), 95, size = 0.6f)
    } else {
        driven((w * h / 90f).roundToInt(), 94, slant = 0.32f, strength = 0.5f)
    }
    done(bloom = 0.5f, vignette = 0.3f, threshold = 0.6f)
}

/**
 * Масленица: winter seen off at sunset — the straw lady burning on her pole in the middle of a
 * snowy field, sparks going up, a pole hung with ribbons, the village and its church on the rise.
 */
internal fun Painting.maslenitsa(art: WeekArt, live: Boolean) {
    sky(0f to 0x3A4A8A, 0.35f to 0xA86A8E, 0.7f to 0xF4A06C, 1f to 0xFFD6A0, to = h * 0.62f)
    sun(lightX, lightY, h * 0.042f, c(0xFFC890), power = 0.9f, streak = 0.45f)
    clouds(h * 0.04f, h * 0.44f, 0.36f, h * 0.1f, 101, c(0xFFC8A0), c(0x6E5A8A), opacity = 0.85f, stretch = 3.4f, silver = 0.9f)
    val far = ridge(h * 0.6f, h * 0.035f, h * 0.7f, 102, lift = h * 0.03f, liftX = w * 0.2f, liftWidth = w * 0.2f)
    land(far, h, c(0x6A5A8A), c(0x8A7AA0), texture = 0.06f, rim = c(0xFFD8B0), rimStrength = 0.5f, shading = 0.2f)
    val village = treeLine(far.shifted(h * 0.01f), h * 0.02f..h * 0.05f, h * 0.02f, conifers = 0.4f, seed = 103)
    forest(village, h * 0.64f, tones(0x3A2E4E, 0x4E3E62, 0x6A5478, 0x9A7A98, 0xFFC8A0), 104, leaf = h * 0.009f, depth = h * 0.03f, rim = 0.5f)
    church(w * 0.2f, far.at(w * 0.2f) + h * 0.015f, h * 0.07f, c(0xF4E0D8), c(0xE8B04A))
    for ((i, fx) in listOf(0.32f, 0.4f, 0.9f).withIndex()) {
        house(w * fx, far.at(w * fx) + h * 0.03f, h * 0.05f, c(0x4A3440), c(0x3A2A36), 105 + i, snowRoof = c(0xFFE0D8), window = c(0xFFB85C), smoke = c(0xC8B0C0))
    }
    haze(h * 0.64f, h * 0.54f, c(0xFFD0B0), 0.4f)
    val field = flat(h * 0.66f, h * 0.01f, seed = 108)
    snowfield(field, h * 0.62f, c(0xFFE8DA), c(0x9A86B4), c(0xE8C8C0), 109, relief = 0.9f)
    // The pole to climb for the prize: a wheel at its top, ribbons streaming on the wind, boots hung up.
    val px = w * 0.17f
    val pb = h * 0.9f
    val ph = h * 0.72f
    val pole = pen()
    pole.shader = LinearGradient(px - h * 0.01f, 0f, px + h * 0.01f, 0f, c(0xC89A6A), c(0x5A3A22), Shader.TileMode.CLAMP)
    canvas.drawRect(px - h * 0.009f, pb - ph, px + h * 0.009f, pb, pole)
    canvas.drawOval(px - h * 0.06f, pb - ph - h * 0.012f, px + h * 0.06f, pb - ph + h * 0.012f, stroke(c(0x4A3222), max(0.3f, h * 0.008f)))
    val ribbons = intArrayOf(c(0xE0302A), c(0xF2C230), c(0x2A7AD8), c(0x3AA84A), c(0xF2F2F2), c(0xD84A9A))
    for ((k, color) in ribbons.withIndex()) {
        val sx = px - h * 0.06f + h * 0.12f * k / (ribbons.size - 1f)
        val sy = pb - ph + h * 0.006f
        val len = h * (0.16f + (k % 3) * 0.05f)
        val p = stroke(color, max(0.25f, h * 0.01f))
        path.reset()
        path.moveTo(sx, sy)
        path.cubicTo(sx + len * 0.3f, sy + h * 0.04f, sx + len * 0.6f, sy - h * 0.02f, sx + len, sy + h * (0.03f + (k % 2) * 0.03f))
        canvas.drawPath(path, p)
    }
    for (side in listOf(-1f, 1f)) {
        val bx = px + side * h * 0.035f
        val by = pb - ph + h * 0.1f
        canvas.drawLine(bx, pb - ph + h * 0.01f, bx, by - h * 0.04f, stroke(c(0x2A2A2E), max(0.12f, h * 0.002f)))
        canvas.drawRoundRect(bx - h * 0.012f, by - h * 0.05f, bx + h * 0.012f, by, h * 0.006f, h * 0.006f, pen(c(0x2A1E18)))
        val toe = bx + side * h * 0.03f
        canvas.drawRoundRect(min(bx - h * 0.012f, toe), by - h * 0.014f, max(bx + h * 0.012f, toe), by, h * 0.006f, h * 0.006f, pen(c(0x2A1E18)))
    }
    // The fire's light on the snow, then the straw lady burning.
    val ex = w * 0.5f
    val eb = h * 0.86f
    glow(ex, eb, h * 0.8f, c(0xFF9A40), 0.45f, squash = 0.35f)
    effigy(ex, eb, h * 0.4f, 1f, 110)
    if (!live) fireflies(h * 0.1f, h * 0.55f, (w * h / 900f).roundToInt(), c(0xFFB050), 111, size = 0.4f)
    sparkles(h * 0.7f, h, (w * h / 120f).roundToInt(), c(0xFFE8D8), 112, 0.24f)
    done(bloom = 0.7f, vignette = 0.22f, threshold = 0.62f)
}

/**
 * Капель: the March sun on a carved eave — icicles hanging in a row, running and dripping, the
 * snow on the roof going soft; a starling house on the birch, the village roofs thawing below.
 */
internal fun Painting.drops(art: WeekArt, live: Boolean) {
    sky(0f to 0x2A68C6, 0.5f to 0x68A2E8, 0.85f to 0xB8D6F4, 1f to 0xEAF2F8)
    sun(lightX, lightY, h * 0.045f, c(0xFFF6E2), power = 1f, streak = 0.3f)
    clouds(h * 0.3f, h * 0.75f, 0.3f, h * 0.12f, 121, c(0xFFFFFF), c(0xB0C4DE), opacity = 0.85f)
    // Far below: the village's roofs, half thawed, and bare trees.
    val far = ridge(h * 0.86f, h * 0.02f, w * 0.6f, 122)
    val trees = treeLine(far, h * 0.05f..h * 0.1f, h * 0.03f, conifers = 0.3f, seed = 123)
    forest(trees, h * 0.9f, tones(0x5A4E5E, 0x6E6474, 0x8A8090, 0xB4AEBA, 0xE8E4E8), 124, leaf = h * 0.01f, depth = h * 0.04f, rim = 0.3f)
    for ((i, fx) in listOf(0.2f, 0.42f, 0.6f).withIndex()) {
        house(w * fx, far.at(w * fx) + h * 0.04f, h * 0.12f, c(0x6A4E3E), c(0x5A6A4A), 125 + i, snowRoof = c(0xF0F4FA), window = 0, smoke = 0)
    }
    thaw(flat(h * 0.9f, h * 0.005f, seed = 128), h * 0.86f, c(0xF6F8FA), c(0xB4C4DC), c(0x7A6A5A), c(0x4A3E36), c(0x5E96DA), c(0xC8DCF0), c(0xE0E4EA), 129, cover = 0.7f)
    // The birch with its starling house.
    val bx = w * 0.86f
    birch(bx, h * 1.05f, h * 1.1f, h * 0.035f, 130, lean = -0.02f, bark = c(0xFBF8F0), barkShade = c(0x9EA9BC), twig = c(0x7A4A3E), weeping = 0.35f)
    val hy = h * 0.46f
    val hs = h * 0.1f
    canvas.drawRect(bx - hs * 0.4f, hy - hs * 0.3f, bx + hs * 0.4f, hy + hs * 0.7f, pen(c(0x8A6444)))
    path.reset()
    path.moveTo(bx - hs * 0.55f, hy - hs * 0.25f)
    path.lineTo(bx, hy - hs * 0.65f)
    path.lineTo(bx + hs * 0.55f, hy - hs * 0.25f)
    path.close()
    canvas.drawPath(path, pen(c(0x5A4030)))
    canvas.drawCircle(bx, hy + hs * 0.12f, hs * 0.13f, pen(c(0x1E1612)))
    // The eave across the top: the snow on the roof hanging over, the carved board, icicles under it.
    val eave = h * 0.2f
    val board = pen()
    board.shader = LinearGradient(0f, h * 0.09f, 0f, eave, c(0xA87A4E), c(0x6A4A2E), Shader.TileMode.CLAMP)
    val scallop = h * 0.055f
    path.reset()
    path.moveTo(-1f, h * 0.08f)
    path.lineTo(w + 1f, h * 0.08f)
    path.lineTo(w + 1f, eave - scallop * 0.5f)
    var sx = w + 1f
    while (sx > -scallop) {
        path.quadTo(sx - scallop / 2f, eave + scallop * 0.35f, sx - scallop, eave - scallop * 0.5f)
        sx -= scallop
    }
    path.close()
    canvas.drawPath(path, board)
    val carving = stroke(Tone.alpha(c(0x3A2414), 0.55f), max(0.2f, h * 0.004f))
    canvas.drawLine(-1f, h * 0.115f, w + 1f, h * 0.115f, carving)
    sx = scallop / 2f
    while (sx < w + scallop) {
        canvas.drawCircle(sx, h * 0.145f, h * 0.008f, pen(c(0x2A1A10)))
        path.reset()
        path.moveTo(sx - scallop * 0.3f, h * 0.125f)
        path.lineTo(sx, h * 0.165f)
        path.lineTo(sx + scallop * 0.3f, h * 0.125f)
        canvas.drawPath(path, carving)
        sx += scallop
    }
    val snow = pen()
    snow.shader = LinearGradient(0f, 0f, 0f, h * 0.11f, c(0xFFFFFF), c(0xC4D2EA), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(-1f, -1f)
    path.lineTo(w + 1f, -1f)
    path.lineTo(w + 1f, h * 0.075f)
    var x = w + 1f
    val r = Random(131)
    while (x > -2f) {
        val step = h * r.range(0.08f, 0.16f)
        path.quadTo(x - step / 2f, h * r.range(0.1f, 0.125f), x - step, h * r.range(0.075f, 0.09f))
        x -= step
    }
    path.close()
    canvas.drawPath(path, snow)
    canvas.drawPath(path, stroke(Tone.alpha(c(0x8A9ABE), 0.35f), h * 0.004f))
    icicles(-2f, w + 2f, eave, (w / 4.5f).roundToInt(), h * 0.4f, 132)
    // Drops falling, and the glints of the sun in the ice.
    val d = Random(133)
    repeat((w / 14f).roundToInt()) {
        val dx = d.nextFloat() * w
        val dy = eave + h * d.range(0.2f, 0.7f)
        canvas.drawLine(dx, dy - h * 0.03f, dx, dy, stroke(Tone.alpha(c(0xFFFFFF), 0.35f), 0.3f))
        canvas.drawCircle(dx, dy, h * 0.005f, pen(0xDDEAF4FF.toInt()))
    }
    sparkles(eave, eave + h * 0.3f, (w / 3f).roundToInt(), c(0xFFFFFF), 134, 0.3f)
    done(bloom = 0.55f, vignette = 0.1f, threshold = 0.7f)
}

/**
 * Первый снег: the first snow on a village street — white on the roofs and the fence, the
 * ground showing through in patches, a rowan still red and capped with snow, a birch keeping its
 * last yellow leaves; big flakes coming down.
 */
internal fun Painting.firstSnow(art: WeekArt, live: Boolean) {
    sky(0f to 0x8A96AE, 0.5f to 0xB8C0CC, 0.85f to 0xDCDCDA, 1f to 0xEEE6DC, to = h * 0.62f)
    val far = treeLine(flat(h * 0.58f, h * 0.006f, seed = 141), h * 0.03f..h * 0.07f, h * 0.022f, conifers = 0.5f, seed = 142)
    forest(far, h * 0.62f, tones(0x5A5E68, 0x6E727C, 0x8A8E98, 0xB4B8C0, 0xE8EAEE), 143, leaf = h * 0.009f, depth = h * 0.03f, rim = 0.3f)
    church(w * 0.62f, h * 0.59f, h * 0.06f, c(0xE8E6E2), c(0x5A7A5A))
    haze(h * 0.62f, h * 0.5f, c(0xDCDAD6), 0.45f)
    val street = flat(h * 0.64f, h * 0.006f, seed = 144)
    thaw(street, h * 0.6f, c(0xF6F8FA), c(0xB0B8CC), c(0x5A4E44), c(0x3A322C), c(0x7A8494), c(0xA8B0BC), c(0xD8D8DA), 145, cover = 0.72f, pools = 0.12f)
    // The house on the left, its carved window frames white, the windows lit on the grey morning.
    val hx = w * 0.18f
    val hg = h * 0.8f
    val hw = h * 0.5f
    house(hx, hg, hw, c(0x5A3E2E), c(0x4A5A4A), 146, snowRoof = c(0xF4F6FA), window = c(0xFFD08A), smoke = c(0xD0D4DC))
    for (k in listOf(-1, 1)) {
        val wx = hx + k * hw * 0.22f
        val wy = hg - hw * 0.55f + hw * 0.55f * 0.3f
        val ww = hw * 0.14f
        val trim = stroke(c(0xF0ECE4), max(0.3f, hw * 0.02f), round = false)
        canvas.drawRect(wx - ww * 0.62f, wy - ww * 0.1f, wx + ww * 0.62f, wy + ww * 1.3f, trim)
        path.reset()
        path.moveTo(wx - ww * 0.8f, wy - ww * 0.15f)
        path.lineTo(wx, wy - ww * 0.55f)
        path.lineTo(wx + ww * 0.8f, wy - ww * 0.15f)
        path.close()
        canvas.drawPath(path, pen(c(0xF0ECE4)))
    }
    fence(w * 0.34f, w * 1.02f, h * 0.84f, h * 0.07f, c(0x4A3E36), 147, snow = c(0xF4F6FA))
    // The birch with its last leaves, and the rowan, red under the snow.
    birch(w * 0.9f, h * 0.9f, h * 0.8f, h * 0.024f, 148, lean = -0.03f, crown = SeasonClock.of(42).birchLeaves(), leaves = 0.2f, leafSize = h * 0.007f, twig = c(0x4A3A34), weeping = 0.5f)
    val rx = w * 0.58f
    val rg = h * 0.9f
    broadleaf(rx, rg, h * 0.44f, h * 0.36f, h * 0.26f, c(0x3A2E28), tones(0x5A2A1A, 0x8A3A22, 0xB8542A, 0xD8703A, 0xF09A5A), 149, leaf = h * 0.01f, lumps = 12, leaves = 0.2f)
    val r = Random(150)
    repeat(9) {
        val bx = rx + r.range(-1f, 1f) * h * 0.14f
        val by = rg - h * 0.44f + h * r.range(0.04f, 0.2f)
        berries(bx, by, h * 0.022f, 8, c(0xD8302A), 151 + it)
        canvas.drawOval(bx - h * 0.022f, by - h * 0.03f, bx + h * 0.022f, by - h * 0.014f, pen(c(0xF8FAFE)))
    }
    if (!live) snowfall((w * h / 70f).roundToInt(), 160, size = 1.1f)
    done(bloom = 0.3f, vignette = 0.16f)
}

/**
 * Снегири: two bullfinches on a rowan branch under snow — rose-red breasts, the berries red
 * beside them, the winter wood soft and pale behind.
 */
internal fun Painting.bullfinches(art: WeekArt, live: Boolean) {
    sky(0f to 0xA8B8D0, 0.5f to 0xC8D4E2, 1f to 0xF0F2F6)
    // The wood behind, far out of focus: pale trunks and soft blue shadows.
    val r = Random(171)
    repeat(9) {
        val x = r.nextFloat() * w
        val p = pen()
        p.shader = LinearGradient(x - h * 0.03f, 0f, x + h * 0.03f, 0f, Tone.alpha(c(0x8A96AE), 0f), Tone.alpha(c(0x8A96AE), 0.35f), Shader.TileMode.MIRROR)
        p.maskFilter = BlurMaskFilter(h * 0.03f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRect(x - h * r.range(0.02f, 0.05f), 0f, x + h * r.range(0.02f, 0.05f), h, p)
    }
    val far = treeLine(flat(h * 0.8f, h * 0.02f, seed = 172), h * 0.2f..h * 0.4f, h * 0.08f, conifers = 0.8f, seed = 173)
    forest(far, h, veiled(tones(0x6A7A98, 0x7E8EAA, 0x96A4BE, 0xB8C4D6, 0xE0E6EE), c(0xD4DCE8), 0.5f), 174, leaf = h * 0.02f, depth = h * 0.2f, rim = 0.2f)
    fog(h * 0.4f, h, c(0xE8ECF4), 0.8f, h * 0.1f, 175)
    repeat(14) {
        val x = r.nextFloat() * w
        val y = r.nextFloat() * h
        glow(x, y, h * r.range(0.03f, 0.08f), c(0xFFFFFF), r.range(0.2f, 0.5f))
    }
    // The branch, loaded with snow along its top.
    val bark = c(0x4A3A30)
    branch(-w * 0.05f, h * 0.7f, w * 1.05f, h * 0.52f, h * 0.03f, bark, 176, snow = true)
    branch(w * 1.05f, h * 0.9f, w * 0.55f, h * 0.8f, h * 0.02f, bark, 177, snow = true)
    fun branchY(x: Float) = h * 0.7f + (h * 0.52f - h * 0.7f) * ((x + w * 0.05f) / (w * 1.1f))
    // Clusters of berries hanging under it, capped with snow.
    for ((k, t) in listOf(0.14f, 0.46f, 0.6f, 0.86f).withIndex()) {
        val bx = w * t
        val by = branchY(bx) + h * 0.07f
        berries(bx, by, h * 0.05f, 10, c(0xD82E26), 178 + k)
        canvas.drawOval(bx - h * 0.03f, by - h * 0.06f, bx + h * 0.035f, by - h * 0.035f, pen(c(0xF8FAFF)))
    }
    bullfinch(w * 0.32f, branchY(w * 0.32f) - h * 0.13f * 0.36f - h * 0.012f, h * 0.13f, 1f)
    bullfinch(w * 0.72f, branchY(w * 0.72f) - h * 0.11f * 0.36f - h * 0.012f, h * 0.11f, -1f)
    if (!live) snowfall((w * h / 110f).roundToInt(), 185, size = 1f)
    done(bloom = 0.3f, vignette = 0.14f)
}

/**
 * Каток: an evening skating rink in the park — strings of lights over the ice, the lamps and the
 * lit windows of the town in it, skaters going round, spruces standing in the snow at its edge.
 */
internal fun Painting.rink(art: WeekArt, live: Boolean) {
    sky(0f to 0x0E1A44, 0.4f to 0x24356E, 0.8f to 0x4A5C94, 1f to 0x8A8CB0, to = h * 0.58f)
    stars(h * 0.4f, (w * h / 160f).roundToInt(), 191, brightness = 0.6f)
    facades(-w * 0.05f, w * 1.05f, h * 0.56f, h * 0.06f, tones(0x3A3E58, 0x44485E, 0x4E4A5E, 0x3E4660), 0.45f, 192)
    val trees = treeLine(flat(h * 0.6f, h * 0.006f, seed = 193), h * 0.06f..h * 0.12f, h * 0.03f, conifers = 0.6f, seed = 194)
    forest(trees, h * 0.64f, tones(0x0A1230, 0x142046, 0x22325E, 0x4A5C8E, 0xC0CCF0), 195, leaf = h * 0.011f, depth = h * 0.05f, rim = 0.35f)
    val field = flat(h * 0.62f, h * 0.004f, seed = 196)
    snowfield(field, h * 0.58f, c(0xC8D4F2), c(0x4E60A0), c(0x6E80B8), 197, relief = 0.7f)
    // The lights strung over the rink from lamp posts.
    val posts = listOf(0.06f, 0.36f, 0.66f, 0.96f)
    for (fx in posts) streetLamp(w * fx, h * 0.66f, h * 0.36f, 1f)
    val ys = FloatArray(posts.size) { h * 0.3f + h * 0.002f }
    garland(FloatArray(posts.size) { w * posts[it] + h * 0.36f * 0.06f }, ys, h * 0.02f, 40, h * 0.009f, 1f, 198)
    garland(floatArrayOf(w * 0.06f, w * 0.96f), floatArrayOf(h * 0.36f, h * 0.36f), h * 0.05f, 24, h * 0.008f, 0.9f, 199)
    // The ice: the lights and the trees in it, scored by the skates.
    val rink = Path().apply { addOval(RectF(w * 0.02f, h * 0.64f, w * 0.98f, h * 1.12f), Path.Direction.CW) }
    water(h * 0.645f, h, c(0x9AB4E0), 0.25f, ripple = 0.08f, seed = 200, glint = c(0xFFE8C0), glintStrength = 0.3f, clip = rink)
    canvas.withClip(rink) {
        val r = Random(201)
        repeat((w / 3f).roundToInt()) {
            val cx = w * r.range(0.1f, 0.9f)
            val cy = h * r.range(0.7f, 1f)
            val rx = h * r.range(0.05f, 0.3f)
            drawArc(RectF(cx - rx, cy - rx * 0.2f, cx + rx, cy + rx * 0.2f), r.range(0f, 180f), r.range(20f, 90f), false, stroke(Tone.alpha(c(0xFFFFFF), r.range(0.08f, 0.22f)), 0.2f))
        }
    }
    canvas.drawOval(RectF(w * 0.02f, h * 0.64f, w * 0.98f, h * 1.12f), stroke(c(0xDCE4F8), h * 0.018f))
    val skaters = listOf(
        floatArrayOf(0.3f, 0.78f, 0.1f, 1f), floatArrayOf(0.5f, 0.72f, 0.075f, -1f), floatArrayOf(0.68f, 0.84f, 0.13f, 1f),
        floatArrayOf(0.18f, 0.9f, 0.16f, -1f), floatArrayOf(0.84f, 0.74f, 0.08f, -1f),
    )
    val coats = intArrayOf(c(0xB8302A), c(0x2A4A8A), c(0x3A6A4A), c(0x6A3A7A), c(0x2A2A3A))
    val scarves = intArrayOf(c(0xF2C230), c(0xE8E8F0), c(0xE0402E), c(0x5AB8FF), c(0xF28AB0))
    for ((i, s) in skaters.withIndex()) skater(w * s[0], h * s[1], h * s[2] * 1.4f, coats[i], scarves[i], s[3])
    val spruces = tones(0x060C1A, 0x0E1A30, 0x1C2E4C, 0x33496C, 0x566C90)
    conifer(w * 0.02f, h * 1.02f, h * 0.44f, h * 0.18f, spruces, 202, snow = c(0xE8EEFF), snowShade = c(0x7F93C8))
    conifer(w * 0.99f, h * 1.04f, h * 0.5f, h * 0.2f, spruces, 203, snow = c(0xE8EEFF), snowShade = c(0x7F93C8))
    if (!live) snowfall((w * h / 160f).roundToInt(), 204, size = 0.6f)
    done(bloom = 0.8f, vignette = 0.28f, threshold = 0.55f)
}

/** Where the frosted window stands in a [w] × [h] picture. */
private fun frostyWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.7f, h * 1.1f)
    return RectF(w / 2f - ww / 2f, h * 0.06f, w / 2f + ww / 2f, h * 0.7f)
}

/**
 * Морозное окно: frost ferns grown over the panes, the lit street outside through the clear middle;
 * lights strung along the frame, and on the sill a mug of cocoa, tangerines, a spruce twig with a
 * bauble — the week before the New Year.
 */
internal fun Painting.frostyWindow(art: WeekArt, live: Boolean) {
    wall(RectF(0f, 0f, w, h), c(0x4A3036), c(0x2A1C20), grain = 0.18f)
    val stripes = pen()
    stripes.shader = LinearGradient(0f, 0f, h * 0.08f, 0f, intArrayOf(0x00000000, 0x14FFE0C0, 0x00000000), null, Shader.TileMode.REPEAT)
    canvas.drawRect(0f, 0f, w, h, stripes)
    val win = frostyWindow(w, h)
    val frame = h * 0.022f
    window(win, c(0xEDE6DA), frame, 2, 211) {
        sky(0f to 0x0A1230, 0.6f to 0x1E2E5E, 1f to 0x3A4A7A, to = h * 0.7f)
        facades(-w * 0.05f, w * 1.05f, h * 0.72f, h * 0.16f, tones(0x3A3450, 0x4A3E54, 0x3E4460), 0.55f, 212)
        val street = flat(h * 0.74f, 0f)
        snowfield(street, h * 0.66f, c(0xC4D2F2), c(0x4C5E98), c(0x6E82B8), 213, relief = 0.6f)
        streetLamp(w * 0.72f, h * 0.95f, h * 0.55f, 1f)
        dressedTree(w * 0.28f, h * 0.98f, h * 0.5f, 214, lights = 1f)
        if (!live) snowfall((w * h / 18f).roundToInt(), 215, size = 0.7f)
    }
    frostOnGlass(win, 1f, 216)
    garland(floatArrayOf(win.left - frame, win.centerX(), win.right + frame), floatArrayOf(win.top - frame * 0.5f, win.top + frame * 0.6f, win.top - frame * 0.5f), h * 0.01f, 18, h * 0.011f, 1f, 217)
    // The sill: a broad white board, and what's on it.
    val sill = win.bottom + frame * 2.6f
    tabletop(sill, c(0xF0EAE0), 218)
    glow(w * 0.5f, sill, w * 0.6f, c(0xFFC080), 0.25f, squash = 0.3f)
    mug(w * 0.34f, h * 0.97f, h * 0.18f, c(0xC83A30), c(0x6A3A22), steam = true, seed = 219)
    for ((k, o) in listOf(floatArrayOf(0.56f, 0.95f, 0.1f), floatArrayOf(0.63f, 0.97f, 0.11f), floatArrayOf(0.595f, 0.9f, 0.095f)).withIndex()) {
        tangerine(w * o[0], h * o[1], h * o[2], leaf = k == 1)
    }
    // A spruce twig with a red bauble.
    val tx = w * 0.8f
    val ty = h * 0.93f
    val needles = stroke(c(0x1E4A2E), max(0.15f, h * 0.004f))
    canvas.drawLine(tx - h * 0.12f, ty + h * 0.02f, tx + h * 0.14f, ty - h * 0.03f, stroke(c(0x4A3222), h * 0.008f))
    val r = Random(220)
    repeat(60) {
        val t = r.nextFloat()
        val bx = tx - h * 0.12f + h * 0.26f * t
        val by = ty + h * 0.02f - h * 0.05f * t
        val a = r.range(-2.4f, -0.7f) + (if (r.nextBoolean()) PIF else 0f)
        canvas.drawLine(bx, by, bx + cos(a) * h * 0.035f, by + sin(a) * h * 0.02f, needles)
    }
    val bauble = pen()
    bauble.shader = RadialGradient(tx - h * 0.02f, ty - h * 0.02f, h * 0.06f, intArrayOf(c(0xFFB0A0), c(0xD8302A), c(0x6A1010)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(tx, ty + h * 0.01f, h * 0.04f, bauble)
    done(bloom = 0.6f, vignette = 0.32f, threshold = 0.6f)
}

/** Where the window of the New Year's room stands in a [w] × [h] picture. */
private fun newYearWindow(w: Float, h: Float): RectF {
    val ww = min(w * 0.34f, h * 0.46f)
    val left = w * 0.6f
    return RectF(left, h * 0.1f, left + ww, h * 0.62f)
}

/**
 * Новогодняя ночь: the room on the year's last night — the tree lit and hung with baubles, the
 * presents under it, the clock at a few minutes to midnight, and fireworks over the town in the
 * window.
 */
internal fun Painting.newYear(art: WeekArt, live: Boolean) {
    wall(RectF(0f, 0f, w, h * 0.84f), c(0x1E3A38), c(0x142826), grain = 0.16f)
    val pattern = pen()
    pattern.shader = LinearGradient(0f, 0f, h * 0.1f, h * 0.1f, intArrayOf(0x00000000, 0x12E8C878, 0x00000000), null, Shader.TileMode.REPEAT)
    canvas.drawRect(0f, 0f, w, h * 0.84f, pattern)
    tabletop(h * 0.84f, c(0x6A4228), 231)
    val win = newYearWindow(w, h)
    val frame = h * 0.02f
    window(win, c(0xE8E0D2), frame, 2, 232) {
        sky(0f to 0x060A22, 0.6f to 0x121C48, 1f to 0x2A3464, to = h)
        firework(w * 0.32f, h * 0.2f, h * 0.2f, c(0xFF5A4A), 1f, 233)
        firework(w * 0.74f, h * 0.16f, h * 0.15f, c(0x6AC8FF), 1f, 234)
        firework(w * 0.58f, h * 0.4f, h * 0.12f, c(0xFFD35A), 1f, 235)
        firework(w * 0.14f, h * 0.44f, h * 0.08f, c(0x8AFF9A), 0.9f, 238)
        facades(-w * 0.05f, w * 1.05f, h * 1.04f, h * 0.1f, tones(0x1E2238, 0x262A40, 0x2A2638), 0.55f, 236)
        if (!live) snowfall((w * h / 20f).roundToInt(), 237, size = 0.6f)
    }
    curtain(win.left - h * 0.12f, win.left + h * 0.02f, h * 0.04f, h * 0.84f, c(0x8A2A2A), 243, gatherRight = false)
    curtain(win.right - h * 0.02f, win.right + h * 0.12f, h * 0.04f, h * 0.84f, c(0x8A2A2A), 239, gatherRight = true)
    canvas.drawRect(win.left - h * 0.14f, h * 0.03f, win.right + h * 0.14f, h * 0.045f, pen(c(0xC8A060)))
    wallClock(w * 0.44f, h * 0.2f, h * 0.07f)
    garland(floatArrayOf(-2f, w * 0.25f, w * 0.52f), floatArrayOf(h * 0.04f, h * 0.06f, h * 0.035f), h * 0.02f, 18, h * 0.009f, 1f, 240)
    // The tree, its light on the walls and the floor, the presents under it.
    val tx = w * 0.26f
    glow(tx, h * 0.55f, h * 0.9f, c(0xFFC060), 0.3f)
    dressedTree(tx, h * 0.92f, h * 0.76f, 241, lights = 1f)
    gifts(tx + h * 0.02f, h * 0.95f, h * 0.12f, 242)
    for ((k, o) in listOf(floatArrayOf(0.5f, 0.96f, 0.08f), floatArrayOf(0.555f, 0.975f, 0.085f)).withIndex()) {
        tangerine(w * o[0], h * o[1], h * o[2], leaf = k == 0)
    }
    done(bloom = 0.8f, vignette = 0.32f, threshold = 0.5f)
}

// endregion
