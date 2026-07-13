import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const RENDERER_DIR = '/home/user/test/weather-app/out/renderer'
const SCRATCH = '/tmp/claude-0/-home-user-test/832da10e-ad7d-5dc7-a2d3-449552362c62/scratchpad'
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

function buildFixture() {
  const nowMs = Date.now()
  const hours = []
  for (let i = -24; i <= 48; i++) hours.push(nowMs + i * HOUR_MS)
  const sunsetTodayMs = nowMs + 6 * HOUR_MS
  const sunriseTodayMs = nowMs - 4 * HOUR_MS
  const dailyDates = []
  for (let i = -1; i <= 10; i++) dailyDates.push(new Date(nowMs + i * 86_400_000).toISOString().slice(0, 10))

  return {
    utc_offset_seconds: 0,
    current: {
      time: isoLocal(nowMs),
      temperature_2m: 18,
      relative_humidity_2m: 55,
      apparent_temperature: 17,
      is_day: 1,
      precipitation: 0.4,
      weather_code: 61,
      cloud_cover: 55,
      pressure_msl: 1013,
      wind_speed_10m: 14,
      wind_direction_10m: 210,
      wind_gusts_10m: 22
    },
    hourly: {
      time: hours.map(isoLocal),
      temperature_2m: hours.map((_, i) => 16 + Math.sin(i / 4) * 4),
      precipitation_probability: hours.map((_, i) => Math.round(30 + Math.sin(i / 3) * 25)),
      weather_code: hours.map((_, i) => (i % 8 === 0 ? 61 : 2)),
      is_day: hours.map(() => 1),
      uv_index: hours.map(() => 4),
      visibility: hours.map(() => 18000),
      pressure_msl: hours.map(() => 1013)
    },
    daily: {
      time: dailyDates,
      weather_code: dailyDates.map((_, i) => [0, 2, 61, 3, 95, 71, 45, 2, 61, 0, 2, 3][i % 12]),
      temperature_2m_max: dailyDates.map((_, i) => 22 - i),
      temperature_2m_min: dailyDates.map((_, i) => 12 - i),
      sunrise: dailyDates.map(() => isoLocal(sunriseTodayMs)),
      sunset: dailyDates.map(() => isoLocal(sunsetTodayMs)),
      precipitation_probability_max: dailyDates.map((_, i) => 20 + i * 5),
      wind_speed_10m_max: dailyDates.map(() => 16),
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

async function themeRun(theme) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    permissions: ['geolocation'],
    geolocation: { latitude: 51.5074, longitude: -0.1278 }
  })
  const page = await context.newPage()
  const consoleErrors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })
  page.on('pageerror', (err) => consoleErrors.push(String(err)))

  const fixture = buildFixture()
  await page.route('**/v1/forecast**', (route) => {
    const url = new URL(route.request().url())
    const currentParam = url.searchParams.get('current') ?? ''
    if (currentParam.split(',').length <= 3) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ current: { temperature_2m: 12, weather_code: 0, is_day: 1 } })
      })
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(fixture) })
  })
  await page.route('**/v1/search**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        results: [
          { name: 'Paris', country: 'France', admin1: 'Ile-de-France', latitude: 48.85, longitude: 2.35 },
          { name: 'Paris', country: 'United States', admin1: 'Texas', latitude: 33.66, longitude: -95.55 }
        ]
      })
    })
  )

  await page.addInitScript((t) => window.localStorage.setItem('cinematic-weather:theme', t), theme)

  await page.goto(BASE_URL, { waitUntil: 'load' })
  await page.waitForSelector('.bento-card', { timeout: 15000 })
  // win95 plays a ~2.3s boot splash overlay that blocks interaction on first entry.
  await page.waitForTimeout(theme === 'win95' ? 2800 : 2200)

  check(`${theme}: bento cards rendered`, (await page.locator('.bento-card').count()) >= 8)

  await page.screenshot({ path: `${SCRATCH}/mx-${theme}-idle.png`, fullPage: false })

  // Hover a card (tilt/glow should not throw)
  const heroCard = page.locator('.bento-card').first()
  await heroCard.hover({ position: { x: 30, y: 20 }, force: true })
  await page.waitForTimeout(300)
  await page.screenshot({ path: `${SCRATCH}/mx-${theme}-hover.png` })

  // Open search dropdown
  await page.locator('.search-input').fill('Paris')
  await page.waitForSelector('.search-result-item', { timeout: 4000 }).catch(() => {})
  check(`${theme}: search results open`, (await page.locator('.search-result-item').count()) > 0)
  await page.screenshot({ path: `${SCRATCH}/mx-${theme}-search.png` })
  await page.locator('.search-input').fill('')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // Open favorites dropdown
  const favTrigger = page.locator('[aria-label="Saved locations"]').first()
  if (await favTrigger.count()) {
    await favTrigger.click({ force: true })
    await page.waitForTimeout(400)
    await page.screenshot({ path: `${SCRATCH}/mx-${theme}-favorites.png` })
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  }

  // Open a daily-forecast row popover (layoutId morph)
  const dailyRow = page.locator('.df-row').first()
  if (await dailyRow.count()) {
    await dailyRow.click({ force: true })
    await page.waitForTimeout(500)
    await page.screenshot({ path: `${SCRATCH}/mx-${theme}-daily-popover.png` })
    await page.keyboard.press('Escape')
    // The popover's spring exit + its full-viewport overlay both take a bit
    // longer than a fixed short wait to fully clear -- wait for the overlay
    // itself to detach rather than guessing a duration.
    await page
      .waitForSelector('.df-detail-overlay', { state: 'detached', timeout: 2000 })
      .catch(() => {})
  }

  // Open settings panel
  const gearBtn = page.locator('[aria-label="Settings"]').first()
  if (await gearBtn.count()) {
    await gearBtn.click({ force: true })
    await page.waitForTimeout(500)
    await page.screenshot({ path: `${SCRATCH}/mx-${theme}-settings.png` })
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  }

  // win95/skeuo-specific: confirm backdrop-filter is actually neutralized on glass panels
  if (theme === 'win95' || theme === 'skeuo') {
    const blurValue = await page.evaluate(() => {
      const el = document.querySelector('.glass-panel')
      return el ? getComputedStyle(el).backdropFilter : null
    })
    check(`${theme}: backdrop-filter neutralized`, blurValue === 'none' || blurValue === '' || blurValue == null, String(blurValue))
  } else {
    const blurValue = await page.evaluate(() => {
      const el = document.querySelector('.glass-panel')
      return el ? getComputedStyle(el).backdropFilter : null
    })
    check(`${theme}: backdrop-filter is real blur`, !!blurValue && blurValue !== 'none', String(blurValue))
  }

  check(`${theme}: no console errors`, consoleErrors.length === 0, consoleErrors.slice(0, 6).join(' | '))
  await context.close()
}

for (const theme of ['light', 'dark', 'tuscany', 'win95', 'skeuo']) {
  await themeRun(theme)
}

await browser.close()
server.close()

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
if (failed.length > 0) {
  console.log('FAILURES:', failed.map((f) => f.name).join(', '))
  process.exit(1)
}
