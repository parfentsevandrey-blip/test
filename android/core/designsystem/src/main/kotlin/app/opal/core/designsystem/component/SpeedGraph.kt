package app.opal.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.theme.OpalTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * Live throughput: download as a filled curve, upload as a line. [down]/[up] are bytes per second,
 * oldest first (one sample per second from Tor's BW events). The vertical scale eases toward the
 * window maximum so the graph never jumps.
 */
@Composable
fun SpeedGraph(
    down: ImmutableList<Long>,
    up: ImmutableList<Long>,
    description: String,
    modifier: Modifier = Modifier,
    capacity: Int = 60,
) {
    val colors = OpalTheme.colors
    val peak = (down + up).maxOrNull()?.coerceAtLeast(MIN_SCALE) ?: MIN_SCALE
    val scale by animateFloatAsState(peak * 1.15f, tween(600), label = "speedScale")
    Canvas(modifier.semantics { contentDescription = description }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val step = size.width / (capacity - 1)
        fun path(samples: List<Long>, closed: Boolean): Path {
            val p = Path()
            if (samples.isEmpty()) return p
            val start = capacity - samples.size
            var prev: Offset? = null
            samples.forEachIndexed { i, v ->
                val point = Offset((start + i) * step, size.height - (v / scale) * size.height)
                if (prev == null) {
                    p.moveTo(point.x, if (closed) size.height else point.y)
                    if (closed) p.lineTo(point.x, point.y)
                } else {
                    val mid = (prev!!.x + point.x) / 2f
                    p.cubicTo(mid, prev!!.y, mid, point.y, point.x, point.y)
                }
                prev = point
            }
            if (closed && prev != null) {
                p.lineTo(prev!!.x, size.height)
                p.close()
            }
            return p
        }
        val fill =
            Brush.verticalGradient(
                listOf(colors.download.copy(alpha = 0.35f), colors.download.copy(alpha = 0f))
            )
        drawPath(path(down, closed = true), fill)
        drawPath(
            path(down, closed = false),
            colors.download,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path(up, closed = false),
            colors.upload,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private const val MIN_SCALE = 50_000L
