package app.rosa.weather.core.designsystem.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * One unhurried clock for all ambient motion — drifting clouds, turning sun rays, a pulsing dot, a
 * fluttering compass needle. It ticks [FPS] times a second whatever the display's refresh rate, and
 * every ambient element changes on the same frames. On a 120 Hz screen the sky, and each glass
 * surface refracting it, is redrawn four times less often; scrolling and touch still run at the
 * full rate. Read [seconds] in the draw phase only, so a tick never recomposes anything.
 */
@Stable
class AmbientClock internal constructor() {
    internal val time = mutableFloatStateOf(0f)

    /** Seconds since the clock started, in 1/[FPS] s steps; frozen while motion is off. */
    val seconds: Float get() = time.floatValue

    companion object {
        const val FPS = 30
    }
}

/** A frozen clock by default, so previews and tests without an environment stay still. */
val LocalAmbientClock = staticCompositionLocalOf { AmbientClock() }

@Composable
fun rememberAmbientClock(running: Boolean): AmbientClock {
    val clock = remember { AmbientClock() }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val base = clock.time.floatValue
        var start = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (start < 0) start = now
                clock.time.floatValue = base + (now - start) / 1_000_000_000f
            }
            // Sleep through the frames in between; the next tick lands on the first vsync after,
            // which is 33 ms on 60, 90 and 120 Hz displays alike.
            delay(1000L / AmbientClock.FPS - 6)
        }
    }
    return clock
}
