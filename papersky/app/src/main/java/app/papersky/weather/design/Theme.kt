package app.papersky.weather.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.scene.ColorMath
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.ScenePalette

/**
 * Every colour of the app comes from the sky (DESIGN_DOCTRINE §3): the paper is cut from the same
 * sheet as the diorama, so it is cream on a sunny day, peach at golden hour and deep ink-blue on a
 * rainy night — and the whole interface glides from one to the other with the weather.
 */
@Immutable
data class PaperColors(
    val sky: Color,
    val skyBottom: Color,
    val onSky: Color,
    val onSkySoft: Color,
    val paper: Color,
    val paperInk: Color,
    val paperInkSoft: Color,
    val accent: Color,
    val tape: Color,
    val shadow: Color,
    val precip: Color,
    val sun: Color,
    val isNight: Boolean,
) {
    /** Ink for precipitation printed on this paper. */
    val rainInk: Color get() = if (isNight) Color(0xFF9CC4EC) else Color(0xFF3F77B3)

    companion object {
        fun from(p: ScenePalette): PaperColors {
            val ink = Color(p.paperInk)
            return PaperColors(
                sky = Color(p.skyTop),
                skyBottom = Color(p.skyBottom),
                onSky = Color(p.onSky),
                onSkySoft = Color(p.onSkySoft),
                paper = Color(p.paper),
                paperInk = ink,
                paperInkSoft = ink.copy(alpha = 0.62f),
                accent = Color(p.accent),
                tape = Color(p.tape),
                shadow = Color(ColorMath.withAlpha(p.shadow, (ColorMath.a(p.shadow) / 255f).coerceAtLeast(0.3f))),
                precip = Color(p.precip),
                sun = Color(p.sun),
                isNight = p.isDarkPaper,
            )
        }

        val Default = from(Palettes.ClearDay)
    }
}

/**
 * Three voices, each with one job (DESIGN_DOCTRINE §6): Unbounded speaks the numbers and the
 * titles, Manrope explains, Caveat is what a person wrote by hand.
 */
object PaperFonts {
    private fun unbounded(weight: Int) = Font(
        R.font.unbounded, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun manrope(weight: Int) = Font(
        R.font.manrope, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun caveat(weight: Int) = Font(
        R.font.caveat, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val Display = FontFamily(unbounded(250), unbounded(300), unbounded(400), unbounded(500), unbounded(600), unbounded(700))
    val Body = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))
    val Hand = FontFamily(caveat(500), caveat(600), caveat(700))
}

@Immutable
data class PaperType(
    /** The temperature: large and light, standing in the sky. */
    val hero: TextStyle,
    /** Screen titles. */
    val display: TextStyle,
    /** Values on tiles, big temperatures on cards. */
    val title: TextStyle,
    /** Names: places, card titles. */
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    /** Small caps over a card. */
    val label: TextStyle,
    val caption: TextStyle,
    /** Temperatures in lists and charts. */
    val number: TextStyle,
    val hand: TextStyle,
    val handLarge: TextStyle,
) {
    companion object {
        private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        private val D = PaperFonts.Display
        private val B = PaperFonts.Body
        private val H = PaperFonts.Hand

        val Default = PaperType(
            hero = TextStyle(fontFamily = D, fontWeight = FontWeight(300), fontSize = 112.sp, letterSpacing = (-0.04).em, lineHeight = 1.0.em, lineHeightStyle = tight),
            display = TextStyle(fontFamily = D, fontWeight = FontWeight(500), fontSize = 34.sp, letterSpacing = (-0.02).em, lineHeight = 1.1.em),
            title = TextStyle(fontFamily = D, fontWeight = FontWeight(600), fontSize = 22.sp, letterSpacing = (-0.01).em, lineHeight = 1.2.em),
            heading = TextStyle(fontFamily = D, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 1.3.em),
            body = TextStyle(fontFamily = B, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 1.45.em),
            bodyStrong = TextStyle(fontFamily = B, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 1.4.em),
            label = TextStyle(fontFamily = B, fontWeight = FontWeight(800), fontSize = 11.sp, letterSpacing = 0.12.em),
            caption = TextStyle(fontFamily = B, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 1.35.em),
            number = TextStyle(fontFamily = D, fontWeight = FontWeight(500), fontSize = 16.sp, fontFeatureSettings = "tnum"),
            hand = TextStyle(fontFamily = H, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 1.15.em),
            handLarge = TextStyle(fontFamily = H, fontWeight = FontWeight(600), fontSize = 30.sp, lineHeight = 1.1.em),
        )
    }
}

/** Text standing straight on the sky gets a soft shadow (or a pale glow, if the ink is dark). */
fun TextStyle.onSky(lightInk: Boolean): TextStyle = copy(
    shadow = if (lightInk) Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 3f), 14f)
    else Shadow(Color.White.copy(alpha = 0.4f), Offset(0f, 2f), 12f),
)

// Dynamic (not static): colours animate, and only readers should recompose.
val LocalPaperColors = compositionLocalOf { PaperColors.Default }
val LocalPaperType = staticCompositionLocalOf { PaperType.Default }

object Paper {
    val colors: PaperColors @Composable get() = LocalPaperColors.current
    val type: PaperType @Composable get() = LocalPaperType.current
    val light: Light @Composable get() = LocalLight.current
}

/** Provides colours that glide (rather than snap) whenever the sky changes. */
@Composable
fun PaperTheme(palette: ScenePalette, light: Light, content: @Composable () -> Unit) {
    val target = PaperColors.from(palette)
    val spec = tween<Color>(durationMillis = 900)
    val sky by animateColorAsState(target.sky, spec, label = "sky")
    val skyBottom by animateColorAsState(target.skyBottom, spec, label = "skyBottom")
    val onSky by animateColorAsState(target.onSky, spec, label = "onSky")
    val onSkySoft by animateColorAsState(target.onSkySoft, spec, label = "onSkySoft")
    val paper by animateColorAsState(light.lit(target.paper), spec, label = "paper")
    val ink by animateColorAsState(target.paperInk, spec, label = "ink")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val tape by animateColorAsState(target.tape, spec, label = "tape")
    val shadow by animateColorAsState(target.shadow, spec, label = "shadow")
    val precip by animateColorAsState(target.precip, spec, label = "precip")
    val sun by animateColorAsState(target.sun, spec, label = "sun")
    val colors = PaperColors(
        sky = sky, skyBottom = skyBottom, onSky = onSky, onSkySoft = onSkySoft, paper = paper,
        paperInk = ink, paperInkSoft = ink.copy(alpha = 0.62f), accent = accent, tape = tape,
        shadow = shadow, precip = precip, sun = sun, isNight = target.isNight,
    )
    CompositionLocalProvider(
        LocalPaperColors provides colors,
        LocalPaperType provides PaperType.Default,
        LocalLight provides light,
        content = content,
    )
}
