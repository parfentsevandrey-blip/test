package app.rosa.weather.widget.render.calendar

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Deterministic value noise and its fractal sums: the grain of skies, hills, fog and foliage. The
 * same arguments always give the same value, so a month paints the same picture every time.
 */
internal object Noise {
    fun rand(x: Int, y: Int, seed: Int): Float {
        var h = seed * 0x3C6EF372 + x * 0x27D4EB2D + y * 0x165667B1
        h = (h xor (h ushr 15)) * 0x2C1B3C6D
        h = (h xor (h ushr 12)) * 0x297A2D39
        h = h xor (h ushr 15)
        return (h ushr 8) / 16_777_216f
    }

    fun value(x: Float, y: Float, seed: Int): Float {
        val fx = floor(x)
        val fy = floor(y)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val tx = x - fx
        val ty = y - fy
        val u = tx * tx * (3f - 2f * tx)
        val v = ty * ty * (3f - 2f * ty)
        val a = rand(ix, iy, seed)
        val b = rand(ix + 1, iy, seed)
        val c = rand(ix, iy + 1, seed)
        val d = rand(ix + 1, iy + 1, seed)
        val top = a + (b - a) * u
        val bottom = c + (d - c) * u
        return top + (bottom - top) * v
    }

    /** A fractal sum of [octaves] of [value] noise, 0..1. */
    fun fbm(x: Float, y: Float, seed: Int, octaves: Int = 5, gain: Float = 0.5f): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var fx = x
        var fy = y
        for (o in 0 until octaves) {
            sum += value(fx, fy, seed + o * 1013) * amp
            norm += amp
            amp *= gain
            fx = fx * 2.02f + 5.3f
            fy = fy * 2.02f + 1.7f
        }
        return sum / norm
    }

    /**
     * Cellular noise: the jittered points of a grid, and for ([x], [y]) the distance to the
     * nearest ([Cell.f1]) and the next nearest ([Cell.f2]) of them, the offset from the nearest
     * ([Cell.dx], [Cell.dy]) and its id. Leaves gather into clumps round the points; where two
     * clumps meet (f2 - f1 small) lies the crevice between them.
     */
    fun cellular(x: Float, y: Float, seed: Int, out: Cell): Cell {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        var f1 = 9f
        var f2 = 9f
        var dx1 = 0f
        var dy1 = 0f
        var id = 0
        for (j in -1..1) {
            for (i in -1..1) {
                val cx = ix + i
                val cy = iy + j
                val px = cx + 0.15f + 0.7f * rand(cx, cy, seed)
                val py = cy + 0.15f + 0.7f * rand(cx, cy, seed + 17)
                val dx = x - px
                val dy = y - py
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < f1) {
                    f2 = f1
                    f1 = d
                    dx1 = dx
                    dy1 = dy
                    id = cx * 73856093 xor cy * 19349663
                } else if (d < f2) {
                    f2 = d
                }
            }
        }
        out.f1 = f1
        out.f2 = f2
        out.dx = dx1
        out.dy = dy1
        out.id = id
        return out
    }

    class Cell {
        var f1 = 0f
        var f2 = 0f
        var dx = 0f
        var dy = 0f
        var id = 0

        /** A value 0..1 fixed for this cell. */
        fun pick(seed: Int): Float = rand(id, seed, 7)
    }

    /** Sharp crests where plain noise has soft ones: mountain ridges. 0..1. */
    fun ridged(x: Float, y: Float, seed: Int, octaves: Int = 5): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var fx = x
        var fy = y
        var weight = 1f
        for (o in 0 until octaves) {
            var n = 1f - abs(value(fx, fy, seed + o * 1013) * 2f - 1f)
            n *= n * weight
            weight = (n * 1.6f).coerceIn(0f, 1f)
            sum += n * amp
            norm += amp
            amp *= 0.5f
            fx = fx * 2.1f + 3.1f
            fy = fy * 2.1f + 7.7f
        }
        return sum / norm
    }
}

/** Colours as plain ARGB ints, mixed in sRGB: fast enough for every pixel of a painting. */
internal object Tone {
    fun of(rgb: Long): Int = (0xFF000000 or rgb).toInt()

    fun alpha(color: Int, a: Float): Int = (color and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val aa = a ushr 24
        val ar = a shr 16 and 0xFF
        val ag = a shr 8 and 0xFF
        val ab = a and 0xFF
        val ba = b ushr 24
        val br = b shr 16 and 0xFF
        val bg = b shr 8 and 0xFF
        val bb = b and 0xFF
        return ((aa + (ba - aa) * k).roundToInt() shl 24) or
            ((ar + (br - ar) * k).roundToInt() shl 16) or
            ((ag + (bg - ag) * k).roundToInt() shl 8) or
            (ab + (bb - ab) * k).roundToInt()
    }

    /** [color] made lighter (towards white, [t] > 0) or darker (towards black, [t] < 0). */
    fun shade(color: Int, t: Float): Int = if (t >= 0f) mix(color, color or 0xFFFFFF, t) else mix(color, color and 0xFF000000.toInt(), -t)

    fun luminance(color: Int): Float = ((color shr 16 and 0xFF) * 0.2126f + (color shr 8 and 0xFF) * 0.7152f + (color and 0xFF) * 0.0722f) / 255f

    /** A smooth 0..1 step between [edge0] and [edge1]. */
    fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
