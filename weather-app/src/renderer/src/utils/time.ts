import { clamp01 } from './math'

export interface SunPosition {
  /** sin(elevation). 1 = overhead, 0 = on the horizon, -1 = directly below. */
  altitude: number
  /** 0-2π decorative sweep angle used to place the sun/moon in the sky dome. */
  azimuthRad: number
  isDay: boolean
}

/**
 * Derives a smooth, continuous sun position from real sunrise/sunset times.
 * Not astronomically precise (it does not compute true solar azimuth), but
 * guarantees altitude = 0 exactly at sunrise/sunset, +1 at the day's
 * midpoint and -1 at the night's midpoint — which is what the sky/lighting
 * need to look right against real daylight hours anywhere on Earth.
 */
export function computeSunPosition(
  now: Date,
  sunriseToday: Date,
  sunsetToday: Date,
  sunsetYesterday: Date,
  sunriseTomorrow: Date
): SunPosition {
  const t = now.getTime()
  let phase: number

  if (t >= sunriseToday.getTime() && t <= sunsetToday.getTime()) {
    const dayFrac = clamp01(
      (t - sunriseToday.getTime()) / (sunsetToday.getTime() - sunriseToday.getTime())
    )
    phase = dayFrac * 0.5
  } else if (t > sunsetToday.getTime()) {
    const nightFrac = clamp01(
      (t - sunsetToday.getTime()) / (sunriseTomorrow.getTime() - sunsetToday.getTime())
    )
    phase = 0.5 + nightFrac * 0.5
  } else {
    const nightFrac = clamp01(
      (t - sunsetYesterday.getTime()) / (sunriseToday.getTime() - sunsetYesterday.getTime())
    )
    phase = 0.5 + nightFrac * 0.5
    if (phase >= 1) phase -= 1
  }

  const altitude = Math.sin(phase * Math.PI * 2)
  const azimuthRad = phase * Math.PI * 2
  return { altitude, azimuthRad, isDay: altitude > 0 }
}

/** Cheap wall-clock fraction of the day elapsed, 0 (midnight) - 1 (next midnight). */
export function getTimeOfDayFrac(now: Date): number {
  return (now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60) / 1440
}
