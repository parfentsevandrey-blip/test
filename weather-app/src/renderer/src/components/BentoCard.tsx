import type { CSSProperties, MouseEvent, ReactNode } from 'react'
import { Win95TitleButtons } from './retro/Win95TitleButtons'

interface BentoCardProps {
  /** Grid span/sizing class, e.g. "bento-hero", "bento-wide", "bento-1". */
  span: string
  /** Staggers the idle float AND the entrance cascade so cards feel individually alive. */
  floatDelay?: number
  children: ReactNode
}

/** Max tilt in degrees at the card's edge. Kept subtle: premium, not gimmicky. */
const MAX_TILT_DEG = 2.6

/**
 * Shared "floating" card shell used by every weather tile. Provides:
 * - a staggered cinematic entrance (rise + de-blur, cascading per card)
 * - a continuous idle float (starts after the entrance completes)
 * - hover: gentle 3D tilt toward the cursor + lift/scale + a cursor-following
 *   glow. Tilt/scale/float live on independent CSS properties (transform /
 *   scale / translate) so they compose without fighting.
 */
export function BentoCard({ span, floatDelay = 0, children }: BentoCardProps): JSX.Element {
  const handleMouseMove = (event: MouseEvent<HTMLDivElement>): void => {
    const el = event.currentTarget
    const rect = el.getBoundingClientRect()
    const fx = (event.clientX - rect.left) / rect.width
    const fy = (event.clientY - rect.top) / rect.height

    el.style.setProperty('--glow-x', `${fx * 100}%`)
    el.style.setProperty('--glow-y', `${fy * 100}%`)
    el.style.setProperty('--tilt-x', `${((0.5 - fy) * 2 * MAX_TILT_DEG).toFixed(2)}deg`)
    el.style.setProperty('--tilt-y', `${((fx - 0.5) * 2 * MAX_TILT_DEG).toFixed(2)}deg`)
  }

  const handleMouseLeave = (event: MouseEvent<HTMLDivElement>): void => {
    const el = event.currentTarget
    el.style.setProperty('--tilt-x', '0deg')
    el.style.setProperty('--tilt-y', '0deg')
  }

  const style = { '--stagger': `${floatDelay}s` } as CSSProperties

  return (
    <div
      className={`bento-card glass-panel ${span}`}
      style={style}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      <div className="bento-card-glow" />
      <div className="bento-card-content">{children}</div>
      {/* Hidden outside the win95 theme (display gated in retro.css). */}
      <Win95TitleButtons />
    </div>
  )
}
