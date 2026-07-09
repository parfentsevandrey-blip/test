import { useEffect, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import './DailyForecast.css'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { CalendarIcon } from './icons'
import { getConditionInfo } from '../utils/weatherCondition'
import {
  formatClock,
  formatSpeed,
  formatTemperature,
  formatWeekday,
  speedUnitFor
} from '../utils/units'
import type { DailyForecastPoint, TemperatureUnit } from '../types/weather'

/** Minimum visible fill width (% of track) so single-degree days don't vanish. */
const MIN_FILL_PCT = 6

const clampPct = (value: number): number => Math.min(100, Math.max(0, value))

/** Noon avoids UTC-parse off-by-one-day issues (same trick as formatWeekday). */
const atNoon = (isoDate: string): Date => new Date(`${isoDate}T12:00:00`)

const formatDateShort = (isoDate: string): string =>
  atNoon(isoDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })

const formatWeekdayLong = (isoDate: string): string =>
  atNoon(isoDate).toLocaleDateString('en-US', { weekday: 'long' })

interface DailyDetailPopoverProps {
  day: DailyForecastPoint
  isToday: boolean
  unit: TemperatureUnit
  onClose: () => void
}

/**
 * Drill-down for a single day, using only data the daily forecast already
 * fetches (peak wind/UV, sunrise/sunset) — no new request. An overlay
 * rather than in-place row expansion, since the card is a fixed 10-row,
 * zero-slack layout with no room to grow a row taller.
 */
function DailyDetailPopover({ day, isToday, unit, onClose }: DailyDetailPopoverProps): JSX.Element {
  const condition = getConditionInfo(day.weatherCode)
  const speedUnit = speedUnitFor(unit)

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return createPortal(
    <div className="df-detail-overlay" onMouseDown={onClose}>
      <div
        className="df-detail-panel glass-panel"
        role="dialog"
        aria-modal="true"
        aria-label={`${isToday ? 'Today' : formatWeekdayLong(day.date)} details`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="df-detail-header">
          <WeatherIcon condition={condition.condition} isDay={true} className="df-detail-icon" />
          <div className="df-detail-heading">
            <span className="df-detail-day">{isToday ? 'Today' : formatWeekdayLong(day.date)}</span>
            <span className="df-detail-date">{formatDateShort(day.date)}</span>
          </div>
          <button type="button" className="icon-btn df-detail-close" aria-label="Close" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" aria-hidden="true">
              <line x1={6} y1={6} x2={18} y2={18} />
              <line x1={18} y1={6} x2={6} y2={18} />
            </svg>
          </button>
        </div>

        <div className="df-detail-condition">{condition.label}</div>

        <div className="df-detail-grid">
          <div className="df-detail-stat" style={{ '--row-i': 0 } as CSSProperties}>
            <span className="df-detail-stat-label">High</span>
            <span className="df-detail-stat-value">{formatTemperature(day.tempMax, unit)}</span>
          </div>
          <div className="df-detail-stat" style={{ '--row-i': 1 } as CSSProperties}>
            <span className="df-detail-stat-label">Low</span>
            <span className="df-detail-stat-value">{formatTemperature(day.tempMin, unit)}</span>
          </div>
          <div className="df-detail-stat" style={{ '--row-i': 2 } as CSSProperties}>
            <span className="df-detail-stat-label">Rain chance</span>
            <span className="df-detail-stat-value">{Math.round(day.precipitationProbabilityMax)}%</span>
          </div>
          <div className="df-detail-stat" style={{ '--row-i': 3 } as CSSProperties}>
            <span className="df-detail-stat-label">Peak wind</span>
            <span className="df-detail-stat-value">{formatSpeed(day.windSpeedMax, speedUnit)}</span>
          </div>
          {day.uvIndexMax !== null && (
            <div className="df-detail-stat" style={{ '--row-i': 4 } as CSSProperties}>
              <span className="df-detail-stat-label">Peak UV</span>
              <span className="df-detail-stat-value">{Math.round(day.uvIndexMax)}</span>
            </div>
          )}
          <div className="df-detail-stat" style={{ '--row-i': 5 } as CSSProperties}>
            <span className="df-detail-stat-label">Sunrise</span>
            <span className="df-detail-stat-value">{formatClock(day.sunrise)}</span>
          </div>
          <div className="df-detail-stat" style={{ '--row-i': 6 } as CSSProperties}>
            <span className="df-detail-stat-label">Sunset</span>
            <span className="df-detail-stat-value">{formatClock(day.sunset)}</span>
          </div>
        </div>
      </div>
    </div>,
    document.body
  )
}

export function DailyForecast(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  if (!weather) return null

  const selectedDay = selectedDate ? weather.daily.find((d) => d.date === selectedDate) : undefined

  const globalMin = Math.min(...weather.daily.map((day) => day.tempMin))
  const globalMax = Math.max(...weather.daily.map((day) => day.tempMax))
  const span = globalMax - globalMin

  const pct = (temperature: number): number =>
    span === 0 ? 50 : clampPct(((temperature - globalMin) / span) * 100)

  const nowPct = pct(weather.current.temperature)

  return (
    <BentoCard span="bento-daily" floatDelay={0.3}>
      <div className="df-root">
        <div className="card-title df-title">
          <CalendarIcon />
          10-Day Forecast
        </div>

        <div className="df-rows" role="list" aria-label="Daily forecast for the next 10 days">
          {weather.daily.map((day, index) => {
            const isToday = index === 0
            const condition = getConditionInfo(day.weatherCode)
            const pop = Math.round(day.precipitationProbabilityMax)
            const minPct = pct(day.tempMin)
            const maxPct = pct(day.tempMax)

            // Window into the full-scale gradient (see .df-fill in the CSS).
            const width = Math.max(maxPct - minPct, MIN_FILL_PCT)
            const left = Math.min(minPct, 100 - width)
            const fillStyle: CSSProperties = {
              left: `${left}%`,
              width: `${width}%`,
              backgroundSize: `${10000 / width}% 100%`,
              backgroundPositionX: width >= 100 ? '0%' : `${(100 * left) / (100 - width)}%`
            }

            const label =
              `${isToday ? 'Today' : formatWeekdayLong(day.date)}, ${formatDateShort(day.date)}: ` +
              `${condition.label}, high ${formatTemperature(day.tempMax, unit)}, ` +
              `low ${formatTemperature(day.tempMin, unit)}` +
              (pop > 0 ? `, ${pop}% chance of precipitation` : '')

            return (
              <button
                type="button"
                className={`df-row${isToday ? ' is-today' : ''}`}
                role="listitem"
                aria-label={`${label}. Show details.`}
                aria-haspopup="dialog"
                title={condition.label}
                key={day.date}
                onClick={() => setSelectedDate(day.date)}
              >
                <div className="df-day-col">
                  <span className="df-day">{isToday ? 'Today' : formatWeekday(day.date)}</span>
                  <span className="df-date">{formatDateShort(day.date)}</span>
                </div>

                <WeatherIcon condition={condition.condition} isDay={true} className="df-icon" />

                {/* Always render for columnar rhythm; only meaningful chances (>=20%) earn the blue emphasis. */}
                <span className={`df-pop${pop < 20 ? ' df-pop--low' : ''}`}>{`${pop}%`}</span>

                <div className="df-range">
                  <span className="df-temp-min">{formatTemperature(day.tempMin, unit)}</span>
                  <div className="df-track">
                    <div className="df-fill" style={fillStyle} />
                    {isToday && (
                      <div
                        className="df-now-dot"
                        style={{ left: `${nowPct}%` }}
                        aria-hidden="true"
                      />
                    )}
                  </div>
                  <span className="df-temp-max">{formatTemperature(day.tempMax, unit)}</span>
                </div>
              </button>
            )
          })}
        </div>
      </div>

      {selectedDay && (
        <DailyDetailPopover
          day={selectedDay}
          isToday={selectedDay.date === weather.daily[0].date}
          unit={unit}
          onClose={() => setSelectedDate(null)}
        />
      )}
    </BentoCard>
  )
}
