package app.papersky.weather.design

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Springs of DESIGN_DOCTRINE §9. Everything physical moves on one of these. */
object Motion {
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> release(): SpringSpec<T> = spring(dampingRatio = 0.42f, stiffness = 420f)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 240f)
    fun <T> lift(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 380f)
    fun <T> flip(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 170f)
    fun <T> fold(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 170f)
    fun <T> wiggle(): SpringSpec<T> = spring(dampingRatio = 0.28f, stiffness = 220f)
    fun <T> snap(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 520f)
    fun <T> string(): SpringSpec<T> = spring(dampingRatio = 0.2f, stiffness = 30f)
    fun <T> digit(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 300f)

    /** Delay between sheets laid down one after another. */
    const val STAGGER_MS = 70L
}

/** Lift levels of DESIGN_DOCTRINE §4.2. */
class Elevation(val h: Dp, val contactBlur: Dp, val contactAlpha: Float, val ambientBlur: Dp, val ambientAlpha: Float) {
    companion object {
        val Flat = Elevation(0.dp, 0.dp, 0f, 0.dp, 0f)
        val Resting = Elevation(1.5.dp, 1.dp, 0.2f, 5.dp, 0.14f)
        val Pinned = Elevation(3.dp, 1.5.dp, 0.24f, 12.dp, 0.2f)
        val Lifted = Elevation(9.dp, 3.dp, 0.18f, 26.dp, 0.26f)
        val Flying = Elevation(16.dp, 4.dp, 0.12f, 36.dp, 0.24f)

        fun level(i: Int): Elevation = when (i) {
            0 -> Flat
            1 -> Resting
            2 -> Pinned
            3 -> Lifted
            else -> Flying
        }
    }
}
