import './CurrentWeatherCard.css'
import { useEffect, useMemo, useState } from 'react'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { LocationPinIcon } from './icons'
import { getConditionInfo } from '../utils/weatherCondition'
import { celsiusTo, formatHour, formatTemperature } from '../utils/units'
import { toAbsoluteInstant } from '../utils/time'
import { useCountUp } from '../hooks/useCountUp'
import type { TemperatureUnit, WeatherData } from '../types/weather'

const STAT_ICON_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.5,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true
}

const HOUR_MS = 3_600_000

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

type PressureTrend = 'rising' | 'falling' | 'steady'

/** Pressure change over the last 3 hours (>1 hPa either way = a real trend, not sensor noise). */
function pressureTrend(weather: WeatherData, nowMs: number): PressureTrend | null {
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
 * The selected city's wall clock, ticking every second. Returns a Date whose
 * *UTC* fields hold the city's local time (absolute now + the city's UTC
 * offset), so callers must format it with timeZone: 'UTC'.
 */
function useCityClock(utcOffsetSeconds: number): Date {
  const [shifted, setShifted] = useState(() => new Date(Date.now() + utcOffsetSeconds * 1000))

  useEffect(() => {
    const update = (): void => setShifted(new Date(Date.now() + utcOffsetSeconds * 1000))
    update()
    const id = setInterval(update, 1000)
    return () => clearInterval(id)
  }, [utcOffsetSeconds])

  return shifted
}

/**
 * One short auto-generated sentence about the most notable thing in the next
 * 12 hours: a likely precipitation window first, then a >=2°C temperature
 * swing, then a sky-condition fallback.
 */
function buildInsight(weather: WeatherData, unit: TemperatureUnit): string {
  const nowMs = Date.now()
  const hourSeed = Math.floor(nowMs / HOUR_MS)
  const next12 = weather.hourly
    .filter((h) => {
      const t = toAbsoluteInstant(h.time, weather.utcOffsetSeconds).getTime()
      return t > nowMs && t <= nowMs + 12 * HOUR_MS
    })
    .slice(0, 12)

  if (next12.length === 0) return 'Conditions holding steady'

  const wettest = next12.reduce((a, b) => (b.precipitationProbability > a.precipitationProbability ? b : a))
  if (wettest.precipitationProbability >= 45) {
    const { condition } = getConditionInfo(wettest.weatherCode)
    const pct = Math.round(wettest.precipitationProbability)
    const hour = formatHour(wettest.time)
    if (condition === 'snow') {
      return pick([`Snow likely around ${hour} — ${pct}%`, `Bundle up — snow moves in around ${hour}`], hourSeed)
    }
    if (condition === 'thunderstorm') {
      return pick([`Storms likely around ${hour} — ${pct}%`, `Thunder rolling in around ${hour}`], hourSeed)
    }
    return pick([`Rain likely around ${hour} — ${pct}%`, `Grab an umbrella before ${hour}`], hourSeed)
  }

  const current = weather.current.temperature
  const hottest = next12.reduce((a, b) => (b.temperature > a.temperature ? b : a))
  const coldest = next12.reduce((a, b) => (b.temperature < a.temperature ? b : a))
  const rise = hottest.temperature - current
  const drop = current - coldest.temperature
  if (rise >= 2 && rise >= drop) {
    const hi = formatTemperature(hottest.temperature, unit)
    const hour = formatHour(hottest.time)
    return pick([`Warming to ${hi} by ${hour}`, `Warms up to ${hi} later, around ${hour}`], hourSeed)
  }
  if (drop >= 2) {
    const lo = formatTemperature(coldest.temperature, unit)
    const hour = formatHour(coldest.time)
    return pick([`Cooling to ${lo} by ${hour}`, `Temperatures dip to ${lo} by ${hour}`], hourSeed)
  }

  const clearish = next12.filter((h) => {
    const c = getConditionInfo(h.weatherCode).condition
    return c === 'clear' || c === 'partly-cloudy'
  })
  if (clearish.length >= next12.length * 0.7) {
    return weather.current.isDay
      ? pick(['Clear evening ahead', 'Clear skies through the evening'], hourSeed)
      : pick(['Clear night ahead', 'Clear skies overnight'], hourSeed)
  }

  // Nothing dramatic ahead — this is the app's blandest fallback, so reach for
  // yesterday's same-hour reading (already fetched via past_days=1, otherwise
  // unused) before settling for a generic "steady" line.
  const yesterday = yesterdaySameHourTemp(weather, nowMs)
  if (yesterday !== null) {
    const delta = current - yesterday
    if (Math.abs(delta) >= 2) {
      const word = delta > 0 ? 'warmer' : 'cooler'
      return `${formatTempDelta(delta, unit)} ${word} than this time yesterday`
    }
  }

  return pick(['Steady conditions for the next few hours', 'A quiet stretch of weather ahead'], hourSeed)
}

export function CurrentWeatherCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  // Hooks run unconditionally (before the null guard) to satisfy hook rules.
  const cityClock = useCityClock(weather?.utcOffsetSeconds ?? 0)
  const targetTemp = weather ? Math.round(celsiusTo(unit, weather.current.temperature)) : 0
  const animatedTemp = useCountUp(targetTemp)
  const insight = useMemo(() => (weather ? buildInsight(weather, unit) : ''), [weather, unit])

  if (!weather) return null

  const { condition, label } = getConditionInfo(weather.current.weatherCode)
  const { name, admin1, country } = weather.location
  const placeSub = [admin1, country].filter(Boolean).join(' · ')

  const today = weather.daily.at(0)
  const pressure = `${Math.round(weather.current.pressure)} hPa`
  const trend = pressureTrend(weather, Date.now())

  // City wall clock is encoded in the Date's UTC fields (see useCityClock).
  const clockDay = cityClock.toLocaleDateString('en-US', { weekday: 'long', timeZone: 'UTC' })
  const clockTime = cityClock.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'UTC'
  })

  const shownTemp = Math.round(animatedTemp)
  // Reserve the wider of current/final digit counts so the count-up never
  // shifts the layout (tabular numerals make every digit 1ch wide).
  const numeralCh = Math.max(String(shownTemp).length, String(targetTemp).length)
  const unitLetter = unit === 'fahrenheit' ? 'F' : 'C'

  return (
    <BentoCard span="bento-hero">
      <div className="hero-current">
        <div className="card-title hero-title">
          <LocationPinIcon />
          Current Conditions
        </div>

        <div className="hero-place">
          <div className="hero-place-name">{name}</div>
          {placeSub && <div className="hero-place-sub">{placeSub}</div>}
          <div className="hero-clock">
            <span className="hero-clock-dot" aria-hidden="true" />
            <span className="hero-clock-day">{clockDay}</span>
            <span className="hero-clock-sep" aria-hidden="true">
              ·
            </span>
            <span className="hero-clock-time">{clockTime}</span>
          </div>
        </div>

        <div className="hero-main">
          <span
            className="hero-temp"
            role="img"
            aria-label={`${targetTemp} degrees ${unit === 'fahrenheit' ? 'Fahrenheit' : 'Celsius'}`}
          >
            <span
              className="hero-temp-numeral"
              style={{ minWidth: `${numeralCh}ch` }}
              aria-hidden="true"
            >
              {shownTemp}
            </span>
            <span className="hero-temp-unit" aria-hidden="true">
              °{unitLetter}
            </span>
          </span>
          <WeatherIcon condition={condition} isDay={weather.current.isDay} className="hero-icon" />
        </div>

        <div className="hero-cond-group">
          <div className="hero-condition">{label}</div>
          <div className="hero-feels">
            Feels like {formatTemperature(weather.current.apparentTemperature, unit)}
          </div>
        </div>

        <div className="hero-footer">
          <div className="hero-stats">
            {today && (
              <span
                className="hero-stat hero-stat--high"
                role="img"
                aria-label={`Today's high ${formatTemperature(today.tempMax, unit)}`}
              >
                <svg {...STAT_ICON_PROPS}>
                  <path d="M12 19V5" />
                  <path d="m5 12 7-7 7 7" />
                </svg>
                <span className="hero-stat-value">{formatTemperature(today.tempMax, unit)}</span>
              </span>
            )}
            {today && (
              <span
                className="hero-stat hero-stat--low"
                role="img"
                aria-label={`Today's low ${formatTemperature(today.tempMin, unit)}`}
              >
                <svg {...STAT_ICON_PROPS}>
                  <path d="M12 5v14" />
                  <path d="m19 12-7 7-7-7" />
                </svg>
                <span className="hero-stat-value">{formatTemperature(today.tempMin, unit)}</span>
              </span>
            )}
            <span
              className="hero-stat hero-stat--pressure"
              role="img"
              aria-label={trend ? `Pressure ${pressure}, ${trend}` : `Pressure ${pressure}`}
            >
              <svg {...STAT_ICON_PROPS}>
                <path d="m12 14 4-4" />
                <path d="M3.34 19a10 10 0 1 1 17.32 0" />
              </svg>
              <span className="hero-stat-value">{pressure}</span>
              {trend && (
                <svg
                  className={`hero-stat-trend hero-stat-trend--${trend}`}
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2.6}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  {trend === 'steady' ? <path d="M5 12h14" /> : <path d="M6 15l6-6 6 6" />}
                </svg>
              )}
            </span>
          </div>

          <div className="hero-insight">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
            </svg>
            <span className="hero-insight-text">{insight}</span>
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
