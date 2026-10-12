// Entry point: the app shell — a file manager (js/shell.js): the places on the left, the path on top, the place itself in
// the middle — global banners and states, routing to views.
import { html, render, useEffect, useState } from "../vendor/preact-htm.js";
import { Icon, Logo } from "./icons.js";
import { t, tx } from "./i18n.js";
import { initPrefs } from "./prefs.js";
import { startRosa } from "./rosa.js";
import { go, useRoute } from "./router.js";
import { refreshState, reconnectNow, startLive } from "./sse.js";
import { state, useStore } from "./store.js";
import { cx } from "./util.js";
import { useNow } from "./hooks.js";
import { DialogHost } from "./components/modal.js";
import { toast, ToastHost } from "./components/toast.js";
import { Button, CopyButton, Spinner } from "./components/ui.js";
import { PathBar, Places, StatusBar, placeOf, trailOf } from "./shell.js";
import { OnboardingView } from "./views/onboarding.js";
import { HomeView } from "./views/home.js";
import { HelpHost } from "./views/help.js";
import { DevicesView } from "./views/devices.js";
import { FilesView } from "./views/files.js";
import { MailView } from "./views/mail.js";
import { ChatView } from "./views/chat.js";
import { ServicesView } from "./views/services.js";
import { SettingsView } from "./views/settings.js";
import { OffersBanner } from "./views/transfers.js";
import { NearbyAskHost, NearbyView } from "./views/nearby.js";

const VIEWS = {
  home: HomeView,
  devices: DevicesView,
  files: FilesView,
  nearby: NearbyView,
  mail: MailView,
  chat: ChatView,
  services: ServicesView,
  settings: SettingsView,
};

// Screens that manage their own scrolling panes (fixed-height layout).
const FIXED = new Set(["mail", "chat"]);

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

function useDocumentTitle(trail, configured) {
  const counters = useStore((s) => s.counters);
  const lang = useStore((s) => s.lang);
  const asks = useStore((s) => s.nearby.requests.length + s.nearby.invites.length); // devices nearby that wait for an answer
  const here = configured && trail.length ? trail[trail.length - 1].label : "";
  useEffect(() => {
    const n = (counters.mail || 0) + (counters.chat || 0) + (counters.offers || 0) + asks;
    const page = configured ? here : t("onb.title");
    document.title = `${n ? `(${n}) ` : ""}${page} — ${t("app.name")}`;
  }, [counters, here, configured, lang, asks]);
}

function App() {
  const route = useRoute();
  const booted = useStore((s) => s.booted);
  const authError = useStore((s) => s.authError);
  const loadError = useStore((s) => s.loadError);
  const configured = useStore((s) => s.configured);
  const lang = useStore((s) => s.lang);
  let section = route.parts[0] || "home";
  if (!VIEWS[section]) section = "home";
  // (the path and the place are worked out from these: subscribing to them is what keeps the path bar current)
  const self = useStore((s) => s.self);
  useStore((s) => s.peers);
  useStore((s) => s.labels);
  const trail = self ? trailOf(route, state) : [];
  const place = placeOf(route, state);
  useDocumentTitle(trail, configured);

  // Move focus to the main region on navigation between places (a11y).
  useEffect(() => {
    const m = document.getElementById("main");
    if (m && document.activeElement && document.activeElement.closest && document.activeElement.closest(".places, .fm-bar")) {
      m.focus({ preventScroll: true });
    }
    window.scrollTo(0, 0);
  }, [place]);

  // Onboarding ignores the route; start the member UI from "#/" once it is done. The phone's «Ещё» (more) is gone: the
  // overview is where everything is.
  useEffect(() => {
    if (booted && !configured && !["", "#", "#/"].includes(location.hash)) go("#/", { replace: true });
    else if (route.parts[0] === "more") go("#/home", { replace: true });
  }, [booted, configured, route.parts[0]]);

  if (authError) return html`<${Unauthorized} />${html`<${ToastHost} />`}`;
  if (!booted) return html`<${Booting} />`;
  if (loadError && !self) return html`<${LoadFailed} />`;
  if (!configured) return html`<${OnboardingView} key=${lang} /><${NearbyAskHost} /><${DialogHost} /><${ToastHost} />`;

  const View = VIEWS[section];
  const fixed = FIXED.has(section);
  // On phones a conversation / an open message takes the whole screen.
  const immersive = (section === "chat" && !!route.parts[1]) || (section === "mail" && (!!route.parts[2] || route.parts[1] === "compose"));
  // The overview lists incoming offers itself (with Принять / Отклонить), Transfers has them in its list.
  const showOffers = section !== "home" && !(section === "files" && (route.parts[1] || "send") === "send");
  return html`
    <a class="skip-link" href="#main" onClick=${(e) => { e.preventDefault(); document.getElementById("main").focus(); }}>${t("app.skip")}</a>
    <div class=${cx("shell fm", fixed && "shell--fixed", immersive && "shell--immersive", place === "home" && "fm--root")} key=${lang} data-place=${place}>
      <${Places} place=${place} />
      <div class="main">
        <${PathBar} trail=${trail} />
        <div class="banners">
          <${OfflineBanner} />
          ${showOffers && html`<${OffersBanner} />`}
        </div>
        <main id="main" class=${cx("content", `content--${section}`)} tabindex="-1" data-testid=${"page-" + section}>
          <${View} route=${route} />
        </main>
        <${StatusBar} />
      </div>
    </div>
    <${HelpHost} />
    <${NearbyAskHost} />
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
startRosa();
startLive();
registerSW();

// Expose for debugging in the console (read-only use).
window.__themesh = { state };
