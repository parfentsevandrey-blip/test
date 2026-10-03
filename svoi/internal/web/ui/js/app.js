// Entry point: app shell (sidebar / top bar / mobile tab bar), global
// banners and states, routing to views.
import { html, render, useEffect } from "../vendor/preact-htm.js";
import { Icon, Logo } from "./icons.js";
import { t, tn } from "./i18n.js";
import { initPrefs } from "./prefs.js";
import { href, useRoute } from "./router.js";
import { refreshState, reconnectNow, startLive } from "./sse.js";
import { state, useStore } from "./store.js";
import { cx, sortPeers } from "./util.js";
import { useNow } from "./hooks.js";
import { DialogHost } from "./components/modal.js";
import { ToastHost } from "./components/toast.js";
import { DeviceAvatar } from "./components/avatar.js";
import { Button, Spinner } from "./components/ui.js";
import { OnboardingView } from "./views/onboarding.js";
import { DevicesView } from "./views/devices.js";
import { FilesView } from "./views/files.js";
import { MailView } from "./views/mail.js";
import { ChatView } from "./views/chat.js";
import { ServicesView } from "./views/services.js";
import { SettingsView } from "./views/settings.js";
import { MoreView } from "./views/more.js";
import { OffersBanner } from "./views/transfers.js";

const NAV = [
  { id: "devices", icon: "devices", label: "nav.devices" },
  { id: "files", icon: "files", label: "nav.files", counter: "offers" },
  { id: "mail", icon: "mail", label: "nav.mail", counter: "mail" },
  { id: "chat", icon: "chat", label: "nav.chat", counter: "chat" },
  { id: "services", icon: "services", label: "nav.services" },
  { id: "settings", icon: "settings", label: "nav.settings" },
];

const TABS = ["devices", "files", "mail", "chat", "more"];

const VIEWS = {
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
    <a class="brand" href="#/devices" aria-label=${t("app.name")}>
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
        return html`<a key=${n.id} href=${"#/" + n.id} class=${cx("nav__item", active && "is-active")}
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
        <span class="ellipsis mono faint xsmall">${self.ip4 || self.short}</span>
      </span>
    </a>`}
  </aside>`;
}

function TabBar({ section }) {
  const counters = useStore((s) => s.counters);
  const restart = useStore((s) => !!(s.settings && s.settings.restartRequired));
  const items = [
    { id: "devices", icon: "devices", label: t("nav.devices") },
    { id: "files", icon: "files", label: t("nav.files"), n: counters.offers, tone: "warn" },
    { id: "mail", icon: "mail", label: t("nav.mail"), n: counters.mail },
    { id: "chat", icon: "chat", label: t("nav.chat"), n: counters.chat },
    { id: "more", icon: "more", label: t("nav.more"), dot: restart },
  ];
  const activeTab = TABS.includes(section) ? section : "more";
  return html`<nav class="tabbar" aria-label=${t("nav.label")}>
    ${items.map((it) => html`<a key=${it.id} href=${"#/" + it.id} class=${cx("tabbar__item", activeTab === it.id && "is-active")}
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

export function natTone(d) {
  return d === "open" || d === "easy" ? "ok" : d === "hard" ? "warn" : "muted";
}

function TopBar() {
  const self = useStore((s) => s.self);
  const peers = useStore((s) => s.peers);
  if (!self) return html`<header class="topbar"></header>`;
  const total = peers.length + 1;
  const online = peers.filter((p) => p.online).length + 1;
  const diff = (self.nat && self.nat.difficulty) || "unknown";
  const tone = natTone(diff);
  const onlinePeers = sortPeers(peers).slice(0, 4);
  return html`<header class="topbar">
    <a class="topbar__brand" href="#/devices" aria-label=${t("app.name")}>
      <${Logo} size=${30} />
    </a>
    <div class="topbar__self">
      <span class="topbar__name ellipsis">${self.name}</span>
      ${self.meshName && html`<span class="topbar__mesh ellipsis">${t("app.meshName", { name: self.meshName })}</span>`}
    </div>
    <div class="topbar__status">
      <a class="conn-pill" href="#/devices" title=${t("top.onlineTitle")}>
        <span class="conn-pill__dots" aria-hidden="true">
          ${onlinePeers.map((p) => html`<span key=${p.id} class=${cx("conn-pill__dot", p.online ? (p.path === "relay" ? "is-relay" : "is-on") : "is-off")}></span>`)}
        </span>
        <span class="conn-pill__text tnum">
          <span class="conn-pill__long">${t("top.online", { n: online, total })}</span>
          <span class="conn-pill__short">${online}/${total}</span>
        </span>
      </a>
      <a class=${cx("nat-chip", `nat-chip--${tone}`)} href="#/settings/network" title=${t("nat.title." + diff)}>
        <${Icon} name=${tone === "warn" ? "alert" : tone === "ok" ? "shieldCheck" : "radar"} size=${15} />
        <span class="nat-chip__text">${t("nat.chip." + diff)}</span>
      </a>
    </div>
  </header>`;
}

function OfflineBanner() {
  const conn = useStore((s) => s.conn);
  const next = useStore((s) => s.nextRetryAt || 0);
  const now = useNow(1000);
  if (conn !== "offline") return null;
  const secs = Math.max(0, Math.ceil((next - now) / 1000));
  return html`<div class="gbanner gbanner--offline" role="alert">
    <${Icon} name="wifiOff" size=${18} />
    <div class="grow">
      <strong>${t("offline.title")}</strong>
      <span class="gbanner__text">${secs > 0 ? t("offline.retryIn", { s: secs }) : t("offline.retrying")}</span>
    </div>
    <${Button} size="sm" variant="secondary" onClick=${reconnectNow}>${t("offline.retryNow")}</${Button}>
  </div>`;
}

function FullScreen({ icon, tone = "neutral", title, text, children }) {
  return html`<div class="fullscreen">
    <div class="fullscreen__card">
      <div class="fullscreen__brand"><${Logo} size=${40} /><span class="brand__name">${t("app.name")}</span></div>
      <div class=${cx("fullscreen__icon", `fullscreen__icon--${tone}`)}><${Icon} name=${icon} size=${28} /></div>
      <h1 class="fullscreen__title">${title}</h1>
      <div class="fullscreen__text">${text}</div>
      ${children && html`<div class="fullscreen__actions">${children}</div>`}
    </div>
  </div>`;
}

function Unauthorized() {
  return html`<${FullScreen} icon="lock" tone="warn" title=${t("auth.title")}
      text=${html`<p>${t("auth.text1")}</p><p class="mt-2">${t("auth.text2")}</p>
        <div class="code-line mt-4"><span>svoi open</span></div>`}>
    <${Button} variant="primary" icon="refresh" onClick=${() => location.reload()}>${t("auth.retry")}</${Button}>
  </${FullScreen}>`;
}

function LoadFailed() {
  return html`<${FullScreen} icon="wifiOff" tone="err" title=${t("boot.failTitle")} text=${html`<p>${t("boot.failText")}</p>`}>
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
  devices: "nav.devices", files: "nav.files", mail: "nav.mail", chat: "nav.chat",
  services: "nav.services", settings: "nav.settings", more: "nav.more",
};

function useDocumentTitle(section) {
  const counters = useStore((s) => s.counters);
  const configured = useStore((s) => s.configured);
  const lang = useStore((s) => s.lang);
  useEffect(() => {
    const n = (counters.mail || 0) + (counters.chat || 0) + (counters.offers || 0);
    const page = configured ? t(TITLES[section] || "nav.devices") : t("onb.title");
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
  let section = route.parts[0] || "devices";
  if (!VIEWS[section]) section = "devices";
  useDocumentTitle(section);

  // Move focus to the main region on navigation between sections (a11y).
  useEffect(() => {
    const m = document.getElementById("main");
    if (m && document.activeElement && document.activeElement.closest && document.activeElement.closest(".nav, .tabbar")) {
      m.focus({ preventScroll: true });
    }
    window.scrollTo(0, 0);
  }, [section]);

  if (authError) return html`<${Unauthorized} />${html`<${ToastHost} />`}`;
  if (!booted) return html`<${Booting} />`;
  if (loadError && !hasSelf) return html`<${LoadFailed} />`;
  if (!configured) return html`<${OnboardingView} key=${lang} /><${DialogHost} /><${ToastHost} />`;

  const View = VIEWS[section];
  const fixed = FIXED.has(section);
  const showOffers = !(section === "files" && (route.parts[1] || "send") === "send");
  return html`
    <a class="skip-link" href="#main" onClick=${(e) => { e.preventDefault(); document.getElementById("main").focus(); }}>${t("app.skip")}</a>
    <div class=${cx("shell", fixed && "shell--fixed")} key=${lang}>
      <${Sidebar} section=${section} />
      <div class="main">
        <${TopBar} />
        <div class="banners">
          <${OfflineBanner} />
          ${showOffers && html`<${OffersBanner} />`}
        </div>
        <main id="main" class=${cx("content", `content--${section}`)} tabindex="-1">
          <${View} route=${route} />
        </main>
      </div>
      <${TabBar} section=${section} />
    </div>
    <${DialogHost} />
    <${ToastHost} />`;
}

function registerSW() {
  if (!("serviceWorker" in navigator)) return;
  if (location.protocol !== "https:" && location.hostname !== "127.0.0.1" && location.hostname !== "localhost") return;
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").catch(() => { /* not critical */ });
  });
}

initPrefs();
const root = document.getElementById("app");
root.textContent = "";
render(html`<${App} />`, root);
startLive();
registerSW();

// Expose for debugging in the console (read-only use).
window.__svoi = { state };
