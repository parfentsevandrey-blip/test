import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const RENDERER_DIR = process.argv[2]
const LABEL = process.argv[3] ?? 'run'
if (!RENDERER_DIR) {
  console.error('Usage: node .perf-trace.mjs <renderer-dir> <label>')
  process.exit(1)
}

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

const nowMs = Date.now()
const fixture = {
  utc_offset_seconds: 0,
  current: {
    time: new Date(nowMs).toISOString().slice(0, 16),
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
    time: Array.from({ length: 30 }, (_, i) => new Date(nowMs + i * 3600000).toISOString().slice(0, 16)),
    temperature_2m: Array.from({ length: 30 }, (_, i) => 16 + Math.sin(i / 4) * 4),
    precipitation_probability: Array.from({ length: 30 }, (_, i) => Math.round(30 + Math.sin(i / 3) * 25)),
    weather_code: Array.from({ length: 30 }, (_, i) => (i % 8 === 0 ? 61 : 2)),
    is_day: Array(30).fill(1),
    uv_index: Array(30).fill(4),
    visibility: Array(30).fill(18000),
    pressure_msl: Array(30).fill(1013)
  },
  daily: {
    time: Array.from({ length: 11 }, (_, i) => new Date(nowMs + i * 86400000).toISOString().slice(0, 10)),
    weather_code: Array.from({ length: 11 }, (_, i) => [0, 2, 61, 3, 95, 71, 45, 2, 61, 0, 2][i % 11]),
    temperature_2m_max: Array.from({ length: 11 }, (_, i) => 22 - i),
    temperature_2m_min: Array.from({ length: 11 }, (_, i) => 12 - i),
    sunrise: Array(11).fill(new Date(nowMs - 4 * 3600000).toISOString().slice(0, 16)),
    sunset: Array(11).fill(new Date(nowMs + 6 * 3600000).toISOString().slice(0, 16)),
    precipitation_probability_max: Array.from({ length: 11 }, (_, i) => 20 + i * 5),
    wind_speed_10m_max: Array(11).fill(16),
    uv_index_max: Array(11).fill(5)
  }
}

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' })
const context = await browser.newContext({
  viewport: { width: 1280, height: 800 },
  permissions: ['geolocation'],
  geolocation: { latitude: 51.5074, longitude: -0.1278 }
})
const page = await context.newPage()

await page.route('**/v1/forecast**', (route) => {
  const url = new URL(route.request().url())
  const currentParam = url.searchParams.get('current') ?? ''
  if (currentParam.split(',').length <= 3) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ current: { temperature_2m: 12, weather_code: 0, is_day: 1 } }) })
  }
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(fixture) })
})
await page.route('**/v1/search**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '{"results":[]}' }))

await page.goto(BASE_URL, { waitUntil: 'load' })
await page.waitForSelector('.bento-card', { timeout: 15000 })
await page.waitForTimeout(4000)

const client = await context.newCDPSession(page)
const events = []
client.on('Tracing.dataCollected', (data) => events.push(...data.value))

async function traceWindow(label, actionFn, durationMs) {
  await client.send('Tracing.start', {
    categories: [
      'disabled-by-default-devtools.timeline',
      'devtools.timeline',
      'blink,devtools.timeline',
      'disabled-by-default-devtools.timeline.frame'
    ].join(','),
    transferMode: 'ReportEvents'
  })
  await Promise.all([actionFn(), new Promise((r) => setTimeout(r, durationMs))])
  const done = new Promise((resolve) => client.once('Tracing.tracingComplete', resolve))
  await client.send('Tracing.end')
  await done

  const byName = {}
  for (const e of events) {
    if (e.ph !== 'X' && e.ph !== 'b' && e.ph !== 'e') continue
    const dur = e.dur ?? 0
    if (!byName[e.name]) byName[e.name] = { count: 0, totalUs: 0 }
    byName[e.name].count++
    byName[e.name].totalUs += dur
  }
  events.length = 0

  const interesting = [
    'UpdateLayoutTree',
    'Layout',
    'Layerize',
    'Paint',
    'CompositeLayers',
    'UpdateLayer',
    'PrePaint',
    'HitTest',
    'RunTask'
  ]
  const summary = {}
  for (const name of interesting) {
    if (byName[name]) summary[name] = { count: byName[name].count, totalMs: +(byName[name].totalUs / 1000).toFixed(2) }
  }
  const styleAndPaintTotalMs = ['UpdateLayoutTree', 'Layout', 'Paint', 'CompositeLayers', 'UpdateLayer', 'PrePaint']
    .reduce((sum, n) => sum + (byName[n]?.totalUs ?? 0), 0) / 1000

  console.log(`\n--- ${label} ---`)
  console.log(JSON.stringify(summary, null, 2))
  console.log(`STYLE+LAYOUT+PAINT+COMPOSITE total: ${styleAndPaintTotalMs.toFixed(2)}ms over ${durationMs}ms window`)
}

// Idle window: nothing but the continuous CSS/FM loops already running.
await traceWindow(`${LABEL} :: IDLE`, async () => {}, 3000)

// Hover-sweep window: simulate a user scanning across several cards.
const cardBoxes = []
const cardCount = await page.locator('.bento-card').count()
for (let i = 0; i < Math.min(cardCount, 6); i++) {
  const box = await page.locator('.bento-card').nth(i).boundingBox()
  if (box) cardBoxes.push(box)
}
await traceWindow(
  `${LABEL} :: HOVER-SWEEP`,
  async () => {
    const steps = 40
    for (let i = 0; i < steps; i++) {
      const box = cardBoxes[i % cardBoxes.length]
      const t = (i % 8) / 8
      const x = box.x + box.width * (0.2 + 0.6 * t)
      const y = box.y + box.height * 0.4
      await page.mouse.move(x, y)
      await page.waitForTimeout(75)
    }
  },
  3200
)

await browser.close()
server.close()
