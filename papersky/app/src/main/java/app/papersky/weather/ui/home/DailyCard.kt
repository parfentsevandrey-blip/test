package app.papersky.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.papersky.weather.R
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.Day
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.Hairline
import app.papersky.weather.design.Label
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.PillShape
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
    val rain = if (colors.isNight) Color(0xFF9CC4EC) else Color(0xFF3F77B3)

    PaperCard(modifier) {
        Label(stringResource(R.string.daily_title))
        Spacer(Modifier.height(8.dp))
        days.forEachIndexed { index, day ->
            val open = expanded == day.date
            val arrow = animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "arrow")
            Column(
                Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (open) "expanded" else "collapsed" }
                    .pressable({
                        h.toggle(!open)
                        expanded = if (open) -1L else day.date
                    }, haptic = false, pressed = 0.985f),
            ) {
                Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(92.dp)) {
                        BasicText(
                            fmt.dayName(day.date + 43_200, nowSec),
                            style = Paper.type.bodyStrong.copy(color = colors.paperInk, fontWeight = FontWeight(if (index == 0) 800 else 700)),
                            maxLines = 1,
                        )
                        BasicText(fmt.date(day.date + 43_200), style = Paper.type.caption.copy(color = colors.paperInkSoft), maxLines = 1)
                    }
                    GlyphIcon(Glyph.of(Condition.fromWmo(day.code), true), size = 30.dp)
                    BasicText(
                        if (day.precipProbability >= 20) "${day.precipProbability}%" else "",
                        Modifier.width(42.dp).padding(start = 4.dp),
                        style = Paper.type.caption.copy(color = rain, fontWeight = FontWeight(700)),
                    )
                    BasicText(fmt.temp(day.tempMin), Modifier.width(38.dp), style = Paper.type.number.copy(color = colors.paperInkSoft, textAlign = TextAlign.End))
                    RangeStrip(day, lo, hi, if (index == 0) currentTemp else null, Modifier.weight(1f).padding(horizontal = 10.dp).height(8.dp))
                    BasicText(fmt.temp(day.tempMax), Modifier.width(38.dp), style = Paper.type.number.copy(color = colors.paperInk))
                    Canvas(Modifier.width(14.dp).height(14.dp).graphicsLayer { rotationZ = arrow.value }) {
                        val c = colors.paperInkSoft
                        drawLine(c, Offset(size.width * 0.2f, size.height * 0.38f), Offset(size.width / 2, size.height * 0.64f), 1.6.dp.toPx(), StrokeCap.Round)
                        drawLine(c, Offset(size.width / 2, size.height * 0.64f), Offset(size.width * 0.8f, size.height * 0.38f), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
                AnimatedVisibility(
                    open,
                    enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(),
                ) {
                    // Unfolds like a folded note: hinged at the top edge.
                    val fold by transition.animateFloat(
                        transitionSpec = { spring(dampingRatio = 0.62f, stiffness = 170f) },
                        label = "fold",
                    ) { if (it == EnterExitState.Visible) 0f else -88f }
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
            if (index < days.lastIndex) Hairline()
        }
    }
}

@Composable
private fun RangeStrip(day: Day, lo: Double, hi: Double, current: Double?, modifier: Modifier) {
    val colors = Paper.colors
    val span = (hi - lo).coerceAtLeast(1.0)
    Canvas(modifier.semantics { contentDescription = "" }) {
        val r = size.height / 2
        drawRoundRect(colors.paperInk.copy(alpha = 0.07f), cornerRadius = CornerRadius(r))
        val x0 = ((day.tempMin - lo) / span * size.width).toFloat()
        val x1 = ((day.tempMax - lo) / span * size.width).toFloat().coerceAtLeast(x0 + size.height)
        drawRoundRect(
            Brush.horizontalGradient(listOf(tempColor(day.tempMin), tempColor(day.tempMax)), startX = x0, endX = x1),
            topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = CornerRadius(r),
        )
        if (current != null) {
            val cx = ((current - lo) / span * size.width).toFloat().coerceIn(x0 + r, x1 - r)
            drawCircle(colors.paper, r * 1.45f, Offset(cx, r))
            drawCircle(colors.paperInk, r * 0.7f, Offset(cx, r))
        }
    }
}

@Composable
private fun DayDetails(day: Day, fmt: WeatherFormat, onPreview: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (day.sunrise > 0) Stat(Glyph.Sunrise, fmt.time(day.sunrise))
            if (day.sunset > 0) Stat(Glyph.Sunset, fmt.time(day.sunset))
            Stat(Glyph.Wind, "${fmt.wind(day.windMax)} · ${fmt.compass(day.windDirection)}", rotation = (day.windDirection + 180f) % 360f)
            Stat(Glyph.Umbrella, fmt.precip(day.precipSum))
            if (day.uvMax >= 1) Stat(Glyph.Uv, "UV ${day.uvMax.roundToInt()}")
            if (day.daylightSeconds > 0) Stat(Glyph.Sun, fmt.duration(day.daylightSeconds.toLong()))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .pressable(onPreview)
                .clip(PillShape)
                .background(colors.accent.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(stringResource(R.string.show_in_sky), style = Paper.type.caption.copy(color = colors.accent, fontWeight = FontWeight(700)))
        }
    }
}

@Composable
private fun Stat(glyph: Glyph, text: String, rotation: Float = 0f) {
    val colors = Paper.colors
    Row(
        Modifier
            .clip(PillShape)
            .background(colors.paperInk.copy(alpha = 0.05f))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, size = 18.dp, rotation = rotation)
        Spacer(Modifier.width(6.dp))
        BasicText(text, style = Paper.type.caption.copy(color = colors.paperInk, fontWeight = FontWeight(600)))
    }
}
