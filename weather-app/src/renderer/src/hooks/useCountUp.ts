import { useEffect, useRef, useState } from 'react'

const DEFAULT_DURATION_MS = 900

/**
 * Animates a displayed number toward `target` over `durationMs` with an
 * ease-out cubic, starting from whatever value is currently shown (0 on
 * first mount, so the initial load counts up too). Under
 * prefers-reduced-motion the value jumps instantly. The rAF loop is
 * cancelled on retarget/unmount.
 */
export function useCountUp(target: number, durationMs: number = DEFAULT_DURATION_MS): number {
  const [display, setDisplay] = useState(0)
  const displayRef = useRef(0)

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      displayRef.current = target
      setDisplay(target)
      return undefined
    }

    const from = displayRef.current
    if (from === target) return undefined

    const start = performance.now()
    let frame = requestAnimationFrame(function tick(now: number): void {
      const t = Math.min(1, (now - start) / durationMs)
      const eased = 1 - (1 - t) ** 3
      const value = from + (target - from) * eased
      displayRef.current = value
      setDisplay(value)
      if (t < 1) frame = requestAnimationFrame(tick)
    })
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs])

  return display
}
