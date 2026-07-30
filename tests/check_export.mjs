// Проверка книги, которую строит cian_browser.js (Способ 1 — вставка в консоль).
//   node tests/check_export.mjs
//
// Делает две вещи:
//   1) сверяет, что блок сборки .xlsx (между XLSX-BLOCK-START и XLSX-BLOCK-END)
//      совпадает ПОБУКВЕННО с копией в extension/content.js — это единственное,
//      что удерживает два экспортёра от расхождения;
//   2) строит книгу на фиксированных лотах и проверяет её геометрию: число
//      ячеек в строке против числа заголовков, блок отделки в «Сводке»,
//      целостность zip.

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { partsToTree, unzipParts } from "./xlsx_tree.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const START = "// ===== XLSX-BLOCK-START";
const END = "// ===== XLSX-BLOCK-END";

let failed = 0;
const fail = (m) => { console.error("  ✗ " + m); failed++; };
const pass = (m) => console.log("  ✓ " + m);

// --- 1. копии блока сборщика не разъехались ---------------------------------
function grabBlock(file) {
  const src = fs.readFileSync(path.join(ROOT, file), "utf8");
  const a = src.indexOf(START);
  const b = src.indexOf(END);
  if (a < 0 || b < 0) throw new Error(`${file}: маркеры XLSX-BLOCK не найдены`);
  return src.slice(src.lastIndexOf("\n", a) + 1, src.indexOf("\n", b) + 1);
}
const blocks = ["extension/content.js", "cian_browser.js"].map(grabBlock);
if (blocks[0] === blocks[1]) pass(`сборщик .xlsx идентичен в обоих экспортёрах (${blocks[0].length} байт)`);
else {
  const a = blocks[0].split("\n"), b = blocks[1].split("\n");
  const i = a.findIndex((l, k) => l !== b[k]);
  fail(`сборщик .xlsx разошёлся, первая разница в строке ${i + 1} блока:\n` +
    `      content.js:      ${JSON.stringify(a[i])}\n      cian_browser.js: ${JSON.stringify(b[i])}`);
}

// --- 2. книга ----------------------------------------------------------------
const src = fs.readFileSync(path.join(ROOT, "cian_browser.js"), "utf8");
const from = src.indexOf("  function categoryOf(");
const to = src.indexOf("  function download(");
if (from < 0 || to < 0) throw new Error("не нашёл границы экспортирующей части cian_browser.js");

const api = eval(`(() => {
  const JKNAME = "Тестовый ЖК", JKID = 123456;
${src.slice(from, to)}
  return { normalize, buildWorkbook, buildXlsxParts, buildXlsxBlob, HEADERS, COLW, FIN };
})()`);

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
const book = api.buildWorkbook(rows, { 1: 10, 2: 20, 9: 5 }, 40);
const tree = partsToTree(api.buildXlsxParts(book));

// геометрия: в листах данных каждая строка обязана иметь ровно HEADERS.length ячеек
let geomOk = true;
for (const sh of tree.sheets) {
  if (sh.name === "Сводка") continue;                    // у сводки своя геометрия
  if ((sh.colWidths || []).length !== api.HEADERS.length) {
    fail(`лист «${sh.name}»: ширин ${sh.colWidths.length} при ${api.HEADERS.length} заголовках`);
    geomOk = false;
  }
  sh.rows.slice(3).forEach((r, i) => {
    if (r.length !== api.HEADERS.length) {
      fail(`лист «${sh.name}», строка ${i + 4}: ${r.length} ячеек при ${api.HEADERS.length} заголовках`);
      geomOk = false;
    }
  });
}
if (geomOk) pass("во всех листах данных число ячеек совпадает с числом заголовков");

const flat = JSON.stringify(tree);
if (flat.includes("Светлая квартира, окна во двор.")) pass("текст объявления попадает в книгу");
else fail("в книге нет текста объявления");

// блок «ОТДЕЛКА / РЕМОНТ» в Сводке обязан сходиться с числом лотов
const summary = tree.sheets.find((s) => s.name === "Сводка");
if (!summary) fail("нет листа «Сводка»");
else {
  const flatRows = summary.rows.map((r) => r.map((c) => (c && c.v != null ? String(c.v) : "")));
  const start = flatRows.findIndex((r) => r[0] === "ОТДЕЛКА / РЕМОНТ");
  const stop = flatRows.findIndex((r, i) => i > start && String(r[0]).startsWith("ДИАПАЗОН"));
  if (start < 0) fail("в Сводке нет блока «ОТДЕЛКА / РЕМОНТ»");
  else {
    const chunk = summary.rows.slice(start, stop < 0 ? undefined : stop);
    const nums = chunk.map((r) => r[1]).filter((c) => c && c.t === "n").map((c) => c.v);
    const total = nums.reduce((a, b) => a + b, 0);
    if (total === rows.length) pass(`блок отделки сходится: ${nums.join(" + ")} = ${rows.length} лотов`);
    else fail(`блок отделки даёт ${total} при ${rows.length} лотах (лот с незнакомым значением поля выпал?)`);
    if (JSON.stringify(chunk).includes("smartFinish")) pass("незнакомое значение поля Циан видно в Сводке");
    else fail("незнакомое значение поля Циан пропало из Сводки");
  }
}

// --- 3. контейнер ------------------------------------------------------------
const dirtyBook = api.buildWorkbook(
  [api.normalize({ cianId: 9, roomsCount: 1, totalArea: "30", floorNumber: 1,
    bargainTerms: { price: 5000000 }, addedTimestamp: 1748400000,
    description: "Ремонт свежий \ud83c дом \u{1F3E0} двор" })],
  {}, 1);
const blob = await api.buildXlsxBlob(dirtyBook);
const buf = Buffer.from(await blob.arrayBuffer());
if (buf[0] !== 0x50 || buf[1] !== 0x4b) fail("файл не начинается с PK — перед zip что-то дописано (BOM?)");
else {
  const unzipped = unzipParts(buf);
  const xml = [...unzipped.values()].join("");
  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(xml)) fail("в XML остались управляющие символы");
  else if (/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/.test(xml) || /(^|[^\uD800-\uDBFF])[\uDC00-\uDFFF]/.test(xml)) {
    fail("в XML остался одинокий суррогат — Excel не откроет книгу");
  } else pass(`zip собран и распакован (${buf.length} байт, частей ${unzipped.size}), эмодзи цело`);
}

console.log(failed ? `\nПРОВАЛЕНО: ${failed}` : "\nВсё зелено.");
process.exit(failed ? 1 : 0);
