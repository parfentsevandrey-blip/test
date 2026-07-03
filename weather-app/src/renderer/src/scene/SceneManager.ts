import * as THREE from 'three'
import type { WeatherData } from '../types/weather'
import type { SceneContext, SceneEffect, SceneParams } from './contract'
import { getBaseVisibility, getConditionInfo } from '../utils/weatherCondition'
import { computeSunPosition, getTimeOfDayFrac, toAbsoluteInstant } from '../utils/time'
import { clamp01, degToRad, lerp } from '../utils/math'
import { Sky } from './Sky'
import { Stars } from './Stars'
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
  temperatureC: 18
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
  private readonly postFX: PostFX
  private readonly clock = new THREE.Clock()
  private weather: WeatherData | null = null
  private rafId: number | null = null
  private running = false
  /** Own elapsed accumulator (dt-summed) so pausing never resets the clock. */
  private elapsedTime = 0
  private lastRenderTs = 0

  constructor(canvas: HTMLCanvasElement) {
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 1200)
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

    const sunLight = new THREE.DirectionalLight(0xffffff, 1)
    const hemiLight = new THREE.HemisphereLight(0x88aadd, 0x223344, 0.4)
    scene.add(sunLight, hemiLight)

    this.ctx = { scene, camera, renderer, sunLight, hemiLight, quality: 'high' }

    // Serene-sky composition: just the atmospheric gradient dome + sun/moon,
    // subtle night stars, weather-gated fog haze, real precipitation and
    // storm lightning. The old flythrough extras (terrain silhouette,
    // volumetric cloud blobs, birds, shooting stars, rainbows, sun rays) are
    // retired for a calm, premium desktop backdrop. Their source files stay on
    // disk, dormant — nothing constructs them, so they cost nothing.
    this.effects = [
      new Sky(this.ctx),
      new Stars(this.ctx),
      new Fog(this.ctx),
      new Precipitation(this.ctx),
      new Lightning(this.ctx)
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
   * A near-still, slowly-reorienting frame — an ambient window on the sky, not
   * a flythrough. The sky dome is centered at the origin with matrixAutoUpdate
   * off, so translating the camera barely changes what's on screen; only its
   * orientation reads. So we hold a fixed low anchor with a 1-2 unit breathe
   * and let the frame drift almost imperceptibly: yaw locks toward the sun's
   * azimuth (keeping the horizon glow and disc in view) with a ~0.05 rad
   * ultra-slow sway, and the gaze sits just above the horizon with a tiny bob.
   * Life comes from the drifting haze band and the sun/moon's real-time arc,
   * not from moving the camera around a scene that no longer has near geometry.
   */
  private updateCamera(_dt: number, elapsed: number, params: SceneParams): void {
    const t = elapsed

    this.ctx.camera.position.set(
      Math.sin(t * 0.015) * 1.2,
      7 + Math.sin(t * 0.02) * 0.5,
      14
    )

    // Base yaw points away from the sun's azimuth so the lit horizon sits in
    // frame; a whisper of oscillation keeps it from reading as a frozen still.
    const yaw = params.sunAzimuthRad + Math.PI + Math.sin(t * 0.018) * 0.05
    // Pitch a touch above the horizon (higher when the sun is high) so the
    // softened below-horizon band stays low in the frame.
    const altitude01 = clamp01(params.sunAltitude * 0.5 + 0.5)
    const pitch = lerp(0.08, 0.16, altitude01) + Math.sin(t * 0.025) * 0.015

    const cosP = Math.cos(pitch)
    const dirX = Math.sin(yaw) * cosP
    const dirY = Math.sin(pitch)
    const dirZ = Math.cos(yaw) * cosP

    const cam = this.ctx.camera.position
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
      temperatureC: current.temperature
    }
  }
}
