// Modal dialog, side drawer / bottom sheet, and promise-based confirm/prompt.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { setState, state, useStore } from "../store.js";
import { cx } from "../util.js";
import { isTopLayer, Portal } from "./portal.js";
import { Button, IconButton } from "./ui.js";

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

let titleSeq = 0;

/** Shared behaviour: focus in on open, restore on close, Esc to close, Tab trap. */
function useDialogBehaviour(ref, onClose, initialFocus) {
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const prev = document.activeElement;
    const node = ref.current;
    // Focus after the portal content is in the DOM.
    const tm = setTimeout(() => {
      if (!node) return;
      const target = (initialFocus && node.querySelector(initialFocus)) ||
        node.querySelector("[autofocus]") ||
        node.querySelector(".modal__body " + FOCUSABLE) ||
        node.querySelector(FOCUSABLE) || node;
      target.focus({ preventScroll: true });
    }, 20);
    const onKey = (e) => {
      const layer = node && node.closest(".portal");
      if (layer && !isTopLayer(layer)) return;
      // Keys typed into a popup menu opened from this dialog belong to the menu.
      if (node && e.target !== document.body && !node.contains(e.target)) return;
      if (e.key === "Escape" && closeRef.current) {
        e.preventDefault();
        e.stopPropagation();
        closeRef.current();
      } else if (e.key === "Tab" && node) {
        const f = Array.from(node.querySelectorAll(FOCUSABLE)).filter((x) => x.offsetParent !== null || x === document.activeElement);
        if (!f.length) return;
        const first = f[0], last = f[f.length - 1];
        if (e.shiftKey && (document.activeElement === first || !node.contains(document.activeElement))) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && (document.activeElement === last || !node.contains(document.activeElement))) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener("keydown", onKey, true);
    return () => {
      clearTimeout(tm);
      document.removeEventListener("keydown", onKey, true);
      if (prev && prev.focus && document.contains(prev)) setTimeout(() => prev.focus({ preventScroll: true }), 0);
    };
  }, []);
}

function ModalInner({ onClose, title, subtitle, icon, children, footer, size, class: cls, closeOnBackdrop, initialFocus, hideClose, tone }) {
  const ref = useRef(null);
  const tid = useRef(`dlg-t${++titleSeq}`);
  useDialogBehaviour(ref, onClose, initialFocus);
  return html`<div class="modal-layer">
    <div class="modal-backdrop" onClick=${() => closeOnBackdrop && onClose && onClose()}></div>
    <div class=${cx("modal", `modal--${size}`, tone && `modal--${tone}`, cls)} role="dialog" aria-modal="true"
        aria-labelledby=${title ? tid.current : undefined} tabindex="-1" ref=${ref}>
      ${(title || !hideClose) && html`<header class="modal__head">
        ${icon && html`<span class=${cx("modal__icon", tone && `modal__icon--${tone}`)}><${Icon} name=${icon} size=${20} /></span>`}
        <div class="grow">
          ${title && html`<h2 class="modal__title" id=${tid.current}>${title}</h2>`}
          ${subtitle && html`<p class="modal__sub">${subtitle}</p>`}
        </div>
        ${!hideClose && onClose && html`<${IconButton} icon="x" label=${t("common.close")} onClick=${onClose} class="modal__close" />`}
      </header>`}
      <div class="modal__body">${children}</div>
      ${footer && html`<footer class="modal__foot">${footer}</footer>`}
    </div>
  </div>`;
}

/**
 * <Modal title onClose footer size="sm|md|lg|xl|full">…</Modal>
 * Rendered into #layers; the rest of the app becomes inert while open.
 */
export function Modal(props) {
  const { size = "md", closeOnBackdrop = true } = props;
  return html`<${Portal} modal=${true} class="portal portal--modal">
    <${ModalInner} ...${props} size=${size} closeOnBackdrop=${closeOnBackdrop} />
  </${Portal}>`;
}

function DrawerInner({ onClose, title, children, label, header, class: cls, footer }) {
  const ref = useRef(null);
  useDialogBehaviour(ref, onClose);
  return html`<div class="drawer-layer">
    <div class="modal-backdrop modal-backdrop--light" onClick=${onClose}></div>
    <aside class=${cx("drawer", cls)} role="dialog" aria-modal="true" aria-label=${label || title} tabindex="-1" ref=${ref}>
      <div class="drawer__grip" aria-hidden="true"></div>
      <header class="drawer__head">
        <div class="grow">${header || html`<h2 class="drawer__title">${title}</h2>`}</div>
        <${IconButton} icon="x" label=${t("common.close")} onClick=${onClose} />
      </header>
      <div class="drawer__body">${children}</div>
      ${footer && html`<footer class="drawer__foot">${footer}</footer>`}
    </aside>
  </div>`;
}

/** Side panel on desktop, bottom sheet on phones. */
export function Drawer(props) {
  return html`<${Portal} modal=${true} class="portal portal--drawer"><${DrawerInner} ...${props} /></${Portal}>`;
}

// ---------- promise-based dialogs ----------
let dlgSeq = 0;

function openDialog(kind, opts) {
  return new Promise((resolve) => {
    const id = ++dlgSeq;
    setState({ dialogs: [...state.dialogs, { id, kind, opts, resolve }] });
  });
}

function closeDialog(id, value) {
  const d = state.dialogs.find((x) => x.id === id);
  setState({ dialogs: state.dialogs.filter((x) => x.id !== id) });
  if (d) d.resolve(value);
}

/**
 * confirmDialog({ title, text, confirmText, danger, requireText, icon }) → Promise<boolean>
 * `requireText`: the user must type this exact text to enable the confirm button.
 */
export function confirmDialog(opts) {
  return openDialog("confirm", opts);
}

/** promptDialog({ title, label, value, placeholder, confirmText, validate, hint }) → Promise<string|null> */
export function promptDialog(opts) {
  return openDialog("prompt", opts);
}

function ConfirmDialog({ d }) {
  const o = d.opts;
  const [typed, setTyped] = useState("");
  const ok = !o.requireText || typed.trim() === o.requireText;
  const done = (v) => closeDialog(d.id, v);
  return html`<${Modal} size="sm" title=${o.title} icon=${o.icon || (o.danger ? "alert" : undefined)} tone=${o.danger ? "danger" : undefined}
      onClose=${() => done(false)}
      footer=${html`
        <${Button} variant="ghost" onClick=${() => done(false)} autofocus=${!!o.danger && !o.requireText}>${o.cancelText || t("common.cancel")}</${Button}>
        <${Button} variant=${o.danger ? "danger" : "primary"} disabled=${!ok} onClick=${() => done(true)} autofocus=${!o.danger && !o.requireText}>
          ${o.confirmText || t("common.ok")}
        </${Button}>`}>
    ${o.text && html`<div class="confirm-text">${o.text}</div>`}
    ${o.requireText && html`<div class="field mt-3">
      <label class="field__label" for=${"req" + d.id}>${o.requireLabel || t("common.typeToConfirm", { text: o.requireText })}</label>
      <input id=${"req" + d.id} class="input" value=${typed} autocomplete="off" spellcheck="false" autofocus
        onInput=${(e) => setTyped(e.target.value)}
        onKeyDown=${(e) => { if (e.key === "Enter" && ok) done(true); }} />
    </div>`}
  </${Modal}>`;
}

function PromptDialog({ d }) {
  const o = d.opts;
  const [val, setVal] = useState(o.value || "");
  const [err, setErr] = useState("");
  const submit = (e) => {
    e && e.preventDefault();
    const v = val.trim();
    const problem = o.validate ? o.validate(v) : (!v && !o.allowEmpty ? t("common.required") : "");
    if (problem) { setErr(problem); return; }
    closeDialog(d.id, v);
  };
  return html`<${Modal} size="sm" title=${o.title} icon=${o.icon} onClose=${() => closeDialog(d.id, null)}
      footer=${html`
        <${Button} variant="ghost" onClick=${() => closeDialog(d.id, null)}>${t("common.cancel")}</${Button}>
        <${Button} variant="primary" onClick=${submit}>${o.confirmText || t("common.save")}</${Button}>`}>
    <form onSubmit=${submit} class="stack">
      ${o.text && html`<p class="muted">${o.text}</p>`}
      <div class=${cx("field", err && "has-error")}>
        ${o.label && html`<label class="field__label" for=${"pr" + d.id}>${o.label}</label>`}
        <input id=${"pr" + d.id} class="input" value=${val} placeholder=${o.placeholder || ""} autofocus
          autocomplete="off" spellcheck="false" maxlength=${o.maxLength || 200}
          onInput=${(e) => { setVal(e.target.value); setErr(""); }}
          onFocus=${(e) => { if (o.selectBase) { const v = e.target.value; const i = v.lastIndexOf("."); e.target.setSelectionRange(0, i > 0 ? i : v.length); } }} />
        ${err ? html`<p class="field__error" role="alert">${err}</p>` : o.hint && html`<p class="field__hint">${o.hint}</p>`}
      </div>
    </form>
  </${Modal}>`;
}

/** Renders the stack of promise-based dialogs. Mount once at the app root. */
export function DialogHost() {
  const dialogs = useStore((s) => s.dialogs);
  return html`${dialogs.map((d) => d.kind === "confirm"
    ? html`<${ConfirmDialog} key=${d.id} d=${d} />`
    : html`<${PromptDialog} key=${d.id} d=${d} />`)}`;
}
