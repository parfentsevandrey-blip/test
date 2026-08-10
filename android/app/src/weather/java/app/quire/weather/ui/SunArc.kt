package app.quire.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Where the day is between sunrise and sunset.
 *
 * The two times on their own are a pair of numbers to subtract in your head. Drawn as an arc with
 * the sun on it, "how much daylight is left" is a glance — which is the only question anybody
 * actually asks of a sunset time.
 *
 * The arc is a half-circle rather than the true solar path: the true one depends on latitude and
 * season and would be a different shape every day, which is precision nobody wants at the cost of
 * a picture nobody recognises.
 */
@Composable
fun SunArc(sunrise: LocalDateTime, sunset: LocalDateTime) {
    val scheme = MaterialTheme.colorScheme
    val locale = app.quire.calendar.m3.rememberLocale()
    val clock = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }

    val now = remember { LocalDateTime.now() }
    val whole = Duration.between(sunrise, sunset).toMinutes().coerceAtLeast(1L)
    val gone = Duration.between(sunrise, now).toMinutes()
    val progress = (gone.toFloat() / whole.toFloat()).coerceIn(0f, 1f)
    val up = now.isAfter(sunrise) && now.isBefore(sunset)

    val track = scheme.outlineVariant
    val travelled = scheme.primary
    val disc = scheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val inset = 10.dp.toPx()
                val left = inset
                val right = size.width - inset
                val baseline = size.height - 6.dp.toPx()
                val radius = (right - left) / 2f
                val centre = (left + right) / 2f

                fun point(fraction: Float): Offset {
                    val angle = Math.PI * (1f - fraction)
                    return Offset(
                        x = centre + (radius * kotlin.math.cos(angle)).toFloat(),
                        y = baseline - (radius * 0.62f * kotlin.math.sin(angle)).toFloat(),
                    )
                }

                // The whole arc, dashed, then the part of it the day has already spent, solid.
                val full = Path().apply {
                    moveTo(point(0f).x, point(0f).y)
                    for (step in 1..STEPS) {
                        val p = point(step / STEPS.toFloat())
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = full,
                    color = track,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                )

                if (progress > 0f) {
                    val walked = Path().apply {
                        moveTo(point(0f).x, point(0f).y)
                        val last = (STEPS * progress).toInt().coerceAtLeast(1)
                        for (step in 1..last) {
                            val p = point(step / STEPS.toFloat())
                            lineTo(p.x, p.y)
                        }
                    }
                    drawPath(walked, color = travelled, style = Stroke(width = 3.dp.toPx()))
                }

                // The horizon, and the sun on it or above it.
                drawLine(
                    color = track,
                    start = Offset(0f, baseline),
                    end = Offset(size.width, baseline),
                    strokeWidth = 1.dp.toPx(),
                )
                if (up) {
                    drawCircle(color = disc, radius = 5.dp.toPx(), center = point(progress))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = clock.format(sunrise),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = clock.format(sunset),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Enough segments that a polyline reads as a curve at this size. */
private const val STEPS = 48
