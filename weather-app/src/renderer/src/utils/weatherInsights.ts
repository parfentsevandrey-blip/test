import { getConditionInfo } from './weatherCondition'
import { toAbsoluteInstant } from './time'
import { formatHour, formatSpeed, formatTemperature } from './units'
import type { TemperatureUnit, WeatherData } from '../types/weather'

const HOUR_MS = 3_600_000

/** What kind of thing an insight is about -- drives the accent color/icon a card shows next to it. */
export type InsightKind = 'precip' | 'pressure' | 'temp-rise' | 'temp-drop' | 'wind' | 'uv' | 'clear' | 'steady'

export interface WeatherInsight {
  kind: InsightKind
  headline: string
}

export type PressureTrend = 'rising' | 'falling' | 'steady'

/** Stable-per-hour pick so phrasing varies across refreshes/days without flickering on every render. */
function pick<T>(options: readonly T[], seed: number): T {
  return options[Math.abs(seed) % options.length]
}

/** A signed temperature delta in the display unit, e.g. "3°" (no sign — callers add the word). */
function formatTempDelta(celsiusDelta: number, unit: TemperatureUnit): string {
  const converted = unit === 'fahrenheit' ? (celsiusDelta * 9) / 5 : celsiusDelta
  return `${Math.round(Math.abs(converted))}°`
}

/**
 * Yesterday's temperature at the same local hour, if the hourly series (which
 * includes one day back via past_days=1) has a matching entry within 30 min.
 */
function yesterdaySameHourTemp(weather: WeatherData, nowMs: number): number | null {
  const targetMs = nowMs - 24 * HOUR_MS
  let best: { diffMs: number; temperature: number } | null = null
  for (const h of weather.hourly) {
    const t = toAbsoluteInstant(h.time, weather.utcOffsetSeconds).getTime()
    const diffMs = Math.abs(t - targetMs)
    if (diffMs > 30 * 60 * 1000) continue
    if (best === null || diffMs < best.diffMs) best = { diffMs, temperature: h.temperature }
  }
  return best?.temperature ?? null
}

/** Pressure change over the last 3 hours (>1 hPa either way = a real trend, not sensor noise). */
export function pressureTrend(weather: WeatherData, nowMs: number): PressureTrend | null {
  const targetMs = nowMs - 3 * HOUR_MS
  let best: { diffMs: number; pressure: number } | null = null
  for (const h of weather.hourly) {
    const t = toAbsoluteInstant(h.time, weather.utcOffsetSeconds).getTime()
    if (t > nowMs) continue
    const diffMs = Math.abs(t - targetMs)
    if (diffMs > 90 * 60 * 1000) continue
    if (best === null || diffMs < best.diffMs) best = { diffMs, pressure: h.pressure }
  }
  if (best === null) return null
  const delta = weather.current.pressure - best.pressure
  if (delta > 1) return 'rising'
  if (delta < -1) return 'falling'
  return 'steady'
}

/**
 * Every notable thing about the next several hours, ranked most-to-least
 * important (precipitation/storms first, then an incoming pressure change,
 * then a real temperature swing, then today's wind/UV extremes, then a
 * clear-skies read, falling back to a yesterday comparison or a generic
 * "steady" line). Built as a ranked LIST rather than a single early-return
 * string so multiple surfaces (the hero card's one-line insight, the
 * WeatherOutlookCard's top two) stay in sync from one source instead of
 * duplicating this scan-and-rank logic per component.
 *
 * Hourly data only carries temperature/precipitation/pressure (see
 * HourlyForecastPoint) -- wind/UV/visibility only exist as a single current
 * reading or a daily max, so those insights compare "today's max" against
 * "right now" rather than scanning an hour-by-hour series like precip/temp do.
 */
export function buildInsights(weather: WeatherData, unit: TemperatureUnit): WeatherInsight[] {
  const nowMs = Date.now()
  const hourSeed = Math.floor(nowMs / HOUR_MS)
  const insights: WeatherInsight[] = []

  const next12 = weather.hourly
    .filter((h) => {
      const t = toAbsoluteInstant(h.time, weather.utcOffsetSeconds).getTime()
      return t > nowMs && t <= nowMs + 12 * HOUR_MS
    })
    .slice(0, 12)

  if (next12.length > 0) {
    const wettest = next12.reduce((a, b) => (b.precipitationProbability > a.precipitationProbability ? b : a))
    if (wettest.precipitationProbability >= 45) {
      const { condition } = getConditionInfo(wettest.weatherCode)
      const pct = Math.round(wettest.precipitationProbability)
      const hour = formatHour(wettest.time)
      if (condition === 'snow') {
        insights.push({
          kind: 'precip',
          headline: pick([`Snow likely around ${hour} — ${pct}%`, `Bundle up — snow moves in around ${hour}`], hourSeed)
        })
      } else if (condition === 'thunderstorm') {
        insights.push({
          kind: 'precip',
          headline: pick([`Storms likely around ${hour} — ${pct}%`, `Thunder rolling in around ${hour}`], hourSeed)
        })
      } else {
        insights.push({
          kind: 'precip',
          headline: pick([`Rain likely around ${hour} — ${pct}%`, `Grab an umbrella before ${hour}`], hourSeed)
        })
      }
    }

    if (pressureTrend(weather, nowMs) === 'falling') {
      insights.push({
        kind: 'pressure',
        headline: pick(['Pressure falling — a change is on the way', 'Pressure dropping, weather may turn'], hourSeed)
      })
    }

    const current = weather.current.temperature
    const hottest = next12.reduce((a, b) => (b.temperature > a.temperature ? b : a))
    const coldest = next12.reduce((a, b) => (b.temperature < a.temperature ? b : a))
    const rise = hottest.temperature - current
    const drop = current - coldest.temperature
    if (rise >= 2 && rise >= drop) {
      const hi = formatTemperature(hottest.temperature, unit)
      const hour = formatHour(hottest.time)
      insights.push({
        kind: 'temp-rise',
        headline: pick([`Warming to ${hi} by ${hour}`, `Warms up to ${hi} later, around ${hour}`], hourSeed)
      })
    } else if (drop >= 2) {
      const lo = formatTemperature(coldest.temperature, unit)
      const hour = formatHour(coldest.time)
      insights.push({
        kind: 'temp-drop',
        headline: pick([`Cooling to ${lo} by ${hour}`, `Temperatures dip to ${lo} by ${hour}`], hourSeed)
      })
    }
  }

  const today = weather.daily[0]
  if (today) {
    // ~4 m/s (~14-15 km/h) headroom between the current reading and today's
    // max reads as "noticeably windier later" without firing on ordinary gust noise.
    if (today.windSpeedMax - weather.current.windSpeed >= 4) {
      const speedUnit = unit === 'fahrenheit' ? 'mph' : 'kmh'
      insights.push({
        kind: 'wind',
        headline: `Windier later — up to ${formatSpeed(today.windSpeedMax, speedUnit)} today`
      })
    }
    if ((today.uvIndexMax ?? 0) >= 8) {
      insights.push({
        kind: 'uv',
        headline: `UV peaks at ${Math.round(today.uvIndexMax ?? 0)} today — wear sunscreen if you're out`
      })
    }
  }

  if (next12.length > 0) {
    const clearish = next12.filter((h) => {
      const c = getConditionInfo(h.weatherCode).condition
      return c === 'clear' || c === 'partly-cloudy'
    })
    if (clearish.length >= next12.length * 0.7) {
      insights.push({
        kind: 'clear',
        headline: weather.current.isDay
          ? pick(['Clear evening ahead', 'Clear skies through the evening'], hourSeed)
          : pick(['Clear night ahead', 'Clear skies overnight'], hourSeed)
      })
    }
  }

  if (insights.length === 0) {
    // Nothing dramatic ahead — this is the app's blandest fallback, so reach
    // for yesterday's same-hour reading (already fetched via past_days=1,
    // otherwise unused) before settling for a generic "steady" line.
    const yesterday = yesterdaySameHourTemp(weather, nowMs)
    if (yesterday !== null) {
      const delta = weather.current.temperature - yesterday
      if (Math.abs(delta) >= 2) {
        const word = delta > 0 ? 'warmer' : 'cooler'
        insights.push({ kind: 'steady', headline: `${formatTempDelta(delta, unit)} ${word} than this time yesterday` })
      }
    }
  }

  if (insights.length === 0) {
    insights.push({
      kind: 'steady',
      headline: pick(['Steady conditions for the next few hours', 'A quiet stretch of weather ahead'], hourSeed)
    })
  }

  return insights
}
