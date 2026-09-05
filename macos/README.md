# Veil for macOS

The same tunnel as the Android app — Tor reached through Snowflake, meek,
WebTunnel or Conjure, with no server of ours anywhere in it — as a native
macOS app for Apple Silicon, drawn in the Tahoe design language.

Everything that took the Android app a dozen versions to learn is carried
over rather than rediscovered: connected means a stream actually went
through, a re-dial holds applications' connections open instead of refusing
them, the link is pulsed so a path that died in a sleep is noticed in
seconds, and the network directory ships inside the app so a first connect
does not download it.

## What is reused, and what is new

The Go core is shared with Android, source for source: the pluggable
transports (lyrebird, Snowflake with our own raced rendezvous, Conjure), the
userspace TCP/IP stack, the ad blocker and the directory seed all compile for
`darwin/arm64` unchanged. `macos.go` and `packetflow.go` are the only
additions, and they exist because the two systems hand over the tunnel
differently.

| | Android | macOS |
|---|---|---|
| Tunnel | `VpnService` + TUN fd | `NEPacketTunnelProvider` + utun fd |
| Link endpoint | `fdbased` (Linux only) | `iobased` with a 4-byte utun offset |
| tor | `tor-android` (JNI) | official `tor` binary, spawned |
| SOCKS | loopback TCP port | unix socket in the App Group |
| UI | Compose, Material 3 Expressive | SwiftUI, Liquid Glass |

The unix socket is a real improvement over the Android build rather than a
port detail: a loopback TCP port is reachable by every other process on the
machine, and a socket in the container is not.

## What you need on your Mac

1. **macOS 26 (Tahoe) and Xcode 26.** The interface uses Liquid Glass, which
   does not exist earlier. `Design/Glass.swift` is the single place that
   touches those APIs, so a lower target is a change in one file.
2. **Go 1.24+ and gomobile**, to build the core:
   `go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`
3. **XcodeGen** (`brew install xcodegen`), because the project is kept as
   `project.yml` rather than a `.pbxproj` nobody can read a diff of.
4. **An Apple Developer account.** This is the one hard requirement and it is
   not ours: a packet tunnel needs the
   `com.apple.developer.networking.networkextension` entitlement, which
   Apple issues only to a paid team. Set `DEVELOPMENT_TEAM` in `project.yml`.

## Building

```sh
cd macos
./scripts/fetch-tor.sh      # official tor + PT binaries, macos-aarch64
./scripts/build-core.sh     # Veiltun.xcframework from the shared Go module
xcodegen generate
open Veil.xcodeproj
```

Then in Xcode: select the *Veil* scheme, set your team on both targets, and
run. The first launch asks to install a system extension and to add a VPN
configuration; both are macOS asking whether it may route your traffic, and
both are expected.

For a development build the system extension needs developer mode once:

```sh
systemextensionsctl developer on
```

## How a connect goes

The same sequence as Android, for the same reasons:

1. The transports start and listen on loopback; tor is spawned with them
   already declared, so changing route later is a control-port command and
   never a restart.
2. The directory seed is planted if tor has no cache of its own, so the
   longest part of a first connect is skipped.
3. tor bootstraps over the chosen obfuscation. Snowflake's two ways of
   reaching its broker are raced inside the transport, on one bridge line.
4. **A real stream is opened through tor before anything is called
   connected.** A bootstrap percentage is not evidence; a stream is.
5. Only then is the tunnel attached and the interface configured.

While it is up, the pulse beats every twenty seconds and the supervisor acts
on what it measures rather than on what it assumes.

## Layout

```
VeilCore/           Go: the darwin additions to the shared module
Veil/               The app: UI, tor control, connection logic
  Design/           Liquid Glass tokens and components
  Core/             TorController, TunnelCoordinator, VPNManager
  Model/            Transport, BridgeLine, TunnelState, Settings
  Views/            Tunnel, Routes, Diagnostics, Settings, Log
VeilTunnel/         The network extension: utun fd in, packets out
scripts/            Core and tor build scripts
project.yml         XcodeGen input
```
