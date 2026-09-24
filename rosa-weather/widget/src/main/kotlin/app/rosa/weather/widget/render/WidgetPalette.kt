package app.rosa.weather.widget.render

import android.content.Context
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WidgetAccent
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTheme

/** Wallpaper-derived Material You tones, captured at render time (API 31+ system colours). */
data class DynamicTones(
    val accentLight: Int,
    val accentMid: Int,
    val accentDark: Int,
    val neutralLight: Int,
    val neutralDark: Int,
    val tertiary: Int,
) {
    companion object {
        fun from(context: Context) = DynamicTones(
            accentLight = context.getColor(android.R.color.system_accent1_100),
            accentMid = context.getColor(android.R.color.system_accent1_400),
            accentDark = context.getColor(android.R.color.system_accent1_800),
            neutralLight = context.getColor(android.R.color.system_neutral1_50),
            neutralDark = context.getColor(android.R.color.system_neutral1_900),
            tertiary = context.getColor(android.R.color.system_accent3_300),
        )

        /** Used by previews and tests where no system palette exists. */
        val Fallback = DynamicTones(
            accentLight = 0xFFD9E2FF.toInt(),
            accentMid = 0xFF7C93D6.toInt(),
            accentDark = 0xFF26386B.toInt(),
            neutralLight = 0xFFF8F8FC.toInt(),
            neutralDark = 0xFF1A1B20.toInt(),
            tertiary = 0xFFE2A8D8.toInt(),
        )
    }
}

/**
 * Every colour a widget needs, resolved once per render from the style, theme, accent choice and
 * the live sky palette.
 */
data class WidgetPalette(
    val isDark: Boolean,
    val ink: Int,
    val inkSoft: Int,
    val inkFaint: Int,
    val accent: Int,
    val rain: Int,
    val fill: Int,
    val glyphTone: WeatherGlyphPainter.Tone,
    val textShadow: Boolean,
    val sky: SkyPalette,
    /** How the big temperature is drawn; null draws it as flat ink (Paper is printed, not glass). */
    val glass: GlassInk?,
) {
    companion object {
        fun resolve(
            config: WidgetConfig,
            sky: SkyPalette,
            systemNight: Boolean,
            isDaylight: Boolean,
            dynamic: DynamicTones,
        ): WidgetPalette {
            val dark = when (config.theme) {
                WidgetTheme.Light -> false
                WidgetTheme.Dark -> true
                WidgetTheme.Auto -> when (config.style) {
                    WidgetStyle.Glass, WidgetStyle.Sky -> !sky.isLight
                    WidgetStyle.Clear -> true
                    WidgetStyle.Tonal -> systemNight
                    WidgetStyle.Paper -> !isDaylight
                }
            }
            val paper = config.style == WidgetStyle.Paper
            val tonal = config.style == WidgetStyle.Tonal
            val ink = when {
                paper -> if (dark) 0xFFF3E9D8.toInt() else 0xFF2A241F.toInt()
                tonal -> if (dark) dynamic.accentLight else dynamic.accentDark
                config.style == WidgetStyle.Sky && config.theme == WidgetTheme.Auto -> sky.ink.value
                dark -> 0xFFFFFBF5.toInt()
                else -> 0xFF1B2030.toInt()
            }
            val skyAccent = if (dark) sky.accent.value else Argb(sky.accent.value).lerp(Argb.hex(0x1B2030), 0.62f).value
            val accent = when (config.accent) {
                WidgetAccent.Sky -> if (paper) (if (dark) 0xFFF0A27A.toInt() else 0xFFC4552F.toInt()) else skyAccent
                WidgetAccent.Temperature -> skyAccent // replaced per-value by the temperature scale
                WidgetAccent.Dynamic -> if (dark) dynamic.tertiary else dynamic.accentMid
                WidgetAccent.Mono -> ink
            }
            // What the glass numerals refract: the sky behind the pane, or the wallpaper's tones.
            val refract = when {
                config.accent == WidgetAccent.Mono -> Argb(ink).lerp(if (dark) Argb.hex(0x40485C) else Argb.hex(0x8A93A6), 0.6f)
                tonal -> Argb(dynamic.accentMid)
                else -> sky.zenith
            }
            val glass = when {
                paper -> null
                // Clear crystal: a white body whose lower edges take on the colour behind it.
                dark -> GlassInk(
                    top = withAlpha(Argb(ink).lerp(refract, 0.04f).value, 0.97f),
                    bottom = withAlpha(Argb(ink).lerp(refract, 0.22f).value, 0.9f),
                    rim = 0xFFFFFFFF.toInt(),
                    foot = withAlpha(refract.lerp(Argb(ink), 0.3f).value, 0.6f),
                    shadow = 0x5A050815,
                    sheen = 0,
                )
                // Smoked glass: a translucent body, light along its upper edges, depth along the lower.
                else -> GlassInk(
                    top = withAlpha(ink, 0.84f),
                    bottom = withAlpha(Argb(ink).lerp(refract, 0.3f).value, 0.7f),
                    rim = 0xD8FFFFFF.toInt(),
                    foot = withAlpha(Argb(ink).lerp(Argb.hex(0x000000), 0.4f).value, 0.5f),
                    shadow = withAlpha(ink, 0.2f),
                    sheen = 0x3CFFFFFF,
                )
            }
            return WidgetPalette(
                isDark = dark,
                ink = ink,
                inkSoft = withAlpha(ink, 0.7f),
                inkFaint = withAlpha(ink, 0.42f),
                accent = accent,
                rain = if (dark) 0xFF8CCBFF.toInt() else 0xFF2F7BE0.toInt(),
                fill = withAlpha(ink, if (dark) 0.09f else 0.07f),
                glyphTone = when (config.style) {
                    WidgetStyle.Clear, WidgetStyle.Paper -> WeatherGlyphPainter.Tone.Mono
                    else -> if (config.accent == WidgetAccent.Mono) WeatherGlyphPainter.Tone.Mono else WeatherGlyphPainter.Tone.Color
                },
                textShadow = config.style == WidgetStyle.Clear || (config.style == WidgetStyle.Sky && dark),
                sky = sky,
                glass = glass,
            )
        }

        fun withAlpha(color: Int, alpha: Float): Int =
            (((color ushr 24) * alpha).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
    }
}
