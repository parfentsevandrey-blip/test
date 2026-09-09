# TorVeil

A Windows privacy client that routes traffic over Tor with Snowflake
obfuscation, user-controlled multi-hop circuits, and DAITA-style traffic
shaping.

It is a desktop application (Wails + WebView2) around a Go engine that drives a
Tor process you install yourself. Two modes:

- **Proxy mode** — loopback SOCKS5 and HTTP listeners. No driver, no
  administrator rights; you point applications at them.
- **Full-tunnel mode** — a Wintun adapter captures the machine's TCP traffic
  and DNS. Needs administrator rights.

Both modes share one data path, so the shaping and the circuit policy apply
identically either way.

---

## What the three headline features actually do

This section is deliberately blunt about limits. A privacy tool that overstates
what it does is worse than one that does less and says so.

### Snowflake

Tor's Snowflake transport, run as a pluggable transport exactly as Tor Browser
runs it. The first hop is a volunteer WebRTC proxy and the broker is reached by
domain fronting, so an observer between you and the internet sees a WebRTC
session to a CDN rather than a connection to Tor.

obfs4 and a plain direct connection are also available. Bridge lines are
editable in Settings, because the Tor Project rotates them and a hard-coded
list eventually stops working.

### Multi-hop

Tor circuits are three hops by default and the client does not normally let you
choose them. TorVeil takes stream attachment away from Tor
(`__LeaveStreamsUnattached`) and builds every circuit itself over the control
port, which is what makes the following possible:

- **2 to 5 hops.** Extra middles are inserted between entry and exit.
- **Entry and exit country pinning**, resolved through Tor's own GeoIP database
  so TorVeil and Tor always agree on which relay is where.
- **Guard pinning.** One entry relay is chosen and reused, and pinned in Tor's
  own configuration too, so the circuits Tor builds for itself leave through
  the same guard.
- **Path constraints Tor applies to its own circuits**: no relay twice, no two
  hops in the same /16, and no two hops in the same relay family (checked
  against microdescriptors, and only counted when both relays name each other).

Stream attachment **fails closed**. If no circuit matching your policy can be
built, the stream is closed rather than quietly handed to a Tor-chosen path —
otherwise a country pin could silently stop applying and you would not know.

Two honest caveats:

- More hops is not more anonymity against the adversary Tor actually worries
  about. Three hops already defeats any single relay; a fourth costs latency
  and buys little against someone who can watch both ends.
- Pinning a country narrows the relay set you can be assigned, which makes your
  traffic easier to tell apart from other users'. It is a trade.

### DAITA-style traffic shaping

Mullvad's DAITA works inside WireGuard, where the client owns both ends of the
tunnel and can pad any packet to a constant size and inject dummy packets the
server drops. Tor gives a client neither: a stream to a website carries
application bytes, and a byte injected into it would be delivered to the
website. So the padding cannot live in the same stream as the data.

TorVeil splits the job into three mechanisms:

1. **Timing quantisation.** Every shaped connection may only hand bytes to Tor
   on a shared clock tick (10–25 ms depending on profile, with jitter). It does
   not change how many bytes are sent, but it destroys the fine-grained
   inter-packet timing that website-fingerprinting classifiers rely on most,
   and it merges concurrent streams into one aggregate burst.

2. **Tor's own padding at full strength.** `ConnectionPadding` and
   `CircuitPadding` on, with the reduced variants off. Tor ships these reduced
   to save bandwidth; this is the cheapest real padding available and it was
   designed for exactly this threat.

3. **Cover traffic on a chaff channel.** Dummy bytes need a destination that
   discards them, so TorVeil publishes an ephemeral v3 onion service pointing
   at a local sink and sends the padding there. That traffic never reaches an
   exit relay, and it shares the single TLS connection to the guard with your
   real traffic — which is the point: an observer on your link sees one
   encrypted flow whose volume and timing no longer track what you are doing.

   In the Paranoid profile the aggregate send rate is held steady and cover
   traffic fills whatever real traffic does not use. The rate rises immediately
   to meet demand and decays with a ~25 s half-life, so the end of a transfer
   is smoothed out instead of being announced by a sudden drop to idle.

| Profile | Send grid | Tor padding | Cover traffic | Cost |
|---|---|---|---|---|
| Off | — | default | none | ~0% |
| Light | 25 ms | full | none | ~2–5% |
| Balanced | 15 ms | full | around activity, ≤32 KiB/s | ~15–30% |
| Paranoid | 10 ms | full | constant rate, ≤128 KiB/s | up to ~128 KiB/s |

**Cover traffic is not free to anyone but you.** Each chaff byte travels about
six relay hops (three out to the rendezvous point, three back), all of it
donated bandwidth. That is why every profile caps the rate, why Balanced only
generates cover traffic around real activity, and why the ceiling is shown in
the interface. Raise it only if you need it.

**What shaping protects against:** someone watching the link between you and
your entry relay — an ISP, a network operator, a hostile Wi-Fi.

**What it does not:** anything the exit relay sees, and any adversary who can
observe both ends of a circuit at once. Neither Tor nor DAITA defends against
end-to-end correlation by a global observer, and this does not either.

---

## Requirements

- Windows 10 or 11 (x64). WebView2 is present by default on both.
- **A Tor installation.** TorVeil does not bundle Tor; it drives a build you
  install, so the binary you run stays one you can verify. Either works:
  - [Tor Expert Bundle](https://www.torproject.org/download/tor/) — includes
    `snowflake-client.exe` and `lyrebird.exe` under `pluggable_transports/`.
  - Tor Browser — TorVeil finds `Browser\TorBrowser\Tor` automatically.

  If it is somewhere else, set **Settings → Extra search directory**.
- **`wintun.dll`** next to `torveil.exe`, for full-tunnel mode only. Download
  from [wintun.net](https://www.wintun.net/) and take the DLL from `bin\amd64`.
- Administrator rights, for full-tunnel mode only.

## Build

From Linux or macOS (Wails v2 needs no CGO for a Windows target):

```sh
./build/build.sh
```

On Windows:

```powershell
.\build\build.ps1
```

Both run `gofmt`, `go vet` for host and Windows, and the test suite before
building, then produce `dist\torveil.exe` (GUI) and `dist\torveild.exe`
(headless).

If you have the Wails CLI, `wails build` also packages an icon and an embedded
manifest; the scripts above ship `torveil.exe.manifest` alongside the binary to
get DPI awareness without that dependency.

## Running

Launch `torveil.exe`. Pick a transport and mode in **Settings**, a circuit
shape in **Route**, a profile in **Shaping**, then **Connect**.

In proxy mode, point applications at the addresses shown on the Status screen
(default `127.0.0.1:9150` for SOCKS5 and `127.0.0.1:9151` for HTTP).

The headless build takes the same settings as flags:

```
torveild -transport snowflake -hops 4 -exit de -shaping balanced
torveild -locate      # report which Tor binaries were found
torveild -profiles    # describe the shaping profiles
```

---

## Known limitations

These are structural, not bugs waiting to be fixed.

**Tor carries TCP only.** UDP cannot be tunnelled at all. In full-tunnel mode
that means:

- QUIC does not work, so it is blocked by default — browsers then fall back to
  TCP instead of stalling on a protocol with nowhere to go.
- ICMP does not work; `ping` tells you nothing useful about connectivity.
- IPv6 is blocked rather than tunnelled, because an IPv6-capable machine would
  otherwise reach IPv6 sites straight past the tunnel.
- DNS is handled by a loopback forwarder on `127.0.0.1:53` that talks to Tor's
  DNSPort, and outbound DNS to anything else is blocked. TorVeil refuses to
  bring the tunnel up if it cannot bind that port, because a tunnel with no
  working resolver invites the user to "fix" it by pointing DNS back at their
  ISP.

**Snowflake plus full-tunnel mode is fragile.** Once the default route points
at the tunnel, Tor's own traffic has to be routed back out over the physical
interface, and TorVeil discovers those addresses by watching which remote
endpoints the `tor` and transport processes hold. Windows exposes no remote
address for UDP sockets, and Snowflake's WebRTC transport is UDP, so those
peers cannot be discovered — Snowflake may lose its connection once the tunnel
comes up. The interface warns about this. **Use obfs4 or a direct connection
for full-tunnel mode, or keep Snowflake and use proxy mode.**

**Onion services in full-tunnel mode** work because TorVeil moves Tor's virtual
address range out of `127.0.0.0/8` (which Windows never routes to an interface)
into `10.192.0.0/10` and routes that into the tunnel. Onion streams are
attached by Tor itself rather than by TorVeil's path policy, because rendezvous
circuits are Tor's to build.

**The kill switch blocks when the tunnel is *not* up.** While the tunnel is
running, routing is what keeps traffic inside it; a default-deny firewall
policy would block the tunnelled applications themselves, since their
connections are ordinary outbound connections as far as Windows Firewall is
concerned. The danger is the moment the adapter disappears and the routes go
with it, so that is when lockdown engages. Rules are created through `netsh`
and are therefore visible and removable in the Windows Firewall UI, and a
journal file lets a crashed run be cleaned up on the next start.

**Logs are memory-only.** Circuit paths, connection errors and Tor's notices
describe a session in enough detail to be worth protecting, so nothing is
written to disk. Closing TorVeil discards them.

---

## Architecture

```
  applications                Wintun adapter (full-tunnel mode)
       │                              │
       │ SOCKS5 / HTTP                │ IP packets
       ▼                              ▼
  ┌─────────────────┐        ┌──────────────────────┐
  │ internal/proxy  │        │ internal/tunnel      │
  │ loopback        │        │ gVisor netstack,     │
  │ listeners       │        │ routes, DNS, bypass, │
  │                 │        │ firewall             │
  └────────┬────────┘        └──────────┬───────────┘
           │                            │
           └────────────┬───────────────┘
                        ▼
              ┌──────────────────────┐
              │ internal/shaper      │  shared clock, cover traffic
              │ timing + chaff       │  on an onion channel
              └──────────┬───────────┘
                         ▼
              ┌──────────────────────┐
              │ Tor SOCKS port       │
              └──────────┬───────────┘
                         │
              ┌──────────┴───────────┐
              │ internal/tor         │  control port: consensus, country
              │ process, control,    │  resolution, path selection,
              │ circuits, streams    │  EXTENDCIRCUIT, ATTACHSTREAM
              └──────────┬───────────┘
                         ▼
              snowflake-client / obfs4 / direct
                         ▼
                   the Tor network
```

| Package | Responsibility |
|---|---|
| `internal/tor` | Control protocol client, Tor supervision, torrc, consensus parsing, path selection, circuit and stream management |
| `internal/shaper` | Shaping profiles, the shared send clock, the onion-terminated chaff generator |
| `internal/proxy` | Loopback SOCKS5 and HTTP listeners, and the dialer into Tor |
| `internal/tunnel` | Wintun adapter, userspace TCP/IP stack, routing, DNS forwarder, bypass watcher, firewall |
| `internal/core` | The state machine that ties it together and the API the UI binds to |
| `internal/config` | Persisted settings |
| `internal/logging` | Bounded in-memory log buffer |
| `cmd/torveil` | Wails desktop application |
| `cmd/torveild` | Headless runner |

### Design decisions worth knowing about

- **Hostnames are never resolved locally.** They are passed to Tor as SOCKS5
  domain-type addresses, so name resolution happens at the exit relay. A local
  lookup would leak the destination to the ISP no matter how well the
  connection itself is protected.
- **The loopback listeners refuse to bind a routable address.** An open Tor
  proxy on a LAN is a much larger exposure than anything this protects against.
- **Tor exits when TorVeil does** (`__OwningControllerProcess`), so a crashed
  UI cannot leave an orphaned Tor with an open SOCKS port.
- **The ephemeral onion service is not detached**, so it disappears with the
  control connection rather than leaving a published descriptor pointing at a
  machine that is no longer listening.
- **Circuits are isolated by destination** and expire after 10 minutes, so one
  long session is not carried end to end by one set of relays.

## Configuration

Settings live in `%APPDATA%\TorVeil\config.json`; the Tor data directory and
generated `torrc` are in `%APPDATA%\TorVeil\tor`. The **Open settings folder**
button in Settings goes there. Everything in the file is editable from the
interface; the file is the same schema, so it can also be edited directly while
TorVeil is closed.

## Tests

```sh
go test ./...
```

Covers the control-protocol parsers, bridge-line parsing, consensus parsing and
exit-policy evaluation, path selection under country/port/subnet/family
constraints, torrc rendering, the shaping quota and rate-decay arithmetic, and
a SOCKS5/HTTP round trip against a stub Tor — including the check that
hostnames reach Tor unresolved.

## Licence and provenance

TorVeil is an independent client. It is not affiliated with the Tor Project or
with Mullvad; "DAITA-style" describes the goal of the shaping layer, not a
shared implementation. Tor, Snowflake, obfs4 and Wintun are the work of their
respective projects and are neither bundled nor modified here.
