import * as THREE from 'three'
import type { SceneContext, SceneEffect, SceneParams } from './contract'
import { makeRadialTexture } from './textures'
import { clamp, clamp01, lerp } from '../utils/math'

/** Radius of the sky dome mesh itself. */
const SKY_RADIUS = 450
/** Radius the sun/moon billboards orbit on (must stay inside the dome). */
const CELESTIAL_RADIUS = 420

const skyVertexShader = /* glsl */ `
  varying vec3 vWorldPosition;

  void main() {
    vec4 worldPosition = modelMatrix * vec4(position, 1.0);
    vWorldPosition = worldPosition.xyz;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

const skyFragmentShader = /* glsl */ `
  varying vec3 vWorldPosition;

  uniform vec3 uZenithColor;
  uniform vec3 uMidColor;
  uniform vec3 uHorizonColor;
  uniform vec3 uGroundColor;
  uniform float uSunAltitude;
  uniform float uFlatness;

  void main() {
    float h = normalize(vWorldPosition).y;

    float upperT = pow(clamp(h, 0.0, 1.0), 0.55);
    vec3 upperColor = mix(uMidColor, uZenithColor, upperT);

    float lowerT = pow(clamp(-h, 0.0, 1.0), 0.5);
    vec3 lowerColor = mix(uMidColor, uGroundColor, lowerT);

    vec3 baseColor = h >= 0.0 ? upperColor : lowerColor;

    // The horizon glow band widens as the sun nears the horizon
    // (sunrise/sunset bleed a lot more color across the sky).
    float bandWidth = mix(6.0, 2.6, smoothstep(0.0, 0.35, abs(uSunAltitude)));
    float horizonBand = 1.0 - clamp(abs(h) * bandWidth, 0.0, 1.0);
    vec3 skyColor = mix(baseColor, uHorizonColor, horizonBand);

    // Flatten contrast for overcast / foggy / stormy conditions.
    vec3 flatColor = mix(uHorizonColor, uZenithColor, 0.55);
    skyColor = mix(skyColor, flatColor, uFlatness);

    gl_FragColor = vec4(skyColor, 1.0);
  }
`

/**
 * Sky dome + sun + moon + celestial lighting.
 *
 * Owns `ctx.sunLight` and `ctx.hemiLight` per the contract's ownership
 * rules: this is the only effect that repositions/recolors them.
 */
export class Sky implements SceneEffect {
  private readonly scene: THREE.Scene
  private readonly domeGeometry: THREE.SphereGeometry
  private readonly domeMaterial: THREE.ShaderMaterial
  private readonly dome: THREE.Mesh

  private readonly sunTexture: THREE.CanvasTexture
  private readonly moonTexture: THREE.CanvasTexture
  private readonly sunMaterial: THREE.SpriteMaterial
  private readonly moonMaterial: THREE.SpriteMaterial
  private readonly sunSprite: THREE.Sprite
  private readonly moonSprite: THREE.Sprite

  private readonly sunLight: THREE.DirectionalLight
  private readonly hemiLight: THREE.HemisphereLight

  // Scratch objects reused every frame -- never reallocated in update().
  private readonly scratchColor = new THREE.Color()
  private readonly sunDiscColor = new THREE.Color()
  private readonly sunLightColor = new THREE.Color()
  private readonly hemiSkyColor = new THREE.Color()
  private readonly hemiGroundColor = new THREE.Color()
  private readonly zenithColor = new THREE.Color()
  private readonly midColor = new THREE.Color()
  private readonly horizonColor = new THREE.Color()
  private readonly groundColor = new THREE.Color()
  private readonly sunDir = new THREE.Vector3()
  private readonly moonDir = new THREE.Vector3()

  constructor(ctx: SceneContext) {
    this.scene = ctx.scene
    this.sunLight = ctx.sunLight
    this.hemiLight = ctx.hemiLight

    // --- Sky dome -------------------------------------------------------
    this.domeGeometry = new THREE.SphereGeometry(SKY_RADIUS, 32, 16)
    this.domeMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uZenithColor: { value: new THREE.Color(0x1c5fd6) },
        uMidColor: { value: new THREE.Color(0x5b9de0) },
        uHorizonColor: { value: new THREE.Color(0xdfe9f2) },
        uGroundColor: { value: new THREE.Color(0x0a0e14) },
        uSunAltitude: { value: 0.5 },
        uFlatness: { value: 0 }
      },
      vertexShader: skyVertexShader,
      fragmentShader: skyFragmentShader,
      side: THREE.BackSide,
      depthWrite: false,
      fog: false
    })
    this.dome = new THREE.Mesh(this.domeGeometry, this.domeMaterial)
    this.dome.renderOrder = -100
    this.dome.matrixAutoUpdate = false
    this.dome.updateMatrix()
    this.scene.add(this.dome)

    // --- Sun / moon billboards ------------------------------------------
    this.sunTexture = makeRadialTexture('rgba(255,250,230,1)', 'rgba(255,180,80,0)', 128, 0.05)
    this.moonTexture = makeRadialTexture('rgba(235,245,255,1)', 'rgba(190,210,235,0)', 128, 0.05)

    this.sunMaterial = new THREE.SpriteMaterial({
      map: this.sunTexture,
      color: 0xffffff,
      transparent: true,
      depthWrite: false,
      fog: false,
      blending: THREE.AdditiveBlending
    })
    this.moonMaterial = new THREE.SpriteMaterial({
      map: this.moonTexture,
      color: 0xffffff,
      transparent: true,
      depthWrite: false,
      fog: false,
      blending: THREE.AdditiveBlending
    })

    this.sunSprite = new THREE.Sprite(this.sunMaterial)
    this.sunSprite.scale.setScalar(46)
    this.sunSprite.renderOrder = -90
    this.scene.add(this.sunSprite)

    this.moonSprite = new THREE.Sprite(this.moonMaterial)
    this.moonSprite.scale.setScalar(30)
    this.moonSprite.renderOrder = -90
    this.scene.add(this.moonSprite)

    // --- Light rig --------------------------------------------------------
    // DirectionalLight needs its target parented into the scene graph for
    // its matrixWorld (and therefore the light direction) to update.
    this.sunLight.target.position.set(0, 0, 0)
    this.scene.add(this.sunLight.target)
  }

  update(_dt: number, _elapsed: number, params: SceneParams): void {
    const altitude = clamp(params.sunAltitude, -1, 1)
    const azimuth = params.sunAzimuthRad

    const overcast = clamp01(
      Math.max(
        params.cloudCover,
        params.condition === 'cloudy' || params.condition === 'thunderstorm'
          ? 0.6
          : params.condition === 'fog'
            ? 0.5
            : 0
      )
    )

    // Direction toward the sun (Y up). altitude is sin(elevation), so
    // asin(altitude) recovers the true elevation angle for the horizontal
    // (cosine) component.
    const elevation = Math.asin(altitude)
    const horizontal = Math.cos(elevation)
    this.sunDir.set(Math.sin(azimuth) * horizontal, altitude, Math.cos(azimuth) * horizontal)
    // The moon sits opposite the sun on the celestial sphere: azimuth + PI
    // and mirrored altitude fall out of simply negating the sun direction.
    this.moonDir.copy(this.sunDir).multiplyScalar(-1)

    // sunsetT peaks when the sun sits right on the horizon (either rising
    // or setting); nightT ramps up once it drops well below it.
    const sunsetT = clamp01(1 - Math.abs(altitude) * 3.2)
    const nightT = clamp01(-altitude * 1.4 + 0.15)

    // ---- Sky dome colors -------------------------------------------------
    // Day tones lean soft/pastel rather than saturated, so the bright glass
    // UI floats over an airy, commercial-feeling sky instead of a deep one.
    this.zenithColor.set(0x4585dd).lerp(this.hex(0x2c3a6b), sunsetT * 0.6)
    this.zenithColor.lerp(this.hex(0x03050c), nightT)

    this.midColor.set(0x8fbdec).lerp(this.hex(0xd98a5f), sunsetT)
    this.midColor.lerp(this.hex(0x060912), nightT)

    this.horizonColor.set(0xeef4fa).lerp(this.hex(0xffb37a), sunsetT)
    this.horizonColor.lerp(this.hex(0x0b1220), nightT)

    this.groundColor.set(0x7a8a94).lerp(this.hex(0x020306), clamp01(nightT + 0.3))

    // Desaturate/flatten the gradient for cloudy, foggy or stormy skies.
    const flatness = clamp01(overcast * 0.85 + (1 - params.visibility) * 0.3)

    const u = this.domeMaterial.uniforms
    ;(u.uZenithColor.value as THREE.Color).copy(this.zenithColor)
    ;(u.uMidColor.value as THREE.Color).copy(this.midColor)
    ;(u.uHorizonColor.value as THREE.Color).copy(this.horizonColor)
    ;(u.uGroundColor.value as THREE.Color).copy(this.groundColor)
    u.uSunAltitude.value = altitude
    u.uFlatness.value = flatness

    // ---- Sun / moon billboards -------------------------------------------
    this.sunSprite.position.copy(this.sunDir).multiplyScalar(CELESTIAL_RADIUS)
    this.moonSprite.position.copy(this.moonDir).multiplyScalar(CELESTIAL_RADIUS)

    // Cross-fade smoothly around the horizon instead of a hard cutoff.
    const sunVisibility = clamp01(altitude * 8 + 0.5)
    const moonVisibility = clamp01(-altitude * 8 + 0.5)
    const haze = 1 - flatness * 0.85
    this.sunMaterial.opacity = sunVisibility * haze
    this.moonMaterial.opacity = moonVisibility * haze * 0.85

    // Warmer near the horizon, pale gold near zenith; disc swells slightly
    // low in the sky, mimicking atmospheric magnification.
    this.sunDiscColor.set(0xfff4d6).lerp(this.hex(0xff8c42), sunsetT)
    this.sunMaterial.color.copy(this.sunDiscColor)
    this.sunSprite.scale.setScalar(46 * lerp(1, 1.35, sunsetT))
    this.moonSprite.scale.setScalar(30 * lerp(1, 1.15, sunsetT))

    // ---- Celestial lights --------------------------------------------------
    this.sunLight.position.copy(this.sunDir).multiplyScalar(200)

    const dayIntensity = clamp01(altitude * 1.6 + 0.1)
    const overcastDim = lerp(1, 0.22, overcast)
    const visDim = lerp(0.55, 1, params.visibility)
    this.sunLight.intensity = dayIntensity * 2.6 * overcastDim * visDim

    this.sunLightColor.set(0xffffff).lerp(this.hex(0xffb46b), sunsetT)
    this.sunLightColor.lerp(this.hex(0x35507a), nightT * 0.7)
    this.sunLight.color.copy(this.sunLightColor)

    this.hemiSkyColor.set(0xbfd9ff).lerp(this.hex(0xffcf9e), sunsetT * 0.5)
    this.hemiSkyColor.lerp(this.hex(0x0b1830), nightT)
    this.hemiGroundColor.set(0x6b6255).lerp(this.hex(0x1a1f2a), nightT)

    this.hemiLight.color.copy(this.hemiSkyColor)
    this.hemiLight.groundColor.copy(this.hemiGroundColor)
    const hemiBase = clamp01(altitude * 0.9 + 0.55)
    this.hemiLight.intensity = Math.max(0.08, hemiBase * 1.1 * lerp(1, 0.45, overcast))
  }

  dispose(): void {
    this.scene.remove(this.dome)
    this.domeGeometry.dispose()
    this.domeMaterial.dispose()

    this.scene.remove(this.sunSprite)
    this.scene.remove(this.moonSprite)
    this.sunMaterial.dispose()
    this.moonMaterial.dispose()
    this.sunTexture.dispose()
    this.moonTexture.dispose()

    this.scene.remove(this.sunLight.target)
  }

  /** Sets the shared scratch Color to `hex` and returns it, to avoid per-call allocation in lerp chains. */
  private hex(hex: number): THREE.Color {
    return this.scratchColor.set(hex)
  }
}
