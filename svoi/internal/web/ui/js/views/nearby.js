// Devices nearby (docs/UI-API.md → "Devices nearby").
//
// Every device around with The Mesh open is listed — the ones in a mesh that can add this one, and the ones that are not
// in a mesh yet — and one tap connects: this device asks to be added, invites the other one, or the two make a mesh
// together (the node works out which). The other side answers in a tap too: an invitation shows here with Принять /
// Отклонить; a request to be added opens a dialog by itself (and stays as a row until it is answered). No code to type,
// no QR to scan: both screens only show the same six digits, for whoever wants to be sure.
// While the list is on the screen the interface tells the node that the person looks (POST nearby/look every half a
// minute): only then does a device that is not in a mesh make itself known by name.
// The node does the work and keeps the state; this file is its face.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { post } from "../api.js";
import { nearbyEvents, nearbyOf, nowSec, setState, state, useStore } from "../store.js";
import { useNow } from "../hooks.js";
import { fmtCountdown } from "../format.js";
import { DeviceAvatar } from "../components/avatar.js";
import { Button, Callout, Field, Spinner } from "../components/ui.js";
import { Modal } from "../components/modal.js";
import { DnsPreview, PageHeader } from "../components/misc.js";
import { toast, toastError } from "../components/toast.js";
import { cx, normalizeDeviceName, osName } from "../util.js";
import { offeredName, validateName } from "../naming.js";
import { AddDeviceModal } from "./add-device.js";

const RUNNING = new Set(["connecting", "waiting", "confirmed"]);
const enc = encodeURIComponent;

/** "482913" → "482 913": easier to compare across two screens. */
export function spaced(code) {
  const c = String(code || "").replace(/\s+/g, "");
  return c.length === 6 ? `${c.slice(0, 3)} ${c.slice(3)}` : c;
}

/**
 * What to say when discovery of devices on the home network does not work (self.lan, from the node): the system refuses
 * to send there (on a Mac: the "Local Network" permission is off), it fails some other way, or a VPN takes the home
 * network's traffic (a phone can tell: self.lan.vpn is "lan"). Nothing for the ordinary "not on Wi-Fi right now", nor for
 * a VPN that most likely lets the home network go around it ("on": said only while nobody is found, see searching).
 */
export function lanNotice(self) {
  const lan = self && self.lan;
  if (!lan) return null;
  if (lan.problem === "failed") return { text: t("home.att.lanFailed"), sub: lan.detail || "" };
  if (lan.problem === "blocked") {
    const mac = self.os === "darwin";
    return { text: t(mac ? "home.att.lanBlockedMac" : "home.att.lanBlocked"), sub: t(mac ? "home.att.lanBlockedMacHow" : "home.att.lanBlockedHow") };
  }
  if (lan.vpn === "lan") return { text: t("home.att.lanVpn"), sub: t("home.att.lanVpnHow") };
  return null;
}

/**
 * While a VPN takes the home network's traffic, what to do about it — under a list of devices too: they are heard, but
 * they get what this device sends only if the VPN lets local addresses through.
 */
function VpnNote({ self }) {
  if (!self || !self.lan || self.lan.vpn !== "lan") return null;
  return html`<p class="nb-vpn" role="note" data-testid="nearby-vpn"><${Icon} name="alert" size=${16} />
    <span><strong>${t("home.att.lanVpn")}.</strong> ${t("home.att.lanVpnHow")}</span></p>`;
}

/**
 * Takes the answer to a request about devices nearby. The answer is a snapshot from when the request was served; the
 * events that followed are fresher, so an attempt that is running already is never taken back to what the answer says.
 */
function takeAnswer(view) {
  const next = nearbyOf(view);
  setState((s) => (RUNNING.has(s.nearby.join.state) ? { nearby: { ...next, join: s.nearby.join } } : { nearby: next }));
}

async function ask(path, body) {
  const events = nearbyEvents();
  const v = await post(path, body);
  // An event that came while the request was served is fresher than the answer: when the administrator allows a device,
  // the node answers at once and forgets the request a moment later; the event about that must not be undone by the answer.
  if (nearbyEvents() === events) takeAnswer(v);
  return v;
}

/** The six digits, big, for whoever wants to compare them with the other screen. */
function Code({ code, label, testid }) {
  return html`<div class="nearby-code" data-testid=${testid}>
    <span class="nearby-code__label">${label}</span>
    <span class="nearby-code__digits tnum" aria-label=${String(code).split("").join(" ")} data-code=${code}>${spaced(code)}</span>
  </div>`;
}

// ------------------------------------------------------------------------ looking

// The name this device gives while it is not in a mesh (chosen on the start screen), kept for "try again".
let chosenName = "";
const myName = () => normalizeDeviceName(chosenName || offeredName(state.self));

let lookers = 0;
let lookTimer = 0;
function lookNow() {
  if (document.visibilityState === "hidden") return;
  ask("nearby/look", { name: state.configured ? "" : myName() }).catch(() => { /* the next one */ });
}
if (typeof document !== "undefined") document.addEventListener("visibilitychange", () => { if (lookers && document.visibilityState === "visible") lookNow(); });

/** While the component is on the screen, the person looks at the list of devices nearby: the node is told so. */
export function useLooking() {
  useEffect(() => {
    if (++lookers === 1) {
      lookNow();
      lookTimer = setInterval(lookNow, 30000);
    }
    return () => {
      if (--lookers === 0) {
        clearInterval(lookTimer);
        post("nearby/look", { on: false }).catch(() => {});
      }
    };
  }, []);
}

// ------------------------------------------------------------------------ what a tap does

function failed(e) {
  if (e.code === "notfound") toast({ level: "warn", title: t("nearby.gone") });
  else if (e.code === "offline") toast({ level: "warn", title: t("nearby.noAdmin"), text: t("nearby.noAdminText") });
  else toastError(e);
}

async function connectTo(d) {
  try {
    await ask("nearby/connect", { id: d.id, deviceName: myName(), meshName: t("nearby.defaultMesh") });
  } catch (e) {
    failed(e);
  }
}

async function answerInvite(inv, accept) {
  try {
    await ask(`nearby/invites/${enc(inv.id)}`, { accept, deviceName: myName(), meshName: t("nearby.defaultMesh") });
    if (!accept) toast({ level: "info", title: t("nearby.declined", { name: inv.name }) });
  } catch (e) {
    failed(e);
  }
}

async function takeBack(id) {
  try {
    await ask(`nearby/sent/${enc(id)}/cancel`);
  } catch (e) {
    if (e.code !== "notfound") toastError(e);
  }
}

// ------------------------------------------------------------------------ the list

/** How an invitation this device sent goes (on the row of the device it went to). */
function SentState({ s, onAgain }) {
  const [busy, setBusy] = useState(false);
  const wait = s.state === "sent" || s.state === "joining";
  const text = {
    sent: s.kind === "pair" ? t("nearby.sent.pair") : t("nearby.sent.invite"),
    joining: t("nearby.sent.joining"), joined: t("nearby.sent.joined"), declined: t("nearby.sent.declined"),
    expired: t("nearby.sent.expired"), failed: t("nearby.sent.failed"),
  }[s.state] || s.state;
  const run = async (f) => { setBusy(true); await f(); setBusy(false); };
  return html`<div class=${cx("nb-sent", `nb-sent--${s.state}`)} data-testid="nearby-sent" data-state=${s.state} role="status" aria-live="polite">
    <span class="nb-sent__text">
      ${wait ? html`<${Spinner} size=${14} />` : html`<${Icon} name=${s.state === "joined" ? "checkCircle" : "info"} size=${15} />`}
      <span>${text}</span>
    </span>
    ${s.state === "sent" && html`<${Button} size="sm" variant="ghost" loading=${busy} onClick=${() => run(() => takeBack(s.id))} data-testid="nearby-sent-cancel">${t("common.cancel")}</${Button}>`}
    ${["declined", "expired", "failed"].includes(s.state) && onAgain && html`<${Button} size="sm" variant="secondary" icon="refresh" loading=${busy}
      onClick=${() => run(async () => { await takeBack(s.id); await onAgain(); })} data-testid="nearby-sent-again">${t("nearby.again")}</${Button}>`}
  </div>`;
}

function InviteRow({ inv }) {
  const [busy, setBusy] = useState("");
  const run = (accept) => async () => {
    setBusy(accept ? "accept" : "decline");
    await answerInvite(inv, accept);
    setBusy("");
  };
  return html`<li class="nb-row nb-row--invite" data-testid="nearby-invite" data-id=${inv.id} data-kind=${inv.kind} data-name=${inv.name}>
    <${DeviceAvatar} dev=${{ name: inv.name, os: inv.os }} size=${44} showStatus=${false} />
    <div class="grow nb-row__who">
      <p class="nb-row__name ellipsis">${inv.name}</p>
      <p class="nb-row__sub">${inv.kind === "invite" ? t("nearby.inviteTo", { mesh: inv.meshName || "—" }) : t("nearby.wantsPair")}</p>
    </div>
    <div class="nb-row__actions">
      <${Button} size="sm" variant="ghost" loading=${busy === "decline"} onClick=${run(false)} data-testid="nearby-decline">${t("nearby.decline")}</${Button}>
      <${Button} size="sm" variant="primary" icon="check" loading=${busy === "accept"} onClick=${run(true)} data-testid="nearby-accept">${t("nearby.accept")}</${Button}>
    </div>
  </li>`;
}

function RequestRow({ r }) {
  return html`<li class="nb-row nb-row--invite" data-testid="nearby-request" data-id=${r.id}>
    <${DeviceAvatar} dev=${{ name: r.name, os: r.os }} size=${44} showStatus=${false} />
    <div class="grow nb-row__who">
      <p class="nb-row__name ellipsis">${r.name}</p>
      <p class="nb-row__sub tnum">${t("nearby.asks")} · ${t("home.att.nearbyCode", { code: spaced(r.code) })}</p>
    </div>
    <div class="nb-row__actions">
      <${Button} size="sm" variant="primary" onClick=${() => showRequest(r.id)} data-testid="nearby-request-open">${t("home.att.nearbyGo")}</${Button}>
    </div>
  </li>`;
}

function DeviceRow({ d, sent, configured, canAdd }) {
  const [busy, setBusy] = useState(false);
  const go = async () => {
    if (!configured && validateName(myName())) return;
    setBusy(true);
    await connectTo(d);
    setBusy(false);
  };
  const label = d.kind === "mesh" ? t("nearby.connect") : configured ? t("nearby.add") : t("nearby.pair");
  const sub = [d.kind === "mesh" ? t("nearby.mesh", { mesh: d.meshName }) : t("nearby.notInMesh"), d.os && osName(d.os)].filter(Boolean).join(" · ");
  const blocked = configured && !canAdd;
  return html`<li class="nb-row" data-testid="nearby-device" data-id=${d.id} data-name=${d.name} data-kind=${d.kind}>
    <${DeviceAvatar} dev=${{ name: d.name, os: d.os }} size=${44} showStatus=${false} />
    <div class="grow nb-row__who">
      <p class="nb-row__name ellipsis">${d.name}</p>
      <p class="nb-row__sub ellipsis">${blocked ? t("nearby.noAdminShort") : sub}</p>
    </div>
    ${sent ? html`<${SentState} s=${sent} onAgain=${go} />`
      : html`<${Button} variant="primary" size="sm" icon=${d.kind === "mesh" ? "link" : "plus"} loading=${busy} disabled=${blocked} onClick=${go}
          aria-label=${t("nearby.connectTo", { name: d.name })} data-testid="nearby-connect">${label}</${Button}>`}
  </li>`;
}

/**
 * The list: invitations to answer first, then the devices around (each with what its tap does, or how the invitation sent
 * to it goes), then invitations that went to devices no longer heard. `limit`: at most that many rows, and a link to the
 * whole list.
 */
export function NearbyList({ limit = 0, empty = null }) {
  const nb = useStore((s) => s.nearby);
  const configured = useStore((s) => s.configured);
  const sentTo = new Map(nb.sent.map((s) => [s.id, s]));
  const listed = new Set(nb.devices.map((d) => d.id));
  const invitedBy = new Set(nb.invites.map((i) => i.id));
  const rows = [
    ...(configured ? nb.requests.map((r) => html`<${RequestRow} key=${"r" + r.id} r=${r} />`) : []),
    ...nb.invites.map((inv) => html`<${InviteRow} key=${"i" + inv.id} inv=${inv} />`),
    ...nb.devices.filter((d) => !invitedBy.has(d.id)).map((d) => html`<${DeviceRow} key=${"d" + d.id} d=${d} sent=${sentTo.get(d.id)} configured=${configured} canAdd=${nb.canAdd} />`),
    ...nb.sent.filter((s) => !listed.has(s.id) && !invitedBy.has(s.id)).map((s) => html`<li key=${"s" + s.id} class="nb-row" data-testid="nearby-device" data-id=${s.id} data-name=${s.name} data-gone="true">
      <${DeviceAvatar} dev=${{ name: s.name, os: s.os }} size=${44} showStatus=${false} />
      <div class="grow nb-row__who"><p class="nb-row__name ellipsis">${s.name}</p><p class="nb-row__sub">${s.os ? osName(s.os) : ""}</p></div>
      <${SentState} s=${s} />
    </li>`),
  ];
  if (!rows.length) return empty;
  const shown = limit ? rows.slice(0, limit) : rows;
  return html`<ul class="nb-list" data-testid="nearby-list">${shown}</ul>
    ${limit > 0 && rows.length > limit && html`<a class="nb-more" href="#/nearby" data-testid="nearby-more">${tn("nearby.more", rows.length - limit)}<${Icon} name="chevronRight" size=${16} /></a>`}`;
}

/** While nobody is around, what to say: that the app is looking (and the hint), or what is wrong when it cannot. */
function searching(self, hint) {
  const trouble = lanNotice(self);
  const lan = (self && self.lan) || {};
  const noNetwork = lan.problem === "no-network";
  return {
    title: trouble ? trouble.text : noNetwork ? t("lan.noNetwork") : t("nearby.searching"),
    // a VPN is on, and nobody is found: the likeliest reason, and what to do
    text: trouble ? trouble.sub : noNetwork ? "" : lan.vpn === "on" ? t("nearby.vpnHint") : hint,
  };
}

/** While nobody is around: that the app is looking, or what is wrong when it cannot. */
function Searching({ self, hint }) {
  const say = searching(self, hint);
  return html`<div class="nb-empty" data-testid="nearby-searching">
    <span class="nearby__radar nb-empty__radar" aria-hidden="true"><${Icon} name="radar" size=${26} /></span>
    <div class="grow">
      <p class="nb-empty__title" role="status" aria-live="polite">${say.title}</p>
      <p class="nb-empty__text">${say.text}</p>
    </div>
  </div>`;
}

function NearbyName({ self, name, setName }) {
  const placeholder = offeredName(self);
  const [editing, setEditing] = useState(false);
  const shown = normalizeDeviceName(name || placeholder);
  const problem = name ? validateName(shown) : "";
  const done = () => {
    setEditing(false);
    chosenName = normalizeDeviceName(name);
    lookNow(); // the devices around see the new name at once
  };
  if (!editing) {
    return html`<div class="nearby__me">
      <span>${t("nearby.name")}</span>
      <strong class="mono" data-testid="nearby-name">${shown}</strong>
      <button type="button" class="linklike" onClick=${() => setEditing(true)} data-testid="nearby-rename">${t("nearby.rename")}</button>
    </div>`;
  }
  return html`<div class="nearby__rename">
    <${Field} label=${t("onb.deviceName")} hint=${t("onb.deviceNameHint")} error=${problem} extra=${html`<${DnsPreview} name=${name.trim() ? name : placeholder} />`}>
      ${(id, d) => html`<input id=${id} class="input" value=${name} placeholder=${placeholder} maxlength="63" autocapitalize="off" spellcheck="false" autofocus
        data-testid="nearby-name-input" aria-describedby=${d} aria-invalid=${problem ? "true" : undefined}
        onInput=${(e) => setName(e.target.value)} onBlur=${() => setName(normalizeDeviceName(name))}
        onKeyDown=${(e) => { if (e.key === "Enter" && !problem) done(); }} />`}
    </${Field}>
    <${Button} size="sm" variant="secondary" disabled=${!!problem} onClick=${done}>${t("nearby.nameDone")}</${Button}>
  </div>`;
}

/**
 * The start screen's list (a device that is not in a mesh): everybody around with The Mesh open, invitations to this
 * device, and the name it is seen under.
 */
export function NearbySection({ self }) {
  const nb = useStore((s) => s.nearby);
  const off = useStore((s) => !!s.settings && s.settings.lan === false);
  const [name, setName] = useState(chosenName);
  useLooking();
  if (off) return null; // the person turned looking around on the network off
  const found = nb.devices.length + nb.invites.length + nb.sent.length;
  // While nobody is around, the head says so itself, with the one radar of the section (no second one in an empty list)
  const say = !found && searching(self, t("nearby.searchingHint"));
  return html`<section class=${cx("nearby", found && "nearby--found")} data-testid="nearby" data-found=${String(nb.devices.length)} aria-labelledby="nearby-title">
    <header class="nearby__head">
      <span class="nearby__radar" aria-hidden="true"><${Icon} name="radar" size=${24} /></span>
      <div class="grow">
        <h2 class="nearby__title" id="nearby-title">${found ? tn("nearby.found", nb.devices.length + nb.invites.length) : t("nearby.title")}</h2>
        ${found
          ? html`<p class="nearby__sub">${t("nearby.lead")}</p>`
          : html`<p class="nearby__sub" role="status" aria-live="polite" data-testid="nearby-searching">${say.title}</p>`}
      </div>
    </header>
    ${found ? html`<${NearbyList} /><${VpnNote} self=${self} />` : html`<p class="nearby__hint">${say.text}</p>`}
    <${NearbyName} self=${self} name=${name} setName=${setName} />
  </section>`;
}

/** The overview's panel (a device in a mesh): the first few devices around, and the way to the whole list. */
export function NearbyPanel() {
  const self = useStore((s) => s.self);
  useLooking();
  return html`<section class="fm-sec nearby nearby--panel" data-testid="nearby" aria-labelledby="fm-nearby-title">
    <div class="fm-sec__head">
      <h2 class="fm-sec__title" id="fm-nearby-title"><${Icon} name="radar" size=${18} />${t("nav.nearby")}</h2>
      <a class="fm-sec__link" href="#/nearby" data-testid="nearby-all">${t("nearby.all")}<${Icon} name="chevronRight" size=${16} /></a>
    </div>
    <${NearbyList} limit=${4} empty=${html`<${Searching} self=${self} hint=${t("nearby.searchingHintMember")} />`} />
  </section>`;
}

/** The place «Рядом» (a device in a mesh): the whole list, and what to do when the device is not nearby. */
export function NearbyView() {
  const self = useStore((s) => s.self);
  const nb = useStore((s) => s.nearby);
  const [adding, setAdding] = useState(false);
  useLooking();
  return html`<div class="page page--narrow nearby-place">
    <${PageHeader} title=${t("nav.nearby")} subtitle=${t("nearby.placeSub")} />
    <section class="nearby nearby--place" data-testid="nearby" data-found=${String(nb.devices.length)}>
      <${NearbyList} empty=${html`<${Searching} self=${self} hint=${t("nearby.searchingHintMember")} />`} />
      ${nb.devices.length > 0 && html`<${VpnNote} self=${self} />`}
      <p class="faint small nb-visible">${nb.visible ? t("nearby.visibleOn") : t("nearby.visibleOff")} <a href="#/settings/network">${t("nearby.visibleWhere")}</a></p>
    </section>
    ${!nb.canAdd && html`<${Callout} tone="info" icon="info" class="mt-4" data-testid="nearby-no-admin">${t("nearby.noAdminText")}</${Callout}>`}
    <div class="nb-far" data-testid="nearby-far">
      <span class="nb-far__icon" aria-hidden="true"><${Icon} name="qr" size=${22} /></span>
      <div class="grow">
        <p class="strong">${t("nearby.far")}</p>
        <p class="muted small">${t("nearby.farText")}</p>
      </div>
      <${Button} variant="secondary" icon="ticket" onClick=${() => setAdding(true)} data-testid="nearby-far-invite">${t("nearby.farGo")}</${Button}>
    </div>
    ${adding && html`<${AddDeviceModal} onClose=${() => setAdding(false)} />`}
  </div>`;
}

// ------------------------------------------------------------------------ the new device: its request

/** What to check when the device nearby did not answer. */
function ReachTips({ name }) {
  return html`<div data-testid="nearby-reach-help">
    <p>${t("nearby.join.offline", { name })}</p>
    <ul class="onb-tips">
      <li>${t("nearby.join.tip1")}</li>
      <li>${t("nearby.join.tip2")}</li>
      <li>${t("nearby.join.tip3")}</li>
    </ul>
  </div>`;
}

/**
 * The attempt of this device to be added, from "connecting" to the end (`join` is state.nearby.join). It takes the place
 * of the start screen's choices for as long as it lasts.
 */
export function NearbyJoin({ join }) {
  const peer = join.peer || { name: "?" };
  const [busy, setBusy] = useState("");
  const st = join.state;
  const gone = useStore((s) => !s.nearby.devices.some((d) => peer.id && d.id === peer.id));

  const cancel = async () => {
    setBusy("cancel");
    try {
      await ask("nearby/cancel");
    } catch (e) {
      if (e.code !== "notfound") toastError(e);
    }
    setBusy("");
  };
  const again = async () => {
    setBusy("again");
    await connectTo(peer);
    setBusy("");
  };

  if (st === "connecting") {
    return html`<div class="onb-form onb-progress nearby-join" role="status" aria-live="polite" data-testid="nearby-join" data-state=${st}>
      <div class="onb-progress__rings" aria-hidden="true"><span></span><span></span><span></span><${DeviceAvatar} dev=${peer} size=${56} showStatus=${false} /></div>
      <h2 class="onb-form__title center">${t("nearby.join.connecting", { name: peer.name })}</h2>
      <p class="muted center">${t("nearby.join.connectingText")}</p>
      <${Button} variant="ghost" loading=${busy === "cancel"} onClick=${cancel} data-testid="nearby-cancel">${t("nearby.join.cancel")}</${Button}>
    </div>`;
  }

  if (st === "waiting" || st === "confirmed") {
    return html`<div class="onb-form nearby-join" data-testid="nearby-join" data-state=${st}>
      <div class="onb-form__head">
        <${DeviceAvatar} dev=${peer} size=${48} showStatus=${false} />
        <div>
          <h2 class="onb-form__title">${t("nearby.join.waitTitle", { name: peer.name })}</h2>
          <p class="muted" role="status" aria-live="polite">${t("nearby.join.waitText", { name: peer.name })}</p>
        </div>
      </div>
      <div class="invite__wait" role="status" data-testid="nearby-waiting-approval">
        <span class="invite__pulse" aria-hidden="true"></span><span>${t("nearby.join.waitShort")}</span>
      </div>
      ${join.code && html`<${Code} code=${join.code} label=${t("nearby.join.codeLabel")} testid="nearby-code" />`}
      <p class="faint small">${t("nearby.join.codeHint")}</p>
      <${Button} variant="ghost" block loading=${busy === "cancel"} onClick=${cancel} data-testid="nearby-cancel">${t("nearby.join.cancel")}</${Button}>
    </div>`;
  }

  if (st === "joined") {
    return html`<div class="onb-form onb-progress nearby-join" role="status" data-testid="nearby-join" data-state=${st}>
      <span class="nearby-join__done" aria-hidden="true"><${Icon} name="checkCircle" size=${44} /></span>
      <h2 class="onb-form__title center">${t("onb.joined")}</h2>
      <p class="muted center">${t("onb.joinedText")}</p>
      <${Spinner} size=${18} />
    </div>`;
  }

  // denied | failed
  const reason = join.reason || (st === "denied" ? "denied" : "failed");
  const title = reason === "denied" ? t("nearby.join.deniedTitle", { name: peer.name })
    : reason === "expired" ? t("nearby.join.expiredTitle") : t("nearby.join.failedTitle");
  const body = reason === "offline" ? html`<${ReachTips} name=${peer.name} />`
    : reason === "denied" ? html`<p>${t("nearby.join.denied")}</p>`
    : reason === "expired" ? html`<p>${t("nearby.join.expired")}</p>`
    : reason === "noadmin" ? html`<p>${t("nearby.join.noadmin", { name: peer.name })}</p>`
    : html`<p>${reason === "invalid" ? t("nearby.join.invalid") : t("nearby.join.broken", { name: peer.name })}</p>${join.error && html`<p class="mono xsmall mt-1">${join.error}</p>`}`;
  return html`<div class="onb-form nearby-join" data-testid="nearby-join" data-state=${st} data-reason=${reason}>
    <${Callout} tone="err" title=${title} role="alert" data-testid="nearby-error">${body}</${Callout}>
    <div class="nearby-join__actions">
      ${!gone && html`<${Button} variant="primary" size="lg" block icon="refresh" loading=${busy === "again"} onClick=${again} data-testid="nearby-again">${t("nearby.join.again")}</${Button}>`}
      <${Button} variant=${gone ? "primary" : "ghost"} size="lg" block loading=${busy === "cancel"} onClick=${cancel} data-testid="nearby-back">${t("nearby.join.back")}</${Button}>
    </div>
  </div>`;
}

/** Is an attempt of this device to be added in progress, or has it ended in something the person has to see? */
export function nearbyJoinShown(join) {
  return !!join && !!join.state && join.state !== "idle" && join.state !== "canceled";
}

// ------------------------------------------------------------------------ the device that adds: the answer

function RequestDialog({ req, onClose }) {
  const meshName = useStore((s) => (s.self && s.self.meshName) || "");
  const [owner, setOwner] = useState(() => (state.self && state.self.owner) || "");
  const [busy, setBusy] = useState("");
  useNow(1000);
  const left = req.expires - nowSec();
  const answer = async (approve) => {
    setBusy(approve ? "allow" : "deny");
    try {
      await ask(`nearby/requests/${enc(req.id)}`, { approve, owner: owner.trim().replace(/\s+/g, " ") });
      if (approve) toast({ level: "success", title: t("nearby.ask.allowed", { name: req.name }), text: t(req.confirmed ? "nearby.ask.allowedNow" : "nearby.ask.allowedWait") });
      else toast({ level: "info", title: t("nearby.ask.denied") });
    } catch (e) {
      if (e.code === "notfound") toast({ level: "warn", title: t("nearby.ask.gone") });
      else failed(e);
    }
    onClose();
  };
  return html`<${Modal} title=${t("nearby.ask.title")} subtitle=${meshName ? t("nearby.ask.sub", { mesh: meshName }) : undefined} icon="userPlus" size="sm"
      onClose=${onClose} testid="nearby-ask"
      footer=${html`
        <${Button} variant="ghost" loading=${busy === "deny"} onClick=${() => answer(false)} data-testid="nearby-deny">${t("nearby.ask.deny")}</${Button}>
        <${Button} variant="primary" icon="check" loading=${busy === "allow"} onClick=${() => answer(true)} data-testid="nearby-allow">${t("nearby.ask.allow")}</${Button}>`}>
    <div class="stack stack--lg" data-id=${req.id}>
      <div class="nearby-who">
        <${DeviceAvatar} dev=${{ name: req.name, os: req.os }} size=${52} showStatus=${false} />
        <div class="grow">
          <p class="strong ellipsis" data-testid="nearby-ask-name">${req.name}</p>
          <p class="faint small">${req.os ? osName(req.os) : ""}</p>
        </div>
      </div>
      <p class="muted">${t("nearby.ask.lead", { name: req.name })}</p>
      <${Code} code=${req.code} label=${t("nearby.ask.codeLabel")} testid="nearby-ask-code" />
      <p class="faint small">${t("nearby.ask.codeText", { name: req.name })}</p>
      <p class=${cx("nearby-state", req.confirmed && "is-ok")} role="status" data-testid="nearby-ask-state" data-confirmed=${String(!!req.confirmed)}>
        <${Icon} name=${req.confirmed ? "checkCircle" : "clock"} size=${16} />${req.confirmed ? t("nearby.ask.confirmedThere") : t("nearby.ask.notYet")}
      </p>
      <${Field} label=${t("nearby.ask.owner")} hint=${t("nearby.ask.ownerHint")} optional>
        ${(id, d) => html`<input id=${id} class="input" value=${owner} maxlength="64" autocomplete="off" placeholder=${t("add.ownerPh")} data-testid="nearby-ask-owner"
          aria-describedby=${d} onInput=${(e) => setOwner(e.target.value)} />`}
      </${Field}>
      <p class="faint xsmall">${t("nearby.ask.rights")}${left > 0 ? html` <span class="tnum">${t("nearby.ask.left", { time: fmtCountdown(left) })}</span>` : null}</p>
    </div>
  </${Modal}>`;
}

/**
 * Opens the dialog for each request that is new, one at a time. A request whose dialog the person closed without an
 * answer stays as a row (the overview, «Рядом») until it expires.
 */
export function NearbyAskHost() {
  const requests = useStore((s) => s.nearby.requests);
  const open = useStore((s) => s.nearbyAsk);
  const opened = useRef(new Set());
  useEffect(() => {
    if (open && requests.some((r) => r.id === open)) return;
    const next = requests.find((r) => !opened.current.has(r.id));
    if (next) {
      opened.current.add(next.id);
      setState({ nearbyAsk: next.id });
    } else if (open) {
      setState({ nearbyAsk: null });
    }
  }, [requests, open]);
  const req = requests.find((r) => r.id === open);
  if (!req) return null;
  return html`<${RequestDialog} key=${req.id} req=${req} onClose=${() => setState({ nearbyAsk: null })} />`;
}

/** Opens the dialog of a request again (from a row). */
export function showRequest(id) {
  setState({ nearbyAsk: id });
}
