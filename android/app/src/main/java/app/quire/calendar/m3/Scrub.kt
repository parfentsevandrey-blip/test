package app.quire.calendar.m3

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Which of `count` equal columns a point at `x` falls in, counted the way the eye counts them.
 *
 * Split out and made pure because the interesting half of a scrub is arithmetic, and arithmetic
 * can be checked without a finger: the edges, the overshoot past either end, and the mirror. In a
 * right-to-left layout the first item is drawn on the right, so the column under the finger and
 * the index in the list run opposite ways — get that wrong and the bar works backwards for a
 * reader of Arabic or Hebrew, which is the kind of bug nobody testing in English ever sees.
 */
internal fun columnAt(x: Float, width: Float, count: Int, rtl: Boolean): Int {
    if (count <= 0) return 0
    if (width <= 0f) return 0
    // Truncation is wrong on the negative side — (-0.4).toInt() is 0, not -1 — so the coercion
    // happens on the fraction, before it becomes an index.
    val column = ((x / width) * count).toInt().coerceIn(0, count - 1)
    return if (rtl) count - 1 - column else column
}

/**
 * Lets one finger slide along a row of `count` equal items, naming each one as it passes.
 *
 * The point is the lift. A navigation bar asks for a whole press-and-release per screen, and
 * looking at four screens costs four of them; here the finger goes down once and the screens come
 * to it. That is the same bargain as a volume slider — the control is continuous because the
 * hand is — and it is why this is a *scrub* rather than a fling: the item under the thumb is the
 * item chosen, absolutely, so backing up is the same gesture as going on, and there is nothing to
 * undo. A fling would have to guess how far you meant to go, and a guess is a thing to correct.
 *
 * Taps are untouched, and that is not a hope but a threshold. Nothing here consumes anything
 * until the finger has travelled the platform's own horizontal touch slop, which is the distance
 * Android itself uses to tell a press from a drag; below it the events pass to the items as
 * before. Above it the drag consumes them, which is exactly what tells a pressed item its press
 * was cancelled — so a scrub that starts on the calendar item never also fires the calendar
 * item's own click on the way past.
 *
 * One tick per crossing, not per frame: the finger moves continuously but the answer does not,
 * and the hand should be told about the answer. This is the same `SegmentTick` the month pager
 * and the segmented rows use, so a boundary crossed feels the same everywhere in the app.
 */
@Composable
internal fun Modifier.scrubbable(
    enabled: Boolean,
    count: Int,
    landedOn: () -> Int,
    onScrub: (Int) -> Unit,
): Modifier {
    if (!enabled || count <= 1) return this
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this.pointerInput(count, rtl) {
        awaitEachGesture {
            // `requireUnconsumed = false`, because the item under the finger has already seen
            // this down and marked it as its own press. That press is welcome to stand; it is
            // only if the finger then travels that this takes the gesture off it.
            val down = awaitFirstDown(requireUnconsumed = false)
            var landed = landedOn()

            val crossed = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
            } ?: return@awaitEachGesture

            fun visit(x: Float) {
                val next = columnAt(x, size.width.toFloat(), count, rtl)
                if (next != landed) {
                    landed = next
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onScrub(next)
                }
            }

            // The crossing itself is already a position worth reading: by the time slop is met
            // the finger may well have arrived somewhere new.
            visit(crossed.position.x)
            horizontalDrag(crossed.id) { change ->
                visit(change.position.x)
                change.consume()
            }
        }
    }
}
