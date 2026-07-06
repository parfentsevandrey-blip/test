import { useEffect, useState } from 'react'
import { useWeatherStore } from '../../store/useWeatherStore'
import { getConditionInfo } from '../../utils/weatherCondition'
import { formatSpeed, formatTemperature, speedUnitFor } from '../../utils/units'
import './retro.css'

/** City-local wall clock, ticking every second, rendered like a tray clock. */
function useCityClock(utcOffsetSeconds: number | undefined): string {
  const [clock, setClock] = useState('')

  useEffect(() => {
    if (utcOffsetSeconds === undefined) {
      setClock('')
      return
    }
    const tick = (): void => {
      const cityNow = new Date(Date.now() + utcOffsetSeconds * 1000)
      setClock(
        cityNow.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' })
      )
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [utcOffsetSeconds])

  return clock
}

/**
 * The classic application status bar: sunken "Ready" panel, a scrolling
 * news-ticker of weather headlines, a proud 56k connection indicator,
 * a tray clock, and the obligatory resize grip that resizes nothing.
 */
export function Win95StatusBar(): JSX.Element {
  const weather = useWeatherStore((s) => s.weather)
  const status = useWeatherStore((s) => s.status)
  const unit = useWeatherStore((s) => s.unit)

  const clock = useCityClock(weather?.utcOffsetSeconds)
  const busy = status === 'loading' || status === 'locating'

  const headlines: string[] = ['CINEMATIC WEATHER 95']
  if (weather) {
    const condition = getConditionInfo(weather.current.weatherCode)
    const today = weather.daily[0]
    headlines.push(
      `${weather.location.name.toUpperCase()}: ${formatTemperature(weather.current.temperature, unit)} ${condition.label.toUpperCase()}`,
      `HI ${formatTemperature(today.tempMax, unit)} / LO ${formatTemperature(today.tempMin, unit)}`,
      `HUMIDITY ${Math.round(weather.current.humidity)}%`,
      `WIND ${formatSpeed(weather.current.windSpeed, speedUnitFor(unit)).toUpperCase()}`
    )
  }
  headlines.push(
    'DOWNLOADED VIA 56K MODEM — PLEASE DO NOT PICK UP THE PHONE',
    'BEST VIEWED AT 800X600 IN 256 COLORS',
    "DON'T FORGET TO SAVE YOUR WORK"
  )
  const ticker = `${headlines.join('  ***  ')}  ***  `

  return (
    <div className="w95-statusbar" aria-label="Status bar">
      {/* Only the ready/busy transition is worth announcing — the outer bar
          used to carry role="status" too, which turned the once-a-second
          clock tick into a screen-reader live-region re-announcement. */}
      <div className="w95-panel w95-ready" role="status">
        {busy ? 'Working...' : 'Ready'}
      </div>

      <div className="w95-panel w95-ticker" aria-hidden="true">
        <div className="w95-ticker-inner">
          <span>{ticker}</span>
          <span>{ticker}</span>
        </div>
      </div>

      <div className="w95-panel w95-modem">
        <span className={`w95-modem-light${busy ? ' is-busy' : ''}`} />
        {busy ? 'Dialing...' : 'Connected at 56.0 Kbps'}
      </div>

      {clock && <div className="w95-panel w95-clock">{clock}</div>}

      <div className="w95-grip" aria-hidden="true" />
    </div>
  )
}
