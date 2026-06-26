package com.monthcalendar.widget.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * The shared widget design system.
 *
 * One 4dp spacing grid, one shape scale, one steep type ramp, and a [WPalette]
 * of semantic colour roles so the same primitives render either on a coloured
 * gradient (weather) or on the live Material-You themed surface (calendar).
 * Emphasis (Medium/Bold weight) is spent only on the single hero per view plus
 * "today"; everything else stays at Normal/Medium — that restraint is what
 * reads as premium rather than busy.
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
    val s12: Dp = 24.dp

    // shape scale
    val rSm: Dp = 12.dp
    val rMd: Dp = 16.dp
    val rLg: Dp = 20.dp
    val rXl: Dp = 28.dp

    // type ramp
    val display: TextUnit = 40.sp     // weather hero temperature
    val display2: TextUnit = 32.sp
    val headline: TextUnit = 22.sp    // calendar month title
    val title: TextUnit = 18.sp
    val titleSm: TextUnit = 15.sp
    val body: TextUnit = 14.sp
    val label: TextUnit = 13.sp
    val caption: TextUnit = 11.sp
    val eyebrow: TextUnit = 11.sp
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

    /** White-on-colour skin for the weather widget's gradient hero. */
    fun onGradient() = WPalette(
        textPrimary = cp(Color.White),
        textSecondary = cp(Color(0xD9FFFFFF)),
        textFaint = cp(Color(0x99FFFFFF)),
        container = cp(Color(0x29FFFFFF)),
        containerStrong = cp(Color(0x3DFFFFFF)),
        onContainer = cp(Color.White),
        accent = cp(Color.White),
        onAccent = cp(Color(0xFF0B1220)),
        info = cp(Color(0xFFAEDBFF)),
    )

    /** Live Material-You themed skin for the calendar widget. */
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

/** Small-caps eyebrow label — the section "tag" of the design language. */
@Composable
fun Eyebrow(text: String, p: WPalette) {
    Text(
        text = text.uppercase(),
        maxLines = 1,
        style = TextStyle(color = p.textFaint, fontSize = D.eyebrow, fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
    )
}

/**
 * A compact metric tile (optional mono icon · value · small-caps label). Used
 * for weather stat strips; [modifier] is expected to carry the glass surface
 * and the row weight from the caller.
 */
@Composable
fun MetricTile(modifier: GlanceModifier, value: String, label: String, p: WPalette, iconRes: Int? = null) {
    Column(
        modifier = modifier.padding(vertical = D.s3, horizontal = D.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (iconRes != null) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(p.textSecondary),
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(GlanceModifier.height(D.s1))
        }
        Text(value, maxLines = 1, style = TextStyle(color = p.onContainer, fontSize = D.label, fontWeight = FontWeight.Bold))
        Text(label.uppercase(), maxLines = 1, style = TextStyle(color = p.textFaint, fontSize = D.eyebrow))
    }
}
