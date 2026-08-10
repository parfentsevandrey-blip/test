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
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * A card under glass: a lit pane over it, and light that keeps moving in its edge without ever
 * going anywhere.
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
 * **A glow that breathes.** Three washes of colour lie over the whole rim at once — the primary
 * from the top left, the tertiary from the top right, the secondary up from the foot — and each
 * one rises and falls on its own count, one, two and three times a lap. Nothing travels. What you
 * see is the bright part of the edge drifting from one quarter of the card to another and the
 * colour of it turning over as it goes, which is what glass does under a light somebody is walking
 * past, and is not a marquee going round a sign.
 *
 * That distinction is the whole history of this file. It was a comet before — a bright streak
 * running the rim on a three-second lap — and a comet is a *thing*, so a page of six of them is
 * six things circling while you are trying to read a temperature. Before that it was a gradient
 * sliding across the card, slowed until it was not quiet but absent. What is here now has no
 * object in it to follow: the light is everywhere on the rim always, and only its weight changes.
 *
 * The three washes are fixed brushes, built once with the size. A frame changes three floats and
 * hands them to `drawPath` as its alpha — nothing is measured, nothing is rebuilt and nothing is
 * allocated, which is what lets nine cards do this at once without it costing anything.
 *
 * [seed] sets a card apart from its neighbours, so six of them are not all bright in the same
 * corner at the same moment. [shimmer] switches the glow off alone: the sheen and the bevel are
 * the card's shape rather than an animation, and stay whatever anybody has asked for.
 *
 * The clock is read inside the draw block rather than in composition. Reading an animated value
 * while composing recomposes the whole card sixty times a second; reading it while drawing redraws
 * it, which is the only part that has changed.
 */
@Composable
fun Modifier.glass(shape: Shape, shimmer: Boolean = true, seed: Int = 0): Modifier {
    val scheme = MaterialTheme.colorScheme

    // Somebody who has switched animation off in the system settings meant this kind too. They
    // keep the pane and its still rim, and nothing is left running behind the card to produce a
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
            animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = LinearEasing)),
            label = "breath",
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
        val rimStroke = Stroke(width = RIM.dp.toPx())

        // One wash per accent, each brightest at a different quarter of the card and falling away
        // across it. Fixed: only how much of each is showing changes.
        val washes = listOf(
            washBrush(lit, Offset.Zero, Offset(size.width, size.height)),
            washBrush(glint, Offset(size.width, 0f), Offset(0f, size.height)),
            washBrush(tint, Offset(size.width / 2f, size.height), Offset(size.width / 2f, 0f)),
        )

        onDrawWithContent {
            drawContent()
            drawPath(outline, brush = sheen)
            drawPath(outline, brush = bevel, style = bevelStroke)

            val breath = clock ?: return@onDrawWithContent
            val turn = wrap(breath.value + apart)
            for (index in washes.indices) {
                // One, two and three risings a lap. Whole numbers, so the whole thing comes back
                // to itself; different ones, so the three of them never settle into a rhythm.
                val swell = 0.5f + 0.5f * sin(TAU * (turn * (index + 1) + PHASES[index]))
                drawPath(
                    path = outline,
                    brush = washes[index],
                    alpha = FLOOR + (1f - FLOOR) * swell,
                    style = rimStroke,
                )
            }
        }
    }
}

/** One accent laid over the whole rim, strongest where its axis begins. */
private fun washBrush(ink: Color, from: Offset, to: Offset): Brush = Brush.linearGradient(
    0.00f to ink.copy(alpha = 0.50f),
    0.55f to ink.copy(alpha = 0.13f),
    1.00f to ink.copy(alpha = 0.05f),
    start = from,
    end = to,
)

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

/** A fixed scatter in 0..1, so each card is somewhere else in the breath. */
private fun scatter(index: Int): Float {
    val value = sin(index * 21.317f + 4.113f) * 12345.678f
    return value - floor(value)
}

/** Into 0..1, whichever side of it the arithmetic came out on. */
private fun wrap(turn: Float): Float = turn - floor(turn)

private const val TAU = 2f * PI.toFloat()

/**
 * How long the slowest of the three washes takes to rise and fall once.
 *
 * Nine seconds, and the other two ride it at twice and three times that, so the rim is always
 * changing and never at any speed you would call movement.
 */
private const val BREATH_MILLIS = 9_000

/** Where each wash starts in its own cycle, so they do not swell together. */
private val PHASES = floatArrayOf(0f, 0.37f, 0.71f)

/** How much of a wash is showing at its lowest, so the rim never goes plain. */
private const val FLOOR = 0.22f

private const val EDGE = 1.2f
private const val RIM = 1.8f
