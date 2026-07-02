import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { makeCloudTexture } from './textures'
import { clamp, clamp01, lerp } from '../utils/math'

/** Fraction of full wind speed clouds keep drifting at even in dead calm, so the deck never freezes solid. */
const AMBIENT_DRIFT_FLOOR = 0.08
/** Fraction of a layer's radius reserved for fading sprites in/out near the wrap boundary (hides the respawn pop). */
const EDGE_FADE_FRACTION = 0.3
/** How far inside the leading-edge boundary a respawned cloud reappears, as a fraction of radiusMax. */
const RESPAWN_JITTER_FRACTION = 0.02
/** Max per-sprite billboard spin speed (radians/sec) -- a slow lazy roil, not a spin. */
const ROT_SPEED_MAX = 0.025
/** Vertical bob speed (radians/sec of each sprite's sine phase). */
const BOB_SPEED = 0.15
/** Thin high clouds read a touch brighter (more sun-facing, less self-shadowed). */
const HIGH_BRIGHTNESS_MUL = 1.08
/** Denser low clouds read a touch darker (thicker, more internal shadowing). */
const LOW_BRIGHTNESS_MUL = 0.93

interface CloudLayerConfig {
  radiusMin: number
  radiusMax: number
  heightMin: number
  heightMax: number
  /** World units/sec drifted at full params.windSpeed. */
  speed: number
  /** Multiplies params.cloudCover to get this layer's peak opacity. */
  opacityMul: number
  scaleMin: number
  scaleMax: number
  /** height/width ratio of each billboard. */
  aspect: number
  bobAmount: number
  countByQuality: Record<Quality, number>
}

/** Higher, thinner, faster-moving cirrus-like deck. */
const HIGH_LAYER_CONFIG: CloudLayerConfig = {
  radiusMin: 110,
  radiusMax: 220,
  heightMin: 92,
  heightMax: 148,
  speed: 9,
  opacityMul: 0.5,
  scaleMin: 32,
  scaleMax: 58,
  aspect: 0.5,
  bobAmount: 1.6,
  countByQuality: { low: 24, medium: 32, high: 40 }
}

/** Lower, denser, slower-moving cumulus-like deck. */
const LOW_LAYER_CONFIG: CloudLayerConfig = {
  radiusMin: 60,
  radiusMax: 155,
  heightMin: 30,
  heightMax: 70,
  speed: 3.4,
  opacityMul: 0.85,
  scaleMin: 40,
  scaleMax: 76,
  aspect: 0.72,
  bobAmount: 0.8,
  countByQuality: { low: 28, medium: 38, high: 48 }
}

/** Per-sprite mutable simulation state for a single cloud billboard. */
interface CloudSpriteState {
  sprite: THREE.Sprite
  material: THREE.SpriteMaterial
  /** Distance along the wind axis, relative to the camera (negative = upwind/leading side, positive = downwind/trailing side). */
  along: number
  /** Fixed cross-wind offset from the camera for this cloud's lifetime between respawns. */
  cross: number
  height: number
  rotPhase: number
  rotSpeed: number
  bobPhase: number
  opacityJitter: number
  colorJitter: number
}

interface CloudLayer {
  config: CloudLayerConfig
  group: THREE.Group
  clouds: CloudSpriteState[]
  /** Shared tint recomputed once per frame, then multiplied per-sprite by colorJitter. */
  color: THREE.Color
}

/**
 * Two drifting billboard-sprite cloud decks -- a thin fast high layer and a
 * denser slow low layer -- scattered in a wide ring around the camera and
 * recycled seamlessly as they drift downwind past the visible range.
 */
export class Clouds implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly camera: THREE.PerspectiveCamera
  private readonly sunLight: THREE.DirectionalLight
  private readonly hemiLight: THREE.HemisphereLight

  private readonly cloudTexture: THREE.CanvasTexture
  private readonly highLayer: CloudLayer
  private readonly lowLayer: CloudLayer

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly windVec = new THREE.Vector3()
  private readonly crossVec = new THREE.Vector3()
  private readonly tintColor = new THREE.Color()
  private readonly ambientFill = new THREE.Color()
  private readonly scratchHex = new THREE.Color()

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.camera = ctx.camera
    this.sunLight = ctx.sunLight
    this.hemiLight = ctx.hemiLight

    this.cloudTexture = makeCloudTexture(256)

    this.highLayer = this.buildLayer(ctx, HIGH_LAYER_CONFIG)
    this.lowLayer = this.buildLayer(ctx, LOW_LAYER_CONFIG)

    this.scene.add(this.highLayer.group, this.lowLayer.group)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    this.windVec.set(Math.sin(params.windDirectionRad), 0, Math.cos(params.windDirectionRad))
    this.crossVec.set(this.windVec.z, 0, -this.windVec.x)

    const windFactor = lerp(AMBIENT_DRIFT_FLOOR, 1, clamp01(params.windSpeed))
    const cover = clamp01(params.cloudCover)

    this.updateTint(params)
    this.highLayer.color.copy(this.tintColor).multiplyScalar(HIGH_BRIGHTNESS_MUL)
    this.lowLayer.color.copy(this.tintColor).multiplyScalar(LOW_BRIGHTNESS_MUL)

    this.updateLayer(this.highLayer, dt, elapsed, windFactor, cover)
    this.updateLayer(this.lowLayer, dt, elapsed, windFactor, cover)
  }

  dispose(): void {
    this.disposeLayer(this.highLayer)
    this.disposeLayer(this.lowLayer)
    this.cloudTexture.dispose()
  }

  private buildLayer(ctx: SceneContext, config: CloudLayerConfig): CloudLayer {
    const group = new THREE.Group()
    const count = config.countByQuality[ctx.quality]
    const clouds: CloudSpriteState[] = []

    for (let i = 0; i < count; i++) {
      const material = new THREE.SpriteMaterial({
        map: this.cloudTexture,
        color: 0xffffff,
        transparent: true,
        depthWrite: false,
        opacity: 0
      })
      const sprite = new THREE.Sprite(material)

      const cloud: CloudSpriteState = {
        sprite,
        material,
        along: 0,
        cross: 0,
        height: 0,
        rotPhase: 0,
        rotSpeed: 0,
        bobPhase: 0,
        opacityJitter: 1,
        colorJitter: 1
      }
      this.randomizeCloud(cloud, config, true)
      material.rotation = cloud.rotPhase

      group.add(sprite)
      clouds.push(cloud)
    }

    return { config, group, clouds, color: new THREE.Color(0xffffff) }
  }

  /**
   * Re-rolls a cloud's scatter offset, height, scale and drift quirks.
   * `initial` scatters it anywhere across the ring; a respawn instead re-enters
   * just past the leading (upwind) edge, opposite the trailing edge it drifted past.
   */
  private randomizeCloud(cloud: CloudSpriteState, config: CloudLayerConfig, initial: boolean): void {
    if (initial) {
      const angle = Math.random() * Math.PI * 2
      const radius = lerp(config.radiusMin, config.radiusMax, Math.random())
      cloud.along = Math.cos(angle) * radius
      cloud.cross = Math.sin(angle) * radius
    } else {
      cloud.along = -config.radiusMax + Math.random() * config.radiusMax * RESPAWN_JITTER_FRACTION
      cloud.cross = (Math.random() * 2 - 1) * config.radiusMax
    }

    cloud.height = lerp(config.heightMin, config.heightMax, Math.random())
    const width = lerp(config.scaleMin, config.scaleMax, Math.random())
    cloud.sprite.scale.set(width, width * config.aspect, 1)
    cloud.rotPhase = Math.random() * Math.PI * 2
    cloud.rotSpeed = (Math.random() * 2 - 1) * ROT_SPEED_MAX
    cloud.bobPhase = Math.random() * Math.PI * 2
    cloud.opacityJitter = lerp(0.75, 1, Math.random())
    cloud.colorJitter = lerp(0.9, 1.08, Math.random())
  }

  private updateLayer(layer: CloudLayer, dt: number, elapsed: number, windFactor: number, cover: number): void {
    const { config, clouds, color } = layer
    const camPos = this.camera.position
    const advance = config.speed * windFactor * dt
    const edgeBand = config.radiusMax * EDGE_FADE_FRACTION
    const edgeStart = config.radiusMax - edgeBand

    for (const cloud of clouds) {
      cloud.along += advance
      // Past the camera's trailing (downwind) edge -- recycle it just past the leading (upwind) edge on the opposite side.
      if (cloud.along > config.radiusMax) {
        this.randomizeCloud(cloud, config, false)
      }

      const edge = clamp01(1 - Math.max(0, Math.abs(cloud.along) - edgeStart) / edgeBand)
      const bobY = cloud.height + Math.sin(elapsed * BOB_SPEED + cloud.bobPhase) * config.bobAmount

      cloud.sprite.position.set(
        camPos.x + this.windVec.x * cloud.along + this.crossVec.x * cloud.cross,
        bobY,
        camPos.z + this.windVec.z * cloud.along + this.crossVec.z * cloud.cross
      )

      cloud.material.opacity = cover * config.opacityMul * edge * cloud.opacityJitter
      cloud.material.rotation = cloud.rotPhase + elapsed * cloud.rotSpeed
      cloud.material.color.copy(color).multiplyScalar(cloud.colorJitter)
    }
  }

  /** Recomputes the shared per-frame cloud tint from condition, sun altitude/color/intensity and hemisphere ambient. */
  private updateTint(params: SceneParams): void {
    const altitude = clamp(params.sunAltitude, -1, 1)
    // Peaks when the sun sits right on the horizon (sunrise or sunset).
    const sunsetT = clamp01(1 - Math.abs(altitude) * 2.4)

    if (params.condition === 'thunderstorm') {
      // Dark, moody, desaturated blue-grey -- barely any warm bleed even near the horizon.
      this.tintColor.set(0x3a4048)
      this.tintColor.lerp(this.hex(0x585f6c), sunsetT * 0.2)
    } else if (params.condition === 'clear' || params.condition === 'partly-cloudy') {
      // Bright fluffy white, warmed toward orange at sunrise/sunset.
      this.tintColor.set(0xffffff)
      this.tintColor.lerp(this.hex(0xffb37a), sunsetT * 0.55)
    } else {
      // Overcast / fog / drizzle / rain / snow: neutral pale-grey deck.
      this.tintColor.set(0xe1e5eb)
      this.tintColor.lerp(this.hex(0xe8b98c), sunsetT * 0.3)
    }

    // Pick up the actual directional-light color -- Sky already warms/cools and dims it per time of day.
    this.tintColor.lerp(this.sunLight.color, 0.28)

    const intensityFactor = clamp01(this.sunLight.intensity / 2.4)
    const nightFloor = params.isDay ? 0.55 : 0.2
    this.tintColor.multiplyScalar(lerp(nightFloor, 1, intensityFactor))

    // Faint ambient fill so night clouds are never pure black.
    this.ambientFill.copy(this.hemiLight.color).multiplyScalar(0.06)
    this.tintColor.add(this.ambientFill)
  }

  private disposeLayer(layer: CloudLayer): void {
    this.scene.remove(layer.group)
    for (const cloud of layer.clouds) {
      cloud.material.dispose()
    }
  }

  /** Sets the shared scratch Color to `value` and returns it, to avoid per-call allocation in lerp chains. */
  private hex(value: number): THREE.Color {
    return this.scratchHex.set(value)
  }
}
