#!/usr/bin/env node
/**
 * Клиент Циан поверх того же API, которым пользуется сама выдача.
 *
 *   node tools/cian/cian.js find   Остров              — id ЖК, метро, района по названию
 *   node tools/cian/cian.js count  --query q.json
 *   node tools/cian/cian.js search --query q.json [--pages 3] [--all] [--out lots.json] [--min-year 2016] [--garden-ring] [--strict нет] [--similar-pages 4]
 *   node tools/cian/cian.js url    --query q.json      — каноническая ссылка cat.php
 *   node tools/cian/cian.js probe  --query q.json --with '{"loggia":{"type":"term","value":true}}'
 *   node tools/cian/cian.js sweep  --query q.json [--limit 250] [--all] [--dedupe] [--resolve 0]
 *   node tools/cian/cian.js verify --ids 1,2 [--photos 12] [--cols 4] [--frames 5,13]  — лист, затем кадры в оригинале
 *   node tools/cian/cian.js snapshot --query q.json | --queries watchlist.json | --from a.json,b.json
 *   node tools/cian/cian.js archive   — что накоплено: даты снимков, запросы, движение цен
 *   node tools/cian/cian.js exposure --query q.json [--deep] — двойники и реальный срок
 *   node tools/cian/cian.js compare --lot 327985409 --cohort lots.json [--tier бизнес]
 *   node tools/cian/cian.js grade  --template 331300080 [--from lots.json]  — заготовка под заполнение
 *   node tools/cian/cian.js grade  --lots lots.json --marks marks.json  — записать оценку отделки
 *   node tools/cian/cian.js grade  --list | --check
 *   node tools/cian/cian.js report — пересобрать docs/cian/lots.md из оценок и архива
 *   node tools/cian/cian.js refresh [--limit N] [--confirm нет] — что из архива ещё продаётся, а что ушло
 *   node tools/cian/cian.js card   327985409 331215568 [--out cards.json]  — история цены и поля, которых нет в выдаче
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
    /* Сколько отдаст пагинация этой сортировки. Держится точно только на
       сортировке по умолчанию: у остальных заявлено 181, а выдано 177–180,
       и внутри одной пагинации попадаются повторы. Останавливаться надо по
       пустой странице, а не по достижению этого числа.

       Разница с offerCount — схлопнутые «похожие»: в выдачу попадает лидер
       группы, остальные прячутся за ним. Достаются они не дроблением, а
       ключом multi_id по номеру лидера (см. expandSimilar). */
    aggregated: d.aggregatedCount ?? null,
    offers: d.offersSerialized || d.offers || [],
    // Циан сам сериализует jsonQuery обратно в query-строку cat.php — по ней
    // видно, какие фильтры он принял, и как они называются в адресе.
    queryString: d.queryString || null,
    fullUrl: d.fullUrl || null,
  };
}

/* ---------- произвольные объявления по списку id ----------
   Поиск фильтровать по списку id не умеет: `multi_id` и `identical_id` — это
   номера группы дублей, а не лота, и на любом id объявления дают ноль.
   Отдельная ручка умеет: до 28 штук за запрос, в том же порядке, с полным
   оффером. На 29 отвечает HTTP 400 «Too many cianOfferIds».

   Ловушка: в этой сериализации НЕТ creationDate, есть только дата поднятия.
   Срок экспозиции отсюда брать нельзя. */
const BY_IDS_API = 'https://api.cian.ru/search-offers/v1/get-offers-by-ids-desktop/';
const BY_IDS_LIMIT = 28;

/* Второй, независимый способ спросить про один лот: обычный поиск умеет
   фильтр `id` типа term. Нужен как подтверждение: ручка by-ids не
   документирована, и её молчание само по себе ещё не доказательство. */
async function offerAliveById(ctx, id) {
  try {
    const r = await searchPage(ctx, { _type: 'flatsale',
      engine_version: { type: 'term', value: 2 },
      id: { type: 'term', value: Number(id) } }, 1);
    return r.count > 0 || (r.offers || []).length > 0;
  } catch (e) { return null; }   // сеть подвела — не знаем, а не «нет»
}

async function offersByIds(ctx, ids, dealType = 'flatsale') {
  /* Мусор на входе не должен исчезать молча: вернём его отдельным списком,
     иначе опечатка в номере выглядит как снятое объявление. */
  const asked = [], bad = [];
  for (const raw of ids) {
    const n = Number(raw);
    if (Number.isFinite(n) && n > 0) { if (!asked.includes(n)) asked.push(n); } else bad.push(raw);
  }
  const offers = [], failed = [];
  for (let i = 0; i < asked.length; i += BY_IDS_LIMIT) {
    const chunk = asked.slice(i, i + BY_IDS_LIMIT);
    let res = null, why = null;
    for (let attempt = 1; attempt <= 4; attempt++) {
      try {
        const r = await ctx.request.post(BY_IDS_API, {
          headers: API_HEADERS,
          /* jsonQuery нужен только ради _type: он обязателен, а фильтры в нём
             на отбор не влияют — список id главнее. */
          data: { cianOfferIds: chunk, jsonQuery: { _type: dealType } },
          timeout: 60000,
        });
        if (r.status() === 200) { res = r; break; }
        why = `http ${r.status()}`;
        /* 400 «Too many cianOfferIds» и прочие клиентские отказы повторять
           бессмысленно — сдаёмся сразу. */
        if (r.status() < 500) break;
      } catch (e) { why = e.message.split('\n')[0]; }
      await sleep(2500 * attempt);
    }
    if (!res) {
      /* Раньше здесь стоял `continue`, и пачка из 28 номеров исчезала без
         следа: refresh счёл бы их снятыми с продажи. Молчаливая потеря —
         худший вид ошибки, потому что выглядит как ответ. */
      failed.push(...chunk);
      log(`  ! пачка из ${chunk.length} не ответила (${why}) — эти номера не проверены`);
      continue;
    }
    offers.push(...((await res.json()).offersSerialized || []));
    if (i + BY_IDS_LIMIT < asked.length) await sleep(1100);
  }
  /* Сопоставлять только по cianId: длина ответа не равна длине запроса —
     мёртвых нет, дубли сервер схлопывает. */
  const back = new Set(offers.map((o) => o.cianId || o.id));
  return { offers, failed, bad, missing: asked.filter((id) => !back.has(id) && !failed.includes(id)) };
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
    /* Координаты приходят и в выдаче, и в карточке. Без них нельзя отсечь
       выдачу по кольцу: район — слишком грубая единица, Хамовники и
       Пресненский лежат по обе стороны Садового. */
    lat: ((o.geo || {}).coordinates || {}).lat ?? null,
    lng: ((o.geo || {}).coordinates || {}).lng ?? null,
    created,
    daysOnMarket: created ? Math.round((Date.now() - Date.parse(created)) / 86400000) : null,
    // added — дата последнего поднятия, creationDate её переживает
    bumped: o.added || null,
    title: o.title || null,
    hasFurniture: o.hasFurniture ?? null,
    decoration: o.decoration ?? null,
    repairType: o.repairType ?? null,
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
    /* Сколько объявлений Циан спрятал за это как «похожие». Ровно из-за них
       offerCount больше того, что отдаёт пагинация: в выдачу попадает
       только лидер группы. */
    similarCount: ((o.similar || {}).count) ?? 0,
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
  let count = null, aggregated = null;
  const q = sort ? { ...jsonQuery, sort: { type: 'term', value: sort } } : jsonQuery;
  for (let p = 1; p <= maxPages; p++) {
    const { count: c, aggregated: ag, offers } = await searchPage(ctx, q, p);
    if (count === null) { count = c; aggregated = ag; }
    if (!offers.length) break;
    const before = seen.size;
    offers.forEach((o) => { const n = normalize(o); if (!seen.has(n.id)) seen.set(n.id, n); });
    log(`  ${(sort || 'по умолчанию').padEnd(18)} стр ${p}: +${offers.length}, новых ${seen.size - before}, накоплено ${seen.size}`);
    await sleep(1200);
  }
  return { count, aggregated };
}

async function collect(ctx, jsonQuery, maxPages, all) {
  const seen = new Map();
  let count = null, aggregated = null;
  for (const sort of all ? SORTS : [null]) {
    const c = await collectSorted(ctx, jsonQuery, maxPages, seen, sort);
    if (count === null) { count = c.count; aggregated = c.aggregated; }
    if (!all) break;
  }
  return { count, aggregated, lots: [...seen.values()] };
}

/* ---------- раскрытие схлопнутых групп ----------
   Циан прячет похожие объявления за одним: у лидера приходит
   `similar: {count, url}`, а в url — параметр `multi_id` с его же номером.
   Запрос с этим ключом отдаёт всю группу и уважает остальные фильтры;
   по номеру не-лидера возвращается ноль, из-за чего ключ и сочли нерабочим.

   Это и есть разница между offerCount и тем, что отдаёт пагинация, — и
   достаётся она прямо, а не дроблением запроса по осям. */
/* Ключ multi_id отдаёт группу целиком и НЕ уважает остальные фильтры: на
   запросе с apartment=false раскрытие групп притащило 178 апартаментов,
   тогда как обычная выдача не дала ни одного. Всё, что пришло из группы,
   надо сверить с исходным запросом самому.

   Проверяются только те условия, которые видны в записи лота; чего проверить
   нечем — то и не отбрасывается. */
function matchesQuery(lot, q) {
  const rng = (key, val) => {
    const r = q[key] && q[key].value;
    if (!r || val == null) return true;
    if (r.gte != null && val < r.gte) return false;
    if (r.lte != null && val > r.lte) return false;
    return true;
  };
  if (q.apartment && q.apartment.value === false && lot.isApartments) return false;
  if (q.room && Array.isArray(q.room.value) && lot.rooms != null && !q.room.value.includes(lot.rooms)) return false;
  if (!rng('total_area', lot.totalArea)) return false;
  if (!rng('price', lot.priceRub)) return false;
  if (q.house_year && buildingYear(lot) != null && !rng('house_year', buildingYear(lot))) return false;
  if (q.floor && lot.floor != null && !rng('floor', lot.floor)) return false;
  return true;
}

async function expandSimilar(ctx, q, lots, maxPages = 4) {
  const leaders = lots.filter((l) => l.similarCount > 0);
  if (!leaders.length) return { added: [], dropped: 0, leaders: 0, cut: [], failed: [], short: [] };
  const have = new Set(lots.map((l) => l.id));
  const added = [];
  let dropped = 0;
  /* Группа могла оборваться тремя разными способами, и молчать нельзя ни об
     одном: раньше всё это выглядело одинаково — «раскрыл группы, добавилось
     N», и сколько осталось за краем, не знал никто.

     cut    — упёрлись в потолок страниц, за ним ещё есть;
     failed — ручка не ответила, группа не перечислена вовсе или наполовину;
     short  — лидер обещал similarCount, а пришло меньше. */
  const cut = [], failed = [], short = [];
  for (const l of leaders) {
    const sub = { ...q, multi_id: { type: 'term', value: l.id } };
    let seen = 0, pages = 0, broke = null;
    for (let p = 1; p <= maxPages; p++) {
      let r;
      try { r = await searchPage(ctx, sub, p); }
      catch (e) { broke = e.message || 'ручка не ответила'; break; }
      pages = p;
      if (!r.offers.length) break;
      seen += r.offers.length;
      for (const o of r.offers) {
        const n = normalize(o);
        if (have.has(n.id)) continue;
        have.add(n.id);
        if (matchesQuery(n, q)) added.push(n); else dropped++;
      }
      if (r.offers.length < 28) break;
      /* Страница полная и потолок близко — за ним осталось непрочитанное.
         Кроме случая, когда лидер обещал ровно столько, сколько уже
         прочитано: тогда обрыв мнимый, и кричать не о чем. */
      if (p === maxPages && (l.similarCount == null || l.similarCount > seen)) {
        cut.push({ id: l.id, promised: l.similarCount, seen, pages: p });
      }
      await sleep(700);
    }
    if (broke) failed.push({ id: l.id, promised: l.similarCount, seen, pages, why: broke });
    else if (l.similarCount != null && seen < l.similarCount && !cut.some((c) => c.id === l.id)) {
      short.push({ id: l.id, promised: l.similarCount, seen });
    }
    await sleep(500);
  }
  return { added, dropped, leaders: leaders.length, cut, failed, short };
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

/* Ось географии: если в запросе несколько районов, каждый — самостоятельный
   кусок. Районы не пересекаются по построению, а поодиночке помещаются в
   глубину выдачи там, где вместе не помещались. */
async function splitByGeo(ctx, q, parentCount) {
  const vals = (q.geo && Array.isArray(q.geo.value)) ? q.geo.value : null;
  if (!vals || vals.length < 2) return [];
  const out = [];
  for (const v of vals) {
    const sub = { ...q, geo: { type: 'geo', value: [v] } };
    const { count } = await searchPage(ctx, sub, 1);
    await sleep(700);
    if (!count) continue;
    if (parentCount != null && count > parentCount) {
      log(`  ! geo=${v.type}:${v.id} даёт ${count} при родительских ${parentCount} — ось отброшена`);
      return [];
    }
    out.push({ q: sub, count, label: `${v.type}=${v.id}` });
  }
  return out;
}

/* Ось площади. Диапазон берётся из самого запроса; своих границ не ставим —
   это сняло бы пользовательский фильтр и притащило лоты вне заказанного. */
async function splitByArea(ctx, q, parentCount, parts = 3) {
  const base = (q.total_area && q.total_area.value) || {};
  const lo = base.gte, hi = base.lte;
  if (lo == null || hi == null || hi - lo < 6) return [];
  const edges = [lo];
  /* Границы только целые: дробное lte Циан округляет вниз и создаёт дыру
     (297+95=392 против родительских 400). Целые дают перекрытие, а его
     снимает схлопывание по id. */
  for (let i = 1; i < parts; i++) edges.push(Math.round(lo + (hi - lo) * i / parts));
  edges.push(hi);
  const out = [];
  for (let i = 0; i < edges.length - 1; i++) {
    const sub = { ...q, total_area: { type: 'range', value: { gte: edges[i], lte: edges[i + 1] } } };
    const { count } = await searchPage(ctx, sub, 1);
    await sleep(700);
    if (!count) continue;
    if (parentCount != null && count > parentCount) {
      log(`  ! площадь ${edges[i]}–${edges[i + 1]} даёт ${count} при родительских ${parentCount} — ось отброшена`);
      return [];
    }
    out.push({ q: sub, count, label: `${edges[i]}–${edges[i + 1]} м²` });
  }
  return out;
}

/* Ось этажа — единственная, которая не только режет без пересечений, но и
   РАЗБИВАЕТ схлопывание: сумма offerCount по полосам точно равна родителю,
   а сумма aggregatedCount выше родительской на треть. Оси geo и room режут
   так же чисто, но не вскрывают ничего — множество id после них то же.

   Полосы подобраны под московские новостройки 2018+, где этажность почти
   вся 17+. На малоэтажной вторичке их придётся считать от распределения. */
const FLOOR_BANDS = [[1, 5], [6, 11], [12, 19], [20, 99]];

async function splitByFloor(ctx, q, parentCount) {
  if (q.floor) return [];                       // этаж уже задан — делить нечего
  const out = [];
  for (const [lo, hi] of FLOOR_BANDS) {
    const sub = { ...q, floor: { type: 'range', value: { gte: lo, lte: hi } } };
    const { count, aggregated } = await searchPage(ctx, sub, 1);
    await sleep(700);
    if (!count) continue;
    if (parentCount != null && count > parentCount) {
      log(`  ! этажи ${lo}–${hi} дают ${count} при родительских ${parentCount} — ось отброшена`);
      return [];
    }
    out.push({ q: sub, count, aggregated, label: `эт.${lo}–${hi}` });
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

async function sweep(ctx, q, limit, maxPages, allSorts) {
  const top = await searchPage(ctx, q, 1);
  log(`заявлено ${top.count}, перечислимо за один проход ${top.aggregated ?? '?'}` +
      (top.aggregated && top.count > top.aggregated
        ? ` — разницу в ${top.count - top.aggregated} подряд не добрать, только дроблением` : ''));
  let buckets = [{ q, count: top.count, label: 'всё' }];
  /* Порядок осей — от самой «чистой» к самой грубой. География делит без
     остатка и первой; отделка на вторичке бесполезна (см. traps.md, п. 15),
     поэтому идёт после комнатности, а не до неё; площадь — последний рубеж
     перед дроблением по цене. */
  for (const axis of ['geo', 'rooms', 'floor', 'decor', 'area']) {
    const next = [];
    for (const b of buckets) {
      if (b.count <= limit) { next.push(b); continue; }
      const parts = axis === 'geo' ? await splitByGeo(ctx, b.q, b.count)
        : axis === 'floor' ? await splitByFloor(ctx, b.q, b.count)
          : axis === 'area' ? await splitByArea(ctx, b.q, b.count)
            : await splitByAxis(ctx, b.q, axis, b.count);
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
  /* Одна сортировка внутри куска обрывается там же, где обрывалась выдача
     целиком: замер по Академическому показал, что развёртка нашла 39 лотов
     мимо обычного поиска, а тот — 29 мимо развёртки. Дробление и перебор
     сортировок ловят разное, поэтому применяются вместе. */
  const sorts = allSorts ? SORTS : [null];
  for (const b of buckets) {
    // из какой ветки отделки пришёл лот — это и есть надёжная метка комплектности
    const dv = b.q.decorations_list && b.q.decorations_list.value[0];
    for (const sort of sorts) {
      const sq = sort ? { ...b.q, sort: { type: 'term', value: sort } } : b.q;
      for (let p = 1; p <= maxPages; p++) {
        const { offers } = await searchPage(ctx, sq, p);
        if (!offers.length) break;
        offers.forEach((o) => {
          const n = normalize(o);
          if (dv) n.decorFilter = dv;
          if (!seen.has(n.id)) seen.set(n.id, n);
          else if (dv && !seen.get(n.id).decorFilter) seen.get(n.id).decorFilter = dv;
        });
        await sleep(800);
      }
    }
    log(`  ${b.label.padEnd(46)} ${String(b.count).padStart(5)} -> накоплено ${seen.size}`);
  }
  return { seen, declared: top.count, aggregated: top.aggregated };
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
/* Оценку ищем по всем объявлениям одной квартиры: у схлопнутой записи
   победившее объявление может быть не тем, которое оценивали. */
function gradeFor(grades, lot) {
  if (!grades || !lot) return null;
  const ids = [lot.id, ...((lot.alsoListedAs || []).map((x) => (x && x.id) ?? x))];
  for (const id of ids) if (grades[String(id)]) return grades[String(id)];
  if (lot.fingerprint) {
    const hit = Object.values(grades).find((g) => g.fingerprint && g.fingerprint === lot.fingerprint);
    if (hit) return hit;
  }
  return null;
}

/* Оценка ставилась по тем кадрам, которые были в галерее в тот день. Если
   продавец с тех пор добавил снимки, запись описывает уже не весь товар: на
   Космодамианской 4/22 к двенадцати рендерам дома потом добавили съёмку
   квартиры, и она оказалась бетонной коробкой, а в записи стояло «буква не
   ставится». Сравнивать есть с чем только там, где записано photosSeen. */
function galleryGrew(grade, lot) {
  if (!grade || !grade.photosSeen || !lot) return null;
  const now = (lot.photos || []).length;
  return now > grade.photosSeen ? { was: grade.photosSeen, now } : null;
}

/* Ряд цен квартиры из всех её объявлений. Циан отдаёт изменения новыми
   вперёд; переворачиваем каждый список, а потом сортируем по дате —
   сортировка в JS устойчива, поэтому внутри одного дня порядок сохранится.
   Без этого два изменения за одну дату встают как попало, и «500 -> 399»
   превращается в «-0%». */
function mergedPriceHistory(entry) {
  if (!entry || !entry.priceHistory) return null;
  const all = Object.entries(entry.priceHistory)
    .flatMap(([id, ch]) => (Array.isArray(ch) ? ch.slice().reverse() : []).map((p) => ({ ...p, id })))
    .sort((x, y) => x.date.localeCompare(y.date));
  return all.length > 1 ? all : null;
}

function loadGrades(p) {
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) { return { updated: null, flats: {} }; }
}

function loadArchive(p) {
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) { return { updated: null, flats: {} }; }
}

/* Что накоплено в архиве. Пока этого не было, форму архива приходилось
   выяснивать одноразовыми скриптами — и «в архиве одна дата» я заметил
   позже, чем следовало. */
function archiveStat(arc) {
  const flats = Object.values(arc.flats || {});
  const dates = new Set(), sources = {};
  let repeat = 0, moved = 0, priceMoves = [];
  for (const f of flats) {
    dates.add(f.firstSeen); dates.add(f.lastSeen);
    (f.sources || []).forEach((s) => { sources[s] = (sources[s] || 0) + 1; });
    const seen = new Set((f.listings || []).map((l) => l.seen).filter(Boolean));
    if (f.firstSeen !== f.lastSeen || seen.size > 1) repeat++;
    /* Одно объявление, две цены — это торг, а не переразмещение. */
    const byId = {};
    (f.listings || []).forEach((l) => { (byId[l.id] = byId[l.id] || []).push(l); });
    for (const rows of Object.values(byId)) {
      const prices = [...new Set(rows.map((r) => r.price).filter(Boolean))];
      if (prices.length > 1) {
        moved++;
        priceMoves.push({ address: f.address, from: rows[0].price, to: rows[rows.length - 1].price,
          id: rows[0].id, seen: rows[rows.length - 1].seen });
      }
    }
  }
  const sorted = [...dates].filter(Boolean).sort();
  const last = sorted[sorted.length - 1] || null;
  /* «Не попала в последний снимок» — кандидат в ушедшие только для тех, чей
     запрос в этом снимке был. Квартира, залитая из файла и ни в одном
     запросе не состоящая, просто вне поля зрения, и считать её пропавшей —
     та же ошибка, что уже дала 76 выдуманных пропаж. */
  const missing = flats.filter((f) => f.lastSeen !== last);
  const stale = missing.filter((f) => (f.sources || []).length).length;
  const blind = missing.length - stale;
  return { flats: flats.length, dates: sorted, first: sorted[0] || null, last,
    repeat, sources, moved, priceMoves, stale, blind };
}

function mergeArchive(arc, lots, today, source) {
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
        sources: source ? [source] : [],
        listings: [{ id: l.id, created: l.created, price: l.priceRub, seen: today }],
      };
      fresh++;
    } else {
      e.lastSeen = today;
      if (source) {
        e.sources = e.sources || [];
        if (!e.sources.includes(source)) e.sources.push(source);
      }
      if (!e.listings.some((x) => x.id === l.id)) {
        e.listings.push({ id: l.id, created: l.created, price: l.priceRub, seen: today });
      }
      updated++;
    }
  }
  arc.updated = today;
  /* Пропажа считается только внутри своего запроса. Иначе снимок по одному
     району объявляет «ушедшими» все квартиры всех остальных запросов —
     ровно это и произошло при первом же большом заливе, и в отчёте появились
     76 несуществующих пропаж.

     Без имени запроса (навалом из файлов) пропажу не считаем вовсе: судить
     не по чему, а молчание честнее выдуманного списка. */
  const gone = [];
  if (source) {
    const seenFps = new Set(lots.map((l) => l.fingerprint).filter(Boolean));
    for (const [fp, e] of Object.entries(arc.flats)) {
      if (e.lastSeen === today) continue;
      if (!(e.sources || []).includes(source)) continue;
      if (seenFps.has(fp)) continue;
      gone.push({ fp, address: e.address, lastSeen: e.lastSeen, sources: e.sources });
    }
  }
  return { fresh, updated, changes, gone, source: source || null };
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

/* Значения repairType из карточки. В выдаче поиска этого поля нет вовсе.
   На первых семи лотах оно совпало с оценкой по фотографиям — бетон `no`,
   обычный ремонт `euro`, авторский `design`, — и я чуть не записал его в
   надёжные. Восьмой опроверг: Lucky (327357005) помечен `design`, а на
   кадрах кухня эконом и крашеные двери, оценка C.

   Значит поле такое же заявительное, как галочка «дизайнерский» в поиске, и
   работает та же несимметричность, что у hasFurniture: `no` продавцу
   невыгодно, поэтому ему верим; `design` не стоит ничего, поэтому нет.
   Заполняется у вторички; у застройщика вместо него `decoration`. */
const REPAIR_RU = { no: 'без ремонта', cosmetic: 'косметический', euro: 'евроремонт', design: 'дизайнерский' };

function completeness(lot) {
  const t = (lot.description || '').toLowerCase();
  /* Верим только отрицанию: «нет ремонта» продавцу невыгодно. Значение
     `design` не проверяем и в выводы не берём — см. REPAIR_RU. */
  if (lot.repairType === 'no') return 'оболочка';
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

/* ---------- уровень отделки ----------
   Комплектность (`completeness`) отвечает только на вопрос «есть мебель или
   нет». Разницу между авторским интерьером за 1,7 млн ₽/м² и обычной
   квартирой за те же деньги поля не описывают вообще, и решает её глаз.

   Чтобы взгляд был повторяемым, оценка ставится не буквой, а шестью
   наблюдаемыми признаками — их видно на фотографиях, и через полгода они
   читаются так же. Буква из них выводится арифметикой, а не впечатлением.

   Шкала признаков — от богатого к бедному, `null` — «на кадрах не видно»
   (это не то же самое, что «нет»). */
const MARKERS = {
  stone: ['слэб', 'керамогранит', 'нет'],            // цельная плита / плитка с повтором рисунка / нет
  joinery: ['на заказ', 'серийная', 'нет'],          // в размер помещения / стандартные модули / нет
  kitchen: ['интегрированная', 'встроенная', 'эконом', 'нет'],
  light: ['сценарный', 'базовый', 'нет'],            // «нет» — голые крюки в потолке
  furniture: ['полный', 'частичный', 'нет'],
  bath: ['камень и бренд', 'плитка', 'не отделан'],
  /* Добавлены после того, как на трёх десятках квартир стало ясно, чего не
     хватает: пол разделяет B и C лучше всех прочих признаков, а двери
     скрытого монтажа — самая дешёвая примета авторской работы.
     Записи без них остаются в силе: буква считается по видимым. */
  floor: ['массив ёлочкой', 'инженерная доска', 'ламинат', 'стяжка'],
  doors: ['скрытые', 'в наличнике', 'нет'],
};

/* Веса подобраны так, чтобы буква совпала с тем, как эти же квартиры
   читаются глазом: A — авторский премиум, B — качественный полный ремонт,
   C — жилая массовая отделка, D — отделка застройщика без мебели,
   E — бетон или белая коробка. */
const MARKER_POINTS = {
  stone: { 'слэб': 2, 'керамогранит': 1, 'нет': 0 },
  joinery: { 'на заказ': 2, 'серийная': 1, 'нет': 0 },
  kitchen: { 'интегрированная': 2, 'встроенная': 1, 'эконом': 0.5, 'нет': 0 },
  light: { 'сценарный': 2, 'базовый': 1, 'нет': 0 },
  furniture: { 'полный': 2, 'частичный': 1, 'нет': 0 },
  bath: { 'камень и бренд': 2, 'плитка': 1, 'не отделан': 0 },
  floor: { 'массив ёлочкой': 2, 'инженерная доска': 1.5, 'ламинат': 0.5, 'стяжка': 0 },
  doors: { 'скрытые': 2, 'в наличнике': 1, 'нет': 0 },
};

function gradeLevel(m) {
  m = m || {};
  for (const [k, v] of Object.entries(m)) {
    if (v != null && MARKERS[k] && !MARKERS[k].includes(v)) {
      throw new Error(`признак ${k}: «${v}» не из списка ${MARKERS[k].join(' / ')}`);
    }
  }
  /* Бетон и белая коробка — состояние, а не оценка: считать баллы там нечего. */
  if (m.kitchen === 'нет' && m.furniture === 'нет' && m.bath === 'не отделан') return 'E';
  /* Отделка есть, а жить нельзя: ни кухни, ни мебели, ни света. */
  if (m.kitchen === 'нет' && m.furniture === 'нет') return 'D';
  const known = Object.keys(MARKER_POINTS).filter((k) => m[k] != null);
  /* Меньше четырёх признаков — судить не по чему. Пустая оценка честнее
     выдуманной буквы, как и с медианой по когорте меньше четырёх лотов. */
  if (known.length < 4) return null;
  const avg = known.reduce((s, k) => s + MARKER_POINTS[k][m[k]], 0) / known.length;
  if (avg >= 1.7) return 'A';
  if (avg >= 1.2) return 'B';
  if (avg >= 0.6) return 'C';
  return 'D';
}

/* Чем подтверждён уровень — вопрос отдельный от самого уровня и не менее
   важный: рендер и фотография описывают одну и ту же квартиру с разной
   доказательной силой, и разница должна попадать в цену, а не теряться. */
const PROOFS = ['фото', 'рендер', 'смешанное', 'интерьера нет'];

/* Что говорят сами фотографии, без оглядки на текст объявления. Словарь тот
   же, что у completeness, чтобы два ответа можно было сравнить в лоб. */
function observedState(m) {
  m = m || {};
  if (m.kitchen == null && m.furniture == null) return 'неизвестно';
  if (m.kitchen === 'нет' && m.furniture === 'нет') return 'оболочка';
  if (m.furniture === 'нет') return 'оболочка';       // кухня есть, жить нельзя
  if (m.furniture === 'полный' && m.kitchen && m.kitchen !== 'нет') return 'под ключ';
  return 'неизвестно';
}

function gradeRecord(lot, g) {
  if (g.proof && !PROOFS.includes(g.proof)) {
    throw new Error(`подтверждение: «${g.proof}» не из списка ${PROOFS.join(' / ')}`);
  }
  const level = g.level || gradeLevel(g.markers);
  if (g.framesSeen != null && !Array.isArray(g.framesSeen)) {
    throw new Error('framesSeen: список номеров кадров, а не число');
  }
  const claimed = completeness(lot);
  const observed = observedState(g.markers);
  /* Ради этой строки всё и затевалось: продавец пишет «под ключ», на кадрах
     пустые комнаты. Побеждают кадры, но расхождение остаётся видимым — это
     сведение о продавце, а не только о квартире. */
  /* «Ремонт не сдан» и «оболочка» описывают одно и то же положение дел —
     въехать нельзя; текст здесь точнее кадров, а не противоречит им.
     Расхождение считается только между «жить можно» и «жить нельзя». */
  const rank = (s) => (s === 'под ключ' ? 1 : s === 'неизвестно' ? null : 0);
  const conflict = rank(observed) != null && rank(claimed) != null && rank(observed) !== rank(claimed);
  return {
    id: lot.id,
    /* Оценка ставится квартире, а не объявлению: объявление переклеивают, и
       без отпечатка оценка теряется вместе со старым id. */
    fingerprint: lot.fingerprint || null,
    address: `${lot.street || ''} ${lot.house || ''}`.trim(),
    area: lot.totalArea,
    price: lot.priceRub,
    pricePerM2: lot.priceRub && lot.totalArea ? Math.round(lot.priceRub / lot.totalArea) : null,
    state: conflict || claimed === 'неизвестно' ? observed : claimed,
    claimedState: claimed,
    observedState: observed,
    conflict,
    level,
    proof: g.proof || null,
    markers: g.markers || {},
    photosSeen: g.photosSeen ?? null,
    /* Какие кадры и в каком разрешении смотрели. Без этого оценку нельзя
       перепроверить: «премиум» без указания, что именно показало камень,
       остаётся впечатлением. */
    framesSeen: g.framesSeen || null,
    framesFull: g.framesFull || null,
    note: g.note || '',
    gradedAt: g.gradedAt,
  };
}

/* ---------- сколько стоит довести до «под ключ» ----------
   Это ДОПУЩЕНИЕ, а не измерение: у нас нет ни одной сметы. Оно вынесено
   сюда, чтобы всякий вывод «переоценён на N%» можно было пересчитать под
   другую цифру, а не искать её в тексте ответа.

   Диапазоны — за квадратный метр, включая отделку, кухню, технику, мебель и
   ведение проекта; срок работ 8-12 месяцев в цену не входит и обсуждается
   отдельно. */
const FINISH_COST = {
  'бизнес': { low: 130e3, mid: 155e3, high: 180e3 },
  'премиум': { low: 200e3, mid: 260e3, high: 320e3 },
  'делюкс': { low: 300e3, mid: 400e3, high: 500e3 },
};

function finishCost(area, tier = 'бизнес') {
  const c = FINISH_COST[tier];
  if (!c) throw new Error(`класс «${tier}» неизвестен: ${Object.keys(FINISH_COST).join(', ')}`);
  return { low: c.low * area, mid: c.mid * area, high: c.high * area, perM2: c };
}

/* Во сколько обойдётся метр после ремонта — то число, которое сравнивается с
   ценой готовой квартиры. Без него оболочка выглядит дешёвой. */
function loadedPricePerM2(lot, tier = 'бизнес') {
  if (!lot.priceRub || !lot.totalArea) return null;
  const c = finishCost(lot.totalArea, tier);
  return {
    low: Math.round((lot.priceRub + c.low) / lot.totalArea),
    mid: Math.round((lot.priceRub + c.mid) / lot.totalArea),
    high: Math.round((lot.priceRub + c.high) / lot.totalArea),
  };
}

/* Обратный ход: сколько должна стоить оболочка, чтобы после ремонта выйти
   вровень с готовой квартирой по цене метра. Разница с запрашиваемой ценой и
   есть переоценка. */
function fairShellPrice(area, finishedPricePerM2, tier = 'бизнес') {
  const c = finishCost(area, tier);
  return {
    low: Math.round(finishedPricePerM2 * area - c.high),
    mid: Math.round(finishedPricePerM2 * area - c.mid),
    high: Math.round(finishedPricePerM2 * area - c.low),
  };
}

/* ---------- внутри Садового кольца ----------
   Границы районов для центра бесполезны: Хамовники, Пресненский, Таганский и
   Замоскворечье лежат по обе стороны кольца, и «ЦАО» смешивает Софийскую
   набережную с Шмитовским проездом. Отсекать надо по географии.

   Контур — по площадям, через которые проходит само кольцо, с запада по
   часовой стрелке. Это приближение: реальная линия идёт по проездам, а не по
   прямым между площадями, и лоты в 100–150 м от кольца могут попасть не на ту
   сторону. Для отбора сопоставимых этой точности достаточно, для спора о
   конкретном адресе — нет. */
const GARDEN_RING = [
  [55.7607, 37.5806], // Кудринская площадь
  [55.7474, 37.5828], // Смоленская площадь
  [55.7379, 37.5896], // Зубовская площадь
  [55.7346, 37.5977], // Крымская площадь
  [55.7286, 37.6112], // Калужская (Октябрьская) площадь
  [55.7288, 37.6250], // Серпуховская площадь
  [55.7300, 37.6377], // Павелецкая площадь
  [55.7412, 37.6529], // Таганская площадь
  [55.7583, 37.6602], // Земляной Вал, Курская
  [55.7692, 37.6494], // Садовая-Черногрязская, Красные Ворота
  [55.7730, 37.6320], // Сухаревская площадь
  [55.7745, 37.6160], // Самотёчная площадь
  [55.7702, 37.5960], // Триумфальная площадь
];

function pointInPolygon(lat, lng, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [yi, xi] = poly[i], [yj, xj] = poly[j];
    if ((yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/* null, а не false, когда координат нет: «неизвестно» и «снаружи» — разное. */
function insideGardenRing(lot) {
  if (lot.lat == null || lot.lng == null) return null;
  return pointInPolygon(lot.lat, lot.lng, GARDEN_RING);
}

/* Насколько близко к линии кольца, в метрах. Контур приблизительный, и
   уточнить его нечем: во всей собранной выдаче нашлось два адреса на самом
   кольце — на калибровку этого не хватает. Раз точности нет, пусть будет
   хотя бы видно, где ответу верить нельзя.

   Расстояние считается по плоской близости: на широте Москвы градус долготы
   короче градуса широты примерно вдвое. Для сотен метров этого довольно. */
const M_PER_DEG_LAT = 111320;
const M_PER_DEG_LNG = 111320 * Math.cos(55.75 * Math.PI / 180);

function ringMargin(lot) {
  if (lot.lat == null || lot.lng == null) return null;
  const px = lot.lng * M_PER_DEG_LNG, py = lot.lat * M_PER_DEG_LAT;
  let best = Infinity;
  for (let i = 0, j = GARDEN_RING.length - 1; i < GARDEN_RING.length; j = i++) {
    const ax = GARDEN_RING[j][1] * M_PER_DEG_LNG, ay = GARDEN_RING[j][0] * M_PER_DEG_LAT;
    const bx = GARDEN_RING[i][1] * M_PER_DEG_LNG, by = GARDEN_RING[i][0] * M_PER_DEG_LAT;
    const dx = bx - ax, dy = by - ay;
    const len2 = dx * dx + dy * dy;
    const t = len2 ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2)) : 0;
    const cx = ax + t * dx, cy = ay + t * dy;
    best = Math.min(best, Math.hypot(px - cx, py - cy));
  }
  return Math.round(best);
}

/* Пограничная полоса, внутри которой машинному ответу верить нельзя. 300 м —
   порядок ошибки самого контура: он идёт прямыми между площадями, а кольцо
   изгибается по проездам. */
const RING_DOUBT_M = 300;

function ringVerdict(lot) {
  const inside = insideGardenRing(lot);
  if (inside === null) return { inside: null, margin: null, sure: false };
  const margin = ringMargin(lot);
  return { inside, margin, sure: margin > RING_DOUBT_M };
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
/* ---------- карточка объявления ----------
   Выдача поиска и карточка — два разных источника, и ни один не покрывает
   другой: 51 поле есть только в карточке, 98 — только в выдаче. Самое ценное
   из карточного — история цены. Она лежит НЕ внутри объекта оффера, а рядом
   с ним, в `offerData.priceChanges`; из-за этого её полгода никто не видел.

   Отдельного запроса не нужно: блок приходит сразу в разметке страницы.
   Индекс элемента внутри бандла — деталь сборки фронтенда, а не договор,
   поэтому ищем перебором по наличию `value.offerData.offer`. */
const CARD_GRAB = () => {
  const bundle = (window._cianConfig || {})['frontend-offer-card'] || [];
  const hit = bundle.find((x) => x && x.value && x.value.offerData && x.value.offerData.offer);
  if (!hit) return null;
  const d = hit.value.offerData, o = d.offer;
  return {
    id: o.id,
    price: (o.bargainTerms || {}).price ?? null,
    /* null — ключа не было вовсе, [] — ключ есть и пуст: разные вещи. */
    priceChanges: Array.isArray(d.priceChanges) ? d.priceChanges.map((p) => ({
      date: (p.changeTime || '').slice(0, 10),
      price: (p.priceData || {}).price ?? null,
    })) : null,
    viewsLine: (d.stats || {}).totalViewsFormattedString || null,
    bti: (d.bti || {}).houseData || null,
    tourUrl: (((d.tours || {}).externalTour) || {}).tourUrl || null,
    decoration: o.decoration ?? null,
    decorationInfo: (d.decorationInfo || {}).type ?? null,
    repairType: o.repairType ?? null,
    roomType: o.roomType ?? null,
    windowsViewType: o.windowsViewType ?? null,
    allRoomsArea: o.allRoomsArea ?? null,
    amenities: o.amenities || null,
    passengerLifts: o.passengerLiftsCount ?? null,
    cargoLifts: o.cargoLiftsCount ?? null,
    editDate: o.editDate ?? null,
    isImported: o.isImported ?? null,
    isFromBuilder: o.isFromBuilder ?? null,
    isDuplicate: o.isDuplicate ?? null,
    objectGuid: o.objectGuid ?? null,
  };
};

/* «2046 просмотров, 14 за сегодня» — строка для человека, а не число. */
function parseViews(line) {
  if (!line) return { total: null, today: null };
  const nums = String(line).match(/\d[\d\s ]*/g) || [];
  const n = (x) => (x == null ? null : parseInt(String(x).replace(/\D/g, ''), 10));
  return { total: n(nums[0]), today: /сегодня/.test(line) ? n(nums[1]) : null };
}

/* Собственная оценка Циан. Отдельный вызов, приходит не всегда: из восьми
   проверенных лотов пусто у трёх — у обеих оболочек и у продажи от
   застройщика. Модель, судя по всему, не видит отделки: авторский ремонт в
   Кутузовском XII она оценила на 28% ниже запрашиваемой цены, а обычные
   квартиры в ЮЗАО пометила «Хорошая цена». Поэтому это не истина, а ещё
   один независимый взгляд — полезный именно тем, что чужой. */
async function estimation(ctx, id) {
  try {
    const r = await ctx.request.get(
      `https://api.cian.ru/price-estimator/v1/get-estimation-and-trend-web/?cianOfferId=${id}`,
      { headers: { referer: `https://www.cian.ru/sale/flat/${id}/`, 'user-agent': UA, 'accept-language': 'ru-RU' },
        timeout: 40000 });
    const j = await r.json();
    const pi = j.priceInfo;
    if (!pi) return null;
    return {
      estimation: pi.estimation || null,
      range: pi.estimationRange || null,
      label: (pi.priceTag || {}).priceLabel || null,
      /* Числа Циан отдаёт строками для человека; для счёта берём их же
         короткие границы из priceTag. */
      lowMln: parseFloat(String((pi.priceTag || {}).estimationLowerBoundShort || '').replace(',', '.')) || null,
      highMln: parseFloat(String((pi.priceTag || {}).estimationUpperBoundShort || '').replace(',', '.')) || null,
    };
  } catch (e) { return null; }
}

async function readCard(ctx, id, attempts = 3) {
  let why = 'не прочиталась';
  for (let i = 1; i <= attempts; i++) {
    const p = await ctx.newPage();
    try {
      const resp = await p.goto(`https://www.cian.ru/sale/flat/${id}/`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await p.waitForTimeout(3000);
      if (/captcha/i.test(p.url())) { why = 'капча'; }
      else if (resp && resp.status() === 404) { await p.close(); return { error: 'объявление снято (404)' }; }
      else {
        const r = await p.evaluate(CARD_GRAB);
        if (!r) why = 'разметка не разобралась';
        /* Сверка на всякий случай: страница могла увести редиректом на
           другой лот, и тогда мы бы записали чужие данные своему id. */
        else if (Number(r.id) !== Number(id)) why = `страница отдала чужой лот ${r.id}`;
        else { await p.close(); return { ...r, views: parseViews(r.viewsLine) }; }
      }
    } catch (e) { why = e.message.split('\n')[0]; }
    await p.close();
    await sleep(9000);   // капча ловится примерно на каждой пятой карточке
  }
  return { error: why };
}

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

  /* Единственная команда, которой сеть не нужна вовсе: поднимать ради неё
     браузер и греть куки — платить полминуты ни за что. */
  if (cmd === 'archive') {
    const arc = loadArchive(a.archive || 'docs/cian/archive.json');
    const s = archiveStat(arc);
    log(`архив: ${s.flats} квартир, снимки ${s.first} … ${s.last} (${s.dates.length} дат)`);
    log(`встречались больше одного раза: ${s.repeat}` +
      (s.repeat ? '' : ' — пока ни одна: срок экспозиции держится только на близнецах'));
    log(`не попали в последний снимок, хотя их запрос снимался: ${s.stale} — кандидаты в ушедшие, подтверждать командой refresh`);
    if (s.blind) log(`вне поля зрения (запрос не снимался или имени нет): ${s.blind} — про них снимок не говорит ничего`);
    const src = Object.entries(s.sources).sort((x, y) => y[1] - x[1]);
    if (src.length) {
      log(`\nпо запросам (внутри них и считается пропажа):`);
      src.forEach(([k, v]) => log(`  ${String(v).padStart(5)}  ${k}`));
      const noSrc = s.flats - Object.values(s.sources).reduce((x, y) => x + y, 0);
      if (noSrc > 0) log(`  ${String(noSrc).padStart(5)}  без имени запроса (залив из файлов) — пропажа по ним не считается`);
    } else log('\nни у одной квартиры нет имени запроса: весь архив залит из файлов, пропажу считать не по чему');
    if (s.moved) {
      log(`\nцена одного и того же объявления менялась между снимками: ${s.moved}`);
      s.priceMoves.slice(0, 10).forEach((m) => log(`  ${m.id}  ${m.from.toLocaleString('ru-RU')} -> ${m.to.toLocaleString('ru-RU')} ₽` +
        `  (${((m.to - m.from) / m.from * 100).toFixed(1)}%)  ${m.seen}  ${m.address}`));
    } else log('\nдвижения цен пока не видно: для этого нужны два снимка одной и той же квартиры');
    process.exit(0);
  }

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
      let { count, aggregated, lots } = await collect(ctx, q, pages, !!a.all);
      if (a.similar !== 'нет') {
        const simPages = parseInt(a['similar-pages'] || '4', 10);
        const { added, dropped, leaders, cut, failed, short } = await expandSimilar(ctx, q, lots, simPages);
        if (leaders) {
          log(`раскрыл схлопнутые группы: лидеров ${leaders}, добавилось ${added.length}` +
              (dropped ? `, отброшено не подходящих под запрос ${dropped}` : ''));
          lots = lots.concat(added);
          /* Обрезка обязана быть слышной. Пока о ней молчали, «раскрыл
             группы» читалось как «раскрыл целиком», а за потолком в четыре
             страницы могло остаться сколько угодно. */
          if (cut.length) {
            log(`  ! ${cut.length} групп упёрлись в потолок ${simPages} страниц — за ним ещё есть:`);
            cut.slice(0, 5).forEach((c) => log(`      лидер ${c.id}: прочитано ${c.seen}` +
              (c.promised ? ` из обещанных ${c.promised}` : '')));
            log(`      добрать: --similar-pages ${simPages * 2}`);
          }
          if (failed.length) {
            log(`  ! ${failed.length} групп не перечислены: ручка не ответила`);
            failed.slice(0, 5).forEach((f) => log(`      лидер ${f.id}: прочитано ${f.seen} за ${f.pages} стр. — ${f.why}`));
          }
          if (short.length) {
            log(`  ! ${short.length} групп отдали меньше обещанного (Циан считает similar по-своему):`);
            short.slice(0, 5).forEach((s) => log(`      лидер ${s.id}: обещано ${s.promised}, пришло ${s.seen}`));
          }
        }
      }
      /* Фильтры Циан текут: `apartment=0` пропускает апартаменты, и чем
         глубже пагинация, тем больше — на районном запросе 52 из 274.
         Флаги --no-apartments и --min-year были частным случаем этого;
         теперь весь запрос сверяется со своей же выдачей. */
      if (a.strict !== 'нет') {
        const before = lots.length;
        const bad = lots.filter((l) => !matchesQuery(l, q));
        if (bad.length) {
          lots = lots.filter((l) => matchesQuery(l, q));
          const why = {};
          bad.forEach((l) => {
            if (q.apartment && q.apartment.value === false && l.isApartments) why['апартаменты'] = (why['апартаменты'] || 0) + 1;
            else if (q.room && l.rooms != null && !q.room.value.includes(l.rooms)) why['другая комнатность'] = (why['другая комнатность'] || 0) + 1;
            else why['вне заданных границ'] = (why['вне заданных границ'] || 0) + 1;
          });
          log(`фильтры Циан протекли: ${before - lots.length} лотов не подходят под собственный запрос ` +
              `(${Object.entries(why).map(([k, v]) => `${k} ${v}`).join(', ')})`);
        }
      }
      const enumerated = lots.length;
      log(`Циан заявляет ${count}, перечислимо за проход ${aggregated ?? '?'}, реально перечислено ${enumerated}` +
          (count && enumerated < count ? ' — offerCount считает ДО схлопывания похожих объявлений' : ''));
      /* Недобор молчаливым быть не должен. На районном запросе развёртка с
         перебором сортировок дала 238 лотов против 168 у обычного обхода —
         на 42% больше, и без подсказки этой разницы никто не заметит. */
      /* Считать недобор надо от aggregatedCount, а не от offerCount: второй
         завышен схлопыванием и недостижим подряд в принципе. */
      const ceiling = aggregated || count;
      if (ceiling && enumerated < ceiling * 0.9) {
        log(`перечислено ${Math.round(enumerated / ceiling * 100)}% от потолка одного прохода — добавьте --pages/--all.`);
      }
      if (aggregated && count > aggregated * 1.15) {
        log(`заявлено на ${count - aggregated} больше, чем можно перечислить подряд: схлопывание похожих объявлений.`);
        log(`Разбить его умеет только дробление:  node tools/cian/cian.js sweep --query ${a.query || '<запрос>'} --all --limit 120`);
      }
      const before = lots.length;
      if (a['no-apartments']) lots = lots.filter((l) => !l.isApartments);
      if (a['min-year']) {
        const y = parseInt(a['min-year'], 10);
        const unknown = lots.filter((l) => buildingYear(l) == null).length;
        lots = lots.filter((l) => { const b = buildingYear(l); return b == null || b >= y; });
        if (unknown) log(`год постройки неизвестен у ${unknown} лотов — оставлены, отсев по году их не касается`);
      }
      if (a['max-area']) lots = lots.filter((l) => l.totalArea && l.totalArea <= parseFloat(a['max-area']));
      if (a['garden-ring']) {
        const noCoords = lots.filter((l) => insideGardenRing(l) === null).length;
        const doubt = lots.filter((l) => { const v = ringVerdict(l); return v.inside !== null && !v.sure; });
        lots = lots.filter((l) => insideGardenRing(l) === true);
        if (noCoords) log(`координат нет у ${noCoords} лотов — в отбор по кольцу не попали`);
        if (doubt.length) {
          log(`у самой линии кольца (±${RING_DOUBT_M} м), машинному ответу верить нельзя — ${doubt.length}:`);
          doubt.forEach((l) => { const v = ringVerdict(l);
            log(`  ${l.id}  ${v.inside ? 'внутри' : 'снаружи'} с запасом ${v.margin} м  ${l.street || ''} ${l.house || ''}`); });
        }
      }
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
      const { seen, declared, aggregated } = await sweep(ctx, q, parseInt(a.limit || '250', 10), maxPages, !!a.all);
      // по умолчанию доопределяем комплектность фильтром: без неё сравнение цен врёт
      if (a.resolve !== '0') await resolveDecoration(ctx, q, seen, Math.min(maxPages, 6));
      let { lots } = finishSweep(seen, { count: declared });
      log(`\nзаявлено ${declared}, за один проход перечислимо ${aggregated ?? '?'}, собрано ${lots.length}` +
          (aggregated ? ` — против ${aggregated} без дробления` : ''));
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
      const only = a.ids ? String(a.ids).split(',').map(Number) : null;
      let lots = [];
      if (a.from) {
        /* Проверять уже собранное, не гоняя поиск заново. Раньше, чтобы
           посмотреть десять известных лотов, приходилось воспроизводить
           запрос, который их содержит, — и вся работа по контактным листам
           уходила в одноразовые скрипты мимо инструмента. */
        for (const f of String(a.from).split(',')) {
          const d = JSON.parse(fs.readFileSync(f.trim(), 'utf8'));
          for (const l of (d.lots || d.flats || (Array.isArray(d) ? d : []))) {
            if (!lots.some((x) => x.id === l.id)) lots.push(l);
          }
        }
        log(`из файлов: ${lots.length} лотов`);
      } else if (only && !a.query) {
        /* Раньше, чтобы посмотреть десять известных лотов, приходилось
           воспроизводить поиск, который их содержит. Теперь спрашиваем прямо. */
        const r = await offersByIds(ctx, only);
        lots = r.offers.map(normalize);
        log(`по номерам: ${lots.length} из ${only.length}`);
        if (r.bad.length) log(`  не номера, пропущены: ${r.bad.join(', ')}`);
        if (r.failed.length) log(`  НЕ ПРОВЕРЕНЫ (ручка не ответила): ${r.failed.join(', ')}`);
        if (r.missing.length) log(`  не нашлись — сняты или чужие номера: ${r.missing.join(', ')}`);
      } else {
        lots = (await collect(ctx, loadQuery(a.query), parseInt(a.pages || '2', 10), false)).lots;
      }
      if (only) {
        const missing = only.filter((id) => !lots.some((l) => l.id === id));
        lots = lots.filter((l) => only.includes(l.id));
        /* Молчаливая потеря — та же болезнь, что с отсевом по году: если
           запрошенного лота в источнике нет, об этом надо сказать. */
        if (missing.length) log(`не нашлось в источнике: ${missing.join(', ')}`);
        lots.sort((x, y) => only.indexOf(x.id) - only.indexOf(y.id));
      }
      const vGrades = loadGrades(a.grades || 'docs/cian/grades.json').flats;
      const noPhotos = lots.filter((l) => !(l.photos || []).length).map((l) => l.id);
      if (noPhotos.length && a.photos !== '0') log(`без фотографий в источнике: ${noPhotos.join(', ')}`);
      lots = lots.slice(0, parseInt(a.limit || String(only ? only.length : 8), 10));
      const dir = a.dir || 'cian-photos';
      const report = [];
      for (const l of lots) {
        const r = assessRepair(l);
        const nPhoto = parseInt(a.photos || '9', 10);
        let files = [], sheet = null;
        if (a.frames) {
          /* Второй шаг проверки: выбранные кадры в исходном разрешении.
             Контактный лист отвечает на вопрос «что в квартире есть», а
             «настоящее ли это» решается только в оригинале — на мелком
             кадре рендер один раз уже сошёл за съёмку. */
          const want = String(a.frames).split(',').map((x) => parseInt(x, 10)).filter(Boolean);
          fs.mkdirSync(`${dir}/${l.id}`, { recursive: true });
          for (const n of want) {
            const url = (l.photos || [])[n - 1];
            if (!url) { log(`   кадра ${n} нет: всего ${(l.photos || []).length}`); continue; }
            try {
              const r = await ctx.request.get(url, { timeout: 30000 });
              const f = `${dir}/${l.id}/кадр-${String(n).padStart(2, '0')}.jpg`;
              fs.writeFileSync(f, await r.body());
              files.push(f);
            } catch (e) { log(`   кадр ${n} не скачался`); }
          }
        } else if (a.photos !== '0') {
          if (a.files) files = await fetchPhotos(ctx, l, `${dir}/${l.id}`, nPhoto);
          else sheet = await contactSheet(ctx, page, l, `${dir}/${l.id}.jpg`, nPhoto, parseInt(a.cols || '3', 10));
        }
        const ev = finishEvidence(l);
        const gr = gradeFor(vGrades, l);
        report.push({ id: l.id, url: l.url, price: l.priceRub, area: l.totalArea, ...r, evidence: ev,
          completeness: completeness(l), grade: gr || null, sheet, photos: files });
        log(`\n${l.id}  ${l.rooms}к ${l.totalArea} м²  ${(l.priceRub || 0).toLocaleString('ru-RU')} ₽  — ${r.verdict}, ${completeness(l)}`);
        /* Если квартиру уже смотрели глазами, слово за записанной оценкой, а
           не за разбором текста: текст — предварительный отсев, и не более. */
        if (gr) {
          log(`   ОЦЕНКА ПО ФОТО (${gr.gradedAt}): отделка ${gr.level || '—'}, ${gr.state}, подтверждено: ${gr.proof || '—'}` +
              (gr.conflict ? '  ! текст объявления этому противоречит' : ''));
          if (gr.note) log(`   ${gr.note}`);
          const grew = galleryGrew(gr, l);
          if (grew) log(`   ГАЛЕРЕЯ ВЫРОСЛА: смотрели ${grew.was} кадров, сейчас ${grew.now} — оценку надо пересмотреть`);
        }
        if (ev.unfinished) log('   ремонт по тексту ещё не завершён');
        log(`   комплектация: ${ev.spelledOut ? 'расписана по маркам' : ev.brands.length ? 'марки названы частично' : 'марки не названы'}` +
          (ev.brands.length ? ` (${ev.categories.join(', ')}: ${ev.brands.slice(0, 8).join(', ')})` : '') +
          `; мебель и техника ${ev.furnished ? 'заявлены' : 'не заявлены'}`);
        if (r.red.length) log(`   против: ${r.red.join('; ')}`);
        if (r.yellow.length) log(`   насторожило: ${r.yellow.join('; ')}`);
        if (r.green.length) log(`   за: ${r.green.join('; ')}`);
        if (r.flags.length) log(`   поля: ${r.flags.join('; ')}`);
        if (sheet) log(`   контактный лист: ${sheet}`);
        else if (files.length) log(`   кадры в исходном разрешении: ${files.length} шт. в ${dir}/${l.id}/`);
      }
      if (a.out) fs.writeFileSync(a.out, JSON.stringify(report, null, 2) + '\n');
      log('\nТекст и поля — только предварительный отсев. Окончательный ответ дают фотографии.');

    } else if (cmd === 'snapshot' || cmd === 'exposure') {
      const today = new Date().toISOString().slice(0, 10);
      const archivePath = a.archive || 'docs/cian/archive.json';
      const arc = loadArchive(archivePath);
      let lots = [], q = null;
      const perQuery = [];
      if (a.from) {
        /* Уже собранная выдача кладётся в архив без сети. Иначе всё, что
           набрано по ходу разбора, пропадает, а архив растёт по одному
           запросу за раз и годами не догоняет то, что мы и так видели. */
        for (const f of String(a.from).split(',')) {
          const d = JSON.parse(fs.readFileSync(f.trim(), 'utf8'));
          const part = d.lots || d.flats || (Array.isArray(d) ? d : []);
          part.forEach((l) => { if (!lots.some((x) => x.id === l.id)) lots.push(l); });
          log(`  ${f.trim()}: ${part.length}`);
        }
        log(`из файлов: ${lots.length} объявлений`);
      } else if (a.queries) {
        /* Список запросов, за которыми следим постоянно. Снимок по одному
           запросу почти бесполезен: он видит только свой угол рынка. */
        const list = JSON.parse(fs.readFileSync(a.queries, 'utf8'));
        for (const item of (list.queries || list)) {
          log(`\n[${item.name || item.query}]`);
          const sub = typeof item.jsonQuery === 'object' ? item.jsonQuery : loadQuery(item.query);
          const r = await collect(ctx, sub, parseInt(a.pages || '3', 10), !!a.all);
          /* Каждый запрос кладётся в архив под своим именем. Свалить всё в
             одну кучу значит потерять единственное, внутри чего пропажу
             можно считать: без имени запроса «ушедших» не отличить от тех,
             кого этот запрос никогда и не видел. */
          perQuery.push({ name: item.name || item.query, lots: r.lots });
          r.lots.forEach((l) => { if (!lots.some((x) => x.id === l.id)) lots.push(l); });
        }
        log(`\nвсего по ${(list.queries || list).length} запросам: ${lots.length}`);
      } else {
        q = loadQuery(a.query);
        lots = (await collect(ctx, q, parseInt(a.pages || '3', 10), !!a.all)).lots;
      }

      if (cmd === 'snapshot') {
        /* Имя запроса — то, внутри чего считается пропажа. У залива из файлов
           его нет, и это не оплошность: судить о пропаже там не по чему. */
        const source = a.source || (a.from ? null : a.queries ? null : (a.query || null));
        /* Список запросов сливается в архив по одному, каждый под своим
           именем: пропажа считается внутри запроса, и общий котёл её съедал. */
        const runs = perQuery.length ? perQuery.map((p) => ({ name: p.name, lots: p.lots }))
          : [{ name: source, lots }];
        let fresh = 0, updated = 0;
        const changes = [], goneAll = [];
        for (const run of runs) {
          const r = mergeArchive(arc, run.lots, today, run.name);
          fresh += r.fresh; updated += r.updated;
          changes.push(...r.changes);
          if (r.gone.length) goneAll.push({ name: run.name, gone: r.gone });
          if (perQuery.length) {
            log(`  [${run.name}] +${r.fresh} новых, ${r.updated} подтверждено` +
                (r.gone.length ? `, пропали ${r.gone.length}` : ''));
          }
        }
        fs.writeFileSync(archivePath, JSON.stringify(arc, null, 2) + '\n');
        log(`архив ${archivePath}: +${fresh} новых квартир, ${updated} подтверждено, всего ${Object.keys(arc.flats).length}`);
        if (changes.length) {
          log(`\nцена изменилась с прошлого снимка (${changes.length}):`);
          changes.slice(0, 10).forEach((c) => log(`  ${c.id}  ${c.from.toLocaleString('ru-RU')} -> ${c.to.toLocaleString('ru-RU')} ₽` +
            `  (${c.to < c.from ? '' : '+'}${((c.to - c.from) / c.from * 100).toFixed(1)}%)  ${c.address}`));
        }
        if (goneAll.length) {
          log('\nбыли в этом же запросе, теперь нет (сняты, проданы или ушли из фильтра):');
          goneAll.forEach(({ name, gone }) => {
            log(`  [${name}] ${gone.length}`);
            gone.slice(0, 8).forEach((g) => log(`      ${g.address} — в последний раз ${g.lastSeen}`));
          });
        } else if (!runs.some((r) => r.name)) {
          log('\nпропажу не считаю: снимок без имени запроса, сравнивать не с чем');
        }
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
                  region: (q && q.region) || { type: 'terms', value: [1] },
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

    } else if (cmd === 'compare') {
      /* Положение лота в когорте я считал руками в одноразовых скриптах, и
         поэтому ни один разбор не повторялся дважды одинаково. Здесь тот же
         счёт, но с разделением по комплектности и по проверенному уровню
         отделки: без него медиана смешивает бетон с квартирой под ключ. */
      const raw = [];
      for (const f of String(a.cohort).split(',')) {
        const src = JSON.parse(fs.readFileSync(f.trim(), 'utf8'));
        for (const l of (src.lots || src.flats || (Array.isArray(src) ? src : []))) {
          if (!raw.some((x) => x.id === l.id)) raw.push(l);
        }
      }
      /* Лоты без корпуса в адресе схлопнуть нельзя — их четверть выдачи, и
         раньше они молча выпадали из сравнения вместе со своими ценами.
         Схлопнутые и одиночные идут в когорту вместе. */
      const { flats: collapsed, loose } = dedupe(raw);
      const flats = collapsed.concat(loose);
      if (loose.length) log(`без корпуса в адресе: ${loose.length} — в когорте оставлены, но дубли среди них не схлопнуть`);
      const grades = loadGrades(a.grades || 'docs/cian/grades.json').flats;
      const ppm = (l) => (l.priceRub && l.totalArea ? l.priceRub / l.totalArea : null);
      /* Искать надо и среди схлопнутых: та же квартира могла попасть в
         когорту под другим объявлением, и «не найден» было бы неправдой. */
      const target = flats.find((l) => String(l.id) === String(a.lot))
        || flats.find((l) => (l.alsoListedAs || []).some((x) => String(x.id ?? x) === String(a.lot)))
        || raw.find((l) => String(l.id) === String(a.lot));
      if (target && String(target.id) !== String(a.lot)) {
        log(`${a.lot} — то же самое, что ${target.id}: одна квартира, разные объявления\n`);
      }
      if (!target) { log(`лот ${a.lot} не найден ни в одном из файлов когорты`); }
      else {
        const g = gradeFor(grades, target) || {};
        const arc = loadArchive(a.archive || 'docs/cian/archive.json');
        /* История цены живёт в архиве по отпечатку квартиры и переживает
           переклейку объявления. Снижали ли цену — сигнал не слабее срока
           экспозиции, а часто и сильнее. */
        const histOf = (l) => {
          /* Ищем и по отпечатку, и по номеру объявления: собранный файл мог
             прийти без отпечатка, а история в архиве всё равно есть. */
          let e = l.fingerprint && arc.flats[l.fingerprint];
          if (!e) {
            const ids = [l.id, ...((l.alsoListedAs || []).map((x) => (x && x.id) ?? x))];
            e = Object.values(arc.flats).find((f) => f.priceHistory
              && ids.some((id) => f.priceHistory[String(id)]));
          }
          const all = mergedPriceHistory(e);
          if (!all) return null;
          return { from: all[0], to: all[all.length - 1], n: all.length,
            pct: (all[all.length - 1].price - all[0].price) / all[0].price * 100 };
        };
        const tPpm = ppm(target);
        const tier = a.tier || 'бизнес';
        log(`${target.id}  ${target.rooms || 'ст'}к ${target.totalArea} м²  ${(target.priceRub || 0).toLocaleString('ru-RU')} ₽  ` +
            `= ${Math.round(tPpm).toLocaleString('ru-RU')} ₽/м²  ${target.street || ''} ${target.house || ''}`);
        const th = histOf(target);
        if (th) {
          log(`цена: ${th.n} изменений, ${th.from.date} ${(th.from.price / 1e6).toFixed(1)} млн -> ` +
              `${th.to.date} ${(th.to.price / 1e6).toFixed(1)} млн  (${th.pct > 0 ? '+' : ''}${th.pct.toFixed(1)}%)`);
        }
        log(`состояние: ${g.state || completeness(target)}` +
            (g.level ? `, отделка ${g.level}` : ', отделка не оценивалась') +
            (g.proof ? `, подтверждено: ${g.proof}` : '') +
            (g.conflict ? '  ! текст объявления расходится с фотографиями' : ''));
        const pool = flats.filter((l) => l.id !== target.id && ppm(l));
        log(`\nкогорта: ${raw.length} объявлений -> ${flats.length} квартир`);
        /* Когорту задаёт человек, и склеить в неё пол-Москвы легко. Состав по
           районам печатается, чтобы смешение было видно до, а не после
           вывода о медиане. */
        const byD = {};
        flats.forEach((l) => { const d = l.district || 'без района'; byD[d] = (byD[d] || 0) + 1; });
        const parts = Object.entries(byD).sort((x, y) => y[1] - x[1]);
        log(`  районы: ${parts.slice(0, 6).map(([d, n]) => `${d} ${n}`).join(', ')}` +
            (parts.length > 6 ? ` и ещё ${parts.length - 6}` : ''));
        if (parts.length > 3) log('  ! когорта из нескольких районов — медиана по ней смешивает разные рынки');

        const band = (name, xs) => {
          if (xs.length < 4) { log(`  ${name.padEnd(34)} ${String(xs.length).padStart(3)} — меньше четырёх, медиану не считаю`); return; }
          const m = median(xs.map(ppm));
          log(`  ${name.padEnd(34)} ${String(xs.length).padStart(3)} шт  медиана ${String(Math.round(m).toLocaleString('ru-RU')).padStart(10)} ₽/м²  ` +
              `наш ${tPpm > m ? '+' : ''}${((tPpm / m - 1) * 100).toFixed(0)}%`);
        };
        log('медианы по когорте:');
        band('все', pool);
        for (const st of ['под ключ', 'оболочка', 'ремонт не сдан', 'неизвестно']) {
          band(`комплектность: ${st}`, pool.filter((l) => { const x = gradeFor(grades, l); return x ? x.state === st : completeness(l) === st; }));
        }
        for (const lv of ['A', 'B', 'C', 'D', 'E']) {
          band(`отделка ${lv} (по фотографиям)`, pool.filter((l) => { const x = gradeFor(grades, l); return x && x.level === lv && x.proof === 'фото'; }));
        }

        const shell = ['оболочка', 'ремонт не сдан'].includes(g.state || completeness(target));
        if (shell) {
          const loaded = loadedPricePerM2(target, tier);
          const c = finishCost(target.totalArea, tier);
          log(`\nэто не готовая квартира. Ремонт класса «${tier}» — допущение ${(c.perM2.low / 1e3).toFixed(0)}–${(c.perM2.high / 1e3).toFixed(0)} тыс ₽/м²:`);
          log(`  довести до «под ключ»: ${(c.low / 1e6).toFixed(1)}–${(c.high / 1e6).toFixed(1)} млн, 8–12 месяцев`);
          log(`  метр после ремонта: ${loaded.low.toLocaleString('ru-RU')} – ${loaded.high.toLocaleString('ru-RU')} ₽/м²`);
          const turnkey = pool.filter((l) => { const x = gradeFor(grades, l); return x ? x.state === 'под ключ' : completeness(l) === 'под ключ'; });
          if (turnkey.length >= 4) {
            const m = median(turnkey.map(ppm));
            const fair = fairShellPrice(target.totalArea, m, tier);
            log(`  готовая медиана ${Math.round(m).toLocaleString('ru-RU')} ₽/м² -> оболочка должна стоить ` +
                `${(fair.low / 1e6).toFixed(1)}–${(fair.high / 1e6).toFixed(1)} млн, просят ${(target.priceRub / 1e6).toFixed(1)}`);
            const over = (target.priceRub / fair.mid - 1) * 100;
            log(`  переоценка: ${over > 0 ? '+' : ''}${over.toFixed(0)}% (${((target.priceRub - fair.mid) / 1e6).toFixed(1)} млн)`);
          } else log('  готовых в когорте меньше четырёх — не с чем сводить');
        }

        const near = pool.filter((l) => Math.abs(l.totalArea - target.totalArea) / target.totalArea <= 0.15)
          .sort((x, y) => ppm(y) - ppm(x));
        log(`\nближайшие по площади (±15%), ${near.length}:`);
        for (const l of near.slice(0, 14)) {
          const x = gradeFor(grades, l) || {};
          const h = histOf(l);
          log(`  ${String(Math.round(ppm(l)).toLocaleString('ru-RU')).padStart(10)} ${String(l.id).padEnd(11)} ` +
              `${String(l.totalArea).padStart(6)} м² ${String((l.priceRub / 1e6).toFixed(1)).padStart(6)} млн ` +
              `${String(l.daysOnMarket ?? '').padStart(4)}д ${(x.level || '—').padEnd(2)} ${(x.proof || '').padEnd(13)} ` +
              `${(x.state || completeness(l)).padEnd(14)} ${h ? `${h.pct > 0 ? '+' : ''}${h.pct.toFixed(0)}% ` : '     '}` +
              `${(l.street || '')} ${(l.house || '')}`);
        }
        const gaps = near.slice(0, 6).map((l) => ({ l, g: comparabilityGaps(target, l) })).filter((x) => x.g.length);
        if (gaps.length) {
          log('\nчем эти лоты не сопоставимы с нашим:');
          gaps.forEach((x) => log(`  ${x.l.id}: ${x.g.join('; ')}`));
        }
      }

    } else if (cmd === 'grade') {
      /* Оценка отделки живёт в файле, а не в переписке: иначе каждая новая
         сессия оценивает те же квартиры заново и приходит к другой букве. */
      const store = loadGrades(a.store || 'docs/cian/grades.json');
      if (a.template) {
        /* Заготовка под заполнение. Приём применяется тем чаще, чем меньше
           вокруг него возни: набирать восемь ключей руками по памяти —
           верный способ забросить его через неделю. */
        const ids = String(a.template).split(',').map(Number).filter(Boolean);
        const known = a.from ? (() => { const d = JSON.parse(fs.readFileSync(a.from, 'utf8'));
          return d.lots || d.flats || (Array.isArray(d) ? d : []); })() : [];
        const out = {};
        for (const id of ids) {
          const l = known.find((x) => x.id === id) || {};
          out[id] = {
            proof: `<${PROOFS.join(' | ')}>`,
            photosSeen: l.photosCount ?? null,
            framesSeen: [],
            framesFull: [],
            markers: Object.fromEntries(Object.entries(MARKERS)
              .map(([k, v]) => [k, `<${v.join(' | ')} | null>`])),
            note: '',
          };
        }
        process.stdout.write(JSON.stringify(out, null, 2) + '\n');
        log('\nЗаполнить по docs/cian/photo.md, null — «на кадрах не видно».');
        log(`Кадры в оригинале:  node tools/cian/cian.js verify --ids ${ids.join(',')} --frames 3,6,11 --dir sheets`);
      } else if (a.check) {
        /* Хранилище оценок должно возражать само. Три претензии, каждая
           уже случалась на живых данных. */
        const rows = Object.values(store.flats);
        const say = (t, xs, how) => {
          if (!xs.length) return;
          log(`\n${t} (${xs.length}):`);
          xs.forEach((r) => log(`  ${String(r.id).padEnd(11)} ${(r.level || '—').padEnd(2)} ${(r.proof || '').padEnd(13)} ${how(r)}  ${r.address}`));
        };
        say('оценка поставлена по картинке, а не по квартире',
          rows.filter((r) => r.level && r.proof === 'рендер'),
          () => 'буква описывает визуализацию — в сравнении цен это другой товар');
        say('буква есть, а кадров интерьера нет',
          rows.filter((r) => r.level && r.proof === 'интерьера нет'),
          () => 'признак поставлен выводом из текста, а не увиден');
        say('текст объявления расходится с кадрами',
          rows.filter((r) => r.conflict),
          (r) => `по тексту «${r.claimedState}», на кадрах «${r.observedState}»`);
        say('премиальные признаки при пустой квартире',
          rows.filter((r) => r.state === 'оболочка' && ['A', 'B'].includes(r.level)),
          (r) => 'отделка есть, жить нельзя — сравнивать только с такими же');
        /* Одинаковые признаки, разные буквы означали бы, что арифметика
           разъехалась с записями. */
        const byKey = {};
        rows.forEach((r) => {
          if (!r.markers) return;
          const k = JSON.stringify(Object.entries(r.markers).sort());
          (byKey[k] = byKey[k] || []).push(r);
        });
        const split = Object.values(byKey).filter((g) => new Set(g.map((r) => r.level)).size > 1);
        if (split.length) {
          log(`\nодинаковые признаки, разные буквы (${split.length}) — так быть не может:`);
          split.forEach((g) => log(`  ${g.map((r) => `${r.id}:${r.level}`).join(' ')}`));
        }
        const thin = rows.filter((r) => r.markers && Object.values(r.markers).filter((v) => v != null).length < 4);
        if (thin.length) log(`\nпризнаков меньше четырёх, буквы нет (${thin.length}): ${thin.map((r) => r.id).join(', ')}`);
        /* Средний балл по шести признакам и по восьми — разные шкалы: пол и
           двери оба стоят по два, и стоит их дописать, как средний ползёт
           вверх. На Усачева 11Д ровно это и перевело B в A. Буква на неполном
           наборе сравнима с такой же неполной, но не с восьмёркой. */
        const part = rows.filter((r) => r.level && r.markers &&
          Object.values(r.markers).filter((v) => v != null).length < 6);
        if (part.length) {
          log(`\nбуква на пяти признаках и меньше — черновая (${part.length}):`);
          part.forEach((r) => log(`  ${String(r.id).padEnd(11)} ${r.level}  ` +
            `${Object.values(r.markers).filter((v) => v != null).length} признаков  ${r.address}`));
        }
        const noFull = rows.filter((r) => !r.framesFull || !r.framesFull.length);
        log(`\nбез кадров в исходном разрешении: ${noFull.length} из ${rows.length} — второй шаг проверки пропущен`);
      } else if (a.list || !a.marks) {
        const rows = Object.values(store.flats).sort((x, y) => (y.pricePerM2 || 0) - (x.pricePerM2 || 0));
        log(`оценок в ${a.store || 'docs/cian/grades.json'}: ${rows.length}` + (store.updated ? `, обновлено ${store.updated}` : ''));
        for (const r of rows) {
          log(`  ${String(r.level || '—').padEnd(2)} ${String(r.proof || '').padEnd(13)} ${String(r.pricePerM2 || '').padStart(9)} ₽/м²  ` +
              `${String(r.id).padEnd(11)} ${String(r.area || '').padStart(6)} м²  ${(r.state || '').padEnd(14)}` +
              `${r.conflict ? ' ! текст врёт ' : '             '}${r.address}`);
        }
        if (!rows.length) log('  пусто: оценки ставятся командой grade --lots <файл> --marks <файл>');
      } else {
        const marks = JSON.parse(fs.readFileSync(a.marks, 'utf8'));
        const src = a.lots ? JSON.parse(fs.readFileSync(a.lots, 'utf8')) : {};
        const lots = src.lots || src.flats || (Array.isArray(src) ? src : []);
        const today = new Date().toISOString().slice(0, 10);
        let added = 0, changed = 0;
        for (const [id, g] of Object.entries(marks)) {
          const lot = lots.find((l) => String(l.id) === String(id)) || g.lot || { id: Number(id) };
          let rec;
          try { rec = gradeRecord(lot, { ...g, gradedAt: g.gradedAt || today }); }
          catch (e) { log(`  ! ${id}: ${e.message}`); continue; }
          const prev = store.flats[id];
          if (!prev) added++;
          else if (prev.level !== rec.level || prev.proof !== rec.proof) {
            changed++;
            log(`  ${id}: ${prev.level || '—'}/${prev.proof || '—'} -> ${rec.level || '—'}/${rec.proof || '—'}`);
          }
          store.flats[id] = { ...prev, ...rec };
        }
        store.updated = today;
        fs.writeFileSync(a.store || 'docs/cian/grades.json', JSON.stringify(store, null, 2) + '\n');
        log(`оценок: +${added} новых, ${changed} пересмотрено, всего ${Object.keys(store.flats).length}`);
        const all = Object.values(store.flats);
        const noProof = all.filter((r) => r.proof === 'рендер' || r.proof === 'интерьера нет').length;
        if (noProof) log(`не подтверждено фотографиями: ${noProof} — при сравнении цен это отдельный товар`);
        const clash = all.filter((r) => r.conflict);
        if (clash.length) {
          log(`\nтекст расходится с фотографиями (${clash.length}):`);
          clash.forEach((r) => log(`  ${r.id}  по тексту «${r.claimedState}», на кадрах «${r.observedState}»  ${r.address}`));
        }
      }

    } else if (cmd === 'report') {
      /* Таблица лотов раньше писалась руками и устаревала на следующий день.
         Теперь она собирается из оценок и архива, поэтому не гниёт: чтобы
         обновить, достаточно перезапустить. */
      const grades = loadGrades(a.grades || 'docs/cian/grades.json');
      const arc = loadArchive(a.archive || 'docs/cian/archive.json');
      const rows = Object.values(grades.flats);
      const ids = rows.map((r) => r.id);
      log(`оценённых лотов: ${ids.length}, спрашиваю текущие цены…`);
      const lr = await offersByIds(ctx, ids);
      if (lr.failed.length) log(`! ${lr.failed.length} номеров не проверены — в таблице они останутся с прошлой ценой`);
      const live = new Map(lr.offers.map((o) => [o.cianId || o.id, normalize(o)]));
      const unchecked = new Set(lr.failed);
      const today = new Date().toISOString().slice(0, 10);
      const histOf = (r) => mergedPriceHistory((r.fingerprint && arc.flats[r.fingerprint])
        || Object.values(arc.flats).find((f) => f.priceHistory && f.priceHistory[String(r.id)]));
      const out = [];
      out.push('# Осмотренные лоты', '');
      out.push('Собирается командой `node tools/cian/cian.js report`, руками не правится.', '');
      out.push(`Оценок: ${rows.length}. Цены проверены ${today}.`, '');
      out.push('Буква — уровень отделки по шести наблюдаемым признакам, см. [quality.md](quality.md).');
      out.push('«Чем подтверждено» важнее буквы: рендер и фотография — разная доказательная сила.', '');
      out.push('| ₽/м² | id | м² | Цена | Отделка | Подтверждено | Состояние | Движение цены | Адрес |');
      out.push('|--:|---|--:|--:|:--:|---|---|---|---|');
      rows.sort((x, y) => (y.pricePerM2 || 0) - (x.pricePerM2 || 0));
      let gone = 0;
      for (const r of rows) {
        const l = live.get(r.id);
        if (!l && !unchecked.has(r.id)) gone++;
        const price = l ? l.priceRub : r.price;
        const ppm = price && r.area ? Math.round(price / r.area) : r.pricePerM2;
        const h = histOf(r);
        const move = h ? `${((h[h.length - 1].price - h[0].price) / h[0].price * 100).toFixed(0)}% с ${h[0].date}` : '';
        out.push(`| ${(ppm || '').toLocaleString('ru-RU')} | [${r.id}](https://www.cian.ru/sale/flat/${r.id}/) | ` +
          `${r.area || ''} | ${price ? (price / 1e6).toFixed(1) + ' млн' : ''} | ${r.level || '—'} | ${r.proof || ''} | ` +
          `${r.state || ''}${r.conflict ? ' ⚠' : ''} | ${move} | ${l ? '' : unchecked.has(r.id) ? '_не проверен_ · ' : '**снят** · '}${r.address} |`);
      }
      out.push('', `⚠ — текст объявления расходится с фотографиями. Снято с продажи с момента осмотра: ${gone}.`, '');
      out.push('## Заметки по каждому', '');
      for (const r of rows) if (r.note) out.push(`**${r.id}**, ${r.address}. ${r.note}`, '');
      const file = a.out || 'docs/cian/lots.md';
      fs.writeFileSync(file, out.join('\n'));
      log(`-> ${file}: ${rows.length} лотов, снято с продажи ${gone}`);

    } else if (cmd === 'refresh') {
      /* Что из архива ещё продаётся, а что ушло. До сих пор «ушло» нельзя
         было отличить от «просто не попало в этот запрос»; теперь объявления
         спрашиваются поимённо, и молчание в ответе — это факт, а не пробел
         в охвате. */
      const ap = a.archive || 'docs/cian/archive.json';
      const arc = loadArchive(ap);
      const today = new Date().toISOString().slice(0, 10);
      const entries = Object.entries(arc.flats);
      /* По одному свежему объявлению на квартиру: этого хватает, чтобы
         понять, жива ли она, и вчетверо дешевле полного перебора. */
      const probe = new Map();
      for (const [fp, e] of entries) {
        const last = (e.listings || []).slice().sort((x, y) => String(x.seen).localeCompare(String(y.seen))).pop();
        if (last && last.id) probe.set(last.id, { fp, e, listed: last });
      }
      const ids = [...probe.keys()].slice(0, parseInt(a.limit || '2000', 10));
      log(`спрашиваю ${ids.length} объявлений пачками по ${BY_IDS_LIMIT}…`);
      const rr = await offersByIds(ctx, ids);
      const alive = new Map(rr.offers.map((o) => [o.cianId || o.id, normalize(o)]));
      const unchecked = new Set(rr.failed);
      log(`ответили: ${alive.size} из ${ids.length}`);
      /* Правдоподобность итога — часть проверки. Если не отвечает почти вся
         пачка, вероятнее отказавшая сессия, чем рынок, опустевший за ночь.
         На маленьких выборках это норма (в архиве есть куски из старых
         развёрток, ушедшие целиком), поэтому порог по объёму. */
      if (ids.length >= 100 && alive.size < ids.length * 0.15) {
        log(`\n! не ответили ${Math.round((1 - alive.size / ids.length) * 100)}% из ${ids.length}. Так рынок не пустеет —`);
        log('  вероятнее капча или отказ сессии. Ничего не записываю, прогоните ещё раз.');
        return;
      }
      if (unchecked.size) log(`не проверены (ручка отказала): ${unchecked.size} — они НЕ считаются ушедшими`);

      /* Молчание ручки — не приговор: подтверждаем вторым способом. Проверка
         именно так и поймала себя на деле — две квартиры на Усачёва
         действительно ушли, и обе метода сошлись. */
      /* Непроверенные — не молчащие. Смешать эти две вещи значило бы
         объявить проданными 28 квартир из-за одной сетевой ошибки. */
      const silent = ids.filter((id) => !alive.has(id) && !unchecked.has(id));
      const confirmed = new Set();
      const doConfirm = a.confirm !== 'нет' && silent.length <= parseInt(a['confirm-limit'] || '300', 10);
      if (silent.length && doConfirm) {
        log(`подтверждаю пропажу вторым способом (${silent.length} лотов)…`);
        let unknown = 0;
        for (const id of silent) {
          const alive2 = await offerAliveById(ctx, id);
          if (alive2 === false) confirmed.add(id);
          else if (alive2 === null) unknown++;
          await sleep(900);
        }
        if (unknown) log(`  у ${unknown} проверка не прошла по сети — считаю их живыми`);
      } else if (silent.length) {
        log(`не подтверждаю: ${silent.length} молчащих больше потолка, ставлю метку под вопросом`);
      }

      let gone = 0, moved = 0, same = 0, doubted = 0;
      const moves = [];
      for (const id of ids) {
        if (unchecked.has(id)) continue;
        const { fp, e, listed } = probe.get(id);
        const live = alive.get(id);
        if (!live) {
          if (doConfirm && !confirmed.has(id)) { doubted++; continue; }
          e.goneSince = e.goneSince || today;
          e.goneConfirmed = doConfirm;
          gone++;
          continue;
        }
        delete e.goneSince;
        e.lastSeen = today;
        same++;
        if (live.priceRub && listed.price && live.priceRub !== listed.price) {
          moved++;
          moves.push({ fp, id, from: listed.price, to: live.priceRub, address: e.address });
          e.listings.push({ id, created: live.created, price: live.priceRub, seen: today });
        }
      }
      arc.updated = today;
      fs.writeFileSync(ap, JSON.stringify(arc, null, 2) + '\n');
      log(`\nещё продаётся: ${same}`);
      log(`не отвечают, подтверждено двумя способами (продано, снято или скрыто): ${gone}`);
      if (doubted) log(`молчали в ручке, но нашлись поиском — считаю живыми: ${doubted}`);
      if (moves.length) {
        moves.sort((x, y) => (x.to - x.from) / x.from - (y.to - y.from) / y.from);
        log(`\nцена изменилась у ${moves.length}:`);
        moves.slice(0, 20).forEach((m) => log(`  ${m.id}  ${(m.from / 1e6).toFixed(1)} -> ${(m.to / 1e6).toFixed(1)} млн  ` +
          `(${m.to > m.from ? '+' : ''}${((m.to - m.from) / m.from * 100).toFixed(1)}%)  ${m.address}`));
      }
      log('\nМолчание в ответе — не то же самое, что продано: объявление могли снять, скрыть или переклеить.');

    } else if (cmd === 'card') {
      /* История цены привязана к объявлению, а не к квартире: перевыставление
         её обнуляет. Поэтому истории склеиваются по отпечатку — тем же,
         которым схлопываются дубли. Иначе «цена не менялась» будет означать
         всего лишь «объявление свежее». */
      let ids = a._.slice(1).map(Number).filter(Boolean);
      if (a.from) {
        for (const f of String(a.from).split(',')) {
          const d = JSON.parse(fs.readFileSync(f.trim(), 'utf8'));
          for (const l of (d.lots || d.flats || (Array.isArray(d) ? d : []))) if (!ids.includes(l.id)) ids.push(l.id);
        }
      }
      ids = ids.slice(0, parseInt(a.limit || '25', 10));
      const cards = [];
      for (const id of ids) {
        const c = await readCard(ctx, id);
        if (!c || c.error) { log(`${id}  ${(c && c.error) || 'не прочиталась'}`); continue; }
        c.estimation = await estimation(ctx, id);
        cards.push(c);
        const ch = c.priceChanges || [];
        const first = ch[ch.length - 1], last = ch[0];
        log(`\n${id}  ${(c.price || 0).toLocaleString('ru-RU')} ₽  ` +
            `${c.views.total != null ? c.views.total + ' просмотров' : 'просмотры не прочитались'}` +
            (c.views.today ? `, ${c.views.today} за сегодня` : ''));
        if (ch.length > 1) {
          const d = (last.price - first.price) / first.price * 100;
          log(`  цена: ${ch.length} изменений, ${first.date} ${(first.price / 1e6).toFixed(1)} млн -> ` +
              `${last.date} ${(last.price / 1e6).toFixed(1)} млн  (${d > 0 ? '+' : ''}${d.toFixed(1)}%)`);
          ch.slice().reverse().forEach((p) => log(`     ${p.date}  ${(p.price / 1e6).toFixed(2)} млн`));
        } else if (ch.length === 1) {
          log(`  цена: с ${ch[0].date} не менялась — но это может значить и «объявление свежее»`);
        } else if (c.priceChanges === null) {
          log('  цена: блока истории в карточке нет (не то же самое, что «не менялась»)');
        }
        if (c.estimation) {
          const e = c.estimation;
          const over = e.highMln && c.price ? (c.price / 1e6 / e.highMln - 1) * 100 : null;
          log(`  оценка Циан: ${e.estimation} (${e.range})` + (e.label ? `, метка «${e.label}»` : '') +
              (over != null && over > 0 ? `  — цена выше верхней границы на ${over.toFixed(0)}%` : ''));
        }
        const extra = [];
        if (c.decoration) extra.push(`отделка ${c.decoration}`);
        if (c.repairType) extra.push(`ремонт: ${REPAIR_RU[c.repairType] || c.repairType}`);
        if (c.roomType) extra.push(`комнаты ${c.roomType}`);
        if (c.windowsViewType) extra.push(`окна ${c.windowsViewType}`);
        if (c.allRoomsArea) extra.push(`площади комнат ${c.allRoomsArea}`);
        if (c.isDuplicate) extra.push('Циан считает объявление дублем');
        if (extra.length) log(`  из карточки: ${extra.join('; ')}`);
        if (c.bti) log(`  БТИ: ${c.bti.yearRelease} г., ${c.bti.flatCount} квартир, лифтов ${c.bti.lifts}, ` +
                       `${c.bti.seriesName || ''}${c.bti.isEmergency ? ', АВАРИЙНЫЙ' : ''}`);
        if (c.tourUrl) log(`  3D-тур: ${c.tourUrl}`);
        await sleep(2500);
      }
      if (a.out) { fs.writeFileSync(a.out, JSON.stringify(cards, null, 2) + '\n'); log(`\n-> ${a.out}`); }
      if (a.archive !== 'нет') {
        /* История цены пропадает вместе с объявлением, поэтому её место в
           архиве, привязанном к квартире, а не к объявлению. */
        const ap = a.archive || 'docs/cian/archive.json';
        const arc = loadArchive(ap);
        const srcAll = [];
        if (a.from) for (const f of String(a.from).split(',')) {
          const d = JSON.parse(fs.readFileSync(f.trim(), 'utf8'));
          srcAll.push(...(d.lots || d.flats || (Array.isArray(d) ? d : [])));
        }
        let saved = 0;
        for (const c of cards) {
          const lot = srcAll.find((l) => l.id === c.id);
          const fp = (lot && lot.fingerprint)
            || Object.keys(arc.flats).find((k) => (arc.flats[k].listings || []).some((x) => x.id === c.id));
          if (!fp || !arc.flats[fp]) continue;
          const e = arc.flats[fp];
          e.priceHistory = e.priceHistory || {};
          e.priceHistory[c.id] = c.priceChanges;
          if (c.views && c.views.total != null) {
            e.views = e.views || {};
            e.views[c.id] = { total: c.views.total, seen: new Date().toISOString().slice(0, 10) };
          }
          if (c.repairType) e.repairType = c.repairType;
          saved++;
        }
        if (saved) {
          fs.writeFileSync(ap, JSON.stringify(arc, null, 2) + '\n');
          log(`история цены записана в архив для ${saved} квартир`);
        } else log('в архиве этих квартир нет — история никуда не записана');
      }
      /* Склейка историй по квартире: разные объявления одной квартиры дают
         разные куски одного ряда цен. */
      if (a.from || ids.length > 1) {
        const src = [];
        if (a.from) for (const f of String(a.from).split(',')) {
          const d = JSON.parse(fs.readFileSync(f.trim(), 'utf8'));
          src.push(...(d.lots || d.flats || (Array.isArray(d) ? d : [])));
        }
        const byFp = new Map();
        for (const c of cards) {
          const lot = src.find((l) => l.id === c.id);
          const fp = lot && lot.fingerprint;
          if (!fp) continue;
          if (!byFp.has(fp)) byFp.set(fp, []);
          byFp.get(fp).push(c);
        }
        const merged = [...byFp.entries()].filter(([, cs]) => cs.length > 1);
        if (merged.length) {
          log(`\nодна квартира — несколько объявлений, история склеена (${merged.length}):`);
          for (const [fp, cs] of merged) {
            const all = cs.flatMap((c) => (c.priceChanges || []).map((p) => ({ ...p, id: c.id })))
              .sort((x, y) => x.date.localeCompare(y.date));
            log(`  ${fp}: объявления ${cs.map((c) => c.id).join(', ')}`);
            all.forEach((p) => log(`     ${p.date}  ${(p.price / 1e6).toFixed(2)} млн  (объявление ${p.id})`));
            const d = (all[all.length - 1].price - all[0].price) / all[0].price * 100;
            log(`     итого с ${all[0].date}: ${d > 0 ? '+' : ''}${d.toFixed(1)}%`);
          }
        }
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
module.exports = { normalize, groupSameFlat, dedupe, findTwins, withMarket, median, assessRepair, mergeArchive, archiveStat, completeness, comparabilityGaps, features, readiness, finishEvidence, buildingYear, insideGardenRing, ringMargin, ringVerdict, pointInPolygon,
  gradeLevel, gradeRecord, observedState, gradeFor, galleryGrew, parseViews, REPAIR_RU, offersByIds, mergedPriceHistory,
  expandSimilar, matchesQuery, finishCost, loadedPricePerM2, fairShellPrice, MARKERS, PROOFS };
