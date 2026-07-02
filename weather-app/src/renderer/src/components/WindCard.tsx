import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WindIcon, CompassNeedleIcon } from './icons'
import { formatSpeed, speedUnitFor } from '../utils/units'

const DIRECTIONS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW']

function directionLabel(deg: number): string {
  return DIRECTIONS[Math.round(deg / 45) % 8]
}

export function WindCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const speedUnit = speedUnitFor(unit)
  const { windSpeed, windDirection, windGusts } = weather.current

  return (
    <BentoCard span="bento-1" floatDelay={0.2}>
      <div className="metric-card">
        <div className="metric-header">
          <WindIcon />
          <span className="metric-label">Wind</span>
        </div>
        <div className="metric-value">{formatSpeed(windSpeed, speedUnit)}</div>
        <div className="metric-sub">{directionLabel(windDirection)} - Gusts {formatSpeed(windGusts, speedUnit)}</div>
        <div className="metric-visual metric-compass">
          <CompassNeedleIcon rotation={windDirection} />
        </div>
      </div>
    </BentoCard>
  )
}
