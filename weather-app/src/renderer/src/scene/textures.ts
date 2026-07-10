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

/**
 * A real photospheric disc for the sun sprite instead of a formless 2-stop
 * blur: a rounder-shouldered 4-stop gradient with a bright core, faint
 * granulation mottling and a thin limb-brightening ring. Baked once at
 * construction -- never touched per-frame.
 */
export function makeSunDiscTexture(size = 160): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  const cx = size / 2
  const cy = size / 2
  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, size / 2)
  gradient.addColorStop(0, 'rgba(255,252,235,1)')
  gradient.addColorStop(0.28, 'rgba(255,225,150,1)')
  gradient.addColorStop(0.55, 'rgba(255,170,70,0.55)')
  gradient.addColorStop(1, 'rgba(255,140,60,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  // Faint granulation/facula mottling on the disc body -- a handful of tiny,
  // near-invisible blotches so the core doesn't read as a perfectly flat fill.
  ctx.globalCompositeOperation = 'overlay'
  const blotchCount = 8 + Math.floor(Math.random() * 7)
  for (let i = 0; i < blotchCount; i++) {
    const angle = Math.random() * Math.PI * 2
    const radius = Math.random() * size * 0.32
    const bx = cx + Math.cos(angle) * radius
    const by = cy + Math.sin(angle) * radius
    const br = size * (0.015 + Math.random() * 0.02)
    const blotch = ctx.createRadialGradient(bx, by, 0, bx, by, br)
    const alpha = 0.04 + Math.random() * 0.04
    blotch.addColorStop(0, `rgba(255,255,255,${alpha})`)
    blotch.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = blotch
    ctx.fillRect(bx - br, by - br, br * 2, br * 2)
  }

  // Thin limb-brightening ring just inside the edge.
  ctx.globalCompositeOperation = 'source-over'
  ctx.strokeStyle = 'rgba(255,255,255,0.10)'
  ctx.lineWidth = Math.max(1, size * 0.012)
  ctx.beginPath()
  ctx.arc(cx, cy, size * 0.42, 0, Math.PI * 2)
  ctx.stroke()

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

/**
 * A moon disc with low-contrast maria/crater mottling so it reads as a rock
 * lit by reflected light, not just a paler sun. Baked once at construction.
 */
export function makeMoonDiscTexture(size = 128): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  const cx = size / 2
  const cy = size / 2
  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, size / 2)
  gradient.addColorStop(0, 'rgba(240,246,255,1)')
  gradient.addColorStop(0.6, 'rgba(215,228,245,0.9)')
  gradient.addColorStop(1, 'rgba(190,210,235,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  // Clip everything below to the disc so maria blotches never bleed past the limb.
  ctx.save()
  ctx.beginPath()
  ctx.arc(cx, cy, size / 2, 0, Math.PI * 2)
  ctx.clip()

  ctx.globalCompositeOperation = 'multiply'
  const mariaCount = 6 + Math.floor(Math.random() * 4)
  for (let i = 0; i < mariaCount; i++) {
    const angle = Math.random() * Math.PI * 2
    const radius = Math.random() * size * 0.38
    const mx = cx + Math.cos(angle) * radius
    const my = cy + Math.sin(angle) * radius
    const mr = size * (0.06 + Math.random() * 0.16)
    const blotch = ctx.createRadialGradient(mx, my, 0, mx, my, mr)
    const alpha = 0.12 + Math.random() * 0.1
    blotch.addColorStop(0, `rgba(150,165,190,${alpha})`)
    blotch.addColorStop(1, 'rgba(150,165,190,0)')
    ctx.fillStyle = blotch
    ctx.fillRect(mx - mr, my - mr, mr * 2, mr * 2)
  }
  ctx.restore()

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

/**
 * A soft radial core plus thin cross-shaped diffraction spikes, for the
 * brightest handful of stars only -- the detail that separates a photographed
 * starfield from generic particle dust.
 */
export function makeStarSpikeTexture(size = 96): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  const cx = size / 2
  const cy = size / 2

  const core = ctx.createRadialGradient(cx, cy, 0, cx, cy, size * 0.35)
  core.addColorStop(0, 'rgba(255,255,255,1)')
  core.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = core
  ctx.fillRect(0, 0, size, size)

  const drawSpike = (horizontal: boolean): void => {
    const gradient = horizontal
      ? ctx.createLinearGradient(0, cy, size, cy)
      : ctx.createLinearGradient(cx, 0, cx, size)
    gradient.addColorStop(0, 'rgba(255,255,255,0)')
    gradient.addColorStop(0.5, 'rgba(255,255,255,0.9)')
    gradient.addColorStop(1, 'rgba(255,255,255,0)')
    ctx.fillStyle = gradient
    const thickness = Math.max(1, size * 0.016)
    if (horizontal) {
      ctx.fillRect(0, cy - thickness / 2, size, thickness)
    } else {
      ctx.fillRect(cx - thickness / 2, 0, thickness, size)
    }
  }
  drawSpike(true)
  drawSpike(false)

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  return texture
}

/** A single color stop for makeGradientRampTexture: [position 0-1, CSS color string]. */
export type GradientStop = [number, string]

/**
 * Bakes an arbitrary multi-stop color ramp into a thin 1D-style texture,
 * sampled via a single varying (e.g. a shader's per-vertex/fragment 0-1
 * "core" value) instead of hardcoding a flat color -- used to give the
 * lightning bolt a real hot-core-to-fringe gradient.
 */
export function makeGradientRampTexture(stops: GradientStop[], width = 64): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = 2
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  const gradient = ctx.createLinearGradient(0, 0, width, 0)
  for (const [pos, color] of stops) gradient.addColorStop(pos, color)
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, canvas.height)

  const texture = new THREE.CanvasTexture(canvas)
  texture.wrapS = THREE.ClampToEdgeWrapping
  texture.wrapT = THREE.ClampToEdgeWrapping
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  texture.needsUpdate = true
  return texture
}

/**
 * An irregular, lumpy wisp silhouette for ground-mist puffs instead of a
 * perfect circle -- breaks the "obviously a billboard sprite" tell that
 * shows up when several perfectly-round puffs overlap.
 */
export function makeWispTexture(size = 160): THREE.CanvasTexture {
  const base = document.createElement('canvas')
  base.width = size
  base.height = size
  const baseCtx = base.getContext('2d')
  if (!baseCtx) throw new Error('2D canvas context unavailable')

  const cx = size / 2
  const cy = size / 2
  const gradient = baseCtx.createRadialGradient(cx, cy, 0, cx, cy, size / 2)
  gradient.addColorStop(0, 'rgba(235,238,242,0.85)')
  gradient.addColorStop(1, 'rgba(220,224,230,0)')
  baseCtx.fillStyle = gradient
  baseCtx.fillRect(0, 0, size, size)

  // Build an irregular alpha mask from several overlapping soft lobes, then
  // multiply the base gradient's alpha by it (destination-in) so the final
  // shape is a soft-edged, non-circular lump rather than a disc.
  const mask = document.createElement('canvas')
  mask.width = size
  mask.height = size
  const maskCtx = mask.getContext('2d')
  if (!maskCtx) throw new Error('2D canvas context unavailable')
  const lobeCount = 4 + Math.floor(Math.random() * 3)
  for (let i = 0; i < lobeCount; i++) {
    const angle = Math.random() * Math.PI * 2
    const dist = Math.random() * size * 0.25
    const lx = cx + Math.cos(angle) * dist
    const ly = cy + Math.sin(angle) * dist
    const lr = size * (0.35 + Math.random() * 0.2)
    const lobe = maskCtx.createRadialGradient(lx, ly, 0, lx, ly, lr)
    const alpha = 0.35 + Math.random() * 0.15
    lobe.addColorStop(0, `rgba(255,255,255,${alpha})`)
    lobe.addColorStop(1, 'rgba(255,255,255,0)')
    maskCtx.fillStyle = lobe
    maskCtx.fillRect(0, 0, size, size)
  }

  baseCtx.globalCompositeOperation = 'destination-in'
  baseCtx.drawImage(mask, 0, 0)
  baseCtx.globalCompositeOperation = 'source-over'

  const texture = new THREE.CanvasTexture(base)
  texture.needsUpdate = true
  return texture
}

/** Elongated, soft-edged horizontal wisp for ground-mist puffs streaming past in high wind. */
export function makeMistStreakTexture(size = 128): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  ctx.translate(size / 2, size / 2)
  ctx.scale(3, 1)
  ctx.translate(-size / 2, -size / 2)

  const gradient = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  gradient.addColorStop(0, 'rgba(235,238,242,0.85)')
  gradient.addColorStop(1, 'rgba(220,224,230,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  return texture
}
