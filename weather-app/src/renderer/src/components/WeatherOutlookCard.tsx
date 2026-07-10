import { useMemo } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { OutlookIcon } from './icons'
import { buildInsights, type InsightKind } from '../utils/weatherInsights'
import './MetricCards.css'
import './WeatherOutlookCard.css'

const EASE_OUT = [0.16, 1, 0.3, 1] as const

/** A small per-kind accent dot so two stacked insights read as distinct
 *  signals rather than a flat list -- reuses existing theme tokens, no new colors. */
function insightColor(kind: InsightKind): string {
  switch (kind) {
    case 'precip':
    case 'temp-drop':
      return 'var(--info)'
    case 'temp-rise':
      return 'var(--accent-strong)'
    case 'wind':
      return 'var(--text-secondary)'
    case 'uv':
      return 'var(--uv-high)'
    case 'clear':
      return 'var(--uv-low)'
    case 'pressure':
    case 'steady':
    default:
      return 'var(--text-tertiary)'
  }
}

/**
 * A compact "what's notable about the next several hours" card -- the top
 * two ranked entries from the same shared insight generator that feeds the
 * hero card's one-line summary (see utils/weatherInsights.ts), so this
 * doesn't duplicate that scan-and-rank logic. Replaces VisibilityCard in the
 * grid: raw visibility distance is the least commonly checked of the small
 * metric tiles, and a forward-looking summary is far more actionable.
 */
export function WeatherOutlookCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)
  const theme = useWeatherStore((s) => s.theme)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()
  const motionOn = !isRetro && !prefersReducedMotion

  const insights = useMemo(() => (weather ? buildInsights(weather, unit) : []), [weather, unit])

  if (!weather) return null

  const primary = insights[0]
  const secondary = insights[1]
  const slide = prefersReducedMotion ? 0 : 6

  /* 680px-window vertical budget: header 16 + two text items (each up to 3
     wrapped lines at ~13px/~11.5px) fill the remaining ~145px -- no gauge/ring
     visual needed here, this card is text-forward by design. */
  return (
    <BentoCard span="bento-1" floatDelay={1.1}>
      <div className="metric-card outlook-card">
        <div className="metric-header">
          <motion.span
            className="mx-icon"
            whileHover={motionOn ? { scale: 1.05, filter: 'drop-shadow(0 0 6px var(--accent))' } : undefined}
            whileTap={motionOn ? { scale: 0.95 } : undefined}
            transition={{ type: 'spring', stiffness: 300, damping: 18 }}
          >
            <OutlookIcon />
          </motion.span>
          <span className="metric-label">Outlook</span>
        </div>

        <div className="outlook-item outlook-item--primary">
          <span className="outlook-dot" style={{ background: insightColor(primary.kind) }} aria-hidden="true" />
          {isRetro ? (
            <span className="outlook-text outlook-text--primary">{primary.headline}</span>
          ) : (
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={primary.headline}
                className="outlook-text outlook-text--primary"
                initial={{ opacity: 0, y: slide }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -slide, transition: { duration: 0.2 } }}
                transition={{ duration: 0.3, ease: EASE_OUT }}
              >
                {primary.headline}
              </motion.span>
            </AnimatePresence>
          )}
        </div>

        {secondary && (
          <div className="outlook-item outlook-item--secondary">
            <span className="outlook-dot" style={{ background: insightColor(secondary.kind) }} aria-hidden="true" />
            {isRetro ? (
              <span className="outlook-text outlook-text--secondary">{secondary.headline}</span>
            ) : (
              <AnimatePresence mode="wait" initial={false}>
                <motion.span
                  key={secondary.headline}
                  className="outlook-text outlook-text--secondary"
                  initial={{ opacity: 0, y: slide }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -slide, transition: { duration: 0.2 } }}
                  transition={{ duration: 0.3, ease: EASE_OUT, delay: motionOn ? 0.05 : 0 }}
                >
                  {secondary.headline}
                </motion.span>
              </AnimatePresence>
            )}
          </div>
        )}
      </div>
    </BentoCard>
  )
}
