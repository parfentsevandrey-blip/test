package app.quire.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The curve is drawn behind the columns rather than beside them, so a temperature and its point on
 * the line occupy the same place on screen and the eye does not have to travel between them.
 */
@Composable
fun HourStrip(
    hours: List<HourForecast>,
    units: WeatherModel.Settings,
    modifier: Modifier = Modifier,
) {
    if (hours.size < 2) return
    val locale = app.quire.calendar.m3.rememberLocale()
    val clock = remember(locale) { DateTimeFormatter.ofPattern("HH", locale) }

    val warmest = hours.maxOf { it.temperature }
    val coldest = hours.minOf { it.temperature }
    val span = (warmest - coldest).coerceAtLeast(1.0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        hours.forEachIndexed { index, hour ->
            HourColumn(
                hour = hour,
                units = units,
                clock = clock,
                // Where this hour's temperature sits between the coldest and warmest of the day,
                // and where its neighbours do, which is what lets each column draw its own slice
                // of one continuous line.
                before = hours.getOrNull(index - 1)?.let { level(it.temperature, coldest, span) },
                here = level(hour.temperature, coldest, span),
                after = hours.getOrNull(index + 1)?.let { level(it.temperature, coldest, span) },
                first = index == 0,
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

private fun level(value: Double, coldest: Double, span: Double): Float =
    ((value - coldest) / span).toFloat().coerceIn(0f, 1f)

@Composable
private fun HourColumn(
    hour: HourForecast,
    units: WeatherModel.Settings,
    clock: DateTimeFormatter,
    before: Float?,
    here: Float,
    after: Float?,
    first: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    // Read out here: a draw scope has no access to the theme.
    val primary = scheme.primary
    val tertiary = scheme.tertiary
    val now = remember { LocalDateTime.now() }
    val isNow = first || (hour.time.hour == now.hour && hour.time.toLocalDate() == now.toLocalDate())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(ColumnWidth),
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
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hour.rain > 0) "${hour.rain}%" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.tertiary,
            maxLines = 1,
        )

        // The curve, with each temperature riding its own point on it.
        //
        // The label moves with the line rather than sitting in a row above it: a flat row of
        // numbers over a line is two things to read, and the whole reason to draw a curve is that
        // the shape should be readable without reading anything.
        Box(modifier = Modifier.fillMaxWidth().height(CurveHeight)) {
            Canvas(Modifier.fillMaxWidth().height(CurveHeight)) {
                fun y(level: Float) = LabelBand.toPx() +
                    (size.height - LabelBand.toPx() - Baseline.toPx()) * (1f - level)

                val path = Path()
                val midX = size.width / 2f
                path.moveTo(0f, y(((before ?: here) + here) / 2f))
                path.lineTo(midX, y(here))
                path.lineTo(size.width, y((here + (after ?: here)) / 2f))
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(listOf(tertiary, primary)),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(color = primary, radius = 3.dp.toPx(), center = Offset(midX, y(here)))
            }
            Text(
                text = "${units.degrees.from(hour.temperature).roundToInt()}°",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = labelOffset(here)),
            )
        }
    }
}

/** Where a label sits for a given level: just above that level's point on the curve. */
private fun labelOffset(level: Float): androidx.compose.ui.unit.Dp =
    (CurveHeight - LabelBand - Baseline) * (1f - level)

private val ColumnWidth = 56.dp

/** Tall enough that a day's swing is a shape rather than a wobble. */
private val CurveHeight = 84.dp

/** Room for the label above the highest point, and for the line below the lowest. */
private val LabelBand = 20.dp
private val Baseline = 6.dp
