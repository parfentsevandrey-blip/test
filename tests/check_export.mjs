// Проверка книги, которую строит cian_browser.js (Способ 1 — вставка в консоль).
//   node tests/check_export.mjs
//
// Этот экспортёр собирает XML вручную, а <Cell> в нём не поддерживает ss:Index —
// поэтому пропуск ОДНОЙ ячейки бесшумно сдвигает все столбцы правее, и Excel
// никакой ошибки не покажет: цена просто окажется под чужим заголовком.
// Тест сверяет число ячеек в каждой строке с числом заголовков и проверяет,
// что блок «ОТДЕЛКА / РЕМОНТ» в Сводке сходится с числом лотов.

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const src = fs.readFileSync(path.join(ROOT, "cian_browser.js"), "utf8");

// вырезаем «чистую» часть скрипта: от нормализации до генерации книги,
// без сетевых запросов и обращений к DOM
const from = src.indexOf("  function categoryOf(");
const to = src.indexOf("  function download(");
if (from < 0 || to < 0) throw new Error("не нашёл границы экспортирующей части cian_browser.js");

const api = eval(`(() => {
  const JKNAME = "Тестовый ЖК", JKID = 123456;
${src.slice(from, to)}
  return { normalize, buildWorkbook, HEADERS, COLW, FIN };
})()`);

let failed = 0;
const fail = (m) => { console.error("  ✗ " + m); failed++; };
const pass = (m) => console.log("  ✓ " + m);

if (api.COLW.length === api.HEADERS.length) pass(`COLW и HEADERS согласованы (${api.HEADERS.length} колонок)`);
else fail(`COLW = ${api.COLW.length}, HEADERS = ${api.HEADERS.length}`);

const OFFERS = [
  { cianId: 1, roomsCount: 1, totalArea: "40", floorNumber: 3, bargainTerms: { price: 10000000 },
    addedTimestamp: 1748000000, decoration: "fine", description: "Светлая квартира, окна во двор." },
  { cianId: 2, roomsCount: 2, totalArea: "60", floorNumber: 5, bargainTerms: { price: 20000000 },
    addedTimestamp: 1748100000, description: "Сделан евроремонт в квартире." },
  // значение поля, которого нет в словаре: Циан ввёл новую категорию
  { cianId: 3, roomsCount: 2, totalArea: "55", floorNumber: 7, bargainTerms: { price: 18000000 },
    addedTimestamp: 1748200000, decoration: "smartFinish", description: "Окна на парк, высокие потолки." },
  // ни поля, ни признаков в тексте
  { cianId: 4, roomsCount: 0, isStudio: true, totalArea: "25", floorNumber: 9,
    bargainTerms: { price: 8000000 }, addedTimestamp: 1748300000, description: "Рядом метро и школа." },
];
const rows = OFFERS.map(api.normalize);
const xml = api.buildWorkbook(rows, { 1: 10, 2: 20, 9: 5 }, 40);

// --- геометрия таблиц --------------------------------------------------------
const sheets = [...xml.matchAll(/<Worksheet ss:Name="([^"]*)">([\s\S]*?)<\/Worksheet>/g)];
if (!sheets.length) fail("в книге нет ни одного листа");
let geomOk = true;
for (const [, name, body] of sheets) {
  if (name === "Сводка") continue;                     // у сводки своя геометрия
  const cols = (body.match(/<Column /g) || []).length;
  if (cols !== api.HEADERS.length) {
    fail(`лист «${name}»: ${cols} <Column> при ${api.HEADERS.length} заголовках`);
    geomOk = false;
  }
  const rowsXml = [...body.matchAll(/<Row>([\s\S]*?)<\/Row>/g)].map((m) => m[1]);
  // строки 1-3 — заголовок листа и пустая строка, шапка — 4-я, данные — с 5-й
  rowsXml.slice(3).forEach((rowBody, i) => {
    const cells = (rowBody.match(/<Cell(\s|\/|>)/g) || []).length;
    if (cells !== api.HEADERS.length) {
      fail(`лист «${name}», строка ${i + 4}: ${cells} ячеек при ${api.HEADERS.length} заголовках`);
      geomOk = false;
    }
  });
}
if (geomOk) pass(`во всех листах данных число ячеек совпадает с числом заголовков`);

// --- «Описание» действительно доезжает до книги ------------------------------
if (xml.includes("Светлая квартира, окна во двор.")) pass("текст объявления попадает в книгу");
else fail("в книге нет текста объявления");

// --- блок «ОТДЕЛКА / РЕМОНТ» в Сводке сходится -------------------------------
const summary = sheets.find(([, n]) => n === "Сводка");
if (!summary) fail("нет листа «Сводка»");
else {
  const body = summary[2];
  const start = body.indexOf("ОТДЕЛКА / РЕМОНТ");
  const stop = body.indexOf("ДИАПАЗОН ЦЕН", start);
  if (start < 0) fail("в Сводке нет блока «ОТДЕЛКА / РЕМОНТ»");
  else {
    const chunk = body.slice(start, stop < 0 ? undefined : stop);
    const nums = [...chunk.matchAll(/<Data ss:Type="Number">(\d+)<\/Data>/g)].map((m) => +m[1]);
    const total = nums.reduce((a, b) => a + b, 0);
    if (total === rows.length) pass(`блок отделки сходится: ${nums.join(" + ")} = ${rows.length} лотов`);
    else fail(`блок отделки даёт ${total} при ${rows.length} лотах (лот с незнакомым значением поля выпал?)`);
    if (chunk.includes("smartFinish")) pass("незнакомое значение поля Циан видно в Сводке, а не теряется");
    else fail("незнакомое значение поля Циан пропало из Сводки");
  }
}

// --- XML валиден: ни управляющих символов, ни одиноких суррогатов ------------
const dirty = api.buildWorkbook(
  [api.normalize({ cianId: 9, roomsCount: 1, totalArea: "30", floorNumber: 1,
    bargainTerms: { price: 5000000 }, addedTimestamp: 1748400000,
    description: "Ремонт\u0001 свежий \ud83c дом \u{1F3E0} двор" })],
  {}, 1);
if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(dirty)) fail("в XML остались управляющие символы");
else if (/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/.test(dirty) || /(^|[^\uD800-\uDBFF])[\uDC00-\uDFFF]/.test(dirty)) {
  fail("в XML остался одинокий суррогат — Excel не откроет книгу");
} else pass("XML без управляющих символов и одиноких суррогатов, эмодзи цело");

console.log(failed ? `\nПРОВАЛЕНО: ${failed}` : "\nВсё зелено.");
process.exit(failed ? 1 : 0);
