package app.rosa.weather.core.data.network

import app.rosa.weather.core.model.AirQuality
import app.rosa.weather.core.model.CurrentConditions
import app.rosa.weather.core.model.DailyPoint
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.HourlyPoint
import app.rosa.weather.core.model.NowcastPoint
import app.rosa.weather.core.model.Place
import kotlin.math.roundToInt

/** Converts wire DTOs to the domain model, tolerating the sparse `null`s Open-Meteo may return. */
internal object ForecastMapper {

    fun map(placeId: String, response: ForecastResponse, air: AirQuality?, fetchedAt: Long): Forecast {
        val hourly = mapHourly(response.hourly)
        val current = mapCurrent(response.current, hourly, fetchedAt)
        return Forecast(
            placeId = placeId,
            latitude = response.latitude,
            longitude = response.longitude,
            timezone = response.timezone,
            utcOffsetSeconds = response.utcOffsetSeconds,
            fetchedAt = fetchedAt,
            current = current,
            hourly = hourly,
            daily = mapDaily(response.daily),
            nowcast = mapNowcast(response.minutely15),
            air = air,
        )
    }

    fun mapAir(response: AirQualityResponse): AirQuality? {
        val c = response.current ?: return null
        return AirQuality(
            time = c.time,
            europeanAqi = c.europeanAqi?.roundToInt(),
            usAqi = c.usAqi?.roundToInt(),
            pm25 = c.pm25,
            pm10 = c.pm10,
            ozone = c.ozone,
        )
    }

    fun mapPlace(result: GeocodingResult): Place = Place(
        id = "geo:${result.id}",
        name = result.name,
        latitude = result.latitude,
        longitude = result.longitude,
        region = result.admin1 ?: result.admin2,
        country = result.country,
        countryCode = result.countryCode,
        timezone = result.timezone,
    )

    private fun mapCurrent(dto: CurrentDto?, hourly: List<HourlyPoint>, fetchedAt: Long): CurrentConditions {
        // Fall back to the hourly slot if "current" is missing entirely (rare, but seen on outages).
        val fallback = hourly.lastOrNull { it.time <= fetchedAt } ?: hourly.firstOrNull()
        return CurrentConditions(
            time = dto?.time ?: fallback?.time ?: fetchedAt,
            temperature = dto?.temperature ?: fallback?.temperature ?: 0.0,
            apparentTemperature = dto?.apparentTemperature ?: dto?.temperature ?: fallback?.apparentTemperature ?: 0.0,
            humidity = dto?.humidity?.roundToInt() ?: fallback?.humidity ?: 0,
            dewPoint = dto?.dewPoint,
            weatherCode = dto?.weatherCode ?: fallback?.weatherCode ?: 0,
            isDay = (dto?.isDay ?: 1) == 1,
            cloudCover = dto?.cloudCover?.roundToInt() ?: fallback?.cloudCover ?: 0,
            pressure = dto?.pressure ?: fallback?.pressure ?: 1013.25,
            windSpeed = dto?.windSpeed ?: fallback?.windSpeed ?: 0.0,
            windGusts = dto?.windGusts ?: dto?.windSpeed ?: fallback?.windGusts ?: 0.0,
            windDirection = dto?.windDirection?.roundToInt() ?: fallback?.windDirection ?: 0,
            precipitation = dto?.precipitation ?: 0.0,
            uvIndex = dto?.uvIndex ?: fallback?.uvIndex ?: 0.0,
            visibility = dto?.visibility ?: fallback?.visibility,
        )
    }

    private fun mapHourly(dto: HourlyDto?): List<HourlyPoint> {
        if (dto == null) return emptyList()
        return dto.time.indices.mapNotNull { i ->
            val temperature = dto.temperature.getOrNull(i) ?: return@mapNotNull null
            val wind = dto.windSpeed.getOrNull(i) ?: 0.0
            HourlyPoint(
                time = dto.time[i],
                temperature = temperature,
                apparentTemperature = dto.apparentTemperature.getOrNull(i) ?: temperature,
                precipitationProbability = dto.precipitationProbability.getOrNull(i)?.roundToInt() ?: 0,
                precipitation = dto.precipitation.getOrNull(i) ?: 0.0,
                weatherCode = dto.weatherCode.getOrNull(i) ?: 0,
                cloudCover = dto.cloudCover.getOrNull(i)?.roundToInt() ?: 0,
                windSpeed = wind,
                windDirection = dto.windDirection.getOrNull(i)?.roundToInt() ?: 0,
                windGusts = dto.windGusts.getOrNull(i) ?: wind,
                uvIndex = dto.uvIndex.getOrNull(i) ?: 0.0,
                isDay = (dto.isDay.getOrNull(i) ?: 1) == 1,
                humidity = dto.humidity.getOrNull(i)?.roundToInt() ?: 0,
                visibility = dto.visibility.getOrNull(i),
                pressure = dto.pressure.getOrNull(i),
            )
        }
    }

    private fun mapDaily(dto: DailyDto?): List<DailyPoint> {
        if (dto == null) return emptyList()
        return dto.time.indices.mapNotNull { i ->
            val max = dto.temperatureMax.getOrNull(i) ?: return@mapNotNull null
            val min = dto.temperatureMin.getOrNull(i) ?: return@mapNotNull null
            DailyPoint(
                time = dto.time[i],
                weatherCode = dto.weatherCode.getOrNull(i) ?: 0,
                temperatureMax = max,
                temperatureMin = min,
                apparentMax = dto.apparentMax.getOrNull(i),
                apparentMin = dto.apparentMin.getOrNull(i),
                sunrise = dto.sunrise.getOrNull(i),
                sunset = dto.sunset.getOrNull(i),
                precipitationSum = dto.precipitationSum.getOrNull(i) ?: 0.0,
                precipitationProbabilityMax = dto.precipitationProbabilityMax.getOrNull(i)?.roundToInt() ?: 0,
                windSpeedMax = dto.windSpeedMax.getOrNull(i) ?: 0.0,
                windDirectionDominant = dto.windDirectionDominant.getOrNull(i)?.roundToInt(),
                uvIndexMax = dto.uvIndexMax.getOrNull(i) ?: 0.0,
                daylightDuration = dto.daylightDuration.getOrNull(i),
            )
        }
    }

    private fun mapNowcast(dto: Minutely15Dto?): List<NowcastPoint> {
        if (dto == null) return emptyList()
        return dto.time.indices.map { i ->
            NowcastPoint(
                time = dto.time[i],
                precipitation = dto.precipitation.getOrNull(i) ?: 0.0,
                weatherCode = dto.weatherCode.getOrNull(i),
            )
        }
    }
}
