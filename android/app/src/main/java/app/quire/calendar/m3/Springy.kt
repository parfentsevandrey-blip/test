package app.quire.calendar.m3

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The press a finger can feel through the glass.
 *
 * A card that dips slightly under a touch and springs back on release is the difference between
 * pressing a picture of a card and pressing a card. The dip is two per cent — enough for the hand
 * to notice, not enough for the eye to call it an animation — and the return is the theme's own
 * expressive spring, so it overshoots by a hair on the way back the way a physical thing would.
 *
 * It reads the interaction source the component already has, so the ripple and the dip are the
 * same gesture rather than two effects that happen to start together.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.springPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        // Two specs, not one, and the asymmetry is the whole point.
        //
        // Going down, the finger is on the card and the theme's fast spring answers it. Coming
        // back it must NOT overshoot, because an overshoot is a claim about inertia and a tap
        // has none: the card was bloomed a hair past its resting size after every single tap,
        // reporting a flick nobody performed. That is worse than plain decoration — it is a
        // miscalibrated signal, spending the loudness budget where there is nothing to say.
        // Material already forbids overshoot on colour for exactly this reason.
        //
        // Where a finger genuinely does hand over inertia — a fling through the pager, a
        // pull-to-refresh let go — the spatial specs still overshoot, and should.
        animationSpec = if (pressed) {
            MaterialTheme.motionScheme.fastSpatialSpec()
        } else {
            tapReturnSpec()
        },
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The spring a surface returns on when the gesture that moved it carried no inertia.
 *
 * Kept here so the rule lives in one file and an audit is a grep rather than a reading: anything
 * springing back from a tap uses this, and anything springing back from a throw does not.
 */
internal fun <T> tapReturnSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    androidx.compose.animation.core.spring(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
    )
