import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { EyeIcon } from './icons'
import { formatVisibility } from '../utils/units'
import { clamp01 } from '../utils/math'
import './MetricCards.css'

/** ~20 km reads as "unlimited" visibility; drives how far the depth lines reach. */
const FULL_VISIBILITY_METERS = 20000

/** Depth lines top (farthest, faintest) to bottom (nearest, strongest). */
const DEPTH_LINES = [
  { width: '36%', weight: 0.24 },
  { width: '58%', weight: 0.46 },
  { width: '78%', weight: 0.72 },
  { width: '100%', weight: 1 }
]

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
  const fraction = clamp01((visibility ?? 0) / FULL_VISIBILITY_METERS)

  return (
    <BentoCard span="bento-1" floatDelay={1.4}>
      <div className="metric-card">
        <div className="metric-header">
          <EyeIcon />
          <span className="metric-label">Visibility</span>
        </div>
        <div className="metric-value">{formatVisibility(visibility, unit)}</div>
        <div className="metric-sub">{visibilityDescriptor(visibility)}</div>
        <div className="metric-visual vis-depth" aria-hidden="true">
          {DEPTH_LINES.map((line) => (
            <div
              key={line.width}
              className="vis-depth-line"
              style={{ width: line.width, opacity: 0.07 + fraction * line.weight * 0.85 }}
            />
          ))}
        </div>
      </div>
    </BentoCard>
  )
}
