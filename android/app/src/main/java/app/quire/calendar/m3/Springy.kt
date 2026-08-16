package app.quire.calendar.m3

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
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
@Composable
internal fun Modifier.springPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
