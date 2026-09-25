package app.rosa.weather.widget.render.calendar

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The per-pixel parts of the paintings: crowns of leaves, spruces, the wall of a forest. Each
 * pixel is worked out from a shape, a surface normal and the painting's light, so a crown is a
 * volume of lit clumps and a spruce a stack of branch tiers carrying snow — not a flat cut-out.
 */

/** A lump of a crown: an ellipse at ([x], [y]) with radii [rx], [ry]. */
internal class Blob(val x: Float, val y: Float, val rx: Float, val ry: Float)

/** [tones] as a ramp from the first (v = 0) to the last (v = 1), blended between neighbours. */
internal fun ramp(tones: IntArray, v: Float): Int {
    val f = v.coerceIn(0f, 1f) * (tones.size - 1)
    val i = floor(f).toInt().coerceAtMost(tones.size - 2)
    return Tone.mix(tones[i], tones[i + 1], f - i)
}

/**
 * A crown of leaves, per pixel: the union of [blobs], its edge broken into leafy clumps and
 * holes, each clump rounded and lit from the painting's light, darker in the hollows and
 * underneath, glowing at the rim when [back] light shines through it. [tones] run from the
 * deepest shade to full light; [variety] mixes other hues in by patches (an autumn crown).
 */
internal fun Painting.foliage(
    blobs: List<Blob>,
    tones: IntArray,
    seed: Int,
    leaf: Float,
    holes: Float = 0.5f,
    back: Float = 0f,
    backTone: Int = 0xFFFFE8B0.toInt(),
    variety: IntArray? = null,
    varietyScale: Float = leaf * 5f,
    varietyAmount: Float = 0.7f,
    flowers: IntArray? = null,
    flowerAmount: Float = 0f,
    alpha: Float = 1f,
) {
    if (blobs.isEmpty()) return
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    var maxR = 0f
    for (bl in blobs) {
        l = min(l, bl.x - bl.rx)
        t = min(t, bl.y - bl.ry)
        r = max(r, bl.x + bl.rx)
        b = max(b, bl.y + bl.ry)
        maxR = max(maxR, max(bl.rx, bl.ry))
    }
    val pad = leaf * 1.5f
    val area = RectF(l - pad, t - pad, r + pad, b + pad)
    val n = blobs.size
    val bx = FloatArray(n) { blobs[it].x }
    val by = FloatArray(n) { blobs[it].y }
    val irx = FloatArray(n) { 1f / blobs[it].rx }
    val iry = FloatArray(n) { 1f / blobs[it].ry }
    // A grid over the crown: each cell knows the blobs that reach into it.
    val cell = max(maxR, leaf * 4f)
    val cols = max(1, ceil(area.width() / cell).toInt())
    val rows = max(1, ceil(area.height() / cell).toInt())
    val grid = Array(cols * rows) { IntArray(0) }
    run {
        val lists = Array(cols * rows) { ArrayList<Int>() }
        for (i in 0 until n) {
            val c0 = floor((bx[i] - blobs[i].rx * 1.3f - area.left) / cell).toInt().coerceIn(0, cols - 1)
            val c1 = floor((bx[i] + blobs[i].rx * 1.3f - area.left) / cell).toInt().coerceIn(0, cols - 1)
            val r0 = floor((by[i] - blobs[i].ry * 1.3f - area.top) / cell).toInt().coerceIn(0, rows - 1)
            val r1 = floor((by[i] + blobs[i].ry * 1.3f - area.top) / cell).toInt().coerceIn(0, rows - 1)
            for (rr in r0..r1) for (cc in c0..c1) lists[rr * cols + cc].add(i)
        }
        for (k in lists.indices) grid[k] = lists[k].toIntArray()
    }
    val cx = (l + r) / 2f
    val cy = (t + b) / 2f
    val top = t
    val height = max(1f, b - t)
    var lx = lightX - cx
    var ly = lightY - cy
    val len = max(1f, hypot(lx, ly))
    lx /= len
    ly /= len
    // The light comes a little from the front and from above.
    var ldx = lx * 0.72f
    var ldy = ly * 0.72f - 0.3f
    var ldz = 0.62f
    val ll = sqrt(ldx * ldx + ldy * ldy + ldz * ldz)
    ldx /= ll
    ldy /= ll
    ldz /= ll
    val px = 1f / kx
    val clump = Noise.Cell()
    val bud = Noise.Cell()
    val clumpSize = leaf * 2.3f
    pixels(area) { x, y ->
        val ci = floor((x - area.left) / cell).toInt().coerceIn(0, cols - 1)
        val ri = floor((y - area.top) / cell).toInt().coerceIn(0, rows - 1)
        val list = grid[ri * cols + ci]
        var best = -9f
        var bi = -1
        for (i in list) {
            val dx = (x - bx[i]) * irx[i]
            val dy = (y - by[i]) * iry[i]
            val f = 1f - dx * dx - dy * dy
            if (f > best) {
                best = f
                bi = i
            }
        }
        if (bi < 0 || best < -holes) return@pixels 0
        // Clumps of leaves round the points of a warped cellular grid (so no two are alike);
        // their borders are soft crevices.
        val wx = (Noise.value(x / (clumpSize * 1.3f), y / (clumpSize * 1.3f), seed + 21) - 0.5f) * 0.9f
        val wy = (Noise.value(x / (clumpSize * 1.3f), y / (clumpSize * 1.3f), seed + 22) - 0.5f) * 0.9f
        Noise.cellular(x / clumpSize + wx, y / clumpSize + wy, seed, clump)
        val bump = 1f - Tone.smooth(0f, 0.85f, clump.f1)
        val crevice = Tone.smooth(0f, 0.35f, clump.f2 - clump.f1)
        val micro = Noise.fbm(x / (leaf * 0.5f), y / (leaf * 0.5f), seed + 5, 2)
        val edge = best + (bump - 0.55f) * holes * 1.7f + (micro - 0.5f) * holes * 0.35f
        // Coverage, anti-aliased over about a pixel at the scale of a clump.
        val aa = 1.6f * px / clumpSize + 2f * irx[bi] * px
        val a = (edge / aa).coerceIn(0f, 1f)
        if (a <= 0f) return@pixels 0
        val dx = (x - bx[bi]) * irx[bi]
        val dy = (y - by[bi]) * iry[bi]
        val dz = sqrt(max(0f, 1f - dx * dx - dy * dy))
        // The surface: the crown's roundness, bent by each clump's own dome and the leaves on it.
        val cz = sqrt(max(0f, 1f - clump.f1 * clump.f1 * 1.4f))
        val leafTilt = Noise.value(x / (leaf * 0.3f), y / (leaf * 0.3f), seed + 6) - 0.5f
        var nx = dx * 0.8f + clump.dx * 0.45f + (micro - 0.5f) * 0.6f + leafTilt * 0.4f
        var ny = dy * 0.8f + clump.dy * 0.45f + (Noise.value(x / (leaf * 0.3f), y / (leaf * 0.3f), seed + 7) - 0.5f) * 0.6f
        var nz = dz * 0.7f + cz * 0.4f + 0.25f
        val nl = sqrt(nx * nx + ny * ny + nz * nz)
        nx /= nl
        ny /= nl
        nz /= nl
        val diffuse = max(0f, nx * ldx + ny * ldy + nz * ldz)
        val low = (y - top) / height
        val own = (clump.pick(seed) - 0.5f) * 0.16f
        val v = (0.08f + 0.92f * diffuse) * (0.62f + 0.38f * crevice) * (0.55f + 0.45f * micro + 0.1f) * (1.12f - 0.42f * low) * (0.7f + 0.3f * dz) + own
        var color = ramp(tones, v.coerceIn(0f, 1f))
        if (variety != null) {
            val k = Noise.value(x / varietyScale, y / varietyScale, seed + 9)
            val hue = variety[((k * 0.7f + clump.pick(seed + 3) * 0.3f) * variety.size).toInt().coerceIn(0, variety.size - 1)]
            color = Tone.mix(color, Tone.shade(hue, (v - 0.55f) * 1.1f), varietyAmount * Tone.smooth(0.2f, 0.5f, Noise.value(x / (varietyScale * 1.7f), y / (varietyScale * 1.7f), seed + 11)))
        }
        if (back > 0f) {
            // Light through the leaves at the rim, strongest on the side facing the light.
            val rim = (1f - dz) * (1f - dz) * max(0f, dx * lx + dy * ly + 0.3f)
            color = Tone.mix(color, backTone, (rim * back * crevice).coerceIn(0f, 0.85f))
        }
        if (flowers != null && flowerAmount > 0f) {
            // Blossom in small clusters on the clumps, thickest where they catch the light.
            Noise.cellular(x / (leaf * 0.7f), y / (leaf * 0.7f), seed + 13, bud)
            val cut = flowerAmount * (0.35f + 0.65f * diffuse) * Tone.smooth(0.3f, 0.6f, bump)
            if (bud.pick(seed) < cut && bud.f1 < 0.42f) {
                val hue = flowers[(bud.pick(seed + 1) * flowers.size).toInt().coerceIn(0, flowers.size - 1)]
                val petal = Tone.shade(hue, (diffuse - 0.6f) * 0.45f - bud.f1 * 0.4f)
                color = Tone.mix(color, petal, Tone.smooth(0.42f, 0.28f, bud.f1))
            }
        }
        Tone.alpha(color, a * alpha)
    }
}

/**
 * Blobs filling an ellipse [crownW] × [crownH] centred at ([x], [y]): a big core and lumps
 * round it, larger at the top, so the crown's outline is lumpy and its top domed.
 */
internal fun crownBlobs(x: Float, y: Float, crownW: Float, crownH: Float, count: Int, seed: Int, lumpiness: Float = 1f): List<Blob> {
    val r = kotlin.random.Random(seed)
    val list = ArrayList<Blob>()
    list += Blob(x, y + crownH * 0.05f, crownW * 0.36f, crownH * 0.36f)
    repeat(count) {
        val a = r.nextFloat() * 2f * Math.PI.toFloat()
        val d = sqrt(r.nextFloat()) * 0.62f
        val bx = x + kotlin.math.cos(a) * d * crownW * 0.5f
        val by = y + kotlin.math.sin(a) * d * crownH * 0.5f
        val size = (0.14f + r.nextFloat() * 0.12f * lumpiness) * (1.1f - 0.3f * ((by - y) / crownH + 0.5f))
        list += Blob(bx, by, crownW * size, crownW * size * (0.8f + r.nextFloat() * 0.25f))
    }
    return list
}

/**
 * A spruce per pixel: a cone of branch tiers, each flaring out at its lower edge, needles
 * roughening the outline, the side towards the light brighter; with [snow], every tier's upper
 * face carries a white load that thins towards the tips.
 */
internal fun Painting.conifer(
    x0: Float,
    ground: Float,
    height: Float,
    width: Float,
    tones: IntArray,
    seed: Int,
    snow: Int = 0,
    snowShade: Int = 0,
    snowLoad: Float = 0.55f,
    trunk: Int = 0xFF2A1E18.toInt(),
    alpha: Float = 1f,
) {
    val top = ground - height
    val half = width / 2f
    val area = RectF(x0 - half * 1.25f, top - 1f, x0 + half * 1.25f, ground + 0.5f)
    val tiers = (height / width * 4.2f).coerceIn(6f, 17f)
    val ld = if (lightX >= x0) 1f else -1f
    val px = 1f / kx
    val needle = max(0.3f, width * 0.028f)
    val lump = Noise.Cell()
    pixels(area) { x, y ->
        val v = (y - top) / height
        if (v < 0f) return@pixels 0
        val tp = v * tiers + (Noise.value((x - x0) / (width * 0.3f), v * 3f, seed) - 0.5f) * 0.4f
        val tier = floor(tp)
        val ph = tp - tier
        val tierRand = Noise.rand(tier.toInt(), 0, seed)
        // Each tier droops: its reach grows towards its lower edge, the tips hanging.
        val env = half * v.pow(0.9f) * (0.46f + 0.54f * ph.pow(0.5f)) * (0.86f + 0.28f * tierRand)
        val d = abs(x - x0)
        // Well clear of the branches (and of the trunk): nothing to work out.
        if (d > env * 1.25f + needle * 2f && (v < 0.86f || d > width * 0.05f)) return@pixels 0
        val side = if (x >= x0) 1f else -1f
        // Needles run out and down along the branches.
        val along = (x - x0) * side * 0.6f + (y - top) * 0.8f
        val acrossB = (y - top) * 0.6f - (x - x0) * side * 0.8f
        val streak = Noise.value(along / (needle * 3.5f), acrossB / needle, seed + 1)
        val fringe = (Noise.fbm(x / needle, y / (needle * 1.3f), seed + 4, 2) - 0.5f) * (half * 0.2f + needle * 1.5f) + (streak - 0.5f) * needle * 1.5f
        val reach = env + fringe
        val a = ((reach - d) / px).coerceIn(0f, 1f)
        if (a <= 0f || v > 1f) {
            if (v > 0.86f && v <= 1.01f && d < width * 0.045f) return@pixels Tone.alpha(trunk, alpha)
            return@pixels 0
        }
        val u = ((x - x0) / max(0.01f, reach)).coerceIn(-1f, 1f)
        val flank = u * ld
        // Lit on the flank towards the light and on each tier's upper face; the core and the
        // underside of each tier deep in shade.
        val s = 0.4f + 0.28f * flank + 0.26f * (1f - ph) - 0.18f * (1f - abs(u)) * ph + (streak - 0.5f) * 0.45f
        var color = ramp(tones, s.coerceIn(0f, 1f))
        if (snow != 0) {
            Noise.cellular(x / (width * 0.09f), y / (width * 0.07f), seed + 3, lump)
            val load = snowLoad * (0.3f + 0.5f * (1f - abs(u))) * (0.55f + 0.9f * lump.pick(seed))
            val lumpEdge = load + (0.45f - lump.f1) * 0.25f
            if (ph < lumpEdge) {
                val k = Tone.smooth(lumpEdge, lumpEdge - 0.08f, ph)
                val dome = sqrt(max(0f, 1f - lump.f1 * lump.f1 * 2f))
                val lit = (0.5f + 0.5f * flank * 0.6f + (-lump.dy * 0.6f + lump.dx * ld * 0.5f) + dome * 0.2f - ph * 0.6f).coerceIn(0f, 1f)
                color = Tone.mix(color, Tone.mix(snowShade, snow, lit), k)
            }
        }
        Tone.alpha(color, a * alpha)
    }
}

/**
 * The wall of a forest under [line] down to [bottom], per pixel: crowns of many trees in [tones],
 * lit along their tops and dark lower down in the wood, [variety] hues patched in by tree.
 */
internal fun Painting.forest(
    line: Painting.Line,
    bottom: Float,
    tones: IntArray,
    seed: Int,
    leaf: Float,
    depth: Float,
    variety: IntArray? = null,
    varietyScale: Float = leaf * 4f,
    rim: Float = 0.25f,
) {
    val area = RectF(0f, line.top - 1f, w, bottom)
    val clip = android.graphics.Path().apply {
        moveTo(-1f, bottom)
        lineTo(-1f, line.ys[0])
        for (i in line.ys.indices) lineTo(i * line.step, line.ys[i])
        lineTo(w + 1f, line.ys.last())
        lineTo(w + 1f, bottom)
        close()
    }
    val crown = Noise.Cell()
    // Half resolution: a wall of trees in the distance, softened by the air anyway; the clip keeps its edge crisp.
    field(area, kx * 0.5f, clip) { x, y ->
        val below = y - line.at(x)
        val wx = (Noise.value(x / (leaf * 3f), y / (leaf * 3f), seed + 21) - 0.5f) * 0.9f
        val wy = (Noise.value(x / (leaf * 3f), y / (leaf * 3f), seed + 22) - 0.5f) * 0.9f
        Noise.cellular(x / (leaf * 2.4f) + wx, y / (leaf * 2f) + wy, seed, crown)
        val dome = sqrt(max(0f, 1f - crown.f1 * crown.f1 * 1.3f))
        val crevice = Tone.smooth(0f, 0.35f, crown.f2 - crown.f1)
        val leafy = Noise.fbm(x / (leaf * 0.5f), y / (leaf * 0.5f), seed + 1, 2)
        val facing = (crown.dx * lightFrom(x) * 0.8f - crown.dy * 0.6f) * dome
        var v = 0.55f - 0.48f * Tone.smooth(0f, depth, below) + facing * 0.22f + (leafy - 0.5f) * 0.4f - (1f - crevice) * 0.1f
        v += rim * (1f - Tone.smooth(0f, leaf * 1.2f, below))
        v += (crown.pick(seed) - 0.5f) * 0.14f
        var color = ramp(tones, v.coerceIn(0f, 1f))
        if (variety != null) {
            val k = crown.pick(seed + 5) * 0.6f + Noise.value(x / varietyScale, y / varietyScale, seed + 3) * 0.4f
            val hue = variety[(k * variety.size).toInt().coerceIn(0, variety.size - 1)]
            color = Tone.mix(color, Tone.shade(hue, (v - 0.55f) * 1.2f), 0.6f)
        }
        color
    }
}

/**
 * Ground-plane coordinates for a point below [horizon]: [across] and [away] grow into the
 * distance, so noise sampled on them shrinks and flattens with perspective as the eye expects.
 */
internal class Ground(private val w: Float, private val h: Float, private val horizon: Float) {
    var across = 0f
        private set
    var away = 0f
        private set

    fun at(x: Float, y: Float): Ground {
        val k = max(0.004f, (y - horizon) / h)
        away = 1f / k
        across = (x - w / 2f) / h * away
        return this
    }
}
