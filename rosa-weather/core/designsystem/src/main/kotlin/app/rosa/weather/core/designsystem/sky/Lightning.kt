package app.rosa.weather.core.designsystem.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * One lightning channel, made for its strike: a jagged path from the top of the sky down to where
 * it ends, built by midpoint displacement (every segment split and its middle pushed aside, less at
 * each finer scale) so it is jagged at every scale like the real thing, with a few forks leaving
 * it and dying out. Coordinates are fractions of the scene's width and height.
 */
internal class LightningChannel(seed: Int, x: Float, end: Float) {
    /** The main channel first, then its forks, each with how bright it burns (0..1). */
    val strokes: List<Pair<List<Offset>, Float>>

    init {
        val random = Random(seed)
        val main = jagged(Offset(x, -0.02f), Offset(x + (random.nextFloat() - 0.5f) * 0.18f, end), 0.2f, 6, random)
        val forks = ArrayList<Pair<List<Offset>, Float>>()
        repeat(2 + random.nextInt(3)) {
            val at = (main.size * (0.18f + random.nextFloat() * 0.5f)).toInt().coerceIn(1, main.size - 2)
            val from = main[at]
            val heading = main[(at + 4).coerceAtMost(main.size - 1)] - main[at - 1]
            val angle = (if (random.nextBoolean()) 1f else -1f) * (0.4f + random.nextFloat() * 0.45f)
            val length = (end - from.y) * (0.25f + random.nextFloat() * 0.35f)
            val direction = heading.rotate(angle).normalized()
            val to = from + direction * length
            forks += jagged(from, to, 0.24f, 5, random) to (0.4f + random.nextFloat() * 0.3f)
        }
        strokes = listOf(main to 1f) + forks
    }

    private fun jagged(from: Offset, to: Offset, roughness: Float, levels: Int, random: Random): List<Offset> {
        var points = listOf(from, to)
        var spread = (to - from).getDistance() * roughness
        repeat(levels) {
            val next = ArrayList<Offset>(points.size * 2)
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val across = (b - a).rotate(1.5707964f).normalized()
                next += a
                next += (a + b) / 2f + across * ((random.nextFloat() - 0.5f) * 2f * spread)
            }
            next += points.last()
            points = next
            spread *= 0.48f
        }
        return points
    }
}

private fun Offset.rotate(angle: Float) = Offset(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))

private fun Offset.normalized(): Offset {
    val length = max(hypot(x, y), 0.0001f)
    return Offset(x / length, y / length)
}

private val Glow = Color(0xFF9DB2FF)
private val Core = Color(0xFFF4F7FF)

/**
 * Draws [channel] at [brightness] over the whole scene: a crisp white-blue core in a halo, at the
 * display's own resolution rather than the soft sky's, so the strike reads sharp through the rain.
 */
internal fun DrawScope.drawLightning(channel: LightningChannel, brightness: Float) {
    if (brightness <= 0.01f) return
    val unit = size.minDimension
    for ((points, strength) in channel.strokes) {
        val path = Path().apply {
            moveTo(points[0].x * size.width, points[0].y * size.height)
            for (i in 1 until points.size) lineTo(points[i].x * size.width, points[i].y * size.height)
        }
        val b = (brightness * strength).coerceIn(0f, 1f)
        drawPath(path, Glow, alpha = 0.07f * b, style = Stroke(unit * 0.07f * strength, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Glow, alpha = 0.16f * b, style = Stroke(unit * 0.028f * strength, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Glow, alpha = 0.42f * b, style = Stroke(unit * 0.011f * strength, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Core, alpha = 0.97f * b, style = Stroke(max(2f, unit * 0.0052f * strength), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
