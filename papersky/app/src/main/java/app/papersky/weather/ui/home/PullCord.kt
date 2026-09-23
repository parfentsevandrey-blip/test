package app.papersky.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.paperThumb
import app.papersky.weather.design.rememberHaptics
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Pull-to-refresh as a pendant (DESIGN_DOCTRINE §8): a paper disc hanging on a hairline thread
 * from the top edge. Drag it down: the thread stretches with rubber-band resistance and ticks
 * under the finger; past the notch it clicks, and on release it springs back while the arc
 * printed on the disc turns until the sky has been fetched.
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

    val turn = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            launch {
                while (true) {
                    swing.animateTo(4f, tween(1100, easing = EaseInOutSine))
                    swing.animateTo(-4f, tween(1100, easing = EaseInOutSine))
                }
            }
            while (true) {
                turn.animateTo(turn.value + 360f, tween(1400, easing = LinearEasing))
            }
        } else {
            swing.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 40f))
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
                    // A tap gives the disc a little nudge.
                    h.softTick()
                    scope.launch {
                        swing.animateTo(6f, spring(stiffness = 300f))
                        swing.animateTo(0f, Motion.string())
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
                        scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
                        scope.launch {
                            swing.animateTo(if (swing.value >= 0) -5f else 5f, spring(stiffness = 200f))
                            if (!refreshing) swing.animateTo(0f, Motion.string())
                        }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.6f)) } },
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
                // A hairline thread.
                drawLine(colors.onSky.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, len), 1.dp.toPx())
                // The paper disc, with an arc printed on it that turns as you pull.
                val r = 13.dp.toPx()
                val c = Offset(cx, len + r)
                paperThumb(c, r, pull.value / maxPull, colors.paper, colors.paperInk, colors.shadow)
                val a = pull.value / threshold * 300f + turn.value
                val rr = 5.5.dp.toPx()
                val ink = if (armed || refreshing) colors.accent else colors.paperInk
                drawArc(ink, a, 270f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                val end = Math.toRadians((a + 270f).toDouble())
                val tip = Offset(c.x + rr * cos(end).toFloat(), c.y + rr * sin(end).toFloat())
                drawCircle(ink, 1.4.dp.toPx(), tip)
            }
        }
    }
}
