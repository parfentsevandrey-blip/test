package app.opal.core.designsystem.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule

/**
 * The wide floating action in the thumb zone (see [BottomAccessory]). Its glass changes character
 * with the action — tinted with the accent when it starts something, clear when it stops it — and
 * squashes like a drop of liquid whenever the action flips. [progress] draws a thin ring around the
 * icon (0f..1f; 0f spins while the amount is still unknown).
 */
@Composable
fun GlassActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    progress: Float? = null,
    stateDescription: String? = null,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val tint by
        animateColorAsState(
            if (emphasized) colors.accent.copy(alpha = EMPHASIS_ALPHA) else Color.Transparent,
            spring(stiffness = 300f),
            label = "actionTint",
        )
    val content by
        animateColorAsState(
            if (emphasized) Color.White else colors.onGlass,
            spring(stiffness = 300f),
            label = "actionContent",
        )
    val squash = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(emphasized) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (reduced) return@LaunchedEffect
        squash.snapTo(1f)
        squash.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 420f))
    }
    GlassSurface(
        shape = Capsule(),
        style = GlassStyle.Bar,
        layer = GlassLayer.Floating,
        tint = tint,
        interactive = enabled,
        layerBlock = {
            // Wider and flatter for an instant, then back: the "liquid" flip.
            val s = squash.value * SQUASH
            scaleX = 1f + s
            scaleY = 1f - s * 1.6f
        },
        modifier =
            modifier
                .height(ACTION_HEIGHT)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { stateDescription?.let { this.stateDescription = it } }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptics?.tap()
                    onClick()
                },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
                if (progress != null) ProgressRing(progress, content, reduced)
                Icon(
                    icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 2 })
                },
                label = "actionLabel",
            ) { text ->
                Text(
                    text,
                    style = OpalTheme.type.subhead,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A round glass button of the same height as [GlassActionButton], for a secondary action next to
 * it. While [busy] a ring spins around the icon (e.g. Tor's pause between two new identities).
 */
@Composable
fun GlassOrbButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    GlassSurface(
        shape = Capsule(),
        style = GlassStyle.Bar,
        layer = GlassLayer.Floating,
        interactive = enabled,
        modifier =
            modifier
                .size(ACTION_HEIGHT)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { this.contentDescription = contentDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptics?.tap()
                    onClick()
                },
    ) {
        if (busy) ProgressRing(0f, colors.onGlass, reduced)
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onGlass,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun ProgressRing(progress: Float, color: Color, reduced: Boolean) {
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ringSweep")
    val spin =
        if (progress <= 0f && !reduced) {
            val transition = rememberInfiniteTransition(label = "ringSpin")
            transition.animateFloat(
                0f,
                360f,
                infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                label = "ringAngle",
            )
        } else {
            null
        }
    Canvas(Modifier.size(RING_SIZE)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke),
        )
        val start = (spin?.value ?: 0f) - 90f
        val arc = if (progress <= 0f) 70f else 360f * sweep
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = arc,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Height of the accessory action; the scaffold reserves room for it above the tab bar. */
val ACTION_HEIGHT = 56.dp
private val RING_SIZE = 34.dp
private const val EMPHASIS_ALPHA = 0.82f
private const val SQUASH = 0.035f
private const val DISABLED_ALPHA = 0.6f
