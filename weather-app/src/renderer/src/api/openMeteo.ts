import type { GeoLocation, WeatherData } from '../types/weather'

const FORECAST_URL = 'https://api.open-meteo.com/v1/forecast'

const CURRENT_VARS = [
  'temperature_2m',
  'relative_humidity_2m',
  'apparent_temperature',
  'is_day',
  'precipitation',
  'weather_code',
  'cloud_cover',
  'pressure_msl',
  'wind_speed_10m',
  'wind_direction_10m',
  'wind_gusts_10m'
].join(',')

const HOURLY_VARS = ['temperature_2m', 'precipitation_probability', 'weather_code', 'is_day', 'uv_index'].join(
  ','
)

const DAILY_VARS = [
  'weather_code',
  'temperature_2m_max',
  'temperature_2m_min',
  'sunrise',
  'sunset',
  'precipitation_probability_max',
  'wind_speed_10m_max',
  'uv_index_max'
].join(',')

interface RawForecastResponse {
  utc_offset_seconds: number
  current: {
    time: string
    temperature_2m: number
    relative_humidity_2m: number
    apparent_temperature: number
    is_day: number
    precipitation: number
    weather_code: number
    cloud_cover: number
    pressure_msl: number
    wind_speed_10m: number
    wind_direction_10m: number
    wind_gusts_10m: number
  }
  hourly: {
    time: string[]
    temperature_2m: number[]
    precipitation_probability: number[]
    weather_code: number[]
    is_day: number[]
    uv_index: number[]
  }
  daily: {
    time: string[]
    weather_code: number[]
    temperature_2m_max: number[]
    temperature_2m_min: number[]
    sunrise: string[]
    sunset: string[]
    precipitation_probability_max: number[]
    wind_speed_10m_max: number[]
    uv_index_max: number[]
  }
}

function findHourlyUvIndex(hourly: RawForecastResponse['hourly'], currentIsoTime: string): number | null {
  const flooredHour = `${currentIsoTime.slice(0, 13)}:00`
  const index = hourly.time.indexOf(flooredHour)
  return index >= 0 ? hourly.uv_index[index] : null
}

/**
 * Fetches current conditions, an hourly forecast and a 7-day daily forecast
 * from Open-Meteo — a free, open weather API that requires no API key.
 * Requests one extra day in the past solely to derive yesterday's sunset,
 * which the cinematic scene needs for smooth pre-dawn sun/moon positioning.
 */
export async function fetchWeatherData(location: GeoLocation, signal?: AbortSignal): Promise<WeatherData> {
  const url = new URL(FORECAST_URL)
  url.searchParams.set('latitude', location.latitude.toString())
  url.searchParams.set('longitude', location.longitude.toString())
  url.searchParams.set('current', CURRENT_VARS)
  url.searchParams.set('hourly', HOURLY_VARS)
  url.searchParams.set('daily', DAILY_VARS)
  url.searchParams.set('timezone', 'auto')
  url.searchParams.set('temperature_unit', 'celsius')
  url.searchParams.set('wind_speed_unit', 'ms')
  url.searchParams.set('past_days', '1')
  url.searchParams.set('forecast_days', '7')

  const response = await fetch(url, { signal })
  if (!response.ok) {
    throw new Error(`Weather request failed (${response.status})`)
  }
  const raw = (await response.json()) as RawForecastResponse

  const upcomingStart = 1 // index 0 is yesterday (past_days=1)

  return {
    location,
    fetchedAt: Date.now(),
    current: {
      time: raw.current.time,
      temperature: raw.current.temperature_2m,
      apparentTemperature: raw.current.apparent_temperature,
      humidity: raw.current.relative_humidity_2m,
      isDay: raw.current.is_day === 1,
      precipitation: raw.current.precipitation,
      weatherCode: raw.current.weather_code,
      cloudCover: raw.current.cloud_cover,
      pressure: raw.current.pressure_msl,
      windSpeed: raw.current.wind_speed_10m,
      windDirection: raw.current.wind_direction_10m,
      windGusts: raw.current.wind_gusts_10m,
      uvIndex: findHourlyUvIndex(raw.hourly, raw.current.time)
    },
    hourly: raw.hourly.time.map((time, i) => ({
      time,
      temperature: raw.hourly.temperature_2m[i],
      precipitationProbability: raw.hourly.precipitation_probability[i],
      weatherCode: raw.hourly.weather_code[i],
      isDay: raw.hourly.is_day[i] === 1
    })),
    daily: raw.daily.time.slice(upcomingStart).map((date, offset) => {
      const i = offset + upcomingStart
      return {
        date,
        weatherCode: raw.daily.weather_code[i],
        tempMax: raw.daily.temperature_2m_max[i],
        tempMin: raw.daily.temperature_2m_min[i],
        precipitationProbabilityMax: raw.daily.precipitation_probability_max[i],
        sunrise: raw.daily.sunrise[i],
        sunset: raw.daily.sunset[i],
        windSpeedMax: raw.daily.wind_speed_10m_max[i],
        uvIndexMax: raw.daily.uv_index_max[i] ?? null
      }
    }),
    sunTimes: {
      sunsetYesterday: raw.daily.sunset[0],
      sunriseToday: raw.daily.sunrise[1],
      sunsetToday: raw.daily.sunset[1],
      sunriseTomorrow: raw.daily.sunrise[2]
    },
    utcOffsetSeconds: raw.utc_offset_seconds
  }
}
