package app.quire.engine.fx

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorInt
import kotlin.math.cos
import kotlin.math.sin

/**
 * A pooled particle system. Used for confirmation bursts when something is chosen: a handful of
 * sparks in the accent colour that arc out, drag to a stop and fade.
 *
 * Every attribute is its own primitive array and the live particles are packed at the front, so
 * a frame walks contiguous memory and allocates nothing. A particle that dies is filled in by
 * the last live one, which reorders the pool but keeps it dense. Lengths are in pixels and
 * seconds, matching the canvas the caller draws into.
 *
 * @param capacity the most particles alive at once; emitting past it recycles, never grows.
 */
class Particles(capacity: Int = 256) {

    private val pool: Int = capacity.coerceIn(1, MAX_CAPACITY)

    private val posX = FloatArray(pool)
    private val posY = FloatArray(pool)
    private val velX = FloatArray(pool)
    private val velY = FloatArray(pool)
    private val lifeLeft = FloatArray(pool)
    private val lifeSpan = FloatArray(pool)
    private val radius = FloatArray(pool)
    private val dragK = FloatArray(pool)
    private val pullK = FloatArray(pool)
    private val tint = IntArray(pool)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var live = 0
    private var cursor = 0

    // Seeded once, deterministically: two identical bursts should look identical, and a run
    // that misbehaves should misbehave the same way twice.
    private var rng: Int = SEED

    /** How many particles are still on screen, for a host deciding whether to keep drawing. */
    val alive: Int
        get() = live

    /**
     * Emits [count] particles from a point with an outward spread. [speed] is the middle of the
     * launch speed in pixels per second and also sets how large the sparks are, since it is the
     * only scale the call carries. The alpha of [colour] scales the whole particle.
     */
    fun burst(
        x: Float,
        y: Float,
        count: Int,
        @ColorInt colour: Int,
        speed: Float,
        spread: Float = 6.283f,
    ) {
        if (count <= 0) return
        val emitted = count.coerceAtMost(pool)
        val arc = if (spread > 0f) spread else 0f
        val base = nextUnit() * TAU
        val scale = if (speed > 0f) speed else 1f
        var i = 0
        while (i < emitted) {
            // Angles are spaced across the arc and only jittered within their own slot: drawing
            // each one at random clumps them, and the burst reads as a smear instead of a star.
            val slot = (i + 0.5f) / emitted
            val jitter = (nextUnit() - 0.5f) * arc / emitted
            val angle = base + (slot - 0.5f) * arc + jitter
            val launch = scale * (0.55f + 0.75f * nextUnit())
            emit(
                x = x,
                y = y,
                vx = cos(angle) * launch,
                vy = sin(angle) * launch,
                span = BURST_LIFE + BURST_LIFE_SPAN * nextUnit(),
                size = (SPARK_BASE + scale * SPARK_PER_SPEED) * (0.7f + 0.6f * nextUnit()),
                drag = BURST_DRAG + BURST_DRAG_SPAN * nextUnit(),
                pull = 1f,
                colour = colour,
            )
            i++
        }
    }

    /** Emits a soft trail particle, for dragging. */
    fun trail(x: Float, y: Float, @ColorInt colour: Int) {
        emit(
            x = x,
            y = y,
            vx = (nextUnit() - 0.5f) * TRAIL_DRIFT,
            vy = (nextUnit() - 0.5f) * TRAIL_DRIFT,
            span = TRAIL_LIFE + TRAIL_LIFE_SPAN * nextUnit(),
            size = TRAIL_SIZE * (0.75f + 0.5f * nextUnit()),
            drag = TRAIL_DRAG,
            // A trail marks where a finger has been, so it hangs in place rather than falling.
            pull = 0.06f,
            colour = colour,
        )
    }

    /** Steps every live particle by [dt] seconds; false when nothing is alive. */
    fun advance(dt: Float): Boolean {
        if (live == 0) return false
        // Positive test, so a NaN or a stalled clock leaves the system standing rather than
        // poisoning every position with it.
        if (!(dt > 0f)) return true
        // A long step (a dropped frame, a resumed activity) would otherwise throw the sparks
        // off screen in one jump, so it is spent rather than obeyed.
        val step = dt.coerceAtMost(MAX_STEP)
        var i = 0
        while (i < live) {
            val remaining = lifeLeft[i] - step
            if (remaining <= 0f) {
                recycle(i)
                // The particle swapped into this slot has not moved yet, so i stays put.
                continue
            }
            lifeLeft[i] = remaining
            velY[i] += GRAVITY * pullK[i] * step
            // Drag as a rational decay rather than an exponential: it costs a divide instead
            // of a call, and it cannot overshoot into a reversed velocity at any step size.
            val damp = 1f / (1f + dragK[i] * step)
            velX[i] *= damp
            velY[i] *= damp
            posX[i] += velX[i] * step
            posY[i] += velY[i] * step
            i++
        }
        return live > 0
    }

    /** Paints every live particle, scaled by [alpha] so a host can fade the whole system out. */
    fun draw(canvas: Canvas, alpha: Float = 1f) {
        if (live == 0) return
        val master = alpha.coerceIn(0f, 1f)
        if (!(master > 0f)) return
        var i = 0
        while (i < live) {
            val remain = lifeLeft[i] / lifeSpan[i]
            val fade = remain * remain * (3f - 2f * remain)
            // A spark that appears at full strength pops; the first eighth of its life arrives.
            val rise = ((1f - remain) * 8f).coerceAtMost(1f)
            val source = ((tint[i] ushr 24) and 0xFF).toFloat()
            val core = (master * fade * rise * source + 0.5f).toInt().coerceIn(0, 255)
            if (core > 0) {
                val rgb = tint[i] and 0x00FFFFFF
                val r = radius[i] * (0.55f + 0.45f * fade)
                val halo = (core * HALO_ALPHA).toInt().coerceIn(0, 255)
                // Two flat circles instead of a blur: a halo under a core reads as soft light
                // and costs no mask filter, which would rasterise off screen every call.
                if (halo > 0) {
                    paint.color = rgb or (halo shl 24)
                    canvas.drawCircle(posX[i], posY[i], r * HALO_SCALE, paint)
                }
                paint.color = rgb or (core shl 24)
                canvas.drawCircle(posX[i], posY[i], r, paint)
            }
            i++
        }
    }

    /** Drops every particle at once, for a screen that is going away mid-burst. */
    fun clear() {
        live = 0
        cursor = 0
    }

    private fun emit(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        span: Float,
        size: Float,
        drag: Float,
        pull: Float,
        colour: Int,
    ) {
        val at = slot()
        posX[at] = x
        posY[at] = y
        velX[at] = vx
        velY[at] = vy
        lifeSpan[at] = span
        lifeLeft[at] = span
        radius[at] = size
        dragK[at] = drag
        pullK[at] = pull
        tint[at] = colour
    }

    private fun slot(): Int {
        if (live < pool) {
            val at = live
            live++
            return at
        }
        // Full. Overwriting in rotation loses an arbitrary particle; dropping the new one
        // instead would make a burst vanish exactly when the screen is busiest.
        cursor++
        if (cursor >= pool) cursor = 0
        return cursor
    }

    private fun recycle(index: Int) {
        val last = live - 1
        if (index != last) {
            posX[index] = posX[last]
            posY[index] = posY[last]
            velX[index] = velX[last]
            velY[index] = velY[last]
            lifeLeft[index] = lifeLeft[last]
            lifeSpan[index] = lifeSpan[last]
            radius[index] = radius[last]
            dragK[index] = dragK[last]
            pullK[index] = pullK[last]
            tint[index] = tint[last]
        }
        live = last
    }

    // Xorshift32: three shifts and three xors, no object, no lock, and a period long enough
    // that a burst never repeats a pattern a person could notice.
    private fun nextUnit(): Float {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return (x ushr 8) * INV_24_BIT
    }

    private companion object {
        const val MAX_CAPACITY = 4096
        const val SEED = 0x9E3779B9.toInt()
        const val INV_24_BIT = 1f / 16777216f
        const val TAU = 6.2831855f
        const val MAX_STEP = 0.05f
        const val GRAVITY = 780f

        const val BURST_LIFE = 0.42f
        const val BURST_LIFE_SPAN = 0.5f
        const val BURST_DRAG = 2.4f
        const val BURST_DRAG_SPAN = 2.2f
        const val SPARK_BASE = 1.8f
        const val SPARK_PER_SPEED = 0.005f

        const val TRAIL_LIFE = 0.45f
        const val TRAIL_LIFE_SPAN = 0.3f
        const val TRAIL_DRIFT = 26f
        const val TRAIL_DRAG = 6f
        const val TRAIL_SIZE = 6.5f

        const val HALO_SCALE = 2.4f
        const val HALO_ALPHA = 0.32f
    }
}
