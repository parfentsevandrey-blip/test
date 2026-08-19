package app.quire.weather

import androidx.compose.material3.lightColorScheme
import app.quire.weather.ui.SkyMoment
import app.quire.weather.ui.skyColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The sky's arithmetic, asked at chosen moments.
 *
 * Everything the living sky draws — where the sun is, whether the moon is out and how much of
 * it, what colour the top of the page should be — is decided here, by pure functions over the
 * clock and today's sunrise and sunset. So the questions worth asking are askable: what does
 * half past four in the morning look like, and is a January the third moon actually full.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkyMomentTest {

    private val sunrise: LocalDateTime = LocalDate.of(2026, 8, 19).atTime(5, 12)
    private val sunset: LocalDateTime = LocalDate.of(2026, 8, 19).atTime(20, 41)

    private fun at(hour: Int, minute: Int): SkyMoment =
        SkyMoment.of(LocalDate.of(2026, 8, 19).atTime(hour, minute), sunrise, sunset, day = true)

    @Test
    fun `noon is day, half way through it`() {
        val noon = at(13, 0)
        assertNull("the sun was down at one in the afternoon", noon.night)
        assertTrue("no daylight fraction at noon", noon.daylight != null)
        assertEquals(0.50f, noon.daylight!!, 0.03f)
        assertEquals(1f, noon.light, 0.001f)
        assertEquals(0f, noon.glow, 0.001f)
    }

    @Test
    fun `the small hours are night, and dawn is a ramp, not a switch`() {
        val deep = at(1, 30)
        assertNull("the sun was up at half past one", deep.daylight)
        assertEquals(0f, deep.light, 0.001f)

        // Forty minutes before sunrise: still night by the fraction, but the light is climbing
        // and the horizon has begun to glow.
        val dawn = at(4, 30)
        assertNull(dawn.daylight)
        assertTrue("dawn has no night fraction", dawn.night != null)
        assertTrue("the light had not started climbing before sunrise", dawn.light > 0.05f)
        assertTrue("the light was already full before the sun rose", dawn.light < 0.5f)
        assertTrue("no glow at the edge of sunrise", dawn.glow > 0f)
    }

    @Test
    fun `sunset itself is the golden hour's peak`() {
        val setting = at(20, 41)
        assertEquals(1f, setting.glow, 0.001f)
        assertTrue("the light had already gone at sunset", setting.light > 0.3f)

        val evening = at(23, 0)
        assertEquals(0f, evening.glow, 0.001f)
        assertEquals(0f, evening.light, 0.001f)
        // Night length is 24h minus the 15h29m day; 2h19m past sunset is about a quarter in.
        assertEquals(0.27f, evening.night!!, 0.05f)
    }

    @Test
    fun `without the times, the one known bit is worn plainly`() {
        val day = SkyMoment.of(LocalDateTime.of(2026, 8, 19, 13, 0), null, null, day = true)
        assertEquals(1f, day.light, 0.001f)
        assertEquals(0.5f, day.daylight!!, 0.001f)
        val night = SkyMoment.of(LocalDateTime.of(2026, 8, 19, 1, 0), null, null, day = false)
        assertEquals(0f, night.light, 0.001f)
        assertEquals(0.5f, night.night!!, 0.001f)
    }

    /** Against the almanac: January 2026 was full on the 3rd and new on the 18th. */
    @Test
    fun `the calendar fold lands on the real moon`() {
        val full = SkyMoment.of(LocalDate.of(2026, 1, 3).atTime(23, 0), null, null, day = false)
        assertEquals(0.5f, full.moonPhase, 0.04f)

        val new = SkyMoment.of(LocalDate.of(2026, 1, 18).atTime(23, 0), null, null, day = false)
        assertTrue(
            "January 18th 2026 read as ${new.moonPhase}, not a new moon",
            new.moonPhase < 0.05f || new.moonPhase > 0.95f,
        )
    }

    @Test
    fun `the wash tells night from noon from the golden hour`() {
        val scheme = lightColorScheme()
        val night = skyColour(scheme, at(1, 30))
        val noon = skyColour(scheme, at(13, 0))
        val golden = skyColour(scheme, at(20, 41))

        assertTrue("night and noon wear the same colour", night != noon)
        assertTrue("the golden hour is just the day again", golden != noon)
        assertTrue("the golden hour is just the night again", golden != night)

        fun luma(c: androidx.compose.ui.graphics.Color) =
            c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f
        assertTrue("the night sky is not darker than the noon one", luma(night) < luma(noon))
    }
}
