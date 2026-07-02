import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { clamp, clamp01, lerp } from '../utils/math'

/** Half-size of the terrain plane (world units) -- comfortably beyond the camera's whole flight envelope, with fog hiding the far edge. */
const TERRAIN_HALF_SIZE = 340
/** Grid segments per side, scaled by quality -- built once, so a denser grid only costs construction time, not per-frame cost. */
const SEGMENTS_BY_QUALITY: Record<Quality, number> = { low: 110, medium: 160, high: 210 }
/** Distance from the camera-flight center that stays essentially flat (where the camera actually flies over). */
const FLAT_RADIUS = 42
/** Distance at which the hill amplitude reaches its full height. */
const HILL_RADIUS = 130
/** Peak hill/mountain height in world units. */
const MAX_HEIGHT = 62
/** Smoothing rate (1/s) for color/roughness so weather-data refreshes never pop. */
const SMOOTHING_RATE = 1.2

// --- Palette ---------------------------------------------------------------

/** Warm, earthy tone for low ground in full daylight. */
const LOW_DAY_COLOR = new THREE.Color(0x5c6a3f)
/** Cooler, rockier tone for high peaks in daylight. */
const HIGH_DAY_COLOR = new THREE.Color(0x8a8f86)
const NIGHT_COLOR = new THREE.Color(0x0a0d13)
const SUNSET_COLOR = new THREE.Color(0xd98a5f)
const WET_COLOR = new THREE.Color(0x272c22)
const SNOW_COLOR = new THREE.Color(0xeef3f8)
const HAZE_COLOR = new THREE.Color(0xb9c4cf)

const ROUGHNESS_DRY = 0.92
const ROUGHNESS_WET = 0.3
const ROUGHNESS_SNOW = 0.95
const METALNESS_WET = 0.1

/** Fractal value-noise, computed once per vertex at construction time -- no runtime cost. */
function hash2D(x: number, z: number): number {
  const s = Math.sin(x * 127.1 + z * 311.7) * 43758.5453123
  return s - Math.floor(s)
}

function noise2D(x: number, z: number): number {
  const xi = Math.floor(x)
  const zi = Math.floor(z)
  const xf = x - xi
  const zf = z - zi
  const u = xf * xf * (3 - 2 * xf)
  const v = zf * zf * (3 - 2 * zf)
  const a = hash2D(xi, zi)
  const b = hash2D(xi + 1, zi)
  const c = hash2D(xi, zi + 1)
  const d = hash2D(xi + 1, zi + 1)
  return lerp(lerp(a, b, u), lerp(c, d, u), v)
}

function fbm2D(x: number, z: number, octaves: number): number {
  let sum = 0
  let amp = 0.5
  let freq = 1
  let max = 0
  for (let i = 0; i < octaves; i++) {
    sum += amp * noise2D(x * freq, z * freq)
    max += amp
    amp *= 0.48
    freq *= 2.08
  }
  return sum / max
}

/** Height field: flat near the camera's flight path, rising into rolling hills/mountains further out. */
function terrainHeight(x: number, z: number): number {
  const dist = Math.sqrt(x * x + z * z)
  const hillT = clamp01((dist - FLAT_RADIUS) / (HILL_RADIUS - FLAT_RADIUS))
  const eased = hillT * hillT * (3 - 2 * hillT)

  const macro = fbm2D(x * 0.0055, z * 0.0055, 5)
  const detail = fbm2D(x * 0.028, z * 0.028, 3) * 0.2
  const ridged = Math.pow(1 - Math.abs(macro * 2 - 1), 1.5)

  const nearRipple = fbm2D(x * 0.05, z * 0.05, 2) * 0.6

  return lerp(nearRipple, (macro + detail + ridged * 0.4) * MAX_HEIGHT, eased)
}

/**
 * Draws a soft, mottled grayscale albedo (tileable blotches + fine speckle
 * grain) on an offscreen canvas -- gives the terrain surface natural
 * variation without any external image assets. Neutral gray so the
 * per-frame material tint (day/night/condition) fully controls final color.
 */
function makeGroundTexture(size = 256): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D canvas context unavailable')

  ctx.fillStyle = 'rgb(128,128,128)'
  ctx.fillRect(0, 0, size, size)

  const blobs = 46
  for (let i = 0; i < blobs; i++) {
    const cx = Math.random() * size
    const cy = Math.random() * size
    const r = size * (0.04 + Math.random() * 0.12)
    const shade = Math.round(96 + Math.random() * 80)
    const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, r)
    gradient.addColorStop(0, `rgba(${shade},${shade},${shade},0.5)`)
    gradient.addColorStop(1, `rgba(${shade},${shade},${shade},0)`)
    ctx.fillStyle = gradient
    ctx.beginPath()
    ctx.arc(cx, cy, r, 0, Math.PI * 2)
    ctx.fill()
  }

  const speckles = 900
  for (let i = 0; i < speckles; i++) {
    const x = Math.random() * size
    const y = Math.random() * size
    const shade = Math.round(90 + Math.random() * 110)
    ctx.fillStyle = `rgba(${shade},${shade},${shade},0.35)`
    ctx.fillRect(x, y, 1, 1)
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.RepeatWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(28, 28)
  return texture
}

/**
 * Builds one continuous heightfield: flat where the camera actually flies,
 * rising smoothly into rolling hills and mountains toward the horizon, with
 * real per-vertex height (not a flat wall silhouette) so lighting genuinely
 * reveals ridgelines, valleys and slopes as the sun moves. Vertex colors
 * bake a static low/high gradient; live day/night/weather tint applies via
 * `material.color` every frame.
 */
function buildTerrainGeometry(segments: number): THREE.PlaneGeometry {
  const geometry = new THREE.PlaneGeometry(TERRAIN_HALF_SIZE * 2, TERRAIN_HALF_SIZE * 2, segments, segments)
  geometry.rotateX(-Math.PI / 2)

  const position = geometry.attributes.position as THREE.BufferAttribute
  const vertexCount = position.count
  const colors = new Float32Array(vertexCount * 3)
  const blendColor = new THREE.Color()

  for (let i = 0; i < vertexCount; i++) {
    const x = position.getX(i)
    const z = position.getZ(i)
    const height = terrainHeight(x, z)
    position.setY(i, height)

    // Bake a canonical low(earthy)->high(rocky) hue gradient directly as RGB,
    // plus a permanent snow-cap tinge above a height threshold (real
    // mountains keep snow caps regardless of the current local weather).
    // The live day/night/weather shift then applies as a single uniform
    // `material.color` multiplier in update() -- it can darken/warm/cool
    // this baked gradient, just not re-hue low vs. high independently.
    const highT = clamp01(height / MAX_HEIGHT)
    blendColor.copy(LOW_DAY_COLOR).lerp(HIGH_DAY_COLOR, highT)
    const snowCapT = clamp01((highT - 0.72) / 0.28)
    blendColor.lerp(SNOW_COLOR, snowCapT * 0.75)

    colors[i * 3] = blendColor.r
    colors[i * 3 + 1] = blendColor.g
    colors[i * 3 + 2] = blendColor.b
  }

  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  geometry.computeVertexNormals()
  return geometry
}

export class Terrain implements SceneEffect {
  private readonly scene: THREE.Scene

  private readonly texture: THREE.CanvasTexture
  private readonly geometry: THREE.PlaneGeometry
  private readonly material: THREE.MeshStandardMaterial
  private readonly mesh: THREE.Mesh

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly scratchTint = new THREE.Color()
  private readonly tintCurrent = new THREE.Color(1, 1, 1)
  private smoothedRoughness = ROUGHNESS_DRY
  private smoothedMetalness = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene

    this.texture = makeGroundTexture(256)
    const segments = SEGMENTS_BY_QUALITY[ctx.quality]
    this.geometry = buildTerrainGeometry(segments)

    this.material = new THREE.MeshStandardMaterial({
      map: this.texture,
      vertexColors: true,
      color: this.tintCurrent,
      roughness: ROUGHNESS_DRY,
      metalness: 0,
      fog: true
    })

    this.mesh = new THREE.Mesh(this.geometry, this.material)
    this.mesh.matrixAutoUpdate = false
    this.mesh.updateMatrix()
    this.scene.add(this.mesh)
  }

  /**
   * The terrain's per-vertex color already bakes the full low(earthy)-
   * >high(rocky/snow-capped) gradient. This drives one uniform multiplier
   * on top of that baked gradient for day/night, sunset warmth, overcast
   * dimming, haze and wet/snow-covered darkening/paling -- it shifts the
   * whole surface's brightness and color temperature together rather than
   * re-hueing low ground and high peaks independently.
   */
  update(dt: number, _elapsed: number, params: SceneParams): void {
    const k = 1 - Math.exp(-dt * SMOOTHING_RATE)

    const altitude = clamp(params.sunAltitude, -1, 1)
    const altitudeT = clamp01(altitude * 1.8 + 0.5)
    const dayT = params.isDay ? altitudeT : altitudeT * 0.15
    const sunsetT = clamp01(1 - Math.abs(altitude) * 2.6) * altitudeT
    const hazeT = clamp01(1 - params.visibility)

    this.scratchTint.set(0xffffff).lerp(NIGHT_COLOR, 1 - dayT)
    this.scratchTint.lerp(SUNSET_COLOR, sunsetT * 0.3)
    this.scratchTint.lerp(HAZE_COLOR, hazeT * 0.35)

    const overcast = clamp01(Math.max(params.cloudCover, params.condition === 'thunderstorm' ? 0.7 : 0))
    this.scratchTint.multiplyScalar(lerp(1, 0.72, overcast))

    let roughnessTarget = ROUGHNESS_DRY
    let metalnessTarget = 0

    if (params.condition === 'rain' || params.condition === 'drizzle' || params.condition === 'thunderstorm') {
      const wet = clamp01(params.precipitationIntensity)
      this.scratchTint.lerp(WET_COLOR, wet * 0.55)
      roughnessTarget = lerp(ROUGHNESS_DRY, ROUGHNESS_WET, wet)
      metalnessTarget = lerp(0, METALNESS_WET, wet)
    } else if (params.condition === 'snow') {
      const cover = lerp(0.5, 0.9, clamp01(params.precipitationIntensity))
      this.scratchTint.lerp(SNOW_COLOR, cover * 0.7)
      roughnessTarget = ROUGHNESS_SNOW
    }

    this.tintCurrent.lerp(this.scratchTint, k)
    this.material.color.copy(this.tintCurrent)

    this.smoothedRoughness += (roughnessTarget - this.smoothedRoughness) * k
    this.smoothedMetalness += (metalnessTarget - this.smoothedMetalness) * k
    this.material.roughness = this.smoothedRoughness
    this.material.metalness = this.smoothedMetalness
  }

  dispose(): void {
    this.scene.remove(this.mesh)
    this.geometry.dispose()
    this.material.dispose()
    this.texture.dispose()
  }
}
