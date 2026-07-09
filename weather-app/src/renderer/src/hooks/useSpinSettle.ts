import { useEffect, useRef, type RefObject } from 'react'

/** Degrees/ms — matches the pace of the CSS keyframe this hook replaces (`spin 1s linear infinite`). */
const DEG_PER_MS = 360 / 1000
/** Settle duration bounds: a near-full extra turn takes the max, an already-almost-upright angle is near-instant. */
const SETTLE_MAX_MS = 900
const SETTLE_MIN_MS = 120

/**
 * Drives a spin icon's rotation manually via rAF instead of an infinite CSS
 * keyframe, so the exact current angle is always known on the JS side. When
 * `active` drops to false, eases from that angle to the next upright turn
 * instead of a CSS animation's hard mid-rotation cut. Under
 * prefers-reduced-motion the icon just stays static.
 */
export function useSpinSettle(active: boolean): RefObject<SVGSVGElement> {
  const ref = useRef<SVGSVGElement>(null)
  const angleRef = useRef(0)

  useEffect(() => {
    const el = ref.current
    if (!el) return undefined

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      el.style.transition = 'none'
      el.style.transform = 'none'
      return undefined
    }

    if (!active) {
      const current = angleRef.current % 360
      const remaining = current === 0 ? 360 : 360 - current
      const duration = Math.max(SETTLE_MIN_MS, Math.round((remaining / 360) * SETTLE_MAX_MS))
      el.style.transition = `transform ${duration}ms var(--ease-out)`
      el.style.transform = `rotate(${current + remaining}deg)`
      angleRef.current = current + remaining
      return undefined
    }

    el.style.transition = 'none'
    let rafId: number
    let lastTs: number | null = null
    const tick = (ts: number): void => {
      if (lastTs !== null) {
        angleRef.current = (angleRef.current + (ts - lastTs) * DEG_PER_MS) % 360
        el.style.transform = `rotate(${angleRef.current}deg)`
      }
      lastTs = ts
      rafId = requestAnimationFrame(tick)
    }
    rafId = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(rafId)
  }, [active])

  return ref
}
