#!/usr/bin/env node
'use strict';
/* Пилот переписи: короткие замеры реальной картины против плана
   (docs/cian/census/PLAN.md). Каждая команда — один прогон со своим
   бюджетом запросов. В --dir пишутся телеметрия, ответы и сводки; там же
   куки между прогонами и файл STOP после встречи с антиботом.

     node tools/cian/census/pilot.js canary --dir D
     node tools/cian/census/pilot.js counts --dir D --set base|outside
     node tools/cian/census/pilot.js read   --dir D --name N --query q.json [--pages 54]
     node tools/cian/census/pilot.js groups --dir D --name N [--n 12] [--pages 2]
     node tools/cian/census/pilot.js photos --dir D --name N [--n 3]

   Общие флаги: --budget (запросов на прогон), --gap и --jitter (мс паузы
   после ответа), --cooldown (часов выдержки после антибота, по умолчанию 2).
   Коды выхода: 0 — готово, 2 — кончился бюджет, 3 — антибот, 1 — прочее. */
const fs = require('fs');
const path = require('path');
const { openSession, careful, stopGuard } = require('./client');
const { normalize } = require('../cian');
const GEO = require('../../../docs/cian/moscow-geo.json');

const log = (...a) => process.stdout.write(a.join(' ') + '\n');

const MSK = { _type: 'flatsale', engine_version: { type: 'term', value: 2 }, region: { type: 'terms', value: [1] } };
const geo = (...ids) => ({ geo: { type: 'geo', value: ids.map((id) => ({ type: 'district', id })) } });
const term = (value) => ({ type: 'term', value });
const terms = (value) => ({ type: 'terms', value });

/* Девять округов внутри МКАД (с заМКАДными районами внутри них). */
const OLD_OKRUGS = [4, 5, 6, 7, 8, 9, 10, 11, 1];
/* Районы старых округов целиком за МКАД и два частично: у Кунцева за МКАД
   Рублёво и Мякинино, у Выхино-Жулебина — Жулебино. Точный отбор — по
   контуру МКАД и координатам лота; здесь — порядок величины. */
const OUTSIDE = [33, 52, 58, 63, 66, 78, 107, 110, 112, 117, 121, 125, 126];
const PARTLY = [72, 115];

const NAMES = new Map();
for (const o of GEO.okrugs) {
  NAMES.set(o.id, o.name);
  for (const r of o.raions) NAMES.set(r.id, r.name);
}

function args(argv) {
  const a = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) {
      const k = argv[i].slice(2);
      const v = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : true;
      a[k] = v;
    } else a._.push(argv[i]);
  }
  return a;
}

function loadQuery(q) {
  if (!q) throw new Error('нужен --query');
  return JSON.parse(fs.existsSync(q) ? fs.readFileSync(q, 'utf8') : q);
}

function readJsonl(file) {
  if (!fs.existsSync(file)) return [];
  return fs.readFileSync(file, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
}

const median = (xs) => {
  const s = xs.filter((x) => x != null).sort((a, b) => a - b);
  return s.length ? s[Math.floor(s.length / 2)] : null;
};
const pct = (n, d) => (d ? `${Math.round((100 * n) / d)}%` : '—');
const tally = (xs) => xs.reduce((m, x) => ((m[x ?? '—'] = (m[x ?? '—'] || 0) + 1), m), {});

/* Поля сырого ответа, которых нет в normalize и которые нужны переписи:
   размер группы похожих, кадастровые номера, флаг реновации, отпечаток
   текста (minhash), ЖК и застройщик, номера кадров. */
function extra(o) {
  const nb = o.newbuilding || null;
  const ph = (o.photos || []).find((p) => !p.isLayout) || null;
  return {
    similar: o.similar ? { count: o.similar.count ?? null } : null,
    cad: o.cadastralNumber || null,
    bcad: o.buildingCadastralNumber || null,
    demolished: o.demolishedInMoscowProgramm ?? null,
    minhash: Array.isArray(o.descriptionMinhash) ? o.descriptionMinhash : null,
    dupDesc: o.isDuplicatedDescription ?? null,
    nbId: nb ? nb.id ?? null : null,
    nbName: nb ? nb.name ?? null : null,
    builders: o.buildersIds || null,
    rosreestr: o.isRosreestrChecked ?? null,
    classType: (o.building || {}).classType ?? null,
    status: o.status ?? null,
    byHomeowner: o.isByHomeowner ?? null,
    photoIds: (o.photos || []).map((p) => p.id),
    photo: ph ? { full: ph.fullUrl, thumb: ph.thumbnailUrl, thumb2: ph.thumbnail2Url, mini: ph.miniUrl } : null,
  };
}

function record(o) {
  const n = normalize(o);
  delete n.description;          // текст не нужен пилоту; отпечаток — в minhash
  return { ...n, x: extra(o) };
}

async function main() {
  const a = args(process.argv.slice(2));
  const cmd = a._[0];
  if (!cmd || a.help) { log(fs.readFileSync(__filename, 'utf8').split('*/')[0]); return 0; }
  const dir = a.dir || 'census-pilot';
  fs.mkdirSync(dir, { recursive: true });
  const TELE = path.join(dir, 'telemetry.jsonl');
  const STATE = path.join(dir, 'state.json');
  const STOP = path.join(dir, 'STOP');
  const cooldown = parseFloat(a.cooldown || '2') * 3600e3;

  const guard = stopGuard(STOP, cooldown);
  if (guard) {
    log(`! антибот останавливал сбор ${guard.at} (${guard.why}); выдержка ещё ${Math.ceil(guard.leftMs / 60000)} мин — в сеть не идём`);
    return 3;
  }
  const writeStop = (why, extraInfo = {}) => {
    fs.writeFileSync(STOP, JSON.stringify({ at: new Date().toISOString(), why, cmd, ...extraInfo }, null, 1));
    if (fs.existsSync(STATE)) fs.unlinkSync(STATE);   // помеченные куки не нужны
  };

  let s;
  try {
    s = await openSession({ state: STATE });
  } catch (e) {
    fs.appendFileSync(TELE, JSON.stringify({ t: new Date().toISOString(), op: 'home', v: e.code === 'ANTIBOT' ? 'antibot' : 'net', why: e.message }) + '\n');
    if (e.code === 'ANTIBOT') writeStop(e.message);
    log(`! ${e.message}`);
    return e.code === 'ANTIBOT' ? 3 : 1;
  }
  fs.appendFileSync(TELE, JSON.stringify({ t: new Date().toISOString(), op: 'home', status: s.home.status, ms: s.home.ms, v: 'ok', cookies: s.home.cookies }) + '\n');
  log(`главная: http ${s.home.status}, ${s.home.ms} мс, ${s.home.cookies ? 'куки прошлого прогона' : 'новый посетитель'}`);

  const api = careful(s, {
    budget: parseInt(a.budget || '40', 10),
    gap: parseInt(a.gap || '2000', 10),
    jitter: parseInt(a.jitter || '1500', 10),
    telemetry: TELE,
  });
  const cmds = { canary, counts, read, groups, photos };
  if (!cmds[cmd]) { await s.close(); log(`неизвестная команда: ${cmd}`); return 1; }

  let code = 0;
  try {
    await cmds[cmd](api, a, dir);
  } catch (e) {
    if (e.code === 'ANTIBOT') { writeStop(e.message, { after: api.st.sent }); code = 3; }
    else if (e.code === 'BUDGET') code = 2;
    else if (e.code === 'FAILED') code = 1;
    else { await s.close(); throw e; }
    log(`! ${e.message}`);
  }
  log(api.report());
  if (code === 3) await s.browser.close(); else await s.close();
  return code;
}

/* Один запрос: жив ли адрес и что отвечает поиск по всей Москве. */
async function canary(api) {
  const r = await api.search(MSK, 1, 'canary');
  log(`поиск: заявлено ${r.count}, перечислимо ${r.aggregated}, на странице ${r.offers.length}`);
}

/* Счётчики: по запросу на строку. */
async function counts(api, a, dir) {
  const set = a.set || 'base';
  const rows = set === 'outside'
    ? [...OUTSIDE.map((id) => [`${NAMES.get(id)} (за МКАД)`, { ...MSK, ...geo(id) }]),
       ...PARTLY.map((id) => [`${NAMES.get(id)} (частично)`, { ...MSK, ...geo(id) }])]
    : [
      ['Москва, всё', MSK],
      ['без долей и койко-мест', { ...MSK, room: terms([1, 2, 3, 4, 5, 6, 7, 9]) }],
      ['вторичка', { ...MSK, building_status: term(1) }],
      ['новостройки', { ...MSK, building_status: term(2) }],
      ['только апартаменты', { ...MSK, apartment: term(true) }],
      ['опубликовано за сутки', { ...MSK, publish_period: term(86400) }],
      ['дома под снос (реновация)', { ...MSK, demolished_in_moscow_programm: term(true) }],
      ...OLD_OKRUGS.map((id) => [NAMES.get(id), { ...MSK, ...geo(id) }]),
      ...[325, 326, 151].map((id) => [`${NAMES.get(id)} (вне МКАД)`, { ...MSK, ...geo(id) }]),
    ];
  const out = [];
  const file = path.join(dir, `counts-${set}.json`);
  try {
    for (const [name, q] of rows) {
      const r = await api.search(q, 1, 'count');
      out.push({ name, count: r.count, aggregated: r.aggregated, queryString: r.queryString });
      log(`${name.padEnd(34)} ${String(r.count).padStart(7)} ${String(r.aggregated).padStart(7)}`);
    }
  } finally {
    fs.writeFileSync(file, JSON.stringify({ at: new Date().toISOString(), set, rows: out }, null, 1));
  }
}

/* Полное чтение одного запроса в ценовой сортировке: страницы подряд до
   пустой. Сколько запросов на тысячу лотов, какая доля за лидерами групп,
   какие поля реально приходят. */
async function read(api, a, dir) {
  const name = a.name || 'slice';
  const q = { ...loadQuery(a.query), sort: term(a.sort || 'price_object_order') };
  const maxPages = parseInt(a.pages || '54', 10);
  const file = path.join(dir, `read-${name}.jsonl`);
  const summaryFile = path.join(dir, `read-${name}.json`);
  fs.writeFileSync(file, '');
  const seen = new Set();
  const head = { at: new Date().toISOString(), query: q, pages: 0, requests0: api.st.sent, dupAcrossPages: 0 };
  try {
    for (let p = 1; p <= maxPages; p++) {
      const r = await api.search(q, p, 'read');
      if (p === 1) Object.assign(head, { count: r.count, aggregated: r.aggregated, queryString: r.queryString });
      if (!r.offers.length) break;
      head.pages = p;
      for (const o of r.offers) {
        const id = o.cianId || o.id;
        if (seen.has(id)) { head.dupAcrossPages++; continue; }
        seen.add(id);
        fs.appendFileSync(file, JSON.stringify(record(o)) + '\n');
      }
      if (p % 10 === 0) log(`  стр. ${p}: ${seen.size} лотов`);
    }
  } finally {
    head.requests = api.st.sent - head.requests0;
    const lots = readJsonl(file);
    head.summary = summarizeLots(lots, head);
    fs.writeFileSync(summaryFile, JSON.stringify(head, null, 1));
    printSummary(head);
  }
}

function summarizeLots(lots, head) {
  const n = lots.length;
  const leaders = lots.filter((l) => (l.similarCount || 0) > 0);
  const hidden = leaders.reduce((s, l) => s + l.similarCount, 0);
  const primary = (l) => !!(l.fromDeveloper || l.saleType === 'fz214' || l.saleType === 'dupt' || l.houseFinished === false || l.x.nbId);
  return {
    lots: n,
    requestsPer1000: n ? +((1000 * (head.requests || 0)) / n).toFixed(1) : null,
    leaders: leaders.length,
    hiddenBehindLeaders: hidden,
    leadersPrimary: leaders.filter(primary).length,
    primary: lots.filter(primary).length,
    apartments: lots.filter((l) => l.isApartments).length,
    withHouseId: lots.filter((l) => l.houseId).length,
    withCadastral: lots.filter((l) => l.x.cad).length,
    withBuildingCadastral: lots.filter((l) => l.x.bcad).length,
    withMinhash: lots.filter((l) => l.x.minhash).length,
    dupDescription: lots.filter((l) => l.x.dupDesc).length,
    demolished: lots.filter((l) => l.x.demolished).length,
    withBuildYear: lots.filter((l) => l.buildYear).length,
    withCoords: lots.filter((l) => l.lat && l.lng).length,
    classType: tally(lots.map((l) => l.x.classType)),
    saleType: tally(lots.map((l) => l.saleType)),
    sellerType: tally(lots.map((l) => l.sellerType)),
    flatType: tally(lots.map((l) => l.flatType)),
    photosMedian: median(lots.map((l) => l.photosCount)),
    priceMin: Math.min(...lots.map((l) => l.priceRub || Infinity)),
    priceMax: Math.max(...lots.map((l) => l.priceRub || 0)),
  };
}

function printSummary(h) {
  const s = h.summary;
  log(`\nзаявлено ${h.count}, перечислимо ${h.aggregated}; прочитано ${s.lots} лотов за ${h.pages} стр. (${h.requests} запросов, ${s.requestsPer1000} на 1000 лотов); повторов между страницами ${h.dupAcrossPages}`);
  log(`лидеров групп ${s.leaders} (из них первичка ${s.leadersPrimary}), за ними спрятано ${s.hiddenBehindLeaders}`);
  log(`первичка ${pct(s.primary, s.lots)}, апартаменты ${s.apartments}; номер дома ${pct(s.withHouseId, s.lots)}, год ${pct(s.withBuildYear, s.lots)}, координаты ${pct(s.withCoords, s.lots)}`);
  log(`кадастровый номер квартиры ${pct(s.withCadastral, s.lots)}, дома ${pct(s.withBuildingCadastral, s.lots)}; minhash текста ${pct(s.withMinhash, s.lots)}, «текст повторяется» ${s.dupDescription}; реновация ${s.demolished}`);
  log(`classType ${JSON.stringify(s.classType)}; saleType ${JSON.stringify(s.saleType)}; продавцы ${JSON.stringify(s.sellerType)}`);
}

/* Группы похожих: сколько обещано на лидере и сколько приходит по
   multi_id, и кто в группе — сток застройщика или разные квартиры вторички. */
async function groups(api, a, dir) {
  const name = a.name || 'slice';
  const head = JSON.parse(fs.readFileSync(path.join(dir, `read-${name}.json`), 'utf8'));
  const lots = readJsonl(path.join(dir, `read-${name}.jsonl`));
  const n = parseInt(a.n || '12', 10);
  const maxPages = parseInt(a.pages || '2', 10);
  const leaders = lots.filter((l) => (l.similarCount || 0) > 0).sort((x, y) => y.similarCount - x.similarCount);
  const big = leaders.slice(0, Math.ceil(n / 2));
  const rest = leaders.slice(big.length);
  const step = Math.max(1, Math.floor(rest.length / Math.max(1, n - big.length)));
  const pick = [...big, ...rest.filter((_, i) => i % step === 0).slice(0, n - big.length)];
  const base = { ...head.query };
  delete base.sort;
  const out = [];
  const file = path.join(dir, `groups-${name}.json`);
  try {
    for (const l of pick) {
      const q = { ...base, multi_id: term(l.id) };
      const members = [];
      let pages = 0;
      for (let p = 1; p <= maxPages; p++) {
        const r = await api.search(q, p, 'group');
        if (!r.offers.length) break;
        pages = p;
        for (const o of r.offers) members.push(record(o));
        if (members.length >= (l.similarCount || 0) + 1) break;
      }
      const others = members.filter((m) => m.id !== l.id);
      const g = {
        leader: l.id, promised: l.similarCount, got: others.length, pages, leaderIncluded: members.some((m) => m.id === l.id),
        sameNb: others.filter((m) => m.x.nbId && m.x.nbId === l.x.nbId).length,
        sameHouse: others.filter((m) => m.houseId && m.houseId === l.houseId).length,
        noHouse: others.filter((m) => !m.houseId).length,
        fromDeveloper: others.filter((m) => m.fromDeveloper).length,
        floors: [...new Set(others.map((m) => m.floor))].length,
        sameFingerprint: others.filter((m) => m.fingerprint && m.fingerprint === l.fingerprint).length,
        saleType: tally(others.map((m) => m.saleType)),
        leaderSaleType: l.saleType, leaderComplex: l.complex, leaderSeller: l.sellerType,
      };
      out.push(g);
      log(`${l.id}: обещано ${g.promised}, пришло ${g.got} (${pages} стр.); тот же ЖК ${g.sameNb}, тот же дом ${g.sameHouse}, без дома ${g.noHouse}, от застройщика ${g.fromDeveloper}, этажей ${g.floors}, та же квартира ${g.sameFingerprint} — ${l.complex || l.street || ''}`);
    }
  } finally {
    fs.writeFileSync(file, JSON.stringify({ at: new Date().toISOString(), groups: out }, null, 1));
  }
}

/* Кадры: какие варианты размера отдаёт CDN и сколько они весят. */
async function photos(api, a, dir) {
  const name = a.name || 'slice';
  const lots = readJsonl(path.join(dir, `read-${name}.jsonl`)).filter((l) => l.x.photo);
  const n = parseInt(a.n || '3', 10);
  const out = [];
  const pickLots = lots.filter((_, i) => i % Math.max(1, Math.floor(lots.length / n)) === 0).slice(0, n);
  try {
    for (const [i, l] of pickLots.entries()) {
      const variants = i === 0 ? ['mini', 'thumb', 'thumb2', 'full'] : ['thumb'];
      for (const v of variants) {
        const url = l.x.photo[v];
        const r = await api.image(url);
        out.push({ id: l.id, variant: v, url, ...r });
        log(`${l.id} ${v.padEnd(6)} ${r.size ? `${r.size.w}×${r.size.h}` : '?'} ${(r.bytes / 1024).toFixed(0)} КБ ${r.type}`);
      }
    }
  } finally {
    fs.writeFileSync(path.join(dir, `photos-${name}.json`), JSON.stringify(out, null, 1));
  }
}

if (require.main === module) {
  main().then((code) => process.exit(code || 0), (e) => { log(e.stack || String(e)); process.exit(1); });
}

module.exports = { extra, summarizeLots, OUTSIDE, PARTLY, OLD_OKRUGS };
