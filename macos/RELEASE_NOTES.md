Veil for macOS — Apple Silicon, macOS 26 (Tahoe).

Tor reached through Snowflake, meek, WebTunnel or Conjure, with no account,
no subscription and no server of ours anywhere in it. The interface is drawn
in Liquid Glass; the tunnel core is the same Go code as the Android build.

## The tunnel will not start in an unsigned build

This is not a bug worth reporting. A packet tunnel needs the entitlement
`com.apple.developer.networking.networkextension`, and Apple issues that only
to a paid developer team. Without it macOS refuses to load the network
extension, so the window opens and the interface works, but nothing connects.

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
