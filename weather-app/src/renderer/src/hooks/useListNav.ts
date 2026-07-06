import { useEffect, useState, type KeyboardEvent } from 'react'

export interface UseListNavOptions<T> {
  items: readonly T[]
  isOpen: boolean
  onSelect: (item: T) => void
  onClose: () => void
}

export interface UseListNavResult {
  highlightedIndex: number
  onKeyDown: (event: KeyboardEvent<HTMLElement>) => void
}

/**
 * Arrow-key/Enter/Escape navigation for a popover list of selectable items.
 * Consumers decide how to surface `highlightedIndex`: SearchBar keeps real
 * focus on its text input and points aria-activedescendant at it (a true
 * combobox), while FavoritesMenu instead moves real DOM focus to match it
 * (a standard menu) — see each component's own effect.
 */
export function useListNav<T>({ items, isOpen, onSelect, onClose }: UseListNavOptions<T>): UseListNavResult {
  const [highlightedIndex, setHighlightedIndex] = useState(-1)

  useEffect(() => {
    setHighlightedIndex(-1)
  }, [isOpen, items.length])

  function onKeyDown(event: KeyboardEvent<HTMLElement>): void {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }
    if (!isOpen || items.length === 0) return

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setHighlightedIndex((i) => (i + 1) % items.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlightedIndex((i) => (i <= 0 ? items.length - 1 : i - 1))
    } else if (event.key === 'Enter') {
      if (highlightedIndex >= 0 && highlightedIndex < items.length) {
        event.preventDefault()
        onSelect(items[highlightedIndex])
      }
    }
  }

  return { highlightedIndex, onKeyDown }
}
