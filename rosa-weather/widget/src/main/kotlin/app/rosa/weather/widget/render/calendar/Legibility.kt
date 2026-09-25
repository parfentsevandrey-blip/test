package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.get
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG's contrast arithmetic: relative luminance, and the ratio between two of them. */
internal object Contrast {
    /** Everything a calendar shows to be read — days, names, dates — keeps this (WCAG AA). */
    const val TEXT = 4.5f

    /** The neighbouring months' days: dimmed on purpose, never lost. */
    const val SECONDARY = 3f

    /**
     * What each colour is held to above those: the drawn strokes' soft edges, and a ground that
     * varies within a cell, take a little of any contrast worked out to the letter.
     */
    const val MARGIN = 0.5f

    private val LINEAR = FloatArray(256) { v ->
        val c = v / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    fun luminance(color: Int): Float =
        0.2126f * LINEAR[color shr 16 and 0xFF] + 0.7152f * LINEAR[color shr 8 and 0xFF] + 0.0722f * LINEAR[color and 0xFF]

    fun ratio(a: Float, b: Float): Float = (max(a, b) + 0.05f) / (min(a, b) + 0.05f)

    fun ratio(a: Int, b: Int): Float = ratio(luminance(a), luminance(b))
}

/**
 * What text stands on: its darkest and its lightest (as relative luminance) and its average
 * colour. Flat for paper or a tonal pane; measured from the painting for the glass laid over it.
 */
internal class Backing(val low: Float, val high: Float, val mean: Int) {
    /** Light text belongs on it (past the point where white and black contrast equally with it). */
    val dark: Boolean get() = Contrast.luminance(mean) < 0.18f

    /** The worst contrast [ink] gets anywhere on this ground. */
    fun contrast(ink: Int): Float {
        val l = Contrast.luminance(ink)
        return min(Contrast.ratio(l, low), Contrast.ratio(l, high))
    }

    /**
     * [color] moved toward [toward] — as little as it takes — until it keeps [target] against
     * every part of this ground: an accent lightened over dark glass, deepened over light.
     */
    fun legible(color: Int, target: Float, toward: Int): Int {
        if (contrast(color) >= target) return color
        for (i in 1..20) {
            val c = Tone.mix(color, toward, i / 20f)
            if (contrast(c) >= target) return c
        }
        return toward
    }

    /**
     * [ink] faded into the ground — as far as [most], never below [target]: the softer tones of
     * days gone by and of other months, which still read.
     */
    fun faded(ink: Int, target: Float, most: Float): Int {
        var best = ink
        for (i in 1..20) {
            val c = Tone.mix(ink, mean, most * i / 20f)
            if (contrast(c) < target) break
            best = c
        }
        return best
    }

    companion object {
        fun flat(color: Int): Backing {
            val l = Contrast.luminance(color)
            return Backing(l, l, color)
        }

        fun between(a: Int, b: Int): Backing {
            val la = Contrast.luminance(a)
            val lb = Contrast.luminance(b)
            return Backing(min(la, lb), max(la, lb), Tone.mix(a, b, 0.5f))
        }

        /**
         * Glass over [frost] (a painting frosted, [scale] of its pixels per dp), within [area] dp:
         * how thick its [tint] must lie for [ink] to keep [target] everywhere there — at least
         * [least], the look chosen, at most [most] — and the ground it leaves. Only the very
         * darkest and lightest specks are left out: a day's number may stand anywhere, over the
         * sun or the fire as over the shadows.
         */
        fun under(frost: Bitmap, scale: Float, area: RectF, tint: Int, ink: Int, least: Float, most: Float = 0.92f, target: Float = Contrast.TEXT + Contrast.MARGIN): Pair<Float, Backing> {
            val x0 = (area.left * scale).toInt().coerceIn(0, frost.width - 1)
            val y0 = (area.top * scale).toInt().coerceIn(0, frost.height - 1)
            val x1 = (area.right * scale).toInt().coerceIn(x0 + 1, frost.width)
            val y1 = (area.bottom * scale).toInt().coerceIn(y0 + 1, frost.height)
            val w = x1 - x0
            val n = w * (y1 - y0)
            val pixels = IntArray(n)
            frost.getPixels(pixels, 0, w, x0, y0, w, y1 - y0)
            val lum = FloatArray(n) { Contrast.luminance(pixels[it]) }
            val order = (0 until n).sortedBy { lum[it] }
            val darkest = pixels[order[(n * 0.005f).toInt().coerceAtMost(n - 1)]]
            val lightest = pixels[order[(n * 0.995f).toInt().coerceAtMost(n - 1)]]
            var r = 0L
            var g = 0L
            var b = 0L
            for (p in pixels) {
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            val mean = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            var alpha = least.coerceIn(0f, most)
            while (true) {
                val ground = between(Tone.mix(darkest, tint, alpha), Tone.mix(lightest, tint, alpha)).let { Backing(it.low, it.high, Tone.mix(mean, tint, alpha)) }
                if (ground.contrast(ink) >= target || alpha >= most) return alpha to ground
                alpha = min(most, alpha + 0.02f)
            }
        }

        /**
         * A shade laid over [picture] within [area] dp ([scale] of its pixels per dp) so [ink] keeps
         * [target] over it: the shade's alpha in [least]..[most] (of [shade]) — none if the
         * picture there is already dark (or light) enough.
         */
        fun shade(picture: Bitmap, scale: Float, area: RectF, shade: Int, ink: Int, least: Float, most: Float, target: Float = Contrast.TEXT + Contrast.MARGIN): Float {
            val x0 = (area.left * scale).toInt().coerceIn(0, picture.width - 1)
            val y0 = (area.top * scale).toInt().coerceIn(0, picture.height - 1)
            val x1 = (area.right * scale).toInt().coerceIn(x0 + 1, picture.width)
            val y1 = (area.bottom * scale).toInt().coerceIn(y0 + 1, picture.height)
            // A coarse grid of samples is enough to know the light behind a line of text.
            val stepX = max(1, (x1 - x0) / 48)
            val stepY = max(1, (y1 - y0) / 16)
            val lums = ArrayList<Pair<Float, Int>>()
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    val p = picture[x, y]
                    lums += Contrast.luminance(p) to p
                    x += stepX
                }
                y += stepY
            }
            lums.sortBy { it.first }
            val light = Contrast.luminance(ink) > 0.4f
            val worst = if (light) lums[(lums.size * 0.92f).toInt().coerceAtMost(lums.size - 1)].second else lums[(lums.size * 0.08f).toInt()].second
            var alpha = least
            while (alpha < most && Contrast.ratio(ink, Tone.mix(worst, shade, alpha)) < target) alpha += 0.02f
            return alpha.coerceAtMost(most)
        }
    }
}

/** The colours text takes on one render's ground, each held to its contrast there. */
internal class Inks(
    /** This month's days to come. */
    val day: Int,
    /** Days gone by: softer, still read. */
    val past: Int,
    val weekend: Int,
    val pastWeekend: Int,
    /** The neighbouring months' days filling the first and last weeks. */
    val faint: Int,
    /** Weekday names, week numbers, the forecast's temperatures: small print. */
    val label: Int,
    val labelWeekend: Int,
) {
    companion object {
        fun of(ink: Int, accent: Int, ground: Backing): Inks {
            val toward = if (ground.dark) 0xFFFFFFFF.toInt() else 0xFF0B0F1C.toInt()
            val text = Contrast.TEXT + Contrast.MARGIN
            val day = ground.legible(ink, text, toward)
            val weekend = ground.legible(Tone.mix(accent, ink, 0.2f), text, toward)
            return Inks(
                day = day,
                past = ground.faded(day, text, 0.4f),
                weekend = weekend,
                pastWeekend = ground.faded(weekend, text, 0.35f),
                faint = ground.faded(day, Contrast.SECONDARY + Contrast.MARGIN, 0.7f),
                label = ground.faded(day, text, 0.3f),
                labelWeekend = ground.faded(weekend, text, 0.15f),
            )
        }
    }
}

/**
 * The header's type — the month's name, the day under it — and the shade under it: over the
 * painting, light type on a dark veil or dark type on a pale one, whichever the sky there needs
 * less of; on glass or paper, the ground's own inks and no shade.
 */
internal class HeaderTone(
    val ink: Int,
    val soft: Int,
    /** Light type (over something dark). */
    val dark: Boolean,
    val shadow: Boolean,
    /** How dense the shade under it must be, 0..1, in [shade]'s colour. */
    val scrim: Float,
    val shade: Int,
) {
    companion object {
        private const val LIGHT = 0xFFFFFBF5.toInt()
        private const val DARK = 0xFF1B2030.toInt()
        private const val NIGHT = 0xFF0A0E1C.toInt()
        private const val PALE = 0xFFFFFFFF.toInt()

        fun on(inks: Inks, ground: Backing, shadow: Boolean) = HeaderTone(inks.day, inks.label, ground.dark, shadow, 0f, 0)

        /** Type standing on [painting] ([scale] of its pixels per dp) within [box] dp. */
        fun over(painting: android.graphics.Bitmap, scale: Float, box: android.graphics.RectF, night: Boolean): HeaderTone {
            val lightSoft = Tone.mix(LIGHT, 0xFFC4CCDA.toInt(), 0.22f)
            val darkSoft = Tone.mix(DARK, 0xFF4A5264.toInt(), 0.3f)
            val forLight = Backing.shade(painting, scale, box, NIGHT, lightSoft, least = 0.1f, most = 0.72f)
            val forDark = Backing.shade(painting, scale, box, PALE, darkSoft, least = 0.1f, most = 0.72f)
            // Light type is the painting's own way; only a clearly paler sky takes dark type.
            return if (night || forLight <= forDark + 0.12f) {
                HeaderTone(LIGHT, lightSoft, dark = true, shadow = true, scrim = forLight, shade = NIGHT)
            } else {
                HeaderTone(DARK, darkSoft, dark = false, shadow = false, scrim = forDark, shade = PALE)
            }
        }
    }
}

/** What a pane's glass needs to be for its type: the tint's density, the ground it leaves, the header's tone. */
internal class PaneTone(val alpha: Float, val ground: Backing, val header: HeaderTone?)
