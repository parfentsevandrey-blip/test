package com.cozyhome.weather.ui.scenes

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.cozyhome.weather.data.WeatherKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Continuous scene clock. The value is only read inside draw lambdas,
 * so every frame invalidates the draw phase alone — no recomposition.
 */
@Composable
fun rememberSceneSeconds(): State<Float> = produceState(0f) {
    var start = -1L
    while (true) {
        withInfiniteAnimationFrameNanos { nanos ->
            if (start < 0) start = nanos
            value = (nanos - start) / 1_000_000_000f
        }
    }
}

/** Deterministic pseudo-random in [0, 1) so particles are stable across frames. */
private fun hash(i: Int): Float {
    val x = sin(i * 127.1f + 311.7f) * 43758.547f
    return x - floor(x)
}

@Composable
fun SceneBackground(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    val time = rememberSceneSeconds()
    Crossfade(
        targetState = kind to isDay,
        animationSpec = tween(1100),
        label = "scene",
    ) { (k, day) ->
        Canvas(modifier.fillMaxSize()) {
            val t = time.value
            when (k) {
                WeatherKind.CLEAR -> if (day) drawSunnyFan(t) else drawNight(t)
                WeatherKind.PARTLY -> drawPartly(t, day)
                WeatherKind.CLOUDY -> drawCloudyTea(t, day)
                WeatherKind.FOG -> drawFog(t, day)
                WeatherKind.RAIN -> drawRainyHearth(t, day)
                WeatherKind.SNOW -> drawSnow(t, day)
                WeatherKind.THUNDER -> drawThunder(t)
            }
            // bottom scrim so floating cards stay readable over any scene
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.30f),
                )
            )
        }
    }
}

private fun DrawScope.sky(colors: List<Color>) {
    drawRect(brush = Brush.verticalGradient(colors, startY = 0f, endY = size.height))
}

// ---------------------------------------------------------------- rain + hearth

private fun DrawScope.drawRainyHearth(t: Float, day: Boolean) {
    val w = size.width
    val h = size.height
    sky(
        if (day) listOf(Color(0xFF4A5568), Color(0xFF3A4152), Color(0xFF262232))
        else listOf(Color(0xFF2A2F45), Color(0xFF1D1B2C), Color(0xFF17131F))
    )
    drawRain(t, count = 90)
    drawHearth(t)
}

private fun DrawScope.drawRain(t: Float, count: Int) {
    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val speed = 0.5f + hash(i) * 0.7f
        val x = hash(i * 13) * w + sin(t + i) * 6f
        val y = ((t * speed + hash(i * 7)) % 1f) * (h * 1.1f) - h * 0.05f
        drawLine(
            color = Color.White.copy(alpha = 0.14f + hash(i * 5) * 0.14f),
            start = Offset(x, y),
            end = Offset(x + 7f, y + 26f),
            strokeWidth = 1.8f + hash(i * 11) * 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawHearth(t: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val baseY = h * 0.86f
    val flicker = 0.85f + 0.15f * sin(t * 11f) * sin(t * 5.3f)

    val glowCenter = Offset(cx, baseY - h * 0.03f)
    val glowRadius = w * 0.45f * flicker
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFF9800).copy(alpha = 0.30f * flicker), Color.Transparent),
            center = glowCenter,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = glowCenter,
    )

    val logColor = Color(0xFF5D4037)
    rotate(degrees = -14f, pivot = Offset(cx, baseY)) {
        drawRoundRect(
            logColor,
            topLeft = Offset(cx - w * 0.09f, baseY - 10f),
            size = Size(w * 0.18f, 20f),
            cornerRadius = CornerRadius(10f),
        )
    }
    rotate(degrees = 14f, pivot = Offset(cx, baseY)) {
        drawRoundRect(
            logColor.copy(alpha = 0.92f),
            topLeft = Offset(cx - w * 0.09f, baseY - 10f),
            size = Size(w * 0.18f, 20f),
            cornerRadius = CornerRadius(10f),
        )
    }

    flameLayer(cx, baseY, w * 0.16f, h * 0.16f, t, 0f, Color(0xFFE65100).copy(alpha = 0.9f))
    flameLayer(cx, baseY, w * 0.11f, h * 0.12f, t, 1.7f, Color(0xFFFF9800).copy(alpha = 0.95f))
    flameLayer(cx, baseY, w * 0.06f, h * 0.075f, t, 3.1f, Color(0xFFFFD54F))

    for (i in 0 until 14) {
        val cycle = (t * (0.35f + hash(i) * 0.4f) + hash(i * 3)) % 1f
        val sy = baseY - h * 0.02f - cycle * h * 0.30f
        val sx = cx + (hash(i * 7) - 0.5f) * w * 0.16f + sin(t * 2f + i) * 14f * cycle
        drawCircle(
            Color(0xFFFFB74D),
            radius = 3.5f * (1f - cycle) + 0.5f,
            center = Offset(sx, sy),
            alpha = (1f - cycle) * 0.9f,
        )
    }
}

private fun DrawScope.flameLayer(
    cx: Float,
    baseY: Float,
    w: Float,
    h: Float,
    t: Float,
    phase: Float,
    color: Color,
) {
    val hh = h * (0.85f + 0.15f * sin(t * 9f + phase) * sin(t * 6.7f + phase * 2f))
    val tip = cx + sin(t * 7f + phase) * w * 0.25f
    val path = Path().apply {
        moveTo(cx - w / 2f, baseY)
        quadraticTo(cx - w * 0.75f, baseY - hh * 0.45f, tip, baseY - hh)
        quadraticTo(cx + w * 0.75f, baseY - hh * 0.45f, cx + w / 2f, baseY)
        close()
    }
    drawPath(path, color)
}

// ---------------------------------------------------------------- sunny + fan

private fun DrawScope.drawSunnyFan(t: Float) {
    val w = size.width
    val h = size.height
    sky(listOf(Color(0xFF64B5F6), Color(0xFFB3E5FC), Color(0xFFFFE0B2)))

    // sun with slowly rotating rays
    val sun = Offset(w * 0.22f, h * 0.16f)
    val sunR = w * 0.10f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF176).copy(alpha = 0.55f), Color.Transparent),
            center = sun,
            radius = sunR * 3.2f,
        ),
        radius = sunR * 3.2f,
        center = sun,
    )
    rotate(degrees = (t * 6f) % 360f, pivot = sun) {
        for (k in 0 until 12) {
            rotate(degrees = k * 30f, pivot = sun) {
                drawLine(
                    Color(0xFFFFD54F).copy(alpha = 0.9f),
                    start = Offset(sun.x, sun.y - sunR - 14f),
                    end = Offset(sun.x, sun.y - sunR - 38f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
    drawCircle(Color(0xFFFFEE58), radius = sunR, center = sun)

    // cozy room fan, spinning fast
    val fan = Offset(w * 0.76f, h * 0.62f)
    val fr = w * 0.13f
    drawLine(Color(0xFF546E7A), Offset(fan.x, fan.y + fr), Offset(fan.x, fan.y + fr + 74f), strokeWidth = 9f, cap = StrokeCap.Round)
    drawOval(
        Color(0xFF546E7A),
        topLeft = Offset(fan.x - fr * 0.55f, fan.y + fr + 64f),
        size = Size(fr * 1.1f, 22f),
    )
    rotate(degrees = (t * 640f) % 360f, pivot = fan) {
        for (k in 0 until 3) {
            rotate(degrees = k * 120f, pivot = fan) {
                drawOval(
                    Color(0xFFB0BEC5).copy(alpha = 0.95f),
                    topLeft = Offset(fan.x - fr * 0.17f, fan.y - fr * 0.94f),
                    size = Size(fr * 0.34f, fr * 0.82f),
                )
            }
        }
    }
    drawCircle(Color(0xFF455A64), radius = fr * 0.14f, center = fan)
    drawCircle(Color(0xFF78909C), radius = fr, center = fan, style = Stroke(4f))
    drawCircle(Color(0xFF78909C).copy(alpha = 0.6f), radius = fr * 0.55f, center = fan, style = Stroke(2.5f))

    // breeze arcs drifting away from the fan
    for (j in 0 until 3) {
        val drift = (t * 90f + j * 60f) % 200f
        val alpha = (1f - drift / 200f) * 0.5f
        drawArc(
            color = Color.White.copy(alpha = alpha),
            startAngle = 130f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(fan.x - fr * 1.6f - drift, fan.y - fr * 0.9f),
            size = Size(fr * 1.5f, fr * 1.8f),
            style = Stroke(5f, cap = StrokeCap.Round),
        )
    }

    // drifting dust sparkles in the sunbeam
    for (i in 0 until 12) {
        val tw = 0.3f + 0.7f * abs(sin(t * (0.7f + hash(i)) + i))
        drawCircle(
            Color.White.copy(alpha = 0.35f * tw),
            radius = 2f + hash(i * 3) * 2f,
            center = Offset(
                w * (0.1f + 0.8f * hash(i * 7)) + sin(t * 0.4f + i) * 30f,
                h * (0.1f + 0.5f * hash(i * 11)) + sin(t * 0.23f + i * 2f) * 20f,
            ),
        )
    }
}

// ---------------------------------------------------------------- snow + candle

private fun DrawScope.drawSnow(t: Float, day: Boolean) {
    val w = size.width
    val h = size.height
    sky(
        if (day) listOf(Color(0xFF90A4AE), Color(0xFFB6C4CC), Color(0xFFCFD8DC))
        else listOf(Color(0xFF37474F), Color(0xFF2A363D), Color(0xFF1E272C))
    )

    for (layer in 0 until 3) {
        val radius = 2.5f + layer * 1.9f
        for (i in 0 until 18) {
            val idx = i + layer * 37
            val cycle = (t * (0.05f + 0.045f * layer) + hash(idx)) % 1f
            val y = cycle * (h + 40f) - 20f
            val x = hash(idx * 3) * w + sin(t * (0.5f + 0.2f * layer) + idx) * (18f + 14f * layer)
            drawCircle(
                Color.White.copy(alpha = 0.40f + 0.18f * layer),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }

    // candle
    val cx = w * 0.2f
    val baseY = h * 0.84f
    val halo = 0.8f + 0.2f * sin(t * 9f) * sin(t * 4.7f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFB74D).copy(alpha = 0.30f * halo), Color.Transparent),
            center = Offset(cx, baseY - 84f),
            radius = 130f * halo,
        ),
        radius = 130f * halo,
        center = Offset(cx, baseY - 84f),
    )
    drawRoundRect(
        Color(0xFFFFF3E0),
        topLeft = Offset(cx - 17f, baseY - 72f),
        size = Size(34f, 72f),
        cornerRadius = CornerRadius(10f),
    )
    flameLayer(cx, baseY - 74f, 18f, 36f, t, 0.6f, Color(0xFFFFB74D))
    flameLayer(cx, baseY - 74f, 10f, 24f, t, 1.9f, Color(0xFFFFF176))

    drawMug(t, w * 0.8f, baseY + 12f)
}

// ---------------------------------------------------------------- clouds + tea

private fun DrawScope.drawCloudyTea(t: Float, day: Boolean) {
    val w = size.width
    val h = size.height
    sky(
        if (day) listOf(Color(0xFF78909C), Color(0xFF9FB1BC), Color(0xFFB0BEC5))
        else listOf(Color(0xFF37404A), Color(0xFF2B333C), Color(0xFF20262D))
    )
    val cloudColor = if (day) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f)
    for (i in 0 until 5) {
        val s = w * (0.10f + 0.06f * hash(i * 5))
        val span = w + s * 4f
        val x = ((t * (10f + 6f * hash(i)) + hash(i * 9) * span) % span) - s * 2f
        val y = h * (0.08f + 0.30f * hash(i * 3))
        drawCloud(x, y, s, cloudColor)
    }
    drawMug(t, w * 0.5f, h * 0.86f)
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, s: Float, color: Color) {
    drawCircle(color, s * 0.55f, Offset(cx - s * 0.5f, cy))
    drawCircle(color, s * 0.72f, Offset(cx, cy - s * 0.28f))
    drawCircle(color, s * 0.55f, Offset(cx + s * 0.55f, cy))
    drawRoundRect(
        color,
        topLeft = Offset(cx - s, cy - s * 0.15f),
        size = Size(s * 2f, s * 0.62f),
        cornerRadius = CornerRadius(s * 0.31f),
    )
}

private fun DrawScope.drawMug(t: Float, cx: Float, bottomY: Float) {
    val mugW = 66f
    val mugH = 48f
    drawRoundRect(
        Color(0xFF8D6E63),
        topLeft = Offset(cx - mugW / 2f, bottomY - mugH),
        size = Size(mugW, mugH),
        cornerRadius = CornerRadius(14f),
    )
    drawArc(
        color = Color(0xFF8D6E63),
        startAngle = -70f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx + mugW / 2f - 8f, bottomY - mugH + 8f),
        size = Size(30f, 30f),
        style = Stroke(8f, cap = StrokeCap.Round),
    )
    for (s in 0 until 7) {
        val cycle = (t * 0.22f + s * 0.14f) % 1f
        val yy = bottomY - mugH - 8f - cycle * 110f
        val xx = cx + sin(t * 1.3f + s * 1.1f) * (6f + 14f * cycle)
        drawCircle(
            Color.White.copy(alpha = (1f - cycle) * 0.30f),
            radius = 5f + 7f * cycle,
            center = Offset(xx, yy),
        )
    }
}

// ---------------------------------------------------------------- fog

private fun DrawScope.drawFog(t: Float, day: Boolean) {
    val w = size.width
    val h = size.height
    sky(
        if (day) listOf(Color(0xFF9E9E9E), Color(0xFFB5B5B5), Color(0xFFCACACA))
        else listOf(Color(0xFF3C3F45), Color(0xFF2F3237), Color(0xFF25272B))
    )

    // warm lantern glow bleeding through the mist
    val pulse = 0.85f + 0.15f * sin(t * 1.3f)
    val lantern = Offset(w * 0.5f, h * 0.55f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFCC80).copy(alpha = 0.22f * pulse), Color.Transparent),
            center = lantern,
            radius = w * 0.45f * pulse,
        ),
        radius = w * 0.45f * pulse,
        center = lantern,
    )

    for (band in 0 until 5) {
        val yb = h * (0.25f + 0.15f * band)
        val path = Path().apply {
            moveTo(-20f, yb)
            for (xi in 0..24) {
                val x = xi / 24f * (w + 40f) - 20f
                val y = yb + sin(x * 0.008f + t * (0.35f + 0.09f * band) + band * 2.1f) * h * 0.022f
                lineTo(x, y)
            }
            lineTo(w + 20f, h + 20f)
            lineTo(-20f, h + 20f)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.055f + 0.028f * band))
    }
}

// ---------------------------------------------------------------- clear night

private fun DrawScope.drawNight(t: Float) {
    val w = size.width
    val h = size.height
    val skyTop = Color(0xFF232946)
    val skyMid = Color(0xFF15182E)
    sky(listOf(skyTop, skyMid, Color(0xFF0D0F1E)))

    for (i in 0 until 70) {
        val tw = 0.25f + 0.75f * abs(sin(t * (0.4f + hash(i * 7)) * 3f + i))
        drawCircle(
            Color.White.copy(alpha = 0.9f * tw),
            radius = 1f + hash(i * 11) * 1.8f,
            center = Offset(hash(i) * w, hash(i * 3) * h * 0.72f),
        )
    }

    // crescent moon
    val moon = Offset(w * 0.75f, h * 0.16f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF9C4).copy(alpha = 0.30f), Color.Transparent),
            center = moon,
            radius = 120f,
        ),
        radius = 120f,
        center = moon,
    )
    drawCircle(Color(0xFFFFF9C4), radius = 46f, center = moon)
    drawCircle(skyTop, radius = 44f, center = Offset(moon.x - 20f, moon.y - 10f))

    // fireflies
    for (i in 0 until 9) {
        val fx = w * (0.15f + 0.7f * hash(i * 5)) + sin(t * 0.37f + i * 2.1f) * 70f + sin(t * 0.9f + i) * 25f
        val fy = h * (0.45f + 0.4f * hash(i * 3)) + sin(t * 0.53f + i) * 45f
        val pulse = 0.35f + 0.65f * abs(sin(t * 1.7f + i * 1.3f))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFD4E157).copy(alpha = 0.5f * pulse), Color.Transparent),
                center = Offset(fx, fy),
                radius = 16f,
            ),
            radius = 16f,
            center = Offset(fx, fy),
        )
        drawCircle(Color(0xFFF0F4C3), radius = 2.6f, center = Offset(fx, fy), alpha = pulse)
    }
}

// ---------------------------------------------------------------- partly cloudy

private fun DrawScope.drawPartly(t: Float, day: Boolean) {
    val w = size.width
    val h = size.height
    if (day) {
        sky(listOf(Color(0xFF81D4FA), Color(0xFFB3E5FC), Color(0xFFE1F5FE)))
        val sun = Offset(w * 0.7f, h * 0.14f)
        val sunR = w * 0.08f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFF176).copy(alpha = 0.5f), Color.Transparent),
                center = sun,
                radius = sunR * 3f,
            ),
            radius = sunR * 3f,
            center = sun,
        )
        drawCircle(Color(0xFFFFEE58), radius = sunR, center = sun)
    } else {
        drawNight(t)
    }
    val cloudColor = if (day) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f)
    for (i in 0 until 3) {
        val s = w * (0.11f + 0.05f * hash(i * 5))
        val span = w + s * 4f
        val x = ((t * (12f + 5f * hash(i)) + hash(i * 9) * span) % span) - s * 2f
        val y = h * (0.10f + 0.22f * hash(i * 3))
        drawCloud(x, y, s, cloudColor)
    }
}

// ---------------------------------------------------------------- thunder

private fun DrawScope.drawThunder(t: Float) {
    val w = size.width
    val h = size.height
    sky(listOf(Color(0xFF37324B), Color(0xFF232030), Color(0xFF141218)))
    drawRain(t, count = 70)

    val seg = floor(t / 3.2f).toInt()
    val r = hash(seg * 17)
    val phase = t % 3.2f
    if (r > 0.35f && phase < 0.45f) {
        val strength = sin(phase / 0.45f * PI.toFloat())
        drawRect(Color.White.copy(alpha = 0.16f * strength))
        val path = Path().apply {
            var x = w * (0.25f + 0.5f * hash(seg * 29))
            var y = 0f
            moveTo(x, y)
            for (step in 1..6) {
                x += (hash(seg * 7 + step) - 0.5f) * w * 0.14f
                y += h * 0.09f
                lineTo(x, y)
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.35f * strength), style = Stroke(11f, cap = StrokeCap.Round))
        drawPath(path, Color(0xFFFFF59D), alpha = strength.coerceIn(0f, 1f), style = Stroke(3.5f, cap = StrokeCap.Round))
    }
    drawHearth(t)
}
