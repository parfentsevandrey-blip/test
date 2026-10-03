// Log viewer: polls GET [d/:id/]diag/logs while visible, with level filter,
// text filter, pause, copy and download.
import { html, useEffect, useLayoutEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { devPrefix, get } from "../api.js";
import { useInterval } from "../hooks.js";
import { copyText, cx } from "../util.js";
import { Button, IconButton, Segmented, Spinner } from "../components/ui.js";
import { toast } from "../components/toast.js";

const LEVELS = ["all", "info", "warn", "error"];
const RANK = { debug: 0, info: 1, warn: 2, warning: 2, error: 3 };

function stamp(ts) {
  const d = new Date(ts * 1000);
  const p = (x) => String(x).padStart(2, "0");
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function LogViewer({ dev }) {
  const [lines, setLines] = useState(null);
  const [err, setErr] = useState(null);
  const [level, setLevel] = useState("all");
  const [q, setQ] = useState("");
  const [paused, setPaused] = useState(false);
  const box = useRef(null);
  const stick = useRef(true);

  const load = async () => {
    try {
      const r = await get(`${devPrefix(dev)}diag/logs?limit=300`);
      setLines(r.lines || []);
      setErr(null);
    } catch (e) { setErr(e); }
  };
  useEffect(() => { setLines(null); load(); }, [dev]);
  useInterval(load, 3000, !paused);

  useLayoutEffect(() => {
    const el = box.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [lines, level, q]);

  const min = level === "all" ? 0 : RANK[level];
  const shown = (lines || []).filter((l) => (RANK[l.level] ?? 1) >= min && (!q || l.msg.toLowerCase().includes(q.toLowerCase())));
  const asText = () => shown.map((l) => `${new Date(l.ts * 1000).toISOString()} ${String(l.level).toUpperCase().padEnd(5)} ${l.msg}`).join("\n");
  const download = () => {
    const url = URL.createObjectURL(new Blob([asText() + "\n"], { type: "text/plain" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `themesh-log-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-")}.txt`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return html`<div class="logs">
    <div class="logs__bar">
      <${Segmented} size="sm" label=${t("logs.level")} value=${level} onChange=${setLevel}
        options=${LEVELS.map((l) => ({ value: l, label: t("logs.lv." + l) }))} />
      <div class="input-wrap logs__q"><${Icon} name="search" size=${15} />
        <input class="input" type="search" value=${q} placeholder=${t("logs.filter")} aria-label=${t("logs.filter")} onInput=${(e) => setQ(e.target.value)} /></div>
      <span class="grow"></span>
      <${IconButton} icon=${paused ? "play" : "pause"} label=${paused ? t("logs.resume") : t("logs.pause")} active=${paused} onClick=${() => setPaused(!paused)} />
      <${IconButton} icon="copy" label=${t("logs.copy")} onClick=${async () => { if (await copyText(asText())) toast({ level: "success", title: t("copy.copied") }); }} />
      <${IconButton} icon="download" label=${t("logs.download")} onClick=${download} />
    </div>
    <div class="logs__box" ref=${box} tabindex="0" role="log" aria-label=${t("logs.title")} aria-live="off"
        onScroll=${(e) => { const el = e.currentTarget; stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40; }}>
      ${lines === null && !err && html`<div class="logs__empty"><${Spinner} size=${16} /></div>`}
      ${err && html`<div class="logs__empty danger-text">${t("err." + err.code)} <${Button} size="sm" variant="ghost" onClick=${load}>${t("common.retry")}</${Button}></div>`}
      ${lines !== null && !shown.length && html`<div class="logs__empty faint">${t("logs.empty")}</div>`}
      ${shown.map((l, i) => html`<div key=${i} class=${cx("logline", `logline--${l.level}`)}>
        <span class="logline__ts">${stamp(l.ts)}</span><span class="logline__lv">${String(l.level).toUpperCase()}</span><span class="logline__msg">${l.msg}</span>
      </div>`)}
    </div>
    <p class="faint xsmall">${paused ? t("logs.paused") : t("logs.live")}</p>
  </div>`;
}
