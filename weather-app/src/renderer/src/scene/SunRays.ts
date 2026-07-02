import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { clamp, clamp01, lerp } from '../utils/math'

/**
 * Rays hug the sun disc: just inside Sky's CELESTIAL_RADIUS (420) so the
 * billboard sits fractionally in front of the sun sprite on the same bearing.
 */
const RAY_RADIUS = 400
/** World-units sprite scale (sun disc is 46 @ 420, so rays reach ~4x the disc). */
const BASE_SCALE = 190
/** Premium restraint: never brighter than this, even at high noon. */
const MAX_OPACITY = 0.35
/** Barely perceptible rotational drift keeps the rays feeling alive. */
const ROTATION_SPEED = 0.01

const TEXTURE_SIZE_BY_QUALITY: Record<Quality, number> = {
  low: 192,
  medium: 256,
  high: 320
}

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side opacity curve. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/** Deterministic 0-1 hash so the ray layout is stable across constructions. */
function hash01(n: number): number {
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453
  return s - Math.floor(s)
}

/**
 * Procedural god-ray texture: a soft warm core with 12 long spokes of
 * alternating length/intensity, drawn as gradient-filled triangles rotated
 * around the center. Additive-friendly (fades to transparent black).
 */
function makeSunRaysTexture(size: number): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const g = canvas.getContext('2d')
  if (!g) throw new Error('2D canvas context unavailable')

  const c = size / 2
  g.clearRect(0, 0, size, size)
  g.globalCompositeOperation = 'lighter'

  const rayCount = 12
  for (let i = 0; i < rayCount; i++) {
    const major = i % 2 === 0
    // Small deterministic jitter so the spokes don't read as a clock face.
    const angle = (i / rayCount) * Math.PI * 2 + (hash01(i) - 0.5) * 0.22
    const len = c * (major ? lerp(0.86, 0.99, hash01(i + 17)) : lerp(0.5, 0.68, hash01(i + 29)))
    const halfWidth = size * (major ? 0.022 : 0.015)
    const alpha = major ? 0.5 : 0.28

    g.save()
    g.translate(c, c)
    g.rotate(angle)
    const grad = g.createLinearGradient(0, 0, len, 0)
    grad.addColorStop(0, `rgba(255,244,214,${alpha})`)
    grad.addColorStop(0.35, `rgba(255,238,196,${alpha * 0.55})`)
    grad.addColorStop(1, 'rgba(255,220,160,0)')
    g.fillStyle = grad
    g.beginPath()
    g.moveTo(0, -halfWidth)
    g.lineTo(len, 0)
    g.lineTo(0, halfWidth)
    g.closePath()
    g.fill()
    g.restore()
  }

  // Soft core + wide halo laid over the spokes so they melt into the disc.
  const halo = g.createRadialGradient(c, c, 0, c, c, c)
  halo.addColorStop(0, 'rgba(255,250,235,0.85)')
  halo.addColorStop(0.14, 'rgba(255,240,205,0.4)')
  halo.addColorStop(0.42, 'rgba(255,225,170,0.1)')
  halo.addColorStop(1, 'rgba(255,215,150,0)')
  g.fillStyle = halo
  g.fillRect(0, 0, size, size)

  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

/**
 * Volumetric-feeling god rays hugging the sun disc on clear / partly-cloudy
 * days. A single additive billboard: rays drift almost imperceptibly, warm up
 * toward gold at sunrise/sunset (matching Sky's sun disc tinting), and fade
 * smoothly with cloud cover so weather refreshes never pop.
 */
export class SunRays implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly texture: THREE.CanvasTexture
  private readonly material: THREE.SpriteMaterial
  private readonly sprite: THREE.Sprite

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly sunDir = new THREE.Vector3()
  private readonly warmColor = new THREE.Color(0xffb46b)

  /** Smoothed opacity so weather-driven changes never pop. */
  private opacity = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene

    this.texture = makeSunRaysTexture(TEXTURE_SIZE_BY_QUALITY[ctx.quality])
    this.material = new THREE.SpriteMaterial({
      map: this.texture,
      color: 0xffffff,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      fog: false,
      blending: THREE.AdditiveBlending
    })

    this.sprite = new THREE.Sprite(this.material)
    this.sprite.scale.setScalar(BASE_SCALE)
    // Behind Sky's sun sprite (-90) but in front of the dome (-100); order
    // between additive layers is visually irrelevant, this just keeps the
    // whole celestial stack in one early pass.
    this.sprite.renderOrder = -91
    this.sprite.visible = false
    this.scene.add(this.sprite)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const altitude = clamp(params.sunAltitude, -1, 1)
    const conditionOk = params.condition === 'clear' || params.condition === 'partly-cloudy'
    const gate = params.isDay && conditionOk && altitude > 0.04

    // Strength grows as the sun clears the horizon and the sky opens up.
    const altFactor = smoothstep(0.04, 0.2, altitude)
    const cloudFactor = clamp01(1 - params.cloudCover)
    const visFactor = lerp(0.55, 1, clamp01(params.visibility))
    const target = gate ? MAX_OPACITY * altFactor * cloudFactor * visFactor : 0

    // Exponential smoothing (~1s to 63%, ~2s to 95%) so refreshes never pop.
    this.opacity += (target - this.opacity) * (1 - Math.exp(-dt * 1.7))

    if (this.opacity <= 0.004 && target <= 0) {
      // Idle at effectively zero cost when the trigger conditions fail.
      this.opacity = 0
      if (this.sprite.visible) this.sprite.visible = false
      return
    }
    this.sprite.visible = this.opacity > 0.004

    // Direction toward the sun, computed exactly like Sky.ts: altitude is
    // sin(elevation), so asin recovers the horizontal (cosine) component.
    const elevation = Math.asin(altitude)
    const horizontal = Math.cos(elevation)
    this.sunDir.set(
      Math.sin(params.sunAzimuthRad) * horizontal,
      altitude,
      Math.cos(params.sunAzimuthRad) * horizontal
    )
    this.sprite.position.copy(this.sunDir).multiplyScalar(RAY_RADIUS)

    // Warm white-gold near noon, deep gold at the horizon -- keyed to the
    // same sunset factor Sky uses for the sun disc.
    const sunsetT = clamp01(1 - Math.abs(altitude) * 3.2)
    this.material.color.set(0xfff2d0).lerp(this.warmColor, sunsetT * 0.85)

    // Rays swell slightly low in the sky (like the disc) and breathe gently.
    const breathe = 1 + Math.sin(elapsed * 0.15) * 0.025
    this.sprite.scale.setScalar(BASE_SCALE * lerp(1, 1.18, sunsetT) * breathe)

    this.material.rotation = elapsed * ROTATION_SPEED
    this.material.opacity = this.opacity
  }

  dispose(): void {
    this.scene.remove(this.sprite)
    this.material.dispose()
    this.texture.dispose()
  }
}
