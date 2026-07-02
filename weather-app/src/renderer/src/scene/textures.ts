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
