// The shell is a file manager: one panel instead of menus. On the left the places — the overview, the devices of the
// mesh as drives (this one first), the devices nearby, and the folders of the app (transfers, mail, chat, programs);
// on top the path to where one is, with back / forward / up; at the bottom a status bar. On a phone the places are the
// overview screen itself (views/home.js), and the bar on top has the way up and the name of the place.
import { html } from "../vendor/preact-htm.js";
import { Icon, Logo } from "./icons.js";
import { t, tn } from "./i18n.js";
import { back, canBack, canForward, forward, href } from "./router.js";
import { useStore } from "./store.js";
import { cx, sortPeers } from "./util.js";
import { IconButton } from "./components/ui.js";
import { DeviceAvatar } from "./components/avatar.js";
import { openHelp } from "./views/help.js";

/** The number on the "Рядом" place: devices one can connect with, invitations to answer, requests to answer. */
export function nearbyCount(nb) {
  return nb.devices.length + nb.invites.length + nb.requests.length;
}

const selfId = (st, id) => !id || id === "self" || (st.self && id === st.self.id);
const peerName = (st, id) => (selfId(st, id) ? (st.self ? st.self.name : "") : (st.peers.find((p) => p.id === id) || {}).name || String(id || "").slice(0, 8));
const MAIL_FOLDERS = { inbox: "mail.folder.inbox", sent: "mail.folder.sent", trash: "mail.folder.trash" };

/**
 * Where the route is, as the path bar shows it: [{label, href, icon}], from the mesh (the root of the file manager) to
 * the place itself.
 */
export function trailOf(route, st) {
  const mesh = st.self && st.self.meshName;
  const out = [{ label: mesh ? t("fm.root", { name: mesh }) : t("app.name"), href: "#/home", icon: "network", root: true }];
  const [sec, a, b, c, ...rest] = route.parts;
  const add = (label, to, icon) => out.push({ label, href: to, icon });
  switch (sec || "home") {
    case "home":
      if (a) add(peerName(st, a), href(["home", a]), "drive");
      break;
    case "devices":
      add(t("nav.devices"), "#/devices", "devices");
      if (a) add(peerName(st, a), href(["devices", a]));
      break;
    case "nearby":
      add(t("nav.nearby"), "#/nearby", "radar");
      break;
    case "files":
      if (a === "send") add(t("nav.transfers"), "#/files/send", "swap");
      else if (a === "shares") {
        add(peerName(st, "self"), href(["files", "browse", "self"]), "drive");
        add(t("files.tab.shares"), "#/files/shares", "folder");
      } else if (a === "browse" && b) {
        add(peerName(st, b), href(["files", "browse", b]), "drive");
        if (c) add(st.labels["share:" + b + "/" + c] || c, href(["files", "browse", b, c]), "folder");
        rest.forEach((seg, i) => add(seg, href(["files", "browse", b, c, ...rest.slice(0, i + 1)])));
      } else add(t("nav.files"), "#/files/browse", "files");
      break;
    case "mail":
      add(t("nav.mail"), "#/mail", "mail");
      if (a === "compose") add(t("fm.compose"), "#/mail/compose");
      else if (MAIL_FOLDERS[a]) add(t(MAIL_FOLDERS[a]), href(["mail", a]));
      break;
    case "chat":
      add(t("nav.chat"), "#/chat", "chat");
      if (a) add(peerName(st, a), href(["chat", a]));
      break;
    case "services":
      add(t("nav.services"), "#/services", "services");
      break;
    case "settings":
      add(t("nav.settings"), "#/settings", "settings");
      if (a) add(t("set.sec." + a), href(["settings", a]));
      break;
  }
  return out;
}

/** The place a route belongs to, for the places panel (the overview, a device, nearby, a folder of the app). */
export function placeOf(route, st) {
  const [sec, a, b] = route.parts;
  switch (sec || "home") {
    case "home": return a ? "dev:" + a : "home";
    case "files":
      if (a === "send") return "transfers";
      if (a === "shares") return "dev:self";
      if (a === "browse" && b) return selfId(st, b) ? "dev:self" : "dev:" + b;
      return "files";
    case "devices": return a ? "dev:" + a : "devices";
    default: return sec;
  }
}

function Badge({ n, tone }) {
  if (!n) return null;
  return html`<span class=${cx("nav__badge", tone && `nav__badge--${tone}`)}>${n > 99 ? "99+" : n}</span>`;
}

function Place({ id, to, icon, label, n, tone, dot, current, testid, children, cls }) {
  const active = current === id;
  return html`<a href=${to} class=${cx("nav__item place", cls, active && "is-active")} data-testid=${testid} data-place=${id}
      aria-current=${active ? "page" : undefined} aria-label=${n ? `${label}, ${tn("nav.badgeCount", n)}` : undefined} title=${label}>
    <span class="nav__icon">${children || html`<${Icon} name=${icon} size=${19} />`}</span>
    <span class="nav__label">${label}</span>
    <${Badge} n=${n} tone=${tone} />
    ${dot && html`<span class="nav__dot"></span>`}
  </a>`;
}

/** The places panel (computers and tablets). */
export function Places({ place }) {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  const counters = useStore((s) => s.counters);
  const nb = useStore((s) => s.nearby);
  const restart = useStore((s) => !!(s.settings && s.settings.restartRequired));
  const near = nearbyCount(nb);
  return html`<aside class="sidebar places" aria-label=${t("fm.places")}>
    <a class="brand" href="#/home" aria-label=${t("app.name")}>
      <${Logo} size=${34} />
      <span class="brand__text">
        <span class="brand__name">${t("app.name")}</span>
        ${self && self.meshName && html`<span class="brand__mesh">${t("app.meshName", { name: self.meshName })}</span>`}
      </span>
    </a>
    <nav class="nav places__tree" aria-label=${t("nav.label")}>
      <${Place} id="home" to="#/home" icon="network" label=${t("nav.home")} current=${place} testid="nav-home" />
      <div class="places__group" role="group" aria-labelledby="places-devices">
        <a class="places__head" id="places-devices" href="#/devices" data-testid="nav-devices" aria-current=${place === "devices" ? "page" : undefined}>
          <span>${t("nav.devices")}</span><span class="places__count tnum">${peers.length + 1}</span>
        </a>
        ${self && html`<${Place} id="dev:self" to=${href(["files", "browse", "self"])} label=${self.name} current=${place} testid="place-device" cls="place--device">
          <${DeviceAvatar} dev=${self} size=${22} status="self" />
        </${Place}>`}
        ${sortPeers(peers).map((p) => html`<${Place} key=${p.id} id=${"dev:" + p.id} to=${href(["files", "browse", p.id])} label=${p.name} current=${place}
            testid="place-device" cls=${cx("place--device", !p.online && "is-offline")}>
          <${DeviceAvatar} dev=${p} size=${22} />
        </${Place}>`)}
      </div>
      <div class="places__group">
        <${Place} id="nearby" to="#/nearby" icon="radar" label=${t("nav.nearby")} n=${near} tone=${nb.invites.length || nb.requests.length ? "warn" : null}
          current=${place} testid="nav-nearby" />
      </div>
      <div class="places__group" role="group" aria-labelledby="places-folders">
        <span class="places__head" id="places-folders"><span>${t("fm.folders")}</span></span>
        <${Place} id="transfers" to="#/files/send" icon="swap" label=${t("nav.transfers")} n=${counters.offers} tone="warn" current=${place} testid="nav-files" />
        <${Place} id="mail" to="#/mail" icon="mail" label=${t("nav.mail")} n=${counters.mail} current=${place} testid="nav-mail" />
        <${Place} id="chat" to="#/chat" icon="chat" label=${t("nav.chat")} n=${counters.chat} current=${place} testid="nav-chat" />
        <${Place} id="services" to="#/services" icon="services" label=${t("nav.services")} current=${place} testid="nav-services" />
      </div>
    </nav>
    <div class="places__foot">
      <${Place} id="settings" to="#/settings" icon="settings" label=${t("nav.settings")} dot=${restart} current=${place} testid="nav-settings" />
    </div>
  </aside>`;
}

// The phone app has a menu of its own (start with the phone, battery, the log…); it asks for it here.
const appMenu = () => (window.themeshShell && typeof window.themeshShell.menu === "function" ? () => window.themeshShell.menu() : null);

function ConnPill() {
  const peers = useStore((s) => s.peers);
  const total = peers.length + 1;
  const online = peers.filter((p) => p.online).length + 1;
  const shown = sortPeers(peers).slice(0, 4);
  return html`<a class="conn-pill" href="#/devices" title=${t("top.onlineTitle")} data-testid="conn-pill">
    <span class="conn-pill__dots" aria-hidden="true">
      ${shown.map((p) => html`<span key=${p.id} class=${cx("conn-pill__dot", p.online ? (p.path === "relay" ? "is-relay" : "is-on") : "is-off")}></span>`)}
    </span>
    <span class="conn-pill__text tnum">
      <span class="conn-pill__long">${t("top.online", { n: online, total })}</span>
      <span class="conn-pill__short">${online}/${total}</span>
    </span>
  </a>`;
}

/**
 * The bar on top: back, forward and up, the path to the place (each step a link), and on the right how many devices are
 * on the line, help, settings and the phone app's menu. On a phone: up, and the name of the place.
 */
export function PathBar({ trail }) {
  const self = useStore((s) => s.self);
  useStore((s) => s.labels);
  if (!self) return html`<header class="topbar fm-bar"></header>`;
  const openAppMenu = appMenu();
  const here = trail[trail.length - 1];
  const up = trail.length > 1 ? trail[trail.length - 2] : null;
  return html`<header class="topbar fm-bar">
    <div class="fm-bar__nav">
      <${IconButton} icon="chevronLeft" size="sm" variant="ghost" label=${t("fm.back")} disabled=${!canBack()} onClick=${() => back("#/home")} class="fm-bar__btn fm-bar__back" data-testid="fm-back" />
      <${IconButton} icon="chevronRight" size="sm" variant="ghost" label=${t("fm.forward")} disabled=${!canForward()} onClick=${forward} class="fm-bar__btn fm-bar__fwd" data-testid="fm-forward" />
      <${IconButton} icon="arrowUp" size="sm" variant="ghost" label=${up ? t("fm.upTo", { name: up.label }) : t("fm.up")} disabled=${!up} href=${up ? up.href : undefined} class="fm-bar__btn fm-bar__up" data-testid="fm-up" />
    </div>
    <a class="topbar__brand" href="#/home" aria-label=${t("app.name")}><${Logo} size=${30} /></a>
    <nav class="fm-path" aria-label=${t("fm.path")} data-testid="fm-path">
      <ol>
        ${trail.map((it, i) => {
          const last = i === trail.length - 1;
          return html`<li key=${i} class=${cx("fm-path__step", it.root && "fm-path__step--root", last && "is-here")}>
            ${i > 0 && html`<${Icon} name="chevronRight" size=${14} class="fm-path__sep" />`}
            ${last
              ? html`<span class="fm-path__here" aria-current="page">${it.icon && html`<${Icon} name=${it.icon} size=${16} />`}<span class="ellipsis">${it.label}</span></span>`
              : html`<a href=${it.href}>${it.icon && html`<${Icon} name=${it.icon} size=${16} />`}<span class="ellipsis">${it.label}</span></a>`}
          </li>`;
        })}
      </ol>
    </nav>
    <div class="fm-title" aria-hidden="true">
      ${up && html`<span class="fm-title__up ellipsis">${up.label}</span>`}
      <span class="fm-title__here ellipsis">${here.label}</span>
    </div>
    <div class="topbar__status">
      <${ConnPill} />
      <${IconButton} icon="help" label=${t("top.help")} onClick=${openHelp} class="topbar__help" data-testid="help-button" />
      <${IconButton} icon="settings" label=${t("nav.settings")} href="#/settings" class="topbar__help fm-bar__settings" data-testid="fm-settings" />
      ${openAppMenu && html`<${IconButton} icon="moreV" label=${t("top.appMenu")} onClick=${openAppMenu} class="topbar__help" data-testid="app-menu-button" />`}
    </div>
  </header>`;
}

/** The status bar under the content (computers): the network in a few words. */
export function StatusBar() {
  const peers = useStore((s) => s.peers);
  const nb = useStore((s) => s.nearby);
  const counters = useStore((s) => s.counters);
  const total = peers.length + 1;
  const online = peers.filter((p) => p.online).length + 1;
  const near = nb.devices.length;
  return html`<footer class="fm-status" data-testid="fm-status">
    <span class="fm-status__item"><span class=${cx("fm-status__dot", online > 1 && "is-on")} aria-hidden="true"></span>${tn("fm.status.online", total, { n: online, total })}</span>
    ${near > 0 && html`<a class="fm-status__item" href="#/nearby"><${Icon} name="radar" size=${14} />${tn("fm.status.nearby", near)}</a>`}
    ${counters.offers > 0 && html`<a class="fm-status__item is-warn" href="#/files/send"><${Icon} name="inbox" size=${14} />${tn("fm.status.offers", counters.offers)}</a>`}
  </footer>`;
}
