import { create } from 'zustand'
import type { GeoLocation, TemperatureUnit, WeatherData } from '../types/weather'
import { fetchWeatherData } from '../api/openMeteo'
import { FALLBACK_LOCATION, getBrowserLocation, searchLocations } from '../api/geocoding'

export type WeatherStatus = 'idle' | 'locating' | 'loading' | 'ready' | 'error'
export type ThemePreference = 'light' | 'dark' | 'auto'

const UNIT_STORAGE_KEY = 'cinematic-weather:unit'
const LOCATION_STORAGE_KEY = 'cinematic-weather:last-location'
const THEME_STORAGE_KEY = 'cinematic-weather:theme'
const FAVORITES_STORAGE_KEY = 'cinematic-weather:favorites'
const REFRESH_INTERVAL_MS = 10 * 60 * 1000

function loadStoredUnit(): TemperatureUnit {
  const stored = localStorage.getItem(UNIT_STORAGE_KEY)
  return stored === 'fahrenheit' ? 'fahrenheit' : 'celsius'
}

function loadStoredLocation(): GeoLocation | null {
  const stored = localStorage.getItem(LOCATION_STORAGE_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored) as GeoLocation
  } catch {
    return null
  }
}

function loadStoredTheme(): ThemePreference {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  return stored === 'dark' || stored === 'auto' ? stored : 'light'
}

function loadStoredFavorites(): GeoLocation[] {
  const stored = localStorage.getItem(FAVORITES_STORAGE_KEY)
  if (!stored) return []
  try {
    const parsed = JSON.parse(stored) as GeoLocation[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** Two locations are "the same place" when their coordinates match to ~100m. */
export function isSameLocation(a: GeoLocation, b: GeoLocation): boolean {
  return Math.abs(a.latitude - b.latitude) < 0.001 && Math.abs(a.longitude - b.longitude) < 0.001
}

/**
 * The theme that's actually applied to the document: 'auto' resolves to
 * light while the selected location is in daylight and dark at night.
 */
export function resolveTheme(preference: ThemePreference, weather: WeatherData | null): 'light' | 'dark' {
  if (preference !== 'auto') return preference
  if (!weather) return 'light'
  return weather.current.isDay ? 'light' : 'dark'
}

interface WeatherStoreState {
  location: GeoLocation | null
  weather: WeatherData | null
  unit: TemperatureUnit
  theme: ThemePreference
  favorites: GeoLocation[]
  status: WeatherStatus
  error: string | null
  searchQuery: string
  searchResults: GeoLocation[]
  isSearching: boolean

  /** Called once on app start: restores the last location, otherwise tries geolocation, otherwise falls back to a default city. */
  init: () => Promise<void>
  selectLocation: (location: GeoLocation) => Promise<void>
  search: (query: string) => Promise<void>
  clearSearch: () => void
  toggleUnit: () => void
  setTheme: (theme: ThemePreference) => void
  /** Adds the location to favorites, or removes it if already present. */
  toggleFavorite: (location: GeoLocation) => void
  refresh: () => Promise<void>
}

let searchAbortController: AbortController | null = null
let refreshTimer: ReturnType<typeof setInterval> | null = null

export const useWeatherStore = create<WeatherStoreState>((set, get) => ({
  location: null,
  weather: null,
  unit: loadStoredUnit(),
  theme: loadStoredTheme(),
  favorites: loadStoredFavorites(),
  status: 'idle',
  error: null,
  searchQuery: '',
  searchResults: [],
  isSearching: false,

  init: async () => {
    const stored = loadStoredLocation()
    if (stored) {
      await get().selectLocation(stored)
      return
    }

    set({ status: 'locating' })
    try {
      const location = await getBrowserLocation()
      await get().selectLocation(location)
    } catch {
      await get().selectLocation(FALLBACK_LOCATION)
    }
  },

  selectLocation: async (location: GeoLocation) => {
    set({ status: 'loading', error: null, location, searchResults: [], searchQuery: '' })
    try {
      const weather = await fetchWeatherData(location)
      set({ weather, status: 'ready' })
      localStorage.setItem(LOCATION_STORAGE_KEY, JSON.stringify(location))

      if (refreshTimer) clearInterval(refreshTimer)
      refreshTimer = setInterval(() => {
        get().refresh()
      }, REFRESH_INTERVAL_MS)
    } catch (err) {
      set({ status: 'error', error: err instanceof Error ? err.message : 'Failed to load weather' })
    }
  },

  refresh: async () => {
    const { location } = get()
    if (!location) return
    try {
      const weather = await fetchWeatherData(location)
      set({ weather, status: 'ready', error: null })
    } catch (err) {
      set({ error: err instanceof Error ? err.message : 'Failed to refresh weather' })
    }
  },

  search: async (query: string) => {
    set({ searchQuery: query })
    if (query.trim().length < 2) {
      set({ searchResults: [], isSearching: false })
      return
    }

    searchAbortController?.abort()
    searchAbortController = new AbortController()
    set({ isSearching: true })
    try {
      const results = await searchLocations(query, searchAbortController.signal)
      set({ searchResults: results, isSearching: false })
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      set({ isSearching: false })
    }
  },

  clearSearch: () => {
    searchAbortController?.abort()
    set({ searchQuery: '', searchResults: [], isSearching: false })
  },

  toggleUnit: () => {
    const next: TemperatureUnit = get().unit === 'celsius' ? 'fahrenheit' : 'celsius'
    localStorage.setItem(UNIT_STORAGE_KEY, next)
    set({ unit: next })
  },

  setTheme: (theme: ThemePreference) => {
    localStorage.setItem(THEME_STORAGE_KEY, theme)
    set({ theme })
  },

  toggleFavorite: (location: GeoLocation) => {
    const { favorites } = get()
    const next = favorites.some((f) => isSameLocation(f, location))
      ? favorites.filter((f) => !isSameLocation(f, location))
      : [...favorites, location]
    localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(next))
    set({ favorites: next })
  }
}))
