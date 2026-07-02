import * as THREE from 'three'
import type { WeatherData } from '../types/weather'
import type { SceneContext, SceneEffect, SceneParams } from './contract'
import { getBaseVisibility, getConditionInfo } from '../utils/weatherCondition'
import { computeSunPosition, getTimeOfDayFrac } from '../utils/time'
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
    const camera = new THREE.PerspectiveCamera(52, 1, 0.1, 1200)
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
    const driftSpeed = 0.012 + params.windSpeed * 0.01
    this.cameraAngle = elapsed * driftSpeed
    const radius = 26
    const baseHeight = 9.5
    const bob = Math.sin(elapsed * 0.18) * 0.6

    this.ctx.camera.position.set(
      Math.cos(this.cameraAngle) * radius,
      baseHeight + bob,
      Math.sin(this.cameraAngle) * radius
    )
    this.ctx.camera.lookAt(0, baseHeight * 0.35, 0)
  }

  private computeParams(): SceneParams {
    if (!this.weather) return DEFAULT_PARAMS

    const { current, sunTimes } = this.weather
    const conditionInfo = getConditionInfo(current.weatherCode)
    const now = new Date()

    const sun = computeSunPosition(
      now,
      new Date(sunTimes.sunriseToday),
      new Date(sunTimes.sunsetToday),
      new Date(sunTimes.sunsetYesterday),
      new Date(sunTimes.sunriseTomorrow)
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
