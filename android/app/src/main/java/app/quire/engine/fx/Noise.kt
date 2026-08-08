package app.quire.engine.fx

import kotlin.math.floor

/** Deterministic value noise, no allocation, for grain, warp and organic drift. */
object Noise {

    // Murmur3's finaliser. Any integer hash would do; this one is chosen because its avalanche
    // is good enough that neighbouring lattice cells share no visible structure, which is the
    // whole reason the noise looks organic rather than woven.
    private const val MIX_A: Int = 0x85EBCA6B.toInt()
    private const val MIX_B: Int = 0xC2B2AE35.toInt()

    // Odd co-prime multipliers, so a lattice coordinate cannot collide with another axis.
    private const val KEY_X: Int = 0x1B873593
    private const val KEY_Y: Int = 0x2545F491
    private const val KEY_SEED: Int = 0x27D4EB2D
    private const val KEY_OCTAVE: Int = 8191
    private const val ORIGIN: Int = 0x3C6EF35F

    // The hash is folded to 24 bits and spread over -1..1. 24 bits is more resolution than an
    // 8-bit channel can show, and it divides exactly into a float mantissa.
    private const val INV_HALF_SPAN: Float = 1f / 8388608f

    // Past eight octaves the term is smaller than one step of an 8-bit channel, so summing
    // more only costs time.
    private const val MAX_OCTAVES: Int = 8

    /** Noise along one axis, in -1..1; for drifting a single value with no clock. */
    fun value1(x: Float, seed: Int = 0): Float {
        val cell = floor(x)
        val index = cell.toInt()
        val t = smooth(x - cell)
        val a = corner(index, 0, seed)
        val b = corner(index + 1, 0, seed)
        return a + (b - a) * t
    }

    /** Noise over a plane, in -1..1; for grain, warp fields and per-pixel mottle. */
    fun value2(x: Float, y: Float, seed: Int = 0): Float {
        val cellX = floor(x)
        val cellY = floor(y)
        val ix = cellX.toInt()
        val iy = cellY.toInt()
        val tx = smooth(x - cellX)
        val ty = smooth(y - cellY)
        val c00 = corner(ix, iy, seed)
        val c10 = corner(ix + 1, iy, seed)
        val c01 = corner(ix, iy + 1, seed)
        val c11 = corner(ix + 1, iy + 1, seed)
        val top = c00 + (c10 - c00) * tx
        val bottom = c01 + (c11 - c01) * tx
        return top + (bottom - top) * ty
    }

    /**
     * Sum of octaves; amplitude halves each octave. Stays in -1..1, and clamps [octaves] to
     * 1..8 so a caller cannot ask for terms that cost time and change nothing.
     */
    fun fbm(x: Float, y: Float, octaves: Int = 3, seed: Int = 0): Float {
        val terms = octaves.coerceIn(1, MAX_OCTAVES)
        var sum = 0f
        var total = 0f
        var amplitude = 1f
        var px = x
        var py = y
        var i = 0
        while (i < terms) {
            sum += value2(px, py, seed + i * KEY_OCTAVE) * amplitude
            total += amplitude
            amplitude *= 0.5f
            // Octaves are shifted as well as doubled: doubling alone leaves every octave
            // sharing the lattice corners at whole coordinates, and their zeroes line up
            // into a visible grid.
            px = px * 2f + 71.3f
            py = py * 2f + 37.9f
            i++
        }
        return sum / total
    }

    // Smootherstep rather than smoothstep: its second derivative is continuous, so a value
    // used as a position does not visibly kick as it crosses a lattice line.
    private fun smooth(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun corner(x: Int, y: Int, seed: Int): Float {
        var h = ORIGIN + x * KEY_X + y * KEY_Y + seed * KEY_SEED
        h = h xor (h ushr 16)
        h *= MIX_A
        h = h xor (h ushr 13)
        h *= MIX_B
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) * INV_HALF_SPAN - 1f
    }
}
