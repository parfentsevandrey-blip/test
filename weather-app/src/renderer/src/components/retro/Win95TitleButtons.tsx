import { useState } from 'react'
import type { MouseEvent } from 'react'
import { Win95Dialog } from './Win95Dialog'
import { playClickTap, playErrorChord } from '../../utils/retroSounds'
import './retro.css'

/** Every attempt to close the weather is met with period-appropriate bureaucracy. */
const CLOSE_GAGS: Array<{ title: string; message: string }> = [
  {
    title: 'Cinematic Weather 95',
    message: 'The weather cannot be closed. It will continue outside regardless.'
  },
  {
    title: 'Error',
    message: 'WEATHER.EXE is a system process and cannot be terminated.'
  },
  {
    title: 'Access Denied',
    message: 'Closing the sky requires administrator privileges (and an umbrella).'
  },
  {
    title: 'Cinematic Weather 95',
    message: 'This card is busy performing 14,000 unnecessary disk operations. Try again in 1995.'
  }
]

function runCardAnimation(button: HTMLElement, className: string, durationMs: number): void {
  const card = button.closest<HTMLElement>('.bento-card')
  if (!card || card.classList.contains(className)) return
  card.classList.add(className)
  setTimeout(() => card.classList.remove(className), durationMs)
}

/**
 * The `_ □ ✕` cluster that turns each card's navy strip into a real 90s
 * title bar. Minimize/maximize play chunky stepped animations; close opens
 * a rotating gag message box, because the weather is not that easy to quit.
 */
export function Win95TitleButtons(): JSX.Element {
  const [gag, setGag] = useState<{ title: string; message: string } | null>(null)

  const handleMinimize = (event: MouseEvent<HTMLButtonElement>): void => {
    playClickTap()
    runCardAnimation(event.currentTarget, 'w95-minimizing', 950)
  }

  const handleMaximize = (event: MouseEvent<HTMLButtonElement>): void => {
    playClickTap()
    runCardAnimation(event.currentTarget, 'w95-maximizing', 500)
  }

  const handleClose = (): void => {
    playErrorChord()
    setGag(CLOSE_GAGS[Math.floor(Math.random() * CLOSE_GAGS.length)])
  }

  return (
    <>
      <div className="w95-titlebtns">
        <button type="button" className="w95-titlebtn" aria-label="Minimize card" onClick={handleMinimize}>
          <svg viewBox="0 0 8 7" aria-hidden="true">
            <rect x="0" y="5" width="6" height="2" fill="currentColor" />
          </svg>
        </button>
        <button type="button" className="w95-titlebtn" aria-label="Maximize card" onClick={handleMaximize}>
          <svg viewBox="0 0 8 7" aria-hidden="true">
            <rect x="0.5" y="0.5" width="7" height="6" fill="none" stroke="currentColor" />
            <rect x="0" y="0" width="8" height="2" fill="currentColor" />
          </svg>
        </button>
        <button type="button" className="w95-titlebtn" aria-label="Close card" onClick={handleClose}>
          <svg viewBox="0 0 8 7" aria-hidden="true">
            <path d="M0 0 L7 7 M7 0 L0 7" stroke="currentColor" strokeWidth="1.6" transform="translate(0.5,0)" />
          </svg>
        </button>
      </div>

      {gag && (
        <Win95Dialog title={gag.title} icon="error" onClose={() => setGag(null)}>
          {gag.message}
        </Win95Dialog>
      )}
    </>
  )
}
