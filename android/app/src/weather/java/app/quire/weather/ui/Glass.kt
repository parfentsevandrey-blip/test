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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.sin

/**
 * A card under glass: a lit pane over it, and colour moving slowly in its edge.
 *
 * Three things, and only the last of them moves.
 *
 * **A sheen.** A vertical wash over the whole card, brightest at the top and darkest at the foot.
 * That is what a curved lit surface does, and it is most of where the card's volume comes from —
 * a flat rectangle of one colour is a hole in the page, and the same rectangle with a top and a
 * bottom is an object on it. It is drawn in white and black rather than in any of the scheme's
 * colours, because "lit from above" is a fact about light and not about a palette.
 *
 * **A bevel.** The edge picks the light up in the same direction: white along the top of the rim,
 * the plain outline colour through the middle, black along the foot. Together with the shadow the
 * card casts, that is the whole of the trick — the three cues that say *raised* are a highlight
 * where the light lands, a shade opposite it, and something underneath.
 *
 * **A flow.** One slow band of the scheme's own primary, tertiary and secondary, repeating along
 * the rim and sliding. Because it repeats, several colours sit on the edge at once and pass
 * through each other, which is what makes it read as light in glass rather than as a border being
 * recoloured. It takes the better part of half a minute to go round; at a glance the card is
 * still, and only somebody looking at it will catch it changing.
 *
 * There were motes riding this rim for two versions — specks with lifetimes, drifting off the line
 * and back. They were a good drawing and the wrong one: a weather screen is a page of numbers you
 * check for four seconds, and anything travelling around the edge of it is a thing to watch rather
 * than a thing to read past. What is left is the part that does not ask for attention.
 *
 * [seed] sets a card apart from its neighbours, so three reading cards in a row are not the same
 * colour in the same place at the same moment. [shimmer] switches off the flow alone: the sheen
 * and the bevel are the card's shape rather than an animation, and stay whatever anybody has
 * asked for.
 *
 * The clock is read inside the draw block rather than in composition. Reading an animated value
 * while composing recomposes the whole card sixty times a second; reading it while drawing redraws
 * it, which is the only part that has changed — and the path and the brushes are built once per
 * size rather than once per frame.
 */
@Composable
fun Modifier.glass(shape: Shape, shimmer: Boolean = true, seed: Int = 0): Modifier {
    val scheme = MaterialTheme.colorScheme

    // Somebody who has switched animation off in the system settings meant this kind too. They
    // keep the pane and lose the flow — and nothing is left running behind the card to produce a
    // frame nobody will look at.
    val context = LocalContext.current
    val moving = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    val clock: State<Float>? = if (shimmer && moving) {
        rememberInfiniteTransition(label = "glass").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing)),
            label = "turn",
        )
    } else {
        null
    }

    val lit = scheme.primary
    val glint = scheme.tertiary
    val tint = scheme.secondary
    val edge = scheme.outlineVariant
    // How hard the light may be laid on. On paper a white sheen has almost nowhere to go and a
    // shadow does all the work; in the dark it is the other way round.
    val night = scheme.surface.luminance() < 0.5f
    val apart = scatter(seed)

    return this.drawWithCache {
        val outline = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val sheen = sheenBrush(night, size)
        val bevel = bevelBrush(night, edge, size)
        val bevelStroke = Stroke(width = EDGE.dp.toPx())
        val flowStroke = Stroke(width = FLOW.dp.toPx())
        // One repeat of the colour band, measured along the diagonal the light runs down.
        val band = (size.width + size.height) / (2f * ZONES)

        onDrawWithContent {
            drawContent()
            drawPath(outline, brush = sheen)
            drawPath(outline, brush = bevel, style = bevelStroke)

            val turn = clock ?: return@onDrawWithContent
            // Slid by exactly one repeat per lap, and the band's two ends are the same colour, so
            // there is neither a seam where repeats meet nor a jump at the end of a lap.
            val slide = wrap(turn.value + apart) * band
            drawPath(
                path = outline,
                brush = Brush.linearGradient(
                    0.00f to lit.copy(alpha = 0.05f),
                    0.16f to glint.copy(alpha = 0.19f),
                    0.30f to tint.copy(alpha = 0.11f),
                    0.44f to lit.copy(alpha = 0.03f),
                    0.60f to lit.copy(alpha = 0.23f),
                    0.70f to glint.copy(alpha = 0.15f),
                    0.86f to tint.copy(alpha = 0.07f),
                    1.00f to lit.copy(alpha = 0.05f),
                    start = Offset(slide, slide),
                    end = Offset(slide + band, slide + band),
                    tileMode = TileMode.Repeated,
                ),
                style = flowStroke,
            )
        }
    }
}

/** The lit face of the pane: bright at the top, falling away to a shade at the foot. */
private fun sheenBrush(night: Boolean, size: Size): Brush = Brush.linearGradient(
    0.00f to Color.White.copy(alpha = if (night) 0.075f else 0.30f),
    0.34f to Color.White.copy(alpha = if (night) 0.014f else 0.05f),
    0.78f to Color.Black.copy(alpha = if (night) 0.012f else 0.010f),
    1.00f to Color.Black.copy(alpha = if (night) 0.055f else 0.040f),
    start = Offset.Zero,
    end = Offset(0f, size.height),
)

/** The edge under the same light: a highlight along the top, a shade along the foot. */
private fun bevelBrush(night: Boolean, edge: Color, size: Size): Brush = Brush.linearGradient(
    0.00f to Color.White.copy(alpha = if (night) 0.34f else 0.85f),
    0.45f to edge.copy(alpha = 0.38f),
    1.00f to Color.Black.copy(alpha = if (night) 0.30f else 0.14f),
    start = Offset.Zero,
    end = Offset(0f, size.height),
)

/** A fixed scatter in 0..1, so each card starts its lap somewhere else. */
private fun scatter(index: Int): Float {
    val value = sin(index * 21.317f + 4.113f) * 12345.678f
    return value - floor(value)
}

/** Into 0..1, whichever side of it the arithmetic came out on. */
private fun wrap(turn: Float): Float = turn - floor(turn)

/**
 * How long the colour takes to go round once.
 *
 * Half a minute, where it started at seven seconds. The brief was light in glass, and glass does
 * not hurry; anything quick enough to notice out of the corner of an eye is asking to be watched.
 */
private const val TURN_MILLIS = 26_000

/** How many repeats of the colour band live on the rim at once. */
private const val ZONES = 2.5f

private const val EDGE = 1.2f
private const val FLOW = 2.4f
