export interface GeoLocation {
  name: string
  country: string
  admin1?: string
  latitude: number
  longitude: number
  timezone: string
}

export interface CurrentWeather {
  time: string
  temperature: number
  apparentTemperature: number
  humidity: number
  isDay: boolean
  precipitation: number
  weatherCode: number
  cloudCover: number
  pressure: number
  windSpeed: number
  windDirection: number
  windGusts: number
  uvIndex: number | null
}

export interface HourlyForecastPoint {
  time: string
  temperature: number
  precipitationProbability: number
  weatherCode: number
  isDay: boolean
}

export interface DailyForecastPoint {
  date: string
  weatherCode: number
  tempMax: number
  tempMin: number
  precipitationProbabilityMax: number
  sunrise: string
  sunset: string
  windSpeedMax: number
  uvIndexMax: number | null
}

export interface SunTimes {
  sunriseToday: string
  sunsetToday: string
  sunsetYesterday: string
  sunriseTomorrow: string
}

export interface WeatherData {
  location: GeoLocation
  current: CurrentWeather
  hourly: HourlyForecastPoint[]
  /** Always exactly today + the next 6 days (yesterday, fetched only for sun-position math, is not included here). */
  daily: DailyForecastPoint[]
  sunTimes: SunTimes
  fetchedAt: number
}

export type TemperatureUnit = 'celsius' | 'fahrenheit'
export type SpeedUnit = 'kmh' | 'mph'
