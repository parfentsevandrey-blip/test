import { useEffect, useState } from 'react'

/**
 * Keeps a conditionally-rendered node mounted for `delayMs` after `isOpen`
 * goes false, so a caller can toggle a `.closing` class and let a CSS exit
 * animation play instead of the node vanishing on the same frame it closes.
 */
export function useDelayedUnmount(isOpen: boolean, delayMs: number): boolean {
  const [mounted, setMounted] = useState(isOpen)

  useEffect(() => {
    if (isOpen) {
      setMounted(true)
      return undefined
    }
    if (!mounted) return undefined
    const timeout = setTimeout(() => setMounted(false), delayMs)
    return () => clearTimeout(timeout)
  }, [isOpen, mounted, delayMs])

  return mounted
}
