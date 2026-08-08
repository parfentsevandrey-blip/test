package app.quire.engine.anim

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The largest step any integrator here will take from one call. A dropped frame otherwise hands
 * a stiff integrator a step big enough to diverge instead of settle; motion falls behind real
 * time by the overflow, which is invisible, where a diverging spring is not.
 */
internal const val MAX_STEP_SECONDS: Float = 0.064f

/** Fixed substep for the integrators, small enough that the stiffest profile stays stable. */
internal const val SUB_STEP_SECONDS: Float = 0.004f

/** Ceiling on substeps per call, so a long step costs bounded work. */
internal const val MAX_SUB_STEPS: Int = 16

/**
 * Anything the frame clock can push forward: one value, its own integrator, its own rest state.
 */
interface Animatable {

    /** Moves the value on by [dt] seconds; returns false once there is nothing left to do. */
    fun advance(dt: Float): Boolean

    /** Whether this has settled, so a host can stop asking the clock for frames. */
    val atRest: Boolean
}

/**
 * Every moving value in the app, so that an interrupted gesture continues instead of snapping.
 *
 * A duration-and-easing animation has to restart from zero when its target changes mid-flight,
 * which is exactly what a flick between months does. A spring carries its velocity across the
 * change, so the new motion begins from the speed the finger left behind.
 */
class Spring(value: Float = 0f) : Animatable {

    /** Where the value is now — read this every frame; write it only to teleport. */
    var value: Float = value

    /** Where the value is headed; changing it mid-flight keeps the current [velocity]. */
    var target: Float = value

    /** Current speed in value units per second, seeded by a fling or carried across retargets. */
    var velocity: Float = 0f

    /** Pull toward [target]; higher is faster and tighter. */
    var stiffness: Float = MotionProfile.STANDARD.stiffness

    /** 1 settles without overshoot, below 1 overshoots and returns, above 1 crawls in. */
    var dampingRatio: Float = MotionProfile.STANDARD.dampingRatio

    // Set by profile(MotionProfile.OFF) and cleared by any other profile: reduced motion is a
    // contract about arriving now, not a very stiff spring that still takes a few frames.
    private var instant: Boolean = false

    override val atRest: Boolean
        get() = abs(value - target) < REST_DISTANCE && abs(velocity) < REST_VELOCITY

    /** Puts the value somewhere with no motion at all, for seeding state before it is shown. */
    fun snapTo(v: Float) {
        value = v
        target = v
        velocity = 0f
    }

    /** Adopts a named liveliness, which is how the app's motion setting reaches every value. */
    fun profile(p: MotionProfile) {
        stiffness = p.stiffness
        dampingRatio = p.dampingRatio
        instant = p.instant
        if (instant) {
            value = target
            velocity = 0f
        }
    }

    override fun advance(dt: Float): Boolean {
        if (instant || atRest) {
            value = target
            velocity = 0f
            return false
        }
        val clamped = clamp(dt, 0f, MAX_STEP_SECONDS)
        val steps = ceil(clamped / SUB_STEP_SECONDS).toInt().coerceIn(1, MAX_SUB_STEPS)
        val h = clamped / steps
        val k = stiffness
        val c = 2f * dampingRatio * sqrt(stiffness)
        repeat(steps) {
            velocity += (-k * (value - target) - c * velocity) * h
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
 * Friction-only motion for flings: no target, just velocity bleeding off.
 *
 * Thresholds and bounds are read in whatever unit the value carries — pixels for a pan, a
 * fraction for a zoom — so [min] and [max] must be set in that same unit.
 */
class Decay(value: Float = 0f) : Animatable {

    /** Where the value is now. */
    var value: Float = value

    /** Speed in value units per second; set this from a gesture's release velocity. */
    var velocity: Float = 0f

    /** Exponential rate the speed decays at; 4.2 is a fling that runs for about a second. */
    var friction: Float = 4.2f

    /** Lower bound; travel past it is rubber-banded and pulled back. Unbounded by default. */
    var min: Float = Float.NEGATIVE_INFINITY

    /** Upper bound; travel past it is rubber-banded and pulled back. Unbounded by default. */
    var max: Float = Float.POSITIVE_INFINITY

    override val atRest: Boolean
        get() = abs(velocity) < REST_VELOCITY && abs(overshoot()) < REST_DISTANCE

    /** Drops the value somewhere and kills the fling, for seeding state before it is shown. */
    fun snapTo(v: Float) {
        value = v
        velocity = 0f
    }

    override fun advance(dt: Float): Boolean {
        if (atRest) {
            settle()
            return false
        }
        val clamped = clamp(dt, 0f, MAX_STEP_SECONDS)
        val steps = ceil(clamped / SUB_STEP_SECONDS).toInt().coerceIn(1, MAX_SUB_STEPS)
        val h = clamped / steps
        repeat(steps) { step(h) }
        if (atRest) {
            settle()
            return false
        }
        return true
    }

    private fun step(h: Float) {
        val over = overshoot()
        if (over != 0f) {
            // Out of bounds the fling is over; what is left is a slightly over-damped pull back
            // to the edge, which is what makes an overrun feel like it is being resisted.
            velocity += (-RUBBER_STIFFNESS * over - rubberDamping * velocity) * h
            value += velocity * h
        } else if (friction > 0f) {
            // Closed form over the substep, so the travel does not depend on the step size.
            val remaining = exp(-friction * h)
            value += velocity * (1f - remaining) / friction
            velocity *= remaining
        } else {
            value += velocity * h
        }
    }

    private fun overshoot(): Float = when {
        value < min -> value - min
        value > max -> value - max
        else -> 0f
    }

    private fun settle() {
        value = clamp(value, min, max)
        velocity = 0f
    }

    private companion object {
        const val REST_DISTANCE = 0.0008f
        const val REST_VELOCITY = 0.05f
        const val RUBBER_STIFFNESS = 260f
        val rubberDamping = 2f * 1.15f * sqrt(RUBBER_STIFFNESS)
    }
}

/**
 * A value that walks a list of keyframes in its own time, for scripted sequences — the parts of
 * the interface that play rather than respond, where a spring has no target to chase.
 */
class Track(vararg keys: Pair<Float, Float>) : Animatable {

    private val times: FloatArray
    private val points: FloatArray

    /** Where the value is now; the next [advance] or [seek] overwrites it. */
    var value: Float = 0f

    private var time: Float = 0f

    init {
        val sorted = keys.sortedBy { it.first }
        times = FloatArray(sorted.size)
        points = FloatArray(sorted.size)
        for (i in sorted.indices) {
            times[i] = sorted[i].first
            points[i] = sorted[i].second
        }
        if (points.isNotEmpty()) value = points[0]
    }

    /** How long the whole sequence lasts, for a host scheduling anything around it. */
    val duration: Float
        get() = if (times.isEmpty()) 0f else times[times.size - 1]

    override val atRest: Boolean
        get() = times.isEmpty() || time >= duration

    /** Plays the sequence again from its first keyframe. */
    fun restart() {
        seek(0f)
    }

    /** Jumps to [t] seconds into the sequence, for scrubbing or for starting part way in. */
    fun seek(t: Float) {
        time = clamp(t, 0f, duration)
        sample()
    }

    override fun advance(dt: Float): Boolean {
        if (times.isEmpty()) return false
        if (time >= duration) {
            sample()
            return false
        }
        time = clamp(time + clamp(dt, 0f, MAX_STEP_SECONDS), 0f, duration)
        sample()
        return time < duration
    }

    // Segments are eased rather than linear: straight lines between keyframes read as mechanical
    // at every corner, and a scripted sequence is the one place with no velocity to inherit.
    private fun sample() {
        val n = times.size
        if (n == 0) return
        if (time <= times[0]) {
            value = points[0]
            return
        }
        if (time >= times[n - 1]) {
            value = points[n - 1]
            return
        }
        var i = 0
        while (i < n - 1 && times[i + 1] < time) i++
        val span = times[i + 1] - times[i]
        val t = if (span <= 0f) 1f else (time - times[i]) / span
        value = lerp(points[i], points[i + 1], smoothstep(0f, 1f, t))
    }
}

/**
 * How lively the world feels. `OFF` is not a lesser setting — it is what the app switches to for
 * someone who has asked the whole system to hold still.
 */
enum class MotionProfile(
    val key: String,
    val stiffness: Float,
    val dampingRatio: Float,
) {
    /** No motion: values arrive at their target on the frame they are given it. */
    OFF("off", 1000f, 1f),

    /** Slow and settled, no overshoot. */
    CALM("calm", 92f, 1.0f),

    /** The default: quick, with a trace of overshoot. */
    STANDARD("standard", 148f, 0.76f),

    /** Fast and springy, overshoots visibly. */
    PLAYFUL("playful", 210f, 0.52f),
    ;

    /** Whether motion should be skipped entirely rather than merely hurried. */
    val instant: Boolean
        get() = this == OFF

    /** Delay between neighbouring elements entering together. */
    val staggerSeconds: Float
        get() = when (this) {
            OFF -> 0f
            CALM -> 0.052f
            STANDARD -> 0.044f
            PLAYFUL -> 0.036f
        }

    companion object {

        /** Reads a stored or unknown preference back into a profile, defaulting to STANDARD. */
        fun from(key: String?): MotionProfile {
            if (key == null) return STANDARD
            var i = 0
            val all = entries
            while (i < all.size) {
                if (all[i].key == key) return all[i]
                i++
            }
            return STANDARD
        }
    }
}

/** Straight interpolation, the workhorse of every layout that lives between two states. */
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Eased 0..1 ramp across a range, for fades and reveals that must not start or stop abruptly. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 == edge0) return if (x < edge0) 0f else 1f
    val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Bounds a value without throwing when the bounds arrive crossed, unlike coerceIn. */
fun clamp(v: Float, lo: Float, hi: Float): Float = when {
    v < lo -> lo
    v > hi -> hi
    else -> v
}
