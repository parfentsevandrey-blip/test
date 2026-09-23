package app.rosa.weather.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.glass.Backdrop
import app.rosa.weather.core.designsystem.glass.GlassState
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.glass.LocalGlassEnvironment
import app.rosa.weather.core.designsystem.glass.liquidGlass
import app.rosa.weather.core.designsystem.glass.rememberGlassState
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import kotlinx.coroutines.launch

/**
 * The backdrop every glass surface refracts. Provided once at the screen root, so components
 * don't have to thread it through every call.
 */
val LocalBackdrop = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }

/**
 * A Liquid Glass surface. It *materialises* when it first appears — lensing and highlights grow
 * in — rather than fading, and carries a soft contact shadow so it floats above the sky.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Regular,
    cornerRadius: Dp = 28.dp,
    state: GlassState = rememberGlassState(),
    shadow: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val environment = LocalGlassEnvironment.current
    val motion = LocalMotionEnabled.current
    LaunchedEffect(state) {
        if (motion && state.materialize == 1f) {
            val a = Animatable(0f)
            a.animateTo(1f, RosaMotion.gel()) { state.materialize = value.coerceIn(0f, 1.2f) }
            state.materialize = 1f
        }
    }
    val shape = RoundedCornerShape(cornerRadius)
    val base = if (shadow) {
        modifier.dropShadow(shape, Shadow(radius = 24.dp, color = Color.Black, offset = DpOffset(0.dp, 6.dp), alpha = 0.12f))
    } else {
        modifier
    }
    val glass = if (backdrop != null) {
        base.liquidGlass(backdrop, style, cornerRadius, state, environment)
    } else {
        base.then(Modifier.graphicsLayer { clip = true; this.shape = shape })
    }
    CompositionLocalProvider(LocalContentColor provides Rosa.colors.ink) {
        Box(glass.padding(contentPadding), contentAlignment = contentAlignment, content = content)
    }
}

/**
 * Glass button with Apple-style interaction: the glass swells a few dp under the finger (gel
 * spring), lights up from the touch point outward, and gives a soft haptic tick.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Regular,
    cornerRadius: Dp = 100.dp,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val state = rememberGlassState()
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val grow = if (size.height > 0) 1f + 8f / size.height else 1.04f

    GlassSurface(
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .onSizeChanged { size = it }
            .graphicsLayer {
                val p = press.value
                scaleX = 1f + (grow - 1f) * p
                scaleY = 1f + (grow - 1f) * p * 0.85f
                alpha = if (enabled) 1f else 0.5f
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
                onClick { currentOnClick(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptics?.press()
                    state.touch = down.position
                    scope.launch { press.animateTo(1f, RosaMotion.press()) }
                    scope.launch {
                        Animatable(state.touchStrength).animateTo(1f, tween(160)) { state.touchStrength = value }
                    }
                    val up = waitForUpOrCancellation()
                    scope.launch { press.animateTo(0f, RosaMotion.gel()) }
                    scope.launch {
                        Animatable(state.touchStrength).animateTo(0f, tween(420)) { state.touchStrength = value }
                    }
                    if (up != null) currentOnClick()
                }
            },
        style = style,
        cornerRadius = cornerRadius,
        state = state,
        contentPadding = contentPadding,
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Small circular glass icon button (44dp touch target, per HIG). */
@Composable
fun GlassIconButton(
    icon: RosaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Regular,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        style = style,
        contentDescription = contentDescription,
        contentPadding = PaddingValues(12.dp),
    ) {
        RosaIconView(icon, tint = Rosa.colors.ink)
    }
}

/** Fill used for content placed *on* glass: Apple's rule is never glass-on-glass. */
@Composable
fun Modifier.glassFill(cornerRadius: Dp = 16.dp, strength: Float = 1f): Modifier {
    val fill = Rosa.colors.fill
    return this.then(
        Modifier.graphicsLayer {
            shape = RoundedCornerShape(cornerRadius)
            clip = true
        }.background(fill.copy(alpha = fill.alpha * strength)),
    )
}

/** Shared press-scale for list rows and cards that aren't themselves glass buttons. */
@Composable
fun rememberPressScale(pressed: Boolean): Float {
    val motion = LocalMotionEnabled.current
    val scale by animateFloatAsState(if (pressed && motion) 0.97f else 1f, RosaMotion.press(), label = "press")
    return scale
}
