import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { REFRESH_INTERVAL_OPTIONS_MIN, useWeatherStore } from '../store/useWeatherStore'
import { useSegThumb } from '../hooks/useSegThumb'
import { ClockIcon, BellIcon, PowerIcon } from './icons'
import './SettingsPanel.css'

interface SettingsPanelProps {
  onClose: () => void
}

interface ToggleRowProps {
  label: string
  description: string
  checked: boolean
  onChange: (checked: boolean) => void
}

function ToggleRow({ label, description, checked, onChange }: ToggleRowProps): JSX.Element {
  return (
    <div className="settings-row">
      <div className="settings-row-text">
        <span className="settings-row-label">{label}</span>
        <span className="settings-row-desc">{description}</span>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className={`settings-toggle${checked ? ' is-on' : ''}`}
        onClick={() => onChange(!checked)}
      >
        <span className="settings-toggle-thumb" />
      </button>
    </div>
  )
}

/**
 * A settings overlay rather than a grid card — the bento grid is already
 * full at 9 tight-fitting cards, and a settings surface belongs to app
 * chrome, not the forecast instrument cluster.
 */
export function SettingsPanel({ onClose }: SettingsPanelProps): JSX.Element {
  const refreshIntervalMinutes = useWeatherStore((s) => s.refreshIntervalMinutes)
  const setRefreshIntervalMinutes = useWeatherStore((s) => s.setRefreshIntervalMinutes)
  const rainAlertsEnabled = useWeatherStore((s) => s.rainAlertsEnabled)
  const setRainAlertsEnabled = useWeatherStore((s) => s.setRainAlertsEnabled)
  const launchAtLoginEnabled = useWeatherStore((s) => s.launchAtLoginEnabled)
  const launchAtLoginSupported = useWeatherStore((s) => s.launchAtLoginSupported)
  const setLaunchAtLogin = useWeatherStore((s) => s.setLaunchAtLogin)
  const favoritesCount = useWeatherStore((s) => s.favorites.length)

  const pillRef = useRef<HTMLDivElement>(null)
  const { left, width, ready } = useSegThumb(pillRef, String(refreshIntervalMinutes))

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  // Requesting permission is a one-shot browser dialog — only fire it the
  // moment the user actually opts in, never on mount or silently in the
  // background. If the user (or Windows) has already blocked notifications,
  // the toggle simply can't turn on, matching what will actually happen.
  const handleRainToggle = (enabled: boolean): void => {
    if (enabled && 'Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission().then((permission) => {
        setRainAlertsEnabled(permission === 'granted')
      })
      return
    }
    setRainAlertsEnabled(enabled)
  }

  return createPortal(
    <div className="settings-overlay" onMouseDown={onClose}>
      <div
        className="settings-panel glass-panel"
        role="dialog"
        aria-modal="true"
        aria-label="Settings"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="settings-header">
          <span className="settings-title">Settings</span>
          <button type="button" className="icon-btn settings-close" aria-label="Close settings" onClick={onClose}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" aria-hidden="true">
              <line x1={6} y1={6} x2={18} y2={18} />
              <line x1={18} y1={6} x2={6} y2={18} />
            </svg>
          </button>
        </div>

        <div className="settings-section">
          <div className="card-title settings-section-title">
            <ClockIcon />
            Refresh interval
          </div>
          <div
            className={'control-pill settings-interval-pill' + (ready ? ' seg-thumb-ready' : '')}
            data-control="refresh-interval"
            role="group"
            aria-label="Background refresh interval"
            ref={pillRef}
          >
            <span className="seg-thumb" style={{ left: `${left}px`, width: `${width}px` }} aria-hidden="true" />
            {REFRESH_INTERVAL_OPTIONS_MIN.map((minutes) => (
              <button
                key={minutes}
                type="button"
                className={'seg' + (refreshIntervalMinutes === minutes ? ' active' : '')}
                onClick={() => setRefreshIntervalMinutes(minutes)}
              >
                {minutes}m
              </button>
            ))}
          </div>
        </div>

        <div className="settings-section">
          <div className="card-title settings-section-title">
            <BellIcon />
            Notifications
          </div>
          <ToggleRow
            label="Rain alerts"
            description="A desktop notification when rain looks likely soon"
            checked={rainAlertsEnabled}
            onChange={handleRainToggle}
          />
        </div>

        {launchAtLoginSupported && (
          <div className="settings-section">
            <div className="card-title settings-section-title">
              <PowerIcon />
              Windows
            </div>
            <ToggleRow
              label="Launch at startup"
              description="Open Cinematic Weather automatically when Windows starts"
              checked={launchAtLoginEnabled}
              onChange={setLaunchAtLogin}
            />
          </div>
        )}

        <div className="settings-footer">
          <span>Cinematic Weather · v1.0</span>
          <span>Weather data from Open-Meteo.com</span>
          {favoritesCount > 0 && (
            <span>
              {favoritesCount} saved location{favoritesCount === 1 ? '' : 's'}
            </span>
          )}
        </div>
      </div>
    </div>,
    document.body
  )
}
