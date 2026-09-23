package app.rosa.weather.core.designsystem.component

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import app.rosa.weather.core.model.WeatherCondition
import kotlinx.coroutines.isActive

/** A small, consistent stroke icon set (rounded caps, 1.8 px on a 24 grid). */
enum class RosaIcon { Search, Plus, Settings, Location, Close, Back, Widgets, Refresh, Check, Trash, Drag, Chevron, Sparkle }

@Composable
fun RosaIconView(icon: RosaIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(block: Path.() -> Unit) = drawPath(Path().apply(block), tint, style = stroke)
        when (icon) {
            RosaIcon.Search -> {
                drawCircle(tint, 7f * u, Offset(10.5f * u, 10.5f * u), style = stroke)
                drawLine(tint, Offset(15.8f * u, 15.8f * u), Offset(20.5f * u, 20.5f * u), stroke.width, StrokeCap.Round)
            }
            RosaIcon.Plus -> {
                drawLine(tint, Offset(12f * u, 5f * u), Offset(12f * u, 19f * u), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(5f * u, 12f * u), Offset(19f * u, 12f * u), stroke.width, StrokeCap.Round)
            }
            RosaIcon.Settings -> {
                // Three sliders: calmer than a gear and readable at 20 dp.
                listOf(6f to 15f, 12f to 8f, 18f to 13f).forEach { (y, knob) ->
                    drawLine(tint, Offset(4f * u, y * u), Offset(20f * u, y * u), stroke.width, StrokeCap.Round)
                    drawCircle(tint, 2.4f * u, Offset(knob * u, y * u))
                }
            }
            RosaIcon.Location -> p {
                moveTo(20f * u, 4f * u)
                lineTo(12.5f * u, 20f * u)
                lineTo(11f * u, 13f * u)
                lineTo(4f * u, 11.5f * u)
                close()
            }
            RosaIcon.Close -> {
                drawLine(tint, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(18f * u, 6f * u), Offset(6f * u, 18f * u), stroke.width, StrokeCap.Round)
            }
            RosaIcon.Back -> p {
                moveTo(15f * u, 5f * u)
                lineTo(8f * u, 12f * u)
                lineTo(15f * u, 19f * u)
            }
            RosaIcon.Chevron -> p {
                moveTo(9f * u, 5f * u)
                lineTo(16f * u, 12f * u)
                lineTo(9f * u, 19f * u)
            }
            RosaIcon.Widgets -> {
                listOf(Offset(4f, 4f), Offset(13f, 4f), Offset(4f, 13f)).forEach {
                    drawRoundRect(
                        tint, Offset(it.x * u, it.y * u), androidx.compose.ui.geometry.Size(7f * u, 7f * u),
                        androidx.compose.ui.geometry.CornerRadius(2.2f * u), style = stroke,
                    )
                }
                drawCircle(tint, 3.6f * u, Offset(16.5f * u, 16.5f * u), style = stroke)
            }
            RosaIcon.Refresh -> {
                drawArc(tint, -60f, 290f, false, Offset(5f * u, 5f * u), androidx.compose.ui.geometry.Size(14f * u, 14f * u), style = stroke)
                p {
                    moveTo(19.5f * u, 4.5f * u)
                    lineTo(19f * u, 9f * u)
                    lineTo(14.5f * u, 8.2f * u)
                }
            }
            RosaIcon.Check -> p {
                moveTo(5f * u, 12.5f * u)
                lineTo(10f * u, 17.5f * u)
                lineTo(19f * u, 6.5f * u)
            }
            RosaIcon.Trash -> {
                p {
                    moveTo(5f * u, 7f * u); lineTo(19f * u, 7f * u)
                    moveTo(9.5f * u, 7f * u); lineTo(10f * u, 4.5f * u); lineTo(14f * u, 4.5f * u); lineTo(14.5f * u, 7f * u)
                    moveTo(6.5f * u, 7f * u); lineTo(7.5f * u, 19.5f * u); lineTo(16.5f * u, 19.5f * u); lineTo(17.5f * u, 7f * u)
                }
            }
            RosaIcon.Drag -> listOf(8f, 12f, 16f).forEach { y ->
                drawLine(tint, Offset(6f * u, y * u), Offset(18f * u, y * u), stroke.width, StrokeCap.Round)
            }
            RosaIcon.Sparkle -> p {
                moveTo(12f * u, 3f * u)
                quadraticTo(12f * u, 12f * u, 21f * u, 12f * u)
                quadraticTo(12f * u, 12f * u, 12f * u, 21f * u)
                quadraticTo(12f * u, 12f * u, 3f * u, 12f * u)
                quadraticTo(12f * u, 12f * u, 12f * u, 3f * u)
            }
        }
    }
}

/**
 * The shared weather glyph in Compose. When [animated], rays turn, drops fall and flakes drift
 * (paused automatically when the system disables animations).
 */
@Composable
fun WeatherGlyph(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    moonPhase: Double = 0.3,
    animated: Boolean = false,
    tone: WeatherGlyphPainter.Tone = WeatherGlyphPainter.Tone.Color,
    tint: Color = Color.White,
    contentDescription: String? = null,
) {
    val painter = remember { WeatherGlyphPainter() }
    val time = remember { mutableFloatStateOf(0f) }
    val motion = LocalMotionEnabled.current
    if (animated && motion) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (isActive) withFrameNanos { time.floatValue = (it - start) / 1e9f }
        }
    }
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(semantics)) {
        drawGlyph(painter, condition, isDay, moonPhase, tone, tint, if (animated) time.floatValue + 0.001f else 0f)
    }
}

private fun DrawScope.drawGlyph(
    painter: WeatherGlyphPainter,
    condition: WeatherCondition,
    isDay: Boolean,
    moonPhase: Double,
    tone: WeatherGlyphPainter.Tone,
    tint: Color,
    time: Float,
) {
    drawIntoCanvas { canvas ->
        painter.draw(canvas.nativeCanvas, condition, isDay, RectF(0f, 0f, size.width, size.height), tone, tint.toArgb(), moonPhase, time)
    }
}
