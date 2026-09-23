package app.rosa.weather.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForecastMomentTest {
    private val now = 1_758_628_800L
    private val forecast = SampleForecast.create(SampleForecast.Scenario.RainyAfternoon, nowEpochSeconds = now)

    @Test
    fun `moment near fetch time trusts current conditions`() {
        val moment = forecast.momentAt(forecast.current.time)
        assertThat(moment.temperature).isWithin(1e-9).of(forecast.current.temperature)
        assertThat(moment.weatherCode).isEqualTo(forecast.current.weatherCode)
    }

    @Test
    fun `moment hours later follows the hourly forecast`() {
        val later = now + 5 * 3600 + 1800
        val moment = forecast.momentAt(later)
        val idx = forecast.hourIndexAt(later)
        val a = forecast.hourly[idx]
        val b = forecast.hourly[idx + 1]
        assertThat(moment.temperature).isWithin(1e-6).of((a.temperature + b.temperature) / 2)
        assertThat(moment.weatherCode).isEqualTo(a.weatherCode)
    }

    @Test
    fun `hoursFrom starts with the running hour`() {
        val hours = forecast.hoursFrom(now + 1200)
        assertThat(hours.first().time).isAtMost(now + 1200)
        assertThat(hours.first().time + 3600).isGreaterThan(now + 1200)
    }

    @Test
    fun `headline announces rain from nowcast`() {
        val moment = forecast.momentAt(now)
        val headline = Headlines.pick(forecast, moment)
        assertThat(headline).isInstanceOf(Headline.PrecipitationStarts::class.java)
        assertThat((headline as Headline.PrecipitationStarts).minutes).isIn(1..60)
    }

    @Test
    fun `day lookup returns the local calendar day`() {
        val day = forecast.dayAt(now)!!
        assertThat(day.time).isAtMost(now)
        assertThat(day.time + 86_400).isGreaterThan(now)
    }
}
