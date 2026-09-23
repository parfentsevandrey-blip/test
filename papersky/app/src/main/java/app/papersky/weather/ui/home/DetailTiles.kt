package app.papersky.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.Astro
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.WeatherMoment
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Eight tiles of paper; each has a small engraved instrument and flips over to explain itself. */
@Composable
fun DetailsGrid(forecast: Forecast, moment: WeatherMoment, fmt: WeatherFormat, nowSec: Long, modifier: Modifier = Modifier) {
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> WindTile(moment, fmt, m) },
        { m -> HumidityTile(moment, fmt, m) },
        { m -> UvTile(forecast, moment, nowSec, m) },
        { m -> PressureTile(forecast, moment, fmt, nowSec, m) },
        { m -> SunTile(forecast, fmt, nowSec, m) },
        { m -> PrecipTile(forecast, fmt, nowSec, m) },
        { m -> FeelsTile(moment, fmt, m) },
        { m -> MoonTile(fmt, nowSec, m) },
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A tile (§8): small capitals, an engraved instrument, the value in the serif. Tap flips it over
 * (`flip` spring) onto an explanation in the italic; edge-on it turns away from the light.
 */
@Composable
private fun FlipTile(
    seed: Int,
    title: String,
    value: String,
    caption: String,
    explanation: String,
    modifier: Modifier = Modifier,
    art: @Composable (appear: () -> Float) -> Unit,
) {
    var flipped by rememberSaveable { mutableStateOf(false) }
    val rotation = animateFloatAsState(if (flipped) 180f else 0f, Motion.flip(), label = "flip")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(seed * 60L)
        appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 60f))
    }
    val h = rememberHaptics()
    val density = LocalDensity.current.density
    val showBack = rotation.value > 90f
    Box(
        modifier
            .height(186.dp)
            .semantics { contentDescription = "$title: $value. $caption" }
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 18f * density
            },
    ) {
        if (!showBack) {
            PaperCard(Modifier.fillMaxSize(), onClick = { h.softTick(); flipped = true }, contentPadding = PaddingValues(16.dp)) {
                Label(title)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) { art { appear.value } }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    value,
                    style = Paper.type.title.copy(color = Paper.colors.paperInk, fontSize = 26.sp, lineHeight = 1.05.em),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 26.sp, stepSize = 1.sp),
                )
                BasicText(caption, style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft, fontSize = 12.sp), maxLines = 2)
            }
        } else {
            PaperCard(
                Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
                onClick = { h.softTick(); flipped = false }, contentPadding = PaddingValues(16.dp),
            ) {
                Label(title)
                Spacer(Modifier.height(10.dp))
                BasicText(
                    explanation,
                    style = Paper.type.note.copy(color = Paper.colors.paperInk, fontSize = 18.sp),
                    autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 18.sp, stepSize = 0.5.sp),
                )
            }
        }
        // The sheet darkens as it turns edge-on, like paper away from the light.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val edge = 1f - kotlin.math.abs(90f - rotation.value % 180f) / 90f
                if (edge > 0.01f) drawRoundRect(Color.Black.copy(alpha = edge * 0.18f), cornerRadius = CornerRadius(16.dp.toPx()))
            },
        )
    }
}

@Composable
private fun WindTile(m: WeatherMoment, fmt: WeatherFormat, modifier: Modifier) {
    val clock = LocalSceneClock.current
    val colors = Paper.colors
    val measurer = rememberTextMeasurer()
    val points = androidx.compose.ui.platform.LocalResources.current.getStringArray(R.array.compass_points)
    val gustiness = ((m.windGusts - m.windSpeed) / 6.0).toFloat().coerceIn(0f, 1f)
    val captionStyle = Paper.type.caption
    FlipTile(
        1, stringResource(R.string.tile_wind), fmt.wind(m.windSpeed),
        stringResource(R.string.tile_wind_caption, fmt.compass(m.windDirection), fmt.wind(m.windGusts)),
        stringResource(R.string.tile_wind_back), modifier,
    ) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 4.dp.toPx()
            drawCircle(colors.paperInk.copy(alpha = 0.16f), r, c, style = Stroke(1f))
            for (k in 0 until 24) {
                val a = k * 15 * PI / 180
                val inner = r - (if (k % 6 == 0) 5.dp.toPx() else 2.5.dp.toPx())
                drawLine(colors.paperInk.copy(alpha = 0.25f), Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 1f)
            }
            listOf(0, 2, 4, 6).forEach { k ->
                val a = (k * 45 - 90) * PI / 180
                val label = measurer.measure(points[k], captionStyle.copy(fontSize = 10.sp, color = colors.paperInkSoft))
                drawText(label, topLeft = Offset(c.x + (r - 13.dp.toPx()) * cos(a).toFloat() - label.size.width / 2, c.y + (r - 13.dp.toPx()) * sin(a).toFloat() - label.size.height / 2))
            }
            val t = clock.seconds.floatValue
            val wobble = sin(t * 2.3f) * (2f + gustiness * 9f) + sin(t * 5.1f) * gustiness * 3f
            rotate(((m.windDirection + 180f) % 360f) * appear + wobble, c) {
                val len = r * 0.62f
                // A hairline needle: the pigment points where the wind goes.
                drawLine(colors.accent, Offset(c.x, c.y + len * 0.7f), Offset(c.x, c.y - len), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(colors.accent, Offset(c.x, c.y - len), Offset(c.x - 4.dp.toPx(), c.y - len + 7.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(colors.accent, Offset(c.x, c.y - len), Offset(c.x + 4.dp.toPx(), c.y - len + 7.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(colors.paper, 3.dp.toPx(), c)
            drawCircle(colors.paperInk, 3.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun HumidityTile(m: WeatherMoment, fmt: WeatherFormat, modifier: Modifier) {
    val clock = LocalSceneClock.current
    val colors = Paper.colors
    val water = colors.rainInk
    FlipTile(
        2, stringResource(R.string.tile_humidity), fmt.percent(m.humidity),
        m.dewPoint?.let { stringResource(R.string.tile_dew_point, fmt.temp(it)) } ?: stringResource(R.string.tile_humidity_caption),
        stringResource(R.string.tile_humidity_back), modifier,
    ) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            val w = size.minDimension * 0.62f
            val left = (size.width - w) / 2
            val top = 6.dp.toPx()
            val bottom = size.height - 4.dp.toPx()
            val jar = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, bottom, CornerRadius(10.dp.toPx())))
            }
            val level = bottom - (bottom - top) * (m.humidity / 100f) * appear
            val t = clock.seconds.floatValue
            clipPath(jar) {
                val wave = Path().apply {
                    moveTo(left, bottom)
                    var x = left
                    while (x <= left + w) {
                        lineTo(x, level + sin(x / 9f + t * 1.6f) * 2.dp.toPx())
                        x += 3f
                    }
                    lineTo(left + w, bottom)
                    close()
                }
                drawPath(wave, water.copy(alpha = 0.28f))
            }
            drawPath(jar, colors.paperInk.copy(alpha = 0.45f), style = Stroke(1.2.dp.toPx()))
            for (k in 1..3) {
                val y = bottom - (bottom - top) * k / 4f
                drawLine(colors.paperInk.copy(alpha = 0.3f), Offset(left + w - 6.dp.toPx(), y), Offset(left + w, y), 1f)
            }
        }
    }
}

@Composable
private fun UvTile(f: Forecast, m: WeatherMoment, nowSec: Long, modifier: Modifier) {
    val colors = Paper.colors
    val uv = m.uvIndex
    val max = f.dayAt(nowSec)?.uvMax ?: uv
    val level = when {
        uv < 3 -> R.string.uv_low
        uv < 6 -> R.string.uv_moderate
        uv < 8 -> R.string.uv_high
        uv < 11 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    }
    FlipTile(
        3, stringResource(R.string.tile_uv), "${uv.roundToInt()} · ${stringResource(level)}",
        stringResource(R.string.tile_uv_caption, max.roundToInt().toString()), stringResource(R.string.tile_uv_back), modifier,
    ) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            // The pointer bead must stay inside the drawing, clear of the value below it.
            val r = minOf(size.width / 2 - 12.dp.toPx(), size.height - 18.dp.toPx())
            val c = Offset(size.width / 2, size.height - 11.dp.toPx())
            val stops = listOf(Color(0xFF9CB2A2), Color(0xFFCFB073), Color(0xFFC98457), Color(0xFFAE4936), Color(0xFF7A5A8C))
            drawArc(
                Brush.sweepGradient(listOf(stops[4]) + stops + listOf(stops[0]), center = c),
                180f, 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
            val frac = (uv / 11.0).coerceIn(0.0, 1.0).toFloat() * appear
            val a = PI * (1 + frac)
            val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            drawCircle(colors.paper, 7.dp.toPx(), p)
            drawCircle(colors.paperInk, 7.dp.toPx(), p, style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun PressureTile(f: Forecast, m: WeatherMoment, fmt: WeatherFormat, nowSec: Long, modifier: Modifier) {
    val colors = Paper.colors
    val later = f.hourly.getOrNull(f.hourIndexAt(nowSec) + 3)?.pressure ?: m.pressure
    val trend = later - m.pressure
    val trendText = stringResource(
        when {
            trend > 1.0 -> R.string.pressure_rising
            trend < -1.0 -> R.string.pressure_falling
            else -> R.string.pressure_steady
        },
    )
    FlipTile(4, stringResource(R.string.tile_pressure), fmt.pressure(m.pressure), trendText, stringResource(R.string.tile_pressure_back), modifier) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            val c = Offset(size.width / 2, size.height * 0.62f)
            val r = minOf(size.width / 2, size.height * 0.6f) - 4.dp.toPx()
            for (k in 0..16) {
                val a = PI * (0.75 + k / 16.0 * 1.5)
                val inner = if (k % 4 == 0) r - 9.dp.toPx() else r - 5.dp.toPx()
                drawLine(colors.paperInk.copy(alpha = 0.35f), Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 1f)
            }
            val frac = ((m.pressure - 970) / 80.0).coerceIn(0.0, 1.0).toFloat()
            val a = PI * (0.75 + frac * 1.5 * appear)
            drawLine(colors.accent, c, Offset(c.x + (r - 12.dp.toPx()) * cos(a).toFloat(), c.y + (r - 12.dp.toPx()) * sin(a).toFloat()), 1.5.dp.toPx(), StrokeCap.Round)
            // Trend arrow as a small ghost needle.
            val ft = ((later - 970) / 80.0).coerceIn(0.0, 1.0).toFloat()
            val at = PI * (0.75 + ft * 1.5)
            if (abs(trend) > 0.3) drawLine(colors.accent.copy(alpha = 0.3f), c, Offset(c.x + (r - 16.dp.toPx()) * cos(at).toFloat(), c.y + (r - 16.dp.toPx()) * sin(at).toFloat()), 1.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.paper, 3.dp.toPx(), c)
            drawCircle(colors.paperInk, 3.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun SunTile(f: Forecast, fmt: WeatherFormat, nowSec: Long, modifier: Modifier) {
    val colors = Paper.colors
    val day = f.dayAt(nowSec) ?: return
    val next = f.daily.getOrNull(f.dayIndexAt(nowSec) + 1)
    val progress = Astro.sunProgress(nowSec, day.sunrise, day.sunset, true)
    val isDay = progress in 0f..1f
    val value = if (isDay) fmt.time(day.sunset) else fmt.time(if (nowSec < day.sunrise) day.sunrise else next?.sunrise ?: day.sunrise)
    val caption = stringResource(if (isDay) R.string.tile_sun_sets else R.string.tile_sun_rises) + " · " + fmt.duration(day.daylightSeconds.toLong())
    FlipTile(5, stringResource(R.string.tile_sun), value, caption, stringResource(R.string.tile_sun_back), modifier) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val base = size.height - 10.dp.toPx()
            val top = 8.dp.toPx()
            val arc = Path().apply {
                moveTo(left, base)
                cubicTo(left + (right - left) * 0.2f, top, left + (right - left) * 0.8f, top, right, base)
            }
            drawPath(arc, colors.paperInk.copy(alpha = 0.3f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx()))))
            drawLine(colors.paperInk.copy(alpha = 0.35f), Offset(0f, base), Offset(size.width, base), 1f)
            val p = progress.coerceIn(0f, 1f) * appear
            fun bez(t: Float, a: Float, b: Float, c: Float, d: Float) = (1 - t) * (1 - t) * (1 - t) * a + 3 * (1 - t) * (1 - t) * t * b + 3 * (1 - t) * t * t * c + t * t * t * d
            val x = bez(p, left, left + (right - left) * 0.2f, left + (right - left) * 0.8f, right)
            val y = bez(p, base, top, top, base)
            if (isDay) {
                drawCircle(colors.accent.copy(alpha = 0.18f), 11.dp.toPx(), Offset(x, y), style = Stroke(1f))
                drawCircle(colors.accent, 6.dp.toPx(), Offset(x, y))
            } else {
                val m = Offset(size.width / 2, top + 14.dp.toPx())
                drawCircle(colors.paperInk.copy(alpha = 0.8f), 7.dp.toPx(), m)
                drawCircle(colors.paper, 6.dp.toPx(), Offset(m.x + 3.5.dp.toPx(), m.y - 2.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PrecipTile(f: Forecast, fmt: WeatherFormat, nowSec: Long, modifier: Modifier) {
    val colors = Paper.colors
    val next = f.hoursFrom(nowSec, 12)
    val today = f.dayAt(nowSec)
    val water = colors.rainInk
    FlipTile(
        6, stringResource(R.string.tile_precip), fmt.precip(next.sumOf { it.precipitation }),
        stringResource(R.string.tile_precip_caption, fmt.precip(today?.precipSum ?: 0.0)), stringResource(R.string.tile_precip_back), modifier,
    ) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            if (next.isEmpty()) return@Canvas
            val w = size.width / next.size
            next.forEachIndexed { i, h ->
                val chance = h.precipProbability / 100f
                val bh = (size.height - 6.dp.toPx()) * chance * appear
                drawLine(colors.paperInk.copy(alpha = 0.1f), Offset(i * w + w / 2, 0f), Offset(i * w + w / 2, size.height), 1f)
                if (bh > 0) drawRoundRect(water.copy(alpha = 0.35f + 0.5f * (h.precipitation / 3.0).toFloat().coerceIn(0f, 1f)), Offset(i * w + w * 0.34f, size.height - bh), Size(w * 0.32f, bh), CornerRadius(w * 0.16f))
            }
        }
    }
}

@Composable
private fun FeelsTile(m: WeatherMoment, fmt: WeatherFormat, modifier: Modifier) {
    val colors = Paper.colors
    val diff = m.feelsLike - m.temperature
    val reason = stringResource(
        when {
            diff <= -2 && m.windSpeed > 4 -> R.string.feels_windy
            diff >= 2 && m.humidity > 60 -> R.string.feels_humid
            diff >= 2 -> R.string.feels_sunny
            else -> R.string.feels_same
        },
    )
    FlipTile(7, stringResource(R.string.tile_feels), fmt.temp(m.feelsLike), reason, stringResource(R.string.tile_feels_back), modifier) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            val cx = size.width / 2
            val tubeW = 10.dp.toPx()
            val top = 6.dp.toPx()
            val bulbR = 10.dp.toPx()
            val bulbY = size.height - bulbR - 2.dp.toPx()
            val frac = ((m.feelsLike + 25) / 65).toFloat().coerceIn(0.05f, 1f) * appear
            val fillTop = bulbY - (bulbY - top) * frac
            val color = tempColor(m.feelsLike)
            drawRoundRect(colors.paperInk.copy(alpha = 0.4f), Offset(cx - tubeW / 2, top), Size(tubeW, bulbY - top), CornerRadius(tubeW / 2), style = Stroke(1.2.dp.toPx()))
            drawRoundRect(color, Offset(cx - 1.5.dp.toPx(), fillTop), Size(3.dp.toPx(), bulbY - fillTop), CornerRadius(1.5.dp.toPx()))
            drawCircle(colors.paper, bulbR, Offset(cx, bulbY))
            drawCircle(colors.paperInk.copy(alpha = 0.4f), bulbR, Offset(cx, bulbY), style = Stroke(1.2.dp.toPx()))
            drawCircle(color, bulbR - 3.5.dp.toPx(), Offset(cx, bulbY))
            for (k in 0..4) {
                val y = top + 6.dp.toPx() + k * (bulbY - top - 20.dp.toPx()) / 4
                drawLine(colors.paperInk.copy(alpha = 0.3f), Offset(cx + tubeW / 2 + 4.dp.toPx(), y), Offset(cx + tubeW / 2 + (if (k % 2 == 0) 10 else 7).dp.toPx(), y), 1f)
            }
        }
    }
}

@Composable
private fun MoonTile(fmt: WeatherFormat, nowSec: Long, modifier: Modifier) {
    val colors = Paper.colors
    val phase = Astro.moonPhase(nowSec)
    val lit = (Astro.moonIllumination(phase) * 100).roundToInt()
    FlipTile(8, stringResource(R.string.tile_moon), fmt.moonPhase(phase), stringResource(R.string.tile_moon_caption, lit), stringResource(R.string.tile_moon_back), modifier) { appearOf ->
        Canvas(Modifier.fillMaxSize()) {
            val appear = appearOf()
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 6.dp.toPx()
            // On dark (night) paper the shadow must be darker than the paper, not the light ink.
            val shade = if (colors.isNight) Color(0xFF0B1022).copy(alpha = 0.82f) else colors.paperInk.copy(alpha = 0.72f)
            drawMoonPhase(c, r * (0.85f + 0.15f * appear), phase, Color(0xFFEDE4CC), shade)
        }
    }
}

/** Moon disc with its terminator (lit part light, shadow dark). */
fun DrawScope.drawMoonPhase(c: Offset, r: Float, phase: Float, light: Color, dark: Color) {
    drawCircle(light, r, c)
    val k = cos(2 * PI * phase).toFloat()
    val waxing = phase < 0.5f
    val path = Path().apply {
        arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, if (waxing) -180f else 180f, true)
        val ex = r * abs(k)
        val sweep = if (waxing) (if (k > 0) -180f else 180f) else (if (k > 0) 180f else -180f)
        arcTo(androidx.compose.ui.geometry.Rect(c.x - ex, c.y - r, c.x + ex, c.y + r), 90f, sweep, false)
        close()
    }
    if (k > -0.97f) drawPath(path, dark)
    drawCircle(dark.copy(alpha = 0.35f), r, c, style = Stroke(1f))
}
