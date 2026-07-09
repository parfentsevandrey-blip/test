import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { isSameLocation, useWeatherStore } from '../store/useWeatherStore'
import { useListNav } from '../hooks/useListNav'
import { useDelayedUnmount } from '../hooks/useDelayedUnmount'
import { fetchBatchCurrentConditions, type BatchCurrentConditions } from '../api/openMeteo'
import { celsiusTo } from '../utils/units'
import './FavoritesMenu.css'

const EXIT_MS = 160

function StarIcon({ filled }: { filled: boolean }): JSX.Element {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 3.2 L14.7 8.9 L21 9.8 L16.5 14.1 L17.6 20.3 L12 17.3 L6.4 20.3 L7.5 14.1 L3 9.8 L9.3 8.9 Z" />
    </svg>
  )
}

function CloseIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden="true">
      <line x1={6} y1={6} x2={18} y2={18} />
      <line x1={18} y1={6} x2={6} y2={18} />
    </svg>
  )
}

export function FavoritesMenu(): JSX.Element {
  const favorites = useWeatherStore((s) => s.favorites)
  const location = useWeatherStore((s) => s.location)
  const unit = useWeatherStore((s) => s.unit)
  const toggleFavorite = useWeatherStore((s) => s.toggleFavorite)
  const selectLocation = useWeatherStore((s) => s.selectLocation)

  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const itemRefs = useRef<Array<HTMLButtonElement | null>>([])
  const mounted = useDelayedUnmount(open, EXIT_MS)

  // Live temp per saved city — one batched request (not one per favorite),
  // fetched fresh each time the dropdown opens. null = still loading.
  const [conditions, setConditions] = useState<Array<BatchCurrentConditions | null> | null>(null)

  useEffect(() => {
    function handleMouseDown(event: MouseEvent): void {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [])

  useEffect(() => {
    if (!open || favorites.length === 0) return
    const controller = new AbortController()
    setConditions(null)
    fetchBatchCurrentConditions(favorites, controller.signal)
      .then(setConditions)
      .catch((err) => {
        if (err instanceof DOMException && err.name === 'AbortError') return
        setConditions(favorites.map(() => null))
      })
    return () => controller.abort()
  }, [open, favorites])

  const closeAndRefocusTrigger = (): void => {
    setOpen(false)
    triggerRef.current?.focus()
  }

  // Real DOM focus moves between rows to match the highlight (a standard
  // ARIA menu), unlike SearchBar's combobox which keeps focus on its input.
  const { highlightedIndex, onKeyDown } = useListNav({
    items: favorites,
    isOpen: open,
    onSelect: (favorite) => {
      closeAndRefocusTrigger()
      void selectLocation(favorite)
    },
    onClose: closeAndRefocusTrigger
  })

  useEffect(() => {
    if (highlightedIndex >= 0) itemRefs.current[highlightedIndex]?.focus()
  }, [highlightedIndex])

  const currentIsFavorite = location !== null && favorites.some((f) => isSameLocation(f, location))

  return (
    <div className="favorites-menu" ref={rootRef} tabIndex={-1} onKeyDown={onKeyDown}>
      <button
        type="button"
        ref={triggerRef}
        className={`icon-btn${open ? ' menu-open' : ''}`}
        onClick={() => setOpen((v) => !v)}
        aria-label="Saved locations"
        title="Saved locations"
      >
        <StarIcon filled={favorites.length > 0} />
      </button>

      {mounted && (
        <div
          className={`favorites-dropdown${open ? '' : ' is-closing'}`}
          role="menu"
          aria-label="Saved locations"
        >
          {location && (
            <button
              type="button"
              className="favorites-action"
              role="menuitem"
              onClick={() => toggleFavorite(location)}
            >
              <StarIcon filled={currentIsFavorite} />
              <span>
                {currentIsFavorite ? 'Remove' : 'Save'} {location.name}
              </span>
            </button>
          )}

          {favorites.length > 0 && <div className="favorites-divider" />}

          {favorites.length === 0 ? (
            <div className="favorites-empty">No saved locations yet</div>
          ) : (
            favorites.map((favorite, index) => (
              <div
                className={
                  'favorites-item-row' +
                  (location && isSameLocation(favorite, location) ? ' is-current' : '')
                }
                key={`${favorite.name}-${favorite.latitude}-${favorite.longitude}`}
                style={{ '--row-i': index } as CSSProperties}
              >
                <button
                  type="button"
                  role="menuitem"
                  className="favorites-item"
                  ref={(el) => {
                    itemRefs.current[index] = el
                  }}
                  onClick={() => {
                    setOpen(false)
                    void selectLocation(favorite)
                  }}
                >
                  <span className="favorites-item-text">
                    <span className="favorites-item-name">{favorite.name}</span>
                    <span className="favorites-item-meta">
                      {favorite.admin1 ? `${favorite.admin1}, ` : ''}
                      {favorite.country}
                    </span>
                  </span>
                  {conditions?.[index] && (
                    <span className="favorites-item-temp">
                      {Math.round(celsiusTo(unit, conditions[index]!.temperature))}°
                    </span>
                  )}
                </button>
                <button
                  type="button"
                  className="favorites-remove"
                  aria-label={`Remove ${favorite.name} from saved locations`}
                  onClick={(event) => {
                    event.stopPropagation()
                    toggleFavorite(favorite)
                    // The clicked button's own row unmounts immediately (the
                    // list re-keys), which would otherwise drop focus to
                    // <body> and strand Escape/arrow-key handling outside
                    // the menu — reclaim it on the menu root instead.
                    rootRef.current?.focus()
                  }}
                >
                  <CloseIcon />
                </button>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
