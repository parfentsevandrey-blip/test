import { useEffect, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion, useReducedMotion, type PanInfo } from 'framer-motion'
import './DailyForecast.css'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { CalendarIcon } from './icons'
import { getConditionInfo } from '../utils/weatherCondition'
import {
  formatClock,
  formatSpeed,
  formatTemperature,
  formatWeekday,
  speedUnitFor
} from '../utils/units'
import type { DailyForecastPoint, TemperatureUnit } from '../types/weather'

/** Minimum visible fill width (% of track) so single-degree days don't vanish. */
const MIN_FILL_PCT = 6

const EASE_OUT = [0.16, 1, 0.3, 1] as const

const clampPct = (value: number): number => Math.min(100, Math.max(0, value))

/** Noon avoids UTC-parse off-by-one-day issues (same trick as formatWeekday). */
const atNoon = (isoDate: string): Date => new Date(`${isoDate}T12:00:00`)

const formatDateShort = (isoDate: string): string =>
  atNoon(isoDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })

const formatWeekdayLong = (isoDate: string): string =>
  atNoon(isoDate).toLocaleDateString('en-US', { weekday: 'long' })

/** Staggered row entrance (non-retro only) — the same cascade shape used by
 *  every other forecast list in the app (see HourlyForecast.tsx). */
const rowsContainerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.05, delayChildren: 0.04 } }
}

const rowVariants = {
  hidden: { opacity: 0, y: 18, scale: 0.97 },
  visible: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { duration: 0.45, ease: EASE_OUT }
  }
}

const detailGridVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.045, delayChildren: 0.1 } }
}

const detailStatVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.32, ease: EASE_OUT } }
}

interface DailyStat {
  label: string
  value: string
  /** High/Low get the hero gradient-text treatment in the popover. */
  hero?: boolean
}

interface DailyDetailPopoverProps {
  day: DailyForecastPoint
  isToday: boolean
  unit: TemperatureUnit
  /** Same left/width/background-position window used by this day's row bar,
   *  computed once by the parent so both bars read the same underlying scale. */
  fillStyle: CSSProperties
  onClose: () => void
}

/**
 * Drill-down for a single day, using only data the daily forecast already
 * fetches (peak wind/UV, sunrise/sunset) — no new request. An overlay
 * rather than in-place row expansion, since the card is a fixed 10-row,
 * zero-slack layout with no room to grow a row taller.
 *
 * Non-retro: the condition icon shares a layoutId with the row that opened
 * it, so it visually morphs from the small list glyph into the popover's
 * hero icon; AnimatePresence (owned by the parent) drives mount/exit, and
 * the panel can be dragged down to dismiss. win95 renders the original
 * plain, instant, non-motion popover untouched.
 */
function DailyDetailPopover({
  day,
  isToday,
  unit,
  fillStyle,
  onClose
}: DailyDetailPopoverProps): JSX.Element {
  const theme = useWeatherStore((s) => s.theme)
  const weather = useWeatherStore((s) => s.weather)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()

  const condition = getConditionInfo(day.weatherCode)
  const speedUnit = speedUnitFor(unit)

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const stats: DailyStat[] = [
    { label: 'High', value: formatTemperature(day.tempMax, unit), hero: true },
    { label: 'Low', value: formatTemperature(day.tempMin, unit), hero: true },
    { label: 'Rain chance', value: `${Math.round(day.precipitationProbabilityMax)}%` },
    { label: 'Peak wind', value: formatSpeed(day.windSpeedMax, speedUnit) },
    ...(day.uvIndexMax !== null
      ? [{ label: 'Peak UV', value: `${Math.round(day.uvIndexMax)}` }]
      : []),
    { label: 'Sunrise', value: formatClock(day.sunrise) },
    { label: 'Sunset', value: formatClock(day.sunset) }
  ]

  // ---------- win95: original plain, instant, non-motion popover ----------
  if (isRetro) {
    return createPortal(
      <div className="df-detail-overlay" onMouseDown={onClose}>
        <div
          className="df-detail-panel glass-panel"
          role="dialog"
          aria-modal="true"
          aria-label={`${isToday ? 'Today' : formatWeekdayLong(day.date)} details`}
          onMouseDown={(event) => event.stopPropagation()}
        >
          <div className="df-detail-header">
            <WeatherIcon condition={condition.condition} isDay={true} className="df-detail-icon" />
            <div className="df-detail-heading">
              <span className="df-detail-day">{isToday ? 'Today' : formatWeekdayLong(day.date)}</span>
              <span className="df-detail-date">{formatDateShort(day.date)}</span>
            </div>
            <button type="button" className="icon-btn df-detail-close" aria-label="Close" onClick={onClose}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" aria-hidden="true">
                <line x1={6} y1={6} x2={18} y2={18} />
                <line x1={18} y1={6} x2={6} y2={18} />
              </svg>
            </button>
          </div>

          <div className="df-detail-condition">{condition.label}</div>

          <div className="df-detail-grid">
            {stats.map((stat) => (
              <div className="df-detail-stat" key={stat.label}>
                <span className="df-detail-stat-label">{stat.label}</span>
                <span className="df-detail-stat-value">{stat.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>,
      document.body
    )
  }

  // ---------- Every other theme: full Framer Motion treatment ----------
  const layoutTransition = prefersReducedMotion
    ? { duration: 0 }
    : { type: 'spring' as const, stiffness: 300, damping: 28 }

  const handleDragEnd = (_event: MouseEvent | TouchEvent | PointerEvent, info: PanInfo): void => {
    if (info.offset.y > 110 || info.velocity.y > 650) onClose()
  }

  return createPortal(
    <motion.div
      className="df-detail-overlay"
      onMouseDown={onClose}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, transition: { duration: 0.18 } }}
      transition={{ duration: 0.22 }}
    >
      <motion.div
        className="df-detail-panel glass-panel"
        role="dialog"
        aria-modal="true"
        aria-label={`${isToday ? 'Today' : formatWeekdayLong(day.date)} details`}
        onMouseDown={(event) => event.stopPropagation()}
        drag={prefersReducedMotion ? false : 'y'}
        dragConstraints={{ top: 0, bottom: 0 }}
        dragElastic={0.5}
        onDragEnd={prefersReducedMotion ? undefined : handleDragEnd}
        whileDrag={prefersReducedMotion ? undefined : { scale: 0.98 }}
        initial={{ opacity: 0, scale: 0.86, y: 26 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.9, y: 16, transition: { duration: 0.18 } }}
        transition={prefersReducedMotion ? { duration: 0.12 } : { type: 'spring', stiffness: 300, damping: 28 }}
      >
        <div className="df-detail-header">
          <motion.div
            className="df-icon-wrap df-detail-icon-wrap"
            layoutId={`df-icon-${day.date}`}
            transition={layoutTransition}
          >
            <WeatherIcon condition={condition.condition} isDay={true} className="df-detail-icon" />
          </motion.div>
          <div className="df-detail-heading">
            <span className="df-detail-day">{isToday ? 'Today' : formatWeekdayLong(day.date)}</span>
            <span className="df-detail-date">{formatDateShort(day.date)}</span>
          </div>
          <motion.button
            type="button"
            className="icon-btn df-detail-close"
            aria-label="Close"
            onClick={onClose}
            whileHover={prefersReducedMotion ? undefined : { scale: 1.12, rotate: 90 }}
            whileTap={prefersReducedMotion ? undefined : { scale: 0.9 }}
            transition={{ type: 'spring', stiffness: 400, damping: 20 }}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" aria-hidden="true">
              <line x1={6} y1={6} x2={18} y2={18} />
              <line x1={18} y1={6} x2={6} y2={18} />
            </svg>
          </motion.button>
        </div>

        <div className="df-detail-condition">{condition.label}</div>

        {/* Gradient-accented range bar, new in this pass: same info→accent
            window as the row's bar, given a soft glow befitting the drill-down. */}
        <div className="df-detail-range">
          <span className="df-detail-range-min">{formatTemperature(day.tempMin, unit)}</span>
          <div className="df-track df-detail-track">
            <motion.div
              className="df-fill df-detail-fill"
              style={fillStyle}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{
                duration: prefersReducedMotion ? 0 : 0.55,
                delay: prefersReducedMotion ? 0 : 0.18,
                ease: EASE_OUT
              }}
            />
          </div>
          <span className="df-detail-range-max">{formatTemperature(day.tempMax, unit)}</span>
        </div>

        <motion.div
          className="df-detail-grid"
          initial={prefersReducedMotion ? false : 'hidden'}
          animate={prefersReducedMotion ? undefined : 'visible'}
          variants={detailGridVariants}
        >
          {stats.map((stat) => (
            <motion.div className="df-detail-stat" key={stat.label} variants={detailStatVariants}>
              <span className="df-detail-stat-label">{stat.label}</span>
              <span
                className={`df-detail-stat-value${stat.hero ? ' df-detail-stat-value--hero' : ''}`}
              >
                {stat.value}
              </span>
            </motion.div>
          ))}
        </motion.div>
      </motion.div>
    </motion.div>,
    document.body
  )
}

export function DailyForecast(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  const theme = useWeatherStore((s) => s.theme)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  if (!weather) return null

  const selectedDay = selectedDate ? weather.daily.find((d) => d.date === selectedDate) : undefined

  const globalMin = Math.min(...weather.daily.map((day) => day.tempMin))
  const globalMax = Math.max(...weather.daily.map((day) => day.tempMax))
  const span = globalMax - globalMin

  const pct = (temperature: number): number =>
    span === 0 ? 50 : clampPct(((temperature - globalMin) / span) * 100)

  const nowPct = pct(weather.current.temperature)

  // Window into the full-scale gradient (see .df-fill in the CSS) — shared by
  // both a row's bar and (when that row is selected) the popover's bar.
  const fillStyleFor = (day: DailyForecastPoint): CSSProperties => {
    const minPct = pct(day.tempMin)
    const maxPct = pct(day.tempMax)
    const width = Math.max(maxPct - minPct, MIN_FILL_PCT)
    const left = Math.min(minPct, 100 - width)
    return {
      left: `${left}%`,
      width: `${width}%`,
      backgroundSize: `${10000 / width}% 100%`,
      backgroundPositionX: width >= 100 ? '0%' : `${(100 * left) / (100 - width)}%`
    }
  }

  const rows = weather.daily.map((day, index) => {
    const isToday = index === 0
    const condition = getConditionInfo(day.weatherCode)
    const pop = Math.round(day.precipitationProbabilityMax)
    const fillStyle = fillStyleFor(day)

    const label =
      `${isToday ? 'Today' : formatWeekdayLong(day.date)}, ${formatDateShort(day.date)}: ` +
      `${condition.label}, high ${formatTemperature(day.tempMax, unit)}, ` +
      `low ${formatTemperature(day.tempMin, unit)}` +
      (pop > 0 ? `, ${pop}% chance of precipitation` : '')

    const dayCol = (
      <div className="df-day-col">
        <span className="df-day">{isToday ? 'Today' : formatWeekday(day.date)}</span>
        <span className="df-date">{formatDateShort(day.date)}</span>
      </div>
    )

    const popSpan = <span className={`df-pop${pop < 20 ? ' df-pop--low' : ''}`}>{`${pop}%`}</span>

    if (isRetro) {
      return (
        <button
          type="button"
          className={`df-row${isToday ? ' is-today' : ''}`}
          role="listitem"
          aria-label={`${label}. Show details.`}
          aria-haspopup="dialog"
          title={condition.label}
          key={day.date}
          onClick={() => setSelectedDate(day.date)}
        >
          {dayCol}
          <WeatherIcon condition={condition.condition} isDay={true} className="df-icon" />
          {popSpan}
          <div className="df-range">
            <span className="df-temp-min">{formatTemperature(day.tempMin, unit)}</span>
            <div className="df-track">
              <div className="df-fill" style={fillStyle} />
              {isToday && <div className="df-now-dot" style={{ left: `${nowPct}%` }} aria-hidden="true" />}
            </div>
            <span className="df-temp-max">{formatTemperature(day.tempMax, unit)}</span>
          </div>
        </button>
      )
    }

    return (
      <motion.button
        type="button"
        className={`df-row${isToday ? ' is-today' : ''}`}
        role="listitem"
        aria-label={`${label}. Show details.`}
        aria-haspopup="dialog"
        title={condition.label}
        key={day.date}
        onClick={() => setSelectedDate(day.date)}
        variants={rowVariants}
        whileHover={prefersReducedMotion ? undefined : { y: -4, scale: 1.015 }}
        whileFocus={prefersReducedMotion ? undefined : { y: -4, scale: 1.015 }}
        whileTap={prefersReducedMotion ? undefined : { scale: 0.985 }}
        transition={{ type: 'spring', stiffness: 380, damping: 22 }}
      >
        {dayCol}
        <motion.div
          className="df-icon-wrap"
          layoutId={`df-icon-${day.date}`}
          transition={
            prefersReducedMotion ? { duration: 0 } : { type: 'spring', stiffness: 300, damping: 28 }
          }
        >
          <WeatherIcon condition={condition.condition} isDay={true} className="df-icon" />
        </motion.div>
        {popSpan}
        <div className="df-range">
          <span className="df-temp-min">{formatTemperature(day.tempMin, unit)}</span>
          <div className="df-track">
            <div className="df-fill" style={fillStyle} />
            {isToday &&
              (prefersReducedMotion ? (
                <div className="df-now-dot" style={{ left: `${nowPct}%` }} aria-hidden="true" />
              ) : (
                <motion.div
                  className="df-now-dot"
                  style={{ left: `${nowPct}%` }}
                  aria-hidden="true"
                  animate={{ scale: [1, 1.35, 1], opacity: [1, 0.7, 1] }}
                  transition={{ duration: 2.2, repeat: Infinity, ease: 'easeInOut' }}
                />
              ))}
          </div>
          <span className="df-temp-max">{formatTemperature(day.tempMax, unit)}</span>
        </div>
      </motion.button>
    )
  })

  return (
    <BentoCard span="bento-daily" floatDelay={0.3}>
      <div className="df-root">
        <div className="card-title df-title">
          <CalendarIcon />
          10-Day Forecast
        </div>

        {isRetro ? (
          <div className="df-rows" role="list" aria-label="Daily forecast for the next 10 days">
            {rows}
          </div>
        ) : (
          <motion.div
            className="df-rows"
            role="list"
            aria-label="Daily forecast for the next 10 days"
            initial={prefersReducedMotion ? false : 'hidden'}
            animate={prefersReducedMotion ? undefined : 'visible'}
            variants={rowsContainerVariants}
          >
            {rows}
          </motion.div>
        )}
      </div>

      <AnimatePresence>
        {selectedDay && (
          <DailyDetailPopover
            key={selectedDay.date}
            day={selectedDay}
            isToday={selectedDay.date === weather.daily[0].date}
            unit={unit}
            fillStyle={fillStyleFor(selectedDay)}
            onClose={() => setSelectedDate(null)}
          />
        )}
      </AnimatePresence>
    </BentoCard>
  )
}
