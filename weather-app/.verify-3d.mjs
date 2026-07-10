import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { readFile, unlink, mkdir } from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const RENDERER_DIR = path.join(SCRIPT_DIR, 'out/renderer')
const SCREENSHOT_DIR = path.join(os.tmpdir(), 'cinematic-weather-verify-3d')
await mkdir(SCREENSHOT_DIR, { recursive: true })
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.woff2': 'font/woff2' }
const server = createServer(async (req, res) => {
  let filePath = path.join(RENDERER_DIR, req.url === '/' ? 'index.html' : req.url.split('?')[0])
  try {
    const data = await readFile(filePath)
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filePath)] ?? 'application/octet-stream' })
    res.end(data)
  } catch {
    res.writeHead(404)
    res.end('nf')
  }
})
await new Promise((r) => server.listen(0, r))
const port = server.address().port
const BASE_URL = `http://localhost:${port}`

const HOUR_MS = 3_600_000
function isoLocal(ms) {
  return new Date(ms).toISOString().slice(0, 16)
}

function buildFixture({ weatherCode = 0, cloudCover = 10, windSpeed = 3, precipitation = 0, night = false }) {
  const nowMs = Date.now()
  const hours = []
  for (let i = -24; i <= 48; i++) hours.push(nowMs + i * HOUR_MS)
  const realNow = Date.now()
  const sunsetTodayMs = night ? realNow - 2 * HOUR_MS : realNow + 6 * HOUR_MS
  const sunriseTodayMs = night ? realNow + 8 * HOUR_MS : realNow - 4 * HOUR_MS
  const sunsetYesterdayMs = sunsetTodayMs - 24 * HOUR_MS
  const sunriseTomorrowMs = sunriseTodayMs + 24 * HOUR_MS
  const dailyDates = []
  for (let i = -1; i <= 10; i++) dailyDates.push(new Date(nowMs + i * 86_400_000).toISOString().slice(0, 10))

  return {
    utc_offset_seconds: 0,
    current: {
      time: isoLocal(nowMs),
      temperature_2m: 18,
      relative_humidity_2m: 55,
      apparent_temperature: 17,
      is_day: night ? 0 : 1,
      precipitation,
      weather_code: weatherCode,
      cloud_cover: cloudCover,
      pressure_msl: 1013,
      wind_speed_10m: windSpeed,
      wind_direction_10m: 210,
      wind_gusts_10m: windSpeed * 1.5
    },
    hourly: {
      time: hours.map(isoLocal),
      temperature_2m: hours.map(() => 18),
      precipitation_probability: hours.map(() => 10),
      weather_code: hours.map(() => weatherCode),
      is_day: hours.map(() => 1),
      uv_index: hours.map(() => 3),
      visibility: hours.map(() => 20000),
      pressure_msl: hours.map(() => 1013)
    },
    daily: {
      time: dailyDates,
      weather_code: dailyDates.map(() => weatherCode),
      temperature_2m_max: dailyDates.map(() => 20),
      temperature_2m_min: dailyDates.map(() => 10),
      sunrise: [
        isoLocal(sunriseTodayMs - 24 * HOUR_MS),
        isoLocal(sunriseTodayMs),
        isoLocal(sunriseTomorrowMs),
        ...dailyDates.slice(3).map((_, i) => isoLocal(sunriseTomorrowMs + (i + 1) * 24 * HOUR_MS))
      ],
      sunset: [
        isoLocal(sunsetYesterdayMs),
        isoLocal(sunsetTodayMs),
        isoLocal(sunsetTodayMs + 24 * HOUR_MS),
        ...dailyDates.slice(3).map((_, i) => isoLocal(sunsetTodayMs + (i + 2) * 24 * HOUR_MS))
      ],
      precipitation_probability_max: dailyDates.map(() => 15),
      wind_speed_10m_max: dailyDates.map(() => windSpeed + 2),
      uv_index_max: dailyDates.map(() => 5)
    }
  }
}

const results = []
function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'} - ${name}${detail ? ' :: ' + detail : ''}`)
}

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' })

async function scenario(label, fixtureOpts, opts = {}) {
  const context = await browser.newContext({
    viewport: { width: 1040, height: 680 },
    permissions: ['geolocation'],
    geolocation: { latitude: 51.5074, longitude: -0.1278 }
  })
  const page = await context.newPage()
  const consoleErrors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })
  page.on('pageerror', (err) => consoleErrors.push(String(err)))

  const fixture = buildFixture(fixtureOpts)
  await page.route('**/v1/forecast**', (route) => {
    const url = new URL(route.request().url())
    const currentParam = url.searchParams.get('current') ?? ''
    if (currentParam.split(',').length <= 3) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ current: { temperature_2m: 12, weather_code: 0, is_day: 1 } }) })
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(fixture) })
  })
  await page.route('**/v1/search**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '{"results":[]}' }))

  if (opts.theme) {
    await page.addInitScript((t) => window.localStorage.setItem('cinematic-weather:theme', t), opts.theme)
  }
  if (opts.quality) {
    await page.addInitScript((q) => window.localStorage.setItem('cinematic-weather:quality', q), opts.quality)
  }

  await page.goto(BASE_URL, { waitUntil: 'load' })
  await page.waitForSelector('.bento-card', { timeout: 15000 })
  await page.waitForTimeout(opts.waitMs ?? 3000)

  const canvasBox = await page.locator('canvas').first().boundingBox()
  check(`${label}: canvas present`, !!canvasBox)

  // Screenshot the actual COMPOSITED frame (not a live gl.readPixels() call --
  // that reads the default framebuffer async from outside the render loop,
  // which reliably comes back all-zero on a preserveDrawingBuffer:false
  // context regardless of whether rendering is actually fine). A PNG this
  // size can't be a blank/solid-color canvas -- a truly broken render (camera
  // NaN, degenerate lookAt) collapses to a near-flat image that compresses
  // far smaller than this app's normal gradient-sky-plus-UI screenshot.
  const screenshotPath = path.join(SCREENSHOT_DIR, `3d-${label}.png`)
  const buf = await page.screenshot({ path: screenshotPath })
  check(`${label}: screenshot is not a blank/broken render`, buf.length > 15000, `${buf.length} bytes`)
  if (!opts.screenshot) await unlink(screenshotPath).catch(() => {})

  if (opts.extraChecks) {
    await opts.extraChecks(page, check)
  }

  check(`${label}: no console errors`, consoleErrors.length === 0, consoleErrors.slice(0, 5).join(' | '))
  await context.close()
}

const SCENARIOS = (globalThis.__VERIFY_SCENARIOS__ ?? [
  ['day-clear', { weatherCode: 0, windSpeed: 2 }, { screenshot: true }],
  ['day-storm-wind', { weatherCode: 95, windSpeed: 18, cloudCover: 90, precipitation: 5 }, { screenshot: true }],
  ['night-clear', { weatherCode: 0, night: true, windSpeed: 1 }, { screenshot: true }],
  ['night-storm', { weatherCode: 95, night: true, windSpeed: 15, cloudCover: 90, precipitation: 5 }, { screenshot: true }],
  ['fog', { weatherCode: 45, windSpeed: 1, cloudCover: 80 }, { screenshot: true }],
  ['snow', { weatherCode: 73, windSpeed: 8, cloudCover: 70, precipitation: 2 }, { screenshot: true }],
  ['win95-storm', { weatherCode: 95, windSpeed: 18, cloudCover: 90, precipitation: 5 }, { theme: 'win95', screenshot: true }]
])

for (const [label, fixtureOpts, opts] of SCENARIOS) {
  await scenario(label, fixtureOpts, opts)
}

await browser.close()
server.close()

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
if (failed.length > 0) {
  console.log('FAILURES:', failed.map((f) => f.name).join(', '))
  process.exit(1)
}
