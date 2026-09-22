package app.papersky.weather.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.design.LocalScenePalette
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.pressable
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import kotlin.math.max
import kotlin.math.min

/**
 * The next day and a half on one ribbon of paper. Tap an hour — or long-press and scrub — and the
 * whole sky above rehearses that moment: the sun slides, rain starts, lights come on.
 */
@Composable
fun HourlyCard(
    forecast: Forecast,
    fmt: WeatherFormat,
    nowSec: Long,
    preview: Long?,
    onPreview: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val hours = remember(forecast, nowSec / 3600) { forecast.hoursFrom(nowSec, 36) }
    if (hours.size < 2) return
    val density = LocalDensity.current
    val colW = 58.dp
    val colPx = with(density) { colW.toPx() }
    val h = rememberHaptics()
    val onPreviewNow by rememberUpdatedState(onPreview)
    var lastIndex by remember { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()
    val glyphs = remember { GlyphRenderer() }
    val clock = LocalSceneClock.current
    val palette = LocalScenePalette.current
    val glyphColors = remember(palette) { palette?.let { GlyphColors.from(it, onPaper = true) } }
    // Snowy palettes paint precipitation white; on paper we need a readable blue.
    val rainInk = glyphColors?.let { androidx.compose.ui.graphics.Color(it.rain) } ?: colors.precip
    val start = hours.first().time
    val selectedX = preview?.let { ((it - start) / 3600f) }
    val selection by animateFloatAsState(selectedX ?: -1f, spring(stiffness = 700f), label = "sel")
    val nowText = stringResource(R.string.widget_now)
    val type = Paper.type
    val summary = hours.take(12).joinToString { "${fmt.hour(it.time)} ${fmt.temp(it.temperature)}" }

    PaperCard(modifier, seed = 21, tilt = -0.4f, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.hourly_title))
            Spacer(Modifier.weight(1f))
            if (preview != null) {
                BasicText(
                    stringResource(R.string.back_to_now),
                    Modifier.pressable({ h.confirm(); onPreviewNow(null) }).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = Paper.type.caption.copy(color = colors.accent),
                )
            } else {
                BasicText(stringResource(R.string.hourly_hint), style = Paper.type.caption.copy(color = colors.paperInkSoft))
            }
        }
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .padding(horizontal = 12.dp)
                    .width(colW * hours.size)
                    .height(178.dp)
                    .semantics { contentDescription = summary }
                    .pointerInput(hours) {
                        detectTapGestures { pos ->
                            val i = (pos.x / colPx).toInt().coerceIn(0, hours.lastIndex)
                            h.tick()
                            onPreviewNow(if (i == 0) null else hours[i].time)
                        }
                    }
                    .pointerInput(hours) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                h.dragStart()
                                lastIndex = (pos.x / colPx).toInt()
                                onPreviewNow(start + ((pos.x / colPx) * 3600).toLong())
                            },
                            onDragEnd = { h.gestureEnd() },
                        ) { change, _ ->
                            change.consume()
                            val x = change.position.x.coerceIn(0f, colPx * hours.size - 1)
                            val i = (x / colPx).toInt()
                            if (i != lastIndex) { lastIndex = i; h.tick() }
                            // Continuous time: the scene moves smoothly between hours.
                            onPreviewNow(start + ((x / colPx - 0.5f).coerceAtLeast(0f) * 3600).toLong())
                        }
                    },
            ) {
                val w = colPx
                val temps = hours.map { it.temperature }
                val lo = temps.min()
                val hi = temps.max()
                val span = max(hi - lo, 1.0)
                val curveTop = 96.dp.toPx()
                val curveH = 34.dp.toPx()
                fun yOf(t: Double) = (curveTop + curveH * (1 - (t - lo) / span)).toFloat()

                // Selected column: a lifted strip of paper.
                if (selection >= 0f) {
                    val sx = selection * w
                    drawRoundRect(colors.accent.copy(alpha = 0.12f), Offset(sx, 0f), Size(w, size.height), CornerRadius(14.dp.toPx()))
                    drawLine(colors.accent, Offset(sx + w / 2, 4.dp.toPx()), Offset(sx + w / 2, size.height - 4.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }

                // Day boundaries.
                hours.forEachIndexed { i, hour ->
                    if (i > 0 && fmt.localDate(hour.time) != fmt.localDate(hours[i - 1].time)) {
                        val x = i * w
                        drawLine(colors.paperInk.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
                        val label = measurer.measure(fmt.weekdayShort(hour.time + 60), type.hand.copy(fontSize = 15.sp, color = colors.accent))
                        drawText(label, topLeft = Offset(x + 4.dp.toPx(), size.height - label.size.height - 2.dp.toPx()))
                    }
                }

                // Temperature curve with a soft paper wash beneath.
                val pts = hours.mapIndexed { i, hr -> Offset(i * w + w / 2, yOf(hr.temperature)) }
                val curve = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[max(i - 1, 0)]
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val p3 = pts[min(i + 2, pts.size - 1)]
                        cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
                    }
                }
                val wash = Path().apply {
                    addPath(curve)
                    lineTo(pts.last().x, curveTop + curveH + 14.dp.toPx())
                    lineTo(pts.first().x, curveTop + curveH + 14.dp.toPx())
                    close()
                }
                drawPath(wash, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.18f), colors.accent.copy(alpha = 0f)), startY = curveTop, endY = curveTop + curveH + 14.dp.toPx()))
                drawPath(curve, colors.accent, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))

                val t = clock.seconds.floatValue
                hours.forEachIndexed { i, hour ->
                    val cx = i * w + w / 2
                    val label = measurer.measure(
                        if (i == 0) nowText else fmt.hour(hour.time),
                        TextStyle(fontFamily = type.caption.fontFamily, fontWeight = if (i == 0) type.bodyStrong.fontWeight else type.caption.fontWeight, fontSize = 12.sp, color = if (i == 0) colors.paperInk else colors.paperInkSoft, textAlign = TextAlign.Center),
                    )
                    drawText(label, topLeft = Offset(cx - label.size.width / 2, 4.dp.toPx()))
                    glyphColors?.let { gc ->
                        val g = Glyph.of(Condition.fromWmo(hour.code), hour.isDay)
                        val gs = 30.dp.toPx()
                        drawIntoCanvas { glyphs.draw(it.nativeCanvas, g, cx - gs / 2, 28.dp.toPx(), gs, gc, t + i * 0.4f) }
                    }
                    val p = pts[i]
                    drawCircle(colors.paper, 4.dp.toPx(), p)
                    drawCircle(colors.accent, 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    val temp = measurer.measure(fmt.temp(hour.temperature), type.number.copy(fontSize = 15.sp, color = colors.paperInk))
                    drawText(temp, topLeft = Offset(cx - temp.size.width / 2, p.y - temp.size.height - 5.dp.toPx()))

                    // Precipitation: a little bar and its chance.
                    if (hour.precipProbability >= 10) {
                        val barMax = 18.dp.toPx()
                        val bh = max(3.dp.toPx(), barMax * hour.precipProbability / 100f)
                        val by = 162.dp.toPx() - bh
                        drawRoundRect(rainInk.copy(alpha = 0.25f + hour.precipProbability / 180f), Offset(cx - 5.dp.toPx(), by), Size(10.dp.toPx(), bh), CornerRadius(4.dp.toPx()))
                        if (hour.precipProbability >= 30) {
                            val pct = measurer.measure("${hour.precipProbability}%", type.caption.copy(fontSize = 10.sp, color = rainInk))
                            drawText(pct, topLeft = Offset(cx - pct.size.width / 2, by - pct.size.height - 1.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}
