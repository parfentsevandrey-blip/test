export function DropletIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
      <path d="M12 3 C12 3 6 10.5 6 15 a6 6 0 0 0 12 0 C18 10.5 12 3 12 3 Z" />
    </svg>
  )
}

export function WindIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <path d="M3 8 H14 a2.5 2.5 0 1 0 -2.5 -2.5" />
      <path d="M3 12.5 H18 a2.5 2.5 0 1 1 -2.5 2.5" />
      <path d="M3 17 H11 a2 2 0 1 1 -2 2" />
    </svg>
  )
}

export function SunBurstIcon(): JSX.Element {
  const rays = [0, 45, 90, 135, 180, 225, 270, 315]
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <circle cx={12} cy={12} r={3.6} />
      {rays.map((angle) => {
        const rad = (angle * Math.PI) / 180
        const x1 = 12 + Math.cos(rad) * 6.2
        const y1 = 12 + Math.sin(rad) * 6.2
        const x2 = 12 + Math.cos(rad) * 9.4
        const y2 = 12 + Math.sin(rad) * 9.4
        return <line key={angle} x1={x1} y1={y1} x2={x2} y2={y2} />
      })}
    </svg>
  )
}

export function SunriseIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <path d="M6 14 a6 6 0 0 1 12 0" />
      <line x1={12} y1={2.5} x2={12} y2={6} />
      <line x1={5.6} y1={7.6} x2={7.4} y2={9.2} />
      <line x1={18.4} y1={7.6} x2={16.6} y2={9.2} />
      <line x1={3} y1={14} x2={21} y2={14} />
      <path d="M9 11 L12 8 L15 11" />
    </svg>
  )
}

export function SunsetIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <path d="M6 12 a6 6 0 0 1 12 0" />
      <line x1={2.5} y1={12} x2={4.5} y2={12} />
      <line x1={19.5} y1={12} x2={21.5} y2={12} />
      <line x1={12} y1={2.5} x2={12} y2={4.5} />
      <line x1={5.6} y1={5.6} x2={7} y2={7} />
      <line x1={18.4} y1={5.6} x2={17} y2={7} />
      <line x1={3} y1={17} x2={21} y2={17} />
      <path d="M9 20.5 L12 17.5 L15 20.5" />
    </svg>
  )
}

export function EyeIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12 C5 6 9 3.5 12 3.5 S19 6 22 12 C19 18 15 20.5 12 20.5 S5 18 2 12 Z" />
      <circle cx={12} cy={12} r={3.2} />
    </svg>
  )
}

export function RainIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <path d="M7 15 a4.5 4.5 0 0 1 .5 -8.97 A6 6 0 0 1 19 8.5 A4 4 0 0 1 18 16 H7 Z" />
      <line x1={9} y1={18.5} x2={8} y2={21} />
      <line x1={13} y1={18.5} x2={12} y2={21} />
      <line x1={17} y1={18.5} x2={16} y2={21} />
    </svg>
  )
}

export function CompassNeedleIcon({ rotation }: { rotation: number }): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx={12} cy={12} r={10} stroke="currentColor" strokeOpacity={0.25} strokeWidth={1.3} />
      <g transform={`rotate(${rotation} 12 12)`}>
        <path d="M12 4 L14.4 13 L12 11.2 L9.6 13 Z" fill="var(--accent)" />
        <line x1={12} y1={11.2} x2={12} y2={19} stroke="currentColor" strokeOpacity={0.4} strokeWidth={1.3} />
      </g>
    </svg>
  )
}
