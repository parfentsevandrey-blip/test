// Тесты СЛОЯ ЗАХВАТА extension/content.js: currentJkIdFromUrl /
// queryMismatchesJk / rememberQuery — плюс pageResultCount.
//
//   node tests/check_capture.mjs             — прогнать
//   NEGATIVE=1 node tests/check_capture.mjs  — негативный контроль (см. низ файла)
//   CIAN_CONTENT_JS=/путь/content.js …       — проверить другую копию
//
// ЗАЧЕМ ОТДЕЛЬНЫЙ ФАЙЛ, А НЕ СЕКЦИЯ В check_collect.mjs. Стенд
// collect_harness.mjs режет content.js по границе «кончился код без браузера»
// и ПАДАЕТ, если в срез попала строка со словом document/window/location
// (checkSlice). Это правильный контракт для планировщика — но он же означает,
// что слой захвата тем стендом не покрывается в принципе. Здесь свой срез: он,
// наоборот, состоит из браузерного кода, а location/window приходят
// параметрами. Смешивать два взаимоисключающих контракта резки в одном файле
// незачем.
//
// ЧТО ЗДЕСЬ ЗАЩИЩАЕТСЯ. Слой захвата решает, КАКОЙ jsonQuery уйдёт в сбор.
// На странице ЖК search-offers-desktop дёргают не только основной список, но и
// побочные виджеты («Похожие ЖК», «Рекомендуем») — их jsonQuery относится к
// ДРУГОМУ newobject. Ошибка в queryMismatchesJk не роняет сбор и не даёт
// исключения: выгрузка молча проходит целиком по ЧУЖОМУ ЖК. Пользователь
// получает валидный .xlsx с валидными лотами не того комплекса — и ни одна
// проверка книги этого не увидит.
//
// ТРИ ВИДА СТРОК В ВЫВОДЕ:
//   ✓  инвариант держится;
//   ✗  инвариант сломан — ненулевой код возврата;
//   △  ИЗВЕСТНАЯ ДЫРА: инвариант НЕ держится на текущем коде, поведение
//      зафиксировано характеризующей проверкой. Прогон не валится, но если
//      дыру закроют — тест это заметит и попросит перенести проверку в основные.

import fs from "fs";
import path from "path";
import { fileURLToPath, pathToFileURL } from "url";
import { spawnSync } from "child_process";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CONTENT_JS = process.env.CIAN_CONTENT_JS || path.join(ROOT, "extension", "content.js");

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

// ===========================================================================
// 1. РЕЗКА
// ===========================================================================
// Границы — по СМЫСЛУ, а не по номерам строк: начало слоя — объявление
// currentJkIdFromUrl, конец — подмена window.fetch (её в срез не берём: тест
// вызывает rememberQuery напрямую, а хуки fetch/XHR — это три строки склейки,
// ради которых пришлось бы тащить в песочницу настоящие Web API).

function anchor(src, needle, what) {
  const i = src.indexOf(needle);
  if (i < 0) {
    throw new Error(
      `якорь среза не найден: «${needle}» (${what}) в ${CONTENT_JS}\n` +
      `    Слой захвата переехал или переименован. Почините якорь в tests/check_capture.mjs —\n` +
      `    НЕ удаляйте проверку: без среза тест перестанет исполнять настоящий код.`);
  }
  if (src.indexOf(needle, i + 1) >= 0) {
    throw new Error(`якорь среза неоднозначен: «${needle}» (${what}) встречается более одного раза`);
  }
  return i;
}

// Контракт среза: без него сдвиг границы деградирует в ТИХО НЕПОЛНЫЙ срез —
// код соберётся, тесты позеленеют, а проверять будет нечего.
function checkSlice(name, code, must) {
  for (const id of must) {
    if (!new RegExp(`(^|[^\\w$])${id}([^\\w$]|$)`, "m").test(code)) {
      throw new Error(`срез ${name}: внутри нет «${id}» — граница уехала, срез неполон`);
    }
  }
  return code;
}

const MUST_CAPTURE = ["currentJkIdFromUrl", "queryMismatchesJk", "rememberQuery", "newobject", "geo"];
const MUST_PRC = ["pageResultCount", "parseInt"];

function sliceCapture(src) {
  const from = anchor(src, "  function currentJkIdFromUrl() {", "начало слоя захвата");
  const hook = anchor(src, "    const of = window.fetch;", "подмена window.fetch");
  const to = src.lastIndexOf("try {", hook);          // отрезаем вместе с try, обрамляющим хук
  if (to <= from) throw new Error("подмена window.fetch оказалась ВЫШЕ currentJkIdFromUrl — слои переставили местами");
  return checkSlice("захвата", src.slice(from, to), MUST_CAPTURE);
}

function slicePrc(src) {
  const from = anchor(src, "  function pageResultCount() {", "начало pageResultCount");
  const to = anchor(src, "  // ---------- определить ID и имя ЖК ----------", "конец pageResultCount");
  if (to <= from) throw new Error("detectJk оказался ВЫШЕ pageResultCount — слои переставили местами");
  return checkSlice("pageResultCount", src.slice(from, to), MUST_PRC);
}

// Внешние имена приходят ПАРАМЕТРАМИ и затеняют глобальные: location и window
// у каждого сценария свои, никакого общего состояния между проверками.
// Ни строки content.js «ради теста».
let SRC = null, CAPTURE = null, PRC = null, captureFactory = null, prcFactory = null;

function bootstrap() {
  SRC = fs.readFileSync(CONTENT_JS, "utf8");
  CAPTURE = sliceCapture(SRC);
  PRC = slicePrc(SRC);
  // eslint-disable-next-line no-eval
  captureFactory = eval(`(function (location, window) {
${CAPTURE}
  return { currentJkIdFromUrl, queryMismatchesJk, rememberQuery };
})`);
  // eslint-disable-next-line no-eval
  prcFactory = eval(`(function (document) {
${PRC}
  return pageResultCount;
})`);
}

// ===========================================================================
// 2. ФИКСТУРЫ
// ===========================================================================
const JK = 123456, OTHER = 999999;
const JK_URL = `https://www.cian.ru/zhiloy-kompleks-simfoniya-34-${JK}/`;
const JK_URL_Q = `https://www.cian.ru/cat.php?deal_type=sale&newobject%5B0%5D=${JK}&region=1`;
const CAT_URL = "https://www.cian.ru/cat.php?deal_type=sale&engine_version=2&region=1";

const jq = (extra) => ({ _type: "flatsale", engine_version: { type: "term", value: 2 }, ...extra });
const byNewobject = (id) => jq({ newobject: { type: "terms", value: [id] } });
const byGeo = (id, type = "newobject") => jq({ geo: { type: "geo", value: [{ type, id }] } });
const wrap = (q) => JSON.stringify({ jsonQuery: q });

// Один прогон rememberQuery: своё окно, свой location, ловим и бросок тоже —
// хук стоит на пути НАСТОЯЩЕГО fetch страницы, исключение отсюда сломало бы
// саму выдачу Циан, а не только выгрузку.
function remember(href, body) {
  const win = {};
  const api = captureFactory({ href }, win);
  let threw = null;
  try { api.rememberQuery(body); } catch (e) { threw = e; }
  return { captured: win.__cianCapturedQuery, threw };
}

// ===========================================================================
// 3. СРЕЗ
// ===========================================================================
function testSlice() {
  section("Срез слоя захвата");
  const n = CAPTURE.split("\n").length;
  check(n > 10 && n < 60, `срез захвата: ${n} строк (currentJkIdFromUrl … подмена window.fetch)`,
    `срез захвата подозрительного размера (${n} строк) — граница уехала`);
  check(/window\.__cianCapturedQuery/.test(CAPTURE),
    "в срезе есть запись в window.__cianCapturedQuery — это и есть точка, откуда сбор берёт запрос",
    "в срезе нет записи __cianCapturedQuery — режем не тот код");
  const nп = PRC.trim().split("\n").length;
  check(nп > 3 && nп < 30, `срез pageResultCount: ${nп} строк`,
    `срез pageResultCount подозрительного размера (${nп} строк)`);
}

// ===========================================================================
// 4. ID ЖК ИЗ URL
// ===========================================================================
function testJkId() {
  section("currentJkIdFromUrl");
  const id = (href) => captureFactory({ href }, {}).currentJkIdFromUrl();
  eq(id(JK_URL), JK, "ЧПУ-адрес ЖК");
  eq(id(JK_URL + "?minprice=5000000"), JK, "ЧПУ-адрес ЖК с query-строкой");
  eq(id(JK_URL + "#offers"), JK, "ЧПУ-адрес ЖК с якорем");
  eq(id(JK_URL_Q), JK, "newobject[0]= в query (кодированные скобки)");
  eq(id(`https://www.cian.ru/cat.php?newobject[0]=${JK}`), JK, "newobject[0]= в query (сырые скобки)");
  eq(id(CAT_URL), null, "обычная выдача — ID ЖК не выдумывается");
}

// ===========================================================================
// 5. СВЕРКА ЗАПРОСА С ТЕКУЩИМ ЖК
// ===========================================================================
// Центральный инвариант всего файла. Ломается он ТИХО: выгрузка проходит,
// книга собирается, лоты валидные — просто не того ЖК.
function testMismatch() {
  section("queryMismatchesJk: свой ЖК против виджета «похожие ЖК»");
  const m = captureFactory({ href: JK_URL }, {}).queryMismatchesJk;

  check(m(byNewobject(JK), JK) === false,
    "свой ЖК по newobject — совпадение",
    "свой ЖК по newobject признан ЧУЖИМ -> запрос страницы будет выброшен");
  check(m(byGeo(JK), JK) === false,
    "свой ЖК по geo[newobject] — совпадение",
    "свой ЖК по geo признан ЧУЖИМ -> запрос страницы будет выброшен");
  check(m(byGeo(JK, "jk"), JK) === false,
    "свой ЖК по geo[jk] — совпадение",
    "geo с type=jk не распознан как привязка к ЖК");
  check(m(byNewobject(OTHER), JK) === true,
    "чужой newobject — непопадание",
    "чужой newobject признан СВОИМ -> выгрузится ЧУЖОЙ ЖК целиком");
  check(m(byGeo(OTHER), JK) === true,
    "чужой geo[newobject] — непопадание",
    "чужой geo признан СВОИМ -> выгрузится ЧУЖОЙ ЖК целиком");

  // Строки против чисел: id в geo приходит и числом, и строкой — сверка обязана
  // быть по значению, иначе виджет отбрасывал бы сам список страницы.
  check(m(byGeo(String(JK)), JK) === false,
    "id строкой («123456») сверяется по значению, а не по типу",
    "id строкой признан чужим — сверка сравнивает типы, а не значения");

  // Запрос без привязки к ЖК (обычный поиск по фильтрам/карте) проверке НЕ
  // подлежит — иначе на такой странице не захватилось бы ничего.
  check(m(jq({}), JK) === false,
    "запрос без newobject/geo — сверка не применяется",
    "запрос без привязки к ЖК признан чужим — на выдаче по фильтрам сбор ослепнет");
  check(m(byGeo(77, "location"), JK) === false,
    "geo[location] (город/район) — не привязка к ЖК, сверка не применяется",
    "geo[location] принят за привязку к ЖК");

  // Несколько ЖК в одном запросе: своё среди них — попадание.
  check(m(jq({ newobject: { type: "terms", value: [OTHER, JK] } }), JK) === false,
    "свой ЖК среди нескольких newobject — совпадение",
    "свой ЖК среди нескольких признан чужим");
}

// ===========================================================================
// 6. ВОСЕМЬ СЦЕНАРИЕВ ПЕРЕХВАТА ЦЕЛИКОМ
// ===========================================================================
// То же самое, но через rememberQuery — с разбором тела, чтением location и
// записью в window: ровно то, что происходит на живой странице.
function testRemember() {
  section("rememberQuery: что доедет до сбора");

  let r = remember(JK_URL, wrap(byNewobject(JK)));
  check(r.captured && r.captured.newobject.value[0] === JK,
    "1. свой ЖК по newobject — захвачен",
    "1. свой ЖК по newobject ПОТЕРЯН — сбор уйдёт угадывать фильтр по __NEXT_DATA__");

  r = remember(JK_URL, wrap(byGeo(JK)));
  check(!!r.captured, "2. свой ЖК по geo — захвачен", "2. свой ЖК по geo ПОТЕРЯН");

  r = remember(JK_URL, wrap(byNewobject(OTHER)));
  check(r.captured === undefined,
    "3. виджет «похожие ЖК» (чужой newobject) — отброшен",
    "3. виджет с ЧУЖИМ newobject ЗАХВАЧЕН -> выгрузится ЧУЖОЙ ЖК целиком");

  r = remember(JK_URL, wrap(byGeo(OTHER)));
  check(r.captured === undefined,
    "4. виджет «похожие ЖК» (чужой geo) — отброшен",
    "4. виджет с ЧУЖИМ geo ЗАХВАЧЕН -> выгрузится ЧУЖОЙ ЖК целиком");

  r = remember(JK_URL, wrap(byGeo(77, "location")));
  check(!!r.captured,
    "5. запрос без привязки к ЖК — захвачен (сверка не применяется)",
    "5. запрос без привязки к ЖК ОТБРОШЕН");

  r = remember(CAT_URL, wrap(byNewobject(OTHER)));
  check(!!r.captured,
    "6. URL не про ЖК — сверки нет, запрос захвачен",
    "6. на обычной выдаче сверка применилась и съела запрос");

  r = remember(JK_URL, JSON.stringify({ jsonQuery: { page: { type: "term", value: 2 } } }));
  check(r.captured === undefined && !r.threw,
    "7. тело без jsonQuery._type — игнор без броска",
    "7. тело без _type захвачено или бросило исключение в fetch страницы");

  r = remember(JK_URL, "{не json");
  check(r.captured === undefined && !r.threw,
    "8. битый JSON — игнор без броска",
    "8. битый JSON пролез наружу исключением — хук ломает fetch самой страницы");

  // Тело объектом, а не строкой: XHR.send и fetch отдают и то и другое.
  r = remember(JK_URL, { jsonQuery: byNewobject(JK) });
  check(!!r.captured, "тело объектом (не строкой) — захвачено",
    "тело объектом не разобрано — половина хуков (XHR) работает вхолостую");

  // Последний правильный запрос вытесняет предыдущий (пользователь поменял
  // фильтр), но виджет — не вытесняет.
  const win = {};
  const api = captureFactory({ href: JK_URL }, win);
  api.rememberQuery(wrap(jq({ newobject: { type: "terms", value: [JK] }, room: { type: "terms", value: [1] } })));
  api.rememberQuery(wrap(byNewobject(OTHER)));
  check(win.__cianCapturedQuery && win.__cianCapturedQuery.room,
    "виджет, пришедший ПОСЛЕ списка, не вытесняет захваченный запрос с фильтрами",
    "виджет затёр захваченный запрос -> уедет выгрузка без фильтров пользователя или чужого ЖК");
}

// ===========================================================================
// 7. СЧЁТЧИК НА СТРАНИЦЕ
// ===========================================================================
// pageResultCount кормит два места: подпись в шапке панели и диагностику
// сбора (console.log «на странице: N»). Число сверяется с собранным — по нему
// пользователь и судит, всё ли выгрузилось.
function testPageResultCount() {
  const prc = (text) => prcFactory({ body: text === null ? null : { innerText: text } })();
  section("pageResultCount");

  eq(prc("Найдено 1 234 объявления"), 1234, "«Найдено N» с обычным пробелом-разделителем");
  eq(prc("Найдено 1 234 объявления"), 1234, "неразрывный пробел (U+00A0) в разряде");
  eq(prc("Найдено 28 объявлений"), 28, "без разделителя разрядов");
  eq(prc("НАЙДЕНО 512 ОБЪЯВЛЕНИЙ"), 512, "регистр не важен");
  eq(prc("Купить квартиру\nНайдено 96 объявлений\nСортировка"), 96, "счётчик посреди текста страницы");
  eq(prc("Здесь ничего похожего нет"), null, "нет совпадения -> null");
  eq(prc(""), null, "пустой текст -> null");
  eq(prc(null), null, "document.body ещё не готов -> null, без броска");

  // Запасная ветка на случай страниц без слова «Найдено». Она НЕ РАБОТАЕТ:
  // \b в JS считает границей слова только ASCII, а после кириллической «я»/«й»
  // граница не наступает — если дальше не латиница/цифра. Значит регулярка
  // срабатывает лишь на строках вида «42 объявленияZ», которых на странице не
  // бывает, и запасной ветки фактически нет.
  const fallback = prc("Показать 42 объявления на карте");
  gap(fallback === null,
    () => "запасная ветка pageResultCount мертва: «Показать 42 объявления на карте» -> " +
          `${fallback} (ожидалось 42). \\b после кириллицы в JS не срабатывает — ` +
          "регулярка /([\\d ]+)\\s+объявлени[йяе]\\b/ ловит только «42 объявленияZ». " +
          "Лечится заменой \\b на (?![а-яё]) — тогда на странице без слова «Найдено» счётчик перестанет быть null",
    "запасная ветка pageResultCount ожила: счётчик читается и без слова «Найдено»");
  check(prc("42 объявленияZ") === 42,
    "…и это именно про \\b: с латиницей следом та же строка читается как 42",
    "запасная ветка не срабатывает даже там, где \\b формально выполняется — сломано что-то ещё");
}

// ===========================================================================
// 8. НЕГАТИВНЫЙ КОНТРОЛЬ
// ===========================================================================
// Тест, который не краснеет на сломанном коде, — это украшение прогона.
// Каждая поломка ниже вносится во ВРЕМЕННУЮ копию content.js (оригинал не
// трогается), и проверяется, что тест не просто упал, а НАЗВАЛ причину.
const BREAKAGES = [
  {
    name: "сверка ЖК всегда говорит «совпало» (виджеты перестают отбрасываться)",
    from: "    return !ids.map(Number).includes(Number(jkId));",
    to: "    return false;",
    expect: "ЗАХВАЧЕН -> выгрузится ЧУЖОЙ ЖК целиком",
  },
  {
    name: "сверка ЖК убрана из rememberQuery",
    from: "      if (jkId && queryMismatchesJk(b.jsonQuery, jkId)) return;",
    to: "      /* сверки нет */",
    expect: "ЗАХВАЧЕН -> выгрузится ЧУЖОЙ ЖК целиком",
  },
  {
    name: "сверка ЖК сравнивает типы (id строкой перестаёт совпадать)",
    from: "    return !ids.map(Number).includes(Number(jkId));",
    to: "    return !ids.includes(jkId);",
    expect: "id строкой признан чужим",
  },
  {
    name: "запрос без привязки к ЖК начинает считаться чужим",
    from: "    if (!ids.length) return false;",
    to: "    if (!ids.length) return true;",
    expect: "на выдаче по фильтрам сбор ослепнет",
  },
  {
    name: "сломан разбор newobject (виден только geo)",
    from: "    if (jq.newobject && Array.isArray(jq.newobject.value)) ids.push(...jq.newobject.value);",
    to: "    if (false) ids.push();",
    expect: "чужой newobject признан СВОИМ",
  },
  {
    name: "основная ветка pageResultCount не находит «Найдено»",
    from: "t.match(/Найдено",
    to: "t.match(/НайденоXX",
    expect: "«Найдено N» с обычным пробелом-разделителем",
  },
  {
    name: "потерян неразрывный пробел в чистке разрядов",
    from: 'parseInt(m[1].replace(/[\\s ]/g, ""), 10)',
    to: 'parseInt(m[1].replace(/[ ]/g, ""), 10)',
    expect: "неразрывный пробел",
  },
];

function negativeControl() {
  const src = fs.readFileSync(CONTENT_JS, "utf8");
  const dir = fs.mkdtempSync(path.join(process.env.TMPDIR || "/tmp", "cian-cap-neg-"));
  let bad = 0;
  console.log(`НЕГАТИВНЫЙ КОНТРОЛЬ: ${BREAKAGES.length} поломки во временных копиях content.js\n  (${dir})`);
  for (const b of BREAKAGES) {
    console.log(`\n── ${b.name} ─────────────────────`);
    const n = src.split(b.from).length - 1;
    if (n !== 1) {
      console.error(`  ✗ якорь поломки не найден или неоднозначен (${n} вхождений): «${b.from}»`);
      bad++; continue;
    }
    const file = path.join(dir, "m" + (BREAKAGES.indexOf(b) + 1) + ".js");
    fs.writeFileSync(file, src.replace(b.from, b.to));
    const out = spawnSync(process.execPath, [fileURLToPath(import.meta.url)], {
      encoding: "utf8",
      env: { ...process.env, CIAN_CONTENT_JS: file, NEGATIVE: "" },
    });
    const all = (out.stdout || "") + (out.stderr || "");
    const hit = all.split("\n").filter((l) => l.includes("✗") && l.includes(b.expect));
    if (out.status === 0) { console.error("  ✗ тест остался ЗЕЛЁНЫМ на сломанном коде (код возврата 0)"); bad++; }
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
function main() {
  const t0 = Date.now();
  console.log(`Слой захвата: ${CONTENT_JS}`);
  // Срез — единственная точка, где тест может умереть ДО первой проверки.
  // Умирать он обязан внятно, а не стек-трейсом из недр eval.
  try { bootstrap(); }
  catch (e) {
    fail(`не удалось вырезать слой захвата из content.js:\n      ${e.message.split("\n").join("\n      ")}`);
    return 1;
  }
  testSlice();
  testJkId();
  testMismatch();
  testRemember();
  testPageResultCount();

  const wall = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nПрогон ${wall} с. Известных дыр (△): ${gaps}${closed ? `, закрыто с прошлого раза: ${closed}` : ""}.`);
  console.log(failed ? `ПРОВАЛЕНО: ${failed}` : "Всё зелено.");
  return failed ? 1 : 0;
}

const isMain = process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;
if (isMain) process.exit(process.env.NEGATIVE ? negativeControl() : main());
