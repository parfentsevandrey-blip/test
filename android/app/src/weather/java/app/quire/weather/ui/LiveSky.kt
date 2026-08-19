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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import app.quire.weather.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The weather, moving, and moving the way this weather is moving.
 *
 * A weather app that draws a raincloud and then sits perfectly still is a diagram of the weather.
 * This is the sky itself, and it is not a decoration bolted on beside the numbers: the rain leans
 * the way the wind is actually blowing and as hard as it is actually blowing, so the picture is
 * one more reading rather than one more ornament.
 *
 * Everything has depth. A particle's distance decides its size, its speed, its brightness and how
 * far the wind pushes it, all from one number — which is the whole trick, because a field where
 * every drop is the same drop reads as a screensaver and a field with a near edge and a far one
 * reads as sky.
 *
 * Every position is a pure function of one looping clock and the particle's own index. Nothing is
 * stored between frames: a field of fifty drops is fifty sines, not fifty objects being stepped
 * and collected. That also makes it deterministic — the same second of the same sky draws the same
 * picture, which is what lets a render test look at it. The only thing allocated in a frame is the
 * handful of radial gradients the clouds are made of, because a gradient bakes its centre and the
 * centre is what is moving.
 *
 * The loop is seamless because every speed is a whole number of laps per period. A drop moving at
 * two and a third laps arrives back a third of the way down and the whole field visibly jumps; at
 * two laps exactly, nobody can find the seam.
 */
@Composable
fun LiveSky(
    sky: Sky,
    day: Boolean,
    modifier: Modifier = Modifier,
    windKmh: Double = 0.0,
    windFrom: Int = -1,
    // Where the sun or the moon actually is, 0..1 across its arc — null keeps the old fixed
    // corner, which is also what a forecast without sunrise times degrades to.
    daylight: Float? = null,
    night: Float? = null,
    /** The moon, 0 new → 0.5 full → 1 new again. Only read when [night] is known. */
    moonPhase: Float = 0.5f,
    // How hard it is actually falling this quarter-hour, from the minute-cast. Negative means
    // "not known", and the sky falls back to what its category usually looks like.
    rainMm: Double = -1.0,
    snowCm: Double = -1.0,
) {
    val scheme = MaterialTheme.colorScheme

    // Somebody who has turned animation off in the system settings has said what they want, and
    // an endless one is exactly the kind they meant. Battery saver is the same sentence said by
    // the battery. Both get the same sky, standing still.
    val context = LocalContext.current
    val moving = remember(context) {
        val animated = android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
        val saving = context.getSystemService(android.os.PowerManager::class.java)
            ?.isPowerSaveMode == true
        animated && !saving
    }

    val transition = rememberInfiniteTransition(label = "sky")
    val running by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PERIOD_MILLIS, easing = LinearEasing)),
        label = "clock",
    )
    val clock = if (moving) running else STILL

    // Which way the weather leans, and how hard. A direction is where the wind comes *from*, so
    // what it pushes things along by is the opposite: a westerly blows east. With no direction
    // known, everything falls with the small lean that makes rain look like rain.
    val strength = (windKmh / WIND_FULL).coerceIn(0.0, 1.0).toFloat()
    val lean = if (windFrom < 0) {
        0.35f
    } else {
        -sin(windFrom * PI.toFloat() / 180f) * (0.3f + 0.7f * strength) + 0.1f
    }

    val ink = scheme.onSurface
    val accent = scheme.primary

    // The same weather code can be a sprinkle or a sheet. When the minute-cast knows which,
    // the field says which: the count and the weight of the drops ride the actual millimetres,
    // and the category's usual look stands in only where nothing better is known.
    val pour = if (rainMm >= 0.0) (rainMm / HARD_RAIN_MM).coerceIn(0.0, 1.0).toFloat() else -1f
    val flurry = if (snowCm >= 0.0) (snowCm / HARD_SNOW_CM).coerceIn(0.0, 1.0).toFloat() else -1f
    fun dropsOf(usual: Int) = if (pour < 0f) usual else (10 + 44 * pour).toInt()
    fun weightOf(usual: Float) = if (pour < 0f) usual else 0.55f + 0.65f * pour
    fun flakesOf(usual: Int) = if (flurry < 0f) usual else (8 + 26 * flurry).toInt()

    Canvas(modifier) {
        when (sky) {
            Sky.CLEAR, Sky.MOSTLY_CLEAR ->
                if (day) sun(clock, accent, daylight) else stars(clock, ink, accent, night, moonPhase)
            Sky.PARTLY_CLOUDY, Sky.OVERCAST -> {
                if (day) sun(clock, accent, daylight) else stars(clock, ink, accent, night, moonPhase)
                cloud(clock, ink, lean)
            }
            Sky.FOG -> fog(clock, ink)
            // No cloud under the rain. A bank of cloud is a large soft shape and the rain is a
            // field of small hard ones; together over a page of type they stopped being weather
            // behind it and started being a picture in front of it.
            Sky.DRIZZLE -> rain(clock, accent, drops = dropsOf(18), weight = weightOf(0.6f), lean = lean)
            Sky.RAIN -> rain(clock, accent, drops = dropsOf(26), weight = weightOf(0.9f), lean = lean)
            Sky.SHOWERS -> rain(clock, accent, drops = dropsOf(34), weight = weightOf(1.1f), lean = lean)
            Sky.THUNDER -> {
                rain(clock, accent, drops = dropsOf(28), weight = weightOf(1f), lean = lean)
                lightning(clock, accent)
            }
            Sky.SLEET -> {
                rain(clock, accent, drops = dropsOf(14), weight = weightOf(0.7f), lean = lean)
                snow(clock, ink, flakes = flakesOf(10), lean = lean)
            }
            Sky.SNOW -> snow(clock, ink, flakes = flakesOf(22), lean = lean)
        }
    }
}

/**
 * A deterministic value in 0..1 from an index and a salt.
 *
 * The usual shader hash. It is not a good random number and does not need to be: what it has to
 * do is scatter fifty drops across a width without any two landing on top of each other, and do it
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

/** One turn of a sine, from a value already wrapped into 0..1. */
private fun wave(turn: Float): Float = sin(turn * 2f * PI.toFloat())

/**
 * Rain, in three depths.
 *
 * Near drops are long, bright, quick and pushed hardest by the wind; far ones are short, faint and
 * slow. One number does all four, which is what keeps a downpour from looking like a hatching
 * pattern. Every drop also carries a little of its own speed, so no two columns beat together.
 */
private fun DrawScope.rain(clock: Float, colour: Color, drops: Int, weight: Float, lean: Float) {
    val height = size.height
    for (index in 0 until drops) {
        val depth = scatter(index, 20)
        val laps = 2 + (index % 3)
        val near = 0.45f + 0.55f * depth
        val y = fall(clock, laps, scatter(index, 2)) * (height + LEAD) - LEAD
        val length = (9f + laps * 4.5f) * weight * near * density
        val x = scatter(index, 1) * (size.width + 2f * SPILL * density) - SPILL * density
        // Faded at the bottom of the band rather than cut off at it: rain that stops on a
        // straight line across the page is a rectangle of rain, not weather behind a page.
        val alpha = 0.17f * weight * near * (1f - (y / height).coerceIn(0f, 1f))
        if (alpha <= 0.01f) continue
        drawLine(
            color = colour.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + lean * length * SLANT, y + length),
            strokeWidth = (0.9f + 1.1f * near) * density,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Snow, as flakes rather than as dots.
 *
 * The near ones get three crossed arms and a slow turn, which is the difference between snow and a
 * field of full stops; the far ones stay dots, because at four pixels an arm is a smudge and the
 * cost of drawing it is the same as the cost of drawing a flake you can see.
 */
private fun DrawScope.snow(clock: Float, colour: Color, flakes: Int, lean: Float) {
    val height = size.height
    for (index in 0 until flakes) {
        val depth = scatter(index, 21)
        val near = 0.4f + 0.6f * depth
        val laps = 1 + (index % 2)
        val phase = scatter(index, 3)
        val y = fall(clock, laps, phase) * (height + LEAD) - LEAD
        // Sideways wander on its own loop, plus whatever the wind is doing to everything.
        val sway = wave(fall(clock, laps * 2, phase)) * 10f * near * density
        val push = lean * (y + LEAD) * 0.18f
        val x = scatter(index, 4) * size.width + sway + push
        val alpha = 0.24f * near * (1f - (y / height).coerceIn(0f, 1f))
        if (alpha <= 0.01f) continue
        val ink = colour.copy(alpha = alpha)
        val radius = (1.3f + 2.6f * depth) * density

        if (depth < 0.45f) {
            drawCircle(ink, radius * 0.8f, Offset(x, y))
            continue
        }
        val turn = fall(clock, laps, phase + 0.25f) * 2f * PI.toFloat() * (if (index % 2 == 0) 1f else -1f)
        for (arm in 0 until 3) {
            val angle = turn + arm * PI.toFloat() / 3f
            val dx = cos(angle) * radius
            val dy = sin(angle) * radius
            drawLine(
                color = ink,
                start = Offset(x - dx, y - dy),
                end = Offset(x + dx, y + dy),
                strokeWidth = 1.1f * density,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Cloud, in layers.
 *
 * Two banks at two depths crossing at two speeds, each bobbing gently on its own phase. One bank
 * at one speed is a texture sliding past; two at different speeds is weather with a distance in
 * it, and the parallax does all of that for the price of a second loop.
 */
private fun DrawScope.cloud(clock: Float, colour: Color, lean: Float) {
    val height = size.height
    val direction = if (lean < 0f) -1f else 1f
    for (index in 0 until 4) {
        val depth = scatter(index, 22)
        val laps = 1 + (index % 2)
        val span = size.width + 2f * BANK * density
        val travel = fall(clock, laps, scatter(index, 6))
        val x = (if (direction > 0f) travel else 1f - travel) * span - BANK * density
        val bob = wave(fall(clock, laps, scatter(index, 23))) * 6f * density
        val y = (0.10f + scatter(index, 7) * 0.60f) * height + bob
        val radius = (30f + depth * 62f) * density
        val alpha = 0.028f + 0.026f * depth
        // A radial fade rather than a flat disc. A cloud with a crisp edge is a circle, and three
        // circles with crisp edges are three circles. This is the one thing here that allocates
        // per frame — a gradient bakes its centre, and the centre is what is moving — and seven
        // small brushes a frame is nothing against what it buys.
        blob(Offset(x, y), radius, colour, alpha)
        blob(Offset(x + radius * 0.70f, y + radius * 0.20f), radius * 0.70f, colour, alpha * 0.85f)
        blob(Offset(x - radius * 0.64f, y + radius * 0.16f), radius * 0.58f, colour, alpha * 0.75f)
    }
}

/** One soft round of cloud: solid at the middle, gone at the edge. */
private fun DrawScope.blob(centre: Offset, radius: Float, colour: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colour.copy(alpha = alpha), colour.copy(alpha = 0f)),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * Fog, as banks that swell rather than as rules that slide.
 *
 * A straight line drifting sideways is a rule drifting sideways. Each band is drawn as a run of
 * short segments whose thickness rides a sine along its own length, so the bank thins and thickens
 * as it passes and reads as something with no edges.
 */
private fun DrawScope.fog(clock: Float, colour: Color) {
    val height = size.height
    val steps = 22
    for (index in 0 until 5) {
        val phase = scatter(index, 9)
        val drift = wave(fall(clock, 1, phase)) * 30f * density
        val base = (0.12f + index * 0.17f) * height
        val thick = (6f + scatter(index, 10) * 10f) * density
        for (step in 0 until steps) {
            val along = step / (steps - 1f)
            val swell = 0.45f + 0.55f * (0.5f + 0.5f * wave(along * 1.5f + fall(clock, 1, phase)))
            val x0 = -30f * density + along * (size.width + 60f * density) + drift
            val x1 = x0 + (size.width + 60f * density) / steps + 1.5f
            val y = base + wave(along * 0.8f + phase) * 5f * density
            // Butt caps, and each segment overlapping the next. A round cap on a segment as thick
            // as this one is a bead, and a row of beads is a row of beads however faint it is.
            drawLine(
                color = colour.copy(alpha = 0.026f * swell + 0.008f),
                start = Offset(x0, y),
                end = Offset(x1, y),
                strokeWidth = thick * swell,
                cap = StrokeCap.Butt,
            )
        }
    }
}

/**
 * The sun, breathing, where the sun actually is.
 *
 * Three rings at three phases, so it swells and settles instead of pulsing on one beat like a
 * warning light. It had a turning fan of rays for a version; over a page of type that is a
 * pinwheel, and a pinwheel is a thing you look at rather than a thing you read past.
 *
 * Its place comes from the day itself: how far between sunrise and sunset the clock is, run
 * along a shallow arc — low in the east over morning coffee, overhead at noon, low in the west
 * by dinner. The glance that reads the temperature reads the hour for free. Without the times
 * it keeps the old fixed corner rather than pretending to know.
 */
private fun DrawScope.sun(clock: Float, colour: Color, daylight: Float? = null) {
    val centre = if (daylight == null) {
        Offset(size.width * 0.80f, size.height * 0.15f)
    } else {
        arcSpot(daylight)
    }
    for (ring in 0 until 3) {
        val swell = 1f + 0.06f * wave(fall(clock, 1, ring * 0.33f))
        drawCircle(
            colour.copy(alpha = 0.045f - ring * 0.012f),
            (50f + ring * 38f) * density * swell,
            centre,
        )
    }
    val halo = 1f + 0.04f * wave(fall(clock, 2, 0.5f))
    drawCircle(
        color = colour.copy(alpha = 0.07f),
        radius = 44f * density * halo,
        center = centre,
        style = Stroke(width = 1.6f * density),
    )
}

/** Where along the shallow arc across the band a body at [fraction] of its journey sits. */
private fun DrawScope.arcSpot(fraction: Float): Offset = Offset(
    size.width * (0.12f + 0.76f * fraction),
    size.height * (0.68f - 0.53f * sin(PI.toFloat() * fraction)),
)

/**
 * The moon, wearing its actual phase, where the night actually is.
 *
 * The shape is the difference of two circles — the disc and its own shadow, slid aside by how
 * lit the moon is tonight: on top of it at new, clear of it at full, half-way off at a quarter.
 * A new moon keeps a thin ring rather than vanishing, because a night sky with a hole where the
 * moon should be reads as a mistake, and a ring is what a new moon looks like to anyone who
 * looks up. The arithmetic for the phase is a calendar fold in [SkyMoment]; this only draws it.
 */
private fun DrawScope.moon(night: Float, phase: Float, colour: Color) {
    val centre = arcSpot(night)
    val radius = 9f * density
    blob(centre, radius * 3.4f, colour, 0.05f)

    // 0 at new, 1 at full, by the cosine of the phase angle.
    val lit = 0.5f * (1f - cos(phase * 2f * PI.toFloat()))
    if (lit < 0.06f) {
        drawCircle(
            color = colour.copy(alpha = 0.14f),
            radius = radius,
            center = centre,
            style = Stroke(width = 1.2f * density),
        )
        return
    }
    val disc = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(centre - Offset(radius, radius), Size(radius * 2f, radius * 2f)))
    }
    // Waxing is lit from the right, waning from the left; the shadow slides the other way.
    val side = if (phase < 0.5f) 1f else -1f
    val shadowCentre = Offset(centre.x - side * 2.3f * radius * lit, centre.y)
    val shadowRadius = radius * 1.04f
    val shadow = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                shadowCentre - Offset(shadowRadius, shadowRadius),
                Size(shadowRadius * 2f, shadowRadius * 2f),
            ),
        )
    }
    drawPath(
        path = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, shadow),
        color = colour.copy(alpha = 0.32f),
    )
}

/**
 * Stars, and once a lap something crossing them.
 *
 * The twinkle is two sines of different rates multiplied rather than one, so the field never
 * settles into a visible rhythm. A handful of the brightest carry a small cross of light, which is
 * what a bright star looks like through anything, and one shooting star a lap gives the sky
 * something to have missed. When the night's own clock is known, the moon rides it.
 */
private fun DrawScope.stars(
    clock: Float,
    colour: Color,
    accent: Color,
    night: Float? = null,
    moonPhase: Float = 0.5f,
) {
    if (night != null) moon(night, moonPhase, colour)
    for (index in 0 until 30) {
        val x = scatter(index, 11) * size.width
        val y = scatter(index, 12) * size.height * 0.82f
        val slow = 0.5f + 0.5f * wave(fall(clock, 1 + (index % 3), scatter(index, 13)))
        val quick = 0.5f + 0.5f * wave(fall(clock, 3 + (index % 4), scatter(index, 25)))
        val bright = scatter(index, 26)
        val alpha = (0.06f + 0.26f * slow * (0.4f + 0.6f * quick)) * (0.5f + 0.8f * bright)
        val radius = (0.7f + bright * 1.6f) * density
        drawCircle(colour.copy(alpha = alpha), radius, Offset(x, y))
        if (bright > 0.92f) {
            val flare = radius * 4.5f
            drawLine(
                colour.copy(alpha = alpha * 0.45f),
                Offset(x - flare, y), Offset(x + flare, y),
                strokeWidth = 0.9f * density, cap = StrokeCap.Round,
            )
            drawLine(
                colour.copy(alpha = alpha * 0.45f),
                Offset(x, y - flare), Offset(x, y + flare),
                strokeWidth = 0.9f * density, cap = StrokeCap.Round,
            )
        }
    }

    // One crossing, over a tenth of the lap, from up-right to down-left.
    val since = clock - SHOOT_AT
    if (since in 0f..SHOOT_FOR) {
        val along = since / SHOOT_FOR
        val head = Offset(
            size.width * (0.95f - along * 0.75f),
            size.height * (0.08f + along * 0.42f),
        )
        val tail = Offset(head.x + 70f * density, head.y - 39f * density)
        val fade = (1f - along) * (if (along < 0.15f) along / 0.15f else 1f)
        drawLine(
            accent.copy(alpha = 0.30f * fade),
            head, tail,
            strokeWidth = 1.8f * density,
            cap = StrokeCap.Round,
        )
        drawCircle(accent.copy(alpha = 0.40f * fade), 2f * density, head)
    }
}

/**
 * A storm: two strikes a lap, each a bolt and a wash.
 *
 * The wash alone was a screen going pale on a metronome. What makes it read as lightning is the
 * bolt — a jagged line down through the band, different every strike because its kinks come from
 * the strike's own index — and the shape of the fade: sharp on, slow off, which is what lightning
 * does and what a linear ramp does not.
 */
private fun DrawScope.lightning(clock: Float, colour: Color) {
    for (strike in 0 until 2) {
        val at = 0.22f + strike * 0.46f
        val since = clock - at
        if (since < 0f || since > FLASH_FOR) continue
        val decay = 1f - (since / FLASH_FOR)
        val glow = decay * decay
        drawRect(colour.copy(alpha = 0.07f * glow), size = Size(size.width, size.height))

        val bolt = Path()
        val top = -6f * density
        val foot = size.height * 0.72f
        val kinks = 6
        var x = size.width * (0.24f + 0.5f * scatter(strike, 27))
        bolt.moveTo(x, top)
        for (kink in 1..kinks) {
            val y = top + (foot - top) * kink / kinks
            x += (scatter(strike * 8 + kink, 28) - 0.5f) * 46f * density
            bolt.lineTo(x, y)
        }
        drawPath(
            path = bolt,
            color = colour.copy(alpha = 0.45f * glow),
            style = Stroke(width = 2.4f * density, cap = StrokeCap.Round),
        )
        drawPath(
            path = bolt,
            color = colour.copy(alpha = 0.12f * glow),
            style = Stroke(width = 7f * density, cap = StrokeCap.Round),
        )
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

/** How far past each side a drop may start, so a leaning field has no bare edge. */
private const val SPILL = 70f

/** How much of its own length a drop is allowed to travel sideways at full wind. */
private const val SLANT = 0.75f

/** The wind, in km/h, at which the lean is as far over as it goes. */
private const val WIND_FULL = 55.0

/** A quarter-hour of rain, in mm, past which the field is as thick as it gets (~10 mm/h). */
private const val HARD_RAIN_MM = 2.5

/** A quarter-hour of snow, in cm, past which the flurry is as thick as it gets. */
private const val HARD_SNOW_CM = 1.0

/** How far off each edge a cloud is allowed to sit while it drifts in. */
private const val BANK = 90f

/** When in the lap a star falls, and for how much of it. */
private const val SHOOT_AT = 0.55f
private const val SHOOT_FOR = 0.11f

/** How much of a lap a lightning flash lasts. */
private const val FLASH_FOR = 0.11f
