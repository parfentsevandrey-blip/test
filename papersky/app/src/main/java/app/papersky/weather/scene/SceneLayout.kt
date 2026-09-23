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
 * silhouette, star positions. The [SceneVariant.River] print (Ophelia, §16) has its own planes:
 * two canopies of tree crowns, the rose bank with a willow, the river and the mossy near bank.
 * Built once per size / place and reused for every frame — nothing here is recomputed while the
 * scene animates.
 */
internal class SceneLayout(private val dp: Float) {

    /** How a plane's edge is cut. */
    enum class Contour {
        /** Mountain crests: cusped peaks. */
        Crests,
        /** Rolling hills. */
        Rolling,
        /** Tree crowns: rounded bumps with cusped hollows between them. */
        Crowns,
        /** A waterline: all but level. */
        Flat,
    }

    private class Spec(val offset: Float, val amp: Float, val wavelengthDp: Float, val depth: Float, val contour: Contour)

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
        val contour: Contour,
    ) {
        /** Mountain crests (cusped) rather than rolling hills. */
        val sharp: Boolean get() = contour == Contour.Crests
    }

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
    var variant = SceneVariant.Mountains; private set

    // ---- The river print (Ophelia) ---------------------------------------------------------

    /** Dog roses on the bank: x, y, size triples. */
    var roses = FloatArray(0); private set

    /** Spikes of purple loosestrife rising from the bank: x, height pairs. */
    var loosestrife = FloatArray(0); private set

    /** Daisies in the moss of the near bank: x, y pairs. */
    var daisies = FloatArray(0); private set

    /** The willow leaning over the water: its tapered trunk and the soft masses of its crown. */
    val willow = Path()
    var willowTop = Float.NaN; private set

    /** Masses of the willow's crown: centre x, centre y, radius x, radius y. */
    var willowCrown = FloatArray(0); private set

    /** Willow fronds: anchor x, anchor y, length, phase. */
    var fronds = FloatArray(0); private set

    /** Reeds on the near bank: x, height, lean, phase. */
    var reeds = FloatArray(0); private set

    /** Flowers adrift on the river: start (0..1), lane (0..1), speed dp/s, kind, size. */
    var flowers = FloatArray(0); private set

    /** Still glints on the water: x, y, length triples. */
    var glints = FloatArray(0); private set

    // Two mountain ranges, two hills, the meadow (§10).
    private val mountains = arrayOf(
        Spec(-0.40f, 0.34f, 150f, 0.1f, Contour.Crests),
        Spec(-0.20f, 0.24f, 120f, 0.3f, Contour.Crests),
        Spec(0.02f, 0.14f, 125f, 0.52f, Contour.Rolling),
        Spec(0.26f, 0.11f, 100f, 0.76f, Contour.Rolling),
        Spec(0.58f, 0.08f, 86f, 1f, Contour.Rolling),
    )

    // Far trees, near trees, the rose bank, the river, the mossy near bank (§16).
    private val river = arrayOf(
        Spec(-0.48f, 0.2f, 62f, 0.1f, Contour.Crowns),
        Spec(-0.24f, 0.16f, 44f, 0.3f, Contour.Crowns),
        Spec(0.02f, 0.09f, 26f, 0.52f, Contour.Crowns),
        Spec(0.14f, 0.012f, 160f, 0.76f, Contour.Flat),
        Spec(0.84f, 0.05f, 140f, 1f, Contour.Rolling),
    )

    private val specs get() = if (variant == SceneVariant.River) river else mountains

    /** Index of the river among the planes of the river print. */
    val waterIndex get() = 3

    /** Rebuilds when anything that shapes the scene changed; returns true if it did. */
    /** Frequencies and weights of the five mountain octaves. */
    private companion object {
        val RIDGE_F = floatArrayOf(1.3f, 2.9f, 5.7f, 11f, 21f)
        val RIDGE_W = floatArrayOf(0.46f, 0.26f, 0.14f, 0.09f, 0.05f)
        val CROWN_F = floatArrayOf(1f, 2.3f, 4.7f, 9.1f)
        val CROWN_W = floatArrayOf(0.5f, 0.28f, 0.14f, 0.08f)
    }

    fun ensure(w: Float, h: Float, seed: Int, horizonFrac: Float, detail: Float, depth: Float = Float.NaN, village: Boolean = true, variant: SceneVariant = SceneVariant.Mountains): Boolean {
        if (w == this.w && h == this.h && seed == this.seed && horizonFrac == this.horizonFrac && detail == this.detail && depth.equals(requestedDepth) && village == this.village && variant == this.variant) return false
        this.w = w; this.h = h; this.seed = seed; this.horizonFrac = horizonFrac; this.detail = detail; requestedDepth = depth; this.village = village; this.variant = variant
        horizonY = h * horizonFrac
        val below = h - horizonY
        depthD = if (!depth.isNaN()) depth else min(below * 0.92f, min(w, h) * 0.46f).coerceAtLeast(18 * dp)
        buildRidges()
        buildRiver()
        buildBands()
        buildClouds()
        buildStars()
        buildVillage()
        buildBlanket()
        return true
    }

    // ---- Ridges --------------------------------------------------------------------------------

    fun edge(r: Ridge, x: Float): Float = edgeOf(r.base, r.amp, r.wavelength, r.phases, r.contour, x)

    private fun edgeOf(base: Float, amp: Float, wavelength: Float, ph: FloatArray, contour: Contour, x: Float): Float {
        val xd = x / wavelength
        return when (contour) {
            Contour.Crests -> {
                // Cusped peaks: 1 − |sin| folds each wave into a crest; five octaves add the rock.
                var n = 0f
                for (k in 0 until 5) n += RIDGE_W[k] * (1f - abs(sin(xd * RIDGE_F[k] + ph[k])) - 0.363f)
                base - n * amp * 1.5f
            }
            Contour.Crowns -> {
                // |sin| rounds each wave into a crown with a cusp between neighbours.
                var n = 0f
                for (k in 0 until 4) n += CROWN_W[k] * (abs(sin(xd * CROWN_F[k] + ph[k])) - 0.637f)
                base - n * amp * 1.8f
            }
            Contour.Flat -> base - (sin(xd * 0.7f + ph[0]) * 0.6f + sin(xd * 2.3f + ph[1]) * 0.4f) * amp
            Contour.Rolling -> base - (sin(xd * 0.8f + ph[0]) * 0.55f + sin(xd * 1.9f + ph[1]) * 0.3f + sin(xd * 4.3f + ph[2]) * 0.15f) * amp
        }
    }

    private fun buildRidges() {
        ridges.clear()
        val bottom = h + 40 * dp
        val left = -margin
        val right = w + margin
        for ((i, spec) in specs.withIndex()) {
            val step = (if (spec.contour == Contour.Rolling || spec.contour == Contour.Flat) 3f else 2f) * dp
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
                val y = edgeOf(base, amp, wavelength, ph, spec.contour, x)
                path.lineTo(x, y)
                top = min(top, y); low = max(low, y)
                x += step
            }
            path.lineTo(right, edgeOf(base, amp, wavelength, ph, spec.contour, right))
            path.lineTo(right, bottom)
            path.close()
            ridges += Ridge(i, path, top, low, base, amp, spec.depth, ph, wavelength, spec.contour)
        }
    }

    private fun buildRiver() {
        roses = FloatArray(0); loosestrife = FloatArray(0); daisies = FloatArray(0)
        fronds = FloatArray(0); reeds = FloatArray(0); flowers = FloatArray(0); glints = FloatArray(0)
        willow.reset(); willowTop = Float.NaN
        if (variant != SceneVariant.River) return
        val s = (min(w, h) / (dp * 220f)).coerceIn(0.62f, 1.25f)
        val widthDp = w / dp
        val bank = ridges[2]
        val water = ridges[3]
        val near = ridges[4]

        // Dog roses gather on two or three bushes of the bank.
        val out = ArrayList<Float>()
        val nRoses = (widthDp / 5f * detail).roundToInt().coerceIn(12, 90)
        for (i in 0 until nRoses) {
            val x = rand(i, seed + 701) * w
            val bush = sin(x / (70f * dp) + rand(seed, 703) * 6f)
            if (bush < 0.1f) continue
            val e = edge(bank, x)
            out += x; out += e + 4 * dp + rand(i, seed + 705) * 0.5f * (water.base - e); out += (1.3f + rand(i, seed + 707) * 0.9f) * dp * s
        }
        roses = out.toFloatArray()

        // Purple loosestrife at the right-hand end of the bank.
        out.clear()
        for (i in 0 until 4) {
            out += w * (0.84f + 0.035f * i + rand(i, seed + 711) * 0.02f); out += (14f + rand(i, seed + 713) * 12f) * dp * s
        }
        loosestrife = out.toFloatArray()

        // Daisies in the moss.
        out.clear()
        val nDaisies = (widthDp / 9f * detail).roundToInt().coerceIn(8, 60)
        for (i in 0 until nDaisies) {
            val x = rand(i, seed + 721) * w
            out += x; out += edge(near, x) + (5f + rand(i, seed + 723) * 46f) * dp * s
        }
        daisies = out.toFloatArray()

        // The willow grows from the left bank and leans out over the water: a tapered trunk, a
        // crown of soft masses along its upper half, fronds hanging from the crown to the water.
        val wy = edge(water, 0f)
        val lean = depthD * 0.36f
        val px = floatArrayOf(-8 * dp, w * 0.05f, w * 0.14f, w * 0.34f)
        val py = floatArrayOf(wy + 4 * dp, wy - lean * 0.45f, wy - lean * 0.88f, wy - lean)
        val n = 24
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        val half = FloatArray(n + 1)
        for (i in 0..n) {
            val t = i / n.toFloat()
            xs[i] = cubic(t, px[0], px[1], px[2], px[3])
            ys[i] = cubic(t, py[0], py[1], py[2], py[3])
            half[i] = (5.5f - 4f * t) * dp * s
        }
        for (i in 0..n) {
            val j = min(i + 1, n)
            val k = max(i - 1, 0)
            val dx = xs[j] - xs[k]
            val dy = ys[j] - ys[k]
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val nx = -dy / len * half[i]
            val ny = dx / len * half[i]
            if (i == 0) willow.moveTo(xs[i] + nx, ys[i] + ny) else willow.lineTo(xs[i] + nx, ys[i] + ny)
        }
        for (i in n downTo 0) {
            val j = min(i + 1, n)
            val k = max(i - 1, 0)
            val dx = xs[j] - xs[k]
            val dy = ys[j] - ys[k]
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            willow.lineTo(xs[i] - (-dy / len * half[i]), ys[i] - (dx / len * half[i]))
        }
        willow.close()
        out.clear()
        // A weeping crown: round masses gathered over the upper trunk, spilling down past its tip.
        val masses = 11
        for (m in 0 until masses) {
            val t = 0.45f + 0.62f * m / (masses - 1)
            val droop = max(0f, t - 0.85f) * 3.2f
            out += cubic(t.coerceAtMost(1f), px[0], px[1], px[2], px[3]) + max(0f, t - 1f) * w * 0.4f + (rand(m, seed + 729) - 0.5f) * 14 * dp * s
            out += cubic(t.coerceAtMost(1f), py[0], py[1], py[2], py[3]) + (droop * 26f - 10f + rand(m, seed + 727) * 12f) * dp * s
            out += (13f + rand(m, seed + 725) * 8f) * dp * s
            out += (11f + rand(m, seed + 723) * 6f) * dp * s
        }
        willowCrown = out.toFloatArray()
        willowTop = (0 until masses).minOf { willowCrown[it * 4 + 1] - willowCrown[it * 4 + 3] }
        // Fronds hang from under the crown towards the water.
        out.clear()
        val nFronds = (52 * detail).roundToInt().coerceIn(16, 56)
        for (i in 0 until nFronds) {
            val m = (rand(i, seed + 731) * masses).toInt().coerceAtMost(masses - 1)
            val cx = willowCrown[m * 4]
            val cy = willowCrown[m * 4 + 1]
            val rx = willowCrown[m * 4 + 2]
            val ry = willowCrown[m * 4 + 3]
            val bx = cx + (rand(i, seed + 733) - 0.5f) * 1.8f * rx
            val by = cy + ry * (0.1f + 0.6f * rand(i, seed + 735))
            out += bx; out += by
            out += min((wy - by) * (0.45f + 0.5f * rand(i, seed + 737)), 120 * dp * s).coerceAtLeast(8 * dp)
            out += rand(i, seed + 739) * 6.283f
        }
        fronds = out.toFloatArray()

        // Reeds and iris leaves on the near bank, mostly at the left like in the painting.
        out.clear()
        val nReeds = (18 * detail).roundToInt().coerceIn(6, 20)
        for (i in 0 until nReeds) {
            val left = i < nReeds * 0.75f
            val x = if (left) w * (0.01f + 0.2f * rand(i, seed + 741)) else w * (0.86f + 0.13f * rand(i, seed + 741))
            out += x; out += (34f + rand(i, seed + 743) * 46f) * dp * s
            out += (rand(i, seed + 745) - 0.35f) * 16 * dp * s; out += rand(i, seed + 747) * 6.283f
        }
        reeds = out.toFloatArray()

        // Flowers adrift: poppies, roses, forget-me-nots, buttercups, violets.
        out.clear()
        val nFlowers = (widthDp / 36f * detail).roundToInt().coerceIn(5, 16)
        for (i in 0 until nFlowers) {
            out += rand(i, seed + 751); out += 0.15f + 0.7f * rand(i, seed + 753)
            out += (2.5f + 4f * rand(i, seed + 755)) * dp; out += (i % 5).toFloat()
            out += (0.8f + 0.5f * rand(i, seed + 757)) * s
        }
        flowers = out.toFloatArray()

        // Still glints on the water.
        out.clear()
        val nGlints = (widthDp / 8f).roundToInt().coerceIn(10, 70)
        for (i in 0 until nGlints) {
            val x = rand(i, seed + 761) * w
            val top = edge(water, x)
            val bottom = edge(near, x)
            if (bottom - top < 6 * dp) continue
            out += x; out += top + (0.15f + 0.8f * rand(i, seed + 763)) * (bottom - top); out += (6f + 22f * rand(i, seed + 765)) * dp
        }
        glints = out.toFloatArray()
    }

    private fun cubic(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
        val u = 1 - t
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
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
            // The rose bank carries the willow and the loosestrife.
            if (variant == SceneVariant.River && b == 2) top = min(top - 20 * dp, if (willowTop.isNaN()) top else willowTop - 30 * dp)
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
        if (detail < 0.5f || variant == SceneVariant.River) return
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
