import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import type { WeatherCondition } from '../utils/weatherCondition'
import { makeRadialTexture, makeStreakTexture } from './textures'
import { clamp, clamp01, lerp } from '../utils/math'

type PrecipMode = 'rain' | 'drizzle' | 'snow'

/** Shared particle pool size, scaled a little by quality tier (spec range: 6000-9000). */
const MAX_COUNT_BY_QUALITY: Record<Quality, number> = {
  low: 6000,
  medium: 7500,
  high: 9000
}

/** Horizontal half-width (X/Z) of the particle volume. It is re-centered on the camera every frame. */
const HALF_EXTENT = 34
/** World-space Y a particle recycles from once it falls below this (roughly ground level). */
const FLOOR_Y = -2
/** World-space Y a recycled particle reappears near. */
const CEIL_Y = 46
const VOLUME_HEIGHT = CEIL_Y - FLOOR_Y

const RAIN_FALL_MIN = 20
const RAIN_FALL_MAX = 32
const DRIZZLE_FALL_MIN = 12
const DRIZZLE_FALL_MAX = 18
const SNOW_FALL_MIN = 2.2
const SNOW_FALL_MAX = 4.6

const RAIN_WIND_DRIFT = 12
const DRIZZLE_WIND_DRIFT = 8
const SNOW_WIND_DRIFT = 5

const RAIN_FRAC_MAX = 1
const DRIZZLE_FRAC_MAX = 0.32
const SNOW_FRAC_MAX = 0.62

const RAIN_OPACITY_MAX = 0.92
const DRIZZLE_OPACITY_MAX = 0.55
const SNOW_OPACITY_MAX = 0.95

const RAIN_SIZE = 15
const DRIZZLE_SIZE = 10
const SNOW_SIZE = 23

const RAIN_TILT_SCALE = 1.1
const DRIZZLE_TILT_SCALE = 0.6
const MAX_TILT = 0.85

const SNOW_SWAY_MIN = 0.5
const SNOW_SWAY_MAX = 1.6
const SWAY_FREQ = 0.9

const VERTEX_SHADER = /* glsl */ `
  attribute float aSizeVariance;
  attribute float aSwayPhase;

  uniform float uElapsed;
  uniform float uPixelRatio;
  uniform float uBaseSize;
  uniform float uSwayAmount;

  varying float vFade;

  const float REF_DISTANCE = 18.0;
  const float SWAY_FREQ = ${SWAY_FREQ.toFixed(3)};

  void main() {
    vec3 pos = position;
    pos.x += sin(uElapsed * SWAY_FREQ + aSwayPhase) * uSwayAmount;
    pos.z += cos(uElapsed * SWAY_FREQ * 0.77 + aSwayPhase * 1.3) * uSwayAmount * 0.6;

    vec4 mvPosition = modelViewMatrix * vec4(pos, 1.0);
    gl_Position = projectionMatrix * mvPosition;

    float dist = max(-mvPosition.z, 0.001);
    float size = uBaseSize * aSizeVariance * uPixelRatio * (REF_DISTANCE / dist);
    gl_PointSize = clamp(size, 1.0, 260.0);

    float nearFade = smoothstep(0.8, 4.0, dist);
    float farFade = 1.0 - smoothstep(34.0, 50.0, dist);
    vFade = nearFade * farFade;
  }
`

const FRAGMENT_SHADER = /* glsl */ `
  uniform sampler2D uMap;
  uniform float uOpacity;
  uniform float uTiltAngle;
  uniform vec3 uColor;

  varying float vFade;

  void main() {
    vec2 uv = gl_PointCoord - 0.5;
    float s = sin(uTiltAngle);
    float c = cos(uTiltAngle);
    vec2 ruv = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c) + 0.5;
    if (ruv.x < 0.0 || ruv.x > 1.0 || ruv.y < 0.0 || ruv.y > 1.0) {
      discard;
    }

    vec4 tex = texture2D(uMap, ruv);
    float alpha = tex.a * uOpacity * vFade;
    if (alpha <= 0.004) {
      discard;
    }
    gl_FragColor = vec4(uColor * tex.rgb, alpha);
  }
`

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side curves. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/**
 * Unified rain / drizzle / snow particle system. A single THREE.Points pool
 * (sized once from `ctx.quality`) is reused across all three conditions --
 * only uniforms, the active draw range and the CPU-side simulation constants
 * change when the weather condition switches. The whole volume is rigidly
 * re-centered on the camera's X/Z every frame so it always reads as
 * "precipitation around the player" no matter how far the camera drifts.
 */
export class Precipitation implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly camera: THREE.PerspectiveCamera

  private readonly geometry: THREE.BufferGeometry
  private readonly material: THREE.ShaderMaterial
  private readonly points: THREE.Points

  private readonly streakTexture: THREE.CanvasTexture
  private readonly snowTexture: THREE.CanvasTexture

  private readonly maxCount: number
  private readonly positions: Float32Array
  /** Per-particle fall-speed variance, CPU-only (never read by the GPU). */
  private readonly fallVariance: Float32Array
  private readonly swayPhase: Float32Array

  private readonly scratchCamRight = new THREE.Vector3()
  private readonly targetColor = new THREE.Color()
  private readonly currentColor = new THREE.Color()

  private smoothedIntensity = 0
  private smoothedWind = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.camera = ctx.camera

    this.maxCount = MAX_COUNT_BY_QUALITY[ctx.quality]
    const count = this.maxCount

    const positions = new Float32Array(count * 3)
    const sizeVariance = new Float32Array(count)
    const swayPhase = new Float32Array(count)
    const fallVariance = new Float32Array(count)

    for (let i = 0; i < count; i++) {
      const i3 = i * 3
      positions[i3] = (Math.random() * 2 - 1) * HALF_EXTENT
      positions[i3 + 1] = FLOOR_Y + Math.random() * VOLUME_HEIGHT
      positions[i3 + 2] = (Math.random() * 2 - 1) * HALF_EXTENT

      sizeVariance[i] = lerp(0.7, 1.35, Math.random())
      swayPhase[i] = Math.random() * Math.PI * 2
      fallVariance[i] = lerp(0.8, 1.25, Math.random())
    }

    this.positions = positions
    this.fallVariance = fallVariance
    this.swayPhase = swayPhase

    this.geometry = new THREE.BufferGeometry()
    this.geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    this.geometry.setAttribute('aSizeVariance', new THREE.BufferAttribute(sizeVariance, 1))
    this.geometry.setAttribute('aSwayPhase', new THREE.BufferAttribute(swayPhase, 1))
    this.geometry.setDrawRange(0, 0)
    // Particles only ever roam within this fixed local volume, so a single
    // static bounding sphere (transformed by the object's world matrix each
    // frame) is enough for correct frustum culling forever.
    this.geometry.boundingSphere = new THREE.Sphere(
      new THREE.Vector3(0, (FLOOR_Y + CEIL_Y) / 2, 0),
      Math.hypot(HALF_EXTENT, VOLUME_HEIGHT / 2)
    )

    this.streakTexture = makeStreakTexture(64)
    this.snowTexture = makeRadialTexture('rgba(255,255,255,1)', 'rgba(255,255,255,0)', 64, 0.05)

    this.material = new THREE.ShaderMaterial({
      uniforms: {
        uMap: { value: this.streakTexture },
        uOpacity: { value: 0 },
        uElapsed: { value: 0 },
        uPixelRatio: { value: ctx.renderer.getPixelRatio() },
        uBaseSize: { value: RAIN_SIZE },
        uSwayAmount: { value: 0 },
        uTiltAngle: { value: 0 },
        uColor: { value: new THREE.Color(0xffffff) }
      },
      vertexShader: VERTEX_SHADER,
      fragmentShader: FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      depthTest: true,
      fog: false
    })

    this.points = new THREE.Points(this.geometry, this.material)
    this.points.frustumCulled = true
    this.points.visible = false
    this.scene.add(this.points)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const mode = this.resolveMode(params.condition)

    // Smooth intensity & wind so weather-data refreshes never cause a pop.
    const intensitySmoothing = 1 - Math.exp(-dt * 1.6)
    this.smoothedIntensity += (clamp01(params.precipitationIntensity) - this.smoothedIntensity) * intensitySmoothing
    const windSmoothing = 1 - Math.exp(-dt * 1.2)
    this.smoothedWind += (clamp01(params.windSpeed) - this.smoothedWind) * windSmoothing

    const intensity = this.smoothedIntensity
    const wind = this.smoothedWind

    let fallMin: number
    let fallMax: number
    let windDrift: number
    let fracMax: number
    let opacityMax: number
    let baseSize: number
    let tiltScale: number
    let swayAmount: number
    let texture: THREE.CanvasTexture
    let colorHex: number

    switch (mode) {
      case 'snow':
        fallMin = SNOW_FALL_MIN
        fallMax = SNOW_FALL_MAX
        windDrift = SNOW_WIND_DRIFT
        fracMax = SNOW_FRAC_MAX
        opacityMax = SNOW_OPACITY_MAX
        baseSize = SNOW_SIZE
        tiltScale = 0
        swayAmount = lerp(SNOW_SWAY_MIN, SNOW_SWAY_MAX, wind)
        texture = this.snowTexture
        colorHex = 0xffffff
        break
      case 'drizzle':
        fallMin = DRIZZLE_FALL_MIN
        fallMax = DRIZZLE_FALL_MAX
        windDrift = DRIZZLE_WIND_DRIFT
        fracMax = DRIZZLE_FRAC_MAX
        opacityMax = DRIZZLE_OPACITY_MAX
        baseSize = DRIZZLE_SIZE
        tiltScale = DRIZZLE_TILT_SCALE
        swayAmount = 0
        texture = this.streakTexture
        colorHex = 0xd7e6f5
        break
      case 'rain':
      default:
        fallMin = RAIN_FALL_MIN
        fallMax = RAIN_FALL_MAX
        windDrift = RAIN_WIND_DRIFT
        fracMax = RAIN_FRAC_MAX
        opacityMax = RAIN_OPACITY_MAX
        baseSize = RAIN_SIZE
        tiltScale = RAIN_TILT_SCALE
        swayAmount = 0
        texture = this.streakTexture
        colorHex = 0xbcd4f2
        break
    }

    const fallSpeed = lerp(fallMin, fallMax, intensity)
    const windDirX = Math.sin(params.windDirectionRad)
    const windDirZ = Math.cos(params.windDirectionRad)
    const driftSpeed = windDrift * wind
    const driftX = windDirX * driftSpeed
    const driftZ = windDirZ * driftSpeed

    // ---- Simulate & recycle every particle. Buffers are mutated in place;
    // nothing is allocated here regardless of how many are currently active.
    const pos = this.positions
    const fallVar = this.fallVariance
    const swayPhase = this.swayPhase
    const span = HALF_EXTENT * 2

    for (let i = 0; i < this.maxCount; i++) {
      const i3 = i * 3
      pos[i3 + 1] -= fallSpeed * fallVar[i] * dt
      pos[i3] += driftX * dt
      pos[i3 + 2] += driftZ * dt

      if (pos[i3 + 1] < FLOOR_Y) {
        pos[i3 + 1] += VOLUME_HEIGHT
        pos[i3] = (Math.random() * 2 - 1) * HALF_EXTENT
        pos[i3 + 2] = (Math.random() * 2 - 1) * HALF_EXTENT
        swayPhase[i] = Math.random() * Math.PI * 2
      }

      if (pos[i3] > HALF_EXTENT) pos[i3] -= span
      else if (pos[i3] < -HALF_EXTENT) pos[i3] += span
      if (pos[i3 + 2] > HALF_EXTENT) pos[i3 + 2] -= span
      else if (pos[i3 + 2] < -HALF_EXTENT) pos[i3 + 2] += span
    }
    this.geometry.attributes.position.needsUpdate = true
    this.geometry.attributes.aSwayPhase.needsUpdate = true

    // Keep the whole volume rigidly centered on the camera (X/Z, and
    // vertically too) so it always surrounds the player regardless of how
    // far the camera drifts or how high it flies.
    this.points.position.x = this.camera.position.x
    this.points.position.z = this.camera.position.z
    this.points.position.y = this.camera.position.y - (FLOOR_Y + VOLUME_HEIGHT / 2)

    // ---- Active particle count & opacity scale smoothly with intensity;
    // the buffers themselves never change size.
    const countFrac = smoothstep(0, 1, intensity) * fracMax
    const activeCount = Math.min(this.maxCount, Math.floor(this.maxCount * countFrac))
    this.geometry.setDrawRange(0, activeCount)
    this.points.visible = activeCount > 0

    const visibilityFactor = lerp(0.45, 1, clamp01(params.visibility))
    const opacity = smoothstep(0, 0.22, intensity) * opacityMax * visibilityFactor

    // ---- Wind-driven streak tilt: project the world wind direction onto the
    // camera's screen-right axis so streaks visibly lean into the wind from
    // whatever angle the camera currently views them.
    this.scratchCamRight.setFromMatrixColumn(this.camera.matrixWorld, 0)
    const rightDot = windDirX * this.scratchCamRight.x + windDirZ * this.scratchCamRight.z
    const tiltAngle = tiltScale === 0 ? 0 : clamp(rightDot * wind * tiltScale, -MAX_TILT, MAX_TILT)

    // ---- Lighting-aware tint: dimmer at night, full brightness in daylight.
    const dayFactor = clamp01(params.sunAltitude * 1.4 + 0.55)
    const brightness = lerp(0.4, 1, dayFactor)
    this.targetColor.set(colorHex).multiplyScalar(brightness)
    const colorSmoothing = 1 - Math.exp(-dt * 2)
    this.currentColor.lerp(this.targetColor, colorSmoothing)

    const u = this.material.uniforms
    u.uMap.value = texture
    u.uOpacity.value = opacity
    u.uBaseSize.value = baseSize
    u.uElapsed.value = elapsed
    u.uSwayAmount.value = swayAmount
    u.uTiltAngle.value = tiltAngle
    ;(u.uColor.value as THREE.Color).copy(this.currentColor)
  }

  dispose(): void {
    this.scene.remove(this.points)
    this.geometry.dispose()
    this.material.dispose()
    this.streakTexture.dispose()
    this.snowTexture.dispose()
  }

  private resolveMode(condition: WeatherCondition): PrecipMode {
    if (condition === 'snow') return 'snow'
    if (condition === 'drizzle') return 'drizzle'
    return 'rain'
  }
}
