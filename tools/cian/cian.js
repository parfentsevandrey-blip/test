#!/usr/bin/env node
/**
 * Клиент Циан поверх того же API, которым пользуется сама выдача.
 *
 *   node tools/cian/cian.js count  --query q.json
 *   node tools/cian/cian.js search --query q.json [--pages 3] [--out lots.json] [--no-apartments] [--min-year 2016]
 *   node tools/cian/cian.js probe  --query q.json --with '{"loggia":{"type":"term","value":true}}'
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

/* Частые подряд запросы иногда получают 5xx/429 — отступаем и повторяем. */
async function searchPage(ctx, jsonQuery, pageNumber, attempt = 1) {
  const res = await ctx.request.post(SEARCH_API, {
    headers: API_HEADERS,
    data: { jsonQuery: { ...jsonQuery, page: { type: 'term', value: pageNumber } } },
    timeout: 45000,
  });
  if (res.status() !== 200) {
    if (attempt <= 3) { await sleep(2000 * attempt); return searchPage(ctx, jsonQuery, pageNumber, attempt + 1); }
    throw new Error(`search API ${res.status()} на странице ${pageNumber}`);
  }
  const j = await res.json();
  const d = j.data || {};
  return { count: d.offerCount ?? null, offers: d.offersSerialized || d.offers || [] };
}

/* Плоская запись из «сырого» оффера: только то, по чему реально отбирают. */
function normalize(o) {
  const b = o.building || {};
  const addr = (o.geo && o.geo.address) || [];
  // Округ и район оба приходят с geoType="district", различаются полем type.
  const pick = (t) => (addr.find((a) => a.type === t) || {}).name || null;
  const und = ((o.geo && o.geo.undergrounds) || [])[0] || null;
  const created = o.creationDate ? o.creationDate.slice(0, 10) : null;
  return {
    id: o.cianId || o.id,
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
    title: o.title || null,
  };
}

async function collect(ctx, jsonQuery, maxPages) {
  const seen = new Map();
  let count = null;
  for (let p = 1; p <= maxPages; p++) {
    const { count: c, offers } = await searchPage(ctx, jsonQuery, p);
    if (count === null) count = c;
    if (!offers.length) break;
    offers.forEach((o) => { const n = normalize(o); if (!seen.has(n.id)) seen.set(n.id, n); });
    log(`  страница ${p}: +${offers.length} (всего собрано ${seen.size} из ${count})`);
    if (seen.size >= (count || 0)) break;
    await sleep(1200);
  }
  return { count, lots: [...seen.values()] };
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

    } else if (cmd === 'probe') {
      // Подбор имени фильтра: если счётчик не сдвинулся — Циан молча его проигнорировал.
      const q = loadQuery(a.query);
      const base = (await searchPage(ctx, q, 1)).count;
      log(`база: ${base}`);
      const extra = JSON.parse(a.with);
      for (const [k, v] of Object.entries(extra)) {
        const c = (await searchPage(ctx, { ...q, [k]: v }, 1)).count;
        log(`  ${k} = ${JSON.stringify(v)} -> ${c}` + (c === base ? '   (не применился)' : ''));
      }

    } else if (cmd === 'search') {
      const q = loadQuery(a.query);
      const pages = parseInt(a.pages || '3', 10);
      let { count, lots } = await collect(ctx, q, pages);
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
      const res = { fetched: new Date().toISOString().slice(0, 10), count, jsonQuery: q, lots };
      if (a.out) { fs.writeFileSync(a.out, JSON.stringify(res, null, 2) + '\n'); log(`-> ${a.out}`); }
      else log(JSON.stringify(res, null, 2));

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
