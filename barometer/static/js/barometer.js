/* =========================================================================
   Барометр мобилизации — фронтенд (vanilla JS, без внешних библиотек)
   ========================================================================= */
"use strict";

const ZONES = [
  { max: 25,  color: "var(--z-low)",  hex: "#2ec78a", label: "Низкий риск" },
  { max: 45,  color: "var(--z-mod)",  hex: "#8bd34f", label: "Умеренный риск" },
  { max: 70,  color: "var(--z-elev)", hex: "#f2c14e", label: "Повышенный риск" },
  { max: 92,  color: "var(--z-high)", hex: "#f08a3c", label: "Высокий риск" },
  { max: 101, color: "var(--z-crit)", hex: "#e8514a", label: "Мобилизация объявлена" },
];
const STREAM_META = {
  media:     { icon: "📰", name: "Независимые СМИ" },
  deepstate: { icon: "🗺️", name: "DeepState · фронт" },
  analysts:  { icon: "🛰️", name: "Аналитики и соцсети" },
  raids:     { icon: "🚨", name: "Облавы и бусификация" },
};

const $ = (id) => document.getElementById(id);

function zoneFor(v) {
  for (const z of ZONES) if (v < z.max) return z;
  return ZONES[ZONES.length - 1];
}
function esc(s) {
  return (s || "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}
function relTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso.replace(" ", "T") + "Z");
  if (isNaN(d)) return iso;
  const s = Math.floor((Date.now() - d.getTime()) / 1000);
  if (s < 60) return "только что";
  if (s < 3600) return Math.floor(s / 60) + " мин назад";
  if (s < 86400) return Math.floor(s / 3600) + " ч назад";
  return Math.floor(s / 86400) + " дн назад";
}

/* ----------------------------- gauge ------------------------------------ */
function polar(cx, cy, r, valuePct) {
  const ang = Math.PI * (1 - valuePct / 100); // 0→π (лево), 100→0 (право)
  return { x: cx + r * Math.cos(ang), y: cy - r * Math.sin(ang) };
}
function arcPath(cx, cy, r, v0, v1) {
  const a = polar(cx, cy, r, v0), b = polar(cx, cy, r, v1);
  return `M ${a.x.toFixed(1)} ${a.y.toFixed(1)} A ${r} ${r} 0 0 1 ${b.x.toFixed(1)} ${b.y.toFixed(1)}`;
}
function renderGauge(value) {
  const cx = 210, cy = 212, r = 176, w = 26;
  const segs = [
    [0, 25, "#2ec78a"], [25, 45, "#8bd34f"], [45, 70, "#f2c14e"],
    [70, 92, "#f08a3c"], [92, 100, "#e8514a"],
  ];
  let svg = "";
  // фон-трек
  svg += `<path d="${arcPath(cx, cy, r, 0, 100)}" fill="none" stroke="#1a2436" stroke-width="${w + 6}" stroke-linecap="round"/>`;
  // цветные зоны
  for (const [a, b, col] of segs)
    svg += `<path d="${arcPath(cx, cy, r, a, b)}" fill="none" stroke="${col}" stroke-width="${w}" opacity="0.92"/>`;
  // деления
  for (const t of [0, 25, 50, 75, 100]) {
    const p1 = polar(cx, cy, r - w / 2 - 4, t), p2 = polar(cx, cy, r + w / 2 + 4, t);
    const lab = polar(cx, cy, r + w / 2 + 20, t);
    svg += `<line x1="${p1.x.toFixed(1)}" y1="${p1.y.toFixed(1)}" x2="${p2.x.toFixed(1)}" y2="${p2.y.toFixed(1)}" stroke="#3a4d6b" stroke-width="2"/>`;
    svg += `<text x="${lab.x.toFixed(1)}" y="${lab.y.toFixed(1)}" fill="#5d6e89" font-size="12" font-family="monospace" text-anchor="middle" dominant-baseline="middle">${t}</text>`;
  }
  // стрелка
  const z = zoneFor(value);
  const tip = polar(cx, cy, r - w - 6, value);
  const left = polar(cx, cy, 10, value - 50);
  const right = polar(cx, cy, 10, value + 50);
  svg += `<polygon points="${tip.x.toFixed(1)},${tip.y.toFixed(1)} ${left.x.toFixed(1)},${left.y.toFixed(1)} ${right.x.toFixed(1)},${right.y.toFixed(1)}" fill="${z.hex}"/>`;
  svg += `<circle cx="${cx}" cy="${cy}" r="13" fill="#0e1420" stroke="${z.hex}" stroke-width="3"/>`;
  svg += `<circle cx="${tip.x.toFixed(1)}" cy="${tip.y.toFixed(1)}" r="5" fill="${z.hex}"/>`;
  $("gauge").innerHTML = svg;
}

/* --------------------------- categories --------------------------------- */
function renderCategories(cats) {
  const html = cats.map((c) => {
    const v = c.signed; // -100..100
    const pos = v >= 0;
    const width = (Math.abs(v) / 100) * 50;
    const fill = pos
      ? `<span class="fill pos" style="width:${width}%"></span>`
      : `<span class="fill neg" style="width:${width}%"></span>`;
    return `<div class="cat">
      <div class="row"><span class="lbl">${esc(c.label)} <span class="wt">(вес ${Math.round(c.weight * 100)}%)</span></span>
      <span class="val">${v > 0 ? "+" : ""}${v.toFixed(0)}</span></div>
      <div class="divbar"><div class="mid"></div>${fill}</div>
    </div>`;
  }).join("");
  $("cats").innerHTML = html;
}

/* ----------------------------- streams ---------------------------------- */
function streamMode(streamKey, sources) {
  const ss = sources.filter((s) => s.stream === streamKey);
  if (!ss.length) return "error";
  if (ss.some((s) => s.mode === "live")) return "live";
  if (ss.some((s) => s.mode === "sample")) return "sample";
  return "error";
}
function renderStreams(streams, sources) {
  const html = streams.map((s) => {
    const m = STREAM_META[s.key] || { icon: "•", name: s.label };
    const mode = streamMode(s.key, sources);
    const modeLbl = { live: "онлайн", sample: "сэмпл", error: "нет связи" }[mode];
    return `<div class="stream">
      <div class="ico">${m.icon}</div>
      <div class="name">${esc(s.label)}</div>
      <div class="num">${s.relevant}</div>
      <div class="hint">релевантных сигналов</div>
      <div class="st"><span class="dot ${mode}"></span>${modeLbl}</div>
    </div>`;
  }).join("");
  $("streams").innerHTML = html;
}

/* ----------------------------- drivers ---------------------------------- */
function renderDrivers(drivers) {
  if (!drivers.length) { $("drivers").innerHTML = `<div class="hint">Пока нет выраженных сигналов.</div>`; return; }
  $("drivers").innerHTML = drivers.map((d) => {
    const pos = d.polarity > 0;
    const cls = pos ? "pos" : "neg";
    const ex = d.example || {};
    const exHtml = ex.url
      ? `<a class="ex" href="${esc(ex.url)}" target="_blank" rel="noopener">${esc(ex.source)} · ${esc(ex.title)}</a>`
      : `<span class="ex">${esc(d.category_label)}</span>`;
    return `<div class="driver">
      <div class="sgn ${cls}">${pos ? "▲" : "▼"}</div>
      <div class="body"><div class="term">${esc(d.term)}</div>${exHtml}</div>
      <div class="bar"><span class="${cls}" style="width:${d.strength}%"></span></div>
      <div class="cnt">×${d.count}</div>
    </div>`;
  }).join("");
}

/* ------------------------------ feed ------------------------------------ */
function renderFeed(feed) {
  if (!feed.length) { $("feed").innerHTML = `<div class="hint">Нет релевантных новостей в окне.</div>`; return; }
  $("feed").innerHTML = feed.map((it) => {
    const sm = STREAM_META[it.stream] || { name: it.stream };
    const tags = (it.terms || []).map((t) =>
      `<span class="tag ${t.polarity > 0 ? "pos" : "neg"}">${esc(t.term)}</span>`).join("");
    const ttl = it.url
      ? `<a href="${esc(it.url)}" target="_blank" rel="noopener">${esc(it.title)}</a>`
      : esc(it.title);
    return `<div class="fitem">
      <div class="meta"><span class="src">${esc(it.source)}</span>
        <span class="streamtag">${esc(sm.name)}</span>
        <span>· ${relTime(it.published)}</span></div>
      <div class="ttl">${ttl}</div>
      ${tags ? `<div class="tags">${tags}</div>` : ""}
    </div>`;
  }).join("");
}

/* ----------------------------- history ---------------------------------- */
function renderHistory(history) {
  const svg = $("chart"), empty = $("chart-empty");
  if (!history || history.length < 2) { svg.classList.add("hidden"); empty.classList.remove("hidden"); return; }
  svg.classList.remove("hidden"); empty.classList.add("hidden");
  const W = 560, H = 180, pad = 8;
  const xs = (i) => pad + (i / (history.length - 1)) * (W - 2 * pad);
  const ys = (v) => H - pad - (v / 100) * (H - 2 * pad);
  let g = "";
  for (const gl of [25, 50, 75]) {
    g += `<line x1="0" y1="${ys(gl).toFixed(1)}" x2="${W}" y2="${ys(gl).toFixed(1)}" stroke="#1a2436" stroke-width="1"/>`;
    g += `<text x="4" y="${(ys(gl) - 4).toFixed(1)}" fill="#3a4d6b" font-size="10" font-family="monospace">${gl}</text>`;
  }
  const pts = history.map((h, i) => `${xs(i).toFixed(1)},${ys(h.v).toFixed(1)}`).join(" ");
  const area = `${pad},${H - pad} ${pts} ${(W - pad)},${H - pad}`;
  g += `<polygon points="${area}" fill="rgba(90,162,255,.10)"/>`;
  g += `<polyline points="${pts}" fill="none" stroke="#5aa2ff" stroke-width="2.5"/>`;
  const last = history[history.length - 1];
  g += `<circle cx="${xs(history.length - 1).toFixed(1)}" cy="${ys(last.v).toFixed(1)}" r="4" fill="${zoneFor(last.v).hex}"/>`;
  svg.innerHTML = g;
}

/* --------------------------- render all --------------------------------- */
function render(state) {
  if (!state || state.status === "empty" || !state.reading) return false;
  const r = state.reading;

  renderGauge(r.final_barometer);
  const z = zoneFor(r.final_barometer);
  $("gval").innerHTML = `${r.final_barometer.toFixed(0)}<small>/100</small>`;
  $("gval").style.color = z.hex;
  $("gzone").textContent = z.label;
  $("gzone").style.color = z.hex;

  // прогноз
  const fc = r.forecast || {};
  $("fc-label").textContent = fc.label || "—";
  $("fc-date").textContent = fc.date ? new Date(fc.date).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" }) : "";
  $("fc-basis").textContent = fc.basis ? "Основание: " + fc.basis : "";
  const conf = Math.round((r.confidence || 0) * 100);
  $("conf-val").textContent = conf + "%";
  $("conf-bar").style.width = conf + "%";
  $("kv-vel").textContent = (r.velocity == null) ? "—" : (r.velocity > 0 ? "+" : "") + r.velocity.toFixed(2) + "/дн";
  $("kv-rel").textContent = r.components.relevant_items;
  $("kv-rule").textContent = r.barometer.toFixed(0);
  $("kv-llm").textContent = (r.llm_barometer == null) ? "выкл." : r.llm_barometer.toFixed(0);
  const llm = r.llm;
  $("llm-rationale").textContent = (llm && llm.rationale) ? "Claude: " + llm.rationale : "";

  renderStreams(r.streams || [], state.sources || []);
  renderCategories(r.components.categories || []);

  // deepstate
  const ds = r.components.deepstate || {};
  $("ds-occ").textContent = ds.occupied_km2 != null ? Math.round(ds.occupied_km2).toLocaleString("ru-RU") + " км²" : "—";
  const delta = ds.delta_km2;
  $("ds-delta").textContent = (delta == null) ? "—" : (delta > 0 ? "+" : "") + Math.round(delta) + " км²";
  $("ds-delta").style.color = (delta == null) ? "" : (delta < 0 ? "var(--neg)" : "var(--pos)");
  $("ds-trend").textContent = ds.trend || "";

  renderHistory(state.history || []);
  renderDrivers(r.drivers || []);
  renderFeed(state.feed || []);

  // чипы
  $("chip-updated").innerHTML = `Обновлено: <b>${relTime(r.taken_at)}</b>`;
  const cfg = state.config || {};
  toggleChip($("chip-llm"), cfg.llm_enabled, "Claude");
  toggleChip($("chip-x"), cfg.x_live, "X API");

  // источники в подвале
  $("srclist").innerHTML = (state.sources || []).map((s) =>
    `<span class="s"><span class="dot ${s.mode}"></span>${esc(s.name)} · ${s.items_count}</span>`).join("");

  $("skeleton").classList.add("hidden");
  $("app").classList.remove("hidden");
  return true;
}
function toggleChip(el, on, name) {
  el.classList.toggle("on", !!on);
  el.classList.toggle("off", !on);
  el.innerHTML = `<span class="dot"></span>${name}: <b>${on ? "вкл" : "выкл"}</b>`;
}

/* ----------------------------- data flow -------------------------------- */
let pollTimer = null;
async function fetchState() {
  try {
    const res = await fetch("/api/state");
    const state = await res.json();
    const ok = render(state);
    schedulePoll(ok ? 60000 : 5000); // если данных ещё нет — опрашиваем чаще
  } catch (e) {
    schedulePoll(8000);
  }
}
function schedulePoll(ms) {
  clearTimeout(pollTimer);
  pollTimer = setTimeout(fetchState, ms);
}
async function refresh() {
  const btn = $("refresh");
  btn.classList.add("loading"); btn.disabled = true;
  btn.querySelector(".lbl").textContent = "Обновление…";
  try {
    const res = await fetch("/api/refresh", { method: "POST" });
    render(await res.json());
  } catch (e) { /* no-op */ }
  finally {
    btn.classList.remove("loading"); btn.disabled = false;
    btn.querySelector(".lbl").textContent = "Обновить";
  }
}

document.addEventListener("DOMContentLoaded", () => {
  $("refresh").addEventListener("click", refresh);
  fetchState();
});
