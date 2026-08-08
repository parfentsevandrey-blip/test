package app.quire.calendar.ui

import android.content.ContentResolver
import android.provider.Settings
import android.view.View
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
    CALM("calm", 92f, 1.0f),
    STANDARD("standard", 148f, 0.76f),
    PLAYFUL("playful", 210f, 0.52f),
    ;

    val instant: Boolean get() = this == OFF

    /** Stagger between neighbouring elements entering together. */
    val staggerMillis: Long
        get() = when (this) {
            OFF -> 0L
            CALM -> 52L
            STANDARD -> 44L
            PLAYFUL -> 36L
        }

    companion object {
        fun from(key: String?): MotionProfile = entries.firstOrNull { it.key == key } ?: STANDARD

        /**
         * Zero animator scale means the system has been asked to hold still —
         * used to pick the *initial* profile and nothing else. Forcing it on
         * every launch would leave someone who turned animations down for speed
         * with an app that can never animate, and no setting that overrides it.
         */
        fun systemDefault(resolver: ContentResolver): MotionProfile = runCatching {
            if (Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            ) {
                OFF
            } else {
                STANDARD
            }
        }.getOrDefault(STANDARD)
    }
}

/** Scalar helpers used all over the drawing code. */
fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 == edge0) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * A frame loop of our own.
 *
 * The platform animators — ValueAnimator, ViewPropertyAnimator — are scaled by
 * Settings.Global.ANIMATOR_DURATION_SCALE, so on a phone with animations turned
 * down, or simply in battery saver, every one of them completes instantly. This
 * app's motion is its interface, not decoration on top of it, so it is driven
 * from postOnAnimation and a nanosecond clock instead, which nothing can scale
 * to zero.
 */
class Ticker(private val view: View, private val onFrame: (Float) -> Boolean) {

    private var last = 0L
    private var running = false

    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
            last = now
            if (onFrame(dt.coerceIn(0f, 0.064f))) {
                view.postOnAnimation(this)
            } else {
                running = false
                last = 0L
            }
        }
    }

    fun kick() {
        if (running) return
        running = true
        last = 0L
        view.postOnAnimation(step)
    }

    fun stop() {
        running = false
        last = 0L
        view.removeCallbacks(step)
    }
}
