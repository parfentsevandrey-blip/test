// Home («Главная»): the landing screen for people who don't care how the mesh
// works. One sentence about the network, four big actions, the devices with
// plain buttons, what needs attention and — while the network is new — three
// first steps. Everything comes from the store (/api/state, transfers,
// counters, invites): no requests of its own.
import { html, useEffect, useState } from "../../vendor/preact-htm.js";
import { Icon } from "../icons.js";
import { locale, t, tn, tx } from "../i18n.js";
import { go, href } from "../router.js";
import { nowSec, useStore } from "../store.js";
import { setStartFlag, startFlag } from "../prefs.js";
import { cx, sortPeers } from "../util.js";
import { fmtBytes, fmtCountdown } from "../format.js";
import { useNow } from "../hooks.js";
import { DeviceAvatar } from "../components/avatar.js";
import { ConnLine, DeviceActions, connState, platformLine } from "../components/device-actions.js";
import { Button } from "../components/ui.js";
import { Modal } from "../components/modal.js";
import { toast } from "../components/toast.js";
import { AddDeviceModal } from "./add-device.js";
import { DeviceDrawer } from "./device-drawer.js";
import { openHelp } from "./help.js";
import { isIncomingOffer, transferAction } from "./transfers.js";

const MAX_NAMES = 3;
// A device that has been away longer than this is "asleep" (a tablet in a drawer), not a problem.
const RECENT = 24 * 3600;

/** "a, b, c и ещё 2" (or, in running text, "a и b"). */
function names(list, prose = false) {
  const shown = list.slice(0, MAX_NAMES).map((p) => p.name);
  const more = list.length - shown.length;
  if (more > 0) return shown.join(", ") + " " + t("home.status.andMore", { n: more });
  if (prose && typeof Intl.ListFormat === "function") return new Intl.ListFormat(locale(), { type: "conjunction" }).format(shown);
  return shown.join(", ");
}

// ---------------------------------------------------------------- status sentence
/**
 * ok | partial | offline | alone — counted with this device, as the top bar does.
 * Devices that dropped off in the last day make it "partial" and are named;
 * ones away for longer only get a calm mention under "Всё в порядке".
 */
export function networkStatus(peers) {
  const total = peers.length + 1;
  const n = peers.filter((p) => p.online).length + 1;
  if (!peers.length) return { state: "alone", icon: "userPlus", title: t("home.status.alone"), line: t("home.status.aloneLine") };
  const off = sortPeers(peers.filter((p) => !p.online));
  if (n === 1) {
    return {
      state: "offline", icon: "wifiOff",
      title: off.length === 1 ? t("home.status.oneOff", { name: off[0].name }) : t("home.status.none"),
      line: t("home.status.noneLine"),
    };
  }
  const now = nowSec();
  const recent = off.filter((p) => p.lastSeen && now - p.lastSeen < RECENT);
  if (recent.length) {
    return { state: "partial", icon: "alert", title: t("home.status.some", { names: names(recent) }), line: tn("home.status.someLine", total, { n, total }) };
  }
  return {
    state: "ok", icon: "checkCircle", title: tn("home.status.ok", total, { n, total }),
    line: off.length ? t("home.status.okAsleep", { names: names(off, true) }) : t("home.status.okLine"),
  };
}

function Status({ peers }) {
  const s = networkStatus(peers);
  return html`<div class=${cx("home-status", `home-status--${s.state}`)} data-testid="home-status" data-state=${s.state}>
    <span class="home-status__icon"><${Icon} name=${s.icon} size=${26} /></span>
    <div class="grow">
      <p class="home-status__title" aria-live="polite">${s.title}</p>
      <p class="home-status__line">${s.line}</p>
    </div>
    <button type="button" class="home-status__help" onClick=${openHelp} data-testid="home-help">
      <${Icon} name="help" size=${18} /><span>${t("home.how")}</span>
    </button>
  </div>`;
}

// ---------------------------------------------------------------- what needs attention
function OfferRow({ tr }) {
  const [busy, setBusy] = useState("");
  const run = async (a) => {
    setBusy(a);
    const ok = await transferAction(tr, a);
    setBusy("");
    if (ok && a === "accept") toast({ level: "success", title: t("home.att.accepted", { name: tr.name }), text: t("home.att.acceptedText"), link: "#/files/send", actionLabel: t("home.att.acceptedGo") });
  };
  return html`<li data-testid="home-offer" data-id=${tr.id}>
    <div class="home-att__row home-att__row--offer">
      <span class="home-att__icon is-warn"><${Icon} name="inbox" size=${18} /></span>
      <div class="grow">
        <p class="home-att__text">${tx("offer.one", { who: html`<strong>${tr.peerName}</strong>`, name: html`<strong class="break">${tr.name}</strong>` })}</p>
        <p class="home-att__sub tnum">${fmtBytes(tr.size)}</p>
      </div>
      <div class="home-att__actions">
        <${Button} size="sm" variant="ghost" loading=${busy === "decline"} onClick=${() => run("decline")} data-testid="offer-decline">${t("tr.decline")}</${Button}>
        <${Button} size="sm" variant="primary" icon="check" loading=${busy === "accept"} onClick=${() => run("accept")} data-testid="offer-accept">${t("tr.accept")}</${Button}>
      </div>
    </div>
  </li>`;
}

/** A whole-row link: «2 новых письма — Открыть почту ›». */
function LinkRow({ icon, tone, text, sub, to, label, testid }) {
  return html`<li data-testid=${testid}>
    <a class="home-att__row home-att__row--link" href=${to}>
      <span class=${cx("home-att__icon", tone && `is-${tone}`)}><${Icon} name=${icon} size=${18} /></span>
      <span class="grow"><span class="home-att__text">${text}</span>${sub && html`<span class="home-att__sub">${sub}</span>`}</span>
      <span class="home-att__go"><span class="home-att__go-label">${label}</span><${Icon} name="chevronRight" size=${16} /></span>
    </a>
  </li>`;
}

function InviteRows({ invites }) {
  useNow(1000);
  return invites.map((inv) => {
    const left = inv.expires - nowSec();
    const sub = [inv.owner && t("inv.for", { owner: inv.owner }), left > 0 ? t("inv.left", { time: fmtCountdown(left) }) : t("inv.expired")].filter(Boolean).join(" · ");
    return html`<${LinkRow} key=${inv.id} icon="ticket" text=${t("home.att.invite")} sub=${sub} to="#/devices" label=${t("home.att.inviteGo")} testid="home-att-invite" />`;
  });
}

function Attention({ offers, counters, invites, restart }) {
  if (!offers.length && !counters.mail && !counters.chat && !invites.length && !restart) return null;
  return html`<section class="home-card home-att" data-testid="home-attention" aria-labelledby="home-att-title">
    <h2 class="home-sec__title" id="home-att-title">${t("home.attention")}</h2>
    <ul class="home-att__list">
      ${offers.slice(0, MAX_NAMES).map((tr) => html`<${OfferRow} key=${tr.id} tr=${tr} />`)}
      ${offers.length > MAX_NAMES && html`<${LinkRow} icon="inbox" tone="warn" text=${tn("home.att.moreOffers", offers.length - MAX_NAMES)} to="#/files/send" label=${t("offer.review")} testid="home-att-offers" />`}
      ${counters.mail > 0 && html`<${LinkRow} icon="mail" tone="accent" text=${tn("home.att.mail", counters.mail)} to="#/mail/inbox" label=${t("home.att.mailGo")} testid="home-att-mail" />`}
      ${counters.chat > 0 && html`<${LinkRow} icon="chat" tone="accent" text=${tn("home.att.chat", counters.chat)} to="#/chat" label=${t("home.att.chatGo")} testid="home-att-chat" />`}
      ${invites.length > 0 && html`<${InviteRows} invites=${invites} />`}
      ${restart && html`<${LinkRow} icon="refresh" tone="warn" text=${t("home.att.restart")} to="#/settings" label=${t("home.att.restartGo")} testid="home-att-restart" />`}
    </ul>
  </section>`;
}

// ---------------------------------------------------------------- getting started
function StartChecklist({ peers, transfers, onAdd, onDismiss }) {
  const hasPeer = peers.length > 0;
  const steps = [
    { id: "add", done: hasPeer, title: "home.start.add", text: "home.start.addText", onClick: onAdd },
    { id: "browse", done: startFlag("browsed"), title: "home.start.browse", text: "home.start.browseText", to: "#/files/browse" },
    { id: "send", done: startFlag("sent") || transfers.some((x) => x.dir === "out"), title: "home.start.send", text: "home.start.sendText", to: "#/files/send" },
  ];
  const next = steps.find((s) => !s.done);
  return html`<section class="home-card home-start" data-testid="home-start-checklist" aria-labelledby="home-start-title">
    <header class="home-start__head">
      <div class="grow">
        <h2 class="home-sec__title" id="home-start-title">${t("home.start.title")}</h2>
        <p class="home-start__sub">${next ? t("home.start.sub") : t("home.start.allDone")}</p>
      </div>
      <${Button} size="sm" variant="ghost" onClick=${onDismiss} aria-label=${t("home.start.hideLabel")} data-testid="home-start-dismiss">${t("home.start.hide")}</${Button}>
    </header>
    <ol class="home-start__steps">
      ${steps.map((s, i) => {
        // Looking around and sending need a second device first.
        const can = !s.done && (s.id === "add" || hasPeer);
        return html`<li key=${s.id} class=${cx("home-step", s.done && "is-done", s === next && "is-next")} data-step=${s.id} data-done=${String(s.done)}>
          <span class="home-step__n" aria-hidden="true">${s.done ? html`<${Icon} name="check" size=${16} strokeWidth=${2.4} />` : i + 1}</span>
          <div class="grow">
            <p class="home-step__title">${t(s.title)}${s.done && html`<span class="sr-only"> — ${t("home.start.done")}</span>`}</p>
            <p class="home-step__text">${t(s.text)}</p>
          </div>
          ${s.done
            ? html`<span class="home-step__done" aria-hidden="true">${t("home.start.done")}</span>`
            : can && html`<${Button} size="sm" variant=${s === next ? "primary" : "secondary"} href=${s.to} onClick=${s.onClick} data-testid=${"home-start-" + s.id}>${t("home.start.go")}</${Button}>`}
        </li>`;
      })}
    </ol>
  </section>`;
}

// ---------------------------------------------------------------- big actions
const ACTIONS = [
  { id: "send", icon: "send", title: "home.act.send", text: "home.act.sendText" },
  { id: "chat", icon: "chat", title: "home.act.chat", text: "home.act.chatText" },
  { id: "files", icon: "folderOpen", title: "home.act.files", text: "home.act.filesText" },
  { id: "add", icon: "userPlus", title: "home.act.add", text: "home.act.addText" },
];

function ActionCard({ a, to, onClick }) {
  const inner = html`<span class=${cx("home-act__icon", `home-act__icon--${a.id}`)}><${Icon} name=${a.icon} size=${24} /></span>
    <span class="home-act__text"><span class="home-act__title">${t(a.title)}</span><span class="home-act__sub">${t(a.text)}</span></span>`;
  return to
    ? html`<a class="home-act" href=${to} data-testid=${"home-action-" + a.id}>${inner}</a>`
    : html`<button type="button" class="home-act" onClick=${onClick} data-testid=${"home-action-" + a.id}>${inner}</button>`;
}

const pickTarget = (kind, p) => (kind === "send" ? href(["files", "send"], { to: p.id }) : href(["chat", p.id]));

/** «Кому отправить файл?» / «Кому написать?» — when there is more than one device to choose from. */
function DevicePick({ kind, peers, onClose }) {
  return html`<${Modal} size="sm" title=${t(kind === "send" ? "home.pick.sendTitle" : "home.pick.chatTitle")} icon=${kind === "send" ? "send" : "chat"}
      onClose=${onClose} testid="device-pick">
    <ul class="pick">
      ${sortPeers(peers).map((p) => html`<li key=${p.id}>
        <a class="pick__item" href=${pickTarget(kind, p)} onClick=${onClose} data-testid="device-pick-item" data-peer=${p.id}>
          <${DeviceAvatar} dev=${p} size=${40} />
          <span class="grow"><span class="pick__name ellipsis">${p.name}</span>
            <span class="pick__sub">${p.online ? platformLine(p) : t("home.pick.offline")}</span></span>
          <${Icon} name="chevronRight" size=${18} class="pick__chev" />
        </a>
      </li>`)}
    </ul>
    ${kind === "send" && html`<p class="pick__later"><a href="#/files/send" onClick=${onClose}>${t("home.pick.later")}</a></p>`}
  </${Modal}>`;
}

// ---------------------------------------------------------------- devices
function HomeDevice({ p }) {
  const st = connState(p);
  return html`<article class=${cx("hdev", `hdev--${st}`)} data-testid="home-device" data-peer=${p.id} data-name=${p.deviceName || p.name}
      data-online=${String(!!p.online)} data-state=${st}>
    <div class="hdev__head">
      <${DeviceAvatar} dev=${p} size=${48} showStatus=${false} />
      <div class="grow">
        <h3 class="hdev__name ellipsis"><a class="stretched" href=${href(["home", p.id])}>${p.name}</a></h3>
        <p class="hdev__sub ellipsis">${platformLine(p)}</p>
      </div>
    </div>
    <${ConnLine} p=${p} />
    <${DeviceActions} p=${p} />
  </article>`;
}

// ---------------------------------------------------------------- the page
export function HomeView({ route }) {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  const transfers = useStore((s) => s.transfers);
  const counters = useStore((s) => s.counters);
  const invites = useStore((s) => s.invites);
  const restart = useStore((s) => !!(s.settings && s.settings.restartRequired));
  const [adding, setAdding] = useState(false);
  const [picking, setPicking] = useState(null); // "send" | "chat"
  const [startHidden, setStartHidden] = useState(() => startFlag("startDismissed"));
  // `#/home?add=1` (empty states elsewhere link here): open «Добавить устройство» right away.
  const wantsAdd = route.query.get("add") === "1";
  useEffect(() => {
    if (!wantsAdd) return;
    setAdding(true);
    go("#/home", { replace: true });
  }, [wantsAdd]);
  if (!self) return null;
  const selId = route.parts[1] || null;
  const offers = transfers.filter(isIncomingOffer);
  const sorted = sortPeers(peers);
  const showStart = !startHidden && (peers.length <= 1 || !transfers.length);

  const needDevice = () => {
    toast({ level: "info", title: t("home.act.needDevice") });
    setAdding(true);
  };
  // One device: straight there. More: ask which one. None: add one first.
  const direct = peers.length === 1 ? peers[0] : null;
  const actionProps = (a) => {
    if (a.id === "add") return { onClick: () => setAdding(true) };
    if (!peers.length) return { onClick: needDevice };
    if (a.id === "files") return { to: "#/files/browse" };
    if (direct) return { to: pickTarget(a.id, direct) };
    return { onClick: () => setPicking(a.id) };
  };
  const dismissStart = () => {
    setStartFlag("startDismissed");
    setStartHidden(true);
  };
  const attention = html`<${Attention} offers=${offers} counters=${counters} invites=${invites} restart=${restart} />`;
  const hasAttention = offers.length || counters.mail || counters.chat || invites.length || restart;

  return html`<div class="page home">
    <h1 class="sr-only">${t("home.h1")}</h1>
    <${Status} peers=${peers} />

    ${(hasAttention || showStart) && html`<div class=${cx("home__row", hasAttention && showStart && "home__row--two")}>
      ${attention}
      ${showStart && html`<${StartChecklist} peers=${peers} transfers=${transfers} onAdd=${() => setAdding(true)} onDismiss=${dismissStart} />`}
    </div>`}

    <section class="home-actions" aria-labelledby="home-actions-title">
      <h2 class="sr-only" id="home-actions-title">${t("home.actions")}</h2>
      <div class="home-actions__grid">
        ${ACTIONS.map((a) => html`<${ActionCard} key=${a.id} a=${a} ...${actionProps(a)} />`)}
      </div>
    </section>

    <section class="home-devs" aria-labelledby="home-devs-title">
      <div class="home-sec__head">
        <div class="grow">
          <h2 class="home-sec__title" id="home-devs-title">${t("home.devices")}</h2>
          <p class="home-sec__sub">${t("home.here", { name: self.name })}</p>
        </div>
        <a class="home-sec__link" href="#/devices" data-testid="home-devices-all">${t("home.devicesAll")}<${Icon} name="chevronRight" size=${16} /></a>
      </div>
      ${sorted.length
        ? html`<div class="home-devs__grid">${sorted.map((p) => html`<${HomeDevice} key=${p.id} p=${p} />`)}</div>`
        : html`<div class="home-card home-devs__empty">
            <span class="home-devs__empty-icon"><${Icon} name="devices" size=${26} /></span>
            <div class="grow"><p class="strong">${t("home.noDevices")}</p><p class="muted small">${t("home.noDevicesText")}</p></div>
            <${Button} variant="primary" icon="userPlus" onClick=${() => setAdding(true)}>${t("dev.add")}</${Button}>
          </div>`}
    </section>

    ${selId && html`<${DeviceDrawer} id=${selId} onClose=${() => go("#/home")} />`}
    ${picking && html`<${DevicePick} kind=${picking} peers=${peers} onClose=${() => setPicking(null)} />`}
    ${adding && html`<${AddDeviceModal} onClose=${() => setAdding(false)} />`}
  </div>`;
}
