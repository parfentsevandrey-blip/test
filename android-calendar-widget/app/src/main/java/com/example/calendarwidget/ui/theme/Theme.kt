package com.example.calendarwidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

private val DarkScheme = darkColorScheme(
    primary = DefaultAccent,
    background = AppBgBottom,
    surface = AppBgTop,
    onPrimary = TextPrimaryDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
)

private val LightScheme = lightColorScheme(
    primary = DefaultAccent,
    background = androidx.compose.ui.graphics.Color(0xFFFDF4EE),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onPrimary = TextPrimaryLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
)

/**
 * App theme. On Android 12+ it adopts Material You dynamic colour
 * (`dynamicDark/LightColorScheme`); on older devices it falls back to the
 * design's static schemes (раздел 4 — Dynamic color).
 */
@Composable
fun CalendarWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.withManrope(),
        displayMedium = base.displayMedium.withManrope(),
        displaySmall = base.displaySmall.withManrope(),
        headlineLarge = base.headlineLarge.withManrope(),
        headlineMedium = base.headlineMedium.withManrope(),
        headlineSmall = base.headlineSmall.withManrope(),
        titleLarge = base.titleLarge.withManrope(),
        titleMedium = base.titleMedium.withManrope(),
        titleSmall = base.titleSmall.withManrope(),
        bodyLarge = base.bodyLarge.withManrope(),
        bodyMedium = base.bodyMedium.withManrope(),
        bodySmall = base.bodySmall.withManrope(),
        labelLarge = base.labelLarge.withManrope(),
        labelMedium = base.labelMedium.withManrope(),
        labelSmall = base.labelSmall.withManrope(),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

private fun TextStyle.withManrope(): TextStyle = copy(fontFamily = Manrope)
