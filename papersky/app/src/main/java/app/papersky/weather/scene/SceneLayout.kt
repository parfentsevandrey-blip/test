package app.papersky.weather.scene

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry of one paper diorama: layered ridges (mountains, forested hills, meadows), cloud
 * cut-outs and star positions. Built once per size / place and reused for every frame — nothing
 * here is recomputed while the scene animates.
 */
internal class SceneLayout(private val dp: Float) {

    enum class Kind { Peaks, Forest, Rolling }

    private class Spec(val offset: Float, val amp: Float, val wavelengthDp: Float, val kind: Kind, val treeScale: Float, val depth: Float)

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
        val kind: Kind,
        internal val phases: FloatArray,
        internal val wavelength: Float,
        /** Trees standing on the ridge line, cut from a slightly darker sheet. */
        val forest: Path? = null,
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

    private val specs = arrayOf(
        Spec(-0.60f, 0.52f, 112f, Kind.Peaks, 0f, 0.08f),
        Spec(-0.28f, 0.30f, 150f, Kind.Forest, 0.8f, 0.3f),
        Spec(0.04f, 0.27f, 190f, Kind.Rolling, 0f, 0.52f),
        Spec(0.34f, 0.23f, 215f, Kind.Forest, 1.1f, 0.74f),
        Spec(0.64f, 0.17f, 290f, Kind.Rolling, 0f, 1f),
    )

    /** Rebuilds when anything that shapes the scene changed; returns true if it did. */
    fun ensure(w: Float, h: Float, seed: Int, horizonFrac: Float, detail: Float, depth: Float = Float.NaN): Boolean {
        if (w == this.w && h == this.h && seed == this.seed && horizonFrac == this.horizonFrac && detail == this.detail && depth.equals(requestedDepth)) return false
        this.w = w; this.h = h; this.seed = seed; this.horizonFrac = horizonFrac; this.detail = detail; requestedDepth = depth
        horizonY = h * horizonFrac
        val below = h - horizonY
        depthD = if (!depth.isNaN()) depth else min(below * 0.92f, min(w, h) * 0.46f).coerceAtLeast(18 * dp)
        buildRidges()
        buildBands()
        buildClouds()
        buildStars()
        return true
    }

    // ---- Ridges --------------------------------------------------------------------------------

    fun edge(r: Ridge, x: Float): Float = edgeOf(r.kind, r.base, r.amp, r.wavelength, r.phases, x)

    private fun edgeOf(kind: Kind, base: Float, amp: Float, wavelength: Float, ph: FloatArray, x: Float): Float {
        val xd = x / wavelength
        val n = when (kind) {
            Kind.Peaks -> {
                // Ridged noise: rounded summits, sharp saddles — reads as distant mountains.
                val a = peak(xd * 1.0f + ph[0])
                val b = peak(xd * 2.3f + ph[1])
                val swell = sin(xd * 0.37f + ph[2]) * 0.28f
                a * 0.7f + b * 0.3f + swell - 0.35f
            }
            else -> sin(xd * 0.8f + ph[0]) * 0.55f + sin(xd * 1.9f + ph[1]) * 0.3f + sin(xd * 4.1f + ph[2]) * 0.15f
        }
        return base - n * amp
    }

    private fun peak(v: Float): Float {
        val s = sin(v)
        return 1f - sqrt(s * s + 0.018f)
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
            val ph = FloatArray(4) { k -> rand(seed, i * 7 + k + 1) * 6.283f }
            val path = Path()
            path.fillType = Path.FillType.WINDING
            path.moveTo(left, bottom)
            var x = left
            var top = Float.MAX_VALUE
            var low = -Float.MAX_VALUE
            while (x <= right) {
                val y = edgeOf(spec.kind, base, amp, wavelength, ph, x)
                path.lineTo(x, y)
                top = min(top, y); low = max(low, y)
                x += step
            }
            val yEnd = edgeOf(spec.kind, base, amp, wavelength, ph, right)
            path.lineTo(right, yEnd)
            path.lineTo(right, bottom)
            path.close()

            var forest: Path? = null
            if (spec.kind == Kind.Forest && detail >= 0.5f) {
                forest = Path().apply { fillType = Path.FillType.WINDING }
                top = min(top, addForest(forest, spec, i, base, amp, wavelength, ph))
            }
            ridges += Ridge(i, path, top, low, base, amp, spec.depth, spec.kind, ph, wavelength, forest)
        }
    }

    /** Conifers in loose clusters along the ridge line, as extra contours of the same sheet. */
    private fun addForest(path: Path, spec: Spec, i: Int, base: Float, amp: Float, wavelength: Float, ph: FloatArray): Float {
        val treeH = (depthD * 0.1f * spec.treeScale).coerceIn(6 * dp, 26 * dp)
        var x = -margin
        var k = 0
        var top = Float.MAX_VALUE
        while (x < w + margin) {
            val cluster = sin(x / (47 * dp * (1 + spec.treeScale)) + ph[3]) + 0.3f * sin(x / (17 * dp) + ph[2] * 2)
            // Woods thin out towards their edges instead of stopping like a wall.
            val density = smoothstep(-0.1f, 0.55f, cluster)
            if (density > 0.05f && rand(k, seed + 930 + i) < 0.35f + 0.65f * density) {
                val th = treeH * (0.45f + 0.55f * density) * (0.7f + 0.5f * rand(k, seed + 900 + i))
                val tw = th * (0.5f + 0.12f * rand(k, seed + 910 + i))
                val gy = edgeOf(spec.kind, base, amp, wavelength, ph, x) + th * 0.12f
                // Two tiers read as a fir rather than a triangle.
                path.moveTo(x, gy - th)
                path.lineTo(x + tw * 0.32f, gy - th * 0.46f)
                path.lineTo(x + tw * 0.16f, gy - th * 0.48f)
                path.lineTo(x + tw * 0.5f, gy - th * 0.04f)
                path.lineTo(x + tw * 0.06f, gy)
                path.lineTo(x - tw * 0.06f, gy)
                path.lineTo(x - tw * 0.5f, gy - th * 0.04f)
                path.lineTo(x - tw * 0.16f, gy - th * 0.48f)
                path.lineTo(x - tw * 0.32f, gy - th * 0.46f)
                path.close()
                top = min(top, gy - th)
                x += tw * (0.5f + 0.7f * rand(k, seed + 920 + i))
            } else {
                x += treeH * 0.45f
            }
            k++
        }
        return top
    }

    private fun buildBands() {
        bands.clear()
        val groups = listOf(0 to 1, 2 to 3, 4 to 4)
        for ((gi, g) in groups.withIndex()) {
            val (a, b) = g
            val top = (a..b).minOf { ridges[it].top } - 18 * dp
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

        // A ceiling of wide banks for overcast skies.
        val banks = (w / (dp * 150f)).roundToInt().coerceIn(3, 7)
        for (i in 0 until banks) {
            val cw = w * (0.42f + 0.22f * rand(i, seed + 300))
            val ch = cw * (0.28f + 0.06f * rand(i, seed + 301))
            val y = -ch * (0.38f + 0.22f * rand(i, seed + 302)) + sky * 0.05f * (i % 2)
            clouds += cloud(cw, ch, i * 31 + seed, x0 = (i + rand(i, seed + 303) * 0.4f) / banks * (w + cw), y = y, depth = 0.15f + 0.1f * (i % 2), ceiling = true, rank = i)
        }

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
