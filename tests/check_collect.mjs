// Тесты слоя сбора extension/content.js (apiFetch / paginateSegment / priceSplit
// / collectAll).
//
//   node tests/check_collect.mjs             — прогнать
//   UPDATE=1 node tests/check_collect.mjs    — перезаписать сводку прогонов
//   NEGATIVE=1 node tests/check_collect.mjs  — негативный контроль (см. низ файла)
//   CIAN_DUMP='cap=2' node tests/check_collect.mjs   — распечатать маршрут сценария
//   CIAN_CONTENT_JS=/путь/content.js …       — проверить другую копию
//
// ЗАЧЕМ. На книгу Excel тестов 690 строк, на слой сбора — ноль, а правки
// планировщика делаются вслепую. Дальше по плану три фичи, которые все упираются
// в сеть: перебор decorations_list (×4 запросов), фоновые алерты (запросы без
// человека за рулём — капчу пройти некому) и накопление по нескольким ЖК.
// Ни одну нельзя оценить, пока не измерено, во что превращается CONFIG.reqBudget
// на реальных HTTP и сколько минут стоит прогон.
//
// КАК УСТРОЕНО. Настоящий код вырезается из content.js и исполняется со стабами
// (tests/collect_harness.mjs). Сеть — программируемый мок-сервер: он разбирает
// jsonQuery и честно нарезает страницы из универсума лотов, поэтому тест
// проверяет, что планировщик ПРАВИЛЬНО СТРОИТ ФИЛЬТРЫ, а не что он ходит по
// заранее известному маршруту. Время виртуальное: sleep-ы не ждут, но их
// длительности настоящие — по ним и проверяются бэкофф и Retry-After.
//
// ТРИ ВИДА СТРОК В ВЫВОДЕ:
//   ✓  инвариант держится;
//   ✗  инвариант сломан — ненулевой код возврата;
//   △  ИЗВЕСТНАЯ ДЫРА: инвариант НЕ держится на текущем коде, поведение
//      зафиксировано характеризующей проверкой. Такая строка не валит прогон,
//      но если дыру закроют — тест это заметит и попросит перенести проверку
//      в основные. Всё, что помечено △, — это список того, что надо чинить
//      ПЕРЕД фичами (а)/(б)/(в), а не «тесты не дописаны».

import fs from "fs";
import path from "path";
import { fileURLToPath, pathToFileURL } from "url";
import { spawnSync } from "child_process";
import {
  CONTENT_JS, sliceCollector, makeFactory, makeMath, makeConsole,
  runCollect, makeUniverse, makeBase, segments, fmtReq, baseFilterViolations } from "./collect_harness.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SNAPSHOT = path.join(HERE, "collect_snapshot.json");

let failed = 0, gaps = 0, closed = 0;
const fail = (m) => { console.error("  ✗ " + m); failed++; };
const pass = (m) => console.log("  ✓ " + m);
const section = (t) => console.log(`\n── ${t} ${"─".repeat(Math.max(0, 66 - t.length))}`);

// Проверка с ДВУМЯ разными сообщениями: при падении видно, ЧТО сломалось,
// а не «ожидалось true, получено false». Сообщение можно передать функцией —
// тогда оно вычисляется, только если действительно понадобилось (сообщение о
// поломке часто лезет в данные, которых при успехе просто нет).
const msg = (m) => (typeof m === "function" ? m() : m);
const check = (cond, okMsg, badMsg) => (cond ? pass(msg(okMsg)) : fail(msg(badMsg)));
const eq = (actual, expected, what) =>
  check(actual === expected, `${what}: ${actual}`, `${what}: ожидалось ${expected}, получено ${actual}`);

// Характеризующая проверка известного дефекта. holds === «дыра ещё на месте».
const gap = (holds, nowMsg, fixedMsg) => {
  if (holds) { console.log("  △ ИЗВЕСТНАЯ ДЫРА: " + msg(nowMsg)); gaps++; }
  else { console.log("  ✓ ДЫРА ЗАКРЫТА: " + msg(fixedMsg) + " — перенесите проверку в основные"); closed++; }
};

const uniq = (a) => new Set(a).size;
const sum = (a) => a.reduce((x, y) => x + y, 0);
const hh = (ms) => (ms / 3.6e6).toFixed(2) + " ч";

// Сводка прогонов: одна строка на сценарий. Любая правка планировщика меняет
// строку, и в диффе видно цену правки в запросах и минутах, а не только
// «тесты зелёные».
const runs = [];
// Инвариант, который обязан выполняться в КАЖДОМ прогоне, поэтому проверяется
// централизованно в record(), а не отдельной секцией: новый сценарий получает
// его бесплатно и не может «забыть».
let baseIntactChecked = 0;
const record = (name, r, extra = {}) => {
  if (r.base && r.mock) {
    const bad = baseFilterViolations(r.mock, r.base);
    baseIntactChecked++;
    if (bad.length) {
      check(false, "", `«${name}»: фильтры пользователя не доехали до запроса — ${bad[0]}` +
        (bad.length > 1 ? ` (и ещё ${bad.length - 1})` : ""));
    }
  }
  runs.push({
    name,
    собрано: r.res ? r.res.offers.length : null,
    всего: r.res ? r.res.totalInJk : null,
    http: r.http,
    логических: r.logical,
    ретраев: r.res ? r.res.health.retries : (r.api ? (r.api.getHealth() || {}).retries : null),
    дрейф: r.res ? r.res.health.totalDrift : null,
    сегментов: segments(r.mock).length,
    снов: r.clock.log.length,
    виртСек: +(r.clock.elapsed() / 1000).toFixed(1),
    бросок: r.err ? r.err.message : null,
    ...extra,
  });
  // Журнал запросов читается как МАРШРУТ планировщика и сразу показывает, куда
  // он сходил лишний раз. Печатать всегда — утонуть (в худшем сценарии 2472
  // строки), поэтому по требованию:  CIAN_DUMP=<часть имени сценария>
  if (process.env.CIAN_DUMP && name.includes(process.env.CIAN_DUMP)) {
    console.log(`  ─ журнал запросов «${name}» (${r.mock.requests.length} шт., первые 40):`);
    r.mock.requests.slice(0, 40).forEach((q) => console.log("    " + fmtReq(q)));
    console.log(`  ─ журнал сна (${r.clock.log.length} шт., первые 20):`);
    r.clock.log.slice(0, 20).forEach((sl) => console.log(`    t=${String(sl.at).padStart(7)}мс  ${String(sl.ms).padStart(6)}мс  ${sl.tag}`));
    console.log(`  ─ onProgress (первые 8): ${r.progress.slice(0, 8).map((m) => `'${m}'`).join(", ")}`);
  }
  return r;
};

// Один и тот же прогон нужен двум секциям (обрезка по maxPages и исчерпание
// бюджета) — считаем его один раз: 400 запросов не бесплатны даже на моке.
let bigRun = null;
const big = async () => (bigRun ||= record("20000 лотов: обрезка по maxPages + исчерпание бюджета",
  await runCollect({ offers: makeUniverse({ rooms: [9, 7, 1, 2, 3, 4, 5, 6], perRoom: 2500 }) })));

// Срез вырезается один раз на весь прогон. api0 — экземпляр без сети: из него
// читаются CONFIG/ROOMS/API и вызывается чистая withFilters.
let factory = null, api0 = null, CFG = null;
function bootstrap() {
  factory = makeFactory();
  api0 = factory(async () => { throw new Error("сеть не нужна"); }, () => 0, makeConsole(), Math, Date);
  CFG = api0.CONFIG;
}

// ===========================================================================
// 1. СРЕЗ: границы и константы
// ===========================================================================
async function testSlice() {
  section("Срез content.js и константы сбора");
  const sl = sliceCollector();
  pass(`срез вырезан из ${path.relative(process.cwd(), CONTENT_JS) || CONTENT_JS}: константы ${sl.linesA} стр., планировщик ${sl.linesB} стр.`);

  // Все числовые ожидания ниже выведены из этих констант. Если их поменяли —
  // тест обязан сказать это прямо, а не разойтись в двадцати местах сразу.
  const want = { region: 1, delayMin: 300, delayMax: 700, maxPages: 54, pageSize: 28,
                 minPriceSpan: 200000, priceCeiling: 3000000000, maxRetries: 4,
                 backoffBase: 1500, reqBudget: 400 };
  for (const [k, v] of Object.entries(want)) {
    check(CFG[k] === v,
      `CONFIG.${k} = ${v}`,
      `CONFIG.${k} изменился: было ${v}, стало ${CFG[k]} — ожидания в tests/check_collect.mjs надо пересчитать вручную`);
  }
  check(JSON.stringify(api0.ROOMS) === "[9,7,1,2,3,4,5,6]",
    "ROOMS = [9,7,1,2,3,4,5,6] (9 = студии, порядок задаёт очередь декомпозиции)",
    `ROOMS изменились на ${JSON.stringify(api0.ROOMS)} — порядок опроса комнатности другой, ожидания totalsByRoom не сойдутся`);
  check(/^https:\/\/api\.cian\.ru\/search-offers\//.test(api0.API),
    "API указывает на api.cian.ru/search-offers",
    `API уехал на ${api0.API}`);
}

// ===========================================================================
// 2. apiFetch: разбор ответа
// ===========================================================================
// Форма ответа Циан нестабильна (offersSerialized / offers / items, иногда лот
// завёрнут в {offer:{…}}, поле «всего» называется четырьмя способами). Все
// фолбэки написаны в apiFetch «на всякий случай» и до сих пор не были проверены
// ни разу — то есть неизвестно, работают ли они вообще.
async function testResponseShapes() {
  section("apiFetch: формы ответа и фолбэки поля total");
  const call = async (payload) => {
    let body = null;
    const api = factory(
      async (url, init) => { body = init.body; return { status: 200, headers: { get: () => null }, json: async () => payload }; },
      () => 0, makeConsole(), makeMath(), Date);
    return { res: await api.apiFetch({ jsonQuery: { _type: "flatsale" } }), body };
  };
  const three = [{ cianId: 1 }, { cianId: 2 }, { cianId: 3 }];

  let r = await call({ data: { offersSerialized: [{ offer: { cianId: 77, price: 5 } }, { cianId: 88 }], aggregatedCount: 9 } });
  check(r.res.offers.length === 2 && r.res.offers[0].cianId === 77 && r.res.offers[1].cianId === 88,
    "лот распакован из обёртки {offer:{…}}, невёрнутый лот пропущен как есть",
    `распаковка {offer:{…}} сломана: получено ${JSON.stringify(r.res.offers)}`);
  eq(r.res.total, 9, "total из aggregatedCount");

  eq((await call({ data: { offers: three, aggregatedCount: 5 } })).res.offers.length, 3, "offers читается из data.offers");
  eq((await call({ data: { items: three, aggregatedCount: 5 } })).res.offers.length, 3, "offers читается из data.items");
  eq((await call({ data: { offersSerialized: three, offerCount: 5 } })).res.total, 5, "total из offerCount (когда aggregatedCount нет)");
  eq((await call({ data: { offersSerialized: three, offersCount: 6 } })).res.total, 6, "total из offersCount");
  eq((await call({ data: { offersSerialized: three, totalCount: 7 } })).res.total, 7, "total из totalCount");
  eq((await call({ data: { offersSerialized: three } })).res.total, 3, "total падает на длину страницы, когда счётчика нет вовсе");
  eq((await call({ data: { offersSerialized: three, aggregatedCount: "12" } })).res.total, 12, "строковый aggregatedCount разбирается parseInt");
  eq((await call({ offersSerialized: three, aggregatedCount: 4 } )).res.total, 4, "ответ без конверта data разбирается (d.data || d)");
  eq((await call({ data: { offersSerialized: [], aggregatedCount: 40 } })).res.total, 40, "пустая страница не обнуляет total ответа");

  // `aggregatedCount || offerCount || …` — цепочка через ||, поэтому ЧЕСТНЫЙ
  // ноль («по фильтру ничего нет») неотличим от «поля нет». Для сегмента с
  // пустой выдачей это безобидно (длина страницы тоже 0), но поведение надо
  // знать: на непустой странице с aggregatedCount=0 total станет размером страницы.
  eq((await call({ data: { offersSerialized: three, aggregatedCount: 0 } })).res.total, 3,
    "aggregatedCount=0 трактуется как «поля нет» и заменяется длиной страницы");

  const q = JSON.parse((await call({ data: { offersSerialized: [] } })).body);
  check(q.jsonQuery && q.jsonQuery._type === "flatsale",
    "тело запроса — {jsonQuery:{…}} с фильтрами пользователя внутри",
    `тело запроса потеряло jsonQuery: ${JSON.stringify(q).slice(0, 120)}`);

  // Распаковка проверена на apiFetch; но ключ дедупликации берётся уже ПОСЛЕ
  // неё (`o.cianId || o.id`), поэтому нужен и сквозной прогон: если обёртку
  // однажды перестанут снимать, cianId окажется undefined и весь сбор молча
  // выродится в один лот.
  const rw = await runCollect({ offers: makeUniverse({ rooms: [2], perRoom: 50 }), shape: "wrapped" });
  check(rw.res.offers.length === 50 && rw.res.offers.every((o) => o.cianId != null),
    "сквозной сбор на ответах вида {offer:{…}}: 50 лотов с непустыми cianId",
    `сквозной сбор на ответах {offer:{…}} дал ${rw.res.offers.length} лотов, из них без cianId — ` +
    `${rw.res.offers.filter((o) => o.cianId == null).length}`);

  // fetchPage — «совместимость: одиночная страница». Внутри collectAll он не
  // используется, поэтому проверить его больше негде, а сломать легко.
  let sent = null;
  const one = factory(
    async (url, init) => { sent = JSON.parse(init.body).jsonQuery; return { status: 200, headers: { get: () => null }, json: async () => ({ data: { offersSerialized: [], aggregatedCount: 5 } }) }; },
    () => 0, makeConsole(), makeMath(), Date);
  const got = await one.fetchPage(makeBase(), 3, 7);
  check(sent && sent.page.value === 7 && JSON.stringify(sent.room.value) === "[3]" && got.total === 5,
    "fetchPage(base, room, page) шлёт ровно эту комнату и эту страницу",
    () => `fetchPage построил неверный запрос: page=${JSON.stringify(sent && sent.page)} room=${JSON.stringify(sent && sent.room)}`);
}

// ===========================================================================
// 3. apiFetch: отказы, бэкофф, Retry-After
// ===========================================================================
// health пишется только внутри collectAll (вне сбора он null), поэтому отказы
// проверяются через collectAll на первой же странице: так же, как это увидит
// пользователь.
async function testFailures() {
  section("apiFetch: 403, ретраи, бэкофф, Retry-After");

  const small = makeUniverse({ rooms: [2], perRoom: 50 });

  // --- 403: ТЕРМИНАЛЬНОЕ состояние. Ретраи по блокировке — самый быстрый
  // способ получить бан IP, поэтому «не ретраить» тут важнее, чем «дособрать».
  // Терминальность означает «больше не ходить», а НЕ «выбросить собранное»:
  // прогон возвращает то, что успел, с пометкой health.wafBlock.
  const r403 = record("403 на первой странице", await runCollect({
    offers: small, faults: [{ when: { nth: 1 }, then: { status: 403 } }],
  }));
  const h403 = r403.res ? r403.res.health : {};
  check(h403.wafBlock === true && /403/.test(h403.wafMessage || ""),
    `403 помечен как блокировка, а сообщение объясняет, что делать: «${h403.wafMessage}»`,
    `403 не дал понятной диагностики: wafBlock=${h403.wafBlock}, message=${h403.wafMessage}`);
  check(/подожд|смените сеть/i.test(h403.wafMessage || ""),
    "и не советует «пройти капчу»: при WAF-блоке проходить нечего",
    `сообщение про 403 советует не то: «${h403.wafMessage}»`);
  eq(r403.http, 1, "403: сделан ровно один HTTP-запрос (ретраев нет)");
  eq(r403.clock.log.length, 0, "403: не спали ни миллисекунды");
  eq((r403.api.getHealth() || {}).retries, 0, "403: health.retries не тронут — это не «неудачная попытка», а отказ в доступе");
  check(r403.api.isHealthWarn(h403), "403: прогон помечен как требующий проверки",
    "403: прогон выглядит штатным");

  // --- Капча под кодом 200. Близнец 403 и до сих пор — самый вредный путь:
  // apiFetch звал r.json() на любом 200, не глядя на тип, HTML давал
  // SyntaxError, тот падал в общий catch и РЕТРАИЛСЯ как сетевая ошибка.
  // То есть на антибот код отвечал увеличением нагрузки, а диагностика
  // показывала «сеть × 4».
  const rHtml = record("капча: 200 с HTML-телом", await runCollect({
    offers: small, faults: [{ when: { nth: 1 }, then: { status: 200, ct: "text/html; charset=utf-8" } }],
  }));
  const hHtml = rHtml.res ? rHtml.res.health : {};
  eq(rHtml.http, 1, "200 с HTML: ровно ОДНО обращение вместо четырёх");
  eq(rHtml.clock.log.length, 0, "200 с HTML: ни миллисекунды сна");
  eq(hHtml.retryStatuses.network || 0, 0, "200 с HTML: больше не маскируется под сетевую ошибку");
  check(hHtml.wafBlock === true && /провер/i.test(hHtml.wafMessage || ""),
    `200 с HTML распознан как проверка браузера: «${hHtml.wafMessage}»`,
    `200 с HTML не распознан: wafBlock=${hHtml.wafBlock}, message=${hHtml.wafMessage}`);
  // В журнале у него свой код: иначе он растворится среди честных двухсоток.
  check(rHtml.log.length === 1 && rHtml.log[0].st === "HTML" && rHtml.log[0].ct === "html",
    `в журнале запись со своим кодом: st=${rHtml.log[0] && rHtml.log[0].st}, ct=${rHtml.log[0] && rHtml.log[0].ct}, len=${rHtml.log[0] && rHtml.log[0].len}`,
    `запись о проверке браузера в журнале неотличима от успеха: ${JSON.stringify(rHtml.log[0])}`);

  // Настоящая блок-страница: 403 + text/html + ~21.5 КБ — оба отпечатка сразу.
  const rWaf = record("WAF: 403 + text/html, 21570 Б", await runCollect({
    offers: small, faults: [{ when: { nth: 1 }, then: { waf: true } }],
  }));
  eq(rWaf.http, 1, "WAF: одно обращение");
  eq(rWaf.log[0].len, 21570, "WAF: размер тела попал в журнал (второй отпечаток после content-type)");
  eq(rWaf.log[0].ct, "html", "WAF: тип тела попал в журнал");

  // --- сеть: ретраится, бэкофф растёт вдвое.
  const rnet = record("сеть падает 3 раза подряд", await runCollect({
    offers: small, faults: [{ when: (f, i) => i <= 3, then: { throw: "network" } }],
  }));
  eq(rnet.http, 5, "сеть: 4 HTTP на первую логическую страницу (3 обрыва + успех) + 1 на вторую");
  eq(rnet.res.health.retries, 3, "сеть: health.retries = 3");
  eq(rnet.res.health.retryStatuses.network, 3, "сеть: health.retryStatuses.network = 3");
  const nb = rnet.clock.sleeps("backoff");
  check(nb.length === 3 && nb.every((ms, k) => ms >= CFG.backoffBase * 2 ** k && ms < CFG.backoffBase * 2 ** k + 400),
    `бэкофф растёт вдвое от backoffBase: ${nb.join(" → ")} мс (база 1500/3000/6000 + джиттер до 400)`,
    () => `бэкофф не соответствует backoffBase*2^n: получено ${nb.join(" → ")} мс`);
  check(nb.every((ms, k) => k === 0 || ms > nb[k - 1]),
    "каждая следующая пауза строго больше предыдущей",
    () => `паузы не растут: ${nb.join(" → ")} мс`);

  // --- Retry-After: игнор заголовка = гарантированная эскалация от троттлинга
  // к капче, то есть смерть фич (а) и (б).
  const rra = record("429 с Retry-After: 30", await runCollect({
    offers: small, faults: [{ when: (f, i) => i <= 2, then: { status: 429, retryAfter: 30 } }],
  }));
  const rb = rra.clock.sleeps("backoff");
  check(rb.length === 2 && rb.every((ms) => ms >= 30000 && ms < 30400),
    `Retry-After: 30 уважается — ждали ${rb.join(" и ")} мс вместо базовых 1500/3000`,
    () => `Retry-After: 30 проигнорирован: ждали ${rb.join(" и ")} мс (базовый бэкофф ≈1500/3000)`);
  eq(rra.res.health.retryStatuses["429"], 2, "429: health.retryStatuses[429] = 2");
  check(rra.con.warns.some((w) => /HTTP 429/.test(w) && /попытка 1\/4/.test(w)),
    `в console.warn виден номер попытки: «${rra.con.warns[0]}»`,
    () => `console.warn не сообщает о ретрае (единственное место, где номер попытки вообще наблюдаем): ${JSON.stringify(rra.con.warns)}`);

  // --- 429 без остановки: сколько HTTP стоит безнадёжная страница.
  const r429 = record("429 на всех попытках", await runCollect({
    offers: small, faults: [{ when: () => true, then: { status: 429 } }],
  }));
  check(r429.err && /429/.test(r429.err.message),
    `после maxRetries=${CFG.maxRetries} попыток 429 сбор падает с «${r429.err && r429.err.message}»`,
    `безнадёжный 429 не завершился исключением: ${r429.err ? r429.err.message : "сбор прошёл штатно"}`);
  eq(r429.http, CFG.maxRetries, "429: ровно maxRetries HTTP на одну логическую страницу");

  // Учёт неудачных попыток обязан быть ОДИНАКОВЫМ на обоих путях. Раньше путь
  // 429/5xx считал и последнюю, заведомо фатальную попытку, а сетевой бросал
  // раньше инкремента: 4 против 3 при одинаковых четырёх обращениях. Порог
  // isHealthWarn (retries/requests) из-за этого зависел от способа отказа.
  const h429 = r429.api.getHealth();
  const rNet = record("сеть падает на всех попытках", await runCollect({
    offers: small, faults: [{ when: () => true, then: { throw: true } }],
  }));
  const hNet = rNet.api.getHealth();
  eq(rNet.http, CFG.maxRetries, "сеть: ровно maxRetries HTTP на одну логическую страницу");
  check(h429.retries === hNet.retries && h429.retries === CFG.maxRetries,
    `оба пути считают неудачные попытки одинаково: 429 → ${h429.retries}, сеть → ${hNet.retries} при ${CFG.maxRetries} обращениях`,
    `учёт асимметричен: 429 → ${h429.retries}, сеть → ${hNet.retries} (обращений в обоих случаях ${CFG.maxRetries})`);
  eq(h429.retryStatuses["429"], CFG.maxRetries, "и разбивка по статусу совпадает с числом попыток: 429");
  eq(hNet.retryStatuses.network, CFG.maxRetries, "и разбивка по статусу совпадает с числом попыток: сеть");
  gap(r429.clock.sleeps("backoff").length === CFG.maxRetries,
    () => `перед гарантированным отказом код ещё спит: ${r429.clock.sleeps("backoff").length} сна на ${CFG.maxRetries} попыток, ` +
    `последний (${r429.clock.sleeps("backoff")[CFG.maxRetries - 1]} мс) — чистое ожидание перед throw`,
    "сон перед последней, фатальной попыткой убран");

  // --- нерепитабельные 4xx: 404 не станет 200 ни на четвёртой попытке.
  const r404 = record("404 на всех попытках", await runCollect({
    offers: small, faults: [{ when: () => true, then: { status: 404 } }],
  }));
  const h404 = r404.api.getHealth();
  gap(r404.http === CFG.maxRetries && (h404.retryStatuses.network || 0) === CFG.maxRetries,
    () => `404 ретраится ${r404.http} раза и попадает в диагностику как «сеть» (retryStatuses.network=${h404.retryStatuses.network}), ` +
    `хотя это неправильный jsonQuery или капча — 4 бесполезных обращения и ~11 с ожидания`,
    "нерепитабельные 4xx больше не ретраятся и не маскируются под сетевую ошибку");
}

// ===========================================================================
// 4. withFilters: фильтры пользователя
// ===========================================================================
// Обещание расширения — «выгружаем ровно то, что вы видите на экране».
// Оно держится ровно на том, что withFilters делает ГЛУБОКУЮ копию базового
// jsonQuery и переопределяет только page/room/price/sort.
async function testWithFilters() {
  section("withFilters: пользовательские фильтры доезжают до каждого запроса");
  const base = makeBase({ polygon: { type: "poly", value: ["1,2"] } });
  const before = JSON.stringify(base);
  const q1 = api0.withFilters(base, { page: 3, room: 2, priceGte: 5e6, priceLte: 9e6, sort: "price_object_order" }).jsonQuery;

  check(JSON.stringify(base) === before,
    "withFilters не мутирует базовый jsonQuery (страница пользователя не ломается)",
    "withFilters ИЗМЕНИЛ базовый jsonQuery — все последующие запросы пойдут с чужими фильтрами");
  check(JSON.stringify(q1.page) === '{"type":"term","value":3}',
    "page подставляется как {type:'term',value:N}",
    `page имеет неверную форму: ${JSON.stringify(q1.page)}`);
  check(JSON.stringify(q1.room) === '{"type":"terms","value":[2]}',
    "room подставляется как {type:'terms',value:[N]}",
    `room имеет неверную форму: ${JSON.stringify(q1.room)}`);
  check(JSON.stringify(q1.price) === '{"type":"range","value":{"gte":5000000,"lte":9000000}}',
    "price подставляется как {type:'range',value:{gte,lte}}",
    `price имеет неверную форму: ${JSON.stringify(q1.price)}`);
  for (const k of ["_type", "engine_version", "newobject", "geo", "total_area", "polygon"])
    check(JSON.stringify(q1[k]) === JSON.stringify(base[k]),
      `фильтр «${k}» доехал до запроса нетронутым`,
      `фильтр «${k}» потерян или изменён: было ${JSON.stringify(base[k])}, стало ${JSON.stringify(q1[k])}`);

  const q2 = api0.withFilters(base, {}).jsonQuery;
  check(q2.page === undefined && q2.room === undefined && q2.price === undefined,
    "без параметров withFilters ничего не добавляет от себя",
    `withFilters добавил лишнее: page=${JSON.stringify(q2.page)} room=${JSON.stringify(q2.room)} price=${JSON.stringify(q2.price)}`);
}

// ===========================================================================
// 5. Чистый прогон: бюджет, паузы, отсутствие лишней декомпозиции
// ===========================================================================
async function testCleanRun() {
  section("Чистый прогон (недобора нет)");
  const r = record("чистый прогон, 50 лотов", await runCollect({ offers: makeUniverse({ rooms: [2], perRoom: 50 }) }));

  eq(r.res.offers.length, 50, "собраны все лоты");
  eq(r.res.totalInJk, 50, "totalInJk равен total выдачи");
  eq(r.logical, 2, "потрачено логических страниц (50 лотов / 28 = 2)");
  eq(r.http, 2, "реальных HTTP ровно столько же — ретраев не было");
  eq(r.ok200, r.logical, "health.requests совпадает с числом успешных ответов мока");

  // Декомпозиция по комнатности — дорогая (×8 сегментов). Она обязана
  // включаться ТОЛЬКО при недоборе, иначе бюджет сгорит на пустом месте.
  const withRoom = r.mock.requests.filter((q) => q.rooms != null).length;
  check(withRoom === 0 && r.res.totalsByRoom === null,
    "при полном сборе разложения по комнатности не было (0 запросов с room, totalsByRoom=null)",
    `разложение по комнатности запустилось без недобора: ${withRoom} запросов с фильтром room`);
  const withPrice = r.mock.requests.filter((q) => q.gte != null || q.lte != null).length;
  eq(withPrice, 0, "дробления по цене при полном сборе не было (запросов с price)");

  eq(r.clock.log.length, 1, "между двумя страницами ровно одна пауза");
  const p = r.clock.log[0];
  check(p.tag === "pause" && p.ms >= CFG.delayMin && p.ms < CFG.delayMax,
    `пауза между страницами ${p.ms} мс — внутри [${CFG.delayMin}, ${CFG.delayMax})`,
    `пауза между страницами ${p.ms} мс вышла за [${CFG.delayMin}, ${CFG.delayMax}) — CONFIG.delayMin/Max больше не соблюдаются`);

  const h = r.res.health;
  check(h.retries === 0 && h.totalDrift === 0 && api0.isHealthWarn(h) === false,
    "здоровье чистого прогона чистое, суффикс «_проверить» к имени файла не добавится",
    `чистый прогон дал предупреждение: ${JSON.stringify(h)}`);
  // Формат итоговой строки поменялся вместе с учётом: теперь она отдельно
  // называет РЕАЛЬНЫЕ обращения и логические страницы — раньше смешивала.
  check(r.con.logs.some((l) => /ИТОГО 50\/50 за 2 обращений \(2 страниц/.test(l)),
    `в консоль напечатан понятный итог: «${r.con.logs[0]}»`,
    `итоговая строка сбора не найдена: ${JSON.stringify(r.con.logs)}`);
  check(r.progress[0] === "стр.1…" && r.progress.includes("Собрано 28…"),
    `onProgress ведёт пользователя по страницам: ${r.progress.slice(0, 3).map((s) => `'${s}'`).join(", ")}`,
    `onProgress выдал неожиданные сообщения: ${JSON.stringify(r.progress.slice(0, 4))}`);
}

// ===========================================================================
// 6. Недобор -> разложение по комнатности -> дедуп
// ===========================================================================
// cap=2 — модель настоящего потолка выдачи Циан: по одному фильтру сервер
// отдаёт не больше двух страниц, сколько бы ни было лотов. Именно потолок (а не
// 429) заставляет прямой проход недобрать и включает декомпозицию.
async function testRoomsDecomposition() {
  section("Недобор -> разложение по комнатности -> дедуп");
  const universe = [...makeUniverse(), { id: 9999, room: 8, price: 12_000_000 }];  // room 8 вне ROOMS -> недобор неустраним
  const r = record("cap=2 (потолок выдачи) -> комнаты", await runCollect({ offers: universe, cap: 2 }));

  eq(r.res.totalInJk, 321, "grandTotal взят с первой страницы прямого прохода");
  eq(r.res.offers.length, 320, "собрано всё, что достижимо через ROOMS (лот с комнатностью 8 недостижим)");
  eq(r.logical, 20, "логических страниц: 4 прямого прохода + 8 комнат × 2");
  eq(r.http, 20, "реальных HTTP столько же (отказов не было)");
  eq(r.ok200, r.logical, "health.requests совпадает с числом успешных ответов мока");

  const segs = segments(r.mock);
  const roomSegs = segs.filter((s) => s.rooms != null).map((s) => s.rooms[0]);
  check(JSON.stringify(roomSegs) === JSON.stringify(api0.ROOMS),
    `комнаты опрошены ровно в порядке ROOMS: ${roomSegs.join(", ")}`,
    `порядок опроса комнатности не совпал с ROOMS: ${roomSegs.join(", ")} вместо ${api0.ROOMS.join(", ")}`);
  check(JSON.stringify(r.res.totalsByRoom) === JSON.stringify({ 1: 40, 2: 40, 3: 40, 4: 40, 5: 40, 6: 40, 7: 40, 9: 40 }),
    "totalsByRoom заполнен по всем восьми комнатам — «Всего на Циан» в Сводке будет честным",
    `totalsByRoom неполон или неверен: ${JSON.stringify(r.res.totalsByRoom)}`);

  // Дедуп. Прямой проход и комнатные сегменты возвращают ОДНИ И ТЕ ЖЕ лоты —
  // это штатный режим, а не патология. Проверяем и то, что дублей нет, и то,
  // что они вообще были (иначе тест ничего не доказывает).
  const served = sum([...r.mock.served.values()]);
  const ids = r.res.offers.map((o) => o.cianId);
  check(served > ids.length,
    `сервер отдал ${served} лотов при ${ids.length} уникальных — повторная выдача между сегментами действительно была`,
    `сегменты не пересеклись (${served} подач на ${ids.length} лотов) — сценарий не проверяет дедуп, почините фикстуру`);
  check(uniq(ids) === ids.length,
    `дедуп между сегментами работает: ${ids.length} лотов, ${uniq(ids)} уникальных cianId`,
    `дедуп между сегментами не работает: в выдаче ${ids.length} лотов, уникальных cianId — ${uniq(ids)}`);

  // seg (Set внутри сегмента) против byId (глобальная Map): сегмент room 9 обязан
  // отработать полностью, хотя 28 его лотов уже лежат в byId после прямого прохода.
  // Подмена seg на byId сломала бы обнаружение недобора и дробление не запускалось
  // бы никогда.
  const seg9 = segs.find((s) => s.rooms && s.rooms[0] === 9);
  eq(seg9.pages.length, 2, "сегмент room 9 отработал обе свои страницы, хотя его лоты уже были в byId");

  // Главная незакрытая дыра: 1 лот из 321 потерян, а health чист.
  gap(api0.isHealthWarn(r.res.health) === false,
    `собрано ${r.res.offers.length} из ${r.res.totalInJk}, но health чист (${JSON.stringify(r.res.health)}) ` +
    "и isHealthWarn=false: файл получит имя БЕЗ суффикса «_проверить», баннер в панели не покажется",
    "недобор попал в health и включил isHealthWarn");
}

// ===========================================================================
// 7. Недобор внутри комнаты -> дробление по цене
// ===========================================================================
async function testPriceSplitTriggers() {
  section("Недобор внутри комнаты -> дробление по цене");
  const universe = [...makeUniverse(), { id: 9999, room: 8, price: 12_000_000 }];
  const r = record("cap=1 -> комнаты + цена", await runCollect({ offers: universe, cap: 1 }));

  const segs = segments(r.mock);
  const priced = segs.filter((s) => s.gte != null);
  check(priced.length > 0,
    `при недоборе внутри комнаты включилось дробление по цене: ${priced.length} ценовых сегментов`,
    "недобор внутри комнаты не привёл к дроблению по цене — priceSplit не вызывается");
  eq(r.res.offers.length, 320, "дробление по цене добрало всё достижимое");

  // Порядок: цена дробится ТОЛЬКО внутри уже недобравшего комнатного сегмента.
  const firstPriced = r.mock.requests.findIndex((q) => q.gte != null);
  const firstRoom = r.mock.requests.findIndex((q) => q.rooms != null);
  check(firstRoom >= 0 && firstRoom < firstPriced,
    `порядок декомпозиции соблюдён: прямой проход -> комнаты (#${firstRoom + 1}) -> цена (#${firstPriced + 1})`,
    `дробление по цене (#${firstPriced + 1}) началось раньше разложения по комнатности (#${firstRoom + 1})`);
  check(priced.every((s) => s.rooms != null),
    "все ценовые сегменты уточняют комнатный фильтр, а не отменяют его",
    () => `есть ценовой сегмент без фильтра room — комнатный фильтр потерян: ${JSON.stringify(priced.find((s) => s.rooms == null))}`);

  // LIFO-стек берёт ВЕРХНЮЮ половину первой, а hi0 = priceCeiling = 3 млрд,
  // тогда как реальные цены — 5–105 млн. Первые уровни дробления обслуживают
  // заведомо пустые полосы, и каждая стоит два логических запроса.
  const emptyPriced = priced.filter((s) => s.pages.every((p) => p.got === 0));
  gap(emptyPriced.length > 0,
    `${emptyPriced.length} из ${priced.length} ценовых сегментов ушли в заведомо пустые полосы ` +
    `(${sum(emptyPriced.map((s) => s.http))} запросов впустую): границы берутся из priceCeiling=3 млрд, а не из цен уже собранных лотов`,
    "пустые ценовые полосы больше не запрашиваются");
}

// ===========================================================================
// 8. priceSplit: порог minPriceSpan, партиция, завершаемость
// ===========================================================================
// Враждебный мок: сервер ВСЕГДА рапортует total=99999, то есть total > seen
// выполняется на любом сегменте. Единственное, что может остановить дробление, —
// арифметика порога minPriceSpan. Пользовательский фильтр цены сужен до 3 млн,
// чтобы всё дерево уместилось в бюджет: тогда завершаемость доказывается САМА,
// а не потолком reqBudget.
async function testPriceSplitMath() {
  section("priceSplit: порог minPriceSpan, партиция диапазона, завершаемость");
  const LO = 5_000_000, HI = 8_000_000;
  const r = record("враждебный total=99999, фильтр ₽5–8 млн", await runCollect({
    offers: makeUniverse({ rooms: [2], perRoom: 100, priceOf: () => 6_000_000 }),
    base: makeBase({ room: { type: "terms", value: [2] }, price: { type: "range", value: { gte: LO, lte: HI } } }),
    cap: 1,
    faults: [{ when: () => true, then: { total: 99999 } }],
  }));

  check(!r.err, "collectAll завершилась, а не крутилась до бесконечности", `collectAll упала: ${r.err && r.err.message}`);
  check(r.logical < CFG.reqBudget,
    `дробление кончилось САМО за ${r.logical} логических страниц, не упёршись в бюджет ${CFG.reqBudget} — завершаемость обеспечена арифметикой порога, а не потолком`,
    `дробление остановил бюджет (${r.logical} страниц при reqBudget=${CFG.reqBudget}), а не порог minPriceSpan — завершаемость не доказана`);

  // Пользовательский фильтр цены — граница, за которую нельзя выходить: иначе
  // в файл попадут лоты, которых пользователь на странице не видел.
  const out = r.mock.requests.filter((q) => q.gte < LO || q.lte > HI);
  check(out.length === 0,
    `все ${r.mock.requests.length} запросов остались внутри пользовательского фильтра ₽${LO / 1e6}–${HI / 1e6} млн`,
    () => `${out.length} запросов вышли за пользовательский фильтр цены, первый: gte=${out[0].gte} lte=${out[0].lte}`);

  const ivs = [...new Set(r.mock.requests.filter((q) => q.gte != null).map((q) => q.gte + ":" + q.lte))]
    .map((s) => s.split(":").map(Number)).sort((a, b) => a[0] - b[0]);
  const known = new Set(ivs.map(([a, b]) => a + ":" + b));
  const isSplit = ([a, b]) => { const m = Math.floor((a + b) / 2); return known.has(a + ":" + m) && known.has((m + 1) + ":" + b); };
  const leaves = ivs.filter((iv) => !isSplit(iv));

  // Дыра в партиции = целая ценовая полоса не попадает в файл, причём именно в
  // том сценарии (недобор), ради которого дробление и делается.
  const holes = [];
  for (let i = 1; i < leaves.length; i++) if (leaves[i][0] !== leaves[i - 1][1] + 1) holes.push([leaves[i - 1][1], leaves[i][0]]);
  check(holes.length === 0,
    `листья партиции стыкуются встык: ${leaves.length} листьев, дыр и перекрытий 0`,
    () => `в партиции по цене ${holes.length} разрыв(ов), первый между ${holes[0][0]} и ${holes[0][1]} — лоты этой полосы не будут выгружены`);
  check(leaves.length > 0 && leaves[0][0] === LO && leaves[leaves.length - 1][1] === HI,
    `объединение листьев покрывает весь диапазон ${LO}…${HI}`,
    () => `листья покрывают ${leaves.length ? leaves[0][0] + "…" + leaves[leaves.length - 1][1] : "(ничего)"} вместо ${LO}…${HI}`);

  const splitSpans = ivs.filter(isSplit).map(([a, b]) => b - a);
  check(splitSpans.every((s) => s > CFG.minPriceSpan),
    `ни один диапазон уже minPriceSpan=${CFG.minPriceSpan} не дробился (минимальный из дроблёных — ${Math.min(...splitSpans)})`,
    () => `диапазон шириной ${Math.min(...splitSpans)} ≤ minPriceSpan всё-таки дробился — это путь к [a,a] и вечному циклу`);
  const minLeaf = Math.min(...leaves.map(([a, b]) => b - a));
  check(minLeaf <= CFG.minPriceSpan,
    `порог реально ограничивает глубину: самый узкий лист ${minLeaf} ≤ minPriceSpan=${CFG.minPriceSpan}`,
    `дробление не дошло до порога (самый узкий лист ${minLeaf}) — сценарий не проверяет minPriceSpan`);
  check(leaves.every(([a, b]) => b > a),
    "ни один лист не выродился в точку [a,a] (иначе при total>seen был бы вечный цикл)",
    () => `есть вырожденный лист: ${JSON.stringify(leaves.find(([a, b]) => b <= a))}`);

  // Оборотная сторона: total > seen, диапазон упёрся в порог — и никакого следа.
  gap(r.res.health.retries === 0 && r.res.health.totalDrift === 0 && api0.isHealthWarn(r.res.health) === false,
    `собрано ${r.res.offers.length} из ${r.res.totalInJk} (охват ${Math.round(100 * r.res.offers.length / r.res.totalInJk)}%), ` +
    "все диапазоны упёрлись в minPriceSpan — и ни счётчика, ни поля в health, ни предупреждения",
    "упирание в minPriceSpan попало в health");
}

// ===========================================================================
// 9. Три условия выхода из paginateSegment
// ===========================================================================
// Без любого из них сегмент либо зависает, либо жжёт по 54 пустых запроса.
async function testSegmentExits() {
  section("Три условия выхода из paginateSegment");

  // (a) потолок страниц: сегмент крупнее maxPages*pageSize=1512 лотов обрезается.
  const rA = await big();
  const firstA = segments(rA.mock)[0];
  eq(firstA.pages.length, CFG.maxPages, "(a) прямой проход остановлен ровно на maxPages страниц");
  eq(sum(firstA.pages.map((p) => p.got)), CFG.maxPages * CFG.pageSize, "(a) собрано лотов до обрезки (maxPages × pageSize)");

  // (b) две пустые страницы подряд: конец выдачи. Без этого выхода каждый
  // сегмент дожигал бы до 54 пустых запросов.
  const rB = record("(b) со 2-й страницы пусто", await runCollect({
    offers: makeUniverse({ rooms: [2], perRoom: 100 }),
    faults: [{ when: (f) => f.page >= 2, then: { empty: true } }],
  }));
  const firstB = segments(rB.mock)[0];
  check(firstB.pages.length === 3,
    "(b) сегмент остановился на двух пустых страницах подряд: 1 с данными + 2 пустые",
    `(b) сегмент не остановился на двух пустых страницах подряд: ${firstB.pages.length} запросов вместо 3`);

  // (c) ротация выдачи: сервер бесконечно отдаёт непустые страницы без новых id.
  // Единственная защита — page >= ceil(total/pageSize) + 2.
  const rC = record("(c) сервер всегда отдаёт первую страницу", await runCollect({
    offers: makeUniverse({ rooms: [2], perRoom: 100 }), repeatFirstPage: true,
  }));
  const firstC = segments(rC.mock)[0];
  const expectC = Math.ceil(100 / CFG.pageSize) + 2;
  check(firstC.pages.length === expectC,
    `(c) при ротации выдачи сегмент оборван на ${expectC}-й странице (ceil(total/pageSize)+2), а не крутился бесконечно`,
    `(c) защита от бесконечной ротации не сработала: ${firstC.pages.length} страниц вместо ${expectC}`);
  eq(sum(firstC.pages.map((p) => p.got)), expectC * CFG.pageSize, "(c) сервер отдавал одно и то же (лотов подано)");
  eq(rC.res.offers.length, 100, "(c) при ротации в выдачу попали только уникальные лоты");

  // Исчерпание бюджета и обрезка по maxPages нигде не фиксируются: наружу
  // отдаётся только {total, seen}, в health полей нет.
  gap(rA.res.health.totalDrift === 0 && api0.isHealthWarn(rA.res.health) === false,
    `прямой проход обрезан на ${CFG.maxPages * CFG.pageSize} лотах и бюджет исчерпан (${rA.logical} страниц), ` +
    `собрано ${rA.res.offers.length} из ${rA.res.totalInJk} — health чист, предупреждения не будет`,
    "обрезка по maxPages / исчерпание бюджета попали в health");
}

// ===========================================================================
// 10. Дрейф total
// ===========================================================================
// Циан отдаёт РАЗНЫЙ aggregatedCount на разных страницах одного сегмента.
// Код это считает — но решение о полноте принимает по ПОСЛЕДНЕЙ странице.
async function testTotalDrift() {
  section("Дрейф total между страницами одного сегмента");

  const rDown = record("дрейф вниз: 60 -> 30 на 2-й странице", await runCollect({
    offers: makeUniverse({ rooms: [2], perRoom: 60 }),
    faults: [{ when: { page: 2 }, then: { total: 30 } }],
  }));
  eq(rDown.res.health.totalDrift, 1, "дрейф total посчитан ровно один раз");
  // Порог по дрейфу поднят с нуля: у Циан дрейф total — норма, и предупреждение
  // на КАЖДОЙ выгрузке обесценивало сам суффикс «_проверить». Теперь о качестве
  // говорит недобор, а дрейф должен быть заметным (>2), чтобы тревожить.
  check(api0.isHealthWarn(rDown.res.health) === false,
    "единичный дрейф total больше не тревожит: он у Циан в порядке вещей",
    "единичный дрейф total всё ещё включает isHealthWarn — предупреждение обесценится");
  check(api0.isHealthWarn({ requests: 10, retries: 0, totalDrift: 0, shortfall: 5 }) === true,
    "зато недобор включает isHealthWarn — это и есть признак неполной выгрузки",
    "недобор не включает isHealthWarn: главный признак качества не виден пользователю");
  check(api0.isHealthWarn({ requests: 10, retries: 0, totalDrift: 0, budgetExhausted: true }) === true,
    "исчерпание бюджета включает isHealthWarn",
    "исчерпание бюджета не видно пользователю");
  gap(rDown.res.totalInJk === 30 && rDown.res.offers.length === 56,
    `totalInJk взят с ПОСЛЕДНЕЙ страницы (${rDown.res.totalInJk}) при ${rDown.res.offers.length} собранных лотах — ` +
    "в панели это охват 187%, зажатый до 100%, и сегмент объявлен полным раньше времени",
    "totalInJk фиксируется по первой странице и не переписывается дрейфом");

  const rZero = record("пустая 3-я страница с aggregatedCount=0", await runCollect({
    offers: makeUniverse({ rooms: [2], perRoom: 60 }),
    faults: [{ when: (f) => f.page >= 3, then: { empty: true, total: 0 } }],
  }));
  check(rZero.res.health.totalDrift >= 1,
    `расхождение total зафиксировано (totalDrift=${rZero.res.health.totalDrift})`,
    "обнуление total на пустой странице не попало в totalDrift");
  gap(rZero.res.totalInJk === 0 && rZero.res.offers.length === 56,
    `пустая страница с aggregatedCount=0 затёрла честный total: totalInJk=0 при ${rZero.res.offers.length} собранных из 60. ` +
    "Декомпозиция отменена (`if (grandTotal && …)`), а в книге охват покажется как 100% (`totalInJk || rows.length`)",
    "пустая страница больше не обнуляет total сегмента");
}

// ===========================================================================
// 11. 403 в середине сегмента
// ===========================================================================
async function testForbiddenMidSegment() {
  section("403 на НЕ первой странице сегмента");
  const r = record("403 со 2-й страницы любого сегмента", await runCollect({
    offers: makeUniverse({ rooms: [2], perRoom: 60 }),
    faults: [{ when: (f) => f.page >= 2, then: { status: 403 } }],
  }));
  check(!r.err,
    "403 на не первой странице не роняет весь сбор (сегмент просто обрывается)",
    `403 на не первой странице уронил весь сбор: ${r.err && r.err.message}`);
  check(r.res.offers.length > 0,
    `уже собранные лоты сохранены: ${r.res.offers.length} шт.`,
    "после 403 в середине сегмента выдача оказалась пустой");

  const fatal = r.mock.requests.filter((q) => q.status === 403).length;
  eq(r.http - r.logical, fatal, "каждый лишний HTTP сверх логических страниц — это 403 без ретрая");
  gap(r.res.health.retries === 0 && Object.keys(r.res.health.retryStatuses).length === 0,
    `${fatal} фатальных 403 не оставили в health ни следа (${JSON.stringify(r.res.health)}): ` +
    `health.requests=${r.logical} против ${r.http} реальных HTTP — по диагностике невозможно понять, что Циан отказывал`,
    "оборванные сегменты попали в health");
  gap(fatal > 5,
    `на отказ сервера планировщик ответил РОСТОМ нагрузки: после первого 403 сделано ещё ${fatal - 1} обращений ` +
    "(обрыв на странице ≥2 неотличим от штатного недобора, поэтому запускается дробление)",
    "обрыв сегмента больше не приводит к дроблению");
}

// ===========================================================================
// 12. Бюджет запросов и РЕАЛЬНОЕ число HTTP
// ===========================================================================
// Главная цифра всей работы. reqBudget ограничивает логические страницы
// (requests++ стоит ПОСЛЕ успешного apiFetch), а ретраи внутри apiFetch в него
// не входят вовсе. Сколько это в реальных обращениях к api.cian.ru — до сих пор
// никто не измерял, а от ответа зависит, выживут ли фичи (а)/(б)/(в).
async function testBudget() {
  section("Бюджет запросов против реальных HTTP");
  const HARD_CAP = 2 * CFG.reqBudget * CFG.maxRetries + CFG.maxRetries;   // 3204

  // (1) бюджет сам по себе: 20000 лотов, отказов нет (тот же прогон, что и в
  // проверке обрезки по maxPages).
  const rB = await big();
  check(rB.logical <= CFG.reqBudget,
    `бюджет запросов соблюдён: ${rB.logical} логических страниц при reqBudget=${CFG.reqBudget}`,
    `бюджет запросов не соблюдён: ${rB.logical} логических страниц при reqBudget=${CFG.reqBudget}`);
  eq(rB.logical, CFG.reqBudget, "бюджет выбран до конца (сценарий действительно упирается в потолок)");
  eq(rB.ok200, rB.logical, "health.requests совпадает с числом успешных ответов мока");
  eq(rB.http, rB.logical, "без отказов реальных HTTP ровно столько же, сколько логических страниц");

  // (2) усиление на троттлинге: каждая четвёртая попытка успешна.
  const rAmp = record("усиление: успешна каждая 4-я попытка", await runCollect({
    offers: [...makeUniverse(), { id: 9999, room: 8, price: 12_000_000 }], cap: 2,
    faults: [{ when: (f, i) => i % 4 !== 0, then: { status: 429 } }],
  }, { усиление: 4 }));
  const amp = rAmp.http / rAmp.logical;
  eq(rAmp.logical, 20, "логических страниц столько же, сколько без отказов");
  check(Math.abs(amp - CFG.maxRetries) < 0.01,
    `один бюджетный юнит стоит до maxRetries HTTP: ${rAmp.http} обращений на ${rAmp.logical} страниц = ×${amp.toFixed(2)}`,
    `усиление изменилось: ${rAmp.http}/${rAmp.logical} = ×${amp.toFixed(2)} вместо ×${CFG.maxRetries}`);

  // (3) худший случай: страница 1 отдаётся с четвёртой попытки, страницы ≥2
  // отказывают всегда. Это модель сессии, которую Циан уже душит.
  let a = 0;
  const rW = record("худший случай: p1 = 429×3+200, p≥2 = 429 всегда", await runCollect({
    offers: makeUniverse({ rooms: [9, 7, 1, 2, 3, 4, 5, 6], perRoom: 2500 }),
    faults: [{ when: (f) => (f.page === 1 ? (++a % 4 !== 0) : true), then: { status: 429 } }],
  }));
  // Предохранителем стал бюджет РЕАЛЬНЫХ обращений, поэтому до потолка
  // логических страниц прогон в патологии больше не доходит — и это цель, а не
  // регресс: раньше 400 «страниц» стоили 2472 обращения к Циан.
  check(rW.logical <= CFG.reqBudget, `логических страниц ${rW.logical} ≤ ${CFG.reqBudget}`,
    `логических страниц ${rW.logical} — больше мягкого лимита ${CFG.reqBudget}`);
  check(rW.http <= CFG.httpBudget, `реальных обращений ${rW.http} ≤ httpBudget ${CFG.httpBudget}`,
    `реальных обращений ${rW.http} — БОЛЬШЕ бюджета ${CFG.httpBudget}: предохранитель не сработал`);
  check(rW.res && rW.res.health.budgetExhausted === true,
    "исчерпание бюджета помечено в health, а собранное сохранено",
    "исчерпание бюджета не помечено — неполная выгрузка неотличима от полной");
  check(rW.http <= HARD_CAP,
    `реальных HTTP ${rW.http} ≤ жёсткого потолка ${HARD_CAP} (2·reqBudget·maxRetries + maxRetries)`,
    `реальных HTTP ${rW.http} — БОЛЬШЕ расчётного потолка ${HARD_CAP}: появился ещё один путь ретраев, формулу надо пересчитать`);
  gap(rW.http > 5 * CFG.reqBudget,
    () => `при бюджете ${CFG.reqBudget} сделано ${rW.http} реальных обращений к api.cian.ru (×${(rW.http / rW.logical).toFixed(2)}), ` +
    `ретраев ${rW.res.health.retries}. Для фичи (а) decorations_list (×4 запросов) это ~${(rW.http * 4 / 1000).toFixed(1)} тыс. обращений: ` +
    "бюджет надо переносить с логических страниц на реальные HTTP",
    `бюджет теперь ограничивает и реальные HTTP (${rW.http} при reqBudget=${CFG.reqBudget})`);

  // (4) второй предохранитель — по стенным часам. Бюджет обращений времени не
  // ограничивает: 900 обращений, каждое с бэкоффом до waitCeiling, давали 42
  // минуты. В ЖЁСТКОЙ патологии первым срабатывает именно он.
  const spentMs = rW.clock.elapsed();
  check(spentMs <= CFG.timeBudgetMs + CFG.waitCeiling,
    `прогон уложился в timeBudgetMs: ${hh(spentMs)} при потолке ${hh(CFG.timeBudgetMs)}`,
    `прогон занял ${hh(spentMs)} при потолке ${hh(CFG.timeBudgetMs)} — предохранитель по времени не сработал`);
  check(spentMs > CFG.timeBudgetMs * 0.9 && rW.http < CFG.httpBudget,
    `в жёсткой патологии первым срабатывает именно ВРЕМЯ (${hh(spentMs)}), а бюджет обращений ещё не выбран (${rW.http} из ${CFG.httpBudget})`,
    `сценарий перестал упираться во время: ${hh(spentMs)}, HTTP ${rW.http} из ${CFG.httpBudget} — проверка потолка по часам стала холостой`);

  // (5) МЯГКИЙ троттлинг — там, где связывает уже бюджет обращений, а не время.
  // Каждая страница отдаётся с третьей попытки: 3 HTTP на страницу и всего
  // ~5 с бэкоффа, поэтому за полчаса успевает накопиться 900 обращений. Без
  // этого сценария httpBudget проверялся бы «в тени» потолка по времени: тот
  // срабатывал раньше и делал проверку холостой.
  let m = 0;
  const rM = record("мягкий троттлинг: успешна каждая 3-я попытка", await runCollect({
    offers: makeUniverse({ rooms: [9, 7, 1, 2, 3, 4, 5, 6], perRoom: 2500 }),
    faults: [{ when: () => (++m % 3 !== 0), then: { status: 429 } }],
  }));
  check(rM.http <= CFG.httpBudget, `реальных обращений ${rM.http} ≤ httpBudget ${CFG.httpBudget}`,
    `реальных обращений ${rM.http} — БОЛЬШЕ бюджета ${CFG.httpBudget}: предохранитель не сработал`);
  check(rM.http >= CFG.httpBudget * 0.95 && rM.clock.elapsed() < CFG.timeBudgetMs && rM.logical < CFG.reqBudget,
    `и связывает здесь именно он: ${rM.http} обращений за ${hh(rM.clock.elapsed())} на ${rM.logical} страницах ` +
    `(время ещё не вышло, логических страниц ещё не ${CFG.reqBudget})`,
    `сценарий перестал упираться в httpBudget: HTTP ${rM.http}, время ${hh(rM.clock.elapsed())}, страниц ${rM.logical}`);
  check(rM.res && rM.res.health.budgetExhausted === true,
    "исчерпание бюджета обращений помечено в health",
    "исчерпание бюджета обращений не помечено");

  // Бэкофф не переносится между страницами: delay — локальная переменная
  // apiFetch, поэтому каждая новая страница начинает эскалацию заново с 1500 мс.
  const firstFour = rW.clock.sleeps("backoff").slice(0, 3);
  const nextFour = rW.clock.sleeps("backoff").slice(3, 6);
  gap(nextFour.length === 3 && nextFour[0] < firstFour[2],
    () => `бэкофф не переносится между страницами: ${firstFour.join("→")} мс, затем снова ${nextFour.join("→")} мс — ` +
    "сессия, которую уже душат, получает полный цикл эскалации на КАЖДОЙ странице",
    "бэкофф сохраняется между страницами одного сбора");
}

// ===========================================================================
// 13. Телеметрия
// ===========================================================================
// Журнал — основание для всего дальнейшего: константы темпа берутся из
// измеренного, а не из головы, и он же отвечает на открытые вопросы (читается
// ли Retry-After через CORS, бывает ли на этом эндпоинте 429 вообще).
// Поэтому главный инвариант — ПОЛНОТА: одна запись на каждое обращение.
async function testTelemetry() {
  section("Телеметрия: одна запись на каждое обращение");

  const rT = record("телеметрия: троттлинг + обрывы", await runCollect({
    offers: makeUniverse({ rooms: [1, 2, 3], perRoom: 200 }), cap: 2,
    faults: [
      { when: (f, i) => i % 5 === 0, then: { status: 429, retryAfter: 3 } },
      { when: (f, i) => i % 7 === 0, then: { throw: "network" } },
    ],
  }));
  // Самый дешёвый и самый сильный инвариант: ловит ЛЮБОЙ пропуск учёта.
  eq(rT.log.length, rT.http, "записей в журнале ровно столько, сколько реальных обращений");
  eq(rT.agg.http, rT.http, "и агрегат сходится с ними же");
  check(rT.log.every((r) => r.dur != null && r.att >= 1 && r.gap != null && r.seg && r.page != null),
    "у каждой записи есть длительность, номер попытки, зазор, сегмент и страница",
    () => `неполные записи: ${JSON.stringify(rT.log.filter((r) => r.dur == null || !r.seg).slice(0, 2))}`);

  // Метка сегмента — то, чем запись сопоставляется с маршрутом. Без неё
  // «одна страница дорого» и «много страниц» в журнале неразличимы.
  const segs = new Set(rT.log.map((r) => r.seg));
  check(segs.size > 3 && [...segs].some((s) => /^r\d/.test(s)),
    `сегменты различимы в журнале: ${[...segs].slice(0, 4).join(", ")}… (всего ${segs.size})`,
    `метки сегментов бесполезны: ${JSON.stringify([...segs].slice(0, 5))}`);

  // Ретраи одной страницы отличимы от разных страниц: att растёт внутри одной.
  const retried = rT.log.filter((r) => r.att > 1);
  check(retried.length > 0 && retried.every((r) => r.att <= CFG.maxRetries),
    `повторы видны отдельно: ${retried.length} записей с att>1, максимум ${Math.max(...retried.map((r) => r.att))}`,
    "повторные попытки в журнале неотличимы от первых");

  // raSeen закрывает открытый вопрос: Retry-After не входит в CORS-safelist,
  // и ветка «уважаем просьбу сервера» может быть мёртвой в бою. На стенде
  // заголовок читается — значит поле работает и в бою даст честный ответ.
  check(rT.agg.raSeen > 0 && rT.log.filter((r) => r.raSeen).every((r) => r.st === 429),
    `Retry-After зафиксирован ${rT.agg.raSeen} раз и только на 429 — поле готово ответить, читается ли он в бою`,
    `raSeen считается неверно: ${rT.agg.raSeen}, статусы ${JSON.stringify(rT.log.filter((r) => r.raSeen).map((r) => r.st))}`);

  // Агрегат — ровно то, что поедет в хранилище. Его размер и есть ограничение.
  const bytes = JSON.stringify(rT.agg).length;
  check(bytes <= 400,
    `агрегат прогона занимает ${bytes} символов (кольцевой буфер на 200 прогонов ≈ ${Math.round(bytes * 200 / 1024)} КБ)`,
    `агрегат раздулся до ${bytes} символов — 200 прогонов дадут ${Math.round(bytes * 200 / 1024)} КБ при квоте 5 МиБ на весь ориджин`);
  const rawBytes = JSON.stringify(rT.log).length;
  console.log(`    · сырой журнал этого прогона: ${rawBytes} символов (${Math.round(rawBytes / rT.log.length)} на запись) — ` +
    "в хранилище он не попадает никогда, только в скачиваемую книгу");
  check(rT.agg.byStatus && Object.keys(rT.agg.byStatus).length >= 2,
    `в агрегате разбивка по статусам: ${JSON.stringify(rT.agg.byStatus)}`,
    "агрегат не различает статусы — по нему нельзя отличить троттлинг от сбоя сети");
  check(rT.agg.p50 != null && rT.agg.p95 != null && rT.agg.p95 >= rT.agg.p50,
    `перцентили длительности посчитаны: p50=${rT.agg.p50} мс, p95=${rT.agg.p95} мс (главный ранний признак «проверки браузера»)`,
    `перцентили сломаны: p50=${rT.agg.p50}, p95=${rT.agg.p95}`);

  // Журнал не должен пережить прогон: следующий начинается с чистого листа.
  const rT2 = await runCollect({ offers: makeUniverse({ rooms: [2], perRoom: 30 }) });
  check(rT2.log.length === rT2.http,
    "следующий прогон пишет свой журнал с нуля",
    `журнал протёк между прогонами: ${rT2.log.length} записей на ${rT2.http} обращений`);
}

// ===========================================================================
// 14. Отмена сбора
// ===========================================================================
// Отмены не было ни в каком виде: нажать «Выгрузить» и передумать было нельзя,
// вкладка оставалась занята до конца — а конец в патологии наступал через
// десятки минут. Проверяется ровно то, что делает кнопку не декоративной:
// после нажатия к Циан больше НЕ ходят, а собранное не выбрасывается.
async function testCancel() {
  section("Отмена: кнопка останавливает сбор и сохраняет собранное");

  const rC = record("отмена после 5-го обращения", await runCollect({
    offers: makeUniverse({ rooms: [9, 7, 1, 2, 3, 4, 5, 6], perRoom: 2500 }),
    cancelAfter: 5,
  }));
  check(!rC.err, "отмена не превращается в исключение — вызывающий получает результат",
    `отмена вылетела исключением: ${rC.err && rC.err.message}`);
  eq(rC.http, 5, "после отмены к Циан не ушло НИ ОДНОГО лишнего обращения");
  check(rC.res && rC.res.health.cancelled === true,
    "отмена помечена в health.cancelled",
    "отмена не помечена — неполная выгрузка неотличима от полной");
  check(rC.res && rC.res.offers.length > 0,
    `собранное до отмены сохранено: ${rC.res ? rC.res.offers.length : 0} лотов`,
    "отмена выбросила всё собранное");
  check(rC.api.isHealthWarn(rC.res.health),
    "отменённый сбор помечен как требующий проверки (имя файла получит «_проверить»)",
    "отменённый сбор выглядит как штатный");
  // Причина обязана дойти до человека дословно — это то, что он прочтёт в
  // панели и в листе «Сводка».
  const why = rC.api.healthReasons(rC.res.health);
  check(why.some((s) => /Отмена/.test(s)),
    `причина названа прямо: «${why[0]}»`,
    `среди причин нет отмены: ${JSON.stringify(why)}`);

  // Токен обязан умереть вместе с прогоном, иначе следующий запуск начнётся
  // уже «отменённым» и упадёт на первом же обращении.
  check(rC.api.isCancelled() === false,
    "после прогона токен отмены сброшен — следующий запуск начнётся с чистого листа",
    "токен отмены пережил прогон: повторная выгрузка отменится сразу");
  const again = await rC.api.collectAll(makeBase(), () => {});
  check(again.offers.length > 0 && !again.health.cancelled,
    `повторный сбор на том же экземпляре идёт штатно: ${again.offers.length} лотов`,
    "повторный сбор после отмены не работает");

  // Прерываемость сна — то, без чего кнопка декоративна: на бэкоффе в
  // waitCeiling=60 с пользователь нажал бы «Отмена» и ещё минуту ждал.
  // Проверяется на НАСТОЯЩИХ таймерах: на виртуальных любой сон мгновенен.
  const rt = makeFactory()(async () => ({ status: 200, headers: { get: () => null }, json: async () => ({}) }),
    globalThis.setTimeout, makeConsole(), Math, Date);
  rt.beginRun();
  const t0 = Date.now();
  const slept = rt.sleep(CFG.waitCeiling);          // 60 с
  rt.cancelRun();
  const won = await Promise.race([slept.then(() => "разбудили"),
    new Promise((r) => globalThis.setTimeout(() => r("не разбудили"), 500))]);
  rt.endRun();
  check(won === "разбудили",
    `спящий сбор просыпается по отмене за ${Date.now() - t0} мс вместо ${CFG.waitCeiling / 1000} с`,
    `sleep(${CFG.waitCeiling}) не прервался за 500 мс — «Отмена» на бэкоффе декоративна`);
}

// ===========================================================================
// 15. Паузы между обращениями
// ===========================================================================
async function testPacing() {
  section("Паузы между обращениями к API");
  const r = record("стыки сегментов: cap=1, две комнатности", await runCollect({
    offers: makeUniverse({ rooms: [9, 7], perRoom: 40 }), cap: 1,
  }));
  const inSeg = segments(r.mock).reduce((n, s) => n + Math.max(0, s.pages.length - 1), 0);
  const outOfRange = r.clock.log.filter((s) => s.ms < CFG.delayMin || s.ms >= CFG.delayMax);
  check(outOfRange.length === 0,
    `все ${r.clock.log.length} пауз внутри [${CFG.delayMin}, ${CFG.delayMax}) — CONFIG.delayMin/Max соблюдены`,
    () => `${outOfRange.length} пауз вышли за [${CFG.delayMin}, ${CFG.delayMax}), первая — ${outOfRange[0].ms} мс`);
  check(r.clock.log.length >= inSeg,
    `между страницами внутри сегментов паузы есть (${inSeg} межстраничных переходов, ${r.clock.log.length} пауз)`,
    `пауз меньше, чем переходов между страницами: ${r.clock.log.length} против ${inSeg}`);
  gap(r.clock.log.length < r.http - 1,
    `${r.http - 1 - r.clock.log.length} переходов между сегментами прошли ЗАЛПОМ без паузы ` +
    `(${r.http} обращений, ${r.clock.log.length} пауз): priceSplit стартует следующий диапазон немедленно. ` +
    "Для фичи (а) decorations_list число стыков вырастет вчетверо",
    "пауза стоит перед каждым обращением, включая стыки сегментов");
}

// ===========================================================================
// 14. Честные таймеры (smoke)
// ===========================================================================
// Ловит класс ошибок «тест зелёный только потому, что таймеры мгновенные»:
// если кто-то заменит `await sleep(ms)` на голый setTimeout без await,
// виртуальный журнал этого не заметит, а настенные часы — заметят.
async function testRealTimers() {
  section("Честные таймеры (smoke, единственный медленный тест)");
  if (process.env.CIAN_SKIP_REALTIMERS) { console.log("  · пропущен (CIAN_SKIP_REALTIMERS)"); return; }
  const r = await runCollect({ offers: makeUniverse({ rooms: [2], perRoom: 70 }), real: true });
  const asked = r.clock.total(), wall = r.clock.wall();
  check(wall >= asked * 0.8,
    `sleep-ы действительно ожидаются: запрошено ${Math.round(asked)} мс, прошло ${wall} мс настенных`,
    `запрошено ${Math.round(asked)} мс сна, а прошло всего ${wall} мс — паузы не дожидаются (потерян await у sleep?)`);
  eq(r.res.offers.length, 70, "smoke на честных таймерах собрал все лоты");
}

// ===========================================================================
// 15. Сводка прогонов (снимок)
// ===========================================================================
// Инварианты выше отвечают на «что сломалось». Снимок отвечает на «сколько это
// стоит»: в диффе видно цену правки планировщика в запросах и минутах.
function snapshotSummary() {
  section("Сводка прогонов");
  const actual = { _format: "cian-collect-summary-1", runs };
  const line = (r) => `${r.name}: собрано ${r.собрано}/${r.всего}, HTTP ${r.http}, логических ${r.логических}, ` +
    `сегментов ${r.сегментов}, ретраев ${r.ретраев}, дрейф ${r.дрейф}, вирт. ${r.виртСек} с` + (r.бросок ? `, бросок: ${r.бросок}` : "");

  if (process.env.UPDATE) {
    fs.writeFileSync(SNAPSHOT, JSON.stringify(actual, null, 1) + "\n");
    console.log(`  Сводка перезаписана: ${SNAPSHOT} (${runs.length} сценариев)`);
    runs.forEach((r) => console.log("    " + line(r)));
    return;
  }
  if (!fs.existsSync(SNAPSHOT)) {
    fail(`нет сводки ${SNAPSHOT} — создайте её: UPDATE=1 node tests/check_collect.mjs`);
    return;
  }
  const expected = JSON.parse(fs.readFileSync(SNAPSHOT, "utf8"));
  const byName = new Map(expected.runs.map((r) => [r.name, r]));
  let diffs = 0;
  for (const r of runs) {
    const e = byName.get(r.name);
    if (!e) { fail(`сценарий «${r.name}» отсутствует в сводке — обновите её: UPDATE=1 node tests/check_collect.mjs`); diffs++; continue; }
    const changed = Object.keys(r).filter((k) => JSON.stringify(r[k]) !== JSON.stringify(e[k]));
    if (changed.length) {
      diffs++;
      fail(`«${r.name}» изменился по полям ${changed.join(", ")}:\n      было:  ${line(e)}\n      стало: ${line(r)}`);
    }
  }
  for (const e of expected.runs) if (!runs.some((r) => r.name === e.name)) { fail(`сценарий «${e.name}» исчез из прогона`); diffs++; }
  if (!diffs) {
    pass(`цена сбора не изменилась ни в одном из ${runs.length} сценариев (запросы, сегменты, ретраи, виртуальное время)`);
  } else {
    console.error("\n  Если изменение планировщика СОЗНАТЕЛЬНОЕ — сверьте дифф глазами (он показывает цену правки\n" +
                  "  в запросах и минутах) и обновите сводку:  UPDATE=1 node tests/check_collect.mjs");
  }
  runs.forEach((r) => console.log("    · " + line(r)));
}

// ===========================================================================
// 16. НЕГАТИВНЫЙ КОНТРОЛЬ
// ===========================================================================
// Тест, который никогда не краснел, ничего не проверяет. Вносим во ВРЕМЕННУЮ
// копию content.js по одной поломке планировщика и требуем, чтобы прогон
// падал — и чтобы падал с сообщением про ПРАВИЛЬНУЮ причину.
const BREAKAGES = [
  {
    name: "снята проверка бюджета в paginateSegment",
    from: "      while (page <= CONFIG.maxPages && requests < CONFIG.reqBudget) {",
    to: "      while (page <= CONFIG.maxPages) {",
    expect: "бюджет запросов не соблюдён",
  },
  {
    name: "сломан дедуп по cianId (byId ключуется порядковым номером)",
    from: "if (id != null) byId.set(id, o); });",
    to: 'if (id != null) byId.set(id + "#" + byId.size, o); });',
    expect: "дедуп между сегментами не работает",
  },
  {
    name: "убран выход по двум пустым страницам подряд",
    from: "if (++empty >= 2) break;",
    to: "if (++empty >= 999) break;",
    expect: "не остановился на двух пустых страницах подряд",
  },
  {
    // Настоящий предохранитель после шага 2.1. Мягкий лимит по логическим
    // страницам его не подменяет: ретраи живут внутри apiFetch и в requests
    // не попадают — без этой строки патология снова стоит тысячи обращений.
    name: "снят бюджет реальных HTTP в apiFetch",
    from: '        if (health.http >= CONFIG.httpBudget) throw BudgetError("запросов");',
    to: '        if (false) throw BudgetError("запросов");',
    expect: "БОЛЬШЕ бюджета",
  },
  {
    // Второй предохранитель. Бюджет обращений времени не ограничивает: без
    // этой строки тот же патологический прогон снова растягивается на часы.
    name: "снят потолок по стенным часам",
    from: '        if (health.t0 && Date.now() - health.t0 >= CONFIG.timeBudgetMs) throw BudgetError("времени");',
    to: '        if (false) throw BudgetError("времени");',
    expect: "предохранитель по времени не сработал",
  },
  {
    // Отмена, которая ничего не отменяет: кнопка нажимается, флаг ставится, а
    // apiFetch его не читает и продолжает ходить к Циан.
    name: "apiFetch перестал замечать отмену",
    from: "  const isCancelled = () => !!(cancelToken && cancelToken.cancelled);",
    to: "  const isCancelled = () => false;",
    expect: "после отмены к Циан не ушло",
  },
  {
    // Сон, который нельзя прервать: кнопка «Отмена» на бэкоффе становится
    // декоративной — пользователь ждёт до waitCeiling независимо от нажатия.
    name: "sleep снова непрерываемый",
    from: "    if (t) t.wakes.add(fire);",
    to: "    if (false) t.wakes.add(fire);",
    expect: "не прервался за 500 мс",
  },
  {
    // Возврат к поведению до 2.19: r.json() зовётся на любом 200, HTML даёт
    // SyntaxError, и капча ретраится как сетевая ошибка — код отвечает на
    // антибот увеличением нагрузки.
    name: "проверка content-type убрана: капча снова ретраится",
    from: "        if (r.status === 403 || ct === \"html\") {",
    to: "        if (r.status === 403) {",
    expect: "200 с HTML",
  },
  {
    // Журнал с дырами хуже отсутствующего: по нему будут настраивать темп.
    name: "часть обращений не попадает в журнал",
    from: "  const teleLog = (rec) => { if (tele && tele.length < TELE_MAX) tele.push(rec); };",
    to: "  const teleLog = (rec) => { if (tele && tele.length < TELE_MAX && rec.att === 1) tele.push(rec); };",
    expect: "записей в журнале ровно столько",
  },
];

function negativeControl() {
  const src = fs.readFileSync(CONTENT_JS, "utf8");
  const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || "/tmp", "cian-neg-"));
  let bad = 0;
  console.log(`НЕГАТИВНЫЙ КОНТРОЛЬ: ${BREAKAGES.length} поломки во временных копиях content.js\n  (${dir})`);
  for (const [k, b] of BREAKAGES.entries()) {
    console.log(`\n── ${b.name} ─────────────────────`);
    const n = src.split(b.from).length - 1;
    if (n !== 1) {
      console.error(`  ✗ якорь поломки не найден или неоднозначен (${n} вхождений): «${b.from}»`);
      bad++; continue;
    }
    // Имя файла нумеруем: кириллица целиком уходит в \W, и две поломки подряд
    // писались бы в один и тот же «____________.js».
    const file = path.join(dir, `${k + 1}-${b.expect.slice(0, 12).replace(/\W/g, "_")}.js`);
    fs.writeFileSync(file, src.replace(b.from, b.to));
    const out = spawnSync(process.execPath, [fileURLToPath(import.meta.url)], {
      encoding: "utf8",
      env: { ...process.env, CIAN_CONTENT_JS: file, CIAN_SKIP_REALTIMERS: "1", UPDATE: "", NEGATIVE: "" },
    });
    const all = (out.stdout || "") + (out.stderr || "");
    const hit = all.split("\n").filter((l) => l.includes("✗") && l.includes(b.expect));
    if (out.status === 0) { console.error(`  ✗ тест остался ЗЕЛЁНЫМ на сломанном коде (код возврата 0)`); bad++; }
    else console.log(`  ✓ тест покраснел (код возврата ${out.status})`);
    if (!hit.length) {
      console.error(`  ✗ среди падений нет сообщения про «${b.expect}» — тест краснеет не по той причине`);
      console.error((all.split("\n").filter((l) => l.includes("✗")).slice(0, 4).map((l) => "      " + l.trim()).join("\n")) || "      (падений вообще нет)");
      bad++;
    } else {
      console.log(`  ✓ причина названа верно:\n      ${hit[0].trim()}`);
      const others = all.split("\n").filter((l) => l.includes("✗") && !l.includes(b.expect)).length;
      if (others) console.log(`      (плюс ${others} сопутствующих падений — поломка задевает и соседние инварианты)`);
    }
  }
  console.log(bad ? `\nНЕГАТИВНЫЙ КОНТРОЛЬ ПРОВАЛЕН: ${bad}` : "\nНегативный контроль пройден: каждая поломка ловится и названа верно.");
  return bad ? 1 : 0;
}

// ===========================================================================
async function main() {
  const t0 = Date.now();
  console.log(`Слой сбора: ${CONTENT_JS}`);
  // Срез — единственная точка, где тест может умереть ДО первой проверки.
  // Умирать он обязан внятно: «якорь не найден / срез неполон / срез захватил
  // браузерный код», а не стек-трейсом из недр eval.
  try { bootstrap(); }
  catch (e) {
    fail(`не удалось вырезать слой сбора из content.js:\n      ${e.message.split("\n").join("\n      ")}`);
    return 1;
  }
  await testSlice();
  await testResponseShapes();
  await testFailures();
  await testWithFilters();
  await testCleanRun();
  await testRoomsDecomposition();
  await testPriceSplitTriggers();
  await testPriceSplitMath();
  await testSegmentExits();
  await testTotalDrift();
  await testForbiddenMidSegment();
  await testBudget();
  await testTelemetry();
  await testCancel();
  await testPacing();
  await testRealTimers();
  snapshotSummary();

  const wall = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nПрогон ${wall} с. Известных дыр (△): ${gaps}${closed ? `, закрыто с прошлого раза: ${closed}` : ""}.`);
  console.log(failed ? `ПРОВАЛЕНО: ${failed}` : "Всё зелено.");
  return failed ? 1 : 0;
}

const isMain = process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;
if (isMain) process.exit(process.env.NEGATIVE ? negativeControl() : await main());
