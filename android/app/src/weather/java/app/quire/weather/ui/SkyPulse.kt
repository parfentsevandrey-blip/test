package app.quire.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.quire.weather.Sky

/**
 * Weather you can feel: every falling sky lands in the hand, and each lands its own way.
 *
 * Rain is the lightest tick the platform has, at irregular intervals — rain on a roof does not
 * keep a beat, and a metronome in the hand would be a notification, not weather. The gap between
 * ticks rides the minute-cast's actual millimetres, so a drizzle is an occasional tap and a
 * downpour is a patter. Snow is the same idea said more quietly: a softer touch, seconds apart,
 * because snow lands and rain hits. Sleet is rain with the odd harder knock in it — the ice —
 * on a deterministic scatter, so the same sleet knocks the same rhythm. And thunder rumbles:
 * when the sky's shared clock crosses a strike — the exact beat the bolt is drawn — the hand
 * gets one hard knock and two echoes rolling off, which is the shape thunder actually has.
 *
 * Dry skies get nothing. Fog gets nothing on purpose: fog is the weather that makes no sound,
 * and a haptic for it would be an ornament — the taste rule is the same in the hand as on the
 * screen, feedback only where it is information.
 *
 * The loop is paced by the frame clock rather than by wall time, and that one choice does three
 * jobs: the taps stop the moment the app leaves the screen (a backgrounded app gets no frames,
 * and weather tapping a pocket is a bug, not a feature); they stop under battery saver and
 * animations-off exactly when the sky stops; and a test can drive the whole thing on the paused
 * clock and simply count.
 */
@Composable
internal fun SkyPulse(
    sky: Sky,
    active: Boolean,
    intensity: Float,
    /** The sky's own lap, from [rememberSkyClock]; null forgoes the thunder's rumble. */
    clock: State<Float>? = null,
    tap: ((HapticFeedbackType) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val level = rememberUpdatedState(intensity)
    LaunchedEffect(active, sky) {
        if (!active) return@LaunchedEffect
        val land: (HapticFeedbackType) -> Unit =
            tap ?: { touch -> haptics.performHapticFeedback(touch) }
        var index = 0
        var wait = fallGap(sky, index, level.value)
        var last = 0L
        var lap = clock?.value ?: -1f
        // The echoes of a strike still on their way to the hand: milliseconds left, then what.
        val echoes = ArrayList<Pair<Long, HapticFeedbackType>>(RUMBLE.size)
        while (true) {
            var step = 0L
            withFrameNanos { now ->
                if (last != 0L) step = (now - last) / 1_000_000
                last = now
            }

            wait -= step
            if (wait <= 0) {
                land(fallTouch(sky, index))
                index++
                wait = fallGap(sky, index, level.value)
            }

            // The rumble rides the same lap the bolts draw by: the knock lands on the flash.
            if (sky == Sky.THUNDER && clock != null) {
                val turned = clock.value
                if (lap >= 0f && struck(lap, turned)) echoes.addAll(RUMBLE)
                lap = turned
            }
            if (echoes.isNotEmpty()) {
                val due = echoes.listIterator()
                while (due.hasNext()) {
                    val (left, touch) = due.next()
                    val remain = left - step
                    if (remain <= 0) {
                        land(touch)
                        due.remove()
                    } else {
                        due.set(remain to touch)
                    }
                }
            }
        }
    }
}

/** The touch this sky lands on the [index]th fall: rain hits, snow lands, sleet knocks. */
internal fun fallTouch(sky: Sky, index: Int): HapticFeedbackType = when {
    sky == Sky.SNOW -> HapticFeedbackType.TextHandleMove
    sky == Sky.SLEET && pellet(index) -> HapticFeedbackType.SegmentTick
    else -> HapticFeedbackType.SegmentFrequentTick
}

/**
 * Whether the [index]th fall of sleet is the ice — the odd harder knock among the taps,
 * by the same scatter hash the sky falls by, so the same sleet knocks the same rhythm.
 */
internal fun pellet(index: Int): Boolean = scatter(index, 53) > 0.72f

/** The silence before the [index]th fall, by this sky's own metre. */
internal fun fallGap(sky: Sky, index: Int, intensity: Float): Long =
    if (sky == Sky.SNOW) snowGap(index, intensity) else tapGap(index, intensity)

/**
 * The silence before the [index]th tap of rain, in milliseconds.
 *
 * Deterministic — the same rain taps the same rhythm — and irregular by the same scatter hash
 * the sky falls by. Hard rain closes the gaps; nothing ever comes faster than a tenth of a
 * second, because the point is weather in the hand, not a phone buzzing.
 */
internal fun tapGap(index: Int, intensity: Float): Long {
    val base = 720f - 540f * intensity.coerceIn(0f, 1f)
    val jitter = 0.5f + 1.1f * scatter(index, 41)
    return (base * jitter).toLong().coerceAtLeast(100L)
}

/**
 * The silence before the [index]th flake, seconds long where rain's is fractions of one:
 * snow is the sky at its quietest, and the hand should have to notice it, not be told.
 */
internal fun snowGap(index: Int, intensity: Float): Long {
    val base = 2600f - 1400f * intensity.coerceIn(0f, 1f)
    val jitter = 0.5f + 1.1f * scatter(index, 47)
    return (base * jitter).toLong().coerceAtLeast(400L)
}

/**
 * The thunder, as the hand gets it: one hard knock and two echoes rolling off, each pair a
 * delay from the strike and what lands then. The shape matters — thunder is a front and a
 * tail, and three equal buzzes would be a phone, not a storm.
 */
internal val RUMBLE = listOf(
    0L to HapticFeedbackType.LongPress,
    90L to HapticFeedbackType.ContextClick,
    210L to HapticFeedbackType.SegmentTick,
)

/**
 * Whether the lap crossed a strike between two readings of the clock.
 *
 * The clock loops, so a reading can come back smaller than the one before it — the seam — and
 * a strike sitting across the seam still counts. A clock standing still never strikes, which
 * is battery saver and animations-off silencing the thunder along with everything else.
 */
internal fun struck(before: Float, after: Float): Boolean {
    if (before == after) return false
    return STRIKE_AT.any { at ->
        if (after >= before) at > before && at <= after else at > before || at <= after
    }
}
