import { create } from 'zustand'
import type { GeoLocation, TemperatureUnit, WeatherData } from '../types/weather'
import { fetchWeatherData } from '../api/openMeteo'
import { FALLBACK_LOCATION, getBrowserLocation, searchLocations } from '../api/geocoding'

export type WeatherStatus = 'idle' | 'locating' | 'loading' | 'ready' | 'error'
export type ThemePreference = 'light' | 'dark' | 'auto' | 'win95' | 'tuscany' | 'skeuo'
export type ResolvedTheme = 'light' | 'dark' | 'win95' | 'tuscany' | 'skeuo'

const UNIT_STORAGE_KEY = 'cinematic-weather:unit'
const LOCATION_STORAGE_KEY = 'cinematic-weather:last-location'
const THEME_STORAGE_KEY = 'cinematic-weather:theme'
const FAVORITES_STORAGE_KEY = 'cinematic-weather:favorites'
const REFRESH_INTERVAL_STORAGE_KEY = 'cinematic-weather:refresh-interval-minutes'
const RAIN_ALERTS_STORAGE_KEY = 'cinematic-weather:rain-alerts'

const DEFAULT_REFRESH_MINUTES = 10
/** The only intervals the settings panel offers — anything else in storage falls back to the default. */
export const REFRESH_INTERVAL_OPTIONS_MIN = [5, 10, 15, 30, 60] as const

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
  return stored === 'dark' ||
    stored === 'auto' ||
    stored === 'win95' ||
    stored === 'tuscany' ||
    stored === 'skeuo'
    ? stored
    : 'light'
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

function loadStoredRefreshMinutes(): number {
  const stored = Number(localStorage.getItem(REFRESH_INTERVAL_STORAGE_KEY))
  return REFRESH_INTERVAL_OPTIONS_MIN.includes(stored as (typeof REFRESH_INTERVAL_OPTIONS_MIN)[number])
    ? stored
    : DEFAULT_REFRESH_MINUTES
}

function loadStoredRainAlertsEnabled(): boolean {
  return localStorage.getItem(RAIN_ALERTS_STORAGE_KEY) === 'true'
}

/** Two locations are "the same place" when their coordinates match to ~100m. */
export function isSameLocation(a: GeoLocation, b: GeoLocation): boolean {
  return Math.abs(a.latitude - b.latitude) < 0.001 && Math.abs(a.longitude - b.longitude) < 0.001
}

/**
 * The theme that's actually applied to the document: 'auto' resolves to
 * light while the selected location is in daylight and dark at night;
 * 'win95' is its own fully-styled retro mode.
 */
export function resolveTheme(preference: ThemePreference, weather: WeatherData | null): ResolvedTheme {
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
  /** True only for the duration of a background refresh() call — independent of `status`, which
      only reflects the initial-load/city-switch lifecycle. Drives the header refresh button's spin. */
  isRefreshing: boolean
  searchQuery: string
  searchResults: GeoLocation[]
  isSearching: boolean
  /** Set when search() itself failed (network/geocoding outage) — distinct from a genuine zero-result search. */
  searchError: string | null

  /** Minutes between background refreshes; one of REFRESH_INTERVAL_OPTIONS_MIN. */
  refreshIntervalMinutes: number
  /** Desktop notification when rain looks imminent (see hooks/useRainAlerts.ts). */
  rainAlertsEnabled: boolean
  /** Whether the app is registered to launch when Windows starts. Only meaningful (and only ever true) inside the packaged Electron app. */
  launchAtLoginEnabled: boolean
  /** False in a plain browser tab (no window.api) — the settings panel hides the launch-at-login control in that case. */
  launchAtLoginSupported: boolean

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
  setRefreshIntervalMinutes: (minutes: number) => void
  setRainAlertsEnabled: (enabled: boolean) => void
  setLaunchAtLogin: (enabled: boolean) => void
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
  isRefreshing: false,
  searchQuery: '',
  searchResults: [],
  isSearching: false,
  searchError: null,
  refreshIntervalMinutes: loadStoredRefreshMinutes(),
  rainAlertsEnabled: loadStoredRainAlertsEnabled(),
  launchAtLoginEnabled: false,
  launchAtLoginSupported: typeof window.api?.getLaunchAtLogin === 'function',

  init: async () => {
    if (get().launchAtLoginSupported) {
      window.api
        .getLaunchAtLogin()
        .then((enabled) => set({ launchAtLoginEnabled: enabled }))
        .catch(() => undefined)
    }

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
    set({ status: 'loading', error: null, location, searchResults: [], searchQuery: '', searchError: null })
    try {
      const weather = await fetchWeatherData(location)
      set({ weather, status: 'ready' })
      localStorage.setItem(LOCATION_STORAGE_KEY, JSON.stringify(location))

      if (refreshTimer) clearInterval(refreshTimer)
      refreshTimer = setInterval(() => {
        get().refresh()
      }, get().refreshIntervalMinutes * 60_000)
    } catch (err) {
      set({ status: 'error', error: err instanceof Error ? err.message : 'Failed to load weather' })
    }
  },

  refresh: async () => {
    const { location } = get()
    if (!location) return
    set({ isRefreshing: true })
    try {
      const weather = await fetchWeatherData(location)
      set({ weather, status: 'ready', error: null })
    } catch (err) {
      set({ error: err instanceof Error ? err.message : 'Failed to refresh weather' })
    } finally {
      set({ isRefreshing: false })
    }
  },

  search: async (query: string) => {
    set({ searchQuery: query, searchError: null })
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
      set({
        isSearching: false,
        searchResults: [],
        searchError: err instanceof Error ? err.message : 'Search unavailable'
      })
    }
  },

  clearSearch: () => {
    searchAbortController?.abort()
    set({ searchQuery: '', searchResults: [], isSearching: false, searchError: null })
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
  },

  setRefreshIntervalMinutes: (minutes: number) => {
    if (!REFRESH_INTERVAL_OPTIONS_MIN.includes(minutes as (typeof REFRESH_INTERVAL_OPTIONS_MIN)[number])) return
    localStorage.setItem(REFRESH_INTERVAL_STORAGE_KEY, String(minutes))
    set({ refreshIntervalMinutes: minutes })

    // Re-arm immediately on the new cadence rather than waiting out whatever
    // was left of the old interval.
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = setInterval(() => {
        get().refresh()
      }, minutes * 60_000)
    }
  },

  setRainAlertsEnabled: (enabled: boolean) => {
    localStorage.setItem(RAIN_ALERTS_STORAGE_KEY, String(enabled))
    set({ rainAlertsEnabled: enabled })
  },

  setLaunchAtLogin: (enabled: boolean) => {
    if (!get().launchAtLoginSupported) return
    // Optimistic: the main process call is fire-and-forget from here, and
    // reflects reality closely enough that a round-trip isn't worth the delay.
    set({ launchAtLoginEnabled: enabled })
    window.api.setLaunchAtLogin(enabled)
  }
}))
