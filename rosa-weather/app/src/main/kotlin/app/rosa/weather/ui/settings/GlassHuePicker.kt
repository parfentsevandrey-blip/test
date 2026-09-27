package app.rosa.weather.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.rememberPressScale
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.GlassTint
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The colour of tinted glass: eight named hues as beads of glass, and every hue between them on a
 * strip of the spectrum below. The whole app's glass flows into the colour as it is picked.
 */
@Composable
internal fun GlassHuePicker(hue: Int, onHue: (Int) -> Unit, modifier: Modifier = Modifier) {
    val names = listOf(
        stringResource(R.string.color_rose),
        stringResource(R.string.color_coral),
        stringResource(R.string.color_amber),
        stringResource(R.string.color_mint),
        stringResource(R.string.color_teal),
        stringResource(R.string.color_azure),
        stringResource(R.string.color_indigo),
        stringResource(R.string.color_lilac),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            GlassTint.Swatches.forEachIndexed { i, swatch ->
                Bead(swatch, names[i], selected = swatch == hue, Modifier.weight(1f)) { onHue(swatch) }
            }
        }
        HueStrip(hue, onHue, nameOf = { h -> GlassTint.Swatches.indexOfFirst { hueDistance(it, h) <= NAMED_WITHIN }.takeIf { it >= 0 }?.let(names::get) })
    }
}

/**
 * A bead of coloured glass: lit from above, deeper at its foot; the chosen one stands proud in a
 * ring. The whole of its share of the row, a full finger tall, takes the touch.
 */
@Composable
private fun Bead(hue: Int, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val ink = Rosa.colors.ink
    val lift by animateFloatAsState(if (selected) 1f else 0f, RosaMotion.gel(), label = "bead")
    var pressed by remember { mutableStateOf(false) }
    val press = rememberPressScale(pressed)
    val color = GlassTint.swatch(hue).toColor()
    Box(
        modifier
            .height(TouchSize)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptics?.tick()
                        onClick()
                    },
                )
            }
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = name
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(BeadSize + 8.dp)
                .graphicsLayer {
                    val s = press * (1f + 0.1f * lift)
                    scaleX = s
                    scaleY = s
                },
        ) { drawBead(color, ink, lift) }
    }
}

private fun DrawScope.drawBead(color: Color, ink: Color, lift: Float) {
    val r = BeadSize.toPx() / 2
    val c = center
    drawCircle(color, r, c)
    // Deeper toward its foot, where the light crosses the most glass.
    drawCircle(Brush.verticalGradient(0.45f to Color.Transparent, 1f to lerp(color, Color.Black, 0.35f), startY = c.y - r, endY = c.y + r), r, c)
    // The light caught on its crown.
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), center = c + Offset(-r * 0.3f, -r * 0.42f), radius = r * 0.62f),
        r,
        c,
    )
    drawCircle(Color.White.copy(alpha = 0.55f), r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
    if (lift > 0.01f) drawCircle(ink.copy(alpha = lift), r + 3.5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
}

/**
 * Every hue on a strip of glass. Its knob, a bead of the colour under it, lifts into a lens while
 * it is dragged, magnifying the spectrum, and ticks as it passes each named colour. The app's glass
 * follows a few times a second (each change is saved) and settles on the hue where it lets go.
 */
@Composable
private fun HueStrip(hue: Int, onHue: (Int) -> Unit, nameOf: (Int) -> String?) {
    val haptics = LocalHaptics.current
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableFloatStateOf(hue.toFloat()) }
    LaunchedEffect(hue) { if (!dragging) shown = hue.toFloat() }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(1f, 1000f), label = "lift")
    val commit by rememberUpdatedState(onHue)
    val degrees = shown.roundToInt()
    val state = nameOf(degrees) ?: stringResource(R.string.glass_hue_value, degrees)
    val label = stringResource(R.string.glass_hue)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(TouchSize)
            .semantics {
                contentDescription = label
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..MAX_HUE)
                setProgress { value ->
                    commit(value.roundToInt().coerceIn(0, MAX_HUE.toInt()))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    haptics?.press()
                    val knob = KnobSize.toPx()
                    fun hueAt(x: Float) = ((x - knob / 2f) / (size.width - knob)).coerceIn(0f, 1f) * MAX_HUE
                    var sent = -1
                    var sentAt = 0L
                    fun follow(x: Float, time: Long, final: Boolean) {
                        val before = shown
                        shown = hueAt(x)
                        // A tick for each named colour the knob passes.
                        if (GlassTint.Swatches.any { (before - it) * (shown - it) <= 0f && before != shown }) haptics?.tick()
                        val h = shown.roundToInt()
                        if (h != sent && (final || time - sentAt >= FOLLOW_MILLIS)) {
                            sent = h
                            sentAt = time
                            commit(h)
                        }
                    }
                    follow(down.position.x, down.uptimeMillis, final = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        follow(change.position.x, change.uptimeMillis, final = !change.pressed)
                        if (!change.pressed) break
                    }
                    dragging = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The track: the spectrum seen through a strip of glass, lit along its top.
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(TrackHeight)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Brush.horizontalGradient(Spectrum), cornerRadius = r)
            drawRoundRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.4f), 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f)), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = 0.55f), cornerRadius = r, style = Stroke(1.dp.toPx()))
        }
        val travel = maxWidth - KnobSize
        val knob = Modifier
            .offset(x = travel * (shown / MAX_HUE))
            .size(KnobSize)
            .graphicsLayer {
                val s = 1f + 0.3f * lift
                scaleX = s
                scaleY = s
            }
        if (lift > 0.02f) GlassSurface(knob, style = GlassStyle.Lens, cornerRadius = KnobSize / 2, shadow = false) {}
        Box(
            knob
                .graphicsLayer { alpha = 1f - lift }
                .dropShadow(CircleShape, Shadow(radius = 6.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.22f))
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(GlassTint.swatch(degrees).toColor()),
        )
    }
}

/** Degrees between two hues, the short way round. */
private fun hueDistance(a: Int, b: Int): Int = abs(a - b).let { minOf(it, 360 - it) }

/** The whole spectrum, as the beads show their hues. */
private val Spectrum = (0..12).map { GlassTint.swatch(it * 30).toColor() }

private const val MAX_HUE = 359f

/** A hue this close to a named one is called by its name. */
private const val NAMED_WITHIN = 4

/** How often the app's glass follows the knob while it is dragged. */
private const val FOLLOW_MILLIS = 90L

private val BeadSize = 30.dp
private val KnobSize = 28.dp
private val TrackHeight = 14.dp

/** A finger's worth: the least a control takes the touch over, whatever it looks like. */
private val TouchSize = 48.dp
