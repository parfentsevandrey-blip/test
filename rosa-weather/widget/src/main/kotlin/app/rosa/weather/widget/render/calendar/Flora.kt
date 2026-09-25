package app.rosa.weather.widget.render.calendar

import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The near things of the paintings — trees, grass, flowers, a house, haystacks — drawn as paths
 * and lit from the painting's light ([Painting.lightX]), so a sunlit flank is warm and bright and
 * the other side falls into shade.
 */

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

/** A light-to-shade gradient across a thing [half] wide at [x]: lit on the side of the light. */
private fun Painting.across(x: Float, half: Float, shade: Int, lit: Int): LinearGradient {
    val fromLeft = lightX < x
    return LinearGradient(x - half, 0f, x + half, 0f, if (fromLeft) lit else shade, if (fromLeft) shade else lit, Shader.TileMode.CLAMP)
}

/**
 * A limb and everything growing from it: a slightly bent stroke that forks [depth] more times,
 * thinning as it goes. Where it ends, its tip is added to [tips] (x, y pairs).
 */
internal fun Painting.limb(
    x: Float,
    y: Float,
    angle: Float,
    length: Float,
    width: Float,
    depth: Int,
    r: Random,
    color: Int,
    spread: Float,
    tips: MutableList<Float>,
    droop: Float = 0f,
) {
    val bend = r.range(-0.3f, 0.3f)
    val ex = x + cos(angle) * length
    val ey = y + sin(angle) * length + droop * length * 0.25f
    val s = stroke(color, max(0.12f, width))
    path.reset()
    path.moveTo(x, y)
    path.quadTo(x + cos(angle + bend) * length * 0.55f, y + sin(angle + bend) * length * 0.55f, ex, ey)
    canvas.drawPath(path, s)
    if (depth <= 0 || length < 0.9f) {
        tips += ex
        tips += ey
        return
    }
    val kids = if (r.nextFloat() < 0.35f) 3 else 2
    for (k in 0 until kids) {
        val a = angle + (k - (kids - 1) / 2f) * spread * r.range(0.6f, 1.25f) + r.range(-0.18f, 0.18f)
        limb(ex, ey, a, length * r.range(0.56f, 0.76f), width * 0.62f, depth - 1, r, color, spread, tips, droop)
    }
}

/**
 * A birch: a white trunk leaning a little, marked with the dark dashes of its bark and blackened
 * at the foot; branches rising and forking into fine twigs, long strands hanging from them. With
 * [crown] tones (dark to light) a lacy crown of small leaves gathers round the twigs.
 */
internal fun Painting.birch(
    x: Float,
    ground: Float,
    height: Float,
    trunkWidth: Float,
    seed: Int,
    lean: Float = 0f,
    bark: Int = Tone.of(0xF3F0E8),
    barkShade: Int = Tone.of(0x9DA6B8),
    mark: Int = Tone.of(0x2A2622),
    twig: Int = Tone.of(0x5B4A42),
    crown: IntArray? = null,
    leaves: Float = 0.6f,
    leafSize: Float = 0.8f,
    weeping: Float = 0.6f,
) {
    val r = Random(seed)
    val segs = 16
    val phase = r.range(0f, 6f)
    val xs = FloatArray(segs + 1) { i ->
        val t = i / segs.toFloat()
        x + lean * height * t + sin(t * 3.4f + phase) * trunkWidth * 0.5f * t
    }
    val ys = FloatArray(segs + 1) { i -> ground - height * i / segs }
    val ws = FloatArray(segs + 1) { i -> trunkWidth * (1f - 0.85f * i / segs) / 2f }
    path.reset()
    path.moveTo(xs[0] - ws[0], ys[0] + 0.5f)
    for (i in 0..segs) path.lineTo(xs[i] - ws[i], ys[i])
    for (i in segs downTo 0) path.lineTo(xs[i] + ws[i], ys[i])
    path.lineTo(xs[0] + ws[0], ys[0] + 0.5f)
    path.close()
    val trunkPath = android.graphics.Path(path)
    val p = pen()
    p.shader = across(x, trunkWidth * 0.6f, barkShade, bark)
    canvas.drawPath(trunkPath, p)
    // The bark's dark dashes, and the blackened, cracked foot.
    val m = pen()
    val count = (height / max(1f, trunkWidth) * 1.6f).roundToInt().coerceIn(6, 80)
    repeat(count) {
        val t = r.nextFloat().pow(0.8f) * 0.9f
        val i = (t * segs).toInt().coerceAtMost(segs - 1)
        val cx = xs[i] + (xs[i + 1] - xs[i]) * (t * segs - i)
        val cy = ground - height * t
        val half = ws[i] * r.range(0.25f, 0.9f)
        val off = r.range(-1f, 1f) * (ws[i] - half)
        m.color = Tone.alpha(mark, r.range(0.5f, 0.92f))
        val th = max(0.15f, trunkWidth * r.range(0.04f, 0.12f))
        canvas.drawRoundRect(cx + off - half, cy - th, cx + off + half, cy + th * 0.7f, th, th, m)
    }
    m.shader = LinearGradient(0f, ground, 0f, ground - height * 0.16f, Tone.alpha(mark, 0.9f), 0, Shader.TileMode.CLAMP)
    canvas.drawPath(trunkPath, m)
    m.shader = null
    // Branches: rising from the upper trunk at a sharp angle, forking into twigs.
    val tips = ArrayList<Float>()
    val branches = (5 + height / (trunkWidth * 5f)).roundToInt().coerceIn(5, 12)
    repeat(branches) { b ->
        val t = r.range(0.38f, 0.96f)
        val i = (t * segs).toInt().coerceAtMost(segs - 1)
        val side = if (b % 2 == 0) -1f else 1f
        val angle = -PI.toFloat() / 2f + side * r.range(0.3f, 0.75f)
        val len = height * r.range(0.1f, 0.2f) * (1.3f - t * 0.6f)
        limb(xs[i], ground - height * t, angle, len, max(0.3f, ws[i] * 0.5f), 3, r, Tone.alpha(twig, 0.92f), 0.42f, tips, droop = weeping * 0.6f)
    }
    // Long thin strands hanging from the twigs.
    if (weeping > 0f) {
        val hang = stroke(Tone.alpha(twig, 0.4f), 0.16f)
        var k = 0
        while (k < tips.size) {
            val tx = tips[k]
            val ty = tips[k + 1]
            if (r.nextFloat() < 0.8f) {
                val drop = height * r.range(0.03f, 0.1f) * weeping
                path.reset()
                path.moveTo(tx, ty)
                path.quadTo(tx + r.range(-0.6f, 0.6f), ty + drop * 0.5f, tx + r.range(-0.8f, 0.8f), ty + drop)
                canvas.drawPath(path, hang)
            }
            k += 2
        }
    }
    if (crown != null && leaves > 0f && tips.isNotEmpty()) {
        val blobs = ArrayList<Blob>()
        var k = 0
        while (k < tips.size) {
            if (r.nextFloat() < leaves) {
                val s = leafSize * r.range(2.5f, 5f)
                blobs += Blob(tips[k] + r.range(-1f, 1f), tips[k + 1] + s * 0.4f, s, s * 1.2f)
            }
            k += 2
        }
        foliage(blobs, crown, seed + 1, leafSize, holes = 0.85f)
    }
}

/**
 * A broad-leaved tree — oak, maple, lime, apple: a trunk forking into limbs, and a crown of lit
 * leaf clumps ([foliage]) built round [crownW] × [crownH]; [blossom] flowers it (an orchard in May),
 * [variety] mixes autumn hues, [back] makes it glow where the light is behind it.
 */
internal fun Painting.broadleaf(
    x: Float,
    ground: Float,
    height: Float,
    crownW: Float,
    crownH: Float,
    trunk: Int,
    tones: IntArray,
    seed: Int,
    leaf: Float = crownW * 0.028f,
    blossom: IntArray? = null,
    blossomAmount: Float = 0f,
    variety: IntArray? = null,
    back: Float = 0f,
    lumps: Int = 16,
    holes: Float = 0.45f,
) {
    val r = Random(seed)
    val cy = ground - height + crownH * 0.5f
    val base = crownW * 0.07f
    val fork = cy + crownH * 0.18f
    path.reset()
    path.moveTo(x - base, ground + 0.4f)
    path.cubicTo(x - base * 0.6f, ground - (ground - fork) * 0.4f, x - base * 0.45f, fork + (ground - fork) * 0.2f, x - base * 0.3f, fork)
    path.lineTo(x + base * 0.3f, fork)
    path.cubicTo(x + base * 0.45f, fork + (ground - fork) * 0.2f, x + base * 0.6f, ground - (ground - fork) * 0.4f, x + base, ground + 0.4f)
    path.close()
    val tp = pen()
    tp.shader = across(x, base, Tone.shade(trunk, -0.4f), Tone.shade(trunk, 0.18f))
    canvas.drawPath(path, tp)
    // Bark: fine vertical furrows.
    val bark = stroke(Tone.alpha(Tone.shade(trunk, -0.55f), 0.45f), max(0.15f, base * 0.06f))
    repeat((base * 3f).roundToInt().coerceIn(3, 30)) {
        val bx = x + r.range(-base * 0.8f, base * 0.8f)
        canvas.drawLine(bx, ground, bx + r.range(-0.3f, 0.3f) * base, fork + (ground - fork) * r.range(0.1f, 0.6f), bark)
    }
    // Limbs spreading into the crown, short enough to stay inside it.
    val tips = ArrayList<Float>()
    repeat(4 + r.nextInt(3)) {
        val a = -PI.toFloat() / 2f + r.range(-0.9f, 0.9f)
        limb(x, fork, a, crownH * r.range(0.14f, 0.22f), base * 0.55f, 1, r, Tone.shade(trunk, -0.15f), 0.5f, tips)
    }
    val blobs = crownBlobs(x, cy, crownW, crownH, lumps, seed)
    foliage(blobs, tones, seed, leaf, holes = holes, variety = variety, back = back, flowers = blossom, flowerAmount = blossomAmount)
}

/**
 * A Scots pine as Shishkin painted them: a tall bare trunk, orange where the light catches it
 * high up, and a crown of flat dark clumps at the top.
 */
internal fun Painting.pine(x: Float, ground: Float, height: Float, seed: Int, crown: IntArray, trunk: Int, trunkLit: Int) {
    val r = Random(seed)
    val segs = 10
    val bend = r.range(-0.03f, 0.03f) * height
    val xs = FloatArray(segs + 1) { i -> x + bend * sin(i / segs.toFloat() * PI.toFloat()) }
    val base = height * 0.022f
    path.reset()
    for (i in 0..segs) {
        val w = base * (1f - 0.75f * i / segs)
        if (i == 0) path.moveTo(xs[i] - w, ground + 0.4f) else path.lineTo(xs[i] - w, ground - height * i / segs)
    }
    for (i in segs downTo 0) path.lineTo(xs[i] + base * (1f - 0.75f * i / segs), if (i == 0) ground + 0.4f else ground - height * i / segs)
    path.close()
    val p = pen()
    p.shader = LinearGradient(0f, ground, 0f, ground - height, intArrayOf(Tone.shade(trunk, -0.3f), trunk, trunkLit), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    canvas.drawPath(path, p)
    val q = pen()
    q.shader = across(x, base * 1.4f, 0x66000000, 0)
    canvas.drawPath(path, q)
    // Crown: flat clumps of needles on short branches at the top, lit at their rims.
    val clumps = 5 + r.nextInt(4)
    val blobs = ArrayList<Blob>()
    repeat(clumps) { k ->
        val t = 0.66f + 0.34f * (k / (clumps - 1f)) + r.range(-0.03f, 0.03f)
        val cy = ground - height * t
        val side = if (k % 2 == 0) -1f else 1f
        val reach = height * r.range(0.05f, 0.12f) * (1.1f - t * 0.5f)
        val stem = xs[(t * segs).toInt().coerceAtMost(segs)]
        val cx = stem + side * reach * 0.6f
        canvas.drawLine(stem, cy + reach * 0.2f, cx, cy, stroke(trunk, base * 0.35f))
        val cw = reach * r.range(1.1f, 1.5f)
        blobs += Blob(cx, cy, cw, cw * r.range(0.3f, 0.42f))
        blobs += Blob(cx + side * cw * 0.35f, cy + cw * 0.08f, cw * 0.6f, cw * 0.24f)
    }
    foliage(blobs, crown, seed + 1, leaf = height * 0.006f, holes = 0.6f, back = 0.6f)
}

/**
 * Grass from [from] to [to]: blades nearer the bottom taller and more of them, bending with the
 * wind by [lean], tips catching the light.
 */
internal fun Painting.grass(
    from: Float,
    to: Float,
    count: Int,
    height: Float,
    colors: IntArray,
    seed: Int,
    lean: Float = 0.25f,
    width: Float = 0.4f,
    alpha: Float = 1f,
) {
    val r = Random(seed)
    val p = stroke(0, width)
    repeat(count) {
        val v = r.nextFloat().pow(0.6f)
        val y = from + (to - from) * v
        val x = r.nextFloat() * w
        val hgt = height * (0.35f + 0.65f * v) * r.range(0.5f, 1.2f)
        val bend = (lean + r.range(-0.25f, 0.25f)) * hgt
        p.strokeWidth = width * (0.5f + v) * r.range(0.7f, 1.2f)
        p.color = Tone.alpha(colors[r.nextInt(colors.size)], alpha * r.range(0.7f, 1f))
        path.reset()
        path.moveTo(x, y)
        path.quadTo(x + bend * 0.2f, y - hgt * 0.6f, x + bend, y - hgt)
        canvas.drawPath(path, p)
    }
}

/** Kinds of wild flowers: petals and heart. */
internal enum class Bloom(val petals: Int, val petal: Int, val heart: Int, val round: Boolean = false) {
    Chamomile(9, Tone.of(0xFFFFFF), Tone.of(0xF4C430)),
    Cornflower(7, Tone.of(0x4F74E3), Tone.of(0x2D3F9E)),
    Dandelion(0, Tone.of(0xFFD42E), Tone.of(0xF2B705), round = true),
    Poppy(5, Tone.of(0xE0332B), Tone.of(0x2A1A1A)),
    Clover(0, Tone.of(0xE58BC0), Tone.of(0xC0628F), round = true),
    Buttercup(5, Tone.of(0xFFE14A), Tone.of(0xE0A800)),
}

/**
 * Wild flowers scattered from [from] to [to]: dots far off, open flowers with petals near the
 * bottom, each on a thin stem.
 */
internal fun Painting.flowers(from: Float, to: Float, count: Int, kinds: List<Bloom>, seed: Int, size: Float = 1.3f, stem: Int = Tone.of(0x4A7A3A)) {
    val r = Random(seed)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val s = stroke(stem, 0.25f)
    repeat(count) {
        val v = r.nextFloat().pow(0.7f)
        val y = from + (to - from) * v
        val x = r.nextFloat() * w
        val kind = kinds[r.nextInt(kinds.size)]
        val sz = size * (0.25f + v * 0.9f) * r.range(0.75f, 1.25f)
        if (sz > 0.7f) {
            s.strokeWidth = 0.18f + sz * 0.08f
            canvas.drawLine(x, y + sz * 1.2f, x + r.range(-0.4f, 0.4f), y + sz * 4f, s)
        }
        if (sz < 0.75f || kind.round || kind.petals == 0) {
            p.color = kind.petal
            canvas.drawCircle(x, y, max(0.3f, sz * 0.75f), p)
            if (sz > 0.9f) {
                p.color = Tone.shade(kind.petal, 0.35f)
                canvas.drawCircle(x - sz * 0.2f, y - sz * 0.25f, sz * 0.3f, p)
            }
            return@repeat
        }
        val turn = r.nextFloat() * 6.28f
        val tilt = r.range(0.45f, 0.9f)
        p.color = kind.petal
        for (k in 0 until kind.petals) {
            val a = turn + k * 2f * PI.toFloat() / kind.petals
            val px = x + cos(a) * sz * 0.62f
            val py = y + sin(a) * sz * 0.62f * tilt
            canvas.drawCircle(px, py, sz * 0.38f, p)
        }
        p.color = kind.heart
        canvas.drawCircle(x, y, sz * 0.36f, p)
    }
}

/**
 * Ripe grain from [from] to [to]: a texture of stalks that coarsens towards the viewer, heavy ears
 * nodding in the foreground, the side of each ear towards the light glowing.
 */
internal fun Painting.wheat(from: Float, to: Float, count: Int, seed: Int, stalk: Int, ear: Int, earLit: Int, height: Float) {
    val r = Random(seed)
    val s = stroke(stalk, 0.3f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(count) {
        val v = r.nextFloat().pow(0.55f)
        val y = from + (to - from) * v
        val x = r.nextFloat() * w
        val hgt = height * (0.15f + 0.85f * v * v) * r.range(0.8f, 1.2f)
        val nod = r.range(-0.1f, 0.3f) * hgt
        s.strokeWidth = 0.15f + v * 0.4f
        s.color = Tone.alpha(stalk, 0.6f + 0.4f * v)
        path.reset()
        path.moveTo(x, y)
        path.quadTo(x + nod * 0.2f, y - hgt * 0.6f, x + nod, y - hgt)
        canvas.drawPath(path, s)
        if (v > 0.35f) {
            // The ear: a column of grains.
            val grains = 5 + (v * 4f).toInt()
            val size = 0.35f + v * 0.75f
            val lit = (lightX - x) * (lightX - (x + nod)) >= 0f
            for (g in 0 until grains) {
                val gy = y - hgt - g * size * 0.9f
                val gx = x + nod + g * nod * 0.05f
                p.color = if (g % 2 == 0 == lit) earLit else ear
                canvas.drawOval(gx - size * 0.45f, gy - size * 0.7f, gx + size * 0.45f, gy + size * 0.7f, p)
            }
        }
    }
}

/**
 * A log house: timber walls, a pitched roof (under snow in winter), windows glowing warm with
 * their light spilling out, and smoke rising from the chimney.
 */
internal fun Painting.house(x: Float, ground: Float, width: Float, wall: Int, roof: Int, seed: Int, snowRoof: Int = 0, window: Int = 0, smoke: Int = 0) {
    val r = Random(seed)
    val wh = width * 0.55f
    val left = x - width / 2f
    val right = x + width / 2f
    val wallTop = ground - wh
    val p = pen()
    p.shader = across(x, width / 2f, Tone.shade(wall, -0.3f), Tone.shade(wall, 0.1f))
    canvas.drawRect(left, wallTop, right, ground + 0.3f, p)
    // Logs.
    val logs = stroke(Tone.alpha(Tone.shade(wall, -0.5f), 0.55f), 0.18f)
    var ly = wallTop + wh / 7f
    while (ly < ground) {
        canvas.drawLine(left, ly, right, ly, logs)
        ly += wh / 7f
    }
    // Windows.
    if (window != 0) {
        val ww = width * 0.14f
        for (k in listOf(-1, 1)) {
            val wx = x + k * width * 0.22f
            val wy = wallTop + wh * 0.3f
            glow(wx, wy + ww * 0.6f, ww * 5f, window, 0.45f)
            canvas.drawRect(wx - ww / 2f, wy, wx + ww / 2f, wy + ww * 1.2f, pen(window))
            val frame = stroke(Tone.shade(wall, -0.55f), 0.22f, round = false)
            canvas.drawRect(wx - ww / 2f, wy, wx + ww / 2f, wy + ww * 1.2f, frame)
            canvas.drawLine(wx, wy, wx, wy + ww * 1.2f, frame)
            canvas.drawLine(wx - ww / 2f, wy + ww * 0.5f, wx + ww / 2f, wy + ww * 0.5f, frame)
        }
    }
    // Roof.
    val peak = wallTop - width * 0.42f
    path.reset()
    path.moveTo(left - width * 0.1f, wallTop + 0.4f)
    path.lineTo(x, peak)
    path.lineTo(right + width * 0.1f, wallTop + 0.4f)
    path.close()
    val q = pen()
    q.shader = across(x, width * 0.6f, Tone.shade(roof, -0.25f), roof)
    canvas.drawPath(path, q)
    if (snowRoof != 0) {
        path.reset()
        path.moveTo(left - width * 0.14f, wallTop + 0.8f)
        path.lineTo(x, peak - width * 0.05f)
        path.lineTo(right + width * 0.14f, wallTop + 0.8f)
        path.quadTo(x + width * 0.3f, wallTop - width * 0.05f, x, peak + width * 0.12f)
        path.quadTo(x - width * 0.3f, wallTop - width * 0.05f, left - width * 0.14f, wallTop + 0.8f)
        path.close()
        val s = pen()
        s.shader = across(x, width * 0.6f, Tone.shade(snowRoof, -0.18f), snowRoof)
        canvas.drawPath(path, s)
    }
    // Chimney and smoke.
    val cx = x + width * 0.22f
    canvas.drawRect(cx - width * 0.05f, peak + width * 0.1f, cx + width * 0.05f, wallTop - width * 0.12f, pen(Tone.shade(wall, -0.35f)))
    if (smoke != 0) {
        var sx = cx
        var sy = peak + width * 0.06f
        repeat(9) { k ->
            val size = width * (0.08f + k * 0.05f)
            glow(sx, sy, size * 2.2f, smoke, 0.38f * (1f - k / 10f), mode = BlendMode.SRC_OVER)
            sx += width * r.range(0.04f, 0.14f)
            sy -= width * r.range(0.14f, 0.22f)
        }
    }
}

/** A haystack: a tall rounded rick of hay, straw lines on it, its shadow side deep, a pole on top. */
internal fun Painting.haystack(x: Float, ground: Float, width: Float, height: Float, tone: Int, shade: Int, seed: Int) {
    val r = Random(seed)
    val hw = width / 2f
    path.reset()
    path.moveTo(x - hw * 0.9f, ground + 0.3f)
    path.cubicTo(x - hw * 1.12f, ground - height * 0.45f, x - hw * 0.55f, ground - height * 0.95f, x, ground - height)
    path.cubicTo(x + hw * 0.55f, ground - height * 0.95f, x + hw * 1.12f, ground - height * 0.45f, x + hw * 0.9f, ground + 0.3f)
    path.close()
    val body = android.graphics.Path(path)
    val p = pen()
    p.shader = across(x, hw, shade, tone)
    canvas.drawPath(body, p)
    val s = stroke(Tone.alpha(Tone.shade(tone, -0.4f), 0.5f), 0.16f)
    val lit = stroke(Tone.alpha(Tone.shade(tone, 0.25f), 0.35f), 0.14f)
    canvas.withClip(body) {
        repeat((width * 2.2f).roundToInt().coerceIn(10, 90)) {
            val u = r.range(-1f, 1f)
            val v = r.range(0f, 1f)
            val sx = x + u * hw
            val sy = ground - height * v
            drawLine(sx, sy, sx + u * hw * 0.15f + r.range(-0.4f, 0.4f), sy + height * r.range(0.06f, 0.16f), if (r.nextFloat() < 0.3f) lit else s)
        }
    }
    canvas.drawLine(x, ground - height, x + width * 0.02f, ground - height * 1.14f, stroke(Tone.shade(shade, -0.3f), 0.3f))
}

/** A white church on the horizon: a small nave, a bell tower, onion domes. */
internal fun Painting.church(x: Float, ground: Float, size: Float, wall: Int, dome: Int) {
    val p = pen(wall)
    canvas.drawRect(x - size * 0.6f, ground - size * 0.55f, x + size * 0.3f, ground + 0.2f, p)
    canvas.drawRect(x + size * 0.3f, ground - size * 1.25f, x + size * 0.62f, ground + 0.2f, p)
    val d = pen(dome)
    fun onion(cx: Float, by: Float, r: Float) {
        path.reset()
        path.moveTo(cx - r, by)
        path.cubicTo(cx - r * 1.3f, by - r * 1.2f, cx - r * 0.1f, by - r * 1.4f, cx, by - r * 2.1f)
        path.cubicTo(cx + r * 0.1f, by - r * 1.4f, cx + r * 1.3f, by - r * 1.2f, cx + r, by)
        path.close()
        canvas.drawPath(path, d)
        canvas.drawLine(cx, by - r * 2.1f, cx, by - r * 2.8f, stroke(dome, max(0.12f, r * 0.12f)))
    }
    onion(x - size * 0.15f, ground - size * 0.55f, size * 0.22f)
    onion(x + size * 0.46f, ground - size * 1.25f, size * 0.15f)
}

/** Birds far off: small bent strokes in a loose flock. */
internal fun Painting.birds(x: Float, y: Float, count: Int, spread: Float, size: Float, color: Int, seed: Int) {
    val r = Random(seed)
    val s = stroke(color, max(0.2f, size * 0.12f))
    repeat(count) {
        val bx = x + r.range(-spread, spread)
        val by = y + r.range(-spread * 0.35f, spread * 0.35f)
        val sz = size * r.range(0.6f, 1.2f)
        val flap = r.range(-0.3f, 0.5f)
        path.reset()
        path.moveTo(bx - sz, by - sz * (0.2f + flap))
        path.quadTo(bx - sz * 0.45f, by - sz * 0.35f, bx, by)
        path.quadTo(bx + sz * 0.45f, by - sz * 0.35f, bx + sz, by - sz * (0.2f + flap))
        canvas.drawPath(path, s)
    }
}

/** Sparkles on snow or water: tiny bright points, a few with a cross of light. */
internal fun Painting.sparkles(from: Float, to: Float, count: Int, color: Int, seed: Int, size: Float = 0.35f) {
    val r = Random(seed)
    val p = pen()
    repeat(count) {
        val x = r.nextFloat() * w
        val y = from + (to - from) * r.nextFloat().pow(0.8f)
        val a = r.range(0.4f, 1f)
        val s = size * r.range(0.5f, 1.4f)
        p.color = Tone.alpha(color, a)
        canvas.drawCircle(x, y, s, p)
        if (r.nextFloat() < 0.18f) {
            val l = s * r.range(3f, 6f)
            val q = stroke(Tone.alpha(color, a * 0.7f), 0.15f)
            canvas.drawLine(x - l, y, x + l, y, q)
            canvas.drawLine(x, y - l, x, y + l, q)
            pen()
        }
    }
}

/**
 * Snow falling: flakes in depth — far ones small and sharp, near ones large, soft and faint,
 * as a lens focused on the distance sees them.
 */
internal fun Painting.snowfall(count: Int, seed: Int, size: Float = 1f, top: Float = 0f, bottom: Float = h) {
    val r = Random(seed)
    val p = pen()
    repeat(count) {
        val depth = r.nextFloat().pow(2f)
        val x = r.nextFloat() * w
        val y = top + r.nextFloat() * (bottom - top)
        val s = size * (0.35f + depth * 2.4f)
        if (depth > 0.55f) {
            p.maskFilter = BlurMaskFilter(s * 0.6f, BlurMaskFilter.Blur.NORMAL)
            p.color = Tone.alpha(0xFFFFFFFF.toInt(), 0.35f + (1f - depth) * 0.3f)
        } else {
            p.maskFilter = null
            p.color = Tone.alpha(0xFFFFFFFF.toInt(), 0.55f + depth * 0.4f)
        }
        canvas.drawCircle(x, y, s, p)
    }
}

/** Leaves on the air: small lit shapes turning as they fall. */
internal fun Painting.fallingLeaves(count: Int, colors: IntArray, seed: Int, size: Float = 1.6f, top: Float = 0f, bottom: Float = h) {
    val r = Random(seed)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(count) {
        val depth = r.nextFloat()
        val x = r.nextFloat() * w
        val y = top + r.nextFloat() * (bottom - top)
        val s = size * (0.6f + depth * 1.4f)
        val a = r.nextFloat() * 360f
        p.color = colors[r.nextInt(colors.size)]
        p.maskFilter = if (depth > 0.85f) BlurMaskFilter(s * 0.35f, BlurMaskFilter.Blur.NORMAL) else null
        path.reset()
        path.moveTo(x - s, y)
        path.quadTo(x - s * 0.1f, y - s * 0.7f, x + s, y)
        path.quadTo(x - s * 0.1f, y + s * 0.7f, x - s, y)
        canvas.withRotation(a, x, y) { drawPath(path, p) }
    }
}

/** Blossom petals on the wind: pale, tilted ovals. */
internal fun Painting.fallingPetals(count: Int, colors: IntArray, seed: Int, size: Float = 1.1f) {
    val r = Random(seed)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(count) {
        val depth = r.nextFloat()
        val x = r.nextFloat() * w
        val y = r.nextFloat() * h
        val s = size * (0.6f + depth * 1.3f)
        p.color = colors[r.nextInt(colors.size)]
        p.maskFilter = if (depth > 0.85f) BlurMaskFilter(s * 0.4f, BlurMaskFilter.Blur.NORMAL) else null
        canvas.withRotation(r.nextFloat() * 180f, x, y) { drawOval(x - s, y - s * 0.6f, x + s, y + s * 0.6f, p) }
    }
}

/** Fireflies or motes of light between [top] and [bottom]: a hot point in a soft halo. */
internal fun Painting.fireflies(top: Float, bottom: Float, count: Int, color: Int, seed: Int, size: Float = 0.6f) {
    val r = Random(seed)
    repeat(count) {
        val x = r.nextFloat() * w
        val y = top + r.nextFloat() * (bottom - top)
        val s = size * r.range(0.6f, 1.4f)
        val a = r.range(0.4f, 1f)
        glow(x, y, s * 9f, color, 0.45f * a)
        canvas.drawCircle(x, y, s, pen(Tone.alpha(Tone.mix(color, 0xFFFFFFFF.toInt(), 0.6f), a)))
    }
}

/** Reeds by the water: thin stems, a few with dark heads. */
internal fun Painting.reeds(x0: Float, x1: Float, ground: Float, count: Int, height: Float, color: Int, head: Int, seed: Int) {
    val r = Random(seed)
    val s = stroke(color, 0.3f)
    val p = pen(head)
    repeat(count) {
        val x = x0 + r.nextFloat() * (x1 - x0)
        val hgt = height * r.range(0.4f, 1.1f)
        val bend = r.range(-0.15f, 0.25f) * hgt
        s.strokeWidth = r.range(0.2f, 0.4f)
        path.reset()
        path.moveTo(x, ground)
        path.quadTo(x + bend * 0.3f, ground - hgt * 0.6f, x + bend, ground - hgt)
        canvas.drawPath(path, s)
        if (r.nextFloat() < 0.3f) canvas.drawOval(x + bend - 0.45f, ground - hgt - 0.2f, x + bend + 0.45f, ground - hgt + hgt * 0.14f, p)
    }
}

/** A rail fence along [y] from [x0] to [x1], leaning with age. */
internal fun Painting.fence(x0: Float, x1: Float, y: Float, height: Float, color: Int, seed: Int, snow: Int = 0) {
    val r = Random(seed)
    val s = stroke(color, max(0.3f, height * 0.08f))
    val posts = ((x1 - x0) / (height * 1.6f)).roundToInt().coerceAtLeast(2)
    var prevTop = 0f
    for (k in 0..posts) {
        val x = x0 + (x1 - x0) * k / posts
        val tilt = r.range(-0.12f, 0.12f) * height
        canvas.drawLine(x, y, x + tilt, y - height, s)
        if (snow != 0) canvas.drawOval(x + tilt - height * 0.1f, y - height - height * 0.08f, x + tilt + height * 0.1f, y - height + height * 0.04f, pen(snow))
        if (k > 0) {
            val px = x0 + (x1 - x0) * (k - 1) / posts
            canvas.drawLine(px, y - height * 0.45f, x, y - height * 0.5f + r.range(-0.3f, 0.3f), stroke(color, max(0.25f, height * 0.06f)))
            canvas.drawLine(px, prevTop + height * 0.12f, x, y - height * 0.86f, stroke(color, max(0.25f, height * 0.06f)))
        }
        prevTop = y - height
    }
    pen()
}
