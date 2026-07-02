import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { SunriseIcon, SunsetIcon } from './icons'
import { formatClock } from '../utils/units'
import { toAbsoluteInstant } from '../utils/time'
import { clamp01 } from '../utils/math'

const ARC_CENTER_X = 50
const ARC_CENTER_Y = 54
const ARC_RADIUS = 38

export function SunCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const { sunTimes, utcOffsetSeconds } = weather
  const sunrise = toAbsoluteInstant(sunTimes.sunriseToday, utcOffsetSeconds)
  const sunset = toAbsoluteInstant(sunTimes.sunsetToday, utcOffsetSeconds)
  const now = new Date()

  const dayLengthMs = sunset.getTime() - sunrise.getTime()
  const progress = clamp01((now.getTime() - sunrise.getTime()) / dayLengthMs)
  const isDaytime = now.getTime() >= sunrise.getTime() && now.getTime() <= sunset.getTime()

  const angle = Math.PI - progress * Math.PI
  const sunX = ARC_CENTER_X + Math.cos(angle) * ARC_RADIUS
  const sunY = ARC_CENTER_Y - Math.sin(angle) * ARC_RADIUS

  const arcPath = `M ${ARC_CENTER_X - ARC_RADIUS} ${ARC_CENTER_Y} A ${ARC_RADIUS} ${ARC_RADIUS} 0 0 1 ${ARC_CENTER_X + ARC_RADIUS} ${ARC_CENTER_Y}`

  return (
    <BentoCard span="bento-wide" floatDelay={0.4}>
      <div className="metric-card sun-card">
        <div className="metric-header">
          <SunriseIcon />
          <span className="metric-label">Sunrise &amp; Sunset</span>
        </div>

        <svg className="sun-arc" viewBox="0 0 100 60" aria-hidden="true">
          <path d={arcPath} className="sun-arc-track" />
          <path
            d={arcPath}
            className="sun-arc-progress"
            style={{ strokeDasharray: `${progress * ARC_RADIUS * Math.PI} ${ARC_RADIUS * Math.PI}` }}
          />
          <line x1={ARC_CENTER_X - ARC_RADIUS} y1={ARC_CENTER_Y} x2={ARC_CENTER_X + ARC_RADIUS} y2={ARC_CENTER_Y} className="sun-arc-horizon" />
          {isDaytime && <circle cx={sunX} cy={sunY} r={4.5} className="sun-arc-dot" />}
        </svg>

        <div className="sun-times">
          <div className="meta-chip">
            <SunriseIcon />
            <div>
              <div className="meta-chip-label">Sunrise</div>
              <div className="meta-chip-value">{formatClock(sunTimes.sunriseToday)}</div>
            </div>
          </div>
          <div className="meta-chip">
            <SunsetIcon />
            <div>
              <div className="meta-chip-label">Sunset</div>
              <div className="meta-chip-value">{formatClock(sunTimes.sunsetToday)}</div>
            </div>
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
