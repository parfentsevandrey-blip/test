// Files → Send: pick recipients, drop/pick/paste files, upload with progress,
// then follow the deliveries in the transfers list.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { upload } from "../api.js";
import { state, upsertTransfer, useStore } from "../store.js";
import { fmtBytes, fmtPercent } from "../format.js";
import { cx, guessMime, uid } from "../util.js";
import { useWindowEvent } from "../hooks.js";
import { FileIcon } from "../components/avatar.js";
import { DeviceChips } from "../components/devicepicker.js";
import { DropZone, useFileDrop } from "../components/misc.js";
import { Button, Callout, Card, IconButton, Progress } from "../components/ui.js";
import { toast } from "../components/toast.js";
import { TransferList } from "./transfers.js";

export function SendTab({ route }) {
  const peers = useStore((s) => s.peers);
  const initialTo = (route.query.get("to") || "").split(",").filter(Boolean);
  const [to, setTo] = useState(initialTo);
  const [files, setFiles] = useState([]); // { key, file, progress, status: staged|uploading|error, error, abort }
  const filesRef = useRef(files);
  filesRef.current = files;

  // Follow ?to= changes (e.g. "Send file" from a device card while already here).
  useEffect(() => { if (initialTo.length) setTo(initialTo); }, [route.query.get("to")]);
  useEffect(() => () => filesRef.current.forEach((f) => f.abort && f.abort()), []);

  const add = (list) => {
    const items = list.map((file) => ({ key: uid("f"), file, progress: 0, status: "staged", error: null }));
    setFiles((cur) => [...cur, ...items]);
  };
  const over = useFileDrop(add);
  useWindowEvent("paste", (e) => {
    const target = e.target;
    if (target && /^(INPUT|TEXTAREA)$/.test(target.tagName)) return;
    const list = Array.from((e.clipboardData && e.clipboardData.files) || []);
    if (list.length) { e.preventDefault(); add(list); toast({ level: "info", title: tn("send.pasted", list.length) }); }
  });

  const patch = (key, p) => setFiles((cur) => cur.map((f) => (f.key === key ? { ...f, ...p } : f)));
  const remove = (key) => setFiles((cur) => {
    const f = cur.find((x) => x.key === key);
    if (f && f.abort) f.abort();
    return cur.filter((x) => x.key !== key);
  });

  const sendOne = async (f, recipients) => {
    const name = f.file.name || "file";
    const mime = f.file.type || guessMime(name);
    const q = `transfers?to=${recipients.map(encodeURIComponent).join(",")}&name=${encodeURIComponent(name)}&mime=${encodeURIComponent(mime)}`;
    const up = upload(q, f.file, { onProgress: (l, tot) => patch(f.key, { progress: tot ? l / tot : 0 }) });
    patch(f.key, { status: "uploading", progress: 0, error: null, abort: up.abort });
    try {
      const r = await up.promise;
      (r && r.transfers || []).forEach(upsertTransfer);
      setFiles((cur) => cur.filter((x) => x.key !== f.key));
      return true;
    } catch (e) {
      if (e.code === "aborted") return false;
      patch(f.key, { status: "error", error: e, abort: null });
      return false;
    }
  };

  const sendAll = async () => {
    const recipients = to.filter((id) => state.peers.some((p) => p.id === id));
    if (!recipients.length) return;
    const queue = filesRef.current.filter((f) => f.status === "staged" || f.status === "error");
    let okN = 0;
    // Two uploads at a time: the local node is fast, this just keeps the UI lively.
    const workers = [0, 1].map(async () => {
      while (queue.length) {
        const f = queue.shift();
        if (await sendOne(f, recipients)) okN++;
      }
    });
    await Promise.all(workers);
    if (okN) {
      const names = recipients.map((id) => (state.peers.find((p) => p.id === id) || {}).name).filter(Boolean).join(", ");
      toast({ level: "success", title: tn("send.queued", okN), text: t("send.queuedTo", { names }) });
    }
  };

  const recipients = peers.filter((p) => to.includes(p.id));
  const offline = recipients.filter((p) => !p.online);
  const pending = files.filter((f) => f.status !== "uploading");
  const uploading = files.some((f) => f.status === "uploading");
  const total = files.reduce((a, f) => a + (f.file.size || 0), 0);

  return html`<div class=${cx("send", over && "is-dragging")}>
    <div class="send__compose">
      <${Card} class="send__card">
        <div class="send__step">
          <h2 class="send__h"><span class="send__n">1</span>${t("send.to")}</h2>
          <${DeviceChips} value=${to} onChange=${setTo} label=${t("send.to")} showOwnShortcut />
          ${offline.length > 0 && html`<p class="send__note"><${Icon} name="clock" size=${14} />${tn("send.offlineNote", offline.length, { names: offline.map((p) => p.name).join(", ") })}</p>`}
        </div>
        <div class="send__step">
          <h2 class="send__h"><span class="send__n">2</span>${t("send.what")}</h2>
          <${DropZone} onFiles=${add} title=${t("send.dropTitle")} hint=${t("send.dropHint")} />
          ${files.length > 0 && html`<ul class="staged" aria-label=${t("send.staged")}>
            ${files.map((f) => html`<li key=${f.key} class=${cx("staged__item", f.status === "error" && "is-error")}>
              <${FileIcon} name=${f.file.name} mime=${f.file.type} boxed size=${36} />
              <div class="grow">
                <div class="row row--between"><span class="ellipsis strong small" title=${f.file.name}>${f.file.name}</span>
                  <span class="faint xsmall tnum nowrap">${f.status === "uploading" ? fmtPercent(f.progress, 1) : fmtBytes(f.file.size)}</span></div>
                ${f.status === "uploading" && html`<${Progress} value=${f.progress * 100} size="sm" label=${f.file.name} />`}
                ${f.status === "error" && html`<p class="danger-text xsmall">${t("err." + f.error.code)}${f.error.message && f.error.code === "invalid" ? ` — ${f.error.message}` : ""}</p>`}
              </div>
              <${IconButton} icon="x" size="sm" label=${t("send.removeFile", { name: f.file.name })} onClick=${() => remove(f.key)} />
            </li>`)}
          </ul>`}
        </div>
        <div class="send__foot">
          <p class="send__summary">
            ${files.length ? tn("send.summaryFiles", files.length, { size: fmtBytes(total) }) : t("send.summaryNone")}
            ${recipients.length > 0 && html` → <strong>${tn("send.summaryTo", recipients.length)}</strong>`}
          </p>
          <${Button} variant="primary" icon="send" disabled=${!pending.length || !recipients.length} loading=${uploading} onClick=${sendAll}>
            ${t("send.send")}
          </${Button}>
        </div>
        ${!recipients.length && files.length > 0 && html`<${Callout} tone="info" class="mt-3">${t("send.pickRecipient")}</${Callout}>`}
      </${Card}>
    </div>
    <section class="send__list" aria-labelledby="tr-title">
      <h2 class="send__listh" id="tr-title">${t("tr.title")}</h2>
      <${TransferList} />
    </section>
    ${over && html`<div class="drop-overlay" aria-hidden="true"><div class="drop-overlay__inner"><${Icon} name="upload" size=${36} /><p>${t("send.dropOverlay")}</p></div></div>`}
  </div>`;
}
