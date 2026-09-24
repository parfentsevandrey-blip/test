package app.rosa.weather.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A switch whose knob is solid at rest and turns into Liquid Glass while your finger holds it —
 * exactly how Apple's controls "lift" into glass during interaction.
 */
@Composable
fun GlassToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val colors = Rosa.colors
    var pressed by remember { mutableStateOf(false) }
    val position by animateFloatAsState(if (checked) 1f else 0f, RosaMotion.gel(), label = "toggle")
    val lift by animateFloatAsState(if (pressed) 1f else 0f, spring(1f, 1000f), label = "lift")
    val current by rememberUpdatedState(checked)
    val onChange by rememberUpdatedState(onCheckedChange)
    Box(
        modifier
            .size(width = 58.dp, height = 32.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = waitForUpOrCancellationCompat()
                    pressed = false
                    if (up) {
                        haptics?.toggle(!current)
                        onChange(!current)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val track = androidx.compose.ui.graphics.lerp(colors.fill.copy(alpha = 0.22f), colors.accent, position)
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        }
        val knobW = 38.dp
        val travel = 58.dp - knobW - 4.dp
        val knob = Modifier
            .offset { IntOffset((2.dp + travel * position).roundToPx(), 2.dp.roundToPx()) }
            .size(width = knobW, height = 28.dp)
            .graphicsLayer {
                val s = 1f + 0.22f * lift
                scaleX = s
                scaleY = s
            }
        if (lift > 0.02f) {
            GlassSurface(knob, style = GlassStyle.Lens, cornerRadius = 14.dp, shadow = false) {}
        }
        Box(
            knob
                .graphicsLayer { alpha = 1f - lift }
                .dropShadow(RoundedCornerShape(14.dp), Shadow(radius = 6.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.18f))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
        )
    }
}

/**
 * Slider with a glass knob that lifts while dragged, gel-squashes with velocity, and ticks at
 * each step.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stateDescription: String? = null,
) {
    val haptics = LocalHaptics.current
    val colors = Rosa.colors
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (pressed) 1f else 0f, spring(1f, 1000f), label = "lift")
    val squash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { if (stateDescription != null) this.stateDescription = stateDescription }
            .pointerInput(range, steps) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    tracker.resetTracking()
                    fun update(x: Float) {
                        val f = (x / size.width).coerceIn(0f, 1f)
                        var v = range.start + f * (range.endInclusive - range.start)
                        if (steps > 0) {
                            val step = (range.endInclusive - range.start) / (steps + 1)
                            v = range.start + ((v - range.start) / step).roundToInt() * step
                        }
                        if (v != currentValue) {
                            if (steps > 0) haptics?.tick()
                            onChange(v)
                        }
                    }
                    update(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        update(change.position.x)
                        val v = tracker.calculateVelocity().x / 3000f
                        scope.launch { squash.snapTo(v.coerceIn(-1f, 1f)) }
                        change.consume()
                    }
                    pressed = false
                    scope.launch { squash.animateTo(0f, spring(0.6f, 250f)) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(colors.fill.copy(alpha = 0.25f), cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(colors.accent, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
        val knobW = 34.dp
        val knobX = with(density) { (fraction * (widthPx - knobW.toPx())).roundToInt() }
        val knob = Modifier
            .offset { IntOffset(knobX, 0) }
            .size(width = knobW, height = 24.dp)
            .graphicsLayer {
                val s = 1f + 0.35f * lift
                val q = squash.value
                scaleX = s * (1f + abs(q) * 0.25f)
                scaleY = s * (1f - abs(q) * 0.12f)
            }
        if (lift > 0.02f) GlassSurface(knob, style = GlassStyle.Lens, cornerRadius = 12.dp, shadow = false) {}
        Box(
            knob
                .graphicsLayer { alpha = 1f - lift }
                .dropShadow(RoundedCornerShape(12.dp), Shadow(radius = 6.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.2f))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        )
    }
}

/**
 * Segmented control on glass. The selection is a lens that *flows* to the new option — it
 * stretches toward the target while moving and settles with a gel spring.
 */
@Composable
fun <T> GlassSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val colors = Rosa.colors
    val index = options.indexOf(selected).coerceAtLeast(0)
    val currentIndex by rememberUpdatedState(index)
    val select by rememberUpdatedState(onSelect)
    val position = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) { position.animateTo(index.toFloat(), RosaMotion.gel()) }
    GlassSurface(modifier.height(44.dp), cornerRadius = 22.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight().padding(4.dp)) {
            val segment = maxWidth / options.size
            val stretch = abs(position.value - index).coerceAtMost(1f)
            Box(
                Modifier
                    .offset { IntOffset((segment * position.value).roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = 1f + stretch * 0.35f
                        scaleY = 1f - stretch * 0.12f
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.ink.copy(alpha = if (colors.isLightSky) 0.12f else 0.18f)),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { i, option ->
                    Box(
                        Modifier
                            .width(segment)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Tab
                                this.selected = i == index
                            }
                            .pointerInput(option) {
                                detectTapGestures {
                                    if (i != currentIndex) {
                                        haptics?.tick()
                                        select(option)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(option),
                            style = Rosa.type.label,
                            color = if (i == index) colors.ink else colors.inkSoft,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page dots that behave like droplets: the active one stretches toward the next as you swipe and
 * the neighbours merge through it.
 */
@Composable
fun LiquidPageIndicator(count: Int, position: () -> Float, modifier: Modifier = Modifier) {
    if (count <= 1) return
    val colors = Rosa.colors
    Canvas(modifier.size(width = (count * 14 + 8).dp, height = 10.dp)) {
        val spacing = 14.dp.toPx()
        val r = 3.2.dp.toPx()
        val y = size.height / 2
        val startX = (size.width - spacing * (count - 1)) / 2
        for (i in 0 until count) {
            drawCircle(colors.inkFaint, r, Offset(startX + i * spacing, y))
        }
        // Read in the draw phase: swiping never recomposes the indicator.
        val p = position()
        val base = p.toInt().coerceIn(0, count - 1)
        val frac = p - base
        val headX = startX + (base + (frac * 2f).coerceAtMost(1f)) * spacing
        val tailX = startX + (base + ((frac - 0.5f) * 2f).coerceIn(0f, 1f)) * spacing
        val left = minOf(headX, tailX) - r * 1.25f
        val right = maxOf(headX, tailX) + r * 1.25f
        drawRoundRect(
            colors.ink,
            topLeft = Offset(left, y - r * 1.25f),
            size = Size(right - left, r * 2.5f),
            cornerRadius = CornerRadius(r * 1.25f),
        )
    }
}

private suspend fun AwaitPointerEventScope.waitForUpOrCancellationCompat(): Boolean =
    waitForUpOrCancellation() != null
