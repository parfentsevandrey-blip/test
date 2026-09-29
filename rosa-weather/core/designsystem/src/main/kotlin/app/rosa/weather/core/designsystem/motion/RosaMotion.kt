package app.rosa.weather.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens. Apple's reference spring for Liquid Glass is `duration 0.5, bounce 0.3`, i.e.
 * stiffness ≈ 158 and damping ratio 0.7 — "gel-like", settling without wobbling forever.
 */
object RosaMotion {
    const val GelStiffness = 158f
    const val GelDamping = 0.7f

    fun <T> gel(): FiniteAnimationSpec<T> = spring(GelDamping, GelStiffness)

    fun <T> press(): FiniteAnimationSpec<T> = spring(0.5f, 300f)

    fun <T> snappy(): FiniteAnimationSpec<T> = spring(0.85f, Spring.StiffnessMediumLow)

    fun <T> lazy(): FiniteAnimationSpec<T> = spring(1f, Spring.StiffnessVeryLow)

    val gelOffset = spring(GelDamping, GelStiffness, IntOffset(1, 1))
}

/** False when the user disabled animations system-wide (Remove animations / scale 0). */
val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberSystemMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}
