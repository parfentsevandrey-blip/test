package app.rosa.weather.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
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
 * How many glass surfaces the content here already sits on. Glass never refracts glass (Apple's
 * rule): a surface placed on another becomes a platter set into it instead of a hole through it.
 */
val LocalGlassLevel = androidx.compose.runtime.staticCompositionLocalOf { 0 }

/**
 * A Liquid Glass surface. It *materialises* when it first appears — lensing and highlights grow
 * in — rather than fading, and floats above the sky on a soft shadow (controls also on a tight
 * contact shadow). Placed on other glass it becomes a platter: a tinted inset with a lit upper lip
 * that still lights up under the finger. Lenses ([GlassStyle.Lens]) stay lenses anywhere.
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
    /** Light up under the finger (never consumes the touch). */
    touchResponsive: Boolean = true,
    /** The primary action: set into other glass, it stands out as a denser, brighter platter. */
    prominent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val environment = LocalGlassEnvironment.current
    val motion = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val level = LocalGlassLevel.current
    if (level > 0 && style != GlassStyle.Lens) {
        Platter(modifier, cornerRadius, state, contentPadding, contentAlignment, touchResponsive && motion, level, prominent, content)
        return
    }
    // Materialise once. Lists dispose cards that scroll away and recreate them on the way back;
    // saveable state survives that, so scrolling never replays the appearance.
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (motion && !appeared && state.materialize == 1f) {
            val a = Animatable(0f)
            a.animateTo(1f, RosaMotion.gel()) { state.materialize = value.coerceIn(0f, 1.2f) }
            state.materialize = 1f
        }
        appeared = true
    }
    val shape = RoundedCornerShape(cornerRadius)
    val card = style == GlassStyle.Frosted || style == GlassStyle.Sheet
    val base = when {
        !shadow -> modifier
        // Cards lie on a broad, soft shadow; floating controls also on a tight contact one.
        card -> modifier.dropShadow(shape, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 10.dp), alpha = 0.13f))
        else -> modifier
            .dropShadow(shape, Shadow(radius = 22.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.14f))
            .dropShadow(shape, Shadow(radius = 3.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.08f))
    }
    val glass = if (backdrop != null) {
        base.liquidGlass(backdrop, style, cornerRadius, state, environment)
            .then(if (touchResponsive && motion) Modifier.glassTouch(state, scope) else Modifier)
    } else {
        base.then(Modifier.graphicsLayer { clip = true; this.shape = shape })
    }
    CompositionLocalProvider(LocalContentColor provides Rosa.colors.ink, LocalGlassLevel provides level + 1) {
        Box(glass.padding(contentPadding), contentAlignment = contentAlignment, content = content)
    }
}

/** A surface set into the glass it sits on: tinted inset, lit upper lip, a glow under the finger. */
@Composable
private fun Platter(
    modifier: Modifier,
    cornerRadius: Dp,
    state: GlassState,
    contentPadding: PaddingValues,
    contentAlignment: Alignment,
    touchResponsive: Boolean,
    level: Int,
    prominent: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Rosa.colors
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(cornerRadius)
    val fill = if (prominent) {
        (if (colors.isLightSky) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.26f))
    } else {
        colors.fill.copy(alpha = colors.fill.alpha * if (colors.isLightSky) 1.2f else 1f)
    }
    val lip = Color.White.copy(alpha = if (colors.isLightSky) 0.55f else if (prominent) 0.42f else 0.2f)
    val surface = modifier
        .clip(shape)
        .drawBehind {
            drawRect(fill)
            val touch = state.touch
            val strength = state.touchStrength
            if (touch.isSpecified && strength > 0f) {
                val reach = size.maxDimension * 0.8f
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f * strength), Color.Transparent), touch, reach), reach, touch)
            }
        }
        .border(0.8.dp, Brush.verticalGradient(0f to lip, 0.55f to lip.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.04f)), shape)
        .then(if (touchResponsive) Modifier.glassTouch(state, scope) else Modifier)
    CompositionLocalProvider(LocalContentColor provides colors.ink, LocalGlassLevel provides level + 1) {
        Box(surface.padding(contentPadding), contentAlignment = contentAlignment, content = content)
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
    /** The primary action among its neighbours. */
    prominent: Boolean = false,
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
                    // The light follows the finger across the glass until it lets go.
                    val up = trackUntilUp { state.touch = it }
                    scope.launch { press.animateTo(0f, RosaMotion.gel()) }
                    scope.launch {
                        Animatable(state.touchStrength).animateTo(0f, tween(420)) { state.touchStrength = value }
                    }
                    if (up != null) {
                        up.consume()
                        currentOnClick()
                    }
                }
            },
        style = style,
        cornerRadius = cornerRadius,
        state = state,
        contentPadding = contentPadding,
        contentAlignment = Alignment.Center,
        touchResponsive = false,
        prominent = prominent,
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
    val hop = rememberHop()
    GlassButton(
        onClick = {
            hop.play()
            onClick()
        },
        modifier = modifier,
        style = style,
        contentDescription = contentDescription,
        contentPadding = PaddingValues(12.dp),
    ) {
        RosaIconView(icon, tint = Rosa.colors.ink, modifier = Modifier.hop(hop))
    }
}

/**
 * A little springy hop — tilt, swell, settle with a wobble — that icons do when tapped, so every
 * control answers with motion as well as a haptic.
 */
@Stable
class Hop internal constructor(private val scope: kotlinx.coroutines.CoroutineScope, private val enabled: Boolean) {
    internal val value = Animatable(0f)

    fun play() {
        if (!enabled) return
        scope.launch {
            value.snapTo(1f)
            value.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 380f))
        }
    }
}

@Composable
fun rememberHop(): Hop {
    val scope = rememberCoroutineScope()
    val motion = LocalMotionEnabled.current
    return remember(scope, motion) { Hop(scope, motion) }
}

fun Modifier.hop(hop: Hop): Modifier = graphicsLayer {
    val v = hop.value.value
    rotationZ = -16f * v
    scaleX = 1f + 0.2f * v
    scaleY = 1f + 0.2f * v
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

/**
 * Glow under the finger for any glass surface. Observes the gesture without consuming it, so
 * content inside keeps working, and backs off as soon as the finger starts scrolling.
 */
private fun Modifier.glassTouch(state: GlassState, scope: kotlinx.coroutines.CoroutineScope): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.touch = down.position
            val glow = scope.launch {
                delay(70) // a scroll that starts right away never lights up
                Animatable(state.touchStrength).animateTo(0.75f, tween(180)) { state.touchStrength = value }
            }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                state.touch = change.position
            }
            glow.cancel()
            scope.launch { Animatable(state.touchStrength).animateTo(0f, tween(380)) { state.touchStrength = value } }
        }
    }

/**
 * Like `waitForUpOrCancellation`, but reports every move to [onMove]. Returns the up change, or
 * null if the gesture was consumed elsewhere (a scroll) or left the element.
 */
private suspend fun AwaitPointerEventScope.trackUntilUp(onMove: (Offset) -> Unit): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.all { it.changedToUp() }) return event.changes[0]
        if (event.changes.any { it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding) }) return null
        event.changes.firstOrNull()?.let { onMove(it.position) }
        val finalPass = awaitPointerEvent(PointerEventPass.Final)
        if (finalPass.changes.any { it.isConsumed }) return null
    }
}
