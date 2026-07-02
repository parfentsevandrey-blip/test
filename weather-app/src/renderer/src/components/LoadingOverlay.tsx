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
        <div className="error-banner glass-panel">
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
      <div className="spinner" />
      <div className="loading-text">
        {status === 'locating' ? 'Finding your location...' : 'Loading weather...'}
      </div>
    </div>
  )
}
