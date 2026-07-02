import './SunCard.css'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { SunriseIcon } from './icons'
import { formatClock } from '../utils/units'
import { toAbsoluteInstant } from '../utils/time'
import { clamp01 } from '../utils/math'

const VIEW_W = 100
const VIEW_H = 52
const CX = 50
const HORIZON_Y = 46
const RADIUS = 34
/** How far (in viewBox units) the night-time moon dot dips below the horizon. */
const NIGHT_DIP = 8
const ARC_LENGTH = Math.PI * RADIUS
const GRADIENT_ID = 'sun-path-arc-gradient'

const ARC_PATH = `M ${CX - RADIUS} ${HORIZON_Y} A ${RADIUS} ${RADIUS} 0 0 1 ${CX + RADIUS} ${HORIZON_Y}`

/** Fraction of the way from start to end, clamped to [0, 1]; 0 for degenerate spans. */
function fracBetween(startMs: number, endMs: number, nowMs: number): number {
  const span = endMs - startMs
  if (span <= 0) return 0
  return clamp01((nowMs - startMs) / span)
}

export function SunCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)

  if (!weather) return null

  const { sunTimes, utcOffsetSeconds } = weather
  const sunrise = toAbsoluteInstant(sunTimes.sunriseToday, utcOffsetSeconds)
  const sunset = toAbsoluteInstant(sunTimes.sunsetToday, utcOffsetSeconds)
  const nowMs = Date.now()

  const progress = fracBetween(sunrise.getTime(), sunset.getTime(), nowMs)
  const isDaytime = nowMs >= sunrise.getTime() && nowMs <= sunset.getTime()

  // Sun marker rides the day arc from the sunrise endpoint to the sunset one.
  const sunAngle = Math.PI - progress * Math.PI
  const sunX = CX + Math.cos(sunAngle) * RADIUS
  const sunY = HORIZON_Y - Math.sin(sunAngle) * RADIUS

  // At night, a small moon dot mirrors the journey just below the horizon
  // (same horizontal sweep, shallow vertical dip so it stays inside the card).
  let moonX = 0
  let moonY = 0
  if (!isDaytime) {
    const nightProgress =
      nowMs < sunrise.getTime()
        ? fracBetween(
            toAbsoluteInstant(sunTimes.sunsetYesterday, utcOffsetSeconds).getTime(),
            sunrise.getTime(),
            nowMs
          )
        : fracBetween(
            sunset.getTime(),
            toAbsoluteInstant(sunTimes.sunriseTomorrow, utcOffsetSeconds).getTime(),
            nowMs
          )
    const moonAngle = Math.PI - nightProgress * Math.PI
    moonX = CX + Math.cos(moonAngle) * RADIUS
    moonY = HORIZON_Y + Math.sin(moonAngle) * NIGHT_DIP
  }

  const daylightMinutes = Math.max(0, Math.round((sunset.getTime() - sunrise.getTime()) / 60000))
  const daylight = `${Math.floor(daylightMinutes / 60)}h ${daylightMinutes % 60}m`
  const sunriseLabel = formatClock(sunTimes.sunriseToday)
  const sunsetLabel = formatClock(sunTimes.sunsetToday)

  return (
    <BentoCard span="bento-wide" floatDelay={0.4}>
      <div className="sun-path-card">
        <div className="metric-header">
          <SunriseIcon />
          <span className="metric-label">Sunrise &amp; Sunset</span>
        </div>

        <svg
          className="sun-path-arc"
          viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label={`Sun path: sunrise ${sunriseLabel}, sunset ${sunsetLabel}, ${daylight} of daylight`}
        >
          <defs>
            <linearGradient id={GRADIENT_ID} x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" className="sun-path-grad-from" />
              <stop offset="1" className="sun-path-grad-to" />
            </linearGradient>
          </defs>

          <line
            className="sun-path-horizon"
            x1={6}
            y1={HORIZON_Y}
            x2={VIEW_W - 6}
            y2={HORIZON_Y}
          />
          <path className="sun-path-track" d={ARC_PATH} />
          {progress > 0 && (
            <path
              className="sun-path-progress"
              d={ARC_PATH}
              stroke={`url(#${GRADIENT_ID})`}
              strokeDasharray={`${progress * ARC_LENGTH} ${ARC_LENGTH}`}
            />
          )}

          {isDaytime ? (
            <g>
              <circle className="sun-path-sun-halo" cx={sunX} cy={sunY} r={7} />
              <circle className="sun-path-sun-core" cx={sunX} cy={sunY} r={3.2} />
            </g>
          ) : (
            <circle className="sun-path-moon" cx={moonX} cy={moonY} r={2.6} />
          )}
        </svg>

        <div className="sun-path-stats">
          <div className="sun-path-stat">
            <span className="sun-path-stat-label">Daylight</span>
            <span className="sun-path-stat-value">{daylight}</span>
          </div>
          <div className="sun-path-stat">
            <span className="sun-path-stat-label">Sunrise</span>
            <span className="sun-path-stat-value">{sunriseLabel}</span>
          </div>
          <div className="sun-path-stat">
            <span className="sun-path-stat-label">Sunset</span>
            <span className="sun-path-stat-value">{sunsetLabel}</span>
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
