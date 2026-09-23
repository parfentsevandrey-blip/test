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
    val glass by animateColorAsState(if (light) Color(0xFFFFFFFF) else Color(0xFF0B1020), spec, label = "glass")
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
    private fun onest(weight: Int) = Font(
        R.font.onest,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun fraunces(weight: Int, soft: Float = 100f) = Font(
        R.font.fraunces,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("SOFT", soft),
            FontVariation.Setting("opsz", 144f),
            FontVariation.Setting("WONK", 0f),
        ),
    )

    /** UI face with full Cyrillic. */
    val Onest = FontFamily(onest(400), onest(500), onest(600), onest(700), onest(800))

    /** Soft optical-size serif for the big numerals — the cosy signature of the app. */
    val Fraunces = FontFamily(fraunces(300), fraunces(400), fraunces(500), fraunces(600))
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
            hero = TextStyle(fontFamily = RosaFonts.Fraunces, fontWeight = FontWeight(400), fontSize = 124.sp, lineHeight = 124.sp, letterSpacing = (-4).sp, lineHeightStyle = trim),
            display = TextStyle(fontFamily = RosaFonts.Fraunces, fontWeight = FontWeight(400), fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1).sp),
            numeral = TextStyle(fontFamily = RosaFonts.Fraunces, fontWeight = FontWeight(500), fontSize = 22.sp, lineHeight = 24.sp),
            title = TextStyle(fontFamily = RosaFonts.Onest, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
            headline = TextStyle(fontFamily = RosaFonts.Onest, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
            body = TextStyle(fontFamily = RosaFonts.Onest, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
            label = TextStyle(fontFamily = RosaFonts.Onest, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
            caption = TextStyle(fontFamily = RosaFonts.Onest, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
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
