import './CurrentWeatherCard.css'
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { AnimatePresence, motion, useAnimation, useReducedMotion } from 'framer-motion'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { LocationPinIcon, OutlookIcon } from './icons'
import { getConditionInfo } from '../utils/weatherCondition'
import { celsiusTo, formatTemperature } from '../utils/units'
import { buildInsights, pressureTrend } from '../utils/weatherInsights'
import { useCountUp } from '../hooks/useCountUp'
import type { WeatherCondition } from '../utils/weatherCondition'

const STAT_ICON_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.5,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true
}

const EASE_OUT = [0.16, 1, 0.3, 1] as const

/**
 * The selected city's wall clock, ticking every second. Returns a Date whose
 * *UTC* fields hold the city's local time (absolute now + the city's UTC
 * offset), so callers must format it with timeZone: 'UTC'.
 */
function useCityClock(utcOffsetSeconds: number): Date {
  const [shifted, setShifted] = useState(() => new Date(Date.now() + utcOffsetSeconds * 1000))

  useEffect(() => {
    const update = (): void => setShifted(new Date(Date.now() + utcOffsetSeconds * 1000))
    update()
    const id = setInterval(update, 1000)
    return () => clearInterval(id)
  }, [utcOffsetSeconds])

  return shifted
}

/** A condition-tinted ambient wash color for the hero backdrop — same spirit
 *  as WeatherIcon's per-condition glow, but written locally (warm for clear
 *  daylight, cool blue for rain/snow, violet for storms). */
function conditionWashColor(condition: WeatherCondition, isDay: boolean): string {
  switch (condition) {
    case 'clear':
      return isDay ? 'var(--accent-strong)' : 'var(--info)'
    case 'partly-cloudy':
      return isDay ? 'var(--accent)' : 'var(--info)'
    case 'thunderstorm':
      return 'var(--uv-extreme)'
    case 'snow':
      return 'color-mix(in srgb, var(--info) 60%, white)'
    case 'fog':
      return 'var(--text-tertiary)'
    case 'cloudy':
    case 'drizzle':
    case 'rain':
    default:
      return 'var(--info)'
  }
}

const STAT_CONTAINER_VARIANTS = {
  hidden: {},
  // delayChildren lands after BentoCard's own ~0.9s entrance settles, so the
  // stat pills stagger in as a distinct second beat rather than fighting the
  // card's own rise/de-blur for attention.
  visible: { transition: { staggerChildren: 0.08, delayChildren: 0.55 } }
}

export function CurrentWeatherCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  const theme = useWeatherStore((s) => s.theme)

  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()

  // Hooks run unconditionally (before the null guard) to satisfy hook rules.
  const cityClock = useCityClock(weather?.utcOffsetSeconds ?? 0)
  const targetTemp = weather ? Math.round(celsiusTo(unit, weather.current.temperature)) : 0
  const animatedTemp = useCountUp(targetTemp)
  const insight = useMemo(() => (weather ? buildInsights(weather, unit)[0].headline : ''), [weather, unit])
  // Keyed on `weather` (not the once-a-second city clock) so this hourly-series
  // scan doesn't re-run 48-72 Date parses every single tick — the live seconds
  // clock forces a re-render every second, but pressure trend only actually
  // changes when new data arrives.
  const trend = useMemo(() => (weather ? pressureTrend(weather, Date.now()) : null), [weather])

  // A brief spring "pop" + glow flash punctuates every settled temperature
  // change (new data, unit toggle) on top of the continuous count-up, so the
  // reading never just snaps — it rolls, then lands with a flourish.
  const tempPop = useAnimation()
  const prevTargetRef = useRef(targetTemp)
  useEffect(() => {
    if (isRetro || prefersReducedMotion) return
    if (prevTargetRef.current === targetTemp) return
    prevTargetRef.current = targetTemp
    void tempPop.start({
      scale: [1, 1.1, 1],
      filter: [
        'drop-shadow(0 0 0px transparent)',
        'drop-shadow(0 0 24px var(--accent-glow))',
        'drop-shadow(0 0 0px transparent)'
      ],
      transition: {
        scale: { type: 'spring', stiffness: 300, damping: 11 },
        filter: { duration: 0.7, ease: EASE_OUT }
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetTemp, isRetro, prefersReducedMotion])

  if (!weather) return null

  const { condition, label } = getConditionInfo(weather.current.weatherCode)
  const { name, admin1, country } = weather.location
  const placeSub = [admin1, country].filter(Boolean).join(' · ')

  const today = weather.daily.at(0)
  const pressure = `${Math.round(weather.current.pressure)} hPa`

  // City wall clock is encoded in the Date's UTC fields (see useCityClock).
  const clockDay = cityClock.toLocaleDateString('en-US', { weekday: 'long', timeZone: 'UTC' })
  const clockTime = cityClock.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'UTC'
  })

  const shownTemp = Math.round(animatedTemp)
  // Reserve the wider of current/final digit counts so the count-up never
  // shifts the layout (tabular numerals make every digit 1ch wide).
  const numeralCh = Math.max(String(shownTemp).length, String(targetTemp).length)
  const unitLetter = unit === 'fahrenheit' ? 'F' : 'C'

  // Movement distances collapse to 0 under prefers-reduced-motion — the
  // crossfades still happen (opacity only), but nothing slides.
  const slide = prefersReducedMotion ? 0 : 6
  const washColor = conditionWashColor(condition, weather.current.isDay)

  const statItemVariants = {
    hidden: { opacity: 0, y: prefersReducedMotion ? 0 : 10 },
    visible: { opacity: 1, y: 0, transition: { duration: 0.45, ease: EASE_OUT } }
  }
  // BentoCard already applies a whole-card hover scale+tilt; a large lift/scale
  // on these pills too would stack into a chaotic double-motion, so this is
  // kept to a subtle accent (small lift, no scale) that doesn't compete with it.
  const statWhileHover = prefersReducedMotion ? undefined : { y: -1 }
  const statWhileTap = prefersReducedMotion ? undefined : { scale: 0.97 }

  return (
    <BentoCard span="bento-hero">
      <div className="hero-current">
        {!isRetro && (
          <AnimatePresence>
            <motion.div
              key={`${condition}:${weather.current.isDay}`}
              className="hero-condition-wash"
              style={{ '--wash-color': washColor } as CSSProperties}
              aria-hidden="true"
              initial={{ opacity: 0 }}
              animate={{ opacity: prefersReducedMotion ? 0.36 : [0.24, 0.4, 0.24] }}
              exit={{ opacity: 0, transition: { duration: 0.6 } }}
              transition={
                prefersReducedMotion
                  ? { duration: 0.6 }
                  : { opacity: { duration: 7, repeat: Infinity, ease: 'easeInOut' } }
              }
            />
          </AnimatePresence>
        )}

        <div className="card-title hero-title">
          <LocationPinIcon />
          Current Conditions
        </div>

        <div className="hero-place">
          {isRetro ? (
            <div className="hero-place-name">{name}</div>
          ) : (
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={name}
                className="hero-place-name"
                initial={{ opacity: 0, y: -slide }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: slide }}
                transition={{ duration: 0.35, ease: EASE_OUT }}
              >
                {name}
              </motion.div>
            </AnimatePresence>
          )}
          {placeSub && <div className="hero-place-sub">{placeSub}</div>}
          <div className="hero-clock">
            <span className="hero-clock-dot" aria-hidden="true" />
            <span className="hero-clock-day">{clockDay}</span>
            <span className="hero-clock-sep" aria-hidden="true">
              ·
            </span>
            <span className="hero-clock-time">{clockTime}</span>
          </div>
        </div>

        <div className="hero-main">
          <span
            className="hero-temp"
            role="img"
            aria-label={`${targetTemp} degrees ${unit === 'fahrenheit' ? 'Fahrenheit' : 'Celsius'}`}
          >
            {isRetro ? (
              <span className="hero-temp-numeral" style={{ minWidth: `${numeralCh}ch` }} aria-hidden="true">
                {shownTemp}
              </span>
            ) : (
              <motion.span
                className="hero-temp-numeral"
                style={{ minWidth: `${numeralCh}ch` }}
                aria-hidden="true"
                animate={tempPop}
              >
                {shownTemp}
              </motion.span>
            )}
            <span className="hero-temp-unit" aria-hidden="true">
              °{unitLetter}
            </span>
          </span>
          <WeatherIcon condition={condition} isDay={weather.current.isDay} className="hero-icon" glowPulse />
        </div>

        <div className="hero-cond-group">
          {isRetro ? (
            <div className="hero-condition">{label}</div>
          ) : (
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={label}
                className="hero-condition"
                initial={{ opacity: 0, y: -slide }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: slide }}
                transition={{ duration: 0.3, ease: EASE_OUT }}
              >
                {label}
              </motion.div>
            </AnimatePresence>
          )}
          <div className="hero-feels">
            Feels like {formatTemperature(weather.current.apparentTemperature, unit)}
          </div>
        </div>

        <div className="hero-footer">
          {isRetro ? (
            <div className="hero-stats">
              {today && (
                <span
                  className="hero-stat hero-stat--high"
                  role="img"
                  aria-label={`Today's high ${formatTemperature(today.tempMax, unit)}`}
                >
                  <svg {...STAT_ICON_PROPS}>
                    <path d="M12 19V5" />
                    <path d="m5 12 7-7 7 7" />
                  </svg>
                  <span className="hero-stat-value">{formatTemperature(today.tempMax, unit)}</span>
                </span>
              )}
              {today && (
                <span
                  className="hero-stat hero-stat--low"
                  role="img"
                  aria-label={`Today's low ${formatTemperature(today.tempMin, unit)}`}
                >
                  <svg {...STAT_ICON_PROPS}>
                    <path d="M12 5v14" />
                    <path d="m19 12-7 7-7-7" />
                  </svg>
                  <span className="hero-stat-value">{formatTemperature(today.tempMin, unit)}</span>
                </span>
              )}
              <span
                className="hero-stat hero-stat--pressure"
                role="img"
                aria-label={trend ? `Pressure ${pressure}, ${trend}` : `Pressure ${pressure}`}
              >
                <svg {...STAT_ICON_PROPS}>
                  <path d="m12 14 4-4" />
                  <path d="M3.34 19a10 10 0 1 1 17.32 0" />
                </svg>
                <span className="hero-stat-value">{pressure}</span>
                {trend && (
                  <svg
                    className={`hero-stat-trend hero-stat-trend--${trend}`}
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2.6}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    {trend === 'steady' ? <path d="M5 12h14" /> : <path d="M6 15l6-6 6 6" />}
                  </svg>
                )}
              </span>
            </div>
          ) : (
            <motion.div className="hero-stats" variants={STAT_CONTAINER_VARIANTS} initial="hidden" animate="visible">
              {today && (
                <motion.span
                  className="hero-stat hero-stat--high"
                  role="img"
                  aria-label={`Today's high ${formatTemperature(today.tempMax, unit)}`}
                  variants={statItemVariants}
                  whileHover={statWhileHover}
                  whileTap={statWhileTap}
                  transition={{ type: 'spring', stiffness: 260, damping: 24 }}
                >
                  <svg {...STAT_ICON_PROPS}>
                    <path d="M12 19V5" />
                    <path d="m5 12 7-7 7 7" />
                  </svg>
                  <span className="hero-stat-value">{formatTemperature(today.tempMax, unit)}</span>
                </motion.span>
              )}
              {today && (
                <motion.span
                  className="hero-stat hero-stat--low"
                  role="img"
                  aria-label={`Today's low ${formatTemperature(today.tempMin, unit)}`}
                  variants={statItemVariants}
                  whileHover={statWhileHover}
                  whileTap={statWhileTap}
                  transition={{ type: 'spring', stiffness: 260, damping: 24 }}
                >
                  <svg {...STAT_ICON_PROPS}>
                    <path d="M12 5v14" />
                    <path d="m19 12-7 7-7-7" />
                  </svg>
                  <span className="hero-stat-value">{formatTemperature(today.tempMin, unit)}</span>
                </motion.span>
              )}
              <motion.span
                className="hero-stat hero-stat--pressure"
                role="img"
                aria-label={trend ? `Pressure ${pressure}, ${trend}` : `Pressure ${pressure}`}
                variants={statItemVariants}
                whileHover={statWhileHover}
                whileTap={statWhileTap}
                transition={{ type: 'spring', stiffness: 260, damping: 24 }}
              >
                <svg {...STAT_ICON_PROPS}>
                  <path d="m12 14 4-4" />
                  <path d="M3.34 19a10 10 0 1 1 17.32 0" />
                </svg>
                <span className="hero-stat-value">{pressure}</span>
                {trend && (
                  <svg
                    className={`hero-stat-trend hero-stat-trend--${trend}`}
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2.6}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    {trend === 'steady' ? <path d="M5 12h14" /> : <path d="M6 15l6-6 6 6" />}
                  </svg>
                )}
              </motion.span>
            </motion.div>
          )}

          <div className="hero-insight">
            <OutlookIcon />
            {isRetro ? (
              <span className="hero-insight-text">{insight}</span>
            ) : (
              <AnimatePresence mode="wait" initial={false}>
                <motion.span
                  key={insight}
                  className="hero-insight-text"
                  initial={{ opacity: 0, y: slide }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -slide, transition: { duration: 0.2 } }}
                  transition={{ duration: 0.3, ease: EASE_OUT }}
                >
                  {insight}
                </motion.span>
              </AnimatePresence>
            )}
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
