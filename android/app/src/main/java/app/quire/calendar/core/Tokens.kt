package app.quire.calendar.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt

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
    @ColorInt val light: Int,
    @ColorInt val dark: Int,
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

/** Which set of surfaces to paint on. AUTO follows the system. */
enum class Skin(val key: String) {
    AUTO("auto"),
    PAPER("paper"),
    INK("ink"),
    ;

    companion object {
        fun from(key: String?): Skin = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

data class Palette(
    val dark: Boolean,
    @ColorInt val canvas: Int,
    @ColorInt val surface: Int,
    @ColorInt val ink: Int,
    @ColorInt val inkMuted: Int,
    @ColorInt val inkFaint: Int,
    @ColorInt val inkGhost: Int,
    @ColorInt val hairline: Int,
    @ColorInt val hairlineStrong: Int,
    @ColorInt val accent: Int,
    @ColorInt val onAccent: Int,
    @ColorInt val press: Int,
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

    fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun resolveDark(context: Context, skin: Skin): Boolean = when (skin) {
        Skin.PAPER -> false
        Skin.INK -> true
        Skin.AUTO -> isSystemDark(context)
    }

    /** Blend `color` towards transparency without touching its channels. */
    @ColorInt
    fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
