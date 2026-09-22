package app.papersky.weather.ui.home

import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Hour
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.Ink
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.design.LocalScenePalette
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperColors
import app.papersky.weather.design.PaperSheet
import app.papersky.weather.design.PaperType
import app.papersky.weather.design.Stock
import app.papersky.weather.design.graphRuling
import app.papersky.weather.design.pressable
import app.papersky.weather.design.pressed
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.design.stickerImage
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val ColW = 56.dp
private val ChartH = 168.dp
private val GlyphTop = 26.dp
private val GlyphSize = 36.dp

/**
 * The next day and a half on a strip of graph paper. Tap an hour — or long-press and scrub — and
 * a slip of tracing paper slides to it while the whole sky above rehearses that moment.
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
    val colPx = with(density) { ColW.toPx() }
    val h = rememberHaptics()
    val onPreviewNow by rememberUpdatedState(onPreview)
    var lastIndex by remember { mutableIntStateOf(-1) }
    val start = hours.first().time
    val selection = animateFloatAsState(preview?.let { (it - start) / 3600f } ?: -1f, Motion.snap(), label = "sel")
    val summary = remember(hours, fmt) { hours.take(12).joinToString { "${fmt.hour(it.time)} ${fmt.temp(it.temperature)}" } }
    val scroll = rememberScrollState()
    // The pencil draws the curve once, when the ribbon is first laid down.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1300)) }

    PaperSheet(
        modifier,
        stock = Stock.Graph,
        seed = 17,
        tape = true,
        contentPadding = PaddingValues(vertical = 16.dp),
        decoration = { graphRuling() },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.hourly_title))
            Spacer(Modifier.weight(1f))
            if (preview != null) {
                BasicText(
                    stringResource(R.string.back_to_now),
                    Modifier.pressable({ h.confirm(); onPreviewNow(null) }).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = Paper.type.caption.copy(color = Ink.RedPencil, fontWeight = FontWeight(800)).pressed(),
                )
            } else {
                BasicText(stringResource(R.string.hourly_hint), style = Paper.type.caption.copy(color = colors.paperInkSoft).pressed())
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .width(ColW * hours.size)
                    .height(ChartH)
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
                                onPreviewNow(start + ((pos.x / colPx - 0.5f).coerceAtLeast(0f) * 3600).toLong())
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
                HourlyChart(hours, fmt, colors, Paper.type, stringResource(R.string.widget_now), reveal)
                StickerMotion(hours, scroll, reveal)
                // The tracing-paper slip: its own tiny layer, the only thing redrawn while scrubbing.
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val s = selection.value
                        if (s < 0f) return@drawBehind
                        val x = s * colPx + 1.dp.toPx()
                        val w = colPx - 2.dp.toPx()
                        val r = CornerRadius(4.dp.toPx())
                        drawRoundRect(Ink.Shadow.copy(alpha = 0.1f), Offset(x + 0.6.dp.toPx(), 1.5.dp.toPx()), Size(w, size.height), r)
                        drawRoundRect(Color.White.copy(alpha = 0.42f), Offset(x, 0f), Size(w, size.height), r)
                        drawRoundRect(Stock.Cotton.brush, Offset(x, 0f), Size(w, size.height), r, alpha = 0.3f)
                        drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(x, 0f), Size(w, size.height), r, style = Stroke(0.8.dp.toPx()))
                        // A red-pencil tick at the top, like a mark made on the slip.
                        val cx = x + w / 2
                        drawLine(Ink.RedPencil.copy(alpha = 0.85f), Offset(cx - 6.dp.toPx(), 2.dp.toPx()), Offset(cx + 6.dp.toPx(), 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    },
                )
            }
        }
    }
}

/**
 * The stickers' moving parts (rays, drops, flakes), drawn per frame and only for the columns in
 * view; the stickers themselves are baked into the chart.
 */
@Composable
private fun StickerMotion(hours: List<Hour>, scroll: ScrollState, reveal: Animatable<Float, *>) {
    val palette = LocalScenePalette.current ?: return
    val gc = remember(palette) { GlyphColors.from(palette, onPaper = true) }
    val glyphs = remember(hours) { hours.map { Glyph.of(Condition.fromWmo(it.code), it.isDay) } }
    if (glyphs.none { it in GlyphRenderer.ANIMATED }) return
    val clock = LocalSceneClock.current
    val renderer = remember { GlyphRenderer() }
    Box(
        Modifier.fillMaxWidth().height(ChartH).drawBehind {
            if (reveal.value < 1f) return@drawBehind
            val w = ColW.toPx()
            val px = GlyphSize.toPx()
            val art = px * GlyphRenderer.STICKER_ART
            val inset = (px - art) / 2
            val pad = 10.dp.toPx()
            val first = ((scroll.value - pad) / w).toInt().coerceAtLeast(0)
            val last = min(glyphs.lastIndex, first + ceil(scroll.viewportSize / w).toInt() + 1)
            val t = clock.seconds.floatValue + 0.01f
            drawIntoCanvas { c ->
                for (i in first..last) {
                    val g = glyphs[i]
                    if (g !in GlyphRenderer.ANIMATED) continue
                    val left = i * w + w / 2 - px / 2 + inset
                    renderer.draw(c.nativeCanvas, g, left, GlyphTop.toPx() + inset, art, gc, t + i * 0.37f, 0f, GlyphRenderer.Pass.Motion)
                }
            }
        },
    )
}

/** Everything static about the ribbon, laid out and measured once. */
@Composable
private fun HourlyChart(hours: List<Hour>, fmt: WeatherFormat, colors: PaperColors, type: PaperType, nowText: String, reveal: Animatable<Float, *>) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val palette = LocalScenePalette.current
    val glyphColors = remember(palette) { palette?.let { GlyphColors.from(it, onPaper = true) } }
    val border = Paper.light.lit(Color(0xFFFFFCF4)).toArgb()
    val pencil = Ink.RedPencil
    val rainInk = Ink.RainOnPaper
    Box(
        Modifier
            .fillMaxWidth()
            .height(ChartH)
            .drawWithCache {
                val w = ColW.toPx()
                val n = hours.size
                val temps = hours.map { it.temperature }
                val lo = temps.min()
                val hi = temps.max()
                val span = max(hi - lo, 1.0)
                val curveTop = 102.dp.toPx()
                val curveH = 30.dp.toPx()
                fun yOf(t: Double) = (curveTop + curveH * (1 - (t - lo) / span)).toFloat()
                val pts = List(n) { Offset(it * w + w / 2, yOf(temps[it])) }
                val curve = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 0 until n - 1) {
                        val p0 = pts[max(i - 1, 0)]
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val p3 = pts[min(i + 2, n - 1)]
                        cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
                    }
                }
                val floor = curveTop + curveH + 18.dp.toPx()
                // Coloured-pencil shading under the curve: diagonal hatching clipped to the area.
                val wash = Path().apply {
                    addPath(curve)
                    lineTo(pts.last().x, floor)
                    lineTo(pts.first().x, floor)
                    close()
                }
                val hatch = Path().apply {
                    var x = -curveH * 2
                    while (x < size.width) {
                        moveTo(x, floor)
                        lineTo(x + (floor - curveTop + 4.dp.toPx()), curveTop - 4.dp.toPx())
                        x += 4.dp.toPx()
                    }
                }
                val washBrush = Brush.verticalGradient(listOf(pencil.copy(alpha = 0.1f), pencil.copy(alpha = 0f)), startY = curveTop, endY = floor)
                val measure = PathMeasure(curve.asAndroidPath(), false)
                val length = measure.length
                val partial = android.graphics.Path()

                val labelStyle = type.caption.copy(fontSize = 12.5.sp, color = colors.paperInkSoft)
                val nowStyle = labelStyle.copy(color = colors.paperInk, fontWeight = FontWeight(800))
                val labels = List(n) { i -> measurer.measure(if (i == 0) nowText else fmt.hour(hours[i].time), if (i == 0) nowStyle else labelStyle) }
                val tempStyle = type.number.copy(fontSize = 15.sp, color = colors.paperInk)
                val tempLabels = List(n) { i -> measurer.measure(fmt.temp(hours[i].temperature), tempStyle) }
                val pctStyle = type.caption.copy(fontSize = 10.5.sp, color = rainInk, fontWeight = FontWeight(800))
                val pct = List(n) { i -> hours[i].precipProbability.takeIf { it >= 30 }?.let { measurer.measure("$it%", pctStyle) } }
                val dayStyle = type.label.copy(fontSize = 10.sp, color = pencil)
                val days = List(n) { i ->
                    if (i > 0 && fmt.localDate(hours[i].time) != fmt.localDate(hours[i - 1].time)) measurer.measure(fmt.weekdayShort(hours[i].time + 60).uppercase(), dayStyle) else null
                }
                val glyphPx = GlyphSize.roundToPx()
                val glyphs = glyphColors?.let { gc ->
                    List(n) { i ->
                        val g = Glyph.of(Condition.fromWmo(hours[i].code), hours[i].isDay)
                        stickerImage(g, glyphPx, gc, animated = g in GlyphRenderer.ANIMATED, border = border)
                    }
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))

                onDrawBehind {
                    val r = reveal.value
                    // "Now" is circled in pencil, as if marked on the paper.
                    drawRoundRect(pencil.copy(alpha = 0.5f), Offset(4.dp.toPx(), 2.dp.toPx()), Size(w - 8.dp.toPx(), 22.dp.toPx()), CornerRadius(11.dp.toPx()), style = Stroke(1.2.dp.toPx()))
                    days.forEachIndexed { i, label ->
                        if (label == null) return@forEachIndexed
                        val x = i * w
                        drawLine(pencil.copy(alpha = 0.45f), Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 1.dp.toPx(), pathEffect = dash)
                        drawText(label, topLeft = Offset(x + 5.dp.toPx(), size.height - label.size.height - 2.dp.toPx()))
                    }
                    clipPath(wash) {
                        drawPath(wash, washBrush, alpha = r)
                        drawPath(hatch, pencil.copy(alpha = 0.16f * r), style = Stroke(0.8.dp.toPx()))
                    }
                    if (r < 1f) {
                        partial.reset()
                        measure.getSegment(0f, length * r, partial, true)
                        drawPencil(partial.asComposePath(), pencil)
                    } else {
                        drawPencil(curve, pencil)
                    }
                    for (i in 0 until n) {
                        // Each column settles in as the pencil reaches it.
                        val a = ((r * n - i) / 2.5f).coerceIn(0f, 1f)
                        if (a <= 0f) continue
                        val cx = i * w + w / 2
                        val lift = (1f - a) * 6.dp.toPx()
                        val label = labels[i]
                        drawText(label, topLeft = Offset(cx - label.size.width / 2, 6.dp.toPx()), alpha = a)
                        glyphs?.let { drawImage(it[i], Offset(cx - glyphPx / 2f, GlyphTop.toPx() + lift), alpha = a) }
                        val p = pts[i]
                        drawCircle(colors.paper, 3.4.dp.toPx(), p, alpha = a)
                        drawCircle(pencil, 3.4.dp.toPx(), p, style = Stroke(1.6.dp.toPx()), alpha = a)
                        val t = tempLabels[i]
                        drawText(t, topLeft = Offset(cx - t.size.width / 2, p.y - t.size.height - 5.dp.toPx() + lift), alpha = a)
                        val chance = hours[i].precipProbability
                        if (chance >= 10) {
                            // A blue-ink column, hatched like pen shading.
                            val barMax = 16.dp.toPx()
                            val bh = max(3.dp.toPx(), barMax * chance / 100f) * a
                            val by = 154.dp.toPx() - bh
                            val bw = 8.dp.toPx()
                            drawRoundRect(rainInk.copy(alpha = 0.12f + chance / 400f), Offset(cx - bw / 2, by), Size(bw, bh), CornerRadius(2.dp.toPx()))
                            var yy = by + 2.dp.toPx()
                            while (yy < by + bh) {
                                drawLine(rainInk.copy(alpha = 0.55f), Offset(cx - bw / 2, yy + 2.dp.toPx()), Offset(cx + bw / 2, yy), 0.9.dp.toPx())
                                yy += 2.6.dp.toPx()
                            }
                            pct[i]?.let { pl -> drawText(pl, topLeft = Offset(cx - pl.size.width / 2, by - pl.size.height - 1.dp.toPx()), alpha = a) }
                        }
                    }
                }
            },
    )
}

/** A coloured-pencil line: a soft wide pass and a crisper core with a little grain. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPencil(path: Path, color: Color) {
    drawPath(path, color.copy(alpha = 0.25f), style = Stroke(3.6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = 0.9f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 0.6.dp.toPx()))))
}

