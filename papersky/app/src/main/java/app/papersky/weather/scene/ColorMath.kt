package app.papersky.weather.scene

import kotlin.math.pow
import kotlin.math.roundToInt

/** Small ARGB helpers shared by the renderer, palettes and widgets (no Compose dependency). */
object ColorMath {
    fun a(c: Int) = c ushr 24 and 0xFF
    fun r(c: Int) = c shr 16 and 0xFF
    fun g(c: Int) = c shr 8 and 0xFF
    fun b(c: Int) = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun lerp(x: Int, y: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return argb(
            (a(x) + (a(y) - a(x)) * f).roundToInt(),
            (r(x) + (r(y) - r(x)) * f).roundToInt(),
            (g(x) + (g(y) - g(x)) * f).roundToInt(),
            (b(x) + (b(y) - b(x)) * f).roundToInt(),
        )
    }

    fun withAlpha(c: Int, alpha: Float): Int = (c and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    fun scaleAlpha(c: Int, factor: Float): Int = withAlpha(c, a(c) / 255f * factor)

    /** WCAG relative luminance, 0..1. */
    fun luminance(c: Int): Float {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return (0.2126 * ch(r(c)) + 0.7152 * ch(g(c)) + 0.0722 * ch(b(c))).toFloat()
    }

    fun contrast(x: Int, y: Int): Float {
        val l1 = luminance(x)
        val l2 = luminance(y)
        return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
    }

    fun darken(c: Int, amount: Float) = lerp(c, c and 0xFF000000.toInt(), amount)
    fun lighten(c: Int, amount: Float) = lerp(c, (c and 0xFF000000.toInt()) or 0xFFFFFF, amount)
}

/** Deterministic, allocation-free pseudo random numbers in [0, 1). */
internal fun rand(i: Int, salt: Int): Float {
    var x = i * 374_761_393 + salt * 668_265_263
    x = (x xor (x ushr 13)) * 1_274_126_177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16_777_216f
}

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

internal fun fract(x: Float): Float = x - kotlin.math.floor(x)
