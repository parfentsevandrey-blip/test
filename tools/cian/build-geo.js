#!/usr/bin/env node
/**
 * Сборка геометрии для оси «адрес и вид» из выгрузок OSM (Overpass).
 *
 *   node tools/cian/build-geo.js --dir scratchpad --out tools/cian/geo/cao.json
 *
 * На вход кладутся три файла Overpass (out geom / out tags center):
 *   river.json      way["waterway"="river"]["name"~"Москва"]
 *   parks-geom.json way|rel["leisure"~"park|garden|nature_reserve|common"]
 *   buildings.json  way["building"]  (out tags center)
 *   streets.json    way["highway"]["name"]
 *
 * Зачем отдельный шаг, а не чтение OSM на лету: ломаная реки нужна каждому
 * лоту, а выгрузка весит мегабайты и живёт за чужим сервисом с лимитом на
 * запросы. Здесь она один раз упрощается и ложится в репозиторий, где её
 * можно прочитать глазами и переспросить через полгода.
 */
const fs = require('fs');

const args = (argv) => {
  const a = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) {
      const k = argv[i].slice(2);
      a[k] = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : true;
    } else a._.push(argv[i]);
  }
  return a;
};

const M_PER_DEG_LAT = 111320;
const M_PER_DEG_LNG = 111320 * Math.cos(55.75 * Math.PI / 180);

/* Упрощение Дугласа-Пекера. Порог в метрах: на 15 м ломаная реки теряет
   рябь берега и сохраняет излучину, а это ровно то, что нужно для ответа
   «далеко ли вода». */
function simplify(pts, tolM) {
  if (pts.length < 3) return pts;
  const x = (p) => p[1] * M_PER_DEG_LNG, y = (p) => p[0] * M_PER_DEG_LAT;
  const keep = new Array(pts.length).fill(false);
  keep[0] = keep[pts.length - 1] = true;
  const stack = [[0, pts.length - 1]];
  while (stack.length) {
    const [i, j] = stack.pop();
    let worst = -1, wi = -1;
    const ax = x(pts[i]), ay = y(pts[i]), bx = x(pts[j]), by = y(pts[j]);
    const dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy);
    for (let k = i + 1; k < j; k++) {
      const px = x(pts[k]), py = y(pts[k]);
      const d = len ? Math.abs(dy * px - dx * py + bx * ay - by * ax) / len
        : Math.hypot(px - ax, py - ay);
      if (d > worst) { worst = d; wi = k; }
    }
    if (worst > tolM && wi > 0) { keep[wi] = true; stack.push([i, wi], [wi, j]); }
  }
  return pts.filter((_, i) => keep[i]);
}

/* Площадь замкнутого контура в гектарах — формула шнурования в метрах. */
function ringHa(ring) {
  let s = 0;
  for (let i = 0; i < ring.length; i++) {
    const [ay, ax] = ring[i], [by, bx] = ring[(i + 1) % ring.length];
    s += (ax * M_PER_DEG_LNG) * (by * M_PER_DEG_LAT) - (bx * M_PER_DEG_LNG) * (ay * M_PER_DEG_LAT);
  }
  return Math.abs(s) / 2 / 10000;
}

const readJson = (f) => (fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : null);
const geomToPts = (g) => (g || []).map((p) => [+p.lat.toFixed(5), +p.lon.toFixed(5)]);

/* Внешние члены мультиполигона — это КУСКИ границы, а не готовые кольца.
   Первая сборка приняла каждый кусок за контур, и Парк Горького вышел
   площадью 5.6 га вместо девяноста, а Нескучный сад — пятью. Куски надо
   сшивать по совпадающим концам. */
function stitchRings(parts) {
  const same = (a, b) => a[0] === b[0] && a[1] === b[1];
  const left = parts.filter((p) => p && p.length > 1).map((p) => p.slice());
  const rings = [];
  while (left.length) {
    let cur = left.shift();
    let grew = true;
    while (grew && !same(cur[0], cur[cur.length - 1])) {
      grew = false;
      for (let i = 0; i < left.length; i++) {
        const p = left[i];
        if (same(cur[cur.length - 1], p[0])) { cur = cur.concat(p.slice(1)); left.splice(i, 1); grew = true; break; }
        if (same(cur[cur.length - 1], p[p.length - 1])) { cur = cur.concat(p.slice().reverse().slice(1)); left.splice(i, 1); grew = true; break; }
        if (same(cur[0], p[p.length - 1])) { cur = p.slice(0, -1).concat(cur); left.splice(i, 1); grew = true; break; }
        if (same(cur[0], p[0])) { cur = p.slice().reverse().slice(0, -1).concat(cur); left.splice(i, 1); grew = true; break; }
      }
    }
    /* Незамкнутый остаток не выбрасываем: он всё равно описывает границу
       парка, а расстояние до ломаной считается и без замыкания. Но площадь
       по нему честно не посчитать, поэтому такие куски помечены. */
    rings.push({ pts: cur, closed: same(cur[0], cur[cur.length - 1]) });
  }
  return rings;
}

function main() {
  const a = args(process.argv.slice(2));
  const dir = a.dir || 'scratchpad';
  const out = a.out || 'tools/cian/geo/cao.json';
  const tol = parseFloat(a.tol || '15');
  const report = [];

  /* ---- река ---- */
  const riverSrc = readJson(`${dir}/river.json`);
  let river = [];
  if (riverSrc) {
    river = riverSrc.elements.filter((e) => e.geometry && e.geometry.length > 1)
      .map((e) => simplify(geomToPts(e.geometry), tol))
      .filter((p) => p.length > 1);
    report.push(`река: ${river.length} участков, ${river.reduce((s, p) => s + p.length, 0)} точек`);
  } else report.push('река: файла нет');

  /* ---- зелень ---- */
  const parkSrc = readJson(`${dir}/parks-geom.json`);
  const green = [];
  if (parkSrc) {
    for (const e of parkSrc.elements) {
      const name = e.tags && e.tags.name;
      if (!name) continue;
      const rings = e.type === 'relation'
        ? stitchRings((e.members || []).filter((m) => m.role === 'outer' && m.geometry).map((m) => geomToPts(m.geometry)))
        : [{ pts: geomToPts(e.geometry), closed: true }];
      for (const r of rings) {
        if (r.pts.length < 4) continue;
        const ha = r.closed ? ringHa(r.pts) : null;
        if (ha != null && ha < 1) continue;
        green.push({ name, ha: ha == null ? null : +ha.toFixed(1), ring: simplify(r.pts, tol * 2) });
      }
    }
    green.sort((x, y) => (y.ha || 0) - (x.ha || 0));
    const open = green.filter((g) => (g.ha || 0) >= 2);
    report.push(`зелень: ${green.length} контуров, из них крупнее 2 га — ${open.length}`);
    open.slice(0, 12).forEach((g) => report.push(`    ${String(g.ha).padStart(6)} га  ${g.name}`));
  } else report.push('зелень: файла нет');

  /* ---- этажность ---- */
  /* Гаражи, навесы, будки и торговые павильоны из линии крыш выброшены.
     Не из брезгливости: в центре их столько, что медиана этажности вокруг
     Остоженки выходила 3 — то есть по этой линии квартира на пятом этаже
     считалась бы видовой. С отсевом и по верхней четверти выходит 5-6, что
     и есть реальная высота квартала. */
  const SKIP_BUILDING = new Set(['service', 'garage', 'garages', 'shed', 'roof', 'kiosk', 'hut',
    'carport', 'container', 'greenhouse', 'warehouse', 'industrial', 'retail', 'guardhouse',
    'construction', 'ruins', 'no', 'bunker', 'toilets', 'shelter', 'transformer_tower', 'substation']);
  const bSrc = readJson(`${dir}/buildings.json`);
  const buildings = [];
  if (bSrc) {
    let noLevels = 0, skipped = 0;
    for (const e of bSrc.elements) {
      if (!e.center || !e.tags) continue;
      if (SKIP_BUILDING.has(e.tags.building)) { skipped++; continue; }
      let lv = parseInt(e.tags['building:levels'], 10);
      /* Высота в метрах встречается чаще уровней у башен; 3.2 м на этаж —
         грубо, но у башни ошибка в этаж ничего не решает. */
      if (!lv && e.tags.height) lv = Math.round(parseFloat(e.tags.height) / 3.2);
      if (!lv || !Number.isFinite(lv) || lv < 1 || lv > 120) { noLevels++; continue; }
      buildings.push([+e.center.lat.toFixed(5), +e.center.lon.toFixed(5), lv]);
    }
    report.push(`дома: ${buildings.length} с этажностью, ${noLevels} без неё, ${skipped} служебных отброшено`);
  } else report.push('дома: файла нет');

  /* ---- улицы ---- */
  const sSrc = readJson(`${dir}/streets.json`);
  const streets = [];
  if (sSrc) {
    const by = new Map();
    for (const e of sSrc.elements) {
      const name = e.tags && e.tags.name;
      if (!name || !e.geometry) continue;
      if (!by.has(name)) by.set(name, []);
      by.get(name).push(simplify(geomToPts(e.geometry), tol));
    }
    for (const [name, paths] of by) streets.push({ name, paths: paths.filter((p) => p.length > 1) });
    report.push(`улицы: ${streets.length} названий, ${streets.reduce((s, x) => s + x.paths.length, 0)} участков`);
  } else report.push('улицы: файла нет');

  fs.mkdirSync(out.replace(/\/[^/]+$/, ''), { recursive: true });
  fs.writeFileSync(out, JSON.stringify({
    source: 'OpenStreetMap (Overpass API), ODbL',
    bbox: a.bbox || '55.70,37.53 — 55.765,37.65',
    simplifiedToM: tol,
    river, green, buildings, streets,
  }) + '\n');
  report.forEach((r) => process.stdout.write(r + '\n'));
  process.stdout.write(`\n${out}: ${(fs.statSync(out).size / 1024).toFixed(0)} КБ\n`);
}

if (require.main === module) main();
module.exports = { simplify, ringHa };
