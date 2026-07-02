import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { EyeIcon } from './icons'
import { clamp01 } from '../utils/math'
import './MetricCards.css'

/** ~20 km reads as "unlimited" visibility; the gauge's full-scale end stop. */
const FULL_VISIBILITY_METERS = 20000
const METERS_PER_MILE = 1609.34

/** Minor graduations on the gauge track (fractions of full scale). */
const GAUGE_TICKS = [0.25, 0.5, 0.75]

function visibilityDescriptor(meters: number | null): string {
  if (meters === null) return 'Unavailable'
  if (meters >= 15000) return 'Excellent'
  if (meters >= 8000) return 'Clear'
  if (meters >= 4000) return 'Moderate'
  if (meters >= 1000) return 'Poor'
  return 'Very poor'
}

/** One-line context under the gauge, mirroring the wind/humidity footlines. */
function visibilityContext(meters: number | null): string {
  if (meters === null) return 'No sensor reading'
  if (meters >= 15000) return 'Clear line of sight'
  if (meters >= 8000) return 'Slight haze in the air'
  if (meters >= 4000) return 'Haze limits distance'
  if (meters >= 1000) return 'Mist shortens the view'
  return 'Dense fog or heavy rain'
}

export function VisibilityCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const visibility = weather.current.visibility
  const fraction = clamp01((visibility ?? 0) / FULL_VISIBILITY_METERS)
  const imperial = unit === 'fahrenheit'
  const distance = visibility === null ? null : imperial ? visibility / METERS_PER_MILE : visibility / 1000
  const fullScale = imperial
    ? `${Math.round(FULL_VISIBILITY_METERS / METERS_PER_MILE)} mi`
    : `${FULL_VISIBILITY_METERS / 1000} km`

  /* 680px-window vertical budget (content ≈ 162px): header 16 + value 40 +
     sub 20 = 76 fixed; the gauge (8 track + 6 gap + 13 labels ≈ 27) centers
     in the remaining ~86px flexed slot with generous air on both sides. */
  return (
    <BentoCard span="bento-1" floatDelay={1.4}>
      <div className="metric-card">
        <div className="metric-header">
          <EyeIcon />
          <span className="metric-label">Visibility</span>
        </div>
        <div className="metric-value">
          {distance !== null ? (
            <>
              <span className="mx-value">{distance.toFixed(1)}</span>
              <span className="mx-unit">{imperial ? 'mi' : 'km'}</span>
            </>
          ) : (
            '—'
          )}
        </div>
        <div className="metric-sub">{visibilityDescriptor(visibility)}</div>
        <div className="metric-visual vis-gauge-wrap" aria-hidden="true">
          <div className="vis-gauge">
            <div className="vis-gauge-track">
              <div className="vis-gauge-fill" style={{ width: `${fraction * 100}%` }} />
              {GAUGE_TICKS.map((tick) => (
                <span key={tick} className="vis-gauge-tick" style={{ left: `${tick * 100}%` }} />
              ))}
            </div>
            <div className="vis-gauge-labels">
              <span>0</span>
              <span>{fullScale}</span>
            </div>
          </div>
        </div>
        <div className="mx-footline">{visibilityContext(visibility)}</div>
      </div>
    </BentoCard>
  )
}
