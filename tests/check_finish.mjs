// Проверка классификатора отделки в JS-экспортёрах.
//   node tests/check_finish.mjs
//
// Делает две вещи:
//   1) сверяет, что FIN-блок в extension/content.js и в cian_browser.js
//      совпадает ПОБУКВЕННО — это единственное, что удерживает три копии
//      правил от расхождения;
//   2) прогоняет общий корпус tests/finish_corpus.json.
//
// Тот же корпус прогоняет `python3 cian_scraper.py --self-test`. Если оба
// прогона зелёные, три экспортёра классифицируют одинаково.

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const START = "// ===== FIN-BLOCK-START";
const END = "// ===== FIN-BLOCK-END";

function extractBlock(file) {
  const src = fs.readFileSync(path.join(ROOT, file), "utf8");
  const a = src.indexOf(START);
  const b = src.indexOf(END);
  if (a < 0 || b < 0) throw new Error(`${file}: маркеры FIN-блока не найдены`);
  const lineStart = src.lastIndexOf("\n", a) + 1;   // вместе с отступом строки
  return src.slice(lineStart, src.indexOf("\n", b) + 1);
}

const files = ["extension/content.js", "cian_browser.js"];
const blocks = files.map(extractBlock);

let failed = 0;
const fail = (msg) => { console.error("  ✗ " + msg); failed++; };
const pass = (msg) => console.log("  ✓ " + msg);

// --- 1. копии блока не разъехались ------------------------------------------
if (blocks[0] === blocks[1]) {
  pass(`FIN-блок идентичен в ${files.join(" и ")} (${blocks[0].length} байт)`);
} else {
  const a = blocks[0].split("\n"), b = blocks[1].split("\n");
  const i = a.findIndex((l, k) => l !== b[k]);
  fail(`FIN-блок разошёлся между ${files[0]} и ${files[1]}, первая разница в строке ${i + 1} блока:\n` +
    `      ${files[0]}: ${JSON.stringify(a[i])}\n      ${files[1]}: ${JSON.stringify(b[i])}`);
}

// --- 2. корпус ---------------------------------------------------------------
const api = eval(`(() => {\n${blocks[0]}\n  return { finishFromText, finishOf, descriptionOf, clipDesc, finNorm, FIN, FIELD_FIN };\n})()`);
const corpus = JSON.parse(fs.readFileSync(path.join(ROOT, "tests/finish_corpus.json"), "utf8"));

let ok = 0;
const got = [];
for (const [text, expected] of corpus) {
  const actual = api.finishFromText(text);
  got.push(actual);
  if (actual === expected) ok++;
  else fail(`«${text}»\n      ожидалось: ${expected}\n      получено:  ${actual}`);
}
if (ok === corpus.length) pass(`корпус: ${ok}/${corpus.length}`);
else console.error(`  корпус: ${ok}/${corpus.length}`);

// вектор ответов — его же печатает --self-test, удобно сравнивать глазами
fs.writeFileSync(path.join(ROOT, "tests/.finish_vector_js.json"),
  JSON.stringify(got, null, 0) + "\n");

// --- 3. приоритет слоёв и крайние случаи ------------------------------------
const cases = [
  ["поле Циан важнее текста",
    api.finishOf({ decoration: "preFine", description: "Сделан евроремонт" }),
    { fin: "Предчистовая (white box)", src: "Циан-поле" }],
  ["категория выводится из текста",
    api.finishOf({ description: "Сделан авторский ремонт по дизайн-проекту" }),
    { fin: "Дизайнерский", src: "из описания" }],
  ["decoration=fineWithFurniture",
    api.finishOf({ decoration: "fineWithFurniture" }),
    { fin: "Под ключ / с мебелью", src: "Циан-поле" }],
  ["repairType=no",
    api.finishOf({ repairType: "no" }),
    { fin: "Без ремонта", src: "Циан-поле" }],
  ["неизвестное значение поля отдаётся как есть",
    api.finishOf({ decoration: "superLux" }),
    { fin: "superLux", src: "Циан-поле" }],
  ["пустой оффер",
    api.finishOf({}),
    { fin: null, src: "" }],
  ["описание пустое -> отделка не выдумывается",
    api.finishOf({ description: "Продаётся квартира в новом доме." }),
    { fin: null, src: "" }],
];
for (const [name, actual, expected] of cases) {
  if (JSON.stringify(actual) === JSON.stringify(expected)) pass(name);
  else fail(`${name}: ожидалось ${JSON.stringify(expected)}, получено ${JSON.stringify(actual)}`);
}

// Классификация обязана видеть ПОЛНЫЙ текст: признак отделки часто стоит в конце
// объявления, и на обрезанном тексте категория теряется или переворачивается.
const longAd = "Отличная квартира в тихом центре города. ".repeat(30) + "Требуется ремонт.";
const full = api.descriptionOf({ description: longAd });
if (full.length > 600) pass(`descriptionOf отдаёт текст целиком (${full.length} симв.), обрезка — дело clipDesc`);
else fail(`descriptionOf обрезал текст до ${full.length} симв.`);
if (api.finishOf({ description: longAd }).fin === "Без ремонта") {
  pass("категория определяется по полному тексту (признак в конце объявления)");
} else {
  fail(`на длинном объявлении категория потерялась: ${api.finishOf({ description: longAd }).fin}`);
}

// Обрезка для ячейки не должна разрубать суррогатную пару: одинокий суррогат
// делает XML невалидным, и Excel отказывается открыть книгу целиком.
const longEmoji = "\u0430".repeat(599) + "\u{1F3E0}" + " хвост";
const cut = api.clipDesc(api.descriptionOf({ description: longEmoji }));
if (/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/.test(cut) || /(^|[^\uD800-\uDBFF])[\uDC00-\uDFFF]/.test(cut)) {
  fail("clipDesc оставил одинокий суррогат");
} else if (Array.from(cut).length !== 601) {
  fail(`clipDesc дал ${Array.from(cut).length} симв. вместо 601 (600 + многоточие)`);
} else pass("clipDesc режет по кодовым точкам, суррогатная пара цела");

const cleaned = api.descriptionOf({ description: "Продам&nbsp;<b>2к</b> квартиру&mdash;срочно&#33;" });
if (cleaned === "Продам 2к квартиру\u2014срочно!") {
  pass("описание: сняты теги, раскрыты сущности, вычищены NBSP и управляющие символы");
} else fail("очистка описания: " + JSON.stringify(cleaned));

// Эмодзи — ДВА code unit в JS и один символ в Python, а окна {0,n} в стоп-контекстах
// считают единицы движка. Без выпиливания один и тот же текст дал бы разные
// категории в расширении и в Python-скрипте.
if (!/[\uD800-\uDFFF]/.test(api.finNorm("Ремонт \u{1F3E0} свежий"))) {
  pass("finNorm выпиливает эмодзи (иначе JS и Python разъезжаются)");
} else fail("finNorm оставил суррогаты — окна стоп-контекстов разойдутся с Python");

// U+0085 пробельный в Python и НЕ пробельный в JS: нормализация не должна
// опираться на \s.
if (api.finNorm("ремонт\u0085подъезда") === "ремонт подъезда") {
  pass("finNorm схлопывает пробелы явным классом, а не \\s");
} else fail("finNorm на U+0085: " + JSON.stringify(api.finNorm("ремонт\u0085подъезда")));

console.log(failed ? `\nПРОВАЛЕНО: ${failed}` : "\nВсё зелено.");
process.exit(failed ? 1 : 0);
