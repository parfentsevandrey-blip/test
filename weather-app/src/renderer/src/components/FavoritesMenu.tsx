import { useEffect, useRef, useState, type CSSProperties, type MouseEvent as ReactMouseEvent } from 'react'
import { AnimatePresence, motion, Reorder, useDragControls, useReducedMotion } from 'framer-motion'
import { isSameLocation, resolveTheme, useWeatherStore } from '../store/useWeatherStore'
import { useListNav } from '../hooks/useListNav'
import { useDelayedUnmount } from '../hooks/useDelayedUnmount'
import { useListHighlightThumb } from '../hooks/useListHighlightThumb'
import { fetchBatchCurrentConditions, type BatchCurrentConditions } from '../api/openMeteo'
import { celsiusTo } from '../utils/units'
import type { GeoLocation } from '../types/weather'
import './FavoritesMenu.css'

const EXIT_MS = 160

// Mirrors useWeatherStore's own (unexported) FAVORITES_STORAGE_KEY so that
// drag-to-reorder can write through the exact same localStorage slot
// toggleFavorite already uses. There's no dedicated reorder action on the
// store, and adding one is out of this file's scope — but zustand hooks
// always carry a static .setState, so we can update the shared store
// directly from here without needing to touch useWeatherStore.ts.
const FAVORITES_STORAGE_KEY = 'cinematic-weather:favorites'

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

function GripIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx={9} cy={6} r={1.3} />
      <circle cx={9} cy={12} r={1.3} />
      <circle cx={9} cy={18} r={1.3} />
      <circle cx={15} cy={6} r={1.3} />
      <circle cx={15} cy={12} r={1.3} />
      <circle cx={15} cy={18} r={1.3} />
    </svg>
  )
}

interface FavoriteRowProps {
  favorite: GeoLocation
  index: number
  isCurrent: boolean
  temp: number | null
  reducedMotion: boolean
  registerRef: (el: HTMLButtonElement | null) => void
  onSelect: () => void
  onRemove: (event: ReactMouseEvent<HTMLButtonElement>) => void
}

// Drag-to-reorder needs its own useDragControls() per row, so each row is
// its own component (a hook can't live in a .map() callback).
function FavoriteRow({
  favorite,
  index,
  isCurrent,
  temp,
  reducedMotion,
  registerRef,
  onSelect,
  onRemove
}: FavoriteRowProps): JSX.Element {
  const dragControls = useDragControls()

  return (
    <Reorder.Item
      as="div"
      value={favorite}
      dragListener={false}
      dragControls={dragControls}
      className={'favorites-item-row' + (isCurrent ? ' is-current' : '')}
      data-nav-index={index}
      style={{ '--row-i': index } as CSSProperties}
      initial={reducedMotion ? false : { opacity: 0, y: -4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={
        reducedMotion
          ? { duration: 0 }
          : {
              // Layout (drag-reorder reflow) gets its own snappy spring —
              // kept separate from the mount stagger below so dropping a
              // dragged row doesn't inherit that stagger's entrance delay.
              layout: { type: 'spring', stiffness: 500, damping: 32 },
              opacity: { duration: 0.26, delay: 0.09 + index * 0.026, ease: [0.16, 1, 0.3, 1] },
              y: { duration: 0.26, delay: 0.09 + index * 0.026, ease: [0.16, 1, 0.3, 1] }
            }
      }
      whileDrag={reducedMotion ? undefined : { scale: 1.035, zIndex: 2, boxShadow: 'var(--glow-strong)' }}
    >
      <span
        className="favorites-drag-handle"
        onPointerDown={(event) => dragControls.start(event)}
        aria-hidden="true"
      >
        <GripIcon />
      </span>
      <button type="button" role="menuitem" className="favorites-item" ref={registerRef} onClick={onSelect}>
        <span className="favorites-item-text">
          <span className="favorites-item-name">{favorite.name}</span>
          <span className="favorites-item-meta">
            {favorite.admin1 ? `${favorite.admin1}, ` : ''}
            {favorite.country}
          </span>
        </span>
        {temp !== null && <span className="favorites-item-temp">{Math.round(temp)}°</span>}
      </button>
      <button
        type="button"
        className="favorites-remove"
        aria-label={`Remove ${favorite.name} from saved locations`}
        onClick={onRemove}
      >
        <CloseIcon />
      </button>
    </Reorder.Item>
  )
}

export function FavoritesMenu(): JSX.Element {
  const favorites = useWeatherStore((s) => s.favorites)
  const location = useWeatherStore((s) => s.location)
  const unit = useWeatherStore((s) => s.unit)
  const theme = useWeatherStore((s) => s.theme)
  const weather = useWeatherStore((s) => s.weather)
  const toggleFavorite = useWeatherStore((s) => s.toggleFavorite)
  const selectLocation = useWeatherStore((s) => s.selectLocation)

  const isRetro = resolveTheme(theme, weather) === 'win95'
  const prefersReducedMotion = useReducedMotion()

  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
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

  const { top: thumbTop, height: thumbHeight, ready: thumbReady } = useListHighlightThumb(
    dropdownRef,
    highlightedIndex
  )

  const currentIsFavorite = location !== null && favorites.some((f) => isSameLocation(f, location))

  // Real drag-to-reorder (Reorder.Group below) writes straight back to the
  // store + localStorage, the same two places toggleFavorite writes to.
  const handleReorder = (next: GeoLocation[]): void => {
    localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(next))
    useWeatherStore.setState({ favorites: next })
  }

  if (isRetro) {
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
            className={
              `favorites-dropdown${open ? '' : ' is-closing'}` +
              (thumbReady ? ' list-thumb-ready' : '')
            }
            role="menu"
            aria-label="Saved locations"
            ref={dropdownRef}
          >
            {favorites.length > 0 && (
              <span
                className="list-nav-thumb"
                style={{ top: `${thumbTop}px`, height: `${thumbHeight}px` }}
                aria-hidden="true"
              />
            )}

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
                  data-nav-index={index}
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

      <AnimatePresence>
        {open && (
          <motion.div
            className={`favorites-dropdown${thumbReady ? ' list-thumb-ready' : ''}`}
            role="menu"
            aria-label="Saved locations"
            ref={dropdownRef}
            layoutScroll
            initial={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.9, y: -8, filter: 'blur(8px)' }}
            animate={{ opacity: 1, scale: 1, y: 0, filter: 'blur(0px)' }}
            exit={
              prefersReducedMotion
                ? { opacity: 0, transition: { duration: 0.1 } }
                : { opacity: 0, scale: 0.92, y: -4, filter: 'blur(6px)', transition: { duration: 0.16 } }
            }
            transition={prefersReducedMotion ? { duration: 0.12 } : { type: 'spring', stiffness: 340, damping: 28 }}
          >
            {favorites.length > 0 && (
              <motion.span
                className="list-nav-thumb"
                animate={{ top: thumbTop, height: thumbHeight }}
                transition={
                  thumbReady && !prefersReducedMotion
                    ? { type: 'spring', stiffness: 420, damping: 34 }
                    : { duration: 0 }
                }
                aria-hidden="true"
              />
            )}

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
              <Reorder.Group as="div" axis="y" className="favorites-list" values={favorites} onReorder={handleReorder}>
                {favorites.map((favorite, index) => (
                  <FavoriteRow
                    key={`${favorite.name}-${favorite.latitude}-${favorite.longitude}`}
                    favorite={favorite}
                    index={index}
                    isCurrent={Boolean(location && isSameLocation(favorite, location))}
                    temp={conditions?.[index] ? celsiusTo(unit, conditions[index]!.temperature) : null}
                    reducedMotion={Boolean(prefersReducedMotion)}
                    registerRef={(el) => {
                      itemRefs.current[index] = el
                    }}
                    onSelect={() => {
                      setOpen(false)
                      void selectLocation(favorite)
                    }}
                    onRemove={(event) => {
                      event.stopPropagation()
                      toggleFavorite(favorite)
                      rootRef.current?.focus()
                    }}
                  />
                ))}
              </Reorder.Group>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
