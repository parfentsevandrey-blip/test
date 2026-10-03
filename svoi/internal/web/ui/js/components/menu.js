// Dropdown menu (menu button pattern) rendered in a portal so it is never
// clipped by scroll containers. Arrow keys / Home / End / Esc supported.
import { html, useEffect, useLayoutEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { cx } from "../util.js";
import { Portal } from "./portal.js";

let menuSeq = 0;

/**
 * items: [{ label, icon, onClick, href, danger, disabled, checked, hint, divider, heading }]
 * trigger: ({ ref, onClick, onKeyDown, "aria-expanded", "aria-haspopup", "aria-controls" }) => vnode
 */
export function Menu({ items, trigger, align = "end", width, label, class: cls }) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState(null);
  const anchor = useRef(null);
  const menuRef = useRef(null);
  const id = useRef(`menu${++menuSeq}`);
  const focusFirst = useRef(true);

  const place = () => {
    const a = anchor.current;
    if (!a) return;
    const r = a.getBoundingClientRect();
    const vw = window.innerWidth, vh = window.innerHeight;
    const m = menuRef.current;
    const mh = m ? m.offsetHeight : 240;
    const mw = m ? m.offsetWidth : (width || 220);
    let top = r.bottom + 6;
    if (top + mh > vh - 8 && r.top - mh - 6 > 8) top = r.top - mh - 6;
    let left = align === "end" ? r.right - mw : r.left;
    left = Math.max(8, Math.min(left, vw - mw - 8));
    setPos({ top: Math.max(8, top), left });
  };

  useLayoutEffect(() => { if (open) place(); }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (menuRef.current && menuRef.current.contains(e.target)) return;
      if (anchor.current && anchor.current.contains(e.target)) return;
      setOpen(false);
    };
    const onMove = () => place();
    document.addEventListener("pointerdown", onDown, true);
    window.addEventListener("resize", onMove);
    window.addEventListener("scroll", onMove, true);
    // focus first item once rendered
    const tm = setTimeout(() => {
      place();
      const its = menuRef.current ? menuRef.current.querySelectorAll('[role^="menuitem"]:not([disabled])') : [];
      if (its.length) (focusFirst.current ? its[0] : its[its.length - 1]).focus();
    }, 10);
    return () => {
      clearTimeout(tm);
      document.removeEventListener("pointerdown", onDown, true);
      window.removeEventListener("resize", onMove);
      window.removeEventListener("scroll", onMove, true);
    };
  }, [open]);

  const close = (refocus = true) => {
    setOpen(false);
    if (refocus && anchor.current) anchor.current.focus();
  };

  const onMenuKey = (e) => {
    const its = Array.from(menuRef.current.querySelectorAll('[role^="menuitem"]:not([disabled])'));
    const i = its.indexOf(document.activeElement);
    if (e.key === "ArrowDown") { e.preventDefault(); its[(i + 1) % its.length].focus(); }
    else if (e.key === "ArrowUp") { e.preventDefault(); its[(i - 1 + its.length) % its.length].focus(); }
    else if (e.key === "Home") { e.preventDefault(); its[0].focus(); }
    else if (e.key === "End") { e.preventDefault(); its[its.length - 1].focus(); }
    else if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); close(); }
    else if (e.key === "Tab") { setOpen(false); }
  };

  const triggerProps = {
    ref: anchor,
    onClick: (e) => { e.stopPropagation(); focusFirst.current = true; setOpen((o) => !o); },
    onKeyDown: (e) => {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        focusFirst.current = e.key === "ArrowDown";
        setOpen(true);
      }
    },
    "aria-expanded": open ? "true" : "false",
    "aria-haspopup": "menu",
    "aria-controls": open ? id.current : undefined,
  };

  return html`${trigger(triggerProps)}
    ${open && html`<${Portal} class="portal portal--menu">
      <div class=${cx("menu", cls)} role="menu" id=${id.current} aria-label=${label} ref=${menuRef}
          style=${pos ? `top:${pos.top}px;left:${pos.left}px;${width ? `width:${width}px;` : ""}` : `visibility:hidden;${width ? `width:${width}px;` : ""}`}
          onKeyDown=${onMenuKey}>
        ${items.filter(Boolean).map((it, i) => {
          if (it.divider) return html`<div class="menu__divider" role="separator" key=${"d" + i}></div>`;
          if (it.heading) return html`<div class="menu__heading" key=${"h" + i} role="presentation">${it.heading}</div>`;
          const role = it.checked !== undefined ? "menuitemradio" : "menuitem";
          const content = html`
            ${it.icon ? html`<${Icon} name=${it.icon} size=${16} />` : it.checked !== undefined ? html`<span class="menu__check">${it.checked ? html`<${Icon} name="check" size=${16} />` : null}</span>` : null}
            <span class="menu__label">${it.label}${it.hint && html`<span class="menu__hint">${it.hint}</span>`}</span>
            ${it.checked && it.icon ? html`<${Icon} name="check" size=${16} class="menu__tick" />` : null}`;
          if (it.href && !it.disabled) {
            return html`<a key=${i} role=${role} class=${cx("menu__item", it.danger && "is-danger")} href=${it.href} tabindex="-1"
              aria-checked=${it.checked !== undefined ? String(!!it.checked) : undefined}
              onClick=${() => close(false)}>${content}</a>`;
          }
          return html`<button key=${i} type="button" role=${role} tabindex="-1" class=${cx("menu__item", it.danger && "is-danger")}
              disabled=${it.disabled} aria-checked=${it.checked !== undefined ? String(!!it.checked) : undefined}
              onClick=${() => { close(true); it.onClick && it.onClick(); }}>${content}</button>`;
        })}
      </div>
    </${Portal}>`}`;
}
