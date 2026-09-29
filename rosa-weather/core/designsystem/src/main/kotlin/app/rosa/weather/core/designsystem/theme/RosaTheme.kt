package app.rosa.weather.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.rosa.weather.core.designsystem.R
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.SkyPalette

fun Argb.toColor(): Color = Color(value)

/**
 * Colours derived live from the sky: the whole UI re-tints as the sky changes (and animates
 * between moods), and flips to dark ink over bright skies — Apple's adaptive glass behaviour.
 */
@Immutable
data class RosaColors(
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val accent: Color,
    val warm: Color,
    val cool: Color,
    val rain: Color,
    val glassTint: Color,
    val fill: Color,
    val isLightSky: Boolean,
    val zenith: Color,
    val horizon: Color,
)

@Composable
fun animatedRosaColors(palette: SkyPalette): RosaColors {
    val spec = tween<Color>(1200)
    val ink by animateColorAsState(palette.ink.toColor(), spec, label = "ink")
    val accent by animateColorAsState(palette.accent.toColor(), spec, label = "accent")
    val warm by animateColorAsState(palette.warm.toColor(), spec, label = "warm")
    val cool by animateColorAsState(palette.cool.toColor(), spec, label = "cool")
    val zenith by animateColorAsState(palette.zenith.toColor(), spec, label = "zenith")
    val horizon by animateColorAsState(palette.horizon.toColor(), spec, label = "horizon")
    val light = palette.isLight
    // Milky glass over bright skies; over dark ones smoky glass that keeps the sky's own hue —
    // deep blue by day, violet at dusk, ink at night — instead of turning grey.
    val smoke = lerp(palette.zenith.toColor(), Color(0xFF0B1020), 0.72f)
    val glass by animateColorAsState(if (light) Color(0xFFFFFFFF) else smoke, spec, label = "glass")
    return RosaColors(
        ink = ink,
        inkSoft = ink.copy(alpha = 0.72f),
        inkFaint = ink.copy(alpha = 0.45f),
        accent = accent,
        warm = warm,
        cool = cool,
        rain = if (light) Color(0xFF2F7BE0) else Color(0xFF8CCBFF),
        glassTint = glass,
        fill = ink.copy(alpha = if (light) 0.07f else 0.1f),
        isLightSky = light,
        zenith = zenith,
        horizon = horizon,
    )
}

val LocalRosaColors = staticCompositionLocalOf {
    RosaColors(
        ink = Color.White, inkSoft = Color.White.copy(0.72f), inkFaint = Color.White.copy(0.45f),
        accent = Color(0xFFFFD37A), warm = Color(0xFFFFB26B), cool = Color(0xFF7FB6FF),
        rain = Color(0xFF8CCBFF), glassTint = Color(0xFF0B1020), fill = Color.White.copy(0.1f),
        isLightSky = false, zenith = Color(0xFF1E3A70), horizon = Color(0xFF7FA6D8),
    )
}

object RosaFonts {
    private fun manrope(weight: Int) = Font(
        R.font.manrope,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun cormorant(weight: Int) = Font(
        R.font.cormorant,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    /** UI face: an airy modern grotesque with full Cyrillic. */
    val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

    /**
     * A high-contrast Garamond for the numerals — refined rather than loud. Its default figures
     * are old-style, so every numeral style asks for [LINING_FIGURES].
     */
    val Cormorant = FontFamily(cormorant(300), cormorant(400), cormorant(500), cormorant(600))

    /** OpenType feature: digits on the cap height, like a temperature should be. */
    const val LINING_FIGURES = "lnum"
}

@Immutable
data class RosaType(
    val hero: TextStyle,
    val display: TextStyle,
    val numeral: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
) {
    companion object {
        private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        val Default = RosaType(
            hero = TextStyle(
                fontFamily = RosaFonts.Cormorant, fontWeight = FontWeight.Light, fontSize = 124.sp, lineHeight = 124.sp,
                letterSpacing = (-2).sp, fontFeatureSettings = RosaFonts.LINING_FIGURES, lineHeightStyle = trim,
            ),
            display = TextStyle(
                fontFamily = RosaFonts.Cormorant, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 46.sp,
                letterSpacing = (-0.5).sp, fontFeatureSettings = RosaFonts.LINING_FIGURES,
            ),
            numeral = TextStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 24.sp),
            title = TextStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
            headline = TextStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
            body = TextStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
            label = TextStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
            caption = TextStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
        )
    }
}

val LocalRosaType = staticCompositionLocalOf { RosaType.Default }

object Rosa {
    val colors: RosaColors @Composable get() = LocalRosaColors.current
    val type: RosaType @Composable get() = LocalRosaType.current
}

@Composable
fun RosaTheme(colors: RosaColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isLightSky) {
        lightColorScheme(primary = colors.ink, onPrimary = Color.White, secondary = colors.accent, surface = Color.White, onSurface = colors.ink)
    } else {
        darkColorScheme(primary = colors.accent, onPrimary = Color(0xFF14172A), secondary = colors.accent, surface = Color(0xFF14172A), onSurface = colors.ink)
    }
    CompositionLocalProvider(LocalRosaColors provides colors, LocalRosaType provides RosaType.Default) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
