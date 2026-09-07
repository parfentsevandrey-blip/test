#!/usr/bin/env node
/**
 * Свести собранные лоты (search --out) в таблицу по ЖК.
 *
 *   node tools/cian/aggregate-complexes.js lots/*.json --out complexes.json
 *
 * Для каждого ЖК: район, адрес, координаты, год постройки / срок сдачи,
 * этажность, корпусов, квартиры или апартаменты, лотов в продаже, цена за м²
 * (мин / медиана / макс), доля лотов от застройщика, положение относительно
 * Садового кольца по контуру из cian.js.
 */
const fs = require('fs');
const path = require('path');
const { median, insideGardenRing, ringVerdict, buildingYear } = require('./cian.js');

const args = process.argv.slice(2);
const outIdx = args.indexOf('--out');
const out = outIdx >= 0 ? args[outIdx + 1] : null;
const files = args.filter((a, i) => a !== '--out' && i !== outIdx + 1);

const byId = new Map();
const declaredByComplex = new Map();
for (const f of files) {
  const j = JSON.parse(fs.readFileSync(f, 'utf8'));
  const tag = path.basename(f, '.json');
  for (const l of j.lots || []) if (!byId.has(l.id)) byId.set(l.id, { ...l, _src: tag });
  /* Точечный запрос по одному ЖК: offerCount Циан — честнее, чем перечисленное,
     потому что пагинация обрывается, а «похожие» схлопываются. */
  const geo = (((j.jsonQuery || {}).geo || {}).value) || [];
  if (geo.length === 1 && geo[0].type === 'newobject' && j.declaredCount && (j.lots || []).length) {
    const name = j.lots[0].complex;
    if (name) declaredByComplex.set(name, Math.max(declaredByComplex.get(name) || 0, j.declaredCount));
  }
}
const lots = [...byId.values()];

const complexes = new Map();
for (const l of lots) {
  const key = l.complex || null;
  if (!key) continue;
  if (!complexes.has(key)) complexes.set(key, []);
  complexes.get(key).push(l);
}

const mode = (arr) => {
  const m = new Map();
  for (const v of arr) if (v != null && v !== '') m.set(v, (m.get(v) || 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
};
const uniq = (arr) => [...new Set(arr.filter((v) => v != null && v !== ''))];

const rows = [];
for (const [name, ls] of complexes) {
  const perM2 = ls.filter((l) => l.priceRub && l.totalArea).map((l) => Math.round(l.priceRub / l.totalArea)).sort((a, b) => a - b);
  const years = uniq(ls.map((l) => l.buildYear));
  const deadlines = uniq(ls.map((l) => l.deadline && l.deadline.year));
  const dlQ = ls.map((l) => l.deadline).filter(Boolean).sort((a, b) => (b.year - a.year) || ((b.quarter || 0) - (a.quarter || 0)))[0] || null;
  const floors = uniq(ls.map((l) => l.floors)).sort((a, b) => a - b);
  const houses = uniq(ls.map((l) => l.houseId));
  const coords = ls.find((l) => l.lat != null) || {};
  const ring = ringVerdict(coords);
  const finished = ls.map((l) => l.houseFinished).filter((v) => v != null);
  const apart = ls.filter((l) => l.isApartments).length;
  const fromDev = ls.filter((l) => l.fromDeveloper).length;
  const src = uniq(ls.map((l) => l._src));
  rows.push({
    complex: name,
    okrug: mode(ls.map((l) => l.okrug)),
    district: mode(ls.map((l) => l.district)),
    street: mode(ls.map((l) => l.street)),
    house: mode(ls.map((l) => l.house)),
    addresses: uniq(ls.map((l) => [l.street, l.house].filter(Boolean).join(', '))).slice(0, 6),
    lat: coords.lat ?? null, lng: coords.lng ?? null,
    insideRing: ring.inside, ringMargin: ring.margin, ringSure: ring.sure,
    buildYears: years.sort(), buildYearMin: years.length ? Math.min(...years) : null, buildYearMax: years.length ? Math.max(...years) : null,
    deadlineYears: deadlines.sort(), deadline: dlQ,
    finishedShare: finished.length ? Math.round(100 * finished.filter(Boolean).length / finished.length) : null,
    floorsMin: floors[0] ?? null, floorsMax: floors[floors.length - 1] ?? null,
    housesSeen: houses.length,
    lots: ls.length, declared: declaredByComplex.get(name) ?? null, apartmentsShare: Math.round(100 * apart / ls.length), fromDeveloperShare: Math.round(100 * fromDev / ls.length),
    saleTypes: uniq(ls.map((l) => l.saleType)),
    decorations: uniq(ls.map((l) => l.decoration)),
    perM2Min: perM2[0] ?? null, perM2Median: perM2.length ? median(perM2) : null, perM2Max: perM2[perM2.length - 1] ?? null,
    priceMin: Math.min(...ls.map((l) => l.priceRub || Infinity)), priceMax: Math.max(...ls.map((l) => l.priceRub || 0)),
    areaMin: Math.min(...ls.map((l) => l.totalArea || Infinity)), areaMax: Math.max(...ls.map((l) => l.totalArea || 0)),
    urls: ls.slice(0, 3).map((l) => l.url),
    sources: src,
  });
}
rows.sort((a, b) => (b.perM2Median || 0) - (a.perM2Median || 0));
const res = { fetched: new Date().toISOString().slice(0, 10), lots: lots.length, withComplex: lots.filter((l) => l.complex).length, complexes: rows };
if (out) { fs.writeFileSync(out, JSON.stringify(res, null, 2) + '\n'); console.log(`-> ${out}: ${rows.length} ЖК из ${lots.length} лотов`); }
else console.log(JSON.stringify(res, null, 2));
