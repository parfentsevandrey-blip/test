import { useEffect, useState } from 'react'
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
/** Golden hour ≈ the first hour after sunrise / the last hour before sunset. */
const GOLDEN_HOUR_MS = 3_600_000
/** Live tick cadence for the countdown hero line (also nudges the arc marker). */
const TICK_MS = 30_000

const ARC_PATH = `M ${CX - RADIUS} ${HORIZON_Y} A ${RADIUS} ${RADIUS} 0 0 1 ${CX + RADIUS} ${HORIZON_Y}`

/** Fraction of the way from start to end, clamped to [0, 1]; 0 for degenerate spans. */
function fracBetween(startMs: number, endMs: number, nowMs: number): number {
  const span = endMs - startMs
  if (span <= 0) return 0
  return clamp01((nowMs - startMs) / span)
}

/** Point on the day arc for a daylight fraction (0 = sunrise end, 1 = sunset end). */
function arcPoint(frac: number): { x: number; y: number } {
  const angle = Math.PI - frac * Math.PI
  return { x: CX + Math.cos(angle) * RADIUS, y: HORIZON_Y - Math.sin(angle) * RADIUS }
}

/** "5h 12m" / "42m" style duration for the countdown hero line. */
function formatDelta(ms: number): string {
  const totalMin = Math.max(0, Math.round(ms / 60000))
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h ${m}m`
}

export function SunCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  // Live clock: the countdown (and the sun marker with it) refreshes every 30s.
  const [nowMs, setNowMs] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNowMs(Date.now()), TICK_MS)
    return () => clearInterval(id)
  }, [])

  if (!weather) return null

  const { sunTimes, utcOffsetSeconds } = weather
  const sunrise = toAbsoluteInstant(sunTimes.sunriseToday, utcOffsetSeconds)
  const sunset = toAbsoluteInstant(sunTimes.sunsetToday, utcOffsetSeconds)
  const sunriseMs = sunrise.getTime()
  const sunsetMs = sunset.getTime()

  const progress = fracBetween(sunriseMs, sunsetMs, nowMs)
  const isDaytime = nowMs >= sunriseMs && nowMs <= sunsetMs

  // Sun marker rides the day arc from the sunrise endpoint to the sunset one.
  const { x: sunX, y: sunY } = arcPoint(progress)

  // At night, a small moon dot mirrors the journey just below the horizon
  // (same horizontal sweep, shallow vertical dip so it stays inside the card).
  let moonX = 0
  let moonY = 0
  if (!isDaytime) {
    const nightProgress =
      nowMs < sunriseMs
        ? fracBetween(
            toAbsoluteInstant(sunTimes.sunsetYesterday, utcOffsetSeconds).getTime(),
            sunriseMs,
            nowMs
          )
        : fracBetween(
            sunsetMs,
            toAbsoluteInstant(sunTimes.sunriseTomorrow, utcOffsetSeconds).getTime(),
            nowMs
          )
    const moonAngle = Math.PI - nightProgress * Math.PI
    moonX = CX + Math.cos(moonAngle) * RADIUS
    moonY = HORIZON_Y + Math.sin(moonAngle) * NIGHT_DIP
  }

  const daylightMinutes = Math.max(0, Math.round((sunsetMs - sunriseMs) / 60000))
  const daylight = `${Math.floor(daylightMinutes / 60)}h ${daylightMinutes % 60}m`
  const sunriseLabel = formatClock(sunTimes.sunriseToday)
  const sunsetLabel = formatClock(sunTimes.sunsetToday)

  // Hero countdown to the next sun event (before dawn -> today's sunrise,
  // during the day -> today's sunset, after dusk -> tomorrow's sunrise).
  const nextEventName = isDaytime ? 'Sunset' : 'Sunrise'
  const nextEventMs =
    nowMs < sunriseMs
      ? sunriseMs
      : nowMs <= sunsetMs
        ? sunsetMs
        : toAbsoluteInstant(sunTimes.sunriseTomorrow, utcOffsetSeconds).getTime()
  const countdown = formatDelta(nextEventMs - nowMs)

  // Golden-hour ticks: ~1h after sunrise and ~1h before sunset, as fractions
  // of the daylight span mapped onto the arc. Skipped for very short days
  // where the two windows would overlap and the ticks would collide.
  const daySpanMs = sunsetMs - sunriseMs
  const showGolden = daySpanMs > GOLDEN_HOUR_MS * 2.5
  const goldenTicks = showGolden
    ? [
        {
          frac: GOLDEN_HOUR_MS / daySpanMs,
          title: `Morning golden hour — the hour after sunrise (${sunriseLabel})`
        },
        {
          frac: 1 - GOLDEN_HOUR_MS / daySpanMs,
          title: `Evening golden hour — the hour before sunset (${sunsetLabel})`
        }
      ]
    : []

  return (
    <BentoCard span="bento-wide" floatDelay={0.4}>
      <div className="sun-path-card">
        <div className="metric-header">
          <SunriseIcon />
          <span className="metric-label">Sunrise &amp; Sunset</span>
        </div>

        <div className="sun-path-countdown" role="timer" aria-label={`${nextEventName} in ${countdown}`}>
          <span className="sun-path-countdown-value">{countdown}</span>
          <span className="sun-path-countdown-label">until {nextEventName.toLowerCase()}</span>
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

          {goldenTicks.map((tick) => {
            const { x, y } = arcPoint(tick.frac)
            return (
              <g key={tick.title} className="sun-path-golden">
                <title>{tick.title}</title>
                <rect
                  x={x - 1.7}
                  y={y - 1.7}
                  width={3.4}
                  height={3.4}
                  transform={`rotate(45 ${x} ${y})`}
                />
              </g>
            )
          })}

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
