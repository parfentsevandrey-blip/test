package app.rosa.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.HourlyPoint
import app.rosa.weather.core.model.TemperatureScale
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.floor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ItemWidth = 58.dp
private const val HOURS = 48

/**
 * A time machine in a card. The ribbon of the next 48 hours scrolls under a fixed glass lens;
 * whatever hour sits in the lens is "now" for the whole screen — the sky, the sun, the rain and
 * the big numerals all travel with your finger. Hours tick under the thumb; sunrise, sunset and
 * midnight give a firmer click. Let go and it snaps to an hour; tap "back to now" to return.
 */
@Composable
fun HourlyTimeline(
    forecast: Forecast,
    now: Long,
    currentTemperature: Double,
    format: WeatherFormat,
    onScrub: (offsetHours: Float, dragging: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstIndex = forecast.firstHourIndexFrom(now)
    val hours = remember(forecast, firstIndex) { forecast.hoursFrom(now).take(HOURS) }
    if (hours.size < 2) return
    val colors = Rosa.colors
    val haptics = LocalHaptics.current
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val itemPx = with(density) { ItemWidth.toPx() }
    val scope = rememberCoroutineScope()
    val onScrubState by rememberUpdatedState(onScrub)

    val offsetHours by remember {
        derivedStateOf { state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx }
    }
    val temps = remember(hours, currentTemperature) {
        hours.mapIndexed { i, h -> if (i == 0) currentTemperature else h.temperature }
    }
    // Centre a flat day in the band instead of letting it hug the bottom.
    val span = (temps.max() - temps.min()).coerceAtLeast(4.0)
    val minT = (temps.max() + temps.min()) / 2 - span / 2
    val maxT = minT + span
    val milestones = remember(forecast, hours) { milestoneHours(forecast, hours, format) }

    // Report the scrub position continuously; tick on every whole hour crossed.
    LaunchedEffect(state) {
        snapshotFlow { offsetHours to state.isScrollInProgress }.collect { (offset, dragging) ->
            onScrubState(offset, dragging)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { floor(offsetHours + 0.5f).toInt() }.distinctUntilChanged().collect { hour ->
            if (!state.isScrollInProgress) return@collect
            if (hour in milestones) haptics?.milestone() else haptics?.tick()
        }
    }

    GlassSurface(modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 30.dp) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.next_hours), style = Rosa.type.label, color = colors.inkSoft)
                    Text(stringResource(R.string.scrub_hint), style = Rosa.type.caption, color = colors.inkFaint, maxLines = 1)
                }
                AnimatedVisibility(offsetHours > 0.6f, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    GlassButton(
                        onClick = { scope.launch { state.animateScrollToItem(0) } },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        style = GlassStyle.Clear,
                    ) {
                        Text(stringResource(R.string.back_to_now), style = Rosa.type.caption, color = colors.ink)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(158.dp)) {
                // The fixed lens under the ribbon: real glass, so the sky bends through it.
                GlassSurface(
                    Modifier.padding(start = 12.dp).width(ItemWidth).fillMaxHeight(),
                    style = GlassStyle.Lens,
                    cornerRadius = 26.dp,
                    shadow = false,
                ) {}
                LazyRow(
                    state = state,
                    flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp + ItemWidth * 4),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(hours, key = { _, h -> h.time }) { i, hour ->
                        HourCell(
                            hour = hour,
                            label = if (i == 0) format.now() else format.hour(hour.time),
                            prev = temps.getOrNull(i - 1),
                            next = temps.getOrNull(i + 1),
                            temp = temps[i],
                            minT = minT,
                            maxT = maxT,
                            format = format,
                            milestone = i in milestones,
                            chance = forecast.chanceForHourStarting(firstIndex + i),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourCell(
    hour: HourlyPoint,
    label: String,
    prev: Double?,
    next: Double?,
    temp: Double,
    minT: Double,
    maxT: Double,
    format: WeatherFormat,
    milestone: Boolean,
    chance: Int,
) {
    val colors = Rosa.colors
    val condition = WeatherCondition.fromWmo(hour.weatherCode)
    Column(
        Modifier.width(ItemWidth).fillMaxHeight().semantics {
            contentDescription = "$label, ${format.temperature(temp)}, ${format.condition(condition, hour.isDay)}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Text(label, style = Rosa.type.caption, color = if (milestone) colors.accent else colors.inkSoft, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        WeatherGlyph(condition, hour.isDay, Modifier.size(30.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val span = (maxT - minT).coerceAtLeast(3.0)
            val lineColor = TemperatureScale.colorFor(temp).toColor()
            val prevColor = TemperatureScale.colorFor(prev ?: temp).toColor()
            val nextColor = TemperatureScale.colorFor(next ?: temp).toColor()
            val ink = colors.ink
            val measurer = rememberTextMeasurer()
            val labelStyle = Rosa.type.label.copy(color = colors.ink)
            val tempText = format.temperature(temp)
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val top = 22.dp.toPx()
                val bottom = size.height - 18.dp.toPx()
                fun y(t: Double) = (bottom - ((t - minT) / span).toFloat() * (bottom - top))
                val cy = y(temp)
                val ly = y(((prev ?: temp) + temp) / 2)
                val ry = y(((next ?: temp) + temp) / 2)
                val path = Path().apply {
                    moveTo(0f, ly)
                    quadraticTo(size.width * 0.25f, (ly + cy) / 2 + (cy - ly) * 0.35f, size.width / 2, cy)
                    quadraticTo(size.width * 0.75f, (ry + cy) / 2 + (cy - ry) * 0.35f, size.width, ry)
                }
                if (prev == null) {
                    path.reset()
                    path.moveTo(size.width / 2, cy)
                    path.quadraticTo(size.width * 0.75f, (ry + cy) / 2, size.width, ry)
                }
                if (next == null) {
                    path.reset()
                    path.moveTo(0f, ly)
                    path.quadraticTo(size.width * 0.25f, (ly + cy) / 2, size.width / 2, cy)
                }
                drawPath(
                    path,
                    Brush.horizontalGradient(listOf(lerpColor(prevColor, lineColor), lineColor, lerpColor(nextColor, lineColor))),
                    style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round),
                )
                // Soft glow under the ribbon.
                val fill = Path().apply {
                    addPath(path)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                if (prev != null && next != null) {
                    drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = size.height))
                }
                drawCircle(ink, 3.4.dp.toPx(), Offset(size.width / 2, cy))
                drawCircle(lineColor, 2.2.dp.toPx(), Offset(size.width / 2, cy))
                val layout = measurer.measure(tempText, labelStyle)
                drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, cy - layout.size.height - 5.dp.toPx()))

                // Precipitation chance as a small bar along the bottom.
                val p = chance / 100f
                if (p >= 0.1f) {
                    val bw = 14.dp.toPx()
                    val bh = 10.dp.toPx() * p + 2.dp.toPx()
                    drawRoundRect(
                        colors.rain.copy(alpha = 0.35f + p * 0.6f),
                        topLeft = Offset(size.width / 2 - bw / 2, size.height - bh),
                        size = androidx.compose.ui.geometry.Size(bw, bh),
                        cornerRadius = CornerRadius(bw / 2),
                    )
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color) = androidx.compose.ui.graphics.lerp(a, b, 0.5f)

/** Indices of hours that deserve a firmer haptic: sunrise, sunset, midnight. */
private fun milestoneHours(forecast: Forecast, hours: List<HourlyPoint>, format: WeatherFormat): Set<Int> {
    val events = forecast.daily.flatMap { listOfNotNull(it.sunrise, it.sunset) }
    return hours.indices.filter { i ->
        val t = hours[i].time
        val midnight = format.hour(t).let { it == "0" || it == "12 AM" || it == "00" }
        midnight || events.any { it in t until t + 3600 }
    }.toSet()
}
