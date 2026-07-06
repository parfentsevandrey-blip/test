import { useLayoutEffect, useRef, useState, type RefObject } from 'react'

interface SegThumbBox {
  left: number
  width: number
}

export interface SegThumbResult extends SegThumbBox {
  ready: boolean
}

/**
 * Measures the real `.seg.active` box inside a segmented-control container
 * via getBoundingClientRect (not a CSS percentage trick), since segments can
 * render at genuinely unequal auto-widths. `ready` flips true only after the
 * first real measurement, so the sliding-thumb CSS transition can stay armed
 * off until then and never animate in from a wrong (0,0) starting position.
 */
export function useSegThumb(containerRef: RefObject<HTMLElement>, activeKey: string): SegThumbResult {
  const [box, setBox] = useState<SegThumbBox>({ left: 0, width: 0 })
  const readyRef = useRef(false)
  const [ready, setReady] = useState(false)

  const measure = (): void => {
    const container = containerRef.current
    if (!container) return
    const active = container.querySelector<HTMLElement>('.seg.active')
    if (!active) return
    const c = container.getBoundingClientRect()
    const a = active.getBoundingClientRect()
    // The thumb is absolutely positioned relative to the container's PADDING
    // box (i.e. left:0 starts just inside the border), while getBoundingClientRect
    // measures the BORDER box — subtract the container's own rendered border
    // width or the thumb drifts by exactly that many pixels.
    setBox({ left: a.left - c.left - container.clientLeft, width: a.width })
    if (!readyRef.current) {
      readyRef.current = true
      setReady(true)
    }
  }

  useLayoutEffect(() => {
    measure()
    const raf = requestAnimationFrame(measure)
    const container = containerRef.current
    const ro = container ? new ResizeObserver(measure) : null
    if (container && ro) ro.observe(container)
    return () => {
      cancelAnimationFrame(raf)
      ro?.disconnect()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useLayoutEffect(() => {
    measure()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeKey])

  return { ...box, ready }
}
