package app.quire.engine.design

import androidx.annotation.ColorInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU: Float = 6.2831855f
private const val INV_255: Float = 1f / 255f

// Chroma is found by bisection over a range no wider than ~0.4, so twelve halvings land well
// inside the eight-bit step the result is quantised to anyway.
private const val GAMUT_STEPS: Int = 12

// A single-precision round trip through two matrices and a cube leaves a channel a hair outside
// 0..1 for colours that genuinely sit on the boundary; this is the width of that hair.
private const val GAMUT_SLACK: Float = 1e-4f

/** Perceptual colour, so generated palettes hold their contrast instead of drifting. */
object Oklch {

    /**
     * Splits [colour] into perceptual coordinates, writing [L, C, H] into [out]: lightness 0..1,
     * chroma from 0, hue in radians 0..2π. Alpha is not part of the split.
     */
    fun fromSrgb(@ColorInt colour: Int, out: FloatArray) {
        require(out.size >= 3) { "out must hold at least three floats" }
        withOklab(colour) { l, a, b ->
            out[0] = l
            out[1] = sqrt(a * a + b * b)
            out[2] = normaliseHue(atan2(b, a))
        }
    }

    /**
     * Builds a colour from perceptual coordinates, pulling chroma in until the result is one
     * sRGB can actually show. Lightness is held while chroma gives way, because lightness is
     * what carries contrast and chroma is what carries only enthusiasm.
     */
    @ColorInt
    fun toSrgb(l: Float, c: Float, h: Float, alpha: Float = 1f): Int {
        val lightness = l.coerceIn(0f, 1f)
        val cosH = cos(h)
        val sinH = sin(h)
        var chroma = max(c, 0f)
        if (!fits(lightness, chroma * cosH, chroma * sinH)) {
            var low = 0f
            var high = chroma
            var i = 0
            while (i < GAMUT_STEPS) {
                val mid = (low + high) * 0.5f
                if (fits(lightness, mid * cosH, mid * sinH)) low = mid else high = mid
                i++
            }
            chroma = low
        }
        return pack(lightness, chroma * cosH, chroma * sinH, alpha)
    }

    /** Moves [colour] along the lightness axis, keeping its hue, chroma and alpha. */
    @ColorInt
    fun lighten(@ColorInt colour: Int, byL: Float): Int = withOklab(colour) { l, a, b ->
        toSrgb(l + byL, sqrt(a * a + b * b), atan2(b, a), alphaOf(colour))
    }

    /**
     * Resets how saturated [colour] is without moving its lightness, for muting a colour without
     * spending the contrast it had. A grey has no hue to keep, so it re-saturates towards red.
     */
    @ColorInt
    fun withChroma(@ColorInt colour: Int, c: Float): Int = withOklab(colour) { l, a, b ->
        toSrgb(l, c, atan2(b, a), alphaOf(colour))
    }

    /** Turns [colour] around the hue circle, for deriving one hue from another. */
    @ColorInt
    fun rotateHue(@ColorInt colour: Int, radians: Float): Int = withOklab(colour) { l, a, b ->
        toSrgb(l, sqrt(a * a + b * b), atan2(b, a) + radians, alphaOf(colour))
    }

    /** WCAG-style relative luminance contrast, 1..21. */
    fun contrast(@ColorInt a: Int, @ColorInt b: Int): Float {
        // Both colours are read as opaque: what a translucent colour really contrasts with is
        // whatever ends up behind it, which is the caller's to composite first.
        val la = luminance(a)
        val lb = luminance(b)
        return if (la >= lb) (la + 0.05f) / (lb + 0.05f) else (lb + 0.05f) / (la + 0.05f)
    }

    /** Picks whichever of [light] or [dark] reads better on [background]. */
    @ColorInt
    fun readableOn(@ColorInt background: Int, @ColorInt light: Int, @ColorInt dark: Int): Int =
        if (contrast(background, light) >= contrast(background, dark)) light else dark

    /**
     * Mixes [a] towards [b] by [t], perceptually, so the halfway point of two colours is the
     * colour halfway between them rather than the muddy one channel averaging produces. Alpha is
     * mixed alongside, but a fully transparent colour still contributes its channels.
     */
    @ColorInt
    fun blend(@ColorInt a: Int, @ColorInt b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return withOklab(a) { la, aa, ba ->
            withOklab(b) { lb, ab, bb ->
                val l = la + (lb - la) * k
                // Mixed in rectangular Oklab rather than polar: interpolating hue would take the
                // long way round for opposite hues, and has no defined direction through grey.
                val x = aa + (ab - aa) * k
                val y = ba + (bb - ba) * k
                val alpha = alphaOf(a) + (alphaOf(b) - alphaOf(a)) * k
                toSrgb(l, sqrt(x * x + y * y), atan2(y, x), alpha)
            }
        }
    }

    // The conversions hand their three results to a lambda rather than filling a scratch array:
    // inlined, this costs no object and keeps every entry point reentrant and thread-safe, which
    // matters because the widget renderer and the interface both call in here.
    private inline fun <R> withOklab(colour: Int, body: (l: Float, a: Float, b: Float) -> R): R {
        val r = linear(((colour shr 16) and 0xFF) * INV_255)
        val g = linear(((colour shr 8) and 0xFF) * INV_255)
        val b = linear((colour and 0xFF) * INV_255)
        val cl = cubeRoot(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val cm = cubeRoot(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val cs = cubeRoot(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return body(
            0.2104542553f * cl + 0.7936177850f * cm - 0.0040720468f * cs,
            1.9779984951f * cl - 2.4285922050f * cm + 0.4505937099f * cs,
            0.0259040371f * cl + 0.7827717662f * cm - 0.8086757660f * cs,
        )
    }

    private inline fun <R> withLinearRgb(
        l: Float,
        a: Float,
        b: Float,
        body: (r: Float, g: Float, b: Float) -> R,
    ): R {
        val cl = l + 0.3963377774f * a + 0.2158037573f * b
        val cm = l - 0.1055613458f * a - 0.0638541728f * b
        val cs = l - 0.0894841775f * a - 1.2914855480f * b
        val ll = cl * cl * cl
        val mm = cm * cm * cm
        val ss = cs * cs * cs
        return body(
            4.0767416621f * ll - 3.3077115913f * mm + 0.2309699292f * ss,
            -1.2684380046f * ll + 2.6097574011f * mm - 0.3413193965f * ss,
            -0.0041960863f * ll - 0.7034186147f * mm + 1.7076147010f * ss,
        )
    }

    private fun fits(l: Float, a: Float, b: Float): Boolean =
        withLinearRgb(l, a, b) { r, g, blue -> showable(r) && showable(g) && showable(blue) }

    private fun pack(l: Float, a: Float, b: Float, alpha: Float): Int =
        withLinearRgb(l, a, b) { r, g, blue ->
            val al = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            (al shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(blue)
        }

    private fun showable(v: Float): Boolean = v >= -GAMUT_SLACK && v <= 1f + GAMUT_SLACK

    private fun channel(linearValue: Float): Int {
        val v = gamma(linearValue.coerceIn(0f, 1f))
        return (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun linear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun gamma(c: Float): Float =
        if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

    // Every cone response reaching here is a positive combination of non-negative channels, so
    // the guard catches rounding noise rather than a real negative.
    private fun cubeRoot(v: Float): Float = if (v <= 0f) 0f else v.pow(1f / 3f)

    private fun luminance(colour: Int): Float {
        val r = linear(((colour shr 16) and 0xFF) * INV_255)
        val g = linear(((colour shr 8) and 0xFF) * INV_255)
        val b = linear((colour and 0xFF) * INV_255)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun alphaOf(colour: Int): Float = ((colour ushr 24) and 0xFF) * INV_255

    private fun normaliseHue(h: Float): Float {
        val v = h % TAU
        return if (v < 0f) v + TAU else v
    }
}
