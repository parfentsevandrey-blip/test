# The Mesh — local HTTP API contract (UI ⇄ node)

The web UI is a static single-page app that talks to **its own local node** over
HTTP on `127.0.0.1`. Everything is JSON unless stated otherwise. Remote devices are
reached *through* the local node (`/api/peers/:id/...`), the browser never talks to
another device directly.

This document is the single source of truth: the Go backend implements exactly this,
and the UI mock server (`web-dev/mock-server.mjs`) must implement exactly this too.

## Conventions

* **Base**: all endpoints live under `/api/`. The UI uses **relative URLs** (`fetch("api/state")`
  from a page served at `/`), uses **hash routing** (`#/devices`, `#/files`, …) and loads
  every asset from the same origin (no CDN, must work offline).
* **Auth**: the node prints `http://127.0.0.1:PORT/?t=<login code>`; `themesh url` / `themesh open` print a
  fresh one. A **login code** is random (192 bit), works **once** and for **10 minutes**. Visiting the link
  exchanges it for a random *session id* (kept by the node as a hash in `ui.sessions`), sets that as an
  `HttpOnly; SameSite=Strict` cookie and redirects to `/` (the code is gone from the address bar). The
  cookie is named `themesh_session_<port>`: browsers share cookies between the ports of one host, and
  without the port in the name signing in to a second node on the same machine (the demo's four
  devices) would sign the first one out. The UI never touches the cookie (it is HttpOnly). A session lasts 14 days and is renewed while it is used, so an active browser stays
  signed in; it survives a restart of the node. The master token (`ui.token`) is for the command line
  only (`Authorization: Bearer …`) and is never put in a URL or a cookie — the browser cannot learn it.
  API calls without a valid session get `401 {"error":{"code":"unauthorized",…}}` — the UI then shows a
  full-screen "Session expired — run `themesh open` and open the new link" page (a refresh does not help;
  the old link is used up).
  The link the node hands to a browser **started on the same machine** (`themesh open`, the browser
  `themesh up` launches) passes through a command line, where other users of the machine can read it; such
  a link is tied to the user who asked for it (Linux: the owner of the client socket, from
  `/proc/net/tcp`). Opening it as another user gives `403` (plain text, the code is *not* used up);
  the UI will not normally see this page. A link printed for copying (`themesh url`) is not tied to anyone.
  State-changing requests (anything but GET/HEAD) must send header `X-Themesh: 1`; only a request that
  carries the real master token is exempt (any other `Authorization` header does not turn the check off).
  The event stream (below) ends when the session does.
  Auth endpoints (all but `logout` are for the command line, the UI never calls them):
  `POST /api/login/code` (Bearer only; optional body `{"local": true}` = for a browser on this machine, tied to
  the asking user) → `{"code","url","expiresIn":600,"singleUse":true,"sessionTtl","bound":false}`;
  `POST /api/logout` ends this browser's session (the UI may offer "Sign out"; `?all=1` — Bearer only —
  ends every session and cancels unused links, it is `themesh signout`);
  `GET /api/handshake?n=<16–128 chars>` (no auth) → `{"proof": hex(HMAC-SHA256(token, "themesh-handshake/v1\0"+n))}` —
  the command line checks it before it ever sends the token to the address recorded in `ui.addr`.
* **IDs**: a device ID is the 52-char lowercase base32 string (`"id"` fields). `self` is
  accepted wherever `:id` of a device is expected and means "this device".
  `short` is the first 8 chars, for display. Other IDs (`transfer.id`, `mail.id`, …)
  are opaque strings.
* **Device names** are DNS labels: the backend turns whatever the user typed into lowercase ASCII
  letters, digits and hyphens (≤ 32 chars; collisions get `-2`, `-3`…). Cyrillic is transliterated
  (а a, б b, в v, г g, д d, е e, ё yo, ж zh, з z, и i, й y, к k, л l, м m, н n, о o, п p, р r, с s, т t,
  у u, ф f, х kh, ц ts, ч ch, ш sh, щ shch, ъ/ь dropped, ы y, э e, ю yu, я ya; і i, ї yi, є ye, ґ g, ў u),
  accents are stripped (é → e), everything else becomes `-`; an empty result becomes `device`.
  So `Кухонный ноутбук` → `kukhonnyy-noutbuk`, reachable as `kukhonnyy-noutbuk.mesh`. Forms that ask for
  a device name (onboarding, rename) should preview the result live (a JS port of that table);
  what counts is `name` in the backend's response. Users who want a Russian display name use the
  per-device **alias** (local nickname, any text).
* **Times** are Unix **seconds** (integers). `0`/`null` means "never/unknown".
* **Sizes** are bytes.
* **Errors**: non-2xx responses carry `{"error":{"code":"…","message":"human readable"}}`.
  Codes: `unauthorized`, `notconfigured`, `denied`, `notfound`, `invalid`, `exists`,
  `offline` (target device not connected), `expired` (only `POST /api/mesh/join`), `busy`, `toolarge`,
  `unsupported`, `internal`. HTTP: 400 invalid, 401 unauthorized, 403 denied, 404 notfound, 409 exists,
  410 expired, 412 notconfigured, 413 toolarge, 502 offline, 503 busy, 500 internal.
  `message` is already localised-neutral English; the UI maps `code` to Russian/English text.
* **Streaming bodies** (upload/download) are raw bytes, not multipart.

## Live updates — Server-Sent Events

`GET /api/events` → `text/event-stream`. Each message is `event: <type>` + `data: <json>`.
The UI keeps one `EventSource`; on (re)connect it first calls `GET /api/state`. After a sign-out (or when
the session ends) the node closes the stream; the reconnect then gets `401`.
A browser tab that is hidden may drop the connection; that is fine.

| event       | data                                                                                   |
|-------------|----------------------------------------------------------------------------------------|
| `hello`     | `{"serverTime": 1760000000}` sent right after connecting                               |
| `self`      | a full **Self** object (endpoints / NAT / name changed)                                |
| `peers`     | full array of **Peer** (replace the list)                                              |
| `transfer`  | one **Transfer** (upsert by `id`)                                                      |
| `transfer.removed` | `{"id": "…"}`                                                                   |
| `mail`      | `{"id","folder","unread":bool}` a message arrived or changed → refresh list/counters  |
| `chat`      | one **ChatMessage** plus `"peer": "<id>"` (thread key) — new or state change          |
| `counters`  | `{"mail": 3, "chat": 2, "offers": 1}` unread/pending badges                            |
| `notify`    | `{"level":"info\|success\|warn\|error","title":"…","text":"…","link":"#/files"?}` toast |
| `invites`   | full array of **Invite** (a pending invite was used/expired)                           |
| `shares`    | full array of local **Share**                                                          |
| `forwards`  | full array of **Forward**                                                              |
| `nearby`    | the whole **Nearby** picture (devices around, this device's own request, requests to answer) |

## Data types

### Self
```jsonc
{
  "id": "…52 chars…", "short": "abcd1234", "name": "laptop", "owner": "Andrey",
  "ip4": "100.64.12.34", "ip6": "fd12:3456::1", "admin": true,
  "meshId": "k3j4h5g6f7d8", "meshName": "Home",
  "udpPort": 41710,
  "endpoints": [ {"addr": "203.0.113.5:41710", "kind": "mapped"},
                 {"addr": "192.168.1.5:41710", "kind": "local"},
                 {"addr": "203.0.113.5:41710", "kind": "observed"} ],   // mapped | local | stun | observed
  "nat": { "mappingVaries": false, "public": ["203.0.113.5:41710"], "hasIPv6": false,
           "stun": true,
           "difficulty": "easy" },        // open | easy | hard | unknown
  "version": "0.1.0", "os": "linux", "arch": "amd64", "started": 1760000000,
  "configured": true,
  "relay": true,                           // this device agrees to relay for others
  "relayed": { "packets": 120, "bytes": 150000 },
  "lan": {                                 // how looking for devices on the home network goes (absent while Settings.lan is off or the node has not started it yet)
    "enabled": true,                       // the node is listening for and sending beacons
    "networks": ["192.168.1.23/24 (wlan0)"],   // where this device announces itself
    "problem": "",                         // "" = fine | "no-network" (no Wi-Fi or cable: nothing to look at, not an error) |
                                           // "blocked" (the system refuses to send there: on a Mac the "Local Network" permission is off) |
                                           // "failed" (sending fails for another reason, see `detail`)
    "detail": ""                           // the system's own error text, for `blocked` and `failed`
  },
  "portmap": {                             // present only while the router port mapping is switched on
    "state": "mapped",                     // searching | mapped | private | unavailable
    "protocol": "upnp",                    // upnp | natpmp, once mapped
    "external": "203.0.113.5:41710",       // the public address the router forwards to this device
    "gateway": "192.168.1.1", "error": ""  // the router that answered; why not (diagnostics)
  }
}
```
`defaultName` is present only while `configured` is `false` (the device has no mesh yet, `name` is
empty): the name the device will give itself when `POST /api/mesh/create` or `/api/mesh/join` come
without a `deviceName` — the `--name` the program was started with (the phone app passes the phone's own
name), else the host name — already reduced to a valid device name (see **Device names**). The first-run
forms put it in the name field as the suggestion and preview it, so what they show is what the device
really becomes (on a join the inviting mesh may still add `-2`, `-3`… if the name is taken). When it is
missing (an older node, a mock), fall back to a guess from `os`.

`lan`: what the UI says about it: `ok` — nothing (or the list of networks in Settings → Network); `no-network` — "no Wi-Fi: there is nobody
to look for", **not** a problem to nag about; `blocked` / `failed` — a notice on Home («Требует внимания») and in Settings, with the way out (on a
Mac: System Settings → Privacy & Security → Local Network → The Mesh), because without it devices on one Wi-Fi do not find each other by themselves.
On a Mac the desktop shell checks the permission itself and shows its own dialog once; this field is how the interface, which runs in the same window,
learns about it. `lan` is missing from a node that has not tried yet (a mock, an older node): show nothing.

`portmap`: the device asks the home router (UPnP IGD, NAT-PMP) to forward its UDP port, so other
devices can reach it directly without anybody opening a port by hand. `mapped` — done, `external` is
offered to the others first (endpoint kind `mapped`, and `nat.difficulty` becomes `open`);
`searching` — looking for a router; `unavailable` — no router answered (UPnP/NAT-PMP off or not
supported: nothing breaks, hole punching and relaying still work; the node looks again later);
`private` — the router's own address is not public (double NAT, carrier-grade NAT): a mapping would
not help, so none is made. The mapping is removed when the node stops.
`difficulty`: `open` = directly reachable, `easy` = hole punching works, `hard` = symmetric NAT
(traffic will use a relay), `unknown` = not enough info yet.

### Peer
```jsonc
{
  "id": "…", "short": "…", "name": "nas",           // name = alias if set, else deviceName
  "deviceName": "nas", "alias": "", "owner": "Andrey",
  "ip4": "100.64.0.7", "ip6": "fd…", "admin": false,
  "online": true,
  "path": "lan",            // lan | direct | relay | none   (how packets currently flow)
  "relayVia": "home-server",// device name, only when path == "relay"
  "rttMs": 3.2,             // 0 when unknown
  "addr": "192.168.1.9:41710",   // current direct address, if any
  "lastSeen": 1760000000, "connectedAt": 1760000000,
  "os": "linux", "arch": "arm64", "version": "0.1.0",
  "caps": ["files","mail","chat","tunnel","relay"],
  "uptime": 86400,          // seconds the remote node has been running; 0 unknown
  "shares": 2,              // number of shares it exposes to me
  "services": [ {"name":"ssh","port":22,"description":""} ],   // services it exposes to me
  "txBytes": 1234, "rxBytes": 5678, "txRelay": 0, "rxRelay": 0,
  "lastError": "",          // last connection error, shown as a hint when offline
  "endpoints": ["192.168.1.9:41710"],
  "clockSkewMs": 12
}
```
The list excludes this device. Sort in the UI (online first, then by name).

### Transfer
```jsonc
{ "id": "t_8f3a…", "dir": "in",                     // in | out
  "peer": "<device id>", "peerName": "phone",
  "name": "photo.jpg", "size": 3145728, "done": 1048576, "mime": "image/jpeg",
  "state": "active",   // offered | queued | active | done | failed | declined | canceled
  "speed": 1234567,    // bytes/s, only while active
  "error": "", "created": 1760000000, "updated": 1760000100, "finished": null,
  "path": "/home/me/Downloads/The Mesh/photo.jpg"       // incoming + done only
}
```
* `in` + `offered`: someone wants to send you a file → show **Accept / Decline** (unless
  auto-accepted). `in` + `queued`: accepted, waiting for the sender to be online.
* `out` + `queued`: waiting for the recipient to be online; `out` + `offered`: delivered,
  waiting for them to accept; `out` + `active`: being downloaded by them.

### Mail (summary and full)
```jsonc
{ "id": "m_…", "kind": "mail", "folder": "inbox",     // inbox | sent | trash
  "from": {"id": "…", "name": "phone"},
  "to": [ {"id": "…", "name": "nas", "state": "delivered", "at": 1760000000} ],
                                                       // state: queued | sent | delivered | failed
  "subject": "Backup report", "snippet": "first ~140 chars of the body",
  "ts": 1760000000, "unread": true, "attachments": 2, "thread": "…", "starred": false,
  "ext": ExtMail? }                                    // only for a letter from / to the Internet, see below
```
Full message adds:
```jsonc
{ …summary…, "body": "plain text, may contain newlines and links",
  "inReplyTo": null,
  "attachments": [ {"name":"report.pdf","size":123456,"mime":"application/pdf",
                    "sha256":"…hex…", "state":"ready", "got": 123456,
                    "needsConsent": true} ] }
                    // state: ready | fetching | remote (not downloaded yet) | failed
                    // needsConsent (only when true): a `remote` attachment over 25 MB is not
                    // downloaded on its own — show "Download (31 MB)" which POSTs …/fetch
```
Bodies are **plain text**: the UI must render them escaped (auto-link URLs, keep line breaks);
never inject as HTML.

#### Letters from and to the Internet (`ext`)
A device that is set up as a *mail gateway* (see "Own address" below) turns the letters of its domain into letters of the mesh
and the letters of the mesh into mail on the Internet. Such a letter carries `ext`:
```jsonc
// from the Internet (the gateway is "from" as far as the mesh goes: from.id is the gateway, from.name the sender as a person)
{ "dir": "in", "from": {"name": "GitHub", "addr": "noreply@github.com"},   // name only when the letter has one
  "to": [{"name":"Andrey","addr":"andrey@example.org"}], "cc": [], "replyTo": [],   // (empty lists are left out)
  "mailbox": "andrey@example.org",                     // the mailbox of ours it came to
  "verdict": "verified",                               // verified | unverified | suspicious — what the checks of the gateway say of the sender
  "spf": "pass", "dkim": "github.com", "dmarc": "pass", // SPF/DMARC: pass|fail|softfail|neutral|none|temperror|permerror;
                                                       // dkim: the domains whose signature held ("" when there was none)
  "via": {"id": "…", "name": "home-server"},           // the gateway
  "hasHtml": true,                                     // there is formatted text: GET /api/mail/:id/html
  "remoteImages": 2,                                   // pictures on the Internet the letter asks for (never loaded)
  "truncated": false, "messageId": "<…@mail.example>" }
// to the Internet
{ "dir": "out", "from": {"name":"Andrey","addr":"andrey@example.org"}, "to": [{"addr":"friend@gmail.com"}], "cc": [],
  "mailbox": "andrey@example.org", "via": {"id":"…","name":"home-server"}, "messageId": "<…@example.org>",
  "recipients": [ {"addr":"friend@gmail.com","kind":"to","state":"delivered","code":250,"text":"2.0.0 OK","at":1760000000} ] }
                    // state: queued | deferred (tried, will be tried again — text says why) | delivered | failed (code/text: the words
                    // of the other server, or of the gateway: "cancelled", "the domain … does not exist or takes no mail"); at is null until final
```
`to` of the summary lists only the **devices** a letter goes to: the gateway that only passes a letter on is not in it, so a letter to the
Internet alone has an empty `to`. The body of such a letter is always the plain-text version; the UI shows the formatted one in a frame
(below) when `ext.hasHtml`. A letter from the Internet is **somebody else's text**: the verdict is a hint, not a guarantee.

### Chat
```jsonc
// thread list item
{ "peer": {"id": "…", "name": "phone", "online": true},
  "last": {"text": "see you", "ts": 1760000000, "from": "<id>", "state": "delivered"},
  "unread": 2 }
// message
{ "id": "c_…", "from": "<id>", "to": "<id>", "mine": false, "text": "hello", "ts": 1760000000,
  "state": "delivered",             // queued | sent | delivered | failed
  "attachments": [ {"name":"x.png","size":1,"mime":"image/png","sha256":"…","state":"ready"} ] }
```

### Share (files this device exposes) and remote share
```jsonc
{ "id": "sh_…", "name": "Photos", "path": "/mnt/photos", "mode": "ro",   // ro | rw
  "allow": ["*"],                  // "*" = every member, or a list of device ids
  "exists": true,                  // false if the folder is missing on disk
  "blocked": true }                // only when true: the folder holds themesh's own keys (it is the data
                                   // directory, a parent of it, or inside it) — it is NOT served; show a warning
                                   // and let the user pick a sub-folder
// what a remote device exposes to you (GET /api/peers/:id/shares):
{ "id": "sh_…", "name": "Photos", "mode": "ro" }
```

### Service, remote service, forward (TCP port sharing)
```jsonc
{ "id": "sv_…", "name": "ssh", "addr": "127.0.0.1:22", "description": "SSH", "allow": ["*"] }
{ "name": "ssh", "port": 22, "description": "SSH" }                    // remote service
{ "id": "fw_…", "peer": "<id>", "peerName": "nas", "service": "ssh",
  "listen": "127.0.0.1:2222", "state": "listening",                    // listening | error | stopped
  "error": "", "conns": 0 }
```

### Invite
```jsonc
{ "id": "…", "code": "MESH1-AEAWVQFQ-…", "admin": false,
  "owner": "Anna",                  // whose device this invitation is for; the *inviter* decides it
  "created": 1760000000, "expires": 1760001800,
  "qrSvg": "<svg …>…</svg>",        // server-rendered QR code of `code`; safe to inject
  "endpoints": ["192.168.1.23:41710", "203.0.113.5:41710"] }   // the addresses the code carries (technical details)
```
The QR carries the code **without the dashes** that group `code` for reading (`MESH1-` and then the base32 body in one piece;
parsing ignores dashes either way). That is a QR one size smaller, so a phone camera reads it off a screen more easily. An invitation
holds at most six addresses of the inviting device, chosen so that every way to reach it is represented (the router-forwarded one,
the home-network one, the public IPv4, a global IPv6): a newcomer on the same Wi-Fi can only use the home-network address, and with
many IPv6 addresses it used to be cut off.

### Devices nearby

A device that has The Mesh but is **not in any mesh** lists the devices around that can add it — administrators of a mesh that are on the
same network and have not switched "Show this device nearby" off — and a tap asks one of them. No code is typed and no QR is scanned: **both
screens show the same six digits** (derived from the keys of the very connection between them, so somebody in the middle shows two different
numbers), the person at the new device says "they match", and the person at the device that adds says "add". Only then does the new device get a
**regular** (never administrator) membership, the same one an invitation gives.

```jsonc
// Nearby — GET /api/nearby, state.nearby, the `nearby` event (always the whole picture)
{ "visible": true,                     // this device, if it is an administrator, tells others that it can add them (Settings.nearby)
  "devices": [                         // only while this device is not in a mesh: who can add it (a device is listed while it is heard, ~16 s)
    { "id": "…", "name": "macbook-andrey", "meshName": "Дом", "os": "darwin", "seen": 1760000000 } ],
  "join": {                            // this device's own request to be added
    "state": "idle",                   // idle | connecting | waiting | confirmed | joined | denied | failed | canceled
    "peer": { /* the device asked, as in devices[] */ },
    "code": "482913",                  // waiting and confirmed: the six digits to compare with the other screen
    "reason": "",                      // denied / failed: offline | denied | expired | invalid | failed
    "error": "" },                     // the technical text for `reason`, if any
  "requests": [                        // only on an administrator: devices that ask to be added and wait for an answer (≤ 3, each valid 2 minutes)
    { "id": "…", "name": "pixel-8", "os": "android", "code": "482913",
      "confirmed": false,              // the person at the new device has already said "they match"
      "created": 1760000000, "expires": 1760000120 } ] }
```
States of `join`: `connecting` (setting up the secure connection) → `waiting` (**show `code`, ask "do they match?"**; the person can say yes, or cancel) → `confirmed`
(waiting for the person at the other device) → `joined` (the device is a member now: reload `GET /api/state`; `configured` is `true`), or `denied`
(`reason`: `denied` — the other person said no; `expired` — nobody answered in 2 minutes) / `failed` (`offline` — the device did not answer: show the advice that
the invitation flow shows, plus "same Wi-Fi, no client isolation on the router"; `invalid` / `failed` — something else, `error` has the text).
`canceled` is what a request the person gave up looks like: show the list again. After an end (`joined`, `denied`, `failed`, `canceled`) `POST /api/nearby/cancel`
forgets it (state `idle`); leaving the mesh forgets it too.

The interface (`js/views/nearby.js`): on the start screen a card **«Рядом с вами»** lists `devices` with a «Подключиться» button each, or says that it
is looking (and, when `self.lan.problem` is `blocked`/`failed`, why it cannot); a request takes the place of the start choices while it lasts;
on an administrator a **dialog opens by itself** for each new entry of `requests` (device name, OS, the digits, whether the other person has confirmed,
"Whose device is it?", «Добавить» / «Отклонить»), and an entry whose dialog was closed stays as a row on Home until it expires. The desktop shell and the
phone app show a system notification for each new request when the window is not in front.

Events win over answers. The node answers `POST /api/nearby/requests/{id}` at once (the request is still in the picture it returns) and forgets the request
a moment later, which comes as a `nearby` event; the answer can reach the page after that event. The interface therefore counts the `nearby` events and takes
the picture from an answer (and from `GET /api/state`) only if none came while it was on its way — otherwise the request that was just allowed would stay on
Home under "Needs your attention" (`web-e2e/tests/nearby.mjs` checks this with two real programs).

### Scanning an invitation (phone app)
The Android app lets a person scan the QR code that another device shows, instead of typing the code. The window is a WebView around
this same interface, so the app and the interface meet in two small places:

* **`window.themeshApp`** — an object the app adds to the page (only the app does; in a browser it is `undefined`).
  `canScan()` → `true` when the phone has a camera; `scanInvite()` opens the camera screen. The join form shows a **«Scan QR code»**
  button (`data-testid="onb-scan"`) only when `window.themeshApp && window.themeshApp.canScan()`.
* **`themesh-scan`** — a DOM event the app dispatches on `window` when the camera screen closes:
  `new CustomEvent("themesh-scan", { detail })` with `detail` one of `{"text": "MESH1-…"}` (what the camera read: an invitation in
  capitals, no dashes or blanks), `{"error": "cancelled"}` (the person backed out: say nothing), `{"error": "denied"}` (no permission to
  use the camera) or `{"error": "unavailable"}` (no camera, or it would not open). The form takes the invitation out of `detail.text`
  (it also finds one inside a longer text, such as a link), fills the code field and joins at once, with the device name it offers;
  a text that holds no invitation gets a short notice and nothing else.

The camera is used only to read the code: frames are not stored or sent anywhere.

### Settings
```jsonc
{ "downloadDir": "/home/me/Downloads/The Mesh",
  "autoAccept": "own",             // own = from devices of the same owner | all | ask
  "autoAcceptMaxMB": 0,            // 0 = no limit
  "relay": true,                   // act as a relay for other members
  "stunEnabled": true, "stunServers": ["stun.l.google.com:19302"],
  "udpPort": 41710, "lan": true,
  "nearby": true,                  // an administrator device tells the devices around that it can add them (see Devices nearby); applies at once
  "portMap": true,                 // ask the home router to forward our UDP port (UPnP / NAT-PMP); see Self.portmap
  "socks": {"enabled": false, "listen": "127.0.0.1:1080"},
  "tun": { "enabled": false,       // create a virtual network interface (Linux, needs root / CAP_NET_ADMIN)
           "manageHosts": true,    // also add `<name>.mesh` lines to /etc/hosts
           "state": "off",         // off | running | error   (live status, read-only)
           "name": "themesh0",        // interface name while running
           "error": "",            // why it could not start, e.g. "permission denied — run as root"
           "supported": true,      // false on platforms without TUN support → hide the section
           "txPackets": 0, "rxPackets": 0, "dropped": 0 },
  "restartRequired": false }       // true when a change needs a restart (udpPort, lan…)
```
`PUT /api/settings` accepts `{"tun": {"enabled": true}}` (either field, either order). With the
interface up, every device is reachable at its overlay address (`ip4`/`ip6`) and as
`<name>.mesh` by *any* program (ssh, a browser, `ping`), not only through the web UI.
Language and theme are **client-side only** (`localStorage`), not part of Settings.

## Endpoints

### State & lifecycle
| method & path | body → response |
|---|---|
| `GET /api/state` | → `{ "version", "configured", "self": Self, "peers": [Peer], "transfers": [Transfer] (active + last 50), "counters": {"mail","chat","offers"}, "invites": [Invite], "settings": Settings, "nearby": Nearby, "removed"?: {"meshName": "Дом", "at": 1760000000} }`. Works when `configured:false` (then `self` has only id/short/version/os/arch/configured and `defaultName`, `peers: []`). `removed` is present only while the device is outside any mesh **because an administrator removed it** from one: the onboarding screen should say so ("this device was removed from the network «Дом» by an administrator — ask for a new invitation"). The device already has a fresh identity then, so a new invitation just works. A `notify` event with `level: "warn"` and `link: "#/"` is sent at the moment it happens, followed by a `peers` event with an empty list; the UI should reload `GET /api/state`. |
| `GET /api/events` | SSE, see above |
| `POST /api/mesh/create` | `{"meshName","deviceName","owner"}` → `{"ok":true}` (then reload state) |
| `POST /api/mesh/join` | `{"invite","deviceName"}` → `{"ok":true}`; may take up to ~25 s; errors are human readable in `error.message`, and the **code says why**, because the advice differs: `invalid` — the text is no invitation (malformed, copied only partly); `expired` — past its lifetime by this device's clock (also say: check the date and time here); `offline` — the device that made the invitation did not answer (it is off, on another network, or the invitation is used up or cancelled: from outside those look the same; the message lists the addresses that were tried); `denied` — the inviter answered and refused. There is no `owner` here: whose device this is was set by the inviting device in the invitation (an `owner` sent anyway is ignored), so a new device cannot claim someone else's name to get auto-accepted files. |
| `POST /api/mesh/leave` | `{}` → `{"ok":true}` (forgets the mesh, keeps the device key) |
| `POST /api/netcheck` | `{}` → `{ "self": Self }` re-runs STUN + re-probes peers (takes ≤ 3 s) |
| `GET /api/diag/logs?limit=200` | → `{"lines":[{"ts":1760000000,"level":"info","msg":"…"}]}` |
| `GET /api/diag/ping?peer=:id` | → `{"ms": 4.1}` application-level round trip (502 offline) |

### Devices & invites
| method & path | body → response |
|---|---|
| `GET /api/invites` | → `[Invite]` |
| `POST /api/invites` | `{"admin":false,"ttlMinutes":30,"owner":"Anna"}` → `Invite` (admin only; `denied` otherwise). `owner` is whose device the invitation is for (≤ 64 characters; empty = the inviter's own owner). The "Add a device" dialog asks for it ("Whose device is it?", prefilled with this device's owner). |
| `DELETE /api/invites/:id` | → `{"ok":true}` |
| `POST /api/peers/:id/alias` | `{"alias":"Dad's phone"}` → `{"ok":true}` (local nickname; empty clears) |
| `POST /api/peers/:id/revoke` | `{}` → `{"ok":true}` admin only; permanently removes the device |
| `POST /api/peers/:id/rename` | `{"name":"new-name"}` → `{"ok":true}` admin only (re-issues its certificate) |
| `POST /api/peers/:id/admin` | `{"admin":true}` → `{"ok":true}` admin only. **Promotion only**: it hands over the mesh key (the UI must warn). Demotion (`{"admin":false}`) is refused with `unsupported`: the key cannot be taken back, so an administrator stays one — and **removing an administrator does not take its power away** (it still holds the mesh key and can enrol devices); the revoke dialog must say so when `peer.admin` is true. |

### Devices nearby
| method & path | body → response |
|---|---|
| `GET /api/nearby` | → **Nearby** |
| `POST /api/nearby/connect` | `{"id":"<devices[].id>","deviceName":"phone"}` → **Nearby** (`join.state` is `connecting`; the rest comes as `nearby` events). `notfound` — the device is no longer listed (it left, or switched off); `invalid` — this device is already in a mesh, or a request is already running. `deviceName` is what this device is called in that mesh (empty: its usual name; see **Device names**). |
| `POST /api/nearby/confirm` | `{}` → **Nearby**. The person says the six digits match. `invalid` unless `join.state` is `waiting`. |
| `POST /api/nearby/cancel` | `{}` → **Nearby**. Gives up a running request; after an end, forgets it. |
| `POST /api/nearby/requests/:id` | `{"approve":true,"owner":"Anna"}` → **Nearby** (administrator only, `denied` otherwise). `owner` is whose device the new one is (≤ 64 characters; empty: this device's own owner). Approving does not add the device yet: it is added when its person has confirmed the digits too (either order). `notfound` — the request is gone (the device left or 2 minutes passed). |
| `PUT /api/settings` | `{"nearby":false}` — stop telling the devices around that this one can add them. |

Command line: `themesh nearby` lists the devices around and the requests waiting; `themesh nearby join NAME` asks a device (and shows the digits); `themesh nearby allow ID [--owner NAME]`
and `themesh nearby deny ID` answer a request — a server with no screen can be added, and can add, the same way.

### Files — browsing shares (works for `self` too)
| method & path | → response |
|---|---|
| `GET /api/peers/:id/shares` | `[RemoteShare]` |
| `GET /api/peers/:id/fs?share=:shareId&path=/a/b` | `{ "path":"/a/b", "canWrite":false, "entries":[{"name":"x.jpg","isDir":false,"size":123,"mtime":1760000000,"mime":"image/jpeg"}] }` dirs first is NOT guaranteed — the UI sorts |
| `GET /api/peers/:id/file?share=:shareId&path=/a/x.jpg[&dl=1]` | raw bytes, `Content-Type`, `Content-Length`, **supports `Range`** (video/audio seeking). `dl=1` → `Content-Disposition: attachment` |
| `PUT /api/peers/:id/file?share=:shareId&path=/a/x.jpg` | raw body = file bytes → `{"ok":true,"size":123}`; `403` if the share is read-only; add `&overwrite=1` to replace; otherwise `409 exists` |
| `POST /api/peers/:id/fs` | `{"op":"mkdir","share","path"}` \| `{"op":"rename","share","path","to"}` \| `{"op":"delete","share","path"}` → `{"ok":true}` |
| `GET /api/peers/:id/thumb?share=…&path=…&w=256` | JPEG thumbnail for images (404 if not an image) — optional, the UI falls back to the original or an icon |

### Files — sending to a device (AirDrop-style)
| method & path | body → response |
|---|---|
| `GET /api/transfers` | `[Transfer]` (all, newest first) |
| `POST /api/transfers?to=:id[,:id2]&name=photo.jpg&mime=image/jpeg` | **raw body = file bytes** (use XHR for upload progress) → `{"transfers":[Transfer]}` (one per recipient) |
| `POST /api/transfers/:id/accept` · `/decline` · `/cancel` · `/retry` | `{}` → `Transfer` |
| `DELETE /api/transfers/:id` | removes a finished/failed entry from history → `{"ok":true}` |
| `GET /api/transfers/:id/file` | for `in` + `done`: download the received file (Range supported) |

### Files — folders this device shares
| method & path | body → response |
|---|---|
| `GET /api/shares` | `[Share]` |
| `POST /api/shares` | `{"name","path","mode":"ro","allow":["*"]}` → `Share` (`invalid` if the folder does not exist, or if it would expose The Mesh's own keys: the data directory, one of its parents such as the home folder, or something inside it — the message says so) |
| `PUT /api/shares/:id` | same fields → `Share` |
| `DELETE /api/shares/:id` | → `{"ok":true}` |
| `GET /api/local/fs?path=/home/me` | folder picker for *this* device: `{"path":"/home/me","parent":"/home","home":"/home/me","sep":"/","roots":["/"],"entries":[{"name":"Documents","isDir":true}]}` — directories only; `path` omitted → home |

### Mail
| method & path | body → response |
|---|---|
| `GET /api/mail?folder=inbox&q=text&limit=50&before=1760000000` | `{"items":[MailSummary],"total":120,"unread":3}` newest first; `before` = `ts` of the last item you have (paging) |
| `GET /api/mail/:id` | full Mail (does **not** mark as read) |
| `POST /api/mail` | `{"to":["<id>",…],"subject":"…","body":"…","attachments":["<blob sha256>",…],"inReplyTo":"m_…"?,"emailTo":["friend@gmail.com","Name <a@b.c>"]?,"emailCc":[…]?,"from":"andrey@example.org"?}` → `{"id":"m_…"}`; delivery is asynchronous (store-and-forward). `to` may be empty when `emailTo` is not. `from` is the mailbox the letter goes out from (omitted: the only one this device has; `400` when it has none or names one it does not have; `404` when no gateway of the mesh is known) — the gateway must be one this device trusts (an administrator, or a device of the same owner). Attachments of a letter to the Internet: up to 20 MiB in all. A reply carries `inReplyTo` and keeps the thread (`In-Reply-To`/`References`) |
| `GET /api/mail/gateways` | `{"gateways":[{"id","name","self":bool,"online":bool,"domain":"example.org","host":"mail.example.org","mailboxes":["andrey@example.org"],"ready":bool}]}` — the gateways this device may write through, with the mailboxes it has at each (what the composer offers as "from"); `[]` when there is none |
| `GET /api/mail/:id/html` | for a letter from the Internet that `hasHtml`: **a page**, not JSON — the cleaned formatted text, for an `<iframe sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox" src=…>` (no `allow-scripts`). It is served under a policy of its own (`default-src 'none'; img-src data:; style-src 'unsafe-inline'; form-action 'none'; …`, `Referrer-Policy: no-referrer`, `no-store`): no script, nothing from the network; the pictures of the Internet are taken out, the pictures that belong to the letter (`cid:`) are put in as `data:` (when this device has them). `404` for a letter without formatted text |
| `POST /api/mail/:id/flags` | `{"unread":false,"starred":true,"folder":"trash"}` (any subset) → `{"ok":true}` |
| `DELETE /api/mail/:id` | in trash: delete forever; elsewhere: move to trash → `{"ok":true}` |
| `POST /api/blobs?name=report.pdf&mime=application/pdf` | raw body → `{"id":"<sha256>","name","size","mime"}` (attachment staging) |
| `GET /api/mail/:id/attachments/:index` | download attachment (`?dl=1` for attachment disposition); `404`/`409` while still `remote`/`fetching` |
| `POST /api/mail/:id/attachments/:index/fetch` | `{}` → `{"ok":true}` — the user agrees to download an attachment with `needsConsent` (or retries a `failed` one). Show it as `fetching` at once; there is no event when it starts, a `mail` event comes when it ends (`ready`, or back to `remote`/`failed`) — re-read the message then |

### Chat
| method & path | body → response |
|---|---|
| `GET /api/chat/threads` | `[ChatThread]` newest first |
| `GET /api/chat/:peerId?before=1760000000&limit=50` | `{"messages":[ChatMessage]}` oldest → newest within the page |
| `POST /api/chat/:peerId` | `{"text":"hi","attachments":["<blob sha256>"]}` → `ChatMessage` |
| `POST /api/chat/:peerId/read` | `{}` → `{"ok":true}` |
| `GET /api/chat/messages/:id/attachments/:index` | download |
| `POST /api/chat/messages/:id/attachments/:index/fetch` | same as for mail |

### Services (TCP port sharing)
| method & path | body → response |
|---|---|
| `GET /api/services` | `[Service]` this device publishes |
| `POST /api/services` | `{"name","addr":"127.0.0.1:22","description","allow":["*"]}` → `Service` |
| `PUT /api/services/:id` · `DELETE /api/services/:id` | |
| `GET /api/peers/:id/services` | `[RemoteService]` |
| `GET /api/forwards` | `[Forward]` |
| `POST /api/forwards` | `{"peer":"<id>","service":"ssh","listen":"127.0.0.1:0"}` → `Forward` (port 0 = pick a free one; `listen` in the response has the real port) |
| `DELETE /api/forwards/:id` | |

### Own address — the mail gateway (Internet mail)
The device that has the public address (port 25) and the domain is the *gateway*; it is off until its owner sets it up. An administrator can do it
for another device with the `/api/d/:peerId` prefix (below).

| method & path | body → response |
|---|---|
| `GET /api/mailgw` | MailGateway: `{"enabled","domain","host","listen","dkimSelector","publicIPv4","mailboxes":[{"name":"andrey","address":"andrey@example.org","devices":["<id>",…]}],"relay":null \| {"host","port","username"?,"passwordSet":bool,"mode":"starttls\|tls\|plain","spfInclude"?},"status":{"enabled","running":bool (the sender works),"listening":bool,"listenAddr"?,"listenError"?,"listenErrorKind"?:"permission\|inuse\|other","relay":bool,"queue":3,"selector","detectedIPv4"?,"publicIPv4"?},"defaults":{"host":"mail.<domain>","listen":":25"}}` |
| `PUT /api/mailgw` | the same fields without `status`/`defaults`/`address`/`passwordSet` (`relay.password` only goes in, never comes out; a relay with the same host and login and **no** password keeps the one it has) → MailGateway. `400 invalid` with the reason in `message` (English): no domain / no mailbox while `enabled`, a name that is not a domain, a mailbox with no devices or with a device that is not in the mesh, a port that is not a number … A running gateway is restarted with the new setup; a server that cannot take its port does not stop the sender (`status.listenError`, `listenErrorKind` says why: `permission` — port 25 needs rights) |
| `GET /api/mailgw/dns` | `{"domain","host","publicIPv4"?,"relay":bool,"report":{"ready":bool,"records":[{"id":"mx\|a\|aaaa\|spf\|dkim\|dmarc\|ptr","type":"MX\|A\|AAAA\|TXT\|PTR","name":"…","value":"…","required":bool,"state":"ok\|missing\|wrong\|unknown","found"?:["…"],"detail"?:"other-host\|address-unknown\|other-address\|two-records\|ip-not-allowed:softfail\|other-key\|other-name\|dns-error: …"}]}}` — the records the domain needs, each with what the real DNS says of it now (`ready`: every required record is right). `400` while no domain is saved |
| `GET /api/mailgw/queue` | `{"items":[{"id","from","origin":"m_…","size","created","rcpts":[{"addr","state":"queued\|deferred","code"?,"text"?,"attempts"?,"next"?}]}]}` — the letters that are still on their way |
| `POST /api/mailgw/queue/retry` | `{}` → `{"ok":true}` — try the letters that wait now |
| `POST /api/mailgw/queue/:id/cancel` | `{}` → `{"ok":true}` — give up: the recipients that still wait fail with "cancelled" (`404` if it is not in the queue) |

### Settings
`GET /api/settings` → Settings · `PUT /api/settings` (any subset of the fields) → Settings.

### Managing another device (admins only)
Any of the *configuration* endpoints can be addressed to another device by prefixing the
path with `/api/d/:peerId`:

`/api/d/:peerId/shares[/:id]`, `/api/d/:peerId/services[/:id]`, `/api/d/:peerId/settings`,
`/api/d/:peerId/local/fs`, `/api/d/:peerId/diag/logs`, `/api/d/:peerId/state`, `/api/d/:peerId/mailgw[/…]`.

The node forwards the request to that device, which executes it as if it were local.
`403 denied` unless **this** device is an admin; `502 offline` if the device is not
connected. The UI uses it for "Manage device ▾" selectors on the Shares, Services and
Settings screens (headless NAS / home server configuration from the laptop's UI).
