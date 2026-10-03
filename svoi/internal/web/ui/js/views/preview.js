// File preview lightbox with prev/next. Safe by construction:
//  - images and SVG go through <img> (no script execution),
//  - text/markdown/json is fetched and shown as text in <pre>,
//  - PDFs are fetched and re-wrapped as a Blob with a forced application/pdf
//    type before going into an <iframe>, so a lying server cannot make it HTML,
//  - nothing is ever navigated to directly on the UI origin.
import { html, useEffect, useLayoutEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { fmtBytes, fmtDateTime } from "../format.js";
import { cx, previewKind } from "../util.js";
import { FileIcon } from "../components/avatar.js";
import { Button, IconButton, Spinner } from "../components/ui.js";
import { Modal } from "../components/modal.js";

const TEXT_LIMIT = 256 * 1024;

function TextPreview({ url, name }) {
  const [st, setSt] = useState({ loading: true, text: "", cut: false, err: null });
  useEffect(() => {
    const ac = new AbortController();
    setSt({ loading: true, text: "", cut: false, err: null });
    fetch(url, { headers: { Range: `bytes=0-${TEXT_LIMIT - 1}` }, signal: ac.signal, credentials: "same-origin" })
      .then(async (r) => {
        if (!r.ok && r.status !== 206) throw new Error(String(r.status));
        let text = await r.text();
        const cut = r.status === 206 && /\/(\d+)$/.test(r.headers.get("content-range") || "") &&
          Number(RegExp.$1) > TEXT_LIMIT;
        if (/\.json$/i.test(name) && !cut) {
          try { text = JSON.stringify(JSON.parse(text), null, 2); } catch { /* keep as is */ }
        }
        setSt({ loading: false, text, cut, err: null });
      })
      .catch((e) => { if (e.name !== "AbortError") setSt({ loading: false, text: "", cut: false, err: e }); });
    return () => ac.abort();
  }, [url]);
  if (st.loading) return html`<div class="pv__center"><${Spinner} size=${24} label=${t("common.loading")} /></div>`;
  if (st.err) return html`<div class="pv__center pv__msg"><${Icon} name="alertCircle" size=${28} /><p>${t("pv.loadFailed")}</p></div>`;
  return html`<div class="pv__text">
    <pre tabindex="0">${st.text}</pre>
    ${st.cut && html`<p class="pv__cut">${t("pv.textCut")}</p>`}
  </div>`;
}

function PdfPreview({ url }) {
  const [st, setSt] = useState({ src: "", err: null });
  useEffect(() => {
    let obj = "";
    const ac = new AbortController();
    setSt({ src: "", err: null });
    fetch(url, { signal: ac.signal, credentials: "same-origin" })
      .then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.arrayBuffer(); })
      .then((buf) => {
        obj = URL.createObjectURL(new Blob([buf], { type: "application/pdf" }));
        setSt({ src: obj, err: null });
      })
      .catch((e) => { if (e.name !== "AbortError") setSt({ src: "", err: e }); });
    return () => { ac.abort(); if (obj) URL.revokeObjectURL(obj); };
  }, [url]);
  if (st.err) return html`<div class="pv__center pv__msg"><${Icon} name="alertCircle" size=${28} /><p>${t("pv.loadFailed")}</p></div>`;
  if (!st.src) return html`<div class="pv__center"><${Spinner} size=${24} label=${t("common.loading")} /></div>`;
  return html`<iframe class="pv__pdf" src=${st.src} title=${t("pv.pdf")}></iframe>`;
}

function MediaError({ item }) {
  return html`<div class="pv__center pv__msg">
    <${FileIcon} name=${item.name} mime=${item.mime} boxed size=${72} />
    <p class="strong">${t("pv.cantPlay")}</p>
    <p class="muted small">${t("pv.cantPlayHint")}</p>
    ${item.dlUrl && html`<${Button} variant="primary" icon="download" href=${item.dlUrl} download=${item.name}>${t("common.download")}</${Button}>`}
  </div>`;
}

// Media previews are keyed by URL by the caller, so their state starts fresh for
// every file — no "reset" effects that could race with load/error events.
function ImagePreview({ item }) {
  const [loaded, setLoaded] = useState(false);
  const [err, setErr] = useState(false);
  const img = useRef(null);
  // A cached/loopback image may finish loading before our listeners matter.
  useLayoutEffect(() => {
    const el = img.current;
    if (el && el.complete) {
      if (el.naturalWidth > 0) setLoaded(true);
      else if (el.currentSrc) setErr(true);
    }
  }, []);
  if (err) return html`<${MediaError} item=${item} />`;
  return html`<div class="pv__img-wrap">
    ${!loaded && html`<div class="pv__center pv__abs"><${Spinner} size=${24} /></div>`}
    <img ref=${img} class=${cx("pv__img", loaded && "is-loaded")} src=${item.url} alt=${item.name} decoding="async"
      onLoad=${() => setLoaded(true)} onError=${() => setErr(true)} />
  </div>`;
}

function VideoPreview({ item }) {
  const [err, setErr] = useState(false);
  if (err) return html`<${MediaError} item=${item} />`;
  return html`<video class="pv__video" src=${item.url} controls preload="metadata" playsinline onError=${() => setErr(true)}></video>`;
}

function AudioPreview({ item }) {
  const [err, setErr] = useState(false);
  return html`<div class="pv__center pv__audio">
    <div class="pv__disc"><${Icon} name="music" size=${44} /></div>
    <p class="strong break center">${item.name}</p>
    ${err ? html`<p class="danger-text small">${t("pv.cantPlay")}</p>`
      : html`<audio src=${item.url} controls preload="metadata" onError=${() => setErr(true)}></audio>`}
  </div>`;
}

function NoPreview({ item }) {
  return html`<div class="pv__center pv__msg">
    <${FileIcon} name=${item.name} mime=${item.mime} boxed size=${80} />
    <p class="strong">${t("pv.none")}</p>
    <p class="muted small">${[item.size !== undefined && fmtBytes(item.size), item.mime].filter(Boolean).join(" · ")}</p>
    ${item.dlUrl && html`<${Button} variant="primary" icon="download" href=${item.dlUrl} download=${item.name}>${t("common.download")}</${Button}>`}
  </div>`;
}

/**
 * items: [{ name, mime, size, mtime, url, dlUrl }]
 */
export function PreviewModal({ items, index, onIndex, onClose, extraActions }) {
  const item = items[index];
  const touch = useRef(null);
  // The key handler is registered once and reads the latest values from a ref,
  // so a quick ←/→ right after navigating never uses a stale index.
  const cur = useRef({ index, items, onIndex });
  cur.current = { index, items, onIndex };
  const go = (d) => {
    const c = cur.current;
    if (c.items.length < 2) return;
    const next = (c.index + d + c.items.length) % c.items.length;
    c.index = next;
    c.onIndex(next);
  };
  useEffect(() => {
    const onKey = (e) => {
      if (e.target && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
      if (e.target && (e.target.tagName === "VIDEO" || e.target.tagName === "AUDIO") && (e.key === "ArrowLeft" || e.key === "ArrowRight")) return;
      if (e.key === "ArrowRight") { e.preventDefault(); go(1); }
      if (e.key === "ArrowLeft") { e.preventDefault(); go(-1); }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);
  if (!item) return null;
  const kind = previewKind(item.name, item.mime);
  let body;
  if (kind === "image") body = html`<${ImagePreview} item=${item} key=${item.url} />`;
  else if (kind === "video") body = html`<${VideoPreview} item=${item} key=${item.url} />`;
  else if (kind === "audio") body = html`<${AudioPreview} item=${item} key=${item.url} />`;
  else if (kind === "text") body = html`<${TextPreview} url=${item.url} name=${item.name} key=${item.url} />`;
  else if (kind === "pdf") body = html`<${PdfPreview} url=${item.url} key=${item.url} />`;
  else body = html`<${NoPreview} item=${item} key=${item.url} />`;

  const head = html`<div class="pv__bar">
    <${FileIcon} name=${item.name} mime=${item.mime} boxed size=${36} />
    <div class="grow">
      <h2 class="pv__title ellipsis" title=${item.name} data-testid="preview-name">${item.name}</h2>
      <p class="pv__sub tnum">${[item.size !== undefined && fmtBytes(item.size), item.mtime && fmtDateTime(item.mtime), items.length > 1 && t("pv.counter", { i: index + 1, n: items.length })].filter(Boolean).join(" · ")}</p>
    </div>
    ${extraActions}
    ${item.dlUrl && html`<${IconButton} icon="download" label=${t("common.download")} href=${item.dlUrl} download=${item.name} />`}
    <${IconButton} icon="x" label=${t("common.close")} onClick=${onClose} />
  </div>`;

  return html`<${Modal} size="full" class="pv" onClose=${onClose} hideClose=${true} initialFocus=".pv__stage" label=${item.name} testid="preview">
    ${head}
    <div class=${cx("pv__stage", `pv__stage--${kind || "none"}`)} tabindex="-1"
        onTouchStart=${(e) => { touch.current = e.touches[0].clientX; }}
        onTouchEnd=${(e) => {
          if (touch.current === null || kind === "pdf" || kind === "text") return;
          const dx = e.changedTouches[0].clientX - touch.current;
          touch.current = null;
          if (Math.abs(dx) > 60) go(dx < 0 ? 1 : -1);
        }}>
      ${body}
      ${items.length > 1 && html`
        <button type="button" class="pv__nav pv__nav--prev" aria-label=${t("pv.prev")} onClick=${() => go(-1)} data-testid="preview-prev"><${Icon} name="chevronLeft" size=${24} /></button>
        <button type="button" class="pv__nav pv__nav--next" aria-label=${t("pv.next")} onClick=${() => go(1)} data-testid="preview-next"><${Icon} name="chevronRight" size=${24} /></button>`}
    </div>
  </${Modal}>`;
}
