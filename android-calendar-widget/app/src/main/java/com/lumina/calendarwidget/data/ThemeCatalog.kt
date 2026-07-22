package com.lumina.calendarwidget.data

/**
 * A fully-resolved set of color tokens for the widget. Values are ARGB longs (0xAARRGGBB).
 * Every token maps 1:1 to a Material You role so [dynamic color][WidgetSettings.dynamicColor]
 * can substitute wallpaper colors while keeping the exact same rendering code.
 */
data class ThemeColors(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Long,
    val backgroundGradientEnd: Long,
    val surface: Long,
    val onSurface: Long,
    val muted: Long,
    val accent: Long,
    val onAccent: Long,
    val todayBg: Long,
    val todayText: Long,
    val weekendText: Long,
    val gridLine: Long,
    val headerText: Long,
)

/**
 * The curated built-in presets. Each is a designed pair of surfaces, inks, and a single accent;
 * inks are temperature-matched (never pure #FFFFFF/#000000) so the set reads as one family.
 */
object ThemeCatalog {

    const val DEFAULT_LIGHT_ID = "paper"
    const val DEFAULT_DARK_ID = "ink"
    const val AMOLED_ID = "onyx_amoled"

    val presets: List<ThemeColors> = listOf(
        ThemeColors("paper", "Paper", false,
            0xFFEDEAE1, 0xFFEDEAE1, 0xFFF7F5EF, 0xFF1C1B18, 0xFFA9A398,
            0xFFC4402C, 0xFFFDFBF6, 0xFFC4402C, 0xFFFDFBF6, 0xFFB0684F, 0xFFE4E0D5, 0xFF1C1B18),
        ThemeColors("ink", "Ink", true,
            0xFF0C0B0A, 0xFF0C0B0A, 0xFF171410, 0xFFECE7DD, 0xFF6E675C,
            0xFFE9634A, 0xFF1A1512, 0xFFE9634A, 0xFF1A1512, 0xFFC08A6E, 0xFF2A251E, 0xFFECE7DD),
        ThemeColors("meridian", "Meridian", false,
            0xFFE9EBEE, 0xFFE9EBEE, 0xFFF6F7F9, 0xFF1A1D22, 0xFF9CA1AB,
            0xFF2B54D4, 0xFFFFFFFF, 0xFF2B54D4, 0xFFFFFFFF, 0xFF6C7686, 0xFFE0E3E8, 0xFF1A1D22),
        ThemeColors("obsidian_gold", "Obsidian Gold", true,
            0xFF08080A, 0xFF0B0B12, 0xFF15151B, 0xFFECE6DA, 0xFF7C776D,
            0xFFCBA75E, 0xFF1A1408, 0xFFCBA75E, 0xFF1A1408, 0xFFA0937B, 0xFF26242C, 0xFFF3EDE1),
        ThemeColors("midnight_sapphire", "Midnight Sapphire", true,
            0xFF070A12, 0xFF0C1220, 0xFF131A29, 0xFFE7ECF3, 0xFF6E7788,
            0xFF5A8CE0, 0xFF081019, 0xFF5A8CE0, 0xFF081019, 0xFF93A6C6, 0xFF1E2637, 0xFFEFF3F9),
        ThemeColors("emerald_noir", "Emerald Noir", true,
            0xFF070A09, 0xFF0B120F, 0xFF111815, 0xFFE7ECE8, 0xFF6F7A73,
            0xFF3EA783, 0xFF04130D, 0xFF3EA783, 0xFF04130D, 0xFF86AD98, 0xFF18221E, 0xFFEDF2EE),
        ThemeColors("daylight", "Daylight", false,
            0xFFF7F2FA, 0xFFEFE7F5, 0xFFFFFFFF, 0xFF1D1B20, 0xFF7A757F,
            0xFF6750A4, 0xFFFFFFFF, 0xFF6750A4, 0xFFFFFFFF, 0xFF7D5260, 0xFFE7E0EC, 0xFF6750A4),
        ThemeColors("twilight", "Twilight", true,
            0xFF141218, 0xFF1C1826, 0xFF211F26, 0xFFE6E0E9, 0xFF948F99,
            0xFFD0BCFF, 0xFF381E72, 0xFFD0BCFF, 0xFF381E72, 0xFFEFB8C8, 0xFF322F35, 0xFFD0BCFF),
        ThemeColors("blossom", "Blossom", false,
            0xFFFFF7F9, 0xFFFBECF0, 0xFFFFFFFF, 0xFF201A1C, 0xFF86737A,
            0xFFB0295A, 0xFFFFFFFF, 0xFFB0295A, 0xFFFFFFFF, 0xFF8A5A00, 0xFFF0DDE3, 0xFFB0295A),
        ThemeColors("alabaster", "Alabaster", false,
            0xFFF4EEE2, 0xFFF4EEE2, 0xFFFCF8F0, 0xFF211C13, 0xFF8A806E,
            0xFF8C6A2A, 0xFFFCF7EC, 0xFF8C6A2A, 0xFFFCF7EC, 0xFF9A6E3C, 0xFFE6DCC9, 0xFF211C13),
        ThemeColors("onyx_amoled", "Onyx (AMOLED)", true,
            0xFF000000, 0xFF000000, 0xFF0E0E10, 0xFFF2F2F5, 0xFF8E8E96,
            0xFFB4C5FF, 0xFF062E6F, 0xFFB4C5FF, 0xFF062E6F, 0xFFFFB4A8, 0xFF1C1C1F, 0xFFB4C5FF),
    )

    private val byId = presets.associateBy { it.id }

    val lightPresets: List<ThemeColors> get() = presets.filter { !it.isDark }
    val darkPresets: List<ThemeColors> get() = presets.filter { it.isDark }

    fun byId(id: String): ThemeColors = byId[id] ?: presets.first()

    /**
     * Resolve the final palette for the given settings and current system dark-mode state.
     * Applies theme-mode selection, custom-color overrides, and background opacity.
     */
    fun resolve(settings: WidgetSettings, systemDark: Boolean): ThemeColors {
        val base = when {
            settings.useCustomColors -> custom(settings)
            else -> byId(
                when (settings.themeMode) {
                    ThemeMode.AMOLED -> AMOLED_ID
                    ThemeMode.LIGHT -> settings.lightPresetId
                    ThemeMode.DARK -> settings.darkPresetId
                    ThemeMode.SYSTEM -> if (systemDark) settings.darkPresetId else settings.lightPresetId
                }
            )
        }
        if (settings.backgroundOpacity >= 100) return base
        val a = settings.backgroundOpacity
        return base.copy(
            background = withAlpha(base.background, a),
            backgroundGradientEnd = withAlpha(base.backgroundGradientEnd, a),
            surface = withAlpha(base.surface, a),
        )
    }

    private fun custom(s: WidgetSettings): ThemeColors = ThemeColors(
        id = "custom",
        name = "Custom",
        isDark = luminance(s.backgroundColor) < 0.5,
        background = s.backgroundColor,
        backgroundGradientEnd = s.backgroundColor,
        surface = s.surfaceColor,
        onSurface = s.dayTextColor,
        muted = s.mutedTextColor,
        accent = s.accentColor,
        onAccent = contrastColor(s.accentColor),
        todayBg = s.todayColor,
        todayText = contrastColor(s.todayColor),
        weekendText = s.weekendTextColor,
        gridLine = s.mutedTextColor,
        headerText = s.dayTextColor,
    )
}

/** ARGB long with the alpha channel scaled to [opacityPercent] (0..100), RGB preserved. */
fun withAlpha(argb: Long, opacityPercent: Int): Long {
    val a = (opacityPercent.coerceIn(0, 100) * 255 / 100).toLong()
    return (a shl 24) or (argb and 0xFFFFFF)
}

/** Perceptual luminance in 0..1 from an ARGB long (alpha ignored). */
fun luminance(argb: Long): Double {
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    return 0.299 * r + 0.587 * g + 0.114 * b
}

/** Pick a readable ink (near-black or off-white) for text placed on [argb]. */
fun contrastColor(argb: Long): Long =
    if (luminance(argb) > 0.55) 0xFF16151A else 0xFFFDFDFB
