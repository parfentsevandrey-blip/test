import { useLayoutEffect, useState, type RefObject } from 'react'

interface ThumbBox {
  top: number
  height: number
}

export interface ListHighlightThumbResult extends ThumbBox {
  ready: boolean
}

/**
 * Vertical sibling of useSegThumb: measures the row carrying
 * `data-nav-index={highlightedIndex}` inside `containerRef` and returns its
 * top/height (relative to the container, which must be `position`-d so
 * `offsetTop` resolves against it) for a sliding highlight bar behind
 * keyboard-navigated list rows (SearchBar results, FavoritesMenu).
 *
 * `ready` flips true only once a highlighted row has actually been measured
 * (box + ready commit together, so the CSS transition never animates in
 * from a stale/zero position -- same technique as useSegThumb), and resets
 * to false whenever highlightedIndex goes back to -1 so the next highlight
 * starts fresh instead of sliding in from wherever the thumb last was.
 */
export function useListHighlightThumb(
  containerRef: RefObject<HTMLElement>,
  highlightedIndex: number
): ListHighlightThumbResult {
  const [box, setBox] = useState<ThumbBox>({ top: 0, height: 0 })
  const [ready, setReady] = useState(false)

  useLayoutEffect(() => {
    if (highlightedIndex < 0) {
      setReady(false)
      return undefined
    }

    const measure = (): void => {
      const container = containerRef.current
      const row = container?.querySelector<HTMLElement>(`[data-nav-index="${highlightedIndex}"]`)
      if (!row) return
      setBox({ top: row.offsetTop, height: row.offsetHeight })
      setReady(true)
    }

    measure()
    const raf = requestAnimationFrame(measure)
    return () => cancelAnimationFrame(raf)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [highlightedIndex])

  return { ...box, ready }
}
