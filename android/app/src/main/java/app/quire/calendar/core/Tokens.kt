package app.quire.calendar.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import app.quire.engine.design.Oklch
import app.quire.engine.design.SystemScheme
import kotlin.math.max
import kotlin.math.min

/**
 * The whole visual language lives here, in one file, as plain numbers.
 *
 * Two surfaces render Quire: Canvas (in-app) and RemoteViews (the home-screen
 * widget). Neither can share an Android theme with the other, so the palette is
 * expressed in Kotlin and both read from it. `colors.xml` mirrors only the two
 * window-background values that must exist before any code runs.
 */

/** Accent is the single coloured voice in the interface. Everything else is ink. */
enum class Accent(
    val key: String,
    @get:ColorInt val light: Int,
    @get:ColorInt val dark: Int,
) {
    CINNABAR("cinnabar", 0xFFC0402B.toInt(), 0xFFE2593F.toInt()),
    INDIGO("indigo", 0xFF2E4A7D.toInt(), 0xFF7D97D1.toInt()),
    MOSS("moss", 0xFF4C5D3C.toInt(), 0xFF93A67C.toInt()),
    OCHRE("ochre", 0xFF9A6F21.toInt(), 0xFFD9A94E.toInt()),
    PLUM("plum", 0xFF6C3A55.toInt(), 0xFFB87CA0.toInt()),
    GRAPHITE("graphite", 0xFF26261F.toInt(), 0xFFE6E4DE.toInt()),
    ;

    companion object {
        fun from(key: String?): Accent = entries.firstOrNull { it.key == key } ?: CINNABAR
    }
}

/**
 * Which set of surfaces to paint on. AUTO follows the system.
 *
 * COLOUR is the odd one out: rather than ink on paper it fills the whole card with the accent
 * taken down to a deep, saturated ground and sets the dates in near-white on top of it. It is a
 * widget skin only — the app derives its own palette from a seed — and it exists because a card
 * that carries its own colour reads as an object on the wallpaper instead of a hole in it.
 */
enum class Skin(val key: String) {
    AUTO("auto"),
    PAPER("paper"),
    INK("ink"),
    COLOUR("colour"),
    ;

    companion object {
        fun from(key: String?): Skin = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

data class Palette(
    val dark: Boolean,
    @get:ColorInt val canvas: Int,
    @get:ColorInt val surface: Int,
    @get:ColorInt val ink: Int,
    @get:ColorInt val inkMuted: Int,
    @get:ColorInt val inkFaint: Int,
    @get:ColorInt val inkGhost: Int,
    @get:ColorInt val hairline: Int,
    @get:ColorInt val hairlineStrong: Int,
    @get:ColorInt val accent: Int,
    @get:ColorInt val onAccent: Int,
    @get:ColorInt val press: Int,
)

object Tokens {

    // ---- Paper (light) -------------------------------------------------
    private const val PAPER_CANVAS = 0xFFF6F5F1.toInt()
    private const val PAPER_SURFACE = 0xFFFCFBF8.toInt()
    private const val PAPER_INK = 0xFF14130F.toInt()
    private const val PAPER_INK_MUTED = 0xFF6A675D.toInt()
    private const val PAPER_INK_FAINT = 0xFF97948A.toInt()
    private const val PAPER_INK_GHOST = 0xFFC8C5BB.toInt()
    private const val PAPER_HAIRLINE = 0x1A14130F
    private const val PAPER_HAIRLINE_STRONG = 0x3314130F
    private const val PAPER_PRESS = 0x1214130F

    // ---- Ink (dark) ----------------------------------------------------
    private const val INK_CANVAS = 0xFF0C0C0B.toInt()
    private const val INK_SURFACE = 0xFF141413.toInt()
    private const val INK_INK = 0xFFF0EEE8.toInt()
    private const val INK_INK_MUTED = 0xFF8E8B81.toInt()
    private const val INK_INK_FAINT = 0xFF64615A.toInt()
    private const val INK_INK_GHOST = 0xFF3A3934.toInt()
    private const val INK_HAIRLINE = 0x1FF0EEE8
    private const val INK_HAIRLINE_STRONG = 0x3DF0EEE8
    private const val INK_PRESS = 0x14F0EEE8

    fun palette(dark: Boolean, accent: Accent): Palette = if (dark) {
        Palette(
            dark = true,
            canvas = INK_CANVAS,
            surface = INK_SURFACE,
            ink = INK_INK,
            inkMuted = INK_INK_MUTED,
            inkFaint = INK_INK_FAINT,
            inkGhost = INK_INK_GHOST,
            hairline = INK_HAIRLINE,
            hairlineStrong = INK_HAIRLINE_STRONG,
            accent = accent.dark,
            onAccent = INK_CANVAS,
            press = INK_PRESS,
        )
    } else {
        Palette(
            dark = false,
            canvas = PAPER_CANVAS,
            surface = PAPER_SURFACE,
            ink = PAPER_INK,
            inkMuted = PAPER_INK_MUTED,
            inkFaint = PAPER_INK_FAINT,
            inkGhost = PAPER_INK_GHOST,
            hairline = PAPER_HAIRLINE,
            hairlineStrong = PAPER_HAIRLINE_STRONG,
            accent = accent.light,
            onAccent = 0xFFFFFFFF.toInt(),
            press = PAPER_PRESS,
        )
    }

    /**
     * The filled card: the accent's own hue taken down to a deep ground, with the dates set in
     * near-white on it.
     *
     * Every value is walked in Oklch from the accent rather than picked by hand, so all six
     * accents give a card of the same weight instead of one that is nearly black and another
     * that glows. Lightness is fixed and only the hue travels, which is the whole trick.
     */
    fun filled(@ColorInt source: Int, dark: Boolean = true): Palette {
        val lch = FloatArray(3)
        Oklch.fromSrgb(source, lch)
        val hue = lch[2]
        // A near-grey accent has no hue worth carrying, so the ground borrows a little chroma
        // rather than landing on black — otherwise Graphite would be the only skinless skin.
        val chroma = max(lch[1], 0.035f)

        // Both faces are the same idea at opposite ends of the lightness axis: a ground that is
        // unmistakably the accent's hue, ink far enough from it to read, and a marker lightness
        // chosen so the ink of the *other* face sits on it. Only the numbers differ, which is
        // what keeps a widget recognisably itself when the phone changes its mind at sunrise.
        val cardL = if (dark) CARD_L_DARK else CARD_L_LIGHT
        val inkL = if (dark) INK_L_DARK else INK_L_LIGHT
        val markL = if (dark) MARK_L_DARK else MARK_L_LIGHT

        val card = Oklch.toSrgb(cardL, min(chroma * (if (dark) 0.85f else 0.28f), CARD_CHROMA_MAX), hue)
        val ink = Oklch.toSrgb(inkL, min(chroma * 0.10f, 0.02f), hue)
        val mark = Oklch.toSrgb(markL, min(chroma * (if (dark) 0.75f else 0.95f), 0.14f), hue)
        return Palette(
            dark = dark,
            canvas = card,
            surface = card,
            ink = ink,
            // The quiet tiers are the same ink at less of it, so the card colour reads through
            // them: a separate grey would go muddy against a saturated ground.
            inkMuted = withAlpha(ink, 0.74f),
            inkFaint = withAlpha(ink, 0.56f),
            inkGhost = withAlpha(ink, 0.34f),
            hairline = withAlpha(ink, 0.16f),
            hairlineStrong = withAlpha(ink, 0.26f),
            accent = mark,
            // What sits on the marker: the far end of the lightness axis from it, so a today disc
            // carries its date whichever face the card is wearing.
            onAccent = Oklch.toSrgb(
                if (dark) cardL * 0.8f else 0.99f,
                min(chroma * 0.6f, 0.06f),
                hue,
            ),
            press = withAlpha(ink, 0.12f),
        )
    }

    // Deep enough that white sits on it at better than 12:1, light enough that the hue is still
    // a colour rather than a black with a rumour in it.
    private const val CARD_L_DARK = 0.255f

    // The same card in daylight: a tint rather than a wash, with the chroma pulled well in —
    // a pale ground holds far less colour before it stops looking like paper and starts looking
    // like a highlighter.
    private const val CARD_L_LIGHT = 0.945f

    private const val CARD_CHROMA_MAX = 0.085f
    private const val INK_L_DARK = 0.975f
    private const val INK_L_LIGHT = 0.22f

    // The today disc and the add button.
    private const val MARK_L_DARK = 0.80f
    private const val MARK_L_LIGHT = 0.52f

    fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Whether a skin paints dark.
     *
     * COLOUR follows the system like AUTO does. It used to be pinned dark, on the reasoning that a
     * card carrying its own colour is an object rather than a page — but an object on a home
     * screen that stays night-black through a bright morning is not an object, it is a widget that
     * forgot to look. The filled card has a daylight face; this is what asks for it.
     */
    fun resolveDark(context: Context, skin: Skin): Boolean = when (skin) {
        Skin.PAPER -> false
        Skin.INK -> true
        Skin.AUTO, Skin.COLOUR -> isSystemDark(context)
    }

    /**
     * The palette a widget wearing [skin] paints in.
     *
     * A filled card follows the device's own Material scheme when [dynamic] is set and the
     * platform publishes one, so the widget matches the wallpaper it is sitting on; otherwise it
     * is built from the accent the placement was configured with. Paper and Ink are unaffected —
     * they are a printed page, and a printed page does not change colour with the wallpaper.
     */
    fun widgetPalette(
        context: Context,
        skin: Skin,
        accent: Accent,
        dynamic: Boolean = false,
    ): Palette {
        val dark = resolveDark(context, skin)
        if (skin != Skin.COLOUR) return palette(dark, accent)
        // The scheme is read for the face the card is about to wear, so a light card takes the
        // wallpaper's light primary rather than a dark-scheme colour lightened after the fact.
        val scheme = if (dynamic) SystemScheme.read(context, dark = dark) else null
        return filled(scheme?.primary ?: if (dark) accent.light else accent.dark, dark)
    }

    /** Blend `color` towards transparency without touching its channels. */
    @ColorInt
    fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
