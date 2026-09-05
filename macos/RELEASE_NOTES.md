Veil for macOS — Apple Silicon, macOS 26 (Tahoe).

Tor reached through Snowflake, meek, WebTunnel or Conjure, with no account,
no subscription and no server of ours anywhere in it. The interface is drawn
in Liquid Glass; the tunnel core is the same Go code as the Android build.

## Opening it

The app is signed ad-hoc, not by an identified developer, so the first launch
is refused with "cannot be opened because the developer cannot be verified".
Right-click the app → **Open** → **Open** once; after that it opens normally.

If macOS instead says the app is *damaged*, the download was quarantined
before the signature could be checked. Two commands fix it:

    xattr -cr /Applications/Veil.app
    codesign --force --deep --sign - /Applications/Veil.app

## Without Apple's entitlement, it works through the system proxy

A packet tunnel — every application, nothing to configure — needs the
entitlement `com.apple.developer.networking.networkextension`, which Apple
issues only to a paid developer team. This build has no such signature, so
macOS will not load the tunnel.

So the app does the next best thing, by itself: after tor has connected and a
real stream has gone through it, the system's SOCKS, HTTP and HTTPS proxies
are pointed at tor. macOS asks for your password once (its own dialogue, not
a terminal). Safari, Chrome, Firefox and most applications then go through
Tor, hostnames included. Telegram does not follow the system proxy; the
tunnel screen has a one-click button for it.

**The one limitation:** an application with its own networking — most games,
some messengers — ignores the system proxy and stays on the open network.
The app says which mode it is in rather than pretending.

## A build that tunnels everything

To get a build that connects, set three repository secrets and run the
workflow again:

- `APPLE_TEAM_ID`
- `APPLE_CERT_P12` (a Developer ID certificate, base64-encoded)
- `APPLE_CERT_PASSWORD`

The App Group `group.app.veil.mac` must also belong to your team, since that
is how the extension reaches tor's socket. Its name is in `macos/project.yml`
and in both entitlements files.

## What is inside

- tor is the Tor Project's own binary, taken from their expert bundle.
- The pluggable transports, the userspace TCP/IP stack, the ad blocker and the
  shipped network directory are the same Go module the Android app uses.
- SOCKS and the control port are unix sockets inside the app's container
  rather than loopback ports, so no other process on the machine can reach
  them.
