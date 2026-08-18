package app.quire.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.quire.R
import app.quire.weather.HourForecast
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The next twenty-four hours, as a shape rather than a list.
 *
 * A five-day forecast tells you what kind of week it is; this tells you whether to leave now. The
 * curve is the point of it — a column of numbers has to be read left to right and compared in the
 * head, and a line does the comparing for you: where the afternoon peaks, when it falls off, how
 * sharply the rain arrives.
 *
 * The numbers sit in a row of their own and the curve runs underneath them, filled. The labels
 * used to ride the line, each one just above its own point, which is defensible on paper and wrong
 * on a real afternoon: a stretch of weather that changes by a degree an hour moves each label
 * three points, so the row is neither aligned nor visibly stepped and reads as badly set type.
 *
 * The curve is one path across the whole strip rather than a slice drawn inside each column. Two
 * translucent shapes that share an edge do not add up to one shape — each edge is antialiased
 * against what is behind it, so the seam comes out lighter than either side and the fill ends up
 * with a row of notches along its foot, one per hour.
 *
 * It sits in a card at [Gutter], like the readings above it and the days below, so the scrolling
 * content is bounded by the same left and right edges as everything else on the screen instead of
 * running off into the margin.
 */
@Composable
fun HourStrip(
    hours: List<HourForecast>,
    units: WeatherModel.Settings,
    modifier: Modifier = Modifier,
) {
    if (hours.size < 2) return
    val scheme = MaterialTheme.colorScheme
    val locale = app.quire.calendar.m3.rememberLocale()
    val clock = remember(locale) { DateTimeFormatter.ofPattern("HH", locale) }
    val now = remember { LocalDateTime.now() }

    val warmest = hours.maxOf { it.temperature }
    val coldest = hours.minOf { it.temperature }
    val span = (warmest - coldest).coerceAtLeast(1.0)
    val levels = hours.map { ((it.temperature - coldest) / span).toFloat().coerceIn(0f, 1f) }

    // Decided once for the whole strip, not per column: if any hour is wet every column gets the
    // line so the rows stay level with each other, and if none is, nobody gets a blank one. The
    // blank line was a dead band across the strip on exactly the days nothing was happening.
    val wet = hours.any { it.rain > 0 }

    // Read out here: a draw scope has no access to the theme.
    val primary = scheme.primary
    val tertiary = scheme.tertiary

    // How much of the line has arrived. Keyed on the hours, so a refresh that brings a new
    // forecast draws the new shape rather than swapping it in behind the old one.
    val drawn = remember(hours.first().time) { Animatable(0f) }
    LaunchedEffect(hours.first().time) {
        drawn.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    Card(
        shape = RoundedCornerShape(CardCorner),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .testTag(BLOCK),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        val strip = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The content dissolves at the viewport's edges instead of being guillotined by
                // them, and each fade appears only while there is something on its side to scroll
                // to — a fade over the end of the data would say "more" where there is none.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .edgeFades(strip)
                .horizontalScroll(strip)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Row {
                hours.forEachIndexed { index, hour ->
                    val isNow = index == 0 ||
                        (hour.time.hour == now.hour && hour.time.toLocalDate() == now.toLocalDate())
                    HourHead(hour, units, clock, isNow)
                }
            }

            Spacer(Modifier.height(4.dp))

            // The shape, under the numbers rather than behind them: a short filled band that says
            // where the afternoon peaks and how sharply the evening falls off, which is the one
            // thing a row of numbers cannot say.
            Canvas(
                Modifier
                    .width(ColumnWidth * hours.size)
                    .height(CurveHeight),
            ) {
                val step = size.width / hours.size
                fun y(level: Float) = Ceiling.toPx() +
                    (size.height - Ceiling.toPx() - Floor.toPx()) * (1f - level)

                // The line draws itself from now outwards rather than appearing all at once. It
                // is the one thing on the screen that is a shape rather than a number, and a
                // shape arriving as a shape is how you notice it is one.
                val reach = drawn.value * levels.size
                val ridge = Path().apply {
                    moveTo(0f, y(levels.first()))
                    levels.forEachIndexed { index, level ->
                        if (index > reach) return@forEachIndexed
                        lineTo(step * index + step / 2f, y(level))
                    }
                    if (reach >= levels.size - 1) lineTo(size.width, y(levels.last()))
                }
                drawPath(
                    path = Path().apply {
                        addPath(ridge)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    },
                    // Faded to the same colour at zero alpha rather than to Color.Transparent:
                    // transparent is black, and a gradient run in non-premultiplied sRGB walks the
                    // hue towards black on the way there, which puts a dirty band under the curve.
                    brush = Brush.verticalGradient(
                        listOf(
                            primary.copy(alpha = 0.32f * drawn.value),
                            primary.copy(alpha = 0f),
                        ),
                    ),
                )
                drawPath(
                    path = ridge,
                    brush = Brush.horizontalGradient(listOf(tertiary, primary)),
                    style = Stroke(width = 2.dp.toPx()),
                )
                levels.forEachIndexed { index, level ->
                    if (index > reach) return@forEachIndexed
                    drawCircle(
                        color = primary,
                        radius = 3.dp.toPx(),
                        center = Offset(step * index + step / 2f, y(level)),
                    )
                }
            }

            if (wet) {
                Spacer(Modifier.height(6.dp))
                Row {
                    hours.forEach { hour ->
                        Text(
                            text = if (hour.rain > 0) "${hour.rain}%" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.tertiary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(ColumnWidth),
                        )
                    }
                }
            }
        }
    }
}

/** The hour, its sky and its temperature: three rows level across the whole strip. */
@Composable
private fun HourHead(
    hour: HourForecast,
    units: WeatherModel.Settings,
    clock: DateTimeFormatter,
    isNow: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.width(ColumnWidth)) {
        if (isNow) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainerHighest),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Text(
                text = if (isNow) stringResource(R.string.wx_now) else clock.format(hour.time),
                style = MaterialTheme.typography.labelMedium,
                color = if (isNow) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                painter = painterResource(hour.sky.icon(hour.day)),
                contentDescription = stringResource(hour.sky.label),
                // The sky's own ink, so a wet afternoon is visible in the strip's colour before
                // a single glyph is read.
                tint = skyInk(hour.sky, scheme),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${units.degrees.from(hour.temperature).roundToInt()}°",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * The dissolve at each end of the strip's viewport.
 *
 * Drawn over the scrolled content with destination-in alpha, which is why the whole strip is
 * composed offscreen first — a blend against the card behind it would punch through to the card.
 * Each side's strength follows how much there is left to scroll on that side, so the fade is the
 * scroll saying "more", not a decoration.
 */
private fun Modifier.edgeFades(strip: ScrollState): Modifier = this.drawWithContent {
    drawContent()
    val reach = FadeWidth.toPx()
    val left = (strip.value / reach).coerceIn(0f, 1f)
    val right = ((strip.maxValue - strip.value) / reach).coerceIn(0f, 1f)
    if (left > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 1f - left),
                1f to Color.Black,
                endX = reach,
            ),
            size = androidx.compose.ui.geometry.Size(reach, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
    if (right > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                1f to Color.Black.copy(alpha = 1f - right),
                startX = size.width - reach,
                endX = size.width,
            ),
            topLeft = Offset(size.width - reach, 0f),
            size = androidx.compose.ui.geometry.Size(reach, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val FadeWidth = 18.dp

private val ColumnWidth = 52.dp

/** Short, because it is now a shape beside the numbers rather than the place they live. */
private val CurveHeight = 44.dp

/** Room above the warmest point for the stroke, and below the coldest for the fill to read. */
private val Ceiling = 5.dp
private val Floor = 7.dp
