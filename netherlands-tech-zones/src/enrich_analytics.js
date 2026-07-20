// Insert analytics (SWOT, risks, ratios, scenarios, takeaways, stronger conclusion) into report_data.json
const fs = require("fs");
const P = "/tmp/claude-0/-home-user-test/75f8d986-3bb9-55fe-8e31-c773d42a8676";
const wf = JSON.parse(fs.readFileSync(P + "/tasks/wi4fpzefs.output", "utf8"));
const A = wf.result.bySection;
const D = JSON.parse(fs.readFileSync("report_data.enriched.json", "utf8"));

// ---- editor fixes (fact + artifact) ----
const FIX = [
  ["технологического суверенитета技技", "технологического суверенитета"], // safety
  ["стать技技 центром", "стать центром"],
  ["技技", ""],
  ["~100% (и ~90% всей литографии)", "~100% рынка EUV"],
  [" (и ~90% всей литографии)", ""],
  ["приходится добровольно резать", "приходится резать"],
];
function fixStr(s) { if (typeof s !== "string") return s; for (const [a, b] of FIX) s = s.split(a).join(b); return s; }
function fixDeep(b) {
  for (const k of ["text", "title"]) if (b[k]) b[k] = fixStr(b[k]);
  if (Array.isArray(b.items)) b.items = b.items.map(fixStr);
  if (Array.isArray(b.rows)) b.rows = b.rows.map((r) => r.map(fixStr));
  if (Array.isArray(b.stats)) b.stats = b.stats.map((s) => ({ ...s, label: fixStr(s.label), value: fixStr(s.value) }));
  if (b.type === "callout" && b.title && b.title.includes("склоняется") && !b.accent) b.accent = "blue";
  return b;
}
for (const sid in A) A[sid] = A[sid].map(fixDeep);

const HEX = { orange: "EA580C", blue: "2563EB", green: "059669", purple: "7C3AED", gold: "D9A521" };
function tableWidths(headers) {
  const n = headers.length;
  if (n === 4) return [2600, 1700, 2560, 2500];
  if (n === 3) return [2600, 1700, 5060];
  return headers.map(() => Math.floor(9360 / n));
}
// normalize a workflow block (accent/color hints -> hex, stats -> items, table widths)
function norm(b) {
  const o = { type: b.type };
  if (b.text != null) o.text = b.text;
  if (b.title != null) o.title = b.title;
  if (b.items) o.items = b.items;
  if (b.headers) { o.headers = b.headers; o.rows = b.rows; o.widths = tableWidths(b.headers); }
  if (b.type === "callout") o.accent = HEX[b.accent] || HEX.orange;
  if (b.type === "bullets") o.color = HEX[b.color] || HEX.orange;
  if (b.type === "statstrip") o.items = (b.stats || b.items).map((s) => ({ value: s.value, label: s.label, color: HEX[s.color] || HEX.orange }));
  return o;
}
const normArr = (arr) => arr.map(norm);

// ---- takeaways: exactly 4 callouts in order [chips, data, ai, geopolitics] ----
const tk = (A.takeaways || []).filter((b) => b.type === "callout").map(norm);
const takeawayBefore = {                 // insert callout BEFORE the h1 that starts each string
  "Место в мировой цепочке": tk[0],      // end of chips
  "Часть III": tk[1],                    // end of data-centers
  "Геополитика": tk[2],                  // end of AI
  "Что дальше": tk[3],                   // end of geopolitics
};

// ---- analytics section ----
const analytics = [
  { type: "h1", text: "Аналитика: сильные стороны, риски и сценарии" },
  { type: "para", text: "До сих пор мы описывали, что и где происходит. Теперь посмотрим на всю картину глазами аналитика: в чём Нидерланды по-настоящему сильны, где их слабые места, какие риски способны всё пошатнуть и как страна может выглядеть к 2030 году. Это не новые факты, а выводы из уже сказанного." },
  { type: "h2", text: "Сильные и слабые стороны, возможности и угрозы (SWOT)" },
  ...normArr(A.swot || []),
  { type: "h2", text: "Карта рисков: где тонко" },
  ...normArr(A.risk || []),
  { type: "h2", text: "Аналитика в цифрах: что говорят соотношения" },
  ...normArr(A.quant || []),
  { type: "h2", text: "Три сценария до 2030 года" },
  ...normArr(A.scenario || []),
];

// ---- new conclusion (replaces the old "Заключение" section) ----
const cc = normArr(A.conclusion || []);
let concTitle = "Заключение: главный вывод";
const firstH2 = cc.find((b) => b.type === "h2");
if (firstH2) concTitle = firstH2.text.match(/заключ/i) ? firstH2.text : ("Заключение: " + firstH2.text.replace(/^[^:]*:\s*/, ""));
const newConclusion = [{ type: "h1", text: concTitle }, ...cc.filter((b) => b.type !== "h2")];

// ---- rebuild block list with injections ----
const out = [];
let i = 0;
const B = D.blocks;
while (i < B.length) {
  const b = B[i];
  if (b.type === "h1") {
    const key = Object.keys(takeawayBefore).find((k) => b.text.startsWith(k));
    if (key && takeawayBefore[key]) out.push(takeawayBefore[key]);
    if (b.text.startsWith("Заключение")) {
      out.push(...analytics);           // analytics section goes just before the conclusion
      out.push(...newConclusion);        // replace old conclusion
      i++;                               // skip old conclusion blocks up to "Источники"
      while (i < B.length && !(B[i].type === "h1" && B[i].text.startsWith("Источники"))) i++;
      continue;
    }
  }
  out.push(b);
  i++;
}
D.blocks = out;

// ---- rebuild TOC (pages filled later by toc_pages.py) ----
D.tocStatic = [];
for (const b of out) {
  if (b.type === "h1") D.tocStatic.push({ level: 1, title: b.text, page: 0 });
  else if (b.type === "zonecard") D.tocStatic.push({ level: 2, title: b.name, page: 0 });
}

fs.writeFileSync("report_data.json", JSON.stringify(D, null, 1));
console.log("WROTE report_data.json:", out.length, "blocks,", D.tocStatic.length, "TOC entries");
console.log("takeaways:", tk.length, "| swot:", (A.swot||[]).length, "| risk:", (A.risk||[]).length, "| quant:", (A.quant||[]).length, "| scenario:", (A.scenario||[]).length, "| conclusion:", cc.length);
