package app.quire.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * The current minute, and it keeps being the current minute.
 *
 * Two places on the weather screen used to hold `remember { LocalDateTime.now() }`, which freezes
 * at first composition and never moves again: the sun's position on its arc, and which column of
 * the hour strip is "Now". Neither is decoration. The sun's position *is* the reading — an arc
 * that answers "where the day was when you opened the screen" is a graph telling a lie — and a
 * strip that marks the wrong hour as now is worse than one that marks none.
 *
 * It ticks on the minute boundary rather than on an interval, so the marker moves when the clock
 * a person is looking at moves, and the first tick after opening the screen is however long is
 * actually left of this minute. A second is the floor, because a delay of nought would spin.
 *
 * `delay` rather than `withFrameNanos` on purpose: this needs sixty ticks an hour, not sixty a
 * second, and a frame-clock loop would keep the composition from ever going idle — which is what
 * makes SkyPulse untestable without a paused clock. Under a test clock the delay is driven by
 * the harness like any other suspending wait.
 */
@Composable
internal fun rememberMinute(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        val into = now.second * 1_000L + now.nano / 1_000_000L
        delay((60_000L - into).coerceAtLeast(1_000L))
    }
}
