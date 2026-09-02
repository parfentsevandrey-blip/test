package app.veil.vpn.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import app.veil.vpn.R
import app.veil.vpn.model.TunnelState

/**
 * The one control that matters.
 *
 * Expressive design asks shape and motion to carry state rather than a label,
 * so the button is a live morph between two polygons: still and many-sided when
 * idle, spinning while the app is working its way out, and settling into a
 * near-circle once traffic is flowing. The bootstrap percentage is drawn as an
 * arc around it, because during a slow Snowflake connect that number is the
 * only thing telling the user the app has not hung.
 */
@Composable
fun ConnectButton(
    state: TunnelState,
    bootstrapPercent: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 232.dp,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val shapes = remember { ButtonShapes() }
    val target = when {
        state.isLive -> shapes.connected
        state.isBusy -> shapes.working
        state is TunnelState.Failed -> shapes.failed
        else -> shapes.idle
    }
    val morph = remember(target) { Morph(shapes.idle, target) }

    // How far along the morph we are: the resting silhouette for this state.
    val settle by animateFloatAsState(
        targetValue = if (state is TunnelState.Idle) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "settle",
    )

    val transition = rememberInfiniteTransition(label = "connect")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "spin",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse),
        label = "breathe",
    )

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            state.isLive -> breathe
            state.isBusy -> 1f
            else -> 1f
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "scale",
    )

    val container by animateColorAsState(
        targetValue = when {
            state.isLive -> MaterialTheme.colorScheme.primary
            state is TunnelState.Escalating -> MaterialTheme.colorScheme.tertiary
            state.isBusy -> MaterialTheme.colorScheme.primaryContainer
            state is TunnelState.Failed -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "container",
    )
    val accent = when {
        state.isLive -> MaterialTheme.colorScheme.onPrimary
        state is TunnelState.Failed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    val description = when {
        state.isLive -> stringResource(R.string.a11y_connected)
        state.isBusy -> stringResource(R.string.a11y_connecting)
        else -> stringResource(R.string.a11y_idle)
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = size / 2),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val path = remember { android.graphics.Path() }
        val matrix = remember { android.graphics.Matrix() }
        val bounds = remember { android.graphics.RectF() }

        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val shapeRadius = radius * 0.78f * scale

            // The silhouette itself.
            path.rewind()
            morph.toPath(settle, path)
            path.computeBounds(bounds, true)
            val extent = maxOf(bounds.width(), bounds.height()).takeIf { it > 0f } ?: 1f
            matrix.reset()
            matrix.setTranslate(-bounds.centerX(), -bounds.centerY())
            matrix.postScale(2f * shapeRadius / extent, 2f * shapeRadius / extent)
            matrix.postTranslate(center.x, center.y)
            path.transform(matrix)

            val spinning = if (state.isBusy) spin else spin * 0.08f
            rotate(spinning) {
                drawPath(
                    path = path.asComposePath(),
                    brush = Brush.linearGradient(
                        colors = listOf(container, container.copy(alpha = 0.82f)),
                        start = Offset(0f, 0f),
                        end = Offset(this.size.width, this.size.height),
                    ),
                )
            }

            // Progress ring: a track plus the bootstrap arc.
            val ringStroke = radius * 0.055f
            val inset = ringStroke
            drawCircle(
                color = trackColor.copy(alpha = 0.45f),
                radius = radius - inset,
                style = Stroke(width = ringStroke),
            )
            if (state.isBusy && bootstrapPercent > 0) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * (bootstrapPercent.coerceIn(0, 100) / 100f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        this.size.width - inset * 2,
                        this.size.height - inset * 2,
                    ),
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round),
                )
            } else if (state.isLive) {
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = radius - inset,
                    style = Stroke(width = ringStroke),
                )
            }
        }

        ConnectButtonLabel(state = state, bootstrapPercent = bootstrapPercent, tint = accent)
    }
}

/**
 * The polygons the button morphs between. Held in one place so the silhouettes
 * stay a deliberate set rather than four unrelated shapes.
 */
private class ButtonShapes {
    /** Many-sided and still: something waiting to be used. */
    val idle: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 12,
        innerRadius = 0.86f,
        rounding = CornerRounding(0.28f),
    )

    /** Fewer, sharper points, so the spin reads clearly. */
    val working: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 8,
        innerRadius = 0.68f,
        rounding = CornerRounding(0.22f),
    )

    /** Settled: almost a circle, the calmest shape in the set. */
    val connected: RoundedPolygon = RoundedPolygon.circle(numVertices = 12)

    /** Lopsided on purpose: something is wrong and it should look wrong. */
    val failed: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 5,
        innerRadius = 0.52f,
        rounding = CornerRounding(0.12f),
    )
}

/**
 * Text inside the button. Kept to two lines: a verb and, while connecting, the
 * one number that says progress is being made.
 */
@Composable
private fun ConnectButtonLabel(state: TunnelState, bootstrapPercent: Int, tint: Color) {
    val headline = when (state) {
        is TunnelState.Idle -> stringResource(R.string.action_connect)
        is TunnelState.Connected -> stringResource(R.string.action_disconnect)
        is TunnelState.Failed -> stringResource(R.string.action_retry)
        else -> stringResource(R.string.action_cancel)
    }
    val detail = when (state) {
        is TunnelState.Probing -> stringResource(state.noteRes)
        is TunnelState.Starting -> stringResource(state.transport.labelRes)
        is TunnelState.Bootstrapping -> "$bootstrapPercent%"
        is TunnelState.Escalating -> stringResource(state.to.labelRes)
        is TunnelState.Connected -> stringResource(state.transport.labelRes)
        else -> null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = tint,
        )
        AnimatedVisibility(visible = detail != null) {
            Text(
                text = detail.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = tint.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
