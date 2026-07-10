import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import './LoadingOverlay.css'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'

/* Three Catmull-Rom-smoothed blob outlines sharing an identical command
 * structure (M + 8 C segments + Z). Only BLOB_PATHS[0] is actually rendered
 * (as a static resting shape) -- animating the raw "d" attribute between
 * these on a repeat:Infinity loop was real per-frame main-thread path
 * recompute + repaint cost for a decorative flourish, so it's no longer
 * tweened at runtime. The alternates are kept in case a future one-shot
 * transition wants them. */
const BLOB_PATHS = [
  'M100,28 C114.85,27.76 132.25,42.75 145.25,54.75 C158.25,66.75 178.47,85.39 178,100 C177.53,114.61 155.43,130.09 142.43,142.43 C129.43,154.76 114.85,173.29 100,174 C85.15,174.71 66.66,159 53.33,146.67 C40,134.34 19.53,115.08 20,100 C20.47,84.92 42.83,68.16 56.16,56.16 C69.49,44.16 85.15,28.24 100,28 Z',
  'M100,40 C118.62,40.24 145.49,34.85 155.15,44.85 C164.82,54.85 157.53,81.14 158,100 C158.47,118.86 167.65,147.65 157.98,157.98 C148.32,168.32 118.62,162.71 100,162 C81.38,161.29 56.26,164.07 46.26,153.74 C36.26,143.41 40.47,118.38 40,100 C39.53,81.62 33.43,53.43 43.43,43.43 C53.43,33.43 81.38,39.76 100,40 Z',
  'M100,20 C115.56,21.89 129.35,45.65 141.01,58.99 C152.68,72.32 169.06,85.39 170,100 C170.94,114.61 158.34,132.67 146.67,146.67 C135,160.67 114.85,184.71 100,184 C85.15,183.29 68.91,156.43 57.57,142.43 C46.24,128.43 33.65,115.79 32,100 C30.35,84.21 36.34,61.01 47.67,47.67 C59.01,34.34 84.44,18.11 100,20 Z'
] as const

const EASE_OUT = [0.16, 1, 0.3, 1] as const

export function LoadingOverlay(): JSX.Element | null {
  const status = useWeatherStore((s) => s.status)
  const error = useWeatherStore((s) => s.error)
  const refresh = useWeatherStore((s) => s.refresh)
  const theme = useWeatherStore((s) => s.theme)
  const weather = useWeatherStore((s) => s.weather)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()

  /* Windows 95 keeps the exact loader it has always had: plain markup, no
     Framer Motion, no mesh/blob/glow additions -- completely untouched. */
  if (isRetro) {
    if (status === 'ready') {
      return null
    }

    if (status === 'error') {
      return (
        <div className="loading-overlay">
          <div className="error-banner glass-panel" role="alert">
            <div className="title">Couldn&apos;t load weather</div>
            <div className="message">{error ?? 'Something went wrong. Please try again.'}</div>
            <button type="button" aria-label="Try again" onClick={() => refresh()}>
              Try again
            </button>
          </div>
        </div>
      )
    }

    return (
      <div className="loading-overlay">
        <div className="loading-sun" aria-hidden="true">
          <span className="loading-sun-ring ring-1" />
          <span className="loading-sun-ring ring-2" />
          <span className="loading-sun-ring ring-3" />
          <span className="loading-sun-core" />
        </div>
        <div className="loading-text" role="status" aria-live="polite">
          {status === 'locating' ? 'Finding your location...' : 'Loading weather...'}
        </div>
      </div>
    )
  }

  return (
    <AnimatePresence mode="wait">
      {status === 'error' ? (
        <motion.div
          key="error"
          className="loading-overlay loading-overlay-modern"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0, transition: { duration: 0.25 } }}
          transition={{ duration: 0.4, ease: EASE_OUT }}
        >
          <motion.div
            className="loading-mesh loading-mesh-error"
            aria-hidden="true"
            animate={prefersReducedMotion ? undefined : { opacity: [0.55, 0.85, 0.55] }}
            transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
          />
          <motion.div
            className="error-banner glass-panel error-banner-modern"
            role="alert"
            initial={prefersReducedMotion ? false : { opacity: 0, y: 26, scale: 0.92 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 0.94, transition: { duration: 0.2 } }}
            transition={{ type: 'spring', stiffness: 260, damping: 20, mass: 0.7 }}
          >
            <motion.div
              className="error-glyph"
              aria-hidden="true"
              animate={prefersReducedMotion ? undefined : { rotate: [0, -10, 9, -6, 4, 0] }}
              transition={{ duration: 0.7, delay: 0.2, ease: EASE_OUT }}
            >
              !
            </motion.div>
            <div className="title">Couldn&apos;t load weather</div>
            <div className="message">{error ?? 'Something went wrong. Please try again.'}</div>
            <motion.button
              type="button"
              aria-label="Try again"
              onClick={() => refresh()}
              whileHover={prefersReducedMotion ? undefined : { scale: 1.02, transition: { type: 'spring', stiffness: 300, damping: 22 } }}
              whileTap={prefersReducedMotion ? undefined : { scale: 0.97 }}
            >
              Try again
            </motion.button>
          </motion.div>
        </motion.div>
      ) : status !== 'ready' ? (
        <motion.div
          key="loading"
          className="loading-overlay loading-overlay-modern"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0, transition: { duration: 0.3 } }}
          transition={{ duration: 0.45, ease: EASE_OUT }}
        >
          <motion.div
            className="loading-mesh"
            aria-hidden="true"
            animate={
              prefersReducedMotion
                ? undefined
                : { opacity: [0.5, 0.9, 0.5], scale: [1, 1.12, 1], rotate: [0, 6, 0] }
            }
            transition={{ duration: 9, repeat: Infinity, ease: 'easeInOut' }}
          />

          <div className="loading-stage" aria-hidden="true">
            <motion.svg
              className="loading-orbit-svg"
              viewBox="0 0 200 200"
              initial={false}
              animate={prefersReducedMotion ? undefined : { rotate: 360 }}
              transition={{ duration: 6.5, repeat: Infinity, ease: 'linear' }}
            >
              <defs>
                <linearGradient id="loading-orbit-grad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="var(--accent)" />
                  <stop offset="100%" stopColor="var(--accent-strong)" />
                </linearGradient>
              </defs>
              {/* Draws in once, then holds steady -- the spinning look comes
                  from the parent svg's cheap (transform-only) 360deg rotate
                  loop above, not from re-animating pathLength/opacity every
                  frame, which was measurable main-thread paint cost for a
                  purely decorative ring. */}
              <motion.circle
                cx={100}
                cy={100}
                r={86}
                className="loading-orbit-ring"
                stroke="url(#loading-orbit-grad)"
                initial={prefersReducedMotion ? false : { pathLength: 0, opacity: 0 }}
                animate={{ pathLength: 0.72, opacity: 0.82 }}
                transition={{ duration: 0.9, ease: EASE_OUT }}
              />
            </motion.svg>

            <motion.svg className="loading-blob-svg" viewBox="0 0 200 200">
              <defs>
                <linearGradient id="loading-blob-grad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="var(--accent-strong)" />
                  <stop offset="100%" stopColor="var(--accent)" />
                </linearGradient>
              </defs>
              {/* Static shape (was a continuous d-attribute morph across all
                  three blobs) -- ambient life still comes from the rotating
                  ring, orbiting particles and pulsing core around it. */}
              <motion.path
                className="loading-blob-path"
                fill="url(#loading-blob-grad)"
                d={BLOB_PATHS[0]}
                initial={prefersReducedMotion ? false : { scale: 0.6, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ duration: 0.6, ease: EASE_OUT }}
              />
            </motion.svg>

            <motion.span
              className="loading-orb-core"
              animate={
                prefersReducedMotion ? undefined : { scale: [1, 1.16, 1], opacity: [0.85, 1, 0.85] }
              }
              transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
            />

            {!prefersReducedMotion &&
              [0, 1, 2].map((i) => (
                <motion.span
                  key={i}
                  className={`loading-particle-track track-${i}`}
                  animate={{ rotate: 360 }}
                  transition={{
                    duration: 4.5 + i * 1.1,
                    repeat: Infinity,
                    ease: 'linear',
                    delay: -i * 0.6
                  }}
                >
                  <span className="loading-particle" />
                </motion.span>
              ))}
          </div>

          <AnimatePresence mode="wait">
            <motion.div
              key={status === 'locating' ? 'locating' : 'loading'}
              className="loading-text loading-text-modern"
              role="status"
              aria-live="polite"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.35, ease: EASE_OUT }}
            >
              {status === 'locating' ? 'Finding your location...' : 'Loading weather...'}
            </motion.div>
          </AnimatePresence>
        </motion.div>
      ) : null}
    </AnimatePresence>
  )
}
