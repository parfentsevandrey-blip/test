import { useEffect, useMemo, useState } from 'react'
import { useWeatherStore } from '../../store/useWeatherStore'
import { getConditionInfo } from '../../utils/weatherCondition'
import type { WeatherData } from '../../types/weather'
import { playAssistantPop } from '../../utils/retroSounds'
import './retro.css'

/** How long the paperclip politely waits before offering unsolicited help. */
const APPEAR_DELAY_MS = 3500
/** The bubble sits over the last card's chart area, so it auto-hides instead
    of permanently covering that data — still reopenable via the clip. */
const AUTO_DISMISS_MS = 7000

const GENERIC_TIPS = [
  'It looks like you are checking the weather. Would you like some help with that?',
  'This forecast downloaded faster than one song did in 1995. Progress!',
  'Tip: press Ctrl+K to search for a city. No mouse driver required.',
  'Remember to save your work before going outside.',
  'I used to help with documents. Now I do weather. Life finds a way.',
  'Fun fact: this app uses more memory than my entire office suite did.'
]

/** Weather-aware advice first, timeless 90s wisdom after. */
function buildTips(weather: WeatherData | null): string[] {
  if (!weather) return GENERIC_TIPS

  const tips: string[] = []
  const { condition } = getConditionInfo(weather.current.weatherCode)

  if (condition === 'rain' || condition === 'drizzle' || condition === 'thunderstorm') {
    tips.push('It looks like it is going to rain. Would you like help finding an umbrella?')
  }
  if (condition === 'thunderstorm') {
    tips.push('Thunderstorm detected. Please unplug your modem — mom said so.')
  }
  if (condition === 'snow') {
    tips.push('Snow expected. I recommend defragmenting your driveway.')
  }
  if (condition === 'fog') {
    tips.push('Foggy out there. Visibility is worse than a CRT at the wrong refresh rate.')
  }
  if (condition === 'clear' && weather.current.isDay) {
    tips.push('It looks like a beautiful day! Consider going outside. (Save your work first.)')
  }
  if (!weather.current.isDay && condition === 'clear') {
    tips.push('Clear night sky. Perfect weather for a 2 AM chat room session.')
  }
  if (weather.current.windSpeed > 8) {
    tips.push('It is windy today. Hold on to your floppy disks.')
  }
  if ((weather.current.uvIndex ?? 0) >= 6) {
    tips.push('High UV index. Apply SUNSCREEN.EXE before going out.')
  }
  if (weather.current.temperature >= 28) {
    tips.push('It is hot today. Keep your CPU and yourself adequately cooled.')
  }
  if (weather.current.temperature <= -5) {
    tips.push('Freezing outside! Even my wire feels stiff. Wear layers.')
  }

  return [...tips, ...GENERIC_TIPS]
}

/**
 * A certain helpful paperclip, reborn as a weather assistant. Lives in the
 * bottom-right corner, blinks, bobs, and dispenses era-accurate advice.
 * Click the clip to cycle tips; the bubble's × just hides the bubble.
 */
export function Win95Clippy(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const [visible, setVisible] = useState(false)
  const [bubbleOpen, setBubbleOpen] = useState(true)
  const [tipIndex, setTipIndex] = useState(0)

  const tips = useMemo(() => buildTips(weather), [weather])

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), APPEAR_DELAY_MS)
    return () => clearTimeout(timer)
  }, [])

  useEffect(() => {
    // Gated on `visible` too — otherwise this timer (like the appear-delay
    // one above) starts counting from mount, so the bubble could dismiss
    // itself only a moment after actually appearing on screen.
    if (!visible || !bubbleOpen) return undefined
    const timer = setTimeout(() => setBubbleOpen(false), AUTO_DISMISS_MS)
    return () => clearTimeout(timer)
  }, [visible, bubbleOpen, tipIndex])

  if (!visible) return null

  const handleClipClick = (): void => {
    playAssistantPop()
    if (!bubbleOpen) {
      setBubbleOpen(true)
    } else {
      setTipIndex((i) => (i + 1) % tips.length)
    }
  }

  return (
    <div className="w95-clippy">
      {bubbleOpen && (
        <div className="w95-clippy-bubble" role="status">
          <button
            type="button"
            className="w95-bubble-close"
            aria-label="Hide assistant tip"
            onClick={(event) => {
              event.stopPropagation()
              setBubbleOpen(false)
            }}
          >
            ×
          </button>
          {tips[tipIndex % tips.length]}
        </div>
      )}

      <button
        type="button"
        className="w95-clippy-body"
        aria-label="Office assistant: next tip"
        onClick={handleClipClick}
      >
        <svg className="w95-clippy-svg" viewBox="0 0 64 88" aria-hidden="true">
          {/* Wire: outer loop down, around, and back up the middle. */}
          <path
            d="M22 32 v32 a11 11 0 0 0 22 0 V24 a8 8 0 0 0 -16 0 v36 a3.5 3.5 0 0 0 7 0 V32"
            fill="none"
            stroke="#8f959e"
            strokeWidth="5"
            strokeLinecap="round"
          />
          <path
            d="M22 32 v32 a11 11 0 0 0 22 0 V24 a8 8 0 0 0 -16 0 v36 a3.5 3.5 0 0 0 7 0 V32"
            fill="none"
            stroke="#d7dbe0"
            strokeWidth="1.6"
            strokeLinecap="round"
          />
          <g className="w95-clippy-eyes">
            <ellipse cx="24" cy="12" rx="7.5" ry="9.5" fill="#ffffff" stroke="#000000" strokeWidth="1.5" />
            <ellipse cx="41" cy="12" rx="7.5" ry="9.5" fill="#ffffff" stroke="#000000" strokeWidth="1.5" />
            <circle cx="26" cy="14" r="2.6" fill="#000000" />
            <circle cx="43" cy="14" r="2.6" fill="#000000" />
          </g>
        </svg>
      </button>
    </div>
  )
}
