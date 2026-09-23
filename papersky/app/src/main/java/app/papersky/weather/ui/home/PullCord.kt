package app.papersky.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperFibres
import app.papersky.weather.design.beechBead
import app.papersky.weather.design.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pull-to-refresh as a lamp cord hanging from the top edge. Drag the bead down: the string
 * stretches with rubber-band resistance and ticks under the finger; past the notch it clicks,
 * and on release it springs back and swings while the sky is being fetched.
 */
@Composable
fun PullCord(refreshing: Boolean, onPull: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val h = rememberHaptics()
    val engine = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    val swing = remember { Animatable(0f) }
    val restLen = with(density) { 58.dp.toPx() }
    val threshold = with(density) { 92.dp.toPx() }
    val maxPull = with(density) { 150.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    var lastTick by remember { mutableIntStateOf(0) }
    val onPullNow by rememberUpdatedState(onPull)

    LaunchedEffect(refreshing) {
        if (refreshing) {
            while (true) {
                swing.animateTo(12f, tween(650, easing = EaseInOutSine))
                swing.animateTo(-12f, tween(650, easing = EaseInOutSine))
            }
        } else {
            swing.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 40f))
        }
    }

    Box(
        modifier
            .size(64.dp, 250.dp)
            .semantics {
                contentDescription = label
                onClick { onPullNow(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    // A tap gives the bead a little nudge.
                    h.softTick()
                    scope.launch {
                        swing.animateTo(18f, spring(stiffness = 300f))
                        swing.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 30f))
                    }
                })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { h.dragStart(); armed = false; lastTick = 0 },
                    onDragEnd = {
                        if (armed) {
                            engine.cordRelease()
                            onPullNow()
                        } else {
                            h.gestureEnd()
                        }
                        armed = false
                        scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 260f)) }
                        scope.launch {
                            swing.animateTo(if (swing.value >= 0) -16f else 16f, spring(stiffness = 200f))
                            if (!refreshing) swing.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 30f))
                        }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.3f)) } },
                ) { change, dy ->
                    change.consume()
                    val resistance = 1f - (pull.value / maxPull) * 0.7f
                    val next = (pull.value + dy * resistance).coerceIn(0f, maxPull)
                    scope.launch { pull.snapTo(next) }
                    val step = (next / with(density) { 14.dp.toPx() }).toInt()
                    if (step != lastTick) { lastTick = step; h.softTick() }
                    val nowArmed = next >= threshold
                    if (nowArmed && !armed) h.threshold()
                    armed = nowArmed
                }
            },
    ) {
        val colors = Paper.colors
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val len = restLen + pull.value
            rotate(swing.value, Offset(cx, 0f)) {
                // Twisted cotton string.
                drawLine(colors.onSky.copy(alpha = 0.55f), Offset(cx, 0f), Offset(cx, len), 2.dp.toPx(), StrokeCap.Round)
                var y = 6.dp.toPx()
                while (y < len - 6.dp.toPx()) {
                    drawLine(colors.onSky.copy(alpha = 0.25f), Offset(cx - 1.2.dp.toPx(), y), Offset(cx + 1.2.dp.toPx(), y + 3.dp.toPx()), 1.dp.toPx())
                    y += 6.dp.toPx()
                }
                // Wooden bead with a paper tag.
                val r = 10.dp.toPx()
                beechBead(Offset(cx, len + r), r)
                val tagTop = len + r * 2 + 3.dp.toPx()
                val tag = Path().apply {
                    moveTo(cx - 9.dp.toPx(), tagTop + 4.dp.toPx())
                    lineTo(cx, tagTop)
                    lineTo(cx + 9.dp.toPx(), tagTop + 4.dp.toPx())
                    lineTo(cx + 9.dp.toPx(), tagTop + 24.dp.toPx())
                    lineTo(cx - 9.dp.toPx(), tagTop + 24.dp.toPx())
                    close()
                }
                translate(0.8.dp.toPx(), 2.dp.toPx()) { drawPath(tag, Color.Black.copy(alpha = 0.18f)) }
                drawPath(tag, colors.paper)
                drawPath(tag, PaperFibres, alpha = 0.8f)
                drawPath(tag, colors.paperInk.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
                // Refresh swirl printed on the tag; it spins as you pull.
                val a = pull.value / threshold * 300f
                val c = Offset(cx, tagTop + 14.dp.toPx())
                val rr = 4.5.dp.toPx()
                for (k in 0 until 10) {
                    val t0 = Math.toRadians((a + k * 27).toDouble())
                    val t1 = Math.toRadians((a + k * 27 + 18).toDouble())
                    drawLine(
                        (if (armed || refreshing) colors.accent else colors.paperInk).copy(alpha = 0.3f + k * 0.07f),
                        Offset(c.x + rr * cos(t0).toFloat(), c.y + rr * sin(t0).toFloat()),
                        Offset(c.x + rr * cos(t1).toFloat(), c.y + rr * sin(t1).toFloat()),
                        1.6.dp.toPx(), StrokeCap.Round,
                    )
                }
            }
        }
    }
}
