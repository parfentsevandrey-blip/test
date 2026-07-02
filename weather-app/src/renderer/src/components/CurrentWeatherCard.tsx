import './CurrentWeatherCard.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { celsiusTo, formatHour, formatTemperature } from '../utils/units'
import { toAbsoluteInstant } from '../utils/time'
import type { TemperatureUnit, WeatherData } from '../types/weather'

const STAT_ICON_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true
}

const COUNT_UP_MS = 900
const HOUR_MS = 3_600_000

/**
 * Animates a displayed number toward `target` over ~0.9s with an ease-out
 * cubic, starting from whatever value is currently shown (0 on first mount,
 * so the initial load counts up too). Under prefers-reduced-motion the value
 * jumps instantly. The rAF loop is cancelled on retarget/unmount.
 */
function useCountUp(target: number): number {
  const [display, setDisplay] = useState(0)
  const displayRef = useRef(0)

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      displayRef.current = target
      setDisplay(target)
      return undefined
    }

    const from = displayRef.current
    if (from === target) return undefined

    const start = performance.now()
    let frame = requestAnimationFrame(function tick(now: number): void {
      const t = Math.min(1, (now - start) / COUNT_UP_MS)
      const eased = 1 - (1 - t) ** 3
      const value = from + (target - from) * eased
      displayRef.current = value
      setDisplay(value)
      if (t < 1) frame = requestAnimationFrame(tick)
    })
    return () => cancelAnimationFrame(frame)
  }, [target])

  return display
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
    const word = condition === 'snow' ? 'Snow' : condition === 'thunderstorm' ? 'Storms' : 'Rain'
    return `${word} likely around ${formatHour(wettest.time)} — ${Math.round(wettest.precipitationProbability)}%`
  }

  const current = weather.current.temperature
  const hottest = next12.reduce((a, b) => (b.temperature > a.temperature ? b : a))
  const coldest = next12.reduce((a, b) => (b.temperature < a.temperature ? b : a))
  const rise = hottest.temperature - current
  const drop = current - coldest.temperature
  if (rise >= 2 && rise >= drop) {
    return `Warming to ${formatTemperature(hottest.temperature, unit)} by ${formatHour(hottest.time)}`
  }
  if (drop >= 2) {
    return `Cooling to ${formatTemperature(coldest.temperature, unit)} by ${formatHour(coldest.time)}`
  }

  const clearish = next12.filter((h) => {
    const c = getConditionInfo(h.weatherCode).condition
    return c === 'clear' || c === 'partly-cloudy'
  })
  if (clearish.length >= next12.length * 0.7) {
    return weather.current.isDay ? 'Clear evening ahead' : 'Clear night ahead'
  }
  return 'Steady conditions for the next few hours'
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
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0" />
            <circle cx="12" cy="10" r="3" />
          </svg>
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
              aria-label={`Pressure ${pressure}`}
            >
              <svg {...STAT_ICON_PROPS}>
                <path d="m12 14 4-4" />
                <path d="M3.34 19a10 10 0 1 1 17.32 0" />
              </svg>
              <span className="hero-stat-value">{pressure}</span>
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
