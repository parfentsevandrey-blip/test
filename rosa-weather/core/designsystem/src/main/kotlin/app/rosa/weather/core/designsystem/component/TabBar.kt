package app.rosa.weather.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow as DropShadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.glass.LocalGlassEnvironment
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One destination of a [GlassTabBar]: its glass icon and its name. */
@Immutable
data class GlassTab(val icon: RosaIcon, val label: String)

/** How tall the tab bar stands; screens keep their content clear of it. */
val GlassTabBarHeight = 64.dp

/**
 * Apple's Liquid Glass tab bar, down where the thumb is: a floating capsule of glass, each tab an
 * icon over its name, the selected one in the accent colour on a soft pill.
 *
 * Touch the bar and the pill lifts into a lens of clear glass, a little larger than the bar, that
 * follows the finger across the tabs for as long as it stays down — stretching like a drop the
 * faster it goes, the icon under it lighting up and swelling as it passes through the glass, a
 * haptic tick at every tab. Let go and the tab under the finger opens while the lens settles back
 * into a pill there. A tap simply sends the drop flowing to the tab.
 */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val motion = LocalMotionEnabled.current
    val colors = Rosa.colors
    val scope = rememberCoroutineScope()
    val count = tabs.size
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    // The drop's two edges, in tabs: the leading one under the finger and the one trailing it.
    val position = remember { Animatable(selected.toFloat()) }
    val trail = remember { Animatable(selected.toFloat()) }
    var pressed by remember { mutableStateOf(false) }
    // While the finger is down, the tab under the lens is lit before it opens.
    val underLens by remember(count) { derivedStateOf { position.value.roundToInt().coerceIn(0, count - 1) } }
    LaunchedEffect(selected) {
        if (pressed) return@LaunchedEffect
        launch { position.animateTo(selected.toFloat(), RosaMotion.gel()) }
        trail.animateTo(selected.toFloat(), TabTail)
    }
    val moving = abs(position.value - selected) > 0.02f || abs(trail.value - selected) > 0.02f
    val lift by animateFloatAsState(if (pressed || (moving && motion)) 1f else 0f, spring(0.72f, 520f), label = "lift")
    // How bright the sky behind light type is: over a bright blue day the pill darkens instead of
    // lightening, and the selected name keeps to white, so both still read.
    val bright = if (colors.isLightSky) 0f else LocalGlassEnvironment.current.tintBoost
    // On the cozy mood's honey glass an amber name would vanish: the selected tab is lit instead,
    // an amber pill with the name in ivory on it.
    val glow = if (colors.isLightSky) 0f else colors.glassGlow
    val rest = if (colors.isLightSky) {
        Color.White.copy(alpha = 0.55f)
    } else {
        lerp(Color.White.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.16f), bright).let { if (glow > 0f) lerp(it, colors.accent.copy(alpha = 0.55f), glow) else it }
    }
    val lip = Color.White.copy(alpha = if (colors.isLightSky) 0.85f else 0.28f)
    val shape = RoundedCornerShape(GlassTabBarHeight / 2)

    BoxWithConstraints(modifier.fillMaxWidth().height(GlassTabBarHeight)) {
        val segment = (maxWidth - Inset * 2) / count
        // A layer of its own above the content: a deep shadow that still shows over a night sky
        // or a dark card, so the bar never reads as part of what lies under it.
        Box(Modifier.fillMaxSize().dropShadow(shape, DropShadow(radius = 26.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = if (colors.isLightSky) 0.2f else 0.45f)))
        // The capsule itself: denser than a button's glass, so its names read over the bright
        // horizon at the bottom of the sky.
        GlassSurface(Modifier.fillMaxSize(), style = BarGlass, cornerRadius = GlassTabBarHeight / 2, touchResponsive = false) {}
        // Its own tone: by night a little lighter than the dark glass of the cards, on a bright
        // blue day a little darker than the glass may get on its own; and a crisp edge all round.
        val tone = if (colors.isLightSky) Color.Transparent else lerp(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.12f), bright)
        val edge = if (colors.isLightSky) {
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.95f), 1f to Color.Black.copy(alpha = 0.12f))
        } else {
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.4f), 1f to Color.White.copy(alpha = 0.12f))
        }
        Box(Modifier.fillMaxSize().clip(shape).background(tone).border(1.dp, edge, shape))
        // The drop spans both of its edges and stays inside the bar: at the last tab it squashes
        // against the wall. Stretched, it thins a little, as liquid keeps its volume.
        val drop = Modifier
            .offset {
                val from = minOf(position.value, trail.value).coerceAtLeast(0f)
                val to = (maxOf(position.value, trail.value) + 1f).coerceAtMost(count.toFloat())
                IntOffset((Inset + segment * ((from + to - 1f) / 2f)).roundToPx(), Inset.roundToPx())
            }
            .width(segment)
            .height(GlassTabBarHeight - Inset * 2)
            .graphicsLayer {
                val from = minOf(position.value, trail.value).coerceAtLeast(0f)
                val to = (maxOf(position.value, trail.value) + 1f).coerceAtMost(count.toFloat())
                val stretch = to - from - 1f
                // Lifted, the lens stands proud of the bar, as Apple's does.
                scaleX = (1f + stretch) * (1f + 0.12f * lift)
                scaleY = (1f - 0.14f * stretch.coerceIn(0f, 1f)) * (1f + 0.3f * lift)
            }
        val pillShape = RoundedCornerShape((GlassTabBarHeight - Inset * 2) / 2)
        Box(
            drop
                .graphicsLayer { alpha = 1f - lift }
                .clip(pillShape)
                .background(rest)
                .border(0.8.dp, Brush.verticalGradient(0f to lip, 0.55f to lip.copy(alpha = 0f)), pillShape),
        )
        if (lift > 0.02f) GlassSurface(drop, style = GlassStyle.Lens, cornerRadius = (GlassTabBarHeight - Inset * 2) / 2) {}
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Inset)
                .selectableGroup()
                .pointerInput(count) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val width = size.width / count.toFloat()
                        fun at(x: Float) = (x / width - 0.5f).coerceIn(0f, count - 1f)
                        pressed = true
                        haptics?.press()
                        var target = at(down.position.x).roundToInt()
                        if (target != currentSelected) haptics?.tick()
                        var lifted = false
                        try {
                            // The lens reaches for the finger, then follows it.
                            val start = at(down.position.x)
                            scope.launch { position.animateTo(start, Follow) }
                            scope.launch { trail.animateTo(start, TabTail) }
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val a = at(change.position.x)
                                scope.launch { position.animateTo(a, Follow) }
                                scope.launch { trail.animateTo(a, TabTail) }
                                val nearest = a.roundToInt()
                                if (nearest != target) {
                                    target = nearest
                                    haptics?.tick()
                                }
                            }
                            lifted = true
                        } finally {
                            pressed = false
                            // The lens settles into a pill on the chosen tab, and the tab opens —
                            // only when the finger was lifted, not when the gesture was called off.
                            val chosen = if (lifted) target else currentSelected
                            scope.launch { position.animateTo(chosen.toFloat(), RosaMotion.gel()) }
                            scope.launch { trail.animateTo(chosen.toFloat(), TabTail) }
                            if (lifted && chosen != currentSelected) select(chosen)
                        }
                    }
                },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val lit = if (pressed) i == underLens else i == selected
                // The selected tab in the accent over dark skies; over bright ones, where the
                // accent's gold would fade into the milky glass, in a deep blue.
                val selectedTint = when {
                    colors.isLightSky -> SelectedOnLight
                    glow > 0f -> lerp(colors.accent, colors.ink, glow)
                    else -> colors.accent
                }
                val selectedLabel = when {
                    colors.isLightSky -> SelectedOnLight
                    glow > 0f -> lerp(lerp(colors.accent, colors.ink, bright), colors.ink, glow)
                    else -> lerp(colors.accent, colors.ink, bright)
                }
                Column(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = i == selected
                            onClick {
                                if (i != currentSelected) select(i)
                                true
                            }
                        }
                        .graphicsLayer {
                            // What passes through the lens swells, as under a magnifier.
                            val focus = (1f - abs(i - position.value)).coerceIn(0f, 1f) * lift
                            val s = 1f + 0.16f * focus
                            scaleX = s
                            scaleY = s
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RosaIconView(tab.icon, if (lit) selectedTint else colors.ink, size = 24.dp)
                    Text(
                        tab.label,
                        // Light type keeps a soft shadow, as over the sky itself.
                        style = if (colors.isLightSky) Rosa.type.caption else Rosa.type.caption.copy(shadow = LabelShadow),
                        color = if (lit) selectedLabel else colors.ink,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/** Between the bar's edge and its pill. */
private val Inset = 4.dp

/** The bar's glass: the controls' own, with a denser tone of its own for the names on it. */
private val BarGlass = GlassStyle.Regular.copy(tintAlpha = 0.26f)

private val SelectedOnLight = Color(0xFF1F5FC0)

private val LabelShadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1.5f), 8f)

/** The lens following the finger: tight enough to feel attached, soft enough not to jitter. */
private val Follow = spring<Float>(dampingRatio = 1f, stiffness = 1400f)

/**
 * The trailing edge of the drop: a little softer and bouncier than the gel spring its leading
 * edge rides, so it lags behind as it moves and overshoots once as it arrives.
 */
private val TabTail = spring<Float>(dampingRatio = 0.55f, stiffness = 110f)
