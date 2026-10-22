// Screenshots of Yandex Maps for every listing plus an overview map.
// Uses the public map widget (yandex.ru/map-widget), which renders the
// same tiles as yandex.ru/maps without its bot wall. Maps are shot without
// the widget's own pins; the report draws numbered markers on top, at
// positions computed here with Yandex's projection (EPSG:3395).
//   node maps.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const HERE = __dirname;
const OUT = path.join(HERE, 'build', 'maps');
const listings = JSON.parse(fs.readFileSync(path.join(HERE, 'data', 'listings.json'), 'utf8'));
// Garden Ring geometry (OSM); the overview is framed to show the whole ring.
const ring = JSON.parse(fs.readFileSync(path.join(HERE, 'data', 'ring.json'), 'utf8'));

// Elliptical Mercator in "world pixels" at zoom z (256px tiles).
const E = 0.0818191908426;
function project(lat, lng, z) {
  const size = 256 * 2 ** z;
  const phi = lat * Math.PI / 180;
  const esin = E * Math.sin(phi);
  const y = Math.log(Math.tan(Math.PI / 4 + phi / 2) * ((1 - esin) / (1 + esin)) ** (E / 2));
  return { x: (lng + 180) / 360 * size, y: (0.5 - y / (2 * Math.PI)) * size };
}

async function shoot(page, url, file, clip) {
  await page.goto(url, { waitUntil: 'networkidle', timeout: 90000 });
  for (const label of ['Allow essential cookies', 'Разрешить обязательные', 'Allow all', 'Принять']) {
    const btn = page.getByText(label, { exact: false });
    if (await btn.count()) { await btn.first().click().catch(() => {}); break; }
  }
  await page.waitForTimeout(3500);
  await page.screenshot({ path: file, clip });
}

// The clip is centred on the viewport (= map centre) and keeps clear of the
// widget's buttons on the edges and the "open in Yandex Maps" chip.
function centredClip(view, w, h) {
  return { x: (view.width - w) / 2, y: (view.height - h) / 2, width: w, height: h };
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const meta = { listings: {}, overview: null };

  const view = { width: 1400, height: 1000 };
  const page = await browser.newPage({ viewport: view, deviceScaleFactor: 2, locale: 'ru-RU' });
  const clip = centredClip(view, 1200, 720);
  for (const it of listings) {
    const { lat, lng } = it.coords;
    const z = 17;
    await shoot(page, `https://yandex.ru/map-widget/v1/?ll=${lng},${lat}&z=${z}&l=map&lang=ru_RU`,
      path.join(OUT, `${it.id}.png`), clip);
    meta.listings[it.id] = { z, w: clip.width, h: clip.height };
    console.log('map', it.id);
  }
  await page.close();

  // Overview: frame the bounding box of all listings at a fixed zoom.
  // Full-page overview (A4 landscape aspect); the ring sits in the left part,
  // the right part is left for the list card.
  const oview = { width: 1800, height: 1300 };
  const opage = await browser.newPage({ viewport: oview, deviceScaleFactor: 2, locale: 'ru-RU' });
  const oclip = centredClip(oview, 1700, 1202);
  const SHIFT = 300;
  const z = 14;
  const pts = listings.map(i => ({ id: i.id, order: i.order, ...project(i.coords.lat, i.coords.lng, z) }));
  const frame = pts.concat(ring.ways.flat().map(([lat, lng]) => project(lat, lng, z)));
  const cx = (Math.min(...frame.map(p => p.x)) + Math.max(...frame.map(p => p.x))) / 2 + SHIFT;
  const cy = (Math.min(...frame.map(p => p.y)) + Math.max(...frame.map(p => p.y))) / 2;
  // Back-project the pixel centre to lat/lng for the widget's ll parameter.
  const size = 256 * 2 ** z;
  const lng0 = cx / size * 360 - 180;
  const lat0 = (() => { // invert project() numerically
    let lo = 50, hi = 60;
    for (let k = 0; k < 60; k++) { const mid = (lo + hi) / 2; if (project(mid, lng0, z).y > cy) lo = mid; else hi = mid; }
    return (lo + hi) / 2;
  })();
  await shoot(opage, `https://yandex.ru/map-widget/v1/?ll=${lng0},${lat0}&z=${z}&l=map&lang=ru_RU`,
    path.join(OUT, 'overview.png'), oclip);
  meta.overview = {
    z, w: oclip.width, h: oclip.height, center: { lat: lat0, lng: lng0 },
    markers: pts.map(p => ({ id: p.id, order: p.order,
      left: +(50 + (p.x - cx) / oclip.width * 100).toFixed(3),
      top: +(50 + (p.y - cy) / oclip.height * 100).toFixed(3) })),
  };
  console.log('map overview');

  fs.writeFileSync(path.join(OUT, 'maps.json'), JSON.stringify(meta, null, 1));
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
