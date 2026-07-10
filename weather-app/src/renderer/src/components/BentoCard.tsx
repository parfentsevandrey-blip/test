import type { MouseEvent, ReactNode } from 'react'
import { useEffect } from 'react'
import { motion, useAnimation, useMotionValue, useReducedMotion, useSpring, useTransform } from 'framer-motion'
import { resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { Win95TitleButtons } from './retro/Win95TitleButtons'

interface BentoCardProps {
  /** Grid span/sizing class, e.g. "bento-hero", "bento-wide", "bento-1". */
  span: string
  /** Staggers the idle float AND the entrance cascade so cards feel individually alive. */
  floatDelay?: number
  children: ReactNode
}

/** Real spring physics now, not a CSS transition -- pushed well past the old
 *  2.6deg cap since a physically-resolved spring reads as premium even at a
 *  dramatic angle, where a CSS-eased tilt that far would read as cheap. */
const MAX_TILT_DEG = 9
const TILT_SPRING = { stiffness: 200, damping: 20, mass: 0.6 }
const EASE_OUT = [0.16, 1, 0.3, 1] as const

/**
 * Shared "floating" card shell used by every weather tile. For every theme
 * except win95 this is a Framer Motion physics playground:
 * - a staggered cinematic entrance (rise + de-blur + scale-in, cascading per card)
 * - a continuous idle float that kicks in once the entrance settles
 * - hover: a real spring-driven 3D tilt toward the cursor, plus a lift/scale
 * - press: a springy squash
 * win95 renders a plain, fully static div instead -- Framer Motion's inline
 * transform would otherwise fight the retro theme's own bevel styling, and a
 * floating/tilting window is the one thing Windows 95 never did.
 */
export function BentoCard({ span, floatDelay = 0, children }: BentoCardProps): JSX.Element {
  const theme = useWeatherStore((s) => s.theme)
  const weather = useWeatherStore((s) => s.weather)
  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()

  const mouseX = useMotionValue(0.5)
  const mouseY = useMotionValue(0.5)
  const springX = useSpring(mouseX, TILT_SPRING)
  const springY = useSpring(mouseY, TILT_SPRING)
  const rotateX = useTransform(springY, [0, 1], [MAX_TILT_DEG, -MAX_TILT_DEG])
  const rotateY = useTransform(springX, [0, 1], [-MAX_TILT_DEG, MAX_TILT_DEG])

  const controls = useAnimation()

  useEffect(() => {
    if (isRetro || prefersReducedMotion) return
    let cancelled = false
    async function sequence(): Promise<void> {
      await controls.start('visible')
      if (!cancelled) void controls.start('float')
    }
    void sequence()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isRetro, prefersReducedMotion])

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>): void => {
    const rect = event.currentTarget.getBoundingClientRect()
    mouseX.set((event.clientX - rect.left) / rect.width)
    mouseY.set((event.clientY - rect.top) / rect.height)
  }

  const handleMouseLeave = (): void => {
    mouseX.set(0.5)
    mouseY.set(0.5)
  }

  if (isRetro) {
    return (
      <div className={`bento-card glass-panel ${span}`}>
        <div className="bento-card-content">{children}</div>
        <Win95TitleButtons />
      </div>
    )
  }

  return (
    <motion.div
      className={`bento-card glass-panel ${span}`}
      style={prefersReducedMotion ? undefined : { perspective: 950, rotateX, rotateY }}
      onMouseMove={prefersReducedMotion ? undefined : handleMouseMove}
      onMouseLeave={prefersReducedMotion ? undefined : handleMouseLeave}
      initial={prefersReducedMotion ? false : 'hidden'}
      animate={prefersReducedMotion ? undefined : controls}
      variants={{
        hidden: { opacity: 0, y: 28, scale: 0.94, filter: 'blur(8px)' },
        visible: {
          opacity: 1,
          y: 0,
          scale: 1,
          filter: 'blur(0px)',
          transition: { duration: 0.85, delay: floatDelay * 0.5 + 0.05, ease: EASE_OUT }
        },
        float: {
          y: [0, -7, 0],
          transition: { duration: 8, ease: 'easeInOut', repeat: Infinity, delay: floatDelay * 0.5 }
        }
      }}
      whileHover={prefersReducedMotion ? undefined : { scale: 1.035, transition: { type: 'spring', stiffness: 320, damping: 18 } }}
      whileTap={prefersReducedMotion ? undefined : { scale: 0.98 }}
    >
      <div className="bento-card-content">{children}</div>
      <Win95TitleButtons />
    </motion.div>
  )
}
