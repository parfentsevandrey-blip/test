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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.ScenePalette

/**
 * Paper, ink and one pigment (DESIGN_DOCTRINE §5). The sky and the print above it carry the
 * weather's colour; the paper only takes a breath of it — ivory by day, charcoal by night — so
 * every sheet reads as the same stock printed with two inks.
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
    val shadow: Color,
    val precip: Color,
    val sun: Color,
    val isNight: Boolean,
) {
    /** Indigo for precipitation printed on this paper. */
    val rainInk: Color get() = if (isNight) Color(0xFFA3B8CE) else Color(0xFF3F5E80)

    /** Hairline rules and outlines. */
    val rule: Color get() = paperInk.copy(alpha = 0.1f)

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
                paperInkSoft = ink.copy(alpha = if (p.isDarkPaper) 0.62f else 0.6f),
                accent = Color(p.accent),
                shadow = shadowOf(p.isDarkPaper),
                precip = Color(p.precip),
                sun = Color(p.sun),
                isNight = p.isDarkPaper,
            )
        }

        val Default = from(Palettes.ClearDay)

        /** Warm sepia under ivory paper, plain black under charcoal (§4.2). */
        fun shadowOf(night: Boolean) = if (night) Color.Black else Color(0xFF2B2118)
    }
}

/**
 * Two faces, three voices (DESIGN_DOCTRINE §6): Cormorant Garamond speaks the figures and the names,
 * its italic is the human voice, Manrope carries the data. The serif is rebuilt with lining figures
 * on by default, so numbers stand upright everywhere, widgets included.
 */
object PaperFonts {
    private fun font(res: Int, weight: Int, italic: Boolean = false) = Font(
        res, FontWeight(weight), if (italic) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val Serif = FontFamily((300..700 step 100).map { font(R.font.serif, it) })
    val Italic = FontFamily((300..600 step 100).map { font(R.font.serif_italic, it, italic = true) })
    val Sans = FontFamily((400..600 step 100).map { font(R.font.manrope, it) })
}

@Immutable
data class PaperType(
    /** The temperature: large and light, standing in the sky. */
    val hero: TextStyle,
    /** Screen titles. */
    val display: TextStyle,
    /** Values on tiles, big temperatures on cards. */
    val title: TextStyle,
    /** Names: places, days, card titles. */
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    /** Small capitals over a section. */
    val label: TextStyle,
    val caption: TextStyle,
    /** Temperatures in lists and charts. */
    val number: TextStyle,
    /** The human voice: notes, hints, the back of a tile. */
    val note: TextStyle,
    /** The weather in words under the temperature; the day's main note. */
    val noteLarge: TextStyle,
) {
    companion object {
        private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        private val S = PaperFonts.Serif
        private val I = PaperFonts.Italic
        private val G = PaperFonts.Sans

        val Default = PaperType(
            hero = TextStyle(fontFamily = S, fontWeight = FontWeight(300), fontSize = 136.sp, letterSpacing = (-0.02).em, lineHeight = 1.0.em, lineHeightStyle = tight),
            display = TextStyle(fontFamily = S, fontWeight = FontWeight(400), fontSize = 38.sp, letterSpacing = (-0.01).em, lineHeight = 1.1.em),
            title = TextStyle(fontFamily = S, fontWeight = FontWeight(500), fontSize = 28.sp, lineHeight = 1.15.em),
            heading = TextStyle(fontFamily = S, fontWeight = FontWeight(600), fontSize = 21.sp, lineHeight = 1.2.em),
            body = TextStyle(fontFamily = G, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 1.5.em),
            bodyStrong = TextStyle(fontFamily = G, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 1.4.em),
            label = TextStyle(fontFamily = G, fontWeight = FontWeight(600), fontSize = 10.5.sp, letterSpacing = 0.2.em),
            caption = TextStyle(fontFamily = G, fontWeight = FontWeight(500), fontSize = 12.5.sp, letterSpacing = 0.01.em, lineHeight = 1.4.em),
            number = TextStyle(fontFamily = S, fontWeight = FontWeight(600), fontSize = 19.sp, fontFeatureSettings = "tnum"),
            note = TextStyle(fontFamily = I, fontWeight = FontWeight(400), fontSize = 21.sp, fontStyle = FontStyle.Italic, lineHeight = 1.25.em),
            noteLarge = TextStyle(fontFamily = I, fontWeight = FontWeight(400), fontSize = 30.sp, fontStyle = FontStyle.Italic, lineHeight = 1.15.em),
        )
    }
}

/** Text printed on the sky gets a soft shadow (or a pale glow, if the ink is dark) (§4.4). */
fun TextStyle.onSky(lightInk: Boolean): TextStyle = copy(
    shadow = if (lightInk) Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 2f), 12f)
    else Shadow(Color.White.copy(alpha = 0.35f), Offset(0f, 2f), 12f),
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
    val spec = tween<Color>(durationMillis = 1200)
    val sky by animateColorAsState(target.sky, spec, label = "sky")
    val skyBottom by animateColorAsState(target.skyBottom, spec, label = "skyBottom")
    val onSky by animateColorAsState(target.onSky, spec, label = "onSky")
    val onSkySoft by animateColorAsState(target.onSkySoft, spec, label = "onSkySoft")
    val paper by animateColorAsState(light.lit(target.paper), spec, label = "paper")
    val ink by animateColorAsState(target.paperInk, spec, label = "ink")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val shadow by animateColorAsState(target.shadow, spec, label = "shadow")
    val precip by animateColorAsState(target.precip, spec, label = "precip")
    val sun by animateColorAsState(target.sun, spec, label = "sun")
    val colors = PaperColors(
        sky = sky, skyBottom = skyBottom, onSky = onSky, onSkySoft = onSkySoft, paper = paper,
        paperInk = ink, paperInkSoft = ink.copy(alpha = if (target.isNight) 0.62f else 0.6f), accent = accent,
        shadow = shadow, precip = precip, sun = sun, isNight = target.isNight,
    )
    CompositionLocalProvider(
        LocalPaperColors provides colors,
        LocalPaperType provides PaperType.Default,
        LocalLight provides light,
        content = content,
    )
}
