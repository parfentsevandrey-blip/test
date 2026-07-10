import * as THREE from 'three'
import type { SceneContext, SceneEffect, SceneParams } from './contract'
import { makeMoonDiscTexture, makeSunDiscTexture } from './textures'
import { clamp, clamp01, lerp } from '../utils/math'

/** Radius of the sky dome mesh itself. */
const SKY_RADIUS = 450
/** Radius the sun/moon billboards orbit on (must stay inside the dome). */
const CELESTIAL_RADIUS = 420
/** Exponential smoothing rate (1/s) for overcast/visibility, matching Fog's
 * SMOOTHING_RATE so a weather refresh rolls the whole sky -- gradient
 * flatness, haze, sun/moon dimming -- from e.g. clear to storm over a
 * couple of seconds instead of popping instantly. */
const SMOOTHING_RATE = 1.6

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
  uniform float uTime;
  uniform vec2 uWind;
  uniform float uHaze;
  uniform float uStriationStrength;
  uniform vec3 uAntiSunDir;
  uniform float uDuskStrength;
  uniform float uCloudPatchiness;
  uniform vec3 uStrikeDir;
  uniform float uStrikeBrightness;

  void main() {
    vec3 dir = normalize(vWorldPosition);
    float h = dir.y;

    float upperT = pow(clamp(h, 0.0, 1.0), 0.55);
    vec3 upperColor = mix(uMidColor, uZenithColor, upperT);

    // Gentler horizon->ground falloff (0.85 vs 0.5): with the terrain gone the
    // lower hemisphere is visible along the frame bottom, so it must read as a
    // soft ground-haze, never a hard band.
    float lowerT = pow(clamp(-h, 0.0, 1.0), 0.85);
    vec3 lowerColor = mix(uMidColor, uGroundColor, lowerT);

    vec3 baseColor = h >= 0.0 ? upperColor : lowerColor;

    // The horizon glow band widens as the sun nears the horizon
    // (sunrise/sunset bleed a lot more color across the sky).
    float bandWidth = mix(6.0, 2.6, smoothstep(0.0, 0.35, abs(uSunAltitude)));
    float horizonBand = 1.0 - clamp(abs(h) * bandWidth, 0.0, 1.0);
    vec3 skyColor = mix(baseColor, uHorizonColor, horizonBand);

    // Whisper-soft drifting atmospheric striations — a few gentle, near-
    // horizontal bands that slowly wave and drift with the wind so a clear sky
    // isn't dead flat. Deliberately NOT 2D noise blobs (that was the old
    // volumetric-cloud look we're retiring); the mix factor stays a few
    // percent so it reads as haze layering, not cloud.
    float ang = atan(dir.z, dir.x);
    float drift = uWind.x * uTime * 0.03 + uWind.y * uTime * 0.02;
    float s1 = sin(h * 9.0 + sin(ang * 2.0 + drift) * 1.2 + drift * 0.5);
    float s2 = sin(h * 16.0 - ang * 1.5 + drift * 0.8);
    float haze = 0.5 + 0.28 * s1 + 0.14 * s2;
    float band = smoothstep(0.04, 0.32, h) * (1.0 - smoothstep(0.5, 0.9, h));
    vec3 hazeColor = mix(uHorizonColor, uZenithColor, 0.42);
    skyColor = mix(skyColor, hazeColor, clamp(band * uHaze * haze, 0.0, 1.0) * uStriationStrength);

    // Anti-twilight arch: the Belt of Venus (a dusty pink-mauve band) sitting
    // just above Earth's own shadow rising (a cooler steel-blue band) on the
    // side of the sky opposite the sun -- only visible during the ~10 minutes
    // around sunrise/sunset, and only on the anti-solar side of the dome.
    float antiSun = dot(dir, uAntiSunDir);
    float duskCone = pow(clamp(antiSun, 0.0, 1.0), 4.0);
    float beltBand = smoothstep(0.02, 0.10, h) * (1.0 - smoothstep(0.14, 0.24, h));
    float shadowBand = smoothstep(-0.02, 0.02, h) * (1.0 - smoothstep(0.02, 0.09, h));
    vec3 beltColor = vec3(0.68, 0.52, 0.58);
    vec3 shadowColor = vec3(0.22, 0.29, 0.42);
    skyColor = mix(skyColor, beltColor, beltBand * duskCone * uDuskStrength * 0.5);
    skyColor = mix(skyColor, shadowColor, shadowBand * duskCone * uDuskStrength * 0.4);

    // Patchy cloud-shadow flicker: a restless drifting darkening for partly-
    // cloudy skies (peaks at ~50% cloud cover, silent at 0% or 100%), reusing
    // the drift phase already computed for the striation haze above.
    float shadowGate = uCloudPatchiness * (1.0 - uCloudPatchiness) * 4.0;
    float patchNoise = sin(dir.x * 2.6 + drift * 0.6) * sin(dir.z * 3.1 - drift * 0.4) * 0.5 + 0.5;
    float shadowMix = shadowGate * smoothstep(0.35, 0.75, patchNoise) * smoothstep(0.05, 0.4, h);
    skyColor *= mix(1.0, 0.90, shadowMix);

    // Flatten contrast for overcast / foggy / stormy conditions.
    vec3 flatColor = mix(uHorizonColor, uZenithColor, 0.55);
    skyColor = mix(skyColor, flatColor, uFlatness);

    // Lightning-lit sky patch: the dome brightens locally toward the actual
    // strike direction (not a full-screen wash) -- placed AFTER the overcast
    // flattening above so it still reads clearly during a storm, exactly
    // when it matters most.
    float strikeCone = pow(clamp(dot(dir, normalize(uStrikeDir)), 0.0, 1.0), 8.0);
    skyColor = mix(skyColor, vec3(0.92, 0.95, 1.0), strikeCone * uStrikeBrightness * 0.6);

    gl_FragColor = vec4(skyColor, 1.0);
  }
`

// The moon is a real billboarded disc (not a THREE.Sprite -- SpriteMaterial's
// built-in shader can't carry the custom terminator varyings below) that
// bills itself toward the camera purely in the vertex shader: transform the
// mesh's own origin into view space via modelViewMatrix, then offset by the
// local quad corner scaled by uScale, all still in view space. That's the
// same trick THREE.SpriteMaterial uses internally, so the mesh's own
// rotation is irrelevant and no per-frame quaternion copy is needed.
const MOON_VERTEX_SHADER = /* glsl */ `
  uniform float uScale;
  varying vec2 vUv;

  void main() {
    vUv = uv * 2.0 - 1.0;
    vec4 mvPosition = modelViewMatrix * vec4(0.0, 0.0, 0.0, 1.0);
    mvPosition.xy += position.xy * uScale;
    gl_Position = projectionMatrix * mvPosition;
  }
`

// Real crescent/gibbous terminator via the same two-same-radius-circle
// technique SunCard's 2D moon glyph already uses (a shadow disc at the
// origin, a lit disc offset by uOffsetX, both clipped to the origin disc's
// circular bounds) -- proven, cheap, and visually convincing despite not
// being the astronomically-exact ellipse terminator.
const MOON_FRAGMENT_SHADER = /* glsl */ `
  uniform sampler2D uMap;
  uniform float uOffsetX;
  uniform float uShadowDarken;
  uniform float uOpacity;
  varying vec2 vUv;

  void main() {
    float r = length(vUv);
    float edgeAlpha = 1.0 - smoothstep(0.94, 1.0, r);
    if (edgeAlpha <= 0.002) {
      discard;
    }

    vec2 uv01 = vUv * 0.5 + 0.5;
    vec3 texColor = texture2D(uMap, uv01).rgb;

    float distToLit = length(vUv - vec2(uOffsetX, 0.0));
    float lit = 1.0 - smoothstep(0.98, 1.02, distToLit);
    vec3 color = texColor * mix(uShadowDarken, 1.0, lit);

    gl_FragColor = vec4(color, edgeAlpha * uOpacity);
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
  private readonly sunSprite: THREE.Sprite
  private readonly moonGeometry: THREE.PlaneGeometry
  private readonly moonMaterial: THREE.ShaderMaterial
  private readonly moonMesh: THREE.Mesh

  private readonly sunLight: THREE.DirectionalLight
  private readonly hemiLight: THREE.HemisphereLight

  // Shared cross-effect signals (see contract.ts): lightning brightens a local
  // dome patch, a gust temporarily stirs the striation band harder.
  private readonly lightningBrightness: { value: number }
  private readonly lightningDir: THREE.Vector3
  private readonly windGust: { value: number }

  // Smoothed toward the raw weather-derived targets each frame (see update()).
  private smoothedOvercast = 0
  private smoothedVisibility = 1

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
    this.lightningBrightness = ctx.lightningBrightness
    this.lightningDir = ctx.lightningDir
    this.windGust = ctx.windGust

    // --- Sky dome -------------------------------------------------------
    this.domeGeometry = new THREE.SphereGeometry(SKY_RADIUS, 32, 16)
    this.domeMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uZenithColor: { value: new THREE.Color(0x1c5fd6) },
        uMidColor: { value: new THREE.Color(0x5b9de0) },
        uHorizonColor: { value: new THREE.Color(0xdfe9f2) },
        uGroundColor: { value: new THREE.Color(0x0a0e14) },
        uSunAltitude: { value: 0.5 },
        uFlatness: { value: 0 },
        uTime: { value: 0 },
        uWind: { value: new THREE.Vector2(1, 0) },
        uHaze: { value: 0.4 },
        uStriationStrength: { value: 0.13 },
        uAntiSunDir: { value: new THREE.Vector3(0, -1, 0) },
        uDuskStrength: { value: 0 },
        uCloudPatchiness: { value: 0 },
        uStrikeDir: { value: new THREE.Vector3(0, 1, 0) },
        uStrikeBrightness: { value: 0 }
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

    // --- Sun billboard ----------------------------------------------------
    // A real photospheric disc (baked once, see textures.ts) rather than a
    // formless 2-stop blur; still an additive glow sprite, matching the
    // sun's existing bright-light-source treatment.
    this.sunTexture = makeSunDiscTexture()
    this.sunMaterial = new THREE.SpriteMaterial({
      map: this.sunTexture,
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

    // --- Moon: a real billboarded disc with a genuine crescent/gibbous
    // terminator (see MOON_VERTEX_SHADER/MOON_FRAGMENT_SHADER above) instead
    // of a flat always-full glow sprite. Normal (not additive) blending,
    // since it's now a lit/shadowed surface that should occlude the sky
    // behind it, not a light source.
    this.moonTexture = makeMoonDiscTexture()
    this.moonMaterial = new THREE.ShaderMaterial({
      uniforms: {
        uMap: { value: this.moonTexture },
        uScale: { value: 30 },
        uOffsetX: { value: 2 },
        uShadowDarken: { value: 0.15 },
        uOpacity: { value: 0 }
      },
      vertexShader: MOON_VERTEX_SHADER,
      fragmentShader: MOON_FRAGMENT_SHADER,
      transparent: true,
      depthWrite: false,
      fog: false
    })
    this.moonGeometry = new THREE.PlaneGeometry(1, 1)
    this.moonMesh = new THREE.Mesh(this.moonGeometry, this.moonMaterial)
    this.moonMesh.renderOrder = -90
    this.scene.add(this.moonMesh)

    // --- Light rig --------------------------------------------------------
    // DirectionalLight needs its target parented into the scene graph for
    // its matrixWorld (and therefore the light direction) to update.
    this.sunLight.target.position.set(0, 0, 0)
    this.scene.add(this.sunLight.target)
  }

  update(dt: number, elapsed: number, params: SceneParams): void {
    const altitude = clamp(params.sunAltitude, -1, 1)
    const azimuth = params.sunAzimuthRad

    const overcastTarget = clamp01(
      Math.max(
        params.cloudCover,
        params.condition === 'cloudy' || params.condition === 'thunderstorm'
          ? 0.6
          : params.condition === 'fog'
            ? 0.5
            : 0
      )
    )
    // Weather data refreshes snap cloudCover/visibility to new values instantly;
    // smoothing them here (same exponential-lerp as Fog's density) is what makes
    // every value derived below -- flatness, haze, sun/moon dimming -- glide.
    const smoothing = 1 - Math.exp(-dt * SMOOTHING_RATE)
    this.smoothedOvercast += (overcastTarget - this.smoothedOvercast) * smoothing
    this.smoothedVisibility += (params.visibility - this.smoothedVisibility) * smoothing
    const overcast = this.smoothedOvercast
    const visibility = this.smoothedVisibility

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

    // Soft hazy stone that continues the horizon downward — not a dark
    // silhouette band (the terrain that used to hide it is gone).
    this.groundColor.set(0xb9c2cb).lerp(this.hex(0x10161f), clamp01(nightT * 0.9 + 0.12))

    // Desaturate/flatten the gradient for cloudy, foggy or stormy skies.
    const flatness = clamp01(overcast * 0.85 + (1 - visibility) * 0.3)

    const u = this.domeMaterial.uniforms
    ;(u.uZenithColor.value as THREE.Color).copy(this.zenithColor)
    ;(u.uMidColor.value as THREE.Color).copy(this.midColor)
    ;(u.uHorizonColor.value as THREE.Color).copy(this.horizonColor)
    ;(u.uGroundColor.value as THREE.Color).copy(this.groundColor)
    u.uSunAltitude.value = altitude
    u.uFlatness.value = flatness
    u.uTime.value = elapsed
    ;(u.uWind.value as THREE.Vector2).set(
      Math.sin(params.windDirectionRad),
      Math.cos(params.windDirectionRad)
    ).multiplyScalar(0.4 + params.windSpeed)
    // Faint even under a clear sky, a touch more with cloud cover — but capped
    // low so it never becomes a cloud.
    u.uHaze.value = 0.35 + overcast * 0.45
    // Stronger wind visibly stirs the striation band; near-calm keeps it a bare
    // whisper. A live gust (shared ctx.windGust) temporarily stirs it harder
    // still, so the sky visibly surges in sync with the camera/mist/precip gust.
    u.uStriationStrength.value = lerp(0.06, 0.2, clamp01(params.windSpeed)) * (1 + this.windGust.value * 0.6)

    // Anti-twilight arch (Belt of Venus) peaks in the same dusk/dawn window
    // the sunset colors already peak in, on the side of the sky opposite the
    // sun -- moonDir is already that exact antipodal unit vector.
    ;(u.uAntiSunDir.value as THREE.Vector3).copy(this.moonDir)
    u.uDuskStrength.value = sunsetT * (1 - nightT)
    // Patchy cloud-shadow flicker uses the already-smoothed overcast so a
    // weather refresh never pops the darkening in/out, matching every other
    // weather-reactive value in this shader.
    u.uCloudPatchiness.value = overcast
    // Lightning-lit sky patch: whatever Lightning last wrote to the shared signal.
    ;(u.uStrikeDir.value as THREE.Vector3).copy(this.lightningDir)
    u.uStrikeBrightness.value = this.lightningBrightness.value

    // ---- Sun / moon billboards -------------------------------------------
    this.sunSprite.position.copy(this.sunDir).multiplyScalar(CELESTIAL_RADIUS)
    this.moonMesh.position.copy(this.moonDir).multiplyScalar(CELESTIAL_RADIUS)

    // Cross-fade smoothly around the horizon instead of a hard cutoff.
    const sunVisibility = clamp01(altitude * 8 + 0.5)
    const moonVisibility = clamp01(-altitude * 8 + 0.5)
    const haze = 1 - flatness * 0.85
    this.sunMaterial.opacity = sunVisibility * haze
    this.moonMaterial.uniforms.uOpacity.value = moonVisibility * haze * 0.85

    // Warmer near the horizon, pale gold near zenith; disc swells slightly
    // low in the sky, mimicking atmospheric magnification.
    this.sunDiscColor.set(0xfff4d6).lerp(this.hex(0xff8c42), sunsetT)
    // A faint, narrow-window green-flash tint right at the true horizon (real
    // atmospheric-optics phenomenon: differential refraction separates the
    // rim's blue/green from its red body) -- gated off under cloud/fog so it
    // never shows through anything but a genuinely clear horizon.
    const flashWindow = clamp01(1 - Math.abs(altitude) / 0.018)
    const flashGate = flashWindow * clamp01(1 - overcast * 2)
    this.sunDiscColor.lerp(this.hex(0x8fe0c0), flashGate * 0.12)
    this.sunMaterial.color.copy(this.sunDiscColor)
    this.sunSprite.scale.setScalar(46 * lerp(1, 1.35, sunsetT))
    this.moonMaterial.uniforms.uScale.value = 30 * lerp(1, 1.15, sunsetT)

    // Real crescent/gibbous terminator: same two-same-radius-circle offset
    // technique as SunCard's 2D moon glyph (utils/moonPhase.ts is the single
    // shared source of the underlying synodic-month math for both).
    const moonSign = params.moonPhaseFrac < 0.5 ? 1 : -1
    this.moonMaterial.uniforms.uOffsetX.value = moonSign * 2 * (1 - params.moonIllumination)

    // ---- Celestial lights --------------------------------------------------
    this.sunLight.position.copy(this.sunDir).multiplyScalar(200)

    const dayIntensity = clamp01(altitude * 1.6 + 0.1)
    const overcastDim = lerp(1, 0.22, overcast)
    const visDim = lerp(0.55, 1, visibility)
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
    this.sunMaterial.dispose()
    this.sunTexture.dispose()

    this.scene.remove(this.moonMesh)
    this.moonGeometry.dispose()
    this.moonMaterial.dispose()
    this.moonTexture.dispose()

    this.scene.remove(this.sunLight.target)
  }

  /** Sets the shared scratch Color to `hex` and returns it, to avoid per-call allocation in lerp chains. */
  private hex(hex: number): THREE.Color {
    return this.scratchColor.set(hex)
  }
}
