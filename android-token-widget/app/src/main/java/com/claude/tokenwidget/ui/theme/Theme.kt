package com.claude.tokenwidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ClaudeClay = Color(0xFFD97757)
private val ClaudeClayDark = Color(0xFFE89A82)

private val LightColors = lightColorScheme(
    primary = ClaudeClay,
    secondary = Color(0xFF6F5C52),
    background = Color(0xFFFBF7F2),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = ClaudeClayDark,
    secondary = Color(0xFFD2BFB4),
    background = Color(0xFF1F1D1B),
    surface = Color(0xFF2A2724),
)

/**
 * Material 3 (Expressive) theme. Uses dynamic colour on Android 12+ so the app
 * and the home-screen widget share the device's wallpaper-derived palette;
 * falls back to the Claude clay brand scheme otherwise.
 */
@Composable
fun ClaudeTokenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
