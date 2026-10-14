package app.rosa.weather.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.rosa.weather.core.designsystem.glass.GlassState
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.seaglass.LocalSeaPalette
import app.rosa.weather.core.designsystem.seaglass.SeaGlass
import app.rosa.weather.core.designsystem.seaglass.SeaPalette
import app.rosa.weather.core.designsystem.seaglass.SeaTone
import app.rosa.weather.core.designsystem.seaglass.seaGlass
import app.rosa.weather.core.designsystem.seaglass.seaPill
import app.rosa.weather.core.designsystem.theme.LocalRosaColors
import app.rosa.weather.core.designsystem.theme.Rosa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [GlassSurface] in sea glass. A piece of coloured glass of its own on the canvas; set into other
 * glass, a frosted pill in it (the design's nested pills); a lens (the hourly "now") is such a pill
 * too, a little brighter. Pressed, it sinks: it gives a little, its shadow draws in under it and
 * its colour deepens — and comes back up when let go.
 */
@Composable
internal fun SeaSurface(
    modifier: Modifier,
    style: GlassStyle,
    cornerRadius: Dp,
    state: GlassState,
    shadow: Boolean,
    contentPadding: PaddingValues,
    contentAlignment: Alignment,
    touchResponsive: Boolean,
    prominent: Boolean,
    tone: SeaTone?,
    level: Int,
    content: @Composable BoxScope.() -> Unit,
) {
    val parent = LocalSeaPalette.current
    if ((level > 0 && tone == null) || style == GlassStyle.Lens) {
        val host = parent ?: SeaGlass.Milk
        val pill = modifier.seaPill(host, cornerRadius, strength = if (style == GlassStyle.Lens || prominent) 1.4f else 1f)
        CompositionLocalProvider(LocalGlassLevel provides level + 1) {
            Box(pill.padding(contentPadding), contentAlignment = contentAlignment, content = content)
        }
        return
    }
    val palette = SeaGlass.palette(tone ?: if (prominent) SeaTone.Cyan else SeaTone.Milk)
    val card = style == GlassStyle.Frosted || style == GlassStyle.Sheet
    val scope = rememberCoroutineScope()
    val sunk by remember(state) { derivedStateOf { state.touchStrength > 0.35f } }
    val rest = if (card) SeaGlass.Dimens.Elevation else SeaGlass.Dimens.Elevation * 0.9f
    val elevation by animateDpAsState(if (sunk) rest * 0.4f else rest, spring(0.7f, 500f), label = "sink")
    val base = Rosa.colors
    val glass = modifier
        .graphicsLayer {
            val s = 1f - (if (card) 0.012f else 0.03f) * state.touchStrength
            scaleX = s
            scaleY = s
        }
        .seaGlass(palette, cornerRadius, if (shadow) elevation else 0.dp, press = { state.touchStrength })
        .then(if (touchResponsive) Modifier.seaTouch(state, scope) else Modifier)
    CompositionLocalProvider(
        LocalRosaColors provides SeaGlass.colorsOn(palette, base),
        LocalContentColor provides palette.content,
        LocalSeaPalette provides palette,
        LocalGlassLevel provides level + 1,
    ) {
        Box(glass.padding(contentPadding), contentAlignment = contentAlignment, content = content)
    }
}

/**
 * The finger on sea glass: held a moment without scrolling, the glass is pressed; it lets go as
 * soon as the finger starts to scroll. Never consumes the touch.
 */
private fun Modifier.seaTouch(state: GlassState, scope: kotlinx.coroutines.CoroutineScope): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.touch = down.position
        val push = scope.launch {
            delay(70)
            Animatable(state.touchStrength).animateTo(1f, spring(0.8f, 600f)) { state.touchStrength = value }
        }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
        }
        push.cancel()
        scope.launch { Animatable(state.touchStrength).animateTo(0f, spring(0.55f, 380f)) { state.touchStrength = value } }
    }
}

/** Sea glass of two palettes mixed, for glass that changes colour as it turns on. */
internal fun lerpPalette(a: SeaPalette, b: SeaPalette, t: Float): SeaPalette {
    if (t <= 0f) return a
    if (t >= 1f) return b
    return SeaPalette(
        top = lerp(a.top, b.top, t),
        bottom = lerp(a.bottom, b.bottom, t),
        opacity = a.opacity + (b.opacity - a.opacity) * t,
        rimTop = lerp(a.rimTop, b.rimTop, t),
        rimBottom = lerp(a.rimBottom, b.rimBottom, t),
        content = lerp(a.content, b.content, t),
        speckDark = lerp(a.speckDark, b.speckDark, t),
        speckLight = lerp(a.speckLight, b.speckLight, t),
        pill = lerp(a.pill, b.pill, t),
        spot = lerp(a.spot, b.spot, t),
    )
}

/**
 * A round icon button in sea glass, after the design's call controls: at rest a milky circle,
 * barely raised; pressed, it turns into a soft squircle of cyan glass, lifted on its coloured
 * shadow — and morphs back when let go.
 */
@Composable
internal fun SeaIconButton(
    icon: RosaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHaptics.current
    val motion = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val active = remember { Animatable(0f) }
    val hop = rememberHop()
    val currentOnClick by rememberUpdatedState(onClick)
    val f = active.value
    val palette = lerpPalette(SeaGlass.Milk, SeaGlass.Cyan, f)
    val size = 46.dp
    val radius = lerp(size / 2, 15.dp, f)
    val elevation = lerp(4.dp, SeaGlass.Dimens.Elevation, f)
    Box(
        modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .size(size)
            .seaGlass(palette, radius, elevation)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics?.press()
                    scope.launch { active.animateTo(1f, if (motion) spring(0.62f, 420f) else spring(1f, 10_000f)) }
                    var up = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            up = !change.isConsumed
                            break
                        }
                        if (change.isConsumed) break
                    }
                    scope.launch { active.animateTo(0f, if (motion) spring(0.5f, 260f) else spring(1f, 10_000f)) }
                    if (up) {
                        hop.play()
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        RosaIconView(icon, tint = palette.content, modifier = Modifier.hop(hop))
    }
}

/**
 * A switch in sea glass, after the design's toggles: a capsule of glass — mint when on, milky
 * when off — with a round pebble of paler glass in it that slides across; held, the pebble
 * swells a little and its glass deepens.
 */
@Composable
internal fun SeaToggle(checked: Boolean, position: Float, lift: Float, modifier: Modifier) {
    val track = lerpPalette(SeaGlass.Milk, SeaGlass.Mint, position)
    val knob = lerpPalette(SeaGlass.Milk, lerpPalette(SeaGlass.Mint, SeaGlass.Milk, 0.45f), position)
    Box(modifier.size(width = 60.dp, height = 34.dp)) {
        Box(Modifier.size(width = 60.dp, height = 34.dp).seaGlass(track, 17.dp, elevation = 4.dp))
        Box(
            Modifier
                .padding(start = 3.dp + 26.dp * position, top = 3.dp)
                .size(28.dp)
                .graphicsLayer {
                    val s = 1f + 0.12f * lift
                    scaleX = s * (1f + 0.08f * lift)
                    scaleY = s
                }
                .seaGlass(knob, 14.dp, elevation = 5.dp, press = { lift }),
        )
    }
}
