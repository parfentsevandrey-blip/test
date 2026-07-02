import { useWeatherStore } from '../store/useWeatherStore'

export function UnitToggle(): JSX.Element {
  const unit = useWeatherStore((s) => s.unit)
  const toggleUnit = useWeatherStore((s) => s.toggleUnit)

  return (
    <div className="control-pill" role="group" aria-label="Temperature units">
      <button
        type="button"
        className={'seg' + (unit === 'celsius' ? ' active' : '')}
        onClick={() => unit !== 'celsius' && toggleUnit()}
        aria-label="Use Celsius"
      >
        °C
      </button>
      <button
        type="button"
        className={'seg' + (unit === 'fahrenheit' ? ' active' : '')}
        onClick={() => unit !== 'fahrenheit' && toggleUnit()}
        aria-label="Use Fahrenheit"
      >
        °F
      </button>
    </div>
  )
}
