// Settings → Own address: the mail gateway of a device (mail to and from the Internet).
// The setup of the gateway (domain, mailboxes, a mail service to send through), what it is doing now, the records the
// domain needs in the DNS with what the DNS says of each, the queue of letters on their way, a test letter.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { t, tn } from "../i18n.js";
import { devPrefix, get, post, put } from "../api.js";
import { href } from "../router.js";
import { useStore } from "../store.js";
import { fmtShortDate } from "../format.js";
import { useInterval } from "../hooks.js";
import { cx } from "../util.js";
import { DeviceChips } from "../components/devicepicker.js";
import { TechDetails } from "../components/misc.js";
import { Button, Callout, Card, Chip, CopyButton, Field, IconButton, Segmented, Skeleton, Switch } from "../components/ui.js";
import { toast, toastError } from "../components/toast.js";

const EMPTY_RELAY = { host: "", port: "", username: "", password: "", mode: "starttls", spfInclude: "" };
const BOX_RE = /^[a-z0-9][a-z0-9._+-]{0,63}$/;
const DOMAIN_RE = /^(?=.{3,253}$)([^\s.@/:]+\.)+[^\s.@/:]{2,}$/;

/** The editable copy of the setup the node reports. */
function toDraft(v) {
  return {
    enabled: !!v.enabled, domain: v.domain || "", host: v.host || "", listen: v.listen || "", publicIPv4: v.publicIPv4 || "",
    mailboxes: (v.mailboxes || []).map((b) => ({ name: b.name, devices: [...(b.devices || [])] })),
    relayOn: !!v.relay,
    relay: v.relay ? { host: v.relay.host || "", port: String(v.relay.port || ""), username: v.relay.username || "", password: "", mode: v.relay.mode || "starttls", spfInclude: v.relay.spfInclude || "" } : { ...EMPTY_RELAY },
  };
}

function toBody(d, v) {
  return {
    enabled: d.enabled, domain: d.domain.trim(), host: d.host.trim(), listen: d.listen.trim(), dkimSelector: v.dkimSelector || "", publicIPv4: d.publicIPv4.trim(),
    mailboxes: d.mailboxes.map((b) => ({ name: b.name.trim().toLowerCase(), devices: b.devices })),
    relay: d.relayOn && d.relay.host.trim()
      ? { host: d.relay.host.trim(), port: Number(d.relay.port) || 0, username: d.relay.username.trim(), password: d.relay.password, mode: d.relay.mode, spfInclude: d.relay.spfInclude.trim() }
      : null,
  };
}

/** What is wrong with the setup, in words, per field (the node checks again and has the last word). */
function problems(d) {
  const p = {};
  const domain = d.domain.trim();
  if (domain && !DOMAIN_RE.test(domain)) p.domain = t("mailgw.err.domainForm");
  else if (d.enabled && !domain) p.domain = t("mailgw.err.domain");
  if (d.enabled && !d.mailboxes.length) p.boxes = t("mailgw.err.boxes");
  const seen = new Set();
  d.mailboxes.forEach((b, i) => {
    const name = b.name.trim().toLowerCase();
    if (!BOX_RE.test(name)) p["box" + i] = t("mailgw.err.boxName");
    else if (seen.has(name)) p["box" + i] = t("mailgw.err.boxDup");
    else if (!b.devices.length) p["box" + i] = t("mailgw.err.boxDevices");
    seen.add(name);
  });
  if (d.relayOn && !d.relay.host.trim()) p.relayHost = t("mailgw.err.relayHost");
  if (d.publicIPv4.trim() && !/^\d{1,3}(\.\d{1,3}){3}$/.test(d.publicIPv4.trim())) p.publicIp = t("mailgw.err.publicIp");
  return p;
}

// ---------------------------------------------------------------- state of the gateway
function StatusCard({ st, queueN }) {
  if (!st) return null;
  const on = st.enabled && st.running;
  const tone = !st.enabled ? "neutral" : on && st.listening ? "ok" : "warn";
  const label = !st.enabled ? t("mailgw.st.off") : on && st.listening ? t("mailgw.st.on") : t("mailgw.st.sendOnly");
  const kind = ["permission", "inuse"].includes(st.listenErrorKind) ? st.listenErrorKind : "other";
  return html`<${Card} class="mgw-status" data-testid="mailgw-status" data-state=${!st.enabled ? "off" : on && st.listening ? "on" : "partial"}>
    <div class="row row--wrap gap-3">
      <${Chip} tone=${tone} dot>${label}</${Chip}>
      ${st.listening && st.listenAddr && html`<span class="small faint mono">${t("mailgw.st.listening", { addr: st.listenAddr })}</span>`}
      ${queueN > 0 && html`<span class="small faint">${tn("mailgw.st.queue", queueN)}</span>`}
    </div>
    ${st.enabled && st.listenError && html`<${Callout} tone="err" title=${t("mailgw.listenErr.title")} class="mgw-gap" data-testid="mailgw-listen-error">
      ${t("mailgw.listenErr." + kind)}
      <${TechDetails}><code class="mono small">${st.listenError}</code></${TechDetails}>
    </${Callout}>`}
  </${Card}>`;
}

// ---------------------------------------------------------------- setup
function Mailbox({ b, i, domain, members, error, onChange, onRemove }) {
  return html`<div class=${cx("mgw-box", error && "has-error")} data-testid="mailgw-box">
    <div class="mgw-box__head">
      <input class="input mono mgw-box__name" value=${b.name} placeholder=${t("mailgw.boxNamePh")} aria-label=${t("mailgw.boxName")} spellcheck="false" autocapitalize="off"
        data-testid="mailgw-box-name" onInput=${(e) => onChange({ ...b, name: e.target.value })} />
      <span class="mgw-box__at mono small faint ellipsis">@${domain || "…"}</span>
      <${IconButton} icon="trash" size="sm" label=${t("mailgw.boxRemove", { name: b.name || String(i + 1) })} onClick=${onRemove} data-testid="mailgw-box-remove" />
    </div>
    <p class="xsmall faint mgw-box__label" id=${"mgw-bd-" + i}>${t("mailgw.boxDevices")}</p>
    <${DeviceChips} value=${b.devices} onChange=${(devices) => onChange({ ...b, devices })} peers=${members} label=${t("mailgw.boxDevices")} />
    ${error && html`<p class="field__error" role="alert">${error}</p>`}
  </div>`;
}

function SetupCard({ view, draft, setDraft, dirty, saving, onSave, fieldErrors, serverError }) {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  // every device of the mesh may have a mailbox, this one (whose own entry is not among its peers) too
  const members = self ? [{ ...self, online: true, path: "direct", lastSeen: Math.floor(Date.now() / 1000) }, ...peers] : peers;
  const set = (patch) => setDraft((d) => ({ ...d, ...patch }));
  const setRelay = (patch) => setDraft((d) => ({ ...d, relay: { ...d.relay, ...patch } }));
  const defHost = draft.domain.trim() ? "mail." + draft.domain.trim().toLowerCase() : view.defaults.host;
  const status = view.status || {};
  const found = status.detectedIPv4 || status.publicIPv4 || "";
  return html`<${Card} class="stack stack--lg" data-testid="mailgw-setup">
    <${Switch} label=${t("mailgw.enable")} description=${t("mailgw.enableHint")} checked=${draft.enabled} onChange=${(v) => set({ enabled: v })} testid="mailgw-enable" />
    <${Field} label=${t("mailgw.domain")} hint=${t("mailgw.domainHint")} error=${fieldErrors.domain}>
      ${(id, d) => html`<input id=${id} class="input mono" value=${draft.domain} placeholder="example.org" aria-describedby=${d} spellcheck="false" autocapitalize="off" inputmode="url"
        data-testid="mailgw-domain" onInput=${(e) => set({ domain: e.target.value })} />`}
    </${Field}>
    <div class="field">
      <span class="field__label">${t("mailgw.boxes")}</span>
      <p class="field__hint">${t("mailgw.boxesHint")}</p>
      <div class="stack stack--sm mgw-boxes">
        ${draft.mailboxes.map((b, i) => html`<${Mailbox} key=${i} b=${b} i=${i} domain=${draft.domain.trim().toLowerCase()} members=${members} error=${fieldErrors["box" + i]}
          onChange=${(nb) => set({ mailboxes: draft.mailboxes.map((x, j) => (j === i ? nb : x)) })}
          onRemove=${() => set({ mailboxes: draft.mailboxes.filter((_, j) => j !== i) })} />`)}
        ${!draft.mailboxes.length && html`<p class="small faint" data-testid="mailgw-no-boxes">${t("mailgw.boxNone")}</p>`}
        ${fieldErrors.boxes && html`<p class="field__error" role="alert">${fieldErrors.boxes}</p>`}
        <div><${Button} size="sm" icon="plus" data-testid="mailgw-add-box"
          onClick=${() => set({ mailboxes: [...draft.mailboxes, { name: draft.mailboxes.length ? "" : (self && self.owner ? self.owner.toLowerCase().replace(/[^a-z0-9._+-]/g, "") : ""), devices: self ? [self.id] : [] }] })}>${t("mailgw.boxAdd")}</${Button}></div>
      </div>
    </div>
    <${TechDetails} summary=${t("mailgw.advanced")}>
      <div class="stack stack--lg" data-testid="mailgw-advanced">
        <div class="mgw-relay stack">
          <${Switch} label=${t("mailgw.relay")} description=${t("mailgw.relayHint")} checked=${draft.relayOn} onChange=${(v) => set({ relayOn: v })} testid="mailgw-relay-switch" />
          ${draft.relayOn && html`<div class="stack">
            <div class="mgw-grid">
              <${Field} label=${t("mailgw.relayHost")} error=${fieldErrors.relayHost}>
                ${(id) => html`<input id=${id} class="input mono" value=${draft.relay.host} placeholder="smtp.example.net" spellcheck="false" autocapitalize="off" data-testid="mailgw-relay-host"
                  onInput=${(e) => setRelay({ host: e.target.value })} />`}
              </${Field}>
              <${Field} label=${t("mailgw.relayPort")}>
                ${(id) => html`<input id=${id} class="input mono" value=${draft.relay.port} placeholder=${draft.relay.mode === "tls" ? "465" : draft.relay.mode === "plain" ? "25" : "587"} inputmode="numeric" data-testid="mailgw-relay-port"
                  onInput=${(e) => setRelay({ port: e.target.value.replace(/\D/g, "") })} />`}
              </${Field}>
            </div>
            <div class="field"><span class="field__label">${t("mailgw.relayMode")}</span>
              <${Segmented} label=${t("mailgw.relayMode")} class="seg--wrap" value=${draft.relay.mode} onChange=${(v) => setRelay({ mode: v })}
                options=${[{ value: "starttls", label: t("mailgw.relayModeStarttls"), testid: "mailgw-mode-starttls" }, { value: "tls", label: t("mailgw.relayModeTls"), testid: "mailgw-mode-tls" },
                  { value: "plain", label: t("mailgw.relayModePlain"), testid: "mailgw-mode-plain" }]} /></div>
            <div class="mgw-grid">
              <${Field} label=${t("mailgw.relayUser")}>
                ${(id) => html`<input id=${id} class="input" value=${draft.relay.username} autocomplete="off" spellcheck="false" autocapitalize="off" data-testid="mailgw-relay-user"
                  onInput=${(e) => setRelay({ username: e.target.value })} />`}
              </${Field}>
              <${Field} label=${t("mailgw.relayPass")} hint=${view.relay && view.relay.passwordSet && !draft.relay.password ? t("mailgw.relayPassSet") : undefined}>
                ${(id, d) => html`<input id=${id} class="input" type="password" value=${draft.relay.password} autocomplete="new-password" aria-describedby=${d} data-testid="mailgw-relay-pass"
                  onInput=${(e) => setRelay({ password: e.target.value })} />`}
              </${Field}>
            </div>
            <${Field} label=${t("mailgw.relaySpf")} hint=${t("mailgw.relaySpfHint")}>
              ${(id, d) => html`<input id=${id} class="input mono" value=${draft.relay.spfInclude} placeholder="_spf.example.net" aria-describedby=${d} spellcheck="false" autocapitalize="off" data-testid="mailgw-relay-spf"
                onInput=${(e) => setRelay({ spfInclude: e.target.value })} />`}
            </${Field}>
          </div>`}
        </div>
        <${Field} label=${t("mailgw.host")} hint=${t("mailgw.hostHint", { def: defHost })}>
          ${(id, d) => html`<input id=${id} class="input mono" value=${draft.host} placeholder=${defHost} aria-describedby=${d} spellcheck="false" autocapitalize="off" data-testid="mailgw-host"
            onInput=${(e) => set({ host: e.target.value })} />`}
        </${Field}>
        <${Field} label=${t("mailgw.listen")} hint=${t("mailgw.listenHint", { def: view.defaults.listen })}>
          ${(id, d) => html`<input id=${id} class="input mono" value=${draft.listen} placeholder=${view.defaults.listen} aria-describedby=${d} spellcheck="false" autocapitalize="off" data-testid="mailgw-listen"
            onInput=${(e) => set({ listen: e.target.value })} />`}
        </${Field}>
        <${Field} label=${t("mailgw.publicIp")} hint=${t("mailgw.publicIpHint", { found: found || t("mailgw.publicIpNone") })} error=${fieldErrors.publicIp}>
          ${(id, d) => html`<input id=${id} class="input mono" value=${draft.publicIPv4} placeholder=${found || "203.0.113.7"} aria-describedby=${d} inputmode="decimal" data-testid="mailgw-public-ip"
            onInput=${(e) => set({ publicIPv4: e.target.value })} />`}
        </${Field}>
      </div>
    </${TechDetails}>
    ${serverError && html`<${Callout} tone="err" title=${t("mailgw.saveFailed")} data-testid="mailgw-save-error">${serverError}</${Callout}>`}
    <div class="row gap-3">
      <${Button} variant="primary" loading=${saving} disabled=${!dirty} onClick=${onSave} data-testid="mailgw-save">${t("mailgw.save")}</${Button}>
      ${dirty && html`<${Button} variant="ghost" onClick=${() => setDraft(toDraft(view))}>${t("common.cancel")}</${Button}>`}
    </div>
  </${Card}>`;
}

// ---------------------------------------------------------------- the records in the DNS
const REC_TONE = { ok: "ok", missing: "err", wrong: "err", unknown: "neutral" };
const REC_ICON = { ok: "checkCircle", missing: "xCircle", wrong: "alertCircle", unknown: "clock" };

/** The text for why a record is wrong (the node says a code, perhaps with a detail after a colon). */
function detailText(code) {
  if (!code) return "";
  const key = code.split(":")[0];
  const known = ["other-host", "address-unknown", "other-address", "two-records", "ip-not-allowed", "other-key", "other-name", "dns-error"];
  return known.includes(key) ? t("mailgw.detail." + key) : "";
}

function DnsRecord({ r }) {
  const label = ["mx", "a", "aaaa", "spf", "dkim", "dmarc", "ptr"].includes(r.id) ? t("mailgw.rec." + r.id) : r.id;
  const why = r.state !== "ok" ? detailText(r.detail) : "";
  return html`<li class=${cx("dnsrec", "dnsrec--" + r.state)} data-testid="mailgw-rec" data-id=${r.id} data-state=${r.state}>
    <div class="dnsrec__head">
      <${Chip} size="sm" tone=${REC_TONE[r.state] || "neutral"} icon=${REC_ICON[r.state] || "clock"}>${t("mailgw.recState." + (REC_TONE[r.state] ? r.state : "unknown"))}</${Chip}>
      <span class="strong small">${label}</span>
      <span class="xsmall faint">${r.required ? t("mailgw.rec.required") : t("mailgw.rec.optional")}</span>
    </div>
    <dl class="dnsrec__kv">
      <dt>${t("mailgw.rec.type")}</dt><dd class="mono">${r.type}</dd>
      <dt>${t("mailgw.rec.name")}</dt><dd class="mono dnsrec__val">${r.name}<${CopyButton} text=${r.name} label=${t("mailgw.rec.copy", { what: t("mailgw.rec.name").toLowerCase() })} /></dd>
      <dt>${t("mailgw.rec.value")}</dt><dd class="mono dnsrec__val">${r.value ? html`<span class="dnsrec__text">${r.value}</span><${CopyButton} text=${r.value} label=${t("mailgw.rec.copy", { what: t("mailgw.rec.value").toLowerCase() })} />` : "—"}</dd>
    </dl>
    ${r.state !== "ok" && r.found && r.found.length > 0 && html`<p class="xsmall faint dnsrec__found">${t("mailgw.recFound", { found: r.found.join(" · ") })}</p>`}
    ${why && html`<p class="xsmall warn-text dnsrec__why">${why}</p>`}
    ${r.id === "ptr" && html`<p class="xsmall faint">${t("mailgw.ptrHint")}</p>`}
  </li>`;
}

function DnsCard({ prefix, saved, canCheck }) {
  const [rep, setRep] = useState(null);
  const [busy, setBusy] = useState(false);
  const seq = useRef(0);
  const check = async () => {
    const my = ++seq.current;
    setBusy(true);
    try {
      const r = await get(prefix + "mailgw/dns");
      if (my === seq.current) setRep(r);
    } catch (e) {
      if (my === seq.current) { setRep(null); toastError(e, t("mailgw.dnsFailed")); }
    }
    if (my === seq.current) setBusy(false);
  };
  // after a change of the saved setup the records are different: ask again
  useEffect(() => { if (canCheck) check(); else setRep(null); }, [saved, canCheck]);
  const records = (rep && rep.report && rep.report.records) || [];
  const needsIp = records.some((r) => r.id === "a" && !r.value);
  return html`<${Card} class="stack" data-testid="mailgw-dns">
    <div class="row row--between row--wrap gap-3">
      <div><h3 class="strong">${t("mailgw.dns")}</h3><p class="small muted">${t("mailgw.dnsText")}</p></div>
      <${Button} size="sm" icon="refresh" loading=${busy} disabled=${!canCheck} onClick=${check} data-testid="mailgw-dns-check">${t("mailgw.dnsCheck")}</${Button}>
    </div>
    ${!canCheck && html`<p class="small faint">${t("mailgw.dnsSaveFirst")}</p>`}
    ${rep && html`<${Callout} tone=${rep.report.ready ? "ok" : "warn"} data-testid="mailgw-dns-verdict" data-ready=${String(!!rep.report.ready)}>${rep.report.ready ? t("mailgw.dnsReady") : t("mailgw.dnsNotReady")}</${Callout}>`}
    ${rep && needsIp && html`<p class="small warn-text">${t("mailgw.dnsNeedsIp")}</p>`}
    ${records.length > 0 && html`<ul class="dnsrecs">${records.map((r) => html`<${DnsRecord} key=${r.id} r=${r} />`)}</ul>`}
  </${Card}>`;
}

// ---------------------------------------------------------------- the queue and a test letter
function QueueCard({ prefix, items, reload }) {
  const retry = async () => { try { await post(prefix + "mailgw/queue/retry", {}); setTimeout(reload, 600); } catch (e) { toastError(e); } };
  const cancel = async (id) => {
    try { await post(prefix + `mailgw/queue/${encodeURIComponent(id)}/cancel`, {}); toast({ level: "success", title: t("mailgw.queueCancelled") }); reload(); }
    catch (e) { toastError(e); }
  };
  return html`<${Card} class="stack" data-testid="mailgw-queue">
    <div class="row row--between row--wrap gap-3">
      <h3 class="strong">${t("mailgw.queue")}</h3>
      ${items.length > 0 && html`<${Button} size="sm" icon="retry" onClick=${retry} data-testid="mailgw-queue-retry">${t("mailgw.queueRetry")}</${Button}>`}
    </div>
    ${items.length === 0 ? html`<p class="small faint" data-testid="mailgw-queue-empty">${t("mailgw.queueEmpty")}</p>`
      : html`<ul class="mgw-queue">${items.map((it) => html`<li key=${it.id} class="mgw-queue__item" data-testid="mailgw-queue-item" data-id=${it.id}>
        <div class="grow stack stack--sm">
          <span class="xsmall faint tnum">${fmtShortDate(it.created)} · ${it.from}</span>
          ${(it.rcpts || []).map((r) => html`<span class="small" key=${r.addr}><span class="strong">${r.addr}</span>
            <${Chip} size="sm" tone=${r.state === "failed" ? "err" : r.state === "delivered" ? "ok" : r.state === "deferred" ? "warn" : "neutral"}>${t("mail.st." + (r.state || "queued"))}</${Chip}>
            ${r.text && html`<span class="xsmall faint"> ${r.text}</span>`}</span>`)}
        </div>
        <${IconButton} icon="x" size="sm" label=${t("mailgw.queueCancel")} onClick=${() => cancel(it.id)} data-testid="mailgw-queue-cancel" />
      </li>`)}</ul>`}
  </${Card}>`;
}

function TestCard() {
  const [to, setTo] = useState("");
  const [busy, setBusy] = useState(false);
  const ok = /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]{2,}$/.test(to.trim());
  const send = async (e) => {
    e && e.preventDefault();
    if (!ok || busy) return;
    setBusy(true);
    try {
      const r = await post("mail", { to: [], emailTo: [to.trim()], subject: t("mailgw.testSubject"), body: t("mailgw.testBody") });
      toast({ level: "success", title: t("mailgw.testSent"), link: r && r.id ? href(["mail", "sent", r.id]) : null, actionLabel: t("compose.view") });
      setTo("");
    } catch (err) { toastError(err); }
    setBusy(false);
  };
  return html`<${Card} class="stack" data-testid="mailgw-test">
    <div><h3 class="strong">${t("mailgw.test")}</h3><p class="small muted">${t("mailgw.testText")}</p></div>
    <form class="savefield" onSubmit=${send}>
      <input class="input" type="email" value=${to} placeholder="name@gmail.com" aria-label=${t("mailgw.testTo")} autocapitalize="off" spellcheck="false" data-testid="mailgw-test-to"
        onInput=${(e) => setTo(e.target.value)} />
      <${Button} type="submit" variant="primary" size="sm" icon="send" loading=${busy} disabled=${!ok} data-testid="mailgw-test-send">${t("mailgw.testSend")}</${Button}>
    </form>
  </${Card}>`;
}

// ---------------------------------------------------------------- the section
export function MailGateway({ dev, deviceName }) {
  const prefix = devPrefix(dev);
  const [view, setView] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [draft, setDraft] = useState(null);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState("");
  const [tried, setTried] = useState(false);
  const [queue, setQueue] = useState([]);
  const draftBase = useRef("");

  const loadQueue = async () => {
    try { const q = await get(prefix + "mailgw/queue"); setQueue(q.items || []); } catch { /* the status shows what matters */ }
  };
  const load = async (first) => {
    try {
      const v = await get(prefix + "mailgw");
      setView(v);
      setLoadError(null);
      if (first) { const d = toDraft(v); draftBase.current = JSON.stringify(d); setDraft(d); }
      loadQueue();
    } catch (e) {
      if (first || !view) setLoadError(e);
    }
  };
  useEffect(() => { setView(null); setDraft(null); setLoadError(null); setQueue([]); load(true); }, [dev]);
  // the state changes by itself (a port taken, letters going out): look again now and then
  useInterval(() => load(false), 6000, !!view);

  if (loadError) {
    return html`<${Card}><${Callout} tone="err" title=${t("set.loadError")}>${t("err." + loadError.code)}
      <div class="mgw-gap"><${Button} size="sm" icon="refresh" onClick=${() => load(true)}>${t("common.retry")}</${Button}></div></${Callout}></${Card}>`;
  }
  if (!view || !draft) {
    return html`<${Card}><div class="stack"><${Skeleton} w="40%" h=${18} /><${Skeleton} h=${12} /><${Skeleton} w="70%" h=${12} /></div></${Card}>`;
  }
  const dirty = JSON.stringify(draft) !== draftBase.current;
  const fieldErrors = tried ? problems(draft) : {};
  const save = async () => {
    setTried(true);
    setServerError("");
    if (Object.keys(problems(draft)).length) return;
    setSaving(true);
    try {
      const v = await put(prefix + "mailgw", toBody(draft, view));
      setView(v);
      const d = toDraft(v);
      draftBase.current = JSON.stringify(d);
      setDraft(d);
      setTried(false);
      toast({ level: "success", title: t("mailgw.saved") });
      loadQueue();
    } catch (e) {
      setServerError(e.code === "invalid" && e.message ? e.message : t("err." + (e.code || "internal")));
    }
    setSaving(false);
  };
  const savedKey = JSON.stringify([view.domain, view.host, view.publicIPv4, view.relay && view.relay.spfInclude, !!view.relay, view.status && view.status.publicIPv4]);
  const running = !!(view.status && view.status.running);
  return html`<div class="stack stack--lg mgw" data-testid="mailgw">
    <${Card} class="stack">
      <p>${t("mailgw.intro")}</p>
      <p class="small faint">${dev === "self" || !dev ? t("mailgw.thisDevice") : t("mailgw.thisDeviceRemote", { name: deviceName })}</p>
      <${TechDetails} summary=${t("mailgw.need")}>
        <ul class="mgw-need">${[1, 2, 3, 4].map((i) => html`<li key=${i}>${t("mailgw.need" + i)}</li>`)}</ul>
      </${TechDetails}>
    </${Card}>
    <${StatusCard} st=${view.status} queueN=${queue.length} />
    <${SetupCard} view=${view} draft=${draft} setDraft=${setDraft} dirty=${dirty} saving=${saving} onSave=${save} fieldErrors=${fieldErrors} serverError=${serverError} />
    ${view.domain && view.mailboxes.length > 0 && html`<${DnsCard} prefix=${prefix} saved=${savedKey} canCheck=${true} />`}
    ${(running || queue.length > 0) && html`<${QueueCard} prefix=${prefix} items=${queue} reload=${loadQueue} />`}
    ${view.enabled && running && view.mailboxes.length > 0 && html`<${TestCard} />`}
  </div>`;
}
