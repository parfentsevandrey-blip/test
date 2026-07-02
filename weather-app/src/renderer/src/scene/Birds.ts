import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { clamp01, lerp } from '../utils/math'

/** Flocks spawn on a circle of this radius and fly a chord across it. */
const SPAWN_RADIUS = 240
/** Cruise altitude band (world units) — well above the terrain, under the cloud deck. */
const HEIGHT_MIN = 55
const HEIGHT_MAX = 115
/** Randomized pause between flock spawns, in seconds. */
const SPAWN_INTERVAL_MIN = 18
const SPAWN_INTERVAL_MAX = 45
/** First flock appears a little sooner so a fresh scene feels alive quickly. */
const FIRST_SPAWN_MIN = 8
const FIRST_SPAWN_MAX = 25

/** Hard pool size: geometry for this many flocks/birds is built once, up front. */
const MAX_FLOCK_SLOTS = 2
const MAX_BIRDS_PER_FLOCK = 9
/** 4 triangles per bird (two wings + two body triangles), non-indexed. */
const TRIS_PER_BIRD = 4
const VERTS_PER_BIRD = TRIS_PER_BIRD * 3

/** Fraction of the path spent fading in / fading out near the spawn/despawn edges. */
const EDGE_FADE_FRACTION = 0.12
/** Exponential fade rate when weather conditions flip mid-flight (~2s to settle). */
const CONDITION_FADE_RATE = 1.4

interface FlockTier {
  maxFlocks: number
  minBirds: number
  maxBirds: number
}

const TIER_BY_QUALITY: Record<Quality, FlockTier> = {
  low: { maxFlocks: 1, minBirds: 5, maxBirds: 7 },
  medium: { maxFlocks: 2, minBirds: 5, maxBirds: 8 },
  high: { maxFlocks: 2, minBirds: 6, maxBirds: 9 }
}

const VERTEX_SHADER = /* glsl */ `
  // Per-vertex: 0 on body/inner-wing vertices, 1 at wing tips (drives the flap rotation).
  attribute float aFlap;
  // xyz: bird offset within the flock, w: per-bird flap phase.
  attribute vec4 aBird;
  // x: flap angular speed, y: bob amplitude, z: bob phase, w: per-bird scale (wingspan).
  attribute vec4 aMisc;

  uniform float uTime;

  void main() {
    float scale = aMisc.w;
    vec3 p = position * scale;

    // Wingbeat: a sine with a touch of second harmonic reads as a quick
    // downstroke / slower recovery instead of a metronome.
    float beat = uTime * aMisc.x + aBird.w;
    float ang = (sin(beat) * 0.85 + sin(beat * 2.0) * 0.15) * 0.95;
    float s = sin(ang);
    float c = cos(ang);

    // Rotate wing-tip vertices about the forward (z) axis; both wings rise
    // together. The hinge sits at x ~ 0 so rotating about the origin is exact.
    p.y += abs(p.x) * s * aFlap;
    p.x = mix(p.x, p.x * c, aFlap);

    // The body heaves slightly against the stroke, plus a slow per-bird bob
    // so the formation never looks rigidly welded together.
    p.y -= s * 0.05 * scale * (1.0 - aFlap);
    p.y += sin(uTime * (aMisc.x * 0.31) + aMisc.z) * aMisc.y;

    vec3 flockPos = p + aBird.xyz;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(flockPos, 1.0);
  }
`

const FRAGMENT_SHADER = /* glsl */ `
  uniform vec3 uColor;
  uniform float uOpacity;

  void main() {
    gl_FragColor = vec4(uColor, uOpacity);
  }
`

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side fade curves. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

function randRange(min: number, max: number): number {
  return lerp(min, max, Math.random())
}

function randInt(min: number, max: number): number {
  return min + Math.floor(Math.random() * (max - min + 1))
}

/** All mutable per-flock flight state; every field lives for the app's lifetime. */
interface FlockSlot {
  mesh: THREE.Mesh
  geometry: THREE.BufferGeometry
  material: THREE.ShaderMaterial
  birdAttr: THREE.BufferAttribute
  miscAttr: THREE.BufferAttribute
  active: boolean
  start: THREE.Vector3
  dir: THREE.Vector3
  heading: number
  speed: number
  pathLength: number
  traveled: number
  /** Smoothed 0-1 weather-conditions fade so data refreshes never pop. */
  fade: number
  waveAmp: number
  waveFreq: number
  wavePhase: number
}

/**
 * Occasional flocks of birds crossing the daytime sky in a loose, flapping
 * V formation — spawning outside the visible radius on one side (biased
 * downwind), gliding a gentle undulating chord across the dome, and fading
 * out on the far side.
 *
 * Each flock is a single merged mesh (<= 36 triangles); wing flap and
 * per-bird bobbing run entirely in the vertex shader, so per-frame CPU work
 * is a handful of scalar ops and one position/rotation write per live flock.
 * Both flock slots are pre-allocated in the constructor and only toggled
 * visible; when conditions aren't met the effect idles at zero cost.
 */
export class Birds implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly tier: FlockTier
  private readonly slots: FlockSlot[] = []

  private spawnTimer = randRange(FIRST_SPAWN_MIN, FIRST_SPAWN_MAX)

  // Scratch objects reused every frame / every spawn -- never reallocated.
  private readonly scratchPos = new THREE.Vector3()
  private readonly scratchPerp = new THREE.Vector3()
  private readonly silhouetteColor = new THREE.Color()
  private readonly scratchColor = new THREE.Color()

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.tier = TIER_BY_QUALITY[ctx.quality]

    for (let f = 0; f < MAX_FLOCK_SLOTS; f++) {
      this.slots.push(this.buildFlockSlot())
    }
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const conditionsMet =
      params.isDay &&
      params.condition !== 'thunderstorm' &&
      params.condition !== 'snow' &&
      params.visibility > 0.5

    // --- Spawning ---------------------------------------------------------
    let activeCount = 0
    for (let i = 0; i < this.slots.length; i++) {
      if (this.slots[i].active) activeCount++
    }

    if (conditionsMet && activeCount < this.tier.maxFlocks) {
      this.spawnTimer -= dt
      if (this.spawnTimer <= 0) {
        for (let i = 0; i < this.slots.length; i++) {
          if (!this.slots[i].active) {
            this.spawnFlock(this.slots[i], params)
            break
          }
        }
        this.spawnTimer = randRange(SPAWN_INTERVAL_MIN, SPAWN_INTERVAL_MAX)
      }
    }

    // --- Silhouette tint: near-black, lifted slightly by haze and low sun --
    const duskLift = 1 - clamp01(params.sunAltitude * 2)
    this.silhouetteColor.set(0x12161d)
    this.silhouetteColor.lerp(this.scratchColor.set(0x2b2620), duskLift * 0.45)
    this.silhouetteColor.lerp(this.scratchColor.set(0x4d5a70), (1 - params.visibility) * 0.55)

    // --- Per-flock flight -------------------------------------------------
    for (let i = 0; i < this.slots.length; i++) {
      const slot = this.slots[i]
      if (!slot.active) continue

      slot.traveled += slot.speed * dt

      const fadeTarget = conditionsMet ? 1 : 0
      slot.fade += (fadeTarget - slot.fade) * (1 - Math.exp(-dt * CONDITION_FADE_RATE))

      if (slot.traveled >= slot.pathLength || (!conditionsMet && slot.fade < 0.01)) {
        slot.active = false
        slot.mesh.visible = false
        continue
      }

      this.scratchPos.copy(slot.dir).multiplyScalar(slot.traveled).add(slot.start)
      const wave = slot.traveled * slot.waveFreq + slot.wavePhase
      this.scratchPos.y += Math.sin(wave) * slot.waveAmp
      slot.mesh.position.copy(this.scratchPos)
      slot.mesh.rotation.y = slot.heading
      // Pitch gently with the slope of the height wave so climbs/dips read as intentional.
      slot.mesh.rotation.x = -Math.cos(wave) * slot.waveAmp * slot.waveFreq * 0.6

      const t = slot.traveled / slot.pathLength
      const edgeFade =
        smoothstep(0, EDGE_FADE_FRACTION, t) * (1 - smoothstep(1 - EDGE_FADE_FRACTION, 1, t))
      const opacity = slot.fade * edgeFade * 0.92

      slot.material.uniforms.uOpacity.value = opacity
      slot.material.uniforms.uTime.value = elapsed
      ;(slot.material.uniforms.uColor.value as THREE.Color).copy(this.silhouetteColor)
      slot.mesh.visible = opacity > 0.004
    }
  }

  dispose(): void {
    for (let i = 0; i < this.slots.length; i++) {
      const slot = this.slots[i]
      this.scene.remove(slot.mesh)
      slot.geometry.dispose()
      slot.material.dispose()
    }
    this.slots.length = 0
  }

  // ---------------------------------------------------------------------
  // Construction
  // ---------------------------------------------------------------------

  /** Builds one pooled flock: merged geometry for MAX_BIRDS_PER_FLOCK birds + its material/mesh. */
  private buildFlockSlot(): FlockSlot {
    const vertCount = MAX_BIRDS_PER_FLOCK * VERTS_PER_BIRD
    const positions = new Float32Array(vertCount * 3)
    const flap = new Float32Array(vertCount)
    const bird = new Float32Array(vertCount * 4)
    const misc = new Float32Array(vertCount * 4)

    // Bird-local silhouette, unit wingspan, nose toward +z. Written once;
    // only aBird/aMisc are rewritten on each spawn.
    // Layout per bird: [left wing tri, right wing tri, body front tri, body rear tri].
    //                     x       y      z     flapWeight
    const SHAPE: ReadonlyArray<readonly [number, number, number, number]> = [
      // Left wing: inner leading edge, inner trailing edge, swept-back tip.
      [-0.045, 0, 0.1, 0],
      [-0.045, 0, -0.1, 0],
      [-0.5, 0.03, -0.16, 1],
      // Right wing (mirror).
      [0.045, 0, 0.1, 0],
      [0.045, 0, -0.1, 0],
      [0.5, 0.03, -0.16, 1],
      // Body: slim diamond (nose, shoulders, tail).
      [0, 0.005, 0.3, 0],
      [-0.05, 0, 0.02, 0],
      [0.05, 0, 0.02, 0],
      [-0.05, 0, 0.02, 0],
      [0, 0.005, -0.27, 0],
      [0.05, 0, 0.02, 0]
    ]

    for (let b = 0; b < MAX_BIRDS_PER_FLOCK; b++) {
      for (let v = 0; v < VERTS_PER_BIRD; v++) {
        const idx = b * VERTS_PER_BIRD + v
        const src = SHAPE[v]
        positions[idx * 3] = src[0]
        positions[idx * 3 + 1] = src[1]
        positions[idx * 3 + 2] = src[2]
        flap[idx] = src[3]
        // Sane defaults so the buffer is fully initialized before first spawn.
        misc[idx * 4] = 7
        misc[idx * 4 + 3] = 1.6
      }
    }

    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geometry.setAttribute('aFlap', new THREE.BufferAttribute(flap, 1))
    const birdAttr = new THREE.BufferAttribute(bird, 4)
    birdAttr.setUsage(THREE.DynamicDrawUsage)
    geometry.setAttribute('aBird', birdAttr)
    const miscAttr = new THREE.BufferAttribute(misc, 4)
    miscAttr.setUsage(THREE.DynamicDrawUsage)
    geometry.setAttribute('aMisc', miscAttr)

    const material = new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uOpacity: { value: 0 },
        uColor: { value: new THREE.Color(0x12161d) }
      },
      vertexShader: VERTEX_SHADER,
      fragmentShader: FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide
    })

    const mesh = new THREE.Mesh(geometry, material)
    mesh.visible = false
    // Flock offsets live in a shader attribute, so the geometry's bounding
    // volume can't reflect them; with only two tiny meshes, skipping culling
    // is cheaper and always correct.
    mesh.frustumCulled = false
    this.scene.add(mesh)

    return {
      mesh,
      geometry,
      material,
      birdAttr,
      miscAttr,
      active: false,
      start: new THREE.Vector3(),
      dir: new THREE.Vector3(),
      heading: 0,
      speed: 12,
      pathLength: SPAWN_RADIUS * 2,
      traveled: 0,
      fade: 0,
      waveAmp: 2,
      waveFreq: 0.02,
      wavePhase: 0
    }
  }

  // ---------------------------------------------------------------------
  // Spawning (rare; mutates pre-allocated buffers in place, no `new`)
  // ---------------------------------------------------------------------

  private spawnFlock(slot: FlockSlot, params: SceneParams): void {
    // Fly roughly downwind with some scatter so paths vary run to run.
    const heading = params.windDirectionRad + (Math.random() - 0.5) * 1.2
    slot.heading = heading
    slot.dir.set(Math.sin(heading), 0, Math.cos(heading))
    this.scratchPerp.set(Math.cos(heading), 0, -Math.sin(heading))

    // Chord across the spawn circle, offset sideways so flocks don't always
    // pass dead-center overhead.
    const lateral = (Math.random() - 0.5) * 160
    const halfChord = Math.sqrt(SPAWN_RADIUS * SPAWN_RADIUS - lateral * lateral)
    slot.start
      .copy(this.scratchPerp)
      .multiplyScalar(lateral)
      .addScaledVector(slot.dir, -halfChord)
    slot.start.y = randRange(HEIGHT_MIN, HEIGHT_MAX)

    slot.pathLength = halfChord * 2
    slot.traveled = 0
    slot.speed = randRange(10, 16) * (1 + params.windSpeed * 0.5)
    slot.fade = 1
    slot.waveAmp = randRange(1.5, 4)
    slot.waveFreq = randRange(0.015, 0.03)
    slot.wavePhase = Math.random() * Math.PI * 2

    // --- Rewrite per-bird attributes for a fresh formation -----------------
    const birdCount = randInt(this.tier.minBirds, this.tier.maxBirds)
    const bird = slot.birdAttr.array as Float32Array
    const misc = slot.miscAttr.array as Float32Array

    for (let b = 0; b < birdCount; b++) {
      // Loose V: the leader up front, followers staggered back on alternating
      // sides with per-bird jitter so it never looks stamped out.
      let ox = 0
      let oy = 0
      let oz = 0
      if (b > 0) {
        const side = b % 2 === 1 ? -1 : 1
        const rank = Math.ceil(b / 2)
        ox = side * rank * 2.3 + (Math.random() - 0.5) * 1.4
        oy = (Math.random() - 0.5) * 1.6
        oz = -rank * 2.0 + (Math.random() - 0.5) * 1.2
      }

      const phase = Math.random() * Math.PI * 2
      const flapSpeed = randRange(5.5, 8.5)
      const bobAmp = randRange(0.15, 0.45)
      const bobPhase = Math.random() * Math.PI * 2
      // Wingspan 1.2-2.2 world units; the leader trends slightly larger.
      const scale = randRange(1.2, 2.2) * (b === 0 ? 1.08 : 1)

      for (let v = 0; v < VERTS_PER_BIRD; v++) {
        const idx = (b * VERTS_PER_BIRD + v) * 4
        bird[idx] = ox
        bird[idx + 1] = oy
        bird[idx + 2] = oz
        bird[idx + 3] = phase
        misc[idx] = flapSpeed
        misc[idx + 1] = bobAmp
        misc[idx + 2] = bobPhase
        misc[idx + 3] = scale
      }
    }

    slot.birdAttr.needsUpdate = true
    slot.miscAttr.needsUpdate = true
    slot.geometry.setDrawRange(0, birdCount * VERTS_PER_BIRD)

    slot.material.uniforms.uOpacity.value = 0
    slot.active = true
    slot.mesh.visible = false // becomes visible once edge fade lifts opacity
  }
}
