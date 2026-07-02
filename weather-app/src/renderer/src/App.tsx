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

export function App(): JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const sceneRef = useRef<SceneManager | null>(null)

  const init = useWeatherStore((s) => s.init)
  const refresh = useWeatherStore((s) => s.refresh)
  const weather = useWeatherStore((s) => s.weather)
  const status = useWeatherStore((s) => s.status)
  const theme = useWeatherStore((s) => s.theme)

  const updatedAgo = useUpdatedAgo(weather?.fetchedAt)

  useEffect(() => {
    void init()
  }, [init])

  // Apply the resolved theme ('auto' follows day/night at the selected
  // location; 'win95' also switches the 3D scene into retro pixelation).
  useEffect(() => {
    const resolved = resolveTheme(theme, weather)
    document.documentElement.dataset.theme = resolved
    sceneRef.current?.setRetro(resolved === 'win95')
  }, [theme, weather])

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

  return (
    <div className="app-shell">
      <canvas ref={canvasRef} className="scene-canvas" />

      <div className="ui-overlay">
        <header className="app-header">
          <div className="header-brand">
            <span className="dot" />
            Cinematic Weather
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
      </div>

      <LoadingOverlay />
    </div>
  )
}
