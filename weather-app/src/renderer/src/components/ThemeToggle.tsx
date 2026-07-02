import { useWeatherStore, type ThemePreference } from '../store/useWeatherStore'

function SunIcon(): JSX.Element {
  const rays = [0, 45, 90, 135, 180, 225, 270, 315]
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" aria-hidden="true">
      <circle cx={12} cy={12} r={4} />
      {rays.map((angle) => {
        const rad = (angle * Math.PI) / 180
        return (
          <line
            key={angle}
            x1={12 + Math.cos(rad) * 6.6}
            y1={12 + Math.sin(rad) * 6.6}
            x2={12 + Math.cos(rad) * 9.4}
            y2={12 + Math.sin(rad) * 9.4}
          />
        )
      })}
    </svg>
  )
}

function MoonIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 14.5 A8.5 8.5 0 1 1 9.5 4 A7 7 0 0 0 20 14.5 Z" />
    </svg>
  )
}

/** A classic 9x-era window: title bar with close box, hard corners. */
function RetroWindowIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="square" aria-hidden="true">
      <rect x={3.5} y={4.5} width={17} height={15} />
      <line x1={3.5} y1={9} x2={20.5} y2={9} />
      <rect x={16} y={6} width={2.4} height={1.6} fill="currentColor" stroke="none" />
    </svg>
  )
}

const OPTIONS: { value: ThemePreference; title: string; icon: JSX.Element | string }[] = [
  { value: 'light', title: 'Light theme', icon: <SunIcon /> },
  { value: 'dark', title: 'Dark theme', icon: <MoonIcon /> },
  { value: 'auto', title: 'Follow day/night at the selected location', icon: 'A' },
  { value: 'win95', title: 'Windows 95 retro theme', icon: <RetroWindowIcon /> }
]

export function ThemeToggle(): JSX.Element {
  const theme = useWeatherStore((s) => s.theme)
  const setTheme = useWeatherStore((s) => s.setTheme)

  return (
    <div className="control-pill" role="group" aria-label="Theme">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`seg${theme === option.value ? ' active' : ''}`}
          onClick={() => setTheme(option.value)}
          title={option.title}
          aria-label={option.title}
        >
          {option.icon}
        </button>
      ))}
    </div>
  )
}
