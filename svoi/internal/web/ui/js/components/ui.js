// Core UI primitives: Button, IconButton, Chip, Badge, Card, Spinner, Progress,
// Switch, Segmented, Field, EmptyState, Skeleton, Callout, KV, CopyButton.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { copyText, cx } from "../util.js";
import { toast } from "./toast.js";

export function Button({
  variant = "secondary", size = "md", icon, iconRight, loading = false, disabled = false,
  type = "button", href, children, class: cls, block = false, ...rest
}) {
  const className = cx("btn", `btn--${variant}`, size !== "md" && `btn--${size}`, block && "btn--block", loading && "is-loading", cls);
  const inner = html`
    ${loading ? html`<span class="spinner spinner--btn" aria-hidden="true"></span>` : icon && html`<${Icon} name=${icon} size=${size === "sm" ? 16 : 18} />`}
    ${children !== undefined && children !== null && children !== false && html`<span class="btn__label">${children}</span>`}
    ${iconRight && html`<${Icon} name=${iconRight} size=${size === "sm" ? 16 : 18} />`}
  `;
  if (href && !disabled) {
    return html`<a class=${className} href=${href} ...${rest}>${inner}</a>`;
  }
  return html`<button type=${type} class=${className} disabled=${disabled || loading} aria-busy=${loading ? "true" : undefined} ...${rest}>${inner}</button>`;
}

export function IconButton({ icon, label, size = "md", variant = "ghost", badge, active, class: cls, iconSize, href, ...rest }) {
  const className = cx("icon-btn", `icon-btn--${variant}`, size !== "md" && `icon-btn--${size}`, active && "is-active", cls);
  const is = iconSize || (size === "sm" ? 16 : size === "lg" ? 22 : 18);
  const inner = html`<${Icon} name=${icon} size=${is} />${badge ? html`<span class="icon-btn__badge">${badge > 99 ? "99+" : badge}</span>` : null}`;
  if (href) return html`<a class=${className} href=${href} aria-label=${label} title=${label} ...${rest}>${inner}</a>`;
  return html`<button type="button" class=${className} aria-label=${label} title=${label} aria-pressed=${active === undefined ? undefined : String(!!active)} ...${rest}>${inner}</button>`;
}

export function Chip({ tone = "neutral", icon, dot = false, children, class: cls, title, size }) {
  return html`<span class=${cx("chip", `chip--${tone}`, size === "sm" && "chip--sm", cls)} title=${title}>
    ${dot && html`<span class="chip__dot" aria-hidden="true"></span>`}
    ${icon && html`<${Icon} name=${icon} size=${size === "sm" ? 12 : 14} />`}
    <span>${children}</span>
  </span>`;
}

export function Badge({ count, tone = "accent", label }) {
  if (!count) return null;
  return html`<span class=${cx("badge", `badge--${tone}`)} aria-label=${label}>${count > 99 ? "99+" : count}</span>`;
}

export function Card({ children, class: cls, as = "section", pad = true, ...rest }) {
  const Tag = as;
  return html`<${Tag} class=${cx("card", pad && "card--pad", cls)} ...${rest}>${children}</${Tag}>`;
}

export function CardHeader({ title, subtitle, icon, actions, id }) {
  return html`<header class="card__head">
    ${icon && html`<span class="card__icon"><${Icon} name=${icon} size=${18} /></span>`}
    <div class="grow">
      <h2 class="card__title" id=${id}>${title}</h2>
      ${subtitle && html`<p class="card__sub">${subtitle}</p>`}
    </div>
    ${actions && html`<div class="card__actions">${actions}</div>`}
  </header>`;
}

export function Spinner({ size = 18, label }) {
  return html`<span class="spinner" style=${`width:${size}px;height:${size}px`} role=${label ? "status" : undefined} aria-label=${label} aria-hidden=${label ? undefined : "true"}></span>`;
}

export function Progress({ value = 0, max = 100, tone = "accent", indeterminate = false, label, size }) {
  const pct = max > 0 ? Math.max(0, Math.min(100, (value / max) * 100)) : 0;
  return html`<div class=${cx("progress", `progress--${tone}`, indeterminate && "progress--indeterminate", size === "sm" && "progress--sm")}
      role="progressbar" aria-label=${label} aria-valuemin="0" aria-valuemax="100" aria-valuenow=${indeterminate ? undefined : Math.round(pct)}>
    <div class="progress__bar" style=${indeterminate ? "" : `width:${pct}%`}></div>
  </div>`;
}

let switchSeq = 0;
export function Switch({ checked, onChange, label, description, disabled = false, id }) {
  const ref = useRef(id || `sw${++switchSeq}`);
  const sid = ref.current;
  return html`<div class=${cx("switch-row", disabled && "is-disabled")}>
    <div class="grow">
      <label class="switch-row__label" for=${sid}>${label}</label>
      ${description && html`<p class="switch-row__desc" id=${sid + "-d"}>${description}</p>`}
    </div>
    <button type="button" role="switch" id=${sid} class=${cx("switch", checked && "is-on")} aria-checked=${String(!!checked)}
      aria-describedby=${description ? sid + "-d" : undefined} disabled=${disabled}
      onClick=${() => onChange && onChange(!checked)}>
      <span class="switch__thumb"></span>
    </button>
  </div>`;
}

/** Segmented control (radio group semantics, arrow-key navigation). */
export function Segmented({ value, options, onChange, label, size, class: cls, full = false }) {
  const onKey = (e) => {
    const i = options.findIndex((o) => o.value === value);
    let n = -1;
    if (e.key === "ArrowRight" || e.key === "ArrowDown") n = (i + 1) % options.length;
    if (e.key === "ArrowLeft" || e.key === "ArrowUp") n = (i - 1 + options.length) % options.length;
    if (n >= 0) {
      e.preventDefault();
      onChange(options[n].value);
      const btns = e.currentTarget.querySelectorAll("button");
      btns[n] && btns[n].focus();
    }
  };
  return html`<div class=${cx("seg", size === "sm" && "seg--sm", full && "seg--full", cls)} role="radiogroup" aria-label=${label} onKeyDown=${onKey}>
    ${options.map((o) => html`<button type="button" role="radio" key=${o.value} aria-checked=${String(o.value === value)}
        tabindex=${o.value === value ? 0 : -1} class=${cx("seg__opt", o.value === value && "is-on")}
        disabled=${o.disabled} title=${o.title}
        onClick=${() => onChange(o.value)}>
      ${o.icon && html`<${Icon} name=${o.icon} size=${16} />`}<span>${o.label}</span>
    </button>`)}
  </div>`;
}

let fieldSeq = 0;
/** Labelled form field. Children receive the generated id via render prop or `id` attribute. */
export function Field({ label, hint, error, children, id, class: cls, optional }) {
  const ref = useRef(id || `f${++fieldSeq}`);
  const fid = ref.current;
  const content = typeof children === "function" ? children(fid, hint || error ? fid + "-h" : undefined) : children;
  return html`<div class=${cx("field", error && "has-error", cls)}>
    ${label && html`<label class="field__label" for=${fid}>${label}${optional && html` <span class="faint">· ${t("common.optional")}</span>`}</label>`}
    ${content}
    ${error ? html`<p class="field__error" id=${fid + "-h"} role="alert">${error}</p>`
      : hint && html`<p class="field__hint" id=${fid + "-h"}>${hint}</p>`}
  </div>`;
}

export function EmptyState({ icon = "info", title, text, children, tone = "neutral", compact = false, illustration }) {
  return html`<div class=${cx("empty", `empty--${tone}`, compact && "empty--compact")}>
    ${illustration || html`<div class="empty__icon"><${Icon} name=${icon} size=${compact ? 22 : 28} /></div>`}
    ${title && html`<h3 class="empty__title">${title}</h3>`}
    ${text && html`<p class="empty__text">${text}</p>`}
    ${children && html`<div class="empty__actions">${children}</div>`}
  </div>`;
}

export function Skeleton({ w = "100%", h = 14, r = 6, class: cls, style = "" }) {
  return html`<span class=${cx("skeleton", cls)} aria-hidden="true" style=${`width:${typeof w === "number" ? w + "px" : w};height:${h}px;border-radius:${r}px;${style}`}></span>`;
}

export function Callout({ tone = "info", icon, title, children, actions, class: cls, role }) {
  const ic = icon || { info: "info", warn: "alert", err: "alertCircle", ok: "checkCircle", accent: "sparkle" }[tone] || "info";
  return html`<div class=${cx("callout", `callout--${tone}`, cls)} role=${role}>
    <span class="callout__icon"><${Icon} name=${ic} size=${18} /></span>
    <div class="grow">
      ${title && html`<p class="callout__title">${title}</p>`}
      ${children && html`<div class="callout__body">${children}</div>`}
    </div>
    ${actions && html`<div class="callout__actions">${actions}</div>`}
  </div>`;
}

/** Copy-to-clipboard button. `inline` renders a compact icon-only button. */
export function CopyButton({ text, label, children, size = "sm", variant = "ghost", toastText, class: cls }) {
  const [done, setDone] = useState(false);
  const timer = useRef(0);
  useEffect(() => () => clearTimeout(timer.current), []);
  const onClick = async (e) => {
    e.stopPropagation();
    const ok = await copyText(text);
    if (ok) {
      setDone(true);
      clearTimeout(timer.current);
      timer.current = setTimeout(() => setDone(false), 1600);
      if (toastText) toast({ level: "success", title: toastText });
    } else {
      toast({ level: "error", title: t("copy.failed") });
    }
  };
  const lbl = label || t("copy.copy");
  if (!children) {
    return html`<button type="button" class=${cx("icon-btn", `icon-btn--${variant}`, `icon-btn--${size}`, "copy-btn", done && "is-done", cls)}
        aria-label=${done ? t("copy.copied") : lbl} title=${done ? t("copy.copied") : lbl} onClick=${onClick}>
      <${Icon} name=${done ? "check" : "copy"} size=${size === "sm" ? 15 : 17} />
      <span class="sr-only" aria-live="polite">${done ? t("copy.copied") : ""}</span>
    </button>`;
  }
  return html`<${Button} variant=${variant === "ghost" ? "secondary" : variant} size=${size} icon=${done ? "check" : "copy"} onClick=${onClick} class=${cls}>
    ${done ? t("copy.copied") : children}
  </${Button}>`;
}

/** Key/value list. items: [{ k, v, mono, copy, title }] */
export function KV({ items, class: cls }) {
  return html`<dl class=${cx("kv", cls)}>
    ${items.filter(Boolean).map((it) => html`<div class="kv__row" key=${it.k}>
      <dt class="kv__k">${it.k}</dt>
      <dd class=${cx("kv__v", it.mono && "mono", "tnum")}>
        <span class=${cx(it.wrap ? "break" : "ellipsis")} title=${it.title || (typeof it.v === "string" ? it.v : undefined)}>${it.v}</span>
        ${it.copy && html`<${CopyButton} text=${it.copy} label=${t("copy.copyWhat", { what: it.k })} />`}
      </dd>
    </div>`)}
  </dl>`;
}

/** Small visual key: coloured dot + text. */
export function Dot({ tone = "ok", pulse = false, label }) {
  return html`<span class=${cx("dot", `dot--${tone}`, pulse && "dot--pulse")} role=${label ? "img" : undefined} aria-label=${label} aria-hidden=${label ? undefined : "true"}></span>`;
}

/** Textarea that grows with its content (up to max rows via CSS max-height). */
export function AutoTextarea({ value, onInput, class: cls, inputRef, ...rest }) {
  const ref = useRef(null);
  const fit = () => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight + 2, 320) + "px";
  };
  useEffect(fit, [value]);
  return html`<textarea ref=${(el) => { ref.current = el; if (inputRef) inputRef.current = el; }} class=${cx("input", "textarea", cls)} value=${value}
    onInput=${(e) => { onInput && onInput(e); fit(); }} ...${rest}></textarea>`;
}
