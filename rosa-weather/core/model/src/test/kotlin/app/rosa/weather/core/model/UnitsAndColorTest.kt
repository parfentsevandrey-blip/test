package app.rosa.weather.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnitsAndColorTest {
    @Test
    fun `unit conversions`() {
        val metric = Units()
        assertThat(metric.pressure(1013.25)).isWithin(0.1).of(760.0)
        val us = Units.forCountry("US")
        assertThat(us.temperature(100.0)).isWithin(1e-9).of(212.0)
        assertThat(us.wind(10.0)).isWithin(0.01).of(22.37)
        assertThat(Units.beaufort(0.1)).isEqualTo(0)
        assertThat(Units.beaufort(9.0)).isEqualTo(5)
        assertThat(Units.beaufort(40.0)).isEqualTo(12)
    }

    @Test
    fun `no negative zero`() {
        assertThat(Units().roundedTemperature(-0.3)).isEqualTo(0)
    }

    @Test
    fun `compass points`() {
        assertThat(compassPoint(0)).isEqualTo(0)
        assertThat(compassPoint(359)).isEqualTo(0)
        assertThat(compassPoint(90)).isEqualTo(4)
        assertThat(compassPoint(225)).isEqualTo(10)
    }

    @Test
    fun `oklab lerp keeps endpoints`() {
        val a = Argb.hex(0x3558A2)
        val b = Argb.hex(0xFFB077)
        assertThat(a.lerp(b, 0f)).isEqualTo(a)
        assertThat(a.lerp(b, 1f)).isEqualTo(b)
    }

    @Test
    fun `palettes pick legible ink`() {
        val noonClear = SkyPalette.of(45.0, WeatherVisual.ClearDay)
        val night = SkyPalette.of(-30.0, WeatherVisual.ClearDay)
        val snowyDay = SkyPalette.of(20.0, WeatherVisual.from(WeatherCondition.HeavySnow, 100))
        assertThat(night.isLight).isFalse()
        assertThat(snowyDay.isLight).isTrue()
        assertThat(noonClear.zenith).isNotEqualTo(night.zenith)
    }

    @Test
    fun `wmo mapping`() {
        assertThat(WeatherCondition.fromWmo(0)).isEqualTo(WeatherCondition.Clear)
        assertThat(WeatherCondition.fromWmo(65)).isEqualTo(WeatherCondition.HeavyRain)
        assertThat(WeatherCondition.fromWmo(99)).isEqualTo(WeatherCondition.ThunderstormHail)
        assertThat(WeatherCondition.fromWmo(75).family).isEqualTo(WeatherCondition.Family.Snow)
    }
}
