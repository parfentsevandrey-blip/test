# svoi — local HTTP API contract (UI ⇄ node)

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
* **Auth**: the node prints `http://127.0.0.1:PORT/?t=<login code>`; `svoi url` / `svoi open` print a
  fresh one. A **login code** is random (192 bit), works **once** and for **10 minutes**. Visiting the link
  exchanges it for a random *session id* (kept by the node as a hash in `ui.sessions`), sets that as an
  `HttpOnly; SameSite=Strict` cookie `svoi_session` and redirects to `/` (the code is gone from the
  address bar). A session lasts 14 days and is renewed while it is used, so an active browser stays
  signed in; it survives a restart of the node. The master token (`ui.token`) is for the command line
  only (`Authorization: Bearer …`) and is never put in a URL or a cookie — the browser cannot learn it.
  API calls without a valid session get `401 {"error":{"code":"unauthorized",…}}` — the UI then shows a
  full-screen "Session expired — run `svoi open` and open the new link" page (a refresh does not help;
  the old link is used up).
  State-changing requests (anything but GET/HEAD) must send header `X-Svoi: 1`.
  Auth endpoints (all but `logout` are for the command line, the UI never calls them):
  `POST /api/login/code` (Bearer only) → `{"code","url","expiresIn":600,"singleUse":true,"sessionTtl"}`;
  `POST /api/logout` ends this browser's session (the UI may offer "Sign out"; `?all=1` — Bearer only —
  ends every session and cancels unused links, it is `svoi signout`);
  `GET /api/handshake?n=<16–128 chars>` (no auth) → `{"proof": hex(HMAC-SHA256(token, "svoi-handshake/v1\0"+n))}` —
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
  So `Кухонный ноутбук` → `kukhonnyy-noutbuk`, reachable as `kukhonnyy-noutbuk.svoi`. Forms that ask for
  a device name (onboarding, rename) should preview the result live (a JS port of that table);
  what counts is `name` in the backend's response. Users who want a Russian display name use the
  per-device **alias** (local nickname, any text).
* **Times** are Unix **seconds** (integers). `0`/`null` means "never/unknown".
* **Sizes** are bytes.
* **Errors**: non-2xx responses carry `{"error":{"code":"…","message":"human readable"}}`.
  Codes: `unauthorized`, `notconfigured`, `denied`, `notfound`, `invalid`, `exists`,
  `offline` (target device not connected), `busy`, `toolarge`, `unsupported`,
  `internal`. HTTP: 400 invalid, 401 unauthorized, 403 denied, 404 notfound, 409 exists,
  412 notconfigured, 413 toolarge, 502 offline, 503 busy, 500 internal.
  `message` is already localised-neutral English; the UI maps `code` to Russian/English text.
* **Streaming bodies** (upload/download) are raw bytes, not multipart.

## Live updates — Server-Sent Events

`GET /api/events` → `text/event-stream`. Each message is `event: <type>` + `data: <json>`.
The UI keeps one `EventSource`; on (re)connect it first calls `GET /api/state`.
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

## Data types

### Self
```jsonc
{
  "id": "…52 chars…", "short": "abcd1234", "name": "laptop", "owner": "Andrey",
  "ip4": "100.64.12.34", "ip6": "fd12:3456::1", "admin": true,
  "meshId": "k3j4h5g6f7d8", "meshName": "Home",
  "udpPort": 41710,
  "endpoints": [ {"addr": "192.168.1.5:41710", "kind": "local"},
                 {"addr": "203.0.113.5:41710", "kind": "observed"} ],   // local | stun | observed
  "nat": { "mappingVaries": false, "public": ["203.0.113.5:41710"], "hasIPv6": false,
           "stun": true,
           "difficulty": "easy" },        // open | easy | hard | unknown
  "version": "0.1.0", "os": "linux", "arch": "amd64", "started": 1760000000,
  "configured": true,
  "relay": true,                           // this device agrees to relay for others
  "relayed": { "packets": 120, "bytes": 150000 }
}
```
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
  "path": "/home/me/Downloads/Svoi/photo.jpg"       // incoming + done only
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
  "ts": 1760000000, "unread": true, "attachments": 2, "thread": "…", "starred": false }
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
  "blocked": true }                // only when true: the folder holds svoi's own keys (it is the data
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
{ "id": "…", "code": "SVOI1-AEAWVQFQ-…", "admin": false,
  "owner": "Anna",                  // whose device this invitation is for; the *inviter* decides it
  "created": 1760000000, "expires": 1760001800,
  "qrSvg": "<svg …>…</svg>" }       // server-rendered QR code of `code`; safe to inject
```

### Settings
```jsonc
{ "downloadDir": "/home/me/Downloads/Svoi",
  "autoAccept": "own",             // own = from devices of the same owner | all | ask
  "autoAcceptMaxMB": 0,            // 0 = no limit
  "relay": true,                   // act as a relay for other members
  "stunEnabled": true, "stunServers": ["stun.l.google.com:19302"],
  "udpPort": 41710, "lan": true,
  "socks": {"enabled": false, "listen": "127.0.0.1:1080"},
  "tun": { "enabled": false,       // create a virtual network interface (Linux, needs root / CAP_NET_ADMIN)
           "manageHosts": true,    // also add `<name>.svoi` lines to /etc/hosts
           "state": "off",         // off | running | error   (live status, read-only)
           "name": "svoi0",        // interface name while running
           "error": "",            // why it could not start, e.g. "permission denied — run as root"
           "supported": true,      // false on platforms without TUN support → hide the section
           "txPackets": 0, "rxPackets": 0, "dropped": 0 },
  "restartRequired": false }       // true when a change needs a restart (udpPort, lan…)
```
`PUT /api/settings` accepts `{"tun": {"enabled": true}}` (either field, either order). With the
interface up, every device is reachable at its overlay address (`ip4`/`ip6`) and as
`<name>.svoi` by *any* program (ssh, a browser, `ping`), not only through the web UI.
Language and theme are **client-side only** (`localStorage`), not part of Settings.

## Endpoints

### State & lifecycle
| method & path | body → response |
|---|---|
| `GET /api/state` | → `{ "version", "configured", "self": Self, "peers": [Peer], "transfers": [Transfer] (active + last 50), "counters": {"mail","chat","offers"}, "invites": [Invite], "settings": Settings, "removed"?: {"meshName": "Дом", "at": 1760000000} }`. Works when `configured:false` (then `self` has only id/short/version/os/arch/configured, `peers: []`). `removed` is present only while the device is outside any mesh **because an administrator removed it** from one: the onboarding screen should say so ("this device was removed from the network «Дом» by an administrator — ask for a new invitation"). The device already has a fresh identity then, so a new invitation just works. A `notify` event with `level: "warn"` and `link: "#/"` is sent at the moment it happens, followed by a `peers` event with an empty list; the UI should reload `GET /api/state`. |
| `GET /api/events` | SSE, see above |
| `POST /api/mesh/create` | `{"meshName","deviceName","owner"}` → `{"ok":true}` (then reload state) |
| `POST /api/mesh/join` | `{"invite","deviceName"}` → `{"ok":true}`; may take up to ~25 s; errors are human readable in `error.message`. There is no `owner` here: whose device this is was set by the inviting device in the invitation (an `owner` sent anyway is ignored), so a new device cannot claim someone else's name to get auto-accepted files. |
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
| `POST /api/shares` | `{"name","path","mode":"ro","allow":["*"]}` → `Share` (`invalid` if the folder does not exist, or if it would expose svoi's own keys: the data directory, one of its parents such as the home folder, or something inside it — the message says so) |
| `PUT /api/shares/:id` | same fields → `Share` |
| `DELETE /api/shares/:id` | → `{"ok":true}` |
| `GET /api/local/fs?path=/home/me` | folder picker for *this* device: `{"path":"/home/me","parent":"/home","home":"/home/me","sep":"/","roots":["/"],"entries":[{"name":"Documents","isDir":true}]}` — directories only; `path` omitted → home |

### Mail
| method & path | body → response |
|---|---|
| `GET /api/mail?folder=inbox&q=text&limit=50&before=1760000000` | `{"items":[MailSummary],"total":120,"unread":3}` newest first; `before` = `ts` of the last item you have (paging) |
| `GET /api/mail/:id` | full Mail (does **not** mark as read) |
| `POST /api/mail` | `{"to":["<id>",…],"subject":"…","body":"…","attachments":["<blob sha256>",…],"inReplyTo":"m_…"?}` → `{"id":"m_…"}`; delivery is asynchronous (store-and-forward) |
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

### Settings
`GET /api/settings` → Settings · `PUT /api/settings` (any subset of the fields) → Settings.

### Managing another device (admins only)
Any of the *configuration* endpoints can be addressed to another device by prefixing the
path with `/api/d/:peerId`:

`/api/d/:peerId/shares[/:id]`, `/api/d/:peerId/services[/:id]`, `/api/d/:peerId/settings`,
`/api/d/:peerId/local/fs`, `/api/d/:peerId/diag/logs`, `/api/d/:peerId/state`.

The node forwards the request to that device, which executes it as if it were local.
`403 denied` unless **this** device is an admin; `502 offline` if the device is not
connected. The UI uses it for "Manage device ▾" selectors on the Shares, Services and
Settings screens (headless NAS / home server configuration from the laptop's UI).
