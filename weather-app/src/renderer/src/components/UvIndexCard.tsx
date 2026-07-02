import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { SunBurstIcon } from './icons'
import { clamp01 } from '../utils/math'
import './MetricCards.css'

/** Severity boundaries marked with ticks on the scale track. */
const UV_TICKS = [3, 6, 8, 11]
const UV_MAX = 11

interface UvSeverity {
  label: string
  color: string
}

/**
 * Zone TEXT colors come from theme-paired tokens (--uv-* in global.css):
 * dark theme keeps the scale track's vivid hues, light theme swaps in
 * darkened equivalents so the label stays readable on bright glass.
 */
function uvSeverity(uv: number): UvSeverity {
  if (uv < 3) return { label: 'Low', color: 'var(--uv-low)' }
  if (uv < 6) return { label: 'Moderate', color: 'var(--uv-moderate)' }
  if (uv < 8) return { label: 'High', color: 'var(--uv-high)' }
  if (uv < 11) return { label: 'Very high', color: 'var(--uv-veryhigh)' }
  return { label: 'Extreme', color: 'var(--uv-extreme)' }
}

/** WHO-style protection guidance, one calm line per severity band. */
function uvHint(uv: number): string {
  if (uv < 3) return 'No protection needed'
  if (uv < 6) return 'Wear sunscreen'
  if (uv < 8) return 'Sunscreen and hat advised'
  if (uv < 11) return 'Avoid midday sun'
  return 'Stay in shade midday'
}

export function UvIndexCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const uv = weather.current.uvIndex
  const rounded = uv !== null ? Math.round(uv) : null
  const severity = rounded !== null ? uvSeverity(rounded) : null

  /* 680px-window vertical budget (content ≈ 162px): header 16 + value 40 +
     sub 20 + hint ~17 + scale block ~33 (14 pad + 6 track + 13 end labels)
     = 126 fixed; the ~36px surplus is split by the two auto margins so the
     hint floats evenly between the readout and the instrument scale. */
  return (
    <BentoCard span="bento-1" floatDelay={1}>
      <div className="metric-card">
        <div className="metric-header">
          <SunBurstIcon />
          <span className="metric-label">UV Index</span>
        </div>
        <div className="metric-value">{rounded ?? '—'}</div>
        <div className="metric-sub uv-sub" style={severity !== null ? { color: severity.color } : undefined}>
          {severity !== null ? severity.label : 'Unavailable'}
        </div>
        {rounded !== null && <div className="uv-hint">{uvHint(rounded)}</div>}
        <div className="uv-scale">
          <div className="uv-scale-track">
            {UV_TICKS.map((tick) => (
              <span key={tick} className="uv-tick" style={{ left: `${(tick / UV_MAX) * 100}%` }} />
            ))}
          </div>
          {rounded !== null && (
            <div className="uv-scale-marker" style={{ left: `${clamp01(rounded / UV_MAX) * 100}%` }} />
          )}
          <div className="uv-scale-labels" aria-hidden="true">
            <span>0</span>
            <span>11+</span>
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
