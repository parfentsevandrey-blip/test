// Entry point: app shell (sidebar / top bar / mobile tab bar), global
// banners and states, routing to views.
import { html, render, useEffect, useState } from "../vendor/preact-htm.js";
import { Icon, Logo } from "./icons.js";
import { t, tn, tx } from "./i18n.js";
import { initPrefs } from "./prefs.js";
import { go, useRoute } from "./router.js";
import { refreshState, reconnectNow, startLive } from "./sse.js";
import { state, useStore } from "./store.js";
import { cx, sortPeers } from "./util.js";
import { useNow } from "./hooks.js";
import { DialogHost } from "./components/modal.js";
import { toast, ToastHost } from "./components/toast.js";
import { DeviceAvatar } from "./components/avatar.js";
import { Button, CopyButton, IconButton, Spinner } from "./components/ui.js";
import { OnboardingView } from "./views/onboarding.js";
import { HomeView } from "./views/home.js";
import { HelpHost, openHelp } from "./views/help.js";
import { DevicesView } from "./views/devices.js";
import { FilesView } from "./views/files.js";
import { MailView } from "./views/mail.js";
import { ChatView } from "./views/chat.js";
import { ServicesView } from "./views/services.js";
import { SettingsView } from "./views/settings.js";
import { MoreView } from "./views/more.js";
import { OffersBanner } from "./views/transfers.js";

const NAV = [
  { id: "home", icon: "home", label: "nav.home" },
  { id: "devices", icon: "devices", label: "nav.devices" },
  { id: "files", icon: "files", label: "nav.files", counter: "offers" },
  { id: "mail", icon: "mail", label: "nav.mail", counter: "mail" },
  { id: "chat", icon: "chat", label: "nav.chat", counter: "chat" },
  { id: "services", icon: "services", label: "nav.services" },
  { id: "settings", icon: "settings", label: "nav.settings" },
];

// Phones have room for five tabs. The device list lives on Home (and the full
// Devices page is one tap away from there and from «Ещё»).
const TABS = ["home", "files", "mail", "chat", "more"];
const TAB_OF = { devices: "home" };

const VIEWS = {
  home: HomeView,
  devices: DevicesView,
  files: FilesView,
  mail: MailView,
  chat: ChatView,
  services: ServicesView,
  settings: SettingsView,
  more: MoreView,
};

// Screens that manage their own scrolling panes (fixed-height layout).
const FIXED = new Set(["mail", "chat"]);

function NavBadge({ n, tone }) {
  if (!n) return null;
  return html`<span class=${cx("nav__badge", tone && `nav__badge--${tone}`)}>${n > 99 ? "99+" : n}</span>`;
}

function Sidebar({ section }) {
  const self = useStore((s) => s.self);
  const counters = useStore((s) => s.counters);
  const restart = useStore((s) => !!(s.settings && s.settings.restartRequired));
  return html`<aside class="sidebar">
    <a class="brand" href="#/home" aria-label=${t("app.name")}>
      <${Logo} size=${34} />
      <span class="brand__text">
        <span class="brand__name">${t("app.name")}</span>
        ${self && self.meshName && html`<span class="brand__mesh">${t("app.meshName", { name: self.meshName })}</span>`}
      </span>
    </a>
    <nav class="nav" aria-label=${t("nav.label")}>
      ${NAV.map((n) => {
        const count = n.counter ? counters[n.counter] || 0 : 0;
        const active = section === n.id;
        const aria = count ? `${t(n.label)}, ${tn("nav.badgeCount", count)}` : undefined;
        return html`<a key=${n.id} href=${"#/" + n.id} class=${cx("nav__item", active && "is-active")} data-testid=${"nav-" + n.id}
            aria-current=${active ? "page" : undefined} aria-label=${aria} title=${t(n.label)}>
          <span class="nav__icon"><${Icon} name=${n.icon} size=${20} /></span>
          <span class="nav__label">${t(n.label)}</span>
          <${NavBadge} n=${count} tone=${n.counter === "offers" ? "warn" : null} />
          ${n.id === "settings" && restart && html`<span class="nav__dot" title=${t("settings.restartRequired")}></span>`}
        </a>`;
      })}
    </nav>
    ${self && html`<a class="sidebar__self" href="#/settings" title=${t("dev.thisDevice")}>
      <${DeviceAvatar} dev=${self} size=${36} status="self" />
      <span class="sidebar__self-text">
        <span class="ellipsis strong">${self.name}</span>
        <span class="ellipsis faint xsmall">${t("dev.thisDevice")}</span>
      </span>
    </a>`}
  </aside>`;
}

function TabBar({ section }) {
  const counters = useStore((s) => s.counters);
  const restart = useStore((s) => !!(s.settings && s.settings.restartRequired));
  const items = [
    { id: "home", icon: "home", label: t("nav.home") },
    { id: "files", icon: "files", label: t("nav.files"), n: counters.offers, tone: "warn" },
    { id: "mail", icon: "mail", label: t("nav.mail"), n: counters.mail },
    { id: "chat", icon: "chat", label: t("nav.chat"), n: counters.chat },
    { id: "more", icon: "more", label: t("nav.more"), dot: restart },
  ];
  const activeTab = TABS.includes(section) ? section : TAB_OF[section] || "more";
  return html`<nav class="tabbar" aria-label=${t("nav.label")}>
    ${items.map((it) => html`<a key=${it.id} href=${"#/" + it.id} class=${cx("tabbar__item", activeTab === it.id && "is-active")} data-testid=${"tab-" + it.id}
        aria-current=${activeTab === it.id ? "page" : undefined}
        aria-label=${it.n ? `${it.label}, ${tn("nav.badgeCount", it.n)}` : undefined}>
      <span class="tabbar__icon"><${Icon} name=${it.icon} size=${22} />
        ${it.n ? html`<span class=${cx("tabbar__badge", it.tone && `tabbar__badge--${it.tone}`)}>${it.n > 99 ? "99+" : it.n}</span>` : null}
        ${it.dot && html`<span class="tabbar__dot"></span>`}
      </span>
      <span class="tabbar__label">${it.label}</span>
    </a>`)}
  </nav>`;
}

// How the connection works (NAT type, relays) is in Settings → Сеть; the top
// bar only says how many devices are online and offers «Как это работает?».
function TopBar() {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  if (!self) return html`<header class="topbar"></header>`;
  const total = peers.length + 1;
  const online = peers.filter((p) => p.online).length + 1;
  const onlinePeers = sortPeers(peers).slice(0, 4);
  return html`<header class="topbar">
    <a class="topbar__brand" href="#/home" aria-label=${t("app.name")}>
      <${Logo} size=${30} />
    </a>
    <div class="topbar__self">
      <span class="topbar__name ellipsis">${self.name}</span>
      ${self.meshName && html`<span class="topbar__mesh ellipsis">${t("app.meshName", { name: self.meshName })}</span>`}
    </div>
    <div class="topbar__status">
      <a class="conn-pill" href="#/devices" title=${t("top.onlineTitle")} data-testid="conn-pill">
        <span class="conn-pill__dots" aria-hidden="true">
          ${onlinePeers.map((p) => html`<span key=${p.id} class=${cx("conn-pill__dot", p.online ? (p.path === "relay" ? "is-relay" : "is-on") : "is-off")}></span>`)}
        </span>
        <span class="conn-pill__text tnum">
          <span class="conn-pill__long">${t("top.online", { n: online, total })}</span>
          <span class="conn-pill__short">${online}/${total}</span>
        </span>
      </a>
      <${IconButton} icon="help" label=${t("top.help")} onClick=${openHelp} class="topbar__help" data-testid="help-button" />
    </div>
  </header>`;
}

function OfflineBanner() {
  const conn = useStore((s) => s.conn);
  const next = useStore((s) => s.nextRetryAt || 0);
  const now = useNow(1000);
  if (conn !== "offline") return null;
  const secs = Math.max(0, Math.ceil((next - now) / 1000));
  return html`<div class="gbanner gbanner--offline" role="alert" data-testid="offline-banner">
    <${Icon} name="wifiOff" size=${18} />
    <div class="grow">
      <strong>${t("offline.title")}</strong>
      <span class="gbanner__text">${secs > 0 ? t("offline.retryIn", { s: secs }) : t("offline.retrying")}</span>
    </div>
    <${Button} size="sm" variant="secondary" onClick=${reconnectNow}>${t("offline.retryNow")}</${Button}>
  </div>`;
}

function FullScreen({ icon, tone = "neutral", title, text, children, testid, reason }) {
  return html`<div class="fullscreen" data-testid=${testid} data-reason=${reason}>
    <div class="fullscreen__card">
      <div class="fullscreen__brand"><${Logo} size=${40} /><span class="brand__name">${t("app.name")}</span></div>
      <div class=${cx("fullscreen__icon", `fullscreen__icon--${tone}`)}><${Icon} name=${icon} size=${28} /></div>
      <h1 class="fullscreen__title">${title}</h1>
      <div class="fullscreen__text">${text}</div>
      ${children && html`<div class="fullscreen__actions">${children}</div>`}
    </div>
  </div>`;
}

/**
 * No valid session (401) or signed out. Sign-in links are one-time codes, so a
 * reload never helps: the person runs `themesh open` / `themesh url` and opens the
 * new link. "Check again" covers having done that in another tab (same cookie).
 */
function Unauthorized() {
  const signedOut = useStore((s) => s.signedOut);
  const linkUsed = deadLoginCode;
  const [checking, setChecking] = useState(false);
  const reason = signedOut ? "signed-out" : linkUsed ? "link" : "expired";
  const cmd = { open: html`<code class="mono">themesh open</code>`, url: html`<code class="mono">themesh url</code>` };
  const title = signedOut ? t("auth.signedOut.title") : linkUsed ? t("auth.link.title") : t("auth.expired.title");
  const text = signedOut ? tx("auth.signedOut.text", cmd) : linkUsed ? tx("auth.link.text", cmd) : tx("auth.expired.text", cmd);
  const check = async () => {
    setChecking(true);
    await refreshState();
    if (!state.authError) reconnectNow();
    setChecking(false);
  };
  return html`<${FullScreen} icon=${signedOut ? "logout" : "lock"} tone=${signedOut ? "neutral" : "warn"} testid="unauthorized"
      reason=${reason} title=${title}
      text=${html`<div class="auth-text">
        <p>${text}</p>
        <div class="code-line mt-4"><span>themesh open</span><${CopyButton} text="themesh open" /></div>
        <p class="faint small mt-3">${t("auth.checkHint")}</p>
      </div>`}>
    <${Button} variant="primary" icon="refresh" loading=${checking} onClick=${check} data-testid="auth-retry">${t("auth.retry")}</${Button}>
  </${FullScreen}>`;
}

function LoadFailed() {
  return html`<${FullScreen} icon="wifiOff" tone="err" testid="load-failed" title=${t("boot.failTitle")} text=${html`<p>${t("boot.failText")}</p>`}>
    <${Button} variant="primary" icon="refresh" onClick=${() => { refreshState(); reconnectNow(); }}>${t("offline.retryNow")}</${Button}>
  </${FullScreen}>`;
}

function Booting() {
  return html`<div class="fullscreen"><div class="center stack" style="align-items:center">
    <${Logo} size=${48} class="boot-logo" />
    <${Spinner} size=${20} label=${t("common.loading")} />
  </div></div>`;
}

const TITLES = {
  home: "nav.home", devices: "nav.devices", files: "nav.files", mail: "nav.mail", chat: "nav.chat",
  services: "nav.services", settings: "nav.settings", more: "nav.more",
};

function useDocumentTitle(section) {
  const counters = useStore((s) => s.counters);
  const configured = useStore((s) => s.configured);
  const lang = useStore((s) => s.lang);
  useEffect(() => {
    const n = (counters.mail || 0) + (counters.chat || 0) + (counters.offers || 0);
    const page = configured ? t(TITLES[section] || "nav.home") : t("onb.title");
    document.title = `${n ? `(${n}) ` : ""}${page} — ${t("app.name")}`;
  }, [counters, section, configured, lang]);
}

function App() {
  const route = useRoute();
  const booted = useStore((s) => s.booted);
  const authError = useStore((s) => s.authError);
  const loadError = useStore((s) => s.loadError);
  const configured = useStore((s) => s.configured);
  const hasSelf = useStore((s) => !!s.self);
  const lang = useStore((s) => s.lang);
  let section = route.parts[0] || "home";
  if (!VIEWS[section]) section = "home";
  useDocumentTitle(section);

  // Move focus to the main region on navigation between sections (a11y).
  useEffect(() => {
    const m = document.getElementById("main");
    if (m && document.activeElement && document.activeElement.closest && document.activeElement.closest(".nav, .tabbar")) {
      m.focus({ preventScroll: true });
    }
    window.scrollTo(0, 0);
  }, [section]);

  // Onboarding ignores the route; start the member UI from "#/" once it is done.
  useEffect(() => {
    if (booted && !configured && !["", "#", "#/"].includes(location.hash)) go("#/", { replace: true });
  }, [booted, configured]);

  if (authError) return html`<${Unauthorized} />${html`<${ToastHost} />`}`;
  if (!booted) return html`<${Booting} />`;
  if (loadError && !hasSelf) return html`<${LoadFailed} />`;
  if (!configured) return html`<${OnboardingView} key=${lang} /><${DialogHost} /><${ToastHost} />`;

  const View = VIEWS[section];
  const fixed = FIXED.has(section);
  // On phones a conversation / an open message takes the whole screen.
  const immersive = (section === "chat" && !!route.parts[1]) || (section === "mail" && (!!route.parts[2] || route.parts[1] === "compose"));
  // Home lists incoming offers itself (with Принять / Отклонить), Files → Send has them in its list.
  const showOffers = section !== "home" && !(section === "files" && (route.parts[1] || "send") === "send");
  return html`
    <a class="skip-link" href="#main" onClick=${(e) => { e.preventDefault(); document.getElementById("main").focus(); }}>${t("app.skip")}</a>
    <div class=${cx("shell", fixed && "shell--fixed", immersive && "shell--immersive")} key=${lang}>
      <${Sidebar} section=${section} />
      <div class="main">
        <${TopBar} />
        <div class="banners">
          <${OfflineBanner} />
          ${showOffers && html`<${OffersBanner} />`}
        </div>
        <main id="main" class=${cx("content", `content--${section}`)} tabindex="-1" data-testid=${"page-" + section}>
          <${View} route=${route} />
        </main>
      </div>
      <${TabBar} section=${section} />
    </div>
    <${HelpHost} />
    <${DialogHost} />
    <${ToastHost} />`;
}

const loadedAt = Date.now();

function registerSW() {
  if (!("serviceWorker" in navigator)) return;
  if (location.protocol !== "https:" && location.hostname !== "127.0.0.1" && location.hostname !== "localhost") return;
  // A new binary serves a new sw.js (the node stamps VERSION with a hash of the UI
  // files). When a new worker takes over a page that already had one, this page
  // runs the previous UI: reload once. Not on the very first install (no
  // controller yet), at most once per page. Right after loading (the usual case:
  // the update is found by this very navigation) reload at once; later in a
  // session, offer it instead so a half-written message is not lost.
  const had = !!navigator.serviceWorker.controller;
  let done = false;
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (!had || done) return;
    done = true;
    if (Date.now() - loadedAt < 30000 || document.visibilityState === "hidden") location.reload();
    else toast({ level: "info", title: t("app.updated"), text: t("app.updatedText"), actionLabel: t("app.reload"), onAction: () => location.reload(), timeout: 0 });
  });
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").catch(() => { /* not critical */ });
  });
}

// A sign-in code that reaches the page was not accepted: the node redirects a
// good one away (`/?t=…` → `/`). Drop it from the address bar either way.
const deadLoginCode = new URLSearchParams(location.search).has("t");
if (deadLoginCode) {
  const q = new URLSearchParams(location.search);
  q.delete("t");
  history.replaceState(history.state, "", location.pathname + (q.toString() ? "?" + q : "") + location.hash);
}

initPrefs();
const root = document.getElementById("app");
root.textContent = "";
render(html`<${App} />`, root);
startLive();
registerSW();

// Expose for debugging in the console (read-only use).
window.__themesh = { state };
