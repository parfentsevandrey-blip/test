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
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.Label
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.PaperRule
import app.papersky.weather.design.pressable
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

/** Ten days on a taped card; tap a day and it unfolds like a folded note. */
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

    PaperCard(modifier, seed = 34, tilt = 0.5f, tape = true, wiggleOnTap = false) {
        Label(stringResource(R.string.daily_title))
        Spacer(Modifier.height(6.dp))
        days.forEachIndexed { index, day ->
            val open = expanded == day.date
            val arrow = animateFloatAsState(if (open) 180f else 0f, Motion.snap(), label = "arrow")
            Column(
                Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (open) "expanded" else "collapsed" }
                    .pressable({
                        h.toggle(!open)
                        expanded = if (open) -1L else day.date
                    }, haptic = false, pressed = 0.985f),
            ) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(92.dp)) {
                        BasicText(
                            fmt.dayName(day.date + 43_200, nowSec),
                            style = Paper.type.bodyStrong.copy(color = colors.paperInk, fontWeight = FontWeight(if (index == 0) 800 else 700)),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                        )
                        BasicText(fmt.date(day.date + 43_200), style = Paper.type.caption.copy(color = colors.paperInkSoft), maxLines = 1)
                    }
                    // Only today's glyph moves; the rest stay still (few live canvases at a time).
                    GlyphIcon(Glyph.of(Condition.fromWmo(day.code), true), size = 30.dp, animate = index == 0)
                    BasicText(
                        if (day.precipProbability >= 20) "${day.precipProbability}%" else "",
                        Modifier.width(42.dp).padding(start = 4.dp),
                        style = Paper.type.caption.copy(color = colors.rainInk),
                    )
                    BasicText(fmt.temp(day.tempMin), Modifier.width(40.dp), style = Paper.type.number.copy(color = colors.paperInkSoft, textAlign = TextAlign.End))
                    RangeStrip(day, lo, hi, if (index == 0) currentTemp else null, Modifier.weight(1f).padding(horizontal = 10.dp).height(10.dp))
                    BasicText(fmt.temp(day.tempMax), Modifier.width(40.dp), style = Paper.type.number.copy(color = colors.paperInk))
                    Canvas(Modifier.width(14.dp).height(14.dp).graphicsLayer { rotationZ = arrow.value }) {
                        val c = colors.paperInkSoft
                        drawLine(c, Offset(size.width * 0.2f, size.height * 0.4f), Offset(size.width / 2, size.height * 0.65f), 1.6.dp.toPx(), StrokeCap.Round)
                        drawLine(c, Offset(size.width / 2, size.height * 0.65f), Offset(size.width * 0.8f, size.height * 0.4f), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
                AnimatedVisibility(
                    open,
                    enter = expandVertically(Motion.fold()) + fadeIn(tween(120)),
                    exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(tween(160)),
                ) {
                    // Unfolds like a folded note: hinged at its top edge (§9 `fold`).
                    val fold by transition.animateFloat(transitionSpec = { Motion.fold() }, label = "fold") { if (it == EnterExitState.Visible) 0f else -88f }
                    DayDetails(
                        day, fmt,
                        onPreview = { onPreviewDay(day.date + 13 * 3600) },
                        modifier = Modifier.graphicsLayer {
                            rotationX = fold
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            cameraDistance = 18f * density
                        },
                    )
                }
            }
            if (index < days.lastIndex) PaperRule()
        }
    }
}

/** A strip of paint from the cold end to the warm end of the ten days; a dot marks now. */
@Composable
private fun RangeStrip(day: Day, lo: Double, hi: Double, current: Double?, modifier: Modifier) {
    val colors = Paper.colors
    val span = (hi - lo).coerceAtLeast(1.0)
    Canvas(modifier.semantics { contentDescription = "" }) {
        val r = size.height / 2
        drawRoundRect(colors.paperInk.copy(alpha = 0.08f), cornerRadius = CornerRadius(r))
        val x0 = ((day.tempMin - lo) / span * size.width).toFloat()
        val x1 = ((day.tempMax - lo) / span * size.width).toFloat().coerceAtLeast(x0 + size.height)
        drawRoundRect(
            Brush.horizontalGradient(listOf(tempColor(day.tempMin), tempColor(day.tempMax)), startX = x0, endX = x1),
            topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = CornerRadius(r),
        )
        if (current != null) {
            val cx = ((current - lo) / span * size.width).toFloat().coerceIn(x0 + r, x1 - r)
            drawCircle(colors.paper, r * 1.25f, Offset(cx, r))
            drawCircle(colors.paperInk, r * 0.6f, Offset(cx, r))
        }
    }
}

@Composable
private fun DayDetails(day: Day, fmt: WeatherFormat, onPreview: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (day.sunrise > 0) Stat(Glyph.Sunrise, fmt.time(day.sunrise))
            if (day.sunset > 0) Stat(Glyph.Sunset, fmt.time(day.sunset))
            Stat(Glyph.Wind, "${fmt.wind(day.windMax)} · ${fmt.compass(day.windDirection)}", rotation = (day.windDirection + 180f) % 360f)
            Stat(Glyph.Umbrella, fmt.precip(day.precipSum))
            if (day.uvMax >= 1) Stat(Glyph.Uv, "UV ${day.uvMax.roundToInt()}")
            if (day.daylightSeconds > 0) Stat(Glyph.Sun, fmt.duration(day.daylightSeconds.toLong()))
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            stringResource(R.string.show_in_sky),
            Modifier.pressable(onPreview).padding(vertical = 4.dp),
            style = Paper.type.bodyStrong.copy(color = colors.accent),
        )
    }
}

@Composable
private fun Stat(glyph: Glyph, text: String, rotation: Float = 0f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(glyph, size = 20.dp, animate = false, rotation = rotation)
        Spacer(Modifier.width(4.dp))
        BasicText(text, style = Paper.type.caption.copy(color = Paper.colors.paperInk))
    }
}
