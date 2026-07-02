import './CurrentWeatherCard.css'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature } from '../utils/units'

const STAT_ICON_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true
}

export function CurrentWeatherCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const { condition, label } = getConditionInfo(weather.current.weatherCode)
  const { name, admin1, country } = weather.location
  const placeSub = [admin1, country].filter(Boolean).join(' · ')

  const today = weather.daily.at(0)
  const pressure = `${Math.round(weather.current.pressure)} hPa`

  return (
    <BentoCard span="bento-hero">
      <div className="hero-current">
        <div className="hero-place">
          <div className="hero-place-name">{name}</div>
          {placeSub && <div className="hero-place-sub">{placeSub}</div>}
        </div>

        <div className="hero-main">
          <span className="hero-temp">{formatTemperature(weather.current.temperature, unit)}</span>
          <WeatherIcon condition={condition} isDay={weather.current.isDay} className="hero-icon" />
        </div>

        <div className="hero-condition">{label}</div>
        <div className="hero-feels">
          Feels like {formatTemperature(weather.current.apparentTemperature, unit)}
        </div>

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
      </div>
    </BentoCard>
  )
}
