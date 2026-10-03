// Toasts: toast({ level, title, text, link, actionLabel, onAction, timeout }).
// Rendered into #layers so they stay usable while a modal makes the app inert.
import { html, useEffect, useRef } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { setState, state, useStore } from "../store.js";
import { cx } from "../util.js";
import { Portal } from "./portal.js";

let seq = 0;
const timers = new Map();

export function toast({ level = "info", title = "", text = "", link = null, actionLabel, onAction, timeout } = {}) {
  const id = ++seq;
  const ms = timeout !== undefined ? timeout : level === "error" ? 8000 : level === "warn" ? 6500 : 4500;
  // Collapse identical consecutive toasts (e.g. repeated errors).
  const dup = state.toasts.find((x) => x.title === title && x.text === text && x.level === level);
  if (dup) dismiss(dup.id);
  setState({ toasts: [...state.toasts.slice(-4), { id, level, title, text, link, actionLabel, onAction, ms }] });
  if (ms > 0) timers.set(id, setTimeout(() => dismiss(id), ms));
  return id;
}

export function dismiss(id) {
  clearTimeout(timers.get(id));
  timers.delete(id);
  setState({ toasts: state.toasts.filter((x) => x.id !== id) });
}

/** Show an API error as a toast, mapping its code to friendly text. */
export function toastError(err, title) {
  if (!err || err.code === "aborted" || err.name === "AbortError") return;
  const code = err.code || "internal";
  const mapped = t("err." + code);
  toast({
    level: "error",
    title: title || mapped,
    text: title ? mapped : (code === "invalid" || code === "internal" ? err.message || "" : ""),
  });
}

const ICON = { info: "info", success: "checkCircle", warn: "alert", error: "alertCircle" };

function ToastItem({ x }) {
  const ref = useRef(null);
  // Pause auto-dismiss while hovered or focused.
  const pause = () => { clearTimeout(timers.get(x.id)); };
  const resume = () => { if (x.ms > 0) timers.set(x.id, setTimeout(() => dismiss(x.id), 2500)); };
  useEffect(() => { /* mount animation handled by CSS */ }, []);
  return html`<div class=${cx("toast", `toast--${x.level}`)} role=${x.level === "error" ? "alert" : "status"} ref=${ref} data-testid="toast" data-level=${x.level}
      onMouseEnter=${pause} onMouseLeave=${resume} onfocusin=${pause} onfocusout=${resume}>
    <span class="toast__icon"><${Icon} name=${ICON[x.level] || "info"} size=${18} /></span>
    <div class="toast__content">
      ${x.title && html`<p class="toast__title">${x.title}</p>`}
      ${x.text && html`<p class="toast__text">${x.text}</p>`}
      ${(x.link || x.onAction) && html`<div class="toast__actions">
        ${x.link && html`<a class="toast__link" href=${x.link} onClick=${() => dismiss(x.id)}>${x.actionLabel || t("common.open")}</a>`}
        ${!x.link && x.onAction && html`<button type="button" class="toast__link" onClick=${() => { dismiss(x.id); x.onAction(); }}>${x.actionLabel}</button>`}
      </div>`}
    </div>
    <button type="button" class="toast__close" aria-label=${t("common.close")} onClick=${() => dismiss(x.id)}>
      <${Icon} name="x" size=${16} />
    </button>
  </div>`;
}

export function ToastHost() {
  const toasts = useStore((s) => s.toasts);
  return html`<${Portal} class="portal portal--toasts">
    <section class="toasts" aria-label=${t("common.notifications")} aria-live="polite">
      ${toasts.map((x) => html`<${ToastItem} key=${x.id} x=${x} />`)}
    </section>
  </${Portal}>`;
}
