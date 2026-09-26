package app.opal.core.designsystem.glass

// The lens follows Kyant0/AndroidLiquidGlass catalog (LiquidBottomTabs, DampedDragAnimation),
// Apache License 2.0, https://github.com/Kyant0/AndroidLiquidGlass. Unlike there, the whole bar is
// the gesture area: the lens jumps under the finger wherever the bar is pressed.

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
data class TabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    /** Stable id for UI automation (baseline profile generator, benchmarks). */
    val testTag: String = label,
)

/**
 * Floating glass tab bar. The selection is a lens of glass: press anywhere on the bar and slide
 * without lifting the finger — the lens lifts off, follows the finger magnifying the tabs beneath
 * it, and the tab under it is selected when the finger lifts. While content scrolls down
 * ([compact]) the bar shrinks and hides labels, leaving more room for content.
 *
 * Touch belongs to the bar's own gesture (it consumes touch before the tabs see it, so a tap never
 * selects twice); TalkBack and keyboards use the tabs' semantics and focus. Without live glass
 * (simplified graphics, Android < 13) the lens is a flat pill that moves the same way.
 */
@Composable
fun GlassTabBar(
    items: ImmutableList<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = OpalTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val backdrops = LocalGlassBackdrops.current
    // The lens refracts (AGSL): full-quality glass only.
    val liquid = LocalGlassQuality.current == GlassQuality.Full && backdrops != null
    val motion = if (reduced) spring<Dp>(stiffness = Spring.StiffnessHigh) else spring(0.74f, 380f)
    val height by animateDpAsState(if (compact) 52.dp else 64.dp, motion, label = "tabBarHeight")
    // At large font scales the labels would not fit: icons only (each keeps its label for
    // TalkBack).
    val iconsOnly = compact || LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val labelAlpha by animateFloatAsState(if (iconsOnly) 0f else 1f, label = "tabLabels")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val scope = rememberCoroutineScope()
    val tabLens =
        remember(scope, items.size, reduced) { TabLens(scope, items.size, selectedIndex, reduced) }
    LaunchedEffect(tabLens, selectedIndex) { tabLens.glideTo(selectedIndex) }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // The bar swells a little while the lens is lifted.
    val swell: GraphicsLayerScope.() -> Unit =
        remember(tabLens, reduced) {
            {
                if (!reduced) {
                    val grow = BAR_SWELL.toPx() / size.width.coerceAtLeast(1f)
                    val scale = lerp(1f, 1f + grow, tabLens.pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
            }
        }

    BoxWithConstraints(
        modifier.height(height).pointerInput(tabLens, rtl, haptics) {
            awaitEachGesture {
                // Initial pass: the tabs' own click handling never sees touch (keyboard and
                // TalkBack still use it).
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                val inset = TAB_INSET.toPx()
                val slot = (size.width - inset * 2) / tabLens.count
                fun tabAt(x: Float): Float {
                    val fromStart = if (rtl) size.width - x else x
                    return (fromStart - inset) / slot - 0.5f
                }
                tabLens.hold(tabAt(down.position.x))
                var hovered = tabLens.target
                var lifted = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        lifted = true
                        break
                    }
                    if (change.positionChanged()) {
                        tabLens.moveTo(tabAt(change.position.x))
                        if (tabLens.target != hovered) {
                            hovered = tabLens.target
                            haptics?.tick()
                        }
                    }
                    change.consume()
                }
                val index = if (lifted) tabLens.target else currentSelected
                tabLens.letGo(index)
                if (index != currentSelected) {
                    haptics?.tap()
                    currentOnSelect(index)
                }
            }
        }
    ) {
        val slot = (maxWidth - TAB_INSET * 2) / items.size
        val slotPx = with(LocalDensity.current) { slot.toPx() }
        val direction = if (rtl) -1f else 1f
        val lensShift: GraphicsLayerScope.() -> Unit = {
            translationX = direction * tabLens.value * slotPx
        }
        // The glass lens (and the copy it magnifies) exists only while it is lifted or gliding: at
        // rest it would redraw three glass layers every frame of the aurora for a still picture.
        // A flat pill and an accent-colored tab look exactly like the resting lens.
        val lensActive by remember(tabLens) { derivedStateOf { tabLens.isActive } }
        // Drawn over the tabs by the lens, so only a veil: the selected tab keeps its color.
        val veil =
            if (colors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)

        GlassSurface(
            shape = Capsule(),
            style = GlassStyle.Bar,
            layer = GlassLayer.Floating,
            layerBlock = swell,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!liquid || !lensActive) {
                Box(
                    Modifier.fillMaxSize().padding(TAB_INSET),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier.graphicsLayer(lensShift)
                            .width(slot)
                            .fillMaxHeight()
                            .clip(Capsule())
                            .background(if (liquid) veil else colors.glassTintStrong)
                    )
                }
            }
            TabRow(
                items = items,
                selectedIndex = selectedIndex,
                labelAlpha = labelAlpha,
                style =
                    when {
                        !liquid -> TabRowStyle.FlatSelection
                        lensActive -> TabRowStyle.Bar
                        else -> TabRowStyle.RestingLens
                    },
                onSelect = { index ->
                    if (index != selectedIndex) {
                        haptics?.tap()
                        onSelect(index)
                    }
                },
                modifier = Modifier.fillMaxSize().padding(TAB_INSET),
            )
        }

        if (liquid && backdrops != null) {
            val tabsBackdrop = rememberLayerBackdrop()
            val lensBackdrop = rememberCombinedBackdrop(backdrops.floating, tabsBackdrop)
            if (lensActive) {
                GlassLens(
                    items = items,
                    selectedIndex = selectedIndex,
                    labelAlpha = labelAlpha,
                    tabLens = tabLens,
                    tabsBackdrop = tabsBackdrop,
                    lensBackdrop = lensBackdrop,
                    swell = swell,
                    lensShift = lensShift,
                    slot = slot,
                    veil = veil,
                    reduced = reduced,
                )
            }
        }
    }
}

/**
 * The lifted lens: an invisible copy of the bar with every tab drawn as selected (recorded into
 * [tabsBackdrop]) and the glass lens over it that refracts and magnifies that copy.
 */
@Composable
private fun GlassLens(
    items: ImmutableList<TabItem>,
    selectedIndex: Int,
    labelAlpha: Float,
    tabLens: TabLens,
    tabsBackdrop: LayerBackdrop,
    lensBackdrop: Backdrop,
    swell: GraphicsLayerScope.() -> Unit,
    lensShift: GraphicsLayerScope.() -> Unit,
    slot: Dp,
    veil: Color,
    reduced: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        GlassSurface(
            shape = Capsule(),
            style = GlassStyle.Bar,
            layer = GlassLayer.Floating,
            layerBlock = swell,
            modifier =
                Modifier.fillMaxSize()
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop),
        ) {
            TabRow(
                items = items,
                selectedIndex = selectedIndex,
                labelAlpha = labelAlpha,
                style = TabRowStyle.UnderLens,
                onSelect = null,
                // Magnified while the lens is lifted, like a drop of water over the icons.
                contentScale = { lerp(1f, LENS_MAGNIFICATION, tabLens.pressProgress) },
                modifier = Modifier.fillMaxSize().padding(TAB_INSET),
            )
        }
        Box(
            Modifier.padding(TAB_INSET)
                .graphicsLayer(lensShift)
                .width(slot)
                .fillMaxHeight()
                .drawBackdrop(
                    backdrop = lensBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val lifted = tabLens.pressProgress
                        if (lifted > 0f) {
                            lens(
                                LENS_REFRACTION_HEIGHT.toPx() * lifted,
                                LENS_REFRACTION_AMOUNT.toPx() * lifted,
                                chromaticAberration = true,
                            )
                        }
                    },
                    highlight = { Highlight.Default.copy(alpha = tabLens.pressProgress) },
                    shadow = { Shadow(alpha = tabLens.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * tabLens.pressProgress,
                            alpha = tabLens.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = tabLens.scaleX
                        scaleY = tabLens.scaleY
                        if (!reduced) {
                            // Squash and stretch with speed, like a drop of liquid.
                            val stretch =
                                (abs(tabLens.velocity) * STRETCH_PER_TAB_PER_SECOND).coerceAtMost(
                                    MAX_STRETCH
                                )
                            scaleX /= 1f - stretch
                            scaleY *= 1f - stretch / 3f
                        }
                    },
                    onDrawSurface = {
                        val lifted = tabLens.pressProgress
                        drawRect(veil, alpha = 1f - lifted)
                        drawRect(Color.Black.copy(alpha = 0.03f * lifted))
                    },
                )
        )
    }
}

private enum class TabRowStyle {
    /** The bar under a glass lens: the selected tab is marked by the lens, not by color. */
    Bar,
    /** Live glass at rest: the flat stand-in for the resting lens (selected tab in accent). */
    RestingLens,
    /** The bar with a flat pill (no live glass): the selected tab is tinted in place. */
    FlatSelection,
    /** The copy the lens magnifies: every tab looks selected. */
    UnderLens,
}

@Composable
private fun TabRow(
    items: ImmutableList<TabItem>,
    selectedIndex: Int,
    labelAlpha: Float,
    style: TabRowStyle,
    /** Null for the copy under the lens (no semantics, no focus). */
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    contentScale: (() -> Float)? = null,
) {
    val colors = OpalTheme.colors
    Row(
        modifier.then(if (onSelect != null) Modifier.selectableGroup() else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val lensed = style == TabRowStyle.UnderLens
            val iconTint =
                when {
                    lensed -> colors.accent
                    selected && style != TabRowStyle.Bar -> colors.accent
                    else -> colors.onGlass
                }
            val labelColor =
                when {
                    lensed -> colors.accent
                    selected && style == TabRowStyle.RestingLens -> colors.accent
                    selected -> colors.onGlass
                    else -> colors.onGlassMuted
                }
            Column(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (contentScale != null) {
                            Modifier.graphicsLayer {
                                val scale = contentScale()
                                scaleX = scale
                                scaleY = scale
                            }
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (onSelect != null) {
                            Modifier.testTag(item.testTag).clip(Capsule()).selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSelect(index)
                            }
                        } else {
                            Modifier
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (selected || lensed) item.selectedIcon else item.icon,
                    contentDescription = if (labelAlpha < 0.5f) item.label else null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
                if (labelAlpha > 0.01f) {
                    // Long labels (ru: «Подключение») shrink a little instead of clipping.
                    BasicText(
                        item.label,
                        style = OpalTheme.type.caption.copy(color = labelColor),
                        maxLines = 1,
                        autoSize =
                            TextAutoSize.StepBased(
                                minFontSize = 9.sp,
                                maxFontSize = OpalTheme.type.caption.fontSize,
                                stepSize = 0.5.sp,
                            ),
                        modifier =
                            Modifier.padding(horizontal = 2.dp).graphicsLayer {
                                alpha = labelAlpha
                            },
                    )
                }
            }
        }
    }
}

/**
 * Position and "liquid" response of the tab bar lens. [value] is the lens position in tabs
 * (fractional while it moves), [pressProgress] how far it has lifted off the bar (0 resting, 1 a
 * magnifying glass), [scaleX]/[scaleY] its swell and [velocity] its damped speed in tabs per
 * second, for squash and stretch.
 */
@Stable
private class TabLens(
    private val scope: CoroutineScope,
    val count: Int,
    initial: Int,
    private val reduced: Boolean,
) {
    private val position = Animatable(initial.toFloat(), POSITION_THRESHOLD)
    private val press = Animatable(0f, 0.001f)
    private val swellX = Animatable(1f, 0.001f)
    private val swellY = Animatable(1f, 0.001f)
    private val speed = Animatable(0f, SPEED_THRESHOLD)
    private val tracker = VelocityTracker()
    private var settling: Job? = null
    private var held = false

    val value: Float
        get() = position.value

    /** The tab the lens is heading to: under the finger while dragged. */
    val target: Int
        get() = position.targetValue.roundToInt().coerceIn(0, count - 1)

    val pressProgress: Float
        get() = press.value

    val scaleX: Float
        get() = swellX.value

    val scaleY: Float
        get() = swellY.value

    val velocity: Float
        get() = speed.value

    /** Lifted or still gliding: the glass lens is on screen. */
    val isActive: Boolean
        get() = press.value > 0f || position.isRunning

    /** Finger down at [at] (in tabs): lift the lens and bring it under the finger. */
    fun hold(at: Float) {
        held = true
        settling?.cancel()
        tracker.resetTracking()
        scope.launch { press.animateTo(1f, PRESS_SPEC) }
        if (!reduced) {
            scope.launch { swellX.animateTo(LIFTED_SCALE, SWELL_X_SPEC) }
            scope.launch { swellY.animateTo(LIFTED_SCALE, SWELL_Y_SPEC) }
        }
        moveTo(at)
    }

    fun moveTo(at: Float) {
        val to = at.coerceIn(0f, (count - 1).toFloat())
        scope.launch {
            position.animateTo(to, if (reduced) FOLLOW_REDUCED_SPEC else FOLLOW_SPEC) {
                trackSpeed()
            }
        }
    }

    /** Finger up: settle on [index], then lower the lens back into the bar. */
    fun letGo(index: Int) {
        held = false
        moveTo(index.toFloat())
        settling = scope.launch {
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                snapshotFlow { position.value }.first { abs(it - index) < SETTLE_DISTANCE }
            }
            launch { press.animateTo(0f, PRESS_SPEC) }
            launch { swellX.animateTo(1f, SWELL_X_SPEC) }
            launch { swellY.animateTo(1f, SWELL_Y_SPEC) }
            launch { speed.animateTo(0f, SPEED_SPEC) }
        }
    }

    /** The selection changed elsewhere (back navigation, keyboard, TalkBack): glide there. */
    fun glideTo(index: Int) {
        if (held || position.targetValue == index.toFloat()) return
        if (reduced) {
            moveTo(index.toFloat())
            return
        }
        hold(position.value)
        letGo(index)
    }

    private fun trackSpeed() {
        tracker.addPosition(SystemClock.uptimeMillis(), Offset(position.value, 0f))
        val tabsPerSecond = tracker.calculateVelocity().x
        scope.launch { speed.animateTo(tabsPerSecond, SPEED_SPEC) }
    }
}

private val TAB_INSET = 6.dp
private val BAR_SWELL = 8.dp
private val LENS_REFRACTION_HEIGHT = 10.dp
private val LENS_REFRACTION_AMOUNT = 14.dp
private const val LIFTED_SCALE = 1.3f
private const val LENS_MAGNIFICATION = 1.2f
private const val STRETCH_PER_TAB_PER_SECOND = 0.02f
private const val MAX_STRETCH = 0.2f
private const val POSITION_THRESHOLD = 0.001f
private const val SPEED_THRESHOLD = 0.05f
private const val SETTLE_DISTANCE = 0.05f
private const val SETTLE_TIMEOUT_MS = 600L
private const val LARGE_FONT_SCALE = 1.3f

// Damped following: critically damped and stiff, so the lens trails the finger a hair.
private val FOLLOW_SPEC = spring(1f, 1000f, POSITION_THRESHOLD)
private val FOLLOW_REDUCED_SPEC = spring(1f, Spring.StiffnessHigh, POSITION_THRESHOLD)
private val PRESS_SPEC = spring(1f, 1000f, 0.001f)
private val SWELL_X_SPEC = spring(0.6f, 250f, 0.001f)
private val SWELL_Y_SPEC = spring(0.7f, 250f, 0.001f)
private val SPEED_SPEC = spring(0.5f, 300f, SPEED_THRESHOLD)
