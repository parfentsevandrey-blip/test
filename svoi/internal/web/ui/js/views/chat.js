// Chat: thread list + conversation (bubbles, day separators, delivery ticks,
// attachments, Enter to send). Drill-down on phones.
import { html, useEffect, useLayoutEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { chatAttachmentUrl, get, post } from "../api.js";
import { back, go, href } from "../router.js";
import { useEvent, useStore } from "../store.js";
import { dayDiff, fmtAgo, fmtBytes, fmtDay, fmtShortDate, fmtTime } from "../format.js";
import { useIsMobile } from "../hooks.js";
import { setStartFlag } from "../prefs.js";
import { cx, fileKind, previewKind, sortPeers } from "../util.js";
import { DeviceAvatar, FileIcon } from "../components/avatar.js";
import { AttachmentFetch, attStateText, keepFetching, Linkified } from "../components/misc.js";
import { AutoTextarea, Button, EmptyState, IconButton, Skeleton, Spinner } from "../components/ui.js";
import { Menu } from "../components/menu.js";
import { toastError } from "../components/toast.js";
import { connText } from "../components/device-actions.js";
import { AttachmentChips, useAttachments } from "./compose.js";
import { PreviewModal } from "./preview.js";

const PAGE = 50;

// The same plain words as the device cards: «На связи · напрямую», no delays in ms.
function statusText(p) {
  if (!p) return "";
  if (!p.online) return t("chat.seen", { ago: fmtAgo(p.lastSeen) });
  return connText(p);
}

function Tick({ st }) {
  const map = {
    queued: { icon: "clock", label: t("chat.st.queued") },
    sent: { icon: "check", label: t("chat.st.sent") },
    delivered: { icon: "checks", label: t("chat.st.delivered") },
    failed: { icon: "alertCircle", label: t("chat.st.failed") },
  };
  const m = map[st] || map.queued;
  return html`<span class=${cx("tick", `tick--${st}`)} title=${m.label}><${Icon} name=${m.icon} size=${14} strokeWidth=${2} label=${m.label} /></span>`;
}

// ---------------------------------------------------------------- threads
function NewChatMenu({ threads, variant = "secondary" }) {
  const peers = useStore((s) => s.peers);
  const withThread = new Set(threads.map((x) => x.peer.id));
  const list = sortPeers(peers);
  const items = [
    { heading: t("chat.newHeading") },
    ...list.map((p) => ({ label: p.name, hint: withThread.has(p.id) ? t("chat.hasThread") : p.online ? t("dev.status.online") : t("dev.status.offline"), icon: "chat", onClick: () => go(href(["chat", p.id])) })),
  ];
  return html`<${Menu} items=${items} align="start" width=${260} label=${t("chat.new")}
    trigger=${(p) => html`<button type="button" class=${`btn btn--${variant} btn--sm`} data-testid="chat-new" ...${p}><${Icon} name="plus" size=${16} /><span class="btn__label">${t("chat.new")}</span></button>`} />`;
}

function ThreadList({ threads, loading, error, active, onRetry }) {
  const peers = useStore((s) => s.peers);
  const self = useStore((s) => s.self);
  if (loading && !threads.length) {
    return html`<ul class="threads">${Array.from({ length: 5 }, (_, i) => html`<li key=${i} class="thread thread--skel"><${Skeleton} w=${44} h=${44} r=${13} /><div class="grow stack stack--sm"><${Skeleton} w="40%" h=${13} /><${Skeleton} w="75%" h=${12} /></div></li>`)}</ul>`;
  }
  if (error && !threads.length) {
    return html`<${EmptyState} compact icon="alertCircle" tone="err" title=${t("chat.loadError")} text=${t("err." + error.code)}><${Button} size="sm" icon="refresh" onClick=${onRetry}>${t("common.retry")}</${Button}></${EmptyState}>`;
  }
  if (!threads.length && !peers.length) {
    return html`<${EmptyState} compact icon="chat" title=${t("chat.noThreads")} text=${t("chat.noPeersText")}>
      <${Button} size="sm" variant="primary" icon="userPlus" href="#/home?add=1">${t("dev.add")}</${Button}></${EmptyState}>`;
  }
  if (!threads.length) {
    return html`<${EmptyState} compact icon="chat" title=${t("chat.noThreads")} text=${t("chat.noThreadsText")}><${NewChatMenu} threads=${threads} variant="primary" /></${EmptyState}>`;
  }
  return html`<ul class="threads" aria-label=${t("chat.threads")}>
    ${threads.map((th) => {
      const p = peers.find((x) => x.id === th.peer.id) || { ...th.peer, deviceName: th.peer.name };
      const mine = self && th.last && th.last.from === self.id;
      const text = th.last && th.last.text ? th.last.text : t("chat.attachment");
      return html`<li key=${th.peer.id}>
        <a href=${href(["chat", th.peer.id])} class=${cx("thread", active === th.peer.id && "is-active", th.unread > 0 && "is-unread")} aria-current=${active === th.peer.id ? "true" : undefined}
            data-testid="thread" data-peer=${th.peer.id} data-unread=${th.unread}>
          <${DeviceAvatar} dev=${p} size=${44} />
          <span class="thread__body">
            <span class="thread__top"><span class="thread__name ellipsis">${p.name}</span><span class="thread__time tnum">${th.last ? fmtShortDate(th.last.ts) : ""}</span></span>
            <span class="thread__last">
              ${mine && html`<${Tick} st=${th.last.state} />`}
              <span class="ellipsis">${mine ? t("chat.you") + " " : ""}${text}</span>
              ${th.unread > 0 && html`<span class="badge badge--accent" aria-label=${tn("nav.badgeCount", th.unread)}>${th.unread}</span>`}
            </span>
          </span>
        </a>
      </li>`;
    })}
  </ul>`;
}

// ---------------------------------------------------------------- conversation
function Bubble({ m, first, last, onPreview, onMedia, onFetching }) {
  const atts = m.attachments || [];
  return html`<div class=${cx("bubble", m.mine ? "bubble--mine" : "bubble--theirs", first && "is-first", last && "is-last", m.state === "failed" && "is-failed")}
      data-testid="bubble" data-id=${m.id} data-state=${m.state} data-mine=${String(!!m.mine)}>
    ${atts.length > 0 && html`<div class="bubble__atts">
      ${atts.map((a, i) => {
        const ready = a.state === "ready" || !a.state;
        if (ready && fileKind(a.name, a.mime) === "image" && previewKind(a.name, a.mime) === "image") {
          return html`<button type="button" key=${i} class="bubble__img" onClick=${() => onPreview(m, i)} aria-label=${t("mail.att.preview", { name: a.name })}>
            <img src=${chatAttachmentUrl(m.id, i)} alt=${a.name} decoding="async" onLoad=${onMedia} />
          </button>`;
        }
        const st = attStateText(a);
        return html`<div key=${i} class=${cx("bubble__file", a.state === "failed" && "is-failed")} data-testid="chat-attachment" data-state=${a.state || "ready"} data-index=${i}>
          <${FileIcon} name=${a.name} mime=${a.mime} boxed size=${36} />
          <span class="grow"><span class="ellipsis strong small" title=${a.name}>${a.name}</span><span class="xsmall bubble__fmeta tnum">${fmtBytes(a.size)}${st ? " · " + st : ""}</span></span>
          ${ready && previewKind(a.name, a.mime) && html`<${IconButton} icon="eye" size="sm" label=${t("mail.att.preview", { name: a.name })} onClick=${() => onPreview(m, i)} />`}
          ${ready && html`<${IconButton} icon="download" size="sm" label=${t("mail.att.download", { name: a.name })} href=${chatAttachmentUrl(m.id, i, true)} download=${a.name} />`}
          ${a.state === "fetching" && html`<${Spinner} size=${16} />`}
          <${AttachmentFetch} a=${a} fetchPath=${`chat/messages/${encodeURIComponent(m.id)}/attachments/${i}/fetch`} onStarted=${() => onFetching(m, i)} />
        </div>`;
      })}
    </div>`}
    ${m.text && html`<${Linkified} text=${m.text} class="bubble__text" />`}
    <span class="bubble__meta">
      <time class="tnum" datetime=${new Date(m.ts * 1000).toISOString()}>${fmtTime(m.ts)}</time>
      ${m.mine && html`<${Tick} st=${m.state} />`}
    </span>
  </div>`;
}

function Conversation({ peerId, isMobile, onRead }) {
  const peer = useStore((s) => s.peers.find((p) => p.id === peerId) || null);
  const [msgs, setMsgs] = useState([]);
  const [st, setSt] = useState({ loading: true, error: null, older: true, loadingOlder: false });
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [newBelow, setNewBelow] = useState(0);
  const [pv, setPv] = useState(null);
  const A = useAttachments();
  const scroller = useRef(null);
  const input = useRef(null);
  const fileRef = useRef(null);
  const keepScroll = useRef(null); // preserve position when prepending
  const stick = useRef(true);

  const markRead = () => {
    if (document.visibilityState === "hidden") return;
    post(`chat/${encodeURIComponent(peerId)}/read`, {}).then(onRead).catch(() => {});
  };

  useEffect(() => {
    let alive = true;
    setMsgs([]); setSt({ loading: true, error: null, older: true, loadingOlder: false }); setNewBelow(0); stick.current = true;
    get(`chat/${encodeURIComponent(peerId)}?limit=${PAGE}`).then((r) => {
      if (!alive) return;
      const list = r.messages || [];
      setMsgs(list);
      setSt({ loading: false, error: null, older: list.length >= PAGE, loadingOlder: false });
      markRead();
    }).catch((e) => { if (alive) setSt({ loading: false, error: e, older: false, loadingOlder: false }); });
    setTimeout(() => input.current && !isMobile && input.current.focus(), 60);
    return () => { alive = false; };
  }, [peerId]);

  useEvent("chat", (d) => {
    if (!d || d.peer !== peerId) return;
    const { peer: _p, ...m } = d;
    setMsgs((cur) => {
      const i = cur.findIndex((x) => x.id === m.id);
      if (i >= 0) { const c = cur.slice(); c[i] = { ...c[i], ...m, attachments: keepFetching(c[i].attachments, m.attachments) }; return c; }
      return [...cur, m].sort((a, b) => a.ts - b.ts);
    });
    if (!m.mine) {
      const el = scroller.current;
      const near = el && el.scrollHeight - el.scrollTop - el.clientHeight < 120;
      if (near) stick.current = true; else setNewBelow((n) => n + 1);
      markRead();
    }
  });

  // Scroll handling: stick to bottom on new content, keep position when prepending.
  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;
    if (keepScroll.current !== null) {
      el.scrollTop = el.scrollHeight - keepScroll.current;
      keepScroll.current = null;
    } else if (stick.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [msgs, st.loading]);

  const loadOlder = async () => {
    if (!st.older || st.loadingOlder || !msgs.length) return;
    setSt((s) => ({ ...s, loadingOlder: true }));
    try {
      const r = await get(`chat/${encodeURIComponent(peerId)}?limit=${PAGE}&before=${msgs[0].ts}`);
      const list = (r.messages || []).filter((m) => !msgs.some((x) => x.id === m.id));
      const el = scroller.current;
      keepScroll.current = el ? el.scrollHeight - el.scrollTop : null;
      stick.current = false;
      setMsgs((cur) => [...list, ...cur]);
      setSt((s) => ({ ...s, loadingOlder: false, older: list.length >= PAGE }));
    } catch (e) {
      setSt((s) => ({ ...s, loadingOlder: false }));
      toastError(e);
    }
  };

  // No event when an attachment download starts: show it as fetching right away
  // (the `chat` event at the end carries the updated message).
  const markFetching = (m, i) => setMsgs((cur) => cur.map((x) => (x.id === m.id
    ? { ...x, attachments: (x.attachments || []).map((a, j) => (j === i ? { ...a, state: "fetching", needsConsent: false } : a)) }
    : x)));

  // Images change the height after load: keep the view pinned if it was at the bottom.
  const stickToBottom = () => {
    const el = scroller.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  };

  const onScroll = (e) => {
    const el = e.currentTarget;
    const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
    stick.current = dist < 80;
    if (dist < 80 && newBelow) setNewBelow(0);
    if (el.scrollTop < 60) loadOlder();
  };

  const send = async () => {
    const body = text.trim();
    if ((!body && !A.ids.length) || A.busy || sending) return;
    setSending(true);
    try {
      const m = await post(`chat/${encodeURIComponent(peerId)}`, { text: body, attachments: A.ids });
      setStartFlag("sent");
      stick.current = true;
      setMsgs((cur) => (cur.some((x) => x.id === m.id) ? cur.map((x) => (x.id === m.id ? { ...x, ...m } : x)) : [...cur, m]));
      setText("");
      A.reset();
      onRead();
    } catch (e) {
      toastError(e, t("chat.sendFailed"));
    } finally {
      setSending(false);
      input.current && input.current.focus();
    }
  };

  const onKey = (e) => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing && e.keyCode !== 229) {
      e.preventDefault();
      send();
    }
  };

  const name = peer ? peer.name : peerId.slice(0, 8);
  // Group into days and runs of messages by the same sender.
  const rows = [];
  let lastDay = null;
  msgs.forEach((m, i) => {
    const day = new Date(m.ts * 1000).toDateString();
    if (day !== lastDay) { rows.push({ sep: true, ts: m.ts, key: "d" + m.ts + i }); lastDay = day; }
    const prev = msgs[i - 1], next = msgs[i + 1];
    const sameDay = (a, b) => a && b && new Date(a.ts * 1000).toDateString() === new Date(b.ts * 1000).toDateString();
    const first = !prev || prev.mine !== m.mine || !sameDay(prev, m) || m.ts - prev.ts > 300;
    const last = !next || next.mine !== m.mine || !sameDay(next, m) || next.ts - m.ts > 300;
    rows.push({ m, first, last, key: m.id });
  });
  const pvItems = pv ? pv.m.attachments.map((a, i) => ({ name: a.name, mime: a.mime, size: a.size, url: chatAttachmentUrl(pv.m.id, i), dlUrl: chatAttachmentUrl(pv.m.id, i, true) })) : [];

  return html`<section class="conv" aria-label=${t("chat.with", { name })} data-testid="conversation" data-peer=${peerId}>
    <header class="conv__head">
      ${isMobile && html`<${IconButton} icon="arrowLeft" label=${t("common.back")} onClick=${() => back("#/chat")} />`}
      ${peer ? html`<${DeviceAvatar} dev=${peer} size=${40} />` : html`<span class="avatar" style="--av:40px"><${Icon} name="user" size=${20} /></span>`}
      <div class="grow conv__who">
        <h2 class="conv__name ellipsis">${name}</h2>
        <p class=${cx("conv__status ellipsis", peer && peer.online ? (peer.path === "relay" ? "warn-text" : "accent") : "faint")}>${peer ? statusText(peer) : t("dev.notFound")}</p>
      </div>
      ${peer && html`
        <${IconButton} icon="send" label=${t("dev.act.sendFile")} href=${href(["files", "send"], { to: peer.id })} />
        <${IconButton} icon="info" label=${t("chat.details")} href=${href(["devices", peer.id])} />`}
    </header>
    <div class="conv__scroll" ref=${scroller} onScroll=${onScroll} role="log" aria-live="polite" aria-relevant="additions" tabindex="0" aria-label=${t("chat.messages")}>
      ${st.loadingOlder && html`<div class="conv__older"><${Spinner} size=${16} /></div>`}
      ${st.loading && html`<div class="conv__loading">${[60, 40, 70, 30].map((w, i) => html`<div key=${i} class=${cx("row", i % 2 && "row--end")}><${Skeleton} w=${w + "%"} h=${38} r=${16} /></div>`)}</div>`}
      ${st.error && html`<${EmptyState} compact icon="alertCircle" tone="err" title=${t("chat.loadError")} text=${t("err." + st.error.code)} />`}
      ${!st.loading && !st.error && !msgs.length && html`<${EmptyState} compact icon="chat" tone="accent" title=${t("chat.emptyConv", { name })} text=${peer && !peer.online ? t("chat.emptyConvOffline") : t("chat.emptyConvText")} />`}
      ${rows.map((r) => r.sep
        ? html`<div class="daysep" key=${r.key}><span>${dayDiff(r.ts) === 1 ? t("chat.yesterday") : fmtDay(r.ts)}</span></div>`
        : html`<div key=${r.key} class=${cx("bubble-row", r.m.mine && "is-mine", r.first && "is-first")}><${Bubble} m=${r.m} first=${r.first} last=${r.last} onPreview=${(m, i) => setPv({ m, i })} onMedia=${stickToBottom} onFetching=${markFetching} /></div>`)}
    </div>
    ${newBelow > 0 && html`<button type="button" class="conv__new" onClick=${() => { const el = scroller.current; if (el) el.scrollTop = el.scrollHeight; setNewBelow(0); }}>
      <${Icon} name="arrowDown" size=${16} />${tn("chat.newBelow", newBelow)}</button>`}
    <footer class="conv__compose">
      ${peer && !peer.online && html`<p class="conv__offline"><${Icon} name="clock" size=${14} />${t("chat.offlineNote", { name })}</p>`}
      <${AttachmentChips} atts=${A.atts} onRemove=${A.remove} />
      <div class="composer">
        <input type="file" multiple class="sr-only" ref=${fileRef} tabindex="-1" aria-hidden="true" data-testid="chat-files" onChange=${(e) => { const l = Array.from(e.target.files || []); e.target.value = ""; if (l.length) A.add(l); }} />
        <${IconButton} icon="paperclip" label=${t("chat.attach")} onClick=${() => fileRef.current && fileRef.current.click()} />
        <${AutoTextarea} inputRef=${input} rows="1" value=${text} class="composer__input" placeholder=${t("chat.placeholder")} aria-label=${t("chat.placeholder")} data-testid="chat-input"
          onInput=${(e) => setText(e.target.value)} onKeyDown=${onKey} />
        <${IconButton} icon="send" variant="primary" label=${t("chat.send")} disabled=${(!text.trim() && !A.ids.length) || A.busy || sending} onClick=${send} data-testid="chat-send" />
      </div>
      <p class="composer__hint faint xsmall">${t("chat.hint")}</p>
    </footer>
    ${pv && html`<${PreviewModal} items=${pvItems} index=${pv.i} onIndex=${(i) => setPv({ ...pv, i })} onClose=${() => setPv(null)} />`}
  </section>`;
}

export function ChatView({ route }) {
  const isMobile = useIsMobile();
  const peerId = route.parts[1] || null;
  const [threads, setThreads] = useState([]);
  const [st, setSt] = useState({ loading: true, error: null });
  const timer = useRef(0);

  const load = async () => {
    try {
      const r = await get("chat/threads");
      setThreads(Array.isArray(r) ? r : []);
      setSt({ loading: false, error: null });
    } catch (e) { setSt({ loading: false, error: e }); }
  };
  const reloadSoon = () => { clearTimeout(timer.current); timer.current = setTimeout(load, 250); };
  useEffect(() => { load(); return () => clearTimeout(timer.current); }, []);
  useEvent("chat", reloadSoon);
  useEvent("refreshed", reloadSoon);

  const onRead = () => {
    setThreads((cur) => cur.map((x) => (x.peer.id === peerId ? { ...x, unread: 0 } : x)));
    reloadSoon();
  };

  const showList = !isMobile || !peerId;
  const showConv = !isMobile || !!peerId;
  return html`<div class=${cx("chat", peerId && "has-open")}>
    ${showList && html`<aside class="chat__list" aria-label=${t("chat.threads")}>
      <div class="chat__listhead">
        <h1 class="chat__title">${t("nav.chat")}</h1>
        <${NewChatMenu} threads=${threads} />
      </div>
      <p class="chat__intro">${t("chat.intro")}</p>
      <div class="chat__threads">
        <${ThreadList} threads=${threads} loading=${st.loading} error=${st.error} active=${peerId} onRetry=${load} />
      </div>
    </aside>`}
    ${showConv && html`<div class="chat__conv">
      ${peerId ? html`<${Conversation} peerId=${peerId} isMobile=${isMobile} onRead=${onRead} key=${peerId} />`
        : html`<div class="conv conv--empty"><${EmptyState} icon="chat" title=${t("chat.pick")} text=${t("chat.pickText")} /></div>`}
    </div>`}
  </div>`;
}
