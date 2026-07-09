import { useEffect, useRef } from 'react'
import { useWeatherStore } from '../store/useWeatherStore'
import { getConditionInfo } from '../utils/weatherCondition'
import { toAbsoluteInstant } from '../utils/time'
import { formatHour } from '../utils/units'

/** How far ahead to look for an incoming rain/snow window. */
const LOOKAHEAD_MS = 90 * 60 * 1000
/** Below this, a "likely" chance of precipitation isn't worth interrupting the user for. */
const PROBABILITY_THRESHOLD = 60
const WET_CONDITIONS = new Set(['rain', 'drizzle', 'snow', 'thunderstorm'])

/**
 * Fires one desktop notification per distinct upcoming wet window — using
 * hourly data the app already fetches, no extra API calls. Re-checked on
 * every refresh, but only actually notifies once per detected window (keyed
 * by that hour's timestamp) so a 10-minute refresh cadence doesn't repeat
 * the same alert, and resets the moment the selected city changes.
 */
export function useRainAlerts(): void {
  const weather = useWeatherStore((s) => s.weather)
  const rainAlertsEnabled = useWeatherStore((s) => s.rainAlertsEnabled)
  const lastNotifiedTimeRef = useRef<string | null>(null)
  const lastLocationKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!weather) return

    const locationKey = `${weather.location.latitude},${weather.location.longitude}`
    if (lastLocationKeyRef.current !== locationKey) {
      lastLocationKeyRef.current = locationKey
      lastNotifiedTimeRef.current = null
    }

    if (!rainAlertsEnabled) return
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return

    const nowMs = Date.now()
    const upcoming = weather.hourly.find((h) => {
      const t = toAbsoluteInstant(h.time, weather.utcOffsetSeconds).getTime()
      if (t <= nowMs || t > nowMs + LOOKAHEAD_MS) return false
      if (h.precipitationProbability < PROBABILITY_THRESHOLD) return false
      return WET_CONDITIONS.has(getConditionInfo(h.weatherCode).condition)
    })

    if (!upcoming || lastNotifiedTimeRef.current === upcoming.time) return
    lastNotifiedTimeRef.current = upcoming.time

    const word = getConditionInfo(upcoming.weatherCode).condition === 'snow' ? 'Snow' : 'Rain'
    new Notification(`${word} expected soon`, {
      body: `${Math.round(upcoming.precipitationProbability)}% chance around ${formatHour(upcoming.time)} in ${weather.location.name}`
    })
  }, [weather, rainAlertsEnabled])
}
