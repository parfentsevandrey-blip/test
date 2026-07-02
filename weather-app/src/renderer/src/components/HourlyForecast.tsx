import { useId } from 'react'
import type { CSSProperties } from 'react'
import './HourlyForecast.css'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature, formatHour } from '../utils/units'

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

  return (
    <BentoCard span="bento-hourly">
      <div className="hourly-forecast">
        <div className="hourly-title">Next 24 Hours</div>
        <div
          className="hf-scroll"
          role="region"
          aria-label="Hourly forecast for the next 24 hours, scroll horizontally for later hours"
          tabIndex={0}
        >
          <div className="hf-strip" style={stripStyle}>
            <div
              className="hf-now-highlight"
              style={{ left: NOW_INDEX * COL_WIDTH + 2, width: COL_WIDTH - 4 }}
              aria-hidden="true"
            />

            <div className="hf-cells">
              {points.map((point, index) => (
                <div
                  className={'hf-cell' + (index === NOW_INDEX ? ' is-now' : '')}
                  key={point.time}
                >
                  <span className="hf-time">
                    {index === NOW_INDEX ? 'Now' : formatHour(point.time)}
                  </span>
                  <WeatherIcon
                    condition={getConditionInfo(point.weatherCode).condition}
                    isDay={point.isDay}
                    className="hf-icon"
                  />
                  <span className="hf-temp">{formatTemperature(point.temperature, unit)}</span>
                </div>
              ))}
            </div>

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
              <path d={areaPath} fill={`url(#${gradientId})`} stroke="none" />
              <path className="hf-curve-line" d={linePath} />
              <circle
                className="hf-curve-dot"
                cx={round2(nowPoint.x)}
                cy={round2(nowPoint.y)}
                r={3.2}
              />
            </svg>

            <div className="hf-pops">
              {points.map((point) => (
                <span
                  className={
                    'hf-pop' + (Math.round(point.precipitationProbability) >= 30 ? ' is-notable' : '')
                  }
                  key={point.time}
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
