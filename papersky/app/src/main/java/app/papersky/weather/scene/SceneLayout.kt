package app.papersky.weather.scene

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry of one paper diorama: three soft rolling hills cut from paper (far, middle, near
 * meadow), cloud cut-outs, the hamlet and trees, star positions. Built once per size / place and
 * reused for every frame — nothing here is recomputed while the scene animates.
 */
internal class SceneLayout(private val dp: Float) {

    private class Spec(val offset: Float, val amp: Float, val wavelengthDp: Float, val depth: Float)

    class Ridge(
        val index: Int,
        val path: Path,
        /** Highest point of the silhouette (trees included). */
        val top: Float,
        /** Lowest point of the edge; below it this ridge covers everything. */
        val bottom: Float,
        val base: Float,
        val amp: Float,
        /** 0 = farthest, 1 = nearest; drives colour, haze and parallax. */
        val depth: Float,
        internal val phases: FloatArray,
        internal val wavelength: Float,
    )

    /** Ridges grouped for compositing: each band is one cached layer with its own parallax. */
    class Band(val first: Int, val last: Int, val top: Float, val bottom: Float, val depth: Float)

    class Cloud(
        val front: Path,
        val back: Path,
        val width: Float,
        val height: Float,
        val x0: Float,
        /** Top of the cloud's box. */
        val y: Float,
        val depth: Float,
        val ceiling: Boolean,
        /** Order in which clouds appear as cover grows. */
        val rank: Int,
    )

    /** A paper cottage standing on the meadow. */
    class House(
        val x: Float,
        val w: Float,
        val h: Float,
        val roofH: Float,
        val twoWindows: Boolean,
        val chimney: Boolean,
        val door: Boolean,
        val tone: Float,
    )

    /** A tree on the meadow; it sways every frame, so it lives outside the cached bands. */
    class Tree(val x: Float, val h: Float, val pine: Boolean, val tone: Float)

    val houses = ArrayList<House>(6)
    val trees = ArrayList<Tree>(26)

    /** Street lantern by the village, or NaN. */
    var lanternX = Float.NaN; private set

    /** Scalloped overcast festoon along the top of the sky; repeats every [blanketPeriod]. */
    val blanket = Path()
    var blanketPeriod = 1f; private set

    /** Size factor for props (houses, trees, flakes). */
    var propScale = 1f; private set

    var w = -1f; private set
    var h = -1f; private set
    var horizonY = 0f; private set
    var depthD = 0f; private set
    val margin get() = 28 * dp

    val ridges = ArrayList<Ridge>(5)
    val bands = ArrayList<Band>(3)
    val clouds = ArrayList<Cloud>(14)

    /** Stars as x,y pairs in three twinkle groups. */
    val stars: Array<FloatArray> = Array(3) { FloatArray(0) }

    /** Bright stars: x, y, size triples. */
    var bright = FloatArray(0); private set

    private var seed = Int.MIN_VALUE
    private var horizonFrac = -1f
    private var detail = -1f
    private var requestedDepth = Float.NaN
    private var village = true

    // Far hill, middle hill, near meadow — gentle sums of sines, like the first Papersky.
    private val specs = arrayOf(
        Spec(-0.32f, 0.19f, 150f, 0.3f),
        Spec(0.02f, 0.15f, 111f, 0.62f),
        Spec(0.62f, 0.11f, 88f, 1f),
    )

    /** Rebuilds when anything that shapes the scene changed; returns true if it did. */
    fun ensure(w: Float, h: Float, seed: Int, horizonFrac: Float, detail: Float, depth: Float = Float.NaN, village: Boolean = true): Boolean {
        if (w == this.w && h == this.h && seed == this.seed && horizonFrac == this.horizonFrac && detail == this.detail && depth.equals(requestedDepth) && village == this.village) return false
        this.w = w; this.h = h; this.seed = seed; this.horizonFrac = horizonFrac; this.detail = detail; requestedDepth = depth; this.village = village
        horizonY = h * horizonFrac
        val below = h - horizonY
        depthD = if (!depth.isNaN()) depth else min(below * 0.92f, min(w, h) * 0.46f).coerceAtLeast(18 * dp)
        buildRidges()
        buildBands()
        buildClouds()
        buildStars()
        buildVillage()
        buildBlanket()
        return true
    }

    // ---- Ridges --------------------------------------------------------------------------------

    fun edge(r: Ridge, x: Float): Float = edgeOf(r.base, r.amp, r.wavelength, r.phases, x)

    private fun edgeOf(base: Float, amp: Float, wavelength: Float, ph: FloatArray, x: Float): Float {
        val xd = x / wavelength
        val n = sin(xd * 0.8f + ph[0]) * 0.55f + sin(xd * 1.9f + ph[1]) * 0.3f + sin(xd * 4.3f + ph[2]) * 0.15f
        return base - n * amp
    }

    private fun buildRidges() {
        ridges.clear()
        val bottom = h + 40 * dp
        val left = -margin
        val right = w + margin
        val step = 3 * dp
        for ((i, spec) in specs.withIndex()) {
            val base = horizonY + depthD * spec.offset
            val amp = depthD * spec.amp
            val wavelength = spec.wavelengthDp * dp * (0.85f + 0.3f * rand(seed, 40 + i))
            val ph = FloatArray(3) { k -> rand(seed, i * 7 + k + 1) * 6.283f }
            val path = Path()
            path.moveTo(left, bottom)
            var x = left
            var top = Float.MAX_VALUE
            var low = -Float.MAX_VALUE
            while (x <= right) {
                val y = edgeOf(base, amp, wavelength, ph, x)
                path.lineTo(x, y)
                top = min(top, y); low = max(low, y)
                x += step
            }
            path.lineTo(right, edgeOf(base, amp, wavelength, ph, right))
            path.lineTo(right, bottom)
            path.close()
            ridges += Ridge(i, path, top, low, base, amp, spec.depth, ph, wavelength)
        }
    }

    private fun buildBands() {
        bands.clear()
        // One cached layer per hill, each with its own parallax.
        val groups = ridges.indices.map { it to it }
        for ((gi, g) in groups.withIndex()) {
            val (a, b) = g
            var top = (a..b).minOf { ridges[it].top } - 18 * dp
            // The meadow's band carries the cottages: leave room for roofs and chimneys.
            if (b == ridges.lastIndex) top -= 34 * dp * (min(w, h) / (dp * 220f)).coerceIn(0.62f, 1.25f)
            val bottom = if (gi < groups.lastIndex) {
                // Below the next band's lowest edge, nothing of this band can show.
                ridges[groups[gi + 1].first].bottom + 10 * dp
            } else {
                ridges[b].bottom + 24 * dp
            }
            bands += Band(a, b, top, max(bottom, top + 8 * dp), ridges[b].depth)
        }
    }

    // ---- Clouds --------------------------------------------------------------------------------

    private fun buildClouds() {
        clouds.clear()
        val s = (min(w, h) / (dp * 240f)).coerceIn(0.55f, 1.2f)
        val sky = max(horizonY - depthD * 0.35f, h * 0.2f)

        val n = (w / (dp * 82f)).roundToInt().coerceIn(3, 9)
        for (i in 0 until n) {
            val depth = rand(i, seed + 101)
            val cw = (64f + rand(i, seed + 103) * 74f) * dp * s * (0.72f + 0.45f * depth)
            val ch = cw * (0.4f + rand(i, seed + 107) * 0.12f)
            val y = (0.08f + rand(i, seed + 109) * 0.5f) * sky
            clouds += cloud(cw, ch, i * 17 + seed * 3, x0 = rand(i, seed + 113) * (w + cw), y = y, depth = depth, ceiling = false, rank = i)
        }
    }

    private fun cloud(cw: Float, ch: Float, salt: Int, x0: Float, y: Float, depth: Float, ceiling: Boolean, rank: Int): Cloud {
        val front = cloudPath(cw, ch, salt)
        val back = cloudPath(cw * 0.7f, ch * 0.95f, salt + 7).apply { offset(cw * 0.26f, -ch * 0.24f) }
        return Cloud(front, back, cw, ch, x0, y, depth, ceiling, rank)
    }

    /** Puffs over a rounded base, cut flat underneath like a paper cut-out. Local box 0..cw × 0..ch. */
    private fun cloudPath(cw: Float, ch: Float, salt: Int): Path {
        val base = Path().apply {
            addRoundRect(RectF(0f, ch * 0.52f, cw, ch), ch * 0.24f, ch * 0.24f, Path.Direction.CW)
        }
        val n = 3 + (rand(salt, 17) * 2.6f).toInt()
        val puffs = Path()
        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            val bell = sin(t * PI.toFloat())
            val r = ch * (0.26f + 0.3f * bell * (0.75f + 0.5f * rand(salt + i, 19)))
            val cx = cw * (0.12f + t * 0.76f) + (rand(salt + i, 23) - 0.5f) * cw * 0.05f
            val cy = ch * 0.78f - r * (0.55f + 0.35f * bell)
            puffs.addCircle(cx, cy, r, Path.Direction.CW)
        }
        base.op(puffs, Path.Op.UNION)
        base.op(Path().apply { addRect(-cw, -ch * 2, cw * 2, ch, Path.Direction.CW) }, Path.Op.INTERSECT)
        return base
    }


    // ---- Village, trees, overcast --------------------------------------------------------------

    private fun buildVillage() {
        houses.clear()
        trees.clear()
        lanternX = Float.NaN
        propScale = (min(w, h) / (dp * 220f)).coerceIn(0.62f, 1.25f)
        if (detail < 0.5f) return
        val widthDp = w / dp
        val s = propScale
        if (village) {
            // A hamlet: a few cottages huddled together, not a row of boxes.
            val n = (widthDp / 110f * detail).roundToInt().coerceIn(2, 5)
            val hw = 18f * dp * s
            val span = n * hw * 1.35f
            var x = (0.14f + 0.72f * rand(seed, 1601)) * (w - span) + hw / 2
            for (i in 0 until n) {
                val cw = (14f + rand(i, seed + 203) * 8f) * dp * s
                houses += House(
                    x = x,
                    w = cw,
                    h = cw * (0.74f + rand(i, seed + 205) * 0.24f),
                    roofH = cw * (0.5f + rand(i, seed + 207) * 0.22f),
                    twoWindows = cw > 17 * dp * s && rand(i, seed + 209) > 0.4f,
                    chimney = rand(i, seed + 211) > 0.25f,
                    door = rand(i, seed + 213) > 0.35f,
                    tone = rand(i, seed + 215),
                )
                x += cw * (1.08f + 0.35f * rand(i, seed + 217))
            }
            val last = houses.last()
            lanternX = (last.x + last.w * 0.95f).takeIf { it < w - 8 * dp } ?: (houses.first().x - houses.first().w * 0.95f)
        }
        val nTrees = (widthDp / 30f * detail).roundToInt().coerceIn(2, 26)
        for (i in 0 until nTrees) {
            val tx = (i + 0.5f + (rand(i, seed + 301) - 0.5f) * 0.8f) / nTrees * w
            if (houses.any { kotlin.math.abs(it.x - tx) < it.w * 0.95f }) continue
            if (!lanternX.isNaN() && kotlin.math.abs(lanternX - tx) < 8 * dp * s) continue
            trees += Tree(tx, (15f + rand(i, seed + 303) * 16f) * dp * s, rand(i, seed + 305) < 0.6f, rand(i, seed + 307))
        }
    }

    private fun buildBlanket() {
        blanket.reset()
        val scallop = 30 * dp * propScale
        blanketPeriod = scallop * 2
        val bottom = max(horizonY - depthD * 0.9f, h * 0.12f) * 0.34f
        val left = -blanketPeriod * 2
        blanket.moveTo(left, -12 * dp)
        blanket.lineTo(left, bottom)
        var bx = left
        var k = 0
        while (bx < w + blanketPeriod * 2) {
            val r = scallop * (0.75f + 0.5f * rand(k++, seed + 401))
            blanket.quadTo(bx + r * 0.5f, bottom + r * 0.95f, bx + r, bottom)
            bx += r
        }
        blanket.lineTo(bx, -12 * dp)
        blanket.close()
    }

    // ---- Stars ---------------------------------------------------------------------------------

    private fun buildStars() {
        val top = horizonY - depthD * 0.4f
        val n = ((w * top) / (dp * dp * 720f)).roundToInt().coerceIn(10, 150)
        val groups = Array(3) { ArrayList<Float>() }
        for (i in 0 until n) {
            val x = rand(i, seed + 501) * w
            // Denser towards the zenith, thinning into the glow near the horizon.
            val v = rand(i, seed + 503)
            val y = v * v * top * 0.95f
            groups[i % 3].apply { add(x); add(y) }
        }
        for (g in 0..2) stars[g] = groups[g].toFloatArray()
        val nb = (n / 22).coerceIn(2, 6)
        bright = FloatArray(nb * 3) { k ->
            val i = k / 3
            when (k % 3) {
                0 -> rand(i, seed + 521) * w
                1 -> rand(i, seed + 523) * top * 0.7f
                else -> (5f + rand(i, seed + 525) * 4f) * dp
            }
        }
    }
}
