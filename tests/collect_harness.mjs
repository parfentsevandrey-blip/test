// Стенд слоя сбора extension/content.js: вырезаем НАСТОЯЩИЙ код планировщика
// запросов, исполняем его в node со стабами сети/времени/случайности и снимаем
// журналы, по которым потом пишутся утверждения (tests/check_collect.mjs).
//
// ЗАЧЕМ ОТДЕЛЬНЫЙ ФАЙЛ. Стенд переиспользуется: следующие на очереди фичи —
// перебор decorations_list (×4 запросов), фоновые алерты (запросы без человека
// за рулём) и накопление по нескольким ЖК — все упираются в тот же планировщик.
// Тест с утверждениями будет свой у каждой, а мок сети, виртуальные часы и
// резка среза — общие.
//
// ПОЧЕМУ ВЫРЕЗ, А НЕ КОПИЯ. content.js — один IIFE в MAIN-world без модулей и
// сборки; экспортировать из него нечего. Копия кода в тест означала бы тест,
// который проверяет копию, а не то, что поедет пользователю. Поэтому режем
// исходник по семантическим якорям и исполняем через eval.

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
export const CONTENT_JS = process.env.CIAN_CONTENT_JS || path.join(ROOT, "extension", "content.js");

// ===========================================================================
// 1. РЕЗКА СРЕЗА
// ===========================================================================
// Границы проводятся не по номерам строк и не по комментариям (и то и другое
// уезжает при любой правке), а по СМЫСЛУ:
//   срез A = код, который ещё не знает про браузер (константы сбора);
//   срез B = сам планировщик, от токена отмены до начала слоя нормализации.
// Слова document/window/XMLHttpRequest — это Web API, их не переименуют при
// рефакторинге, поэтому граница «кончился код без браузера» устойчивее любого
// имени функции.

const DOM = /(^|[^\w$])(document|window|location|XMLHttpRequest|navigator|localStorage)([^\w$]|$)/;
const isComment = (l) => /^\s*(\/\/|\*|\/\*)/.test(l);

function anchor(src, needle, what) {
  const i = src.indexOf(needle);
  if (i < 0) {
    throw new Error(
      `якорь среза не найден: «${needle}» (${what}) в ${CONTENT_JS}\n` +
      `    Слой сбора переехал или переименован. Почините якорь в tests/collect_harness.mjs —\n` +
      `    НЕ удаляйте проверку: без среза тест перестанет исполнять настоящий код.`);
  }
  if (src.indexOf(needle, i + 1) >= 0) {
    throw new Error(`якорь среза неоднозначен: «${needle}» (${what}) встречается в ${CONTENT_JS} более одного раза`);
  }
  return i;
}

// Баланс фигурных скобок в строке, с учётом кавычек/шаблонов/строчных комментариев.
// Нужен, чтобы не отрезать середину функции: наивный рез «до первой строки с DOM»
// обрывается ПОСЛЕ сигнатуры `function currentJkIdFromUrl() {` (слово location
// стоит строкой ниже) и даёт висячую скобку -> SyntaxError на eval.
function braceDelta(line) {
  let d = 0, q = null, esc = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (q) { if (esc) esc = false; else if (c === "\\") esc = true; else if (c === q) q = null; continue; }
    if (c === '"' || c === "'" || c === "`") { q = c; continue; }
    if (c === "/" && line[i + 1] === "/") break;
    if (c === "{") d++; else if (c === "}") d--;
  }
  return d;
}

// Срез A: константы сбора (CONFIG/API/ROOMS/health/isHealthWarn).
// Конец — первая НЕкомментарная строка с браузерным глобалом, отмотанная назад
// до ближайшей точки с НУЛЕВЫМ балансом скобок.
function sliceConsts(src) {
  const from = anchor(src, "  const CONFIG = {", "начало блока констант сбора");
  const lines = src.slice(from).split("\n");
  const out = [];
  let depth = 0, cut = 0, hitDom = false;
  for (const l of lines) {
    if (!isComment(l) && DOM.test(l)) { hitDom = true; break; }
    out.push(l);
    depth += braceDelta(l);
    if (depth === 0) cut = out.length;                 // безопасная точка реза
  }
  if (!hitDom) throw new Error("срез A не нашёл границу: после CONFIG нигде не встретился браузерный код — структура content.js изменилась");
  if (!cut) throw new Error("срез A вышел пустым: сразу после CONFIG идёт браузерный код");
  return out.slice(0, cut).join("\n");
}

// Срез B: планировщик. Конец — `function categoryOf(`, ТОТ ЖЕ якорь, на который
// уже опирается tests/check_workbook.mjs. Одна точка отказа на два теста, а не
// две: переименуют слой нормализации — оба упадут одинаково и чинить надо в
// одном месте.
function sliceCollect(src) {
  // Начало — объявление токена отмены: он ПЕРВЫЙ в слое сбора, потому что от
  // него зависит уже сам sleep (прерываемый). Раньше якорем был `const sleep`,
  // и появление отмены выше него оставило бы cancelRun за пределами среза.
  const from = anchor(src, "  let cancelToken = null;", "начало слоя сбора");
  const to = anchor(src, "  function categoryOf(", "начало слоя нормализации");
  if (to <= from) throw new Error("categoryOf оказался ВЫШЕ слоя сбора — слои content.js переставили местами, срез B бессмыслен");
  return src.slice(from, to);
}

// Контракт среза. Без него сдвиг границы деградирует в ТИХО НЕПОЛНЫЙ срез:
// код соберётся, тесты позеленеют, а проверять будет нечего.
function checkSlice(name, code, must) {
  for (const id of must) {
    if (!new RegExp(`(^|[^\\w$])${id}([^\\w$]|$)`, "m").test(code)) {
      throw new Error(`срез ${name}: внутри нет «${id}» — граница уехала, срез неполон`);
    }
  }
  const bad = code.split("\n").filter((l) => !isComment(l) && DOM.test(l));
  if (bad.length) {
    throw new Error(`срез ${name}: захватил браузерный код (${bad.length} стр.), первая:\n    ${bad[0].trim()}`);
  }
  return code;
}

export const MUST_A = ["CONFIG", "API", "ROOMS", "health", "isHealthWarn", "reqBudget", "maxRetries", "backoffBase", "minPriceSpan"];
export const MUST_B = ["sleep", "pause", "dig", "withFilters", "apiFetch", "fetchPage", "collectAll", "paginateSegment", "priceSplit", "cancelRun"];

export function sliceCollector(contentJsPath = CONTENT_JS) {
  const src = fs.readFileSync(contentJsPath, "utf8");
  const A = checkSlice("A", sliceConsts(src), MUST_A);
  const B = checkSlice("B", sliceCollect(src), MUST_B);
  return { A, B, linesA: A.split("\n").length, linesB: B.split("\n").length };
}

// Фабрика: срез исполняется как тело функции, а все внешние имена приходят
// ПАРАМЕТРАМИ и затеняют глобальные. Именно так лечится время: sleep объявлен
// внутри среза как const и снаружи неподменяем, а setTimeout — свободная
// переменная, и параметр её перекрывает. Ни строки content.js «ради теста».
export function makeFactory(contentJsPath = CONTENT_JS) {
  const { A, B } = sliceCollector(contentJsPath);
  // eslint-disable-next-line no-eval
  // Date приходит параметром вместе с остальными: предохранитель по стенным
  // часам (CONFIG.timeBudgetMs) иначе непроверяем — виртуальные часы прогоняют
  // сутки за миллисекунды настоящего времени, и потолок не сработал бы никогда.
  return eval(`(function (fetch, setTimeout, console, Math, Date) {
${A}
${B}
  return { CONFIG, API, ROOMS, sleep, pause, dig, withFilters, apiFetch, fetchPage,
           collectAll, isHealthWarn, healthReasons, beginRun, cancelRun, endRun, isCancelled,
           getHealth: () => health };
})`);
}

// ===========================================================================
// 2. ВИРТУАЛЬНЫЕ ЧАСЫ
// ===========================================================================
// Время исчезает, а ДЛИТЕЛЬНОСТИ остаются: колбэк уходит в setImmediate (прогон
// — доли секунды), но ms в журнале — настоящее число, посчитанное продакшн-кодом.
// Поэтому «бэкофф растёт» и «Retry-After уважается» проверяются прямо по журналу,
// без имитации. Заодно now — это метрика: сколько сбор занял бы у пользователя.
//
// setImmediate, а не queueMicrotask: цепочка на сотни страниц × несколько await
// должна давать событийному циклу дышать. Порядок не страдает — collectAll
// строго последовательна, параллельных таймеров в срезе нет.
export function makeClock({ real = false } = {}) {
  const log = [];
  let now = 0, tag = "pause";
  const startedAt = Date.now();
  const setTimeoutStub = (cb, ms) => {
    log.push({ at: Math.round(now), ms: Math.round(ms), tag });
    now += ms;
    if (real) return globalThis.setTimeout(cb, ms);     // «честный» режим: реально ждём
    setImmediate(cb);
    return 0;
  };
  // Date для среза: now() идёт по ВИРТУАЛЬНЫМ часам. Наследуемся от настоящего
  // Date, чтобы всё остальное (конструктор, toISOString) продолжало работать.
  const T0 = Date.UTC(2026, 6, 1, 12, 0, 0);
  class VirtualDate extends Date {
    constructor(...a) { if (!a.length) super(T0 + now); else super(...a); }
    static now() { return T0 + now; }
  }
  return {
    setTimeout: setTimeoutStub,
    Date: VirtualDate,
    log,
    // Тег ставит мок в момент ответа (не 200 -> "backoff", 200 -> "pause"),
    // поэтому классификация сна не гадательная и не требует стек-трейсов.
    tagAs: (t) => { tag = t; },
    elapsed: () => now,                                 // виртуальные мс
    wall: () => Date.now() - startedAt,                 // настоящие мс
    sleeps: (t) => (t ? log.filter((s) => s.tag === t) : log).map((s) => s.ms),
    total: () => log.reduce((a, s) => a + s.ms, 0),
  };
}

// Детерминированный Math поверх настоящего. Спред {...Math} НЕ работает —
// свойства Math неперечисляемые, потеряются floor/ceil/imul, на которых стоит
// сам планировщик. Поэтому Object.create(Math) + свой random (mulberry32).
export function makeMath(seed = 42) {
  let s = seed >>> 0;
  const m = Object.create(Math);
  m.random = () => {
    s = (s + 0x6D2B79F5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return m;
}

// console как сборщик строк: apiFetch пишет туда единственное место, где
// наблюдаем номер попытки, а collectAll — итоговую строку, которую человек
// видит в DevTools при разборе жалобы «выгрузилось не всё».
export function makeConsole() {
  const warn = [], log = [];
  return {
    warn: (...a) => warn.push(a.join(" ")),
    log: (...a) => log.push(a.join(" ")),
    error: (...a) => warn.push(a.join(" ")),
    warns: warn, logs: log,
  };
}

// ===========================================================================
// 3. МОК СЕРВЕРА
// ===========================================================================
// Мок — не таблица заготовленных ответов, а маленький сервер: он держит
// универсум лотов, честно разбирает jsonQuery, фильтрует и нарезает страницу.
// Иначе тест проверял бы, что код ходит по заранее известному маршруту, а не
// что он правильно строит фильтры.
export function makeCianMock({
  offers = [],
  pageSize = 28,          // серверный размер страницы; СПЕЦИАЛЬНО отдельный от
                          // CONFIG.pageSize — расхождение обязано ловиться
  cap = Infinity,         // потолок выдачи Циан: сколько СТРАНИЦ сервер вообще
                          // готов отдать по одному фильтру (главная ручка недобора)
  faults = [],
  shape = "offersSerialized",   // offersSerialized | offers | items | wrapped | flat
  totalField = "aggregatedCount",
  repeatFirstPage = false,      // модель ротации: любая страница = первая
  clock,
} = {}) {
  const requests = [];
  const served = new Map();     // карта подачи: id -> сколько раз сервер его отдал
  let n = 0;

  const decode = (body) => {
    const q = JSON.parse(body).jsonQuery;
    const pv = (q.price && q.price.value) || {};
    return {
      page: q.page ? q.page.value : 1,
      rooms: q.room ? q.room.value.slice() : null,
      gte: pv.gte == null ? null : pv.gte,
      lte: pv.lte == null ? null : pv.lte,
      raw: q,
    };
  };

  const hit = (rule, f, i) => {
    if (typeof rule.when === "function") return rule.when(f, i);
    return Object.entries(rule.when).every(([k, v]) => {
      if (k === "nth") return i === v;
      if (k === "room") return f.rooms != null && f.rooms.includes(v);
      if (k === "pageAtLeast") return f.page >= v;
      return f[k] === v;
    });
  };

  const envelope = (list, total) => {
    const items = list.map((o) => ({ cianId: o.id, roomsCount: o.room, bargainTerms: { price: o.price } }));
    const payload = shape === "wrapped" ? items.map((o) => ({ offer: o })) : items;
    const key = shape === "offers" ? "offers" : shape === "items" ? "items" : "offersSerialized";
    const data = { [key]: payload };
    if (totalField !== "none") data[totalField] = total;
    return shape === "flat" ? data : { data };          // apiFetch терпит и d.data, и d
  };

  const fetchStub = async (url, init) => {
    const f = decode(init.body);
    const i = ++n;
    const matched = offers.filter((o) =>
      (f.rooms == null || f.rooms.includes(o.room)) &&
      (f.gte == null || o.price >= f.gte) &&
      (f.lte == null || o.price <= f.lte));
    let total = matched.length;
    const visible = cap === Infinity ? matched : matched.slice(0, cap * pageSize);
    let page = repeatFirstPage
      ? visible.slice(0, pageSize)
      : visible.slice((f.page - 1) * pageSize, f.page * pageSize);

    let status = 200, retryAfter = null, threw = null;
    for (const rule of faults) {
      if (rule.times != null && rule.times <= 0) continue;
      if (!hit(rule, f, i)) continue;
      if (rule.times != null) rule.times--;
      if (rule.then.status) { status = rule.then.status; retryAfter = rule.then.retryAfter || null; }
      if (rule.then.throw) threw = rule.then.throw;
      if (rule.then.empty) page = [];
      if (rule.then.total != null) total = rule.then.total;
    }

    const ok = status === 200 && !threw;
    // got=null для не-200 намеренно: иначе в журнале видно «429 got=28» —
    // число посчитано ДО применения сбоя, и «после 429 страница не засчитана»
    // читается неверно.
    requests.push({
      i, at: Math.round(clock ? clock.elapsed() : 0), url,
      page: f.page, rooms: f.rooms, gte: f.gte, lte: f.lte, body: f.raw,
      status: threw ? "NET" : status, got: ok ? page.length : null, total,
    });

    if (threw) { if (clock) clock.tagAs("backoff"); throw new TypeError("Failed to fetch"); }
    if (!ok) {
      if (clock) clock.tagAs("backoff");
      return {
        status,
        headers: { get: (h) => (h === "Retry-After" && retryAfter ? String(retryAfter) : null) },
        json: async () => ({}),
      };
    }
    if (clock) clock.tagAs("pause");
    page.forEach((o) => served.set(o.id, (served.get(o.id) || 0) + 1));
    return { status: 200, headers: { get: () => null }, json: async () => envelope(page, total) };
  };

  return { fetch: fetchStub, requests, served, httpCalls: () => n };
}

// Фикстура-универсум: распределение по комнатности и цене — явный параметр
// сценария, а не случайность.
export function makeUniverse({ rooms = [9, 7, 1, 2, 3, 4, 5, 6], perRoom = 40, idFrom = 1000,
                               priceOf = (k) => 5_000_000 + ((k * 7919) % 100) * 1_000_000 } = {}) {
  const out = [];
  let id = idFrom;
  for (const r of rooms) for (let k = 0; k < perRoom; k++) out.push({ id: id++, room: r, price: priceOf(k, r) });
  return out;
}

// Базовый jsonQuery «как у пользователя»: кроме служебных полей тут лежат
// фильтры, которые планировщик обязан протащить в КАЖДЫЙ запрос нетронутыми.
export function makeBase(extra = {}) {
  return {
    _type: "flatsale",
    engine_version: { type: "term", value: 2 },
    newobject: { type: "terms", value: [123456] },
    geo: { type: "geo", value: [{ type: "newobject", id: 123456 }] },
    total_area: { type: "range", value: { gte: 30 } },
    ...extra,
  };
}

// ===========================================================================
// 4. ОДИН ПРОГОН
// ===========================================================================
export async function runCollect(opts = {}) {
  const {
    offers = [], base = makeBase(), seed = 42, real = false, cancelAfter = null,
    factory = makeFactory(), ...mockOpts
  } = opts;
  const clock = makeClock({ real });
  const mock = makeCianMock({ offers, clock, ...mockOpts });
  const con = makeConsole();
  // Отмена «изнутри прогона»: пользователь жмёт кнопку, пока collectAll ещё
  // работает. Снаружи это не воспроизвести — прогон ждёт сам себя, — поэтому
  // кнопку «нажимает» мок, отдав N-й ответ.
  // Нажимает РОВНО ОДИН раз: человек тоже жмёт кнопку однажды, а повторный
  // прогон на том же экземпляре обязан идти начисто.
  let pressed = false;
  const fetchStub = cancelAfter == null ? mock.fetch : async (...a) => {
    const r = await mock.fetch(...a);
    if (!pressed && mock.httpCalls() >= cancelAfter) { pressed = true; api.cancelRun(); }
    return r;
  };
  const api = factory(fetchStub, clock.setTimeout, con, makeMath(seed), clock.Date);
  const progress = [];
  const baseSnapshot = JSON.stringify(base);
  let res = null, err = null;
  try {
    res = await api.collectAll(base, (msg) => progress.push(msg));
  } catch (e) {
    err = e;
  }
  return {
    api, res, err, mock, clock, con, progress, base, baseSnapshot,
    http: mock.httpCalls(),
    logical: res ? res.health.requests : null,
    ok200: mock.requests.filter((r) => r.status === 200).length,
  };
}

// Фильтры пользователя обязаны доехать до КАЖДОГО запроса нетронутыми.
// Это самый дорогой из возможных отказов: потеряв newobject/geo, расширение
// молча выгрузит не ЖК, а весь город — и внешне всё будет выглядеть штатно,
// просто лотов станет в сто раз больше. Проверка вешается на каждый прогон
// централизованно, чтобы её нельзя было забыть в новом сценарии.
export function baseFilterViolations(mock, base) {
  // page/room/price планировщик имеет право менять — это его работа
  const own = new Set(["page", "room", "price"]);
  const keys = Object.keys(base).filter((k) => !own.has(k));
  const bad = [];
  mock.requests.forEach((r, i) => {
    const q = r.body;
    if (!q) return;
    for (const k of keys) {
      if (JSON.stringify(q[k]) !== JSON.stringify(base[k])) {
        bad.push(`запрос #${i + 1}: поле «${k}» ${q[k] === undefined ? "потеряно" : "изменено"}`);
        break;
      }
    }
  });
  return bad;
}

// ===========================================================================
// 5. РАЗБОР ЖУРНАЛА ЗАПРОСОВ
// ===========================================================================
// Ключ сегмента — набор фильтров без page. Новый сегмент начинается там, где
// page вернулся к 1 или сменился ключ: paginateSegment всегда идёт 1,2,3…,
// поэтому склеить два разных сегмента в один невозможно.
export function segments(mock) {
  const out = [];
  let cur = null;
  for (const r of mock.requests) {
    const key = JSON.stringify([r.rooms, r.gte, r.lte]);
    if (!cur || cur.key !== key || r.page === 1) {
      cur = { key, rooms: r.rooms, gte: r.gte, lte: r.lte, http: 0, pages: [], reqs: [] };
      out.push(cur);
    }
    cur.http++;
    cur.reqs.push(r);
    if (r.status === 200) cur.pages.push(r);
  }
  return out;
}

// Человекочитаемая строка запроса — журнал должен читаться как маршрут.
export function fmtReq(r) {
  const who = r.rooms ? "r" + r.rooms.join("/") : "все";
  const price = r.gte != null || r.lte != null
    ? ` ₽${((r.gte ?? 0) / 1e6).toFixed(3)}-${((r.lte ?? 0) / 1e6).toFixed(3)}М` : "";
  return `#${String(r.i).padStart(3)} t=${String(r.at).padStart(7)}ms  ${(who + price + " p" + r.page).padEnd(34)}` +
         ` -> ${String(r.status).padEnd(3)} got=${r.got} total=${r.total}`;
}

export function dumpRequests(mock, n = 12) {
  return mock.requests.slice(0, n).map(fmtReq).join("\n");
}
