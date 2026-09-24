package app.rosa.weather.core.designsystem.component

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.glyph.GlyphRaster
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.roundToInt

/**
 * The app's icon set, drawn as glass: closed shapes get a translucent body, every line a crisp
 * edge that dims slightly toward the bottom, and the pieces you touch (slider knobs, the search
 * lens) carry a specular glint. Rounded caps on a 24-unit grid, legible from 14 to 28 dp.
 */
enum class RosaIcon { Search, Plus, Settings, Location, Close, Back, Widgets, Refresh, Check, Trash, Drag, Chevron, Sparkle }

@Composable
fun RosaIconView(icon: RosaIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        GlassIconScope(this, u, tint).draw(icon)
    }
}

private class GlassIconScope(val scope: DrawScope, val u: Float, val tint: Color) {
    private val line = Brush.verticalGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.78f)))
    private val body = Brush.verticalGradient(listOf(tint.copy(alpha = tint.alpha * 0.3f), tint.copy(alpha = tint.alpha * 0.1f)))
    private val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun p(x: Float, y: Float) = Offset(x * u, y * u)

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    /** A translucent body with a lit edge. */
    private fun glass(shape: Path, closed: Boolean = true) = with(scope) {
        if (closed) drawPath(shape, body)
        drawPath(shape, line, style = stroke)
    }

    private fun lines(vararg segments: Pair<Offset, Offset>) = with(scope) {
        segments.forEach { (a, b) -> drawLine(line, a, b, stroke.width, StrokeCap.Round) }
    }

    private fun glint(at: Offset, r: Float = 1.05f) = with(scope) {
        drawCircle(Color.White.copy(alpha = 0.85f), r * u, at)
    }

    /** A small glass orb: bright core, tinted body, crisp ring, glint. */
    private fun orb(center: Offset, r: Float) = with(scope) {
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), tint.copy(alpha = 0.85f)), center = center - Offset(r * 0.35f * u, r * 0.35f * u), radius = r * 1.3f * u),
            r * u,
            center,
        )
        drawCircle(tint, r * u, center, style = Stroke(1.1f * u))
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.28f * u, center - Offset(r * 0.38f * u, r * 0.38f * u))
    }

    fun draw(icon: RosaIcon) = with(scope) {
        when (icon) {
            RosaIcon.Search -> {
                glass(path { addOval(Rect(p(4f, 4f), p(17f, 17f))) })
                // The lens catches the light.
                drawArc(Color.White.copy(alpha = 0.75f), 200f, 60f, false, p(6.8f, 6.8f), Size(7.4f * u, 7.4f * u), style = Stroke(1.2f * u, cap = StrokeCap.Round))
                drawLine(line, p(15.6f, 15.6f), p(20.3f, 20.3f), 2.5f * u, StrokeCap.Round)
            }
            RosaIcon.Plus -> lines(p(12f, 5f) to p(12f, 19f), p(5f, 12f) to p(19f, 12f))
            RosaIcon.Settings -> {
                // Three tracks with glass knobs — calmer than a gear and readable at 20 dp.
                listOf(6f to 15.5f, 12f to 8.5f, 18f to 13.5f).forEach { (y, knob) ->
                    drawLine(tint.copy(alpha = tint.alpha * 0.55f), p(4f, y), p(20f, y), 1.7f * u, StrokeCap.Round)
                    orb(p(knob, y), 2.6f)
                }
            }
            RosaIcon.Location -> {
                glass(
                    path {
                        moveTo(20f * u, 4f * u)
                        lineTo(12.5f * u, 20f * u)
                        lineTo(11f * u, 13f * u)
                        lineTo(4f * u, 11.5f * u)
                        close()
                    },
                )
                glint(p(16.6f, 7.6f), 0.8f)
            }
            RosaIcon.Close -> lines(p(6f, 6f) to p(18f, 18f), p(18f, 6f) to p(6f, 18f))
            RosaIcon.Back -> glass(path { moveTo(15f * u, 5f * u); lineTo(8f * u, 12f * u); lineTo(15f * u, 19f * u) }, closed = false)
            RosaIcon.Chevron -> glass(path { moveTo(9f * u, 5f * u); lineTo(16f * u, 12f * u); lineTo(9f * u, 19f * u) }, closed = false)
            RosaIcon.Widgets -> {
                // Four glass tiles, one of them round.
                listOf(Offset(3.8f, 3.8f), Offset(13.2f, 3.8f), Offset(3.8f, 13.2f)).forEach { o ->
                    glass(path { addRoundRect(RoundRect(Rect(p(o.x, o.y), Size(7f * u, 7f * u)), CornerRadius(2.3f * u))) })
                    glint(p(o.x + 1.9f, o.y + 1.9f), 0.7f)
                }
                glass(path { addOval(Rect(p(13.2f, 13.2f), Size(7f * u, 7f * u))) })
                glint(p(15.1f, 15.1f), 0.7f)
            }
            RosaIcon.Refresh -> {
                drawArc(line, -60f, 290f, false, p(5f, 5f), Size(14f * u, 14f * u), style = stroke)
                drawPath(
                    path {
                        moveTo(20.3f * u, 3.8f * u)
                        lineTo(19.6f * u, 9.6f * u)
                        lineTo(13.9f * u, 8.6f * u)
                        close()
                    },
                    tint,
                )
            }
            RosaIcon.Check -> glass(path { moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19f * u, 6.5f * u) }, closed = false)
            RosaIcon.Trash -> {
                lines(p(4.5f, 7f) to p(19.5f, 7f))
                drawPath(path { moveTo(9.5f * u, 7f * u); lineTo(10f * u, 4.5f * u); lineTo(14f * u, 4.5f * u); lineTo(14.5f * u, 7f * u) }, line, style = stroke)
                glass(
                    path {
                        moveTo(6.5f * u, 7f * u)
                        lineTo(7.5f * u, 19.5f * u)
                        lineTo(16.5f * u, 19.5f * u)
                        lineTo(17.5f * u, 7f * u)
                    },
                )
                glint(p(9f, 9.6f), 0.7f)
            }
            RosaIcon.Drag -> lines(p(6f, 8f) to p(18f, 8f), p(6f, 12f) to p(18f, 12f), p(6f, 16f) to p(18f, 16f))
            RosaIcon.Sparkle -> {
                val star = path {
                    moveTo(12f * u, 3f * u)
                    quadraticTo(12f * u, 12f * u, 21f * u, 12f * u)
                    quadraticTo(12f * u, 12f * u, 12f * u, 21f * u)
                    quadraticTo(12f * u, 12f * u, 3f * u, 12f * u)
                    quadraticTo(12f * u, 12f * u, 12f * u, 3f * u)
                    close()
                }
                drawPath(star, Brush.radialGradient(listOf(Color.White, tint.copy(alpha = tint.alpha * 0.55f)), center = p(12f, 12f), radius = 9f * u))
                drawPath(star, line, style = Stroke(1.2f * u, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * The shared weather glyph in Compose. A still glyph is a cached bitmap ([GlyphRaster]), so lists
 * of them scroll for free. When [animated], only what moves — the sun's rays, falling drops and
 * flakes — is drawn live on the shared ambient clock (still when the system disables animations),
 * under the cached clouds, inside the glyph's own layer so scrolling never redraws it.
 *
 * @param rasterScale renders the cached bitmap this much larger, for glyphs that get magnified.
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
    onLightBackground: Boolean = app.rosa.weather.core.designsystem.theme.Rosa.colors.isLightSky,
    rasterScale: Float = 1f,
) {
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val argb = tint.toArgb()
    if (!animated || !WeatherGlyphPainter.moves(condition, isDay)) {
        Canvas(modifier.then(semantics)) {
            drawRaster(condition, isDay, moonPhase, tone, argb, onLightBackground, WeatherGlyphPainter.Part.Whole, rasterScale)
        }
        return
    }
    val painter = remember { WeatherGlyphPainter() }
    val clock = LocalAmbientClock.current
    // Mono glyphs knock gaps out of what lies beneath their clouds, so they can't be split.
    val split = tone == WeatherGlyphPainter.Tone.Color
    Canvas(modifier.then(semantics).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val time = clock.seconds + 0.001f
        drawIntoCanvas { canvas ->
            painter.draw(
                canvas.nativeCanvas, condition, isDay, RectF(0f, 0f, size.width, size.height), tone, argb, moonPhase, time,
                onLightBackground, if (split) WeatherGlyphPainter.Part.Beneath else WeatherGlyphPainter.Part.Whole,
            )
        }
        if (split) drawRaster(condition, isDay, moonPhase, tone, argb, onLightBackground, WeatherGlyphPainter.Part.Clouds, 1f)
    }
}

private fun DrawScope.drawRaster(
    condition: WeatherCondition,
    isDay: Boolean,
    moonPhase: Double,
    tone: WeatherGlyphPainter.Tone,
    tint: Int,
    onLight: Boolean,
    part: WeatherGlyphPainter.Part,
    scale: Float,
) {
    val width = size.width.roundToInt()
    val height = size.height.roundToInt()
    if (width <= 0 || height <= 0) return
    val image = GlyphRaster.get(
        condition, isDay, moonPhase, tone, tint, onLight, part,
        (width * scale).roundToInt(), (height * scale).roundToInt(),
    )
    drawImage(image, dstSize = IntSize(width, height), filterQuality = FilterQuality.Low)
}
