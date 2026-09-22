package app.papersky.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.Astro
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.WeatherMoment
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.DeckleShape
import app.papersky.weather.design.Ink
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.Stock
import app.papersky.weather.design.beechBead
import app.papersky.weather.design.material
import app.papersky.weather.design.pressable
import app.papersky.weather.design.pressed
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.design.tiltFor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Eight paper tiles; each has a little moving illustration and flips over to explain itself. */
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f)) }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(12.dp)

/**
 * A chipboard tile (§8): a cotton label glued on top, a printed illustration that moves, the value.
 * Tap flips it over (`flip` spring) onto a handwritten note; it lands with a dull knock.
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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val showBack = rotation.value > 90f
    val tilt = tiltFor(seed * 13 + 5, 1f)
    val colors = Paper.colors
    Box(
        modifier
            .height(192.dp)
            .semantics { contentDescription = "$title: $value. $caption" }
            .graphicsLayer {
                rotationZ = tilt
                rotationY = rotation.value
                cameraDistance = 18f * density
            }
            .pressable({
                h.softTick()
                flipped = !flipped
                scope.launch { delay(320); h.cardboard() }
            }, haptic = false, pressed = 0.97f),
    ) {
        if (!showBack) {
            Column(Modifier.fillMaxSize().material(Stock.Chipboard, TileShape, level = 2).padding(12.dp)) {
                GluedLabel(title, seed)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) { art { appear.value } }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    value,
                    style = Paper.type.title.copy(color = colors.paperInk, fontSize = 24.sp, fontWeight = FontWeight(800), fontFeatureSettings = "tnum").pressed(),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp, stepSize = 1.sp),
                )
                BasicText(caption, style = Paper.type.caption.copy(color = Color(0xFF4A3F33)).pressed(), maxLines = 2)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .material(Stock.Chipboard, TileShape, level = 2)
                    .padding(10.dp),
            ) {
                // A slip of paper glued to the back with a note written on it.
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = tiltFor(seed * 7, 1.2f) }
                        .material(Stock.Notebook, DeckleShape(seed = seed * 11, corner = 6.dp), level = 1)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Label(title)
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        explanation,
                        style = Paper.type.handSmall.copy(color = colors.handInk, fontSize = 17.5.sp),
                        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 17.5.sp, stepSize = 0.5.sp),
                    )
                }
            }
        }
        // Edge-on, the tile shows its thickness and turns away from the light.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val edge = 1f - abs(90f - rotation.value % 180f) / 90f
                if (edge > 0.01f) {
                    drawRoundRect(Color.Black.copy(alpha = edge * 0.24f), cornerRadius = CornerRadius(12.dp.toPx()))
                    drawRoundRect(Color(0xFF8C7A5E).copy(alpha = edge), topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                }
            },
        )
    }
}

/** The title label: a strip of cotton paper glued onto the chipboard, slightly askew. */
@Composable
private fun GluedLabel(text: String, seed: Int) {
    Box(
        Modifier
            .graphicsLayer { rotationZ = tiltFor(seed * 31, 1.5f) }
            .material(Stock.Cotton, RoundedCornerShape(2.dp), level = 1)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) { Label(text, color = Paper.colors.paperInk) }
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
            drawCircle(colors.paperInk.copy(alpha = 0.12f), r, c, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))))
            listOf(0, 2, 4, 6).forEach { k ->
                val a = (k * 45 - 90) * PI / 180
                val label = measurer.measure(points[k], captionStyle.copy(fontSize = 10.sp, color = colors.paperInkSoft))
                drawText(label, topLeft = Offset(c.x + (r - 8.dp.toPx()) * cos(a).toFloat() - label.size.width / 2, c.y + (r - 8.dp.toPx()) * sin(a).toFloat() - label.size.height / 2))
            }
            val t = clock.seconds.floatValue
            val wobble = sin(t * 2.3f) * (2f + gustiness * 9f) + sin(t * 5.1f) * gustiness * 3f
            rotate(((m.windDirection + 180f) % 360f) * appear + wobble, c) {
                val len = r * 0.72f
                val p = Path().apply {
                    moveTo(c.x, c.y - len)
                    lineTo(c.x + 7.dp.toPx(), c.y - len + 14.dp.toPx())
                    lineTo(c.x + 2.dp.toPx(), c.y - len + 12.dp.toPx())
                    lineTo(c.x + 2.dp.toPx(), c.y + len * 0.7f)
                    lineTo(c.x - 2.dp.toPx(), c.y + len * 0.7f)
                    lineTo(c.x - 2.dp.toPx(), c.y - len + 12.dp.toPx())
                    lineTo(c.x - 7.dp.toPx(), c.y - len + 14.dp.toPx())
                    close()
                }
                // A paper arrow standing a little off the card: shadow first, then the paper.
                translate(1.2.dp.toPx(), 2.6.dp.toPx()) { drawPath(p, Ink.Shadow.copy(alpha = 0.22f)) }
                drawPath(p, Stock.Terracotta.base)
                drawPath(p, Color.White.copy(alpha = 0.18f), style = Stroke(0.8.dp.toPx()))
                // Paper fletching.
                for (k in 0..1) {
                    val y = c.y + len * (0.45f + k * 0.18f)
                    drawLine(colors.accent.copy(alpha = 0.7f), Offset(c.x, y), Offset(c.x - 7.dp.toPx(), y + 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.accent.copy(alpha = 0.7f), Offset(c.x, y), Offset(c.x + 7.dp.toPx(), y + 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            beechBead(c, 4.dp.toPx())
        }
    }
}

@Composable
private fun HumidityTile(m: WeatherMoment, fmt: WeatherFormat, modifier: Modifier) {
    val clock = LocalSceneClock.current
    val colors = Paper.colors
    val water = Color(0xFF5B8FC7)
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
                addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, bottom, CornerRadius(14.dp.toPx())))
            }
            val level = bottom - (bottom - top) * (m.humidity / 100f) * appear
            val t = clock.seconds.floatValue
            clipPath(jar) {
                val wave = Path().apply {
                    moveTo(left, bottom)
                    var x = left
                    while (x <= left + w) {
                        lineTo(x, level + sin(x / 9f + t * 2.2f) * 3.dp.toPx())
                        x += 3f
                    }
                    lineTo(left + w, bottom)
                    close()
                }
                drawPath(wave, water.copy(alpha = 0.55f))
                drawCircle(Color.White.copy(alpha = 0.4f), 2.5.dp.toPx(), Offset(left + w * 0.3f, level + ((t * 18) % (bottom - level + 1))))
            }
            drawPath(jar, Color.White.copy(alpha = 0.16f))
            drawPath(jar, colors.paperInk.copy(alpha = 0.45f), style = Stroke(1.6.dp.toPx()))
            // Glass catches the window light on its left shoulder.
            drawLine(Color.White.copy(alpha = 0.75f), Offset(left + 5.dp.toPx(), top + 10.dp.toPx()), Offset(left + 5.dp.toPx(), bottom - 12.dp.toPx()), 2.2.dp.toPx(), StrokeCap.Round)
            // Brass lid.
            val lidH = 6.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFF1D58E), Color(0xFFB08A34), Color(0xFF7A5A1C)), startY = top - lidH - 2.dp.toPx(), endY = top),
                Offset(left + w * 0.12f, top - lidH - 1.dp.toPx()), Size(w * 0.76f, lidH), CornerRadius(2.dp.toPx()),
            )
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
            val r = minOf(size.width / 2, size.height) - 8.dp.toPx()
            val c = Offset(size.width / 2, size.height - 4.dp.toPx())
            val stops = listOf(Color(0xFF8CC7B5), Color(0xFFE9C46A), Color(0xFFEF8F4E), Color(0xFFD9483B), Color(0xFF9B6BC3))
            drawArc(
                Brush.sweepGradient(listOf(stops[4]) + stops + listOf(stops[0]), center = c),
                180f, 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round),
            )
            val frac = (uv / 11.0).coerceIn(0.0, 1.0).toFloat() * appear
            val a = PI * (1 + frac)
            val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            beechBead(p, 9.dp.toPx())
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
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 5.dp.toPx()
            // A little brass barometer: rim, cream face, blued-steel needle.
            drawCircle(Ink.Shadow.copy(alpha = 0.25f), r + 5.dp.toPx(), c + Offset(1.dp.toPx(), 2.5.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF6DE9A), Color(0xFFB08A34), Color(0xFF6E4F18)), center = c - Offset(r * 0.5f, r * 0.6f), radius = r * 2.2f), r + 4.dp.toPx(), c)
            drawCircle(Color(0xFFF8F0DC), r, c)
            drawCircle(Ink.Shadow.copy(alpha = 0.18f), r, c, style = Stroke(1.5.dp.toPx()))
            for (k in 0..16) {
                val a = PI * (0.75 + k / 16.0 * 1.5)
                val inner = if (k % 4 == 0) r - 9.dp.toPx() else r - 5.dp.toPx()
                drawLine(colors.paperInk.copy(alpha = 0.35f), Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), 1.5.dp.toPx())
            }
            val frac = ((m.pressure - 970) / 80.0).coerceIn(0.0, 1.0).toFloat()
            val a = PI * (0.75 + frac * 1.5 * appear)
            drawLine(Ink.Shadow.copy(alpha = 0.2f), c + Offset(1.dp.toPx(), 2.dp.toPx()), Offset(c.x + (r - 8.dp.toPx()) * cos(a).toFloat() + 1.dp.toPx(), c.y + (r - 8.dp.toPx()) * sin(a).toFloat() + 2.dp.toPx()), 2.4.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFF2B3A55), c, Offset(c.x + (r - 8.dp.toPx()) * cos(a).toFloat(), c.y + (r - 8.dp.toPx()) * sin(a).toFloat()), 2.4.dp.toPx(), StrokeCap.Round)
            // Trend arrow as a small ghost needle.
            val ft = ((later - 970) / 80.0).coerceIn(0.0, 1.0).toFloat()
            val at = PI * (0.75 + ft * 1.5)
            if (abs(trend) > 0.3) drawLine(Ink.RedPencil.copy(alpha = 0.55f), c, Offset(c.x + (r - 14.dp.toPx()) * cos(at).toFloat(), c.y + (r - 14.dp.toPx()) * sin(at).toFloat()), 1.6.dp.toPx(), StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF6DE9A), Color(0xFFB08A34)), center = c - Offset(1.dp.toPx(), 1.dp.toPx()), radius = 5.dp.toPx()), 3.5.dp.toPx(), c)
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
            drawPath(arc, colors.paperInk.copy(alpha = 0.3f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
            drawLine(colors.paperInk.copy(alpha = 0.35f), Offset(0f, base), Offset(size.width, base), 1.5.dp.toPx())
            val p = progress.coerceIn(0f, 1f) * appear
            fun bez(t: Float, a: Float, b: Float, c: Float, d: Float) = (1 - t) * (1 - t) * (1 - t) * a + 3 * (1 - t) * (1 - t) * t * b + 3 * (1 - t) * t * t * c + t * t * t * d
            val x = bez(p, left, left + (right - left) * 0.2f, left + (right - left) * 0.8f, right)
            val y = bez(p, base, top, top, base)
            if (isDay) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD98A).copy(alpha = 0.7f), Color.Transparent), center = Offset(x, y), radius = 20.dp.toPx()), 20.dp.toPx(), Offset(x, y))
                drawCircle(Ink.Shadow.copy(alpha = 0.2f), 8.dp.toPx(), Offset(x + 1.dp.toPx(), y + 2.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A0), Color(0xFFF3A23F)), center = Offset(x - 3.dp.toPx(), y - 3.dp.toPx()), radius = 11.dp.toPx()), 8.dp.toPx(), Offset(x, y))
            } else {
                drawCircle(Color(0xFFF4E6C4), 8.dp.toPx(), Offset(size.width / 2, top + 14.dp.toPx()))
                drawCircle(colors.paper, 7.dp.toPx(), Offset(size.width / 2 + 4.dp.toPx(), top + 11.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PrecipTile(f: Forecast, fmt: WeatherFormat, nowSec: Long, modifier: Modifier) {
    val colors = Paper.colors
    val next = f.hoursFrom(nowSec, 12)
    val today = f.dayAt(nowSec)
    val water = Ink.RainOnPaper
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
                drawRoundRect(colors.paperInk.copy(alpha = 0.07f), Offset(i * w + w * 0.2f, 0f), Size(w * 0.6f, size.height), CornerRadius(w * 0.3f))
                if (bh > 0) {
                    val heavy = (h.precipitation / 3.0).toFloat().coerceIn(0f, 1f)
                    drawRoundRect(water.copy(alpha = 0.18f + 0.3f * heavy), Offset(i * w + w * 0.2f, size.height - bh), Size(w * 0.6f, bh), CornerRadius(w * 0.3f))
                    var yy = size.height - bh + 2.dp.toPx()
                    while (yy < size.height - 1.dp.toPx()) {
                        drawLine(water.copy(alpha = 0.5f + 0.4f * heavy), Offset(i * w + w * 0.22f, yy + 2.dp.toPx()), Offset(i * w + w * 0.78f, yy), 0.9.dp.toPx())
                        yy += 2.8.dp.toPx()
                    }
                }
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
            val tubeW = 12.dp.toPx()
            val top = 6.dp.toPx()
            val bulbR = 13.dp.toPx()
            val bulbY = size.height - bulbR - 2.dp.toPx()
            val frac = ((m.feelsLike + 25) / 65).toFloat().coerceIn(0.05f, 1f) * appear
            val fillTop = bulbY - (bulbY - top) * frac
            val color = tempColor(m.feelsLike)
            drawRoundRect(colors.paperInk.copy(alpha = 0.1f), Offset(cx - tubeW / 2, top), Size(tubeW, bulbY - top), CornerRadius(tubeW / 2))
            drawRoundRect(color, Offset(cx - tubeW / 2 + 3.dp.toPx(), fillTop), Size(tubeW - 6.dp.toPx(), bulbY - fillTop), CornerRadius(tubeW / 2))
            drawCircle(color, bulbR, Offset(cx, bulbY))
            drawCircle(colors.paperInk.copy(alpha = 0.3f), bulbR, Offset(cx, bulbY), style = Stroke(1.5.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx - tubeW / 2 + 2.dp.toPx(), top + 5.dp.toPx()), Offset(cx - tubeW / 2 + 2.dp.toPx(), bulbY - bulbR), 1.6.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = 0.55f), bulbR * 0.28f, Offset(cx - bulbR * 0.4f, bulbY - bulbR * 0.4f))
            for (k in 0..4) {
                val y = top + 6.dp.toPx() + k * (bulbY - top - 20.dp.toPx()) / 4
                drawLine(colors.paperInk.copy(alpha = 0.3f), Offset(cx + tubeW / 2 + 4.dp.toPx(), y), Offset(cx + tubeW / 2 + 10.dp.toPx(), y), 1.5.dp.toPx())
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
            val shade = Color(0xFF2A2F45).copy(alpha = 0.85f)
            drawMoonPhase(c, r * (0.85f + 0.15f * appear), phase, Color(0xFFF1E4C3), shade)
        }
    }
}

/** Moon disc with its terminator (lit part light, shadow dark). */
fun DrawScope.drawMoonPhase(c: Offset, r: Float, phase: Float, light: Color, dark: Color) {
    drawCircle(light, r, c)
    // Maria and craters.
    drawCircle(Color(0xFFB9A57E).copy(alpha = 0.35f), r * 0.22f, Offset(c.x - r * 0.28f, c.y - r * 0.18f))
    drawCircle(Color(0xFFB9A57E).copy(alpha = 0.28f), r * 0.14f, Offset(c.x + r * 0.3f, c.y + r * 0.25f))
    drawCircle(Color(0xFFB9A57E).copy(alpha = 0.25f), r * 0.09f, Offset(c.x + r * 0.05f, c.y - r * 0.5f))
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
    drawCircle(dark.copy(alpha = 0.3f), r, c, style = Stroke(1.dp.toPx()))
}
