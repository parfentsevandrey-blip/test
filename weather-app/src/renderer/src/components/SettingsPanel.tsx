import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion, useReducedMotion, type Variants } from 'framer-motion'
import { REFRESH_INTERVAL_OPTIONS_MIN, resolveTheme, useWeatherStore } from '../store/useWeatherStore'
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
  /** win95 renders a plain, instantly-toggled switch -- no spring, no scale. */
  isRetro: boolean
}

function ToggleRow({ label, description, checked, onChange, isRetro }: ToggleRowProps): JSX.Element {
  const prefersReducedMotion = useReducedMotion()

  const text = (
    <div className="settings-row-text">
      <span className="settings-row-label">{label}</span>
      <span className="settings-row-desc">{description}</span>
    </div>
  )

  if (isRetro) {
    return (
      <div className="settings-row">
        {text}
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

  return (
    <div className="settings-row">
      {text}
      <motion.button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className={`settings-toggle${checked ? ' is-on' : ''}`}
        onClick={() => onChange(!checked)}
        whileHover={prefersReducedMotion ? undefined : { scale: 1.06 }}
        whileTap={prefersReducedMotion ? undefined : { scale: 0.88 }}
      >
        <motion.span
          className="settings-toggle-thumb"
          animate={{ x: checked ? 18 : 0 }}
          transition={prefersReducedMotion ? { duration: 0 } : { type: 'spring', stiffness: 520, damping: 30 }}
        />
      </motion.button>
    </div>
  )
}

/* Backdrop is a plain opacity fade -- the blur-in drama belongs to the panel
   itself, not a scrim the eye barely lingers on. */
const overlayVariants: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.22 } },
  exit: { opacity: 0, transition: { duration: 0.18 } }
}

const reducedOverlayVariants: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.12 } },
  exit: { opacity: 0, transition: { duration: 0.1 } }
}

/* Panel springs in with a de-blur, then orchestrates its own children's
   stagger via `when: 'beforeChildren'` -- one physics settle, one cascade. */
const panelVariants: Variants = {
  hidden: { opacity: 0, scale: 0.9, y: 26, filter: 'blur(16px)' },
  visible: {
    opacity: 1,
    scale: 1,
    y: 0,
    filter: 'blur(0px)',
    transition: {
      type: 'spring',
      stiffness: 260,
      damping: 24,
      mass: 0.9,
      when: 'beforeChildren',
      staggerChildren: 0.07,
      delayChildren: 0.06
    }
  },
  exit: {
    opacity: 0,
    scale: 0.94,
    y: 14,
    filter: 'blur(8px)',
    transition: { duration: 0.2, ease: [0.4, 0, 1, 1] }
  }
}

const reducedPanelVariants: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.16, staggerChildren: 0.03 } },
  exit: { opacity: 0, transition: { duration: 0.12 } }
}

const sectionVariants: Variants = {
  hidden: { opacity: 0, y: 14 },
  visible: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 320, damping: 26 } }
}

const reducedSectionVariants: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.14 } }
}

/**
 * A settings overlay rather than a grid card — the bento grid is already
 * full at 9 tight-fitting cards, and a settings surface belongs to app
 * chrome, not the forecast instrument cluster.
 *
 * Every theme except win95 gets a full Framer Motion treatment: backdrop
 * fade, spring + de-blur panel entrance, staggered section cascade, and
 * springy toggle/segment interactions. win95 renders the original plain,
 * instant, unanimated dialog untouched.
 *
 * App.tsx mounts/unmounts this via `{settingsOpen && <SettingsPanel/>}` and
 * isn't ours to edit, so exit animation is self-managed: closing sets local
 * `isClosing` state, AnimatePresence plays the exit variants, and only once
 * that finishes does `onExitComplete` call the real `onClose` to actually
 * unmount from the parent tree.
 */
export function SettingsPanel({ onClose }: SettingsPanelProps): JSX.Element {
  const theme = useWeatherStore((s) => s.theme)
  const weather = useWeatherStore((s) => s.weather)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()

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

  const [isClosing, setIsClosing] = useState(false)
  const requestClose = (): void => {
    if (isRetro) {
      onClose()
    } else {
      setIsClosing(true)
    }
  }

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') requestClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isRetro])

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

  const closeIcon = (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" aria-hidden="true">
      <line x1={6} y1={6} x2={18} y2={18} />
      <line x1={18} y1={6} x2={6} y2={18} />
    </svg>
  )

  if (isRetro) {
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
              {closeIcon}
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
              isRetro
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
                isRetro
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

  return createPortal(
    <AnimatePresence onExitComplete={onClose}>
      {!isClosing && (
        <motion.div
          key="settings-overlay"
          className="settings-overlay"
          onMouseDown={requestClose}
          variants={prefersReducedMotion ? reducedOverlayVariants : overlayVariants}
          initial="hidden"
          animate="visible"
          exit="exit"
        >
          <motion.div
            className="settings-panel glass-panel"
            role="dialog"
            aria-modal="true"
            aria-label="Settings"
            onMouseDown={(event) => event.stopPropagation()}
            variants={prefersReducedMotion ? reducedPanelVariants : panelVariants}
          >
            <motion.div
              className="settings-header"
              variants={prefersReducedMotion ? reducedSectionVariants : sectionVariants}
            >
              <span className="settings-title">Settings</span>
              <motion.button
                type="button"
                className="icon-btn settings-close"
                aria-label="Close settings"
                onClick={requestClose}
                whileHover={prefersReducedMotion ? undefined : { scale: 1.1, rotate: 90 }}
                whileTap={prefersReducedMotion ? undefined : { scale: 0.85 }}
              >
                {closeIcon}
              </motion.button>
            </motion.div>

            <motion.div
              className="settings-section"
              variants={prefersReducedMotion ? reducedSectionVariants : sectionVariants}
            >
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
                <motion.span
                  className="seg-thumb"
                  animate={{ left, width }}
                  transition={
                    ready && !prefersReducedMotion ? { type: 'spring', stiffness: 420, damping: 34 } : { duration: 0 }
                  }
                  aria-hidden="true"
                />
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
            </motion.div>

            <motion.div
              className="settings-section"
              variants={prefersReducedMotion ? reducedSectionVariants : sectionVariants}
            >
              <div className="card-title settings-section-title">
                <BellIcon />
                Notifications
              </div>
              <ToggleRow
                label="Rain alerts"
                description="A desktop notification when rain looks likely soon"
                checked={rainAlertsEnabled}
                onChange={handleRainToggle}
                isRetro={false}
              />
            </motion.div>

            {launchAtLoginSupported && (
              <motion.div
                className="settings-section"
                variants={prefersReducedMotion ? reducedSectionVariants : sectionVariants}
              >
                <div className="card-title settings-section-title">
                  <PowerIcon />
                  Windows
                </div>
                <ToggleRow
                  label="Launch at startup"
                  description="Open Cinematic Weather automatically when Windows starts"
                  checked={launchAtLoginEnabled}
                  onChange={setLaunchAtLogin}
                  isRetro={false}
                />
              </motion.div>
            )}

            <motion.div
              className="settings-footer"
              variants={prefersReducedMotion ? reducedSectionVariants : sectionVariants}
            >
              <span>Cinematic Weather · v1.0</span>
              <span>Weather data from Open-Meteo.com</span>
              {favoritesCount > 0 && (
                <span>
                  {favoritesCount} saved location{favoritesCount === 1 ? '' : 's'}
                </span>
              )}
            </motion.div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  )
}
