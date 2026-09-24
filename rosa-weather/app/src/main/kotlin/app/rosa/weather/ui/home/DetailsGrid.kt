package app.rosa.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.R as DsR
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.numeralText
import app.rosa.weather.core.model.AirLevel
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Right now, in detail": a grid of frosted tiles, each with its own small living instrument —
 * a compass whose needle swings into the wind, humidity as liquid that sloshes, a UV arc, a
 * barometer, the sun's path with today's position and the moon in its real phase. Returned as
 * rows of two, so a list can compose them one row at a time as they scroll into view.
 */
fun detailRows(forecast: Forecast, moment: ForecastMoment, format: WeatherFormat): List<List<@Composable (Modifier) -> Unit>> {
    val today = forecast.dayAt(moment.epochSeconds)
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        add { m -> FeelsLikeTile(moment, format, m) }
        add { m -> WindTile(moment, format, m) }
        add { m -> HumidityTile(moment, format, m) }
        add { m -> UvTile(moment, format, m) }
        if (today?.sunrise != null && today.sunset != null) add { m -> SunTile(today.sunrise!!, today.sunset!!, moment.epochSeconds, format, m) }
        add { m -> PressureTile(moment, format, m) }
        add { m -> MoonTile(moment, format, m) }
        if (forecast.air?.europeanAqi != null) add { m -> AirTile(forecast, format, m) }
        if (moment.visibility != null) add { m -> VisibilityTile(moment, format, m) }
        if (today != null) add { m -> PrecipitationTile(today.precipitationSum, today.precipitationProbabilityMax, format, m) }
    }
    return tiles.chunked(2)
}

@Composable
fun DetailRow(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { tile -> tile(Modifier.weight(1f).aspectRatio(1f)) }
        if (tiles.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier, subtitle: String? = null, visual: @Composable BoxScope.() -> Unit) {
    val colors = Rosa.colors
    GlassSurface(
        modifier.clearAndSetSemantics { contentDescription = listOfNotNull(title, value, subtitle).joinToString(", ") },
        style = GlassStyle.Frosted,
        cornerRadius = 26.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(title, style = Rosa.type.label, color = colors.inkSoft, maxLines = 1)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center, content = visual)
            Text(numeralText(value), style = Rosa.type.numeral, color = colors.ink, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = Rosa.type.caption, color = colors.inkSoft, maxLines = 2)
        }
    }
}

@Composable
private fun FeelsLikeTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    val delta = m.apparentTemperature - m.temperature
    val hint = stringResource(
        when {
            delta > 1.5 -> R.string.feels_warmer
            delta < -1.5 -> R.string.feels_colder
            else -> R.string.feels_same
        },
    )
    Tile(stringResource(DsR.string.detail_feels_like), f.temperature(m.apparentTemperature), modifier, hint) {
        val colors = Rosa.colors
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            // Actual vs felt on a small thermometer line.
            val y = size.height / 2
            drawLine(colors.ink.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
            val span = 20f
            fun x(t: Double) = (((t - m.temperature) / span).toFloat() * 0.5f + 0.5f).coerceIn(0.05f, 0.95f) * size.width
            drawCircle(colors.inkSoft, 5.dp.toPx(), Offset(x(m.temperature), y))
            drawCircle(colors.accent, 7.dp.toPx(), Offset(x(m.apparentTemperature), y))
        }
    }
}

@Composable
private fun WindTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    val needle = remember { Animatable(m.windDirection.toFloat() - 60f) }
    LaunchedEffect(m.windDirection) { needle.animateTo(m.windDirection.toFloat(), RosaMotion.gel()) }
    val motion = LocalMotionEnabled.current
    val clock = LocalAmbientClock.current
    // Stronger wind, quicker flutter (one swing every 0.3–1.6 s), on the shared ambient clock.
    val swing = ((1600 - m.windSpeed * 60) / 1000.0).toFloat().coerceAtLeast(0.3f)
    Tile(
        stringResource(DsR.string.detail_wind),
        "${f.wind(m.windSpeed)} ${f.compass(m.windDirection)}",
        modifier,
        stringResource(DsR.string.detail_gusts, f.wind(m.windGusts)),
    ) {
        val colors = Rosa.colors
        Canvas(Modifier.size(78.dp)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(colors.ink.copy(alpha = 0.16f), r, c, style = Stroke(1.dp.toPx()))
            for (i in 0 until 36) {
                val a = i * 10 * PI / 180
                val len = if (i % 9 == 0) 8.dp.toPx() else 4.dp.toPx()
                drawLine(
                    colors.ink.copy(alpha = if (i % 9 == 0) 0.6f else 0.25f),
                    Offset(c.x + sin(a).toFloat() * (r - len), c.y - cos(a).toFloat() * (r - len)),
                    Offset(c.x + sin(a).toFloat() * r, c.y - cos(a).toFloat() * r),
                    1.2.dp.toPx(),
                )
            }
            val flutter = sin(clock.seconds * PI.toFloat() / swing)
            val wobble = if (motion) flutter * (1.5f + m.windGusts.toFloat() * 0.25f) else 0f
            // The needle points where the wind blows *to*.
            rotate(needle.value + 180f + wobble, c) {
                val path = Path().apply {
                    moveTo(c.x, c.y - r * 0.78f)
                    lineTo(c.x + r * 0.13f, c.y + r * 0.1f)
                    lineTo(c.x, c.y - r * 0.02f)
                    lineTo(c.x - r * 0.13f, c.y + r * 0.1f)
                    close()
                }
                drawPath(path, colors.accent)
                drawLine(colors.ink.copy(alpha = 0.5f), Offset(c.x, c.y), Offset(c.x, c.y + r * 0.62f), 2.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(colors.ink, 3.dp.toPx(), c)
        }
    }
}

@Composable
private fun HumidityTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    val motion = LocalMotionEnabled.current
    val clock = LocalAmbientClock.current
    val level = remember { Animatable(0f) }
    LaunchedEffect(m.humidity) { level.animateTo(m.humidity / 100f, RosaMotion.gel()) }
    Tile(
        stringResource(DsR.string.detail_humidity),
        f.percent(m.humidity),
        modifier,
        m.dewPoint?.let { stringResource(R.string.humidity_dew, f.temperature(it)) },
    ) {
        val colors = Rosa.colors
        Canvas(Modifier.size(width = 54.dp, height = 70.dp)) {
            // A glass drop filled to the humidity level, with a gently moving surface.
            val w = size.width
            val h = size.height
            val drop = Path().apply {
                moveTo(w / 2, 0f)
                cubicTo(w * 0.55f, h * 0.2f, w, h * 0.45f, w, h * 0.66f)
                cubicTo(w, h * 0.88f, w * 0.78f, h, w / 2, h)
                cubicTo(w * 0.22f, h, 0f, h * 0.88f, 0f, h * 0.66f)
                cubicTo(0f, h * 0.45f, w * 0.45f, h * 0.2f, w / 2, 0f)
                close()
            }
            drawPath(drop, colors.ink.copy(alpha = 0.1f))
            val surface = h * (1f - level.value)
            val water = Path().apply {
                moveTo(0f, h)
                var x = 0f
                lineTo(0f, surface)
                val phase = clock.seconds * 2f * PI.toFloat() / 2.6f
                while (x <= w) {
                    val y = surface + if (motion) sin(phase + x / w * 2f * PI.toFloat()) * 2.5.dp.toPx() else 0f
                    lineTo(x, y)
                    x += 2f
                }
                lineTo(w, h)
                close()
            }
            val clip = Path().apply { op(drop, water, androidx.compose.ui.graphics.PathOperation.Intersect) }
            drawPath(clip, Brush.verticalGradient(listOf(colors.rain.copy(alpha = 0.55f), colors.rain), startY = surface, endY = h))
            drawPath(drop, colors.ink.copy(alpha = 0.55f), style = Stroke(1.4.dp.toPx()))
        }
    }
}

@Composable
private fun UvTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(m.uvIndex) { sweep.animateTo((m.uvIndex / 11.0).toFloat().coerceIn(0f, 1f), RosaMotion.gel()) }
    Tile(stringResource(DsR.string.detail_uv), "${m.uvIndex.roundToInt()}", modifier, f.uvLevel(m.uvIndex)) {
        val colors = Rosa.colors
        Canvas(Modifier.size(84.dp, 48.dp)) {
            val stroke = 8.dp.toPx()
            val rect = Size(size.width - stroke, (size.width - stroke))
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(colors.ink.copy(alpha = 0.14f), 180f, 180f, false, topLeft, rect, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(Color(0xFF8EE6C4), Color(0xFFFFD27A), Color(0xFFFF8A5C), Color(0xFFC77DFF), Color(0xFF8EE6C4))),
                180f, 180f * sweep.value, false, topLeft, rect, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun SunTile(sunrise: Long, sunset: Long, now: Long, f: WeatherFormat, modifier: Modifier) {
    val daylightMinutes = ((sunset - sunrise) / 60).toInt()
    Tile(
        stringResource(if (now < sunset && now >= sunrise) DsR.string.detail_sunset else DsR.string.detail_sunrise),
        f.time(if (now < sunset && now >= sunrise) sunset else sunrise),
        modifier,
        "${stringResource(DsR.string.detail_daylight)} ${f.minutes(daylightMinutes)}",
    ) {
        val colors = Rosa.colors
        val t = ((now - sunrise).toFloat() / (sunset - sunrise)).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val base = size.height - 4.dp.toPx()
            val path = Path().apply {
                moveTo(0f, base)
                cubicTo(size.width * 0.2f, -size.height * 0.2f, size.width * 0.8f, -size.height * 0.2f, size.width, base)
            }
            drawPath(path, colors.ink.copy(alpha = 0.3f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            drawLine(colors.ink.copy(alpha = 0.25f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            val x = size.width * t
            val y = base - (base + size.height * 0.2f) * 0.75f * 4f * t * (1 - t)
            drawCircle(Color(0x66FFD27A), 11.dp.toPx(), Offset(x, y))
            drawCircle(Color(0xFFFFF1C4), 5.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun PressureTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    val needle = remember { Animatable(0f) }
    LaunchedEffect(m.pressure) { needle.animateTo(((m.pressure - 980) / 60).toFloat().coerceIn(0f, 1f), RosaMotion.gel()) }
    Tile(stringResource(DsR.string.detail_pressure), f.pressureValue(m.pressure), modifier, f.pressureUnit()) {
        val colors = Rosa.colors
        Canvas(Modifier.size(80.dp)) {
            val r = size.minDimension / 2
            val start = 135f
            val sweepAll = 270f
            for (i in 0..30) {
                val a = Math.toRadians((start + sweepAll * i / 30).toDouble())
                val len = if (i % 5 == 0) 8.dp.toPx() else 4.dp.toPx()
                drawLine(
                    colors.ink.copy(alpha = if (i % 5 == 0) 0.55f else 0.22f),
                    Offset(center.x + cos(a).toFloat() * (r - len), center.y + sin(a).toFloat() * (r - len)),
                    Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r),
                    1.3.dp.toPx(),
                )
            }
            val a = Math.toRadians((start + sweepAll * needle.value).toDouble())
            drawLine(colors.accent, center, Offset(center.x + cos(a).toFloat() * r * 0.72f, center.y + sin(a).toFloat() * r * 0.72f), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.ink, 3.5.dp.toPx(), center)
        }
    }
}

@Composable
private fun MoonTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    Tile(stringResource(DsR.string.detail_moon), "${(m.moonPhase.illumination * 100).roundToInt()}%", modifier, f.moonPhase(m.moonPhase.phase)) {
        WeatherGlyph(WeatherCondition.Clear, isDay = false, moonPhase = m.moonPhase.phase, modifier = Modifier.size(72.dp))
    }
}

@Composable
private fun AirTile(forecast: Forecast, f: WeatherFormat, modifier: Modifier) {
    val air = forecast.air ?: return
    val aqi = air.europeanAqi ?: return
    Tile(stringResource(DsR.string.detail_air), "$aqi", modifier, f.airLevel(air.level)) {
        val colors = Rosa.colors
        val position = remember { Animatable(0f) }
        LaunchedEffect(aqi) { position.animateTo((aqi / 100f).coerceIn(0f, 1f), RosaMotion.gel()) }
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val y = size.height / 2
            drawLine(
                Brush.horizontalGradient(listOf(Color(0xFF8EE6C4), Color(0xFFD8E98A), Color(0xFFFFD27A), Color(0xFFFF8A5C), Color(0xFFC77DFF))),
                Offset(0f, y), Offset(size.width, y), 6.dp.toPx(), StrokeCap.Round,
            )
            val x = size.width * position.value
            drawCircle(colors.ink, 6.dp.toPx(), Offset(x, y))
            drawCircle(if (air.level <= AirLevel.Fair) Color(0xFF8EE6C4) else Color(0xFFFFD27A), 3.5.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun VisibilityTile(m: ForecastMoment, f: WeatherFormat, modifier: Modifier) {
    val meters = m.visibility ?: return
    Tile(stringResource(DsR.string.detail_visibility), f.visibility(meters), modifier, stringResource(R.string.visibility_hint).takeIf { meters > 10_000 }) {
        val colors = Rosa.colors
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val clear = (meters / 20_000.0).toFloat().coerceIn(0.05f, 1f)
            for (i in 0 until 4) {
                val y = size.height * (0.2f + i * 0.22f)
                drawLine(colors.ink.copy(alpha = (0.5f - i * 0.1f) * (0.4f + clear * 0.6f)), Offset(size.width * (0.1f + i * 0.05f), y), Offset(size.width * (0.9f - i * 0.05f), y), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PrecipitationTile(mm: Double, chance: Int, f: WeatherFormat, modifier: Modifier) {
    Tile(stringResource(DsR.string.detail_precipitation), f.precipitation(mm), modifier, "${stringResource(DsR.string.detail_chance)} $chance%") {
        WeatherGlyph(if (mm > 0.2) WeatherCondition.Rain else WeatherCondition.PartlyCloudy, isDay = true, animated = mm > 0.2, modifier = Modifier.size(64.dp))
    }
}
