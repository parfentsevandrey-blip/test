# svoi web UI — notes

The UI is a static single-page app in `internal/web/ui/` (embedded into the binary with
`go:embed`). It talks only to its own node over the API in `docs/UI-API.md`. No build step,
no npm at runtime, no CDN: every byte is served from the embedded directory.

## Layout of the code

```
internal/web/ui/
  index.html              entry point; loads css/*, js/boot.js (theme before first paint), js/app.js
  manifest.webmanifest    PWA manifest (start_url ./#/devices, scope ./)
  sw.js                   service worker: static shell cache-first (stale-while-revalidate),
                          navigations network-first, never touches /api/ or ?t=
  icons/                  icon.svg (any), maskable.svg, PNG renders (192/512/maskable/apple-touch)
  vendor/preact-htm.js    Preact 10 + hooks + htm as one ES module (rebuild: web-dev/vendor-build/)
  css/tokens.css          every colour/spacing/radius/motion token; [data-theme=dark|light]
  css/base.css            reset, typography, focus rings, utilities, reduced motion
  css/components.css      buttons, chips, inputs, modal/drawer, toasts, menu, tabs, …
  css/layout.css          shell: sidebar (≥1100px), icon rail (760–1099px), tab bar (<760px)
  css/views.css           per-screen styles
  js/app.js               shell, global banners, full-screen states, view routing
  js/api.js               fetch/XHR helpers (relative URLs, X-Svoi header, ApiError)
  js/sse.js               one EventSource + backoff; GET api/state on every (re)connect
  js/store.js             tiny global store + useStore(selector) + event bus
  js/router.js            hash router (#/section/…, each segment URI-encoded)
  js/i18n.js, i18n/*.js   t()/tn()/tx(); ru (default) and en dictionaries, Russian plurals
  js/prefs.js             language/theme (localStorage "svoi.lang"/"svoi.theme")
  js/format.js            sizes, speeds, RTT, dates, durations (Intl)
  js/util.js              linkify, device/file-kind heuristics, clipboard, misc
  js/hooks.js             useAsync, useNow, useMedia, useInterval, usePersistent…
  js/icons.js             hand-drawn inline SVG icon set + logo mark
  js/components/          ui (Button, IconButton, Chip, Card, Switch, Segmented, Field,
                          EmptyState, Skeleton, Callout, KV, CopyButton, Progress…),
                          modal (Modal, Drawer, confirmDialog, promptDialog), toast, menu,
                          portal, avatar (DeviceAvatar, FileIcon), devicepicker
                          (DeviceChips, ManageDeviceSelect), folderpicker, misc
  js/views/               onboarding, devices (+topology, device-drawer, add-device),
                          files (+files-send, transfers, files-browse, preview, files-shares),
                          mail (+compose), chat, services, settings (+logs), more
web-dev/
  mock-server.mjs         zero-dependency mock of the whole API (see below)
  screenshots.mjs         walks every screen in dark/light × 1440×900/390×844 → web-dev/screens/
  smoke.mjs               interaction smoke test of the main flows (fails on console errors)
  check-i18n.mjs          every t() key exists in ru and en; plural forms are complete
  make-assets.mjs         renders the PNG icons and web-dev/fixtures/sample.webm with Chromium
  lib.mjs                 helpers shared by the scripts
```

Routes: `#/devices[/<id>]`, `#/files/send[?to=<id>]`, `#/files/browse[/<dev>[/<share>[/<path…>]]]`
(`self` = this device), `#/files/shares[?d=<id>]`, `#/mail/<inbox|sent|trash>[/<msgId>]`,
`#/mail/compose[?to=<id,…>|?reply=<msgId>[&all=1]]`, `#/chat[/<peerId>]`,
`#/services[?peer=<id>][&d=<id>]`, `#/settings[/<device|network|tun|files|interface|advanced|about>][?d=<id>]`,
`#/more` (phones). `?d=<peerId>` = "Manage device ▾" (admin, through `/api/d/:id/…`).

## Running

```sh
node web-dev/mock-server.mjs                      # http://127.0.0.1:8777/  (scenario "full")
node web-dev/mock-server.mjs --scenario empty     # a mesh with only this device
node web-dev/mock-server.mjs --scenario onboarding
node web-dev/mock-server.mjs --calm               # no random background events (stable screenshots)
node web-dev/mock-server.mjs --auth               # requires the cookie: open /?t=dev first
node web-dev/mock-server.mjs --tun-error          # enabling the TUN interface fails ("permission denied")
node web-dev/mock-server.mjs --latency 300        # slower API to look at loading states

export NODE_PATH=/opt/node22/lib/node_modules      # Playwright is installed globally
node web-dev/screenshots.mjs [--only devices,mail] [--lang en]
node web-dev/smoke.mjs [--only mail,chat] [--base http://127.0.0.1:18777]
node web-dev/check-i18n.mjs
node web-dev/make-assets.mjs                      # only when icons/video fixture change
```

Mock hooks (test-only, GET or POST): `/__mock/offer?from=phone`, `/__mock/chat?from=dad-pc&text=…`,
`/__mock/mail?from=nas`, `/__mock/join[?name=tablet]` (consumes the newest invite → device joins),
`/__mock/drop?for=5` (drop SSE, refuse reconnects for N s), `/__mock/peer?name=nas&online=0`,
`/__mock/auth?on=1`, `/__mock/tun?error=1`, `/__mock/reset`.

The mock fakes 7 devices (Linux laptop = this device, admin; `phone` over relay via
home-server; `home-server` direct + admin with ssh/jellyfin/grafana; `nas` on LAN with rw/ro
shares; `dad-pc` (Windows, alias, other owner, old version, clock skew); two offline devices),
generated media (procedural PNG "photos", SVG, WAV melodies, real PDFs, text, a VP8 clip,
large pseudo-random files streamed lazily) with Range support, transfers that progress over
SSE, mail/chat delivery state machines, invites with a fake QR, netcheck, logs, share/service/
forward CRUD, the `/api/d/:id/…` proxy (403 for non-admins, 502 for offline devices), 409 on
duplicate uploads, 403 on read-only shares. It also sends the CSP we recommend (below) so the
UI is verified to work under it.

## Notes for the Go side

* Serve `index.html` for `/`; hash routing means no other SPA fallback is needed.
  Serve `sw.js` with `Cache-Control: no-cache` (the SW updates the shell in the background;
  bump `VERSION` in `sw.js` when you want old caches dropped immediately).
* Recommended headers for the UI: `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `X-Frame-Options: DENY`, and
  `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; connect-src 'self'; frame-src 'self' blob:; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`.
  `data:` images are needed for the invite QR (shown via `<img src="data:image/svg+xml,…">`),
  `blob:` frames for the PDF preview.
* User files (`/api/peers/:id/file`, transfer files, attachments) come from other members and
  must be treated as hostile: send `nosniff`, and for `text/html`, `image/svg+xml` and other
  active types either force `Content-Disposition: attachment` or add `Content-Security-Policy: sandbox`.
  The UI itself never navigates to these URLs: images go through `<img>`, text is fetched and
  shown as text, PDFs are re-wrapped in a `Blob` typed `application/pdf`, everything else is
  a download link.
* Nothing from the API is ever inserted as HTML. Mail/chat bodies are rendered as text with
  `white-space: pre-wrap`; http(s) URLs become `<a rel="noopener noreferrer" target="_blank">`.

## Stable selectors (`data-testid`)

Shell: `nav-<section>` (sidebar), `tab-<devices|files|mail|chat|more>` (phone tab bar),
`page-<section>` (the `<main>`), `conn-pill`, `nat-chip` (`data-difficulty`), `offline-banner`,
`offers-banner`, `offer-accept`, `offer-decline`, `toast` (`data-level`), `unauthorized`,
`load-failed`. Dialogs: `confirm-ok`, `confirm-cancel`, `confirm-input`, `prompt-input`,
`prompt-ok`, `preview` (+`preview-name`, `preview-prev`, `preview-next`), `folder-picker`
(+`picker-item` `data-name`, `picker-choose`), `device-drawer`, `compose`, `invite-modal`.
Onboarding: `page-onboarding`, `onb-create`, `onb-join`, `onb-mesh-name`, `onb-device-name`,
`onb-owner`, `onb-code`, `onb-submit`, `onb-progress`, `onb-error`.
Devices: `add-device`, `topology`, `topo-node` (`data-id`, `data-path`), `device-card`
(`data-id`, `data-name`, `data-online`), `self-card`, `invites`, `invite-row`, `invite-create`,
`invite-qr`, `invite-code`, `invite-waiting`, `invite-done`, `device-ping`, `device-ping-result`.
Files: `files-tab-<send|browse|shares>`, `device-chip` (`data-id`, `data-name`), `dropzone-input`,
`staged-file`, `send-submit`, `transfer` (`data-id`, `data-state`, `data-dir`) with
`transfer-accept|decline|cancel|retry|remove|open|download`, `browse-device`, `share-card`
(`data-id`, `data-mode`), `file-row` / `file-tile` (`data-name`, `data-dir`), `file-filter`,
`view-list`, `view-grid`, `read-only`, `upload-button`, `upload-input`, `upload-panel`,
`upload-item` (`data-status`), `new-folder`, `share-add`, `share-row`, `share-path`, `share-pick`,
`share-name`, `share-save`, `manage-device`.
Mail: `mail-compose`, `mail-folder-<inbox|sent|trash>`, `mail-search`, `mail-item` (`data-id`,
`data-unread`), `mail-reader`, `mail-reply`, `mail-trash`, `mail-delivery`, `mail-recipient`
(`data-state`), `compose-subject`, `compose-body`, `compose-files`, `attachment` (`data-status`),
`compose-send`. Chat: `thread` (`data-peer`, `data-unread`), `chat-new`, `conversation`,
`bubble` (`data-id`, `data-state`, `data-mine`), `chat-input`, `chat-send`, `chat-files`.
Services: `service-card` (`data-peer`, `data-service`, `data-forwarded`), `service-connect`,
`service-disconnect`, `forward-addr`, `service-publish`, `service-name`, `service-addr`,
`service-save`, `published-service`. Settings: `settings-<section>`, `netcheck`, `leave-mesh`,
`setting-relay`, `tun-section`, `tun-state` (`data-state`), `tun-enabled`, `tun-hosts`,
`lang-<auto|ru|en>`, `theme-<auto|light|dark>`.

## API gaps, ambiguities and assumptions

1. **No SSE event for settings.** `settings.tun` counters (and `restartRequired`) only change
   on `GET`/`PUT`. The Settings screen polls `GET [d/:id/]settings` every 3 s while the TUN
   interface is enabled. A `settings` SSE event (full Settings) would remove the polling.
2. **TUN `PUT` semantics assumed**: `{"tun":{"enabled":true}}` / `{"tun":{"manageHosts":false}}`
   are merged into the current tun object; a failed start answers 200 with `state:"error"` and
   `error` (shown inline). For `socks` the UI always sends the whole object `{enabled, listen}`
   because the contract does not say nested objects are merged.
3. **Mail paging cursor.** `before=<ts of last item>` skips messages that share that second.
   The UI de-duplicates by id, but a cursor like `before=<ts>&beforeId=<id>` would be exact.
4. **Folder unread counts.** `GET /api/mail` returns `unread` for the requested folder only;
   the folder list shows the inbox count from `counters.mail` and nothing for Sent/Trash.
5. **Chat thread preview** has no attachment info: when `last.text` is empty the UI shows
   «Вложение». Chat attachments have no fetch progress (`got`), unlike mail attachments.
6. **`?dl=1` on chat attachments** is not documented; the UI adds it to download links and also
   sets the `download` attribute, so either behaviour works.
7. **No retry for failed chat messages / mail recipients** (no endpoint); the UI shows the state.
8. **Remote devices through `/api/d/:id/`**: `netcheck` is not in the forwarded list, so the
   "Проверить сеть" button is hidden for remote devices. `/api/d/:id/state` is assumed to return
   the same shape as `/api/state` (the UI reads `self` and `settings` from it).
9. **`fs` rename `to`** is assumed to be a bare name in the same folder (not a path);
   `delete` of a folder is assumed recursive (the confirmation says so).
10. **`<name>.svoi`** in the TUN hints uses `deviceName` (not the local alias).
11. **`Peer.services`** from `state`/`peers` is used for the Services screen (no per-device
    `GET /api/peers/:id/services` calls); it is assumed to be the list the device exposes *to us*.
12. **Join errors**: the UI maps `invalid` → "the code didn't work", `busy`/network → "couldn't
    reach the inviting device", `denied` → "rejected", and shows `error.message` below. A
    distinct code for "invite expired" would allow a more precise message.
13. **Thumbnails**: `GET …/thumb` should return `image/jpeg`; the mock returns PNG. Any image
    type works for the UI (404 → falls back to the original for files < 6 MB, then to an icon).
14. **`counters.offers`** is used for badges; the offers banner itself is derived from
    `transfers` (`dir:"in", state:"offered"`), so both must agree.
15. **Auth for SSE**: `EventSource` cannot send headers, so the cookie must authorise
    `GET /api/events` (the bearer header used by test tools is not available to the UI).
16. **Onboarding device name**: suggested from `self.os`; the UI normalises spaces to `-` and
    validates `^[\p{L}\p{N}][\p{L}\p{N}._-]{0,62}$` (same as rename) — adjust if the node is stricter.

## Rough edges / not done

* The topology uses one ring; beyond ~12 devices labels get crowded.
* No virtualised lists: folders with several thousand entries render but scroll heavier.
* No folder upload (drag a whole directory), no resumable uploads, no mail forwarding, no
  "starred" mail view (star is stored and shown only).
* The service worker serves the cached shell first; after an upgrade the first load may still
  show the previous UI until the background update finishes (reload fixes it).
* Verified in headless Chromium only (desktop and phone viewports, dark/light, RU/EN);
  Safari/Firefox not run here. Uses `inert`, `color-mix()`, `dvh` — current browsers only.
* The fake QR from the mock is not scannable (the real node renders a real one).
