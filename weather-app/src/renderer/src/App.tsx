import { useEffect, useRef } from 'react'
import { useWeatherStore } from './store/useWeatherStore'
import { SceneManager } from './scene/SceneManager'
import { TitleBar } from './components/TitleBar'
import { SearchBar } from './components/SearchBar'
import { CurrentWeatherCard } from './components/CurrentWeatherCard'
import { HourlyForecast } from './components/HourlyForecast'
import { DailyForecast } from './components/DailyForecast'
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
        <TitleBar />

        <div className="content-grid">
          <div className="top-left">
            <SearchBar />
            <CurrentWeatherCard />
          </div>
          <div className="bottom-bar">
            <HourlyForecast />
            <DailyForecast />
          </div>
        </div>

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
      </div>

      <LoadingOverlay />
    </div>
  )
}
