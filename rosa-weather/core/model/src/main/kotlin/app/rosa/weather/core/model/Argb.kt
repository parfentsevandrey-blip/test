package app.rosa.weather.core.model

import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Platform-free packed ARGB colour, so palettes can be computed and unit-tested on the JVM and
 * then handed to Compose (`Color(argb.value)`) or `android.graphics.Paint` unchanged.
 */
@JvmInline
value class Argb(val value: Int) {
    val alpha: Int get() = value ushr 24 and 0xFF
    val red: Int get() = value shr 16 and 0xFF
    val green: Int get() = value shr 8 and 0xFF
    val blue: Int get() = value and 0xFF

    fun withAlpha(alpha: Float): Argb =
        Argb((alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24 or (value and 0x00FFFFFF))

    /** Relative luminance (WCAG), 0..1. */
    val luminance: Double
        get() = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)

    fun lerp(to: Argb, t: Float): Argb = oklabLerp(this, to, t)

    companion object {
        val White = Argb(0xFFFFFFFF.toInt())
        val Black = Argb(0xFF000000.toInt())

        fun hex(rgb: Long): Argb = Argb((0xFF000000 or rgb).toInt())

        fun rgb(r: Int, g: Int, b: Int, a: Int = 255): Argb =
            Argb((a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF))

        private fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.040_45) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        private fun gamma(linear: Double): Int {
            val c = linear.coerceIn(0.0, 1.0)
            val s = if (c <= 0.003_130_8) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
            return (s * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }

        /**
         * Perceptual interpolation in OKLab: twilight gradients stay luminous instead of turning
         * muddy grey halfway between blue and orange the way sRGB interpolation does.
         */
        fun oklabLerp(a: Argb, b: Argb, t: Float): Argb {
            val tt = t.coerceIn(0f, 1f).toDouble()
            val la = toOklab(a)
            val lb = toOklab(b)
            val l = la[0] + (lb[0] - la[0]) * tt
            val m = la[1] + (lb[1] - la[1]) * tt
            val s = la[2] + (lb[2] - la[2]) * tt
            val alpha = a.alpha + (b.alpha - a.alpha) * tt
            return fromOklab(l, m, s, alpha.toInt())
        }

        private fun toOklab(c: Argb): DoubleArray {
            val r = linear(c.red)
            val g = linear(c.green)
            val b = linear(c.blue)
            val l = cbrt(0.412_221_470_8 * r + 0.536_332_536_3 * g + 0.051_445_992_9 * b)
            val m = cbrt(0.211_903_498_2 * r + 0.680_699_545_1 * g + 0.107_396_956_6 * b)
            val s = cbrt(0.088_302_461_9 * r + 0.281_718_837_6 * g + 0.629_978_700_5 * b)
            return doubleArrayOf(
                0.210_454_255_3 * l + 0.793_617_785_0 * m - 0.004_072_046_8 * s,
                1.977_998_495_1 * l - 2.428_592_205_0 * m + 0.450_593_709_9 * s,
                0.025_904_037_1 * l + 0.782_771_766_2 * m - 0.808_675_766_0 * s,
            )
        }

        private fun fromOklab(lightness: Double, a: Double, b: Double, alpha: Int): Argb {
            val l = (lightness + 0.396_337_777_4 * a + 0.215_803_757_3 * b).pow(3)
            val m = (lightness - 0.105_561_345_8 * a - 0.063_854_172_8 * b).pow(3)
            val s = (lightness - 0.089_484_177_5 * a - 1.291_485_548_0 * b).pow(3)
            val r = 4.076_741_662_1 * l - 3.307_711_591_3 * m + 0.230_969_929_2 * s
            val g = -1.268_438_004_6 * l + 2.609_757_401_1 * m - 0.341_319_396_5 * s
            val bl = -0.004_196_086_3 * l - 0.703_418_614_7 * m + 1.707_614_701_0 * s
            return rgb(gamma(r), gamma(g), gamma(bl), alpha)
        }
    }
}
