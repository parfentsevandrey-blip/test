import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { DropletIcon } from './icons'
import { formatTemperature } from '../utils/units'
import { clamp01 } from '../utils/math'
import './MetricCards.css'

const RING_RADIUS = 17.5
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS

function humidityDescriptor(pct: number): string {
  if (pct < 30) return 'Dry'
  if (pct < 60) return 'Comfortable'
  if (pct < 80) return 'Humid'
  return 'Very humid'
}

/** Magnus-formula dew point (°C). Null when RH is 0 (log undefined). */
function dewPointCelsius(tempC: number, rhPct: number): number | null {
  if (rhPct <= 0) return null
  const b = 17.62
  const c = 243.12
  const g = Math.log(rhPct / 100) + (b * tempC) / (c + tempC)
  return (c * g) / (b - g)
}

export function HumidityCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const humidity = weather.current.humidity
  const filled = clamp01(humidity / 100) * RING_CIRCUMFERENCE
  const dewPoint = dewPointCelsius(weather.current.temperature, humidity)

  /* 680px-window vertical budget (content ≈ 162px): header 16 + value 40 +
     sub 20 + dew-point footline 18 = 94 fixed → ~68px left for the flexed
     ring slot; the ring scales down from its 92px max without clipping. */
  return (
    <BentoCard span="bento-1" floatDelay={0.6}>
      <div className="metric-card">
        <div className="metric-header">
          <DropletIcon />
          <span className="metric-label">Humidity</span>
        </div>
        <div className="metric-value">
          <span className="mx-value">{Math.round(humidity)}</span>
          <span className="mx-unit">%</span>
        </div>
        <div className="metric-sub">{humidityDescriptor(humidity)}</div>
        <div className="metric-visual humidity-ring" aria-hidden="true">
          <svg viewBox="0 0 44 44">
            <defs>
              <linearGradient id="humidity-ring-grad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" style={{ stopColor: 'var(--info)', stopOpacity: 0.4 }} />
                <stop offset="100%" style={{ stopColor: 'var(--info)', stopOpacity: 1 }} />
              </linearGradient>
            </defs>
            <circle className="humidity-ring-track" cx={22} cy={22} r={RING_RADIUS} strokeWidth={4} />
            <circle
              className="humidity-ring-fill"
              cx={22}
              cy={22}
              r={RING_RADIUS}
              strokeWidth={4}
              stroke="url(#humidity-ring-grad)"
              strokeDasharray={`${filled} ${RING_CIRCUMFERENCE}`}
              transform="rotate(-90 22 22)"
            />
            {/* The big .metric-value already shows the %, so the ring holds a droplet glyph instead. */}
            <g className="humidity-ring-droplet" transform="translate(15.4 15) scale(0.55)">
              <path
                d="M12 3 C12 3 6 10.5 6 15 a6 6 0 0 0 12 0 C18 10.5 12 3 12 3 Z"
                fill="currentColor"
                fillOpacity={0.18}
                stroke="currentColor"
                strokeWidth={2.4}
              />
            </g>
          </svg>
        </div>
        {dewPoint !== null && (
          <div className="mx-footline">
            Dew point <b>{formatTemperature(dewPoint, unit)}</b>
          </div>
        )}
      </div>
    </BentoCard>
  )
}
