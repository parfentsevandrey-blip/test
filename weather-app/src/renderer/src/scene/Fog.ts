import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { makeMistStreakTexture, makeRadialTexture } from './textures'
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
/** How far a full moon can lift the night fog color back toward daytime, at moonIllumination === 1. */
const NIGHT_FOG_MOON_LIFT = 0.12

// --- Ground-hugging mist + high haze layers ------------------------------

/** Shared per-sprite drift/fade tuning for one billboard layer (ground mist or the higher haze band). */
interface MistLayerSpec {
  radiusMin: number
  radiusMax: number
  heightMin: number
  heightMax: number
  scaleMin: number
  scaleMax: number
  /** height/width ratio of each billboard -- flattened so it hugs its band instead of reading as a cloud. */
  aspect: number
  /** Multiplier on the shared MIST_SPEED base drift rate. */
  speedMult: number
  /** Peak opacity a single sprite in this layer can reach, even in the densest fog. */
  maxOpacity: number
  /** Fraction of the ring radius reserved for fading sprites in/out near the wrap boundary. */
  edgeFadeFraction: number
  /** How far inside the leading-edge boundary a respawned puff reappears, as a fraction of the radius. */
  respawnJitterFraction: number
  /** Max per-sprite billboard spin speed (radians/sec) for the ambient (non-wind-aligned) roil. */
  rotSpeedMax: number
}

/** Sprite count per quality tier -- a handful of large soft puffs, not a particle system. */
const MIST_COUNT_BY_QUALITY: Record<Quality, number> = { low: 10, medium: 14, high: 18 }
const MIST_RADIUS_MIN = 10
const MIST_RADIUS_MAX = 46
const MIST_HEIGHT_MIN = 0.2
const MIST_HEIGHT_MAX = 2.4
const MIST_SCALE_MIN = 32
const MIST_SCALE_MAX = 58
const MIST_ASPECT = 0.32
/** World units/sec drifted at full params.windSpeed -- much slower than the cloud decks. */
const MIST_SPEED = 0.5
/** Fraction of full drift speed mist keeps creeping at even in dead calm. */
const MIST_AMBIENT_DRIFT_FLOOR = 0.15
const MIST_EDGE_FADE_FRACTION = 0.35
const MIST_MAX_OPACITY = 0.16
const MIST_ROT_SPEED_MAX = 0.01
const MIST_RESPAWN_JITTER_FRACTION = 0.02
/** How much the shared wind-gust envelope temporarily speeds up drift, on top of the steady wind factor. */
const MIST_GUST_DRIFT_BOOST = 1.4
/** params.windSpeed above which the ground mist swaps its round puff texture for an elongated, wind-aligned streak. */
const WIND_STREAK_THRESHOLD = 0.4

/** Second, higher & faster-drifting haze band -- a pure depth-banding/parallax cue behind the ground mist, not a detailed puff layer. */
const HAZE_COUNT_BY_QUALITY: Record<Quality, number> = { low: 5, medium: 7, high: 10 }
const HAZE_RADIUS_MIN = 16
const HAZE_RADIUS_MAX = 58
const HAZE_HEIGHT_MIN = 3.5
const HAZE_HEIGHT_MAX = 8
const HAZE_SCALE_MIN = 46
const HAZE_SCALE_MAX = 80
const HAZE_ASPECT = 0.4
const HAZE_SPEED_MULT = 2.4
const HAZE_EDGE_FADE_FRACTION = 0.3
const HAZE_MAX_OPACITY = 0.09
const HAZE_ROT_SPEED_MAX = 0.006
const HAZE_RESPAWN_JITTER_FRACTION = 0.02

const GROUND_MIST_SPEC: MistLayerSpec = {
  radiusMin: MIST_RADIUS_MIN,
  radiusMax: MIST_RADIUS_MAX,
  heightMin: MIST_HEIGHT_MIN,
  heightMax: MIST_HEIGHT_MAX,
  scaleMin: MIST_SCALE_MIN,
  scaleMax: MIST_SCALE_MAX,
  aspect: MIST_ASPECT,
  speedMult: 1,
  maxOpacity: MIST_MAX_OPACITY,
  edgeFadeFraction: MIST_EDGE_FADE_FRACTION,
  respawnJitterFraction: MIST_RESPAWN_JITTER_FRACTION,
  rotSpeedMax: MIST_ROT_SPEED_MAX
}

const HAZE_LAYER_SPEC: MistLayerSpec = {
  radiusMin: HAZE_RADIUS_MIN,
  radiusMax: HAZE_RADIUS_MAX,
  heightMin: HAZE_HEIGHT_MIN,
  heightMax: HAZE_HEIGHT_MAX,
  scaleMin: HAZE_SCALE_MIN,
  scaleMax: HAZE_SCALE_MAX,
  aspect: HAZE_ASPECT,
  speedMult: HAZE_SPEED_MULT,
  maxOpacity: HAZE_MAX_OPACITY,
  edgeFadeFraction: HAZE_EDGE_FADE_FRACTION,
  respawnJitterFraction: HAZE_RESPAWN_JITTER_FRACTION,
  rotSpeedMax: HAZE_ROT_SPEED_MAX
}

/** Night mist brightness floor scales with moon illumination instead of a flat isDay-only value. */
const NIGHT_MIST_FLOOR_NEW_MOON = 0.22
const NIGHT_MIST_FLOOR_FULL_MOON = 0.55
const DAY_MIST_FLOOR = 0.75

/** Per-sprite mutable simulation state for a single ground-mist/haze billboard. */
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

/** A billboard layer: its tuning spec, the group it's parented under, its sprite pool, and its own smoothed opacity. */
interface MistLayer {
  spec: MistLayerSpec
  group: THREE.Group
  sprites: MistSprite[]
  smoothedOpacity: number
}

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side density curve. */
function smoothstep01(t: number): number {
  const x = clamp01(t)
  return x * x * (3 - 2 * x)
}

/**
 * Atmospheric fog/mist. Owns `ctx.scene.fog` (a THREE.FogExp2 whose density
 * and color are driven every frame from `params.visibility`, sun altitude
 * and condition) plus two billboard-sprite layers near the ground for
 * cinematic depth in low-visibility scenes: a detailed, wind-reactive ground
 * mist and a higher, faster-drifting haze band that reads as parallax depth
 * banding behind it.
 */
export class Fog implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly camera: THREE.PerspectiveCamera
  private readonly sunLight: THREE.DirectionalLight
  private readonly hemiLight: THREE.HemisphereLight
  private readonly windGust: { value: number }

  private readonly fog: THREE.FogExp2
  private smoothedDensity = DENSITY_CLEAR

  private readonly mistTexture: THREE.CanvasTexture
  private readonly mistStreakTexture: THREE.CanvasTexture
  private readonly groundLayer: MistLayer
  private readonly hazeLayer: MistLayer

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly targetColor = new THREE.Color()
  private readonly ambientTint = new THREE.Color()
  private readonly windVec = new THREE.Vector3()
  private readonly crossVec = new THREE.Vector3()
  private readonly scratchCamRight = new THREE.Vector3()
  private readonly scratchCamUp = new THREE.Vector3()

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.camera = ctx.camera
    this.sunLight = ctx.sunLight
    this.hemiLight = ctx.hemiLight
    this.windGust = ctx.windGust

    this.fog = new THREE.FogExp2(DAY_FOG_COLOR, DENSITY_CLEAR)
    this.scene.fog = this.fog

    this.mistTexture = makeRadialTexture('rgba(235,238,242,0.85)', 'rgba(220,224,230,0)', 128, 0)
    this.mistStreakTexture = makeMistStreakTexture(128)

    this.groundLayer = this.buildMistLayer(GROUND_MIST_SPEC, MIST_COUNT_BY_QUALITY[ctx.quality])
    this.hazeLayer = this.buildMistLayer(HAZE_LAYER_SPEC, HAZE_COUNT_BY_QUALITY[ctx.quality])
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const smoothing = 1 - Math.exp(-dt * SMOOTHING_RATE)
    this.updateFog(smoothing, params)
    this.updateMist(dt, elapsed, smoothing, params)
  }

  dispose(): void {
    this.scene.remove(this.groundLayer.group)
    this.scene.remove(this.hazeLayer.group)
    for (const m of this.groundLayer.sprites) m.material.dispose()
    for (const m of this.hazeLayer.sprites) m.material.dispose()
    this.mistTexture.dispose()
    this.mistStreakTexture.dispose()
    this.scene.fog = null
  }

  private buildMistLayer(spec: MistLayerSpec, count: number): MistLayer {
    const group = new THREE.Group()
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
      this.randomizeMist(state, spec, true)
      material.rotation = state.rotPhase
      group.add(sprite)
      sprites.push(state)
    }
    this.scene.add(group)
    return { spec, group, sprites, smoothedOpacity: 0 }
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
    if (!params.isDay) {
      // Faint moonlit lift -- a full moon night never gets fully as dark/blue as a new-moon night.
      this.targetColor.lerp(DAY_FOG_COLOR, clamp01(params.moonIllumination) * NIGHT_FOG_MOON_LIFT)
    }

    let darken = 1
    if (params.condition === 'thunderstorm') darken = THUNDERSTORM_DARKEN
    else if (params.condition === 'cloudy') darken = CLOUDY_DARKEN
    this.targetColor.multiplyScalar(darken)

    this.fog.color.lerp(this.targetColor, smoothing)
  }

  /** Drifts and fades both billboard layers; invisible outside of reduced-visibility weather. */
  private updateMist(dt: number, elapsed: number, smoothing: number, params: SceneParams): void {
    this.windVec.set(Math.sin(params.windDirectionRad), 0, Math.cos(params.windDirectionRad))
    this.crossVec.set(this.windVec.z, 0, -this.windVec.x)

    // Faint ambient pickup from the shared hemisphere light, dimmed toward night by the sun's own intensity.
    this.ambientTint.copy(this.hemiLight.color).multiplyScalar(0.12)
    const nightFloor = params.isDay
      ? DAY_MIST_FLOOR
      : lerp(NIGHT_MIST_FLOOR_NEW_MOON, NIGHT_MIST_FLOOR_FULL_MOON, clamp01(params.moonIllumination))
    const intensityFactor = clamp01(this.sunLight.intensity / 2.4)
    const brightness = lerp(nightFloor, 1, intensityFactor)

    this.updateMistLayer(this.groundLayer, dt, elapsed, smoothing, params, brightness, true)
    this.updateMistLayer(this.hazeLayer, dt, elapsed, smoothing, params, brightness, false)
  }

  private updateMistLayer(
    layer: MistLayer,
    dt: number,
    elapsed: number,
    smoothing: number,
    params: SceneParams,
    brightness: number,
    allowStreak: boolean
  ): void {
    const spec = layer.spec
    const windFactor = lerp(MIST_AMBIENT_DRIFT_FLOOR, 1, clamp01(params.windSpeed))
    const gustBoost = 1 + this.windGust.value * MIST_GUST_DRIFT_BOOST
    const advance = MIST_SPEED * spec.speedMult * windFactor * gustBoost * dt

    const targetOpacity = clamp01(1 - params.visibility) * spec.maxOpacity
    layer.smoothedOpacity += (targetOpacity - layer.smoothedOpacity) * smoothing
    const globalOpacity = layer.smoothedOpacity

    const edgeBand = spec.radiusMax * spec.edgeFadeFraction
    const edgeStart = spec.radiusMax - edgeBand

    layer.group.visible = globalOpacity > 0.0008
    if (!layer.group.visible) return

    // Above the wind threshold, the ground layer swaps its round puff texture for an elongated
    // streak and aligns it to the wind's on-screen projection (via the camera's own right/up axes,
    // same trick Precipitation.ts uses for rain tilt) so it visibly streams past instead of drifting.
    const useStreak = allowStreak && clamp01(params.windSpeed) > WIND_STREAK_THRESHOLD
    let windScreenAngle = 0
    if (useStreak) {
      this.scratchCamRight.setFromMatrixColumn(this.camera.matrixWorld, 0)
      this.scratchCamUp.setFromMatrixColumn(this.camera.matrixWorld, 1)
      windScreenAngle = Math.atan2(this.windVec.dot(this.scratchCamUp), this.windVec.dot(this.scratchCamRight))
    }

    const camPos = this.camera.position
    for (const m of layer.sprites) {
      m.along += advance
      // Past the camera's trailing (downwind) edge -- recycle it just past the leading (upwind) edge.
      if (m.along > spec.radiusMax) {
        this.randomizeMist(m, spec, false)
      }

      const edge = clamp01(1 - Math.max(0, Math.abs(m.along) - edgeStart) / edgeBand)

      m.sprite.position.set(
        camPos.x + this.windVec.x * m.along + this.crossVec.x * m.cross,
        m.height,
        camPos.z + this.windVec.z * m.along + this.crossVec.z * m.cross
      )
      m.material.opacity = globalOpacity * edge * m.opacityJitter
      if (useStreak) {
        m.material.map = this.mistStreakTexture
        m.material.rotation = windScreenAngle
      } else {
        m.material.map = this.mistTexture
        m.material.rotation = m.rotPhase + elapsed * m.rotSpeed
      }
      m.material.color.set(0xffffff).multiplyScalar(brightness).add(this.ambientTint)
    }
  }

  /**
   * Re-rolls a mist puff's scatter offset, height and scale quirks. `initial`
   * scatters it anywhere across the ring; a respawn instead re-enters just
   * past the leading (upwind) edge, opposite the trailing edge it drifted past.
   */
  private randomizeMist(m: MistSprite, spec: MistLayerSpec, initial: boolean): void {
    if (initial) {
      const angle = Math.random() * Math.PI * 2
      const radius = lerp(spec.radiusMin, spec.radiusMax, Math.random())
      m.along = Math.cos(angle) * radius
      m.cross = Math.sin(angle) * radius
    } else {
      m.along = -spec.radiusMax + Math.random() * spec.radiusMax * spec.respawnJitterFraction
      m.cross = (Math.random() * 2 - 1) * spec.radiusMax
    }

    m.height = lerp(spec.heightMin, spec.heightMax, Math.random())
    const width = lerp(spec.scaleMin, spec.scaleMax, Math.random())
    m.sprite.scale.set(width, width * spec.aspect, 1)
    m.opacityJitter = lerp(0.6, 1, Math.random())
    m.rotPhase = Math.random() * Math.PI * 2
    m.rotSpeed = (Math.random() * 2 - 1) * spec.rotSpeedMax
  }
}
