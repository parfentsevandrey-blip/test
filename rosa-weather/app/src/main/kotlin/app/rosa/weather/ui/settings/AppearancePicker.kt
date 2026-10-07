package app.rosa.weather.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.rememberPressScale
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WeatherVisual
import kotlin.math.PI
import kotlin.math.sin

/** Four little living skies to choose the app's light from. */
@Composable
internal fun AppearancePicker(selected: Appearance, onSelect: (Appearance) -> Unit) {
    val labels = mapOf(
        Appearance.Auto to stringResource(R.string.appearance_auto),
        Appearance.Light to stringResource(R.string.appearance_light),
        Appearance.Evening to stringResource(R.string.appearance_evening),
        Appearance.Dark to stringResource(R.string.appearance_dark),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Appearance.entries.forEach { mode ->
            MoodTile(mode, labels.getValue(mode), mode == selected, Modifier.weight(1f)) { onSelect(mode) }
        }
    }
}

private class MoodColors(val top: Color, val bottom: Color, val glow: Color, val sun: Color)

private fun moodColors(mode: Appearance, elevation: Double): MoodColors {
    val p = SkyPalette.of(mode, elevation, WeatherVisual.ClearDay)
    return MoodColors(p.zenith.toColor(), p.horizon.toColor(), p.glow.toColor(), p.sun.toColor())
}

@Composable
private fun MoodTile(mode: Appearance, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val clock = LocalAmbientClock.current
    val colors = Rosa.colors
    val sky = remember(mode) { moodColors(mode, elevation = 28.0) }
    val night = remember(mode) { moodColors(Appearance.Dark, elevation = -16.0) }
    val lift by animateFloatAsState(if (selected) 1f else 0f, RosaMotion.gel(), label = "mood")
    var pressed by remember { mutableStateOf(false) }
    val press = rememberPressScale(pressed)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .graphicsLayer {
                    // The chosen sky rises towards you; pressing sinks it a little.
                    val s = press * (0.94f + 0.06f * lift)
                    scaleX = s
                    scaleY = s
                    translationY = -4.dp.toPx() * lift
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = {
                            haptics?.tick()
                            onClick()
                        },
                    )
                }
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                    contentDescription = label
                },
        ) {
            val radius = CornerRadius(18.dp.toPx())
            val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
            val t = clock.seconds
            clipPath(shape) {
                drawRect(Brush.verticalGradient(listOf(sky.top, sky.bottom)))
                when (mode) {
                    Appearance.Auto -> autoSky(sky, night, t)
                    Appearance.Light -> lightSky(sky, t)
                    Appearance.Evening -> eveningSky(sky, t)
                    Appearance.Dark -> darkSky(sky, t)
                }
                // Glass sheen across the top, as on every pane in the app.
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.Transparent))
            }
            // Glass rim; the chosen tile gets a bright double edge.
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.5f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                cornerRadius = radius,
                style = Stroke(1.2.dp.toPx()),
            )
            if (lift > 0.01f) {
                val inset = 3.dp.toPx()
                drawRoundRect(
                    colors.accent.copy(alpha = lift),
                    topLeft = Offset(-inset, -inset),
                    size = Size(size.width + inset * 2, size.height + inset * 2),
                    cornerRadius = CornerRadius(radius.x + inset),
                    style = Stroke(2.dp.toPx()),
                )
                checkBadge(lift, colors.accent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = if (selected) Rosa.type.label else Rosa.type.caption, color = if (selected) colors.ink else colors.inkSoft, maxLines = 1)
    }
}

/** Day fading diagonally into night: this mode follows the real sky. */
private fun DrawScope.autoSky(day: MoodColors, night: MoodColors, t: Float) {
    val diagonal = Path().apply {
        moveTo(size.width, size.height * 0.18f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        lineTo(0f, size.height * 0.82f)
        close()
    }
    clipPath(diagonal) {
        drawRect(Brush.verticalGradient(listOf(night.top, night.bottom)))
        stars(t, count = 7, fromY = 0.45f)
        crescent(Offset(size.width * 0.72f, size.height * 0.74f), size.minDimension * 0.1f, lerp(night.top, night.bottom, 0.74f))
    }
    sun(Offset(size.width * 0.3f, size.height * 0.27f), size.minDimension * 0.11f, day.sun, day.glow, t)
}

private fun DrawScope.lightSky(sky: MoodColors, t: Float) {
    sun(Offset(size.width * 0.7f, size.height * 0.28f), size.minDimension * 0.12f, sky.sun, Color.White, t)
    // A wisp of porcelain cloud.
    val y = size.height * 0.68f
    drawOval(Color.White.copy(alpha = 0.7f), Offset(size.width * 0.12f, y), Size(size.width * 0.55f, size.height * 0.1f))
    drawOval(Color.White.copy(alpha = 0.55f), Offset(size.width * 0.34f, y - size.height * 0.05f), Size(size.width * 0.4f, size.height * 0.1f))
}

private fun DrawScope.eveningSky(sky: MoodColors, t: Float) {
    stars(t, count = 3, fromY = 0f, toY = 0.35f)
    // The sun has just slipped under the horizon; its light still blooms there.
    val horizon = Offset(size.width * 0.5f, size.height * 1.02f)
    val breathe = 0.9f + 0.1f * sin(t * PI.toFloat() / 2.4f)
    drawCircle(
        Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.9f), sky.glow.copy(alpha = 0f)), center = horizon, radius = size.width * 0.8f * breathe),
        radius = size.width * 0.8f * breathe,
        center = horizon,
    )
    drawCircle(sky.sun, radius = size.minDimension * 0.16f, center = horizon)
}

private fun DrawScope.darkSky(night: MoodColors, t: Float) {
    stars(t, count = 12, fromY = 0f)
    crescent(Offset(size.width * 0.68f, size.height * 0.3f), size.minDimension * 0.14f, lerp(night.top, night.bottom, 0.3f))
}

private fun DrawScope.sun(center: Offset, radius: Float, core: Color, glow: Color, t: Float) {
    val breathe = 1f + 0.06f * sin(t * PI.toFloat() / 1.8f)
    drawCircle(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.75f), glow.copy(alpha = 0f)), center = center, radius = radius * 3.2f * breathe),
        radius = radius * 3.2f * breathe,
        center = center,
    )
    drawCircle(core, radius, center)
    drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.62f, center)
}

private fun DrawScope.crescent(center: Offset, radius: Float, sky: Color) {
    drawCircle(Color(0xFFF4F1FF), radius, center)
    drawCircle(sky, radius * 0.86f, center + Offset(radius * 0.42f, -radius * 0.2f))
}

/** A few twinkling stars scattered on fixed, pseudo-random spots. */
private fun DrawScope.stars(t: Float, count: Int, fromY: Float, toY: Float = 0.95f) {
    fun hash(v: Float): Float {
        val x = sin(v) * 43758.547f
        return x - kotlin.math.floor(x)
    }
    repeat(count) { i ->
        val fx = hash(i * 12.9898f + 1.3f) * 0.84f + 0.08f
        val fy = fromY + hash(i * 78.233f + 4.1f) * (toY - fromY)
        val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * (1.3f + i * 0.37f) + i * 2.1f))
        drawCircle(Color.White.copy(alpha = twinkle), radius = (0.9f + (i % 3) * 0.35f).dp.toPx(), center = Offset(size.width * fx, size.height * fy))
    }
}

private fun DrawScope.checkBadge(lift: Float, accent: Color) {
    val r = 9.dp.toPx() * lift
    val c = Offset(size.width - 11.dp.toPx(), 11.dp.toPx())
    drawCircle(accent, r, c)
    val tick = Path().apply {
        moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
        lineTo(c.x - r * 0.1f, c.y + r * 0.36f)
        lineTo(c.x + r * 0.48f, c.y - r * 0.32f)
    }
    drawPath(tick, Color(0xFF14172A), style = Stroke(1.8.dp.toPx() * lift, cap = StrokeCap.Round))
}
