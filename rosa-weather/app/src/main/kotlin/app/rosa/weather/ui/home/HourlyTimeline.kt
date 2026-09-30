package app.rosa.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.HourlyPoint
import app.rosa.weather.core.model.TemperatureScale
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ItemWidth = 62.dp

/** The lens reaches a little past its hour on both sides, so "Now" has air around it. */
private val LensOverhang = 7.dp
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
    // The first time the card is shown the temperature line draws itself from now onward, each
    // hour's dot and reading popping in as the line reaches it. Scrolled back to, it is just there.
    val motion = LocalMotionEnabled.current
    var drawn by rememberSaveable { mutableStateOf(!motion) }
    val reveal = remember { Animatable(if (drawn) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (drawn) return@LaunchedEffect
        delay(160)
        reveal.animateTo(1f, tween(1150, easing = FastOutSlowInEasing))
        drawn = true
    }
    val revealCells = { if (reveal.value >= 1f) Float.MAX_VALUE else reveal.value * REVEAL_CELLS }

    // Report the scrub position continuously; tick on every whole hour crossed.
    LaunchedEffect(state) {
        snapshotFlow { offsetHours to state.isScrollInProgress }.collect { (offset, dragging) ->
            onScrubState(offset, dragging)
        }
    }
    // The lens is liquid: hours gliding under it pull it into a stretch along their way, and when
    // they stop it springs back through a wobble, like a drop.
    val stretch = remember { Animatable(0f) }
    LaunchedEffect(state, motion) {
        if (!motion) return@LaunchedEffect
        var last = offsetHours
        var lastNanos = withFrameNanos { it }
        var settle: Job? = null
        snapshotFlow { offsetHours }.collect { hours ->
            // Timed by the frames the hours moved in.
            val nanos = withFrameNanos { it }
            val dt = (nanos - lastNanos) / 1_000_000_000f
            val speed = if (dt > 0.001f && dt < 0.25f) (hours - last) / dt else 0f
            last = hours
            lastNanos = nanos
            launch { stretch.animateTo((speed / LENS_FULL_SPEED).coerceIn(-1f, 1f), spring(dampingRatio = 0.8f, stiffness = 700f)) }
            settle?.cancel()
            settle = launch {
                delay(60)
                stretch.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 240f))
            }
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
                    Modifier
                        .padding(start = 12.dp - LensOverhang)
                        .width(ItemWidth + LensOverhang * 2)
                        .fillMaxHeight()
                        .graphicsLayer {
                            // Stretched along the hours' way, a little thinner for it (a drop
                            // keeps its volume), and leaning into them.
                            val s = stretch.value
                            val a = abs(s)
                            scaleX = 1f + 0.14f * a
                            scaleY = 1f - 0.045f * a
                            translationX = -s * 5.dp.toPx()
                        },
                    style = GlassStyle.Lens,
                    cornerRadius = 22.dp,
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
                            index = i,
                            revealed = revealCells,
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
                            // Magnifier: hours swell as they glide under the lens.
                            focus = { (1f - abs(i - offsetHours) / 1.6f).coerceIn(0f, 1f) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourCell(
    index: Int,
    revealed: () -> Float,
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
    focus: () -> Float,
) {
    val colors = Rosa.colors
    val condition = WeatherCondition.fromWmo(hour.weatherCode)
    // The hour under the lens comes alive: its sun turns, its rain falls.
    val focusNow by rememberUpdatedState(focus)
    val underLens by remember { derivedStateOf { focusNow() > 0.6f } }
    Column(
        Modifier.width(ItemWidth).fillMaxHeight().semantics {
            contentDescription = "$label, ${format.temperature(temp)}, ${format.condition(condition, hour.isDay)}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            style = Rosa.type.caption,
            color = if (milestone) colors.accent else colors.inkSoft,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val s = 1f + 0.05f * focus()
                scaleX = s
                scaleY = s
            },
        )
        Spacer(Modifier.height(6.dp))
        WeatherGlyph(
            condition,
            hour.isDay,
            Modifier.size(30.dp).graphicsLayer {
                val f = focus()
                scaleX = 1f + 0.3f * f
                scaleY = 1f + 0.3f * f
                translationY = -3.dp.toPx() * f
            },
            animated = underLens,
            // Rendered at the lens's magnification, so it stays crisp under it.
            rasterScale = 1.3f,
        )
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
                // How much of this hour the line has reached as it draws itself (1 once drawn).
                val shown = (revealed() - index).coerceIn(0f, 1f)
                if (shown <= 0f) return@Canvas
                val pop = ((shown - 0.35f) / 0.4f).coerceIn(0f, 1f)
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
                clipRect(right = size.width * shown) {
                    val ribbon = Brush.horizontalGradient(listOf(lerpColor(prevColor, lineColor), lineColor, lerpColor(nextColor, lineColor)))
                    // The line glows in its own colour, like light in a glass rod: a soft halo,
                    // the line, and a fine highlight along its top.
                    drawPath(path, ribbon, alpha = 0.13f, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, ribbon, alpha = 0.22f, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, ribbon, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                    translate(top = -0.6.dp.toPx()) {
                        drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    // A deep glow under the ribbon.
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    if (prev != null && next != null) {
                        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.27f), lineColor.copy(alpha = 0.07f), Color.Transparent), startY = top, endY = size.height))
                    }
                }
                if (pop <= 0f) return@Canvas
                val f = focus()
                val dot = Offset(size.width / 2, cy)
                // The dot springs into place as the line reaches it.
                val swell = 1f + 0.5f * (1f - pop) * pop * 4f
                if (f > 0.01f) drawCircle(lineColor.copy(alpha = 0.28f * f * pop), (6.dp + 5.dp * f).toPx(), dot)
                // A glossy bead on the line: a white setting, the hour's colour, a spark of light.
                val bead = (3.4.dp + 1.4.dp * f).toPx() * swell
                drawCircle(Color.Black.copy(alpha = 0.2f * pop), bead * 1.06f, dot + Offset(0f, 0.8.dp.toPx()))
                drawCircle(ink.copy(alpha = ink.alpha * pop), bead, dot)
                drawCircle(lineColor.copy(alpha = pop), bead * 0.65f, dot)
                drawCircle(Color.White.copy(alpha = 0.8f * pop), bead * 0.2f, dot + Offset(-bead * 0.22f, -bead * 0.24f))
                val layout = measurer.measure(tempText, labelStyle)
                val textTop = cy - layout.size.height - (5.dp + 3.dp * f).toPx() + (1f - pop) * 6.dp.toPx()
                scale(1f + 0.14f * f, pivot = Offset(size.width / 2, textTop + layout.size.height)) {
                    drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, textTop), alpha = pop)
                }

                // Precipitation chance as a small bar along the bottom, rising as the line passes.
                val p = chance / 100f
                if (p >= 0.1f) {
                    val bw = 14.dp.toPx()
                    val bh = (10.dp.toPx() * p + 2.dp.toPx()) * pop
                    val at = Offset(size.width / 2 - bw / 2, size.height - bh)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(lerpColor(colors.rain, Color.White).copy(alpha = 0.45f + p * 0.5f), colors.rain.copy(alpha = 0.35f + p * 0.6f)), startY = at.y, endY = size.height),
                        topLeft = at,
                        size = androidx.compose.ui.geometry.Size(bw, bh),
                        cornerRadius = CornerRadius(bw / 2),
                    )
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color) = androidx.compose.ui.graphics.lerp(a, b, 0.5f)

/** How fast the hours glide under the lens (per second) when it is stretched all it will. */
private const val LENS_FULL_SPEED = 14f

/** How many hours the line crosses as it draws itself: about those on screen at once. */
private const val REVEAL_CELLS = 7f

/** Indices of hours that deserve a firmer haptic: sunrise, sunset, midnight. */
private fun milestoneHours(forecast: Forecast, hours: List<HourlyPoint>, format: WeatherFormat): Set<Int> {
    val events = forecast.daily.flatMap { listOfNotNull(it.sunrise, it.sunset) }
    return hours.indices.filter { i ->
        val t = hours[i].time
        val midnight = format.hour(t).let { it == "0" || it == "12 AM" || it == "00" }
        midnight || events.any { it in t until t + 3600 }
    }.toSet()
}
