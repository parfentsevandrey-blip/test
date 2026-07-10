import { useEffect, useId, useRef, type CSSProperties } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { useWeatherStore } from '../store/useWeatherStore'
import { useListNav } from '../hooks/useListNav'
import { useListHighlightThumb } from '../hooks/useListHighlightThumb'
import type { GeoLocation } from '../types/weather'

function SearchIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
      <circle cx={7} cy={7} r={5.25} />
      <line x1={11} y1={11} x2={14.5} y2={14.5} />
    </svg>
  )
}

export function SearchBar(): JSX.Element {
  const searchQuery = useWeatherStore((s) => s.searchQuery)
  const searchResults = useWeatherStore((s) => s.searchResults)
  const isSearching = useWeatherStore((s) => s.isSearching)
  const searchError = useWeatherStore((s) => s.searchError)
  const search = useWeatherStore((s) => s.search)
  const clearSearch = useWeatherStore((s) => s.clearSearch)
  const selectLocation = useWeatherStore((s) => s.selectLocation)

  const rootRef = useRef<HTMLDivElement>(null)
  const resultsRef = useRef<HTMLDivElement>(null)
  const reactId = useId()
  const listboxId = `search-listbox-${reactId}`

  useEffect(() => {
    function handleMouseDown(event: MouseEvent): void {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        clearSearch()
      }
    }

    document.addEventListener('mousedown', handleMouseDown)
    return () => {
      document.removeEventListener('mousedown', handleMouseDown)
    }
  }, [clearSearch])

  const showResults = searchQuery.trim().length >= 2

  const handleSelect = (result: GeoLocation): void => {
    void selectLocation(result)
  }

  // Real focus stays on the input the whole time (a command-palette-style
  // combobox) — the highlight below is virtual, surfaced via
  // aria-activedescendant and the .is-highlighted class.
  const { highlightedIndex, onKeyDown } = useListNav({
    items: searchResults,
    isOpen: showResults,
    onSelect: handleSelect,
    onClose: clearSearch
  })

  const { top: thumbTop, height: thumbHeight, ready: thumbReady } = useListHighlightThumb(
    resultsRef,
    highlightedIndex
  )

  return (
    <div className="search-bar" ref={rootRef}>
      <div className="search-input-row">
        <SearchIcon />
        <input
          className="search-input"
          value={searchQuery}
          onChange={(e) => void search(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Search for a city..."
          aria-label="Search for a city"
          role="combobox"
          aria-expanded={showResults}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={highlightedIndex >= 0 ? `${listboxId}-option-${highlightedIndex}` : undefined}
        />
        <span className="search-kbd" aria-hidden="true">
          Ctrl K
        </span>
      </div>
      <AnimatePresence>
        {showResults && (
          <motion.div
            className={`search-results${thumbReady ? ' list-thumb-ready' : ''}`}
            id={listboxId}
            role="listbox"
            aria-label="City search results"
            ref={resultsRef}
            initial={{ opacity: 0, scale: 0.96, y: -8, filter: 'blur(6px)' }}
            animate={{ opacity: 1, scale: 1, y: 0, filter: 'blur(0px)' }}
            exit={{ opacity: 0, scale: 0.97, y: -4, filter: 'blur(4px)', transition: { duration: 0.15 } }}
            transition={{ type: 'spring', stiffness: 340, damping: 28 }}
          >
            {searchError !== null ? (
              <div className="search-empty">Search unavailable — check your connection</div>
            ) : isSearching && searchResults.length === 0 ? (
              <div className="search-empty">Searching...</div>
            ) : !isSearching && searchResults.length === 0 ? (
              <div className="search-empty">No matches found</div>
            ) : (
              <>
                <motion.span
                  className="list-nav-thumb"
                  animate={{ top: thumbTop, height: thumbHeight }}
                  transition={thumbReady ? { type: 'spring', stiffness: 420, damping: 34 } : { duration: 0 }}
                  aria-hidden="true"
                />
                {searchResults.map((result, index) => (
                  <motion.button
                    type="button"
                    id={`${listboxId}-option-${index}`}
                    role="option"
                    aria-selected={index === highlightedIndex}
                    className={`search-result-item${index === highlightedIndex ? ' is-highlighted' : ''}`}
                    key={`${result.name}-${result.latitude}-${result.longitude}`}
                    data-nav-index={index}
                    style={{ '--row-i': index } as CSSProperties}
                    onClick={() => handleSelect(result)}
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.26, delay: 0.06 + index * 0.024, ease: [0.16, 1, 0.3, 1] }}
                    whileHover={{ x: 3 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    <span className="search-result-name">{result.name}</span>
                    <span className="search-result-meta">
                      {result.admin1 ? `${result.admin1}, ` : ''}
                      {result.country}
                    </span>
                  </motion.button>
                ))}
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
