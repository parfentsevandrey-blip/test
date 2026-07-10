import type * as THREE from 'three'
import type { WeatherCondition } from '../utils/weatherCondition'

export type Quality = 'low' | 'medium' | 'high'

/**
 * The single per-frame snapshot every visual effect reacts to. Recomputed
 * once per frame by SceneManager from the live WeatherData + a real-time
 * clock, then passed unchanged to every effect's `update()`.
 */
export interface SceneParams {
  condition: WeatherCondition
  isDay: boolean
  /** 0 (no clouds) - 1 (fully overcast) */
  cloudCover: number
  /** 0 (dry) - 1 (heaviest rain/snow in the dataset) */
  precipitationIntensity: number
  /** 0-1, normalized wind speed (real-world m/s / 20, clamped) */
  windSpeed: number
  /** Radians. The compass direction the wind blows TOWARD. */
  windDirectionRad: number
  /** sin(sun elevation angle). 1 = directly overhead, 0 = on the horizon, -1 = directly below. */
  sunAltitude: number
  /** Radians, 0-2π, sun position around the horizon (also used to place the moon opposite it). */
  sunAzimuthRad: number
  /** 0-1, fraction of the local day elapsed. Cheap fallback signal for day/night blending. */
  timeOfDayFrac: number
  /** 0 (dense fog, can't see far) - 1 (perfectly clear) */
  visibility: number
  /** True only while `condition === 'thunderstorm'`. Effects should idle any storm-only behavior when false. */
  thunderActive: boolean
  temperatureC: number
  /** 0 (new moon, no moonlight) - 1 (full moon) -- from utils/moonPhase.ts's synodic-month calc, independent of sunAltitude/isDay. */
  moonIllumination: number
  /** 0-1 raw synodic phase (0/1 = new, 0.5 = full) from utils/moonPhase.ts's getMoonPhase -- distinct from moonIllumination, since Sky's moon-disc terminator also needs the waxing (< 0.5) vs waning (>= 0.5) side, not just the illuminated fraction. */
  moonPhaseFrac: number
}

/**
 * Shared Three.js objects every effect is constructed with. Built once by
 * SceneManager and handed to every effect's constructor.
 *
 * Ownership rules (so effects don't fight over shared state):
 * - Only `Sky` may reposition/recolor `sunLight` and `hemiLight` (it owns
 *   celestial mechanics: sun/moon position, color temperature, ambient tint).
 * - Shadows are intentionally OFF globally for performance headroom across
 *   many simultaneous particle systems — use light color/intensity and fog
 *   for depth cues instead of `castShadow`/`receiveShadow`.
 * - Every effect adds its own object(s) to `scene` in its constructor and
 *   must remove + dispose everything it added in `dispose()`.
 */
export interface SceneContext {
  scene: THREE.Scene
  camera: THREE.PerspectiveCamera
  renderer: THREE.WebGLRenderer
  sunLight: THREE.DirectionalLight
  hemiLight: THREE.HemisphereLight
  quality: Quality
  /**
   * Boxed 0-1 signal written by Lightning every frame (its strike/sheet/afterglow
   * envelopes combined into one number) so Sky/PostFX/Precipitation can react to
   * a flash without a direct dependency on Lightning. Boxed (rather than a plain
   * number on SceneContext) so it can be mutated in place by whichever effect
   * owns it -- same pattern as the shared sunLight/hemiLight objects.
   */
  lightningBrightness: { value: number }
  /** Unit direction toward the most recent strike/sheet source, written alongside lightningBrightness. Only meaningful while lightningBrightness.value > 0. */
  lightningDir: THREE.Vector3
  /**
   * Boxed 0-1 "a gust is happening right now" envelope, computed once per frame
   * by SceneManager from windSpeed so every wind-reactive effect (camera jitter,
   * Sky striation, Fog drift, Precipitation turbulence) shares one canonical
   * gust signal instead of each rolling its own private incommensurate-sine gust.
   */
  windGust: { value: number }
}

/** Implemented by every visual effect module (Sky, Stars, Clouds, Precipitation, Lightning, Fog, Terrain). */
export interface SceneEffect {
  /** Called once per animation frame. `elapsed` is seconds since the scene started (safe for continuous animation/noise phases). */
  update(dt: number, elapsed: number, params: SceneParams): void
  /** Remove everything this effect added to the scene and dispose all geometries/materials/textures it created. */
  dispose(): void
}

/** The post-processing effect additionally owns the actual render call (via an EffectComposer). */
export interface PostProcessor extends SceneEffect {
  render(dt: number): void
  resize(width: number, height: number): void
}
