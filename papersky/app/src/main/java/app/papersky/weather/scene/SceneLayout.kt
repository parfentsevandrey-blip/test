package app.papersky.weather.scene

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry of one print (DESIGN_DOCTRINE §10): five planes — two mountain ranges with sharp
 * crests, two hills and the meadow — vellum clouds, the overcast veil, a hamlet and trees in
 * silhouette, star positions. Built once per size / place and reused for every frame — nothing
 * here is recomputed while the scene animates.
 */
internal class SceneLayout(private val dp: Float) {

    private class Spec(val offset: Float, val amp: Float, val wavelengthDp: Float, val depth: Float, val sharp: Boolean)

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
        /** Mountain crests (cusped) rather than rolling hills. */
        val sharp: Boolean,
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

    /** A house in silhouette on the meadow. */
    class House(
        val x: Float,
        val w: Float,
        val h: Float,
        val roofH: Float,
        val twoWindows: Boolean,
        val tone: Float,
    )

    /** A tree on the meadow; it sways every frame, so it lives outside the cached bands. */
    class Tree(val x: Float, val h: Float, val pine: Boolean, val tone: Float)

    val houses = ArrayList<House>(6)
    val trees = ArrayList<Tree>(26)

    /** Tiny conifers along the crest of the far hill, baked into its band: x, height pairs. */
    var treeLine = FloatArray(0); private set

    /** The overcast veil along the top of the sky, with a long gentle hem; repeats every [blanketPeriod]. */
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

    // Two mountain ranges, two hills, the meadow (§10).
    private val specs = arrayOf(
        Spec(-0.40f, 0.34f, 150f, 0.1f, sharp = true),
        Spec(-0.20f, 0.24f, 120f, 0.3f, sharp = true),
        Spec(0.02f, 0.14f, 125f, 0.52f, sharp = false),
        Spec(0.26f, 0.11f, 100f, 0.76f, sharp = false),
        Spec(0.58f, 0.08f, 86f, 1f, sharp = false),
    )

    /** Rebuilds when anything that shapes the scene changed; returns true if it did. */
    /** Frequencies and weights of the five mountain octaves. */
    private companion object {
        val RIDGE_F = floatArrayOf(1.3f, 2.9f, 5.7f, 11f, 21f)
        val RIDGE_W = floatArrayOf(0.46f, 0.26f, 0.14f, 0.09f, 0.05f)
    }

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

    fun edge(r: Ridge, x: Float): Float = edgeOf(r.base, r.amp, r.wavelength, r.phases, r.sharp, x)

    private fun edgeOf(base: Float, amp: Float, wavelength: Float, ph: FloatArray, sharp: Boolean, x: Float): Float {
        val xd = x / wavelength
        if (sharp) {
            // Cusped peaks: 1 − |sin| folds each wave into a crest; five octaves add the rock.
            var n = 0f
            for (k in 0 until 5) n += RIDGE_W[k] * (1f - abs(sin(xd * RIDGE_F[k] + ph[k])) - 0.363f)
            return base - n * amp * 1.5f
        }
        val n = sin(xd * 0.8f + ph[0]) * 0.55f + sin(xd * 1.9f + ph[1]) * 0.3f + sin(xd * 4.3f + ph[2]) * 0.15f
        return base - n * amp
    }

    private fun buildRidges() {
        ridges.clear()
        val bottom = h + 40 * dp
        val left = -margin
        val right = w + margin
        for ((i, spec) in specs.withIndex()) {
            val step = (if (spec.sharp) 2f else 3f) * dp
            val base = horizonY + depthD * spec.offset
            val amp = depthD * spec.amp
            val wavelength = spec.wavelengthDp * dp * (0.85f + 0.3f * rand(seed, 40 + i))
            val ph = FloatArray(5) { k -> rand(seed, i * 7 + k + 1) * 6.283f }
            val path = Path()
            path.moveTo(left, bottom)
            var x = left
            var top = Float.MAX_VALUE
            var low = -Float.MAX_VALUE
            while (x <= right) {
                val y = edgeOf(base, amp, wavelength, ph, spec.sharp, x)
                path.lineTo(x, y)
                top = min(top, y); low = max(low, y)
                x += step
            }
            path.lineTo(right, edgeOf(base, amp, wavelength, ph, spec.sharp, right))
            path.lineTo(right, bottom)
            path.close()
            ridges += Ridge(i, path, top, low, base, amp, spec.depth, ph, wavelength, spec.sharp)
        }
    }

    private fun buildBands() {
        bands.clear()
        // One cached layer per hill, each with its own parallax.
        val groups = ridges.indices.map { it to it }
        for ((gi, g) in groups.withIndex()) {
            val (a, b) = g
            var top = (a..b).minOf { ridges[it].top } - 14 * dp
            // The meadow's band carries the houses; the hill behind it its line of trees.
            if (b == ridges.lastIndex) top -= 30 * dp * (min(w, h) / (dp * 220f)).coerceIn(0.62f, 1.25f)
            if (b == ridges.lastIndex - 1) top -= 12 * dp
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
            val cw = (84f + rand(i, seed + 103) * 90f) * dp * s * (0.72f + 0.45f * depth)
            val ch = cw * (0.2f + rand(i, seed + 107) * 0.06f)
            val y = (0.08f + rand(i, seed + 109) * 0.5f) * sky
            clouds += cloud(cw, ch, i * 17 + seed * 3, x0 = rand(i, seed + 113) * (w + cw), y = y, depth = depth, ceiling = false, rank = i)
        }
    }

    private fun cloud(cw: Float, ch: Float, salt: Int, x0: Float, y: Float, depth: Float, ceiling: Boolean, rank: Int): Cloud {
        val front = cloudPath(cw, ch, salt)
        val back = cloudPath(cw * 0.66f, ch * 0.9f, salt + 7).apply { offset(cw * 0.28f, -ch * 0.2f) }
        return Cloud(front, back, cw, ch, x0, y, depth, ceiling, rank)
    }

    /** A sheet of vellum: a long flat base with two or three low domes. Local box 0..cw × 0..ch. */
    private fun cloudPath(cw: Float, ch: Float, salt: Int): Path {
        val base = Path().apply {
            addRoundRect(RectF(0f, ch * 0.66f, cw, ch), ch * 0.17f, ch * 0.17f, Path.Direction.CW)
        }
        val n = 2 + (rand(salt, 17) * 1.9f).toInt()
        val puffs = Path()
        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            val bell = sin(t * PI.toFloat())
            val r = ch * (0.2f + 0.2f * bell * (0.75f + 0.5f * rand(salt + i, 19)))
            val cx = cw * (0.2f + t * 0.6f) + (rand(salt + i, 23) - 0.5f) * cw * 0.08f
            val cy = ch * 0.86f - r * (0.45f + 0.3f * bell)
            // Long, low ellipses rather than puffs: calm sheets of vellum.
            puffs.addOval(RectF(cx - r * 2.6f, cy - r, cx + r * 2.6f, cy + r), Path.Direction.CW)
        }
        base.op(puffs, Path.Op.UNION)
        base.op(Path().apply { addRect(-cw, -ch * 2, cw * 2, ch, Path.Direction.CW) }, Path.Op.INTERSECT)
        return base
    }


    // ---- Village, trees, overcast --------------------------------------------------------------

    private fun buildVillage() {
        houses.clear()
        trees.clear()
        treeLine = FloatArray(0)
        propScale = (min(w, h) / (dp * 220f)).coerceIn(0.62f, 1.25f)
        if (detail < 0.5f) return
        val widthDp = w / dp
        val s = propScale
        if (village) {
            // A few houses gathered together, in silhouette.
            val n = (widthDp / 120f * detail).roundToInt().coerceIn(2, 4)
            val hw = 13f * dp * s
            val span = n * hw * 1.3f
            var x = (0.14f + 0.72f * rand(seed, 1601)) * (w - span) + hw / 2
            for (i in 0 until n) {
                val cw = (10f + rand(i, seed + 203) * 5f) * dp * s
                houses += House(
                    x = x,
                    w = cw,
                    h = cw * (0.62f + rand(i, seed + 205) * 0.22f),
                    roofH = cw * (0.42f + rand(i, seed + 207) * 0.18f),
                    twoWindows = cw > 12.5f * dp * s && rand(i, seed + 209) > 0.4f,
                    tone = rand(i, seed + 215),
                )
                x += cw * (1.12f + 0.4f * rand(i, seed + 217))
            }
        }
        // Trees gather in groves rather than standing in a row.
        val nGroves = (widthDp / 90f * detail).roundToInt().coerceIn(2, 7)
        for (g in 0 until nGroves) {
            val gx = (g + 0.2f + 0.6f * rand(g, seed + 301)) / nGroves * w
            val count = 1 + (rand(g, seed + 302) * 4f).toInt()
            for (k in 0 until count) {
                val tx = gx + (k - count / 2f) * 7f * dp * s + (rand(g * 7 + k, seed + 304) - 0.5f) * 4 * dp * s
                if (houses.any { kotlin.math.abs(it.x - tx) < it.w * 0.9f }) continue
                val pine = rand(g * 7 + k, seed + 305) < 0.72f
                trees += Tree(tx, (14f + rand(g * 7 + k, seed + 303) * 18f) * dp * s * (if (pine) 1f else 1.15f), pine, rand(g * 7 + k, seed + 307))
            }
        }
        // A fine line of conifers on the crest of the hill behind the meadow.
        if (ridges.size >= 2) {
            val n = (widthDp / 7f * detail).roundToInt().coerceIn(10, 80)
            val out = ArrayList<Float>(n * 2)
            for (i in 0 until n) {
                val cluster = sin(i * 0.37f + rand(seed, 331) * 6f)
                if (cluster < -0.2f) continue
                out += (i + rand(i, seed + 333)) / n * w
                out += (4f + rand(i, seed + 335) * 5f) * dp * s * (0.7f + 0.3f * cluster)
            }
            treeLine = out.toFloatArray()
        }
    }

    private fun buildBlanket() {
        blanket.reset()
        val wave = 190 * dp * propScale
        blanketPeriod = wave
        val bottom = max(horizonY - depthD * 0.9f, h * 0.12f) * 0.32f
        val amp = 9 * dp * propScale
        val left = -blanketPeriod * 2
        val right = w + blanketPeriod * 2
        blanket.moveTo(left, -12 * dp)
        var bx = left
        while (bx <= right) {
            // Whole periods only, so the veil can slide by one period seamlessly.
            val ph = (bx - left) / wave * 6.283f
            blanket.lineTo(bx, bottom + sin(ph) * amp + sin(ph * 2f + 1.3f) * amp * 0.35f)
            bx += 4 * dp
        }
        blanket.lineTo(right, -12 * dp)
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
