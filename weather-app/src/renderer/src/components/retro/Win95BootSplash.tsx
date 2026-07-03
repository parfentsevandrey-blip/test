import { useEffect, useMemo, useRef, useState } from 'react'
import { playStartupChime } from '../../utils/retroSounds'
import './retro.css'

const PIXEL = 9

/** Sun-behind-cloud pixel art, one char per pixel (see PALETTE). */
const ART = [
  '.............yyyy...',
  '............yyyyyy..',
  '............yyyyyy..',
  '....wwww....yyyyyy..',
  '..wwwwwwww...yyyy...',
  '.wwwwwwwwwwwww......',
  'wwwwwwwwwwwwwwww....',
  'wwwwwwwwwwwwwwwww...',
  'wWWWWWWWWWWWWWWWw...',
  '.WWWWWWWWWWWWWWW....',
  '...b....b....b......',
  '..b....b....b.......'
]

const PALETTE: Record<string, string> = {
  y: '#ffd800',
  w: '#ffffff',
  W: '#b8bcc4',
  b: '#3070f0'
}

function buildPixelShadow(): string {
  const shadows: string[] = []
  ART.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      const color = PALETTE[row[x]]
      if (color) shadows.push(`${x * PIXEL}px ${y * PIXEL}px 0 ${color}`)
    }
  })
  return shadows.join(', ')
}

interface Win95BootSplashProps {
  onDone: () => void
}

/**
 * Full-screen "Starting Cinematic Weather 95..." boot sequence, shown when
 * the retro theme powers on: black screen, pixel-art logo, blinking DOS
 * cursor, the endless blue stripe crawling along the bottom — and the
 * startup chime right before the desktop appears.
 */
export function Win95BootSplash({ onDone }: Win95BootSplashProps): JSX.Element {
  const [fading, setFading] = useState(false)
  const pixelShadow = useMemo(buildPixelShadow, [])

  // Keep the latest onDone without retriggering the timers: parent re-renders
  // (weather arriving, status flips) must not extend the boot sequence.
  const onDoneRef = useRef(onDone)
  onDoneRef.current = onDone

  useEffect(() => {
    const chime = setTimeout(playStartupChime, 1200)
    const fade = setTimeout(() => setFading(true), 1900)
    const done = setTimeout(() => onDoneRef.current(), 2300)
    return () => {
      clearTimeout(chime)
      clearTimeout(fade)
      clearTimeout(done)
    }
  }, [])

  return (
    <div className={`w95-boot${fading ? ' is-done' : ''}`} aria-hidden="true">
      <div
        className="w95-boot-art"
        style={{
          width: ART[0].length * PIXEL,
          height: ART.length * PIXEL
        }}
      >
        <span style={{ boxShadow: pixelShadow, width: PIXEL, height: PIXEL }} />
      </div>

      <div className="w95-boot-title">
        Cinematic Weather<span className="w95-boot-ver">95</span>
      </div>

      <div className="w95-boot-sub">
        Starting Cinematic Weather 95...<span className="w95-boot-cursor">▌</span>
      </div>

      <div className="w95-boot-bar" />
    </div>
  )
}
