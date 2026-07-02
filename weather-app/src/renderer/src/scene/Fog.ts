import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { makeRadialTexture } from './textures'
import { clamp, clamp01, lerp } from '../utils/math'

/** FogExp2 density at params.visibility === 1 -- a whisper of atmospheric haze, never truly zero. */
const DENSITY_CLEAR = 0.0006
/** FogExp2 density at params.visibility === 0 -- thick enough to swallow the terrain/horizon within a short distance. */
const DENSITY_DENSE = 0.055
/** Extra density always added on top of the visibility curve when condition === 'fog', so it never reads as merely "hazy". */
const FOG_CONDITION_EXTRA_DENSITY = 0.026
/** Exponential smoothing rate (1/s) for density/color/mist opacity so weather-data refreshes never pop. */
const SMOOTHING_RATE = 1.6

/** Warm, pale grey daytime fog. */
const DAY_FOG_COLOR = new THREE.Color(0xcfc7b6)
/** Dark blue-grey night fog. */
const NIGHT_FOG_COLOR = new THREE.Color(0x141b26)
/** Warm bleed mixed in near sunrise/sunset, same spirit as Sky/Clouds' horizon warmth. */
const SUNSET_FOG_COLOR = new THREE.Color(0xd99e6c)

/** Color darkening multiplier applied on top of the day/night blend for moody conditions. */
const THUNDERSTORM_DARKEN = 0.55
const CLOUDY_DARKEN = 0.82

// --- Ground-hugging mist layer -------------------------------------------

/** Sprite count per quality tier -- a handful of large soft puffs, not a particle system. */
const MIST_COUNT_BY_QUALITY: Record<Quality, number> = { low: 10, medium: 14, high: 18 }
const MIST_RADIUS_MIN = 10
const MIST_RADIUS_MAX = 46
const MIST_HEIGHT_MIN = 0.2
const MIST_HEIGHT_MAX = 2.4
const MIST_SCALE_MIN = 32
const MIST_SCALE_MAX = 58
/** height/width ratio of each billboard -- flattened so it hugs the ground instead of reading as a cloud. */
const MIST_ASPECT = 0.32
/** World units/sec drifted at full params.windSpeed -- much slower than the cloud decks. */
const MIST_SPEED = 0.5
/** Fraction of full drift speed mist keeps creeping at even in dead calm. */
const MIST_AMBIENT_DRIFT_FLOOR = 0.15
/** Fraction of the ring radius reserved for fading sprites in/out near the wrap boundary. */
const MIST_EDGE_FADE_FRACTION = 0.35
/** Peak opacity a single mist puff can reach, even in the densest fog. */
const MIST_MAX_OPACITY = 0.16
/** Max per-sprite billboard spin speed (radians/sec) -- an almost imperceptible slow roil. */
const MIST_ROT_SPEED_MAX = 0.01
/** How far inside the leading-edge boundary a respawned puff reappears, as a fraction of the radius. */
const MIST_RESPAWN_JITTER_FRACTION = 0.02

/** Per-sprite mutable simulation state for a single ground-mist billboard. */
interface MistSprite {
  sprite: THREE.Sprite
  material: THREE.SpriteMaterial
  /** Distance along the wind axis, relative to the camera (negative = upwind, positive = downwind). */
  along: number
  /** Fixed cross-wind offset from the camera for this puff's lifetime between respawns. */
  cross: number
  height: number
  opacityJitter: number
  rotPhase: number
  rotSpeed: number
}

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side density curve. */
function smoothstep01(t: number): number {
  const x = clamp01(t)
  return x * x * (3 - 2 * x)
}

/**
 * Atmospheric fog/mist. Owns `ctx.scene.fog` (a THREE.FogExp2 whose density
 * and color are driven every frame from `params.visibility`, sun altitude
 * and condition) plus a handful of large, near-transparent, slow-drifting
 * billboard sprites near the ground for cinematic depth in low-visibility
 * scenes.
 */
export class Fog implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly camera: THREE.PerspectiveCamera
  private readonly sunLight: THREE.DirectionalLight
  private readonly hemiLight: THREE.HemisphereLight

  private readonly fog: THREE.FogExp2
  private smoothedDensity = DENSITY_CLEAR
  private smoothedMistOpacity = 0

  private readonly mistTexture: THREE.CanvasTexture
  private readonly mistGroup: THREE.Group
  private readonly mistSprites: MistSprite[]

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly targetColor = new THREE.Color()
  private readonly ambientTint = new THREE.Color()
  private readonly windVec = new THREE.Vector3()
  private readonly crossVec = new THREE.Vector3()

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.camera = ctx.camera
    this.sunLight = ctx.sunLight
    this.hemiLight = ctx.hemiLight

    this.fog = new THREE.FogExp2(DAY_FOG_COLOR, DENSITY_CLEAR)
    this.scene.fog = this.fog

    this.mistTexture = makeRadialTexture('rgba(235,238,242,0.85)', 'rgba(220,224,230,0)', 128, 0)

    this.mistGroup = new THREE.Group()
    const count = MIST_COUNT_BY_QUALITY[ctx.quality]
    const sprites: MistSprite[] = []
    for (let i = 0; i < count; i++) {
      const material = new THREE.SpriteMaterial({
        map: this.mistTexture,
        color: 0xffffff,
        transparent: true,
        depthWrite: false,
        opacity: 0,
        fog: false
      })
      const sprite = new THREE.Sprite(material)
      const state: MistSprite = {
        sprite,
        material,
        along: 0,
        cross: 0,
        height: 0,
        opacityJitter: 1,
        rotPhase: 0,
        rotSpeed: 0
      }
      this.randomizeMist(state, true)
      material.rotation = state.rotPhase
      this.mistGroup.add(sprite)
      sprites.push(state)
    }
    this.mistSprites = sprites
    this.scene.add(this.mistGroup)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const smoothing = 1 - Math.exp(-dt * SMOOTHING_RATE)
    this.updateFog(smoothing, params)
    this.updateMist(dt, elapsed, smoothing, params)
  }

  dispose(): void {
    this.scene.remove(this.mistGroup)
    for (const m of this.mistSprites) {
      m.material.dispose()
    }
    this.mistTexture.dispose()
    this.scene.fog = null
  }

  /** Drives ctx.scene.fog's density and color from visibility, condition, sun altitude and day/night. */
  private updateFog(smoothing: number, params: SceneParams): void {
    const vis = clamp01(params.visibility)
    const curve = smoothstep01(1 - vis)
    let targetDensity = lerp(DENSITY_CLEAR, DENSITY_DENSE, curve)
    if (params.condition === 'fog') {
      targetDensity += FOG_CONDITION_EXTRA_DENSITY
    }
    this.smoothedDensity += (targetDensity - this.smoothedDensity) * smoothing
    this.fog.density = this.smoothedDensity

    const altitude = clamp(params.sunAltitude, -1, 1)
    // Continuous day/night ramp (mirrors Sky's dayIntensity curve family); isDay gates it so the
    // terminator ambiguity never leaves the fog reading "daytime" once night has actually fallen.
    const altitudeT = clamp01(altitude * 1.8 + 0.5)
    const dayT = params.isDay ? altitudeT : altitudeT * 0.15
    const sunsetT = clamp01(1 - Math.abs(altitude) * 2.6) * altitudeT

    this.targetColor.copy(NIGHT_FOG_COLOR).lerp(DAY_FOG_COLOR, dayT)
    this.targetColor.lerp(SUNSET_FOG_COLOR, sunsetT * 0.3)

    let darken = 1
    if (params.condition === 'thunderstorm') darken = THUNDERSTORM_DARKEN
    else if (params.condition === 'cloudy') darken = CLOUDY_DARKEN
    this.targetColor.multiplyScalar(darken)

    this.fog.color.lerp(this.targetColor, smoothing)
  }

  /** Drifts and fades the low ground-mist billboards; invisible outside of reduced-visibility weather. */
  private updateMist(dt: number, elapsed: number, smoothing: number, params: SceneParams): void {
    this.windVec.set(Math.sin(params.windDirectionRad), 0, Math.cos(params.windDirectionRad))
    this.crossVec.set(this.windVec.z, 0, -this.windVec.x)

    const windFactor = lerp(MIST_AMBIENT_DRIFT_FLOOR, 1, clamp01(params.windSpeed))
    const advance = MIST_SPEED * windFactor * dt

    const targetOpacity = clamp01(1 - params.visibility) * MIST_MAX_OPACITY
    this.smoothedMistOpacity += (targetOpacity - this.smoothedMistOpacity) * smoothing
    const globalOpacity = this.smoothedMistOpacity

    // Faint ambient pickup from the shared hemisphere light, dimmed toward night by the sun's own intensity.
    this.ambientTint.copy(this.hemiLight.color).multiplyScalar(0.12)
    const nightFloor = params.isDay ? 0.75 : 0.4
    const intensityFactor = clamp01(this.sunLight.intensity / 2.4)
    const brightness = lerp(nightFloor, 1, intensityFactor)

    const camPos = this.camera.position
    const edgeBand = MIST_RADIUS_MAX * MIST_EDGE_FADE_FRACTION
    const edgeStart = MIST_RADIUS_MAX - edgeBand

    this.mistGroup.visible = globalOpacity > 0.0008

    if (!this.mistGroup.visible) return

    for (const m of this.mistSprites) {
      m.along += advance
      // Past the camera's trailing (downwind) edge -- recycle it just past the leading (upwind) edge.
      if (m.along > MIST_RADIUS_MAX) {
        this.randomizeMist(m, false)
      }

      const edge = clamp01(1 - Math.max(0, Math.abs(m.along) - edgeStart) / edgeBand)

      m.sprite.position.set(
        camPos.x + this.windVec.x * m.along + this.crossVec.x * m.cross,
        m.height,
        camPos.z + this.windVec.z * m.along + this.crossVec.z * m.cross
      )
      m.material.opacity = globalOpacity * edge * m.opacityJitter
      m.material.rotation = m.rotPhase + elapsed * m.rotSpeed
      m.material.color.set(0xffffff).multiplyScalar(brightness).add(this.ambientTint)
    }
  }

  /**
   * Re-rolls a mist puff's scatter offset, height and scale quirks. `initial`
   * scatters it anywhere across the ring; a respawn instead re-enters just
   * past the leading (upwind) edge, opposite the trailing edge it drifted past.
   */
  private randomizeMist(m: MistSprite, initial: boolean): void {
    if (initial) {
      const angle = Math.random() * Math.PI * 2
      const radius = lerp(MIST_RADIUS_MIN, MIST_RADIUS_MAX, Math.random())
      m.along = Math.cos(angle) * radius
      m.cross = Math.sin(angle) * radius
    } else {
      m.along = -MIST_RADIUS_MAX + Math.random() * MIST_RADIUS_MAX * MIST_RESPAWN_JITTER_FRACTION
      m.cross = (Math.random() * 2 - 1) * MIST_RADIUS_MAX
    }

    m.height = lerp(MIST_HEIGHT_MIN, MIST_HEIGHT_MAX, Math.random())
    const width = lerp(MIST_SCALE_MIN, MIST_SCALE_MAX, Math.random())
    m.sprite.scale.set(width, width * MIST_ASPECT, 1)
    m.opacityJitter = lerp(0.6, 1, Math.random())
    m.rotPhase = Math.random() * Math.PI * 2
    m.rotSpeed = (Math.random() * 2 - 1) * MIST_ROT_SPEED_MAX
  }
}
