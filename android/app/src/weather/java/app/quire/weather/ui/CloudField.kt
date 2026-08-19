package app.quire.weather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * A real three-dimensional cloud field, without an engine.
 *
 * The case against 3D here was never against the third dimension — it was against shipping a
 * five-megabyte renderer whose pixels no test can see. So the renderer is this file: clouds are
 * clusters of soft spheres placed in an actual 3D box, a camera projects them by the one honest
 * rule there is (a thing twice as far away is half as big and half as far off-centre), the far
 * ones are painted first, and the drawing is the same Skia canvas as the rest of the sky — GPU
 * on the phone, software in a test, the same picture in both. Zero bytes of engine, and a render
 * test can put one puff at two depths and measure the perspective with a ruler.
 *
 * Everything else follows the sky's own house rules: positions are pure functions of the looping
 * clock and an index, drift is a whole number of laps so the loop is seamless, the wind decides
 * the direction and pace, and the golden hour lights the undersides — the same [SkyMoment.glow]
 * that leans the wash leans the clouds, because a sunset that colours the sky but not the clouds
 * in it is only half a sunset.
 */

/** One soft sphere of cloud, in world units: x across, y up from the horizon, z away. */
internal class Puff(val x: Float, val y: Float, val z: Float, val size: Float)

/**
 * Perspective, whole: how far off the axis a world offset lands, in focal units.
 *
 * The same division answers every question this file asks — where a puff sits horizontally,
 * where vertically, and how big it draws — which is what makes it worth testing on its own:
 * twice the depth must mean exactly half of each.
 */
internal fun projected(offAxis: Float, depth: Float, focal: Float): Float = focal * offAxis / depth

/** The slab of world the clouds live in. Near enough to loom, far enough to be a sky. */
internal const val CLOUD_NEAR = 1.6f
internal const val CLOUD_FAR = 7f

/** Half the world's width; past the widest frustum edge, so the drift wrap happens off screen. */
internal const val CLOUD_SPAN = 8.5f

/**
 * Draws a list of puffs through the camera, far ones first.
 *
 * The camera sits at the origin looking down +z, with the horizon low on the band so the field
 * fills the sky rather than the page. Depth does the shading: near puffs are a little denser
 * and a little larger than the same cloud far away, which is the whole reason a field of flat
 * blobs reads as air with distance in it.
 */
internal fun DrawScope.puffField(
    puffs: List<Puff>,
    colour: Color,
    glowTint: Color,
    glow: Float,
    alphaScale: Float = 1f,
) {
    val focal = size.width * 0.5f
    val cx = size.width * 0.5f
    val horizon = size.height * 0.95f
    val inked = lerp(colour, glowTint, glow * 0.55f)

    for (puff in puffs.sortedByDescending { it.z }) {
        if (puff.z <= CLOUD_NEAR * 0.5f) continue
        val sx = cx + projected(puff.x, puff.z, focal)
        val sy = horizon - projected(puff.y, puff.z, focal)
        val radius = projected(puff.size, puff.z, focal)
        if (radius < 1f) continue
        if (sx + radius < 0f || sx - radius > size.width) continue

        val nearness = ((CLOUD_FAR - puff.z) / (CLOUD_FAR - CLOUD_NEAR)).coerceIn(0f, 1f)
        val alpha = (0.030f + 0.045f * nearness) * alphaScale
        blob(Offset(sx, sy), radius, inked, alpha)
    }
}

/**
 * The weather's own cloud field: [cover] of the sky, drifting the way the wind says.
 *
 * Each cloud is a deterministic cluster of five spheres around a base — one core, a cap or two
 * above it and shoulders beside it, which is roughly what water vapour does — and every value
 * comes from the scatter hash, so the same second of the same sky is the same field, which is
 * what lets the tests look at it at all.
 */
internal fun DrawScope.cloudField(
    clock: Float,
    cover: Float,
    lean: Float,
    colour: Color,
    glowTint: Color,
    glow: Float,
) {
    val count = (3 + cover * 6f).toInt()
    val direction = if (lean < 0f) -1f else 1f
    val puffs = ArrayList<Puff>(count * 5)
    val focal = size.width * 0.5f
    val band = size.height * 0.95f

    for (cloud in 0 until count) {
        // The whole cluster shares one journey across the box; a whole number of laps keeps the
        // loop seamless, and deeper clouds take the slower lap the way distance does.
        val z = CLOUD_NEAR + 0.6f + scatter(cloud, 31) * (CLOUD_FAR - CLOUD_NEAR - 1.2f)
        val laps = 1 + (cloud % 2)
        val travel = fall(clock, laps, scatter(cloud, 32))
        val x = ((if (direction > 0f) travel else 1f - travel) * 2f - 1f) * CLOUD_SPAN
        // The altitude is chosen so the cluster rides at its level of the visible band at its
        // own depth — vertically the sky is art-directed to fill the band it was given, while
        // size and sideways parallax stay the camera's honest arithmetic. A fixed world
        // altitude instead pins the field to one band height and flies off any other.
        val level = 0.12f + scatter(cloud, 33) * 0.55f
        val y = (band * (1f - level) / focal) * z +
            wave(fall(clock, laps, scatter(cloud, 34))) * 0.02f * z
        val core = 0.34f + scatter(cloud, 35) * 0.30f

        puffs += Puff(x, y, z, core)
        puffs += Puff(x - core * 0.9f, y - core * 0.12f, z, core * 0.62f)
        puffs += Puff(x + core * 0.85f, y - core * 0.10f, z + 0.05f, core * 0.66f)
        puffs += Puff(x - core * 0.30f, y + core * 0.42f, z - 0.04f, core * 0.58f)
        puffs += Puff(x + core * 0.38f, y + core * 0.36f, z + 0.03f, core * 0.52f)
    }

    puffField(puffs, colour, glowTint, glow, alphaScale = 0.8f + 0.5f * cover)
}
