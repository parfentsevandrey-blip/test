import * as THREE from 'three'
import type { Quality, SceneContext, SceneEffect, SceneParams } from './contract'
import { clamp, clamp01, lerp } from '../utils/math'

/** Radius of the dome the raymarch runs against -- just needs to comfortably enclose the camera's whole flight envelope. */
const DOME_RADIUS = 340
/** World-space Y bounds of the cloud "slab" the raymarch searches within. */
const CLOUD_BASE_Y = 46
const CLOUD_TOP_Y = 150
/** Raymarch step count per quality tier -- the main cost/quality knob. */
const STEPS_BY_QUALITY: Record<Quality, number> = { low: 12, medium: 18, high: 26 }
/** World units/sec the noise field drifts at full params.windSpeed. */
const WIND_DRIFT_SPEED = 2.6
/** Fraction of full drift speed kept even at zero wind, so the deck never freezes solid. */
const AMBIENT_DRIFT_FLOOR = 0.15
/** Smoothing rate (1/s) for color/coverage so weather-data refreshes never pop. */
const SMOOTHING_RATE = 0.8

const VERTEX_SHADER = /* glsl */ `
  varying vec3 vWorldPosition;

  void main() {
    vec4 worldPosition = modelMatrix * vec4(position, 1.0);
    vWorldPosition = worldPosition.xyz;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

function fragmentShader(steps: number): string {
  return /* glsl */ `
  varying vec3 vWorldPosition;

  uniform vec3 uCameraPos;
  uniform vec3 uWindOffset;
  uniform vec3 uSunDir;
  uniform float uCoverage;
  uniform float uDensityScale;
  uniform vec3 uColorTop;
  uniform vec3 uColorBottom;
  uniform vec3 uColorBehind;
  uniform float uSunBoost;

  float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
  }

  float valueNoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
      mix(
        mix(hash(i + vec3(0.0, 0.0, 0.0)), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
        mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x),
        f.y),
      mix(
        mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
        mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x),
        f.y),
      f.z);
  }

  float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.52;
    float freq = 1.0;
    float ampSum = 0.0;
    for (int i = 0; i < 4; i++) {
      sum += amp * valueNoise(p * freq);
      ampSum += amp;
      amp *= 0.5;
      freq *= 2.05;
    }
    // Normalize to a true [0,1] range, then contrast-stretch around the
    // midpoint -- summed/averaged octaves cluster tightly near 0.5, so
    // without this the coverage threshold below would never see values
    // close enough to either extreme to read as "clear" or "solid".
    float n = sum / ampSum;
    return clamp((n - 0.5) * 1.9 + 0.5, 0.0, 1.0);
  }

  void main() {
    vec3 rayDir = normalize(vWorldPosition - uCameraPos);
    float safeY = abs(rayDir.y) < 0.02 ? (rayDir.y < 0.0 ? -0.02 : 0.02) : rayDir.y;

    float tBase = (${CLOUD_BASE_Y.toFixed(1)} - uCameraPos.y) / safeY;
    float tTop = (${CLOUD_TOP_Y.toFixed(1)} - uCameraPos.y) / safeY;
    float tNear = max(min(tBase, tTop), 0.0);
    float tFar = min(max(tBase, tTop), 900.0);

    if (tFar <= tNear) {
      gl_FragColor = vec4(0.0);
      return;
    }

    float stepSize = (tFar - tNear) / float(${steps});
    vec3 pos = uCameraPos + rayDir * tNear;
    vec3 stepVec = rayDir * stepSize;

    float accumAlpha = 0.0;
    vec3 accumColor = vec3(0.0);

    for (int i = 0; i < ${steps}; i++) {
      if (accumAlpha > 0.97) break;

      float heightFrac = clamp((pos.y - ${CLOUD_BASE_Y.toFixed(1)}) / ${(CLOUD_TOP_Y - CLOUD_BASE_Y).toFixed(1)}, 0.0, 1.0);
      float verticalFalloff = smoothstep(0.0, 0.3, heightFrac) * (1.0 - smoothstep(0.6, 1.0, heightFrac));

      vec3 samplePos = pos * uDensityScale + uWindOffset;
      float n = fbm(samplePos);
      // As coverage rises the threshold a sample must clear drops, so more
      // of the noise field qualifies as cloud -- with a soft ramp width so
      // there's always a mottled transition band, never a flat hard edge,
      // even at very high coverage.
      float coverageThreshold = mix(0.78, -0.25, uCoverage);
      float density = smoothstep(coverageThreshold, coverageThreshold + 0.3, n) * verticalFalloff;

      if (density > 0.01) {
        float sunSample = fbm((pos + uSunDir * 9.0) * uDensityScale + uWindOffset);
        float sunReach = 1.0 - clamp(sunSample * uCoverage * 1.4, 0.0, 0.85);

        vec3 col = mix(uColorBottom, uColorTop, heightFrac);
        col *= mix(0.55, 1.0 + uSunBoost, sunReach);

        float stepAlpha = clamp(density * 0.55, 0.0, 1.0);
        float contrib = stepAlpha * (1.0 - accumAlpha);
        accumColor += col * contrib;
        accumAlpha += contrib;
      }

      pos += stepVec;
    }

    float distFade = clamp(tNear / 700.0, 0.0, 1.0);
    accumColor = mix(accumColor, uColorBehind * accumAlpha, distFade * 0.45);

    gl_FragColor = vec4(accumColor, accumAlpha);
  }
`
}

/**
 * A single raymarched cloud volume: a dome mesh whose fragment shader
 * marches through a procedural 3D noise field between CLOUD_BASE_Y and
 * CLOUD_TOP_Y, accumulating density into color+alpha. Unlike camera-facing
 * sprite billboards, this has real depth -- clouds occlude/parallax against
 * each other and the terrain correctly from every angle, and the lighting
 * genuinely responds to a sun-direction density sample (a cheap self-shadow
 * proxy) instead of a flat per-sprite tint.
 */
export class VolumetricClouds implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly domeGeometry: THREE.SphereGeometry
  private readonly domeMaterial: THREE.ShaderMaterial
  private readonly dome: THREE.Mesh

  private readonly windOffset = new THREE.Vector3()
  private readonly sunDir = new THREE.Vector3()
  private readonly scratchColor = new THREE.Color()
  private readonly colorTop = new THREE.Color(0xffffff)
  private readonly colorBottom = new THREE.Color(0xdadde3)
  private readonly colorBehind = new THREE.Color(0x9fb3d0)
  private smoothedCoverage = 0

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene

    const steps = STEPS_BY_QUALITY[ctx.quality]

    this.domeGeometry = new THREE.SphereGeometry(DOME_RADIUS, 24, 16)
    this.domeMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uCameraPos: { value: ctx.camera.position },
        uWindOffset: { value: this.windOffset },
        uSunDir: { value: this.sunDir },
        uCoverage: { value: 0 },
        uDensityScale: { value: 0.016 },
        uColorTop: { value: this.colorTop },
        uColorBottom: { value: this.colorBottom },
        uColorBehind: { value: this.colorBehind },
        uSunBoost: { value: 0.3 }
      },
      vertexShader: VERTEX_SHADER,
      fragmentShader: fragmentShader(steps),
      side: THREE.BackSide,
      transparent: true,
      depthWrite: false,
      depthTest: true,
      fog: false
    })

    this.dome = new THREE.Mesh(this.domeGeometry, this.domeMaterial)
    this.dome.renderOrder = -85
    this.scene.add(this.dome)
  }

  update(dt: number, _elapsed: number, params: SceneParams): void {
    const k = 1 - Math.exp(-dt * SMOOTHING_RATE)

    const windFactor = lerp(AMBIENT_DRIFT_FLOOR, 1, clamp01(params.windSpeed))
    this.windOffset.x += Math.sin(params.windDirectionRad) * WIND_DRIFT_SPEED * windFactor * dt * 0.016
    this.windOffset.z += Math.cos(params.windDirectionRad) * WIND_DRIFT_SPEED * windFactor * dt * 0.016

    const altitude = clamp(params.sunAltitude, -1, 1)
    const elevation = Math.asin(altitude)
    const horizontal = Math.cos(elevation)
    this.sunDir.set(
      Math.sin(params.sunAzimuthRad) * horizontal,
      Math.max(altitude, 0.12),
      Math.cos(params.sunAzimuthRad) * horizontal
    )

    const targetCoverage = clamp01(params.cloudCover)
    this.smoothedCoverage += (targetCoverage - this.smoothedCoverage) * k
    this.domeMaterial.uniforms.uCoverage.value = this.smoothedCoverage

    this.updateTint(params, altitude)
  }

  dispose(): void {
    this.scene.remove(this.dome)
    this.domeGeometry.dispose()
    this.domeMaterial.dispose()
  }

  private updateTint(params: SceneParams, altitude: number): void {
    const sunsetT = clamp01(1 - Math.abs(altitude) * 2.4)
    const nightT = clamp01(-altitude * 1.4 + 0.15)

    let top: THREE.Color
    let bottom: THREE.Color
    if (params.condition === 'thunderstorm') {
      top = this.hex(0x545b66)
      bottom = this.hex(0x2c313a)
    } else if (params.condition === 'clear' || params.condition === 'partly-cloudy') {
      top = this.hex(0xffffff)
      bottom = this.hex(0xd7dde6)
    } else {
      top = this.hex(0xe4e8ee)
      bottom = this.hex(0xb9c0cc)
    }

    this.colorTop.copy(top).lerp(this.hex(0xffcf9e), sunsetT * 0.5).lerp(this.hex(0x141a26), nightT * 0.85)
    this.colorBottom.copy(bottom).lerp(this.hex(0xd98a5f), sunsetT * 0.4).lerp(this.hex(0x0a0d14), nightT * 0.9)

    this.colorBehind.set(0x9fb3d0).lerp(this.hex(0xffb37a), sunsetT * 0.6).lerp(this.hex(0x060a14), nightT)

    const dim = params.isDay ? lerp(0.75, 1, clamp01(altitude * 1.6 + 0.1)) : lerp(0.2, 0.4, clamp01(-altitude))
    this.colorTop.multiplyScalar(dim)
    this.colorBottom.multiplyScalar(dim)

    this.domeMaterial.uniforms.uSunBoost.value = lerp(0.15, 0.45, sunsetT)
  }

  private hex(value: number): THREE.Color {
    return this.scratchColor.set(value)
  }
}
