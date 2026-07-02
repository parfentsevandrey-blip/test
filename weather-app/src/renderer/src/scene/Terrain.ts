import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { clamp, clamp01, lerp } from '../utils/math'

/** Radius of the flat ground disc. Comfortably covers the camera's full orbit (radius 26). */
const GROUND_RADIUS = 260
/** Fan-triangle slice count for the ground disc, scaled a little by quality tier. */
const GROUND_SEGMENTS_BY_QUALITY: Record<Quality, number> = { low: 48, medium: 64, high: 80 }
/** World-space Y the ground disc sits at. */
const GROUND_Y = 0

/** Smoothing rate (1/s) for color/roughness so weather-data refreshes never pop. */
const SMOOTHING_RATE = 1.2

// --- Ground palette -------------------------------------------------------

/** Warm, earthy grass/soil tone for full daylight. */
const GROUND_DAY_COLOR = new THREE.Color(0x5c6a3f)
/** Cool, near-black ground tone once the sun is well below the horizon. */
const GROUND_NIGHT_COLOR = new THREE.Color(0x0f141b)
/** Golden-hour bleed mixed in near sunrise/sunset, echoing Sky/Clouds' horizon warmth. */
const GROUND_SUNSET_COLOR = new THREE.Color(0x8a6a45)
/** Dark, saturated tone the ground shifts toward when soaked by rain. */
const GROUND_WET_COLOR = new THREE.Color(0x272c22)
/** Pale, cool tone the ground shifts toward under snow cover. */
const GROUND_SNOW_COLOR = new THREE.Color(0xe9f0f7)

const GROUND_ROUGHNESS_DRY = 0.92
const GROUND_ROUGHNESS_WET = 0.28
const GROUND_ROUGHNESS_SNOW = 0.96
const GROUND_METALNESS_WET = 0.12

// --- Horizon ridge palette --------------------------------------------------

/** Distant, hazier ridge line -- cool slate by day. */
const RIDGE_FAR_DAY = new THREE.Color(0x5f6b78)
const RIDGE_FAR_NIGHT = new THREE.Color(0x080b12)
/** Nearer, lower hill line -- reads more vegetated/earthy by day. */
const RIDGE_NEAR_DAY = new THREE.Color(0x3c4a34)
const RIDGE_NEAR_NIGHT = new THREE.Color(0x05070a)
/** Shared warm bleed applied to both ridge layers near sunrise/sunset. */
const RIDGE_SUNSET_COLOR = new THREE.Color(0xd98a5f)
/** Pale haze tone ridges bleed toward as params.visibility drops. */
const RIDGE_HAZE_COLOR = new THREE.Color(0xb9c4cf)

/** Baked per-vertex brightness multiplier for ridge peaks (sky-lit, brighter). */
const RIDGE_PEAK_BRIGHTNESS = 1.25
/** Baked per-vertex brightness multiplier for ridge bases (hazier, darker). */
const RIDGE_BASE_BRIGHTNESS = 0.6

/** Segment count for both ridge rings, scaled a little by quality tier. */
const RIDGE_SEGMENTS_BY_QUALITY: Record<Quality, number> = { low: 40, medium: 56, high: 72 }
/** How far below the ground the ridge base sits, so it never shows a gap under the terrain. */
const RIDGE_BASE_Y = -12

const RIDGE_FAR_RADIUS = 225
const RIDGE_FAR_RADIUS_JITTER = 30
const RIDGE_FAR_HEIGHT_MIN = 16
const RIDGE_FAR_HEIGHT_MAX = 46

const RIDGE_NEAR_RADIUS = 140
const RIDGE_NEAR_RADIUS_JITTER = 22
const RIDGE_NEAR_HEIGHT_MIN = 6
const RIDGE_NEAR_HEIGHT_MAX = 18

/**
 * Draws a soft, mottled grayscale albedo for the ground disc (tileable blotches
 * + fine speckle grain) on an offscreen canvas -- gives the flat disc some
 * natural variation without any external image assets. Neutral gray so the
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
  texture.repeat.set(20, 20)
  return texture
}

/**
 * Builds one closed ring of jagged mountain/hill silhouette walls: a top
 * vertex ring (height varies per segment via a few layered sine harmonics
 * plus jitter, for a natural but stylized skyline) and a bottom ring sunk
 * well below the ground plane so no seam ever shows. Vertex colors bake a
 * static peak-bright/base-dark gradient; the live day/night/haze tint is
 * applied every frame purely via `material.color`, so nothing here needs to
 * be touched again after construction.
 */
function buildRidgeGeometry(
  segments: number,
  radiusBase: number,
  radiusJitter: number,
  heightMin: number,
  heightMax: number,
  baseY: number,
  seed: number
): THREE.BufferGeometry {
  const vertCount = segments * 2
  const positions = new Float32Array(vertCount * 3)
  const colors = new Float32Array(vertCount * 3)
  const indices: number[] = []

  for (let i = 0; i < segments; i++) {
    const angle = (i / segments) * Math.PI * 2
    const n =
      Math.sin(angle * 3 + seed) * 0.4 +
      Math.sin(angle * 7 + seed * 1.7) * 0.25 +
      Math.sin(angle * 13 + seed * 2.3) * 0.15 +
      (Math.random() - 0.5) * 0.3
    const heightT = clamp01(n * 0.5 + 0.5)
    const height = lerp(heightMin, heightMax, heightT)
    const radius = radiusBase + (Math.random() - 0.5) * radiusJitter

    const x = Math.sin(angle) * radius
    const z = Math.cos(angle) * radius

    const topIdx = i
    const baseIdx = segments + i

    positions[topIdx * 3] = x
    positions[topIdx * 3 + 1] = baseY + height
    positions[topIdx * 3 + 2] = z

    positions[baseIdx * 3] = x
    positions[baseIdx * 3 + 1] = baseY
    positions[baseIdx * 3 + 2] = z

    colors[topIdx * 3] = RIDGE_PEAK_BRIGHTNESS
    colors[topIdx * 3 + 1] = RIDGE_PEAK_BRIGHTNESS
    colors[topIdx * 3 + 2] = RIDGE_PEAK_BRIGHTNESS

    colors[baseIdx * 3] = RIDGE_BASE_BRIGHTNESS
    colors[baseIdx * 3 + 1] = RIDGE_BASE_BRIGHTNESS
    colors[baseIdx * 3 + 2] = RIDGE_BASE_BRIGHTNESS
  }

  for (let i = 0; i < segments; i++) {
    const next = (i + 1) % segments
    const t0 = i
    const t1 = next
    const b0 = segments + i
    const b1 = segments + next
    indices.push(t0, b0, t1, t1, b0, b1)
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  geometry.setIndex(indices)
  geometry.computeVertexNormals()
  return geometry
}

/**
 * Grounding backdrop: a large flat ground disc plus two concentric rings of
 * low-poly mountain/hill silhouettes around the horizon, so the scene reads
 * as a place instead of floating in empty sky. Everything here is static
 * geometry built once in the constructor -- `update()` only retints material
 * colors (and the ground's roughness/metalness) from the current params, so
 * it costs a handful of Color lerps per frame regardless of quality tier.
 */
export class Terrain implements SceneEffect {
  private readonly scene: THREE.Scene

  private readonly groundTexture: THREE.CanvasTexture
  private readonly groundGeometry: THREE.CircleGeometry
  private readonly groundMaterial: THREE.MeshStandardMaterial
  private readonly groundMesh: THREE.Mesh

  private readonly ridgeFarGeometry: THREE.BufferGeometry
  private readonly ridgeFarMaterial: THREE.MeshLambertMaterial
  private readonly ridgeFarMesh: THREE.Mesh

  private readonly ridgeNearGeometry: THREE.BufferGeometry
  private readonly ridgeNearMaterial: THREE.MeshLambertMaterial
  private readonly ridgeNearMesh: THREE.Mesh

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly scratchA = new THREE.Color()
  private readonly scratchB = new THREE.Color()
  private readonly groundColorCurrent = new THREE.Color().copy(GROUND_NIGHT_COLOR)
  private readonly ridgeFarColorCurrent = new THREE.Color().copy(RIDGE_FAR_NIGHT)
  private readonly ridgeNearColorCurrent = new THREE.Color().copy(RIDGE_NEAR_NIGHT)
  private smoothedRoughness = GROUND_ROUGHNESS_DRY
  private smoothedMetalness = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene

    // --- Ground disc ------------------------------------------------------
    this.groundTexture = makeGroundTexture(256)
    const groundSegments = GROUND_SEGMENTS_BY_QUALITY[ctx.quality]
    this.groundGeometry = new THREE.CircleGeometry(GROUND_RADIUS, groundSegments)
    this.groundGeometry.rotateX(-Math.PI / 2)

    this.groundMaterial = new THREE.MeshStandardMaterial({
      map: this.groundTexture,
      color: this.groundColorCurrent,
      roughness: GROUND_ROUGHNESS_DRY,
      metalness: 0,
      fog: true
    })
    this.groundMesh = new THREE.Mesh(this.groundGeometry, this.groundMaterial)
    this.groundMesh.position.y = GROUND_Y
    this.groundMesh.matrixAutoUpdate = false
    this.groundMesh.updateMatrix()
    this.scene.add(this.groundMesh)

    // --- Horizon ridges -----------------------------------------------------
    const ridgeSegments = RIDGE_SEGMENTS_BY_QUALITY[ctx.quality]

    this.ridgeFarGeometry = buildRidgeGeometry(
      ridgeSegments,
      RIDGE_FAR_RADIUS,
      RIDGE_FAR_RADIUS_JITTER,
      RIDGE_FAR_HEIGHT_MIN,
      RIDGE_FAR_HEIGHT_MAX,
      RIDGE_BASE_Y,
      1.7
    )
    this.ridgeFarMaterial = new THREE.MeshLambertMaterial({
      color: this.ridgeFarColorCurrent,
      vertexColors: true,
      flatShading: true,
      side: THREE.DoubleSide,
      fog: true
    })
    this.ridgeFarMesh = new THREE.Mesh(this.ridgeFarGeometry, this.ridgeFarMaterial)
    this.ridgeFarMesh.matrixAutoUpdate = false
    this.ridgeFarMesh.updateMatrix()
    this.scene.add(this.ridgeFarMesh)

    this.ridgeNearGeometry = buildRidgeGeometry(
      ridgeSegments,
      RIDGE_NEAR_RADIUS,
      RIDGE_NEAR_RADIUS_JITTER,
      RIDGE_NEAR_HEIGHT_MIN,
      RIDGE_NEAR_HEIGHT_MAX,
      RIDGE_BASE_Y,
      5.2
    )
    this.ridgeNearMaterial = new THREE.MeshLambertMaterial({
      color: this.ridgeNearColorCurrent,
      vertexColors: true,
      flatShading: true,
      side: THREE.DoubleSide,
      fog: true
    })
    this.ridgeNearMesh = new THREE.Mesh(this.ridgeNearGeometry, this.ridgeNearMaterial)
    this.ridgeNearMesh.matrixAutoUpdate = false
    this.ridgeNearMesh.updateMatrix()
    this.scene.add(this.ridgeNearMesh)
  }

  update(dt: number, _elapsed: number, params: SceneParams): void {
    const k = 1 - Math.exp(-dt * SMOOTHING_RATE)

    const altitude = clamp(params.sunAltitude, -1, 1)
    const altitudeT = clamp01(altitude * 1.8 + 0.5)
    // Continuous day/night ramp (mirrors Fog/Sky's family of curves); isDay
    // gates it so the terminator ambiguity never leaves the ground reading
    // "daytime" once night has actually fallen.
    const dayT = params.isDay ? altitudeT : altitudeT * 0.15
    const sunsetT = clamp01(1 - Math.abs(altitude) * 2.6) * altitudeT

    this.updateGround(k, dayT, sunsetT, params)
    this.updateRidges(k, dayT, sunsetT, params)
  }

  dispose(): void {
    this.scene.remove(this.groundMesh)
    this.groundGeometry.dispose()
    this.groundMaterial.dispose()
    this.groundTexture.dispose()

    this.scene.remove(this.ridgeFarMesh)
    this.ridgeFarGeometry.dispose()
    this.ridgeFarMaterial.dispose()

    this.scene.remove(this.ridgeNearMesh)
    this.ridgeNearGeometry.dispose()
    this.ridgeNearMaterial.dispose()
  }

  /** Retints/reroughens the ground disc from day/night, cloud cover and condition. */
  private updateGround(k: number, dayT: number, sunsetT: number, params: SceneParams): void {
    this.scratchA.copy(GROUND_NIGHT_COLOR).lerp(GROUND_DAY_COLOR, dayT)
    this.scratchA.lerp(GROUND_SUNSET_COLOR, sunsetT * 0.35)

    // Overcast / stormy skies dim the ground a touch, same spirit as Sky's hemi dimming.
    const overcast = clamp01(Math.max(params.cloudCover, params.condition === 'thunderstorm' ? 0.7 : 0))
    this.scratchA.multiplyScalar(lerp(1, 0.72, overcast))

    let roughnessTarget = GROUND_ROUGHNESS_DRY
    let metalnessTarget = 0

    if (params.condition === 'rain' || params.condition === 'drizzle' || params.condition === 'thunderstorm') {
      // Wet look: darker, more saturated ground with a lower roughness (sharper highlights)
      // and a slight metalness bump standing in for a faint specular sheen.
      const wet = clamp01(params.precipitationIntensity)
      this.scratchA.lerp(GROUND_WET_COLOR, wet * 0.8)
      roughnessTarget = lerp(GROUND_ROUGHNESS_DRY, GROUND_ROUGHNESS_WET, wet)
      metalnessTarget = lerp(0, GROUND_METALNESS_WET, wet)
    } else if (params.condition === 'snow') {
      // Snow cover: pale blue-white, matte (high roughness) -- always mostly covered,
      // with fresh precipitation intensity pushing it toward fully blanketed.
      const cover = lerp(0.6, 1, clamp01(params.precipitationIntensity))
      this.scratchA.lerp(GROUND_SNOW_COLOR, cover)
      roughnessTarget = GROUND_ROUGHNESS_SNOW
    }

    this.groundColorCurrent.lerp(this.scratchA, k)
    this.groundMaterial.color.copy(this.groundColorCurrent)
    this.smoothedRoughness += (roughnessTarget - this.smoothedRoughness) * k
    this.smoothedMetalness += (metalnessTarget - this.smoothedMetalness) * k
    this.groundMaterial.roughness = this.smoothedRoughness
    this.groundMaterial.metalness = this.smoothedMetalness
  }

  /** Retints both horizon ridge rings from day/night and params.visibility haze. */
  private updateRidges(k: number, dayT: number, sunsetT: number, params: SceneParams): void {
    const hazeT = clamp01(1 - params.visibility)

    this.scratchA.copy(RIDGE_FAR_NIGHT).lerp(RIDGE_FAR_DAY, dayT)
    this.scratchA.lerp(RIDGE_SUNSET_COLOR, sunsetT * 0.4)
    this.scratchA.lerp(RIDGE_HAZE_COLOR, hazeT * 0.65)
    this.ridgeFarColorCurrent.lerp(this.scratchA, k)
    this.ridgeFarMaterial.color.copy(this.ridgeFarColorCurrent)

    this.scratchB.copy(RIDGE_NEAR_NIGHT).lerp(RIDGE_NEAR_DAY, dayT)
    this.scratchB.lerp(RIDGE_SUNSET_COLOR, sunsetT * 0.22)
    this.scratchB.lerp(RIDGE_HAZE_COLOR, hazeT * 0.3)
    this.ridgeNearColorCurrent.lerp(this.scratchB, k)
    this.ridgeNearMaterial.color.copy(this.ridgeNearColorCurrent)
  }
}
