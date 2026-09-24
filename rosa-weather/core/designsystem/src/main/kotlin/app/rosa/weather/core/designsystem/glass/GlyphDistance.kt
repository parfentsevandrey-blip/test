package app.rosa.weather.core.designsystem.glass

import kotlin.math.sqrt

/**
 * Signed distance to the edge of a coverage mask, in pixels: positive inside the shape, negative
 * outside. It is what lets glass numerals have a real bevel: every stroke, hairline or stem, gets
 * the same rounded rim measured from its own edge, where a blurred mask would leave thick stems
 * flat and thin ones faint.
 *
 * Exact Euclidean distance transform (Felzenszwalb & Huttenlocher), refined to sub-pixel accuracy
 * on anti-aliased edge pixels and smoothed once so surface normals don't show the pixel grid.
 */
internal object GlyphDistance {
    private const val FAR = 1e20f

    /** @param alpha coverage 0..255 per pixel, row-major. */
    fun signed(alpha: IntArray, width: Int, height: Int): FloatArray {
        val n = width * height
        val inside = FloatArray(n)
        val outside = FloatArray(n)
        for (i in 0 until n) {
            val covered = alpha[i] >= 128
            inside[i] = if (covered) FAR else 0f // distance to the nearest outside pixel
            outside[i] = if (covered) 0f else FAR // distance to the nearest inside pixel
        }
        transform(inside, width, height)
        transform(outside, width, height)
        val d = FloatArray(n)
        for (i in 0 until n) {
            val a = alpha[i]
            d[i] = when {
                // An edge runs through this pixel: its coverage says how far, better than the grid.
                a in 1..254 -> a / 255f - 0.5f
                a >= 128 -> sqrt(inside[i]) - 0.5f
                else -> 0.5f - sqrt(outside[i])
            }
        }
        return smooth(d, width, height)
    }

    /** In-place 2-D squared distance transform: columns, then rows. */
    private fun transform(grid: FloatArray, width: Int, height: Int) {
        val size = maxOf(width, height)
        val f = FloatArray(size)
        val out = FloatArray(size)
        val v = IntArray(size)
        val z = FloatArray(size + 1)
        for (x in 0 until width) {
            for (y in 0 until height) f[y] = grid[y * width + x]
            line(f, height, out, v, z)
            for (y in 0 until height) grid[y * width + x] = out[y]
        }
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) f[x] = grid[row + x]
            line(f, width, out, v, z)
            for (x in 0 until width) grid[row + x] = out[x]
        }
    }

    /** 1-D squared distance transform of [f]: the lower envelope of parabolas rooted at each sample. */
    private fun line(f: FloatArray, n: Int, out: FloatArray, v: IntArray, z: FloatArray) {
        fun cross(q: Int, p: Int) = ((f[q] + q * q) - (f[p] + p * p)) / (2f * q - 2f * p)
        var k = 0
        v[0] = 0
        z[0] = Float.NEGATIVE_INFINITY
        z[1] = Float.POSITIVE_INFINITY
        for (q in 1 until n) {
            var s = cross(q, v[k])
            while (s <= z[k]) {
                k--
                s = cross(q, v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.POSITIVE_INFINITY
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val p = v[k]
            out[q] = (q - p).toFloat() * (q - p) + f[p]
        }
    }

    /** One 3×3 box pass: removes the grid's facets from the gradient, keeps the edge in place. */
    private fun smooth(d: FloatArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(d.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= height) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= width) continue
                        sum += d[yy * width + xx]
                        count++
                    }
                }
                out[y * width + x] = sum / count
            }
        }
        return out
    }
}
