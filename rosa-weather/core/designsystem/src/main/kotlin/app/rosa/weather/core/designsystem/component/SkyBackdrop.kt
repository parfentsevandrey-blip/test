package app.rosa.weather.core.designsystem.component

import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.rosa.weather.core.designsystem.glass.GlassEnvironment
import app.rosa.weather.core.designsystem.glass.LocalGlassEnvironment
import app.rosa.weather.core.designsystem.glass.backdropSource
import app.rosa.weather.core.designsystem.glass.rememberBackdrop
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.haptics.rememberRosaHaptics
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.motion.rememberSystemMotionEnabled
import app.rosa.weather.core.designsystem.sensor.rememberTilt
import app.rosa.weather.core.designsystem.sensor.toLightAngle
import app.rosa.weather.core.designsystem.sky.SceneQuality
import app.rosa.weather.core.designsystem.sky.SkyParams
import app.rosa.weather.core.designsystem.sky.SkyScene
import app.rosa.weather.core.designsystem.theme.RosaTheme
import app.rosa.weather.core.designsystem.theme.animatedRosaColors
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.SkyPalette

/**
 * Root of every Rosa screen: theme coloured by the sky, haptics, motion preferences and the
 * shared glass lighting (tint adapts to the sky; highlights follow device tilt).
 */
@Composable
fun RosaEnvironment(settings: AppSettings, palette: SkyPalette, content: @Composable () -> Unit) {
    val colors = animatedRosaColors(palette)
    val haptics = rememberRosaHaptics(settings.haptics)
    val motion = rememberSystemMotionEnabled()
    val tilt = rememberTilt(enabled = settings.tiltLighting && motion)
    val environment = remember { GlassEnvironment() }
    environment.tint = colors.glassTint
    LaunchedEffect(tilt) {
        snapshotFlow { tilt.value }.collect { environment.lightAngle = it.toLightAngle() }
    }
    RosaTheme(colors) {
        CompositionLocalProvider(
            LocalHaptics provides haptics,
            LocalMotionEnabled provides motion,
            LocalGlassEnvironment provides environment,
            LocalTilt provides tilt,
        ) {
            content()
        }
    }
}

val LocalTilt = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<androidx.compose.ui.geometry.Offset>?> { null }

/**
 * Paints the living sky and makes it the backdrop that all glass on top refracts.
 */
@Composable
fun SkyBackdrop(
    params: SkyParams,
    effects: EffectsQuality,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    transitionMillis: Int = 1400,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberBackdrop()
    val quality = rememberSceneQuality(effects)
    val motion = LocalMotionEnabled.current
    Box(modifier.fillMaxSize()) {
        SkyScene(
            params = params,
            modifier = Modifier.fillMaxSize().backdropSource(backdrop),
            quality = quality,
            tilt = LocalTilt.current,
            animate = motion,
            haptics = LocalHaptics.current,
            interactive = interactive,
            transitionMillis = transitionMillis,
        )
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content()
        }
    }
}

@Composable
private fun rememberSceneQuality(effects: EffectsQuality): SceneQuality {
    val context = LocalContext.current
    return remember(effects) {
        when (effects) {
            EffectsQuality.Battery -> SceneQuality.Battery
            EffectsQuality.Balanced -> SceneQuality.Balanced
            EffectsQuality.Cinematic -> SceneQuality.Cinematic
            EffectsQuality.Auto -> {
                val power = context.getSystemService(PowerManager::class.java)
                val saver = power?.isPowerSaveMode == true
                val hot = (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE
                if (saver || hot) SceneQuality.Battery else SceneQuality.Balanced
            }
        }
    }
}
