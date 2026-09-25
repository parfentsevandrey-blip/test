package app.rosa.weather.widget.motion

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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

    /**
     * The calendar's seasons in motion, on taller tiles ([SEASON_W] × [SEASON_H]) in [VARIANTS]
     * variants each: September's birch leaves and October's maple leaves falling like leaves do,
     * May's apple petals fluttering, the fireflies of July's dusk and August's night, January's
     * glittering frost in the air, June's poplar fluff, and August's falling stars.
     */
    enum class Season(val id: String, val seed: Int, val colors: List<String>) {
        LeavesGold("season_leaves_gold", 91, listOf("#F6C443", "#E8A62E", "#FFD75E", "#D98E26", "#F2B53A")),
        LeavesRed("season_leaves_red", 97, listOf("#E8632B", "#F28C38", "#D24A2A", "#B8342A", "#F6B04A")),
        Petals("season_petals", 101, listOf("#FFFFFF", "#FFF4F7", "#FDE3EA", "#F9D0DC")),
        Fireflies("season_fireflies", 107, listOf("#FFF6C8", "#E4F7A8")),
        FirefliesLow("season_fireflies_low", 109, listOf("#FFF6C8", "#E4F7A8")),
        Glitter("season_glitter", 113, listOf("#FFFFFF", "#FFF6DC", "#E2ECFF")),
        Fluff("season_fluff", 127, listOf("#FFFFFF")),
        Meteors("season_meteors", 131, listOf("#FFFFFF", "#E4F7A8")),
    }

    const val SEASON_W = 180f
    const val SEASON_H = 180f

    fun tileName(kind: Season, variant: Int) = "motion_${kind.id}_${'a' + variant}"

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
        put("color/motion_glow_firefly.xml", radial(listOf(0f to "#FFFFF8D0", 0.18f to "#E6F4F8B0", 0.45f to "#55D9F59A", 1f to "#00D9F59A"), "The glow of a firefly"))
        put("color/motion_glow_fluff.xml", radial(listOf(0f to "#F2FFFFFF", 0.35f to "#B3FFFFFF", 0.7f to "#33FFFFFF", 1f to "#00FFFFFF"), "A tuft of poplar fluff"))
        put("color/motion_glow_frost.xml", radial(listOf(0f to "#FFFFFFFF", 0.2f to "#99FFF6DC", 1f to "#00DDE8FF"), "A glint of frost in the air"))
        put("color/motion_meteor.xml", linear(listOf(0f to "#00CFE0FF", 0.7f to "#99E8F0FF", 1f to "#FFFFFFFF"), 0f, METEOR, "A falling star, faint at its tail"))
        for (kind in Season.entries) {
            repeat(VARIANTS) { variant ->
                val name = tileName(kind, variant)
                val tile = when (kind) {
                    Season.LeavesGold, Season.LeavesRed -> leavesTile(kind, variant)
                    Season.Petals -> petalsTile(kind, variant)
                    Season.Fireflies -> firefliesTile(kind, variant, top = 6f)
                    Season.FirefliesLow -> firefliesTile(kind, variant, top = SEASON_H * 0.55f)
                    Season.Glitter -> glitterTile(kind, variant)
                    Season.Fluff -> fluffTile(kind, variant)
                    Season.Meteors -> meteorsTile(kind, variant)
                }
                put("drawable/$name.xml", tile)
                put("layout/$name.xml", tileLayout(name, SEASON_W, SEASON_H))
            }
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

    // region Seasons

    /**
     * Leaves falling the way leaves fall: in three depths, each swinging from side to side as it
     * sinks, tilting into each swing, and turning over now and then — its underside, darker,
     * showing as it does. Every leaf has a twin a tile above it, so the fall loops without a seam;
     * they keep clear of the sides, so neighbouring columns can differ.
     */
    private fun leavesTile(kind: Season, variant: Int): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 5)
        val maple = kind == Season.LeavesRed
        val layers = listOf(
            Layer(4, 3f..3.8f, fall = 8f, sway = 5f, alpha = 0.75f),
            Layer(3, 4.4f..5.6f, fall = 12f, sway = 8f, alpha = 0.92f),
            Layer(2, 6.5f..8f, fall = 18f, sway = 11f, alpha = 0.96f),
        )
        layers.forEachIndexed { layer, spec ->
            val fall = group("leaves$layer")
            repeat(spec.count) { i ->
                val size = rnd.range(spec.sizes)
                val margin = spec.sway + size * 1.6f + 1f
                val x = rnd.range(margin, SEASON_W - margin)
                val y = rnd.nextFloat() * SEASON_H
                val color = kind.colors[rnd.nextInt(kind.colors.size)]
                val swing = rnd.range(2.8f, 4.6f)
                val phase = rnd.nextFloat()
                val tilt = rnd.range(22f, 38f)
                val turn = rnd.range(1.4f, 2.6f) * (if (rnd.nextBoolean()) 1f else -1f)
                val base = rnd.range(-30f, 30f)
                for (copy in 0..1) {
                    val name = "leaf${layer}_${i}_$copy"
                    val body = group("${name}_t", scaleX = size, scaleY = size)
                        .add(path(if (maple) MAPLE else BIRCH, fill = color, fillAlpha = spec.alpha))
                        .add(path(if (maple) MAPLE_SHADE else BIRCH_SHADE, name = "${name}_shade", fill = INK, fillAlpha = 0f))
                        .add(path(if (maple) MAPLE_VEINS else BIRCH_VEIN, stroke = INK, strokeWidth = 0.07f, strokeAlpha = 0.3f * spec.alpha))
                    fall.add(group(name, x, y - copy * SEASON_H).add(body))
                    // Swinging as it sinks, and tilting into each swing.
                    val sideways = (0..12).map { k -> Key(k / 12f, x + spec.sway * sin(2 * PI.toFloat() * (k / 12f + phase))) }
                    val lean = (0..12).map { k -> Key(k / 12f, base + tilt * cos(2 * PI.toFloat() * (k / 12f + phase))) }
                    v.animate(name, Anim(ms(swing), listOf(Keys("translateX", sideways), Keys("rotation", lean))))
                    // Turning over: narrowing to its edge, then showing its darker underside.
                    val over = (0..8).map { k -> cos(2 * PI.toFloat() * k / 8f) }
                    v.animate("${name}_t", Anim(ms(abs(turn)), listOf(Keys("scaleY", over.mapIndexed { k, c -> Key(k / 8f, size * c) }))))
                    v.animate("${name}_shade", Anim(ms(abs(turn)), listOf(Keys("fillAlpha", over.mapIndexed { k, c -> Key(k / 8f, (-c).coerceAtLeast(0f) * 0.45f) }))))
                }
            }
            v.body += fall
            v.animate("leaves$layer", Anim(ms(SEASON_H / spec.fall), listOf(Ramp("translateY", 0f, SEASON_H))))
        }
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}", SEASON_W, SEASON_H)
    }

    /** Apple petals on the wind: pink at the heart, fluttering as they drift down, in three depths. */
    private fun petalsTile(kind: Season, variant: Int): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 5)
        val layers = listOf(
            Layer(6, 1.5f..2f, fall = 6f, sway = 5f, alpha = 0.8f),
            Layer(5, 2.3f..3f, fall = 9f, sway = 8f, alpha = 0.92f),
            Layer(3, 3.5f..4.4f, fall = 13f, sway = 12f, alpha = 0.96f),
        )
        layers.forEachIndexed { layer, spec ->
            val fall = group("petals$layer")
            repeat(spec.count) { i ->
                val size = rnd.range(spec.sizes)
                val margin = spec.sway + size * 1.5f + 1f
                val x = rnd.range(margin, SEASON_W - margin)
                val y = rnd.nextFloat() * SEASON_H
                val color = kind.colors[rnd.nextInt(kind.colors.size)]
                val swing = rnd.range(3.2f, 5.4f)
                val phase = rnd.nextFloat()
                val flutter = rnd.range(0.9f, 1.6f)
                val base = rnd.nextFloat() * 360f
                val spin = if (rnd.nextBoolean()) 1f else -1f
                for (copy in 0..1) {
                    val name = "petal${layer}_${i}_$copy"
                    val body = group("${name}_t", scaleX = size, scaleY = size)
                        .add(path(PETAL, fill = color, fillAlpha = spec.alpha))
                        .add(path(PETAL_HEART, fill = "#F29BB4", fillAlpha = 0.55f * spec.alpha))
                    fall.add(group(name, x, y - copy * SEASON_H).add(body))
                    val sideways = (0..12).map { k -> Key(k / 12f, x + spec.sway * sin(2 * PI.toFloat() * (k / 12f + phase))) }
                    val turning = listOf(Key(0f, base), Key(1f, base + spin * 360f))
                    v.animate(name, Anim(ms(swing), listOf(Keys("translateX", sideways))))
                    v.animate(name, Anim(ms(swing * 2f), listOf(Keys("rotation", turning))))
                    val flap = (0..8).map { k -> Key(k / 8f, size * (0.25f + 0.75f * abs(cos(PI.toFloat() * k / 8f)))) }
                    v.animate("${name}_t", Anim(ms(flutter), listOf(Keys("scaleY", flap))))
                }
            }
            v.body += fall
            v.animate("petals$layer", Anim(ms(SEASON_H / spec.fall), listOf(Ramp("translateY", 0f, SEASON_H))))
        }
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}", SEASON_W, SEASON_H)
    }

    /**
     * Fireflies wandering on slow closed loops below [top], each a hot point in a soft halo that
     * lights up and goes out in its own rhythm.
     */
    private fun firefliesTile(kind: Season, variant: Int, top: Float): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 5)
        fireflies(v, rnd, count = if (top > 20f) 5 else 8, top = top, bottom = SEASON_H - 6f, core = kind.colors[0])
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}", SEASON_W, SEASON_H)
    }

    private fun fireflies(v: Vector, rnd: Random, count: Int, top: Float, bottom: Float, core: String) {
        repeat(count) { i ->
            val a1 = rnd.range(6f, 11f)
            val a2 = rnd.range(1.5f, 4f)
            val b1 = rnd.range(3f, 7f)
            val b2 = rnd.range(1f, 3f)
            val x0 = rnd.range(8f + a1 + a2, SEASON_W - 8f - a1 - a2)
            val y0 = rnd.range(top + b1 + b2, bottom - b1 - b2)
            val p = List(4) { rnd.nextFloat() * 2f * PI.toFloat() }
            val period = rnd.range(9f, 16f)
            val keys = 16
            val xs = (0..keys).map { k ->
                val t = 2 * PI.toFloat() * k / keys
                Key(k / keys.toFloat(), x0 + a1 * sin(t + p[0]) + a2 * sin(2 * t + p[1]))
            }
            val ys = (0..keys).map { k ->
                val t = 2 * PI.toFloat() * k / keys
                Key(k / keys.toFloat(), y0 + b1 * cos(t + p[2]) + b2 * sin(3 * t + p[3]))
            }
            val size = rnd.range(2.6f, 4.4f)
            val name = "firefly$i"
            v.body += group(name, x0, y0).add(
                group("${name}_s", scaleX = size, scaleY = size)
                    .add(path(circle(0f, 0f, 1f), name = "${name}_halo", fill = "@color/motion_glow_firefly", fillAlpha = 0.1f))
                    .add(path(circle(0f, 0f, 0.17f), name = "${name}_core", fill = core, fillAlpha = 0.15f)),
            )
            v.animate(name, Anim(ms(period), listOf(Keys("translateX", xs), Keys("translateY", ys))))
            // Lit for a breath, then a long rest, each in its own rhythm.
            val blink = rnd.range(2.6f, 5.2f)
            val start = rnd.nextFloat() * blink * 0.6f
            val glow = loop(blink, 0.08f, start to listOf(0f to 0.08f, 0.35f to 1f, 0.8f to 0.85f, 1.4f to 0.08f))
            v.animate("${name}_halo", Anim(ms(blink), listOf(Keys("fillAlpha", glow))))
            v.animate("${name}_core", Anim(ms(blink), listOf(Keys("fillAlpha", glow.map { Key(it.fraction, (it.value * 1.1f).coerceAtMost(1f)) }))))
        }
    }

    /**
     * Diamond dust: ice crystals sinking slowly through frosty air, each glinting — a cross of light
     * for an instant — as it turns to the sun.
     */
    private fun glitterTile(kind: Season, variant: Int): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 5)
        val fall = group("dust")
        repeat(18) { i ->
            val x = rnd.range(6f, SEASON_W - 6f)
            val y = rnd.nextFloat() * SEASON_H
            val size = rnd.range(2.4f, 4.4f)
            val color = kind.colors[rnd.nextInt(kind.colors.size)]
            val period = rnd.range(2.6f, 5f)
            val at = rnd.nextFloat() * period * 0.7f
            val flash = loop(period, 0f, at to listOf(0f to 0f, 0.12f to 1f, 0.45f to 0f))
            val speck = loop(period, 0.55f, at to listOf(0f to 0.55f, 0.12f to 1f, 0.5f to 0.55f))
            for (copy in 0..1) {
                val name = "glint${i}_$copy"
                fall.add(
                    group(name, x, y - copy * SEASON_H, scaleX = size, scaleY = size)
                        .add(path(circle(0f, 0f, 1f), name = "${name}_halo", fill = "@color/motion_glow_frost", fillAlpha = 0f))
                        .add(path(SPARK, name = "${name}_spark", fill = color, fillAlpha = 0f))
                        .add(path(circle(0f, 0f, 0.16f), name = "${name}_speck", fill = color, fillAlpha = 0.55f)),
                )
                v.animate("${name}_halo", Anim(ms(period), listOf(Keys("fillAlpha", flash.scaled(0.6f)))))
                v.animate("${name}_spark", Anim(ms(period), listOf(Keys("fillAlpha", flash))))
                v.animate("${name}_speck", Anim(ms(period), listOf(Keys("fillAlpha", speck))))
            }
        }
        v.body += fall
        v.animate("dust", Anim(ms(SEASON_H / 3.2f), listOf(Ramp("translateY", 0f, SEASON_H))))
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}", SEASON_W, SEASON_H)
    }

    /** Poplar fluff: soft white tufts drifting down, wandering on the air as they go. */
    private fun fluffTile(kind: Season, variant: Int): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 5)
        val fall = group("fluff")
        repeat(6) { i ->
            val a = rnd.range(8f, 16f)
            val b = rnd.range(3f, 6f)
            val x = rnd.range(a + 8f, SEASON_W - a - 8f)
            val y = rnd.nextFloat() * SEASON_H
            val size = rnd.range(2.4f, 4.6f)
            val period = rnd.range(7f, 12f)
            val p = rnd.nextFloat() * 2f * PI.toFloat()
            for (copy in 0..1) {
                val name = "tuft${i}_$copy"
                fall.add(
                    group(name, x, y - copy * SEASON_H, scaleX = size, scaleY = size)
                        .add(path(circle(0f, 0f, 1f), fill = "@color/motion_glow_fluff", fillAlpha = 0.85f))
                        .add(path(circle(0f, 0f, 0.12f), fill = kind.colors[0], fillAlpha = 0.9f)),
                )
                val xs = (0..12).map { k -> Key(k / 12f, x + a * sin(2 * PI.toFloat() * k / 12f + p)) }
                val ys = (0..12).map { k -> Key(k / 12f, y - copy * SEASON_H + b * sin(4 * PI.toFloat() * k / 12f + p)) }
                v.animate(name, Anim(ms(period), listOf(Keys("translateX", xs), Keys("translateY", ys))))
            }
        }
        v.body += fall
        v.animate("fluff", Anim(ms(SEASON_H / 3f), listOf(Ramp("translateY", 0f, SEASON_H))))
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}", SEASON_W, SEASON_H)
    }

    /**
     * The top of an August night: now and then a star falls — a streak drawn out along its path
     * and gone — over fireflies in the grass below.
     */
    private fun meteorsTile(kind: Season, variant: Int): String {
        val v = Vector()
        val rnd = Random(kind.seed * 101 + variant * 13 + 5)
        val period = 9f + variant * 1.7f
        repeat(2) { i ->
            val x = rnd.range(60f, SEASON_W - 10f)
            val y = rnd.range(8f, 50f)
            val angle = rnd.range(145f, 165f)
            val scale = rnd.range(0.7f, 1.15f)
            val at = (i * period * 0.5f + rnd.nextFloat() * period * 0.3f) % (period - 1.2f)
            val name = "meteor$i"
            v.body += group(name, x, y, scaleX = scale, scaleY = scale).attr("android:rotation", f(angle))
                .add(path("M0,0L${f(METEOR)},0", name = "${name}_streak", stroke = "@color/motion_meteor", strokeWidth = 0.9f, cap = true, trimEnd = 0f))
            // Drawn out head first, then gone from the tail.
            val head = loop(period, 0f, at to listOf(0f to 0f, 0.28f to 1f, 0.9f to 1f, 0.92f to 0f))
            val tail = loop(period, 0f, at to listOf(0f to 0f, 0.12f to 0f, 0.62f to 1f, 0.9f to 1f, 0.92f to 0f))
            v.animate("${name}_streak", Anim(ms(period), listOf(Keys("trimPathEnd", head), Keys("trimPathStart", tail))))
        }
        fireflies(v, rnd, count = 4, top = SEASON_H * 0.6f, bottom = SEASON_H - 6f, core = kind.colors[1])
        return animatedVector(v, "Live ${kind.id.replace('_', ' ')}", SEASON_W, SEASON_H)
    }

    private class Layer(val count: Int, val sizes: ClosedFloatingPointRange<Float>, val fall: Float, val sway: Float, val alpha: Float)

    // endregion

    // region Shapes

    /** A birch leaf, tip to the right, a unit from its middle to the tip; its stem to the left. */
    private const val BIRCH = "M-0.95,0C-0.72,-0.66 0.22,-0.74 1.25,0C0.22,0.74 -0.72,0.66 -0.95,0ZM-0.95,-0.05L-1.5,0.05L-1.48,0.12L-0.93,0.05Z"

    /** The half of a birch leaf that darkens as it turns over. */
    private const val BIRCH_SHADE = "M-0.95,0C-0.72,0.66 0.22,0.74 1.25,0Z"

    private const val BIRCH_VEIN = "M-0.9,0L1.05,0M-0.3,0L0.05,-0.42M-0.3,0L0.05,0.42M0.25,0L0.55,-0.32M0.25,0L0.55,0.32"

    /** A maple leaf: five pointed lobes round the stem, a unit across its middle. */
    private const val MAPLE = "M0,-1L0.18,-0.55L0.62,-0.72L0.5,-0.3L0.95,-0.38L0.62,0.05L0.8,0.35L0.3,0.28L0.12,0.55L0.04,0.42L0.06,0.98L-0.02,0.98L-0.04,0.42L-0.12,0.55L-0.3,0.28L-0.8,0.35L-0.62,0.05L-0.95,-0.38L-0.5,-0.3L-0.62,-0.72L-0.18,-0.55Z"

    private const val MAPLE_SHADE = "M0,-1L0.18,-0.55L0.62,-0.72L0.5,-0.3L0.95,-0.38L0.62,0.05L0.8,0.35L0.3,0.28L0.12,0.55L0,0.42Z"

    private const val MAPLE_VEINS = "M0,0.42L0,-0.85M0,0.3L0.72,-0.58M0,0.3L-0.72,-0.58M0,0.36L0.62,0.28M0,0.36L-0.62,0.28"

    /** An apple petal: rounded, with a notch at its tip; the heart towards the left. */
    private const val PETAL = "M-1,0C-1,-0.64 -0.2,-0.86 0.52,-0.56C0.86,-0.42 1.02,-0.2 0.8,0C1.02,0.2 0.86,0.42 0.52,0.56C-0.2,0.86 -1,0.64 -1,0Z"

    private const val PETAL_HEART = "M-1,0C-1,-0.34 -0.62,-0.44 -0.22,-0.26C-0.34,-0.08 -0.34,0.08 -0.22,0.26C-0.62,0.44 -1,0.34 -1,0Z"

    /** A glint: a four-pointed star of light, a unit to each point. */
    private const val SPARK = "M0,-1L0.09,-0.09L1,0L0.09,0.09L0,1L-0.09,0.09L-1,0L-0.09,-0.09Z"

    /** The length of a falling star's streak, dp. */
    private const val METEOR = 38f

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

    private fun animatedVector(v: Vector, title: String, width: Float = TILE_W, height: Float = TILE_H): String {
        val vector = El("vector")
            .attr("android:width", "${f(width)}dp")
            .attr("android:height", "${f(height)}dp")
            .attr("android:viewportWidth", f(width))
            .attr("android:viewportHeight", f(height))
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

    private fun tileLayout(drawable: String, width: Float = TILE_W, height: Float = TILE_H): String {
        val root = El("ProgressBar")
            .attr("xmlns:android", "http://schemas.android.com/apk/res/android")
            .attr("xmlns:tools", "http://schemas.android.com/tools")
            .attr("android:id", "@+id/motion_tile")
            .attr("android:layout_width", "${f(width)}dp")
            .attr("android:layout_height", "${f(height)}dp")
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

    /** A radial gradient round the origin of a unit circle: glows drawn at any size by scaling. */
    private fun radial(stops: List<Pair<Float, String>>, title: String): String {
        val root = El("gradient")
            .attr("xmlns:android", "http://schemas.android.com/apk/res/android")
            .attr("android:type", "radial")
            .attr("android:centerX", "0")
            .attr("android:centerY", "0")
            .attr("android:gradientRadius", "1")
        stops.forEach { (offset, color) -> root.add(El("item").attr("android:offset", f(offset)).attr("android:color", color)) }
        return document(title, root)
    }

    private fun linear(stops: List<Pair<Float, String>>, fromX: Float, toX: Float, title: String): String {
        val root = El("gradient")
            .attr("xmlns:android", "http://schemas.android.com/apk/res/android")
            .attr("android:type", "linear")
            .attr("android:startX", f(fromX))
            .attr("android:startY", "0")
            .attr("android:endX", f(toX))
            .attr("android:endY", "0")
        stops.forEach { (offset, color) -> root.add(El("item").attr("android:offset", f(offset)).attr("android:color", color)) }
        return document(title, root)
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
