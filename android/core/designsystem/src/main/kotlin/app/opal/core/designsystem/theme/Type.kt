package app.opal.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.opal.core.designsystem.R

private fun inter(resId: Int, weight: FontWeight) =
    Font(
        resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** Inter (SIL OFL 1.1), subset to Latin + Cyrillic, variable weight. Text cut: optical size 14. */
val InterText =
    FontFamily(
        inter(R.font.inter, FontWeight.Normal),
        inter(R.font.inter, FontWeight.Medium),
        inter(R.font.inter, FontWeight.SemiBold),
        inter(R.font.inter, FontWeight.Bold),
    )

/** Display cut (optical size 32) for large numerals and titles. */
val InterDisplay =
    FontFamily(
        inter(R.font.inter_display, FontWeight.Medium),
        inter(R.font.inter_display, FontWeight.SemiBold),
        inter(R.font.inter_display, FontWeight.Bold),
    )

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

@Immutable
data class OpalTypography(
    val hero: TextStyle =
        TextStyle(
            fontFamily = InterDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.02).em,
            lineHeightStyle = tight,
        ),
    val title: TextStyle =
        TextStyle(
            fontFamily = InterDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.015).em,
        ),
    val headline: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.01).em,
        ),
    val subhead: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
    val body: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
    val bodyStrong: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
    val callout: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
    val label: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.01.em,
        ),
    val caption: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
    /** Tabular figures for speeds, timers and percentages (no jitter while counting). */
    val numeric: TextStyle =
        TextStyle(
            fontFamily = InterText,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontFeatureSettings = "tnum",
        ),
    val heroNumeric: TextStyle =
        TextStyle(
            fontFamily = InterDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontFeatureSettings = "tnum",
            letterSpacing = (-0.02).em,
        ),
)
