import { useEffect, useRef } from 'react'
import { useWeatherStore } from '../store/useWeatherStore'
import type { GeoLocation } from '../types/weather'

function SearchIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.4} aria-hidden="true">
      <circle cx={7} cy={7} r={5.25} />
      <line x1={11} y1={11} x2={14.5} y2={14.5} />
    </svg>
  )
}

export function SearchBar(): JSX.Element {
  const searchQuery = useWeatherStore((s) => s.searchQuery)
  const searchResults = useWeatherStore((s) => s.searchResults)
  const isSearching = useWeatherStore((s) => s.isSearching)
  const search = useWeatherStore((s) => s.search)
  const clearSearch = useWeatherStore((s) => s.clearSearch)
  const selectLocation = useWeatherStore((s) => s.selectLocation)

  const rootRef = useRef<HTMLDivElement>(null)

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

  return (
    <div className="search-bar glass-panel" ref={rootRef}>
      <div className="search-input-row">
        <SearchIcon />
        <input
          className="search-input"
          value={searchQuery}
          onChange={(e) => void search(e.target.value)}
          placeholder="Search for a city..."
          aria-label="Search for a city"
        />
      </div>
      {showResults && (
        <div className="search-results glass-panel">
          {isSearching && searchResults.length === 0 ? (
            <div className="search-empty">Searching...</div>
          ) : !isSearching && searchResults.length === 0 ? (
            <div className="search-empty">No matches found</div>
          ) : (
            searchResults.map((result) => (
              <button
                type="button"
                className="search-result-item"
                key={`${result.name}-${result.latitude}-${result.longitude}`}
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
