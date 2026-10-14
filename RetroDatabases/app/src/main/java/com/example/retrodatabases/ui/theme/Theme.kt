package com.example.retrodatabases.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark theme in the spirit of 80s terminals: dark background and bright accents. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFA5EEF9),
    secondary = Color(0xFFF06292),
    onSecondary = Color(0xFF5C0A2C),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE1E6EE),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFE1E6EE),
    surfaceVariant = Color(0xFF232B36),
    onSurfaceVariant = Color(0xFFB7C0CC),
    outline = Color(0xFF5B6675),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00677A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5EEF9),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFFB0215C),
    onSecondary = Color.White,
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484F),
    outline = Color(0xFF71787F),
)

@Composable
fun RetroDatabasesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
