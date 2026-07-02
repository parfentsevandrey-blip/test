import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { makeRadialTexture } from './textures'
import { clamp01, lerp } from '../utils/math'

/** Fraction of stars that gently twinkle (brightness + size pulse) over time. */
const TWINKLE_FRACTION = 0.05

/** Star shell sits just inside the (larger) sky dome. */
const SPHERE_RADIUS_MIN = 400
const SPHERE_RADIUS_MAX = 440

/** Keep the point count in the 2500-4000 range requested, scaled a little by quality tier. */
const STAR_COUNT_BY_QUALITY: Record<Quality, number> = {
  low: 2500,
  medium: 3200,
  high: 4000
}

const VERTEX_SHADER = /* glsl */ `
  attribute float aSize;
  attribute float aBrightness;
  attribute vec3 aColor;
  attribute vec3 aTwinkle; // x: phase, y: amplitude (0 = no twinkle), z: speed

  uniform float uElapsed;
  uniform float uPixelRatio;
  uniform float uBaseSize;

  varying vec3 vColor;
  varying float vAlpha;

  void main() {
    float twinkleMask = step(0.0001, aTwinkle.y);
    float twinkle = 1.0 + aTwinkle.y * sin(uElapsed * aTwinkle.z + aTwinkle.x);
    float alphaPulse = mix(1.0, clamp(twinkle, 0.05, 1.9), twinkleMask);
    float sizePulse = mix(1.0, clamp(twinkle, 0.2, 1.6), twinkleMask);

    vColor = aColor;
    vAlpha = aBrightness * alphaPulse;

    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
    gl_Position = projectionMatrix * mvPosition;
    gl_PointSize = max(1.0, aSize * uBaseSize * uPixelRatio * sizePulse);
  }
`

const FRAGMENT_SHADER = /* glsl */ `
  uniform sampler2D uMap;
  uniform float uOpacity;

  varying vec3 vColor;
  varying float vAlpha;

  void main() {
    vec4 tex = texture2D(uMap, gl_PointCoord);
    float alpha = tex.a * vAlpha * uOpacity;
    if (alpha <= 0.001) discard;
    gl_FragColor = vec4(tex.rgb * vColor, alpha);
  }
`

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side opacity curve. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/** Slightly varied star tints: mostly white, with icy-blue, warm and pale-red outliers. */
function pickStarColor(): [number, number, number] {
  const r = Math.random()
  if (r < 0.7) return [1, 1, 1]
  if (r < 0.85) return [0.78, 0.87, 1]
  if (r < 0.96) return [1, 0.93, 0.8]
  return [1, 0.82, 0.78]
}

/**
 * A twinkling night-sky starfield on a large point-sprite sphere shell.
 * Fades in at night and fades back out with daylight, cloud cover and fog.
 */
export class Stars implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly geometry: THREE.BufferGeometry
  private readonly material: THREE.ShaderMaterial
  private readonly starTexture: THREE.CanvasTexture
  private readonly points: THREE.Points

  /** Smoothed opacity so weather-driven changes never pop. */
  private opacity = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene

    const count = STAR_COUNT_BY_QUALITY[ctx.quality]
    const positions = new Float32Array(count * 3)
    const sizes = new Float32Array(count)
    const brightness = new Float32Array(count)
    const colors = new Float32Array(count * 3)
    const twinkle = new Float32Array(count * 3)

    for (let i = 0; i < count; i++) {
      // Uniform random point on a unit sphere.
      const u = Math.random() * 2 - 1
      const theta = Math.random() * Math.PI * 2
      const ringR = Math.sqrt(Math.max(0, 1 - u * u))
      const dx = ringR * Math.cos(theta)
      const dz = ringR * Math.sin(theta)
      const dy = u
      const radius = lerp(SPHERE_RADIUS_MIN, SPHERE_RADIUS_MAX, Math.random())

      positions[i * 3] = dx * radius
      positions[i * 3 + 1] = dy * radius
      positions[i * 3 + 2] = dz * radius

      // Bias toward small/dim stars with a handful of bright standouts.
      const weight = Math.pow(Math.random(), 2.4)
      sizes[i] = lerp(0.6, 2.6, weight)
      brightness[i] = lerp(0.3, 1, weight) * lerp(0.85, 1, Math.random())

      const tint = pickStarColor()
      colors[i * 3] = tint[0]
      colors[i * 3 + 1] = tint[1]
      colors[i * 3 + 2] = tint[2]

      if (Math.random() < TWINKLE_FRACTION) {
        twinkle[i * 3] = Math.random() * Math.PI * 2
        twinkle[i * 3 + 1] = lerp(0.35, 0.85, Math.random())
        twinkle[i * 3 + 2] = lerp(0.6, 2.6, Math.random())
      } else {
        twinkle[i * 3] = 0
        twinkle[i * 3 + 1] = 0
        twinkle[i * 3 + 2] = 1
      }
    }

    this.geometry = new THREE.BufferGeometry()
    this.geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    this.geometry.setAttribute('aSize', new THREE.BufferAttribute(sizes, 1))
    this.geometry.setAttribute('aBrightness', new THREE.BufferAttribute(brightness, 1))
    this.geometry.setAttribute('aColor', new THREE.BufferAttribute(colors, 3))
    this.geometry.setAttribute('aTwinkle', new THREE.BufferAttribute(twinkle, 3))
    this.geometry.computeBoundingSphere()

    this.starTexture = makeRadialTexture('rgba(255,255,255,1)', 'rgba(255,255,255,0)', 64)

    this.material = new THREE.ShaderMaterial({
      uniforms: {
        uMap: { value: this.starTexture },
        uOpacity: { value: 0 },
        uElapsed: { value: 0 },
        uPixelRatio: { value: ctx.renderer.getPixelRatio() },
        uBaseSize: { value: 3.2 }
      },
      vertexShader: VERTEX_SHADER,
      fragmentShader: FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })

    this.points = new THREE.Points(this.geometry, this.material)
    this.points.visible = false
    this.scene.add(this.points)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    // Fully visible below ~-0.05 sun altitude, smoothly gone by full daylight.
    const nightFactor = 1 - smoothstep(-0.05, 0.2, params.sunAltitude)
    const cloudFactor = lerp(1, 0.05, clamp01(params.cloudCover))
    const visibilityFactor = clamp01(lerp(0.08, 1, clamp01(params.visibility)))
    const target = clamp01(nightFactor * cloudFactor * visibilityFactor)

    // Exponential smoothing so weather-data updates never cause a visible pop.
    const smoothing = 1 - Math.exp(-dt * 3)
    this.opacity += (target - this.opacity) * smoothing

    this.material.uniforms.uOpacity.value = this.opacity
    this.material.uniforms.uElapsed.value = elapsed
    this.points.visible = this.opacity > 0.003
  }

  dispose(): void {
    this.scene.remove(this.points)
    this.geometry.dispose()
    this.material.dispose()
    this.starTexture.dispose()
  }
}
