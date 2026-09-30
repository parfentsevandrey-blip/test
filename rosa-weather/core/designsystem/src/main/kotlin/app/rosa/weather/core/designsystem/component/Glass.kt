package app.rosa.weather.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
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
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The backdrop every glass surface refracts. Provided once at the screen root, so components
 * don't have to thread it through every call.
 */
val LocalBackdrop = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }

/**
 * Whether glass here is rich (the weather app): it floats on layered shadows in the sky's own
 * colour, catches a band of light as it arrives, and the world's reflections and the colours in
 * its rim slide along it as it scrolls. Otherwise (the calendar) it is the plainer glass, on soft
 * grey shadows.
 */
val LocalRichGlass = androidx.compose.runtime.staticCompositionLocalOf { false }

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
    val rich = LocalRichGlass.current
    // Where the pane first stands on screen (dp from the top), for the sweep's cascade.
    val arrivedAt = remember { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current.density
    LaunchedEffect(state) {
        if (motion && !appeared && state.materialize == 1f) {
            // Rich glass catches a band of light as it arrives, sweeping across it once — a wave
            // washing down the screen: the lower a pane, the later the light reaches it.
            if (rich) {
                launch {
                    val top = snapshotFlow { arrivedAt.floatValue }.first { !it.isNaN() }
                    delay(SWEEP_DELAY + (top.coerceIn(0f, 900f) * SWEEP_CASCADE).toLong())
                    animate(0f, 1f, animationSpec = tween(SWEEP_MILLIS, easing = FastOutSlowInEasing)) { v, _ -> state.sweep = v }
                    state.sweep = 0f
                }
            }
            val a = Animatable(0f)
            a.animateTo(1f, RosaMotion.gel()) { state.materialize = value.coerceIn(0f, 1.2f) }
            state.materialize = 1f
        }
        appeared = true
    }
    val shape = RoundedCornerShape(cornerRadius)
    val card = style == GlassStyle.Frosted || style == GlassStyle.Sheet
    val colors = Rosa.colors
    // Rich glass casts its shadows in the sky's own deep colour rather than grey — navy under a
    // blue day, violet at dusk — as light through tinted air does: it floats in the scene.
    val deep = lerp(colors.zenith, Color.Black, 0.55f)
    val base = when {
        !shadow -> modifier
        // Cards lie on a broad, soft shadow (rich: a broad ambient one well below, and a tighter
        // one that grounds the edge); floating controls also on a tight contact one.
        card && rich -> modifier
            .dropShadow(shape, Shadow(radius = 44.dp, color = deep, offset = DpOffset(0.dp, 18.dp), alpha = if (colors.isLightSky) 0.2f else 0.3f))
            .dropShadow(shape, Shadow(radius = 10.dp, color = deep, offset = DpOffset(0.dp, 4.dp), alpha = if (colors.isLightSky) 0.12f else 0.18f))
        card -> modifier.dropShadow(shape, Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 10.dp), alpha = 0.13f))
        rich -> modifier
            .dropShadow(shape, Shadow(radius = 24.dp, color = deep, offset = DpOffset(0.dp, 10.dp), alpha = if (colors.isLightSky) 0.18f else 0.26f))
            .dropShadow(shape, Shadow(radius = 4.dp, color = deep, offset = DpOffset(0.dp, 1.5.dp), alpha = 0.12f))
        else -> modifier
            .dropShadow(shape, Shadow(radius = 22.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.14f))
            .dropShadow(shape, Shadow(radius = 3.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.08f))
    }
    val placed = if (rich && motion && !appeared) {
        Modifier.onGloballyPositioned { if (arrivedAt.floatValue.isNaN()) arrivedAt.floatValue = it.positionInRoot().y / density }
    } else {
        Modifier
    }
    val glass = if (backdrop != null) {
        base.then(placed).liquidGlass(backdrop, style, cornerRadius, state, environment)
            .then(if (touchResponsive && motion) Modifier.glassTouch(state, scope) else Modifier)
            // What lies on the glass keeps to its shape (a card's own tint or picture has its
            // rounded corners); the glass itself, drawn before, still blooms past its edge.
            .clip(shape)
    } else {
        base.then(Modifier.graphicsLayer { clip = true; this.shape = shape })
    }
    CompositionLocalProvider(LocalContentColor provides Rosa.colors.ink, LocalGlassLevel provides level + 1) {
        Box(glass.padding(contentPadding), contentAlignment = contentAlignment, content = content)
    }
}

/**
 * When the band of light crosses glass that has just arrived, how much later per dp further down
 * the screen, and how long it takes.
 */
private const val SWEEP_DELAY = 120L
private const val SWEEP_CASCADE = 0.45f
private const val SWEEP_MILLIS = 1500

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
    val light = remember(state, scope) { GlassTouchLight(state, scope) }
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
                    light.press(1f)
                    // The light follows the finger across the glass until it lets go.
                    val up = trackUntilUp { state.touch = it }
                    // Let go, the button settles like a gel: a small swing past its size and back.
                    scope.launch { press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = RosaMotion.GelStiffness * 1.4f)) }
                    light.release(420)
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
private fun Modifier.glassTouch(state: GlassState, scope: kotlinx.coroutines.CoroutineScope): Modifier {
    val light = GlassTouchLight(state, scope)
    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.touch = down.position
            var lit = false
            val glow = scope.launch {
                delay(70) // a scroll that starts right away never lights up
                lit = true
                light.press(0.75f)
            }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                state.touch = change.position
            }
            glow.cancel()
            if (lit) light.release(380)
        }
    }
}

/**
 * The finger's light on the glass. Pressed: the light comes up under it and runs round the rim
 * from there, both ways, while the glass swells into a lens. Let go: the light fades, and the lens
 * springs back like a gel — past flat into a slight dimple and back, twice, calmly.
 */
private class GlassTouchLight(private val state: GlassState, private val scope: kotlinx.coroutines.CoroutineScope) {
    private var glow: kotlinx.coroutines.Job? = null
    private var lens: kotlinx.coroutines.Job? = null
    private var spread: kotlinx.coroutines.Job? = null

    fun press(strength: Float) {
        glow?.cancel()
        lens?.cancel()
        spread?.cancel()
        glow = scope.launch { Animatable(state.touchStrength).animateTo(strength, tween(160)) { state.touchStrength = value } }
        lens = scope.launch { Animatable(state.lens).animateTo(strength, RosaMotion.press()) { state.lens = value } }
        spread = scope.launch {
            state.spread = 0f
            Animatable(0f).animateTo(1f, tween(620, easing = FastOutSlowInEasing)) { state.spread = value }
        }
    }

    fun release(fadeMillis: Int) {
        glow?.cancel()
        lens?.cancel()
        glow = scope.launch { Animatable(state.touchStrength).animateTo(0f, tween(fadeMillis)) { state.touchStrength = value } }
        lens = scope.launch {
            Animatable(state.lens).animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 230f)) { state.lens = value }
            state.lens = 0f
        }
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
