import * as THREE from 'three'
import type { WeatherData } from '../types/weather'
import type { SceneContext, SceneEffect, SceneParams } from './contract'
import type { WeatherCondition } from '../utils/weatherCondition'
import { getBaseVisibility, getConditionInfo } from '../utils/weatherCondition'
import { computeSunPosition, getTimeOfDayFrac, toAbsoluteInstant } from '../utils/time'
import { getMoonIlluminatedFraction, getMoonPhase } from '../utils/moonPhase'
import { clamp, clamp01, degToRad, lerp } from '../utils/math'
import { Sky } from './Sky'
import { Stars } from './Stars'
import { ShootingStars } from './ShootingStars'
import { Precipitation } from './Precipitation'
import { Lightning } from './Lightning'
import { Fog } from './Fog'
import { PostFX } from './PostFX'

const DEFAULT_PARAMS: SceneParams = {
  condition: 'clear',
  isDay: true,
  cloudCover: 0.15,
  precipitationIntensity: 0,
  windSpeed: 0.15,
  windDirectionRad: 0,
  sunAltitude: 0.6,
  sunAzimuthRad: Math.PI * 0.35,
  timeOfDayFrac: 0.45,
  visibility: 1,
  thunderActive: false,
  temperatureC: 18,
  moonIllumination: 0.5,
  moonPhaseFrac: 0.25
}

/** Cheap smoothstep, matching GLSL semantics -- used by the shared wind-gust envelope below. */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp01((x - edge0) / (edge1 - edge0))
  return t * t * (3 - 2 * t)
}

/** Wraps an angle difference into [-PI, PI] so a yaw/azimuth delta never takes the "long way around". */
function normalizeAngleDelta(angle: number): number {
  let a = angle % (Math.PI * 2)
  if (a > Math.PI) a -= Math.PI * 2
  if (a < -Math.PI) a += Math.PI * 2
  return a
}

/**
 * Frame-rate cap for the ambient scene. The flythrough is a slow, soft
 * background behind the cards, so ~40fps is visually indistinguishable from
 * 60/120/144Hz while doing a fraction of the (raymarched-cloud + bloom) GPU
 * work — a big power/heat win, especially on high-refresh displays where an
 * uncapped loop would otherwise render 120-240 frames a second for no benefit.
 */
const TARGET_FRAME_MS = 1000 / 40

/**
 * Owns the Three.js scene/camera/renderer and drives every visual effect
 * from a single SceneParams snapshot computed each frame from the latest
 * WeatherData plus the real-time clock (so the sun/moon keep moving between
 * weather refreshes).
 */
export class SceneManager {
  private readonly ctx: SceneContext
  private readonly effects: SceneEffect[]
  private readonly lightning: Lightning
  private readonly postFX: PostFX
  private readonly clock = new THREE.Clock()
  private weather: WeatherData | null = null
  private rafId: number | null = null
  private running = false
  /** Own elapsed accumulator (dt-summed) so pausing never resets the clock. */
  private elapsedTime = 0
  private lastRenderTs = 0
  /** Smoothed toward the per-frame gust target -- see updateWindGust(). */
  private smoothedGust = 0

  // --- Camera composition state (see updateCamera) ---
  /** Slow-orbit phase, advanced every frame; folds a ~42min-period creep into yaw so a long-lived session never repeats the same short loop. */
  private longDriftPhase = 0
  /** Exponentially smoothed wind-roll (radians) -- see updateCamera's wind-roll-parallax term. */
  private smoothedRoll = 0
  private prevCondition: WeatherCondition | null = null
  private prevThunderActive = false
  private prevIsDay = true
  /** Elapsed-seconds timestamp of the last condition/thunder/day-night transition, plus the offsets it kicked off. */
  private transitionStartTime = -Infinity
  private transitionYawOffset = 0
  private transitionPitchOffset = 0
  /** Last lightning strike timestamp this camera has already reacted to, plus the flinch it kicked off. */
  private seenStrikeAt = -Infinity
  private flinchStartTime = -Infinity
  private flinchYawTarget = 0
  private flinchPitchTarget = 0

  constructor(canvas: HTMLCanvasElement) {
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 1200)
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

    const sunLight = new THREE.DirectionalLight(0xffffff, 1)
    const hemiLight = new THREE.HemisphereLight(0x88aadd, 0x223344, 0.4)
    scene.add(sunLight, hemiLight)

    this.ctx = {
      scene,
      camera,
      renderer,
      sunLight,
      hemiLight,
      quality: 'high',
      lightningBrightness: { value: 0 },
      lightningDir: new THREE.Vector3(0, 1, 0),
      windGust: { value: 0 }
    }

    // Serene-sky composition: the atmospheric gradient dome + sun/moon,
    // subtle night stars + occasional shooting stars, weather-gated fog haze,
    // real precipitation and storm lightning. The old flythrough extras
    // (terrain silhouette, volumetric cloud blobs, birds, rainbows, sun rays)
    // are retired for a calm, premium desktop backdrop. Their source files
    // stay on disk, dormant — nothing constructs them, so they cost nothing.
    this.lightning = new Lightning(this.ctx)
    this.effects = [
      new Sky(this.ctx),
      new Stars(this.ctx),
      new ShootingStars(this.ctx),
      new Fog(this.ctx),
      new Precipitation(this.ctx),
      this.lightning
    ]
    this.postFX = new PostFX(this.ctx)

    const { clientWidth, clientHeight } = canvas.parentElement ?? { clientWidth: window.innerWidth, clientHeight: window.innerHeight }
    this.resize(clientWidth || window.innerWidth, clientHeight || window.innerHeight)
  }

  setWeatherData(weather: WeatherData | null): void {
    this.weather = weather
  }

  /** Toggles the Win95 retro look: the scene renders through a pixelation/posterize pass. */
  setRetro(enabled: boolean): void {
    this.postFX.setRetro(enabled)
  }

  resize(width: number, height: number): void {
    if (width <= 0 || height <= 0) return
    this.ctx.camera.aspect = width / height
    this.ctx.camera.updateProjectionMatrix()
    this.ctx.renderer.setSize(width, height)
    this.postFX.resize(width, height)
  }

  start(): void {
    if (this.running) return
    this.running = true
    this.clock.start()
    // Stop rendering entirely while the window is hidden/minimized (Electron
    // background windows otherwise keep the rAF loop — and the GPU — running).
    document.addEventListener('visibilitychange', this.onVisibilityChange)
    if (!document.hidden) this.scheduleLoop()
  }

  stop(): void {
    this.running = false
    document.removeEventListener('visibilitychange', this.onVisibilityChange)
    if (this.rafId !== null) cancelAnimationFrame(this.rafId)
    this.rafId = null
  }

  private scheduleLoop(): void {
    if (this.rafId !== null) return
    this.lastRenderTs = 0
    const tick = (ts: number): void => {
      this.rafId = requestAnimationFrame(tick)
      // Frame-rate cap: skip frames that arrive sooner than the target period,
      // so a 120/144Hz display doesn't render 2-4x more than the scene needs.
      if (this.lastRenderTs !== 0 && ts - this.lastRenderTs < TARGET_FRAME_MS) return
      this.lastRenderTs = ts

      const dt = Math.min(this.clock.getDelta(), 0.1)
      this.elapsedTime += dt
      const elapsed = this.elapsedTime
      const params = this.computeParams()

      this.updateWindGust(dt, elapsed, params)
      this.updateCamera(dt, elapsed, params)
      for (const effect of this.effects) effect.update(dt, elapsed, params)
      this.postFX.update(dt, elapsed, params)
      this.postFX.render(dt)
    }
    this.rafId = requestAnimationFrame(tick)
  }

  private readonly onVisibilityChange = (): void => {
    if (!this.running) return
    if (document.hidden) {
      if (this.rafId !== null) cancelAnimationFrame(this.rafId)
      this.rafId = null
    } else {
      // Consume the whole hidden-time delta so the scene resumes exactly where
      // it paused instead of lurching forward by the time spent hidden.
      this.clock.getDelta()
      this.scheduleLoop()
    }
  }

  dispose(): void {
    this.stop()
    for (const effect of this.effects) effect.dispose()
    this.postFX.dispose()
    this.ctx.renderer.dispose()
  }

  /**
   * One canonical "a gust is happening right now" envelope, shared via
   * ctx.windGust so camera jitter, Sky's striation, Fog's mist drift and
   * Precipitation's turbulence all react to the same gust instead of each
   * rolling its own private (and inevitably out-of-sync) sine noise. Two
   * incommensurate slow sines feed a smoothstep gate so gusts build and
   * ease over roughly a second and a half rather than snapping or repeating
   * on an obvious beat; scaled by windSpeed so dead calm stays silent.
   */
  private updateWindGust(dt: number, elapsed: number, params: SceneParams): void {
    const gustRaw = (Math.sin(elapsed * 0.083) * Math.sin(elapsed * 0.031 + 1.3) + 1) * 0.5
    const gustTarget = smoothstep(0.55, 0.9, gustRaw) * clamp01(params.windSpeed)
    this.smoothedGust += (gustTarget - this.smoothedGust) * (1 - Math.exp(-dt * 0.6))
    this.ctx.windGust.value = this.smoothedGust
  }

  /**
   * A near-still, slowly-reorienting frame — an ambient window on the sky, not
   * a flythrough. The sky dome is centered at the origin with matrixAutoUpdate
   * off, so translating the camera barely changes what's on screen; only its
   * orientation reads. So we hold a fixed low anchor with a 1-2 unit breathe
   * and layer several independent, mostly-imperceptible-per-frame signals on
   * top of the base yaw/pitch: a long (~42min) orbital creep so a session left
   * running finds a genuinely different composition rather than repeating a
   * short loop; a shared-gust-amplified wind jitter; a golden-hour calm/hold
   * as the sun/moon nears the true horizon; a brief reframe impulse on a real
   * condition change; a fast flinch toward each lightning strike; a whisper of
   * wind-driven roll; and subtle fov breathing. Every term is additive and
   * kept small so the frame still reads as one restrained, ambient shot, not
   * a shaky handheld take. Life otherwise comes from the drifting haze band
   * and the sun/moon's real-time arc, not from moving through near geometry.
   */
  private updateCamera(dt: number, elapsed: number, params: SceneParams): void {
    const t = elapsed

    this.longDriftPhase += dt * 0.0025
    const longDrift = Math.sin(this.longDriftPhase) * 0.4 + Math.cos(this.longDriftPhase * 0.63 + 1.1) * 0.25

    this.ctx.camera.position.set(
      Math.sin(t * 0.06) * 2.2,
      7 + Math.sin(t * 0.08) * 0.9,
      14
    )

    // Golden-hour hold: the frame visibly calms as the sun/moon disc nears
    // the true horizon (roughly the 15-20 minutes around actual sunrise/
    // sunset), the single most photogenic moment Sky.ts renders.
    const goldenWeight = clamp01(1 - Math.abs(params.sunAltitude) / 0.12)

    // Wind-driven unsteadiness on top of the base sway -- two incommensurate
    // sine frequencies read as a soft gust rather than a metronome, silent in
    // dead calm, and amplified during a shared gust (ctx.windGust) so every
    // gust-reactive effect in the scene visibly surges together; damped
    // toward zero as the golden-hour hold takes over.
    const windJitterAmp = clamp01(params.windSpeed) * 0.05 * (1 + this.ctx.windGust.value * 2.2) * (1 - goldenWeight * 0.7)
    const yawJitter = (Math.sin(t * 0.9) * 0.6 + Math.sin(t * 2.3 + 1.7) * 0.4) * windJitterAmp
    const pitchJitter = (Math.sin(t * 1.3 + 0.5) * 0.5 + Math.sin(t * 3.1) * 0.5) * windJitterAmp * 0.4

    // Base yaw points away from the sun's azimuth so the lit horizon sits in
    // frame; a whisper of oscillation keeps it from reading as a frozen still.
    let yaw = params.sunAzimuthRad + Math.PI + Math.sin(t * 0.072) * 0.09 + yawJitter + longDrift
    // Pitch a touch above the horizon (higher when the sun is high) so the
    // softened below-horizon band stays low in the frame; golden-hour eases
    // it toward a slightly lower, more deliberate "hold on the disc" framing.
    const altitude01 = clamp01(params.sunAltitude * 0.5 + 0.5)
    let pitch = lerp(0.08, 0.16, altitude01) + Math.sin(t * 0.1) * 0.025 + pitchJitter
    pitch = lerp(pitch, 0.05, goldenWeight * 0.5)

    // Condition-transition reframe impulse: a brief, deliberate reorientation
    // whenever the weather snapshot actually flips state (never on an
    // unrelated refresh), decaying back to the ambient composition over a
    // few seconds so the frame reads as directed, not just procedural.
    if (
      params.condition !== this.prevCondition ||
      params.thunderActive !== this.prevThunderActive ||
      params.isDay !== this.prevIsDay
    ) {
      this.transitionStartTime = t
      if (params.thunderActive && !this.prevThunderActive) {
        // Storm arriving: bias toward the direction the storm blows in from and tilt up to open more sky for bolts.
        this.transitionYawOffset = clamp(normalizeAngleDelta(params.windDirectionRad - yaw) * 0.15, -0.06, 0.06)
        this.transitionPitchOffset = 0.045
      } else if (params.isDay !== this.prevIsDay) {
        this.transitionYawOffset = 0
        this.transitionPitchOffset = -0.03
      } else {
        this.transitionYawOffset = 0
        this.transitionPitchOffset = 0
      }
      this.prevCondition = params.condition
      this.prevThunderActive = params.thunderActive
      this.prevIsDay = params.isDay
    }
    const transitionEnvelope = Math.exp(-Math.max(0, t - this.transitionStartTime) * 0.35)
    yaw += this.transitionYawOffset * transitionEnvelope
    pitch += this.transitionPitchOffset * transitionEnvelope

    // Lightning flinch: a tiny, fast nudge toward each strike's direction,
    // then an easy settle back -- the frame genuinely reacting to the storm
    // rather than swaying obliviously through a flash right beside it. The
    // target is computed once per strike (using the gaze as of that instant)
    // and simply decays away, decoupled from how yaw/pitch drift afterward.
    if (this.lightning.lastStrikeAt !== this.seenStrikeAt) {
      this.seenStrikeAt = this.lightning.lastStrikeAt
      this.flinchStartTime = t
      const dir = this.lightning.lastStrikeDir
      const strikeAzimuth = Math.atan2(dir.x, dir.z)
      const strikeElevation = Math.asin(clamp(dir.y, -1, 1))
      this.flinchYawTarget = clamp(normalizeAngleDelta(strikeAzimuth - yaw), -0.5, 0.5) * 0.14
      this.flinchPitchTarget = clamp(strikeElevation - pitch, -0.4, 0.4) * 0.12
    }
    const flinchEnvelope = Math.exp(-Math.max(0, t - this.flinchStartTime) * 4.5)
    yaw += this.flinchYawTarget * flinchEnvelope
    pitch += this.flinchPitchTarget * flinchEnvelope

    // Wind-driven roll: a whisper of lean keyed to whether the wind crosses
    // the frame left-to-right or right-to-left -- the wind physically
    // pushing on the frame, a cheap but effective parallax cue.
    const crossWind = Math.sin(params.windDirectionRad - yaw)
    const rollTarget = crossWind * clamp01(params.windSpeed) * 0.028
    this.smoothedRoll += (rollTarget - this.smoothedRoll) * clamp01(dt / 3)

    // Subtle focal-length breathing -- a slow primary breath plus a slightly
    // faster harmonic that only shows up as cloud cover rises, so storm/
    // overcast scenes feel a touch more unsettled than a clear sky. Felt, not
    // consciously noticed: the total swing stays under ~1.1deg either way.
    const fovBreath = Math.sin(t * 0.045) * 0.7 + Math.sin(t * 0.111 + 2.1) * 0.35 * clamp01(params.cloudCover)
    this.ctx.camera.fov = 60 + fovBreath
    this.ctx.camera.updateProjectionMatrix()

    const cosP = Math.cos(pitch)
    const dirX = Math.sin(yaw) * cosP
    const dirY = Math.sin(pitch)
    const dirZ = Math.cos(yaw) * cosP

    const cam = this.ctx.camera.position
    this.ctx.camera.up.set(Math.sin(this.smoothedRoll), Math.cos(this.smoothedRoll), 0)
    this.ctx.camera.lookAt(cam.x + dirX * 100, cam.y + dirY * 100, cam.z + dirZ * 100)
  }

  private computeParams(): SceneParams {
    if (!this.weather) return DEFAULT_PARAMS

    const { current, sunTimes, utcOffsetSeconds } = this.weather
    const conditionInfo = getConditionInfo(current.weatherCode)
    const now = new Date()

    const sun = computeSunPosition(
      now,
      toAbsoluteInstant(sunTimes.sunriseToday, utcOffsetSeconds),
      toAbsoluteInstant(sunTimes.sunsetToday, utcOffsetSeconds),
      toAbsoluteInstant(sunTimes.sunsetYesterday, utcOffsetSeconds),
      toAbsoluteInstant(sunTimes.sunriseTomorrow, utcOffsetSeconds)
    )

    const precipitationIntensity = clamp01(
      Math.max(conditionInfo.precipitationIntensity, clamp01(current.precipitation / 5))
    )
    const cloudCover = clamp01(current.cloudCover / 100)
    const visibility = clamp01(lerp(getBaseVisibility(conditionInfo.condition), 1, 1 - cloudCover) * 0.6 + getBaseVisibility(conditionInfo.condition) * 0.4)
    const moonPhaseFrac = getMoonPhase(now.getTime())
    const moonIllumination = getMoonIlluminatedFraction(moonPhaseFrac)

    return {
      condition: conditionInfo.condition,
      isDay: sun.isDay,
      cloudCover,
      precipitationIntensity,
      windSpeed: clamp01(current.windSpeed / 20),
      windDirectionRad: degToRad(current.windDirection),
      sunAltitude: sun.altitude,
      sunAzimuthRad: sun.azimuthRad,
      timeOfDayFrac: getTimeOfDayFrac(now),
      visibility,
      thunderActive: conditionInfo.condition === 'thunderstorm',
      temperatureC: current.temperature,
      moonIllumination,
      moonPhaseFrac
    }
  }
}
