package app.opal.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import app.opal.core.designsystem.glass.GlassStyle
import app.opal.core.designsystem.glass.GlassSurface
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule

enum class SphereMode {
    /** VPN off (or standby): tap to connect. */
    Idle,
    /** Bootstrapping or recovering. */
    Working,
    /** Connected: the sphere has become the status capsule. */
    Protected,
    /** Blocked or failed. */
    Alert,
}

/**
 * The connection control: a glass sphere that refracts the aurora. While connecting it shows a
 * shape that settles from a soft star into a circle as Tor's bootstrap progresses; once protected
 * it flows (spring on width/height, the capsule shape follows) into a status capsule carrying
 * [capsuleContent].
 *
 * [progress] is Tor's bootstrap progress 0..1, or null when there is no meaningful value
 * (recovering, waiting for network) — then the ring spins instead of lying about progress.
 */
@Composable
fun ConnectionSphere(
    mode: SphereMode,
    progress: Float?,
    actionLabel: String,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sphereSize: Dp = 196.dp,
    capsuleWidth: Dp = 300.dp,
    capsuleHeight: Dp = 76.dp,
    capsuleContent: @Composable RowScope.() -> Unit,
) {
    val colors = OpalTheme.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHaptics.current
    val expanded = mode == SphereMode.Protected
    val morphSpring =
        if (reduced) spring<Dp>(stiffness = Spring.StiffnessHigh) else spring(0.72f, 260f)
    val width by
        animateDpAsState(
            if (expanded) capsuleWidth else sphereSize,
            morphSpring,
            label = "sphereWidth",
        )
    val height by
        animateDpAsState(
            if (expanded) capsuleHeight else sphereSize,
            morphSpring,
            label = "sphereHeight",
        )
    val tint by
        animateColorAsState(
            when (mode) {
                SphereMode.Idle -> Color.Transparent
                SphereMode.Working -> colors.connecting.copy(alpha = 0.10f)
                SphereMode.Protected -> colors.connected.copy(alpha = 0.14f)
                SphereMode.Alert -> colors.error.copy(alpha = 0.12f)
            },
            tween(600),
            label = "sphereTint",
        )
    GlassSurface(
        shape = Capsule(),
        style = GlassStyle.Sphere,
        tint = tint,
        interactive = true,
        modifier =
            modifier
                .size(width, height)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) {
                    haptics?.tap()
                    onClick()
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = actionLabel
                    stateDescription = stateLabel
                },
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f)).togetherWith(
                    fadeOut(tween(90)) + scaleOut(targetScale = 0.92f)
                )
            },
            contentAlignment = Alignment.Center,
            label = "sphereContent",
        ) { isCapsule ->
            if (isCapsule) {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = capsuleContent,
                )
            } else {
                SphereFace(mode, progress, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SphereFace(mode: SphereMode, progress: Float?, modifier: Modifier = Modifier) {
    val colors = OpalTheme.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        when (mode) {
            SphereMode.Working -> {
                ProgressRing(progress, colors.connecting, Modifier.fillMaxSize().padding(12.dp))
                MorphIndicator(progress ?: 0f, colors.connecting, Modifier.size(92.dp))
                if (progress != null) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        style =
                            OpalTheme.type.heroNumeric.copy(
                                fontSize = OpalTheme.type.headline.fontSize
                            ),
                        color = if (colors.isDark) colors.background else Color.White,
                    )
                }
            }
            else -> {
                Icon(
                    OpalIcons.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (mode == SphereMode.Alert) colors.error else colors.onGlass,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

/** Arc of bootstrap progress; spins as an indeterminate segment when [progress] is null. */
@Composable
private fun ProgressRing(progress: Float?, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val animated by
        animateFloatAsState(progress ?: 0f, spring(stiffness = Spring.StiffnessLow), label = "ring")
    val spin =
        if (progress == null && !reduced) {
            rememberInfiniteTransition(label = "ringSpin")
                .animateFloat(
                    0f,
                    360f,
                    infiniteRepeatable(tween(1_400, easing = LinearEasing)),
                    label = "ringAngle",
                )
        } else {
            null
        }
    val track = OpalTheme.colors.onGlassMuted.copy(alpha = 0.22f)
    Canvas(modifier.graphicsLayer { rotationZ = spin?.value ?: 0f }) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val sweep = if (progress == null) 90f else 360f * animated
        drawArc(
            color,
            -90f,
            sweep,
            false,
            Offset(inset, inset),
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * A soft 8-point star that relaxes into a circle as [progress] goes 0 → 1 (graphics-shapes Morph),
 * slowly turning while it works.
 */
@Composable
fun MorphIndicator(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val morph = remember {
        val star =
            RoundedPolygon.star(
                8,
                radius = 1f,
                innerRadius = 0.78f,
                rounding = CornerRounding(0.18f),
                innerRounding = CornerRounding(0.18f),
            )
        Morph(star, RoundedPolygon.circle(8, radius = 1f))
    }
    val settle by
        animateFloatAsState(
            progress.coerceIn(0f, 1f),
            spring(stiffness = Spring.StiffnessVeryLow),
            label = "morph",
        )
    val turn =
        if (!reduced) {
            rememberInfiniteTransition(label = "morphTurn")
                .animateFloat(
                    0f,
                    360f,
                    infiniteRepeatable(tween(9_000, easing = LinearEasing)),
                    label = "morphAngle",
                )
        } else {
            null
        }
    Box(
        modifier
            .graphicsLayer { rotationZ = turn?.value ?: 0f }
            .drawWithCache {
                val path = morph.toPath(settle, android.graphics.Path()).asComposePath()
                val matrix =
                    Matrix().apply {
                        translate(size.width / 2f, size.height / 2f)
                        scale(size.minDimension / 2f, size.minDimension / 2f)
                    }
                path.transform(matrix)
                onDrawBehind { drawPath(path, color.copy(alpha = 0.92f)) }
            }
    )
}
