import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { EyeIcon } from './icons'
import { formatVisibility } from '../utils/units'

function visibilityDescriptor(meters: number | null): string {
  if (meters === null) return 'Unavailable'
  if (meters >= 15000) return 'Excellent'
  if (meters >= 8000) return 'Clear'
  if (meters >= 4000) return 'Moderate'
  if (meters >= 1000) return 'Poor'
  return 'Very poor'
}

export function VisibilityCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const visibility = weather.current.visibility

  return (
    <BentoCard span="bento-1" floatDelay={1.4}>
      <div className="metric-card">
        <div className="metric-header">
          <EyeIcon />
          <span className="metric-label">Visibility</span>
        </div>
        <div className="metric-value">{formatVisibility(visibility, unit)}</div>
        <div className="metric-sub">{visibilityDescriptor(visibility)}</div>
      </div>
    </BentoCard>
  )
}
