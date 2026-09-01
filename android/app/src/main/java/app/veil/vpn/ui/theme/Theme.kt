package app.veil.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The palette.
 *
 * Material 3 Expressive leans on colour to carry state, so the scheme is built
 * around one job: making "connected" and "not connected" unmistakable at a
 * glance, in either theme, without resorting to a green dot. The primary role
 * is the tunnel's own colour; the tertiary role is reserved for the moments the
 * app is escalating, so a change of route reads as movement rather than as an
 * error.
 *
 * Dynamic colour is honoured by default, as the guidelines ask, and falls back
 * to this scheme where the platform has none.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3FD3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1C0062),
    inversePrimary = Color(0xFFC9BEFF),

    secondary = Color(0xFF00696A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9CF1F0),
    onSecondaryContainer = Color(0xFF002020),

    tertiary = Color(0xFF984061),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E001D),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceDim = Color(0xFFDDD8E0),
    surfaceBright = Color(0xFFFDF7FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F1FA),
    surfaceContainer = Color(0xFFF1ECF4),
    surfaceContainerHigh = Color(0xFFECE6EF),
    surfaceContainerHighest = Color(0xFFE6E0E9),

    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC9C5D0),
    inverseSurface = Color(0xFF312F36),
    inverseOnSurface = Color(0xFFF4EFF7),
    scrim = Color(0xFF000000),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9BEFF),
    onPrimary = Color(0xFF2F0F92),
    primaryContainer = Color(0xFF4527A9),
    onPrimaryContainer = Color(0xFFE6DEFF),
    inversePrimary = Color(0xFF5B3FD3),

    secondary = Color(0xFF80D5D4),
    onSecondary = Color(0xFF003736),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFF9CF1F0),

    tertiary = Color(0xFFFFB1C8),
    onTertiary = Color(0xFF5E1133),
    tertiaryContainer = Color(0xFF7B2949),
    onTertiaryContainer = Color(0xFFFFD9E2),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3A383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),

    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF312F36),
    scrim = Color(0xFF000000),
)

@Composable
fun VeilTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        // The expressive scheme is springier and slightly overshooting, which
        // is what makes the connect button feel like a physical switch rather
        // than a progress bar.
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
