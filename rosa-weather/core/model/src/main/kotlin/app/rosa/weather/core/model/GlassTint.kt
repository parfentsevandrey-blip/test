package app.rosa.weather.core.model

import kotlin.math.abs

/**
 * The colour of the glass in [Appearance.Tinted]. The user picks only a hue; how light or deep
 * the glass and the accent are is worked out for the sky behind them, by relative luminance, so
 * that every hue keeps the type on the glass and the accent on screen equally legible: pale,
 * luminous glass under dark ink over a bright sky, deep glass under light ink over a dark one.
 * (By lightness alone, yellow glass would glare and blue glass would go murky.)
 */
object GlassTint {
    /** Rose, the app's namesake. */
    const val DEFAULT_HUE = 340

    /** The named hues offered as swatches, in HSL degrees; any other is a slide of the finger away. */
    val Swatches = listOf(340, 10, 36, 150, 178, 205, 234, 274)

    /** The glass's own tone. */
    fun glass(hue: Int, lightSky: Boolean): Argb =
        if (lightSky) tone(hue, saturation = 0.9, luminance = 0.6) else tone(hue, saturation = 0.78, luminance = 0.045)

    /** The accent: selections, switches, the lens of the tab bar, highlighted type. */
    fun accent(hue: Int, lightSky: Boolean): Argb =
        if (lightSky) tone(hue, saturation = 0.85, luminance = 0.085) else tone(hue, saturation = 0.95, luminance = 0.46)

    /** Light held in the coloured glass, glowing inside its rim. */
    fun glow(hue: Int): Argb = tone(hue, saturation = 1.0, luminance = 0.4)

    /** The hue at its most vivid, as the picker shows it. */
    fun swatch(hue: Int): Argb = hsl(hue, saturation = 0.82, lightness = 0.58)

    /** The hue at [saturation], as light as it needs to be to have the relative [luminance] asked for. */
    fun tone(hue: Int, saturation: Double, luminance: Double): Argb {
        // Luminance only grows with lightness at a fixed hue and saturation: halve the interval.
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2
            if (hsl(hue, saturation, mid).luminance < luminance) lo = mid else hi = mid
        }
        return hsl(hue, saturation, (lo + hi) / 2)
    }

    fun hsl(hue: Int, saturation: Double, lightness: Double): Argb {
        val h = (((hue % 360) + 360) % 360) / 60.0
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val x = c * (1 - abs(h % 2 - 1))
        val m = lightness - c / 2
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun channel(v: Double) = ((v + m) * 255 + 0.5).toInt().coerceIn(0, 255)
        return Argb.rgb(channel(r), channel(g), channel(b))
    }
}
