package app.quire.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.quire.weather.Sky
import kotlin.math.PI
import kotlin.math.sin

/**
 * The weather, moving.
 *
 * A weather app that draws a raincloud and then sits perfectly still is a diagram of the weather.
 * This is the sky itself: rain falls behind the temperature, snow drifts, cloud passes, stars come
 * and go, and the sun breathes. It sits under everything and is never touched, so it costs nothing
 * but the frames it draws.
 *
 * Every particle's position is a pure function of one looping clock and its own index. Nothing is
 * stored between frames and nothing is allocated in one: a field of forty drops is forty sines,
 * not forty objects being stepped and collected. That also makes it deterministic — the same
 * second of the same sky draws the same picture, which is what lets a render test look at it.
 *
 * The loop is seamless because every particle's speed is a whole number of laps per period. A drop
 * moving at two and a third laps arrives back a third of the way down and the whole field visibly
 * jumps; at two laps exactly, nobody can tell where the seam is.
 */
@Composable
fun LiveSky(sky: Sky, day: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    // Somebody who has turned animation off in the system settings has said what they want, and
    // an endless one is exactly the kind they meant. They get the same sky, standing still.
    val context = LocalContext.current
    val moving = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    val transition = rememberInfiniteTransition(label = "sky")
    val running by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PERIOD_MILLIS, easing = LinearEasing)),
        label = "clock",
    )
    val clock = if (moving) running else STILL

    val ink = scheme.onSurface
    val accent = scheme.primary

    Canvas(modifier) {
        when (sky) {
            Sky.CLEAR -> if (day) sun(clock, accent) else stars(clock, ink)
            Sky.MOSTLY_CLEAR -> if (day) sun(clock, accent) else stars(clock, ink)
            Sky.PARTLY_CLOUDY, Sky.OVERCAST -> cloud(clock, ink)
            Sky.FOG -> fog(clock, ink)
            Sky.DRIZZLE -> rain(clock, accent, drops = 26, weight = 0.7f)
            Sky.RAIN -> rain(clock, accent, drops = 40, weight = 1f)
            Sky.SHOWERS -> rain(clock, accent, drops = 54, weight = 1.25f)
            Sky.THUNDER -> {
                rain(clock, accent, drops = 44, weight = 1.15f)
                lightning(clock, accent)
            }
            Sky.SLEET -> {
                rain(clock, accent, drops = 22, weight = 0.8f)
                snow(clock, ink, flakes = 16)
            }
            Sky.SNOW -> snow(clock, ink, flakes = 34)
        }
    }
}

/**
 * A deterministic value in 0..1 from an index and a salt.
 *
 * The usual shader hash. It is not a good random number and does not need to be: what it has to
 * do is scatter forty drops across a width without any two landing on top of each other, and do it
 * the same way every frame.
 */
private fun scatter(index: Int, salt: Int): Float {
    val value = sin(index * 12.9898f + salt * 78.233f) * 43758.5453f
    return value - kotlin.math.floor(value)
}

/** Where a particle is on its way down, given how many laps of the clock it does. */
private fun fall(clock: Float, laps: Int, phase: Float): Float {
    val value = clock * laps + phase
    return value - kotlin.math.floor(value)
}

private fun DrawScope.rain(clock: Float, colour: Color, drops: Int, weight: Float) {
    val width = size.width
    val height = size.height
    for (index in 0 until drops) {
        val laps = 2 + (index % 3)
        val x = scatter(index, 1) * width
        val y = fall(clock, laps, scatter(index, 2)) * (height + LEAD) - LEAD
        val length = (10f + laps * 5f) * weight * density
        // Faded at the bottom of the band rather than cut off at it: rain that stops on a
        // straight line across the page is a rectangle of rain, not weather behind a page.
        val alpha = 0.26f * weight * (1f - (y / height).coerceIn(0f, 1f))
        if (alpha <= 0.01f) continue
        drawLine(
            color = colour.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + SLANT * density, y + length),
            strokeWidth = 1.6f * density,
        )
    }
}

private fun DrawScope.snow(clock: Float, colour: Color, flakes: Int) {
    val width = size.width
    val height = size.height
    for (index in 0 until flakes) {
        val laps = 1 + (index % 2)
        val phase = scatter(index, 3)
        val y = fall(clock, laps, phase) * (height + LEAD) - LEAD
        // Sideways drift on the same loop, so a flake wanders instead of dropping like a stone.
        val sway = sin((fall(clock, laps * 2, phase) * 2f * PI).toFloat()) * 9f * density
        val x = scatter(index, 4) * width + sway
        val radius = (1.6f + scatter(index, 5) * 1.8f) * density
        val alpha = 0.34f * (1f - (y / height).coerceIn(0f, 1f))
        if (alpha <= 0.01f) continue
        drawCircle(colour.copy(alpha = alpha), radius, Offset(x, y))
    }
}

private fun DrawScope.cloud(clock: Float, colour: Color) {
    val width = size.width
    val height = size.height
    for (index in 0 until 5) {
        val laps = 1
        val span = width + 2f * BANK * density
        val x = fall(clock, laps, scatter(index, 6)) * span - BANK * density
        val y = (0.18f + scatter(index, 7) * 0.55f) * height
        val radius = (34f + scatter(index, 8) * 46f) * density
        drawCircle(colour.copy(alpha = 0.05f), radius, Offset(x, y))
        drawCircle(colour.copy(alpha = 0.04f), radius * 0.72f, Offset(x + radius * 0.7f, y + radius * 0.24f))
    }
}

private fun DrawScope.fog(clock: Float, colour: Color) {
    val width = size.width
    val height = size.height
    for (index in 0 until 5) {
        val phase = scatter(index, 9)
        val drift = sin((fall(clock, 1, phase) * 2f * PI).toFloat()) * 26f * density
        val y = (0.16f + index * 0.16f) * height
        val thickness = (7f + scatter(index, 10) * 9f) * density
        drawLine(
            color = colour.copy(alpha = 0.055f),
            start = Offset(-40f * density + drift, y),
            end = Offset(width + 40f * density + drift, y),
            strokeWidth = thickness,
        )
    }
}

/**
 * The sun, breathing.
 *
 * Three rings around a point up and to the right, each at a different phase, so the glow swells
 * and settles rather than pulsing on a single beat like a warning light.
 */
private fun DrawScope.sun(clock: Float, colour: Color) {
    val centre = Offset(size.width * 0.82f, size.height * 0.16f)
    for (ring in 0 until 3) {
        val phase = ring * 0.33f
        val swell = 1f + 0.07f * sin((fall(clock, 1, phase) * 2f * PI).toFloat())
        val radius = (54f + ring * 40f) * density * swell
        drawCircle(colour.copy(alpha = 0.075f - ring * 0.02f), radius, centre)
    }
    // A ring of light, drawn rather than filled, so the glow has an edge to catch the eye.
    val halo = 1f + 0.05f * sin((fall(clock, 2, 0.5f) * 2f * PI).toFloat())
    drawCircle(
        color = colour.copy(alpha = 0.10f),
        radius = 46f * density * halo,
        center = centre,
        style = Stroke(width = 2f * density),
    )
}

private fun DrawScope.stars(clock: Float, colour: Color) {
    for (index in 0 until 30) {
        val x = scatter(index, 11) * size.width
        val y = scatter(index, 12) * size.height * 0.8f
        val laps = 1 + (index % 4)
        val twinkle = 0.5f + 0.5f * sin((fall(clock, laps, scatter(index, 13)) * 2f * PI).toFloat())
        val alpha = 0.10f + 0.34f * twinkle
        val radius = (0.9f + scatter(index, 14) * 1.4f) * density
        drawCircle(colour.copy(alpha = alpha), radius, Offset(x, y))
    }
}

/**
 * Two flashes a period, brief and off-centre.
 *
 * Brief is the whole point: a storm that lights up on a metronome is a disco. The pulse is sharp
 * on and slow off, which is what lightning does and what a linear fade does not.
 */
private fun DrawScope.lightning(clock: Float, colour: Color) {
    for (strike in 0 until 2) {
        val at = 0.22f + strike * 0.46f
        val since = clock - at
        if (since < 0f || since > 0.10f) continue
        val decay = 1f - (since / 0.10f)
        drawRect(colour.copy(alpha = 0.14f * decay * decay), size = Size(size.width, size.height))
    }
}

/**
 * Where the sky is frozen when animation is switched off.
 *
 * Not zero: at zero every falling thing is at the top of its lap and the field is a straight line
 * across the page. A third of the way in, they are scattered.
 */
private const val STILL = 0.34f

/** How long one lap of everything takes. Long enough that nothing reads as a loop. */
private const val PERIOD_MILLIS = 9_000

/** How far above the band a falling particle starts, so none of them appear out of nothing. */
private const val LEAD = 60f

/** How far a drop moves sideways over its own length. */
private const val SLANT = 2.4f

/** How far off each edge a cloud is allowed to sit while it drifts in. */
private const val BANK = 90f
