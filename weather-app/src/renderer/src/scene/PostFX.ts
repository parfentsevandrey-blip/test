import * as THREE from 'three'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import type { PostProcessor, SceneContext, SceneParams } from './contract'
import { clamp, clamp01, lerp } from '../utils/math'

/** Restrained glow around bright highlights (sun disc, moon, lightning). Kept
 *  low so the sky reads matte/editorial, not bloomy — but never zero, or the
 *  sun disc goes flat. */
const BLOOM_STRENGTH_BASE = 0.18
const BLOOM_STRENGTH_NIGHT_BOOST = 0.08
const BLOOM_STRENGTH_STORM_BOOST = 0.06
const BLOOM_RADIUS = 0.4
const BLOOM_THRESHOLD = 0.9

/** How quickly modulated bloom strength chases its target, in "per second" smoothing terms. */
const BLOOM_SMOOTH_RATE = 0.6

/** Vignette: how far in (0 = screen center, ~1 = corner) the darkening starts, and how strong it gets. */
const VIGNETTE_SOFTNESS = 0.32
const VIGNETTE_STRENGTH_BASE = 0.08
const VIGNETTE_STRENGTH_NIGHT_BOOST = 0.06
const VIGNETTE_STRENGTH_STORM_BOOST = 0.05

/** Film grain: kept deliberately faint so it reads as "premium" texture, not a
 *  noisy overlay — and it quietly kills gradient banding on the matte sky. */
const GRAIN_STRENGTH_BASE = 0.006
const GRAIN_STRENGTH_STORM_BOOST = 0.01

/**
 * Per-condition + day/night color grade applied in the vignette/grain pass:
 * a contrast pivot around mid-gray, a saturation mix, then a faint tint —
 * deliberately subtle so it reads as a matte-editorial grade, never a filter.
 */
const GRADE_NIGHT_CONTRAST_LIFT = 0.05
const GRADE_STORM_CONTRAST_LIFT = 0.08
const GRADE_FOG_CONTRAST_DROP = 0.1
const GRADE_NIGHT_SATURATION_DROP = 0.12
const GRADE_STORM_SATURATION_DROP = 0.1
const GRADE_FOG_SATURATION_DROP = 0.2
const GRADE_CLOUD_SATURATION_DROP = 0.04
const DAY_GRADE_TINT = new THREE.Color(1.015, 1.0, 0.985)
const NIGHT_GRADE_TINT = new THREE.Color(0.97, 0.99, 1.04)
const FOG_GRADE_TINT = new THREE.Color(0.99, 0.99, 0.97)
const NEUTRAL_WHITE = new THREE.Color(1, 1, 1)

/** Storm-driven radial chromatic aberration (UV units at full storm intensity) -- edge-only via the vignette pass's own center-distance gate, independent of bloom. */
const ABERRATION_STRENGTH_STORM = 0.006

/** Dynamic tone-mapping exposure: golden-hour lift, night dim, fog flatten -- unified with the lightning kick into one toneMappingExposure computation. */
const GOLDEN_HOUR_EXPOSURE_LIFT = 0.12
const NIGHT_EXPOSURE_DIM = 0.22
const FOG_EXPOSURE_FLATTEN = 0.08
const LIGHTNING_EXPOSURE_KICK = 0.35

/** Win95 retro mode: chunky pixels, crushed VGA palette, faint scanlines + RGB shadow-mask. */
const RETRO_PIXEL_SIZE = 4.0 // block edge, in device pixels (spec: ~3.5x-4.5x)
const RETRO_POSTERIZE_STEPS = 4.0 // floor(c * 4 + 0.5) / 4 -> 5 levels per channel
const RETRO_SATURATION = 1.12 // slight saturation lift for 16-bit-era punch
const RETRO_SCANLINE_DARKEN = 0.05 // ~5% darkening, alternating whole logical (chunky-pixel) rows
const RETRO_SHADOWMASK_STRENGTH = 0.05 // faint RGB triad shadow-mask, at true device-pixel resolution
const RETRO_BLOOM_STRENGTH = 0.25 // bloom eases down toward this while retro is on
const RETRO_RAMP_SECONDS = 0.25 // quick on/off ramp so the toggle never pops

const FULLSCREEN_VERTEX_SHADER = /* glsl */ `
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
  uniform float uAberrationStrength;
  uniform vec3 uGradeTint;
  uniform float uGradeSaturation;
  uniform float uGradeContrast;
  varying vec2 vUv;

  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.0, 289.0))) * 45758.5453);
  }

  void main() {
    vec2 centered = vUv - 0.5;
    float dist = length(centered) * 1.4142135;

    // Storm-driven radial chromatic aberration -- edge-only (screen center stays
    // clean via the same dist gate the vignette uses below), orthogonal to bloom.
    float aberration = uAberrationStrength * smoothstep(0.35, 1.0, dist);
    vec2 dir = dist > 0.0001 ? centered / dist : vec2(0.0);
    vec2 offset = dir * aberration;
    vec3 color = vec3(
      texture2D(tDiffuse, vUv + offset).r,
      texture2D(tDiffuse, vUv).g,
      texture2D(tDiffuse, vUv - offset).b
    );
    float alpha = texture2D(tDiffuse, vUv).a;

    // Per-condition + day/night color grade: contrast pivot, then saturation, then a faint tint.
    color = (color - 0.5) * uGradeContrast + 0.5;
    float gradeLuma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(gradeLuma), color, uGradeSaturation);
    color *= uGradeTint;

    float vignette = 1.0 - uVignetteStrength * smoothstep(uVignetteSoftness, 1.0, dist);
    float grain = (hash(vUv * (uTime * 37.0 + 1.0)) - 0.5) * uGrainStrength;

    gl_FragColor = vec4(color * vignette + grain, alpha);
  }
`

const RETRO_FRAGMENT_SHADER = /* glsl */ `
  uniform sampler2D tDiffuse;
  uniform float uRetro;
  uniform vec2 uResolution;
  varying vec2 vUv;

  // Classic 4x4 Bayer ordered-dither matrix, normalized to [0,1).
  float bayer4(vec2 cell) {
    vec2 c = mod(floor(cell), 4.0);
    float index = c.x + c.y * 4.0;
    // Row-major thresholds of the standard Bayer 4x4 pattern.
    float threshold = 0.0;
    if (index < 0.5) threshold = 0.0;
    else if (index < 1.5) threshold = 8.0;
    else if (index < 2.5) threshold = 2.0;
    else if (index < 3.5) threshold = 10.0;
    else if (index < 4.5) threshold = 12.0;
    else if (index < 5.5) threshold = 4.0;
    else if (index < 6.5) threshold = 14.0;
    else if (index < 7.5) threshold = 6.0;
    else if (index < 8.5) threshold = 3.0;
    else if (index < 9.5) threshold = 11.0;
    else if (index < 10.5) threshold = 1.0;
    else if (index < 11.5) threshold = 9.0;
    else if (index < 12.5) threshold = 15.0;
    else if (index < 13.5) threshold = 7.0;
    else if (index < 14.5) threshold = 13.0;
    else threshold = 5.0;
    return threshold / 16.0;
  }

  void main() {
    vec4 texel = texture2D(tDiffuse, vUv);

    // Cheap guard: while retro is off this pass is a pure passthrough.
    if (uRetro < 0.001) {
      gl_FragColor = texel;
      return;
    }

    // (a) Pixelate: snap UVs to a grid of ${RETRO_PIXEL_SIZE.toFixed(1)}-device-pixel blocks,
    // sampling each block at its center for clean, stable chunky pixels.
    vec2 grid = uResolution / ${RETRO_PIXEL_SIZE.toFixed(1)};
    vec2 snappedUv = (floor(vUv * grid) + 0.5) / grid;
    vec3 retro = texture2D(tDiffuse, snappedUv).rgb;

    // (b) VGA punch: slight saturation lift, then posterize to
    // ${(RETRO_POSTERIZE_STEPS + 1).toFixed(0)} levels per channel. Quantization
    // happens in gamma space (pow 1/2.2 -> quantize -> pow 2.2): in linear
    // space the lowest bands are perceptually huge, so dark noisy regions
    // (night terrain, rain) slam between wildly different saturated
    // primaries and read as glitch garbage rather than a retro palette.
    float luma = dot(retro, vec3(0.299, 0.587, 0.114));
    retro = clamp(mix(vec3(luma), retro, ${RETRO_SATURATION.toFixed(2)}), 0.0, 1.0);
    retro = pow(retro, vec3(1.0 / 2.2));
    // Period-correct ordered dithering (per chunky pixel, not per fragment):
    // breaks up posterization's hue banding on soft sky/haze gradients the
    // same way every 256-color-era renderer did.
    float dither = bayer4(floor(vUv * grid)) - 0.5;
    retro += dither / ${RETRO_POSTERIZE_STEPS.toFixed(1)};
    retro = floor(retro * ${RETRO_POSTERIZE_STEPS.toFixed(1)} + 0.5) / ${RETRO_POSTERIZE_STEPS.toFixed(1)};
    retro = pow(clamp(retro, 0.0, 1.0), vec3(2.2));

    // (c) Very subtle scanlines, ~5% darkening -- alternating whole LOGICAL
    // (chunky-pixel) rows via the same block row index the pixelation above
    // uses. Using raw device-pixel rows here (as before) cut a scanline
    // through the middle of every big pixel instead of darkening whole rows
    // of the retro grid, breaking the pixel-grid alignment.
    float rowIndex = floor(vUv.y * grid.y);
    float scan = 1.0 - ${RETRO_SCANLINE_DARKEN.toFixed(2)} * step(1.0, mod(rowIndex, 2.0));
    retro *= scan;

    // (d) Faint RGB shadow-mask: a real CRT's fixed physical sub-pixel triad,
    // independent of the logical pixel grid above -- operates at true
    // device-pixel resolution so it reads as a texture over the big pixels.
    float maskPhase = mod(gl_FragCoord.x, 3.0);
    vec3 shadowMask = vec3(1.0);
    if (maskPhase < 1.0) {
      shadowMask = vec3(1.0, 1.0 - ${RETRO_SHADOWMASK_STRENGTH.toFixed(2)}, 1.0 - ${RETRO_SHADOWMASK_STRENGTH.toFixed(2)});
    } else if (maskPhase < 2.0) {
      shadowMask = vec3(1.0 - ${RETRO_SHADOWMASK_STRENGTH.toFixed(2)}, 1.0, 1.0 - ${RETRO_SHADOWMASK_STRENGTH.toFixed(2)});
    } else {
      shadowMask = vec3(1.0 - ${RETRO_SHADOWMASK_STRENGTH.toFixed(2)}, 1.0 - ${RETRO_SHADOWMASK_STRENGTH.toFixed(2)}, 1.0);
    }
    retro *= shadowMask;

    // uRetro ramps 0 -> 1 over ~${RETRO_RAMP_SECONDS}s so the toggle never pops.
    gl_FragColor = vec4(mix(texel.rgb, retro, uRetro), texel.a);
  }
`

/**
 * Cinematic post-processing: ACES tone mapping + an EffectComposer chain of
 * RenderPass -> UnrealBloomPass (soft highlight glow) -> Win95 retro pass
 * (pixelate/posterize/scanlines, passthrough while off) -> a subtle inline
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
  private readonly retroPass: ShaderPass
  private readonly vignettePass: ShaderPass
  private readonly outputPass: OutputPass
  private readonly lightningBrightness: { value: number }

  private bloomStrength = BLOOM_STRENGTH_BASE
  private vignetteStrength = VIGNETTE_STRENGTH_BASE
  private grainStrength = GRAIN_STRENGTH_BASE
  private aberrationStrength = 0

  private readonly gradeTint = new THREE.Color(1, 1, 1)
  private readonly scratchGradeTint = new THREE.Color(1, 1, 1)
  private gradeSaturation = 1
  private gradeContrast = 1

  private smoothedExposureBaseline = 1

  /** 0 = fully cinematic, 1 = fully retro; `retroAmount` ramps toward `retroTarget` in update(). */
  private retroTarget = 0
  private retroAmount = 0

  constructor(ctx: SceneContext) {
    this.ctx = ctx
    this.lightningBrightness = ctx.lightningBrightness

    ctx.renderer.outputColorSpace = THREE.SRGBColorSpace
    ctx.renderer.toneMapping = THREE.ACESFilmicToneMapping
    ctx.renderer.toneMappingExposure = 1.0

    const size = ctx.renderer.getSize(new THREE.Vector2())

    this.composer = new EffectComposer(ctx.renderer)

    this.renderPass = new RenderPass(ctx.scene, ctx.camera)
    this.composer.addPass(this.renderPass)

    this.bloomPass = new UnrealBloomPass(size, BLOOM_STRENGTH_BASE, BLOOM_RADIUS, BLOOM_THRESHOLD)
    this.composer.addPass(this.bloomPass)

    // Win95 retro pass sits immediately before the vignette pass. Disabled
    // (skipped entirely by the composer) whenever the retro ramp is at zero.
    const pixelRatio = ctx.renderer.getPixelRatio()
    this.retroPass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        uRetro: { value: 0 },
        uResolution: { value: new THREE.Vector2(size.x * pixelRatio, size.y * pixelRatio) }
      },
      vertexShader: FULLSCREEN_VERTEX_SHADER,
      fragmentShader: RETRO_FRAGMENT_SHADER
    })
    this.retroPass.enabled = false
    this.composer.addPass(this.retroPass)

    this.vignettePass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        uTime: { value: 0 },
        uVignetteStrength: { value: VIGNETTE_STRENGTH_BASE },
        uVignetteSoftness: { value: VIGNETTE_SOFTNESS },
        uGrainStrength: { value: GRAIN_STRENGTH_BASE },
        uAberrationStrength: { value: 0 },
        uGradeTint: { value: new THREE.Color(1, 1, 1) },
        uGradeSaturation: { value: 1 },
        uGradeContrast: { value: 1 }
      },
      vertexShader: FULLSCREEN_VERTEX_SHADER,
      fragmentShader: VIGNETTE_GRAIN_FRAGMENT_SHADER
    })
    this.composer.addPass(this.vignettePass)

    this.outputPass = new OutputPass()
    this.composer.addPass(this.outputPass)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const nightAmount = 1 - clamp01((params.sunAltitude + 0.15) / 0.3)
    const stormAmount = params.thunderActive ? 1 : clamp01(params.precipitationIntensity * 0.4)
    const fogAmount = clamp01(1 - params.visibility)
    const cloudAmount = clamp01(params.cloudCover)

    const targetBloom =
      BLOOM_STRENGTH_BASE + nightAmount * BLOOM_STRENGTH_NIGHT_BOOST + stormAmount * BLOOM_STRENGTH_STORM_BOOST
    const targetVignette =
      VIGNETTE_STRENGTH_BASE + nightAmount * VIGNETTE_STRENGTH_NIGHT_BOOST + stormAmount * VIGNETTE_STRENGTH_STORM_BOOST
    const targetGrain = GRAIN_STRENGTH_BASE + stormAmount * GRAIN_STRENGTH_STORM_BOOST
    const targetAberration = stormAmount * ABERRATION_STRENGTH_STORM

    const smoothT = clamp01(dt * BLOOM_SMOOTH_RATE)
    this.bloomStrength = lerp(this.bloomStrength, targetBloom, smoothT)
    this.vignetteStrength = lerp(this.vignetteStrength, targetVignette, smoothT)
    this.grainStrength = lerp(this.grainStrength, targetGrain, smoothT)
    this.aberrationStrength = lerp(this.aberrationStrength, targetAberration, smoothT)

    // Per-condition + day/night color grade -- subtle contrast/saturation/tint
    // deviations driven by the same continuous signals as everything else,
    // not a discrete per-condition lookup table.
    const targetContrast =
      1 +
      nightAmount * GRADE_NIGHT_CONTRAST_LIFT +
      stormAmount * GRADE_STORM_CONTRAST_LIFT -
      fogAmount * GRADE_FOG_CONTRAST_DROP
    const targetSaturation =
      1 -
      nightAmount * GRADE_NIGHT_SATURATION_DROP -
      stormAmount * GRADE_STORM_SATURATION_DROP -
      fogAmount * GRADE_FOG_SATURATION_DROP -
      cloudAmount * GRADE_CLOUD_SATURATION_DROP
    this.gradeContrast = lerp(this.gradeContrast, targetContrast, smoothT)
    this.gradeSaturation = lerp(this.gradeSaturation, targetSaturation, smoothT)
    this.scratchGradeTint.copy(DAY_GRADE_TINT).lerp(NIGHT_GRADE_TINT, nightAmount).lerp(FOG_GRADE_TINT, fogAmount * 0.6)
    this.gradeTint.lerp(this.scratchGradeTint, smoothT)

    // Quick linear ramp toward the retro target so toggling never pops.
    if (this.retroAmount !== this.retroTarget) {
      const rampStep = dt / RETRO_RAMP_SECONDS
      this.retroAmount =
        this.retroAmount < this.retroTarget
          ? Math.min(this.retroTarget, this.retroAmount + rampStep)
          : Math.max(this.retroTarget, this.retroAmount - rampStep)
    }
    const retro = this.retroAmount
    this.retroPass.uniforms.uRetro.value = retro
    // Skip the pass entirely once fully off -- zero cost while idle.
    this.retroPass.enabled = retro > 0.0001

    // Retro reads flat, not filmic: fade the vignette/grain/aberration/grade
    // modulation out and gently pull bloom down toward its retro level as the
    // ramp rises. The smoothed cinematic values keep tracking underneath, so
    // leaving retro restores them seamlessly.
    this.bloomPass.strength = lerp(this.bloomStrength, RETRO_BLOOM_STRENGTH, retro)
    const uniforms = this.vignettePass.uniforms
    uniforms.uVignetteStrength.value = this.vignetteStrength * (1 - retro)
    uniforms.uGrainStrength.value = this.grainStrength * (1 - retro)
    uniforms.uAberrationStrength.value = this.aberrationStrength * (1 - retro)
    uniforms.uGradeSaturation.value = lerp(1, this.gradeSaturation, 1 - retro)
    uniforms.uGradeContrast.value = lerp(1, this.gradeContrast, 1 - retro)
    ;(uniforms.uGradeTint.value as THREE.Color).copy(this.gradeTint).lerp(NEUTRAL_WHITE, retro)
    uniforms.uTime.value = elapsed

    // Dynamic tone-mapping exposure: golden-hour lift, night dim and fog
    // flatten smoothed into one baseline, unified with a fast, unsmoothed
    // lightning kick so a strike still reads as a snappy flash.
    const altitude = clamp(params.sunAltitude, -1, 1)
    const goldenWeight = clamp01(1 - Math.abs(altitude) / 0.12)
    const targetExposureBaseline =
      1 + goldenWeight * GOLDEN_HOUR_EXPOSURE_LIFT - nightAmount * NIGHT_EXPOSURE_DIM - fogAmount * FOG_EXPOSURE_FLATTEN
    this.smoothedExposureBaseline = lerp(this.smoothedExposureBaseline, targetExposureBaseline, smoothT)
    const lightningKick = clamp01(this.lightningBrightness.value) * LIGHTNING_EXPOSURE_KICK
    this.ctx.renderer.toneMappingExposure = this.smoothedExposureBaseline + lightningKick
  }

  /**
   * Win95 retro mode toggle: ramps the pixelate/posterize/scanline pass in
   * or out over ~0.25s (driven by update()), flattens the vignette/grain
   * modulation and eases bloom toward its retro strength while enabled.
   */
  setRetro(enabled: boolean): void {
    this.retroTarget = enabled ? 1 : 0
    if (enabled) this.retroPass.enabled = true
  }

  render(dt: number): void {
    this.composer.render(dt)
  }

  resize(width: number, height: number): void {
    this.ctx.renderer.setSize(width, height)
    this.composer.setSize(width, height)
    this.bloomPass.resolution.set(width, height)
    // Retro pixel grid is specified in device pixels, matching the composer's
    // actual render-target resolution (logical size * pixel ratio).
    const pixelRatio = this.ctx.renderer.getPixelRatio()
    ;(this.retroPass.uniforms.uResolution.value as THREE.Vector2).set(
      width * pixelRatio,
      height * pixelRatio
    )
  }

  dispose(): void {
    this.renderPass.dispose()
    this.bloomPass.dispose()
    this.retroPass.dispose()
    this.vignettePass.dispose()
    this.outputPass.dispose()
    this.composer.dispose()
  }
}
