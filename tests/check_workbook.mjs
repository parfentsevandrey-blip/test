// Снимок-тест книги, которую строит extension/content.js (buildWorkbook).
//   node tests/check_workbook.mjs            — сверить с эталоном
//   UPDATE=1 node tests/check_workbook.mjs   — перезаписать эталон (осознанно!)
//   CIAN_CONTENT_JS=/путь/content.js node tests/check_workbook.mjs   — проверить другую копию
//
// ЗАЧЕМ. Слой генерации переезжает со SpreadsheetML 2003 (.xls) на настоящий
// .xlsx. Без снимка потеря листа, колонки или стиля обнаружится только когда
// кто-нибудь откроет файл в Excel — то есть через месяц. Тест раскладывает
// книгу в СЕМАНТИЧЕСКОЕ ДЕРЕВО (листы → строки → ячейки {v,t,fmt,href,fill,bold})
// и сверяет с tests/workbook_snapshot.json.
//
// Дерево сознательно НЕ повторяет XML: оно описывает то, что видит пользователь
// в Excel. Поэтому ту же функцию нормализации можно натравить на новую .xlsx-книгу
// и сравнить две реализации между собой. Экспорт для этого — в конце файла.
//
// ДЕТЕРМИНИЗМ. Внутрь вырезанного куска content.js подсовываются СВОИ Date и
// localStorage (см. makeDateStub/makeStorage). Date запинен на фиксированный
// момент, не выведенный из системных часов, поэтому «срок экспозиции, дн» и
// «Данные Циан на …» не меняются ни завтра, ни через год. Это проверяется
// прямо в тесте (assertClockShielded): книга строится второй раз с глобально
// сдвинутым на 400 дней Date и обязана совпасть байт-в-байт.
// Дополнительно все даты вида дд.мм.гггг режутся в "<DATE>" — как требует
// контракт дерева, чтобы снимок пережил и смену способа пиннинга времени.

process.env.TZ = "UTC";   // fmtDate() зовёт getDate()/getMonth() — локальные

import fs from "fs";
import path from "path";
import { fileURLToPath, pathToFileURL } from "url";
import { partsToTree, unzipParts } from "./xlsx_tree.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CONTENT_JS = process.env.CIAN_CONTENT_JS || path.join(ROOT, "extension", "content.js");
const SNAPSHOT = path.join(HERE, "workbook_snapshot.json");

const RealDate = Date;   // захватываем ДО любых подмен глобального Date

// ===========================================================================
// 1. Вырезаем слой генерации из content.js и запускаем его в песочнице
// ===========================================================================

// В вырезанном куске (categoryOf … download) снаружи нужны семь имён:
// dig, isHealthWarn, healthReasons, STATUS_LABEL, statusBreakdown — берём их
// ИСХОДНЫМ ТЕКСТОМ из того же файла, чтобы правка в content.js не разъехалась
// с тестом; Date и localStorage — наши стабы.
// Берём объявление ЦЕЛИКОМ: от префикса до строки, на которой скобки сошлись и
// стоит «;». Однострочный вариант — частный случай: isHealthWarn занимает три
// строки, healthReasons — двенадцать, и обрыв по первому \n давал в песочнице
// синтаксическую ошибку вместо внятного сообщения.
function grabDecl(src, prefix, what) {
  const i = src.indexOf(prefix);
  if (i < 0) throw new Error(`не нашёл ${what} («${prefix}») в ${CONTENT_JS}`);
  let j = i, depth = 0;
  for (;;) {
    const nl = src.indexOf("\n", j);
    const line = src.slice(j, nl < 0 ? undefined : nl);
    for (const ch of line) {
      if ("([{".includes(ch)) depth++;
      else if (")]}".includes(ch)) depth--;
    }
    if (nl < 0) return src.slice(i);
    if (depth === 0 && line.trimEnd().endsWith(";")) return src.slice(i, nl);
    j = nl + 1;
  }
}

export function makeFactory(contentJsPath = CONTENT_JS) {
  const src = fs.readFileSync(contentJsPath, "utf8");
  const from = src.indexOf("  function categoryOf(");
  const to = src.indexOf("  function download(");
  if (from < 0 || to < 0) throw new Error("не нашёл границы экспортирующей части content.js");

  const prelude = [
    grabDecl(src, "  const dig = ", "хелпер dig"),
    grabDecl(src, "  const isHealthWarn = ", "хелпер isHealthWarn"),
    grabDecl(src, "  const healthReasons = ", "хелпер healthReasons"),
    grabDecl(src, "  const STATUS_LABEL = ", "таблица STATUS_LABEL"),
    grabDecl(src, "  const statusBreakdown = ", "хелпер statusBreakdown"),
  ].join("\n");

  // eslint-disable-next-line no-eval
  return eval(`(function (Date, localStorage) {
${prelude}
${src.slice(from, to)}
  return { normalize, enrichExposure, buildWorkbook, computeChanges,
           buildXlsxParts, buildXlsxBlob,
           HEADERS, COLW, HEAT, HEAT_THRESH, HEADER_NOTES, FIN, CATS,
           // Слой хранения живёт в том же срезе и получает тот же стаб
           // localStorage — им пользуется tests/check_storage.mjs.
           loadHistory, saveHistory, loadSnapshots, saveSnapshots, loadMeta, saveMeta,
           loadRuns, rememberRun, RKEY, RUNS_KEEP,
           storageReady, storageResetForTests, migrateToIdb, migrationDue,
           STORE_FLATS, STORE_SNAPS, VERIFY_SAMPLE,
           backupDue, storageInfo, plural, exportBackupData, importBackupData, mergeHistoryFlats,
           storageFault: () => storageFault, HKEY, SKEY, MKEY };
})`);
}

// Date, запиненный на fixedMs. Наследуется от НАСТОЯЩЕГО Date, снятого до
// подмен, — иначе стаб развалится, когда тест подкрутит глобальные часы.
export function makeDateStub(fixedMs) {
  class FakeDate extends RealDate {
    constructor(...args) {
      if (args.length === 0) super(fixedMs);
      else if (args.length === 1) super(args[0]);
      else super(...args);
    }
    static now() { return fixedMs; }
  }
  return FakeDate;
}

// localStorage: content.js читает/пишет cianExcelHistory_v1 и cianExcelSnapshot_v1.
// opts.quotaChars — потолок ВСЕГО хранилища в символах: превышение бросает
// ошибку той же формы, что и Chrome. Именно это нужно, чтобы проверять
// поведение у потолка, а не флагом «сделай вид, что упало».
export function makeStorage(initial, opts = {}) {
  const map = new Map(initial ? Object.entries(initial) : []);
  const writes = [];
  const size = () => [...map.entries()].reduce((n, [k, v]) => n + k.length + v.length, 0);
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => {
      const s = String(v);
      if (opts.quotaChars != null) {
        const was = map.has(k) ? k.length + map.get(k).length : 0;
        if (size() - was + k.length + s.length > opts.quotaChars) {
          const e = new Error(`Failed to execute 'setItem' on 'Storage': Setting the value of '${k}' exceeded the quota.`);
          e.name = "QuotaExceededError"; e.code = 22;
          writes.push({ k, chars: s.length, ok: false });
          throw e;
        }
      }
      writes.push({ k, chars: s.length, ok: true });
      map.set(k, s);
    },
    removeItem: (k) => { map.delete(k); },
    clear: () => map.clear(),
    dump: () => Object.fromEntries(map),
    writes: () => writes.slice(),
    chars: () => size(),
  };
}

// ===========================================================================
// 2. Фикстуры: фиксированный набор офферов
// ===========================================================================
// Подобраны так, чтобы НЕПУСТЫМИ вышли все листы: Сводка (включая бенчмарки по
// этажу и метро, спарклайн, легенду тепловой карты, диагностику сбора),
// Все_лоты, все шесть листов по категориям, Топ_лотов, Дубли_разброс_цен,
// Продавцы, По_корпусам, Изменения. Плюс краевые случаи: лот без цены,
// незнакомое значение отделки, отделка из описания, управляющий символ и
// эмодзи в описании, переподача (сброс даты), обе ветки диагностики сбора.

const DAY = 86400;
// Оба момента — КОНСТАНТЫ, а не «сегодня»: снимок не должен зависеть от часов.
export const T_A_MS = RealDate.UTC(2026, 6, 30, 12, 0, 0);          // 30.07.2026
export const T_B_MS = T_A_MS + 30 * DAY * 1000;                     // 29.08.2026
const T_A = Math.floor(T_A_MS / 1000);
const T_B = Math.floor(T_B_MS / 1000);

const agency = (name) => ({ userType: "agency", agencyName: name });
const owner = () => ({ userType: "homeowner" });
const builder = () => ({ userType: "developer", companyName: "Стройинвест ДСК" });

// o(id, {…}) — минимальный оффер в формате api.cian.ru
function o(cianId, x) {
  const geo = { userInput: x.addr || "Москва, Кутузовский проспект, 12", undergrounds: x.metro || [] };
  return {
    cianId,
    roomsCount: x.rooms, isStudio: x.studio || false, flatType: x.flatType,
    totalArea: String(x.area),
    floorNumber: x.floor,
    building: { floorsCount: 25, buildYear: x.year || 2021, materialType: x.mat || "monolith" },
    newbuilding: { house: { name: x.corp } },
    livingArea: x.living, kitchenArea: x.kitchen,
    geo,
    user: x.user,
    isFromBuilder: x.user && x.user.userType === "developer",
    bargainTerms: x.price == null ? {} : { price: x.price },
    addedTimestamp: x.added,
    decoration: x.decoration, repairType: x.repairType,
    description: x.desc || "",
  };
}

const M = (name, time, walk) => ({ name, time, transportType: walk ? "walk" : "transport" });

// Базовый набор: 12 лотов в ЖК из двух корпусов.
export function fixtureOffersA(base = T_A) {
  return [
    // дубль №1: одна квартира у двух продавцов, цены разные
    o(101, { rooms: 1, area: 42.5, floor: 5, corp: "Корпус 1", price: 21500000, added: base - 120 * DAY,
      user: agency("Инвест Групп"), decoration: "fine", living: 18.2, kitchen: 11.4,
      metro: [M("Кутузовская", 7, true), M("Фили", 14, false)],
      desc: "Светлая квартира, окна во двор, тихий этаж." }),
    o(102, { rooms: 1, area: 42.5, floor: 5, corp: "Корпус 1", price: 22900000, added: base - 40 * DAY,
      user: agency("Дом Плюс"), decoration: "fine",
      metro: [M("Кутузовская", 7, true)],
      desc: "Та же квартира от другого агентства." }),
    // дубль №2: агентство против собственника
    o(103, { rooms: 2, area: 63, floor: 12, corp: "Корпус 1", price: 33000000, added: base - 200 * DAY,
      user: agency("Инвест Групп"), repairType: "euro",
      metro: [M("Кутузовская", 9, true)],
      desc: "Просторная двушка, вид на реку." }),
    o(104, { rooms: 2, area: 63, floor: 12, corp: "Корпус 1", price: 31200000, added: base - 15 * DAY,
      user: owner(), repairType: "euro",
      metro: [M("Кутузовская", 9, true)],
      desc: "Продаю сам, без комиссии." }),
    o(105, { rooms: 3, area: 88.4, floor: 3, corp: "Корпус 2", price: 44000000, added: base - 300 * DAY,
      user: builder(), decoration: "without",
      metro: [M("Фили", 12, false)],
      desc: "Квартира от застройщика в новом корпусе." }),
    o(106, { studio: true, rooms: 0, area: 25.8, floor: 2, corp: "Корпус 2", price: 10500000, added: base - 60 * DAY,
      user: builder(), decoration: "preFine",
      metro: [M("Фили", 12, false)],
      desc: "Студия от застройщика, сдача в этом году." }),
    // незнакомое значение поля Циан — не должно потеряться в Сводке
    o(107, { rooms: 4, area: 120, floor: 25, corp: "Корпус 2", price: 78000000, added: base - 90 * DAY,
      user: agency("Дом Плюс"), decoration: "smartFinish",
      metro: [M("Фили", 22, false)],
      desc: "Пентхаус на последнем этаже, панорамные окна." }),
    // отделка определяется ИЗ ОПИСАНИЯ, поля нет
    o(108, { flatType: "openPlan", area: 55, floor: 1, corp: "Корпус 2", price: 26000000, added: base - 25 * DAY,
      user: owner(),
      metro: [M("Кутузовская", 4, true)],
      desc: "Свободная планировка, сделан дизайнерский ремонт по проекту." }),
    // управляющий символ + эмодзи в описании (проверка esc)
    o(109, { rooms: 1, area: 38, floor: 8, corp: "Корпус 1", price: 19000000, added: base - 10 * DAY,
      user: agency("Инвест Групп"),
      metro: [M("Кутузовская", 6, true)],
      desc: "Окна на парк \u0001 🏠 высокие потолки." }),
    o(110, { rooms: 2, area: 70, floor: 20, corp: "Корпус 2", price: 41000000, added: base - 55 * DAY,
      user: agency("Дом Плюс"), repairType: "cosmetic",
      metro: [M("Фили", 18, false)],
      desc: "Двушка с ремонтом, свободна юридически." }),
    // лот без цены: ppm/тепловая карта/индекс — null
    o(111, { rooms: 3, area: 95, floor: 15, corp: "Корпус 1", added: base - 5 * DAY,
      user: agency("Инвест Групп"),
      metro: [M("Кутузовская", 11, true)],
      desc: "Цена по запросу." }),
    o(112, { studio: true, rooms: 0, area: 27, floor: 4, corp: "Корпус 2", price: 13500000, added: base - 70 * DAY,
      user: builder(), decoration: "fine",
      metro: [M("Фили", 12, false)],
      desc: "Студия с чистовой отделкой." }),
  ];
}

// Вторая выгрузка того же ЖК месяцем позже: цены поехали, один лот пропал,
// один появился, один переподан (сброс даты) — так наполняется лист «Изменения».
export function fixtureOffersB() {
  // те же самые объявления (даты подачи НЕ трогаем — Циан их не меняет сам)
  const rows = fixtureOffersA(T_A).filter((x) => x.cianId !== 111);
  const by = (id) => rows.find((x) => x.cianId === id);
  by(109).bargainTerms.price = 18200000;   // подешевел
  by(110).bargainTerms.price = 43500000;   // подорожал
  // 101 и 102 — одна физическая квартира (одинаковый отпечаток), в снимке
  // остаётся ОДНА запись: смена цены у 101 в лист «Изменения» не попадёт.
  // Это текущее поведение, и снимок обязан его зафиксировать.
  by(101).bargainTerms.price = 20900000;
  by(107).addedTimestamp = T_B;            // переподача: дата Циан «свежее» реальной
  rows.push(o(113, { rooms: 2, area: 58, floor: 7, corp: "Корпус 2", price: 30500000, added: T_B - 3 * DAY,
    user: agency("Дом Плюс"), decoration: "fineWithFurniture",
    metro: [M("Фили", 15, false)],
    desc: "Новый лот в выдаче, под ключ с мебелью." }));
  return rows;
}

const SUBJ_JK = { id: 1704112, isJk: true, title: "ЖК Кутузовский 12", slug: "kutuzovskiy-12" };
const SUBJ_FILTER = { id: null, isJk: false, title: "Выборка Циан · Кутузовский проспект", slug: "vyborka" };

const TOTALS_BY_ROOM = { 9: 14, 7: 3, 1: 40, 2: 52, 3: 21, 4: 6, 5: 2, 6: 1 };

// health: две ветки блока «ДИАГНОСТИКА СБОРА» — спокойная и тревожная.
// В тревожной намеренно собраны ВСЕ поводы разом (недобор, исчерпанный бюджет,
// доля повторов, дрейф total), чтобы список причин целиком попал в снимок.
const HEALTH_OK = { requests: 14, http: 15, retries: 1, retryStatuses: { 429: 1 }, totalDrift: 0, shortfall: 0, budgetExhausted: false };
const HEALTH_WARN = { requests: 12, http: 31, retries: 4, retryStatuses: { 429: 3, 500: 1 }, totalDrift: 3, shortfall: 7, budgetExhausted: true };

// Журнал сбора: по одной записи каждого вида, какие вообще бывают — успех,
// троттлинг с Retry-After, обрыв связи, проверка браузера под кодом 200.
// Иначе лист «Журнал_сбора» попал бы в снимок только в благополучном варианте.
const LOG_WARN = [
  { t: 0, att: 1, gap: 0, seg: "все", page: 1, dur: 210, st: 200, ct: "json", len: 48120, raSeen: 0, n: 28, tot: 150 },
  { t: 812, att: 1, gap: 602, seg: "все", page: 2, dur: 194, st: 200, ct: "json", len: 47004, raSeen: 0, n: 28, tot: 150 },
  { t: 1620, att: 1, gap: 614, seg: "r2", page: 1, dur: 3980, st: 429, ct: "json", len: 62, raSeen: 1 },
  { t: 32100, att: 2, gap: 30120, seg: "r2", page: 1, dur: 240, st: 200, ct: "json", len: 45990, raSeen: 0, n: 28, tot: 52 },
  { t: 33200, att: 1, gap: 660, seg: "r2 ₽5.0-9.0", page: 1, dur: 15020, st: "NET", ct: null, len: null, raSeen: 0 },
  { t: 49900, att: 2, gap: 1680, seg: "r2 ₽5.0-9.0", page: 1, dur: 1170, st: "HTML", ct: "html", len: 21570, raSeen: 0 },
];
const AGG_WARN = {
  ts: 1781956800, http: 31, pages: 12, byStatus: { 200: 25, 429: 3, 500: 1, NET: 1, HTML: 1 },
  p50: 240, p95: 15020, minGap: 0, zeroGaps: 1, wallMs: 51070, drift: 3,
  shortfall: 7, raSeen: 1, budget: 1, cancel: 0, waf: 0,
};

// Один прогон = ровно то, что делает run() в content.js:
// normalize → sort по ₽/м² → enrichExposure → buildWorkbook.
export function buildOnce({ offers, subj, nowMs, storage, totalsByRoom, totalInJk, health, log, agg, contentJsPath }) {
  const api = makeFactory(contentJsPath)(makeDateStub(nowMs), storage);
  const rows = offers.map(api.normalize)
    .sort((a, b) => (a.ppm == null) - (b.ppm == null) || (a.ppm || 0) - (b.ppm || 0));
  api.enrichExposure(rows, subj.id);
  // buildWorkbook отдаёт ДЕРЕВО книги, buildXlsxParts превращает его в части
  // OOXML. Снимок снимается с частей — то есть с того, что реально уедет в файл.
  const book = api.buildWorkbook(subj, rows, totalsByRoom, totalInJk, health, log, agg);
  return { book, parts: api.buildXlsxParts(book) };
}

// Три сценария. Первые два ДЕЛЯТ localStorage: без прошлого снимка листа
// «Изменения» нет, со снимком — есть. Третий — выборка по фильтрам (не ЖК):
// листа «По корпусам» быть не должно.
export function buildScenarios(contentJsPath = CONTENT_JS) {
  const jkStore = makeStorage();
  const noHistory = buildOnce({
    offers: fixtureOffersA(), subj: SUBJ_JK, nowMs: T_A_MS, storage: jkStore,
    totalsByRoom: null, totalInJk: 0, health: HEALTH_OK, contentJsPath,
  });
  const withHistory = buildOnce({
    offers: fixtureOffersB(), subj: SUBJ_JK, nowMs: T_B_MS, storage: jkStore,
    totalsByRoom: TOTALS_BY_ROOM, totalInJk: 150, health: HEALTH_WARN, log: LOG_WARN, agg: AGG_WARN, contentJsPath,
  });
  const filterSubject = buildOnce({
    offers: fixtureOffersA().slice(0, 6), subj: SUBJ_FILTER, nowMs: T_A_MS, storage: makeStorage(),
    totalsByRoom: TOTALS_BY_ROOM, totalInJk: 90, health: null, contentJsPath,
  });
  return [
    { name: "jk-no-history", ...noHistory },
    { name: "jk-with-history", ...withHistory },
    { name: "filter-subject", ...filterSubject },
  ];
}

// ===========================================================================
// 3. Мини-парсер XML (в node нет DOMParser, зависимостей у проекта нет)
// ===========================================================================
// Хватает ровно на машинно-сгенерированный SpreadsheetML: теги, атрибуты в
// двойных кавычках, текст, сущности. Ни CDATA, ни комментариев книга не содержит.

const ENT = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };
function decodeEntities(s) {
  return s.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);/g, (m, g) => {
    if (g[0] !== "#") return ENT[g];
    return String.fromCodePoint(g[1] === "x" || g[1] === "X" ? parseInt(g.slice(2), 16) : parseInt(g.slice(1), 10));
  });
}

export function parseXml(xml) {
  const root = { name: "#root", attrs: {}, children: [], text: "" };
  const stack = [root];
  const top = () => stack[stack.length - 1];
  let i = 0;
  while (i < xml.length) {
    const lt = xml.indexOf("<", i);
    if (lt < 0) { top().text += decodeEntities(xml.slice(i)); break; }
    if (lt > i) top().text += decodeEntities(xml.slice(i, lt));
    if (xml.startsWith("<?", lt)) { i = xml.indexOf("?>", lt) + 2; continue; }
    if (xml.startsWith("<!--", lt)) { i = xml.indexOf("-->", lt) + 3; continue; }
    if (xml.startsWith("</", lt)) {
      const gt = xml.indexOf(">", lt);
      if (stack.length < 2) throw new Error("лишний закрывающий тег в XML");
      stack.pop();
      i = gt + 1;
      continue;
    }
    // конец открывающего тега: '>' вне кавычек (esc() не даёт сырому '>' попасть в атрибут)
    let j = lt + 1, quote = null;
    for (; j < xml.length; j++) {
      const ch = xml[j];
      if (quote) { if (ch === quote) quote = null; }
      else if (ch === '"' || ch === "'") quote = ch;
      else if (ch === ">") break;
    }
    if (j >= xml.length) throw new Error("незакрытый тег в XML");
    const inner = xml.slice(lt + 1, j);
    const selfClose = inner.endsWith("/");
    const body = selfClose ? inner.slice(0, -1) : inner;
    const nameM = body.match(/^([^\s/>]+)/);
    const el = { name: nameM ? nameM[1] : "", attrs: {}, children: [], text: "" };
    for (const m of body.slice(nameM ? nameM[1].length : 0).matchAll(/([^\s=]+)\s*=\s*"([^"]*)"/g)) {
      el.attrs[m[1]] = decodeEntities(m[2]);
    }
    top().children.push(el);
    if (!selfClose) stack.push(el);
    i = j + 1;
  }
  if (stack.length !== 1) throw new Error("незакрытые элементы в XML: " + stack.slice(1).map((e) => e.name).join(", "));
  return root;
}

const kids = (el, name) => el.children.filter((c) => c.name === name);
const kid = (el, name) => el.children.find((c) => c.name === name) || null;

// ===========================================================================
// 4. SpreadsheetML → семантическое дерево (контракт)
// ===========================================================================

export function colName(idx) {
  let s = "", n = idx;
  do { s = String.fromCharCode(65 + (n % 26)) + s; n = Math.floor(n / 26) - 1; } while (n >= 0);
  return s;
}
const a1 = (row, col) => colName(col) + (row + 1);

// "R4C1:R16C27" -> "A4:AA16"
function r1c1ToA1(ref) {
  const m = String(ref).match(/^R(\d+)C(\d+):R(\d+)C(\d+)$/);
  if (!m) return String(ref);
  return `${colName(+m[2] - 1)}${m[1]}:${colName(+m[4] - 1)}${m[3]}`;
}

const upColor = (c) => {
  if (!c) return null;
  const h = String(c).replace(/^#/, "").toUpperCase();
  return "#" + (h.length === 8 ? h.slice(2) : h).slice(0, 6);   // ARGB -> RGB
};

// Правило контракта: любые даты и производные от текущего времени → "<DATE>".
const DATE_RX = /\d{1,2}\.\d{1,2}\.\d{4}|\d{4}-\d{2}-\d{2}T[0-9:.]+Z?/g;
export const maskDates = (s) => (typeof s === "string" ? s.replace(DATE_RX, "<DATE>") : s);

function parseStyles(workbook) {
  const out = {};
  const styles = kid(workbook, "Styles");
  if (!styles) return out;
  for (const st of kids(styles, "Style")) {
    const nf = kid(st, "NumberFormat"), font = kid(st, "Font"), inter = kid(st, "Interior");
    out[st.attrs["ss:ID"]] = {
      fmt: nf ? (nf.attrs["ss:Format"] || null) : null,
      fill: inter && inter.attrs["ss:Pattern"] !== "None" ? upColor(inter.attrs["ss:Color"]) : null,
      bold: !!(font && (font.attrs["ss:Bold"] === "1" || font.attrs["ss:Bold"] === "true")),
    };
  }
  return out;
}

const EMPTY_CELL = Object.freeze({ v: null, t: null, fmt: null, href: null, fill: null, bold: false });

/**
 * Разложить книгу SpreadsheetML 2003 в семантическое дерево.
 * Тот же формат должна отдавать нормализация будущей .xlsx-книги —
 * тогда две реализации сравниваются напрямую.
 * @returns {{sheets: Array}}
 */
export function normalizeSpreadsheetML(xml) {
  const doc = parseXml(xml);
  const wb = kid(doc, "Workbook");
  if (!wb) throw new Error("в XML нет <Workbook>");
  const styles = parseStyles(wb);
  const sheets = kids(wb, "Worksheet").map((ws) => {
    const table = kid(ws, "Table");
    if (!table) throw new Error(`лист «${ws.attrs["ss:Name"]}» без <Table>`);

    const widths = kids(table, "Column").map((c) => {
      const w = parseFloat(c.attrs["ss:Width"]);
      return isNaN(w) ? null : w;
    });
    // контракт: не абсолют (старая книга — пиксели, новая — символы), а отношение к первой колонке
    const w0 = widths.length ? widths[0] : null;
    const colWidths = widths.map((w) => (w == null || !w0 ? null : Math.round((w / w0) * 100) / 100));

    const merges = [];
    const rows = kids(table, "Row").map((rowEl, rIdx) =>
      kids(rowEl, "Cell").map((cellEl, cIdx) => {
        const st = styles[cellEl.attrs["ss:StyleID"]] || null;
        const across = parseInt(cellEl.attrs["ss:MergeAcross"], 10);
        if (across > 0) merges.push(`${a1(rIdx, cIdx)}:${a1(rIdx, cIdx + across)}`);
        const data = kid(cellEl, "Data");
        // Комментарии (HEADER_NOTES) в дерево не входят: по решению пользователя
        // в .xlsx они заменяются строкой-пояснением, сверять их между форматами нечего.
        if (!data) return { ...EMPTY_CELL };
        const isNum = data.attrs["ss:Type"] === "Number";
        let v = data.text;
        if (isNum) v = Number(v);
        else v = maskDates(String(v).replace(/\s+$/, ""));
        if (!isNum && v === "") return { ...EMPTY_CELL };
        return {
          v, t: isNum ? "n" : "s",
          fmt: st ? st.fmt : null,
          href: cellEl.attrs["ss:HRef"] || null,
          fill: st ? st.fill : null,
          bold: st ? st.bold : false,
        };
      })
    );

    const afEl = kid(ws, "AutoFilter");
    const wo = kid(ws, "WorksheetOptions");
    let freeze = null;
    if (wo) {
      const h = parseInt((kid(wo, "SplitHorizontal") || { text: "" }).text, 10) || 0;
      const v = parseInt((kid(wo, "SplitVertical") || { text: "" }).text, 10) || 0;
      if (h || v) freeze = { rows: h, cols: v };
    }
    return {
      name: ws.attrs["ss:Name"],
      colWidths,
      freeze,
      autoFilter: afEl ? r1c1ToA1(afEl.attrs["x:Range"]) : null,
      merges,
      rows,
      // В SpreadsheetML тепловая карта — предрассчитанные заливки (см. fill).
      // В .xlsx на её месте появится colorScale, и это единственное место,
      // где две книги ОБЯЗАНЫ разойтись.
      condFormats: [],
    };
  });
  return { sheets };
}

// ===========================================================================
// 5. Компактная сериализация снимка (иначе JSON нечитаем и огромен)
// ===========================================================================
// Ячейка -> [v, t, fmt, href, fill, bold] с обрезанными хвостовыми дефолтами;
// полностью пустая -> null. Строка книги -> одна строка JSON.

const CELL_DEFAULTS = [null, null, null, null, null, false];
export function encodeCell(c) {
  const a = [c.v, c.t, c.fmt, c.href, c.fill, c.bold];
  let n = a.length;
  while (n > 0 && a[n - 1] === CELL_DEFAULTS[n - 1]) n--;
  return n === 0 ? null : a.slice(0, n);
}
export const encodeTree = (tree) => ({
  sheets: tree.sheets.map((s) => ({
    name: s.name, colWidths: s.colWidths, freeze: s.freeze, autoFilter: s.autoFilter,
    merges: s.merges, condFormats: s.condFormats, rows: s.rows.map((r) => r.map(encodeCell)),
  })),
});

function serialize(snapshot) {
  const j = (x) => JSON.stringify(x);
  const out = ["{", `  "_format": ${j(snapshot._format)},`, `  "_note": ${j(snapshot._note)},`, '  "scenarios": ['];
  snapshot.scenarios.forEach((sc, si) => {
    out.push("    {", `      "name": ${j(sc.name)},`, '      "sheets": [');
    sc.sheets.forEach((sh, hi) => {
      out.push("        {");
      out.push(`          "name": ${j(sh.name)},`);
      out.push(`          "colWidths": ${j(sh.colWidths)},`);
      out.push(`          "freeze": ${j(sh.freeze)},`);
      out.push(`          "autoFilter": ${j(sh.autoFilter)},`);
      out.push(`          "merges": ${j(sh.merges)},`);
      out.push(`          "condFormats": ${j(sh.condFormats)},`);
      out.push('          "rows": [');
      sh.rows.forEach((r, ri) => out.push("            " + j(r) + (ri === sh.rows.length - 1 ? "" : ",")));
      out.push("          ]");
      out.push("        }" + (hi === sc.sheets.length - 1 ? "" : ","));
    });
    out.push("      ]");
    out.push("    }" + (si === snapshot.scenarios.length - 1 ? "" : ","));
  });
  out.push("  ]", "}");
  return out.join("\n") + "\n";
}

export function buildSnapshotObject(contentJsPath = CONTENT_JS) {
  return {
    _format: "cian-workbook-snapshot/1",
    _note: "Снимок книги extension/content.js. Ячейка: [v, t, fmt, href, fill, bold], хвостовые дефолты обрезаны, пустая ячейка = null. Даты вырезаны в <DATE>. Ширины колонок — отношение к первой. Обновлять только через UPDATE=1 node tests/check_workbook.mjs и глазами по диффу.",
    scenarios: buildScenarios(contentJsPath).map((sc) => ({
      name: sc.name,
      ...encodeTree(partsToTree(sc.parts)),
    })),
  };
}

// ===========================================================================
// 6. Сравнение с эталоном
// ===========================================================================

function diffScenarios(actual, expected, push) {
  const byName = (arr) => new Map(arr.map((x) => [x.name, x]));
  const aS = byName(actual), eS = byName(expected);
  for (const name of eS.keys()) if (!aS.has(name)) push(`пропал сценарий «${name}»`);
  for (const name of aS.keys()) if (!eS.has(name)) push(`появился незнакомый сценарий «${name}»`);
  for (const name of eS.keys()) {
    if (!aS.has(name)) continue;
    diffSheets(name, aS.get(name).sheets, eS.get(name).sheets, push);
  }
}

function diffSheets(scenario, actual, expected, push) {
  const an = actual.map((s) => s.name), en = expected.map((s) => s.name);
  if (an.join("|") !== en.join("|")) {
    push(`[${scenario}] набор/порядок листов разошёлся:\n      было:  ${en.join(", ")}\n      стало: ${an.join(", ")}`);
  }
  const aMap = new Map(actual.map((s) => [s.name, s]));
  for (const exp of expected) {
    const act = aMap.get(exp.name);
    if (!act) continue;
    const tag = `[${scenario}] лист «${exp.name}»`;
    for (const f of ["colWidths", "freeze", "autoFilter", "merges", "condFormats"]) {
      if (JSON.stringify(act[f]) !== JSON.stringify(exp[f])) {
        push(`${tag}: ${f}\n      было:  ${JSON.stringify(exp[f])}\n      стало: ${JSON.stringify(act[f])}`);
      }
    }
    if (act.rows.length !== exp.rows.length) {
      push(`${tag}: строк было ${exp.rows.length}, стало ${act.rows.length}`);
    }
    const n = Math.min(act.rows.length, exp.rows.length);
    for (let r = 0; r < n; r++) {
      const ar = act.rows[r], er = exp.rows[r];
      if (ar.length !== er.length) {
        push(`${tag}, строка ${r + 1}: ячеек было ${er.length}, стало ${ar.length} (сдвиг колонок!)`);
      }
      const m = Math.min(ar.length, er.length);
      for (let c = 0; c < m; c++) {
        if (JSON.stringify(ar[c]) !== JSON.stringify(er[c])) {
          push(`${tag}, ${a1(r, c)}:\n      было:  ${JSON.stringify(er[c])}\n      стало: ${JSON.stringify(ar[c])}`);
        }
      }
    }
  }
}

// Проверка, что книга не зависит от системных часов: строим её второй раз,
// подменив ГЛОБАЛЬНЫЙ Date на «+400 дней», и требуем полного совпадения.
function assertClockShielded(contentJsPath) {
  const before = buildScenarios(contentJsPath).map((s) => s.xml).join("\u0000");
  const shift = 400 * DAY * 1000;
  const saved = globalThis.Date;
  class ShiftedDate extends RealDate {
    constructor(...args) {
      if (args.length === 0) super(RealDate.now() + shift);
      else if (args.length === 1) super(args[0]);
      else super(...args);
    }
    static now() { return RealDate.now() + shift; }
  }
  globalThis.Date = ShiftedDate;
  let after;
  try { after = buildScenarios(contentJsPath).map((s) => s.xml).join("\u0000"); }
  finally { globalThis.Date = saved; }
  return before === after;
}

async function main() {
  let failed = 0;
  const fail = (m) => { console.error("  ✗ " + m); failed++; };
  const pass = (m) => console.log("  ✓ " + m);

  if (new RealDate().getTimezoneOffset() !== 0) {
    fail("не удалось переключить процесс в UTC (TZ=UTC) — снимок дат будет плавать");
  }

  const actual = buildSnapshotObject();
  const stats = actual.scenarios.reduce((acc, sc) => {
    sc.sheets.forEach((sh) => {
      acc.sheets++;
      acc.rows += sh.rows.length;
      sh.rows.forEach((r) => { acc.cells += r.length; });
    });
    return acc;
  }, { sheets: 0, rows: 0, cells: 0 });

  if (process.env.UPDATE) {
    fs.writeFileSync(SNAPSHOT, serialize(actual));
    console.log(`Эталон перезаписан: ${SNAPSHOT}`);
    console.log(`  сценариев ${actual.scenarios.length}, листов ${stats.sheets}, строк ${stats.rows}, ячеек ${stats.cells}`);
    return 0;
  }

  if (!fs.existsSync(SNAPSHOT)) {
    fail(`нет эталона ${SNAPSHOT} — создайте его: UPDATE=1 node tests/check_workbook.mjs`);
    return 1;
  }
  const expected = JSON.parse(fs.readFileSync(SNAPSHOT, "utf8"));
  if (expected._format !== actual._format) {
    fail(`формат эталона «${expected._format}» не совпадает с «${actual._format}»`);
    return 1;
  }

  const diffs = [];
  diffScenarios(actual.scenarios, expected.scenarios, (m) => diffs.push(m));
  if (diffs.length) {
    const SHOW = 30;
    diffs.slice(0, SHOW).forEach((d) => fail(d));
    if (diffs.length > SHOW) fail(`…и ещё ${diffs.length - SHOW} расхождений`);
    console.error("\n  Если изменение книги СОЗНАТЕЛЬНОЕ — сверьте дифф глазами и обновите эталон:\n    UPDATE=1 node tests/check_workbook.mjs");
  } else {
    pass(`книга совпадает с эталоном: сценариев ${actual.scenarios.length}, листов ${stats.sheets}, строк ${stats.rows}, ячеек ${stats.cells}`);
  }

  // Листы, которых не должно/должно быть — отдельными понятными проверками,
  // чтобы при падении было видно ЧТО сломалось, а не только «дифф в строке 12».
  const byScen = new Map(actual.scenarios.map((s) => [s.name, s.sheets.map((x) => x.name)]));
  const has = (sc, sheet) => (byScen.get(sc) || []).includes(sheet);
  if (!has("jk-no-history", "Изменения")) pass("без прошлого снимка лист «Изменения» не создаётся");
  else fail("лист «Изменения» появился на первой выгрузке (снимка ещё нет)");
  if (has("jk-with-history", "Изменения")) pass("со снимком прошлой выгрузки лист «Изменения» создаётся");
  else fail("лист «Изменения» не создался при наличии прошлого снимка");
  if (has("filter-subject", "По_корпусам")) fail("лист «По_корпусам» построен для выборки по фильтрам (не ЖК)");
  else pass("для выборки по фильтрам лист «По_корпусам» не создаётся");
  for (const s of ["Сводка", "Все_лоты", "Топ_лотов", "Дубли_разброс_цен", "Продавцы", "По_корпусам"]) {
    if (!has("jk-no-history", s)) fail(`в основном сценарии нет листа «${s}» (лист пропал из книги либо фикстуры перестали его наполнять)`);
  }

  if (assertClockShielded()) pass("книга не зависит от системных часов (сдвиг на 400 дней ничего не меняет)");
  else fail("книга изменилась при сдвиге системных часов — стаб Date не держит");

  // Части OOXML — это ещё не файл. Проверяем сам контейнер: книга обязана
  // начинаться с сигнатуры PK (никакого BOM — он делает zip нечитаемым) и
  // после распаковки давать то же дерево, что и до упаковки.
  await assertZipRoundTrip(pass, fail);

  console.log(failed ? `\nПРОВАЛЕНО: ${failed}` : "\nВсё зелено.");
  return failed ? 1 : 0;
}

async function assertZipRoundTrip(pass, fail) {
  const api = makeFactory()(makeDateStub(T_A_MS), makeStorage());
  for (const sc of buildScenarios()) {
    const blob = await api.buildXlsxBlob(sc.book);
    const buf = Buffer.from(await blob.arrayBuffer());
    if (buf[0] !== 0x50 || buf[1] !== 0x4b) {
      fail(`${sc.name}: файл не начинается с PK — перед zip что-то дописано (BOM?)`);
      continue;
    }
    const a = JSON.stringify(encodeTree(partsToTree(api.buildXlsxParts(sc.book))));
    const b = JSON.stringify(encodeTree(partsToTree(unzipParts(buf))));
    if (a !== b) fail(`${sc.name}: после упаковки в zip и распаковки книга изменилась`);
    else pass(`${sc.name}: zip собран и распакован без потерь (${buf.length} байт)`);
  }
}

const isMain = process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;
if (isMain) process.exit(await main());

// ===========================================================================
// 7. Что переиспользовать при переезде на .xlsx
// ===========================================================================
// import {
//   normalizeSpreadsheetML,   // старая книга (XML-строка) -> дерево
//   encodeTree, encodeCell,   // дерево -> компактная форма, в которой лежит эталон
//   parseXml, colName, maskDates,   // кирпичи для нормализации новой книги
//   buildScenarios, buildOnce,      // те же три сценария на тех же фикстурах
//   fixtureOffersA, fixtureOffersB, // сами офферы
//   makeDateStub, makeStorage, makeFactory,
//   T_A_MS, T_B_MS,           // запиненные моменты времени
// } from "./check_workbook.mjs";
//
// Для новой книги нужен ПАРНЫЙ normalizeXlsx(buffer) -> дерево того же вида
// (распаковать zip, разобрать sheet*.xml + styles.xml + sharedStrings.xml).
// Дальше encodeTree(...) обеих книг сравниваются один в один. Заведомо
// разойдутся ровно три места, и это ожидаемо — так задумано:
//   1) строка-пояснение под шапкой вместо комментариев HEADER_NOTES
//      (в дереве появится лишняя строка, а condFormats/комментарии не пересекаются);
//   2) fill у колонок «Цена за м²» и «Откл. от средней» уедет в condFormats
//      (colorScale вместо девяти предрассчитанных заливок h1…h9);
//   3) colWidths: старая книга задаёт пиксели, новая — символы, поэтому в дереве
//      хранится ОТНОШЕНИЕ к первой колонке; совпасть оно должно с точностью 0.01,
//      а не байт-в-байт — при расхождении сверяйте глазами, не подгоняйте эталон.
// ВСЁ ОСТАЛЬНОЕ (имена и порядок листов, тексты, числа, форматы, ссылки,
// заморозка, автофильтр, объединения) обязано совпасть.
