# svoi web UI — notes

The UI is a static single-page app in `internal/web/ui/` (embedded into the binary with
`go:embed`). It talks only to its own node over the API in `docs/UI-API.md`. No build step,
no npm at runtime, no CDN: every byte is served from the embedded directory.

## Layout of the code

```
internal/web/ui/
  index.html              entry point; loads css/*, js/boot.js (theme before first paint), js/app.js
  manifest.webmanifest    PWA manifest (start_url ./#/home, scope ./)
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
  js/prefs.js             language/theme (localStorage "svoi.lang"/"svoi.theme"), Home's
                          first-steps flags ("svoi.home.browsed|sent|startDismissed")
  js/format.js            sizes, speeds, RTT, dates, durations (Intl)
  js/util.js              linkify, device/file-kind heuristics, osName, clipboard, dnsLabel/uniqueLabel
                          (device name → DNS label, ports of SanitizeName/UniqueName), misc
  js/hooks.js             useAsync, useNow, useMedia, useInterval, usePersistent…
  js/icons.js             hand-drawn inline SVG icon set + logo mark
  js/components/          ui (Button, IconButton, Chip, Card, Switch, Segmented, Field,
                          EmptyState, Skeleton, Callout, KV, CopyButton, Progress…),
                          modal (Modal, Drawer, confirmDialog, promptDialog), toast, menu,
                          portal, avatar (DeviceAvatar, FileIcon), devicepicker
                          (DeviceChips, ManageDeviceSelect), folderpicker, misc (PageHeader,
                          ExpertTag, TechDetails…), device-actions (platformLine, ConnLine,
                          DeviceActions: the plain device words and buttons of Home and Devices)
  js/views/               home (landing page), help («Как это работает?» sheet), onboarding,
                          devices (+topology, device-drawer, add-device),
                          files (+files-send, transfers, files-browse, preview, files-shares),
                          mail (+compose), chat, services, settings (+logs), more
web-dev/
  mock-server.mjs         zero-dependency mock of the whole API (see below)
  screenshots.mjs         walks every screen in dark/light × 1440×900/390×844 → web-dev/screens/ (generated, not tracked; the few shown in the README live in docs/img/;
                          `--dpr 1` makes the phone shots 390 px wide, as docs/img/home-mobile.png)
  smoke.mjs               interaction smoke test of the main flows (fails on console errors)
  check-i18n.mjs          every t() key exists in ru and en; plural forms are complete
  check-util.mjs          unit-ish asserts for js/util.js (dnsLabel incl. the docs/Go test cases)
  make-assets.mjs         renders the PNG icons and web-dev/fixtures/sample.webm with Chromium
  lib.mjs                 helpers shared by the scripts
```

Routes: `#/home[/<id>]` (the default; `/<id>` opens the device drawer over Home; `#/home?add=1` opens
«Добавить устройство» — the empty states of Chat, Files and the recipient chips link there), `#/devices[/<id>]`, `#/files/send[?to=<id>]`, `#/files/browse[/<dev>[/<share>[/<path…>]]]`
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
node web-dev/mock-server.mjs --auth               # sign-in required: open the one-time /?t=<code> link it prints
                                                  # (more from POST /__mock/login; scripts: Authorization: Bearer dev)
node web-dev/mock-server.mjs --tun-error          # enabling the TUN interface fails ("permission denied")
node web-dev/mock-server.mjs --latency 300        # slower API to look at loading states

export NODE_PATH=/opt/node22/lib/node_modules      # Playwright is installed globally
node web-dev/screenshots.mjs [--only devices,mail] [--lang en] [--out /tmp/shots] [--dpr 1]
node web-dev/smoke.mjs [--only mail,chat]
# against the real node: `svoi demo --no-browser --quiet --port 18777 --dir /tmp/demo`, token = the master token in
# /tmp/demo/laptop/data/ui.token (the printed ?t= links are one-time sign-in codes, not the token)
# (steps that need mock hooks or the mock's sample files are skipped or fail on data, not on the UI)
node web-dev/smoke.mjs --base http://127.0.0.1:18777 --token <TOKEN>
node web-dev/check-i18n.mjs
node web-dev/check-util.mjs
node web-dev/make-assets.mjs                      # only when icons/video fixture change
```

Mock hooks (test-only, GET or POST): `/__mock/offer?from=phone[&name=…]`, `/__mock/chat?from=dad-pc&text=…`,
`/__mock/mail?from=nas`, `/__mock/join[?name=tablet]` (consumes the newest invite → device joins),
`/__mock/drop?for=5` (drop SSE, refuse reconnects for N s), `/__mock/peer?name=nas&online=0`,
`/__mock/auth?on=1`, `/__mock/login` (a fresh one-time sign-in link),
`/__mock/portmap?state=mapped|searching|unavailable|private` (router port mapping of this device;
also switches `settings.portMap` on), `/__mock/sw?bump=1` (sw.js
gets a new `VERSION`, as after installing a new binary), `/__mock/tun?error=1`, `/__mock/removed` (an admin removed this device:
back to `configured:false` with `removed`, a fresh identity, the warn `notify` + empty `peers`
events like the node sends), `/__mock/reset`.

The mock fakes 7 devices (Linux laptop = this device, admin; `phone` over relay via
home-server; `home-server` direct + admin with ssh/jellyfin/grafana; `nas` on LAN with rw/ro
shares; `dad-pc` (Windows, alias, other owner, old version, clock skew); two offline devices),
generated media (procedural PNG "photos", SVG, WAV melodies, real PDFs, text, a VP8 clip,
large pseudo-random files streamed lazily) with Range support, transfers that progress over
SSE, mail/chat delivery state machines, invites with a fake QR, netcheck, logs, share/service/
forward CRUD, the `/api/d/:id/…` proxy (403 for non-admins, 502 for offline devices), 409 on
duplicate uploads, 403 on read-only shares, device names turned into DNS labels (same rules as
`SanitizeName`/`UniqueName`), demotion refused with 501 `unsupported`. It also sends the CSP we
recommend (below) so the UI is verified to work under it.

## Plain language: Home and the wording rules

The interface is meant for people who don't care how a mesh works. The landing page is
**«Главная»** (`#/home`, first in the sidebar and the phone tab bar); everything else is one tap away.

**Home**, top to bottom (one column on phones, the same order for the keyboard):

1. *Status sentence* (`home-status`, `data-state`):
   `ok` «Всё в порядке — 5 из 7 устройств на связи»; `partial` «Часть устройств не в сети: nas»;
   `offline` «Другие устройства сейчас не в сети»; `alone` «Вы пока одни в сети — добавьте второе
   устройство». Counts include this device, like the top bar. Only devices that dropped off within
   the last 24 h (`lastSeen`) make it `partial` and are named; devices away for longer (a tablet in a
   drawer) are mentioned calmly under `ok` («mom-laptop и old-tablet сейчас не в сети — письма и файлы
   для них подождут»). With other devices but none online it is `offline`. A «Как это работает?»
   button opens the help sheet.
2. *Нужно ваше внимание* (`home-attention`, hidden when empty): incoming file offers with
   Принять/Отклонить right in the row (up to 3, then a link to Files), unread mail and chat, pending
   invitations (with whom they are for and the countdown), «перезапустите svoi» when
   `restartRequired`. Because Home lists offers itself, the global offers banner is **not** shown on
   Home (it still is on every other page except Files → Send). Accepting toasts «Принимаем «…»» with a
   link to Files.
3. *Начало работы* (`home-start-checklist`): add a second device → open another device's files →
   send something. Shown while the network is young (≤ 1 other device, or no transfers in the store)
   and not dismissed. Step 1 is done when there is a peer; step 3 when the store has an outgoing
   transfer. Looking at another device's folders and sending chat/mail leave no trace in the API,
   so those are remembered per browser (`svoi.home.browsed`, `svoi.home.sent`); «Скрыть» stores
   `svoi.home.startDismissed`.
4. *Four big actions*: «Отправить файл» and «Написать сообщение» ask «Кому?» in a sheet when there
   are two or more devices (`device-pick`, «Выбрать на следующем шаге» for files), go straight to the
   only device when there is one, and say «Сначала добавьте второе устройство» (and open the invite
   dialog) when there is none. «Файлы на других устройствах» → Files → Обзор, «Добавить устройство»
   → the usual dialog.
5. *Ваши устройства*: one card per device — avatar, name, «Андрей · телефон Android», a big dot and
   «На связи · напрямую / в одной сети с вами / через home-server», «Не в сети · был 2 дня назад»,
   and three buttons «Отправить файл · Написать · Файлы». Labels show when the card is wide enough
   (a container query: ≥ 420 px inside the card; the device grids use ≥ 460 px columns, so labels
   are there on a laptop screen); narrower cards and phones (< 480 px) show icons with `aria-label`
   and a tooltip, 44 px tall. Sending and writing
   stay enabled for an offline device (the tooltip says it will wait); «Файлы» is `aria-disabled`
   with the reason in the tooltip and in a toast on click (offline / nothing shared / can't share).
   The card opens the device drawer at `#/home/<id>`. «Подробнее об устройствах» → `#/devices`.

**Navigation.** Sidebar: Главная, Устройства, Файлы, Почта, Чат, Программы, Настройки. Phones keep
five tabs: Главная, Файлы, Почта, Чат, Ещё — `tab-devices` is gone; the device list is on Home, the
full Devices page (map, invitations, technical details) is reached from Home, the «5 из 7» pill and
«Ещё → Устройства», and counts as the Home tab.

**Words.** On the primary screens (Home, device cards, top bar, sidebar, chat header, the map's
node labels, the help sheet) these words are not used: NAT, STUN, UPnP, ретранслятор / relay,
IPv4/IPv6, ID, RTT / «мс» / задержка, LAN, «сервис». Instead: «на связи» / «не в сети»,
«напрямую», «в одной сети с вами», «через home-server» / «через другое устройство», «программы»,
«приглашение — одноразовый код». The «Простой NAT» chip left the top bar.

**Where the technical details went** (all with working copy buttons):

* Settings → Сеть: the NAT card (type, public address, ports, IPv6, STUN, this device's addresses
  with their kinds), relay/LAN/STUN/router switches. Its badge carries `data-testid="nat-chip"`
  and `data-difficulty`.
* «Технические данные» disclosures (`tech-details`, folded by default): on the Devices page in
  «Это устройство» (IPv4, ID, NAT type → Settings, uptime, version, OS, and the overlay address of
  every device); in the device drawer (delay, address, known addresses, IPv4/IPv6, ID, OS, version,
  uptime, clock skew, last error, traffic incl. «через другое устройство»); in Settings → Это
  устройство (ID, IPv4/IPv6, mesh ID, OS, version, uptime).
* Screens for experts carry a small «для опытных» tag: Программы, Settings → Сеть, Сетевой
  интерфейс, Дополнительно (also on their rows in «Ещё»).
* Every main page has a one-line "what is this" subtitle; Mail and Chat show an intro line in their
  list pane. Empty states say what to do next.

**«Как это работает?»** (`help-button` in the top bar, `home-help` on Home → `help-sheet`): every
device has its own key (like an ID card); an invitation is a one-time code; data goes straight
between your devices (sometimes through another of yours), encrypted, no server in the middle; if a
device is off, a letter or file waits. Plus what the dots and the map's lines mean.

## Notes for the Go side

* Serve `index.html` for `/`; hash routing means no other SPA fallback is needed.
  Serve `sw.js` with `Cache-Control: no-cache`. The node rewrites its line
  `const VERSION = "svoi-ui-v1";` to `"svoi-ui-<hash of the embedded UI files>"` (keep that line
  exactly as it is; never bump it by hand), so every new binary installs a fresh worker and cache
  (the mock does the same with a hash of `internal/web/ui/`).
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

Shell: `nav-<section>` (sidebar: `nav-home`, `nav-devices`, …), `tab-<home|files|mail|chat|more>`
(phone tab bar; there is no `tab-devices` any more), `page-<section>` (the `<main>`, `page-home` by
default), `conn-pill`, `help-button` → `help-sheet`, `offline-banner`,
`offers-banner`, `offer-accept`, `offer-decline`, `toast` (`data-level`), `unauthorized`,
`load-failed`; `unauthorized` carries `data-reason` = `expired` (no valid session), `link` (the
page was opened with a used/expired `?t=` code) or `signed-out` (after "Выйти"), with
`auth-retry` ("Проверить снова"). Dialogs: `confirm-ok`, `confirm-cancel`, `confirm-input`, `prompt-input`,
`prompt-ok`, `preview` (+`preview-name`, `preview-prev`, `preview-next`), `folder-picker`
(+`picker-item` `data-name`, `picker-choose`), `device-drawer`, `compose`, `invite-modal`.
Onboarding: `page-onboarding`, `onb-create`, `onb-join`, `onb-mesh-name`, `onb-device-name`,
`onb-owner` (create form only — the join form has no owner), `onb-code`, `onb-submit`,
`onb-progress`, `onb-error`, `removed-notice`.
Device-name preview (onboarding and the rename prompt): `dns-preview` (`data-label` = the label
without `.svoi`).
Home: `home-status` (`data-state` = `ok|partial|offline|alone`), `home-help`, `home-attention` with
`home-offer` (`data-id`; its buttons are `offer-accept` / `offer-decline` like the banner's),
`home-att-mail`, `home-att-chat`, `home-att-invite`, `home-att-restart`, `home-att-offers` (links),
`home-start-checklist` (steps `[data-step=add|browse|send][data-done]`, buttons
`home-start-<add|browse|send>`, `home-start-dismiss`), `home-action-<send|chat|files|add>`,
`device-pick` → `device-pick-item` (`data-peer`), `home-device` (`data-peer`, `data-name`,
`data-online`, `data-state` = `on|relay|wait|off`), `home-devices-all`.
Device cards on Home and Devices: `conn-line` (`data-state`), `device-act-send`, `device-act-chat`,
`device-act-files` (`aria-disabled="true"` + `title` with the reason when unavailable).
Disclosures: `tech-details` («Технические данные», a `<details>`). «Ещё»: `more-devices`.
Devices: `add-device`, `topology`, `topo-node` (`data-id`, `data-path`), `device-card`
(`data-id`, `data-name`, `data-online`, `data-state`), `self-card`, `invites`, `invite-row` (`data-owner`),
`invite-owner` (the "Чьё это устройство?" field), `invite-create`, `invite-qr`, `invite-code`,
`invite-for` («Для: Анна», on the QR step and the done step), `invite-waiting`, `invite-done`,
`device-ping`, `device-ping-result`,
drawer management `manage-alias`, `manage-rename`, `manage-promote` (non-admin peers only),
`manage-admin-note` (admin peers: static "can't be removed" note), `manage-revoke`, and
`revoke-admin-warning` inside the revoke confirmation when the peer is an admin.
Files: `files-tab-<send|browse|shares>`, `device-chip` (`data-id`, `data-name`), `dropzone-input`,
`staged-file`, `send-submit`, `transfer` (`data-id`, `data-state`, `data-dir`) with
`transfer-accept|decline|cancel|retry|remove|open|download`, `browse-device`, `share-card`
(`data-id`, `data-mode`), `file-row` / `file-tile` (`data-name`, `data-dir`), `file-filter`,
`view-list`, `view-grid`, `read-only`, `upload-button`, `upload-input`, `upload-panel`,
`upload-item` (`data-status`), `new-folder`, `share-add`, `share-row` (`data-blocked="true"` when
blocked), `share-blocked` (the «Не раздаётся: в папке ключи svoi» badge), `share-path`,
`share-pick`, `share-name`, `share-save`, `share-error` (the node's message in the dialog),
`manage-device`.
Mail: `mail-compose`, `mail-folder-<inbox|sent|trash>`, `mail-search`, `mail-item` (`data-id`,
`data-unread`), `mail-reader`, `mail-reply`, `mail-trash`, `mail-delivery`, `mail-recipient`
(`data-state`), `mail-attachment` (`data-state`, `data-index`) in the reader, `compose-subject`,
`compose-body`, `compose-files`, `attachment` (`data-status`, staged in compose/chat),
`compose-send`; received attachments in mail and chat: `attachment-fetch` («Загрузить (31 МБ)»,
`needsConsent`) and `attachment-retry` («Повторить», `failed`). Chat: `chat-attachment`
(`data-state`, `data-index`), `thread` (`data-peer`, `data-unread`), `chat-new`, `conversation`,
`bubble` (`data-id`, `data-state`, `data-mine`), `chat-input`, `chat-send`, `chat-files`.
Services: `service-card` (`data-peer`, `data-service`, `data-forwarded`), `service-connect`,
`service-disconnect`, `forward-addr`, `service-publish`, `service-name`, `service-addr`,
`service-save`, `published-service`. Settings: `settings-<section>`, `netcheck`, `nat-chip` (the NAT
card's badge in Settings → Сеть, `data-difficulty`; it used to be in the top bar), `logout`, `leave-mesh`,
`setting-portmap` (switch), `portmap-status` (`data-state` = `self.portmap.state`; absent while the
switch is off), endpoint chips in the NAT card carry `data-kind` (`mapped|local|stun|observed`),
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
    reach the inviting device", `denied` → "rejected", and shows `error.message` below. The node
    currently answers every join failure with `invalid` (`handleMeshJoin`), so a timeout also
    reads "the code didn't work" (+ the node's message); distinct codes for "expired" and
    "unreachable" would allow precise messages.
13. **Thumbnails**: `GET …/thumb` should return `image/jpeg`; the mock returns PNG. Any image
    type works for the UI (404 → falls back to the original for files < 6 MB, then to an icon).
14. **`counters.offers`** is used for badges; the offers banner itself is derived from
    `transfers` (`dir:"in", state:"offered"`), so both must agree.
15. **Auth for SSE**: `EventSource` cannot send headers, so the cookie must authorise
    `GET /api/events` (the bearer header used by test tools is not available to the UI).
16. **Device names** (onboarding, rename): suggested from `self.os`; the form keeps its rule —
    whitespace → `-`, then `^[\p{L}\p{N}][\p{L}\p{N}._-]{0,62}$` — and shows the DNS label the node
    will derive («Адрес в сети: kukhonnyy-noutbuk.svoi») with `dnsLabel()` in `js/util.js`, a port
    of `SanitizeName` (checked by `web-dev/check-util.mjs` against the docs and Go test cases).
    The rename preview also predicts the `-2`, `-3`… suffix from the names of the members it
    knows (`uniqueLabel`, like `UniqueName`); joining can't know the mesh's names, so onboarding
    shows the plain label. Rename answers only `{"ok":true}`: the toast names the predicted
    label, the drawer then shows the real one from `peers`.
17. **Removal by an admin.** The node's notice is `notify {level:"warn", title:<mesh name>,
    text:"removed", link:"#/"}`, then `peers []`. The UI treats `text:"removed"` as a code: no toast
    (the raw word would show), the onboarding screen explains it from `state.removed` instead.
    Reload triggers: any `notify` with `link:"#/"`, a `peers` list that turns empty, a `self` with
    `configured:false`. When the device stops being a member while the UI is open, open
    confirm/prompt dialogs are dismissed and the route is reset to `#/` (onboarding ignores routes).
18. **Demotion** is refused by the node (`unsupported`, HTTP 501 in `internal/api/util.go`; the
    contract's HTTP list doesn't name a status for `unsupported`). The drawer offers promotion
    only; admin peers show a static note, and the revoke confirmation warns that a removed admin
    keeps the mesh key.
19. **Invite owner.** "Add a device" sends `owner` (trimmed, blanks collapsed, ≤ 64; prefilled
    with `self.owner`; empty lets the node use the inviter's own owner). Pending invites show
    «Для: Анна» (falling back to the role text if a node sends no `owner`); the done step shows the
    invite's owner (or the joined device's). The join form sends only `{invite, deviceName}`.
20. **Large attachments.** «Загрузить (31 МБ)» appears for `state:"remote"` with `needsConsent`,
    «Повторить» for any `failed` attachment; both POST `…/fetch`. The UI then shows `fetching`
    at once (the node itself goes remote-wanted → fetching → ready). Mail re-reads the message on
    the `mail` event for it and after an SSE reconnect; chat takes the updated message from the
    `chat` event (it carries the whole ChatMessage — there is no endpoint to re-read one).
21. **Blocked shares.** Rows with `blocked` get the danger badge, a hint and no "browse" button;
    editing one warns until the path changes. `invalid` from `POST/PUT /api/shares` shows the
    node's (English) message in the dialog; when it mentions "keys" / "not exist" the UI adds its
    own words above it — a text match, so a distinct code would be sturdier.
22. **Sign-in.** 401 → «Сессия закончилась» (`data-reason=expired`); when the page was opened
    with a `?t=` code the node did not accept (it redirects only good ones), «Ссылка для входа
    больше не действует» (`link`) — and any `?t=` is dropped from the address bar. «Выйти» (Settings
    → Это устройство) → `POST /api/logout`, then the SSE link is closed and «Вы вышли»
    (`signed-out`) is shown; a 401 from logout itself counts as done. «Проверить снова» re-reads the
    state, which helps after opening a new link in another tab (the cookie is shared).
23. **Leaving the mesh** resets the device's shares, services and forwards (mail and chat stay);
    the confirmation says so. The mock keeps mail and chat across leave/removal too.
24. **Router port mapping.** The switch is shown only when `settings.portMap` is a boolean (a node
    without the feature hides it) and saves like the STUN switch. While it is on and `self.portmap`
    has not arrived yet, the status reads `searching`. `unavailable`/`private` add a faint
    diagnostics line (`gateway` · `error`, as the node sends them). For a managed device (`?d=`)
    there are no `self` events, so its state is read again 2 s after toggling. With
    `nat.difficulty:"open"` and a `mapped` portmap, the NAT card says the router forwards the port.
    The mock's laptop is mapped by default (so it is "open"); its public address is now
    203.0.113.5, the same router as the NAS.

25. **Home without new endpoints.** Everything on Home comes from `/api/state`, `transfers`,
    `counters` and `invites`. Two of the three first steps (opening another device's files, having
    sent a chat message or a letter) leave no trace in the API, so they are remembered in the
    browser only; on a new browser they show as not done until repeated (or the list is hidden).
    "Recently dropped off" (named in the status) is `lastSeen` within 24 h, decided by the UI.

## Rough edges / not done

* The topology uses one ring; beyond ~12 devices labels get crowded.
* No virtualised lists: folders with several thousand entries render but scroll heavier.
* No folder upload (drag a whole directory), no resumable uploads, no mail forwarding, no
  "starred" mail view (star is stored and shown only).
* After an upgrade the first load still starts on the cached (previous) shell, but the new
  worker is installed by that same visit, and when it takes over a page that already had a
  worker the page reloads once by itself (never on the very first install, at most once per
  page). If the takeover comes later than 30 s after loading (an update found mid-session), the
  UI shows a sticky «Вышла новая версия интерфейса — Обновить» toast instead, so a draft is not
  lost. A UI build from before this change cannot do that for itself: the first upgrade away
  from it still needs one manual reload.
* Verified in headless Chromium only (desktop and phone viewports, dark/light, RU/EN);
  Safari/Firefox not run here. Uses `inert`, `color-mix()`, `dvh` — current browsers only.
* The fake QR from the mock is not scannable (the real node renders a real one).
