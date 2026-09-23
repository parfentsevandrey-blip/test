package app.rosa.weather.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class AstronomyTest {
    private fun epoch(iso: String) = Instant.parse(iso).epochSecond

    @Test
    fun `summer solstice solar noon in Moscow is about 57 degrees high`() {
        val sun = Astronomy.sun(epoch("2024-06-21T09:30:00Z"), 55.7558, 37.6173)
        assertThat(sun.elevation).isWithin(1.0).of(57.7)
        assertThat(sun.azimuth).isWithin(5.0).of(180.0)
    }

    @Test
    fun `sun is below the horizon at local midnight`() {
        val sun = Astronomy.sun(epoch("2024-12-21T21:00:00Z"), 55.7558, 37.6173)
        assertThat(sun.elevation).isLessThan(-40.0)
    }

    @Test
    fun `moon phase matches known full and new moons`() {
        val full = Astronomy.moonPhase(epoch("2024-04-23T23:49:00Z"))
        assertThat(full.illumination).isGreaterThan(0.97)
        val new = Astronomy.moonPhase(epoch("2024-04-08T18:21:00Z"))
        assertThat(new.illumination).isLessThan(0.03)
    }

    @Test
    fun `day phase thresholds`() {
        assertThat(DayPhase.fromSunElevation(-20.0)).isEqualTo(DayPhase.Night)
        assertThat(DayPhase.fromSunElevation(-4.0)).isEqualTo(DayPhase.BlueHour)
        assertThat(DayPhase.fromSunElevation(3.0)).isEqualTo(DayPhase.GoldenHour)
        assertThat(DayPhase.fromSunElevation(30.0)).isEqualTo(DayPhase.Day)
    }
}
