import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { clamp01, lerp } from '../utils/math'

/** How far away the arc stands (well inside the sky dome at 450). */
const ARC_DISTANCE = 300
/** Outer radius of the bow; the band is ~8% of it. */
const OUTER_RADIUS = 160
const INNER_RADIUS = OUTER_RADIUS * 0.92
/** Premium restraint: a rainbow should glow, not shout. */
const MAX_OPACITY = 0.35

const THETA_SEGMENTS_BY_QUALITY: Record<Quality, number> = {
  low: 48,
  medium: 72,
  high: 96
}

const VERTEX_SHADER = /* glsl */ `
  varying vec2 vLocal;

  void main() {
    vLocal = position.xy;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const FRAGMENT_SHADER = /* glsl */ `
  varying vec2 vLocal;

  uniform float uInner;
  uniform float uOuter;
  uniform float uOpacity;

  const float PI = 3.14159265;

  // 7 spectral bands, violet (inner) -> red (outer), soft transitions.
  vec3 spectral(float t) {
    vec3 c = vec3(0.55, 0.25, 0.85);                          // violet
    c = mix(c, vec3(0.25, 0.30, 0.90), smoothstep(0.06, 0.18, t)); // indigo
    c = mix(c, vec3(0.15, 0.55, 1.00), smoothstep(0.20, 0.33, t)); // blue
    c = mix(c, vec3(0.25, 0.90, 0.35), smoothstep(0.35, 0.48, t)); // green
    c = mix(c, vec3(1.00, 0.90, 0.25), smoothstep(0.50, 0.62, t)); // yellow
    c = mix(c, vec3(1.00, 0.55, 0.10), smoothstep(0.64, 0.77, t)); // orange
    c = mix(c, vec3(1.00, 0.22, 0.15), smoothstep(0.79, 0.92, t)); // red
    return c;
  }

  void main() {
    float r = length(vLocal);
    float t = clamp((r - uInner) / (uOuter - uInner), 0.0, 1.0);

    // Soft alpha falloff across the band's inner/outer edges.
    float band = smoothstep(0.0, 0.16, t) * (1.0 - smoothstep(0.84, 1.0, t));

    // Dissolve the legs of the arc into the horizon haze.
    float ang = atan(vLocal.y, vLocal.x); // 0..PI across the top half-arc
    float legs = smoothstep(0.0, 0.28, min(ang, PI - ang));

    float alpha = band * legs * uOpacity;
    if (alpha <= 0.002) discard;
    gl_FragColor = vec4(spectral(t), alpha);
  }
`

/** Cheap smoothstep, matching GLSL semantics, for the CPU-side opacity curve. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/**
 * The post-rain reward moment: a spectral half-arc that materializes in the
 * anti-solar direction when sunlight breaks through active rain/drizzle,
 * sinking lower in the sky as the sun climbs (as real bows do), and fading
 * in/out over ~2.5s so weather refreshes never pop.
 */
export class Rainbow implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly geometry: THREE.RingGeometry
  private readonly material: THREE.ShaderMaterial
  private readonly mesh: THREE.Mesh

  /** Smoothed opacity so weather-driven changes never pop. */
  private opacity = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene

    // Top half of a ring: thetaStart 0 sweeps CCW from +X, so thetaLength PI
    // covers local y >= 0 -- the arc standing on the horizon.
    this.geometry = new THREE.RingGeometry(
      INNER_RADIUS,
      OUTER_RADIUS,
      THETA_SEGMENTS_BY_QUALITY[ctx.quality],
      4,
      0,
      Math.PI
    )

    this.material = new THREE.ShaderMaterial({
      uniforms: {
        uInner: { value: INNER_RADIUS },
        uOuter: { value: OUTER_RADIUS },
        uOpacity: { value: 0 }
      },
      vertexShader: VERTEX_SHADER,
      fragmentShader: FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
      fog: false
    })

    this.mesh = new THREE.Mesh(this.geometry, this.material)
    this.mesh.renderOrder = -80 // after the celestial stack, before weather layers
    this.mesh.visible = false
    this.scene.add(this.mesh)
  }

  update(dt: number, _elapsed: number, params: SceneParams): void {
    const altitude = params.sunAltitude
    const rainy = params.condition === 'rain' || params.condition === 'drizzle'
    const gate =
      rainy &&
      params.precipitationIntensity > 0.03 &&
      altitude > 0.05 &&
      altitude < 0.5 &&
      params.cloudCover < 0.9

    // Physically-inspired strength: a low-but-risen sun, a break in the
    // clouds, and enough falling water to refract.
    const sunWindow = smoothstep(0.05, 0.12, altitude) * (1 - smoothstep(0.38, 0.5, altitude))
    const cloudGap = 1 - smoothstep(0.55, 0.9, params.cloudCover)
    const wetness = smoothstep(0.03, 0.18, params.precipitationIntensity)
    const target = gate ? MAX_OPACITY * sunWindow * cloudGap * lerp(0.65, 1, wetness) : 0

    // Exponential smoothing tuned for a ~2.5s fade (95% in ~2.3s).
    this.opacity += (target - this.opacity) * (1 - Math.exp(-dt * 1.3))

    if (this.opacity <= 0.004 && target <= 0) {
      // Idle at effectively zero cost when the trigger conditions fail.
      this.opacity = 0
      if (this.mesh.visible) this.mesh.visible = false
      return
    }
    this.mesh.visible = this.opacity > 0.004
    this.material.uniforms.uOpacity.value = this.opacity

    // The bow stands opposite the sun (anti-solar direction) and sinks as
    // the sun climbs, its base hugging the horizon.
    const antiAz = params.sunAzimuthRad + Math.PI
    const sink = clamp01((altitude - 0.05) / 0.45)
    this.mesh.position.set(
      Math.sin(antiAz) * ARC_DISTANCE,
      lerp(-10, -90, sink),
      Math.cos(antiAz) * ARC_DISTANCE
    )
    // Ring lies in its local XY plane (normal +Z); yaw it so the plane is
    // perpendicular to the anti-solar bearing (DoubleSide handles facing).
    this.mesh.rotation.y = antiAz
  }

  dispose(): void {
    this.scene.remove(this.mesh)
    this.geometry.dispose()
    this.material.dispose()
  }
}
