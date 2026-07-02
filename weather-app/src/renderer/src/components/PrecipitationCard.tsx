import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { RainIcon } from './icons'
import { formatHour } from '../utils/units'

export function PrecipitationCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const flooredHour = `${weather.current.time.slice(0, 13)}:00`
  const startIndex = weather.hourly.findIndex((h) => h.time === flooredHour)
  const upcoming = weather.hourly.slice(startIndex === -1 ? 0 : startIndex, (startIndex === -1 ? 0 : startIndex) + 8)

  const todayMax = weather.daily[0]?.precipitationProbabilityMax ?? (weather.current.precipitation > 0 ? 100 : 0)

  return (
    <BentoCard span="bento-wide" floatDelay={0.9}>
      <div className="metric-card">
        <div className="metric-header">
          <RainIcon />
          <span className="metric-label">Chance of Rain</span>
        </div>
        <div className="metric-value">{Math.round(todayMax)}%</div>
        <div className="metric-sub">Peak chance today</div>

        <div className="precip-bars">
          {upcoming.map((point, index) => (
            <div className="precip-bar-col" key={point.time}>
              <div className="precip-bar-track">
                <div className="precip-bar-fill" style={{ height: `${Math.max(2, point.precipitationProbability)}%` }} />
              </div>
              <span className="precip-bar-label">{index === 0 ? 'Now' : formatHour(point.time)}</span>
            </div>
          ))}
        </div>
      </div>
    </BentoCard>
  )
}
