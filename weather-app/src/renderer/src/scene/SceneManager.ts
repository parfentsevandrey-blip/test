import * as THREE from 'three'
import type { WeatherData } from '../types/weather'
import type { SceneContext, SceneEffect, SceneParams } from './contract'
import { getBaseVisibility, getConditionInfo } from '../utils/weatherCondition'
import { computeSunPosition, getTimeOfDayFrac, toAbsoluteInstant } from '../utils/time'
import { clamp01, degToRad, lerp } from '../utils/math'
import { Sky } from './Sky'
import { Stars } from './Stars'
import { Clouds } from './Clouds'
import { Precipitation } from './Precipitation'
import { Lightning } from './Lightning'
import { Fog } from './Fog'
import { Terrain } from './Terrain'
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
  private cameraAngle = 0

  constructor(canvas: HTMLCanvasElement) {
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 1200)
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))

    const sunLight = new THREE.DirectionalLight(0xffffff, 1)
    const hemiLight = new THREE.HemisphereLight(0x88aadd, 0x223344, 0.4)
    scene.add(sunLight, hemiLight)

    this.ctx = { scene, camera, renderer, sunLight, hemiLight, quality: 'high' }

    this.effects = [
      new Sky(this.ctx),
      new Terrain(this.ctx),
      new Stars(this.ctx),
      new Clouds(this.ctx),
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

  resize(width: number, height: number): void {
    if (width <= 0 || height <= 0) return
    this.ctx.camera.aspect = width / height
    this.ctx.camera.updateProjectionMatrix()
    this.ctx.renderer.setSize(width, height)
    this.postFX.resize(width, height)
  }

  start(): void {
    if (this.rafId !== null) return
    this.clock.start()
    const tick = (): void => {
      this.rafId = requestAnimationFrame(tick)
      const dt = Math.min(this.clock.getDelta(), 0.1)
      const elapsed = this.clock.getElapsedTime()
      const params = this.computeParams()

      this.updateCamera(dt, elapsed, params)
      for (const effect of this.effects) effect.update(dt, elapsed, params)
      this.postFX.update(dt, elapsed, params)
      this.postFX.render(dt)
    }
    tick()
  }

  stop(): void {
    if (this.rafId !== null) cancelAnimationFrame(this.rafId)
    this.rafId = null
  }

  dispose(): void {
    this.stop()
    for (const effect of this.effects) effect.dispose()
    this.postFX.dispose()
    this.ctx.renderer.dispose()
  }

  private updateCamera(_dt: number, elapsed: number, params: SceneParams): void {
    const radius = 26
    const baseHeight = 11
    const bob = Math.sin(elapsed * 0.18) * 0.5
    const cameraHeight = baseHeight + bob

    // Orbit around the sun/moon's current azimuth (with a slow oscillation
    // for cinematic drift) instead of a fully independent sweep -- otherwise
    // the sun frequently ends up entirely out of frame, leaving the sky
    // looking flat and empty regardless of how good the lighting is.
    const oscillation = Math.sin(elapsed * (0.05 + params.windSpeed * 0.015)) * 0.5
    this.cameraAngle = params.sunAzimuthRad + Math.PI + oscillation

    this.ctx.camera.position.set(
      Math.sin(this.cameraAngle) * radius,
      cameraHeight,
      Math.cos(this.cameraAngle) * radius
    )

    // Tilt the look-at target upward, very mildly, as the sun climbs higher
    // -- just enough bias to catch it more often across the day without
    // losing the horizon/terrain, which stays in frame at all times.
    const altitude01 = clamp01(params.sunAltitude * 0.5 + 0.5)
    const lookAtY = lerp(cameraHeight * 0.55, cameraHeight * 1.15, altitude01)
    this.ctx.camera.lookAt(0, lookAtY, 0)
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
