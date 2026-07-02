import * as THREE from 'three'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import type { PostProcessor, SceneContext, SceneParams } from './contract'
import { clamp01, lerp } from '../utils/math'

/** Baseline soft glow around bright highlights (sun disc, moon, lightning, sprite cores). */
const BLOOM_STRENGTH_BASE = 0.62
const BLOOM_STRENGTH_NIGHT_BOOST = 0.22
const BLOOM_STRENGTH_STORM_BOOST = 0.1
const BLOOM_RADIUS = 0.48
const BLOOM_THRESHOLD = 0.86

/** How quickly modulated bloom strength chases its target, in "per second" smoothing terms. */
const BLOOM_SMOOTH_RATE = 0.6

/** Vignette: how far in (0 = screen center, ~1 = corner) the darkening starts, and how strong it gets. */
const VIGNETTE_SOFTNESS = 0.32
const VIGNETTE_STRENGTH_BASE = 0.26
const VIGNETTE_STRENGTH_NIGHT_BOOST = 0.12
const VIGNETTE_STRENGTH_STORM_BOOST = 0.08

/** Film grain: kept deliberately faint so it reads as "premium" texture, not a noisy overlay. */
const GRAIN_STRENGTH_BASE = 0.014
const GRAIN_STRENGTH_STORM_BOOST = 0.018

const VIGNETTE_GRAIN_VERTEX_SHADER = /* glsl */ `
  varying vec2 vUv;

  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const VIGNETTE_GRAIN_FRAGMENT_SHADER = /* glsl */ `
  uniform sampler2D tDiffuse;
  uniform float uTime;
  uniform float uVignetteStrength;
  uniform float uVignetteSoftness;
  uniform float uGrainStrength;
  varying vec2 vUv;

  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.0, 289.0))) * 45758.5453);
  }

  void main() {
    vec4 texel = texture2D(tDiffuse, vUv);

    vec2 centered = vUv - 0.5;
    float dist = length(centered) * 1.4142135;
    float vignette = 1.0 - uVignetteStrength * smoothstep(uVignetteSoftness, 1.0, dist);

    float grain = (hash(vUv * (uTime * 37.0 + 1.0)) - 0.5) * uGrainStrength;

    gl_FragColor = vec4(texel.rgb * vignette + grain, texel.a);
  }
`

/**
 * Cinematic post-processing: ACES tone mapping + an EffectComposer chain of
 * RenderPass -> UnrealBloomPass (soft highlight glow) -> a subtle inline
 * vignette/grain ShaderPass -> OutputPass (final color-space/tone-map
 * resolve). Owns the actual render call for the whole scene once present --
 * SceneManager calls `render(dt)` on this instead of rendering itself.
 *
 * Only reacts to `params`; never touches `ctx.sunLight` / `ctx.hemiLight`
 * (those belong to Sky) and adds no objects to `ctx.scene`.
 */
export class PostFX implements PostProcessor {
  private readonly ctx: SceneContext
  private readonly composer: EffectComposer
  private readonly renderPass: RenderPass
  private readonly bloomPass: UnrealBloomPass
  private readonly vignettePass: ShaderPass
  private readonly outputPass: OutputPass

  private bloomStrength = BLOOM_STRENGTH_BASE
  private vignetteStrength = VIGNETTE_STRENGTH_BASE
  private grainStrength = GRAIN_STRENGTH_BASE

  constructor(ctx: SceneContext) {
    this.ctx = ctx

    ctx.renderer.outputColorSpace = THREE.SRGBColorSpace
    ctx.renderer.toneMapping = THREE.ACESFilmicToneMapping
    ctx.renderer.toneMappingExposure = 1.08

    const size = ctx.renderer.getSize(new THREE.Vector2())

    this.composer = new EffectComposer(ctx.renderer)

    this.renderPass = new RenderPass(ctx.scene, ctx.camera)
    this.composer.addPass(this.renderPass)

    this.bloomPass = new UnrealBloomPass(size, BLOOM_STRENGTH_BASE, BLOOM_RADIUS, BLOOM_THRESHOLD)
    this.composer.addPass(this.bloomPass)

    this.vignettePass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        uTime: { value: 0 },
        uVignetteStrength: { value: VIGNETTE_STRENGTH_BASE },
        uVignetteSoftness: { value: VIGNETTE_SOFTNESS },
        uGrainStrength: { value: GRAIN_STRENGTH_BASE }
      },
      vertexShader: VIGNETTE_GRAIN_VERTEX_SHADER,
      fragmentShader: VIGNETTE_GRAIN_FRAGMENT_SHADER
    })
    this.composer.addPass(this.vignettePass)

    this.outputPass = new OutputPass()
    this.composer.addPass(this.outputPass)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const nightAmount = 1 - clamp01((params.sunAltitude + 0.15) / 0.3)
    const stormAmount = params.thunderActive ? 1 : clamp01(params.precipitationIntensity * 0.4)

    const targetBloom =
      BLOOM_STRENGTH_BASE + nightAmount * BLOOM_STRENGTH_NIGHT_BOOST + stormAmount * BLOOM_STRENGTH_STORM_BOOST
    const targetVignette =
      VIGNETTE_STRENGTH_BASE + nightAmount * VIGNETTE_STRENGTH_NIGHT_BOOST + stormAmount * VIGNETTE_STRENGTH_STORM_BOOST
    const targetGrain = GRAIN_STRENGTH_BASE + stormAmount * GRAIN_STRENGTH_STORM_BOOST

    const smoothT = clamp01(dt * BLOOM_SMOOTH_RATE)
    this.bloomStrength = lerp(this.bloomStrength, targetBloom, smoothT)
    this.vignetteStrength = lerp(this.vignetteStrength, targetVignette, smoothT)
    this.grainStrength = lerp(this.grainStrength, targetGrain, smoothT)

    this.bloomPass.strength = this.bloomStrength
    const uniforms = this.vignettePass.uniforms
    uniforms.uVignetteStrength.value = this.vignetteStrength
    uniforms.uGrainStrength.value = this.grainStrength
    uniforms.uTime.value = elapsed
  }

  render(dt: number): void {
    this.composer.render(dt)
  }

  resize(width: number, height: number): void {
    this.ctx.renderer.setSize(width, height)
    this.composer.setSize(width, height)
    this.bloomPass.resolution.set(width, height)
  }

  dispose(): void {
    this.renderPass.dispose()
    this.bloomPass.dispose()
    this.vignettePass.dispose()
    this.outputPass.dispose()
    this.composer.dispose()
  }
}
