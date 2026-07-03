import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { playErrorChord } from '../../utils/retroSounds'
import './retro.css'

interface Win95DialogProps {
  title: string
  icon?: 'error' | 'info'
  onClose: () => void
  children: ReactNode
}

function ErrorIcon(): JSX.Element {
  return (
    <svg className="w95-dialog-icon" viewBox="0 0 32 32" aria-hidden="true">
      <circle cx="16" cy="16" r="15" fill="#c00000" stroke="#400000" strokeWidth="1" />
      <path d="M10 10 L22 22 M22 10 L10 22" stroke="#ffffff" strokeWidth="3.4" strokeLinecap="round" />
    </svg>
  )
}

function InfoIcon(): JSX.Element {
  return (
    <svg className="w95-dialog-icon" viewBox="0 0 32 32" aria-hidden="true">
      <circle cx="16" cy="16" r="15" fill="#ffffff" stroke="#000080" strokeWidth="1.6" />
      <rect x="14" y="13" width="4" height="12" fill="#000080" />
      <rect x="14" y="7" width="4" height="4" fill="#000080" />
    </svg>
  )
}

/**
 * A period-correct modal message box: navy title bar, silver bevels, one
 * bold [OK] button. Clicking outside doesn't dismiss it — it flashes the
 * title bar and plays the scolding chord, exactly like 1995 taught us.
 */
export function Win95Dialog({ title, icon = 'error', onClose, children }: Win95DialogProps): JSX.Element {
  const [flashing, setFlashing] = useState(false)
  const flashTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape' || event.key === 'Enter') {
        event.preventDefault()
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      if (flashTimer.current) clearTimeout(flashTimer.current)
    }
  }, [onClose])

  const handleOutsideClick = (): void => {
    playErrorChord()
    setFlashing(false)
    // Restart the flash animation even when clicks come in rapid succession.
    requestAnimationFrame(() => setFlashing(true))
    if (flashTimer.current) clearTimeout(flashTimer.current)
    flashTimer.current = setTimeout(() => setFlashing(false), 1000)
  }

  return createPortal(
    <div className="w95-overlay" onMouseDown={handleOutsideClick}>
      <div
        className="w95-dialog"
        role="alertdialog"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className={`w95-dialog-title${flashing ? ' is-flashing' : ''}`}>
          <span className="w95-dialog-title-text">{title}</span>
          <button type="button" className="w95-titlebtn" aria-label="Close" onClick={onClose}>
            <svg viewBox="0 0 8 7" aria-hidden="true">
              <path d="M0 0 L7 7 M7 0 L0 7" stroke="currentColor" strokeWidth="1.6" transform="translate(0.5,0)" />
            </svg>
          </button>
        </div>
        <div className="w95-dialog-body">
          {icon === 'error' ? <ErrorIcon /> : <InfoIcon />}
          <div className="w95-dialog-message">{children}</div>
        </div>
        <div className="w95-dialog-buttons">
          <button type="button" className="w95-push-btn" autoFocus onClick={onClose}>
            OK
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
