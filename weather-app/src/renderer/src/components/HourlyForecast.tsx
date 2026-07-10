import { useEffect, useId, useMemo, useState } from 'react'
import type { CSSProperties, KeyboardEvent, WheelEvent } from 'react'
import { AnimatePresence, animate, motion, useMotionValue, useReducedMotion } from 'framer-motion'
import './HourlyForecast.css'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { ClockIcon } from './icons'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature, formatHour, celsiusTo } from '../utils/units'

/**
 * Fixed column width in CSS px. Hour cells, precip cells and the SVG curve
 * all derive their horizontal metrics from this one constant, which is what
 * keeps every curve point centred under its column while they scroll as one.
 */
const COL_WIDTH = 56
/** SVG viewBox height of the curve band (CSS stretches it to the on-screen height). */
const BAND_HEIGHT = 48
/** Inner padding of the band so the stroke and the "Now" dot never clip. */
const BAND_PAD = 7
const HOURS_SHOWN = 24
/** Index of the "Now" column (the strip always starts at the current hour). */
const NOW_INDEX = 0
/** Columns whose centre is closer than this to a strip edge get an edge-aligned tooltip. */
const TIP_EDGE_PX = 150
const EASE_OUT = [0.16, 1, 0.3, 1] as const

interface CurvePoint {
  x: number
  y: number
}

const round2 = (n: number): number => Math.round(n * 100) / 100
const clampNum = (n: number, min: number, max: number): number => Math.min(Math.max(n, min), max)

/**
 * Smooth open path through every point: Catmull-Rom spline converted to
 * cubic beziers (endpoints clamped by duplicating the first/last point).
 */
function buildCurvePath(points: CurvePoint[]): string {
  if (points.length === 0) return ''
  let d = `M ${round2(points[0].x)} ${round2(points[0].y)}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] ?? points[i]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[i + 2] ?? p2
    const c1x = p1.x + (p2.x - p0.x) / 6
    const c1y = p1.y + (p2.y - p0.y) / 6
    const c2x = p2.x - (p3.x - p1.x) / 6
    const c2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${round2(c1x)} ${round2(c1y)}, ${round2(c2x)} ${round2(c2y)}, ${round2(p2.x)} ${round2(p2.y)}`
  }
  return d
}

/** Staggered entrance for the hourly cells (non-retro only). */
const cellsContainerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.018, delayChildren: 0.04 } }
}

const cellVariants = {
  hidden: { opacity: 0, y: 14, scale: 0.92 },
  visible: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { duration: 0.4, ease: EASE_OUT }
  }
}

export function HourlyForecast(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  const theme = useWeatherStore((s) => s.theme)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()
  const reactId = useId()
  // Hovered column index. Set from onMouseEnter per column (cheap, no
  // mousemove math) and cleared when the pointer leaves the whole strip.
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)

  // The strip's horizontal offset lives in one MotionValue so drag, wheel
  // and keyboard paging all read/write the same source of truth without
  // triggering a React re-render on every frame.
  const stripX = useMotionValue(0)
  const [scrollEl, setScrollEl] = useState<HTMLDivElement | null>(null)
  const [containerWidth, setContainerWidth] = useState(0)

  useEffect(() => {
    if (!scrollEl) return
    const update = (): void => setContainerWidth(scrollEl.clientWidth)
    update()
    const ro = new ResizeObserver(update)
    ro.observe(scrollEl)
    return () => ro.disconnect()
  }, [scrollEl])

  // Both the 24-point window and the SVG curve built from it only depend on
  // the fetched weather data (and the unit-independent temperature values),
  // never on hoveredIndex -- memoized so hovering across cells/pops (which
  // updates hoveredIndex on every mouseenter) doesn't re-run the Catmull-Rom
  // spline + path-string building on every single column crossed.
  const points = useMemo(() => {
    if (!weather) return []
    const flooredHour = `${weather.current.time.slice(0, 13)}:00`
    const startIndex = weather.hourly.findIndex((h) => h.time === flooredHour)
    const from = startIndex === -1 ? 0 : startIndex
    return weather.hourly.slice(from, from + HOURS_SHOWN)
  }, [weather])

  const curve = useMemo(() => {
    if (points.length === 0) return null
    const stripWidth = points.length * COL_WIDTH

    // Map the window's min..max temperature onto the band's vertical extent.
    const temps = points.map((p) => p.temperature)
    const minTemp = Math.min(...temps)
    const maxTemp = Math.max(...temps)
    const tempSpan = maxTemp - minTemp
    const curvePoints: CurvePoint[] = temps.map((t, i) => ({
      x: i * COL_WIDTH + COL_WIDTH / 2,
      y:
        tempSpan === 0
          ? BAND_HEIGHT / 2
          : BAND_PAD + ((maxTemp - t) / tempSpan) * (BAND_HEIGHT - BAND_PAD * 2)
    }))

    const linePath = buildCurvePath(curvePoints)
    const firstPoint = curvePoints[0]
    const lastPoint = curvePoints[curvePoints.length - 1]
    const areaPath = `${linePath} L ${round2(lastPoint.x)} ${BAND_HEIGHT} L ${round2(firstPoint.x)} ${BAND_HEIGHT} Z`
    const nowPoint = curvePoints[NOW_INDEX]
    return { stripWidth, linePath, areaPath, nowPoint }
  }, [points])

  if (!weather || points.length === 0 || !curve) return null

  const { stripWidth, linePath, areaPath, nowPoint } = curve

  // Unique, CSS-safe gradient id (useId can emit ":" which breaks url(#...)).
  const gradientId = `hf-grad-${reactId.replace(/[^a-zA-Z0-9_-]/g, '')}`

  const stripStyle = {
    width: `${stripWidth}px`,
    '--hf-col-w': `${COL_WIDTH}px`
  } as CSSProperties

  // How far the strip can travel: 0 (start) to -(overflow) (fully scrolled).
  const maxOverflow = Math.max(stripWidth - containerWidth, 0)
  const dragConstraints = { left: -maxOverflow, right: 0 }

  // Guard against a stale index if the data window shrank under the cursor.
  const hovered = hoveredIndex !== null && hoveredIndex < points.length ? hoveredIndex : null
  const hoveredPoint = hovered === null ? null : points[hovered]

  // Tooltip anchoring: centred over the column, except near the strip's
  // ends where it left/right-aligns so it can never clip out of the card.
  let tipLeft = 0
  let tipAlign = ''
  if (hovered !== null) {
    const center = hovered * COL_WIDTH + COL_WIDTH / 2
    if (center < TIP_EDGE_PX) {
      tipAlign = ' is-left'
      tipLeft = hovered * COL_WIDTH + 3
    } else if (stripWidth - center < TIP_EDGE_PX) {
      tipAlign = ' is-right'
      tipLeft = hovered * COL_WIDTH + COL_WIDTH - 3
    } else {
      tipLeft = center
    }
  }

  // Wheel scroll: preserved even though native overflow is off (the strip
  // now scrolls via transform so the drag gesture and wheel share one
  // MotionValue instead of fighting over native scrollLeft).
  const handleWheel = (event: WheelEvent<HTMLDivElement>): void => {
    if (isRetro || maxOverflow <= 0) return
    const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
    if (delta === 0) return
    event.preventDefault()
    stripX.set(clampNum(stripX.get() - delta, -maxOverflow, 0))
  }

  // Keyboard paging on the focusable region: Left/Right nudge by ~3/4 of a
  // viewport, Home/End jump to the ends. Springs unless motion is reduced.
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>): void => {
    if (isRetro || maxOverflow <= 0) return
    const step = Math.max(containerWidth * 0.75, COL_WIDTH * 2)
    let next: number | null = null
    if (event.key === 'ArrowLeft') next = clampNum(stripX.get() + step, -maxOverflow, 0)
    else if (event.key === 'ArrowRight') next = clampNum(stripX.get() - step, -maxOverflow, 0)
    else if (event.key === 'Home') next = 0
    else if (event.key === 'End') next = -maxOverflow
    if (next === null) return
    event.preventDefault()
    animate(stripX, next, prefersReducedMotion ? { duration: 0 } : { type: 'spring', stiffness: 260, damping: 32 })
  }

  const nowHighlight = (
    <div
      className="hf-now-highlight"
      style={{ left: NOW_INDEX * COL_WIDTH + 2, width: COL_WIDTH - 4 }}
      aria-hidden="true"
    />
  )

  const hoverHighlight = (
    <div
      className="hf-hover-highlight"
      style={{
        // transform instead of left: sliding the pill between columns is a
        // compositor-only reposition this way; animating `left` would force
        // layout+paint on every hovered cell crossed during a hover sweep.
        transform: `translateX(${(hovered ?? NOW_INDEX) * COL_WIDTH + 2}px)`,
        width: COL_WIDTH - 4,
        opacity: hovered !== null && hovered !== NOW_INDEX ? 1 : 0
      }}
      aria-hidden="true"
    />
  )

  const renderCellContent = (point: (typeof points)[number], index: number): JSX.Element => (
    <>
      <span className="hf-time">{index === NOW_INDEX ? 'Now' : formatHour(point.time)}</span>
      <WeatherIcon
        condition={getConditionInfo(point.weatherCode).condition}
        isDay={point.isDay}
        className="hf-icon"
      />
      <span className="hf-temp">
        {Math.round(celsiusTo(unit, point.temperature))}
        <span className="hf-deg">°</span>
      </span>
    </>
  )

  const bandAndPops = (
    <>
      {/* The curve band also hosts the hover tooltip so the chip always
          stays inside the card (never clips at the card's top edge). */}
      <div className="hf-curve-band">
        <svg
          className="hf-curve"
          viewBox={`0 0 ${stripWidth} ${BAND_HEIGHT}`}
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.18" />
              <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
            </linearGradient>
          </defs>
          <path className="hf-curve-area" d={areaPath} fill={`url(#${gradientId})`} stroke="none" />
          <path className="hf-curve-line" d={linePath} />
          <circle
            className="hf-curve-dot-ring"
            cx={round2(nowPoint.x)}
            cy={round2(nowPoint.y)}
            r={3.2}
          />
          <circle className="hf-curve-dot" cx={round2(nowPoint.x)} cy={round2(nowPoint.y)} r={3.2} />
        </svg>

        {isRetro ? (
          hoveredPoint &&
          hovered !== null && (
            <div className={`hf-tooltip${tipAlign}`} key={hovered} style={{ left: tipLeft }} aria-hidden="true">
              <span className="hf-tooltip-main">
                {hovered === NOW_INDEX ? 'Now' : formatHour(hoveredPoint.time)}
                {' · '}
                {formatTemperature(hoveredPoint.temperature, unit)}
                {' · '}
                {Math.round(hoveredPoint.precipitationProbability)}%
              </span>
              <span className="hf-tooltip-cond">{getConditionInfo(hoveredPoint.weatherCode).label}</span>
            </div>
          )
        ) : (
          <AnimatePresence>
            {hoveredPoint && hovered !== null && (
              <motion.div
                className={`hf-tooltip${tipAlign}`}
                key={hovered}
                style={{ left: tipLeft }}
                aria-hidden="true"
                initial={{ opacity: 0, scale: 0.86, y: 4 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.9, y: 2, transition: { duration: 0.12 } }}
                transition={{ type: 'spring', stiffness: 420, damping: 28 }}
              >
                <span className="hf-tooltip-main">
                  {hovered === NOW_INDEX ? 'Now' : formatHour(hoveredPoint.time)}
                  {' · '}
                  {formatTemperature(hoveredPoint.temperature, unit)}
                  {' · '}
                  {Math.round(hoveredPoint.precipitationProbability)}%
                </span>
                <span className="hf-tooltip-cond">{getConditionInfo(hoveredPoint.weatherCode).label}</span>
              </motion.div>
            )}
          </AnimatePresence>
        )}
      </div>

      <div className="hf-pops">
        {points.map((point, index) => (
          <span
            className={'hf-pop' + (Math.round(point.precipitationProbability) >= 30 ? ' is-notable' : '')}
            key={point.time}
            onMouseEnter={() => setHoveredIndex(index)}
          >
            {Math.round(point.precipitationProbability)}%
          </span>
        ))}
      </div>
    </>
  )

  return (
    <BentoCard span="bento-hourly">
      <div className="hourly-forecast">
        <div className="card-title hf-title">
          <ClockIcon />
          Next 24 Hours
        </div>
        <div
          className="hf-scroll"
          role="region"
          aria-label="Hourly forecast for the next 24 hours, scroll horizontally for later hours"
          tabIndex={0}
          ref={setScrollEl}
          onWheel={handleWheel}
          onKeyDown={handleKeyDown}
        >
          {isRetro ? (
            <div className="hf-strip" style={stripStyle} onMouseLeave={() => setHoveredIndex(null)}>
              {nowHighlight}
              {hoverHighlight}
              <div className="hf-cells">
                {points.map((point, index) => (
                  <div
                    className={'hf-cell' + (index === NOW_INDEX ? ' is-now' : '')}
                    key={point.time}
                    onMouseEnter={() => setHoveredIndex(index)}
                  >
                    {renderCellContent(point, index)}
                  </div>
                ))}
              </div>
              {bandAndPops}
            </div>
          ) : (
            <motion.div
              className="hf-strip"
              style={{ ...stripStyle, x: stripX }}
              onMouseLeave={() => setHoveredIndex(null)}
              drag="x"
              dragConstraints={dragConstraints}
              dragElastic={prefersReducedMotion ? 0 : 0.06}
              dragMomentum={!prefersReducedMotion}
              dragTransition={
                prefersReducedMotion
                  ? { power: 0, timeConstant: 0 }
                  : { power: 0.35, timeConstant: 250, bounceStiffness: 420, bounceDamping: 40 }
              }
            >
              {nowHighlight}
              {hoverHighlight}
              <motion.div
                className="hf-cells"
                initial={prefersReducedMotion ? false : 'hidden'}
                animate={prefersReducedMotion ? undefined : 'visible'}
                variants={cellsContainerVariants}
              >
                {points.map((point, index) => (
                  <motion.div
                    className={'hf-cell' + (index === NOW_INDEX ? ' is-now' : '')}
                    key={point.time}
                    variants={cellVariants}
                    onMouseEnter={() => setHoveredIndex(index)}
                    // Subtle accent only -- the card this cell lives in already
                    // gets its own hover scale from BentoCard, so a big lift/scale
                    // here on top of that read as two competing motions at once.
                    whileHover={prefersReducedMotion ? undefined : { y: -1, scale: 1.02 }}
                    whileTap={prefersReducedMotion ? undefined : { scale: 0.98 }}
                    transition={{ type: 'spring', stiffness: 340, damping: 28 }}
                  >
                    {renderCellContent(point, index)}
                  </motion.div>
                ))}
              </motion.div>
              {bandAndPops}
            </motion.div>
          )}
        </div>
      </div>
    </BentoCard>
  )
}
