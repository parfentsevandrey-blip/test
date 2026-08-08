package app.quire.engine.design

import androidx.annotation.ColorInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAU: Float = 6.2831855f
private const val DEGREE: Float = 0.017453292f

// Ten hues is more categories than any one screen should use, and keeps neighbours 36 degrees
// apart, which stays telling once a mark is only a few pixels wide.
private const val CATEGORY_COUNT: Int = 10

// Far enough round the circle to read as a different colour, near enough to still look related.
private const val AURORA_A_DEGREES: Float = 40f
private const val AURORA_B_DEGREES: Float = -55f

private const val ACCENT_SOFT_MIX: Float = 0.16f

// Text on a filled accent is still text. Any colour can reach at least 4.58:1 against either
// black or white, so this floor is always attainable once both candidates are free to walk.
private const val ON_ACCENT_TARGET: Float = 4.5f

// A tier that misses its contrast floor is pulled back towards the ink in shrinking steps; from
// any sane starting fraction this lands in three or four, and the cap is only a stop.
private const val TIER_STEPS: Int = 24
private const val TIER_RETREAT: Float = 0.85f

// The correction walk moves lightness a hundredth at a time, so the whole 0..1 axis is covered.
private const val WALK_STEPS: Int = 110
private const val WALK_STEP: Float = 0.01f

/**
 * A whole palette derived from one seed colour and a mode, so customisation is a single choice
 * rather than a table of hexes. Roles are named for their job, not their colour.
 *
 * Deriving costs a few hundred colour conversions, which is a build-time price: hold the result
 * and rebuild it only when the seed, the mode or the boost changes.
 */
class Theme(
    @ColorInt val seed: Int,
    val dark: Boolean,
    val contrastBoost: Float = 0f,
) {

    private val boost: Float = contrastBoost.coerceIn(0f, 1f)

    private val seedLch: FloatArray = FloatArray(3).also { Oklch.fromSrgb(seed, it) }
    private val seedL: Float = seedLch[0]
    private val seedChroma: Float = seedLch[1]
    private val hue: Float = seedLch[2]

    // The greys are the seed's own hue at a chroma low enough to still read as neutral. A palette
    // built on dead grey looks unrelated to its accent; a tenth of the seed's chroma is the point
    // where the temperature is felt rather than seen.
    private val neutralChroma: Float = min(seedChroma * 0.10f, 0.014f)

    // The three planes are one base plus two equal steps, so they stay evenly separated whatever
    // the boost does to the base. Dark mode has the room for larger steps than light mode, which
    // runs out of headroom at white.
    private val planeL: Float =
        if (dark) 0.155f - 0.050f * boost else 0.972f + 0.010f * boost
    private val planeStep: Float = if (dark) 0.050f else 0.010f

    private val inkStartL: Float =
        if (dark) 0.950f + 0.045f * boost else 0.180f - 0.120f * boost

    /** The page behind everything: the plane every other colour is judged against. */
    @ColorInt
    val canvas: Int = Oklch.toSrgb(planeL, neutralAt(planeL), hue)

    /** The plane cards, rows and sheets sit on, one step off the canvas. */
    @ColorInt
    val surface: Int = Oklch.toSrgb(planeL + planeStep, neutralAt(planeL + planeStep), hue)

    /** The plane for anything that floats above a surface, such as a menu or a dragged card. */
    @ColorInt
    val surfaceLifted: Int =
        Oklch.toSrgb(planeL + 2f * planeStep, neutralAt(planeL + 2f * planeStep), hue)

    /** Body text and anything that must be read without effort. */
    @ColorInt
    val ink: Int = walkToContrast(
        against = canvas,
        startL = inkStartL,
        // Text carries a little more of the seed than the planes do: at reading size a colour is
        // seen in thin strokes, which shows less of it than a whole plane does.
        chroma = neutralAt(inkStartL) * 1.2f,
        target = 11f + 4f * boost,
        lighter = dark,
    )

    /** Secondary text: still read, but not first. */
    @ColorInt
    val inkMuted: Int = tier(0.36f, 4.5f + 1.5f * boost)

    /** Labels and units that are there when looked for. */
    @ColorInt
    val inkFaint: Int = tier(0.56f, 3.0f + 1.0f * boost)

    /** Disabled marks and placeholders: present, deliberately not legible at a glance. */
    @ColorInt
    val inkGhost: Int = tier(0.76f, 1.25f)

    /** The default rule between rows and around fields. */
    @ColorInt
    val hairline: Int = inkAlpha((if (dark) 0.14f else 0.12f) + 0.06f * boost)

    /** The rule used where a division is structural rather than incidental. */
    @ColorInt
    val hairlineStrong: Int = inkAlpha((if (dark) 0.30f else 0.26f) + 0.10f * boost)

    /** The wash laid over anything being touched, so a press is felt before it is understood. */
    @ColorInt
    val press: Int = inkAlpha((if (dark) 0.10f else 0.09f) + 0.03f * boost)

    /** The single coloured voice: selection, today, the one action that matters on a screen. */
    @ColorInt
    val accent: Int = walkToContrast(
        against = canvas,
        // The seed is only a hue and a chroma here; its lightness is pushed into the half of the
        // range the mode leaves room in, so one seed serves both modes.
        startL = if (dark) max(seedL, 0.70f) else min(seedL, 0.56f),
        chroma = seedChroma,
        target = 3.1f + 1.5f * boost,
        lighter = dark,
    )

    /**
     * What is written or drawn on top of [accent]. Both candidates are pushed to the contrast
     * they need before the better one is chosen: a mid-lightness accent is exactly the case
     * where neither a fixed near-white nor a fixed near-black is far enough away to be read.
     */
    @ColorInt
    val onAccent: Int = Oklch.readableOn(
        accent,
        walkToContrast(
            against = accent,
            startL = 0.985f,
            chroma = neutralAt(0.985f),
            target = ON_ACCENT_TARGET + 2f * boost,
            lighter = true,
        ),
        walkToContrast(
            against = accent,
            startL = 0.150f,
            chroma = neutralAt(0.150f),
            target = ON_ACCENT_TARGET + 2f * boost,
            lighter = false,
        ),
    )

    /** The accent as a fill rather than a mark: chips, selected rows, the tint behind a badge. */
    @ColorInt
    val accentSoft: Int = Oklch.blend(surface, accent, ACCENT_SOFT_MIX)

    /** Two further hues in harmony with the seed, for the background pools. */
    @ColorInt
    val auroraA: Int = pool(AURORA_A_DEGREES)

    /** The second background pool, on the other side of the seed from [auroraA]. */
    @ColorInt
    val auroraB: Int = pool(AURORA_B_DEGREES)

    // Precomputed because categorical colours are asked for inside draw loops, one per mark.
    private val categories: IntArray = IntArray(CATEGORY_COUNT) { index ->
        walkToContrast(
            against = canvas,
            startL = if (dark) 0.74f else 0.56f,
            // A near-grey seed would otherwise make every category the same grey, which is the
            // one thing categorical colours may not be.
            chroma = max(seedChroma, 0.045f),
            target = 3.0f,
            lighter = dark,
            atHue = hue + index * (TAU / CATEGORY_COUNT),
        )
    }

    /** Distinct, evenly spaced hues for categorical marks, holding the seed's chroma. */
    @ColorInt
    fun categorical(index: Int): Int {
        val slot = index % CATEGORY_COUNT
        return categories[if (slot < 0) slot + CATEGORY_COUNT else slot]
    }

    /** Two themes are equal when their inputs are, so a signal can skip an identical rebuild. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Theme) return false
        return seed == other.seed && dark == other.dark && contrastBoost == other.contrastBoost
    }

    /** Agrees with [equals], so a theme can key a cache of anything derived from it. */
    override fun hashCode(): Int {
        var result = seed
        result = 31 * result + if (dark) 1 else 0
        result = 31 * result + contrastBoost.toRawBits()
        return result
    }

    /** Names the inputs rather than the output, since the output is fifteen colours. */
    override fun toString(): String =
        "Theme(seed=#${(seed.toLong() and 0xFFFFFFFFL).toString(16)}, dark=$dark, " +
            "contrastBoost=$boost)"

    /**
     * Walks lightness away from [against] until the pair clears [target], measuring the real
     * eight-bit colours rather than trusting the lightness arithmetic: chroma that had to be
     * pulled in to fit the gamut moves luminance too.
     */
    private fun walkToContrast(
        against: Int,
        startL: Float,
        chroma: Float,
        target: Float,
        lighter: Boolean,
        atHue: Float = hue,
    ): Int {
        val step = if (lighter) WALK_STEP else -WALK_STEP
        var l = startL.coerceIn(0f, 1f)
        var colour = Oklch.toSrgb(l, chroma, atHue)
        var i = 0
        while (i < WALK_STEPS && Oklch.contrast(colour, against) < target) {
            val next = (l + step).coerceIn(0f, 1f)
            // The axis has run out: this is the most contrast this hue can reach, and it is
            // better to return it than to loop.
            if (next == l) break
            l = next
            colour = Oklch.toSrgb(l, chroma, atHue)
            i++
        }
        return colour
    }

    private fun tier(fraction: Float, target: Float): Int {
        var mix = (fraction * (1f - 0.30f * boost)).coerceIn(0f, 1f)
        var colour = Oklch.blend(ink, canvas, mix)
        var i = 0
        while (i < TIER_STEPS && Oklch.contrast(colour, canvas) < target) {
            mix *= TIER_RETREAT
            colour = Oklch.blend(ink, canvas, mix)
            i++
        }
        return colour
    }

    /**
     * The neutral chroma to use at a given lightness. A tint reads far more strongly next to
     * white than it does in the middle of the range, so the temperature tapers towards both ends
     * instead of holding one chroma and turning the lightest plane pink.
     */
    private fun neutralAt(l: Float): Float =
        neutralChroma * sqrt(max(4f * l * (1f - l), 0f))

    private fun pool(degrees: Float): Int = Oklch.toSrgb(
        if (dark) 0.60f else 0.78f,
        // Pools are wide, soft and usually laid down under an alpha, so they hold roughly the
        // seed's chroma: a washed pool cannot be enriched later, but a rich one can be thinned.
        seedChroma * if (dark) 1.10f else 0.95f,
        hue + degrees * DEGREE,
    )

    private fun inkAlpha(alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (a shl 24) or (ink and 0x00FFFFFF)
    }

    companion object {
        /** A handful of seeds worth offering as presets, with names. */
        val seeds: List<Pair<String, Int>> = listOf(
            "Cinnabar" to 0xFFC0402B.toInt(),
            "Ember" to 0xFFD2652A.toInt(),
            "Ochre" to 0xFF9A6F21.toInt(),
            "Moss" to 0xFF4C5D3C.toInt(),
            "Verdigris" to 0xFF1F6F6B.toInt(),
            "Indigo" to 0xFF2E4A7D.toInt(),
            "Iris" to 0xFF5B4B9E.toInt(),
            "Plum" to 0xFF6C3A55.toInt(),
            "Graphite" to 0xFF3C3B38.toInt(),
        )
    }
}
