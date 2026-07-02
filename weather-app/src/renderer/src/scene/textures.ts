import * as THREE from 'three'

/**
 * Generates a soft radial-gradient sprite texture on an offscreen canvas.
 * Used everywhere a "glow" or "puff" billboard is needed (sun, moon, clouds,
 * stars, raindrops, snowflakes) so the app ships with zero external image
 * assets and zero licensing concerns.
 */
export function makeRadialTexture(
  innerColor: string,
  outerColor: string,
  size = 128,
  innerStop = 0
): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  const gradient = ctx.createRadialGradient(
    size / 2,
    size / 2,
    (size / 2) * innerStop,
    size / 2,
    size / 2,
    size / 2
  )
  gradient.addColorStop(0, innerColor)
  gradient.addColorStop(1, outerColor)
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

/** Soft, slightly irregular puff texture for cloud billboards. */
export function makeCloudTexture(size = 256): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  ctx.clearRect(0, 0, size, size)
  const blobs = 7
  for (let i = 0; i < blobs; i++) {
    const angle = (i / blobs) * Math.PI * 2
    const dist = size * 0.14 * Math.sin(i * 2.3)
    const cx = size / 2 + Math.cos(angle) * dist
    const cy = size / 2 + Math.sin(angle) * dist * 0.6
    const r = size * (0.28 + 0.1 * Math.cos(i * 1.7))
    const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, r)
    gradient.addColorStop(0, 'rgba(255,255,255,0.9)')
    gradient.addColorStop(0.6, 'rgba(255,255,255,0.45)')
    gradient.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(cx, cy, r, 0, Math.PI * 2)
    ctx.fill()
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  return texture
}

/** Elongated soft streak used for raindrops. */
export function makeStreakTexture(size = 64): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size * 4
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height)
  gradient.addColorStop(0, 'rgba(210,230,255,0)')
  gradient.addColorStop(0.5, 'rgba(210,230,255,0.85)')
  gradient.addColorStop(1, 'rgba(210,230,255,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(size * 0.35, 0, size * 0.3, canvas.height)

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  return texture
}
