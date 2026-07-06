import { useId, useState } from 'react'
import type { CSSProperties } from 'react'
import './HourlyForecast.css'
import { useWeatherStore } from '../store/useWeatherStore'
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

interface CurvePoint {
  x: number
  y: number
}

const round2 = (n: number): number => Math.round(n * 100) / 100

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

export function HourlyForecast(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  const reactId = useId()
  // Hovered column index. Set from onMouseEnter per column (cheap, no
  // mousemove math) and cleared when the pointer leaves the whole strip.
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)

  if (!weather) return null

  const flooredHour = `${weather.current.time.slice(0, 13)}:00`
  const startIndex = weather.hourly.findIndex((h) => h.time === flooredHour)
  const from = startIndex === -1 ? 0 : startIndex
  const points = weather.hourly.slice(from, from + HOURS_SHOWN)

  if (points.length === 0) return null

  // Unique, CSS-safe gradient id (useId can emit ":" which breaks url(#...)).
  const gradientId = `hf-grad-${reactId.replace(/[^a-zA-Z0-9_-]/g, '')}`
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

  const stripStyle = {
    width: `${stripWidth}px`,
    '--hf-col-w': `${COL_WIDTH}px`
  } as CSSProperties

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
        >
          <div
            className="hf-strip"
            style={stripStyle}
            onMouseLeave={() => setHoveredIndex(null)}
          >
            <div
              className="hf-now-highlight"
              style={{ left: NOW_INDEX * COL_WIDTH + 2, width: COL_WIDTH - 4 }}
              aria-hidden="true"
            />
            {/* Neutral hover pill: glides between columns, fades when idle. */}
            <div
              className="hf-hover-highlight"
              style={{
                left: (hovered ?? NOW_INDEX) * COL_WIDTH + 2,
                width: COL_WIDTH - 4,
                opacity: hovered !== null && hovered !== NOW_INDEX ? 1 : 0
              }}
              aria-hidden="true"
            />

            <div className="hf-cells">
              {points.map((point, index) => (
                <div
                  className={'hf-cell' + (index === NOW_INDEX ? ' is-now' : '')}
                  key={point.time}
                  onMouseEnter={() => setHoveredIndex(index)}
                >
                  <span className="hf-time">
                    {index === NOW_INDEX ? 'Now' : formatHour(point.time)}
                  </span>
                  <WeatherIcon
                    condition={getConditionInfo(point.weatherCode).condition}
                    isDay={point.isDay}
                    className="hf-icon"
                  />
                  <span className="hf-temp">
                    {Math.round(celsiusTo(unit, point.temperature))}
                    <span className="hf-deg">°</span>
                  </span>
                </div>
              ))}
            </div>

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
                <circle
                  className="hf-curve-dot"
                  cx={round2(nowPoint.x)}
                  cy={round2(nowPoint.y)}
                  r={3.2}
                />
              </svg>

              {hoveredPoint && hovered !== null && (
                <div
                  className={`hf-tooltip${tipAlign}`}
                  key={hovered}
                  style={{ left: tipLeft }}
                  aria-hidden="true"
                >
                  <span className="hf-tooltip-main">
                    {hovered === NOW_INDEX ? 'Now' : formatHour(hoveredPoint.time)}
                    {' · '}
                    {formatTemperature(hoveredPoint.temperature, unit)}
                    {' · '}
                    {Math.round(hoveredPoint.precipitationProbability)}%
                  </span>
                  <span className="hf-tooltip-cond">
                    {getConditionInfo(hoveredPoint.weatherCode).label}
                  </span>
                </div>
              )}
            </div>

            <div className="hf-pops">
              {points.map((point, index) => (
                <span
                  className={
                    'hf-pop' + (Math.round(point.precipitationProbability) >= 30 ? ' is-notable' : '')
                  }
                  key={point.time}
                  onMouseEnter={() => setHoveredIndex(index)}
                >
                  {Math.round(point.precipitationProbability)}%
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </BentoCard>
  )
}
