package app.papersky.weather

import app.papersky.weather.core.data.AppJson
import app.papersky.weather.core.data.ForecastDto
import app.papersky.weather.core.data.toForecast
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place

object Fixtures {
    val moscow = Place(id = Place.HERE, name = "Москва", country = "Россия", latitude = 55.75, longitude = 37.62, timezone = "Europe/Moscow", isDeviceLocation = true)

    fun dto(): ForecastDto {
        val text = javaClass.classLoader!!.getResource("forecast_moscow.json")!!.readText()
        return AppJson.decodeFromString(ForecastDto.serializer(), text)
    }

    /** The recorded Moscow forecast, shifted so its "current" observation is [nowMillis]. */
    fun moscow(nowMillis: Long, placeId: String = Place.HERE): Forecast {
        val f = dto().toForecast(placeId, nowMillis)
        val shift = nowMillis / 1000 - f.current.time
        val dayShift = Math.floorDiv(shift, 86_400L) * 86_400L
        return f.copy(
            current = f.current.copy(time = f.current.time + shift),
            hourly = f.hourly.map { it.copy(time = it.time + shift) },
            // Keep daily rows on local midnights.
            daily = f.daily.map { it.copy(date = it.date + dayShift, sunrise = it.sunrise + dayShift, sunset = it.sunset + dayShift) },
            nowcast = f.nowcast.map { it.copy(time = it.time + shift) },
        )
    }
}
