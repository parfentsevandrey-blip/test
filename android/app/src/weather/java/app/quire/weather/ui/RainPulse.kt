package app.quire.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Rain you can feel: a light tick now and then while it rains, in step with how hard.
 *
 * The lightest tick the platform has, at irregular intervals — rain on a roof does not keep a
 * beat, and a metronome in the hand would be a notification, not weather. The gap between ticks
 * rides the minute-cast's actual millimetres, so a drizzle is an occasional tap and a downpour
 * is a patter.
 *
 * The loop is paced by the frame clock rather than by wall time, and that one choice does three
 * jobs: the taps stop the moment the app leaves the screen (a backgrounded app gets no frames,
 * and weather tapping a pocket is a bug, not a feature); they stop under battery saver and
 * animations-off exactly when the sky stops; and a test can drive the whole thing on the paused
 * clock and simply count.
 */
@Composable
internal fun RainPulse(
    active: Boolean,
    intensity: Float,
    tap: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val level = rememberUpdatedState(intensity)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var index = 0
        var wait = tapGap(index, level.value)
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) wait -= (now - last) / 1_000_000
                last = now
            }
            if (wait <= 0) {
                if (tap != null) {
                    tap()
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                index++
                wait = tapGap(index, level.value)
            }
        }
    }
}

/**
 * The silence before the [index]th tap, in milliseconds.
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
