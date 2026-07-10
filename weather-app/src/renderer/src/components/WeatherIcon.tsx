import { AnimatePresence, motion } from 'framer-motion'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import type { WeatherCondition } from '../utils/weatherCondition'

interface WeatherIconProps {
  condition: WeatherCondition
  isDay: boolean
  className?: string
  /** Continuously pulses the glow via a per-frame filter animation -- real
   *  cost, so it's reserved for the one hero-sized instance on screen at a
   *  time (CurrentWeatherCard). Every list context (hourly cells, daily
   *  rows) can have a dozen-plus simultaneous instances; those get a static
   *  glow instead, since animating `filter` on that many elements at once
   *  runs entirely on the main thread and was a measurable source of jank. */
  glowPulse?: boolean
}

const SVG_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true
}

/** Round path/attribute numbers so the emitted SVG stays tidy. */
const rd = (n: number): number => Math.round(n * 100) / 100

const FULL_SUN_RAYS = [0, 45, 90, 135, 180, 225, 270, 315] as const
/** Rays fanned over the exposed top-right of the peeking sun only. */
const PEEK_SUN_RAYS = [225, 270, 315, 0] as const

/**
 * Elegant crescent for clear nights: one outer arc plus a slightly smaller
 * inner arc carving the light side — a single closed, seamless path.
 */
const CRESCENT_PATH = 'M20.46 12.74 A8.46 8.46 0 1 1 11.26 3.54 A6.58 6.58 0 0 0 20.46 12.74 Z'

/** Small crescent tucked behind the cloud for partly-cloudy nights. */
const MINI_CRESCENT_PATH = 'M19.33 7.78 A3.24 3.24 0 1 1 15.82 4.27 A2.52 2.52 0 0 0 19.33 7.78 Z'

/**
 * Sun disc for partly-cloudy days: an open arc (~277°) whose ends land
 * exactly on the cloud outline, so the disc reads as tucked behind it.
 */
const PEEK_SUN_ARC = 'M12.82 7.83 A2.8 2.8 0 1 1 14.9 10.91'

function Rays({
  cx,
  cy,
  inner,
  outer,
  angles
}: {
  cx: number
  cy: number
  inner: number
  outer: number
  angles: readonly number[]
}): JSX.Element {
  return (
    <>
      {angles.map((angle) => {
        const rad = (angle * Math.PI) / 180
        const cos = Math.cos(rad)
        const sin = Math.sin(rad)
        return (
          <line
            key={angle}
            x1={rd(cx + cos * inner)}
            y1={rd(cy + sin * inner)}
            x2={rd(cx + cos * outer)}
            y2={rd(cy + sin * outer)}
          />
        )
      })}
    </>
  )
}

/**
 * Cloud silhouette as one smooth closed path (no stacked circles, no seams):
 * a flat base, a half-circle right puff and a large arc sweeping the top and
 * left. `cx`/`cy` place the centre of the base line; `scale` sizes it
 * (bounding box is 22×16 units at scale 1).
 */
function CloudGlyph({ cx, cy, scale }: { cx: number; cy: number; scale: number }): JSX.Element {
  const s = scale
  const d = [
    `M${rd(cx + 6 * s)} ${rd(cy - 10 * s)}`,
    `h${rd(-1.26 * s)}`,
    `a${rd(8 * s)} ${rd(8 * s)} 0 1 0 ${rd(-7.74 * s)} ${rd(10 * s)}`,
    `h${rd(9 * s)}`,
    `a${rd(5 * s)} ${rd(5 * s)} 0 0 0 0 ${rd(-10 * s)}`,
    'Z'
  ].join(' ')
  return <path d={d} />
}

/** Neat 6-point asterisk: three strokes through the centre at 60° steps. */
function Snowflake({ cx, cy, r }: { cx: number; cy: number; r: number }): JSX.Element {
  const axes = [90, 30, 150]
  return (
    <>
      {axes.map((angle) => {
        const rad = (angle * Math.PI) / 180
        const dx = Math.cos(rad) * r
        const dy = Math.sin(rad) * r
        return (
          <line
            key={angle}
            x1={rd(cx - dx)}
            y1={rd(cy - dy)}
            x2={rd(cx + dx)}
            y2={rd(cy + dy)}
          />
        )
      })}
    </>
  )
}

/** The glyph markup only (no <svg> wrapper) so WeatherIcon can mount it under one shared, keyed <svg>. */
function Glyph({ condition, isDay }: { condition: WeatherCondition; isDay: boolean }): JSX.Element {
  switch (condition) {
    case 'clear':
      if (isDay) {
        return (
          <>
            <circle cx={12} cy={12} r={3.8} fill="currentColor" fillOpacity={0.15} />
            <Rays cx={12} cy={12} inner={5.7} outer={8} angles={FULL_SUN_RAYS} />
          </>
        )
      }
      return <path d={CRESCENT_PATH} />

    case 'partly-cloudy':
      if (isDay) {
        return (
          <>
            <path d={PEEK_SUN_ARC} />
            <Rays cx={15.6} cy={8.2} inner={4.6} outer={6} angles={PEEK_SUN_RAYS} />
            <CloudGlyph cx={11.2} cy={19.2} scale={0.8} />
          </>
        )
      }
      return (
        <>
          <path d={MINI_CRESCENT_PATH} />
          <CloudGlyph cx={11.2} cy={19.2} scale={0.8} />
        </>
      )

    case 'cloudy':
      return <CloudGlyph cx={12} cy={19.4} scale={0.92} />

    case 'fog':
      return (
        <>
          <CloudGlyph cx={12} cy={11.6} scale={0.6} />
          <line x1={4.5} y1={15} x2={19.5} y2={15} />
          <line x1={6.5} y1={18} x2={17.5} y2={18} />
          <line x1={9} y1={21} x2={15} y2={21} />
        </>
      )

    case 'drizzle':
      return (
        <>
          <CloudGlyph cx={12} cy={13} scale={0.68} />
          <line x1={8.6} y1={16} x2={8} y2={17.8} />
          <line x1={12.6} y1={16} x2={12} y2={17.8} />
          <line x1={16.6} y1={16} x2={16} y2={17.8} />
          <line x1={10.6} y1={19.4} x2={10} y2={21.2} />
          <line x1={14.6} y1={19.4} x2={14} y2={21.2} />
        </>
      )

    case 'rain':
      return (
        <>
          <CloudGlyph cx={12} cy={12.6} scale={0.68} />
          <line x1={9.1} y1={15.4} x2={7.3} y2={20.8} />
          <line x1={13.1} y1={15.4} x2={11.3} y2={20.8} />
          <line x1={17.1} y1={15.4} x2={15.3} y2={20.8} />
        </>
      )

    case 'snow':
      return (
        <>
          <CloudGlyph cx={12} cy={12.4} scale={0.68} />
          <g strokeWidth={1.35}>
            <Snowflake cx={9} cy={17.7} r={2.5} />
            <Snowflake cx={15.4} cy={19.1} r={2.5} />
          </g>
        </>
      )

    case 'thunderstorm':
      return (
        <>
          <CloudGlyph cx={12} cy={12} scale={0.66} />
          <path
            d="M13.7 12.4 L9.9 17.4 H12.3 L11.1 21.8 L15.3 15.6 H12.9 Z"
            fill="currentColor"
            stroke="currentColor"
            strokeWidth={1}
          />
        </>
      )

    default:
      return <CloudGlyph cx={12} cy={19.4} scale={0.92} />
  }
}

/** A condition-colored glow, reusing the same theme accent/info/uv-extreme
 *  tokens the rest of the app draws from rather than inventing new hues. */
function getGlowColor(condition: WeatherCondition, isDay: boolean): string {
  switch (condition) {
    case 'clear':
    case 'partly-cloudy':
      return isDay ? 'var(--accent-strong)' : 'var(--info)'
    case 'fog':
      return 'color-mix(in srgb, var(--text-tertiary) 50%, white)'
    case 'snow':
      return 'color-mix(in srgb, var(--info) 55%, white)'
    case 'thunderstorm':
      return 'var(--uv-extreme)'
    case 'cloudy':
    case 'drizzle':
    case 'rain':
    default:
      return 'var(--info)'
  }
}

const GLOW_MIN_PX = 4
const GLOW_MAX_PX = 10

export function WeatherIcon({ condition, isDay, className, glowPulse = false }: WeatherIconProps): JSX.Element {
  const props = {
    ...SVG_PROPS,
    className: className ? `weather-icon-glyph ${className}` : 'weather-icon-glyph'
  }
  const theme = useWeatherStore((s) => s.theme)
  const weather = useWeatherStore((s) => s.weather)
  const isRetro = resolveTheme(theme, weather) === 'win95'

  // win95 keeps the original plain behavior: keying on condition+isDay forces
  // a fresh <svg> node so the glyph swaps instantly with no animation, no glow
  // -- Windows 95 icons never breathed or morphed.
  if (isRetro) {
    return (
      <svg key={`${condition}:${isDay}`} {...props}>
        <Glyph condition={condition} isDay={isDay} />
      </svg>
    )
  }

  const glowColor = getGlowColor(condition, isDay)
  const staticGlow = `drop-shadow(0 0 ${GLOW_MIN_PX}px ${glowColor})`

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.svg
        key={`${condition}:${isDay}`}
        {...props}
        initial={{ opacity: 0, scale: 0.65, rotate: -10 }}
        animate={{
          opacity: 1,
          scale: 1,
          rotate: 0,
          filter: glowPulse
            ? [
                `drop-shadow(0 0 ${GLOW_MIN_PX}px ${glowColor})`,
                `drop-shadow(0 0 ${GLOW_MAX_PX}px ${glowColor})`,
                `drop-shadow(0 0 ${GLOW_MIN_PX}px ${glowColor})`
              ]
            : staticGlow
        }}
        exit={{ opacity: 0, scale: 0.65, rotate: 10, transition: { duration: 0.15 } }}
        transition={{
          opacity: { duration: 0.3 },
          scale: { type: 'spring', stiffness: 300, damping: 15 },
          rotate: { type: 'spring', stiffness: 300, damping: 15 },
          filter: glowPulse ? { duration: 3.2, repeat: Infinity, ease: 'easeInOut' } : { duration: 0.3 }
        }}
      >
        <Glyph condition={condition} isDay={isDay} />
      </motion.svg>
    </AnimatePresence>
  )
}
