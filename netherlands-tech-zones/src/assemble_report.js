// Assemble the enriched report_data.json from the workflow output + fixes + images.
const fs = require("fs");
const P = "/tmp/claude-0/-home-user-test/75f8d986-3bb9-55fe-8e31-c773d42a8676";
const wf = JSON.parse(fs.readFileSync(P + "/tasks/wxq6ue96b.output", "utf8"));
const bySection = wf.result.bySection;
const inject = JSON.parse(fs.readFileSync("zone_inject.json", "utf8"));
const orig = JSON.parse(fs.readFileSync("report_data.orig.json", "utf8"));

const HEX = { orange: "EA580C", blue: "2563EB", green: "059669", purple: "7C3AED", gold: "D9A521" };

// ---------- 1. global fact / style fixes ----------
const FIXES = [
  ["Но с 1 сергодня, точнее с 1 сентября 2023 года, ограничения", "Но с 1 сентября 2023 года ограничения"],
  ["достигала около **$674 млрд**, а в июне впервые в истории европейского бизнеса ненадолго перевалила за **$700 млрд** — больше, чем когда-либо стоила любая другая компания континента. Для сравнения: это дороже всех автопроизводителей Германии вместе взятых.",
   "достигала около **$674 млрд** — больше, чем когда-либо стоила любая другая публичная компания континента."],
  ["входит в «большую четвёрку» дата-центровых столиц Европы — так называемый рынок **FLAP-D**",
   "входит в число крупнейших дата-центровых рынков Европы — так называемую группу **FLAP-D**"],
  ["более 900 сетей", "более 880 сетей"],
  ["более 12 000 исследователей", "более 12 500 исследователей"],
  ["Нидерланды — второй в мире экспортёр сельхозпродукции после США, хотя", "Нидерланды — один из крупнейших в мире экспортёров сельхозпродукции, хотя"],
  ["Для каждой площадки приведён спутниковый снимок — чтобы было видно, как это место выглядит на самом деле.",
   "Для каждой площадки приведена карта — чтобы было видно, где именно она находится и какой крупный город рядом."],
  ["молодые деп-тех команды", "молодые глубокотехнологичные (deep-tech) команды"],
  ["деп-тех", "глубокотехнологичные (deep-tech)"],
];
function fixStr(s) { if (typeof s !== "string") return s; for (const [a, b] of FIXES) s = s.split(a).join(b); return s; }
function fixBlock(b) {
  for (const k of ["text", "title", "summary"]) if (b[k]) b[k] = fixStr(b[k]);
  for (const k of ["items", "companies", "facts"]) if (Array.isArray(b[k])) b[k] = b[k].map(fixStr);
  if (Array.isArray(b.rows)) b.rows = b.rows.map((r) => r.map(fixStr));
  if (Array.isArray(b.stats)) b.stats = b.stats.map((s) => ({ ...s, label: fixStr(s.label), value: fixStr(s.value) }));
  return b;
}
for (const sid in bySection) bySection[sid] = bySection[sid].map(fixBlock);

// ---------- 2. targeted block edits (de-duplication) ----------
// economics para [6]: collapse the passage that duplicates the whole geopolitics section
{
  const e = bySection.economics;
  const idx = e.findIndex((b) => b.type === "para" && b.text && b.text.startsWith("Но защита — это не только деньги"));
  if (idx >= 0) e[idx].text = "Но защита — это не только деньги. Литография ASML стала оружием в **чиповой войне** США и Китая: правительство в Гааге может в любой момент не выдать или отозвать экспортную лицензию — и с 2019 года ни один **EUV-литограф** так и не был отгружён в Китай. Подробно об этом рычаге, ответных мерах Пекина и громком деле **Nexperia** рассказано в разделе «Геополитика».";
}
// ai: shorten the Python callout so it doesn't repeat the Guido/CWI/1989 detail already in the paragraph
{
  const a = bySection.ai;
  const c = a.find((b) => b.type === "callout" && b.title && b.title.includes("Python"));
  if (c) c.text = "**Python** — самый популярный язык для анализа данных и искусственного интеллекта: на нём написана огромная часть инструментов, которыми пользуются исследователи ИИ во всём мире. Его придумали в Амстердаме — и именно поэтому родина одного из главных инструментов ИИ находится в двух шагах от крупнейшего интернет-узла страны.";
}
// data: restore the cancelled-Meta story (explains the later national moratorium)
{
  const d = bySection.data;
  d.push({ type: "para", text: "Не все громкие проекты состоялись. В 2021 году компания **Meta** (владелец Facebook) получила разрешение на огромный дата-центр площадью 166 гектаров в **Зеевольде**, но в 2022 году парламент неожиданно проголосовал против — после волны общественного возмущения из-за расхода земли, воды и энергии. Именно эти споры (вместе со скандалом вокруг воды у Microsoft) подтолкнули государство к общенациональным ограничениям на большие ЦОД, о которых речь пойдёт в четвёртой части." });
}

// ---------- 3. normalize blocks for the docx renderer ----------
function norm(b) {
  const o = { type: b.type };
  if (b.text != null) o.text = b.text;
  if (b.title != null) o.title = b.title;
  if (b.items) o.items = b.items;
  if (b.headers) { o.headers = b.headers; o.rows = b.rows; o.widths = b.widths; }
  if (b.type === "image" || b.type === "fullimage") { o.file = b.file; o.widthPt = b.widthPt; o.maxH = b.maxH; o.caption = b.caption; }
  if (b.type === "callout") o.accent = HEX[b.accent] || HEX.orange;
  if (b.type === "bullets") o.color = HEX[b.color] || HEX.orange;
  if (b.type === "statstrip") o.items = (b.stats || b.items).map((s) => ({ value: s.value, label: s.label, color: HEX[s.color] || HEX.orange }));
  if (b.type === "zonecard") {
    const z = inject[b.zoneKey];
    Object.assign(o, { name: z.name, city: z.city, region: z.region, coords: z.coords, image: z.image, caption: z.caption, maxH: 360, summary: b.summary, companies: b.companies, facts: b.facts });
  }
  return o;
}

// table column widths (dxa, content ~9360)
function tableWidths(b) {
  const n = b.headers.length;
  if (n === 3) return [1500, 3400, 4460];
  if (n === 4) return [2100, 2000, 2300, 2960];
  return b.headers.map(() => Math.floor(9360 / n));
}

// image blocks
const IMG = {
  national: { type: "fullimage", file: "assets/national.jpg", widthPt: 360, maxH: 405, caption: "Карта ключевых технологических зон Нидерландов: производство чипов на юге (Эйндховен, Неймеген), дата-центры на севере и западе (Эмсхавен, Мидденмер, Амстердам), наука об ИИ и квантах в Делфте и Амстердаме." },
  wafer: { type: "fullimage", file: "assets/wafer.jpg", widthPt: 460, maxH: 280, caption: "Кремниевая пластина с готовыми микросхемами. Именно такие «вафли» печатают на литографах ASML, разрезают на отдельные чипы и ставят в наши устройства." },
  datacenter: { type: "fullimage", file: "assets/datacenter.jpg", widthPt: 460, maxH: 280, caption: "Внутри дата-центра: ряды стоек с серверами, которые круглые сутки хранят и обрабатывают данные половины Европы." },
  quantum: { type: "fullimage", file: "assets/quantum.jpg", widthPt: 400, maxH: 320, caption: "Квантовый процессор в криостате: кубиты держат при температуре ниже, чем в открытом космосе, чтобы защитить их от малейшего шума." },
  chart_asml: { type: "image", file: "assets/chart_asml.png", widthPt: 460, caption: "Выручка ASML быстро растёт на волне спроса на чипы и искусственный интеллект." },
  chart_ams: { type: "image", file: "assets/chart_amsterdam_mw.png", widthPt: 460, caption: "Мощность дата-центров измеряют в мегаваттах: Амстердам — один из крупнейших рынков Европы." },
  chart_inv: { type: "image", file: "assets/chart_investment.png", widthPt: 460, caption: "Только три северные площадки притянули более €3,5 млрд инвестиций гиперскейлеров." },
};

// insert image block right after the first block matching pred (returns new array)
function insertAfter(blocks, pred, imgKey) {
  const out = []; let done = false;
  for (const b of blocks) { out.push(b); if (!done && pred(b)) { out.push(IMG[imgKey]); done = true; } }
  return out;
}
function insertBefore(blocks, pred, imgKey) {
  const out = []; let done = false;
  for (const b of blocks) { if (!done && pred(b)) { out.push(IMG[imgKey]); done = true; } out.push(b); }
  return out;
}

// ---------- 4. per-section image insertion ----------
const isZC = (k) => (b) => b.type === "zonecard" && b.zoneKey === k;
// intro: national map before the final wrap-up paragraph (which now mentions "карта")
bySection.intro = insertBefore(bySection.intro, (b) => b.type === "para" && b.text && b.text.startsWith("Сложилась редкая ситуация"), "national");
// chips: wafer before first zonecard; chart after ASML card
bySection.chips = insertBefore(bySection.chips, (b) => b.type === "zonecard", "wafer");
bySection.chips = insertAfter(bySection.chips, isZC("asml"), "chart_asml");
// data: datacenter before first zonecard; charts after amsterdam & agriport cards
bySection.data = insertBefore(bySection.data, (b) => b.type === "zonecard", "datacenter");
bySection.data = insertAfter(bySection.data, isZC("amsterdam"), "chart_ams");
bySection.data = insertAfter(bySection.data, isZC("agriport"), "chart_inv");
// ai: quantum photo before the Delft zonecard
bySection.ai = insertBefore(bySection.ai, isZC("delft"), "quantum");

// ---------- 5. split economics into Part IV and Conclusion ----------
const econ = bySection.economics;
const splitAt = econ.findIndex((b, i) => i > 0 && b.type === "h1"); // second h1 = "Заключение"
const partIV = econ.slice(0, splitAt);
const conclusion = econ.slice(splitAt);

// reuse original compare (h1+para+table) and sources (h1+para+sources)
const compare = orig.blocks.slice(54, 57);
const sources = orig.blocks.slice(61, 64);

// ---------- 6. assemble final order ----------
let blocks = [];
const add = (arr) => { blocks.push(...arr); };
add(bySection.intro);
add(bySection.history);
add(bySection.chips);
add(bySection.supplychain);
add(bySection.data);
add(bySection.ai);
add(bySection.geopolitics);
add(bySection.outlook);
add(partIV);
add(bySection.didyouknow);
add(compare);
add(conclusion);
add(sources);

// normalize + attach table widths
blocks = blocks.map((b) => {
  const o = norm(b);
  if (o.type === "table") o.widths = tableWidths(o);
  return o;
});

// ---------- 7. build TOC (level1 = h1, level2 = zone names); pages filled after first render ----------
const toc = [];
for (const b of blocks) {
  if (b.type === "h1") toc.push({ level: 1, title: b.text, page: 0 });
  else if (b.type === "zonecard") toc.push({ level: 2, title: b.name, page: 0 });
}

const out = { meta: orig.meta, tocStatic: toc, blocks };
fs.writeFileSync("report_data.json", JSON.stringify(out, null, 1));
console.log("WROTE report_data.json:", blocks.length, "blocks,", toc.length, "TOC entries");
