import { useEffect, useState } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import './SunCard.css'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { SunriseIcon } from './icons'
import { formatClock } from '../utils/units'
import { toAbsoluteInstant } from '../utils/time'
import { clamp01 } from '../utils/math'
import { getMoonPhase } from '../utils/moonPhase'

const VIEW_W = 100
const VIEW_H = 52
const CX = 50
const HORIZON_Y = 46
const RADIUS = 34
/** How far (in viewBox units) the night-time moon dot dips below the horizon. */
const NIGHT_DIP = 8
/** Radius of the moon-phase glyph itself. */
const MOON_R = 2.6
const ARC_LENGTH = Math.PI * RADIUS
const GRADIENT_ID = 'sun-path-arc-gradient'
const MOON_CLIP_ID = 'sun-path-moon-clip'
/** Golden hour ≈ the first hour after sunrise / the last hour before sunset. */
const GOLDEN_HOUR_MS = 3_600_000
/** Live tick cadence for the countdown hero line (also nudges the arc marker). */
const TICK_MS = 30_000

const ARC_PATH = `M ${CX - RADIUS} ${HORIZON_Y} A ${RADIUS} ${RADIUS} 0 0 1 ${CX + RADIUS} ${HORIZON_Y}`

const EASE_OUT = [0.16, 1, 0.3, 1] as const
/** Spring config the sun/moon marker glides along the arc with -- soft and a
 *  touch slow so the 30s tick cadence reads as a continuous drift rather than
 *  a series of little jumps. */
const MARKER_SPRING = { type: 'spring', stiffness: 65, damping: 18, mass: 1 } as const
/** Snappier spring for the day/night group swap (mount/unmount at dawn/dusk). */
const GROUP_SPRING = { type: 'spring', stiffness: 220, damping: 20 } as const

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
  const theme = useWeatherStore((s) => s.theme)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()
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

  // A real crescent/gibbous silhouette instead of a flat dot: two same-radius
  // discs, one the moon's lit color, one dark, offset horizontally and
  // clipped to the moon's circular bounds — offset 0 = full overlap (full
  // moon), offset ±MOON_R*2 = no overlap (new moon), sign picks which side
  // is lit (waxing lit on the right, waning on the left).
  const moonPhase = getMoonPhase(nowMs)
  const moonIlluminatedFraction = (1 - Math.cos(2 * Math.PI * moonPhase)) / 2
  const moonLitOffsetX = (moonPhase < 0.5 ? 1 : -1) * MOON_R * 2 * (1 - moonIlluminatedFraction)

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

  // Identifies "today's data" -- changes only when a new day/location's sun
  // times load, not on every 30s tick. Used as a React key so the arc's
  // draw-in replays on real data changes but not on every countdown tick.
  const arcKey = `${sunTimes.sunriseToday}|${sunTimes.sunsetToday}`

  const markerTransition = prefersReducedMotion ? { duration: 0 } : MARKER_SPRING
  const groupTransition = prefersReducedMotion ? { duration: 0 } : GROUP_SPRING
  const drawTransition = prefersReducedMotion ? { duration: 0 } : { duration: 1.1, ease: EASE_OUT }
  const progressTransition = prefersReducedMotion
    ? { duration: 0 }
    : { type: 'spring' as const, stiffness: 55, damping: 20 }

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
            <clipPath id={MOON_CLIP_ID}>
              {/* Kept in lockstep with .sun-path-moon-lit's own cx/cy motion
                  below (identical spring config -> identical interpolation
                  each frame) so the crescent clip boundary never lags the
                  disc it's clipping during the marker's glide. */}
              {isRetro ? (
                <circle className="sun-path-moon-clip" cx={moonX} cy={moonY} r={MOON_R} />
              ) : (
                <motion.circle
                  className="sun-path-moon-clip sun-path-marker-motion"
                  initial={{ cx: moonX, cy: moonY }}
                  animate={{ cx: moonX, cy: moonY }}
                  transition={markerTransition}
                  r={MOON_R}
                />
              )}
            </clipPath>
          </defs>

          <line
            className="sun-path-horizon"
            x1={6}
            y1={HORIZON_Y}
            x2={VIEW_W - 6}
            y2={HORIZON_Y}
          />

          {isRetro ? (
            <path className="sun-path-track" d={ARC_PATH} />
          ) : (
            <motion.path
              key={`track-${arcKey}`}
              className="sun-path-track"
              d={ARC_PATH}
              strokeDasharray={`${ARC_LENGTH} ${ARC_LENGTH}`}
              initial={{ strokeDashoffset: ARC_LENGTH }}
              animate={{ strokeDashoffset: 0 }}
              transition={drawTransition}
            />
          )}

          {progress > 0 &&
            (isRetro ? (
              <path
                className="sun-path-progress"
                d={ARC_PATH}
                stroke={`url(#${GRADIENT_ID})`}
                strokeDasharray={`${progress * ARC_LENGTH} ${ARC_LENGTH}`}
              />
            ) : (
              <motion.path
                key={`progress-${arcKey}`}
                className="sun-path-progress sun-path-progress-motion"
                d={ARC_PATH}
                stroke={`url(#${GRADIENT_ID})`}
                strokeDasharray={`${ARC_LENGTH} ${ARC_LENGTH}`}
                initial={{ strokeDashoffset: ARC_LENGTH }}
                animate={{ strokeDashoffset: ARC_LENGTH * (1 - progress) }}
                transition={progressTransition}
              />
            ))}

          {isRetro ? (
            goldenTicks.map((tick) => {
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
            })
          ) : (
            <motion.g
              key={`golden-${arcKey}`}
              initial="hidden"
              animate="visible"
              variants={{ visible: { transition: { staggerChildren: 0.16, delayChildren: 0.5 } } }}
            >
              {goldenTicks.map((tick) => {
                const { x, y } = arcPoint(tick.frac)
                return (
                  <motion.g
                    key={tick.title}
                    className="sun-path-golden"
                    variants={{
                      hidden: { opacity: 0, scale: 0 },
                      visible: { opacity: 1, scale: 1, transition: { type: 'spring', stiffness: 320, damping: 16 } }
                    }}
                    style={{ transformBox: 'fill-box', transformOrigin: 'center' }}
                  >
                    <title>{tick.title}</title>
                    <rect
                      x={x - 1.7}
                      y={y - 1.7}
                      width={3.4}
                      height={3.4}
                      transform={`rotate(45 ${x} ${y})`}
                    />
                  </motion.g>
                )
              })}
            </motion.g>
          )}

          {isRetro ? (
            isDaytime ? (
              <g>
                <circle className="sun-path-sun-halo" cx={sunX} cy={sunY} r={7} />
                <circle className="sun-path-sun-core" cx={sunX} cy={sunY} r={3.2} />
              </g>
            ) : (
              <g>
                <title>{`Moon, ${Math.round(moonIlluminatedFraction * 100)}% illuminated, ${moonPhase < 0.5 ? 'waxing' : 'waning'}`}</title>
                <circle className="sun-path-moon-shadow" cx={moonX} cy={moonY} r={MOON_R} />
                <circle
                  className="sun-path-moon-lit"
                  cx={moonX + moonLitOffsetX}
                  cy={moonY}
                  r={MOON_R}
                  clipPath={`url(#${MOON_CLIP_ID})`}
                />
              </g>
            )
          ) : (
            <AnimatePresence mode="wait" initial={false}>
              {isDaytime ? (
                <motion.g
                  key="sun"
                  initial={{ opacity: 0, scale: 0.5 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.5 }}
                  transition={groupTransition}
                  style={{ transformBox: 'fill-box', transformOrigin: 'center' }}
                >
                  <motion.circle
                    className="sun-path-sun-halo sun-path-marker-motion"
                    initial={{ cx: sunX, cy: sunY }}
                    animate={{ cx: sunX, cy: sunY }}
                    transition={markerTransition}
                    r={7}
                  />
                  <motion.circle
                    className="sun-path-sun-core sun-path-marker-motion"
                    initial={{ cx: sunX, cy: sunY }}
                    animate={{ cx: sunX, cy: sunY }}
                    transition={markerTransition}
                    r={3.2}
                    whileTap={prefersReducedMotion ? undefined : { scale: 0.85 }}
                  />
                </motion.g>
              ) : (
                <motion.g
                  key="moon"
                  initial={{ opacity: 0, scale: 0.5 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.5 }}
                  transition={groupTransition}
                  style={{ transformBox: 'fill-box', transformOrigin: 'center' }}
                >
                  <title>{`Moon, ${Math.round(moonIlluminatedFraction * 100)}% illuminated, ${moonPhase < 0.5 ? 'waxing' : 'waning'}`}</title>
                  <motion.circle
                    className="sun-path-moon-halo sun-path-marker-motion"
                    initial={{ cx: moonX, cy: moonY }}
                    animate={{ cx: moonX, cy: moonY }}
                    transition={markerTransition}
                    r={6}
                  />
                  <motion.circle
                    className="sun-path-moon-shadow sun-path-marker-motion"
                    initial={{ cx: moonX, cy: moonY }}
                    animate={{ cx: moonX, cy: moonY }}
                    transition={markerTransition}
                    r={MOON_R}
                  />
                  <motion.circle
                    className="sun-path-moon-lit sun-path-marker-motion"
                    initial={{ cx: moonX + moonLitOffsetX, cy: moonY }}
                    animate={{ cx: moonX + moonLitOffsetX, cy: moonY }}
                    transition={markerTransition}
                    r={MOON_R}
                    clipPath={`url(#${MOON_CLIP_ID})`}
                    whileTap={prefersReducedMotion ? undefined : { scale: 0.85 }}
                  />
                </motion.g>
              )}
            </AnimatePresence>
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
