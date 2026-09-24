package app.rosa.weather.widget.render

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WeatherVisual
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The sky's light on the widget's glass: the app's scene-lit glass, drawn once per update. The
 * sun or moon lights the rim on its side of the pane, in its colour (white at noon, gold low in the
 * sky, silver from the moon), as strongly as cloud and the moon's phase allow, and makes a glint
 * where the rim faces it. What it leaves unlit, the sky lights softly from the upper left: Apple's
 * resting light, all there is under an overcast sky or on a moonless night. Between updates the
 * real sun moves on, and so does the light.
 */
internal data class WidgetLight(
    /** Where the sun or moon lies, seen from the pane's centre: radians, y down. */
    val angle: Float,
    /** 0..1: how strongly it lights the glass. */
    val power: Float,
    /** Its light, softened toward white the way glass shows it. */
    val color: Argb,
    /** The sky overhead: what the glass reflects. */
    val sky: Argb,
) {
    /** How much of the rim's light is the sun's or moon's; the rest is the sky's, from [RESTING_ANGLE]. */
    val share: Float get() = (power * 1.6f).coerceIn(0f, 1f)

    /**
     * One direction for the light as a whole, for things too small to show two lights (the glass
     * digits): the sun's and the sky's, weighed by [share]; the stronger one where they oppose.
     */
    val overall: Float
        get() {
            val x = share * cos(angle) + (1f - share) * cos(RESTING_ANGLE)
            val y = share * sin(angle) + (1f - share) * sin(RESTING_ANGLE)
            return when {
                hypot(x, y) >= 0.35f -> atan2(y, x)
                share >= 0.5f -> angle
                else -> RESTING_ANGLE
            }
        }

    companion object {
        /** Apple's resting light: from the upper left, highlights on the −135° / 45° corners. */
        const val RESTING_ANGLE = -2.35f

        val Resting = WidgetLight(RESTING_ANGLE, 0f, Argb.White, Argb.White)

        private val MOONLIGHT = Argb.hex(0xD3DCF0)

        fun of(anchor: SkyAnchor, visual: WeatherVisual, sky: SkyPalette, width: Float, height: Float): WidgetLight {
            val visible = ((anchor.elevation + 2.0) / 5.0).toFloat().coerceIn(0f, 1f)
            val moonlight = ((1.0 - cos(2.0 * PI * anchor.moonPhase)) / 2.0).toFloat()
            // Scattered cloud lets the light through; an overcast sky leaves only its own soft light.
            val t = ((visual.cloudCover - 0.3f) / 0.65f).coerceIn(0f, 1f)
            val clear = 1f - t * t * (3f - 2f * t)
            val power = if (anchor.isSun) {
                visible * clear
            } else {
                visible * 0.55f * clear * (0.35f + 0.65f * moonlight)
            }.coerceIn(0f, 1f)
            return WidgetLight(
                angle = atan2(anchor.y * height - height / 2f, anchor.x * width - width / 2f),
                power = power,
                color = Argb.White.lerp(if (anchor.isSun) sky.sun else MOONLIGHT, 0.65f),
                sky = sky.zenith,
            )
        }
    }
}

/**
 * The pane's rounded outline, finely sampled: where each point is, which way it faces, and where
 * it lies around the centre, so light can be laid along the real rim at any proportions.
 */
internal class PaneRim(rect: RectF, radius: Float) {
    private val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    private val measure = PathMeasure(path, true)
    private val cx = rect.centerX()
    private val cy = rect.centerY()
    private val length = measure.length
    private val count = (length / 1.5f).toInt().coerceIn(64, 480)

    val x = FloatArray(count)
    val y = FloatArray(count)

    /** The outward normal. */
    val nx = FloatArray(count)
    val ny = FloatArray(count)

    /** The way from the centre to the point. */
    private val dx = FloatArray(count)
    private val dy = FloatArray(count)

    /** Distance along the outline. */
    private val along = FloatArray(count)

    /** Where the point lies around the centre, 0..1 clockwise from three o'clock (a SweepGradient's turn). */
    private val turn = FloatArray(count)

    /** Point indices in order of [turn]. */
    private val order: IntArray

    init {
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        for (i in 0 until count) {
            along[i] = length * i / count
            measure.getPosTan(along[i], pos, tan)
            var ox = tan[1]
            var oy = -tan[0]
            if (ox * (pos[0] - cx) + oy * (pos[1] - cy) < 0f) {
                ox = -ox
                oy = -oy
            }
            x[i] = pos[0]
            y[i] = pos[1]
            nx[i] = ox
            ny[i] = oy
            val d = hypot(pos[0] - cx, pos[1] - cy).coerceAtLeast(0.001f)
            dx[i] = (pos[0] - cx) / d
            dy[i] = (pos[1] - cy) / d
            turn[i] = ((atan2(pos[1] - cy, pos[0] - cx) / (2 * PI).toFloat()) + 1f) % 1f
        }
        order = (0 until count).sortedBy { turn[it] }.toIntArray()
    }

    /**
     * The point that faces the light at [angle] most squarely; along a flat side that faces it
     * whole, the one straight toward the light from the centre.
     */
    fun facing(angle: Float): Int {
        val lx = cos(angle)
        val ly = sin(angle)
        var best = 0
        var score = -Float.MAX_VALUE
        for (i in 0 until count) {
            val s = nx[i] * lx + ny[i] * ly + 0.05f * (dx[i] * lx + dy[i] * ly)
            if (s > score) {
                score = s
                best = i
            }
        }
        return best
    }

    /**
     * [color] laid around the rim, at each point as opaque as [alpha] says for the rim's outward
     * normal there ([nx], [ny]) and the way from the centre to it ([dx], [dy]).
     */
    fun sweep(color: Argb, alpha: (nx: Float, ny: Float, dx: Float, dy: Float) -> Float): SweepGradient {
        fun at(i: Int) = alpha(nx[i], ny[i], dx[i], dy[i])
        val colors = IntArray(count + 2)
        val stops = FloatArray(count + 2)
        for (k in 0 until count) {
            val i = order[k]
            colors[k + 1] = color.withAlpha(at(i)).value
            stops[k + 1] = turn[i]
        }
        // Close the circle: both ends take the colour between the last point and the first.
        val first = order.first()
        val last = order.last()
        val gap = turn[first] + 1f - turn[last]
        val t = if (gap > 0f) (1f - turn[last]) / gap else 0f
        val wrap = color.withAlpha(at(last) * (1f - t) + at(first) * t).value
        colors[0] = wrap
        stops[0] = 0f
        colors[count + 1] = wrap
        stops[count + 1] = 1f
        return SweepGradient(cx, cy, colors, stops)
    }

    /** The stretch of rim [reach] either side of point [i], into [into]. */
    fun segment(i: Int, reach: Float, into: Path): Path {
        into.reset()
        val start = along[i] - reach
        val end = along[i] + reach
        if (start < 0f) measure.getSegment(start + length, length, into, true)
        if (end > length) measure.getSegment(0f, end - length, into, true)
        measure.getSegment(start.coerceAtLeast(0f), end.coerceAtMost(length), into, true)
        return into
    }
}
