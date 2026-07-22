package com.lumina.calendarwidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandIndigo = Color(0xFF6366F1)
private val BrandIndigoDark = Color(0xFFA5B4FC)

private val LightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E2FF),
    onPrimaryContainer = Color(0xFF11144B),
    secondary = Color(0xFF5B5D72),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF46464F),
)

private val DarkColors = darkColorScheme(
    primary = BrandIndigoDark,
    onPrimary = Color(0xFF1E2064),
    primaryContainer = Color(0xFF373A8B),
    onPrimaryContainer = Color(0xFFE0E2FF),
    secondary = Color(0xFFC5C4DD),
    background = Color(0xFF121218),
    surface = Color(0xFF1A1A21),
    surfaceVariant = Color(0xFF46464F),
    onSurface = Color(0xFFE5E1E9),
    onSurfaceVariant = Color(0xFFC8C5D0),
)

/** App theme with Material You dynamic color on Android 12+, brand fallback otherwise. */
@Composable
fun LuminaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LuminaTypography,
        content = content,
    )
}
