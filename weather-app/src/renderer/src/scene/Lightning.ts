import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { makeRadialTexture } from './textures'
import { clamp01, degToRad, lerp } from '../utils/math'

/** Zig-zag vertices along the main channel (2^3 + 1, built by 3 rounds of midpoint displacement). */
const MAIN_POINTS = 9
/** Zig-zag vertices per forked branch (2^2 + 1, 2 rounds of midpoint displacement). */
const BRANCH_POINTS = 5
/** Left edge / bright center / right edge per path point, so the ribbon glows brightest along its spine. */
const VERTS_PER_POINT = 3
/** Two triangle-strip quads (left-center, center-right) per path segment. */
const INDICES_PER_SEGMENT = 12

/** How many optional forks the bolt can grow, scaled down a little on low quality. */
const MAX_BRANCHES_BY_QUALITY: Record<Quality, number> = {
  low: 1,
  medium: 2,
  high: 2
}

/** Seconds between strikes while thunderActive -- re-rolled after every strike so it never settles into a beat. */
const STRIKE_INTERVAL_MIN = 4
const STRIKE_INTERVAL_MAX = 14

/** Total lifetime of a single strike's flicker sequence. */
const FLASH_DURATION_MIN = 0.15
const FLASH_DURATION_MAX = 0.35
const FLICKER_MIN = 2
const FLICKER_MAX = 4
/** Gaussian half-width (seconds) of one flicker spike -- narrow enough that several read as distinct strobes. */
const PULSE_SIGMA = 0.022

/** Spherical placement of the bolt's anchor point, "a random direction in the upper sky". */
const MIN_ELEVATION = degToRad(22)
const MAX_ELEVATION = degToRad(62)
const MIN_DISTANCE = 95
const MAX_DISTANCE = 165

/** Shape of the main channel in the bolt's own local (right, up) plane. */
const MAIN_HEIGHT_MIN = 26
const MAIN_HEIGHT_MAX = 48
const MAIN_DRIFT_MIN = -22
const MAIN_DRIFT_MAX = 22
/** Midpoint-displacement jitter, as a fraction of each segment's length; halves every subdivision round. */
const ROUGHNESS = 0.55

const MAIN_BASE_WIDTH_MIN = 1.8
const MAIN_BASE_WIDTH_MAX = 3.0
const MAIN_TIP_WIDTH_MIN = 0.35
const MAIN_TIP_WIDTH_MAX = 0.7

const BRANCH_DIM = 0.6
const BRANCH_SPREAD = 18
const BRANCH_LENGTH_FRAC_MIN = 0.35
const BRANCH_LENGTH_FRAC_MAX = 0.65

const GLOW_SCALE_MIN = 130
const GLOW_SCALE_MAX = 200
const GLOW_MAX_OPACITY = 0.55

/** Mild falloff (rather than the physically-correct inverse-square default) keeps the flash reliably bright at
 *  sky-scale distances without needing huge intensity numbers, matching this app's other lights' tuned scale. */
const LIGHT_DECAY = 1
const LIGHT_PEAK_MIN = 200
const LIGHT_PEAK_MAX = 380

const BOLT_VERTEX_SHADER = /* glsl */ `
  attribute float aCore;
  varying float vCore;

  void main() {
    vCore = aCore;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const BOLT_FRAGMENT_SHADER = /* glsl */ `
  uniform vec3 uColor;
  uniform float uOpacity;
  varying float vCore;

  void main() {
    float glow = pow(clamp(vCore, 0.0, 1.0), 1.6);
    float alpha = glow * uOpacity;
    if (alpha <= 0.003) {
      discard;
    }
    gl_FragColor = vec4(uColor, alpha);
  }
`

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side flicker envelope. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/**
 * Thunderstorm lightning: a randomly-timed jagged bolt (with optional forks) flashed in the upper sky,
 * paired with a dedicated THREE.PointLight that flickers a few times before fading out.
 *
 * Fully idle (no scene mutation beyond a cheap countdown) whenever `params.thunderActive` is false. Never
 * touches `ctx.sunLight` / `ctx.hemiLight` -- Sky owns those.
 */
export class Lightning implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly camera: THREE.PerspectiveCamera
  private readonly maxBranches: number

  private readonly positions: Float32Array
  private readonly cores: Float32Array
  private readonly geometry: THREE.BufferGeometry
  private readonly boltMaterial: THREE.ShaderMaterial
  private readonly bolt: THREE.Mesh

  private readonly glowTexture: THREE.CanvasTexture
  private readonly glowMaterial: THREE.SpriteMaterial
  private readonly glowSprite: THREE.Sprite

  private readonly light: THREE.PointLight

  // Scratch buffers/vectors reused every strike -- never reallocated in update().
  private readonly mainT: Float32Array
  private readonly mainS: Float32Array
  private readonly branchT = new Float32Array(BRANCH_POINTS)
  private readonly branchS = new Float32Array(BRANCH_POINTS)
  private readonly pulseTimes = new Float32Array(FLICKER_MAX)
  private readonly pulsePeaks = new Float32Array(FLICKER_MAX)

  private readonly origin = new THREE.Vector3()
  private readonly normal = new THREE.Vector3()
  private readonly right = new THREE.Vector3()
  private readonly up = new THREE.Vector3()
  private readonly worldUp = new THREE.Vector3(0, 1, 0)
  private readonly scratchCenter = new THREE.Vector3()
  private readonly scratchOffset = new THREE.Vector3()
  private readonly flashColor = new THREE.Color()
  private readonly coolColor = new THREE.Color(0xb9ccff)

  // Mutable strike-scheduling state.
  private active = false
  private strikeTimer = 0
  private activeDuration = 0
  private pulseCount = 0
  private peakLightIntensity = 0
  private nextStrikeIn: number

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.camera = ctx.camera
    this.maxBranches = MAX_BRANCHES_BY_QUALITY[ctx.quality]

    this.mainT = new Float32Array(MAIN_POINTS)
    this.mainS = new Float32Array(MAIN_POINTS)

    const totalPoints = MAIN_POINTS + this.maxBranches * BRANCH_POINTS
    const totalVerts = totalPoints * VERTS_PER_POINT
    const mainSegments = MAIN_POINTS - 1
    const branchSegments = BRANCH_POINTS - 1
    const totalSegments = mainSegments + this.maxBranches * branchSegments

    this.positions = new Float32Array(totalVerts * 3)
    this.cores = new Float32Array(totalVerts)
    const indices = new Uint16Array(totalSegments * INDICES_PER_SEGMENT)

    let idx = 0
    const addSegment = (p0: number, p1: number): void => {
      const b0 = p0 * VERTS_PER_POINT
      const b1 = p1 * VERTS_PER_POINT
      const l0 = b0
      const c0 = b0 + 1
      const r0 = b0 + 2
      const l1 = b1
      const c1 = b1 + 1
      const r1 = b1 + 2
      indices[idx++] = l0
      indices[idx++] = l1
      indices[idx++] = c0
      indices[idx++] = c0
      indices[idx++] = l1
      indices[idx++] = c1
      indices[idx++] = c0
      indices[idx++] = c1
      indices[idx++] = r0
      indices[idx++] = r0
      indices[idx++] = c1
      indices[idx++] = r1
    }
    for (let i = 0; i < mainSegments; i++) addSegment(i, i + 1)
    for (let b = 0; b < this.maxBranches; b++) {
      const offset = MAIN_POINTS + b * BRANCH_POINTS
      for (let i = 0; i < branchSegments; i++) addSegment(offset + i, offset + i + 1)
    }

    this.geometry = new THREE.BufferGeometry()
    this.geometry.setAttribute('position', new THREE.BufferAttribute(this.positions, 3))
    this.geometry.setAttribute('aCore', new THREE.BufferAttribute(this.cores, 1))
    this.geometry.setIndex(new THREE.BufferAttribute(indices, 1))

    this.boltMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uColor: { value: new THREE.Color(0xffffff) },
        uOpacity: { value: 0 }
      },
      vertexShader: BOLT_VERTEX_SHADER,
      fragmentShader: BOLT_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      depthTest: true,
      blending: THREE.AdditiveBlending,
      side: THREE.DoubleSide,
      fog: false
    })
    this.bolt = new THREE.Mesh(this.geometry, this.boltMaterial)
    this.bolt.visible = false
    this.bolt.frustumCulled = true
    this.scene.add(this.bolt)

    this.glowTexture = makeRadialTexture('rgba(255,255,255,1)', 'rgba(190,210,255,0)', 128, 0)
    this.glowMaterial = new THREE.SpriteMaterial({
      map: this.glowTexture,
      color: 0xffffff,
      transparent: true,
      depthWrite: false,
      fog: false,
      opacity: 0,
      blending: THREE.AdditiveBlending
    })
    this.glowSprite = new THREE.Sprite(this.glowMaterial)
    this.glowSprite.visible = false
    this.scene.add(this.glowSprite)

    // Dedicated light, intensity 0 while idle. Never touches ctx.sunLight / ctx.hemiLight.
    this.light = new THREE.PointLight(0xffffff, 0, 0, LIGHT_DECAY)
    this.scene.add(this.light)

    this.nextStrikeIn = lerp(STRIKE_INTERVAL_MIN, STRIKE_INTERVAL_MAX, Math.random())
  }

  update(dt: number, _elapsed: number, params: SceneParams): void {
    if (!params.thunderActive) {
      if (this.active || this.light.intensity !== 0) {
        this.resetIdle()
      }
      return
    }

    if (!this.active) {
      this.nextStrikeIn -= dt
      if (this.nextStrikeIn <= 0) {
        this.startStrike()
      }
      return
    }

    this.updateStrike(dt)
  }

  dispose(): void {
    this.scene.remove(this.bolt)
    this.geometry.dispose()
    this.boltMaterial.dispose()

    this.scene.remove(this.glowSprite)
    this.glowMaterial.dispose()
    this.glowTexture.dispose()

    this.scene.remove(this.light)
  }

  /** Rolls a brand-new jagged bolt (+ optional forks) and flicker sequence, and kicks the strike off. */
  private startStrike(): void {
    // -- Anchor + camera-facing basis for the bolt's flat zig-zag plane.
    const azimuth = Math.random() * Math.PI * 2
    const elevation = lerp(MIN_ELEVATION, MAX_ELEVATION, Math.random())
    const dist = lerp(MIN_DISTANCE, MAX_DISTANCE, Math.random())
    const horiz = Math.cos(elevation)
    this.origin.set(Math.sin(azimuth) * horiz * dist, Math.sin(elevation) * dist, Math.cos(azimuth) * horiz * dist)

    this.normal.copy(this.origin).sub(this.camera.position).normalize()
    this.right.crossVectors(this.worldUp, this.normal).normalize()
    this.up.crossVectors(this.normal, this.right).normalize()

    // -- Main channel: descends from the anchor with a random horizontal drift, jagged via midpoint displacement.
    const height = lerp(MAIN_HEIGHT_MIN, MAIN_HEIGHT_MAX, Math.random())
    const drift = lerp(MAIN_DRIFT_MIN, MAIN_DRIFT_MAX, Math.random())
    this.mainT[0] = 0
    this.mainS[0] = 0
    this.mainT[MAIN_POINTS - 1] = drift
    this.mainS[MAIN_POINTS - 1] = -height
    this.displacePath(this.mainT, this.mainS, MAIN_POINTS, ROUGHNESS)

    const baseWidth = lerp(MAIN_BASE_WIDTH_MIN, MAIN_BASE_WIDTH_MAX, Math.random())
    const tipWidth = lerp(MAIN_TIP_WIDTH_MIN, MAIN_TIP_WIDTH_MAX, Math.random())
    for (let i = 0; i < MAIN_POINTS; i++) {
      const frac = i / (MAIN_POINTS - 1)
      const width = lerp(baseWidth, tipWidth, frac) * lerp(0.8, 1.25, Math.random())
      const core = lerp(1, 0.3, frac)
      this.writeCrossSection(i, this.mainT, this.mainS, i, MAIN_POINTS, width / 2, core)
    }

    // -- Optional forks, peeling off the main channel partway down; unused slots collapse to nothing.
    const activeBranches = Math.floor(Math.random() * (this.maxBranches + 1))
    for (let b = 0; b < this.maxBranches; b++) {
      const branchBase = MAIN_POINTS + b * BRANCH_POINTS
      if (b < activeBranches) {
        const forkIndex = 2 + Math.floor(Math.random() * (MAIN_POINTS - 4))
        const forkT = this.mainT[forkIndex]
        const forkS = this.mainS[forkIndex]
        const lengthFrac = lerp(BRANCH_LENGTH_FRAC_MIN, BRANCH_LENGTH_FRAC_MAX, Math.random())
        this.branchT[0] = forkT
        this.branchS[0] = forkS
        this.branchT[BRANCH_POINTS - 1] = forkT + lerp(-BRANCH_SPREAD, BRANCH_SPREAD, Math.random())
        this.branchS[BRANCH_POINTS - 1] = forkS + (this.mainS[MAIN_POINTS - 1] - forkS) * lengthFrac
        this.displacePath(this.branchT, this.branchS, BRANCH_POINTS, ROUGHNESS * 0.8)

        const branchBaseWidth = tipWidth * lerp(0.6, 1.0, Math.random())
        for (let i = 0; i < BRANCH_POINTS; i++) {
          const frac = i / (BRANCH_POINTS - 1)
          const width = lerp(branchBaseWidth, 0.12, frac)
          const core = lerp(0.8, 0.15, frac) * BRANCH_DIM
          this.writeCrossSection(branchBase + i, this.branchT, this.branchS, i, BRANCH_POINTS, width / 2, core)
        }
      } else {
        this.branchT.fill(0)
        this.branchS.fill(0)
        for (let i = 0; i < BRANCH_POINTS; i++) {
          this.writeCrossSection(branchBase + i, this.branchT, this.branchS, i, BRANCH_POINTS, 0, 0)
        }
      }
    }

    this.geometry.attributes.position.needsUpdate = true
    this.geometry.attributes.aCore.needsUpdate = true
    this.geometry.computeBoundingSphere()

    // -- Flicker envelope: 2-4 rapid pulses inside a short, randomized window.
    this.activeDuration = lerp(FLASH_DURATION_MIN, FLASH_DURATION_MAX, Math.random())
    this.pulseCount = FLICKER_MIN + Math.floor(Math.random() * (FLICKER_MAX - FLICKER_MIN + 1))
    const avgSpacing = (this.activeDuration * 0.65) / this.pulseCount
    let t = 0
    for (let i = 0; i < this.pulseCount; i++) {
      this.pulseTimes[i] = Math.min(t, this.activeDuration * 0.7)
      this.pulsePeaks[i] = i === 0 ? 1 : lerp(0.4, 0.85, Math.random())
      t += avgSpacing * lerp(0.6, 1.4, Math.random())
    }
    this.strikeTimer = 0
    this.active = true
    this.peakLightIntensity = lerp(LIGHT_PEAK_MIN, LIGHT_PEAK_MAX, Math.random())

    this.flashColor.set(0xffffff).lerp(this.coolColor, Math.random())
    this.light.color.copy(this.flashColor)
    this.light.position.copy(this.origin)
    ;(this.boltMaterial.uniforms.uColor.value as THREE.Color).copy(this.flashColor)
    this.glowMaterial.color.copy(this.flashColor)
    this.glowSprite.position.copy(this.origin)
    this.glowSprite.scale.setScalar(lerp(GLOW_SCALE_MIN, GLOW_SCALE_MAX, Math.random()))

    this.bolt.visible = true
    this.glowSprite.visible = true
  }

  /** Advances the current strike's flicker envelope and drives the light / bolt / glow from it. */
  private updateStrike(dt: number): void {
    this.strikeTimer += dt
    const t = this.strikeTimer
    if (t >= this.activeDuration) {
      this.endStrike()
      return
    }

    let brightness = 0
    for (let i = 0; i < this.pulseCount; i++) {
      const dtp = t - this.pulseTimes[i]
      if (dtp < 0) continue
      const pulse = this.pulsePeaks[i] * Math.exp(-(dtp * dtp) / (2 * PULSE_SIGMA * PULSE_SIGMA))
      if (pulse > brightness) brightness = pulse
    }
    const fadeStart = this.activeDuration * 0.78
    const fade = 1 - smoothstep(fadeStart, this.activeDuration, t)
    brightness = clamp01(brightness * fade)

    this.light.intensity = brightness * this.peakLightIntensity
    this.boltMaterial.uniforms.uOpacity.value = Math.min(1, brightness * 1.15)
    this.glowMaterial.opacity = brightness * GLOW_MAX_OPACITY
  }

  private endStrike(): void {
    this.active = false
    this.bolt.visible = false
    this.glowSprite.visible = false
    this.light.intensity = 0
    this.boltMaterial.uniforms.uOpacity.value = 0
    this.glowMaterial.opacity = 0
    this.nextStrikeIn = lerp(STRIKE_INTERVAL_MIN, STRIKE_INTERVAL_MAX, Math.random())
  }

  /** Snaps everything back to fully idle -- used whenever thunderActive drops out mid-strike or between strikes. */
  private resetIdle(): void {
    this.active = false
    this.strikeTimer = 0
    this.bolt.visible = false
    this.glowSprite.visible = false
    this.light.intensity = 0
    this.boltMaterial.uniforms.uOpacity.value = 0
    this.glowMaterial.opacity = 0
    this.nextStrikeIn = lerp(STRIKE_INTERVAL_MIN, STRIKE_INTERVAL_MAX, Math.random())
  }

  /**
   * In-place fractal midpoint displacement over a fixed-size (t, s) point buffer: `t[0]`/`s[0]` and
   * `t[count-1]`/`s[count-1]` must already hold the path's endpoints. `count - 1` must be a power of two.
   */
  private displacePath(t: Float32Array, s: Float32Array, count: number, roughness: number): void {
    let stride = count - 1
    let amp = roughness
    while (stride > 1) {
      const half = stride / 2
      for (let i = half; i < count - 1; i += stride) {
        const a = i - half
        const b = i + half
        const segT = t[b] - t[a]
        const segS = s[b] - s[a]
        const segLen = Math.hypot(segT, segS) || 1
        const midT = (t[a] + t[b]) / 2
        const midS = (s[a] + s[b]) / 2
        const offset = (Math.random() * 2 - 1) * amp * segLen
        t[i] = midT + (-segS / segLen) * offset
        s[i] = midS + (segT / segLen) * offset
      }
      stride = half
      amp *= 0.55
    }
  }

  /**
   * Writes the 3 cross-section vertices (left edge / bright center / right edge) for local path point `i`
   * (out of `count`) into global point slot `globalIndex` of the shared position/core buffers.
   */
  private writeCrossSection(
    globalIndex: number,
    t: Float32Array,
    s: Float32Array,
    i: number,
    count: number,
    halfWidth: number,
    core: number
  ): void {
    const iPrev = i > 0 ? i - 1 : i
    const iNext = i < count - 1 ? i + 1 : i
    const dT = t[iNext] - t[iPrev]
    const dS = s[iNext] - s[iPrev]
    const segLen = Math.hypot(dT, dS) || 1
    const perpT = -dS / segLen
    const perpS = dT / segLen

    this.scratchCenter.copy(this.origin).addScaledVector(this.right, t[i]).addScaledVector(this.up, s[i])
    this.scratchOffset
      .copy(this.right)
      .multiplyScalar(perpT)
      .addScaledVector(this.up, perpS)
      .multiplyScalar(halfWidth)

    const vBase = globalIndex * VERTS_PER_POINT
    const leftOff = vBase * 3
    const centerOff = (vBase + 1) * 3
    const rightOff = (vBase + 2) * 3

    this.positions[centerOff] = this.scratchCenter.x
    this.positions[centerOff + 1] = this.scratchCenter.y
    this.positions[centerOff + 2] = this.scratchCenter.z

    this.positions[leftOff] = this.scratchCenter.x - this.scratchOffset.x
    this.positions[leftOff + 1] = this.scratchCenter.y - this.scratchOffset.y
    this.positions[leftOff + 2] = this.scratchCenter.z - this.scratchOffset.z

    this.positions[rightOff] = this.scratchCenter.x + this.scratchOffset.x
    this.positions[rightOff + 1] = this.scratchCenter.y + this.scratchOffset.y
    this.positions[rightOff + 2] = this.scratchCenter.z + this.scratchOffset.z

    this.cores[vBase] = 0
    this.cores[vBase + 1] = core
    this.cores[vBase + 2] = 0
  }
}
