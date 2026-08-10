package app.quire.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * A rim of glass around a card, with light moving in it.
 *
 * Three things, in the order they are drawn. A still hairline, so the card has an edge even when
 * nothing is happening in it. A highlight that travels round and round — a band of the theme's own
 * primary and tertiary in a gradient that repeats, slid along the diagonal, so what you see is a
 * bright length of light passing over the rim rather than the whole rim changing colour. And a few
 * sparks riding the outline itself, each at its own speed, each twinkling on its own phase.
 *
 * The sparks travel by measuring the card's actual outline rather than by walking a rectangle, so
 * they round the corners instead of cutting them, and the effect comes out right whatever shape the
 * card turns out to be.
 *
 * [seed] sets a card apart from its neighbours. Three reading cards side by side are the same size
 * and would otherwise carry the same light in the same place at the same moment, which reads as a
 * repeating pattern rather than as glass; a different phase each is enough to break it.
 *
 * The clock is read inside the draw block rather than in composition. Reading an animated value
 * while composing recomposes the whole card sixty times a second; reading it while drawing redraws
 * it, which is the only part that has changed.
 */
@Composable
fun Modifier.glassEdge(shape: Shape, on: Boolean = true, seed: Int = 0): Modifier {
    if (!on) return this

    val scheme = MaterialTheme.colorScheme
    val rim = scheme.outlineVariant.copy(alpha = 0.55f)

    // The same courtesy the sky gets: somebody who has switched animation off in the system
    // settings meant this kind too. They keep the hairline and lose the light — and nothing is
    // left running behind the card to produce a frame nobody will look at.
    val context = LocalContext.current
    val moving = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    if (!moving) return this.stillEdge(shape, rim)

    val transition = rememberInfiniteTransition(label = "glass")
    val clock: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing)),
        label = "turn",
    )

    val light = scheme.primary
    val glint = scheme.tertiary
    val offset = scatter(seed)

    return this.drawWithCache {
        // The outline, measured once per size rather than once per frame: a path and its measure
        // are the two expensive objects here and neither depends on the clock.
        val outline = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val ruler = PathMeasure().apply { setPath(outline, true) }
        val around = ruler.length
        val hair = Stroke(width = HAIRLINE.dp.toPx())
        val beam = Stroke(width = BEAM.dp.toPx())
        val span = (size.width + size.height) * 0.5f

        onDrawWithContent {
            drawContent()
            drawPath(outline, color = rim, style = hair)
            if (around <= 0f) return@onDrawWithContent

            val turn = (clock.value + offset) % 1f

            // The travelling highlight. A repeating tile whose two ends are the same colour, so
            // there is no seam where one repeat meets the next, slid along the diagonal by exactly
            // one tile per lap — which is what makes it come back to where it started without a
            // jump anybody can see.
            val shift = turn * span
            drawPath(
                path = outline,
                brush = Brush.linearGradient(
                    0.00f to light.copy(alpha = 0f),
                    0.38f to light.copy(alpha = 0f),
                    0.50f to light.copy(alpha = 0.55f),
                    0.56f to glint.copy(alpha = 0.45f),
                    0.62f to light.copy(alpha = 0f),
                    1.00f to light.copy(alpha = 0f),
                    start = Offset(-span + shift, -span + shift),
                    end = Offset(shift, shift),
                    tileMode = TileMode.Repeated,
                ),
                style = beam,
            )

            // And the sparks, riding the rim. Whole numbers of laps, for the same reason the sky's
            // particles have them: a spark that comes back three-quarters of the way round is a
            // spark that visibly jumps once a lap.
            for (index in 0 until SPARKS) {
                val laps = 1 + (index % 3)
                val at = ((turn * laps + scatter(index)) % 1f) * around
                val here = ruler.getPosition(at)
                val twinkle = 0.5f + 0.5f * sin(
                    ((turn * (2 + index % 3) + scatter(index + 7)) % 1f) * 2f * PI.toFloat(),
                )
                val alpha = 0.2f + 0.5f * twinkle
                val core = (0.8f + 0.7f * scatter(index + 3)) * density
                drawCircle(glint.copy(alpha = alpha * 0.3f), core * 2.6f, here)
                drawCircle(light.copy(alpha = alpha), core, here)
            }
        }
    }
}

/** The hairline on its own, for a phone that has been told not to animate anything. */
private fun Modifier.stillEdge(shape: Shape, rim: Color): Modifier = this.drawWithCache {
    val outline = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
    }
    val hair = Stroke(width = HAIRLINE.dp.toPx())
    onDrawWithContent {
        drawContent()
        drawPath(outline, color = rim, style = hair)
    }
}

/** A fixed scatter in 0..1, so the sparks are spread round the rim and stay where they were put. */
private fun scatter(index: Int): Float {
    val value = sin(index * 21.317f + 4.113f) * 12345.678f
    return value - floor(value)
}

/** How long the light takes to go round once. Slow: this is a shimmer, not a chase light. */
private const val TURN_MILLIS = 7_000

private const val HAIRLINE = 1f
private const val BEAM = 1.6f
private const val SPARKS = 5
