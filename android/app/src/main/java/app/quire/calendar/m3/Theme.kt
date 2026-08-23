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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
    /**
     * How loudly this application moves.
     *
     * Loudness is a claim about importance, and it is finite: an app where everything is loud
     * scores nothing by being loud anywhere. IBM Carbon and Atlassian arrived independently at
     * exactly two levels and no third — productive/expressive, practical/bold — and reserve the
     * loud one for rare, important moments. Android's own documentation says the same: standard
     * for utilitarian UI and repeated interactions.
     *
     * So the weather keeps expressive, where the sky and the hero are the point, and the calendar
     * takes standard. Tapping a day in a month grid is the most repeated interaction in either
     * app, and a calendar that bounces like a hero surface is overstating a change of month. The
     * effects specs are identical in both schemes, so not one fade, colour or opacity shifts by a
     * frame — the whole difference is locked inside geometry.
     */
    motion: MotionScheme = MotionScheme.standard(),
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

    // The one place stillness is enforced. Every animation in both apps resolves its spec through
    // `MaterialTheme.motionScheme`, so swapping the scheme here stops all of them at once and
    // cannot be forgotten at a new call site — which is the difference between a contract and a
    // habit. Places holding their own Animatable read `LocalStillness` and snap themselves.
    val still by rememberStillness()
    CompositionLocalProvider(LocalStillness provides still) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = if (still) CalmMotionScheme else motion,
            content = content,
        )
    }
}

// The fallback scheme, for a device with no dynamic colour and for anyone who turns it off. It is
// the cinnabar the app has always opened on, with the two harmonised hues Material asks for.
private val SeedPrimaryLight = Color(0xFF8F4A3C)
private val SeedSecondaryLight = Color(0xFF77574F)
private val SeedTertiaryLight = Color(0xFF6C5D2F)

private val SeedPrimaryDark = Color(0xFFFFB5A0)
private val SeedSecondaryDark = Color(0xFFE7BDB2)
private val SeedTertiaryDark = Color(0xFFD8C58D)
