package app.quire.calendar.ui

import android.content.ContentResolver
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Every moving thing in the app is a spring, not a curve.
 *
 * A duration-and-easing animation has to be restarted from zero when the target
 * changes mid-flight, which is exactly what happens when someone flicks between
 * months or grabs a panel that is still settling. A spring carries its velocity
 * across the change, so an interrupted gesture continues instead of snapping.
 */
class Spring(
    var value: Float = 0f,
    var target: Float = value,
) {
    var velocity = 0f
    var stiffness = MotionProfile.STANDARD.stiffness
    var dampingRatio = MotionProfile.STANDARD.dampingRatio

    private val damping: Float get() = 2f * dampingRatio * sqrt(stiffness)

    val atRest: Boolean
        get() = abs(value - target) < REST_DISTANCE && abs(velocity) < REST_VELOCITY

    fun snapTo(v: Float) {
        value = v
        target = v
        velocity = 0f
    }

    fun profile(profile: MotionProfile) {
        stiffness = profile.stiffness
        dampingRatio = profile.dampingRatio
    }

    /**
     * Integrates by [seconds]. Substepped at a fixed 4 ms because a dropped
     * frame would otherwise hand the integrator a step large enough to make a
     * stiff spring diverge instead of settle.
     */
    fun advance(seconds: Float): Boolean {
        if (atRest) {
            value = target
            velocity = 0f
            return false
        }
        val clamped = seconds.coerceIn(0f, 0.064f)
        val steps = ceil(clamped / 0.004f).toInt().coerceIn(1, 16)
        val h = clamped / steps
        val k = stiffness
        val c = damping
        repeat(steps) {
            val acceleration = -k * (value - target) - c * velocity
            velocity += acceleration * h
            value += velocity * h
        }
        if (atRest) {
            value = target
            velocity = 0f
            return false
        }
        return true
    }

    private companion object {
        const val REST_DISTANCE = 0.0008f
        const val REST_VELOCITY = 0.008f
    }
}

/**
 * How lively the world feels. `OFF` is not a lesser setting — it is what the
 * app switches to when the system animator scale is zero, which is how someone
 * who needs reduced motion tells every app at once.
 */
enum class MotionProfile(
    val key: String,
    val stiffness: Float,
    val dampingRatio: Float,
) {
    OFF("off", 1000f, 1f),
    CALM("calm", 130f, 1.0f),
    STANDARD("standard", 220f, 0.84f),
    PLAYFUL("playful", 340f, 0.58f),
    ;

    val instant: Boolean get() = this == OFF

    /** Stagger between neighbouring elements entering together. */
    val staggerMillis: Long
        get() = when (this) {
            OFF -> 0L
            CALM -> 34L
            STANDARD -> 26L
            PLAYFUL -> 20L
        }

    companion object {
        fun from(key: String?): MotionProfile = entries.firstOrNull { it.key == key } ?: STANDARD

        /** Zero animator scale means the user asked the whole system to hold still. */
        fun systemHoldsStill(resolver: ContentResolver): Boolean = runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Scalar helpers used all over the drawing code. */
fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 == edge0) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
