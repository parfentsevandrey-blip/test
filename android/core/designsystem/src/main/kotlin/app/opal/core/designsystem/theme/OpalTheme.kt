package app.opal.core.designsystem.theme

import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.glass.GlassQuality
import app.opal.core.designsystem.glass.LocalGlassQuality
import app.opal.core.designsystem.util.Haptics
import app.opal.core.designsystem.util.rememberScreenCornerRadius

val LocalOpalColors = staticCompositionLocalOf { LightColors }
val LocalOpalTypography = staticCompositionLocalOf { OpalTypography() }

/**
 * Motion: false when the user disabled animations (Settings → Accessibility → Remove animations).
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Radius of the physical screen corners; outer UI shapes are concentric with it. */
val LocalScreenCornerRadius = staticCompositionLocalOf { 32.dp }

val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

object OpalTheme {
    val colors: OpalColors
        @Composable get() = LocalOpalColors.current

    val type: OpalTypography
        @Composable get() = LocalOpalTypography.current
}

/**
 * Root theme. [simplifiedGraphics] (user toggle) and the system battery saver both switch glass to
 * a cheaper rendering and freeze the aurora.
 */
@Composable
fun OpalTheme(dark: Boolean, simplifiedGraphics: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val powerSave by rememberPowerSaveMode()
    val reducedMotion =
        remember(context) {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }
    val quality =
        GlassQuality.forDevice(Build.VERSION.SDK_INT, simplified = simplifiedGraphics || powerSave)
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    val haptics = remember(view) { Haptics(view) }
    val material =
        if (dark)
            darkColorScheme(
                primary = colors.accent,
                background = colors.background,
                surface = colors.background,
            )
        else
            lightColorScheme(
                primary = colors.accent,
                background = colors.background,
                surface = colors.background,
            )
    MaterialTheme(colorScheme = material) {
        CompositionLocalProvider(
            LocalOpalColors provides colors,
            LocalOpalTypography provides OpalTypography(),
            LocalReducedMotion provides reducedMotion,
            LocalGlassQuality provides quality,
            LocalScreenCornerRadius provides rememberScreenCornerRadius(),
            LocalHaptics provides haptics,
            LocalIndication provides ripple(),
            content = content,
        )
    }
}

@Composable
private fun rememberPowerSaveMode(): androidx.compose.runtime.State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val pm = context.getSystemService(PowerManager::class.java)
        state.value = pm?.isPowerSaveMode == true
        val receiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                    state.value = pm?.isPowerSaveMode == true
                }
            }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Radius for a shape inset by [inset] from the screen edge, concentric with the screen corner. */
fun concentricRadius(screenRadius: Dp, inset: Dp, minimum: Dp = 12.dp): Dp =
    maxOf(minimum, screenRadius - inset)
