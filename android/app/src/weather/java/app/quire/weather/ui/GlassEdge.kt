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
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A rim of glass around a card, with light living in it.
 *
 * Four things, in the order they are drawn.
 *
 * **A bevel.** The edge is not one flat hairline but a stroke whose colour changes along the
 * card's diagonal — accent where the light would fall, the plain outline colour through the
 * middle, a cooler tone where it falls away. That is what makes an edge read as the lit side of a
 * pane rather than as a border, and it is there before anything moves.
 *
 * **A flow.** A wider, softer stroke carrying a band of colour that repeats two and a half times
 * round the card and slides. Because it repeats, several different colours are on the rim at any
 * one moment and they travel through each other — which is what iridescence actually is, and the
 * difference between a rim that shimmers and a rim that is being swept by one bright line.
 *
 * **Motes.** Not five dots on a wire. Each one is born, blooms and goes out on its own schedule,
 * so the field is always changing who is in it; each travels *unevenly*, gathering speed and
 * easing off again, so it drifts rather than marches; each floats a little off the line and back,
 * so it is a speck suspended in the glass rather than a bead threaded on it; and each drags a
 * short trail behind it in the direction it happens to be going. They take the accent, the
 * tertiary and the secondary between them, so the field is as iridescent as the flow it rides in.
 *
 * How many there are comes from how long the rim is, not from a constant: a small reading card
 * gets five and the five-day card gets a dozen, which is what keeps them the same *density* of
 * light rather than the same count crammed into different perimeters.
 *
 * Everything closes on the lap. A mote's uneven travel is `t + k·sin(2πwt)/2πw` for whole `w`,
 * which is periodic in `t` and stays monotone while `k < 1` — so it speeds up and slows down and
 * still comes back to exactly where it started, with no jump for anyone to catch. The same is true
 * of its float, its bloom and its twinkle: whole numbers of cycles per lap, every one of them.
 *
 * [seed] sets a card apart from its neighbours. Three reading cards side by side are the same size
 * and would otherwise carry the same light in the same place at the same moment, which reads as a
 * repeating pattern rather than as glass.
 *
 * The clock is read inside the draw block rather than in composition. Reading an animated value
 * while composing recomposes the whole card sixty times a second; reading it while drawing redraws
 * it, which is the only part that has changed — and the path, its measure and the brushes are
 * built once per size rather than once per frame.
 */
@Composable
fun Modifier.glassEdge(shape: Shape, on: Boolean = true, seed: Int = 0): Modifier {
    if (!on) return this

    val scheme = MaterialTheme.colorScheme
    val lit = scheme.primary
    val glint = scheme.tertiary
    val tint = scheme.secondary
    val edge = scheme.outlineVariant
    // Whether light on this page can bloom at all; see the mote below for why it matters.
    val night = scheme.surface.luminance() < 0.5f

    // The same courtesy the sky gets: somebody who has switched animation off in the system
    // settings meant this kind too. They keep the bevel and lose the light — and nothing is left
    // running behind the card to produce a frame nobody will look at.
    val context = LocalContext.current
    val moving = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    if (!moving) return this.stillEdge(shape, lit, edge, glint)

    val transition = rememberInfiniteTransition(label = "glass")
    val clock: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing)),
        label = "turn",
    )
    val apart = scatter(seed * 5 + 1)

    return this.drawWithCache {
        val outline = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val ruler = PathMeasure().apply { setPath(outline, true) }
        val around = ruler.length

        val bevel = bevelBrush(lit, edge, glint, size)
        val bevelStroke = Stroke(width = EDGE.dp.toPx())
        val flowStroke = Stroke(width = FLOW.dp.toPx())

        // One repeat of the colour band, measured along the diagonal the light runs down.
        val band = (size.width + size.height) / (2f * ZONES)
        val float = DRIFT.dp.toPx()
        val trail = TRAIL.dp.toPx()
        val unit = density
        val motes = (around / (SPACING * unit)).toInt().coerceIn(MIN_MOTES, MAX_MOTES)

        onDrawWithContent {
            drawContent()
            drawPath(outline, brush = bevel, style = bevelStroke)
            if (around <= 0f) return@onDrawWithContent

            val turn = (clock.value + apart) % 1f

            // The flow. Slid by exactly one repeat per lap, and the tile's two ends are the same
            // colour, so there is neither a seam between repeats nor a jump at the end of a lap.
            val slide = turn * band
            drawPath(
                path = outline,
                brush = Brush.linearGradient(
                    0.00f to lit.copy(alpha = 0.10f),
                    0.16f to glint.copy(alpha = 0.34f),
                    0.30f to tint.copy(alpha = 0.22f),
                    0.44f to lit.copy(alpha = 0.06f),
                    0.60f to lit.copy(alpha = 0.42f),
                    0.70f to glint.copy(alpha = 0.28f),
                    0.86f to tint.copy(alpha = 0.12f),
                    1.00f to lit.copy(alpha = 0.10f),
                    start = Offset(slide, slide),
                    end = Offset(slide + band, slide + band),
                    tileMode = TileMode.Repeated,
                ),
                style = flowStroke,
            )

            for (index in 0 until motes) {
                val place = scatter(index * 4)
                val heft = scatter(index * 4 + 1)
                val bend = scatter(index * 4 + 2)
                val born = scatter(index * 4 + 3)

                // Born, seen, gone. Cubed, so a mote spends most of the lap dark and blooms
                // briefly: a field whose membership changes is alive, a field of permanent dots
                // is a string of lights.
                val life = (turn + born) % 1f
                val bloom = sin(PI.toFloat() * life).let { it * it * it }
                val alpha = bloom * (0.28f + 0.44f * heft)
                if (alpha < FAINTEST) continue

                // Uneven travel: monotone, periodic, and nowhere near constant speed.
                val laps = 1 + (index % 3)
                val wobbles = 1 + (index % 2)
                val base = turn * laps + place
                val walk = base + SWAY * sin(TAU * (wobbles * base + bend)) / (TAU * wobbles)
                val at = wrap(walk) * around

                val here = ruler.getPosition(at)
                val tangent = ruler.getTangent(at)
                val reach = hypot(tangent.x, tangent.y).coerceAtLeast(0.0001f)
                // Off the line and back, along the rim's own normal.
                val aside = Offset(-tangent.y / reach, tangent.x / reach) *
                    (float * sin(TAU * (turn * (2 + index % 3) + heft)))
                val centre = here + aside
                val behind = ruler.getPosition(wrap((at - trail) / around) * around) + aside

                val hue = when (index % 3) {
                    0 -> lit
                    1 -> glint
                    else -> tint
                }
                val core = (0.7f + 1.3f * heft) * unit
                if (night) {
                    drawCircle(hue.copy(alpha = alpha * 0.18f), core * HALO, centre)
                    drawCircle(hue.copy(alpha = alpha * 0.30f), core * 0.55f, behind)
                    drawCircle(hue.copy(alpha = alpha), core, centre)
                } else {
                    // On paper, light does not bloom. Every accent in a light scheme is darker
                    // than the card it is on, so a soft halo of one is not a glow — it is a grey
                    // smudge, and six cards' worth of them read as a dirty screen. In daylight a
                    // mote is a smaller, harder, more saturated speck instead.
                    drawCircle(hue.copy(alpha = alpha * 0.34f), core * 0.5f, behind)
                    drawCircle(hue.copy(alpha = (alpha * 1.4f).coerceAtMost(0.9f)), core * 0.72f, centre)
                }
            }
        }
    }
}

/** The bevel on its own, for a phone that has been told not to animate anything. */
private fun Modifier.stillEdge(shape: Shape, lit: Color, edge: Color, glint: Color): Modifier =
    this.drawWithCache {
        val outline = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val bevel = bevelBrush(lit, edge, glint, size)
        val stroke = Stroke(width = EDGE.dp.toPx())
        onDrawWithContent {
            drawContent()
            drawPath(outline, brush = bevel, style = stroke)
        }
    }

/**
 * The lit edge: accent at the corner the light comes from, plain outline through the middle, a
 * cooler tone where it falls away. Tonal rather than light-and-dark, because "brighter" is white
 * on a dark card and black on a light one and there is no one colour that means it in both.
 */
private fun bevelBrush(lit: Color, edge: Color, glint: Color, size: Size): Brush =
    Brush.linearGradient(
        0f to lit.copy(alpha = 0.32f),
        0.5f to edge.copy(alpha = 0.42f),
        1f to glint.copy(alpha = 0.22f),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )

/** A fixed scatter in 0..1, so the motes are spread round the rim and stay where they were put. */
private fun scatter(index: Int): Float {
    val value = sin(index * 21.317f + 4.113f) * 12345.678f
    return value - floor(value)
}

/** Into 0..1, whichever side of it the arithmetic came out on. */
private fun wrap(turn: Float): Float = turn - floor(turn)

private const val TAU = 2f * PI.toFloat()

/** How long the light takes to go round once. Slow: this is glass catching the light, not a sign. */
private const val TURN_MILLIS = 11_000

/** How far a mote's speed swings either side of even. Below one, so it never doubles back. */
private const val SWAY = 0.62f

/** How many repeats of the colour band live on the rim at once. */
private const val ZONES = 2.5f

private const val EDGE = 1.2f
private const val FLOW = 2.4f

/** How far a mote floats off the line, and how far its trail lags, in points. */
private const val DRIFT = 2.2f
private const val TRAIL = 7f

/** One mote per this many points of rim, so a small card is not as crowded as a large one. */
private const val SPACING = 46f
private const val MIN_MOTES = 5
private const val MAX_MOTES = 16

private const val HALO = 3f

/** Below this a mote is a rounding error with a draw call attached. */
private const val FAINTEST = 0.02f
