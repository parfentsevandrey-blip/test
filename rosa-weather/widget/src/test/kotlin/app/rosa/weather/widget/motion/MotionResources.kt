package app.rosa.weather.widget.motion

import java.util.Locale
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Writes the widget's live weather. A launcher never runs our code between updates, but it does
 * play AnimatedVectorDrawables — on its own render thread, the way every system spinner turns, and
 * only while the widget is on screen. So rain, snow and lightning are built as vector tiles of
 * [TILE_W] × [TILE_H] dp that the widget lays over its picture:
 *
 *  - Rain streaks in three depths, from a pattern that wraps around every edge of a tile, so tiles
 *    side by side join into one field of rain without seams. The field drifts sideways and falls on
 *    two independent loops, which lets it fall at any slant.
 *  - Drops landing on the glass: an impact that flattens and rebounds, a splash of droplets, then a
 *    bead that sits, swells a little and fades.
 *  - Drops sliding down: a bead fills, tips over and runs in jerks, stretched while it moves,
 *    leaving a wet trail and a few droplets behind.
 *  - In a storm, lightning: every tile flashes at the same instants; tiles along the top carry the
 *    bolts.
 *  - Snow in three depths, drifting with the wind and swaying.
 *
 * Every timeline is a loop that ends where it began. Drops come in four variants with their own
 * places and timings, so neighbouring tiles never repeat each other; the streaks are the same in
 * all four, so they join. Only what the launcher animates on its render thread is used: float
 * group and path properties, keyframes, a linear clock, infinite repeats.
 *
 * Regenerate with `./gradlew :widget:testDebugUnitTest --tests '*MotionResourcesTest*' -Prosa.motion`.
 */
internal object MotionResources {
    const val TILE_W = 180f
    const val TILE_H = 90f
    const val VARIANTS = 4

    /** Dark companion of every light stroke, so the weather reads on light glass as on dark. */
    private const val INK = "#0B1426"
    private const val WHITE = "#FFFFFF"
    private const val STORM_PERIOD = 12f

    class Streaks(val count: Int, val length: ClosedFloatingPointRange<Float>, val width: Float, val alpha: Float, val shadow: Float, val pace: Float)

    enum class Rain(
        val id: String,
        val seed: Int,
        val slant: Float,
        val speed: Float,
        val streaks: List<Streaks>,
        val beads: Int,
        val sliders: Int,
        val storm: Boolean = false,
    ) {
        RainLight(
            "rain_light", 11, slant = 0.12f, speed = 230f,
            streaks = listOf(
                Streaks(12, 4f..7f, 0.42f, 0.26f, 0.08f, 0.6f),
                Streaks(5, 8f..12f, 0.6f, 0.34f, 0.11f, 1f),
                Streaks(2, 13f..18f, 0.85f, 0.4f, 0.13f, 1.55f),
            ),
            beads = 4, sliders = 1,
        ),
        RainHeavy(
            "rain_heavy", 23, slant = 0.2f, speed = 270f,
            streaks = listOf(
                Streaks(26, 5f..8f, 0.45f, 0.28f, 0.09f, 0.6f),
                Streaks(12, 9f..14f, 0.65f, 0.36f, 0.12f, 1f),
                Streaks(4, 15f..21f, 0.95f, 0.44f, 0.14f, 1.55f),
            ),
            beads = 5, sliders = 2,
        ),
        Storm(
            "storm", 37, slant = 0.3f, speed = 300f,
            streaks = listOf(
                Streaks(30, 5f..9f, 0.45f, 0.3f, 0.1f, 0.6f),
                Streaks(14, 10f..15f, 0.68f, 0.38f, 0.12f, 1f),
                Streaks(5, 16f..23f, 1f, 0.46f, 0.15f, 1.55f),
            ),
            beads = 5, sliders = 2, storm = true,
        ),
    }

    class Flakes(
        val count: Int,
        val radius: ClosedFloatingPointRange<Float>,
        val alpha: Float,
        val shadow: Float,
        /** dp per second down, and sideways with the wind. */
        val fall: Float,
        val drift: Float,
        val sway: Float,
        val swayPeriod: Float,
        val halo: Boolean = false,
    )

    enum class Snow(val id: String, val seed: Int, val flakes: List<Flakes>) {
        SnowLight(
            "snow_light", 51,
            listOf(
                Flakes(14, 0.5f..0.85f, 0.72f, 0.13f, fall = 11f, drift = 2.2f, sway = 1.5f, swayPeriod = 4.7f),
                Flakes(6, 1f..1.5f, 0.82f, 0.17f, fall = 17f, drift = 3.3f, sway = 2.5f, swayPeriod = 3.9f),
                Flakes(3, 1.8f..2.7f, 0.88f, 0.19f, fall = 26f, drift = 5f, sway = 3.5f, swayPeriod = 3.1f, halo = true),
            ),
        ),
        SnowHeavy(
            "snow_heavy", 67,
            listOf(
                Flakes(30, 0.5f..0.9f, 0.74f, 0.13f, fall = 12f, drift = 2.6f, sway = 1.5f, swayPeriod = 4.5f),
                Flakes(13, 1f..1.6f, 0.84f, 0.17f, fall = 18f, drift = 3.8f, sway = 2.5f, swayPeriod = 3.7f),
                Flakes(6, 1.8f..2.9f, 0.9f, 0.19f, fall = 27f, drift = 5.6f, sway = 3.5f, swayPeriod = 2.9f, halo = true),
            ),
        ),
    }

    fun tileName(kind: Rain, variant: Int) = "motion_${kind.id}_${'a' + variant}"

    fun tileName(kind: Snow) = "motion_${kind.id}"

    /** Every generated file, by path under `res/`. */
    fun files(): Map<String, String> = buildMap {
        put("color/motion_drop_body.xml", gradient(listOf(0f to "#59101828", 0.5f to "#14FFFFFF", 1f to "#80FFFFFF")))
        put("color/motion_drop_rim.xml", gradient(listOf(0f to "#8C050A14", 0.55f to "#52050A14", 1f to "#73FFFFFF")))
        for (kind in Rain.entries) {
            repeat(VARIANTS) { variant ->
                val name = tileName(kind, variant)
                put("drawable/$name.xml", rainTile(kind, variant))
                put("layout/$name.xml", tileLayout(name))
            }
        }
        for (kind in Snow.entries) {
            val name = tileName(kind)
            put("drawable/$name.xml", snowTile(kind))
            put("layout/$name.xml", tileLayout(name))
        }
    }

    // region Rain

    private fun rainTile(kind: Rain, variant: Int): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 7)
        // Far to near: lightning deep in the sky, rain falling past, water on the glass, the flash
        // lighting all of it.
        if (kind.storm && variant < 2) bolt(v, rnd, variant)
        streaks(v, kind)
        val sliders = mutableListOf<Slider>()
        var attempts = 0
        while (sliders.size < kind.sliders && attempts++ < 50) {
            planSlider(rnd, sliders)?.let { sliders += it }
        }
        sliders.forEachIndexed { i, s -> s.emitTrail(v, "s$i", rnd) }
        val beads = mutableListOf<Triple<Float, Float, Float>>()
        attempts = 0
        while (beads.size < kind.beads && attempts++ < 200) {
            val u = rnd.nextFloat()
            val r = 1.2f + u * u * u * 3f
            val margin = 2.8f * r + 1f
            val x = rnd.range(margin, TILE_W - margin)
            val y = rnd.range(1f + 3.6f * r, TILE_H - 1f - 1.6f * r)
            val clear = beads.none { (bx, by, br) -> hypot(bx - x, (by - br) - (y - r)) < 2 * (br + r) + 1.5f } &&
                sliders.none { it.blocks(x, y, r) }
            if (clear) beads += Triple(x, y, r)
        }
        beads.forEachIndexed { i, (x, y, r) -> bead(v, "b$i", x, y, r, rnd) }
        sliders.forEachIndexed { i, s -> s.emitDrop(v, "s$i") }
        if (kind.storm) flash(v)
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}, variant ${'a' + variant}")
    }

    /**
     * Three depths of streaks from one pattern that wraps around the tile's edges. Every copy of a
     * streak that can show while the pattern slides by one tile across and one tile down is drawn,
     * so the view through the tile is always the endless pattern.
     */
    private fun streaks(v: Vector, kind: Rain) {
        val rnd = Random(kind.seed)
        kind.streaks.forEachIndexed { layer, s ->
            val core = StringBuilder()
            val shade = StringBuilder()
            repeat(s.count) {
                val x = rnd.nextFloat() * TILE_W
                val y = rnd.nextFloat() * TILE_H
                val length = rnd.range(s.length)
                val width = s.width * rnd.range(0.8f, 1.2f)
                for (i in -1..1) for (j in -1..1) {
                    val cx = x + i * TILE_W
                    val cy = y + j * TILE_H
                    if (cx + kind.slant * length < -TILE_W - 1 || cx > TILE_W + 1 || cy + length < -TILE_H - 1 || cy > TILE_H + 1) continue
                    core.append(needle(cx, cy, length, width, kind.slant))
                    shade.append(needle(cx + 0.4f, cy + 0.2f, length, width * 1.15f, kind.slant))
                }
            }
            val name = "rain$layer"
            v.body += group(name).add(path(shade.toString(), fill = INK, fillAlpha = s.shadow)).add(path(core.toString(), fill = WHITE, fillAlpha = s.alpha))
            val speed = kind.speed * s.pace
            v.animate(name, Anim(ms(TILE_W / (speed * kind.slant)), listOf(Ramp("translateX", 0f, TILE_W))))
            v.animate(name, Anim(ms(TILE_H / speed), listOf(Ramp("translateY", 0f, TILE_H))))
        }
    }

    /** A falling drop smeared by motion: a needle from where it was to a rounded head. */
    private fun needle(x: Float, y: Float, length: Float, width: Float, slant: Float): String {
        val n = sqrt(1 + slant * slant)
        val dx = slant / n
        val dy = 1 / n
        val hx = x + slant * length
        val hy = y + length
        val half = width / 2
        return "M${f(x)},${f(y)}L${f(hx - dy * half)},${f(hy + dx * half)}L${f(hx + dx * width * 0.7f)},${f(hy + dy * width * 0.7f)}" +
            "L${f(hx + dy * half)},${f(hy - dx * half)}Z"
    }

    /** A bead of rain hitting the glass: impact, splash, a while sitting there, then gone. */
    private fun bead(v: Vector, name: String, x: Float, y: Float, r: Float, rnd: Random) {
        val periodMs = ms(rnd.range(3.2f, 6.2f))
        val period = periodMs / 1000f
        val stay = rnd.range(1.15f, min(3.6f, period - 1.2f))
        val start = rnd.range(0f, period)
        val sx = loop(period, 0f, start to listOf(0f to 0f, 0.045f to 1.34f * r, 0.11f to 0.9f * r, 0.2f to 1.02f * r, 0.3f to r, stay to 1.07f * r, stay + 0.8f to 0.78f * r, stay + 0.82f to 0f))
        val sy = loop(period, 0f, start to listOf(0f to 0f, 0.045f to 0.78f * r, 0.11f to 1.12f * r, 0.2f to 0.98f * r, 0.3f to r, stay to 1.07f * r, stay + 0.8f to 0.78f * r, stay + 0.82f to 0f))
        val alpha = loop(period, 1f, start to listOf(0f to 1f, stay to 1f, stay + 0.8f to 0f, stay + 0.84f to 1f))
        val splash = loop(period, 0f, start to listOf(0f to 0f, 0.035f to 0f, 0.05f to 1f, 0.45f to 0.5f, 1.1f to 0f))

        val drops = StringBuilder()
        repeat(3) {
            val angle = rnd.range(0f, 2 * PI.toFloat())
            val d = rnd.range(1.9f, 2.6f)
            drops.append(circle(kotlin.math.cos(angle) * d, -1f + sin(angle) * d, rnd.range(0.16f, 0.26f)))
        }
        v.body += group(name, x = x, y = y, scaleX = sx.first().value, scaleY = sy.first().value)
            .add(path(BEAD, name = "${name}_body", fill = "@color/motion_drop_body", fillAlpha = alpha.first().value, stroke = "@color/motion_drop_rim", strokeWidth = 0.13f, strokeAlpha = alpha.first().value))
            .add(path(beadLight, name = "${name}_light", fill = WHITE, fillAlpha = 0.5f * alpha.first().value))
            .add(path(ellipse(-0.36f, -1.42f, 0.28f, 0.2f), name = "${name}_glint", fill = "#F5FFFFFF", fillAlpha = alpha.first().value))
            .add(path(drops.toString(), name = "${name}_splash", fill = "#C7FFFFFF", fillAlpha = splash.first().value))
        v.animate(name, Anim(periodMs, listOf(Keys("scaleX", sx), Keys("scaleY", sy))))
        v.animate("${name}_body", Anim(periodMs, listOf(Keys("fillAlpha", alpha), Keys("strokeAlpha", alpha))))
        v.animate("${name}_light", Anim(periodMs, listOf(Keys("fillAlpha", alpha.scaled(0.5f)))))
        v.animate("${name}_glint", Anim(periodMs, listOf(Keys("fillAlpha", alpha))))
        v.animate("${name}_splash", Anim(periodMs, listOf(Keys("fillAlpha", splash))))
    }

    /** Where a sliding drop will run, if it doesn't cross another's way. */
    private fun planSlider(rnd: Random, others: List<Slider>): Slider? {
        val r = rnd.range(2.6f, 3.8f)
        val x0 = rnd.range(14f, TILE_W - 14f)
        if (others.any { kotlin.math.abs(it.points.first().x - x0) < 22f }) return null
        val y0 = rnd.range(4f + 3.2f * r, 38f)
        val drop = rnd.range(22f, min(50f, TILE_H - 5f - y0))
        val points = mutableListOf(P(x0, y0))
        var x = x0
        var y = y0
        var drift = rnd.range(-0.12f, 0.12f)
        val end = y0 + drop
        while (y < end - 0.01f) {
            var step = rnd.range(3.2f, 5.5f)
            // No sliver of a last hop: finish with a proper one.
            if (end - (y + step) < 2f) step = end - y
            y += step
            drift = (drift + rnd.range(-0.1f, 0.1f)).coerceIn(-0.25f, 0.25f)
            x = (x + drift * step + rnd.range(-0.35f, 0.35f)).coerceIn(8f, TILE_W - 8f)
            points += P(x, y)
        }
        return Slider(r, points, rnd)
    }

    /**
     * A drop that runs down the glass. It lands and sits filling up, tips over and runs in a few
     * jerks — longer and thinner while it moves — then rests, and fades; its trail, drawn out
     * behind it, dries after it.
     */
    private class Slider(val r: Float, val points: List<P>, rnd: Random) {
        private val arc = FloatArray(points.size).also { a ->
            for (i in 1 until points.size) a[i] = a[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }

        /** The trail starts inside where the bead sat, so it seems to come out of its top. */
        private val lead = 0.8f * r
        private val trailLength = lead + arc.last()
        private val tx = mutableListOf(0f to points.first().x)
        private val ty = mutableListOf(0f to points.first().y)
        private val sx = mutableListOf(0f to 0f, 0.045f to 1.32f * r, 0.11f to 0.92f * r, 0.2f to 1.02f * r, 0.3f to r)
        private val sy = mutableListOf(0f to 0f, 0.045f to 0.8f * r, 0.11f to 1.12f * r, 0.2f to 0.98f * r, 0.3f to r)
        private val alpha = mutableListOf(0f to 1f)
        private val trim = mutableListOf(0f to 0f)
        private val trail = mutableListOf(0f to 0f)
        private val residues = mutableListOf<Pair<P, List<Pair<Float, Float>>>>()
        private val periodMs: Long
        private val start: Float

        init {
            val segments = points.size - 1
            val runs = (2 + rnd.nextInt(3)).coerceAtMost(segments)
            val cuts = (1 until segments).shuffled(rnd).take(runs - 1).sorted()
            val bounds = listOf(0) + cuts + listOf(segments)
            val passes = FloatArray(points.size)
            var t = rnd.range(0.7f, 1.5f)
            // Filling up before it tips over.
            sx += t to 1.05f * r
            sy += t to 1.08f * r
            for (run in 0 until bounds.size - 1) {
                val a = bounds[run]
                val b = bounds[run + 1]
                val length = arc[b] - arc[a]
                val d = 0.06f + length / rnd.range(60f, 95f)
                if (run == 0) {
                    trim += t - 0.02f to 0f
                    trim += t to lead / trailLength
                    trail += t - 0.02f to 0f
                    trail += t to 1f
                } else {
                    trim += t to (lead + arc[a]) / trailLength
                    sx += t to 1.05f * r
                    sy += t to 1.08f * r
                }
                tx += t to points[a].x
                ty += t to points[a].y
                for (k in a + 1..b) {
                    val tk = t + d * unease((arc[k] - arc[a]) / length)
                    tx += tk to points[k].x
                    ty += tk to points[k].y
                    trim += tk to (lead + arc[k]) / trailLength
                    passes[k] = tk
                }
                sx += t + 0.4f * d to 0.88f * r
                sy += t + 0.4f * d to 1.34f * r
                sx += t + d to 0.97f * r
                sy += t + d to 1.2f * r
                val pause = rnd.range(0.12f, 0.55f)
                if (run < bounds.size - 2 && pause > 0.16f) {
                    sx += t + d + 0.12f to 1.03f * r
                    sy += t + d + 0.12f to 1.1f * r
                }
                t += d + if (run < bounds.size - 2) pause else 0f
            }
            val fade = t + 0.35f
            sx += fade to 1.03f * r
            sy += fade to 1.1f * r
            sx += fade + 0.7f to 0.9f * r
            sy += fade + 0.7f to 1f * r
            sx += fade + 0.72f to 0f
            sy += fade + 0.72f to 0f
            alpha += fade to 1f
            alpha += fade + 0.7f to 0f
            alpha += fade + 0.74f to 1f
            tx += fade + 0.72f to points.last().x
            ty += fade + 0.72f to points.last().y
            tx += fade + 0.74f to points.first().x
            ty += fade + 0.74f to points.first().y
            val dry = fade + 0.3f
            trail += dry to 1f
            trail += dry + 1.3f to 0f
            trim += dry + 1.3f to 1f
            trim += dry + 1.32f to 0f
            // A few droplets left behind on the way.
            (1 until points.size - 1).shuffled(rnd).take(if (points.size > 5) 3 else 2).forEach { k ->
                val side = if (rnd.nextBoolean()) 1f else -1f
                val at = P(points[k].x + side * rnd.range(0.2f, 0.45f) * r, points[k].y - rnd.range(0.3f, 1f) * r)
                residues += at to listOf(0f to 0f, passes[k] + 0.1f to 0f, passes[k] + 0.18f to 1f, dry to 1f, dry + 1.3f to 0f)
            }
            periodMs = ms(dry + 1.34f + rnd.range(1.5f, 4.5f))
            start = rnd.range(0f, periodMs / 1000f)
        }

        private val period get() = periodMs / 1000f

        /** Keeps beads out of the drop's way. */
        fun blocks(x: Float, y: Float, radius: Float): Boolean {
            val left = points.minOf { it.x } - r - radius - 2f
            val right = points.maxOf { it.x } + r + radius + 2f
            val top = points.first().y - 3.3f * r - 2f
            val bottom = points.last().y + 2f * radius + 2f
            return x in left..right && y in top..bottom
        }

        fun emitTrail(v: Vector, name: String, rnd: Random) {
            val data = buildString {
                append("M${f(points[0].x)},${f(points[0].y - lead)}")
                points.forEach { append("L${f(it.x)},${f(it.y)}") }
            }
            val trimKeys = loop(period, 0f, start to trim)
            val trailKeys = loop(period, 0f, start to trail)
            // The glass the drop wiped: a clearer band with darker edges.
            v.body += path(data, name = "${name}_edge", stroke = INK, strokeWidth = 0.95f * r, strokeAlpha = 0.1f * trailKeys.first().value, cap = true, trimEnd = trimKeys.first().value)
            v.body += path(data, name = "${name}_wet", stroke = WHITE, strokeWidth = 0.62f * r, strokeAlpha = 0.2f * trailKeys.first().value, cap = true, trimEnd = trimKeys.first().value)
            for ((part, peak) in listOf("edge" to 0.1f, "wet" to 0.2f)) {
                v.animate("${name}_$part", Anim(periodMs, listOf(Keys("trimPathEnd", trimKeys), Keys("strokeAlpha", trailKeys.map { Key(it.fraction, it.value * peak) }))))
            }
            residues.forEachIndexed { i, (at, keys) ->
                val shown = loop(period, 0f, start to keys)
                val size = rnd.range(0.45f, 0.8f)
                v.body += path(circle(at.x, at.y - size, size), name = "${name}_d$i", fill = "#A6FFFFFF", fillAlpha = shown.first().value)
                v.animate("${name}_d$i", Anim(periodMs, listOf(Keys("fillAlpha", shown))))
            }
        }

        fun emitDrop(v: Vector, name: String) {
            val keysX = loop(period, points.first().x, start to tx)
            val keysY = loop(period, points.first().y, start to ty)
            val scaleX = loop(period, 0f, start to sx)
            val scaleY = loop(period, 0f, start to sy)
            val shown = loop(period, 1f, start to alpha)
            v.body += group(name, x = keysX.first().value, y = keysY.first().value, scaleX = scaleX.first().value, scaleY = scaleY.first().value)
                .add(path(TEAR, name = "${name}_body", fill = "@color/motion_drop_body", fillAlpha = shown.first().value, stroke = "@color/motion_drop_rim", strokeWidth = 0.13f, strokeAlpha = shown.first().value))
                .add(path(tearLight, name = "${name}_light", fill = WHITE, fillAlpha = 0.5f * shown.first().value))
                .add(path(ellipse(-0.34f, -1.55f, 0.26f, 0.34f), name = "${name}_glint", fill = "#F5FFFFFF", fillAlpha = shown.first().value))
            v.animate(name, Anim(periodMs, listOf(Keys("translateX", keysX), Keys("translateY", keysY), Keys("scaleX", scaleX), Keys("scaleY", scaleY))))
            v.animate("${name}_body", Anim(periodMs, listOf(Keys("fillAlpha", shown), Keys("strokeAlpha", shown))))
            v.animate("${name}_light", Anim(periodMs, listOf(Keys("fillAlpha", shown.scaled(0.5f)))))
            v.animate("${name}_glint", Anim(periodMs, listOf(Keys("fillAlpha", shown))))
        }

        /** Time (0..1) at which smoothstep easing has covered [q] of the way. */
        private fun unease(q: Float): Float {
            var lo = 0f
            var hi = 1f
            repeat(24) {
                val mid = (lo + hi) / 2
                if (mid * mid * (3 - 2 * mid) < q) lo = mid else hi = mid
            }
            return (lo + hi) / 2
        }
    }

    // endregion

    // region Lightning

    /** Two strikes a loop, the same instants on every tile: a double flicker, then a triple. */
    private val strikes = listOf(
        1.6f to listOf(0f to 0f, 0.03f to 0.42f, 0.09f to 0.08f, 0.14f to 0.34f, 0.24f to 0.12f, 0.55f to 0f),
        7.4f to listOf(0f to 0f, 0.03f to 0.3f, 0.09f to 0.05f, 0.13f to 0.38f, 0.2f to 0.18f, 0.26f to 0.27f, 0.6f to 0f),
    )
    private val bolts = listOf(
        listOf(0f to 0f, 0.025f to 1f, 0.09f to 0.25f, 0.14f to 0.95f, 0.26f to 0.3f, 0.45f to 0f),
        listOf(0f to 0f, 0.025f to 0.85f, 0.09f to 0.15f, 0.13f to 1f, 0.2f to 0.5f, 0.26f to 0.8f, 0.5f to 0f),
    )

    private fun bolt(v: Vector, rnd: Random, strike: Int) {
        val x0 = rnd.range(55f, 125f)
        val end = P((x0 + rnd.range(-28f, 28f)).coerceIn(20f, TILE_W - 20f), rnd.range(58f, 78f))
        val main = jag(P(x0, -3f), end, levels = 5, roughness = 0.2f, rnd = rnd)
        val branches = List(1 + rnd.nextInt(2)) {
            val from = main[rnd.nextInt(4, main.size * 2 / 3)]
            val side = if (rnd.nextBoolean()) 1f else -1f
            val to = P(from.x + side * rnd.range(12f, 26f), (from.y + rnd.range(12f, 24f)).coerceAtMost(TILE_H - 4f))
            jag(from, to, levels = 4, roughness = 0.25f, rnd = rnd)
        }
        fun polyline(points: List<P>) = buildString {
            points.forEachIndexed { i, p -> append(if (i == 0) "M" else "L").append(f(p.x.coerceIn(4f, TILE_W - 4f))).append(',').append(f(p.y)) }
        }
        val keys = loop(STORM_PERIOD, 0f, strikes[strike].first to bolts[strike])
        val all = polyline(main) + branches.joinToString("") { polyline(it) }
        val name = "bolt"
        v.body += path(all, name = "${name}_glow", stroke = "#A8B4FF", strokeWidth = 2.6f, strokeAlpha = 0f, cap = true, join = true)
        v.body += path(branches.joinToString("") { polyline(it) }, name = "${name}_branches", stroke = "#F4F2FF", strokeWidth = 0.55f, strokeAlpha = 0f, cap = true, join = true)
        v.body += path(polyline(main), name = "${name}_core", stroke = WHITE, strokeWidth = 0.9f, strokeAlpha = 0f, cap = true, join = true)
        v.animate("${name}_glow", Anim(ms(STORM_PERIOD), listOf(Keys("strokeAlpha", keys.map { Key(it.fraction, it.value * 0.5f) }))))
        v.animate("${name}_branches", Anim(ms(STORM_PERIOD), listOf(Keys("strokeAlpha", keys.map { Key(it.fraction, it.value * 0.85f) }))))
        v.animate("${name}_core", Anim(ms(STORM_PERIOD), listOf(Keys("strokeAlpha", keys))))
    }

    /** Midpoint displacement: each halving of a segment knocks its middle sideways, less each time. */
    private fun jag(a: P, b: P, levels: Int, roughness: Float, rnd: Random): List<P> {
        var points = listOf(a, b)
        var amplitude = roughness * hypot(b.x - a.x, b.y - a.y)
        repeat(levels) {
            val next = mutableListOf(points[0])
            for (i in 1 until points.size) {
                val p = points[i - 1]
                val q = points[i]
                val length = hypot(q.x - p.x, q.y - p.y).coerceAtLeast(0.01f)
                val offset = rnd.range(-amplitude, amplitude)
                next += P((p.x + q.x) / 2 - (q.y - p.y) / length * offset, (p.y + q.y) / 2 + (q.x - p.x) / length * offset)
                next += q
            }
            points = next
            amplitude *= 0.5f
        }
        return points
    }

    private fun flash(v: Vector) {
        val keys = loop(STORM_PERIOD, 0f, *strikes.toTypedArray())
        v.body += path("M-1,-1H${f(TILE_W + 1)}V${f(TILE_H + 1)}H-1Z", name = "flash", fill = "#E4E9FF", fillAlpha = keys.first().value)
        v.animate("flash", Anim(ms(STORM_PERIOD), listOf(Keys("fillAlpha", keys))))
    }

    // endregion

    // region Snow

    private fun snowTile(kind: Snow): String {
        val v = Vector()
        val rnd = Random(kind.seed)
        kind.flakes.forEachIndexed { layer, s ->
            val shade = StringBuilder()
            val body = StringBuilder()
            val halo = StringBuilder()
            repeat(s.count) {
                val x = rnd.nextFloat() * TILE_W
                val y = rnd.nextFloat() * TILE_H
                val r = rnd.range(s.radius)
                val reach = r * (if (s.halo) 1.9f else 1f) + s.sway + 1f
                for (i in -1..1) for (j in -1..1) {
                    val cx = x + i * TILE_W
                    val cy = y + j * TILE_H
                    if (cx + reach < -TILE_W || cx - reach > TILE_W || cy + reach < -TILE_H || cy - reach > TILE_H) continue
                    shade.append(circle(cx + 0.3f, cy + 0.4f, r))
                    body.append(circle(cx, cy, r))
                    if (s.halo) halo.append(circle(cx, cy, r * 1.9f))
                }
            }
            val sway = "snow${layer}_sway"
            val fall = "snow$layer"
            val flakes = group(fall).add(path(shade.toString(), fill = INK, fillAlpha = s.shadow))
            if (s.halo) flakes.add(path(halo.toString(), fill = WHITE, fillAlpha = 0.16f))
            flakes.add(path(body.toString(), fill = WHITE, fillAlpha = s.alpha))
            v.body += group(sway).add(flakes)
            v.animate(fall, Anim(ms(TILE_W / s.drift), listOf(Ramp("translateX", 0f, TILE_W))))
            v.animate(fall, Anim(ms(TILE_H / s.fall), listOf(Ramp("translateY", 0f, TILE_H))))
            val swing = (0..12).map { k -> Key(k / 12f, s.sway * sin(2 * PI.toFloat() * k / 12f)) }
            v.animate(sway, Anim(ms(s.swayPeriod), listOf(Keys("translateX", swing))))
        }
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}")
    }

    // endregion

    // region Shapes

    /** A bead sitting on the glass, radius 1 with its foot at the origin (it grows from there). */
    private const val BEAD = "M-1,-1A1,1 0 1,0 1,-1A1,1 0 1,0 -1,-1Z"

    /** A running drop: heavy foot at the origin, drawn out to a point above. */
    private const val TEAR = "M0,-2.35C0.35,-1.95 1,-1.45 1,-0.95C1,-0.4 0.55,0 0,0C-0.55,0 -1,-0.4 -1,-0.95C-1,-1.45 -0.35,-1.95 0,-2.35Z"

    /** Light in a bead: the sky caught along its upper rim, and gathered inside its foot. */
    private val beadLight = crescent(0f, -1f, 0.88f, 0.74f, 210f, 330f) + ellipse(0.06f, -0.34f, 0.5f, 0.17f)

    private val tearLight = crescent(0f, -1.02f, 0.84f, 0.7f, 205f, 300f) + ellipse(0.05f, -0.3f, 0.5f, 0.16f)

    /** A thin band of ring between radii [outer] and [inner], [from]..[to] degrees (y grows down). */
    private fun crescent(cx: Float, cy: Float, outer: Float, inner: Float, from: Float, to: Float): String {
        fun at(radius: Float, degrees: Float): String {
            val a = Math.toRadians(degrees.toDouble())
            return "${f(cx + radius * kotlin.math.cos(a).toFloat())},${f(cy + radius * sin(a).toFloat())}"
        }
        return "M${at(outer, from)}A${f(outer)},${f(outer)} 0 0,1 ${at(outer, to)}L${at(inner, to)}A${f(inner)},${f(inner)} 0 0,0 ${at(inner, from)}Z"
    }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${f(cx - r)},${f(cy)}a${f(r)},${f(r)} 0 1,0 ${f(2 * r)},0a${f(r)},${f(r)} 0 1,0 ${f(-2 * r)},0Z"

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
        "M${f(cx - rx)},${f(cy)}a${f(rx)},${f(ry)} 0 1,0 ${f(2 * rx)},0a${f(rx)},${f(ry)} 0 1,0 ${f(-2 * rx)},0Z"

    // endregion

    // region Timelines

    private data class P(val x: Float, val y: Float)

    data class Key(val fraction: Float, val value: Float)

    /**
     * One loop of [period] s of a value that rests at [rest] and, from each event's start, follows
     * that event's (seconds since start, value) points — first and last at [rest]. Events that run
     * past the end of the loop wrap around to its beginning.
     */
    private fun loop(period: Float, rest: Float, vararg events: Pair<Float, List<Pair<Float, Float>>>): List<Key> {
        for ((_, points) in events) {
            require(points.first().second == rest && points.last().second == rest) { "event must start and end at rest" }
            require(points.zipWithNext().all { (a, b) -> b.first - a.first >= 0.0099f }) { "event points too close: $points" }
            require(points.last().first < period) { "event longer than its loop" }
        }
        fun at(t: Float): Float {
            for ((start, points) in events) {
                val since = ((t - start) % period + period) % period
                if (since <= points.last().first) {
                    val i = points.indexOfLast { it.first <= since }.coerceAtMost(points.size - 2)
                    val (t0, v0) = points[i]
                    val (t1, v1) = points[i + 1]
                    return v0 + (v1 - v0) * ((since - t0) / (t1 - t0)).coerceIn(0f, 1f)
                }
            }
            return rest
        }
        val times = sortedSetOf(0f)
        for ((start, points) in events) points.forEach { (since, _) -> times += (start + since) % period }
        val keys = mutableListOf<Key>()
        for (t in times) {
            val fraction = Math.round(t / period * 10000f) / 10000f
            if (keys.isNotEmpty() && fraction <= keys.last().fraction) continue
            if (fraction >= 1f) continue
            keys += Key(fraction, at(t))
        }
        keys += Key(1f, keys.first().value)
        return keys
    }

    private fun ms(seconds: Float) = (seconds * 1000f).roundToLong()

    private fun List<Key>.scaled(by: Float) = map { Key(it.fraction, it.value * by) }

    private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

    private fun Random.range(r: ClosedFloatingPointRange<Float>) = range(r.start, r.endInclusive)

    // endregion

    // region XML

    class Vector {
        val body = mutableListOf<El>()
        val targets = linkedMapOf<String, MutableList<Anim>>()

        fun animate(target: String, anim: Anim) {
            targets.getOrPut(target) { mutableListOf() } += anim
        }
    }

    class Anim(val durationMs: Long, val props: List<Prop>)

    sealed class Prop(val name: String)

    class Ramp(name: String, val from: Float, val to: Float) : Prop(name)

    class Keys(name: String, val keys: List<Key>) : Prop(name)

    class El(val tag: String) {
        val attrs = mutableListOf<Pair<String, String>>()
        val children = mutableListOf<El>()

        fun attr(name: String, value: String) = apply { attrs += name to value }

        fun add(child: El) = apply { children += child }

        fun write(sb: StringBuilder, depth: Int) {
            val pad = "    ".repeat(depth)
            sb.append(pad).append('<').append(tag)
            attrs.forEach { (k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"') }
            if (children.isEmpty()) {
                sb.append(" />\n")
                return
            }
            sb.append(">\n")
            children.forEach { it.write(sb, depth + 1) }
            sb.append(pad).append("</").append(tag).append(">\n")
        }
    }

    private fun group(name: String, x: Float = 0f, y: Float = 0f, scaleX: Float = 1f, scaleY: Float = 1f) = El("group").apply {
        attr("android:name", name)
        if (x != 0f) attr("android:translateX", f(x))
        if (y != 0f) attr("android:translateY", f(y))
        if (scaleX != 1f) attr("android:scaleX", f(scaleX))
        if (scaleY != 1f) attr("android:scaleY", f(scaleY))
    }

    private fun path(
        data: String,
        name: String? = null,
        fill: String? = null,
        fillAlpha: Float = 1f,
        stroke: String? = null,
        strokeWidth: Float = 0f,
        strokeAlpha: Float = 1f,
        cap: Boolean = false,
        join: Boolean = false,
        trimEnd: Float = 1f,
    ) = El("path").apply {
        if (name != null) attr("android:name", name)
        if (fill != null) {
            attr("android:fillColor", fill)
            if (fillAlpha != 1f) attr("android:fillAlpha", f(fillAlpha))
        }
        if (stroke != null) {
            attr("android:strokeColor", stroke)
            attr("android:strokeWidth", f(strokeWidth))
            if (strokeAlpha != 1f) attr("android:strokeAlpha", f(strokeAlpha))
            if (cap) attr("android:strokeLineCap", "round")
            if (join) attr("android:strokeLineJoin", "round")
        }
        if (trimEnd != 1f) attr("android:trimPathEnd", f(trimEnd))
        attr("android:pathData", data)
    }

    private fun Anim.element(): El {
        val el = El("objectAnimator")
            .attr("android:duration", durationMs.toString())
            .attr("android:interpolator", "@android:interpolator/linear")
            .attr("android:repeatCount", "-1")
        val single = props.singleOrNull()
        if (single is Ramp) {
            return el.attr("android:propertyName", single.name)
                .attr("android:valueFrom", f(single.from))
                .attr("android:valueTo", f(single.to))
                .attr("android:valueType", "floatType")
        }
        props.forEach { p ->
            val holder = El("propertyValuesHolder").attr("android:propertyName", p.name).attr("android:valueType", "floatType")
            when (p) {
                is Ramp -> holder.attr("android:valueFrom", f(p.from)).attr("android:valueTo", f(p.to))
                is Keys -> p.keys.forEach { k -> holder.add(El("keyframe").attr("android:fraction", fraction(k.fraction)).attr("android:value", f(k.value))) }
            }
            el.add(holder)
        }
        return el
    }

    private fun animatedVector(v: Vector, title: String): String {
        val vector = El("vector")
            .attr("android:width", "${f(TILE_W)}dp")
            .attr("android:height", "${f(TILE_H)}dp")
            .attr("android:viewportWidth", f(TILE_W))
            .attr("android:viewportHeight", f(TILE_H))
        v.body.forEach { vector.add(it) }
        val root = El("animated-vector")
            .attr("xmlns:android", "http://schemas.android.com/apk/res/android")
            .attr("xmlns:aapt", "http://schemas.android.com/aapt")
            .add(El("aapt:attr").attr("name", "android:drawable").add(vector))
        v.targets.forEach { (name, anims) ->
            val animation = if (anims.size == 1) anims[0].element() else El("set").apply { anims.forEach { add(it.element()) } }
            root.add(El("target").attr("android:name", name).add(El("aapt:attr").attr("name", "android:animation").add(animation)))
        }
        return document(title, root)
    }

    private fun tileLayout(drawable: String): String {
        val root = El("ProgressBar")
            .attr("xmlns:android", "http://schemas.android.com/apk/res/android")
            .attr("xmlns:tools", "http://schemas.android.com/tools")
            .attr("android:id", "@+id/motion_tile")
            .attr("android:layout_width", "${f(TILE_W)}dp")
            .attr("android:layout_height", "${f(TILE_H)}dp")
            .attr("android:layout_gravity", "top|left")
            .attr("android:importantForAccessibility", "no")
            .attr("android:indeterminate", "true")
            .attr("android:indeterminateOnly", "false")
            .attr("android:indeterminateDrawable", "@drawable/$drawable")
            .attr("android:indeterminateTint", "@null")
            .attr("android:mirrorForRtl", "false")
            // Tiles sit where the picture under them is drawn, and the picture doesn't mirror.
            .attr("tools:ignore", "RtlHardcoded")
        return document("A live weather tile: a ProgressBar is the one view that plays its drawable by itself", root)
    }

    private fun gradient(stops: List<Pair<Float, String>>): String {
        val root = El("gradient")
            .attr("xmlns:android", "http://schemas.android.com/apk/res/android")
            .attr("android:type", "linear")
            .attr("android:startX", "0")
            .attr("android:startY", "-2.2")
            .attr("android:endX", "0")
            .attr("android:endY", "0")
        stops.forEach { (offset, color) -> root.add(El("item").attr("android:offset", f(offset)).attr("android:color", color)) }
        return document("A drop of water as a lens: the ground above, the sky at its foot", root)
    }

    private fun document(title: String, root: El): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<!-- $title. Generated by MotionResources (widget tests); do not edit. -->\n")
        root.write(sb, 0)
        return sb.toString()
    }

    private fun f(v: Float): String {
        val r = Math.round(v * 100f) / 100f
        if (r == 0f) return "0"
        return if (r == r.toLong().toFloat()) r.toLong().toString() else String.format(Locale.ROOT, "%.2f", r).trimEnd('0').trimEnd('.')
    }

    private fun fraction(v: Float): String =
        if (v == 0f) "0" else if (v == 1f) "1" else String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')

    // endregion
}
