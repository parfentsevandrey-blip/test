package app.rosa.weather.widget.render.calendar

import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Props for the year's scenes beyond the open landscape: rooms and windows, fire and candlelight,
 * a cup's steam, the sea and a beach, a camp in the woods, a dacha, a street at dusk, and the
 * small things seen close — a bullfinch on a branch, a mushroom, a snowdrop. All in dp, lit from
 * the painting's light where it matters, soft where a painter would leave them soft.
 */

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

private fun hex(rgb: Long) = Tone.of(rgb)

/** A horizontal gradient across a thing [half] wide at [x]: [shade] on the side away from the light, [lit] toward it. */
private fun Painting.sided(x: Float, half: Float, shade: Int, lit: Int): LinearGradient =
    if (lightX < x) LinearGradient(x - half, 0f, x + half, 0f, lit, shade, Shader.TileMode.CLAMP)
    else LinearGradient(x - half, 0f, x + half, 0f, shade, lit, Shader.TileMode.CLAMP)

/** A soft shadow lying on the floor or the ground under a thing. */
internal fun Painting.floorShadow(x: Float, y: Float, rx: Float, ry: Float, strength: Float) {
    val p = pen()
    p.shader = RadialGradient(x, y, rx, intArrayOf(Tone.alpha(0xFF000000.toInt(), strength), 0), null, Shader.TileMode.CLAMP)
    canvas.withScale(1f, ry / rx, x, y) {
        canvas.drawCircle(x, y, rx, p)
    }
}

// region Rooms

/** What a window looks out on: a painting of its own at this one's resolution, laid into [rect]. */
internal fun Painting.view(rect: RectF, seed: Int, scene: Painting.() -> Unit) {
    val v = Painting(rect.width(), rect.height(), kx, seed)
    v.scene()
    canvas.drawBitmap(v.bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
    v.bitmap.recycle()
}

/** A plain wall over [area]: [top] to [bottom] colour, a faint plaster grain. */
internal fun Painting.wall(area: RectF, top: Int, bottom: Int, grain: Float = 0.12f) {
    val p = pen()
    p.shader = LinearGradient(0f, area.top, 0f, area.bottom, top, bottom, Shader.TileMode.CLAMP)
    canvas.drawRect(area, p)
    if (grain > 0f) texture(android.graphics.Path().apply { addRect(area, android.graphics.Path.Direction.CW) }, grain, 1.4f)
}

/** A wall of round logs laid one on another, each lit along its middle and dark in its seams. */
internal fun Painting.logWall(area: RectF, tone: Int, log: Float, seed: Int) {
    val r = Random(seed)
    var y = area.top - r.range(0f, log)
    while (y < area.bottom) {
        val t = Tone.shade(tone, r.range(-0.08f, 0.08f))
        val p = pen()
        p.shader = LinearGradient(0f, y, 0f, y + log, intArrayOf(Tone.shade(t, -0.55f), Tone.shade(t, 0.12f), t, Tone.shade(t, -0.45f)), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(area.left, y, area.right, y + log, p)
        y += log
    }
    texture(android.graphics.Path().apply { addRect(area, android.graphics.Path.Direction.CW) }, 0.3f, 0.7f)
}

/**
 * Stone laid in courses, per pixel: blocks of uneven size in [tones], each with a worn face, dark
 * mortar between, the whole lit from [lightX] and warmed where [warm] glows (a fire's light).
 */
internal fun Painting.stoneWall(area: RectF, tones: IntArray, block: Float, seed: Int, warmX: Float = -1f, warmY: Float = -1f, warm: Int = 0, warmth: Float = 0f) {
    val bh = block * 0.62f
    pixels(area) { x, y ->
        val row = floor(y / bh).toInt()
        val shift = if (row % 2 == 0) 0f else block * 0.5f
        val jitter = (Noise.rand(row, 0, seed) - 0.5f) * block * 0.3f
        val u = (x + shift + jitter) / block
        val col = floor(u).toInt()
        val fx = u - col
        val fy = y / bh - row
        val edge = min(min(fx, 1f - fx) * block, min(fy, 1f - fy) * bh)
        val pick = Noise.rand(col, row, seed + 1)
        var c = ramp(tones, 0.25f + pick * 0.6f)
        val face = Noise.fbm(x / (block * 0.35f), y / (block * 0.35f), seed + 2, 3)
        c = Tone.shade(c, (face - 0.5f) * 0.35f)
        // Mortar, and the stone's worn edge.
        c = if (edge < 0.45f) Tone.shade(tones[0], -0.35f) else Tone.shade(c, -(1f - Tone.smooth(0.45f, 2.2f, edge)) * 0.35f)
        if (warmth > 0f && warm != 0) {
            val d = kotlin.math.hypot(x - warmX, y - warmY) / (area.width() * 0.6f)
            c = Tone.mix(c, Tone.mix(c, warm, 0.6f), (warmth * (1f - d)).coerceIn(0f, 1f))
        }
        c
    }
}

/**
 * A window onto [scene], in a painted frame [frameWidth] thick, with [panes] across and a cross
 * bar, a sill below; the glass catches a faint sheen of the room.
 */
internal fun Painting.window(rect: RectF, frame: Int, frameWidth: Float, panes: Int, seed: Int, sill: Boolean = true, scene: Painting.() -> Unit) {
    view(rect, seed, scene)
    val sheen = pen()
    sheen.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF, 0x0CFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.3f, 0.45f, 0.62f, 0.8f), Shader.TileMode.CLAMP)
    canvas.drawRect(rect, sheen)
    val f = pen()
    f.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Tone.shade(frame, 0.12f), Tone.shade(frame, -0.2f), Shader.TileMode.CLAMP)
    val half = frameWidth / 2f
    val outer = RectF(rect.left - frameWidth, rect.top - frameWidth, rect.right + frameWidth, rect.bottom + frameWidth)
    path.reset()
    path.addRect(outer, android.graphics.Path.Direction.CW)
    path.addRect(rect, android.graphics.Path.Direction.CCW)
    canvas.drawPath(path, f)
    // Mullions: the panes across, and one bar at two-fifths of the height.
    for (i in 1 until panes) {
        val x = rect.left + rect.width() * i / panes
        canvas.drawRect(x - half * 0.7f, rect.top, x + half * 0.7f, rect.bottom, f)
    }
    val bar = rect.top + rect.height() * 0.4f
    canvas.drawRect(rect.left, bar - half * 0.7f, rect.right, bar + half * 0.7f, f)
    // Inner shadow of the frame on the glass.
    val shade = stroke(0x40000000, frameWidth * 0.5f)
    shade.maskFilter = BlurMaskFilter(frameWidth * 0.5f, BlurMaskFilter.Blur.NORMAL)
    canvas.withClip(rect) { drawRect(rect, shade) }
    if (sill) {
        val s = pen()
        s.shader = LinearGradient(0f, rect.bottom + frameWidth, 0f, rect.bottom + frameWidth * 2.6f, Tone.shade(frame, 0.2f), Tone.shade(frame, -0.3f), Shader.TileMode.CLAMP)
        canvas.drawRect(outer.left - frameWidth * 1.2f, rect.bottom + frameWidth, outer.right + frameWidth * 1.2f, rect.bottom + frameWidth * 2.6f, s)
    }
}

/** Frost grown on glass: fern-like crystals in white, thickest from the frame inward. */
internal fun Painting.frostOnGlass(rect: RectF, amount: Float, seed: Int) {
    val reach = min(rect.width(), rect.height()) * 0.42f
    field(rect, kx * 0.8f) { x, y ->
        val edge = min(min(x - rect.left, rect.right - x), min(y - rect.top, rect.bottom - y))
        val near = 1f - Tone.smooth(0f, reach * (0.55f + 0.6f * Noise.fbm(x / reach, y / reach, seed + 3, 2)), edge)
        val fern = Noise.ridged(x / (reach * 0.16f), y / (reach * 0.16f), seed, 4)
        val a = (near * (0.25f + Tone.smooth(0.45f, 0.9f, fern) * 0.75f) * amount).coerceIn(0f, 0.92f)
        Tone.alpha(0xFFF2F6FF.toInt(), a)
    }
}

/** Rain on a window: drops that hold a point of light, and the runs they leave. */
internal fun Painting.raindrops(rect: RectF, count: Int, size: Float, seed: Int) {
    val r = Random(seed)
    canvas.withClip(rect) {
        repeat(count) {
            val x = rect.left + r.nextFloat() * rect.width()
            val y = rect.top + r.nextFloat() * rect.height()
            val s = size * r.range(0.4f, 1.3f)
            if (r.nextFloat() < 0.3f) {
                val run = stroke(0x2EFFFFFF, s * 0.5f)
                drawLine(x, y - s * r.range(3f, 10f), x + r.range(-0.3f, 0.3f), y, run)
            }
            val body = pen()
            body.shader = RadialGradient(x, y + s * 0.25f, s, intArrayOf(0x10FFFFFF, 0x3A0A1020), floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP)
            drawCircle(x, y, s, body)
            drawCircle(x - s * 0.3f, y - s * 0.35f, s * 0.28f, pen(0xB0FFFFFF.toInt()))
        }
    }
}

/** A curtain hanging in soft folds from [top] to [bottom], drawn aside at its middle. */
internal fun Painting.curtain(x0: Float, x1: Float, top: Float, bottom: Float, color: Int, seed: Int, gatherRight: Boolean) {
    val folds = 7
    val colors = IntArray(folds * 2 + 1) { i -> if (i % 2 == 0) Tone.shade(color, -0.3f) else Tone.shade(color, 0.15f) }
    val p = pen()
    p.shader = LinearGradient(x0, 0f, x1, 0f, colors, null, Shader.TileMode.CLAMP)
    val waist = top + (bottom - top) * 0.62f
    val pinch = (x1 - x0) * 0.45f
    path.reset()
    path.moveTo(x0, top)
    path.lineTo(x1, top)
    if (gatherRight) {
        path.quadTo(x1 - pinch * 0.2f, waist - (bottom - top) * 0.2f, x1 - pinch, waist)
        path.quadTo(x1 - pinch * 0.8f, bottom - (bottom - top) * 0.1f, x1 - pinch * 0.3f, bottom)
        path.lineTo(x0, bottom)
    } else {
        path.lineTo(x1, bottom)
        path.lineTo(x0 + pinch * 0.3f, bottom)
        path.quadTo(x0 + pinch * 0.8f, bottom - (bottom - top) * 0.1f, x0 + pinch, waist)
        path.quadTo(x0 + pinch * 0.2f, waist - (bottom - top) * 0.2f, x0, top)
    }
    path.close()
    canvas.drawPath(path, p)
    texture(android.graphics.Path(path), 0.25f, 0.6f)
    val tie = if (gatherRight) x1 - pinch else x0 + pinch
    canvas.drawRoundRect(tie - (x1 - x0) * 0.12f, waist - 1.2f, tie + (x1 - x0) * 0.12f, waist + 1.2f, 1f, 1f, pen(Tone.shade(color, -0.45f)))
}

/** A table's top in the foreground from [y] down: its lit front edge, the wood's grain. */
internal fun Painting.tabletop(y: Float, color: Int, seed: Int, cloth: Int = 0) {
    val area = RectF(0f, y, w, h)
    val p = pen()
    p.shader = LinearGradient(0f, y, 0f, h, Tone.shade(color, 0.1f), Tone.shade(color, -0.35f), Shader.TileMode.CLAMP)
    canvas.drawRect(area, p)
    if (cloth == 0) {
        val r = Random(seed)
        repeat(18) {
            val gy = y + r.nextFloat() * (h - y)
            canvas.drawLine(0f, gy, w, gy + r.range(-1f, 1f), stroke(Tone.alpha(Tone.shade(color, -0.4f), 0.35f), r.range(0.2f, 0.6f)))
        }
    } else {
        canvas.drawRect(area, pen(Tone.alpha(cloth, 0.9f)))
        val check = stroke(Tone.alpha(Tone.shade(cloth, -0.35f), 0.35f), (h - y) * 0.05f)
        var gx = 0f
        while (gx < w) {
            canvas.drawLine(gx, y, gx - (h - y) * 0.2f, h, check)
            gx += (h - y) * 0.3f
        }
        var gy = y + (h - y) * 0.25f
        while (gy < h) {
            canvas.drawLine(0f, gy, w, gy, check)
            gy += (h - y) * 0.3f
        }
    }
    canvas.drawRect(0f, y - 0.5f, w, y + 0.8f, pen(Tone.alpha(0xFFFFFFFF.toInt(), 0.35f)))
}

/** Steam rising from a cup: a few soft wisps curling up and thinning out. */
internal fun Painting.steam(x: Float, y: Float, height: Float, strength: Float, seed: Int) {
    val r = Random(seed)
    repeat(3) { k ->
        val p = stroke(0, height * 0.07f)
        p.maskFilter = BlurMaskFilter(height * 0.05f, BlurMaskFilter.Blur.NORMAL)
        p.shader = LinearGradient(0f, y, 0f, y - height, Tone.alpha(0xFFFFFFFF.toInt(), 0.55f * strength), 0, Shader.TileMode.CLAMP)
        val x0 = x + (k - 1) * height * 0.12f
        path.reset()
        path.moveTo(x0, y)
        val sway = height * r.range(0.12f, 0.22f) * (if (k % 2 == 0) 1f else -1f)
        path.cubicTo(x0 + sway, y - height * 0.3f, x0 - sway, y - height * 0.6f, x0 + sway * 0.6f, y - height)
        canvas.drawPath(path, p)
    }
}

/** A mug on the table: its body shaded round, the drink inside, the handle, and its steam. */
internal fun Painting.mug(x: Float, base: Float, size: Float, body: Int, drink: Int, steam: Boolean, seed: Int) {
    val hw = size * 0.42f
    val top = base - size
    floorShadow(x + size * 0.1f, base, size * 0.7f, size * 0.14f, 0.35f)
    // Handle.
    val handle = stroke(Tone.shade(body, -0.15f), size * 0.1f)
    canvas.drawArc(RectF(x + hw * 0.7f, top + size * 0.22f, x + hw * 1.55f, top + size * 0.72f), -80f, 160f, false, handle)
    val p = pen()
    p.shader = sided(x, hw, Tone.shade(body, -0.4f), Tone.shade(body, 0.25f))
    path.reset()
    path.moveTo(x - hw, top)
    path.lineTo(x - hw * 0.9f, base - size * 0.06f)
    path.quadTo(x, base + size * 0.05f, x + hw * 0.9f, base - size * 0.06f)
    path.lineTo(x + hw, top)
    path.close()
    canvas.drawPath(path, p)
    canvas.drawOval(x - hw, top - size * 0.08f, x + hw, top + size * 0.08f, pen(Tone.shade(body, 0.35f)))
    canvas.drawOval(x - hw * 0.86f, top - size * 0.055f, x + hw * 0.86f, top + size * 0.065f, pen(drink))
    if (steam) steam(x, top - size * 0.05f, size * 1.4f, 1f, seed)
}

/** A flame: a teardrop, white at its heart, gold, then orange at its edge, and the light it throws. */
internal fun Painting.flame(x: Float, base: Float, width: Float, height: Float, glowRadius: Float = height * 4f) {
    glow(x, base - height * 0.45f, glowRadius, 0xFFFFB24A.toInt(), 0.45f)
    val p = pen()
    p.shader = RadialGradient(x, base - height * 0.28f, height * 0.75f, intArrayOf(0xFFFFFFF0.toInt(), 0xFFFFE08A.toInt(), 0xFFFF9A2E.toInt(), 0x00FF6A1A), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x, base - height)
    path.cubicTo(x + width * 0.25f, base - height * 0.6f, x + width * 0.6f, base - height * 0.3f, x, base)
    path.cubicTo(x - width * 0.6f, base - height * 0.3f, x - width * 0.25f, base - height * 0.6f, x, base - height)
    path.close()
    canvas.drawPath(path, p)
}

/** A candle: its wax lit on the flame's side, a drip down it, the wick and the flame. */
internal fun Painting.candle(x: Float, base: Float, size: Float, wax: Int = hex(0xF4EAD6)) {
    val hw = size * 0.13f
    val top = base - size
    val p = pen()
    p.shader = sided(x, hw, Tone.shade(wax, -0.3f), Tone.shade(wax, 0.15f))
    canvas.drawRect(x - hw, top, x + hw, base, p)
    canvas.drawOval(x - hw, top - hw * 0.35f, x + hw, top + hw * 0.35f, pen(Tone.shade(wax, 0.2f)))
    canvas.drawRoundRect(x - hw * 0.6f, top, x - hw * 0.2f, top + size * 0.25f, hw * 0.2f, hw * 0.2f, pen(Tone.shade(wax, 0.08f)))
    canvas.drawLine(x, top, x, top - size * 0.06f, stroke(0xFF2A2018.toInt(), max(0.12f, size * 0.02f)))
    flame(x, top - size * 0.05f, size * 0.14f, size * 0.32f, size * 2.2f)
}

/**
 * Fire: tongues of flame of uneven height, each red at its edge, orange, then gold and white at
 * its root, sparks lifting off, the warm light round it.
 */
internal fun Painting.fire(x: Float, base: Float, width: Float, height: Float, seed: Int, sparks: Int = 10) {
    val r = Random(seed)
    glow(x, base - height * 0.3f, max(width, height) * 3.2f, 0xFFFF8A2E.toInt(), 0.55f)
    glow(x, base - height * 0.25f, max(width, height) * 1.2f, 0xFFFFC46A.toInt(), 0.6f)
    val layers = listOf(
        Triple(1f, intArrayOf(0xE6D8341C.toInt(), 0xCCFF6A1A.toInt(), 0x00FF6A1A), 7),
        Triple(0.72f, intArrayOf(0xF2FF8A24.toInt(), 0xE6FFB43A.toInt(), 0x00FFC04A), 6),
        Triple(0.45f, intArrayOf(0xFFFFF2C8.toInt(), 0xF2FFD470.toInt(), 0x00FFE08A), 4),
    )
    for ((scale, colors, tongues) in layers) {
        repeat(tongues) { k ->
            val t = (k + 0.5f) / tongues
            val tx = x + (t - 0.5f) * width * scale * 0.9f + r.range(-0.06f, 0.06f) * width
            val th = height * scale * r.range(0.55f, 1f) * (1f - abs(t - 0.5f) * 0.9f)
            val tw = width * scale / tongues * r.range(1.2f, 1.8f)
            val p = pen()
            p.shader = LinearGradient(0f, base, 0f, base - th, colors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            val lean = r.range(-0.25f, 0.25f) * tw
            path.reset()
            path.moveTo(tx - tw, base)
            path.cubicTo(tx - tw * 0.9f, base - th * 0.45f, tx + lean - tw * 0.2f, base - th * 0.7f, tx + lean, base - th)
            path.cubicTo(tx + lean + tw * 0.2f, base - th * 0.7f, tx + tw * 0.9f, base - th * 0.45f, tx + tw, base)
            path.close()
            canvas.drawPath(path, p)
        }
    }
    repeat(sparks) {
        val sx = x + r.range(-0.5f, 0.5f) * width
        val sy = base - height * r.range(0.8f, 2.2f)
        val s = max(0.2f, height * r.range(0.012f, 0.03f))
        glow(sx, sy, s * 5f, 0xFFFFB84A.toInt(), 0.5f)
        canvas.drawCircle(sx, sy, s, pen(0xFFFFE6A8.toInt()))
    }
}

/** Logs crossed in a hearth or a campfire. */
internal fun Painting.logs(x: Float, base: Float, width: Float, seed: Int, bark: Int = hex(0x4A3222)) {
    val r = Random(seed)
    repeat(3) { k ->
        val a = (k - 1) * 0.35f + r.range(-0.08f, 0.08f)
        val len = width * r.range(0.8f, 1.05f)
        val thick = width * 0.12f
        canvas.withRotation(a * 57.3f, x, base) {
            val p = pen()
            p.shader = LinearGradient(0f, base - thick, 0f, base, Tone.shade(bark, 0.2f), Tone.shade(bark, -0.45f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(x - len / 2f, base - thick, x + len / 2f, base, thick / 2f, thick / 2f, p)
            canvas.drawOval(x + len / 2f - thick * 0.4f, base - thick, x + len / 2f + thick * 0.2f, base, pen(hex(0xC89A68)))
        }
    }
}

/**
 * A fireplace in [rect]: a surround of stone with its mantel, the dark hearth arched inside, logs
 * burning, and the light spilling out onto the floor in front.
 */
internal fun Painting.fireplace(rect: RectF, stone: IntArray, seed: Int) {
    stoneWall(rect, stone, rect.width() * 0.16f, seed + 1)
    val mantel = pen()
    mantel.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.1f, Tone.shade(stone[3], 0.15f), Tone.shade(stone[1], -0.2f), Shader.TileMode.CLAMP)
    canvas.drawRect(rect.left - rect.width() * 0.06f, rect.top, rect.right + rect.width() * 0.06f, rect.top + rect.height() * 0.09f, mantel)
    val hearth = RectF(rect.left + rect.width() * 0.17f, rect.top + rect.height() * 0.28f, rect.right - rect.width() * 0.17f, rect.bottom)
    path.reset()
    path.moveTo(hearth.left, hearth.bottom)
    path.lineTo(hearth.left, hearth.top + hearth.width() * 0.3f)
    path.quadTo(hearth.centerX(), hearth.top - hearth.width() * 0.05f, hearth.right, hearth.top + hearth.width() * 0.3f)
    path.lineTo(hearth.right, hearth.bottom)
    path.close()
    val inside = pen()
    inside.shader = LinearGradient(0f, hearth.top, 0f, hearth.bottom, hex(0x0C0806), hex(0x3A1A0A), Shader.TileMode.CLAMP)
    canvas.drawPath(path, inside)
    val arch = android.graphics.Path(path)
    canvas.withClip(arch) {
        logs(hearth.centerX(), hearth.bottom - hearth.height() * 0.06f, hearth.width() * 0.8f, seed + 2)
        fire(hearth.centerX(), hearth.bottom - hearth.height() * 0.1f, hearth.width() * 0.62f, hearth.height() * 0.62f, seed + 3, sparks = 6)
    }
    glow(hearth.centerX(), rect.bottom, rect.width() * 1.4f, 0xFFFF9A40.toInt(), 0.4f, squash = 0.3f)
}

/** Books standing on a shelf or a sill: spines of uneven height and colour, a band on some. */
internal fun Painting.books(x: Float, base: Float, count: Int, height: Float, colors: IntArray, seed: Int) {
    val r = Random(seed)
    var bx = x
    repeat(count) {
        val bw = height * r.range(0.14f, 0.24f)
        val bh = height * r.range(0.72f, 1f)
        val c = colors[r.nextInt(colors.size)]
        val p = pen()
        p.shader = LinearGradient(bx, 0f, bx + bw, 0f, Tone.shade(c, 0.15f), Tone.shade(c, -0.3f), Shader.TileMode.CLAMP)
        canvas.drawRect(bx, base - bh, bx + bw, base, p)
        if (r.nextFloat() < 0.6f) canvas.drawRect(bx, base - bh * 0.8f, bx + bw, base - bh * 0.74f, pen(Tone.alpha(hex(0xE8C77A), 0.8f)))
        bx += bw + height * 0.01f
    }
}

/** A table lamp: its foot, a shade lit from within, and the pool of warm light it casts. */
internal fun Painting.tableLamp(x: Float, base: Float, size: Float, shade: Int) {
    glow(x, base - size * 0.6f, size * 3.2f, 0xFFFFC878.toInt(), 0.5f)
    glow(x, base, size * 1.6f, 0xFFFFD49A.toInt(), 0.35f, squash = 0.35f)
    canvas.drawRect(x - size * 0.04f, base - size * 0.55f, x + size * 0.04f, base, pen(hex(0x3A2A1E)))
    canvas.drawOval(x - size * 0.2f, base - size * 0.06f, x + size * 0.2f, base + size * 0.04f, pen(hex(0x2E2018)))
    val p = pen()
    p.shader = LinearGradient(0f, base - size, 0f, base - size * 0.5f, Tone.mix(shade, 0xFFFFF4D8.toInt(), 0.5f), shade, Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.2f, base - size)
    path.lineTo(x + size * 0.2f, base - size)
    path.lineTo(x + size * 0.36f, base - size * 0.52f)
    path.lineTo(x - size * 0.36f, base - size * 0.52f)
    path.close()
    canvas.drawPath(path, p)
}

/**
 * A wing chair seen from behind and a little to the side: its high back arched at the top, the
 * wings, the rolled arms, short legs — lit on the side toward the light, a warm rim along its edge.
 */
internal fun Painting.armchair(x: Float, base: Float, size: Float, color: Int) {
    floorShadow(x, base, size * 0.75f, size * 0.12f, 0.45f)
    val lit = lightX < x
    val light = Tone.shade(color, 0.18f)
    val shade = Tone.shade(color, -0.55f)
    fun fill(half: Float) = pen().apply { shader = sided(x, half, shade, light) }
    // Legs.
    for (lx in listOf(-0.36f, 0.36f)) canvas.drawRect(x + lx * size - size * 0.03f, base - size * 0.1f, x + lx * size + size * 0.03f, base, pen(hex(0x1E140E)))
    // The seat's skirt and the arms rolled over at the sides.
    canvas.drawRoundRect(x - size * 0.46f, base - size * 0.42f, x + size * 0.46f, base - size * 0.08f, size * 0.06f, size * 0.06f, fill(size * 0.5f))
    for (side in listOf(-1f, 1f)) {
        val ax = x + side * size * 0.44f
        val arm = pen()
        arm.shader = RadialGradient(ax - (if (lit) 1f else -1f) * size * 0.04f, base - size * 0.52f, size * 0.16f, intArrayOf(light, color, shade), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(ax - size * 0.1f, base - size * 0.6f, ax + size * 0.1f, base - size * 0.12f, size * 0.09f, size * 0.09f, arm)
    }
    // The back: arched at the top, the wings swelling out.
    path.reset()
    path.moveTo(x - size * 0.36f, base - size * 0.4f)
    path.lineTo(x - size * 0.4f, base - size * 0.72f)
    path.quadTo(x - size * 0.5f, base - size * 0.84f, x - size * 0.4f, base - size * 0.94f)
    path.quadTo(x, base - size * 1.1f, x + size * 0.4f, base - size * 0.94f)
    path.quadTo(x + size * 0.5f, base - size * 0.84f, x + size * 0.4f, base - size * 0.72f)
    path.lineTo(x + size * 0.36f, base - size * 0.4f)
    path.close()
    canvas.drawPath(path, fill(size * 0.45f))
    // Buttoned upholstery, and the warm rim of light along the lit edge.
    val tuft = pen(Tone.alpha(shade, 0.5f))
    for (row in 0..2) for (col in -2..2) {
        canvas.drawCircle(x + col * size * 0.13f + (row % 2) * size * 0.065f, base - size * (0.56f + row * 0.13f), size * 0.012f, tuft)
    }
    val rim = stroke(Tone.alpha(Tone.mix(light, 0xFFFFC080.toInt(), 0.5f), 0.55f), max(0.2f, size * 0.02f))
    rim.maskFilter = BlurMaskFilter(max(0.2f, size * 0.012f), BlurMaskFilter.Blur.NORMAL)
    val side = if (lit) -1f else 1f
    path.reset()
    path.moveTo(x + side * size * 0.38f, base - size * 0.42f)
    path.lineTo(x + side * size * 0.42f, base - size * 0.72f)
    path.quadTo(x + side * size * 0.52f, base - size * 0.84f, x + side * size * 0.4f, base - size * 0.95f)
    canvas.drawPath(path, rim)
}

/** A cat sitting with its back turned, looking out: body, head, ears, and its tail curled round. */
internal fun Painting.cat(x: Float, base: Float, size: Float, color: Int) {
    val p = pen()
    p.shader = sided(x, size * 0.4f, Tone.shade(color, -0.35f), Tone.shade(color, 0.15f))
    canvas.drawOval(x - size * 0.34f, base - size * 0.72f, x + size * 0.34f, base, p)
    canvas.drawCircle(x, base - size * 0.82f, size * 0.22f, p)
    for (side in listOf(-1f, 1f)) {
        path.reset()
        path.moveTo(x + side * size * 0.2f, base - size * 0.88f)
        path.lineTo(x + side * size * 0.17f, base - size * 1.08f)
        path.lineTo(x + side * size * 0.04f, base - size * 0.96f)
        path.close()
        canvas.drawPath(path, p)
    }
    val tail = stroke(Tone.shade(color, -0.1f), size * 0.1f)
    path.reset()
    path.moveTo(x + size * 0.28f, base - size * 0.1f)
    path.quadTo(x + size * 0.62f, base - size * 0.02f, x + size * 0.5f, base - size * 0.3f)
    canvas.drawPath(path, tail)
}

/** A vase of flowers: the glass, the stems in the water, the blooms in [colors] as fluffy heads or cups. */
internal fun Painting.vase(x: Float, base: Float, size: Float, glass: Int, colors: IntArray, fluffy: Boolean, seed: Int) {
    val r = Random(seed)
    val stems = stroke(hex(0x4E7A34), max(0.15f, size * 0.02f))
    val heads = ArrayList<Pair<Float, Float>>()
    repeat(if (fluffy) 13 else 6) {
        val a = r.range(-0.7f, 0.7f)
        val len = size * r.range(0.8f, 1.25f)
        val ex = x + sin(a) * len
        val ey = base - size * 0.55f - cos(a) * len
        canvas.drawLine(x, base - size * 0.4f, ex, ey, stems)
        heads += ex to ey
    }
    for ((hx, hy) in heads) {
        if (fluffy) {
            // Mimosa: feathery grey-green leaves, and sprays of little yellow balls, soft as down.
            val frond = stroke(hex(0x7A9A7A), max(0.12f, size * 0.008f))
            val fa = r.range(-0.6f, 0.6f)
            for (k in 0..6) {
                val t = k / 6f
                val fx = hx + cos(fa) * size * 0.2f * t
                val fy = hy + size * 0.1f + sin(fa) * size * 0.2f * t
                canvas.drawLine(fx, fy, fx - size * 0.03f, fy - size * 0.03f, frond)
                canvas.drawLine(fx, fy, fx + size * 0.03f, fy + size * 0.03f, frond)
            }
            repeat(24) {
                val bx = hx + r.range(-1f, 1f) * size * 0.16f
                val by = hy + r.range(-1f, 1f) * size * 0.11f
                val br = size * r.range(0.02f, 0.034f)
                val c = Tone.shade(colors[r.nextInt(colors.size)], r.range(-0.12f, 0.12f))
                val ball = pen()
                ball.shader = RadialGradient(bx - br * 0.3f, by - br * 0.3f, br * 1.4f, intArrayOf(Tone.mix(c, 0xFFFFFFFF.toInt(), 0.4f), c, Tone.shade(c, -0.35f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(bx, by, br, ball)
            }
        } else {
            // Tulips: closed cups of petals.
            val c = colors[r.nextInt(colors.size)]
            val cw = size * 0.1f
            val p = pen()
            p.shader = sided(hx, cw, Tone.shade(c, -0.3f), Tone.shade(c, 0.2f))
            path.reset()
            path.moveTo(hx - cw, hy)
            path.quadTo(hx - cw, hy + cw * 1.6f, hx, hy + cw * 1.6f)
            path.quadTo(hx + cw, hy + cw * 1.6f, hx + cw, hy)
            path.lineTo(hx + cw * 0.5f, hy + cw * 0.3f)
            path.lineTo(hx, hy - cw * 0.1f)
            path.lineTo(hx - cw * 0.5f, hy + cw * 0.3f)
            path.close()
            canvas.drawPath(path, p)
        }
    }
    val v = pen()
    v.shader = sided(x, size * 0.22f, Tone.alpha(Tone.shade(glass, -0.3f), 0.85f), Tone.alpha(Tone.shade(glass, 0.35f), 0.75f))
    path.reset()
    path.moveTo(x - size * 0.14f, base - size * 0.55f)
    path.cubicTo(x - size * 0.3f, base - size * 0.35f, x - size * 0.28f, base - size * 0.05f, x - size * 0.18f, base)
    path.lineTo(x + size * 0.18f, base)
    path.cubicTo(x + size * 0.28f, base - size * 0.05f, x + size * 0.3f, base - size * 0.35f, x + size * 0.14f, base - size * 0.55f)
    path.close()
    canvas.drawPath(path, v)
    canvas.drawLine(x - size * 0.12f, base - size * 0.45f, x - size * 0.16f, base - size * 0.12f, stroke(0x88FFFFFF.toInt(), size * 0.025f))
}

/** A jar of jam: glass with the jam's colour deep inside, a cloth over its lid tied with string. */
internal fun Painting.jar(x: Float, base: Float, size: Float, jam: Int, cloth: Int) {
    val hw = size * 0.32f
    val p = pen()
    p.shader = sided(x, hw, Tone.shade(jam, -0.45f), Tone.shade(jam, 0.15f))
    canvas.drawRoundRect(x - hw, base - size * 0.85f, x + hw, base, hw * 0.3f, hw * 0.3f, p)
    canvas.drawLine(x - hw * 0.55f, base - size * 0.72f, x - hw * 0.55f, base - size * 0.18f, stroke(0x66FFFFFF, hw * 0.12f))
    path.reset()
    path.moveTo(x - hw * 1.2f, base - size * 0.8f)
    path.quadTo(x, base - size * 1.05f, x + hw * 1.2f, base - size * 0.8f)
    path.lineTo(x + hw * 1.05f, base - size * 0.66f)
    path.quadTo(x, base - size * 0.72f, x - hw * 1.05f, base - size * 0.66f)
    path.close()
    canvas.drawPath(path, pen(cloth))
    canvas.drawLine(x - hw, base - size * 0.72f, x + hw, base - size * 0.72f, stroke(hex(0xE8D8B8), size * 0.03f))
}

/** A New Year's tree indoors: a spruce hung with baubles and a string of lights, a star on top. */
internal fun Painting.dressedTree(x: Float, base: Float, height: Float, seed: Int, lights: Float = 1f) {
    conifer(x, base, height, height * 0.42f, intArrayOf(hex(0x0A1E14), hex(0x143424), hex(0x1F4A32), hex(0x2E6444), hex(0x4A8A5E)), seed)
    val r = Random(seed + 1)
    val colors = intArrayOf(hex(0xE0342C), hex(0xF2C23A), hex(0x3A7AE0), hex(0xE8E8F0), hex(0xC0306A))
    repeat(16) {
        val t = r.range(0.12f, 0.9f)
        val y = base - height * t
        val half = height * 0.42f * (1f - t) * 0.85f
        val bx = x + r.range(-1f, 1f) * half
        val br = height * r.range(0.018f, 0.03f)
        val c = colors[r.nextInt(colors.size)]
        val p = pen()
        p.shader = RadialGradient(bx - br * 0.35f, y - br * 0.35f, br * 1.4f, intArrayOf(Tone.mix(c, 0xFFFFFFFF.toInt(), 0.7f), c, Tone.shade(c, -0.5f)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, y, br, p)
    }
    val bulbs = intArrayOf(0xFFFFD56A.toInt(), 0xFFFF7A5A.toInt(), 0xFF8AD0FF.toInt(), 0xFFB8FF8A.toInt())
    repeat(22) {
        val t = r.range(0.1f, 0.92f)
        val y = base - height * t
        val half = height * 0.42f * (1f - t) * 0.9f
        val bx = x + r.range(-1f, 1f) * half
        val c = bulbs[r.nextInt(bulbs.size)]
        glow(bx, y, height * 0.05f, c, 0.6f * lights)
        canvas.drawCircle(bx, y, height * 0.008f, pen(Tone.alpha(Tone.mix(c, 0xFFFFFFFF.toInt(), 0.5f), lights)))
    }
    val sy = base - height * 1.02f
    glow(x, sy, height * 0.18f, 0xFFFFD66A.toInt(), 0.7f * max(0.4f, lights))
    val star = pen(0xFFFFE08A.toInt())
    path.reset()
    for (k in 0 until 10) {
        val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
        val rr = if (k % 2 == 0) height * 0.05f else height * 0.02f
        val px = x + cos(a) * rr
        val py = sy + sin(a) * rr
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    canvas.drawPath(path, star)
}

/** Presents under the tree: boxes in bright paper tied with ribbon. */
internal fun Painting.gifts(x: Float, base: Float, size: Float, seed: Int) {
    val r = Random(seed)
    val papers = intArrayOf(hex(0xC8302A), hex(0x2A6AC8), hex(0xE8B83A), hex(0x2E8A5A), hex(0x8A3AB8))
    var gx = x - size * 1.2f
    repeat(4) {
        val bw = size * r.range(0.5f, 0.85f)
        val bh = size * r.range(0.4f, 0.8f)
        val c = papers[r.nextInt(papers.size)]
        val p = pen()
        p.shader = sided(gx + bw / 2f, bw / 2f, Tone.shade(c, -0.35f), Tone.shade(c, 0.12f))
        canvas.drawRect(gx, base - bh, gx + bw, base, p)
        val ribbon = pen(hex(0xF4E2B0))
        canvas.drawRect(gx + bw * 0.45f, base - bh, gx + bw * 0.55f, base, ribbon)
        canvas.drawRect(gx, base - bh * 0.6f, gx + bw, base - bh * 0.5f, ribbon)
        canvas.drawOval(gx + bw * 0.28f, base - bh - bw * 0.14f, gx + bw * 0.5f, base - bh + bw * 0.02f, ribbon)
        canvas.drawOval(gx + bw * 0.5f, base - bh - bw * 0.14f, gx + bw * 0.72f, base - bh + bw * 0.02f, ribbon)
        gx += bw * 0.85f
    }
}

// endregion

// region Sea and summer

/**
 * The sea, per pixel, from the horizon down to [bottom]: deep blue far off turning [shallow] near
 * the shore, swell lines crowding to the horizon, and a road of glitter under the sun.
 */
internal fun Painting.sea(horizon: Float, bottom: Float, deep: Int, shallow: Int, seed: Int, glitter: Float = 1f) {
    field(RectF(0f, horizon, w, bottom), kx * 0.7f) { x, y ->
        val depth = ((y - horizon) / (bottom - horizon)).coerceIn(0f, 1f)
        val z = 1f / (0.04f + depth)
        val swell = Noise.fbm(x / (w * 0.05f) / z * 6f, (y - horizon) * 0.9f * z * 0.02f + x * 0.001f, seed, 3)
        var c = Tone.mix(deep, shallow, Tone.smooth(0.2f, 1f, depth))
        c = Tone.shade(c, (swell - 0.5f) * (0.25f + depth * 0.3f))
        val road = kotlin.math.exp(-((x - lightX) / (w * (0.04f + depth * 0.18f))).let { it * it })
        val spark = Noise.value(x / (0.6f + depth * 2f), y / (0.25f + depth * 0.6f), seed + 3)
        if (glitter > 0f && spark > 0.72f - road * 0.25f) c = Tone.mix(c, 0xFFFFFFF0.toInt(), ((spark - 0.6f) * 2.5f * road * glitter).coerceIn(0f, 0.95f))
        c
    }
}

/** The surf along [line]: a band of white foam with lace behind it running up the sand. */
internal fun Painting.surf(line: Painting.Line, seed: Int) {
    val top = line.top
    field(RectF(0f, top - h * 0.03f, w, h), kx * 0.8f) { x, y ->
        val edge = line.at(x)
        val d = y - edge
        val lace = Noise.fbm(x / (w * 0.02f), y / (h * 0.01f), seed, 3)
        val a = when {
            d < -h * 0.012f -> 0f
            d < h * 0.006f -> 0.85f * Tone.smooth(-h * 0.012f, 0f, d)
            else -> Tone.smooth(0.55f, 0.7f, lace) * (1f - Tone.smooth(h * 0.006f, h * 0.05f, d)) * 0.7f
        }
        Tone.alpha(0xFFFFFFFF.toInt(), a)
    }
}

/** Sand, per pixel, from [line] down: warm, rippled by the wind, finer with the distance. */
internal fun Painting.sand(line: Painting.Line, horizon: Float, tones: IntArray, seed: Int) {
    val g = Ground(w, h, horizon)
    pixels(RectF(0f, line.top, w, h), android.graphics.Path().apply {
        moveTo(-1f, h + 1f)
        lineTo(-1f, line.ys[0])
        for (i in line.ys.indices) lineTo(i * line.step, line.ys[i])
        lineTo(w + 1f, line.ys.last())
        lineTo(w + 1f, h + 1f)
        close()
    }) { x, y ->
        g.at(x, y)
        val ripple = sin(g.away * 3.2f + Noise.fbm(g.across * 0.4f, g.away * 0.3f, seed, 2) * 6f) * 0.5f + 0.5f
        val grain = Noise.value(x * 1.4f, y * 1.4f, seed + 1)
        val v = 0.45f + (ripple - 0.5f) * 0.25f + (grain - 0.5f) * 0.2f + Tone.smooth(0f, 1f, (y - line.top) / (h - line.top)) * 0.15f
        ramp(tones, v.coerceIn(0f, 1f))
    }
}

/** A beach umbrella: its pole, a canopy of stripes lit from above, its shadow on the sand. */
internal fun Painting.umbrella(x: Float, base: Float, size: Float, a: Int, b: Int) {
    floorShadow(x + size * 0.3f, base, size * 0.9f, size * 0.16f, 0.35f)
    canvas.drawLine(x, base, x - size * 0.08f, base - size * 1.05f, stroke(hex(0xE8E2D0), max(0.2f, size * 0.035f)))
    val cx = x - size * 0.08f
    val cy = base - size * 1.05f
    val stripes = 8
    for (k in 0 until stripes) {
        val a0 = PI.toFloat() + k * PI.toFloat() / stripes
        val a1 = a0 + PI.toFloat() / stripes
        path.reset()
        path.moveTo(cx, cy - size * 0.18f)
        path.lineTo(cx + cos(a0) * size * 0.8f, cy + sin(a0) * size * 0.12f + size * 0.12f)
        path.quadTo(cx + cos((a0 + a1) / 2f) * size * 0.84f, cy + size * 0.2f, cx + cos(a1) * size * 0.8f, cy + sin(a1) * size * 0.12f + size * 0.12f)
        path.close()
        val c = if (k % 2 == 0) a else b
        val p = pen()
        p.shader = LinearGradient(0f, cy - size * 0.18f, 0f, cy + size * 0.2f, Tone.shade(c, 0.2f), Tone.shade(c, -0.25f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
    }
}

/** A deck chair: its wooden frame and a striped canvas sling. */
internal fun Painting.deckChair(x: Float, base: Float, size: Float, canvasColor: Int) {
    floorShadow(x + size * 0.2f, base, size * 0.6f, size * 0.12f, 0.3f)
    val wood = stroke(hex(0xB88A52), max(0.2f, size * 0.05f))
    canvas.drawLine(x - size * 0.4f, base, x + size * 0.25f, base - size * 0.75f, wood)
    canvas.drawLine(x + size * 0.35f, base, x - size * 0.1f, base - size * 0.4f, wood)
    val p = pen()
    p.shader = LinearGradient(x - size * 0.3f, 0f, x + size * 0.3f, 0f, intArrayOf(canvasColor, 0xFFFFFFFF.toInt(), canvasColor, 0xFFFFFFFF.toInt(), canvasColor), null, Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.34f, base - size * 0.1f)
    path.quadTo(x - size * 0.05f, base - size * 0.2f, x + size * 0.2f, base - size * 0.7f)
    path.lineTo(x + size * 0.3f, base - size * 0.66f)
    path.quadTo(x + size * 0.05f, base - size * 0.1f, x - size * 0.22f, base - size * 0.02f)
    path.close()
    canvas.drawPath(path, p)
}

/** A sailing boat far out: a dark hull, a white sail lit on its sunny side, its mirror in the water. */
internal fun Painting.sailboat(x: Float, y: Float, size: Float, sail: Int = hex(0xFBF7EE)) {
    val p = pen()
    p.shader = sided(x, size * 0.4f, Tone.shade(sail, -0.3f), sail)
    path.reset()
    path.moveTo(x, y - size)
    path.lineTo(x + size * 0.42f, y - size * 0.12f)
    path.lineTo(x, y - size * 0.12f)
    path.close()
    canvas.drawPath(path, p)
    path.reset()
    path.moveTo(x - size * 0.04f, y - size * 0.85f)
    path.lineTo(x - size * 0.04f, y - size * 0.14f)
    path.lineTo(x - size * 0.3f, y - size * 0.14f)
    path.close()
    canvas.drawPath(path, pen(Tone.shade(sail, -0.15f)))
    path.reset()
    path.moveTo(x - size * 0.38f, y - size * 0.1f)
    path.lineTo(x + size * 0.45f, y - size * 0.1f)
    path.lineTo(x + size * 0.34f, y)
    path.lineTo(x - size * 0.28f, y)
    path.close()
    canvas.drawPath(path, pen(hex(0x2A2E3A)))
    canvas.drawRect(x - size * 0.3f, y + size * 0.02f, x + size * 0.36f, y + size * 0.05f, pen(Tone.alpha(sail, 0.35f)))
}

/** A lighthouse: a tapering tower in red and white bands, its lantern lit. */
internal fun Painting.lighthouse(x: Float, base: Float, height: Float, lit: Float = 0.6f) {
    val bw = height * 0.13f
    val tw = height * 0.08f
    val bands = 5
    for (k in 0 until bands) {
        val y0 = base - height * 0.85f * k / bands
        val y1 = base - height * 0.85f * (k + 1) / bands
        val w0 = bw + (tw - bw) * k / bands
        val w1 = bw + (tw - bw) * (k + 1) / bands
        path.reset()
        path.moveTo(x - w0, y0)
        path.lineTo(x - w1, y1)
        path.lineTo(x + w1, y1)
        path.lineTo(x + w0, y0)
        path.close()
        val c = if (k % 2 == 0) hex(0xF2EEE6) else hex(0xC8322A)
        val p = pen()
        p.shader = sided(x, w0, Tone.shade(c, -0.3f), c)
        canvas.drawPath(path, p)
    }
    val top = base - height * 0.85f
    canvas.drawRect(x - tw * 0.8f, top - height * 0.08f, x + tw * 0.8f, top, pen(hex(0xFFE8A0)))
    glow(x, top - height * 0.04f, height * 0.5f, 0xFFFFE6A0.toInt(), lit)
    path.reset()
    path.moveTo(x - tw, top - height * 0.08f)
    path.lineTo(x, top - height * 0.15f)
    path.lineTo(x + tw, top - height * 0.08f)
    path.close()
    canvas.drawPath(path, pen(hex(0x2A2A30)))
}

/** A campfire: a ring of stones, logs, the fire, sparks, and the ground lit round it. */
internal fun Painting.campfire(x: Float, base: Float, size: Float, seed: Int) {
    glow(x, base, size * 3.5f, 0xFFFF8A3A.toInt(), 0.45f, squash = 0.4f)
    val r = Random(seed)
    repeat(9) { k ->
        val a = k / 9f * 2f * PI.toFloat()
        val sx = x + cos(a) * size * 0.55f
        val sy = base + sin(a) * size * 0.12f
        val sr = size * r.range(0.07f, 0.11f)
        val p = pen()
        p.shader = LinearGradient(0f, sy - sr, 0f, sy + sr, hex(0x8A7E74), hex(0x3A3430), Shader.TileMode.CLAMP)
        canvas.drawOval(sx - sr * 1.3f, sy - sr, sx + sr * 1.3f, sy + sr * 0.7f, p)
    }
    logs(x, base, size * 0.9f, seed + 1)
    fire(x, base - size * 0.02f, size * 0.55f, size * 0.9f, seed + 2, sparks = 14)
}

/** A tent: its canvas pitched in a ridge, the door open and lit from inside. */
internal fun Painting.tent(x: Float, base: Float, size: Float, color: Int, lit: Float) {
    floorShadow(x, base, size * 0.8f, size * 0.12f, 0.3f)
    val p = pen()
    p.shader = sided(x, size * 0.6f, Tone.shade(color, -0.45f), Tone.shade(color, 0.12f))
    path.reset()
    path.moveTo(x - size * 0.62f, base)
    path.lineTo(x, base - size * 0.8f)
    path.lineTo(x + size * 0.62f, base)
    path.close()
    canvas.drawPath(path, p)
    if (lit > 0f) glow(x, base - size * 0.2f, size * 0.9f, 0xFFFFC870.toInt(), 0.45f * lit)
    path.reset()
    path.moveTo(x - size * 0.2f, base)
    path.lineTo(x, base - size * 0.55f)
    path.lineTo(x + size * 0.2f, base)
    path.close()
    canvas.drawPath(path, pen(Tone.mix(hex(0x2A1A10), hex(0xFFC878), lit * 0.8f)))
}

/**
 * A dacha: a wooden house with carved window frames and a glazed veranda along its front, its
 * roof of painted tin; [lit] warms the windows.
 */
internal fun Painting.dacha(x: Float, base: Float, size: Float, wall: Int, roof: Int, trim: Int, lit: Float) {
    val left = x - size * 0.5f
    val right = x + size * 0.5f
    val eaves = base - size * 0.55f
    floorShadow(x + size * 0.15f, base, size * 0.8f, size * 0.08f, 0.3f)
    val p = pen()
    p.shader = sided(x, size * 0.5f, Tone.shade(wall, -0.3f), Tone.shade(wall, 0.12f))
    canvas.drawRect(left, eaves, right, base, p)
    val boards = stroke(Tone.alpha(Tone.shade(wall, -0.5f), 0.4f), max(0.12f, size * 0.006f))
    var by = eaves
    while (by < base) {
        canvas.drawLine(left, by, right, by, boards)
        by += size * 0.05f
    }
    path.reset()
    path.moveTo(left - size * 0.08f, eaves + 0.3f)
    path.lineTo(x, eaves - size * 0.38f)
    path.lineTo(right + size * 0.08f, eaves + 0.3f)
    path.close()
    val rp = pen()
    rp.shader = sided(x, size * 0.6f, Tone.shade(roof, -0.3f), Tone.shade(roof, 0.12f))
    canvas.drawPath(path, rp)
    canvas.drawCircle(x, eaves - size * 0.17f, size * 0.07f, pen(trim))
    canvas.drawCircle(x, eaves - size * 0.17f, size * 0.05f, pen(Tone.mix(hex(0x2A3040), hex(0xFFD08A), lit)))
    // The veranda: many small panes, lit warm when the evening comes.
    val vt = eaves + size * 0.12f
    val vb = base - size * 0.08f
    val glass = Tone.mix(hex(0x5A7288), hex(0xFFD8A0), lit)
    canvas.drawRect(left + size * 0.08f, vt, right - size * 0.08f, vb, pen(glass))
    if (lit > 0f) glow(x, (vt + vb) / 2f, size * 0.7f, 0xFFFFC878.toInt(), 0.35f * lit)
    val bars = stroke(trim, max(0.15f, size * 0.015f))
    for (k in 0..8) {
        val bx = left + size * 0.08f + (size * 0.84f) * k / 8f
        canvas.drawLine(bx, vt, bx, vb, bars)
    }
    canvas.drawLine(left + size * 0.08f, (vt + vb) / 2f, right - size * 0.08f, (vt + vb) / 2f, bars)
    canvas.drawRect(left + size * 0.06f, vt - size * 0.02f, right - size * 0.06f, vt, pen(trim))
    canvas.drawRect(left + size * 0.06f, vb, right - size * 0.06f, vb + size * 0.02f, pen(trim))
}

/** A hammock slung between [x0] and [x1] at [y], sagging into a net. */
internal fun Painting.hammock(x0: Float, x1: Float, y: Float, sag: Float, color: Int) {
    val rope = stroke(Tone.shade(color, -0.3f), max(0.15f, sag * 0.03f))
    path.reset()
    path.moveTo(x0, y)
    path.quadTo((x0 + x1) / 2f, y + sag * 2f, x1, y)
    canvas.drawPath(path, rope)
    val p = pen()
    p.shader = LinearGradient(0f, y, 0f, y + sag, Tone.shade(color, 0.15f), Tone.shade(color, -0.25f), Shader.TileMode.CLAMP)
    path.reset()
    val a = x0 + (x1 - x0) * 0.18f
    val b = x1 - (x1 - x0) * 0.18f
    path.moveTo(a, y + sag * 0.55f)
    path.quadTo((a + b) / 2f, y + sag * 1.9f, b, y + sag * 0.55f)
    path.quadTo((a + b) / 2f, y + sag * 1.15f, a, y + sag * 0.55f)
    path.close()
    canvas.drawPath(path, p)
}

/** A samovar on a table under a cloth: brass shining, its chimney, a teapot crowning it, cups by it. */
internal fun Painting.samovar(x: Float, base: Float, size: Float) {
    val brass = pen()
    brass.shader = sided(x, size * 0.3f, hex(0x7A4E18), hex(0xFFE08A))
    canvas.drawOval(x - size * 0.3f, base - size * 0.72f, x + size * 0.3f, base - size * 0.18f, brass)
    canvas.drawRect(x - size * 0.12f, base - size * 0.2f, x + size * 0.12f, base, brass)
    canvas.drawRect(x - size * 0.2f, base - size * 0.04f, x + size * 0.2f, base, brass)
    canvas.drawRect(x - size * 0.05f, base - size * 0.95f, x + size * 0.05f, base - size * 0.7f, brass)
    canvas.drawOval(x - size * 0.16f, base - size * 1.12f, x + size * 0.16f, base - size * 0.9f, pen(hex(0xE8E0D4)))
    canvas.drawLine(x - size * 0.3f, base - size * 0.35f, x - size * 0.44f, base - size * 0.3f, stroke(hex(0xB8862E), size * 0.05f))
    glow(x + size * 0.1f, base - size * 0.5f, size * 0.2f, 0xFFFFF4C8.toInt(), 0.5f)
    steam(x, base - size * 1.12f, size * 0.8f, 0.7f, 5)
}

/** Garden beds running away in rows: dark soil, and plants in them — [grown] their size, with [berries] strawberries. */
internal fun Painting.gardenBeds(top: Float, bottom: Float, rows: Int, leaf: Int, soil: Int, seed: Int, berries: Boolean = false, grown: Float = 1f) {
    val r = Random(seed)
    for (k in 0 until rows) {
        val t0 = k / rows.toFloat()
        val t1 = (k + 0.62f) / rows
        val y0 = top + (bottom - top) * t0 * t0
        val y1 = top + (bottom - top) * t1 * t1
        val p = pen()
        p.shader = LinearGradient(0f, y0, 0f, y1, Tone.shade(soil, 0.1f), Tone.shade(soil, -0.25f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, y0, w, y1, p)
        val size = (y1 - y0) * 0.9f * grown
        var px = r.range(0f, size)
        while (px < w) {
            val q = pen()
            q.shader = RadialGradient(px, y0, size, Tone.shade(leaf, 0.2f), Tone.shade(leaf, -0.3f), Shader.TileMode.CLAMP)
            canvas.drawOval(px - size * 0.7f, y0 - size * 0.4f, px + size * 0.7f, y0 + size * 0.5f, q)
            if (berries && r.nextFloat() < 0.6f) canvas.drawCircle(px + size * 0.3f, y0 + size * 0.2f, max(0.3f, size * 0.14f), pen(hex(0xE0302A)))
            px += size * r.range(1.3f, 1.9f) / grown.coerceAtLeast(0.5f)
        }
    }
}

/** A greenhouse: its hooped frame and the glass lit where the sky shows in it. */
internal fun Painting.greenhouse(x: Float, base: Float, width: Float, height: Float) {
    path.reset()
    path.moveTo(x - width / 2f, base)
    path.lineTo(x - width / 2f, base - height * 0.55f)
    path.quadTo(x, base - height * 1.25f, x + width / 2f, base - height * 0.55f)
    path.lineTo(x + width / 2f, base)
    path.close()
    val g = pen()
    g.shader = LinearGradient(0f, base - height, 0f, base, 0x99E8F4FF.toInt(), 0x66B8D8C8, Shader.TileMode.CLAMP)
    canvas.drawPath(path, g)
    val frame = stroke(hex(0xF0F2F4), max(0.2f, width * 0.012f))
    canvas.drawPath(path, frame)
    for (k in 1..4) {
        val fx = x - width / 2f + width * k / 5f
        canvas.drawLine(fx, base, fx, base - height * (0.55f + 0.4f * (1f - abs(k - 2.5f) / 2.5f)), frame)
    }
}

/** A fern: fronds arching out of the ground, their leaflets catching the light. */
internal fun Painting.fern(x: Float, base: Float, size: Float, color: Int, seed: Int) {
    val r = Random(seed)
    repeat(6) {
        val a = -PI.toFloat() / 2f + r.range(-1.1f, 1.1f)
        val len = size * r.range(0.6f, 1f)
        val ex = x + cos(a) * len
        val ey = base + sin(a) * len * 0.8f
        val mx = x + cos(a) * len * 0.55f
        val my = base + sin(a) * len * 0.8f * 0.7f - len * 0.1f
        val stem = stroke(Tone.shade(color, -0.3f), max(0.15f, size * 0.015f))
        path.reset()
        path.moveTo(x, base)
        path.quadTo(mx, my, ex, ey)
        canvas.drawPath(path, stem)
        val leaflets = 10
        for (k in 1..leaflets) {
            val t = k / (leaflets + 1f)
            val u = 1f - t
            val px = u * u * x + 2f * u * t * mx + t * t * ex
            val py = u * u * base + 2f * u * t * my + t * t * ey
            val l = size * 0.14f * (1f - t * 0.8f)
            val leaf = stroke(Tone.shade(color, (t - 0.5f) * 0.3f), max(0.2f, size * 0.03f * (1f - t * 0.6f)))
            canvas.drawLine(px, py, px + cos(a - 1.2f) * l, py + sin(a - 1.2f) * l, leaf)
            canvas.drawLine(px, py, px + cos(a + 1.2f) * l, py + sin(a + 1.2f) * l, leaf)
        }
    }
}

/** A tea cup on its saucer, steaming. */
internal fun Painting.teaCup(x: Float, base: Float, size: Float, china: Int, pattern: Int) {
    floorShadow(x + size * 0.1f, base, size * 0.8f, size * 0.12f, 0.3f)
    canvas.drawOval(x - size * 0.7f, base - size * 0.14f, x + size * 0.7f, base + size * 0.06f, pen(Tone.shade(china, -0.1f)))
    canvas.drawOval(x - size * 0.62f, base - size * 0.16f, x + size * 0.62f, base + size * 0.02f, pen(china))
    val cup = pen()
    cup.shader = LinearGradient(x - size * 0.5f, 0f, x + size * 0.5f, 0f, Tone.shade(china, 0.1f), Tone.shade(china, -0.3f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.5f, base - size * 0.66f)
    path.quadTo(x - size * 0.46f, base - size * 0.08f, x, base - size * 0.08f)
    path.quadTo(x + size * 0.46f, base - size * 0.08f, x + size * 0.5f, base - size * 0.66f)
    path.close()
    canvas.drawPath(path, cup)
    canvas.drawLine(x - size * 0.46f, base - size * 0.5f, x + size * 0.46f, base - size * 0.5f, stroke(pattern, max(0.15f, size * 0.04f)))
    canvas.drawOval(x - size * 0.5f, base - size * 0.72f, x + size * 0.5f, base - size * 0.6f, pen(c(0x8A3A10)))
    canvas.drawArc(RectF(x + size * 0.4f, base - size * 0.6f, x + size * 0.78f, base - size * 0.26f), -90f, 180f, false, stroke(china, max(0.2f, size * 0.07f)))
    steam(x, base - size * 0.72f, size * 1.4f, 0.8f, (x * 3f).toInt())
}

// endregion

// region Streets

/**
 * A row of town houses standing on [ground]: facades of two to four storeys in [tones], windows
 * in rows — [lit] of them warm — cornices, roofs against the sky.
 */
internal fun Painting.facades(x0: Float, x1: Float, ground: Float, storey: Float, tones: IntArray, lit: Float, seed: Int) {
    val r = Random(seed)
    var x = x0
    while (x < x1) {
        val bw = storey * r.range(2.2f, 3.6f)
        val floors = 2 + r.nextInt(3)
        val top = ground - storey * floors
        val c = tones[r.nextInt(tones.size)]
        val p = pen()
        p.shader = LinearGradient(0f, top, 0f, ground, Tone.shade(c, 0.08f), Tone.shade(c, -0.25f), Shader.TileMode.CLAMP)
        canvas.drawRect(x, top, x + bw, ground, p)
        canvas.drawRect(x - storey * 0.05f, top - storey * 0.08f, x + bw + storey * 0.05f, top + storey * 0.04f, pen(Tone.shade(c, -0.35f)))
        val cols = (bw / (storey * 0.62f)).toInt().coerceAtLeast(2)
        for (f in 0 until floors) {
            for (k in 0 until cols) {
                val wx = x + bw * (k + 0.5f) / cols
                val wy = top + storey * (f + 0.35f)
                val ww = storey * 0.2f
                val on = r.nextFloat() < lit
                val glass = if (on) Tone.mix(hex(0xFFD08A), hex(0xFFB05A), r.nextFloat()) else Tone.mix(hex(0x2A3444), c, 0.25f)
                canvas.drawRect(wx - ww, wy, wx + ww, wy + storey * 0.42f, pen(glass))
                if (on) glow(wx, wy + storey * 0.2f, storey * 0.5f, 0xFFFFC070.toInt(), 0.25f)
            }
        }
        x += bw + storey * 0.02f
    }
}

/** A street lamp: an iron post, its lantern and the light round it. */
internal fun Painting.streetLamp(x: Float, base: Float, height: Float, lit: Float, color: Int = 0xFFFFD08A.toInt()) {
    val post = stroke(hex(0x1E2228), max(0.2f, height * 0.025f))
    canvas.drawLine(x, base, x, base - height, post)
    canvas.drawLine(x, base - height, x + height * 0.12f, base - height - height * 0.04f, post)
    val lx = x + height * 0.12f
    val ly = base - height + height * 0.02f
    if (lit > 0f) {
        glow(lx, ly, height * 1.1f, color, 0.4f * lit)
        glow(lx, ly, height * 0.25f, 0xFFFFF4DA.toInt(), 0.8f * lit)
    }
    canvas.drawRoundRect(lx - height * 0.05f, ly - height * 0.06f, lx + height * 0.05f, ly + height * 0.04f, height * 0.02f, height * 0.02f, pen(if (lit > 0f) 0xFFFFF0C8.toInt() else hex(0x3A3E44)))
}

/** A park bench: slatted seat and back on iron legs. */
internal fun Painting.bench(x: Float, base: Float, size: Float, wood: Int) {
    floorShadow(x, base, size * 0.55f, size * 0.08f, 0.3f)
    val iron = stroke(hex(0x1E2024), max(0.2f, size * 0.04f))
    canvas.drawLine(x - size * 0.4f, base, x - size * 0.4f, base - size * 0.55f, iron)
    canvas.drawLine(x + size * 0.4f, base, x + size * 0.4f, base - size * 0.55f, iron)
    for (k in 0 until 3) {
        val y = base - size * (0.28f + k * 0.14f)
        canvas.drawRect(x - size * 0.5f, y - size * 0.035f, x + size * 0.5f, y + size * 0.035f, pen(Tone.shade(wood, 0.1f - k * 0.1f)))
    }
    canvas.drawRect(x - size * 0.52f, base - size * 0.26f, x + size * 0.52f, base - size * 0.2f, pen(Tone.shade(wood, -0.2f)))
}

/** A kite high on the wind: a diamond of coloured panels and its tail of bows. */
internal fun Painting.kite(x: Float, y: Float, size: Float, a: Int, b: Int) {
    val quads = listOf(
        floatArrayOf(x, y - size, x - size * 0.6f, y - size * 0.2f, x, y - size * 0.2f) to a,
        floatArrayOf(x, y - size, x + size * 0.6f, y - size * 0.2f, x, y - size * 0.2f) to b,
        floatArrayOf(x - size * 0.6f, y - size * 0.2f, x, y + size * 0.8f, x, y - size * 0.2f) to b,
        floatArrayOf(x + size * 0.6f, y - size * 0.2f, x, y + size * 0.8f, x, y - size * 0.2f) to a,
    )
    for ((pts, c) in quads) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        path.lineTo(pts[2], pts[3])
        path.lineTo(pts[4], pts[5])
        path.close()
        canvas.drawPath(path, pen(c))
    }
    val string = stroke(0x99FFFFFF.toInt(), max(0.12f, size * 0.02f))
    path.reset()
    path.moveTo(x, y + size * 0.8f)
    path.cubicTo(x - size * 0.4f, y + size * 1.6f, x + size * 0.5f, y + size * 2.2f, x - size * 0.2f, y + size * 3.2f)
    canvas.drawPath(path, string)
    for (k in 1..4) {
        val t = k / 5f
        val bx = x + sin(t * 5f) * size * 0.3f
        val by = y + size * (0.8f + t * 2.3f)
        canvas.drawOval(bx - size * 0.12f, by - size * 0.05f, bx + size * 0.12f, by + size * 0.05f, pen(if (k % 2 == 0) a else b))
    }
}

/** Someone walking in the rain under an umbrella: a coat, legs mid-stride, the umbrella's dome and ribs. */
internal fun Painting.umbrellaPerson(x: Float, base: Float, size: Float, umbrella: Int, coat: Int) {
    floorShadow(x, base, size * 0.22f, size * 0.04f, 0.3f)
    val legs = stroke(hex(0x1E1E24), max(0.2f, size * 0.07f))
    canvas.drawLine(x - size * 0.03f, base - size * 0.36f, x - size * 0.09f, base, legs)
    canvas.drawLine(x + size * 0.03f, base - size * 0.36f, x + size * 0.1f, base, legs)
    val p = pen()
    p.shader = sided(x, size * 0.16f, Tone.shade(coat, -0.35f), Tone.shade(coat, 0.1f))
    path.reset()
    path.moveTo(x - size * 0.1f, base - size * 0.78f)
    path.lineTo(x + size * 0.1f, base - size * 0.78f)
    path.lineTo(x + size * 0.15f, base - size * 0.32f)
    path.lineTo(x - size * 0.15f, base - size * 0.32f)
    path.close()
    canvas.drawPath(path, p)
    canvas.drawCircle(x, base - size * 0.86f, size * 0.07f, pen(hex(0xD8B8A0)))
    canvas.drawLine(x + size * 0.02f, base - size * 0.66f, x + size * 0.02f, base - size * 1.08f, stroke(hex(0x2A2A2E), max(0.15f, size * 0.02f)))
    val top = base - size * 1.2f
    val dome = pen()
    dome.shader = LinearGradient(x - size * 0.4f, 0f, x + size * 0.4f, 0f, Tone.shade(umbrella, 0.2f), Tone.shade(umbrella, -0.35f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.42f, top + size * 0.2f)
    path.quadTo(x - size * 0.36f, top - size * 0.06f, x + size * 0.02f, top - size * 0.08f)
    path.quadTo(x + size * 0.4f, top - size * 0.06f, x + size * 0.46f, top + size * 0.2f)
    for (k in 3 downTo 0) {
        val sx = x - size * 0.42f + size * 0.88f * k / 4f
        path.quadTo(sx + size * 0.11f, top + size * 0.13f, sx, top + size * 0.2f)
    }
    path.close()
    canvas.drawPath(path, dome)
    canvas.drawLine(x + size * 0.02f, top - size * 0.08f, x + size * 0.02f, top - size * 0.16f, stroke(hex(0x2A2A2E), max(0.15f, size * 0.02f)))
}

// endregion

// region Festivals

/**
 * Maslenitsa's straw lady on her pole: a straw head in a red kerchief, arms of straw bundles
 * spread wide with ribbons flying from them, a sarafan in bright patches — and, when [burning],
 * the fire taking her from the foot, smoke going up behind.
 */
internal fun Painting.effigy(x: Float, base: Float, size: Float, burning: Float, seed: Int) {
    val r = Random(seed)
    val straw = hex(0xE8C46A)
    val strawShade = hex(0xA8823A)
    if (burning > 0f) {
        // Smoke rising and leaning with the wind.
        var sx = x
        var sy = base - size * 0.6f
        repeat(8) { k ->
            glow(sx, sy, size * (0.22f + k * 0.07f), hex(0x3A3440), 0.28f * (1f - k / 9f), mode = BlendMode.SRC_OVER)
            sx += size * r.range(0.06f, 0.14f)
            sy -= size * r.range(0.16f, 0.24f)
        }
    }
    canvas.drawLine(x, base + size * 0.05f, x, base - size * 1.34f, stroke(hex(0x4A3220), max(0.3f, size * 0.035f)))
    // Arms: bundles of straw tied at the wrists, the ribbons streaming from them.
    for (side in listOf(-1f, 1f)) {
        val sx = x + side * size * 0.46f
        val sy = base - size * 0.98f
        val arm = pen()
        arm.shader = LinearGradient(0f, sy - size * 0.05f, 0f, sy + size * 0.05f, straw, strawShade, Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x, base - size * 1.06f)
        path.quadTo((x + sx) / 2f, base - size * 1.08f, sx, sy - size * 0.03f)
        path.lineTo(sx + side * size * 0.06f, sy + size * 0.04f)
        path.quadTo((x + sx) / 2f, base - size * 0.94f, x, base - size * 0.94f)
        path.close()
        canvas.drawPath(path, arm)
        val bristle = stroke(Tone.alpha(strawShade, 0.7f), max(0.12f, size * 0.008f))
        repeat(6) { k ->
            val t = k / 5f
            canvas.drawLine(sx, sy, sx + side * size * (0.06f + t * 0.04f), sy + size * (-0.04f + t * 0.1f), bristle)
        }
        val ribbons = intArrayOf(hex(0xE0302A), hex(0x2A7AD8), hex(0xF2C230), hex(0x3AA84A))
        for (k in 0..2) {
            val c = ribbons[(k + (if (side > 0) 1 else 0)) % ribbons.size]
            val rib = stroke(c, max(0.2f, size * 0.018f))
            path.reset()
            path.moveTo(sx, sy)
            path.cubicTo(sx + size * 0.12f, sy + size * (0.05f + k * 0.04f), sx + size * 0.2f, sy - size * (0.02f + k * 0.03f), sx + size * (0.32f + k * 0.04f), sy + size * (0.02f + k * 0.05f))
            canvas.drawPath(path, rib)
        }
    }
    // The sarafan: a bell of patched cloth with folds, an apron over it.
    val hem = base - size * 0.38f
    val waist = base - size * 0.96f
    val skirt = pen()
    skirt.shader = LinearGradient(x - size * 0.34f, 0f, x + size * 0.34f, 0f, intArrayOf(hex(0x8A1E1A), hex(0xE0402E), hex(0xF06A3A), hex(0xB82A22)), floatArrayOf(0f, 0.35f, 0.55f, 1f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.12f, waist)
    path.lineTo(x + size * 0.12f, waist)
    path.quadTo(x + size * 0.22f, hem - size * 0.2f, x + size * 0.36f, hem)
    for (k in 5 downTo 0) {
        val fx = x - size * 0.36f + size * 0.72f * k / 6f
        path.quadTo(fx + size * 0.06f, hem + size * 0.04f, fx, hem)
    }
    path.quadTo(x - size * 0.22f, hem - size * 0.2f, x - size * 0.12f, waist)
    path.close()
    val body = android.graphics.Path(path)
    canvas.drawPath(body, skirt)
    canvas.withClip(body) {
        val fold = stroke(Tone.alpha(hex(0x5A1010), 0.4f), max(0.15f, size * 0.012f))
        for (k in -3..3) drawLine(x + k * size * 0.03f, waist, x + k * size * 0.1f, hem, fold)
        drawRect(x - size * 0.4f, hem - size * 0.08f, x + size * 0.4f, hem - size * 0.05f, pen(hex(0xF2C230)))
        drawRect(x - size * 0.4f, waist + size * 0.1f, x + size * 0.4f, waist + size * 0.13f, pen(hex(0x2A6AC8)))
    }
    val apron = pen(hex(0xF4EEE2))
    path.reset()
    path.moveTo(x - size * 0.08f, waist + size * 0.14f)
    path.lineTo(x + size * 0.08f, waist + size * 0.14f)
    path.lineTo(x + size * 0.14f, hem - size * 0.1f)
    path.lineTo(x - size * 0.14f, hem - size * 0.1f)
    path.close()
    canvas.drawPath(path, apron)
    val dots = pen(hex(0xE0302A))
    repeat(6) { canvas.drawCircle(x + r.range(-0.1f, 0.1f) * size, waist + size * r.range(0.2f, 0.5f), size * 0.014f, dots) }
    // The head: a straw bundle, a red kerchief with white spots knotted under it.
    val hy = base - size * 1.16f
    canvas.drawOval(x - size * 0.1f, hy - size * 0.12f, x + size * 0.1f, hy + size * 0.12f, pen(straw))
    val kerchief = pen()
    kerchief.shader = LinearGradient(x - size * 0.14f, 0f, x + size * 0.14f, 0f, hex(0xE83A30), hex(0xA82420), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.13f, hy + size * 0.02f)
    path.quadTo(x - size * 0.13f, hy - size * 0.17f, x, hy - size * 0.17f)
    path.quadTo(x + size * 0.13f, hy - size * 0.17f, x + size * 0.13f, hy + size * 0.02f)
    path.lineTo(x + size * 0.16f, hy + size * 0.2f)
    path.lineTo(x, hy + size * 0.1f)
    path.lineTo(x - size * 0.16f, hy + size * 0.2f)
    path.close()
    canvas.drawPath(path, kerchief)
    repeat(7) { canvas.drawCircle(x + r.range(-0.1f, 0.1f) * size, hy + r.range(-0.14f, 0.12f) * size, size * 0.012f, pen(hex(0xFFF4E8))) }
    if (burning > 0f) fire(x, base, size * 0.9f * burning, size * 0.62f * burning, seed + 1, sparks = 18)
}

/**
 * A wreath afloat on dark water: a ring of green leaves woven with field flowers, a candle burning
 * in its middle, the light running down the water under it.
 */
internal fun Painting.wreath(x: Float, y: Float, size: Float, seed: Int) {
    val r = Random(seed)
    glow(x, y + size * 0.2f, size * 1.6f, 0xFFFFB85A.toInt(), 0.25f, squash = 0.3f)
    val leaves = intArrayOf(hex(0x2E5A2A), hex(0x3E7A34), hex(0x4E8A3E), hex(0x1E3E1E))
    repeat(26) {
        val a = r.nextFloat() * 2f * PI.toFloat()
        val lx = x + cos(a) * size * 0.46f
        val ly = y + sin(a) * size * 0.15f
        canvas.withRotation(a * 57.3f + 90f, lx, ly) {
            canvas.drawOval(lx - size * 0.1f, ly - size * 0.035f, lx + size * 0.1f, ly + size * 0.035f, pen(leaves[r.nextInt(leaves.size)]))
        }
    }
    val flowers = intArrayOf(hex(0xF6F4EA), hex(0xFFD84A), hex(0x6A8AFF), hex(0xF6F4EA), hex(0xE86A9A))
    repeat(9) {
        val a = r.nextFloat() * 2f * PI.toFloat()
        val fx = x + cos(a) * size * 0.46f
        val fy = y + sin(a) * size * 0.15f - size * 0.03f
        val fr = size * r.range(0.045f, 0.07f)
        val c = flowers[r.nextInt(flowers.size)]
        canvas.drawCircle(fx, fy, fr, pen(c))
        canvas.drawCircle(fx, fy, fr * 0.35f, pen(if (c == hex(0xF6F4EA)) hex(0xF2C230) else Tone.shade(c, -0.3f)))
    }
    candle(x, y - size * 0.02f, size * 0.42f)
}

/** Lightning: a jagged bolt, its branches, and the glow round it. */
internal fun Painting.lightning(x: Float, y: Float, length: Float, seed: Int, strength: Float = 1f) {
    val r = Random(seed)
    fun bolt(x0: Float, y0: Float, len: Float, width: Float, depth: Int) {
        var px = x0
        var py = y0
        val steps = 9
        val pts = ArrayList<Pair<Float, Float>>()
        pts += px to py
        repeat(steps) {
            px += r.range(-0.5f, 0.5f) * len / steps * 1.4f
            py += len / steps
            pts += px to py
        }
        path.reset()
        path.moveTo(pts[0].first, pts[0].second)
        for (p in pts.drop(1)) path.lineTo(p.first, p.second)
        val glowStroke = stroke(Tone.alpha(0xFFB8C8FF.toInt(), 0.5f * strength), width * 4f)
        glowStroke.maskFilter = BlurMaskFilter(width * 3f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, glowStroke)
        canvas.drawPath(path, stroke(Tone.alpha(0xFFF4F6FF.toInt(), strength), width))
        if (depth > 0) {
            val k = 2 + r.nextInt(4)
            bolt(pts[k].first, pts[k].second, len * 0.45f, width * 0.55f, depth - 1)
        }
    }
    glow(x, y + length * 0.4f, length * 1.2f, 0xFFB8C8FF.toInt(), 0.35f * strength)
    bolt(x, y, length, max(0.35f, length * 0.012f), 2)
}

/** Icicles hanging from an eave: tapering spikes of clear ice, lit down one side, a drop at their tips. */
internal fun Painting.icicles(x0: Float, x1: Float, y: Float, count: Int, length: Float, seed: Int) {
    val r = Random(seed)
    repeat(count) {
        val x = x0 + (x1 - x0) * r.nextFloat()
        val len = length * r.range(0.3f, 1f)
        val hw = len * r.range(0.07f, 0.12f)
        val p = pen()
        p.shader = LinearGradient(x - hw, 0f, x + hw, 0f, intArrayOf(0xCCB8D4F0.toInt(), 0xF2FFFFFF.toInt(), 0xAA9ABCE0.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x - hw, y)
        path.quadTo(x - hw * 0.4f, y + len * 0.6f, x, y + len)
        path.quadTo(x + hw * 0.4f, y + len * 0.6f, x + hw, y)
        path.close()
        canvas.drawPath(path, p)
        if (r.nextFloat() < 0.4f) {
            canvas.drawCircle(x, y + len + hw * 1.6f, hw * 0.5f, pen(0xDDE8F4FF.toInt()))
            glow(x, y + len + hw * 1.6f, hw * 1.5f, 0xFFFFFFFF.toInt(), 0.5f)
        }
    }
}

// endregion

// region Close at hand

/** Snowdrops coming up through the last snow between [x0] and [x1]: nodding white bells on green stems, two leaves each. */
internal fun Painting.snowdrops(from: Float, to: Float, count: Int, size: Float, seed: Int, x0: Float = 0f, x1: Float = w) {
    val r = Random(seed)
    repeat(count) {
        val v = r.nextFloat().pow(0.7f)
        val y = from + (to - from) * v
        val x = x0 + r.nextFloat() * (x1 - x0)
        val s = size * (0.3f + v * 0.9f)
        val stem = stroke(hex(0x4E8A44), max(0.15f, s * 0.08f))
        path.reset()
        path.moveTo(x, y)
        path.quadTo(x - s * 0.1f, y - s * 1.6f, x + s * 0.3f, y - s * 2f)
        canvas.drawPath(path, stem)
        canvas.drawLine(x, y, x - s * 0.4f, y - s * 1.1f, stroke(hex(0x5E9A50), max(0.15f, s * 0.14f)))
        canvas.drawLine(x, y, x + s * 0.35f, y - s * 0.9f, stroke(hex(0x5E9A50), max(0.15f, s * 0.14f)))
        val bx = x + s * 0.32f
        val by = y - s * 1.75f
        val p = pen()
        p.shader = RadialGradient(bx - s * 0.1f, by, s * 0.5f, intArrayOf(0xFFFFFFFF.toInt(), hex(0xDDE6EE)), null, Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(bx - s * 0.22f, by)
        path.quadTo(bx - s * 0.3f, by + s * 0.55f, bx, by + s * 0.62f)
        path.quadTo(bx + s * 0.3f, by + s * 0.55f, bx + s * 0.22f, by)
        path.close()
        canvas.drawPath(path, p)
        canvas.drawCircle(bx, by - s * 0.02f, s * 0.08f, pen(hex(0x6AA850)))
    }
}

/** Pussy willow: reddish twigs rising, set with soft silver catkins. */
internal fun Painting.catkins(x: Float, base: Float, height: Float, count: Int, seed: Int) {
    val r = Random(seed)
    repeat(count) {
        val a = -PI.toFloat() / 2f + r.range(-0.55f, 0.55f)
        val len = height * r.range(0.6f, 1f)
        val ex = x + cos(a) * len
        val ey = base + sin(a) * len
        val twig = stroke(hex(0x7A3A2A), max(0.2f, height * 0.012f))
        path.reset()
        path.moveTo(x, base)
        path.quadTo((x + ex) / 2f + r.range(-0.05f, 0.05f) * len, (base + ey) / 2f, ex, ey)
        canvas.drawPath(path, twig)
        val buds = 5 + r.nextInt(4)
        for (k in 1..buds) {
            val t = k / (buds + 0.5f)
            val bx = x + (ex - x) * t + (if (k % 2 == 0) 1f else -1f) * height * 0.012f
            val by = base + (ey - base) * t
            val bw = height * 0.022f
            val p = pen()
            p.shader = RadialGradient(bx - bw * 0.3f, by - bw * 0.5f, bw * 1.6f, intArrayOf(0xFFFFFFFF.toInt(), hex(0xD8D8D0), hex(0x9A9A92)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.withRotation((a + PI.toFloat() / 2f) * 57.3f, bx, by) {
                canvas.drawOval(bx - bw, by - bw * 1.7f, bx + bw, by + bw * 1.7f, p)
            }
        }
    }
}

/** A mushroom: a birch bolete's brown cap on a pale stem — or a fly agaric, red with white flecks. */
internal fun Painting.mushroom(x: Float, base: Float, size: Float, agaric: Boolean) {
    floorShadow(x, base, size * 0.45f, size * 0.08f, 0.35f)
    val stem = pen()
    stem.shader = sided(x, size * 0.12f, hex(0xB8AE9E), hex(0xF4EEE2))
    path.reset()
    path.moveTo(x - size * 0.1f, base - size * 0.6f)
    path.quadTo(x - size * 0.16f, base - size * 0.2f, x - size * 0.14f, base)
    path.lineTo(x + size * 0.14f, base)
    path.quadTo(x + size * 0.16f, base - size * 0.2f, x + size * 0.1f, base - size * 0.6f)
    path.close()
    canvas.drawPath(path, stem)
    val capColor = if (agaric) hex(0xD8281E) else hex(0x7A4A26)
    val cap = pen()
    cap.shader = RadialGradient(x - size * 0.12f, base - size * 0.78f, size * 0.5f, intArrayOf(Tone.shade(capColor, 0.3f), capColor, Tone.shade(capColor, -0.35f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - size * 0.4f, base - size * 0.55f)
    path.cubicTo(x - size * 0.38f, base - size * 0.95f, x + size * 0.38f, base - size * 0.95f, x + size * 0.4f, base - size * 0.55f)
    path.quadTo(x, base - size * 0.5f, x - size * 0.4f, base - size * 0.55f)
    path.close()
    canvas.drawPath(path, cap)
    if (agaric) {
        val r = Random((x * 10).toInt())
        repeat(7) { canvas.drawCircle(x + r.range(-0.28f, 0.28f) * size, base - size * r.range(0.62f, 0.82f), size * 0.03f, pen(0xFFF8F2E8.toInt())) }
    }
}

/**
 * A branch reaching into the picture from [x0], [y0] to [x1], [y1]: bark lit along its top, a few
 * twigs, and — with [snow] — a soft load of snow along it.
 */
internal fun Painting.branch(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, bark: Int, seed: Int, snow: Boolean) {
    val r = Random(seed)
    val a = kotlin.math.atan2(y1 - y0, x1 - x0)
    val nx = -sin(a)
    val ny = cos(a)
    path.reset()
    path.moveTo(x0 + nx * width, y0 + ny * width)
    path.quadTo((x0 + x1) / 2f + nx * width * 0.6f, (y0 + y1) / 2f + ny * width * 0.6f + width, x1, y1)
    path.quadTo((x0 + x1) / 2f - nx * width * 0.6f, (y0 + y1) / 2f - ny * width * 0.6f + width, x0 - nx * width, y0 - ny * width)
    path.close()
    val p = pen()
    p.shader = LinearGradient(0f, y0 - width, 0f, y0 + width * 2f, Tone.shade(bark, 0.25f), Tone.shade(bark, -0.45f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, p)
    val tips = ArrayList<Float>()
    repeat(4) {
        val t = r.range(0.2f, 0.85f)
        val bx = x0 + (x1 - x0) * t
        val by = y0 + (y1 - y0) * t
        limb(bx, by, a + r.range(-0.9f, 0.9f), width * r.range(3f, 6f), width * 0.35f, 2, r, bark, 0.5f, tips)
    }
    if (snow) {
        val s = pen()
        s.shader = LinearGradient(0f, y0 - width * 1.6f, 0f, y0, 0xFFFFFFFF.toInt(), hex(0xC8D4EC), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x0, y0 - ny * width * 0.4f - width * 0.2f)
        path.quadTo((x0 + x1) / 2f, (y0 + y1) / 2f - width * 1.6f, x1, y1 - width * 0.3f)
        path.quadTo((x0 + x1) / 2f, (y0 + y1) / 2f - width * 0.2f, x0, y0 + width * 0.1f)
        path.close()
        canvas.drawPath(path, s)
    }
}

/** A cluster of rowan berries hanging from a twig: glossy red, each with its point of light. */
internal fun Painting.berries(x: Float, y: Float, radius: Float, count: Int, color: Int, seed: Int) {
    val r = Random(seed)
    canvas.drawLine(x, y - radius * 1.6f, x, y, stroke(hex(0x5A3A26), max(0.15f, radius * 0.12f)))
    repeat(count) {
        val bx = x + r.range(-1f, 1f) * radius
        val by = y + r.range(-0.6f, 0.9f) * radius
        val br = radius * r.range(0.22f, 0.3f)
        val p = pen()
        p.shader = RadialGradient(bx - br * 0.3f, by - br * 0.3f, br * 1.3f, intArrayOf(Tone.mix(color, 0xFFFFFFFF.toInt(), 0.35f), color, Tone.shade(color, -0.5f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, br, p)
        canvas.drawCircle(bx - br * 0.35f, by - br * 0.35f, br * 0.22f, pen(0xCCFFFFFF.toInt()))
    }
}

/** A bullfinch perched: grey back, rose-red breast, black cap and tail, a white bar on the wing. */
internal fun Painting.bullfinch(x: Float, y: Float, size: Float, facing: Float) {
    val f = if (facing >= 0f) 1f else -1f
    canvas.drawLine(x - f * size * 0.5f, y + size * 0.1f, x - f * size * 0.95f, y + size * 0.3f, stroke(hex(0x1A1A1E), size * 0.16f))
    val breast = pen()
    breast.shader = RadialGradient(x + f * size * 0.1f, y - size * 0.05f, size * 0.6f, intArrayOf(hex(0xF0605A), hex(0xD8403A), hex(0xA02A26)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    canvas.drawOval(x - size * 0.5f, y - size * 0.42f, x + size * 0.5f, y + size * 0.38f, breast)
    val back = pen()
    back.shader = LinearGradient(0f, y - size * 0.45f, 0f, y + size * 0.1f, hex(0x9AA0AA), hex(0x5E646E), Shader.TileMode.CLAMP)
    path.reset()
    path.moveTo(x - f * size * 0.52f, y + size * 0.1f)
    path.quadTo(x - f * size * 0.3f, y - size * 0.46f, x + f * size * 0.22f, y - size * 0.4f)
    path.quadTo(x - f * size * 0.05f, y - size * 0.05f, x - f * size * 0.52f, y + size * 0.1f)
    path.close()
    canvas.drawPath(path, back)
    canvas.drawLine(x - f * size * 0.42f, y - size * 0.05f, x - f * size * 0.02f, y - size * 0.2f, stroke(0xFFF2F2F4.toInt(), size * 0.07f))
    canvas.drawCircle(x + f * size * 0.36f, y - size * 0.42f, size * 0.24f, pen(hex(0x16161A)))
    canvas.drawOval(x + f * size * 0.26f, y - size * 0.34f, x + f * size * 0.5f, y - size * 0.18f, pen(hex(0xE0504A)))
    path.reset()
    path.moveTo(x + f * size * 0.56f, y - size * 0.46f)
    path.lineTo(x + f * size * 0.72f, y - size * 0.4f)
    path.lineTo(x + f * size * 0.56f, y - size * 0.34f)
    path.close()
    canvas.drawPath(path, pen(hex(0x2A2A2E)))
    canvas.drawCircle(x + f * size * 0.44f, y - size * 0.46f, size * 0.04f, pen(0xFFFFFFFF.toInt()))
}

/** A basket woven of willow, full of what the season gives: apples, mushrooms. */
internal fun Painting.basket(x: Float, base: Float, size: Float, contents: IntArray, seed: Int) {
    floorShadow(x, base, size * 0.7f, size * 0.1f, 0.35f)
    val r = Random(seed)
    repeat(6) {
        val cx = x + r.range(-0.4f, 0.4f) * size
        val cy = base - size * r.range(0.5f, 0.62f)
        val cr = size * r.range(0.14f, 0.2f)
        val c = contents[r.nextInt(contents.size)]
        val p = pen()
        p.shader = RadialGradient(cx - cr * 0.3f, cy - cr * 0.3f, cr * 1.4f, intArrayOf(Tone.mix(c, 0xFFFFFFFF.toInt(), 0.3f), c, Tone.shade(c, -0.4f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, cr, p)
    }
    val weave = pen()
    weave.shader = sided(x, size * 0.55f, hex(0x6A4A22), hex(0xC8A060))
    path.reset()
    path.moveTo(x - size * 0.55f, base - size * 0.5f)
    path.lineTo(x + size * 0.55f, base - size * 0.5f)
    path.lineTo(x + size * 0.42f, base)
    path.lineTo(x - size * 0.42f, base)
    path.close()
    canvas.drawPath(path, weave)
    val bands = stroke(Tone.alpha(hex(0x4A3216), 0.6f), max(0.15f, size * 0.03f))
    for (k in 1..3) {
        val y = base - size * 0.5f + size * 0.5f * k / 4f
        canvas.drawLine(x - size * 0.52f + size * 0.03f * k, y, x + size * 0.52f - size * 0.03f * k, y, bands)
    }
    val handle = stroke(hex(0x8A6230), max(0.2f, size * 0.05f))
    canvas.drawArc(RectF(x - size * 0.45f, base - size * 1.15f, x + size * 0.45f, base - size * 0.35f), 180f, 180f, false, handle)
}

/** A Lombardy poplar: a tall narrow column of leaves, lit down one side. */
internal fun Painting.poplar(x: Float, ground: Float, height: Float, tones: IntArray, seed: Int) {
    canvas.drawRect(x - height * 0.012f, ground - height * 0.25f, x + height * 0.012f, ground, pen(hex(0x3A322A)))
    val r = Random(seed)
    val blobs = ArrayList<Blob>()
    repeat(14) { k ->
        val t = 0.15f + 0.85f * k / 13f
        val cy = ground - height * t
        val cw = height * 0.09f * sin(t * PI.toFloat()).coerceAtLeast(0.35f)
        blobs += Blob(x + r.range(-0.3f, 0.3f) * cw, cy, cw, cw * 1.3f)
    }
    foliage(blobs, tones, seed, leaf = height * 0.012f, holes = 0.35f)
}

// endregion
