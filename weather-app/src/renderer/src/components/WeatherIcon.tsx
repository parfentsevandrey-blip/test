import type { WeatherCondition } from '../utils/weatherCondition'

interface WeatherIconProps {
  condition: WeatherCondition
  isDay: boolean
  className?: string
}

const SVG_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.5,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const
}

function SunGlyph({ cx, cy, r }: { cx: number; cy: number; r: number }): JSX.Element {
  const rayLength = r * 0.7
  const rayGap = r * 0.35
  const angles = [0, 45, 90, 135, 180, 225, 270, 315]
  return (
    <>
      <circle cx={cx} cy={cy} r={r} />
      {angles.map((angle) => {
        const rad = (angle * Math.PI) / 180
        const x1 = cx + Math.cos(rad) * (r + rayGap)
        const y1 = cy + Math.sin(rad) * (r + rayGap)
        const x2 = cx + Math.cos(rad) * (r + rayGap + rayLength)
        const y2 = cy + Math.sin(rad) * (r + rayGap + rayLength)
        return <line key={angle} x1={x1} y1={y1} x2={x2} y2={y2} />
      })}
    </>
  )
}

function MoonGlyph({ cx, cy, r }: { cx: number; cy: number; r: number }): JSX.Element {
  return <path d={`M ${cx + r * 0.5} ${cy - r} a ${r} ${r} 0 1 0 0 ${r * 2} a ${r * 0.8} ${r * 0.8} 0 1 1 0 ${-r * 2} z`} />
}

function CloudGlyph({ cx, cy, scale = 1 }: { cx: number; cy: number; scale?: number }): JSX.Element {
  return (
    <path
      d={`M ${cx - 6 * scale} ${cy + 3 * scale}
          a ${3.2 * scale} ${3.2 * scale} 0 0 1 0 ${-6.4 * scale}
          a ${4 * scale} ${4 * scale} 0 0 1 7.6 ${-1.4 * scale}
          a ${3.4 * scale} ${3.4 * scale} 0 0 1 ${1.4 * scale} ${6.6 * scale}
          a ${3 * scale} ${3 * scale} 0 0 1 ${-1 * scale} ${1.2 * scale}
          z`}
    />
  )
}

export function WeatherIcon({ condition, isDay, className }: WeatherIconProps): JSX.Element {
  const props = { ...SVG_PROPS, className }

  switch (condition) {
    case 'clear':
      if (isDay) {
        return (
          <svg {...props}>
            <SunGlyph cx={12} cy={12} r={4.2} />
          </svg>
        )
      }
      return (
        <svg {...props}>
          <MoonGlyph cx={11} cy={12} r={5} />
          <path d="M18 6 L18.6 7.4 L20 8 L18.6 8.6 L18 10 L17.4 8.6 L16 8 L17.4 7.4 Z" fill="currentColor" stroke="none" />
          <circle cx={20.5} cy={12.5} r={0.5} fill="currentColor" stroke="none" />
        </svg>
      )

    case 'partly-cloudy':
      if (isDay) {
        return (
          <svg {...props}>
            <g transform="translate(3.5, -2.5)">
              <SunGlyph cx={12} cy={9} r={2.8} />
            </g>
            <CloudGlyph cx={11.5} cy={15} scale={1.15} />
          </svg>
        )
      }
      return (
        <svg {...props}>
          <g transform="translate(2.5, -2.5)">
            <MoonGlyph cx={10.5} cy={9} r={2.6} />
          </g>
          <CloudGlyph cx={11.5} cy={15} scale={1.15} />
        </svg>
      )

    case 'cloudy':
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={13} scale={1.5} />
        </svg>
      )

    case 'fog':
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={9.5} scale={1.1} />
          <line x1={4.5} y1={16} x2={19.5} y2={16} />
          <line x1={6} y1={19} x2={18} y2={19} />
          <line x1={4.5} y1={13} x2={13.5} y2={13} />
        </svg>
      )

    case 'drizzle':
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={9.5} scale={1.1} />
          <line x1={9} y1={16.5} x2={8.5} y2={18.5} />
          <line x1={12.5} y1={17} x2={12} y2={19} />
          <line x1={16} y1={16.5} x2={15.5} y2={18.5} />
        </svg>
      )

    case 'rain':
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={9} scale={1.15} />
          <line x1={8.5} y1={16} x2={7} y2={20.5} />
          <line x1={12.5} y1={16} x2={11} y2={20.5} />
          <line x1={16.5} y1={16} x2={15} y2={20.5} />
        </svg>
      )

    case 'snow':
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={9} scale={1.15} />
          <g strokeWidth={1.2}>
            <path d="M8 17 L8 20.5 M6.5 17.7 L9.5 19.8 M9.5 17.7 L6.5 19.8" />
            <path d="M16 17 L16 20.5 M14.5 17.7 L17.5 19.8 M17.5 17.7 L14.5 19.8" />
          </g>
        </svg>
      )

    case 'thunderstorm':
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={8.5} scale={1.15} />
          <path
            d="M12.5 14.5 L9.5 19.5 L11.8 19.5 L11 23 L14.5 17.5 L12.2 17.5 Z"
            fill="currentColor"
            stroke="currentColor"
            strokeWidth={1}
          />
        </svg>
      )

    default:
      return (
        <svg {...props}>
          <CloudGlyph cx={12} cy={13} scale={1.5} />
        </svg>
      )
  }
}
