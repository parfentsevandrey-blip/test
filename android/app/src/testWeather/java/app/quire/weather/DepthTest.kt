package app.quire.weather

import app.quire.weather.ui.projected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule of the cloud field's camera, held to exactly.
 *
 * Every question the 3D sky asks — where a puff lands, how big it draws — goes through the same
 * perspective division, so the division is tested as arithmetic rather than as pixels: a thing
 * twice as far away must be exactly half as big and half as far off-centre, no engine involved.
 */
class DepthTest {

    @Test
    fun `twice the depth is exactly half of everything`() {
        val near = projected(offAxis = 1f, depth = 2f, focal = 700f)
        val far = projected(offAxis = 1f, depth = 4f, focal = 700f)
        assertEquals(near, far * 2f, 0.0001f)
    }

    @Test
    fun `projection is linear in the offset and keeps its sign`() {
        val one = projected(offAxis = 1f, depth = 3f, focal = 700f)
        val two = projected(offAxis = 2f, depth = 3f, focal = 700f)
        assertEquals(two, one * 2f, 0.0001f)
        assertTrue("a leftwards offset projected rightwards", projected(-1f, 3f, 700f) < 0f)
    }

    @Test
    fun `at the focal plane the world maps one to one`() {
        assertEquals(700f, projected(offAxis = 1f, depth = 1f, focal = 700f), 0.0001f)
    }
}
