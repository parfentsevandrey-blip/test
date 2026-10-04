// Devices nearby (docs/UI-API.md → "Devices nearby").
//
// A device that is not in a mesh yet lists the devices around that have The Mesh and can add it, and asks one of them in
// one tap. Both screens then show the same six digits, the person at the new device says that they match, and the person
// at the device that adds says yes. A device that can add others gets such requests in a dialog that opens by itself
// (and as a row in Home → "Требует внимания" while it is not answered).
// The node does the work and keeps the state; this file is its face.
import { html, useEffect, useRef, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { t, tn } from "../i18n.js";
import { post } from "../api.js";
import { nearbyOf, nowSec, setState, state, useStore } from "../store.js";
import { useNow } from "../hooks.js";
import { fmtCountdown } from "../format.js";
import { DeviceAvatar } from "../components/avatar.js";
import { Button, Callout, Field, Spinner } from "../components/ui.js";
import { Modal } from "../components/modal.js";
import { DnsPreview } from "../components/misc.js";
import { toast, toastError } from "../components/toast.js";
import { cx, normalizeDeviceName, osName } from "../util.js";
import { offeredName, validateName } from "../naming.js";

const RUNNING = new Set(["connecting", "waiting", "confirmed"]);

/** "482913" → "482 913": easier to compare across two screens. */
export function spaced(code) {
  const c = String(code || "").replace(/\s+/g, "");
  return c.length === 6 ? `${c.slice(0, 3)} ${c.slice(3)}` : c;
}

/**
 * What to say when discovery of devices on the home network does not work (self.lan, from the node): the system refuses
 * to send there (on a Mac: the "Local Network" permission is off) or it fails some other way. Nothing for the ordinary
 * "not on Wi-Fi right now".
 */
export function lanNotice(self) {
  const lan = self && self.lan;
  if (!lan || (lan.problem !== "blocked" && lan.problem !== "failed")) return null;
  if (lan.problem === "failed") return { text: t("home.att.lanFailed"), sub: lan.detail || "" };
  const mac = self.os === "darwin";
  return { text: t(mac ? "home.att.lanBlockedMac" : "home.att.lanBlocked"), sub: t(mac ? "home.att.lanBlockedMacHow" : "home.att.lanBlockedHow") };
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
  const v = await post(path, body);
  takeAnswer(v);
  return v;
}

/** The six digits, big, to be compared with another screen. */
function Code({ code, label, testid }) {
  return html`<div class="nearby-code" data-testid=${testid}>
    <span class="nearby-code__label">${label}</span>
    <span class="nearby-code__digits tnum" aria-label=${String(code).split("").join(" ")} data-code=${code}>${spaced(code)}</span>
  </div>`;
}

// ------------------------------------------------------------------------ the new device: the list

// The name chosen for this device while it asks, kept for "try again".
let chosenName = "";

async function connect(device, name, self) {
  const dn = normalizeDeviceName(name || offeredName(self));
  chosenName = dn;
  try {
    await ask("nearby/connect", { id: device.id, deviceName: dn });
  } catch (e) {
    if (e.code === "notfound") toast({ level: "warn", title: t("nearby.gone") });
    else toastError(e);
  }
}

function NearbyName({ self, name, setName }) {
  const placeholder = offeredName(self);
  const [editing, setEditing] = useState(false);
  const shown = normalizeDeviceName(name || placeholder);
  const problem = name ? validateName(shown) : "";
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
        onKeyDown=${(e) => { if (e.key === "Enter" && !problem) setEditing(false); }} />`}
    </${Field}>
    <${Button} size="sm" variant="secondary" disabled=${!!problem} onClick=${() => setEditing(false)}>${t("nearby.nameDone")}</${Button}>
  </div>`;
}

/**
 * The list on the start screen: the devices around that can add this one, or — while there are none — a quiet line that
 * says that the app is looking (and what to do, or what is wrong, when it cannot).
 */
export function NearbySection({ self }) {
  const nearby = useStore((s) => s.nearby);
  const off = useStore((s) => !!s.settings && s.settings.lan === false);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState("");
  const lan = self && self.lan;
  if (off) return null; // the person turned looking around on the network off
  const devices = nearby.devices;
  const trouble = lanNotice(self);
  const found = devices.length > 0;
  const go = async (d) => {
    const dn = normalizeDeviceName(name || offeredName(self));
    if (validateName(dn)) return;
    setBusy(d.id);
    await connect(d, name, self);
    setBusy("");
  };
  return html`<section class=${cx("nearby", found && "nearby--found")} data-testid="nearby" data-found=${String(devices.length)} aria-labelledby="nearby-title">
    <header class="nearby__head">
      <span class="nearby__radar" aria-hidden="true"><${Icon} name="radar" size=${24} /></span>
      <div class="grow">
        <h2 class="nearby__title" id="nearby-title">${found ? tn("nearby.found", devices.length) : t("nearby.title")}</h2>
        <p class="nearby__sub" role="status" aria-live="polite">${found ? t("nearby.lead") : trouble ? trouble.text : lan && lan.problem === "no-network" ? t("lan.noNetwork") : t("nearby.searching")}</p>
      </div>
    </header>
    ${!found && html`<p class="nearby__hint" data-testid="nearby-searching">${trouble ? trouble.sub : lan && lan.problem === "no-network" ? "" : t("nearby.searchingHint")}</p>`}
    ${found && html`<ul class="nearby__list">
      ${devices.map((d) => html`<li key=${d.id} class="nearby__item" data-testid="nearby-device" data-id=${d.id} data-name=${d.name}>
        <${DeviceAvatar} dev=${{ name: d.name, os: d.os }} size=${44} showStatus=${false} />
        <div class="grow nearby__who">
          <p class="nearby__name ellipsis">${d.name}</p>
          <p class="nearby__meta ellipsis">${[d.meshName && t("nearby.mesh", { mesh: d.meshName }), d.os && osName(d.os)].filter(Boolean).join(" · ")}</p>
        </div>
        <${Button} variant="primary" icon="link" loading=${busy === d.id} onClick=${() => go(d)} aria-label=${t("nearby.connectTo", { name: d.name })} data-testid="nearby-connect">${t("nearby.connect")}</${Button}>
      </li>`)}
    </ul>`}
    ${found && html`<${NearbyName} self=${self} name=${name} setName=${setName} />`}
  </section>`;
}

// ------------------------------------------------------------------------ the new device: the request

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
export function NearbyJoin({ join, self }) {
  const peer = join.peer || { name: "?" };
  const [busy, setBusy] = useState("");
  const st = join.state;
  const gone = useStore((s) => !s.nearby.devices.some((d) => peer.id && d.id === peer.id));

  const run = (what, path) => async () => {
    setBusy(what);
    try {
      await ask(path);
    } catch (e) {
      if (e.code !== "notfound") toastError(e);
    }
    setBusy("");
  };
  const cancel = run("cancel", "nearby/cancel");
  const match = run("match", "nearby/confirm");
  const again = async () => {
    setBusy("again");
    await connect(peer, chosenName, self);
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
    const waiting = st === "waiting";
    return html`<div class="onb-form nearby-join" data-testid="nearby-join" data-state=${st}>
      <div class="onb-form__head">
        <${DeviceAvatar} dev=${peer} size=${48} showStatus=${false} />
        <div>
          <h2 class="onb-form__title">${waiting ? t("nearby.join.waiting") : t("nearby.join.confirmed")}</h2>
          <p class="muted" role="status" aria-live="polite">${waiting ? t("nearby.join.waitingText", { name: peer.name }) : t("nearby.join.confirmedText", { name: peer.name })}</p>
        </div>
      </div>
      <${Code} code=${join.code} label=${t("nearby.join.codeLabel")} testid="nearby-code" />
      ${waiting
        ? html`<div class="nearby-join__actions">
            <${Button} variant="primary" size="lg" block icon="check" loading=${busy === "match"} onClick=${match} data-testid="nearby-match">${t("nearby.join.match")}</${Button}>
            <${Button} variant="ghost" size="lg" block loading=${busy === "cancel"} onClick=${cancel} data-testid="nearby-cancel">${t("nearby.join.cancel")}</${Button}>
          </div>`
        : html`<div class="invite__wait" role="status" data-testid="nearby-waiting-approval">
            <span class="invite__pulse" aria-hidden="true"></span><span>${t("nearby.join.confirmed")}…</span>
          </div>
          <${Button} variant="ghost" block loading=${busy === "cancel"} onClick=${cancel} data-testid="nearby-cancel">${t("nearby.join.cancel")}</${Button}>`}
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
      await ask(`nearby/requests/${encodeURIComponent(req.id)}`, { approve, owner: owner.trim().replace(/\s+/g, " ") });
      if (approve) toast({ level: "success", title: t("nearby.ask.allowed", { name: req.name }), text: t(req.confirmed ? "nearby.ask.allowedNow" : "nearby.ask.allowedWait") });
      else toast({ level: "info", title: t("nearby.ask.denied") });
    } catch (e) {
      if (e.code === "notfound") toast({ level: "warn", title: t("nearby.ask.gone") });
      else toastError(e);
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
      <${Code} code=${req.code} label=${t("nearby.ask.codeLabel")} testid="nearby-ask-code" />
      <p class="muted small">${t("nearby.ask.codeText", { name: req.name })}</p>
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
 * answer stays as a row in Home (→ `showRequest`) until it expires.
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

/** Opens the dialog of a request again (from Home). */
export function showRequest(id) {
  setState({ nearbyAsk: id });
}
