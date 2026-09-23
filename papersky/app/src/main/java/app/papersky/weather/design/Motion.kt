package app.papersky.weather.design

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Springs of DESIGN_DOCTRINE §9. Paper is heavy: almost no overshoot anywhere. */
object Motion {
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> release(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 600f)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.95f, stiffness = 190f)
    fun <T> lift(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 420f)
    fun <T> flip(): SpringSpec<T> = spring(dampingRatio = 0.88f, stiffness = 170f)
    fun <T> fold(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 210f)
    fun <T> snap(): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = 520f)
    fun <T> string(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 70f)
    fun <T> digit(): SpringSpec<T> = spring(dampingRatio = 0.95f, stiffness = 280f)

    /** Delay between sheets laid down one after another. */
    const val STAGGER_MS = 60L
}

/**
 * Elevation levels of DESIGN_DOCTRINE §4.2: a tight contact shadow and a wide soft one, both
 * falling straight down from a light just above and to the left.
 */
class Elevation(
    val contactY: Dp, val contactBlur: Dp, val contactAlpha: Float,
    val ambientY: Dp, val ambientBlur: Dp, val ambientAlpha: Float,
) {
    companion object {
        val Flat = Elevation(0.dp, 0.dp, 0f, 0.dp, 0.dp, 0f)
        val Sheet = Elevation(0.6.dp, 1.5.dp, 0.10f, 5.dp, 16.dp, 0.08f)
        val Raised = Elevation(1.dp, 2.dp, 0.12f, 8.dp, 22.dp, 0.12f)
        val Held = Elevation(1.5.dp, 3.dp, 0.12f, 14.dp, 34.dp, 0.16f)
        val Floating = Elevation(2.dp, 4.dp, 0.10f, 22.dp, 48.dp, 0.18f)

        fun level(i: Int): Elevation = when (i) {
            0 -> Flat
            1 -> Sheet
            2 -> Raised
            3 -> Held
            else -> Floating
        }
    }
}
