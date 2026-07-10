import { useRef } from 'react'
import { motion } from 'framer-motion'
import { useWeatherStore } from '../store/useWeatherStore'
import { useSegThumb } from '../hooks/useSegThumb'

export function UnitToggle(): JSX.Element {
  const unit = useWeatherStore((s) => s.unit)
  const toggleUnit = useWeatherStore((s) => s.toggleUnit)
  const pillRef = useRef<HTMLDivElement>(null)
  const { left, width, ready } = useSegThumb(pillRef, unit)

  return (
    <div
      className={'control-pill' + (ready ? ' seg-thumb-ready' : '')}
      data-control="unit"
      role="group"
      aria-label="Temperature units"
      ref={pillRef}
    >
      <motion.span
        className="seg-thumb"
        animate={{ left, width }}
        transition={ready ? { type: 'spring', stiffness: 420, damping: 34 } : { duration: 0 }}
        aria-hidden="true"
      />
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
