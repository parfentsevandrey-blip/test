package app.quire.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The readings, each drawn the way its own quantity is actually shaped.
 *
 * What was here before was six identical rings in six identical rounded rectangles: one picture
 * answering six different questions. The ring was honest — every one of them had a real
 * denominator — but a ring says only "somewhere between empty and full", and that is the least
 * any of these numbers has to say. Six of them side by side is a dashboard, and a dashboard is
 * the grammar of a settings screen, which is exactly what the old comment on this block admitted
 * it had borrowed.
 *
 * So each reading now gets a container shaped like itself and a mark that encodes what that
 * particular quantity means:
 *
 * - **Rain** is a chance, and people round a chance to fifths — five drops, filled.
 * - **Humidity** is how full the air is — a vessel, filled to a waterline.
 * - **Wind** has a direction before it has a speed — a compass, needled.
 * - **Gusts** mean nothing alone; they mean the gap above the steady wind — a profile that spikes.
 * - **UV** has a published colour scale the whole world already reads — that scale, with a marker.
 * - **Pressure** is only ever "above or below normal" — a dial with normal marked on it.
 *
 * None of these is a ring, and none of them is interchangeable with another, which is the whole
 * point: the shape and the mark together say what the reading is before the number is read.
 */

/** Which reading this is, and therefore how it is drawn. */
internal enum class Dial { RAIN, HUMIDITY, WIND, GUST, UV, PRESSURE }

/** The test handle for one dial's mark. */
internal fun markTag(dial: Dial) = "mark-${dial.name}"

/**
 * One reading, ready to draw.
 *
 * [fraction] is where the value sits in its own everyday range, 0..1 — the same honest
 * denominators the rings used. [bearing] is the wind's heading; [second] is the steady wind on
 * the gust's own scale, because a gust is a comparison.
 */
internal data class Reading(
    val dial: Dial,
    val label: String,
    val value: String,
    val fraction: Float,
    val bearing: Float? = null,
    val second: Float? = null,
)

/** The gap between tiles, and how much of a tile's edge the mark keeps clear of. */
private val TileGap = 10.dp
private const val MARK_INSET = 0.20f

/**
 * The UV scale, as published — green, yellow, orange, red, violet.
 *
 * These are semantic colours, not the app's accent, and they are deliberately fixed: the point
 * of the UV bands is that they mean the same thing on every forecast anybody has ever read, the
 * way a traffic light does. Re-tinting them to the wallpaper would be re-tinting the meaning.
 */
private val UvScale = listOf(
    Color(0xFF558B2F), Color(0xFF9E9D24), Color(0xFFF9A825),
    Color(0xFFEF6C00), Color(0xFFD32F2F), Color(0xFF7B1FA2),
)

/** Where "normal" sits on the pressure dial: 1013 hPa in the 980–1040 band the tile spans. */
private const val PRESSURE_NORMAL = (1013f - 980f) / 60f

@Composable
internal fun ReadingGrid(readings: List<Reading>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TileGap),
        modifier = modifier.fillMaxWidth(),
    ) {
        readings.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TileGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { reading ->
                    ReadingTile(reading, Modifier.weight(1f))
                }
                // A short last row keeps its tiles the width of the ones above rather than
                // stretching two of them across three columns.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One tile: the shape carrying the mark, and the number and the word beneath it.
 *
 * The text sits *below* the shape rather than inside it. A lobed or spiked container has no
 * honest rectangle in the middle to set type in, and type crammed into one is the thing that
 * makes expressive shapes look like a mistake instead of a decision.
 */
@Composable
private fun ReadingTile(reading: Reading, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shapeOf(reading.dial))
                .background(scheme.surfaceContainerHigh)
                // Tagged per dial so a render test can photograph one mark at a time. Six marks
                // that all look plausible and one that ignores its number is the failure this
                // guards against, and it is only findable a tile at a time.
                .testTag(markTag(reading.dial)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Humidity is the one mark that fills the tile edge to edge instead of sitting
                // in a box inside it: the puff *is* the vessel, and the container's own clip
                // gives the water its shoreline. Inset it and you get a rectangle of water
                // floating in a cloud, which is a diagram of nothing.
                if (reading.dial == Dial.HUMIDITY) {
                    waterline(size, reading.fraction, scheme.primary)
                    return@Canvas
                }
                val pad = size.minDimension * MARK_INSET
                val box = Size(size.width - pad * 2, size.height - pad * 2)
                translate(pad, pad) {
                    when (reading.dial) {
                        Dial.RAIN -> drops(box, reading.fraction, scheme.primary, scheme.outlineVariant)
                        Dial.HUMIDITY -> Unit
                        Dial.WIND -> compass(box, reading.fraction, reading.bearing, scheme.primary, scheme.outlineVariant)
                        Dial.GUST -> spike(box, reading.fraction, reading.second, scheme.primary, scheme.outlineVariant)
                        Dial.UV -> uvScale(box, reading.fraction)
                        Dial.PRESSURE -> barometer(
                            box, reading.fraction,
                            scheme.primary, scheme.outlineVariant, scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = reading.value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = reading.label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * The container each reading wears.
 *
 * Chosen so the silhouette is already saying something before the mark inside it does: clover
 * lobes read as drops, a puff reads as damp air, a twelve-sided cookie is a compass rose, a
 * burst is a gust, the sun is the sun, and a barometer has always been a circle.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun shapeOf(dial: Dial) = when (dial) {
    Dial.RAIN -> MaterialShapes.Clover4Leaf.toShape()
    Dial.HUMIDITY -> MaterialShapes.Puffy.toShape()
    Dial.WIND -> MaterialShapes.Cookie12Sided.toShape()
    Dial.GUST -> MaterialShapes.SoftBurst.toShape()
    Dial.UV -> MaterialShapes.Sunny.toShape()
    Dial.PRESSURE -> MaterialShapes.Circle.toShape()
}

// ---- the marks ---------------------------------------------------------

/** A chance of rain, in fifths, because that is the precision anybody actually acts on. */
private fun DrawScope.drops(box: Size, fraction: Float, lit: Color, track: Color) {
    val filled = (fraction.coerceIn(0f, 1f) * 5f).roundToInt()
    val step = box.width / 5f
    val r = step * 0.36f
    for (i in 0 until 5) {
        val cx = step * (i + 0.5f)
        val cy = box.height * 0.5f
        drawPath(teardrop(cx, cy, r), if (i < filled) lit else track)
    }
}

/** One drop: a round belly under a drawn-out point, which is what falling water looks like. */
private fun teardrop(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r * 2.1f)
    cubicTo(cx + r * 1.28f, cy - r * 0.5f, cx + r, cy + r, cx, cy + r)
    cubicTo(cx - r, cy + r, cx - r * 1.28f, cy - r * 0.5f, cx, cy - r * 2.1f)
    close()
}

/** Humidity as what it is: a vessel, filled to a line, with the line behaving like water. */
private fun DrawScope.waterline(box: Size, fraction: Float, lit: Color) {
    val level = box.height * (1f - fraction.coerceIn(0.05f, 1f))
    val crest = box.height * 0.06f
    // Four half-waves fitted to exactly the width, so the surface reads as water and the fill
    // closes on the corners rather than on wherever the last curve happened to land.
    val half = box.width / 4f
    val surface = Path().apply {
        moveTo(0f, level)
        var x = 0f
        var up = true
        repeat(4) {
            quadraticTo(x + half / 2f, level + if (up) -crest else crest, x + half, level)
            x += half
            up = !up
        }
    }
    val body = Path().apply {
        addPath(surface)
        lineTo(box.width, box.height)
        lineTo(0f, box.height)
        close()
    }
    drawPath(body, lit.copy(alpha = 0.42f))
    drawPath(surface, lit, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}

/** The wind: where it is going first, how hard second. */
private fun DrawScope.compass(
    box: Size,
    fraction: Float,
    bearing: Float?,
    lit: Color,
    track: Color,
) {
    val cx = box.width / 2f
    val cy = box.height / 2f
    val r = box.minDimension / 2f
    // Twelve ticks, because the rose the shape is cut as has twelve lobes.
    for (i in 0 until 12) {
        val a = i * PI.toFloat() / 6f
        val outer = r
        val inner = r * if (i % 3 == 0) 0.74f else 0.85f
        drawLine(
            color = track,
            start = Offset(cx + inner * sin(a), cy - inner * cos(a)),
            end = Offset(cx + outer * sin(a), cy - outer * cos(a)),
            strokeWidth = if (i % 3 == 0) 2.6.dp.toPx() else 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    if (bearing == null) {
        // No heading known: say so with a still centre rather than a needle pointing at nothing.
        drawCircle(track, radius = r * 0.16f, center = Offset(cx, cy))
        return
    }
    // The needle grows with the wind, so a calm day is a short needle and a gale is a long one.
    val reach = r * (0.52f + 0.36f * fraction.coerceIn(0f, 1f))
    rotate(bearing, pivot = Offset(cx, cy)) {
        val head = Path().apply {
            moveTo(cx, cy - reach)
            lineTo(cx + r * 0.26f, cy + reach * 0.40f)
            lineTo(cx, cy + reach * 0.16f)
            lineTo(cx - r * 0.26f, cy + reach * 0.40f)
            close()
        }
        drawPath(head, lit)
    }
    drawCircle(lit, radius = r * 0.11f, center = Offset(cx, cy))
}

/** A gust is the gap above the steady wind, so both are drawn and the gap is the picture. */
private fun DrawScope.spike(
    box: Size,
    fraction: Float,
    steady: Float?,
    lit: Color,
    track: Color,
) {
    val floor = box.height * 0.90f
    val ceiling = box.height * 0.08f
    fun y(f: Float) = floor - (floor - ceiling) * f.coerceIn(0f, 1f)
    val base = y(steady ?: (fraction * 0.55f))
    val peak = y(fraction)

    // The steady wind, as the line the day sits on.
    drawLine(
        color = track,
        start = Offset(0f, base),
        end = Offset(box.width, base),
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // And the gust: one sharp excursion above it and back, which is the whole of what a gust is.
    // The area between the two is filled, because the *gap* is the reading — a gust figure on
    // its own says nothing until you know what it is gusting above.
    val profile = Path().apply {
        moveTo(0f, base)
        lineTo(box.width * 0.28f, base)
        lineTo(box.width * 0.50f, peak)
        lineTo(box.width * 0.70f, base)
        lineTo(box.width, base)
    }
    drawPath(
        Path().apply { addPath(profile); lineTo(box.width, base); close() },
        lit.copy(alpha = 0.30f),
    )
    drawPath(profile, lit, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(lit, radius = 3.2.dp.toPx(), center = Offset(box.width * 0.50f, peak))
}

/** The published UV bands, with today marked on them. */
private fun DrawScope.uvScale(box: Size, fraction: Float) {
    val h = box.height * 0.34f
    val top = (box.height - h) / 2f
    drawRoundRect(
        brush = Brush.horizontalGradient(UvScale),
        topLeft = Offset(0f, top),
        size = Size(box.width, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
    )
    // A white pip with a dark hairline round it. The bands are saturated mid-tones and the app
    // has two themes, so neither the ink colour nor white alone stays findable on all five —
    // the pairing does, and it does not depend on the scheme at all.
    val x = (box.width * fraction.coerceIn(0f, 1f)).coerceIn(h * 0.5f, box.width - h * 0.5f)
    val at = Offset(x, top + h / 2f)
    drawCircle(Color.White, radius = h * 0.34f, center = at)
    drawCircle(
        color = Color.Black.copy(alpha = 0.55f),
        radius = h * 0.34f,
        center = at,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

/** A barometer: the band it lives in, where it is, and where normal is. */
private fun DrawScope.barometer(box: Size, fraction: Float, lit: Color, track: Color, mark: Color) {
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    val inset = stroke.width / 2f
    val bounds = Size(box.width - inset * 2, box.height - inset * 2)
    val origin = Offset(inset, inset)
    // Three-quarters of a turn, opening at the bottom, the way a barometer's face is cut.
    val start = 135f
    val sweep = 270f
    drawArc(track, start, sweep, false, origin, bounds, style = stroke)
    drawArc(
        color = lit,
        startAngle = start,
        sweepAngle = sweep * fraction.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = origin,
        size = bounds,
        style = stroke,
    )
    // Normal, ticked. Pressure is only ever read as "above or below this".
    val a = (start + sweep * PRESSURE_NORMAL) * PI.toFloat() / 180f
    val cx = box.width / 2f
    val cy = box.height / 2f
    val r = box.minDimension / 2f
    drawLine(
        color = mark,
        start = Offset(cx + (r - inset) * cos(a), cy + (r - inset) * sin(a)),
        end = Offset(cx + (r * 0.56f) * cos(a), cy + (r * 0.56f) * sin(a)),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
