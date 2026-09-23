package app.papersky.weather.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState

/**
 * The light in the room (DESIGN_DOCTRINE §4). The paper already takes its colour from the sky;
 * the light only adds what a real room would: a golden sheen when the sun is low, dusk gathering
 * in the corners at night, frost on the paper in the cold.
 */
@Immutable
data class Light(
    /** 0 = daylight, 1 = deep night. */
    val night: Float = 0f,
    /** Warmth of a low sun, 0..1. */
    val golden: Float = 0f,
    /** Frost creeping over paper corners, 0..1. */
    val frost: Float = 0f,
) {
    /** A surface's colour under this light: only the low sun changes it, and only a little. */
    fun lit(base: Color): Color = if (golden > 0.01f) lerp(base, GOLD, golden * 0.06f) else base

    /** Darkness gathering in the corners of the screen. */
    val vignette: Float get() = maxOf(0.3f * night, 0.08f * golden)

    companion object {
        val GOLD = Color(0xFFFFB36B)

        fun of(s: SceneState): Light {
            val night = ramp(0.55f, 0.15f, s.daylight)
            val tw = Palettes.timeWeights(s.daylight, s.sunProgress)
            val golden = ((tw[1] + tw[2] * 0.6f) * (1f - night) * (1f - s.cloudCover * 0.6f)).coerceIn(0f, 1f)
            val frost = ramp(-2f, -9f, s.temperature)
            return Light(night, golden, frost)
        }

        private fun ramp(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
    }
}

val LocalLight = staticCompositionLocalOf { Light() }
