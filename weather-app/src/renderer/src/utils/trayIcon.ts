/** Source canvas size in px; the OS scales this down to whatever the actual
 *  tray slot needs (16-32px depending on display scaling), so a moderately
 *  large source keeps the numerals crisp rather than blurry. */
const CANVAS_SIZE = 64

/**
 * Renders the rounded integer temperature as a small flat badge (a rounded
 * square, navy fill, bold white numeral) and returns it as a PNG data URL for
 * use as the Windows tray icon. A solid high-contrast badge — rather than a
 * transparent glyph — is deliberate: it reads clearly whether the taskbar is
 * set to a light or dark system theme.
 */
export function renderTrayIcon(roundedTemperature: number): string | null {
  const canvas = document.createElement('canvas')
  canvas.width = CANVAS_SIZE
  canvas.height = CANVAS_SIZE
  const ctx = canvas.getContext('2d')
  if (!ctx) return null

  const text = String(roundedTemperature)

  ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE)
  ctx.fillStyle = '#14203a'
  ctx.beginPath()
  ctx.roundRect(1, 1, CANVAS_SIZE - 2, CANVAS_SIZE - 2, CANVAS_SIZE * 0.22)
  ctx.fill()

  // Three-character readings (e.g. "-12", "104") need a smaller face to
  // avoid crowding the badge at tray size.
  const fontSize = CANVAS_SIZE * (text.length >= 3 ? 0.4 : 0.52)
  ctx.fillStyle = '#ffffff'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.font = `700 ${fontSize}px "Segoe UI", Arial, sans-serif`
  // Numerals optically sit a touch high in most fonts' em box; nudge down.
  ctx.fillText(text, CANVAS_SIZE / 2, CANVAS_SIZE / 2 + CANVAS_SIZE * 0.03)

  return canvas.toDataURL('image/png')
}
