import { useRef } from 'react'
import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WindIcon } from './icons'
import { formatSpeed, msTo, speedUnitFor } from '../utils/units'
import './MetricCards.css'

const DIRECTIONS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW']

function directionLabel(deg: number): string {
  return DIRECTIONS[Math.round(deg / 45) % 8]
}

/** Standard Beaufort scale: upper bounds in m/s (exclusive), force 0-12. */
const BEAUFORT_BANDS: ReadonlyArray<{ readonly max: number; readonly label: string }> = [
  { max: 0.5, label: 'Calm' },
  { max: 1.6, label: 'Light air' },
  { max: 3.4, label: 'Light breeze' },
  { max: 5.5, label: 'Gentle breeze' },
  { max: 8.0, label: 'Moderate breeze' },
  { max: 10.8, label: 'Fresh breeze' },
  { max: 13.9, label: 'Strong breeze' },
  { max: 17.2, label: 'Near gale' },
  { max: 20.8, label: 'Gale' },
  { max: 24.5, label: 'Strong gale' },
  { max: 28.5, label: 'Storm' },
  { max: 32.7, label: 'Violent storm' },
  { max: Infinity, label: 'Hurricane force' }
]

function beaufort(metersPerSecond: number): { force: number; label: string } {
  const index = BEAUFORT_BANDS.findIndex((band) => metersPerSecond < band.max)
  const force = index === -1 ? BEAUFORT_BANDS.length - 1 : index
  return { force, label: BEAUFORT_BANDS[force].label }
}

/** Static compass face: ring + tiny cardinal letters. The needle is a separate layer. */
function CompassRing(): JSX.Element {
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true">
      <circle cx={24} cy={24} r={16.5} fill="none" stroke="currentColor" strokeOpacity={0.3} strokeWidth={1.2} />
      <g className="wind-compass-cardinals" textAnchor="middle" dominantBaseline="central">
        <text x={24} y={4.4}>N</text>
        <text x={43.6} y={24}>E</text>
        <text x={24} y={43.6}>S</text>
        <text x={4.4} y={24}>W</text>
      </g>
    </svg>
  )
}

/** Needle drawn pointing at N; the wrapper element applies the rotation. */
function CompassNeedle(): JSX.Element {
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true">
      <path d="M24 8.5 L28 25 L24 21.8 L20 25 Z" fill="var(--accent)" />
      <line x1={24} y1={21.8} x2={24} y2={36.5} stroke="currentColor" strokeOpacity={0.45} strokeWidth={1.4} strokeLinecap="round" />
    </svg>
  )
}

interface NeedleSpin {
  lastDirection: number
  rotation: number
}

export function WindCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  // Accumulates rotation across updates so the CSS transition always takes
  // the shortest arc (e.g. 350° -> 10° turns +20°, never -340°).
  const spin = useRef<NeedleSpin | null>(null)

  if (!weather) return null

  const speedUnit = speedUnitFor(unit)
  const { windSpeed, windDirection, windGusts } = weather.current
  const { force, label: beaufortLabel } = beaufort(windSpeed)

  if (spin.current === null) {
    spin.current = { lastDirection: windDirection, rotation: windDirection }
  } else if (spin.current.lastDirection !== windDirection) {
    const delta = ((((windDirection - spin.current.lastDirection) % 360) + 540) % 360) - 180
    spin.current = { lastDirection: windDirection, rotation: spin.current.rotation + delta }
  }

  /* 680px-window vertical budget (content ≈ 162px): header 16 + value 40
     (10.9 top margin + 29 line) + sub 20 + footline 18 = 94 fixed, leaving
     ~68px for the flexed compass slot — comfortably above its useful minimum. */
  return (
    <BentoCard span="bento-1" floatDelay={0.2}>
      <div className="metric-card">
        <div className="metric-header">
          <WindIcon />
          <span className="metric-label">Wind</span>
        </div>
        <div className="metric-value">
          <span className="mx-value">{Math.round(msTo(speedUnit, windSpeed))}</span>
          <span className="mx-unit">{speedUnit === 'mph' ? 'mph' : 'km/h'}</span>
        </div>
        <div className="metric-sub">{directionLabel(windDirection)} · Gusts {formatSpeed(windGusts, speedUnit)}</div>
        <div className="metric-visual wind-compass" aria-hidden="true">
          <CompassRing />
          <div className="wind-compass-needle" style={{ transform: `rotate(${spin.current.rotation}deg)` }}>
            {/* Inner layer so the hover wiggle keyframes compose with the inline rotation. */}
            <div className="wind-needle-wiggle">
              <CompassNeedle />
            </div>
          </div>
        </div>
        <div className="mx-footline">
          Force {force} · <b>{beaufortLabel}</b>
        </div>
      </div>
    </BentoCard>
  )
}
