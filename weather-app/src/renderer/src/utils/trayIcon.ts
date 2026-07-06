/** Source canvas size in px; the OS scales this down to whatever the actual
 *  tray slot needs (16-32px depending on display scaling), so a moderately
 *  large source keeps the numerals crisp rather than blurry. */
const CANVAS_SIZE = 64

/**
 * Renders the rounded integer temperature as a small flat badge and returns
 * it as a PNG data URL for use as the Windows tray icon. A solid
 * high-contrast badge — rather than a transparent glyph — is deliberate: it
 * reads clearly whether the taskbar is set to a light or dark system theme.
 *
 * When `isWin95` is set, the badge switches to a square (0-radius) navy tile
 * in a period system font instead of the modern rounded Segoe-UI badge —
 * every other win95-scoped surface zeroes radii and swaps fonts, and the
 * tray sits in the taskbar the whole time that theme is active.
 */
export function renderTrayIcon(roundedTemperature: number, isWin95 = false): string | null {
  const canvas = document.createElement('canvas')
  canvas.width = CANVAS_SIZE
  canvas.height = CANVAS_SIZE
  const ctx = canvas.getContext('2d')
  if (!ctx) return null

  const text = String(roundedTemperature)

  ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE)
  ctx.fillStyle = isWin95 ? '#000080' : '#14203a'
  ctx.beginPath()
  if (isWin95) {
    ctx.rect(1, 1, CANVAS_SIZE - 2, CANVAS_SIZE - 2)
  } else {
    ctx.roundRect(1, 1, CANVAS_SIZE - 2, CANVAS_SIZE - 2, CANVAS_SIZE * 0.22)
  }
  ctx.fill()

  // Three-character readings (e.g. "-12", "104") need a smaller face to
  // avoid crowding the badge at tray size.
  const fontSize = CANVAS_SIZE * (text.length >= 3 ? 0.4 : 0.52)
  ctx.fillStyle = '#ffffff'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.font = `700 ${fontSize}px ${isWin95 ? '"Tahoma", "MS Sans Serif", sans-serif' : '"Segoe UI", Arial, sans-serif'}`
  // Numerals optically sit a touch high in most fonts' em box; nudge down.
  ctx.fillText(text, CANVAS_SIZE / 2, CANVAS_SIZE / 2 + CANVAS_SIZE * 0.03)

  return canvas.toDataURL('image/png')
}
