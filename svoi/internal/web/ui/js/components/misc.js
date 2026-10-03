// Assorted building blocks: page header, tabs, drop zone, linkified text,
// relative time that ticks, and the access ("who may use") editor.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { useNow } from "../hooks.js";
import { fmtAgo, fmtDateTime } from "../format.js";
import { cx, linkify } from "../util.js";
import { DeviceChips } from "./devicepicker.js";
import { Segmented } from "./ui.js";

export function PageHeader({ title, subtitle, actions, back, children }) {
  return html`<header class="page-head">
    ${back && html`<a class="page-head__back icon-btn icon-btn--ghost" href=${back} aria-label=${t("common.back")} title=${t("common.back")}><${Icon} name="arrowLeft" size=${20} /></a>`}
    <div class="grow page-head__text">
      <h1 class="page-head__title">${title}</h1>
      ${subtitle && html`<p class="page-head__sub">${subtitle}</p>`}
    </div>
    ${actions && html`<div class="page-head__actions">${actions}</div>`}
    ${children}
  </header>`;
}

/** Route-driven tabs: items [{ id, label, href, icon, badge }] */
export function Tabs({ items, active, label, class: cls, testid }) {
  const onKey = (e) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    const links = Array.from(e.currentTarget.querySelectorAll("a"));
    const i = links.indexOf(document.activeElement);
    const n = e.key === "ArrowRight" ? (i + 1) % links.length : (i - 1 + links.length) % links.length;
    links[n].focus();
    e.preventDefault();
  };
  return html`<nav class=${cx("tabs", cls)} aria-label=${label} onKeyDown=${onKey}>
    ${items.map((it) => html`<a key=${it.id} href=${it.href} class=${cx("tabs__tab", active === it.id && "is-active")} data-testid=${testid ? `${testid}-${it.id}` : undefined}
        aria-current=${active === it.id ? "page" : undefined}>
      ${it.icon && html`<${Icon} name=${it.icon} size=${16} />`}
      <span>${it.label}</span>
      ${it.badge ? html`<span class=${`badge badge--${it.badgeTone || "accent"}`}>${it.badge}</span>` : null}
    </a>`)}
  </nav>`;
}

/** Plain-text body with line breaks kept and http(s) links made clickable. */
export function Linkified({ text, class: cls }) {
  return html`<div class=${cx("plain-text", cls)}>${linkify(text || "")}</div>`;
}

/** Relative time that refreshes itself; full date in the tooltip. */
export function Ago({ ts, prefix }) {
  useNow(30000);
  if (!ts) return html`<span>${t("time.never")}</span>`;
  return html`<time datetime=${new Date(ts * 1000).toISOString()} title=${fmtDateTime(ts)}>${prefix}${fmtAgo(ts)}</time>`;
}

function hasFiles(e) {
  const dt = e.dataTransfer;
  if (!dt) return false;
  return Array.from(dt.types || []).includes("Files");
}

/**
 * Drop zone: drag&drop + click to pick. onFiles(File[]).
 * `whole` makes the zone a passive overlay target (no click to open picker).
 */
export function DropZone({ onFiles, title, hint, icon = "upload", multiple = true, compact = false, disabled = false, children, class: cls }) {
  const [over, setOver] = useState(false);
  const input = useRef(null);
  const depth = useRef(0);
  const pick = () => input.current && input.current.click();
  return html`<div class=${cx("dropzone", over && "is-over", compact && "dropzone--compact", disabled && "is-disabled", cls)}
      onDragEnter=${(e) => { if (!hasFiles(e) || disabled) return; e.preventDefault(); depth.current++; setOver(true); }}
      onDragOver=${(e) => { if (!hasFiles(e) || disabled) return; e.preventDefault(); e.dataTransfer.dropEffect = "copy"; }}
      onDragLeave=${() => { depth.current = Math.max(0, depth.current - 1); if (!depth.current) setOver(false); }}
      onDrop=${(e) => {
        if (!hasFiles(e) || disabled) return;
        e.preventDefault();
        depth.current = 0;
        setOver(false);
        const files = Array.from(e.dataTransfer.files || []);
        if (files.length) onFiles(files);
      }}>
    <input type="file" ref=${input} class="sr-only" tabindex="-1" aria-hidden="true" multiple=${multiple} data-testid="dropzone-input"
      onChange=${(e) => { const f = Array.from(e.target.files || []); e.target.value = ""; if (f.length) onFiles(f); }} />
    <button type="button" class="dropzone__btn" onClick=${pick} disabled=${disabled}>
      <span class="dropzone__icon"><${Icon} name=${icon} size=${compact ? 22 : 28} /></span>
      <span class="dropzone__title">${over ? t("drop.release") : title || t("drop.title")}</span>
      ${hint && html`<span class="dropzone__hint">${hint}</span>`}
    </button>
    ${children}
  </div>`;
}

/** Window-level drop target overlay while dragging files over a view. */
export function useFileDrop(onFiles, active = true) {
  const [over, setOver] = useState(false);
  const cb = useRef(onFiles);
  cb.current = onFiles;
  useEffect(() => {
    if (!active) return undefined;
    let depth = 0;
    const enter = (e) => { if (!hasFiles(e)) return; e.preventDefault(); depth++; setOver(true); };
    const overFn = (e) => { if (!hasFiles(e)) return; e.preventDefault(); };
    const leave = () => { depth = Math.max(0, depth - 1); if (!depth) setOver(false); };
    const drop = (e) => {
      if (!hasFiles(e)) return;
      depth = 0;
      setOver(false);
      if (e.defaultPrevented) return; // a nested drop zone already took the files
      e.preventDefault();
      const files = Array.from(e.dataTransfer.files || []);
      if (files.length) cb.current(files);
    };
    window.addEventListener("dragenter", enter);
    window.addEventListener("dragover", overFn);
    window.addEventListener("dragleave", leave);
    window.addEventListener("drop", drop);
    return () => {
      window.removeEventListener("dragenter", enter);
      window.removeEventListener("dragover", overFn);
      window.removeEventListener("dragleave", leave);
      window.removeEventListener("drop", drop);
    };
  }, [active]);
  return over;
}

/** Access editor for shares/services: everyone ("*") or a list of device ids. */
export function AccessEditor({ value, onChange, peers }) {
  const everyone = !value || value.includes("*");
  return html`<div class="stack stack--sm">
    <${Segmented} label=${t("access.label")} value=${everyone ? "all" : "some"} full
      options=${[{ value: "all", label: t("access.everyone"), icon: "devices" }, { value: "some", label: t("access.selected"), icon: "check" }]}
      onChange=${(v) => onChange(v === "all" ? ["*"] : [])} />
    ${!everyone && html`<${DeviceChips} value=${value} onChange=${onChange} peers=${peers} label=${t("access.selected")} />`}
    ${!everyone && value.length === 0 && html`<p class="field__hint">${t("access.noneHint")}</p>`}
  </div>`;
}
