package com.monthcalendar.widget.ui.theme

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

private val Seed = Color(0xFF4A52CC)
private val Light = lightColorScheme(primary = Seed, background = Color(0xFFFCF8FB), surface = Color(0xFFFCF8FB))
private val Dark = darkColorScheme(primary = Color(0xFFBEC2FF), background = Color(0xFF131316), surface = Color(0xFF1B1B1F))

/**
 * Material 3 theme for the settings screen. On Android 12+ it uses the live
 * Material You system palette (so the app and preview match the home-screen
 * widget); older devices fall back to a fixed indigo scheme.
 */
@Composable
fun CalendarTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
