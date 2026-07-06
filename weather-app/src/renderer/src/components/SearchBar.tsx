import { useEffect, useId, useRef, type CSSProperties } from 'react'
import { useWeatherStore } from '../store/useWeatherStore'
import { useListNav } from '../hooks/useListNav'
import { useDelayedUnmount } from '../hooks/useDelayedUnmount'
import type { GeoLocation } from '../types/weather'

const EXIT_MS = 160

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
  const mounted = useDelayedUnmount(showResults, EXIT_MS)

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
      {mounted && (
        <div
          className={`search-results${showResults ? '' : ' is-closing'}`}
          id={listboxId}
          role="listbox"
          aria-label="City search results"
        >
          {searchError !== null ? (
            <div className="search-empty">Search unavailable — check your connection</div>
          ) : isSearching && searchResults.length === 0 ? (
            <div className="search-empty">Searching...</div>
          ) : !isSearching && searchResults.length === 0 ? (
            <div className="search-empty">No matches found</div>
          ) : (
            searchResults.map((result, index) => (
              <button
                type="button"
                id={`${listboxId}-option-${index}`}
                role="option"
                aria-selected={index === highlightedIndex}
                className={`search-result-item${index === highlightedIndex ? ' is-highlighted' : ''}`}
                key={`${result.name}-${result.latitude}-${result.longitude}`}
                style={{ '--row-i': index } as CSSProperties}
                onClick={() => handleSelect(result)}
              >
                <span className="search-result-name">{result.name}</span>
                <span className="search-result-meta">
                  {result.admin1 ? `${result.admin1}, ` : ''}
                  {result.country}
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
