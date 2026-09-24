#!/usr/bin/env node
'use strict';
/* Сборка контура МКАД из выгрузки OpenStreetMap — без сети.

   Выгрузка (Overpass из сессии недоступен, API OSM — да):
     curl -A "..." -o mkad.json \
       https://api.openstreetmap.org/api/0.6/relation/2094222/full.json
   Сборка:
     node tools/cian/census/build-mkad.js --osm mkad.json [--out tools/cian/geo/mkad.json] */
const fs = require('fs');
const { buildRing, FILE } = require('./mkad');

const argv = process.argv.slice(2);
const opt = (k, d) => { const i = argv.indexOf(`--${k}`); return i >= 0 ? argv[i + 1] : d; };
const src = opt('osm');
if (!src) { console.log(fs.readFileSync(__filename, 'utf8').split('*/')[0]); process.exit(1); }
const out = opt('out', FILE);

const osm = JSON.parse(fs.readFileSync(src, 'utf8'));
const rel = osm.elements.find((e) => e.type === 'relation');
const ways = osm.elements.filter((e) => e.type === 'way' && e.tags && /^(trunk|motorway)$/.test(e.tags.highway));
const nodes = new Map(osm.elements.filter((e) => e.type === 'node').map((n) => [n.id, n]));
const points = [];
for (const w of ways) for (const id of w.nodes) { const n = nodes.get(id); if (n) points.push({ lat: n.lat, lng: n.lon }); }

const ring = buildRing(points);
const r = ring.r.filter((x) => x != null);
const doc = {
  source: `OpenStreetMap, relation ${rel ? rel.id : '?'} «${rel && rel.tags ? rel.tags.name : ''}» (ODbL)`,
  built: new Date().toISOString().slice(0, 10),
  ways: ways.length,
  points: points.length,
  interpolatedBins: ring.interpolated,
  radiusKm: { min: +(Math.min(...r) / 1000).toFixed(1), max: +(Math.max(...r) / 1000).toFixed(1) },
  ...ring,
};
fs.writeFileSync(out, JSON.stringify(doc) + '\n');
console.log(`${out}: ${ways.length} участков, ${points.length} точек; радиус кольца ${doc.radiusKm.min}–${doc.radiusKm.max} км; ` +
  `интерполировано делений ${ring.interpolated} из ${ring.bins}; ширина дороги медиана ${median(ring.spread.filter((x) => x != null))} м`);

function median(xs) { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; }
