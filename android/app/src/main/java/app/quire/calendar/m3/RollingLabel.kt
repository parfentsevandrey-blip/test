package app.quire.calendar.m3

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

/**
 * A label that travels when it changes, in whichever direction its subject moved.
 *
 * [order] is what makes the direction meaningful rather than arbitrary — a later month rolls up,
 * an earlier one rolls down — and it is passed separately because the text itself cannot be
 * compared: "August" is not after "July" in any ordering a string knows about. A subject with no
 * order — a place name — passes a constant and always rolls up, which reads as "new", and that
 * is the truth about it.
 *
 * Shared between the two apps: the calendar rolls its months and the weather rolls its places,
 * and a person who has used one should recognise the other.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RollingLabel(text: String, order: Int) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val quick = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = text to order,
        transitionSpec = {
            val forward = targetState.second >= initialState.second
            (
                slideInVertically(spatial) { height -> if (forward) height else -height } +
                    fadeIn(quick)
                ) togetherWith (
                slideOutVertically(spatial) { height -> if (forward) -height else height } +
                    fadeOut(quick)
                ) using SizeTransform(clip = false)
        },
        label = "label",
    ) { (shown, _) ->
        Text(shown)
    }
}
