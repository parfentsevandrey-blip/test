// Transfers: list grouped by what needs attention, rows with progress and
// actions, and the global "incoming file" banner.
import { html, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn, tx } from "../i18n.js";
import { del, post, transferFileUrl } from "../api.js";
import { useStore, upsertTransfer } from "../store.js";
import { fmtBytes, fmtEta, fmtPercent, fmtSpeed } from "../format.js";
import { cx, previewKind } from "../util.js";
import { FileIcon } from "../components/avatar.js";
import { Ago } from "../components/misc.js";
import { Button, Chip, EmptyState, IconButton, Progress } from "../components/ui.js";
import { confirmDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { PreviewModal } from "./preview.js";

const LIVE = new Set(["offered", "queued", "active"]);

export function isIncomingOffer(tr) {
  return tr.dir === "in" && tr.state === "offered";
}

async function act(tr, action) {
  try {
    const r = await post(`transfers/${encodeURIComponent(tr.id)}/${action}`, {});
    if (r && r.id) upsertTransfer(r);
  } catch (e) {
    toastError(e);
  }
}

function stateChip(tr) {
  const out = tr.dir === "out";
  switch (tr.state) {
    case "offered": return out ? html`<${Chip} tone="info" size="sm">${t("tr.st.waitAccept")}</${Chip}>` : html`<${Chip} tone="warn" size="sm">${t("tr.st.offered")}</${Chip}>`;
    case "queued": return html`<${Chip} tone="neutral" size="sm" icon="clock">${out ? t("tr.st.waitDevice") : t("tr.st.queued")}</${Chip}>`;
    case "active": return html`<${Chip} tone="accent" size="sm">${t("tr.st.active")}</${Chip}>`;
    case "done": return html`<${Chip} tone="ok" size="sm" icon="check">${out ? t("tr.st.delivered") : t("tr.st.received")}</${Chip}>`;
    case "failed": return html`<${Chip} tone="err" size="sm" icon="alertCircle">${t("tr.st.failed")}</${Chip}>`;
    case "declined": return html`<${Chip} tone="muted" size="sm">${t("tr.st.declined")}</${Chip}>`;
    case "canceled": return html`<${Chip} tone="muted" size="sm">${t("tr.st.canceled")}</${Chip}>`;
    default: return null;
  }
}

function detail(tr) {
  const out = tr.dir === "out";
  const who = tr.peerName || (tr.peer || "").slice(0, 8);
  switch (tr.state) {
    case "offered": return out ? t("tr.d.waitAccept", { who }) : t("tr.d.offered", { who });
    case "queued": return out ? t("tr.d.waitDevice", { who }) : t("tr.d.queuedIn", { who });
    case "active": {
      const eta = fmtEta(tr.size - tr.done, tr.speed);
      return [fmtPercent(tr.done, tr.size), tr.speed ? fmtSpeed(tr.speed) : "", eta && t("tr.d.eta", { eta })].filter(Boolean).join(" · ");
    }
    case "done": return html`${out ? t("tr.d.delivered") : t("tr.d.received")} <${Ago} ts=${tr.finished || tr.updated} />`;
    case "failed": return tr.error ? t("tr.d.failedWhy", { why: tr.error }) : t("tr.d.failed");
    case "declined": return out ? t("tr.d.declinedOut", { who }) : t("tr.d.declinedIn");
    case "canceled": return t("tr.d.canceled");
    default: return "";
  }
}

export function TransferRow({ tr, compact = false }) {
  const out = tr.dir === "out";
  const [busy, setBusy] = useState("");
  const [preview, setPreview] = useState(false);
  const run = async (a) => { setBusy(a); await act(tr, a); setBusy(""); };
  const remove = async () => {
    try { await del(`transfers/${encodeURIComponent(tr.id)}`); } catch (e) { toastError(e); }
  };
  const showProgress = tr.state === "active" || ((tr.state === "queued" || tr.state === "failed") && tr.done > 0 && tr.size > 0);
  const canOpen = !out && tr.state === "done";
  const pk = previewKind(tr.name, tr.mime);
  return html`<li class=${cx("tr", `tr--${tr.state}`, isIncomingOffer(tr) && "tr--offer", compact && "tr--compact")}>
    <div class="tr__icon">
      <${FileIcon} name=${tr.name} mime=${tr.mime} boxed size=${40} />
      <span class=${cx("tr__dir", out ? "is-out" : "is-in")} title=${out ? t("tr.out") : t("tr.in")}><${Icon} name=${out ? "arrowOut" : "arrowIn"} size=${11} strokeWidth=${2.4} /></span>
    </div>
    <div class="tr__main">
      <div class="tr__top">
        <span class="tr__name ellipsis" title=${tr.name}>${tr.name}</span>
        ${stateChip(tr)}
      </div>
      <div class="tr__meta">
        <span class="tr__peer">${out ? "→" : "←"} ${tr.peerName || (tr.peer || "").slice(0, 8)}</span>
        <span class="tnum">${tr.size ? fmtBytes(tr.size) : ""}</span>
        <span class=${cx("tr__detail", tr.state === "failed" && "danger-text")}>${detail(tr)}</span>
      </div>
      ${showProgress && html`<${Progress} value=${tr.done} max=${tr.size || 1} size="sm" tone=${tr.state === "failed" ? "err" : tr.state === "active" ? "accent" : "muted"} label=${tr.name} />`}
    </div>
    <div class="tr__actions">
      ${isIncomingOffer(tr) && html`
        <${Button} size="sm" variant="primary" icon="check" loading=${busy === "accept"} onClick=${() => run("accept")}>${t("tr.accept")}</${Button}>
        <${Button} size="sm" variant="ghost" loading=${busy === "decline"} onClick=${() => run("decline")}>${t("tr.decline")}</${Button}>`}
      ${canOpen && pk && html`<${IconButton} icon="eye" size="sm" label=${t("tr.open")} onClick=${() => setPreview(true)} />`}
      ${canOpen && html`<${IconButton} icon="download" size="sm" label=${t("common.download")} href=${transferFileUrl(tr.id, true)} download=${tr.name} />`}
      ${out && (tr.state === "failed" || tr.state === "canceled") && html`<${IconButton} icon="retry" size="sm" label=${t("tr.retry")} onClick=${() => run("retry")} />`}
      ${LIVE.has(tr.state) && !isIncomingOffer(tr) && html`<${IconButton} icon="x" size="sm" variant="danger" label=${t("tr.cancel")} onClick=${() => run("cancel")} />`}
      ${!LIVE.has(tr.state) && html`<${IconButton} icon="trash" size="sm" label=${t("tr.remove")} onClick=${remove} />`}
    </div>
    ${preview && html`<${PreviewModal} index=${0} onIndex=${() => {}} onClose=${() => setPreview(false)}
      items=${[{ name: tr.name, mime: tr.mime, size: tr.size, mtime: tr.finished, url: transferFileUrl(tr.id), dlUrl: transferFileUrl(tr.id, true) }]} />`}
  </li>`;
}

export function TransferList({ filter }) {
  const transfers = useStore((s) => s.transfers);
  const list = filter ? transfers.filter(filter) : transfers;
  const offers = list.filter(isIncomingOffer);
  const live = list.filter((x) => LIVE.has(x.state) && !isIncomingOffer(x));
  const history = list.filter((x) => !LIVE.has(x.state));
  const clear = async () => {
    const ok = await confirmDialog({ title: t("tr.clearTitle"), text: t("tr.clearText"), confirmText: t("tr.clear") });
    if (!ok) return;
    const results = await Promise.allSettled(history.map((x) => del(`transfers/${encodeURIComponent(x.id)}`)));
    const failed = results.filter((r) => r.status === "rejected").length;
    if (failed) toast({ level: "warn", title: t("tr.clearPartial", { n: failed }) });
  };
  if (!list.length) {
    return html`<${EmptyState} icon="send" title=${t("tr.emptyTitle")} text=${t("tr.emptyText")} compact />`;
  }
  return html`<div class="trlist">
    ${offers.length > 0 && html`<section class="trlist__sec trlist__sec--offers" aria-labelledby="tr-offers">
      <h3 class="section-title" id="tr-offers"><${Icon} name="inbox" size=${14} /> ${t("tr.sec.offers")} <span class="badge badge--warn">${offers.length}</span></h3>
      <ul class="trlist__items">${offers.map((tr) => html`<${TransferRow} key=${tr.id} tr=${tr} />`)}</ul>
    </section>`}
    ${live.length > 0 && html`<section class="trlist__sec" aria-labelledby="tr-live">
      <h3 class="section-title" id="tr-live">${t("tr.sec.live")}</h3>
      <ul class="trlist__items">${live.map((tr) => html`<${TransferRow} key=${tr.id} tr=${tr} />`)}</ul>
    </section>`}
    ${history.length > 0 && html`<section class="trlist__sec" aria-labelledby="tr-hist">
      <div class="row row--between">
        <h3 class="section-title" id="tr-hist">${t("tr.sec.history")}</h3>
        <${Button} size="sm" variant="ghost" icon="trash" onClick=${clear}>${t("tr.clear")}</${Button}>
      </div>
      <ul class="trlist__items">${history.map((tr) => html`<${TransferRow} key=${tr.id} tr=${tr} />`)}</ul>
    </section>`}
  </div>`;
}

/** Banner shown on every screen (except Files → Send) while somebody wants to send us files. */
export function OffersBanner() {
  const offers = useStore((s) => s.transfers.filter(isIncomingOffer));
  const [busy, setBusy] = useState("");
  if (!offers.length) return null;
  if (offers.length === 1) {
    const tr = offers[0];
    const run = async (a) => { setBusy(a); await act(tr, a); setBusy(""); };
    return html`<div class="gbanner gbanner--offer" role="status">
      <span class="gbanner__icon"><${Icon} name="inbox" size=${18} /></span>
      <div class="grow">
        <span>${tx("offer.one", { who: html`<strong>${tr.peerName}</strong>`, name: html`<strong class="break">«${tr.name}»</strong>` })}</span>
        <span class="gbanner__text tnum">${fmtBytes(tr.size)}</span>
      </div>
      <div class="gbanner__actions">
        <${Button} size="sm" variant="ghost" loading=${busy === "decline"} onClick=${() => run("decline")}>${t("tr.decline")}</${Button}>
        <${Button} size="sm" variant="primary" icon="check" loading=${busy === "accept"} onClick=${() => run("accept")}>${t("tr.accept")}</${Button}>
      </div>
    </div>`;
  }
  const who = Array.from(new Set(offers.map((x) => x.peerName))).join(", ");
  return html`<div class="gbanner gbanner--offer" role="status">
    <span class="gbanner__icon"><${Icon} name="inbox" size=${18} /></span>
    <div class="grow"><span>${tn("offer.many", offers.length, { who })}</span></div>
    <div class="gbanner__actions"><${Button} size="sm" variant="primary" href="#/files/send" iconRight="chevronRight">${t("offer.review")}</${Button}></div>
  </div>`;
}
