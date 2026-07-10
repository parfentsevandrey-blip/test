import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { RainIcon, SunBurstIcon } from './icons'
import { formatHour } from '../utils/units'
import './MetricCards.css'

/** Container-level stagger for the bar cascade; children read "hidden"/"visible" off it. */
const barsContainerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.045, delayChildren: 0.05 } }
}

const barColVariants = {
  hidden: { opacity: 0, y: 12, scale: 0.9 },
  visible: { opacity: 1, y: 0, scale: 1, transition: { type: 'spring' as const, stiffness: 260, damping: 20 } }
}

export function PrecipitationCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const theme = useWeatherStore((s) => s.theme)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()
  const motionOn = !isRetro && !prefersReducedMotion

  if (!weather) return null

  const flooredHour = `${weather.current.time.slice(0, 13)}:00`
  const startIndex = weather.hourly.findIndex((h) => h.time === flooredHour)
  const from = startIndex === -1 ? 0 : startIndex
  const upcoming = weather.hourly.slice(from, from + 12)
  const allDry = upcoming.every((point) => point.precipitationProbability === 0)

  const todayMax = weather.daily[0]?.precipitationProbabilityMax ?? (weather.current.precipitation > 0 ? 100 : 0)
  const fillTransition = motionOn ? { type: 'spring' as const, stiffness: 90, damping: 16 } : { duration: 0 }
  const instantExit = { opacity: 0, transition: { duration: motionOn ? 0.2 : 0 } }

  return (
    <BentoCard span="bento-wide" floatDelay={0.9}>
      <div className="metric-card precip-card">
        <div className="metric-header">
          <motion.span
            className="mx-icon"
            whileHover={motionOn ? { scale: 1.05, filter: 'drop-shadow(0 0 6px var(--accent))' } : undefined}
            whileTap={motionOn ? { scale: 0.95 } : undefined}
            transition={{ type: 'spring', stiffness: 300, damping: 18 }}
          >
            <RainIcon />
          </motion.span>
          <span className="metric-label">Chance of Rain</span>
        </div>
        <div className="metric-value">
          <span className="mx-value">{Math.round(todayMax)}</span>
          <span className="mx-unit">%</span>
        </div>
        <div className="metric-sub">Peak chance today</div>

        <AnimatePresence mode="wait" initial={false}>
          {allDry ? (
            <motion.div
              key="empty"
              className="precip-empty"
              initial={motionOn ? { opacity: 0, y: 8 } : false}
              animate={{ opacity: 1, y: 0, transition: { duration: motionOn ? 0.35 : 0 } }}
              exit={instantExit}
            >
              <SunBurstIcon />
              <span>No rain expected in the next 12 hours</span>
            </motion.div>
          ) : (
            <motion.div
              key="bars"
              className="precip-bars"
              variants={barsContainerVariants}
              initial={motionOn ? 'hidden' : false}
              animate="visible"
              exit={instantExit}
            >
              {upcoming.map((point, index) => (
                <motion.div
                  className={`precip-bar-col${index === 0 ? ' is-now' : ''}`}
                  key={point.time}
                  variants={barColVariants}
                >
                  <div className="precip-bar-track">
                    <motion.div
                      className="precip-bar-fill"
                      animate={{ height: `${Math.max(2, point.precipitationProbability)}%` }}
                      transition={fillTransition}
                    />
                  </div>
                  <span className="precip-bar-value">{Math.round(point.precipitationProbability)}%</span>
                  <span className="precip-bar-label">{index === 0 ? 'Now' : formatHour(point.time)}</span>
                </motion.div>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </BentoCard>
  )
}
