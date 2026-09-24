'use strict';
/* Внутри МКАД или нет.

   МКАД — кольцо вокруг центра, и любой луч из Кремля пересекает его ровно
   один раз. Поэтому контур хранится не многоугольником, а радиусом кольца на
   каждый угол (720 делений по полградуса): точка внутри, если она ближе к
   центру, чем кольцо на её угле. Радиус — медиана точек обеих проезжих
   частей в делении; разброс между ними (ширина дороги с развязками) хранится
   рядом, и лот в пределах этой полосы называется сомнительным, а не
   молча относится к одной из сторон.

   Геометрия — OpenStreetMap, отношение 2094222 «Московская кольцевая
   автомобильная дорога» (ODbL). */
const fs = require('fs');
const path = require('path');

const CENTER = { lat: 55.7520, lng: 37.6175 };      // Кремль
const BINS = 720;
const M_LAT = 110540;                                  // м в градусе широты
const M_LNG = 111320 * Math.cos((CENTER.lat * Math.PI) / 180);
const FILE = path.join(__dirname, '..', 'geo', 'mkad.json');

function polar(lat, lng) {
  const x = (lng - CENTER.lng) * M_LNG;
  const y = (lat - CENTER.lat) * M_LAT;
  let a = Math.atan2(y, x);
  if (a < 0) a += 2 * Math.PI;
  return { a, r: Math.hypot(x, y) };
}

const median = (xs) => {
  const s = [...xs].sort((p, q) => p - q);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

/* Радиус кольца по углам из точек дороги. Пустые деления (участок без
   точек) заполняются интерполяцией между соседними заполненными. */
function buildRing(points, bins = BINS) {
  const buckets = Array.from({ length: bins }, () => []);
  for (const p of points) {
    const { a, r } = polar(p.lat, p.lng);
    buckets[Math.floor((a / (2 * Math.PI)) * bins) % bins].push(r);
  }
  const r = buckets.map((b) => (b.length ? Math.round(median(b)) : null));
  const spread = buckets.map((b) => (b.length ? Math.round(Math.max(...b) - Math.min(...b)) : null));
  const filled = r.filter((x) => x != null).length;
  if (filled < bins / 4) throw new Error(`слишком мало точек: заполнено ${filled} делений из ${bins}`);
  const empty = [];
  for (let i = 0; i < bins; i++) {
    if (r[i] != null) continue;
    empty.push(i);
    let lo = 1, hi = 1;
    while (r[(i - lo + bins) % bins] == null) lo++;
    while (r[(i + hi) % bins] == null) hi++;
    const a = r[(i - lo + bins) % bins], b = r[(i + hi) % bins];
    r[i] = Math.round(a + ((b - a) * lo) / (lo + hi));
  }
  return { center: CENTER, bins, r, spread, interpolated: empty.length };
}

/* Радиус кольца на произвольном угле — линейно между делениями. */
function ringRadius(ring, a) {
  const f = (a / (2 * Math.PI)) * ring.bins - 0.5;
  const i = Math.floor(f);
  const t = f - i;
  const r0 = ring.r[(i + ring.bins) % ring.bins];
  const r1 = ring.r[(i + 1) % ring.bins];
  return r0 + (r1 - r0) * t;
}

let cached = null;
function loadRing(file = FILE) {
  if (!cached || cached.file !== file) cached = { file, ring: JSON.parse(fs.readFileSync(file, 'utf8')) };
  return cached.ring;
}

/* { inside, marginM, sure }: marginM > 0 — внутри на столько метров от оси
   кольца. Полоса сомнения — половина ширины дороги в этом месте плюс 150 м
   на то, что координата у Циан — центр дома, а не подъезд. Без координат —
   null: «неизвестно» не значит «снаружи». */
function mkadVerdict(lat, lng, ring = loadRing()) {
  if (lat == null || lng == null || !Number.isFinite(+lat) || !Number.isFinite(+lng)) return null;
  const { a, r } = polar(+lat, +lng);
  const R = ringRadius(ring, a);
  const i = Math.floor((a / (2 * Math.PI)) * ring.bins) % ring.bins;
  const band = ((ring.spread && ring.spread[i]) || 100) / 2 + 150;
  const marginM = Math.round(R - r);
  return { inside: marginM > 0, marginM, sure: Math.abs(marginM) > band };
}

module.exports = { buildRing, ringRadius, mkadVerdict, loadRing, polar, CENTER, BINS, FILE };
