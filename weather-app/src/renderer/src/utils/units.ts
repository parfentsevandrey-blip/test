import type { SpeedUnit, TemperatureUnit } from '../types/weather'

export function celsiusTo(unit: TemperatureUnit, celsius: number): number {
  return unit === 'fahrenheit' ? (celsius * 9) / 5 + 32 : celsius
}

export function formatTemperature(celsius: number, unit: TemperatureUnit): string {
  return `${Math.round(celsiusTo(unit, celsius))}°`
}

export function msTo(unit: SpeedUnit, metersPerSecond: number): number {
  return unit === 'mph' ? metersPerSecond * 2.23694 : metersPerSecond * 3.6
}

export function formatSpeed(metersPerSecond: number, unit: SpeedUnit): string {
  return `${Math.round(msTo(unit, metersPerSecond))} ${unit === 'mph' ? 'mph' : 'km/h'}`
}

export function formatPercent(fraction0to100: number): string {
  return `${Math.round(fraction0to100)}%`
}

export function formatHour(isoTime: string): string {
  const date = new Date(isoTime)
  return date.toLocaleTimeString(undefined, { hour: 'numeric' })
}

export function formatWeekday(isoDate: string): string {
  const date = new Date(`${isoDate}T12:00:00`)
  return date.toLocaleDateString(undefined, { weekday: 'short' })
}

export function formatClock(isoTime: string): string {
  const date = new Date(isoTime)
  return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}
