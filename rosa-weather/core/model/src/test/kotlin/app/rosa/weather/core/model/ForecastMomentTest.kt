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
    fun `chance for an hour comes from the following slot`() {
        // Open-Meteo reports precipitation for the *preceding* hour.
        val start = forecast.firstHourIndexFrom(now + 1200)
        assertThat(forecast.hoursFrom(now + 1200).first()).isEqualTo(forecast.hourly[start])
        assertThat(forecast.chanceForHourStarting(start)).isEqualTo(forecast.hourly[start + 1].precipitationProbability)
        val last = forecast.hourly.lastIndex
        assertThat(forecast.chanceForHourStarting(last)).isEqualTo(forecast.hourly[last].precipitationProbability)
    }

    @Test
    fun `later precipitation is never announced in the past`() {
        val moment = forecast.momentAt(now)
        val headline = Headlines.pick(forecast, moment)
        if (headline is Headline.PrecipitationLater) assertThat(headline.atEpochSeconds).isGreaterThan(now)
    }

    @Test
    fun `days start with today even on a 25-hour day`() {
        val days = forecast.daysFrom(now)
        assertThat(days.first()).isEqualTo(forecast.dayAt(now))
        // Just before the next local midnight we are still on the same day.
        val nextMidnight = forecast.daily[forecast.daily.indexOf(days.first()) + 1].time
        assertThat(forecast.daysFrom(nextMidnight - 1).first()).isEqualTo(days.first())
        assertThat(forecast.daysFrom(nextMidnight).first().time).isEqualTo(nextMidnight)
    }

    @Test
    fun `day lookup returns the local calendar day`() {
        val day = forecast.dayAt(now)!!
        assertThat(day.time).isAtMost(now)
        assertThat(day.time + 86_400).isGreaterThan(now)
    }

    @Test
    fun `the picture changes when the rain starts, to within half a minute`() {
        // Overcast now, rain in the hourly forecast from the afternoon.
        val start = forecast.nextSceneChange(now)!!
        assertThat(forecast.momentAt(now).sceneKind and 1).isEqualTo(0)
        assertThat(forecast.momentAt(start).sceneKind and 1).isEqualTo(1)
        assertThat(forecast.momentAt(start - 30).sceneKind and 1).isEqualTo(0)
        // …and again when it stops, in the evening.
        val stop = forecast.nextSceneChange(start, horizonSeconds = 8 * 3600)!!
        assertThat(stop).isGreaterThan(start)
        assertThat(forecast.momentAt(stop).sceneKind and 1).isEqualTo(0)
    }

    @Test
    fun `a steady sky has no change to wait for`() {
        val night = SampleForecast.create(SampleForecast.Scenario.ClearNight, nowEpochSeconds = now)
        assertThat(night.nextSceneChange(now, horizonSeconds = 3600)).isNull()
    }

    @Test
    fun `frost grows below freezing and mist with rain`() {
        val snowy = SampleForecast.create(SampleForecast.Scenario.SnowyCold, nowEpochSeconds = now)
        assertThat(snowy.momentAt(now).paneFrost).isGreaterThan(0.5f)
        assertThat(forecast.momentAt(now).paneFrost).isEqualTo(0f)
        val raining = forecast.nextSceneChange(now)!!
        assertThat(forecast.momentAt(raining).paneMist).isAtLeast(0.25f)
    }
}
