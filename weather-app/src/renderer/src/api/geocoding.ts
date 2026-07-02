import type { GeoLocation } from '../types/weather'

const GEOCODING_URL = 'https://geocoding-api.open-meteo.com/v1/search'

interface RawGeocodingResult {
  name: string
  latitude: number
  longitude: number
  country?: string
  admin1?: string
  timezone: string
}

/** Free, open, no-API-key city search powered by Open-Meteo's geocoding service. */
export async function searchLocations(query: string, signal?: AbortSignal): Promise<GeoLocation[]> {
  const trimmed = query.trim()
  if (trimmed.length < 2) return []

  const url = new URL(GEOCODING_URL)
  url.searchParams.set('name', trimmed)
  url.searchParams.set('count', '8')
  url.searchParams.set('language', 'en')
  url.searchParams.set('format', 'json')

  const response = await fetch(url, { signal })
  if (!response.ok) {
    throw new Error(`Geocoding request failed (${response.status})`)
  }
  const data = (await response.json()) as { results?: RawGeocodingResult[] }

  return (data.results ?? []).map((result) => ({
    name: result.name,
    country: result.country ?? '',
    admin1: result.admin1,
    latitude: result.latitude,
    longitude: result.longitude,
    timezone: result.timezone
  }))
}

/** Resolves the browser's geolocation coordinates into a usable GeoLocation, unnamed. */
export function getBrowserLocation(): Promise<GeoLocation> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('Geolocation is not available'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          name: 'My Location',
          country: '',
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
        })
      },
      (error) => reject(error),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 10 * 60 * 1000 }
    )
  })
}

export const FALLBACK_LOCATION: GeoLocation = {
  name: 'New York',
  country: 'United States',
  admin1: 'New York',
  latitude: 40.7128,
  longitude: -74.006,
  timezone: 'America/New_York'
}
