// Mail: folders | message list | reading pane (single-pane drill-down on phones).
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { del, get, mailAttachmentUrl, post } from "../api.js";
import { back, go, href } from "../router.js";
import { state, useEvent, useStore } from "../store.js";
import { fmtBytes, fmtDateTime, fmtShortDate } from "../format.js";
import { useInterval, useIsMobile, useMedia } from "../hooks.js";
import { cx, debounce, previewKind } from "../util.js";
import { DeviceAvatar, FileIcon } from "../components/avatar.js";
import { AttachmentFetch, attStateText, keepFetching, Linkified } from "../components/misc.js";
import { Button, Chip, EmptyState, IconButton, Progress, Skeleton } from "../components/ui.js";
import { confirmDialog } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";
import { ComposeModal } from "./compose.js";
import { PreviewModal } from "./preview.js";

const FOLDERS = [
  { id: "inbox", icon: "inbox" },
  { id: "sent", icon: "sent" },
  { id: "trash", icon: "trash" },
];
const PAGE = 40;
let lastListRoute = "#/mail/inbox";

function peerOf(id) {
  return state.peers.find((p) => p.id === id) || null;
}

export function deliveryTone(st) {
  return st === "delivered" ? "ok" : st === "failed" ? "err" : st === "sent" ? "info" : "neutral";
}

function deliverySummary(to) {
  if (!to || !to.length) return null;
  if (to.some((r) => r.state === "failed")) return { icon: "alertCircle", cls: "danger-text", label: t("mail.st.failed") };
  if (to.every((r) => r.state === "delivered")) return { icon: "checks", cls: "accent", label: t("mail.st.delivered") };
  if (to.some((r) => r.state === "queued")) return { icon: "clock", cls: "faint", label: t("mail.st.queued") };
  return { icon: "check", cls: "faint", label: t("mail.st.sent") };
}

// ---------------------------------------------------------------- list
function MailItem({ m, folder, active, onStar }) {
  const sent = folder === "sent" || (state.self && m.from && m.from.id === state.self.id);
  const who = sent ? t("mail.toNames", { names: (m.to || []).map((r) => r.name).join(", ") }) : (m.from && m.from.name) || "";
  const dev = sent ? peerOf(m.to && m.to[0] && m.to[0].id) : peerOf(m.from && m.from.id);
  const ds = sent ? deliverySummary(m.to) : null;
  return html`<li class=${cx("mitem", m.unread && "is-unread", active && "is-active")} data-testid="mail-item" data-id=${m.id} data-unread=${String(!!m.unread)}>
    <a class="mitem__link" href=${href(["mail", folder, m.id])} aria-current=${active ? "true" : undefined}>
      <span class="mitem__av">${dev ? html`<${DeviceAvatar} dev=${dev} size=${36} showStatus=${false} />` : html`<span class="avatar" style="--av:36px"><${Icon} name="user" size=${18} /></span>`}
        ${m.unread && html`<span class="mitem__dot" aria-label=${t("mail.unread")}></span>`}</span>
      <span class="mitem__body">
        <span class="mitem__top">
          <span class="mitem__who ellipsis">${who}</span>
          <span class="mitem__date tnum">${fmtShortDate(m.ts)}</span>
        </span>
        <span class="mitem__subj ellipsis">${m.subject || t("mail.noSubject")}</span>
        <span class="mitem__snip">
          ${ds && html`<span class=${cx("mitem__ds", ds.cls)} title=${ds.label}><${Icon} name=${ds.icon} size=${14} label=${ds.label} /></span>`}
          <span class="ellipsis">${m.snippet}</span>
          ${m.attachments > 0 && html`<span class="mitem__clip" title=${tn("mail.attN", m.attachments)}><${Icon} name="paperclip" size=${14} label=${tn("mail.attN", m.attachments)} /></span>`}
        </span>
      </span>
    </a>
    <button type="button" class=${cx("mitem__star", m.starred && "is-on")} aria-pressed=${String(!!m.starred)}
      aria-label=${m.starred ? t("mail.unstar") : t("mail.star")} title=${m.starred ? t("mail.unstar") : t("mail.star")} onClick=${() => onStar(m)}>
      <${Icon} name="star" size=${16} />
    </button>
  </li>`;
}

function useMailList(folder, query) {
  const [st, setSt] = useState({ items: [], total: 0, unread: 0, loading: true, error: null, more: false, loadingMore: false });
  const seq = useRef(0);
  const itemsRef = useRef([]);
  itemsRef.current = st.items;
  const load = async (silent = false) => {
    const my = ++seq.current;
    if (!silent) setSt((s) => ({ ...s, loading: true, error: null, items: [] }));
    try {
      const limit = Math.max(PAGE, silent ? itemsRef.current.length : 0);
      const r = await get(`mail?folder=${folder}&limit=${limit}${query ? `&q=${encodeURIComponent(query)}` : ""}`);
      if (my !== seq.current) return;
      setSt({ items: r.items || [], total: r.total || 0, unread: r.unread || 0, loading: false, error: null, more: (r.items || []).length < (r.total || 0), loadingMore: false });
    } catch (e) {
      if (my !== seq.current) return;
      setSt((s) => ({ ...s, loading: false, error: e }));
    }
  };
  const loadMore = async () => {
    const items = itemsRef.current;
    if (!items.length || st.loadingMore) return;
    const my = seq.current;
    setSt((s) => ({ ...s, loadingMore: true }));
    try {
      const last = items[items.length - 1];
      const r = await get(`mail?folder=${folder}&limit=${PAGE}&before=${last.ts}${query ? `&q=${encodeURIComponent(query)}` : ""}`);
      if (my !== seq.current) return;
      const known = new Set(items.map((x) => x.id));
      const add = (r.items || []).filter((x) => !known.has(x.id));
      setSt((s) => ({ ...s, items: [...s.items, ...add], loadingMore: false, more: add.length > 0 && s.items.length + add.length < (r.total || 0) }));
    } catch (e) {
      setSt((s) => ({ ...s, loadingMore: false }));
      toastError(e);
    }
  };
  useEffect(() => { load(false); }, [folder, query]);
  const patch = (id, p) => setSt((s) => ({ ...s, items: s.items.map((x) => (x.id === id ? { ...x, ...p } : x)) }));
  const drop = (id) => setSt((s) => ({ ...s, items: s.items.filter((x) => x.id !== id), total: Math.max(0, s.total - 1) }));
  return { ...st, reload: load, loadMore, patch, drop };
}

// ---------------------------------------------------------------- reader
function AttachmentRow({ m, a, i, onPreview, onFetching }) {
  const ready = a.state === "ready";
  const pk = previewKind(a.name, a.mime);
  const stateText = attStateText(a);
  const askable = (a.state === "remote" && a.needsConsent === true) || a.state === "failed";
  return html`<li class=${cx("att", `att--${a.state}`, askable && "att--ask")} data-testid="mail-attachment" data-state=${a.state} data-index=${i}>
    <${FileIcon} name=${a.name} mime=${a.mime} boxed size=${40} />
    <div class="grow att__main">
      <span class="ellipsis strong small" title=${a.name}>${a.name}</span>
      <span class="xsmall faint tnum">${fmtBytes(a.size)}${stateText && html` · <span class=${a.state === "failed" ? "danger-text" : ""}>${stateText}</span>`}</span>
      ${a.state === "fetching" && a.size > 0 && html`<${Progress} size="sm" value=${a.got || 0} max=${a.size} label=${a.name} />`}
    </div>
    ${ready && pk && html`<${IconButton} icon="eye" size="sm" label=${t("mail.att.preview", { name: a.name })} onClick=${() => onPreview(i)} />`}
    ${ready && html`<${IconButton} icon="download" size="sm" label=${t("mail.att.download", { name: a.name })} href=${mailAttachmentUrl(m.id, i, true)} download=${a.name} />`}
    ${askable
      ? html`<${AttachmentFetch} a=${a} fetchPath=${`mail/${encodeURIComponent(m.id)}/attachments/${i}/fetch`} onStarted=${() => onFetching(i)} />`
      : !ready && html`<span class="att__state">${a.state === "fetching" ? html`<span class="spinner" style="width:16px;height:16px"></span>` : html`<${Icon} name="clock" size=${16} />`}</span>`}
  </li>`;
}

function Reader({ id, folder, onChanged, onRemoved, isMobile }) {
  const self = useStore((s) => s.self);
  const [st, setSt] = useState({ m: null, loading: true, error: null });
  const [preview, setPreview] = useState(-1);
  const load = async (silent) => {
    if (!silent) setSt({ m: null, loading: true, error: null });
    try {
      const m = await get(`mail/${encodeURIComponent(id)}`);
      setSt((s) => ({ m: s.m && s.m.id === m.id ? { ...m, attachments: keepFetching(s.m.attachments, m.attachments) } : m, loading: false, error: null }));
      if (m.unread && !silent) {
        // Opening a message marks it read (GET does not).
        post(`mail/${encodeURIComponent(id)}/flags`, { unread: false }).then(() => {
          setSt((s) => (s.m ? { ...s, m: { ...s.m, unread: false } } : s));
          onChanged(id, { unread: false });
        }).catch(() => {});
      }
    } catch (e) {
      // A background re-read that fails keeps what is shown, unless the message is gone.
      if (!silent || e.code === "notfound") setSt({ m: null, loading: false, error: e });
    }
  };
  useEffect(() => { load(false); setPreview(-1); }, [id]);
  // A fetched attachment ends with a `mail` event for the message; a reconnect may have missed it.
  useEvent("mail", (d) => { if (d && d.id === id) load(true); });
  useEvent("refreshed", () => load(true));
  // Downloads report only their end; poll meanwhile so the progress bar moves.
  const fetching = !!(st.m && Array.isArray(st.m.attachments) && st.m.attachments.some((a) => a.state === "fetching"));
  useInterval(() => load(true), 3000, fetching);

  const m = st.m;
  const listHref = href(["mail", folder]);
  const head = isMobile && html`<div class="reader__mbar"><${IconButton} icon="arrowLeft" label=${t("common.back")} onClick=${() => back(listHref)} /></div>`;
  if (st.loading) {
    return html`<div class="reader">${head}<div class="reader__inner"><${Skeleton} w="60%" h=${24} /><div class="row mt-4"><${Skeleton} w=${40} h=${40} r=${12} /><div class="grow stack stack--sm"><${Skeleton} w="40%" h=${14} /><${Skeleton} w="25%" h=${12} /></div></div>
      <div class="stack mt-6">${[90, 75, 82, 40].map((w, i) => html`<${Skeleton} key=${i} w=${w + "%"} h=${13} />`)}</div></div></div>`;
  }
  if (st.error || !m) {
    return html`<div class="reader">${head}<${EmptyState} icon="alertCircle" tone="err" title=${st.error && st.error.code === "notfound" ? t("mail.gone") : t("mail.loadError")} text=${st.error ? t("err." + st.error.code) : ""} /></div>`;
  }
  const mine = self && m.from && m.from.id === self.id;
  const fromDev = peerOf(m.from && m.from.id);
  const flag = async (p, okText) => {
    try {
      await post(`mail/${encodeURIComponent(m.id)}/flags`, p);
      setSt((s) => ({ ...s, m: { ...s.m, ...p } }));
      onChanged(m.id, p);
      if (okText) toast({ level: "success", title: okText });
    } catch (e) { toastError(e); }
  };
  const toTrash = async () => {
    try {
      await del(`mail/${encodeURIComponent(m.id)}`);
      toast({ level: "success", title: t("mail.trashed"), actionLabel: t("mail.undo"), onAction: async () => {
        try { await post(`mail/${encodeURIComponent(m.id)}/flags`, { folder: m.folder }); onChanged(m.id, {}); } catch (e) { toastError(e); }
      } });
      onRemoved(m.id);
    } catch (e) { toastError(e); }
  };
  const forever = async () => {
    const ok = await confirmDialog({ title: t("mail.delForeverTitle"), text: t("mail.delForeverText"), confirmText: t("mail.delForever"), danger: true });
    if (!ok) return;
    try { await del(`mail/${encodeURIComponent(m.id)}`); toast({ level: "success", title: t("mail.deleted") }); onRemoved(m.id); }
    catch (e) { toastError(e); }
  };
  const restore = () => flag({ folder: mine ? "sent" : "inbox" }, t("mail.restored")).then(() => onRemoved(m.id));
  // No event when a download starts: show it as fetching right away.
  const markFetching = (i) => setSt((s) => (s.m ? { ...s, m: { ...s.m, attachments: s.m.attachments.map((x, j) => (j === i ? { ...x, state: "fetching", got: 0, needsConsent: false } : x)) } } : s));
  const atts = Array.isArray(m.attachments) ? m.attachments : [];
  const pvItems = atts.map((a, i) => ({ name: a.name, mime: a.mime, size: a.size, url: mailAttachmentUrl(m.id, i), dlUrl: mailAttachmentUrl(m.id, i, true), i, ok: a.state === "ready" && previewKind(a.name, a.mime) }))
    .filter((x) => x.ok);
  const others = (m.to || []).filter((r) => !self || r.id !== self.id);

  return html`<article class="reader" aria-labelledby="reader-subj" data-testid="mail-reader" data-id=${m.id}>
    ${head}
    <div class="reader__tools" role="toolbar" aria-label=${t("mail.actions")}>
      ${m.folder !== "trash" && html`
        <${Button} size="sm" icon="reply" href=${href(["mail", "compose"], { reply: m.id })} data-testid="mail-reply">${t("mail.reply")}</${Button}>
        ${!mine && others.length > 0 && html`<${Button} size="sm" variant="ghost" icon="replyAll" href=${href(["mail", "compose"], { reply: m.id, all: 1 })}>${t("mail.replyAll")}</${Button}>`}`}
      <span class="grow"></span>
      <${IconButton} icon="star" label=${m.starred ? t("mail.unstar") : t("mail.star")} active=${!!m.starred} onClick=${() => flag({ starred: !m.starred })} class="reader__star" />
      ${m.folder !== "sent" && html`<${IconButton} icon="mailDot" label=${t("mail.markUnread")} onClick=${() => { flag({ unread: true }, t("mail.markedUnread")); if (isMobile) back(listHref); }} />`}
      ${m.folder === "trash"
        ? html`<${IconButton} icon="retry" label=${t("mail.restore")} onClick=${restore} />
               <${IconButton} icon="trash" variant="danger" label=${t("mail.delForever")} onClick=${forever} />`
        : html`<${IconButton} icon="trash" label=${t("mail.toTrash")} onClick=${toTrash} data-testid="mail-trash" />`}
    </div>
    <div class="reader__inner">
      <h2 class="reader__subj" id="reader-subj">${m.subject || t("mail.noSubject")}</h2>
      <div class="reader__from">
        ${fromDev ? html`<${DeviceAvatar} dev=${fromDev} size=${40} />` : html`<span class="avatar" style="--av:40px"><${Icon} name=${mine ? "laptop" : "user"} size=${20} /></span>`}
        <div class="grow">
          <div class="row row--wrap gap-1"><span class="strong">${mine ? t("mail.me", { name: m.from.name }) : m.from.name}</span>
            <span class="faint small">→ ${(m.to || []).map((r) => (self && r.id === self.id ? t("mail.meShort") : r.name)).join(", ")}</span></div>
          <time class="faint small tnum" datetime=${new Date(m.ts * 1000).toISOString()}>${fmtDateTime(m.ts)}</time>
        </div>
      </div>
      ${mine && m.to && m.to.length > 0 && html`<div class="reader__delivery" aria-label=${t("mail.delivery")} data-testid="mail-delivery">
        ${m.to.map((r) => html`<span class="dlv" key=${r.id} data-testid="mail-recipient" data-state=${r.state}>
          <span class="strong small">${r.name}</span>
          <${Chip} size="sm" tone=${deliveryTone(r.state)} icon=${r.state === "delivered" ? "checks" : r.state === "failed" ? "alertCircle" : r.state === "sent" ? "check" : "clock"}>${t("mail.st." + r.state)}</${Chip}>
          ${r.at ? html`<span class="xsmall faint tnum">${fmtShortDate(r.at)}</span>` : null}
        </span>`)}
        ${m.to.some((r) => r.state === "queued") && html`<p class="xsmall faint reader__dlvhint">${t("mail.queuedHint")}</p>`}
      </div>`}
      <${Linkified} text=${m.body} class="reader__body" />
      ${atts.length > 0 && html`<section class="reader__atts" aria-label=${tn("mail.attN", atts.length)}>
        <h3 class="section-title"><${Icon} name="paperclip" size=${14} /> ${tn("mail.attN", atts.length)}</h3>
        <ul class="atts">${atts.map((a, i) => html`<${AttachmentRow} key=${i} m=${m} a=${a} i=${i}
          onPreview=${(idx) => setPreview(pvItems.findIndex((x) => x.i === idx))} onFetching=${markFetching} />`)}</ul>
      </section>`}
    </div>
    ${preview >= 0 && html`<${PreviewModal} items=${pvItems} index=${preview} onIndex=${setPreview} onClose=${() => setPreview(-1)} />`}
  </article>`;
}

// ---------------------------------------------------------------- view
export function MailView({ route }) {
  const isMobile = useIsMobile();
  const wide = useMedia("(min-width: 1100px)");
  const counters = useStore((s) => s.counters);
  const compose = route.parts[1] === "compose";
  const folder = !compose && FOLDERS.some((f) => f.id === route.parts[1]) ? route.parts[1] : (compose ? (lastListRoute.split("/")[2] || "inbox") : "inbox");
  const openId = !compose ? route.parts[2] || null : null;
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const setQueryDebounced = useRef(debounce((v) => setQuery(v), 300)).current;
  const list = useMailList(folder, query);
  const sentinel = useRef(null);

  if (!compose) lastListRoute = href(["mail", folder]);

  useEffect(() => { setQ(""); setQuery(""); }, [folder]);
  useEvent("mail", () => list.reload(true));
  useEvent("refreshed", () => list.reload(true));

  // Infinite scroll: load the next page when the sentinel becomes visible.
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !list.more) return undefined;
    const io = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) list.loadMore(); }, { rootMargin: "200px" });
    io.observe(el);
    return () => io.disconnect();
  }, [list.more, list.items.length, folder, query]);

  const onStar = async (m) => {
    list.patch(m.id, { starred: !m.starred });
    try { await post(`mail/${encodeURIComponent(m.id)}/flags`, { starred: !m.starred }); }
    catch (e) { list.patch(m.id, { starred: m.starred }); toastError(e); }
  };
  const onChanged = (id, p) => { list.patch(id, p); if (p.folder && p.folder !== folder) list.drop(id); };
  const onRemoved = (id) => {
    const idx = list.items.findIndex((x) => x.id === id);
    list.drop(id);
    const next = list.items[idx + 1] || list.items[idx - 1];
    go(next && !isMobile ? href(["mail", folder, next.id]) : href(["mail", folder]), { replace: true });
  };

  const showList = !isMobile || !openId;
  const showReader = !isMobile || !!openId;

  const folderNav = html`<nav class="mfolders" aria-label=${t("mail.folders")}>
    <${Button} variant="primary" icon="compose" href=${href(["mail", "compose"])} class="mfolders__compose" data-testid="mail-compose">${t("mail.compose")}</${Button}>
    ${FOLDERS.map((f) => html`<a key=${f.id} href=${href(["mail", f.id])} class=${cx("mfolder", folder === f.id && "is-active")} aria-current=${folder === f.id ? "page" : undefined} data-testid=${"mail-folder-" + f.id}>
      <${Icon} name=${f.icon} size=${18} /><span class="grow">${t("mail.folder." + f.id)}</span>
      ${f.id === "inbox" && counters.mail > 0 && html`<span class="mfolder__n tnum">${counters.mail}</span>`}
    </a>`)}
  </nav>`;

  const folderSeg = html`<div class="mfolders-seg" role="tablist" aria-label=${t("mail.folders")}>
    ${FOLDERS.map((f) => html`<a key=${f.id} role="tab" aria-selected=${String(folder === f.id)} href=${href(["mail", f.id])} class=${cx("mfseg", folder === f.id && "is-active")} data-testid=${"mail-folder-" + f.id}>
      <${Icon} name=${f.icon} size=${16} />${t("mail.folder." + f.id)}${f.id === "inbox" && counters.mail > 0 ? html` <span class="badge badge--accent">${counters.mail}</span>` : ""}</a>`)}
  </div>`;

  let listBody;
  if (list.loading && !list.items.length) {
    listBody = html`<ul class="mlist">${Array.from({ length: 7 }, (_, i) => html`<li key=${i} class="mitem mitem--skel"><${Skeleton} w=${36} h=${36} r=${11} />
      <div class="grow stack stack--sm"><${Skeleton} w="45%" h=${13} /><${Skeleton} w="80%" h=${13} /><${Skeleton} w="65%" h=${11} /></div></li>`)}</ul>`;
  } else if (list.error) {
    listBody = html`<${EmptyState} compact icon="alertCircle" tone="err" title=${t("mail.loadError")} text=${t("err." + list.error.code)}>
      <${Button} size="sm" icon="refresh" onClick=${() => list.reload()}>${t("common.retry")}</${Button}></${EmptyState}>`;
  } else if (!list.items.length) {
    listBody = query
      ? html`<${EmptyState} compact icon="search" title=${t("mail.noResults")} text=${t("mail.noResultsText", { q: query })} />`
      : html`<${EmptyState} compact icon=${folder === "trash" ? "trash" : folder === "sent" ? "sent" : "inbox"} title=${t("mail.empty." + folder)} text=${t("mail.emptyText." + folder)}>
          ${folder !== "trash" && html`<${Button} size="sm" variant="primary" icon="compose" href=${href(["mail", "compose"])}>${t("mail.compose")}</${Button}>`}
        </${EmptyState}>`;
  } else {
    listBody = html`<ul class="mlist" aria-label=${t("mail.folder." + folder)}>
      ${list.items.map((m) => html`<${MailItem} key=${m.id} m=${m} folder=${folder} active=${m.id === openId} onStar=${onStar} />`)}
    </ul>
    ${list.more && html`<div class="mlist__more" ref=${sentinel}>
      <${Button} size="sm" variant="ghost" loading=${list.loadingMore} onClick=${list.loadMore}>${t("common.loadMore")}</${Button}>
    </div>`}`;
  }

  return html`<div class=${cx("mail", openId && "has-open")}>
    ${wide && folderNav}
    ${showList && html`<section class="mail__list" aria-label=${t("mail.folder." + folder)}>
      <p class="pane-intro">${t("mail.intro")}</p>
      ${!wide && folderSeg}
      <div class="mail__search">
        <div class="input-wrap"><${Icon} name="search" size=${16} />
          <input class="input" type="search" placeholder=${t("mail.search")} aria-label=${t("mail.search")} value=${q} data-testid="mail-search"
            onInput=${(e) => { setQ(e.target.value); setQueryDebounced(e.target.value.trim()); }} /></div>
        ${!wide && html`<${IconButton} icon="compose" variant="primary" label=${t("mail.compose")} href=${href(["mail", "compose"])} data-testid="mail-compose" />`}
      </div>
      <p class="mail__count faint xsmall tnum">${list.loading ? " " : tn("mail.countN", list.total)}</p>
      <div class="mail__scroll">${listBody}</div>
    </section>`}
    ${showReader && html`<section class="mail__reader" aria-label=${t("mail.reading")}>
      ${openId ? html`<${Reader} id=${openId} folder=${folder} onChanged=${onChanged} onRemoved=${onRemoved} isMobile=${isMobile} key=${openId} />`
        : html`<div class="reader reader--empty"><${EmptyState} icon="mailOpen" title=${t("mail.pickTitle")} text=${t("mail.pickText")} /></div>`}
    </section>`}
    ${compose && html`<${ComposeModal} query=${route.query} onClose=${() => back(lastListRoute)} />`}
  </div>`;
}
