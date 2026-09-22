package app.papersky.weather.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState

/**
 * The room's light (DESIGN_DOCTRINE §4.3). Paper never turns dark: at night a desk lamp warms it
 * and the corners of the room fall into shadow; at golden hour the sun gilds it.
 */
@Immutable
data class Light(
    /** 0 = daylight from the window, 1 = only the desk lamp. */
    val lamp: Float = 0f,
    /** Warmth of a low sun, 0..1. */
    val golden: Float = 0f,
    /** Frost creeping over paper corners, 0..1. */
    val frost: Float = 0f,
) {
    val isLamp: Boolean get() = lamp > 0.5f

    /** A material's colour under this light. */
    fun lit(base: Color): Color {
        var c = base
        if (golden > 0.01f) c = lerp(c, GOLD, golden * 0.09f)
        if (lamp > 0.01f) {
            c = lerp(c, LAMP, lamp * 0.18f)
            val k = 1f - 0.05f * lamp
            c = Color(c.red * k, c.green * k, c.blue * k, c.alpha)
        }
        return c
    }

    /** Shadows are deeper under a single lamp. */
    val shadowBoost: Float get() = 1f + 0.25f * lamp

    /** Top-edge highlight strength. */
    val edgeLight: Float get() = 0.6f - 0.35f * lamp

    /** Darkness in the corners of the room. */
    val vignette: Float get() = maxOf(0.45f * lamp, 0.1f * golden)

    companion object {
        val GOLD = Color(0xFFFFB36B)
        val LAMP = Color(0xFFFFC78A)
        val LAMP_POOL = Color(0xFFFFD9A0)

        fun of(s: SceneState): Light {
            val lamp = ramp(0.55f, 0.2f, s.daylight)
            val tw = Palettes.timeWeights(s.daylight, s.sunProgress)
            val golden = ((tw[1] + tw[2] * 0.6f) * (1f - lamp) * (1f - s.cloudCover * 0.6f)).coerceIn(0f, 1f)
            val frost = ramp(-2f, -9f, s.temperature)
            return Light(lamp, golden, frost)
        }

        private fun ramp(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
    }
}

val LocalLight = staticCompositionLocalOf { Light() }
