import { useEffect, useRef, useState } from 'react'
import { isSameLocation, useWeatherStore } from '../store/useWeatherStore'
import './FavoritesMenu.css'

function StarIcon({ filled }: { filled: boolean }): JSX.Element {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 3.2 L14.7 8.9 L21 9.8 L16.5 14.1 L17.6 20.3 L12 17.3 L6.4 20.3 L7.5 14.1 L3 9.8 L9.3 8.9 Z" />
    </svg>
  )
}

export function FavoritesMenu(): JSX.Element {
  const favorites = useWeatherStore((s) => s.favorites)
  const location = useWeatherStore((s) => s.location)
  const toggleFavorite = useWeatherStore((s) => s.toggleFavorite)
  const selectLocation = useWeatherStore((s) => s.selectLocation)

  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleMouseDown(event: MouseEvent): void {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [])

  const currentIsFavorite = location !== null && favorites.some((f) => isSameLocation(f, location))

  return (
    <div className="favorites-menu" ref={rootRef}>
      <button
        type="button"
        className={`icon-btn${open ? ' menu-open' : ''}`}
        onClick={() => setOpen((v) => !v)}
        aria-label="Saved locations"
        title="Saved locations"
      >
        <StarIcon filled={favorites.length > 0} />
      </button>

      {open && (
        <div className="favorites-dropdown">
          {location && (
            <button
              type="button"
              className="favorites-action"
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
            favorites.map((favorite) => (
              <button
                type="button"
                className={
                  'favorites-item' +
                  (location && isSameLocation(favorite, location) ? ' is-current' : '')
                }
                key={`${favorite.name}-${favorite.latitude}-${favorite.longitude}`}
                onClick={() => {
                  setOpen(false)
                  void selectLocation(favorite)
                }}
              >
                <span className="favorites-item-name">{favorite.name}</span>
                <span className="favorites-item-meta">
                  {favorite.admin1 ? `${favorite.admin1}, ` : ''}
                  {favorite.country}
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
