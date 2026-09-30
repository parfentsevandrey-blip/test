package app.rosa.weather.core.designsystem.sky

import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.momentAt
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The light in the air: motes and the lens's flare only in clear sunshine, diamond dust only in a hard frost. */
class SkyAirTest {
    private fun moment(scenario: SampleForecast.Scenario, epoch: Long): ForecastMoment =
        SampleForecast.create(scenario, nowEpochSeconds = epoch).momentAt(epoch)

    private fun sky(moment: ForecastMoment, appearance: Appearance = Appearance.Auto): SkyParams =
        SkyParams.from(moment, SkyPalette.of(moment.sun.elevation, moment.visual, moment.moonPhase.illumination), appearance)

    @Test
    fun clearSunshineLightsTheAir() {
        val noon = sky(moment(SampleForecast.Scenario.SunnyMild, SUNNY_NOON))
        assertThat(noon.sunlitAir).isGreaterThan(0.5f)
        assertThat(noon.diamondDust).isEqualTo(0f)
    }

    @Test
    fun rainAndNightHaveNone() {
        val rain = sky(moment(SampleForecast.Scenario.RainyAfternoon, RAINY_AFTERNOON))
        assertThat(rain.sunlitAir).isEqualTo(0f)
        assertThat(rain.diamondDust).isEqualTo(0f)
        val night = sky(moment(SampleForecast.Scenario.ClearNight, CLEAR_NIGHT))
        assertThat(night.sunlitAir).isEqualTo(0f)
    }

    /** A fixed mood draws no real sun, so nothing flares or glints in its light. */
    @Test
    fun fixedMoodsHaveNoSunlitAir() {
        val noon = moment(SampleForecast.Scenario.SunnyMild, SUNNY_NOON)
        Appearance.entries.filter { it != Appearance.Auto }.forEach {
            assertThat(sky(noon, it).sunlitAir).isEqualTo(0f)
        }
    }

    @Test
    fun diamondDustNeedsAHardFrost() {
        val noon = moment(SampleForecast.Scenario.SunnyMild, SUNNY_NOON)
        assertThat(sky(noon.copy(temperature = -2.0)).diamondDust).isEqualTo(0f)
        assertThat(sky(noon.copy(temperature = -12.0)).diamondDust).isGreaterThan(0.5f)
        assertThat(sky(noon.copy(temperature = -20.0)).diamondDust).isEqualTo(sky(noon.copy(temperature = -14.0)).diamondDust)
    }

    private companion object {
        const val SUNNY_NOON = 1_758_621_600L
        const val RAINY_AFTERNOON = 1_758_637_800L
        const val CLEAR_NIGHT = 1_758_664_800L
    }
}
