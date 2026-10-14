package app.rosa.weather.core.designsystem.seaglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.designsystem.sky.SkyParams
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pale canvas sea glass lies on, in place of the sky. Almost white, it still tells the
 * weather in a few soft washes of colour drifting very slowly across it: peach where the sun is
 * out, periwinkle under cloud and rain, lilac at night, a cool mist in fog and snow.
 */
@Composable
fun SeaCanvas(params: SkyParams, modifier: Modifier = Modifier) {
    val clock = LocalAmbientClock.current
    val sunny = (if (params.isSun) 1f else 0f) * (1f - params.cloudCover) * (1f - params.stars.coerceIn(0f, 1f))
    val grey = (params.cloudCover * 0.6f + params.rain + params.lightning * 0.5f).coerceIn(0f, 1f)
    val night = params.stars.coerceIn(0f, 1f).let { maxOf(it, if (params.isSun) 0f else 0.7f) }
    val mist = (params.fog + params.snow * 0.8f).coerceIn(0f, 1f)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(if (night > 0.5f) SeaGlass.CanvasLilac else SeaGlass.Canvas, SeaGlass.CanvasCool)))
        val t = clock.seconds
        wash(Color(0xFFFFDCC4), 0.55f * sunny, Offset(0.82f, 0.08f), 0.85f, t, 0f)
        wash(Color(0xFFFFF0C8), 0.35f * sunny, Offset(0.2f, 0.42f), 0.6f, t, 1.7f)
        wash(Color(0xFFD3E0FF), 0.6f * grey, Offset(0.15f, 0.2f), 0.9f, t, 3.1f)
        wash(Color(0xFFE2D4FF), 0.55f * night, Offset(0.85f, 0.15f), 0.9f, t, 4.4f)
        wash(Color(0xFFD9F1F7), 0.5f * mist, Offset(0.5f, 0.75f), 0.95f, t, 5.9f)
        // Always a little colour low down, so the glass at the bottom never floats on flat white.
        wash(Color(0xFFE4EBFB), 0.5f, Offset(0.3f, 1.02f), 0.8f, t, 7.3f)
    }
}

/** A soft round wash of [color] around [at] (fractions of the canvas), drifting slowly. */
private fun DrawScope.wash(color: Color, strength: Float, at: Offset, reach: Float, t: Float, phase: Float) {
    if (strength <= 0.01f) return
    val drift = Offset(sin(t * 0.05f + phase) * 0.04f, cos(t * 0.04f + phase * 1.3f) * 0.03f)
    val center = Offset((at.x + drift.x) * size.width, (at.y + drift.y) * size.height)
    val radius = size.maxDimension * reach * 0.6f
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = strength.coerceIn(0f, 1f)), color.copy(alpha = 0f)), center, radius), radius, center)
}

/**
 * A frosted pill set into sea glass — the selected tab, a nested button: the glass's own frosted
 * white with a fine white edge, no shadow of its own.
 */
fun Modifier.seaPill(palette: SeaPalette, cornerRadius: Dp, strength: Float = 1f): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(palette.pill.copy(alpha = palette.pill.alpha * strength), shape)
        .border(1.dp, Color.White.copy(alpha = 0.6f * strength), shape)
}
