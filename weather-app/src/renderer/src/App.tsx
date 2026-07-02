import { useEffect, useRef } from 'react'
import { useWeatherStore } from './store/useWeatherStore'
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
import { LoadingOverlay } from './components/LoadingOverlay'

function RefreshIcon(): JSX.Element {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 11a8 8 0 1 0-2.34 5.66" />
      <path d="M20 5v6h-6" />
    </svg>
  )
}

export function App(): JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const sceneRef = useRef<SceneManager | null>(null)

  const init = useWeatherStore((s) => s.init)
  const refresh = useWeatherStore((s) => s.refresh)
  const weather = useWeatherStore((s) => s.weather)
  const status = useWeatherStore((s) => s.status)

  useEffect(() => {
    void init()
  }, [init])

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
        <SearchBar />

        <div className="controls-row">
          <UnitToggle />
          <button
            className={`icon-btn${status === 'loading' ? ' spinning' : ''}`}
            onClick={() => void refresh()}
            aria-label="Refresh weather"
          >
            <RefreshIcon />
          </button>
        </div>

        <div className="bento-scroll">
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
