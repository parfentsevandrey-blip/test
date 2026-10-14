package app.rosa.weather.core.designsystem.seaglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rosa.weather.core.designsystem.R
import app.rosa.weather.core.designsystem.theme.RosaColors
import app.rosa.weather.core.designsystem.theme.RosaType

/**
 * «Цветное стекло» (tinted sea glass): the interface in coloured, frosted glass — a pastel body
 * paler toward the bottom, a fine grain of dark and white specks, a rim lit from within along its
 * lower edge, and a soft shadow with a glow of the glass's own colour under it — on a pale canvas
 * instead of the sky. Type and icons in it are always a deep shade of its own hue.
 *
 * Colours from the design tokens (design_tokens.json).
 */
@Immutable
data class SeaPalette(
    val top: Color,
    val bottom: Color,
    /** How much of the canvas the glass hides, 0..1. */
    val opacity: Float,
    val rimTop: Color,
    val rimBottom: Color,
    /** Type and icons on this glass: a deep shade of its hue. */
    val content: Color,
    val speckDark: Color,
    val speckLight: Color,
    /** A frosted pill set into this glass: the selected tab, a nested button. */
    val pill: Color,
    /** The coloured light the glass casts under itself. */
    val spot: Color,
)

/** Which glass an element is made of. */
enum class SeaTone { Lilac, Cyan, Periwinkle, Rose, Peach, Mint, Milk }

object SeaGlass {
    val Canvas = Color(0xFFF6F7FB)
    val CanvasLilac = Color(0xFFF8F2FA)
    val CanvasCool = Color(0xFFEEF3FA)
    val Sheet = Color(0xFFEBF1F8)

    /** Ink on the canvas itself, and its quieter grey for captions. */
    val Ink = Color(0xFF1E2229)
    val SecondaryLabel = Color(0xFF68707A)

    /** The cool shadow every piece of glass casts, under its coloured glow. */
    val AmbientShadow = Color(0xFF142E3D)

    private fun palette(
        top: Long,
        bottom: Long,
        opacity: Float,
        rim: Long,
        rimTop: Float,
        rimBottom: Float,
        content: Long,
        speckDark: Long = content,
        darkAlpha: Float,
        lightAlpha: Float,
        pill: Long,
        spot: Long,
    ) = SeaPalette(
        top = Color(0xFF000000 or top),
        bottom = Color(0xFF000000 or bottom),
        opacity = opacity,
        rimTop = Color(0xFF000000 or rim).copy(alpha = rimTop),
        rimBottom = Color(0xFF000000 or rim).copy(alpha = rimBottom),
        content = Color(0xFF000000 or content),
        speckDark = Color(0xFF000000 or speckDark).copy(alpha = darkAlpha),
        speckLight = Color.White.copy(alpha = lightAlpha),
        pill = Color(pill),
        spot = Color(spot),
    )

    val Lilac = palette(0xEBD2FF, 0xF7E8FF, 0.88f, 0xFFFFFF, 0.45f, 0.9f, 0x531B75, darkAlpha = 0.14f, lightAlpha = 0.5f, pill = 0x59FFFFFF, spot = 0x66D9B2FF)
    val Cyan = palette(0xACE0FF, 0xD4F7FF, 0.88f, 0xE8FFFF, 0.45f, 0.95f, 0x154A69, darkAlpha = 0.16f, lightAlpha = 0.6f, pill = 0x66FFFFFF, spot = 0x668CD6FF)
    val Periwinkle = palette(0xB8CEFF, 0xDCE9FF, 0.88f, 0xF0F6FF, 0.45f, 0.9f, 0x1E3D7A, darkAlpha = 0.14f, lightAlpha = 0.5f, pill = 0x59FFFFFF, spot = 0x669EBEFF)
    val Rose = palette(0xFFB5B0, 0xFFE0DB, 0.9f, 0xFFF5F2, 0.45f, 0.9f, 0x7A1F1D, darkAlpha = 0.17f, lightAlpha = 0.5f, pill = 0x59FFFFFF, spot = 0x66FF9E99)
    val Peach = palette(0xFFD5B8, 0xFFF4E0, 0.88f, 0xFFFDF5, 0.5f, 0.95f, 0x6E3B16, darkAlpha = 0.14f, lightAlpha = 0.6f, pill = 0x66FFFFFF, spot = 0x66FFC89E)
    val Mint = palette(0xB6F0DC, 0xDFF9F0, 0.88f, 0xF2FFFA, 0.45f, 0.9f, 0x14543E, darkAlpha = 0.14f, lightAlpha = 0.5f, pill = 0x59FFFFFF, spot = 0x669BE6CA)
    val Milk = palette(0xFFFFFF, 0xF5F7FA, 0.94f, 0xFFFFFF, 0.7f, 1f, 0x1E2229, speckDark = 0x8C96A6, darkAlpha = 0.13f, lightAlpha = 1f, pill = 0x80FFFFFF, spot = 0x26142E3D)

    fun palette(tone: SeaTone): SeaPalette = when (tone) {
        SeaTone.Lilac -> Lilac
        SeaTone.Cyan -> Cyan
        SeaTone.Periwinkle -> Periwinkle
        SeaTone.Rose -> Rose
        SeaTone.Peach -> Peach
        SeaTone.Mint -> Mint
        SeaTone.Milk -> Milk
    }

    object Dimens {
        val Squircle = 26.dp
        val SmallFab = 20.dp
        val Card = 32.dp
        val Rim = 1.5.dp
        val NestedPadding = 6.dp
        val Elevation = 10.dp
        val Floating = 14.dp
    }

    /** The app's colours on the canvas: deep slate ink, grey captions, chart colours that read on pastel. */
    val CanvasColors = RosaColors(
        ink = Ink,
        inkSoft = SecondaryLabel,
        inkFaint = SecondaryLabel.copy(alpha = 0.6f),
        accent = Color(0xFF3D5FA8),
        warm = Color(0xFFE07A4F),
        cool = Color(0xFF4F86D6),
        rain = Color(0xFF3F7FD6),
        glassTint = Color.White,
        fill = Ink.copy(alpha = 0.06f),
        isLightSky = true,
        zenith = Color(0xFFB8CEFF),
        horizon = Canvas,
    )

    /** The colours inside glass of [palette]: every bit of type and every icon in its own deep hue. */
    fun colorsOn(palette: SeaPalette, base: RosaColors): RosaColors = base.copy(
        ink = palette.content,
        inkSoft = palette.content.copy(alpha = 0.74f),
        inkFaint = palette.content.copy(alpha = 0.48f),
        accent = palette.content,
        fill = palette.content.copy(alpha = 0.08f),
    )

    private fun inter(weight: Int) = Font(
        R.font.inter,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    /** Inter, the face of the design system (Google Sans is not licensed for apps). */
    val Inter = FontFamily(inter(300), inter(400), inter(500), inter(600), inter(700))

    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

    /** The type scale in Inter, after the design tokens: labels 600, captions 500. */
    val Type = RosaType(
        hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-4).sp, lineHeightStyle = trim),
        display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
        numeral = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp),
        title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.4).sp),
        headline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
        label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
        caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
    )
}

/** True when the app is in sea glass ([app.rosa.weather.core.model.Appearance.SeaGlass]). */
val LocalSeaGlass = staticCompositionLocalOf { false }

/** The glass the content here sits on (null: the canvas). */
val LocalSeaPalette = staticCompositionLocalOf<SeaPalette?> { null }

object Sea {
    val enabled: Boolean @Composable get() = LocalSeaGlass.current
}
