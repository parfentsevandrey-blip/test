package com.monthcalendar.widget.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Shared widget design system (2026 Material-Expressive sensibility):
 * one 4dp spacing grid, a pill/large-radius shape scale, a high-contrast type
 * ramp, and a [WPalette] of semantic colour roles so the same components render
 * either on a coloured gradient (weather) or on the themed surface (calendar).
 */
object D {
    // 4dp spacing grid
    val s1: Dp = 2.dp
    val s2: Dp = 4.dp
    val s3: Dp = 6.dp
    val s4: Dp = 8.dp
    val s5: Dp = 10.dp
    val s6: Dp = 12.dp
    val s7: Dp = 14.dp
    val s8: Dp = 16.dp
    val s10: Dp = 20.dp

    // shape scale
    val rSm: Dp = 12.dp
    val rMd: Dp = 18.dp
    val rLg: Dp = 24.dp
    val rXl: Dp = 30.dp

    // type ramp (high contrast: huge hero, tiny eyebrow labels)
    val display: TextUnit = 44.sp
    val display2: TextUnit = 34.sp
    val headline: TextUnit = 22.sp
    val title: TextUnit = 17.sp
    val titleSm: TextUnit = 15.sp
    val body: TextUnit = 13.sp
    val label: TextUnit = 12.sp
    val caption: TextUnit = 10.sp
    val eyebrow: TextUnit = 9.sp
}

/** Semantic colour roles, resolved to concrete providers. */
data class WPalette(
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val textFaint: ColorProvider,
    val container: ColorProvider,
    val containerStrong: ColorProvider,
    val onContainer: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
    val info: ColorProvider,
)

object WPalettes {
    private fun cp(c: Color) = ColorProvider(c)

    /** White-on-colour skin, for the weather widget's gradient hero. */
    fun onGradient() = WPalette(
        textPrimary = cp(Color.White),
        textSecondary = cp(Color(0xCCFFFFFF)),
        textFaint = cp(Color(0x8AFFFFFF)),
        container = cp(Color(0x24FFFFFF)),
        containerStrong = cp(Color(0x3DFFFFFF)),
        onContainer = cp(Color.White),
        accent = cp(Color.White),
        onAccent = cp(Color(0xFF0B1220)),
        info = cp(Color(0xCC9FD8FF)),
    )

    /** Themed skin, derived from dynamic Material colours (calendar widget). */
    @Composable
    fun themed(): WPalette = WPalette(
        textPrimary = GlanceTheme.colors.onSurface,
        textSecondary = GlanceTheme.colors.onSurfaceVariant,
        textFaint = GlanceTheme.colors.onSurfaceVariant,
        container = GlanceTheme.colors.secondaryContainer,
        containerStrong = GlanceTheme.colors.primaryContainer,
        onContainer = GlanceTheme.colors.onSecondaryContainer,
        accent = GlanceTheme.colors.primary,
        onAccent = GlanceTheme.colors.onPrimary,
        info = GlanceTheme.colors.primary,
    )
}

/** A tonal "glass" surface modifier (rounded + filled). */
fun glass(p: WPalette, radius: Dp = D.rMd, strong: Boolean = false): GlanceModifier =
    GlanceModifier.cornerRadius(radius).background(if (strong) p.containerStrong else p.container)

/** Small-caps eyebrow label — the 2026 "section tag" look. */
@Composable
fun Eyebrow(text: String, p: WPalette) {
    Text(
        text = text.uppercase(),
        maxLines = 1,
        style = TextStyle(color = p.textFaint, fontSize = D.eyebrow, fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
    )
}

/** A compact metric tile (value over label) used inside stat strips. */
@Composable
fun MetricTile(modifier: GlanceModifier, value: String, label: String, p: WPalette) {
    Column(
        modifier = modifier.padding(vertical = D.s3, horizontal = D.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, maxLines = 1, style = TextStyle(color = p.onContainer, fontSize = D.body, fontWeight = FontWeight.Bold))
        Text(label.uppercase(), maxLines = 1, style = TextStyle(color = p.textFaint, fontSize = D.eyebrow))
    }
}
