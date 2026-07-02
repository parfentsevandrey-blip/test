import type { CSSProperties, MouseEvent, ReactNode } from 'react'

interface BentoCardProps {
  /** Grid span/sizing class, e.g. "bento-hero", "bento-wide", "bento-tall", "bento-1". */
  span: string
  /** Staggers the idle floating animation so cards don't bob in unison. */
  floatDelay?: number
  children: ReactNode
}

/**
 * Shared "floating" card shell used by every weather metric tile. Provides:
 * - a continuous, gently staggered idle float (via the `bento-float` CSS animation)
 * - a hover reaction: lifts, scales up slightly, and brightens with a
 *   cursor-following glow (position tracked via CSS custom properties, no
 *   per-frame JS loop needed)
 */
export function BentoCard({ span, floatDelay = 0, children }: BentoCardProps): JSX.Element {
  const handleMouseMove = (event: MouseEvent<HTMLDivElement>): void => {
    const el = event.currentTarget
    const rect = el.getBoundingClientRect()
    el.style.setProperty('--glow-x', `${((event.clientX - rect.left) / rect.width) * 100}%`)
    el.style.setProperty('--glow-y', `${((event.clientY - rect.top) / rect.height) * 100}%`)
  }

  const style: CSSProperties = { animationDelay: `${floatDelay}s` }

  return (
    <div
      className={`bento-card glass-panel ${span}`}
      style={style}
      onMouseMove={handleMouseMove}
    >
      <div className="bento-card-glow" />
      <div className="bento-card-content">{children}</div>
    </div>
  )
}
