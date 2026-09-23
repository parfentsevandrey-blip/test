package app.papersky.weather.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hairline UI icons on a 24-unit grid, stroke 1.5 u (DESIGN_DOCTRINE §7); weather glyphs live in GlyphRenderer. */
enum class PaperIcon { Back, Close, Plus, Search, Settings, Widgets, Trash, Check, Chevron, Location, Refresh, Language, Info }

@Composable
fun PaperIconView(icon: PaperIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 1.5f * u, StrokeCap.Round)
        when (icon) {
            PaperIcon.Back -> { line(19f, 12f, 5f, 12f); line(5f, 12f, 11f, 6f); line(5f, 12f, 11f, 18f) }
            PaperIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            PaperIcon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            PaperIcon.Check -> { line(5f, 12.5f, 10f, 17.5f); line(10f, 17.5f, 19f, 7f) }
            PaperIcon.Chevron -> { line(8f, 10f, 12f, 14f); line(12f, 14f, 16f, 10f) }
            PaperIcon.Search -> {
                drawCircle(color, 6f * u, Offset(10.5f * u, 10.5f * u), style = stroke)
                line(15f, 15f, 19.5f, 19.5f)
            }
            PaperIcon.Settings -> settings(u, color, stroke)
            PaperIcon.Widgets -> {
                val r = 1.6f * u
                listOf(Offset(4f, 4f), Offset(13.5f, 4f), Offset(4f, 13.5f), Offset(13.5f, 13.5f)).forEach { o ->
                    drawRoundRect(color, Offset(o.x * u, o.y * u), Size(6.5f * u, 6.5f * u), androidx.compose.ui.geometry.CornerRadius(r), style = stroke)
                }
            }
            PaperIcon.Trash -> {
                line(4.5f, 7f, 19.5f, 7f)
                line(9.5f, 4.5f, 14.5f, 4.5f)
                val p = Path().apply { moveTo(6.5f * u, 7f * u); lineTo(7.5f * u, 19.5f * u); lineTo(16.5f * u, 19.5f * u); lineTo(17.5f * u, 7f * u) }
                drawPath(p, color, style = stroke)
            }
            PaperIcon.Location -> {
                val p = Path().apply {
                    moveTo(12f * u, 21f * u)
                    cubicTo(6.5f * u, 15f * u, 5.5f * u, 12f * u, 5.5f * u, 9.5f * u)
                    cubicTo(5.5f * u, 5.8f * u, 8.4f * u, 3f * u, 12f * u, 3f * u)
                    cubicTo(15.6f * u, 3f * u, 18.5f * u, 5.8f * u, 18.5f * u, 9.5f * u)
                    cubicTo(18.5f * u, 12f * u, 17.5f * u, 15f * u, 12f * u, 21f * u)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawCircle(color, 2.2f * u, Offset(12f * u, 9.5f * u), style = stroke)
            }
            PaperIcon.Refresh -> {
                drawArc(color, -60f, 290f, false, Offset(5f * u, 5f * u), Size(14f * u, 14f * u), style = stroke)
                line(19f, 4f, 19f, 9.5f); line(19f, 9.5f, 13.8f, 9.5f)
            }
            PaperIcon.Language -> {
                drawCircle(color, 8.5f * u, Offset(12f * u, 12f * u), style = stroke)
                drawOval(color, Offset(8f * u, 3.5f * u), Size(8f * u, 17f * u), style = stroke)
                line(3.8f, 12f, 20.2f, 12f)
            }
            PaperIcon.Info -> {
                drawCircle(color, 8.5f * u, Offset(12f * u, 12f * u), style = stroke)
                line(12f, 11f, 12f, 16.5f)
                drawCircle(color, 1.3f * u, Offset(12f * u, 7.8f * u))
            }
        }
    }
}

private fun DrawScope.settings(u: Float, color: Color, stroke: Stroke) {
    // Three rules with a bead on each: the printer's adjustment, not a mechanical gear.
    val rows = floatArrayOf(6.5f, 12f, 17.5f)
    val knobs = floatArrayOf(15f, 8.5f, 13f)
    for (i in rows.indices) {
        val y = rows[i] * u
        val k = knobs[i] * u
        drawLine(color, Offset(4f * u, y), Offset(k - 2.2f * u, y), 1.5f * u, StrokeCap.Round)
        drawLine(color, Offset(k + 2.2f * u, y), Offset(20f * u, y), 1.5f * u, StrokeCap.Round)
        drawCircle(color, 2.2f * u, Offset(k, y), style = stroke)
    }
}
