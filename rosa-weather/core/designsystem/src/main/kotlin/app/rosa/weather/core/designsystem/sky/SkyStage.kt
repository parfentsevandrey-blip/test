package app.rosa.weather.core.designsystem.sky

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * The patch of sky the sun or moon travels in, as fractions of the scene size. The body walks
 * its real daily path inside it — rising low on the left, highest at the top around noon, setting
 * low on the right — so it still moves with the time of day, yet it can never slide behind type.
 * Screens measure their free sky and hand it over; [Default] matches the home screen of a phone.
 */
@Immutable
data class SkyStage(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /**
     * Scene position (fractions) of a body at [path] (0 rising in the east … 1 setting in the
     * west) and [lift] (0 on the horizon … 1 high; negative sinks below the stage as it sets).
     */
    fun at(path: Float, lift: Float): Offset = Offset(
        left + (right - left) * path.coerceIn(0f, 1f),
        bottom - (bottom - top) * lift,
    )

    companion object {
        val Default = SkyStage(0.73f, 0.17f, 0.9f, 0.25f)

        /** Radius of the larger body (the moon) as a fraction of the scene height. */
        const val BODY_RADIUS = 0.03f

        val VectorConverter = TwoWayConverter<SkyStage, AnimationVector4D>(
            convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
            convertFromVector = { SkyStage(it.v1, it.v2, it.v3, it.v4) },
        )
    }
}
