package app.rosa.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.component.glassFill
import app.rosa.weather.core.designsystem.component.rememberPressScale
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.DailyPoint
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.TemperatureScale
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.roundToInt

/**
 * Ten days as temperature capsules on a shared scale, so warm and cold spells read at a glance.
 * Tap a day to unfold it: wind, rain, UV, daylight and that day's temperature curve.
 */
@Composable
fun DailyForecast(forecast: Forecast, now: Long, currentTemperature: Double, format: WeatherFormat, modifier: Modifier = Modifier) {
    val days = remember(forecast, now / 3600) { forecast.daysFrom(now).take(10) }
    if (days.isEmpty()) return
    val lo = days.minOf { it.temperatureMin }
    val hi = days.maxOf { it.temperatureMax }
    var expanded by rememberSaveable { mutableLongStateOf(-1L) }
    val haptics = LocalHaptics.current

    GlassSurface(modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 30.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.daily_title), style = Rosa.type.label, color = Rosa.colors.inkSoft, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
            days.forEachIndexed { i, day ->
                DayRow(
                    day = day,
                    isToday = i == 0,
                    label = format.dayLabel(day.time, now),
                    lo = lo,
                    hi = hi,
                    current = if (i == 0) currentTemperature else null,
                    format = format,
                    expanded = expanded == day.time,
                    forecast = forecast,
                    onClick = {
                        haptics?.press()
                        expanded = if (expanded == day.time) -1L else day.time
                    },
                )
            }
        }
    }
}

@Composable
private fun DayRow(
    day: DailyPoint,
    isToday: Boolean,
    label: String,
    lo: Double,
    hi: Double,
    current: Double?,
    format: WeatherFormat,
    expanded: Boolean,
    forecast: Forecast,
    onClick: () -> Unit,
) {
    val colors = Rosa.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = rememberPressScale(pressed)
    val condition = WeatherCondition.fromWmo(day.weatherCode)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = Rosa.type.headline.copy(fontSize = Rosa.type.body.fontSize),
                color = if (isToday) colors.ink else colors.inkSoft,
                modifier = Modifier.width(92.dp),
                maxLines = 1,
            )
            WeatherGlyph(condition, isDay = true, modifier = Modifier.size(30.dp))
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                if (day.precipitationProbabilityMax >= 30) {
                    Text("${day.precipitationProbabilityMax}%", style = Rosa.type.caption, color = colors.rain)
                }
            }
            Text(format.temperature(day.temperatureMin), style = Rosa.type.body, color = colors.inkSoft, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
            RangeCapsule(lo, hi, day.temperatureMin, day.temperatureMax, current, Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(format.temperature(day.temperatureMax), style = Rosa.type.headline.copy(fontSize = Rosa.type.body.fontSize), color = colors.ink, modifier = Modifier.width(38.dp))
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(RosaMotion.gel()) + fadeIn(),
            exit = shrinkVertically(RosaMotion.gel()) + fadeOut(),
        ) {
            DayDetails(day, forecast, format)
        }
    }
}

@Composable
private fun RangeCapsule(lo: Double, hi: Double, min: Double, max: Double, current: Double?, modifier: Modifier) {
    val track = Rosa.colors.ink.copy(alpha = 0.14f)
    val cold = TemperatureScale.colorFor(min).toColor()
    val warm = TemperatureScale.colorFor(max).toColor()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, RosaMotion.gel()) }
    Canvas(modifier.height(8.dp)) {
        val span = (hi - lo).coerceAtLeast(1.0)
        fun x(t: Double) = ((t - lo) / span).toFloat().coerceIn(0f, 1f) * size.width
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val x0 = x(min)
        val x1 = maxOf(x(max), x0 + size.height)
        val mid = (x0 + x1) / 2
        val half = (x1 - x0) / 2 * grow.value
        drawRoundRect(
            Brush.horizontalGradient(listOf(cold, warm), startX = x0, endX = x1),
            topLeft = Offset(mid - half, 0f),
            size = Size(half * 2, size.height),
            cornerRadius = r,
        )
        if (current != null) {
            val cx = x(current)
            drawCircle(Color.Black.copy(alpha = 0.25f), size.height * 0.95f, Offset(cx, size.height / 2))
            drawCircle(Color.White, size.height * 0.7f, Offset(cx, size.height / 2))
        }
    }
}

@Composable
private fun DayDetails(day: DailyPoint, forecast: Forecast, format: WeatherFormat) {
    val colors = Rosa.colors
    val hours = remember(forecast, day.time) { forecast.hourly.filter { it.time >= day.time && it.time < day.time + 86_400 } }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
        if (hours.size >= 6) {
            val temps = hours.map { it.temperature }
            val lo = temps.min()
            val hi = temps.max()
            val lineColor = colors.accent
            Canvas(Modifier.fillMaxWidth().height(54.dp).padding(vertical = 6.dp)) {
                val span = (hi - lo).coerceAtLeast(2.0)
                val step = size.width / (temps.size - 1)
                val path = androidx.compose.ui.graphics.Path()
                temps.forEachIndexed { i, t ->
                    val x = i * step
                    val y = size.height - ((t - lo) / span).toFloat() * size.height
                    if (i == 0) path.moveTo(x, y) else {
                        val px = (i - 1) * step
                        val py = size.height - ((temps[i - 1] - lo) / span).toFloat() * size.height
                        path.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
                    }
                }
                drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        Spacer(Modifier.height(4.dp))
        val chips = buildList {
            add(stringResource(R.string.day_detail_wind, format.wind(day.windSpeedMax)))
            add(stringResource(R.string.day_detail_rain, format.precipitation(day.precipitationSum), day.precipitationProbabilityMax))
            if (day.uvIndexMax >= 1) add(stringResource(R.string.day_detail_uv, day.uvIndexMax.roundToInt()))
            if (day.sunrise != null && day.sunset != null) add(stringResource(R.string.day_detail_sun, format.time(day.sunrise!!), format.time(day.sunset!!)))
        }
        FlowChips(chips)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(chips: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { chip ->
            Text(
                chip,
                style = Rosa.type.caption,
                color = Rosa.colors.ink,
                modifier = Modifier.glassFill(12.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
