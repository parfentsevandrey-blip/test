import { useWeatherStore } from '../store/useWeatherStore'

export function UnitToggle(): JSX.Element {
  const unit = useWeatherStore((s) => s.unit)
  const toggleUnit = useWeatherStore((s) => s.toggleUnit)

  return (
    <div className="unit-toggle glass-panel">
      <button
        type="button"
        className={'unit-toggle-btn' + (unit === 'celsius' ? ' active' : '')}
        onClick={() => unit !== 'celsius' && toggleUnit()}
        aria-label="Use Celsius"
      >
        °C
      </button>
      <button
        type="button"
        className={'unit-toggle-btn' + (unit === 'fahrenheit' ? ' active' : '')}
        onClick={() => unit !== 'fahrenheit' && toggleUnit()}
        aria-label="Use Fahrenheit"
      >
        °F
      </button>
    </div>
  )
}
