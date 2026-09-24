#!/usr/bin/env node
'use strict';
/* Перепись: все объявления о продаже квартир в заданных округах — короткими
   шагами, которые продолжают друг друга.

   Облачная сессия живёт недолго, одна команда — не дольше десяти минут, а
   антибот может остановить прогон в любой момент. Поэтому перепись — это
   автомат с состоянием на диске: каждый запрос сдвигает его на шаг и сразу
   сохраняется, и следующий прогон продолжает с того же места. Убитая сессия
   теряет не больше одного запроса.

   Порядок работы по округу (логика ломтиков — из откаченного mass, замеры
   по ЗАО — traps.md):
   1. ломтики курсором по цене: у хвоста [lo, …] читается первая страница и,
      если хвост больше потолка выдачи, пятидесятая — цена её первого лота и
      есть граница ломтика. Глубина выдачи — ровно 1 500 на запрос;
   2. страницы ломтиков подряд, в ценовой сортировке, до первой пустой;
   3. группы похожих: лидер прячет за собой другие объявления, и достать их
      можно только ключом multi_id. Политика --groups: resale (по умолчанию —
      раскрывать группы вторички, у новостроек размер группы берётся с
      лидера), all, none.

   Запросы идут осторожным клиентом (client.js): один за раз, пауза с
   разбросом, бюджет на прогон, остановка на первом признаке антибота.

     node tools/cian/census/census.js init   --dir D [--okrugs 4,5,6] [--groups resale|all|none]
     node tools/cian/census/census.js step   --dir D [--budget 120] [--gap 2000] [--jitter 1500]
     node tools/cian/census/census.js status --dir D

   Коды выхода step: 0 — перепись закончена, 2 — кончился бюджет (продолжить
   следующим step), 3 — антибот (STOP, выдержка), 1 — прочее. */
const fs = require('fs');
const path = require('path');

const DEPTH = 1500;                  // офферов на один запрос, не больше
const PAGE_SIZE = 28;
const SLICE_CAP = 1400;              // запас: за время переписи выдача подрастает
const CURSOR_PAGE = 50;              // ранг 1373 — граница следующего ломтика
const PRICE_SORT = 'price_object_order';
const ROOMS = [1, 2, 3, 4, 5, 6, 7, 9];   // без долей (8) и койко-мест (10)
const MSK = { _type: 'flatsale', engine_version: { type: 'term', value: 2 }, region: { type: 'terms', value: [1] } };
const OLD_OKRUGS = [4, 5, 6, 7, 8, 9, 10, 11, 1];

const log = (...a) => process.stdout.write(a.join(' ') + '\n');

/* Ломтик по цене внутри собственного ценового фильтра запроса: дробление
   обязано сужать (traps.md, п. 10). */
function priceSlice(q, lo, hi) {
  const own = (q.price && q.price.value) || {};
  const gte = Math.max(lo || 0, own.gte || 0);
  const lte = hi == null ? own.lte : (own.lte == null ? hi : Math.min(hi, own.lte));
  const value = {};
  if (gte > 0) value.gte = gte;
  if (lte != null) value.lte = lte;
  const out = { ...q };
  if (Object.keys(value).length) out.price = { type: 'range', value };
  else delete out.price;
  return out;
}

/* Сколько страниц у ломтика: по его перечислимому числу плюс одна на
   прирост, не глубже потолка выдачи. */
function slicePages(aggregated) {
  if (aggregated == null) return CURSOR_PAGE + 1;
  return Math.min(Math.ceil(DEPTH / PAGE_SIZE), Math.ceil(aggregated / PAGE_SIZE) + 1);
}

/* Первичка в смысле переписи: сток застройщика, который ведётся по корпусу и
   планировке, а не по квартире. */
const isPrimary = (l) => !!(l.fromDeveloper || l.saleType === 'fz214' || l.saleType === 'dupt' || l.houseFinished === false);

function unitQuery(id, extraQ = {}) {
  return {
    ...MSK,
    geo: { type: 'geo', value: [{ type: 'district', id }] },
    room: { type: 'terms', value: ROOMS },
    ...extraQ,
    sort: { type: 'term', value: PRICE_SORT },
  };
}

function newState(units, o = {}) {
  return {
    version: 1,
    created: new Date().toISOString(),
    groups: o.groups || 'resale',
    units: units.map((u) => ({
      id: u.id, name: u.name, q: u.q, phase: 'start',
      declared: null, aggregated: null,
      cursor: null, slices: [], grp: null, holes: [], requests: 0,
    })),
  };
}

/* Индекс собранного: id → то, что нужно автомату (цена, группа, откуда
   пришёл). Полные записи лежат в lots.jsonl, по строке на объявление. */
function makeStore(file) {
  const index = new Map();
  if (file && fs.existsSync(file)) {
    for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
      if (!line) continue;
      try {
        const l = JSON.parse(line);
        index.set(l.id, brief(l));
      } catch (e) { /* недописанная строка обрыва */ }
    }
  }
  return {
    index,
    add(lots, meta) {
      let fresh = 0;
      for (const l of lots) {
        if (l.id == null || index.has(l.id)) continue;
        const rec = { ...l, unit: meta.unit, via: meta.via, seen: meta.at };
        index.set(l.id, brief(rec));
        if (file) fs.appendFileSync(file, JSON.stringify(rec) + '\n');
        fresh++;
      }
      return fresh;
    },
  };
}
function brief(l) {
  return { price: l.priceRub ?? null, similar: l.similarCount || 0, primary: isPrimary(l), via: l.via || 'page', unit: l.unit ?? null };
}

/* Один шаг автомата для одного округа: один запрос к API. Возвращает false,
   когда округу больше нечего делать. */
async function stepUnit(u, st, store, api, toLot, o = {}) {
  const at = new Date().toISOString();
  const ask = async (q, page, op) => {
    const r = await api.search(q, page, op);
    u.requests++;
    const lots = r.offers.map(toLot);
    return { ...r, lots };
  };
  const own = (u.q.price && u.q.price.value) || {};
  const fatal = (e) => e.code === 'ANTIBOT' || e.code === 'BUDGET';
  const hole = (where, e) => { u.holes.push({ where, why: String(e.message || e).slice(0, 120), at }); };

  if (u.phase === 'start') {
    u.cursor = { lo: own.gte || 0, first: null };
    u.phase = 'slicing';
  }

  if (u.phase === 'slicing') {
    const c = u.cursor;
    const tail = priceSlice(u.q, c.lo, own.lte ?? null);
    if (!c.first) {
      let r;
      try { r = await ask(tail, 1, 'tail'); } catch (e) {
        if (fatal(e)) throw e;
        hole(`хвост от ${c.lo}, стр.1`, e);
        c.fails = (c.fails || 0) + 1;
        if (c.fails >= 3) { u.phase = 'done'; return false; }
        return true;
      }
      store.add(r.lots, { unit: u.id, via: 'page', at });
      if (u.declared == null) { u.declared = r.count; u.aggregated = r.aggregated; }
      c.first = { aggregated: r.aggregated, n: r.lots.length };
      return true;
    }
    const finalSlice = (pages) => {
      u.slices.push({ lo: c.lo, hi: own.lte ?? null, aggregated: c.first.aggregated, pages, next: 2, done: c.first.n < PAGE_SIZE });
      u.phase = 'pages';
    };
    if (c.first.aggregated == null || c.first.aggregated <= SLICE_CAP) { finalSlice(slicePages(c.first.aggregated)); return true; }
    let r;
    try { r = await ask(tail, CURSOR_PAGE, 'cursor'); } catch (e) { if (fatal(e)) throw e; hole(`курсор от ${c.lo}`, e); finalSlice(Math.ceil(DEPTH / PAGE_SIZE)); return true; }
    store.add(r.lots, { unit: u.id, via: 'page', at });
    /* Пятидесятая пуста — хвост перечисляется меньше, чем насчитано, и он сам
       по себе последний ломтик. */
    if (!r.lots.length) { finalSlice(slicePages(c.first.aggregated)); return true; }
    const edge = (r.lots.find((l) => l.priceRub != null) || {}).priceRub;
    if (!edge || edge <= c.lo) {
      u.holes.push({ where: `ломтик от ${c.lo}`, why: 'не режется ценой: курсор не сдвинулся', at });
      finalSlice(Math.ceil(DEPTH / PAGE_SIZE));
      return true;
    }
    u.slices.push({ lo: c.lo, hi: edge - 1, aggregated: null, pages: CURSOR_PAGE + 1, next: 2, done: false });
    u.cursor = { lo: edge, first: null };
    return true;
  }

  if (u.phase === 'pages') {
    const s = u.slices.find((x) => !x.done);
    if (!s) { u.phase = 'groups'; return true; }
    let r;
    try { r = await ask(priceSlice(u.q, s.lo, s.hi), s.next, 'page'); } catch (e) {
      if (fatal(e)) throw e;
      hole(`ломтик ${s.lo}–${s.hi ?? '∞'}, стр.${s.next}`, e);
      s.next++;
      if (s.next > s.pages) s.done = true;
      return true;
    }
    store.add(r.lots, { unit: u.id, via: 'page', at });
    if (s.aggregated == null && r.aggregated != null) s.aggregated = r.aggregated;
    if (!r.lots.length) { s.done = true; s.end = s.next; return true; }
    s.next++;
    if (s.next > s.pages) s.done = true;
    return true;
  }

  if (u.phase === 'groups') {
    if (!u.grp) {
      const policy = st.groups;
      const leaders = policy === 'none' ? [] : [...store.index.entries()]
        .filter(([, b]) => b.unit === u.id && b.via === 'page' && b.similar > 0 && (policy === 'all' || !b.primary))
        .sort((x, y) => y[1].similar - x[1].similar)
        .map(([id, b]) => ({ id, similar: b.similar }));
      u.grp = { queue: leaders, i: 0, page: 1, expanded: 0, skipped: 0, covered: [] };
      return true;
    }
    const g = u.grp;
    /* Крупные группы раскрываются первыми: их члены часто оказываются
       лидерами соседних ломтиков, и такие группы уже не запрашиваются. */
    const covered = new Set(g.covered);
    while (g.i < g.queue.length && g.page === 1 && covered.has(g.queue[g.i].id)) { g.i++; g.skipped++; }
    if (g.i >= g.queue.length) { u.phase = 'done'; return false; }
    const L = g.queue[g.i];
    const maxPages = Math.min(Math.ceil(DEPTH / PAGE_SIZE), Math.ceil((L.similar + 1) / PAGE_SIZE) + 1);
    const q = { ...u.q, multi_id: { type: 'term', value: L.id } };
    delete q.price;
    let r;
    try { r = await ask(q, g.page, 'group'); } catch (e) {
      if (fatal(e)) throw e;
      hole(`группа ${L.id}, стр.${g.page}`, e);
      g.i++; g.page = 1;
      return true;
    }
    /* multi_id не уважает остальные фильтры (traps.md, п. 27): члены группы
       записываются как есть, отсев — при сборке переписи. */
    store.add(r.lots, { unit: u.id, via: 'group', at });
    const queued = new Set(g.queue.slice(g.i + 1).map((x) => x.id));
    for (const m of r.lots) if (queued.has(m.id) && !covered.has(m.id)) { covered.add(m.id); g.covered.push(m.id); }
    if (g.page === 1) g.expanded++;
    if (r.lots.length < PAGE_SIZE || g.page >= maxPages) { g.i++; g.page = 1; } else g.page++;
    return true;
  }
  return false;
}

/* Шаги подряд, пока не кончится работа или бюджет. После каждого шага —
   запись состояния: обрыв теряет не больше одного запроса. */
async function run(st, store, api, toLot, o = {}) {
  const save = o.save || (() => {});
  for (const u of st.units) {
    while (u.phase !== 'done') {
      const more = await stepUnit(u, st, store, api, toLot, o);
      save(st);
      if (!more) break;
    }
  }
  return st.units.every((u) => u.phase === 'done');
}

/* Сводка по состоянию и собранному: что заявлено, что прочитано, где дыры. */
function status(st, store) {
  const rows = st.units.map((u) => {
    const mine = [...store.index.values()].filter((b) => b.unit === u.id);
    const page = mine.filter((b) => b.via === 'page').length;
    const slicesDone = u.slices.filter((s) => s.done).length;
    return {
      id: u.id, name: u.name, phase: u.phase, declared: u.declared, aggregated: u.aggregated,
      listed: page, fromGroups: mine.length - page, total: mine.length,
      coverage: u.aggregated ? +(page / u.aggregated).toFixed(3) : null,
      slices: `${slicesDone}/${u.slices.length}`, groups: u.grp ? `${u.grp.i}/${u.grp.queue.length}` : '—',
      holes: u.holes.length, requests: u.requests,
      leaders: mine.filter((b) => b.via === 'page' && b.similar > 0).length,
      hidden: mine.filter((b) => b.via === 'page').reduce((n, b) => n + b.similar, 0),
      primary: mine.filter((b) => b.primary).length,
    };
  });
  return { rows, lots: store.index.size, requests: rows.reduce((n, r) => n + r.requests, 0) };
}

/* ---------- командная строка ---------- */

function args(argv) {
  const a = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) {
      const k = argv[i].slice(2);
      a[k] = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : true;
    } else a._.push(argv[i]);
  }
  return a;
}

function writeJson(file, v) {
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(v, null, 1));
  fs.renameSync(tmp, file);
}

async function main() {
  const a = args(process.argv.slice(2));
  const cmd = a._[0];
  if (!cmd || a.help) { log(fs.readFileSync(__filename, 'utf8').split('*/')[0]); return 0; }
  const dir = a.dir || 'census-data';
  fs.mkdirSync(dir, { recursive: true });
  const STATE = path.join(dir, 'state.json');
  const LOTS = path.join(dir, 'lots.jsonl');

  if (cmd === 'init') {
    if (fs.existsSync(STATE) && !a.force) { log(`! ${STATE} уже есть — перепись идёт; --force начнёт заново`); return 1; }
    const GEO = require('../../../docs/cian/moscow-geo.json');
    const names = new Map(GEO.okrugs.map((o) => [o.id, o.name]));
    for (const r of GEO.okrugs.flatMap((o) => o.raions)) names.set(r.id, r.name);
    const ids = a.okrugs ? String(a.okrugs).split(',').map(Number) : OLD_OKRUGS;
    const extraQ = a.query ? JSON.parse(fs.existsSync(a.query) ? fs.readFileSync(a.query, 'utf8') : a.query) : {};
    const st = newState(ids.map((id) => ({ id, name: names.get(id) || String(id), q: unitQuery(id, extraQ) })), { groups: a.groups });
    writeJson(STATE, st);
    if (a.force && fs.existsSync(LOTS)) fs.unlinkSync(LOTS);
    log(`перепись заведена: ${st.units.map((u) => u.name).join(', ')}; группы: ${st.groups}`);
    return 0;
  }

  if (!fs.existsSync(STATE)) { log(`! нет ${STATE} — сначала init`); return 1; }
  const st = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const store = makeStore(LOTS);

  if (cmd === 'status') {
    const s = status(st, store);
    for (const r of s.rows) {
      log(`${r.name.padEnd(12)} ${r.phase.padEnd(8)} заявлено ${String(r.declared ?? '—').padStart(6)}, перечислимо ${String(r.aggregated ?? '—').padStart(6)}, ` +
        `прочитано ${String(r.listed).padStart(6)} (${r.coverage == null ? '—' : Math.round(r.coverage * 100) + '%'}) + из групп ${r.fromGroups}; ` +
        `ломтики ${r.slices}, группы ${r.groups}, лидеров ${r.leaders} (за ними ${r.hidden}), первичка ${r.primary}, дыр ${r.holes}, запросов ${r.requests}`);
    }
    log(`всего объявлений ${s.lots}, запросов ${s.requests}`);
    return 0;
  }

  if (cmd === 'step') {
    const { openSession, careful, stopGuard } = require('./client');
    const { record } = require('./pilot');
    const STOP = path.join(dir, 'STOP');
    const guard = stopGuard(STOP, parseFloat(a.cooldown || '2') * 3600e3);
    if (guard) { log(`! антибот останавливал сбор ${guard.at} (${guard.why}); выдержка ещё ${Math.ceil(guard.leftMs / 60000)} мин`); return 3; }
    const TELE = path.join(dir, 'telemetry.jsonl');
    let s;
    try { s = await openSession({ state: path.join(dir, 'cookies.json') }); } catch (e) {
      if (e.code === 'ANTIBOT') fs.writeFileSync(STOP, JSON.stringify({ at: new Date().toISOString(), why: e.message }, null, 1));
      log(`! ${e.message}`);
      return e.code === 'ANTIBOT' ? 3 : 1;
    }
    const api = careful(s, { budget: parseInt(a.budget || '120', 10), gap: parseInt(a.gap || '2000', 10), jitter: parseInt(a.jitter || '1500', 10), telemetry: TELE });
    let code = 0;
    try {
      const done = await run(st, store, api, record, { save: (x) => writeJson(STATE, x) });
      code = done ? 0 : 2;
    } catch (e) {
      if (e.code === 'ANTIBOT') { fs.writeFileSync(STOP, JSON.stringify({ at: new Date().toISOString(), why: e.message, after: api.st.sent }, null, 1)); code = 3; }
      else if (e.code === 'BUDGET') code = 2;
      else { await s.browser.close(); throw e; }
      log(`! ${e.message}`);
    }
    writeJson(STATE, st);
    log(api.report());
    const sum = status(st, store);
    for (const r of sum.rows.filter((x) => x.phase !== 'start')) {
      log(`  ${r.name}: ${r.phase}, прочитано ${r.listed}/${r.aggregated ?? '—'} + из групп ${r.fromGroups}, ломтики ${r.slices}, группы ${r.groups}, дыр ${r.holes}`);
    }
    log(`  всего объявлений ${sum.lots}`);
    if (code === 3) await s.browser.close(); else await s.close();
    return code;
  }

  log(`неизвестная команда: ${cmd}`);
  return 1;
}

if (require.main === module) {
  main().then((code) => process.exit(code || 0), (e) => { log(e.stack || String(e)); process.exit(1); });
}

module.exports = { priceSlice, slicePages, isPrimary, unitQuery, newState, makeStore, stepUnit, run, status,
  DEPTH, PAGE_SIZE, SLICE_CAP, CURSOR_PAGE, ROOMS, OLD_OKRUGS };
