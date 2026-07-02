import './LoadingOverlay.css'
import { useWeatherStore } from '../store/useWeatherStore'

export function LoadingOverlay(): JSX.Element | null {
  const status = useWeatherStore((s) => s.status)
  const error = useWeatherStore((s) => s.error)
  const refresh = useWeatherStore((s) => s.refresh)

  if (status === 'ready') {
    return null
  }

  if (status === 'error') {
    return (
      <div className="loading-overlay">
        <div className="error-banner glass-panel" role="alert">
          <div className="title">Couldn&apos;t load weather</div>
          <div className="message">{error ?? 'Something went wrong. Please try again.'}</div>
          <button type="button" aria-label="Try again" onClick={() => refresh()}>
            Try again
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="loading-overlay">
      <div className="loading-sun" aria-hidden="true">
        <span className="loading-sun-ring ring-1" />
        <span className="loading-sun-ring ring-2" />
        <span className="loading-sun-ring ring-3" />
        <span className="loading-sun-core" />
      </div>
      <div className="loading-text" role="status" aria-live="polite">
        {status === 'locating' ? 'Finding your location...' : 'Loading weather...'}
      </div>
    </div>
  )
}
