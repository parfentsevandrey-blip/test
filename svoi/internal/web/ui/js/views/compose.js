// Compose / reply dialog: recipients (device chips with an "all my devices"
// shortcut), subject, plain-text body, attachments staged via POST api/blobs.
import { html, useEffect, useLayoutEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t } from "../i18n.js";
import { get, post, upload } from "../api.js";
import { href } from "../router.js";
import { setStartFlag } from "../prefs.js";
import { state, useStore } from "../store.js";
import { fmtBytes, fmtDateTime, fmtPercent } from "../format.js";
import { guessMime, uid } from "../util.js";
import { FileIcon } from "../components/avatar.js";
import { DeviceChips } from "../components/devicepicker.js";
import { AutoTextarea, Button, Callout, Progress, Spinner } from "../components/ui.js";
import { confirmDialog, Modal } from "../components/modal.js";
import { toast, toastError } from "../components/toast.js";

function reSubject(s) {
  const base = (s || "").replace(/^\s*((re|ответ|fwd?)\s*(\[\d+\])?:\s*)+/i, "");
  return `Re: ${base}`;
}

function quote(m) {
  const head = t("compose.quoteHead", { date: fmtDateTime(m.ts), name: m.from.name });
  const body = (m.body || "").split("\n").map((l) => (l.startsWith(">") ? ">" + l : "> " + l)).join("\n");
  return `\n\n${head}\n${body}`;
}

/** Stage a file for sending (mail or chat). Returns { promise, abort }. */
export function stageBlob(file, onProgress) {
  const name = file.name || "file";
  const mime = file.type || guessMime(name);
  return upload(`blobs?name=${encodeURIComponent(name)}&mime=${encodeURIComponent(mime)}`, file, { onProgress });
}

export function useAttachments() {
  const [atts, setAtts] = useState([]);
  const ref = useRef(atts);
  ref.current = atts;
  useEffect(() => () => ref.current.forEach((a) => a.abort && a.abort()), []);
  const patch = (key, p) => setAtts((cur) => cur.map((a) => (a.key === key ? { ...a, ...p } : a)));
  const add = (files) => {
    for (const file of files) {
      const key = uid("a");
      const up = stageBlob(file, (l, tot) => patch(key, { progress: tot ? l / tot : 0 }));
      setAtts((cur) => [...cur, { key, name: file.name, size: file.size, mime: file.type, progress: 0, status: "uploading", id: null, abort: up.abort }]);
      up.promise.then((r) => patch(key, { status: "ready", id: r.id, progress: 1, abort: null }))
        .catch((e) => { if (e.code !== "aborted") patch(key, { status: "error", error: e, abort: null }); });
    }
  };
  const remove = (key) => setAtts((cur) => {
    const a = cur.find((x) => x.key === key);
    if (a && a.abort) a.abort();
    return cur.filter((x) => x.key !== key);
  });
  const reset = () => setAtts([]);
  return { atts, add, remove, reset, busy: atts.some((a) => a.status === "uploading"), ids: atts.filter((a) => a.status === "ready").map((a) => a.id) };
}

export function AttachmentChips({ atts, onRemove }) {
  if (!atts.length) return null;
  return html`<ul class="achips">
    ${atts.map((a) => html`<li key=${a.key} class=${"achip" + (a.status === "error" ? " is-error" : "")} data-testid="attachment" data-status=${a.status}>
      <${FileIcon} name=${a.name} mime=${a.mime} size=${16} />
      <span class="achip__name ellipsis" title=${a.name}>${a.name}</span>
      <span class="achip__meta tnum">${a.status === "uploading" ? fmtPercent(a.progress, 1) : a.status === "error" ? t("err." + (a.error && a.error.code || "internal")) : fmtBytes(a.size)}</span>
      ${a.status === "uploading" && html`<span class="achip__bar"><${Progress} size="sm" value=${a.progress * 100} label=${a.name} /></span>`}
      <button type="button" class="achip__x" aria-label=${t("compose.removeAtt", { name: a.name })} onClick=${() => onRemove(a.key)}><${Icon} name="x" size=${14} /></button>
    </li>`)}
  </ul>`;
}

export function ComposeModal({ query, onClose }) {
  const self = useStore((s) => s.self);
  const replyId = query.get("reply");
  const replyAll = query.get("all") === "1";
  const [to, setTo] = useState(() => (query.get("to") || "").split(",").filter((id) => state.peers.some((p) => p.id === id)));
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [orig, setOrig] = useState(null);
  const [loadingOrig, setLoadingOrig] = useState(!!replyId);
  const initial = useRef({ subject: "", body: "" }); // prefilled values don't count as a draft
  const [sending, setSending] = useState(false);
  const A = useAttachments();
  const bodyRef = useRef(null);
  const fileRef = useRef(null);
  const cursorTop = useRef(false);

  // After the quote is filled in, put the caret above it (synchronously, so
  // nothing typed in between ends up below the quote).
  useLayoutEffect(() => {
    if (!cursorTop.current) return;
    const el = bodyRef.current;
    if (!el) return;
    cursorTop.current = false;
    el.focus();
    el.setSelectionRange(0, 0);
    el.scrollTop = 0;
  }, [body]);

  useEffect(() => {
    if (!replyId) return;
    get(`mail/${encodeURIComponent(replyId)}`).then((m) => {
      setOrig(m);
      const me = self && self.id;
      const fromMe = m.from && m.from.id === me;
      let rcpt = fromMe ? (m.to || []).map((r) => r.id) : [m.from.id];
      if (replyAll && !fromMe) rcpt = [m.from.id, ...(m.to || []).map((r) => r.id).filter((id) => id !== me && id !== m.from.id)];
      setTo(rcpt.filter((id) => state.peers.some((p) => p.id === id)));
      setSubject(reSubject(m.subject));
      setBody(quote(m));
      initial.current = { subject: reSubject(m.subject), body: quote(m) };
      cursorTop.current = true;
      setLoadingOrig(false);
    }).catch((e) => { setLoadingOrig(false); toastError(e); });
  }, [replyId]);

  const dirty = subject.trim() !== initial.current.subject.trim() || body.trim() !== initial.current.body.trim() || A.atts.length > 0;
  const canSend = to.length > 0 && (subject.trim() || body.trim() || A.ids.length) && !A.busy && !sending;

  const close = async () => {
    if (dirty && !sending) {
      const ok = await confirmDialog({ title: t("compose.discardTitle"), text: t("compose.discardText"), confirmText: t("compose.discard"), danger: true });
      if (!ok) return;
    }
    onClose();
  };

  const send = async () => {
    if (!canSend) return;
    setSending(true);
    try {
      const r = await post("mail", { to, subject: subject.trim(), body, attachments: A.ids, ...(orig ? { inReplyTo: orig.id } : {}) });
      setStartFlag("sent");
      const names = to.map((id) => (state.peers.find((p) => p.id === id) || {}).name).filter(Boolean).join(", ");
      const offline = to.filter((id) => { const p = state.peers.find((x) => x.id === id); return p && !p.online; }).length;
      toast({ level: "success", title: t("compose.sent"), text: offline ? t("compose.sentQueued", { names }) : t("compose.sentTo", { names }),
        link: r && r.id ? href(["mail", "sent", r.id]) : null, actionLabel: t("compose.view") });
      A.reset();
      onClose();
    } catch (e) {
      setSending(false);
      toastError(e, t("compose.failed"));
    }
  };

  const title = orig ? t("compose.replyTitle") : t("compose.title");
  return html`<${Modal} title=${title} icon=${orig ? "reply" : "compose"} size="lg" class="compose" onClose=${close} closeOnBackdrop=${false} testid="compose"
      initialFocus=${replyId ? ".compose__body" : "#c-subj"}
      footer=${html`
        <button type="button" class="btn btn--ghost btn--sm compose__attach" onClick=${() => fileRef.current && fileRef.current.click()}>
          <${Icon} name="paperclip" size=${16} /><span class="btn__label">${t("compose.attach")}</span></button>
        <span class="grow compose__hint faint xsmall"><kbd>Ctrl</kbd> + <kbd>Enter</kbd> — ${t("compose.sendHint")}</span>
        <${Button} variant="ghost" onClick=${close}>${t("common.cancel")}</${Button}>
        <${Button} variant="primary" icon="send" disabled=${!canSend} loading=${sending} onClick=${send} data-testid="compose-send">${t("compose.send")}</${Button}>`}>
    <div class="compose__form" onKeyDown=${(e) => { if ((e.ctrlKey || e.metaKey) && e.key === "Enter") { e.preventDefault(); send(); } }}
        onDragOver=${(e) => { if (e.dataTransfer && Array.from(e.dataTransfer.types || []).includes("Files")) e.preventDefault(); }}
        onDrop=${(e) => { if (e.defaultPrevented || !e.dataTransfer || !e.dataTransfer.files.length) return; e.preventDefault(); A.add(Array.from(e.dataTransfer.files)); }}>
      <input type="file" multiple class="sr-only" ref=${fileRef} tabindex="-1" aria-hidden="true" data-testid="compose-files"
        onChange=${(e) => { const l = Array.from(e.target.files || []); e.target.value = ""; if (l.length) A.add(l); }} />
      ${loadingOrig && html`<div class="row faint small"><${Spinner} size=${14} />${t("common.loading")}</div>`}
      <div class="compose__row">
        <span class="compose__label" id="c-to">${t("compose.to")}</span>
        <div class="grow"><${DeviceChips} value=${to} onChange=${setTo} label=${t("compose.to")} showOwnShortcut /></div>
      </div>
      <div class="compose__row">
        <label class="compose__label" for="c-subj">${t("compose.subject")}</label>
        <input id="c-subj" class="input" value=${subject} maxlength="200" placeholder=${t("compose.subjectPh")} autofocus=${!replyId} data-testid="compose-subject"
          onInput=${(e) => setSubject(e.target.value)} />
      </div>
      <${AutoTextarea} inputRef=${bodyRef} value=${body} rows="9" class="compose__body" placeholder=${t("compose.bodyPh")} aria-label=${t("compose.body")} data-testid="compose-body"
        onInput=${(e) => setBody(e.target.value)} />
      <${AttachmentChips} atts=${A.atts} onRemove=${A.remove} />
      ${to.some((id) => { const p = state.peers.find((x) => x.id === id); return p && !p.online; }) &&
        html`<${Callout} tone="neutral" icon="clock">${t("compose.offlineNote")}</${Callout}>`}
    </div>
  </${Modal}>`;
}
