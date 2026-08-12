#!/usr/bin/env node
/**
 * Клиент Циан поверх того же API, которым пользуется сама выдача.
 *
 *   node tools/cian/cian.js count  --query q.json
 *   node tools/cian/cian.js search --query q.json [--pages 3] [--all] [--out lots.json] [--no-apartments] [--min-year 2016]
 *   node tools/cian/cian.js url    --query q.json      — каноническая ссылка cat.php
 *   node tools/cian/cian.js probe  --query q.json --with '{"loggia":{"type":"term","value":true}}'
 *   node tools/cian/cian.js sweep  --query q.json [--limit 250] [--dedupe] — большой ЖК целиком
 *   node tools/cian/cian.js verify --query q.json [--ids 1,2] [--photos 6] [--dir out]
 *   node tools/cian/cian.js snapshot --query q.json   — записать выдачу в архив
 *   node tools/cian/cian.js exposure --query q.json [--deep] — двойники и реальный срок
 *   node tools/cian/cian.js stats  332550701 331961171
 *   node tools/cian/cian.js geo    [--out moscow-geo.json]
 *
 * --query принимает путь к JSON-файлу с jsonQuery или сам JSON строкой.
 * Справочник ключей и значений — docs/cian/filters.json и docs/cian/jsonquery.md.
 */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const fs = require('fs');

const SEARCH_API = 'https://api.cian.ru/search-offers/v2/search-offers-desktop/';
const GEO_API = 'https://www.cian.ru/api/geo/get-districts-tree/?locationId=1';
const CHROME = process.env.CIAN_CHROME || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36';

const log = (...a) => process.stdout.write(a.join(' ') + '\n');

/* Через MITM-прокси Chromium с TLS 1.3 получает ERR_CONNECTION_RESET,
   поэтому потолок протокола опускаем до 1.2. */
async function open() {
  const browser = await chromium.launch({
    executablePath: CHROME,
    headless: true,
    args: ['--no-sandbox', '--ssl-version-max=tls1.2', '--disable-blink-features=AutomationControlled'],
    proxy: process.env.HTTPS_PROXY || process.env.https_proxy
      ? { server: process.env.HTTPS_PROXY || process.env.https_proxy }
      : undefined,
  });
  const ctx = await browser.newContext({
    locale: 'ru-RU', timezoneId: 'Europe/Moscow', viewport: { width: 1440, height: 950 },
    userAgent: UA, ignoreHTTPSErrors: true,
  });
  const page = await ctx.newPage();
  // Прогрев: без куки cian.ru отдаёт капчу.
  await page.goto('https://www.cian.ru/', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2500);
  return { browser, ctx, page };
}

const API_HEADERS = {
  'content-type': 'application/json',
  referer: 'https://www.cian.ru/cat.php?deal_type=sale&engine_version=2&offer_type=flat&region=1',
  'user-agent': UA,
  'accept-language': 'ru-RU,ru;q=0.9',
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* Частые подряд запросы получают то 5xx, то обрыв соединения — отступаем
   и повторяем в обоих случаях. */
async function searchPage(ctx, jsonQuery, pageNumber, attempt = 1) {
  const retry = async (why) => {
    if (attempt > 3) throw new Error(`search API: ${why} (страница ${pageNumber})`);
    await sleep(2500 * attempt);
    return searchPage(ctx, jsonQuery, pageNumber, attempt + 1);
  };
  let res;
  try {
    res = await ctx.request.post(SEARCH_API, {
      headers: API_HEADERS,
      data: { jsonQuery: { ...jsonQuery, page: { type: 'term', value: pageNumber } } },
      timeout: 45000,
    });
  } catch (e) {
    return retry(e.message.split('\n')[0]);
  }
  if (res.status() !== 200) return retry(`http ${res.status()}`);
  const j = await res.json();
  const d = j.data || {};
  return {
    count: d.offerCount ?? null,
    offers: d.offersSerialized || d.offers || [],
    // Циан сам сериализует jsonQuery обратно в query-строку cat.php — по ней
    // видно, какие фильтры он принял, и как они называются в адресе.
    queryString: d.queryString || null,
    fullUrl: d.fullUrl || null,
  };
}

/* Плоская запись из «сырого» оффера: только то, по чему реально отбирают. */
function normalize(o) {
  const b = o.building || {};
  const addr = (o.geo && o.geo.address) || [];
  // Округ и район оба приходят с geoType="district", различаются полем type.
  const pick = (t) => (addr.find((a) => a.type === t) || {}).name || null;
  const und = ((o.geo && o.geo.undergrounds) || [])[0] || null;
  const created = o.creationDate ? o.creationDate.slice(0, 10) : null;
  const houseId = (addr.find((a) => a.type === 'house') || {}).id || null;
  const area = o.totalArea ? parseFloat(o.totalArea) : null;
  return {
    id: o.cianId || o.id,
    /* Отпечаток физической квартиры: переживает переразмещение объявления,
       потому что новый id получает объявление, а не сама квартира. */
    fingerprint: houseId && area != null && o.floorNumber != null
      ? `${houseId}|${o.floorNumber}|${area.toFixed(1)}|${o.roomsCount}` : null,
    houseId,
    url: (o.fullUrl || '').split('?')[0],
    rooms: o.roomsCount ?? null,
    isApartments: !!o.isApartments,
    totalArea: o.totalArea ? parseFloat(o.totalArea) : null,
    livingArea: o.livingArea ? parseFloat(o.livingArea) : null,
    kitchenArea: o.kitchenArea ? parseFloat(o.kitchenArea) : null,
    floor: o.floorNumber ?? null,
    floors: b.floorsCount ?? null,
    buildYear: b.buildYear ?? null,
    material: b.materialType ?? null,
    priceRub: (o.bargainTerms && o.bargainTerms.priceRur) ?? null,
    okrug: pick('okrug'),
    district: pick('raion'),
    street: pick('street'),
    house: pick('house'),
    metro: und ? { name: und.name, minutes: und.time ?? null, byFoot: und.transportType === 'walk' } : null,
    complex: (o.newbuilding && o.newbuilding.name) || null,
    created,
    daysOnMarket: created ? Math.round((Date.now() - Date.parse(created)) / 86400000) : null,
    // added — дата последнего поднятия, creationDate её переживает
    bumped: o.added || null,
    title: o.title || null,
    hasFurniture: o.hasFurniture ?? null,
    photosCount: (o.photos || []).length,
    photos: (o.photos || []).map((ph) => ph.fullUrl).filter(Boolean),
    description: o.description || '',
  };
}

/* Порядки сортировки дают частично разные срезы: выдача обрывается раньше
   заявленного offerCount, и «хвост» у каждой сортировки свой. */
const SORTS = [null, 'price_object_order', 'creation_date_desc', 'area_order'];

async function collectSorted(ctx, jsonQuery, maxPages, seen, sort) {
  let count = null;
  const q = sort ? { ...jsonQuery, sort: { type: 'term', value: sort } } : jsonQuery;
  for (let p = 1; p <= maxPages; p++) {
    const { count: c, offers } = await searchPage(ctx, q, p);
    if (count === null) count = c;
    if (!offers.length) break;
    const before = seen.size;
    offers.forEach((o) => { const n = normalize(o); if (!seen.has(n.id)) seen.set(n.id, n); });
    log(`  ${(sort || 'по умолчанию').padEnd(18)} стр ${p}: +${offers.length}, новых ${seen.size - before}, накоплено ${seen.size}`);
    await sleep(1200);
  }
  return count;
}

async function collect(ctx, jsonQuery, maxPages, all) {
  const seen = new Map();
  let count = null;
  for (const sort of all ? SORTS : [null]) {
    const c = await collectSorted(ctx, jsonQuery, maxPages, seen, sort);
    if (count === null) count = c;
    if (!all) break;
  }
  return { count, lots: [...seen.values()] };
}

/* ---------- развёртка большой выдачи ----------
   Пагинация обрывается, не добрав до offerCount: у ЖК «Остров» 974 из 1212 за
   44 страницы. Лечится дроблением на непересекающиеся подзапросы: каждый
   меньше потолка — значит перечисляется целиком. */
const DECORATIONS = ['fineWithFurniture', 'fine', 'preFine', 'without', 'rough'];
const ROOMS = [9, 1, 2, 3, 4, 5, 6];       // 9 — студия, у неё roomsCount пустой

async function splitByAxis(ctx, q, axis) {
  const out = [];
  const values = axis === 'decor' ? DECORATIONS : ROOMS;
  const key = axis === 'decor' ? 'decorations_list' : 'room';
  for (const v of values) {
    const sub = { ...q, [key]: { type: 'terms', value: [v] } };
    const { count } = await searchPage(ctx, sub, 1);
    if (count) out.push({ q: sub, count, label: `${key}=${v}` });
    await sleep(700);
  }
  return out;
}

async function splitByPrice(ctx, bucket, limit) {
  /* Делим пополам по цене, пока каждая половина не станет меньше потолка. */
  const out = [];
  const queue = [{ ...bucket, lo: 0, hi: 200e6 }];
  let guard = 0;
  while (queue.length && guard++ < 24) {
    const b = queue.shift();
    if (b.count <= limit || b.hi - b.lo < 2e6) { out.push(b); continue; }
    const mid = Math.round((b.lo + b.hi) / 2 / 1e5) * 1e5;
    for (const [lo, hi] of [[b.lo, mid], [mid, b.hi]]) {
      const sub = { ...b.q, price: { type: 'range', value: { gte: lo || undefined, lte: hi } } };
      const { count } = await searchPage(ctx, sub, 1);
      if (count) queue.push({ q: sub, count, lo, hi, label: `${b.label} ${(lo / 1e6).toFixed(0)}–${(hi / 1e6).toFixed(0)}млн` });
      await sleep(700);
    }
  }
  return out;
}

async function sweep(ctx, q, limit, maxPages) {
  const top = await searchPage(ctx, q, 1);
  log(`заявлено ${top.count}`);
  let buckets = [{ q, count: top.count, label: 'всё' }];
  for (const axis of ['decor', 'rooms']) {
    const next = [];
    for (const b of buckets) {
      if (b.count <= limit) { next.push(b); continue; }
      const parts = await splitByAxis(ctx, b.q, axis);
      log(`  ${b.label}: ${b.count} -> ${parts.map((x) => `${x.label}:${x.count}`).join(' ')}`);
      next.push(...parts.map((x) => ({ ...x, label: `${b.label} / ${x.label}` })));
    }
    buckets = next;
    if (buckets.every((b) => b.count <= limit)) break;
  }
  const heavy = buckets.filter((b) => b.count > limit);
  for (const b of heavy) {
    const parts = await splitByPrice(ctx, b, limit);
    log(`  ${b.label}: ${b.count} -> дроблю по цене на ${parts.length}`);
    buckets = buckets.filter((x) => x !== b).concat(parts);
  }
  log(`подзапросов: ${buckets.length}`);

  const seen = new Map();
  for (const b of buckets) {
    for (let p = 1; p <= maxPages; p++) {
      const { offers } = await searchPage(ctx, b.q, p);
      if (!offers.length) break;
      offers.forEach((o) => { const n = normalize(o); if (!seen.has(n.id)) seen.set(n.id, n); });
      await sleep(800);
    }
    log(`  ${b.label.padEnd(46)} ${String(b.count).padStart(5)} -> накоплено ${seen.size}`);
  }
  return { declared: top.count, lots: [...seen.values()] };
}

/* ---------- проверка заявленного ремонта ----------
   Галочка «дизайнерский» ставится продавцом и ничем не подтверждается.
   Текст объявления при этом почти всегда себя выдаёт: под ключ описывают
   мебелью и техникой, а белую коробку — отделкой и планировкой. */
const RED = [
  ['без отделки', 'прямо сказано «без отделки»'], ['предчистов', 'предчистовая отделка'],
  ['white ?box', 'white box'], ['вайтбокс', 'white box'], ['под чистовую', 'под чистовую'],
  ['черновая', 'черновая отделка'], ['требует ремонта', 'требует ремонта'],
  ['под ремонт', 'под ремонт'], ['голые стены', 'голые стены'], ['бетон', 'упомянут бетон'],
];
const GREEN = [
  ['мебел', 'мебель'], ['техник', 'техника'], ['встроен', 'встроенная мебель'],
  ['дизайн-проект', 'дизайн-проект'], ['авторск', 'авторский интерьер'],
  ['под ключ', 'под ключ'], ['гардеробн', 'гардеробная'], ['заезжай и живи', 'заезжай и живи'],
  ['miele|gaggenau|bosch|siemens|smeg|poliform|molteni|boffi|hansgrohe|grohe|villeroy|duravit|kettal|flos',
    'названы бренды мебели/техники'],
];
const YELLOW = [
  ['отделка от застройщика', 'отделка застройщика, а не дизайнерский ремонт'],
  ['чистовая отделка', 'чистовая отделка застройщика'],
  ['свободная планировк', 'свободная планировка'],
];

function assessRepair(lot) {
  const t = (lot.description || '').toLowerCase();
  const hit = (list) => list.filter(([re]) => new RegExp(re).test(t)).map(([, why]) => why);
  const red = hit(RED), yellow = hit(YELLOW), green = hit(GREEN);
  const flags = [];
  if (lot.hasFurniture === false) flags.push('поле hasFurniture=false');
  if (lot.hasFurniture == null) flags.push('поле hasFurniture не заполнено');
  if (lot.photosCount < 8) flags.push(`мало фото (${lot.photosCount})`);
  let verdict = 'похоже на правду';
  if (red.length) verdict = 'ПРОТИВОРЕЧИЕ';
  else if (!green.length || (lot.hasFurniture !== true && lot.photosCount < 10)) verdict = 'под вопросом';
  return { verdict, red, yellow, green, flags };
}

async function fetchPhotos(ctx, lot, dir, n) {
  fs.mkdirSync(dir, { recursive: true });
  const files = [];
  for (const [i, u] of lot.photos.slice(0, n).entries()) {
    try {
      const r = await ctx.request.get(u, { timeout: 30000 });
      const f = `${dir}/${String(i + 1).padStart(2, '0')}.jpg`;
      fs.writeFileSync(f, await r.body());
      files.push(f);
    } catch (e) { /* одна битая картинка не повод ронять проверку */ }
  }
  return files;
}

/* ---------- реальный срок экспозиции ----------
   creationDate обнуляется, если объявление снять и выложить заново. Отпечаток
   квартиры — нет. Архив хранит, когда мы увидели квартиру впервые, и это
   переразмещением не стирается. */
function loadArchive(p) {
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) { return { updated: null, flats: {} }; }
}

function mergeArchive(arc, lots, today) {
  let fresh = 0, updated = 0;
  for (const l of lots) {
    if (!l.fingerprint) continue;
    const e = arc.flats[l.fingerprint];
    if (!e) {
      arc.flats[l.fingerprint] = {
        firstSeen: today, lastSeen: today, address: `${l.street || ''}, ${l.house || ''}`.trim(),
        rooms: l.rooms, area: l.totalArea, floor: l.floor,
        listings: [{ id: l.id, created: l.created, price: l.priceRub, seen: today }],
      };
      fresh++;
    } else {
      e.lastSeen = today;
      if (!e.listings.some((x) => x.id === l.id)) {
        e.listings.push({ id: l.id, created: l.created, price: l.priceRub, seen: today });
      }
      updated++;
    }
  }
  arc.updated = today;
  return { fresh, updated };
}

/* Схлопывание объявлений в квартиры. В больших ЖК одну квартиру выставляют
   десятки субагентов по разной цене — считать их отдельными лотами бессмысленно,
   а переплата за такой же объект доходит до 20%. */
function dedupe(lots) {
  const byFp = new Map(), loose = [];
  for (const l of lots) {
    if (!l.fingerprint) { loose.push(l); continue; }
    (byFp.get(l.fingerprint) || byFp.set(l.fingerprint, []).get(l.fingerprint)).push(l);
  }
  const flats = [...byFp.values()].map((g) => {
    const priced = g.filter((x) => x.priceRub).sort((x, y) => x.priceRub - y.priceRub);
    const best = priced[0] || g[0], worst = priced[priced.length - 1] || g[0];
    return {
      ...best,
      listings: g.length,
      priceMin: best.priceRub, priceMax: worst.priceRub,
      overpay: best.priceRub && worst.priceRub !== best.priceRub
        ? +((worst.priceRub - best.priceRub) / best.priceRub * 100).toFixed(1) : 0,
      alsoListedAs: g.filter((x) => x.id !== best.id).map((x) => ({ id: x.id, price: x.priceRub })),
      earliestCreated: g.map((x) => x.created).filter(Boolean).sort()[0] || best.created,
    };
  });
  return { flats, loose };
}

/* Что видно уже сейчас, без накопленного архива: одна и та же квартира,
   выставленная несколькими объявлениями — разными агентами или заново. */
function findTwins(lots) {
  const byFp = new Map();
  lots.forEach((l) => { if (l.fingerprint) (byFp.get(l.fingerprint) || byFp.set(l.fingerprint, []).get(l.fingerprint)).push(l); });
  return [...byFp.values()].filter((g) => g.length > 1);
}

/* Просмотры живут только в отрисованной карточке: кнопка статистики,
   по клику — «N просмотров с даты создания объявления DD.MM.YYYY». */
async function stats(page, id) {
  await page.goto(`https://www.cian.ru/sale/flat/${id}/`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(4000);
  await page.evaluate(() => document.querySelector('[data-name="OfferStats"]')?.click());
  await page.waitForTimeout(1800);
  return page.evaluate(() => {
    const html = document.documentElement.innerHTML;
    const i = html.indexOf('с даты создания объявления');
    const around = i === -1 ? '' : html.slice(Math.max(0, i - 200), i + 220).replace(/<[^>]+>/g, ' ');
    const m = around.match(/([\d\s ]+)\s*просмотр\S*\s*с даты создания объявления\s*(\d{2}\.\d{2}\.\d{4})/);
    const short = (document.querySelector('[data-name="OfferStats"]') || {}).innerText || null;
    return { views: m ? parseInt(m[1].replace(/\D/g, ''), 10) : null, created: m ? m[2] : null, line: short };
  });
}

/* ---------- CLI ---------- */
function args(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) {
      const k = argv[i].slice(2);
      const v = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : true;
      out[k] = v;
    } else out._.push(argv[i]);
  }
  return out;
}
const loadQuery = (v) => {
  if (!v || v === true) throw new Error('нужен --query <файл.json|json-строка>');
  return JSON.parse(fs.existsSync(v) ? fs.readFileSync(v, 'utf8') : v);
};

(async () => {
  const a = args(process.argv.slice(2));
  const cmd = a._[0];
  if (!cmd || a.help) { log(fs.readFileSync(__filename, 'utf8').split('*/')[0]); process.exit(0); }

  const { browser, ctx, page } = await open();
  try {
    if (cmd === 'geo') {
      const txt = await page.evaluate(async (u) => (await fetch(u, { credentials: 'include' })).text(), GEO_API);
      const tree = JSON.parse(txt);
      const out = {
        source: GEO_API, fetched: new Date().toISOString().slice(0, 10), regionId: 1,
        okrugCount: tree.length, raionCount: tree.reduce((n, o) => n + o.childs.length, 0),
        okrugs: tree.map((o) => ({ id: o.id, name: o.name, raions: o.childs.map((r) => ({ id: r.id, name: r.name })) })),
      };
      if (a.out) fs.writeFileSync(a.out, JSON.stringify(out, null, 2) + '\n');
      log(`${out.okrugCount} округов, ${out.raionCount} районов` + (a.out ? ` -> ${a.out}` : ''));
      log(out.okrugs.map((o) => `${String(o.id).padStart(4)}  ${o.name}`).join('\n'));

    } else if (cmd === 'count') {
      const { count } = await searchPage(ctx, loadQuery(a.query), 1);
      log(String(count));

    } else if (cmd === 'url') {
      // Каноническая ссылка cat.php для jsonQuery — её можно открыть в браузере.
      const r = await searchPage(ctx, loadQuery(a.query), 1);
      log(r.fullUrl || `https://www.cian.ru/cat.php?${r.queryString}`);

    } else if (cmd === 'probe') {
      /* Признак «фильтр принят» — появление ключа в канонической query-строке,
         а не изменение счётчика: фильтр может быть применён и не отсечь ничего. */
      const q = loadQuery(a.query);
      const b0 = await searchPage(ctx, q, 1);
      const baseKeys = new Set(decodeURIComponent(b0.queryString || '').split('&').map((x) => x.split('=')[0]));
      log(`база: ${b0.count}`);
      for (const [k, v] of Object.entries(JSON.parse(a.with))) {
        await sleep(1100);
        const r = await searchPage(ctx, { ...q, [k]: v }, 1);
        const added = decodeURIComponent(r.queryString || '').split('&')
          .filter((x) => !baseKeys.has(x.split('=')[0]));
        log(`  ${k} = ${JSON.stringify(v)}`);
        log(`      count ${b0.count} -> ${r.count}` +
            (added.length ? `, принят как ${added.join(' ')}` : ', НЕ ПРИНЯТ (в query-строку не попал)'));
      }

    } else if (cmd === 'search') {
      const q = loadQuery(a.query);
      const pages = parseInt(a.pages || '3', 10);
      let { count, lots } = await collect(ctx, q, pages, !!a.all);
      const enumerated = lots.length;
      log(`Циан заявляет ${count}, реально перечислено ${enumerated}` +
          (count && enumerated < count ? ' — offerCount у Циан завышен, это оценка, а не точное число' : ''));
      const before = lots.length;
      if (a['no-apartments']) lots = lots.filter((l) => !l.isApartments);
      if (a['min-year']) lots = lots.filter((l) => l.buildYear && l.buildYear >= parseInt(a['min-year'], 10));
      if (a['max-area']) lots = lots.filter((l) => l.totalArea && l.totalArea <= parseFloat(a['max-area']));
      if (lots.length !== before) log(`после доотбора на своей стороне: ${lots.length} из ${before}`);
      if (a.stats) {
        for (const l of lots.slice(0, parseInt(a.stats, 10) || 5)) {
          Object.assign(l, await stats(page, l.id));
          await page.waitForTimeout(2000);
        }
      }
      const res = { fetched: new Date().toISOString().slice(0, 10), declaredCount: count, enumerated, kept: lots.length, jsonQuery: q, lots };
      if (a.out) { fs.writeFileSync(a.out, JSON.stringify(res, null, 2) + '\n'); log(`-> ${a.out}`); }
      else log(JSON.stringify(res, null, 2));

    } else if (cmd === 'sweep') {
      const q = loadQuery(a.query);
      const { declared, lots } = await sweep(ctx, q, parseInt(a.limit || '250', 10), parseInt(a.pages || '12', 10));
      log(`\nзаявлено ${declared}, собрано ${lots.length} (${Math.round(lots.length / declared * 100)}%)`);
      const { flats, loose } = dedupe(lots);
      const multi = flats.filter((f) => f.listings > 1).sort((x, y) => y.listings - x.listings);
      log(`объявлений ${lots.length} -> квартир ${flats.length}` +
          (loose.length ? ` (+${loose.length} без корпуса в адресе, схлопнуть нельзя)` : ''));
      log(`квартир, выставленных больше одного раза: ${multi.length}, лишних объявлений ${lots.length - flats.length - loose.length}`);
      multi.slice(0, 5).forEach((f) => log(
        `  ${f.street}, ${f.house}, эт.${f.floor}, ${f.totalArea} м² — ${f.listings} объявл., ` +
        `${(f.priceMin || 0).toLocaleString('ru-RU')}–${(f.priceMax || 0).toLocaleString('ru-RU')} ₽` +
        (f.overpay ? `, переплата за верхнее ${f.overpay}%` : '')));
      const out = a.dedupe ? flats.concat(loose) : lots;
      if (a.out) { fs.writeFileSync(a.out, JSON.stringify({ declared, collected: lots.length, flats: flats.length, lots: out }, null, 2) + '\n'); log(`-> ${a.out}`); }

    } else if (cmd === 'verify') {
      const q = loadQuery(a.query);
      const only = a.ids ? String(a.ids).split(',').map(Number) : null;
      let { lots } = await collect(ctx, q, parseInt(a.pages || '2', 10), false);
      if (only) lots = lots.filter((l) => only.includes(l.id));
      lots = lots.slice(0, parseInt(a.limit || '8', 10));
      const dir = a.dir || 'cian-photos';
      const report = [];
      for (const l of lots) {
        const r = assessRepair(l);
        const files = a.photos === false || a.photos === '0' ? []
          : await fetchPhotos(ctx, l, `${dir}/${l.id}`, parseInt(a.photos || '6', 10));
        report.push({ id: l.id, url: l.url, price: l.priceRub, area: l.totalArea, ...r, photos: files });
        log(`\n${l.id}  ${l.rooms}к ${l.totalArea} м²  ${(l.priceRub || 0).toLocaleString('ru-RU')} ₽  — ${r.verdict}`);
        if (r.red.length) log(`   против: ${r.red.join('; ')}`);
        if (r.yellow.length) log(`   насторожило: ${r.yellow.join('; ')}`);
        if (r.green.length) log(`   за: ${r.green.join('; ')}`);
        if (r.flags.length) log(`   поля: ${r.flags.join('; ')}`);
        if (files.length) log(`   фото: ${files.length} шт. в ${dir}/${l.id}/ — посмотреть глазами`);
      }
      if (a.out) fs.writeFileSync(a.out, JSON.stringify(report, null, 2) + '\n');
      log('\nТекст и поля — только предварительный отсев. Окончательный ответ дают фотографии.');

    } else if (cmd === 'snapshot' || cmd === 'exposure') {
      const q = loadQuery(a.query);
      const today = new Date().toISOString().slice(0, 10);
      const archivePath = a.archive || 'docs/cian/archive.json';
      const arc = loadArchive(archivePath);
      const { lots } = await collect(ctx, q, parseInt(a.pages || '3', 10), !!a.all);

      if (cmd === 'snapshot') {
        const { fresh, updated } = mergeArchive(arc, lots, today);
        fs.writeFileSync(archivePath, JSON.stringify(arc, null, 2) + '\n');
        log(`архив ${archivePath}: +${fresh} новых квартир, ${updated} подтверждено, всего ${Object.keys(arc.flats).length}`);
        log('Чем дольше архив ведётся, тем труднее скрыть настоящий срок экспозиции.');
      } else {
        /* --deep: добираем близнецов запросом по дому. Второе объявление той же
           квартиры часто не проходит фильтры поиска (другая цена, другой ремонт),
           и внутри выдачи его не видно. */
        if (a.deep) {
          const houses = [...new Set(lots.map((l) => l.houseId).filter(Boolean))]
            .slice(0, parseInt(a.deep === true ? '12' : a.deep, 10));
          log(`\nдобираю объявления по домам (${houses.length})…`);
          for (const h of houses) {
            try {
              const { offers } = await searchPage(ctx, {
                _type: 'flatsale', engine_version: { type: 'term', value: 2 },
                region: q.region || { type: 'terms', value: [1] },
                geo: { type: 'geo', value: [{ type: 'house', id: h }] },
              }, 1);
              offers.map(normalize).forEach((n) => { if (!lots.some((l) => l.id === n.id)) lots.push(n); });
            } catch (e) { /* дом мог не отдаться — не повод рушить разбор */ }
            await sleep(1000);
          }
        }

        const twins = findTwins(lots);
        log(`\nдвойники (одна квартира — несколько объявлений): ${twins.length}`);
        twins.forEach((g) => {
          const sorted = [...g].sort((x, y) => (x.created || '').localeCompare(y.created || ''));
          const oldest = sorted[0], newest = sorted[sorted.length - 1];
          const real = oldest.daysOnMarket;
          log(`\n  ${g[0].street}, ${g[0].house} — ${g[0].rooms}к ${g[0].totalArea} м², эт.${g[0].floor}`);
          sorted.forEach((x) => log(`      ${x.id}  создано ${x.created}  ${String(x.daysOnMarket).padStart(4)} дн  ` +
            `${(x.priceRub || 0).toLocaleString('ru-RU')} ₽`));
          log(`      реальная экспозиция не меньше ${real} дн (младшее объявление показывает ${newest.daysOnMarket})`);
          if (oldest.priceRub && newest.priceRub && oldest.priceRub !== newest.priceRub) {
            const d = newest.priceRub - oldest.priceRub;
            log(`      цена за это время ${d < 0 ? 'снижена' : 'поднята'} на ${Math.abs(d).toLocaleString('ru-RU')} ₽`);
          }
        });
        log('\nсрок экспозиции: заявленный против архивного');
        let corrected = 0;
        for (const l of lots) {
          const e = l.fingerprint && arc.flats[l.fingerprint];
          if (!e) continue;
          const real = Math.round((Date.parse(today) - Date.parse(e.firstSeen)) / 86400000);
          if (real > (l.daysOnMarket || 0) + 1) {
            corrected++;
            log(`  ${l.id}  заявлено ${l.daysOnMarket} дн, в архиве с ${e.firstSeen} — не меньше ${real} дн` +
                (e.listings.length > 1 ? `, объявлений за это время: ${e.listings.length}` : ''));
          }
        }
        if (!Object.keys(arc.flats).length) log('  архив пуст — сначала наберите снимки командой snapshot');
        else if (!corrected) log('  расхождений с архивом нет');
      }

    } else if (cmd === 'stats') {
      for (const id of a._.slice(1)) {
        const s = await stats(page, id);
        log(`${id}  ${s.views} просмотров с ${s.created}  (${s.line})`);
        await page.waitForTimeout(2000);
      }
    } else {
      log(`неизвестная команда: ${cmd}`);
    }
  } finally {
    await browser.close();
  }
})();
