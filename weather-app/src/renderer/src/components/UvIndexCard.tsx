import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { SunBurstIcon } from './icons'
import { clamp01 } from '../utils/math'

function uvDescriptor(uv: number): string {
  if (uv < 3) return 'Low'
  if (uv < 6) return 'Moderate'
  if (uv < 8) return 'High'
  if (uv < 11) return 'Very high'
  return 'Extreme'
}

export function UvIndexCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const uv = weather.current.uvIndex
  const rounded = uv !== null ? Math.round(uv) : null

  return (
    <BentoCard span="bento-1" floatDelay={1}>
      <div className="metric-card">
        <div className="metric-header">
          <SunBurstIcon />
          <span className="metric-label">UV Index</span>
        </div>
        <div className="metric-value">{rounded ?? '—'}</div>
        <div className="metric-sub">{rounded !== null ? uvDescriptor(rounded) : 'Unavailable'}</div>
        <div className="uv-scale">
          <div className="uv-scale-track" />
          {rounded !== null && (
            <div className="uv-scale-marker" style={{ left: `${clamp01(rounded / 11) * 100}%` }} />
          )}
        </div>
      </div>
    </BentoCard>
  )
}
