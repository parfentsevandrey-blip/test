// Краевые случаи СБОРА (extension/content.js: apiFetch / paginateSegment /
// priceSplit / collectAll) — то, что реально приходит от Циан и чего нет в
// «правильном» сценарии.
//
//   node tests/check_collect_edges.mjs                — прогнать
//   CIAN_DUMP='фантом' node tests/check_collect_edges.mjs   — маршрут сценария
//   CIAN_CONTENT_JS=/путь/content.js …                — проверить другую копию
//
// ЧЕМ ОТЛИЧАЕТСЯ ОТ tests/check_collect.mjs. Тот проверяет ПЛАНИРОВЩИК на
// добросовестном сервере (правильные фильтры, декомпозиция, бюджет, бэкофф).
// Здесь сервер ВРЁТ и ЛОМАЕТСЯ ровно так, как ломается Циан: aggregatedCount не
// сходится с числом реально отдаваемых лотов, страница моргает пустотой, в лоте
// нет cianId, Retry-After просит подождать час. Такие ответы не гипотеза: на них
// стоит вся дальнейшая работа — перебор decorations_list (×4 запросов), фоновые
// алерты (капчу пройти некому) и накопление по нескольким ЖК.
//
// Мок здесь СВОЙ (не makeCianMock из стенда): в этих сценариях total обязан быть
// оторван от числа лотов на странице, а заголовки и форма ответа — задаваться
// поштучно. Из общего стенда берутся только срез content.js, виртуальные часы,
// детерминированный Math и консоль.
//
// ТРИ ВИДА СТРОК:
//   ✓  инвариант держится;
//   ✗  инвариант сломан — ненулевой код возврата;
//   △  ИЗВЕСТНАЯ ДЫРА: поведение зафиксировано характеризующей проверкой.
//      Прогон не валится, но если дыру закроют — тест это заметит.

import path from "path";
import { fileURLToPath, pathToFileURL } from "url";
import { makeFactory, makeClock, makeMath, makeConsole, makeBase, CONTENT_JS } from "./collect_harness.mjs";

let failed = 0, gaps = 0, closed = 0;
const msg = (m) => (typeof m === "function" ? m() : m);
const fail = (m) => { console.error("  ✗ " + msg(m)); failed++; };
const pass = (m) => console.log("  ✓ " + msg(m));
const check = (cond, ok, bad) => (cond ? pass(ok) : fail(bad));
const eq = (a, b, what) => check(a === b, `${what}: ${a}`, `${what}: ожидалось ${b}, получено ${a}`);
const gap = (holds, now, fixed) => {
  if (holds) { console.log("  △ ИЗВЕСТНАЯ ДЫРА: " + msg(now)); gaps++; }
  else { console.log("  ✓ ДЫРА ЗАКРЫТА: " + msg(fixed) + " — перенесите проверку в основные"); closed++; }
};
const section = (t) => console.log(`\n── ${t} ${"─".repeat(Math.max(0, 66 - t.length))}`);
// Знак сохраняется намеренно: отрицательное виртуальное время — это не сбой
// стенда, а следствие отрицательной паузы (см. сценарий «Retry-After: -5»).
const hms = (ms) => {
  const s = Math.round(Math.abs(ms) / 1000);
  return `${ms < 0 ? "-" : ""}${Math.floor(s / 3600)}ч ${Math.floor((s % 3600) / 60)}м ${s % 60}с`;
};

// Формула охвата из computeStats() — то ЕДИНСТВЕННОЕ число, по которому
// пользователь судит о полноте выгрузки (панель результата и лист «Сводка»).
// Дублируется здесь намеренно: она живёт в DOM-слое content.js, в срез не
// попадает, а без неё «собрано 28 из 100» не переводится в то, что человек увидит.
const coverage = (rows, totalInJk) => (totalInJk ? Math.min(100, Math.round((rows / totalInJk) * 100)) : 100);

// ===========================================================================
// МОК: сервер, которому разрешено врать
// ===========================================================================
// handler(filters, i) -> { body } | { status, retryAfter } | { throw: true }
function makeMock({ handler, clock }) {
  const requests = [];
  let n = 0;
  const fetchStub = async (url, init) => {
    const q = JSON.parse(init.body).jsonQuery;
    const pv = (q.price && q.price.value) || {};
    const f = {
      page: q.page ? q.page.value : 1,
      rooms: q.room ? q.room.value.slice() : null,
      gte: pv.gte == null ? null : pv.gte,
      lte: pv.lte == null ? null : pv.lte,
    };
    const r = handler(f, ++n) || {};
    const status = r.throw ? "NET" : (r.status || 200);
    const data = (r.body && (r.body.data || r.body)) || {};
    requests.push({
      i: n, at: Math.round(clock.elapsed()), ...f, status,
      got: status === 200 ? (data.offersSerialized || data.offers || data.items || []).length : null,
      total: status === 200 ? (data.aggregatedCount ?? null) : null,
    });
    if (r.throw) { clock.tagAs("backoff"); throw new TypeError("Failed to fetch"); }
    if (status !== 200) {
      clock.tagAs("backoff");
      return {
        status,
        headers: { get: (h) => (h === "Retry-After" && r.retryAfter != null ? String(r.retryAfter) : null) },
        json: async () => ({}),
      };
    }
    clock.tagAs("pause");
    return { status: 200, headers: { get: () => null }, json: async () => r.body };
  };
  return { fetch: fetchStub, requests, calls: () => n };
}

// Конверт ответа. idKey=null — лот вообще без идентификатора.
const envelope = (list, total, { key = "offersSerialized", totalKey = "aggregatedCount", idKey = "cianId" } = {}) => {
  const data = {
    [key]: list.map((o) => {
      const it = { roomsCount: o.room, bargainTerms: { price: o.price } };
      if (idKey) it[idKey] = o.id;
      return it;
    }),
  };
  if (totalKey) data[totalKey] = total;
  return { data };
};

// Добросовестная нарезка страницы из универсума; врать можно через total/env.
const serve = ({ offers, pageSize = 28, total, env = {} }) => (f) => {
  const matched = offers.filter((o) =>
    (f.rooms == null || f.rooms.includes(o.room)) &&
    (f.gte == null || o.price >= f.gte) && (f.lte == null || o.price <= f.lte));
  const page = matched.slice((f.page - 1) * pageSize, f.page * pageSize);
  const t = typeof total === "function" ? total(matched, f) : (total == null ? matched.length : total);
  return { body: envelope(page, t, env) };
};

const lots = ({ n = 100, room = 2, idFrom = 1000, price = (k) => 5e6 + k * 1e6 } = {}) =>
  Array.from({ length: n }, (_, k) => ({ id: idFrom + k, room, price: price(k) }));

let factory = null, api0 = null, CFG = null;

async function run(name, { handler, base = makeBase(), seed = 42 }) {
  const clock = makeClock();
  const mock = makeMock({ handler, clock });
  const con = makeConsole();
  const api = factory(mock.fetch, clock.setTimeout, con, makeMath(seed));
  const progress = [];
  let res = null, err = null;
  try { res = await api.collectAll(base, (m) => progress.push(m)); } catch (e) { err = e; }
  const health = res ? res.health : (api.getHealth() || { retries: 0, retryStatuses: {}, totalDrift: 0 });
  const r = {
    name, api, res, err, mock, clock, con, progress, health,
    http: mock.calls(),
    logical: health.requests ?? null,
    rows: res ? res.offers.length : 0,
    totalInJk: res ? res.totalInJk : 0,
    warn: api.isHealthWarn(health),
  };
  r.coverage = coverage(r.rows, r.totalInJk);
  runs.push(r);
  if (process.env.CIAN_DUMP && name.includes(process.env.CIAN_DUMP)) {
    console.log(`  ─ маршрут «${name}» (${mock.requests.length} запросов, первые 40):`);
    mock.requests.slice(0, 40).forEach((q) => console.log("    " +
      `#${String(q.i).padStart(3)} t=${String(q.at).padStart(8)}мс ` +
      `${((q.rooms ? "r" + q.rooms.join("/") : "все") + (q.gte != null ? ` ₽${q.gte}-${q.lte}` : "") + " p" + q.page).padEnd(36)}` +
      ` -> ${String(q.status).padEnd(3)} got=${q.got} total=${q.total}`));
  }
  return r;
}
const runs = [];
const line = (r) => `${r.name}: собрано ${r.rows}/${r.totalInJk} (охват ${r.coverage}%), HTTP ${r.http}, ` +
  `логических ${r.logical}, ретраев ${r.health.retries}, вирт. ${hms(r.clock.elapsed())}` +
  `${r.warn ? ", ⚠ предупреждение" : ""}${r.err ? ", бросок: " + r.err.message : ""}`;

// ===========================================================================
// 1. Размер выдачи: 0, 1, ровно кратно странице
// ===========================================================================
async function testSizes() {
  section("Размер выдачи: 0, 1, кратно pageSize");

  const empty = await run("total=0 (в ЖК всё продано)", { handler: serve({ offers: [], total: 0 }) });
  check(!empty.err && empty.rows === 0 && empty.totalInJk === 0,
    "total=0 не роняет сбор и не запускает декомпозицию: 0 лотов, 0 комнатных сегментов",
    () => `total=0 обработан неверно: ${empty.err ? empty.err.message : `${empty.rows} лотов, totalInJk=${empty.totalInJk}`}`);
  check(empty.mock.requests.every((q) => q.rooms == null && q.gte == null),
    "по пустой выдаче не пошли ни комнаты, ни дробление по цене",
    "пустая выдача запустила декомпозицию");
  gap(empty.http === 2,
    `на заведомо пустой выдаче делается ${empty.http} запроса: total=0 и got=0 уже на первой странице, ` +
    "но выход только по ДВУМ пустым подряд — вторая страница спрашивается всегда " +
    "(для фоновых алертов это ×2 трафика на каждый «ничего не изменилось»)",
    "пустая выдача с total=0 останавливается на первой странице");

  const one = await run("total=1", { handler: serve({ offers: lots({ n: 1 }) }) });
  check(one.rows === 1 && one.http === 1, "total=1: один лот за один запрос", () => `total=1: ${one.rows} лотов за ${one.http} запросов`);

  // Ровно кратно размеру страницы — классическое место для «лишней» пустой
  // страницы: seg.size >= total обязан сработать РАНЬШЕ, чем сервер вернёт пустоту.
  for (const n of [28, 56, 1512]) {
    const r = await run(`total=${n} (кратно pageSize)`, { handler: serve({ offers: lots({ n }) }) });
    eq(r.rows, n, `total=${n}: собрано`);
    eq(r.http, n / CFG.pageSize, `total=${n}: запросов ровно total/pageSize (лишней пустой страницы нет)`);
  }
}

// ===========================================================================
// 2. total врёт в БОЛЬШУЮ сторону
// ===========================================================================
// Самый частый перекос Циан: aggregatedCount считает лот, который пагинация уже
// не отдаёт (снят, скрыт, дубль). Недостающего лота НЕ СУЩЕСТВУЕТ, поэтому вся
// декомпозиция заведомо не может сойтись — вопрос лишь в том, сколько она
// сожжёт, прежде чем упрётся в reqBudget.
async function testTotalTooHigh() {
  section("total врёт в большую сторону (страницы кончились раньше)");

  const jk = [];
  let id = 1000;
  for (const room of api0.ROOMS) for (let k = 0; k < 40; k++) jk.push({ id: id++, room, price: 5e6 + (k % 20) * 1e6 });

  const honest = await run("ЖК на 320 лотов, честный total", { handler: serve({ offers: jk }) });
  eq(honest.rows, 320, "контроль: честный total — собрано");
  eq(honest.http, 12, "контроль: честный total — запросов");

  // +1 «фантом» на КАЖДЫЙ сегмент: и в прямом проходе, и в каждой комнате.
  const phantom = await run("фантомный лот в aggregatedCount (+1)", {
    handler: serve({ offers: jk, total: (m) => (m.length ? m.length + 1 : 0) }),
  });
  eq(phantom.rows, 320, "фантом: собрано столько же лотов, сколько при честном total");
  gap(phantom.logical === CFG.reqBudget,
    () => `ОДИН несуществующий лот в total превратил ${honest.http} запросов в ${phantom.http} ` +
      `(×${Math.round(phantom.http / honest.http)}, весь бюджет reqBudget=${CFG.reqBudget}): ` +
      `${phantom.mock.requests.filter((q) => q.got === 0).length} из них вернули НОЛЬ лотов, ` +
      `${new Set(phantom.mock.requests.map((q) => JSON.stringify([q.rooms, q.gte, q.lte]))).size} различных сегментов. ` +
      `Только сна ${hms(phantom.clock.elapsed())}, результат тот же — 320 лотов`,
    "недостижимый остаток больше не сжигает весь бюджет");
  gap(!phantom.warn && phantom.health.totalDrift === 0,
    () => `и ни следа в диагностике: health=${JSON.stringify(phantom.health)}, предупреждения нет, ` +
      `охват ${phantom.coverage}% — по книге прогон за ${phantom.http} запросов неотличим от прогона за ${honest.http}`,
    "исчерпание бюджета попало в health");

  // Фантом ровно в одной комнатности — та же цена: комнатный сегмент не сошёлся,
  // значит дробится по цене до самого minPriceSpan.
  const oneRoom = await run("фантом только у трёшек", {
    handler: (f) => serve({ offers: jk, total: (m) => (m.length ? m.length + (m.some((o) => o.room === 3) ? 1 : 0) : 0) })(f),
  });
  gap(oneRoom.logical === CFG.reqBudget,
    `фантом в ОДНОЙ комнатности стоит ровно столько же — ${oneRoom.http} запросов: ` +
    "недобор в любом сегменте включает дробление по цене, а оно не умеет останавливаться на «остаток недостижим»",
    "локальный недобор больше не сжигает бюджет целиком");
}

// ===========================================================================
// 3. total врёт в МЕНЬШУЮ сторону
// ===========================================================================
async function testTotalTooLow() {
  section("total врёт в меньшую сторону");

  const r = await run("total=10 при 100 реальных лотах", { handler: serve({ offers: lots({ n: 100 }), total: 10 }) });
  gap(r.rows === 28 && r.http === 1,
    () => `сегмент объявлен полным на первой же странице (seg.size=${r.rows} >= total=${r.totalInJk}): ` +
      `${r.rows} лотов из 100 в файле, ${r.http} запрос, декомпозиции нет`,
    "заниженный total больше не обрывает пагинацию");
  gap(!r.warn && r.health.totalDrift === 0 && r.coverage === 100,
    () => `и это НИГДЕ не видно: дрейфа нет (все страницы говорят одно и то же), health=${JSON.stringify(r.health)}, ` +
      `а охват в панели и в «Сводке» = Math.min(100, ${r.rows}/${r.totalInJk}) = ${r.coverage}% — ` +
      "пользователю показывают полную выгрузку при 72 потерянных лотах",
    "заниженный total больше не выдаётся за 100% охвата");
}

// ===========================================================================
// 4. Повторы, форма ответа, отсутствующий идентификатор
// ===========================================================================
async function testDuplicatesAndIds() {
  section("Повторы между страницами, форма ответа, отсутствующий id");

  // Одна и та же квартира на разных страницах (Циан так делает при ротации).
  const all = lots({ n: 100 });
  const dup = await run("на каждой странице повторяются 5 лотов с первой", {
    handler: (f) => {
      const matched = all.filter((o) =>
        (f.rooms == null || f.rooms.includes(o.room)) &&
        (f.gte == null || o.price >= f.gte) && (f.lte == null || o.price <= f.lte));
      let page = matched.slice((f.page - 1) * 28, f.page * 28);
      if (f.page > 1 && page.length) page = [...matched.slice(0, 5), ...page].slice(0, 28);
      return { body: envelope(page, matched.length) };
    },
  });
  eq(dup.rows, 100, "повторы между страницами: в файле каждый лот ровно один раз");
  check(dup.http < 100,
    `повторы восстанавливаются декомпозицией: ${dup.http} запросов вместо 4 (повтор съедает место на странице)`,
    () => `повторы между страницами обошлись в ${dup.http} запросов`);
  check(new Set(dup.res.offers.map((o) => o.cianId)).size === dup.rows,
    "дедупликация по cianId: дублей в выдаче нет",
    "в выдаче остались дубли");

  // offersSerialized отсутствует — сквозной прогон, не только apiFetch.
  const alt = await run("ответ в поле offers", { handler: serve({ offers: lots({ n: 60 }), env: { key: "offers" } }) });
  eq(alt.rows, 60, "offersSerialized нет, есть offers: собрано");

  // Лот без cianId и без id: ключ дедупликации взять неоткуда.
  const noId = await run("лоты без cianId и без id", { handler: serve({ offers: lots({ n: 60 }), env: { idKey: null } }) });
  gap(noId.rows === 0,
    () => `лоты без cianId/id молча выбрасываются (\`if (id != null)\`): в файле ${noId.rows} строк при total=${noId.totalInJk}. ` +
      `Пустой сегмент неотличим от недобора, поэтому сверху ещё и ${noId.http} запросов ` +
      `(весь бюджет) в поисках лотов, которые уже пришли, и health=${JSON.stringify(noId.health)} без единого признака беды`,
    "лот без идентификатора больше не теряется молча");

  // cianId === 0 — ложное значение: `o.cianId || o.id` уходит на несуществующий id.
  const zero = await run("в выдаче лот с cianId = 0", {
    handler: serve({ offers: [{ id: 0, room: 2, price: 5e6 }, ...lots({ n: 5, idFrom: 1 })] }),
  });
  gap(zero.rows === 5 && !zero.res.offers.some((o) => o.cianId === 0),
    () => `лот с cianId=0 потерян: ключ берётся как \`o.cianId || o.id\` (|| вместо ??), ноль — ложное значение. ` +
      `Собрано ${zero.rows} из ${zero.totalInJk}, и поиск «пропавшего» стоил ещё ${zero.http} запросов`,
    "cianId=0 больше не теряется (|| заменён на ??)");
}

// ===========================================================================
// 5. Retry-After: абсурдные и нечисловые значения
// ===========================================================================
// Циан отдаёт Retry-After на антибот-троттлинге, и просит он не 30 секунд.
// Значение используется как есть: wait = ra * 1000, без потолка и без проверки
// знака. Всё это время вкладка молчит: onProgress в apiFetch не вызывается.
async function testRetryAfter() {
  section("Retry-After: потолок, знак, нечисловые значения");

  const hour = await run("429 однократно, Retry-After: 3600", {
    handler: (f, i) => (i === 1 ? { status: 429, retryAfter: 3600 } : serve({ offers: lots({ n: 40 }) })(f)),
  });
  gap(hour.clock.elapsed() >= 3.6e6,
    () => `один 429 с Retry-After: 3600 останавливает выгрузку на ${hms(hour.clock.elapsed())}: ` +
      "потолка у ожидания нет, отмены нет, в панели всё это время висит «стр.1…» " +
      `(последнее сообщение onProgress: «${hour.progress[hour.progress.length - 1]}»), ` +
      `а единственный след — console.warn: «${hour.con.warns[0]}»`,
    "ожидание по Retry-After ограничено разумным потолком");

  const dead = await run("429 на КАЖДОМ запросе, Retry-After: 3600", { handler: () => ({ status: 429, retryAfter: 3600 }) });
  const naps = dead.clock.sleeps("backoff");
  check(!!dead.err && /429/.test(dead.err.message),
    `безнадёжный 429 заканчивается броском «${dead.err && dead.err.message}»`,
    () => `безнадёжный 429 не бросил исключение: ${dead.err ? dead.err.message : "сбор прошёл штатно"}`);
  // Раньше это была ОДНА проверка на два разных дефекта, и починка первого
  // делала вид, что починен и второй. Разделено: длительность и лишний сон —
  // независимые вещи.
  gap(dead.clock.elapsed() >= 4 * 3.6e6,
    () => `устойчивый 429 с Retry-After: 3600 держит вкладку ${hms(dead.clock.elapsed())}`,
    () => `ожидание ограничено потолком: ${hms(dead.clock.elapsed())} вместо часов`);
  gap(naps.length === CFG.maxRetries,
    () => `последний сон (${hms(naps[naps.length - 1])}) — уже после ПОСЛЕДНЕЙ попытки, ` +
      "чистое ожидание перед throw (на сетевом пути такой сон отсечён проверкой " +
      "attempt >= maxRetries, на пути 429/5xx — нет)",
    "перед фатальным броском больше не спят");

  // Нечисловое значение (HTTP-date по RFC 7231 — легальная форма заголовка).
  const date = await run("Retry-After: HTTP-date", {
    handler: (f, i) => (i <= 2 ? { status: 429, retryAfter: "Wed, 21 Oct 2026 07:28:00 GMT" } : serve({ offers: lots({ n: 40 }) })(f)),
  });
  const db = date.clock.sleeps("backoff");
  check(db.length === 2 && db.every((ms, k) => ms >= CFG.backoffBase * 2 ** k && ms < CFG.backoffBase * 2 ** k + 400),
    `нечисловой Retry-After (HTTP-date) не ломает бэкофф: ${db.map(Math.round).join(" → ")} мс, как без заголовка`,
    () => `нечисловой Retry-After дал странные паузы: ${db.map(Math.round).join(" → ")} мс`);
  eq(date.rows, 40, "после ретраев сбор доходит до конца");

  // Знак и форма числа: parseInt берёт что дают.
  const neg = await run("Retry-After: -5", {
    handler: (f, i) => (i <= 2 ? { status: 429, retryAfter: -5 } : serve({ offers: lots({ n: 40 }) })(f)),
  });
  const nb = neg.clock.sleeps("backoff");
  gap(nb.every((ms) => ms < 0),
    () => `отрицательный Retry-After превращается в ОТРИЦАТЕЛЬНУЮ паузу (${nb.map(Math.round).join(", ")} мс): ` +
      "setTimeout выполняет колбэк немедленно, и повторы летят в сервер без задержки ровно тогда, когда он попросил подождать",
    "отрицательный Retry-After больше не даёт отрицательную паузу");
  const exp = await run("Retry-After: 1e3", {
    handler: (f, i) => (i <= 1 ? { status: 429, retryAfter: "1e3" } : serve({ offers: lots({ n: 40 }) })(f)),
  });
  gap(exp.clock.sleeps("backoff")[0] < 2000,
    () => `«1e3» читается parseInt как 1 секунда вместо 1000 (пауза ${Math.round(exp.clock.sleeps("backoff")[0])} мс) — ` +
      "заголовок разбирается parseInt, а не Number, и любая нестандартная запись молча занижает паузу",
    "Retry-After разбирается строго");
}

// ===========================================================================
// 6. Потери, которые не доходят до диагностики
// ===========================================================================
// Потеря лотов и её видимость — разные вещи. Долю ретраев (retries/requests)
// длинный удачный прогон размывает до нуля, поэтому isHealthWarn смотрит ещё и
// на абсолютный недобор health.shortfall. Здесь проверяется, что видимость
// потери больше не зависит от того, в каком месте прогона она случилась.
async function testSilentLoss() {
  section("Обрыв и пустые страницы: потери мимо диагностики");

  // Пользователь сам сузил цену до полосы уже minPriceSpan — значит это
  // ПОСЛЕДНИЙ сегмент: priceSplit дробить не может, восстановления не будет.
  const LO = 5_000_000, HI = 5_150_000;                    // 150 000 < minPriceSpan
  const narrow = makeBase({ room: { type: "terms", value: [2] }, price: { type: "range", value: { gte: LO, lte: HI } } });
  const band = lots({ n: 1000, price: (k) => LO + (k % 150) * 1000 });

  const tail = await run("обрыв сети на последней (36-й) странице последнего сегмента", {
    base: narrow, handler: (f) => (f.page === 36 ? { throw: true } : serve({ offers: band })(f)),
  });
  eq(tail.rows, 980, "обрыв на последней странице: собрано");
  eq(tail.health.shortfall, 1000 - tail.rows, "недобор посчитан абсолютно, а не как доля ретраев");

  const head = await run("тот же обрыв, но на 3-й странице", {
    base: narrow, handler: (f) => (f.page === 3 ? { throw: true } : serve({ offers: band })(f)),
  });
  // Раньше здесь стоял контроль ДЫРЫ: тот же отказ в начале прогона давал
  // предупреждение, а в конце — нет, потому что isHealthWarn считал только долю
  // retries/requests, и она тонула в длинном удачном прогоне. Теперь порог
  // абсолютный (health.shortfall), и место потери на её заметность не влияет.
  const ratio = tail.health.retries / tail.logical;
  check(tail.warn && head.warn,
    `место потери больше не влияет на заметность: обрыв на последней странице (${tail.rows}/1000, охват ${tail.coverage}%) ` +
    `и он же на третьей (${head.rows}/1000) — предупреждение в обоих случаях`,
    `предупреждение зависит от места потери: в конце ${tail.warn ? "есть" : "НЕТ"}, в начале ${head.warn ? "есть" : "НЕТ"}`);
  check(ratio <= 0.15,
    `и держится оно именно на недоборе, а не на доле ретраев: ${tail.health.retries} на ${tail.logical} страниц = ` +
    `${ratio.toFixed(3)} ≤ 0.15 — прежний относительный порог здесь по-прежнему молчит`,
    `контроль потерял смысл: доля ретраев ${ratio.toFixed(3)} сама превысила 0.15, ` +
    "предупреждение могло сработать и по старому порогу");

  // Две пустые страницы подряд в середине выдачи — единственный признак «выдача
  // кончилась», и он же признак «сервер моргнул».
  const blink = await run("две пустые страницы подряд в середине выдачи", {
    base: narrow,
    handler: (f) => (f.page === 3 || f.page === 4
      ? { body: { data: { offersSerialized: [], aggregatedCount: 300 } } }
      : serve({ offers: lots({ n: 300, price: (k) => LO + (k % 150) * 1000 }) })(f)),
  });
  gap(blink.rows === 56 && !blink.warn,
    () => `сегмент оборван на двух пустых страницах: ${blink.rows} лотов из ${blink.totalInJk} (охват ${blink.coverage}%), ` +
      "пустая страница не перезапрашивается, счётчика «оборвано по пустоте» в health нет, предупреждения нет",
    "пустая страница в середине выдачи больше не обрывает сегмент молча");
}

// ===========================================================================
// 7. Плотность обращений к api.cian.ru
// ===========================================================================
// CONFIG.delayMin/delayMax — единственная защита от антибота. pause() стоит в
// конце итерации paginateSegment, поэтому КАЖДЫЙ выход из сегмента (по break) и
// каждый переход к следующему ценовому сегменту происходят без задержки вовсе.
async function testPacing() {
  section("Плотность обращений: паузы между сегментами");

  const burn = runs.find((r) => r.name === "фантомный лот в aggregatedCount (+1)");
  const gapsMs = burn.mock.requests.slice(1).map((q, k) => q.at - burn.mock.requests[k].at);
  const zero = gapsMs.filter((g) => g === 0).length;
  const avg = Math.round(burn.clock.elapsed() / burn.mock.requests.length);
  gap(zero > gapsMs.length / 4,
    `в сценарии с фантомом ${zero} из ${gapsMs.length} пар запросов уходят БЕЗ единой миллисекунды задержки ` +
    `(${Math.round(100 * zero / gapsMs.length)}%, средний интервал ${avg} мс при CONFIG.delayMin=${CFG.delayMin}): ` +
    "pause() вызывается только между страницами ВНУТРИ сегмента, а выход из сегмента (любой break) и переход к " +
    "следующему ценовому сегменту в priceSplit паузы не делают вовсе. Максимальная плотность приходится ровно на " +
    "аварийный режим, когда сервер и так недоволен",
    "паузы соблюдаются и на переходах между сегментами");
}

// ===========================================================================
async function main() {
  console.log(`Слой сбора: ${CONTENT_JS}`);
  try {
    factory = makeFactory();
    api0 = factory(async () => { throw new Error("сеть не нужна"); }, () => 0, makeConsole(), Math);
    CFG = api0.CONFIG;
  } catch (e) {
    fail(`не удалось вырезать слой сбора из content.js:\n      ${e.message.split("\n").join("\n      ")}`);
    return 1;
  }
  const t0 = Date.now();
  await testSizes();
  await testTotalTooHigh();
  await testTotalTooLow();
  await testDuplicatesAndIds();
  await testRetryAfter();
  await testSilentLoss();
  await testPacing();

  section("Цена каждого сценария");
  runs.forEach((r) => console.log("    · " + line(r)));
  console.log(`\nПрогон ${((Date.now() - t0) / 1000).toFixed(1)} с, сценариев ${runs.length}, ` +
    `обращений к «Циан» ${runs.reduce((a, r) => a + r.http, 0)}. Известных дыр (△): ${gaps}${closed ? `, закрыто: ${closed}` : ""}.`);
  console.log(failed ? `ПРОВАЛЕНО: ${failed}` : "Всё зелено.");
  return failed ? 1 : 0;
}

const isMain = process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;
if (isMain) process.exit(await main());
