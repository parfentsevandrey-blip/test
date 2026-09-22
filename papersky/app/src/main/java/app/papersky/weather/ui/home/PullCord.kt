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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.papersky.weather.design.Ink
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.design.Paper
import app.papersky.weather.design.Stock
import app.papersky.weather.design.beechBead
import app.papersky.weather.design.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Pull-to-refresh as a lamp cord hanging from the top edge. Drag the bead down: the string
 * stretches with rubber-band resistance and ticks under the finger; past the notch it clicks,
 * and on release it springs back and swings while the sky is being fetched.
 */
@Composable
fun PullCord(refreshing: Boolean, onPull: () -> Unit, label: String, tag: String, modifier: Modifier = Modifier) {
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
        val measurer = rememberTextMeasurer()
        val handStyle = Paper.type.handSmall.copy(fontSize = 15.sp, color = Ink.Blue)
        val light = Paper.light
        val kraft = light.lit(Stock.Kraft.base)
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val len = restLen + pull.value
            rotate(swing.value, Offset(cx, 0f)) {
                // Twisted cotton string with a shadow on the sky.
                drawLine(Ink.Shadow.copy(alpha = 0.2f), Offset(cx + 1.5.dp.toPx(), 0f), Offset(cx + 1.5.dp.toPx(), len + 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFF1E8D6), Offset(cx, 0f), Offset(cx, len), 2.2.dp.toPx(), StrokeCap.Round)
                var y = 5.dp.toPx()
                while (y < len - 5.dp.toPx()) {
                    drawLine(Color(0xFFB9A889), Offset(cx - 1.1.dp.toPx(), y), Offset(cx + 1.1.dp.toPx(), y + 2.5.dp.toPx()), 0.9.dp.toPx())
                    y += 5.dp.toPx()
                }
                // Beech bead.
                val r = 10.dp.toPx()
                beechBead(Offset(cx, len + r), r)
                // Kraft tag hanging from the bead on a short loop, with a word in blue ink.
                val text = measurer.measure(tag, handStyle.copy(color = if (armed || refreshing) Ink.RedPencil else Ink.Blue))
                val tagW = (text.size.width + 16.dp.toPx()).coerceAtLeast(38.dp.toPx())
                val tagH = 24.dp.toPx()
                val tagTop = len + r * 2 + 6.dp.toPx()
                drawLine(Color(0xFFB9A889), Offset(cx, len + r * 2 - 1.dp.toPx()), Offset(cx, tagTop + 4.dp.toPx()), 1.dp.toPx())
                val shape = Path().apply {
                    moveTo(cx - tagW / 2, tagTop + 6.dp.toPx())
                    lineTo(cx - tagW / 2 + 6.dp.toPx(), tagTop)
                    lineTo(cx + tagW / 2 - 6.dp.toPx(), tagTop)
                    lineTo(cx + tagW / 2, tagTop + 6.dp.toPx())
                    lineTo(cx + tagW / 2, tagTop + tagH)
                    lineTo(cx - tagW / 2, tagTop + tagH)
                    close()
                }
                translate(1.dp.toPx(), 2.5.dp.toPx()) { drawPath(shape, Ink.Shadow.copy(alpha = 0.22f)) }
                drawPath(shape, kraft)
                drawPath(shape, Stock.Kraft.brush)
                drawPath(shape, Color.White.copy(alpha = 0.18f), style = Stroke(0.8.dp.toPx()))
                drawCircle(Color(0xFF3A2A1A).copy(alpha = 0.6f), 1.6.dp.toPx(), Offset(cx, tagTop + 4.dp.toPx()))
                // Spins a little as you pull, like a tag on a string.
                rotate((pull.value / threshold) * -6f, Offset(cx, tagTop + tagH / 2)) {
                    drawText(text, topLeft = Offset(cx - text.size.width / 2, tagTop + (tagH - text.size.height) / 2 + 2.dp.toPx()))
                }
            }
        }
    }
}
