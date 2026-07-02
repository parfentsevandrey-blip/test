import { useWeatherStore } from '../store/useWeatherStore'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature, formatSpeed, formatPercent, formatClock } from '../utils/units'

function DropletIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
      <path d="M12 3 C12 3 6 10.5 6 15 a6 6 0 0 0 12 0 C18 10.5 12 3 12 3 Z" />
    </svg>
  )
}

function WindIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <path d="M3 8 H14 a2.5 2.5 0 1 0 -2.5 -2.5" />
      <path d="M3 12.5 H18 a2.5 2.5 0 1 1 -2.5 2.5" />
      <path d="M3 17 H11 a2 2 0 1 1 -2 2" />
    </svg>
  )
}

function SunBurstIcon(): JSX.Element {
  const rays = [0, 45, 90, 135, 180, 225, 270, 315]
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <circle cx={12} cy={12} r={3.6} />
      {rays.map((angle) => {
        const rad = (angle * Math.PI) / 180
        const x1 = 12 + Math.cos(rad) * 6.2
        const y1 = 12 + Math.sin(rad) * 6.2
        const x2 = 12 + Math.cos(rad) * 9.4
        const y2 = 12 + Math.sin(rad) * 9.4
        return <line key={angle} x1={x1} y1={y1} x2={x2} y2={y2} />
      })}
    </svg>
  )
}

function SunsetIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <path d="M6 12 a6 6 0 0 1 12 0" />
      <line x1={2.5} y1={12} x2={4.5} y2={12} />
      <line x1={19.5} y1={12} x2={21.5} y2={12} />
      <line x1={12} y1={2.5} x2={12} y2={4.5} />
      <line x1={5.6} y1={5.6} x2={7} y2={7} />
      <line x1={18.4} y1={5.6} x2={17} y2={7} />
      <line x1={3} y1={17} x2={21} y2={17} />
      <path d="M9 20.5 L12 17.5 L15 20.5" />
    </svg>
  )
}

export function CurrentWeatherCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const { condition, label } = getConditionInfo(weather.current.weatherCode)

  return (
    <div className="current-card glass-panel">
      <div className="current-location">
        {weather.location.name}
        <span className="country">
          {weather.location.admin1 ? ', ' + weather.location.admin1 : ''}
          {weather.location.country ? ' - ' + weather.location.country : ''}
        </span>
      </div>
      <div className="current-main">
        <span className="current-temp">{formatTemperature(weather.current.temperature, unit)}</span>
        <WeatherIcon condition={condition} isDay={weather.current.isDay} className="current-icon" />
      </div>
      <div className="current-condition">{label}</div>
      <div className="current-feels-like">
        Feels like {formatTemperature(weather.current.apparentTemperature, unit)}
      </div>
      <div className="current-meta-grid">
        <div className="meta-chip">
          <DropletIcon />
          <div>
            <div className="meta-chip-label">Humidity</div>
            <div className="meta-chip-value">{formatPercent(weather.current.humidity)}</div>
          </div>
        </div>
        <div className="meta-chip">
          <WindIcon />
          <div>
            <div className="meta-chip-label">Wind</div>
            <div className="meta-chip-value">{formatSpeed(weather.current.windSpeed, 'kmh')}</div>
          </div>
        </div>
        <div className="meta-chip">
          <SunBurstIcon />
          <div>
            <div className="meta-chip-label">UV Index</div>
            <div className="meta-chip-value">
              {weather.current.uvIndex !== null ? Math.round(weather.current.uvIndex) : '—'}
            </div>
          </div>
        </div>
        <div className="meta-chip">
          <SunsetIcon />
          <div>
            <div className="meta-chip-label">Sunset</div>
            <div className="meta-chip-value">{formatClock(weather.sunTimes.sunsetToday)}</div>
          </div>
        </div>
      </div>
    </div>
  )
}
