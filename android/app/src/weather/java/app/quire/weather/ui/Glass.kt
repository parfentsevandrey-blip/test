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
 * Frosted glass, quietly alive.
 *
 * Three layers, each with one job.
 *
 * **A hairline.** One clean rim, white in the dark and ink on paper, the same weight all the way
 * round. The earlier bevels and colour-washed rims photographed badly for a reason worth writing
 * down: an animated rim is only ever seen mid-frame, and a rim that is beautiful *in motion* but
 * muddy in any single frame is muddy — a screenshot is a frame, and so is every glance.
 *
 * **Frost.** A breath of white falling from the card's top edge, gone by a third of the way down.
 * Against the page's gradient this is what makes the card read as a pane of something rather than
 * a grey rectangle: lit where a pane catches the sky, plain where it does not.
 *
 * **Drift.** One wide, soft band of light crossing the face on the diagonal, endlessly and very
 * slowly — a lap takes eight and a half seconds, and the band is so wide and so faint that what
 * registers is not "something is moving" but "the light is not quite still", which is how glass
 * behaves in a room where anything at all is happening. It is drawn as a repeating gradient tile
 * slid by exactly one period per lap, so the loop has no seam and no jump; it is filled inside
 * the card's own outline, so it cannot reach anything outside the card; and every earlier version
 * of this file that put an *object* on the rim — sparks, motes, a comet — died of the same
 * disease, which is that an object asks to be watched and a page of numbers must not.
 *
 * [seed] offsets each card's lap so neighbours are not lit in step. [shimmer] switches the drift
 * off alone; the hairline and the frost are the card's shape and stay. A phone whose animations
 * are switched off system-wide keeps the shape and never starts the clock.
 *
 * The clock is read inside the draw block, not in composition, and the brushes and paths are
 * built once per size — a frame costs three draws and no allocation.
 */
@Composable
fun Modifier.glass(shape: Shape, shimmer: Boolean = true, seed: Int = 0): Modifier {
    val scheme = MaterialTheme.colorScheme
    val night = scheme.surface.luminance() < 0.5f

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
            label = "drift",
        )
    } else {
        null
    }

    val apart = scatter(seed)
    val rim = if (night) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
    val frostInk = Color.White.copy(alpha = if (night) 0.065f else 0.35f)
    val shine = Color.White.copy(alpha = if (night) 0.05f else 0.11f)
    val clear = Color.White.copy(alpha = 0f)

    return this.drawWithCache {
        val outline = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val hair = Stroke(width = HAIRLINE.dp.toPx())
        val frost = Brush.verticalGradient(
            0f to frostInk,
            FROST_REACH to clear,
            1f to clear,
            endY = size.height,
        )
        // The drift's geometry: one tile the length of the card's diagonal run, repeated, so
        // sliding it by exactly one tile per lap closes the loop invisibly.
        val run = (size.width + size.height) * 0.5f

        onDrawWithContent {
            drawContent()
            drawPath(outline, brush = frost)
            drawPath(outline, color = rim, style = hair)

            val lap = clock ?: return@onDrawWithContent
            val slide = wrap(lap.value + apart) * run
            drawPath(
                path = outline,
                brush = Brush.linearGradient(
                    0.00f to clear,
                    0.38f to clear,
                    0.52f to shine,
                    0.66f to clear,
                    1.00f to clear,
                    start = Offset(slide, slide * SLOPE),
                    end = Offset(slide + run, (slide + run) * SLOPE),
                    tileMode = TileMode.Repeated,
                ),
            )
        }
    }
}

/** A fixed scatter in 0..1, so each card is somewhere else in its lap. */
private fun scatter(index: Int): Float {
    val value = sin(index * 21.317f + 4.113f) * 12345.678f
    return value - floor(value)
}

/** Into 0..1, whichever side of it the arithmetic came out on. */
private fun wrap(turn: Float): Float = turn - floor(turn)

/** How long one pass of the light takes. Slow enough to be weather, fast enough to be alive. */
private const val LAP_MILLIS = 8_500

/** How far down the card the frost reaches before the pane goes plain. */
private const val FROST_REACH = 0.32f

/** The band leans off the horizontal, so it reads as light and not as a scanline. */
private const val SLOPE = 0.6f

private const val HAIRLINE = 1f
