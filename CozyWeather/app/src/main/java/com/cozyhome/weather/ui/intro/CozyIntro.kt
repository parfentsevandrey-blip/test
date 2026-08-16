package com.cozyhome.weather.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cozyhome.weather.ui.scenes.rememberSceneSeconds
import com.cozyhome.weather.util.Haptics
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

private fun hash(i: Int): Float {
    val x = sin(i * 127.1f + 311.7f) * 43758.547f
    return x - floor(x)
}

/**
 * Game-like cozy entrance: a floating island with a warm little house.
 * Tapping (or waiting) "flies" the camera into the glowing window.
 */
@Composable
fun CozyIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val time = rememberSceneSeconds()
    var leaving by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(0f) }
    val titleIn = remember { Animatable(0f) }

    LaunchedEffect(leaving) {
        if (leaving) {
            Haptics.enterWorld(context)
            reveal.animateTo(1f, tween(durationMillis = 950, easing = FastOutSlowInEasing))
            onFinished()
        }
    }
    LaunchedEffect(Unit) {
        delay(300)
        titleIn.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        delay(6500)
        if (!leaving) leaving = true
    }

    val pulse = rememberInfiniteTransition(label = "introPulse")
    val hintAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hintAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val r = reveal.value
                scaleX = 1f + r * 6f
                scaleY = 1f + r * 6f
                alpha = 1f - r
                transformOrigin = TransformOrigin(0.5f, 0.58f)
            }
            .background(Color(0xFF1B1430))
            .pointerInput(Unit) {
                detectTapGestures {
                    Haptics.click(context)
                    if (!leaving) leaving = true
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawIntroWorld(time.value) }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
                .graphicsLayer {
                    scaleX = 0.7f + 0.3f * titleIn.value
                    scaleY = 0.7f + 0.3f * titleIn.value
                    alpha = titleIn.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Уютная погода",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFF3E0),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "маленький тёплый мир твоей погоды",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB8AEDF),
            )
        }

        Text(
            text = "нажми, чтобы войти ✨",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .graphicsLayer { alpha = hintAlpha },
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFFFE0B2),
        )
    }
}

private fun DrawScope.drawIntroWorld(t: Float) {
    val w = size.width
    val h = size.height

    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF241B3E), Color(0xFF1B1430), Color(0xFF120D22)),
        )
    )

    // twinkling stars
    for (i in 0 until 60) {
        val tw = 0.2f + 0.8f * abs(sin(t * (0.5f + hash(i * 7)) * 2.4f + i))
        drawCircle(
            Color.White.copy(alpha = 0.85f * tw),
            radius = 1f + hash(i * 11) * 1.7f,
            center = Offset(hash(i) * w, hash(i * 3) * h * 0.6f),
        )
    }

    // the floating island bobs gently
    val bob = sin(t * 0.8f) * 14f
    val cx = w * 0.5f
    val groundY = h * 0.62f + bob

    // island rock + grass
    drawOval(
        Color(0xFF352A52),
        topLeft = Offset(cx - 170f, groundY - 6f),
        size = Size(340f, 96f),
    )
    drawOval(
        Color(0xFF6F9B6B),
        topLeft = Offset(cx - 170f, groundY - 20f),
        size = Size(340f, 46f),
    )

    // dangling roots / crystals under the island
    for (i in 0 until 4) {
        val rx = cx - 100f + i * 66f
        val path = Path().apply {
            moveTo(rx, groundY + 70f)
            lineTo(rx + 12f, groundY + 70f)
            lineTo(rx + 6f, groundY + 108f + sin(t + i) * 6f)
            close()
        }
        drawPath(path, Color(0xFF4A3B70))
    }

    // little house
    val houseW = 130f
    val houseH = 92f
    val houseLeft = cx - houseW / 2f
    val houseTop = groundY - 12f - houseH
    drawRoundRect(
        Color(0xFFFFF6EA),
        topLeft = Offset(houseLeft, houseTop),
        size = Size(houseW, houseH),
        cornerRadius = CornerRadius(14f),
    )
    val roof = Path().apply {
        moveTo(houseLeft - 18f, houseTop + 6f)
        lineTo(cx, houseTop - 58f)
        lineTo(houseLeft + houseW + 18f, houseTop + 6f)
        close()
    }
    drawPath(roof, Color(0xFF8D5B4C))

    // glowing window — the "door" into the app
    val winC = Offset(cx, houseTop + houseH * 0.52f)
    val glow = 0.8f + 0.2f * sin(t * 2.1f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFB74D).copy(alpha = 0.55f * glow), Color.Transparent),
            center = winC,
            radius = 90f * glow,
        ),
        radius = 90f * glow,
        center = winC,
    )
    drawCircle(Color(0xFFFFB74D), radius = 26f, center = winC)
    drawLine(Color(0xFF8D5B4C), Offset(winC.x - 26f, winC.y), Offset(winC.x + 26f, winC.y), strokeWidth = 4f)
    drawLine(Color(0xFF8D5B4C), Offset(winC.x, winC.y - 26f), Offset(winC.x, winC.y + 26f), strokeWidth = 4f)

    // chimney smoke
    for (s in 0 until 5) {
        val cycle = (t * 0.18f + s * 0.2f) % 1f
        val sy = houseTop - 40f - cycle * 130f
        val sx = cx + 52f + sin(t * 0.9f + s * 1.4f) * (10f + 18f * cycle)
        drawCircle(
            Color.White.copy(alpha = (1f - cycle) * 0.25f),
            radius = 7f + 9f * cycle,
            center = Offset(sx, sy),
        )
    }

    // fireflies drifting around the island
    for (i in 0 until 10) {
        val fx = cx + (hash(i * 5) - 0.5f) * w * 0.8f + sin(t * 0.4f + i * 2.1f) * 60f
        val fy = groundY - 60f + (hash(i * 3) - 0.5f) * 300f + sin(t * 0.6f + i) * 35f
        val p = 0.3f + 0.7f * abs(sin(t * 1.6f + i * 1.3f))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFD4E157).copy(alpha = 0.5f * p), Color.Transparent),
                center = Offset(fx, fy),
                radius = 14f,
            ),
            radius = 14f,
            center = Offset(fx, fy),
        )
        drawCircle(Color(0xFFF0F4C3), radius = 2.4f, center = Offset(fx, fy), alpha = p)
    }
}
