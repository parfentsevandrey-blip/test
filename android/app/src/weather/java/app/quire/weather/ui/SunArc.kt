package app.quire.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.quire.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Where the day is between sunrise and sunset.
 *
 * The two times on their own are a pair of numbers to subtract in your head. Drawn as an arc with
 * the sun on it, "how much daylight is left" is a glance — which is the only question anybody
 * actually asks of a sunset time. The subtraction is written out under the arc as well, because
 * the picture answers "roughly" and some days you want the number.
 *
 * The arc is a half-circle rather than the true solar path: the true one depends on latitude and
 * season and would be a different shape every day, which is precision nobody wants at the cost of
 * a picture nobody recognises.
 */
@Composable
fun SunArc(sunrise: LocalDateTime, sunset: LocalDateTime, glass: Boolean = true) {
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

    // While the sun is up the useful number is what is left of the day; once it is down, what the
    // day was worth. Same arithmetic, different question.
    val minutes = if (up) whole - gone else whole
    val span = stringResource(R.string.wx_span, (minutes / 60L).toInt(), (minutes % 60L).toInt())
    val caption =
        stringResource(if (up) R.string.wx_daylight_left else R.string.wx_daylight, span)

    // The sun runs out along the arc to where it actually is rather than appearing there. It is
    // a picture of a journey, and a journey that is over before you look at it is a diagram.
    val travelled = remember(sunrise) { Animatable(0f) }
    LaunchedEffect(sunrise, progress) {
        travelled.animateTo(progress, tween(durationMillis = 1_100, easing = FastOutSlowInEasing))
    }
    val walk = travelled.value

    val track = scheme.outlineVariant
    val ink = scheme.primary
    val disc = scheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .testTag(BLOCK)
            .glass(CardDefaults.shape, glass, seed = 8),
        elevation = CardDefaults.cardElevation(defaultElevation = CardLift),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(ArcHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = 10.dp.toPx()
                    val left = inset
                    val right = size.width - inset
                    val baseline = size.height - Horizon.toPx()
                    val radius = (right - left) / 2f
                    val centre = (left + right) / 2f
                    // The apex is put where the box ends rather than at a fixed fraction of the
                    // width. A fixed fraction is a half-circle only at one width: on a phone the
                    // arc wanted to be a hundred points tall inside a seventy-point box, so its
                    // whole middle was clipped away and what was left looked like two stubs in
                    // the corners with a hole between them.
                    val rise = baseline - Apex.toPx()

                    fun point(fraction: Float): Offset {
                        val angle = Math.PI * (1f - fraction)
                        return Offset(
                            x = centre + (radius * kotlin.math.cos(angle)).toFloat(),
                            y = baseline - (rise * kotlin.math.sin(angle)).toFloat(),
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

                    if (walk > 0f) {
                        val walked = Path().apply {
                            moveTo(point(0f).x, point(0f).y)
                            val last = (STEPS * walk).toInt().coerceAtLeast(1)
                            for (step in 1..last) {
                                val p = point(step / STEPS.toFloat())
                                lineTo(p.x, p.y)
                            }
                        }
                        drawPath(walked, color = ink, style = Stroke(width = 3.dp.toPx()))
                    }

                    // The horizon, and the sun on it or above it.
                    drawLine(
                        color = track,
                        start = Offset(0f, baseline),
                        end = Offset(size.width, baseline),
                        strokeWidth = 1.dp.toPx(),
                    )
                    if (up) {
                        drawCircle(color = disc, radius = 5.dp.toPx(), center = point(walk))
                    }
                }
                // Under the arc and above the horizon — the one part of the card the drawing
                // leaves empty, and the one number the drawing cannot say exactly.
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Horizon + 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = stringResource(R.string.wx_sunrise),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = clock.format(sunrise),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.wx_sunset),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = clock.format(sunset),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** Enough segments that a polyline reads as a curve at this size. */
private const val STEPS = 48

private val ArcHeight = 96.dp

/** How far the horizon sits above the bottom of the box, and the apex below the top. */
private val Horizon = 6.dp
private val Apex = 4.dp
