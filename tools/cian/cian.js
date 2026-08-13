#!/usr/bin/env node
/**
 * Клиент Циан поверх того же API, которым пользуется сама выдача.
 *
 *   node tools/cian/cian.js find   Остров              — id ЖК, метро, района по названию
 *   node tools/cian/cian.js count  --query q.json
 *   node tools/cian/cian.js search --query q.json [--pages 3] [--all] [--out lots.json] [--no-apartments] [--min-year 2016]
 *   node tools/cian/cian.js url    --query q.json      — каноническая ссылка cat.php
 *   node tools/cian/cian.js probe  --query q.json --with '{"loggia":{"type":"term","value":true}}'
 *   node tools/cian/cian.js sweep  --query q.json [--limit 250] [--dedupe] [--resolve 0] — большой ЖК целиком
 *   node tools/cian/cian.js verify --query q.json [--ids 1,2] [--photos 9] [--files] [--dir out]
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
    decoration: o.decoration ?? null,
    /* Поля, которые API отдаёт почти всегда, а прежняя запись выбрасывала.
       Каждое меняет смысл сравнения: переуступка — не то же, что ДДУ;
       субагент накидывает поверх цены застройщика; студия — не «0 комнат». */
    flatType: o.flatType || null,                                   // rooms / studio / openPlan
    saleType: (o.bargainTerms && o.bargainTerms.saleType) || null,  // free / fz214 / dupt
    /* По ДДУ покупается не квартира, а обязательство её построить. Срок сдачи
       и признак готовности корпуса решают не меньше, чем площадь и цена. */
    deadline: (() => {
      const d = b.deadline, h = ((o.newbuilding || {}).house || {});
      const y = (d && d.year) || (h.finishDate && h.finishDate.year) || null;
      if (!y) return null;
      return { year: y, quarter: (d && d.quarter) || (h.finishDate && h.finishDate.quarter) || null };
    })(),
    houseFinished: (() => {
      const h = ((o.newbuilding || {}).house || {});
      if (typeof h.isFinished === 'boolean') return h.isFinished;
      if (b.deadline && typeof b.deadline.isComplete === 'boolean') return b.deadline.isComplete;
      return null;
    })(),
    mortgageAllowed: (o.bargainTerms && o.bargainTerms.mortgageAllowed) ?? null,
    sellerType: (o.user && o.user.userType) || null,
    isSubAgent: (o.user && o.user.isSubAgent) ?? null,
    fromDeveloper: o.fromDeveloper ?? null,
    parkingType: ((b.parking || {}).type) || null,
    promoted: !!(o.isTop3 || o.isPremium),
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

/* Ось дробления обязана СУЖАТЬ запрос. Если ключ уже задан в исходном
   запросе, берём пересечение, а не затираем: иначе поиск «3 комнаты» после
   дробления по комнатности уходит искать студии и однушки, и на выходе
   оказывается больше объявлений, чем было заявлено. */
async function splitByAxis(ctx, q, axis, parentCount) {
  const key = axis === 'decor' ? 'decorations_list' : 'room';
  const axisValues = axis === 'decor' ? DECORATIONS : ROOMS;
  const already = q[key] && Array.isArray(q[key].value) ? q[key].value : null;
  const values = already ? axisValues.filter((v) => already.includes(v)) : axisValues;
  if (already && values.length <= 1) return [];          // делить нечего
  const out = [];
  for (const v of values) {
    const sub = { ...q, [key]: { type: 'terms', value: [v] } };
    const { count } = await searchPage(ctx, sub, 1);
    await sleep(700);
    if (!count) continue;
    if (parentCount != null && count > parentCount) {     // страховка от расширения
      log(`  ! ${key}=${v} даёт ${count} при родительских ${parentCount} — ось отброшена`);
      return [];
    }
    out.push({ q: sub, count, label: `${key}=${v}` });
  }
  return out;
}

async function splitByPrice(ctx, bucket, limit) {
  /* Делим пополам по цене, пока каждая половина не станет меньше потолка.
     Границы берём из самого запроса: свои поставить — значит снять
     пользовательский потолок цены и притащить лоты дороже заказанного. */
  const base = (bucket.q.price && bucket.q.price.value) || {};
  const LO = base.gte || 0, HI = base.lte || 200e6;
  const out = [];
  const queue = [{ ...bucket, lo: LO, hi: HI }];
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
      const parts = await splitByAxis(ctx, b.q, axis, b.count);
      if (!parts.length) { next.push(b); continue; }     // ось не подошла — кусок несём дальше как есть
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
    // из какой ветки отделки пришёл лот — это и есть надёжная метка комплектности
    const dv = b.q.decorations_list && b.q.decorations_list.value[0];
    for (let p = 1; p <= maxPages; p++) {
      const { offers } = await searchPage(ctx, b.q, p);
      if (!offers.length) break;
      offers.forEach((o) => {
        const n = normalize(o);
        if (dv) n.decorFilter = dv;
        if (!seen.has(n.id)) seen.set(n.id, n);
        else if (dv && !seen.get(n.id).decorFilter) seen.get(n.id).decorFilter = dv;
      });
      await sleep(800);
    }
    log(`  ${b.label.padEnd(46)} ${String(b.count).padStart(5)} -> накоплено ${seen.size}`);
  }
  return { seen, declared: top.count };
}

/* Фильтр decorations_list делит выдачу без остатка, поэтому прогон по каждому
   его значению доопределяет комплектность там, где поля пустые. */
async function resolveDecoration(ctx, q, seen, maxPages) {
  const unknown = () => [...seen.values()].filter((l) => !l.decorFilter).length;
  log(`доопределяю отделку фильтром (без метки: ${unknown()})`);
  let base = null;
  try { base = (await searchPage(ctx, q, 1)).count; } catch (e) { /* сверять будет не с чем */ }
  for (const v of DECORATIONS) {
    const sub = { ...q, decorations_list: { type: 'terms', value: [v] } };
    let n = 0;
    for (let p = 1; p <= maxPages; p++) {
      let r; try { r = await searchPage(ctx, sub, p); } catch (e) { break; }
      /* На вторичке фильтр отделки — пустышка: он возвращает ту же выдачу
         целиком. Метить по нему нельзя, иначе всё подряд станет «оболочкой». */
      if (p === 1 && base && r.count === base) { log(`  ${v}: фильтр не сузил выдачу — метка не ставится`); break; }
      if (!r.offers.length) break;
      r.offers.forEach((o) => {
        const id = o.cianId || o.id;
        const l = seen.get(id);
        if (l && !l.decorFilter) { l.decorFilter = v; n++; }
      });
      await sleep(800);
    }
    if (n) log(`  ${v}: помечено ${n}`);
  }
  log(`осталось без метки: ${unknown()}`);
}

function finishSweep(seen, top) {
  if (seen.size > top.count * 1.05) {
    log(`\n! собрано ${seen.size} при заявленных ${top.count}: дробление расширило запрос, результату верить нельзя`);
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

/* Слова, после которых упоминание белой коробки говорит о прошлом квартиры,
   а не о её нынешнем состоянии: «куплена в состоянии white box, сделан ремонт». */
const PAST = /(был|была|было|куплен|приобрет|из состояния|после|вместо|до ремонта|сдавалась|передан|передела|перестро|демонтир|заменен|заменён|убран)\S*\s+(\S+\s+){0,4}$/i;

function assessRepair(lot) {
  const t = (lot.description || '').toLowerCase();
  /* RED засчитывается, только если перед ним нет разговора о прошлом. */
  const red = RED.filter(([re, ]) => {
    const m = new RegExp(re).exec(t);
    return m && !PAST.test(t.slice(Math.max(0, m.index - 60), m.index));
  }).map(([, why]) => why);
  const redPast = RED.filter(([re]) => new RegExp(re).test(t)).length - red.length;
  const yellow = YELLOW.filter(([re]) => new RegExp(re).test(t)).map(([, why]) => why);
  const green = GREEN.filter(([re]) => new RegExp(re).test(t)).map(([, why]) => why);

  const flags = [];
  if (redPast) flags.push(`${redPast} упоминание о прошлом состоянии — не в счёт`);
  if (lot.photosCount < 8) flags.push(`мало фото (${lot.photosCount})`);

  /* Проверено вручную: hasFurniture=false ловил настоящую подмену (голая
     отделка застройщика под видом дизайнерского ремонта), а пустое поле
     давало один шум — оно не заполнено у большинства объявлений. */
  /* Текстовый признак сам по себе слабый: на проверке единственное найденное
     «противоречие» оказалось рассказом о переделанной белой коробке. Поэтому
     твёрдый вердикт даёт только структурное поле, а слово против богатого
     описания понижается до «под вопросом». */
  let verdict = 'похоже на правду';
  if (lot.hasFurniture === false) { verdict = 'ПРОТИВОРЕЧИЕ'; red.unshift('поле hasFurniture=false при заявленном ремонте под ключ'); }
  else if (red.length) verdict = green.length >= 2 ? 'под вопросом' : 'ПРОТИВОРЕЧИЕ';
  else if (!green.length && lot.hasFurniture !== true) verdict = 'под вопросом';
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

/* Контактный лист: все фотографии лота одним изображением. Смотреть шесть
   файлов на объявление не масштабируется — на тридцати лотах это почти две
   сотни открытий. Сборка идёт в браузере: он и декодирует JPEG, и рисует.
   Картинки подаются как data:-URL, иначе холст «портится» чужим origin
   и toDataURL запрещён. */
async function contactSheet(ctx, page, lot, file, n, cols = 3) {
  const imgs = [];
  for (const u of lot.photos.slice(0, n)) {
    try {
      const r = await ctx.request.get(u, { timeout: 30000 });
      imgs.push('data:image/jpeg;base64,' + (await r.body()).toString('base64'));
    } catch (e) { /* пропускаем битую */ }
  }
  if (!imgs.length) return null;
  const dataUrl = await page.evaluate(async ({ imgs, cols, caption }) => {
    const CELL = 420, PAD = 6, HEAD = 34;
    const rows = Math.ceil(imgs.length / cols);
    const cv = document.createElement('canvas');
    cv.width = cols * CELL + (cols + 1) * PAD;
    cv.height = HEAD + rows * CELL + (rows + 1) * PAD;
    const g = cv.getContext('2d');
    g.fillStyle = '#111'; g.fillRect(0, 0, cv.width, cv.height);
    g.fillStyle = '#fff'; g.font = '18px sans-serif';
    g.fillText(caption, PAD, 24);
    await Promise.all(imgs.map((src, i) => new Promise((res) => {
      const im = new Image();
      im.onload = () => {
        const x = PAD + (i % cols) * (CELL + PAD);
        const y = HEAD + PAD + Math.floor(i / cols) * (CELL + PAD);
        // вписываем с сохранением пропорций, лишнее обрезаем по центру
        const s = Math.max(CELL / im.width, CELL / im.height);
        const w = im.width * s, h = im.height * s;
        g.save(); g.beginPath(); g.rect(x, y, CELL, CELL); g.clip();
        g.drawImage(im, x + (CELL - w) / 2, y + (CELL - h) / 2, w, h);
        g.restore();
        res();
      };
      im.onerror = () => res();
      im.src = src;
    })));
    return cv.toDataURL('image/jpeg', 0.82);
  }, { imgs, cols, caption: `${lot.id} · ${lot.rooms || 'студия'} · ${lot.totalArea} м² · ${(lot.priceRub || 0).toLocaleString('ru-RU')} ₽ · ${lot.street || ''} ${lot.house || ''}` });
  fs.mkdirSync(require('path').dirname(file), { recursive: true });
  fs.writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'));
  return file;
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
  const changes = [];
  for (const l of lots) {
    if (!l.fingerprint) continue;
    const e = arc.flats[l.fingerprint];
    if (e) {
      // цена того же объявления между снимками — самый честный сигнал торга
      const prev = e.listings.filter((x) => x.id === l.id).pop();
      if (prev && prev.price && l.priceRub && prev.price !== l.priceRub) {
        changes.push({ id: l.id, from: prev.price, to: l.priceRub, address: e.address });
        e.listings.push({ id: l.id, created: l.created, price: l.priceRub, seen: today });
      }
    }
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
  // квартиры, которых в этом снимке не было: сняты, проданы или ушли из фильтра
  const seenFps = new Set(lots.map((l) => l.fingerprint).filter(Boolean));
  const gone = Object.entries(arc.flats)
    .filter(([fp, e]) => e.lastSeen !== today && seenFps.size && !seenFps.has(fp))
    .map(([fp, e]) => ({ fp, address: e.address, lastSeen: e.lastSeen }));
  return { fresh, updated, changes, gone };
}

/* ---------- комплектность ----------
   Цена за метр сопоставима только между объектами одной готовности. Оболочка
   с премиальной отделкой и квартира под ключ с мебелью и кухней — разные
   товары, и сравнивать их по ₽/м² значит выдавать одно за другое. */
const BARE = /без отделки|предчистов|white ?box|под чистовую|черновая/;

/* Ремонт, который ещё идёт. Продавец пишет об этом прямо — «работы завершатся
   в августе», — но поля объявления такую квартиру не отличают от готовой. */
const UNFINISHED = /(ремонтн\w+ работ\w*|ремонт|отделк\w+|работы)[^.]{0,80}(завершат?ся|заверш[иё]тся|будет заверш|планируется заверш|закончат?ся|окончани\w+)|ремонт в процессе|идёт ремонт|идет ремонт/;

/* Мебель и техника в комплекте — заявка словами. Поле hasFurniture этого не
   говорит (см. ниже), а текст говорит, и его хотя бы можно предъявить. */
const FURNISHED = /меблирован|с мебелью и техник|мебел\w+ и (бытов\w+ )?техник|под ключ|под тапочк|укомплектован\w* мебел/;

function completeness(lot) {
  const t = (lot.description || '').toLowerCase();
  const m = BARE.exec(t);
  const bareNow = m && !PAST.test(t.slice(Math.max(0, m.index - 60), m.index));
  /* Метка отделки осмысленна только для первичной продажи. На вторичке фильтр
     decorations_list приравнивает «нет данных» к «без отделки» и возвращает
     469 лотов из 492 — по нему вся вторичка стала бы оболочкой. */
  const isPrimary = lot.fromDeveloper === true || lot.saleType === 'fz214'
    || lot.saleType === 'dupt' || lot.houseFinished === false;
  if (isPrimary) {
    /* turnkey приходит только из карточки: в выдаче поиска decoration
       принимает without/fine/rough/null и ничего больше. */
    if (lot.decoration === 'turnkey' || lot.decorFilter === 'fineWithFurniture'
      || lot.decoration === 'fineWithFurniture') return 'под ключ';
    if (['without', 'rough', 'fine', 'preFine'].includes(lot.decorFilter)) return 'оболочка';
  }
  if (lot.hasFurniture === false) return 'оболочка';
  if (lot.decoration === 'without' || lot.decoration === 'rough') return 'оболочка';
  if (bareNow) return 'оболочка';
  /* Незавершённый ремонт — отдельное состояние. Это не оболочка (деньги в
     отделку вложены) и не «под ключ» (въехать нельзя, и результата никто
     не видел). Проверять до всех остальных признаков готовности. */
  if (UNFINISHED.test(t)) return 'ремонт не сдан';
  /* hasFurniture=true не значит ничего. На вторичке галочка стоит у 70 лотов
     из 187, и в их числе пустая квартира без кухни (330733568) и квартира,
     где ремонт сдаётся в августе (331424705). Ставит её продавец, никто не
     проверяет. Заявка словами хотя бы конкретна: «полностью укомплектована
     мебелью и техникой» — это обещание, за которое можно спросить. */
  if (FURNISHED.test(t) || (/мебел/.test(t) && /техник/.test(t))) return 'под ключ';
  return 'неизвестно';
}

/* ---------- чем подтверждён уровень отделки ----------
   «Дизайнерский ремонт» — галочка, её ставят все. Что действительно отличает
   премиальную комплектацию от чистовой отделки застройщика, так это
   перечень марок: кухня Arrital со столешницей Fenix и техникой Gaggenau —
   проверяемое утверждение, «квартира с новой отделкой» — нет.
   Список не полный и не может быть полным; он нужен, чтобы отделить лот с
   поимённой комплектацией от лота, где о начинке не сказано ничего. */
const BRANDS = {
  'техника': ['gaggenau', 'miele', 'sub-zero', 'subzero', 'wolf', 'la cornue', 'v-zug', 'liebherr',
    'smeg', 'bosch', 'siemens', 'neff', 'aeg', 'electrolux', 'asko', 'kuppersbusch', 'falmec', 'daikin'],
  'кухня и мебель': ['arrital', 'poliform', 'varenna', 'boffi', 'molteni', 'scavolini', 'valcucine',
    'ernestomeda', 'snaidero', 'rimadesio', 'minotti', 'flexform', 'ditre', 'cassina', 'baxter',
    'meridiani', 'porada', 'bonaldo', 'bontempi', 'natuzzi', 'calligaris', 'boconcept', 'b&b italia'],
  'сантехника': ['cea', 'fantini', 'gessi', 'dornbracht', 'axor', 'hansgrohe', 'grohe', 'thg',
    'flaminia', 'antonio lupi', 'agape', 'kaldewei', 'duravit', 'villeroy', 'geberit', 'tece',
    'simas', 'cielo', 'catalano', 'carlofrattini'],
  'свет': ['moooi', 'flos', 'vibia', 'artemide', 'foscarini', 'lodes', 'occhio', 'delta light',
    'tom dixon', 'catellani', 'bocci', 'sovet'],
  'материалы': ['fenix', 'maxfine', 'fmg', 'iris ceramica', 'italon', 'laminam', 'florim', 'marazzi',
    'atlas concorde', 'dekton', 'neolith', 'caesarstone', 'antolini', 'landoor'],
};

function finishEvidence(lot) {
  const t = (lot.description || '').toLowerCase();
  const found = {}, list = [];
  for (const [cat, names] of Object.entries(BRANDS)) {
    for (const n of names) {
      if (new RegExp(`(^|[^a-zа-я])${n.replace(/[-&]/g, '\\$&')}([^a-zа-я]|$)`).test(t)) {
        (found[cat] = found[cat] || []).push(n);
        list.push(n);
      }
    }
  }
  return {
    furnished: FURNISHED.test(t) || (/мебел/.test(t) && /техник/.test(t)),
    unfinished: UNFINISHED.test(t),
    designerClaimed: /дизайнерск\w+ (ремонт|отделк|интерьер)|авторск\w+ (ремонт|интерьер|проект)/.test(t),
    brands: list,
    categories: Object.keys(found),
    /* Три и более категории марок — комплектацию описали, а не назвали. */
    spelledOut: Object.keys(found).length >= 3,
  };
}

/* Год постройки. У новостроек buildYear пустой — год живёт в сроке сдачи
   корпуса. На запросе «дизайнерский ремонт, дом от 2017» по ЦАО поле было
   пустым у 63 лотов из 137, и отсев по нему выбрасывал их все, включая
   готовые дома 2017–2023. Отсутствие данных — не то же самое, что
   несоответствие. */
function buildingYear(lot) {
  if (lot.buildYear) return lot.buildYear;
  if (lot.deadline && lot.deadline.year) return lot.deadline.year;
  return null;
}

/* Готовность: ключи на руках или обязательство построить к сроку.
   Квартира со сдачей в 2030 и квартира, куда можно въехать сегодня, — разные
   товары: деньги на эскроу заморожены на годы, а сроки сдвигаются. */
function readiness(lot) {
  if (lot.houseFinished === true) return 'сдан';
  if (lot.houseFinished === false || (lot.deadline && lot.deadline.year)) {
    return lot.deadline && lot.deadline.year ? `строится до ${lot.deadline.year}` : 'строится';
  }
  if (lot.saleType === 'free' && lot.buildYear) return 'сдан';
  return 'неизвестно';
}

const SALE_RU = { free: 'свободная продажа', fz214: 'ДДУ', dupt: 'переуступка' };

/* Что мешает сравнивать два лота напрямую. Пустой список — сравнение честное. */
function comparabilityGaps(a, b) {
  const g = [];
  const ca = completeness(a), cb = completeness(b);
  if (ca !== cb) g.push(`комплектность: ${ca} / ${cb}`);
  const ra = readiness(a), rb = readiness(b);
  if (ra !== rb && ra !== 'неизвестно' && rb !== 'неизвестно') g.push(`готовность: ${ra} / ${rb}`);
  else if (a.deadline && b.deadline && Math.abs(a.deadline.year - b.deadline.year) >= 1) {
    g.push(`срок сдачи: ${a.deadline.year} / ${b.deadline.year}`);
  }
  if (a.saleType && b.saleType && a.saleType !== b.saleType) {
    g.push(`условия сделки: ${SALE_RU[a.saleType] || a.saleType} / ${SALE_RU[b.saleType] || b.saleType}`);
  }
  if (a.isApartments !== b.isApartments) g.push('апартаменты / квартира');
  const f = (x) => x.features || {};
  if (f(a).euroLayout !== f(b).euroLayout) g.push('планировка: евро / классическая');
  if (a.floors && b.floors && a.floor != null && b.floor != null) {
    const rel = (x) => x.floor / x.floors;
    if (Math.abs(rel(a) - rel(b)) > 0.4) g.push('сильно разная высота этажа в доме');
  }
  return g;
}

/* Признаки, которых нет в полях API и которые живут только в описании.
   Замерено на 300 лотах: паркинг упоминают 45%, евро-планировку 34%, вид 31%. */
function features(lot) {
  const t = (lot.description || '').toLowerCase();
  return {
    parkingMentioned: /парк(инг|овочн)|машиноместо|м\/м\b/.test(t),
    parkingSeparate: /(машиноместо|парковочн\w+|паркинг)[^.]{0,60}(отдельн|доплат|не включ)/.test(t),
    euroLayout: /\bевро[-\s]?\d|кухня-гостиная|евродвушк|евротрёшк|евротрешк/.test(t),
    viewClaimed: /вид[^.]{0,40}(на реку|на парк|кремл|сити|панорамн|на воду)/.test(t),
  };
}

/* ---------- цена относительно рынка ----------
   Голая цена ничего не говорит. После sweep на руках весь дом и весь район,
   поэтому положение лота считается по своим данным, без оценок Циан. */
const median = (xs) => {
  if (!xs.length) return null;
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

function withMarket(lots, minCohort = 4) {
  const ppm = (l) => (l.priceRub && l.totalArea ? l.priceRub / l.totalArea : null);
  const enriched = lots.map((l) => ({ ...l, completeness: completeness(l), readiness: readiness(l), features: features(l) }));
  /* Медиана считается внутри своей комплектности: иначе оболочки утягивают
     планку вниз и квартира под ключ выглядит переоценённой (или наоборот). */
  const groups = { house: new Map(), cohort: new Map() };
  for (const l of enriched) {
    const v = ppm(l); if (!v) continue;
    if (l.houseId) {
      // готовность и в ключе корпуса: в одном доме корпуса сдают разными годами
      const hk = `${l.houseId}|${l.completeness}|${l.readiness}`;
      (groups.house.get(hk) || groups.house.set(hk, []).get(hk)).push(v);
    }
    const ck = `${l.district}|${l.rooms}|${l.completeness}|${l.readiness}`;
    (groups.cohort.get(ck) || groups.cohort.set(ck, []).get(ck)).push(v);
  }
  const med = (m, k, n) => { const xs = m.get(k); return xs && xs.length >= n ? median(xs) : null; };
  return enriched.map((l) => {
    const v = ppm(l);
    const hk = `${l.houseId}|${l.completeness}|${l.readiness}`, ck = `${l.district}|${l.rooms}|${l.completeness}|${l.readiness}`;
    const mh = l.houseId ? med(groups.house, hk, minCohort) : null;
    const mc = med(groups.cohort, ck, minCohort);
    const rel = (m) => (v && m ? +((v - m) / m * 100).toFixed(1) : null);
    return {
      ...l,
      pricePerM2: v ? Math.round(v) : null,
      vsBuildingPct: rel(mh), vsCohortPct: rel(mc),
      // с чем именно сравнивали — чтобы процент нельзя было прочитать вслепую
      comparedWith: { completeness: l.completeness, readiness: l.readiness,
        inBuilding: (groups.house.get(hk) || []).length, inCohort: (groups.cohort.get(ck) || []).length },
    };
  });
}

/* Схлопывание объявлений в квартиры. В больших ЖК одну квартиру выставляют
   десятки субагентов по разной цене — считать их отдельными лотами бессмысленно,
   а переплата за такой же объект доходит до 20%. */
/* Площадь одной и той же квартиры продавцы указывают по-разному — встречалось
   «65,5 м² (по факту 67,8)». Точное совпадение теряет такие пары, поэтому
   внутри дома/этажа/комнатности площади сшиваются с допуском. */
function groupSameFlat(lots, areaTol = 0.6) {
  const coarse = new Map(), loose = [];
  for (const l of lots) {
    if (!l.houseId || l.floor == null || l.totalArea == null) { loose.push(l); continue; }
    // Комнатность в ключ не идёт: у студий и свободных планировок она пустая,
    // и одно объявление той же квартиры уезжало в отдельную группу.
    const k = `${l.houseId}|${l.floor}`;
    (coarse.get(k) || coarse.set(k, []).get(k)).push(l);
  }
  const groups = [];
  for (const bucket of coarse.values()) {
    const byArea = [...bucket].sort((a, b) => a.totalArea - b.totalArea);
    let cur = [byArea[0]];
    for (const l of byArea.slice(1)) {
      if (l.totalArea - cur[cur.length - 1].totalArea <= areaTol) cur.push(l);
      else { groups.push(cur); cur = [l]; }
    }
    groups.push(cur);
    /* Совпали дом, этаж и площадь — но если комнатность указана у разных
       объявлений по-разному, это разные квартиры зеркальных планировок.
       Пустая комнатность ничему не противоречит и остаётся с группой. */
    for (let i = groups.length - 1; i >= 0; i--) {
      const g = groups[i];
      /* Вид жилья берём из flatType (rooms/studio/openPlan) — он заполнен
         всегда, в отличие от roomsCount, пустого у студий. */
      const kind = (x) => (x.flatType && x.flatType !== 'rooms' ? x.flatType : x.rooms);
      const kinds = [...new Set(g.map(kind).filter((r) => r != null))];
      if (kinds.length <= 1) continue;
      groups.splice(i, 1, ...kinds.map((r) => g.filter((x) => kind(x) === r || kind(x) == null)));
    }
  }
  return { groups, loose };
}

function dedupe(lots, areaTol) {
  const { groups, loose } = groupSameFlat(lots, areaTol);
  const flats = groups.map((g) => {
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
function findTwins(lots, areaTol) {
  return groupSameFlat(lots, areaTol).groups.filter((g) => g.length > 1);
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

if (require.main === module) (async () => {
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

    } else if (cmd === 'find') {
      /* Поиск id по названию: ЖК, метро, район, город. Без этого id ЖК
         приходилось выуживать, перебирая выдачу по району. */
      const query = a._.slice(1).join(' ') || a.query;
      const url = 'https://api.cian.ru/geo-suggest/v1/suggest/'
        + `?query=${encodeURIComponent(query)}&regionId=${a.region || 1}&offerType=flat&dealType=sale`;
      const r = await ctx.request.get(url, { headers: { referer: 'https://www.cian.ru/', 'user-agent': UA }, timeout: 30000 });
      const s = ((await r.json()).data || {}).suggestions || {};
      const GEO = { newbuildings: 'newobject', undergrounds: 'underground', districts: 'district', streets: 'street' };
      for (const [group, items] of Object.entries(s)) {
        const list = (items && items.items) || [];
        if (!list.length) continue;
        log(`\n${group}${GEO[group] ? `  (geo type "${GEO[group]}")` : ''}`);
        list.slice(0, 8).forEach((i) => log(`  id=${String(i.id).padEnd(9)} ${i.fullName || i.name}${i.address ? ' — ' + i.address : ''}`));
      }

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
      if (a['min-year']) {
        const y = parseInt(a['min-year'], 10);
        const unknown = lots.filter((l) => buildingYear(l) == null).length;
        lots = lots.filter((l) => { const b = buildingYear(l); return b == null || b >= y; });
        if (unknown) log(`год постройки неизвестен у ${unknown} лотов — оставлены, отсев по году их не касается`);
      }
      if (a['max-area']) lots = lots.filter((l) => l.totalArea && l.totalArea <= parseFloat(a['max-area']));
      if (lots.length !== before) log(`после доотбора на своей стороне: ${lots.length} из ${before}`);
      lots = withMarket(lots);
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
      const maxPages = parseInt(a.pages || '12', 10);
      const { seen, declared } = await sweep(ctx, q, parseInt(a.limit || '250', 10), maxPages);
      // по умолчанию доопределяем комплектность фильтром: без неё сравнение цен врёт
      if (a.resolve !== '0') await resolveDecoration(ctx, q, seen, Math.min(maxPages, 6));
      let { lots } = finishSweep(seen, { count: declared });
      log(`\nзаявлено ${declared}, собрано ${lots.length} (${Math.round(lots.length / declared * 100)}%)`);
      lots = withMarket(lots);
      const { flats, loose } = dedupe(lots);
      const multi = flats.filter((f) => f.listings > 1).sort((x, y) => y.listings - x.listings);
      log(`объявлений ${lots.length} -> квартир ${flats.length}` +
          (loose.length ? ` (+${loose.length} без корпуса в адресе, схлопнуть нельзя)` : ''));
      log(`квартир, выставленных больше одного раза: ${multi.length}, лишних объявлений ${lots.length - flats.length - loose.length}`);
      multi.slice(0, 5).forEach((f) => log(
        `  ${f.street}, ${f.house}, эт.${f.floor}, ${f.totalArea} м² — ${f.listings} объявл., ` +
        `${(f.priceMin || 0).toLocaleString('ru-RU')}–${(f.priceMax || 0).toLocaleString('ru-RU')} ₽` +
        (f.overpay ? `, переплата за верхнее ${f.overpay}%` : '')));
      const byComp = {};
      flats.forEach((f) => { byComp[f.completeness] = (byComp[f.completeness] || 0) + 1; });
      log(`комплектность: ${Object.entries(byComp).map(([k, v]) => `${k} ${v}`).join(', ')}`);
      const tally = (fn) => { const m = {}; flats.forEach((f) => { const k = fn(f); if (k) m[k] = (m[k] || 0) + 1; }); return m; };
      const sales = tally((f) => SALE_RU[f.saleType] || f.saleType);
      if (Object.keys(sales).length) log(`договор: ${Object.entries(sales).map(([k, v]) => `${k} ${v}`).join(', ')}`);
      const ready = tally((f) => f.readiness);
      log(`готовность: ${Object.entries(ready).map(([k, v]) => `${k} ${v}`).join(', ')}`);
      const feat = (k) => flats.filter((f) => f.features && f.features[k]).length;
      log(`из описаний: паркинг ${feat('parkingMentioned')}, евро-планировка ${feat('euroLayout')}, заявлен вид ${feat('viewClaimed')}`);
      const promoted = flats.filter((f) => f.promoted).length;
      if (promoted) log(`платное продвижение: ${promoted} из ${flats.length}`);
      const rated = flats.filter((f) => f.vsBuildingPct != null).sort((x, y) => x.vsBuildingPct - y.vsBuildingPct);
      if (rated.length) {
        log('\nцена относительно медианы своего корпуса:');
        const line = (f, tag) => log(`  ${tag} ${String(f.vsBuildingPct > 0 ? '+' + f.vsBuildingPct : f.vsBuildingPct).padStart(6)}%  ${f.id}  ` +
          `${f.totalArea} м², ${(f.priceRub || 0).toLocaleString('ru-RU')} ₽  [${f.completeness}, ${f.readiness}, сравнение с ${f.comparedWith.inBuilding}]  ${f.street}, ${f.house}`);
        rated.slice(0, 3).forEach((f) => line(f, 'дешевле'));
        rated.slice(-2).forEach((f) => line(f, 'дороже '));
      }
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
        const nPhoto = parseInt(a.photos || '9', 10);
        let files = [], sheet = null;
        if (a.photos !== '0') {
          if (a.files) files = await fetchPhotos(ctx, l, `${dir}/${l.id}`, nPhoto);
          else sheet = await contactSheet(ctx, page, l, `${dir}/${l.id}.jpg`, nPhoto);
        }
        const ev = finishEvidence(l);
        report.push({ id: l.id, url: l.url, price: l.priceRub, area: l.totalArea, ...r, evidence: ev, completeness: completeness(l), sheet, photos: files });
        log(`\n${l.id}  ${l.rooms}к ${l.totalArea} м²  ${(l.priceRub || 0).toLocaleString('ru-RU')} ₽  — ${r.verdict}, ${completeness(l)}`);
        if (ev.unfinished) log('   ремонт по тексту ещё не завершён');
        log(`   комплектация: ${ev.spelledOut ? 'расписана по маркам' : ev.brands.length ? 'марки названы частично' : 'марки не названы'}` +
          (ev.brands.length ? ` (${ev.categories.join(', ')}: ${ev.brands.slice(0, 8).join(', ')})` : '') +
          `; мебель и техника ${ev.furnished ? 'заявлены' : 'не заявлены'}`);
        if (r.red.length) log(`   против: ${r.red.join('; ')}`);
        if (r.yellow.length) log(`   насторожило: ${r.yellow.join('; ')}`);
        if (r.green.length) log(`   за: ${r.green.join('; ')}`);
        if (r.flags.length) log(`   поля: ${r.flags.join('; ')}`);
        if (sheet) log(`   контактный лист: ${sheet}`);
        else if (files.length) log(`   фото: ${files.length} шт. в ${dir}/${l.id}/`);
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
        const { fresh, updated, changes, gone } = mergeArchive(arc, lots, today);
        fs.writeFileSync(archivePath, JSON.stringify(arc, null, 2) + '\n');
        log(`архив ${archivePath}: +${fresh} новых квартир, ${updated} подтверждено, всего ${Object.keys(arc.flats).length}`);
        if (changes.length) {
          log(`\nцена изменилась с прошлого снимка (${changes.length}):`);
          changes.slice(0, 10).forEach((c) => log(`  ${c.id}  ${c.from.toLocaleString('ru-RU')} -> ${c.to.toLocaleString('ru-RU')} ₽` +
            `  (${c.to < c.from ? '' : '+'}${((c.to - c.from) / c.from * 100).toFixed(1)}%)  ${c.address}`));
        }
        if (gone.length) log(`\nне попали в этот снимок (сняты, проданы или ушли из фильтра): ${gone.length}`);
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
              // в большом доме объявлений сильно больше 28, одной страницей не обойтись
              for (let pg = 1; pg <= parseInt(a['house-pages'] || '3', 10); pg++) {
                const { offers } = await searchPage(ctx, {
                  _type: 'flatsale', engine_version: { type: 'term', value: 2 },
                  region: q.region || { type: 'terms', value: [1] },
                  geo: { type: 'geo', value: [{ type: 'house', id: h }] },
                }, pg);
                if (!offers.length) break;
                offers.map(normalize).forEach((n) => { if (!lots.some((l) => l.id === n.id)) lots.push(n); });
                await sleep(800);
              }
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

/* Чистые функции наружу — чтобы их можно было проверить без сети. */
module.exports = { normalize, groupSameFlat, dedupe, findTwins, withMarket, median, assessRepair, mergeArchive, completeness, comparabilityGaps, features, readiness, finishEvidence, buildingYear };
