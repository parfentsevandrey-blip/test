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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * A card under glass: a lit pane over it, and a light running round its edge.
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
 * **A comet.** A short bright streak that runs round the rim, tapering from a lit head to nothing
 * at its tail and shifting colour down its length — primary at the head, tertiary through the
 * middle, secondary at the tail. It travels by *arc length along the card's own outline*, which is
 * the thing the version before it got wrong: that one was a gradient sliding across the card in
 * screen space, so it did not go round anything, and slowed to invisibility it may as well not
 * have been there. This one is on the path, so it rounds the corners, and it is quick — a lap
 * takes about three seconds, which is fast enough to catch out of the corner of an eye.
 *
 * The taper is two dozen strokes stacked rather than two dozen laid end to end. Each one starts
 * further along than the last and they all finish at the head, so the light builds up by how many
 * have piled at a point — and, because every layer is a single unbroken stroke, there is no joint
 * anywhere for the notch to appear at. Their paths, strokes and colours are built once per size,
 * so a frame is two dozen calls to `getSegment` and as many strokes, and nothing else.
 *
 * [seed] sets a card apart from its neighbours, so six of them are not all lit in the same corner
 * at the same moment. [shimmer] switches off the comet alone: the sheen and the bevel are the
 * card's shape rather than an animation, and stay whatever anybody has asked for.
 *
 * The clock is read inside the draw block rather than in composition. Reading an animated value
 * while composing recomposes the whole card sixty times a second; reading it while drawing redraws
 * it, which is the only part that has changed.
 */
@Composable
fun Modifier.glass(shape: Shape, shimmer: Boolean = true, seed: Int = 0): Modifier {
    val scheme = MaterialTheme.colorScheme

    // Somebody who has switched animation off in the system settings meant this kind too. They
    // keep the pane and lose the comet — and nothing is left running behind the card to produce a
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
            animationSpec = infiniteRepeatable(tween(LAP_MILLIS, easing = LinearEasing)),
            label = "lap",
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
        val ruler = PathMeasure().apply { setPath(outline, true) }
        val around = ruler.length

        val sheen = sheenBrush(night, size)
        val bevel = bevelBrush(night, edge, size)
        val bevelStroke = Stroke(width = EDGE.dp.toPx())

        // The comet, laid out once. Nothing here changes from frame to frame except where the
        // head is, so all of it — the paths to measure into, the strokes and the colours — is
        // built with the size and reused.
        val streak = min(around * STREAK_SHARE, STREAK_CAP.dp.toPx())
        val pieces = List(LAYERS) { Path() }
        val reaches = FloatArray(LAYERS) { index -> streak * (LAYERS - index) / LAYERS }
        val strokes = List(LAYERS) { index ->
            val along = index / (LAYERS - 1f)
            Stroke(
                width = (THIN + (THICK - THIN) * along).dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val inks = List(LAYERS) { index ->
            val along = index / (LAYERS - 1f)
            val hue = if (along < 0.5f) {
                lerp(tint, glint, along * 2f)
            } else {
                lerp(glint, lit, (along - 0.5f) * 2f)
            }
            hue.copy(alpha = LAYER)
        }

        onDrawWithContent {
            drawContent()
            drawPath(outline, brush = sheen)
            drawPath(outline, brush = bevel, style = bevelStroke)

            val lap = clock ?: return@onDrawWithContent
            if (around <= 0f) return@onDrawWithContent
            val head = wrap(lap.value + apart) * around
            // Nested rather than laid end to end. Cutting the streak into pieces and giving each
            // its own alpha is the obvious way to taper it and it does not work: two translucent
            // strokes that merely touch are each antialiased against what is behind them, so the
            // pair composites to less than either and the streak comes out notched at every joint
            // — the same fault the hourly curve had when it was drawn a column at a time. These
            // all start at their own point and run to the same head, so every one of them is a
            // single unbroken stroke and the fade is how many of them have piled up. The deepest
            // steps land at the tail, where the light is faint enough that nobody can see them,
            // and the widths grow by a fifteenth of a point a layer, which is nothing at all.
            for (index in 0 until LAYERS) {
                strand(ruler, pieces[index], around, head - reaches[index], head, inks[index], strokes[index])
            }
        }
    }
}

/**
 * One short piece of the streak, laid on the outline between two distances along it.
 *
 * A path measure will not hand back a segment that runs off the end and back to the start, so a
 * layer straddling the seam is asked for in two halves — which most of them are for the moment
 * the comet is crossing whatever point the outline happens to begin at.
 */
private fun DrawScope.strand(
    ruler: PathMeasure,
    piece: Path,
    around: Float,
    from: Float,
    to: Float,
    ink: Color,
    stroke: Stroke,
) {
    val start = wrap(from / around) * around
    val end = wrap(to / around) * around
    piece.reset()
    if (start <= end) {
        ruler.getSegment(start, end, piece, true)
    } else {
        ruler.getSegment(start, around, piece, true)
        ruler.getSegment(0f, end, piece, true)
    }
    drawPath(piece, color = ink, style = stroke)
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
 * How long the comet takes to go round once.
 *
 * Three seconds. It was seven at first, then twenty-six in an attempt to quieten it, and at
 * twenty-six it was not quiet — it was invisible, which is a different thing and not what a switch
 * marked "glass edges" should leave you with. Slow enough to be still is slow enough to be absent.
 */
private const val LAP_MILLIS = 3_100

/** How much of the rim the streak covers, and the most it may be on a long one. */
private const val STREAK_SHARE = 0.3f
private const val STREAK_CAP = 260f

/**
 * How many layers the streak is built from, and how much each one carries.
 *
 * Twenty-four at nine per cent apiece compose to about nine tenths at the head — bright enough to
 * be the point of the effect — while the step between two neighbouring layers is small enough
 * that the ramp reads as continuous.
 */
private const val LAYERS = 24
private const val LAYER = 0.09f

private const val THIN = 1f
private const val THICK = 2.8f

private const val EDGE = 1.2f
