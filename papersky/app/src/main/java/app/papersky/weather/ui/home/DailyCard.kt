package app.papersky.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.Day
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.DeckleShape
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.Ink
import app.papersky.weather.design.Label
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperSheet
import app.papersky.weather.design.Stock
import app.papersky.weather.design.TagShape
import app.papersky.weather.design.eyelet
import app.papersky.weather.design.material
import app.papersky.weather.design.pressable
import app.papersky.weather.design.pressed
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.Glyph
import kotlin.math.roundToInt

/** Temperature → colour on a cold-to-warm paint strip. */
fun tempColor(celsius: Double): Color {
    val stops = listOf(
        -20.0 to Color(0xFF6C7FD8), -5.0 to Color(0xFF7FB2E5), 5.0 to Color(0xFF8CC7B5),
        15.0 to Color(0xFFE9C46A), 25.0 to Color(0xFFEF8F4E), 35.0 to Color(0xFFD9483B),
    )
    if (celsius <= stops.first().first) return stops.first().second
    if (celsius >= stops.last().first) return stops.last().second
    val i = stops.indexOfLast { it.first <= celsius }
    val (t0, c0) = stops[i]
    val (t1, c1) = stops[i + 1]
    val f = ((celsius - t0) / (t1 - t0)).toFloat()
    return Color(
        red = c0.red + (c1.red - c0.red) * f,
        green = c0.green + (c1.green - c0.green) * f,
        blue = c0.blue + (c1.blue - c0.blue) * f,
    )
}

/** Ten days on an index card: a red heading rule, a blue rule under each day; a day unfolds a note. */
@Composable
fun DailyCard(
    forecast: Forecast,
    fmt: WeatherFormat,
    nowSec: Long,
    currentTemp: Double?,
    onPreviewDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val days = forecast.daysFrom(nowSec, 10)
    if (days.isEmpty()) return
    val lo = days.minOf { it.tempMin }
    val hi = days.maxOf { it.tempMax }
    var expanded by rememberSaveable { mutableLongStateOf(-1L) }
    val h = rememberHaptics()
    val blueRule = Color(0xFF9DB6D8).copy(alpha = 0.5f)
    val redRule = Color(0xFFD9655B).copy(alpha = 0.75f)

    PaperSheet(modifier, stock = Stock.IndexCard, seed = 29, pin = true, contentPadding = PaddingValues(top = 18.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(redRule, Offset(0f, size.height), Offset(size.width, size.height), 1.3.dp.toPx()) }
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        ) { Label(stringResource(R.string.daily_title)) }
        days.forEachIndexed { index, day ->
            val open = expanded == day.date
            val arrow = animateFloatAsState(if (open) 180f else 0f, Motion.snap(), label = "arrow")
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(blueRule, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
                    .semantics { stateDescription = if (open) "expanded" else "collapsed" }
                    .pressable({
                        h.toggle(!open)
                        expanded = if (open) -1L else day.date
                    }, haptic = false, pressed = 0.985f)
                    .padding(horizontal = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(88.dp)) {
                        BasicText(
                            fmt.dayName(day.date + 43_200, nowSec),
                            style = Paper.type.bodyStrong.copy(color = colors.paperInk, fontWeight = FontWeight(if (index == 0) 800 else 700)).pressed(),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                        )
                        BasicText(fmt.date(day.date + 43_200), style = Paper.type.caption.copy(color = colors.paperInkSoft).pressed(), maxLines = 1)
                    }
                    // Only today's sticker is alive; the rest are still (§14: few live canvases).
                    GlyphIcon(Glyph.of(Condition.fromWmo(day.code), true), size = 34.dp, animate = index == 0, rotation = ((day.date / 86_400) % 7 - 3).toFloat() * 1.5f)
                    BasicText(
                        if (day.precipProbability >= 20) "${day.precipProbability}%" else "",
                        Modifier.width(40.dp).padding(start = 4.dp),
                        style = Paper.type.caption.copy(color = Ink.RainOnPaper, fontWeight = FontWeight(800)).pressed(),
                    )
                    BasicText(fmt.temp(day.tempMin), Modifier.width(36.dp), style = Paper.type.number.copy(color = colors.paperInkSoft, textAlign = TextAlign.End).pressed())
                    RangeStrip(day, lo, hi, if (index == 0) currentTemp else null, Modifier.weight(1f).padding(horizontal = 9.dp).height(10.dp))
                    BasicText(fmt.temp(day.tempMax), Modifier.width(36.dp), style = Paper.type.number.copy(color = colors.paperInk).pressed())
                    Canvas(Modifier.width(14.dp).height(14.dp).graphicsLayer { rotationZ = arrow.value }) {
                        val c = colors.paperInkSoft
                        drawLine(c, Offset(size.width * 0.2f, size.height * 0.38f), Offset(size.width / 2, size.height * 0.64f), 1.6.dp.toPx(), StrokeCap.Round)
                        drawLine(c, Offset(size.width / 2, size.height * 0.64f), Offset(size.width * 0.8f, size.height * 0.38f), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
                AnimatedVisibility(
                    open,
                    enter = expandVertically(Motion.fold()) + fadeIn(tween(120)),
                    exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(tween(160)),
                ) {
                    // Unfolds like a folded note: hinged at its top edge (§9 `fold`).
                    val fold by transition.animateFloat(transitionSpec = { Motion.fold() }, label = "fold") { if (it == EnterExitState.Visible) 0f else -88f }
                    DayNote(
                        day, fmt, seed = index,
                        onPreview = { onPreviewDay(day.date + 13 * 3600) },
                        modifier = Modifier.graphicsLayer {
                            rotationX = fold
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            cameraDistance = 18f * density
                        },
                    )
                }
            }
        }
    }
}

/** A strip of watercolour on the card: cold to warm, with soft pooled ends. */
@Composable
private fun RangeStrip(day: Day, lo: Double, hi: Double, current: Double?, modifier: Modifier) {
    val colors = Paper.colors
    val span = (hi - lo).coerceAtLeast(1.0)
    Canvas(modifier.semantics { contentDescription = "" }) {
        val r = size.height / 2
        // The pencil guide the paint was laid along.
        drawLine(colors.paperInk.copy(alpha = 0.12f), Offset(0f, r), Offset(size.width, r), 1.dp.toPx(), StrokeCap.Round)
        val x0 = ((day.tempMin - lo) / span * size.width).toFloat()
        val x1 = ((day.tempMax - lo) / span * size.width).toFloat().coerceAtLeast(x0 + size.height)
        val paint = Brush.horizontalGradient(listOf(tempColor(day.tempMin), tempColor(day.tempMax)), startX = x0, endX = x1)
        drawRoundRect(paint, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = CornerRadius(r), alpha = 0.85f)
        // Pigment pools at the edges of a wash.
        drawRoundRect(paint, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()), alpha = 0.9f)
        drawRoundRect(Stock.Cotton.brush, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = CornerRadius(r), alpha = 0.6f)
        if (current != null) {
            val cx = ((current - lo) / span * size.width).toFloat().coerceIn(x0 + r, x1 - r)
            drawLine(Ink.RedPencil, Offset(cx, -3.dp.toPx()), Offset(cx, size.height + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** The unfolded note: a scrap of cotton paper with kraft tags for the day's numbers. */
@Composable
private fun DayNote(day: Day, fmt: WeatherFormat, seed: Int, onPreview: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp)
            .material(Stock.Cotton, DeckleShape(seed = 300 + seed, corner = 8.dp), level = 1)
            .padding(14.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (day.sunrise > 0) Stat(Glyph.Sunrise, fmt.time(day.sunrise))
            if (day.sunset > 0) Stat(Glyph.Sunset, fmt.time(day.sunset))
            Stat(Glyph.Wind, "${fmt.wind(day.windMax)} · ${fmt.compass(day.windDirection)}", rotation = (day.windDirection + 180f) % 360f)
            Stat(Glyph.Umbrella, fmt.precip(day.precipSum))
            if (day.uvMax >= 1) Stat(Glyph.Uv, "UV ${day.uvMax.roundToInt()}")
            if (day.daylightSeconds > 0) Stat(Glyph.Sun, fmt.duration(day.daylightSeconds.toLong()))
        }
        Spacer(Modifier.height(12.dp))
        PaperButton(stringResource(R.string.show_in_sky), onPreview, Modifier.fillMaxWidth())
    }
}

/** A kraft tag with an eyelet, a still sticker and a number. */
@Composable
private fun Stat(glyph: Glyph, text: String, rotation: Float = 0f) {
    Row(
        Modifier
            .material(Stock.Kraft, TagShape(8.dp), level = 1)
            .drawBehind { eyelet(Offset(9.dp.toPx(), size.height / 2)) }
            .padding(start = 19.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, size = 22.dp, rotation = rotation, animate = false)
        Spacer(Modifier.width(5.dp))
        BasicText(text, style = Paper.type.caption.copy(color = Color(0xFF3B2A1A), fontWeight = FontWeight(700)).pressed())
    }
}
