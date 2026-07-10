import type { MouseEvent, ReactNode } from 'react'
import { useEffect, useMemo, useRef } from 'react'
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

/** Restrained tilt -- a physically-resolved spring reads as premium even at a
 *  small angle; a wide swing (an earlier pass pushed this to 9deg) reads as
 *  aggressive/gimmicky instead, and does nothing for feel that's worth the
 *  extra motion. Softer spring too, so hover settles instead of overshooting. */
const MAX_TILT_DEG = 3.5
const TILT_SPRING = { stiffness: 170, damping: 26, mass: 0.6 }
const HOVER_SPRING = { type: 'spring', stiffness: 260, damping: 24 } as const
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

  // Cached once per hover (see handleMouseEnter) instead of calling
  // getBoundingClientRect() on every mousemove -- that call forces a
  // synchronous layout, and doing it on every pixel of movement is exactly
  // the "layout thrashing" pattern that made hovering feel sluggish.
  const rectRef = useRef<DOMRect | null>(null)

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

  const handleMouseEnter = (event: MouseEvent<HTMLDivElement>): void => {
    rectRef.current = event.currentTarget.getBoundingClientRect()
  }

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>): void => {
    const rect = rectRef.current
    if (!rect) return
    mouseX.set((event.clientX - rect.left) / rect.width)
    mouseY.set((event.clientY - rect.top) / rect.height)
  }

  const handleMouseLeave = (): void => {
    rectRef.current = null
    mouseX.set(0.5)
    mouseY.set(0.5)
  }

  // Stable across re-renders (weather/theme ticks, parent updates) so Framer
  // Motion isn't handed a fresh variants object every time -- floatDelay is
  // fixed per card instance, so this only ever computes once in practice.
  const variants = useMemo(
    () => ({
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
        transition: { duration: 8, ease: 'easeInOut' as const, repeat: Infinity, delay: floatDelay * 0.5 }
      }
    }),
    [floatDelay]
  )

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
      onMouseEnter={prefersReducedMotion ? undefined : handleMouseEnter}
      onMouseMove={prefersReducedMotion ? undefined : handleMouseMove}
      onMouseLeave={prefersReducedMotion ? undefined : handleMouseLeave}
      initial={prefersReducedMotion ? false : 'hidden'}
      animate={prefersReducedMotion ? undefined : controls}
      variants={variants}
      whileHover={prefersReducedMotion ? undefined : { scale: 1.015, transition: HOVER_SPRING }}
      whileTap={prefersReducedMotion ? undefined : { scale: 0.985 }}
    >
      <div className="bento-card-content">{children}</div>
      <Win95TitleButtons />
    </motion.div>
  )
}
