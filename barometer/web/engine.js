/* =========================================================================
   Барометр мобилизации — автономный браузерный движок (без сервера).

   Один файл делает всё, что раньше делал Python:
     • анализ новостей по лексикону (правила, RU/EN, полярность+вес);
     • расчёт барометра 0–100 (те же категории, веса и сигмоид);
     • прогноз даты по скорости роста (история в localStorage);
     • живой сбор: DeepState напрямую, RSS/Telegram через CORS-прокси;
     • рендер дашборда.

   Конфигурация (CONFIG) и стартовые данные (SEED) внедряются при сборке
   из config.py — поэтому логика гарантированно совпадает с серверной версией.
   ========================================================================= */
"use strict";

const CONFIG = (typeof window !== "undefined" ? window.CONFIG : global.CONFIG) || {};
const SEED = (typeof window !== "undefined" ? window.SEED : global.SEED) || [];
const BUILD_AT = (typeof window !== "undefined" ? window.BUILD_AT : global.BUILD_AT) || "";

const ZONES = [
  { max: 25,  hex: "#2ec78a", label: "Низкий риск" },
  { max: 45,  hex: "#8bd34f", label: "Умеренный риск" },
  { max: 70,  hex: "#f2c14e", label: "Повышенный риск" },
  { max: 92,  hex: "#f08a3c", label: "Высокий риск" },
  { max: 101, hex: "#e8514a", label: "Мобилизация объявлена" },
];
const STREAM_META = {
  media:     { icon: "📰", name: "Независимые СМИ" },
  deepstate: { icon: "🗺️", name: "DeepState · фронт" },
  analysts:  { icon: "🛰️", name: "Аналитики и соцсети" },
  raids:     { icon: "🚨", name: "Облавы и бусификация" },
};

/* ----------------------------- утилиты ---------------------------------- */
function zoneFor(v) { for (const z of ZONES) if (v < z.max) return z; return ZONES[ZONES.length - 1]; }
function esc(s) { return (s || "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c])); }
function nowStr() { return new Date().toISOString().slice(0, 19).replace("T", " "); }
function parseDate(s) { if (!s) return nowStr(); const d = new Date(s); return isNaN(d) ? nowStr() : d.toISOString().slice(0, 19).replace("T", " "); }
function dtMs(s) { if (!s) return Date.now(); const d = new Date(s.replace(" ", "T") + "Z"); return isNaN(d) ? Date.now() : d.getTime(); }
function normalize(s) { return (s || "").toLowerCase().replace(/ё/g, "е").replace(/ /g, " ").replace(/\s+/g, " ").trim(); }
function stripHtml(s) { return normalizeWs((s || "").replace(/<[^>]+>/g, " ")); }
function normalizeWs(s) { const t = document_unescape(s); return t.replace(/\s+/g, " ").trim().slice(0, 600); }
function document_unescape(s) {
  // Минимальная распаковка HTML-сущностей без DOM (для node-тестов и браузера).
  return (s || "")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&nbsp;/g, " ")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(+n))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)));
}

/* предкомпиляция лексикона/подсказок */
const LEX = (CONFIG.lexicon || []).map((e) => ({ cat: e[0], pol: e[1], w: e[2], term: e[3], norm: normalize(e[3]) }));
const HINTS = (CONFIG.relevanceHints || []).map(normalize);

/* ----------------------------- анализ ----------------------------------- */
function analyzeItem(it) {
  const text = normalize((it.title || "") + " " + (it.summary || ""));
  const signals = [];
  for (const e of LEX) if (e.norm && text.includes(e.norm)) signals.push({ category: e.cat, polarity: e.pol, weight: e.w, term: e.term });
  it.signals = signals;
  it.relevant = (signals.length > 0 || HINTS.some((h) => text.includes(h))) ? 1 : 0;
  return it;
}

/* --------------------------- DeepState ---------------------------------- */
const EARTH_R = 6371.0088;
function ringArea(coords) {
  const n = coords.length; if (n < 3) return 0;
  let s = 0;
  for (let i = 0; i < n; i++) {
    const a = coords[i], b = coords[(i + 1) % n];
    s += (b[0] - a[0]) * Math.PI / 180 * (2 + Math.sin(a[1] * Math.PI / 180) + Math.sin(b[1] * Math.PI / 180));
  }
  return Math.abs(s) * EARTH_R * EARTH_R / 2;
}
function polyArea(rings) { if (!rings || !rings.length) return 0; let a = ringArea(rings[0]); for (let i = 1; i < rings.length; i++) a -= ringArea(rings[i]); return Math.max(a, 0); }
function geomArea(g) {
  if (!g) return 0;
  if (g.type === "Polygon") return polyArea(g.coordinates);
  if (g.type === "MultiPolygon") return g.coordinates.reduce((s, p) => s + polyArea(p), 0);
  return 0;
}
function statusOf(props) { const m = /geoJSON\.status\.([a-zA-Z_]+)/.exec((props && props.name) || ""); return m ? m[1] : ""; }
function deepstateFromGeoJSON(data) {
  const feats = (data.map || data).features || [];
  let occ = 0, unk = 0, occN = 0;
  for (const f of feats) {
    const t = f.geometry && f.geometry.type;
    if (t !== "Polygon" && t !== "MultiPolygon") continue;
    const st = statusOf(f.properties);
    if (st === "occupied") { occ += geomArea(f.geometry); occN++; }
    else if (st === "unknown") { unk += geomArea(f.geometry); }
  }
  return { status: "ok", occupied_km2: Math.round(occ * 10) / 10, unknown_km2: Math.round(unk * 10) / 10,
           occupied_polys: occN, taken_at: data.datetime ? parseDate(data.datetime) : nowStr() };
}
function deepstateNorm(snap) {
  const info = { available: false, delta_km2: null, occupied_km2: null, trend: "нет данных" };
  if (!snap || snap.status !== "ok") return { norm: 0, info };
  info.available = true; info.occupied_km2 = snap.occupied_km2;
  const delta = snap.delta_km2;
  if (delta == null) { info.trend = "первый замер"; return { norm: 0, info }; }
  info.delta_km2 = delta;
  const raw = -delta / (CONFIG.params.deepstateScale || 120);
  const norm = raw / (Math.abs(raw) + 1);
  info.trend = delta > 5 ? `продвижение РФ +${Math.round(delta)} км²`
             : delta < -5 ? `отступление РФ ${Math.round(delta)} км²` : "фронт стабилен";
  return { norm, info };
}

/* ----------------------------- скоринг ---------------------------------- */
function sigmoid(z) { if (z < -60) return 0; if (z > 60) return 1; return 1 / (1 + Math.exp(-z)); }

function computeReading(items, deepstateSnap, history, sourcesOkRatio, llm) {
  const P = CONFIG.params, CATS = CONFIG.categories, SL = CONFIG.streamLabels;
  const now = Date.now();
  const windowStart = now - P.windowDays * 86400000;
  const catSignal = {}; for (const c in CATS) catSignal[c] = 0;
  const drivers = {};
  const streamRel = {}, streamSig = {}; for (const s in SL) { streamRel[s] = 0; streamSig[s] = 0; }
  let nRel = 0;

  for (const it of items) {
    if (!it.relevant) continue;
    const pub = dtMs(it.published);
    if (pub < windowStart) continue;
    nRel++;
    const stream = it.stream || "media";
    if (stream in streamRel) streamRel[stream]++;
    const age = Math.max(0, (now - pub) / 86400000);
    const decay = Math.pow(0.5, age / P.halflife);
    const sw = +it.source_weight || 1;
    for (const sig of it.signals || []) {
      const cat = sig.category; if (!(cat in catSignal)) continue;
      const contrib = sig.polarity * sig.weight * decay * sw;
      catSignal[cat] += contrib;
      if (stream in streamSig) streamSig[stream] += contrib;
      const key = cat + "|" + sig.term + "|" + (sig.polarity >= 0 ? 1 : -1);
      let d = drivers[key];
      if (!d) { d = drivers[key] = { category_label: CATS[cat].label, term: sig.term, polarity: sig.polarity, abs: 0, count: 0, best: -1, example: null }; }
      d.abs += Math.abs(contrib); d.count++;
      const single = sig.weight * decay * sw;
      if (single > d.best) { d.best = single; d.example = { title: it.title, url: it.url, source: it.source_name, published: it.published }; }
    }
  }

  const ds = deepstateNorm(deepstateSnap);
  const norm = {};
  for (const c in CATS) {
    if (c === "deepstate") norm[c] = ds.norm;
    else { const s = catSignal[c]; norm[c] = s / (Math.abs(s) + CATS[c].scale); }
  }
  let composite = 0; for (const c in CATS) composite += CATS[c].weight * norm[c];
  const barometer = Math.round(1000 * sigmoid(P.K * composite + P.B)) / 10;

  // Смешиваем с оценкой ИИ (Gemini), если она есть: 65% правила + 35% ИИ.
  let llmScore = null, final = barometer;
  if (llm && typeof llm.score === "number") { llmScore = llm.score; final = Math.round((0.65 * barometer + 0.35 * llmScore) * 10) / 10; }

  const categories = Object.keys(CATS).map((c) => ({
    key: c, label: CATS[c].label, weight: CATS[c].weight,
    norm: Math.round(norm[c] * 1000) / 1000, signed: Math.round(norm[c] * 1000) / 10,
    contribution: Math.round(CATS[c].weight * norm[c] * 1000) / 1000,
  }));
  const streams = Object.keys(SL).map((s) => ({ key: s, label: SL[s], relevant: streamRel[s], signal: Math.round(streamSig[s] * 100) / 100 }));

  const dl = Object.values(drivers).sort((a, b) => b.abs - a.abs).slice(0, 8);
  const maxAbs = dl.length ? Math.max(...dl.map((d) => d.abs)) : 1;
  const driversOut = dl.map((d) => ({ category_label: d.category_label, term: d.term, polarity: d.polarity, count: d.count, strength: Math.round(1000 * d.abs / maxAbs) / 10, example: d.example }));

  const velocity = computeVelocity(history, now, final);
  const forecast = computeForecast(final, velocity);
  const volConf = Math.min(1, nRel / 60), histConf = Math.min(1, history.length / 14);
  const confidence = Math.round((0.5 * volConf + 0.3 * histConf + 0.2 * (sourcesOkRatio == null ? 1 : sourcesOkRatio)) * 100) / 100;

  return {
    taken_at: nowStr(), barometer, final_barometer: final, llm_barometer: llmScore, llm: llm || null,
    velocity: velocity == null ? null : Math.round(velocity * 1000) / 1000,
    predicted_date: forecast.date, confidence, zone: zoneFor(final).label,
    components: { categories, composite: Math.round(composite * 1000) / 1000, deepstate: ds.info, relevant_items: nRel },
    streams, drivers: driversOut, forecast,
  };
}

function computeVelocity(history, now, current) {
  const cutoff = now - 21 * 86400000;
  const pts = [];
  for (const h of history) { const t = dtMs(h.t); if (t >= cutoff && h.v != null) pts.push([(t - cutoff) / 86400000, +h.v]); }
  pts.push([(now - cutoff) / 86400000, current]);
  if (pts.length < 2) return null;
  const xs = pts.map((p) => p[0]); const span = Math.max(...xs) - Math.min(...xs);
  if (span < 0.5) return null;
  const n = pts.length, sx = sum(xs), sy = sum(pts.map((p) => p[1]));
  const sxx = sum(pts.map((p) => p[0] * p[0])), sxy = sum(pts.map((p) => p[0] * p[1]));
  const denom = n * sxx - sx * sx; if (Math.abs(denom) < 1e-9) return null;
  return (n * sxy - sx * sy) / denom;
}
function sum(a) { return a.reduce((s, x) => s + x, 0); }

function computeForecast(current, velocity) {
  const P = CONFIG.params; const today = new Date().toISOString().slice(0, 10);
  if (current >= P.announced) return { label: "Мобилизация объявлена / идёт", date: today, days: 0, basis: "барометр в красной зоне" };
  if (velocity == null) return { label: "Накопление данных — прогноз появится после нескольких замеров", date: null, days: null, basis: "недостаточно истории" };
  if (velocity <= 0.15) return { label: "В обозримом будущем не прогнозируется", date: null, days: null, basis: `барометр не растёт (${velocity >= 0 ? "+" : ""}${velocity.toFixed(2)}/день)` };
  const days = (P.forecast - current) / velocity;
  if (days <= 0) return { label: "Риск максимальный — возможно в любой момент", date: today, days: 0, basis: "экстраполяция тренда" };
  if (days > 730) return { label: "Более 2 лет — фактически не прогнозируется", date: null, days: Math.round(days), basis: `очень медленный рост (+${velocity.toFixed(2)}/день)` };
  const d = new Date(Date.now() + Math.round(days) * 86400000).toISOString().slice(0, 10);
  return { label: `Ориентировочно через ~${Math.round(days)} дн.`, date: d, days: Math.round(days), basis: `рост +${velocity.toFixed(2)} пункта/день до порога ${P.forecast}` };
}

/* ===================== ниже — только браузерная часть ==================== */
/* (сеть, localStorage, DOM-рендер; в node-тестах не вызывается)            */

/* ----------------------------- сеть ------------------------------------- */
const PROXY = {
  raw: (u) => "https://api.allorigins.win/raw?url=" + encodeURIComponent(u),
  get: (u) => "https://api.allorigins.win/get?url=" + encodeURIComponent(u),
  cors: (u) => "https://corsproxy.io/?url=" + encodeURIComponent(u),
};
async function withTimeout(p, ms) {
  const ctrl = new AbortController(); const id = setTimeout(() => ctrl.abort(), ms);
  try { return await p(ctrl.signal); } finally { clearTimeout(id); }
}
async function getText(url) {
  return withTimeout(async (signal) => { const r = await fetch(url, { signal }); if (!r.ok) throw new Error("HTTP " + r.status); return r.text(); }, 12000);
}
function b64utf8(b64) { const bin = atob(b64); const a = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) a[i] = bin.charCodeAt(i); return new TextDecoder("utf-8").decode(a); }
async function proxiedText(url) {
  try { return await getText(PROXY.raw(url)); } catch (e) {}
  try {
    const j = JSON.parse(await getText(PROXY.get(url)));
    let c = j.contents || "";
    if (c.startsWith("data:") && c.includes(";base64,")) c = b64utf8(c.split(";base64,")[1]);
    if (c) return c;
  } catch (e) {}
  try { return await getText(PROXY.cors(url)); } catch (e) {}
  throw new Error("all proxies failed for " + url);
}

/* ------------------- ИИ-аналитика: бесплатные провайдеры ---------------- */
// Все, кроме Gemini, — OpenAI-совместимые (один и тот же формат запроса).
// CORS у всех разрешён, поэтому работают прямо из браузера.
const AI_PROVIDERS = {
  openrouter: { label: "OpenRouter (бесплатные модели)", base: "https://openrouter.ai/api/v1", model: "meta-llama/llama-3.3-70b-instruct:free", signup: "https://openrouter.ai/keys", kind: "openai", hint: "ключ sk-or-…" },
  groq:       { label: "Groq (быстро, бесплатно)",       base: "https://api.groq.com/openai/v1", model: "llama-3.3-70b-versatile", signup: "https://console.groq.com/keys", kind: "openai", hint: "ключ gsk_…" },
  mistral:    { label: "Mistral (бесплатно)",            base: "https://api.mistral.ai/v1", model: "mistral-small-latest", signup: "https://console.mistral.ai/api-keys", kind: "openai", hint: "ключ Mistral" },
  gemini:     { label: "Google Gemini",                  base: "", model: "gemini-2.5-flash", signup: "https://aistudio.google.com/apikey", kind: "gemini", hint: "ключ AIza…" },
};
const DEFAULT_PROVIDER = "openrouter";
// Запасные бесплатные модели: при 429/перегрузке пробуем следующую.
const AI_FALLBACKS = {
  openrouter: ["meta-llama/llama-3.3-70b-instruct:free", "openai/gpt-oss-120b:free", "qwen/qwen3-next-80b-a3b-instruct:free", "openai/gpt-oss-20b:free"],
  groq: ["llama-3.3-70b-versatile", "llama-3.1-8b-instant"],
  mistral: ["mistral-small-latest"],
};
const AI_PROMPT =
  "Ты — аналитик OSINT. По подборке заголовков новостей из независимых российских СМИ, " +
  "Telegram и западных военных аналитиков оцени вероятность объявления НОВОЙ волны " +
  "мобилизации в России в ближайшие месяцы.\n" +
  "Верни СТРОГО JSON и больше ничего: {\"score\": <целое 0-100, 0 — мобилизации не будет, 100 — объявлена/идёт>, " +
  "\"expected_window\": \"<срок, напр. '1–3 месяца' или 'не просматривается'>\", " +
  "\"rationale\": \"<2-3 предложения по-русски: ключевые сигналы за и против>\"}.\n\nНовости:\n";

function aiProvider() { return lsGet("baro_ai_provider", DEFAULT_PROVIDER); }
function aiKey() { return (lsGet("baro_ai_key", "") || "").trim(); }
function aiModel() { const p = AI_PROVIDERS[aiProvider()] || {}; return (lsGet("baro_ai_model", "") || "").trim() || p.model; }
function aiOn() { return !!aiKey() && lsGet("baro_ai_on", true); }

function _aiLines(items) { return items.map((it) => `- [${(it.published || "").slice(0, 10)}] (${it.source_name}) ${it.title}`).join("\n"); }
function _parseScore(text) {
  let o = null;
  try { o = JSON.parse(text); } catch (e) { const m = /\{[\s\S]*\}/.exec(text || ""); if (m) try { o = JSON.parse(m[0]); } catch (e2) {} }
  if (!o || typeof o.score === "undefined") return null;
  return {
    score: Math.max(0, Math.min(100, Math.round(+o.score))),
    expected_window: String(o.expected_window || "").slice(0, 120),
    rationale: String(o.rationale || "").slice(0, 600),
  };
}

async function aiAnalyze(items) {
  const key = aiKey(); if (!key) return null;
  const prov = aiProvider();
  const pcfg = AI_PROVIDERS[prov]; if (!pcfg) return null;
  const picked = items.filter((i) => i.relevant).slice(0, 40); if (!picked.length) return null;
  const prompt = AI_PROMPT + _aiLines(picked);

  if (pcfg.kind === "gemini") {
    const res = await _geminiCall(key, aiModel(), prompt);
    return res ? { ...res, model: aiModel(), provider: prov, at: nowStr() } : null;
  }
  // OpenAI-совместимые: перебираем выбранную модель + запасные при 429/перегрузке.
  const chosen = aiModel();
  const cands = [chosen, ...((AI_FALLBACKS[prov] || []).filter((m) => m !== chosen))];
  let lastErr = null;
  for (const m of cands) {
    try {
      const res = await _openaiCall(pcfg.base, key, m, prompt);
      if (res) return { ...res, model: m, provider: prov, at: nowStr() };
    } catch (e) {
      lastErr = e;
      if (e.status === 401 || e.status === 403) break;  // авторизация — перебор бесполезен
    }
  }
  if (lastErr) throw lastErr;
  return null;
}

async function _openaiCall(base, key, model, prompt) {
  const body = { model, messages: [{ role: "user", content: prompt }], temperature: 0.2 };
  const data = await withTimeout(async (signal) => {
    const r = await fetch(base + "/chat/completions", { method: "POST", headers: { "Content-Type": "application/json", "Authorization": "Bearer " + key }, body: JSON.stringify(body), signal });
    if (!r.ok) { const t = await r.text().catch(() => ""); const e = new Error("HTTP " + r.status + " " + t.slice(0, 160)); e.status = r.status; throw e; }
    return r.json();
  }, 30000);
  let text = ((((data.choices || [])[0] || {}).message) || {}).content || "";
  if (Array.isArray(text)) text = text.map((p) => (p && p.text) || "").join("");
  return _parseScore(text);
}

async function _geminiCall(key, model, prompt) {
  const body = { contents: [{ parts: [{ text: prompt }] }], generationConfig: { temperature: 0.2, maxOutputTokens: 700, responseMimeType: "application/json" } };
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`;
  const data = await withTimeout(async (signal) => {
    const r = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body), signal });
    if (!r.ok) { const t = await r.text().catch(() => ""); throw new Error("HTTP " + r.status + " " + t.slice(0, 160)); }
    return r.json();
  }, 30000);
  const parts = (((data.candidates || [])[0] || {}).content || {}).parts || [];
  return _parseScore(parts.map((p) => p.text || "").join(""));
}

/* ------------------------- парсинг лент --------------------------------- */
function parseRSS(text) {
  // Браузер: надёжный DOMParser; node/без DOM: регексп-фолбэк.
  if (typeof DOMParser !== "undefined") {
    const doc = new DOMParser().parseFromString(text, "application/xml");
    if (!doc.querySelector("parsererror")) {
      return [...doc.querySelectorAll("item, entry")].map((n) => {
        const g = (s) => { const e = n.querySelector(s); return e ? e.textContent.trim() : ""; };
        const linkEl = n.querySelector("link");
        const link = linkEl ? (linkEl.getAttribute("href") || linkEl.textContent.trim()) : "";
        return { title: g("title"), link, summary: stripHtml(g("description") || g("summary") || g("encoded")), published: parseDate(g("pubDate") || g("published") || g("updated")) };
      });
    }
  }
  return parseRSSRegex(text);
}
function parseRSSRegex(text) {
  const out = [];
  const blocks = text.match(/<(item|entry)[\s\S]*?<\/\1>/gi) || [];
  for (const b of blocks) {
    const pick = (tag) => { const m = new RegExp("<" + tag + "[^>]*>([\\s\\S]*?)<\\/" + tag + ">", "i").exec(b); return m ? cdata(m[1]) : ""; };
    let link = pick("link");
    const href = /<link[^>]*href="([^"]+)"/i.exec(b); if (href) link = href[1];
    out.push({ title: cdata(pick("title")), link, summary: stripHtml(pick("description") || pick("summary") || pick("content:encoded")), published: parseDate(pick("pubDate") || pick("published") || pick("updated")) });
  }
  return out;
}
function cdata(s) { return document_unescape((s || "").replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")).trim(); }

function parseTelegram(html) {
  const out = [];
  if (typeof DOMParser !== "undefined") {
    const doc = new DOMParser().parseFromString(html, "text/html");
    for (const m of doc.querySelectorAll(".tgme_widget_message")) {
      const txt = m.querySelector(".tgme_widget_message_text");
      const time = m.querySelector("time[datetime]");
      const post = m.getAttribute("data-post") || "";
      const text = txt ? normalizeWs(txt.textContent) : "";
      if (text) out.push({ text, post, datetime: time ? time.getAttribute("datetime") : "" });
    }
  }
  return out;
}

/* ---------------------------- сбор данных ------------------------------- */
function mkId() { return "id" + Math.abs(hashStr([].slice.call(arguments).join("|"))).toString(36); }
function hashStr(s) { let h = 0; for (let i = 0; i < s.length; i++) { h = (h << 5) - h + s.charCodeAt(i); h |= 0; } return h; }

async function fetchRSS(src) {
  const txt = await proxiedText(src.url);
  return parseRSS(txt).filter((e) => e.title || e.summary).map((e) => ({
    id: mkId(src.id, e.link, e.title), source_id: src.id, source_name: src.name, stream: src.stream,
    lang: src.lang, title: e.title, summary: e.summary, url: e.link, published: e.published, source_weight: src.source_weight,
  }));
}
async function fetchTelegram(ch) {
  const txt = await proxiedText("https://t.me/s/" + ch.channel);
  return parseTelegram(txt).slice(-30).map((m) => ({
    id: mkId(ch.id, m.post), source_id: ch.id, source_name: ch.name, stream: ch.stream, lang: "ru",
    title: m.text.slice(0, 140), summary: m.text.slice(0, 600), url: "https://t.me/" + m.post, published: parseDate(m.datetime), source_weight: ch.source_weight,
  }));
}
async function fetchDeepState() {
  let data;
  try {  // прямой запрос (CORS разрешён), с таймаутом чтобы не зависнуть
    data = await withTimeout(async (signal) => { const r = await fetch(CONFIG.deepstateUrl, { signal }); if (!r.ok) throw new Error("HTTP " + r.status); return r.json(); }, 12000);
  } catch (e) {
    data = JSON.parse(await proxiedText(CONFIG.deepstateUrl));
  }
  return deepstateFromGeoJSON(data);
}

async function collectNews() {  // только RSS + Telegram (DeepState грузится отдельно и быстро)
  const tasks = [], statuses = [];
  const run = async (label, stream, fn) => {
    try { const items = await fn(); statuses.push({ name: label, stream, mode: "live", items_count: items.length }); return items; }
    catch (e) { statuses.push({ name: label, stream, mode: "error", items_count: 0, last_error: String(e).slice(0, 120) }); return []; }
  };
  for (const s of CONFIG.rssSources) tasks.push(run(s.name, s.stream, () => fetchRSS(s)));
  for (const c of CONFIG.telegramChannels) tasks.push(run(c.name, c.stream, () => fetchTelegram(c)));
  const lists = await Promise.all(tasks);
  return { items: [].concat(...lists), statuses };
}
function dsStatusOf(ds) {
  return ds && ds.status === "ok"
    ? { name: "DeepStateMAP (карта фронта)", stream: "deepstate", mode: "live", items_count: ds.occupied_polys }
    : { name: "DeepStateMAP (карта фронта)", stream: "deepstate", mode: "error", items_count: 0 };
}

/* ----------------------------- хранилище -------------------------------- */
const LS = { items: "baro_items_v1", hist: "baro_hist_v1", ds: "baro_ds_v1" };
function lsGet(k, def) { try { return JSON.parse(localStorage.getItem(k)) || def; } catch (e) { return def; } }
function lsSet(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }

function mergeItems(base, extra) {
  const map = {};
  const key = (it) => it.url || it.id || (it.source_name + "|" + it.title);
  for (const it of base) map[key(it)] = it;
  for (const it of extra) map[key(it)] = it;
  // держим только окно + запас, чтобы localStorage не разрастался
  const cutoff = Date.now() - (CONFIG.params.windowDays + 4) * 86400000;
  return Object.values(map).filter((it) => dtMs(it.published) >= cutoff).sort((a, b) => dtMs(b.published) - dtMs(a.published)).slice(0, 1500);
}
function deepstateDelta(snap) {
  if (!snap || snap.status !== "ok") return snap;
  const hist = lsGet(LS.ds, []);
  const prev = hist.length ? hist[hist.length - 1] : null;
  let delta = null;
  if (prev && prev.taken_at !== snap.taken_at) delta = Math.round((snap.occupied_km2 - prev.occupied_km2) * 10) / 10;
  else if (prev) delta = snap.delta_km2 != null ? snap.delta_km2 : (Math.round((snap.occupied_km2 - prev.occupied_km2) * 10) / 10);
  snap.delta_km2 = delta;
  if (!prev || prev.taken_at !== snap.taken_at) { hist.push({ taken_at: snap.taken_at, occupied_km2: snap.occupied_km2 }); lsSet(LS.ds, hist.slice(-120)); }
  return snap;
}
function pushHistory(barometer) {
  const hist = lsGet(LS.hist, []);
  const last = hist.length ? hist[hist.length - 1] : null;
  if (!last || (Date.now() - dtMs(last.t)) > 3600000) { hist.push({ t: nowStr(), v: barometer }); lsSet(LS.hist, hist.slice(-500)); }
  return hist;
}

/* ------------------------------ рендер ---------------------------------- */
const $ = (id) => document.getElementById(id);
function relTime(iso) { if (!iso) return "—"; const s = Math.floor((Date.now() - dtMs(iso)) / 1000); if (s < 60) return "только что"; if (s < 3600) return Math.floor(s / 60) + " мин назад"; if (s < 86400) return Math.floor(s / 3600) + " ч назад"; return Math.floor(s / 86400) + " дн назад"; }

function polar(cx, cy, r, v) { const a = Math.PI * (1 - v / 100); return { x: cx + r * Math.cos(a), y: cy - r * Math.sin(a) }; }
function arcPath(cx, cy, r, v0, v1) { const a = polar(cx, cy, r, v0), b = polar(cx, cy, r, v1); return `M ${a.x.toFixed(1)} ${a.y.toFixed(1)} A ${r} ${r} 0 0 1 ${b.x.toFixed(1)} ${b.y.toFixed(1)}`; }
function renderGauge(value) {
  const cx = 220, cy = 200, r = 160, w = 26;
  const segs = [[0, 25, "#2fd08a"], [25, 45, "#8fd24b"], [45, 70, "#f4c945"], [70, 92, "#f59042"], [92, 100, "#ef5350"]];
  let s = `<path d="${arcPath(cx, cy, r, 0, 100)}" fill="none" stroke="#1f2a40" stroke-width="${w + 8}" stroke-linecap="round"/>`;
  for (const [a, b, c] of segs) s += `<path d="${arcPath(cx, cy, r, a, b)}" fill="none" stroke="${c}" stroke-width="${w}"/>`;
  for (const t of [0, 25, 50, 75, 100]) { const l = polar(cx, cy, r + w / 2 + 15, t); s += `<text x="${l.x.toFixed(1)}" y="${l.y.toFixed(1)}" fill="#6f8099" font-size="13" font-family="monospace" text-anchor="middle" dominant-baseline="middle">${t}</text>`; }
  const z = zoneFor(value);
  // маркер-указатель на дуге (вместо длинной стрелки — чтобы не перекрывать число)
  const mt = polar(cx, cy, r - w / 2 - 2, value);
  const m1 = polar(cx, cy, r - w / 2 - 20, value - 3.2);
  const m2 = polar(cx, cy, r - w / 2 - 20, value + 3.2);
  s += `<polygon points="${mt.x.toFixed(1)},${mt.y.toFixed(1)} ${m1.x.toFixed(1)},${m1.y.toFixed(1)} ${m2.x.toFixed(1)},${m2.y.toFixed(1)}" fill="${z.hex}" stroke="#0b0f1a" stroke-width="1.5"/>`;
  s += `<text x="${cx}" y="158" text-anchor="middle" font-family="'JetBrains Mono',monospace" font-size="68" font-weight="700" fill="${z.hex}">${Math.round(value)}</text>`;
  s += `<text x="${cx}" y="182" text-anchor="middle" font-size="12.5" fill="#6f8099">пунктов из 100</text>`;
  $("gauge").innerHTML = s;
}
function renderCategories(cats) {
  $("cats").innerHTML = cats.map((c) => { const v = c.signed, pos = v >= 0, w = Math.abs(v) / 100 * 50; return `<div class="cat"><div class="row"><span class="lbl">${esc(c.label)} <span class="wt">(вес ${Math.round(c.weight * 100)}%)</span></span><span class="val">${v > 0 ? "+" : ""}${v.toFixed(0)}</span></div><div class="divbar"><div class="mid"></div><span class="fill ${pos ? "pos" : "neg"}" style="width:${w}%"></span></div></div>`; }).join("");
}
function streamMode(key, sources) { const ss = sources.filter((s) => s.stream === key); if (!ss.length) return "error"; if (ss.some((s) => s.mode === "live")) return "live"; if (ss.some((s) => s.mode === "sample")) return "sample"; return "error"; }
function renderStreams(streams, sources) {
  $("streams").innerHTML = streams.map((s) => { const m = STREAM_META[s.key] || { icon: "•" }; const mode = streamMode(s.key, sources); const lbl = { live: "онлайн", sample: "сэмпл", error: "нет связи" }[mode]; return `<div class="stream"><div class="ico">${m.icon}</div><div class="name">${esc(s.label)}</div><div class="num">${s.relevant}</div><div class="hint">релевантных сигналов</div><div class="st"><span class="dot ${mode}"></span>${lbl}</div></div>`; }).join("");
}
function renderDrivers(drivers) {
  if (!drivers.length) { $("drivers").innerHTML = `<div class="hint">Пока нет выраженных сигналов.</div>`; return; }
  $("drivers").innerHTML = drivers.map((d) => { const pos = d.polarity > 0, cls = pos ? "pos" : "neg", ex = d.example || {}; const exH = ex.url ? `<a class="ex" href="${esc(ex.url)}" target="_blank" rel="noopener">${esc(ex.source)} · ${esc(ex.title)}</a>` : `<span class="ex">${esc(d.category_label)}</span>`; return `<div class="driver"><div class="sgn ${cls}">${pos ? "▲" : "▼"}</div><div class="body"><div class="term">${esc(d.term)}</div>${exH}</div><div class="bar"><span class="${cls}" style="width:${d.strength}%"></span></div><div class="cnt">×${d.count}</div></div>`; }).join("");
}
function renderFeed(feed) {
  if (!feed.length) { $("feed").innerHTML = `<div class="hint">Нет релевантных новостей в окне.</div>`; return; }
  $("feed").innerHTML = feed.map((it) => { const sm = STREAM_META[it.stream] || { name: it.stream }; const tags = (it.terms || []).map((t) => `<span class="tag ${t.polarity > 0 ? "pos" : "neg"}">${esc(t.term)}</span>`).join(""); const ttl = it.url ? `<a href="${esc(it.url)}" target="_blank" rel="noopener">${esc(it.title)}</a>` : esc(it.title); return `<div class="fitem"><div class="meta"><span class="src">${esc(it.source)}</span><span class="streamtag">${esc(sm.name)}</span><span>· ${relTime(it.published)}</span></div><div class="ttl">${ttl}</div>${tags ? `<div class="tags">${tags}</div>` : ""}</div>`; }).join("");
}
function renderHistory(history) {
  const svg = $("chart"), empty = $("chart-empty");
  if (!history || history.length < 2) { svg.classList.add("hidden"); empty.classList.remove("hidden"); return; }
  svg.classList.remove("hidden"); empty.classList.add("hidden");
  const W = 560, H = 180, pad = 8, xs = (i) => pad + i / (history.length - 1) * (W - 2 * pad), ys = (v) => H - pad - v / 100 * (H - 2 * pad);
  let g = "";
  for (const gl of [25, 50, 75]) g += `<line x1="0" y1="${ys(gl).toFixed(1)}" x2="${W}" y2="${ys(gl).toFixed(1)}" stroke="#1a2436"/><text x="4" y="${(ys(gl) - 4).toFixed(1)}" fill="#3a4d6b" font-size="10" font-family="monospace">${gl}</text>`;
  const pts = history.map((h, i) => `${xs(i).toFixed(1)},${ys(h.v).toFixed(1)}`).join(" ");
  g += `<polygon points="${pad},${H - pad} ${pts} ${W - pad},${H - pad}" fill="rgba(90,162,255,.10)"/><polyline points="${pts}" fill="none" stroke="#5aa2ff" stroke-width="2.5"/>`;
  const last = history[history.length - 1]; g += `<circle cx="${xs(history.length - 1).toFixed(1)}" cy="${ys(last.v).toFixed(1)}" r="4" fill="${zoneFor(last.v).hex}"/>`;
  svg.innerHTML = g;
}

function buildFeed(items) {
  return items.filter((it) => it.relevant).sort((a, b) => dtMs(b.published) - dtMs(a.published)).slice(0, 60).map((it) => {
    const seen = {}, terms = [];
    for (const s of it.signals || []) if (!seen[s.term]) { seen[s.term] = 1; terms.push({ term: s.term, polarity: s.polarity }); }
    return { title: it.title, url: it.url, source: it.source_name, stream: it.stream, published: it.published, terms: terms.slice(0, 6) };
  });
}

function applyReading(reading, sources, history, note) {
  renderGauge(reading.final_barometer);
  const z = zoneFor(reading.final_barometer);
  $("gzone").textContent = z.label; $("gzone").style.color = z.hex;
  const fc = reading.forecast || {};
  $("fc-label").textContent = fc.label || "—";
  $("fc-date").textContent = fc.date ? new Date(fc.date).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" }) : "";
  $("fc-basis").textContent = fc.basis ? "Основание: " + fc.basis : "";
  const conf = Math.round((reading.confidence || 0) * 100); $("conf-val").textContent = conf + "%"; $("conf-bar").style.width = conf + "%";
  $("kv-vel").textContent = reading.velocity == null ? "—" : (reading.velocity > 0 ? "+" : "") + reading.velocity.toFixed(2) + "/дн";
  $("kv-rel").textContent = reading.components.relevant_items;
  $("kv-rule").textContent = reading.barometer.toFixed(0);
  $("kv-llm").textContent = (reading.llm_barometer == null) ? "—" : reading.llm_barometer.toFixed(0);
  const llm = reading.llm;
  $("llm-rationale").textContent = (llm && llm.rationale) ? "ИИ: " + llm.rationale : "";
  renderStreams(reading.streams || [], sources || []);
  renderCategories(reading.components.categories || []);
  const ds = reading.components.deepstate || {};
  $("ds-occ").textContent = ds.occupied_km2 != null ? Math.round(ds.occupied_km2).toLocaleString("ru-RU") + " км²" : "—";
  const delta = ds.delta_km2; $("ds-delta").textContent = delta == null ? "—" : (delta > 0 ? "+" : "") + Math.round(delta) + " км²";
  $("ds-delta").style.color = delta == null ? "" : (delta < 0 ? "var(--neg)" : "var(--pos)");
  $("ds-trend").textContent = ds.trend || "";
  renderHistory(history || []);
  renderDrivers(reading.drivers || []);
  $("chip-updated").innerHTML = `Обновлено: <b>${relTime(reading.taken_at)}</b>${note ? " · " + note : ""}`;
  updateAiChip();
  $("srclist").innerHTML = (sources || []).map((s) => `<span class="s"><span class="dot ${s.mode}"></span>${esc(s.name)} · ${s.items_count}</span>`).join("");
  $("skeleton").classList.add("hidden"); $("app").classList.remove("hidden");
}

// Клиентский рендер (seed/прокси): строим ленту из items.
function render(reading, items, sources, history, note) {
  applyReading(reading, sources, history, note);
  renderFeed(buildFeed(items || []));
  renderContext([]);  // контекст приходит только из серверного data.json
}

// Серверный рендер: готовый state.json из data.json (надёжный канал, без прокси).
function renderServerState(state) {
  applyReading(state.reading, state.sources || [], state.history || [], "данные сервера");
  renderFeed(state.feed || []);
  renderContext(state.context || []);
}

function renderContext(refs) {
  const card = $("context-card"); if (!card) return;
  if (!refs || !refs.length) { card.classList.add("hidden"); return; }
  card.classList.remove("hidden");
  $("context").innerHTML = refs.map((r) =>
    `<a class="ref" href="${esc(r.url)}" target="_blank" rel="noopener">📖 ${esc(r.title)}</a>`).join("");
}

/* ---------------------------- управление -------------------------------- */
let CURRENT = { items: [], sources: [] };
let LLM = lsGet("baro_llm", null);   // последняя оценка Gemini
function computeAndRender(items, sources, note) {
  items.forEach(analyzeItem);
  let ds = lsGet("baro_ds_last", null);
  const reading = computeReading(items, ds, lsGet(LS.hist, []), sources ? okRatio(sources) : 1, LLM);
  const hist = pushHistory(reading.final_barometer);
  render(reading, items, sources || seedSources(items), hist, note);
  CURRENT = { items, sources: sources || CURRENT.sources };
}
function okRatio(sources) { if (!sources.length) return 1; return sources.filter((s) => s.mode !== "error").length / sources.length; }
function seedSources(items) {
  // статусы для стартовых данных (до первого живого сбора)
  const byStream = {}; for (const it of items) byStream[it.stream] = (byStream[it.stream] || 0) + 1;
  const names = {}; for (const it of items) names[it.source_name] = it.stream;
  return Object.keys(names).map((n) => ({ name: n, stream: names[n], mode: "sample", items_count: 0 }));
}

async function refresh(initial) {
  const btn = $("refresh"); if (btn) { btn.classList.add("loading"); btn.disabled = true; btn.querySelector(".lbl").textContent = "Обновление…"; }
  // DeepState и новости тянем ПАРАЛЛЕЛЬНО и рисуем по мере готовности —
  // медленный/недоступный источник не блокирует остальные.
  let dsStatus = null, newsStatuses = [], dsDone = false, newsDone = false;
  const reRender = () => {
    const statuses = newsStatuses.concat(dsStatus ? [dsStatus] : []);
    const note = newsDone ? liveNote(statuses) : "обновляю…";
    computeAndRender(currentItems(), statuses.length ? statuses : null, note);
  };
  const dsP = (async () => {
    try { let ds = await fetchDeepState(); if (ds.status === "ok") { ds = deepstateDelta(ds); lsSet("baro_ds_last", ds); } dsStatus = dsStatusOf(ds); }
    catch (e) { dsStatus = dsStatusOf(null); }
    dsDone = true; reRender();
  })();
  const newsP = (async () => {
    try { const news = await collectNews(); newsStatuses = news.statuses; lsSet(LS.items, mergeItems(lsGet(LS.items, []).concat(SEED), news.items)); }
    catch (e) {}
    newsDone = true; reRender();
  })();
  await Promise.allSettled([dsP, newsP]);
  reRender();
  // 3) ИИ-аналитика (если включена) — поверх свежих новостей
  if (aiOn()) {
    try { const res = await aiAnalyze(currentItems()); if (res) { LLM = res; lsSet("baro_llm", LLM); } } catch (e) {}
    reRender();
  }
  if (btn) { btn.classList.remove("loading"); btn.disabled = false; btn.querySelector(".lbl").textContent = "Обновить"; }
}
function liveNote(statuses) { const ok = statuses.filter((s) => s.mode === "live").length; return `источников онлайн: ${ok}/${statuses.length}`; }
function currentItems() { const cached = lsGet(LS.items, []); return mergeItems(cached, SEED); }

function updateAiChip() {
  const c = $("chip-ai"); if (!c) return;
  const on = aiOn();
  c.classList.toggle("on", on);
  c.innerHTML = `<span class="dot"></span>ИИ: <b>${on ? "вкл" : "выкл"}</b>`;
}

function setupSettings() {
  const modal = $("settings"); if (!modal) return;
  const sel = $("ai-provider");
  // наполняем список провайдеров
  if (sel && !sel.options.length) {
    for (const k in AI_PROVIDERS) { const o = document.createElement("option"); o.value = k; o.textContent = AI_PROVIDERS[k].label; sel.appendChild(o); }
  }
  const fill = () => {
    const p = aiProvider(); const pc = AI_PROVIDERS[p] || {};
    if (sel) sel.value = p;
    $("ai-key").value = aiKey();
    $("ai-key").placeholder = "API-ключ — " + (pc.hint || "");
    $("ai-model").value = (lsGet("baro_ai_model", "") || pc.model);  // показываем реальную модель по умолчанию
    $("ai-model").placeholder = pc.model || "";
    $("ai-on").checked = lsGet("baro_ai_on", true);
    if ($("ai-signup")) $("ai-signup").href = pc.signup || "#";
    $("ai-status").textContent = "";
  };
  const open = () => { fill(); modal.classList.remove("hidden"); };
  const close = () => modal.classList.add("hidden");
  const recompute = () => computeAndRender(currentItems(), CURRENT.sources && CURRENT.sources.length ? CURRENT.sources : null);
  if ($("settings-btn")) $("settings-btn").addEventListener("click", open);
  if ($("ai-cancel")) $("ai-cancel").addEventListener("click", close);
  modal.addEventListener("click", (e) => { if (e.target === modal) close(); });
  if (sel) sel.addEventListener("change", () => { lsSet("baro_ai_provider", sel.value); lsSet("baro_ai_model", ""); fill(); });
  if ($("ai-save")) $("ai-save").addEventListener("click", async () => {
    lsSet("baro_ai_provider", sel ? sel.value : aiProvider());
    lsSet("baro_ai_key", $("ai-key").value.trim());
    lsSet("baro_ai_model", $("ai-model").value.trim());
    lsSet("baro_ai_on", $("ai-on").checked);
    updateAiChip();
    if (aiKey() && aiOn()) {
      $("ai-status").textContent = "Проверяю ключ…";
      try {
        const r = await aiAnalyze(currentItems());
        if (r) { LLM = r; lsSet("baro_llm", LLM); recompute(); $("ai-status").textContent = `✓ Готово: оценка ИИ ${r.score}/100`; }
        else { $("ai-status").textContent = "Не удалось получить ответ — проверьте ключ/модель."; }
      } catch (e) {
        let m = String(e.message || e);
        if (e.status === 429 || /429|rate.?limit/i.test(m)) m = "все бесплатные модели сейчас перегружены (rate limit). Попробуйте через минуту или выберите провайдера Groq — там свободный лимит надёжнее.";
        else if (e.status === 401 || e.status === 403 || /invalid|unauthor|api key/i.test(m)) m = "ключ не принят — проверьте, что скопировали его полностью.";
        else if (/model/i.test(m)) m = "неверная модель. Очистите поле «Модель» (возьмётся по умолчанию) или впишите :free-модель с openrouter.ai/models";
        $("ai-status").textContent = "Ошибка: " + m.slice(0, 200);
      }
    } else {
      LLM = null; lsSet("baro_llm", null); recompute(); close();
    }
  });
  updateAiChip();
}

// Надёжный канал: готовый data.json с raw.githubusercontent (там CORS).
async function tryLoadServer() {
  const url = CONFIG.dataUrl; if (!url) return null;
  const bust = url + (url.includes("?") ? "&" : "?") + "t=" + Date.now();
  try {
    const r = await withTimeout((signal) => fetch(bust, { cache: "no-store", signal }), 12000);
    if (!r.ok) return null;
    const s = await r.json();
    return (s && s.reading) ? s : null;
  } catch (e) { return null; }
}

let SERVER_MODE = false;
async function loadLive(initial) {
  const s = await tryLoadServer();
  if (s) { SERVER_MODE = true; renderServerState(s); }
  else { SERVER_MODE = false; await refresh(initial); }  // фолбэк: клиентский сбор через прокси
}

function boot() {
  // 1) мгновенно показываем барометр на стартовых данных
  computeAndRender(currentItems(), null, "загрузка…");
  setupSettings();
  // 2) основной источник — серверный data.json, иначе клиентский сбор
  if ($("refresh")) $("refresh").addEventListener("click", () => loadLive(false));
  loadLive(true);
  setInterval(() => loadLive(false), 5 * 60 * 1000);  // тихое обновление
}

if (typeof document !== "undefined") {
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
}

/* экспорт для node-тестов */
if (typeof module !== "undefined") module.exports = { normalize, analyzeItem, computeReading, parseRSS, parseRSSRegex, parseTelegram, deepstateFromGeoJSON, deepstateNorm, stripHtml, aiAnalyze };
