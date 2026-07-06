import { useEffect, useRef, useState } from 'react'
import { resolveTheme, useWeatherStore } from './store/useWeatherStore'
import { SceneManager } from './scene/SceneManager'
import { SearchBar } from './components/SearchBar'
import { CurrentWeatherCard } from './components/CurrentWeatherCard'
import { HourlyForecast } from './components/HourlyForecast'
import { DailyForecast } from './components/DailyForecast'
import { WindCard } from './components/WindCard'
import { HumidityCard } from './components/HumidityCard'
import { UvIndexCard } from './components/UvIndexCard'
import { VisibilityCard } from './components/VisibilityCard'
import { SunCard } from './components/SunCard'
import { PrecipitationCard } from './components/PrecipitationCard'
import { UnitToggle } from './components/UnitToggle'
import { ThemeToggle } from './components/ThemeToggle'
import { FavoritesMenu } from './components/FavoritesMenu'
import { LoadingOverlay } from './components/LoadingOverlay'
import { Win95StatusBar } from './components/retro/Win95StatusBar'
import { Win95Clippy } from './components/retro/Win95Clippy'
import { Win95BootSplash } from './components/retro/Win95BootSplash'
import { Win95Dialog } from './components/retro/Win95Dialog'
import { celsiusTo, formatTemperature } from './utils/units'
import { getConditionInfo } from './utils/weatherCondition'
import { renderTrayIcon } from './utils/trayIcon'

function RefreshIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 11a8 8 0 1 0-2.34 5.66" />
      <path d="M20 5v6h-6" />
    </svg>
  )
}

/** "just now" / "5 min ago" — refreshed once a minute. */
function useUpdatedAgo(fetchedAt: number | undefined): string {
  const [, forceTick] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => forceTick((n) => n + 1), 60_000)
    return () => clearInterval(timer)
  }, [])

  if (!fetchedAt) return ''
  const minutes = Math.floor((Date.now() - fetchedAt) / 60_000)
  if (minutes < 1) return 'Updated just now'
  if (minutes === 1) return 'Updated 1 min ago'
  return `Updated ${minutes} min ago`
}

/** Not yet in every bundled lib.dom.d.ts; optional so this stays harmless either way. */
type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void | Promise<void>) => {
    ready: Promise<void>
    finished: Promise<void>
    updateCallbackDone: Promise<void>
  }
}

export function App(): JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const sceneRef = useRef<SceneManager | null>(null)

  const init = useWeatherStore((s) => s.init)
  const refresh = useWeatherStore((s) => s.refresh)
  const weather = useWeatherStore((s) => s.weather)
  const status = useWeatherStore((s) => s.status)
  const theme = useWeatherStore((s) => s.theme)
  const unit = useWeatherStore((s) => s.unit)

  const updatedAgo = useUpdatedAgo(weather?.fetchedAt)

  const resolved = resolveTheme(theme, weather)
  const [aboutOpen, setAboutOpen] = useState(false)
  // Boot splash: plays on every power-on of the retro theme, including a
  // persisted win95 preference on app start. keyed so re-entries restart it.
  const [bootVisible, setBootVisible] = useState(() => resolved === 'win95')
  const [bootKey, setBootKey] = useState(0)
  const prevResolvedRef = useRef<string | null>(null)

  useEffect(() => {
    void init()
  }, [init])

  // Apply the resolved theme ('auto' follows day/night at the selected
  // location; 'win95' also switches the 3D scene into retro pixelation).
  // Crossfades through the View Transitions API when available so a theme
  // click reads as one considered gesture instead of a hard color-snap; the
  // very first application (nothing to crossfade from yet) and
  // prefers-reduced-motion both fall straight through to a plain assignment.
  useEffect(() => {
    const prev = prevResolvedRef.current
    const isFirstApplication = prev === null
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const doc = document as ViewTransitionDocument

    const applyTheme = (): void => {
      document.documentElement.dataset.theme = resolved
      sceneRef.current?.setRetro(resolved === 'win95')
    }

    if (isFirstApplication || prefersReducedMotion || typeof doc.startViewTransition !== 'function') {
      applyTheme()
    } else {
      doc.startViewTransition(applyTheme)
    }

    prevResolvedRef.current = resolved
    if (resolved === 'win95' && prev !== null && prev !== 'win95') {
      setBootKey((k) => k + 1)
      setBootVisible(true)
    }
  }, [resolved])

  // Ctrl/Cmd+K focuses the city search, like every commercial app.
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        document.querySelector<HTMLInputElement>('.search-input')?.focus()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  useEffect(() => {
    if (!canvasRef.current) return

    const scene = new SceneManager(canvasRef.current)
    sceneRef.current = scene
    scene.start()

    const handleResize = (): void => scene.resize(window.innerWidth, window.innerHeight)
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      scene.dispose()
      sceneRef.current = null
    }
  }, [])

  useEffect(() => {
    sceneRef.current?.setWeatherData(weather)
  }, [weather])

  // Windows system-tray icon: shows the live temperature as a numeral instead
  // of a static app glyph, so the reading is visible without opening the
  // window. window.api is only present inside the Electron preload context
  // (absent when this bundle is loaded in a plain browser), so this is a
  // silent no-op anywhere else.
  useEffect(() => {
    if (!weather) return
    const rounded = Math.round(celsiusTo(unit, weather.current.temperature))
    const dataUrl = renderTrayIcon(rounded)
    if (!dataUrl) return
    const condition = getConditionInfo(weather.current.weatherCode).label
    const tooltip = `${weather.location.name} — ${formatTemperature(weather.current.temperature, unit)}, ${condition}`
    window.api?.updateTray?.(dataUrl, tooltip)
  }, [weather, unit])

  const isWin95 = resolved === 'win95'
  const busy = status === 'loading' || status === 'locating'

  return (
    <div className="app-shell" data-loading={busy ? 'true' : undefined}>
      <canvas ref={canvasRef} className="scene-canvas" />

      <div className="ui-overlay">
        <header className="app-header">
          <div
            className="header-brand"
            onClick={isWin95 ? () => setAboutOpen(true) : undefined}
            title={isWin95 ? 'About Cinematic Weather 95' : undefined}
          >
            <span className="dot" />
            {isWin95 ? 'Cinematic Weather 95' : 'Cinematic Weather'}
          </div>

          <SearchBar />

          <div className="header-spacer" />

          <div className="header-controls">
            {updatedAgo && <span className="header-status">{updatedAgo}</span>}
            <FavoritesMenu />
            <UnitToggle />
            <ThemeToggle />
            <button
              className={`icon-btn${status === 'loading' ? ' spinning' : ''}`}
              onClick={() => void refresh()}
              aria-label="Refresh weather"
              title="Refresh weather"
            >
              <RefreshIcon />
            </button>
          </div>
        </header>

        <div className="bento-viewport">
          <div className="bento-grid">
            <CurrentWeatherCard />
            <HourlyForecast />
            <WindCard />
            <HumidityCard />
            <UvIndexCard />
            <VisibilityCard />
            <DailyForecast />
            <SunCard />
            <PrecipitationCard />
          </div>
        </div>

        {isWin95 && <Win95StatusBar />}
        {isWin95 && <Win95Clippy />}
      </div>

      <LoadingOverlay />

      {isWin95 && bootVisible && (
        <Win95BootSplash key={bootKey} onDone={() => setBootVisible(false)} />
      )}

      {aboutOpen && (
        <Win95Dialog title="About Cinematic Weather 95" icon="info" onClose={() => setAboutOpen(false)}>
          <div className="w95-about-rows">
            <strong>Cinematic Weather 95</strong>
            <span>Version 4.00.950 B</span>
            <span>Copyright © 1995–2026 Cinematic Softworks</span>
            <div className="w95-about-sep" />
            <span>Memory: 640K (that ought to be enough for anybody)</span>
            <span>Weather data: open-meteo.com, via 56k modem</span>
          </div>
        </Win95Dialog>
      )}
    </div>
  )
}
