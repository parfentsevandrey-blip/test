import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { DropletIcon } from './icons'
import { formatPercent } from '../utils/units'

function humidityDescriptor(pct: number): string {
  if (pct < 30) return 'Dry'
  if (pct < 60) return 'Comfortable'
  if (pct < 80) return 'Humid'
  return 'Very humid'
}

export function HumidityCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const humidity = weather.current.humidity

  return (
    <BentoCard span="bento-1" floatDelay={0.6}>
      <div className="metric-card">
        <div className="metric-header">
          <DropletIcon />
          <span className="metric-label">Humidity</span>
        </div>
        <div className="metric-value">{formatPercent(humidity)}</div>
        <div className="metric-sub">{humidityDescriptor(humidity)}</div>
        <div className="metric-visual">
          <div className="humidity-gauge">
            <div className="humidity-gauge-fill" style={{ height: `${Math.min(100, humidity)}%` }} />
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
