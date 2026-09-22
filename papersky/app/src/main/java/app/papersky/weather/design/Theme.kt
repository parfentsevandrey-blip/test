package app.papersky.weather.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

/** App colours are *derived from the sky*: the whole UI re-tints with weather and time of day. */
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
    companion object {
        fun from(p: ScenePalette): PaperColors {
            val paper = Color(p.paper)
            val ink = Color(p.paperInk)
            return PaperColors(
                sky = Color(p.skyTop),
                skyBottom = Color(p.skyBottom),
                onSky = Color(p.onSky),
                onSkySoft = Color(p.onSkySoft),
                paper = paper,
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

object PaperFonts {
    private fun manrope(weight: Int) = Font(
        R.font.manrope, FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    /** One family carries the whole product; hierarchy comes from weight, size and spacing. */
    val Manrope = FontFamily(manrope(200), manrope(300), manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))
}

@Immutable
data class PaperType(
    /** The big temperature: ExtraLight, tight, tabular. */
    val hero: TextStyle,
    val display: TextStyle,
    val title: TextStyle,
    /** Line under the hero (the condition) and note headlines. */
    val lead: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    /** Longer observational sentences. */
    val quote: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val number: TextStyle,
    /** Values on detail tiles. */
    val numberLight: TextStyle,
) {
    companion object {
        private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        private val F = PaperFonts.Manrope

        val Default = PaperType(
            hero = TextStyle(fontFamily = F, fontWeight = FontWeight(200), fontSize = 128.sp, letterSpacing = (-0.06).em, lineHeight = 1.0.em, lineHeightStyle = tight, fontFeatureSettings = "tnum"),
            display = TextStyle(fontFamily = F, fontWeight = FontWeight(300), fontSize = 34.sp, letterSpacing = (-0.03).em, lineHeight = 1.1.em),
            title = TextStyle(fontFamily = F, fontWeight = FontWeight(600), fontSize = 22.sp, letterSpacing = (-0.015).em, lineHeight = 1.2.em),
            lead = TextStyle(fontFamily = F, fontWeight = FontWeight(500), fontSize = 21.sp, letterSpacing = (-0.01).em, lineHeight = 1.25.em),
            heading = TextStyle(fontFamily = F, fontWeight = FontWeight(700), fontSize = 16.sp, letterSpacing = (-0.005).em, lineHeight = 1.3.em),
            body = TextStyle(fontFamily = F, fontWeight = FontWeight(500), fontSize = 15.sp, lineHeight = 1.45.em),
            bodyStrong = TextStyle(fontFamily = F, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 1.4.em),
            quote = TextStyle(fontFamily = F, fontWeight = FontWeight(500), fontSize = 16.sp, letterSpacing = (-0.005).em, lineHeight = 1.42.em),
            label = TextStyle(fontFamily = F, fontWeight = FontWeight(700), fontSize = 11.sp, letterSpacing = 0.14.em),
            caption = TextStyle(fontFamily = F, fontWeight = FontWeight(500), fontSize = 12.5.sp, lineHeight = 1.35.em),
            number = TextStyle(fontFamily = F, fontWeight = FontWeight(600), fontSize = 16.sp, fontFeatureSettings = "tnum"),
            numberLight = TextStyle(fontFamily = F, fontWeight = FontWeight(300), fontSize = 28.sp, letterSpacing = (-0.03).em, fontFeatureSettings = "tnum"),
        )
    }
}

// Dynamic (not static): colours animate, and only readers should recompose each frame.
val LocalPaperColors = compositionLocalOf { PaperColors.Default }
val LocalPaperType = staticCompositionLocalOf { PaperType.Default }

object Paper {
    val colors: PaperColors @Composable get() = LocalPaperColors.current
    val type: PaperType @Composable get() = LocalPaperType.current
}

/** Provides colours that glide (rather than snap) whenever the sky palette changes. */
@Composable
fun PaperTheme(palette: ScenePalette, content: @Composable () -> Unit) {
    val target = PaperColors.from(palette)
    val spec = tween<Color>(durationMillis = 900)
    val sky by animateColorAsState(target.sky, spec, label = "sky")
    val skyBottom by animateColorAsState(target.skyBottom, spec, label = "skyBottom")
    val onSky by animateColorAsState(target.onSky, spec, label = "onSky")
    val onSkySoft by animateColorAsState(target.onSkySoft, spec, label = "onSkySoft")
    val paper by animateColorAsState(target.paper, spec, label = "paper")
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
    CompositionLocalProvider(LocalPaperColors provides colors, LocalPaperType provides PaperType.Default, content = content)
}
