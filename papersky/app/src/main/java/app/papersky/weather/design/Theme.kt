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
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.ScenePalette

/** Inks and papers of DESIGN_DOCTRINE §3 and §5. */
object Ink {
    val Graphite = Color(0xFF2B2824)
    val Sepia = Color(0xFF6E5B49)
    val Blue = Color(0xFF2F4F86)
    val RedPencil = Color(0xFFC8483B)
    val Terracotta = Color(0xFFC9573A)
    val RainOnPaper = Color(0xFF3F77B3)
    val Shadow = Color(0xFF2A1C10)
    val Tapes = listOf(Color(0xFFE9A79A), Color(0xFFF2D38B), Color(0xFFA9C9B8), Color(0xFFAFC3E0), Color(0xFFD8B8E0))
}

/**
 * Sky colours come from the living palette; paper colours come from the materials under the
 * room's [Light] — paper never turns into a "dark theme".
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
    val handInk: Color,
    val redPencil: Color,
    val shadow: Color,
    val precip: Color,
    val sun: Color,
    val isNight: Boolean,
) {
    companion object {
        fun from(p: ScenePalette, light: Light): PaperColors = PaperColors(
            sky = Color(p.skyTop),
            skyBottom = Color(p.skyBottom),
            onSky = Color(p.onSky),
            onSkySoft = Color(p.onSkySoft),
            paper = light.lit(Stock.Cotton.base),
            paperInk = Ink.Graphite,
            paperInkSoft = Ink.Sepia,
            accent = Ink.Terracotta,
            handInk = Ink.Blue,
            redPencil = Ink.RedPencil,
            shadow = Ink.Shadow,
            precip = Color(p.precip),
            sun = Color(p.sun),
            isNight = light.isLamp,
        )

        val Default = from(Palettes.ClearDay, Light())
    }
}

object PaperFonts {
    private fun manrope(weight: Int) = Font(
        R.font.manrope, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    private fun caveat(weight: Int) = Font(
        R.font.caveat, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    /** The dominant: every number, label, heading and button. */
    val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

    /** The only accent: things a person wrote by hand (DESIGN_DOCTRINE §6). */
    val Hand = FontFamily(caveat(500), caveat(600), caveat(700))
}

@Immutable
data class PaperType(
    /** Temperature, cut from card. */
    val hero: TextStyle,
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val number: TextStyle,
    val hand: TextStyle,
    val handSmall: TextStyle,
) {
    companion object {
        private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        private val M = PaperFonts.Manrope

        val Default = PaperType(
            hero = TextStyle(fontFamily = M, fontWeight = FontWeight(800), fontSize = 112.sp, letterSpacing = (-0.04).em, lineHeight = 1.0.em, lineHeightStyle = tight, fontFeatureSettings = "tnum"),
            display = TextStyle(fontFamily = M, fontWeight = FontWeight(800), fontSize = 34.sp, letterSpacing = (-0.02).em, lineHeight = 1.1.em),
            title = TextStyle(fontFamily = M, fontWeight = FontWeight(700), fontSize = 22.sp, letterSpacing = (-0.01).em, lineHeight = 1.2.em),
            heading = TextStyle(fontFamily = M, fontWeight = FontWeight(700), fontSize = 16.sp, lineHeight = 1.3.em),
            body = TextStyle(fontFamily = M, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 1.45.em),
            bodyStrong = TextStyle(fontFamily = M, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 1.4.em),
            label = TextStyle(fontFamily = M, fontWeight = FontWeight(800), fontSize = 11.sp, letterSpacing = 0.12.em),
            caption = TextStyle(fontFamily = M, fontWeight = FontWeight(600), fontSize = 12.5.sp, lineHeight = 1.35.em),
            number = TextStyle(fontFamily = M, fontWeight = FontWeight(700), fontSize = 16.sp, fontFeatureSettings = "tnum"),
            hand = TextStyle(fontFamily = PaperFonts.Hand, fontWeight = FontWeight(600), fontSize = 23.sp, lineHeight = 1.18.em),
            handSmall = TextStyle(fontFamily = PaperFonts.Hand, fontWeight = FontWeight(600), fontSize = 19.sp, lineHeight = 1.2.em),
        )
    }
}

/** Letterpress (§4.5): ink pressed into a light material catches a pale rim below. */
fun TextStyle.pressed(onDark: Boolean = false): TextStyle = copy(
    shadow = if (onDark) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, -2.5f), 0f)
    else Shadow(Color.White.copy(alpha = 0.7f), Offset(0f, 2.5f), 0f),
)

/** Text laid straight onto the sky gets a soft shadow (or glow, if the ink is dark). */
fun TextStyle.onSky(lightInk: Boolean): TextStyle = copy(
    shadow = if (lightInk) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 4f), 16f)
    else Shadow(Color.White.copy(alpha = 0.45f), Offset(0f, 2f), 12f),
)

// Dynamic (not static): colours animate, and only readers should recompose.
val LocalPaperColors = compositionLocalOf { PaperColors.Default }
val LocalPaperType = staticCompositionLocalOf { PaperType.Default }

object Paper {
    val colors: PaperColors @Composable get() = LocalPaperColors.current
    val type: PaperType @Composable get() = LocalPaperType.current
    val light: Light @Composable get() = LocalLight.current
}

/** Provides colours that glide (rather than snap) whenever the sky or the light changes. */
@Composable
fun PaperTheme(palette: ScenePalette, light: Light, content: @Composable () -> Unit) {
    val target = PaperColors.from(palette, light)
    val spec = tween<Color>(durationMillis = 900)
    val sky by animateColorAsState(target.sky, spec, label = "sky")
    val skyBottom by animateColorAsState(target.skyBottom, spec, label = "skyBottom")
    val onSky by animateColorAsState(target.onSky, spec, label = "onSky")
    val onSkySoft by animateColorAsState(target.onSkySoft, spec, label = "onSkySoft")
    val paper by animateColorAsState(target.paper, spec, label = "paper")
    val precip by animateColorAsState(target.precip, spec, label = "precip")
    val sun by animateColorAsState(target.sun, spec, label = "sun")
    val colors = target.copy(sky = sky, skyBottom = skyBottom, onSky = onSky, onSkySoft = onSkySoft, paper = paper, precip = precip, sun = sun)
    CompositionLocalProvider(
        LocalPaperColors provides colors,
        LocalPaperType provides PaperType.Default,
        LocalLight provides light,
        content = content,
    )
}
