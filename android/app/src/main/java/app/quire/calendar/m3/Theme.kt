package app.quire.calendar.m3

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The app's Material 3 Expressive theme.
 *
 * Colour comes from the device wherever the device has an opinion: from Android 12 the platform
 * derives a whole scheme from the wallpaper and publishes it, and `dynamicLightColorScheme` hands
 * it over as the Material roles. That is the point of the setting — a calendar that matches the
 * phone it lives on — so it is on by default and the fixed scheme below is only the fallback for
 * an older device or someone who would rather choose.
 *
 * Motion is the expressive scheme rather than the standard one: springs with a little overshoot,
 * which is what makes M3 Expressive feel like itself. `MaterialExpressiveTheme` also brings the
 * expressive shape and type scales, so components pick up the rounder, bolder defaults.
 */
@Composable
fun QuireTheme(
    dark: Boolean,
    dynamic: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // The configuration is read, and the scheme keyed on it, because that is the only thing that
    // ties this to the user changing their colours. `dynamicLightColorScheme` is a plain function
    // over the context's resources: it answers with whatever is current when it is called, and
    // nothing calls it again on its own. Without a read of the configuration here, a screen that
    // is already composed keeps the colours it was composed with — which is exactly the bug this
    // fixes, an app that only took the new palette after being killed and reopened.
    val configuration = LocalConfiguration.current
    val scheme: ColorScheme = remember(dark, dynamic, configuration) {
        when {
            dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme(
                primary = SeedPrimaryDark,
                secondary = SeedSecondaryDark,
                tertiary = SeedTertiaryDark,
            )
            else -> lightColorScheme(
                primary = SeedPrimaryLight,
                secondary = SeedSecondaryLight,
                tertiary = SeedTertiaryLight,
            )
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

// The fallback scheme, for a device with no dynamic colour and for anyone who turns it off. It is
// the cinnabar the app has always opened on, with the two harmonised hues Material asks for.
private val SeedPrimaryLight = Color(0xFF8F4A3C)
private val SeedSecondaryLight = Color(0xFF77574F)
private val SeedTertiaryLight = Color(0xFF6C5D2F)

private val SeedPrimaryDark = Color(0xFFFFB5A0)
private val SeedSecondaryDark = Color(0xFFE7BDB2)
private val SeedTertiaryDark = Color(0xFFD8C58D)
