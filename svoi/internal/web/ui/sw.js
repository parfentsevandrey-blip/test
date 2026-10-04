// Service worker: makes the UI shell installable and fast.
//  - static shell: cache-first (stale-while-revalidate, so updates roll in),
//  - navigations: network-first with the cached index.html as fallback,
//  - anything under /api/ (and the ?t= login link): never touched, never cached.
// The node rewrites the next line on the fly to "themesh-ui-<hash of the embedded UI
// files>", so every new binary gets a new cache. Keep it exactly as it is.
const VERSION = "themesh-ui-v1";
const SHELL = [
  "./",
  "index.html",
  "manifest.webmanifest",
  "css/tokens.css",
  "css/base.css",
  "css/components.css",
  "css/layout.css",
  "css/views.css",
  "css/glass.css",
  "css/fonts.css",
  "css/rosa-stars.css",
  "css/rosa.css",
  "fonts/manrope.woff2",
  "fonts/cormorant.woff2",
  "js/sky-palette.js",
  "js/boot.js",
  "js/rosa.js",
  "js/app.js",
  "js/api.js",
  "js/store.js",
  "js/router.js",
  "js/sse.js",
  "js/i18n.js",
  "js/prefs.js",
  "js/format.js",
  "js/util.js",
  "js/hooks.js",
  "js/icons.js",
  "js/i18n/ru.js",
  "js/i18n/en.js",
  "js/components/ui.js",
  "js/components/portal.js",
  "js/components/modal.js",
  "js/components/toast.js",
  "js/components/menu.js",
  "js/components/avatar.js",
  "js/components/devicepicker.js",
  "js/components/misc.js",
  "js/components/folderpicker.js",
  "js/components/device-actions.js",
  "js/views/onboarding.js",
  "js/views/home.js",
  "js/views/help.js",
  "js/views/devices.js",
  "js/views/topology.js",
  "js/views/device-drawer.js",
  "js/views/add-device.js",
  "js/views/files.js",
  "js/views/files-send.js",
  "js/views/files-browse.js",
  "js/views/files-shares.js",
  "js/views/transfers.js",
  "js/views/preview.js",
  "js/views/mail.js",
  "js/views/compose.js",
  "js/views/chat.js",
  "js/views/services.js",
  "js/views/settings.js",
  "js/views/logs.js",
  "js/views/more.js",
  "vendor/preact-htm.js",
  "icons/icon.svg",
  "icons/icon-192.png",
  "icons/icon-512.png",
  "icons/maskable-512.png",
  "icons/apple-touch-icon.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(VERSION);
    // Add one by one so a single missing file does not abort the install.
    await Promise.all(SHELL.map((u) => cache.add(new Request(u, { cache: "reload" })).catch(() => {})));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) if (key !== VERSION) await caches.delete(key);
    await self.clients.claim();
  })());
});

function isApi(url) {
  return url.pathname.includes("/api/") || url.searchParams.has("t");
}

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin || isApi(url)) return; // network only

  if (req.mode === "navigate") {
    event.respondWith((async () => {
      try {
        const res = await fetch(req);
        if (res.ok) {
          const cache = await caches.open(VERSION);
          cache.put("index.html", res.clone());
        }
        return res;
      } catch {
        const cached = await caches.match("index.html");
        return cached || Response.error();
      }
    })());
    return;
  }

  event.respondWith((async () => {
    const cache = await caches.open(VERSION);
    const cached = await cache.match(req, { ignoreSearch: true });
    const network = fetch(req).then((res) => {
      if (res.ok && res.type === "basic") cache.put(req, res.clone());
      return res;
    }).catch(() => null);
    if (cached) {
      event.waitUntil(network);
      return cached;
    }
    const res = await network;
    return res || Response.error();
  })());
});
