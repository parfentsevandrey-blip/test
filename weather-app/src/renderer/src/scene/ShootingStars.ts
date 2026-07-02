import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { makeRadialTexture } from './textures'
import { clamp01, lerp } from '../utils/math'

/** Meteors streak just inside the star shell (stars sit at 400-440, sky dome at 450). */
const RADIUS_MIN = 380
const RADIUS_MAX = 420
/** Every point of a meteor's path stays at least this high, well clear of the camera's near field. */
const MIN_PATH_HEIGHT = 60
/** Spawn elevation window (sin of elevation angle): high in the dome, never skimming the horizon. */
const SPAWN_SIN_ELEVATION_MIN = 0.35
const SPAWN_SIN_ELEVATION_MAX = 0.8

/** Seconds between meteors, re-randomized after every spawn. */
const SPAWN_INTERVAL_MIN = 7
const SPAWN_INTERVAL_MAX = 22
/** Chance a meteor is chased by a quick second one. */
const DOUBLE_CHANCE = 0.15

const LIFE_MIN = 0.7
const LIFE_MAX = 1.3

/** Two meteors cover the worst case: a primary still fading while its double streaks. */
const POOL_SIZE = 2

/** Cross-sections in the trail ribbon, scaled a little by quality tier. */
const TRAIL_SEGMENTS_BY_QUALITY: Record<Quality, number> = {
  low: 10,
  medium: 14,
  high: 18
}

const TRAIL_VERTEX_SHADER = /* glsl */ `
  attribute float aAlpha;

  varying float vAlpha;

  void main() {
    vAlpha = aAlpha;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const TRAIL_FRAGMENT_SHADER = /* glsl */ `
  uniform vec3 uHeadColor;
  uniform vec3 uTailColor;
  uniform float uOpacity;

  varying float vAlpha;

  void main() {
    float alpha = vAlpha * uOpacity;
    if (alpha <= 0.002) discard;
    // Whiter and hotter toward the head, cooler blue toward the tail tip.
    vec3 color = mix(uTailColor, uHeadColor, pow(vAlpha, 0.55));
    gl_FragColor = vec4(color, alpha);
  }
`

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side envelopes. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/** World up, used to build tangent frames on the sky sphere. Never mutated. */
const UP = new THREE.Vector3(0, 1, 0)

/**
 * One pooled meteor: a camera-facing tapered ribbon trail plus a radial
 * glow sprite for the head. All geometry/material objects are built once
 * in the ShootingStars constructor and reused for every flight.
 */
class Meteor {
  active = false
  age = 0
  life = 1
  travelDist = 100
  maxTrail = 40
  width = 1.3
  headScale = 5.5
  seed = 0
  readonly start = new THREE.Vector3()
  readonly dir = new THREE.Vector3(0, -1, 0)

  constructor(
    readonly geometry: THREE.BufferGeometry,
    readonly positions: Float32Array,
    readonly positionAttr: THREE.BufferAttribute,
    readonly trailMaterial: THREE.ShaderMaterial,
    readonly trail: THREE.Mesh,
    readonly headMaterial: THREE.SpriteMaterial,
    readonly head: THREE.Sprite
  ) {}
}

/**
 * Occasional meteors streaking across clear night skies. A meteor spawns
 * every ~7-22s (15% of the time chased by a quick double), travels a
 * randomized diagonal high on the sky dome over 0.7-1.3s while its trail
 * stretches out behind it, then the whole streak fades away. Idles at
 * effectively zero cost whenever the sky is bright, cloudy or unsettled.
 */
export class ShootingStars implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly camera: THREE.PerspectiveCamera

  private readonly headTexture: THREE.CanvasTexture
  private readonly pool: Meteor[]
  private readonly trailSegments: number
  /** Pre-baked half-width taper per cross-section (head -> tail), scaled by each meteor's width. */
  private readonly taper: Float32Array

  /** Smoothed 0-1 eligibility so weather-data refreshes never pop a meteor in or out. */
  private fade = 0
  private spawnTimer: number
  /** Countdown to the second meteor of a double; 0 when none is pending. */
  private doubleTimer = 0

  /** Where the most recent primary spawned, so a double streaks alongside it. */
  private readonly lastStart = new THREE.Vector3(0, RADIUS_MIN, 0)
  private readonly lastDir = new THREE.Vector3(1, -0.5, 0).normalize()

  // Scratch objects reused every frame / every spawn -- never reallocated.
  private readonly scratchHead = new THREE.Vector3()
  private readonly scratchView = new THREE.Vector3()
  private readonly scratchSide = new THREE.Vector3()
  private readonly scratchNormal = new THREE.Vector3()
  private readonly scratchEast = new THREE.Vector3()
  private readonly scratchDown = new THREE.Vector3()

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.camera = ctx.camera

    const segments = TRAIL_SEGMENTS_BY_QUALITY[ctx.quality]
    this.trailSegments = segments

    this.taper = new Float32Array(segments + 1)
    for (let i = 0; i <= segments; i++) {
      const s = i / segments
      this.taper[i] = Math.pow(1 - s, 0.8) * 0.5
    }

    // Hot white core bleeding into a cool blue halo.
    this.headTexture = makeRadialTexture('rgba(255,255,255,1)', 'rgba(150,185,255,0)', 64, 0.02)

    this.pool = []
    for (let p = 0; p < POOL_SIZE; p++) {
      const vertexCount = (segments + 1) * 2
      const positions = new Float32Array(vertexCount * 3)
      const alphas = new Float32Array(vertexCount)
      const indices = new Uint16Array(segments * 6)

      for (let i = 0; i <= segments; i++) {
        const s = i / segments
        // Per-vertex brightness fades toward the tail tip.
        const alpha = Math.pow(1 - s, 1.6)
        alphas[i * 2] = alpha
        alphas[i * 2 + 1] = alpha
      }
      for (let i = 0; i < segments; i++) {
        const a = i * 2
        const k = i * 6
        indices[k] = a
        indices[k + 1] = a + 1
        indices[k + 2] = a + 2
        indices[k + 3] = a + 1
        indices[k + 4] = a + 3
        indices[k + 5] = a + 2
      }

      const geometry = new THREE.BufferGeometry()
      const positionAttr = new THREE.BufferAttribute(positions, 3)
      positionAttr.setUsage(THREE.DynamicDrawUsage)
      geometry.setAttribute('position', positionAttr)
      geometry.setAttribute('aAlpha', new THREE.BufferAttribute(alphas, 1))
      geometry.setIndex(new THREE.BufferAttribute(indices, 1))

      const trailMaterial = new THREE.ShaderMaterial({
        uniforms: {
          uHeadColor: { value: new THREE.Color(0xf2f7ff) },
          uTailColor: { value: new THREE.Color(0x7fa8ff) },
          uOpacity: { value: 0 }
        },
        vertexShader: TRAIL_VERTEX_SHADER,
        fragmentShader: TRAIL_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide,
        blending: THREE.AdditiveBlending
      })

      const trail = new THREE.Mesh(geometry, trailMaterial)
      trail.visible = false
      // Positions mutate every frame; skip stale bounding-sphere culling.
      trail.frustumCulled = false
      this.scene.add(trail)

      const headMaterial = new THREE.SpriteMaterial({
        map: this.headTexture,
        color: 0xeaf2ff,
        transparent: true,
        opacity: 0,
        depthWrite: false,
        fog: false,
        blending: THREE.AdditiveBlending
      })
      const head = new THREE.Sprite(headMaterial)
      head.visible = false
      head.frustumCulled = false
      this.scene.add(head)

      this.pool.push(new Meteor(geometry, positions, positionAttr, trailMaterial, trail, headMaterial, head))
    }

    this.spawnTimer = lerp(SPAWN_INTERVAL_MIN, SPAWN_INTERVAL_MAX, Math.random())
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const eligible =
      !params.isDay &&
      params.cloudCover < 0.45 &&
      (params.condition === 'clear' || params.condition === 'partly-cloudy')

    // ~1.5-2s exponential ease whenever the weather snapshot flips state.
    const smoothing = 1 - Math.exp(-dt * 2.2)
    this.fade += ((eligible ? 1 : 0) - this.fade) * smoothing

    let anyActive = false
    for (let i = 0; i < POOL_SIZE; i++) {
      const meteor = this.pool[i]
      if (meteor !== undefined && meteor.active) anyActive = true
    }

    // Fully idle: nothing in flight and no permission to launch more.
    if (!eligible && !anyActive) {
      this.doubleTimer = 0
      return
    }

    if (eligible) {
      this.spawnTimer -= dt
      if (this.spawnTimer <= 0) {
        const free = this.findFree()
        if (free !== null) {
          this.spawnPrimary(free)
          if (Math.random() < DOUBLE_CHANCE) {
            this.doubleTimer = lerp(0.25, 0.6, Math.random())
          }
        }
        this.spawnTimer = lerp(SPAWN_INTERVAL_MIN, SPAWN_INTERVAL_MAX, Math.random())
      }
      if (this.doubleTimer > 0) {
        this.doubleTimer -= dt
        if (this.doubleTimer <= 0) {
          const free = this.findFree()
          if (free !== null) this.spawnDouble(free)
          this.doubleTimer = 0
        }
      }
    } else {
      // Conditions failed mid-flight: let anything airborne finish, spawn no more.
      this.doubleTimer = 0
    }

    for (let i = 0; i < POOL_SIZE; i++) {
      const meteor = this.pool[i]
      if (meteor !== undefined && meteor.active) this.advanceMeteor(meteor, dt, elapsed)
    }
  }

  dispose(): void {
    for (let i = 0; i < POOL_SIZE; i++) {
      const meteor = this.pool[i]
      if (meteor === undefined) continue
      this.scene.remove(meteor.trail)
      this.scene.remove(meteor.head)
      meteor.geometry.dispose()
      meteor.trailMaterial.dispose()
      meteor.headMaterial.dispose()
    }
    this.pool.length = 0
    this.headTexture.dispose()
  }

  private findFree(): Meteor | null {
    for (let i = 0; i < POOL_SIZE; i++) {
      const meteor = this.pool[i]
      if (meteor !== undefined && !meteor.active) return meteor
    }
    return null
  }

  /** Launches a meteor high on the dome, biased toward the camera's forward azimuth so it's actually seen. */
  private spawnPrimary(meteor: Meteor): void {
    this.camera.getWorldDirection(this.scratchView)
    const baseAzimuth = Math.atan2(this.scratchView.x, this.scratchView.z)
    const azimuth = baseAzimuth + (Math.random() * 2 - 1) * 1.2

    const sinEl = lerp(SPAWN_SIN_ELEVATION_MIN, SPAWN_SIN_ELEVATION_MAX, Math.random())
    const cosEl = Math.sqrt(Math.max(0, 1 - sinEl * sinEl))
    const radius = lerp(RADIUS_MIN, RADIUS_MAX, Math.random())
    meteor.start
      .set(Math.sin(azimuth) * cosEl, sinEl, Math.cos(azimuth) * cosEl)
      .multiplyScalar(radius)

    // Tangent frame on the sky sphere at the spawn point: `east` runs level
    // around the dome, `down` slides toward the horizon.
    const normal = this.scratchNormal.copy(meteor.start).normalize()
    const east = this.scratchEast.crossVectors(UP, normal).normalize()
    const down = this.scratchDown.crossVectors(east, normal).normalize()

    // Randomized diagonal: 20-57 degrees below level, either sideways direction.
    const sideSign = Math.random() < 0.5 ? -1 : 1
    const downAngle = lerp(0.35, 1.0, Math.random())
    meteor.dir
      .copy(east)
      .multiplyScalar(Math.cos(downAngle) * sideSign)
      .addScaledVector(down, Math.sin(downAngle))
      .normalize()

    this.lastStart.copy(meteor.start)
    this.lastDir.copy(meteor.dir)
    this.finishSpawn(meteor)
  }

  /** The quick second meteor of a double: streaks near-parallel alongside the last primary. */
  private spawnDouble(meteor: Meteor): void {
    meteor.start.set(
      this.lastStart.x + (Math.random() - 0.5) * 70,
      this.lastStart.y + (Math.random() - 0.5) * 40,
      this.lastStart.z + (Math.random() - 0.5) * 70
    )
    const radius = lerp(RADIUS_MIN, RADIUS_MAX, Math.random())
    meteor.start.normalize().multiplyScalar(radius)

    meteor.dir.copy(this.lastDir)
    meteor.dir.x += (Math.random() - 0.5) * 0.24
    meteor.dir.y += (Math.random() - 0.5) * 0.12
    meteor.dir.z += (Math.random() - 0.5) * 0.24
    meteor.dir.normalize()

    this.finishSpawn(meteor)
  }

  /** Rolls per-flight stats shared by primaries and doubles, then activates the meteor at zero opacity. */
  private finishSpawn(meteor: Meteor): void {
    meteor.life = lerp(LIFE_MIN, LIFE_MAX, Math.random())
    const speed = lerp(95, 140, Math.random())
    meteor.travelDist = speed * meteor.life

    // Keep the entire path above MIN_PATH_HEIGHT by shortening the run if needed.
    if (meteor.dir.y < -1e-3) {
      const maxDist = (meteor.start.y - MIN_PATH_HEIGHT) / -meteor.dir.y
      if (meteor.travelDist > maxDist) meteor.travelDist = Math.max(30, maxDist)
    }

    meteor.maxTrail = Math.min(meteor.travelDist * 0.5, lerp(30, 55, Math.random()))
    meteor.width = lerp(1.0, 1.7, Math.random())
    meteor.headScale = lerp(4.5, 7, Math.random())
    meteor.seed = Math.random() * 100
    meteor.age = 0
    meteor.active = true

    // Wake at zero opacity; the first advance() eases it in from the envelope.
    meteor.trailMaterial.uniforms.uOpacity.value = 0
    meteor.headMaterial.opacity = 0
    meteor.head.position.copy(meteor.start)
    meteor.trail.visible = true
    meteor.head.visible = true
  }

  private advanceMeteor(meteor: Meteor, dt: number, elapsed: number): void {
    meteor.age += dt
    if (meteor.age >= meteor.life) {
      meteor.active = false
      meteor.trail.visible = false
      meteor.head.visible = false
      return
    }

    const t = meteor.age / meteor.life
    // Quick flare-in, hold, then a long graceful burn-out.
    const envelope = smoothstep(0, 0.12, t) * (1 - smoothstep(0.55, 1, t))
    const opacity = envelope * this.fade

    // Head slides linearly along the streak's straight diagonal.
    const head = this.scratchHead
      .copy(meteor.dir)
      .multiplyScalar(meteor.travelDist * t)
      .add(meteor.start)

    // Ribbon side axis: perpendicular to travel and view ray so the thin
    // strip always faces the camera.
    this.scratchView.copy(head).sub(this.camera.position)
    const side = this.scratchSide.crossVectors(meteor.dir, this.scratchView)
    if (side.lengthSq() < 1e-6) side.set(0, 1, 0)
    side.normalize()

    // Trail stretches out over the first ~45% of life, then rides along.
    const trailLen = meteor.maxTrail * smoothstep(0, 0.45, t)

    const positions = meteor.positions
    const segments = this.trailSegments
    const dirX = meteor.dir.x
    const dirY = meteor.dir.y
    const dirZ = meteor.dir.z
    for (let i = 0; i <= segments; i++) {
      const back = (trailLen * i) / segments
      const cx = head.x - dirX * back
      const cy = head.y - dirY * back
      const cz = head.z - dirZ * back
      const halfWidth = meteor.width * this.taper[i]
      const ox = side.x * halfWidth
      const oy = side.y * halfWidth
      const oz = side.z * halfWidth
      const k = i * 6
      positions[k] = cx + ox
      positions[k + 1] = cy + oy
      positions[k + 2] = cz + oz
      positions[k + 3] = cx - ox
      positions[k + 4] = cy - oy
      positions[k + 5] = cz - oz
    }
    meteor.positionAttr.needsUpdate = true
    meteor.trailMaterial.uniforms.uOpacity.value = opacity

    // Head outshines the trail, with a subtle high-frequency shimmer.
    meteor.head.position.copy(head)
    meteor.headMaterial.opacity = Math.min(1, opacity * 1.6)
    const shimmer = 1 + 0.15 * Math.sin(elapsed * 47 + meteor.seed)
    meteor.head.scale.setScalar(meteor.headScale * shimmer * (0.6 + 0.4 * envelope))
  }
}
