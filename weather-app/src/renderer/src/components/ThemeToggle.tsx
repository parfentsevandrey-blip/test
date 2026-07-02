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

const OPTIONS: { value: ThemePreference; label: string; title: string }[] = [
  { value: 'light', label: 'Light', title: 'Light theme' },
  { value: 'dark', label: 'Dark', title: 'Dark theme' },
  { value: 'auto', label: 'Auto', title: 'Follow day/night at the selected location' }
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
          {option.value === 'light' ? <SunIcon /> : option.value === 'dark' ? <MoonIcon /> : 'A'}
        </button>
      ))}
    </div>
  )
}
