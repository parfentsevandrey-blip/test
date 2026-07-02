import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { RainIcon, SunBurstIcon } from './icons'
import { formatHour } from '../utils/units'
import './MetricCards.css'

export function PrecipitationCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const flooredHour = `${weather.current.time.slice(0, 13)}:00`
  const startIndex = weather.hourly.findIndex((h) => h.time === flooredHour)
  const from = startIndex === -1 ? 0 : startIndex
  const upcoming = weather.hourly.slice(from, from + 12)
  const allDry = upcoming.every((point) => point.precipitationProbability === 0)

  const todayMax = weather.daily[0]?.precipitationProbabilityMax ?? (weather.current.precipitation > 0 ? 100 : 0)

  return (
    <BentoCard span="bento-wide" floatDelay={0.9}>
      <div className="metric-card precip-card">
        <div className="metric-header">
          <RainIcon />
          <span className="metric-label">Chance of Rain</span>
        </div>
        <div className="metric-value">{Math.round(todayMax)}%</div>
        <div className="metric-sub">Peak chance today</div>

        {allDry ? (
          <div className="precip-empty">
            <SunBurstIcon />
            <span>No rain expected in the next 12 hours</span>
          </div>
        ) : (
          <div className="precip-bars">
            {upcoming.map((point, index) => (
              <div className={`precip-bar-col${index === 0 ? ' is-now' : ''}`} key={point.time}>
                <div className="precip-bar-track">
                  <div
                    className="precip-bar-fill"
                    style={{ height: `${Math.max(2, point.precipitationProbability)}%` }}
                  />
                </div>
                <span className="precip-bar-value">{Math.round(point.precipitationProbability)}%</span>
                <span className="precip-bar-label">{index === 0 ? 'Now' : formatHour(point.time)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </BentoCard>
  )
}
