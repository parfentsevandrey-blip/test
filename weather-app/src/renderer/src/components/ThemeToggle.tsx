import { useRef } from 'react'
import { motion } from 'framer-motion'
import { useWeatherStore, type ThemePreference } from '../store/useWeatherStore'
import { useSegThumb } from '../hooks/useSegThumb'

function SunIcon(): JSX.Element {
  const rays = [0, 45, 90, 135, 180, 225, 270, 315]
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
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
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 14.5 A8.5 8.5 0 1 1 9.5 4 A7 7 0 0 0 20 14.5 Z" />
    </svg>
  )
}

/** A lone cypress on a hill — the signature silhouette of the Tuscan countryside. */
function CypressIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3.2 C13.9 6 14.4 10 14.1 14 C14 16 13.3 17.4 12 17.4 C10.7 17.4 10 16 9.9 14 C9.6 10 10.1 6 12 3.2 Z" fill="currentColor" stroke="none" />
      <line x1={12} y1={17.4} x2={12} y2={20.2} />
      <path d="M4 20.6 C7.5 19.6 16.5 19.6 20 20.6" />
    </svg>
  )
}

/** A classic 9x-era window: title bar with close box, hard corners. */
function RetroWindowIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="square" aria-hidden="true">
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
  { value: 'tuscany', title: 'Tuscany theme', icon: <CypressIcon /> },
  { value: 'win95', title: 'Windows 95 retro theme', icon: <RetroWindowIcon /> }
]

export function ThemeToggle(): JSX.Element {
  const theme = useWeatherStore((s) => s.theme)
  const setTheme = useWeatherStore((s) => s.setTheme)
  const pillRef = useRef<HTMLDivElement>(null)
  const { left, width, ready } = useSegThumb(pillRef, theme)

  return (
    <div
      className={'control-pill' + (ready ? ' seg-thumb-ready' : '')}
      data-control="theme"
      role="group"
      aria-label="Theme"
      ref={pillRef}
    >
      <motion.span
        className="seg-thumb"
        animate={{ left, width }}
        transition={ready ? { type: 'spring', stiffness: 420, damping: 34 } : { duration: 0 }}
        aria-hidden="true"
      />
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
